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

import inetsoft.report.ReportSheet;
import inetsoft.sree.security.*;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.util.IndexedStorage;

import java.security.Principal;
import java.util.EnumSet;
import java.util.List;

/**
 * AssetRepository manages sheets and their folders, each one of which
 * belongs to a scope namely <tt>GLOBAL_SCOPE</tt>, <tt>REPORT_SCOPE</tt> and
 * <tt>USER_SCOPE</tt>. We can access any one of them using its associated
 * <tt>AssetEntry</tt>, which contains information including scope, type,
 * user, path and other properties like report id.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface AssetRepository {
   /**
    * Query scope asset.
    */
   int QUERY_SCOPE = 0;
   /**
    * Global scope asset.
    */
   int GLOBAL_SCOPE = 1;
   /**
    * Report scope asset.
    */
   int REPORT_SCOPE = 2;
   /**
    * User scope asset.
    */
   int USER_SCOPE = 4;
   /**
    * Temporary scope asset.
    */
   int TEMPORARY_SCOPE = 8;
   /**
    * Component scope asset.
    */
   int COMPONENT_SCOPE = 16;
   /**
    * Component scope asset.
    */
   int REPOSITORY_SCOPE = 32;

   /**
    * Thread local.
    */
   ThreadLocal<Object> ASSET_ERRORS = new ThreadLocal<>();

   ThreadLocal<Boolean> IGNORE_PERM = ThreadLocal.withInitial(() -> Boolean.FALSE);

   /**
    * Local query.
    */
   String LOCAL_QUERY = "Local Query";

   /**
    * Local worksheet.
    */
   String REPORT_WORKSHEET = "Local Worksheet";

   /**
    * Set the parent of the engine.
    * @param engine the specified parent.
    */
   void setParent(AssetRepository engine);

   /**
    * Get the parent of the engine.
    * @return the parent of the engine.
    */
   AssetRepository getParent();

   /**
    * Check if supports one scope.
    * @param scope the specified scope.
    * @return <tt>true</tt> if supports, <tt>false</tt> otherwise.
    */
   boolean supportsScope(int scope) throws Exception;

   /**
    * Check permission.
    *
    * @param principal the specified user.
    * @param type      the type of resource.
    * @param resource  the specified resource.
    * @param action    the permitted action.
    *
    * @return {@code true} if allowed {@code false} otherwise.
    */
   boolean checkPermission(Principal principal, ResourceType type, String resource,
                           EnumSet<ResourceAction> action);

   /**
    * Check asset permission.
    * @param principal the specified user.
    * @param entry the specified asset entry.
    * @param action the specified permission.
    */
   void checkAssetPermission(Principal principal, AssetEntry entry,
                             ResourceAction action) throws Exception;

   /**
    * Get the sub entries of a folder.
    * @param entry the specified folder entry.
    * @param user the specified user.
    * @param action the specified access way.
    * @return the sub entries of the folder.
    */
   AssetEntry[] getEntries(AssetEntry entry, Principal user,
                           ResourceAction action) throws Exception;

   /**
    * Get the sub entries of a folder.
    * @param entry the specified folder entry.
    * @param user the specified user.
    * @param action the specified access way.
    * @param selector used to select the type of entries to return. OR'ed
    * value of the entry types defined in AssetEntry.
    * @return the sub entries of the folder.
    */
   AssetEntry[] getEntries(AssetEntry entry, Principal user,
                           ResourceAction action,
                           AssetEntry.Selector selector)
      throws Exception;

   /**
    * Get the sub entries of a folder.
    * @param entry the specified folder entry.
    * @param user the specified user.
    * @param action the specified access way.
    * @param selector used to select the type of entries to return. OR'ed
    * value of the entry types defined in AssetEntry.
    * @return the sub entries of the folder.
    */
   AssetEntry[] getAllEntries(AssetEntry entry, Principal user,
                              ResourceAction action,
                              AssetEntry.Selector selector)
      throws Exception;

   /**
    * Add one folder.
    * @param entry the specified folder entry.
    * @param user the specified user.
    */
   void addFolder(AssetEntry entry, Principal user) throws Exception;

   /**
    * Check if contain the specified asset entry.
    * @param entry the specified asset entry.
    * @return <tt>true</tt> if contains, <tt>false</tt> otherwise.
    */
   boolean containsEntry(AssetEntry entry) throws Exception;

   /**
    * Get the asset entry from repository that contains meta information.
    */
   AssetEntry getAssetEntry(AssetEntry entry) throws Exception;

   /**
    * Change one folder.
    * @param oentry the specified old folder entry.
    * @param nentry the specified new folder entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to change folder forcely without checking dependency.
    */
   void changeFolder(AssetEntry oentry, AssetEntry nentry, Principal user, boolean force)
      throws Exception;

   /**
    * Change one folder.
    * @param oentry the specified old folder entry.
    * @param nentry the specified new folder entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to change folder forcely without checking dependency.
    * @param callFireEvent if send event
    */
   void changeFolder(AssetEntry oentry, AssetEntry nentry, Principal user, boolean force,  boolean callFireEvent)
           throws Exception;

   /**
    * Remove one folder.
    * @param entry the specified folder entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to remove folder forcely without checking.
    */
   void removeFolder(AssetEntry entry, Principal user, boolean force)
      throws Exception;

   /**
    * Get one sheet.
    * @param entry the specified sheet entry.
    * @param user the specified user.
    * @param permission <tt>true</tt> to check permission, <tt>false</tt> otherwise.
    * @param ctype the asset content type.
    * @return the associated sheet.
    */
   AbstractSheet getSheet(AssetEntry entry, Principal user,
                          boolean permission, AssetContent ctype)
      throws Exception;

   /**
    * Get one sheet.
    * @param entry the specified sheet entry.
    * @param user the specified user.
    * @param permission <tt>true</tt> to check permission, <tt>false</tt> otherwise.
    * @param ctype the asset content type.
    * @param useCache if use sheet cache.
    * @return the associated sheet.
    */
   AbstractSheet getSheet(AssetEntry entry, Principal user,
                          boolean permission, AssetContent ctype, boolean useCache)
      throws Exception;

   /**
    * Set one sheet.
    * @param entry the specified sheet entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to set sheet forcely without checking.
    */
   void setSheet(AssetEntry entry, AbstractSheet ws, Principal user,
                 boolean force)
      throws Exception;

   /**
    * Change one sheet.
    * @param oentry the specified old sheet entry.
    * @param nentry the specified new sheet entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to change sheet forcely without checking dependency.
    * @param callFireEvent if send event
    * @param ignorePermissions Whether permission is ignored
    */
   void changeSheet(AssetEntry oentry, AssetEntry nentry,
                    Principal user, boolean force, boolean callFireEvent, boolean ignorePermissions)
      throws Exception;

   /**
    * Change one sheet.
    * @param oentry the specified old sheet entry.
    * @param nentry the specified new sheet entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to change sheet forcely without checking dependency.
    * @param callFireEvent if send event
    */
   void changeSheet(AssetEntry oentry, AssetEntry nentry,
                    Principal user, boolean force, boolean callFireEvent)
      throws Exception;

   /**
    * Change one sheet.
    * @param oentry the specified old sheet entry.
    * @param nentry the specified new sheet entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to change sheet forcely without checking dependency.
    */
   void changeSheet(AssetEntry oentry, AssetEntry nentry,
                    Principal user, boolean force)
      throws Exception;

   /**
    * Remove one sheet.
    * @param entry the specified sheet entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to remove sheet forcely without checking.
    */
   void removeSheet(AssetEntry entry, Principal user, boolean force)
      throws Exception;

   /**
    * Get the sheets depend on a sheet.
    * @param entry the specified sheet entry.
    * @param user the specified user.
    * @return the sheet entries depend on the sheet.
    */
   AssetEntry[] getSheetDependencies(AssetEntry entry,
                                     Principal user) throws Exception;

   /**
    * Check if to change a folder scope is allowed.
    * @param entry the specified folder entry.
    * @param nscope the specified new scope to change to.
    * @param user the specified user.
    */
   void allowsFolderScopeChange(AssetEntry entry, int nscope,
                                Principal user) throws Exception;

   /**
    * Check if to change a sheet scope is allowed.
    * @param entry the specified sheet entry.
    * @param nscope the specified new scope to change to.
    * @param user the specified uder.
    */
   void allowsSheetScopeChange(AssetEntry entry, int nscope,
                               Principal user) throws Exception;

   /**
    * Check if folder is removeable.
    * @param entry the specified folder entry.
    * @param user the specified user.
    */
   void checkFolderRemoveable(AssetEntry entry, Principal user)
      throws Exception;

   /**
    * Check if a sheet is removeable.
    * @param entry the specified sheet entry.
    * @param user the specified user.
    */
   void checkSheetRemoveable(AssetEntry entry, Principal user)
      throws Exception;

   /**
    * Make sure folders in repository and asset are in sync.
    * @param user the repository for user or null for global.
    */
   void syncFolders(Principal user) throws Exception;

   /**
    * Adds a listener that is notified when this repository is modified.
    *
    * @param listener the listener to remove.
    */
   void addAssetChangeListener(AssetChangeListener listener);

   /**
    * Removes an asset change listener from the notification list.
    *
    * @param listener the listener to remove.
    */
   void removeAssetChangeListener(AssetChangeListener listener);

   /**
    * Get the session object.
    * @return the session object.
    */
   Object getSession() throws Exception;

   /**
    * Dispose the asset repository.
    */
   void dispose();

   /**
    * Rename a user.
    * @param oname the old name of the user.
    * @param nname the new name of the user.
    */
   void renameUser(IdentityID oname, IdentityID nname) throws Exception;

   /**
    * Remove a user.
    *
    * @param identityID the name of the user.
    */
   void removeUser(IdentityID identityID) throws Exception;

   /**
    * Get the viewsheet bookmark.
    * @param entry the entry of the specified viewsheet.
    * @param user the specified user.
    * @return the viewsheet bookmark if any, <tt>null<tt> not found.
    */
   VSBookmark getVSBookmark(AssetEntry entry, Principal user)
      throws Exception;

   /**
    * Set the viewsheet bookmark.
    * @param entry the entry of the specified viewsheet.
    * @param bookmark the specified viewsheet bookmark.
    * @param user the specified user.
    */
   void setVSBookmark(AssetEntry entry, VSBookmark bookmark,
                      Principal user)
      throws Exception;

   /**
    * Clear all the viewsheet bookmarks.
    * @param entry the entry of the specified viewsheet.
    */
   void clearVSBookmark(AssetEntry entry) throws Exception;

   /**
    * Return a list of users who have bookmarks for an entry
    *
    * @param entry The entry to use as criteria
    *
    * @return A list of users who have bookmarks for this entry
    */
   List<IdentityID> getBookmarkUsers(AssetEntry entry) throws Exception;

   /**
    * Get the indexed storage of an asset entry.
    * @param entry the specified asset entry.
    * @return the indexed storage of the asset entry.
    */
   IndexedStorage getStorage(AssetEntry entry) throws Exception;

   /**
    * Create an unique identifier for the entry.
    */
   String getEntryIdentifier(AssetEntry entry);

   /**
    * Get the current report sheet.
    */
   ReportSheet getCurrentReport();

   /**
    * Get current report sheet file name.
    */
   String getFileName();

   /**
    *  Get asset entry of the current report sheet.
    */
   AssetEntry getAssetEntry();

   /**
    * Clear the cache for the entry.
    */
   void clearCache(AssetEntry entry);

   /**
    * Copy properties such as the alias from the parent folder to the specified entry.
    *
    * @param entry the specified asset entry
    * @param storage the specified indexed storage
    */
   void copyEntryProperty(AssetEntry entry, IndexedStorage storage);
}
