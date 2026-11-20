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
import inetsoft.util.script.graal.ScopeProxy;
import inetsoft.util.script.graal.ScriptHostAccess;
import inetsoft.util.script.graal.ScriptScope;
import org.graalvm.polyglot.Context;
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

   /**
    * Bug #81929: an unset parameter reads as JS null (13.8/Rhino), not undefined,
    * through the real GraalJS engine, without breaking string coercion or key
    * enumeration.
    */
   @Test
   void testUnsetParameterReadsNullInGraalJs() throws Exception {
      VariableTable vars = new VariableTable();
      vars.put("setx", 1);

      // the production host access, which allows the host arrays of getMemberKeys()
      try(Context ctx = Context.newBuilder("js").allowHostAccess(ScriptHostAccess.hostAccess()).build()) {
         ctx.getBindings("js").putMember("parameter", new ScopeProxy(new VariableScriptable(vars)));

         assertTrue(ctx.eval("js", "parameter.nosuch === null").asBoolean());
         assertEquals("object", ctx.eval("js", "typeof parameter.nosuch").asString());
         assertEquals("object", ctx.eval("js", "typeof parameter['nosuch']").asString());
         assertTrue(ctx.eval("js", "parameter.nosuch == null").asBoolean());
         assertTrue(ctx.eval("js", "parameter.setx === 1").asBoolean());
         assertEquals(1, ctx.eval("js", "parameter.length").asInt());
         assertTrue(ctx.eval("js", "parameter.parameterNames != null").asBoolean());
         assertEquals("setx=1", ctx.eval("js", "'' + parameter").asString());
         assertEquals("setx=1", ctx.eval("js", "String(parameter)").asString());
         assertEquals("function", ctx.eval("js", "typeof parameter.valueOf").asString());
         assertTrue(ctx.eval("js", "Object.keys(parameter).indexOf('setx') >= 0").asBoolean());
         assertTrue(ctx.eval("js", "Object.keys(parameter).indexOf('nosuch') < 0").asBoolean());
         assertTrue(ctx.eval("js", "JSON.stringify(parameter).indexOf('\"setx\":1') >= 0").asBoolean());
         assertTrue(ctx.eval("js", "JSON.stringify(parameter).indexOf('nosuch') < 0").asBoolean());
         assertTrue(ctx.eval("js", "parameter.hasOwnProperty('setx')").asBoolean());

         // known trade-off: GraalJS answers in/hasOwnProperty/Reflect.has from the same
         // hasMember() that gates the read, so every name reports as present; whether a
         // parameter was supplied is told by Object.keys (above) or by "!= null"
         assertTrue(ctx.eval("js", "'nosuch' in parameter").asBoolean());
         assertTrue(ctx.eval("js", "parameter.hasOwnProperty('nosuch')").asBoolean());
         assertTrue(ctx.eval("js", "Reflect.has(parameter, 'nosuch')").asBoolean());
         assertFalse(ctx.eval("js", "parameter.nosuch != null").asBoolean());

         ctx.eval("js", "parameter.newp = 7");
         assertEquals(7, ctx.eval("js", "parameter.newp").asInt());
      }

      // the Java-side contract is unchanged
      VariableScriptable scriptable = new VariableScriptable(vars);
      assertFalse(scriptable.hasMember("nosuch"));
      assertNull(scriptable.getMember("nosuch"));
   }
}
