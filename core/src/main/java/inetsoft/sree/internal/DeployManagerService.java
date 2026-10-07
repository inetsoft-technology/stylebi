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
import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.snapshot.ViewsheetAsset2;
import inetsoft.report.io.viewsheet.snapshot.WorksheetAsset2;
import inetsoft.sree.*;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.ServerFilePathPolicy;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Identity;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.web.admin.content.repository.model.BookmarkConflict;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.dep.*;
import inetsoft.web.admin.deploy.*;
import inetsoft.web.admin.schedule.ScheduleSecretIdChecker;
import inetsoft.web.admin.schedule.ScheduleTaskIdentityChecker;
import inetsoft.web.portal.data.SecretIdAuthorizer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import java.io.*;
import java.nio.file.Files;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
@Service
@Lazy
public class DeployManagerService {
   @Autowired
   public DeployManagerService(SecurityEngine securityEngine,
                               DependencyHandler dependencyHandler,
                               DataSourceRegistry dataSourceRegistry,
                               DashboardRegistryManager dashboardRegistryManager,
                               LibManagerProvider libManagerProvider,
                               DashboardManager dashboardManager,
                               XRepository repository,
                               FileSystemService fileSystemService,
                               DataSpace dataSpace,
                               EmbeddedTableStorage embeddedTableStorage,
                               RepletRegistryManager repletRegistryManager)
   {
      this.securityEngine = securityEngine;
      this.dependencyHandler = dependencyHandler;
      this.dataSourceRegistry = dataSourceRegistry;
      this.dashboardRegistryManager = dashboardRegistryManager;
      this.libManagerProvider = libManagerProvider;
      this.dashboardManager = dashboardManager;
      this.repository = repository;
      this.fileSystemService = fileSystemService;
      this.dataSpace = dataSpace;
      this.embeddedTableStorage = embeddedTableStorage;
      this.repletRegistryManager = repletRegistryManager;
   }

   /**
    * Get jar info.
    */
   public static PartialDeploymentJarInfo getInfo(String filePath, boolean isImportAsSiteAdmin)
      throws Exception
   {
      File file = FileSystemService.getInstance().getFile(filePath, "JarFileInfo.xml");

      if(!file.exists()) {
         throw new IOException("JarFileInfo.xml missing");
      }

      try(InputStream in = new FileInputStream(file)) {
         Document infoDom = Tool.parseXML(in);
         Element root = infoDom.getDocumentElement();
         final PartialDeploymentJarInfo info = new PartialDeploymentJarInfo();
         info.parseXML(root);
         String currOrgID = OrganizationManager.getInstance().getCurrentOrgID();

         if(isImportAsSiteAdmin) {
            handleImportAsSiteAdmin(info, currOrgID);
         }

         return info;
      }
   }

   //if importing as site admin, should import all assets and update pointers to current organization
   public static void handleImportAsSiteAdmin(PartialDeploymentJarInfo info, String currOrgID) throws Exception {
      for(PartialDeploymentJarInfo.SelectedAsset asset : info.getSelectedEntries()) {
         if(asset.getUser() != null && asset.getUser().orgID != null) {
            asset.getUser().orgID = currOrgID;
         }
      }

      for(PartialDeploymentJarInfo.RequiredAsset asset : info.getDependentAssets()) {
         if(asset.getUser() != null && asset.getUser().orgID != null) {
            asset.getUser().orgID = currOrgID;
         }
      }

      Map<String, String> oFolderAlias = new HashMap<>(info.getFolderAlias());
      info.getFolderAlias().clear();

      for(String key : oFolderAlias.keySet()) {
         String path = oFolderAlias.get(key);

         if(key.indexOf("^") > -1) {
            key = key.substring(0, key.lastIndexOf("^") + 1) + currOrgID;

            for(String keySection : key.split("\\^")) {
               if(keySection.contains(IdentityID.KEY_DELIMITER)) {
                  IdentityID updatedUser = IdentityID.getIdentityIDFromKey(keySection);
                  updatedUser.orgID = OrganizationManager.getInstance().getCurrentOrgID();
                  key = key.replace(keySection, updatedUser.convertToKey());
               }
            }
         }

         info.getFolderAlias().put(key, path);
      }
   }

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

      Tool.deleteFile(fileSystemService1.getFile(cacheFolder));

      while((jentry = (JarEntry) jarIn.getNextEntry()) != null) {
         String ename = jentry.getName();
         String fname = "JarFileInfo.xml".equals(ename) ? ename :
            "f" + Math.abs(ename.hashCode());
         String outFileName = cacheFolder + File.separator + fname;
         names.put(fname, ename);
         File outFile = fileSystemService1.getFile(outFileName);

         if(jentry.isDirectory()) {
            if(!outFile.mkdirs()) {
               LOG.warn("Failed to create temporary directory: " + outFile);
            }
         }
         else {
            if(!outFile.getParentFile().exists()) {
               if(!outFile.getParentFile().mkdirs()) {
                  LOG.warn("Failed to create temporary directory: {}", outFile.getParentFile());
               }
            }

            if(!outFile.exists()) {
               if(!outFile.createNewFile()) {
                  LOG.warn("Failed to create temporary file: {}", outFile);
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

   public void importAssets(boolean overwriting,
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
      importAssets(overwriting, order, info, desktop, principal, ignoreList, actionRecord,
         failedList, targetFolderInfo, ignoreUserAssets, null);
   }

   public void importAssets(boolean overwriting,
                            final List<String> order,
                            DeploymentInfo info,
                            boolean desktop, Principal principal,
                            List<String> ignoreList,
                            ActionRecord actionRecord,
                            List<String> failedList,
                            ImportTargetFolderInfo targetFolderInfo,
                            List<String> ignoreUserAssets,
                            Map<String, Boolean> bookmarkResolutions)
      throws Exception
   {
      PasswordEncryption.setDecryptForceLocal(true);
      // Bug #77628, count the secrets that could not be decrypted with this server's master
      // password, they are imported as their encrypted value and must be re-entered
      AtomicInteger decryptFailures = new AtomicInteger();
      PasswordEncryption.setMasterDecryptFailures(decryptFailures);
      Set<String> undecryptableAssets = new LinkedHashSet<>();

      try {
         importAssets0(overwriting, order, info, desktop, principal, ignoreList, actionRecord,
            failedList, targetFolderInfo, ignoreUserAssets, bookmarkResolutions,
            undecryptableAssets);
         Set<String> ignoredQueries = info.getIgnoredQueries();

         if(!ignoredQueries.isEmpty()) {
            Catalog catalog = Catalog.getCatalog();
            String queries = String.join(", ", ignoredQueries);
            String msg = ignoredQueries.size() > 1 ? "em.import.ignoredQueries" : "em.import.ignoredQuery";
            failedList.add(catalog.getString(msg, queries));
         }

         if(decryptFailures.get() > 0) {
            // a warning, not a failure, the assets are imported
            Catalog catalog = Catalog.getCatalog();
            String msg = undecryptableAssets.isEmpty() ?
               catalog.getString("em.import.undecryptableSecrets") :
               catalog.getString("em.import.undecryptableSecrets.assets",
                                 String.join(", ", undecryptableAssets));
            info.getImportWarnings().add(msg);
         }
      }
      finally {
         PasswordEncryption.setDecryptForceLocal(false);
         PasswordEncryption.setMasterDecryptFailures(null);
      }
   }

   private void importAssets0(boolean overwriting,
                              final List<String> order,
                              DeploymentInfo info,
                              boolean desktop, Principal principal,
                              List<String> ignoreList,
                              ActionRecord actionRecord,
                              List<String> failedList,
                              ImportTargetFolderInfo targetFolderInfo,
                              List<String> ignoreUserAssets,
                              Map<String, Boolean> bookmarkResolutions,
                              Set<String> undecryptableAssets)
      throws Exception
   {
      List<AssetEntry> vss = new ArrayList<>();
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(overwriting);

      if(bookmarkResolutions != null && !bookmarkResolutions.isEmpty()) {
         config.setContextAttribute("bookmarkResolutions", bookmarkResolutions);
      }

      File[] files = info.getFiles();
      Map<String, String> names = info.getNames();
      List<PartialDeploymentJarInfo.RequiredAsset> ignoreAssets = new ArrayList<>();
      List<String> ignoreSub = new ArrayList<>();
      String currOrg = OrganizationManager.getInstance().getCurrentOrgID(principal);

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
         EmbeddedTableStorage embeddedTables = embeddedTableStorage;

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

               AssetObject newEntry = getAssetObjectByAsset(newAsset);
               changeAssetMap.put(supportEntry, newEntry);

               if(supportEntry instanceof AssetEntry) {
                  AssetObject currOrgEntry = ((AssetEntry) supportEntry).cloneAssetEntry(
                                             new Organization(OrganizationManager.getInstance().getCurrentOrgID()));
                  changeAssetMap.put(currOrgEntry, newEntry);
               }

            }

            List<File> unImportedFile = new ArrayList<>();

            for(File file : files) {
               actionRecord = SUtil.getActionRecord(
                  principal, ActionRecord.ACTION_NAME_IMPORT, null, null);

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

                  // DeploymentInfo.processDcNames() replaces "^_^" with "/" in all zip entry
                  // names before import, so the separator here is "/" not "^_^".
                  int idx = fname.indexOf('/');

                  if(idx >= 0) {
                     fname = fname.substring(idx + 1);
                  }

                  if(ignoreUserAssets.contains(fname)) {
                     continue;
                  }
               }

               XAsset asset = DeployHelper.getAsset(file, names);

               if(asset == null) {
                  importAsset(file, null, ignoreSub, failedList, embeddedTables, ignoreAssets,
                              vss, overwriting, actionRecord, info, desktop, config, dataSpace,
                              principal);
                  continue;
               }

               if(isIgnoreAsset(asset, ignoreAssets) || ignoreUserAssets.contains(asset.getPath())) {
                  continue;
               }

               Set<AssetObject> dependencies = helper.getDependencies(asset);
               File transformFile = helper.getTransformFile(asset);
               String originalOrg = asset != null && asset.getUser() != null ? asset.getUser().orgID : null;

               if(OrganizationManager.getInstance().isSiteAdmin(principal) && asset != null) {
                  if(asset.getUser() != null) {
                     asset.getUser().setOrgID(currOrg);
                  }

                  if(asset instanceof AbstractSheetAsset) {
                     ((AbstractSheetAsset) asset).getAssetEntry().setOrgID(currOrg);

                     if(((AbstractSheetAsset) asset).getAssetEntry().getUser() != null) {
                        ((AbstractSheetAsset) asset).getAssetEntry().getUser().setOrgID(currOrg);
                     }

                     ((AbstractSheetAsset) asset).getAssetEntry().toIdentifier(true);
                  }
               }

               AssetObject entry = getAssetObjectByAsset(asset);

               //requires checking against raw dependency on file, revert to original
               if(OrganizationManager.getInstance().isSiteAdmin(principal) && originalOrg != null) {
                  Set<AssetObject> originalDependencies = dependencies.stream()
                     .map(dep -> (AssetObject) dep.clone())
                     .collect(Collectors.toSet());

                  for(AssetObject assetObject : originalDependencies) {
                     if(assetObject instanceof AssetEntry) {
                        ((AssetEntry) assetObject).setOrgID(originalOrg);

                        if(((AssetEntry) assetObject).getUser() != null) {
                           ((AssetEntry) assetObject).getUser().setOrgID(originalOrg);
                        }

                        ((AssetEntry) assetObject).toIdentifier(true);
                        AssetEntry assetEntry = (AssetEntry) assetObject;
                        IdentityID currentUser = assetEntry.getUser();
                        AssetEntry newOrgAsset;

                        if(currentUser != null) {
                           newOrgAsset = assetEntry.cloneAssetEntry(
                              currentUser, new IdentityID(currentUser.getName(), currOrg));
                        }
                        else {
                           newOrgAsset = (AssetEntry) assetEntry.clone();
                        }

                        newOrgAsset.setOrgID(currOrg);
                        newOrgAsset.toIdentifier(true);
                        changeAssetMap.put(assetObject, newOrgAsset);
                     }
                  }

                  dependencies.addAll(originalDependencies);
               }

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

               AtomicInteger decryptFailures = PasswordEncryption.getMasterDecryptFailures();
               int decryptFailuresBefore = decryptFailures == null ? 0 : decryptFailures.get();

               try {
                  IS_IMPORTING.set(true);
                  importAsset(file, nAsset, ignoreSub, failedList, embeddedTables,
                              ignoreAssets, vss, overwriting, actionRecord, info, desktop, config,
                              dataSpace, principal);
               }
               finally {
                  IS_IMPORTING.remove();
               }

               if(decryptFailures != null && decryptFailures.get() > decryptFailuresBefore) {
                  undecryptableAssets.add((nAsset != null ? nAsset : asset).getPath());
               }
            }

            for(File file : unImportedFile) {
               importAsset(file, null, ignoreSub, failedList,
                           embeddedTables, ignoreAssets,
                           vss, overwriting, actionRecord, info, desktop, config, dataSpace,
                           principal);
            }
         }
         finally {
            // @by stephenwebster, Save the manager once to prevent unnecessary save and reloads
            // which can feel slow on the GUI.
            LibManager manager = libManagerProvider.getManager(principal);

            if(manager.isDirty()) {
               manager.save();
            }
         }
      }

      Tool.deleteFile(this.fileSystemService.getFile(info.getUnzipFolderPath()));
      AssetRepository repository = AssetUtil.getAssetRepository(false);

      try {
         for(AssetEntry entry : vss) {
            Viewsheet vs = (Viewsheet) repository.getSheet(entry, null, false,
               AssetContent.ALL);
            ViewsheetSandbox box = new ViewsheetSandbox(vs,
               Viewsheet.SHEET_DESIGN_MODE, null, false, entry);

            // Bug #77609: the sandbox is only needed for updateAssemblies()
            try {
               box.updateAssemblies();
            }
            finally {
               box.dispose();
            }
         }
      }
      catch(Throwable e) {
         LOG.error("Failed to create materialized views", e);
      }
   }

   /**
    * Scans the unzipped import JAR for bookmark conflicts — {@code (user, bookmarkName)} pairs
    * where an entry exists in both the import and the current repository.
    *
    * @param info             deployment info including files and names
    * @param targetFolderInfo the target folder for import remapping (may be null)
    * @param ignoreList       list of dependent asset indices to exclude
    * @return list of conflicts, one entry per conflicting {@code (viewsheetPath, user, bookmarkName)} triple
    */
   public List<BookmarkConflict> getBookmarkConflicts(
      DeploymentInfo info, ImportTargetFolderInfo targetFolderInfo,
      List<String> ignoreList) throws Exception
   {
      List<BookmarkConflict> conflicts = new ArrayList<>();
      File[] files = info.getFiles();

      if(files == null || files.length == 0) {
         return conflicts;
      }

      // Build the set of ignored dependent assets
      List<PartialDeploymentJarInfo.RequiredAsset> ignoreAssets = new ArrayList<>();

      for(int i = 0; i < info.getDependentAssets().size(); i++) {
         if(ignoreList != null && ignoreList.contains(i + "")) {
            ignoreAssets.add(info.getDependentAssets().get(i));
         }
      }

      AssetRepository engine = AssetUtil.getAssetRepository(false);

      DeployHelper deployHelper = targetFolderInfo != null
         ? new DeployHelper(info, targetFolderInfo)
         : null;

      AssetEntry targetFolder = deployHelper != null ? deployHelper.getTargetFolder() : null;

      if(targetFolder != null && targetFolder.isRoot()) {
         targetFolder = null;
      }

      AssetEntry commonPrefixFolder = deployHelper != null && targetFolder != null
         ? deployHelper.getCommonPrefixFolder()
         : null;

      Map<String, String> names = info.getNames();

      for(File file : files) {
         if(file == null || !file.isFile()) {
            continue;
         }

         XAsset asset = DeployHelper.getAsset(file, names);

         if(asset == null || !ViewsheetAsset.VIEWSHEET.equals(asset.getType())) {
            continue;
         }

         // Skip assets excluded by the admin
         if(isIgnoreAsset(asset, ignoreAssets)) {
            continue;
         }

         if(targetFolder != null) {
            XAsset remapped = getChangeRootFolderAsset(asset, targetFolder,
               new HashSet<>(), commonPrefixFolder, false, new HashSet<>(), new HashMap<>(), false);

            if(remapped != null) {
               asset = remapped;
            }
         }

         AssetEntry entry = ((ViewsheetAsset) asset).getAssetEntry();

         try(InputStream in = new FileInputStream(file)) {
            Document doc = Tool.parseXML(in);

            if(doc == null) {
               continue;
            }

            Element root = doc.getDocumentElement();
            Element belem = Tool.getChildNodeByTagName(root, "AllBookmarks");

            if(belem == null) {
               continue;
            }

            NodeList usersList = Tool.getChildNodesByTagName(belem, "user");

            for(int i = 0; i < usersList.getLength(); i++) {
               if(!(usersList.item(i) instanceof Element)) {
                  continue;
               }

               Element userElem = (Element) usersList.item(i);
               String userName = Tool.getChildValueByTagName(userElem, "name");
               Element bookmarkElem = Tool.getChildNodeByTagName(userElem, "bookmarks");

               if(userName == null || bookmarkElem == null) {
                  continue;
               }

               VSBookmark imported = new VSBookmark();
               imported.parseXML(bookmarkElem);

               IdentityID userID = IdentityID.getIdentityIDFromKey(userName);
               userID.setOrgID(OrganizationManager.getInstance().getCurrentOrgID());

               // Bookmarks for an owner that does not exist in this organization are skipped
               // by ViewsheetAsset.parseContent0(), so never prompt to resolve them.
               if(!ViewsheetAsset.bookmarkOwnerExists(userID)) {
                  continue;
               }

               VSBookmark existing = engine.getVSBookmark(entry, new XPrincipal(userID));

               if(existing == null) {
                  continue;
               }

               for(String bName : imported.getBookmarks()) {
                  // INITIAL_STATE is always a mirror of HOME_BOOKMARK (written together in
                  // RuntimeViewsheet.saveBookmark when confirmed=true). Showing it as a
                  // separate conflict row would be confusing and redundant — skip it here;
                  // it is handled implicitly when HOME_BOOKMARK is resolved.
                  if(VSBookmark.INITIAL_STATE.equals(bName)) {
                     continue;
                  }

                  VSBookmarkInfo existingInfo = existing.getBookmarkInfo(bName);

                  if(existingInfo == null) {
                     continue; // no conflict — name only exists in import
                  }

                  VSBookmarkInfo importedInfo = imported.getBookmarkInfo(bName);
                  conflicts.add(BookmarkConflict.builder()
                     .viewsheetPath(entry.getPath())
                     .user(userID.convertToKey())
                     .userLabel(userID.getName())
                     .bookmarkName(bName)
                     .existingCreated(existingInfo.getCreateTime())
                     .existingModified(existingInfo.getLastModified())
                     .importedCreated(importedInfo != null ? importedInfo.getCreateTime() : -1L)
                     .importedModified(importedInfo != null ? importedInfo.getLastModified() : -1L)
                     .build());
               }
            }
         }
         catch(Exception e) {
            LOG.warn("Failed to scan bookmark conflicts in asset '{}'", entry.getPath(), e);
         }
      }

      return conflicts;
   }

   private static String getAssetFileIdentifier(XAsset asset) {
      return DeployHelper.getAssetFileIdentifier(asset);
   }

   private void transformAssetFile(AssetObject supportEntry, File transformFile,
                                   Set<AssetObject> dependencies,
                                   Map<AssetObject, AssetObject> changeAssetMap)
   {
      if(transformFile != null && dependencies != null && supportEntry != null) {
         Map<Integer, List<RenameInfo>> typeInfos =
            createRenameInfos(supportEntry, dependencies, changeAssetMap);

         if(!typeInfos.isEmpty()) {
            typeInfos.forEach((key, value) -> {
               RenameDependencyInfo renameDependencyInfo = new RenameDependencyInfo();
               renameDependencyInfo.setRenameInfo(supportEntry, value);

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

   private Map<Integer, List<RenameInfo>> createRenameInfos(
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

         AssetObject originDependency = dependency;

         AssetObject changedNewEntry = changeAssetMap.get(originDependency);

         if(originDependency instanceof AssetEntry entry) {
            originDependency = entry.cloneAssetEntry(
               new Organization(OrganizationManager.getInstance().getCurrentOrgID()));
            changedNewEntry = changeAssetMap.get(originDependency) == null ? changedNewEntry :
                                                                             changeAssetMap.get(originDependency);
         }

         boolean isTaskAsset = supportEntry instanceof AssetEntry && ((AssetEntry) supportEntry).isScheduleTask();
         boolean taskDependencyExtend = false;

         if(changedNewEntry == null && isTaskAsset && dependency instanceof AssetEntry dAssetEntry) {
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

         if(dependency instanceof AssetEntry assetEntry) {
            isCubeDs = "true".equals(assetEntry.getProperty("isCube")) && !assetEntry.isWorksheet() && !assetEntry.isViewsheet();

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
               String newPath = ((AssetEntry) Objects.requireNonNull(changedNewEntry)).getPath();
               boolean isQuery = supportEntry instanceof AssetEntry &&
                  ((AssetEntry) supportEntry).isQuery();
               int type = RenameInfo.DATA_SOURCE | RenameInfo.DATA_SOURCE_FOLDER;

               if(!isQuery) {
                  XDataSource dx = dataSourceRegistry.getDataSource(newPath);

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

         if(types.isEmpty()) {
            continue;
         }

         for(Integer type : types) {
            if(changedNewEntry instanceof AssetEntry changedNewEntryAsset) {
               RenameInfo renameInfo = null;
               AssetEntry dependencyAsset = (AssetEntry) dependency;

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
                  String oldName = dependencyAsset.toIdentifier(true);
                  String newName = changedNewEntryAsset.toIdentifier(true);

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

      while(graph.getAllNodes() != null && !graph.getAllNodes().isEmpty()) {
         List<TopologicalSortGraph<AssetObject>.GraphNode> graphNodes = graph.getLeafNodes();

         if(graphNodes.isEmpty() && !graph.getAllNodes().isEmpty()) {
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

   private boolean importAsset(File file, XAsset importAsAsset,
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

   private boolean importAsset(File file, XAsset importAsAsset,
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
      if(filename.startsWith("__")) {
         String folder = null;
         String fname = null;

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

            // DeploymentInfo.processDcNames() replaces "^_^" with "/" in all zip entry
            // names before import, so the separator here is "/" not "^_^".
            int idx = fname.indexOf('/');

            if(idx >= 0) {
               folder = fname.substring(0, idx);
               fname = fname.substring(idx + 1);
            }
         }

         if(folder == null) {
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
         String path = Objects.requireNonNull(asset).getPath();

         if(isIgnoreAsset(asset, ignoreAssets)) {
            return false;
         }

         if(asset instanceof TableStyleAsset) {
            path = asset.getPath();
         }

         if(!type.equals(asset.getType())) {
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

                  if(!(principal.getName().equals(owner.convertToKey()) || securityEngine.checkPermission(
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

                  if(!securityEngine.checkPermission(
                     principal, resource.getType(), resource.getPath(), action))
                  {
                     String assetName = null;

                     if(asset instanceof DeviceAsset) {
                        assetName = asset.getType().equals(DeviceAsset.DEVICE) ?
                           ((DeviceAsset) asset).getDeviceInfo().getName() : path;
                     }

                     String msg =  catalog.getString("em.import.file.failed.noPermission",
                        asset.getType() + " " + assetName);
                     failedList.add(msg);
                     LOG.warn(msg);
                     return false;
                  }
               }
            }

            if(asset instanceof XDataSourceAsset dataSourceAsset && principal != null &&
               !isImportedSecretIdsAllowed(dataSourceAsset, file, principal))
            {
               String msg = catalog.getString("em.import.file.failed.secretIdNotAllowed",
                  asset.getType() + " " + path);
               failedList.add(msg);
               LOG.warn(msg);
               return false;
            }

            // Bug #64331, the server paths of the data source xml are not trusted. An existing
            // data source that isn't overwritten is kept as it is, so nothing is checked
            if(asset instanceof XDataSourceAsset dataSourceAsset && principal != null &&
               !isKeptDataSource(dataSourceAsset, config) &&
               !isImportedServerPathsAllowed(file, principal))
            {
               String msg = catalog.getString("em.import.file.failed.serverPathNotAllowed",
                  asset.getType() + " " + path);
               failedList.add(msg);
               LOG.warn(msg);
               return false;
            }

            if(asset instanceof ScheduleTaskAsset && principal != null &&
               !isImportedScheduleSecretIdsAllowed(file, principal))
            {
               String msg = catalog.getString("em.import.file.failed.secretIdNotAllowed",
                  asset.getType() + " " + path);
               failedList.add(msg);
               LOG.warn(msg);
               return false;
            }

            // Bug #77281, the owner and execute-as identity of the task xml are not trusted
            if(asset instanceof ScheduleTaskAsset scheduleTaskAsset && principal != null &&
               !new ScheduleTaskIdentityChecker(securityEngine).isUnrestricted(principal))
            {
               if(!isImportedScheduleTaskAllowed(file, principal)) {
                  String msg = catalog.getString("em.import.file.failed.noPermission",
                     asset.getType() + " " + path);
                  failedList.add(msg);
                  LOG.warn(msg);
                  return false;
               }

               scheduleTaskAsset.setRestrictedImporter(principal);
            }

            if(ViewsheetAsset.VIEWSHEET.equals(type) && path.contains("/")) {
               String folder = path.substring(0, path.lastIndexOf("/"));
               setFolderProperty(folder, asset.getUser(), jarInfo);
            }

            if(asset instanceof VSAutoSaveAsset || asset instanceof WSAutoSaveAsset) {
               if(input.available() > 0) {
                  asset.parseContent(input, config, true, false);
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

               alias = jarInfo.getFolderAlias().get(entry.toIdentifier());

               if(selected) {
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

            if(asset instanceof VirtualPrivateModelAsset vpm) {
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
               asset.parseContent(input, config, true, OrganizationManager.getInstance().isSiteAdmin(principal));

               if(asset instanceof ScheduleTaskAsset) {
                  dependencyHandler.updateTaskDependencies((ScheduleTaskAsset) asset);

                  // Bug #77936, a warning, not a failure, the task is imported
                  if(!((ScheduleTaskAsset) asset).getClearedPasswordPaths().isEmpty()) {
                     info.getImportWarnings().add(Catalog.getCatalog().getString(
                        "em.import.schedulePasswordsCleared", asset.getPath()));
                  }
               }

               if(asset instanceof XDataSourceAsset) {
                  String dpath = ((XDataSourceAsset) asset).getDatasource();
                  XDataSource source = dataSourceRegistry.getDataSource(dpath);

                  if(source instanceof XMLADataSource) {
                     XDomain domain = repository.getDomain(dpath);
                     dependencyHandler.updateCubeDomainDependencies(domain, true);
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
                  (VSDashboard) dashboardRegistryManager.getRegistry(user).getDashboard(name);

               if(dashboard != null && dashboard.getViewsheet() != null) {
                  String id = dashboard.getViewsheet().getIdentifier();
                  UpdateDependencyHandler.addDashboardDepedency(id, entry);

                  if(user != null) {
                     Identity identity = new User(user, new String[0], new String[0],
                                                  new IdentityID[0], null, null);
                     dashboardManager.addDashboard(identity, name);
                  }
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

   /**
    * Determines if the importer may use the cloud secret ids that an imported data source
    * references. The ids are read from the imported XML before it is parsed, so that a rejected
    * id is never resolved.
    */
   boolean isImportedSecretIdsAllowed(XDataSourceAsset asset, File file, Principal principal)
      throws Exception
   {
      Set<String> secretIds;

      try(InputStream input = new FileInputStream(file)) {
         Document doc = input.available() > 0 ? Tool.parseXML(input) : null;

         if(doc == null) {
            return true;
         }

         secretIds = SecretIdAuthorizer.getCloudSecretIds(doc.getDocumentElement());
      }

      if(secretIds.isEmpty()) {
         return true;
      }

      XDataSource stored = dataSourceRegistry.getDataSource(asset.getDatasource());
      Predicate<String> check = new SecretIdAuthorizer(securityEngine, dataSourceRegistry)
         .createCheck(stored, principal);
      return secretIds.stream().allMatch(check);
   }

   /**
    * Bug #64331, determines if the importer may save the server paths, such as the root folder
    * of a Text/Excel Directory data source, that the data sources of an imported data source
    * asset set. A path is allowed when the importer may use any path, when it is under an
    * allowed root, or when it is the same as the path of the data source that it overwrites.
    */
   boolean isImportedServerPathsAllowed(File file, Principal principal) throws Exception {
      ServerFilePathPolicy policy = ServerFilePathPolicy.create(securityEngine);

      if(policy.isUnrestricted(principal)) {
         return true;
      }

      try(InputStream input = new FileInputStream(file)) {
         Document doc = input.available() > 0 ? Tool.parseXML(input) : null;

         if(doc == null) {
            return true;
         }

         NodeList nodes = doc.getDocumentElement().getElementsByTagName("datasource");

         for(int i = 0; i < nodes.getLength(); i++) {
            Element elem = (Element) nodes.item(i);

            // only the types with a server path are parsed, such as Text/Excel Directory, so
            // the other data sources of the file, JDBC and REST included, are not parsed here
            if(!hasServerPaths(Tool.getAttribute(elem, "type"))) {
               continue;
            }

            // a credential is never fetched while the paths are checked, the secret ids were
            // already checked and are fetched when the data source is imported
            XDataSource source = TabularDataSource.withCredentialFetchGate(id -> false, () -> {
               try {
                  XDataSourceWrapper wrapper = new XDataSourceWrapper();
                  wrapper.parseXML(elem);
                  return wrapper.getSource();
               }
               catch(Exception e) {
                  LOG.warn("Failed to parse the imported data source {}",
                           Tool.getAttribute(elem, "name"), e);
                  return null;
               }
            });

            // a data source with a server path that can't be parsed is refused
            if(source == null) {
               return false;
            }

            XDataSource stored = dataSourceRegistry.getDataSource(source.getFullName());

            if(policy.getRefusedPath(source, stored, principal) != null) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Determines if an imported data source type has a server path, such as the root folder of
    * a Text/Excel Directory data source.
    */
   private static boolean hasServerPaths(String type) {
      try {
         String cls = type == null ? null : Config.getConfig().getDataSourceClass(type);
         Class<?> dxClass = cls == null ? null : Config.getConfig().getClass(type, cls);
         return dxClass != null && TabularDataSource.class.isAssignableFrom(dxClass) &&
            ServerFilePathPolicy.hasServerPaths(dxClass);
      }
      catch(Exception e) {
         LOG.debug("Failed to get the class of the data source type {}", type, e);
         return false;
      }
   }

   /**
    * Determines if the import keeps an existing data source as it is, that is when it is not
    * overwritten (see XDataSourceAsset.parseContent).
    */
   private static boolean isKeptDataSource(XDataSourceAsset asset, XAssetConfig config) {
      // an additional connection is not visible, and is written by a different branch
      return asset.isVisible() &&
         (config == null || !config.isOverwriting()) && asset.exists();
   }

   /**
    * Determines if the importer may use the cloud secret ids that an imported schedule task
    * references. Parsing a task does not resolve its secret ids.
    */
   boolean isImportedScheduleSecretIdsAllowed(File file, Principal principal) throws Exception {
      if(!Tool.isCloudSecrets()) {
         return true;
      }

      ScheduleTask task;

      try(InputStream input = new FileInputStream(file)) {
         Document doc = input.available() > 0 ? Tool.parseXML(input) : null;
         Element taskElem = doc == null ? null :
            Tool.getChildNodeByTagName(doc.getDocumentElement(), "Task");

         if(taskElem == null) {
            return true;
         }

         task = new ScheduleTask();
         task.parseXML(taskElem);
      }

      ScheduleTask existing = ScheduleManager.getScheduleManager().getScheduleTask(task.getTaskId());
      return new ScheduleSecretIdChecker(securityEngine).isAllowed(task, existing, principal);
   }

   /**
    * Bug #77281, determines if an importer that is not a site admin may import a schedule
    * task. The task is parsed the same way ScheduleTaskAsset.parseContent stores it for such
    * an importer, with the owner and execute-as identity moved to the importer's organization,
    * and it gets the same checks as the schedule task import (ImportTaskController): the
    * scheduler permission regardless of the removable flag, no internal task or internal task
    * content, and an owner and execute-as identity the task editor lets the importer pick.
    */
   boolean isImportedScheduleTaskAllowed(File file, Principal principal) throws Exception {
      Element taskElem;
      boolean hasContent;

      try(InputStream input = new FileInputStream(file)) {
         Document doc = input.available() > 0 ? Tool.parseXML(input) : null;
         hasContent = doc != null;
         taskElem = doc == null ? null :
            Tool.getChildNodeByTagName(doc.getDocumentElement(), "Task");
      }

      // an empty file is not parsed at all. Bug #77454, a file without a task (e.g. only
      // folders) is refused before anything of it is written
      if(taskElem == null) {
         if(hasContent) {
            LOG.warn("Schedule task file is not imported, it contains no task");
         }

         return !hasContent;
      }

      if(!securityEngine.checkPermission(principal, ResourceType.SCHEDULER, "*",
                                         ResourceAction.ACCESS))
      {
         LOG.warn("Schedule task is not imported, the user doesn't have the scheduler permission");
         return false;
      }

      ScheduleTaskIdentityChecker checker = new ScheduleTaskIdentityChecker(securityEngine);
      ScheduleTask task = checker.parseImportedTask(taskElem, principal);
      String taskId = task.getTaskId();

      if(task.getType() == ScheduleTask.Type.INTERNAL_TASK ||
         ScheduleManager.isInternalTask(taskId) || ScheduleManager.isInternalTask(task.getName()))
      {
         LOG.warn("Internal task {} is not imported, it's not allowed for the user", taskId);
         return false;
      }

      if(!checker.isAllowed(task, taskId, principal)) {
         return false;
      }

      for(String internalTask : ScheduleTaskIdentityChecker.getInternalTaskContents(task)) {
         if(!securityEngine.checkPermission(principal, ResourceType.SCHEDULE_TASK, internalTask,
                                            ResourceAction.WRITE))
         {
            LOG.warn("Task {} is not imported, it contains the actions or conditions of " +
                     "the internal task {}", taskId, internalTask);
            return false;
         }
      }

      return true;
   }

   private static String getIdleSrtFileNameInSpace(String folder, String fname) {
      String targetPath = SreeEnv.getProperty("sree.home") + File.separator + folder;

      if(fname.endsWith(".srt")) {
         fname = fname.substring(0, fname.length() - 4);

         return SUtil.findIdleFileNameInSpace(targetPath, fname, ".srt");
      }

      return SUtil.findIdleFileNameInSpace(targetPath, fname, null);
   }

   public XAsset getChangeRootFolderAsset(XAsset asset,
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
                  String autoRename = Objects.requireNonNull(originalNewIdentifier)
                     .replace("^" + path, "^" + path + "_" + renameIndex);

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
               !Tool.isEmptyString(asset.getUser().name) && !(commonPrefixFolder.getUser() == null) &&
               !Tool.equals(asset.getUser().getName(), commonPrefixFolder.getUser().getName()))
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

   private XAsset autoRenameDataSourceAsset(XAsset xAsset) {
      return autoRenameDataSourceAsset(xAsset, null, null, null);
   }

   private XAsset autoRenameDataSourceAsset(XAsset xAsset, Set<AssetObject> dependencies,
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

      if(xAsset instanceof XDataSourceAsset dasset) {
         String[] existNames = dataSourceRegistry.getDataSourceNames();
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

      if(asset instanceof VSAutoSaveAsset autoSaveAsset) {

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
      RepletRegistry registry = RepletRegistryManager.getInstance().getRegistry(user);
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

   private final SecurityEngine securityEngine;
   private final DependencyHandler dependencyHandler;
   private final DataSourceRegistry dataSourceRegistry;
   private final DashboardRegistryManager dashboardRegistryManager;
   private final LibManagerProvider libManagerProvider;
   private final DashboardManager dashboardManager;
   private final XRepository repository;
   private final FileSystemService fileSystemService;
   private final DataSpace dataSpace;
   private final EmbeddedTableStorage embeddedTableStorage;
   private final RepletRegistryManager repletRegistryManager;
   public static final ThreadLocal<Boolean> IS_IMPORTING = ThreadLocal.withInitial(() -> Boolean.FALSE);
   private static final Logger LOG = LoggerFactory.getLogger(DeployManagerService.class);
}
