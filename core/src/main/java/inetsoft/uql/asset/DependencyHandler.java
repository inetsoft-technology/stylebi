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
package inetsoft.uql.asset;

import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.*;
import inetsoft.uql.asset.sync.RenameDependencyInfo;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.SingletonManager;
import inetsoft.util.Tool;
import inetsoft.util.dep.*;
import org.apache.commons.lang.StringUtils;

import java.util.List;

@SingletonManager.Singleton(DependencyHandler.Reference.class)
public interface DependencyHandler {
   static DependencyHandler getInstance() {
      return SingletonManager.getInstance(DependencyHandler.class);
   }

   void renameDependencies(AssetObject oentry, AssetObject nentry);

   boolean updateDependencies(AssetObject oentry, AssetObject nentry);

   RenameDependencyInfo getRenameDependencyInfo(AssetObject oentry, AssetObject nentry);

   void deleteDependencies(AssetObject entry);

   void deleteDependenciesKey(AssetObject entry);

   void flushDependencyMap();

   default void updateSheetDependencies(AbstractSheet sheet, AbstractSheet osheet, AssetEntry entry)
   {
      updateSheetDependencies(sheet, osheet, entry, false);
   }

   void updateSheetDependencies(AbstractSheet sheet, AbstractSheet osheet, AssetEntry entry,
                                boolean cache);

   default void updateSheetDependencies(AbstractSheet sheet, AssetEntry entry, boolean add)
      throws Exception
   {
      updateSheetDependencies(sheet, entry, add, false);
   }

   void updateSheetDependencies(AbstractSheet sheet, AssetEntry entry, boolean add,
                               boolean cache) throws Exception;

   void updateVPMDependencies(VirtualPrivateModel vpm, VirtualPrivateModel ovpm, String database,
                              AssetEntry entry);

   void updateModelDependencies(XLogicalModel lmodel, boolean add);

   void updateQueryDependencies(XQuery query, boolean add);

   void updateDrillDependencies(AssetEntry entry, XDrillInfo drill, boolean add);

   void updateTaskDependencies(ScheduleTaskAsset asset);

   void updateDashboardDependencies(IdentityID user, String name, boolean add);

   void updateTaskDependencies(ScheduleTask task, boolean add);

   void updateCubeDomainDependencies(XDomain domain, boolean add);

   void updateScriptDependencies(String script, AssetEntry entry, boolean add,
                                 boolean cache);

   default void updateScriptDependencies(String oscript, String nscript, AssetEntry entry) {
      updateScriptDependencies(oscript, nscript, entry, false);
   }

   void updateScriptDependencies(String oscript, String nscript, AssetEntry entry, boolean cache);

   void updatePhysicalDependencies(AssetEntry entry, String sourcePath);

   static String getAssetId(String path, AssetEntry.Type type) {
      return getAssetId(path, type, AssetRepository.GLOBAL_SCOPE, null);
   }

   static String getAssetId(String path, AssetEntry.Type type, int scope) {
      return getAssetId(path, type, scope, null);
   }

   static String getAssetId(String path, AssetEntry.Type type, int scope, String organizationId) {
      if(type.isDataSource() || type.isQuery() || type.isLogicModel() || type.isPartition() ||
         type.isVPM())
      {
         scope = AssetRepository.QUERY_SCOPE;
      }

      if(type.isScript()) {
         scope = AssetRepository.COMPONENT_SCOPE;
      }

      AssetEntry entry = new AssetEntry(scope, type, path, null, organizationId);

      return entry.toIdentifier();
   }

   static String getCubeSourceKey(String source) {
      return getCubeSourceKey(source, null);
   }

   static String getCubeSourceKey(String source, List<XAsset> importAssets) {
      if(source == null || !source.startsWith(Assembly.CUBE_VS)) {
         return null;
      }

      String path = source.substring(Assembly.CUBE_VS.length());
      int idx = path.lastIndexOf("/");

      if(idx < 0) {
         return null;
      }

      String dxName = path.substring(0, idx);

      if(importAssets != null) {
         for(XAsset importAsset : importAssets) {
            if(!(importAsset instanceof XDataSourceAsset)) {
               continue;
            }

            if(Tool.equals(((XDataSourceAsset) importAsset).getDatasource(), dxName)) {
               return getAssetId(importAsset.getPath(), AssetEntry.Type.DATA_SOURCE);
            }
         }
      }
      else {
         // this is ok for static context because DataSourceRegistry also has a remote design stub
         DataSourceRegistry registry = DataSourceRegistry.getRegistry();
         XDataSource dx = registry.getDataSource(dxName);

         if(dx instanceof JDBCDataSource) {
            return getAssetId(path, AssetEntry.Type.LOGIC_MODEL);
         }
         else if(dx instanceof XMLADataSource) {
            return getAssetId(dxName, AssetEntry.Type.DATA_SOURCE);
         }
      }

      return null;
   }

   static String getUniqueSource(String prefix, String source) {
      if(StringUtils.isEmpty(prefix)) {
         return source;
      }

      if(StringUtils.isEmpty(source)) {
         return prefix;
      }

       return prefix + "/" + source;
   }

   final class Reference extends SingletonManager.Reference<DependencyHandler> {
      @Override
      public synchronized DependencyHandler get(Object... parameters) {
         if(handler == null) {
            handler = new LocalDependencyHandler();
         }

         return handler;
      }

      @Override
      public void dispose() {
         if(handler != null) {
            handler = null;
         }
      }

      private DependencyHandler handler;
   }
}
