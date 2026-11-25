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
package inetsoft.web.viewsheet;

import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Bug #82265: the same script error recorded several times in one request is sent once.
 * The helper is static and needs no Spring context.
 */
@Tag("core")
class EventAspectDedupScriptExceptionsTest {
   private static ScriptException err(String assembly, String msg) {
      return new ScriptException("Script execution error in assembly: " + assembly + "\n" + msg);
   }

   @Test
   void sameAssemblyAndMessageCollapsesToOne() {
      List<Exception> in = new ArrayList<>();

      for(int i = 0; i < 4; i++) {
         in.add(err("SelectionTree1", "noSuchFunction123 is not defined"));
      }

      assertEquals(1, EventAspect.dedupScriptExceptions(in).size());
   }

   @Test
   void differentMessageStaysDistinctInOrder() {
      ScriptException a = err("SelectionTree1", "m1");
      ScriptException b = err("SelectionTree1", "m2");
      List<Exception> out = EventAspect.dedupScriptExceptions(List.of(a, b, err("SelectionTree1", "m1")));

      assertEquals(2, out.size());
      assertSame(a, out.get(0));
      assertSame(b, out.get(1));
   }

   @Test
   void differentAssemblyStaysDistinct() {
      List<Exception> out = EventAspect.dedupScriptExceptions(
         List.of(err("Text1", "boom"), err("Text2", "boom")));

      assertEquals(2, out.size());
   }

   @Test
   void wrappedScriptExceptionIsDeduped() {
      List<Exception> out = EventAspect.dedupScriptExceptions(List.of(
         new RuntimeException("same", new ScriptException("x")),
         new RuntimeException("same", new ScriptException("x"))));

      assertEquals(1, out.size());
   }

   @Test
   void nonScriptExceptionsAreUntouched() {
      Exception e1 = new IllegalStateException("same");
      Exception e2 = new IllegalStateException("same");
      List<Exception> out = EventAspect.dedupScriptExceptions(List.of(e1, e2));

      assertEquals(2, out.size());
      assertSame(e1, out.get(0));
      assertSame(e2, out.get(1));
   }
}
