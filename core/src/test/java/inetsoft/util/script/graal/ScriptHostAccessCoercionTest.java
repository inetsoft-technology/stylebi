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
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard for #81889: raw host calls (Java.type statics, host
 * constructors, host object methods) rejected a numeric string for a numeric
 * parameter and an array / plain object for a String or Collection parameter,
 * which Rhino coerced via ToNumber / ToString.
 */
@Tag("core")
class ScriptHostAccessCoercionTest {
   public static class T {
      public String o(String s) {
         return "S:" + s;
      }

      public String o(int i) {
         return "I:" + i;
      }

      public String onlyS(String s) {
         return "S:" + s;
      }

      public String onlyI(int i) {
         return "I:" + i;
      }

      public String onlyL(long l) {
         return "L:" + l;
      }

      public String dd(double d) {
         return "D:" + d;
      }

      public String dd(long l) {
         return "L:" + l;
      }

      public String bi(boolean b) {
         return "B:" + b;
      }

      public String bi(int i) {
         return "I:" + i;
      }

      public String oo(Object o) {
         return "O";
      }

      public String oo(boolean b) {
         return "B";
      }
   }

   private Value eval(String js) {
      try(Context c = Context.newBuilder("js")
         .allowHostAccess(ScriptHostAccess.hostAccess())
         .allowHostClassLookup(
            ScriptHostAccess.classFilter(java.util.Set.of(), new String[0], true))
         .build())
      {
         c.getBindings("js").putMember("t", new T());
         c.getBindings("js").putMember("proxy",
            (org.graalvm.polyglot.proxy.ProxyObject)
               org.graalvm.polyglot.proxy.ProxyObject.fromMap(new java.util.HashMap<>()));
         Value v = c.eval("js", js);
         // detach from the closing context
         return v == null || v.isNull() ? null : c.asValue(v.toString());
      }
   }

   private String str(String js) {
      Value v = eval(js);
      return v == null ? null : v.asString();
   }

   private void refused(String js) {
      assertThrows(PolyglotException.class, () -> eval(js), js);
   }

   @Test
   void numericStringToDoubleAndInt() {
      assertEquals("8", str("Java.type('java.lang.Math').pow('2','3')"));
      assertEquals("ff", str("Java.type('java.lang.Integer').toHexString('255')"));
      assertEquals("1", str("new (Java.type('java.awt.geom.Rectangle2D.Double'))" +
                            "('1',2,3,4).getX()"));
      assertEquals("1.50", str(
         "new (Java.type('java.math.BigDecimal'))('1.5').setScale('2').toString()"));
      assertEquals("1.5", str(
         "new (Java.type('java.math.BigDecimal'))('1.5').toString()"));
   }

   @Test
   void doubleStringSemantics() {
      assertEquals("NaN", str("Java.type('java.lang.Math').max('abc',1)"));
      assertEquals("2.5", str("Java.type('java.lang.Math').max('2.5',1)"));
      assertEquals("3.7", str("Java.type('java.lang.Math').abs('-3.7')"));
      assertEquals("D:5.5", str("t.dd('5.5')"));
   }

   @Test
   void intAndLongOnlyForFiniteIntegralStrings() {
      assertEquals("I:7", str("t.onlyI('7')"));
      assertEquals("I:0", str("t.onlyI('')"));
      assertEquals("L:3000000000", str("t.onlyL('3000000000')"));
      refused("t.onlyI('abc')");
      refused("t.onlyI('7.5')");
      refused("t.onlyI('3000000000')");
      refused("t.onlyL('x')");
   }

   @Test
   void existingOverloadsUnchanged() {
      assertEquals("S:5", str("t.o('5')"));
      assertEquals("I:5", str("t.o(5)"));
      assertEquals("O", str("t.oo('5')"));
   }

   @Test
   void arrayToString() {
      assertEquals("S:c,d", str("t.onlyS(['c','d'])"));
      assertEquals("S:1,2,3", str("t.onlyS([1,[2,3]])"));
      assertEquals("S:a,,b,", str("t.onlyS(['a',null,'b',undefined])"));
   }

   @Test
   void plainObjectToStringOnly() {
      assertEquals("S:[object Object]", str("t.onlyS({})"));
      refused("t.onlyS(new Date(0))");
      refused("t.onlyS(/a/)");
      refused("t.onlyS(new Map())");
      refused("t.onlyS(function() {})");
      refused("t.onlyS(proxy)");
      refused("t.onlyS(new (Java.type('java.util.ArrayList'))())");
   }

   @Test
   void arrayToCollection() {
      assertEquals("2", str("new (Java.type('java.util.ArrayList'))([1,2]).size()"));
   }

   @Test
   void graphElementStringArgs() {
      String le = "new (Java.type('inetsoft.graph.element.LineElement'))";
      assertEquals("a,b", str("var e = " + le + "(['a','b']); e.getVar(0)"));
      assertEquals("c,d", str("var e = " + le + "('a'); e.addDim(['c','d']); " +
                              "e.getDim(0)"));
      assertEquals("b", str("var e = " + le + "('a',['b']); e.getVar(0)"));
   }

   @Test
   void numericStringWithBooleanOverloadIsAmbiguous() {
      // documents the accepted #81889 consequence: both mappings share the
      // final tier. A non-numeric string still binds the boolean overload.
      assertEquals("B:true", str("t.bi('abc')"));
      assertThrows(PolyglotException.class, () -> eval("t.bi('5')"));
   }
}
