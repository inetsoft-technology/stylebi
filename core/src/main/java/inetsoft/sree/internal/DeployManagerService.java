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

import inetsoft.report.LibManager;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.snapshot.ViewsheetAsset2;
import inetsoft.report.io.viewsheet.snapshot.WorksheetAsset2;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistry;
import inetsoft.sree.web.dashboard.VSDashboard;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.dep.*;
import inetsoft.web.admin.deploy.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

/**
 * The DeployManagerService is used by an administrator to perform common
 * deployment tasks, such as export assets, import assets, deploy assets and
 * find assets.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class DeployManagerService {
   /**
    * Create a DeployManagerService.
    */
   public DeployManagerService() {
      super();
   }

   /**
    * Get the deploy manager service.
    */
   public static DeployManagerService getService() {
      return SingletonManager.getInstance(DeployManagerService.class);
   }

   /**
    * Import assets. WARNING: this method should not be called directly as it
    * may cause a deadlock. Instead, call RepletEngine.importAssets().
    *
    * @param data the jar file is provided as a byte array.
    * @param replace indicates if existing assets should be overwritten.
    * @param actionRecord the record to be inserted in the auditing database.
    */
   public void importAssets(byte[] data, boolean replace,
                            ActionRecord actionRecord) throws Exception
   {
      JarInputStream jarIn = null;
      InputStream in = null;
      OutputStream out = null;

      try {
         in = new ByteArrayInputStream(data);
         jarIn = new JarInputStream(in);
         JarEntry jentry;
         FileSystemService fileSystemService = FileSystemService.getInstance();
         String cacheDirectory = fileSystemService.getCacheDirectory();
         String cacheFolder = cacheDirectory + File.separator +
            "partialDeploymentJarUnzip2";
         FileSystemService fileSystemService1 = FileSystemService.getInstance();

         Tool.deleteFile(fileSystemService1.getFile(cacheFolder));
         final ArrayList<String> fileOrders = new ArrayList<>();
         Map<String, String> names = new HashMap<>();

         while((jentry = (JarEntry) jarIn.getNextEntry()) != null) {
            String ename = jentry.getName();
            String fname = "JarFileInfo.xml".equals(ename) ? ename :
               "f" + Math.abs(ename.hashCode());
            String outFileName = cacheFolder + File.separator + fname;
            names.put(fname, ename);
            File outFile = fileSystemService1.getFile(outFileName);

            if(jentry.isDirectory()) {
               if(!outFile.mkdirs()) {
                  LOG.warn("Failed to create import directory: " + outFile);
               }
            }
            else {
               if(!outFile.getParentFile().exists()) {
                  if(!outFile.getParentFile().mkdirs()) {
                     LOG.warn(
                        "Failed to create import directory: " + outFile);
                  }
               }

               if(!outFile.exists()) {
                  if(!outFile.createNewFile()) {
                     LOG.warn(
                        "Failed to create import temp file: " + outFile);
                  }

                  // wait 100 minutes for user to import files
                  fileSystemService.remove(outFile, 6000000);
                  fileOrders.add(outFile.getName());
               }

               out = new FileOutputStream(outFile);
               Tool.copyTo(jarIn, out);
               out.close();
            }
         }

         jarIn.close();
         File file = fileSystemService1.getFile(cacheFolder, "JarFileInfo.xml");
         Document infoDom = Tool.parseXML(new FileInputStream(file));
         Element root = infoDom.getDocumentElement();
         final PartialDeploymentJarInfo info = new PartialDeploymentJarInfo();
         final DeploymentInfo deploymentInfo = new DeploymentInfo(info, names, cacheFolder);
         names = deploymentInfo.getNames();
         info.parseXML(root);
         Tool.deleteFile(file);

         DataSpace space = DataSpace.getDataSpace();
         XAssetConfig config = new XAssetConfig();
         config.setOverwriting(replace);
         File[] files = deploymentInfo.getFiles();
         assert files != null;

         if(files.length > 0) {
            Arrays.sort(files, (f1, f2) -> {
               int result = fileOrders.indexOf(f1.getName()) - fileOrders.indexOf(f2.getName());

               if(result == 0) {
                  Long lastModified1 = f1.lastModified();
                  Long lastModified2 = f2.lastModified();
                  result = lastModified1.compareTo(lastModified2);
               }

               return result;
            });
         }

         for(File file1 : files) {
            if(file1.isDirectory()) {
               continue;
            }

            String filename = file1.getName();
            filename = names.get(filename);

            if(filename == null) {
               continue;
            }

            filename = Tool.replaceAll(filename, "^_^", "/");

            // templates or sub-reports or report files
            if(filename != null && filename.startsWith("__")) {
               String folder = null;
               String fname = null;

               if(filename.startsWith("__SUBREPORT_")) {
                  fname = filename.substring(12);
                  folder = "templates" + File.separator + "subreports";
               }
               else if(filename.startsWith("__TEMPLATE_MYREPORTS_'")) {
                  fname = filename.substring("__TEMPLATE_MYREPORTS_'".length());
                  int idx = fname.indexOf("'");

                  if(idx < 0) {
                     LOG.error(
                        "The template path for users is incorrect: " + filename);
                     continue;
                  }

                  String user = fname.substring(0, idx);
                  fname = fname.substring(idx + 1);
                  IdentityID userID = IdentityID.getIdentityIDFromKey(user);
                  folder = "portal/" + userID.orgID + "/" + userID.name + "/my dashboard";
               }
               else if(filename.startsWith("__TEMPLATE_")) {
                  fname = filename.substring(11);
                  folder = "templates";
               }
               else if(filename.startsWith("__REPORTFILE_")) {
                  fname = filename.substring(13);
                  folder = "ReportFiles";
               }

               if(folder == null || fname == null) {
                  continue;
               }

               if(space.exists(folder, fname)) {
                  if(!replace) {
                     continue;
                  }
               }

               if(actionRecord != null) {
                  actionRecord.setObjectName(fname);
                  actionRecord.setObjectType(ActionRecord.OBJECT_TYPE_REPORT);
                  Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
                  actionRecord.setActionTimestamp(actionTimestamp);
                  //declare the asset tyle further
                  actionRecord.setActionError(ActionRecord.OBJECT_TYPE_REPORT);
               }

               try(InputStream inp = new FileInputStream(file1)) {
                  space.withOutputStream(folder, fname, os -> IOUtils.copy(inp, os));
               }
               catch(Throwable e) {
                  LOG.error(
                     "Failed to write deployed asset file {} in folder {}", fname, folder, e);

                  if(actionRecord != null) {
                     actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
                     actionRecord.setActionError(e.getMessage());
                  }
               }
               finally {
                  if(actionRecord != null) {
                     Audit.getInstance()
                        .auditAction(actionRecord, ThreadContext.getContextPrincipal());
                  }
               }
            }
            // normal xasset
            else if(filename != null) {
               int idx = filename.indexOf("_");

               if(idx < 0) {
                  continue;
               }

               String type = filename.substring(0, idx);
               List<?> types = XAssetUtil.getXAssetTypes(true);

               if(!types.contains(type)) {
                  continue;
               }

               String identifier = filename.substring(idx + 1);
               XAsset asset = XAssetUtil.createXAsset(identifier);

               if(asset == null || !type.equals(asset.getType())) {
                  continue;
               }

               in = new FileInputStream(file1);

               if(ViewsheetAsset.VIEWSHEET.equals(type)) {
                  TransformerManager xform = TransformerManager.getManager(
                     TransformerManager.VIEWSHEET);

                  // @by ChrisS bug1402502061808 2014-6-18
                  // Set the "sourceName" parameter property in TransformerManager.
                  Properties propsOut = new Properties();
                  propsOut.setProperty("sourceName", filename);
                  xform.setProperties(propsOut);

                  Document doc = Tool.parseXML(in);
                  doc = (Document) xform.transform(doc);

                  in.close();
                  ByteArrayOutputStream output = new ByteArrayOutputStream();
                  PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(output, StandardCharsets.UTF_8));

                  writer.println(
                     "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                  writeNode(writer, doc.getDocumentElement());
                  writer.flush();

                  in = new ByteArrayInputStream(output.toByteArray());
               }

               if(ViewsheetAsset.VIEWSHEET.equals(type) && asset.getPath().contains("/")) {
                  String folder = asset.getPath().substring(0, asset.getPath().lastIndexOf("/"));

                  setFolderProperty(folder, info);
               }

               asset.parseContent(in, config, true);
            }
         }

         Tool.deleteFile(fileSystemService1.getFile(cacheFolder));
      }
      finally {
         IOUtils.closeQuietly(jarIn);
         IOUtils.closeQuietly(in);
         IOUtils.closeQuietly(out);

         // @by stephenwebster, Save the manager once to prevent unnecessary save and reloads
         // which can feel slow on the GUI.
         LibManager manager = LibManager.getManager();

         if(manager.isDirty()) {
            manager.save();
         }
      }
   }

   /**
    * Write node.
    */
   private void writeNode(PrintWriter writer, Node node) {
      switch(node.getNodeType()) {
         case Node.CDATA_SECTION_NODE:
            writer.print("<![CDATA[");
            writer.print(node.getNodeValue());
            writer.print("]]>");
            break;

         case Node.ELEMENT_NODE:
            writer.print("<");
            writer.print(node.getNodeName());
            Element elem = (Element) node;
            NamedNodeMap attrs = elem.getAttributes();

            for(int i = 0; i < attrs.getLength(); i++) {
               Node attr = attrs.item(i);
               writer.print(" ");
               writer.print(attr.getNodeName());
               writer.print("=\"");
               writer.print(Tool.encodeHTMLAttribute(attr.getNodeValue()));
               writer.print("\"");
            }

            writer.print(">");
            NodeList elems = elem.getChildNodes();

            for(int i = 0; i < elems.getLength(); i++) {
               writeNode(writer, elems.item(i));
            }

            writer.print("</");
            writer.print(node.getNodeName());
            writer.print(">");
            break;

         case Node.TEXT_NODE:
            writer.print(Tool.encodeHTMLAttribute(node.getNodeValue()));
            break;
      }
   }

   /**
    * Set the property of folders.
    */
   private void setFolderProperty(String folder, PartialDeploymentJarInfo info)
      throws Exception
   {
      RepletRegistry registry = RepletRegistry.getRegistry();
      String[] values = Tool.split(folder, '/');
      String newFolder = "";

      for(int i = 0; i < values.length; i++) {
         newFolder = i > 0 ? newFolder + "/" + values[i] : values[i];

         if(registry.isFolder(newFolder)) {
            continue;
         }

         registry.addFolder(newFolder);
         registry.setFolderAlias(newFolder, info.getFolderAlias().get(newFolder));
         registry.setFolderDescription(
            newFolder, info.getFolderDescription().get(newFolder));

         registry.save();
      }
   }

   /**
    * Get jar info.
    */
   public static PartialDeploymentJarInfo getInfo(String filePath)
      throws Exception
   {
      File file = FileSystemService.getInstance().getFile(filePath, "JarFileInfo.xml");

      if(!file.exists()) {
         File dir = FileSystemService.getInstance().getFile(filePath);
         LOG.error("Deploy dir: " + dir + " files: " + Arrays.toString(dir.list()));
         LOG.error("Last deployment: delete: " + new java.util.Date(delete1) +
            " load file: " + new java.util.Date(load1) +
            " expand file: " + new java.util.Date(set1) + " : " + files1);
         throw new IOException("JarFileInfo.xml missing");
      }

      try(InputStream in = new FileInputStream(file)) {
         Document infoDom = Tool.parseXML(in);
         Element root = infoDom.getDocumentElement();
         final PartialDeploymentJarInfo info = new PartialDeploymentJarInfo();
         info.parseXML(root);
         return info;
      }
   }

   // temp debugging for import error
   private static long delete1 = 0;
   private static long set1 = 0;
   private static long load1 = 0;
   private static String files1 = "";

   /**
    * Set the jar file to be imported.
    */
   public static String setJarFile(InputStream in, List<String> fileOrders,
                                   Map<String, String> names)
      throws Exception
   {
      return setJarFile(in,  fileOrders, names, false);
   }

   /**
    * Set the jar file to be imported.
    */
   public static String setJarFile(InputStream in, List<String> fileOrders,
                                   Map<String, String> names, boolean uniqueCacheFolder)
      throws Exception
   {
      JarInputStream jarIn = new JarInputStream(in);
      JarEntry jentry;
      FileSystemService fileSystemService = FileSystemService.getInstance();
      String cacheFolder = fileSystemService.getCacheDirectory() + File.separator +
         "partialDeploymentJarUnzip" + (uniqueCacheFolder ? System.currentTimeMillis() : "");
      FileSystemService fileSystemService1 = FileSystemService.getInstance();

      delete1 = System.currentTimeMillis();
      Tool.deleteFile(fileSystemService1.getFile(cacheFolder));
      load1 = System.currentTimeMillis();
      files1 = "";

      while((jentry = (JarEntry) jarIn.getNextEntry()) != null) {
         String ename = jentry.getName();
         String fname = "JarFileInfo.xml".equals(ename) ? ename :
            "f" + Math.abs(ename.hashCode());
         String outFileName = cacheFolder + File.separator + fname;
         names.put(fname, ename);
         File outFile = fileSystemService1.getFile(outFileName);

         files1 = files1.isEmpty() ? ename : files1 + "," + ename;

         if("JarFileInfo.xml".equals(ename)) {
            set1 = System.currentTimeMillis();
         }

         if(jentry.isDirectory()) {
            if(!outFile.mkdirs()) {
               LOG.warn("Failed to create temporary directory: " + outFile);
            }
         }
         else {
            if(!outFile.getParentFile().exists()) {
               if(!outFile.getParentFile().mkdirs()) {
                  LOG.warn(
                     "Failed to create temporary directory: " + outFile.getParentFile());
               }
            }

            if(!outFile.exists()) {
               if(!outFile.createNewFile()) {
                  LOG.warn("Failed to create temporary file: " + outFile);
               }

               // wait 100 minutes for user to import files
               fileSystemService.remove(outFile, 6000000);
               fileOrders.add(outFile.getName());
            }

            FileOutputStream out = new FileOutputStream(outFile);
            Tool.copyTo(jarIn, out);
            out.close();
         }
      }

      jarIn.close();
      return cacheFolder;
   }

   /**
    * Sort the import files.
    *
    * exported jar file already has correct order.
    */
   private static void sortFiles(File[] files, final List<String> order, Map<String, String> names) {
      List<String> fileOrders = Arrays.asList("XDATASOURCE_", "__WS_EMBEDDED_TABLE_", "XPARTITION_", "VPM_",
         "XQUERY_", "XLOGICALMODEL_", "WORKSHEET_", "TABLESTYLE_", "VIEWSHEET_",
         "__SUBREPORT_", "__TEMPLATE_", "REPLET_", "SCHEDULETASK_");

      // exported jar file already has correct order
      if(files.length > 0) {
         Arrays.sort(files, (f1, f2) -> {
            String name1 = getFileName(f1, names);
            String name2 = getFileName(f2, names);
            int sortIndex1 = getSortNameIndex(name1, fileOrders);
            int sortIndex2 = getSortNameIndex(name2, fileOrders);

            if(sortIndex1 != -1 && sortIndex2 != -1) {
               if(sortIndex1 == sortIndex2) {
                  if(order != null) {
                     int idx1 = order.indexOf(f1.getName());
                     int idx2 = order.indexOf(f2.getName());

                     if(idx1 >= 0 && idx2 >= 0) {
                        return Integer.compare(idx1, idx2);
                     }
                  }

                  return 0;
               }
               else {
                  return sortIndex1 > sortIndex2 ? 1 : -1;
               }
            }

            if(order != null) {
               int idx1 = order.indexOf(f1.getName());
               int idx2 = order.indexOf(f2.getName());

               if(idx1 >= 0 && idx2 >= 0) {
                  return Integer.compare(idx1, idx2);
               }
            }

            Long lastModified1 = f1.lastModified();
            Long lastModified2 = f2.lastModified();
            return lastModified1.compareTo(lastModified2);
         });
      }
   }

   private static int getSortNameIndex(String name, List<String> fileOrders) {
      if(name == null) {
         return -1;
      }

      for(int i = 0; i < fileOrders.size(); i++) {
         if(name.startsWith(fileOrders.get(i))) {
            return i;
         }
      }

      return -1;
   }

   public static void importAssets(boolean overwriting,
                                   final List<String> order,
                                   DeploymentInfo info,
                                   boolean desktop, Principal principal,
                                   List<String> ignoreList,
                                   ActionRecord actionRecord,
                                   List<String> failedList,
                                   ImportTargetFolderInfo targetFolderInfo,
                                   List<String> ignoreUserAssets)
      throws Exception
   {
      List<AssetEntry> vss = new ArrayList<>();
      DataSpace space = DataSpace.getDataSpace();
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(overwriting);
      FileSystemService fileSystemService = FileSystemService.getInstance();
      File[] files = info.getFiles();
      Map<String, String> names = info.getNames();
      List<PartialDeploymentJarInfo.RequiredAsset> ignoreAssets = new ArrayList<>();
      List<String> ignoreSub = new ArrayList<>();

      for(int i = 0; i < info.getDependentAssets().size(); i++) {
         if(ignoreList != null && ignoreList.contains(i + "")) {
            ignoreAssets.add(info.getDependentAssets().get(i));
         }
      }

      for(PartialDeploymentJarInfo.RequiredAsset ignoreAsset : ignoreAssets) {
         ignoreSub.add(ignoreAsset.getPath());
      }

      if(files != null) {
         DeployHelper helper = new DeployHelper(info, targetFolderInfo);
         AssetEntry targetFolder = helper.getTargetFolder();

         if(targetFolder != null && targetFolder.isRoot()) {
            targetFolder = null;
         }

         AssetEntry commonPrefixFolder = helper.getCommonPrefixFolder();
         List<File> locationChangedRelated = new ArrayList<>();
         List<File> unsupportLocations = new ArrayList<>();

         if(targetFolder != null) {
            splitSupportCustomLocationFiles(files, names, locationChangedRelated, unsupportLocations);
         }

         sortFiles(files, order, names);
         EmbeddedTableStorage embeddedTables = EmbeddedTableStorage.getInstance();

         try {
            List<XAsset> assets = DeployHelper.getAssets(files, names);
            List<XAsset> causeCycleObjects = topologicalSort(assets, helper.getGraph());
            Set<String> importedNewObjs = new HashSet<>();
            List<XAsset> selectedAssets = DeployUtil.getEntryAssets(info);
            Map<AssetObject, AssetObject> changeAssetMap = helper.getChangeAssetMap();

            for(XAsset xAsset : assets) {
               actionRecord = SUtil.getActionRecord(principal,
                   ActionRecord.ACTION_NAME_IMPORT, null, null);

               if(isIgnoreAsset(xAsset, ignoreAssets) || ignoreUserAssets.contains(xAsset.getPath())
                  || targetFolder == null || !targetFolderInfo.isDependenciesApplyTarget() &&
                  !selectedAssets.contains(xAsset))
               {
                  continue;
               }

               if(!causeCycleObjects.contains(xAsset) && !ViewsheetAsset.VIEWSHEET.equals(xAsset.getType()) &&
                  !(xAsset instanceof XDataSourceAsset))
               {
                  continue;
               }

               String identifier = getAssetFileIdentifier(xAsset);
               File file = helper.getFileMap().get(identifier);

               if(file == null) {
                  continue;
               }

               AssetObject supportEntry = getAssetObjectByAsset(xAsset);
               XAsset newAsset = !isSupportCustomLocationAsset(xAsset) ? null :
                  getChangeRootFolderAsset(xAsset, targetFolder, importedNewObjs,
                     commonPrefixFolder, true, helper.getDependencies(xAsset), changeAssetMap, false);

               if(supportEntry == null || newAsset == null) {
                  continue;
               }

               changeAssetMap.put(supportEntry, getAssetObjectByAsset(newAsset));
            }

            List<File> unImportedFile = new ArrayList<>();

            for(int i = 0; i < files.length; i++) {
               actionRecord = SUtil.getActionRecord(principal,
                  ActionRecord.ACTION_NAME_IMPORT, null, null);
               File file = files[i];

               if(file == null) {
                  continue;
               }

               String fileName = getFileName(file, names);

               if(Tool.isEmptyString(fileName)) {
                  continue;
               }

               // done when import replet.
               if(fileName.startsWith("__TEMPLATE_")) {
                  unImportedFile.add(file);
                  continue;
               }

               if(fileName.startsWith("__SUBREPORT_")) {
                  continue;
               }

               if(fileName.startsWith("__WS_EMBEDDED_TABLE_")) {
                  String fname = fileName.substring("__WS_EMBEDDED_TABLE_".length());

                  int idx = fname.indexOf('/');

                  if(idx > 0) {
                     fname = fname.substring(idx + 1);
                  }

                  if(ignoreUserAssets.contains(fname)) {
                     continue;
                  }
               }

               XAsset asset = DeployHelper.getAsset(file, names);

               if(asset == null) {
                  importAsset(file, null, ignoreSub, failedList, embeddedTables, ignoreAssets,
                     vss, overwriting, actionRecord, info, desktop, config, space,
                     principal);
                  continue;
               }

               if(isIgnoreAsset(asset, ignoreAssets) || ignoreUserAssets.contains(asset.getPath())) {
                  continue;
               }

               Set<AssetObject> dependencies = helper.getDependencies(asset);
               File transformFile = helper.getTransformFile(asset);
               AssetObject entry = getAssetObjectByAsset(asset);

               // sync file if any depends on asset was auto renamed.
               if(dependencies != null && !dependencies.isEmpty()) {
                  transformAssetFile(entry, transformFile, dependencies, changeAssetMap);
               }

               AssetEntry toFolder = targetFolder;
               XAsset fixAssetForCheck = asset;

               // convert the snapshot vs and ws to normal then to check selected.
               if(asset instanceof ViewsheetAsset2) {
                  fixAssetForCheck = new ViewsheetAsset(((ViewsheetAsset2) asset).getAssetEntry());
               }
               else if(asset instanceof WorksheetAsset2) {
                  fixAssetForCheck = new WorksheetAsset(((WorksheetAsset2) asset).getAssetEntry());
               }

               // don't need change folder
               if(targetFolder == null || !targetFolderInfo.isDependenciesApplyTarget() &&
                  !selectedAssets.contains(fixAssetForCheck) || unsupportLocations.contains(file))
               {
                  toFolder = null;
               }

               // change folder and auto rename
               XAsset nAsset = getChangeRootFolderAsset(asset, toFolder, importedNewObjs,
                  commonPrefixFolder, true, dependencies, changeAssetMap, false);

               if(!Tool.equals(asset, nAsset)) {
                  UpdateDependencyHandler.replaceDataSourceInfo(file, asset, nAsset);
                  UpdateDependencyHandler.replaceQueryInfo(file, asset, nAsset);

                  if(nAsset != null) {
                     importedNewObjs.add(nAsset.toIdentifier());
                  }

                  changeAssetMap.put(entry, getAssetObjectByAsset(nAsset));
               }

               importAsset(file, nAsset, ignoreSub, failedList, embeddedTables,
                  ignoreAssets, vss, overwriting, actionRecord, info, desktop, config,
                  space, principal);
            }

            for(int i = 0; i < unImportedFile.size(); i++) {
               importAsset(unImportedFile.get(i), null, ignoreSub, failedList,
                  embeddedTables, ignoreAssets,
                  vss, overwriting, actionRecord, info, desktop, config, space,
                  principal);
            }
         }
         finally {
            // @by stephenwebster, Save the manager once to prevent unnecessary save and reloads
            // which can feel slow on the GUI.
            LibManager manager = LibManager.getManager();

            if(manager.isDirty()) {
               manager.save();
            }
         }
      }

      Tool.deleteFile(fileSystemService.getFile(info.getUnzipFolderPath()));
      AssetRepository repository = AssetUtil.getAssetRepository(false);

      try {
         for(AssetEntry entry : vss) {
            Viewsheet vs = (Viewsheet) repository.getSheet(entry, null, false,
               AssetContent.ALL);
            ViewsheetSandbox box = new ViewsheetSandbox(vs,
               Viewsheet.SHEET_DESIGN_MODE, null, false, entry);
            box.updateAssemblies();
         }
      }
      catch(Throwable e) {
         LOG.error("Failed to create materialized views", e);
      }
   }

   private static String getAssetFileIdentifier(XAsset asset) {
      return DeployHelper.getAssetFileIdentifier(asset);
   }

   private static void transformAssetFile(AssetObject supportEntry, File transformFile,
                                          Set<AssetObject> dependencies,
                                          Map<AssetObject, AssetObject> changeAssetMap)
   {
      if(transformFile != null && dependencies != null && supportEntry != null) {
         Map<Integer, List<RenameInfo>> typeInfos =
            createRenameInfos(supportEntry, dependencies, changeAssetMap);

         if(typeInfos.size() > 0) {
            typeInfos.entrySet().forEach(entry -> {
               RenameDependencyInfo renameDependencyInfo = new RenameDependencyInfo();
               renameDependencyInfo.setRenameInfo(supportEntry, entry.getValue());

               if(renameDependencyInfo.getAssetObjects() != null &&
                  renameDependencyInfo.getAssetObjects().length > 0)
               {
                  renameDependencyInfo.setAssetFile(supportEntry, transformFile);
                  DependencyTransformer.renameDep(renameDependencyInfo, false);
               }
            });
         }
      }
   }

   private static Map<Integer, List<RenameInfo>> createRenameInfos(
      AssetObject supportEntry,
      Set<AssetObject> dependencies,
      Map<AssetObject, AssetObject> changeAssetMap)
   {
      Map<Integer, List<RenameInfo>> typeInfos = new HashMap<>();

      for(AssetObject dependency : dependencies) {
         AssetEntry physicalTableOrQuery = null;

         if(dependency instanceof AssetEntry && (((AssetEntry) dependency).isPhysicalTable() ||
            ((AssetEntry) dependency).isQuery()))
         {
            physicalTableOrQuery = (AssetEntry) dependency;

            if(physicalTableOrQuery.getProperty("prefix") != null) {
               dependency = new AssetEntry(AssetRepository.QUERY_SCOPE,
                  AssetEntry.Type.DATA_SOURCE, physicalTableOrQuery.getProperty("prefix"), null);
            }
            else {
               dependency = new AssetEntry(AssetRepository.QUERY_SCOPE,
                  AssetEntry.Type.QUERY, physicalTableOrQuery.getPath(), null);
            }
         }

         AssetEntry physicalTableOrQueryChange = null;

         if(physicalTableOrQuery != null) {
            physicalTableOrQueryChange = (AssetEntry) changeAssetMap.get(physicalTableOrQuery);
         }

         AssetObject changedNewEntry = changeAssetMap.get(dependency);

         boolean isTaskAsset = supportEntry instanceof AssetEntry && ((AssetEntry) supportEntry).isScheduleTask();
         boolean taskDependencyExtend = false;

         if(changedNewEntry == null && isTaskAsset && dependency instanceof AssetEntry) {
            AssetEntry dAssetEntry = (AssetEntry) dependency;

            if(dAssetEntry.isPartition()) {
               changedNewEntry = changeAssetMap.get(new AssetEntry(dAssetEntry.getScope(),
                  AssetEntry.Type.EXTENDED_PARTITION, dAssetEntry.getPath(), dAssetEntry.getUser()));
            }
            else if(dAssetEntry.isLogicModel()) {
               changedNewEntry = changeAssetMap.get(new AssetEntry(dAssetEntry.getScope(),
                  AssetEntry.Type.EXTENDED_LOGIC_MODEL, dAssetEntry.getPath(), dAssetEntry.getUser()));
            }

            taskDependencyExtend = changedNewEntry != null;

            if(changedNewEntry == null && dAssetEntry.isTable()) {
               changedNewEntry = changeAssetMap.get(new AssetEntry(dAssetEntry.getScope(),
                  AssetEntry.Type.WORKSHEET, dAssetEntry.getParentPath(), dAssetEntry.getUser()));

               if(changedNewEntry instanceof AssetEntry) {
                  changedNewEntry = new AssetEntry(dAssetEntry.getScope(),
                     dAssetEntry.getType(), ((AssetEntry) changedNewEntry).getPath() + "/" + dAssetEntry.getName(),
                     dAssetEntry.getUser());
               }
            }
         }

         if(changedNewEntry == null && physicalTableOrQueryChange == null) {
            continue;
         }

         if(changedNewEntry == null && physicalTableOrQueryChange != null) {
            dependency = physicalTableOrQuery;
            changedNewEntry = physicalTableOrQueryChange;
         }

         List<Integer> types = new ArrayList<>();
         boolean isCubeDs = false;

         if(dependency instanceof AssetEntry) {
            AssetEntry assetEntry = (AssetEntry) dependency;
            isCubeDs = "true".equals(assetEntry.getProperty("isCube")) && !assetEntry.isWorksheet();

            if(assetEntry.isWorksheet()) {
               types.add(RenameInfo.ASSET | RenameInfo.SOURCE);
            }
            else if(isTaskAsset && assetEntry.isTable()) {
               types.add(RenameInfo.ASSET | RenameInfo.TABLE);
            }
            else if(assetEntry.isViewsheet()) {
               types.add(RenameInfo.EMBED_VIEWSHEET | RenameInfo.VIEWSHEET);
               types.add(RenameInfo.HYPERLINK | RenameInfo.VIEWSHEET);
            }
            else if(physicalTableOrQuery != null && physicalTableOrQuery.isPhysicalTable()) {
               types.add(RenameInfo.DATA_SOURCE_FOLDER | RenameInfo.PHYSICAL_TABLE);
            }
            else if(physicalTableOrQuery != null && physicalTableOrQuery.isQuery()) {
               if(!Tool.equals(dependency, physicalTableOrQuery)) {
                  types.add(RenameInfo.DATA_SOURCE_FOLDER | RenameInfo.QUERY);
               }

               if(!Tool.equals(physicalTableOrQuery, changeAssetMap.get(physicalTableOrQuery)))
               {
                  types.add(RenameInfo.QUERY);
               }
            }
            else if(assetEntry.isDataSource()) {
               String newPath = ((AssetEntry) changedNewEntry).getPath();
               boolean isQuery = supportEntry instanceof AssetEntry &&
                  ((AssetEntry) supportEntry).isQuery();
               int type = RenameInfo.DATA_SOURCE | RenameInfo.DATA_SOURCE_FOLDER;

               if(!isQuery) {
                  XDataSource dx =
                     DataSourceRegistry.getRegistry().getDataSource(newPath);

                  if(dx == null) {
                     continue;
                  }

                  String sourceType = dx.getType();
                  boolean tabular = dx instanceof ListedDataSource ||
                     sourceType.startsWith(SourceInfo.REST_PREFIX) ||
                     dx instanceof TabularDataSource;

                  if(tabular) {
                     type |= RenameInfo.TABULAR_SOURCE;
                  }

                  if(!isCubeDs) {
                     isCubeDs = dx instanceof XMLADataSource;
                  }
               }

               if(isCubeDs) {
                  type = RenameInfo.CUBE | RenameInfo.DATA_SOURCE;
               }

               types.add(type);
               types.add(RenameInfo.SQL_TABLE | RenameInfo.DATA_SOURCE_FOLDER);
            }
            else if(assetEntry.isPartition()) {
               types.add(RenameInfo.PARTITION | RenameInfo.DATA_SOURCE);
            }
            else if(assetEntry.isLogicModel()) {
               int type = RenameInfo.LOGIC_MODEL | RenameInfo.DATA_SOURCE_FOLDER;

               if("true".equals(assetEntry.getProperty("isCube"))) {
                  type = type | RenameInfo.CUBE;
               }

               types.add(type);
            }
            else if(assetEntry.isVPM()) {
               types.add(RenameInfo.VPM | RenameInfo.DATA_SOURCE);
            }
         }

         if(types.size() == 0) {
            continue;
         }

         for(Integer type : types) {
            if(changedNewEntry instanceof AssetEntry) {
               RenameInfo renameInfo = null;
               AssetEntry dependencyAsset = (AssetEntry) dependency;
               AssetEntry changedNewEntryAsset = (AssetEntry) changedNewEntry;

               if(physicalTableOrQuery != null) {
                  if((type & RenameInfo.DATA_SOURCE_FOLDER) == RenameInfo.DATA_SOURCE_FOLDER)
                  {
                     renameInfo = new RenameInfo(dependencyAsset.getPath(), changedNewEntryAsset.getPath(), type);
                     renameInfo.setSource(physicalTableOrQuery.getName());
                  }
                  else if((type & RenameInfo.QUERY) == RenameInfo.QUERY &&
                     physicalTableOrQueryChange != null)
                  {
                     renameInfo = new RenameInfo(physicalTableOrQuery.getName(),
                        physicalTableOrQueryChange.getName(),
                        (RenameInfo.QUERY | RenameInfo.SOURCE));
                  }
               }
               else if((type & RenameInfo.VPM) == RenameInfo.VPM) {
                  renameInfo = new RenameInfo(dependencyAsset.getPath(), changedNewEntryAsset.getPath(), type);
               }
               else if(dependencyAsset.isLogicModel() || dependencyAsset.isPartition()) {
                  String oldSource = dependencyAsset.getProperty("prefix");
                  String newSource = changedNewEntryAsset.getProperty("prefix");
                  renameInfo = new RenameInfo(oldSource, newSource, type);

                  if(taskDependencyExtend) {
                     String source = dependencyAsset.getName();
                     String parent = changedNewEntryAsset.getProperty("parentPartition");

                     if(!Tool.isEmptyString(parent)) {
                        source = parent + "/" + source;
                     }

                     renameInfo.setSource(source);
                  }
                  else {
                     renameInfo.setSource(dependencyAsset.getName());
                  }

                  renameInfo.setModelFolder(changedNewEntryAsset.getProperty("modelFolder"));
               }
               else if(isCubeDs) {
                  String oldSource = Assembly.CUBE_VS + dependencyAsset.getPath();
                  String newSource = Assembly.CUBE_VS + changedNewEntryAsset.getPath();
                  renameInfo = new RenameInfo(oldSource, newSource, type);
               }
               else {
                  boolean rest =
                     (type & RenameInfo.TABULAR_SOURCE) == RenameInfo.TABULAR_SOURCE;
                  String oldName = dependencyAsset.toIdentifier();
                  String newName = changedNewEntryAsset.toIdentifier();

                  if(rest || (type & RenameInfo.SQL_TABLE) == RenameInfo.SQL_TABLE) {
                     oldName = dependencyAsset.getPath();
                     newName = changedNewEntryAsset.getPath();
                  }

                  renameInfo = new RenameInfo(oldName, newName, type, rest);
               }

               if(renameInfo != null) {
                  typeInfos.computeIfAbsent(type, key -> new ArrayList<>()).add(renameInfo);
               }
            }
         }
      }

      return typeInfos;
   }

   /**
    * Topological Sort assets by dependencies graph.
    *
    * @param assets be sorted asets.
    * @param graph dependencies graph.
    *
    * @return cause cycle asset -> dependency the cause cycle asset.
    */
   private static List<XAsset> topologicalSort(List<XAsset> assets,
                                               TopologicalSortGraph<AssetObject> graph)
   {
      List<AssetObject> sortedObjects = new ArrayList<>();
      List<AssetObject> causeCycleObjects = new ArrayList<>();

      while(graph.getAllNodes() != null && graph.getAllNodes().size() > 0) {
         List<TopologicalSortGraph<AssetObject>.GraphNode> graphNodes = graph.getLeafNodes();

         if(graphNodes.size() == 0 && graph.getAllNodes().size() > 0) {
            // all nodes is a cycle, remove a node to damage the cycle.
            TopologicalSortGraph<AssetObject>.GraphNode node = graph.getNode(graphNode -> {
               AssetObject data = graphNode.getData();

               return data instanceof AssetEntry &&
                  (((AssetEntry) data).isWorksheet() || ((AssetEntry) data).isViewsheet() ||
                     ((AssetEntry) data).isScheduleTask());
            });

            if(node != null) {
               graph.removeNode(node);
               causeCycleObjects.add(node.getData());
            }
            else {
               throw new DependencyCycleException("import assets has cycle dependencies");
            }
         }

         for(TopologicalSortGraph<AssetObject>.GraphNode graphNode : graphNodes) {
            sortedObjects.add(graphNode.getData());
            graph.removeNode(graphNode);
         }
      }

      sortedObjects.addAll(causeCycleObjects);

      assets.sort((asset0, asset1) -> {
         int index0 = sortedObjects.indexOf(getAssetObjectByAsset(asset0));
         int index1 = sortedObjects.indexOf(getAssetObjectByAsset(asset1));

         return Tool.compare(index0, index1);
      });

      return assets.stream().
         filter(asset -> causeCycleObjects.contains(getAssetObjectByAsset(asset)))
         .collect(Collectors.toList());
   }

   private static AssetObject getAssetObjectByAsset(XAsset asset) {
      return DeployHelper.getAssetObjectByAsset(asset);
   }

   private static boolean importAsset(File file, XAsset importAsAsset,
                                      List<String> ignoreSub,
                                      List<String> failedList, EmbeddedTableStorage embeddedTables,
                                      List<PartialDeploymentJarInfo.RequiredAsset> ignoreAssets,
                                      List<AssetEntry> vss, boolean overwriting,
                                      ActionRecord actionRecord,
                                      DeploymentInfo info, boolean desktop,
                                      XAssetConfig config, DataSpace space, Principal principal)
      throws IOException
   {
      return importAsset(file, importAsAsset, ignoreSub, failedList, embeddedTables, ignoreAssets,
         vss, overwriting, actionRecord, info, desktop, config, space, principal,
         false, null);
   }

   private static boolean importAsset(File file, XAsset importAsAsset,
                                      List<String> ignoreSub,
                                      List<String> failedList, EmbeddedTableStorage embeddedTables,
                                      List<PartialDeploymentJarInfo.RequiredAsset> ignoreAssets,
                                      List<AssetEntry> vss, boolean overwriting,
                                      ActionRecord actionRecord,
                                      DeploymentInfo info, boolean desktop,
                                      XAssetConfig config, DataSpace space, Principal principal,
                                      boolean autoRenameExistSrt,
                                      Consumer<String> newTemplatePathProcess)
      throws IOException
   {
      if(file.isDirectory()) {
         return false;
      }

      Map<String, String> names = info.getNames();
      PartialDeploymentJarInfo jarInfo = info.getJarInfo();
      String filename = getFileName(file, names);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());

      if(filename == null) {
         return false;
      }

      Catalog catalog = Catalog.getCatalog();

      // templates or sub-reports or report files
      if(filename != null && filename.startsWith("__")) {
         String folder = null;
         String fname = null;
         boolean isSubReport = false;

         if(filename.startsWith("__SUBREPORT_")) {
            String checkName = filename.substring(12, filename.length() - 4);
            String folderPrefix = "/templates/subreports";
            boolean containsFolder = checkName.startsWith(folderPrefix);

            if(containsFolder) {
               checkName = checkName.substring(folderPrefix.length());
            }

            if(ignoreSub.contains(checkName)) {
               return false;
            }

            fname = filename.substring(12);

            if(containsFolder) {
               fname = fname.substring(folderPrefix.length());
            }

            // Bug #51723, strip leading (possibly nested) /templates/subreports folders
            fname = fname.replaceAll("^(/templates/subreports)+", "");

            // Bug #51723, if the subreport is directly under /templates, keep it there,
            // otherwise put it under /templates/subreports
            if(fname.startsWith("/templates")) {
               fname = fname.substring(10);
               folder = "templates";
            }
            else {
               folder = "templates" + File.separator + "subreports";
            }

            isSubReport = true;
         }
         else if(filename.startsWith("__TEMPLATE_MYREPORTS_'")) {
            fname = filename.substring("__TEMPLATE_MYREPORTS_'".length());
            int idx = fname.indexOf("'");

            if(idx < 0) {
               String msg = catalog.getString("Could not import report assets, the user template {} " +
                  "path is incorrect: ", filename);
               failedList.add(msg);
               LOG.warn(msg);
               return false;
            }

            IdentityID user = importAsAsset != null ? importAsAsset.getUser() : new IdentityID(fname.substring(0, idx), pId.orgID);

            if(Tool.isEmptyString(user.name)) {
               folder = "templates";
            }
            else {
               folder = "portal/" + user.orgID + "/" + user.name + "/my dashboard";
            }

            fname = fname.substring(idx + 1);
         }
         else if(filename.startsWith("__TEMPLATE_")) {
            fname = filename.substring(11);

            if(importAsAsset != null && !Tool.isEmptyString(importAsAsset.getUser().name)) {
               IdentityID userID = importAsAsset.getUser();
               folder = "portal/" + userID.orgID + "/" + userID.name + "/my dashboard";
            }
            else {
               folder = "templates";
            }
         }
         else if(filename.startsWith("__WS_EMBEDDED_TABLE_")) {
            fname = filename.substring("__WS_EMBEDDED_TABLE_".length());

            int idx = fname.indexOf('/');

            if(idx > 0) {
               folder = fname.substring(0, idx);
               fname = fname.substring(idx + 1);
            }
         }

         if(folder == null || fname == null) {
            String msg = catalog.getString("Could not import report assets, the user template {} " +
               "path is incorrect: ", filename);
            failedList.add(msg);
            LOG.warn(msg);
            return false;
         }

         if(filename.startsWith("__WS_EMBEDDED_TABLE_")) {
            if(!embeddedTables.tableExists(fname) || overwriting) {
               try(InputStream input = new FileInputStream(file)) {
                  embeddedTables.writeTable(fname, input);
               }
            }

            return true;
         }

         try {
            if(space.exists(folder, fname)) {
               if(autoRenameExistSrt && newTemplatePathProcess != null) {
                  try {
                     fname = getIdleSrtFileNameInSpace(folder, fname);
                     newTemplatePathProcess.accept(fname);
                  }
                  catch(Exception ex) {
                     if(!overwriting) {
                        return false;
                     }
                  }
               }
               else if(!overwriting) {
                  return false;
               }
            }

            space.withOutputStream(folder, fname, out -> Files.copy(file.toPath(), out));

            // wait for the file to become available before proceeding
            int maxWaitTime = 0;

            try {
               maxWaitTime = Integer.parseInt(
                  SreeEnv.getProperty("import.assets.file.wait.time", "0"));
            }
            catch(Exception ex) {
               // do nothing
            }

            long startTime = System.currentTimeMillis();

            while(System.currentTimeMillis() - startTime < maxWaitTime &&
               !space.exists(folder, fname))
            {
               Thread.sleep(Math.min(500, maxWaitTime));
            }
         }
         catch(Exception e) {
            String errorMessage = e.getMessage() == null ? e.toString() : e.getMessage();

            if(!failedList.contains(errorMessage)) {
               failedList.add(errorMessage);
            }

            LOG.error(catalog.getString(
               "em.import.file.failedToWriteToFolder", fname, folder), e);
         }
      }
      // normal xasset
      else {
         int idx = filename.indexOf('_');

         if(idx < 0) {
            return false;
         }

         String type = filename.substring(0, idx);
         List<?> types = XAssetUtil.getXAssetTypes(true);

         if(!types.contains(type)) {
            return false;
         }

         String identifier = filename.substring(idx + 1);
         int orgIdx = StringUtils.ordinalIndexOf(identifier, "^", 4);

         if(orgIdx != -1) {
            identifier = identifier.substring(0, orgIdx);
         }

         XAsset asset = importAsAsset != null ? importAsAsset : XAssetUtil.createXAsset(identifier);
         String path = asset.getPath();

         if(isIgnoreAsset(asset, ignoreAssets)) {
            return false;
         }

         if(asset instanceof TableStyleAsset) {
            path = asset.getPath();
         }

         if(asset == null || !type.equals(asset.getType())) {
            String msg = catalog.getString("em.import.file.failed.invalidFile", path);
            failedList.add(msg);
            LOG.warn(msg);
            return false;
         }

         InputStream input = null;

         try {
            if(actionRecord != null) {
               actionRecord.setObjectName(getRecordName(path, asset));
               actionRecord.setObjectType(getAuditType(asset));
               actionRecord.setScheduleUser();
               Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
               actionRecord.setActionTimestamp(actionTimestamp);
               //declare the asset tyle further
               actionRecord.setActionError(type);
            }

            input = new FileInputStream(file);
            Resource resource;

            // if asset already exists, check asset permission
            if(asset.exists()) {
               resource = asset.getSecurityResource();
            }
            else {
               resource = AssetUtil.getParentSecurityResource(asset);
            }

            if(principal != null && resource != null) {
               // query have no relationship with datasouce when import
               // so check datasource permission manually
               if(asset.getUser() != null) {
                  // User scope asset is allowed to import if the current
                  // user is the owner or it has admin permission on owner
                  IdentityID owner = asset.getUser();

                  if(!(principal.getName().equals(owner.convertToKey()) || SecurityEngine.getSecurity().checkPermission(
                     principal, ResourceType.SECURITY_USER, owner, ResourceAction.ADMIN)) ||
                     !Tool.equals(owner.getOrgID(), OrganizationManager.getInstance().getCurrentOrgID()))
                  {
                     String msg = catalog.getString("em.import.file.failed.noPermission",
                        asset.getType() + " " + path);
                     failedList.add(msg);
                     LOG.warn(msg);
                     return false;
                  }
               }
               else {
                  ResourceAction action = AssetUtil.getAssetDeployPermission(resource);

                  if(!SecurityEngine.getSecurity().checkPermission(
                     principal, resource.getType(), resource.getPath(), action))
                  {
                     String assetName = asset.getType().equals(DeviceAsset.DEVICE) ?
                        ((DeviceAsset) asset).getDeviceInfo().getName() : path;
                     String msg =  catalog.getString("em.import.file.failed.noPermission",
                        asset.getType() + " " + assetName);
                     failedList.add(msg);
                     LOG.warn(msg);
                     return false;
                  }
               }
            }

            if(ViewsheetAsset.VIEWSHEET.equals(type) && path.contains("/")) {
               String folder = path.substring(0, path.lastIndexOf("/"));
               setFolderProperty(folder, asset.getUser(), jarInfo);
            }

            if(asset instanceof VSAutoSaveAsset || asset instanceof WSAutoSaveAsset) {
               if(input.available() > 0) {
                  asset.parseContent(input, config, true);
               }

               return false;
            }

            if(asset instanceof AbstractSheetAsset) {
               AssetEntry entry = ((AbstractSheetAsset) asset).getAssetEntry();
               String alias = null;
               String desc = null;
               boolean selected = false;

               for(PartialDeploymentJarInfo.SelectedAsset selectedAsset : info.getSelectedEntries())
               {
                  if(selectedAsset.getPath().equals(asset.getPath())) {
                     selected = selectedAsset.getType().equals(asset.getType());
                     break;
                  }
               }

               if(selected) {
                  alias = jarInfo.getFolderAlias().get(entry.toIdentifier());
                  desc = jarInfo.getFolderDescription().get(entry.getPath());
               }
               else {
                  for(PartialDeploymentJarInfo.RequiredAsset required : info.getDependentAssets()) {
                     if(required.getPath().equals(asset.getPath())) {
                        desc = required.getAssetDescription();
                        break;
                     }
                  }
               }

               entry.setAlias(alias);
               entry.setProperty("description", desc);

               try(InputStream input2 = new FileInputStream(file)) {
                  if(input2.available() > 0) {
                     Document doc = Tool.parseXML(input2, "UTF-8", false, false);
                     UpdateDependencyHandler.addSheetDependencies(
                        (AbstractSheetAsset) asset, doc);
                  }
               }
            }

            if(asset instanceof XLogicalModelAsset) {
               String model = asset.getPath();

               try(InputStream input2 = new FileInputStream(file)) {
                  if(input2.available() > 0) {
                     Document doc = Tool.parseXML(input2, "UTF-8", false, false);
                     UpdateDependencyHandler.addModelDependencies(model, doc);
                  }
               }
            }

            if(asset instanceof VirtualPrivateModelAsset) {
               VirtualPrivateModelAsset vpm = (VirtualPrivateModelAsset) asset;
               String ds = vpm.getDataSource();

               try(InputStream input2 = new FileInputStream(file)) {
                  if(input2.available() > 0) {
                     Document doc = Tool.parseXML(input2, "UTF-8", false, false);
                     UpdateDependencyHandler.addVPMDependencies(ds, vpm.getPath(), doc);
                  }
               }
            }

            // For Bug #1786, do not remove MVDef.
            // we need an enhancement here to help the user
            // determine if they should recreate the mv or not.
                  /*
                  if(asset instanceof ViewsheetAsset && overwriting) {
                     AssetRepository engine = AssetUtil.getAssetRepository(false);
                     AssetEntry entry = ((ViewsheetAsset) asset).getAssetEntry();

                     if(engine.containsEntry(entry)) {
                        MVManager mgr = MVManager.getManager();
                        mgr.removeDependencies(entry);
                     }
                  }
                  */

            if(input.available() > 0) {
               // there import file.
               asset.parseContent(input, config, true);

               if(asset instanceof ScheduleTaskAsset) {
                  DependencyHandler.getInstance().updateTaskDependencies((ScheduleTaskAsset) asset);
               }

               if(asset instanceof XDataSourceAsset) {
                  DataSourceRegistry registry = DataSourceRegistry.getRegistry();
                  String dpath = ((XDataSourceAsset) asset).getDatasource();
                  XDataSource source = registry.getDataSource(dpath);

                  if(source instanceof XMLADataSource) {
                     XDomain domain = XFactory.getRepository().getDomain(dpath);
                     DependencyHandler.getInstance().updateCubeDomainDependencies(domain, true);
                  }
               }
            }

            // ChrisS bug1382579817311 2014-6-3
            // For audit, display the query name under the query folder,
            // under the data source name, under the data source folder.
            if(actionRecord != null) {
               // ChrisS bug1382584898114 2014-6-4
               // For audit, if the worksheet is under a user, then
               // display the worksheet name under that user.
               if(asset instanceof WorksheetAsset) {
                  IdentityID worksheetUser =
                     ((WorksheetAsset) asset).getAssetEntry().getUser();

                  if(worksheetUser != null) {
                     actionRecord.setObjectName("User/" + worksheetUser.name + "/" +
                        actionRecord.getObjectName());
                  }
               }
            }

            if(DashboardAsset.DASHBOARD.equals(type)) {
               DashboardAsset rasset = (DashboardAsset) asset;
               String name = rasset.getPath();
               IdentityID user = rasset.getUser();
               AssetEntry entry = new AssetEntry(AssetRepository.USER_SCOPE,
                  AssetEntry.Type.DASHBOARD, name, user);
               VSDashboard dashboard =
                  (VSDashboard) DashboardRegistry.getRegistry(user).getDashboard(name);

               if(dashboard != null && dashboard.getViewsheet() != null) {
                  String id = dashboard.getViewsheet().getIdentifier();
                  UpdateDependencyHandler.addDashboardDepedency(id, entry);
               }
            }

            if(desktop && asset instanceof ViewsheetAsset) {
               AssetRepository engine = AssetUtil.getAssetRepository(false);
               ViewsheetAsset vasset = (ViewsheetAsset) asset;
               Viewsheet vs = (Viewsheet) vasset.getCurrentSheet(engine);

               if(vs != null && vs.getViewsheetInfo().getMVType() ==
                  ViewsheetInfo.EMBEDDED_MV)
               {
                  vss.add(vasset.getAssetEntry());
               }
            }
         }
         catch(Exception e) {
            String errorMessage = e.getMessage() == null ? e.toString() : e.getMessage();

            if(!failedList.contains(errorMessage)) {
               failedList.add(errorMessage);
            }

            LOG.error(catalog.getString("em.import.file.failed", path), e);

            if(actionRecord != null) {
               actionRecord.setActionError(e.getMessage());
            }
         }
         finally {
            if(input != null) {
               try {
                  input.close();
               }
               catch(Exception ex2) {
                  // ignore it
               }
            }

            if(actionRecord != null) {
               Audit.getInstance().auditAction(actionRecord, principal);
            }
         }
      }

      return true;
   }

   private static String getIdleSrtFileNameInSpace(String folder, String fname) throws Exception {
      String targetPath = SreeEnv.getProperty("sree.home") + File.separator + folder;

      if(fname.endsWith(".srt")) {
         fname = fname.substring(0, fname.length() - 4);

         return SUtil.findIdleFileNameInSpace(targetPath, fname, ".srt");
      }

      return SUtil.findIdleFileNameInSpace(targetPath, fname, null);
   }

   public static XAsset getChangeRootFolderAsset(XAsset asset,
                                                 AssetEntry targetFolder,
                                                 Set<String> importedNewObjs,
                                                 AssetEntry commonPrefixFolder,
                                                 boolean createUserFolder,
                                                 Set<AssetObject> dependencies,
                                                 Map<AssetObject, AssetObject> changeAssetMap,
                                                 boolean justUpdatePath)
   {
      try {
         String nIdentifier = changeFolder(asset, targetFolder, commonPrefixFolder,
            createUserFolder, changeAssetMap);

         if(nIdentifier != null && !Tool.equals(nIdentifier, asset.toIdentifier())) {
            asset = XAssetUtil.createXAsset(nIdentifier);
         }

         if(asset instanceof FolderChangeableAsset) {
            if(importedNewObjs != null) {
               String originalNewIdentifier = nIdentifier;
               int renameIndex = 1;

               while(importedNewObjs.contains(nIdentifier)) {
                  String path = asset.getPath();
                  String autoRename = originalNewIdentifier.replace("^" + path,
                     "^" + path + "_" + renameIndex);

                  if(Tool.equals(autoRename, nIdentifier)) {
                     return asset;
                  }

                  nIdentifier = autoRename;
                  renameIndex++;
               }

               if(renameIndex > 1) {
                  asset = XAssetUtil.createXAsset(nIdentifier);
               }
            }

            return autoRenameDataSourceAsset(asset);
         }
      }
      catch(UnsupportedOperationException ignore) {
      }

      return asset;
   }

   private static String changeFolder(XAsset asset,
                                      AssetEntry targetFolder,
                                      AssetEntry commonPrefixFolder,
                                      boolean createUserFolder,
                                      Map<AssetObject, AssetObject> changeAssetMap)
   {
      String nIdentifier = null;

      if(targetFolder == null) {
         nIdentifier = getUpdatedIdentifier(asset, changeAssetMap);

         return nIdentifier == null ? asset.toIdentifier() : nIdentifier;
      }

      if(asset instanceof FolderChangeableAsset) {
         String targetFolderPath = targetFolder.getPath();

         if(targetFolderPath.startsWith(Tool.MY_DASHBOARD)) {
            if(targetFolderPath.length() == Tool.MY_DASHBOARD.length()) {
               targetFolderPath = "/";
            }
            else {
               targetFolderPath = targetFolderPath.substring(11);
            }
         }

         if(targetFolder.getScope() == AssetRepository.USER_SCOPE) {
            nIdentifier = ((FolderChangeableAsset) asset)
               .getChangeFolderIdentifier(commonPrefixFolder.getPath(), targetFolderPath,
                  targetFolder.getUser());
         }
         else {
            if(createUserFolder && asset.getUser() != null &&
               !Tool.isEmptyString(asset.getUser().name) &&
               !Tool.equals(asset.getUser(), commonPrefixFolder.getUser()))
            {
               targetFolderPath += "/" + asset.getUser().name;
            }

            nIdentifier = getUpdatedIdentifier(asset, changeAssetMap);

            if(nIdentifier == null) {
               nIdentifier = ((FolderChangeableAsset) asset)
                  .getChangeFolderIdentifier(commonPrefixFolder.getPath(), targetFolderPath,
                     targetFolder.getUser());
            }
         }
      }

      return nIdentifier;
   }

   /**
    * Get the updated identifier for the target asset after source was renamed or changed folder.
    */
   private static String getUpdatedIdentifier(XAsset asset, Map<AssetObject,
                                              AssetObject> changeAssetMap)
   {
      String dataSource = null;

      if(asset instanceof XPartitionAsset) {
         dataSource = ((XPartitionAsset) asset).getDataSource();
      }
      else if(asset instanceof XLogicalModelAsset) {
         dataSource = ((XLogicalModelAsset) asset).getDataSource();
      }
      else if(asset instanceof VirtualPrivateModelAsset) {
         dataSource = ((VirtualPrivateModelAsset) asset).getDataSource();
      }

      if(dataSource != null) {
         AssetEntry dataSourceEntry = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.DATA_SOURCE, dataSource, null);

         if(changeAssetMap != null) {
            AssetObject newSource = changeAssetMap.get(dataSourceEntry);

            if(newSource instanceof AssetEntry) {
               String pathSpliter = XUtil.DATAMODEL_PATH_SPLITER;
               return asset.toIdentifier().replace(
                  pathSpliter + dataSource + pathSpliter,
                  pathSpliter + ((AssetEntry) newSource).getPath() + pathSpliter);
            }
         }
      }

      return null;
   }

   private static XAsset autoRenameDataSourceAsset(XAsset xAsset) {
      return autoRenameDataSourceAsset(xAsset, null, null, null);
   }

   private static XAsset autoRenameDataSourceAsset(XAsset xAsset, Set<AssetObject> dependencies,
                                                   Map<AssetObject, AssetObject> changeAssetMap,
                                                   Supplier<AssetObject> getParentFunc)
   {
      boolean justUpdatePath = dependencies == null && changeAssetMap == null;
      XDataSourceAsset parent = null;

      if(justUpdatePath &&
         (xAsset instanceof XLogicalModelAsset || xAsset instanceof XPartitionAsset))
      {
         boolean logical = xAsset instanceof XLogicalModelAsset;
         String datasource = logical ? ((XLogicalModelAsset) xAsset).getDataSource() :
            ((XPartitionAsset) xAsset).getDataSource();
         parent = new XDataSourceAsset(datasource);
      }

      String path = xAsset.getPath();

      if(path == null || !xAsset.exists() && (parent == null || !parent.exists())) {
         return xAsset;
      }

      int idx = path.lastIndexOf('/');
      String assetName = idx >= 0 ? path.substring(idx + 1) : path;

      if(xAsset instanceof XDataSourceAsset) {
         DataSourceRegistry registry = DataSourceRegistry.getRegistry();
         String[] existNames = registry.getDataSourceNames();
         XDataSourceAsset dasset = (XDataSourceAsset) xAsset;
         String existDsFullName = dasset.getDataSourceName(dasset.getDatasource());

         // not same folder, auto rename avoid relocate the exist one.
         if(!Tool.equals(dasset.getPath(), existDsFullName)) {
            return autoRenameAsset(xAsset, assetName, existNames);
         }
      }
      // show updated path in import dialog.
      else if(justUpdatePath && parent != null) {
         XAsset nasset = autoRenameDataSourceAsset(parent, null, null, null);

         if(!Tool.equals(nasset, parent)) {
            String opath = xAsset.getPath();
            String npath = opath.replace(parent.getPath(), nasset.getPath());
            return xAsset instanceof XLogicalModelAsset ?
               new XLogicalModelAsset(npath) : new XPartitionAsset(npath);
         }
      }

      return xAsset;
   }

   private static XAsset autoRenameAsset(XAsset asset, String assetName,
                                         String[] existNames)
   {
      String identifier = asset.toIdentifier();
      String path = asset.getPath();

      for(int i = -1; i < Integer.MAX_VALUE; i++) {
         String nameSuffix = i >=0 ? "_" + i : "";
         String name = assetName + nameSuffix;
         String autoRename = identifier.replace("^" + path, "^" + path + nameSuffix);
         XAsset newAsset = XAssetUtil.createXAsset(autoRename);

         if(!Tool.contains(existNames, name)) {
            if(Tool.equals(autoRename, identifier)) {
               return asset;
            }

            return newAsset;
         }
      }

      return asset;
   }

   private static XAsset getAssetByFile(File file, Map<String, String> names) {
      return DeployHelper.getAssetByFile(file, names);
   }

   private static boolean isLocationRelatedAsset(XAsset asset) {
      return isSupportCustomLocationAsset(asset) ||
         asset instanceof DashboardAsset || asset instanceof ScheduleTaskAsset;
   }

   private static boolean isSupportCustomLocationAsset(XAsset asset) {
      return asset instanceof WorksheetAsset ||
         asset instanceof ViewsheetAsset || asset instanceof XDataSourceAsset ||
         isDatasourceChildren(asset);
   }

   private static boolean isDatasourceChildren(XAsset asset) {
      return asset instanceof XLogicalModelAsset || asset instanceof XPartitionAsset ||
         asset instanceof VirtualPrivateModelAsset;
   }

   /**
    * Get the proper object type of an asset for auditing purposes
    * @param asset The asset for which we will determine the audit object type
    * @return The audit record object type.
    */
   private static String getAuditType(XAsset asset) {
      String assetType = asset.getType();

      return switch(assetType) {
         case ViewsheetAsset.VIEWSHEET -> ActionRecord.OBJECT_TYPE_DASHBOARD;
         case VirtualPrivateModelAsset.VPM -> ActionRecord.OBJECT_TYPE_VIRTUAL_PRIVATE_MODEL;
         case DeviceAsset.DEVICE -> ActionRecord.OBJECT_TYPE_DEVICE;
         case ScriptAsset.SCRIPT -> ActionRecord.OBJECT_TYPE_SCRIPT;
         case DashboardAsset.DASHBOARD -> ActionRecord.OBJECT_TYPE_DASHBOARD;
         case TableStyleAsset.TABLESTYLE -> ActionRecord.OBJECT_TYPE_TABLE_STYLE;
         case XDataSourceAsset.XDATASOURCE -> ActionRecord.OBJECT_TYPE_DATASOURCE;
         case VSSnapshotAsset.VSSNAPSHOT -> ActionRecord.OBJECT_TYPE_SNAPSHOT;
         case ScheduleTaskAsset.SCHEDULETASK -> ActionRecord.OBJECT_TYPE_TASK;
         case WorksheetAsset.WORKSHEET -> ActionRecord.OBJECT_TYPE_WORKSHEET;
         case XPartitionAsset.XPARTITION -> ActionRecord.OBJECT_TYPE_PHYSICAL_VIEW;
         case XLogicalModelAsset.XLOGICALMODEL -> ActionRecord.OBJECT_TYPE_LOGICAL_MODEL;
         case null, default -> ActionRecord.OBJECT_TYPE_ASSET;
      };
   }

   private static String getRecordName(String oname, XAsset asset) {
      IdentityID user = asset.getUser();
      String nname;

      if(asset instanceof DeviceAsset) {
         return ((DeviceAsset) asset).getDeviceInfo() != null ? ((DeviceAsset) asset).getDeviceInfo().getName() : null;
      }

      if(asset instanceof VSAutoSaveAsset) {
         VSAutoSaveAsset autoSaveAsset = (VSAutoSaveAsset) asset;

         String path = autoSaveAsset.getPath();

         if(path.contains("^")) {
            String[] paths = path.split("\\^");

            if(paths.length > 3) {
               return paths[2] + "/" + paths[3];
            }
         }
      }

      if(!Tool.equals(ViewsheetAsset.VIEWSHEET, asset.getType())) {
         return oname;
      }

      if(user == null) {
         nname = "Repository/" + oname;
      }
      else {
         nname = "User/" + user.name + "/" + oname;
      }

      return nname;
   }

   /**
    * Set the alias and description for the specifed folder.
    */
   //public
   private static void setFolderProperty(String folder, IdentityID user,
                                         PartialDeploymentJarInfo info) throws Exception {
      RepletRegistry registry = RepletRegistry.getRegistry(user);
      String[] values = Tool.split(folder, '/');
      String newFolder = "";

      for(int i = 0; i < values.length; i++) {
         newFolder = i > 0 ? newFolder + "/" + values[i] : values[i];
         String folderPath = newFolder;

         if(user != null && !Tool.isEmptyString(user.name) && folderPath != null &&
            !folderPath.startsWith(Tool.MY_DASHBOARD))
         {
            folderPath = Tool.MY_DASHBOARD + "/" + folderPath;
         }

         if(registry.isFolder(folderPath)) {
            continue;
         }

         registry.addFolder(folderPath);
         registry.setFolderAlias(folderPath, info.getFolderAlias().get(newFolder));
         registry.setFolderDescription(
            folderPath, info.getFolderDescription().get(folderPath));
      }

      registry.save();
   }

   /**
    * Get file name.
    */
   private static String getFileName(File file, Map<String, String> names) {
      return DeployHelper.getFileName(file, names);
   }

   /**
    * Check the asset whether need ignore.
    */
   private static boolean isIgnoreAsset(XAsset asset,
                                        List<PartialDeploymentJarInfo.RequiredAsset> ignoreAssets)
   {
      for(PartialDeploymentJarInfo.RequiredAsset ignoreAsset : ignoreAssets) {
         if(ignoreAsset.getPath().equals(asset.getPath()) &&
            ignoreAsset.getType().equals(asset.getType()) &&
            (asset.getUser() == null || asset.getUser().equals(ignoreAsset.getUser())))
         {
            return true;
         }
      }

      return false;
   }

   private static void splitSupportCustomLocationFiles(File[] files, Map<String, String> names,
                                                       List<File> locationChangedRelated,
                                                       List<File> unsupportLocations)
   {
      for(File file : files) {

         if(file != null) {
            String filename = getFileName(file, names);

            if(filename != null && (filename.startsWith("__SUBREPORT_") ||
               filename.startsWith("__TEMPLATE_MYREPORTS_'") || filename.startsWith("__TEMPLATE_")))
            {
               locationChangedRelated.add(file);
            }
            else {
               XAsset asset = getAssetByFile(file, names);

               if(isLocationRelatedAsset(asset)) {
                  locationChangedRelated.add(file);
               }
               else {
                  unsupportLocations.add(file);
               }
            }
         }
         else {
            unsupportLocations.add(file);
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(DeployManagerService.class);
}
