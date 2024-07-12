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
package inetsoft.report;

import inetsoft.report.lib.ScriptEntry;
import inetsoft.report.lib.logical.*;
import inetsoft.report.lib.physical.*;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.BlobStorage;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class manages the bean, table style, and script library.
 * It is a singleton class.
 * The library is created inside the designer. Any beans or table styles
 * used in a report are automatically handled by the report engine. The
 * library is only of interest to a program if it needs to manipulate the
 * library programmatically instead of through the designer.
 *
 * @version 7.0
 * @author InetSoft Technology Corp
 */
@SuppressWarnings("WeakerAccess")
@SingletonManager.Singleton(LibManager.Reference.class)
public class LibManager implements AutoCloseable {
   /**
    * Action event ID if style is removed.
    */
   public static final int STYLE_REMOVED = 0x10;
   /**
    * Action event ID if style is added.
    */
   public static final int STYLE_ADDED = 0x20;
   /**
    * Action event ID if style is modified.
    */
   public static final int STYLE_MODIFIED = 0x40;
   /**
    * Action event ID if script is removed.
    */
   public static final int SCRIPT_REMOVED = 0x100;
   /**
    * Action event ID if script is added.
    */
   public static final int SCRIPT_ADDED = 0x200;
   /**
    * Action event ID if script is modified.
    */
   public static final int SCRIPT_MODIFIED = 0x400;
   /**
    * Action event ID if script is reloaded.
    */
   public static final int SCRIPT_RELOADED = 0x800;

   /**
    * Table style separator.
    */
   public static final String SEPARATOR = "~";
   /**
    * The user define style .
    */
   public static final String USER_DEFINE = "User Defined";

   public static final String PREFIX_SCRIPT = "script/";
   public static final String PREFIX_STYLE = "style/";
   public static final String COMMENT_SUFFIX = ".comment";

   /**
    * Constructor.
    */
   protected LibManager(LibrarySecurity librarySecurity) {
      final LogicalLibraryFactory factory = new LogicalLibraryFactory(librarySecurity);
      scripts = factory.createScriptLogicalLibrary();
      styles = factory.createTableStyleLogicalLibrary();
      styleFolders = factory.createTableStyleFolderLogicalLibrary();
      this.storages = new ConcurrentHashMap<>();
      getStorage();
      this.debouncer = new DefaultDebouncer<>();
   }

   private BlobStorage<Metadata> getStorage() {
      String storeID = OrganizationManager.getInstance().getCurrentOrgID() + "__" + "library";

      if(storages.containsKey(storeID)) {
         return storages.get(storeID);
      }
      else {
         BlobStorage<Metadata> storage = SingletonManager.getInstance(BlobStorage.class, storeID, false);
         storages.put(storeID, storage);

         try {
            storage.addListener(changeListener);
            loadLibrary(storage);
         }
         catch(Exception e) {
            LOG.error("Failed to initialize library", e);
         }

         return storage;
      }
   }

   /**
    * Gets the shared instance of the library manager.
    *
    * @return the library manager.
    */
   public static LibManager getManager() {
      return SingletonManager.getInstance(LibManager.class);
   }

   /**
    * Restarts the library manager.
    */
   public static void restart() {
      clear();
      getManager();
   }

   /**
    * Clears the cached library manager.
    */
   public static void clear() {
      SingletonManager.reset(LibManager.class);
   }

   /**
    * Initialize this library manager.
    *
    * @param event true if should fire event when resource changes.
    */
   protected synchronized void init(boolean event) {
      try {
         setInitializing(true);
      }
      catch(Exception e) {
         LOG.error("Failed to initialize library", e);
      }
      finally {
         setInitializing(false);
      }
   }

   /**
    * Remove all elements contained in the library manager.
    */
   public synchronized void removeAllElements() {
      getLogicalLibraries().forEach(LogicalLibrary::clear);
   }

   /**
    * Get last modified timestamp.
    *
    * @return last modified timestamp.
    */
   public long lastModified() {
      return ts;
   }

   /**
    * Refresh lib manager.
    *
    * @param event true if should fire event when resource changes.
    */
   public void refresh(boolean event) {
      // do-nothing
   }

   /**
    * Tear down lib manager.
    */
   protected synchronized void tearDown() {
      try {
         for(BlobStorage<Metadata> storage : storages.values()) {
            storage.close();
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to close blob storage", e);
      }

      closed = true;
   }

   @Override
   public void close() throws Exception {
      tearDown();
   }

   /**
    * Do a heartbeat to synchronize data.
    */
   protected void heartbeat() {
      // do nothing
   }

   /**
   /**
    * Finds out whether the script name is part of Audit Scripts
    */
   public boolean isAuditScript(String name) {
      return scripts.isAudit(name);
   }

   private synchronized void loadLibrary(BlobStorage<Metadata> storage) throws Exception {
      PhysicalLibrary library = new StorageLibrary(storage);
      loadLibrary(library, true, false, true);
   }

   /**
    * Loads a library.
    *
    * @param library   the library to load.
    * @param overwrite <tt>true</tt> to overwrite any existing entries in this
    *                  library.
    * @param audit     <tt>true</tt> to avoid loading audit objects into repository
    * @param init      {@code true} if initializing the library.
    *
    * @throws IOException if an I/O error occurs.
    */
   protected synchronized void loadLibrary(PhysicalLibrary library, boolean overwrite,
                                           boolean audit, boolean init)
      throws IOException {
      LoadOptions options = LoadOptions.builder()
         .overwrite(overwrite)
         .audit(audit)
         .init(init)
         .build();

      loadVersion(library);
      library.close();

      // load styles.
      styles.load(library, options);
      library.close();

      options = LoadOptions.builder().from(options)
         .requiresUserDefinedStyleFolder(styles.requiresUserDefinedStyleFolder())
         .build();

      styleFolders.load(library, options);
      library.close();

      // load script functions
      scripts.load(library, options);
      library.close();
   }

   /**
    * Get next available table style id.
    */
   public String getNextStyleID(String name) {
      return styles.getNextStyleID(name);
   }

   /**
    * Load a library file and add the version to the library.
    *
    * @param library the library to load.
    */
   private void loadVersion(PhysicalLibrary library) throws IOException {
      Iterator<PhysicalLibraryEntry> iter = library.getEntries();

      while(iter.hasNext()) {
         PhysicalLibraryEntry entry = iter.next();
         String name = entry.getPath();

         // check for version file
         if(name.startsWith("version")) {
            break;
         }
      }
   }

   /**
    * Rename a table style.
    * @param oldName old name of the table style.
    * @param newName new name for the table style.
    */
   public void renameTableStyle(String oldName, String newName, String oid) {
      final String newId = styles.rename(oldName, newName, oid);
      RenameInfo rinfo = new RenameInfo(oldName, newName, RenameInfo.TABLE_STYLE);
      rinfo.setUpdateStorage(true);
      RenameTransformHandler.getTransformHandler().addTransformTask(rinfo);

      if(newId != null) {
         fireActionEvent(newId, STYLE_MODIFIED);
      }
   }

   /**
    * Get all the script function names.
    * @return all the script function names.
    */
   public Enumeration<String> getScripts() {
      return scripts.toSecureEnumeration();
   }

   /**
    * Find a function with the same name by ignoring the case.
    */
   public String findScriptName(String name) {
      return scripts.caseInsensitiveFindName(name, true);
   }

   /**
    * Get a script function by its name.
    * @param name the specified script function name.
    * @return corresponding script function.
    */
   public String getScript(String name) {
      return scripts.getFunction(name);
   }

   /**
    * Set a new script function or replace an existing script function.
    * @param name the specified script function name.
    * @param func the specified script function.
    */
   public void setScript(String name, String func) {
      setScript(name, ScriptEntry.builder()
         .from(name, func)
         .build());
   }

   public void setScript(String name, ScriptEntry script) {
      final int id = scripts.put(name, script);
      fireActionEvent(name, id);
   }

   /**
    * Rename a script function.
    * @param oldName old name of the script function.
    * @param newName new name of the script function.
    */
   public void renameScript(String oldName, String newName) {
      if(scripts.rename(oldName, newName)) {
         RenameInfo rinfo = new RenameInfo(oldName, newName, RenameInfo.SCRIPT_FUNCTION);
         rinfo.setUpdateStorage(true);
         RenameTransformHandler.getTransformHandler().addTransformTask(rinfo);
         fireActionEvent(newName, SCRIPT_MODIFIED);
         fireActionEvent(oldName, SCRIPT_REMOVED);
      }
   }

   /**
    * Remove a script function from the library.
    * @param name the specified script function name.
    */
   public void removeScript(String name) {
      scripts.remove(name);
      fireActionEvent(name, SCRIPT_REMOVED);
   }

   /**
    * Get library entry.
    * @param name the library entry name
    */
   public LogicalLibraryEntry<?> getLogicalLibraryEntry(String name) {
      return scripts.getLogicalLibraryEntry(name);
   }

   /**
    * Get comments of a script function.
    * @param name the specified script function name.
    */
   public String getScriptComment(String name) {
      return scripts.getComment(name);
   }

   /**
    * Set comments of a script function.
    * @param name the specified script function name.
    * @param comment the specified comments.
    */
   public void setScriptComment(String name, String comment) {
      scripts.putComment(name, comment);
   }

   public void setScriptCommentProperties(String name, Properties comment, boolean isImport) {
      scripts.putCommentProperties(name, comment, isImport);
   }

   /**
    * Get a user defined function's signature by its name.
    * @param name the specified script function name.
    * @return corresponding script function's signature.
    */
   public String getUserSignature(String name) {
      return scripts.getSignature(name);
   }

   /**
    * Check if contains a folder.
    * @param folder the specified folder name.
    * @return <tt>true</tt> if contains, <tt>false</tt> otherwise.
    */
   public boolean containsFolder(String folder) {
      return styleFolders.contains(folder);
   }

   /**
    * Finds out whether the folder name is part of Audit table style folder.
    */
   public boolean isAuditStyleFolder(String folder) {
      return styleFolders.isAudit(folder);
   }

   /**
    * Add a table style folder.
    * @param folder the specified folder name.
    */
   public void addTableStyleFolder(String folder) {
      styleFolders.add(folder);
      fireActionEvent(folder, STYLE_MODIFIED);
   }

   /**
    * Remove a table style folder.
    * @param folder the folder name to be removed.
    */
   public void removeTableStyleFolder(String folder) {
      styleFolders.remove(folder);
      fireActionEvent(folder, STYLE_MODIFIED);
   }

   /**
    * Rename a table style folder.
    * @param oldName old name of the table style folder.
    * @param newName new name for the table style folder.
    */
   public void renameTableStyleFolder(String oldName, String newName) {
      styleFolders.rename(oldName, newName);
      fireActionEvent(newName, STYLE_MODIFIED);
   }

   /**
    * Get table style folders.
    *
    * @param folder parent folder.
    *
    * @return sub folders of the specified parent folder.
    */
   public String[] getTableStyleFolders(String folder) {
      return getTableStyleFolders(folder, false);
   }

   /**
    * Get table style folders.
    *
    * @param folder      parent folder.
    * @param filterAudit whether or not to filter out audit table style folders.
    *
    * @return sub folders of the specified parent folder.
    */
   public String[] getTableStyleFolders(String folder, boolean filterAudit) {
      return styleFolders.getTableStyleFolders(folder, filterAudit).toArray(new String[0]);
   }

   /**
    * Get table styles under a folder.
    *
    * @param folder parent folder.
    *
    * @return table styles under the specified parent folder.
    */
   public XTableStyle[] getTableStyles(String folder) {
      return getTableStyles(folder, false);
   }

   /**
    * Get table styles under a folder.
    *
    * @param folder      parent folder.
    * @param filterAudit whether or not to filter out audit table styles.
    *
    * @return table styles under the specified parent folder.
    */
   public XTableStyle[] getTableStyles(String folder, boolean filterAudit) {
      return styles.getTableStyles(folder, filterAudit).toArray(new XTableStyle[0]);
   }

   /**
    * Get all the table style names.
    * @return all the table style names.
    */
   public Enumeration<String> getTableStyles() {
      return styles.toSecureEnumeration();
   }

   /**
    * Get a table style by its name.
    * @param name the specified table style id or name.
    * @return corresponding table style object.
    */
   public XTableStyle getTableStyle(String name) {
      XTableStyle style = styles.get(name);
      // id is used internally, but we allow table style to be set by script
      // so we should support name too.
      return style == null ? styles.getByName(name) : style;
   }

   /**
    * Set a new table style or replace an existing table style.
    * @param name the specified table style name.
    * @param style the specified table style.
    */
   public void setTableStyle(String name, XTableStyle style) {
      setTableStyle(name, style, true);
   }

   /**
    * Set a new table style or replace an existing table style.
    * @param name the specified table style name.
    * @param style the specified table style.
    * @param checkParent <tt>true</tt> to check parent permission.
    */
   public void setTableStyle(String name, XTableStyle style, boolean checkParent) {
      final int id = styles.put(name, style, checkParent);
      fireActionEvent(name, id);
   }

   /**
    * Remove a table style from the library.
    * @param name the specified table style name.
    */
   public void removeTableStyle(String name) {
      styles.remove(name);
      fireActionEvent(name, STYLE_REMOVED);
   }

   /**
    * Save the library contents to the library file.
    */
   public synchronized void save() throws Exception {
      BlobStorage<Metadata> storage = getStorage();
      storage.removeListener(changeListener);

      try {
         StorageLibrary library = new StorageLibrary(storage);
         save(library);
      }
      finally {
         ts = storage.getLastModified().toEpochMilli();
         storage.addListener(changeListener);
      }
   }

   /**
    * Saves this library.
    *
    * @param library the library storage.
    *
    * @throws IOException if an I/O error occurs.
    */
   private synchronized void save(PhysicalLibrary library) throws IOException {
      final SaveOptions options = SaveOptions.builder()
         .filterAudit(false)
         .forceSave(false)
         .build();

      save(library, options);
   }

   /**
    * Saves this library.
    *
    * @param library the library storage.
    * @param options the save options.
    *
    * @throws IOException if an I/O error occurs.
    */
   private synchronized void save(PhysicalLibrary library, SaveOptions options) throws IOException {
      try {
         // save version
         PhysicalLibraryEntry entry = library.createEntry(CURR_VERSION, null);

         try(OutputStream output = entry.getOutputStream()) {
            output.write(0);
         }

         scripts.save(library, options);
         styles.save(library, options);
         styleFolders.save(library, options);
      }
      finally {
         library.close();
      }
   }

   /**
    * Add a listener to be notified when the library has been changed.
    * @param listener the specified listener to add.
    */
   public void addActionListener(ActionListener listener) {
      listeners.add(listener);
   }

   /**
    * Remove a listener.
    * @param listener the specified listener to remove
    */
   public void removeActionListener(ActionListener listener) {
      listeners.remove(listener);
   }

   /**
    * Add a refresh listener that will be notified if the datasource registry
    * has changed.
    * @param listener the specified refresh listener.
    */
   public void addRefreshedListener(PropertyChangeListener listener) {
      refreshedListeners.add(listener);
   }

   /**
    * Remove a refresh listener.
    * @param listener the specified refresh listener.
    */
   public void removeRefreshedListener(PropertyChangeListener listener) {
      refreshedListeners.remove(listener);
   }

   /**
    * Fire an action event.
    * @param name the changed object name.
    * @param actionID the event actionID defined in this class.
    */
   protected void fireActionEvent(String name, int actionID) {
      ActionEvent evt = new ActionEvent(this, actionID, name);
      List<ActionListener> currentListeners;

      synchronized(this) {
         currentListeners = new ArrayList<>(this.listeners);
      }

      for(ActionListener listener : currentListeners) {
         try {
            if(listener != null) {
               listener.actionPerformed(evt);
            }
         }
         catch(Exception ex) {
            LOG.warn("Failed to process action event", ex);
         }
      }
   }

   /**
    * Fire event.
    * @param ots the specified last modified timestamp.
    */
   protected void fireEvent(long ots) {
      if(ots != 0 && ts != 0 && ots != ts) {
         for(PropertyChangeListener listener : refreshedListeners) {
            listener.propertyChange(new PropertyChangeEvent(
               LibManager.this, "LibManager", null, null));
         }
      }
   }

   private final BlobStorage.Listener<Metadata> changeListener = new BlobStorage.Listener<Metadata>() {
      @Override
      public void blobAdded(BlobStorage.Event<Metadata> event) {
         fireEvent(event.getNewValue().getLastModified().toEpochMilli());
      }

      @Override
      public void blobUpdated(BlobStorage.Event<Metadata> event) {
         fireEvent(event.getNewValue().getLastModified().toEpochMilli());
      }

      @Override
      public void blobRemoved(BlobStorage.Event<Metadata> event) {
         fireEvent(System.currentTimeMillis());
      }

      private void fireEvent(long timestamp) {
         debouncer.debounce("change", 1L, TimeUnit.SECONDS, () -> {
            try {
               init(true);
               fireActionEvent("", SCRIPT_RELOADED);
               LibManager.this.fireEvent(timestamp);
            }
            catch(Exception e) {
               LOG.error("Failed to reload library file", e);
            }
         });
      }
   };

   /**
    * Get the file input stream from the path. If the path does not exist, null
    * will be returned.
    * @param path the path of a file.
    */
   public InputStream getFileInputStream(String path) {
      if(path.startsWith("style\\")) {
         String name = path.substring(6);
         XTableStyle style = styles.get(name);

         if(style == null) {
            return null;
         }

         ByteArrayOutputStream output = new ByteArrayOutputStream();
         style.export(output);
         return new ByteArrayInputStream(output.toByteArray());
      }

      return null;
   }

   /**
    * Check if is older version compare to current version.
    */
   @SuppressWarnings("unused")
   private static boolean isOlderVersion(String version) {
      // @temp jasons, keeping this method until we've tested that there are no
      //       issues caused by the change.
      if(version == null || version.equals("")) {
         return true;
      }

      try {
         float f1 = Float.parseFloat(version.substring(7));
         float f2 = Float.parseFloat(CURR_VERSION.substring(7));

         return f1 < f2;
      }
      catch(Exception e) {
         LOG.warn("Invalid library version: " + version, e);
      }

      return false;
   }

   /**
    * Checks to see if this LibManager is in the process of loading assets
    * @return <tt>true</tt> if initializing; <tt>false</tt> otherwise.
    */
   public boolean isInitializing() {
      return initializing;
   }

   /**
    * Sets whether this LibManager is in the process of loading assets
    * @param initializing boolean value indicating initialization status
    */
   protected void setInitializing(boolean initializing) {
      this.initializing = initializing;
   }

   /**
    * Checks to see if this LibManager was modified, but the storage is not updated.
    * @return <tt>true</tt> if dirty; <tt>false</tt> otherwise.
    */
   public boolean isDirty() {
      return getLogicalLibraries().stream().anyMatch(LogicalLibrary::hasTransactions);
   }

   private List<LogicalLibrary<?>> getLogicalLibraries() {
      return Arrays.asList(scripts, styles, styleFolders);
   }

   public static final class Reference extends SingletonManager.Reference<LibManager> {
      @Override
      public LibManager get(Object ... parameters) {
         boolean doInit = false;
         lock.lock();

         try {
            if(manager == null) {
               doInit = true;
               manager = new LibManager(new NoopLibrarySecurity());
            }
            else {
               // Init storage for org if it isn't initialized yet
               manager.getStorage();
            }
         }
         finally {
            lock.unlock();
         }

         if(doInit) {
            manager.init(true);
         }

         return manager;
      }

      @Override
      public void dispose() {
         lock.lock();

         try {
            if(manager != null) {
               manager.tearDown();
               manager = null;
            }
         }
         finally {
            lock.unlock();
         }
      }

      private final Lock lock = new ReentrantLock();
      private LibManager manager;
   }

   // current library version
   private static final String CURR_VERSION = "version11.3";

   protected volatile long ts; // last modified timestamp

   private boolean initializing = false;

   private final ScriptLogicalLibrary scripts;
   private final TableStyleLogicalLibrary styles;
   private final TableStyleFolderLogicalLibrary styleFolders;

   private final List<ActionListener> listeners = Collections.synchronizedList(new ArrayList<>());
   private final List<PropertyChangeListener> refreshedListeners = new ArrayList<>();
   private volatile boolean closed = false;

   private final ConcurrentHashMap<String, BlobStorage<Metadata>> storages;
   private final Debouncer<String> debouncer;

   private static final Logger LOG = LoggerFactory.getLogger(LibManager.class);

   public static final class Metadata implements Serializable {
      public String getPath() {
         return path;
      }

      public void setPath(String path) {
         this.path = path;
      }

      public boolean isDirectory() {
         return directory;
      }

      public void setDirectory(boolean directory) {
         this.directory = directory;
      }

      public String getComment() {
         return comment;
      }

      public void setComment(String comment) {
         this.comment = comment;
      }

      public Properties getCommentProperties() {
         return commentProperties;
      }

      public void setCommentProperties(Properties commentProperties) {
         this.commentProperties = commentProperties;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         Metadata metadata = (Metadata) o;
         return directory == metadata.directory &&
            Objects.equals(path, metadata.path) &&
            Objects.equals(comment, metadata.comment) &&
            Objects.equals(commentProperties, metadata.commentProperties);
      }

      @Override
      public int hashCode() {
         return Objects.hash(path, directory, comment, commentProperties);
      }

      @Override
      public String toString() {
         return "Metadata{" +
            "path='" + path + '\'' +
            ", directory=" + directory +
            ", comment='" + comment + '\'' +
            ", commentProperties=" + commentProperties +
            '}';
      }

      private String path;
      private boolean directory;
      private String comment;
      private Properties commentProperties;
   }
}
