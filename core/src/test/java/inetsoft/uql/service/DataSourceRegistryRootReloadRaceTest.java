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

import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.storage.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.util.Config;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78532: after a restart, a node's {@code DataSourceRegistry.init()} wrote an empty data
 * source registry root over the stored one.
 *
 * <p>{@code BlobStorage.LoadBlobsTask.validate()} adds indexed-storage roots that the store lacks
 * (here the MV_DEF_FOLDER root) to the loaded map only, so the replicated map holds more entries
 * than the key-value engine, and every later open of the store by another node reloads it with
 * {@code map.clear()} followed by {@code map.putAll(...)}. A reader on any node that looks at the
 * map between the two sees no stored root, and {@code DataSourceRegistry.init()} then stores an
 * empty one.</p>
 *
 * <p>The reload is held deterministically at the first map event that its own singleton thread
 * fires, i.e. right after its first change to the map. With {@code MockCluster} the events are
 * fired synchronously on the writing thread.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DataSourceRegistryRootReloadRaceTest.RegistryConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceRegistryRootReloadRaceTest {
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

      @Bean
      public DataSourceRegistry dataSourceRegistry(IndexedStorage indexedStorage, Config config,
                                                   Cluster cluster) throws Exception
      {
         return new DataSourceRegistry(indexedStorage, config, cluster);
      }
   }

   @BeforeEach
   void savePrincipal() {
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void restorePrincipal() {
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   /**
    * A reader of the store must see a stored key while another node's open of the store
    * reloads it.
    */
   @Test
   void storedRootStaysVisibleWhileAnotherNodeReloadsTheStore() throws Exception {
      String org = "org78532a";
      Fixture fixture = openStoreOnFirstNode(org);
      HeldReload reload = startSecondNodeOpen(fixture);

      try {
         boolean held = reload.awaitHeld();
         boolean visible = indexedStorage.contains(fixture.rootId);
         boolean inEngine = engine.contains(fixture.storeId, fixture.rootId);
         System.out.println("[78532] LB2 reload held at its first map change: " + held +
                               ", indexedStorage.contains(root) = " + visible +
                               ", engine.contains(root) = " + inEngine +
                               ", map.size() = " + fixture.map().size());

         assertTrue(inEngine, "setup: the engine must hold the root");
         assertTrue(visible, "the stored data source root " + fixture.rootId +
            " was not visible to a reader while another node reloaded " + fixture.storeId);
      }
      finally {
         reload.finish();
      }
   }

   /**
    * {@code DataSourceRegistry.init()} on one node while another node's open of the store reloads
    * it must not replace the stored data source root with an empty one.
    */
   @Test
   void initDuringAnotherNodesReloadKeepsTheStoredRoot() throws Exception {
      String org = "org78532b";
      Fixture fixture = openStoreOnFirstNode(org);
      HeldReload reload = startSecondNodeOpen(fixture);
      Thread init = null;

      try {
         boolean held = reload.awaitHeld();
         System.out.println("[78532] E2E reload held at its first map change: " + held);
         ExecutorService initExecutor = Executors.newSingleThreadExecutor();
         CompletableFuture<Thread> initThread = new CompletableFuture<>();
         Future<?> initDone = initExecutor.submit(() -> {
            initThread.complete(Thread.currentThread());
            ThreadContext.setContextPrincipal(principal(org));

            try {
               registry.init();
            }
            finally {
               ThreadContext.setContextPrincipal(null);
            }

            return null;
         });
         initExecutor.shutdown();
         init = initThread.get(10, TimeUnit.SECONDS);

         // wait until init() either returned or is waiting for its root write, which queues
         // behind the held reload on the store's singleton executor
         long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
         boolean writing = false;

         while(!initDone.isDone() && System.nanoTime() < deadline) {
            StackTraceElement[] stack = init.getStackTrace();

            if(Arrays.stream(stack).anyMatch(f -> f.getClassName().equals(BlobStorage.class.getName()) &&
                                              f.getMethodName().equals("createDirectory")))
            {
               writing = true;
               System.out.println("[78532] LB3 init() is writing the root while the reload is held:");
               Arrays.stream(stack).limit(14).forEach(f -> System.out.println("[78532]    at " + f));
               break;
            }

            Thread.onSpinWait();
         }

         System.out.println("[78532] init() returned before release: " + initDone.isDone() +
                               ", init() writing root: " + writing);
         reload.release();
         initDone.get(30, TimeUnit.SECONDS);
         reload.finish();

         @SuppressWarnings("unchecked")
         Blob<BlobIndexedStorage.Metadata> stored =
            (Blob<BlobIndexedStorage.Metadata>) engine.get(fixture.storeId, fixture.rootId);
         int entries = stored.getMetadata().getFolder().getEntries().length;
         System.out.println("[78532] engine root entries after init(): " + entries +
                               ", lastModified " + stored.getLastModified());

         assertEquals(1, entries, "the data source registry root stored in the engine was " +
            "replaced by init() while another node reloaded " + fixture.storeId);
      }
      finally {
         reload.finish();

         if(init != null) {
            init.join(TimeUnit.SECONDS.toMillis(30));
         }
      }
   }

   /**
    * Seeds the engine with a populated data source registry root and the indexed-storage roots
    * except the MV_DEF_FOLDER root, and opens the store on this node (the first load).
    */
   private Fixture openStoreOnFirstNode(String org) throws Exception {
      Fixture fixture = new Fixture(org);
      AssetFolder root = new AssetFolder();
      root.addEntry(new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
                                   "Orders", null, org));
      engine.put(fixture.storeId, fixture.rootId, folderBlob(fixture.rootId, root));

      for(AssetEntry.Type type : new AssetEntry.Type[] {
         AssetEntry.Type.REPOSITORY_FOLDER, AssetEntry.Type.FOLDER,
         AssetEntry.Type.SCHEDULE_TASK_FOLDER })
      {
         String id = new AssetEntry(AssetRepository.GLOBAL_SCOPE, type, "/", null, org).toIdentifier();
         engine.put(fixture.storeId, id, folderBlob(id, new AssetFolder()));
      }

      ThreadContext.setContextPrincipal(principal(org));
      assertTrue(indexedStorage.contains(fixture.rootId), "setup: first load must see the root");

      long engineSize = engine.size(fixture.storeId);
      int mapSize = fixture.map().size();
      String mvRoot = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.MV_DEF_FOLDER,
                                     "/", null, org).toIdentifier();
      System.out.println("[78532] LB1 after first load of " + fixture.storeId + ": map.size() = " +
                            mapSize + ", engine.size() = " + engineSize + ", map has " + mvRoot +
                            ": " + fixture.map().containsKey(mvRoot) + ", engine has it: " +
                            engine.contains(fixture.storeId, mvRoot));
      return fixture;
   }

   /**
    * Opens the store as another node does at startup: a new {@code LocalKeyValueStorage} whose
    * constructor submits the store's load and waits for it. The load is held at its first map
    * change.
    */
   private HeldReload startSecondNodeOpen(Fixture fixture) {
      HeldReload reload = new HeldReload(fixture);
      cluster.addReplicatedMapListener(fixture.mapName(), reload);
      reload.start();
      return reload;
   }

   private static Blob<BlobIndexedStorage.Metadata> folderBlob(String key, AssetFolder folder) {
      BlobIndexedStorage.Metadata metadata = new BlobIndexedStorage.Metadata();
      metadata.setClassName(AssetFolder.class.getName());
      metadata.setIdentifier(key);
      metadata.setFolder(folder);
      return new Blob<>(key, null, 0L, Instant.now(), metadata);
   }

   private static Principal principal(String org) {
      return new SRPrincipal(new IdentityID("admin", org), new IdentityID[0], new String[0], org, 0L);
   }

   private final class Fixture {
      Fixture(String org) {
         this.storeId = org + "__indexedStorage";
         this.rootId = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER,
                                      "/", null, org).toIdentifier();
      }

      String mapName() {
         return "inetsoft.storage.kv." + storeId;
      }

      DistributedMap<String, ?> map() {
         return cluster.getReplicatedMap(mapName());
      }

      final String storeId;
      final String rootId;
   }

   /**
    * The second node's open of the store, held at the first map event fired by the store's
    * singleton (load) thread.
    */
   private final class HeldReload implements MapChangeListener<String, Object> {
      HeldReload(Fixture fixture) {
         this.fixture = fixture;
      }

      void start() {
         Thread thread = new Thread(() -> {
            try(KeyValueStorageManager otherNode = new KeyValueStorageManager(engine, cluster)) {
               BlobStorage<BlobIndexedStorage.Metadata> storage = BlobStorage.createBlobStorage(
                  fixture.storeId, true, blobCache, otherNode, cluster);
               storage.close();
            }
            catch(Exception e) {
               failure = e;
            }
            finally {
               opened.countDown();
            }
         }, "78532-second-node-open");
         thread.setDaemon(true);
         thread.start();
      }

      /**
       * @return {@code true} if the reload reached its first map change, {@code false} if the open
       *         completed without any map change.
       */
      boolean awaitHeld() throws Exception {
         long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);

         while(System.nanoTime() < deadline) {
            if(held.await(50, TimeUnit.MILLISECONDS)) {
               return true;
            }

            if(opened.getCount() == 0) {
               assertNull(failure, () -> "the second node's open failed: " + failure);
               return false;
            }
         }

         fail("the second node's open neither changed the map nor completed");
         return false;
      }

      void release() {
         release.countDown();
      }

      void finish() throws Exception {
         release.countDown();
         cluster.removeReplicatedMapListener(fixture.mapName(), this);
         assertTrue(opened.await(30, TimeUnit.SECONDS), "the second node's open did not complete");
         assertNull(failure, () -> "the second node's open failed: " + failure);
      }

      @Override
      public void entryAdded(EntryEvent<String, Object> event) {
         hold();
      }

      @Override
      public void entryRemoved(EntryEvent<String, Object> event) {
         hold();
      }

      @Override
      public void entryUpdated(EntryEvent<String, Object> event) {
         hold();
      }

      private void hold() {
         if(Thread.currentThread().getName().equals("MockClusterSingleton-" + fixture.storeId) &&
            armed.compareAndSet(true, false))
         {
            held.countDown();

            try {
               release.await(20, TimeUnit.SECONDS);
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         }
      }

      private final Fixture fixture;
      private final AtomicBoolean armed = new AtomicBoolean(true);
      private final CountDownLatch held = new CountDownLatch(1);
      private final CountDownLatch release = new CountDownLatch(1);
      private final CountDownLatch opened = new CountDownLatch(1);
      private volatile Exception failure;
   }

   @Autowired Cluster cluster;
   @Autowired KeyValueEngine engine;
   @Autowired BlobCache blobCache;
   @Autowired IndexedStorage indexedStorage;
   @Autowired DataSourceRegistry registry;
   private Principal savedPrincipal;
}
