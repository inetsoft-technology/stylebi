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

import inetsoft.report.PropertyChangeEvent;
import inetsoft.report.internal.Util;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.uql.XPrincipal;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.beans.PropertyChangeListener;
import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

/**
 * RepletRegistry handles registration of replets. It loads the registration
 * file from the location specified in the 'replet.repository.file' property.
 *
 * @author InetSoft Technology Corp
 * @version 7.0
 */
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
    * Rename folder event.
    */
   static final String RENAME_FOLDER_EVENT = "rename_folder";
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

      PropertyChangeEvent evt = new PropertyChangeEvent(Util.getOrgEventSourceID(src, orgID), name, oval, nval);

      fireEventToListeners(listeners, evt, name);

      synchronized(this.listeners) {
         listeners = new Vector<>(getGlobalListeners());
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
                  LOG.debug("Repository XML file not found: {}", repfile);
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
      if(folder == null || folder.isEmpty()) {
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
    */
   public String changeFolder(String oldFolderName, String newFolderName) {
      return changeFolder(oldFolderName, newFolderName, null);
   }

   /**
    * Change folder name.
    *
    * @param oldFolderName old folder name.
    * @param newFolderName new folder name.
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

      if(SUtil.isDefaultVSGloballyVisible(principal) && principal != null &&
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
            renameFolder(oname, nname, oname.equals(oldFolderName));
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
   private synchronized void renameFolder(String ofolder, String nfolder, boolean transaction) {
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

      if(context == null) {
         context = new FolderContext(name);
         getFolderContextmap().put(name, context);
      }

      context.addFavoritesUser(principal);
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
    * Whether registry file exist.
    */
   protected boolean registryFileExist() {
      return DataSpace.getDataSpace().exists(null, getRegistryPath());
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

   public synchronized void shutdown() {
      DataSpace space = DataSpace.getDataSpace();
      dmgr.removeChangeListener(space, getRegistryDir(), getRegistryFileName(), changeListener);
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

   synchronized Hashtable<String, String> getFolderMap() {
      if(!folders.containsKey(Tool.MY_DASHBOARD)) {
         folders.put(Tool.MY_DASHBOARD, Tool.MY_DASHBOARD);
      }

      if(!folders.containsKey("/")) {
         folders.put("/", "/");
      }

      return folders;
   }

   synchronized Hashtable<String, FolderContext> getFolderContextmap() {
     if(!foldercontextmap.containsKey(Tool.MY_DASHBOARD)) {
        foldercontextmap.put(Tool.MY_DASHBOARD, new FolderContext(Tool.MY_DASHBOARD));
     }

      return foldercontextmap;
   }

   static Vector<WeakReference<PropertyChangeListener>> getGlobalListeners() {
      return ConfigurationContext.getContext().computeIfAbsent(GLOBAL_LISTENERS, k -> new Vector<>());
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
    * User replet registry.
    */
   final static class UserRepletRegistry extends RepletRegistry {
      UserRepletRegistry(String user) throws Exception {
         super(user == null ? null : IdentityID.getIdentityIDFromKey(user).getOrgID());
         this.user = user;
         init0();
      }

      @Override
      protected void init() {
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

   class XMLHandler extends DefaultHandler {
      @Override
      public void characters(char[] ch, int start, int length) {
         String str = new String(ch, start, length);

         if(context != null && !"\n".equals(str)) {
            if(!buffer.isEmpty()) {
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
            if(!isValidRepletNode(attrs)) {
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
         else if("Request".equals(ename) || !buffer.isEmpty()) {
            put(ename, attrs, true);
         }
      }

      @Override
      public void endElement(String uri, String localName, String name) {
         if(!cdatabuf.isEmpty()) {
            buffer.append("<![CDATA[").append(cdatabuf).append("]]>");
            cdatabuf.setLength(0);
         }

         if(context != null && ("Request".equals(name) || !buffer.isEmpty())) {
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

   private boolean noMyreports = false; // true to disable My Reports
   private final String orgID;
   // folder names -> folder name
   protected Hashtable<String, String> folders = new Hashtable<>();
   // folder name -> a context of a folder
   private final Hashtable<String, FolderContext> foldercontextmap = new Hashtable<>();
   protected final Vector<WeakReference<PropertyChangeListener>> listeners = new Vector<>();
   protected DataChangeListenerManager dmgr = new DataChangeListenerManager();
   protected long date = -2L; // last modified
   protected boolean loaded;

   static final String GLOBAL_LISTENERS = RepletRegistry.class.getName() + ".globalListeners";
   private static final Logger LOG = LoggerFactory.getLogger(RepletRegistry.class);
}
