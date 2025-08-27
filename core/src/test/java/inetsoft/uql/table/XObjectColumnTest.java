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

import inetsoft.util.graphics.ImageWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.Serializable;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class XObjectColumnTest {

   XObjectColumn column;
   XTableColumnCreator creator;

   @BeforeEach
   void setUp() {
      creator = mock(XTableColumnCreator.class);
      when(creator.isCache()).thenReturn(true);
      when(creator.isDynamic()).thenReturn(false);
      column = new XObjectColumn(creator, (char) 5, (char) 10);
   }

   @Test
   void testGetCreator() {
      assertNotNull(XObjectColumn.getCreator());
      assertNotNull(XObjectColumn.getCreator((Class<?>) null));
      assertNotNull(XObjectColumn.getCreator((String) null));
      assertNotNull(XObjectColumn.getCreator(String.class));
      assertNotNull(XObjectColumn.getCreator("string"));
      assertNotNull(XObjectColumn.getCreator(UnknownClass.class));
      assertNotNull(XObjectColumn.getCreator("unknown_type"));
   }

   @Test
   void testConstructors() {
      XObjectColumn c1 = new XObjectColumn((char) 2, (char) 3);
      assertNotNull(c1);
      XObjectColumn c2 = new XObjectColumn(creator, (char) 2, (char) 3);
      assertNotNull(c2);
   }

   @Test
   void testGetCreator0() {
      assertNotNull(column.getCreator0());
   }

   @Test
   void testGetType() {
      assertEquals("unknown", column.getType());
   }

   @Test
   void testIsPrimitive() {
      assertFalse(column.isPrimitive());
   }

   @Test
   void testCapacity() {
      assertEquals(10, column.capacity());
   }

   @Test
   void testAddObjectAndEnsureCapacity() {
      for (int i = 0; i < 7; i++) {
         column.addObject("obj" + i);
      }
      assertEquals("obj0", column.getObject(0));
      assertEquals("obj6", column.getObject(6));
   }

   @Test
   void testAddObjectWithNull() {
      column.addObject(null);
      assertNull(column.getObject(0));
   }

   @Test
   void testAddObjectWithSerializable() {
      Serializable obj = "serial";
      column.addObject(obj);
      assertEquals(obj, column.getObject(0));
   }

   @Test
   void testAddObjectWithImage() {
      Image img = mock(Image.class);
      column.addObject(img);
      assertTrue(column.isImage());
   }

   @Test
   void testCacheBehavior() {
      Object obj1 = "cache1";
      Object obj2 = "cache1";
      column.addObject(obj1);
      column.addObject(obj2);
      assertSame(column.getObject(0), column.getObject(1));
      assertNotNull(column.getCache(obj1));
   }

   @Test
   void testDynamicTypeConversion() {
      when(creator.isDynamic()).thenReturn(true);
      XTableColumn result = column.addObject("test");
      assertInstanceOf(XStringColumn.class, result);
      assertEquals("test", result.getObject(0));
   }

   @Test
   void testGetPreferredColumnCoverage() {
      assertNotNull(XObjectColumn.getCreator(String.class));
      assertNotNull(XObjectColumn.getCreator(Boolean.class));
      assertNotNull(XObjectColumn.getCreator(Float.class));
      assertNotNull(XObjectColumn.getCreator(Double.class));
      assertNotNull(XObjectColumn.getCreator(Byte.class));
      assertNotNull(XObjectColumn.getCreator(Short.class));
      assertNotNull(XObjectColumn.getCreator(Integer.class));
      assertNotNull(XObjectColumn.getCreator(Long.class));
      assertNotNull(XObjectColumn.getCreator(java.sql.Date.class));
      assertNotNull(XObjectColumn.getCreator(java.sql.Time.class));
      assertNotNull(XObjectColumn.getCreator(java.sql.Timestamp.class));
      assertNotNull(XObjectColumn.getCreator(java.util.Date.class));
      assertNotNull(XObjectColumn.getCreator(java.math.BigDecimal.class));
      assertNotNull(XObjectColumn.getCreator(java.math.BigInteger.class));
   }

   @Test
   void testGetObject() {
      column.addObject("abc");
      assertEquals("abc", column.getObject(0));
   }

   @Test
   void testGetObjectOutOfBounds() {
      assertThrows(ArrayIndexOutOfBoundsException.class, () -> column.getObject(100));
   }

   @Test
   void testGetNumericValuesReturnZero() {
      column.addObject("abc");
      assertEquals(0, column.getDouble(0));
      assertEquals(0, column.getFloat(0));
      assertEquals(0, column.getLong(0));
      assertEquals(0, column.getInt(0));
      assertEquals(0, column.getShort(0));
      assertEquals(0, column.getByte(0));
      assertFalse(column.getBoolean(0));
   }

   @Test
   void testIsNull() {
      column.addObject(null);
      assertTrue(column.isNull(0));
      column.addObject("notnull");
      assertFalse(column.isNull(1));
   }

   @Test
   void testIsValid() {
      assertTrue(column.isValid());
      column.dispose();
      assertFalse(column.isValid());
   }

   @Test
   void testIsSerializable() {
      assertTrue(column.isSerializable());
      Object nonSerializable = new Object();
      column.addObject(nonSerializable);
      assertFalse(column.isSerializable());
      assertNull(column.copyToBuffer());
   }

   @Test
   void testDisposeAndInvalidate() {
      column.addObject("test");
      column.dispose();
      assertFalse(column.isValid());
      assertThrows(NullPointerException.class, () -> column.getObject(0));

      column = new XObjectColumn(creator, (char) 5, (char) 10);
      column.addObject("test");
      column.invalidate();
      assertThrows(NullPointerException.class, () -> column.getObject(0));
   }

   @Test
   void testCacheDisabled() {
      when(creator.isCache()).thenReturn(false);
      XObjectColumn noCacheCol = new XObjectColumn(creator, (char) 5, (char) 10);
      assertFalse(noCacheCol.hasCache());
      noCacheCol.addObject("test");
   }

   @Test
   void testCompleteAndRemoveObjectPool() {
      column.addObject("test");
      column.complete();
      assertFalse(column.hasCache());
      column.removeObjectPool();
      assertFalse(column.hasCache());
   }

   @Test
   void testCopyFromBuffer() {
      column.addObject("abc");
      column.addObject("def");

      ByteBuffer buffer = column.copyToBuffer();
      assertNotNull(buffer);
      assertEquals(0, buffer.position());

      XObjectColumn newColumn = new XObjectColumn(creator, (char) 2, (char) 5);
      newColumn.addObject("placeholder1");
      newColumn.addObject("placeholder2");

      newColumn.copyFromBuffer(buffer);

      assertEquals("abc", newColumn.getObject(0));
      assertEquals("def", newColumn.getObject(1));
   }

   @Test
   void testGetInMemoryLength() {
      assertEquals(5, column.getInMemoryLength());
      column.addObject("abc");
      assertEquals(5, column.getInMemoryLength());
   }

   @Test
   void testGetHeaderLength() {
      assertEquals(8, column.getHeaderLength());
   }

   @Test
   void testIsImage() {
      assertFalse(column.isImage());
      Image img = mock(Image.class);
      column.addObject(img);
      assertTrue(column.isImage());
   }

   @Test
   void testGetCacheAndHasCache() {
      assertNull(column.getCache(null));
      assertTrue(column.hasCache());
   }

   static class UnknownClass {}
}