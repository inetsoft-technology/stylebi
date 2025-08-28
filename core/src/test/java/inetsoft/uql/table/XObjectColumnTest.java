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
   void testGetType() {
      assertEquals("unknown", column.getType());
   }

   @Test
   void testIsPrimitive() {
      assertFalse(column.isPrimitive());
   }

   @Test
   void testEnsureCapacity() {
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
   void testAddObjectSerializable() {
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
   void testTypeMapping() {
      assertInstanceOf(XStringColumn.getCreator().getClass(), XObjectColumn.getCreator(String.class));
      assertInstanceOf(XBooleanColumn.getCreator().getClass(), XObjectColumn.getCreator(Boolean.class));
      assertInstanceOf(XFloatColumn.getCreator().getClass(), XObjectColumn.getCreator(Float.class));
      assertInstanceOf(XDoubleColumn.getCreator().getClass(), XObjectColumn.getCreator(Double.class));
      assertInstanceOf(XByteColumn.getCreator().getClass(), XObjectColumn.getCreator(Byte.class));
      assertInstanceOf(XShortColumn.getCreator().getClass(), XObjectColumn.getCreator(Short.class));
      assertInstanceOf(XIntegerColumn.getCreator().getClass(), XObjectColumn.getCreator(Integer.class));
      assertInstanceOf(XLongColumn.getCreator().getClass(), XObjectColumn.getCreator(Long.class));
      assertInstanceOf(XDateColumn.getCreator().getClass(), XObjectColumn.getCreator(java.sql.Date.class));
      assertInstanceOf(XTimeColumn.getCreator().getClass(), XObjectColumn.getCreator(java.sql.Time.class));
      assertInstanceOf(XTimestampColumn.getCreator().getClass(), XObjectColumn.getCreator(java.sql.Timestamp.class));
      assertInstanceOf(XTimestampColumn.getCreator().getClass(), XObjectColumn.getCreator(java.util.Date.class));

      XTableColumnCreator bigDecimalCreator = XObjectColumn.getCreator(java.math.BigDecimal.class);
      assertTrue(
         bigDecimalCreator.getClass() == XBDDoubleColumn.getCreator().getClass()
            || bigDecimalCreator.getClass() == XObjectColumn.getCreator().getClass()
      );

      assertInstanceOf(XBILongColumn.getCreator().getClass(), XObjectColumn.getCreator(java.math.BigInteger.class));

      assertInstanceOf(XStringColumn.getCreator().getClass(), XObjectColumn.getCreator("string"));
      assertInstanceOf(XBooleanColumn.getCreator().getClass(), XObjectColumn.getCreator("boolean"));
      assertInstanceOf(XFloatColumn.getCreator().getClass(), XObjectColumn.getCreator("float"));
      assertInstanceOf(XDoubleColumn.getCreator().getClass(), XObjectColumn.getCreator("double"));
      assertInstanceOf(XDoubleColumn.getCreator().getClass(), XObjectColumn.getCreator("decimal"));
      assertInstanceOf(XByteColumn.getCreator().getClass(), XObjectColumn.getCreator("byte"));
      assertInstanceOf(XShortColumn.getCreator().getClass(), XObjectColumn.getCreator("short"));
      assertInstanceOf(XIntegerColumn.getCreator().getClass(), XObjectColumn.getCreator("integer"));
      assertInstanceOf(XLongColumn.getCreator().getClass(), XObjectColumn.getCreator("long"));
      assertInstanceOf(XDateColumn.getCreator().getClass(), XObjectColumn.getCreator("date"));
      assertInstanceOf(XTimeColumn.getCreator().getClass(), XObjectColumn.getCreator("time"));
      assertNotNull(XObjectColumn.getCreator("time_instant"));

      assertNotNull(XObjectColumn.getCreator((String) null));
      assertNotNull(XObjectColumn.getCreator("unknown_type"));
   }

   @Test
   void testGetObjectOutOfBounds() {
      assertThrows(ArrayIndexOutOfBoundsException.class, () -> column.getObject(100));
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
   void testDispose() {
      column.addObject("test");
      column.dispose();
      assertFalse(column.isValid());
      assertThrows(NullPointerException.class, () -> column.getObject(0));
   }

   @Test
   void testInvalidate() {
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
   void testCompleteClearsCache() {
      column.addObject("test");
      assertTrue(column.hasCache());
      column.complete();
      assertFalse(column.hasCache());
   }

   @Test
   void testRemoveObjectPoolClearsCache() {
      column.addObject("test");
      assertTrue(column.hasCache());
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
   void testIsImage() {
      assertFalse(column.isImage());
      Image img = mock(Image.class);
      column.addObject(img);
      assertTrue(column.isImage());
   }
}