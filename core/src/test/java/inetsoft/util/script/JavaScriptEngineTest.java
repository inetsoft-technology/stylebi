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
package inetsoft.util.script;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mozilla.javascript.*;

import java.awt.*;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JavaScriptEngineTest {
   @BeforeAll
   static void before() {
      defaultZone = TimeZone.getDefault();
      defaultLocale = Locale.getDefault();
      TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("US/Eastern")));
      Locale.setDefault(Locale.US);
   }

   @AfterAll
   static void after() {
      TimeZone.setDefault(defaultZone);
      Locale.setDefault(defaultLocale);
   }

   @Test
   void test47883() {
      // Bug #47883
      // Script taken from expression column, turns the result into a string representing
      // the difference in time between the two dates. e.g. 4 d 5 h 54 m
      java.util.Date d1 = new Timestamp(toDate("2021-03-05T22:05:12").getTime());
      java.util.Date d2 = new Timestamp(toDate("2021-03-10T03:59:17").getTime());

      double D = JavaScriptEngine.dateDiff("d", d1, d2);
      assertEquals(4, D);
      d1 = JavaScriptEngine.dateAdd("d", (int) D, d1);

      double H = JavaScriptEngine.dateDiff("h", d1, d2);
      assertEquals(5, H);
      d1 = JavaScriptEngine.dateAdd("h", (int) H, d1);

      double M = JavaScriptEngine.dateDiff("n", d1, d2);  // n = mins
      assertEquals(54, M);
      d1 = JavaScriptEngine.dateAdd("n", (int) M, d1);

      double S = JavaScriptEngine.dateDiff("s", d1, d2);  // s = seconds
      assertEquals(5, S);
   }

   @Test
   void DSTBoundaryIs23HourDiff() {
      java.util.Date d1 = toDate("2021-03-13T12:00");
      java.util.Date d2 = toDate("2021-03-14T12:00");
      assertEquals(23, JavaScriptEngine.dateDiff("h", d1, d2));
   }

   @Test
   void DSTBoundaryIs25HourDiff() {
      java.util.Date d1 = toDate("2021-11-06T12:00");
      java.util.Date d2 = toDate("2021-11-07T12:00");
      assertEquals(25, JavaScriptEngine.dateDiff("h", d1, d2));
   }

   @Test
   void noDSTBoundaryIs24HourDiff() {
      java.util.Date d1 = toDate("2021-04-14T12:00");
      java.util.Date d2 = toDate("2021-04-15T12:00");
      assertEquals(24, JavaScriptEngine.dateDiff("h", d1, d2));
   }

   @Test
   void testIsNull() {
      // Test with null value
      assertTrue(JavaScriptEngine.isNull(null));

      // Test with Undefined.instance
      assertTrue(JavaScriptEngine.isNull(org.mozilla.javascript.Undefined.instance));

      // Test with non-null values
      assertFalse(JavaScriptEngine.isNull("string"));
      assertFalse(JavaScriptEngine.isNull(123));
      assertFalse(JavaScriptEngine.isNull(new Object()));
   }

   @Test
   void testIsDate() {
      // Test with valid date objects
      assertTrue(JavaScriptEngine.isDate(new java.util.Date()));
      assertTrue(JavaScriptEngine.isDate(new Timestamp(System.currentTimeMillis())));

      // Test with invalid date objects
      assertFalse(JavaScriptEngine.isDate("2023-01-01"));
      assertFalse(JavaScriptEngine.isDate(12345));
      assertFalse(JavaScriptEngine.isDate(null));
      assertFalse(JavaScriptEngine.isDate(new Object()));
   }

   @Test
   void testIsNumber() {
      // Test with valid numbers
      assertTrue(JavaScriptEngine.isNumber(123));
      assertTrue(JavaScriptEngine.isNumber(-123.45));

      // Test with non-number values
      assertFalse(JavaScriptEngine.isNumber("string"));
      assertFalse(JavaScriptEngine.isNumber(null));
      assertFalse(JavaScriptEngine.isNumber(new Object()));
   }

   @Test
   void testTrim() {
      assertEquals("he llo", JavaScriptEngine.trim("  he llo  "));
      assertEquals("hello", JavaScriptEngine.trim("hello"));
      assertEquals("", JavaScriptEngine.trim("   "));
   }

   @Test
   void testLtrim() {
      assertEquals("he llo  ", JavaScriptEngine.ltrim("  he llo  "));
      assertEquals("hello", JavaScriptEngine.ltrim("hello"));
      assertEquals("", JavaScriptEngine.ltrim("   "));
   }

   @Test
   void testRtrim() {
      assertEquals("  he llo", JavaScriptEngine.rtrim("  he llo  "));
      assertEquals("hello", JavaScriptEngine.rtrim("hello"));
      assertEquals("", JavaScriptEngine.rtrim("   "));//bug #71262
   }

   @Test
   void testFormatDate() {
      // Test with a valid date object
      java.util.Date d1 = toDate("2021-04-14T00:00");
      String format = "yyyy-MM-dd";
      String result = JavaScriptEngine.formatDate(d1, format);
      assertEquals("2021-04-14", result);

      // Test with null value
      result = JavaScriptEngine.formatDate(null, format);
      assertEquals("NaD", result);

      // Test with an invalid object
      result = JavaScriptEngine.formatDate("invalid", format);
      assertEquals("NaD", result);

      //Test different date formats
      java.util.Date d2 = toDate("2023-10-01T15:23:15");
      assertEquals("Oct.1.2023", JavaScriptEngine.formatDate(d2, "MMM.d.yyyy"));
      assertEquals("Sunday, October 01, 2023", JavaScriptEngine.formatDate(d2, "EEEEE, MMMMM dd, yyyy"));
      assertEquals("10/01/2023 15:23:15", JavaScriptEngine.formatDate(d2, "MM/dd/yyyy H:mm:ss"));
      assertEquals("Oct. 1, 2023 3:23:15 PM", JavaScriptEngine.formatDate(d2, "MMM. d, yyyy h:mm:ss a"));
      assertEquals("274-2023 15:23:15", JavaScriptEngine.formatDate(d2, "D-y k:m:s"));
      assertEquals("40-1, 7-07,1 2023", JavaScriptEngine.formatDate(d2, "w-W, u-uu,F yyyy"));
   }

   @Test
   void testFormatNumber() {
      // Test with valid number and format
      assertEquals("123.46",
                   JavaScriptEngine.formatNumber(123.456, "#.##", null));

      // Test with rounding mode
      assertEquals("123.5",
                   JavaScriptEngine.formatNumber(123.456, "#.#", "ROUND_HALF_UP"));
      assertEquals("123.4",
                   JavaScriptEngine.formatNumber(123.456, "#.#", "ROUND_DOWN"));

      // Test with null format string
      assertThrows(
         NullPointerException.class, () -> JavaScriptEngine.formatNumber(123.456, null, null));

      // Test with edge cases
      assertEquals(".00", JavaScriptEngine.formatNumber(0, "#.00", null));
      assertEquals("-123.46",
                   JavaScriptEngine.formatNumber(-123.456, "#.##", null));
   }

   @Test
   void testNumberToString() {
      // Test with a valid number
      assertEquals("123", JavaScriptEngine.numberToString(123));

      // Test with a null value
      assertNull(JavaScriptEngine.numberToString(null));

      // Test with a non-number object
      assertEquals("test", JavaScriptEngine.numberToString("test"));
   }

   @Test
   void testParseDate() {
      // Test with valid date string and format
      assertEquals(toDate("2023-10-01T00:00"),
                   JavaScriptEngine.parseDate("2023-10-01", "yyyy-MM-dd"));

      // Test with valid date string and null format
      assertNotNull(JavaScriptEngine.parseDate("October 1, 2023", null));

      // Test with invalid date string
      assertNull(JavaScriptEngine.parseDate("invalid-date", "yyyy-MM-dd"));

      // Test with null date string
      assertNull(JavaScriptEngine.parseDate(null, "yyyy-MM-dd"));

      // Test with null format and invalid date string
      assertNull(JavaScriptEngine.parseDate("invalid-date", null));

      // Test with valid date string and default format fallback
      assertNotNull(JavaScriptEngine.parseDate("2023-10-01", null));

      //Test different date formats
      assertEquals(toDate("2023-10-01T00:00"),
                   JavaScriptEngine.parseDate("Oct.1.2023 ", "MMM.d.yyyy"));
      assertEquals(toDate("2023-10-01T00:00"),
                   JavaScriptEngine.parseDate("Sunday, October 01, 2023", "EEEEE, MMMMM dd, yyyy"));
      assertEquals(toDate("2023-10-01T15:23:15"),
                   JavaScriptEngine.parseDate("10/01/2023 15:23:15", "MM/dd/yyyy H:mm:ss"));
      assertEquals(toDate("2023-10-01T15:23:15"),
                   JavaScriptEngine.parseDate("Oct. 1, 2023 3:23:15 PM", "MMM. d, yyyy h:mm:ss a"));
      assertEquals(toDate("2023-01-05T15:23:15"),
                   JavaScriptEngine.parseDate("5-2023 15:23:15", "D-y k:m:s"));
   }

   @ParameterizedTest
   @MethodSource("provideSplitStrTestCases")
   void testSplitStr(Object input, String[] expected) {
      String[] result = JavaScriptEngine.splitStr(input);
      assertArrayEquals(expected, result);
   }

   private static Stream<Arguments> provideSplitStrTestCases() {
      return Stream.of(
         Arguments.of("a,b,c", new String[]{ "a", "b", "c"}),
         Arguments.of("", new String[]{}),
         Arguments.of("  a , b , c  ", new String[]{"  a ", " b ", " c  "}),
         Arguments.of(12345, new String[]{"12345"})
      );
   }

   @ParameterizedTest
   @MethodSource("provideSplitTestCases")
   void testSplit(Object input, Object[] expected) {
      Object[] result = JavaScriptEngine.split(input);
      assertArrayEquals(expected, result);
   }

   private static Stream<Arguments> provideSplitTestCases() {
      return Stream.of(
         Arguments.of(null, new Object[0]), // Null input
         Arguments.of(new NativeArray(new Object[] { "a", "b", "c" }),
                      new Object[] { "a", "b", "c" }), // NativeArray input
         Arguments.of(new Object[] { 1, 2, 3 }, new Object[] { 1, 2, 3 }), // Java array input
         Arguments.of("a,b,c", new Object[] { "a", "b", "c" }), // String input
         Arguments.of(42, new Object[] { 42 }) // Single object input
      );
   }

   @ParameterizedTest
   @MethodSource("provideSplit2TestCases")
   void testSplit2(String input, String delimiter, Integer limit, String[] expected) {
      String[] result = JavaScriptEngine.split(input, delimiter, limit);
      assertArrayEquals(expected, result);
   }

   private static Stream<Arguments> provideSplit2TestCases() {
      return Stream.of(
         Arguments.of("a b c", null, null, new String[]{"a", "b", "c"}), // Default delimiter (space) and no limit
         Arguments.of("a,b,c", ",", null, new String[]{"a", "b", "c"}),  // Custom delimiter (comma) and no limit
         Arguments.of("a,b,c", ",", 2, new String[]{"a", "b"}),          // Custom delimiter (comma) with a limit
         Arguments.of("a--b--c", "--", null, new String[]{"a", "b", "c"}), // Delimiter longer than one character
         Arguments.of("abc", ",", null, new String[]{"abc"})             // Delimiter not found in the string
      );
   }

   @ParameterizedTest
   @MethodSource("provideIndexOfTestCases")
   void testIndexOf(Object arr, Object val, int expectedIndex) {
      assertEquals(expectedIndex, JavaScriptEngine.indexOf(arr, val));
   }

   private static Stream<Arguments> provideIndexOfTestCases() {
      return Stream.of(
         Arguments.of(new String[]{"a", "b", "c"}, "b", 1), // Element exists
         Arguments.of(new String[]{"a", "b", "c"}, "d", -1), // Element does not exist
         Arguments.of(new int[]{1, 2, 3}, 2, 1), // Primitive array
         Arguments.of(null, "a", -1) // Null array
      );
   }

   @Test
   void testSetAndIsSQL() {
      JavaScriptEngine engine = new JavaScriptEngine();

      // Test setting SQL to true
      engine.setSQL(true);
      assertTrue(engine.isSQL());

      // Test setting SQL to false
      engine.setSQL(false);
      assertFalse(engine.isSQL());
   }

   @Test
   void testToFunction() {
      JavaScriptEngine engine = new JavaScriptEngine();
      assertEquals("name[]", engine.toArray("name"));
      assertEquals("test()", engine.toFunction("test"));
      assertEquals("test", engine.translateArray("test"));
      assertEquals("hello_world", engine.toIdentifier("hello world"));
   }

   @Test
   void testDatePart() {
      // Test with valid date and interval
      java.util.Date date = new java.util.Date();
      double result = JavaScriptEngine.datePart("d", date, false);
      assertTrue(result >= 1 && result <= 31);

      // Test with null date
      result = JavaScriptEngine.datePart("d", null, false);
      assertEquals(0, result);

      // Test with applyWeekStart as true
      result = JavaScriptEngine.datePart("d", date, true);
      assertTrue(result >= 1 && result <= 31);

      //Test with other intervals
      java.util.Date d2 = toDate("2025-05-26T11:23:35");
      assertEquals(2025, JavaScriptEngine.datePart("yyyy", d2, false));
      assertEquals(2, JavaScriptEngine.datePart("q", d2, false));//bug #71285
      assertEquals(5, JavaScriptEngine.datePart("m", d2, false));
      assertEquals(146, JavaScriptEngine.datePart("y", d2, false));
      assertEquals(26, JavaScriptEngine.datePart("d", d2, false));
      assertEquals(2, JavaScriptEngine.datePart("w", d2, false));
      assertEquals(21, JavaScriptEngine.datePart("ww", d2, false));
      assertEquals(11, JavaScriptEngine.datePart("h", d2, false));
      assertEquals(23, JavaScriptEngine.datePart("n", d2, false));
      assertEquals(35, JavaScriptEngine.datePart("s", d2, false));
   }

   @Test
   void testDateDiff() {
      // Test with valid dates and interval "yyyy" (years)
      java.util.Date d1 = toDate("2020-01-01T00:00");
      java.util.Date d2 = toDate("2023-01-01T00:00");
      assertEquals(3, JavaScriptEngine.dateDiff("yyyy", d1, d2));

      // Test with valid dates and interval "m" (months) and "ww"(week of the year)
      d1 = toDate("2023-01-01T00:00");
      d2 = toDate("2023-04-01T00:00");
      assertEquals(3, JavaScriptEngine.dateDiff("m", d1, d2));
//      assertEquals(13, JavaScriptEngine.dateDiff("ww", d1, d2));//bug #71288

      // Test with valid dates and interval "w" (day of the week) and "d"(day of the month)
      d1 = toDate("2023-04-01T00:00");
      d2 = toDate("2023-04-10T00:00");
//      assertEquals(9, JavaScriptEngine.dateDiff("w", d1, d2));//bug #71288
      assertEquals(9, JavaScriptEngine.dateDiff("d", d1, d2));

      // Test with valid dates and interval "q" (quarters) and "y"(day of the year)
      d1 = toDate("2023-01-01T00:00");
      d2 = toDate("2023-10-01T00:00");
      assertEquals(3, JavaScriptEngine.dateDiff("q", d1, d2));
      assertEquals(273, JavaScriptEngine.dateDiff("y", d1, d2));

      // Test with interval "h" (hours), "n" (minutes), and "s" (seconds)
      d1 = toDate("2023-10-01T02:12:23");
      d2 = toDate("2023-10-01T06:01:47");
      assertEquals(3, JavaScriptEngine.dateDiff("h", d1, d2));
      assertEquals(229, JavaScriptEngine.dateDiff("n", d1, d2));
      assertEquals(13764, JavaScriptEngine.dateDiff("s", d1, d2));
   }

   @Test
   void testDateAdd() throws ParseException {
      java.util.Date date = toDate("2023-04-01T10:23:34");
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      // Test with valid date and interval "yyyy" (years)
      assertEquals(simpleDateFormat.parse("2024-04-01 10:23:34"),
                   JavaScriptEngine.dateAdd("yyyy", 1, date));

      // Test with valid date and interval "q" (quarters)
      assertEquals(simpleDateFormat.parse("2023-07-01 10:23:34"),
                   JavaScriptEngine.dateAdd("q", 1, date));

      // Test with valid date and interval "m" (months)
      assertEquals(simpleDateFormat.parse("2023-06-01 10:23:34"),
                   JavaScriptEngine.dateAdd("m", 2, date));

      // Test with valid date and interval "d" (days) and "y" (Day of the year)
      assertEquals(simpleDateFormat.parse("2023-04-06 10:23:34"),
                   JavaScriptEngine.dateAdd("d", 5, date));
      assertEquals(simpleDateFormat.parse("2023-04-04 10:23:34"),
                   JavaScriptEngine.dateAdd("y", 3, date));

      // Test with valid date and interval "h" (hours), "n" (minutes), and "s" (seconds)
      assertEquals(simpleDateFormat.parse("2023-04-01 12:23:34"),
                   JavaScriptEngine.dateAdd("h", 2, date));
      assertEquals(simpleDateFormat.parse("2023-04-01 10:43:34"),
                   JavaScriptEngine.dateAdd("n", 20, date));
      assertEquals(simpleDateFormat.parse("2023-04-01 10:24:04"),
                   JavaScriptEngine.dateAdd("s", 30, date));

      // Test with valid date and interval "w" (Day of the week) and "ww" (Week of the year)
      assertEquals(simpleDateFormat.parse("2023-04-03 10:23:34"),
                   JavaScriptEngine.dateAdd("w", 2, date));
      assertEquals(simpleDateFormat.parse("2023-04-22 10:23:34"),
                   JavaScriptEngine.dateAdd("ww", 3, date));

      // Test with null date
      assertNull(JavaScriptEngine.dateAdd("d", 5, null));
   }

   @Test
   void testDatePartForceWeekOfMonth() {
      // Test case: forceDcToDateWeekOfMonth > 0 and interval is "wm"
      java.util.Date d1 = toDate("2025-05-26T00:00");
      assertEquals(5, JavaScriptEngine.datePartForceWeekOfMonth("wm", d1, false, 5));

      // Test case: Valid date and interval "mq" (month of quarter)
      java.util.Date d2 = toDate("2025-04-15T00:00");
      assertEquals(1, JavaScriptEngine.datePartForceWeekOfMonth("mq", d2, false, -1));

      // Test case: Valid date and interval "dq" (day of quarter)
      assertEquals(15, JavaScriptEngine.datePartForceWeekOfMonth("dq", d2, false, -1));

      // Test case: Valid date and interval "wq" (week of quarter)
      assertEquals(2, JavaScriptEngine.datePartForceWeekOfMonth("wq", d2, false, -1));

      // Test case: Null date
      assertEquals(0, JavaScriptEngine.datePartForceWeekOfMonth("d", null, false, -1));
   }

   @Test
   void testGetNames() {
      JavaScriptEngine engine = new JavaScriptEngine();
      Scriptable scope = new NativeObject();

      // Add properties to the scope
      scope.put("prop1", scope, "value1");
      scope.put("prop2", scope, "value2");

      // Test with parent = false
      Object[] names = engine.getNames(null, scope, false);
      assertArrayEquals(new Object[]{"prop1", "prop2"}, names);
      Object[] displayNames = engine.getDisplayNames(null, scope, false);
      assertArrayEquals(new Object[]{"prop1", "prop2"}, displayNames);
      Object[] ids = engine.getIds(null, scope, false);
      assertArrayEquals(new Object[]{"prop1", "prop2"}, ids);

      // Test with parent = true (including parent scope)
      Scriptable parentScope = new NativeObject();
      parentScope.put("parentProp", parentScope, "parentValue");
      scope.setParentScope(parentScope);

      names = engine.getNames(null, scope, true);
      assertArrayEquals(new Object[]{"parentProp", "prop1", "prop2"}, names);
      displayNames = engine.getDisplayNames(null, scope, true);
      assertArrayEquals(new Object[]{"parentProp", "prop1", "prop2"}, displayNames);
      ids = engine.getIds(null, scope, true);
      assertArrayEquals(new Object[]{"parentProp", "prop1", "prop2"}, ids);

      // Test with a null scope
      names = engine.getNames(null, null, false);
      assertNotNull(names);
      assertEquals(0, names.length);
      displayNames = engine.getDisplayNames(null, null, false);
      assertNotNull(displayNames);
      assertEquals(0, displayNames.length);
      ids = engine.getIds(null, null, false);
      assertNotNull(ids);
      assertEquals(0, ids.length);
   }

   @Test
   void testGetAllFunctions() throws Exception {
      JavaScriptEngine engine = new JavaScriptEngine();
      engine.init(new HashMap<>()); // Initialize the engine with an empty variable map

      // Test with fieldOnly = false
      Set functions = JavaScriptEngine.getAllFunctions(engine, false);
      assertNotNull(functions);
      assertFalse(functions.isEmpty());

      // Test with fieldOnly = true
      Set fieldOnlyFunctions = JavaScriptEngine.getAllFunctions(engine, true);
      assertNotNull(fieldOnlyFunctions);
      assertFalse(fieldOnlyFunctions.isEmpty());
   }

   @Test
   void testGetImages() {
      JavaScriptEngine engine = new JavaScriptEngine();
      // Test with a null input
      Image result = JavaScriptEngine.getImage(null);
      assertNull(result);

      //Load an image from a non-existing resource
      result = JavaScriptEngine.getImage("non-existing-resource");
      assertNull(result);

      //Load an image from a valid url
      result= JavaScriptEngine.getImage(
         "https://www.inetsoft.com/images/website/homepage/dataPipeline-1.png");
      assertNotNull(result);

      //Load an image from a valid resource path
      result = JavaScriptEngine.getImage("/inetsoft/web/resources/images/logo.png");
      assertNotNull(result);
   }

   @Test
   void testFindObject() {
      ScriptableObject scope = new NativeObject();
      ScriptableObject parentScope = new NativeObject();
      scope.setParentScope(parentScope);

      // Add properties to the scope
      scope.put("key1", scope, "value1");
      parentScope.put("key2", parentScope, "value2");

      // Test finding an existing key in the current scope
      Object result = JavaScriptEngine.findObject("key1", scope, new Vector<>());
      assertEquals("value1", result);

      // Test finding an existing key in the parent scope
      result = JavaScriptEngine.findObject("key2", scope, new Vector<>());
      assertEquals("value2", result);

      // Test finding a non-existing key
      result = JavaScriptEngine.findObject("key3", scope, new Vector<>());
      assertEquals(Scriptable.NOT_FOUND, result);

      // Test with a null scope
      result = JavaScriptEngine.findObject("key1", null, new Vector<>());
      assertEquals(Scriptable.NOT_FOUND, result);

      // Test with a circular reference in the scope chain
      parentScope.setParentScope(scope);
      result = JavaScriptEngine.findObject("key1", scope, new Vector<>());
      assertEquals("value1", result);
   }

   /**
    * @param localDateTime an ISO-8601 datetime string, e.g. 2007-12-03T10:15:30
    *
    * @return the corresponding date in the default time zone.
    */
   private java.util.Date toDate(String localDateTime) {
      return java.util.Date.from(LocalDateTime.parse(localDateTime)
         .atZone(ZoneId.systemDefault())
         .toInstant());
   }

   private static TimeZone defaultZone;
   private static Locale defaultLocale;
}