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
package inetsoft.util.script.graal;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard for #81683: a call on a raw host object (invokeMember, host
 * constructor, bean setter) bound a non-boolean script value to a boolean
 * parameter and failed with "Cannot convert ... to Java type 'boolean'", where
 * Rhino used JS truthiness (ScriptRuntime.toBoolean). Note the Rhino rule makes
 * the string 'false' true.
 */
@Tag("core")
class ScriptHostAccessBooleanCoercionTest {
   public static class T {
      public String got;

      public void setB(boolean b) {
         got = "b:" + b;
      }

      public void setW(Boolean b) {
         got = "w:" + b;
      }

      public void setOB(Object o) {
         got = "obj:" + o;
      }

      public void setOB(boolean b) {
         got = "bool:" + b;
      }

      public void setSB(String s) {
         got = "str:" + s;
      }

      public void setSB(boolean b) {
         got = "bool:" + b;
      }

      public void setIB(int i) {
         got = "int:" + i;
      }

      public void setIB(boolean b) {
         got = "bool:" + b;
      }
   }

   private String eval(String js) {
      T t = new T();

      try(Context c = Context.newBuilder("js")
         .allowHostAccess(ScriptHostAccess.hostAccess())
         .allowHostClassLookup(
            ScriptHostAccess.classFilter(java.util.Set.of(), new String[0], true))
         .build())
      {
         c.getBindings("js").putMember("t", t);
         c.getBindings("js").putMember("hostInt", Integer.valueOf(3));
         c.eval("js", js);
      }

      return t.got;
   }

   @Test
   void primitiveAndWrapperFollowJsTruthiness() {
      String[] truthy = {"1", "2.5", "-1", "'true'", "'false'", "'abc'", "{}", "[]",
                         "new Date()", "hostInt", "true"};
      String[] falsy = {"0", "''", "false"};

      for(String arg : truthy) {
         assertEquals("b:true", eval("t.setB(" + arg + ")"), "setB " + arg);
         assertEquals("w:true", eval("t.setW(" + arg + ")"), "setW " + arg);
      }

      for(String arg : falsy) {
         assertEquals("b:false", eval("t.setB(" + arg + ")"), "setB " + arg);
         assertEquals("w:false", eval("t.setW(" + arg + ")"), "setW " + arg);
      }
   }

   @Test
   void booleanWrapperNullStaysNull() {
      // no null mapping on purpose: null means "unset" for Boolean setters
      assertEquals("w:null", eval("t.setW(null)"));
      assertEquals("w:null", eval("t.setW(undefined)"));
   }

   @Test
   void nullToPrimitiveBooleanStillThrows() {
      // out of scope (#81683): the host path never defaulted null to false
      assertThrows(Exception.class, () -> eval("t.setB(null)"));
   }

   @Test
   void overloadWithObjectKeepsObject() {
      assertEquals("obj:1", eval("t.setOB(1)"));
      assertEquals("bool:true", eval("t.setOB(true)"));
   }

   @Test
   void overloadWithStringKeepsString() {
      assertEquals("str:x", eval("t.setSB('x')"));
      assertEquals("bool:true", eval("t.setSB(true)"));
   }

   @Test
   void overloadWithIntKeepsInt() {
      assertEquals("int:3", eval("t.setIB(3)"));
      assertEquals("int:3", eval("t.setIB(hostInt)"));
      assertEquals("bool:false", eval("t.setIB(false)"));
   }

   @Test
   void numericArgToStringOrBooleanOverloadIsAmbiguous() {
      // documents the accepted consequence: the Number -> String and the new
      // Number -> Boolean mappings share the LOWEST tier, so neither wins
      assertThrows(Exception.class, () -> eval("t.setSB(1)"));
      assertThrows(Exception.class, () -> eval("t.setIB(2.5)"));
   }
}
