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
package inetsoft.web.admin.ai.schedule;

import inetsoft.sree.schedule.CompletionCondition;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.security.IdentityID;
import inetsoft.web.admin.ai.schedule.RedistributePlan.RedistributedTask;
import inetsoft.web.admin.ai.schedule.RedistributePlan.SkippedTask;
import inetsoft.web.api.schedule.ScheduleCondition;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The redistribution plan is the arithmetic of Enterprise Manager's Balance Tasks run on copies;
 * the placement itself is pinned by {@code ScheduleTaskServiceTest}. These tests cover what the
 * plan adds: validation, the tasks that are left out and why, full condition lists, and that the
 * stored tasks are never touched.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScheduleRedistributionServiceTest {
   @Mock private ScheduleManager scheduleManager;
   private ScheduleRedistributionService service;

   @BeforeEach void setup() {
      service = new ScheduleRedistributionService(scheduleManager);
   }

   private static RedistributePlanRequest request(String start, String end, Integer concurrency,
                                                  String... taskIds)
   {
      RedistributePlanRequest req = new RedistributePlanRequest();
      req.setStartTime(start);
      req.setEndTime(end);
      req.setMaxConcurrency(concurrency);
      req.setTaskIds(List.of(taskIds));
      return req;
   }

   private static TimeCondition time(int type, int hour, int minute) {
      TimeCondition tc = new TimeCondition();
      tc.setType(type);
      tc.setHour(hour);
      tc.setMinute(minute);
      tc.setSecond(0);
      return tc;
   }

   private ScheduleTask stored(String id, inetsoft.sree.schedule.ScheduleCondition... conditions) {
      ScheduleTask task = new ScheduleTask(id);
      task.setOwner(new IdentityID("admin", "host-org"));

      for(inetsoft.sree.schedule.ScheduleCondition condition : conditions) {
         task.addCondition(condition);
      }

      when(scheduleManager.getScheduleTask(id)).thenReturn(task);
      return task;
   }

   private static inetsoft.web.api.schedule.TimeCondition dto(RedistributedTask task, int index) {
      return (inetsoft.web.api.schedule.TimeCondition) task.conditions().get(index);
   }

   @Test void movesEveryTaskAndLeavesTheStoredTasksAlone() throws Exception {
      ScheduleTask a = stored("a", time(TimeCondition.EVERY_DAY, 9, 0));
      ScheduleTask b = stored("b", time(TimeCondition.EVERY_DAY, 9, 0));

      RedistributePlan plan = service.plan(request("01:00", "05:00", null, "a", "b"));

      // 240 minutes / 2 conditions = 120 -> snapped to 60
      assertEquals(2, plan.tasks().size());
      assertEquals("a", plan.tasks().get(0).taskId());
      assertEquals(1, dto(plan.tasks().get(0), 0).getHour());
      assertEquals(0, dto(plan.tasks().get(0), 0).getMinute());
      assertEquals(2, dto(plan.tasks().get(1), 0).getHour());
      assertEquals(60L, plan.intervalMinutes());
      assertTrue(plan.skipped().isEmpty());
      assertEquals(9, ((TimeCondition) a.getCondition(0)).getHour(), "the stored task changed");
      assertEquals(9, ((TimeCondition) b.getCondition(0)).getHour(), "the stored task changed");
   }

   @Test void carriesTheWholeConditionListOfAChangedTask() throws Exception {
      CompletionCondition completion = new CompletionCondition();
      completion.setTaskName("admin~;~host-org:upstream");
      stored("a", time(TimeCondition.EVERY_DAY, 9, 0), completion);

      RedistributePlan plan = service.plan(request("01:00", "05:00", null, "a"));

      List<ScheduleCondition> conditions = plan.tasks().get(0).conditions();
      assertEquals(2, conditions.size());
      assertInstanceOf(inetsoft.web.api.schedule.TimeCondition.class, conditions.get(0));
      assertInstanceOf(inetsoft.web.api.schedule.CompletionCondition.class, conditions.get(1));
   }

   @Test void omitsATaskThatAlreadySitsWhereTheWindowPutsIt() throws Exception {
      stored("a", time(TimeCondition.EVERY_DAY, 1, 0));

      RedistributePlan plan = service.plan(request("01:00", "05:00", null, "a"));

      assertTrue(plan.tasks().isEmpty());
      assertEquals(
         List.of(new SkippedTask("a", "its time conditions already sit where the window " +
                                      "places them")),
         plan.skipped());
   }

   @Test void reportsUnknownDataCycleAndUnsupportedConditionTasks() throws Exception {
      when(scheduleManager.getScheduleTask("missing")).thenReturn(null);
      ScheduleTask cycle = new ScheduleTask("cycle", ScheduleTask.Type.CYCLE_TASK);
      cycle.setOwner(new IdentityID("INETSOFT_SYSTEM", "host-org"));
      cycle.addCondition(time(TimeCondition.EVERY_DAY, 5, 0));
      when(scheduleManager.getScheduleTask("cycle")).thenReturn(cycle);
      stored("other", time(TimeCondition.EVERY_DAY, 9, 0),
             new inetsoft.sree.schedule.NeverRunCondition());
      stored("good", time(TimeCondition.EVERY_DAY, 9, 0));

      RedistributePlan plan = service.plan(
         request("01:00", "05:00", null, "missing", "cycle", "other", "good"));

      assertEquals(List.of("good"),
                   plan.tasks().stream().map(RedistributedTask::taskId).toList());
      assertEquals(List.of("missing", "cycle", "other"),
                   plan.skipped().stream().map(SkippedTask::taskId).toList());
      assertEquals(5, ((TimeCondition) cycle.getCondition(0)).getHour());
   }

   @Test void aTaskWithOnlyRunOnceConditionsIsReportedAndStillCounted() throws Exception {
      TimeCondition once = time(TimeCondition.AT, 7, 7);
      once.setDate(new java.util.Date(0L));
      stored("once", once);
      stored("daily", time(TimeCondition.EVERY_DAY, 9, 0));
      stored("daily2", time(TimeCondition.EVERY_DAY, 9, 0));

      RedistributePlan plan = service.plan(
         request("00:00", "03:00", null, "once", "daily", "daily2"));

      // three conditions are counted: 180 / 3 = 60 -> 30 minutes, as Balance Tasks would do
      assertEquals(30L, plan.intervalMinutes());
      assertEquals(0, dto(plan.tasks().get(0), 0).getMinute());
      assertEquals(30, dto(plan.tasks().get(1), 0).getMinute());
      assertTrue(plan.skipped().get(0).reason().contains("run-once or day-of-week"));
   }

   @Test void noTimeConditionAnywhereIsAnEmptyPlanNotAnError() throws Exception {
      stored("a");

      RedistributePlan plan = service.plan(request("01:00", "05:00", null, "a"));

      assertTrue(plan.tasks().isEmpty());
      assertEquals(0L, plan.intervalMinutes());
      assertEquals("it has no time condition", plan.skipped().get(0).reason());
   }

   @Test void refusesABuiltInTask() {
      ScheduleTask system = new ScheduleTask("__balance tasks__");
      system.setRemovable(false);
      when(scheduleManager.getScheduleTask("__balance tasks__")).thenReturn(system);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.plan(request("01:00", "05:00", null, "__balance tasks__")));
      assertTrue(ex.getMessage().startsWith("taskIds[0]:"), ex.getMessage());
      assertTrue(ex.getMessage().contains("built-in/system task"), ex.getMessage());
   }

   @Test void namesTheTaskWhoseHourlyConditionHasNoEnd() {
      TimeCondition hourly = time(TimeCondition.EVERY_HOUR, 1, 0);
      hourly.setHourEnd(23);
      hourly.setMinuteEnd(-1);
      hourly.setSecondEnd(-1);
      stored("hourly", hourly);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.plan(request("01:00", "05:00", null, "hourly")));
      assertTrue(ex.getMessage().startsWith("taskIds[0]: \"hourly\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("no end time"), ex.getMessage());
   }

   @Test void movesAnHourlyConditionWithAnEndAndKeepsItsWindowLength() throws Exception {
      TimeCondition hourly = time(TimeCondition.EVERY_HOUR, 1, 0);
      hourly.setHourEnd(1);
      hourly.setMinuteEnd(30);
      hourly.setSecondEnd(0);
      stored("hourly", hourly);

      RedistributePlan plan = service.plan(request("02:00", "05:00", null, "hourly"));

      inetsoft.web.api.schedule.TimeCondition moved = dto(plan.tasks().get(0), 0);
      assertEquals(2, moved.getHour());
      assertEquals(2, moved.getHourEnd());
      assertEquals(30, moved.getMinuteEnd());
   }

   @Test void refusesAMalformedRequestNamingTheField() {
      assertRefused("startTime", request(null, "05:00", null, "a"));
      assertRefused("startTime", request("1am", "05:00", null, "a"));
      assertRefused("endTime", request("01:00", "25:00", null, "a"));
      assertRefused("endTime", request("01:00", "05:60", null, "a"));
      assertRefused("startTime", request("05:00", "01:00", null, "a"));
      assertRefused("startTime", request("05:00", "05:00", null, "a"));
      assertRefused("maxConcurrency", request("01:00", "05:00", 0, "a"));
      assertRefused("maxConcurrency", request("01:00", "05:00", -3, "a"));
      assertRefused("taskIds", request("01:00", "05:00", null));
      assertRefused("taskIds[1]", request("01:00", "05:00", null, "a", "a"));
      assertRefused("taskIds[0]", request("01:00", "05:00", null, " "));

      RedistributePlanRequest noList = request("01:00", "05:00", null, "a");
      noList.setTaskIds(null);
      assertRefused("taskIds", noList);
      assertRefused("request", null);
   }

   private void assertRefused(String field, RedistributePlanRequest req) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.plan(req));
      assertTrue(ex.getMessage().startsWith(field + ":"),
                 "expected a message starting with " + field + ": but got: " + ex.getMessage());
   }
}
