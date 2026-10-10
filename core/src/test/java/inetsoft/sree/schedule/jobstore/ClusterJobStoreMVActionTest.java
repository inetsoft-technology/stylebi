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
package inetsoft.sree.schedule.jobstore;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.internal.cluster.ignite.*;
import inetsoft.sree.schedule.*;
import inetsoft.sree.schedule.quartz.ScheduleTaskJob;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.ConfigurationContext;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.*;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.spi.SchedulerSignaler;
import org.springframework.context.ApplicationContext;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78240: a task that the scheduler receives from the server over RMI holds deserialized
 * MV actions, which load their MV lazily. Storing the task's job serializes the actions in the
 * job store's transaction, so the serialization must not load the MV, which reads a cache and
 * makes Ignite fail the transaction. Runs on one embedded Ignite node with the product's
 * replicated cache configuration; loading an MV reads a cache of the node.
 */
@Tag("core")
class ClusterJobStoreMVActionTest {
   @BeforeAll
   static void startIgnite(@TempDir Path workDir) {
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("ClusterJobStoreMVActionTest");
      config.setWorkDirectory(workDir.toString());
      config.setMetricsLogFrequency(0);
      IgniteUtils.configBinaryTypes(config);

      int[] ports = freePorts(2);
      TcpDiscoverySpi discovery = new TcpDiscoverySpi();
      discovery.setLocalAddress("127.0.0.1");
      discovery.setLocalPort(ports[0]);
      discovery.setLocalPortRange(0);
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(List.of("127.0.0.1:" + ports[0]));
      discovery.setIpFinder(ipFinder);
      config.setDiscoverySpi(discovery);

      TcpCommunicationSpi communication = new TcpCommunicationSpi();
      communication.setLocalAddress("127.0.0.1");
      communication.setLocalPort(ports[1]);
      communication.setLocalPortRange(0);
      config.setCommunicationSpi(communication);

      DataStorageConfiguration storage = new DataStorageConfiguration();
      storage.getDefaultDataRegionConfiguration()
         .setInitialSize(64L << 20)
         .setMaxSize(64L << 20);
      config.setDataStorageConfiguration(storage);
      ignite = Ignition.start(config);
   }

   @AfterAll
   static void stopIgnite() {
      if(ignite != null) {
         ignite.close();
         ignite = null;
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      for(String name : ignite.cacheNames()) {
         ignite.cache(name).clear();
      }

      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      // the cache that loading an MV reads, created before any transaction because Ignite can't
      // start a cache in one
      IgniteCache<String, String> mvDefs = cache("ClusterJobStoreMVActionTest.mvDefs");
      mvDef = mock(MVDef.class);
      when(mvDef.getName()).thenReturn(MV_NAME);
      mvManager = mock(MVManager.class);
      when(mvManager.get(anyString())).thenAnswer(inv -> {
         // as MVManager.get() checks the MV storage, which is backed by a replicated cache
         mvDefs.containsKey(inv.getArgument(0));
         return MV_NAME.equals(inv.getArgument(0)) ? mvDef : null;
      });
      installContext(new IgniteBackedCluster(), mvManager);
      store = new ClusterJobStore();
      store.initialize(null, mock(SchedulerSignaler.class));
      store.schedulerStarted();

      // the owner's principal is not needed to store the job
      sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutil.when(() -> SUtil.getScheduleTaskRunPrincipal(any(), any(), anyBoolean()))
         .thenReturn(null);
   }

   @AfterEach
   void tearDown() {
      sutil.close();
      ConfigurationContext.getContext().setApplicationContext(savedAppContext);
   }

   /**
    * A deserialized MV action is serialized again and described without loading its MV, and keeps
    * the name of the MV, by which it loads it when needed.
    */
   @Test
   void deserializedActionIsSerializedWithoutLoadingTheMV() throws Exception {
      MVAction action = roundTrip(new MVAction(mvDef));

      MVAction again = roundTrip(action);
      assertEquals("MVAction: " + MV_NAME, action.toString());
      verifyNoInteractions(mvManager);

      assertSame(mvDef, again.getMV());
      verify(mvManager).get(MV_NAME);
   }

   /**
    * The first stage of a data cycle with a deserialized MV action is stored.
    */
   @Test
   void storesACycleTaskWithADeserializedAction() throws Exception {
      ScheduleTask task = createCycleTask("cycle1");
      task.addAction(roundTrip(new MVAction(mvDef)));
      JobDetail job = createJob(task);

      assertDoesNotThrow(() -> store.storeJob(job, false));

      assertStored(job);
   }

   /**
    * The second stage of a data cycle, which runs on the completion of the first, with a
    * deserialized MV action is stored.
    */
   @Test
   void storesASecondStageCycleTaskWithADeserializedAction() throws Exception {
      ScheduleTask task = createCycleTask("cycle1 Stage 2");
      task.addCondition(new CompletionCondition("SYSTEM~;~host-org__cycle1"));
      task.addAction(roundTrip(new MVAction(mvDef)));
      JobDetail job = createJob(task);

      assertDoesNotThrow(() -> store.storeJob(job, false));

      assertStored(job);
   }

   /**
    * A task that creates MVs at a scheduled time, with a deserialized MV action, is stored.
    */
   @Test
   void storesAnMVTaskWithADeserializedAction() throws Exception {
      ScheduleTask task = new ScheduleTask("MV Task: 1", ScheduleTask.Type.MV_TASK);
      task.setOwner(new IdentityID("admin", "host-org"));
      task.addAction(roundTrip(new MVAction(mvDef)));
      JobDetail job = createJob(task);

      assertDoesNotThrow(() -> store.storeJob(job, false));

      assertStored(job);
   }

   /**
    * The server pushing a task that the scheduler has stored on its start replaces the stored
    * job, and the job is still there.
    */
   @Test
   void replacesAJobWithADeserializedAction() throws Exception {
      ScheduleTask loaded = createCycleTask("cycle1");
      loaded.addAction(new MVAction(mvDef));
      store.storeJob(createJob(loaded), false);

      ScheduleTask pushed = createCycleTask("cycle1");
      pushed.addAction(roundTrip(new MVAction(mvDef)));
      JobDetail job = createJob(pushed);

      assertDoesNotThrow(() -> store.storeJob(job, true));

      assertStored(job);
   }

   private void assertStored(JobDetail job) throws Exception {
      JobDetail stored = store.retrieveJob(job.getKey());
      assertNotNull(stored, "the job was not stored");
      ScheduleTask task =
         (ScheduleTask) stored.getJobDataMap().get(ScheduleTask.class.getName());
      assertNotNull(task);
      assertEquals(1, task.getActionCount());
      MVAction action = (MVAction) task.getAction(0);
      assertEquals("MVAction: " + MV_NAME, action.toString());
      assertSame(mvDef, action.getMV(), "the stored action did not load its MV by its name");
   }

   private static ScheduleTask createCycleTask(String cycle) {
      ScheduleTask task = new ScheduleTask("DataCycle Task: " + cycle,
                                           ScheduleTask.Type.CYCLE_TASK);
      task.setOwner(new IdentityID("SYSTEM", "host-org"));
      return task;
   }

   /**
    * Creates the job of a task as {@code Scheduler.addTask} does.
    */
   private static JobDetail createJob(ScheduleTask task) {
      JobDataMap dataMap = new JobDataMap();
      dataMap.put(ScheduleTask.class.getName(), task);
      return JobBuilder.newJob(ScheduleTaskJob.class)
         .withIdentity(task.getTaskId(), inetsoft.sree.schedule.Scheduler.GROUP_NAME)
         .storeDurably()
         .usingJobData(dataMap)
         .build();
   }

   /**
    * Serializes and deserializes an action, as RMI does when the server sends a task to the
    * scheduler.
    */
   private static MVAction roundTrip(MVAction action) throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(action);
      }

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         return (MVAction) in.readObject();
      }
   }

   private static void installContext(Cluster cluster, MVManager mvManager) {
      PropertiesEngine properties = mock(PropertiesEngine.class);
      when(properties.getProperty(any(String.class), any(String.class), anyBoolean()))
         .thenAnswer(inv -> inv.getArgument(1));
      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(Cluster.class)).thenReturn(cluster);
      when(context.getBean(PropertiesEngine.class)).thenReturn(properties);
      when(context.getBean(MVManager.class)).thenReturn(mvManager);
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   @SuppressWarnings("unchecked")
   private static <K, V> IgniteCache<K, V> cache(String name) {
      try {
         Method method = IgniteCluster.class.getDeclaredMethod(
            "getCacheConfiguration", String.class, CacheMode.class, int.class);
         method.setAccessible(true);
         CacheConfiguration<K, V> config =
            (CacheConfiguration<K, V>) method.invoke(null, name, CacheMode.REPLICATED, 2);
         return ignite.getOrCreateCache(config);
      }
      catch(ReflectiveOperationException e) {
         throw new IllegalStateException(e);
      }
   }

   /**
    * A cluster whose replicated maps and transactions are Ignite's.
    */
   private static final class IgniteBackedCluster extends MockCluster {
      @Override
      public <K, V> DistributedMap<K, V> getReplicatedMap(String name) {
         return new IgniteDistributedMap<>(cache(name));
      }

      @Override
      public <K, V> MultiMap<K, V> getReplicatedMultiMap(String name) {
         return new IgniteMultiMap<>(ClusterJobStoreMVActionTest.<K, Collection<V>>cache(name));
      }

      @Override
      public String getLocalMember() {
         return "test-member";
      }

      @Override
      public <T, E extends Exception> T runInTransaction(long timeout, TimeUnit unit,
                                                          TransactionalAction<T, E> action)
         throws E
      {
         return IgniteCluster.runInTransaction(ignite, timeout, unit, action);
      }
   }

   /**
    * Picks distinct free ports, as the other embedded Ignite tests do, so the topology can't
    * pick up a node of another test or fork.
    */
   private static int[] freePorts(int count) {
      java.net.ServerSocket[] sockets = new java.net.ServerSocket[count];
      int[] ports = new int[count];

      try {
         for(int i = 0; i < count; i++) {
            sockets[i] = new java.net.ServerSocket(0);
            ports[i] = sockets[i].getLocalPort();
         }

         return ports;
      }
      catch(IOException ex) {
         throw new UncheckedIOException(ex);
      }
      finally {
         for(java.net.ServerSocket socket : sockets) {
            if(socket != null) {
               try {
                  socket.close();
               }
               catch(IOException ignore) {
               }
            }
         }
      }
   }

   private static final String MV_NAME = "mv1";
   private static Ignite ignite;
   private ApplicationContext savedAppContext;
   private MVDef mvDef;
   private MVManager mvManager;
   private ClusterJobStore store;
   private MockedStatic<SUtil> sutil;
}
