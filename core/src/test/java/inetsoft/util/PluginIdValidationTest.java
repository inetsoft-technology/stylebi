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
import inetsoft.uql.jdbc.drivers.DriverPluginGenerator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80422: a plugin id supplied by the caller (tool argument or manifest Plugin-Id) was used
 * unvalidated as the manifest value, the blob storage key and a directory under the plugin
 * directory, so an id such as "../x" escaped the plugin directory.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PluginIdValidationTest {
   @Test
   void rejectsUnsafeIds() {
      String[] bad = {
         "../x", "a/b", "a\\b", "\\\\server\\share", "a\tb", "/abs", "C:/x", "C:x", "..", ".",
         "", " ", "a\nb", "a\rb", "a\0b", "x/../y", "a..b", null
      };

      for(String id : bad) {
         assertThrows(IllegalArgumentException.class, () -> Plugins.validatePluginId(id),
                      "expected rejection of " + id);
      }
   }

   @Test
   void acceptsLegitimateIds() {
      String[] good = {
         "org.hsqldb", "name-1.0", "inetsoft.tabular.x", "my_plugin", "com.inetsoft.jdbc.mysql",
         "a+b", "my plugin", ".hidden"
      };

      for(String id : good) {
         assertDoesNotThrow(() -> Plugins.validatePluginId(id), "expected acceptance of " + id);
      }
   }

   @Test
   void generatorRejectsUnsafeValues() throws Exception {
      File out = tempDir.resolve("out.zip").toFile();
      DriverPluginGenerator generator = new DriverPluginGenerator();
      File[] jars = new File[0];
      String[] drivers = new String[0];

      IllegalArgumentException ex = assertThrows(
         IllegalArgumentException.class,
         () -> generator.generatePlugin("../x", "1.0", "n", drivers, jars, out));
      assertTrue(ex.getMessage().startsWith("pluginId:"));
      assertFalse(ex.getMessage().contains("\n"));
      assertThrows(IllegalArgumentException.class,
                   () -> generator.generatePlugin("ok", "1.0\nPlugin-Id: x", "n", drivers, jars, out));
      assertThrows(IllegalArgumentException.class,
                   () -> generator.generatePlugin("ok", "1.0", "n\rx", drivers, jars, out));
   }

   @Test
   void installRejectsBadIdWithoutCreatingStorageEntry() throws Exception {
      Plugins plugins = Plugins.getInstance();
      File zip = createPlugin("../test80422-escape", tempDir.resolve("a.zip"));

      try(InputStream input = new FileInputStream(zip)) {
         assertThrows(IllegalArgumentException.class,
                      () -> plugins.installPlugin(input, zip.getName(), false));
      }

      assertFalse(storage(plugins).exists("../test80422-escape"));
   }

   @Test
   void containmentCheckThrowsForEscapingFolder() throws Exception {
      FileSystemService fs = FileSystemService.getInstance();
      File dir = tempDir.resolve("plugins").toFile();
      File relative = new File("target/test80422-plugins");

      for(File root : new File[] { dir, relative }) {
         assertEquals("ok", Plugins.resolveInsidePluginDirectory(fs, root, "ok").getName());
         assertNotNull(Plugins.resolveInsidePluginDirectory(fs, root, "a/b"));

         for(String name : new String[] {
            "../x", "a/../../x", "/abs/x", tempDir.resolve("other").toString(), ".", "a/..", ".." })
         {
            assertThrows(IllegalArgumentException.class,
                         () -> Plugins.resolveInsidePluginDirectory(fs, root, name),
                         "expected containment failure for " + name);
         }
      }
   }

   @Test
   void storedBadIdIsSkippedNotThrown() throws Exception {
      Plugins plugins = Plugins.getInstance();
      String badId = "../test80422-stored";
      File zip = createPlugin(badId, tempDir.resolve("b.zip"));
      BlobStorage<Plugin.Descriptor> storage = storage(plugins);

      // write straight to storage, as a left-over entry from before the validation existed
      try(InputStream input = new FileInputStream(zip);
          BlobTransaction<Plugin.Descriptor> tx = storage.beginTransaction();
          OutputStream output = tx.newStream(badId, new Plugin.Descriptor(zip)))
      {
         input.transferTo(output);
         tx.commit();
      }

      try {
         Thread.sleep(500L);
         assertNull(plugins.getPlugin(badId));
         assertDoesNotThrow(() -> {
            Method resync = Plugins.class.getDeclaredMethod("getSyncedStorage");
            resync.setAccessible(true);
            resync.invoke(plugins);
         });
         assertNull(plugins.getPlugin(badId));
      }
      finally {
         storage.delete(badId);
      }
   }

   @SuppressWarnings("unchecked")
   private BlobStorage<Plugin.Descriptor> storage(Plugins plugins) throws Exception {
      Method method = Plugins.class.getDeclaredMethod("getStorage");
      method.setAccessible(true);
      return (BlobStorage<Plugin.Descriptor>) method.invoke(plugins);
   }

   private File createPlugin(String id, Path path) throws IOException {
      Manifest manifest = new Manifest();
      Attributes attributes = manifest.getMainAttributes();
      attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
      attributes.putValue("Plugin-Id", id);
      attributes.putValue("Plugin-Name", "test");
      attributes.putValue("Plugin-Version", "1.0.0");
      attributes.putValue("Plugin-Vendor", "InetSoft");
      File file = Files.createFile(path).toFile();

      try(ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file))) {
         output.putNextEntry(new ZipEntry("classes/META-INF/MANIFEST.MF"));
         manifest.write(output);
         output.closeEntry();
      }

      return file;
   }

   @TempDir
   Path tempDir;
}
