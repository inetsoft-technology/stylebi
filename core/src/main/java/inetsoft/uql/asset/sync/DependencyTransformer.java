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
package inetsoft.uql.asset.sync;

import inetsoft.report.Hyperlink;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.GroupedThread;
import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.xpath.*;
import java.io.*;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.*;

/**
 * DependencyTransformer is a class to process rename dependencies. Its children classes is as this:
 *
 *    ReportDependencyTransformer is process report binding query/lm/ws:
 *       *ReportQueryDependencyTransformer(report binding query)
 *       *ReportLMDependencyTransformer(report binding logic model)
 *       *ReportWSDependencyTransformer(report binding ws)
 *
 *    AssetDependencyTransformer is process vs/ws binding query/lm/ws:
 *       *AssetQueryDependencyTransformer(vs/ws binding query)
 *       *AssetLMDependencyTransformer(vs/ws binding logic model)
 *       *AssetWSDependencyTransformer(vs/ws binding ws)
 *       *AssetTabularDependencyTransformer(ws binding tabular)
 *
 */
public abstract class DependencyTransformer {
   /**
    * Get the processor. The info will have list of rename infos, but they will have the same
    * source type and so will use the same processor. Such as:
    * 1. change 3 columns of one query.
    (ReportQueryDependencyTransformer and AssetQueryDependencyTransformer)
    * 2. rename ws table name and ws column names.
    (ReportWSDependencyTransformer and AssetWSDependencyTransformer)
    */
   public static void renameDep(RenameDependencyInfo dinfo) {
      renameDep(dinfo, true);
   }

   /**
    * Get the processor. The info will have list of rename infos, but they will have the same
    * source type and so will use the same processor. Such as:
    * 1. change 3 columns of one query.
    (ReportQueryDependencyTransformer and AssetQueryDependencyTransformer)
    * 2. rename ws table name and ws column names.
    (ReportWSDependencyTransformer and AssetWSDependencyTransformer)
    */
   public static void renameDep(RenameDependencyInfo dinfo, boolean syncProcess) {
      long start = System.currentTimeMillis();
      LOG.debug("Rename transformation start: " + start);

      if(dinfo == null || dinfo.getDependencyMap().isEmpty()) {
         return;
      }

      AssetObject[] entries = dinfo.getAssetObjects();

      if(syncProcess) {
         int nthread = DependencyTool.getThreadNumber(entries.length);
         // the transform assets number for each thread.
         int count = (int) Math.ceil(entries.length * 1.0 / nthread);
         final int pri = Thread.currentThread().getPriority();
         ExecutorService executors = Executors.newFixedThreadPool(nthread);

         for(int i = 0; i < nthread; i++) {
            List<AssetObject> list = DependencyTool.getThreadAssets(i, count, entries);

            executors.execute((new GroupedThread("AssetTransformerThread_" + i) {
               {
                  setDaemon(true);
               }

               @Override
               protected void doRun() {
                  setPriority(pri);
                  transformAssets(list, dinfo);
               }
            }));
         }

         executors.shutdown();

         try {
            executors.awaitTermination(50, TimeUnit.SECONDS);
         }
         catch(InterruptedException ignore) {
         }

         long end = System.currentTimeMillis();
         LOG.debug("Rename transformation end, cost: " + (end - start));
      }
      else {
         transformAssets(Arrays.asList(entries), dinfo);
      }
   }

   /**
    * Transform the assets.
    * @param list     the assets list need to transform.
    * @param dinfo    the RenameDependencyInfo which keeps the rename information.
    */
   private static void transformAssets(List<AssetObject> list, RenameDependencyInfo dinfo) {
      for(AssetObject entry : list) {
         transformAsset(entry, dinfo);
      }
   }

   /**
    * Transform the asset.
    * @param entry     the asset need to transform.
    * @param dinfo    the RenameDependencyInfo which keeps the rename information.
    */
   public static void transformAsset(AssetObject entry, RenameDependencyInfo dinfo) {
      if(entry instanceof AssetEntry) {
         List<RenameDependencyInfo> causeRenameDepsInfos = null;

         if(((AssetEntry) entry).isScheduleTask()) {
            TaskAssetDependencyTransformer taskTransformer =
               new TaskAssetDependencyTransformer((AssetEntry) entry);
            taskTransformer.setAssetFile(dinfo.getAssetFile(entry));
            taskTransformer.process(dinfo.getRenameInfo(entry));
         }
         else if(((AssetEntry) entry).isDashboard()) {
            DashboardAssetDependencyTransformer dashboardTransformer =
               new DashboardAssetDependencyTransformer((AssetEntry) entry);
            dashboardTransformer.setAssetFile(dinfo.getAssetFile(entry));
            dashboardTransformer.process(dinfo.getRenameInfo(entry));
         }
         else {
            causeRenameDepsInfos = AssetDependencyTransformer.renameDep((AssetEntry) entry,
                  dinfo.getAssetFile(entry), dinfo.getRenameInfo(entry));
         }

         if(!(causeRenameDepsInfos == null || causeRenameDepsInfos.size() == 0)) {
            for(RenameDependencyInfo causeRenameDepsInfo : causeRenameDepsInfos) {
               if(causeRenameDepsInfo == null) {
                  continue;
               }

               if(dinfo.isRecursive()) {
                  renameDep(causeRenameDepsInfo);
               }
            }
         }
      }

      if(dinfo.getAssetFile(entry) == null) {
         if(dinfo.isRecursive()) {
            try {
               Cluster.getInstance().sendMessage(new RenameTransformFinishedEvent(entry, dinfo));
            }
            catch(Exception e) {
               LOG.error("Failed to send message: ", e);
            }
         }
         else if(dinfo.isRuntime()) {
            try {
               RenameTransformFinishedEvent evt = new RenameTransformFinishedEvent(entry, dinfo);
               evt.setReload(true);
               Cluster.getInstance().sendMessage(evt);
            }
            catch(Exception e) {
               LOG.error("Failed to send message: ", e);
            }

            return;
         }
      }
   }

   /**
    * process rename infos.
    * @param infos rename infos
    * @return cause new RenameInfos
    */
   public abstract RenameDependencyInfo process(List<RenameInfo> infos);

   protected void renameTablePaths(Element doc, RenameInfo info) {
      NodeList list = getChildNodes(doc, ".//tableDataPath/path/aPath");

      for(int i = 0; i < list.getLength(); i++) {
         Element binding = (Element) list.item(i);
         renameTablePath(binding, info);
      }
   }

   // override by logic model and ws:
   // model should change oname's . to :
   // ws should check source table name.
   protected void renameTablePath(Element apath, RenameInfo info) {
      String val = Tool.getValue(apath);

      if(Tool.equals(info.getOldName(), val)) {
         replaceCDATANode(apath, info.getNewName());
      }
   }

   public static void prepareChildrenSources(String path, List<String> list, XRepository repository)
      throws Exception
   {
      String[] sources = repository.getSubDataSourceNames(path);
      list.addAll(Arrays.asList(sources));

      String[] folders = repository.getSubfolderNames(path);

      for(String folder : folders) {
         prepareChildrenSources(folder, list, repository);
      }
   }

   public static RenameDependencyInfo createDependencyInfo(XDataSource datasource,
                                                           String oldSourceName,
                                                           String newSourceName)
   {
      return createDependencyInfo(datasource, oldSourceName, newSourceName, false);
   }

   public static RenameDependencyInfo createDependencyInfo(XDataSource datasource,
                                                           String oldSourceName,
                                                           String newSourceName, boolean dsFolder)
   {
      if(datasource instanceof XMLADataSource) {
         return createCubeDependencyInfo(oldSourceName, newSourceName);
      }

      boolean tabular = datasource instanceof ListedDataSource ||
         datasource.getType().startsWith(SourceInfo.REST_PREFIX) ||
         datasource instanceof TabularDataSource;

      if(tabular) {
         return createTabularDependencyInfo(oldSourceName, newSourceName, dsFolder);
      }

      return createSourceDependencyInfo(oldSourceName, newSourceName, dsFolder);
   }

   public static RenameDependencyInfo createCubeDependencyInfo(String oldSourceName,
                                                               String newSourceName)
   {
      String id = getAssetId(oldSourceName, AssetEntry.Type.DATA_SOURCE);
      oldSourceName = Assembly.CUBE_VS + oldSourceName;
      newSourceName = Assembly.CUBE_VS + newSourceName;
      List<RenameInfo> tinfos = new ArrayList<>();
      RenameInfo info = new RenameInfo(oldSourceName, newSourceName,
         RenameInfo.CUBE | RenameInfo.DATA_SOURCE, false);
      tinfos.add(info);
      RenameDependencyInfo dinfo = createRenameDependency(id, tinfos);

      if(dinfo == null) {
         return null;
      }

      dinfo.setRenameInfos(tinfos);

      return dinfo;
   }

   public static List<RenameDependencyInfo> createDatasourceFolderDependencyInfo(
      DataSourceRegistry dataSourceRegistry,
      String oldPath,
      String newPath)
   {
      List<RenameDependencyInfo> infos = new ArrayList<>();
      List<String> children = dataSourceRegistry.getSubDataSourceNames(oldPath, true);

      for(String name : children) {
         XDataSource child = (XDataSource) dataSourceRegistry.getDataSource(name).clone();
         int onameIdx = name.indexOf(oldPath);
         String newName = name;

         if(onameIdx != -1) {
            newName = name.replaceFirst(oldPath, newPath);
         }

         child.setName(newName);
         RenameDependencyInfo dinfo = DependencyTransformer.createDependencyInfo(
            child, name, child.getFullName(), true);
         infos.add(dinfo);
      }

      return infos;
   }

   private static RenameDependencyInfo createTabularDependencyInfo(String oldSourceName,
                                                                   String newSourceName,
                                                                   boolean isDSFolder)
   {
      String id = getTabularAssetId(oldSourceName);
      List<RenameInfo> tinfos = new ArrayList<>();
      List<RenameInfo> qinfos = new ArrayList<>();
      int type = isDSFolder ? RenameInfo.TABULAR_SOURCE | RenameInfo.DATA_SOURCE_FOLDER :
         RenameInfo.TABULAR_SOURCE | RenameInfo.DATA_SOURCE;
      RenameInfo info = new RenameInfo(oldSourceName, newSourceName, type, true);

      tinfos.add(info);
      RenameDependencyInfo dinfo = createRenameDependency(id, tinfos);
      dinfo = dinfo == null ? new RenameDependencyInfo() : dinfo;

      List<AssetObject> entries = getDependencies(id);

      if(entries != null) {
         for(AssetObject entry : entries) {
            dinfo.addRenameInfo(entry, info);
         }
      }

      // need update key of dependencies index storage.
      dinfo.setRenameInfos(tinfos);

      return dinfo;
   }

   private static RenameDependencyInfo createSourceDependencyInfo(String oldSourceName,
                                                                  String newSourceName)
   {
      return createSourceDependencyInfo(oldSourceName, newSourceName, false);
   }

   private static RenameDependencyInfo createSourceDependencyInfo(String oldSourceName,
                                                                  String newSourceName,
                                                                  boolean dsFolder)
   {
      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      List<RenameInfo> rinfos = new ArrayList<>();

      fixModelDependency(oldSourceName, newSourceName, dinfo, dsFolder, rinfos);
      fixPhyTableDependency(oldSourceName, newSourceName, dinfo, dsFolder, rinfos);
      fixSQLSourceDependency(oldSourceName, newSourceName, dinfo, dsFolder, rinfos);
      dinfo.setRenameInfos(rinfos);

      return dinfo;
   }

   /**
    * Create a RenameDependencyInfo for datasource folder rename transformation.
    *
    * When rename datasource folder, find all the sources, list their children queries and models
    * and rename all them.
    *
    * @param oldDatasourceFolder the old datasource folder name.
    * @param newDatasourceFolder the new datasource folder name.
    * @param childrenSources     the sub datasource name of this datasorce folder.
    */
   //
   public static RenameDependencyInfo createDependencyInfo(String oldDatasourceFolder,
                                                           String newDatasourceFolder,
                                                           List<String> childrenSources)
   {
      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      List<RenameInfo> rinfos = new ArrayList<>();

      for(String source : childrenSources) {
         String nsource = source.replace(oldDatasourceFolder + "/", newDatasourceFolder + "/");
         XDataSource ds = null;

         try {
            ds = getXRepository().getDataSource(source);
         }
         catch(RemoteException e) {
            // do nothing
         }

         if(ds instanceof XMLADataSource) {
            return createCubeDependencyInfo(source, nsource);
         }

         boolean tabular = ds != null && (ds instanceof ListedDataSource ||
            (ds.getType() != null && ds.getType().startsWith(SourceInfo.REST_PREFIX))) ||
             ds instanceof TabularDataSource;

         if(tabular) {
            RenameDependencyInfo rdinfo = createTabularDependencyInfo(source, nsource, true);
            rinfos.addAll(rdinfo.getRenameInfos());
            Map<AssetObject, List<RenameInfo>> map = rdinfo.getDependencyMap();

            for(AssetObject entry : map.keySet()) {
               dinfo.addRenameInfos(entry, map.get(entry));
            }
         }
         else {
            // Only model's key will be changed in dependency file, so do not fix query, query do not affect
            fixModelDependency(source, nsource, dinfo, true, rinfos);
            fixPhyTableDependency(source, nsource, dinfo, true, rinfos);
            // ws sql bound table binding table in source.
            fixSQLSourceDependency(source, nsource, dinfo, true, rinfos);
         }
      }

      dinfo.setRenameInfos(rinfos);
      return dinfo;
   }

   public static RenameDependencyInfo createDependencyInfo(String opath, String npath) {
      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      List<RenameInfo> rinfos = new ArrayList<>();

      fixModelDependency(opath, npath, dinfo, true, rinfos);
      fixPhyTableDependency(opath, npath, dinfo, true, rinfos);
      fixSQLSourceDependency(opath, npath, dinfo, true, rinfos);
      dinfo.setRenameInfos(rinfos);

      return dinfo;
   }

   protected static void fixModelDependency(String oldSourceName, String newSourceName,
                                            RenameDependencyInfo dinfo,
                                            boolean isDSFolder, List<RenameInfo> infos)
   {
      fixLogicalModelDependency(oldSourceName, newSourceName, dinfo, isDSFolder, infos);
      fixPartitionDependency(oldSourceName, newSourceName, dinfo, isDSFolder, infos);
      fixVPMDependency(oldSourceName, newSourceName, dinfo, isDSFolder, infos);
   }

   protected static void fixLogicalModelDependency(String oldSourceName, String newSourceName,
                                                   RenameDependencyInfo dinfo,
                                                   boolean isDSFolder, List<RenameInfo> infos)
   {
      try {
         XRepository xrep = getXRepository();
         String ds = isDSFolder ? oldSourceName : newSourceName;
         int type = isDSFolder ? RenameInfo.LOGIC_MODEL | RenameInfo.DATA_SOURCE_FOLDER :
            RenameInfo.LOGIC_MODEL | RenameInfo.DATA_SOURCE;

         // rename data model dependencies
         XDataModel dxm = xrep.getDataModel(ds);

         if(dxm != null) {
            for(String name : dxm.getLogicalModelNames()) {
               XLogicalModel xlm = dxm.getLogicalModel(name);
               int rtype = type;

               fixLogicalModelDependency(xlm, oldSourceName, newSourceName, dinfo, infos, rtype);
               String[] childNames = xlm.getLogicalModelNames();

               for(String childName : childNames) {
                  XLogicalModel child = xlm.getLogicalModel(childName);
                  fixLogicalModelDependency(child, oldSourceName, newSourceName, dinfo, infos, type);
               }
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to rename model dependencies", e);
      }
   }

   private static void fixLogicalModelDependency(XLogicalModel xlm,
                                                 String oldSourceName, String newSourceName,
                                                 RenameDependencyInfo dinfo,
                                                 List<RenameInfo> infos, int type)
   {
      String name = xlm.getName();
      XLogicalModel base = xlm.getBaseModel();
      String baseName = base == null ? null : base.getName();

      if(!StringUtils.isEmpty(baseName)) {
         name = baseName + "/" + name;
      }

      List<AssetObject> entries = getModelDependencies(getSourceUnique(oldSourceName, name));

      if(entries == null) {
         return;
      }

      RenameInfo ninfo = new RenameInfo(oldSourceName, newSourceName, type);
      ninfo.setSource(name);
      ninfo.setModelFolder(xlm.getFolder());

      for(AssetObject entry : entries) {
         dinfo.addRenameInfo(entry, ninfo);
      }

      infos.add(ninfo);
   }

   protected static void fixVPMDependency(String oldSourceName, String newSourceName,
                                          RenameDependencyInfo dinfo,
                                          boolean isDSFolder, List<RenameInfo> infos)
   {
      try {
         XRepository xrep = getXRepository();
         String ds = isDSFolder ? oldSourceName : newSourceName;
         int type = isDSFolder ? RenameInfo.VPM | RenameInfo.DATA_SOURCE_FOLDER :
            RenameInfo.VPM | RenameInfo.DATA_SOURCE;
         XDataModel dxm = xrep.getDataModel(ds);

         if(dxm != null) {
            String[] virtualPrivateModelNames = dxm.getVirtualPrivateModelNames();

            if(virtualPrivateModelNames == null) {
               return;
            }

            for(String vpmName : virtualPrivateModelNames) {
               String vpmPath = oldSourceName + "/" + vpmName;
               List<AssetObject> vpmDependencies = getVPMDependencies(vpmPath);

               if(vpmDependencies == null || vpmDependencies.size() == 0) {
                  continue;
               }

               RenameInfo renameInfo = new RenameInfo(vpmPath, newSourceName + "/" + vpmName, type);

               for(AssetObject entry : vpmDependencies) {
                  dinfo.addRenameInfo(entry, renameInfo);
               }

               infos.add(renameInfo);
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to create rename info for partition", e);
      }
   }

   protected static void fixPartitionDependency(String oldSourceName, String newSourceName,
                                                RenameDependencyInfo dinfo,
                                                boolean isDSFolder, List<RenameInfo> infos)
   {
      try {
         XRepository xrep = getXRepository();
         String ds = isDSFolder ? oldSourceName : newSourceName;
         int type = isDSFolder ? RenameInfo.PARTITION | RenameInfo.DATA_SOURCE_FOLDER :
            RenameInfo.PARTITION | RenameInfo.DATA_SOURCE;

         // rename data model dependencies
         XDataModel dxm = xrep.getDataModel(ds);

         if(dxm != null) {
            String[] names = dxm.getPartitionNames();

            for(String partitionName : names) {
               XPartition partition = dxm.getPartition(partitionName);
               fixPartitionDependency(partition, oldSourceName, newSourceName, dinfo, infos, type);
               String[] childNames = partition.getPartitionNames();

               for(String childName : childNames) {
                  XPartition child = partition.getPartition(childName);
                  fixPartitionDependency(child, oldSourceName, newSourceName, dinfo, infos, type);
               }
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to create rename info for partition", e);
      }
   }

   private static void fixPartitionDependency(XPartition partition,
                                              String oldSourceName, String newSourceName,
                                              RenameDependencyInfo dinfo, List<RenameInfo> infos,
                                              int type)
   {
      String name = partition.getName();
      XPartition base = partition.getBasePartition();
      String baseName = base == null ? null : base.getName();

      if(!StringUtils.isEmpty(baseName)) {
         name = baseName + "/" + name;
      }

      List<AssetObject> entries = getPartitionDependencies(getSourceUnique(oldSourceName, name));

      if(entries == null) {
         return;
      }

      RenameInfo ninfo = new RenameInfo(oldSourceName, newSourceName, type);
      ninfo.setSource(name);
      ninfo.setModelFolder(partition.getFolder());

      for(AssetObject entry : entries) {
         dinfo.addRenameInfo(entry, ninfo);
      }

      infos.add(ninfo);
   }

   // For physical table, we can't get all tables to find dependency, we can only find dependency
   // to find used physical tables to get rename infos.
   private static void fixPhyTableDependency(String oldSourceName, String newSourceName,
                                             RenameDependencyInfo dinfo, boolean isDSFolder,
                                             List<RenameInfo> infos)
   {
      try {
         int type = isDSFolder ? RenameInfo.PHYSICAL_TABLE | RenameInfo.DATA_SOURCE_FOLDER :
                 RenameInfo.PHYSICAL_TABLE | RenameInfo.DATA_SOURCE;
         DependencyStorageService service = DependencyStorageService.getInstance();
         Set<String> keys = service.getKeys(null);
         String osource = oldSourceName;

         if(osource.contains("/")) {
            osource = osource.substring(osource.lastIndexOf("/") + 1);
         }

         for(String key : keys) {
            if(key.startsWith("directory."))
            {
               continue;
            }

            AssetEntry entry = AssetEntry.createAssetEntry(key);

            if(entry == null || !entry.isPhysicalTable() && !entry.isPartition()) {
               continue;
            }

            String id = entry.getPath();

            if(!id.contains("/")) {
               continue;
            }

            if(entry.isPartition()) {
               int idx = id.lastIndexOf("/");
               String source = id.substring(0, idx);
               String table = id.substring(idx + 1);

               if(Tool.equals(oldSourceName, source)) {
                  String nid = newSourceName + "/" + table;
                  entry.setPath(nid);
                  String nkey = key.replace(id, nid);
                  service.rename(key, nkey, OrganizationManager.getInstance().getCurrentOrgID());
               }

               continue;
            }

            int idx = id.indexOf("/");
            String source = id.substring(0, idx);
            String table = id.substring(idx + 1);

            // If the key is current sources's physical table, rename its info
            if(Tool.equals(source, osource)) {
               RenameInfo ninfo = new RenameInfo(oldSourceName, newSourceName, type);
               ninfo.setSource(table);
               List<AssetObject> entries = getDependencies(key);

               for(AssetObject obj : entries) {
                  dinfo.addRenameInfo(obj, ninfo);
               }

               infos.add(ninfo);
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to rename physical table dependencies", e);
      }
   }

   private static void fixSQLSourceDependency(String oldSourceName, String newSourceName,
                                              RenameDependencyInfo dinfo,
                                              boolean isDSFolder, List<RenameInfo> infos)
   {
      String id = getTabularAssetId(oldSourceName);
      List<AssetObject> entries = getDependencies(id);

      if(entries == null) {
         return;
      }

      int type = isDSFolder ? RenameInfo.SQL_TABLE | RenameInfo.DATA_SOURCE_FOLDER :
         RenameInfo.SQL_TABLE | RenameInfo.DATA_SOURCE | RenameInfo.PHYSICAL_TABLE;
      RenameInfo info = new RenameInfo(oldSourceName, newSourceName, type);

      for(AssetObject entry : entries) {
         dinfo.addRenameInfo(entry, info);
      }

      infos.add(info);
   }

   // For dependency storage should using asset entry's idenfifier as key, so create one key
   // for query and model name.
   public static String getAssetId(String name, AssetEntry.Type type) {
      return DependencyHandler.getAssetId(name, type);
   }

   public static String getTabularAssetId(String name) {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, name, null);

      return entry.toIdentifier();
   }

   public static RenameDependencyInfo createRenameDependency(String id, List<RenameInfo> infos) {
      List<AssetObject> deps = getDependencies(id);

      if(deps == null || deps.isEmpty()) {
         return null;
      }

      RenameDependencyInfo renameDependencyInfo = new RenameDependencyInfo();

      for(AssetObject assetObject : deps) {
         renameDependencyInfo.setRenameInfo(assetObject, infos);
      }

      return renameDependencyInfo;
   }

   // logic to fix binding worksheet.
   // Create rename info for ws table and columns.
   public static RenameDependencyInfo createRenameInfo(RuntimeWorksheet rws) {
      final Worksheet ws = rws.getWorksheet();
      final AssetEntry bentry = rws.getEntry();
      final String id = bentry.toIdentifier();
      return createRenameInfo(ws, bentry, id);
   }

   // logic to fix binding worksheet.
   // Create rename info for ws table and columns.
   public static RenameDependencyInfo createRenameInfo(Worksheet ws, AssetEntry bentry, String id) {
      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      // For report, it can only binding primary assembly of ws, so not add add other column
      // change infos to list.
      List<RenameInfo> reportInfos = new ArrayList<>();
      List<RenameInfo> assetInfos = new ArrayList<>();
      Assembly primary = ws.getPrimaryAssembly();
      Assembly[] assemblies = ws.getAssemblies();

      addAssemblyRenameInfo(primary, reportInfos, id, dinfo);

      if(!reportInfos.isEmpty()) {
         String path = bentry.getPath() + "/" + primary.getAbsoluteName();
         AssetEntry tableEntry = new AssetEntry(
            bentry.getScope(), AssetEntry.Type.TABLE, path, bentry.getUser());
         List<AssetObject> entries = getDependencies(tableEntry.toIdentifier());

         for(AssetObject entry : entries) {
            dinfo.setRenameInfo(entry, reportInfos);
         }
      }

      for(Assembly assembly : assemblies) {
         addAssemblyRenameInfo(assembly, assetInfos, id,dinfo);
      }

      List<AssetObject> entries = getDependencies(id);

      if(entries != null) {
         for(AssetObject entry : entries) {
            if(entry instanceof AssetEntry) {
               if(assetInfos.size() != 0) {
                  dinfo.setRenameInfo(entry, assetInfos);
               }
            }
         }
      }

      return dinfo;
   }

   public static void addAssemblyRenameInfo(Assembly assembly,
      List<RenameInfo> infos, String id, RenameDependencyInfo dinfo)
   {
      if(!(assembly instanceof AbstractWSAssembly)) {
         return;
      }

      AbstractWSAssembly ass = (AbstractWSAssembly) assembly;

      String nname = assembly.getName();
      String oname = ((AbstractWSAssembly) assembly).getOldName();

      // If copy one table, the copyed table should not have relation ship, so should not fix
      // columns.
      if(oname == null) {
         return;
      }

      if(oname != null && !oname.equals(nname)) {
         int type = RenameInfo.ASSET | RenameInfo.TABLE;

         if(assembly instanceof NamedGroupAssembly) {
            type |= RenameInfo.ASSET_NAMED_GROUP;
         }

         RenameInfo info = new RenameInfo(oname, nname, type, id);
         info.setPrimaryTable(ass.getInfo().isPrimary());
         infos.add(info);
      }

      if(ass instanceof AbstractTableAssembly) {
         ColumnSelection cols = ((AbstractTableAssembly) ass).getColumnSelection(false);

         for(int i = 0; i < cols.getAttributeCount(); i++) {
            DataRef ref = cols.getAttribute(i);

            if(ref instanceof ColumnRef) {
               ColumnRef col = (ColumnRef) ref;

               if(col.getOldName() != null && !Tool.equals(col.getOldName(), col.getDisplayName())) {
                  RenameInfo info = new RenameInfo(col.getOldName(), col.getDisplayName(),
                          RenameInfo.ASSET | RenameInfo.COLUMN, id, nname, col.getEntity());
                  info.setAlias(!Tool.isEmptyString(col.getAlias()));
                  info.setPrimaryTable(ass.getInfo().isPrimary());
                  infos.add(info);
               }
            }
         }

         if(!Tool.equals(ass.getOldName(), ass.getName())) {
            AssetEntry wentry = AssetEntry.createAssetEntry(id);
            AssetEntry tentry = new AssetEntry(wentry.getScope(), AssetEntry.Type.TABLE,
               wentry.getPath() + "/" + ass.getOldName(), wentry.getUser());
            List<AssetObject> entries = getDependencies(tentry.toIdentifier());

            if(entries != null && infos.size() != 0) {
               for(AssetObject entry : entries) {
                  dinfo.addRenameInfos(entry, infos);
               }
            }
         }
      }
   }

   protected static String getKey(RenameInfo info, boolean old) {
      String name = old ? info.getOldName() : info.getNewName();

      if(info.isCube() && name != null && name.startsWith(Assembly.CUBE_VS)) {
         name = name.substring(Assembly.CUBE_VS.length());

         return getAssetId(name, AssetEntry.Type.DATA_SOURCE);
      }

      if(info.isWorksheet()) {
         return name;
      }

      if(info.isTask()) {
         return getAssetId("/" + name, AssetEntry.Type.SCHEDULE_TASK);
      }

      if(info.isQuery() && info.isFolder()) {
         return getAssetId(info.getSource(), AssetEntry.Type.QUERY);
      }

      if(info.isTabularSource()) {
         return getTabularAssetId(name);
      }

      if(info.isLogicalModel()) {
         if(info.isSource()) {
            return getAssetId(getSourceUnique(info.getPrefix(), name), AssetEntry.Type.LOGIC_MODEL);
         }
         else if(info.isDataSource() || info.isDataSourceFolder()) {
            return getAssetId(getSourceUnique(name, info.getSource()), AssetEntry.Type.LOGIC_MODEL);
         }
      }

      if(info.isPhysicalTable()) {
         if(info.isSource()) {
            return getAssetId(getSourceUnique(info.getPrefix(), name), AssetEntry.Type.DATA_SOURCE);
         }
         else if(info.isDataSource() || info.isDataSourceFolder()) {
            return getAssetId(getSourceUnique(name, info.getSource()), AssetEntry.Type.DATA_SOURCE);
         }
      }

      if(info.isScriptFunction()) {
         return getAssetId(name, AssetEntry.Type.SCRIPT);
      }

      if(info.isVPM()) {
         return getAssetId(name, AssetEntry.Type.VPM);
      }

      if(info.isSqlTable()) {
         return getAssetId(name, AssetEntry.Type.DATA_SOURCE);
      }

      if(info.isHyperlink()) {
         if(info.isReportLink()) {
            return getAssetId(name, AssetEntry.Type.REPLET);
         }
         else {
            return name;
         }
      }

      if(info.isEmbedViewsheet()) {
         return name;
      }

      if(info.isDashboard()) {
         return name;
      }

      if(info.isTableStyle()) {
         return getAssetId(name, AssetEntry.Type.TABLE_STYLE);
      }

      if(info.isPartition()) {
         if(Tool.isEmptyString(info.getSource()) && name != null) {
            name = name.replace("^", "/");
            return getAssetId(name , AssetEntry.Type.PARTITION);
         }
         else {
            return getAssetId(name + "/" + info.getSource(), AssetEntry.Type.PARTITION);
         }
      }

      return getAssetId(name, info.isQuery() ? AssetEntry.Type.QUERY : AssetEntry.Type.LOGIC_MODEL);
   }

   protected static String getOldKey(RenameInfo info) {
      return getKey(info, true);
   }

   private static String getNewKey(RenameInfo info) {
       return getKey(info, false);
   }

   public static String getSourceUnique(String prefix, String source) {
      return DependencyHandler.getUniqueSource(prefix, source);
   }

   protected static void renameDepStorage(RenameInfo rinfo) {
      if(!renameStorage(rinfo)) {
         return;
      }

      try {
         DependencyStorageService service = DependencyStorageService.getInstance();
         service.rename(getOldKey(rinfo), getNewKey(rinfo), rinfo.getOrganizationId());
      }
      catch(Exception e) {
         LOG.error("Failed to rename dependency storage: ", e);
      }
   }

   // For query, query name will affect to change storage
   // For logicmodel, datasource name and source name will affect.
   // For physical table, datasource name and source name will affect.
   // For sql table, datasource folder, datasource name and source name will affect.
   // For rest source, datasource folder and datasource name will affect.
   private static boolean renameStorage(RenameInfo info) {
      if((info.isWorksheet() || info.isQuery()) && info.isSource() ||
         info.isWorksheet() && info.isTable())
      {
         return true;
      }

      if(info.isLogicalModel() || info.isCube()) {
         return true;
      }

      if(info.isPhysicalTable() && (info.isSource() || info.isDataSource())) {
         return true;
      }

      if(info.isSqlTable() && (info.isSource() || info.isDataSource() || info.isDataSourceFolder()))
      {
         return true;
      }

      if(info.isPartition() && info.isDataSourceFolder()) {
         return true;
      }

      if(info.isRest() && (info.isDataSourceFolder() || info.isDataSource())) {
         return true;
      }

      if(info.isVPM()) {
         return true;
      }

      if(info.isDashboard()) {
         return true;
      }

      return info.isHyperlink() || info.isEmbedViewsheet() ||
         info.isTableStyle() || info.isScriptFunction() ||
         info.isCube() || info.isTask();
   }

   public static List<AssetObject> getQueryDependencies(String qname) {
      return DependencyTool.getQueryDependencies(qname);
   }

   public static List<AssetObject> getModelDependencies(String name) {
      return DependencyTool.getModelDependencies(name);
   }

   public static List<AssetObject> getPartitionDependencies(String name) {
      return DependencyTool.getPartitionDependencies(name);
   }

   public static List<AssetObject> getVPMDependencies(String name) {
      return DependencyTool.getVpmDependencies(name);
   }

   public static List<AssetObject> getDependencies(String entryId) {
      return DependencyTool.getDependencies(entryId);
   }

   protected void replaceCDATANode(Node elem, String value) {
      replaceElementCDATANode(elem, value);
   }

   public static void replaceElementCDATANode(Node elem, String value) {
      Document doc = elem.getOwnerDocument();
      Node node = doc.createCDATASection(value != null ? value : "");
      NodeList nlist = elem.getChildNodes();
      int len = nlist.getLength(); // optimize

      if(len == 0) {
         return;
      }

      while(elem.hasChildNodes()) {
         elem.removeChild(elem.getFirstChild());
      }

      elem.appendChild(node);
   }

   // property have two type: key---value   name----value.
   protected void replacePropertyNode(Element elem, String key, String oname, String nname) {
      replacePropertyNode(elem, key, oname, nname, false);
   }

   // the node assetentry/
   protected void replacePropertyNode0(Element elem, String key, String oname, String nname,
                                       boolean isTotal)
   {
      replacePropertyNode(elem, key, oname, nname, isTotal);
   }

   protected void replacePropertyNode1(Element elem, String key, String oname, String nname,
                                       boolean isTotal)
   {
      replacePropertyNode(elem, key, oname, nname, isTotal);
   }

   // the node assetentry/properties/
   protected void replacePropertyNode(Element elem, String key, String oname, String nname,
                                      boolean isTotal)
   {
      NodeList list = getChildNodes(elem, ".//properties/property | ./property");

      for(int i = 0; i < list.getLength(); i++){
         Element prop = (Element) list.item(i);
         Element keyElem = Tool.getChildNodeByTagName(prop, "key");

         if(keyElem == null) {
            keyElem = Tool.getChildNodeByTagName(prop, "name");
         }

         String keyVal = Tool.getValue(keyElem);

         if(!Tool.equals(key, keyVal)) {
            continue;
         }

         Element valueElem = Tool.getChildNodeByTagName(prop, "value");
         String val = Tool.getValue(valueElem);

         if(isTotal && Tool.equals(val, oname)) {
            replaceCDATANode(valueElem, nname);
         }
         else if(!isTotal && val != null && (Tool.equals(val, oname) ||
            val.startsWith(oname + "/") || val.startsWith(oname + "^_^")) && !Tool.equals(val, nname))
         {
            replaceCDATANode(valueElem, val.replace(oname, nname));
         }
      }
   }

   protected String getPropertyValue(Element elem, String key) {
      NodeList list = getChildNodes(elem, "./property");

      for(int i = 0; i < list.getLength(); i++){
         Element prop = (Element) list.item(i);
         Element keyElem = Tool.getChildNodeByTagName(prop, "key");

         if(keyElem == null) {
            keyElem = Tool.getChildNodeByTagName(prop, "name");
         }

         String keyVal = Tool.getValue(keyElem);

         if(Tool.equals(key, keyVal)) {
            Element valueElem = Tool.getChildNodeByTagName(prop, "value");
            return Tool.getValue(valueElem);
         }
      }

      return null;
   }

   // child node have cdata value
   protected boolean replaceChildValue(Element elem, String key, String oname, String nname,
                                       boolean isTotal)
   {
      return replaceChildValue(elem, key, oname, nname, isTotal, false);
   }

   // child node have cdata value
   protected boolean replaceChildValue(Element elem, String key, String oname, String nname,
                                       boolean isTotal, boolean autoCreateRemove)
   {
      Element child = Tool.getChildNodeByTagName(elem, key);
      String val = Tool.getValue(child);

      if(isTotal && Tool.equals(val, oname)) {
         if(child == null) {
            if(autoCreateRemove) {
               child = elem.getOwnerDocument().createElement(key);
               child.appendChild(elem.getOwnerDocument().createCDATASection(nname));
               elem.appendChild(child);
            }
            else {
               return false;
            }
         }
         else {
            replaceCDATANode(child, nname);
         }

         return true;
      }
      else if(!isTotal && val != null && val.contains(oname)) {
         replaceCDATANode(child, val.replace(oname, nname));

         return true;
      }

      return false;
   }

   protected boolean replaceAttribute(Element elem, String key, String oname, String nname,
                                      boolean isTotal)
   {
      String attr = Tool.getAttribute(elem, key);
      boolean success = false;

      if(isTotal && Tool.equals(attr, oname)) {
         setAttribute(elem, key, nname);
         success = true;
      }
      else if(!isTotal && attr != null && attr.contains(oname)) {
         setAttribute(elem, key, attr.replace(oname, nname));
         success = true;
      }

      return success;
   }

   protected void replaceOldName(Element elem, String oname, String nname) {
      if(elem == null) {
         return;
      }

      String val = Tool.getValue(elem);

      if(val != null && val.contains("/" + oname)) {
         replaceCDATANode(elem, val.replace(oname, nname));
      }
   }

   protected void replaceDataSource(Element elem, String oname, String nname) {
      if(elem == null) {
         return;
      }

      String val = Tool.getValue(elem);
      int index = val.indexOf("/");

      if(Tool.equals(val, oname) || index != -1 && val.startsWith(oname + "/")) {
         replaceCDATANode(elem, nname + val.substring(oname.length()));
      }
   }

   private void setAttribute(Element elem, String name, String value) {
      if(value != null) {
         elem.setAttribute(name, value);
      }
      else {
         elem.removeAttribute(name);
      }
   }

   private static XRepository getXRepository() throws RemoteException {
      return XFactory.getRepository();
   }

   public static void updateRenameInfos(Object rid, AssetObject assetEntry,
                                        List<RenameInfo> renameInfos,
                                        Map<Object, List<RenameDependencyInfo>> renameInfoMap)
   {
      if(renameInfos.isEmpty()) {
         return;
      }

      List<RenameDependencyInfo> dependencyInfos = renameInfoMap.get(rid);

      if(dependencyInfos == null) {
         dependencyInfos = new ArrayList<>();
      }

      RenameDependencyInfo dependencyInfo = new RenameDependencyInfo();
      dependencyInfo.setRenameInfo(assetEntry, renameInfos);
      dependencyInfos.add(dependencyInfo);

      renameInfoMap.put(rid, dependencyInfos);
   }

   public static void fixRenameDepEntry(String rid, AssetObject oldEntry, AssetObject newEntry,
                                        Map<Object, List<RenameDependencyInfo>> renameInfoMap)
   {
      List<RenameDependencyInfo> renameDependencyInfos = renameInfoMap.get(rid);

      if(oldEntry == null || oldEntry.equals(newEntry)) {
         return;
      }

      for(RenameDependencyInfo dependencyInfo : renameDependencyInfos) {
         List<RenameInfo> infos = dependencyInfo.getRenameInfo(oldEntry);

         if(infos != null)  {
            dependencyInfo.removeRenameInfo(oldEntry);
            dependencyInfo.setRenameInfo(newEntry, infos);
         }
      }
   }

   public static void initTableColumnOldNames(AbstractTableAssembly ass, boolean open) {
      ColumnSelection cols = ass.getColumnSelection(false);

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         DataRef ref = cols.getAttribute(i);

         if(ref instanceof ColumnRef) {
            ColumnRef col = (ColumnRef) ref;
            String oname = col.getOldName();
            col.setOldName(col.getDisplayName());

            if(open) {
               col.setLastOldName(col.getDisplayName());
            }
            else {
               col.setLastOldName(oname);
            }
         }
      }
   }

   public static NodeList getChildNodes(Element doc, String path) {
      return DependencyTool.getChildNodes(xpath, doc, path);
   }

   protected Element getChildNode(Element doc, String path) {
      return DependencyTool.getChildNode(xpath, doc, path);
   }

   protected boolean getBoolean(Element doc, String path) {
      try {
         XPathExpression expr = xpath.compile(path);
         return (boolean) expr.evaluate(doc, XPathConstants.BOOLEAN);
      }
      catch(XPathExpressionException ignore) {
         // ignored
      }

      return false;
   }

   protected String getString(Element doc, String path) {
      return DependencyTool.getString(doc, path);
   }

   protected Element getFirstChildNode(Element doc, String path) {
      try {
         XPathExpression expr = xpath.compile(path);
         NodeList list = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

         if(list.getLength() == 0) {
            return null;
         }

         for(int i = 0; i < list.getLength(); i++) {
            if(list.item(i) instanceof Element) {
               return (Element) list.item(i);
            }
         }
      }
      catch(XPathExpressionException ignore) {
         // ignored
      }

      return null;
   }

   /**
    * Get the data source
    */
   protected XDataSource getDataSource(String sourceName, String additional) {
      XDataSource dataSource = getRepository().getDataSource(sourceName, true);

      if(additional != null && dataSource instanceof AdditionalConnectionDataSource) {
         AdditionalConnectionDataSource<?> jds = (AdditionalConnectionDataSource<?>) dataSource;

         if(!XUtil.OUTER_MOSE_LAYER_DATABASE.equals(additional)) {
            AdditionalConnectionDataSource<?> source = jds.getDataSource(additional);
            return source == null ? dataSource : source;
         }
      }

      return dataSource;
   }

   /**
    * Get repository.
    */
   protected XRepository getRepository() {
      try {
         return XFactory.getRepository();
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }

      return null;
   }

   public File getAssetFile() {
      return assetFile;
   }

   public void setAssetFile(File assetFile) {
      this.assetFile = assetFile;
   }

   protected Document getAssetFileDoc() throws Exception {
      try(InputStream input = new FileInputStream(getAssetFile())) {
         if(input.available() > 0) {
            Document document = Tool.parseXML(input, "UTF-8", false, false);
            Element root = getChildNode(document.getDocumentElement(), "./assembly");

            if(root != null) {
               NodeList linkList = getChildNodes(root, ".//Hyperlink");

               for(int i = 0; i < linkList.getLength(); i++) {
                  String link = Tool.getAttribute((Element) linkList.item(i), "Link");

                  if(link != null) {
                     ((Element) linkList.item(i)).setAttribute("Link",
                                                               Hyperlink.handleAssetLinkOrgMismatch(link));
                  }
               }
            }

            return document;
         }
      }

      return null;
   }

   /**
    * Get RenameDependencyInfo for the extend models when the base model name is changed.
    * @param model base model.
    * @param oldBaseModelName old name of the base model.
    * @param newBaseModelName new name of the base model.
    *
    * @return extend Model DependencyInfo
    */
   public static RenameDependencyInfo createExtendModelDependencyInfo(XLogicalModel model,
                                                                      String oldBaseModelName,
                                                                      String newBaseModelName)
   {
      if(model == null) {
         return null;
      }

      String[] extendModelNames = model.getLogicalModelNames();

      if(extendModelNames == null) {
         return null;
      }

      RenameDependencyInfo renameDependencyInfo = new RenameDependencyInfo();
      String datasource = model.getDataSource();

      for(String extendModelName : extendModelNames) {
         List<AssetObject> modelDependencies =
            getModelDependencies(model.getDataSource() + "/" + oldBaseModelName + "/" + extendModelName);

         if(modelDependencies == null) {
            continue;
         }

         int type = RenameInfo.LOGIC_MODEL | RenameInfo.SOURCE;
         RenameInfo rinfo = new RenameInfo(oldBaseModelName +  XUtil.DATAMODEL_PATH_SPLITER + extendModelName,
                                           newBaseModelName +  XUtil.DATAMODEL_PATH_SPLITER + extendModelName, type);
         rinfo.setPrefix(datasource);
         rinfo.setModelFolder(model.getFolder());

         for(AssetObject modelDependency : modelDependencies) {
            renameDependencyInfo.addRenameInfo(modelDependency, rinfo);
         }
      }

      return renameDependencyInfo;
   }

   private File assetFile;

   protected static final XPath xpath = XPathFactory.newInstance().newXPath();
   private static final Logger LOG = LoggerFactory.getLogger(DependencyTransformer.class);
}
