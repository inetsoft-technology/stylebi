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
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class XTimestampColumnTest {

   private XTimestampColumn column;

   @BeforeEach
   void setUp() {
      column = new XTimestampColumn((char) 10, (char) 20);
   }

   @Test
   void testGetCreator() {
      assertNotNull(XTimestampColumn.getCreator());
      assertEquals(java.sql.Timestamp.class, XTimestampColumn.getCreator().getColType());

      XTableColumn createdColumn = XTimestampColumn.getCreator().createColumn((char) 5, (char) 10);
      assertTrue(createdColumn instanceof XTimestampColumn);
   }

   @Test
   void testGetType() {
      assertEquals(XSchema.TIME_INSTANT, column.getType());
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
      Timestamp timestamp1 = new Timestamp(System.currentTimeMillis());
      Timestamp timestamp2 = new Timestamp(System.currentTimeMillis() + 1000);

      column.addObject(timestamp1);
      column.addObject(null);
      column.addObject(timestamp2);

      assertEquals(timestamp1, column.getObject(0));
      assertNull(column.getObject(1));
      assertTrue(column.isNull(1));
      assertFalse(column.isNull(0));
   }

   @Test
   void testStringParsing() {
      String isoFormat = "2024-01-15T10:30:45.123Z";
      String jodaFormat = "2024-01-15T10:30:45";
      String customFormat = "15-01-2024";

      column.addObject(isoFormat);
      assertNotNull(column.getObject(0));
      assertInstanceOf(Timestamp.class, column.getObject(0));

      column.addObject(jodaFormat);
      assertNotNull(column.getObject(1));
      assertInstanceOf(Timestamp.class, column.getObject(1));

      column.addObject(customFormat);
      assertNotNull(column.getObject(2));
      assertInstanceOf(Timestamp.class, column.getObject(2));
   }

   @Test
   void testInvalidStrings() {
      String[] invalidStrings = {
         "invalid-timestamp-format",
         "",
         null
      };

      for (String invalidString : invalidStrings) {
         XTimestampColumn testColumn = new XTimestampColumn((char) 5, (char) 10);
         testColumn.addObject(invalidString);
         assertNull(testColumn.getObject(0), "Should handle: " + invalidString);
      }
   }

   @Test
   void testNumericalConversions() {
      Timestamp timestamp = new Timestamp(123456789L);
      column.addObject(timestamp);

      assertEquals(123456789.0, column.getDouble(0), 0.001);
      assertEquals(123456789.0f, column.getFloat(0), 0.001f);
      assertEquals(123456789L, column.getLong(0));
      assertEquals(123456789, column.getInt(0));
      assertEquals((short)123456789, column.getShort(0));
      assertEquals((byte)123456789, column.getByte(0));
      assertTrue(column.getBoolean(0));
   }

   @Test
   void testArrayBounds() {
      column.addObject(new Timestamp(System.currentTimeMillis()));
      assertDoesNotThrow(() -> column.getObject(0));
      assertThrows(ArrayIndexOutOfBoundsException.class, () -> column.getObject(10));
   }

   @Test
   void testBoundaryValues() {
      column.addObject(new Timestamp(Long.MIN_VALUE));
      column.addObject(new Timestamp(Long.MAX_VALUE));
      column.addObject(new Timestamp(0));

      assertEquals(Long.MIN_VALUE, column.getLong(0));
      assertEquals(Long.MAX_VALUE, column.getLong(1));
      assertEquals(0, column.getLong(2));
   }

   @Test
   void testLifecycle() {
      assertTrue(column.isValid());

      column.addObject(new Timestamp(System.currentTimeMillis()));
      column.invalidate();
      assertFalse(column.isValid());

      column.dispose();
      assertFalse(column.isValid());
   }

   @Test
   void testArrayExpansion() {
      for (int i = 0; i < 15; i++) {
         column.addObject(new Timestamp(System.currentTimeMillis() + i));
      }
      assertTrue(column.getInMemoryLength() >= 15);
      assertDoesNotThrow(() -> column.getObject(14));
      assertThrows(ArrayIndexOutOfBoundsException.class, () -> column.getObject(20));
   }

   @Test
   void testCopyToAndFromBuffer() {
      Timestamp timestamp1 = new Timestamp(System.currentTimeMillis());
      Timestamp timestamp2 = new Timestamp(System.currentTimeMillis() + 1000);

      column.addObject(timestamp1);
      column.addObject(timestamp2);

      ByteBuffer buffer = column.copyToBuffer();
      assertNotNull(buffer);

      XTimestampColumn newColumn = new XTimestampColumn((char) 2, (char) 5);
      newColumn.pos = 2;
      newColumn.copyFromBuffer(buffer);

      assertEquals(timestamp1, newColumn.getObject(0));
      assertEquals(timestamp2, newColumn.getObject(1));
   }

   @Test
   void testGetInMemoryLength() {
      int initialLength = column.getInMemoryLength();
      column.addObject(new Timestamp(System.currentTimeMillis()));
      assertEquals(initialLength, column.getInMemoryLength());
   }
}