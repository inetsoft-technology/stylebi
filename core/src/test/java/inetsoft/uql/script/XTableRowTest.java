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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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
      assertEquals("value1", xTableRow.getMember("column1"));
      assertEquals("value2", xTableRow.getMember("column2"));
      assertNull(xTableRow.getMember("nonexistent"));
      assertEquals(2, xTableRow.getMember("length"));
   }

   @Test
   void testGetIndexedProperty() {
      assertEquals("value1", xTableRow.getArrayElement(0));
      assertEquals("value2", xTableRow.getArrayElement(1));
      assertNull(xTableRow.getArrayElement(2));
      assertNull(xTableRow.getArrayElement(-1));
   }

   @Test
   void testGetArraySize() {
      assertEquals(2, xTableRow.getArraySize());
   }

   @Test
   void testHasNamedProperty() {
      assertTrue(xTableRow.hasMember("column1"));
      assertTrue(xTableRow.hasMember("column2"));
      assertTrue(xTableRow.hasMember("length"));
      assertFalse(xTableRow.hasMember("nonexistent"));
   }

   @Test
   void testPutNamedProperty() {
      xTableRow.putMember("column1", "newValue");
      assertEquals("value1", xTableRow.getMember("column1")); // No change expected
   }

   @Test
   void testRemoveNamedProperty() {
      assertFalse(xTableRow.removeMember("column1"));
      assertTrue(xTableRow.hasMember("column1")); // No deletion expected
   }

   @Test
   void testGetAndSetParentScope() {
      ScriptScope parent = mock(ScriptScope.class);

      xTableRow.setParentScope(parent);
      assertEquals(parent, xTableRow.getParentScope());
   }

   @Test
   void testGetMemberKeys() {
      Object[] ids = xTableRow.getMemberKeys();
      // indices 0,1 first, then the map keys (HashMap iteration order), then "length"
      assertEquals(5, ids.length);
      assertEquals(0, ids[0]);
      assertEquals(1, ids[1]);
      assertEquals("length", ids[4]);
      List<Object> middle = Arrays.asList(ids[2], ids[3]);
      assertTrue(middle.containsAll(Arrays.asList("column1", "column2")));
   }
}
