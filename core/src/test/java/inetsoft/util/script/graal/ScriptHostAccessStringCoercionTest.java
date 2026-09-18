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
 * Regression guard for #76778: a script calling a method on a *raw host object*
 * — e.g. {@code var f = new inetsoft.uql.XFormatInfo; f.setFormat(StyleConstant.NUMBER)}
 * — goes through GraalJS {@code invokeMember}, not {@link ScriptFunction}, so the
 * String coercion restored for our own scriptable dispatch in #75693 did not apply
 * and the call failed with "Cannot convert '3'(java.lang.Integer) to Java type
 * 'java.lang.String': Invalid or lossy primitive coercion".
 */
@Tag("core")
class ScriptHostAccessStringCoercionTest {
   public static class StringParam {
      public String got;

      public void setFormat(String format) {
         got = format;
      }
   }

   /** A type whose overloads must keep resolving as they did before the mapping. */
   public static class Overloaded {
      public String got;

      public void setX(int i) {
         got = "int:" + i;
      }

      public void setX(String s) {
         got = "String:" + s;
      }
   }

   private Context context() {
      return Context.newBuilder("js")
         .allowHostAccess(ScriptHostAccess.hostAccess())
         .allowHostClassLookup(ScriptHostAccess.classFilter(java.util.Set.of(), new String[0], true))
         .build();
   }

   private String eval(String js, Object target) {
      try(Context c = context()) {
         c.getBindings("js").putMember("t", target);
         c.getBindings("js").putMember("hostInt", Integer.valueOf(3));
         c.eval("js", js);
      }

      return target instanceof StringParam ? ((StringParam) target).got
         : ((Overloaded) target).got;
   }

   @Test
   void hostIntegerBindsToStringParameter() {
      // the reported case: StyleConstant.NUMBER reaches the script as a host Integer
      assertEquals("3", eval("t.setFormat(hostInt);", new StringParam()));
   }

   @Test
   void jsNumberBindsToStringParameterWithoutDecimalPoint() {
      // Rhino's ToString(3) is "3", not "3.0"
      assertEquals("3", eval("t.setFormat(3);", new StringParam()));
      assertEquals("3.5", eval("t.setFormat(3.5);", new StringParam()));
   }

   @Test
   void jsBooleanBindsToStringParameter() {
      assertEquals("true", eval("t.setFormat(true);", new StringParam()));
   }

   @Test
   void wholeNumberOutsideLongRangeIsNotClamped() {
      // must not narrow to Long.MAX_VALUE; shares ScriptFunction.toStringValue's guard
      assertNotEquals(Long.toString(Long.MAX_VALUE), eval("t.setFormat(1e21);", new StringParam()));
   }

   @Test
   void stringParameterKeepsAStringUnchanged() {
      assertEquals("$#,###", eval("t.setFormat('$#,###');", new StringParam()));
   }

   @Test
   void overloadResolutionIsUnaffected() {
      // LOWEST precedence keeps the mapping out of overload selection
      assertEquals("int:7", eval("t.setX(7);", new Overloaded()));
      assertEquals("String:abc", eval("t.setX('abc');", new Overloaded()));
   }
}
