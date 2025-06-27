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

import inetsoft.uql.VariableTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VariableScriptableTest {

   private VariableTable mockVariableTable;
   private VariableScriptable variableScriptable;

   @BeforeEach
   void setUp() {
      mockVariableTable = mock(VariableTable.class);
      variableScriptable = new VariableScriptable(mockVariableTable);
   }

   @Test
   void testGetClassName() {
      assertEquals("Variable", variableScriptable.getClassName());
   }

   @Test
   void testGetNamedPropertyExists() throws Exception {
      when(mockVariableTable.get("testKey")).thenReturn("testValue");
      Object result = variableScriptable.get("testKey", variableScriptable);
      assertEquals("testValue", result);
   }

   @Test
   void testGetNamedPropertyDoesNotExist() throws Exception {
      when(mockVariableTable.get("nonExistentKey")).thenReturn(null);
      Object result = variableScriptable.get("nonExistentKey", variableScriptable);
      assertNull(result);
   }

   @Test
   void testGetNamedPropertySpecialKeys() {
      when(mockVariableTable.size()).thenReturn(5);
      Object lengthResult = variableScriptable.get("length", variableScriptable);
      assertEquals(5, lengthResult);

      Enumeration<String> mockKeys = mock(Enumeration.class);
      when(mockKeys.hasMoreElements()).thenReturn(true, false);
      when(mockKeys.nextElement()).thenReturn("param1");
      when(mockVariableTable.keys()).thenReturn(mockKeys);

      Object parameterNamesResult = variableScriptable.get("parameterNames", variableScriptable);
      assertArrayEquals(new String[]{ "param1" }, (String[]) parameterNamesResult);
   }

   @Test
   void testGetIndexedProperty() {
      Object result = variableScriptable.get(0, variableScriptable);
      assertEquals(Undefined.instance, result);
   }

   @Test
   void testHasNamedPropertyExists() throws Exception {
      when(mockVariableTable.get("testKey")).thenReturn("testValue");
      assertTrue(variableScriptable.has("testKey", variableScriptable));
   }

   @Test
   void testHasNamedPropertyNotExists() throws Exception {
      when(mockVariableTable.get("invalidKey")).thenReturn(null);
      assertFalse(variableScriptable.has("invalidKey", variableScriptable));
   }

   @Test
   void testHasNamedPropertySpecialKeys() {
      assertTrue(variableScriptable.has("length", variableScriptable));
      assertTrue(variableScriptable.has("parameterNames", variableScriptable));
   }

   @Test
   void testHasIndexedProperty() {
      assertFalse(variableScriptable.has(0, variableScriptable));
   }

   @Test
   void testPutNamedProperty() {
      variableScriptable.put("testKey", variableScriptable, "testValue");
      verify(mockVariableTable).put("testKey", "testValue");
      verify(mockVariableTable).setAsIs("testKey", true);
   }

   @Test
   void testDeleteNamedProperty() {
      variableScriptable.delete("testKey");
      verify(mockVariableTable).remove("testKey");
   }

   @Test
   void testSetGetPrototype() {
      Scriptable mockPrototype = mock(Scriptable.class);
      variableScriptable.setPrototype(mockPrototype);
      assertEquals(mockPrototype, variableScriptable.getPrototype());
   }

   @Test
   void testSetGetParentScope() {
      Scriptable mockParentScope = mock(Scriptable.class);
      variableScriptable.setParentScope(mockParentScope);
      assertEquals(mockParentScope, variableScriptable.getParentScope());
   }

   @Test
   void testGetIds() {
      Enumeration<String> mockKeys = mock(Enumeration.class);
      when(mockKeys.hasMoreElements()).thenReturn(true, false);
      when(mockKeys.nextElement()).thenReturn("key1");
      when(mockVariableTable.keys()).thenReturn(mockKeys);

      Object[] ids = variableScriptable.getIds();
      assertArrayEquals(new Object[]{ "key1", "length", "parameterNames", "_USER_", "_ROLES_", "_GROUPS_", "__principal__" }, ids);
   }

   @Test
   void testGetDefaultValue() {
      when(mockVariableTable.toString()).thenReturn("VariableTableString");
      Object result = variableScriptable.getDefaultValue(String.class);
      assertEquals("VariableTableString", result);
   }

   @Test
   void testUnwrap() {
      assertEquals(mockVariableTable, variableScriptable.unwrap());
   }

   @Test
   void testToString() {
      when(mockVariableTable.toString()).thenReturn("VariableTableString");
      assertEquals("VariableTableString", variableScriptable.toString());
   }
}