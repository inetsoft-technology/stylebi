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

import inetsoft.storage.*;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78253: the "plugins" store is fetched once. When either storage manager evicts it (more
 * than 50 other stores opened, e.g. by several organizations), the held instance is closed and
 * its map listener removed, so an install on this node and the installs and uninstalls of other
 * nodes were never loaded or unloaded until a restart. The evictions here are natural ones,
 * reached by opening other stores through the real managers.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PluginsStorageEvictionTest {
   @BeforeEach
   void setUp() {
      plugins = Plugins.getInstance();
      installed.clear();
   }

   @AfterEach
   void tearDown() throws Exception {
      for(String id : installed) {
         plugins.uninstallPlugin(id);
      }
   }

   /**
    * The inner key-value store is evicted from KeyValueStorageManager. A plugin uploaded on this
    * node afterwards must be loaded.
    */
   @Test
   void installAfterKeyValueEvictionIsLoaded() throws Exception {
      BlobStorage<Plugin.Descriptor> held = getHeldStorage();
      KeyValueStorageManager manager = KeyValueStorageManager.getInstance();
      evict(held, i -> manager.<Serializable>getStorage("test78253.kv.evict." + i));

      String id = "test78253-local";
      File zip = createPlugin(id, "1.0.0");

      try(InputStream input = new FileInputStream(zip)) {
         assertTrue(plugins.installPlugin(input, zip.getName(), false));
      }

      installed.add(id);
      waitFor(() -> plugins.getPlugin(id) != null);
      assertNotSame(held, getHeldStorage(), "the closed plugin storage was not replaced");
      assertFalse(getHeldStorage().isClosed());
   }

   /**
    * The blob store is evicted from BlobStorageManager. Another node installs one plugin and
    * uninstalls another while this node is detached; a later read on this node, which writes
    * nothing, must load the first and unload the second, each once.
    */
   @Test
   void remoteChangesWhileDetachedAreAppliedOnRead() throws Exception {
      String removedId = "test78253-removed";
      String addedId = "test78253-added";
      File removedZip = createPlugin(removedId, "1.0.0");
      File addedZip = createPlugin(addedId, "1.0.0");

      try(InputStream input = new FileInputStream(removedZip)) {
         assertTrue(plugins.installPlugin(input, removedZip.getName(), false));
      }

      installed.add(removedId);
      waitFor(() -> plugins.getPlugin(removedId) != null);

      BlobStorage<Plugin.Descriptor> held = getHeldStorage();
      BlobStorageManager manager = BlobStorageManager.getInstance();
      evict(held, i -> manager.<Serializable>getStorage("test78253.blob.evict." + i, false));

      // the other node writes through its own instance; wait for the events of its writes, so
      // that they have been dispatched before anything on this node re-attaches
      BlobStorage<Plugin.Descriptor> writer = manager.getStorage("plugins", true);
      assertNotSame(held, writer);
      List<String> writerEvents = new CopyOnWriteArrayList<>();
      BlobStorage.Listener<Plugin.Descriptor> writerListener = new BlobStorage.Listener<>() {
         @Override
         public void blobAdded(BlobStorage.Event<Plugin.Descriptor> event) {
            writerEvents.add("added:" + event.getNewValue().getMetadata().getId());
         }

         @Override
         public void blobUpdated(BlobStorage.Event<Plugin.Descriptor> event) {
         }

         @Override
         public void blobRemoved(BlobStorage.Event<Plugin.Descriptor> event) {
            writerEvents.add("removed:" + event.getOldValue().getMetadata().getId());
         }
      };
      writer.addListener(writerListener);

      try {
         try(InputStream input = new FileInputStream(addedZip);
             BlobTransaction<Plugin.Descriptor> tx = writer.beginTransaction();
             OutputStream output = tx.newStream(addedId, new Plugin.Descriptor(addedZip)))
         {
            input.transferTo(output);
            tx.commit();
         }

         installed.add(addedId);
         writer.delete(removedId);
         waitFor(() -> writerEvents.contains("added:" + addedId) &&
            writerEvents.contains("removed:" + removedId));
      }
      finally {
         writer.removeListener(writerListener);
      }

      // nothing on this node has seen the changes yet
      Map<String, Plugin> loaded = getLoadedPlugins();
      assertTrue(loaded.containsKey(removedId));
      assertFalse(loaded.containsKey(addedId));

      // a read re-attaches and applies the changes made while detached
      List<String> ids = new ArrayList<>();
      plugins.getPlugins().forEach(p -> ids.add(p.getId()));
      assertTrue(ids.contains(addedId), ids.toString());
      assertFalse(ids.contains(removedId), ids.toString());
      assertNull(plugins.getPlugin(removedId));

      // the plugin is loaded once, a late event does not load it again
      Plugin added = plugins.getPlugin(addedId);
      Thread.sleep(1000L);
      assertSame(added, plugins.getPlugin(addedId));
      assertFalse(getHeldStorage().isClosed());
   }

   private void evict(BlobStorage<Plugin.Descriptor> held, java.util.function.IntConsumer open)
      throws InterruptedException
   {
      // a store fetched again recently can survive one pass (W-TinyLFU admission), so the same
      // ids are opened again until the held instance is closed
      for(int pass = 0; pass < 10 && !held.isClosed(); pass++) {
         for(int i = 0; i < 60; i++) {
            open.accept(i);
         }

         long end = System.currentTimeMillis() + 2000L;

         while(!held.isClosed() && System.currentTimeMillis() < end) {
            Thread.sleep(50L);
         }
      }

      assertTrue(held.isClosed(), "the plugin storage was never evicted and closed");
   }

   private File createPlugin(String id, String version) throws IOException {
      Manifest manifest = new Manifest();
      Attributes attributes = manifest.getMainAttributes();
      attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
      attributes.putValue("Plugin-Id", id);
      attributes.putValue("Plugin-Name", id);
      attributes.putValue("Plugin-Version", version);
      attributes.putValue("Plugin-Vendor", "InetSoft");
      File file = Files.createFile(tempDir.resolve(id + ".zip")).toFile();

      try(ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file))) {
         output.putNextEntry(new ZipEntry("classes/META-INF/MANIFEST.MF"));
         manifest.write(output);
         output.closeEntry();
      }

      return file;
   }

   @SuppressWarnings("unchecked")
   private BlobStorage<Plugin.Descriptor> getHeldStorage() throws Exception {
      Field field = Plugins.class.getDeclaredField("blobStorage");
      field.setAccessible(true);
      return (BlobStorage<Plugin.Descriptor>) field.get(plugins);
   }

   @SuppressWarnings("unchecked")
   private Map<String, Plugin> getLoadedPlugins() throws Exception {
      Field field = Plugins.class.getDeclaredField("plugins");
      field.setAccessible(true);
      return (Map<String, Plugin>) field.get(plugins);
   }

   private static void waitFor(BooleanSupplier condition) throws InterruptedException {
      long end = System.currentTimeMillis() + 10000L;

      while(!condition.getAsBoolean()) {
         if(System.currentTimeMillis() > end) {
            fail("Timed out waiting for the condition");
         }

         Thread.sleep(50L);
      }
   }

   @TempDir
   Path tempDir;
   private Plugins plugins;
   private final List<String> installed = new ArrayList<>();
}
