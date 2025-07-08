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

import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class ScheduleParameterScopeTest {

   @ParameterizedTest
   @CsvSource({
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
      try (MockedConstruction<GregorianCalendar> mockedCalendar = Mockito.mockConstruction(
         GregorianCalendar.class, (mock, context) -> {
            Calendar fixedCalendar = createCalendar(2024, Calendar.FEBRUARY, 29, 23, 59, 59);
            mockCalendarMethod(mock, fixedCalendar);
         })) {
         ScheduleParameterScope scheduleParameterScope = new ScheduleParameterScope();

         Date actualDate = (Date) scheduleParameterScope.get(dynamicDate, null);
         assertEquals(expected, sdf.format(actualDate));

      } catch (Exception e) {
         e.printStackTrace();
         throw new RuntimeException("Test Schedule  DynamicDate failed due to exception: " + e.getMessage());
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

      "2, 1, 00, 59, _LAST_HOUR, 2024-02-29 23:59:59",  // check hour and minute
      "2, 1, 00, 00, _LAST_MINUTE, 2024-02-29 23:59:59"  // check hour and minute
   })
   void testQuarterDynamicDate(String month, int day, int hour, int minite, String dynamicDate, String expected) {
      try (MockedConstruction<GregorianCalendar> mockedCalendar = Mockito.mockConstruction(
         GregorianCalendar.class, (mock, context) -> {
            Calendar fixedCalendar = createCalendar(2024, Integer.parseInt(month), day, hour, minite, 59);
            mockCalendarMethod(mock, fixedCalendar);
         })) {
         ScheduleParameterScope scheduleParameterScope = new ScheduleParameterScope();

         Date actualDate = (Date) scheduleParameterScope.get(dynamicDate, null);
         assertEquals(expected, sdf.format(actualDate));

      } catch (Exception e) {
         e.printStackTrace();
         throw new RuntimeException("Test Schedule Quarter DynamicDates failed due to exception: " + e.getMessage());
      }
   }

   @Test
   void testStaticMethod() {
      try (MockedConstruction<GregorianCalendar> mockedCalendar = Mockito.mockConstruction(
         GregorianCalendar.class, (mock, context) -> {
            Calendar fixedCalendar = createCalendar(2024, 1, 29, 23, 59, 59);
            mockCalendarMethod(mock, fixedCalendar);
         })) {
         Date actualDate = ScheduleParameterScope.getEndOfThisMonth(XSchema.DATE);
         assertEquals("2024-02-29 00:00:00", sdf.format(actualDate));

         actualDate = ScheduleParameterScope.getEndOfThisQuarter(XSchema.DATE);
         assertEquals("2024-03-31 00:00:00", sdf.format(actualDate));

         actualDate = ScheduleParameterScope.getEndOfThisWeek(XSchema.DATE);
         assertEquals("2024-03-02 00:00:00", sdf.format(actualDate));

         actualDate = ScheduleParameterScope.getEndOfThisYear(XSchema.DATE);
         assertEquals("2024-12-31 00:00:00", sdf.format(actualDate));

         ScheduleParameterScope scheduleParameterScope = new ScheduleParameterScope();
         assertEquals(25, scheduleParameterScope.getIds().length);

      } catch (Exception e) {
         e.printStackTrace();
         throw new RuntimeException("Test Schedule Quarter DynamicDates failed due to exception: " + e.getMessage());
      }
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

   private Calendar createCalendar(int year, int month, int day, int hour, int minute, int second) {
      Calendar calendar = new GregorianCalendar(year, month , day, hour, minute, second);
      calendar.set(Calendar.MILLISECOND, 0);
      return calendar;
   }

   SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
}
