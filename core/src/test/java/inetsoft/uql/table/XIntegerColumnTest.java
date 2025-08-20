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

class XIntegerColumnTest {

   private XIntegerColumn column;

   @BeforeEach
   void setUp() {
      column = new XIntegerColumn((char) 10, (char) 20);
   }

   @Test
   void testGetCreator() {
      assertNotNull(XIntegerColumn.getCreator());
      assertEquals(Integer.class, XIntegerColumn.getCreator().getColType());
   }

   @Test
   void testGetType() {
      assertEquals(XSchema.INTEGER, column.getType());
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
      column.addObject(123);
      column.addObject(Tool.NULL_INTEGER);
      assertEquals(123, column.getObject(0));
      assertNull(column.getObject(1));
   }

   @Test
   void testIsNull() {
      column.addObject(null);
      assertTrue(column.isNull(0));
      column.addObject(123);
      assertFalse(column.isNull(1));
   }

   @Test
   void testGetValue() {
      column.addObject(123);
      column.addObject(0);
      assertEquals(123, column.getInt(0));
      assertEquals((short)123, column.getShort(0));
      assertEquals(123L, column.getLong(0));
      assertEquals((byte)123, column.getByte(0));
      assertEquals(123.0f, column.getFloat(0));
      assertEquals(123.0, column.getDouble(0));
      assertTrue( column.getBoolean(0));
      assertFalse( column.getBoolean(1)); // since 0 is not true
   }

   @Test
   void testDispose() {
      column.dispose();
      assertThrows(NullPointerException.class, () -> column.getObject(0));
   }

   @Test
   void testIsValid() {
      assertTrue(column.isValid());
      column.invalidate();
      assertFalse(column.isValid());
   }

   @Test
   void testCopyToBuffer() {
      column.addObject(123);
      column.addObject(456);

      ByteBuffer buffer = column.copyToBuffer();

      assertNotNull(buffer);
      assertEquals(8, buffer.limit()); // 2 integers * 4 bytes each
      assertEquals(123, buffer.asIntBuffer().get(0));
      assertEquals(456, buffer.asIntBuffer().get(1));
   }
}
