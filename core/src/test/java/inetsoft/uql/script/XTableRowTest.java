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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class XTableRowTest {

   private XTable mockTable;
   private Map<String, Integer> mockMap;
   private XTableRow xTableRow;

   @BeforeEach
   void setUp() {
      mockTable = mock(XTable.class);
      mockMap = new HashMap<>();
      mockMap.put("column1", 0);
      mockMap.put("column2", 1);

      when(mockTable.getColCount()).thenReturn(2);
      when(mockTable.getObject(0, 0)).thenReturn("value1");
      when(mockTable.getObject(0, 1)).thenReturn("value2");

      xTableRow = new XTableRow(mockTable, 0, mockMap);
   }

   @Test
   void testGetClassName() {
      assertEquals("XTableRow", xTableRow.getClassName());
   }

   @Test
   void testGetNamedProperty() {
      assertEquals("value1", xTableRow.get("column1", xTableRow));
      assertEquals("value2", xTableRow.get("column2", xTableRow));
      assertEquals(Undefined.instance, xTableRow.get("nonexistent", xTableRow));
      assertEquals(2, xTableRow.get("length", xTableRow));
   }

   @Test
   void testGetIndexedProperty() {
      assertEquals("value1", xTableRow.get(0, xTableRow));
      assertEquals("value2", xTableRow.get(1, xTableRow));
      assertEquals(Undefined.instance, xTableRow.get(2, xTableRow));
      assertEquals(Undefined.instance, xTableRow.get(-1, xTableRow));
   }

   @Test
   void testHasNamedProperty() {
      assertTrue(xTableRow.has("column1", xTableRow));
      assertTrue(xTableRow.has("column2", xTableRow));
      assertTrue(xTableRow.has("length", xTableRow));
      assertFalse(xTableRow.has("nonexistent", xTableRow));
   }

   @Test
   void testHasIndexedProperty() {
      assertTrue(xTableRow.has(0, xTableRow));
      assertTrue(xTableRow.has(1, xTableRow));
      assertFalse(xTableRow.has(2, xTableRow));
      assertFalse(xTableRow.has(-1, xTableRow));
   }

   @Test
   void testPutNamedProperty() {
      xTableRow.put("column1", xTableRow, "newValue");
      assertEquals("value1", xTableRow.get("column1", xTableRow)); // No change expected
   }

   @Test
   void testPutIndexedProperty() {
      xTableRow.put(0, xTableRow, "newValue");
      assertEquals("value1", xTableRow.get(0, xTableRow)); // No change expected
   }

   @Test
   void testDeleteNamedProperty() {
      xTableRow.delete("column1");
      assertTrue(xTableRow.has("column1", xTableRow)); // No deletion expected
   }

   @Test
   void testDeleteIndexedProperty() {
      xTableRow.delete(0);
      assertTrue(xTableRow.has(0, xTableRow)); // No deletion expected
   }

   @Test
   void testGetPrototypeAndParentScope() {
      Scriptable prototype = mock(Scriptable.class);
      Scriptable parent = mock(Scriptable.class);

      xTableRow.setPrototype(prototype);
      xTableRow.setParentScope(parent);

      assertEquals(prototype, xTableRow.getPrototype());
      assertEquals(parent, xTableRow.getParentScope());
   }

   @Test
   void testGetIds() {
      Object[] ids = xTableRow.getIds();
      assertArrayEquals(new Object[]{ 0, 1, "column1", "column2", "length" }, ids);
   }

   @Test
   void testGetDefaultValue() {
      assertEquals(xTableRow, xTableRow.getDefaultValue(null));
   }

   @Test
   void testHasInstance() {
      assertFalse(xTableRow.hasInstance(mock(Scriptable.class)));
   }
}