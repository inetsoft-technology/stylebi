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
package inetsoft.sree.schedule.jobstore;

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MockCluster;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.cloudrunner.CloudJob;
import inetsoft.sree.schedule.cloudrunner.CloudJobFactory;
import inetsoft.sree.schedule.cloudrunner.CloudJobResult;
import inetsoft.sree.schedule.cloudrunner.ScheduleTaskCloudJob;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.config.CloudRunnerConfig;
import inetsoft.util.config.ClusterConfig;
import inetsoft.util.config.InetsoftConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.quartz.*;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.SchedulerSignaler;
import org.springframework.context.ApplicationContext;

import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * How {@code schedule.task.timeout} sets the time a job store keeps honouring a run of a task:
 * the hold kept for a cloud runner run whose node left the cluster, and the bound after which a
 * run is considered stale. The store runs on the in-memory {@link MockCluster}, which is enough
 * because only the stored jobs are read. The property is served by a mock
 * {@code PropertiesEngine}, so the real {@code ScheduleTask.getTaskTimeout()} reads it on any
 * thread.
 */
@Tag("core")
class ClusterJobStoreTaskTimeoutTest {
   private static final long LAUNCH_MARGIN = TimeUnit.MINUTES.toMillis(5);
   private static final String TASK = "org~;admin:T";

   @BeforeEach
   void setUp() throws Exception {
      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      cluster = new MockCluster();

      PropertiesEngine properties = mock(PropertiesEngine.class);
      when(properties.getProperty(any(String.class), any(String.class), anyBoolean()))
         .thenAnswer(inv -> inv.getArgument(1));
      when(properties.getProperty(eq("schedule.task.timeout"), anyBoolean()))
         .thenAnswer(inv -> timeoutProperty);

      InetsoftConfig config = new InetsoftConfig();
      config.setCluster(new ClusterConfig());
      CloudRunnerConfig cloudRunner = new CloudRunnerConfig();
      cloudRunner.setType(TimeoutJobFactory.TYPE);
      config.setCloudRunner(cloudRunner);
      InetsoftConfig runnerConfig = new InetsoftConfig();
      runnerConfig.setCluster(new ClusterConfig());
      cluster.<String, InetsoftConfig>getMap("cloud.runner.config").put("config", runnerConfig);

      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(Cluster.class)).thenReturn(cluster);
      when(context.getBean(PropertiesEngine.class)).thenReturn(properties);
      when(context.getBean(InetsoftConfig.class)).thenReturn(config);
      ConfigurationContext.getContext().setApplicationContext(context);

      store = new ClusterJobStore();
      store.initialize(null, mock(SchedulerSignaler.class));
      store.schedulerStarted();
      TimeoutJobFactory.EXECUTION_IDS.clear();
   }

   @AfterEach
   void tearDown() {
      try {
         store.shutdown();
      }
      finally {
         ConfigurationContext.getContext().setApplicationContext(savedAppContext);
      }
   }

   @ParameterizedTest(name = "cloud run hold with schedule.task.timeout [{0}] is the default " +
      "timeout plus the launch margin")
   @ValueSource(strings = { "0", "-1" })
   void cloudRunHoldUsesTheDefaultTimeoutWhenTimeoutIsNotPositive(String property)
      throws Exception
   {
      timeoutProperty = property;
      TriggerWrapper blocked = storedWrapper("cloud-hold-default", ScheduleTaskCloudJob.class,
                                             TriggerState.BLOCKED);

      assertEquals(ScheduleTask.DEFAULT_TASK_TIMEOUT + LAUNCH_MARGIN,
                   store.getOrphanedRunHold(blocked));
   }

   @Test
   void cloudRunHoldIsTheConfiguredTimeoutPlusTheLaunchMargin() throws Exception {
      timeoutProperty = "1500";
      TriggerWrapper blocked = storedWrapper("cloud-hold-configured", ScheduleTaskCloudJob.class,
                                             TriggerState.BLOCKED);

      assertEquals(1500L + LAUNCH_MARGIN, store.getOrphanedRunHold(blocked));
   }

   @Test
   void onlyABlockedCloudRunIsHeldAfterItsNodeLeft() throws Exception {
      timeoutProperty = "0";
      TriggerWrapper localBlocked = storedWrapper("hold-local", LocalJob.class,
                                                  TriggerState.BLOCKED);
      TriggerWrapper cloudAcquired = storedWrapper("hold-cloud-acquired",
                                                   ScheduleTaskCloudJob.class,
                                                   TriggerState.ACQUIRED);

      assertEquals(0L, store.getOrphanedRunHold(localBlocked),
                   "a local run dies with its node");
      assertEquals(0L, store.getOrphanedRunHold(cloudAcquired),
                   "nothing was launched before the trigger fired");
   }

   @ParameterizedTest(name = "cloud job timeout and store hold agree for schedule.task.timeout " +
      "[{0}]")
   @NullSource
   @ValueSource(strings = { "abc", "0", "-1", "1500" })
   void storeHoldMatchesTheTimeoutTheCloudJobWaitsWith(String property) throws Exception {
      timeoutProperty = property;
      TriggerWrapper blocked = storedWrapper("cloud-differential", ScheduleTaskCloudJob.class,
                                             TriggerState.BLOCKED);

      long cloudJobTimeout = timeoutOfExecutedCloudJob();

      assertEquals(cloudJobTimeout, store.getOrphanedRunHold(blocked) - LAUNCH_MARGIN,
                   "the store must hold a cloud run for the timeout its job waited with");
   }

   @Test
   void localRunBoundIsTheDefaultTimeoutWithoutLaunchMarginWhenTimeoutIsNotPositive()
      throws Exception
   {
      timeoutProperty = "0";
      JobDetail job = storeJob("bound-local", LocalJob.class);

      assertEquals(ScheduleTask.DEFAULT_TASK_TIMEOUT, store.getRunBound(job.getKey()),
                   "a local run has no launch margin, and may outlast the default");
   }

   /**
    * Runs ScheduleTaskCloudJob.execute with a container that answers at once, and returns the
    * timeout field the job waited with.
    */
   private long timeoutOfExecutedCloudJob() throws Exception {
      ScheduleTaskCloudJob job = new ScheduleTaskCloudJob();
      JobDetail detail = JobBuilder.newJob(ScheduleTaskCloudJob.class)
         .withIdentity(TASK, "test")
         .build();
      detail.getJobDataMap().put(ScheduleTask.class.getName(), new ScheduleTask(TASK));
      JobExecutionContext context = mock(JobExecutionContext.class);
      when(context.getJobDetail()).thenReturn(detail);

      Thread container = new Thread(() -> {
         try {
            String executionId = TimeoutJobFactory.EXECUTION_IDS.poll(5, TimeUnit.SECONDS);

            if(executionId != null) {
               cluster.sendMessage(new CloudJobResult(TASK, true, "ok", executionId));
            }
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      });
      container.setDaemon(true);
      container.start();

      try {
         job.execute(context);
      }
      finally {
         container.join(5000);
      }

      java.lang.reflect.Field field = ScheduleTaskCloudJob.class.getDeclaredField("timeout");
      field.setAccessible(true);
      return field.getLong(job);
   }

   private JobDetail storeJob(String name, Class<? extends Job> jobClass) throws Exception {
      JobDetail job = JobBuilder.newJob(jobClass)
         .withIdentity(name, "ClusterJobStoreTaskTimeoutTest")
         .storeDurably()
         .build();
      store.storeJob(job, true);
      return job;
   }

   private TriggerWrapper storedWrapper(String name, Class<? extends Job> jobClass,
                                        TriggerState state)
      throws Exception
   {
      JobDetail job = storeJob(name, jobClass);
      SimpleTriggerImpl trigger = new SimpleTriggerImpl();
      trigger.setKey(TriggerKey.triggerKey(name + "-trigger", "ClusterJobStoreTaskTimeoutTest"));
      trigger.setJobKey(job.getKey());
      trigger.setStartTime(new Date(System.currentTimeMillis() + 3600_000));
      return TriggerWrapper.newOwnedTriggerWrapper(
         (OperableTrigger) trigger, state, "gone", "gone", "gone-");
   }

   public static class LocalJob implements Job {
      @Override
      public void execute(JobExecutionContext context) {
      }
   }

   /** A cloud runner whose container never does anything itself. */
   public static class TimeoutJobFactory implements CloudJobFactory {
      static final String TYPE = "task-timeout-test";
      static final BlockingQueue<String> EXECUTION_IDS = new LinkedBlockingQueue<>();

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
         return new CloudJob() {
            @Override
            public void start() {
               EXECUTION_IDS.add(executionId);
            }

            @Override
            public void stop() {
            }
         };
      }
   }

   private volatile String timeoutProperty;
   private ApplicationContext savedAppContext;
   private MockCluster cluster;
   private ClusterJobStore store;
}
