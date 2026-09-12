/*
 * This file is part of StyleBI.
 * Copyright (C) 2026 InetSoft Technology
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
package inetsoft.uql;

import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.CoreTool;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Time;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Intent vs implementation suspects
 *
 * [Suspect 1] getValueString(Object) lacks a plain java.util.Date branch; falls through to
 *             Date.toString(), unlike getValueSQLString / CoreTool.getDataString.
 *             Conclusion (do not fix): real inconsistency, but FE happy path uses getObject →
 *             sql types; only edge display if plain util.Date reaches ConditionUtil. SQL OK.
 *
 * [Suspect 2] BOOLEAN try/catch around Boolean.valueOf is dead (never throws); garbage input
 *             silently becomes FALSE.
 *             Conclusion (do not fix): same permissive semantics as Tool.getData; FE boolean
 *             UI is True/False only — not frontend-reproducible.
 *
 * [Suspect 3] getData DATE/TIME_INSTANT return plain util.Date, not sql.Date/Timestamp
 *             (only TIME is re-wrapped).
 *             Conclusion (do not fix): inconsistent with getObject, but getData has no
 *             production callers — not frontend-reproducible.
 */
@Tag("core")
class AbstractConditionTest {

   // ---- createDefaultValue ----

   @ParameterizedTest(name = "[{index}] createDefaultValue({0}) = {1}")
   @MethodSource("createDefaultValueScalarCases")
   void createDefaultValueReturnsExpectedForScalarType(String type, Object expected) {
      assertEquals(expected, AbstractCondition.createDefaultValue(type));
   }

   static Stream<Arguments> createDefaultValueScalarCases() {
      return Stream.of(
         Arguments.of(XSchema.STRING, ""),
         Arguments.of(XSchema.CHAR, ""),
         Arguments.of(XSchema.BOOLEAN, Boolean.TRUE),
         Arguments.of(XSchema.BYTE, (byte) 0),
         Arguments.of(XSchema.DOUBLE, 0D),
         Arguments.of(XSchema.FLOAT, 0F),
         Arguments.of(XSchema.INTEGER, 0),
         Arguments.of(XSchema.LONG, 0L),
         Arguments.of(XSchema.SHORT, (short) 0),
         Arguments.of(null, ""), // null type falls through to the initial "" value
         Arguments.of("unknowntype", "") // unrecognized type falls through to the initial "" value
      );
   }

   @ParameterizedTest
   @ValueSource(strings = {XSchema.DATE, XSchema.TIME, XSchema.TIME_INSTANT})
   void createDefaultValueReturnsCurrentDateForDateLikeTypes(String type) {
      assertInstanceOf(Date.class, AbstractCondition.createDefaultValue(type));
   }

   // ---- getValueSQLString ----

   @ParameterizedTest(name = "[{index}] getValueSQLString({0}) = \"{1}\"")
   @MethodSource("getValueSQLStringScalarCases")
   void getValueSQLStringReturnsExpectedForScalar(Object value, String expected) {
      assertEquals(expected, AbstractCondition.getValueSQLString(value));
   }

   static Stream<Arguments> getValueSQLStringScalarCases() {
      return Stream.of(
         Arguments.of(null, ""),
         Arguments.of("hello", "'hello'"),
         Arguments.of(42, "42"),
         Arguments.of(3.14, "3.14")
      );
   }

   @ParameterizedTest
   @MethodSource("dateLikeSqlStringCases")
   void getValueSQLStringFormatsDateLikeValuesUsingCoreTool(Object value, DateFormat expectedFormat) {
      assertEquals(expectedFormat.format(value), AbstractCondition.getValueSQLString(value));
   }

   static Stream<Arguments> dateLikeSqlStringCases() {
      return Stream.of(
         Arguments.of(java.sql.Date.valueOf("2024-03-15"), CoreTool.dateFmt.get()),
         Arguments.of(java.sql.Time.valueOf("10:30:45"), CoreTool.timeFmt.get()),
         Arguments.of(java.sql.Timestamp.valueOf("2024-03-15 10:30:45"), CoreTool.timeInstantFmt.get())
      );
   }

   @ParameterizedTest(name = "[{index}] getValueSQLString({0}) = \"{1}\"")
   @MethodSource("getValueSQLStringArrayCases")
   void getValueSQLStringJoinsArrayElements(Object[] value, String expected) {
      assertEquals(expected, AbstractCondition.getValueSQLString(value));
   }

   static Stream<Arguments> getValueSQLStringArrayCases() {
      return Stream.of(
         Arguments.of((Object) new Object[0], ""),
         Arguments.of((Object) new Object[]{"a", "b", "c"}, "'a','b','c'"),
         Arguments.of((Object) new Object[]{1, 2, 3}, "1,2,3"),
         Arguments.of((Object) new Object[]{"x", 5}, "'x',5")
      );
   }

   @Test
   void getValueSQLStringUserVariableReturnsDollarReference() {
      UserVariable var = new UserVariable("myVar");
      assertEquals("$(myVar)", AbstractCondition.getValueSQLString(var));
   }

   @Test
   void getValueSQLStringExpressionValueReturnsRawExpression() {
      ExpressionValue expr = new ExpressionValue();
      expr.setExpression("field['A'] + 1");
      assertEquals("field['A'] + 1", AbstractCondition.getValueSQLString(expr));
   }

   @Test
   void getValueSQLStringPlainUtilDateIsFormattedAsTimeInstant() {
      // Unlike getValueString(Object), this overload does special-case a plain java.util.Date
      // (not just the java.sql.* subtypes) by formatting it as a timestamp.
      Date date = new Date();
      java.sql.Timestamp asTimestamp = new java.sql.Timestamp(date.getTime());
      assertEquals(CoreTool.timeInstantFmt.get().format(asTimestamp),
                   AbstractCondition.getValueSQLString(date));
   }

   @Test
   void getValueSQLStringFallsBackToObjectToStringForUnrecognizedType() {
      Object value = new Object() {
         @Override
         public String toString() {
            return "custom-repr";
         }
      };
      assertEquals("custom-repr", AbstractCondition.getValueSQLString(value));
   }

   // ---- getValueString(Object, String, boolean) ----

   @Test
   void getValueStringWithDefTrueAndNullValueReturnsDefault() {
      // null value with def=true -> creates default value for the type and calls toString
      String result = AbstractCondition.getValueString(null, XSchema.INTEGER, true);
      assertEquals("0", result);
   }

   @Test
   void getValueStringWithDefFalseAndNullValueReturnsToolNull() {
      String result = AbstractCondition.getValueString(null, XSchema.INTEGER, false);
      assertNotNull(result);
   }

   @ParameterizedTest
   @MethodSource("dateLikeGetValueStringCases")
   void getValueStringFormatsDateLikeValuesForMatchingType(
      Object value, String type, DateFormat expectedFormat)
   {
      assertEquals(expectedFormat.format(value), AbstractCondition.getValueString(value, type, true));
   }

   static Stream<Arguments> dateLikeGetValueStringCases() {
      return Stream.of(
         Arguments.of(java.sql.Date.valueOf("2024-03-15"), XSchema.DATE, CoreTool.dateFmt.get()),
         Arguments.of(java.sql.Time.valueOf("10:30:45"), XSchema.TIME, CoreTool.timeFmt.get()),
         Arguments.of(java.sql.Timestamp.valueOf("2024-03-15 10:30:45"), XSchema.TIME_INSTANT,
                      CoreTool.timeInstantFmt.get())
      );
   }

   @Test
   void getValueStringNonDateFallsThrough() {
      String result = AbstractCondition.getValueString("hello", XSchema.STRING, true);
      assertEquals("hello", result);
   }

   @Test
   void getValueStringDateTypeWithNonDateValueSkipsDateFormatBranch() {
      // type has a DateFormat, but value isn't a Date instance -> falls through to
      // getValueString(value) instead of calling format.format(value)
      String result = AbstractCondition.getValueString("not-a-date-object", XSchema.DATE, true);
      assertEquals("not-a-date-object", result);
   }

   // ---- getData ----

   @ParameterizedTest(name = "[{index}] getData({0}, \"{1}\") = {2}")
   @MethodSource("getDataScalarCases")
   void getDataParsesScalarTypes(String type, String rawValue, Object expected) {
      assertEquals(expected, AbstractCondition.getData(type, rawValue));
   }

   static Stream<Arguments> getDataScalarCases() {
      return Stream.of(
         Arguments.of(XSchema.STRING, "hello", "hello"),
         Arguments.of(XSchema.INTEGER, "42", 42),
         Arguments.of(XSchema.DOUBLE, "3.14", 3.14),
         Arguments.of(XSchema.BOOLEAN, "true", Boolean.TRUE)
      );
   }

   @Test
   void getDataTimeType() {
      java.sql.Time t = java.sql.Time.valueOf("10:30:45");
      String formatted = CoreTool.timeFmt.get().format(t);
      Object result = AbstractCondition.getData(XSchema.TIME, formatted);
      assertInstanceOf(Time.class, result);
   }

   @Test
   void getDataDateTypeEmptyValueReturnsNull() {
      // empty string for date types returns null
      Object result = AbstractCondition.getData(XSchema.DATE, "");
      assertNull(result);
   }

   @Test
   void getDataDateTypeMalformedValueFallsBackToToolGetData() {
      // ParseException is swallowed; falls through to Tool.getData(type, value, true), which
      // itself fails to parse "not-a-date" as any date/time shape and returns the raw string
      Object result = AbstractCondition.getData(XSchema.DATE, "not-a-date");
      assertEquals("not-a-date", result);
   }

   // ---- getDateFormat ----

   @ParameterizedTest
   @MethodSource("getDateFormatKnownTypeCases")
   void getDateFormatReturnsSharedCoreToolFormatterForDateTypes(String type, DateFormat expected) {
      // assertSame also proves the result is non-null
      assertSame(expected, AbstractCondition.getDateFormat(type));
   }

   static Stream<Arguments> getDateFormatKnownTypeCases() {
      return Stream.of(
         Arguments.of(XSchema.DATE, CoreTool.dateFmt.get()),
         Arguments.of(XSchema.TIME, CoreTool.timeFmt.get()),
         Arguments.of(XSchema.TIME_INSTANT, CoreTool.timeInstantFmt.get())
      );
   }

   @ParameterizedTest
   @ValueSource(strings = {XSchema.STRING, XSchema.INTEGER})
   void getDateFormatReturnsNullForNonDateTypes(String type) {
      assertNull(AbstractCondition.getDateFormat(type));
   }

   @Test
   void getDateFormatNullTypeThrowsNpe() {
      // no null-guard before type.equals(...); documents current fail-fast behavior
      assertThrows(NullPointerException.class, () -> AbstractCondition.getDateFormat(null));
   }

   @Test
   void dateFormatsProduceDifferentOutputs() {
      Date now = new Date();
      String dateStr = AbstractCondition.getDateFormat(XSchema.DATE).format(now);
      String timeStr = AbstractCondition.getDateFormat(XSchema.TIME).format(now);
      String instantStr = AbstractCondition.getDateFormat(XSchema.TIME_INSTANT).format(now);

      assertNotEquals(dateStr, timeStr);
      assertNotEquals(dateStr, instantStr);
      assertNotEquals(timeStr, instantStr);
   }

   // ---- getValueString(Object) [1-arg] ----

   @Nested
   class GetValueStringSingleArgTests {
      @Test
      void getValueStringUserVariableReturnsDollarReference() {
         UserVariable var = new UserVariable("myVar");
         assertEquals("$(myVar)", AbstractCondition.getValueString(var));
      }

      @Test
      void getValueStringNumberUsesToolToString() {
         assertEquals("42", AbstractCondition.getValueString(42));
      }

      // These sql.Date/Time/Timestamp branches are otherwise unreachable from the 3-arg
      // getValueString(value, type, true) tests: for DATE/TIME/TIME_INSTANT types, that
      // overload's own getDateFormat(type) branch short-circuits directly to
      // format.format(value) and never delegates to this 1-arg overload.
      @ParameterizedTest
      @MethodSource("inetsoft.uql.AbstractConditionTest#dateLikeSqlStringCases")
      void getValueStringFormatsSqlDateLikeValuesDirectly(Object value, DateFormat expectedFormat) {
         assertEquals(expectedFormat.format(value), AbstractCondition.getValueString(value));
      }
   }

   // ---- getObject(String, String) ----

   @Nested
   class GetObjectTwoArgTests {
      @Test
      void getObjectNullValueForNonStringTypeReturnsDefault() {
         assertEquals(0, AbstractCondition.getObject(XSchema.INTEGER, null));
      }

      @Test
      void getObjectEmptyValueForNonStringTypeReturnsDefault() {
         assertEquals(0, AbstractCondition.getObject(XSchema.INTEGER, ""));
      }

      @Test
      void getObjectEmptyValueForStringTypeReturnsEmptyStringNotDefault() {
         // string/char types are exempt from the "empty value -> default" rule
         assertEquals("", AbstractCondition.getObject(XSchema.STRING, ""));
      }

      @Test
      void getObjectValidFloatReturnsFloat() {
         assertEquals(3.14F, AbstractCondition.getObject(XSchema.FLOAT, "3.14"));
      }

      @Test
      void getObjectInvalidFloatSwallowsExceptionAndReturnsZero() {
         assertEquals(0F, AbstractCondition.getObject(XSchema.FLOAT, "not-a-number"));
      }

      @Test
      void getObjectValidIntegerReturnsInteger() {
         assertEquals(42, AbstractCondition.getObject(XSchema.INTEGER, "42"));
      }

      @Test
      void getObjectDecimalStringForIntegerFallsBackToDoubleTruncation() {
         // Integer.valueOf("3.9") fails, falls back to Double.valueOf(value).intValue()
         assertEquals(3, AbstractCondition.getObject(XSchema.INTEGER, "3.9"));
      }

      @Test
      void getObjectTotallyInvalidIntegerReturnsZero() {
         assertEquals(0, AbstractCondition.getObject(XSchema.INTEGER, "abc"));
      }

      @Test
      void getObjectValidLongReturnsLong() {
         assertEquals(123456789L, AbstractCondition.getObject(XSchema.LONG, "123456789"));
      }

      @Test
      void getObjectValidByteReturnsByte() {
         assertEquals((byte) 42, AbstractCondition.getObject(XSchema.BYTE, "42"));
      }

      @Test
      void getObjectInvalidByteSwallowsExceptionAndReturnsZero() {
         // BYTE has only a single-level fallback (unlike SHORT/INTEGER/LONG's Double retry)
         assertEquals((byte) 0, AbstractCondition.getObject(XSchema.BYTE, "not-a-number"));
      }

      @Test
      void getObjectValidDoubleReturnsDouble() {
         assertEquals(3.14, AbstractCondition.getObject(XSchema.DOUBLE, "3.14"));
      }

      @Test
      void getObjectInvalidDoubleSwallowsExceptionAndReturnsZero() {
         assertEquals(0D, AbstractCondition.getObject(XSchema.DOUBLE, "not-a-number"));
      }

      @Test
      void getObjectValidShortReturnsShort() {
         assertEquals((short) 42, AbstractCondition.getObject(XSchema.SHORT, "42"));
      }

      @Test
      void getObjectDecimalStringForShortFallsBackToDoubleTruncation() {
         // Short.valueOf("3.9") fails, falls back to Double.valueOf(value).shortValue()
         assertEquals((short) 3, AbstractCondition.getObject(XSchema.SHORT, "3.9"));
      }

      @Test
      void getObjectTotallyInvalidShortReturnsZero() {
         assertEquals((short) 0, AbstractCondition.getObject(XSchema.SHORT, "abc"));
      }

      @Test
      void getObjectDecimalStringForLongFallsBackToDoubleTruncation() {
         // Long.valueOf("3.9") fails, falls back to Double.valueOf(value).longValue()
         assertEquals(3L, AbstractCondition.getObject(XSchema.LONG, "3.9"));
      }

      @Test
      void getObjectTotallyInvalidLongReturnsZero() {
         assertEquals(0L, AbstractCondition.getObject(XSchema.LONG, "abc"));
      }

      @Test
      void getObjectValidDateRoundTrips() {
         java.sql.Date d = java.sql.Date.valueOf("2024-03-15");
         String formatted = CoreTool.dateFmt.get().format(d);
         Object result = AbstractCondition.getObject(XSchema.DATE, formatted);
         assertInstanceOf(java.sql.Date.class, result);
      }

      @Test
      void getObjectInvalidDateSwallowsExceptionAndReturnsCurrentDate() {
         // malformed date silently falls back to "now" instead of surfacing an error
         Object result = AbstractCondition.getObject(XSchema.DATE, "not-a-date");
         assertInstanceOf(java.sql.Date.class, result);
      }

      @Test
      void getObjectValidTimeColonFormatReturnsTime() {
         Object result = AbstractCondition.getObject(XSchema.TIME, "10:30:45");
         assertInstanceOf(java.sql.Time.class, result);
      }

      @Test
      void getObjectValidTimeInstantNewFormatReturnsTimestamp() {
         java.sql.Timestamp ts = java.sql.Timestamp.valueOf("2024-03-15 10:30:45");
         String formatted = CoreTool.timeInstantFmt.get().format(ts);
         Object result = AbstractCondition.getObject(XSchema.TIME_INSTANT, formatted);
         assertInstanceOf(java.sql.Timestamp.class, result);
      }

      @Test
      void getObjectUnrecognizedTypeReturnsValueUnchanged() {
         assertEquals("raw-value", AbstractCondition.getObject("unknowntype", "raw-value"));
      }
   }

   // ---- getObject(String, String, boolean) ----

   @Nested
   class GetObjectThreeArgTests {
      @Test
      void getObjectWithVarTrueCreatesUserVariable() {
         Object result = AbstractCondition.getObject(XSchema.INTEGER, "myVar", true);
         assertInstanceOf(UserVariable.class, result);
         assertEquals("myVar", ((UserVariable) result).getName());
      }

      @Test
      void getObjectWithVarFalseDelegatesToTwoArgOverload() {
         assertEquals(42, AbstractCondition.getObject(XSchema.INTEGER, "42", false));
      }
   }

   // ---- checkValueString ----

   @Nested
   class CheckValueStringTests {
      @Test
      void checkValueStringNullValueBypassesValidation() throws Exception {
         AbstractCondition.checkValueString(null, XSchema.INTEGER);
      }

      @ParameterizedTest
      @ValueSource(strings = {"", "null"})
      void checkValueStringBlankOrLiteralNullBypassesValidation(String value) throws Exception {
         AbstractCondition.checkValueString(value, XSchema.INTEGER);
      }

      @Test
      void checkValueStringVariableReferenceBypassesValidation() throws Exception {
         // looks like a variable reference $(...) -> not validated against type, even though
         // "myVar" would fail INTEGER validation
         AbstractCondition.checkValueString("$(myVar)", XSchema.INTEGER);
      }

      @Test
      void checkValueStringUnclosedVariableReferenceIsValidatedNormally() {
         // Missing closing ')' means the "starts and ends with $(...)" bypass check fails,
         // so this falls through to normal INTEGER validation and throws.
         assertThrows(NumberFormatException.class,
                      () -> AbstractCondition.checkValueString("$(myVar", XSchema.INTEGER));
      }

      @ParameterizedTest
      @MethodSource("validNumericValues")
      void checkValueStringValidNumericDoesNotThrow(String type, String value) throws Exception {
         AbstractCondition.checkValueString(value, type);
      }

      static Stream<Arguments> validNumericValues() {
         return Stream.of(
            Arguments.of(XSchema.FLOAT, "3.14"),
            Arguments.of(XSchema.DOUBLE, "3.14"),
            Arguments.of(XSchema.BYTE, "12"),
            Arguments.of(XSchema.SHORT, "123"),
            Arguments.of(XSchema.INTEGER, "123"),
            Arguments.of(XSchema.LONG, "123456789")
         );
      }

      @ParameterizedTest
      @MethodSource("numericTypes")
      void checkValueStringInvalidNumericThrows(String type) {
         assertThrows(NumberFormatException.class,
                      () -> AbstractCondition.checkValueString("not-a-number", type));
      }

      static Stream<Arguments> numericTypes() {
         return Stream.of(
            Arguments.of(XSchema.FLOAT), Arguments.of(XSchema.DOUBLE),
            Arguments.of(XSchema.BYTE), Arguments.of(XSchema.SHORT),
            Arguments.of(XSchema.INTEGER), Arguments.of(XSchema.LONG)
         );
      }

      @Test
      void checkValueStringValidDateDoesNotThrow() throws Exception {
         String value = CoreTool.dateFmt.get().format(new Date());
         AbstractCondition.checkValueString(value, XSchema.DATE);
      }

      @Test
      void checkValueStringInvalidDateThrowsParseException() {
         assertThrows(ParseException.class,
                      () -> AbstractCondition.checkValueString("not-a-date", XSchema.DATE));
      }

      @Test
      void checkValueStringValidTimeDoesNotThrow() throws Exception {
         String value = CoreTool.timeFmt.get().format(new Date());
         AbstractCondition.checkValueString(value, XSchema.TIME);
      }

      @Test
      void checkValueStringInvalidTimeThrowsParseException() {
         assertThrows(ParseException.class,
                      () -> AbstractCondition.checkValueString("not-a-time", XSchema.TIME));
      }

      @Test
      void checkValueStringValidTimeInstantDoesNotThrow() throws Exception {
         String value = CoreTool.timeInstantFmt.get().format(new Date());
         AbstractCondition.checkValueString(value, XSchema.TIME_INSTANT);
      }

      @Test
      void checkValueStringInvalidTimeInstantThrowsParseException() {
         assertThrows(ParseException.class,
                      () -> AbstractCondition.checkValueString("not-a-timestamp", XSchema.TIME_INSTANT));
      }
   }
}
