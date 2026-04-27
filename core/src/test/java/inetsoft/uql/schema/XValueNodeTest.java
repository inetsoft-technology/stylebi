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
package inetsoft.uql.schema;

import inetsoft.uql.asset.ExpressionValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XValueNode and its static factory methods.
 */
public class XValueNodeTest {

   // -----------------------------------------------------------------------
   // createValueNode(Object, String) — type inference from value
   //
   // IMPORTANT: createValueNode(String, String) is a separate overload that
   // takes (name, type). When both arguments are Strings, Java dispatches to
   // that overload, NOT to createValueNode(Object value, String name).
   // Therefore, to test createValueNode(Object, String) with Object values,
   // we must pass non-String objects.
   // -----------------------------------------------------------------------

   @Test
   void createValueNode_nullValue_returnsNull() {
      // null as Object (cast) → createValueNode(Object, String) → returns null
      assertNull(XValueNode.createValueNode((Object) null, "myNode"));
   }

   @Test
   void createValueNode_string_dispatchesToNameTypeOverload() {
      // createValueNode("hello", "n1") dispatches to createValueNode(String name, String type)
      // because String is more specific than Object.
      // So name="hello", type="n1" (unknown type) → StringValue named "hello" with null value.
      XValueNode node = XValueNode.createValueNode("hello", "n1");
      assertNotNull(node);
      assertInstanceOf(StringValue.class, node);
      assertEquals("hello", node.getName());
      assertNull(node.getValue());
   }

   @Test
   void createValueNode_objectString_returnsStringValue() {
      // To get the (Object, String) overload with a String value, cast to Object explicitly.
      Object strValue = "world";
      XValueNode node = XValueNode.createValueNode(strValue, "n1");
      assertNotNull(node);
      assertInstanceOf(StringValue.class, node);
      assertEquals("n1", node.getName());
      assertEquals("world", node.getValue());
   }

   @Test
   void createValueNode_boolean_returnsBooleanValue() {
      XValueNode node = XValueNode.createValueNode(Boolean.TRUE, "n2");
      assertNotNull(node);
      assertInstanceOf(BooleanValue.class, node);
      assertEquals(Boolean.TRUE, node.getValue());
   }

   @Test
   void createValueNode_double_returnsDoubleValue() {
      XValueNode node = XValueNode.createValueNode(3.14d, "n3");
      assertNotNull(node);
      assertInstanceOf(DoubleValue.class, node);
      assertEquals(3.14d, node.getValue());
   }

   @Test
   void createValueNode_float_returnsFloatValue() {
      XValueNode node = XValueNode.createValueNode(2.5f, "n4");
      assertNotNull(node);
      assertInstanceOf(FloatValue.class, node);
      assertEquals(2.5f, node.getValue());
   }

   @Test
   void createValueNode_integer_returnsIntegerValue() {
      // Integer is a Number but not Double or Float, so IntegerValue
      XValueNode node = XValueNode.createValueNode(42, "n5");
      assertNotNull(node);
      assertInstanceOf(IntegerValue.class, node);
      assertEquals(42, node.getValue());
   }

   @Test
   void createValueNode_long_returnsIntegerValue() {
      // Long is a Number but not Double or Float, so IntegerValue
      XValueNode node = XValueNode.createValueNode(100L, "n6");
      assertNotNull(node);
      assertInstanceOf(IntegerValue.class, node);
   }

   @Test
   void createValueNode_sqlDate_returnsDateValue() {
      Date sqlDate = Date.valueOf("2024-01-15");
      XValueNode node = XValueNode.createValueNode(sqlDate, "n7");
      assertNotNull(node);
      assertInstanceOf(DateValue.class, node);
      assertEquals(sqlDate, node.getValue());
   }

   @Test
   void createValueNode_sqlTimestamp_returnsTimeInstantValue() {
      Timestamp ts = Timestamp.valueOf("2024-01-15 12:30:00");
      XValueNode node = XValueNode.createValueNode(ts, "n8");
      assertNotNull(node);
      assertInstanceOf(TimeInstantValue.class, node);
   }

   @Test
   void createValueNode_sqlTime_returnsTimeValue() {
      Time t = Time.valueOf("10:30:00");
      XValueNode node = XValueNode.createValueNode(t, "n9");
      assertNotNull(node);
      assertInstanceOf(TimeValue.class, node);
   }

   @Test
   void createValueNode_javaUtilDate_returnsTimeInstantValue() {
      java.util.Date d = new java.util.Date();
      XValueNode node = XValueNode.createValueNode(d, "n10");
      assertNotNull(node);
      assertInstanceOf(TimeInstantValue.class, node);
   }

   @Test
   void createValueNode_character_returnsCharacterValue() {
      XValueNode node = XValueNode.createValueNode('A', "n11");
      assertNotNull(node);
      assertInstanceOf(CharacterValue.class, node);
   }

   @Test
   void createValueNode_objectArrayWithString_returnsStringValue() {
      Object[] arr = new Object[]{"first"};
      XValueNode node = XValueNode.createValueNode(arr, "n12");
      assertNotNull(node);
      assertInstanceOf(StringValue.class, node);
   }

   @Test
   void createValueNode_emptyObjectArray_returnsNull() {
      Object[] arr = new Object[]{};
      XValueNode node = XValueNode.createValueNode(arr, "n13");
      assertNull(node);
   }

   // -----------------------------------------------------------------------
   // createValueNodeByType(String, String, Object)
   // -----------------------------------------------------------------------

   @ParameterizedTest
   @CsvSource({
      "string",
      "boolean",
      "byte",
      "double",
      "float",
      "integer",
      "long",
      "short",
      "date",
      "time",
      "timeInstant",
      "char"
   })
   void createValueNodeByType_allKnownTypes_returnsNonNull(String type) {
      XValueNode node = XValueNode.createValueNodeByType("n", type, null);
      assertNotNull(node);
   }

   @Test
   void createValueNodeByType_stringType_returnsStringValueWithCorrectType() {
      XValueNode node = XValueNode.createValueNodeByType("n", XSchema.STRING, "hello");
      assertInstanceOf(StringValue.class, node);
      assertEquals(XSchema.STRING, node.getType());
      assertEquals("hello", node.getValue());
   }

   @Test
   void createValueNodeByType_booleanType_returnsBooleanValue() {
      XValueNode node = XValueNode.createValueNodeByType("n", XSchema.BOOLEAN, null);
      assertInstanceOf(BooleanValue.class, node);
      assertEquals(XSchema.BOOLEAN, node.getType());
   }

   @Test
   void createValueNodeByType_doubleType_returnsDoubleValue() {
      XValueNode node = XValueNode.createValueNodeByType("n", XSchema.DOUBLE, 1.5d);
      assertInstanceOf(DoubleValue.class, node);
      assertEquals(1.5d, node.getValue());
   }

   @Test
   void createValueNodeByType_floatType_returnsFloatValue() {
      XValueNode node = XValueNode.createValueNodeByType("n", XSchema.FLOAT, null);
      assertInstanceOf(FloatValue.class, node);
   }

   @Test
   void createValueNodeByType_integerType_returnsIntegerValue() {
      XValueNode node = XValueNode.createValueNodeByType("n", XSchema.INTEGER, null);
      assertInstanceOf(IntegerValue.class, node);
   }

   @Test
   void createValueNodeByType_longType_returnsLongValue() {
      XValueNode node = XValueNode.createValueNodeByType("n", XSchema.LONG, null);
      assertInstanceOf(LongValue.class, node);
   }

   @Test
   void createValueNodeByType_shortType_returnsShortValue() {
      XValueNode node = XValueNode.createValueNodeByType("n", XSchema.SHORT, null);
      assertInstanceOf(ShortValue.class, node);
   }

   @Test
   void createValueNodeByType_byteType_returnsByteValue() {
      XValueNode node = XValueNode.createValueNodeByType("n", XSchema.BYTE, null);
      assertInstanceOf(ByteValue.class, node);
   }

   @Test
   void createValueNodeByType_dateType_returnsDateValue() {
      XValueNode node = XValueNode.createValueNodeByType("n", XSchema.DATE, null);
      assertInstanceOf(DateValue.class, node);
   }

   @Test
   void createValueNodeByType_timeType_returnsTimeValue() {
      XValueNode node = XValueNode.createValueNodeByType("n", XSchema.TIME, null);
      assertInstanceOf(TimeValue.class, node);
   }

   @Test
   void createValueNodeByType_timeInstantType_returnsTimeInstantValue() {
      XValueNode node = XValueNode.createValueNodeByType("n", XSchema.TIME_INSTANT, null);
      assertInstanceOf(TimeInstantValue.class, node);
   }

   @Test
   void createValueNodeByType_charType_returnsCharacterValue() {
      XValueNode node = XValueNode.createValueNodeByType("n", XSchema.CHAR, null);
      assertInstanceOf(CharacterValue.class, node);
   }

   @Test
   void createValueNodeByType_nullType_returnsStringValue() {
      XValueNode node = XValueNode.createValueNodeByType("n", null, null);
      assertInstanceOf(StringValue.class, node);
   }

   @Test
   void createValueNodeByType_unknownType_returnsStringValue() {
      XValueNode node = XValueNode.createValueNodeByType("n", "unknownType", null);
      assertInstanceOf(StringValue.class, node);
   }

   // -----------------------------------------------------------------------
   // isExpression()
   // -----------------------------------------------------------------------

   @Test
   void isExpression_normalStringValue_returnsFalse() {
      XValueNode node = XValueNode.createValueNode("hello", "n");
      assertFalse(node.isExpression());
   }

   @Test
   void isExpression_expressionValueSet_returnsTrue() {
      XValueNode node = new StringValue("n");
      ExpressionValue ev = new ExpressionValue();
      ev.setExpression("someScript()");
      node.setValue(ev);
      assertTrue(node.isExpression());
   }

   @Test
   void isExpression_nullValue_returnsFalse() {
      XValueNode node = new StringValue("n");
      assertFalse(node.isExpression());
   }

   // -----------------------------------------------------------------------
   // format()
   // -----------------------------------------------------------------------

   @Test
   void format_normalStringValue_returnsValueToString() {
      // cast to Object to call the (Object, String) overload correctly
      Object value = "world";
      XValueNode node = XValueNode.createValueNode(value, "n");
      assertEquals("world", node.format());
   }

   @Test
   void format_expressionValue_returnsExpressionString() {
      XValueNode node = new StringValue("n");
      ExpressionValue ev = new ExpressionValue();
      ev.setExpression("myExpr");
      node.setValue(ev);
      assertEquals("myExpr", node.format());
   }

   @Test
   void format_integerValue_returnsIntegerString() {
      XValueNode node = XValueNode.createValueNode(99, "n");
      assertEquals("99", node.format());
   }

   // -----------------------------------------------------------------------
   // equals(Object)
   // -----------------------------------------------------------------------

   @Test
   void equals_sameNameAndValue_returnsTrue() {
      XValueNode n1 = new StringValue("myName");
      n1.setValue("foo");
      XValueNode n2 = new StringValue("myName");
      n2.setValue("foo");
      assertTrue(n1.equals(n2));
   }

   @Test
   void equals_differentName_returnsFalse() {
      XValueNode n1 = new StringValue("nameA");
      n1.setValue("foo");
      XValueNode n2 = new StringValue("nameB");
      n2.setValue("foo");
      assertFalse(n1.equals(n2));
   }

   @Test
   void equals_differentValue_returnsFalse() {
      XValueNode n1 = new StringValue("myName");
      n1.setValue("foo");
      XValueNode n2 = new StringValue("myName");
      n2.setValue("bar");
      assertFalse(n1.equals(n2));
   }

   @Test
   void equals_null_returnsFalse() {
      XValueNode n1 = new StringValue("myName");
      n1.setValue("foo");
      assertFalse(n1.equals(null));
   }

   @Test
   void equals_differentClass_returnsFalse() {
      XValueNode n1 = new StringValue("myName");
      n1.setValue("foo");
      XValueNode n2 = new IntegerValue("myName");
      n2.setValue("foo");
      // different classes → false
      assertFalse(n1.equals(n2));
   }

   @Test
   void equals_sameInstance_returnsTrue() {
      XValueNode n1 = new StringValue("myName");
      n1.setValue("foo");
      assertTrue(n1.equals(n1));
   }

   @Test
   void equals_sameNameSameNullValue_returnsTrue() {
      XValueNode n1 = new StringValue("myName");
      XValueNode n2 = new StringValue("myName");
      // both have null value
      assertTrue(n1.equals(n2));
   }
}
