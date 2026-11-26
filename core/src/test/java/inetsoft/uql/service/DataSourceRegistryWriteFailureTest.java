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
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.util.Config;
import inetsoft.util.*;
import inetsoft.web.portal.data.DataSourceBrowserService;
import inetsoft.web.portal.data.DataSourceInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #82094: a failed write of the data source index (the root) was logged and reported as a
 * success, and left the cached index listing a folder whose object was never stored. The next
 * successful write saved that index, and the folder then broke the portal data source search for
 * every user.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DataSourceRegistryWriteFailureTest.RegistryConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceRegistryWriteFailureTest {
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
      registry = new DataSourceRegistry(storage, config, cluster);
      registry.init();
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   @Test
   void failedFolderCreateThrowsAndLeavesNothingBehind() throws Exception {
      String name = "ghost82094";
      registry.setDataSourceFolder(folder("ok82094"));
      failRootWrites(1);

      assertThrows(RuntimeException.class, () -> registry.setDataSourceFolder(folder(name)));

      assertFalse(registry.getSubfolderNames(null).contains(name),
                  "the cached index lists the folder that was not saved");
      assertFalse(indexedStorage.contains(entry(name).toIdentifier(), ORG),
                  "the folder object is stored but not listed");
      assertFalse(registry.isDataSourcePathInUse(name), "the failed name is blocked");

      // the next successful write must not save the failed entry into the index
      registry.setDataSourceFolder(folder("other82094"));
      assertFalse(storedRoot().containsEntry(entry(name)),
                  "the stored index lists the folder that was not saved");
      registry.clearCache();
      assertFalse(registry.getSubfolderNames(null).contains(name));

      // a retry with the same name succeeds
      registry.setDataSourceFolder(folder(name));
      registry.clearCache();
      assertTrue(registry.getSubfolderNames(null).contains(name));
      assertNotNull(registry.getDataSourceFolder(name));
      assertTrue(storedRoot().containsEntry(entry(name)));
   }

   @Test
   void persistentRootFailureIsReportedEveryTime() throws Exception {
      String name = "down82094";
      failRootWrites(Integer.MAX_VALUE);

      assertThrows(RuntimeException.class, () -> registry.setDataSourceFolder(folder(name)));
      assertThrows(RuntimeException.class, () -> registry.setDataSourceFolder(folder(name)));
      assertFalse(registry.getSubfolderNames(null).contains(name));
      assertFalse(indexedStorage.contains(entry(name).toIdentifier(), ORG));

      doCallRealMethod().when(storage).putXMLSerializable(eq(rootId()), any());
      registry.setDataSourceFolder(folder(name));
      registry.clearCache();
      assertNotNull(registry.getDataSourceFolder(name));
   }

   @Test
   void failedUpdateKeepsTheListedEntry() throws Exception {
      String name = "upd82094";
      registry.setDataSourceFolder(folder(name));
      AssetEntry before = cachedRootEntry(name);
      Date modified = before.getModifiedDate();
      Thread.sleep(5);
      failRootWrites(1);

      assertThrows(RuntimeException.class, () -> registry.setDataSourceFolder(folder(name)));

      AssetEntry after = cachedRootEntry(name);
      assertNotNull(after, "the existing entry was dropped from the index");
      assertEquals(modified, after.getModifiedDate(),
                   "the listed entry was changed by a write that failed");
      assertNotNull(registry.getDataSourceFolder(name), "the existing folder was removed");
   }

   @Test
   void searchSkipsAFolderThatCannotBeLoaded() throws Exception {
      registry.setDataSourceFolder(folder("okFolder82094"));
      registry.setDataSourceFolder(folder("phantom82094"));
      // the index lists the folder, but its object is gone (as left by a failed write)
      indexedStorage.remove(entry("phantom82094").toIdentifier(), true);
      registry.clearCache();
      assertNull(registry.getDataSourceFolder("phantom82094"));

      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(Principal.class), any(), anyString(), any()))
         .thenReturn(true);
      DataSourceBrowserService service = new DataSourceBrowserService(
         security, null, null, null, registry, config, null);

      List<String> names = service.getSearchDataSources(null, "82094", principal()).stream()
         .map(DataSourceInfo::name)
         .toList();

      assertTrue(names.contains("okFolder82094"), "search lost the other entries: " + names);
      assertFalse(names.contains("phantom82094"), names.toString());
   }

   private void failRootWrites(int count) throws Exception {
      int[] left = { count };
      doAnswer(invocation -> {
         if(left[0] > 0) {
            left[0]--;
            throw new IOException("Failed to create directory (injected)");
         }

         return invocation.callRealMethod();
      }).when(storage).putXMLSerializable(eq(rootId()), any());
   }

   private AssetEntry cachedRootEntry(String name) {
      AssetEntry[] entries = registry.getEntries(name);

      for(AssetEntry entry : entries) {
         if(name.equals(entry.getPath())) {
            return entry;
         }
      }

      return null;
   }

   private AssetFolder storedRoot() throws Exception {
      return (AssetFolder) indexedStorage.getXMLSerializable(rootId(), null);
   }

   private static String rootId() {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER,
                            "/", null, ORG).toIdentifier();
   }

   private static AssetEntry entry(String name) {
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

   private static final String ORG = "org82094";
   @Autowired Cluster cluster;
   @Autowired IndexedStorage indexedStorage;
   @Autowired Config config;
   private IndexedStorage storage;
   private DataSourceRegistry registry;
   private Principal savedPrincipal;
}
