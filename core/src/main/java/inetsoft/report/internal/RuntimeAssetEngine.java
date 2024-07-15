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
package inetsoft.report.internal;

import inetsoft.report.ReportSheet;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;

import java.lang.ref.WeakReference;
import java.security.Principal;
import java.util.EnumSet;
import java.util.List;

/**
 * Runtime asset engine, the asset engine for execution.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class RuntimeAssetEngine implements AssetRepository {
   /**
    * Create a runtime asset engine.
    * @param server the specified server asset engine.
    * @param report the specified report sheet.
    */
   public RuntimeAssetEngine(AssetRepository server, AssetRepository report) {
      server.setParent(this);

      if(report != null) {
         report.setParent(this);
      }

      hash = server.hashCode();

      if(report != null) {
         hash = hash ^ report.hashCode();
      }

      this.server = new WeakReference<>(server);
      this.report = report == null ? null : new WeakReference<>(report);
   }

   /**
    * Get the asset repository.
    * @return the asset repository.
    */
   public AssetRepository getRepository() {
      return server.get();
   }

   /**
    * Get the report sheet.
    * @return the report sheet.
    */
   public ReportSheet getReport() {
      return report == null ? null : (ReportSheet) report.get();
   }

   /**
    * Check if the runtime asset engine is available.
    * @return <tt>true</tt> if available, <tt>false</tt> otherwise.
    */
   public boolean isAvailable() {
      if(server.get() == null) {
         return false;
      }

      return report == null || report.get() != null;
   }

   /**
    * Set the parent of the engine.
    * @param engine the specified parent.
    */
   @Override
   public void setParent(AssetRepository engine) {
      throw new UnsupportedOperationException();
   }

   /**
    * Get the parent of the engine.
    * @return the parent of the engine.
    */
   @Override
   public AssetRepository getParent() {
      throw new UnsupportedOperationException();
   }

  /**
   * Get the corresponding asset repository.
   * @param entry the specified asset entry.
   * @return the corresponding asset repository.
   */
  protected AssetRepository getAssetRepository(AssetEntry entry) {
     return entry.getScope() == REPORT_SCOPE ? getReport() : getRepository();
  }

   /**
    * Check if supports one scope.
    * @param scope the specified scope.
    * @return <tt>true</tt> if supports, <tt>false</tt> otherwise.
    */
   @Override
   public boolean supportsScope(int scope) throws Exception {
      return getRepository().supportsScope(scope) ||
         (getReport() != null && getReport().supportsScope(scope));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                  EnumSet<ResourceAction> actions)
   {
      return getRepository().checkPermission(principal, type, resource,  actions);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void checkAssetPermission(Principal principal, AssetEntry entry, ResourceAction action)
      throws Exception
   {
      AssetRepository rep = getAssetRepository(entry);
      rep.checkAssetPermission(principal, entry, action);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AssetEntry[] getEntries(AssetEntry entry, Principal user, ResourceAction action)
      throws Exception
   {
      AssetRepository rep = getAssetRepository(entry);
      return rep.getEntries(entry, user, action);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AssetEntry[] getEntries(AssetEntry entry, Principal user, ResourceAction action,
                                  AssetEntry.Selector selector) throws Exception
   {
      AssetRepository rep = getAssetRepository(entry);
      return rep.getEntries(entry, user, action, selector);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AssetEntry[] getAllEntries(AssetEntry entry, Principal user, ResourceAction permission,
                                     AssetEntry.Selector selector) throws Exception
   {
      AssetRepository rep = getAssetRepository(entry);
      return rep.getAllEntries(entry, user, permission, selector);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void addFolder(AssetEntry entry, Principal user) {
      throw new UnsupportedOperationException();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean containsEntry(AssetEntry entry) throws Exception {
      AssetRepository rep = getAssetRepository(entry);
      return rep != null && rep.containsEntry(entry);
   }

   @Override
   public AssetEntry getAssetEntry(AssetEntry entry) throws Exception {
      AssetRepository rep = getAssetRepository(entry);
      return rep != null ? rep.getAssetEntry(entry) : null;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void changeFolder(AssetEntry oentry, AssetEntry nentry, Principal user, boolean force) {
      throw new UnsupportedOperationException();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void changeFolder(AssetEntry oentry, AssetEntry nentry, Principal user, boolean force, boolean callFireEvent) {
      throw new UnsupportedOperationException();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void removeFolder(AssetEntry entry, Principal user, boolean force) {
      throw new UnsupportedOperationException();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AbstractSheet getSheet(AssetEntry entry, Principal user, boolean permission,
                                 AssetContent ctype) throws Exception
   {
      return getSheet(entry, user, permission, ctype, true);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AbstractSheet getSheet(AssetEntry entry, Principal user, boolean permission,
                                 AssetContent ctype, boolean useCache)
      throws Exception
   {
      AssetRepository rep = getAssetRepository(entry);
      return rep.getSheet(entry, user, permission, ctype, useCache);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setSheet(AssetEntry entry, AbstractSheet ws, Principal user, boolean force) {
      throw new UnsupportedOperationException();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void changeSheet(AssetEntry oentry, AssetEntry nentry, Principal user,
                           boolean force, boolean callFireEvent, boolean ignorePermissions)
   {
      throw new UnsupportedOperationException();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void changeSheet(AssetEntry oentry, AssetEntry nentry, Principal user,
      boolean force, boolean callFireEvent)
   {
      throw new UnsupportedOperationException();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void changeSheet(AssetEntry oentry, AssetEntry nentry, Principal user, boolean force) {
      throw new UnsupportedOperationException();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void removeSheet(AssetEntry entry, Principal user, boolean force) {
      throw new UnsupportedOperationException();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AssetEntry[] getSheetDependencies(AssetEntry entry, Principal user) {
      throw new UnsupportedOperationException();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void allowsFolderScopeChange(AssetEntry entry, int nscope, Principal user) {
      throw new UnsupportedOperationException();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void allowsSheetScopeChange(AssetEntry entry, int nscope, Principal user) {
      throw new UnsupportedOperationException();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void checkFolderRemoveable(AssetEntry entry, Principal user) {
      throw new UnsupportedOperationException();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void checkSheetRemoveable(AssetEntry entry, Principal user) {
      throw new UnsupportedOperationException();
   }

   /**
    * Make sure folders in repository and asset are in sync.
    */
   @Override
   public void syncFolders(Principal user) {
   }

   /**
    * Adds a listener that is notified when this repository is modified.
    *
    * @param listener the listener to remove.
    */
   @Override
   public void addAssetChangeListener(AssetChangeListener listener) {
      getRepository().addAssetChangeListener(listener);

      if(getReport() != null) {
         getReport().addAssetChangeListener(listener);
      }
   }

   /**
    * Removes an asset change listener from the notification list.
    *
    * @param listener the listener to remove.
    */
   @Override
   public void removeAssetChangeListener(AssetChangeListener listener) {
      getRepository().removeAssetChangeListener(listener);

      if(getReport() != null) {
         getReport().removeAssetChangeListener(listener);
      }
   }

   /**
    * Set the asset change listener.
    * @param listener the specified asset change listener.
    */
   public void setAssetChangeListener(AssetChangeListener listener) {
      this.listener = listener;
   }

   /**
    * Get the asset change listener.
    * @return the asset change listener if any.
    */
   public AssetChangeListener getAssetChangeListener() {
      return listener;
   }

   /**
    * Dispose the asset repository.
    */
   @Override
   public void dispose() {
      throw new UnsupportedOperationException();
   }

   /**
    * Get the session object.
    * @return the session object.
    */
   @Override
   public Object getSession() throws Exception {
      return getRepository().getSession();
   }

   /**
    * Get the hash code value
    * @return the hash code value.
    */
   public int hashCode() {
      return hash;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof RuntimeAssetEngine)) {
         return false;
      }

      RuntimeAssetEngine engine = (RuntimeAssetEngine) obj;
      return Tool.equals(engine.getRepository(), getRepository()) &&
         Tool.equals(engine.getReport(), getReport());
   }

   /**
    * Rename a user.
    * @param oname the old name of the user.
    * @param nname the new name of the user.
    */
   @Override
   public void renameUser(IdentityID oname, IdentityID nname) throws Exception {
      getRepository().renameUser(oname, nname);
   }

   /**
    * Remove a user.
    *
    * @param identityID the name of the user.
    */
   @Override
   public void removeUser(IdentityID identityID) throws Exception {
      getRepository().removeUser(identityID);
   }

   /**
    * Get the viewsheet bookmark.
    * @param entry the entry of the specified viewsheet.
    * @param user the specified user.
    * @return the viewsheet bookmark if any, <tt>null<tt> not found.
    */
   @Override
   public VSBookmark getVSBookmark(AssetEntry entry, Principal user)
      throws Exception
   {
      return getRepository().getVSBookmark(entry, user);
   }

   /**
    * Set the viewsheet bookmark.
    * @param entry the entry of the specified viewsheet.
    * @param bookmark the specified viewsheet bookmark.
    * @param user the specified user.
    */
   @Override
   public void setVSBookmark(AssetEntry entry, VSBookmark bookmark,
                             Principal user)
      throws Exception
   {
      getRepository().setVSBookmark(entry, bookmark, user);
   }

   /**
    * Clear all the viewsheet bookmarks.
    * @param entry the entry of the specified viewsheet.
    */
   @Override
   public void clearVSBookmark(AssetEntry entry) throws Exception {
      getRepository().clearVSBookmark(entry);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public List<IdentityID> getBookmarkUsers(AssetEntry entry) throws Exception {
      return getRepository().getBookmarkUsers(entry);
   }

   /**
    * Get the indexed storage of an asset entry.
    * @param entry the specified asset entry.
    * @return the indexed storage of the asset entry.
    */
   @Override
   public IndexedStorage getStorage(AssetEntry entry) throws Exception {
      return getRepository().getStorage(entry);
   }

   /**
    * Create an unique identifier for the entry.
    */
   @Override
   public String getEntryIdentifier(AssetEntry entry) {
      return entry.toIdentifier();
   }

   /**
    * Get the current report sheet.
    */
   @Override
   public ReportSheet getCurrentReport() {
      return null;
   }

   /**
    * Get current report sheet file name.
    */
   @Override
   public String getFileName() {
      return null;
   }

   /**
    *  Get asset entry of the current report sheet.
    */
   @Override
   public AssetEntry getAssetEntry() {
      return null;
   }

   @Override
   public void clearCache(AssetEntry entry) {
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void copyEntryProperty(AssetEntry entry, IndexedStorage storage) {
      getRepository().copyEntryProperty(entry, storage);
   }

   private WeakReference<AssetRepository> server;
   private WeakReference<AssetRepository> report;
   private AssetChangeListener listener;
   private int hash;
}
