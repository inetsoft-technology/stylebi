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

import inetsoft.sree.security.IdentityID;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ScheduleTaskTest {
   private ScheduleTask scheduleTask;

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

      IdentityID testUser = new IdentityID("testUser", "testOrg");
      scheduleTask.setUser(testUser);
      assertEquals(testUser.getName(), scheduleTask.getUser());

      scheduleTask.setDescription("Test Schedule Task");
      assertEquals("Test Schedule Task", scheduleTask.getDescription());

      assertEquals("task1", scheduleTask.toView(false));
      scheduleTask.setOwner(testUser);
      assertEquals("testUser:task1", scheduleTask.toView(true));
      assertEquals("testUser:task1", scheduleTask.toView(true, true));
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
