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
import java.util.stream.Collectors;

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

   /**
    * Refuses a time range name that is not defined. A blank name means "no time range" and is
    * accepted. {@link #convertCondition(TimeCondition)} deliberately stays lenient (cycle rollback
    * feeds it snapshot names whose range may have been deleted since), so user input must be
    * checked here, at plan time.
    *
    * @param conditionLabel the path of the condition, e.g. {@code changes[0].spec.conditions[1]}.
    */
   static void requireKnownTimeRange(String conditionLabel, String timeRange) {
      if(timeRange == null || timeRange.isBlank()) {
         return;
      }

      List<String> names = TimeRange.getTimeRanges().stream()
         .map(TimeRange::getName)
         .collect(Collectors.toList());

      if(!names.contains(timeRange)) {
         throw new IllegalArgumentException(
            conditionLabel + ".timeRange: \"" + timeRange + "\" is not a defined time range " +
            "(available: " + (names.isEmpty() ? "none" : String.join(", ", names)) + ")");
      }
   }

   /**
    * Refuses a time range on any condition of a list. Enterprise Manager disables time ranges for
    * a scheduled cycle, so one set through the API would be a state the editor cannot produce. A
    * null or blank name means "no time range" and is accepted, so the output of reading a cycle
    * can be sent back unchanged. {@link #convertCondition(TimeCondition)} deliberately stays
    * lenient (cycle rollback feeds it stored conditions), so user input must be checked here, at
    * plan time.
    *
    * @param conditionsLabel the path of the list, e.g. {@code changes[0].spec.conditions}.
    */
   static void refuseTimeRanges(String conditionsLabel, List<TimeCondition> conditions) {
      for(int i = 0; i < conditions.size(); i++) {
         String timeRange = conditions.get(i).getTimeRange();

         if(timeRange != null && !timeRange.isBlank()) {
            throw new IllegalArgumentException(
               conditionsLabel + "[" + i + "].timeRange: \"" + timeRange + "\" -- a time range " +
               "is not supported on a scheduled cycle (Enterprise Manager disables time ranges " +
               "for cycles); remove timeRange");
         }
      }
   }

   /**
    * Refuses a run-once (AT) condition on any condition of a list. Enterprise Manager offers only
    * Daily/Weekly/Monthly/Hourly for a scheduled cycle, so an AT one set through the API would be a
    * state the editor cannot produce. {@link #convertCondition(TimeCondition)} deliberately stays
    * lenient (cycle rollback feeds it stored conditions, and schedule tasks share it), so user
    * input must be checked here, at plan time.
    *
    * @param conditionsLabel the path of the list, e.g. {@code changes[0].spec.conditions}.
    */
   static void refuseRunOnce(String conditionsLabel, List<TimeCondition> conditions) {
      for(int i = 0; i < conditions.size(); i++) {
         if(conditions.get(i).getType() == TimeCondition.Type.AT) {
            throw new IllegalArgumentException(
               conditionsLabel + "[" + i + "].type: \"AT\" -- a run-once (AT) condition is not " +
               "supported on a scheduled cycle (Enterprise Manager offers only " +
               "Daily/Weekly/Monthly/Hourly for cycles); use EVERY_DAY, EVERY_WEEK, EVERY_MONTH " +
               "or EVERY_HOUR");
         }
      }
   }

   /**
    * Refuses a time zone id the JVM cannot resolve. {@link TimeZone#getTimeZone(String)} silently
    * answers GMT for such an id, so a typo would otherwise be stored as GMT. A null or blank id
    * means "no time zone given" and is accepted, as is a case variant of "UTC"/"GMT" (the zero
    * offset it resolves to is what was meant). {@link #convertCondition(TimeCondition)}
    * deliberately stays lenient (cycle rollback feeds it stored zones), so user input must be
    * checked here, at plan time.
    *
    * @param conditionLabel the path of the condition, e.g. {@code changes[0].spec.conditions[1]}.
    */
   static void requireKnownTimeZone(String conditionLabel, String timeZone) {
      if(timeZone == null || timeZone.isBlank()) {
         return;
      }

      String trimmed = timeZone.strip();

      if("UTC".equalsIgnoreCase(trimmed) || "GMT".equalsIgnoreCase(trimmed)) {
         return;
      }

      // an id the JVM cannot resolve comes back as GMT, whose own id is the only valid spelling
      if("GMT".equals(TimeZone.getTimeZone(timeZone).getID())) {
         throw new IllegalArgumentException(
            conditionLabel + ".timeZone: \"" + timeZone + "\" is not a recognized time zone id " +
            "(use a Java/IANA id such as \"America/New_York\" or \"Etc/UTC\", or a custom id " +
            "such as \"GMT+8\"); it would otherwise be stored as GMT");
      }
   }

   /**
    * Refuses a time of day outside the ranges {@code TimeCondition.validateRanges} enforces
    * (hour 0-23, minute and second 0-59; for EVERY_HOUR also the end fields, where -1 means
    * unset). AT is exempt, its time comes from {@code date}. Without this an hour of 25 is shown
    * to the reviewer as "1" (the plan XML wraps it) and a minute of 70 is stored as given.
    * {@link #convertCondition(TimeCondition)} deliberately stays lenient (rollback and read-back
    * feed it stored values), so user input must be checked here, at plan time.
    *
    * @param conditionLabel the path of the condition, e.g. {@code changes[0].spec.conditions[1]}.
    */
   static void requireTimeOfDayInRange(String conditionLabel, TimeCondition condition) {
      if(condition.getType() == TimeCondition.Type.AT) {
         return;
      }

      requireInRange(conditionLabel, "hour", condition.getHour(), 0, 23);
      requireInRange(conditionLabel, "minute", condition.getMinute(), 0, 59);
      requireInRange(conditionLabel, "second", condition.getSecond(), 0, 59);

      if(condition.getType() == TimeCondition.Type.EVERY_HOUR) {
         requireInRange(conditionLabel, "hourEnd", condition.getHourEnd(), -1, 23);
         requireInRange(conditionLabel, "minuteEnd", condition.getMinuteEnd(), -1, 59);
         requireInRange(conditionLabel, "secondEnd", condition.getSecondEnd(), -1, 59);
      }
   }

   private static void requireInRange(String conditionLabel, String field, int value, int min,
                                      int max)
   {
      if(value < min || value > max) {
         throw new IllegalArgumentException(
            conditionLabel + "." + field + ": expected an integer between " + min + " and " +
            max + "; got " + value);
      }
   }

   /**
    * Applies {@link #requireTimeOfDayInRange(String, TimeCondition)} to every condition of a list.
    *
    * @param conditionsLabel the path of the list, e.g. {@code changes[0].spec.conditions}.
    */
   static void requireTimeOfDayInRange(String conditionsLabel, List<TimeCondition> conditions) {
      for(int i = 0; i < conditions.size(); i++) {
         requireTimeOfDayInRange(conditionsLabel + "[" + i + "]", conditions.get(i));
      }
   }

   /**
    * Applies {@link #requireKnownTimeZone} to every condition of a list.
    *
    * @param conditionsLabel the path of the list, e.g. {@code changes[0].spec.conditions}.
    */
   static void requireKnownTimeZones(String conditionsLabel, List<TimeCondition> conditions) {
      for(int i = 0; i < conditions.size(); i++) {
         requireKnownTimeZone(conditionsLabel + "[" + i + "]", conditions.get(i).getTimeZone());
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
