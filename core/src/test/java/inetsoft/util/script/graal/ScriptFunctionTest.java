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
}
