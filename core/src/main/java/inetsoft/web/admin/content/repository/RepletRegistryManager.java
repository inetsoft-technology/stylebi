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
package inetsoft.web.admin.content.repository;

import inetsoft.report.internal.Util;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.VSSnapshot;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.*;

/**
 * Stateless class that provides support for managing the replet registry.
 *
 * @since 12.1
 */
@Service
public class RepletRegistryManager {
   /**
    * Creates a new instance of <tt>RepletRegistryManager</tt>.
    */
   public RepletRegistryManager() {
      this(true);
   }

   /**
    * Creates a new instance of <tt>RepletRegistryManager</tt>.
    *
    * @param addRepositoryRootFolder <tt>true</tt> to add a "Repository" root
    *                                folder to all repository entries.
    */
   public RepletRegistryManager(boolean addRepositoryRootFolder) {
      this.addRepositoryRootFolder = addRepositoryRootFolder;
   }

   /**
    * Renames a worksheet folder.
    *
    * @param oldPath   the path to the original folder.
    * @param newPath   the path to the new folder.
    * @param user      the user that owns the folder.
    * @param principal the principal identifying the current user.
    *
    * @return <tt>true</tt> if the folder was renamed; <tt>false</tt> otherwise.
    *
    * @throws Exception if an error prevented the folder from being renamed.
    */
   public boolean changeWorksheetFolder(String oldPath, String newPath,
                                        IdentityID user, Principal principal)
      throws Exception
   {
      if(oldPath.equals(newPath)) {
         return true;
      }

      if(!oldPath.equals(newPath) &&
         isDuplicateWorksheetName(newPath, user, true, principal))
      {
         return false;
      }

      AssetRepository repository = AssetUtil.getAssetRepository(false);
      int scope = user == null ? AssetRepository.GLOBAL_SCOPE :
         AssetRepository.USER_SCOPE;
      AssetEntry oldfolder = new AssetEntry(scope, AssetEntry.Type.FOLDER,
                                            oldPath, user);
      AssetEntry newfolder = new AssetEntry(scope, AssetEntry.Type.FOLDER,
                                            newPath, user);

      oldfolder = repository.getAssetEntry(oldfolder);
      newfolder.copyMetaData(oldfolder);

      repository.changeFolder(oldfolder, newfolder, principal, true);

      return true;
   }

   /**
    * Updates a repository folder.
    *
    * @param oldPath     the old folder path.
    * @param oldUser     the name of user in whose repository the old folder is
    *                    located.
    * @param newPath     the new folder path.
    * @param newUser     the name of user in whose repository the new folder is
    *                    located.
    * @param alias       the alias for the new folder.
    * @param description the description for the new folder.
    * @param replace     <tt>true</tt> to replace the old folder.
    * @param newRuleType the new archive rule for the folder.
    * @param ruleDate    the date for the new archive rule.
    * @param principal   the principal identifying the current user.
    *
    * @throws Exception if an error prevented the folder from being renamed.
    */
   public void updateRepositoryFolder(String oldPath, IdentityID oldUser,
                                      String newPath, IdentityID newUser,
                                      String alias, String description,
                                      boolean replace, String newRuleType,
                                      String ruleDate, Principal principal)
      throws Exception
   {
      RepletRegistry registry = RepletRegistry.getRegistry(oldUser);
      RepletRegistry registryTo = RepletRegistry.getRegistry(newUser);
      boolean sameScope = Tool.equals(oldUser, newUser);

      if(!newPath.equals(oldPath) || !sameScope) {
         if(!Tool.equals(oldPath, newPath)) {
            checkPermission(oldPath, ResourceType.REPORT, ResourceAction.DELETE, principal);
         }

         if(SUtil.isDuplicatedRepositoryPath(newPath, newUser)) {
            throw new DuplicateNameException();
         }

         if(sameScope && replace) {
            String result = registryTo.changeFolder(oldPath, newPath);

            if(!"true".equals(result)) {
               throw new RenameFailedException(result);
            }
         }
         else {
            int idx = newPath.lastIndexOf("/");
            String newFolder = idx > 0 ? newPath.substring(idx + 1) : newPath;
            int oidx = oldPath.lastIndexOf("/");
            String oldFolder = oidx > 0 ? oldPath.substring(oidx + 1) : oldPath;
            String oPath = oidx > 0 ? oldPath.substring(0, oidx + 1) : "";

            if(!Tool.equals(newFolder, oldFolder)) {
               registry.changeFolder(oldPath, oPath + newFolder);
               registry.save();

               oldPath = oPath + newFolder;
            }

            newPath = idx > 0 ? newPath.substring(0, idx) : "";
            copyFile(null, oldPath, oldUser,
                     RepositoryEntry.FOLDER, newPath,
                     newUser, RepositoryEntry.FOLDER,
                     replace, null, principal);
         }

         if(!sameScope && replace) {
            removeFile(oldPath, oldUser, RepositoryEntry.FOLDER, null,
                       principal);
         }

         ScheduleManager mgr = ScheduleManager.getScheduleManager();
         AssetEntry oentry = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, oldPath, null);
         AssetEntry nentry = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, newPath, null);
         mgr.renameSheetInSchedule(oentry, nentry);
      }

      registryTo.setFolderDescription(newPath, description);
      registryTo.setFolderAlias(newPath, alias, true);
      registryTo.save();
   }

   /**
    * Updates the meta-data of a worksheet or viewsheet.
    *
    * @param identifier  the asset identifier.
    * @param oldName     the old name of the asset.
    * @param newName     the new name of the asset.
    * @param alias       the display name of the asset.
    * @param description a descrption of the asset.
    * @param principal   the principal identifying the current user.
    *
    * @return the asset entry of the updated sheet.
    *
    * @throws Exception if the sheet could nto be updated.
    */
   public AssetEntry updateSheet(String identifier, String oldName, String newName,
                                 String alias, String description, Principal principal)
      throws Exception
   {
      boolean rename = !(newName == null || newName.equals(oldName));
      AssetEntry oentry = getAssetEntry(identifier, principal);

      if(oentry == null) {
         throw new FileNotFoundException(Catalog.getCatalog().getString(
            "common.asset.notFound", oldName));
      }

      AssetRepository repository = AssetUtil.getAssetRepository(false);
      AbstractSheet vs = repository.getSheet(oentry, principal, false,
                                             AssetContent.ALL);

      if(vs instanceof Viewsheet) {
         ((Viewsheet) vs).getViewsheetInfo().setDescription(description);
      }
      else if(vs instanceof VSSnapshot) {
         ((VSSnapshot) vs).setSnapshotDescription(description);
      }

      if(rename) {
         String opath = oentry.getPath();
         int idx = opath.lastIndexOf('/');
         String npath = idx < 0 ? newName : opath.substring(0, idx + 1) + newName;
         npath = SUtil.removeControlChars(npath);

         if(oentry.isWorksheet() && isDuplicateWorksheetName(
            npath, oentry.getUser(), false, principal))
         {
            LOG.warn(Catalog.getCatalog().getString("Duplicate Name"));
            throw new DuplicateNameException();
         }

         AssetEntry nentry = new AssetEntry(
            oentry.getScope(), oentry.getType(), npath, oentry.getUser());
         nentry.copyProperties(oentry);
         nentry.addFavoritesUser(oentry.getFavoritesUser());

         if(vs instanceof Viewsheet || vs instanceof VSSnapshot) {
            VSUtil.refreshBookmarkId(oentry, nentry);
         }

         if(SUtil.isDuplicatedEntry(repository, nentry)) {
            throw new DuplicateNameException();
         }

         repository.changeSheet(oentry, nentry, principal, true);
         oentry = nentry;
      }

      if(oentry.isWorksheet()) {
         oentry.setProperty("description", description);
      }

      oentry.setAlias(alias);
      repository.setSheet(oentry, vs, principal, true);
      return oentry;
   }

   /**
    * Copies a file.
    *
    * @param pathFrom the path to the original file.
    * @param userFrom the user that owns the original file.
    * @param typeFrom the type of the original file.
    * @param pathTo   the path to the target file.
    * @param userTo   the owner of the target file.
    * @param typeTo   the type of the target file.
    * @param move     <tt>true</tt> to move the file; <tt>false</tt> to copy only.
    * @param infos     a map of messages.
    * @param principal the principal identifying the current user.
    *
    * @return <tt>true</tt> if the file was copied; <tt>false</tt> otherwise.
    *
    * @throws Exception if an error prevented the file from being copied.
    */
   public boolean copyFile(String identifier, String pathFrom, IdentityID userFrom,
                           int typeFrom, String pathTo, IdentityID userTo,
                           int typeTo, boolean move,
                           Map<String, List<String>> infos, Principal principal)
      throws Exception
   {
      if(infos == null) {
         infos = new HashMap<>();
         infos.put("warning", new ArrayList<>());
         infos.put("error", new ArrayList<>());
         infos.put("info", new ArrayList<>());
      }

      if((typeFrom & RepositoryEntry.FOLDER) != 0) {
         return copyFolder(pathFrom, userFrom, typeFrom, pathTo,
            userTo, typeTo, move, infos, principal);
      }
      else if((typeFrom & RepositoryEntry.VIEWSHEET) != 0) {
         return copySheet(identifier, pathFrom, userFrom, pathTo, userTo, infos,
                          false, move, principal);
      }
      else if((typeFrom & RepositoryEntry.WORKSHEET) != 0) {
         return copySheet(identifier, pathFrom, userFrom, pathTo, userTo, infos,
                          true, move, principal);
      }

      return true;
   }

   /**
    * Removes a file.
    *
    * @param path      the path to the file.
    * @param user      the user that owns the file.
    * @param type      the type of file.
    * @param infos     a map of messages.
    * @param principal the principal identifying the current user.
    */
   public void removeFile(String path, IdentityID user, int type,
                          Map<String, List<String>> infos, Principal principal)
      throws Exception
   {
      List<String> warnings = infos == null ? new ArrayList<>() :
         infos.get("warning");
      boolean isWS = false;
      Catalog catalog = Catalog.getCatalog();
      AssetEntry.Type newType = AssetEntry.Type.UNKNOWN;

      if((type & RepositoryEntry.WORKSHEET) != 0) {
         newType = AssetEntry.Type.WORKSHEET;
         isWS = true;
      }
      else if((type & RepositoryEntry.VIEWSHEET) != 0) {
         newType = type != RepositoryEntry.VIEWSHEET ?
            AssetEntry.Type.VIEWSHEET_SNAPSHOT : AssetEntry.Type.VIEWSHEET;
      }

      if(newType == AssetEntry.Type.VIEWSHEET ||
         newType == AssetEntry.Type.VIEWSHEET_SNAPSHOT ||
         (newType == AssetEntry.Type.WORKSHEET && isWS))
      {
         int iindex = path.lastIndexOf("&identifier=");
         path = iindex < 0 ? path : path.substring(0, iindex);
      }

      //AssetEntry.Type.WORKSHEET value is same with RepositoryEntry.REPLET, so need
      // isWS.
      if(newType == AssetEntry.Type.VIEWSHEET ||
         newType == AssetEntry.Type.VIEWSHEET_SNAPSHOT ||
         newType == AssetEntry.Type.WORKSHEET)
      {
         int scope = user != null ? AssetRepository.USER_SCOPE :
            AssetRepository.GLOBAL_SCOPE;
         path = splitMyReportPath(path);

         if(newType == AssetEntry.Type.WORKSHEET) {
            path = splitWorksheetPath(path, false);
         }

         AssetEntry viewsheet = new AssetEntry(scope, newType, path, user);
         AssetRepository repository = AssetUtil.getAssetRepository(false);

         try {
            setPermissionIgnored();
            repository.removeSheet(viewsheet, principal, true);
         }
         catch(Exception ex) {
            LOG.error("Failed to remove viewsheet " + viewsheet +
               " for user " + principal, ex);
         }
         finally {
            removePermissionIgnored();
         }
      }
      //only handle ws folder entry, not handle folder entry.
      else if((type & RepositoryEntry.WORKSHEET_FOLDER) ==
         RepositoryEntry.WORKSHEET_FOLDER)
      {
         path = splitWorksheetPath(path, user != null);

         if(user != null) {
            path = splitMyReportPath(path);
         }

         try {
            setPermissionIgnored();
            removeWorksheetFolder(path, user, false, principal);
         }
         finally {
            removePermissionIgnored();
         }
      }
      else if((type & RepositoryEntry.FOLDER) != 0) {
         RepletRegistry registry = RepletRegistry.getRegistry(user);
         String[] repletFolders = registry.getFolders(path);

         try {
            setPermissionIgnored();
         }
         finally {
            removePermissionIgnored();
         }

         //remove viewsheets
         AssetEntry[] assets = getViewsheetEntries(path, user, principal);

         try {
            setPermissionIgnored();

            for(AssetEntry asset : assets) {
               String vsPath = asset.getPath() +
                  "&identifier=" + asset.toIdentifier();
               AssetEntry.Type vstype = asset.isVSSnapshot() ?
                  AssetEntry.Type.VIEWSHEET_SNAPSHOT : AssetEntry.Type.VIEWSHEET;
               removeFile(vsPath, user, vstype.id(), infos, principal);
            }
         }
         finally {
            removePermissionIgnored();
         }

         //remove worksheets
         AssetEntry[] wsAssets = getWorksheetEntries(path, user, principal);

         try {
            setPermissionIgnored();

            for(AssetEntry wsAsset : wsAssets) {
               String wsPath = wsAsset.getPath() +
                  "&identifier=" + wsAsset.toIdentifier();
               removeFile(wsPath, user, AssetEntry.Type.WORKSHEET.id(), infos, principal);
            }
         }
         finally {
            removePermissionIgnored();
         }

         removePermissionIgnored();

         //remove folders
         for(String repletFolder : repletFolders) {
            if(!registry.removeFolder(repletFolder)) {
               warnings.add(catalog.getString(
                  "em.registry.deleteFolderError", repletFolder));
            }
         }

         //remove self
         if(!registry.removeFolder(path)) {
            warnings.add(catalog.getString(
               "em.registry.deleteFolderError", path));
         }

         registry.save();
      }
   }

   /**
    * Removes a worksheet folder.
    *
    * @param folderName the name of the folder.
    * @param user       the user that owns the folder.
    * @param force      <tt>true</tt> to force removal.
    * @param principal  the principal identifying the current user.
    *
    * @throws Exception if the folder could not be removed.
    */
   public void removeWorksheetFolder(String folderName, IdentityID user,
                                     boolean force, Principal principal)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      int scope = user == null ? AssetRepository.GLOBAL_SCOPE :
         AssetRepository.USER_SCOPE;
      AssetEntry folderEntry = new AssetEntry(scope, AssetEntry.Type.FOLDER,
                                              folderName, user);

      if(repository.containsEntry(folderEntry)) {
         repository.removeFolder(folderEntry, principal, force);
      }
   }

   /**
    * Copies a folder.
    *
    * @param pathFrom the path to the original file.
    * @param userFrom the user that owns the original file.
    * @param typeFrom the type of the original file.
    * @param pathTo   the path to the target file.
    * @param userTo   the owner of the target file.
    * @param typeTo   the type of the target file.
    * @param move     <tt>true</tt> to move the file; <tt>false</tt> to copy only.
    * @param infos     a map of messages.
    * @param principal the principal identifying the current user.
    *
    * @return <tt>true</tt> if the file was copied; <tt>false</tt> otherwise.
    *
    * @throws Exception if an error prevented the file from being copied.
    */
   private boolean copyFolder(String pathFrom, IdentityID userFrom,
                              int typeFrom, String pathTo, IdentityID userTo,
                              int typeTo, boolean move,
                              Map<String, List<String>> infos,
                              Principal principal) throws Exception
   {
      List<String> messages = infos.get("info");
      int pindex = pathFrom.lastIndexOf("/");
      boolean isWSFolder = (typeFrom & RepositoryEntry.WORKSHEET_FOLDER) ==
         RepositoryEntry.WORKSHEET_FOLDER;
      String entryName = pindex < 0 ? pathFrom : pathFrom.substring(pindex + 1);
      Catalog catalog = Catalog.getCatalog();

      String oto = pathTo;
      RepletRegistry registryTo = RepletRegistry.getRegistry(userTo);
      pathTo = "".equals(pathTo) || "/".equals(pathTo) ? "" : pathTo + "/";
      pathTo += entryName;
      String identifier = null;

      if(isWSFolder) {
         pathFrom = splitWorksheetPath(pathFrom, userFrom != null);
         pathTo = splitWorksheetPath(pathTo, userTo != null);
         pathFrom = userFrom != null ? splitMyReportPath(pathFrom): pathFrom;
         pathTo = userTo != null ? splitMyReportPath(pathTo): pathTo;

         //by nickgovus, 2023.11.08, Bug #62904, pass metaData to moved folder by getting from repository
         AssetRepository repository = AssetUtil.getAssetRepository(false);
         AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER,
                                              pathFrom, userFrom);
         oldEntry = repository.getAssetEntry(oldEntry);

         if(!addWorksheetFolder(pathTo, oldEntry, userTo, principal)) {
            infos.get("error").add(catalog.getString("Duplicate Name"));
            return false;
         }

         try {
            setPermissionIgnored();
            AssetEntry[] assets = getWorksheetEntries(pathFrom, userFrom, principal);

            for(AssetEntry asset : assets) {
               if(asset.isFolder()) {
                  pathFrom = asset.getPath();
                  typeFrom = RepositoryEntry.WORKSHEET_FOLDER;
               }
               else {
                  identifier = asset.toIdentifier();
                  pathFrom =
                     (userFrom != null ? Tool.MY_DASHBOARD : RepositoryEntry.REPOSITORY_FOLDER) +
                     "/" + asset.getPath();
                  typeFrom = RepositoryEntry.WORKSHEET;
               }

               try {
                  setPermissionIgnored();
                  copyFile(identifier, pathFrom, userFrom, typeFrom, pathTo,
                           userTo, typeTo, move, infos, principal);
               }
               finally {
                  removePermissionIgnored();
               }
            }

            return true;
         }
         finally {
            removePermissionIgnored();
         }
      }

      RepletRegistry registryFrom = RepletRegistry.getRegistry(userFrom);
      // the folder already exist? use it directly
      checkPermission(pathTo, ResourceType.REPORT, ResourceAction.DELETE, principal);
      registryTo.addFolder(pathTo);

      SecurityEngine security = SecurityEngine.getSecurity();
      if(security != null) {
         Permission perm = security.getPermission(ResourceType.REPORT, pathFrom);
         if(perm != null) {
            security.setPermission(ResourceType.REPORT, pathTo, perm);
         }
      }

      registryTo.setFolderAlias(
         pathTo, registryFrom.getFolderAlias(pathFrom));
      registryTo.setFolderDescription(
         pathTo, registryFrom.getFolderDescription(pathFrom));
      registryTo.save();

      // registry folders to asset engine
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      getViewsheetEntries(pathTo, userTo, principal);
      repository.syncFolders(userTo != null ? new SRPrincipal(userTo) : null);

      String[] repletFolders = registryFrom.getFolders(pathFrom);

      try {
         setPermissionIgnored();
      }
      finally {
         removePermissionIgnored();
      }

      try {
         //copy viewsheets
         setPermissionIgnored();
         AssetEntry[] assets =
            getViewsheetEntries(pathFrom, userFrom, principal);

         for(AssetEntry asset : assets) {
            String viewsheetPath = (userFrom != null ? Tool.MY_DASHBOARD + "/" :
               RepositoryEntry.REPOSITORY_FOLDER + "/") + asset.getPath();

            try {
               setPermissionIgnored();
               copySheet(asset, viewsheetPath, userFrom, pathTo, userTo, infos, false,
                  move, principal);
            }
            finally {
               removePermissionIgnored();
            }
         }
      }
      finally {
         removePermissionIgnored();
      }

      try {
         //copy folders
         for(String repletFolder : repletFolders) {
            copyFile(null, repletFolder, userFrom, typeFrom,
                     pathTo, userTo, typeTo, move, infos, principal);
         }
      }
      finally {
         removePermissionIgnored();
      }

      if(move) {
         registryFrom.removeFolder(pathFrom);
      }

      return true;
   }

   /**
    * Copies a worksheet or viewsheet.
    *
    * @param oentry      the entry of the asset being copied.
    * @param pathFrom    the path to the original file.
    * @param userFrom    the user that owns the original file.
    * @param pathTo      the path to the target file.
    * @param userTo      the owner of the target file.
    * @param move        <tt>true</tt> to move the file; <tt>false</tt> to copy
    *                    only.
    * @param infos       a map of messages.
    * @param isWorksheet <tt>true</tt> if a worksheet; <tt>false</tt> if a
    *                    viewsheet.
    * @param principal   the principal identifying the current user.
    *
    * @return <tt>true</tt> if the file was copied; <tt>false</tt> otherwise.
    *
    * @throws Exception if an error prevented the file from being copied.
    */
   private boolean copySheet(AssetEntry oentry, String pathFrom,
                             IdentityID userFrom, String pathTo, IdentityID userTo,
                             Map<String, List<String>> infos,
                             boolean isWorksheet, boolean move,
                             Principal principal)
      throws Exception
   {
      List<String> messages = infos.get("info");
      Catalog catalog = Catalog.getCatalog();
      int pindex = pathFrom.lastIndexOf("/");
      String entryName = pindex < 0 ? pathFrom : pathFrom.substring(pindex + 1);

      if(oentry == null) {
         return true;
      }

      AssetRepository repository = AssetUtil.getAssetRepository(false);
      AbstractSheet vs = repository.getSheet(oentry, principal, false,
                                             AssetContent.ALL);
      String npath = "".equals(pathTo) || "/".equals(pathTo) ?
         "" : (pathTo + "/");
      int scope = userTo == null ? AssetRepository.GLOBAL_SCOPE :
         AssetRepository.USER_SCOPE;
      npath = splitMyReportPath(npath);

      if(isWorksheet && npath.contains(Tool.WORKSHEET)) {
         npath = splitWorksheetPath(npath, false);
      }

      if(isWorksheet && isDuplicateWorksheetName(
         npath + entryName, userTo, false, principal))
      {
         infos.get("error").add(catalog.getString("Duplicate Name"));
         return false;
      }

      AssetEntry nentry = new AssetEntry(scope, oentry.getType(),
                                         npath + entryName, userTo);

      if(((userFrom == null && userTo != null) ||
         (userFrom != null && !userFrom.equals(userTo))) &&
         (vs instanceof Viewsheet))
      {
         AssetEntry basedEntry = ((Viewsheet) vs).getBaseEntry();

         if(basedEntry != null && basedEntry.isWorksheet() &&
            basedEntry.getScope() == AssetRepository.USER_SCOPE)
         {
            messages.add(catalog.getString(
               "em.viewsheetCopy.basedUserWorksheet", entryName));
         }
      }

      int index = 1;
      String nname = entryName;

      while(SUtil.isDuplicatedEntry(repository, nentry)) {
         nname = entryName + index;
         nentry = new AssetEntry(scope, oentry.getType(),
                                 npath + nname, userTo);
         index++;
      }

      if(!nname.equals(entryName)) {
         messages.add(catalog.getString(
            oentry.isWorksheet() ? "em.ws.exist" : "em.vs.exist" , entryName,
            "".equals(pathTo) ? "/" : pathTo, nname));
      }

      int times = 0;
      ResourceType resourceType;

      if(scope == AssetRepository.USER_SCOPE) {
         resourceType = ResourceType.MY_DASHBOARDS;
      }
      else {
         resourceType = isWorksheet ? ResourceType.ASSET : ResourceType.REPORT;
      }

      checkPermission(npath, resourceType, ResourceAction.WRITE, principal);

      try {
         setPermissionIgnored();
         long time1 = 0;
         long time2 = 0;

         // user scope need 5s to refresh, so here try to prevent
         // this exceptions
         while(times <= 11) {
            try {
               Date start = new Date();
               getViewsheetEntries(pathTo, userTo, false, principal);
               //System.out.println("getentry :" + (new Date().getTime() - start.getTime()));
               start = new Date();

               if(oentry.getAlias() != null) {
                  nentry.setAlias(oentry.getAlias());
               }

               nentry.copyMetaData(oentry);

               //Record the old path for mv viewsheet change folder.
               if(!isWorksheet && move) {
                  AbstractAssetEngine.LOCAL.set(oentry.toIdentifier());
               }

               // fix bug1373944350215, use changeSheet for vs just like ws,
               // then bookmarks will not be lost.
               if(move) {
                  repository.changeSheet(oentry, nentry, principal, true);
                  //System.out.println("changeSheet :" + (new Date().getTime() - start.getTime()));

               }
               else {
                  ((AbstractAssetEngine) repository).setSheet(nentry, vs,
                     principal, true, false);
               }
            }
            catch(Exception ex) {
               times++;

               if(times > 10) {
                  throw ex;
               }

               LOG.debug("Waiting repository refresh: " + times);

               try {
                  Thread.sleep(500);
               }
               catch(Exception ignore) {
               }

               continue;
            }

            break;
         }
      }
      finally {
         removePermissionIgnored();
      }

      return true;
   }

   /**
    * Copies a worksheet or viewsheet.
    *
    * @param identifier  the identifier of the asset being copied.
    * @param pathFrom    the path to the original file.
    * @param userFrom    the user that owns the original file.
    * @param pathTo      the path to the target file.
    * @param userTo      the owner of the target file.
    * @param move        <tt>true</tt> to move the file; <tt>false</tt> to copy
    *                    only.
    * @param infos       a map of messages.
    * @param isWorksheet <tt>true</tt> if a worksheet; <tt>false</tt> if a
    *                    viewsheet.
    * @param principal   the principal identifying the current user.
    *
    * @return <tt>true</tt> if the file was copied; <tt>false</tt> otherwise.
    *
    * @throws Exception if an error prevented the file from being copied.
    */
   private boolean copySheet(String identifier, String pathFrom,
                             IdentityID userFrom, String pathTo, IdentityID userTo,
                             Map<String, List<String>> infos,
                             boolean isWorksheet, boolean move,
                             Principal principal)
      throws Exception
   {
      AssetEntry oentry = getAssetEntry(identifier, principal);

      return copySheet(oentry, pathFrom, userFrom, pathTo, userTo, infos, isWorksheet, move,
         principal);
   }

   /**
    * Adds a repository folder.
    *
    * @param folderName  the name of the folder to add.
    * @param alias       the alias for the folder.
    * @param description a description of the folder.
    * @param user        the user in whose repository to add the folder.
    *
    * @throws Exception if the folder could not be added.
    */
   public void addRepositoryFolder(String folderName, String alias,
                                   String description, IdentityID user)
      throws Exception
   {
      RepletRegistry registry = RepletRegistry.getRegistry(user);

      if(!registry.addFolder(folderName)) {
         throw new Exception("Failed to add folder " + folderName);
      }

      if(alias != null) {
         registry.setFolderAlias(folderName, alias);
      }

      if(description != null) {
         registry.setFolderDescription(folderName, description);
      }

      registry.save();
   }

   /**
    * Adds a worksheet folder to the repository.
    *
    * @param folderName the name of the folder.
    * @param oldEntry   the AssetEntry of the original folder, to pass metadata
    * @param user       the name of the user creating the folder.
    * @param principal  the principal identifying the current user.
    *
    * @return <tt>true</tt> if the folder was created; <tt>false</tt> otherwise.
    *
    * @throws Exception if an error prevented the folder from being created.
    */
   public boolean addWorksheetFolder(String folderName, AssetEntry oldEntry,
                                     IdentityID user, Principal principal)
      throws Exception
   {
      if(isDuplicateWorksheetName(folderName, user, true, principal)) {
         return false;
      }

      AssetRepository repository = AssetUtil.getAssetRepository(false);
      int scope = user == null ? AssetRepository.GLOBAL_SCOPE :
         AssetRepository.USER_SCOPE;
      AssetEntry folderEntry = new AssetEntry(scope, AssetEntry.Type.FOLDER,
                                              folderName, user);

      if(oldEntry != null) {
         folderEntry.copyMetaData(oldEntry);
      }

      if(!repository.containsEntry(folderEntry)) {
         repository.addFolder(folderEntry, principal);
      }

      return true;
   }

   /**
    * Determines if the worksheet name is already used.
    *
    * @param path the path to the worksheet.
    * @param user the name of the user attempting to create the worksheet.
    * @param principal the principal identifying the current user.
    *
    * @return <tt>true</tt> if a duplicate name; <tt>false/tt> otherwise.
    *
    * @throws Exception if an error prevented a duplicate name from being
    *                   detected.
    */
   public boolean isDuplicateWorksheetName(String path, IdentityID user,
                                           boolean isFolder,
                                           Principal principal)
      throws Exception
   {
      int index = path.lastIndexOf("/");
      String parent = index == -1 ? "/" : path.substring(0, index);
      AssetEntry[] entries = isFolder ? getWorksheetFiles(parent, user, principal) :
         getWorksheetFolders(parent, user, principal);

      for(AssetEntry entry : entries) {
         if(path.equals(entry.getPath())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Gets the worksheet folders in the specified parent folder.
    *
    * @param parent    the path to the parent folder.
    * @param user      the name of the user requesting the folders.
    * @param principal the principal identifying the current user.
    *
    * @return the entries that represent the child folders.
    *
    * @throws Exception if the folders could not be obtained.
    */
   public AssetEntry[] getWorksheetFolders(String parent, IdentityID user,
                                           Principal principal)
      throws Exception
   {
      return getEntries(parent, user, AssetEntry.Type.FOLDER, true,
         new AssetEntry.Selector(AssetEntry.Type.FOLDER,
            AssetEntry.Type.REPORT_WORKSHEET_FOLDER), principal);
   }

   /**
    * Gets the worksheets in the specified parent folder.
    *
    * @param parent    the path to the parent folder.
    * @param user      the name of the user requesting the worksheets.
    * @param principal the principal identifying the current user.
    *
    * @return the entries that represent the child worksheets.
    *
    * @throws Exception if the worksheets could not be obtained.
    */
   public AssetEntry[] getWorksheetFiles(String parent, IdentityID user,
                                         Principal principal)
      throws Exception
   {
      return getEntries(parent, user, AssetEntry.Type.FOLDER, true,
         new AssetEntry.Selector(AssetEntry.Type.WORKSHEET), principal);
   }

   /**
    * Gets all viewsheets and viewsheet folders in a folder.
    *
    * @param parent    the path to the parent folder.
    * @param user      the name of the user requesting the viewsheets.
    * @param loadChildrenProperties whether load the children sheets properties during load folder.
    * @param principal the principal identifying the current user.
    *
    * @return the entries representing the viewsheets and folders.
    *
    * @throws Exception if the viewsheets could not be obtained.
    */
   public AssetEntry[] getViewsheetEntries(String parent, IdentityID user,
                                           boolean loadChildrenProperties,
                                           Principal principal)
      throws Exception
   {
      return getEntries(
         parent, user, AssetEntry.Type.REPOSITORY_FOLDER, loadChildrenProperties,
         new AssetEntry.Selector(AssetEntry.Type.VIEWSHEET_SNAPSHOT), principal);
   }

   /**
    * Gets all viewsheets and viewsheet folders in a folder.
    *
    * @param parent    the path to the parent folder.
    * @param user      the name of the user requesting the viewsheets.
    * @param principal the principal identifying the current user.
    *
    * @return the entries representing the viewsheets and folders.
    *
    * @throws Exception if the viewsheets could not be obtained.
    */
   public AssetEntry[] getViewsheetEntries(String parent, IdentityID user,
                                           Principal principal) throws Exception
   {
      return getEntries(
         parent, user, AssetEntry.Type.REPOSITORY_FOLDER, true,
         new AssetEntry.Selector(AssetEntry.Type.VIEWSHEET_SNAPSHOT), principal);
   }

   /**
    * Gets all worksheets and worksheet folders in a folder.
    *
    * @param parent    the path to the parent folder.
    * @param user      the name of the user requesting the worksheets.
    * @param principal the principal identifying the current user.
    *
    * @return the entries representing the worksheets and folders.
    *
    * @throws Exception if the worksheets could not be obtained.
    */
   public AssetEntry[] getWorksheetEntries(String parent, IdentityID user,
                                           Principal principal) throws Exception
   {
      return getEntries(
         parent, user, AssetEntry.Type.FOLDER, true,
         new AssetEntry.Selector(AssetEntry.Type.WORKSHEET,
            AssetEntry.Type.REPORT_WORKSHEET_FOLDER), principal);
   }

   /**
    * Gets the assets in the specified parent folder.
    *
    * @param parent    the path to the parent folder.
    * @param user      the name of the user requesting the assets.
    * @param entryType the type the parent folder.
    * @param selector  the type of assets to return.
    * @param principal the principal identifying the current user.
    *
    * @return the entries representing the matching assets.
    *
    * @throws Exception if the assets could not be obtained.
    */
   public AssetEntry[] getEntries(String parent, IdentityID user, AssetEntry.Type entryType,
                                  boolean loadChildrenProperties,
                                  AssetEntry.Selector selector, Principal principal)
      throws Exception
   {
      AssetRepository assetRepository = AssetUtil.getAssetRepository(false);

      if(assetRepository != null) {
         int scope = user != null ? AssetRepository.USER_SCOPE : AssetRepository.GLOBAL_SCOPE;
         String ppath;

         if(user != null && Tool.isMyReport(parent)) {
            if(parent.equals(Tool.MY_DASHBOARD)) {
               ppath = "/";
            }
            else {
               ppath = splitMyReportPath(parent);
            }
         }
         else {
            ppath = parent;
         }

         AssetEntry pentry = new AssetEntry(scope, entryType, ppath, user);

         if(!loadChildrenProperties) {
            pentry.setProperty("__oneoff__", true + "");
         }

         return assetRepository.getEntries(pentry, principal, ResourceAction.READ, selector);
      }

      return new AssetEntry[0];
   }

   /**
    * Gets the entry for the specified asset.
    *
    * @param identifier the asset identifier.
    * @param user       the name of the user requesting the asset.
    *
    * @return the asset entry.
    *
    * @throws Exception if the asset entry could not be obtained.
    */
   public AssetEntry getAssetEntry(String identifier, Principal user)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      AssetEntry entry = AssetEntry.createAssetEntry(identifier);

      if(entry == null) {
         return null;
      }

      AssetEntry parent = entry.getParent();
      entry = null;
      AssetEntry.Selector selector = new AssetEntry.Selector(
         AssetEntry.Type.FOLDER, AssetEntry.Type.WORKSHEET,
         AssetEntry.Type.VIEWSHEET, AssetEntry.Type.DATA,
         AssetEntry.Type.PHYSICAL, AssetEntry.Type.VIEWSHEET_SNAPSHOT);

      for(AssetEntry e : repository.getEntries(parent, user, ResourceAction.ADMIN, selector)) {
         if(identifier.equals(e.toIdentifier())) {
            entry = e;
            break;
         }
      }

      return entry;
   }

   /**
    * Gets the path, relative to the My Reports folder.
    *
    * @param parent the full path.
    *
    * @return the relative path.
    */
   public static String splitMyReportPath(String parent) {
      if(!Tool.isMyReport(parent)) {
         return parent;
      }

      return parent.startsWith(Tool.MY_DASHBOARD + "/") ?
         parent.substring(Tool.MY_DASHBOARD.length() + 1) :
         parent.substring(Tool.MY_DASHBOARD.length());
   }

   /**
    * Gets the path relative to the worksheet folder.
    *
    * @param parent the full path.
    * @param isMyReport <tt>true</tt> if the folder is in My Reports;
    *                   <tt>false</tt> otherwise.
    *
    * @return the relative path.
    */
   public String splitWorksheetPath(String parent, boolean isMyReport) {
      String search = isMyReport ? "/" + Tool.WORKSHEET : Tool.WORKSHEET;
      int index = parent.indexOf(search);

      if(index == -1) {
         return parent;
      }

      String path = parent;

      // split "worksheet"
      // this logic is very dangerous. if the path contains "Worksheet" as part of the
      // name, then the path is corrupted. it seems this logic assumes the path is in the
      // form: user-name/Worksheets for my-report, and Worksheets/name for global.
      // add the check for index == 0 so it only apply in the above situation.
      if(!Tool.WORKSHEET.equals(parent) && (isMyReport || index == 0)) {
         path = parent.substring(0, index);
         //"Worksheet" path end index, ws is String "Worksheet" length, myReport
         //is String "/Worksheet" length.
         int endIndex = index + (isMyReport ? 10 : 9);

         //eg: parent is "Worksheet/Sales"
         if(parent.length() > endIndex + 1) {
            //myReport need the "/".
            index = isMyReport ? index + search.length() :
               index + search.length() + 1;
            path = path + parent.substring(index);
         }
      }

      return path;
   }

   /**
    * Gets the name component from a full path.
    *
    * @param path the full path.
    *
    * @return the name.
    */
   public String getName(String path) {
      if(path == null) {
         return null;
      }

      if("/".equals(path)) {
         return "";
      }

      int index = path.lastIndexOf("/");

      return index == -1 ? path : path.substring(index + 1);
   }

   /**
    * Determines if the specified name is being used by a repository folder,
    * replet, or viewsheet.
    *
    * @param nname the name to check.
    * @param oname the original name.
    * @param user the user in whose repository to search for a matching name.
    *
    * @return <tt>true</tt> if duplicate; <tt>false</tt> otherwise.
    *
    * @throws Exception if an error prevented a duplicate from being detected.
    */
   public boolean isDuplicatedName(String nname, String oname, IdentityID user) throws Exception {
      return isDuplicatedName(RepletRegistry.getRegistry(user), nname, oname, user);
   }

   /**
    * Determines if the specified name is already in use.
    *
    * @param registry the registry to check.
    * @param nname    the name to test.
    * @param oname the original name.
    * @param user     the name of the user creating an asset.
    *
    * @return <tt>true</tt> if the name is already in use; <tt>false</tt>
    *         otherwise.
    */
   private boolean isDuplicatedName(RepletRegistry registry, String nname,
                                    String oname, IdentityID user)
   {
      String lowerName = nname.toLowerCase();

      for(String folder : registry.getAllFolders()) {
         if(!Tool.isEmptyString(oname) && folder.equalsIgnoreCase(oname)) {
            continue;
         }

         if(lowerName.equals(folder.toLowerCase())) {
            return true;
         }
      }

      return isDuplicateViewsheet(nname, user);
   }

   /**
    * Determines if a viewsheet with the specified name already exists.
    *
    * @param name the name of the viewsheet.
    * @param user the name of the user creating an asset.
    *
    * @return <tt>true</tt> if the name is already in use; <tt>false</tt>
    *         otherwise.
    */
   boolean isDuplicateViewsheet(String name, IdentityID user) {
      boolean dupVS;

      try {
         dupVS = SUtil.isDuplicatedViewsheet(
            AssetUtil.getAssetRepository(false), name, user);
      }
      catch(Exception ignore) {
         dupVS = false;
      }

      return dupVS;
   }

   /**
    * Determines if a user has permission to access a resource.
    *
    * @param resource    the path to the resource.
    * @param prefix      the prefix for the resource.
    * @param access      the type of access to check.
    * @param principal   the principal identifying the current user.
    *
    * @return <tt>true</tt> if the user has the specified permission;
    *         <tt>false</tt> otherwise.
    */
   public boolean hasPermission(String resource, ResourceType prefix, ResourceAction access,
                                Principal principal)
   {
      return hasPermission(resource, prefix, access, true, principal);
   }

   /**
    * Determines if a user has permission to access a resource.
    *
    * @param resource    the path to the resource.
    * @param prefix      the prefix for the resource.
    * @param access      the type of access to check.
    * @param principal   the principal identifying the current user.
    *
    * @return <tt>true</tt> if the user has the specified permission;
    *         <tt>false</tt> otherwise.
    */
   public boolean hasPermission(String resource, ResourceType prefix, ResourceAction access,
                                boolean checkDelete, Principal principal)
   {
      return checkPermission(
         resource, resource, prefix, access, checkDelete, principal, false);
   }

   /**
    * Determines if a user has permission to access a resource.
    *
    * @param resource    the path to the resource.
    * @param prefix      the prefix for the resource.
    * @param access      the type of access to check.
    * @param principal   the principal identifying the current user.
    *
    * @return <tt>true</tt> if the user has the specified permission;
    *         <tt>false</tt> otherwise.
    */
   public boolean checkPermission(String resource, ResourceType prefix, ResourceAction access,
                                  Principal principal)
   {
      return checkPermission(resource, prefix, access, true, principal);
   }

   /**
    * Determines if a user has permission to access a resource.
    *
    * @param resource    the path to the resource.
    * @param prefix      the prefix for the resource.
    * @param access      the type of access to check.
    * @param checkDelete check whether to delete permissions after checking write permissions
    * @param principal   the principal identifying the current user.
    *
    * @return <tt>true</tt> if the user has the specified permission;
    *         <tt>false</tt> otherwise.
    */
   public boolean checkPermission(String resource, ResourceType prefix, ResourceAction access,
                                  boolean checkDelete, Principal principal)
   {
      if(prefix == ResourceType.ASSET && "".equals(resource)) {
         return checkPermission(
            resource, "Global Worksheet", prefix, access, checkDelete, principal);
      }

      return checkPermission(resource, resource, prefix, access, checkDelete, principal);
   }

   /**
    * Determines if a user has permission to access a resource.
    *
    * @param resource    the path to the resource.
    * @param displayName the display name of the resource.
    * @param prefix      the prefix for the resource.
    * @param access      the type of access to check.
    * @param checkDelete check whether to delete permissions after checking write permissions
    * @param principal   the principal identifying the current user.
    *
    * @return <tt>true</tt> if the user has the specified permission;
    *         <tt>false</tt> otherwise.
    */
   public boolean checkPermission(String resource, String displayName,
                                  ResourceType prefix, ResourceAction access,
                                  boolean checkDelete, Principal principal)
   {
      return checkPermission(
         resource, displayName, prefix, access, checkDelete, principal, true);
   }

   public boolean checkPermission(String resource, String displayName,
                                  ResourceType prefix, EnumSet<ResourceAction> access,
                                  boolean checkDelete, Principal principal)
   {
      for(ResourceAction action : access) {
         if(!checkPermission(resource, displayName, prefix, action, checkDelete, principal)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Determines if a user has permission to access a resource.
    *
    * @param resource       the path to the resource.
    * @param displayName    the display name of the resource.
    * @param prefix         the prefix for the resource.
    * @param access         the type of access to check.
    * @param checkDelete check whether to delete permissions after checking write permissions
    * @param principal      the principal identifying the current user.
    * @param throwException <tt>true</tt> to throw an unchecked exception if the
    *                       user does not have the specified permission;
    *                       <tt>false</tt> otherwise.
    *
    * @return <tt>true</tt> if the user has the specified permission;
    *         <tt>false</tt> otherwise.
    */
   private boolean checkPermission(String resource, String displayName,
                                   ResourceType prefix, ResourceAction access,
                                   boolean checkDelete, Principal principal,
                                   boolean throwException)
   {
      if(isLocalPermissionIgnored()) {
         return true;
      }

//      boolean isAdmin = SUtil.isAdmin(principal);
//
//      if(isAdmin) {
//         return true;
//      }

      AssetRepository assetRepository = AssetUtil.getAssetRepository(false);

      if(assetRepository == null) {
         return true;
      }

      if(!assetRepository.checkPermission(principal, prefix, resource, EnumSet.of(access)))
      {
         if(throwException) {
            String errMsg = Catalog.getCatalog().getString(
               "common.writeAuthority", displayName);

            if(checkDelete && !assetRepository.checkPermission(principal, prefix,
                                                resource, EnumSet.of(ResourceAction.DELETE)))
            {
               errMsg = Catalog.getCatalog().getString(
                  "common.deleteAuthority", displayName);
            }

            MessageException ex = new MessageException(errMsg);
            throw ex;
         }

         return false;
      }

      return true;
   }

   /**
    * Sets a thread local flag that indicates that permissions should be
    * ignored.
    */
   void setPermissionIgnored() {
      AssetRepository.IGNORE_PERM.set(true);
   }

   /**
    * Clears the thread local flag that indicates if permissions should be
    * ignored.
    */
   void removePermissionIgnored() {
      AssetRepository.IGNORE_PERM.remove();
   }

   /**
    * Gets the thread local flag that indicates that permissions should be
    * ignored.
    *
    * @return <tt>true</tt> if ignored; <tt>false</tt> otherwise.
    */
   private boolean isLocalPermissionIgnored() {
      return AssetRepository.IGNORE_PERM.get();
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(RepletRegistryManager.class);

   /**
    * Gets the repository entries in the specified folder.
    *
    * @param parent    the parent folder.
    * @param filter    the type of entries to return.
    * @param user      the name of the user whose repository will be searched.
    * @param type      the type of parent folder.
    * @param principal the principal identifying the current user.
    */
   public RepositoryEntry[] getRepositoryEntries(String parent, int filter, IdentityID user, int type,
                                                 Principal principal) throws Exception
   {
      if(parent == null || parent.length() == 0) {
         return null;
      }

      RepletRegistry registry = null;

      if(((filter & RepositoryEntry.LIVE_REPORTS) == RepositoryEntry.LIVE_REPORTS)) {
         registry = RepletRegistry.getRegistry(user);
      }

      return getRepositoryEntries(parent, filter, user, type, registry, principal);
   }

   RepositoryEntry[] getRepositoryEntries(String parent, int filter, IdentityID user, int type,
                                          RepletRegistry registry, Principal principal)
      throws Exception
   {
      boolean ismy = user != null;
      boolean isSelectedWS = (filter & RepositoryEntry.WORKSHEETS) == RepositoryEntry.WORKSHEETS;
      boolean isWSFolder = (type & RepositoryEntry.WORKSHEET_FOLDER) == RepositoryEntry.WORKSHEET_FOLDER;
      List<RepositoryEntry> vec = new ArrayList<>();
      List<String> regFolders = Arrays.asList(registry.getFolders(parent, true));
      List<String> folders = new ArrayList<>(regFolders);

      folders.sort(Comparator.naturalOrder());

      //add worksheet folder in registry and myReport node.
      // @by ChrisSpagnoli bug1422852419881 2015-2-18
      if(Tool.MY_DASHBOARD.equals(parent) && isSelectedWS) {
         String wsPath = "/".equals(parent) ? Tool.WORKSHEET :
            parent + "/" + Tool.WORKSHEET;
         DefaultFolderEntry folderEntry = new DefaultFolderEntry(
            getPath(wsPath, user), RepositoryEntry.WORKSHEET_FOLDER | RepositoryEntry.USER, user);
         vec.add(folderEntry);
      }

      if(!isWSFolder) {
         for(String folder : folders) {
            if(hasPermission(folder, ResourceType.REPORT, ResourceAction.READ, principal)) {
               RepletFolderEntry folderEntry = new RepletFolderEntry(getPath(folder, user), user);
               folderEntry.setAlias(registry.getFolderAlias(folder));
               vec.add(folderEntry);
            }
         }
      }

      //add worksheets
      if(parent.contains(Tool.WORKSHEET) && isWSFolder && isSelectedWS) {
         String path = splitWorksheetPath(parent, ismy);

         if("".equals(path)) {
            path = "/";
         }

         AssetEntry[] assets = getWorksheetEntries(path, user, principal);

         for(AssetEntry asset : assets) {
            String wsPath = ismy ? Tool.MY_DASHBOARD + "/" + Tool.WORKSHEET + "/" :
               RepositoryEntry.REPOSITORY_FOLDER + "/" + Tool.WORKSHEET + "/";

            if(asset.isFolder()) {
               RepletFolderEntry folderEntry = new RepletFolderEntry(
                  wsPath + asset.getPath(), RepositoryEntry.WORKSHEET_FOLDER,
                  asset.getUser());
               vec.add(folderEntry);
               continue;
            }

            WorksheetEntry wentry = new WorksheetEntry(wsPath +
               asset.getPath(), asset.getUser());
            String description = asset.getProperty("Tooltip");
            description = description != null && !"".equals(description) ?
               description : asset.getDescription();
            wentry.setSnapshot(asset.isVSSnapshot());
            wentry.setIdentifier(asset.toIdentifier());
            wentry.setDescription(description);
            wentry.setAssetEntry(asset);
            vec.add(wentry);
         }
      }

      if(!isWSFolder) {
         // filtering viewsheets & snapshots
         int snapFilter = (filter & RepositoryEntry.SNAPSHOTS);
         int viewFilter = (filter & RepositoryEntry.VIEWSHEETS);

         if(snapFilter == RepositoryEntry.SNAPSHOTS || viewFilter == RepositoryEntry.VIEWSHEETS) {
            AssetEntry[] assets = getViewsheetEntries(parent, user, principal);

            for(AssetEntry asset : assets) {
               if(asset.isVSSnapshot()) {
                  if(snapFilter != RepositoryEntry.SNAPSHOTS) {
                     continue;
                  }
               }
               else {
                  if(viewFilter != RepositoryEntry.VIEWSHEETS) {
                     continue;
                  }
               }

               String viewsheetPath = ismy ? Tool.MY_DASHBOARD + "/" :
                  RepositoryEntry.REPOSITORY_FOLDER + "/";
               ViewsheetEntry ventry = new ViewsheetEntry(
                  viewsheetPath + asset.getPath(), asset.getUser());
               ventry.setSnapshot(asset.isVSSnapshot());
               ventry.setIdentifier(asset.toIdentifier());
               ventry.setAssetEntry(asset);
               vec.add(ventry);
            }
         }
      }

      return vec.toArray(new RepositoryEntry[0]);
   }

   /**
    * Gets the path for the specified user.
    *
    * @param path the path to convert.
    * @param user the user name.
    *
    * @return the converted path.
    */
   String getPath(String path, IdentityID user) {
      return addRepositoryRootFolder && user == null ?
         RepositoryEntry.REPOSITORY_FOLDER + "/" + path : path;
   }

   /**
    * Gets the alias for the specified asset.
    *
    * @param path      the path to the asset.
    * @param user      the user in whose repository contains the asset.
    * @param type      the type of the asset.
    * @param principal the principal that identifies the current user.
    *
    * @return the alias.
    *
    * @throws Exception if an error prevented the alias from being obtained.
    */
   public String getAlias(String path, IdentityID user, int type,
                          Principal principal) throws Exception
   {
      String alias = null;

      if(type == RepositoryEntry.VIEWSHEET ||
         type == RepositoryEntry.WORKSHEET ||
         type == RepositoryEntry.WORKSHEET_FOLDER)
      {
         int scope = user == null ?
            AssetRepository.GLOBAL_SCOPE : AssetRepository.USER_SCOPE;
         AssetEntry.Type assetType = (type == RepositoryEntry.VIEWSHEET) ?
            AssetEntry.Type.VIEWSHEET : (type == RepositoryEntry.WORKSHEET) ?
            AssetEntry.Type.WORKSHEET : AssetEntry.Type.FOLDER;
         AssetEntry entry = new AssetEntry(scope, assetType, path, user);
         entry = getAssetEntry(entry.toIdentifier(), principal);
         alias = entry.getAlias();
      }
      else if(type == RepositoryEntry.FOLDER) {
         alias = RepletRegistry.getRegistry(user).getFolderAlias(path);
      }

      return alias;
   }

   private final boolean addRepositoryRootFolder;

   /**
    * Exception that indicates that an asset name is already in use.
    */
   public static final class DuplicateNameException extends Exception {
   }

   /**
    * Exception that indicates that an asset could not be renamed.
    */
   public static final class RenameFailedException extends Exception {
      /**
       * Creates a new instance of <tt>RenameFailedException</tt>.
       *
       * @param message the error message.
       */
      public RenameFailedException(String message) {
         super(message);
      }
   }
}
