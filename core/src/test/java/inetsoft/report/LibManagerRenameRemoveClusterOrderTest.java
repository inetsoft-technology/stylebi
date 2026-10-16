/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageEvent;
import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.storage.BlobStorageManager;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #76765 SSL-005: renameScript/removeScript used to broadcast the cluster message before the
 * change was saved. The sender also receives its own message, and the resulting
 * reloadLibrary() re-read the pre-change storage, wiping the unsaved rename/removal. The cluster
 * here delivers messages to the local listener synchronously, the worst case of the (in
 * production asynchronous) self-delivery.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  LibManagerRenameRemoveClusterOrderTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LibManagerRenameRemoveClusterOrderTest {
   @Configuration
   static class Config {
      @Bean
      RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }
   }

   @Autowired private BlobStorageManager blobStorageManager;

   private static final long DEBOUNCE_SETTLE_MS = 1500L;

   private LibManager lib;

   @BeforeEach
   void setUp() throws Exception {
      AtomicReference<MessageListener> listener = new AtomicReference<>();
      Cluster cluster = mock(Cluster.class);
      doAnswer(i -> {
         listener.set(i.getArgument(0));
         return null;
      }).when(cluster).addMessageListener(any(MessageListener.class));
      doAnswer(i -> {
         listener.get().messageReceived(
            new MessageEvent(this, "local", true, (Serializable) i.getArgument(0)));
         return null;
      }).when(cluster).sendMessage(any(Serializable.class));

      lib = new LibManager(new NoopLibrarySecurity(), "r76765-ssl005-org", cluster,
                           blobStorageManager) { };
      lib.setScript("fn76765A", "function fn76765A() { return 1; }");
      lib.save();
   }

   @AfterEach
   void tearDown() throws Exception {
      lib.removeScript("fn76765A");
      lib.removeScript("fn76765B");
      lib.save();

      // Every LibManager's storage listener reacts to a change of ANY org's library by reloading
      // after a 1 s debounce. Let that reload land here, while this test's own LibManager is still
      // listening, instead of in a later test class (e.g. ScriptLibraryRenameDeclarationTest),
      // where it reloads the library from storage mid-test and adds a trailing newline to scripts.
      Thread.sleep(DEBOUNCE_SETTLE_MS);
      lib.close();
   }

   @Test
   void renameSurvivesSelfDeliveredClusterMessage() throws Exception {
      lib.renameScript("fn76765A", "fn76765B");
      lib.save();

      assertNull(lib.getScript("fn76765A"));
      assertNotNull(lib.getScript("fn76765B"));
   }

   @Test
   void removeSurvivesSelfDeliveredClusterMessage() throws Exception {
      lib.removeScript("fn76765A");
      lib.save();

      assertNull(lib.getScript("fn76765A"));
   }
}
