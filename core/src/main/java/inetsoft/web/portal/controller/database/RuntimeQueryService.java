/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.controller.database;

import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.Tool;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class RuntimeQueryService {
   public RuntimeQueryService() {
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
      return rmap.get(id);
   }

   public String generateRuntimeId(String name) {
      return name + System.currentTimeMillis();
   }

   public void saveRuntimeQuery(RuntimeXQuery runtimeQuery) {
      mapLock.lock();

      try {
         rmap.put(runtimeQuery.getId(), runtimeQuery);
         heartBeatMap.put(runtimeQuery.getId(), new Date());
      }
      finally {
         mapLock.unlock();
      }
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
      boolean expired = isExpired(id);

      if(!expired) {
         heartBeatMap.put(id, new Date());
      }

      return expired;
   }

   /**
    * Destroy the timeout runtime.
    */
   public void checkTimeout() {
      long now = System.currentTimeMillis();
      long minute3 = now - 180000; // 3 min ago

      mapLock.lock();

      try {
         Set<String> keys = new HashSet<>(rmap.keySet());

         keys.forEach(id -> {
            Date lastTime = heartBeatMap.get(id);
            boolean expired = lastTime.getTime() < minute3;

            if(expired) {
               destroy(id);
            }
         });
      }
      finally {
         mapLock.unlock();
      }
   }

   /**
    * Destroy the runtime.
    */
   public void destroy(String id) {
      mapLock.lock();

      try {
         rmap.remove(id);
         heartBeatMap.remove(id);
      }
      finally {
         mapLock.unlock();
      }
   }

   /**
    * Clear the runtime.
    */
   public void clear() {
      mapLock.lock();

      try {
         rmap.clear();
         heartBeatMap.clear();
      }
      finally {
         mapLock.unlock();
      }
   }

   public boolean isExpired(String id) {
      if(Tool.isEmptyString(id)) {
         return true;
      }

      if(rmap.get(id) != null) {
         return false;
      }

      return true;
   }

   public static class RuntimeXQuery implements Cloneable {
      public RuntimeXQuery(JDBCQuery query, String id, String dataSource) {
         this.query = query;
         this.id = id;
         this.dataSource = dataSource;
         initMetadata();
      }

      private void initMetadata() {
         if(!(query instanceof JDBCQuery)) {
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

            for(int i = 0; i < flds.length; i++) {
               String name = (String) flds[i].getName();
               String type = flds[i].getType();
               XTypeNode node = XSchema.createPrimitiveType(type);
               node.setName(name);
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

   private static final Lock mapLock = new ReentrantLock();
   private static final Map<String, RuntimeXQuery> rmap = new ConcurrentHashMap<>();
   private static final Map<String, Date> heartBeatMap = new ConcurrentHashMap<>();
}
