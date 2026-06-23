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

import org.junit.jupiter.api.*;

import java.time.Duration;

import static inetsoft.sree.schedule.Scheduler.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Layer 1 — Quartz wiring tests.
 *
 * <p>Verifies the execution pipeline: condition fires Quartz job → HarnessTaskJob.execute() →
 * JobCompletionListener.jobWasExecuted() → ScheduleStatusDao status write → CompletionCondition
 * chain trigger.
 *
 * <p>Does NOT test condition timing logic (covered by TimeConditionTest / CompletionConditionTest).
 */
@Tag("core")
class ScheduleTaskCompletionFlowTest {

   SchedulerTestHarness harness;

   @BeforeEach
   void setUp() throws Exception {
      harness = new SchedulerTestHarness();
   }

   @AfterEach
   void tearDown() {
      harness.close();
   }

   // P1 — CompletionCondition chain trigger: Task A finishes → Task B is triggered automatically

   @Test
   void completionCondition_taskAFinishes_triggerTaskB() throws Exception {
      ScheduleTask taskA = buildTask("taskA-chain-success");
      ScheduleTask taskB = buildTask("taskB-chain-success");
      taskB.addCondition(new CompletionCondition(taskA.getTaskId()));

      harness.registerTask(taskA);
      harness.registerTask(taskB);
      harness.triggerNow(taskA.getTaskId());

      harness.waitForStatus(taskA.getTaskId(), FINISHED, Duration.ofSeconds(5));
      harness.waitForStatus(taskB.getTaskId(), FINISHED, Duration.ofSeconds(5));
   }

   // P1 — Chain blocked: Task A fails → Task B must NOT fire

   @Test
   void completionCondition_taskAFails_doesNotTriggerTaskB() throws Exception {
      ScheduleTask taskA = buildFailingTask("taskA-chain-fail");
      ScheduleTask taskB = buildTask("taskB-chain-fail");
      taskB.addCondition(new CompletionCondition(taskA.getTaskId()));

      harness.registerTask(taskA);
      harness.registerTask(taskB);
      harness.triggerNow(taskA.getTaskId());

      harness.waitForStatus(taskA.getTaskId(), FAILED, Duration.ofSeconds(5));

      // Give the chain mechanism time to fire if it incorrectly triggers Task B
      Thread.sleep(800);

      ScheduleStatusDao.Status statusB = harness.getDao().getStatus(taskB.getTaskId());
      assertNull(statusB, "Task B must not be triggered when Task A fails");
   }

   // P3 — Direct Quartz.triggerJob() bypasses the service-layer enabled check.
   //
   // UI "Run Now" path (ScheduleService.runScheduledTask):
   //   Explicitly checks !task.isEnabled() and throws MessageException before calling runNow().
   //   The task never executes; the HTTP response is 500 with an error message
   //   ("em.scheduler.startDisabledTask"). No status entry is written.
   //
   // Direct triggerJob() path (used here and internally by ScheduleManager):
   //   Quartz.triggerJob() does NOT consult task.isEnabled(). The production ScheduleTaskJob
   //   also does not inspect isEnabled() before calling task.run(). The task executes and
   //   FINISHED is written to ScheduleStatusDao — even though the task is disabled.
   //
   // CompletionCondition and disabled tasks:
   //   JobCompletionListener's chain-trigger loop skips disabled dependent tasks explicitly
   //   (JobCompletionListener.java: `if(!task.isEnabled()) { continue; }`).
   //   A disabled task B is never triggered when task A finishes — intentional, not a bug.
   //   See disabledDependentTask_isNotTriggeredByCompletionCondition() below.

   @Test
   void disabledTask_triggeredDirectly_stillReachesFinished() throws Exception {
      ScheduleTask task = buildTask("disabled-task");
      task.setEnabled(false);

      harness.registerTask(task);
      harness.triggerNow(task.getTaskId());

      harness.waitForStatus(task.getTaskId(), FINISHED, java.time.Duration.ofSeconds(5));
   }

   // P3 — CompletionCondition does NOT trigger a disabled dependent task.
   // JobCompletionListener skips disabled jobs in the chain-trigger loop, so task B
   // (disabled) is never fired even when task A finishes successfully.

   @Test
   void disabledDependentTask_isNotTriggeredByCompletionCondition() throws Exception {
      ScheduleTask taskA = buildTask("taskA-disabled-dep");
      ScheduleTask taskB = buildTask("taskB-disabled-dep");
      taskB.setEnabled(false);
      taskB.addCondition(new CompletionCondition(taskA.getTaskId()));

      harness.registerTask(taskA);
      harness.registerTask(taskB);
      harness.triggerNow(taskA.getTaskId());

      harness.waitForStatus(taskA.getTaskId(), FINISHED, Duration.ofSeconds(5));

      // Give the chain mechanism time to fire if it incorrectly triggers task B
      Thread.sleep(800);

      ScheduleStatusDao.Status statusB = harness.getDao().getStatus(taskB.getTaskId());
      assertNull(statusB, "Disabled task B must not be triggered by CompletionCondition");
   }

   // -----------------------------------------------------------------------
   // Helpers
   // -----------------------------------------------------------------------

   private ScheduleTask buildTask(String name) {
      ScheduleTask task = new ScheduleTask(name);
      // Owner must be set before getTaskId() is ever called.
      // setOwner() resets the cached id, so any call to getTaskId() after this
      // produces the owner-qualified id that matches the Quartz job key.
      task.setOwner(SchedulerTestHarness.TEST_OWNER);
      task.setEnabled(true);
      task.addAction(mock(ScheduleAction.class));
      return task;
   }

   private ScheduleTask buildFailingTask(String name) {
      ScheduleAction failingAction = mock(ScheduleAction.class);
      try {
         org.mockito.Mockito.doThrow(new RuntimeException("simulated failure"))
                            .when(failingAction).run(org.mockito.ArgumentMatchers.any());
      }
      catch(Throwable ignored) {}

      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(SchedulerTestHarness.TEST_OWNER);
      task.setEnabled(true);
      task.addAction(failingAction);
      return task;
   }
}
