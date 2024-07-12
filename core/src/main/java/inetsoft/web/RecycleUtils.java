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
package inetsoft.web;

import inetsoft.mv.SharedMVUtil;
import inetsoft.report.internal.Util;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static inetsoft.uql.asset.AssetRepository.REPORT_SCOPE;

/**
 * Utility methods operating for recycle bin.
 *
 * @version 13.1
 * @author InetSoft Technology Corp
 */
public final class RecycleUtils {
   /**
    * Name of the recycle bin folder.
    */
   public static final String RECYCLE_BIN_FOLDER = "Recycle Bin";
   public static final String RECYCLE_FILE_NAME = "server-recycle-bin.xml";
   public static final String MY_REPORT_RECYCLE
      = Tool.MY_DASHBOARD + "/" + RecycleUtils.RECYCLE_BIN_FOLDER;

   /**
    * Check if the specified asset is in the recycle bin folder.
    *
    * @param path the path to the asset.
    *
    * @return <tt>true</tt> if in the recycle bin; <tt>false</tt> otherwise.
    */
   public static boolean isInRecycleBin(String path) {
      return path != null && path.contains(RECYCLE_BIN_FOLDER);
   }

   /**
    * Gets the path in the recycle bin for the specified path.
    *
    * @param path the original path of the asset.
    *
    * @return the recycle bin path of the asset.
    */
   public static String getRecycleBinPath(String path) {
      return path == null || "/".equals(path) ?
         RECYCLE_BIN_FOLDER : RECYCLE_BIN_FOLDER + "/" + path;
   }

   public static void moveRepositoryToRecycleBin(RepositoryEntry entry, Principal principal,
                                                 RecycleBin recycleBin)
      throws Exception
   {
      if(entry.isFolder()) {
         moveRepositoryFolderToRecycleBin(entry.getPath(), entry.getName(), entry.getOwner(), principal, recycleBin);
      }
   }

   public static void moveRepositoryFolderToRecycleBin(String path, String name, IdentityID owner,
                                                       Principal principal, RecycleBin recycleBin)
      throws Exception
   {
      String newName = UUID.randomUUID().toString().replaceAll("-", "");
      String npath = getRecycleBinPath(newName);
      writeLock.lock();

      try {
         AssetRepository repository = AssetUtil.getAssetRepository(false);
         RepletRegistry registry = getRegistry(path, owner);
         int assetScope = SUtil.isMyDashboard(path) ? AssetRepository.USER_SCOPE :
            AssetRepository.GLOBAL_SCOPE;
         AssetEntry newEntry = new AssetEntry(assetScope, AssetEntry.Type.REPOSITORY_FOLDER, npath,
                                              assetScope == AssetRepository.GLOBAL_SCOPE ? null : owner);

         if(!repository.containsEntry(newEntry.getParent())) {
            repository.addFolder(newEntry.getParent(), principal);
         }

         npath = SUtil.isMyDashboard(path) ? Tool.MY_DASHBOARD + "/" + npath : npath;

         Permission originalPermission = getEntryPermission(ResourceType.REPORT, path);
         String msg = registry.changeFolder(path, npath, principal);

         if("true".equals(msg)) {
            registry.save();
            recycleBin.addEntry(npath, path, name, originalPermission,
                                RepositoryEntry.REPOSITORY | RepositoryEntry.FOLDER,
                                assetScope, owner);
            SecurityEngine.getSecurity().setPermission(ResourceType.REPORT, npath, originalPermission);
         }
         else {
            throw new RemoteException(msg);
         }
      }
      catch(MessageException e) {
         throw e;
      }
      catch(Exception ex) {
         throw new RemoteException("Failed to change registry folder from " +
            path + " to " + npath, ex);
      }
      finally {
         writeLock.unlock();
      }
   }

   public static void moveSheetToRecycleBin(AssetEntry oldEntry, Principal principal,
                                            RecycleBin recycleBin, boolean force)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);

      if(!force) {
         repository.checkSheetRemoveable(oldEntry, principal);
      }

      SharedMVUtil.removeMV(oldEntry);

      String newName = UUID.randomUUID().toString().replaceAll("-", "");
      AssetEntry newEntry = new AssetEntry(oldEntry.getScope(),
         oldEntry.getType(), getRecycleBinPath(newName), oldEntry.getUser());

      if(!repository.containsEntry(newEntry.getParent())) {
         repository.addFolder(newEntry.getParent(), principal);
      }

      ResourceType otype = getAssetResourceType(oldEntry);
      String opath = oldEntry.getPath();
      Permission originalPermission = getEntryPermission(otype, opath);

      newEntry.setCreatedDate(oldEntry.getCreatedDate());
      newEntry.setCreatedUsername(oldEntry.getCreatedUsername());
      newEntry.setModifiedDate(oldEntry.getModifiedDate());
      newEntry.setModifiedUsername(oldEntry.getModifiedUsername());
      newEntry.addFavoritesUser(oldEntry.getFavoritesUser());

      repository.changeSheet(oldEntry, newEntry, principal, true);
      recycleBin.addEntry(newEntry.getPath(), oldEntry.getPath(),
         oldEntry.getName(), originalPermission,
         oldEntry.isViewsheet() ? RepositoryEntry.VIEWSHEET :
         RepositoryEntry.WORKSHEET, oldEntry.getScope(), oldEntry.getUser());

      SecurityEngine.getSecurity().setPermission(otype, newEntry.getPath(), originalPermission);
   }

   public static void moveAssetFolderToRecycleBin(String oldPath, IdentityID owner, Principal principal,
                                                  RecycleBin recycleBin, boolean force)
      throws Exception
   {
      AssetEntry oldEntry;

      if(owner != null) {
         oldEntry = getAssetEntry(AssetEntry.Type.FOLDER, oldPath, owner);
      }
      else {
         oldEntry = getAssetEntry(AssetEntry.Type.FOLDER, oldPath);
      }

      moveAssetFolderToRecycleBin(oldEntry, principal, recycleBin, force);
   }

   public static void moveAssetFolderToRecycleBin(AssetEntry oldEntry, Principal principal,
                                                  RecycleBin recycleBin, boolean force)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);

      if(!force) {
         repository.checkFolderRemoveable(oldEntry, principal);
      }

      // no recycle bin for report embedded folder
      if(oldEntry.getScope() == REPORT_SCOPE) {
         repository.removeFolder(oldEntry, principal, true);
         return;
      }

      String newName = UUID.randomUUID().toString().replaceAll("-", "");
      AssetEntry newEntry = new AssetEntry(oldEntry.getScope(),
         AssetEntry.Type.FOLDER, getRecycleBinPath(newName), oldEntry.getUser());

      if(!repository.containsEntry(newEntry.getParent())) {
         repository.addFolder(newEntry.getParent(), principal);
      }

      newEntry.setCreatedDate(oldEntry.getCreatedDate());
      newEntry.setCreatedUsername(oldEntry.getCreatedUsername());
      newEntry.setModifiedDate(oldEntry.getModifiedDate());
      newEntry.setModifiedUsername(oldEntry.getModifiedUsername());

      ResourceType otype = getAssetResourceType(oldEntry);
      String opath = oldEntry.getPath();
      Permission originalPermission = getEntryPermission(otype, opath);

      repository.changeFolder(oldEntry, newEntry, principal, true);
      recycleBin.addEntry(newEntry.getPath(), oldEntry.getPath(), oldEntry.getName(),
         originalPermission, RepositoryEntry.WORKSHEET_FOLDER, oldEntry.getScope(),
         oldEntry.getUser());

      SecurityEngine.getSecurity().setPermission(otype, newEntry.getPath(), originalPermission);
   }

   public static void restoreSheet(RecycleBin.Entry rEntry, boolean overwrite,
                                   Principal principal, RecycleBin recycleBinFile)
      throws Exception
   {
      String path = rEntry.getOriginalScope() == AssetRepository.USER_SCOPE ?
         Tool.MY_DASHBOARD + "/" + rEntry.getPath() : rEntry.getPath();
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      boolean isViewsheet = rEntry.getType() != RepositoryEntry.WORKSHEET;
      IdentityID user = rEntry.getOriginalUser();
      AssetEntry oldEntry = getSheetEntry(path, isViewsheet, user);

      if(SUtil.isMyDashboard(path)) {
         oldEntry = getAssetEntry(oldEntry.getType(), path, user);
      }
      else {
         oldEntry = getAssetEntry(oldEntry.getType(), path);
      }

      String originalPath = rEntry.getOriginalPath();
      originalPath = SUtil.isMyDashboard(originalPath) ?
         originalPath.substring(Tool.MY_DASHBOARD.length() + 1) : originalPath;
      AssetEntry newEntry = new AssetEntry(rEntry.getOriginalScope(),
        oldEntry.getType(), originalPath, rEntry.getOriginalUser());
      validatePath(newEntry, principal);

      try {
         repository.getSheet(oldEntry, principal, false, AssetContent.ALL);
      }
      catch(Exception e) {
         LOG.error("Error restoring file metadata, Sheet {} is corrupt", oldEntry.getPath(), e);
      }

      newEntry.copyMetaData(oldEntry);

      if(oldEntry != null && AssetUtil.isDuplicatedEntry(repository, newEntry)) {
         if(overwrite) {
            repository.removeSheet(newEntry, principal, true);
         }
         else {
            return;
         }
      }

      if(!repository.containsEntry(newEntry.getParent())) {
         repository.addFolder(newEntry.getParent(), principal);
      }

      repository.changeSheet(oldEntry, newEntry, principal, true);
      recycleBinFile.removeEntry(path);
      ResourceType otype = getAssetResourceType(oldEntry);
      String opath = oldEntry.getPath();
      fixMoveEntryPermission(otype, opath, newEntry.getPath(), rEntry.getPermission());
   }

   public static String restoreWSFolder(RecycleBin.Entry rEntry, boolean overwrite,
                                      Principal principal, RecycleBin recycleBinFile)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      String path = rEntry.getPath();
      AssetEntry oldEntry = rEntry.getOriginalUser() == null ?
         getAssetEntry(getFolderType(true), path) :
         getAssetEntry(getFolderType(true), path, rEntry.getOriginalUser());
      oldEntry = getFolderAssetEntry(oldEntry.getParentPath(),
         oldEntry.toIdentifier(), oldEntry.getUser(), true, principal);

      AssetEntry newEntry = new AssetEntry(rEntry.getOriginalScope(),
         AssetEntry.Type.FOLDER, rEntry.getOriginalPath(), rEntry.getOriginalUser());
      validatePath(newEntry, principal);
      newEntry.setCreatedDate(oldEntry.getCreatedDate());
      newEntry.setCreatedUsername(oldEntry.getCreatedUsername());
      newEntry.setModifiedDate(oldEntry.getModifiedDate());
      newEntry.setModifiedUsername(oldEntry.getModifiedUsername());

      if(AssetUtil.isDuplicatedEntry(repository, newEntry)) {
         if(overwrite) {
            ResourceAction action = ResourceAction.valueOf("READ");
            AssetEntry[] entries = repository.getEntries(oldEntry, principal, action);
            RecycleUtils.restoreWSChildren(entries, rEntry, overwrite, principal, recycleBinFile);
            repository.removeFolder(oldEntry, principal, true);
            recycleBinFile.removeEntry(oldEntry.getPath());

            return Catalog.getCatalog(principal).getString("em.archiveSecurity.permissionClear");
         }

         return null;
      }

      if(!repository.containsEntry(newEntry.getParent())) {
         repository.addFolder(newEntry.getParent(), principal);
      }

      repository.changeFolder(oldEntry, newEntry, principal, true);
      recycleBinFile.removeEntry(path);
      ResourceType otype = getAssetResourceType(oldEntry);
      String opath = oldEntry.getPath();
      fixMoveEntryPermission(otype, opath, newEntry.getPath(), rEntry.getPermission());

      return null;
   }

   public static void restoreWSChildren(AssetEntry[] entries, RecycleBin.Entry rEntry,
                                        boolean overwrite, Principal principal,
                                        RecycleBin recycleBinFile)
      throws Exception
   {
      for(AssetEntry cEntry: entries) {
         RecycleBin.Entry crentry = new RecycleBin.Entry();
         crentry.setPath(cEntry.getPath());
         crentry.setOriginalPath(rEntry.getOriginalPath() + "/" + cEntry.getName());
         crentry.setOriginalScope(rEntry.getOriginalScope());
         crentry.setOriginalUser(rEntry.getOriginalUser());

         if(cEntry.isFolder()) {
            RecycleUtils.restoreWSFolder(crentry, overwrite, principal, recycleBinFile);
         }
         else {
            crentry.setType(RepositoryEntry.WORKSHEET);
            RecycleUtils.restoreSheet(crentry, overwrite, principal, recycleBinFile);
         }
      }
   }

   public static String restoreRepositoryFolder(RecycleBin.Entry rEntry, boolean overwrite,
                                              Principal principal, RecycleBin recycleBinFile)
      throws Exception
   {
      String path = rEntry.getPath();
      AssetEntry newEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                           AssetEntry.Type.REPOSITORY_FOLDER,
                                           rEntry.getOriginalPath(), null);
      RepletRegistry registry = RecycleUtils.getRegistry(
         rEntry.getOriginalPath(), rEntry.getOriginalUser());
      checkParentFolderExist(rEntry.getOriginalPath(), registry);
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      repository.checkAssetPermission(principal, newEntry.getParent(), ResourceAction.WRITE);

      if(registry.isFolder(rEntry.getOriginalPath())) {
         if(overwrite) {
            AnalyticRepository analyticRepository = SUtil.getRepletRepository();
            ResourceAction action = ResourceAction.valueOf("READ");
            RepositoryEntry[] entries = analyticRepository.getRepositoryEntries(path, principal,
               action, RepositoryEntry.ALL);
            RecycleUtils.restoreRepositoryChildren(entries, rEntry, overwrite, principal, recycleBinFile);
            registry.removeFolder(path);
            repository.checkAssetPermission(principal, newEntry.getParent(), ResourceAction.WRITE);
            registry.save();
            recycleBinFile.removeEntry(path);

            return Catalog.getCatalog(principal).getString("em.archiveSecurity.permissionClear");
         }
         else {
            return Catalog.getCatalog(principal).getString("em.content.overwriteDisabled");
         }
      }

      Principal user = rEntry.getOriginalUser() == null ?
         null : new XPrincipal(rEntry.getOriginalUser());
      repository.syncFolders(user);
      String msg = registry.changeFolder(path, rEntry.getOriginalPath(), principal);

      if("true".equals(msg)) {
         registry.save();
         recycleBinFile.removeEntry(path);
      }
      else {
         throw new RemoteException(msg);
      }

      return null;
   }

   public static void restoreRepositoryChildren(RepositoryEntry[] entries, RecycleBin.Entry rEntry,
                                                boolean overwrite, Principal principal,
                                                RecycleBin recycleBinFile)
      throws Exception
   {
      for(RepositoryEntry cEntry: entries) {
         RecycleBin.Entry crentry = new RecycleBin.Entry();
         crentry.setOriginalPath(rEntry.getOriginalPath() + "/" + cEntry.getName());
         crentry.setPath(cEntry.getPath());

         if(cEntry.isFolder()) {
            RecycleUtils.restoreRepositoryFolder(crentry, overwrite, principal, recycleBinFile);
         }
         else if(cEntry.getAssetEntry() != null && cEntry.getAssetEntry().isViewsheet()) {
            crentry.setOriginalScope(cEntry.getAssetEntry().getScope());
            crentry.setType(cEntry.getType());
            crentry.setOriginalUser(cEntry.getOwner());
            RecycleUtils.restoreSheet(crentry, overwrite, principal, recycleBinFile);
         }
      }
   }

   /**
    * Check if parent folder exist in repository. If not, add it.
    *
    * @param opath the path to the replet.
    */
   private static void checkParentFolderExist(String opath, RepletRegistry registry) {
      if(opath.lastIndexOf("/") > 0) {
         String oparent = opath.substring(0, opath.lastIndexOf("/"));

         if(!registry.isFolder(oparent)) {
            registry.addFolder(oparent);
         }
      }
   }

   private static void fixMoveEntryPermission(ResourceType type, String oldPath, String newPath,
      Permission newPermission)
   {
      SecurityEngine sengine = SecurityEngine.getSecurity();
      sengine.removePermission(type, oldPath);
      sengine.setPermission(type, newPath, newPermission);
      SecurityEngine.touch();
   }

   private static Permission getRecycleEntryPermission() {
      Permission perm = new Permission();
      Set<String> roles = Collections.singleton("Administrator");
      perm.setUserGrantsForOrg(ResourceAction.READ, roles, null );
      perm.setUserGrantsForOrg(ResourceAction.WRITE, roles, null);
      perm.setUserGrantsForOrg(ResourceAction.DELETE, roles, null);

      return perm;
   }

   private static Permission getEntryPermission(ResourceType type, String name) {
      SecurityEngine sengine = SecurityEngine.getSecurity();
      Permission originalPermission = null;
      Permission oEntryPermission = sengine.getPermission(type, name);
      ResourceType currentType = type;
      String currentResource = name;

      if(oEntryPermission != null) {
         originalPermission = oEntryPermission;
      }
      else {
         while(currentType.isHierarchical() && !"/".equals(currentResource)) {
            Resource parent = currentType.getParent(currentResource);

            if(parent != null) {
               currentResource = parent.getPath();
               currentType = parent.getType();
               oEntryPermission = sengine.getPermission(parent.getType(), parent.getPath());

               if(oEntryPermission != null) {
                  originalPermission = oEntryPermission;
                  break;
               }
            }
            else {
               parent = type.getRoot();

               if(parent != null) {
                  originalPermission = sengine.getPermission(parent.getType(), parent.getPath());
               }

               if(originalPermission == null) {
                  return null;
               }

               break;
            }
         }
      }

      return originalPermission;
   }

   /**
    * Get the asset permission prefix.
    *
    * @param entry the specified asset entry.
    * @return the asset premission prefix of the asset entry.
    */
   public static final ResourceType getAssetResourceType(AssetEntry entry) {
      if(entry.isViewsheet() || entry.isRepositoryFolder()) {
         return ResourceType.REPORT;
      }
      else {
         return ResourceType.ASSET;
      }
   }

   /**
    * Convenience method: Get AssetEntry type for folders.
    *
    * @param isWSFolder if worksheet folder.
    */
   private static AssetEntry.Type getFolderType(boolean isWSFolder) {
      return !isWSFolder ? AssetEntry.Type.REPOSITORY_FOLDER : AssetEntry.Type.FOLDER;
   }

   /**
    * Gets specified folder.
    *
    * @param parent       the path to the parent folder.
    * @param id           the id of the entry we are trying to match
    * @param isWSFolder   if worksheet folder.
    *
    * @return the folder.
    */
   private static AssetEntry getFolderAssetEntry(String parent, String id, IdentityID owner,
                                                 boolean isWSFolder, Principal principal)
      throws Exception
   {
      AssetEntry[] entries = getFolderEntries(parent, owner, isWSFolder, principal);

      for(AssetEntry entry : entries) {
         if(entry.toIdentifier().equals(id)) {
            return entry;
         }
      }

      return null;
   }

   /**
    * Gets the child folders of a folder
    *
    * @param path           path to folder
    * @param isWSFolder     if worksheet folder.
    *
    * @return the child entries
    *
    * @throws Exception if entries could not be retrieved
    */
   private static AssetEntry[] getFolderEntries(String path, IdentityID owner, boolean isWSFolder,
                                                Principal principal)
      throws Exception
   {
      return getEntries(path, owner, getFolderType(isWSFolder),
         new AssetEntry.Selector(getFolderType(isWSFolder)), principal);
   }

   /**
    * Gets the assets in the specified parent folder.
    *
    * @param path    the path to the parent folder.
    * @param entryType the type the parent folder.
    * @param selector  the type of assets to return.
    *
    * @return the entries representing the matching assets.
    *
    * @throws Exception if the assets could not be obtained.
    */
   private static AssetEntry[] getEntries(String path, IdentityID owner, AssetEntry.Type entryType,
                                          AssetEntry.Selector selector, Principal principal)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);

      if(repository != null) {
         AssetEntry pentry = owner == null ?
            new AssetEntry(AssetRepository.GLOBAL_SCOPE, entryType, path, null) :
            new AssetEntry(AssetRepository.USER_SCOPE, entryType, path, owner);
         return repository.getEntries(pentry, principal, ResourceAction.READ, selector);
      }

      return new AssetEntry[0];
   }

   /**
    * Gets the entry for a dashboard or dataset.
    * Returns null if the asset can't be found within the AssetRepository.
    *
    * @param path          the path to the dashboard or dataset
    * @param isViewsheet
    *
    * @return the asset entry in the Asset Repository; null if it wasn't found
    *
    * @throws Exception if the asset entry could not be obtained.
    */
   private static AssetEntry getSheetEntry(String path, boolean isViewsheet, IdentityID user)
      throws Exception
   {
      if(isViewsheet) {
         AssetEntry result = SUtil.isMyDashboard(path) ? getAssetEntry(AssetEntry.Type.VIEWSHEET, path,
            user) :
            getAssetEntry(AssetEntry.Type.VIEWSHEET, path);
         return result != null ? result : getAssetEntry(AssetEntry.Type.VIEWSHEET_SNAPSHOT, path);
      }

      if(SUtil.isMyDashboard(path)){
         return getAssetEntry(AssetEntry.Type.WORKSHEET, path, user);
      }
      else{
         return getAssetEntry(AssetEntry.Type.WORKSHEET, path);
      }
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
   private static AssetEntry getAssetEntry(AssetEntry.Type type, String path) throws Exception {
      AssetEntry result = null;

      if(path != null) {
         // the entries in recycle bin always be global scope.
         result = new AssetEntry(AssetRepository.GLOBAL_SCOPE, type, path, null);
         result = AssetUtil.getAssetRepository(false).getAssetEntry(result);
      }

      return result;
   }

   private static AssetEntry getAssetEntry(AssetEntry.Type type, String path, IdentityID owner) throws Exception {
      AssetEntry result = null;

      if(SUtil.isMyDashboard(path)) {
         path = path.substring(Tool.MY_DASHBOARD.length() + 1);
      }

      if(path != null) {
         result = new AssetEntry(AssetRepository.USER_SCOPE, type, path, owner);
         result = AssetUtil.getAssetRepository(false).getAssetEntry(result);
      }

      return result;
   }

   /**
    * Make sure the path of the entry to restore exists.
    * @param newEntry the specified entry to be recovered.
    * @param principal the specified user.
    */
   public static void validatePath(AssetEntry newEntry, Principal principal) throws Exception {
      if(SUtil.isMyDashboard(newEntry.getPath())) {
         return;
      }

      AssetEntry parent = newEntry.getParent();
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      RepletRegistry registry = RecycleUtils.getRegistry(
         newEntry.getPath(), newEntry.getUser());

      while(parent != null && !repository.containsEntry(parent)) {
         repository.addFolder(parent, principal);

         if(parent.isRepositoryFolder()) {
            registry.addFolder(parent.getPath(), null);
         }

         parent = parent.getParent();
      }
   }

   /**
    * Get replet registry.
    * @param name the specified folder or replet name.
    * @param owner the owner of the specified folder or replet.
    * @return replet registry contains the specified folder or replet name.
    */
   public static RepletRegistry getRegistry(String name, IdentityID owner)
      throws Exception
   {
      return SUtil.isMyReport(name) ?
         RepletRegistry.getRegistry(owner) :
         RepletRegistry.getRegistry();
   }

   /**
    * Get the original type label of an item in recycle bin.
    */
   public static String getTypeLabel(int type) {
      String typeLabel = "";

      switch(type) {
         case RepositoryEntry.FOLDER:
         case RepositoryEntry.REPOSITORY | RepositoryEntry.FOLDER:
            typeLabel = "folder";
            break;
         case RepositoryEntry.VIEWSHEET:
            typeLabel = "dashboard";
            break;
         case RepositoryEntry.WORKSHEET:
            typeLabel = "worksheet";
            break;
         case RepositoryEntry.WORKSHEET_FOLDER:
            typeLabel = "worksheet folder";
            break;
         case RepositoryEntry.PROTOTYPE:
            typeLabel = "prototype";
            break;
      }

      return typeLabel;
   }

   private static final ReentrantLock writeLock = new ReentrantLock();
   private static final Logger LOG = LoggerFactory.getLogger(RecycleUtils.class);
}
