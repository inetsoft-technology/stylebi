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

import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class XDoubleColumnTest {

   private XDoubleColumn column;

   @BeforeEach
   void setUp() {
      column = new XDoubleColumn((char) 10, (char) 20);
   }

   @Test
   void testGetCreator() {
      assertNotNull(XDoubleColumn.getCreator());
      assertEquals(Double.class, XDoubleColumn.getCreator().getColType());
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
   void testCapacity() {
      assertEquals(20, column.capacity());
   }

   @Test
   void testAddGetObject() {
      column.addObject(1.23);
      column.addObject(Tool.NULL_DOUBLE);
      column.addObject("");

      assertEquals(1.23, column.getDouble(0));
      assertTrue(column.isNull(1));
      assertTrue(column.isNull(2));
   }

   @Test
   void testIsNull() {
      column.addObject(null);
      assertTrue(column.isNull(0));
      column.addObject(1.23);
      assertFalse(column.isNull(1));
   }

   @Test
   void testGetValue() {
      column.addObject(1.23);
      column.addObject(0.0);
      assertEquals(1.23, column.getDouble(0));
      assertEquals(1.23f, column.getFloat(0));
      assertEquals(1L, column.getLong(0));
      assertEquals(1, column.getInt(0));
      assertEquals((short) 1, column.getShort(0));
      assertEquals((byte) 1, column.getByte(0));
      assertTrue(column.getBoolean(0));
      assertFalse(column.getBoolean(1));
   }

   @Test
   void testIsValid() {
      assertTrue(column.isValid());
      column.dispose();
      assertFalse(column.isValid());
   }

   @Test
   void testDispose() {
      column.dispose();
      assertThrows(NullPointerException.class, () -> column.getObject(0));
   }

   @Test
   void testInvalidate() {
      column.invalidate();
      assertFalse(column.isValid());
   }

   @Test
   void testGetInMemoryLength() {
      column.addObject(1.23);
      assertEquals(10, column.getInMemoryLength());
   }

   @Test
   void testCopyToBuffer() {
      column.addObject(1.23);
      column.addObject(4.56);

      ByteBuffer buffer = column.copyToBuffer();

      assertNotNull(buffer);
      assertEquals(16, buffer.limit()); // 2 doubles * 8 bytes each
      assertEquals(1.23, buffer.asDoubleBuffer().get(0));
      assertEquals(4.56, buffer.asDoubleBuffer().get(1));
   }
}