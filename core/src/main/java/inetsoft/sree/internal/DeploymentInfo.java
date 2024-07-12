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
package inetsoft.sree.internal;

import inetsoft.sree.internal.sync.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.UpdateDependencyHandler;
import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import inetsoft.util.dep.*;
import inetsoft.web.admin.deploy.*;
import inetsoft.web.admin.deploy.PartialDeploymentJarInfo.RequiredAsset;
import inetsoft.web.admin.deploy.PartialDeploymentJarInfo.SelectedAsset;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.util.*;

public class DeploymentInfo {
   public DeploymentInfo(PartialDeploymentJarInfo jarInfo, ImportJarProperties properties) {
      this(jarInfo, properties.names(), properties.unzipFolderPath());
      this.properties = properties;
   }

   public DeploymentInfo(PartialDeploymentJarInfo jarInfo, Map<String, String> names, String filePath) {
      super();

      this.jarInfo = jarInfo;
      this.onames = names;
      this.filePath = filePath;
      this.files = loadFiles();
      onames = processDcNames(onames);

      if(!jarInfo.isJarFileTransformed()) {
         processDedendencies();
      }

      processNames(onames);
      this.selectedEntries = processSelectedEntries();
      this.dependentAssets = processDependentAssets();

      if(!jarInfo.isJarFileTransformed()) {
         transform();
      }

      jarInfo.setJarFileTransformed(true);
   }

   public PartialDeploymentJarInfo getJarInfo() {
      return jarInfo;
   }

   public File[] getFiles() {
      return files;
   }

   public List<SelectedAsset> getSelectedEntries() {
      return selectedEntries;
   }

   public List<RequiredAsset> getDependentAssets() {
      return dependentAssets;
   }

   public ImportJarProperties getProperties() {
      return properties;
   }

   public Map<String, String> getNames() {
      return nnames;
   }

   private Map<String, List<String>> getDependeciesMap() {
      return jarInfo.getDependeciesMap();
   }

   private Map<String, String> getQueryFolderMap() {
      return jarInfo.getQueryFolderMap();
   }

   public String getUnzipFolderPath() {
      return filePath;
   }

   private List<SelectedAsset> processSelectedEntries() {
      List<SelectedAsset> list = jarInfo.getSelectedEntries();
      List<SelectedAsset> nlist = new ArrayList<>();
      List<?> types = XAssetUtil.getXAssetTypes(true);

      for(int i = 0; i < list.size(); i++) {
         SelectedAsset asset = list.get(i);

         if(XQueryAsset.XQUERY.equals(asset.getType()) &&
            convertedNameMap.containsKey(getQueryFileName(asset.getType(), asset.getPath())))
         {
            SelectedAsset nasset = new SelectedAsset();
            nasset.setType(WorksheetAsset.WORKSHEET);
            String path = asset.getPath();
            String queryFolder = getQueryFolderMap().get(path);

            if(!StringUtils.isEmpty(queryFolder)) {
               StringBuilder builder = new StringBuilder();
               builder.append(queryFolder);
               builder.append("/");
               builder.append(path);
               path = builder.toString();
            }

            nasset.setPath(path);
            nasset.setIcon(DeployUtil.getWorksheetIconPath(Worksheet.TABLE_ASSET));
            nlist.add(nasset);
         }
         else  if(types.contains(asset.getType())) {
            nlist.add(asset);
         }
      }

      return nlist;
   }

   private List<RequiredAsset> processDependentAssets() {
      List<RequiredAsset> list = jarInfo.getDependentAssets();
      List<RequiredAsset> nlist = new ArrayList<>();
      List<?> types = XAssetUtil.getXAssetTypes(true);

      for(int i = 0; i < list.size(); i++) {
         RequiredAsset asset = list.get(i);

         if(XQueryAsset.XQUERY.equals(asset.getType()) &&
            convertedNameMap.containsKey(getQueryFileName(asset.getType(), asset.getPath())))
         {
            RequiredAsset nasset = new RequiredAsset();
            nasset.setType(WorksheetAsset.WORKSHEET);
            String path = asset.getPath();
            String queryFolder = getQueryFolderMap().get(path);

            if(!StringUtils.isEmpty(queryFolder)) {
               StringBuilder builder = new StringBuilder();
               builder.append(queryFolder);
               builder.append("/");
               builder.append(path);
               path = builder.toString();
            }

            nasset.setPath(path);
            // to do, typeDescription
            // to do, detailDescription
            nlist.add(nasset);
         }
         else if(types.contains(asset.getType())) {
            nlist.add(asset);
         }
      }

      return nlist;
   }

   private String getQueryFileName(String type, String path) {
      if(XQueryAsset.XQUERY.equals(type)) {
         StringBuilder builder = new StringBuilder();
         builder.append(XQueryAsset.XQUERY);
         builder.append("_");
         builder.append(XQueryAsset.class.getName());
         builder.append("^");
         builder.append(path);
         return builder.toString();
      }

      return null;
   }

   private void processDedendencies() {
      Map dependeciesMap = jarInfo.getDependeciesMap();
      Map queryFolderMap = jarInfo.getQueryFolderMap();

      for(int i = 0; files != null && i < files.length; i++) {
         QueryDependenciesFinder.collectDependencies(
            files[i], onames, dependeciesMap, queryFolderMap);
      }
   }

   private Map<String, String> processDcNames(Map<String, String> onames) {
      Map<String, String> names = new HashMap<>();

      for(Map.Entry<String, String> entry : onames.entrySet()) {
         String fileName = entry.getKey();
         String value = entry.getValue().replace("^_^", "/");

         if(value.indexOf("inetsoft.common.dep.") != -1) {
            value = value.replace("inetsoft.common.dep.", "inetsoft.util.dep.");
         }

         names.put(fileName, value);
      }

      return names;
   }

   private void processNames(Map<String, String> names) {
      List<String> wsList = getAllWorksheetNames(names);

      for(Map.Entry<String, String> entry : names.entrySet()) {
         String fileName = entry.getKey();
         String value = entry.getValue().replace("^_^", "/");
         int orgIdx = StringUtils.ordinalIndexOf(value, "^", 5);

         // Strip orgID from identifier
         if(orgIdx != -1) {
            value = value.substring(0, orgIdx);
         }

         // XQUERY_inetsoft.common.dep.XQueryAsset^All Sales
         // to
         // WORKSHEET_inetsoft.common.dep.WorksheetAsset^1^2^__NULL__^Sample Queries^_^All Sales // Sample Queries is query folder.
         if(value != null && value.startsWith(XQueryAsset.XQUERY)) {
            value = Tool.replace(value, XQueryAsset.XQUERY, WorksheetAsset.WORKSHEET);
            String[] vals = Tool.split(value, '^');
            String qname = vals[1];
            File file = getFile(files, fileName);
            int queryType = QueryToWsConverter.getQueryType(file);

            if(QueryToWsConverter.isIgnoredQuery(file)) {
               nnames.put(fileName, entry.getValue());
               continue;
            }

            queryTypeMap.put(file, queryType);
            queryFileMap.put(qname, file);
            String folder = getQueryFolderMap().get(qname);

            StringBuilder builder = new StringBuilder();
            builder.append(WorksheetAsset.WORKSHEET);
            builder.append("_");
            builder.append(WorksheetAsset.class.getName());
            builder.append("^1^2^__NULL__^");

            if(!StringUtils.isEmpty(folder)) {
               builder.append(folder);
               builder.append(AssetEntry.PATH_ARRAY_SEPARATOR);
            }

            builder.append(qname);
            String wsName = builder.toString();
            wsName = autoRename(wsName, wsList, 0);
            convertedNameMap.put(entry.getValue(), wsName);
            queryFiles.add(file);
            nnames.put(fileName, wsName);
         }
         else {
            nnames.put(fileName, entry.getValue());
         }
      }
   }

   private void transform() {
      transformQueryDrill();

      for(Map.Entry<String, List<String>> entry : getDependeciesMap().entrySet()) {
         String qname = entry.getKey();
         List<String> list = entry.getValue();

         // ignored query type
         if(!queryFileMap.containsKey(qname)) {
            continue;
         }

         for(int i = 0; i < list.size(); i++) {
            String fileName = list.get(i);
            transformDependency(qname, fileName);
         }
      }

      for(Map.Entry<String, File> entry : queryFileMap.entrySet()) {
         String qname = entry.getKey();
         File file = entry.getValue();

         if(isFileTransformed(file)) {
            continue;
         }

         boolean tabular = queryTypeMap.get(file) == QueryToWsConverter.TABULAR_QUERY;
         QueryToWsConverter converter =
            tabular ? new TabularQueryToWsConverter() : new QueryToWsConverter();
         converter.process(qname, getQueryFolderMap().get(qname), file);
      }
   }

   /**
    * Transform drill subquery for queries.
    */
   private void transformQueryDrill() {
      Map<String, Set<String>> map = new HashMap<>();

      for(Map.Entry<String, List<String>> entry : getDependeciesMap().entrySet()) {
         String qname = entry.getKey();
         List<String> list = entry.getValue();

         if(!queryFileMap.containsKey(qname)) {
            continue;
         }

         for(int i = 0; i < list.size(); i++) {
            String fileName = list.get(i);

            if(fileName.startsWith(XQueryAsset.XQUERY)) {
               map.computeIfAbsent(fileName, k -> new HashSet<>()).add(qname);
            }
         }
      }

      for(Map.Entry<String, Set<String>> entry : map.entrySet()) {
         String fileName = entry.getKey();

         File targetFile = getFileByAssetIdentifer(files, fileName);

         if(targetFile == null) {
            targetFile = getFileByAssetIdentifer(files, convertedNameMap.get(fileName));
         }

         HashMap<String, String> drillMap = new HashMap<>();
         Set<String> querySet = entry.getValue();

         querySet.stream().forEach(qname -> {
            String folder = getQueryFolderMap().get(qname);
            String path = Tool.createPathString(folder, qname);
            AssetEntry wentry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
               AssetEntry.Type.WORKSHEET, path, null);
            String wsIdentifier = wentry.toIdentifier();
            drillMap.put(qname, wsIdentifier);
         });

         AutoDrillTransformer transformer = new AutoDrillTransformer();
         transformer.process(targetFile, drillMap);
      }
   }

   private boolean isFileTransformed(File queryFile) {
      Document queryDoc = UpdateDependencyHandler.getXmlDocByFile(queryFile);
      Element queryRoot = queryDoc.getDocumentElement();
      return "worksheet".equals(queryRoot.getTagName());
   }

   private long getDeploymentDate() {
      return jarInfo.getDeploymentDate().getTime();
   }

   private void transformDependency(String qname, String fileName) {
      File qfile = queryFileMap.get(qname);

      if(isFileTransformed(qfile)) {
         return;
      }

      File targetFile = getFileByAssetIdentifer(files, fileName);

      if(targetFile == null) {
         targetFile = getFileByAssetIdentifer(files, convertedNameMap.get(fileName));
      }

      String folder = getQueryFolderMap().get(qname);
      String path = StringUtils.isEmpty(folder) ? qname : folder + "/" + qname;
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, path, null);
      String wsIdentifier = entry.toIdentifier();
      boolean tabular = queryTypeMap.get(qfile) == QueryToWsConverter.TABULAR_QUERY;

      if(fileName.startsWith(WorksheetAsset.WORKSHEET)) {
         WorksheetTransformer transformer = new WorksheetTransformer();
         transformer.process(qfile, targetFile, qname, tabular);
      }
      else if(fileName.startsWith(ViewsheetAsset.VIEWSHEET)) {
         ViewsheetTransformer transformer = new ViewsheetTransformer();
         transformer.process(folder, qname, targetFile);
      }
      else if(fileName.startsWith(XLogicalModelAsset.XLOGICALMODEL) ||
         fileName.startsWith(XDataSourceAsset.XDATASOURCE))
      {
         AutoDrillTransformer transformer = new AutoDrillTransformer();
         HashMap<String, String> map = new HashMap<>();
         map.put(qname, wsIdentifier);
         transformer.process(targetFile, map);
      }
   }

   private List<String> getAllWorksheetNames(Map<String, String> names) {
      List<String> wsList = new ArrayList<>();

      names.values().stream().forEach(v -> {
         if(v.startsWith(WorksheetAsset.WORKSHEET)) {
            wsList.add(v);
         }
      });

      return wsList;
   }

   private String autoRename(String name, List<String> list, int counter) {
      if(!list.contains(name)) {
         return name;
      }

      String nname = name + "_" + counter;
      return autoRename(nname, list, counter++);
   }

   private File[] loadFiles() {
      String filePath = getUnzipFolderPath();
      FileSystemService fileSystemService = FileSystemService.getInstance();
      return fileSystemService.getFile(filePath).listFiles();
   }

   /**
    * @param files
    * @param name   the file name(not the name in names map).
    */
   private File getFile(File[] files, String name) {
      return files == null ? null : Arrays.stream(files)
         .filter(file -> Tool.equals(name, file.getName()))
         .findFirst()
         .orElse(null);
   }

   private File getFileByAssetIdentifer(File[] files, String asset_identifer) {
      return files == null ? null : Arrays.stream(files)
         .filter(file -> Tool.equals(getNames().get(file.getName()), asset_identifer))
         .findFirst()
         .orElse(null);
   }

   private File[] files;
   private String filePath;
   private List<SelectedAsset> selectedEntries;
   private List<RequiredAsset> dependentAssets;
   private Map<String, String> onames = new HashMap<>();
   private Map<String, String> nnames = new HashMap<>();
   private Map<File, Integer> queryTypeMap = new HashMap<>();
   private Map<String, File> queryFileMap = new HashMap<>(); // key -> query name, value -> file
   private Map<String, String> convertedNameMap = new HashMap<>(); // key -> query name, value-> file name
   private List<File> queryFiles = new ArrayList<>(); // key -> query name, value-> file name
   private PartialDeploymentJarInfo jarInfo;
   private ImportJarProperties properties;
}
