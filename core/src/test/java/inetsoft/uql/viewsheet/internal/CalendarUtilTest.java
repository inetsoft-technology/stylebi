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
package inetsoft.uql.viewsheet.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CalendarUtil}.
 *
 * <p>CalendarUtil is a final utility class with only static methods.
 * Tests cover the publicly accessible methods:
 * <ul>
 *   <li>isSepecificalFormats</li>
 *   <li>invalidCalendarFormat</li>
 *   <li>getYearMonthFormat</li>
 *   <li>getMonthDayFormat</li>
 *   <li>getMonthFormat</li>
 *   <li>getDayFormat</li>
 *   <li>getYearFormat</li>
 *   <li>trimCharacter</li>
 *   <li>fixSplitCharacter</li>
 *   <li>getCalendarFormat</li>
 *   <li>parseStringToDate</li>
 *   <li>formatSelectedDates</li>
 * </ul>
 * </p>
 */
class CalendarUtilTest {

   // ==========================================================================
   // isSepecificalFormats
   // ==========================================================================

   @Test
   void isSepecificalFormats_returnsTrueForFull() {
      assertTrue(CalendarUtil.isSepecificalFormats("FULL"));
   }

   @Test
   void isSepecificalFormats_returnsTrueForLong() {
      assertTrue(CalendarUtil.isSepecificalFormats("LONG"));
   }

   @Test
   void isSepecificalFormats_returnsTrueForMedium() {
      assertTrue(CalendarUtil.isSepecificalFormats("MEDIUM"));
   }

   @Test
   void isSepecificalFormats_returnsTrueForShort() {
      assertTrue(CalendarUtil.isSepecificalFormats("SHORT"));
   }

   @Test
   void isSepecificalFormats_returnsFalseForCustomPattern() {
      assertFalse(CalendarUtil.isSepecificalFormats("yyyy-MM-dd"));
   }

   @Test
   void isSepecificalFormats_returnsFalseForNull() {
      assertFalse(CalendarUtil.isSepecificalFormats(null));
   }

   @Test
   void isSepecificalFormats_returnsFalseForEmptyString() {
      assertFalse(CalendarUtil.isSepecificalFormats(""));
   }

   // ==========================================================================
   // invalidCalendarFormat
   // ==========================================================================

   @Test
   void invalidCalendarFormat_returnsTrueForNull() {
      assertTrue(CalendarUtil.invalidCalendarFormat(null));
   }

   @Test
   void invalidCalendarFormat_returnsTrueForEmptyString() {
      assertTrue(CalendarUtil.invalidCalendarFormat(""));
   }

   @Test
   void invalidCalendarFormat_returnsTrueForBlankWithNoYMD() {
      assertTrue(CalendarUtil.invalidCalendarFormat("HH:mm:ss"));
   }

   @Test
   void invalidCalendarFormat_returnsFalseForPatternWithYear() {
      assertFalse(CalendarUtil.invalidCalendarFormat("yyyy-MM-dd"));
   }

   @Test
   void invalidCalendarFormat_returnsFalseForPatternWithMonthOnly() {
      assertFalse(CalendarUtil.invalidCalendarFormat("MM"));
   }

   @Test
   void invalidCalendarFormat_returnsFalseForPatternWithDayOnly() {
      assertFalse(CalendarUtil.invalidCalendarFormat("dd"));
   }

   // ==========================================================================
   // getYearMonthFormat
   // ==========================================================================

   @Test
   void getYearMonthFormat_knownPatternMM_slash_dd_slash_yyyy() {
      // formatList[0] = {"MM/dd/yyyy", "MM/yyyy", ...}
      String result = CalendarUtil.getYearMonthFormat("MM/dd/yyyy");
      assertEquals("MM/yyyy", result);
   }

   @Test
   void getYearMonthFormat_knownPatternYyyyDashMMDashDd() {
      // formatList[1] = {"yyyy-MM-dd", "yyyy-MM", ...}
      String result = CalendarUtil.getYearMonthFormat("yyyy-MM-dd");
      assertEquals("yyyy-MM", result);
   }

   @Test
   void getYearMonthFormat_patternWithNoYear_returnsDefault() {
      // No year char => returns DEFAULT_YEAR_MONTH_FORMAT = "yyyy-MM"
      String result = CalendarUtil.getYearMonthFormat("MM/dd");
      assertEquals("yyyy-MM", result, "Pattern without year should return default");
   }

   @Test
   void getYearMonthFormat_patternWithNoMonth_returnsDefault() {
      String result = CalendarUtil.getYearMonthFormat("yyyy");
      assertEquals("yyyy-MM", result, "Pattern without month should return default");
   }

   @Test
   void getYearMonthFormat_nullPattern_returnsDefault() {
      String result = CalendarUtil.getYearMonthFormat(null);
      assertEquals("yyyy-MM", result);
   }

   // ==========================================================================
   // getMonthDayFormat
   // ==========================================================================

   @Test
   void getMonthDayFormat_knownPatternMM_slash_dd_slash_yyyy() {
      String result = CalendarUtil.getMonthDayFormat("MM/dd/yyyy");
      assertEquals("MM/dd", result);
   }

   @Test
   void getMonthDayFormat_patternWithNoDay_returnsDefault() {
      // No 'd' in pattern => DEFAULT_MONTH_DAY_FORMAT = "MM-dd"
      String result = CalendarUtil.getMonthDayFormat("MM/yyyy");
      assertEquals("MM-dd", result);
   }

   @Test
   void getMonthDayFormat_patternWithNoMonth_returnsDefault() {
      String result = CalendarUtil.getMonthDayFormat("dd/yyyy");
      assertEquals("MM-dd", result);
   }

   // ==========================================================================
   // getMonthFormat
   // ==========================================================================

   @Test
   void getMonthFormat_extractsMMFromFullPattern() {
      String result = CalendarUtil.getMonthFormat("MM/dd/yyyy");
      assertEquals("MM", result);
   }

   @Test
   void getMonthFormat_extractsMMMM() {
      String result = CalendarUtil.getMonthFormat("MMMM d, yyyy");
      assertEquals("MMMM", result);
   }

   @Test
   void getMonthFormat_nullPattern_returnsDefault() {
      String result = CalendarUtil.getMonthFormat(null);
      assertEquals("MM", result);
   }

   @Test
   void getMonthFormat_patternWithNoMonth_returnsDefault() {
      String result = CalendarUtil.getMonthFormat("dd/yyyy");
      assertEquals("MM", result);
   }

   // ==========================================================================
   // getDayFormat
   // ==========================================================================

   @Test
   void getDayFormat_extractsDdFromFullPattern() {
      String result = CalendarUtil.getDayFormat("MM/dd/yyyy");
      assertEquals("dd", result);
   }

   @Test
   void getDayFormat_extractsSingleD() {
      String result = CalendarUtil.getDayFormat("MMMM d, yyyy");
      assertEquals("d", result);
   }

   @Test
   void getDayFormat_nullPattern_returnsDefault() {
      String result = CalendarUtil.getDayFormat(null);
      assertEquals("dd", result);
   }

   @Test
   void getDayFormat_patternWithNoDay_returnsDefault() {
      String result = CalendarUtil.getDayFormat("MM/yyyy");
      assertEquals("dd", result);
   }

   // ==========================================================================
   // getYearFormat
   // ==========================================================================

   @Test
   void getYearFormat_extractsYyyyFromFullPattern() {
      String result = CalendarUtil.getYearFormat("MM/dd/yyyy");
      assertEquals("yyyy", result);
   }

   @Test
   void getYearFormat_extractsYy() {
      String result = CalendarUtil.getYearFormat("MM/d/yy");
      assertEquals("yy", result);
   }

   @Test
   void getYearFormat_nullPattern_returnsDefault() {
      String result = CalendarUtil.getYearFormat(null);
      assertEquals("yyyy", result);
   }

   @Test
   void getYearFormat_patternWithNoYear_returnsDefault() {
      String result = CalendarUtil.getYearFormat("MM/dd");
      assertEquals("yyyy", result);
   }

   // ==========================================================================
   // getCalendarFormat (uses formatList lookup)
   // ==========================================================================

   @Test
   void getCalendarFormat_yearMonthIndex_forKnownPattern() {
      // formatList[0] = {"MM/dd/yyyy", "MM/yyyy", ...}
      String result = CalendarUtil.getCalendarFormat("MM/dd/yyyy", CalendarUtil.YEAR_MONTH_FORMAT_INDEX);
      assertEquals("MM/yyyy", result);
   }

   @Test
   void getCalendarFormat_yearFormatIndex_forKnownPattern() {
      // formatList[1] = {"yyyy-MM-dd", "yyyy-MM", "MM-dd", "MM", "dd", "yyyy"}
      String result = CalendarUtil.getCalendarFormat("yyyy-MM-dd", CalendarUtil.YEAR_FORMAT_INDEX);
      assertEquals("yyyy", result);
   }

   @Test
   void getCalendarFormat_monthFormatIndex_forKnownPattern() {
      String result = CalendarUtil.getCalendarFormat("yyyy-MM-dd", CalendarUtil.MONTH_FORMAT_INDEX);
      assertEquals("MM", result);
   }

   @Test
   void getCalendarFormat_dayFormatIndex_forKnownPattern() {
      String result = CalendarUtil.getCalendarFormat("yyyy-MM-dd", CalendarUtil.DAY_FORMAT_INDEX);
      assertEquals("dd", result);
   }

   // ==========================================================================
   // trimCharacter
   // ==========================================================================

   @Test
   void trimCharacter_removesLeadingAndTrailingNonValid() {
      char[] valid = {'M', 'y', 'd'};
      // "---yyyy-MM---" => "yyyy-MM"
      String result = CalendarUtil.trimCharacter("---yyyy-MM---", valid);
      assertEquals("yyyy-MM", result);
   }

   @Test
   void trimCharacter_nullOrEmpty_returnsAsIs() {
      char[] valid = {'M', 'y'};
      assertNull(CalendarUtil.trimCharacter(null, valid));
      assertEquals("", CalendarUtil.trimCharacter("", valid));
   }

   @Test
   void trimCharacter_allValidChars_returnsUnchanged() {
      char[] valid = {'M', 'y'};
      assertEquals("yyyy-MM", CalendarUtil.trimCharacter("yyyy-MM", valid));
   }

   @Test
   void trimCharacter_noValidCharsInString_retainsFirstChar() {
      char[] valid = {'M', 'y'};
      // "HH:mm" has no y or M; the leading-trim loop finds no valid char so
      // start stays 0 and nothing is trimmed from the front. The trailing loop
      // removes all non-valid chars from the back stopping when j==0, leaving "H".
      String result = CalendarUtil.trimCharacter("HH:mm", valid);
      assertEquals("H", result);
   }

   // ==========================================================================
   // fixSplitCharacter
   // ==========================================================================

   @Test
   void fixSplitCharacter_removesDoubleNonValidSeparators() {
      char[] valid = {'M', 'y', 'd'};
      // "yyyy--MM": iterating backwards, the second '-' is preceded by another '-'
      // (also non-valid), so it is deleted, leaving "yyyy-MM".
      String result = CalendarUtil.fixSplitCharacter("yyyy--MM", valid);
      assertEquals("yyyy-MM", result);
   }

   @Test
   void fixSplitCharacter_nullOrEmpty_returnsAsIs() {
      char[] valid = {'M', 'y'};
      assertNull(CalendarUtil.fixSplitCharacter(null, valid));
      assertEquals("", CalendarUtil.fixSplitCharacter("", valid));
   }

   @Test
   void fixSplitCharacter_noConsecutiveNonValid_returnsUnchanged() {
      char[] valid = {'M', 'y'};
      assertEquals("yyyy-MM", CalendarUtil.fixSplitCharacter("yyyy-MM", valid));
   }

   // ==========================================================================
   // parseStringToDate
   // ==========================================================================

   @Test
   void parseStringToDate_yearOnlyString() {
      // "y2020" => year prefix: year=2020, month=0 (no month in string), day=1
      Date result = CalendarUtil.parseStringToDate("y2020");
      assertNotNull(result);
      Calendar cal = Calendar.getInstance();
      cal.setTime(result);
      assertEquals(2020, cal.get(Calendar.YEAR));
   }

   @Test
   void parseStringToDate_monthString() {
      // "m2020-5" => year=2020, month=5 (Calendar 0-based = June), day=1
      Date result = CalendarUtil.parseStringToDate("m2020-5");
      assertNotNull(result);
      Calendar cal = Calendar.getInstance();
      cal.setTime(result);
      assertEquals(2020, cal.get(Calendar.YEAR));
      assertEquals(5, cal.get(Calendar.MONTH));
   }

   @Test
   void parseStringToDate_weekString() {
      // "w2020-5-2" => year=2020, month=5 (June), WEEK_OF_MONTH=2
      Date result = CalendarUtil.parseStringToDate("w2020-5-2");
      assertNotNull(result);
      Calendar cal = Calendar.getInstance();
      cal.setTime(result);
      assertEquals(2020, cal.get(Calendar.YEAR));
      assertEquals(5, cal.get(Calendar.MONTH));
   }

   @Test
   void parseStringToDate_dayString() {
      // "2020-10-15" => year=2020, month=10 (Calendar 0-based = November), day=15
      Date result = CalendarUtil.parseStringToDate("2020-10-15");
      assertNotNull(result);
      Calendar cal = Calendar.getInstance();
      cal.setTime(result);
      assertEquals(2020, cal.get(Calendar.YEAR));
      assertEquals(10, cal.get(Calendar.MONTH));
      assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
   }

   @Test
   void parseStringToDate_invalidString_returnsNull() {
      Date result = CalendarUtil.parseStringToDate("not-a-date");
      assertNull(result, "Invalid date string should return null");
   }

   @Test
   void parseStringToDate_monthMinusOne_adjustsMonth() {
      // "m2020-5" with monthMinusOne=false → Calendar.MONTH=5 (June)
      // "m2020-5" with monthMinusOne=true  → Calendar.MONTH=4 (May)
      Date resultNormal   = CalendarUtil.parseStringToDate("m2020-5", false);
      Date resultMinusOne = CalendarUtil.parseStringToDate("m2020-5", true);
      assertNotNull(resultNormal);
      assertNotNull(resultMinusOne);
      Calendar calNormal = Calendar.getInstance();
      calNormal.setTime(resultNormal);
      Calendar calMinus = Calendar.getInstance();
      calMinus.setTime(resultMinusOne);
      assertEquals(5, calNormal.get(Calendar.MONTH));
      assertEquals(4, calMinus.get(Calendar.MONTH));
   }

   @Test
   void parseStringToDate_yearStringWithNoMonth() {
      // "y2021" → year=2021
      Date result = CalendarUtil.parseStringToDate("y2021");
      assertNotNull(result);
      Calendar cal = Calendar.getInstance();
      cal.setTime(result);
      assertEquals(2021, cal.get(Calendar.YEAR));
   }

   // ==========================================================================
   // formatSelectedDates — basic smoke tests
   // ==========================================================================

   /**
    * Single calendar, non-period, default format — should return a non-empty
    * string for a valid ISO date like "2020-10-15".
    */
   @Test
   void formatSelectedDates_singleCalendarDefaultFormat_returnsNonEmpty() {
      String result = CalendarUtil.formatSelectedDates(
         "2020-10-15", "yyyy-MM-dd", false, false, true);
      assertNotNull(result);
      assertFalse(result.isEmpty(), "Formatted date should not be empty");
   }

   /**
    * An empty dateString should be handled gracefully (format applied, may
    * return empty string).
    */
   @Test
   void formatSelectedDates_emptyDateString_doesNotThrow() {
      assertDoesNotThrow(() ->
         CalendarUtil.formatSelectedDates("", "yyyy-MM-dd", false, false, false));
   }

   /**
    * Null format should fall back to the default calendar date format
    * without throwing.
    */
   @Test
   void formatSelectedDates_nullFormat_doesNotThrow() {
      assertDoesNotThrow(() ->
         CalendarUtil.formatSelectedDates("2020-10-15", null, false, false, true));
   }

   /**
    * A FULL special format should be recognised without throwing.
    */
   @Test
   void formatSelectedDates_specialFormatFull_doesNotThrow() {
      assertDoesNotThrow(() ->
         CalendarUtil.formatSelectedDates("2020-10-15", "FULL", false, false, true));
   }

   /**
    * Double calendar with range arrow separator should split correctly.
    */
   @Test
   void formatSelectedDates_doubleCalendarRange_doesNotThrow() {
      String dateStr = "2020-10-01 \u2192 2020-10-31";
      assertDoesNotThrow(() ->
         CalendarUtil.formatSelectedDates(dateStr, "yyyy-MM-dd", true, false, false));
   }

   /**
    * Double calendar with period (" & ") separator should split correctly.
    */
   @Test
   void formatSelectedDates_doubleCalendarPeriod_doesNotThrow() {
      String dateStr = "2020-10-01 & 2020-10-31";
      assertDoesNotThrow(() ->
         CalendarUtil.formatSelectedDates(dateStr, "yyyy-MM-dd", true, true, false));
   }

   // ==========================================================================
   // YEAR_FORMAT_INDEX, MONTH_FORMAT_INDEX, DAY_FORMAT_INDEX constants
   // ==========================================================================

   @Test
   void constants_yearFormatIndexIs5() {
      assertEquals(5, CalendarUtil.YEAR_FORMAT_INDEX);
   }

   @Test
   void constants_monthFormatIndexIs3() {
      assertEquals(3, CalendarUtil.MONTH_FORMAT_INDEX);
   }

   @Test
   void constants_dayFormatIndexIs4() {
      assertEquals(4, CalendarUtil.DAY_FORMAT_INDEX);
   }

   @Test
   void constants_yearMonthFormatIndexIs1() {
      assertEquals(1, CalendarUtil.YEAR_MONTH_FORMAT_INDEX);
   }
}
