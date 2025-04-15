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
package inetsoft.sree;

import inetsoft.sree.internal.*;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * RepletRegistry handles registration of replets. It loads the registration
 * file from the location specified in the 'replet.repository.file' property.
 *
 * @author InetSoft Technology Corp
 * @version 7.0
 */
@SingletonManager.Singleton(RepletRegistry.Reference.class)
public class RepletRegistry implements Serializable {
   /**
    * Add replet event.
    */
   private static final String ADD_REPLET_EVENT = "add_replet";
   /**
    * Add folder event.
    */
   static final String ADD_FOLDER_EVENT = "add_folder";

   /**
    * Rename replet event.
    */
   static final String RENAME_REPLET_EVENT = "rename_replet";
   /**
    * Rename folder event.
    */
   static final String RENAME_FOLDER_EVENT = "rename_folder";
   /**
    * Remove replet event.
    */
   static final String REMOVE_REPLET_EVENT = "remove_replet";
   /**
    * Remove folder event.
    */
   static final String REMOVE_FOLDER_EVENT = "remove_folder";
   /**
    * Edit cycle event.
    */
   public static final String EDIT_CYCLE_EVENT = "edit_cycle";
   /**
    * Change visibility event.
    */
   public static final String VISIBILITY_EVENT = "change_visibility";
   /**
    * Reload event.
    */
   public static final String RELOAD_EVENT = "reload";
   /**
    * Change event, per-transaction event, which should be fired after the
    * change process is over. It's useful for listeners like to be notified
    * but not care how when replet registry changes.
    */
   public static final String CHANGE_EVENT = "change";
   /**
    * Rename folder alias event.
    */
   static final String RENAME_FOLDER_ALIAS_EVENT = "rename_folder_alias";
   /**
    * Return a replet registry instance.
    */
   public static RepletRegistry getRegistry() throws Exception {
      return getRegistry(OrganizationManager.getInstance().getCurrentOrgID());
   }

   private static String getRegistryKey(String user, String orgID) {
      if(user == null) {
         if(orgID == null) {
            orgID = OrganizationManager.getInstance().getCurrentOrgID();
         }

         return GLOBAL_REPOSITORY + orgID;
      }

      return user;
   }

   /**
    * Return a replet registry instance. If userName is not null
    * the returned registry will represent the user's "My Reports"
    * folder.
    */
   public static RepletRegistry getRegistry(IdentityID userName) throws Exception {
      return getRegistry(userName, true);
   }

   /**
    * Return a replet registry instance. If userName is not null
    * the returned registry will represent the user's "My Reports"
    * folder.
    */
   public static RepletRegistry getRegistry(String orgID) throws Exception {
      return getRegistry(orgID, true);
   }

   /**
    * Return a replet registry instance. If userName is not null
    * the returned registry will represent the user's "My Reports"
    * folder.
    */
   public static RepletRegistry getRegistry(String orgID, boolean create) throws Exception {
      try {
         String key = getRegistryKey(null, orgID);
         ResourceCache<String, RepletRegistry> registryCache = getRegistryCache();

         // @by stephenwebster, For Bug #29146
         // During shutdown, do not re-initialize the replet registry.
         if(!create && !registryCache.contains(key)) {
            return null;
         }

         return SingletonManager.getInstance(RepletRegistry.class, key);
      }
      catch(Exception ex) {
         LOG.error("Failed to get registry", ex);
         throw ex;
      }
   }


   /**
    * Return a replet registry instance. If userName is not null
    * the returned registry will represent the user's "My Reports"
    * folder.
    */
   public static RepletRegistry getRegistry(IdentityID userName, boolean create) throws Exception {
      try {
         String key = getRegistryKey(userName == null ? null : userName.convertToKey(), null);
         ResourceCache<String, RepletRegistry> registryCache = getRegistryCache();

         // @by stephenwebster, For Bug #29146
         // During shutdown, do not re-initialize the replet registry.
         if(!create && !registryCache.contains(key)) {
            return null;
         }

         return SingletonManager.getInstance(RepletRegistry.class, key);
      }
      catch(Exception ex) {
         LOG.error("Failed to get registry", ex);
         throw ex;
      }
   }

   /**
    * Remove a user.
    *
    * @param identityID the name of the specified user.
    */
   public static synchronized void removeUser(IdentityID identityID) {
      try {
         RepletRegistry registry = getRegistryCache().remove(identityID.convertToKey());

         if(registry != null) {
            registry.dmgr.clear();
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to remove user from registry: " + identityID, ex);
      }

      // dangerous operation requires verification
      String path = "portal" + File.separator + identityID.getOrgID() + File.separator + identityID.getName();
      DataSpace space = DataSpace.getDataSpace();
      space.delete(null, path);
   }

   /**
    * Rename a user.
    *
    * @param oID the old name of the specified user.
    * @param nID the new name of the specified user.
    */
   public static synchronized void renameUser(IdentityID oID, IdentityID nID) {
      try {
         RepletRegistry registry = getRegistryCache().remove(oID.convertToKey());

         if(registry != null) {
            registry.dmgr.clear();
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to rename user from " + oID + " to " + nID, ex);
      }

      // dangerous operation requires verification
      String opath = "portal" + File.separator + oID.orgID + File.separator + oID.name;
      String npath = "portal" + File.separator + nID.orgID + File.separator + nID.name;
      DataSpace space = DataSpace.getDataSpace();

      if(space.exists("portal", oID.orgID + File.separator + oID.name)) {
         space.rename(opath, npath);
      }

      // for archive reports
      String oapath = npath + File.separator + oID.convertToKey() + "_archive_";

      if(space.exists(null, oapath)) {
         String napath = npath + File.separator + nID.convertToKey() + "_archive_";
         space.rename(oapath, napath);
      }
   }

   /**
    * Copy a user.
    *
    * @param oID the old name of the specified user.
    * @param nID the new name of the specified user.
    */
   public static synchronized void copyUser(IdentityID oID, IdentityID nID) throws Exception {
      String opath = "portal" + File.separator + oID.orgID + File.separator + oID.name;
      String npath = "portal" + File.separator + nID.orgID + File.separator + nID.name;
      DataSpace space = DataSpace.getDataSpace();

      if(space.exists("portal", oID.orgID + File.separator + oID.name)) {
         space.copy(opath, npath);
      }

      // for archive reports
      String oapath = npath + File.separator + oID.convertToKey() + "_archive_";

      if(space.exists(null, oapath)) {
         String napath = npath + File.separator + nID.convertToKey() + "_archive_";
         space.copy(oapath, napath);
      }

      RepletRegistry.changeOrgID(nID, oID.getOrgID(), nID.getOrgID());
   }

   /**
    * Move registry contents from one orgID to another.
    *
    * @param id the name of the specified user.
    * @param oOrgID the orgID to change from.
    * @param nOrgID the orgID to change to.
    */
   public static synchronized void changeOrgID(IdentityID id, String oOrgID, String nOrgID) {
      try {
         RepletRegistry oldRegistry;
         RepletRegistry newRegistry;

         if(id != null) {
            oldRegistry = getRegistryCache().get(getRegistryKey(id.convertToKey(), null));
            newRegistry = getRegistryCache().get(
               getRegistryKey(new IdentityID(id.getName(), nOrgID).convertToKey(), null));
         }
         else {
            oldRegistry = getRegistryCache().get(getRegistryKey(null, oOrgID));
            newRegistry = getRegistryCache().get(getRegistryKey(null, nOrgID));
         }

         Hashtable<String, String> oldFolderMap = oldRegistry.getFolderMap();
         Hashtable<String, String> newFolderMap = newRegistry.getFolderMap();
         newFolderMap.putAll(oldFolderMap);
         oldFolderMap.clear();

         Hashtable<String, FolderContext> oldFolderContextMap = oldRegistry.getFolderContextmap();
         Hashtable<String, FolderContext> newFolderContextMap = newRegistry.getFolderContextmap();
         newFolderContextMap.putAll(oldFolderContextMap);
         oldFolderContextMap.clear();

         oldRegistry.save();
         newRegistry.save();
      }
      catch(Exception ex) {
         LOG.error("Failed to move private folders from " + oOrgID + " to " + nOrgID, ex);
      }
   }

   /**
    * Clear the cached registry. The next call to getRegistry()
    * will reload the registry file. This should only be called by the
    * admin, otherwise it may cause a synchronization problem.
    */
   public static void clear() {
      clear(null);
   }

   /**
    * Clear the cached registry. The next call to getRegistry()
    * will reload the registry file. This should only be called by the
    * admin, otherwise it may cause a synchronization problem.
    */
   public static synchronized void clear(String user) {
      String key = getRegistryKey(user, null);

      try {
         RepletRegistry registry = getRegistryCache().remove(key);

         if(registry != null) {
            registry.dmgr.clear();
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to clear registry cache for user: " + user, ex);
      }
   }

   /**
    * Create a new registry.
    */
   public RepletRegistry(String orgID) throws Exception {
      this.orgID = orgID;
      init();
   }

   /**
    * Add property change listener.
    */
   public void addPropertyChangeListener(PropertyChangeListener listener) {
      synchronized(listeners) {
         for(WeakReference<PropertyChangeListener> ref : listeners) {
            PropertyChangeListener obj = ref.get();

            if(listener.equals(obj)) {
               return;
            }
         }

         WeakReference<PropertyChangeListener> ref = new WeakReference<>(listener);
         listeners.add(ref);
      }
   }

   /**
    * Remove property change listener.
    */
   public void removePropertyChangeListener(PropertyChangeListener listener) {
      synchronized(listeners) {
         for(int i = listeners.size() - 1; i >= 0; i--) {
            WeakReference<PropertyChangeListener> ref = listeners.get(i);
            PropertyChangeListener obj = ref.get();

            if(listener.equals(obj)) {
               listeners.remove(i);
               return;
            }
         }
      }
   }

   /**
    * Fire property change event.
    */
   protected void fireEvent(String src, String name, Object oval, Object nval) {
      Vector<WeakReference<PropertyChangeListener>> listeners;

      synchronized(this.listeners) {
         listeners = new Vector<>(this.listeners);
      }

      PropertyChangeEvent evt = new PropertyChangeEvent(src, name, oval, nval);

      fireEventToListeners(listeners, evt, name);

      synchronized(this.listeners) {
         listeners = new Vector<>(globalListeners);
      }

      fireEventToListeners(listeners, evt, name);
   }

   private void fireEventToListeners(Vector<WeakReference<PropertyChangeListener>> listeners,
                                     PropertyChangeEvent evt, String name)
   {
      for(int i = listeners.size() - 1; i >= 0; i--) {
         WeakReference<PropertyChangeListener> ref = listeners.get(i);
         PropertyChangeListener listener = ref.get();

         if(listener == null) {
            listeners.remove(i);
            continue;
         }

         try {
            listener.propertyChange(evt);
         }
         catch(Exception ex) {
            LOG.error("Failed to fire property change event: " + name, ex);
         }
      }
   }

   /**
    * Reload registry.
    */
   protected void reload() {
      try {
         init();
      }
      catch(Exception ex) {
         LOG.error("Failed to reload registry file", ex);
      }
   }

   /**
    * Check if should always keep uptodate, true to add data change listener.
    */
   protected boolean uptodate() {
      return true;
   }

   /**
    * Init the registry.
    */
   protected synchronized void init() throws Exception {
      // clear out the current in memory copy
      folders.clear();
//      filemap.clear();
//      filefoldermap.clear();

      DataSpace space = DataSpace.getDataSpace();
      String prop = SreeEnv.getProperty("dashboard.mydashboard.disabled");
      noMyreports = Tool.equals(prop, "true", false);
      String repfiles = SreeEnv.getProperty("replet.repository.file");

      // always add root folder
      getFolderMap().put("/", "/");

      StringTokenizer tokens = new StringTokenizer(repfiles, ";", false);

      try {
         while(tokens.hasMoreTokens()) {
            String repfile = getRegistryPath(Tool.convertUserFileName(tokens.nextToken()));

            if(uptodate()) {
               addChangeListener(space, null, repfile);
            }

            try(InputStream repository = space.getInputStream(null, repfile)) {
               if(repository == null) {
                  LOG.warn("Repository XML file not found: {}", repfile);
                  continue;
               }

               load(repository);
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to initialize the registry", ex);
      }
      finally {
         date = space.getLastModified(null, getRegistryPath());
         loaded = date != 0;
      }

      // always add My Reports folder
      if(!getFolderMap().containsKey(Tool.MY_DASHBOARD)) {
         getFolderMap().put(Tool.MY_DASHBOARD, Tool.MY_DASHBOARD);
         getFolderContextmap().put(Tool.MY_DASHBOARD, new FolderContext(Tool.MY_DASHBOARD));
      }
   }

   private String getRegistryPath(String originPath) {
      if(!Tool.isEmptyString(orgID)) {
         return orgID + "/" + originPath;
      }

      return originPath;
   }

   protected void addChangeListener(DataSpace space, String dir, String file) {
      dmgr.addChangeListener(space, dir, file, changeListener);
   }

   protected void load(InputStream repository) throws Exception {
      Tool.safeParseXMLBySAX(repository, new XMLHandler());
   }

   protected boolean isUserRegistry() {
      return false;
   }

   /**
    * Check if is valid replet node.
    */
   protected boolean isValidRepletNode(Attributes attributes) {
      return true;
   }

   /**
    * Get all folder names.
    *
    * @return all folder names.
    */
   public String[] getAllFolders() {
      return getAllFolders(noMyreports);
   }

   /**
    * Get all folder names.
    *
    * @param publicOnly <tt>true</tt> if only include pulic folder.
    * @return all folder names.
    */
   public synchronized String[] getAllFolders(boolean publicOnly) {
      List<String> fns = new ArrayList<>();
      Iterator<String> it = getFolderMap().keySet().iterator();

      for(int i = 0; i < getFolderMap().size() && it.hasNext(); i++) {
         fns.add(it.next());
      }

      if(publicOnly) {
         fns.remove(Tool.MY_DASHBOARD);
      }

      return fns.toArray(new String[0]);
   }

   /**
    * Remove the folder and all the replets inside it.
    *
    * @param folderName folder.
    * @return true if remove successfully.
    */
   public boolean removeFolder(String folderName) {
      return removeFolder(folderName, true, false);
   }

   /**
    * Remove the folder and all the replets inside it.
    */
   public synchronized boolean removeFolder(String folderName, boolean transaction,
                                            boolean saveBeforeEvent, boolean fireEvent)
   {
      if("/".equals(folderName) || Tool.MY_DASHBOARD.equals(folderName)) {
         return false;
      }

      String prefix = folderName + "/";
      List<String> list = new ArrayList<>(getFolderMap().keySet());
      boolean changed = false;

      for(String folder : list) {
         if(folder.equals(folderName) || folder.startsWith(prefix)) {
            getFolderMap().remove(folder);
            changed = true;

            if(fireEvent) {
               fireEvent("registry_" + (folder.equals(folderName) && transaction),
                         REMOVE_FOLDER_EVENT, folder, folder);
            }
         }
      }

      if(changed && transaction) {
         if(saveBeforeEvent) {
            try{
               save();
            }
            catch (Exception e) {
               LOG.warn("Failed to save registry", e);
            }
         }

         if(fireEvent) {
            fireEvent("registry_", CHANGE_EVENT, null, null);
         }
      }

      getFolderContextmap().remove(folderName);

      return true;
   }

   public synchronized boolean removeFolder(String folderName, boolean transaction,
                                            boolean saveBeforeEvent)
   {
      return removeFolder(folderName, transaction, saveBeforeEvent, true);
   }

   /**
    * Add a new folder.
    *
    * @param folder the folder.
    * @return true if add successfully.
    */
   public boolean addFolder(String folder) {
      return addFolder(folder, true);
   }

   /**
    * Add a new folder.
    *
    * @param folder the folder.
    * @return true if add successfully.
    */
   public boolean addFolder(String folder, boolean fireEvent) {
      return addFolder(folder, true, fireEvent);
   }

   /**
    * Add a new folder.
    */
   private synchronized boolean addFolder(String folder, boolean transaction, boolean fireEvent)
   {
      if(folder == null || folder.equals("")) {
         return false;
      }

      // flag to check whether folder+contents already exists
      // if the folder is an exact duplicate (folder name + folder contents
      // are the same), set to true
      // if the folder is not an exact duplicate, set boolean to false
      boolean exactDuplicate = false;

      if(getFolderMap().containsKey(folder)) {
         // @by stevenkuo bug1418949929088 2015-1-9
         // added a conditional that checks the current folder path
         // with the pre-existing folder in the system.
         // if they're the same folder name + content, it means it does not have to be
         // recreated and should continue up the folderpath until the it reaches the root
         String f = getFolderMap().get(folder);

         if(Arrays.equals(getFolders(folder), (getFolders(f)))) {
            exactDuplicate = true;
         }
         else {
            return false;
         }
      }

      if(!exactDuplicate) {
         getFolderMap().put(folder, folder);

         if(fireEvent) {
            fireEvent("registry_" + transaction, ADD_FOLDER_EVENT, folder, folder);
         }

         if(transaction && fireEvent) {
            fireEvent("registry_", CHANGE_EVENT, null, null);
         }
      }

      int idx = folder.lastIndexOf('/');

      // create parents
      if(idx > 0) {
         folder = folder.substring(0, idx);
         return addFolder(folder, transaction);
      }

      return true;
   }

   /**
    * Get sub folders of a folder.
    */
   public String[] getFolders(String folder) {
      return getFolders(folder, noMyreports);
   }

   /**
    * Get sub folders of a folder.
    */
   public synchronized String[] getFolders(String folder, boolean publicOnly) {
      boolean root = folder == null || folder.equals("/");
      Iterator<String> iterator = getFolderMap().keySet().iterator();
      List<String> result = new ArrayList<>();

      while(iterator.hasNext()) {
         String tfolder = iterator.next();
         int index = tfolder.lastIndexOf('/');

         if(index == -1 && root) {
            result.add(tfolder);
         }
         else if(index != -1 &&
            Tool.equals(folder, tfolder.substring(0, index))) {
            result.add(tfolder);
         }
      }

      if(!root || publicOnly) {
         result.remove(Tool.MY_DASHBOARD);
      }

      return result.toArray(new String[0]);
   }

   /**
    * Change folder name.
    *
    * @param oldFolderName old folder name.
    * @param newFolderName new folder name.
    * @return true if change successfully, error message if not.
    */
   public String changeFolder(String oldFolderName, String newFolderName) {
      return changeFolder(oldFolderName, newFolderName, null);
   }

   /**
    * Change folder name.
    *
    * @param oldFolderName old folder name.
    * @param newFolderName new folder name.
    * @return true if change successfully, error message if not.
    */
   public synchronized String changeFolder(String oldFolderName, String newFolderName, Principal principal) {
      Catalog catalog = Catalog.getCatalog();

      if(oldFolderName == null) {
         return catalog.getString("common.repletRegistry.oldNameNull");
      }

      if(newFolderName == null) {
         return catalog.getString("common.repletRegistry.folderNameNull");
      }

      if(oldFolderName.equals(newFolderName)) {
         return "true";
      }

      if(getFolderMap().containsKey(newFolderName)) {
         return catalog.getString("common.repletRegistry.folderExist", newFolderName);
      }

      //todo
      if(SUtil.isDefaultVSGloballyVisible(principal) && principal != null && principal != null &&
         !Tool.equals(((XPrincipal) principal).getOrgId(), Organization.getDefaultOrganizationID()) &&
         !getFolderMap().contains(oldFolderName) &&
         getFolderMap().contains(oldFolderName)) {
         return catalog.getString("common.writeAuthority", Organization.getDefaultOrganizationID());
      }

      String oprefix = oldFolderName + "/";
      List<String> all = new ArrayList<>(getFolderMap().keySet());
      boolean changed = false;

      for(String oname : all) {
         if(oname.equals(oldFolderName) || oname.startsWith(oprefix)) {
            String nname = newFolderName + oname.substring(oldFolderName.length());
            renameFolder(oname, nname, principal, oname.equals(oldFolderName));
            changed = true;
         }
      }

      if(changed) {
         fireEvent("registry_", CHANGE_EVENT, null, null);
      }

      return "true";
   }

   /**
    * Rename folder.
    */
   private synchronized void renameFolder(String ofolder, String nfolder, Principal principal,
                                          boolean transaction)
   {
      getFolderMap().remove(ofolder);
      getFolderMap().put(nfolder, nfolder);
      FolderContext context = getFolderContextmap().get(ofolder);
      getFolderContextmap().remove(ofolder);

      if(context != null) {
         context.setName(nfolder);
         getFolderContextmap().put(nfolder, context);
      }

      fireEvent("registry_" + transaction, RENAME_FOLDER_EVENT, ofolder, nfolder);
   }

   /**
    * Get the number of folders registered in the registry.
    */
   public int getFolderCount() {
      return getFolderMap().size();
   }

   protected FolderContext getFolderContext(String name) {
      return getFolderContextmap().get(name);
   }

   /**
    * Set the folder alias.
    *
    * @param name  folder name.
    * @param alias folder alias.
    */
   public void setFolderAlias(String name, String alias) {
      setFolderAlias(name, alias, false);
   }

   /**
    * Set the folder alias and fires rename_folder_event
    *
    * @param name  folder name.
    * @param alias folder alias.
    */
   public void setFolderAlias(String name, String alias, boolean fireEvent) {
      FolderContext context = getFolderContext(name);

      if(context == null) {
         context = new FolderContext(name);
         getFolderContextmap().put(name, context);
      }

      context.setAlias(alias);

      if(fireEvent) {
         fireEvent("registry_true", RENAME_FOLDER_ALIAS_EVENT, name, alias);
      }
   }

   /**
    * Get the description of a folder.
    *
    * @param name folder name.
    * @return folder description.
    */
   public String getFolderAlias(String name) {
      FolderContext context = getFolderContext(name);

      if(context != null) {
         return context.getAlias();
      }

      return null;
   }

   /**
    * Set the folder descriptions.
    *
    * @param name        folder name.
    * @param description folder descriptions.
    */
   public void setFolderDescription(String name, String description) {
      FolderContext context = getFolderContext(name);

      if(context == null) {
         context = new FolderContext(name);
         getFolderContextmap().put(name, context);
      }

      context.setDescription(description);
   }

   /**
    * Get the description of a folder.
    *
    * @param name folder name.
    * @return folder description.
    */
   public String getFolderDescription(String name) {
      DefaultContext context = getFolderContext(name);

      if(context != null) {
         return context.getDescription();
      }

      return null;
   }

   /**
    * add principal to folder's favoritesUser.
    *
    * @param name    replet name.
    */
   public void addFolderFavoritesUser(String name, String principal) {
      FolderContext context = getFolderContext(name);

      if(context != null) {
         context.addFavoritesUser(principal);
      }
   }

   public void deleteFolderFavoritesUser(String name, String principal) {
      FolderContext context = getFolderContext(name);

      if(context != null) {
         context.deleteFavoritesUser(principal);
      }
   }

   /**
    * Get Folder's favoritesUser
    *
    * @param name replet name.
    * @return favoritesUser
    */
   public String getFolderFavoritesUser(String name) {
      FolderContext context = getFolderContext(name);

      if(context != null) {
         return context.getFavoritesUser();
      }

      return "";
   }

   /**
    * Check if is a folder.
    */
   public boolean isFolder(String folder) {
      return getFolderMap().containsKey(folder);
   }

   /**
    * Get registry path to save.
    */
   protected String getRegistryPath() {
      String path = SreeEnv.getProperty("replet.repository.file");
      int index = path.lastIndexOf(';');
      return index >= 0 ? getRegistryPath(path.substring(index + 1)) : getRegistryPath(path);
   }

   /**
    * Save the registry to a registry file.
    */
   public synchronized void save() throws Exception {
      DataSpace space = DataSpace.getDataSpace();
      String repfile = getRegistryPath();

      if(!loaded) {
         // repository.xml exists in the data space but wasn't loaded so reload the registry
         if(space.exists(null, repfile)) {
            reload();
         }

         loaded = true;
      }

      int idx = repfile.lastIndexOf('.');
      String repb = ((idx > 0) ? repfile.substring(0, idx) : repfile) + ".bak";

      try(InputStream istream = space.getInputStream(null, repfile)) {
         if(istream != null) {
            space.withOutputStream(null, repb, ostream -> Tool.fileCopy(istream, ostream));
         }
      }
      catch(Throwable exc) {
         throw new Exception("Failed to save repository.xml", exc);
      }

      dmgr.removeChangeListener(space, getRegistryDir(), getRegistryFileName(), changeListener);

      try {
         space.withOutputStream(null, repfile, this::save);
      }
      catch(Throwable exc) {
         throw new Exception("Failed to save repository.xml", exc);
      }
      finally {
         date = space.getLastModified(null, repfile);

         if(uptodate()) {
            dmgr.addChangeListener(space, getRegistryDir(), getRegistryFileName(), changeListener);
         }
      }
   }

   protected String getRegistryDir() {
      return null;
   }

   protected String getRegistryFileName() {
      String path = SreeEnv.getProperty("replet.repository.file");
      int index = path.lastIndexOf(';');
      return index >= 0 ? getRegistryPath(path.substring(index + 1)) : getRegistryPath(path);
   }

   /**
    * Get the last modified.
    */
   protected long getLastModified() {
      if(date == -2L) {
         DataSpace space = DataSpace.getDataSpace();
         String repfile = getRegistryPath();
         date = space.getLastModified(null, repfile);
      }

      return date;
   }

   /**
    * Save the registry to a specific stream.
    *
    * @param stream the registry stream.
    */
   private synchronized void save(OutputStream stream) throws IOException {
      try {
         PrintWriter writer =
            new PrintWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));

         writer.println("<?xml version=\"1.0\"  encoding=\"UTF-8\"?>");
         writer.println("<Registry>");
         writer.println("<Version>" + FileVersions.REPOSITORY + "</Version>");

         writeFoldersAndProtos(writer);
         writer.println("</Registry>");
         writer.flush();
      }
      catch(Throwable ex) {
         throw new IOException("Failed to save registry file", ex);
      }
   }

   /**
    * Write folder.
    */
   protected synchronized void writeFoldersAndProtos(PrintWriter writer) {
      for(String folder : folders.keySet()) {
         if(folder.isEmpty()) {
            continue;
         }

         String alias = getFolderAlias(folder);
         writer.print("<Replet name=\"" + Tool.escape(folder) + "\"" +
                         (alias == null ? "" : " alias=\"" + Tool.escape(alias) + "\"") +
                         " orgID=\"" + orgID + "\" folder=\"true\"" +
                         " favoritesUser=\"" + getFolderFavoritesUser(Tool.escape(folder)) + "\"" + ">");

         if(getFolderDescription(folder) != null) {
            writer.print("<![CDATA[" + getFolderDescription(folder) + "]]>");
         }

         writer.println("</Replet>");
      }
   }

   private synchronized Hashtable<String, String> getFolderMap() {
      if(!folders.containsKey(Tool.MY_DASHBOARD)) {
         folders.put(Tool.MY_DASHBOARD, Tool.MY_DASHBOARD);
      }

      if(!folders.containsKey("/")) {
         folders.put("/", "/");
      }

      return folders;
   }

   private synchronized Hashtable<String, FolderContext> getFolderContextmap() {
     if(!foldercontextmap.containsKey(Tool.MY_DASHBOARD)) {
        foldercontextmap.put(Tool.MY_DASHBOARD, new FolderContext(Tool.MY_DASHBOARD));
     }

      return foldercontextmap;
   }

   //Todo
   public static void copyFolderContextMap(String oOID, String nOID) throws Exception {
      RepletRegistry oldRegistry = getRegistry(oOID);
      RepletRegistry newRegistry = getRegistry(nOID);
      Hashtable<String, FolderContext> otable = oldRegistry.getFolderContextmap();
      Hashtable<String, FolderContext> ntable = newRegistry.getFolderContextmap();

      otable.forEach((key, value) -> {
         FolderContext ncontext = new FolderContext(value.getName(), value.getDescription(),
            value.getAlias());
         ntable.put(key, ncontext);
      });
   }

   private static ResourceCache<String, RepletRegistry> getRegistryCache() {
      ResourceCache<String, RepletRegistry> cache;
      REGISTRY_CACHE_LOCK.readLock().lock();

      try {
         cache = ConfigurationContext.getContext().get(REGISTRY_CACHE_KEY);
      }
      finally {
         REGISTRY_CACHE_LOCK.readLock().unlock();
      }

      if(cache == null) {
         REGISTRY_CACHE_LOCK.writeLock().lock();

         try {
            cache = ConfigurationContext.getContext().get(REGISTRY_CACHE_KEY);

            if(cache == null) {
               cache = new RepletRegistryCache();

               ConfigurationContext.getContext().put(REGISTRY_CACHE_KEY, cache);
            }
         }
         finally {
            REGISTRY_CACHE_LOCK.writeLock().unlock();
         }
      }

      return cache;
   }

   /**
    * Data change listener.
    */
   private final DataChangeListener changeListener = e -> {
      LOG.debug(e.toString());

      try {
         if(isUserRegistry()) {
            reload();
         }
         else {
            init();
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to re-initialize registry", ex);
      }

      // @by jasonshobe, need to get lock on the registry prior to firing
      // event to prevent deadlock introduced by DataCycleManager
      synchronized(RepletRegistry.this) {
         fireEvent("registry_", RELOAD_EVENT, null, null);
         fireEvent("registry_", CHANGE_EVENT, null, null);
      }
   };

   /**
    * remove global property change listener .
    */
   public static void removeGlobalPropertyChangeListener(PropertyChangeListener listener) {
      synchronized(globalListeners) {
         for(int i = globalListeners.size() - 1; i >= 0; i--) {
            WeakReference<PropertyChangeListener> ref = globalListeners.get(i);
            PropertyChangeListener obj = ref.get();

            if(listener.equals(obj)) {
               globalListeners.remove(i);
               return;
            }
         }
      }
   }

   /**
    * Add global property change listener to listen the all user replet changed.
    */
   public static void addGlobalPropertyChangeListener(PropertyChangeListener listener) {
      synchronized(globalListeners) {
         for(WeakReference<PropertyChangeListener> ref : globalListeners) {
            PropertyChangeListener obj = ref.get();

            if(listener.equals(obj)) {
               return;
            }
         }

         WeakReference<PropertyChangeListener> ref = new WeakReference<>(listener);
         globalListeners.add(ref);
      }
   }

   /**
    * User replet registry.
    */
   private final static class UserRepletRegistry extends RepletRegistry {
      UserRepletRegistry(String user) throws Exception {
         super(null);
         this.user = user;
         init0();
      }

      @Override
      protected void init() throws Exception {
         // ignore until user is assigned
      }

      @Override
      protected boolean uptodate() {
         return SUtil.isCluster() || "true".equals(System.getProperty("ScheduleServer"));
      }

      @Override
      protected boolean isUserRegistry() {
         return true;
      }

      private void init0() throws Exception {
         // clear out the current in memory copy
         folders.clear();
//         filemap.clear();
//         filefoldermap.clear();

         load();

         // always add My Reports folder
         if(!folders.containsKey(Tool.MY_DASHBOARD)) {
            folders.put(Tool.MY_DASHBOARD, Tool.MY_DASHBOARD);
         }

         if(!folders.containsKey("/")) {
            folders.put("/", "/");
         }

         if(uptodate()) {
            addChangeListener(DataSpace.getDataSpace(), getRegistryDir(),
               getRegistryFileName());
         }
      }

      synchronized void load() throws Exception {
         String file = getRegistryPath();
         DataSpace space = null;

         try {
            space = DataSpace.getDataSpace();

            if(!space.exists(null, file)) {
               return;
            }

            try(InputStream input = space.getInputStream(null, file)) {
               if(input == null) {
                  return;
               }

               load(input);
            }
         }
         finally {
            date = space == null ? 0 : space.getLastModified(null, file);
            loaded = date != 0;
         }
      }

      private String getUser() {
         return user;
      }

      @Override
      protected String getRegistryPath() {
         return getRegistryDir() + getRegistryFileName();
      }

      @Override
      protected String getRegistryDir() {
         IdentityID userID = IdentityID.getIdentityIDFromKey(getUser());
         return "portal" + File.separator + userID.orgID + File.separator + userID.name + File.separator +
            "my dashboard" + File.separator;
      }

      @Override
      protected String getRegistryFileName() {
         return "repository.xml";
      }

      @Override
      protected boolean isValidRepletNode(Attributes attributes) {
         String name = attributes.getValue("name");
         String folder = SUtil.getFolder(name);
         return SUtil.isMyReport(folder);
      }

      /**
       * Fire property change event.
       */
      @Override
      protected void fireEvent(String src, String name, Object oval,
                               Object nval) {
         String user = getUser();
         src = src + "^" + user;
         super.fireEvent(src, name, oval, nval);
      }

      @Override
      protected synchronized void reload() {
         String file = getRegistryPath();

         try {
            long ndate = DataSpace.getDataSpace().getLastModified(null, file);

            if(ndate != date) {
               init0();
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to reload registry file", ex);
         }
      }

      private final String user;
   }

   private static class RepletRegistryCache extends ResourceCache<String, RepletRegistry> {
      public RepletRegistryCache() {
         super(50);
      }

      @Override
      public RepletRegistry get(String key) throws Exception {
         boolean exists = contains(key);
         RepletRegistry registry = super.get(key);

         // @by larryl, the getRepletRepository() can't be called in create.
         // It may in turn call getRepletRegistry() again, and causes two
         // instances of RepletRegistry to be created for the same key
         if(!exists) {
            AnalyticRepository engine = SUtil.getRepletRepository();

            if(engine instanceof PropertyChangeListener) {
               registry.addPropertyChangeListener(
                  (PropertyChangeListener) engine);
            }
         }
         else if(!registry.loaded) {
            DataSpace space = DataSpace.getDataSpace();
            String repfile = registry.getRegistryPath();

            if(space.exists(null, repfile)) {
               registry.reload();
            }
         }

         return registry;
      }

      @Override
      public RepletRegistry create(String key) throws Exception {
         RepletRegistry registry;

         if(key.startsWith(GLOBAL_REPOSITORY)) {
            registry = new RepletRegistry(key.substring(GLOBAL_REPOSITORY.length()));
         }
         else {
            registry = new UserRepletRegistry(key);
         }

         return registry;
      }

      @Override
      protected void processRemoved(RepletRegistry value) {
         // avoid memory leak
         value.dmgr.clear();
      }
   }

   class XMLHandler extends DefaultHandler {
      @Override
      public void characters(char[] ch, int start, int length) {
         String str = new String(ch, start, length);

         if(context != null && !"\n".equals(str)) {
            if(buffer.length() != 0) {
               cdatabuf.append(str);
            }
            else {
               String desc = context.getDescription();
               desc = desc == null ? str : desc + str;
               context.setDescription(desc);
            }
         }
      }

      @Override
      public void startElement(String uri, String localName, String ename, Attributes attrs) {
         if("Replet".equals(ename)) {
            boolean isReplet = "Replet".equals(ename);

            if(isReplet && !isValidRepletNode(attrs)) {
               return;
            }

            String name = attrs.getValue("name");
            String alias = attrs.getValue("alias");
            String favoritesUser = attrs.getValue("favoritesUser");

            if(SUtil.isMyReport(name)) {
               if(!getFolderMap().containsKey(Tool.MY_DASHBOARD)) {
                  getFolderMap().put(Tool.MY_DASHBOARD, Tool.MY_DASHBOARD);
               }
            }

            String fstr = attrs.getValue("folder");

            if("true".equals(fstr)) {
               if(!getFolderMap().containsKey(name)) {
                  getFolderMap().put(name, name);
               }

               this.context = new FolderContext(name, "", alias);
               this.context.addFavoritesUser(favoritesUser);
               getFolderContextmap().put(name, (FolderContext) this.context);
               return;
            }

            // process replet
            String pfolder = SUtil.getFolder(name);

            if(!getFolderMap().containsKey(pfolder)) {
               getFolderMap().put(pfolder, pfolder);
            }
         }
         else if("Request".equals(ename) || buffer.length() != 0) {
            put(ename, attrs, true);
         }
      }

      @Override
      public void endElement(String uri, String localName, String name) {
         if(cdatabuf.length() > 0) {
            buffer.append("<![CDATA[").append(cdatabuf).append("]]>");
            cdatabuf.setLength(0);
         }

         if(context != null && ("Request".equals(name) || buffer.length() > 0)) {
            put(name, null, false);

            if("Request".equals(name)) {
               try {
                  Document doc =
                     Tool.parseXML(new StringReader(buffer.toString()));

                  if(doc != null) {
                     RepletRequest request = new RepletRequest();
                     request.parseXML((Element) doc.getFirstChild());
                     list.add(request);
                  }
               }
               catch(Exception ex) {
                  LOG.error("Failed to parse replet request", ex);
               }

               buffer.setLength(0);
            }
         }
      }

      private void put(String name, Attributes attrs, boolean start) {
         if(!start) {
            buffer.append("</").append(name).append(">");
         }
         else {
            buffer.append("<").append(name);

            for(int i = 0; i < attrs.getLength(); i++) {
               buffer.append(" ").append(attrs.getQName(i)).append("=\"")
                  .append(Tool.escape(attrs.getValue(i))).append("\"");
            }

            buffer.append(" >");
         }
      }

      private DefaultContext context = null;
      private final StringBuffer buffer = new StringBuffer();
      private final StringBuffer cdatabuf = new StringBuffer();
      private final ArrayList<RepletRequest> list = new ArrayList<>();
   }

   public static final class Reference extends SingletonManager.Reference<RepletRegistry> {
      @Override
      public RepletRegistry get(Object ... parameters) {
         ResourceCache<String, RepletRegistry> registryCache = getRegistryCache();
         try {
            return registryCache.get((String) parameters[0]);
         }
         catch(Exception e) {
            LOG.error("Failed to create replet registry: " + parameters[0], e);
            return null;
         }
      }

      @Override
      public void dispose() {
      }
   }

   private boolean noMyreports = false; // true to disable My Reports
   private String orgID;
   // folder names -> folder name
   protected Hashtable<String, String> folders = new Hashtable<>();
   // folder name -> a context of a folder
   private final Hashtable<String, FolderContext> foldercontextmap = new Hashtable<>();
   protected final Vector<WeakReference<PropertyChangeListener>> listeners = new Vector<>();
   protected DataChangeListenerManager dmgr = new DataChangeListenerManager();
   protected long date = -2L; // last modified
   protected boolean loaded;

   private static final String GLOBAL_REPOSITORY = "__ADMIN__";

   private static final Logger LOG = LoggerFactory.getLogger(RepletRegistry.class);

   private static final ReadWriteLock REGISTRY_CACHE_LOCK = new ReentrantReadWriteLock();
   private static final String REGISTRY_CACHE_KEY =
      RepletRegistry.class.getName() + ".registryCache";
   private static Vector<WeakReference<PropertyChangeListener>> globalListeners = new Vector<>();
}
