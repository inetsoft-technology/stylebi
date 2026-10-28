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

import inetsoft.sree.schedule.TimeRange;
import inetsoft.web.api.schedule.TimeCondition;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/**
 * Regression coverage for the extraction of {@code AdminScheduleGateway}'s former private
 * {@code convertCondition} methods into {@link ScheduleConditionConverter} (bug #76848, design
 * §5.1, decision D3) -- round-trips every {@link TimeCondition.Type} bridge-DTO -> real -> bridge-
 * DTO, confirming no field is lost across the extraction. Behavior-preserving: {@code
 * AdminScheduleGatewayTest}'s own existing condition-conversion assertions keep passing unchanged
 * against the refactored gateway (see that class), so this file focuses on field-level coverage
 * this converter's own bridge-DTO fields need that the gateway's own tests don't already assert.
 */
@Tag("core")
class ScheduleConditionConverterTest {
   @Test
   void roundTripsAt() {
      TimeCondition original = new TimeCondition();
      original.setType(TimeCondition.Type.AT);
      original.setDate(OffsetDateTime.of(2026, 3, 15, 9, 30, 0, 0, ZoneOffset.UTC));
      original.setTimeZone("America/New_York");

      TimeCondition roundTripped = roundTrip(original);

      assertEquals(original.getDate().toInstant(), roundTripped.getDate().toInstant());
      assertEquals("America/New_York", roundTripped.getTimeZone());
   }

   @Test
   void roundTripsEveryDay() {
      TimeCondition original = new TimeCondition();
      original.setType(TimeCondition.Type.EVERY_DAY);
      original.setHour(9);
      original.setMinute(30);
      original.setSecond(15);
      original.setInterval(2);
      original.setWeekdayOnly(true);
      original.setTimeZone("UTC");

      TimeCondition roundTripped = roundTrip(original);

      assertEquals(9, roundTripped.getHour());
      assertEquals(30, roundTripped.getMinute());
      assertEquals(15, roundTripped.getSecond());
      assertEquals(2, roundTripped.getInterval());
      assertTrue(roundTripped.isWeekdayOnly());
      assertEquals("UTC", roundTripped.getTimeZone());
   }

   @Test
   void roundTripsEveryWeek() {
      TimeCondition original = new TimeCondition();
      original.setType(TimeCondition.Type.EVERY_WEEK);
      original.setHour(8);
      original.setMinute(0);
      original.setSecond(0);
      original.setInterval(1);
      original.setDaysOfWeek(new int[]{2, 4});
      original.setTimeZone("UTC");

      TimeCondition roundTripped = roundTrip(original);

      assertEquals(1, roundTripped.getInterval());
      assertArrayEquals(new int[]{2, 4}, roundTripped.getDaysOfWeek());
   }

   @Test
   void roundTripsEveryMonth() {
      TimeCondition original = new TimeCondition();
      original.setType(TimeCondition.Type.EVERY_MONTH);
      original.setHour(6);
      original.setMinute(0);
      original.setSecond(0);
      original.setDayOfMonth(15);
      original.setMonthsOfYear(new int[]{0, 3, 6, 9});
      original.setTimeZone("UTC");

      TimeCondition roundTripped = roundTrip(original);

      assertEquals(15, roundTripped.getDayOfMonth());
      assertArrayEquals(new int[]{0, 3, 6, 9}, roundTripped.getMonthsOfYear());
   }

   @Test
   void roundTripsEveryHourIncludingItsFallthroughFields() {
      // The original switch's EVERY_HOUR case has no `break` before `default` -- it deliberately
      // falls through, so an EVERY_HOUR condition picks up BOTH the hourEnd/minuteEnd/secondEnd/
      // hourlyInterval fields AND the default case's daysOfWeek/weekdayOnly fields. A regression
      // that accidentally added a `break` would silently drop daysOfWeek for this type only.
      TimeCondition original = new TimeCondition();
      original.setType(TimeCondition.Type.EVERY_HOUR);
      original.setHour(9);
      original.setMinute(0);
      original.setSecond(0);
      original.setHourEnd(17);
      original.setMinuteEnd(30);
      original.setSecondEnd(0);
      original.setHourlyInterval(2.0f);
      original.setDaysOfWeek(new int[]{1, 3, 5});
      original.setWeekdayOnly(true);
      original.setTimeZone("UTC");

      TimeCondition roundTripped = roundTrip(original);

      assertEquals(17, roundTripped.getHourEnd());
      assertEquals(30, roundTripped.getMinuteEnd());
      assertEquals(2.0f, roundTripped.getHourlyInterval(), 0.0001f);
      assertArrayEquals(new int[]{1, 3, 5}, roundTripped.getDaysOfWeek());
      assertTrue(roundTripped.isWeekdayOnly());
   }

   @Test
   void requireKnownTimeRangeRefusesUnknownNameWithAvailableList() {
      try(MockedStatic<TimeRange> ranges = definedRanges("Overnight", "Morning")) {
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ScheduleConditionConverter.requireKnownTimeRange(
               "changes[0].spec.conditions[1]", "Nope"));
         assertEquals("changes[0].spec.conditions[1].timeRange: \"Nope\" is not a defined " +
                      "time range (available: Overnight, Morning)", ex.getMessage());
      }
   }

   @Test
   void requireKnownTimeRangeIsCaseSensitive() {
      try(MockedStatic<TimeRange> ranges = definedRanges("Morning")) {
         assertThrows(IllegalArgumentException.class,
            () -> ScheduleConditionConverter.requireKnownTimeRange("c", "morning"));
      }
   }

   @Test
   void requireKnownTimeRangeSaysNoneWhenNoRangeIsDefined() {
      try(MockedStatic<TimeRange> ranges = definedRanges()) {
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ScheduleConditionConverter.requireKnownTimeRange("c", "Morning"));
         assertTrue(ex.getMessage().endsWith("(available: none)"));
      }
   }

   @Test
   void requireKnownTimeRangeAcceptsDefinedNullAndBlank() {
      try(MockedStatic<TimeRange> ranges = definedRanges("Morning")) {
         assertDoesNotThrow(() -> {
            ScheduleConditionConverter.requireKnownTimeRange("c", "Morning");
            ScheduleConditionConverter.requireKnownTimeRange("c", null);
            ScheduleConditionConverter.requireKnownTimeRange("c", "");
            ScheduleConditionConverter.requireKnownTimeRange("c", "  ");
         });
      }
   }

   // Cycle rollback feeds the write-direction converter snapshot names whose range may have been
   // deleted since; that path must stay lenient (the range is dropped, the rollback still works).
   @Test
   void writeConverterStaysLenientForAnUndefinedRangeSoRollbackKeepsWorking() {
      try(MockedStatic<TimeRange> ranges = definedRanges()) {
         TimeCondition wire = new TimeCondition();
         wire.setType(TimeCondition.Type.EVERY_DAY);
         wire.setInterval(1);
         wire.setTimeRange("DeletedSince");

         inetsoft.sree.schedule.TimeCondition real =
            assertDoesNotThrow(() -> ScheduleConditionConverter.convertCondition(wire));

         assertNull(real.getTimeRange());
      }
   }

   // bug #79524
   @Test
   void requireTimeOfDayInRangeRefusesOutOfRangeAndNamesTheField() {
      Object[][] cases = {{25, 0, 0, "hour"}, {24, 0, 0, "hour"}, {-1, 0, 0, "hour"},
                          {0, 60, 0, "minute"}, {0, 0, 60, "second"}};

      for(Object[] c : cases) {
         TimeCondition cond = new TimeCondition();
         cond.setType(TimeCondition.Type.EVERY_DAY);
         cond.setHour((Integer) c[0]);
         cond.setMinute((Integer) c[1]);
         cond.setSecond((Integer) c[2]);
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ScheduleConditionConverter.requireTimeOfDayInRange("l", cond));
         assertTrue(ex.getMessage().startsWith("l." + c[3] + ": "), ex.getMessage());
      }
   }

   @Test
   void requireTimeOfDayInRangeAcceptsValidAtAndHourlyUnset() {
      TimeCondition cond = new TimeCondition();
      cond.setType(TimeCondition.Type.EVERY_DAY);
      cond.setHour(23);
      cond.setMinute(59);
      cond.setSecond(59);
      assertDoesNotThrow(() -> ScheduleConditionConverter.requireTimeOfDayInRange("l", cond));

      TimeCondition at = new TimeCondition();
      at.setType(TimeCondition.Type.AT);
      at.setHour(99);
      assertDoesNotThrow(() -> ScheduleConditionConverter.requireTimeOfDayInRange("l", at));

      TimeCondition hourly = new TimeCondition();
      hourly.setType(TimeCondition.Type.EVERY_HOUR);
      hourly.setHourEnd(-1);
      hourly.setMinuteEnd(-1);
      hourly.setSecondEnd(-1);
      assertDoesNotThrow(() -> ScheduleConditionConverter.requireTimeOfDayInRange("l", hourly));
      hourly.setHourEnd(24);
      assertThrows(IllegalArgumentException.class,
         () -> ScheduleConditionConverter.requireTimeOfDayInRange("l", hourly));

      TimeCondition daily = new TimeCondition();
      daily.setType(TimeCondition.Type.EVERY_DAY);
      daily.setHourEnd(99);
      assertDoesNotThrow(() -> ScheduleConditionConverter.requireTimeOfDayInRange("l", daily));
   }

   @Test
   void requireKnownTimeZoneAcceptsResolvableIds() {
      for(String id : new String[]{"America/New_York", "Etc/UTC", "UTC", "GMT", "GMT+8", "GMT0",
                                   "Etc/GMT", "Greenwich", "Zulu", "IST", "PST", "US/Pacific",
                                   "utc", "gmt", "Utc", " UTC "})
      {
         assertDoesNotThrow(() -> ScheduleConditionConverter.requireKnownTimeZone("c", id), id);
      }
   }

   @Test
   void requireKnownTimeZoneAcceptsEveryJvmId() {
      for(String id : java.util.TimeZone.getAvailableIDs()) {
         assertDoesNotThrow(() -> ScheduleConditionConverter.requireKnownTimeZone("c", id), id);
      }
   }

   @Test
   void requireKnownTimeZoneAcceptsAbsentAndBlank() {
      assertDoesNotThrow(() -> {
         ScheduleConditionConverter.requireKnownTimeZone("c", null);
         ScheduleConditionConverter.requireKnownTimeZone("c", "");
         ScheduleConditionConverter.requireKnownTimeZone("c", "  ");
      });
   }

   @Test
   void requireKnownTimeZoneRefusesUnresolvableIds() {
      for(String id : new String[]{"Mars/Olympus", "UTC+8", "+08:00", "Z"}) {
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ScheduleConditionConverter.requireKnownTimeZone(
               "changes[0].spec.conditions[1]", id), id);
         assertTrue(ex.getMessage().startsWith(
            "changes[0].spec.conditions[1].timeZone: \"" + id + "\" is not a recognized"),
                    ex.getMessage());
      }
   }

   private static MockedStatic<TimeRange> definedRanges(String... names) {
      MockedStatic<TimeRange> ranges = mockStatic(TimeRange.class);
      ranges.when(TimeRange::getTimeRanges).thenReturn(
         java.util.Arrays.stream(names)
            .map(n -> new TimeRange(n, "06:00", "12:00", false))
            .collect(java.util.stream.Collectors.toList()));
      return ranges;
   }

   private static TimeCondition roundTrip(TimeCondition original) {
      inetsoft.sree.schedule.ScheduleCondition real = ScheduleConditionConverter.convertCondition(original);
      inetsoft.web.api.schedule.ScheduleCondition back = ScheduleConditionConverter.convertCondition(real);
      assertInstanceOf(TimeCondition.class, back);
      return (TimeCondition) back;
   }
}
