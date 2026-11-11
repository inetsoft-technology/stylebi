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
}
