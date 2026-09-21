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

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.TimeRange;
import inetsoft.web.api.schedule.CompletionCondition;
import inetsoft.web.api.schedule.ScheduleCondition;
import inetsoft.web.api.schedule.TimeCondition;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

/**
 * Converts between the wiz-bridge's own {@code inetsoft.web.api.schedule} condition DTOs
 * ({@link TimeCondition}/{@link CompletionCondition}) and the real {@code
 * inetsoft.sree.schedule} condition objects, in both directions.
 *
 * <p>Extracted from {@link AdminScheduleGateway}'s own former private {@code convertCondition}
 * methods (design §5.1, decision D3) -- a pure, stateless, side-effect-free format converter,
 * shared between {@link AdminScheduleGateway} (schedule tasks) and {@link
 * AdminScheduleCycleGateway}/{@link ScheduleCycleChangePlanService} (scheduled cycles, bug
 * #76848) so both callers can never drift from each other on this exact, historically finicky
 * piece of logic. Behavior-preserving: every branch here is verbatim what {@code
 * AdminScheduleGateway} used to do privately, still covered by {@code AdminScheduleGatewayTest}'s
 * existing condition-conversion assertions against the refactored gateway.
 */
final class ScheduleConditionConverter {
   private ScheduleConditionConverter() {
   }

   static inetsoft.sree.schedule.ScheduleCondition convertCondition(ScheduleCondition condition) {
      if(condition instanceof TimeCondition) {
         return convertCondition((TimeCondition) condition);
      }
      else if(condition instanceof CompletionCondition) {
         return convertCondition((CompletionCondition) condition);
      }
      else {
         throw new IllegalArgumentException("Unsupported condition type: " + condition);
      }
   }

   static inetsoft.sree.schedule.TimeCondition convertCondition(TimeCondition condition) {
      inetsoft.sree.schedule.TimeCondition output = new inetsoft.sree.schedule.TimeCondition();
      final TimeCondition.Type type = condition.getType();
      output.setType(type.value());

      if(condition.getTimeRange() != null) {
         TimeRange range = TimeRange.getTimeRanges().stream()
            .filter(r -> r.getName().equals(condition.getTimeRange()))
            .findFirst()
            .orElse(null);
         output.setTimeRange(range);
      }

      if(condition.getTimeZone() != null) {
         output.setTimeZone(TimeZone.getTimeZone(condition.getTimeZone()));
      }

      output.setHour(condition.getHour());
      output.setMinute(condition.getMinute());
      output.setSecond(condition.getSecond());

      switch(type) {
      case AT:
         final OffsetDateTime date = condition.getDate();
         Objects.requireNonNull(date, "ISO 8601 Date is required for \"AT\" type");
         output.setDate(Date.from(date.toInstant()));
         break;
      case EVERY_DAY:
         output.setInterval(condition.getInterval());
         output.setWeekdayOnly(condition.isWeekdayOnly());
         break;
      case EVERY_WEEK:
      case DAY_OF_WEEK:
         output.setInterval(condition.getInterval());
         output.setDaysOfWeek(condition.getDaysOfWeek());
         break;
      case EVERY_MONTH:
         output.setDayOfMonth(condition.getDayOfMonth());
         output.setWeekOfMonth(condition.getWeekOfMonth());
         output.setDayOfWeek(condition.getDayOfWeek());
         output.setMonthsOfYear(condition.getMonthsOfYear());
         break;
      case WEEK_OF_MONTH:
         output.setWeekOfMonth(condition.getWeekOfMonth());
         output.setDayOfWeek(condition.getDayOfWeek());
         output.setMonthsOfYear(condition.getMonthsOfYear());
         break;
      case DAY_OF_MONTH:
         output.setDayOfMonth(condition.getDayOfMonth());
         output.setMonthsOfYear(condition.getMonthsOfYear());
         break;
      case EVERY_HOUR:
         output.setHourEnd(condition.getHourEnd());
         output.setMinuteEnd(condition.getMinuteEnd());
         output.setSecondEnd(condition.getSecondEnd());
         output.setHourlyInterval(condition.getHourlyInterval());
      default:
         output.setDayOfMonth(condition.getDayOfMonth());
         output.setDayOfWeek(condition.getDayOfWeek());
         output.setWeekOfMonth(condition.getWeekOfMonth());
         output.setInterval(condition.getInterval());
         output.setDaysOfWeek(condition.getDaysOfWeek());
         output.setMonthsOfYear(condition.getMonthsOfYear());
         output.setWeekdayOnly(condition.isWeekdayOnly());
         break;
      }

      return output;
   }

   static inetsoft.sree.schedule.CompletionCondition convertCondition(CompletionCondition condition) {
      inetsoft.sree.schedule.CompletionCondition output =
         new inetsoft.sree.schedule.CompletionCondition();
      output.setTaskName(ScheduleManager.getTaskId(condition.getOwner(), condition.getTaskName(), null));
      return output;
   }

   /**
    * Widens a concretely-typed {@code List<TimeCondition>} (bug #76848's own wire shape for a
    * cycle's {@code spec.conditions}) into {@code List<inetsoft.sree.schedule.ScheduleCondition>}
    * -- deliberately NOT a {@code stream().map(ScheduleConditionConverter::convertCondition)}
    * one-liner: with a concretely-typed {@code TimeCondition} element, overload resolution binds
    * to the {@code TimeCondition}-returning overload, not the {@code ScheduleCondition}-returning
    * one, so {@code Collectors.toList()} would infer {@code List<sree.TimeCondition>} -- not
    * assignable to {@code List<sree.ScheduleCondition>} (generics are not covariant). An explicit
    * loop sidesteps the overload pick entirely.
    */
   static List<inetsoft.sree.schedule.ScheduleCondition> convertConditions(
      List<TimeCondition> conditions)
   {
      List<inetsoft.sree.schedule.ScheduleCondition> result = new ArrayList<>();

      for(TimeCondition condition : conditions) {
         result.add(convertCondition(condition));
      }

      return result;
   }

   static ScheduleCondition convertCondition(inetsoft.sree.schedule.ScheduleCondition condition) {
      if(condition instanceof inetsoft.sree.schedule.TimeCondition) {
         return new TimeCondition((inetsoft.sree.schedule.TimeCondition) condition);
      }
      else if(condition instanceof inetsoft.sree.schedule.CompletionCondition) {
         return new CompletionCondition((inetsoft.sree.schedule.CompletionCondition) condition);
      }
      else {
         throw new IllegalArgumentException("Unsupported condition type: " + condition);
      }
   }
}
