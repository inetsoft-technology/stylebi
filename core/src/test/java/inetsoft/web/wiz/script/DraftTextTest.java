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

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static inetsoft.web.wiz.script.DraftText.EqualsMode.*;
import static org.junit.jupiter.api.Assertions.*;

/** The shared write normalization of the draft kinds that push plain text (Bug #80459). */
@Tag("core")
class DraftTextTest {
   /** The script engine is not initialised in unit tests: "1 +" and "=x" are told apart by hand. */
   private static final UnaryOperator<String> ENGINE =
      text -> text.trim().endsWith("+") || text.startsWith("=") ? "unexpected token" : null;

   private static DraftText.Prepared prepare(String text, DraftText.EqualsMode mode)
      throws Exception
   {
      return DraftText.prepare(text, mode, ENGINE);
   }

   private static void refused(String text, DraftText.EqualsMode mode, String fragment) {
      PairingException ex = assertThrows(PairingException.class, () -> prepare(text, mode),
                                         mode + " " + text);
      assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind(), ex.getMessage());
      assertTrue(ex.getMessage().contains(fragment), ex.getMessage());
   }

   // ---- STRIP: the variable default ---------------------------------------------------------

   @Test
   void stripRemovesOneLeadingEqualsAndReportsIt() throws Exception {
      DraftText.Prepared p = prepare("= 1 > 0", STRIP);
      assertEquals("1 > 0", p.text());
      assertTrue(p.changedEquals());

      p = prepare("  =x", STRIP);
      assertEquals("x", p.text());
      assertTrue(p.changedEquals());
   }

   @Test
   void stripLeavesATextWithoutALeadingEqualsAlone() throws Exception {
      DraftText.Prepared p = prepare("parameter.n > 1", STRIP);

      assertEquals("parameter.n > 1", p.text());
      assertFalse(p.changedEquals());
   }

   @Test
   void stripRefusesADoubleEqualsInsteadOfStrippingJustOne() {
      refused("==x", STRIP, "starts with '=='");
      refused("== x", STRIP, "starts with '=='");
   }

   @Test
   void stripRunsTheSyntaxCheckOnTheTextWithoutTheEquals() throws Exception {
      List<String> seen = new ArrayList<>();
      DraftText.prepare("= 2 * 3", STRIP, text -> {
         seen.add(text);
         return null;
      });

      assertEquals(List.of("2 * 3"), seen);
   }

   // ---- ENSURE: the hyperlink expression ----------------------------------------------------

   @Test
   void ensurePrefixesOneEqualsWhenMissingAndReportsIt() throws Exception {
      DraftText.Prepared p = prepare("x", ENSURE);
      assertEquals("=x", p.text());
      assertTrue(p.changedEquals());

      p = prepare("  \"https://a/\" + n", ENSURE);
      assertEquals("=\"https://a/\" + n", p.text());
      assertTrue(p.changedEquals());
   }

   @Test
   void ensureKeepsAnExistingLeadingEqualsAndReportsNoChange() throws Exception {
      DraftText.Prepared p = prepare("=x", ENSURE);
      assertEquals("=x", p.text());
      assertFalse(p.changedEquals());

      p = prepare("  = x + 1", ENSURE);
      assertEquals("= x + 1", p.text());
      assertFalse(p.changedEquals());
   }

   @Test
   void ensureRunsTheSyntaxCheckOnTheTextWithoutTheEqualsForBothSpellings() throws Exception {
      List<String> seen = new ArrayList<>();
      UnaryOperator<String> record = text -> {
         seen.add(text);
         return null;
      };

      DraftText.prepare("x + 1", ENSURE, record);
      DraftText.prepare("=x + 1", ENSURE, record);
      DraftText.prepare("= x + 1", ENSURE, record);

      assertEquals(List.of("x + 1", "x + 1", "x + 1"), seen);
   }

   @Test
   void ensureRefusesADoubleEqualsABareEqualsAndASyntaxError() {
      refused("==x", ENSURE, "starts with '=='");
      refused("=", ENSURE, "empty");
      refused(" = ", ENSURE, "empty");
      refused("=1 +", ENSURE, "does not parse as a JavaScript expression (unexpected token)");
      refused("1 +", ENSURE, "Nothing was pushed");
   }

   // ---- NONE: SQL ---------------------------------------------------------------------------

   @Test
   void noneKeepsTheTextByteForByteAndRunsNoSyntaxCheck() throws Exception {
      String sql = "  select a, 'b' from t where x = $(var) and y = =1  \n";
      DraftText.Prepared p = DraftText.prepare(sql, NONE, text -> "must not be consulted");

      assertEquals(sql, p.text());
      assertFalse(p.changedEquals());

      assertEquals("= select 1", DraftText.prepare("= select 1", NONE, null).text());
      assertEquals("== x", DraftText.prepare("== x", NONE, null).text());
   }

   @Test
   void noneAcceptsANullSyntaxChecker() throws Exception {
      assertEquals("select 1", DraftText.prepare("select 1", NONE, null).text());
   }

   // ---- common ------------------------------------------------------------------------------

   @Test
   void nullIsRefusedInEveryMode() {
      for(DraftText.EqualsMode mode : DraftText.EqualsMode.values()) {
         refused(null, mode, "text is required.");
      }
   }

   @Test
   void blankIsRefusedInEveryModeWithNothingWasPushed() {
      for(DraftText.EqualsMode mode : DraftText.EqualsMode.values()) {
         for(String blank : new String[] { "", "   ", "\n\t " }) {
            refused(blank, mode, "Nothing was pushed.");
         }
      }
   }

   @Test
   void aBareEqualsIsBlankForStripAndEnsureButTextForNone() throws Exception {
      refused("=", STRIP, "empty");
      refused("=", ENSURE, "empty");
      assertEquals("=", DraftText.prepare("=", NONE, null).text());
   }

   @Test
   void jsSyntaxProblemIsNullWhenTheEngineIsNotInitialised() {
      // The engine is absent in unit tests; the production wrapper must not throw.
      assertDoesNotThrow(() -> DraftText.jsSyntaxProblem("1 + 1"));
   }
}
