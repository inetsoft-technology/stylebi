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

package inetsoft.sree.schedule;

import inetsoft.test.*;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mozilla.javascript.Scriptable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/*
 * Tier: [mockStatic] — MockedConstruction<GregorianCalendar> and Date fix wall-clock for
 * dynamic schedule parameters. Spring (@SreeHome) provides ScriptEnv wiring only.
 *
 * Intent vs implementation suspects: none confirmed for ScheduleParameterScope at this time.
 */

/*
 * Cases deferred - low value or out of scope:
 *
 * [ScheduleParameterScope] getScriptEnv() / Cloneable
 *             -> trivial accessors; NOT duplicated here
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class ScheduleParameterScopeTest {

   @ParameterizedTest
   @CsvSource({
      "_TODAY, 2024-02-29 00:00:00",
      "_BEGINNING_OF_THIS_YEAR, 2024-01-01 00:00:00",
      "_BEGINNING_OF_THIS_QUARTER, 2024-01-01 00:00:00",
      "_BEGINNING_OF_THIS_MONTH, 2024-02-01 00:00:00",
      "_BEGINNING_OF_THIS_WEEK, 2024-02-25 00:00:00",

      "_END_OF_THIS_YEAR, 2024-12-31 23:59:59",
      "_END_OF_THIS_QUARTER, 2024-03-31 23:59:59",
      "_END_OF_THIS_MONTH, 2024-02-29 23:59:59",
      "_END_OF_THIS_WEEK, 2024-03-02 23:59:59",

      "_THIS_QUARTER, 2024-01-29 23:59:59",

      "_LAST_YEAR, 2023-02-28 23:59:59",
      "_LAST_QUARTER, 2023-10-29 23:59:59",  //Issue #71783
      "_LAST_MONTH, 2024-01-29 23:59:59",
      "_LAST_WEEK, 2024-02-22 23:59:59",
      "_LAST_DAY, 2024-02-28 23:59:59",
      "_LAST_HOUR, 2024-02-29 22:59:59",
      "_LAST_MINUTE, 2024-02-29 23:58:59",

      "_NEXT_YEAR, 2025-02-28 23:59:59",
      "_NEXT_QUARTER, 2024-04-29 23:59:59",
      "_NEXT_MONTH, 2024-03-29 23:59:59",
      "_NEXT_WEEK, 2024-03-07 23:59:59",
      "_NEXT_DAY, 2024-03-01 23:59:59",
      "_NEXT_HOUR, 2024-03-01 00:59:59",
      "_NEXT_MINUTE, 2024-03-01 00:00:59"
   })
   void testDynamicDate(String dynamicDate, String expected) {
      Calendar anchor = createCalendar(2024, Calendar.FEBRUARY, 29, 23, 59, 59);

      try(MockedConstruction<GregorianCalendar> ignored = mockCalendarAt(anchor)) {
         ScheduleParameterScope scheduleParameterScope = new ScheduleParameterScope();
         Date actualDate = (Date) scheduleParameterScope.get(dynamicDate, null);
         assertEquals(expected, sdf.format(actualDate));
      }
   }

   @Test
   void testNowReturnsFixedCurrentTime() {
      long fixedMillis = createCalendar(2024, Calendar.FEBRUARY, 29, 23, 59, 59).getTimeInMillis();

      try(MockedConstruction<Date> mockedDate = Mockito.mockConstruction(Date.class, (mock, context) ->
         when(mock.getTime()).thenReturn(fixedMillis))) {
         ScheduleParameterScope scheduleParameterScope = new ScheduleParameterScope();
         Date actualDate = (Date) scheduleParameterScope.get(DynamicDate.NOW, null);
         assertEquals("2024-02-29 23:59:59", sdf.format(actualDate));
         assertEquals(1, mockedDate.constructed().size());
      }
   }

   /**
    * test some special case
    */
   @ParameterizedTest
   @CsvSource({
      "11, 31, 23, 59,  _THIS_QUARTER, 2024-10-31 23:59:59",
      "11, 31, 23, 59, _LAST_QUARTER, 2024-07-31 23:59:59",  //Issue #71783
      "11, 31, 23, 59,  _NEXT_QUARTER, 2025-01-31 23:59:59",
      "2, 31, 23, 59, _THIS_QUARTER, 2024-01-31 23:59:59",
      "2, 31, 23, 59, _LAST_QUARTER, 2023-10-31 23:59:59",
      "2, 31, 23, 59, _NEXT_QUARTER, 2024-04-30 23:59:59",   //Issue #71783

      "0, 31, 23, 59, _THIS_QUARTER, 2024-01-31 23:59:59",   // Q1 first month, day clamp
      "0, 31, 23, 59, _LAST_QUARTER, 2023-10-31 23:59:59",
      "0, 31, 23, 59, _NEXT_QUARTER, 2024-04-30 23:59:59",

      "2, 1, 00, 59, _LAST_HOUR, 2024-02-29 23:59:59",  // check hour and minute
      "2, 1, 00, 00, _LAST_MINUTE, 2024-02-29 23:59:59"  // check hour and minute
   })
   void testQuarterDynamicDate(String month, int day, int hour, int minite, String dynamicDate, String expected) {
      Calendar fixedCalendar = createCalendar(2024, Integer.parseInt(month), day, hour, minite, 59);

      try(MockedConstruction<GregorianCalendar> ignored = mockCalendarAt(fixedCalendar)) {
         ScheduleParameterScope scheduleParameterScope = new ScheduleParameterScope();
         Date actualDate = (Date) scheduleParameterScope.get(dynamicDate, null);
         assertEquals(expected, sdf.format(actualDate));
      }
   }

   @ParameterizedTest
   @CsvSource({
      "1, 24, _BEGINNING_OF_THIS_WEEK, 2024-02-18 00:00:00",  // Saturday anchor
      "1, 24, _END_OF_THIS_WEEK, 2024-02-24 23:59:59",
      "1, 25, _BEGINNING_OF_THIS_WEEK, 2024-02-25 00:00:00",  // Sunday anchor
      "1, 25, _END_OF_THIS_WEEK, 2024-03-02 23:59:59",
      "11, 28, _BEGINNING_OF_THIS_WEEK, 2024-12-22 00:00:00", // year-end Saturday
      "11, 28, _END_OF_THIS_WEEK, 2024-12-28 23:59:59",
      "11, 29, _BEGINNING_OF_THIS_WEEK, 2024-12-29 00:00:00", // year-end Sunday
      "11, 29, _END_OF_THIS_WEEK, 2025-01-04 23:59:59"
   })
   void testWeekBoundaryDynamicDate(int month, int day, String dynamicDate, String expected) {
      Calendar anchor = createCalendar(2024, month, day, 23, 59, 59);

      try(MockedConstruction<GregorianCalendar> ignored = mockCalendarAt(anchor)) {
         ScheduleParameterScope scheduleParameterScope = new ScheduleParameterScope();
         Date actualDate = (Date) scheduleParameterScope.get(dynamicDate, null);
         assertEquals(expected, sdf.format(actualDate));
      }
   }

   @Test
   void testUnknownDynamicDateDelegatesToSuper() {
      ScheduleParameterScope scheduleParameterScope = new ScheduleParameterScope();
      Object actual = scheduleParameterScope.get("_UNKNOWN_DYNAMIC_DATE", null);
      assertSame(Scriptable.NOT_FOUND, actual);
   }

   @Test
   void testStaticMethod() {
      Calendar fixedCalendar = createCalendar(2024, 1, 29, 23, 59, 59);

      try(MockedConstruction<GregorianCalendar> ignored = mockCalendarAt(fixedCalendar)) {
         Date actualDate = ScheduleParameterScope.getBeginningOfThisYear();
         assertEquals("2024-01-01 00:00:00", sdf.format(actualDate));

         actualDate = ScheduleParameterScope.getBeginningOfThisQuarter();
         assertEquals("2024-01-01 00:00:00", sdf.format(actualDate));

         actualDate = ScheduleParameterScope.getBeginningOfThisMonth();
         assertEquals("2024-02-01 00:00:00", sdf.format(actualDate));

         actualDate = ScheduleParameterScope.getBeginningOfThisWeek();
         assertEquals("2024-02-25 00:00:00", sdf.format(actualDate));

         actualDate = ScheduleParameterScope.getEndOfThisMonth(XSchema.DATE);
         assertEquals("2024-02-29 00:00:00", sdf.format(actualDate));

         actualDate = ScheduleParameterScope.getEndOfThisQuarter(XSchema.DATE);
         assertEquals("2024-03-31 00:00:00", sdf.format(actualDate));

         actualDate = ScheduleParameterScope.getEndOfThisWeek(XSchema.DATE);
         assertEquals("2024-03-02 00:00:00", sdf.format(actualDate));

         actualDate = ScheduleParameterScope.getEndOfThisYear(XSchema.DATE);
         assertEquals("2024-12-31 00:00:00", sdf.format(actualDate));

         actualDate = ScheduleParameterScope.getToday();
         assertEquals("2024-02-29 00:00:00", sdf.format(actualDate));

         ScheduleParameterScope scheduleParameterScope = new ScheduleParameterScope();
         assertEquals(25, scheduleParameterScope.getIds().length);
      }
   }

   private MockedConstruction<GregorianCalendar> mockCalendarAt(Calendar anchor) {
      return Mockito.mockConstruction(GregorianCalendar.class, (mock, context) -> {
         Calendar fixedCalendar = (Calendar) anchor.clone();
         mockCalendarMethod(mock, fixedCalendar);
      });
   }

   private void mockCalendarMethod(GregorianCalendar mock, Calendar fixedCalendar) {
      // set the mock calendar to a fixed time
      mock.setTime(fixedCalendar.getTime());

      // mock set operation to modify the fixed calendar
      doAnswer(inv -> {
         int field = inv.getArgument(0);
         int value = inv.getArgument(1);
         fixedCalendar.set(field, value);
         return null;
      }).when(mock).set(anyInt(), anyInt());

      // mock add operation to modify the fixed calendar
      doAnswer(inv -> {
         int field = inv.getArgument(0);
         int amount = inv.getArgument(1);
         fixedCalendar.add(field, amount);
         return null;
      }).when(mock).add(anyInt(), anyInt());

      // mock  get operation and obtain from real calendar
      doAnswer(inv -> {
         int field = inv.getArgument(0);
         return fixedCalendar.get(field);
      }).when(mock).get(anyInt());

      doAnswer(inv -> {
         int field = inv.getArgument(0);
         return fixedCalendar.getActualMaximum(field);
      }).when(mock).getActualMaximum(anyInt());

      // mock getTime to return the fixed time
      when(mock.getTime()).thenAnswer(inv -> fixedCalendar.getTime());
   }

   private static Calendar createCalendar(int year, int month, int day, int hour, int minute, int second) {
      Calendar calendar = new GregorianCalendar(year, month , day, hour, minute, second);
      calendar.set(Calendar.MILLISECOND, 0);
      return calendar;
   }

   SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
}
