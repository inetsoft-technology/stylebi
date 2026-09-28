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
package inetsoft.sree.schedule.cloudrunner;

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.config.*;
import org.junit.jupiter.api.*;
import org.quartz.*;
import org.springframework.context.ApplicationContext;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests that {@link ScheduleTaskCloudJob} completes on the {@link CloudJobResult} of the container
 * it started, and not on the result of another execution of the same task (Bug #77200).
 *
 * <p>The real {@code execute()} is driven with a mock {@link Cluster}. A container result is
 * simulated by delivering it to every registered {@link MessageListener}, which is what the cluster
 * does with a broadcast message. {@link AttributionJobFactory} is registered for the
 * {@code attribution77200} cloud runner type in the test {@code META-INF/services}.
 */
@Tag("core")
class ScheduleTaskCloudJobAttributionTest {
   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() throws Exception {
      AttributionJobFactory.STARTED.clear();
      AttributionJobFactory.blocking = false;

      cluster = mock(Cluster.class);
      doAnswer(inv -> listeners.add(inv.getArgument(0)))
         .when(cluster).addMessageListener(any(MessageListener.class));
      doAnswer(inv -> listeners.remove(inv.<MessageListener>getArgument(0)))
         .when(cluster).removeMessageListener(any(MessageListener.class));
      doAnswer(inv -> sent.add(inv.getArgument(0)))
         .when(cluster).sendMessage(any(Serializable.class));

      InetsoftConfig runnerConfig = new InetsoftConfig();
      runnerConfig.setCluster(new ClusterConfig());
      DistributedMap<String, InetsoftConfig> configMap = mock(DistributedMap.class);
      when(configMap.containsKey("config")).thenReturn(true);
      when(configMap.get("config")).thenReturn(runnerConfig);
      when(cluster.getLock(anyString())).thenReturn(new ReentrantLock());
      when(cluster.<String, InetsoftConfig>getMap("cloud.runner.config")).thenReturn(configMap);
      when(cluster.getClusterAddresses()).thenReturn(List.of());

      InetsoftConfig config = new InetsoftConfig();
      config.setCluster(new ClusterConfig());
      CloudRunnerConfig cloudRunner = new CloudRunnerConfig();
      cloudRunner.setType(AttributionJobFactory.TYPE);
      config.setCloudRunner(cloudRunner);

      PropertiesEngine properties = mock(PropertiesEngine.class);
      when(properties.getProperty(eq("schedule.task.timeout"), anyBoolean())).thenReturn("10000");

      ApplicationContext appContext = mock(ApplicationContext.class);
      when(appContext.getBean(Cluster.class)).thenReturn(cluster);
      when(appContext.getBean(InetsoftConfig.class)).thenReturn(config);
      when(appContext.getBean(PropertiesEngine.class)).thenReturn(properties);

      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      ConfigurationContext.getContext().setApplicationContext(appContext);
   }

   @AfterEach
   void tearDown() {
      AttributionJobFactory.STARTED.forEach(job -> job.release.countDown());
      AttributionJobFactory.STARTED.clear();
      AttributionJobFactory.blocking = false;
      executor.shutdownNow();
      ConfigurationContext.getContext().setApplicationContext(savedAppContext);
   }

   @Test
   void nonBlockingRunnerWaitsForTheResultOfItsOwnContainer() throws Exception {
      Future<String> run1 = execute(new ScheduleTaskCloudJob());
      AttributionJob job1 = awaitStarted();
      Future<String> run2 = execute(new ScheduleTaskCloudJob());
      AttributionJob job2 = awaitStarted();

      broadcast(new CloudJobResult(TASK, true, "ok", job1.executionId));

      assertEquals(SUCCESS, run1.get(5, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> run2.get(500, TimeUnit.MILLISECONDS),
                   "run #2 must not complete on the result of the container of run #1");

      broadcast(new CloudJobResult(TASK, false, "container 2 failed", job2.executionId));

      assertTrue(run2.get(5, TimeUnit.SECONDS).startsWith(FAILED),
                 "run #2 must report the failure of its own container");
      assertNotNull(job1.executionId, "the execution id must be passed to the factory");
      assertNotEquals(job1.executionId, job2.executionId, "each execution must have its own id");
      assertTrue(listeners.isEmpty(), "every listener must be removed");
   }

   @Test
   void blockingRunnerReportsTheResultOfItsOwnContainer() throws Exception {
      AttributionJobFactory.blocking = true;
      Future<String> run1 = execute(new ScheduleTaskCloudJob());
      AttributionJob job1 = awaitStarted();
      Future<String> run2 = execute(new ScheduleTaskCloudJob());
      AttributionJob job2 = awaitStarted();

      // container 1 succeeds, then container 2 fails, and only then do both start() calls return
      broadcast(new CloudJobResult(TASK, true, "ok", job1.executionId));
      broadcast(new CloudJobResult(TASK, false, "container 2 failed", job2.executionId));
      job1.release.countDown();
      job2.release.countDown();

      assertEquals(SUCCESS, run1.get(5, TimeUnit.SECONDS),
                   "run #1 must report the success of its own container");
      assertTrue(run2.get(5, TimeUnit.SECONDS).startsWith(FAILED),
                 "run #2 must report the failure of its own container");
   }

   @Test
   void resultWithoutExecutionIdIsMatchedByTaskName() throws Exception {
      // a runner image that predates the execution id sends a result without one
      Future<String> run = execute(new ScheduleTaskCloudJob());
      awaitStarted();

      broadcast(new CloudJobResult("another task", true, "ok"));
      assertThrows(TimeoutException.class, () -> run.get(500, TimeUnit.MILLISECONDS),
                   "a result for another task must be ignored");

      broadcast(new CloudJobResult(TASK, false, "legacy failure"));
      assertTrue(run.get(5, TimeUnit.SECONDS).startsWith(FAILED),
                 "a result without an execution id must still complete the run");
   }

   @Test
   void interruptCancelsOnlyItsOwnExecution() throws Exception {
      ScheduleTaskCloudJob cloudJob = new ScheduleTaskCloudJob();
      Future<String> run = execute(cloudJob);
      AttributionJob job = awaitStarted();

      cloudJob.interrupt();

      assertEquals(SUCCESS, run.get(5, TimeUnit.SECONDS));
      assertTrue(job.stopped, "interrupt() must stop its own cloud job");
      CancelCloudJob cancel = sent.stream()
         .filter(CancelCloudJob.class::isInstance)
         .map(CancelCloudJob.class::cast)
         .findFirst()
         .orElseThrow(() -> new AssertionError("interrupt() must send a CancelCloudJob"));
      assertEquals(TASK, cancel.getTaskName());
      assertNotNull(cancel.getExecutionId(),
                    "the cancel must carry an execution id, or it cancels every execution");
      assertEquals(job.executionId, cancel.getExecutionId());
   }

   private Future<String> execute(ScheduleTaskCloudJob cloudJob) {
      JobDetail detail = JobBuilder.newJob(ScheduleTaskCloudJob.class)
         .withIdentity(TASK, "test")
         .build();
      detail.getJobDataMap().put(ScheduleTask.class.getName(), new ScheduleTask(TASK));
      JobExecutionContext context = mock(JobExecutionContext.class);
      when(context.getJobDetail()).thenReturn(detail);

      return executor.submit(() -> {
         try {
            cloudJob.execute(context);
            return SUCCESS;
         }
         catch(JobExecutionException e) {
            return FAILED + e.getMessage();
         }
      });
   }

   private AttributionJob awaitStarted() throws InterruptedException {
      AttributionJob job = AttributionJobFactory.STARTED.poll(5, TimeUnit.SECONDS);
      assertNotNull(job, "the cloud job must be started");
      return job;
   }

   private void broadcast(Object message) {
      for(MessageListener listener : listeners) {
         listener.messageReceived(new MessageEvent(cluster, "container", false, message));
      }
   }

   private static final String TASK = "org~;admin:T";
   private static final String SUCCESS = "SUCCESS";
   private static final String FAILED = "FAILED: ";

   private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();
   private final BlockingQueue<Object> sent = new LinkedBlockingQueue<>();
   private final ExecutorService executor = Executors.newCachedThreadPool();
   private Cluster cluster;
   private ApplicationContext savedAppContext;

   public static class AttributionJobFactory implements CloudJobFactory {
      @Override
      public String getType() {
         return TYPE;
      }

      @Override
      public CloudJob createCloudJob(String taskName, String cycle, String orgID) {
         return createCloudJob(taskName, cycle, orgID, null);
      }

      @Override
      public CloudJob createCloudJob(String taskName, String cycle, String orgID,
                                     String executionId)
      {
         return new AttributionJob(executionId, blocking);
      }

      static final String TYPE = "attribution77200";
      static final BlockingQueue<AttributionJob> STARTED = new LinkedBlockingQueue<>();
      static volatile boolean blocking;
   }

   /**
    * A cloud job that sends nothing itself. A non-blocking job returns from {@code start()} at
    * once, like the Kubernetes, Fargate, Azure and Google runners. A blocking job waits in
    * {@code start()} until released, like the Docker runner waiting for its container to exit.
    */
   static class AttributionJob implements CloudJob {
      AttributionJob(String executionId, boolean blocking) {
         this.executionId = executionId;
         this.blocking = blocking;
      }

      @Override
      public void start() {
         AttributionJobFactory.STARTED.add(this);

         if(blocking) {
            try {
               release.await(10, TimeUnit.SECONDS);
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         }
      }

      @Override
      public void stop() {
         stopped = true;
         release.countDown();
      }

      final String executionId;
      final boolean blocking;
      final CountDownLatch release = new CountDownLatch(1);
      volatile boolean stopped;
   }
}
