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

class XBILongColumnTest {

   private XBILongColumn column;

   @BeforeEach
   void setUp() {
      column = new XBILongColumn((char) 10, (char) 20);
   }

   @Test
   void testGetCreator() {
      assertNotNull(XBILongColumn.getCreator());
      assertEquals(Long.class, XBILongColumn.getCreator().getColType());

      XTableColumn createdColumn = XBILongColumn.getCreator().createColumn((char)5, (char)10);
      assertTrue(createdColumn instanceof XBILongColumn);
   }

   @Test
   void testGetType() {
      assertEquals(XSchema.LONG, column.getType());
   }

   @Test
   void testIsPrimitive() {
      assertTrue(column.isPrimitive());
   }

   @Test
   void testSerializable() {
      assertTrue(column.isSerializable());
   }

   @Test
   void testCapacity() {
      assertEquals(20, column.capacity());
   }

   @Test
   void testAddAndGetObject() {
      column.addObject(123L);
      assertEquals(123L, column.getObject(0));

      column.addObject(null);
      assertNull(column.getObject(1));

      column.addObject(42);
      column.addObject(100L);
      column.addObject((short)50);
      column.addObject((byte)10);

      assertEquals(42L, column.getObject(2));
      assertEquals(100L, column.getObject(3));
      assertEquals(50L, column.getObject(4));
      assertEquals(10L, column.getObject(5));
   }

   @Test
   void testIsNull() {
      column.addObject(null);
      column.addObject(123L);

      assertTrue(column.isNull(0));
      assertFalse(column.isNull(1));
   }

   @Test
   void testGeValue() {
      column.addObject(123L);
      column.addObject(0L);

      assertEquals(123, column.getInt(0));
      assertEquals((short)123, column.getShort(0));
      assertEquals(123L, column.getLong(0));
      assertEquals((byte)123, column.getByte(0));
      assertEquals(123.0f, column.getFloat(0), 0.001f);
      assertEquals(123.0, column.getDouble(0), 0.001);
      assertTrue(column.getBoolean(0));
      assertFalse(column.getBoolean(1));
   }

   @Test
   void testArrayBounds() {
      column.addObject(123L);

      assertDoesNotThrow(() -> column.getObject(0));
      assertThrows(ArrayIndexOutOfBoundsException.class, () -> column.getObject(10));
      assertThrows(ArrayIndexOutOfBoundsException.class, () -> column.getObject(-1));
   }

   @Test
   void testLifecycle() {
      assertTrue(column.isValid());

      column.addObject(123L);

      column.invalidate();
      assertFalse(column.isValid());

      column.dispose();
      assertThrows(NullPointerException.class, () -> column.getObject(0));
   }

   @Test
   void testArrayExpansion() {
      for (int i = 0; i < 15; i++) {
         column.addObject((long)i);
      }

      assertEquals(14L, column.getLong(14));
      assertTrue(column.capacity() >= 15);
   }

   @Test
   void testCopyToBuffer() {
      column.addObject(123L);
      column.addObject(456L);

      ByteBuffer buffer = column.copyToBuffer();

      assertNotNull(buffer);
      assertEquals(16, buffer.limit());
      assertEquals(123L, buffer.asLongBuffer().get(0));
      assertEquals(456L, buffer.asLongBuffer().get(1));
   }

   @Test
   void testGetInMemoryLength() {
      assertEquals(10, column.getInMemoryLength());

      column.addObject(123L);
      assertEquals(10, column.getInMemoryLength());
   }
}