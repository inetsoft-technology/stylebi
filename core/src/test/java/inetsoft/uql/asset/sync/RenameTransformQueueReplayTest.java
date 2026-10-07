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
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

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
      manager.close();
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
   void tearDown() {
      logger.detachAppender(appender);
      cluster().dropRenameTransform = false;
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

      CompletableFuture<Void> wait =
         CompletableFuture.runAsync(() -> handler().waitUntilRenameFinished());
      Thread.sleep(500L);
      assertFalse(wait.isDone(), "waits while the rename is queued");

      // it polls every 5 s; seeing it still waiting is enough, so don't wait for the next poll
      cluster().submit(QUEUE_STORE, new RenameTransformTask.Remove(task)).get(10, TimeUnit.SECONDS);
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
      manager.close(); // the queue store evicted from the storage manager

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
      manager.close();
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
      manager.close();
      engine().reopen();
      cluster().destroyReplicatedMap("inetsoft.storage.kv." + QUEUE_STORE);
      cluster().destroyReplicatedMap("inetsoft.storage.kv." + ORG_STORE);
      cluster().resetSubmissions();
   }

   private void reopenWithoutRestart() throws Exception {
      cluster().awaitTasks();
      manager.close();
      cluster().resetSubmissions();
      dss.initStorage();
      dss.getQueue();
      cluster().awaitTasks();
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

   private static final String QUEUE_STORE = "dependencyStorage";
   private static final String QUEUE_KEY = "1^0^__NULL__^rename_queue_v2";
   private static final String LEGACY_QUEUE_KEY = "1^0^__NULL__^rename_queue";
   private static final String ORG = Organization.getDefaultOrganizationID();
   private static final String ORG_STORE = ORG.toLowerCase() + "__dependencyStorage";
   private static final String OLD_WS = "1^2^__NULL__^OldWS^" + ORG;
   private static final String NEW_WS = "1^2^__NULL__^NewWS^" + ORG;
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
