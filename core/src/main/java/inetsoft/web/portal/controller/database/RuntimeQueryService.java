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
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.Tool;
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
   public RuntimeQueryService() {
      cache = Cluster.getInstance().getCache(
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

      RuntimeXQuery runtimeQuery =
         new RuntimeXQuery(query, generateRuntimeId(query.getName()), database);
      VariableTable vars = rws == null ?
         new VariableTable() : rws.getAssetQuerySandbox().getVariableTable();
      runtimeQuery.setVariables(vars);
      saveRuntimeQuery(runtimeQuery);

      return runtimeQuery;
   }

   public String openNewRuntimeQuery(String oldId) throws Exception {
      RuntimeXQuery query = this.getRuntimeQuery(oldId).clone();
      String newId = generateRuntimeId(query.query.getName());
      query.setId(newId);

      saveRuntimeQuery(query);

      return newId;
   }

   public RuntimeXQuery getRuntimeQuery(String id) {
      return cache.get(id);
   }

   public String generateRuntimeId(String name) {
      return name + System.currentTimeMillis();
   }

   public void saveRuntimeQuery(RuntimeXQuery runtimeQuery) {
      cache.put(runtimeQuery.getId(), runtimeQuery);
   }

   public void closeRuntimeQuery(String originRuntimeId, String newRuntimeId, boolean save) {
      RuntimeXQuery newQuery = getRuntimeQuery(newRuntimeId);
      RuntimeXQuery oldQuery = getRuntimeQuery(originRuntimeId);

      if(newQuery == null || oldQuery == null) {
         return;
      }

      if(save) {
         newQuery.setId(originRuntimeId);
         saveRuntimeQuery(newQuery);
      }

      touch(originRuntimeId);
      destroy(newRuntimeId);
   }

   public boolean touch(String id) {
      return cache.get(id) != null;
   }

   /**
    * Destroy the runtime.
    */
   public void destroy(String id) {
      cache.remove(id);
   }

   /**
    * Clear the runtime.
    */
   public void clear() {
      cache.clear();
   }

   public boolean isExpired(String id) {
      return cache.get(id) == null;
   }

   private final Cache<String, RuntimeXQuery> cache;
   private static final String CACHE_NAME = RuntimeQueryService.class.getName() + ".cache";

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

      private JDBCQuery query;
      private String id;
      private int maxPreviewRow;
      private String dataSource;
      private XTypeNode metadata;
      private Map<String, AssetEntry> selectedTables;
      private VariableTable initVars;
      // original alias -> new alias
      private Map<String, String> aliasMapping = new HashMap<>();
   }
}
