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
package inetsoft.sree.schedule.quartz;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.Group;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.sree.security.OrganizationContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;

import java.security.Principal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77168 -- with security disabled, a non-null {@code task.getIdentity()} may now be an
 * unresolved execute-as placeholder that {@code ScheduleTask.parseXML} keeps unconditionally
 * (Bug #77120/#77168), instead of always being {@code null} the way it was before. Because
 * {@code SUtil.getPrincipal(Identity, ...)} unconditionally fabricates a real (but meaningless)
 * principal for such a placeholder, {@link ScheduleTaskJob#execute} must only take the
 * identity-principal branch when security is enabled -- otherwise it must fall back to the
 * owner, exactly as it already does when {@code identity == null}.
 *
 * <p>{@code execute()} calls {@code ThreadContext.setContextPrincipal(principal)} and never
 * clears it -- by design, since in production this runs on a dedicated Quartz worker thread that
 * is about to execute the task under that principal. Run directly on the JUnit thread, that would
 * otherwise leak a mock principal into every later test sharing this JVM/thread, so it is reset in
 * {@link #tearDown()}.
 */
@ExtendWith(MockitoExtension.class)
@Tag("core")
class ScheduleTaskJobTest {
   @BeforeEach
   void saveThreadState() {
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);
      OrganizationContextHolder.clear();
      // an interrupt that arrives while execute() is finishing leaves the flag on this thread
      Thread.interrupted();
   }

   // ----- interrupt handling and the cancelled flag -----

   @Test
   void interruptBeforeAnyExecutionDoesNothingAndLaterRunIsNotMarkedCancelled() throws Exception {
      try(Rig rig = new Rig(false)) {
         ScheduleTaskJob job = new ScheduleTaskJob();

         assertDoesNotThrow(job::interrupt);

         JobDataMap map = new JobDataMap();
         rig.behavior = inv -> null;
         job.execute(rig.context(map));

         assertNull(map.get(CANCELLED), "a run after a no-op interrupt must not be cancelled");
         verify(rig.task, never()).cancel();
      }
   }

   @Test
   void interruptDuringRunEndsExecuteNormallyAndMarksTheRunCancelled() throws Exception {
      try(Rig rig = new Rig(false)) {
         ScheduleTaskJob job = new ScheduleTaskJob();
         JobDataMap map = new JobDataMap();

         runInterrupted(rig, job, map, 1);

         assertEquals(Boolean.TRUE, map.get(CANCELLED));
         verify(rig.task).cancel();
      }
   }

   @Test
   void unrequestedInterruptedExceptionFailsTheRunWithoutCancelledFlag() throws Exception {
      try(Rig rig = new Rig(false)) {
         ScheduleTaskJob job = new ScheduleTaskJob();
         JobDataMap map = new JobDataMap();
         rig.behavior = inv -> {
            throw new InterruptedException("not requested by interrupt()");
         };

         JobExecutionException error =
            assertThrows(JobExecutionException.class, () -> job.execute(rig.context(map)));

         assertTrue(error.getMessage().contains("was interrupted unexpectedly"),
                    error.getMessage());
         assertNull(map.get(CANCELLED));
      }
   }

   @Test
   void interruptingTwiceGivesTheSameOutcomeAsInterruptingOnce() throws Exception {
      try(Rig rig = new Rig(false)) {
         ScheduleTaskJob job = new ScheduleTaskJob();
         JobDataMap map = new JobDataMap();

         assertDoesNotThrow(() -> runInterrupted(rig, job, map, 2));
         Thread.interrupted();

         assertEquals(Boolean.TRUE, map.get(CANCELLED));
      }
   }

   @Test
   void interruptAfterTheRunEndedDoesNotAffectTheNextRun() throws Exception {
      try(Rig rig = new Rig(false)) {
         ScheduleTaskJob job = new ScheduleTaskJob();
         rig.behavior = inv -> null;
         job.execute(rig.context(new JobDataMap()));

         job.interrupt();

         JobDataMap second = new JobDataMap();
         job.execute(rig.context(second));

         assertNull(second.get(CANCELLED));
         verify(rig.task, never()).cancel();
      }
   }

   @Test
   void interruptedRunDoesNotMakeTheNextRunOfTheSameInstanceLookInterrupted() throws Exception {
      try(Rig rig = new Rig(false)) {
         ScheduleTaskJob job = new ScheduleTaskJob();
         runInterrupted(rig, job, new JobDataMap(), 1);

         JobDataMap second = new JobDataMap();
         rig.behavior = inv -> {
            throw new InterruptedException("not requested by interrupt()");
         };

         JobExecutionException error =
            assertThrows(JobExecutionException.class, () -> job.execute(rig.context(second)));

         assertTrue(error.getMessage().contains("was interrupted unexpectedly"),
                    "the interrupted state must be reset after the first run: " +
                    error.getMessage());
         assertNull(second.get(CANCELLED));
      }
   }

   @Test
   void pinnedCurrentBehaviorTaskFailureAfterStopStillFailsExecuteAndIsMarkedCancelled()
      throws Exception
   {
      try(Rig rig = new Rig(false)) {
         ScheduleTaskJob job = new ScheduleTaskJob();
         JobDataMap map = new JobDataMap();
         CountDownLatch started = new CountDownLatch(1);
         rig.behavior = inv -> {
            started.countDown();

            try {
               new CountDownLatch(1).await();
            }
            catch(InterruptedException e) {
               throw new IllegalStateException("failed while stopping");
            }

            return null;
         };
         Thread stopper = stopperThread(started, job, 1);

         JobExecutionException error =
            assertThrows(JobExecutionException.class, () -> job.execute(rig.context(map)));
         stopper.join(5000);

         assertTrue(error.getCause() instanceof IllegalStateException, String.valueOf(error));
         assertEquals(Boolean.TRUE, map.get(CANCELLED),
                      "the run is marked cancelled even though the task failed");
      }
   }

   @Test
   void organizationContextIsRestoredAfterNormalInterruptedAndFailingRuns() throws Exception {
      try(Rig rig = new Rig(false)) {
         when(rig.task.getOwner()).thenReturn(new IdentityID("owner", "o1"));
         ScheduleTaskJob job = new ScheduleTaskJob();

         OrganizationContextHolder.setCurrentOrgId("o0");
         rig.behavior = inv -> {
            assertEquals("o1", OrganizationContextHolder.getCurrentOrgId(),
                         "the task must run in the owner's organization");
            return null;
         };
         job.execute(rig.context(new JobDataMap()));
         assertEquals("o0", OrganizationContextHolder.getCurrentOrgId(), "after a normal run");

         runInterrupted(rig, job, new JobDataMap(), 1);
         assertEquals("o0", OrganizationContextHolder.getCurrentOrgId(), "after an interrupt");

         rig.behavior = inv -> {
            throw new IllegalStateException("boom");
         };
         assertThrows(JobExecutionException.class,
                      () -> job.execute(rig.context(new JobDataMap())));
         assertEquals("o0", OrganizationContextHolder.getCurrentOrgId(), "after a failure");
      }
   }

   @Test
   @Disabled("Bug #81691: Internal schedule task inherits the previous user task's principal on the same worker thread")
   void internalTaskDoesNotSeeThePreviousTasksPrincipal() throws Exception {
      try(Rig rig = new Rig(true)) {
         ThreadContext.setContextPrincipal(mock(SRPrincipal.class));
         AtomicReference<Principal> seen = new AtomicReference<>();
         rig.behavior = inv -> {
            seen.set(ThreadContext.getContextPrincipal());
            return null;
         };

         new ScheduleTaskJob().execute(rig.context(new JobDataMap()));

         assertNull(seen.get());
      }
   }

   /**
    * Runs execute() on this thread while another thread calls interrupt() the given number of
    * times once the task is running.
    */
   private static void runInterrupted(Rig rig, ScheduleTaskJob job, JobDataMap map, int times)
      throws Exception
   {
      CountDownLatch started = new CountDownLatch(1);
      rig.behavior = inv -> {
         started.countDown();
         new CountDownLatch(1).await();
         return null;
      };
      Thread stopper = stopperThread(started, job, times);
      job.execute(rig.context(map));
      stopper.join(5000);
   }

   private static Thread stopperThread(CountDownLatch started, ScheduleTaskJob job, int times) {
      Thread stopper = new Thread(() -> {
         try {
            started.await();

            for(int i = 0; i < times; i++) {
               job.interrupt();
            }
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      });
      stopper.setDaemon(true);
      stopper.start();
      return stopper;
   }

   /**
    * The static mocks and the task mock of one ScheduleTaskJob run. The static mocks are opened
    * on the calling thread, so execute() has to run on that same thread.
    */
   private static final class Rig implements AutoCloseable {
      Rig(boolean internal) {
         tool = mockStatic(Tool.class, org.mockito.Answers.CALLS_REAL_METHODS);
         scheduleManager = mockStatic(ScheduleManager.class);
         sUtil = mockStatic(SUtil.class);
         tool.when(Tool::getIP).thenReturn("127.0.0.1");
         scheduleManager.when(() -> ScheduleManager.isInternalTask(anyString()))
            .thenReturn(internal);

         if(!internal) {
            SRPrincipal runPrincipal = mock(SRPrincipal.class);
            sUtil.when(() -> SUtil.getScheduleTaskRunPrincipal(any(), anyString(), anyBoolean()))
               .thenReturn(runPrincipal);
         }

         sUtil.when(() -> SUtil.runTask(any(), any(), any())).thenAnswer(inv -> behavior.answer(inv));
         task = mock(ScheduleTask.class);
      }

      JobExecutionContext context(JobDataMap map) {
         map.put(ScheduleTask.class.getName(), task);
         JobDetail jobDetail = mock(JobDetail.class);
         when(jobDetail.getKey()).thenReturn(new JobKey("t1", "group"));
         when(jobDetail.getJobDataMap()).thenReturn(map);
         JobExecutionContext context = mock(JobExecutionContext.class);
         when(context.getJobDetail()).thenReturn(jobDetail);
         return context;
      }

      @Override
      public void close() {
         sUtil.close();
         scheduleManager.close();
         tool.close();
      }

      volatile Answer<Object> behavior = inv -> null;
      final ScheduleTask task;
      private final MockedStatic<Tool> tool;
      private final MockedStatic<ScheduleManager> scheduleManager;
      private final MockedStatic<SUtil> sUtil;
   }

   private static final String CANCELLED = "inetsoft.cancelled";
   private Principal savedPrincipal;

   @Test
   void execute_securityDisabled_placeholderIdentity_fallsBackToOwnerPrincipal() throws Exception {
      IdentityID owner = new IdentityID("owner1", "host-org");
      IdentityID unresolvedGroup = new IdentityID("phantom-group", "host-org");

      ScheduleTask task = mock(ScheduleTask.class);
      when(task.getOwner()).thenReturn(owner);
      when(task.getIdentity()).thenReturn(new Group(unresolvedGroup));

      SRPrincipal ownerPrincipal = mock(SRPrincipal.class);

      try(MockedStatic<Tool> tool = mockStatic(Tool.class, org.mockito.Answers.CALLS_REAL_METHODS);
          MockedStatic<ScheduleManager> scheduleManager = mockStatic(ScheduleManager.class);
          MockedStatic<SecurityEngine> securityEngineStatic = mockStatic(SecurityEngine.class);
          MockedStatic<SUtil> sUtil = mockStatic(SUtil.class))
      {
         tool.when(Tool::getIP).thenReturn("127.0.0.1");
         // Bug #77452, the job picks the principal with SUtil.getScheduleTaskRunPrincipal, run
         // it for real so that it calls the stubbed methods below
         sUtil.when(() -> SUtil.getScheduleTaskRunPrincipal(any(), anyString(), anyBoolean()))
            .thenCallRealMethod();
         scheduleManager.when(() -> ScheduleManager.isInternalTask(anyString())).thenReturn(false);

         SecurityEngine engine = mock(SecurityEngine.class);
         when(engine.isSecurityEnabled()).thenReturn(false);
         securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(engine);

         sUtil.when(() -> SUtil.getScheduleTaskOwnerPrincipal(eq(owner), anyString(), eq(true)))
            .thenReturn(ownerPrincipal);

         JobExecutionContext context = buildContext("t1", task);
         assertDoesNotThrow(() -> new ScheduleTaskJob().execute(context));

         // the placeholder identity must never reach SUtil.getPrincipal(Identity, ...)
         sUtil.verify(() -> SUtil.getPrincipal(any(Identity.class), anyString(), anyBoolean()),
                       never());
         sUtil.verify(() -> SUtil.runTask(same(ownerPrincipal), same(task), anyString()));
      }
   }

   @Test
   void execute_securityEnabled_resolvableIdentity_usesIdentityPrincipal() throws Exception {
      IdentityID owner = new IdentityID("owner1", "host-org");
      IdentityID groupId = new IdentityID("g1", "host-org");
      Group group = new Group(groupId);

      ScheduleTask task = mock(ScheduleTask.class);
      when(task.getOwner()).thenReturn(owner);
      when(task.getIdentity()).thenReturn(group);

      SRPrincipal identityPrincipal = mock(SRPrincipal.class);

      try(MockedStatic<Tool> tool = mockStatic(Tool.class, org.mockito.Answers.CALLS_REAL_METHODS);
          MockedStatic<ScheduleManager> scheduleManager = mockStatic(ScheduleManager.class);
          MockedStatic<SecurityEngine> securityEngineStatic = mockStatic(SecurityEngine.class);
          MockedStatic<SUtil> sUtil = mockStatic(SUtil.class))
      {
         tool.when(Tool::getIP).thenReturn("127.0.0.1");
         // Bug #77452, the job picks the principal with SUtil.getScheduleTaskRunPrincipal, run
         // it for real so that it calls the stubbed methods below
         sUtil.when(() -> SUtil.getScheduleTaskRunPrincipal(any(), anyString(), anyBoolean()))
            .thenCallRealMethod();
         scheduleManager.when(() -> ScheduleManager.isInternalTask(anyString())).thenReturn(false);

         SecurityEngine engine = mock(SecurityEngine.class);
         when(engine.isSecurityEnabled()).thenReturn(true);
         securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(engine);

         sUtil.when(() -> SUtil.getPrincipal(same(group), anyString(), eq(true)))
            .thenReturn(identityPrincipal);

         JobExecutionContext context = buildContext("t1", task);
         assertDoesNotThrow(() -> new ScheduleTaskJob().execute(context));

         sUtil.verify(() -> SUtil.getScheduleTaskOwnerPrincipal(any(), anyString(), anyBoolean()),
                       never());
         sUtil.verify(() -> SUtil.runTask(same(identityPrincipal), same(task), anyString()));
      }
   }

   @Test
   void execute_internalTask_doesNotSeePreviousTasksPrincipal() throws Exception {
      // Bug #81691, a previous user task left its principal on the reused worker thread
      ThreadContext.setContextPrincipal(mock(SRPrincipal.class));

      ScheduleTask task = mock(ScheduleTask.class);
      when(task.getOwner()).thenReturn(new IdentityID("owner1", "host-org"));
      java.util.concurrent.atomic.AtomicReference<java.security.Principal> seen =
         new java.util.concurrent.atomic.AtomicReference<>();

      try(MockedStatic<Tool> tool = mockStatic(Tool.class, org.mockito.Answers.CALLS_REAL_METHODS);
          MockedStatic<ScheduleManager> scheduleManager = mockStatic(ScheduleManager.class);
          MockedStatic<SUtil> sUtil = mockStatic(SUtil.class))
      {
         tool.when(Tool::getIP).thenReturn("127.0.0.1");
         scheduleManager.when(() -> ScheduleManager.isInternalTask(anyString())).thenReturn(true);
         sUtil.when(() -> SUtil.runTask(any(), any(), anyString())).thenAnswer(inv -> {
            seen.set(ThreadContext.getContextPrincipal());
            return null;
         });

         JobExecutionContext context = buildContext("internal1", task);
         assertDoesNotThrow(() -> new ScheduleTaskJob().execute(context));
      }

      org.junit.jupiter.api.Assertions.assertNull(seen.get());
   }

   private static JobExecutionContext buildContext(String taskName, ScheduleTask task) {
      JobDataMap dataMap = new JobDataMap();
      dataMap.put(ScheduleTask.class.getName(), task);

      JobDetail jobDetail = mock(JobDetail.class);
      when(jobDetail.getKey()).thenReturn(new JobKey(taskName, "group"));
      when(jobDetail.getJobDataMap()).thenReturn(dataMap);

      JobExecutionContext context = mock(JobExecutionContext.class);
      when(context.getJobDetail()).thenReturn(jobDetail);
      return context;
   }
}
