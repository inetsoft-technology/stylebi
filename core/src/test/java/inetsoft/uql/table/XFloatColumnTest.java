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

class XFloatColumnTest {

   private XFloatColumn column;

   @BeforeEach
   void setUp() {
      column = new XFloatColumn((char) 10, (char) 20);
   }

   @Test
   void testGetCreator() {
      assertNotNull(XFloatColumn.getCreator());
      assertEquals(Float.class, XFloatColumn.getCreator().getColType());
   }

   @Test
   void testGetType() {
      assertEquals(XSchema.FLOAT, column.getType());
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
      column.addObject(1.23f);
      column.addObject(Tool.NULL_FLOAT);

      assertEquals(1.23f, column.getFloat(0));
      assertTrue(column.isNull(1));
   }

   @Test
   void testIsNull() {
      column.addObject(null);
      assertTrue(column.isNull(0));
      column.addObject(1.23f);
      assertFalse(column.isNull(1));
   }

   @Test
   void testGetValue() {
      column.addObject(1.23f);
      column.addObject(0.0f);
      assertEquals(1, column.getInt(0));
      assertEquals((short)1, column.getShort(0));
      assertEquals(1L, column.getLong(0));
      assertEquals((byte)1, column.getByte(0));
      assertEquals(1.23f, column.getFloat(0));
      assertEquals(1.23, column.getDouble(0), 0.0001);
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
      column.addObject(1.23f);
      column.addObject(4.56f);

      ByteBuffer buffer = column.copyToBuffer();

      assertNotNull(buffer);
      assertEquals(8, buffer.limit()); // 2 floats * 4 bytes each
      assertEquals(1.23f, buffer.asFloatBuffer().get(0));
      assertEquals(4.56f, buffer.asFloatBuffer().get(1));
   }
}
