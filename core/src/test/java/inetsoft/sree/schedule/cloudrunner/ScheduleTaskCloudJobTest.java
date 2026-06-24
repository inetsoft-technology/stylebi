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

import inetsoft.sree.schedule.*;
import inetsoft.test.schedule.FakeCloudJobFactory;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
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
 *   <li>Its constructor calls {@code Cluster.getInstance()} and
 *       {@code SreeEnv.getProperty("schedule.task.timeout")} immediately on construction.</li>
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

   private SchedulerTestHarness harness;

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
