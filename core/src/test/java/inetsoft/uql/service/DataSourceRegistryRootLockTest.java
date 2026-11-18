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

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #81435: the data source registry root is read, changed and written back as a whole, and
 * two cluster nodes doing that at the same time lost one node's entry (the data source object was
 * stored but the root did not list it).
 *
 * <p>Two {@code DataSourceRegistry} instances over one shared storage stand for two nodes. The
 * first node's root write is held after it has read the root while the second node changes
 * another entry; the registries share one lock per name, like the cluster does.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DataSourceRegistryRootLockTest.RegistryConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceRegistryRootLockTest {
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
   void setUp() {
      savedPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal());
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   @Test
   void concurrentSetObjectOnTwoNodesKeepsBothEntries() throws Exception {
      Nodes nodes = new Nodes();
      AssetEntry a = folderEntry("lockA");
      AssetEntry b = folderEntry("lockB");
      nodes.runHeld(() -> nodes.a.setObject(a, folder("lockA")),
                    () -> nodes.b.setObject(b, folder("lockB")));

      assertRootHas(nodes, a, b);
   }

   @Test
   void concurrentRemoveAndSetObjectKeepsBothChanges() throws Exception {
      Nodes nodes = new Nodes();
      AssetEntry old = folderEntry("lockOld");
      AssetEntry added = folderEntry("lockNew");
      nodes.a.setObject(old, folder("lockOld"));
      nodes.runHeld(() -> nodes.a.removeObject(old),
                    () -> nodes.b.setObject(added, folder("lockNew")));

      assertRootHas(nodes, added);
      assertFalse(root().containsEntry(old), "the removed entry is still listed");
   }

   @Test
   void concurrentRemoveObjectsAndSetObjectKeepsBothChanges() throws Exception {
      Nodes nodes = new Nodes();
      AssetEntry old1 = folderEntry("lockOld1");
      AssetEntry old2 = folderEntry("lockOld2");
      AssetEntry added = folderEntry("lockNew2");
      nodes.a.setObject(old1, folder("lockOld1"));
      nodes.a.setObject(old2, folder("lockOld2"));
      nodes.runHeld(() -> nodes.a.removeObjects(new AssetEntry[] { old1, old2 }),
                    () -> nodes.b.setObject(added, folder("lockNew2")));

      assertRootHas(nodes, added);
      assertFalse(root().containsEntry(old1), "the removed entry is still listed");
      assertFalse(root().containsEntry(old2), "the removed entry is still listed");
   }

   @Test
   void rootChangesTakeThePerOrgClusterLock() throws Exception {
      Nodes nodes = new Nodes();
      AssetEntry a = folderEntry("lockC");
      nodes.a.setObject(a, folder("lockC"));
      nodes.a.removeObject(a);

      verify(nodes.clusterA, atLeast(2)).getLock(
         DataSourceRegistry.class.getName() + ".root." + ORG);
   }

   private void assertRootHas(Nodes nodes, AssetEntry... entries) throws Exception {
      nodes.a.clearCache();
      nodes.b.clearCache();
      AssetFolder root = root();

      for(AssetEntry entry : entries) {
         assertTrue(root.containsEntry(entry),
                    "the data source root does not list " + entry.getPath());
      }
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

   /**
    * Two registries ("nodes") over one storage whose cluster locks are shared per name.
    */
   private final class Nodes {
      Nodes() throws Exception {
         Map<String, Lock> locks = new ConcurrentHashMap<>();
         clusterA = lockingCluster(locks);
         Cluster clusterB = lockingCluster(locks);
         storage = spy(indexedStorage);
         a = new DataSourceRegistry(storage, config, clusterA);
         b = new DataSourceRegistry(storage, config, clusterB);
         a.init();
      }

      /**
       * Runs the first task on its own thread and holds it at its first write of the data source
       * root, runs the second task, then releases the first. With the cluster lock the second
       * task waits for the first, without it the second one finishes in between.
       */
      void runHeld(Runnable first, Runnable second) throws Exception {
         CountDownLatch held = new CountDownLatch(1);
         CountDownLatch release = new CountDownLatch(1);
         ExecutorService executor = Executors.newFixedThreadPool(2);

         try {
            doAnswer(invocation -> {
               if(Thread.currentThread().getName().equals("node-a") && held.getCount() > 0) {
                  held.countDown();
                  assertTrue(release.await(20, TimeUnit.SECONDS));
               }

               return invocation.callRealMethod();
            }).when(storage).putXMLSerializable(eq(rootId()), any());

            Future<?> f1 = executor.submit(named("node-a", first));
            assertTrue(held.await(20, TimeUnit.SECONDS), "the first node did not write the root");
            Future<?> f2 = executor.submit(named("node-b", second));

            try {
               // without the lock the second node finishes while the first one is held
               f2.get(2, TimeUnit.SECONDS);
            }
            catch(TimeoutException e) {
               // expected with the lock, the second node waits for the first
            }

            release.countDown();
            f1.get(20, TimeUnit.SECONDS);
            f2.get(20, TimeUnit.SECONDS);
         }
         finally {
            release.countDown();
            executor.shutdownNow();
         }
      }

      private Runnable named(String name, Runnable task) {
         return () -> {
            Thread.currentThread().setName(name);
            ThreadContext.setContextPrincipal(principal());

            try {
               task.run();
            }
            finally {
               ThreadContext.setContextPrincipal(null);
            }
         };
      }

      private Cluster lockingCluster(Map<String, Lock> locks) {
         Cluster node = mock(Cluster.class, AdditionalAnswers.delegatesTo(cluster));
         doAnswer(invocation -> locks.computeIfAbsent(invocation.getArgument(0, String.class),
                                                      k -> new ReentrantLock()))
            .when(node).getLock(anyString());
         return node;
      }

      final DataSourceRegistry a;
      final DataSourceRegistry b;
      final Cluster clusterA;
      final IndexedStorage storage;
   }

   private static final String ORG = "org81435";
   @Autowired Cluster cluster;
   @Autowired IndexedStorage indexedStorage;
   @Autowired Config config;
   private Principal savedPrincipal;
}
