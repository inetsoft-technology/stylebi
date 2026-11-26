/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.uql.asset.sync;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.AssetRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82450, importing assets that share a target (one worksheet used by many dashboards) read
 * and rewrote the target's whole dependents record for every asset. The write batch of
 * {@link DependencyStorageService#beginBatch()} keeps the record in memory and writes it once.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DependencyStorageServiceBatchTest {
   private static final String ORG = "dep_batch_org";

   @Autowired
   private KeyValueStorageManager keyValueStorageManager;

   private KeyValueStorageManager countingManager;
   private KeyValueStorage<RenameTransformObject> countingStorage;
   private DependencyStorageService service;

   @SuppressWarnings("unchecked")
   @BeforeEach
   void setUp() {
      countingManager = Mockito.spy(keyValueStorageManager);
      Map<String, KeyValueStorage<RenameTransformObject>> spies = new ConcurrentHashMap<>();
      Mockito.doAnswer(inv -> spies.computeIfAbsent(
            inv.getArgument(0), id -> {
               try {
                  KeyValueStorage<RenameTransformObject> real =
                     (KeyValueStorage<RenameTransformObject>) inv.callRealMethod();
                  countingStorage = Mockito.mock(
                     KeyValueStorage.class, org.mockito.AdditionalAnswers.delegatesTo(real));
                  return countingStorage;
               }
               catch(Throwable e) {
                  throw new RuntimeException(e);
               }
            }))
         .when(countingManager).getStorage(Mockito.anyString(), Mockito.any());
      service = new DependencyStorageService(countingManager);
   }

   @AfterEach
   void tearDown() throws Exception {
      OrganizationManager.runInOrgScope(ORG, () -> {
         service.removeDependencyStorage(ORG);
         return null;
      });
   }

   /**
    * The read-modify-write every handler does for one dependent of a target.
    */
   private void addDependent(String target, AssetObject dependent) throws Exception {
      RenameTransformObject obj = service.get(target);
      DependenciesInfo info;
      List<AssetObject> list;

      if(obj instanceof DependenciesInfo) {
         info = (DependenciesInfo) obj;
         list = info.getDependencies();

         if(list.contains(dependent)) {
            return;
         }
      }
      else {
         info = new DependenciesInfo();
         list = new ArrayList<>();
      }

      list.add(dependent);
      info.setDependencies(list);
      service.put(target, info);
   }

   private static AssetEntry dashboard(int i) {
      return new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                            "/dash" + i, null, ORG);
   }

   private static String target(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                            name, null, ORG).toIdentifier(true);
   }

   private List<AssetObject> stored(String key) throws Exception {
      DependenciesInfo info = (DependenciesInfo) service.get(key);
      return info == null ? null : info.getDependencies();
   }

   private <T> T inOrg(Callable<T> task) throws Exception {
      return OrganizationManager.runInOrgScope(ORG, task);
   }

   @Test
   void batchedAndUnbatchedProduceTheSameRecord() throws Exception {
      inOrg(() -> {
         String plain = target("/plain");
         String batched = target("/batched");

         for(int i = 0; i < 40; i++) {
            addDependent(plain, dashboard(i % 25)); // repeats exercise the contains() path
         }

         try(DependencyStorageService.Batch batch = service.beginBatch()) {
            for(int i = 0; i < 40; i++) {
               addDependent(batched, dashboard(i % 25));
            }
         }

         assertEquals(25, stored(plain).size());
         assertEquals(stored(plain), stored(batched), "same entries in the same order");
         return null;
      });
   }

   @Test
   void batchedReadsAndWritesDoNotGrowWithDependentsAndHitTheStoreOnce() throws Exception {
      inOrg(() -> {
         String key = target("/shared");

         try(DependencyStorageService.Batch batch = service.beginBatch()) {
            for(int i = 0; i < 200; i++) {
               addDependent(key, dashboard(i));
            }

            // nothing is visible to other readers until the batch is flushed
            CompletableFuture<RenameTransformObject> other =
               CompletableFuture.supplyAsync(() -> {
                  try {
                     return OrganizationManager.runInOrgScope(ORG, () -> service.get(key));
                  }
                  catch(Exception e) {
                     throw new RuntimeException(e);
                  }
               });
            assertNull(other.get(30, TimeUnit.SECONDS));
         }

         Mockito.verify(countingStorage, Mockito.times(1)).put(Mockito.eq(key), Mockito.any());
         // one read at the first touch (batch), one by the flush's merge check, one by the other
         // thread; not one per dependent
         Mockito.verify(countingStorage, Mockito.atMost(3)).get(key);
         assertEquals(200, stored(key).size());
         return null;
      });
   }

   @Test
   void unbatchedWritesImmediately() throws Exception {
      inOrg(() -> {
         String key = target("/immediate");

         for(int i = 0; i < 5; i++) {
            addDependent(key, dashboard(i));
         }

         Mockito.verify(countingStorage, Mockito.times(5)).put(Mockito.eq(key), Mockito.any());
         return null;
      });
   }

   @Test
   void failureInTheMiddleStillFlushesWhatWasAdded() throws Exception {
      inOrg(() -> {
         String key = target("/failing");

         assertThrows(IllegalStateException.class, () -> {
            try(DependencyStorageService.Batch batch = service.beginBatch()) {
               for(int i = 0; i < 10; i++) {
                  addDependent(key, dashboard(i));
               }

               throw new IllegalStateException("package failed at asset 10");
            }
         });

         assertEquals(10, stored(key).size());
         // the thread is back to plain writes after the batch
         addDependent(key, dashboard(99));
         Mockito.verify(countingStorage, Mockito.times(2)).put(Mockito.eq(key), Mockito.any());
         return null;
      });
   }

   @Test
   void flushKeepsDependentsStoredByOthersMeanwhile() throws Exception {
      inOrg(() -> {
         String key = target("/merge");
         addDependent(key, dashboard(0));

         try(DependencyStorageService.Batch batch = service.beginBatch()) {
            addDependent(key, dashboard(1));
            addDependent(key, dashboard(2));

            // another import (another thread, so not part of this batch) adds its dependents
            // (replacing the record) while this batch is open
            Executors.newSingleThreadExecutor().submit(() -> {
               OrganizationManager.runInOrgScope(ORG, () -> {
                  DependenciesInfo info = new DependenciesInfo();
                  info.setDependencies(new ArrayList<>(List.of(dashboard(7), dashboard(8))));
                  service.put(key, info);
                  return null;
               });
               return null;
            }).get(30, TimeUnit.SECONDS);
         }

         List<AssetObject> result = stored(key);
         assertEquals(Set.of(dashboard(7), dashboard(8), dashboard(1), dashboard(2)),
                      new HashSet<>(result));
         assertEquals(4, result.size(), "no duplicates");
         return null;
      });
   }

   @Test
   void removeInsideTheBatchSeesBufferedStateAndIsFlushed() throws Exception {
      inOrg(() -> {
         String key = target("/remove");
         addDependent(key, dashboard(0));

         try(DependencyStorageService.Batch batch = service.beginBatch()) {
            addDependent(key, dashboard(1));
            // the remove-handlers read the record through get() and put it back
            DependenciesInfo info = (DependenciesInfo) service.get(key);
            assertEquals(2, info.getDependencies().size(), "buffered state is visible");
            info.getDependencies().remove(dashboard(0));
            service.put(key, info);
         }

         assertEquals(List.of(dashboard(1)), stored(key));
         return null;
      });
   }

   @Test
   void storeWideOperationsFlushTheOpenBatchFirst() throws Exception {
      inOrg(() -> {
         String key = target("/keys");

         try(DependencyStorageService.Batch batch = service.beginBatch()) {
            addDependent(key, dashboard(0));
            assertTrue(service.getKeys(null).contains(key),
                       "getKeys must see the record of the open batch");
         }

         return null;
      });
   }

   @Test
   void nestedBatchDoesNotFlushEarly() throws Exception {
      inOrg(() -> {
         String key = target("/nested");

         try(DependencyStorageService.Batch outer = service.beginBatch()) {
            try(DependencyStorageService.Batch inner = service.beginBatch()) {
               addDependent(key, dashboard(0));
            }

            Mockito.verify(countingStorage, Mockito.never()).put(Mockito.eq(key), Mockito.any());
            addDependent(key, dashboard(1));
         }

         Mockito.verify(countingStorage, Mockito.times(1)).put(Mockito.eq(key), Mockito.any());
         assertEquals(2, stored(key).size());
         return null;
      });
   }
}
