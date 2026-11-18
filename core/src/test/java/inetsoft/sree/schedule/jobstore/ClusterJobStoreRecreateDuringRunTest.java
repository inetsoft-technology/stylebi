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

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MockCluster;
import inetsoft.sree.schedule.*;
import inetsoft.sree.schedule.Scheduler;
import inetsoft.sree.schedule.quartz.*;
import inetsoft.sree.security.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.quartz.*;
import org.quartz.impl.DirectSchedulerFactory;
import org.quartz.simpl.SimpleThreadPool;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81647: the completion of a run must only act on the trigger it fired, and on the status of
 * the task generation it ran. A task deleted (or edited) during a run stores new triggers under
 * the same keys ("taskId-1", "taskId-2"); the old run's completion used to remove the new
 * "taskId-2" (an AT condition) and to write a status under the task name of the recreated task.
 *
 * <p>The axis is the fire instance of the run (the stored trigger's fire instance id) for the
 * trigger, and whether the task was removed (not edited) while the run was in progress for the
 * status. Runs a real Quartz scheduler with a real ClusterJobStore and JobCompletionListener,
 * and the real Scheduler.removeTask (the singleton's Quartz scheduler is replaced for the test); the
 * save of a task again is the store-facing sequence of Scheduler.addTask. Takes about 40 s.
 */
@Tag("slow")
class ClusterJobStoreRecreateDuringRunTest {
   @BeforeEach
   void setUp() {
      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      savedPrincipal = ThreadContext.getContextPrincipal();
      RUNNING.set(0);
   }

   @AfterEach
   void tearDown() throws Exception {
      if(scheduler != null) {
         scheduler.shutdown(true);
         Field field = Scheduler.class.getDeclaredField("scheduler");
         field.setAccessible(true);
         field.set(Scheduler.getScheduler(), null);
      }

      ConfigurationContext.getContext().setApplicationContext(savedAppContext);
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   /** The old run's own AT trigger is still removed after its run when nothing changed. */
   @Test
   void sameGenerationRemovesItsAtTrigger() throws Exception {
      Scenario s = start("control");
      s.waitForRunToComplete();

      assertFalse(s.exists(2), "the AT trigger of the finished run is removed");
      assertEquals(List.of(Scheduler.Status.FINISHED), s.statusesAfterStart());
   }

   /** Deleting the task during the run and recreating it keeps the new triggers and status. */
   @Test
   void deleteAndRecreateKeepsNewTriggersAndWritesNoStatus() throws Exception {
      Scenario s = start("recreate");
      s.sleepIntoRun();
      s.remove();
      s.store(Set.of(1, 2));
      assertTrue(s.exists(1));
      assertTrue(s.exists(2));
      clearInvocations(dao);
      s.waitForRunToComplete();

      assertTrue(s.exists(1), "the new -1 trigger is kept");
      assertTrue(s.exists(2), "the new -2 trigger is kept");
      assertTrue(s.statusesAfterStart().isEmpty(), "no status is written for the old run");
   }

   /** Deleting the task during the run without recreating it leaves no status behind. */
   @Test
   void deleteWithoutRecreateLeavesNoStatus() throws Exception {
      Scenario s = start("delete");
      s.sleepIntoRun();
      s.remove();
      clearInvocations(dao);
      s.waitForRunToComplete();

      assertFalse(s.exists(2));
      assertTrue(s.statusesAfterStart().isEmpty(), "no status is written for a removed task");
   }

   /** Editing the task during the run keeps the new trigger, and the run's final status. */
   @Test
   void editDuringRunKeepsTriggerAndFinalStatus() throws Exception {
      Scenario s = start("edit");
      s.sleepIntoRun();
      // Scheduler.addTask(!first): deleteJob + scheduleJob, no mark and no clearStatus
      s.replace(Set.of(1, 2));
      clearInvocations(dao);
      s.waitForRunToComplete();

      assertTrue(s.exists(1), "the new -1 trigger is kept");
      assertTrue(s.exists(2), "the new -2 trigger is kept");
      assertEquals(List.of(Scheduler.Status.FINISHED), s.statusesAfterStart(),
                   "the run still writes its final status");
   }

   /**
    * ScheduleManager.replaceScheduleTask and the import overwrite remove the task through
    * Scheduler.removeTask(name, true) and save it again under the same id: the run in progress is
    * not the run of a deleted task, so it still writes its final status.
    */
   @Test
   void replaceInPlaceDuringRunKeepsTriggerAndFinalStatus() throws Exception {
      Scenario s = start("replace");
      s.sleepIntoRun();
      s.remove(true);
      s.store(Set.of(1, 2));
      clearInvocations(dao);
      s.waitForRunToComplete();

      assertTrue(s.exists(1), "the new -1 trigger is kept");
      assertTrue(s.exists(2), "the new -2 trigger is kept");
      assertEquals(List.of(Scheduler.Status.FINISHED), s.statusesAfterStart(),
                   "the run still writes its final status");
   }

   /**
    * A trigger stored with no fire instance id (an upgrade, or never fired) is never the one a
    * run fired, so the conditional removal leaves it, whatever fire instance id is given.
    */
   @Test
   void removeTriggerFiredByNeedsTheSameFireInstance() throws Exception {
      installContext(new MockCluster());
      ClusterJobStore store = newScheduler("unit");
      scheduler.start();
      long time = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
      scheduler.scheduleJob(job("task-unit", 10), Set.of(trigger("task-unit", 2, time)), true);
      TriggerKey key = trigger("task-unit", 2, 0).getKey();

      assertFalse(store.removeTriggerFiredBy(key, "other-run"));
      assertFalse(store.removeTriggerFiredBy(key, null));
      assertNotNull(scheduler.getTrigger(key));
   }

   private Scenario start(String name) throws Exception {
      installContext(new MockCluster());
      newScheduler(name);
      scheduler.getListenerManager().addJobListener(
         new JobCompletionListener("TaskCompletionListener"));
      scheduler.start();

      String taskId = "task-81647-" + name;
      long t1 = (System.currentTimeMillis() / 1000 + 3) * 1000;
      // the old task has only trigger "-2" (an AT condition), which fires at t1 and runs 3 s
      scheduler.scheduleJob(job(taskId, RUN_MS), Set.of(trigger(taskId, 2, t1)), true);
      return new Scenario(taskId, t1);
   }

   private ClusterJobStore newScheduler(String name) throws Exception {
      DirectSchedulerFactory factory = DirectSchedulerFactory.getInstance();
      ClusterJobStore jobStore = new ClusterJobStore();
      jobStore.setMisfireThreshold(500);
      String schedulerName = "inetsoft-81647-" + name;
      factory.createScheduler(schedulerName, "node-A",
                              new SimpleThreadPool(3, Thread.NORM_PRIORITY), jobStore, null, 0,
                              2000, -1);
      scheduler = factory.getScheduler(schedulerName);
      // the singleton's removeTask acts on its Quartz scheduler
      Field field = Scheduler.class.getDeclaredField("scheduler");
      field.setAccessible(true);
      field.set(Scheduler.getScheduler(), scheduler);
      return jobStore;
   }

   private final class Scenario {
      Scenario(String taskId, long t1) {
         this.taskId = taskId;
         this.t1 = t1;
         this.jobKey = new JobKey(taskId, Scheduler.GROUP_NAME);
      }

      void sleepIntoRun() throws Exception {
         sleepUntil(t1 + 1000);
         assertEquals(1, RUNNING.get(), "the old run is in progress");
      }

      void waitForRunToComplete() throws Exception {
         sleepUntil(t1 + RUN_MS + 2000);
         assertEquals(0, RUNNING.get());
      }

      /** Scheduler.removeTask: mark the run, delete the job, clear the status. */
      void remove() throws Exception {
         remove(false);
      }

      /** The real Scheduler.removeTask, on the scheduler of the test. */
      void remove(boolean replacing) throws Exception {
         Scheduler.getScheduler().removeTask(taskId, replacing);
      }

      /** Scheduler.addTask(!first). */
      void replace(Set<Integer> indexes) throws Exception {
         scheduler.deleteJob(jobKey);
         store(indexes);
      }

      void store(Set<Integer> indexes) throws Exception {
         Set<Trigger> triggers = new HashSet<>();

         for(int index : indexes) {
            triggers.add(trigger(taskId, index, t1 + 30000L + index));
         }

         scheduler.scheduleJob(job(taskId, 100), triggers, true);
      }

      boolean exists(int index) throws Exception {
         return scheduler.getTrigger(trigger(taskId, index, 0).getKey()) != null;
      }

      /** The statuses set after the last clearInvocations (or the start), in order. */
      List<Scheduler.Status> statusesAfterStart() {
         ArgumentCaptor<Scheduler.Status> status = ArgumentCaptor.forClass(Scheduler.Status.class);
         verify(dao, atLeast(0)).setStatus(eq(taskId), status.capture(), anyLong(), anyLong(),
                                           any(), anyBoolean());
         return status.getAllValues().stream()
            .filter(s -> s != Scheduler.Status.STARTED)
            .toList();
      }

      private final String taskId;
      private final long t1;
      private final JobKey jobKey;
   }

   private static void sleepUntil(long time) throws InterruptedException {
      Thread.sleep(Math.max(0, time - System.currentTimeMillis()));
   }

   private static JobDetail job(String taskId, long duration) {
      ScheduleTask task = new ScheduleTask(taskId);
      task.setOwner(OWNER);
      JobDataMap dataMap = new JobDataMap();
      dataMap.put(ScheduleTask.class.getName(), task);
      dataMap.put("dur", duration);
      return JobBuilder.newJob(SlowJob.class)
         .withIdentity(taskId, Scheduler.GROUP_NAME)
         .storeDurably(true).usingJobData(dataMap).build();
   }

   private static TimeConditionTriggerImpl trigger(String taskId, int index, long time) {
      TimeConditionTriggerImpl trigger = new TimeConditionTriggerImpl();
      trigger.setName(taskId + "-" + index);
      trigger.setGroup(Scheduler.GROUP_NAME);
      trigger.setJobKey(new JobKey(taskId, Scheduler.GROUP_NAME));
      trigger.setCondition(TimeCondition.at(new Date(time)));
      trigger.setStartTime(new Date(time));
      trigger.setEndTime(new Date(time + TimeUnit.HOURS.toMillis(1)));
      trigger.setMisfireInstruction(ConditionTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
      return trigger;
   }

   private void installContext(Cluster cluster) {
      SecurityEngine engine = mock(SecurityEngine.class);
      SecurityProvider provider = mock(SecurityProvider.class);
      when(engine.getSecurityProvider()).thenReturn(provider);
      when(provider.getAuthenticationProvider()).thenReturn(mock(AuthenticationProvider.class));
      XSessionService sessionService = mock(XSessionService.class);
      when(sessionService.createSessionID(any(), any())).thenReturn("test-session-id");
      PropertiesEngine properties = mock(PropertiesEngine.class);
      when(properties.getProperty(eq("local.host.name"))).thenReturn("localhost");
      when(properties.getProperty(eq("local.host.name"), anyBoolean())).thenReturn("localhost");
      when(properties.getProperty(eq("local.host.name"), anyBoolean(), anyBoolean()))
         .thenReturn("localhost");
      when(properties.getProperty(any(String.class), any(String.class), anyBoolean()))
         .thenAnswer(inv -> inv.getArgument(1));
      LicenseManager licenseManager = mock(LicenseManager.class);
      when(licenseManager.getAvailableCpuCount()).thenReturn(2);
      dao = mock(ScheduleStatusDao.class);
      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(Cluster.class)).thenReturn(cluster);
      when(context.getBean(SecurityEngine.class)).thenReturn(engine);
      when(context.getBean(XSessionService.class)).thenReturn(sessionService);
      when(context.getBean(PropertiesEngine.class)).thenReturn(properties);
      when(context.getBean(LicenseManager.class)).thenReturn(licenseManager);
      when(context.getBean(ScheduleStatusDao.class)).thenReturn(dao);
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   @DisallowConcurrentExecution
   public static class SlowJob implements Job {
      @Override
      public void execute(JobExecutionContext context) {
         RUNNING.incrementAndGet();

         try {
            Thread.sleep(context.getMergedJobDataMap().getLong("dur"));
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
         finally {
            RUNNING.decrementAndGet();
         }
      }
   }

   private static final long RUN_MS = 3000;
   private static final AtomicInteger RUNNING = new AtomicInteger();
   private static final IdentityID OWNER = new IdentityID("scheduler-test", "host");
   private ScheduleStatusDao dao;
   private org.quartz.Scheduler scheduler;
   private ApplicationContext savedAppContext;
   private Principal savedPrincipal;
}
