/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.ThreadContext;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.time.*;
import java.util.*;

/**
 * Class that handles balancing tasks over a time range.
 *
 * @since 13.1
 */
public class TaskBalancer {
   /**
    * Update the start time for the specified task and re-balance the time range, as necessary.
    *
    * @param updatedTask the updated task.
    * @param range       the affected time range.
    *
    * @throws Exception if the task could not be saved.
    */
   public void updateTask(ScheduleTask updatedTask, TimeRange range) throws Exception {
      LocalTime now = LocalTime.now();
      LocalTime start = range.getStartTime();
      LocalTime end = range.getEndTime();

      if(!isInRange(now, start, end)) {
         // just rebalance the entire time range
         balanceTasks(range);
         return;
      }

      Set<TimeRange> ranges = new HashSet<>(TimeRange.getTimeRanges());
      List<ScheduleTask> tasks = new ArrayList<>();
      List<TimeCondition> conditions = new ArrayList<>();
      boolean currentTaskAdded = false;

      // add all conditions for the time range whose current start time is after the current time
      for(ScheduleTask task : ScheduleManager.getScheduleManager().getScheduleTasks()) {
         boolean currentTask = task.equals(updatedTask);
         currentTaskAdded = currentTaskAdded || currentTask;
         boolean taskAdded = false;

         for(int i = 0; i < task.getConditionCount(); i++) {
            ScheduleCondition condition = task.getCondition(i);

            if(condition instanceof TimeCondition) {
               TimeCondition timeCondition = (TimeCondition) condition;

               if(isMatchingTimeRange(timeCondition, range, ranges) &&
                  (currentTask || getStartTime(timeCondition).isAfter(now)))
               {
                  conditions.add(timeCondition);

                  if(!taskAdded) {
                     tasks.add(task);
                     taskAdded = true;
                  }
               }
            }
         }
      }

      // the updated condition wasn't in the matched set of conditions, add manually
      if(!currentTaskAdded) {
         for(int i = 0; i < updatedTask.getConditionCount(); i++) {
            ScheduleCondition condition = updatedTask.getCondition(i);

            if(condition instanceof TimeCondition) {
               TimeCondition timeCondition = (TimeCondition) condition;

               if(range.equals(timeCondition.getTimeRange())) {
                  conditions.add(timeCondition);
               }
            }
         }

         tasks.add(updatedTask);
      }

      balanceTasks(range, tasks, conditions);
   }

   /**
    * Balances all tasks in time ranges.
    *
    * @throws Exception if the tasks could not be saved.
    */
   public void balanceTasks() throws Exception {
      for(TimeRange range : TimeRange.getTimeRanges()) {
         balanceTasks(range);
      }

      refreshTaskBalancerTriggers();
   }

   private void refreshTaskBalancerTriggers() throws Exception {
      ScheduleManager scheduleManager = ScheduleManager.getScheduleManager();
      Principal user = ThreadContext.getContextPrincipal();

      for(ScheduleTask task : scheduleManager.getScheduleTasks()) {
         for(int i = 0; i < task.getConditionCount(); i++) {
            ScheduleCondition condition = task.getCondition(i);

            if(condition instanceof TaskBalancerCondition) {
               AssetEntry folderEntry = null;

               if(!StringUtils.isEmpty(task.getPath())) {
                  folderEntry = new AssetEntry( AssetRepository.GLOBAL_SCOPE,
                                                AssetEntry.Type.SCHEDULE_TASK_FOLDER, task.getPath(), null);
               }

               scheduleManager.setScheduleTask(task.getTaskId(), task, folderEntry, user);
               break;
            }
         }
      }
   }

   /**
    * Balances the tasks in a time range.
    *
    * @param range the time range.
    *
    * @throws Exception if the tasks could not be saved.
    */
   void balanceTasks(TimeRange range) throws Exception {
      ScheduleManager scheduleManager = ScheduleManager.getScheduleManager();
      Set<TimeRange> ranges = new HashSet<>(TimeRange.getTimeRanges());
      List<ScheduleTask> tasks = new ArrayList<>();
      List<TimeCondition> conditions = new ArrayList<>();

      for(ScheduleTask task : scheduleManager.getScheduleTasks()) {
         boolean taskAdded = false;

         for(int i = 0; i < task.getConditionCount(); i++) {
            ScheduleCondition condition = task.getCondition(i);

            if(condition instanceof TimeCondition) {
               TimeCondition timeCondition = (TimeCondition) condition;

               if(timeCondition.getTimeRange() != null &&
                  isMatchingTimeRange(timeCondition, range, ranges))
               {
                  conditions.add(timeCondition);

                  if(!taskAdded) {
                     tasks.add(task);
                     taskAdded = true;
                  }
               }
            }
         }
      }

      balanceTasks(range, tasks, conditions);
   }

   /**
    * Balances the tasks in a time range.
    *
    * @param range      the time range.
    * @param tasks      the tasks to balance.
    * @param conditions the time range conditions.
    *
    * @throws Exception if the tasks could not be saved.
    */
   private void balanceTasks(TimeRange range,  List<ScheduleTask> tasks,
                             List<TimeCondition> conditions) throws Exception
   {
      if(conditions.isEmpty()) {
         return;
      }

      LocalTime start;
      LocalTime end = roundDown(range.getEndTime());
      LocalTime now = getCurrentTime();

      // If we are currently in the time range, change the start time window to the current time.
      // This ensures that no tasks will be missed this cycle by shifting the start time back before
      // the current time.
      if(isInRange(now, roundUp(range.getStartTime()), end)) {
         start = now;
      }
      else {
         start = roundUp(range.getStartTime());
      }

      // get the max scheduler concurrency
      int concurrency = Integer.parseInt(SreeEnv.getProperty("schedule.concurrency"));
      // duration (length) of the time range in minutes
      int duration = getDuration(start, end);
      // length of each task "slot" in minutes
      int interval = getInterval(concurrency, duration, conditions.size());

      // use bitmaps to mark which slots are filled
      List<BitSet> slots = new ArrayList<>();
      slots.add(new BitSet());

      // fill slots taken by hard-coded start times
      tasks.stream()
         .flatMap(ScheduleTask::getConditionStream)
         .filter(TimeCondition.class::isInstance)
         .map(TimeCondition.class::cast)
         .filter(c -> c.getTimeRange() == null)
         .forEach(c -> fillSlots(c, start, end, interval, slots));

      int hardCoded = slots.stream()
         .mapToInt(BitSet::cardinality)
         .sum();
      int slotsPerThread = duration / interval;
      int requiredThreads =
         Math.max(1, (int) Math.ceil((conditions.size() + hardCoded) / (float) slotsPerThread));
      int conditionIndex = 0;

      // fill up all but last thread's slots
      for(int i = 0; i < requiredThreads - 1; i++) {
         if(slots.size() == i) {
            slots.add(new BitSet());
         }

         BitSet bitSet = slots.get(i);

         for(int j = bitSet.nextClearBit(0); j < slotsPerThread;
             j = bitSet.nextClearBit(j))
         {
            TimeCondition condition = conditions.get(conditionIndex++);
            updateCondition(condition, j, interval, start, bitSet, range);
         }
      }

      BitSet last;

      if(slots.size() < requiredThreads) {
         last = new BitSet();
      }
      else {
         last = slots.get(requiredThreads - 1);
      }

      // distribute remaining tasks evenly in last thread's slots using divide and conquer approach
      List<Integer> remaining = new ArrayList<>();

      if(conditionIndex < conditions.size()) {
         remaining = distribute(conditionIndex, conditions.size(), 0,
                                slotsPerThread, conditions, last, interval, start, range);
      }

      // fill in any remaining tasks that didn't get assigned because of a hard-coded time to the
      // first empty slots
      for(int i = 0, j = last.nextClearBit(0); i < remaining.size(); i++, j = last.nextClearBit(j))
      {
         int n = remaining.get(i);
         TimeCondition condition = conditions.get(n);
         updateCondition(condition, j, interval, start, last, range);
      }

      // save tasks
      ScheduleManager scheduleManager = ScheduleManager.getScheduleManager();

      for(ScheduleTask task : tasks) {
         AssetEntry folderEntry = null;

         if(!StringUtils.isEmpty(task.getPath())) {
            folderEntry = new AssetEntry( AssetRepository.GLOBAL_SCOPE,
               AssetEntry.Type.SCHEDULE_TASK_FOLDER, task.getPath(), null);
         }

         scheduleManager.setScheduleTask(task.getTaskId(), task, folderEntry, true,
            ThreadContext.getContextPrincipal());
      }
   }

   /**
    * Distributes conditions evenly across single thread's worth of slots.
    *
    * @param minCondition the minimum condition index.
    * @param maxCondition the maximum condition index.
    * @param minSlot      the minimum slot index.
    * @param maxSlot      the maximum slot index.
    * @param conditions   the list of conditions.
    * @param slots        the bit set used to track the filled slots.
    * @param interval     the slot interval length in minutes.
    * @param start        the start time of the range.
    * @param range        the time range.
    *
    * @return a list of condition indexes that were not assigned a slot because it was taken by a
    *         hard-coded start time.
    */
   private List<Integer> distribute(int minCondition, int maxCondition, int minSlot, int maxSlot,
                                    List<TimeCondition> conditions, BitSet slots, int interval,
                                    LocalTime start, TimeRange range)
   {
      if(maxCondition - minCondition > 1) {
         int midCondition = minCondition + ((maxCondition - minCondition) / 2);
         int midSlot = minSlot + ((maxSlot - minSlot) / 2);
         List<Integer> result = distribute(
            minCondition, midCondition, minSlot, midSlot, conditions, slots, interval, start,
            range);
         result.addAll(distribute(
            midCondition, maxCondition, midSlot, maxSlot, conditions, slots, interval, start,
            range));
         return result;
      }

      int slot = slots.nextClearBit(minSlot);

      if(slot >= maxSlot) {
         // use array list instead of singleton list because it may be modified in the above block
         return new ArrayList<>(Collections.singletonList(minCondition));
      }

      TimeCondition condition = conditions.get(minCondition);
      updateCondition(condition, slot, interval, start, slots, range);
      // use array list instead of singleton list because it may be modified in the above block
      return new ArrayList<>();
   }

   /**
    * Updates the start time of a condition to a slot in a time range.
    *
    * @param condition the condition to update.
    * @param slot      the slot index.
    * @param interval  the slot interval length in minutes.
    * @param start     the start time of the range.
    * @param slots     the bit set used to track the used slots.
    * @param range     the time range.
    */
   private void updateCondition(TimeCondition condition, int slot, int interval, LocalTime start,
                                BitSet slots, TimeRange range)
   {
      LocalTime time = start.plusMinutes((long) slot * interval);
      condition.setHour(time.getHour());
      condition.setMinute(time.getMinute());
      condition.setSecond(time.getSecond());
      condition.setTimeRange(range);
      slots.set(slot);
   }

   /**
    * Gets the duration of a time range.
    *
    * @param start the start time.
    * @param end   the end time.
    *
    * @return the duration, in minutes.
    */
   private int getDuration(LocalTime start, LocalTime end) {
      int duration;

      if(start.isAfter(end)) {
         // wraps overnight
         duration = (int) Duration.ofHours(24).minus(Duration.between(end, start)).toMinutes();
      }
      else {
         duration = (int) Duration.between(start, end).toMinutes();
      }

      return duration;
   }

   /**
    * Find the optimal length of each "slot" in the time range. This is the maximum interval,
    * greater than 5 minutes, that minimizes concurrency.
    *
    * @param concurrency the maximum scheduler concurrency.
    * @param duration    the duration of the time range, in minutes.
    * @param conditions  the number of conditions in the range.
    *
    * @return the task interval in minutes.
    */
   private int getInterval(int concurrency, int duration, int conditions) {
      // number of 5 minute blocks
      int len = 0;

      for(int i = 1; i <= concurrency && len < 1; i++) {
         len = (duration * i) / (5 * conditions);
      }

      // number of minutes
      len = len * 5;

      // sanity check, integer division could result in zero for small ranges or large number of
      // conditions
      len = Math.max(1, len);
      return len;
   }

   /**
    * Fill in the slots for the specified condition.
    *
    * @param condition the condition.
    * @param start     the range start time.
    * @param end       the range end time.
    * @param interval  the range interval length in minutes.
    * @param slots     the bit maps used to track the filled slots.
    */
   private void fillSlots(TimeCondition condition, LocalTime start, LocalTime end, int interval,
                          List<BitSet> slots)
   {
      for(int slot : getSlot(condition, start, end, interval)) {
         boolean added = false;

         for(BitSet bitSet : slots) {
            if(bitSet.get(slot)) {
               added = true;
               break;
            }
         }

         if(!added) {
            BitSet bitSet = new BitSet();
            bitSet.set(slot);
            slots.add(bitSet);
         }
      }
   }

   /**
    * Gets the slot indexes for a condition in a time range.
    *
    * @param condition the condition.
    * @param start     the range start time.
    * @param end       the range end time.
    * @param interval  the range interval length in minutes.
    *
    * @return a list of the slot indexes.
    */
   private List<Integer> getSlot(TimeCondition condition, LocalTime start, LocalTime end,
                                 int interval)
   {
      LocalTime taskStart = getStartTime(condition);

      if(condition.getType() == TimeCondition.EVERY_HOUR) {
         LocalTime taskEnd = getEndTime(condition);

         if(taskEnd.equals(taskStart)) {
            // end time is the same as start time, just add one slot
            int slot = getSlot(taskStart, start, end, interval);
            return slot < 0 ? Collections.emptyList() : Collections.singletonList(slot);
         }

         // add slot for each interval between start and end time of condition
         List<Integer> slots = new ArrayList<>();
         long conditionInterval = (long) (condition.getHourlyInterval() * 60);

         while(taskStart.isBefore(taskEnd)) {
            int slot = getSlot(taskStart, start, end, interval);

            if(slot >= 0) {
               slots.add(slot);
            }

            taskStart = taskStart.plusMinutes(conditionInterval);
         }

         return slots;
      }

      int slot = getSlot(taskStart, start, end, interval);
      return slot < 0 ? Collections.emptyList() : Collections.singletonList(slot);
   }

   /**
    * Gets the start time for a condition.
    *
    * @param condition the condition.
    *
    * @return the start time.
    */
   private LocalTime getStartTime(TimeCondition condition) {
      if(condition.getType() == TimeCondition.AT) {
         return LocalDateTime.from(condition.getDate().toInstant().atZone(ZoneId.systemDefault()))
            .toLocalTime();
      }

      int m = Math.max(0, condition.getMinute());
      int s = Math.max(0, condition.getSecond());

      return LocalTime.of(condition.getHour(), m, s);
   }

   /**
    * Gets the end time for a condition.
    *
    * @param condition the condition.
    *
    * @return the end time.
    */
   private LocalTime getEndTime(TimeCondition condition) {
      return LocalTime.of(
         condition.getHourEnd(), condition.getMinuteEnd(), condition.getSecondEnd());
   }

   /**
    * Gets the slot index for a given time in a range.
    *
    * @param time the time to get the slot of.
    * @param start    the range start time.
    * @param end      the range end time.
    * @param interval the range interval length in minutes.
    *
    * @return the slot index or -1 if the time does not fall in the range.
    */
   private int getSlot(LocalTime time, LocalTime start, LocalTime end, int interval) {
      if(isInRange(time, start, end)) {
         int offset;

         if(start.isBefore(end) || !time.isBefore(start)) {
            // regular range or overnight range w/ time between start and midnight
            offset = (int) Duration.between(start, time).toMinutes();
         }
         else {
            // between midnight and end
            offset = (int) Duration.between(LocalTime.MIDNIGHT, time).toMinutes();
         }

         return offset / interval;
      }

      return -1;
   }

   /**
    * Determines if the specified time falls in a range.
    *
    * @param time  the time to check.
    * @param start the range start time.
    * @param end   the range end time.
    *
    * @return {@code true} if in the range; {@code false} if outside the range.
    */
   private boolean isInRange(LocalTime time, LocalTime start, LocalTime end) {
      if(start.isAfter(end)) {
         // wraps overnight, must be after start or before end
         return !time.isBefore(start) || time.isBefore(end);
      }
      else {
         return !time.isBefore(start) && time.isBefore(end);
      }
   }

   /**
    * Determines if a condition matches the specified time range.
    *
    * @param condition the condition to test.
    * @param range     the time range.
    * @param ranges    the set of all defined time ranges.
    *
    * @return {@code true} if the time range matches; {@code false} if it does not.
    */
   private boolean isMatchingTimeRange(TimeCondition condition, TimeRange range,
                                       Set<TimeRange> ranges)
   {
      if(condition.getTimeRange() == null) {
         return false;
      }

      if(ranges.contains(condition.getTimeRange())) {
         // no need to match
         return range.equals(condition.getTimeRange());
      }

      // time range has been changed, find best match
      return range.equals(TimeRange.getMatchingTimeRange(condition.getTimeRange(), ranges));
   }

   /**
    * Gets the current time, rounded up to the nearest 5 minutes.
    *
    * @return the current time.
    */
   private LocalTime getCurrentTime() {
      return roundUp(LocalTime.now());
   }

   /**
    * Rounds a time up to the nearest 5 minutes.
    *
    * @param time the time to round.
    *
    * @return the rounded time.
    */
   private LocalTime roundUp(LocalTime time) {
      int remainder = time.getMinute() % 5;

      if(remainder != 0) {
         return time.plusMinutes(5 - remainder);
      }

      return time;
   }

   /**
    * Rounds a time down to the nearest 5 minutes.
    *
    * @param time the time to round.
    *
    * @return the rounded time.
    */
   private LocalTime roundDown(LocalTime time) {
      int remainder = time.getMinute() % 5;

      if(remainder != 0) {
         return time.minusMinutes(remainder);
      }

      return time;
   }
}
