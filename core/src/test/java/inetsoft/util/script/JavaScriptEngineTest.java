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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JavaScriptEngineTest {
   @BeforeAll
   static void before() {
      defaultZone = TimeZone.getDefault();
      TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("US/Eastern")));
   }

   @AfterAll
   static void after() {
      TimeZone.setDefault(defaultZone);
   }

   @Test
   void test47883() {
      // Bug #47883
      // Script taken from expression column, turns the result into a string representing
      // the difference in time between the two dates. e.g. 4 d 5 h 54 m
      java.util.Date d1 = new Timestamp(toDate("2021-03-05T22:05").getTime());
      java.util.Date d2 = new Timestamp(toDate("2021-03-10T03:59").getTime());

      double D = JavaScriptEngine.dateDiff("d", d1, d2);
      assertEquals(4, D);
      d1 = JavaScriptEngine.dateAdd("d", (int) D, d1);

      double H = JavaScriptEngine.dateDiff("h", d1, d2);
      assertEquals(5, H);
      d1 = JavaScriptEngine.dateAdd("h", (int) H, d1);

      double M = JavaScriptEngine.dateDiff("n", d1, d2);  // n = mins
      assertEquals(54, M);
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
      //assertEquals("", JavaScriptEngine.rtrim("   "));//bug #71262
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

   // 在测试类中添加以下方法
   @Test
   void testParseDate() {
      // Test with valid date string and format
      assertEquals(toDate("2023-10-01T00:00"),
                   JavaScriptEngine.parseDate("2023-10-01", "yyyy-MM-dd"));

      // Test with valid date string and null format
      assertNotNull(JavaScriptEngine.parseDate("October 1, 2023", null));

      // Test with valid time string and parseTime as true
      assertNotNull(JavaScriptEngine.parseDate("12:30:45", true));

      // Test with invalid date string
      assertNull(JavaScriptEngine.parseDate("invalid-date", "yyyy-MM-dd"));

      // Test with null date string
      assertNull(JavaScriptEngine.parseDate(null, "yyyy-MM-dd"));

      // Test with null format and invalid date string
      assertNull(JavaScriptEngine.parseDate("invalid-date", null));

      // Test with valid date string and default format fallback
      assertNotNull(JavaScriptEngine.parseDate("2023-10-01", null));
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
   }

   @Test
   void testDateDiff() {
      // Test with valid dates and interval "yyyy" (years)
      java.util.Date d1 = toDate("2020-01-01T00:00");
      java.util.Date d2 = toDate("2023-01-01T00:00");
      assertEquals(3, JavaScriptEngine.dateDiff("yyyy", d1, d2));

      // Test with valid dates and interval "m" (months)
      d1 = toDate("2023-01-01T00:00");
      d2 = toDate("2023-04-01T00:00");
      assertEquals(3, JavaScriptEngine.dateDiff("m", d1, d2));

      // Test with valid dates and interval "w" (weeks)
      d1 = toDate("2023-04-01T00:00");
      d2 = toDate("2023-04-10T00:00");
      assertEquals(1, JavaScriptEngine.dateDiff("w", d1, d2));

      // Test with valid dates and interval "q" (quarters)
      d1 = toDate("2023-01-01T00:00");
      d2 = toDate("2023-10-01T00:00");
      assertEquals(3, JavaScriptEngine.dateDiff("q", d1, d2));
   }

   @Test
   void testDateAdd() {
      // Test with valid date and interval "d" (days)
      java.util.Date date = toDate("2023-10-01T00:00");
      java.util.Date result = JavaScriptEngine.dateAdd("d", 5, date);
      assertEquals(toDate("2023-10-06T00:00"), result);

      // Test with valid date and interval "m" (months)
      result = JavaScriptEngine.dateAdd("m", 2, date);
      assertEquals(toDate("2023-12-01T00:00"), result);

      // Test with valid date and interval "q" (quarters)
      result = JavaScriptEngine.dateAdd("q", 1, date);
      assertEquals(toDate("2024-01-01T00:00"), result);

      // Test with null date
      result = JavaScriptEngine.dateAdd("d", 5, null);
      assertNull(result);
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
}