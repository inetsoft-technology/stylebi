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
package inetsoft.web.admin.schedule;

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.MessageException;
import inetsoft.web.admin.schedule.model.ScheduleTaskList;
import inetsoft.web.admin.schedule.model.TaskOptionsPaneModel;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.security.Principal;
import java.time.LocalTime;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class ScheduleTaskServiceTest {

   @Mock
   private AnalyticRepository analyticRepository;
   @Mock
   private ScheduleManager scheduleManager;
   @Mock
   private ScheduleService scheduleService;
   @Mock
   private Principal principal;

   private ScheduleTaskService service;

   @BeforeEach
   void setUp() {
      service = new ScheduleTaskService(
         analyticRepository, scheduleManager, scheduleService, null, null, null, null);
   }

   // ── redistributeTasks ────────────────────────────────────────────────────

   @Test
   void redistributeTasks_noTimeConditions_throwsMessageException() throws Exception {
      ScheduleTask task = new ScheduleTask("task1");
      when(scheduleManager.getScheduleTask("task1")).thenReturn(task);

      MessageException ex = assertThrows(MessageException.class, () ->
         service.redistributeTasks(
            LocalTime.of(0, 0), LocalTime.of(23, 0), 4, List.of("task1"), principal));

      assertFalse(ex.getMessage().isEmpty(),
         "the localized message must not be empty -- ensures the em.schedule." +
         "distribution.noTimeConditions catalog key resolved rather than falling back");
      verify(scheduleService, never()).saveTask(any(), any(), any());
   }

   @Test
   void redistributeTasks_emptySelection_throwsMessageException() {
      assertThrows(MessageException.class, () ->
         service.redistributeTasks(
            LocalTime.of(0, 0), LocalTime.of(23, 0), 4, List.of(), principal));
   }

   @Test
   void redistributeTasks_hasTimeCondition_redistributesWithoutError() throws Exception {
      ScheduleTask task = new ScheduleTask("task1");
      TimeCondition tc = new TimeCondition();
      tc.setType(TimeCondition.EVERY_DAY);
      tc.setHour(1);
      tc.setMinute(0);
      task.addCondition(tc);

      when(scheduleManager.getScheduleTask("task1")).thenReturn(task);
      when(scheduleService.getScheduleTaskList("", "", principal))
         .thenReturn(mock(ScheduleTaskList.class));

      ScheduleTaskList result = service.redistributeTasks(
         LocalTime.of(0, 0), LocalTime.of(23, 0), 4, List.of("task1"), principal);

      assertNotNull(result);
      verify(scheduleService).saveTask("task1", task, principal);
   }

   // ── redistributeTasks arithmetic, pinned (Bug #82395 characterization) ───

   private ScheduleTask dailyTask(String id) {
      ScheduleTask task = new ScheduleTask(id);
      TimeCondition tc = new TimeCondition();
      tc.setType(TimeCondition.EVERY_DAY);
      tc.setHour(9);
      tc.setMinute(59);
      tc.setSecond(30);
      task.addCondition(tc);
      when(scheduleManager.getScheduleTask(id)).thenReturn(task);
      return task;
   }

   private List<ScheduleTask> dailyTasks(int count) {
      List<ScheduleTask> tasks = new java.util.ArrayList<>();

      for(int i = 0; i < count; i++) {
         tasks.add(dailyTask("t" + i));
      }

      return tasks;
   }

   private void redistribute(LocalTime start, LocalTime end, int concurrency,
                             List<ScheduleTask> tasks) throws Exception
   {
      when(scheduleService.getScheduleTaskList("", "", principal))
         .thenReturn(mock(ScheduleTaskList.class));
      service.redistributeTasks(start, end, concurrency,
                                tasks.stream().map(ScheduleTask::getTaskId).toList(), principal);
   }

   private static String timeOf(TimeCondition tc) {
      return String.format("%02d:%02d:%02d", tc.getHour(), tc.getMinute(), tc.getSecond());
   }

   private static String timeOf(ScheduleTask task) {
      return timeOf((TimeCondition) task.getCondition(0));
   }

   private static List<String> times(List<ScheduleTask> tasks) {
      return tasks.stream().map(ScheduleTaskServiceTest::timeOf).toList();
   }

   @Test
   void redistributeTasks_largeInterval_snapsDownToOneHour() throws Exception {
      // 240 minutes / 3 conditions = 80 -> snapped to 60
      List<ScheduleTask> tasks = dailyTasks(3);
      redistribute(LocalTime.of(1, 0), LocalTime.of(5, 0), 1, tasks);
      assertEquals(List.of("01:00:00", "02:00:00", "03:00:00"), times(tasks));
   }

   @Test
   void redistributeTasks_intervalOfExactlySixty_snapsDownToThirty() throws Exception {
      // 180 minutes / 3 conditions = 60, which is not > 60, so it falls to the next bucket (30)
      List<ScheduleTask> tasks = dailyTasks(3);
      redistribute(LocalTime.of(0, 0), LocalTime.of(3, 0), 1, tasks);
      assertEquals(List.of("00:00:00", "00:30:00", "01:00:00"), times(tasks));
   }

   @Test
   void redistributeTasks_smallInterval_snapsDownToFiveAndKeepsSecondsZero() throws Exception {
      // 60 minutes / 10 conditions = 6 -> snapped to 5
      List<ScheduleTask> tasks = dailyTasks(10);
      redistribute(LocalTime.of(0, 0), LocalTime.of(1, 0), 1, tasks);
      assertEquals(List.of("00:00:00", "00:05:00", "00:10:00", "00:15:00", "00:20:00",
                           "00:25:00", "00:30:00", "00:35:00", "00:40:00", "00:45:00"),
                   times(tasks));
   }

   @Test
   void redistributeTasks_concurrencyGrowsAndTimesWrapAround() throws Exception {
      // 10 conditions in 60 minutes with up to 4 concurrent: concurrency grows to 3,
      // interval = 60 / ceil(10 / 3) = 15 -> snapped to 10; after 00:50 the time wraps to 00:00
      List<ScheduleTask> tasks = dailyTasks(10);
      redistribute(LocalTime.of(0, 0), LocalTime.of(1, 0), 4, tasks);
      assertEquals(List.of("00:00:00", "00:10:00", "00:20:00", "00:30:00", "00:40:00",
                           "00:50:00", "00:00:00", "00:10:00", "00:20:00", "00:30:00"),
                   times(tasks));
   }

   @Test
   void redistributeTasks_intervalBelowOneMinute_isZeroSoEveryConditionGetsTheStart()
      throws Exception
   {
      // 1 minute / 10 conditions = 0 minutes, left as is
      List<ScheduleTask> tasks = dailyTasks(10);
      redistribute(LocalTime.of(0, 0), LocalTime.of(0, 1), 1, tasks);
      assertEquals(java.util.Collections.nCopies(10, "00:00:00"), times(tasks));
   }

   @Test
   void redistributeTasks_everyHour_movesTheEndToKeepTheWindowLength() throws Exception {
      ScheduleTask task = new ScheduleTask("hourly");
      TimeCondition tc = new TimeCondition();
      tc.setType(TimeCondition.EVERY_HOUR);
      tc.setHour(1);
      tc.setMinute(0);
      tc.setSecond(0);
      tc.setHourEnd(1);
      tc.setMinuteEnd(30);
      tc.setSecondEnd(15);
      task.addCondition(tc);
      when(scheduleManager.getScheduleTask("hourly")).thenReturn(task);

      redistribute(LocalTime.of(2, 0), LocalTime.of(5, 0), 1, List.of(task));

      // window length is 30m15s, the end is the new start plus that, seconds of the end are 0
      assertEquals("02:00:00", timeOf(tc));
      assertEquals(2, tc.getHourEnd());
      assertEquals(30, tc.getMinuteEnd());
      assertEquals(0, tc.getSecondEnd());
   }

   @Test
   void redistributeTasks_weeklyAndMonthly_getTimesInOrder() throws Exception {
      ScheduleTask task = new ScheduleTask("mixed");
      TimeCondition weekly = new TimeCondition();
      weekly.setType(TimeCondition.EVERY_WEEK);
      weekly.setHour(9);
      weekly.setMinute(59);
      weekly.setSecond(30);
      TimeCondition monthly = new TimeCondition();
      monthly.setType(TimeCondition.EVERY_MONTH);
      monthly.setHour(9);
      monthly.setMinute(59);
      monthly.setSecond(30);
      task.addCondition(weekly);
      task.addCondition(monthly);
      when(scheduleManager.getScheduleTask("mixed")).thenReturn(task);

      redistribute(LocalTime.of(0, 0), LocalTime.of(4, 0), 1, List.of(task));

      assertEquals("00:00:00", timeOf(weekly));
      assertEquals("01:00:00", timeOf(monthly));
   }

   @Test
   void redistributeTasks_runOnceAndDayOfWeek_areCountedButNotChanged() throws Exception {
      ScheduleTask first = new ScheduleTask("first");
      TimeCondition runOnce = new TimeCondition();
      runOnce.setType(TimeCondition.AT);
      runOnce.setHour(7);
      runOnce.setMinute(7);
      TimeCondition dayOfWeek = new TimeCondition();
      dayOfWeek.setType(TimeCondition.DAY_OF_WEEK);
      dayOfWeek.setHour(8);
      dayOfWeek.setMinute(8);
      first.addCondition(runOnce);
      first.addCondition(dayOfWeek);
      when(scheduleManager.getScheduleTask("first")).thenReturn(first);
      ScheduleTask second = dailyTask("second");
      ScheduleTask third = dailyTask("third");

      redistribute(LocalTime.of(0, 0), LocalTime.of(3, 0), 1, List.of(first, second, third));

      assertEquals("07:07:00", String.format("%02d:%02d:00", runOnce.getHour(),
                                             runOnce.getMinute()));
      assertEquals("08:08:00", String.format("%02d:%02d:00", dayOfWeek.getHour(),
                                             dayOfWeek.getMinute()));
      // all 4 conditions are counted (180 / 4 = 45 -> 30 minutes); the two that are skipped do
      // not advance the time, so the daily ones land on 00:00 and 00:30. Counting only the
      // changed conditions would give an interval of 60.
      assertEquals("00:00:00", timeOf(second));
      assertEquals("00:30:00", timeOf(third));
      verify(scheduleService, never()).saveTask(eq("first"), any(), any());
   }

   @Test
   void redistributeTasks_followsTheOrderOfTheNames() throws Exception {
      List<ScheduleTask> tasks = dailyTasks(3);
      when(scheduleService.getScheduleTaskList("", "", principal))
         .thenReturn(mock(ScheduleTaskList.class));

      service.redistributeTasks(LocalTime.of(1, 0), LocalTime.of(5, 0), 1,
                                List.of("t2", "t0", "t1"), principal);

      assertEquals("01:00:00", timeOf(tasks.get(2)));
      assertEquals("02:00:00", timeOf(tasks.get(0)));
      assertEquals("03:00:00", timeOf(tasks.get(1)));
   }

   // ── Bug #77213: don't mutate cached (extension) task instances ───────────

   @Test
   void redistributeTasks_skipsDataCycleTasks() throws Exception {
      ScheduleTask task = new ScheduleTask("task1");
      TimeCondition tc = new TimeCondition();
      tc.setType(TimeCondition.EVERY_DAY);
      tc.setHour(5);
      task.addCondition(tc);
      ScheduleTask cycleTask = new ScheduleTask("DataCycle Task: c1", ScheduleTask.Type.CYCLE_TASK);
      cycleTask.setOwner(new IdentityID("INETSOFT_SYSTEM", "host-org"));
      TimeCondition cycleCondition = new TimeCondition();
      cycleCondition.setType(TimeCondition.EVERY_DAY);
      cycleCondition.setHour(5);
      cycleTask.addCondition(cycleCondition);
      when(scheduleManager.getScheduleTask("task1")).thenReturn(task);
      when(scheduleManager.getScheduleTask("cycle1")).thenReturn(cycleTask);
      when(scheduleService.getScheduleTaskList("", "", principal))
         .thenReturn(mock(ScheduleTaskList.class));

      service.redistributeTasks(
         LocalTime.of(0, 0), LocalTime.of(23, 0), 4, List.of("cycle1", "task1"), principal);

      verify(scheduleService).saveTask(any(), same(task), eq(principal));
      verify(scheduleService, never()).saveTask(any(), same(cycleTask), any());
      assertEquals(5, cycleCondition.getHour(), "the cached data cycle task must not be changed");
   }

   @Test
   void setTaskEnabled_savesCopyAndLeavesCachedTaskUnchanged() throws Exception {
      ScheduleTask cached = new ScheduleTask("DataCycle Task: c1", ScheduleTask.Type.CYCLE_TASK);
      cached.setEnabled(true);
      when(scheduleManager.getScheduleTask("cycle1")).thenReturn(cached);

      service.setTaskEnabled("cycle1", false, principal);

      assertTrue(cached.isEnabled(), "the cached task instance must not be mutated");
      verify(scheduleService).saveTask(eq("cycle1"),
         argThat((ScheduleTask t) -> t != cached && !t.isEnabled()), eq(principal));
   }

   // ── setTaskOptions (Bug #77120) ──────────────────────────────────────────

   @Test
   void setTaskOptions_unchangedUnresolvableIdentity_isKept() {
      IdentityID id = new IdentityID("g1", "host-org");
      Group placeholder = new Group(id);
      ScheduleTask task = new ScheduleTask("task1");
      task.setIdentity(placeholder);

      Identity result = applyOptions(task, "g1", Identity.GROUP);

      assertSame(placeholder, result,
                 "an edit that leaves execute-as unchanged must not clear an unresolved identity");
   }

   @Test
   void setTaskOptions_changedUnresolvableIdentity_isNotKept() {
      ScheduleTask task = new ScheduleTask("task1");
      task.setIdentity(new Group(new IdentityID("g1", "host-org")));

      assertNull(applyOptions(task, "g2", Identity.GROUP));
      task.setIdentity(new Group(new IdentityID("g1", "host-org")));
      assertNull(applyOptions(task, "g1", Identity.ROLE));
   }

   private Identity applyOptions(ScheduleTask task, String idName, int idType) {
      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID(principal)).thenReturn("host-org");
      TaskOptionsPaneModel model = TaskOptionsPaneModel.builder()
         .enabled(true)
         .deleteIfNotScheduledToRun(false)
         .securityEnabled(true)
         .owner("admin")
         .idName(idName)
         .idType(idType)
         .build();

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<OrganizationManager> orgs = mockStatic(OrganizationManager.class))
      {
         orgs.when(OrganizationManager::getInstance).thenReturn(orgManager);
         sutil.when(() -> SUtil.getIdentity(any(), anyInt())).thenReturn(null);
         sutil.when(SUtil::loadLocaleProperties).thenReturn(new Properties());

         service.setTaskOptions(model, task, principal);
      }

      return task.getIdentity();
   }
}
