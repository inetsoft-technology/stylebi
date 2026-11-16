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

package inetsoft.web.wiz.script;

import inetsoft.web.wiz.pairing.PairingException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81445: an Expression-mode dynamic value is JavaScript, where $ is not defined. A bare
 * $(Name) was already refused; the same call inside a larger expression parses and only failed
 * when the field was evaluated ("ReferenceError: $ is not defined").
 */
@Tag("core")
class DynamicValueTargetsEmbeddedDollarTest {
   private static final UnaryOperator<String> PARSES = text -> null;

   private static void assertRefused(String text) {
      PairingException ex = assertThrows(PairingException.class,
         () -> DynamicValueTargets.prepareWrite(text, PARSES), text);
      assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind(), text);
      assertTrue(ex.getMessage().contains("Nothing was pushed"), ex.getMessage());
   }

   @Test
   void refusesTheReportedExpressionThatCallsDollarInsideAnotherCall() {
      PairingException ex = assertThrows(PairingException.class,
         () -> DynamicValueTargets.prepareWrite("String($(RadioButton1)) == 'true'", PARSES));

      assertTrue(ex.getMessage().contains("calls $(...)"), ex.getMessage());
      assertTrue(ex.getMessage().contains("ReferenceError"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Variable"), ex.getMessage());
   }

   @Test
   void refusesDollarCallsInOtherPositions() {
      assertRefused("$(A) + 1");
      assertRefused("1 + $ (A)");
      assertRefused("x ? $(A) : 0");
      assertRefused("= String($(A)) == 'true'");
      assertRefused("f(1,$(A))");
   }

   @Test
   void aBareReferenceKeepsItsOwnMessage() {
      PairingException ex = assertThrows(PairingException.class,
         () -> DynamicValueTargets.prepareWrite("$(A)", PARSES));

      assertTrue(ex.getMessage().contains("variable reference"), ex.getMessage());
   }

   @Test
   void leavesAnythingThatIsNotACallToTheBareGlobalAlone() throws Exception {
      for(String ok : new String[] {
         "parameter.cutoff > 0",
         "RadioButton1.selectedObject == 'true'",
         "'cost $(USD)' + 1",
         "\"$(A)\" + 'x'",
         "`$(A)` + 'x'",
         "'it\\'s $(A)' + 1",
         "1 // $(A) is not valid here\n + 2",
         "1 /* $(A) */ + 2",
         "a$(1)",
         "obj.$(1)",
         "x$ + 1",
      }) {
         assertEquals(ok.strip(), DynamicValueTargets.prepareWrite(ok, PARSES).text().strip(), ok);
      }
   }

   @Test
   void theReadNoteStatesTheSyntax() throws Exception {
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.DYNAMIC_VALUE, "Text1", "visible");
      String note = DynamicValueTargets.note(target, false);

      assertTrue(note.contains("JavaScript"), note);
      assertTrue(note.contains("$(Name)"), note);
   }
}
