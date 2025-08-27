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
import java.sql.Date;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;

class XDateColumnTest {

   private XDateColumn column;
   private final Date testDate1 = new Date(1262304000000L); // 2010-01-01
   private final Date testDate2 = new Date(1293840000000L); // 2011-01-01

   @BeforeEach
   void setUp() {
      column = new XDateColumn((char) 10, (char) 20);
   }

   @Test
   void testStaticProperties() {
      assertNotNull(XDateColumn.getCreator());
      assertEquals(java.sql.Date.class, XDateColumn.getCreator().getColType());
      assertEquals(XSchema.DATE, column.getType());
      assertTrue(column.isPrimitive());
      assertEquals(20, column.capacity());
   }

   @Test
   void testAddGetObject() {
      column.addObject(testDate1);
      column.addObject(testDate2);
      column.addObject(null);

      assertEquals(testDate1, column.getObject(0));
      assertEquals(testDate2, column.getObject(1));
      assertNull(column.getObject(2));
      assertTrue(column.isNull(2));
      assertFalse(column.isNull(0));

      assertThrows(ArrayIndexOutOfBoundsException.class, () -> column.getObject(10));
   }

   @Test
   void testAddObjectWithString() {
      column.addObject("2010-01-01");
      Date result = (Date) column.getObject(0);
      assertNotNull(result);

      Calendar cal = Calendar.getInstance();
      cal.setTime(result);
      assertEquals(2010, cal.get(Calendar.YEAR));
      assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH));
      assertEquals(1, cal.get(Calendar.DAY_OF_MONTH));

      column.addObject("invalid-date");
      assertNull(column.getObject(1));
   }

   @Test
   void testAddObjectWithInvalidString() {
      column.addObject("invalid-date-string");
      assertNull(column.getObject(0));
   }

   @Test
   void testAddObjectWithEmptyString() {
      column.addObject("");
      assertNull(column.getObject(0));
      assertTrue(column.isNull(0));
   }

   @Test
   void testAddObjectWithNull() {
      column.addObject(null);
      assertNull(column.getObject(0));
      assertTrue(column.isNull(0));
   }

   @Test
   void testGetNumericValues() {
      column.addObject(testDate1);
      column.addObject(new Date(0));
      column.addObject(null);

      long expectedTime = testDate1.getTime();

      assertEquals(expectedTime, column.getLong(0));
      assertEquals((int) expectedTime, column.getInt(0));
      assertEquals(expectedTime, column.getDouble(0), 0.001);
      assertEquals(expectedTime, column.getFloat(0), 0.001f);
      assertEquals((short) expectedTime, column.getShort(0));
      assertEquals((byte) expectedTime, column.getByte(0));
      assertTrue(column.getBoolean(0));

      assertEquals(0L, column.getLong(1));
      assertFalse(column.getBoolean(1));

      assertEquals(-1L, column.getLong(2));
      assertTrue(column.getBoolean(2));
   }

   @Test
   void testDispose() {
      column.addObject(testDate1);
      column.dispose();
      assertThrows(NullPointerException.class, () -> column.getObject(0));
   }

   @Test
   void testInvalidate() {
      column.addObject(testDate1);
      assertTrue(column.isValid());
      column.invalidate();
      assertFalse(column.isValid());
   }

   @Test
   void testIsSerializable() {
      assertTrue(column.isSerializable());
   }

   @Test
   void testComplete() {
      column.complete();
   }

   @Test
   void testCopyToBuffer() {
      column.addObject(testDate1);
      column.addObject(testDate2);

      ByteBuffer buffer = column.copyToBuffer();

      assertNotNull(buffer);
      assertEquals(0, buffer.position());
      assertEquals(16, buffer.limit());

      long value1 = buffer.getLong();
      long value2 = buffer.getLong();

      assertEquals(testDate1.getTime(), value1);
      assertEquals(testDate2.getTime(), value2);
   }

   @Test
   void testGetInMemoryLength() {
      column.addObject(testDate1);
      column.addObject(testDate2);
      assertEquals(10, column.getInMemoryLength());
   }
}