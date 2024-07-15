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
package inetsoft.util;

import com.github.zafarkhaja.semver.Version;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.*;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.log.LogManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class used to access extensions defined in plugins.
 */
@SingletonManager.Singleton(Plugins.Reference.class)
public final class Plugins implements BlobStorage.Listener<Plugin.Descriptor>, AutoCloseable {
   /**
    * Creates a new instance of <tt>Plugins</tt>.
    */
   public Plugins(BlobStorage<Plugin.Descriptor> blobStorage) {
      this.blobStorage = blobStorage;
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
      this.blobChangeLock = Cluster.getInstance().getLock(BLOB_CHANGE_LOCK);
   }

   // must be called outside of constructor to avoid infinite recursion
   private void init() {
      blobChangeLock.lock();

      try {
         blobStorage.stream()
            .sorted(this::comparePlugins)
            .peek(p -> unzipPlugin(p.getMetadata(), p.getLastModified().toEpochMilli()))
            .forEach(p -> loadPlugin(p.getMetadata()));
         validatePlugins();
      }
      finally {
         blobChangeLock.unlock();
      }

      blobStorage.addListener(this);
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
      return SingletonManager.getInstance(Plugins.class);
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
             BlobTransaction<Plugin.Descriptor> tx = blobStorage.beginTransaction();
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

      return installed;
   }

   /**
    * Explode plugin zip file if necessary.
    *
    * @param descriptor   the plugin descriptor.
    * @param lastModified the last modified timestamp.
    */
   private void unzipPlugin(Plugin.Descriptor descriptor, long lastModified) {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      File folder = getDirectoryPlugin(fileSystemService, descriptor.getId());

      try {
         if(!folder.exists()) {
            // out-of-date, remove and unzip
            if(folder.isDirectory() && folder.lastModified() < lastModified) {
               FileUtils.deleteDirectory(folder);
            }

            if(!folder.isDirectory()) {
               Files.createDirectories(folder.toPath());

               try(ZipInputStream input = new ZipInputStream(blobStorage.getInputStream(descriptor.getId()))) {
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

      if(!plugins.containsKey(id)) {
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
      Plugin plugin = plugins.get(pluginId);

      if(plugin == null || plugin.isReadOnly()) {
         return;
      }

      plugins.remove(pluginId);
      blobStorage.delete(pluginId);
      Drivers.getInstance().pluginRemoved(pluginId);
      plugin.getClassLoader().close();
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
      return plugins.get(id);
   }

   /**
    * Gets all installed plugins.
    *
    * @return the plugins.
    */
   public List<Plugin> getPlugins() {
      return new ArrayList<>(plugins.values());
   }

   @Override
   public void blobAdded(BlobStorage.Event<Plugin.Descriptor> event) {
      blobChangeLock.lock();

      try {
         Plugin.Descriptor descriptor = event.getNewValue().getMetadata();
         unzipPlugin(descriptor, event.getNewValue().getLastModified().toEpochMilli());
         loadPlugin(descriptor);
         Drivers.getInstance().pluginAdded(descriptor.getId());
         Config.reloadServices();
      }
      catch(Exception e) {
         LOG.warn("Failed to load plugin", e);
      }
      finally {
         blobChangeLock.unlock();
      }
   }

   @Override
   public void blobUpdated(BlobStorage.Event<Plugin.Descriptor> event) {
   }

   @Override
   public void blobRemoved(BlobStorage.Event<Plugin.Descriptor> event) {
      blobChangeLock.lock();

      try {
         String pluginId = event.getOldValue().getMetadata().getId();
         Plugin plugin = plugins.remove(pluginId);

         if(plugin != null) {
            Drivers.getInstance().pluginRemoved(pluginId);

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

            Config.reloadServices();
         }
      }
      finally {
         blobChangeLock.unlock();
      }
   }

   @Override
   public void close() throws Exception {
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

   public static final class Reference extends SingletonManager.Reference<Plugins> {
      @SuppressWarnings("unchecked")
      @Override
      public Plugins get(Object... parameters) {
         if(plugins == null) {
            plugins = new Plugins(
               SingletonManager.getInstance(BlobStorage.class, "plugins", true));
            plugins.init();
         }

         return plugins;
      }

      @Override
      public void dispose() {
         if(plugins != null) {
            try {
               plugins.close();
            }
            catch(Exception e) {
               LOG.warn("Failed to close plugins", e);
            }
            finally {
               plugins = null;
            }
         }
      }

      private Plugins plugins;
   }

   private final BlobStorage<Plugin.Descriptor> blobStorage;
   private final File pluginDirectory;
   private final Map<String, Plugin> plugins;
   private final Lock blobChangeLock;

   private static final Logger LOG = LoggerFactory.getLogger(Plugins.class);
   private static final String BLOB_CHANGE_LOCK = Plugins.class.getName() + ".blobChangeLock";
}
