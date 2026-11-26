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
import inetsoft.web.admin.schedule.TaskRedistributor;
import inetsoft.web.admin.ai.schedule.RedistributePlan.RedistributedTask;
import inetsoft.web.admin.ai.schedule.RedistributePlan.SkippedTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Works out, without saving anything, what Enterprise Manager's Balance Tasks would do to a list
 * of tasks, for the admin plugin: the new conditions of every task that changes. The arithmetic is
 * {@link TaskRedistributor}'s, the code Balance Tasks itself runs, applied to copies of the stored
 * tasks. The plugin turns each result into an ordinary update of the task, which goes through the
 * schedule change plan engine (preview, sign-off, drift check, apply, rollback).
 *
 * <p>Differences from Balance Tasks, all of them refusals or reports rather than other results:
 * the request is validated (window order and range, concurrency, empty or repeated task list)
 * with field-named messages instead of an unhandled exception; a built-in task is refused; a task
 * that holds a condition other than a time or completion condition is skipped, because an update
 * of this area cannot carry such a condition and would drop it; and every requested task that does
 * not change is reported with its reason instead of being dropped silently.
 */
@Component
public class ScheduleRedistributionService {
   @Autowired
   public ScheduleRedistributionService(ScheduleManager scheduleManager) {
      this.scheduleManager = scheduleManager;
   }

   /**
    * @throws IllegalArgumentException with a field-named message when the request is malformed,
    *         names a built-in task, or names a task with an hourly condition that has no end time.
    */
   public RedistributePlan plan(RedistributePlanRequest req) throws Exception {
      if(req == null) {
         throw new IllegalArgumentException("request: required");
      }

      LocalTime start = parseTime("startTime", req.getStartTime());
      LocalTime end = parseTime("endTime", req.getEndTime());

      if(!start.isBefore(end)) {
         throw new IllegalArgumentException(
            "startTime: " + req.getStartTime().trim() + " must be before endTime (" +
            req.getEndTime().trim() + "); the window cannot wrap past midnight");
      }

      int concurrency = req.getMaxConcurrency() == null ? 1 : req.getMaxConcurrency();

      if(concurrency < 1) {
         throw new IllegalArgumentException(
            "maxConcurrency: expected an integer of at least 1; got " + concurrency);
      }

      List<String> taskIds = req.getTaskIds();

      if(taskIds == null || taskIds.isEmpty()) {
         throw new IllegalArgumentException("taskIds: at least one task id is required");
      }

      List<SkippedTask> skipped = new ArrayList<>();
      // stored task and the copy the arithmetic runs on, in the requested order
      Map<String, ScheduleTask> originals = new LinkedHashMap<>();
      Map<String, ScheduleTask> copies = new LinkedHashMap<>();
      // what the copies hold before the arithmetic runs, to tell which of them it changed
      Map<String, List<String>> before = new HashMap<>();
      Set<String> seen = new HashSet<>();

      for(int i = 0; i < taskIds.size(); i++) {
         String id = taskIds.get(i);

         if(id == null || id.isBlank()) {
            throw new IllegalArgumentException("taskIds[" + i + "]: a task id is required");
         }

         if(!seen.add(id)) {
            throw new IllegalArgumentException(
               "taskIds[" + i + "]: \"" + id + "\" is listed more than once; list each task at " +
               "most once");
         }

         ScheduleTask stored = scheduleManager.getScheduleTask(id);

         if(stored == null) {
            skipped.add(new SkippedTask(
               id, "no task with this id exists in the current organization"));
         }
         else if(!stored.isRemovable()) {
            throw new IllegalArgumentException(
               "taskIds[" + i + "]: \"" + id + "\" is a built-in/system task and cannot be " +
               "redistributed");
         }
         else if(stored.getType() == ScheduleTask.Type.CYCLE_TASK) {
            skipped.add(new SkippedTask(
               id, "a data cycle task is scheduled by its data cycle, not by its own conditions"));
         }
         else if(hasUnsupportedCondition(stored)) {
            skipped.add(new SkippedTask(
               id, "it has a condition other than a time or completion condition, which this " +
               "area cannot patch without dropping it"));
         }
         else {
            requireHourlyEnds(i, id, stored);
            originals.put(id, stored);
            ScheduleTask copy = ScheduleChangesetApplyService.deepCopy(stored);
            copies.put(id, copy);
            before.put(id, signature(copy));
         }
      }

      List<ScheduleTask> candidates = new ArrayList<>(copies.values());
      long count = TaskRedistributor.countTimeConditions(candidates);

      if(count > 0) {
         try {
            TaskRedistributor.redistribute(candidates, start, end, concurrency, task -> {
            });
         }
         catch(DateTimeException e) {
            throw new IllegalArgumentException(
               "taskIds: a time condition holds a time that cannot be moved: " + e.getMessage());
         }
      }

      List<RedistributedTask> changed = new ArrayList<>();

      for(Map.Entry<String, ScheduleTask> entry : copies.entrySet()) {
         ScheduleTask original = originals.get(entry.getKey());
         ScheduleTask copy = entry.getValue();

         if(signature(copy).equals(before.get(entry.getKey()))) {
            skipped.add(new SkippedTask(entry.getKey(), unchangedReason(original)));
         }
         else {
            changed.add(new RedistributedTask(
               entry.getKey(),
               copy.getConditionStream()
                  .map(ScheduleConditionConverter::convertCondition)
                  .collect(Collectors.toList())));
         }
      }

      long interval = count > 0 ? TaskRedistributor.interval(start, end, concurrency, count) : 0L;
      return new RedistributePlan(changed, skipped, interval);
   }

   private static LocalTime parseTime(String field, String value) {
      if(value == null || value.isBlank()) {
         throw new IllegalArgumentException(
            field + ": required, a time of day like \"01:00\" (HH:mm, 24-hour)");
      }

      Matcher matcher = TIME.matcher(value.trim());

      if(!matcher.matches()) {
         throw new IllegalArgumentException(
            field + ": expected a time of day like \"01:00\" (HH:mm, 24-hour); got \"" + value + "\"");
      }

      int hour = Integer.parseInt(matcher.group(1));
      int minute = Integer.parseInt(matcher.group(2));

      if(hour > 23 || minute > 59) {
         throw new IllegalArgumentException(
            field + ": \"" + value + "\" is not a valid time of day (hour 0-23, minute 0-59)");
      }

      return LocalTime.of(hour, minute);
   }

   private static boolean hasUnsupportedCondition(ScheduleTask task) {
      return task.getConditionStream().anyMatch(
         c -> !(c instanceof TimeCondition) && !(c instanceof CompletionCondition));
   }

   /**
    * An hourly condition without an end time is stored with a negative end, and moving it would
    * fail on that end, so it is named here instead of surfacing as an unhandled exception.
    */
   private static void requireHourlyEnds(int index, String id, ScheduleTask task) {
      boolean unbounded = task.getConditionStream()
         .filter(TimeCondition.class::isInstance)
         .map(TimeCondition.class::cast)
         .anyMatch(c -> c.getType() == TimeCondition.EVERY_HOUR &&
            (c.getHourEnd() < 0 || c.getMinuteEnd() < 0 || c.getSecondEnd() < 0));

      if(unbounded) {
         throw new IllegalArgumentException(
            "taskIds[" + index + "]: \"" + id + "\" has an hourly condition with no end time, " +
            "which cannot be moved into a window; give the condition an end time first or leave " +
            "the task out");
      }
   }

   /** The fields a redistribution can change, per condition, in order. */
   private static List<String> signature(ScheduleTask task) {
      return task.getConditionStream()
         .filter(TimeCondition.class::isInstance)
         .map(TimeCondition.class::cast)
         .map(c -> c.getType() + "|" + c.getHour() + ":" + c.getMinute() + ":" + c.getSecond() +
            "|" + c.getHourEnd() + ":" + c.getMinuteEnd() + ":" + c.getSecondEnd())
         .collect(Collectors.toList());
   }

   private static String unchangedReason(ScheduleTask original) {
      List<TimeCondition> times = original.getConditionStream()
         .filter(TimeCondition.class::isInstance)
         .map(TimeCondition.class::cast)
         .collect(Collectors.toList());

      if(times.isEmpty()) {
         return "it has no time condition";
      }

      if(times.stream().noneMatch(c -> MOVABLE.contains(c.getType()))) {
         return "its time conditions are run-once or day-of-week conditions, which Balance Tasks " +
            "does not move";
      }

      return "its time conditions already sit where the window places them";
   }

   private static final Pattern TIME = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
   private static final Set<Integer> MOVABLE = Set.of(
      TimeCondition.EVERY_HOUR, TimeCondition.EVERY_DAY, TimeCondition.EVERY_WEEK,
      TimeCondition.EVERY_MONTH);
   private final ScheduleManager scheduleManager;
}
