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

import inetsoft.web.api.schedule.TimeCondition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

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

   private static TimeCondition roundTrip(TimeCondition original) {
      inetsoft.sree.schedule.ScheduleCondition real = ScheduleConditionConverter.convertCondition(original);
      inetsoft.web.api.schedule.ScheduleCondition back = ScheduleConditionConverter.convertCondition(real);
      assertInstanceOf(TimeCondition.class, back);
      return (TimeCondition) back;
   }
}
