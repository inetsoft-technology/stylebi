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
package inetsoft.web.viewsheet.service;

import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import inetsoft.uql.VariableTable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ParameterServiceTest {
   // Bug #77329, a client must not be able to set the identity variables that VPM filters on
   @Test
   void readParametersDropsContextVariables() throws Exception {
      Map<String, String[]> parameters = new HashMap<>();
      parameters.put("_USER_", new String[] { "Hardware" });
      parameters.put("_ROLES_", new String[] { "Hardware" });
      parameters.put("_GROUPS_", new String[] { "Hardware" });
      parameters.put("__principal__", new String[] { "Hardware" });
      parameters.put("region", new String[] { "West" });

      VariableTable vars = new ParameterService(null).readParameters(parameters);

      assertFalse(vars.contains("_USER_"));
      assertFalse(vars.contains("_ROLES_"));
      assertFalse(vars.contains("_GROUPS_"));
      assertFalse(vars.contains("__principal__"));
      assertEquals("West", vars.get("region"));
   }

   private static VariableTable read(Object... nameAndValues) {
      Map<String, String[]> parameters = new LinkedHashMap<>();

      for(int i = 0; i < nameAndValues.length; i += 2) {
         parameters.put((String) nameAndValues[i], (String[]) nameAndValues[i + 1]);
      }

      return new ParameterService(null).readParameters(parameters);
   }

   private static String[] strs(String... values) {
      return values;
   }

   @Nested
   class WithoutType {
      @Test
      void nullMap_givesEmptyTable() {
         assertFalse(new ParameterService(null).readParameters(null).contains("x"));
      }

      @Test
      void singleValue_isScalarString() throws Exception {
         assertEquals("15", read("x", strs("15")).get("x"));
      }

      @Test
      void multipleValues_stayAStringArray() throws Exception {
         Object value = read("x", strs("a", "b")).get("x");
         assertArrayEquals(new Object[] { "a", "b" }, (Object[]) value);
      }

      @Test
      void fakeNullText_isNotConvertedWithoutType() throws Exception {
         assertEquals("__null__", read("x", strs("__null__")).get("x"));
      }

      @Test
      // pinned current behavior, #80963 item 4
      void emptyValueArray_isStoredAsNull() throws Exception {
         // VariableTable.put stores an array without a non-null element as null
         assertNull(read("x", strs()).get("x"));
      }

      // Bug #81111, client JSON {"p": null} deserializes to a null value array
      @Test
      void nullValueArray_isStoredAsNullAndKeepsOtherParameters() throws Exception {
         Map<String, String[]> parameters = new LinkedHashMap<>();
         parameters.put("p", null);
         parameters.put("q", strs("15"));

         VariableTable vars = new ParameterService(null).readParameters(parameters);

         assertNull(vars.get("p"));
         assertEquals("15", vars.get("q"));
      }

      @Test
      void nullValueArray_withTypeIsStoredAsNull() throws Exception {
         Map<String, String[]> parameters = new LinkedHashMap<>();
         parameters.put("p", null);
         parameters.put("p.__type__", strs("integer"));

         assertNull(new ParameterService(null).readParameters(parameters).get("p"));
      }
   }

   @Nested
   class WithType {
      @Test
      void typeParameterItself_isNotStored() {
         VariableTable vars = read("x", strs("1"), "x.__type__", strs("integer"));

         assertTrue(vars.contains("x"));
         assertFalse(vars.contains("x.__type__"));
      }

      @Test
      void typeNameIsCaseInsensitive() throws Exception {
         assertEquals(15, read("x", strs("15"), "x.__type__", strs("Integer")).get("x"));
         assertEquals(15, read("x", strs("15"), "x.__type__", strs("integer")).get("x"));
      }

      @Test
      void typeParameterWithNoValue_leavesValueAsString() throws Exception {
         assertEquals("15", read("x", strs("15"), "x.__type__", strs()).get("x"));
      }

      @Test
      void integer_singleValueIsScalar() throws Exception {
         assertEquals(15, read("x", strs("15"), "x.__type__", strs("integer")).get("x"));
      }

      @Test
      void integer_multipleValuesStayAnArray() throws Exception {
         Object value = read("x", strs("1", "2"), "x.__type__", strs("integer")).get("x");
         assertArrayEquals(new Object[] { 1, 2 }, (Object[]) value);
      }

      @Test
      void integer_decimalTextIsTruncated() throws Exception {
         assertEquals(15, read("x", strs("15.7"), "x.__type__", strs("integer")).get("x"));
      }

      @Test
      void integer_unparsableTextBecomesNull() throws Exception {
         assertNull(read("x", strs("abc"), "x.__type__", strs("integer")).get("x"));
      }

      @Test
      void integer_oneUnparsableElementBecomesNullInsideArray() throws Exception {
         Object value = read("x", strs("1", "abc"), "x.__type__", strs("integer")).get("x");
         assertArrayEquals(new Object[] { 1, null }, (Object[]) value);
      }

      @Test
      void integer_allUnparsableElementsCollapseToNull() throws Exception {
         // the array holds only nulls, so VariableTable.put stores null
         assertNull(read("x", strs("a", "b"), "x.__type__", strs("integer")).get("x"));
      }

      @Test
      void long_beyondIntRangeKeepsLongType() throws Exception {
         assertEquals(3000000000L,
                      read("x", strs("3000000000"), "x.__type__", strs("long")).get("x"));
      }

      @Test
      void long_unparsableAsLongFallsBackToDouble() throws Exception {
         assertEquals(1000.0, read("x", strs("1e3"), "x.__type__", strs("long")).get("x"));
      }

      @Test
      void double_parsesDecimalAndPercent() throws Exception {
         assertEquals(1.5, read("x", strs("1.5"), "x.__type__", strs("double")).get("x"));
         assertEquals(0.5, read("x", strs("50%"), "x.__type__", strs("double")).get("x"));
      }

      @Test
      void boolean_acceptsTextAndNumbers() throws Exception {
         assertEquals(true, read("x", strs("TRUE"), "x.__type__", strs("boolean")).get("x"));
         assertEquals(false, read("x", strs("false"), "x.__type__", strs("boolean")).get("x"));
         assertEquals(true, read("x", strs("2"), "x.__type__", strs("boolean")).get("x"));
         assertEquals(false, read("x", strs("0"), "x.__type__", strs("boolean")).get("x"));
      }

      @Test
      void boolean_unparsableTextIsFalse() throws Exception {
         assertEquals(false, read("x", strs("maybe"), "x.__type__", strs("boolean")).get("x"));
      }

      @Test
      void string_keepsEmptyString() throws Exception {
         assertEquals("", read("x", strs(""), "x.__type__", strs("string")).get("x"));
      }

      @Test
      void fakeNull_becomesNullForEveryType() throws Exception {
         assertNull(read("x", strs("__null__"), "x.__type__", strs("string")).get("x"));
         assertNull(read("x", strs("__null__"), "x.__type__", strs("integer")).get("x"));
         assertNull(read("x", strs("__null__"), "x.__type__", strs("date")).get("x"));
      }

      @Test
      void textNull_dependsOnType() throws Exception {
         // the browser sends the text "null" with its type, see show-hyperlink.service.ts
         assertEquals("null", read("x", strs("null"), "x.__type__", strs("string")).get("x"));
         assertNull(read("x", strs("null"), "x.__type__", strs("integer")).get("x"));
         assertNull(read("x", strs("null"), "x.__type__", strs("double")).get("x"));
         assertEquals("null", read("x", strs("null"), "x.__type__", strs("date")).get("x"));
      }

      @Test
      void date_isConvertedToSqlDate() throws Exception {
         Object value = read("x", strs("2024-03-15"), "x.__type__", strs("date")).get("x");

         assertInstanceOf(java.sql.Date.class, value);
         assertEquals("2024-03-15", value.toString());
      }

      @Test
      void date_unparsableTextKeepsOriginalString() throws Exception {
         assertEquals("not a date",
                      read("x", strs("not a date"), "x.__type__", strs("date")).get("x"));
      }

      @Test
      void date_multipleValuesStayAnArray() throws Exception {
         Object value = read("x", strs("2024-03-15", "2024-03-16"),
                             "x.__type__", strs("date")).get("x");

         assertInstanceOf(Object[].class, value);
         assertEquals(2, ((Object[]) value).length);
         assertEquals("2024-03-16", ((Object[]) value)[1].toString());
      }

      @Test
      void unknownType_keepsValueAsString() throws Exception {
         assertEquals("15", read("x", strs("15"), "x.__type__", strs("enum")).get("x"));
      }

      @Test
      void onlyTheFirstTypeValueIsUsed() throws Exception {
         assertEquals(15,
                      read("x", strs("15"), "x.__type__", strs("integer", "string")).get("x"));
      }
   }

   @Nested
   class SelectionParameters {
      @Test
      void calendarStart_isConvertedToDate() throws Exception {
         Object value = read("cal_dateStart", strs("2024-03-15")).get("cal_dateStart");

         assertInstanceOf(java.util.Date.class, value);
      }

      @Test
      // pinned current behavior, #80963 item 5
      void calendarStart_emptyValueStaysEmptyString() throws Exception {
         // getSelectionParam gives null for an empty value, but readParameters treats null as
         // "not converted" and keeps the original text
         assertEquals("", read("cal_dateStart", strs("")).get("cal_dateStart"));
      }

      @Test
      void calendarStart_multipleValuesBecomeDateArray() throws Exception {
         Object value = read("cal_dateStart", strs("2024-03-15", "2024-03-16"))
            .get("cal_dateStart");

         assertInstanceOf(java.util.Date[].class, value);
         assertEquals(2, ((Object[]) value).length);
      }

      @Test
      void calendarStart_typedValueFallsBackToTheTypedValue() throws Exception {
         // the converted java.sql.Date is not a String, so getSelectionParam fails and the
         // value from the type conversion is kept
         Object value = read("cal_dateStart", strs("2024-03-15"),
                             "cal_dateStart.__type__", strs("date")).get("cal_dateStart");

         assertInstanceOf(java.sql.Date.class, value);
      }

      @Test
      void calendarStart_unparsableValueFallsBackToTheText() throws Exception {
         assertEquals("abc", read("cal_dateStart", strs("abc")).get("cal_dateStart"));
      }
   }

   @Nested
   class HardeningRows {
      private java.util.TimeZone oldZone;
      private java.util.Locale oldLocale;

      @BeforeEach
      void saveDefaults() {
         oldZone = java.util.TimeZone.getDefault();
         oldLocale = java.util.Locale.getDefault();
      }

      @AfterEach
      void restoreDefaults() {
         java.util.TimeZone.setDefault(oldZone);
         java.util.Locale.setDefault(oldLocale);
      }

      private Object typed(String type, String text) throws Exception {
         return read("x", strs(text), "x.__type__", strs(type)).get("x");
      }

      @DisplayName("S01b pinned current behavior: date / timeInstant / time conversion in the unchanged JVM zone")
      @Test
      void s01b_jvmZone() throws Exception {
         assertInstanceOf(java.sql.Date.class, typed("date", "2024-03-15"));
         assertEquals("2024-03-15", typed("date", "2024-03-15").toString());
         assertInstanceOf(java.sql.Timestamp.class, typed("timeInstant", "2024-03-15 10:20:30"));
         assertEquals("2024-03-15 10:20:30.0", typed("timeInstant", "2024-03-15 10:20:30").toString());
         assertInstanceOf(java.sql.Time.class, typed("time", "10:20:30"));
         assertEquals("10:20:30", typed("time", "10:20:30").toString());
      }

      @DisplayName("S11b values inside the range of the type are converted exactly")
      @Test
      void s11b_inRange() throws Exception {
         assertEquals((byte) 127, typed("byte", "127"));
         assertEquals((byte) -128, typed("byte", "-128"));
         assertEquals((byte) 12, typed("byte", "12.7"));
         assertEquals((short) 32767, typed("short", "32767"));
         assertEquals((short) -32768, typed("short", "-32768"));
         assertEquals(2147483647, typed("integer", "2147483647"));
         assertEquals(-2147483648, typed("integer", "-2147483648"));
         assertEquals(12, typed("integer", "12.7"));
         assertEquals(9223372036854775807L, typed("long", "9223372036854775807"));
      }

      @Disabled("mismatch S03b")
      @DisplayName("S03b a null map value is neither thrown on nor required")
      @Test
      void s03b_nullMapValue() {
         Map<String, String[]> map = new LinkedHashMap<>();
         map.put("p", null);
         VariableTable[] result = new VariableTable[1];
         assertDoesNotThrow(() -> result[0] = new ParameterService(null).readParameters(map));
      }

      @DisplayName("S09 numeric conversion is independent of the default locale")
      @ParameterizedTest(name = "[{index}] {0} {1}")
      @CsvSource({ "GERMANY, double", "GERMANY, float", "GERMANY, integer", "GERMANY, long",
                   "FRANCE, double", "FRANCE, float", "FRANCE, integer", "FRANCE, long" })
      void s09_locale(String locale, String type) throws Exception {
         for(String text : new String[] { "1234", "1.5" }) {
            java.util.Locale.setDefault(java.util.Locale.US);
            Object control = typed(type, text);

            java.util.Locale.setDefault(locale.equals("GERMANY") ? java.util.Locale.GERMANY :
                                           java.util.Locale.FRANCE);
            assertEquals(control, typed(type, text), type + " " + text + " in " + locale);
         }
      }

      @DisplayName("S10 type names are matched independent of the Turkish locale")
      @ParameterizedTest(name = "[{index}] {0}")
      @CsvSource({ "Integer, 15", "INTEGER, 15", "Date, 2024-03-15" })
      void s10_turkishLocale(String type, String text) throws Exception {
         java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
         Object v = typed(type, text);

         if(type.equals("Date")) {
            assertInstanceOf(java.sql.Date.class, v);
         }
         else {
            assertEquals(15, v);
         }
      }

      @Disabled("mismatch S11")
      @DisplayName("S11 a text out of the range of the type never becomes another number")
      @ParameterizedTest(name = "[{index}] {0} {1}")
      @CsvSource({ "byte, 200", "short, 40000", "integer, 3000000000", "integer, 2147483648",
                   "integer, -2147483649" })
      void s11_outOfRange(String type, String text) throws Exception {
         Object v = typed(type, text);

         if(v != null && !text.equals(v)) {
            assertEquals(0, new java.math.BigDecimal(text)
                            .compareTo(new java.math.BigDecimal(v.toString())),
                         "stored " + v + " (" + v.getClass().getSimpleName() + ") for " + text);
         }
      }

      @DisplayName("S11 the largest int is kept")
      @Test
      void s11_maxInt() throws Exception {
         assertEquals(2147483647, typed("integer", "2147483647"));
      }

      @DisplayName("S02 single value equals Tool.getData of the type")
      @ParameterizedTest(name = "[{index}] {0}")
      @CsvSource({ "string, abc", "boolean, true", "float, 1.5", "double, 2.5", "char, c",
                   "byte, 12", "short, 123", "integer, 42", "long, 4200000000",
                   "timeInstant, 2024-03-15 10:20:30", "date, 2024-03-15", "time, 10:20:30" })
      void s02_matchesToolGetData(String type, String text) throws Exception {
         assertEquals(inetsoft.util.Tool.getData(type, text, true), typed(type, text));
      }

      @Disabled("mismatch S03")
      @DisplayName("S03 a null map value does not throw")
      @Test
      void s03_nullMapValue() {
         Map<String, String[]> map = new LinkedHashMap<>();
         map.put("n", null);
         assertDoesNotThrow(() -> new ParameterService(null).readParameters(map));
      }

      @DisplayName("S03 null elements are kept as null")
      @Test
      void s03_nullElements() throws Exception {
         Object typedArr = read("x", strs("1", null, "3"), "x.__type__", strs("integer")).get("x");
         assertArrayEquals(new Object[] { 1, null, 3 }, (Object[]) typedArr);

         Object untyped = read("y", strs("a", null)).get("y");
         assertArrayEquals(new Object[] { "a", null }, (Object[]) untyped);
      }

      @DisplayName("S04 a client cannot forge context variables, with or without a type")
      @Test
      void s04_forgedContextVariables() throws Exception {
         VariableTable vars = read("__principal__", strs("p"), "_USER_.__type__", strs("string"),
                                   "_USER_", strs("Hardware"), "_ROLES_", strs("r"),
                                   "_ROLES_.__type__", strs("string"),
                                   "__principal__.__type__", strs("string"),
                                   "region", strs("West"));

         assertFalse(vars.contains("__principal__"));
         assertFalse(vars.contains("_USER_"));
         assertFalse(vars.contains("_USER_.__type__"));
         assertFalse(vars.contains("_ROLES_"));
         assertFalse(vars.contains("_ROLES_.__type__"));
         assertEquals("West", vars.get("region"));
      }

      private java.util.Calendar cal(Object value) {
         java.util.Calendar c = java.util.Calendar.getInstance();
         c.setTime((java.util.Date) value);
         return c;
      }

      private void assertDate(Object value, int y, int mo, int d, int h, int mi, int s, int ms) {
         java.util.Calendar c = cal(value);
         assertEquals(y, c.get(java.util.Calendar.YEAR), "year");
         assertEquals(mo, c.get(java.util.Calendar.MONTH), "month");
         assertEquals(d, c.get(java.util.Calendar.DAY_OF_MONTH), "day");
         assertEquals(h, c.get(java.util.Calendar.HOUR_OF_DAY), "hour");
         assertEquals(mi, c.get(java.util.Calendar.MINUTE), "minute");
         assertEquals(s, c.get(java.util.Calendar.SECOND), "second");
         assertEquals(ms, c.get(java.util.Calendar.MILLISECOND), "ms");
      }

      @DisplayName("S05 range slider start / end values")
      @Test
      void s05_rangeSlider() throws Exception {
         final int JAN = java.util.Calendar.JANUARY, FEB = java.util.Calendar.FEBRUARY,
            DEC = java.util.Calendar.DECEMBER;
         assertDate(read("rs_start", strs("{y '2024'}")).get("rs_start"), 2024, JAN, 1, 0, 0, 0, 0);
         assertDate(read("rs_end", strs("{y '2024'}")).get("rs_end"), 2024, DEC, 31, 23, 59, 59, 999);
         assertDate(read("rs_start", strs("{m '2024-02'}")).get("rs_start"), 2024, FEB, 1, 0, 0, 0, 0);
         assertDate(read("rs_end", strs("{m '2024-02'}")).get("rs_end"), 2024, FEB, 29, 23, 59, 59,
                    999);
         assertDate(read("rs_start", strs("{d '2024-02-29'}")).get("rs_start"), 2024, FEB, 29, 0, 0, 0,
                    0);
         assertDate(read("rs_end", strs("{d '2024-02-29'}")).get("rs_end"), 2024, FEB, 29, 23, 59, 59,
                    999);

         Object t = read("rs_start", strs("{t '10:20:30'}")).get("rs_start");
         assertInstanceOf(java.sql.Time.class, t);
         assertEquals(10, cal(t).get(java.util.Calendar.HOUR_OF_DAY));
         assertEquals(20, cal(t).get(java.util.Calendar.MINUTE));
         assertEquals(30, cal(t).get(java.util.Calendar.SECOND));
         Object tEnd = read("rs_end", strs("{t '10:20:30'}")).get("rs_end");
         assertInstanceOf(java.sql.Time.class, tEnd);
         assertEquals(30, cal(tEnd).get(java.util.Calendar.SECOND));

         Object two = read("rs_end", strs("{y '2024'}", "{d '2024-02-29'}")).get("rs_end");
         assertInstanceOf(java.util.Date[].class, two);
         assertDate(((Object[]) two)[0], 2024, DEC, 31, 23, 59, 59, 999);
         assertDate(((Object[]) two)[1], 2024, FEB, 29, 23, 59, 59, 999);
      }

      @DisplayName("S06 range slider end of year / month / day is the same instant")
      @Test
      void s06_endConsistency() throws Exception {
         final int DEC = java.util.Calendar.DECEMBER;
         assertDate(read("rs_end", strs("{y '2024'}")).get("rs_end"), 2024, DEC, 31, 23, 59, 59, 999);
         assertDate(read("rs_end", strs("{m '2024-12'}")).get("rs_end"), 2024, DEC, 31, 23, 59, 59,
                    999);
         assertDate(read("rs_end", strs("{d '2024-12-31'}")).get("rs_end"), 2024, DEC, 31, 23, 59, 59,
                    999);
      }

      @DisplayName("S07 invalid range slider values keep the original text")
      @ParameterizedTest(name = "[{index}] ''{0}''")
      @ValueSource(strings = { "{x 1}", "", "abc" })
      void s07_invalid(String text) throws Exception {
         assertEquals(text, read("rs_start", strs(text)).get("rs_start"));
      }
   }
}
