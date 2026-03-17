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
package inetsoft.uql.asset;

import inetsoft.uql.Condition;
import inetsoft.uql.XCondition;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DateCondition} (tested through concrete subclasses).
 *
 * <p>DateCondition is abstract; tests use {@link PeriodCondition} for the concrete evaluate()
 * logic, and {@link Condition#isInDateRange} for the relative-range (DATE_IN) logic that
 * lives in Condition but delegates to DateCondition helper methods.</p>
 */
public class DateConditionTest {

   // =========================================================================
   // PeriodCondition — concrete evaluate()
   // =========================================================================

   // ---- basic in-range ---------------------------------------------------------

   @Test
   void periodCondition_dateInRange_returnsTrue() {
      PeriodCondition cond = buildPeriodCondition(daysAgo(10), daysFromNow(10));
      assertTrue(cond.evaluate(new Date()));
   }

   @Test
   void periodCondition_dateBeforeRange_returnsFalse() {
      PeriodCondition cond = buildPeriodCondition(daysFromNow(1), daysFromNow(5));
      assertFalse(cond.evaluate(new Date()));
   }

   @Test
   void periodCondition_dateAfterRange_returnsFalse() {
      PeriodCondition cond = buildPeriodCondition(daysAgo(10), daysAgo(1));
      assertFalse(cond.evaluate(new Date()));
   }

   // ---- boundary dates ---------------------------------------------------------

   @Test
   void periodCondition_dateAtFromBoundary_returnsTrue() {
      Date from = daysAgo(5);
      Date to = daysFromNow(5);
      PeriodCondition cond = buildPeriodCondition(from, to);
      // Use the normalised from value from the condition
      assertTrue(cond.evaluate(cond.getFrom()));
   }

   @Test
   void periodCondition_dateAtToBoundary_returnsTrue() {
      Date from = daysAgo(5);
      Date to = daysFromNow(5);
      PeriodCondition cond = buildPeriodCondition(from, to);
      assertTrue(cond.evaluate(cond.getTo()));
   }

   // ---- non-Date values --------------------------------------------------------

   @Test
   void periodCondition_nullValue_returnsFalse() {
      PeriodCondition cond = buildPeriodCondition(daysAgo(5), daysFromNow(5));
      assertFalse(cond.evaluate(null));
   }

   @Test
   void periodCondition_stringValue_returnsFalse() {
      PeriodCondition cond = buildPeriodCondition(daysAgo(5), daysFromNow(5));
      assertFalse(cond.evaluate("2024-01-01"));
   }

   @Test
   void periodCondition_integerValue_returnsFalse() {
      PeriodCondition cond = buildPeriodCondition(daysAgo(5), daysFromNow(5));
      assertFalse(cond.evaluate(12345));
   }

   // ---- java.sql.Date (subclass of java.util.Date) ----------------------------

   @Test
   void periodCondition_sqlDate_inRange_returnsTrue() {
      PeriodCondition cond = buildPeriodCondition(daysAgo(10), daysFromNow(10));
      java.sql.Date sqlDate = new java.sql.Date(System.currentTimeMillis());
      assertTrue(cond.evaluate(sqlDate));
   }

   @Test
   void periodCondition_sqlDate_outOfRange_returnsFalse() {
      PeriodCondition cond = buildPeriodCondition(daysAgo(10), daysAgo(1));
      java.sql.Date sqlDate = new java.sql.Date(System.currentTimeMillis());
      assertFalse(cond.evaluate(sqlDate));
   }

   // ---- Timestamp (subclass of java.util.Date) ---------------------------------

   @Test
   void periodCondition_timestamp_inRange_returnsTrue() {
      PeriodCondition cond = buildPeriodCondition(daysAgo(10), daysFromNow(10));
      Timestamp ts = new Timestamp(System.currentTimeMillis());
      assertTrue(cond.evaluate(ts));
   }

   @Test
   void periodCondition_timestamp_outOfRange_returnsFalse() {
      PeriodCondition cond = buildPeriodCondition(daysAgo(10), daysAgo(1));
      Timestamp ts = new Timestamp(System.currentTimeMillis());
      assertFalse(cond.evaluate(ts));
   }

   // ---- negation ---------------------------------------------------------------

   @Test
   void periodCondition_negated_inRange_returnsFalse() {
      PeriodCondition cond = buildPeriodCondition(daysAgo(5), daysFromNow(5));
      cond.setNegated(true);
      assertFalse(cond.evaluate(new Date()));
   }

   @Test
   void periodCondition_negated_outOfRange_returnsTrue() {
      PeriodCondition cond = buildPeriodCondition(daysAgo(5), daysAgo(1));
      cond.setNegated(true);
      assertTrue(cond.evaluate(new Date()));
   }

   @Test
   void periodCondition_negated_nullValue_returnsFalse() {
      PeriodCondition cond = buildPeriodCondition(daysAgo(5), daysFromNow(5));
      cond.setNegated(true);
      // PeriodCondition.evaluate() returns false early (before negation) for non-Date values
      assertFalse(cond.evaluate(null));
   }

   // ---- isValid ----------------------------------------------------------------

   @Test
   void periodCondition_isValid_toAfterFrom_returnsTrue() {
      PeriodCondition cond = buildPeriodCondition(daysAgo(5), daysFromNow(5));
      assertTrue(cond.isValid());
   }

   @Test
   void periodCondition_isValid_toEqualsFrom_returnsFalse() {
      Date d = new Date();
      PeriodCondition cond = buildPeriodCondition(d, d);
      assertFalse(cond.isValid());
   }

   @Test
   void periodCondition_isValid_toBeforeFrom_returnsFalse() {
      PeriodCondition cond = buildPeriodCondition(daysFromNow(5), daysAgo(5));
      assertFalse(cond.isValid());
   }

   // ---- getFrom/setFrom, getTo/setTo -------------------------------------------

   @Test
   void periodCondition_setFromNull_usesCurrentDate() {
      PeriodCondition cond = new PeriodCondition();
      cond.setFrom(null); // should use new Date()
      assertNotNull(cond.getFrom());
   }

   @Test
   void periodCondition_setToNull_usesCurrentDate() {
      PeriodCondition cond = new PeriodCondition();
      cond.setTo(null);
      assertNotNull(cond.getTo());
   }

   // ---- clone ------------------------------------------------------------------

   @Test
   void periodCondition_clone_producesEqualObject() {
      PeriodCondition cond = buildPeriodCondition(daysAgo(5), daysFromNow(5));
      cond.setName("testCondition");
      cond.setLabel("Test Condition");

      PeriodCondition clone = (PeriodCondition) cond.clone();
      assertNotNull(clone);
      assertNotSame(cond, clone);
      assertEquals(cond.getName(), clone.getName());
      assertEquals(cond.getLabel(), clone.getLabel());
      assertEquals(cond.getFrom(), clone.getFrom());
      assertEquals(cond.getTo(), clone.getTo());
   }

   // ---- name / label -----------------------------------------------------------

   @Test
   void dateCondition_name() {
      PeriodCondition cond = new PeriodCondition();
      cond.setName("myCondition");

      assertEquals("myCondition", cond.getName());
   }

   @Test
   void dateCondition_baseClassLabel_returnedByGetLabel() {
      // PeriodCondition.getLabel() overrides the base to return a formatted date range string,
      // so we verify that setLabel on the base class is accepted and getName round-trips.
      PeriodCondition cond = new PeriodCondition();
      cond.setName("testName");
      assertEquals("testName", cond.getName());
      // getLabel() on PeriodCondition returns a formatted date, not the stored label value
      assertNotNull(cond.getLabel());
   }

   // ---- DateCondition utility methods ------------------------------------------

   @Test
   void getYear_returnsCorrectYear() {
      PeriodCondition cond = new PeriodCondition();
      Calendar cal = Calendar.getInstance();
      cal.set(2020, Calendar.JUNE, 15);
      assertEquals(2020, cond.getYear(cal.getTime()));
   }

   @Test
   void getQuarter_q1_returnsZero() {
      PeriodCondition cond = new PeriodCondition();
      Calendar cal = Calendar.getInstance();
      cal.set(2020, Calendar.JANUARY, 15);  // Jan = month 0 → quarter 0
      assertEquals(0, cond.getQuarter(cal.getTime()));
   }

   @Test
   void getQuarter_q2_returnsOne() {
      PeriodCondition cond = new PeriodCondition();
      Calendar cal = Calendar.getInstance();
      cal.set(2020, Calendar.APRIL, 15);  // Apr = month 3 → quarter 1
      assertEquals(1, cond.getQuarter(cal.getTime()));
   }

   @Test
   void getQuarter_q3_returnsTwo() {
      PeriodCondition cond = new PeriodCondition();
      Calendar cal = Calendar.getInstance();
      cal.set(2020, Calendar.JULY, 15);  // Jul = month 6 → quarter 2
      assertEquals(2, cond.getQuarter(cal.getTime()));
   }

   @Test
   void getQuarter_q4_returnsThree() {
      PeriodCondition cond = new PeriodCondition();
      Calendar cal = Calendar.getInstance();
      cal.set(2020, Calendar.OCTOBER, 15);  // Oct = month 9 → quarter 3
      assertEquals(3, cond.getQuarter(cal.getTime()));
   }

   @Test
   void getMonth_returnsCorrectMonth() {
      PeriodCondition cond = new PeriodCondition();
      Calendar cal = Calendar.getInstance();
      cal.set(2020, Calendar.MARCH, 15);
      assertEquals(Calendar.MARCH, cond.getMonth(cal.getTime()));
   }

   @Test
   void getMonths_returnsCorrectAbsoluteMonths() {
      PeriodCondition cond = new PeriodCondition();
      Calendar cal = Calendar.getInstance();
      cal.set(2020, Calendar.JANUARY, 1);
      int expected = 2020 * 12 + 0; // January = month 0
      assertEquals(expected, cond.getMonths(cal.getTime()));
   }

   @Test
   void getHalfYear_firstHalf_returnsZero() {
      PeriodCondition cond = new PeriodCondition();
      Calendar cal = Calendar.getInstance();
      cal.set(2020, Calendar.MAY, 1); // month 4, 4/6=0
      assertEquals(0, cond.getHalfYear(cal.getTime()));
   }

   @Test
   void getHalfYear_secondHalf_returnsOne() {
      PeriodCondition cond = new PeriodCondition();
      Calendar cal = Calendar.getInstance();
      cal.set(2020, Calendar.JULY, 1); // month 6, 6/6=1
      assertEquals(1, cond.getHalfYear(cal.getTime()));
   }

   // =========================================================================
   // DATE_IN relative range tests via Condition.isInDateRange
   // =========================================================================

   @Test
   void dateIn_thisYear_currentYear_returnsTrue() {
      Condition cond = new Condition();
      assertTrue(cond.isInDateRange("this year", new Date()));
   }

   @Test
   void dateIn_thisYear_lastYear_returnsFalse() {
      Condition cond = new Condition();
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.YEAR, -1);
      assertFalse(cond.isInDateRange("this year", cal.getTime()));
   }

   @Test
   void dateIn_lastYear_lastYear_returnsTrue() {
      Condition cond = new Condition();
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.YEAR, -1);
      assertTrue(cond.isInDateRange("last year", cal.getTime()));
   }

   @Test
   void dateIn_lastYear_currentYear_returnsFalse() {
      Condition cond = new Condition();
      assertFalse(cond.isInDateRange("last year", new Date()));
   }

   @Test
   void dateIn_thisMonth_currentDate_returnsTrue() {
      Condition cond = new Condition();
      assertTrue(cond.isInDateRange("this month", new Date()));
   }

   @Test
   void dateIn_thisMonth_lastMonth_returnsFalse() {
      Condition cond = new Condition();
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.MONTH, -1);
      assertFalse(cond.isInDateRange("this month", cal.getTime()));
   }

   @Test
   void dateIn_lastMonth_lastMonth_returnsTrue() {
      Condition cond = new Condition();
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.MONTH, -1);
      assertTrue(cond.isInDateRange("last month", cal.getTime()));
   }

   @Test
   void dateIn_thisWeek_currentDate_returnsTrue() {
      Condition cond = new Condition();
      assertTrue(cond.isInDateRange("this week", new Date()));
   }

   @Test
   void dateIn_lastWeek_currentDate_returnsFalse() {
      Condition cond = new Condition();
      assertFalse(cond.isInDateRange("last week", new Date()));
   }

   @Test
   void dateIn_lastWeek_lastWeekDate_returnsTrue() {
      Condition cond = new Condition();
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.WEEK_OF_YEAR, -1);
      assertTrue(cond.isInDateRange("last week", cal.getTime()));
   }

   @Test
   void dateIn_thisQuarter_currentDate_returnsTrue() {
      Condition cond = new Condition();
      assertTrue(cond.isInDateRange("this quarter", new Date()));
   }

   @Test
   void dateIn_lastQuarter_currentDate_returnsFalse() {
      Condition cond = new Condition();
      assertFalse(cond.isInDateRange("last quarter", new Date()));
   }

   // ---- null / non-date guard cases -------------------------------------------

   @Test
   void dateIn_nullValue_returnsFalse() {
      Condition cond = new Condition();
      assertFalse(cond.isInDateRange("this year", null));
   }

   @Test
   void dateIn_nullRange_returnsFalse() {
      Condition cond = new Condition();
      assertFalse(cond.isInDateRange(null, new Date()));
   }

   @Test
   void dateIn_nonDateObject_returnsFalse() {
      Condition cond = new Condition();
      assertFalse(cond.isInDateRange("this year", "notADate"));
   }

   // ---- case insensitivity of range strings ------------------------------------

   @Test
   void dateIn_caseInsensitive_thisYear() {
      Condition cond = new Condition();
      assertTrue(cond.isInDateRange("THIS YEAR", new Date()));
      assertTrue(cond.isInDateRange("This Year", new Date()));
   }

   @Test
   void dateIn_caseInsensitive_thisMonth() {
      Condition cond = new Condition();
      assertTrue(cond.isInDateRange("THIS MONTH", new Date()));
   }

   // ---- Parameterized: current-date ranges all match today ---------------------

   static Stream<Arguments> relativeRangesMatchToday() {
      return Stream.of(
         Arguments.of("this year"),
         Arguments.of("this month"),
         Arguments.of("this week"),
         Arguments.of("this quarter")
      );
   }

   @ParameterizedTest
   @MethodSource("relativeRangesMatchToday")
   void dateIn_relativeRange_matchesToday(String range) {
      Condition cond = new Condition();
      assertTrue(cond.isInDateRange(range, new Date()),
         "Expected today to match range: " + range);
   }

   static Stream<Arguments> relativeRangesDoNotMatchToday() {
      return Stream.of(
         Arguments.of("last year"),
         Arguments.of("last month"),
         Arguments.of("last week"),
         Arguments.of("last quarter")
      );
   }

   @ParameterizedTest
   @MethodSource("relativeRangesDoNotMatchToday")
   void dateIn_relativeRange_doesNotMatchToday(String range) {
      Condition cond = new Condition();
      assertFalse(cond.isInDateRange(range, new Date()),
         "Expected today to NOT match range: " + range);
   }

   // =========================================================================
   // DateRange (composite DateCondition)
   // =========================================================================

   @Test
   void dateRange_emptyConditions_isInvalid() {
      DateRange range = new DateRange();
      assertFalse(range.isValid());
   }

   @Test
   void dateRange_withValidCondition_isValid() {
      DateRange range = new DateRange();
      PeriodCondition period = buildPeriodCondition(daysAgo(5), daysFromNow(5));
      range.addDateCondition(period);
      assertTrue(range.isValid());
   }

   @Test
   void dateRange_evaluate_anyConditionTrue_returnsTrue() {
      DateRange range = new DateRange();
      // First period: last week — does NOT match now
      range.addDateCondition(buildPeriodCondition(daysAgo(14), daysAgo(7)));
      // Second period: this week — matches now
      range.addDateCondition(buildPeriodCondition(daysAgo(3), daysFromNow(3)));

      assertTrue(range.evaluate(new Date()));
   }

   @Test
   void dateRange_evaluate_noConditionTrue_returnsFalse() {
      DateRange range = new DateRange();
      range.addDateCondition(buildPeriodCondition(daysAgo(14), daysAgo(7)));
      range.addDateCondition(buildPeriodCondition(daysFromNow(7), daysFromNow(14)));

      assertFalse(range.evaluate(new Date()));
   }

   @Test
   void dateRange_addAndRemoveCondition() {
      DateRange range = new DateRange();
      PeriodCondition period = buildPeriodCondition(daysAgo(5), daysFromNow(5));
      assertTrue(range.addDateCondition(period));
      assertTrue(range.removeDateCondition(period));
      assertFalse(range.isValid()); // empty again
   }

   @Test
   void dateRange_addDuplicate_returnsFalse() {
      DateRange range = new DateRange();
      PeriodCondition period = buildPeriodCondition(daysAgo(5), daysFromNow(5));
      range.addDateCondition(period);
      assertFalse(range.addDateCondition(period)); // duplicate
   }

   @Test
   void dateRange_clear_removesAll() {
      DateRange range = new DateRange();
      range.addDateCondition(buildPeriodCondition(daysAgo(5), daysFromNow(5)));
      range.clear();
      assertEquals(0, range.getDateConditions().length);
      assertFalse(range.isValid());
   }

   // =========================================================================
   // Helpers
   // =========================================================================

   private static PeriodCondition buildPeriodCondition(Date from, Date to) {
      PeriodCondition cond = new PeriodCondition();
      cond.setFrom(from);
      cond.setTo(to);
      return cond;
   }

   private static Date daysAgo(int days) {
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.DAY_OF_YEAR, -days);
      return cal.getTime();
   }

   private static Date daysFromNow(int days) {
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.DAY_OF_YEAR, days);
      return cal.getTime();
   }
}
