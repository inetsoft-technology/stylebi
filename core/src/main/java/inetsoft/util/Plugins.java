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
package inetsoft.util;

import com.github.zafarkhaja.semver.Version;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.sree.security.db.DatabaseAuthenticationProvider;
import inetsoft.storage.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.log.LogManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationEventPublisher;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class used to access extensions defined in plugins.
 */
public class Plugins implements BlobStorage.Listener<Plugin.Descriptor>, AutoCloseable {
   /**
    * Creates a new instance of <tt>Plugins</tt> that fetches its storage from the given manager,
    * and fetches it again when the held instance is evicted from the manager and closed.
    */
   public Plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                  ApplicationEventPublisher eventPublisher)
   {
      this(blobStorageManager.getStorage(STORAGE_ID, true), blobStorageManager, cluster,
           eventPublisher);
   }

   /**
    * Creates a new instance of <tt>Plugins</tt>. A replacement for an evicted storage is fetched
    * from {@link BlobStorageManager#getInstance()}.
    */
   public Plugins(BlobStorage<Plugin.Descriptor> blobStorage, Cluster cluster, ApplicationEventPublisher eventPublisher) {
      this(blobStorage, null, cluster, eventPublisher);
   }

   private Plugins(BlobStorage<Plugin.Descriptor> blobStorage,
                   BlobStorageManager blobStorageManager, Cluster cluster,
                   ApplicationEventPublisher eventPublisher)
   {
      this.blobStorage = blobStorage;
      this.blobStorageManager = blobStorageManager;
      this.eventPublisher = eventPublisher;
      FileSystemService fileSystemService = FileSystemService.getInstance();

      InetsoftConfig config = InetsoftConfig.getInstance();

      if(StringUtils.isEmpty(config.getPluginDirectory())) {
         pluginDirectory = fileSystemService
            .getFile(ConfigurationContext.getContext().getHome(), "plugins")
            .getAbsoluteFile();
      }
      else {
         pluginDirectory = fileSystemService.getFile(config.getPluginDirectory());
      }

      if(!pluginDirectory.isDirectory() && !pluginDirectory.mkdirs()) {
         LOG.warn("Failed to create plugin directory: {}", pluginDirectory);
      }

      this.plugins = new ConcurrentHashMap<>();
      this.blobChangeLock = cluster.getLock(BLOB_CHANGE_LOCK);
   }

   @PostConstruct
   public void initBean() {
      if(!initialized) {
         synchronized(this) {
            if(!initialized) {
               init();
               initialized = true;
            }
         }
      }
   }

   // must be called outside of constructor to avoid infinite recursion
   private void init() {
      BlobStorage<Plugin.Descriptor> storage = getStorage();
      blobChangeLock.lock();

      try {
         storage.stream()
            .sorted(this::comparePlugins)
            .filter(p -> {
               try {
                  unzipPlugin(storage, p.getMetadata(), p.getLastModified().toEpochMilli());
                  return true;
               }
               catch(Exception e) {
                  LOG.error("Failed to unzip plugin {}: {}", p.getMetadata().getId(), e.getMessage());
                  return false;
               }
            })
            .forEach(p -> loadPlugin(p.getMetadata()));
         validatePlugins();
      }
      finally {
         blobChangeLock.unlock();
      }

      storage.addListener(this);
   }

   /**
    * Gets the live plugin storage. The storage manager closes the instance held here when it
    * evicts it, which removes the map listener this class gets its change events from. A closed
    * instance is therefore replaced, the listener is added to the replacement, and the loaded
    * plugins are synchronized with the store, because the installs and uninstalls made on other
    * nodes while detached raised no event here (Bug #78253).
    * <p>
    * The synchronization is run on the resync executor, not on the calling thread: it takes the
    * cluster-wide {@link #blobChangeLock} and publishes the plugin events, whose listeners take
    * their own locks (e.g. the driver services lock in {@code Drivers}), and the read methods are
    * called while holding such locks (e.g. {@code Drivers.initDriverServices()} or a plugin class
    * loader). The write methods use {@link #getSyncedStorage()} instead.
    */
   private BlobStorage<Plugin.Descriptor> getStorage() {
      BlobStorage<Plugin.Descriptor> storage = blobStorage;

      if(!storage.isClosed() || closed || Boolean.TRUE.equals(RESYNCING.get())) {
         return storage;
      }

      BlobStorage<Plugin.Descriptor> fresh = reattachStorage();

      if(resyncPending.get() && !closed) {
         try {
            resyncExecutor.execute(this::resyncIfPending);
         }
         catch(RejectedExecutionException e) {
            LOG.debug("The plugin resync executor is shut down", e);
         }
      }

      return fresh;
   }

   /**
    * Gets the live plugin storage and, after a re-attach, synchronizes the loaded plugins with
    * the store on the calling thread before returning. Only for the install and uninstall
    * methods, which are not called while holding another lock and which check the loaded plugins.
    */
   private BlobStorage<Plugin.Descriptor> getSyncedStorage() {
      getStorage();

      if(!Boolean.TRUE.equals(RESYNCING.get())) {
         resyncIfPending();
      }

      return blobStorage;
   }

   private void resyncIfPending() {
      if(!closed && resyncPending.compareAndSet(true, false)) {
         resync(blobStorage);
      }
   }

   private BlobStorage<Plugin.Descriptor> reattachStorage() {
      BlobStorage<Plugin.Descriptor> old;
      BlobStorage<Plugin.Descriptor> fresh;

      synchronized(storageMonitor) {
         old = blobStorage;

         if(!old.isClosed() || closed) {
            return old;
         }

         try {
            BlobStorageManager manager = blobStorageManager != null ?
               blobStorageManager : BlobStorageManager.getInstance();
            fresh = manager.getStorage(STORAGE_ID, true);
         }
         catch(Exception e) {
            LOG.warn("Failed to fetch a replacement for the closed plugin storage, continuing " +
                        "with the closed instance until a later access succeeds", e);
            return old;
         }

         // add the listener before the resync reads the store, so that a change made after the
         // read still raises an event here
         fresh.addListener(this);
         blobStorage = fresh;

         // before init() runs, it loads the plugins from the replacement itself
         if(initialized) {
            resyncPending.set(true);
         }
      }

      // the manager drops a closed instance without closing it, so its event thread is stopped
      // here, as DataSpace does
      try {
         old.removeListener(this);
         old.close();
      }
      catch(Exception e) {
         LOG.debug("Failed to close the stale plugin storage", e);
      }

      return fresh;
   }

   /**
    * Loads the plugins added to the store and unloads the plugins removed from it while the held
    * storage instance was detached, and reloads a plugin whose stored version changed.
    */
   private void resync(BlobStorage<Plugin.Descriptor> storage) {
      RESYNCING.set(Boolean.TRUE);
      blobChangeLock.lock();

      try {
         List<Blob<Plugin.Descriptor>> stored = storage.stream()
            .sorted(this::comparePlugins)
            .toList();

         // a closed instance enumerates nothing, which must not unload every plugin
         if(storage.isClosed()) {
            LOG.warn("The plugin storage was closed while it was read, the loaded plugins " +
                        "are synchronized on its next access");
            return;
         }

         Set<String> storedIds = new HashSet<>();
         stored.forEach(b -> storedIds.add(b.getMetadata().getId()));

         for(String id : new ArrayList<>(plugins.keySet())) {
            if(!storedIds.contains(id)) {
               removeLoadedPlugin(id);
            }
         }

         for(Blob<Plugin.Descriptor> blob : stored) {
            Plugin.Descriptor descriptor = blob.getMetadata();
            Plugin existing = plugins.get(descriptor.getId());

            if(existing != null && Objects.equals(existing.getVersion(), descriptor.getVersion())) {
               continue;
            }

            try {
               if(existing != null) {
                  removeLoadedPlugin(descriptor.getId());
               }

               addLoadedPlugin(storage, descriptor, blob.getLastModified().toEpochMilli());
            }
            catch(Exception e) {
               LOG.warn("Failed to load plugin {}", descriptor.getId(), e);
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to synchronize the plugins with the plugin storage", e);
      }
      finally {
         blobChangeLock.unlock();
         RESYNCING.remove();
      }
   }

   private int comparePlugins(Blob<Plugin.Descriptor> a, Blob<Plugin.Descriptor> b) {
      if(a.getMetadata().getMergeInto() != null && b.getMetadata().getMergeInto() == null) {
         return 1;
      }

      if(a.getMetadata().getMergeInto() == null && b.getMetadata().getMergeInto() != null) {
         return -1;
      }

      return a.getMetadata().getId().compareTo(b.getMetadata().getId());
   }

   // called from agile
   public void validatePlugins() {
      Iterator<Plugin> values = plugins.values().iterator();

      while(values.hasNext()) {
         Plugin plugin = values.next();
         Plugin.Descriptor descriptor = plugin.getDescriptor();

         if(!isPluginCompatible(descriptor, descriptor.getFile())) {
            values.remove();

            try {
               plugin.getClassLoader().close();
            }
            catch(Exception e) {
               LOG.debug("Failed to close plugin class loader", e);
            }
         }
         else if(descriptor.isPreload()) {
            plugin.preload();
         }
      }
   }

   /**
    * Gets the singleton instance of <tt>Plugins</tt>.
    *
    * @return the plugin manager instance.
    */
   public static Plugins getInstance() {
      Plugins plugins = ConfigurationContext.getContext().getSpringBean(Plugins.class);

      if(!plugins.initialized) {
         synchronized(plugins) {
            if(!plugins.initialized) {
               plugins.init();
               plugins.initialized = true;
            }
         }
      }

      return plugins;
   }

   /**
    * Gets the matching service instances.
    *
    * @param serviceInterface the service interface class.
    * @param id               the identifier of the providing plugin or <tt>null</tt> for
    *                         all plugins.
    *
    * @param <T> the service interface type.
    *
    * @return the matching service instances.
    */
   @SuppressWarnings("SameParameterValue")
   public <T> List<T> getServices(Class<T> serviceInterface, String id) {
      getStorage();
      List<T> result;

      if(id == null) {
         result = new ArrayList<>();

         for(Plugin plugin : plugins.values()) {
            result.addAll(plugin.getServices(serviceInterface));
         }
      }
      else {
         Plugin plugin = plugins.get(id);

         if(plugin == null) {
            result = Collections.emptyList();
         }
         else {
            result = plugin.getServices(serviceInterface);
         }
      }

      return result;
   }

   /**
    * Gets the matching service instance.
    *
    * @param serviceInterface the service interface class.
    * @param id               the identifier of the providing plugin or <tt>null</tt> for
    *                         all plugins.
    *
    * @param <T> the service interface type.
    *
    * @return the matching service instance.
    */
   public <T> T getService(Class<T> serviceInterface, String id) {
      getStorage();
      T result = null;

      if(id == null) {
         for(Plugin plugin : plugins.values()) {
            T service = plugin.getService(serviceInterface);

            if(service != null) {
               result = service;
               break;
            }
         }
      }
      else {
         Plugin plugin = plugins.get(id);

         if(plugin != null) {
            result = plugin.getService(serviceInterface);
         }
      }

      return result;
   }

   /**
    * Installs a plugin.
    *
    * @param input    the stream from which to read the plugin archive file to install.
    * @param fileName the desired plugin file name.
    * @param update   <tt>true</tt> to update an existing plugin if already installed with
    *                 an older version.
    *
    * @return <tt>true</tt> if installed.
    *
    * @throws IOException if an I/O error occurs.
    */
   public boolean installPlugin(InputStream input, String fileName, boolean update)
      throws IOException
   {
      File tempFile = FileSystemService.getInstance().getCacheTempFile("plugin", ".zip");
      assert tempFile != null;

      try {
         tempFile.deleteOnExit();

         try(OutputStream output = new FileOutputStream(tempFile)) {
            IOUtils.copy(input, output);
         }

         return installPlugin(tempFile, fileName, update);
      }
      finally {
         delete(tempFile);
      }
   }

   /**
    * Installs a plugin.
    *
    * @param file     the plugin archive file to install.
    * @param fileName the desired plugin file name.
    * @param update   <tt>true</tt> to update an existing plugin if already installed with
    *                 an older version.
    *
    * @return <tt>true</tt> if installed.
    *
    * @throws IOException if an I/O error occurs.
    */
   private boolean installPlugin(File file, String fileName, boolean update)
      throws IOException
   {
      boolean installed = false;
      boolean uninstall = false;

      Plugin.Descriptor descriptor = new Plugin.Descriptor(file);
      String pluginId = descriptor.getId();
      Principal principal = ThreadContext.getContextPrincipal();
      String pluginVersion = descriptor.getVersion();
      String actionName = ActionRecord.ACTION_NAME_CREATE;
      String objectType = ActionRecord.OBJECT_TYPE_PLUG;
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, descriptor.getName(),
                                                   objectType, actionTimestamp,
                                                   ActionRecord.ACTION_STATUS_FAILURE, null);

      if(pluginId == null) {
         throw new IllegalStateException(
            "The Plugin-Id attribute is missing from the plugin manifest: " +
            fileName);
      }

      if(pluginVersion == null) {
         throw new IllegalStateException(
            "The Plugin-Version attribute is missing from the plugin manifest: " +
            fileName);
      }

      // re-attach a closed storage first, so that the checks below see the plugins installed
      // and uninstalled on other nodes meanwhile and the commit below raises an event here
      getSyncedStorage();

      if(isPluginCompatible(descriptor, fileName)) {
         Plugin existing = plugins.get(pluginId);

         if(existing == null) {
            installed = true;
         }
         else if(update) {
            Version version = Version.parse(pluginVersion);
            Version existingVersion = Version.parse(existing.getVersion());

            if(version.isHigherThan(existingVersion)) {
               installed = true;
               uninstall = true;
            }
         }
         else {
            throw new IllegalStateException(
               Catalog.getCatalog().getString("em.drivers.uploadDriverDuplicate"));
         }
      }

      if(installed) {
         if(uninstall) {
            uninstallPlugin(pluginId);
         }

         try(InputStream input = new FileInputStream(file);
             BlobTransaction<Plugin.Descriptor> tx = getStorage().beginTransaction();
             OutputStream output = tx.newStream(pluginId, descriptor))
         {
            IOUtils.copy(input, output);
            tx.commit();
         }
         catch(Exception ex) {
            actionRecord = null;
            LOG.error("Failed to install plugin {}", pluginId, ex);
            installed = false;
         }
      }

      if(actionRecord != null) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         Audit.getInstance().auditAction(actionRecord, principal);
      }

      resetDBProviderConnection();

      return installed;
   }

   /**
    * Explode plugin zip file if necessary.
    *
    * @param descriptor   the plugin descriptor.
    * @param lastModified the last modified timestamp.
    */
   private void unzipPlugin(BlobStorage<Plugin.Descriptor> storage, Plugin.Descriptor descriptor,
                            long lastModified)
   {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      File folder = getDirectoryPlugin(fileSystemService, descriptor.getId());

      try {
         if(!folder.exists() || folder.lastModified() < lastModified) {
            // out-of-date, remove and unzip
            if(folder.isDirectory() && folder.lastModified() < lastModified) {
               FileUtils.deleteDirectory(folder);
            }

            if(!folder.isDirectory()) {
               Files.createDirectories(folder.toPath());

               try(ZipInputStream input = new ZipInputStream(storage.getInputStream(descriptor.getId()))) {
                  ZipEntry entry;

                  while((entry = input.getNextEntry()) != null) {
                     File entryFile = fileSystemService.getFile(folder, entry.getName());
                     File parent = entryFile.getParentFile();

                     if(!parent.isDirectory()) {
                        Files.createDirectories(parent.toPath());
                     }

                     if(entry.isDirectory()) {
                        Files.createDirectory(entryFile.toPath());
                     }
                     else {
                        Files.copy(input, entryFile.toPath());
                     }
                  }
               }
            }
         }
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to unzip plugin to local directory", e);
      }
   }

   private File getDirectoryPlugin(FileSystemService fileSystemService, String pluginName) {
      return fileSystemService.getFile(pluginDirectory, pluginName);
   }

   // load plugin from plugin folder
   private void loadPlugin(Plugin.Descriptor descriptor) {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      String id = descriptor.getId();
      String name = descriptor.getName();
      String version = descriptor.getVersion();
      String vendor = descriptor.getVendor();
//      boolean pluginClassloaderFirst = descriptor.isPluginClassloaderFirst();
      File folder = getDirectoryPlugin(fileSystemService, id);

      if(id == null) {
         throw new IllegalStateException(
            "The Plugin-Id attribute is missing from the plugin manifest: " +
               fileSystemService.getFile(folder, "classes/META-INF/MANIFEST.MF"));
      }

      if(version == null) {
         throw new IllegalStateException(
            "The Plugin-Version attribute is missing from the plugin manifest: " +
               fileSystemService.getFile(folder, "classes/META-INF/MANIFEST.MF"));
      }

      Plugin existingPlugin = plugins.get(id);

      if(existingPlugin != null) {
         Version newVersion = Version.parse(version);
         Version existingVersion = Version.parse(existingPlugin.getVersion());

         if(newVersion.isLowerThan(existingVersion)) {
            return;
         }
      }

      String[] requiredPlugins = new String[descriptor.getRequiredPlugins().length];

      for(int i = 0; i < requiredPlugins.length; i++) {
         String item = descriptor.getRequiredPlugins()[i];
         int index = item.indexOf(':');
         String requiredId = (index > 0) ? item.substring(0, index) : item;

         requiredPlugins[i] = requiredId;
      }

      try {
         Plugin plugin = new Plugin(
            id, name, vendor, version, false, folder, requiredPlugins, descriptor, this);
         plugins.put(id, plugin);
         LOG.info("Loaded plugin {}:{}", id, version);
      }
      catch(Exception pluginLoadException) {
         LOG.warn("Failed to load plugin {}:{}, Reason: {}",
                  id, version, pluginLoadException.getMessage());
      }
   }

   /**
    * Determines if a plugin is compatible with the current system.
    *
    * @param descriptor the plugin descriptor.
    * @param fileName   the plugin file or folder name.
    *
    * @return <tt>true</tt> if compatible; <tt>false</tt> otherwise.
    */
   private boolean isPluginCompatible(Plugin.Descriptor descriptor, String fileName) {
      boolean result = true;

      for(String item : descriptor.getRequiredApis()) {
         int index = item.indexOf(':');

         if(index < 0) {
            throw new IllegalStateException(
               "Invalid Plugin-Requires attribute in plugin manifest: " +
               item + " in " + fileName);
         }

         String requiredId = item.substring(0, index);
         String requiredVersion = item.substring(index + 1);

         ApiVersion apiVersion;

         try {
            apiVersion = ApiVersion.forId(requiredId);
         }
         catch(IllegalArgumentException e) {
            throw new IllegalStateException(
               "Invalid API or plugin ID in Plugin-Requires attribute of plugin " +
               "manifest: " + fileName);
         }

         if(!apiVersion.satisfies(requiredVersion)) {
            LOG.warn("Plugin version doesn't match: {} <> {}", requiredVersion, apiVersion);
            result = false;
            break;
         }
      }

      if(result) {
         for(String item : descriptor.getRequiredPlugins()) {
            int index = item.indexOf(':');
            String requiredId;
            String requiredVersion = null;

            if(index < 0) {
               requiredId = item;
            }
            else {
               requiredId = item.substring(0, index);
               requiredVersion = item.substring(index + 1);
            }

            Plugin requiredPlugin = getPlugin(requiredId);

            if(requiredPlugin == null) {
               result = false;
               LOG.warn("Required plugin missing: " + requiredId);
               break;
            }

            if(requiredVersion != null &&
               !Version.parse(requiredPlugin.getVersion()).satisfies(requiredVersion))
            {
               result = false;
               LOG.warn("Required plugin version mismatch: " + requiredVersion +
                           " <> " + requiredPlugin.getVersion());
               break;
            }
         }
      }

      if(!result && LogManager.getInstance().isInfoEnabled(LOG.getName())) {
         String id = descriptor.getId();
         String version = descriptor.getVersion();
         LOG.warn(
            "Plugin [" + id + ":" + version + "] is not compatible, requires: " +
            Arrays.toString(descriptor.getRequiredApis()) + ", " +
            Arrays.toString(descriptor.getRequiredPlugins()));
      }

      return result;
   }

   /**
    * Uninstalls a plugin.
    *
    * @param pluginId the identifier of the plugin to remove.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void uninstallPlugin(String pluginId) throws IOException {
      BlobStorage<Plugin.Descriptor> storage = getSyncedStorage();
      Plugin plugin = plugins.get(pluginId);

      if(plugin == null || plugin.isReadOnly()) {
         return;
      }

      plugins.remove(pluginId);
      storage.delete(pluginId);
      eventPublisher.publishEvent(new PluginRemovedEvent(this, pluginId));
      plugin.getClassLoader().close();
      resetDBProviderConnection();
      delete(plugin.getFolder());

      LOG.info("Removed plugin {}:{}", plugin.getId(), plugin.getVersion());
   }

   /**
    * Deletes a file. If the file is a directory, the directory and all of its content are
    * deleted, recursively.
    *
    * @param file the file to delete.
    *
    * @throws IOException if an I/O error occurs.
    */
   private void delete(File file) throws IOException {
      if(file != null) {
         if(file.isDirectory()) {
            Files.walkFileTree(file.toPath(), new SimpleFileVisitor<>() {
               @Override
               public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                  throws IOException
               {
                  Files.delete(file);
                  return FileVisitResult.CONTINUE;
               }

               @Override
               public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                  throws IOException
               {
                  try {
                     Files.delete(dir);
                  }
                  catch(DirectoryNotEmptyException e) {
                     // if this exception gets thrown wait a while and try again
                     try {
                        Thread.sleep(200);
                     }
                     catch(InterruptedException e1) {
                        // ignore it
                     }

                     Files.delete(dir);
                  }

                  return FileVisitResult.CONTINUE;
               }
            });
         }
         else {
            Files.deleteIfExists(file.toPath());
         }
      }
   }

   /**
    * Gets the plugin with the specified identifier.
    *
    * @param id the plugin identifier.
    *
    * @return the matching plugin or <tt>null</tt> if not found.
    */
   public Plugin getPlugin(String id) {
      getStorage();
      return plugins.get(id);
   }

   /**
    * Gets all installed plugins.
    *
    * @return the plugins.
    */
   public List<Plugin> getPlugins() {
      getStorage();
      return new ArrayList<>(plugins.values());
   }

   @Override
   public void blobAdded(BlobStorage.Event<Plugin.Descriptor> event) {
      blobChangeLock.lock();

      try {
         Plugin.Descriptor descriptor = event.getNewValue().getMetadata();
         Plugin existing = plugins.get(descriptor.getId());

         // already loaded by a resync that read the store after this event's write
         if(existing != null && Objects.equals(existing.getVersion(), descriptor.getVersion())) {
            return;
         }

         addLoadedPlugin(
            getStorage(), descriptor, event.getNewValue().getLastModified().toEpochMilli());
      }
      catch(Exception e) {
         LOG.warn("Failed to load plugin", e);
      }
      finally {
         blobChangeLock.unlock();
      }
   }

   // must be called while holding blobChangeLock
   private void addLoadedPlugin(BlobStorage<Plugin.Descriptor> storage,
                                Plugin.Descriptor descriptor, long lastModified)
   {
      unzipPlugin(storage, descriptor, lastModified);
      loadPlugin(descriptor);
      eventPublisher.publishEvent(new PluginAddedEvent(this, descriptor.getId()));
      eventPublisher.publishEvent(new PluginsChangedEvent(this));
      fireActionEvent(descriptor.getId());
   }

   @Override
   public void blobUpdated(BlobStorage.Event<Plugin.Descriptor> event) {
   }

   @Override
   public void blobRemoved(BlobStorage.Event<Plugin.Descriptor> event) {
      blobChangeLock.lock();

      try {
         removeLoadedPlugin(event.getOldValue().getMetadata().getId());
      }
      finally {
         blobChangeLock.unlock();
      }
   }

   // must be called while holding blobChangeLock
   private void removeLoadedPlugin(String pluginId) {
      Plugin plugin = plugins.remove(pluginId);

      if(plugin != null) {
         eventPublisher.publishEvent(new PluginRemovedEvent(this, pluginId));

         try {
            plugin.getClassLoader().close();
         }
         catch(IOException e) {
            LOG.warn("Failed to close plugin class loader", e);
         }

         try {
            delete(plugin.getFolder());
         }
         catch(IOException e) {
            LOG.warn("Failed to delete plugin directory", e);
         }

         eventPublisher.publishEvent(new PluginsChangedEvent(this));
         fireActionEvent(pluginId);
      }
   }

   @Override
   public void close() throws Exception {
      // a closed manager must not fetch a replacement storage
      closed = true;
      resyncExecutor.shutdown();

      try {
         if(!resyncExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
            LOG.warn("Timed out waiting for the plugin resync to finish");
         }
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();
      }

      for(Plugin plugin : plugins.values()) {
         try {
            plugin.getClassLoader().close();
         }
         catch(Exception e) {
            LOG.warn("Failed to close plugin: {}", plugin.getId(), e);
         }

         try {
            blobStorage.close();
         }
         catch(Exception e) {
            LOG.warn("Failed to close blob storage", e);
         }
      }
   }

   public void addActionListener(ActionListener listener) {
      listeners.add(listener);
   }

   public void removeActionListener(ActionListener listener) {
      listeners.remove(listener);
   }

   private void fireActionEvent(String name) {
      ActionEvent evt = new ActionEvent(this, 0, name);
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

   private void resetDBProviderConnection() {
      List<AuthenticationProvider> providers = SecurityEngine.getSecurity()
         .getAuthenticationChain()
         .map(AuthenticationChain::getProviders)
         .orElse(Collections.emptyList());

      for(AuthenticationProvider provider : providers) {
         if(provider instanceof DatabaseAuthenticationProvider) {
            ((DatabaseAuthenticationProvider) provider).resetConnection();
         }
      }
   }

   private volatile BlobStorage<Plugin.Descriptor> blobStorage;
   private final BlobStorageManager blobStorageManager;
   private final Object storageMonitor = new Object();
   // set by a re-attach until the loaded plugins have been synchronized with the store
   private final AtomicBoolean resyncPending = new AtomicBoolean();
   private final ExecutorService resyncExecutor = Executors.newSingleThreadExecutor(r -> {
      Thread thread = new GroupedThread(r, "PluginsResync");
      thread.setDaemon(true);
      return thread;
   });
   private volatile boolean closed = false;
   private final ApplicationEventPublisher eventPublisher;
   private final File pluginDirectory;
   private final Map<String, Plugin> plugins;
   private final Lock blobChangeLock;
   private final List<ActionListener> listeners = new ArrayList<>();
   private volatile boolean initialized = false;

   private static final Logger LOG = LoggerFactory.getLogger(Plugins.class);
   private static final String BLOB_CHANGE_LOCK = Plugins.class.getName() + ".blobChangeLock";
   private static final String STORAGE_ID = "plugins";
   // set while a resync loads plugins, whose loading reads the plugins through getPlugin()
   private static final ThreadLocal<Boolean> RESYNCING = new ThreadLocal<>();
}
