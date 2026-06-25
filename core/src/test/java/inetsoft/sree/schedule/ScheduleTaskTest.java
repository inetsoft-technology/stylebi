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

package inetsoft.sree.schedule;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * Cases deferred - require integration context:
 *
 * [ScheduleTask] run(Principal) / doRun(Principal) - parallel action execution with timeout
 *             -> needs a real thread pool and live ScheduleAction implementations; NOT yet covered
 * [ScheduleTask] cancel() - runtimeTask delegation path
 *             -> needs run() executing concurrently in a separate thread; NOT yet covered
 * [ScheduleTask] writeXML / parseXML - XML round-trip serialization
 *             -> needs full DOM/Spring context; NOT yet covered
 * [ScheduleTask] equals(Object) - multi-field comparison across conditions and actions
 *             -> deferred: requires constructing fully equal tasks with real condition/action equals()
 * [ScheduleTask] getRetryTime(long) - lastRun > 0 branch uses TimeCondition.getRetryTime(time, lastRun)
 *             -> needs the shared mock ScheduleStatusDao to return a status with lastScheduledStartTime > 0;
 *                requires reset(scheduleStatusDao) to avoid polluting testCheckRetryTime; NOT yet covered
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, ScheduleTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class ScheduleTaskTest {
   private ScheduleTask scheduleTask;
   @Autowired SecurityEngine securityEngine;

   @Test
   void testCheckRetryTime() {
      scheduleTask = spy(ScheduleTask.class);
      TimeCondition mockTimeCondition = mock(TimeCondition.class);
      when(mockTimeCondition.check(anyLong())).thenReturn(true);
      when(mockTimeCondition.getRetryTime(anyLong())).thenReturn(1L);

      scheduleTask.addCondition(mockTimeCondition);
      scheduleTask.setCondition(0, mockTimeCondition);

      ViewsheetAction spyVSAction = spy(ViewsheetAction.class);
      spyVSAction.setViewsheet("1^128^__NULL__^f1/vs1^host-org");
      scheduleTask.addAction(spyVSAction);
      scheduleTask.setAction(0, spyVSAction);

      scheduleTask.setName("task1");
      assertEquals("task1", scheduleTask.getName());

      //1. check
      assertTrue(scheduleTask.check(123658));

      //2. check retry time
      scheduleTask.setEnabled(false);
      assertFalse(scheduleTask.isEnabled());
      assertEquals(-1, scheduleTask.getRetryTime(1234568));

      scheduleTask.setEnabled(true);
      //no last run time
      assertEquals(1, scheduleTask.getRetryTime(1234568));
      // have last runtime  ??

      //3. check hasNextRuntime
      assertTrue(scheduleTask.hasNextRuntime(1234568));
      //completionCondition
      scheduleTask.removeCondition(0);
      CompletionCondition mockCompletionCondition = mock(CompletionCondition.class);
      scheduleTask.addCondition(mockCompletionCondition);
      scheduleTask.setCondition(0, mockCompletionCondition);

      assertFalse(scheduleTask.hasNextRuntime(1234568));
      when(scheduleTask.hasNextRuntime(anyLong())).thenReturn(true);
   }

   @Test
   void testRemoveActionContiditon() {
      scheduleTask = createBasicScheduleTask("task1");

      CompletionCondition mockCompletionCondition = mock(CompletionCondition.class);
      when(mockCompletionCondition.getTaskName()).thenReturn("task1");
      scheduleTask.addCondition(mockCompletionCondition);
      scheduleTask.setCondition(1, mockCompletionCondition);

      BatchAction batchAction = mock(BatchAction.class);
      scheduleTask.addAction(batchAction);
      scheduleTask.setAction(1, batchAction);

      scheduleTask.setName("task1");

      //0. check setComplete
      scheduleTask.setComplete("task1", true);
      CompletionCondition completionCondition = (CompletionCondition)scheduleTask.getCondition(1);
      assertFalse(completionCondition.check(anyLong()));

      //1. check remove condition
      scheduleTask.removeCondition(-1);
      assertEquals(2, scheduleTask.getConditionCount());

      scheduleTask.removeCondition(1);
      assertEquals(1, scheduleTask.getConditionCount());

      //2. check remove action
      scheduleTask.removeAction(0);
      assertEquals(1, scheduleTask.getActionCount());
   }

   @Test
   void checkEqual() {
      scheduleTask = createBasicScheduleTask("task1");

      ScheduleTask scheduleTask1 = scheduleTask.clone();
      assertEquals(scheduleTask.getConditionCount(), scheduleTask1.getConditionCount());

      ScheduleTask scheduleTask2 = new ScheduleTask();
      scheduleTask.copyTo(scheduleTask2);
      assertEquals(scheduleTask.getActionCount(), scheduleTask2.getActionCount());
   }

   @Test
   void testOtherSetGetMethod() {
      IdentityID testUser = new IdentityID("testUser", "testOrg");
      FSUser testFSUser = new FSUser(testUser);

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getUser(eq(testUser))).thenReturn(testFSUser);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[] { "host-org", "testOrg" });
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      scheduleTask = createBasicScheduleTask("task1");

      scheduleTask.setDeleteIfNoMoreRun(true);
      assertTrue(scheduleTask.isDeleteIfNoMoreRun());

      scheduleTask.setDurable(true);
      assertTrue(scheduleTask.isDurable());

      scheduleTask.setStartDate(toDate("2025-01-01T00:00:00"));
      assertEquals(toDate("2025-01-01T00:00:00"), scheduleTask.getStartDate());

      scheduleTask.setEndDate(toDate("2025-12-31T23:59:59"));
      assertEquals(toDate("2025-12-31T23:59:59"), scheduleTask.getEndDate());

      scheduleTask.setLocale("en_US");
      assertEquals("en_US", scheduleTask.getLocale());

      scheduleTask.setUser(testUser);
      assertEquals(testUser.getName(), scheduleTask.getUser());

      scheduleTask.setDescription("Test Schedule Task");
      assertEquals("Test Schedule Task", scheduleTask.getDescription());

      assertEquals("task1", scheduleTask.toView(false));
      scheduleTask.setOwner(testUser);
      assertEquals("testUser:task1", scheduleTask.toView(true));
      assertEquals("testUser:task1", scheduleTask.toView(true, true));
   }

   // --- getTaskId ---

   @Test
   void getTaskId_normalTask_noOwner_returnsName() {
      ScheduleTask task = new ScheduleTask("my-task");
      assertEquals("my-task", task.getTaskId());
   }

   @Test
   void getTaskId_normalTask_withOwner_returnsOwnerKeyColonName() {
      ScheduleTask task = new ScheduleTask("my-task");
      IdentityID owner = new IdentityID("alice", "org1");
      task.setOwner(owner);
      assertEquals(owner.convertToKey() + ":my-task", task.getTaskId());
   }

   @Test
   void getTaskId_cycleTask_returnsOwnerKeyDoubleUnderscoreName() {
      IdentityID owner = new IdentityID("alice", "org1");
      ScheduleTask task = new ScheduleTask("my-task", ScheduleTask.Type.CYCLE_TASK);
      task.setOwner(owner);
      assertEquals(owner.convertToKey() + "__my-task", task.getTaskId());
   }

   @Test
   void getTaskId_internalTask_returnsName() {
      ScheduleTask task = new ScheduleTask("internal-task", ScheduleTask.Type.INTERNAL_TASK);
      assertEquals("internal-task", task.getTaskId());
   }

   @Test
   void getTaskId_invalidatedBySetName() {
      IdentityID owner = new IdentityID("alice", "org1");
      ScheduleTask task = new ScheduleTask("original");
      task.setOwner(owner);
      assertEquals(owner.convertToKey() + ":original", task.getTaskId());
      task.setName("renamed");
      assertEquals(owner.convertToKey() + ":renamed", task.getTaskId());
   }

   // --- check ---

   @Test
   void check_allConditionsFalse_returnsFalse() {
      ScheduleTask task = new ScheduleTask("task");
      TimeCondition c1 = mock(TimeCondition.class);
      TimeCondition c2 = mock(TimeCondition.class);
      when(c1.check(anyLong())).thenReturn(false);
      when(c2.check(anyLong())).thenReturn(false);
      task.addCondition(c1);
      task.addCondition(c2);
      assertFalse(task.check(1000L));
   }

   @Test
   void check_allConditionsAlwaysEvaluated_evenAfterFirstTrue() {
      // The loop does not short-circuit so CompletionConditions get their state reset on every check call.
      ScheduleTask task = new ScheduleTask("task");
      TimeCondition c1 = mock(TimeCondition.class);
      TimeCondition c2 = mock(TimeCondition.class);
      when(c1.check(anyLong())).thenReturn(true);
      when(c2.check(anyLong())).thenReturn(false);
      task.addCondition(c1);
      task.addCondition(c2);
      assertTrue(task.check(1000L));
      verify(c1, times(1)).check(anyLong());
      verify(c2, times(1)).check(anyLong()); // must be called even though c1 already returned true
   }

   // --- getRetryTime ---

   @Test
   void getRetryTime_allConditionsReturnNegative_returnsNegative() {
      ScheduleTask task = new ScheduleTask("task");
      TimeCondition cond = mock(TimeCondition.class);
      when(cond.getRetryTime(anyLong())).thenReturn(-1L);
      task.addCondition(cond);
      assertEquals(-1L, task.getRetryTime(1000L));
   }

   // --- setComplete ---

   @Test
   void setComplete_whenTaskIsRunning_doesNotUpdateCondition() throws Exception {
      ScheduleTask task = new ScheduleTask("task");
      CompletionCondition cc = new CompletionCondition("parent-task");
      task.addCondition(cc);

      // simulate task in-progress without calling run()
      Field runningField = ScheduleTask.class.getDeclaredField("running");
      runningField.setAccessible(true);
      runningField.setBoolean(task, true);

      task.setComplete("parent-task", true);

      // cc.setComplete(true) was not called because of the early return; check() returns default false
      assertFalse(cc.check(0L));
   }

   // --- renameDependency ---

   @Test
   void renameDependency_existingDependency_isUpdated() {
      ScheduleTask task = new ScheduleTask("task");
      task.addCondition(new CompletionCondition("old-parent"));

      task.renameDependency("old-parent", "new-parent");

      Enumeration<String> deps = task.getDependency();
      assertEquals("new-parent", deps.nextElement());
      assertFalse(deps.hasMoreElements());
   }

   @Test
   void renameDependency_nonExistentName_doesNotChangeDependency() {
      ScheduleTask task = new ScheduleTask("task");
      task.addCondition(new CompletionCondition("parent"));

      task.renameDependency("no-such-task", "new-name");

      Enumeration<String> deps = task.getDependency();
      assertEquals("parent", deps.nextElement());
      assertFalse(deps.hasMoreElements());
   }

   // --- addCondition / removeCondition dependency tracking ---

   @Test
   void addAndRemoveCompletionCondition_dependencyTracking() {
      ScheduleTask task = new ScheduleTask("task");
      CompletionCondition cc = new CompletionCondition("parent-task");
      task.addCondition(cc);

      Enumeration<String> depsAfterAdd = task.getDependency();
      assertTrue(depsAfterAdd.hasMoreElements());
      assertEquals("parent-task", depsAfterAdd.nextElement());

      task.removeCondition(0);
      assertFalse(task.getDependency().hasMoreElements());
   }

   private ScheduleTask createBasicScheduleTask(String taskName) {
      ScheduleTask scheduleTask = new ScheduleTask(taskName);
      TimeCondition mockTimeCondition = mock(TimeCondition.class);
      scheduleTask.addCondition(mockTimeCondition);
      scheduleTask.setCondition(0, mockTimeCondition);

      ViewsheetAction spyVSAction = spy(ViewsheetAction.class);
      spyVSAction.setViewsheet("1^128^__NULL__^f1/vs1^host-org");
      scheduleTask.addAction(spyVSAction);
      scheduleTask.setAction(0, spyVSAction);

      return scheduleTask;
   }

   private Date toDate(String localDateTime) {
      return Date.from(LocalDateTime.parse(localDateTime)
                          .atZone(ZoneId.systemDefault())  //  ZoneId.systemDefault()
                          .toInstant());
   }
}
