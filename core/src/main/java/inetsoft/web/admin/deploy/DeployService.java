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
package inetsoft.web.admin.deploy;

import inetsoft.report.LibManager;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.*;
import inetsoft.sree.internal.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistry;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.dep.*;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeService;
import inetsoft.web.admin.content.repository.RepletRegistryManager;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.security.auth.MissingResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;
import java.security.Principal;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;

@Service
public class DeployService {
   public DeployService(ContentRepositoryTreeService contentRepositoryTreeService,
                        SecurityEngine securityEngine)
   {
      this.contentRepositoryTreeService = contentRepositoryTreeService;
      this.securityEngine = securityEngine;
   }

   public ImportJarProperties setJarFile(String fpath, boolean gzipped) throws Exception {
      if(fpath == null) {
         throw new Exception(Catalog.getCatalog().getString("em.replet.uploadFile.noFile"));
      }

      File file = FileSystemService.getInstance().getFile(fpath);
      InputStream in = new BufferedInputStream(new FileInputStream(file));

      if(gzipped) {
         in = new GZIPInputStream(in);
      }

      List<String> fileOrders = new ArrayList<>();
      Map<String, String> names = new HashMap<>();
      String cacheFolder = DeployManagerService.setJarFile(in, fileOrders, names, true);

      return ImportJarProperties.builder()
         .unzipFolderPath(cacheFolder)
         .fileOrders(fileOrders)
         .names(names)
         .build();
   }

   public ExportedAssetsModel getJarFileInfo(DeploymentInfo info, Principal principal)
   {
      return getJarFileInfo(info, null, principal);
   }

   public ExportedAssetsModel getJarFileInfo(DeploymentInfo info,
                                             ImportTargetFolderInfo targetFolderInfo,
                                             Principal principal)
   {
      Catalog catalog = Catalog.getCatalog(principal);
      PartialDeploymentJarInfo jarInfo = info.getJarInfo();
      AssetEntry importCommonPrefix = DeployHelper.getImportCommonPrefix(info);
      DeployHelper helper = new DeployHelper(info, targetFolderInfo);
      final Map<AssetObject, AssetObject> changedMap = new HashMap<>();

      List<SelectedAssetModel> selectedEntityModels = info.getSelectedEntries().stream()
         .map(entry -> {
            SelectedAssetModel.Builder builder = SelectedAssetModel.builder()
               .path(getSelectedAssetPath(entry))
               .label(SUtil.getTaskNameWithoutOrg(
                  getSelectedAssetLabel(getSelectedAssetPath(entry),
                  DeployUtil.toRepositoryEntryType(entry.getType()))))
               .type(DeployUtil.toRepositoryEntryType(entry.getType()))
               .typeName(entry.getType())
               .typeLabel(getTypeLabel(entry.getType(), catalog))
               .icon(DeployUtil.iconPathToCSSClass(entry.getIcon()))
               .lastModifiedTime(entry.getLastModifiedTime())
               .user(entry.getUser());

            if(!SecurityEngine.getSecurity().isSecurityEnabled() &&
               (Tool.equals(entry.getType(), WSAutoSaveAsset.AUTOSAVEWS) ||
               Tool.equals(entry.getType(), VSAutoSaveAsset.AUTOSAVEVS)))
            {
               throw new MessageException(Catalog.getCatalog().getString("em.import.fail.fileList") + " " +
                                             Catalog.getCatalog().getString("em.import.userNoSecurity"));
            }

            if(targetFolderInfo != null && targetFolderInfo.getTargetFolder() != null &&
               !targetFolderInfo.getTargetFolder().isRoot())
            {
               XAsset asset = DeployUtil.createAsset(entry);

               if(asset instanceof FolderChangeableAsset) {
                  XAsset changeRootFolderAsset = DeployManagerService.getChangeRootFolderAsset(asset,
                     targetFolderInfo.getTargetFolder(), null, importCommonPrefix,
                     true, helper.getDependencies(asset), changedMap, true);

                  if(changeRootFolderAsset != null) {
                     builder.appliedTargetLabel(getSelectedAssetLabel(changeRootFolderAsset));
                     changedMap.put(DeployHelper.getAssetObjectByAsset(asset),
                        DeployHelper.getAssetObjectByAsset(changeRootFolderAsset));
                  }
               }
               else {
                  String selectedAssetLabel = getSelectedAssetLabel(getSelectedAssetPath(entry),
                                                                    DeployUtil.toRepositoryEntryType(entry.getType()));
                  builder.appliedTargetLabel(SUtil.getTaskNameWithoutOrg(selectedAssetLabel));
               }
            }

            return builder.build();
         })
         .collect(Collectors.toList());

      sortSelected(selectedEntityModels);

      List<PartialDeploymentJarInfo.RequiredAsset> dependentAssets = info.getDependentAssets();

      List<RequiredAssetModel> requiredAssetModels = IntStream.range(0, dependentAssets.size())
         .mapToObj((i) ->{
            RequiredAssetModel.Builder builder = RequiredAssetModel.builder()
               .name(dependentAssets.get(i).getPath() == null ? "" : dependentAssets.get(i).getPath())
               .label(getRequiredAssetLabel(
                  getDeviceLabel(dependentAssets.get(i)), dependentAssets.get(i).getPath() == null
                     ? "" : dependentAssets.get(i).getPath(), dependentAssets.get(i).getType()))
               .type(dependentAssets.get(i).getType())
               .typeLabel(getTypeLabel(dependentAssets.get(i).getType(), catalog))
               .requiredBy(dependentAssets.get(i).getRequiredBy())
               .deviceLabel(getDeviceLabel(dependentAssets.get(i)))
               .lastModifiedTime(dependentAssets.get(i).getLastModifiedTime())
               .user(dependentAssets.get(i).getUser())
               .index(i);

            if(targetFolderInfo != null && targetFolderInfo.isDependenciesApplyTarget() &&
               targetFolderInfo.getTargetFolder() != null &&
               !targetFolderInfo.getTargetFolder().isRoot())
            {
               XAsset asset = DeployUtil.getAsset(dependentAssets.get(i));

               if(asset instanceof FolderChangeableAsset || asset instanceof XQueryAsset) {
                  XAsset changeRootFolderAsset = DeployManagerService.getChangeRootFolderAsset(asset,
                     targetFolderInfo.getTargetFolder(), null, importCommonPrefix,
                     true, helper.getDependencies(asset), changedMap, true);

                  if(changeRootFolderAsset != null) {
                     builder.appliedTargetLabel(getRequiredAssetLabel(changeRootFolderAsset));
                     changedMap.put(DeployHelper.getAssetObjectByAsset(asset),
                        DeployHelper.getAssetObjectByAsset(changeRootFolderAsset));
                  }
               }
               else {
                  builder.appliedTargetLabel(getRequiredAssetLabel(
                     getDeviceLabel(dependentAssets.get(i)), dependentAssets.get(i).getPath(),
                     dependentAssets.get(i).getType()));
               }
            }

            return builder.build();
         })
         .collect(Collectors.toList());

      sortRequired(requiredAssetModels);

      //@temp yanie bug1410199639437
      //check if the jar file is created in newer version
      String version = jarInfo.getVersion();

      //if version is null, the jar is exported before V12.0
      boolean supportVersion = version == null;

      // check if the jar is exported in current version
      if(!supportVersion) {
         supportVersion = FileVersions.JAR_VERSION.equals(version);
      }

      // check if the jar is exported in historic version
      if(!supportVersion) {
         for(String hv : FileVersions.HIS_JAR_VERSIONS) {
            if(hv.equals(version)) {
               supportVersion = true;
               break;
            }
         }
      }

      String deploymentDate = new SimpleDateFormat(SreeEnv.getProperty("format.date.time"))
         .format(jarInfo.getDeploymentDate());

      return ExportedAssetsModel.builder()
         .name(jarInfo.getName())
         .deploymentDate(deploymentDate)
         .overwriting(jarInfo.isOverwriting())
         .selectedEntities(selectedEntityModels)
         .dependentAssets(requiredAssetModels)
         .newerVersion(!supportVersion)
         .build();
   }

   private String getDeviceLabel(PartialDeploymentJarInfo.RequiredAsset asset) {
      return getDeviceLabel(asset.getType(), asset.getTypeDescription());
   }

   private String getDeviceLabel(String type, String typeDescription) {
      return DeviceAsset.DEVICE.equals(type) ? typeDescription : null;
   }

   private String getSelectedAssetPath(PartialDeploymentJarInfo.SelectedAsset entry) {
      return getSelectedAssetPath(entry.getPath(), entry.getType(), entry.getUser());
   }

   private String getSelectedAssetPath(String path, String type, IdentityID user) {
      return isUser(user) ? getUserEntryPath(type, path) : path;
   }

   private String getRequiredAssetLabel(XAsset asset) {
      return getRequiredAssetLabel(getDeviceLabel(asset.getType(), asset.toString()),
         asset.getPath(), asset.getType());
   }

   private String getRequiredAssetLabel(String deviceLabel, String path, String type) {
      if(deviceLabel != null) {
         return deviceLabel;
      }

      String label = path;
      String specialPath = "inetsoft.report.style.";

      if("tablestyle".equals(type)) {
         label = label.replace("~", "/");
      }

      if(label != null && label.startsWith(specialPath)) {
         label = label.substring(specialPath.length());
      }

      return SUtil.getTaskNameWithoutOrg(label);
   }

   private String getSelectedAssetLabel(XAsset asset) {
      String type = asset.getType();
      String path = asset.getPath();
      IdentityID user = asset.getUser();

      return getSelectedAssetLabel(getSelectedAssetPath(path, type, user),
         DeployUtil.toRepositoryEntryType(type));
   }

   private String getSelectedAssetLabel(String label, int type) {
      if(type == RepositoryEntry.DASHBOARD && label.endsWith("__GLOBAL")) {
         label = Catalog.getCatalog().getString("dashboard.globalLabel")
            + " " + label.substring(0, label.length() - 8);
      }
      else if(type == RepositoryEntry.TABLE_STYLE) {
         label = label.replace("~", "/");
      }
      else if(type == RepositoryEntry.SCHEDULE_TASK) {
         int start = label.indexOf(" - ");
         int end = label.indexOf(":");

         if(start >= 0 && end >= 0) {
            label = label.substring(0, start) + label.substring(end);
         }
      }
      else if(type == RepositoryEntry.AUTO_SAVE_VS || type == RepositoryEntry.AUTO_SAVE_WS) {
         label = SUtil.trimAutoSaveOrganization(label);
      }

      return label;
   }

   private String getUserEntryPath(String type, String entryPath) {
      String path;

      if(WorksheetAsset.WORKSHEET.equalsIgnoreCase(type)) {
         path = Tool.MY_DASHBOARD + "/Worksheets/" + entryPath;
      }
      else if(DashboardAsset.DASHBOARD.equalsIgnoreCase(type)) {
         path = Tool.MY_DASHBOARD + "/" + entryPath;
      }
      else if(ScheduleTaskAsset.SCHEDULETASK.equalsIgnoreCase(type))
      {
         path = entryPath;
      }
      else {
         path = Tool.MY_DASHBOARD + "/" + entryPath;
      }

      return path;
   }

   public ImportAssetResponse importAsset(DeploymentInfo info, List<String> ignoreList,
                                          boolean overwriting,
                                          ImportTargetFolderInfo targetFolderInfo,
                                          Principal principal)
      throws Exception
   {
      Set<IdentityID> users = new HashSet<>();
      List<String> ignoreUserAssets = new ArrayList<>();
      boolean isAutoSave = false;
      boolean onlyScheduleTaskUsers = true;
      OrganizationManager manager = OrganizationManager.getInstance();
      boolean siteAdmin = manager.isSiteAdmin(principal);

      for(PartialDeploymentJarInfo.SelectedAsset entry : info.getSelectedEntries()) {
         if(!siteAdmin && !Tool.equals(entry.getUser().orgID, manager.getCurrentOrgID())) {
            ArrayList<String> errors = new ArrayList<>();
            errors.add(Catalog.getCatalog().getString("em.import.ignoreOtherOrgAssetsForOrgAdmin"));
            return ImportAssetResponse.builder()
               .failedAssets(errors)
               .failed(true).build();
         }

         if(VSAutoSaveAsset.AUTOSAVEVS.equals(entry.getType()) ||
            WSAutoSaveAsset.AUTOSAVEWS.equals(entry.getType()))
         {
            isAutoSave = true;
         }
         else if(isUser(entry.getUser())) {
            if(!ScheduleTaskAsset.SCHEDULETASK.equals(entry.getType())) {
               onlyScheduleTaskUsers = false;
            }

            users.add(entry.getUser());

            if(!Tool.equals(OrganizationManager.getInstance().getCurrentOrgID(), entry.getUser().orgID)) {
               ignoreUserAssets.add(entry.getPath());
            }
         }
      }

      for(PartialDeploymentJarInfo.RequiredAsset asset : info.getDependentAssets()) {
         if(isUser(asset.getUser())) {
            if(!ScheduleTaskAsset.SCHEDULETASK.equals(asset.getType())) {
               onlyScheduleTaskUsers = false;
            }

            users.add(asset.getUser());

            if(!Tool.equals(OrganizationManager.getInstance().getCurrentOrgID(), asset.getUser().orgID)) {
               ignoreUserAssets.add(asset.getPath());
            }
         }
      }

      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      boolean noUsers = users.isEmpty() ||
         users.size() == 1 && (users.contains("anonymous") || users.contains("_NULL_")) ||
         onlyScheduleTaskUsers;

      try {
         if(isAutoSave) {
            // if auto save has administrator, support import auto save assets.
            // if no security, only can login em by admin.
         }
         else if(!noUsers && !SecurityEngine.getSecurity().isSecurityEnabled()) {
            throw new Exception(
               Catalog.getCatalog().getString("em.import.userNoSecurity"));
         }
         else if(!noUsers && provider != null) {
            validateUsers(users, provider);
         }
      }
      catch(Exception e) {
         ArrayList<String> errors = new ArrayList<>();
         errors.add(e.getMessage());

         LOG.error(e.getMessage());
         return ImportAssetResponse.builder()
            .failedAssets(errors)
            .failed(true).build();
      }

      ActionRecord actionRecord = SUtil.getActionRecord(principal,
         ActionRecord.ACTION_NAME_IMPORT, null, null);
      AnalyticRepository repository = SUtil.getRepletRepository();

      if(repository instanceof RepletEngine) {
         List<String> list = new ArrayList<>();
         list.addAll(ignoreUserAssets);
         List<String> privateSembeddedData = info.getJarInfo().getDependeciesMap().get("privateSembeddedData");

         if(privateSembeddedData != null) {
            list.addAll(privateSembeddedData);
         }

         List<String> failedAssets = ((RepletEngine) repository).importAssets(
            overwriting, info.getProperties().fileOrders(), info, false, principal, ignoreList,
            targetFolderInfo, actionRecord, list);
         return ImportAssetResponse.builder()
            .failed(!failedAssets.isEmpty())
            .ignoreUserAssets(ignoreUserAssets)
            .failedAssets(failedAssets).build();
      }

      return ImportAssetResponse.builder()
         .ignoreUserAssets(new ArrayList<>())
         .failedAssets(new ArrayList<>())
         .failed(true).build();
   }

   private void validateUsers(Set<IdentityID> users, SecurityProvider provider) throws Exception {
      Set<IdentityID> unregistered = new HashSet<>(Arrays.asList(SUtil.getUnregisteredUsers()));

      for(IdentityID user : users) {
         if(provider.getUser(user) == null && !unregistered.contains(user)) {
            throw new Exception(Catalog.getCatalog().getString("em.import.missingUser", user.getName()));
         }
      }
   }

   public void importAssets(File zipFile, List<String> excluded, boolean overwrite,
                            Principal principal)
      throws Exception
   {
      importAssets(zipFile, excluded, overwrite, null, true, principal);
   }

   public void importAssets(File zipFile, List<String> excluded, boolean overwrite,
                            String targetFolder, boolean applyTargetToDependencies,
                            Principal principal)
      throws Exception
   {
      ImportJarProperties properties = setJarFile(zipFile.getAbsolutePath(), false);
      PartialDeploymentJarInfo info = DeployManagerService.getInfo(properties.unzipFolderPath());

      if(info == null) {
         throw new Exception("Failed to get Jar info from " + properties.unzipFolderPath());
      }

      DeploymentInfo deploymentInfo = new DeploymentInfo(info, properties);
      AssetEntry targetFolderAsset = null;

      if(!Tool.isEmptyString(targetFolder)) {
         targetFolderAsset = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
            AssetEntry.Type.REPOSITORY_FOLDER, targetFolder, null);
         AssetRepository repository = AssetUtil.getAssetRepository(false);
         targetFolderAsset = repository.getAssetEntry(targetFolderAsset);

         if(targetFolderAsset == null) {
            targetFolderAsset = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
               AssetEntry.Type.FOLDER, targetFolder, null);
            targetFolderAsset = repository.getAssetEntry(targetFolderAsset);
         }

         if(targetFolderAsset == null) {
            final DataSourceRegistry registry = DataSourceRegistry.getRegistry();

            if(registry.getDataSourceFolder(targetFolder) != null) {
               targetFolderAsset = new AssetEntry(AssetRepository.QUERY_SCOPE,
                  AssetEntry.Type.DATA_SOURCE_FOLDER, targetFolder, null);
            }
         }

         if(targetFolderAsset == null) {
            throw new MissingResourceException("Folder do not exist: " + targetFolder);
         }
      }

      getJarFileInfo(deploymentInfo, principal);

      List<XAsset> excludedAssets = excluded.stream()
         .map(AssetEntry::createAssetEntry)
         .filter(Objects::nonNull)
         .map(SUtil::getXAsset)
         .collect(Collectors.toList());
      List<String> ignoreList = new ArrayList<>();

      for(int i = 0; i < info.getDependentAssets().size(); i++) {
         if(containsAsset(info.getDependentAssets().get(i), excludedAssets)) {
            ignoreList.add(Integer.toString(i));
         }
      }

      ImportTargetFolderInfo targetFolderInfo = null;

      if(targetFolderAsset != null) {
         targetFolderInfo = new ImportTargetFolderInfo(targetFolderAsset,
            applyTargetToDependencies);
      }

      ImportAssetResponse response = importAsset(deploymentInfo, ignoreList, overwrite,
         targetFolderInfo, principal);

      if(response.failed()) {
         throw new Exception(
            "Failed to import the following assets: " +
            String.join(", ", response.failedAssets()));
      }
   }

   public ExportJarProperties createExport(ExportedAssetsModel exportedAssetsModel, Principal principal)
      throws Exception
   {
      String name = Tool.byteDecode(exportedAssetsModel.name());
      boolean overwriting = exportedAssetsModel.overwriting();
      List<SelectedAssetModel> entryData = exportedAssetsModel.selectedEntities();
      List<RequiredAssetModel> assetData = exportedAssetsModel.dependentAssets();
      List<XAsset> assets = getEntryAssets(entryData, principal);
      List<PartialDeploymentJarInfo.SelectedAsset> entryDataArray = DeployUtil.getEntryData(assets);
      assert assetData != null;
      List<PartialDeploymentJarInfo.RequiredAsset> assetDataArray = assetData.stream()
         .map(this::createRequiredAsset)
         .collect(Collectors.toList());

      PartialDeploymentJarInfo info = new PartialDeploymentJarInfo();
      info.setName(name);
      info.setDeploymentDate(new Timestamp(System.currentTimeMillis()));
      info.setOverwriting(overwriting);
      info.setSelectedEntries(entryDataArray);
      info.setDependentAssets(assetDataArray);

      File zipfile = FileSystemService.getInstance().getCacheFile(name + ".zip");
      DeployUtil.createExport(info, new FileOutputStream(zipfile));

      return ExportJarProperties.builder()
         .zipFilePath(zipfile.getPath())
         .build();
   }

   private PartialDeploymentJarInfo.RequiredAsset createRequiredAsset(RequiredAssetModel model) {
      PartialDeploymentJarInfo.RequiredAsset asset = new PartialDeploymentJarInfo.RequiredAsset();
      asset.setPath(model.name());
      asset.setType(model.type());
      asset.setUser(model.user());
      asset.setTypeDescription(model.typeDescription());
      asset.setRequiredBy(model.requiredBy());
      asset.setDetailDescription(model.detailDescription());
      asset.setAssetDescription(model.assetDescription());
      long lastModifiedTime = model.lastModifiedTime();

      if(lastModifiedTime != 0) {
         asset.setLastModifiedTime(lastModifiedTime);
      }

      return asset;
   }

   /**
    * Export assets by DeployManagerService.
    *
    * @param paths the paths of assets which is to be exported.
    */
   private void createExport(List<String> paths, List<String> excludeDependencies,
                             String name, Principal principal, OutputStream output)
      throws Exception
   {
      List<XAsset> assets = getXAssets(paths).stream()
         .filter(a -> checkPermission(a, principal))
         .collect(Collectors.toList());

      if(assets.isEmpty() && !paths.isEmpty()) {
         throw new Exception("No permission on any selected asset");
      }

      List<XAsset> excludeDependencyAssets = getXAssets(excludeDependencies);

      Timestamp deploymentDate = new Timestamp(System.currentTimeMillis());
      List<PartialDeploymentJarInfo.SelectedAsset> entryData =
         DeployUtil.getEntryData(assets);
      List<PartialDeploymentJarInfo.RequiredAsset> assetData =
         DeployUtil.getRequiredAssets(assets).stream()
            .filter(a -> !containsAsset(a, excludeDependencyAssets))
            .collect(Collectors.toList());

      PartialDeploymentJarInfo info = new PartialDeploymentJarInfo(
         name, deploymentDate, false, entryData, assetData);
      DeployUtil.createExport(info, output);
   }

   private boolean containsAsset(PartialDeploymentJarInfo.RequiredAsset asset,
                                 List<XAsset> assets)
   {
      return assets.stream()
         .anyMatch(a -> Objects.equals(a.getType(), asset.getType()) &&
            Objects.equals(a.getPath(), asset.getPath()) &&
            Objects.equals(a.getUser(), asset.getUser()));
   }

   public void downloadJar(ExportJarProperties properties, Consumer<InputStream> fn)
      throws Exception
   {
      String filePath = properties.zipFilePath();
      File file = FileSystemService.getInstance().getFile(filePath);

      try(InputStream in = new FileInputStream(file)) {
         fn.accept(in);
      }
      finally {
         Tool.deleteFile(file);
      }
   }

   public void exportAssets(String name, List<String> includes, List<String> excludes,
                            List<String> excludeDependencies, OutputStream output,
                            Principal principal) throws Exception
   {
      List<String> paths = getExportPaths(includes, excludes);

      if(paths == null || paths.isEmpty()) {
         throw new Exception("No assets match selection");
      }

      createExport(paths, findCheckedAssets(excludeDependencies), name, principal, output);
   }

   /**
    * Find the assets of the given patterns by DeployManagerService.
    *
    * @param patterns the specified patterns the pattern should be
    *                 '/global/worksheets/folder/subfolder/worksheet' or
    *                 '/user/username/worksheets/folder/subfolder/worksheet'
    *
    * @return all assets path that match the specified patterns
    */
   private List<String> findAssets(List<String> patterns) {
      List<String> results = new ArrayList<>();
      List<String> errPatterns = new ArrayList<>();

      for(String pattern : patterns) {
         if(pattern.isEmpty()) {
            continue;
         }

         List<String> subassets = findAssets(pattern);

         if(subassets == null || subassets.isEmpty()) {
            errPatterns.add(pattern);
         }

         if(subassets != null) {
            results.addAll(subassets);
         }
      }

      results.sort(Comparator.naturalOrder());
      String errStr;

      if(errPatterns.isEmpty()) {
         errStr = "";
      }
      else {
         errStr = Tool.concat(errPatterns.toArray(new String[0]), '\n');
      }

      results.add(0, errStr);
      return results;
   }

   /**
    * Import assets by DeployManagerService.
    *
    * @param data    the jar file is provided as a btye array.
    * @param replace indicates if existing assets should be overwritten.
    */
   public void importAssets(byte[] data, boolean replace) throws Exception {
      try {
         AnalyticRepository repository = SUtil.getRepletRepository();

         if(repository instanceof RepletEngine) {
            ((RepletEngine) repository).importAssets(data, replace);
         }
      }
      catch(Exception e) {
         throw new Exception(e.getMessage());
      }
   }

   /**
    * Get the export path array, it has merged the includes and excludes.
    */
   private List<String> getExportPaths(List<String> includePatterns, List<String> excludePatterns) {
      List<String> included = findCheckedAssets(includePatterns);
      Set<String> excluded = new HashSet<>(findCheckedAssets(excludePatterns));
      return included.stream()
         .filter(p -> !excluded.contains(p))
         .collect(Collectors.toList());
   }

   /**
    * Find assets from DeployManager.
    */
   private List<String> findCheckedAssets(List<String> patterns) {
      if(patterns == null) {
         return Collections.emptyList();
      }

      List<String> assets = findAssets(patterns);

      if(!assets.isEmpty()) {
         assets.remove(0);
      }

      return assets;
   }

   private boolean checkPermission(XAsset asset, Principal principal) {
      Resource resource = AssetUtil.getParentSecurityResource(asset);

      try {
         if(resource == null) {
            return true;
         }

         ResourceAction action = AssetUtil.getAssetDeployPermission(resource);
         return securityEngine.checkPermission(
            principal, resource.getType(), resource.getPath(), action);
      }
      catch(SecurityException e) {
         return false;
      }
   }

   /**
    * Get XAssets by paths.
    */
   private List<XAsset> getXAssets(List<String> paths) {
      ArrayList<XAsset> assets = new ArrayList<>();

      for(String path : paths) {
         assets.add(createXAsset(path));
      }

      return assets;
   }

   /**
    * Create an asset by the path, the format of the path may be
    * /global/worksheets/folder/subfolder/worksheet or
    * /user/username/worksheets/folder/subfolder/worksheet
    */
   private XAsset createXAsset(String path) {
      path = path.substring(1); // remove the first "/"
      String[] items = path.split("/");
      String scope = items[0];
      IdentityID user = null;
      int index = 0;

      if("global".equals(scope)) {
         index++;
      }
      else if("user".equals(scope)) {
         user = new IdentityID(items[1], OrganizationManager.getInstance().getCurrentOrgID());
         index += 2;
      }

      String type = items[index].toUpperCase();
      path = path.substring(path.indexOf(items[index]) + items[index].length() + 1);

      switch(type) {
      case "QUERY":
      case "DATASOURCE":
      case "LOGICALMODEL":
      case "PARTITION":
         type = "X" + type.toUpperCase();
         break;
      case "SNAPSHOT":
         type = "VSSNAPSHOT";
         break;
      default:
      }

      if(user == null && type.equals(DashboardAsset.DASHBOARD) && !path.endsWith("__GLOBAL")) {
         path += "__GLOBAL";
      }

      return SUtil.getXAsset(type, path, user);
   }

   /**
    * Get assets. Only for replet, viewhsheet and
    * snapshot type.
    * @param selectedEntities the specified entry array.
    * @return xasset.
    */
   public List<XAsset> getEntryAssets(List<SelectedAssetModel> selectedEntities,
                                      Principal principal)
   {
      if(selectedEntities == null) {
         return Collections.emptyList();
      }

      return selectedEntities.stream()
         .map(m -> getEntryAsset(m, principal))
         .filter(Objects::nonNull)
         .distinct()
         .collect(Collectors.toList());
   }

   private XAsset getEntryAsset(SelectedAssetModel model, Principal principal) {
      String type = repositoryEntryTypeToAssetType(model.type());

      if(type == null) {
         return null;
      }

      String entityName = type.equals(WorksheetAsset.WORKSHEET) ||
         type.equals(ViewsheetAsset.VIEWSHEET) || type.equals(DashboardAsset.DASHBOARD) ?
         this.contentRepositoryTreeService.getUnscopedPath(model.path()) :
         model.path();
      IdentityID entityUser = model.user();


      // @by arlinex, only global assets are supported
      IdentityID user = entityUser == null || "__NULL__".equals(entityUser.name) ? null : entityUser;
      XAsset asset = SUtil.getXAsset(type, entityName, user);

      if(model.lastModifiedTime() != 0) {
         asset.setLastModifiedTime(model.lastModifiedTime());
      }

      if(asset != null && asset.getType() == ViewsheetAsset.VIEWSHEET) {
         final String identifier = ((ViewsheetAsset) asset).getAssetEntry().toIdentifier();
         AssetEntry entry = getRegistryEntry(identifier, principal);

         if(entry == null) {
            // account for vs snapshots
            VSSnapshotAsset snapshotAsset =
               (VSSnapshotAsset) SUtil.getXAsset(VSSnapshotAsset.VSSNAPSHOT, entityName, user);
            final String vsSnapshotIdentifier = snapshotAsset.getAssetEntry().toIdentifier();
            entry = getRegistryEntry(vsSnapshotIdentifier, principal);

            if(entry != null) {
               asset = snapshotAsset;
            }
         }
      }

      return asset;
   }

   private AssetEntry getRegistryEntry(String identifier, Principal principal) {
      try {
         return registryManager.getAssetEntry(identifier, principal);
      }
      catch(Exception e) {
         throw new RuntimeException(
            "Failed to get registry entry '" + identifier + "' for " + principal, e);
      }
   }

   private String repositoryEntryTypeToAssetType(int repositoryEntryType) { // NOSONAR
      String type = null;

      if(RepositoryEntry.VIEWSHEET == repositoryEntryType) {
         type = ViewsheetAsset.VIEWSHEET;
      }
      else if(RepositoryEntry.AUTO_SAVE_VS == repositoryEntryType) {
         type = VSAutoSaveAsset.AUTOSAVEVS;
      }
      else if(RepositoryEntry.AUTO_SAVE_WS == repositoryEntryType) {
         type = WSAutoSaveAsset.AUTOSAVEWS;
      }
      else if(RepositoryEntry.SNAPSHOTS == repositoryEntryType) {
         type = VSSnapshotAsset.VSSNAPSHOT;
      }
      else if(RepositoryEntry.QUERY == repositoryEntryType) {
         type = XQueryAsset.XQUERY;
      }
      else if((RepositoryEntry.DATA_SOURCE & repositoryEntryType) == RepositoryEntry.DATA_SOURCE ||
         (RepositoryEntry.CUBE & repositoryEntryType) == RepositoryEntry.CUBE)
      {
         type = XDataSourceAsset.XDATASOURCE;
      }
      else if((RepositoryEntry.LOGIC_MODEL & repositoryEntryType) == RepositoryEntry.LOGIC_MODEL) {
         type = XLogicalModelAsset.XLOGICALMODEL;
      }
      else if((RepositoryEntry.PARTITION & repositoryEntryType) == RepositoryEntry.PARTITION) {
         type = XPartitionAsset.XPARTITION;
      }
      else if(RepositoryEntry.VPM == repositoryEntryType) {
         type = VirtualPrivateModelAsset.VPM;
      }
      else if(RepositoryEntry.WORKSHEET == repositoryEntryType) {
         type = WorksheetAsset.WORKSHEET;
      }
      else if(RepositoryEntry.SCRIPT == repositoryEntryType) {
         type = ScriptAsset.SCRIPT;
      }
      else if(RepositoryEntry.TABLE_STYLE == repositoryEntryType) {
         type = TableStyleAsset.TABLESTYLE;
      }
      else if(RepositoryEntry.DASHBOARD == repositoryEntryType) {
         type = DashboardAsset.DASHBOARD;
      }
      else if(RepositoryEntry.SCHEDULE_TASK == repositoryEntryType) {
         type = ScheduleTaskAsset.SCHEDULETASK;
      }

      return type;
   }

   public SelectedAssetModelList filterEntities(SelectedAssetModelList assets,
                                                Principal principal) throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      AssetRepository engine = AssetUtil.getAssetRepository(false);
      Set<SelectedAssetModel> selectedAssets = new HashSet<>(assets.selectedAssets());
      List<SelectedAssetModel> permittedEntities = new ArrayList<>();

      for(SelectedAssetModel entity : selectedAssets) {
         String assetType = repositoryEntryTypeToAssetType(entity.type());

         if(assetType != null && isEntityPermitted(entity, assetType, principal)) {
            XAsset xAsset = SUtil.getXAsset(assetType,
               RepletRegistryManager.splitMyReportPath(entity.path()), entity.user());
            long lastModifiedTime = entity.lastModifiedTime();

            if(entity.type() == RepositoryEntry.VIEWSHEET) {
               AbstractSheet currentSheet = ((ViewsheetAsset) xAsset).getCurrentSheet(engine);
               String description =
                  currentSheet == null ? entity.description() : currentSheet.getDescription();
               String typeName = repositoryEntryTypeToAssetType(entity.type());

               if(currentSheet != null) {
                  lastModifiedTime = currentSheet.getLastModified();
               }

               permittedEntities.add(SelectedAssetModel.builder()
                                        .from(entity)
                                        .label(entity.label() != null && !entity.label().isEmpty() ? entity.label() :
                                               getSelectedAssetLabel(entity.path(), entity.type()))
                                        .typeName(typeName)
                                        .typeLabel(getTypeLabel(typeName, catalog))
                                        .description(description)
                                        .lastModifiedTime(lastModifiedTime)
                                        .build());
            }
            else {
               String typeName = repositoryEntryTypeToAssetType(entity.type());
               permittedEntities.add(SelectedAssetModel.builder()
                                        .from(entity)
                                        .label(entity.label() != null && !entity.label().isEmpty() ? entity.label() :
                                               SUtil.getTaskNameWithoutOrg(getSelectedAssetLabel(entity.path(), entity.type())))
                                        .typeName(typeName)
                                        .typeLabel(getTypeLabel(typeName, catalog))
                                        .lastModifiedTime(lastModifiedTime)
                                        .build());
            }
         }
      }

      sortSelected(permittedEntities);

      return SelectedAssetModelList.builder()
         .selectedAssets(permittedEntities)
         .build();
   }

   private boolean isEntityPermitted(SelectedAssetModel entity, String assetType,
                                     Principal principal) throws SecurityException
   {
      String unscopedPath = contentRepositoryTreeService.getUnscopedPath(entity.path());

      XAsset xasset = SUtil.getXAsset(assetType, unscopedPath, entity.user());

      if(xasset instanceof VSAutoSaveAsset || xasset instanceof WSAutoSaveAsset) {
         return true;
      }

      Resource resource = xasset.getSecurityResource();

      if(resource == null) {
         return securityEngine.checkPermission(
            principal, ResourceType.ASSET, unscopedPath, ResourceAction.ADMIN);
      }
      else if(xasset.getUser() != null) {
         return principal.getName().equals(xasset.getUser().convertToKey()) || SecurityEngine.getSecurity().checkPermission(
            principal, ResourceType.SECURITY_USER, xasset.getUser(), ResourceAction.ADMIN);
      }
      else {
         return securityEngine.checkPermission(
            principal, resource.getType(), resource.getPath(), ResourceAction.ADMIN);
      }
   }

   /**
    * Gets the assets upon which the assets to be exported depend.
    *
    * @param selectedEntities the XML element containing the exported assets.
    *
    * @return the asset dependencies.
    */
   public RequiredAssetModelList getDependentAssetsList(List<SelectedAssetModel> selectedEntities,
                                                        Principal principal)
   {

      List<XAsset> entryAssets = getEntryAssets(selectedEntities, principal);
      final Map<XAsset, DependencyInfo> depAssetsMap = DeployUtil.getDependentAssets(entryAssets);
      final Set<XAsset> assetList = new LinkedHashSet<>(DeployUtil.getDependentAssetsList(entryAssets));

      List<RequiredAssetModel> requiredAssetModelList =
         assetList.stream()
            .map(asset -> createRequiredAssetModel(asset, depAssetsMap.get(asset), principal))
            .distinct()
            .collect(Collectors.toList());

      sortRequired(requiredAssetModelList);

      return RequiredAssetModelList.builder()
         .requiredAssets(requiredAssetModelList)
         .build();
   }

   private RequiredAssetModel createRequiredAssetModel(XAsset asset, DependencyInfo dependency,
                                                       Principal user)
   {
      String label = getRequiredAssetLabel(
         asset instanceof DeviceAsset ? asset.toString() : null, asset.getPath(), asset.getType());
      Catalog catalog = Catalog.getCatalog(user);
      return RequiredAssetModel.builder()
         .name(asset.getPath())
         .label(label)
         .type(asset.getType())
         .typeLabel(getTypeLabel(asset.getType(), catalog))
         .requiredBy(SUtil.getTaskNameWithoutOrg(dependency.getRequiredBy()))
         .user(asset.getUser())
         .detailDescription(dependency.getDescription())
         .typeDescription(asset.toString())
         .assetDescription(getAssetDescription(asset))
         .deviceLabel(asset instanceof DeviceAsset ? asset.toString() : null)
         .lastModifiedTime(dependency.getLastModifiedTime())
         .build();
   }

   /**
    * Find assets of a specific pattern.
    */
   private List<String> findAssets(String patternStr) {
      AssetSearchOptions opts = getSearchOptions(patternStr);
      List<XAsset> assets = new ArrayList<>();

      if("script".equals(opts.type) || "tablestyle".equals(opts.type)) {
         assets.addAll(searchLibManager(opts));
      }
      else if("dashboard".equals(opts.type)) {
        assets.addAll(searchDashboards(opts));
      }
      else {
         assets.addAll(searchIndexedStorage(opts));
      }

      return assets.stream()
         .map(asset -> getSelectorPath(asset, opts.global))
         .filter(Objects::nonNull)
         .collect(Collectors.toList());
   }

   private String getSelectorPath(XAsset asset, boolean global) {
      String path = asset.getPath();
      String type = asset.getType();
      IdentityID user = asset.getUser();
      StringBuilder fullPath = new StringBuilder();

      if(user == null && global) {
         fullPath.append("/global");
      }
      else if(user != null) {
         fullPath.append("/user/").append(user);
      }

      if("XQUERY".equals(type) || "XDATASOURCE".equals(type) ||
         "XLOGICALMODEL".equals(type) || "XPARTITION".equals(type))
      {
         type = type.substring(1);
      }
      else if("VSSNAPSHOT".equals(type)) {
         type = "SNAPSHOT";
      }

      fullPath.append("/").append(type.toLowerCase());

      if(user == null && DashboardAsset.DASHBOARD.equals(type) && path.endsWith("__GLOBAL")) {
         path = path.substring(0, path.indexOf("__GLOBAL"));
      }

      fullPath.append("/").append(path);
      return fullPath.toString();
   }

   private Stream<XAsset> getTableStyleAssets(String folder) {
      LibManager manager = LibManager.getManager();
      Stream<XAsset> assets = Arrays.stream(manager.getTableStyles(folder))
         .map(XTableStyle::getName)
         .map(n -> new TableStyleAsset(n.replaceAll(LibManager.SEPARATOR, "/")));
      Stream<XAsset> children = Arrays.stream(manager.getTableStyleFolders(folder))
         .flatMap(this::getTableStyleAssets);
      return Stream.concat(assets, children);
   }

   private void sortSelected(List<SelectedAssetModel> models) {
      models.sort(Comparator.comparing(SelectedAssetModel::typeName)
                     .thenComparing(SelectedAssetModel::label));
   }

   private void sortRequired(List<RequiredAssetModel> models) {
      models.sort(new Comparator<RequiredAssetModel>() {
         @Override
         public int compare(RequiredAssetModel o1, RequiredAssetModel o2) {
            int result = getType(o1).compareTo(getType(o2));

            if(result == 0) {
               result = o1.label().compareTo(o2.label());
            }

            return result;
         }

         private String getType(RequiredAssetModel model) {
            String type = model.type();

            if(VirtualPrivateModelAsset.VPM.equals(type)) {
               return "X" + VirtualPrivateModelAsset.VPM;
            }

            return type;
         }
      });
   }

   /**
    * Convert the pattern to regex.This method is divided into 2 steps, the
    * first step is to replace single '*' to "[^/]+", the second step is to
    * replace "/**" to "(/.+){0,}".
    */
   private String convertToReg(final String pattern) {
      // the first step
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < pattern.length(); i++) {
         char chr = pattern.charAt(i);

         if(chr != '*') {
            sb.append(chr);
         }
         else if((i > 0 && pattern.charAt(i - 1) != '*') &&
            (i < pattern.length() - 1 && pattern.charAt(i + 1) != '*' || i == pattern.length() - 1))
         {
            sb.append(SINGLE_ITEM_PATTERN);
         }
         else {
            sb.append('*');
         }
      }

      // the second step
      String result = sb.toString();
      return result.replace("**", MULTI_ITEM_PATTERN);
   }

   private boolean isUser(IdentityID user) {
      return user != null && !XAsset.NULL.equals(user.name) && !"null".equals(user.name);
   }

   private String getTypeLabel(String type, Catalog catalog) {
      if(StringUtils.hasText(type)) {
         return catalog.getString("asset.type." + type.toUpperCase());
      }

      return type;
   }

   private String getAssetDescription(XAsset asset) {
      if(asset instanceof AbstractSheetAsset) {
         AssetEntry entry = ((AbstractSheetAsset) asset).getAssetEntry();

         if(entry != null && entry.isWorksheet()) {
            return entry.getProperty("description");
         }
      }

      return null;
   }

   private List<XAsset> searchIndexedStorage(AssetSearchOptions opts) {
      List<XAsset> assets = null;
      final List<AssetEntry.Type> types = opts.getAssetEntryTypes();
      String assetPath = opts.path;

      if(types.contains(AssetEntry.Type.SCHEDULE_TASK)) {
         assetPath = "/" + assetPath;
      }

      Pattern pattern = Pattern.compile(convertToReg(assetPath));

      try {
         final IndexedStorage indexedStorage = IndexedStorage.getIndexedStorage();

         assets = indexedStorage.getKeys(Objects::nonNull)
            .stream()
            .map(AssetEntry::createAssetEntry)
            .filter(Objects::nonNull)
            .filter(e -> {
               // check if type and user is the same
               if(!(types.contains(e.getType()) && (Tool.equals(e.getUser(), opts.user) ||
                  e.getType() == AssetEntry.Type.SCHEDULE_TASK)))
               {
                  return false;
               }

               String path = e.getPath();
               return pattern.matcher(path).matches();
            })
            .map(e -> {
               // handle logic models and partition differently
               // need to find what part of the path is the data source name
               if(e.getType() == AssetEntry.Type.LOGIC_MODEL ||
                  e.getType() == AssetEntry.Type.EXTENDED_LOGIC_MODEL ||
                  e.getType() == AssetEntry.Type.PARTITION ||
                  e.getType() == AssetEntry.Type.EXTENDED_PARTITION)
               {
                  return getModelXAsset(e);
               }
               else if(e.getType() == AssetEntry.Type.SCHEDULE_TASK) {
                  return SUtil.getXAsset(ScheduleTaskAsset.SCHEDULETASK, e.getPath(), opts.user);
               }
               else {
                  return SUtil.getXAsset(e);
               }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
      }
      catch(Exception e) {
         LOG.error("Asset search failed", e);
      }

      return assets;
   }

   @SuppressWarnings("unchecked")
   private List<XAsset> searchLibManager(AssetSearchOptions opts) {
      Stream<XAsset> assetStream = "tablestyle".equals(opts.type) ?
         getTableStyleAssets(null) :
         XAssetEnumeration.getXAssetEnumeration(opts.type.toUpperCase()).stream();
      Pattern pattern = Pattern.compile(convertToReg(opts.path));
      return assetStream.filter((asset) -> Tool.equals(asset.getUser(), opts.user) &&
            pattern.matcher(asset.getPath()).matches())
         .collect(Collectors.toList());
   }

   private List<XAsset> searchDashboards(AssetSearchOptions opts) {
      DashboardRegistry registry = DashboardRegistry.getRegistry(opts.user);
      Pattern pattern = Pattern.compile(convertToReg(opts.path));
      List<XAsset> assets = new ArrayList<>();

      for(String name : registry.getDashboardNames()) {
         if(opts.global && name.endsWith("__GLOBAL")) {
            name = name.substring(0, name.indexOf("__GLOBAL"));
         }

         if(pattern.matcher(name).matches()) {
            assets.add(new DashboardAsset(name, opts.user));
         }
      }

      return assets;
   }

   private XAsset getModelXAsset(AssetEntry entry) {
      AssetEntry.Type type = entry.getType();
      String path = entry.getPath();
      DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      String[] dsNames = registry.getDataSourceFullNames();

      if(type == AssetEntry.Type.LOGIC_MODEL ||
         type == AssetEntry.Type.EXTENDED_LOGIC_MODEL)
      {
         for(String dsName : dsNames) {
            // find the data source this model belongs to
            if(path.startsWith(dsName)) {
               XDataModel model = registry.getDataModel(dsName);
               String logicModelName = path.substring(dsName.length() + 1);
               String[] split = logicModelName.split("/");
               XLogicalModel lmodel = null;

               for(String lname : split) {
                  if(lmodel != null) {
                     lmodel = lmodel.getLogicalModel(lname);
                  }
                  else {
                     lmodel = model.getLogicalModel(lname);
                  }

                  if(lmodel == null) {
                     break;
                  }
               }

               logicModelName = logicModelName.replace("/", "^");

               if(lmodel != null) {
                  return SUtil.getXAsset(XLogicalModelAsset.XLOGICALMODEL,
                                         dsName + "^" + logicModelName, null);
               }
            }
         }
      }
      else if(type == AssetEntry.Type.PARTITION || type == AssetEntry.Type.EXTENDED_PARTITION) {
         for(String dsName : dsNames) {
            // find the data source this partition belongs to
            if(path.startsWith(dsName)) {
               XDataModel model = registry.getDataModel(dsName);
               String partitionName = path.substring(dsName.length() + 1);
               String[] split = partitionName.split("/");
               XPartition partition = null;

               for(String pname : split) {
                  if(partition != null) {
                     partition = partition.getPartition(pname);
                  }
                  else {
                     partition = model.getPartition(pname);
                  }

                  if(partition == null) {
                     break;
                  }
               }

               if(partition != null) {
                  partitionName = partitionName.replace("/", "^");
                  return SUtil.getXAsset(XPartitionAsset.XPARTITION,
                                         dsName + "^" + partitionName, null);
               }
            }
         }
      }

      return null;
   }

   private AssetSearchOptions getSearchOptions(String patternStr) throws IllegalArgumentException {
      boolean global = false;
      String type = null;
      String path = null;
      IdentityID user = null;

      if(patternStr.startsWith("/global/")) {
         global = true;
         Pattern pattern = Pattern.compile(GLOBAL_ASSET_PATTERN);
         Matcher matcher = pattern.matcher(patternStr);

         if(matcher.matches()) {
            try {
               type = matcher.group(1);
               path = matcher.group(2);
            }
            catch(NumberFormatException e) {
               throw new IllegalArgumentException("Invalid pattern string: " + patternStr, e);
            }
         }
         else {
            throw new IllegalArgumentException("Invalid pattern string: " + patternStr);
         }
      }
      else if(patternStr.startsWith("/user/")) {
         Pattern pattern = Pattern.compile(USER_ASSET_PATTERN);
         Matcher matcher = pattern.matcher(patternStr);

         if(matcher.matches()) {
            try {
               user = new IdentityID(matcher.group(1), OrganizationManager.getInstance().getCurrentOrgID());
               type = matcher.group(2);
               path = matcher.group(3);
            }
            catch(NumberFormatException e) {
               throw new IllegalArgumentException("Invalid pattern string: " + patternStr, e);
            }
         }
         else {
            throw new IllegalArgumentException("Invalid pattern string: " + patternStr);
         }
      }

      // escape characters
      path = path.replace("^", "\\^");
      path = path.replace("(", "\\(");
      path = path.replace(")", "\\)");
      return new AssetSearchOptions(global, user, type, path);
   }

   private static class AssetSearchOptions {
      public AssetSearchOptions(boolean global, IdentityID user, String type, String path) {
         this.global = global;
         this.user = user;
         this.type = type;
         this.path = path;
      }

      public List<AssetEntry.Type> getAssetEntryTypes() {
         List<AssetEntry.Type> types = new ArrayList<>();

         switch(type) {
         case "report":
            types.add(AssetEntry.Type.REPLET);
            break;
         case "script":
            types.add(AssetEntry.Type.SCRIPT);
            break;
         case "tablestyle":
            types.add(AssetEntry.Type.TABLE_STYLE);
            break;
         case "viewsheet":
            types.add(AssetEntry.Type.VIEWSHEET);
            break;
         case "snapshot":
            types.add(AssetEntry.Type.VIEWSHEET_SNAPSHOT);
            break;
         case "worksheet":
            types.add(AssetEntry.Type.WORKSHEET);
            break;
         case "vpm":
            types.add(AssetEntry.Type.VPM);
            break;
         case "datasource":
            types.add(AssetEntry.Type.DATA_SOURCE);
            break;
         case "logicalmodel":
            types.add(AssetEntry.Type.LOGIC_MODEL);
            types.add(AssetEntry.Type.EXTENDED_LOGIC_MODEL);
            break;
         case "partition":
            types.add(AssetEntry.Type.PARTITION);
            types.add(AssetEntry.Type.EXTENDED_PARTITION);
            break;
         case "query":
            types.add(AssetEntry.Type.QUERY);
            break;
         case "task":
         case "scheduletask":
            types.add(AssetEntry.Type.SCHEDULE_TASK);
            break;
         }

         return types;
      }

      private boolean global;
      private String type;
      private String path;
      private IdentityID user;
   }

   private static final String SINGLE_ITEM_PATTERN = "[^/]+";
   private static final String MULTI_ITEM_PATTERN = "[^/]+(/.+){0,}";
   private static final String GLOBAL_ASSET_PATTERN = "/global/([^/]+)/(.+)";
   private static final String USER_ASSET_PATTERN = "/user/([^/]+)/([^/]+)/(.+)";
   private final ContentRepositoryTreeService contentRepositoryTreeService;
   private final SecurityEngine securityEngine;
   private final RepletRegistryManager registryManager = new RepletRegistryManager();
   private static final Logger LOG = LoggerFactory.getLogger(DeployService.class);
}
