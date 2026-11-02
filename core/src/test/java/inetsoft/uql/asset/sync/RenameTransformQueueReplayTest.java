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
package inetsoft.uql.asset.sync;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.Organization;
import inetsoft.storage.*;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #77897: the rename transform queue must be replayed after a full cluster restart, exactly
 * once, from the store the queue tasks write to.
 * <p>
 * A restart closes every key-value store, reopens the persisted engine, drops the replicated maps
 * (they live in memory only) and runs the {@code DependencyStorageService} start-up again. The
 * engine keeps values serialized, as a disk store does; if the MapDB store is on the test
 * classpath (for example through {@code -Dmaven.test.additionalClasspath}) the real
 * {@code MapDBKeyValueEngine} is used instead.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  RenameTransformQueueReplayTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RenameTransformQueueReplayTest {
   @BeforeEach
   void setUp() throws Exception {
      // start every test from an empty queue store and reverse index
      cluster().awaitTasks();
      closeStores();
      engine().deleteStorage(QUEUE_STORE);
      engine().deleteStorage(ORG_STORE);
      restart();
      cluster().renameSubmissionsTotal.set(0);

      DependenciesInfo dependencies = new DependenciesInfo();
      List<AssetObject> list = new ArrayList<>();
      list.add(DEPENDENT);
      dependencies.setDependencies(list);
      dss.put(OLD_WS, dependencies);

      logger = (Logger) LoggerFactory.getLogger(LoadRenameQueueTask.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void tearDown() throws Exception {
      logger.detachAppender(appender);
      cluster().dropRenameTransform = false;

      if(repository != null) {
         cluster().awaitTasks();
         AssetUtil.setAssetRepository(false, null);
         repository = null;
      }

      // the context closes the stores after the last test
      dss.awaitQueueOpen();
   }

   @Test
   void queuedRenameIsReplayedOnceAfterRestart() throws Exception {
      cluster().dropRenameTransform = true; // the server stops before the transform starts
      handler().addTransformTask(newTask(), true);
      cluster().dropRenameTransform = false;
      assertEquals(1, queueSize(), "the rename is queued");

      restart();

      assertEquals(1, renameSubmissions(), "the queued rename is replayed");
      assertNull(dss.get(OLD_WS), "the reverse index entry left the old name");
      assertNotNull(dss.get(NEW_WS), "the reverse index entry moved to the new name");
      assertEquals(0, queueSize(), "the replayed rename is dequeued");
      assertTrue(dss.getQueue().isEmpty());

      restart();
      assertEquals(0, renameSubmissions(), "not replayed again on the next restart");

      reopenWithoutRestart();
      assertEquals(0, renameSubmissions(), "not replayed again when the store is reopened");
   }

   @Test
   void storeReopenOnRunningClusterDoesNotReplay() throws Exception {
      cluster().dropRenameTransform = true; // the transform is still waiting to start
      handler().addTransformTask(newTask(), true);
      cluster().dropRenameTransform = false;

      reopenWithoutRestart(); // a second node, or the store evicted and opened again
      assertEquals(0, renameSubmissions(), "a pending rename is not started twice");
      assertEquals(1, queueSize());
   }

   @Test
   void legacyQueueIsLoggedAndDroppedNotReplayed() throws Exception {
      // what an older version left: the queue under the key without the version marker
      RenameTransformQueue legacy = new RenameTransformQueue();
      RenameDependencyInfo task = newTask();
      task.setRenameInfo(DEPENDENT, task.getRenameInfos());
      legacy.add(task);
      engine().put(QUEUE_STORE, LEGACY_QUEUE_KEY, legacy);

      restart();

      assertEquals(0, renameSubmissions(), "a legacy rename is not replayed");
      assertNotNull(dss.get(OLD_WS), "the reverse index is untouched");
      assertNull(dss.get(NEW_WS));
      assertFalse(engine().contains(QUEUE_STORE, LEGACY_QUEUE_KEY), "the legacy queue is removed");
      List<String> warnings = warnings();
      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).contains(OLD_WS + " -> " + NEW_WS), warnings.get(0));
      assertTrue(warnings.get(0).contains(ORG), warnings.get(0));
      assertTrue(warnings.get(0).contains(DEPENDENT.toIdentifier()), warnings.get(0));

      restart();
      assertEquals(1, warnings().size(), "logged once");
   }

   @Test
   void taskIsDroppedAfterThreeAttempts() throws Exception {
      // every start dies before the transform finishes
      cluster().dropRenameTransform = true;
      handler().addTransformTask(newTask(), true); // attempt 1
      restart(); // attempt 2
      restart(); // attempt 3
      assertEquals(3, renameSubmissionsTotal(), "started while under the cap");
      assertEquals(1, queueSize());
      assertTrue(warnings().isEmpty(), warnings().toString());

      restart();

      assertEquals(3, renameSubmissionsTotal(), "not started a fourth time");
      assertEquals(0, queueSize(), "dropped from the queue");
      List<String> warnings = warnings();
      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).contains("started 3 times"), warnings.get(0));
   }

   @Test
   void failingTransformIsStillDequeued() throws Exception {
      RenameDependencyInfo task = newTask();
      task.setRenameInfos(null); // renameDep throws on it

      handler().addTransformTask(task, true);
      cluster().awaitTasks();

      assertEquals(0, queueSize());
   }

   @Test
   void renameOfRemovedOrganizationIsSkipped() throws Exception {
      RenameDependencyInfo task = newTask("r77897-removed-org");
      cluster().dropRenameTransform = true;
      handler().addTransformTask(task, true);
      cluster().dropRenameTransform = false;
      Logger taskLogger = (Logger) LoggerFactory.getLogger(RenameTransformTask.class);
      taskLogger.addAppender(appender);

      try {
         restart();
      }
      finally {
         taskLogger.detachAppender(appender);
      }

      assertEquals(1, renameSubmissions());
      assertEquals(0, queueSize(), "dequeued");
      assertNotNull(dss.get(OLD_WS), "not transformed");
      assertTrue(warnings().stream().anyMatch(w -> w.contains("r77897-removed-org")),
                 warnings().toString());
   }

   @Test
   void removeWithoutQueueDoesNotThrow() {
      engine().remove(QUEUE_STORE, QUEUE_KEY);
      assertDoesNotThrow(() -> new RenameTransformTask.Remove(newTask()).run());
   }

   @Test
   void waitUntilRenameFinishedWaitsWhileTaskIsQueued() throws Exception {
      RenameDependencyInfo task = newTask();
      cluster().dropRenameTransform = true;
      handler().addTransformTask(task, true);
      cluster().dropRenameTransform = false;

      Thread wait = new Thread(() -> handler().waitUntilRenameFinished());
      wait.start();

      try {
         Thread.sleep(500L);
         assertTrue(wait.isAlive(), "waits while the rename is queued");

         // it polls every 5 s; seeing it still waiting is enough, so don't wait for the next poll
         cluster().submit(QUEUE_STORE, new RenameTransformTask.Remove(task))
            .get(10, TimeUnit.SECONDS);
      }
      finally {
         // stop it, or it reads the queue up to 5 s later, in a later test or class
         wait.interrupt();
         wait.join(10000L);
      }
   }

   @Test
   void queuedRenameIsReplayedByTheNextRenameAfterRestart() throws Exception {
      cluster().dropRenameTransform = true;
      handler().addTransformTask(newTask(), true);
      cluster().dropRenameTransform = false;

      restartWithoutOpeningQueue();
      RenameInfo other = new RenameInfo("1^2^__NULL__^OtherWS^" + ORG,
                                        "1^2^__NULL__^OtherWS2^" + ORG,
                                        RenameInfo.ASSET | RenameInfo.SOURCE);
      RenameDependencyInfo next = new RenameDependencyInfo();
      next.setRenameInfos(new ArrayList<>(List.of(other)));
      handler().addTransformTask(next, true);
      cluster().awaitTasks();

      assertEquals(2, renameSubmissions(), "the queued rename is replayed before the new one");
      assertNull(dss.get(OLD_WS));
      assertNotNull(dss.get(NEW_WS));
      assertEquals(0, queueSize());

      restart();
      assertEquals(0, renameSubmissions(), "not replayed again");
   }

   /**
    * DependencyStorageService is lazy, so on a node that hasn't used it yet the first rename
    * creates it on the renameTransform thread. A waiting rename holds the queue store's service
    * until that rename is done, so the creation must not wait on that service.
    */
   @Test
   void firstUseOnTheRenameThreadDoesNotWaitForTheQueueService() throws Exception {
      dss.awaitQueueOpen(); // the stores are closed on the rename thread below
      CountDownLatch go = new CountDownLatch(1);
      Future<?> firstUse = cluster().submit("renameTransform", new TestTask(() -> {
         try {
            go.await(10, TimeUnit.SECONDS);
         }
         catch(InterruptedException e) {
            throw new RuntimeException(e);
         }

         // this node has opened nothing yet: the bean is created here, on the rename thread
         manager.close();
         dss.initStorage(); // its @PostConstruct
      }));
      RenameDependencyInfo task = newTask();
      CompletableFuture<Void> waitingRename =
         CompletableFuture.runAsync(() -> handler().addTransformTask(task, true));

      // let the waiting rename take the queue store's service before the first use goes on
      Thread.sleep(500L);
      go.countDown();

      firstUse.get(5, TimeUnit.SECONDS);
      waitingRename.get(10, TimeUnit.SECONDS);
      cluster().awaitTasks();
      assertNotNull(dss.get(NEW_WS), "the rename is applied");
      assertEquals(0, queueSize());
   }

   @Test
   void nonWaitingRenameAfterStoreEvictionDoesNotBlock() throws Exception {
      // a waiting rename holds the queue store's service
      CountDownLatch release = new CountDownLatch(1);
      cluster().submit(QUEUE_STORE, new TestTask(() -> {
         try {
            release.await(10, TimeUnit.SECONDS);
         }
         catch(InterruptedException e) {
            throw new RuntimeException(e);
         }
      }));
      closeStores(); // the queue store evicted from the storage manager

      RenameDependencyInfo task = newTask();

      try {
         CompletableFuture.runAsync(() -> handler().addTransformTask(task, false))
            .get(2, TimeUnit.SECONDS);
      }
      finally {
         release.countDown();
      }

      cluster().awaitTasks();
      assertNotNull(dss.get(NEW_WS), "the rename is applied");
      assertEquals(0, queueSize());
   }

   /**
    * Bug #79519: a server stopping in the middle of a transform interrupts the renameTransform
    * service thread. The unfinished task must stay in the queue and be replayed at the next start.
    */
   @Test
   void interruptedTransformStaysQueuedAndIsReplayed() throws Exception {
      CountDownLatch entered = new CountDownLatch(1);
      AtomicBoolean workerInterrupted = new AtomicBoolean();
      AssetRepository repository = mockRepository();
      doAnswer(inv -> {
         entered.countDown();

         try {
            // the dependent takes until the stopping server interrupts the transform
            new CountDownLatch(1).await(30, TimeUnit.SECONDS);
         }
         catch(InterruptedException e) {
            workerInterrupted.set(true);
            throw e;
         }

         return null;
      }).when(repository).getStorage(any());
      RenameDependencyInfo task = newTaskWithDependent();
      cluster().dropRenameTransform = true;
      handler().addTransformTask(task, true);
      cluster().dropRenameTransform = false;

      Thread service = new Thread(() -> new RenameTransformTask.Rename(task).run(),
                                  "cluster-service-renameTransform");
      service.start();

      try {
         assertTrue(entered.await(10, TimeUnit.SECONDS), "the transform started");
         service.interrupt(); // Ignite cancels the service when the server stops
      }
      finally {
         service.join(20000L);
      }

      assertFalse(service.isAlive(), "the interrupted task returns");
      cluster().awaitTasks();
      assertEquals(1, queueSize(), "the unfinished task stays in the queue");
      assertTrue(workerInterrupted.get(), "the transform threads are stopped");

      // the next full start replays and finishes it; the dependent has nothing to rewrite
      doReturn(mock(IndexedStorage.class)).when(repository).getStorage(any());
      restart();

      assertEquals(1, renameSubmissions(), "replayed");
      assertEquals(0, queueSize(), "dequeued once finished");
      assertNotNull(dss.get(NEW_WS));
   }

   /**
    * Bug #79519: a stopping server may close the Spring context and the storage without
    * interrupting the renameTransform service thread. The dependent then fails fast and the
    * transform returns normally; the unfinished task must still stay in the queue, because the
    * context is closed, and be replayed at the next start.
    */
   @Test
   void transformFailingOnAClosedContextStaysQueuedAndIsReplayed() throws Exception {
      ConfigurationContext context = ConfigurationContext.getContext();
      ApplicationContext running = context.getApplicationContext();
      // closed, but its beans are still reachable, as they are through the bean cache of a
      // stopping server
      ConfigurableApplicationContext closed = mock(
         ConfigurableApplicationContext.class, AdditionalAnswers.delegatesTo(running));
      doReturn(true).when(closed).isClosed();
      AssetRepository repository = mockRepository();
      doAnswer(inv -> {
         // the server starts stopping in the middle of the transform
         context.setApplicationContext(closed);
         throw new IllegalStateException("storage closed");
      }).when(repository).getStorage(any());
      RenameDependencyInfo task = newTaskWithDependent();
      cluster().dropRenameTransform = true;
      handler().addTransformTask(task, true);
      cluster().dropRenameTransform = false;

      try {
         new RenameTransformTask.Rename(task).run();
         assertFalse(Thread.currentThread().isInterrupted(), "no interrupt involved");
      }
      finally {
         context.setApplicationContext(running);
      }

      verify(repository, atLeastOnce()).getStorage(any());
      cluster().awaitTasks();
      assertEquals(1, queueSize(), "the unfinished task stays in the queue");

      // the next full start replays and finishes it
      doReturn(mock(IndexedStorage.class)).when(repository).getStorage(any());
      restart();

      assertEquals(1, renameSubmissions(), "replayed");
      assertEquals(0, queueSize(), "dequeued once finished");
      assertNotNull(dss.get(NEW_WS));
   }

   /**
    * Bug #79521: the worksheet was renamed back to the old name after the task was queued, so the
    * replay of the task must not bind the dependents to the new name again.
    */
   @Test
   void replayOfARenamedBackTaskIsSkipped() throws Exception {
      AssetRepository repository = mockRepository();
      doReturn(mock(IndexedStorage.class)).when(repository).getStorage(any());
      doAnswer(inv -> isOldWs(inv.getArgument(0))).when(repository).containsEntry(any());
      RenameDependencyInfo task = newTaskWithDependent();
      cluster().dropRenameTransform = true;
      handler().addTransformTask(task, true);
      cluster().dropRenameTransform = false;
      Logger taskLogger = (Logger) LoggerFactory.getLogger(RenameTransformTask.class);
      taskLogger.addAppender(appender);

      try {
         restart();
      }
      finally {
         taskLogger.detachAppender(appender);
      }

      assertEquals(1, renameSubmissions(), "replayed");
      assertEquals(0, queueSize(), "the stale task is dequeued");
      assertNotNull(dss.get(OLD_WS), "the reverse index stays on the old name");
      assertNull(dss.get(NEW_WS));
      verify(repository, never()).getStorage(any());
      assertTrue(warnings().stream().anyMatch(w -> w.contains("exists again") &&
                                                  w.contains(OLD_WS)), warnings().toString());
   }

   @Test
   void replayIsAppliedWhenTheOldAssetCannotBeLookedUp() throws Exception {
      AssetRepository repository = mockRepository();
      doReturn(mock(IndexedStorage.class)).when(repository).getStorage(any());
      doThrow(new IllegalStateException("not ready")).when(repository).containsEntry(any());
      RenameDependencyInfo task = newTaskWithDependent();
      cluster().dropRenameTransform = true;
      handler().addTransformTask(task, true);
      cluster().dropRenameTransform = false;

      restart();

      assertEquals(1, renameSubmissions());
      assertEquals(0, queueSize());
      assertNull(dss.get(OLD_WS));
      assertNotNull(dss.get(NEW_WS), "applied");
      verify(repository, atLeastOnce()).getStorage(any());
   }

   /**
    * A live task always applies, even if the asset was renamed again after it was queued; only a
    * replay is checked.
    */
   @Test
   void liveTaskIsNotCheckedForStaleness() throws Exception {
      AssetRepository repository = mockRepository();
      doReturn(mock(IndexedStorage.class)).when(repository).getStorage(any());
      doReturn(true).when(repository).containsEntry(any());

      handler().addTransformTask(newTaskWithDependent(), true);
      cluster().awaitTasks();

      assertNotNull(dss.get(NEW_WS), "applied");
      assertEquals(0, queueSize());
      verify(repository, never()).containsEntry(any());
   }

   /**
    * Bug #79521: the reverse index moves before the dependents are rewritten, so that the ones
    * rewritten before a failure or stop are found under the new name by a later rename.
    */
   @Test
   void indexMovesBeforeTheDependentsAreRewritten() throws Exception {
      AssetRepository repository = mockRepository();
      AtomicBoolean movedFirst = new AtomicBoolean();
      doAnswer(inv -> {
         movedFirst.set(dss.get(NEW_WS) != null && dss.get(OLD_WS) == null);
         return mock(IndexedStorage.class);
      }).when(repository).getStorage(any());

      handler().addTransformTask(newTaskWithDependent(), true);
      cluster().awaitTasks();

      verify(repository, atLeastOnce()).getStorage(any());
      assertTrue(movedFirst.get(), "the index had moved when the dependent was rewritten");
      assertEquals(0, queueSize());
   }

   /**
    * Bug #79521, the reported sequence: the transform of OldWS -> NewWS is cut short by a stop,
    * NewWS is renamed back to OldWS while the first task is still queued, and the whole cluster
    * restarts. The rename back must find the dependents under NewWS, and the replay of the first
    * task must not bind them to NewWS again.
    */
   @Test
   void renameBackAfterAnInterruptedTransformIsNotUndoneByTheReplay() throws Exception {
      CountDownLatch entered = new CountDownLatch(1);
      AssetRepository repository = mockRepository();
      doAnswer(inv -> {
         entered.countDown();
         new CountDownLatch(1).await(30, TimeUnit.SECONDS); // until the stop interrupts it
         return null;
      }).when(repository).getStorage(any());
      RenameDependencyInfo first = newTaskWithDependent();
      cluster().dropRenameTransform = true;
      handler().addTransformTask(first, true);
      cluster().dropRenameTransform = false;

      Thread service = new Thread(() -> new RenameTransformTask.Rename(first).run(),
                                  "cluster-service-renameTransform");
      service.start();

      try {
         assertTrue(entered.await(10, TimeUnit.SECONDS), "the transform started");
         service.interrupt();
      }
      finally {
         service.join(20000L);
      }

      cluster().awaitTasks();
      assertEquals(1, queueSize(), "the interrupted task stays queued");
      // a later rename looks its dependents up under the new name
      List<AssetObject> found = DependencyTransformer.getDependencies(NEW_WS);
      assertTrue(found != null && found.contains(DEPENDENT), String.valueOf(found));

      // NewWS is renamed back to OldWS on a node that is still running, and that transform
      // finishes while the first task is still queued
      doReturn(mock(IndexedStorage.class)).when(repository).getStorage(any());
      doAnswer(inv -> isOldWs(inv.getArgument(0))).when(repository).containsEntry(any());
      RenameInfo back = new RenameInfo(NEW_WS, OLD_WS, RenameInfo.ASSET | RenameInfo.SOURCE);
      RenameDependencyInfo second = new RenameDependencyInfo();
      second.setRenameInfos(new ArrayList<>(List.of(back)));

      for(AssetObject dependent : found) {
         second.setRenameInfo(dependent, second.getRenameInfos());
      }

      handler().addTransformTask(second, true);
      cluster().awaitTasks();
      assertEquals(1, queueSize(), "only the interrupted task is left");
      assertNull(dss.get(NEW_WS), "the rename back moved the reverse index");
      assertNotNull(dss.get(OLD_WS));

      // the full cluster restart replays the first task; OldWS exists again
      Logger taskLogger = (Logger) LoggerFactory.getLogger(RenameTransformTask.class);
      taskLogger.addAppender(appender);

      try {
         restart();
      }
      finally {
         taskLogger.detachAppender(appender);
      }

      assertEquals(1, renameSubmissions(), "replayed");
      assertEquals(0, queueSize(), "the stale task is dequeued");
      assertNull(dss.get(NEW_WS), "the reverse index doesn't go back to NewWS");
      DependenciesInfo index = (DependenciesInfo) dss.get(OLD_WS);
      assertNotNull(index, "the reverse index is on OldWS again");
      assertTrue(index.getDependencies().contains(DEPENDENT));
      assertTrue(warnings().stream().anyMatch(w -> w.contains("exists again") &&
                                                  w.contains(OLD_WS)), warnings().toString());
   }

   /**
    * Bug #79721: OldWS is renamed to NewWS, the node stops with the dependents still bound to
    * OldWS, then NewWS is renamed to NewerWS on a node that is running. The later rename must
    * also move the dependents from OldWS, or the replay of the first rename binds them to the
    * NewWS that no longer exists.
    */
   @Test
   void renameChainedToAPendingRenameLeavesNoDependentOnTheMissingName() throws Exception {
      mockRepository();
      RenameDependencyInfo first = newTaskWithDependent();
      cluster().dropRenameTransform = true;
      handler().addTransformTask(first, true);
      // the node stopped after the index move, before any dependent was rewritten
      DependencyTransformer.renameDepStorage(first.getRenameInfos().get(0));
      List<AssetObject> found = DependencyTransformer.getDependencies(NEW_WS);
      assertTrue(found != null && found.contains(DEPENDENT), String.valueOf(found));

      RenameDependencyInfo second = newerTask(found);
      handler().addTransformTask(second, true);
      cluster().dropRenameTransform = false;
      assertEquals(2, queueSize());

      Map<AssetObject, String> bindings = new HashMap<>();
      bindings.put(DEPENDENT, OLD_WS);
      // the later rename runs and finishes first, as it does on the live node
      new RenameTransformTask.Rename(queued(second)).run(recorder(bindings), () -> false);
      cluster().awaitTasks();
      assertEquals(NEWER_WS, bindings.get(DEPENDENT), "bound to the new name");
      assertEquals(1, queueSize(), "only the first rename is left");

      // the cluster restart replays the first rename
      new RenameTransformTask.Rename(queued(first), true).run(recorder(bindings), () -> false);
      cluster().awaitTasks();
      assertEquals(NEWER_WS, bindings.get(DEPENDENT), "not rebound to the missing NewWS");
      assertEquals(0, queueSize());
      assertNull(dss.get(OLD_WS));
      assertNull(dss.get(NEW_WS));
      DependenciesInfo index = (DependenciesInfo) dss.get(NEWER_WS);
      assertNotNull(index, "the reverse index is on the last name");
      assertTrue(index.getDependencies().contains(DEPENDENT));
   }

   /**
    * Bug #79721: the node stopped before the index move too, so the later rename finds no
    * dependents under NewWS. It takes the dependents of the pending rename and moves the index.
    */
   @Test
   void renameChainedToAPendingRenameWhoseIndexMoveDidNotRunTakesItsDependents()
      throws Exception
   {
      mockRepository();
      RenameDependencyInfo first = newTaskWithDependent();
      cluster().dropRenameTransform = true;
      handler().addTransformTask(first, true);
      List<AssetObject> none = DependencyTransformer.getDependencies(NEW_WS);
      assertTrue(none == null || none.isEmpty(), String.valueOf(none));

      RenameDependencyInfo second = newerTask(List.of());
      handler().addTransformTask(second, true);
      cluster().dropRenameTransform = false;

      Map<AssetObject, String> bindings = new HashMap<>();
      bindings.put(DEPENDENT, OLD_WS);
      new RenameTransformTask.Rename(queued(second)).run(recorder(bindings), () -> false);
      new RenameTransformTask.Rename(queued(first), true).run(recorder(bindings), () -> false);
      cluster().awaitTasks();

      assertEquals(NEWER_WS, bindings.get(DEPENDENT));
      assertEquals(0, queueSize());
      assertNull(dss.get(OLD_WS));
      assertNull(dss.get(NEW_WS));
      DependenciesInfo index = (DependenciesInfo) dss.get(NEWER_WS);
      assertNotNull(index, "the reverse index moved to the last name");
      assertTrue(index.getDependencies().contains(DEPENDENT));
   }

   @Test
   void renamesThatAreNotAChainAreLeftAlone() {
      RenameInfo aToB = new RenameInfo(OLD_WS, NEW_WS, RenameInfo.ASSET | RenameInfo.SOURCE);
      RenameDependencyInfo pending = new RenameDependencyInfo();
      pending.setRenameInfos(new ArrayList<>(List.of(aToB)));
      pending.setRenameInfo(DEPENDENT, pending.getRenameInfos());
      RenameTransformQueue queue = new RenameTransformQueue();
      queue.add(pending);

      // a rename back, an unrelated asset, another kind of rename: nothing is added
      for(RenameInfo next : List.of(
         new RenameInfo(NEW_WS, OLD_WS, RenameInfo.ASSET | RenameInfo.SOURCE),
         new RenameInfo(NEWER_WS, OLD_WS, RenameInfo.ASSET | RenameInfo.SOURCE),
         new RenameInfo(NEW_WS, NEWER_WS, RenameInfo.ASSET | RenameInfo.TABLE),
         new RenameInfo(NEW_WS, NEWER_WS, RenameInfo.VIEWSHEET | RenameInfo.SOURCE)))
      {
         RenameDependencyInfo later = new RenameDependencyInfo();
         later.setRenameInfos(new ArrayList<>(List.of(next)));
         RenameTransformTask.chainPendingRenames(later, queue);
         assertEquals(1, later.getRenameInfos().size(), next.getOldName());
         assertTrue(later.getDependencyMap().isEmpty(), next.getOldName());
      }
   }

   private RenameDependencyInfo newerTask(List<AssetObject> dependents) {
      RenameInfo rinfo = new RenameInfo(NEW_WS, NEWER_WS, RenameInfo.ASSET | RenameInfo.SOURCE);
      RenameDependencyInfo task = new RenameDependencyInfo();
      task.setRenameInfos(new ArrayList<>(List.of(rinfo)));

      for(AssetObject dependent : dependents) {
         task.setRenameInfo(dependent, task.getRenameInfos());
      }

      return task;
   }

   /**
    * Gets the queued copy of a task, which the chaining changed.
    */
   private RenameDependencyInfo queued(RenameDependencyInfo task) {
      RenameTransformQueue queue = engine().get(QUEUE_STORE, QUEUE_KEY);
      return queue.stream().filter(task::equals).findFirst().orElseThrow();
   }

   /**
    * A transform that rewrites the way the real one does: a binding is replaced if it equals the
    * old name of a rename of its dependent.
    */
   private static Function<RenameDependencyInfo, CompletableFuture<Boolean>> recorder(
      Map<AssetObject, String> bindings)
   {
      return info -> {
         for(AssetObject dependent : info.getAssetObjects()) {
            for(RenameInfo rinfo : info.getRenameInfo(dependent)) {
               if(rinfo.getOldName().equals(bindings.get(dependent))) {
                  bindings.put(dependent, rinfo.getNewName());
               }
            }
         }

         return CompletableFuture.completedFuture(true);
      };
   }

   @Test
   void attemptsRoundTripThroughJson() throws Exception {
      RenameTransformAttempts attempts = new RenameTransformAttempts();
      attempts.set("a", 2);
      ObjectMapper mapper = KeyValueEngine.createObjectMapper();
      RenameTransformAttempts copy = mapper.readValue(
         mapper.writeValueAsString(attempts), RenameTransformAttempts.class);
      assertEquals(2, copy.get("a"));
      assertEquals(0, copy.get("b"));
   }

   private void restart() throws Exception {
      cluster().awaitTasks();
      closeStores();
      engine().reopen();
      cluster().destroyReplicatedMap("inetsoft.storage.kv." + QUEUE_STORE);
      cluster().destroyReplicatedMap("inetsoft.storage.kv." + ORG_STORE);
      cluster().resetSubmissions();
      dss.initStorage();
      // initStorage() opens the queue store without waiting; open it here to replay now
      dss.getQueue();
      cluster().awaitTasks();
   }

   /**
    * A restart after which nothing opens the queue store, so that the next queue change has to
    * replay the queue itself.
    */
   private void restartWithoutOpeningQueue() throws Exception {
      cluster().awaitTasks();
      closeStores();
      engine().reopen();
      cluster().destroyReplicatedMap("inetsoft.storage.kv." + QUEUE_STORE);
      cluster().destroyReplicatedMap("inetsoft.storage.kv." + ORG_STORE);
      cluster().resetSubmissions();
   }

   private void reopenWithoutRestart() throws Exception {
      cluster().awaitTasks();
      closeStores();
      cluster().resetSubmissions();
      dss.initStorage();
      dss.getQueue();
      cluster().awaitTasks();
   }

   /**
    * Closes every key-value store. Waits first for the queue store open that the last
    * {@code initStorage()} started without waiting: if it ran on past the close, it would reopen
    * the store bound to the replicated map that a restart then drops, so the restart would neither
    * replay the queue nor see its changes (Bug #78008).
    */
   private void closeStores() throws Exception {
      dss.awaitQueueOpen();
      manager.close();
   }

   private RenameDependencyInfo newTask() throws Exception {
      return newTask(null);
   }

   private RenameDependencyInfo newTask(String org) throws Exception {
      RenameInfo rinfo = new RenameInfo(OLD_WS, NEW_WS, RenameInfo.ASSET | RenameInfo.SOURCE);

      if(org != null) {
         StringWriter xml = new StringWriter();
         PrintWriter writer = new PrintWriter(xml);
         rinfo.writeXML(writer);
         writer.flush();
         Document doc = Tool.parseXML(new StringReader(xml.toString().replace(
            "organizationId=\"" + ORG + "\"", "organizationId=\"" + org + "\"")));
         rinfo = new RenameInfo();
         rinfo.parseXML(doc.getDocumentElement());
      }

      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      List<RenameInfo> rinfos = new ArrayList<>();
      rinfos.add(rinfo);
      dinfo.setRenameInfos(rinfos);
      return dinfo;
   }

   private RenameDependencyInfo newTaskWithDependent() throws Exception {
      RenameDependencyInfo task = newTask();
      task.setRenameInfo(DEPENDENT, task.getRenameInfos());
      return task;
   }

   /**
    * Installs a mock runtime asset repository, which the transform and the replay check use.
    */
   private AssetRepository mockRepository() {
      repository = mock(AssetRepository.class);
      AssetUtil.setAssetRepository(false, repository);
      return repository;
   }

   private static boolean isOldWs(AssetEntry entry) {
      return entry != null && "OldWS".equals(entry.getPath()) && ORG.equals(entry.getOrgID()) &&
         entry.getScope() == AssetRepository.GLOBAL_SCOPE;
   }

   private int queueSize() {
      RenameTransformQueue queue = engine().get(QUEUE_STORE, QUEUE_KEY);
      return queue == null ? 0 : queue.size();
   }

   private long renameSubmissions() {
      return cluster().submissions.stream().filter(s -> s.startsWith("renameTransform:")).count();
   }

   private long renameSubmissionsTotal() {
      return cluster().renameSubmissionsTotal.get();
   }

   private List<String> warnings() {
      return appender.list.stream()
         .filter(e -> e.getLevel() == Level.WARN)
         .map(ILoggingEvent::getFormattedMessage)
         .collect(Collectors.toList());
   }

   private RenameTransformHandler handler() {
      return new RenameTransformHandler(clusterBean, dss);
   }

   private RecordingCluster cluster() {
      return (RecordingCluster) clusterBean;
   }

   private RestartableEngine engine() {
      return (RestartableEngine) engineBean;
   }

   @Autowired
   private Cluster clusterBean;
   @Autowired
   private KeyValueEngine engineBean;
   @Autowired
   private KeyValueStorageManager manager;
   @Autowired
   private DependencyStorageService dss;
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;
   // held here: the configuration context keeps only a weak reference
   private AssetRepository repository;

   private static final String QUEUE_STORE = "dependencyStorage";
   private static final String QUEUE_KEY = "1^0^__NULL__^rename_queue_v2";
   private static final String LEGACY_QUEUE_KEY = "1^0^__NULL__^rename_queue";
   private static final String ORG = Organization.getDefaultOrganizationID();
   private static final String ORG_STORE = ORG.toLowerCase() + "__dependencyStorage";
   private static final String OLD_WS = "1^2^__NULL__^OldWS^" + ORG;
   private static final String NEW_WS = "1^2^__NULL__^NewWS^" + ORG;
   private static final String NEWER_WS = "1^2^__NULL__^NewerWS^" + ORG;
   private static final AssetEntry DEPENDENT = new AssetEntry(
      AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "DependentVS", null, ORG);

   @Configuration
   static class Config {
      @Bean
      @Primary
      public Cluster recordingCluster() {
         return new RecordingCluster();
      }

      @Bean
      @Primary
      public KeyValueEngine restartableEngine() throws Exception {
         return new RestartableEngine(Files.createTempDirectory("kv77897"));
      }

      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager manager) {
         return new DependencyStorageService(manager);
      }
   }

   static final class TestTask implements SingletonRunnableTask {
      TestTask(Runnable body) {
         this.body = body;
      }

      @Override
      public void run() {
         body.run();
      }

      private final transient Runnable body;
   }

   /**
    * Records the singleton submissions and can drop the {@code renameTransform} ones, which stands
    * for the cluster stopping before the transform started (the task requests live in memory).
    */
   static class RecordingCluster extends MockCluster {
      @Override
      public Future<?> submit(String serviceId, SingletonRunnableTask task) {
         submissions.add(serviceId + ":" + task.getClass().getSimpleName());

         if("renameTransform".equals(serviceId)) {
            renameSubmissionsTotal.incrementAndGet();

            if(dropRenameTransform) {
               return CompletableFuture.completedFuture(null);
            }
         }

         Future<?> future = super.submit(serviceId, task);
         futures.add(future);
         return future;
      }

      void resetSubmissions() {
         submissions.clear();
      }

      /**
       * Waits for the submitted tasks, including the ones they submit.
       */
      void awaitTasks() throws Exception {
         int done = 0;

         while(done < futures.size()) {
            try {
               futures.get(done).get(30, TimeUnit.SECONDS);
            }
            catch(ExecutionException ignore) {
               // a failing task is part of some tests
            }

            done++;
         }
      }

      volatile boolean dropRenameTransform;
      final List<String> submissions = new CopyOnWriteArrayList<>();
      final List<Future<?>> futures = new CopyOnWriteArrayList<>();
      final java.util.concurrent.atomic.AtomicLong renameSubmissionsTotal =
         new java.util.concurrent.atomic.AtomicLong();
   }

   /**
    * A persisted engine that can be closed and opened again. Uses the MapDB store when it is on
    * the classpath, otherwise keeps every value Java-serialized in memory, so that no object is
    * shared across a restart.
    */
   static class RestartableEngine implements KeyValueEngine {
      RestartableEngine(Path dir) throws Exception {
         this.dir = dir;
         reopen();
      }

      void reopen() throws Exception {
         if(delegate != null) {
            delegate.close();
         }

         try {
            delegate = (KeyValueEngine) Class.forName("inetsoft.storage.mapdb.MapDBKeyValueEngine")
               .getConstructor(Path.class).newInstance(dir);
         }
         catch(ClassNotFoundException e) {
            delegate = serialized;
         }
      }

      @Override
      public boolean contains(String id, String key) {
         return delegate.contains(id, key);
      }

      @Override
      public <T> T get(String id, String key) {
         return delegate.get(id, key);
      }

      @Override
      public <T> T put(String id, String key, T value) {
         return delegate.put(id, key, value);
      }

      @Override
      public <T> T remove(String id, String key) {
         return delegate.remove(id, key);
      }

      @Override
      public long size(String id) {
         return delegate.size(id);
      }

      @Override
      public void deleteStorage(String id) {
         delegate.deleteStorage(id);
      }

      @Override
      public <T> Stream<KeyValuePair<T>> stream(String id) {
         return delegate.stream(id);
      }

      @Override
      public Stream<String> idStream() {
         return delegate.idStream();
      }

      @Override
      public void close() throws Exception {
         delegate.close();

         try(Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
         }
      }

      private final Path dir;
      private final SerializedEngine serialized = new SerializedEngine();
      private volatile KeyValueEngine delegate;
   }

   static class SerializedEngine implements KeyValueEngine {
      @Override
      public boolean contains(String id, String key) {
         return store(id).containsKey(key);
      }

      @Override
      public <T> T get(String id, String key) {
         return read(store(id).get(key));
      }

      @Override
      public <T> T put(String id, String key, T value) {
         return read(store(id).put(key, write(value)));
      }

      @Override
      public <T> T remove(String id, String key) {
         return read(store(id).remove(key));
      }

      @Override
      public long size(String id) {
         return store(id).size();
      }

      @Override
      public void deleteStorage(String id) {
         stores.remove(id);
      }

      @Override
      public <T> Stream<KeyValuePair<T>> stream(String id) {
         return store(id).entrySet().stream()
            .map(e -> new KeyValuePair<>(e.getKey(), this.<T>read(e.getValue())));
      }

      @Override
      public Stream<String> idStream() {
         return stores.keySet().stream();
      }

      @Override
      public void close() {
         // the content is kept: it stands for the files of a disk store
      }

      private Map<String, byte[]> store(String id) {
         return stores.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
      }

      private static byte[] write(Object value) {
         ByteArrayOutputStream buffer = new ByteArrayOutputStream();

         try(ObjectOutputStream output = new ObjectOutputStream(buffer)) {
            output.writeObject(value);
         }
         catch(IOException e) {
            throw new UncheckedIOException(e);
         }

         return buffer.toByteArray();
      }

      @SuppressWarnings("unchecked")
      private <T> T read(byte[] data) {
         if(data == null) {
            return null;
         }

         try(ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (T) input.readObject();
         }
         catch(IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
         }
      }

      private final Map<String, Map<String, byte[]>> stores = new ConcurrentHashMap<>();
   }
}
