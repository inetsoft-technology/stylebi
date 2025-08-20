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
import inetsoft.util.swap.XSwapUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class XStringColumnTest {

   private XStringColumn column;

   @BeforeEach
   void setUp() {
      column = new XStringColumn((char) 10, (char) 20);
   }

   @Test
   void testGetCreator() {
      assertNotNull(XStringColumn.getCreator());
      assertEquals(String.class, XStringColumn.getCreator().getColType());
   }

   @Test
   void testGetType() {
      assertEquals(XSchema.STRING, column.getType());
   }

   @Test
   void testIsPrimitive() {
      assertFalse(column.isPrimitive());
   }

   @Test
   void testCapacity() {
      assertEquals(20, column.capacity());
   }

   @Test
   void testAddGetObject() {
      column.addObject("test1");
      column.addObject(null);
      column.addObject("");

      assertEquals("test1", column.getObject(0));
      assertTrue(column.isNull(1));
      assertEquals("", column.getObject(2));
      assertThrows(ArrayIndexOutOfBoundsException .class, () -> column.getObject(11));
   }

   @Test
   void testGetValue() {
      column.addObject("test1");
      assertEquals(0, column.getByte(0));
      assertEquals(0, column.getDouble(0));
      assertEquals(0, column.getFloat(0));
      assertEquals(0, column.getInt(0));
      assertEquals(0, column.getLong(0));
      assertEquals(0, column.getShort(0));
      assertFalse(column.getBoolean(0));
   }

   @Test
   void testIsNull() {
      column.addObject(null);
      assertTrue(column.isNull(0));
      column.addObject("a");
      assertFalse(column.isNull(1));
   }

   @Test
   void testDispose() {
      column.dispose();
      assertThrows(NullPointerException.class, () -> column.getObject(0));
   }

   @Test
   void testInvalidate() {
      assertTrue(column.isValid());
      column.invalidate();
      assertFalse(column.isValid());
   }

   @Test
   void testGetInMemoryLength() {
      column.addObject("test1");
      assertEquals(10, column.getInMemoryLength());
   }

   @Test
   void testCopyToBuffer() {
      column.addObject("test1");
      column.addObject("test2");

      ByteBuffer buffer = column.copyToBuffer();

      assertNotNull(buffer);
      assertEquals(0, buffer.position(), "Buffer should be flipped and ready for reading");
      assertTrue(buffer.limit() > 0, "Buffer should have data written to it");

      String value1 = XSwapUtil.readString(buffer);
      String value2 = XSwapUtil.readString(buffer);

      assertEquals("test1", value1);
      assertEquals("test2", value2);
   }
}
