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
import inetsoft.uql.VariableTable;
import inetsoft.util.script.graal.ScriptScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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
      Object result = variableScriptable.getMember("testKey");
      assertEquals("testValue", result);
   }

   @Test
   void testGetNamedPropertyDoesNotExist() throws Exception {
      when(mockVariableTable.get("nonExistentKey")).thenReturn(null);
      Object result = variableScriptable.getMember("nonExistentKey");
      assertNull(result);
   }

   @Test
   void testGetNamedPropertySpecialKeys() {
      when(mockVariableTable.size()).thenReturn(5);
      Object lengthResult = variableScriptable.getMember("length");
      assertEquals(5, lengthResult);

      Enumeration<String> mockKeys = mock(Enumeration.class);
      when(mockKeys.hasMoreElements()).thenReturn(true, false);
      when(mockKeys.nextElement()).thenReturn("param1");
      when(mockVariableTable.keys()).thenReturn(mockKeys);

      Object parameterNamesResult = variableScriptable.getMember("parameterNames");
      assertArrayEquals(new String[]{ "param1" }, (String[]) parameterNamesResult);
   }

   @Test
   void testHasNamedPropertyExists() throws Exception {
      when(mockVariableTable.get("testKey")).thenReturn("testValue");
      assertTrue(variableScriptable.hasMember("testKey"));
   }

   @Test
   void testHasNamedPropertyNotExists() throws Exception {
      when(mockVariableTable.get("invalidKey")).thenReturn(null);
      assertFalse(variableScriptable.hasMember("invalidKey"));
   }

   @Test
   void testHasNamedPropertySpecialKeys() {
      assertTrue(variableScriptable.hasMember("length"));
      assertTrue(variableScriptable.hasMember("parameterNames"));
   }

   @Test
   void testPutNamedProperty() {
      variableScriptable.putMember("testKey", "testValue");
      verify(mockVariableTable).put("testKey", "testValue");
      verify(mockVariableTable).setAsIs("testKey", true);
   }

   @Test
   void testRemoveNamedProperty() {
      variableScriptable.removeMember("testKey");
      verify(mockVariableTable).remove("testKey");
   }

   @Test
   void testSetGetParentScope() {
      ScriptScope mockParentScope = mock(ScriptScope.class);
      variableScriptable.setParentScope(mockParentScope);
      assertEquals(mockParentScope, variableScriptable.getParentScope());
   }

   @Test
   void testGetMemberKeys() {
      Enumeration<String> mockKeys = mock(Enumeration.class);
      when(mockKeys.hasMoreElements()).thenReturn(true, false);
      when(mockKeys.nextElement()).thenReturn("key1");
      when(mockVariableTable.keys()).thenReturn(mockKeys);

      Object[] ids = variableScriptable.getMemberKeys();
      assertArrayEquals(new Object[]{ "key1", "length", "parameterNames", "_USER_", "_ROLES_", "_GROUPS_", "__principal__" }, ids);
   }

   @Test
   void testUnwrap() {
      assertEquals(mockVariableTable, variableScriptable.unwrap());
   }

   @Test
   void testToString() throws Exception {
      Enumeration<String> mockKeys = mock(Enumeration.class);
      when(mockKeys.hasMoreElements()).thenReturn(true, true, false);
      when(mockKeys.nextElement()).thenReturn("key1", "key2");
      when(mockVariableTable.keys()).thenReturn(mockKeys);
      when(mockVariableTable.get("key1")).thenReturn("value1");
      when(mockVariableTable.get("key2")).thenReturn("value2");

      assertEquals("key1=value1, key2=value2", variableScriptable.toString());
   }

   @Test
   void testToStringSkipsFailedGet() throws Exception {
      Enumeration<String> mockKeys = mock(Enumeration.class);
      when(mockKeys.hasMoreElements()).thenReturn(true, true, false);
      when(mockKeys.nextElement()).thenReturn("key1", "key2");
      when(mockVariableTable.keys()).thenReturn(mockKeys);
      when(mockVariableTable.get("key1")).thenThrow(new RuntimeException("boom"));
      when(mockVariableTable.get("key2")).thenReturn("value2");

      assertEquals("key2=value2", variableScriptable.toString());
   }
}
