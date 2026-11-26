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
package inetsoft.web.admin.schedule;

import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.TimeCondition;

import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The arithmetic of Enterprise Manager's Balance Tasks: spreads the time conditions of a list of
 * tasks over a time window. Shared by {@link ScheduleTaskService#redistributeTasks}, which applies
 * it to the cached tasks and saves them, and by the admin plugin's redistribute plan, which applies
 * it to copies to show what would change, so the two cannot drift apart.
 */
public final class TaskRedistributor {
   private TaskRedistributor() {
   }

   /** Receives each task whose conditions were changed. */
   @FunctionalInterface
   public interface ModifiedTaskHandler {
      void modified(ScheduleTask task) throws Exception;
   }

   /**
    * Counts the time conditions of the tasks, every kind of time condition included, also those
    * {@link #redistribute} leaves unchanged.
    */
   public static long countTimeConditions(List<ScheduleTask> tasks) {
      return tasks.stream()
         .flatMap(ScheduleTask::getConditionStream)
         .filter(TimeCondition.class::isInstance)
         .count();
   }

   /**
    * Places the time conditions of the tasks, in order, one {@code interval} apart from
    * {@code startTime}, wrapping back to the start when the end is reached. The conditions are
    * changed in place. The interval is the window divided over the conditions (at most
    * {@code maxConcurrency} of them starting together when the window is crowded), snapped down to
    * 60/30/15/10/5 minutes. Run-once and day-of-week conditions are counted but not moved.
    *
    * @param onModified called with each task right after its conditions were changed and before the
    *                   next task is touched, so a caller can save the tasks one by one.
    *
    * @throws IllegalArgumentException if the tasks have no time condition at all.
    * @throws Exception anything {@code onModified} throws, which stops the redistribution.
    */
   public static void redistribute(List<ScheduleTask> tasks, LocalTime startTime,
                                   LocalTime endTime, int maxConcurrency,
                                   ModifiedTaskHandler onModified) throws Exception
   {
      long count = countTimeConditions(tasks);

      if(count == 0) {
         throw new IllegalArgumentException("the tasks have no time condition to redistribute");
      }

      long interval = interval(startTime, endTime, maxConcurrency, count);
      LocalTime time = LocalTime.from(startTime);

      for(ScheduleTask task : tasks) {
         boolean modified = false;

         for(int i = 0; i < task.getConditionCount(); i++) {
            if(task.getCondition(i) instanceof TimeCondition) {
               if(!time.isBefore(endTime)) {
                  time = LocalTime.from(startTime);
               }

               TimeCondition condition = (TimeCondition) task.getCondition(i);

               switch(condition.getType()) {
               case TimeCondition.EVERY_HOUR:
                  LocalTime conditionStart = LocalTime.of(
                     condition.getHour(), condition.getMinute(), condition.getSecond());
                  LocalTime conditionEnd = LocalTime.of(
                     condition.getHourEnd(), condition.getMinuteEnd(), condition.getSecondEnd());
                  Duration conditionDuration = Duration.between(conditionStart, conditionEnd);
                  conditionEnd = time.plus(conditionDuration);
                  condition.setHourEnd(conditionEnd.getHour());
                  condition.setMinuteEnd(conditionEnd.getMinute());
                  condition.setSecondEnd(0);
               case TimeCondition.EVERY_DAY:
               case TimeCondition.EVERY_WEEK:
               case TimeCondition.EVERY_MONTH:
                  condition.setHour(time.getHour());
                  condition.setMinute(time.getMinute());
                  condition.setSecond(0);
                  break;
               default:
                  continue;
               }

               // set task start time to time
               time = time.plusMinutes(interval);
               modified = true;
            }
         }

         if(modified) {
            onModified.modified(task);
         }
      }
   }

   /**
    * The distance in minutes between two placed conditions: the window divided over the
    * conditions, with the concurrency raised (up to {@code maxConcurrency}) while that leaves less
    * than 10 minutes, then snapped down to 60, 30, 15, 10 or 5 minutes; 5 and less is kept as is.
    *
    * @param count the number of time conditions, greater than 0.
    */
   public static long interval(LocalTime startTime, LocalTime endTime, int maxConcurrency,
                               long count)
   {
      long duration = Duration.between(startTime, endTime).get(ChronoUnit.SECONDS) / 60L;
      int concurrency;
      long interval = duration / count;

      for(concurrency = 1; concurrency < maxConcurrency && interval < 10L; concurrency++) {
         interval = duration / (long) Math.ceil(count / (double) concurrency);
      }

      interval = duration / (long) Math.ceil(count / (double) concurrency);

      if(interval > 60) {
         interval = 60;
      }
      else if(interval > 30) {
         interval = 30;
      }
      else if(interval > 15) {
         interval = 15;
      }
      else if(interval > 10) {
         interval = 10;
      }
      else if(interval > 5) {
         interval = 5;
      }

      return interval;
   }
}
