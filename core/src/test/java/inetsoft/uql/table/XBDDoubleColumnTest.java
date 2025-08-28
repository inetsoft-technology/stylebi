/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.uql.table;

import inetsoft.util.Tool;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class XBDDoubleColumnTest {

   XBDDoubleColumn column;

   @BeforeEach
   void setUp() {
      column = new XBDDoubleColumn((char) 10, (char) 20);
   }

   @Test
   void testGetCreator() {
      assertNotNull(XBDDoubleColumn.getCreator());
      assertEquals(Double.class, XBDDoubleColumn.getCreator().getColType());
   }

   @Test
   void testGetType() {
      assertEquals(XSchema.DOUBLE, column.getType());
   }

   @Test
   void testIsPrimitive() {
      assertTrue(column.isPrimitive());
   }

   @Test
   void testIsSerializable() {
      assertTrue(column.isSerializable());
   }

   @Test
   void testCapacity() {
      assertEquals(20, column.capacity());
   }

   @Test
   void testAddAndGetObject() {
      column.addObject(null);
      column.addObject(BigDecimal.valueOf(123.45));
      column.addObject(42);
      column.addObject(3.14f);
      column.addObject(100L);

      assertNull(column.getObject(0));
      assertEquals(123.45, column.getObject(1));
      assertEquals(42.0, column.getObject(2));
      assertEquals(3.14, (Double) column.getObject(3), 0.01);
      assertEquals(100.0, column.getObject(4));
   }

   @Test
   void testIsNull() {
      column.addObject(null);
      column.addObject(BigDecimal.valueOf(123.45));

      assertTrue(column.isNull(0));
      assertFalse(column.isNull(1));
   }

   @Test
   void testGetValue() {
      column.addObject(BigDecimal.valueOf(123.5));
      column.addObject(null);
      column.addObject(BigDecimal.valueOf(1));
      column.addObject(BigDecimal.valueOf(0));
      column.addObject(BigDecimal.valueOf(42.7));


      assertEquals(123.5, column.getDouble(0), 0.001);
      assertEquals(123.5f, column.getFloat(0), 0.001f);
      assertEquals(123L, column.getLong(0));
      assertEquals(123, column.getInt(0));
      assertEquals(Tool.NULL_DOUBLE, column.getDouble(1));

      assertTrue(column.getBoolean(2));
      assertFalse(column.getBoolean(3));
      assertTrue(column.getBoolean(4));

      assertTrue(column.isNull(1));
   }

   @Test
   void testDoubleScales() {
      column.addObject(new BigDecimal("100"));
      column.addObject(new BigDecimal("1.23"));
      column.addObject(new BigDecimal("0.001"));
      column.addObject(new BigDecimal("1E9"));
      column.addObject(new BigDecimal("1E11"));
      column.addObject(new BigDecimal("1.23456789"));
      column.addObject(new BigDecimal("123456.789012"));

      assertEquals(100.0, column.getDouble(0), 0.001);
      assertEquals(1.23, column.getDouble(1), 0.001);
      assertEquals(0.001, column.getDouble(2), 1e-6);
      assertEquals(1E9, column.getDouble(3), 1);
      assertEquals(1E11, column.getDouble(4), 1);
      assertEquals(1.23456789, column.getDouble(5), 1e-8);
      assertEquals(123456.789012, column.getDouble(6), 1e-6);
   }

   @Test
   void testLongFlag() {
      assertFalse(column.isLong());

      column.setLong(true);
      assertTrue(column.isLong());

      column.addObject(BigDecimal.valueOf(100));
      assertEquals(100L, column.getObject(0));

      column.addObject(BigDecimal.valueOf(100.5));
      assertEquals(100.5, column.getObject(1));
   }

   @Test
   void testLifecycle() {
      assertTrue(column.isValid());

      column.addObject(1.23);
      column.invalidate();
      assertFalse(column.isValid());

      column.dispose();
      assertThrows(NullPointerException.class, () -> column.getObject(0));
   }

   @Test
   void testInMemoryLength() {
      assertEquals(10, column.getInMemoryLength());
      column.addObject(1.23);
      assertEquals(10, column.getInMemoryLength());
   }

   @Test
   void testArrayIndexBounds() {
      column.addObject(1.23);
      assertDoesNotThrow(() -> column.getObject(0));
      assertThrows(ArrayIndexOutOfBoundsException.class, () -> column.getObject(10));
   }

   @Test
   void testDoubleLarge() {
      BigDecimal largeValue = new BigDecimal("12345678901234567890.1234567890");
      column.addObject(largeValue);
      assertTrue(Double.isFinite(column.getDouble(0)));
   }

   @Test
   void testArrayExpansion() {
      for (int i = 0; i < 15; i++) {
         column.addObject(i);
      }
      assertEquals(14.0, column.getDouble(14), 0.001);
   }

   @Test
   void testSpecialAndNegative() {
      column.addObject(BigDecimal.ZERO);
      column.addObject(BigDecimal.ONE);
      column.addObject(BigDecimal.valueOf(-1.5));
      column.addObject(BigDecimal.valueOf(-123.45));
      column.addObject(BigDecimal.valueOf(-0.001));

      assertEquals(0.0, column.getDouble(0));
      assertEquals(1.0, column.getDouble(1));
      assertEquals(-1.5, column.getDouble(2));
      assertEquals(-123.45, column.getDouble(3), 0.001);
      assertEquals(-0.001, column.getDouble(4), 1e-6);
   }

   @Test
   void testCopyToBuffer() {
      column.addObject(1.23);
      column.addObject(4.56);

      ByteBuffer buffer = column.copyToBuffer();

      assertNotNull(buffer);
      assertEquals(16, buffer.limit());
      assertEquals(1.23, buffer.asDoubleBuffer().get(0), 0.001);
      assertEquals(4.56, buffer.asDoubleBuffer().get(1), 0.001);
   }
}