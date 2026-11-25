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

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.TransformListener;
import inetsoft.util.XMLSerializable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78027, #81785: ScheduleTaskMap trusts its cached task while the last-modified time of the stored
 * task (epoch milliseconds of the blob commit) is unchanged. A put of an existing task reads, and
 * so caches, the old task before it writes the new one. When the new write commits in the same
 * millisecond as the old one, the stored time doesn't change and the old task kept being returned
 * after the new one was saved. The storage here reports the commit time of the first write for
 * the second write too, which is what two commits in the same millisecond look like.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  ScheduleTaskMapCacheTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleTaskMapCacheTest {
   private static final String ORG = "stmcorg";
   private static final String ORG2 = "stmcorg2";
   private static final IdentityID ALICE = new IdentityID("alice", ORG);
   private static final IdentityID ALICE2 = new IdentityID("alice", ORG2);
   private static final String TARGET = "alice~;~" + ORG + ":StmcTarget";

   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SameMillisecondStorage storage;

   private Principal savedPrincipal;
   private final List<BlockedReader> readers = new ArrayList<>();

   @BeforeEach
   void setUp() {
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void tearDown() throws Exception {
      storage.unfreeze();

      try {
         for(BlockedReader reader : readers) {
            reader.thread.join(10000);
         }
      }
      finally {
         ThreadContext.setContextPrincipal(savedPrincipal);
         taskMap().clear();
         taskMap().clearCache();
         taskMap(ORG2).clear();
         taskMap(ORG2).clearCache();
      }

      for(BlockedReader reader : readers) {
         assertFalse(reader.thread.isAlive(), "a reader thread is still running");
      }
   }

   // the reported failure (BatchActionTargetPermissionTest stores a task, then replaces it)
   @Test
   void replacingPut_inTheSameMillisecond_getReturnsTheNewTask() {
      ScheduleTask first = newTask("StmcReplace", null);
      String key = key(first.getTaskId());
      taskMap().put(key, first);
      storage.freeze(key);

      taskMap().put(key, newTask("StmcReplace", TARGET));

      ScheduleTask stored = (ScheduleTask) assertDoesNotThrow(
         () -> storage.getXMLSerializable(key, null, ORG));
      assertEquals(List.of(TARGET), targets(stored), "the new task is in the storage");
      assertEquals(List.of(TARGET), targets(taskMap().get(key)), "the task read back");
   }

   // two saves of one task, each one a whole ScheduleManager save
   @Test
   void secondSave_inTheSameMillisecond_isReadBack() throws Exception {
      ScheduleTask first = newTask("StmcResave", null);
      String taskId = first.getTaskId();
      scheduleManager.setScheduleTask(taskId, first, principal());
      storage.freeze(key(taskId));

      scheduleManager.setScheduleTask(taskId, newTask("StmcResave", TARGET), principal());

      assertEquals(List.of(TARGET), targets(scheduleManager.getScheduleTask(taskId, ORG)),
                   "the task read back");
   }

   // ---- a reader that loaded the old task is held while the writer replaces it ----
   // The reader thread is blocked right after it loaded the task from the storage and before it
   // stores the loaded task in the cache; the writer then completes its put on another thread.

   @Test
   void replacingPut_whileAReaderHoldsTheOldTask_inTheSameMillisecond_getReturnsTheNewTask()
      throws Exception
   {
      ScheduleTask first = newTask("StmcHeld", version(1));
      String key = key(first.getTaskId());
      taskMap().put(key, first);
      storage.freeze(key);
      BlockedReader reader = startBlockedReader(taskMap(), key);

      taskMap().put(key, newTask("StmcHeld", version(2)));
      reader.finish();

      assertEquals(List.of(version(1)), targets(reader.result),
                   "the reader that was already loading still returns what it loaded");
      assertEquals(List.of(version(2)), targets(taskMap().get(key)), "the task read back");
   }

   @Test
   void replacingPut_whileAReaderHoldsTheOldTask_inALaterMillisecond_getReturnsTheNewTask()
      throws Exception
   {
      ScheduleTask first = newTask("StmcLater", version(1));
      String key = key(first.getTaskId());
      taskMap().put(key, first);
      BlockedReader reader = startBlockedReader(taskMap(), key);
      long written = storage.lastModified(key, ORG);
      await().pollInterval(Duration.ofMillis(1)).atMost(Duration.ofSeconds(5))
         .until(() -> System.currentTimeMillis() > written + 1);

      taskMap().put(key, newTask("StmcLater", version(2)));
      reader.finish();

      assertEquals(List.of(version(2)), targets(taskMap().get(key)), "the task read back");
   }

   @Test
   void twoGetsWithNoWriter_theSecondOneIsServedFromTheCache() throws Exception {
      ScheduleTask first = newTask("StmcCached", version(1));
      String key = key(first.getTaskId());
      taskMap().put(key, first);

      assertEquals(List.of(version(1)), targets(taskMap().get(key)));
      int loads = storage.loadCount(key);
      assertEquals(List.of(version(1)), targets(taskMap().get(key)));

      assertEquals(loads, storage.loadCount(key),
                   "an unchanged task must not be loaded from the storage again");
   }

   @Test
   void twoPutsWhileAReaderHoldsTheOldTask_inTheSameMillisecond_getReturnsTheLastTask()
      throws Exception
   {
      ScheduleTask first = newTask("StmcTwoPuts", version(1));
      String key = key(first.getTaskId());
      taskMap().put(key, first);
      storage.freeze(key);
      BlockedReader reader = startBlockedReader(taskMap(), key);

      taskMap().put(key, newTask("StmcTwoPuts", version(2)));
      taskMap().put(key, newTask("StmcTwoPuts", version(3)));
      reader.finish();

      assertEquals(List.of(version(3)), targets(taskMap().get(key)), "the task read back");
   }

   @Test
   void readersLoopWhileTheTaskIsReplaced_noReaderGoesBackToAnOlderVersion_lastGetIsTheLast()
      throws Exception
   {
      int versions = 20;
      ScheduleTask first = newTask("StmcLoop", version(1));
      String key = key(first.getTaskId());
      taskMap().put(key, first);
      AtomicBoolean done = new AtomicBoolean();
      // the readers stop after 3 s even if the writer is not done, so that they cannot keep the
      // writer from finishing
      long readersEnd = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      List<String> problems = new CopyOnWriteArrayList<>();
      List<Thread> threads = new ArrayList<>();

      for(int i = 0; i < 3; i++) {
         Thread thread = new Thread(() -> {
            int seen = 0;

            while(!done.get() && System.nanoTime() < readersEnd) {
               try {
                  ScheduleTask task = taskMap().get(key);
                  int current = task == null ? -1 : versionOf(targets(task).get(0));

                  if(current < seen) {
                     problems.add("read version " + current + " after version " + seen);
                  }

                  seen = Math.max(seen, current);
               }
               catch(Throwable e) {
                  problems.add(String.valueOf(e));
               }
            }
         }, "stmc-reader-" + i);
         threads.add(thread);
         thread.start();
      }

      try {
         for(int v = 2; v <= versions; v++) {
            awaitLaterMillisecond(key);
            taskMap().put(key, newTask("StmcLoop", version(v)));
         }
      }
      finally {
         done.set(true);

         for(Thread thread : threads) {
            thread.join(10000);
            assertFalse(thread.isAlive(), "a reader thread is still running");
         }
      }

      assertTrue(problems.isEmpty(), "readers went back in time: " + problems);
      assertEquals(List.of(version(versions)), targets(taskMap().get(key)), "the last get");
   }

   @Test
   void getOfAnAbsentTaskWhilePutCreatesIt_getReturnsTheCreatedTask() throws Exception {
      ScheduleTask created = newTask("StmcAbsent", version(1));
      String key = key(created.getTaskId());
      BlockedReader reader = startBlockedReader(taskMap(), key);

      taskMap().put(key, created);
      reader.finish();

      assertNull(reader.result, "the reader that was already loading found no task");
      assertEquals(List.of(version(1)), targets(taskMap().get(key)), "the task read back");
   }

   @Test
   void removeWhileAReaderHoldsTheTask_getReturnsNull() throws Exception {
      ScheduleTask first = newTask("StmcRemoved", version(1));
      String key = key(first.getTaskId());
      taskMap().put(key, first);
      BlockedReader reader = startBlockedReader(taskMap(), key);

      taskMap().remove(key);
      reader.finish();

      assertNull(taskMap().get(key), "the removed task must not come back from the cache");
   }

   // ScheduleManager.getScheduleTask is synchronized like setScheduleTask, so a reader cannot be
   // held inside the manager; the held reader reads the task map that the manager save writes to
   @Test
   void managerSaveWhileAReaderHoldsTheOldTask_inTheSameMillisecond_managerReadsTheNewTask()
      throws Exception
   {
      ThreadContext.setContextPrincipal(principal());
      ScheduleTask first = newTask("StmcManager", version(1));
      String taskId = first.getTaskId();
      scheduleManager.setScheduleTask(taskId, first, principal());
      storage.freeze(key(taskId));
      BlockedReader reader = startBlockedReader(taskMap(), key(taskId));

      scheduleManager.setScheduleTask(taskId, newTask("StmcManager", version(2)), principal());
      reader.finish();

      assertEquals(List.of(version(2)), targets(scheduleManager.getScheduleTask(taskId, ORG)),
                   "the task read back");
   }

   @Test
   void sameShortNameInTwoOrganizationsReplacedInTheSameMillisecond_eachOrganizationReadsItsOwn()
      throws Exception
   {
      ScheduleTask firstA = newTask("StmcTwoOrgs", version(1), ALICE);
      ScheduleTask firstB = newTask("StmcTwoOrgs", version(10), ALICE2);
      String keyA = key(firstA.getTaskId(), ORG);
      String keyB = key(firstB.getTaskId(), ORG2);
      assertNotEquals(keyA, keyB);
      taskMap(ORG).put(keyA, firstA);
      taskMap(ORG2).put(keyB, firstB);
      storage.freeze(keyA, ORG);
      storage.freeze(keyB, ORG2);
      BlockedReader readerA = startBlockedReader(taskMap(ORG), keyA);
      BlockedReader readerB = startBlockedReader(taskMap(ORG2), keyB);

      taskMap(ORG).put(keyA, newTask("StmcTwoOrgs", version(2), ALICE));
      taskMap(ORG2).put(keyB, newTask("StmcTwoOrgs", version(20), ALICE2));
      readerA.finish();
      readerB.finish();

      assertEquals(List.of(version(2)), targets(taskMap(ORG).get(keyA)), "organization A");
      assertEquals(List.of(version(20)), targets(taskMap(ORG2).get(keyB)), "organization B");
   }

   /** Waits until the clock is past the millisecond of the last write of the key. */
   private void awaitLaterMillisecond(String key) {
      long written = storage.lastModified(key, ORG);
      await().pollInterval(Duration.ofMillis(1)).atMost(Duration.ofSeconds(5))
         .until(() -> System.currentTimeMillis() > written + 1);
   }

   private static String version(int version) {
      return TARGET + "-v" + version;
   }

   private static int versionOf(String target) {
      return Integer.parseInt(target.substring(target.lastIndexOf("-v") + 2));
   }

   /** Starts a thread that reads the key and holds it right after the storage load. */
   private BlockedReader startBlockedReader(ScheduleTaskMap map, String key)
      throws InterruptedException
   {
      BlockedReader reader = new BlockedReader();
      reader.thread = new Thread(() -> {
         try {
            reader.result = map.get(key);
         }
         catch(Throwable e) {
            reader.failure = e;
         }
      }, "stmc-held-reader");
      readers.add(reader);
      reader.arm = storage.blockReaderAfterLoad(key, reader.thread);
      reader.thread.start();
      assertTrue(reader.arm.entered.await(10, TimeUnit.SECONDS),
                 "the reader never reached the load");
      return reader;
   }

   private static final class BlockedReader {
      Thread thread;
      SameMillisecondStorage.BlockedRead arm;
      volatile ScheduleTask result;
      volatile Throwable failure;

      /** Lets the reader finish and waits for it. */
      void finish() throws InterruptedException {
         arm.release.countDown();
         thread.join(10000);
         assertFalse(thread.isAlive(), "the reader did not finish");
         assertNull(failure, "the reader failed: " + failure);
      }
   }

   private ScheduleTaskMap taskMap() {
      return taskMap(ORG);
   }

   private ScheduleTaskMap taskMap(String org) {
      return scheduleManager.getOrgTaskMap(org);
   }

   private String key(String taskId) {
      return key(taskId, ORG);
   }

   private String key(String taskId, String org) {
      return ReflectionTestUtils.invokeMethod(scheduleManager, "getTaskIdentifier", taskId, org);
   }

   private static ScheduleTask newTask(String name, String target) {
      return newTask(name, target, ALICE);
   }

   private static ScheduleTask newTask(String name, String target, IdentityID owner) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(owner);
      // saved without the schedule permission check, as ScheduleTaskOwnerOrgIdTest does
      task.setRemovable(false);
      // a task without a condition isn't loaded (ScheduleTask.parseXML)
      task.addCondition(TimeCondition.at(1, 30, 0));

      if(target != null) {
         BatchAction batch = new BatchAction();
         batch.setTaskId(target);
         task.addAction(batch);
      }

      return task;
   }

   private static List<String> targets(ScheduleTask task) {
      assertNotNull(task, "the task must not be lost");
      List<String> targets = new ArrayList<>();

      for(int i = 0; i < task.getActionCount(); i++) {
         if(task.getAction(i) instanceof BatchAction batch) {
            targets.add(batch.getTaskId());
         }
      }

      return targets;
   }

   private static SRPrincipal principal() {
      SRPrincipal principal = new SRPrincipal(ALICE, new IdentityID[0], new String[0], ORG,
                                              Tool.getSecureRandom().nextLong());
      principal.setIgnoreLogin(true);
      return principal;
   }

   /**
    * Indexed storage that can keep reporting the last-modified time a key had when it was
    * frozen, as if every later write of the key committed in the same millisecond. It can also
    * hold one given thread right after it loaded a key (see {@link #blockReaderAfterLoad}), and
    * counts the loads of every key.
    */
   static class SameMillisecondStorage extends BlobIndexedStorage {
      SameMillisecondStorage(BlobStorageManager blobStorageManager) {
         super(blobStorageManager);
      }

      void freeze(String key) {
         freeze(key, ORG);
      }

      void freeze(String key, String org) {
         frozen.put(key, super.lastModified(key, org));
      }

      /** Ends every freeze and lets every held reader go on. */
      void unfreeze() {
         frozen.clear();

         for(BlockedRead read : blocked.values()) {
            read.release.countDown();
         }

         blocked.clear();
      }

      /**
       * Holds the given thread inside its next {@code getXMLSerializable} of the key, after the
       * value was loaded, until the returned handle is released. Other threads are not held.
       */
      BlockedRead blockReaderAfterLoad(String key, Thread reader) {
         BlockedRead read = new BlockedRead(reader);
         blocked.put(key, read);
         return read;
      }

      int loadCount(String key) {
         AtomicInteger count = loads.get(key);
         return count == null ? 0 : count.get();
      }

      @Override
      public long lastModified(String key, String orgID) {
         Long ts = key == null ? null : frozen.get(key);
         return ts != null ? ts : super.lastModified(key, orgID);
      }

      @Override
      public XMLSerializable getXMLSerializable(String key, TransformListener trans, String orgID)
         throws Exception
      {
         loads.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
         XMLSerializable value = super.getXMLSerializable(key, trans, orgID);
         BlockedRead read = blocked.get(key);

         if(read != null && read.reader == Thread.currentThread() && blocked.remove(key, read)) {
            read.entered.countDown();
            read.release.await(30, TimeUnit.SECONDS);
         }

         return value;
      }

      static final class BlockedRead {
         BlockedRead(Thread reader) {
            this.reader = reader;
         }

         final Thread reader;
         final CountDownLatch entered = new CountDownLatch(1);
         final CountDownLatch release = new CountDownLatch(1);
      }

      private final Map<String, Long> frozen = new ConcurrentHashMap<>();
      private final Map<String, BlockedRead> blocked = new ConcurrentHashMap<>();
      private final Map<String, AtomicInteger> loads = new ConcurrentHashMap<>();
   }

   @Configuration
   static class Config {
      // replaces the IntegrationTestConfiguration bean of the same name
      @Bean
      public SameMillisecondStorage indexedStorage(BlobStorageManager blobStorageManager) {
         return new SameMillisecondStorage(blobStorageManager);
      }
   }
}
