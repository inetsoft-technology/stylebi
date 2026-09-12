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

import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.util.script.graal.ScriptScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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

      Object result = xTableArray.getMember("length");
      assertEquals(10, result);
   }

   @Test
   void testGetSizeProperty() {
      when(mockTable.getColCount()).thenReturn(5);

      Object result = xTableArray.getMember("size");
      assertEquals(5, result);
   }

   @Test
   void testGetByIndexWithinBounds() {
      when(mockTable.getRowCount()).thenReturn(10);
      when(mockTable.moreRows(5)).thenReturn(true);

      Object result = xTableArray.getArrayElement(5);
      assertNotNull(result);
   }

   @Test
   void testGetByIndexOutOfBounds() {
      when(mockTable.getRowCount()).thenReturn(10);
      when(mockTable.moreRows(15)).thenReturn(true);

      Object result = xTableArray.getArrayElement(15);
      assertNull(result);
      result = xTableArray.getArrayElement(-1);
      assertNull(result);
   }

   @Test
   void testHasLengthProperty() {
      assertTrue(xTableArray.hasMember("length"));
   }

   @Test
   void testHasSizeProperty() {
      assertTrue(xTableArray.hasMember("size"));
   }

   @Test
   void testHasUndefinedProperty() {
      assertFalse(xTableArray.hasMember("undefinedProperty"));
   }

   @Test
   void testGetArraySize() {
      when(mockTable.getRowCount()).thenReturn(10);
      when(mockTable.moreRows(Integer.MAX_VALUE)).thenReturn(true);

      assertEquals(10, xTableArray.getArraySize());
   }

   @Test
   void testGetAndSetParentScope() {
      ScriptScope mockParentScope = mock(ScriptScope.class);

      xTableArray.setParentScope(mockParentScope);
      assertEquals(mockParentScope, xTableArray.getParentScope());
   }

   @Test
   void testGetMemberKeys() {
      when(mockTable.getRowCount()).thenReturn(3);
      when(mockTable.moreRows(Integer.MAX_VALUE)).thenReturn(true);

      Object[] ids = xTableArray.getMemberKeys();
      assertEquals(5, ids.length); // 3 rows + "length" + "size"
      assertEquals("length", ids[3]);
      assertEquals("size", ids[4]);
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
