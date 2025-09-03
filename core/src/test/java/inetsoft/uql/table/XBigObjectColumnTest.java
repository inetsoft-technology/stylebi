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

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.*;

class XBigObjectColumnTest {

   private XBigObjectColumn column;

   @BeforeEach
   void setUp() {
      column = new XBigObjectColumn((char) 10, (char) 20);
   }

   @Test
   void testGetCreator() {
      assertNotNull(XBigObjectColumn.getCreator());
      assertNull(XBigObjectColumn.getCreator().getColType());

      XTableColumn createdColumn = XBigObjectColumn.getCreator().createColumn((char) 5, (char) 10);
      assertTrue(createdColumn instanceof XBigObjectColumn);
   }

   @Test
   void testGetType() {
      assertEquals(XSchema.UNKNOWN, column.getType());
   }

   @Test
   void testIsPrimitive() {
      assertFalse(column.isPrimitive());
   }

   @Test
   void testIsSerializable() {
      assertFalse(column.isSerializable());
   }

   @Test
   void testCapacity() {
      assertEquals(20, column.capacity());
   }

   @Test
   void testIsValid() {
      assertTrue(column.isValid());
   }

   @Test
   void testLength() {
      assertEquals(0, column.length());

      column.addObject("test1");
      column.addObject("test2");

      assertEquals(2, column.length());
   }
   
   @Test
   void testAddAndGetObject() {
      String testString = "Test String";
      Integer testInteger = 123;
      Double testDouble = 45.67;
      Boolean testBoolean = true;
      Object testObject = new Object();

      column.addObject(testString);
      column.addObject(null);
      column.addObject(testInteger);
      column.addObject(testDouble);
      column.addObject(testBoolean);
      column.addObject(testObject);

      assertEquals(testString, column.getObject(0));
      assertNull(column.getObject(1));
      assertEquals(testInteger, column.getObject(2));
      assertEquals(testDouble, column.getObject(3));
      assertEquals(testBoolean, column.getObject(4));
      assertEquals(testObject, column.getObject(5));
   }

   @Test
   void testIsNull() {
      column.addObject(null);
      column.addObject("test object");

      assertTrue(column.isNull(0));
      assertFalse(column.isNull(1));
   }

   @Test
   void testEnsureCapacity() {
      XBigObjectColumn smallColumn = new XBigObjectColumn((char)2, (char)5);

      for (int i = 0; i < 5; i++) {
         smallColumn.addObject("object-" + i);
      }

      assertEquals(5, smallColumn.capacity());
      assertNotNull(smallColumn.getObject(4));
      assertNull(smallColumn.getObject(5));
   }


   @Test
   void testMemoryManagement() {
      assertEquals(0, column.getCacheCount());
      int initialMemoryLength = column.getInMemoryLength();
      assertTrue(initialMemoryLength >= 0);

      column.addObject("test1");
      column.addObject("test2");
      assertTrue(column.getInMemoryLength() >= initialMemoryLength);

      column.getObject(0);
      column.getObject(1);
      assertTrue(column.getCacheCount() > 0);

      for (int i = 0; i < 10; i++) {
         column.addObject("obj-" + i);
         column.getObject(i);
      }
      assertTrue(column.getCacheCount() <= 128);

      assertEquals("test1", column.getObject(0));
      assertEquals("test2", column.getObject(1));
   }

   @Test
   void testDispose() {
      column.addObject("test object");
      column.complete();

      assertTrue(column.isValid());
      assertTrue(column.getInMemoryLength() > 0);
      assertTrue(column.length() > 0);

      column.dispose();
      assertTrue(column.isValid());
      assertEquals(0, column.getInMemoryLength());
   }

   @Test
   void testNegativeIndexThrowsException() {
      assertThrows(IndexOutOfBoundsException.class, () -> column.getObject(-1));
   }

   @Test
   void testEmpty() {
      assertEquals(0, column.length());
      assertNull(column.getObject(0));
      assertTrue(column.isNull(0));
   }
}