/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.asset;

import inetsoft.report.style.XTableStyle;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.util.dep.*;
import inetsoft.util.gui.ObjectInfo;
import inetsoft.report.*;
import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.sree.schedule.*;
import inetsoft.sree.web.dashboard.DashboardRegistry;
import inetsoft.sree.web.dashboard.VSDashboard;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.FunctionIterator;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.asset.internal.ScriptIterator;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.JDBCSelection;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.HyperlinkRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.vslayout.LayoutInfo;
import inetsoft.uql.viewsheet.vslayout.ViewsheetLayout;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.*;
import inetsoft.web.RecycleUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;

public class LocalDependencyHandler implements DependencyHandler {
   /**
    * The function only fix the dependencies of report/vs, ws is fixed in another method.
    * for report/vs can only dependency by two ways:
    * 1. hyperlink
    * 2. embed vs
    * Rename the report/ws/vs dependencies.
    *
    * @param oentry   the report entry.
    * @param nentry   the new entry.
    */
   @Override
   public void renameDependencies(AssetObject oentry, AssetObject nentry) {
      if(!updateDependencies(oentry, nentry)) {
         return;
      }

      RenameTransformHandler.getTransformHandler().addTransformTask(
         getRenameDependencyInfo(oentry, nentry));
   }

   /**
    * The function only fix the dependencies of report/vs, ws is fixed in another method.
    * for report/vs can only dependency by two ways:
    * 1. hyperlink
    * 2. embed vs
    * Rename the report/ws/vs dependencies.
    *
    * @param oentry   the report entry.
    * @param nentry   the new entry.
    */
   @Override
   public boolean updateDependencies(AssetObject oentry, AssetObject nentry) {
      DependencyStorageService service = DependencyStorageService.getInstance();
      Set<String> keys = service.getKeys(null);

      for(String key: keys) {
         renameDependencyToFile(key, oentry, nentry);
      }

      return true;
   }

   @Override
   public RenameDependencyInfo getRenameDependencyInfo(AssetObject oentry,
                                                              AssetObject nentry)
   {
      String oname = null;
      String nname = null;

      if(oentry instanceof AssetEntry) {
         AssetEntry asset = (AssetEntry) oentry;

         if(asset.isWorksheet()) {
            return null;
         }

         oname = ((AssetEntry) oentry).toIdentifier();
         nname = ((AssetEntry) nentry).toIdentifier();
      }

      return getRDInfo(oname, nname);
   }

   private RenameDependencyInfo getRDInfo(String oname, String nname) {
      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      List<RenameInfo> rinfos = new ArrayList<>();
      List<AssetObject> entries = DependencyTransformer.getDependencies(oname);

      if(entries != null && !entries.isEmpty()) {
         RenameInfo vinfo = new RenameInfo(oname, nname, RenameInfo.VIEWSHEET);
         RenameInfo rinfo = new RenameInfo(oname, nname, RenameInfo.HYPERLINK);
         RenameInfo autoDrillInfo = new RenameInfo(oname, nname, RenameInfo.AUTO_DRILL);

         for(AssetObject entry : entries) {
            if(entry instanceof AssetEntry && ((AssetEntry) entry).isScheduleTask()) {
               dinfo.addRenameInfo(entry, vinfo);
            }
            else {
               dinfo.addRenameInfo(entry, rinfo);
               dinfo.addRenameInfo(entry, autoDrillInfo);
            }
         }

         rinfos.add(rinfo);
      }

      List<AssetObject> emEntries = DependencyTool.getEmbedDependencies(oname);

      if(emEntries != null && !emEntries.isEmpty()) {
         RenameInfo rinfo = new RenameInfo(oname, nname, RenameInfo.EMBED_VIEWSHEET);

         for(AssetObject entry : emEntries) {
            dinfo.addRenameInfo(entry, rinfo);
         }

         rinfos.add(rinfo);
      }

      dinfo.setRenameInfos(rinfos);
      return dinfo;
   }

   /**
    * Remove the report/ws/vs dependencies.
    *
    * @param entry   the report entry/ asset entry.
    */
   @Override
   public void deleteDependencies(AssetObject entry) {
      DependencyStorageService service = DependencyStorageService.getInstance();

      try {
         Set<String> keys = service.getKeys(null);

         for(String key: keys) {
            removeDependencyFromFile(key, entry);
         }
      }
      catch(Exception ignore) {
      }
   }

   @Override
   public void deleteDependenciesKey(AssetObject entry) {
      DependencyStorageService service = DependencyStorageService.getInstance();

      if(!(entry instanceof AssetEntry)) {
         return;
      }

      service.remove(((AssetEntry) entry).toIdentifier());
   }

   private XRepository getXRepository() throws RemoteException {
      return XFactory.getRepository();
   }

   /**
    * Update model dependency.
    *
    * @param mname the model name.
    * @param entry the AssetEntry which using this query as source.
    * @param add   if true add dependency, else remove.
    */
   private void updateModelDependency(String mname, AssetObject entry, boolean add,
                                             boolean cache)
   {
      if(mname == null || entry == null) {
         return;
      }

      if(add) {
         addDependencyToFile(getAssetId(mname, AssetEntry.Type.LOGIC_MODEL), entry, cache);
      }
      else {
         removeDependencyFromFile(getAssetId(mname, AssetEntry.Type.LOGIC_MODEL), entry);
      }
   }

   /**
    * Get dependency key for replet/datasource/query/logical model(for report, path can distinguish
    * user scope or global scope).
    */
   public static String getAssetId(String path, AssetEntry.Type type) {
      int scope = AssetRepository.GLOBAL_SCOPE;

      if(type.isDataSource() || type.isQuery() || type.isLogicModel() || type.isPartition() || type.isVPM()) {
         scope = AssetRepository.QUERY_SCOPE;
      }

      return getAssetId(path, type, scope);
   }

   public static String getAssetId(String path, AssetEntry.Type type, int scope) {
      if(type.isDataSource() || type.isQuery() || type.isLogicModel() || type.isPartition()) {
         scope = AssetRepository.QUERY_SCOPE;
      }

      AssetEntry entry = new AssetEntry(scope, type, path, null);

      return entry.toIdentifier();
   }

   private String getTabularAssetId(String name) {
      return DependencyHandler.getAssetId(name,  AssetEntry.Type.DATA_SOURCE);
   }

   /**
    * Update dependencies for the xattributes in the logical model and save the logical model.
    *
    * @param columns     the columns depend on the xattributes.
    * @param dxname      the datasource name.
    * @param logicalName the logical model name.
    * @param entry       the report/asset entry.
    * @param add         if true add dependecy, else remove the dependency.
    */
   private void updateAttributesDependency(ColumnSelection columns,
                                                  String dxname, String logicalName,
                                                  Object entry, boolean add, boolean cache)
      throws Exception
   {
      if(columns == null || dxname == null || logicalName == null || entry == null) {
         return;
      }

      XRepository xrep = getXRepository();
      XDataModel xdm = xrep.getDataModel(dxname);

      if(xdm == null) {
         return;
      }

      XLogicalModel logical = xdm.getLogicalModel(logicalName);

      if(logical != null) {
         updateAttributesDependency(logical, columns, entry, add, cache);
         xdm.addLogicalModel(logical, false);
         xrep.updateDataModel(xdm.clone());
      }
   }

   /**
    * Update direct data source dependency.
    *
    * @param dsName the query name.
    * @param entry  the AssetEntry which using this query as source.
    * @param add    if true add dependency, else remove.
    */
   private void updateDataSourceDependency(String dsName, String tname, Object entry,
                                                  boolean add, boolean cache)
      throws Exception
   {
      if(dsName == null || entry == null) {
         return;
      }

      XRepository xrep = getXRepository();
      XDataSource dataSource = xrep.getDataSource(dsName);

      if(dataSource == null) {
         return;
      }

      String id = DependencyHandler.getUniqueSource(dsName, tname);

      if(add) {
         dataSource.addOuterDependency(entry);
         addDependencyToFile(getAssetId(id, AssetEntry.Type.DATA_SOURCE),
            (AssetObject) entry, cache);
      }
      else {
         dataSource.removeOuterDependency(entry);
         removeDependencyFromFile(getAssetId(id, AssetEntry.Type.DATA_SOURCE),
                 (AssetObject) entry);
      }

      xrep.updateDataSource(dataSource, dsName, false);
   }

   /**
    * Update direct data source dependency.
    *
    * @param dsName the query name.
    * @param entry  the AssetEntry which using this query as source.
    * @param add    if true add dependency, else remove.
    */
   private void updateTabularSourceDependency(String dsName, Object entry, boolean add,
                                                     boolean cache)
      throws Exception
   {
      if(dsName == null || entry == null) {
         return;
      }

      XRepository xrep = getXRepository();
      XDataSource dataSource = xrep.getDataSource(dsName);

      if(dataSource == null) {
         return;
      }

      if(add) {
         addDependencyToFile(getTabularAssetId(dsName), (AssetObject) entry, cache);
      }
      else {
         removeDependencyFromFile(getTabularAssetId(dsName), (AssetObject) entry);
      }
   }

   private void updateEmbedWSSourceDependency(String asset, Object entry, boolean add,
                                                     boolean cache)
   {
      if(asset == null) {
         return;
      }

      if(add) {
         addDependencyToFile(asset, (AssetObject) entry, cache);
      }
      else {
         removeDependencyFromFile(asset, (AssetObject) entry);
      }
   }

   private void updateAssetDependency(String ws, Object entry, boolean add, boolean cache) {
      if(ws == null || entry == null) {
         return;
      }

      if(add) {
         addDependencyToFile(ws, (AssetObject) entry, cache);
      }
      else {
         removeDependencyFromFile(ws, (AssetObject) entry);
      }
   }

   /**
    * Write the cached dependencies info to the dependency files, this is done for
    * improving performance when maintain the dependencies in UpdateAssetDependenciesHandler.
    * To avoid multiple IO operations when maintain dependencies for one file(one ws\report may has
    * servaral dependencies), we do IO operation in batches to improve performance.
    *
    */
   @Override
   public void flushDependencyMap() {
      ExecutorService executors = Executors.newFixedThreadPool(
         Runtime.getRuntime().availableProcessors());
      DependencyStorageService service = DependencyStorageService.getInstance();

      for(Map.Entry<String, ?> e : depMap.entrySet()) {
         String id = e.getKey();
         Object obj = e.getValue();

         if(obj instanceof RenameTransformObject) {
            executors.execute(() -> {
               try {
                  service.put(id, (RenameTransformObject) obj);
               }
               catch(Exception ex) {
                  LOG.warn("Failed to save dependency file: {}", id, ex);
               }
            });
         }
         else {
            LOG.warn(
               "Invalid dependency object: {} ({})",
               id, obj == null ? "null" : obj.getClass().getName());
         }
      }

      executors.shutdown();

      try {
         executors.awaitTermination(1, TimeUnit.HOURS);
      }
      catch(InterruptedException ex) {
         LOG.debug(
            Catalog.getCatalog().getString("maintain.dependencies.threadInterrupted"), ex);
      }

      depMap.clear();
   }

   private void addDependencyToFile(String id, AssetObject entry, boolean cache) {
      DependencyStorageService service = DependencyStorageService.getInstance();
      RenameTransformObject obj;

      try {
         if(cache && depMap.containsKey(id)) {
            obj = depMap.get(id);
         }
         else {
            obj = service.get(id);
         }

         DependenciesInfo info = null;
         List<AssetObject> list;

         if(obj instanceof DependenciesInfo) {
            info = (DependenciesInfo) obj;
         }

         if(info == null) {
            info = new DependenciesInfo();
            info.setDependencies(new ArrayList<>());
         }

         if(cache && !depMap.containsKey(id)) {
            depMap.put(id, info);
         }

         list = info.getDependencies();

         if(list == null) {
            list = new ArrayList<>();
         }

         if(list.contains(entry)) {
            return;
         }

         list.add(entry);
         info.setDependencies(list);

         if(!cache) {
            service.put(id, info);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to update the dependencies to {}.", id);
      }
   }

   private void removeDependencyFromFile(String id, AssetObject entry) {
      DependencyStorageService service = DependencyStorageService.getInstance();
      String removeId = entry instanceof AssetEntry ? ((AssetEntry)entry).toIdentifier() : null;

      try {
         RenameTransformObject obj = service.get(id);

         if(!(obj instanceof DependenciesInfo)) {
            return;
         }

         DependenciesInfo info = (DependenciesInfo) obj;
         List<AssetObject> infos = info.getDependencies();

         if(CollectionUtils.isEmpty(infos)) {
            return;
         }

         infos.remove(entry);

         for(int i = 0; i < infos.size(); i++) {
            AssetObject asset = infos.get(i);
            String assetId;

            if(asset instanceof AssetEntry) {
               assetId = ((AssetEntry) asset).toIdentifier();
            }
            else {
               continue;
            }

            if(Tool.equals(removeId, assetId)) {
               infos.remove(i);
               break;
            }
         }

         service.put(id, info);
      }
      catch(Exception e) {
         LOG.warn("Failed to update the dependencies to file.", e);
      }
   }

   // vs dependency vs will have two cases, save in different folder:
   // 1. vs link to vs
   // 2. vs using vs as embed vs
   private void addEmbedDependencyToFile(String id, AssetObject entry, boolean cache) {
      DependencyStorageService service = DependencyStorageService.getInstance();
      RenameTransformObject obj;

      try {
         if(cache && depMap.containsKey(id)) {
            obj = depMap.get(id);
         }
         else {
            obj = service.get(id);
         }

         DependenciesInfo info = null;
         List<AssetObject> list;

         if(obj instanceof DependenciesInfo) {
            info = (DependenciesInfo) obj;
         }

         if(info == null) {
            info = new DependenciesInfo();
            info.setEmbedDependencies(new ArrayList<>());
         }

         if(cache && !depMap.containsKey(id)) {
            depMap.put(id, info);
         }

         list = info.getEmbedDependencies();

         if(list == null) {
            list = new ArrayList<>();
         }

         if(list.contains(entry)) {
            return;
         }

         list.add(entry);
         info.setEmbedDependencies(list);

         if(!cache) {
            service.put(id, info);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to update the dependencies to {}.", id);
      }
   }

   // only fix embed vs, do not have embed report.
   private void removeEmbedDependencyToFile(String id, AssetObject entry) {
      DependencyStorageService service = DependencyStorageService.getInstance();
      String removeId = entry instanceof AssetEntry ? ((AssetEntry) entry).toIdentifier() : null;

      try {
         RenameTransformObject obj = service.get(id);

         if(!(obj instanceof DependenciesInfo)) {
            return;
         }

         DependenciesInfo info = (DependenciesInfo) obj;
         List<AssetObject> infos = info.getEmbedDependencies();

         if(CollectionUtils.isEmpty(infos)) {
            return;
         }

         infos.remove(entry);

         for(int i = 0; i < infos.size(); i++) {
            AssetObject asset = infos.get(i);
            String assetId;

            if(asset instanceof AssetEntry) {
               assetId = ((AssetEntry) asset).toIdentifier();
            }
            else {
               continue;
            }

            if(Tool.equals(removeId, assetId)) {
               infos.remove(i);
               break;
            }
         }

         service.put(id, info);
      }
      catch(Exception e) {
         LOG.warn("Failed to update the dependencies to file.", e);
      }
   }

   private void renameDependencyToFile(String id, AssetObject oentry, AssetObject nentry) {
      DependencyStorageService service = DependencyStorageService.getInstance();
      String oAssetId = oentry instanceof AssetEntry ? ((AssetEntry)oentry).toIdentifier() : null;

      try {
         RenameTransformObject obj = service.get(id);

         if(!(obj instanceof DependenciesInfo)) {
            return;
         }

         DependenciesInfo dinfo = (DependenciesInfo) obj;
         List<AssetObject> embedDependencies = ((DependenciesInfo) obj).getEmbedDependencies();
         List<AssetObject> infos = dinfo.getDependencies();

         if(CollectionUtils.isEmpty(infos) && CollectionUtils.isEmpty(embedDependencies)) {
            return;
         }

         boolean changed = updateDependenciesInfo(infos, false, oAssetId, nentry);
         changed |= updateDependenciesInfo(embedDependencies, false, oAssetId, nentry);

         if(changed) {
            service.put(id, dinfo);
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to rename the dependencies to file.", e);
      }
   }

   private boolean updateDependenciesInfo(List<AssetObject> infos, boolean isReport,
                                                 String oAssetId, AssetObject nentry)
   {
      if(infos == null) {
         return false;
      }

      boolean changed = false;

       for(int i = 0; i < infos.size(); i++) {
         AssetObject asset = infos.get(i);
         String assetId;

         if(asset instanceof AssetEntry) {
            assetId = ((AssetEntry) asset).toIdentifier();
         }
         else {
            continue;
         }

         if(Tool.equals(oAssetId, assetId)) {
            infos.set(i, nentry);
            changed = true;
            break;
         }
      }

      return changed;
   }

   @Override
   public void updateModelDependencies(XLogicalModel lmodel, boolean add) {
      if(lmodel == null) {
         return;
      }

      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
         lmodel.getDataSource() + "/" + lmodel.getName(),null);
      String view = getAssetId(lmodel.getDataSource() + "/" + lmodel.getPartition(),
         AssetEntry.Type.PARTITION);
      updateModelDependencies(lmodel, entry, add);

      if(add) {
         addDependencyToFile(view, entry, false);
      }
      else {
         removeDependencyFromFile(view, entry);
      }
   }

   private void updateModelDependencies(XLogicalModel lmodel, AssetEntry entry, boolean add)
   {
      Enumeration<XEntity> entities =  lmodel.getEntities();

      while(entities.hasMoreElements()) {
         XEntity entity = entities.nextElement();
         Enumeration<XAttribute> attributes =  entity.getAttributes();

         while(attributes.hasMoreElements()) {
            XAttribute attribute = attributes.nextElement();

            if(attribute.getXMetaInfo() == null || attribute.getXMetaInfo().getXDrillInfo() == null)
            {
               continue;
            }

            XDrillInfo drillInfo = attribute.getXMetaInfo().getXDrillInfo();
            updateDrillDependencies(entry, drillInfo, add);
         }
      }

      String[] subNames = lmodel.getLogicalModelNames();

      if(subNames != null) {
         for(String modelName : subNames) {
            updateModelDependencies(lmodel.getLogicalModel(modelName), entry, add);
         }
      }
   }

   @Override
   public void updateQueryDependencies(XQuery query, boolean add) {
      if(query == null) {
         return;
      }

      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.QUERY, query.getName(), null);

      updateQueryDependencies(query, entry, add);
   }

   private void updateQueryDependencies(XQuery query, AssetEntry entry, boolean add) {
      XSelection selection = query.getSelection();

      if(selection != null) {
         for(int i = 0; i < selection.getColumnCount(); i++) {
            if(selection.getXMetaInfo(i) == null ||
               selection.getXMetaInfo(i).getXDrillInfo() == null)
            {
               continue;
            }

            XDrillInfo drill = selection.getXMetaInfo(i).getXDrillInfo();
            updateDrillDependencies(entry, drill, add);
         }
      }
   }

      /**
    * The method to add query and logic model drill dependencies.
    * @param entry means the asset entry of query or lm in asset.dat
    * @param drill info is the drill info in query or lm
    * @param add means remove old dependencies and add new dependencies.
    */
   @Override
   public void updateDrillDependencies(AssetEntry entry, XDrillInfo drill, boolean add) {
      String id = null;
      int cnt = drill.getDrillPathCount();

      for(int i = 0; i < cnt; i++) {
         DrillPath path = drill.getDrillPath(i);
         String linkName = path.getLink();

         if(path.getLinkType() == Hyperlink.VIEWSHEET_LINK) {
            id = linkName;
         }

         if(id != null) {
            if(add) {
               addDependencyToFile(id, entry, false);
            }
            else {
               removeDependencyFromFile(id, entry);
            }
         }

         DrillSubQuery query = path.getQuery();
         String wsId = query == null ? null : query.getWsIdentifier();

         if(!StringUtils.isEmpty(wsId)) {
            if(add) {
               addDependencyToFile(wsId, entry, false);
            }
            else {
               removeDependencyFromFile(wsId, entry);
            }
         }
      }
   }

   /**
    * Update the sheet dependencies.
    *  @param sheet  the new specified sheet.
    * @param osheet the old specified sheet.
    * @param entry  the AssetEntry of the sheet.
    */
   @Override
   public void updateSheetDependencies(AbstractSheet sheet, AbstractSheet osheet,
                                              AssetEntry entry, boolean cache)
   {
      try {
         updateSheetDependencies(osheet, entry, false, cache);
         updateSheetDependencies(sheet, entry, true, cache);
      }
      catch(Exception ex) {
         LOG.warn("Failed to update the dependencies for the sheet: " + entry.getName(), ex);
      }
   }

   @Override
   public void updateScriptDependencies(String oscript, String nscript, AssetEntry entry, boolean cache) {
      try {
         updateScriptDependencies(oscript, entry, false, cache);
         updateScriptDependencies(nscript, entry, true, cache);
      }
      catch(Exception ex) {
         LOG.warn("Failed to update the dependencies for the script: " + entry.getName(), ex);
      }
   }

   /**
    * Update the sheet dependencies.
    *
    * @param sheet the specified sheet.
    * @param entry the AssetEntry of the sheet.
    * @param add   if true add dependency, else remove.
    */
   @Override
   public void updateSheetDependencies(AbstractSheet sheet, AssetEntry entry, boolean add,
                                              boolean cache)
      throws Exception
   {
      if(sheet == null || entry == null) {
         return;
      }

      if(entry.isWorksheet()) {
         updateWorksheetDependencies((Worksheet) sheet, entry, add, cache);
      }
      else if(entry.isViewsheet() && sheet instanceof Viewsheet) {
         updateViewsheetDependencies((Viewsheet) sheet, entry, add, cache);
      }
   }

   /**
    * Update the worksheet dependencies.
    *
    * @param ws    the specified worksheet.
    * @param entry the AssetEntry of the sheet.
    * @param add   if true add dependency, else remove.
    */
   private void updateWorksheetDependencies(Worksheet ws, AssetEntry entry, boolean add,
                                                   boolean cache)
      throws Exception
   {
      Assembly[] arr = ws.getAssemblies();

      for(Assembly assembly : arr) {
         if(assembly instanceof MirrorTableAssembly &&
            ((MirrorTableAssembly) assembly).isOuterMirror())
         {
            AssetEntry dep = ((MirrorTableAssembly) assembly).getEntry();
            updateEmbedWSSourceDependency(dep.toIdentifier(), entry, add, cache);
         }

         SourceInfo source;
         ColumnSelection columnSelection;

         if(assembly instanceof BoundTableAssembly) {
            source = ((BoundTableAssembly) assembly).getSourceInfo();
            columnSelection = ((BoundTableAssembly) assembly).getColumnSelection(false);
         }
         else if(assembly instanceof AttachedAssembly) {
            AttachedAssembly attachedAssembly = ((AttachedAssembly) assembly);
            source = attachedAssembly.getAttachedSource();
            columnSelection = new ColumnSelection();

            if(attachedAssembly.getAttachedAttribute() != null) {
               columnSelection.addAttribute(attachedAssembly.getAttachedAttribute());
            }
         }
         else {
            continue;
         }

         if(source == null) {
            return;
         }

         // update dependency for logical model.
         // update dependencies for the xattributes of the logial model.
         if(source.getType() == XSourceInfo.MODEL) {
            updateAttributesDependency(columnSelection, source.getPrefix(),
                                       source.getSource(), entry, add, cache);
            updateModelDependency(DependencyHandler.getUniqueSource(source.getPrefix(), source.getSource()),
                                  entry, add, cache);
         }
         else if(source.getType() == XSourceInfo.PHYSICAL_TABLE) {
            updateDataSourceDependency(source.getPrefix(), null, entry, add, cache);
         }
         else if(source.getType() == XSourceInfo.CUBE) {
            updateDataSourceDependency(source.getPrefix(), null, entry, add, cache);
         }
         // fix tabular source's dependency.
         else if(source.getType() == XSourceInfo.DATASOURCE) {
            updateTabularSourceDependency(source.getPrefix(), entry, add, cache);
         }

         if(assembly instanceof SQLBoundTableAssembly) {
            updateDrillDependenciesForWorksheet(((SQLBoundTableAssembly) assembly), entry, add);
         }
      }
   }

   private void updateDrillDependenciesForWorksheet(SQLBoundTableAssembly sqlBound, AssetEntry entry, boolean add) {
      SQLBoundTableAssemblyInfo sqlBoundTableInfo = sqlBound.getSQLBoundTableInfo();
      JDBCQuery query = sqlBoundTableInfo.getQuery();

      if(query != null) {
         JDBCSelection selection = (JDBCSelection) query.getSelection();
         Enumeration<XMetaInfo> xMetaInfos = selection.getXMetaInfos();

         while(xMetaInfos.hasMoreElements()) {
            XMetaInfo xMetaInfo = xMetaInfos.nextElement();

            if(xMetaInfo == null) {
               continue;
            }

            XDrillInfo xDrillInfo = xMetaInfo.getXDrillInfo();

            if(xDrillInfo != null) {
               updateDrillDependencies(entry, xDrillInfo, add);
            }
         }
      }
   }

   @Override
   public void updateVPMDependencies(VirtualPrivateModel vpm, VirtualPrivateModel ovpm,
                                     String database, AssetEntry entry)
   {
      // for vpm, edit vpm will not change database, so we should not remove old and set new
      // only add datasource to storage.
      updateVPMDependencies(ovpm, false, database, entry);
      updateVPMDependencies(vpm, true, database, entry);
   }

   private void updateVPMDependencies(VirtualPrivateModel vpm, boolean add, String ds,
                                      AssetEntry entry)
   {
      if(vpm == null) {
         return;
      }

      Enumeration<VpmCondition> conditions = vpm.getConditions();

      if(!vpm.getConditions().asIterator().hasNext()) {
         AssetEntry sourceEntry = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.DATA_SOURCE, ds, null);

         if(add) {
            addDependencyToFile(sourceEntry.toIdentifier(), entry, false);
         }
         else {
            removeDependencyFromFile(sourceEntry.toIdentifier(), entry);
         }
      }

      while(conditions.hasMoreElements()) {
         VpmCondition condition = conditions.nextElement();
         String table = condition.getTable();

         if(table == null) {
            continue;
         }

         AssetEntry sourceEntry = null;

         if(condition.getType() == VpmCondition.PHYSICMODEL && table != null) {
            sourceEntry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.PARTITION,
                                             ds + "/" + table, null);

         }
         else if(condition.getType() == VpmCondition.TABLE && table != null) {
            sourceEntry = new AssetEntry(AssetRepository.QUERY_SCOPE,
               AssetEntry.Type.DATA_SOURCE, ds, null);
         }

         if(add) {
            addDependencyToFile(sourceEntry.toIdentifier(), entry, false);
         }
         else {
            removeDependencyFromFile(sourceEntry.toIdentifier(), entry);
         }
      }
   }


   /**
    * Update the viewhsheet dependencies.
    *
    * @param vs    the specified viewsheet.
    * @param entry the AssetEntry of the sheet.
    * @param add   if true add dependency, else remove.
    */
   private void updateViewsheetDependencies(Viewsheet vs, AssetEntry entry, boolean add,
                                                   boolean cache)
      throws Exception
   {
      updateRunQueryDependencies(vs, entry, add, cache);
      updateScriptDependencies(vs, entry, add, cache);
      updateDeviceDependencies(vs, entry, add, cache);
      AssetEntry base = vs.getBaseEntry();

      for(Assembly assembly : vs.getAssemblies()) {
         if(assembly instanceof Viewsheet) {
            updateEmbedDependencies((Viewsheet) assembly, entry, add, cache);
         }

         if(assembly instanceof VSAssembly) {
            updateScriptDependencies((VSAssembly) assembly, entry, add, cache);
            updateTableStyleDependencies((VSAssembly) assembly, entry, add, cache);
         }

         updateHyperlinkDependencies(assembly, entry, add, cache);
         updateCubeSourceDependencies(assembly, entry, add, cache);
         updateCalcTableNamedGroupDependencies(assembly, entry, add, cache);
      }

      if(base == null) {
         return;
      }

      if(base.getType() == AssetEntry.Type.PHYSICAL_TABLE) {
         String entryPaths = base.getPath();
         final List<String> allTableTypes = new ArrayList<>(Arrays.asList(
            "EXTERNAL TABLE", "TABLE", "VIEW", "SYNONYM", "ALIAS",
            "MATERIALIZED VIEW", "BASE TABLE"));
         String type = null;
         int typeIndexInPath;

         for(String tableType : allTableTypes) {
            typeIndexInPath = entryPaths.indexOf("/" + tableType + "/");

            if(typeIndexInPath >= 0) {
               type = tableType;
               break;
            }
         }

         if(type == null) {
            return;
         }

         updateDataSourceDependency(base.getProperty("prefix"), null, entry, add, cache);
         return;
      }

      if(base.getType() == AssetEntry.Type.WORKSHEET) {
         updateAssetDependency(base.toIdentifier(), entry, add, cache);
         return;
      }

      if(ObjectInfo.QUERY.equals(base.getProperty("mainType"))) {
         return;
      }

      // worksheet dependencies have already be done in AbstractAssetEngine.
      if(!ObjectInfo.LOGICAL_MODEL.equals(base.getProperty("mainType"))) {
         return;
      }

      XRepository xrep = getXRepository();
      XDataModel xdm = xrep.getDataModel(base.getProperty("prefix"));

      if(xdm != null) {
         List<XLogicalModel> logicalModels = new ArrayList<>();
         XLogicalModel logicalModel = xdm.getLogicalModel(base.getProperty("source"));
         logicalModels.add(logicalModel);

         if(logicalModel != null) {
            String[] extendModelNames = logicalModel.getLogicalModelNames();

             for(String extendModelName : extendModelNames) {
                 logicalModels.add(logicalModel.getLogicalModel(extendModelName));
             }
         }

         updateModelDependency(
            DependencyHandler.getUniqueSource(base.getProperty("prefix"), base.getProperty("source")),
            entry, add, cache);

         for(XLogicalModel logical : logicalModels) {
            if(logical == null) {
               continue;
            }

            Assembly[] arr = vs.getAssemblies();

            for(Assembly assembly : arr) {
               if(assembly != null) {
                  updateAttributesDependency((VSAssembly) assembly, entry, logical, add, cache);
               }
            }

            if(logical.getConnection() != null || logical.getBaseModel() != null) {
                assert logicalModel != null;
                logicalModel.addLogicalModel(logical, false);
            }
            else {
               xdm.addLogicalModel(logical, false);
            }

            xrep.updateDataModel(xdm.clone());
         }
      }
   }

   private void updateRunQueryDependencies(Viewsheet vs, AssetEntry entry, boolean add,
                                           boolean cache)
   {
      ViewsheetInfo vsInfo = vs.getViewsheetInfo();
      List<AssetEntry> list = new ArrayList<>();
      // runQuery command generally be placed in the onInit,
      // here check both onInit and onLoad to be more secure
      collectRunQueryDependencies(vsInfo.getOnInit(), list);
      collectRunQueryDependencies(vsInfo.getOnLoad(), list);

      list.stream()
         .filter(val -> val != null)
         .forEach(val -> {
            if(add) {
               addDependencyToFile(val.toIdentifier(), entry, cache);
            }
            else {
               removeDependencyFromFile(val.toIdentifier(), entry);
            }
         });
   }

   private void collectRunQueryDependencies(String script, List<AssetEntry> list) {
      if(StringUtils.isEmpty(script)) {
         return;
      }

      FunctionIterator iterator = new FunctionIterator(script);
      ScriptIterator.ScriptListener listener = new ScriptIterator.ScriptListener() {
         @Override
         public void nextElement(ScriptIterator.Token token, ScriptIterator.Token pref,
                                 ScriptIterator.Token cref)
         {
            if(token.isRef() && pref != null && "runQuery".equals(pref.val) )
            {
               list.add(createAssetEntry(token.val));
            }
         }
      };

      iterator.addScriptListener(listener);
      iterator.iterate();
      iterator.removeScriptListener(listener);
   }

   /**
    * @param wsPath used in runQuery script, like "ws:global:path" or "ws:global:path:tableName"
    * @return AssetEntry
    */
   private AssetEntry createAssetEntry(String wsPath) {
      if(StringUtils.isEmpty(wsPath) || !wsPath.startsWith("ws:")) {
         return null;
      }

      String[] arr = wsPath.split(":");

      if(arr.length < 3) {
         return null;
      }


      int scope = Tool.equals(arr[1], "global") ?
         AssetRepository.GLOBAL_SCOPE : AssetRepository.USER_SCOPE;
      IdentityID user = scope == AssetRepository.USER_SCOPE ? new IdentityID(arr[1], OrganizationManager.getCurrentOrgName()) : null;
      String path = arr[2]; // {Folder1/Folder 2/.../}datasetName
      path = path.replace("{", "");
      path = path.replace("}", "");

      Principal principal = ThreadContext.getContextPrincipal();
      String orgId;
      if (principal instanceof XPrincipal) {
         orgId = ((XPrincipal) principal).getOrgId();
      } else {
         orgId = OrganizationManager.getInstance().getCurrentOrgID();
      }

      return new AssetEntry(scope, AssetEntry.Type.WORKSHEET, path, user, orgId);
   }

   private void updateEmbedDependencies(Viewsheet embed, AssetEntry entry,
                                               boolean add, boolean cache)
   {
      AssetEntry emEntry = embed.getEntry();
      String id = emEntry.toIdentifier();

      if(add) {
         addEmbedDependencyToFile(id, entry, cache);
      }
      else {
         removeEmbedDependencyToFile(id, entry);
      }
   }

   private void updateTableStyleDependencies(VSAssembly assembly, AssetEntry entry,
                                             boolean add, boolean cache)
   {
      VSAssemblyInfo vinfo = assembly.getVSAssemblyInfo();
      String style = null;

      if(vinfo instanceof TableDataVSAssemblyInfo) {
         style = ((TableDataVSAssemblyInfo) vinfo).getTableStyle();
      }

      if(StringUtils.isEmpty(style)) {
         return;
      }

      String assetId = getAssetId(style, AssetEntry.Type.TABLE_STYLE, AssetRepository.COMPONENT_SCOPE);

      if(add) {
         addDependencyToFile(assetId, entry, cache);
      }
      else {
         removeDependencyFromFile(assetId, entry);
      }
   }

   @Override
   public void updatePhysicalDependencies(AssetEntry entry, String database) {
      String assetId = getAssetId(database, AssetEntry.Type.DATA_SOURCE, AssetRepository.QUERY_SCOPE);
      removeDependencyFromFile(assetId, entry);
      addDependencyToFile(assetId, entry, false);
   }

   private void updateDeviceDependencies(Viewsheet viewsheet, AssetEntry entry,
                                         boolean add, boolean cache)
   {
      LayoutInfo layoutInfo = viewsheet.getLayoutInfo();
      List<ViewsheetLayout> layouts = layoutInfo.getViewsheetLayouts();

      for(ViewsheetLayout layout: layouts) {
         for(String layoutId: layout.getDeviceIds()) {
            String id = getAssetId(layoutId, AssetEntry.Type.DEVICE, AssetRepository.COMPONENT_SCOPE);

            if(add) {
               addDependencyToFile(id, entry, cache);
            }
            else {
               removeDependencyFromFile(id, entry);
            }
         }
      }
   }

   @Override
   public void updateScriptDependencies(String script, AssetEntry entry,
                                         boolean add, boolean cache)
   {
      if(StringUtils.isEmpty(script)) {
         return;
      }

      final Vector functions = new Vector();
      LibManager manager = LibManager.getManager();

      FunctionIterator iterator = new FunctionIterator(script);
      Principal principal = ThreadContext.getContextPrincipal();

      ScriptIterator.ScriptListener listener = new ScriptIterator.ScriptListener() {
         @Override
         public void nextElement(ScriptIterator.Token token, ScriptIterator.Token pref,
                                 ScriptIterator.Token cref)
         {
            if(token.isRef() && !functions.contains(token.val) &&
               manager.findScriptName(token.val) != null)
            {
               AssetEntry scriptEntry = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                  AssetEntry.Type.SCRIPT, token.val,
                  IdentityID.getIdentityIDFromKey(principal.getName()), null);
               String id = scriptEntry.toIdentifier();

               if(add) {
                  addScriptDependencyToFile(id, entry, cache);
               }
               else {
                  removeScriptDependencyToFile(id, entry);
               }
            }
         }
      };

      iterator.addScriptListener(listener);
      iterator.iterate();
      iterator.removeScriptListener(listener);
   }

   private void updateScriptDependencies(VSAssembly assembly, AssetEntry entry,
                                         boolean add, boolean cache)
   {
      VSAssemblyInfo vinfo = assembly.getVSAssemblyInfo();
      String script = vinfo.getScript();
      updateScriptDependencies(script, entry, add, cache);
   }

   private void addScriptDependencyToFile(String id, AssetObject entry, boolean cache) {
      DependencyStorageService service = DependencyStorageService.getInstance();
      RenameTransformObject obj;

      try {
         if(cache && depMap.containsKey(id)) {
            obj = depMap.get(id);
         }
         else {
            obj = service.get(id);
         }

         DependenciesInfo info = null;
         List<AssetObject> list;

         if(obj instanceof DependenciesInfo) {
            info = (DependenciesInfo) obj;
         }

         if(info == null) {
            info = new DependenciesInfo();
            info.setDependencies(new ArrayList<>());
         }

         if(cache && !depMap.containsKey(id)) {
            depMap.put(id, info);
         }

         list = info.getDependencies();

         if(list == null) {
            list = new ArrayList<>();
         }

         if(list.contains(entry)) {
            return;
         }

         list.add(entry);
         info.setDependencies(list);

         if(!cache) {
            service.put(id, info);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to update the dependencies to {}.", id);
      }
   }

   private void removeScriptDependencyToFile(String id, AssetObject entry) {
      DependencyStorageService service = DependencyStorageService.getInstance();
      String removeId = entry instanceof AssetEntry ? ((AssetEntry) entry).toIdentifier() : null;

      try {
         RenameTransformObject obj = service.get(id);

         if(!(obj instanceof DependenciesInfo)) {
            return;
         }

         DependenciesInfo info = (DependenciesInfo) obj;
         List<AssetObject> infos = info.getDependencies();

         if(CollectionUtils.isEmpty(infos)) {
            return;
         }

         infos.remove(entry);

         for(int i = 0; i < infos.size(); i++) {
            AssetObject asset = infos.get(i);
            String assetId;

            if(asset instanceof AssetEntry) {
               assetId = ((AssetEntry) asset).toIdentifier();
            }
            else {
               continue;
            }

            if(Tool.equals(removeId, assetId)) {
               infos.remove(i);
               break;
            }
         }

         service.put(id, info);
      }
      catch(Exception e) {
         LOG.warn("Failed to update the dependencies to file.", e);
      }
   }

   // Add its dependencies to the report/vs it used in hyperlink.
   private void updateHyperlinkDependencies(Assembly assembly, AssetEntry entry, boolean add,
                                                   boolean cache)
   {
      if(assembly instanceof TableDataVSAssembly) {
         TableDataVSAssembly table = (TableDataVSAssembly) assembly;
         TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) table.getInfo();
         TableHyperlinkAttr hattr = info.getHyperlinkAttr();
         Enumeration<Hyperlink> links = hattr == null ? null : hattr.getAllHyperlinks();
         links = links == null ? Collections.emptyEnumeration() : links;

         while(links.hasMoreElements()) {
            Hyperlink link = links.nextElement();
            updateLinkDependencies(link, entry, add, cache);
         }
      }
      else if(assembly instanceof ChartVSAssembly) {
         ChartVSAssembly chart = (ChartVSAssembly) assembly;
         VSChartInfo info = chart.getVSChartInfo();
         Hyperlink link = info.getHyperlink();
         updateLinkDependencies(link, entry, add, cache);

         for(DataRef ref : info.getBindingRefs(true)) {
            if(ref instanceof HyperlinkRef) {
               updateLinkDependencies(((HyperlinkRef) ref).getHyperlink(), entry, add, cache);
            }
         }
      }
      else if(assembly instanceof OutputVSAssembly) {
         OutputVSAssembly output = (OutputVSAssembly) assembly;
         Hyperlink link = ((OutputVSAssemblyInfo) output.getInfo()).getHyperlink();
         updateLinkDependencies(link, entry, add, cache);
      }
   }

   private void updateLinkDependencies(Hyperlink link, AssetObject entry, boolean add,
                                              boolean cache)
   {
      if(link == null) {
         return;
      }

      String id = null;

      if(link.getLinkType() == Hyperlink.VIEWSHEET_LINK) {
         id = link.getLink();
      }

      if(id == null) {
         return;
      }

      if(add) {
         addDependencyToFile(id, entry, cache);
      }
      else {
         removeDependencyFromFile(id, entry);
      }
   }

   // Add its dependencies to the report/vs it binding cube.
   private void updateCubeSourceDependencies(Assembly assembly, AssetEntry entry,
                                                    boolean add, boolean cache)
   {
      if(assembly instanceof DataVSAssembly) {
         DataVSAssembly data = (DataVSAssembly) assembly;
         DataVSAssemblyInfo dinfo = (DataVSAssemblyInfo) data.getVSAssemblyInfo();
         SourceInfo sinfo = dinfo.getSourceInfo();

         if(sinfo == null || sinfo.getType() != XSourceInfo.ASSET) {
            return;
         }

         updateCubeDependencies(sinfo.getSource(), entry, add, cache);
      }
      else if(assembly instanceof OutputVSAssembly) {
         OutputVSAssembly output = (OutputVSAssembly) assembly;
         ScalarBindingInfo oinfo = output.getScalarBindingInfo();

         if(oinfo != null) {
            updateCubeDependencies(oinfo.getTableName(), entry, add, cache);
         }
      }
      else if(assembly instanceof SelectionVSAssembly) {
         SelectionVSAssembly select = (SelectionVSAssembly) assembly;
         SelectionVSAssemblyInfo sinfo = (SelectionVSAssemblyInfo) select.getInfo();

         if(sinfo != null) {
            updateCubeDependencies(sinfo.getTableName(), entry, add, cache);
         }
      }
   }

   /**
    * Add dependencies to the vs calc table asset named group.
    * @param assembly CalcTableVSAssembly
    * @param entry vs entry.
    * @param add whether add.
    * @param cache whether update cache.
    */
   private void updateCalcTableNamedGroupDependencies(Assembly assembly, AssetEntry entry,
                                                             boolean add, boolean cache)
   {
      if(assembly instanceof CalcTableVSAssembly) {
         CalcTableVSAssembly calcTableVSAssembly = (CalcTableVSAssembly) assembly;
         List<CellBindingInfo> cellInfos = calcTableVSAssembly.getTableLayout().getCellInfos(true);

         if(cellInfos == null) {
            return;
         }

         for(CellBindingInfo cellBindingInfo : cellInfos) {
            if(!(cellBindingInfo instanceof TableLayout.TableCellBindingInfo)) {
               continue;
            }

            TableLayout.TableCellBindingInfo tableCellBindingInfo = (TableLayout.TableCellBindingInfo) cellBindingInfo;

            if(tableCellBindingInfo.getCellBinding() == null) {
               continue;
            }

            TableCellBinding cellBinding = tableCellBindingInfo.getCellBinding();
            OrderInfo orderInfo = cellBinding.getOrderInfo(false);

            if(orderInfo == null) {
               continue;
            }

            XNamedGroupInfo namedGroupInfo = orderInfo.getNamedGroupInfo();

            if(!(namedGroupInfo instanceof AssetNamedGroupInfo)) {
               continue;
            }

            AssetNamedGroupInfo assetNamedGroupInfo = (AssetNamedGroupInfo) namedGroupInfo;
            AssetEntry assetEntry = assetNamedGroupInfo.getEntry();

            if(assetEntry != null && assetEntry.isWorksheet()) {
               String id = assetEntry.toIdentifier();

               if(add) {
                  addDependencyToFile(id, entry, cache);
               }
               else {
                  removeDependencyFromFile(id, entry);
               }
            }
         }
      }
   }

   private void updateCubeDependencies(String source, AssetEntry entry,
                                              boolean add, boolean cache)
   {
      if(source == null || !source.startsWith(Assembly.CUBE_VS)) {
         return;
      }

      String cubeKey = getCubeSourceKey(source);

      if(cubeKey == null) {
         LOG.error("cannot find cube source: " + source);
         return;
      }

      if(add) {
         addDependencyToFile(cubeKey, entry, cache);
      }
      else {
         removeDependencyFromFile(cubeKey, entry);
      }
   }

   public static String getCubeSourceKey(String source) {
      return getCubeSourceKey(source, null);
   }

   public static String getCubeSourceKey(String source, List<XAsset> importAssets) {
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

   /**
    * Update dependencies for logical model and xattributes.
    *
    * @param assembly the specified vsassembly.
    * @param entry    the AssetEntry of the worksheet.
    * @param model    the logical model used by the viewsheet.
    * @param add      if true add dependency, else remove.
    */
   private void updateAttributesDependency(VSAssembly assembly, AssetEntry entry,
                                                  XLogicalModel model, boolean add, boolean cache)
   {
      if(assembly instanceof TableVSAssembly) {
         TableVSAssembly table = (TableVSAssembly) assembly;
         ColumnSelection columns = table.getColumnSelection();
         updateAttributesDependency(model, columns, entry, add, cache);
      }
      else if(assembly instanceof CalcTableVSAssembly) {
         CalcTableVSAssembly table = (CalcTableVSAssembly) assembly;
         DataRef[] refs = table.getBindingRefs();
         updateAttributesDependency(model, refs, entry, add, cache);
      }
      else if(assembly instanceof ChartVSAssembly) {
         ChartVSAssembly chart = (ChartVSAssembly) assembly;
         VSChartInfo info = chart.getVSChartInfo();
         VSDataRef[] refs = info.getFields();
         updateAttributesDependency(model, refs, entry, add, cache);
      }
      else if(assembly instanceof SelectionVSAssembly) {
         SelectionVSAssembly selection = (SelectionVSAssembly) assembly;
         DataRef[] refs = selection.getDataRefs();
         updateAttributesDependency(model, refs, entry, add, cache);
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         CrosstabVSAssemblyInfo info = (CrosstabVSAssemblyInfo) assembly.getVSAssemblyInfo();
         VSCrosstabInfo cinfo = info.getVSCrosstabInfo();
         updateAttributesDependency(model, cinfo.getRowHeaders(), entry, add, cache);
         updateAttributesDependency(model, cinfo.getColHeaders(), entry, add, cache);
         updateAttributesDependency(model, cinfo.getAggregates(), entry, add, cache);
      }
      else if(assembly instanceof CurrentSelectionVSAssembly) {
         CurrentSelectionVSAssembly container = (CurrentSelectionVSAssembly) assembly;
         String[] children = container.getAssemblies();
         Viewsheet vs = container.getViewsheet();

         for(String child : children) {
            VSAssembly achild = vs.getAssembly(child);

            if(achild != null) {
               updateAttributesDependency(achild, entry, model, add, cache);
            }
         }
      }
      else if(assembly instanceof BindableVSAssembly) {
         BindableVSAssembly bindable = (BindableVSAssembly) assembly;
         VSAssemblyInfo info = bindable.getVSAssemblyInfo();

         if(info instanceof BindableVSAssemblyInfo) {
            BindingInfo binfo = ((BindableVSAssemblyInfo) info).getBindingInfo();

            if(binfo instanceof ListBindingInfo) {
               DataRef label = ((ListBindingInfo) binfo).getLabelColumn();
               DataRef value = ((ListBindingInfo) binfo).getValueColumn();
               updateAttributesDependency(model, new DataRef[]{ label, value }, entry, add, cache);
            }
            else if(binfo instanceof ScalarBindingInfo) {
               DataRef ref = ((ScalarBindingInfo) binfo).getColumn();
               updateAttributesDependency(model, new DataRef[]{ ref }, entry, add, cache);
            }
         }
      }
   }

   /**
    * Update dependencies for logical model and xattributes.
    *
    * @param model   the logical model used by the report/vs/ws.
    * @param columns the columns depend on the xattributes.
    * @param entry   the AssetEntry of the sheet.
    * @param add     if true add dependency, else remove.
    */
   private void updateAttributesDependency(XLogicalModel model, ColumnSelection columns,
                                                  Object entry, boolean add, boolean cache)
   {
      if(model == null || entry == null || columns == null || columns.getAttributeCount() == 0) {
         return;
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef ref = columns.getAttribute(i);
         updateAttributesDependency(model, ref, entry, add, cache);
      }
   }

   /**
    * Update dependencies for logical model and xattributes.
    *
    * @param model the logical model used by the report/vs/ws.
    * @param refs  the datarefs depend on the xattributes.
    * @param entry the AssetEntry
    * @param add   if true add dependency, else remove.
    */
   private void updateAttributesDependency(XLogicalModel model, DataRef[] refs,
                                                  Object entry, boolean add, boolean cache)
   {
      if(model == null || entry == null || refs == null || refs.length == 0) {
         return;
      }

      for(DataRef ref : refs) {
         updateAttributesDependency(model, ref, entry, add, cache);
      }
   }

   /**
    * Update dependencies for logical model and xattributes.
    *
    * @param model the logical model used by the report/vs/ws.
    * @param ref   the dataref depend on the xattributes.
    * @param entry the AssetEntry
    * @param add   if true add dependency, else remove.
    */
   private void updateAttributesDependency(XLogicalModel model, DataRef ref,
                                                  Object entry, boolean add, boolean cache)
   {
      if(ref == null) {
         return;
      }

      List<String> list = null;

      if(ref instanceof VSDimensionRef) {
         ref = ((VSDimensionRef) ref).getDataRef();
      }
      else if(ref instanceof VSAggregateRef) {
         list = new ArrayList<>();

         String column = ((VSAggregateRef) ref).getColumnValue();
         String column2 = ((VSAggregateRef) ref).getSecondaryColumnValue();

         if(column != null) {
            list.add(column);
         }

         if(column2 != null) {
            list.add(column2);
         }
      }

      XEntity entity;
      XAttribute attribute = null;

      if(ref == null) {
         return;
      }

      String entityName = ref.getEntity();

      if(entityName != null) {
         String modelEntityName;
         String modelAttributeName;

         if(entityName.endsWith("_O")) {
            String attributeName = ref.getAttribute();
            String[] entityAttributeName = attributeName.split(":");

            if(entityAttributeName.length != 2) {
               return;
            }

            modelEntityName = entityAttributeName[0];
            modelAttributeName = entityAttributeName[1];
         }
         else {
            modelEntityName = ref.getEntity();
            modelAttributeName = ref.getAttribute();
         }

         entity = model.getEntity(modelEntityName);

         if(entity != null && !entity.isBaseAttribute(modelAttributeName)) {
            attribute = entity.getAttribute(modelAttributeName);
         }

         updateAttributeDependency(model, attribute, entry, add, cache);
      }
      else {
         String[] attrs;

         if(list != null) {
            attrs = list.toArray(new String[0]);
         }
         else {
            attrs = new String[]{ ref.getAttribute() };
         }

         for(String attr : attrs) {
            while(attribute == null && attr.contains(":")) {
               int idx = attr.indexOf(":");

               if(idx + 1 >= attr.length()) {
                  break;
               }

               String entityName2 = attr.substring(0, idx);
               attr = attr.substring(idx + 1);
               entity = model.getEntity(entityName2);

               if(entity != null && !entity.isBaseAttribute(attr)) {
                  attribute = entity.getAttribute(attr);
               }
            }

            updateAttributeDependency(model, attribute, entry, add, cache);
         }
      }
   }

   /**
    * Update logical model dependency.
    *
    * @param model     the datasource name.
    * @param attribute the attribute.
    * @param entry     the AssetEntry which using this query as source.
    * @param add       if true add dependency, else remove.
    */
   private void updateAttributeDependency(XLogicalModel model, XAttribute attribute,
                                                 Object entry, boolean add, boolean cache)
   {
      if(attribute == null) {
         return;
      }

      if(add) {
         model.addOuterDependency(entry);
         attribute.addOuterDependency(entry);
      }
      else {
         model.removeOuterDependency(entry);
         attribute.removeOuterDependency(entry);
      }
   }

   public void updateDashboardDependencies(IdentityID user, String name, boolean add) {
      AssetEntry entry = new AssetEntry(AssetRepository.USER_SCOPE,
         AssetEntry.Type.DASHBOARD, name, user);

      VSDashboard dashboard = (VSDashboard) DashboardRegistry.getRegistry(user).getDashboard(name);
      String id = dashboard.getViewsheet().getIdentifier();

      if(id != null) {
         if(add) {
            addDependencyToFile(id, entry, false);
         }
         else {
            removeDependencyFromFile(id, entry);
         }
      }
   }

   /**
    * Update dependencies when import schedule task.
    * @param asset   the target schedule task asset.
    */
   @Override
   public void updateTaskDependencies(ScheduleTaskAsset asset) {
      String path = asset.getPath();
      ScheduleManager manager = ScheduleManager.getScheduleManager();
      String taskName = path.substring(path.lastIndexOf('/') + 1);
      ScheduleTask task = manager.getScheduleTask(taskName);

      if(task == null) {
         return;
      }

      for(int i = 0; i < task.getActionCount(); i++) {
         if(task.getAction(i) instanceof ViewsheetAction) {
            ViewsheetAction action = (ViewsheetAction) task.getAction(i);
            String viewsheetId = ((ViewsheetAction) action).getViewsheetName();

            if(viewsheetId != null && !viewsheetId.isEmpty()) {
               int orgIdx = StringUtils.ordinalIndexOf(viewsheetId, "^", 4);

               if(orgIdx != -1) {
                  viewsheetId = viewsheetId.substring(0, orgIdx);
               }

               AssetEntry entry = AssetEntry
                  .createAssetEntry(viewsheetId, OrganizationManager.getInstance().getCurrentOrgID());
               action.setViewsheet(entry.toIdentifier());
            }

         }
      }


      updateTaskDependencies(task, true);
   }

   @Override
   public void updateTaskDependencies(ScheduleTask task, boolean add) {
      if(task == null) {
         return;
      }

      AssetEntry tentry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.SCHEDULE_TASK, "/" + task.getName(), null);

      for(int i = 0; i < task.getActionCount(); i++) {
         ScheduleAction action = task.getAction(i);
         updateTaskActionDependency(tentry, task.getOwner(), action, add);
      }
   }

   /**
    * Add schedule action dependency to dependency storage.
    * @param tentry  the dependency entry.
    * @param owner   the owner of the task.
    * @param add     true if add dependencies info, else remove.
    */
   private void updateTaskActionDependency(AssetEntry tentry, IdentityID owner,
                                                 ScheduleAction action, boolean add)
   {
      AssetEntry entry = null;

      if(action instanceof BatchAction) {
         entry = ((BatchAction) action).getQueryEntry();

         if(entry != null) {
            if(entry.isWorksheet()) {
               addRemoveDep(entry, add, tentry);
            }
            else if(entry.isTable()) {
               AssetEntry parent = entry.getParent();
               AssetEntry asset = new AssetEntry(parent.getScope(), AssetEntry.Type.WORKSHEET,
                  parent.getPath(), parent.getUser());
               addRemoveDep(asset, add, tentry);
            }
         }
      }
      else if(action instanceof ViewsheetAction) {
         if(!Tool.isEmptyString(((ViewsheetAction) action).getViewsheetName())) {
            entry = ((ViewsheetAction) action).getViewsheetEntry();
            addRemoveDep(entry, add, tentry);
         }
      }
      else if(action instanceof IndividualAssetBackupAction) {
         List<XAsset> assets = ((IndividualAssetBackupAction) action).getAssets();

         for(XAsset asset : assets) {
            if(asset instanceof ScheduleTaskAsset) {
               String path = asset.getPath();

               if(path.contains("/")) {
                  int idx = path.lastIndexOf("/");
                  path = path.substring(idx + 1);
               }

               entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                      AssetEntry.Type.SCHEDULE_TASK, "/" + path, null);
            }
            else if(asset instanceof ScriptAsset) {
               String path = asset.getPath();
               entry = new AssetEntry(
                  AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.SCRIPT, path, null);
            }
            else if(asset instanceof TableStyleAsset) {
               LibManager manager = LibManager.getManager();
               String path = asset.getPath();
               XTableStyle style = manager.getTableStyle(path);
               entry = new AssetEntry(
                  AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.TABLE_STYLE, style.getID(), null);
            }
            else if(asset instanceof ViewsheetAsset) {
               entry = ((ViewsheetAsset) asset).getAssetEntry();
            }
            else if(asset instanceof WorksheetAsset) {
               entry = ((WorksheetAsset) asset).getAssetEntry();
            }
            else if(asset instanceof XQueryAsset) {
               XQueryAsset qasset = (XQueryAsset) asset;
               entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.QUERY,
                                      qasset.getPath(), null);
            }
            else if(asset instanceof XPartitionAsset) {
               String path = getModelPath(asset.getPath());
               entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.PARTITION,
                                      path, null);
            }
            else if(asset instanceof XLogicalModelAsset) {
               String path = getModelPath(asset.getPath());
               entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
                                      path, null);
            }
            else if(asset instanceof XDataSourceAsset) {
               XDataSourceAsset qasset = (XDataSourceAsset) asset;
               entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
                                      qasset.getPath(), null);
            }
            else if(asset instanceof VirtualPrivateModelAsset) {
               VirtualPrivateModelAsset vpm = (VirtualPrivateModelAsset) asset;
               String path = vpm.getPath().replace('^', '/');
               entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.VPM, path, null);
            }

            if(entry != null) {
               addRemoveDep(entry, add, tentry);
            }
         }
      }
   }

   private String getModelPath(String path) {
      int idx = path.indexOf(XUtil.DATAMODEL_FOLDER_SPLITER);

      if(idx != -1) {
         String dbPath = path.substring(0, idx);
         String modelPath = path.substring(idx + XUtil.DATAMODEL_FOLDER_SPLITER.length());
         idx = modelPath.indexOf("^");

         if(idx != -1) {
            String modelName = modelPath.substring(idx);
            path = dbPath + modelName;
         }
      }

      return path.replace('^', '/');
   }

   private void addRemoveDep(AssetEntry entry, boolean add, AssetEntry tentry) {
      if(entry != null) {
         if(!add) {
            removeDependencyFromFile(entry.toIdentifier(), tentry);
         }

         if(add) {
            addDependencyToFile(entry.toIdentifier(), tentry, false);
         }
      }
   }

   @Override
   public void updateCubeDomainDependencies(XDomain domain, boolean add) {
      if(domain == null) {
         return;
      }

      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DOMAIN, domain.getDataSource(), null);

      Enumeration<?> cubes = domain.getCubes();

      while(cubes.hasMoreElements()) {
         XCube cube = (XCube) cubes.nextElement();

         // in case has name but not really created
         if(cube == null) {
            continue;
         }

         Enumeration<XDimension> dims = cube.getDimensions();

         while(dims.hasMoreElements()) {
            XDimension dim = dims.nextElement();

            for(int i = 0; i < dim.getLevelCount(); i++) {
               XCubeMember member = dim.getLevelAt(i);
               XMetaInfo metaInfo = member.getXMetaInfo();
               XDrillInfo drillInfo = metaInfo == null ? null : metaInfo.getXDrillInfo();

               if(drillInfo != null) {
                  updateDrillDependencies(entry, drillInfo, add);
               }
            }
         }
      }
   }

   private static final Map<String, RenameTransformObject> depMap =
      Collections.synchronizedMap(new HashMap<>());
   private static final Logger LOG = LoggerFactory.getLogger(LocalDependencyHandler.class);
}
