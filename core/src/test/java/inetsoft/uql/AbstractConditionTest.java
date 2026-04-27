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
package inetsoft.uql;

import inetsoft.uql.schema.XSchema;
import inetsoft.util.CoreTool;
import org.junit.jupiter.api.Test;

import java.sql.Time;
import java.text.DateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class AbstractConditionTest {

   // ---- createDefaultValue ----

   @Test
   void createDefaultValueString() {
      assertEquals("", AbstractCondition.createDefaultValue(XSchema.STRING));
   }

   @Test
   void createDefaultValueChar() {
      assertEquals("", AbstractCondition.createDefaultValue(XSchema.CHAR));
   }

   @Test
   void createDefaultValueBoolean() {
      assertEquals(Boolean.TRUE, AbstractCondition.createDefaultValue(XSchema.BOOLEAN));
   }

   @Test
   void createDefaultValueByte() {
      assertEquals((byte) 0, AbstractCondition.createDefaultValue(XSchema.BYTE));
   }

   @Test
   void createDefaultValueDouble() {
      assertEquals(0D, AbstractCondition.createDefaultValue(XSchema.DOUBLE));
   }

   @Test
   void createDefaultValueFloat() {
      assertEquals(0F, AbstractCondition.createDefaultValue(XSchema.FLOAT));
   }

   @Test
   void createDefaultValueInteger() {
      assertEquals(0, AbstractCondition.createDefaultValue(XSchema.INTEGER));
   }

   @Test
   void createDefaultValueLong() {
      assertEquals(0L, AbstractCondition.createDefaultValue(XSchema.LONG));
   }

   @Test
   void createDefaultValueShort() {
      assertEquals((short) 0, AbstractCondition.createDefaultValue(XSchema.SHORT));
   }

   @Test
   void createDefaultValueDate() {
      Object val = AbstractCondition.createDefaultValue(XSchema.DATE);
      assertInstanceOf(Date.class, val);
   }

   @Test
   void createDefaultValueTime() {
      Object val = AbstractCondition.createDefaultValue(XSchema.TIME);
      assertInstanceOf(Date.class, val);
   }

   @Test
   void createDefaultValueTimeInstant() {
      Object val = AbstractCondition.createDefaultValue(XSchema.TIME_INSTANT);
      assertInstanceOf(Date.class, val);
   }

   @Test
   void createDefaultValueNull() {
      // null type returns ""
      assertEquals("", AbstractCondition.createDefaultValue(null));
   }

   @Test
   void createDefaultValueUnknownType() {
      // Unknown types fall through and return "" (initial value)
      assertEquals("", AbstractCondition.createDefaultValue("unknowntype"));
   }

   // ---- getValueSQLString ----

   @Test
   void getValueSQLStringNull() {
      assertEquals("", AbstractCondition.getValueSQLString(null));
   }

   @Test
   void getValueSQLStringPlainString() {
      assertEquals("'hello'", AbstractCondition.getValueSQLString("hello"));
   }

   @Test
   void getValueSQLStringSqlDate() {
      java.sql.Date d = java.sql.Date.valueOf("2024-03-15");
      String result = AbstractCondition.getValueSQLString(d);
      assertEquals(CoreTool.dateFmt.get().format(d), result);
   }

   @Test
   void getValueSQLStringSqlTime() {
      java.sql.Time t = java.sql.Time.valueOf("10:30:45");
      String result = AbstractCondition.getValueSQLString(t);
      assertEquals(CoreTool.timeFmt.get().format(t), result);
   }

   @Test
   void getValueSQLStringSqlTimestamp() {
      java.sql.Timestamp ts = java.sql.Timestamp.valueOf("2024-03-15 10:30:45");
      String result = AbstractCondition.getValueSQLString(ts);
      assertEquals(CoreTool.timeInstantFmt.get().format(ts), result);
   }

   @Test
   void getValueSQLStringInteger() {
      assertEquals("42", AbstractCondition.getValueSQLString(42));
   }

   @Test
   void getValueSQLStringDouble() {
      assertEquals("3.14", AbstractCondition.getValueSQLString(3.14));
   }

   @Test
   void getValueSQLStringEmptyArray() {
      Object[] arr = new Object[0];
      assertEquals("", AbstractCondition.getValueSQLString(arr));
   }

   @Test
   void getValueSQLStringArrayOfStrings() {
      Object[] arr = new Object[]{"a", "b", "c"};
      assertEquals("'a','b','c'", AbstractCondition.getValueSQLString(arr));
   }

   @Test
   void getValueSQLStringArrayOfNumbers() {
      Object[] arr = new Object[]{1, 2, 3};
      assertEquals("1,2,3", AbstractCondition.getValueSQLString(arr));
   }

   @Test
   void getValueSQLStringMixedArray() {
      Object[] arr = new Object[]{"x", 5};
      assertEquals("'x',5", AbstractCondition.getValueSQLString(arr));
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

   @Test
   void getValueStringDateWithDateType() {
      java.sql.Date d = java.sql.Date.valueOf("2024-03-15");
      String result = AbstractCondition.getValueString(d, XSchema.DATE, true);
      assertEquals(CoreTool.dateFmt.get().format(d), result);
   }

   @Test
   void getValueStringTimeWithTimeType() {
      java.sql.Time t = java.sql.Time.valueOf("10:30:45");
      String result = AbstractCondition.getValueString(t, XSchema.TIME, true);
      assertEquals(CoreTool.timeFmt.get().format(t), result);
   }

   @Test
   void getValueStringTimestampWithInstantType() {
      java.sql.Timestamp ts = java.sql.Timestamp.valueOf("2024-03-15 10:30:45");
      String result = AbstractCondition.getValueString(ts, XSchema.TIME_INSTANT, true);
      assertEquals(CoreTool.timeInstantFmt.get().format(ts), result);
   }

   @Test
   void getValueStringNonDateFallsThrough() {
      String result = AbstractCondition.getValueString("hello", XSchema.STRING, true);
      assertEquals("hello", result);
   }

   // ---- getData ----

   @Test
   void getDataString() {
      Object result = AbstractCondition.getData(XSchema.STRING, "hello");
      assertEquals("hello", result);
   }

   @Test
   void getDataInteger() {
      Object result = AbstractCondition.getData(XSchema.INTEGER, "42");
      assertEquals(42, result);
   }

   @Test
   void getDataDouble() {
      Object result = AbstractCondition.getData(XSchema.DOUBLE, "3.14");
      assertEquals(3.14, result);
   }

   @Test
   void getDataBoolean() {
      Object result = AbstractCondition.getData(XSchema.BOOLEAN, "true");
      assertEquals(Boolean.TRUE, result);
   }

   @Test
   void getDataDateTypeFormattedValue() {
      java.sql.Date d = java.sql.Date.valueOf("2024-03-15");
      String formatted = CoreTool.dateFmt.get().format(d);
      Object result = AbstractCondition.getData(XSchema.DATE, formatted);
      assertNotNull(result);
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

   // ---- getDateFormat ----

   @Test
   void getDateFormatDateReturnsNonNull() {
      DateFormat fmt = AbstractCondition.getDateFormat(XSchema.DATE);
      assertNotNull(fmt);
   }

   @Test
   void getDateFormatDateReturnsSameAsCoreTool() {
      DateFormat fmt = AbstractCondition.getDateFormat(XSchema.DATE);
      assertSame(CoreTool.dateFmt.get(), fmt);
   }

   @Test
   void getDateFormatTimeReturnsNonNull() {
      DateFormat fmt = AbstractCondition.getDateFormat(XSchema.TIME);
      assertNotNull(fmt);
   }

   @Test
   void getDateFormatTimeReturnsSameAsCoreTool() {
      DateFormat fmt = AbstractCondition.getDateFormat(XSchema.TIME);
      assertSame(CoreTool.timeFmt.get(), fmt);
   }

   @Test
   void getDateFormatTimeInstantReturnsNonNull() {
      DateFormat fmt = AbstractCondition.getDateFormat(XSchema.TIME_INSTANT);
      assertNotNull(fmt);
   }

   @Test
   void getDateFormatTimeInstantReturnsSameAsCoreTool() {
      DateFormat fmt = AbstractCondition.getDateFormat(XSchema.TIME_INSTANT);
      assertSame(CoreTool.timeInstantFmt.get(), fmt);
   }

   @Test
   void getDateFormatStringTypeReturnsNull() {
      assertNull(AbstractCondition.getDateFormat(XSchema.STRING));
   }

   @Test
   void getDateFormatIntegerTypeReturnsNull() {
      assertNull(AbstractCondition.getDateFormat(XSchema.INTEGER));
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
}
