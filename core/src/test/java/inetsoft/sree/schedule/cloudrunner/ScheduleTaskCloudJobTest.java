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
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.internal.cluster.MessageEvent;
import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.sree.schedule.*;
import inetsoft.test.schedule.FakeCloudJobFactory;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.config.ClusterConfig;
import inetsoft.util.config.CloudRunnerConfig;
import inetsoft.util.config.InetsoftConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Layer 3 — Cloud protocol tests.
 *
 * <h2>What is being tested</h2>
 * <p>The cloud-runner protocol: a {@link CloudJob} starts in a separate thread (simulating a
 * remote container), executes the scheduled task, and signals completion by sending a
 * {@link CloudJobResult} via {@code Cluster.sendMessage()}. {@link FakeCloudJobFactory} and
 * {@link FakeCloudJobFactory.FakeCloudJob} implement this protocol in-process so no real
 * container or network is needed.
 *
 * <h2>Why {@link ScheduleTaskCloudJob} is not exercised here</h2>
 * <p>{@link ScheduleTaskCloudJob} is the production Quartz job that drives the cloud protocol.
 * It cannot be driven in a unit test because:
 * <ul>
 *   <li>Its constructor calls {@code Cluster.getInstance()} immediately on construction.
 *       (The {@code schedule.task.timeout} read has since moved into {@code execute()}, but
 *       the cluster dependency remains.)</li>
 *   <li>{@code createCloudRunnerConfig()} requires a live Ignite cluster
 *       ({@code getLock()}, {@code getMap()}, {@code getClusterAddresses()}) and a fully
 *       wired {@code InetsoftConfig} Spring bean.</li>
 *   <li>The ServiceLoader type-matching loop needs
 *       {@code InetsoftConfig.getInstance().getCloudRunner().getType()} to equal the factory
 *       type — another Spring dependency that requires a full application context.</li>
 * </ul>
 * <p>Testing {@code ScheduleTaskCloudJob} end-to-end requires a real Ignite cluster and a
 * containerised Docker environment; that belongs in the e2e test suite, not a unit test.
 *
 * <h2>Production data flow (for reference)</h2>
 * <pre>
 *   Quartz → ScheduleTaskCloudJob.execute()
 *               │  registers MessageListener on Cluster
 *               │  ServiceLoader → CloudJobFactory.createCloudJob()
 *               │  CloudJob.start()  ← remote container starts here
 *               │       (task runs remotely)
 *               │  remote sends CloudJobResult via Cluster.sendMessage()
 *               │  MessageListener.onMessage() fires → latch.countDown()
 *               └  reports FINISHED / FAILED to JobCompletionListener
 * </pre>
 * <p>These tests verify steps 3–5 of that flow using {@link FakeCloudJobFactory} in place of
 * a real container factory.
 */
@Tag("core")
class ScheduleTaskCloudJobTest {

   private static final String CLOUD_TASK = "org~;admin:T";

   private SchedulerTestHarness harness;
   private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();
   private Cluster cluster;

   @BeforeEach
   void setUp() throws Exception {
      FakeCloudJobFactory.clearRegistry();
      harness = new SchedulerTestHarness();
   }

   @AfterEach
   void tearDown() {
      harness.close();
      FakeCloudJobFactory.clearRegistry();
   }

   // -----------------------------------------------------------------------
   // Protocol: success path
   // -----------------------------------------------------------------------

   @Test
   void cloudJob_successfulTask_sendsSuccessResult() throws Exception {
      ScheduleTask task = buildTask("cloud-success");
      FakeCloudJobFactory.registerTask(task);

      CloudJob job = new FakeCloudJobFactory()
         .createCloudJob(task.getTaskId(), null, null);
      job.start();

      // FakeCloudJob runs task.run() in a daemon thread, then delivers CloudJobResult
      // via Cluster.getInstance().sendMessage() — captured by the harness mock.
      Object msg = harness.getClusterMessages().poll(5, TimeUnit.SECONDS);

      assertNotNull(msg, "CloudJobResult must be delivered within 5 seconds");
      assertInstanceOf(CloudJobResult.class, msg);
      CloudJobResult result = (CloudJobResult) msg;
      assertTrue(result.isSuccess(), "Successful task must report success=true");
      assertEquals(task.getTaskId(), result.getTaskName(),
         "CloudJobResult.taskName must match the task ID passed to createCloudJob");
   }

   // -----------------------------------------------------------------------
   // Protocol: failure path
   // -----------------------------------------------------------------------

   @Test
   void cloudJob_failingTask_sendsFailureResult() throws Throwable {
      ScheduleTask task = buildFailingTask("cloud-failure");
      FakeCloudJobFactory.registerTask(task);

      CloudJob job = new FakeCloudJobFactory()
         .createCloudJob(task.getTaskId(), null, null);
      job.start();

      Object msg = harness.getClusterMessages().poll(5, TimeUnit.SECONDS);

      assertNotNull(msg, "CloudJobResult must be delivered even when the task throws");
      assertInstanceOf(CloudJobResult.class, msg);
      CloudJobResult result = (CloudJobResult) msg;
      assertFalse(result.isSuccess(), "Failing task must report success=false");
      assertNotNull(result.getMessage(), "Failure message must be non-null");
   }

   // -----------------------------------------------------------------------
   // Protocol: stop cancels before result is sent
   // -----------------------------------------------------------------------

   @Test
   void cloudJob_stopBeforeStart_noResultDelivered() throws Exception {
      ScheduleTask task = buildTask("cloud-stop");
      FakeCloudJobFactory.registerTask(task);

      CloudJob job = new FakeCloudJobFactory()
         .createCloudJob(task.getTaskId(), null, null);
      // Call stop() before start() — the executor shuts down immediately,
      // so the submitted runnable is rejected and no CloudJobResult is ever sent.
      job.stop();
      job.start();   // submitted after shutdownNow() → RejectedExecutionException, silently discarded

      Object msg = harness.getClusterMessages().poll(2, TimeUnit.SECONDS);
      assertNull(msg, "No CloudJobResult must be delivered after stop() precedes start()");
   }

   // -----------------------------------------------------------------------
   // Construction: a bad schedule.task.timeout must not stop Quartz instantiating the job
   // -----------------------------------------------------------------------

   @Test
   void constructorSurvivesAnUnusableTimeoutProperty() {
      // The timeout was once parsed in the constructor, so a missing or non-numeric
      // schedule.task.timeout threw before Quartz could instantiate the job at all: the trigger
      // misfired, the task never ran, and nothing pointed at the property value as the cause.
      // The harness leaves schedule.task.timeout unstubbed (so it reads back null), which is
      // exactly the input that used to throw from here.
      assertDoesNotThrow(ScheduleTaskCloudJob::new,
         "An unusable schedule.task.timeout must not prevent construction of the Quartz job; " +
         "the value belongs in execute(), where a fallback can be reported and applied");
   }

   // -----------------------------------------------------------------------
   // execute(): effective schedule.task.timeout
   // -----------------------------------------------------------------------

   @ParameterizedTest(name = "schedule.task.timeout [{0}] is clamped to the default")
   @ValueSource(strings = { "0", "-1" })
   void executeClampsNonPositiveTimeoutToTheDefault(String timeoutProperty) throws Exception {
      ScheduleTaskCloudJob job = executeWithResultDeliveredAtOnce(timeoutProperty);

      assertEquals(ScheduleTask.DEFAULT_TASK_TIMEOUT, timeoutOf(job),
                   "a cloud job must never wait without a bound");
   }

   @Test
   void executeUsesConfiguredPositiveTimeout() throws Exception {
      ScheduleTaskCloudJob job = executeWithResultDeliveredAtOnce("1500");

      assertEquals(1500L, timeoutOf(job));
   }

   @ParameterizedTest(name = "unusable schedule.task.timeout [{0}] falls back to the default")
   @NullSource
   @ValueSource(strings = { "abc" })
   void executeFallsBackToTheDefaultWhenTimeoutIsMissingOrInvalid(String timeoutProperty)
      throws Exception
   {
      ScheduleTaskCloudJob job = executeWithResultDeliveredAtOnce(timeoutProperty);

      assertEquals(ScheduleTask.DEFAULT_TASK_TIMEOUT, timeoutOf(job));
   }

   @Test
   void executeStopsTheCloudJobAndFailsWhenNoResultArrivesWithinTheTimeout() throws Exception {
      installCloudFixture("200");
      ScheduleTaskCloudJobAttributionTest.AttributionJobFactory.STARTED.clear();
      ScheduleTaskCloudJob job = new ScheduleTaskCloudJob();

      JobExecutionException error =
         assertThrows(JobExecutionException.class, () -> job.execute(jobContext()));

      // execute() wraps the timeout error it throws inside its own catch-all, so the
      // "timed out" text is on the cause rather than on the outer exception
      Throwable cause = error;
      boolean timedOut = false;

      while(cause != null && !timedOut) {
         timedOut = cause.getMessage() != null && cause.getMessage().contains("timed out");
         cause = cause.getCause();
      }

      assertTrue(timedOut, "the failure must say the task timed out: " + error);
      ScheduleTaskCloudJobAttributionTest.AttributionJob started =
         ScheduleTaskCloudJobAttributionTest.AttributionJobFactory.STARTED.poll(1, TimeUnit.SECONDS);
      assertNotNull(started, "the cloud job must have been started");
      assertTrue(started.stopped, "a timed-out cloud job must be stopped");
   }

   // -----------------------------------------------------------------------
   // Factory contract
   // -----------------------------------------------------------------------

   @Test
   void fakeCloudJobFactory_getType_returnsFake() {
      assertEquals("fake", new FakeCloudJobFactory().getType(),
         "Factory type must be 'fake' so it matches cloudRunnerConfig.type in production tests");
   }

   // -----------------------------------------------------------------------
   // Helpers
   // -----------------------------------------------------------------------

   /**
    * Replaces the application context of the harness with one that offers a mock cluster, the
    * cloud runner configuration of the test runner type and the given timeout property. The
    * harness restores the original context when it is closed.
    */
   @SuppressWarnings("unchecked")
   private void installCloudFixture(String timeoutProperty) throws Exception {
      listeners.clear();
      cluster = mock(Cluster.class);
      doAnswer(inv -> listeners.add(inv.getArgument(0)))
         .when(cluster).addMessageListener(any(MessageListener.class));
      doAnswer(inv -> listeners.remove(inv.<MessageListener>getArgument(0)))
         .when(cluster).removeMessageListener(any(MessageListener.class));

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
      cloudRunner.setType(ScheduleTaskCloudJobAttributionTest.AttributionJobFactory.TYPE);
      config.setCloudRunner(cloudRunner);

      PropertiesEngine properties = mock(PropertiesEngine.class);
      when(properties.getProperty(eq("schedule.task.timeout"), anyBoolean()))
         .thenReturn(timeoutProperty);

      ApplicationContext appContext = mock(ApplicationContext.class);
      when(appContext.getBean(Cluster.class)).thenReturn(cluster);
      when(appContext.getBean(InetsoftConfig.class)).thenReturn(config);
      when(appContext.getBean(PropertiesEngine.class)).thenReturn(properties);
      ConfigurationContext.getContext().setApplicationContext(appContext);
   }

   private JobExecutionContext jobContext() {
      JobDetail detail = JobBuilder.newJob(ScheduleTaskCloudJob.class)
         .withIdentity(CLOUD_TASK, "test")
         .build();
      detail.getJobDataMap().put(ScheduleTask.class.getName(), new ScheduleTask(CLOUD_TASK));
      JobExecutionContext context = mock(JobExecutionContext.class);
      when(context.getJobDetail()).thenReturn(detail);
      return context;
   }

   /** Runs execute() on the test thread; a helper thread answers as the finished container. */
   private ScheduleTaskCloudJob executeWithResultDeliveredAtOnce(String timeoutProperty)
      throws Exception
   {
      installCloudFixture(timeoutProperty);
      ScheduleTaskCloudJobAttributionTest.AttributionJobFactory.STARTED.clear();
      ScheduleTaskCloudJob job = new ScheduleTaskCloudJob();
      Thread container = new Thread(() -> {
         try {
            ScheduleTaskCloudJobAttributionTest.AttributionJob started =
               ScheduleTaskCloudJobAttributionTest.AttributionJobFactory.STARTED
                  .poll(5, TimeUnit.SECONDS);

            if(started != null) {
               for(MessageListener listener : listeners) {
                  listener.messageReceived(new MessageEvent(
                     cluster, "container", false,
                     new CloudJobResult(CLOUD_TASK, true, "ok", started.executionId)));
               }
            }
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      });
      container.setDaemon(true);
      container.start();

      try {
         job.execute(jobContext());
      }
      finally {
         container.join(5000);
      }

      return job;
   }

   private static long timeoutOf(ScheduleTaskCloudJob job) throws Exception {
      java.lang.reflect.Field field = ScheduleTaskCloudJob.class.getDeclaredField("timeout");
      field.setAccessible(true);
      return field.getLong(job);
   }

   private ScheduleTask buildTask(String name) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(SchedulerTestHarness.TEST_OWNER);
      task.setEnabled(true);
      task.addAction(mock(ScheduleAction.class));
      return task;
   }

   private ScheduleTask buildFailingTask(String name) throws Throwable {
      ScheduleAction failingAction = mock(ScheduleAction.class);
      doThrow(new RuntimeException("simulated cloud failure")).when(failingAction).run(any());

      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(SchedulerTestHarness.TEST_OWNER);
      task.setEnabled(true);
      task.addAction(failingAction);
      return task;
   }
}
