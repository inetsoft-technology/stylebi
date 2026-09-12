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
package inetsoft.report.filter;

import inetsoft.uql.XConstants;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.Calendar;
import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SortOrder}.
 */
class SortOrderTest {

   // -------------------------------------------------------------------------
   // compare(double, double)
   // -------------------------------------------------------------------------

   @Test
   void compareDouble_bothNull_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertEquals(0, order.compare(Tool.NULL_DOUBLE, Tool.NULL_DOUBLE));
   }

   @Test
   void compareDouble_firstNull_asc_returnsNegative() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertTrue(order.compare(Tool.NULL_DOUBLE, 5.0) < 0,
         "NULL sorts before non-null in ascending order");
   }

   @Test
   void compareDouble_firstNull_desc_returnsPositive() {
      SortOrder order = new SortOrder(SortOrder.SORT_DESC);
      assertTrue(order.compare(Tool.NULL_DOUBLE, 5.0) > 0,
         "NULL sorts after non-null in descending order (i.e. appears last when sorted desc)");
   }

   @Test
   void compareDouble_secondNull_asc_returnsPositive() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertTrue(order.compare(5.0, Tool.NULL_DOUBLE) > 0);
   }

   @Test
   void compareDouble_secondNull_desc_returnsNegative() {
      SortOrder order = new SortOrder(SortOrder.SORT_DESC);
      assertTrue(order.compare(5.0, Tool.NULL_DOUBLE) < 0);
   }

   @Test
   void compareDouble_withinEpsilon_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      // values within POSITIVE_DOUBLE_ERROR (0.0000001) of each other
      assertEquals(0, order.compare(1.0, 1.00000001));
   }

   @Test
   void compareDouble_lessThan_asc_returnsNegative() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertTrue(order.compare(1.0, 2.0) < 0);
   }

   @Test
   void compareDouble_lessThan_desc_returnsPositive() {
      SortOrder order = new SortOrder(SortOrder.SORT_DESC);
      assertTrue(order.compare(1.0, 2.0) > 0);
   }

   @Test
   void compareDouble_greaterThan_asc_returnsPositive() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertTrue(order.compare(3.0, 1.0) > 0);
   }

   @Test
   void compareDouble_greaterThan_desc_returnsNegative() {
      SortOrder order = new SortOrder(SortOrder.SORT_DESC);
      assertTrue(order.compare(3.0, 1.0) < 0);
   }

   // -------------------------------------------------------------------------
   // compare(float, float)
   // -------------------------------------------------------------------------

   @Test
   void compareFloat_bothNull_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertEquals(0, order.compare(Tool.NULL_FLOAT, Tool.NULL_FLOAT));
   }

   @Test
   void compareFloat_firstNull_asc_returnsNegative() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertTrue(order.compare(Tool.NULL_FLOAT, 5.0f) < 0);
   }

   @Test
   void compareFloat_firstNull_desc_returnsPositive() {
      SortOrder order = new SortOrder(SortOrder.SORT_DESC);
      assertTrue(order.compare(Tool.NULL_FLOAT, 5.0f) > 0);
   }

   @Test
   void compareFloat_secondNull_asc_returnsPositive() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertTrue(order.compare(5.0f, Tool.NULL_FLOAT) > 0);
   }

   @Test
   void compareFloat_withinEpsilon_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      // within POSITIVE_FLOAT_ERROR (0.000001f)
      assertEquals(0, order.compare(1.0f, 1.0000005f));
   }

   @Test
   void compareFloat_lessThan_asc_returnsNegative() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertTrue(order.compare(1.0f, 2.0f) < 0);
   }

   @Test
   void compareFloat_greaterThan_desc_returnsNegative() {
      SortOrder order = new SortOrder(SortOrder.SORT_DESC);
      assertTrue(order.compare(3.0f, 1.0f) < 0);
   }

   // -------------------------------------------------------------------------
   // compare(long, long)
   // -------------------------------------------------------------------------

   @Test
   void compareLong_equal_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertEquals(0, order.compare(42L, 42L));
   }

   @Test
   void compareLong_lessThan_asc_returnsNegative() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertTrue(order.compare(10L, 20L) < 0);
   }

   @Test
   void compareLong_lessThan_desc_returnsPositive() {
      SortOrder order = new SortOrder(SortOrder.SORT_DESC);
      assertTrue(order.compare(10L, 20L) > 0);
   }

   @Test
   void compareLong_greaterThan_asc_returnsPositive() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertTrue(order.compare(100L, 50L) > 0);
   }

   // -------------------------------------------------------------------------
   // compare(int, int)
   // -------------------------------------------------------------------------

   @Test
   void compareInt_equal_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertEquals(0, order.compare(7, 7));
   }

   @Test
   void compareInt_lessThan_asc_returnsNegative() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertTrue(order.compare(1, 2) < 0);
   }

   @Test
   void compareInt_greaterThan_desc_returnsNegative() {
      SortOrder order = new SortOrder(SortOrder.SORT_DESC);
      assertTrue(order.compare(5, 3) < 0);
   }

   // -------------------------------------------------------------------------
   // compare(short, short)
   // -------------------------------------------------------------------------

   @Test
   void compareShort_equal_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertEquals(0, order.compare((short) 4, (short) 4));
   }

   @Test
   void compareShort_lessThan_asc_returnsNegative() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertTrue(order.compare((short) 1, (short) 10) < 0);
   }

   @Test
   void compareShort_greaterThan_desc_returnsNegative() {
      SortOrder order = new SortOrder(SortOrder.SORT_DESC);
      assertTrue(order.compare((short) 10, (short) 1) < 0);
   }

   // -------------------------------------------------------------------------
   // Helper to build a Date from calendar fields
   // -------------------------------------------------------------------------

   private Date makeDate(int year, int month, int day) {
      Calendar c = Calendar.getInstance();
      c.set(year, month, day, 0, 0, 0);
      c.set(Calendar.MILLISECOND, 0);
      return c.getTime();
   }

   private Date makeDateTime(int year, int month, int day, int hour, int minute, int second) {
      Calendar c = Calendar.getInstance();
      c.set(year, month, day, hour, minute, second);
      c.set(Calendar.MILLISECOND, 0);
      return c.getTime();
   }

   // -------------------------------------------------------------------------
   // compare(Date, Date) — YEAR grouping
   // -------------------------------------------------------------------------

   @Test
   void compareDates_yearGroup_sameYear_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.YEAR_DATE_GROUP);
      Date d1 = makeDate(2023, Calendar.JANUARY, 1);
      Date d2 = makeDate(2023, Calendar.NOVEMBER, 30);
      // Call compare(Date, Date) with refreshGroupDate=false to avoid resetting groupDate state
      assertEquals(0, order.compare(d1, d2, false));
   }

   @Test
   void compareDates_yearGroup_differentYear_ordered() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.YEAR_DATE_GROUP);
      Date d1 = makeDate(2022, Calendar.JUNE, 1);
      Date d2 = makeDate(2023, Calendar.JUNE, 1);
      assertTrue(order.compare(d1, d2, false) < 0);
   }

   // -------------------------------------------------------------------------
   // compare(Date, Date) — QUARTER grouping
   // -------------------------------------------------------------------------

   @Test
   void compareDates_quarterGroup_sameQuarter_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.QUARTER_DATE_GROUP);
      Date d1 = makeDate(2023, Calendar.JANUARY, 1);   // Q1
      Date d2 = makeDate(2023, Calendar.MARCH, 31);    // Q1
      assertEquals(0, order.compare(d1, d2, false));
   }

   @Test
   void compareDates_quarterGroup_differentQuarter_ordered() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.QUARTER_DATE_GROUP);
      Date d1 = makeDate(2023, Calendar.JANUARY, 1);   // Q1
      Date d2 = makeDate(2023, Calendar.APRIL, 1);     // Q2
      assertTrue(order.compare(d1, d2, false) < 0);
   }

   // -------------------------------------------------------------------------
   // compare(Date, Date) — MONTH grouping
   // -------------------------------------------------------------------------

   @Test
   void compareDates_monthGroup_sameMonth_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.MONTH_DATE_GROUP);
      Date d1 = makeDate(2023, Calendar.MAY, 1);
      Date d2 = makeDate(2023, Calendar.MAY, 31);
      assertEquals(0, order.compare(d1, d2, false));
   }

   @Test
   void compareDates_monthGroup_differentMonth_ordered() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.MONTH_DATE_GROUP);
      Date d1 = makeDate(2023, Calendar.MAY, 1);
      Date d2 = makeDate(2023, Calendar.JUNE, 1);
      assertTrue(order.compare(d1, d2, false) < 0);
   }

   // -------------------------------------------------------------------------
   // compare(Date, Date) — DAY grouping
   // -------------------------------------------------------------------------

   @Test
   void compareDates_dayGroup_sameDay_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.DAY_DATE_GROUP);
      Date d1 = makeDateTime(2023, Calendar.MAY, 15, 8, 0, 0);
      Date d2 = makeDateTime(2023, Calendar.MAY, 15, 23, 59, 59);
      assertEquals(0, order.compare(d1, d2, false));
   }

   @Test
   void compareDates_dayGroup_adjacentDays_ordered() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.DAY_DATE_GROUP);
      Date d1 = makeDate(2023, Calendar.MAY, 14);
      Date d2 = makeDate(2023, Calendar.MAY, 15);
      assertTrue(order.compare(d1, d2, false) < 0);
   }

   // -------------------------------------------------------------------------
   // compare(Date, Date) — HOUR grouping
   // -------------------------------------------------------------------------

   @Test
   void compareDates_hourGroup_sameHour_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.HOUR_DATE_GROUP);
      Date d1 = makeDateTime(2023, Calendar.MAY, 15, 10, 0, 0);
      Date d2 = makeDateTime(2023, Calendar.MAY, 15, 10, 59, 59);
      assertEquals(0, order.compare(d1, d2, false));
   }

   @Test
   void compareDates_hourGroup_differentHour_ordered() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.HOUR_DATE_GROUP);
      Date d1 = makeDateTime(2023, Calendar.MAY, 15, 9, 30, 0);
      Date d2 = makeDateTime(2023, Calendar.MAY, 15, 10, 0, 0);
      assertTrue(order.compare(d1, d2, false) < 0);
   }

   // -------------------------------------------------------------------------
   // compare(Date, Date) — MINUTE grouping
   // -------------------------------------------------------------------------

   @Test
   void compareDates_minuteGroup_sameMinute_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.MINUTE_DATE_GROUP);
      Date d1 = makeDateTime(2023, Calendar.MAY, 15, 10, 30, 0);
      Date d2 = makeDateTime(2023, Calendar.MAY, 15, 10, 30, 59);
      assertEquals(0, order.compare(d1, d2, false));
   }

   @Test
   void compareDates_minuteGroup_differentMinute_ordered() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.MINUTE_DATE_GROUP);
      Date d1 = makeDateTime(2023, Calendar.MAY, 15, 10, 29, 0);
      Date d2 = makeDateTime(2023, Calendar.MAY, 15, 10, 30, 0);
      assertTrue(order.compare(d1, d2, false) < 0);
   }

   // -------------------------------------------------------------------------
   // compare(Date, Date) — SECOND grouping
   // -------------------------------------------------------------------------

   @Test
   void compareDates_secondGroup_sameSecond_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.SECOND_DATE_GROUP);
      Date d1 = makeDateTime(2023, Calendar.MAY, 15, 10, 30, 45);
      Date d2 = makeDateTime(2023, Calendar.MAY, 15, 10, 30, 45);
      assertEquals(0, order.compare(d1, d2, false));
   }

   @Test
   void compareDates_secondGroup_differentSecond_ordered() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.SECOND_DATE_GROUP);
      Date d1 = makeDateTime(2023, Calendar.MAY, 15, 10, 30, 44);
      Date d2 = makeDateTime(2023, Calendar.MAY, 15, 10, 30, 45);
      assertTrue(order.compare(d1, d2, false) < 0);
   }

   // -------------------------------------------------------------------------
   // compare(Date, Date) — WEEK_OF_YEAR (part) grouping
   // -------------------------------------------------------------------------

   @Test
   void compareDates_weekOfYearGroup_sameWeek_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.WEEK_OF_YEAR_DATE_GROUP);

      Calendar c1 = Calendar.getInstance();
      c1.set(2023, Calendar.MARCH, 6);  // week 10
      Calendar c2 = Calendar.getInstance();
      c2.set(2023, Calendar.MARCH, 11); // same week 10
      // same WEEK_OF_YEAR → 0
      assertEquals(0, order.compare(c1.getTime(), c2.getTime(), false));
   }

   @Test
   void compareDates_weekOfYearGroup_differentWeeks_ordered() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.WEEK_OF_YEAR_DATE_GROUP);

      Calendar c1 = Calendar.getInstance();
      c1.set(2023, Calendar.MARCH, 5);  // week 9
      Calendar c2 = Calendar.getInstance();
      c2.set(2023, Calendar.MARCH, 13); // week 11
      assertTrue(order.compare(c1.getTime(), c2.getTime(), false) < 0);
   }

   // -------------------------------------------------------------------------
   // compare(Date, Date) — DAY_OF_MONTH (part) grouping
   // -------------------------------------------------------------------------

   @Test
   void compareDates_dayOfMonthGroup_sameDay_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.DAY_OF_MONTH_DATE_GROUP);
      Date d1 = makeDate(2023, Calendar.JANUARY, 15);
      Date d2 = makeDate(2023, Calendar.MARCH, 15);
      assertEquals(0, order.compare(d1, d2, false));
   }

   @Test
   void compareDates_dayOfMonthGroup_differentDays_ordered() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.DAY_OF_MONTH_DATE_GROUP);
      Date d1 = makeDate(2023, Calendar.JANUARY, 10);
      Date d2 = makeDate(2023, Calendar.JANUARY, 20);
      assertTrue(order.compare(d1, d2, false) < 0);
   }

   // -------------------------------------------------------------------------
   // compare(Date, Date) — DAY_OF_WEEK (part) grouping
   // -------------------------------------------------------------------------

   @Test
   void compareDates_dayOfWeekGroup_sameDayOfWeek_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.DAY_OF_WEEK_DATE_GROUP);
      // Both Mondays
      Date d1 = makeDate(2023, Calendar.MARCH, 6);   // Monday
      Date d2 = makeDate(2023, Calendar.MARCH, 13);  // Monday
      assertEquals(0, order.compare(d1, d2, false));
   }

   @Test
   void compareDates_dayOfWeekGroup_differentDaysOfWeek_ordered() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.DAY_OF_WEEK_DATE_GROUP);
      Date monday = makeDate(2023, Calendar.MARCH, 6);   // Calendar.MONDAY = 2
      Date friday = makeDate(2023, Calendar.MARCH, 10);  // Calendar.FRIDAY = 6
      assertTrue(order.compare(monday, friday, false) < 0);
   }

   // -------------------------------------------------------------------------
   // compare(Date, Date) — HOUR_OF_DAY (part) grouping
   // -------------------------------------------------------------------------

   @Test
   void compareDates_hourOfDayGroup_sameHour_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.HOUR_OF_DAY_DATE_GROUP);
      Date d1 = makeDateTime(2023, Calendar.JANUARY, 1, 14, 0, 0);
      Date d2 = makeDateTime(2023, Calendar.JUNE, 15, 14, 30, 0);
      assertEquals(0, order.compare(d1, d2, false));
   }

   // -------------------------------------------------------------------------
   // compare(Date, Date) — MINUTE_OF_HOUR (part) grouping
   // -------------------------------------------------------------------------

   @Test
   void compareDates_minuteOfHourGroup_sameMinute_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.MINUTE_OF_HOUR_DATE_GROUP);
      Date d1 = makeDateTime(2023, Calendar.JANUARY, 1, 10, 30, 0);
      Date d2 = makeDateTime(2023, Calendar.JUNE, 15, 8, 30, 45);
      assertEquals(0, order.compare(d1, d2, false));
   }

   // -------------------------------------------------------------------------
   // compare(Date, Date) — SECOND_OF_MINUTE (part) grouping
   // -------------------------------------------------------------------------

   @Test
   void compareDates_secondOfMinuteGroup_sameSecond_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.SECOND_OF_MINUTE_DATE_GROUP);
      Date d1 = makeDateTime(2023, Calendar.JANUARY, 1, 10, 30, 45);
      Date d2 = makeDateTime(2023, Calendar.JUNE, 15, 8, 15, 45);
      assertEquals(0, order.compare(d1, d2, false));
   }

   // -------------------------------------------------------------------------
   // getGroupNameIndex
   // -------------------------------------------------------------------------

   @Test
   void getGroupNameIndex_nullGroupNames_returns1() {
      SortOrder order = new SortOrder(SortOrder.SORT_SPECIFIC);
      // groupNames is null → should return 1
      assertEquals(1, order.getGroupNameIndex("anything"));
   }

   @Test
   void getGroupNameIndex_valueInList_returnsIndex() {
      SortOrder order = new SortOrder(SortOrder.SORT_SPECIFIC);
      order.addGroupCondition("Alpha", new ConditionGroup());
      order.addGroupCondition("Beta", new ConditionGroup());
      order.addGroupCondition("Gamma", new ConditionGroup());

      assertEquals(0, order.getGroupNameIndex("Alpha"));
      assertEquals(1, order.getGroupNameIndex("Beta"));
      assertEquals(2, order.getGroupNameIndex("Gamma"));
   }

   @Test
   void getGroupNameIndex_valueNotInList_returnsGroupSizePlusOne() {
      SortOrder order = new SortOrder(SortOrder.SORT_SPECIFIC);
      order.addGroupCondition("Alpha", new ConditionGroup());
      order.addGroupCondition("Beta", new ConditionGroup());

      // size=2, so missing value → 3
      assertEquals(3, order.getGroupNameIndex("NotPresent"));
   }

   @Test
   void getGroupNameIndex_nullValue_matchesEmptyString() {
      SortOrder order = new SortOrder(SortOrder.SORT_SPECIFIC);
      order.addGroupCondition("", new ConditionGroup());
      order.addGroupCondition("Beta", new ConditionGroup());
      // null is treated as "" in toString call
      assertEquals(0, order.getGroupNameIndex(null));
   }

   // -------------------------------------------------------------------------
   // isAsc / isDesc / setAsc / setDesc
   // -------------------------------------------------------------------------

   @Test
   void isAsc_ascOrder_returnsTrue() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertTrue(order.isAsc());
      assertFalse(order.isDesc());
   }

   @Test
   void isDesc_descOrder_returnsTrue() {
      SortOrder order = new SortOrder(SortOrder.SORT_DESC);
      assertTrue(order.isDesc());
      assertFalse(order.isAsc());
   }

   @Test
   void setAsc_switchesDirection() {
      SortOrder order = new SortOrder(SortOrder.SORT_DESC);
      order.setAsc(true);
      assertTrue(order.isAsc());
      assertFalse(order.isDesc());
   }

   @Test
   void setDesc_switchesDirection() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setDesc(true);
      assertTrue(order.isDesc());
      assertFalse(order.isAsc());
   }

   // -------------------------------------------------------------------------
   // getOrder / setOrder
   // -------------------------------------------------------------------------

   @Test
   void getOrder_returnsSetType() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      assertEquals(SortOrder.SORT_ASC, order.getOrder());
      order.setOrder(SortOrder.SORT_DESC);
      assertEquals(SortOrder.SORT_DESC, order.getOrder());
   }

   // -------------------------------------------------------------------------
   // getInterval / getOption via setInterval
   // -------------------------------------------------------------------------

   @Test
   void setInterval_storesValueAndOption() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(2.0, XConstants.MONTH_DATE_GROUP);
      assertEquals(2.0, order.getInterval(), 0.0001);
      assertEquals(XConstants.MONTH_DATE_GROUP, order.getOption());
   }

   // -------------------------------------------------------------------------
   // clone
   // -------------------------------------------------------------------------

   @Test
   void clone_producesIndependentCopy() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.addGroupCondition("A", new ConditionGroup());
      SortOrder clone = (SortOrder) order.clone();
      assertNotNull(clone);
      // modifying original doesn't affect clone
      order.addGroupCondition("B", new ConditionGroup());
      assertEquals(1, clone.getGroupNames().length,
         "Clone should retain only the original group names");
   }

   // -------------------------------------------------------------------------
   // compare(Date, Date) — null handling
   // -------------------------------------------------------------------------

   @Test
   void compareDates_bothNull_notStarted_returnsNeg1() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.DAY_DATE_GROUP);
      // When started=false and d1=null: d1 gets reassigned to d2 (also null),
      // but d1null remains true, so the !started && d1null branch fires → -1.
      assertEquals(-1, order.compare((Date) null, (Date) null, false));
   }

   @Test
   void compareDates_bothNull_alreadyStarted_returns0() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.DAY_DATE_GROUP);
      order.setStarted(true);
      // With started=true, the d1==null && d2==null branch at line 682 fires → 0
      assertEquals(0, order.compare((Date) null, (Date) null, false));
   }

   @Test
   void compareDates_firstNullNotStarted_returnsNeg1() {
      SortOrder order = new SortOrder(SortOrder.SORT_ASC);
      order.setInterval(1, XConstants.DAY_DATE_GROUP);
      // started=false and d1=null → d1 becomes d2, compare returns -1
      Date d2 = makeDate(2023, Calendar.MAY, 1);
      assertEquals(-1, order.compare((Date) null, d2, false));
   }
}
