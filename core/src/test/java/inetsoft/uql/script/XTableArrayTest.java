/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.script;

import inetsoft.uql.XTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class XTableArrayTest {

   private XTable mockTable;
   private XTableArray xTableArray;

   @BeforeEach
   void setUp() {
      mockTable = mock(XTable.class);
      xTableArray = new XTableArray(mockTable);
   }

   @Test
   void testGetClassName() {
      assertEquals("XTableArray", xTableArray.getClassName());
   }

   @Test
   void testGetLengthProperty() {
      when(mockTable.getRowCount()).thenReturn(10);
      when(mockTable.moreRows(Integer.MAX_VALUE)).thenReturn(true);

      Object result = xTableArray.get("length", null);
      assertEquals(10, result);
   }

   @Test
   void testGetSizeProperty() {
      when(mockTable.getColCount()).thenReturn(5);

      Object result = xTableArray.get("size", null);
      assertEquals(5, result);
   }

   @Test
   void testGetByIndexWithinBounds() {
      when(mockTable.getRowCount()).thenReturn(10);
      when(mockTable.moreRows(5)).thenReturn(true);

      Object result = xTableArray.get(5, null);
      assertNotNull(result);
   }

   @Test
   void testGetByIndexOutOfBounds() {
      when(mockTable.getRowCount()).thenReturn(10);
      when(mockTable.moreRows(15)).thenReturn(true);

      Object result = xTableArray.get(15, null);
      assertEquals(Undefined.instance, result);
      result = xTableArray.get(-1, null);
      assertEquals(Undefined.instance, result);
   }

   @Test
   void testHasLengthProperty() {
      assertTrue(xTableArray.has("length", null));
   }

   @Test
   void testHasSizeProperty() {
      assertTrue(xTableArray.has("size", null));
   }

   @Test
   void testHasUndefinedProperty() {
      assertFalse(xTableArray.has("undefinedProperty", null));
   }

   @Test
   void testHasIndexWithinBounds() {
      when(mockTable.getRowCount()).thenReturn(10);
      when(mockTable.moreRows(5)).thenReturn(true);

      assertTrue(xTableArray.has(5, null));
   }

   @Test
   void testHasIndexOutOfBounds() {
      when(mockTable.getRowCount()).thenReturn(10);
      when(mockTable.moreRows(15)).thenReturn(true);

      assertFalse(xTableArray.has(15, null));
      assertFalse(xTableArray.has(-1, null));
   }

   @Test
   void testGetAndSetPrototype() {
      Scriptable mockPrototype = mock(Scriptable.class);

      xTableArray.setPrototype(mockPrototype);
      assertEquals(mockPrototype, xTableArray.getPrototype());
   }

   @Test
   void testGetAndSetParentScope() {
      Scriptable mockParentScope = mock(Scriptable.class);

      xTableArray.setParentScope(mockParentScope);
      assertEquals(mockParentScope, xTableArray.getParentScope());
   }

   @Test
   void testGetIds() {
      when(mockTable.getRowCount()).thenReturn(3);
      when(mockTable.moreRows(Integer.MAX_VALUE)).thenReturn(true);

      Object[] ids = xTableArray.getIds();
      assertEquals(5, ids.length); // 3 rows + "length" + "size"
      assertEquals("length", ids[3]);
      assertEquals("size", ids[4]);
   }

   @Test
   void testGetDefaultValue() {
      XTable mockTable = mock(XTable.class);
      XTableArray xTableArray = new XTableArray(mockTable);

      Object defaultValue = xTableArray.getDefaultValue(null);
      assertEquals(mockTable, defaultValue);
   }

   @Test
   void testUnwrap() {
      assertEquals(mockTable, xTableArray.unwrap());
   }

   @Test
   void testToString() {
      when(mockTable.toString()).thenReturn("MockTable");
      assertEquals("MockTable", xTableArray.toString());
   }
}