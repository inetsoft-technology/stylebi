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
package inetsoft.web.portal.data;

import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.RecycleBin;
import inetsoft.web.RecycleUtils;
import inetsoft.web.composer.AssetTreeController;
import inetsoft.web.portal.controller.SearchComparator;
import inetsoft.web.viewsheet.*;
import org.apache.commons.io.FileExistsException;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.lang.SecurityException;
import java.security.Principal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static inetsoft.uql.asset.AssetRepository.USER_SCOPE;

@Service
public class DataSetService {
   @Autowired
   public DataSetService(SecurityProvider securityProvider,
                         SecurityEngine securityEngine,
                         AssetRepository assetRepository,
                         DataSetSearchService dataSetSearchService)
   {
      this.securityProvider = securityProvider;
      this.securityEngine = securityEngine;
      this.assetRepository = assetRepository;
      this.dataSetSearchService = dataSetSearchService;
   }

   /**
    * Portal data browser.
    * @param folderPath folder path.
    * @param scope scope
    * @param home is move worksheet home folder.
    * @param principal user
    * @return PortalDataBrowserModel
    */
   public PortalDataBrowserModel getDataBrowser(String folderPath, int scope,
                                                String[] movingFolders, boolean home,
                                                Principal principal)
      throws Exception
   {
      if(home) {
         return this.getHomeBrowser(movingFolders, principal);
      }

      return this.getDataBrowser(folderPath, scope, movingFolders, principal);
   }

   private PortalDataBrowserModel getHomeBrowser(String[] movingFolders, Principal principal) {
      AssetEntry sharedWS = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, "/", null);

      List<WorksheetBrowserInfo> folders =
         getSortedEntryStram(new AssetEntry[]{ sharedWS }, AssetEntry.Type.FOLDER)
            .map(e -> getFolderInfo(e, movingFolders, principal))
            .collect(Collectors.toList());

      return PortalDataBrowserModel.builder()
         .root(true)
         .worksheetAccess(securityProvider.checkPermission(
            principal, ResourceType.WORKSHEET, "*", ResourceAction.ACCESS))
         .folders(folders)
         .build();
   }

   public PortalDataBrowserModel getDataBrowser(String folderPath, int scope,
                                                String[] movingFolders, Principal principal)
      throws Exception
   {
      IdentityID user = getUser(principal, scope);
      AssetEntry parentEntry = new AssetEntry(scope, AssetEntry.Type.FOLDER, folderPath, user);

      AssetEntry[] entries = assetRepository.getEntries(
         parentEntry, principal, ResourceAction.READ,
         new AssetEntry.Selector(AssetEntry.Type.FOLDER, AssetEntry.Type.WORKSHEET));

      List<WorksheetBrowserInfo> folders =
         getSortedEntryStram(entries, AssetEntry.Type.FOLDER)
            .filter(entry -> checkAssetPermission(principal, entry, ResourceAction.READ))
            .map(e -> getFolderInfo(e, movingFolders, principal))
            .collect(Collectors.toList());

      if("/".equals(folderPath) && scope == AssetRepository.GLOBAL_SCOPE) {
         AssetEntry privateWsFolder = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                 AssetEntry.Type.FOLDER, "/", user);

         folders.add(0, WorksheetBrowserInfo.builder()
                 .name(Catalog.getCatalog(principal).getString("User Worksheet"))
                 .path("/")
                 .type(AssetEntry.Type.FOLDER)
                 .scope(USER_SCOPE)
                 .id("")
                 .createdDate(-1)
                 .createdDateLabel("")
                 .modifiedDate(-1)
                 .modifiedDateLabel("")
                 .dateFormat(Tool.getDateFormatPattern())
                 .editable(false)
                 .deletable(false)
                 .materialized(false)
                 .canMaterialize(false)
                 .hasSubFolder(hasSubDataSetFolder(privateWsFolder, movingFolders, principal))
                 .workSheetType(-1)
                 .build());
      }

      List<WorksheetBrowserInfo> worksheets =
         getSortedEntryStram(entries, AssetEntry.Type.WORKSHEET)
            .filter(entry -> checkAssetPermission(principal, entry, ResourceAction.READ))
            .map(e -> getWorksheetInfo(e, movingFolders, principal))
            .collect(Collectors.toList());

      return PortalDataBrowserModel.builder()
         .root("/".equals(folderPath))
         .worksheetAccess(securityProvider.checkPermission(
            principal, ResourceType.WORKSHEET, "*", ResourceAction.ACCESS))
         .currentFolder(getBreadcrumbs(folderPath, scope, principal))
         .folders(folders)
         .files(worksheets)
         .build();
   }

   /**
    * Gets the browser view for a folder.
    *
    * @param path   the path to the folder.
    *
    * @return the browser view.
    *
    * @throws Exception if the browser view could not be loaded.
    */
   private List<WorksheetBrowserInfo> getBreadcrumbs(String path, int scope,
                                                     Principal principal)
         throws Exception
   {
      String[] paths = path.split("/");
      List<WorksheetBrowserInfo> folders = new ArrayList<>();
      IdentityID user = getUser(principal, scope);
      AssetEntry root = new AssetEntry(scope, AssetEntry.Type.FOLDER, "/", user);

      folders.add(getFolderInfo(root, principal));

      for(int i = 0; i < paths.length; i++) {
         if(i > 0) {
            paths[i] = paths[i - 1] + "/" + paths[i];
         }

         AssetEntry entry = getExistingAssetEntry(paths[i], scope, AssetEntry.Type.FOLDER,
                                                  principal);

         if(entry == null) {
            break;
         }

         folders.add(getFolderInfo(entry, principal));
      }

      return folders;
   }

   protected boolean matchPrivateWorksheetFolder(String query, Principal principal) {
      String name = Catalog.getCatalog(principal).getString("User Worksheet");

      int distance = StringUtils.getLevenshteinDistance(query, name);
      int lengthDifference = Math.abs(query.length() - name.length());
      int maxDistance = Math.min(query.length(), name.length());
      double equalPercentage = (maxDistance - (distance - lengthDifference)) / (double) maxDistance;

      if(equalPercentage > 0.8) {
         return true;
      }

      return false;
   }

   protected WorksheetBrowserInfo getUserWorksheetFolder(Principal principal) {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      AssetEntry privateWsFolder = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.FOLDER, "/", pId);

      return WorksheetBrowserInfo.builder()
         .name(Catalog.getCatalog(principal).getString("User Worksheet"))
         .path("/")
         .type(AssetEntry.Type.FOLDER)
         .scope(USER_SCOPE)
         .id("")
         .createdDate(-1)
         .createdDateLabel("")
         .modifiedDate(-1)
         .modifiedDateLabel("")
         .editable(false)
         .deletable(false)
         .materialized(false)
         .canMaterialize(false)
         .hasSubFolder(hasSubDataSetFolder(privateWsFolder, null, principal))
         .workSheetType(-1)
         .build();
   }

   public WorksheetBrowserInfo[] getSearchAssets(String folderPath, String query,
                                                 int scope, Principal principal)
      throws Exception
   {
      IdentityID user = getUser(principal, scope);
      AssetEntry entry = new AssetEntry(scope, AssetEntry.Type.FOLDER, folderPath, user);
      AssetEntry[] entries = dataSetSearchService.findAssets(entry, query, principal)
         .toArray(new AssetEntry[0]);
      Stream<WorksheetBrowserInfo> worksheetStream =
         getFilteredEntryStream(entries, AssetEntry.Type.WORKSHEET)
            .filter(e -> checkAssetPermission(principal, e, ResourceAction.READ))
            .map(e -> getWorksheetInfo(e, principal));
      Stream<WorksheetBrowserInfo> folderStream =
         getFilteredEntryStream(entries, AssetEntry.Type.FOLDER)
            .filter(e -> checkAssetPermission(principal, e, ResourceAction.READ))
            .map(e -> getFolderInfo(e, principal));

      return Stream.concat(folderStream, worksheetStream)
         .toArray(WorksheetBrowserInfo[]::new);
   }

   public WorksheetBrowserInfo[] getSortSearchResults(WorksheetBrowserInfo[] searchResults,
                                                     String query)
   {
      return Arrays.stream(searchResults).sorted(new SearchComparator.WorksheetBrowserComparator(query))
         .toArray(WorksheetBrowserInfo[]::new);
   }

   public CheckDuplicateResponse isEntryDuplicated(String oldPath, AssetEntry.Type type,
                                                   String newName, int scope, Principal principal)
      throws Exception
   {
      IdentityID user = getUser(principal, scope);
      int index = oldPath.lastIndexOf("/");
      String newPath = index < 0 || "/".equals(oldPath) ? newName : oldPath.substring(0, index + 1) + newName;
      AssetEntry newEntry = new AssetEntry(scope, type, newPath, user);

      return new CheckDuplicateResponse(AssetUtil.isDuplicatedEntry(assetRepository, newEntry));
   }

   public boolean checkItemsDuplicate(WorksheetBrowserInfo[] items, String path,
                                      int targetScope, Principal principal)
      throws Exception
   {
      boolean hasDuplicate = false;

      for(WorksheetBrowserInfo item : items) {
         String name = item.name();
         AssetEntry oldEntry = AssetEntry.createAssetEntry(item.id());

         if(oldEntry != null) {
            name = oldEntry.getName();
         }

         String itemPath = createPath(path, name);
         AssetEntry entry = new AssetEntry(targetScope, item.type(), itemPath,
            getUser(principal, targetScope));
         hasDuplicate = AssetUtil.isDuplicatedEntry(assetRepository, entry);

         if(hasDuplicate) {
            break;
         }
      }

      return hasDuplicate;
   }

   private Stream<AssetEntry> getSortedEntryStram(AssetEntry[] entries,
                                                  AssetEntry.Type filterType) {

      return getFilteredEntryStream(entries, filterType)
         .sorted(Comparator.comparing(entry -> entry.getName().toLowerCase()));
   }

   private Stream<AssetEntry> getFilteredEntryStream(AssetEntry[] entries,
                                                     AssetEntry.Type filterType)
   {
      return Arrays.stream(entries)
         .filter(e -> e.getType() == filterType)
         .filter(entry -> !RecycleUtils.isInRecycleBin(entry.getPath()) &&
            !SUtil.isMyReport(entry.getPath()));
   }

   /**
    * Determines whether there are sub dataset folders.
    *
    * @param entry the parent folder's entry.
    *
    * @param movingFolders the moving folders
    *
    * @return rure if has sub dataset folder
    */
   private boolean hasSubDataSetFolder(AssetEntry entry, String[] movingFolders,
                                       Principal principal)
   {
      if(entry != null && !entry.isFolder()) {
         return false;
      }

      AssetEntry[] children;

      try {
         children = assetRepository.getEntries(entry, principal, ResourceAction.READ,
                       new AssetEntry.Selector(AssetEntry.Type.FOLDER));

         if(isShearWS(entry)) {
            AssetEntry privateWS = new AssetEntry(
               USER_SCOPE, AssetEntry.Type.FOLDER, "/",
               getUser(principal, USER_SCOPE));

            children = (AssetEntry[]) ArrayUtils.add(children, privateWS);
         }
      }
      catch(Exception ex) {
         LOG.debug(
            "Failed to get sub entries for: {}", entry == null ? "null" : entry.getPath(), ex);
         return false;
      }

      if(children == null || children.length == 0 || movingFolders == null) {
         return false;
      }

      return Arrays.stream(children)
         .anyMatch(child -> !Arrays.asList(movingFolders).contains(child.getPath()));
   }

   private WorksheetBrowserInfo getFolderInfo(AssetEntry entry, Principal principal){
      return getFolderInfo(entry, null, principal);
   }

   /**
    * Gets the worksheet browser info for a folder.
    *
    * @param entry the folder's entry.
    *
    * @param movingFolders the moving folders
    *
    * @return the worksheet browser info.
    */
   private WorksheetBrowserInfo getFolderInfo(AssetEntry entry,
                                              String[] movingFolders, Principal principal)
   {
      updateEntryCreationDetails(entry);
      String modifiedDateLabel = entry.getModifiedDate() == null ? "" :
         new SimpleDateFormat(SreeEnv.getProperty("format.date.time")).format(entry.getModifiedDate());
      String createdDateLabel = entry.getCreatedDate() == null ? "" :
         new SimpleDateFormat(SreeEnv.getProperty("format.date.time")).format(entry.getCreatedDate());
      boolean admin = checkAssetPermission(principal, entry, ResourceAction.ADMIN);
      String parentPath = entry.getParentPath();

      if(entry.getScope() == USER_SCOPE && parentPath != null) {
         if("/".equals(parentPath)) {
            parentPath = Catalog.getCatalog(principal).getString("User Worksheet");
         }
         else {
            parentPath = Catalog.getCatalog(principal).getString("User Worksheet") +
                    "/" + parentPath;
         }
      }

      if(isShearWS(entry)) {
         entry.setAlias(Catalog.getCatalog(principal).getString("Worksheet"));
      }

      return WorksheetBrowserInfo.builder()
         .name(entry.getAlias() == null ? entry.getName() : entry.getAlias())
         .path(entry.getPath())
         .type(AssetEntry.Type.FOLDER)
         .scope(entry.getScope())
         .description(entry.getDescription())
         .id(entry.toIdentifier())
         .createdBy(entry.getCreatedUsername())
         .createdDate(entry.getCreatedDate() == null ? 0 : entry.getCreatedDate().getTime())
         .createdDateLabel(createdDateLabel)
         .modifiedDate(entry.getModifiedDate() == null ? 0 : entry.getModifiedDate().getTime())
         .modifiedDateLabel(modifiedDateLabel)
         .dateFormat(Tool.getDateFormatPattern())
         .editable(admin || checkAssetPermission(principal, entry, ResourceAction.WRITE))
         .deletable(admin || checkAssetPermission(principal, entry, ResourceAction.DELETE))
         .materialized(false)
         .canMaterialize(false)
         .parentPath(parentPath)
         .hasSubFolder(hasSubDataSetFolder(entry, movingFolders, principal))
         .workSheetType(-1)
         .build();
   }

   private boolean isShearWS(AssetEntry entry) {
      return entry != null && entry.getScope() == AssetRepository.GLOBAL_SCOPE &&
         entry.getType() == AssetEntry.Type.FOLDER && "/".equals(entry.getPath());
   }

   /**
    * @return true if permission is allowed, false otherwise.
    */
   private boolean checkAssetPermission(Principal principal, AssetEntry entry,
                                        ResourceAction action)
   {
      boolean allowed = true;

      try {
         assetRepository.checkAssetPermission(principal, entry, action);
      }
      catch(Exception e) {
         allowed = false;
      }

      return allowed;
   }

   protected WorksheetBrowserInfo getWorksheetInfo(AssetEntry entry, Principal principal) {
     return getWorksheetInfo(entry, null, principal);
  }

   protected WorksheetBrowserInfo getWorksheetInfo(AssetEntry entry,
                                                   String[] movingFolders, Principal principal)
   {
      updateEntryCreationDetails(entry);
      boolean editable = checkAssetPermission(principal, entry, ResourceAction.WRITE);
      final boolean deletable = entry.isEditable() &&
         checkAssetPermission(principal, entry, ResourceAction.DELETE);
      boolean canMaterialize = SreeEnv.getBooleanProperty("ws.mv.enabled") &&
         editable && securityProvider.checkPermission(principal, ResourceType.MATERIALIZATION,
                                                      "*", ResourceAction.ACCESS);
      String modifiedDateLabel = entry.getModifiedDate() == null ? "" :
         new SimpleDateFormat(SreeEnv.getProperty("format.date.time")).format(entry.getModifiedDate());
      String createdDateLabel = entry.getCreatedDate() == null ? "" :
         new SimpleDateFormat(SreeEnv.getProperty("format.date.time")).format(entry.getCreatedDate());

      String parentPath = entry.getParentPath();

      if(entry.getScope() == USER_SCOPE && parentPath != null) {
         if("/".equals(parentPath)) {
            parentPath = Catalog.getCatalog(principal).getString("User Worksheet");
         }
         else {
            parentPath = Catalog.getCatalog(principal).getString("User Worksheet") +
                    "/" + parentPath;
         }
      }

      return WorksheetBrowserInfo.builder()
         .name(entry.getAlias() == null || entry.getAlias().isEmpty() ?
                  entry.getName() : entry.getAlias())
         .path(entry.getPath())
         .type(AssetEntry.Type.WORKSHEET)
         .scope(entry.getScope())
         .description(entry.getProperty("description"))
         .id(entry.toIdentifier())
         .createdBy(SUtil.getUserAlias(new IdentityID(entry.getCreatedUsername(),
             OrganizationManager.getInstance().getCurrentOrgID())))
         .createdDate(entry.getCreatedDate() == null ? 0 : entry.getCreatedDate().getTime())
         .createdDateLabel(createdDateLabel)
         .dateFormat(Tool.getDateFormatPattern())
         .modifiedDate(entry.getModifiedDate() == null ? 0 : entry.getModifiedDate().getTime())
         .modifiedDateLabel(modifiedDateLabel)
         .editable(editable)
         .deletable(deletable)
         .materialized(AssetTreeController.getMaterialized(entry, principal))
         .canMaterialize(canMaterialize)
         .parentPath(parentPath)
         .hasSubFolder(hasSubDataSetFolder(entry, movingFolders, principal))
         .workSheetType(getWorksheetType(entry))
         .build();
   }

   /**
    * Get the worksheet type of an asset entry.
    */
   private int getWorksheetType(AssetEntry entry) {
      String val = entry.getProperty(AssetEntry.WORKSHEET_TYPE);
      val = val == null ? Worksheet.TABLE_ASSET + "" : val;
      return Integer.parseInt(val);
   }

   /**
    * Set createBy and createDate for the target asset model.
    */
   private void updateEntryCreationDetails(AssetEntry entry) {
      AssetEntry entry0 = entry;

      try {
         if((entry.getCreatedDate() == null || entry.getCreatedUsername() == null) &&
            entry.getParent() != null)
         {
            inetsoft.util.IndexedStorage storage = assetRepository.getStorage(entry.getParent());
            inetsoft.util.XMLSerializable obj =
               storage.getXMLSerializable(entry.getParent().toIdentifier(), null);

            if(obj instanceof AssetFolder) {
               entry0 = Arrays.stream(((AssetFolder) obj).getEntries())
                  .filter(e -> e.getName().equals(entry.getName()))
                  .findFirst()
                  .orElse(null);
            }
         }
      }
      catch(Exception ignore) {
      }

      if(entry0 == null) {
         return;
      }

      if(entry0.getCreatedUsername() != null) {
         IdentityID identityID = entry0.getUser();
         User user = identityID == null ? null :
            securityProvider.getUser(new IdentityID(entry0.getCreatedUsername(), identityID.orgID));

         if(user != null) {
            entry.setCreatedUsername(user.getAlias() == null ? user.getName() : user.getAlias());
         }
         else {
            entry.setCreatedUsername(entry0.getCreatedUsername());
         }
      }

      entry.setCreatedDate(entry0.getCreatedDate());
      entry.setModifiedDate(entry0.getModifiedDate());
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_RENAME,
      objectType = ActionRecord.OBJECT_TYPE_WORKSHEET
   )
   public void renameWorksheet(@AuditObjectName("'Data Worksheet/' + path()") WorksheetBrowserInfo info,
                               String newName,
                               int scope, Principal principal,
                               @SuppressWarnings("unused") @AuditActionError String message)
      throws Exception
   {
      final String path = info.path();
      scope = info.scope();
      newName = SUtil.removeControlChars(newName);
      IdentityID user = getUser(principal, scope);

      AssetEntry oldEntry = getExistingAssetEntry(path, scope, info.type(), principal);

      if(oldEntry == null) {
         throw new FileNotFoundException(path);
      }
      else if(!Tool.isEmptyString(oldEntry.getAlias())) {
         oldEntry.setAlias(newName);
         assetRepository.changeSheet(oldEntry, oldEntry, principal, true);
         return;
      }

      String parentPath = oldEntry.getParentPath();
      String newPath = "/".equals(parentPath) ? newName : oldEntry.getParentPath() + "/" + newName;
      AbstractSheet sheet = assetRepository.getSheet(oldEntry, principal, false, AssetContent.NO_DATA);
      AssetEntry newEntry = new AssetEntry(scope, AssetEntry.Type.WORKSHEET, newPath, user);

      if(sheet.getCreated() > 0) {
         newEntry.setCreatedDate(new Date(sheet.getCreated()));
      }

      if(sheet.getCreatedBy() != null && !"".equals(sheet.getCreatedBy())) {
         newEntry.setCreatedUsername(sheet.getCreatedBy());
      }

      if(sheet.getLastModified() > 0) {
         newEntry.setModifiedDate(new Date(sheet.getLastModified()));
      }

      if(sheet.getLastModifiedBy() != null && !"".equals(sheet.getLastModifiedBy())) {
         newEntry.setModifiedUsername(sheet.getLastModifiedBy());
      }

      // get permission for originally named resource
      Permission oldPermission = securityProvider.getPermission(ResourceType.ASSET, path);

      assetRepository.changeSheet(oldEntry, newEntry, principal, true);
      securityProvider.setPermission(ResourceType.ASSET, newPath, oldPermission);
      securityProvider.removePermission(ResourceType.ASSET, info.path());
   }

   public void renameFolder(String auditPath, WorksheetBrowserInfo info, String newName,
                            int scope, Principal principal)
   {
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal),
         ActionRecord.ACTION_NAME_RENAME, auditPath, ActionRecord.OBJECT_TYPE_FOLDER, null,
         ActionRecord.ACTION_STATUS_SUCCESS, "Target Entry: " + newName);
      newName = SUtil.removeControlChars(newName);

      try {
         String oldPath = info.path();
         IdentityID user = getUser(principal, scope);

         AssetEntry oldEntry = getExistingAssetEntry(oldPath, scope,  info.type(), principal);
         oldEntry = assetRepository.getAssetEntry(oldEntry);

         if(oldEntry == null) {
            throw new FileNotFoundException(oldPath);
         }

         Permission oldPermission = securityProvider.getPermission(ResourceType.ASSET, info.path());

         int index = oldPath.lastIndexOf("/");
         String newPath = index > 0 ? oldPath.substring(0, index + 1) + newName : newName;
         AssetEntry newEntry = new AssetEntry(scope, AssetEntry.Type.FOLDER, newPath, user);

         newEntry.copyMetaData(oldEntry);

         assetRepository.changeFolder(oldEntry, newEntry, principal, true);

         securityProvider.setPermission(ResourceType.ASSET, newPath, oldPermission);
         securityProvider.removePermission(ResourceType.ASSET, oldPath);
      }
      catch (Exception ex) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(ex.getMessage() + ", Target Entry: " + newName);
         LOG.error("Failed to rename " + auditPath + " to " + newName, ex);
      }
      finally {
         Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
         actionRecord.setActionTimestamp(actionTimestamp);

         if(actionRecord.isValid()) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }
   }

   private AssetEntry getExistingAssetEntry(String path, int scope, AssetEntry.Type type, Principal principal)
      throws Exception
   {
      IdentityID user = getUser(principal, scope);
      AssetEntry entry = new AssetEntry(scope, type, path, user);
      final String identifier = entry.toIdentifier();
      AssetEntry parent = entry.getParent();
      AssetEntry.Selector selector = new AssetEntry.Selector(type);

      if(entry.isWorksheet()) {
         selector = new AssetEntry.Selector(AssetEntry.Type.FOLDER, AssetEntry.Type.WORKSHEET);
      }

      return Arrays.stream(assetRepository.getEntries(
         parent, principal, ResourceAction.READ, selector))
         .filter(e -> identifier.equals(e.toIdentifier()))
         .findFirst()
         .orElse(null);
   }


   @Audited(
      actionName = ActionRecord.ACTION_NAME_CREATE,
      objectType = ActionRecord.OBJECT_TYPE_FOLDER
   )
   public void addFolder(@SuppressWarnings("unused") @AuditObjectName String auditPath,
                         String path, int scope, Principal principal) throws Exception
   {
      IdentityID user = getUser(principal, scope);
      AssetEntry entry = new AssetEntry(scope, AssetEntry.Type.FOLDER, path, user);
      AssetEntry parentEntry = entry.getParent();

      if(!assetRepository.containsEntry(parentEntry)) {
         throw new FileNotFoundException(parentEntry.getPath());
      }

      if(!securityProvider.checkPermission(principal, ResourceType.ASSET,
                                           parentEntry.getPath(), ResourceAction.WRITE))
      {
         throw new SecurityException(Catalog.getCatalog().getString(
            "Permission denied to write " + parentEntry.getPath()));
      }

      Date date = new Date();
      AssetUtil.updateMetaData(entry, principal, date.getTime());
      assetRepository.addFolder(entry, principal);
   }

   public AssetDependenciesResponse isFolderRemoveable(String path, int scope, Principal principal)
      throws Exception
   {
      IdentityID user = getUser(principal, scope);
      AssetEntry.Selector selector = new AssetEntry.Selector(AssetEntry.Type.WORKSHEET);
      AssetEntry pentry = new AssetEntry(scope, AssetEntry.Type.FOLDER, path, user);
      AssetEntry[] entries = assetRepository.getEntries(pentry, principal, ResourceAction.READ, selector);
      List<String> dependencies = new ArrayList<>();

      for(AssetEntry entry2 : entries) {
         try {
            assetRepository.checkSheetRemoveable(entry2, principal);
         }
         catch(DependencyException ex) {
            dependencies.addAll(checkDependencyException(ex));
         }
      }

      return new AssetDependenciesResponse(dependencies);
   }

   public AssetDependenciesResponse isWorksheetRemoveable(String path, int scope, Principal principal)
      throws Exception
   {
      List<String> dependencies = new ArrayList<>();
      IdentityID user = getUser(principal, scope);
      AssetEntry entry = new AssetEntry(scope, AssetEntry.Type.WORKSHEET, path, user);
      try {
         assetRepository.checkSheetRemoveable(entry, principal);
      }
      catch(DependencyException ex) {
         dependencies = checkDependencyException(ex);
      }

      return new AssetDependenciesResponse(dependencies);
   }

   /**
    * Gets the specified folder.
    *
    * @param path          the path to the folder.
    *
    * @return the folder.
    *
    * @throws Exception if the folder could not be obtained.
    */
   public WorksheetBrowserInfo getFolder(String path, int scope, Principal principal)
      throws Exception
   {
      IdentityID user = getUser(principal, scope);
      AssetEntry entry = new AssetEntry(scope, AssetEntry.Type.FOLDER, path, user);

      if(!checkAssetPermission(principal, entry, ResourceAction.READ)) {
         String url = "/api/data/folders/view/" + path;
         throw new inetsoft.sree.security.SecurityException(
            "User=" + principal.getName() + ", Path=" + url);
      }

      return getFolderInfo(entry, principal);
   }

   private List<String> checkDependencyException(DependencyException ex) {
      List<String> dependencies = new ArrayList<>();

      for(Object obj : ex.getEntries()) {
         if(!(obj instanceof AssetEntry)) {
            continue;
         }

         String path = ((AssetEntry) obj).getPath();

         if(!path.startsWith(RecycleUtils.RECYCLE_BIN_FOLDER + "/")) {
            dependencies.add(path);
         }
      }

      return dependencies;
   }

   /**
    * Move folders.
    *
    * @throws Exception if folder could not be moved
    */
   public void moveFolders(MoveCommand[] items, int oldScope, int scope, Principal principal)
      throws Exception
   {
      boolean errorOccured = false;
      int duplicateIndex = -1;
      ActionRecord record = null;

      StringBuilder errorMessage = new StringBuilder();
      Catalog catalog = Catalog.getCatalog(principal);

      for(int i = 0; i < items.length; i++) {
         try {
            String actionMessage = "Target Entry: "
               + getAuditPath(items[i].getPath(), scope, principal);

            record = new ActionRecord(SUtil.getUserName(principal), ActionRecord.ACTION_NAME_MOVE,
               getAuditPath(items[i].getOldPath(), oldScope, principal),
               ActionRecord.OBJECT_TYPE_FOLDER, new Timestamp(System.currentTimeMillis()),
               ActionRecord.ACTION_STATUS_SUCCESS, actionMessage);

            if(!securityProvider.checkPermission(principal, ResourceType.ASSET,
                                                items[i].getPath(), ResourceAction.WRITE))
            {
               String label = items[i].getPath();

               if(Tool.isEmptyString(label) || "/".equals(label)) {
                  label = scope == AssetRepository.GLOBAL_SCOPE ?
                     catalog.getString("Global Worksheet") :
                     catalog.getString("User Worksheet");
               }

               throw new inetsoft.sree.security.SecurityException(
                  catalog.getString("common.writeAuthority", label));
            }

            moveFolder(getAuditPath(items[i].getOldPath(), oldScope, principal),
                       items[i].getOldPath(), items[i].getId(), items[i].getPath(),
                       oldScope, scope, principal, actionMessage);
         }
         catch(Exception e) {
            if(e instanceof FileExistsException && duplicateIndex == -1) {
               duplicateIndex = i;
            }

            if(record != null) {
               record.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
               record.setActionError(e.getMessage());
            }

            errorOccured = true;

            if(errorMessage.indexOf(e.getMessage()) < 0) {
               errorMessage.append(e.getMessage());
               errorMessage.append("\n");
            }
         }
         finally {
            if(record != null) {
               Audit.getInstance().auditAction(record, principal);
            }
         }
      }

      if(duplicateIndex != -1) {
         throw new FileExistsException(items[duplicateIndex].getOldPath());
      }

      if(errorOccured) {
         throw new MessageException(errorMessage.toString());
      }
   }

   /**
    * root path is '/', so audit need fix.
    */
   public String getAuditPath(String path, int scope, Principal principal) {
      String rootLabel = scope == AssetRepository.GLOBAL_SCOPE
         ? Catalog.getCatalog(principal).getString("Global Worksheet")
         : Catalog.getCatalog(principal).getString("User Worksheet");

      String auditPath = rootLabel;

      if(!"/".equals(path)) { // target page is not root.
         auditPath = rootLabel + "/" + path;
      }

      return auditPath;
   }

   /**
    * Move a folder.
    *
    * @param oldPath           original path of the folder
    * @param targetFolderPath  name of folder to which the folder will be moved
    *
    * @throws Exception if folder could not be moved
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_MOVE,
      objectType = ActionRecord.OBJECT_TYPE_FOLDER
   )
   public void moveFolder(@SuppressWarnings("unused") @AuditObjectName String auditPath,
                          String oldPath, String oldId, String targetFolderPath, int oldScope,
                          int scope, Principal principal,
                          @SuppressWarnings("unused") @AuditActionError String message)
      throws Exception
   {
      IdentityID user = getUser(principal, scope);
      AssetEntry oldEntry = AssetEntry.createAssetEntry(oldId);

      if(oldEntry == null) {
         throw new IllegalArgumentException("Invalid asset ID: " + oldId);
      }

      oldEntry = getFolderAssetEntry(
         oldEntry.getParentPath(), oldEntry.toIdentifier(), oldScope, principal);

      if(oldEntry == null) {
         throw new FileNotFoundException(oldPath);
      }

      Permission oldPermission = securityEngine.getPermission(ResourceType.ASSET, oldPath);

      String newPath = createPath(targetFolderPath, oldEntry.getName());
      AssetEntry newEntry = new AssetEntry(scope, AssetEntry.Type.FOLDER, newPath, user);
      Catalog catalog = Catalog.getCatalog();

      if(!assetRepository.containsEntry(newEntry.getParent())) {
         throw new FileNotFoundException(newEntry.getParentPath());
      }
      else if(!checkAssetPermission(principal, oldEntry, ResourceAction.DELETE))
      {
         throw new inetsoft.sree.security.SecurityException(
            catalog.getString("common.deleteAuthority", oldEntry.getPath()));
      }
      else if(!checkAssetPermission(principal, newEntry.getParent(), ResourceAction.WRITE))
      {
         throw new inetsoft.sree.security.SecurityException(
            catalog.getString("common.writeAuthority", newEntry.getPath()));
      }
      newEntry.setModifiedDate(oldEntry.getModifiedDate());
      newEntry.setModifiedUsername(oldEntry.getModifiedUsername());
      newEntry.setCreatedDate(oldEntry.getCreatedDate());
      newEntry.setCreatedUsername(oldEntry.getCreatedUsername());
      assetRepository.changeFolder(oldEntry, newEntry, principal, true);
      RecycleBin recycleBin = RecycleBin.getRecycleBin();
      recycleBin.renameFolder(oldPath, newEntry.getPath());
      securityEngine.setPermission(ResourceType.ASSET, oldPath, oldPermission);
   }

   /**
    * Gets specified folder.
    *
    * @param parent       the path to the parent folder.
    * @param id        the id of the entry we are trying to match
    *
    * @return the folder.
    */
   private AssetEntry getFolderAssetEntry(String parent, String id, int scope, Principal principal)
      throws Exception
   {
      IdentityID user = getUser(principal, scope);
      AssetEntry pentry = new AssetEntry(scope, AssetEntry.Type.FOLDER, parent, user);

      AssetEntry[] entries = assetRepository.getEntries(pentry, principal,
                                          ResourceAction.READ, new AssetEntry.Selector(AssetEntry.Type.FOLDER));

      for(AssetEntry entry : entries) {
         if(entry.toIdentifier().equals(id)) {
            return entry;
         }
      }

      return null;
   }

   /**
    * Convenience method: Get path that results from combining the parent
    * path with the name.
    */
   public String createPath(String parentPath, String name) {
      if(parentPath == null || "".equals(parentPath) || "/".equals(parentPath)) {
         return name;
      }
      else {
         return parentPath + "/" + name;
      }
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_DELETE,
      objectType = ActionRecord.OBJECT_TYPE_FOLDER
   )
   public void deleteFolder(@SuppressWarnings("unused") @AuditObjectName String auditPath,
                            String path, int scope, Principal principal)
      throws Exception
   {
      RecycleBin recycleBin = RecycleBin.getRecycleBin();
      IdentityID user = getUser(principal, scope);
      AssetEntry entry = new AssetEntry(scope, AssetEntry.Type.FOLDER, path, user);

      if(!assetRepository.containsEntry(entry)) {
         throw new FileNotFoundException(entry.getPath());
      }

      entry = assetRepository.getAssetEntry(entry);

      if(RecycleUtils.isInRecycleBin(path)) {
         assetRepository.removeFolder(entry, principal, true);
         securityProvider.removePermission(ResourceType.ASSET, path);
         recycleBin.removeEntry(path);
      }
      else {
         AssetEntry binEntry = new AssetEntry(scope, AssetEntry.Type.FOLDER,
                                              RecycleUtils.getRecycleBinPath("/"), user);

         if(!assetRepository.containsEntry(binEntry)) {
            assetRepository.addFolder(binEntry, null);
         }

         Permission oldPermission = securityProvider.getPermission(ResourceType.ASSET, path);

         String newName = UUID.randomUUID().toString().replaceAll("-", "");
         AssetEntry newEntry = new AssetEntry(scope, AssetEntry.Type.FOLDER,
                                              RecycleUtils.getRecycleBinPath(newName), user);

         if(!assetRepository.containsEntry(newEntry.getParent())) {
            assetRepository.addFolder(newEntry.getParent(), principal);
         }

         newEntry.setCreatedDate(entry.getCreatedDate());
         newEntry.setCreatedUsername(entry.getCreatedUsername());
         newEntry.setModifiedDate(entry.getModifiedDate());
         newEntry.setModifiedUsername(entry.getModifiedUsername());

         assetRepository.changeFolder(entry, newEntry, principal, true);

         securityProvider.removePermission(ResourceType.ASSET, entry.getPath());
         securityProvider.setPermission(ResourceType.ASSET, newEntry.getPath(), oldPermission);

         recycleBin.addEntry(newEntry.getPath(), entry.getPath(), entry.getName(),
                             oldPermission, RepositoryEntry.WORKSHEET_FOLDER, entry.getScope(), entry.getUser());
      }
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_DELETE,
      objectType = ActionRecord.OBJECT_TYPE_WORKSHEET
   )
   public void deleteWorksheet(@AuditObjectName String path, int scope, Principal principal,
                               boolean force)
      throws Exception
   {
      IdentityID user = getUser(principal, scope);
      AssetEntry entry = new AssetEntry(scope, AssetEntry.Type.WORKSHEET, path, user);

      if(!assetRepository.containsEntry(entry)) {
         throw new FileNotFoundException(path);
      }

      if(RecycleUtils.isInRecycleBin(path)) {
         assetRepository.removeSheet(entry, principal, true);
         securityProvider.removePermission(ResourceType.ASSET, path);

         RecycleBin recycleBin = RecycleBin.getRecycleBin();
         recycleBin.removeEntry(entry.getPath());
      }
      else if(force) {
         assetRepository.removeSheet(entry, principal, true);
         securityProvider.removePermission(ResourceType.ASSET, path);
      }
      else {
         moveSheetToBin(entry, scope, principal);
      }
   }

   /**
    * Moves datasets to a different parent folder.
    *
    * @param items the move commands for the datasets.
    *
    * @throws Exception if the datasets could not be moved.
    */
   public void moveDataSets(MoveCommand[] items, int assetScope,
                            int targetScope, Principal principal)
      throws Exception
   {
      boolean errorOccured = false;
      int duplicateIndex = -1;
      ActionRecord record = null;

      Catalog catalog = Catalog.getCatalog(principal);
      StringBuilder errorMessage = new StringBuilder();

      for(int i = 0; i < items.length; i++) {
         try {
            String newPath = createPath(items[i].getPath(), items[i].getName());
            String actionMessage = "Target Entry: " +
               getAuditPath(newPath, targetScope, principal);

            record = new ActionRecord(SUtil.getUserName(principal), ActionRecord.ACTION_NAME_MOVE,
                  items[i].getOldPath(), ActionRecord.OBJECT_TYPE_WORKSHEET,
                  new Timestamp(System.currentTimeMillis()), ActionRecord.ACTION_STATUS_SUCCESS,
                  actionMessage);

            boolean userPermission = assetScope == USER_SCOPE && targetScope == USER_SCOPE &&
               securityProvider.checkPermission(principal, ResourceType.MY_DASHBOARDS,
                                                items[i].getPath(), ResourceAction.READ);

            if(!securityProvider.checkPermission(principal, ResourceType.ASSET, items[i].getPath(),
                                                ResourceAction.WRITE) && !userPermission)
            {
               String label = items[i].getPath();

               if(Tool.isEmptyString(label) || "/".equals(label)) {
                  label = targetScope == AssetRepository.GLOBAL_SCOPE ?
                     catalog.getString("Global Worksheet") :
                     catalog.getString("User Worksheet");
               }

               throw new inetsoft.sree.security.SecurityException(
                  catalog.getString("common.writeAuthority", label));
            }

            moveDataSet(items[i].getOldPath(), items[i], assetScope, targetScope, principal, "");
         }
         catch(Exception e) {
            if(e instanceof FileExistsException && duplicateIndex == -1) {
               duplicateIndex = i;
            }

            if(record != null) {
               record.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
               record.setActionError(e.getMessage());
            }

            errorOccured = true;

            if(errorMessage.indexOf(e.getMessage()) < 0) {
               errorMessage.append(e.getMessage());
               errorMessage.append("\n");
            }
         }
         finally {
            if(record != null) {
               Audit.getInstance().auditAction(record, principal);
            }
         }
      }

      if(duplicateIndex != -1) {
         throw new FileExistsException(items[duplicateIndex].getOldPath());
      }

      if(errorOccured) {
         throw new MessageException(errorMessage.toString());
      }
   }

   /**
    * Moves a dashboard to a different parent folder.
    *
    * @param path    the path of the dashboard to remove.
    * @param command the move command.
    *
    * @throws Exception if the folder could not be removed.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_MOVE,
      objectType = ActionRecord.OBJECT_TYPE_WORKSHEET
   )
   public void moveDataSet(@AuditObjectName String path, MoveCommand command, int assetScope,
                           int targetScope, Principal principal,
                           @SuppressWarnings("unused") @AuditActionError String message)
      throws Exception
   {
      moveSheet(path, command, assetScope, targetScope, principal);
   }

   /**
    * Move a dashboard or data set (AKA viewsheet or worksheet).
    * @param oldPath          the original path of the sheet
    * @param command          info needed to move sheet
    *
    * @throws Exception if dashboard or data set could not be moved
    */
   private void moveSheet(String oldPath, MoveCommand command, int assetScope, int targetScope,
                          Principal principal)
      throws Exception
   {
      String targetFolderPath = command.getPath();
      AssetEntry oldEntry = getSheetEntry(oldPath, false, assetScope, principal);
      long creationDate = command.getDate();

      if(oldEntry == null) {
         throw new FileNotFoundException("No file");
      }

      Permission oldPermission = securityEngine.getPermission(ResourceType.ASSET, oldPath);
      String newPath = createPath(targetFolderPath, oldEntry.getName());
      AssetEntry newEntry = new AssetEntry(targetScope, oldEntry.getType(),
         newPath, getUser(principal, targetScope));
      Catalog catalog = Catalog.getCatalog();

      if(!assetRepository.containsEntry(newEntry.getParent())) {
         throw new FileNotFoundException(newEntry.getParentPath());
      }
      else if(!checkAssetPermission(principal, oldEntry, ResourceAction.DELETE)) {
         throw new inetsoft.sree.security.SecurityException(
            catalog.getString("common.deleteAuthority", oldEntry.getPath()));
      }
      else if(!checkAssetPermission(principal, newEntry.getParent(), ResourceAction.WRITE))
      {
         throw new inetsoft.sree.security.SecurityException(
            catalog.getString("common.writeAuthority", newEntry.getPath()));
      }

      if(oldEntry.getAlias() != null) {
         newEntry.setAlias(oldEntry.getAlias());
      }

      newEntry.setCreatedDate(oldEntry.getCreatedDate());
      newEntry.setCreatedUsername(oldEntry.getCreatedUsername());
      newEntry.setModifiedDate(oldEntry.getModifiedDate());
      newEntry.setModifiedUsername(oldEntry.getModifiedUsername());
      assetRepository.changeSheet(oldEntry, newEntry, principal, true);
      RenameInfo rinfo = new RenameInfo(oldEntry.toIdentifier(),
         newEntry.toIdentifier(), (RenameInfo.ASSET | RenameInfo.SOURCE));
      RenameTransformHandler.getTransformHandler().addTransformTask(rinfo);
      DependencyHandler.getInstance().renameDependencies(oldEntry, newEntry);
      securityEngine.setPermission(ResourceType.ASSET, newPath, oldPermission);
   }

   /**
    * Gets the entry for a dashboard or dataset.
    * Returns null if the asset can't be found within the AssetRepository.
    *
    * @param path          the path to the dashboard or dataset
    * @param isDashboard   true for dashboard, false for datsaet
    *
    * @return the asset entry in the Asset Repository; null if it wasn't found
    *
    * @throws Exception if the asset entry could not be obtained.
    */
   protected AssetEntry getSheetEntry(String path, boolean isDashboard,
                                      int scope, Principal principal)
      throws Exception
   {
      AssetEntry result;

      if(isDashboard) {
         result = getAssetEntry(AssetEntry.Type.VIEWSHEET, path, scope, principal);
         result = result != null ? result :
            getAssetEntry(AssetEntry.Type.VIEWSHEET_SNAPSHOT, path, scope, principal);
      }
      else {
         result = getAssetEntry(AssetEntry.Type.WORKSHEET, path, scope, principal);
      }

      return result;
   }

   /**
    * Returns AssetEntry with the given properties. Returns null if the asset
    * can't be found within the AssetRepository.
    *
    * The returned entry does not contain metadata; use getAssetEntry(String id)
    * for that.
    * Constructs the entry with AssetRepository.GLOBAL_SCOPE and user = null.
    *
    * @param type Asset type
    * @param path path
    * @return null if the asset is not contained in the AssetRepository
    */
   private AssetEntry getAssetEntry(AssetEntry.Type type, String path,
                                    int scope, Principal principal)
      throws Exception
   {
      AssetEntry result = null;

      if(path != null) {
         result = new AssetEntry(scope, type, path, getUser(principal, scope));

         if(assetRepository.containsEntry(result)) {
            result = assetRepository.getAssetEntry(result);
         }
         else {
            result = null;
         }
      }

      return result;
   }

   private void moveSheetToBin(AssetEntry oldEntry, int scope, Principal principal) throws Exception {
      oldEntry = assetRepository.getAssetEntry(oldEntry);
      String newName = UUID.randomUUID().toString().replaceAll("-", "");
      IdentityID user = getUser(principal, scope);
      AssetEntry newEntry = new AssetEntry(scope, oldEntry.getType(),
                                           RecycleUtils.getRecycleBinPath(newName), user);

      Permission permission = securityProvider.getPermission(ResourceType.ASSET,
                                                             oldEntry.getPath());

      if(!assetRepository.containsEntry(newEntry.getParent())) {
         assetRepository.addFolder(newEntry.getParent(), principal);
      }

      newEntry.setCreatedDate(oldEntry.getCreatedDate());
      newEntry.setCreatedUsername(oldEntry.getCreatedUsername());
      newEntry.setModifiedDate(oldEntry.getModifiedDate());
      newEntry.setModifiedUsername(oldEntry.getModifiedUsername());

      assetRepository.changeSheet(oldEntry, newEntry, principal, true);

      securityProvider.removePermission(ResourceType.ASSET, oldEntry.getPath());
      securityProvider.setPermission(ResourceType.ASSET, newEntry.getPath(), permission);
      SecurityEngine.touch();

      RecycleBin recycleBin = RecycleBin.getRecycleBin();
      recycleBin.addEntry(newEntry.getPath(), oldEntry.getPath(), oldEntry.getName(), permission,
                          RepositoryEntry.WORKSHEET, oldEntry.getScope(), oldEntry.getUser());
   }

   private IdentityID getUser(Principal principal, int scope) {
      return scope == USER_SCOPE && principal != null ?
         IdentityID.getIdentityIDFromKey(principal.getName()) : null;
   }

   private final SecurityProvider securityProvider;
   private final SecurityEngine securityEngine;
   private final AssetRepository assetRepository;
   private final DataSetSearchService dataSetSearchService;
   private static final Logger LOG = LoggerFactory.getLogger(DataSetService.class);
}
