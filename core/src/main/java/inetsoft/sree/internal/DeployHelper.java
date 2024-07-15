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
package inetsoft.sree.internal;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.UpdateDependencyHandler;
import inetsoft.util.Tool;
import inetsoft.util.dep.*;
import inetsoft.web.admin.deploy.*;

import java.io.File;
import java.util.*;

public class DeployHelper {
   public DeployHelper(DeploymentInfo info,
                       ImportTargetFolderInfo targetFolderInfo)
   {
      super();
      this.filePath = info.getUnzipFolderPath();
      this.files = info.getFiles();
      this.names = info.getNames();
      this.targetFolder = targetFolderInfo != null ? targetFolderInfo.getTargetFolder() : null;

      if(targetFolder != null && !targetFolder.isRoot()) {
         this.commonPrefixFolder = getImportCommonPrefix(info);
      }

      this.fileMap = getFileMap(files, names);
      List<XAsset> assets = getAssets(files, names);
      prepareDependenciesByFile(assets);
   }

   /**
    * @param filePath  import unzip temp path.
    * @param files     import file array.
    * @param names     temp file name -> asset identifier file name(import zip file).
    */
   public DeployHelper(PartialDeploymentJarInfo info, String filePath, File[] files,
                       ImportTargetFolderInfo targetFolderInfo,
                       Map<String, String> names)
   {
      super();
      this.filePath = filePath;
      this.files = files;
      this.names = names;
      this.targetFolder = targetFolderInfo != null ? targetFolderInfo.getTargetFolder() : null;

      if(targetFolder != null && !targetFolder.isRoot()) {
         this.commonPrefixFolder = getImportCommonPrefix(info);
      }

      this.fileMap = getFileMap(files, names);
      List<XAsset> assets = getAssets(files, names);
      prepareDependenciesByFile(assets);
   }

   /**
    * key -> identifer or filename, value -> file
    */
   public Map<String, File> getFileMap() {
      return fileMap;
   }

   /**
    * key -> asset, value -> file to transform if rename or change folder.
    */
   public Map<XAsset, File> getTransformFileMap() {
      return transformFileMap;
   }

   /**
    * key -> old asset, value -> new asset after renaming or change folder.
    */
   public Map<AssetObject, AssetObject> getChangeAssetMap() {
      return changeAssetMap;
   }

   /**
    * key -> asset, value -> assets which the key depends on.
    */
   public Map<XAsset, Set<AssetObject>> getDependenciesMap() {
      return dependenciesMap;
   }

   public TopologicalSortGraph<AssetObject> getGraph() {
      return graph;
   }

   public AssetEntry getTargetFolder() {
      return targetFolder;
   }

   public AssetEntry getCommonPrefixFolder() {
      return commonPrefixFolder;
   }

   public File getTransformFile(XAsset asset) {
      return transformFileMap.get(asset);
   }

   public Set<AssetObject> getDependencies(XAsset asset) {
      return dependenciesMap.get(asset);
   }

   public static List<XAsset> getAssets(File[] files, Map<String, String> names) {
      List<XAsset> assetList = new ArrayList<>();

      if(names == null || files == null) {
         return assetList;
      }

      for(File relationFile : files) {
         String fileName = getFileName(relationFile, names);
         XAsset asset = getAssetByXAssetIdentifier(fileName);

         if(asset == null) {
            continue;
         }

         assetList.add(asset);
      }

      return assetList;
   }

   public static XAsset getAsset(File file, Map<String, String> names) {
      String fileName = getFileName(file, names);
      return getAssetByXAssetIdentifier(fileName);
   }

   /**
    * Get the file map, key -> file name, value -> file.
    */
   private Map<String, File> getFileMap(File[] files, Map<String, String> names) {
      Map<String, File> fileMap = new HashMap<>();

      for(File file : files) {
         String fileName = getFileName(file, names);
         XAsset asset = getAssetByFile(file, names);

         if(asset != null) {
            fileMap.put(getAssetFileIdentifier(asset), file);
         }
         else if(fileName != null && fileName.startsWith("__")) {
            fileMap.put(fileName, file);
         }
      }

      return fileMap;
   }

   /**
    * Read the asset files to prepare the dependencies, dependencies graph,
    * and the transform file map.
    */
   private void prepareDependenciesByFile(List<XAsset> assets) {
      graph = new TopologicalSortGraph<>();

      for(XAsset asset : assets) {
         String identifier = getAssetFileIdentifier(asset);
         File file = fileMap.get(identifier);

         if(file == null) {
            continue;
         }

         AssetObject assetObject = getAssetObjectByAsset(asset);
         TopologicalSortGraph<AssetObject>.GraphNode node = graph.getNodeByData(assetObject);

         if(node == null) {
            node = graph.new GraphNode(assetObject);
            graph.addNode(node);
         }

         File transformFile = file;
         transformFileMap.put(asset, transformFile);
         Set<AssetObject> dependencies = UpdateDependencyHandler
            .getAssetSupportTargetDependencies(transformFile, asset, assets);

         if(assetObject instanceof AssetEntry && ((AssetEntry) assetObject).isExtendedModel()) {
            AssetEntry entry = (AssetEntry) assetObject;
            AssetEntry.Type type = entry.isExtendedPartition() ?
               AssetEntry.Type.EXTENDED_PARTITION : AssetEntry.Type.EXTENDED_LOGIC_MODEL;
            AssetEntry base =
               new AssetEntry(entry.getScope(), type, entry.getParentPath(), entry.getUser());
            TopologicalSortGraph<AssetObject>.GraphNode childrenNode = graph.getNodeByData(base);

            if(childrenNode == null) {
               childrenNode = graph.new GraphNode(base);
            }

            node.addChild(childrenNode);
         }

         if(dependencies == null) {
            continue;
         }

         if(!shouldNotTransform(asset)) {
            dependenciesMap.put(asset, dependencies);
         }

         for(AssetObject dependency : dependencies) {
            TopologicalSortGraph<AssetObject>.GraphNode childrenNode =
               graph.getNodeByData(dependency);

            if(childrenNode == null) {
               childrenNode = graph.new GraphNode(dependency);
            }

            node.addChild(childrenNode);
         }
      }
   }

   /**
    * Get file name.
    */
   public static String getFileName(File file, Map<String, String> names) {
      String filename = file.getName();
      filename = names.get(filename);
      filename = Tool.replaceAll(filename, "^_^", "/");

      return filename;
   }

   public static XAsset getAssetByFile(File file, Map<String, String> names) {
      String name = getFileName(file, names);

      return getAssetByXAssetIdentifier(name);
   }

   public static String getAssetFileIdentifier(XAsset asset) {
      return asset.toIdentifier();
   }

   public static XAsset getAssetByXAssetIdentifier(String identifier) {
      if(identifier == null) {
         return null;
      }

      int idx = identifier.indexOf('_');

      if(idx < 0) {
         return null;
      }

      String type = identifier.substring(0, idx);
      List<?> types = XAssetUtil.getXAssetTypes(true);

      if(!types.contains(type)) {
         return null;
      }

      return XAssetUtil.createXAsset(identifier.substring(idx + 1), true);
   }

   public static AssetObject getAssetObjectByAsset(XAsset asset) {
      if(asset instanceof AbstractSheetAsset) {
         return ((AbstractSheetAsset) asset).getAssetEntry();
      }
      else if(asset instanceof XDataSourceAsset) {
         return new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.DATA_SOURCE, asset.getPath(), asset.getUser());
      }
      else if(asset instanceof XPartitionAsset) {
         String dataSource = ((XPartitionAsset) asset).getDataSource();
         String parentPartition = ((XPartitionAsset) asset).getParentPartition();
         AssetEntry.Type type = Tool.isEmptyString(parentPartition) ?
            AssetEntry.Type.PARTITION : AssetEntry.Type.EXTENDED_PARTITION;
         AssetEntry partition = new AssetEntry(AssetRepository.QUERY_SCOPE,
            type, asset.getPath().replace('^', '/'), asset.getUser());

         if(!Tool.isEmptyString(dataSource)) {
            partition.setProperty("prefix", dataSource);
         }

         if(!Tool.isEmptyString(parentPartition)) {
            partition.setProperty("parentPartition", parentPartition);
         }

         String modelFolder = ((XPartitionAsset) asset).getModelFolder();

         if(!Tool.isEmptyString(modelFolder)) {
            partition.setProperty("modelFolder", modelFolder);
         }

         return partition;
      }
      else if(asset instanceof XLogicalModelAsset) {
         String dataSource = ((XLogicalModelAsset) asset).getDataSource();
         String parentLogicalModel = ((XLogicalModelAsset) asset).getParentModelName();
         AssetEntry.Type type = Tool.isEmptyString(parentLogicalModel) ?
            AssetEntry.Type.LOGIC_MODEL : AssetEntry.Type.EXTENDED_LOGIC_MODEL;
         AssetEntry logicalModel =  new AssetEntry(AssetRepository.QUERY_SCOPE,
            type, asset.getPath().replace('^', '/'), asset.getUser());

         if(!Tool.isEmptyString(dataSource)) {
            logicalModel.setProperty("prefix", dataSource);
         }

         if(!Tool.isEmptyString(parentLogicalModel)) {
            logicalModel.setProperty("parentPartition", parentLogicalModel);
         }

         String modelFolder = ((XLogicalModelAsset) asset).getModelFolder();

         if(!Tool.isEmptyString(modelFolder)) {
            logicalModel.setProperty("modelFolder", modelFolder);
         }

         return logicalModel;
      }
      else if(asset instanceof VirtualPrivateModelAsset) {
         String dataSource = ((VirtualPrivateModelAsset) asset).getDataSource();

         AssetEntry vpm =  new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.VPM, asset.getPath().replace('^', '/'), asset.getUser());

         if(!Tool.isEmptyString(dataSource)) {
            vpm.setProperty("dataSource", dataSource);
         }

         return vpm;
      }
      else if(asset instanceof ScheduleTaskAsset) {
         return new AssetEntry(AssetRepository.GLOBAL_SCOPE,
            AssetEntry.Type.SCHEDULE_TASK, asset.getPath(), asset.getUser());
      }
      else if(asset instanceof DashboardAsset) {
         return new AssetEntry(AssetRepository.GLOBAL_SCOPE,
            AssetEntry.Type.DASHBOARD, asset.getPath(), asset.getUser());
      }

      return null;
   }

   public static boolean shouldNotTransform(XAsset asset) {
      return asset instanceof XPartitionAsset ||  asset instanceof VirtualPrivateModelAsset;
   }

   public static AssetEntry getImportCommonPrefix(PartialDeploymentJarInfo info) {
      AssetEntry rootFolder = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.REPOSITORY_FOLDER, "/", null);

      if(info == null) {
         return rootFolder;
      }

      List<XAsset> entryAssets = DeployUtil.getEntryAssets(info.getSelectedEntries());

      if(entryAssets == null || entryAssets.size() == 0) {
         return rootFolder;
      }

      XAsset firstAsset = entryAssets.get(0);
      String commonPrefixFolderPath = getAssetFolder(firstAsset.getPath());
      IdentityID commonPrefixFolderUser = firstAsset.getUser();

      for(int i = 1; i < entryAssets.size(); i++) {
         if(Tool.isEmptyString(commonPrefixFolderPath) || "/".endsWith(commonPrefixFolderPath) &&
            Tool.isEmptyString(commonPrefixFolderUser.name))
         {
            return rootFolder;
         }

         XAsset asset = entryAssets.get(i);

         if(!Tool.equals(asset.getUser(), commonPrefixFolderUser)) {
            return rootFolder;
         }

         String[] splitFolder = getAssetFolder(asset.getPath()).split("/");
         String[] commonPrefixSplit = commonPrefixFolderPath.split("/");
         int index;

         for(index = 0; index < splitFolder.length && index < commonPrefixSplit.length; index++) {
            if(!Tool.equals(splitFolder[index], commonPrefixSplit[index])) {
               break;
            }
         }

         StringBuilder commonPrefixPath = new StringBuilder();

         for(int j = 0; j < index; j++) {
            commonPrefixPath.append(commonPrefixSplit[j]);

            if(j < index - 1) {
               commonPrefixPath.append("/");
            }
         }

         commonPrefixFolderPath = commonPrefixPath.toString();
      }

      int scope = Tool.isEmptyString(commonPrefixFolderUser.name) ? AssetRepository.GLOBAL_SCOPE :
         AssetRepository.USER_SCOPE;

      return new AssetEntry(scope, AssetEntry.Type.REPOSITORY_FOLDER,
         commonPrefixFolderPath, commonPrefixFolderUser);
   }

   public static AssetEntry getImportCommonPrefix(DeploymentInfo info) {
      AssetEntry rootFolder = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.REPOSITORY_FOLDER, "/", null);

      if(info == null) {
         return rootFolder;
      }

      List<XAsset> entryAssets = DeployUtil.getEntryAssets(info.getSelectedEntries());

      if(entryAssets == null || entryAssets.size() == 0) {
         return rootFolder;
      }

      XAsset firstAsset = entryAssets.get(0);
      String commonPrefixFolderPath = getAssetFolder(firstAsset.getPath());
      IdentityID commonPrefixFolderUser = firstAsset.getUser();
      String userName = commonPrefixFolderUser == null ? null : commonPrefixFolderUser.name;

      for(int i = 1; i < entryAssets.size(); i++) {
         if(Tool.isEmptyString(commonPrefixFolderPath) || "/".endsWith(commonPrefixFolderPath) &&
            Tool.isEmptyString(userName))
         {
            return rootFolder;
         }

         XAsset asset = entryAssets.get(i);

         if(!Tool.equals(asset.getUser(), commonPrefixFolderUser)) {
            return rootFolder;
         }

         String[] splitFolder = getAssetFolder(asset.getPath()).split("/");
         String[] commonPrefixSplit = commonPrefixFolderPath.split("/");
         int index;

         for(index = 0; index < splitFolder.length && index < commonPrefixSplit.length; index++) {
            if(!Tool.equals(splitFolder[index], commonPrefixSplit[index])) {
               break;
            }
         }

         StringBuilder commonPrefixPath = new StringBuilder();

         for(int j = 0; j < index; j++) {
            commonPrefixPath.append(commonPrefixSplit[j]);

            if(j < index - 1) {
               commonPrefixPath.append("/");
            }
         }

         commonPrefixFolderPath = commonPrefixPath.toString();
      }

      int scope = commonPrefixFolderUser == null || commonPrefixFolderUser != null &&
         Tool.isEmptyString(userName) ? AssetRepository.GLOBAL_SCOPE :
         AssetRepository.USER_SCOPE;

      return new AssetEntry(scope, AssetEntry.Type.REPOSITORY_FOLDER,
         commonPrefixFolderPath, commonPrefixFolderUser);
   }

   public static String getAssetFolder(String path) {
      if(path != null && path.startsWith(Tool.MY_DASHBOARD + "/")) {
         path = path.substring((Tool.MY_DASHBOARD + "/").length());
      }

      return SUtil.getFolder(path);
   }

   private String filePath;
   private File[] files;
   private Map<String, String> names;
   private AssetEntry targetFolder;
   private AssetEntry commonPrefixFolder;
   private TopologicalSortGraph<AssetObject> graph = new TopologicalSortGraph<>();
   private Map<String, File> fileMap = new HashMap<>();
   private Map<XAsset, File> transformFileMap = new HashMap<>();
   private Map<AssetObject, AssetObject> changeAssetMap = new HashMap<>();
   private Map<XAsset, Set<AssetObject>> dependenciesMap = new HashMap<>();
}