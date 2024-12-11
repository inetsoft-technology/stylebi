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

import java.time.*;
import java.util.Comparator;
import java.util.List;

/**
 * Condition used to signal that a time range is about to start and should be re-balanced.
 */
public class TaskBalancerCondition implements ScheduleCondition {
   @Override
   public boolean check(long curr) {
      LocalTime now = getLocalTime(curr);
      boolean result = TimeRange.getTimeRanges().stream()
         .map(TimeRange::getStartTime)
         .mapToLong(t -> Math.abs(Duration.between(now, t).toMinutes()))
         .anyMatch(t -> t <= 10L);

      if(result) {
         this.lastRun = curr;
      }

      return result;
   }

   @Override
   public long getRetryTime(long curr) {
      LocalTime now = getLocalTime(curr);
      List<TimeRange> ranges = TimeRange.getTimeRanges();
      LocalTime next = ranges.stream()
         .map(TimeRange::getStartTime)
         .filter(t -> !t.isBefore(now))
         .min(Comparator.naturalOrder())
         .orElse(null);

      if(next != null) {
         next = next.minusMinutes(10L);

         if(next.isBefore(now)) {
            long nextRun;

            if(Duration.between(now, getLocalTime(lastRun)).toMinutes() < 5L) {
               nextRun = getTimestamp(now.plusMinutes(5L).atDate(LocalDate.now()));
            }
            else {
               nextRun = getTimestamp(now.atDate(LocalDate.now()));
            }

            lastRun = nextRun;
            return nextRun;
         }

         return getTimestamp(next.atDate(LocalDate.now()));
      }

      next = ranges.stream()
         .map(TimeRange::getStartTime)
         .min(Comparator.naturalOrder())
         .orElse(null);

      if(next == null) {
         next = now;
      }

      return getTimestamp(next.minusMinutes(10L).atDate(LocalDate.now().plusDays(1L)));
   }

   private LocalTime getLocalTime(long ts) {
      return LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneOffset.systemDefault())
         .toLocalTime();
   }

   private long getTimestamp(LocalDateTime date) {
      return date.toInstant(OffsetDateTime.now().getOffset()).toEpochMilli();
   }

   long lastRun = 0L;
}
