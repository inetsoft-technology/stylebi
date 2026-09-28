/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.controller.database;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.VariableTable;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.cache.Cache;
import javax.cache.expiry.Duration;
import javax.cache.expiry.TouchedExpiryPolicy;
import java.io.Serializable;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RuntimeQueryService {
   @Autowired
   public RuntimeQueryService(Cluster cluster) {
      this.cluster = cluster;
   }

   @PostConstruct
   public void init() {
      cache = cluster.getCache(
         CACHE_NAME, false, new TouchedExpiryPolicy(new Duration(TimeUnit.MINUTES, 3L)));
   }

   public RuntimeXQuery createRuntimeQuery(RuntimeWorksheet rws, JDBCQuery query, String database,
                                           Principal principal) throws Exception
   {
      query = query.clone();

      if(principal != null) {
         query = (JDBCQuery) VpmProcessor.getInstance().applyHiddenColumns(query,
            rws.getAssetQuerySandbox().getVariableTable(), principal);
      }

      RuntimeXQuery runtimeQuery = new RuntimeXQuery(query, generateRuntimeId(), database);
      runtimeQuery.setOwner(getOwnerKey(principal != null ? principal : contextPrincipal()));
      VariableTable vars = rws == null ?
         new VariableTable() : rws.getAssetQuerySandbox().getVariableTable();
      runtimeQuery.setVariables(vars);
      saveRuntimeQuery(runtimeQuery);

      return runtimeQuery;
   }

   /**
    * Replaces the runtime query of a dialog with a new query (the dialog's clear action).
    * A blank id gets a new id. An id owned by the caller is replaced in place. An id that no
    * longer exists (e.g. expired) is recreated under the same id, owned by the caller. An id
    * owned by anyone else is refused.
    *
    * @param runtimeId the id held by the client, may be blank.
    * @param query     the new query.
    * @param database  the data source name.
    * @param principal the caller.
    *
    * @return the new runtime query.
    */
   public RuntimeXQuery resetRuntimeQuery(String runtimeId, JDBCQuery query, String database,
                                          Principal principal) throws Exception
   {
      String owner = getOwnerKey(principal != null ? principal : contextPrincipal());
      boolean blank = runtimeId == null || runtimeId.trim().isEmpty();
      RuntimeXQuery runtimeQuery = new RuntimeXQuery(
         query.clone(), blank ? generateRuntimeId() : runtimeId, database);
      runtimeQuery.setOwner(owner);
      runtimeQuery.setVariables(new VariableTable());

      if(blank) {
         saveRuntimeQuery(runtimeQuery);
         return runtimeQuery;
      }

      RuntimeXQuery existing = cache.get(runtimeId);

      if(existing == null) {
         if(cache.putIfAbsent(runtimeId, runtimeQuery)) {
            return runtimeQuery;
         }

         // created concurrently, check the owner of that entry instead
         existing = cache.get(runtimeId);
      }

      if(existing != null && !isOwner(existing, owner)) {
         throw new MessageException(
            Catalog.getCatalog().getString("common.sqlquery.sessionExpired"));
      }

      saveRuntimeQuery(runtimeQuery);
      return runtimeQuery;
   }

   public String openNewRuntimeQuery(String oldId) throws Exception {
      return openNewRuntimeQuery(oldId, contextPrincipal());
   }

   public String openNewRuntimeQuery(String oldId, Principal principal) throws Exception {
      RuntimeXQuery oldQuery = this.getRuntimeQuery(oldId, principal);

      if(oldQuery == null) {
         throw new MessageException(
            "The query session has expired. Please close and reopen the query editor.");
      }

      // the clone keeps the owner of the original query
      RuntimeXQuery query = oldQuery.clone();
      String newId = generateRuntimeId();
      query.setId(newId);

      saveRuntimeQuery(query);

      return newId;
   }

   /**
    * Gets a runtime query owned by the principal of the current request or message.
    *
    * @return the runtime query, or {@code null} if it does not exist or is not owned by the
    *         current principal.
    */
   public RuntimeXQuery getRuntimeQuery(String id) {
      return getRuntimeQuery(id, contextPrincipal());
   }

   /**
    * Gets a runtime query owned by the specified principal.
    *
    * @return the runtime query, or {@code null} if it does not exist or is not owned by the
    *         principal.
    */
   public RuntimeXQuery getRuntimeQuery(String id, Principal principal) {
      if(id == null) {
         return null;
      }

      RuntimeXQuery runtimeQuery = cache.get(id);
      return runtimeQuery != null && isOwner(runtimeQuery, getOwnerKey(principal)) ?
         runtimeQuery : null;
   }

   private String generateRuntimeId() {
      return UUID.randomUUID().toString();
   }

   public void saveRuntimeQuery(RuntimeXQuery runtimeQuery) {
      cache.put(runtimeQuery.getId(), runtimeQuery);
   }

   public void closeRuntimeQuery(String originRuntimeId, String newRuntimeId, boolean save) {
      closeRuntimeQuery(originRuntimeId, newRuntimeId, save, contextPrincipal());
   }

   public void closeRuntimeQuery(String originRuntimeId, String newRuntimeId, boolean save,
                                 Principal principal)
   {
      // both ids must be owned by the caller before one is written under the other
      RuntimeXQuery newQuery = getRuntimeQuery(newRuntimeId, principal);
      RuntimeXQuery oldQuery = getRuntimeQuery(originRuntimeId, principal);

      if(newQuery == null || oldQuery == null) {
         return;
      }

      if(save) {
         newQuery.setId(originRuntimeId);
         saveRuntimeQuery(newQuery);
      }

      touch(originRuntimeId, principal);
      destroy(newRuntimeId, principal);
   }

   public boolean touch(String id) {
      return touch(id, contextPrincipal());
   }

   public boolean touch(String id, Principal principal) {
      return getRuntimeQuery(id, principal) != null;
   }

   /**
    * Destroy the runtime query if it is owned by the principal of the current request or
    * message.
    */
   public void destroy(String id) {
      destroy(id, contextPrincipal());
   }

   /**
    * Destroy the runtime query if it is owned by the specified principal.
    */
   public void destroy(String id, Principal principal) {
      if(getRuntimeQuery(id, principal) != null) {
         cache.remove(id);
      }
   }

   public boolean isExpired(String id) {
      return getRuntimeQuery(id) == null;
   }

   /**
    * Gets the key that identifies the owner of a runtime query. For an {@link SRPrincipal}
    * this includes the secure id, so that the entry is bound to the login session, the same
    * as {@code RuntimeSheet.matches}. The current organization is deliberately not part of
    * the key, it depends on the thread.
    */
   static String getOwnerKey(Principal principal) {
      if(principal == null || principal.getName() == null) {
         return null;
      }

      if(principal instanceof SRPrincipal srPrincipal) {
         return principal.getName() + "#" + srPrincipal.getSecureID();
      }

      // authenticated users are always SRPrincipals, other principals are internal ones
      return principal.getName();
   }

   /**
    * Checks if a runtime query is owned by the owner key. A missing key, or a query without
    * an owner (e.g. one written by an older node), never matches.
    */
   private static boolean isOwner(RuntimeXQuery runtimeQuery, String owner) {
      return owner != null && owner.equals(runtimeQuery.getOwner());
   }

   private static Principal contextPrincipal() {
      return ThreadContext.getContextPrincipal();
   }

   private final Cluster cluster;
   private Cache<String, RuntimeXQuery> cache;
   private static final String CACHE_NAME = RuntimeQueryService.class.getName() + ".cache";
   private static final Logger LOG = LoggerFactory.getLogger(RuntimeQueryService.class);

   public static class RuntimeXQuery implements Cloneable, Serializable {
      public RuntimeXQuery(JDBCQuery query, String id, String dataSource) {
         this.query = query;
         this.id = id;
         this.dataSource = dataSource;
         initMetadata();
      }

      private void initMetadata() {
         if(query == null) {
            return;
         }

         UniformSQL uniformSQL = (UniformSQL) query.getSQLDefinition();

         if(uniformSQL == null) {
            return;
         }

         XField[] flds = uniformSQL.getColumnInfo();
         metadata = null;

         if(flds != null && flds.length > 0) {
            metadata = new XTypeNode();

            for(XField fld : flds) {
               String name = (String) fld.getName();
               String type = fld.getType();
               XTypeNode node = XSchema.createPrimitiveType(type);
               Objects.requireNonNull(node).setName(name);
               metadata.addChild(node);
            }
         }
      }

      public JDBCQuery getQuery() {
         return query;
      }

      public void setQuery(JDBCQuery query) {
         this.query = query;
      }

      public String getId() {
         return id;
      }

      public void setId(String id) {
         this.id = id;
      }

      /**
       * Gets the key of the login session that owns this runtime query.
       */
      public String getOwner() {
         return owner;
      }

      void setOwner(String owner) {
         this.owner = owner;
      }

      public int getMaxPreviewRow() {
         return maxPreviewRow;
      }

      public void setMaxPreviewRow(int maxPreviewRow) {
         this.maxPreviewRow = maxPreviewRow;
      }

      public String getDataSource() {
         return dataSource;
      }

      public void setDataSource(String dataSource) {
         this.dataSource = dataSource;
      }

      public XTypeNode getMetadata() {
         return metadata;
      }

      public void setMetadata(XTypeNode metadata) {
         this.metadata = metadata;
      }

      public Map<String, AssetEntry> getSelectedTables() {
         return selectedTables;
      }

      public void setSelectedTables(Map<String, AssetEntry> selectedTables) {
         this.selectedTables = selectedTables;
      }

      public void addSelectedTable(String alias, AssetEntry table) {
         if(this.selectedTables == null) {
            this.selectedTables = new HashMap<>();
         }

         this.selectedTables.put(alias, table);
      }

      public void removeSelectedTable(String alias) {
         if(this.selectedTables != null) {
            this.selectedTables.remove(alias);
         }
      }

      public void renameSelectedTable(String newName, String oldName) {
         if(this.selectedTables != null) {
            AssetEntry removed = this.selectedTables.remove(oldName);

            if(removed != null) {
               this.selectedTables.put(newName, removed);
            }
         }
      }

      public VariableTable getVariables() {
         return initVars;
      }

      public void setVariables(VariableTable initVars) {
         this.initVars = initVars;
      }

      public Map<String, String> getAliasMapping() {
         return aliasMapping;
      }

      public void putAliasMapping(String oldAlias, String newAlias) {
         this.aliasMapping.put(oldAlias, newAlias);
      }

      public void removeAliasMapping(String newAlias) {
         this.aliasMapping.remove(newAlias);
      }

      public void initQueryAliasMapping() {
         XSelection selection = query.getSelection();
         int count = selection.getColumnCount();
         Map<String, String> aliasMapping = new HashMap<>();

         if(count == 0) {
            return;
         }

         for(int i = 0; i < count; i++) {
            String alias = selection.getAlias(i);
            aliasMapping.put(alias, alias);
         }

         this.aliasMapping = aliasMapping;
      }

      @SuppressWarnings("unchecked")
      @Override
      public RuntimeXQuery clone() throws CloneNotSupportedException {
         RuntimeXQuery clone = (RuntimeXQuery) super.clone();
         clone.query = this.query.clone();
         clone.dataSource = this.dataSource;
         clone.id = this.id;
         clone.selectedTables = (Map<String, AssetEntry>) Tool.clone(this.selectedTables);
         clone.initVars = initVars == null ? null : initVars.clone();
         clone.aliasMapping = (Map<String, String>) Tool.clone(this.aliasMapping);

         return clone;
      }

      @Override
      public String toString() {
         return "RuntimeXQuery{" +
            "query=" + query +
            ", id='" + id + '\'' +
            ", maxPreviewRow=" + maxPreviewRow +
            ", dataSource='" + dataSource + '\'' +
            ", metadata=" + metadata +
            ", selectedTables=" + selectedTables +
            ", initVars=" + initVars +
            ", aliasMapping=" + aliasMapping +
            '}';
      }

      private JDBCQuery query;
      private String id;
      private String owner;
      private int maxPreviewRow;
      private String dataSource;
      private XTypeNode metadata;
      private Map<String, AssetEntry> selectedTables;
      private VariableTable initVars;
      // original alias -> new alias
      private Map<String, String> aliasMapping = new HashMap<>();
   }
}
