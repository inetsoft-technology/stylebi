/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

@Tag("core")
class ScriptFunctionTest {
   private Context ctx;

   @BeforeEach void setup() { ctx = Context.create("js"); }
   @AfterEach void teardown() { ctx.close(); }

   /** Records the arguments a script function was invoked with. */
   public static final class Target {
      public String name;
      public boolean visible = true; // non-default so we can detect "false was passed"
      public int count = -1;
      public double ratio = -1.0;
      public char ch = 'X';
      public String label;
      public boolean called;

      public void setActionVisible(String name, boolean visible) {
         this.called = true;
         this.name = name;
         this.visible = visible;
      }

      public void setCount(int count) {
         this.called = true;
         this.count = count;
      }

      public void setRatio(double ratio) {
         this.called = true;
         this.ratio = ratio;
      }

      public void setChar(char ch) {
         this.called = true;
         this.ch = ch;
      }

      public void setLabel(String label) {
         this.called = true;
         this.label = label;
      }
   }

   @Test
   void omittedBooleanArgDefaultsToFalse() {
      // Bug #75525: setActionVisible('Edit') omits the trailing boolean; the
      // reflective invocation must default it to false (Rhino behavior), not
      // fail trying to unbox null into a primitive boolean.
      Target t = new Target();
      ctx.getBindings("js").putMember(
         "setActionVisible",
         new ScriptFunction(t, Target.class, "setActionVisible", String.class, boolean.class));

      ctx.eval("js", "setActionVisible('Edit')");

      assertTrue(t.called);
      assertEquals("Edit", t.name);
      assertFalse(t.visible, "omitted boolean argument should default to false");
   }

   @Test
   void explicitBooleanArgPreserved() {
      Target t = new Target();
      ctx.getBindings("js").putMember(
         "setActionVisible",
         new ScriptFunction(t, Target.class, "setActionVisible", String.class, boolean.class));

      ctx.eval("js", "setActionVisible('Edit', true)");

      assertTrue(t.visible, "explicit boolean argument should be preserved");
   }

   @Test
   void omittedNumericArgDefaultsToZero() {
      Target t = new Target();
      ctx.getBindings("js").putMember(
         "setCount", new ScriptFunction(t, Target.class, "setCount", int.class));

      ctx.eval("js", "setCount()");

      assertTrue(t.called);
      assertEquals(0, t.count, "omitted numeric argument should default to 0");
   }

   @Test
   void numericArgIsNarrowed() {
      // a script number (Double) passed to an int parameter must still be narrowed
      Target t = new Target();
      ctx.getBindings("js").putMember(
         "setCount", new ScriptFunction(t, Target.class, "setCount", int.class));

      ctx.eval("js", "setCount(42)");

      assertEquals(42, t.count);
   }

   @Test
   void numericStringArgIsCoerced() {
      // Bug #75611: a numeric string (e.g. CALC.edate(date, '3')) passed to an
      // int parameter must be coerced to a number, matching Rhino's argument
      // conversion, rather than failing the reflective invocation.
      Target t = new Target();
      ctx.getBindings("js").putMember(
         "setCount", new ScriptFunction(t, Target.class, "setCount", int.class));

      ctx.eval("js", "setCount('3')");

      assertTrue(t.called);
      assertEquals(3, t.count, "numeric string argument should be coerced to int");
   }

   @Test
   void unparseableNumericStringArgBecomesZero() {
      // Bug #75611: an unparseable string to a numeric parameter degrades to
      // NaN -> 0 (Rhino ScriptRuntime.toNumber parity), not an invocation error.
      Target t = new Target();
      ctx.getBindings("js").putMember(
         "setCount", new ScriptFunction(t, Target.class, "setCount", int.class));

      ctx.eval("js", "setCount('abc')");

      assertTrue(t.called);
      assertEquals(0, t.count, "unparseable numeric string should coerce to 0");
   }

   @Test
   void numericStringArgIsCoercedToDouble() {
      // Bug #75611: numeric string coercion also applies to double parameters.
      Target t = new Target();
      ctx.getBindings("js").putMember(
         "setRatio", new ScriptFunction(t, Target.class, "setRatio", double.class));

      ctx.eval("js", "setRatio('2.5')");

      assertTrue(t.called);
      assertEquals(2.5, t.ratio, 0.0, "numeric string argument should be coerced to double");
   }

   @Test
   void booleanArgIsCoercedToNumber() {
      // Bug #75611 side effect: a JS boolean passed to a numeric parameter
      // coerces to 1/0 (JS ToNumber / Rhino parity), rather than failing the
      // reflective invocation. Lock in this widened behavior.
      Target t1 = new Target();
      ctx.getBindings("js").putMember(
         "setCount", new ScriptFunction(t1, Target.class, "setCount", int.class));
      ctx.eval("js", "setCount(true)");
      assertTrue(t1.called);
      assertEquals(1, t1.count, "boolean true should coerce to 1");

      Target t2 = new Target();
      ctx.getBindings("js").putMember(
         "setCount", new ScriptFunction(t2, Target.class, "setCount", int.class));
      ctx.eval("js", "setCount(false)");
      assertEquals(0, t2.count, "boolean false should coerce to 0");
   }

   @Test
   void omittedDoubleArgDefaultsToZero() {
      Target t = new Target();
      ctx.getBindings("js").putMember(
         "setRatio", new ScriptFunction(t, Target.class, "setRatio", double.class));

      ctx.eval("js", "setRatio()");

      assertTrue(t.called);
      assertEquals(0.0, t.ratio, 0.0, "omitted double argument should default to 0.0");
   }

   @Test
   void booleanArgIsCoercedToString() {
      // Bug #75693: a JS boolean passed to a String parameter must be coerced to
      // "true"/"false" (Rhino ScriptRuntime.toString parity), e.g.
      // bindingInfo.setGroupTotal("reseller", ROW_HEADER, true) on the
      // setGroupTotal(String, int, String) overload, rather than failing the
      // reflective invocation ("Failed to invoke script function").
      Target t = new Target();
      ctx.getBindings("js").putMember(
         "setLabel", new ScriptFunction(t, Target.class, "setLabel", String.class));

      ctx.eval("js", "setLabel(true)");

      assertTrue(t.called);
      assertEquals("true", t.label, "boolean true should coerce to \"true\"");
   }

   @Test
   void numberArgIsCoercedToString() {
      // A whole JS number bound to a String parameter stringifies as an integer
      // ("3", not "3.0"), matching JS ToString.
      Target t = new Target();
      ctx.getBindings("js").putMember(
         "setLabel", new ScriptFunction(t, Target.class, "setLabel", String.class));

      ctx.eval("js", "setLabel(3)");

      assertTrue(t.called);
      assertEquals("3", t.label, "whole number should coerce to \"3\"");
   }

   @Test
   void fractionalNumberArgIsCoercedToString() {
      // A fractional JS number bound to a String parameter keeps its decimal
      // form ("3.5"), exercising the String.valueOf branch of toStringValue.
      Target t = new Target();
      ctx.getBindings("js").putMember(
         "setLabel", new ScriptFunction(t, Target.class, "setLabel", String.class));

      ctx.eval("js", "setLabel(3.5)");

      assertTrue(t.called);
      assertEquals("3.5", t.label, "fractional number should coerce to \"3.5\"");
   }

   @Test
   void stringArgToStringParamPreserved() {
      // A String argument bound to a String parameter is passed through unchanged.
      Target t = new Target();
      ctx.getBindings("js").putMember(
         "setLabel", new ScriptFunction(t, Target.class, "setLabel", String.class));

      ctx.eval("js", "setLabel('show')");

      assertEquals("show", t.label, "string argument should be preserved");
   }

   @Test
   void omittedCharArgDefaultsToNul() {
      Target t = new Target();
      ctx.getBindings("js").putMember(
         "setChar", new ScriptFunction(t, Target.class, "setChar", char.class));

      ctx.eval("js", "setChar()");

      assertTrue(t.called);
      assertEquals('\0', t.ch, "omitted char argument should default to '\\0'");
   }
}
