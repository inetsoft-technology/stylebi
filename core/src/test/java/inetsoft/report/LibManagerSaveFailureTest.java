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
package inetsoft.report;

import inetsoft.report.lib.logical.NoopLibrarySecurity;
import inetsoft.report.lib.physical.PhysicalLibrary;
import inetsoft.report.lib.physical.PhysicalLibraryEntry;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80413: the writers flush the pending library transactions before writing, so a save that
 * failed part way (an entry write or the commit by close) used to lose the flushed changes: the
 * next save found nothing pending and never persisted them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class LibManagerSaveFailureTest {
   @BeforeEach
   void before() {
      manager = new LibManager(new NoopLibrarySecurity(), "bug80413", Cluster.getInstance(),
                               BlobStorageManager.getInstance());
      options = SaveOptions.builder().filterAudit(false).forceSave(false).build();
   }

   @Test
   void failureOnNthEntryWriteKeepsTheChangesPending() throws Exception {
      manager.setScript("A", "var a = 1;");
      manager.setScript("B", "var b = 1;");
      manager.setScript("C", "var c = 1;");

      final FakeLibrary failing = new FakeLibrary();
      failing.failOnWrite = 2;
      assertThrows(IOException.class, () -> manager.save(failing, options));
      assertTrue(manager.isDirty(), "changes must stay pending after a failed save");

      final FakeLibrary healthy = new FakeLibrary();
      manager.save(healthy, options);
      assertTrue(healthy.written.containsAll(List.of("script/A", "script/B", "script/C")),
                 "entries never persisted: " + healthy.written);
      assertFalse(manager.isDirty());

      final FakeLibrary again = new FakeLibrary();
      manager.save(again, options);
      assertFalse(again.written.stream().anyMatch(p -> p.startsWith("script/")),
                  "a committed save must not be replayed: " + again.written);
   }

   @Test
   void failureWritingFoldersKeepsTheFolderWritePending() throws Exception {
      manager.addTableStyleFolder("F1");

      final FakeLibrary failing = new FakeLibrary();
      failing.failOnWrite = 1;
      failing.failPrefix = "style/folders.xml";
      assertThrows(IOException.class, () -> manager.save(failing, options));

      final FakeLibrary healthy = new FakeLibrary();
      manager.save(healthy, options);
      assertTrue(healthy.written.contains("style/folders.xml"), "folders.xml never written");
   }

   @Test
   void commitFailureKeepsTheChangesPendingAndIsRethrown() throws Exception {
      manager.setScript("A", "var a = 1;");
      manager.setScript("B", "var b = 1;");

      final FakeLibrary failing = new FakeLibrary();
      failing.failOnClose = true;
      final IOException ex = assertThrows(IOException.class, () -> manager.save(failing, options));
      assertEquals("close failed", ex.getMessage());
      assertTrue(manager.isDirty());

      final FakeLibrary healthy = new FakeLibrary();
      manager.save(healthy, options);
      assertTrue(healthy.written.containsAll(List.of("script/A", "script/B")),
                 "entries never persisted: " + healthy.written);
   }

   @Test
   void closeFailureDoesNotMaskTheWriteFailure() throws Exception {
      manager.setScript("A", "var a = 1;");

      final FakeLibrary failing = new FakeLibrary();
      failing.failOnWrite = 1;
      failing.failOnClose = true;
      final IOException ex = assertThrows(IOException.class, () -> manager.save(failing, options));
      assertEquals("write failed", ex.getMessage());
      assertEquals(1, ex.getSuppressed().length);
      assertEquals("close failed", ex.getSuppressed()[0].getMessage());
   }

   @Test
   void putDuringFailedSaveKeepsOrder() throws Exception {
      manager.setScript("A", "var a = 1;");

      final FakeLibrary failing = new FakeLibrary();
      failing.failOnWrite = 1;
      // a change made by another thread while the save is writing
      failing.onWrite = () -> manager.setScript("B", "var b = 1;");
      assertThrows(IOException.class, () -> manager.save(failing, options));

      final FakeLibrary healthy = new FakeLibrary();
      manager.save(healthy, options);
      final List<String> scripts = new ArrayList<>();

      for(String path : healthy.written) {
         if(path.startsWith("script/")) {
            scripts.add(path);
         }
      }

      assertEquals(List.of("script/A", "script/B"), scripts);
   }

   private LibManager manager;
   private SaveOptions options;

   private static final class FakeLibrary implements PhysicalLibrary {
      @Override
      public PhysicalLibraryEntry createEntry(String path, Properties properties) {
         return (PhysicalLibraryEntry) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] { PhysicalLibraryEntry.class },
            (proxy, method, args) -> {
               switch(method.getName()) {
               case "getPath":
                  return path;
               case "getOutputStream":
                  if(onWrite != null) {
                     final Runnable r = onWrite;
                     onWrite = null;
                     r.run();
                  }

                  if((failPrefix == null || path.startsWith(failPrefix)) && failOnWrite > 0 &&
                     ++writes == failOnWrite)
                  {
                     throw new IOException("write failed");
                  }

                  written.add(path);
                  return new ByteArrayOutputStream();
               default:
                  return null;
               }
            });
      }

      @Override
      public Iterator<PhysicalLibraryEntry> getEntries() {
         return Collections.emptyIterator();
      }

      @Override
      public void close() throws IOException {
         if(failOnClose) {
            throw new IOException("close failed");
         }
      }

      final List<String> written = new ArrayList<>();
      int failOnWrite = 0;
      int writes = 0;
      String failPrefix;
      boolean failOnClose;
      Runnable onWrite;
   }
}
