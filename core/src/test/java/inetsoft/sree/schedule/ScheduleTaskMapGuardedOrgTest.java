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
package inetsoft.sree.schedule;

import inetsoft.sree.security.OrgIdChangeGuard;
import inetsoft.test.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.storage.BlobStorageManager;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.TransformListener;
import inetsoft.util.XMLSerializable;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81319: listing the schedule tasks of an organization whose id was just changed failed with
 * "Failed to list schedule tasks", because the bug #58866 heal write of the root folder in
 * ScheduleTaskMap.getRoot() is refused by OrgIdChangeGuard. A read must not fail because the heal
 * write is refused; it returns the empty root and leaves nothing behind, so that a later read
 * after the guard lifts heals normally.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleTaskMapGuardedOrgTest {
   // a fresh organization id per test, the root blob of an earlier test must not be found
   @Autowired
   BlobStorageManager blobStorageManager;

   private HealRecordingStorage storage;
   private String retired;
   private String other;

   @BeforeEach
   void setUp(TestInfo info) {
      retired = ("bug81319" + info.getTestMethod().map(m -> m.getName()).orElse("x") +
         System.nanoTime()).toLowerCase();
      other = retired + "o";
      storage = new HealRecordingStorage(blobStorageManager);
   }

   @AfterEach
   void tearDown() {
      OrgIdChangeGuard.clear(other);
      OrgIdChangeGuard.clear(retired);
   }

   // retired id (change completed, no org of that id any more): all readers return empty
   @Test
   void readersOfRetiredOrg_returnEmptyAndWriteNothing() throws Exception {
      ScheduleTaskMap map = newMap(retired);
      retire(retired);

      assertEquals(0, (int) onOtherThread(map::size));
      assertTrue(onOtherThread(() -> map.keySet().isEmpty()));
      assertTrue(onOtherThread(() -> map.values().isEmpty()));
      assertTrue(onOtherThread(() -> !map.entrySet().iterator().hasNext()));

      assertFalse(rootStored(map, retired), "no blob written");
      assertFalse(rootCache(map).containsKey(retired), "empty root is not cached");
      assertFalse(rootTsCache(map).containsKey(retired), "no timestamp cached");
   }

   // active change on this node: a reader on another thread gets empty, no exception
   @Test
   void readersDuringActiveChange_returnEmpty() throws Exception {
      ScheduleTaskMap map = newMap(retired);
      OrgIdChangeGuard.begin(retired);

      try {
         assertEquals(0, (int) onOtherThread(map::size));
         assertFalse(rootStored(map, retired));
      }
      finally {
         OrgIdChangeGuard.end(retired, false);
      }
   }

   // once the guard lifts, the next read heals the root as before and caches it
   @Test
   void afterGuardLifts_healWriteHappens() throws Exception {
      ScheduleTaskMap map = newMap(retired);
      retire(retired);
      assertEquals(0, (int) onOtherThread(map::size));

      OrgIdChangeGuard.clear(retired);
      assertEquals(0, (int) onOtherThread(map::size));
      assertTrue(rootStored(map, retired),
                 "root healed after the guard lifted");
      assertTrue(rootCache(map).containsKey(retired));
   }

   // writes to a guarded org still fail loudly
   @Test
   void putOnRetiredOrg_stillThrows() throws Exception {
      ScheduleTaskMap map = newMap(retired);
      retire(retired);
      ScheduleTask task = new ScheduleTask("Bug81319");
      task.addCondition(TimeCondition.at(1, 30, 0));
      String key = ReflectionTestUtils.invokeMethod(
         ScheduleManager.getScheduleManager(), "getTaskIdentifier", task.getTaskId(), retired);

      assertThrows(RuntimeException.class, () -> onOtherThread(() -> map.put(key, task)));
      assertFalse(rootStored(map, retired));
   }

   // an organization without a guard record keeps the #58866 heal
   @Test
   void unrelatedOrg_stillHeals() throws Exception {
      ScheduleTaskMap map = newMap(other);

      assertEquals(0, (int) onOtherThread(map::size));
      assertTrue(rootStored(map, other),
                 "root healed");
      assertTrue(rootCache(map).containsKey(other));
   }

   private ScheduleTaskMap newMap(String org) {
      ScheduleTaskMap map = new ScheduleTaskMap(org);
      // the root folder is missing from the storage, the #58866 corruption the heal write is for
      ReflectionTestUtils.setField(map, "indexedStorage", storage);
      return map;
   }

   /**
    * A blob indexed storage that reports the root folder as missing until it is written, and
    * records the writes. The write itself, and so the guard check, is the real one.
    */
   private static final class HealRecordingStorage extends BlobIndexedStorage {
      HealRecordingStorage(BlobStorageManager manager) {
         super(manager);
      }

      @Override
      public XMLSerializable getXMLSerializable(String key, TransformListener trans, String orgID)
         throws Exception
      {
         return written.get(key);
      }

      @Override
      public long lastModified(String key, String orgID) {
         return written.containsKey(key) ? 1L : 0L;
      }

      @Override
      public boolean isInitialized(String orgID) {
         return true;
      }

      @Override
      public void putXMLSerializable(String key, XMLSerializable value) throws Exception {
         super.putXMLSerializable(key, value);
         written.put(key, value);
      }

      final Map<String, XMLSerializable> written = new java.util.concurrent.ConcurrentHashMap<>();
   }

   private static void retire(String org) {
      OrgIdChangeGuard.begin(org);
      OrgIdChangeGuard.end(org, true);
   }

   private boolean rootStored(ScheduleTaskMap map, String org) {
      return storage.written.containsKey(rootId(map, org));
   }

   private static String rootId(ScheduleTaskMap map, String org) {
      return ReflectionTestUtils.invokeMethod(map, "getRootIdentifier", org);
   }

   @SuppressWarnings("unchecked")
   private static Map<String, AssetFolder> rootCache(ScheduleTaskMap map) {
      return (Map<String, AssetFolder>) ReflectionTestUtils.getField(map, "rootFolders");
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Long> rootTsCache(ScheduleTaskMap map) {
      return (Map<String, Long>) ReflectionTestUtils.getField(map, "rootTS");
   }

   /** Runs on another thread, as a request on another node does (the changing thread is exempt). */
   private static <T> T onOtherThread(Callable<T> action) throws Exception {
      AtomicReference<T> result = new AtomicReference<>();
      AtomicReference<Throwable> error = new AtomicReference<>();
      Thread t = new Thread(() -> {
         try {
            result.set(action.call());
         }
         catch(Throwable e) {
            error.set(e);
         }
      });
      t.start();
      t.join();

      if(error.get() != null) {
         if(error.get() instanceof RuntimeException e) {
            throw e;
         }

         throw new Exception(error.get());
      }

      return result.get();
   }
}
