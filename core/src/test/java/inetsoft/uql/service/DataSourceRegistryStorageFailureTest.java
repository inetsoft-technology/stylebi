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
package inetsoft.uql.service;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.util.Config;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #82205: a storage failure while the registry root is written was only logged, so a caller
 * went on as if the object was saved, and the cached root kept the entry of the object that was
 * never written, which a later successful save then stored.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DataSourceRegistryStorageFailureTest.RegistryConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceRegistryStorageFailureTest {
   @Configuration
   static class RegistryConfig {
      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager, cluster, eventPublisher);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      @Bean
      public IndexedStorage indexedStorage(BlobStorageManager blobStorageManager) {
         return new BlobIndexedStorage(blobStorageManager);
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal());
      storage = spy(indexedStorage);
      Cluster node = mock(Cluster.class, AdditionalAnswers.delegatesTo(cluster));
      Lock lock = new ReentrantLock();
      doReturn(lock).when(node).getLock(anyString());
      registry = new DataSourceRegistry(storage, config, node);
      registry.init();
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   @Test
   void throwingVariantThrowsWhenTheRootWriteFails() throws Exception {
      AssetEntry failed = folderEntry("failedOnce");
      failRootWriteOnce();

      assertThrows(DataSourceStorageException.class,
                   () -> registry.setObjectOrThrow(failed, folder("failedOnce"), true));
      assertFalse(root().containsEntry(failed), "the stored root lists an unsaved object");
   }

   @Test
   void failedSaveLeavesNoEntryInTheCachedRoot() throws Exception {
      AssetEntry failed = folderEntry("phantom");
      AssetEntry later = folderEntry("savedLater");
      // the root is cached by an earlier save, like on a running server
      registry.setObject(folderEntry("warmUp"), folder("warmUp"));
      failRootWriteOnce();

      assertThrows(DataSourceStorageException.class,
                   () -> registry.setObjectOrThrow(failed, folder("phantom"), true));
      registry.setObjectOrThrow(later, folder("savedLater"), true);

      assertTrue(root().containsEntry(later));
      assertFalse(root().containsEntry(failed),
                  "a later save stored the entry of the object that was never written");
   }

   @Test
   void setObjectStillSwallowsTheFailure() throws Exception {
      AssetEntry failed = folderEntry("swallowed");
      failRootWriteOnce();

      assertDoesNotThrow(() -> registry.setObject(failed, folder("swallowed")));
      assertFalse(root().containsEntry(failed));
   }

   private void failRootWriteOnce() throws Exception {
      doThrow(new IOException("Failed to create directory"))
         .doCallRealMethod()
         .when(storage).putXMLSerializable(eq(rootId()), any());
   }

   private AssetFolder root() throws Exception {
      return (AssetFolder) indexedStorage.getXMLSerializable(rootId(), null);
   }

   private static String rootId() {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER,
                            "/", null, ORG).toIdentifier();
   }

   private static AssetEntry folderEntry(String name) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER,
                            name, null, ORG);
   }

   private static DataSourceFolder folder(String name) {
      return new DataSourceFolder(name, LocalDateTime.now(), "admin");
   }

   private static Principal principal() {
      return new SRPrincipal(new IdentityID("admin", ORG), new IdentityID[0], new String[0], ORG,
                             0L);
   }

   private static final String ORG = "org82205";
   @Autowired Cluster cluster;
   @Autowired IndexedStorage indexedStorage;
   @Autowired Config config;
   private IndexedStorage storage;
   private DataSourceRegistry registry;
   private Principal savedPrincipal;
}
