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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class XBooleanColumnTest {

   private XBooleanColumn column;

   @BeforeEach
   void setUp() {
      column = new XBooleanColumn((char) 10, (char) 20);
   }

   @Test
   void testGetCreator() {
      assertNotNull(XBooleanColumn.getCreator());
      assertEquals(Boolean.class, XBooleanColumn.getCreator().getColType());
   }

   @Test
   void testGetType() {
      assertEquals(XSchema.BOOLEAN, column.getType());
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
      column.addObject(true);
      column.addObject(false);
      column.addObject(null);

      assertEquals(true, column.getObject(0));
      assertEquals(false, column.getObject(1));
      assertNull(column.getObject(2));
      assertEquals((byte) 0, column.getByte(2));
      assertThrows(ArrayIndexOutOfBoundsException.class, () -> column.getObject(10));
   }

   @Test
   void testIsNull() {
      column.addObject(null);
      assertTrue(column.isNull(0));
      column.addObject(true);
      assertFalse(column.isNull(1));
   }

   @Test
   void testGetBoolean() {
      column.addObject(true);
      column.addObject(false);

      assertTrue(column.getBoolean(0));
      assertFalse(column.getBoolean(1));
   }

   @Test
   void testGetNumericValues() {
      column.addObject(true);
      column.addObject(false);

      assertEquals(1, column.getInt(0));
      assertEquals(0, column.getInt(1));
      assertEquals(1L, column.getLong(0));
      assertEquals(0L, column.getLong(1));
      assertEquals(1.0, column.getDouble(0));
      assertEquals(0.0, column.getDouble(1));
      assertEquals(1.0f, column.getFloat(0));
      assertEquals(0.0f, column.getFloat(1));
      assertEquals((short) 1, column.getShort(0));
      assertEquals((short) 0, column.getShort(1));
      assertEquals((byte) 1, column.getByte(0));
      assertEquals((byte) 0, column.getByte(1));
   }

   @Test
   void testDispose() {
      column.addObject(true);
      column.dispose();
      assertThrows(NullPointerException.class, () -> column.getObject(0));
   }

   @Test
   void testInvalidate() {
      column.addObject(true);
      assertTrue(column.isValid());
      column.invalidate();
      assertFalse(column.isValid());
   }

   @Test
   void testCopyToBuffer() {
      column.addObject(true);
      column.addObject(false);

      ByteBuffer buffer = column.copyToBuffer();

      assertNotNull(buffer);
      assertEquals(0, buffer.position(), "Buffer should be flipped and ready for reading");
      assertTrue(buffer.limit() > 0, "Buffer should have data written to it");

      byte value1 = buffer.get();
      byte value2 = buffer.get();

      assertEquals(1, value1);
      assertEquals(0, value2);
   }

   @Test
   void testGetInMemoryLength() {
      column.addObject(true);
      column.addObject(false);
      assertEquals(10, column.getInMemoryLength());
   }
}
