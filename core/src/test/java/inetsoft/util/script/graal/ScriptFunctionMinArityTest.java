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

/**
 * Bug #76862: a strict-arity function whose trailing parameters may be omitted, e.g.
 * bindingInfo.setTopNReverse(field, reverse[, type]).
 */
@Tag("core")
class ScriptFunctionMinArityTest {
   private Context ctx;

   @BeforeEach void setup() { ctx = Context.create("js"); }
   @AfterEach void teardown() { ctx.close(); }

   public static final class Target {
      public String field;
      public boolean reverse;
      public int type = -1;
      public boolean called;

      public void set(String field, boolean reverse, int type) {
         this.called = true;
         this.field = field;
         this.reverse = reverse;
         this.type = type;
      }
   }

   private Target bind() {
      Target t = new Target();
      ctx.getBindings("js").putMember(
         "set", new ScriptFunction(t, Target.class, "set", 2, String.class, boolean.class,
                                   int.class));
      return t;
   }

   @Test
   void omittedTrailingArgumentIsPadded() {
      Target t = bind();

      ctx.eval("js", "set('STATE', true)");

      assertTrue(t.called);
      assertEquals("STATE", t.field);
      assertTrue(t.reverse);
      assertEquals(0, t.type, "an omitted int is padded to 0");
   }

   @Test
   void fullArgumentCountIsAccepted() {
      Target t = bind();

      ctx.eval("js", "set('STATE', true, 5)");

      assertEquals(5, t.type);
   }

   @Test
   void fewerThanMinimumIsRejected() {
      Target t = bind();

      Exception ex = assertThrows(RuntimeException.class, () -> ctx.eval("js", "set(true)"));
      assertFalse(t.called, "a rejected call must not invoke the method");
      assertTrue(ex.getMessage().contains(
         "set() expects 3 arguments, got 1 (the last argument is optional)"), ex.getMessage());
   }

   @Test
   void moreThanDeclaredIsRejected() {
      Target t = bind();

      assertThrows(RuntimeException.class, () -> ctx.eval("js", "set('STATE', true, 5, 6)"));
      assertFalse(t.called);
   }

   @Test
   void messageNamesSeveralOptionalArguments() {
      ScriptFunction fn = new ScriptFunction(new Target(), Target.class, "set", 1,
                                             String.class, boolean.class, int.class);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> fn.execute());
      assertEquals("set() expects 3 arguments, got 0 (the last 2 arguments are optional)",
                   ex.getMessage());
   }
}
