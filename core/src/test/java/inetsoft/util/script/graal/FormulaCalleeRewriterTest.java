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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81047: the callee rewrite of {@link FormulaCalleeRewriter}. Pure text in, text out; the
 * set of function names is injected, so no engine or Spring context is involved.
 */
@Tag("core")
class FormulaCalleeRewriterTest {
   private static final Predicate<String> FUNCS = n -> Set.of("year", "YEAR", "dateAdd").contains(n);

   private static String rw(String s) {
      return FormulaCalleeRewriter.rewrite(s, FUNCS);
   }

   private static String c(String n) {
      return "__inetsoft_callee__(" + n + ",\"" + n + "\")";
   }

   @Test
   void internalParserIsAvailable() throws Exception {
      // a Graal upgrade that moves or changes the parser fails here, loudly, instead of the
      // rewrite silently turning itself off
      Class.forName("com.oracle.js.parser.Parser");
      Class.forName("com.oracle.js.parser.ScriptEnvironment");
      Class.forName("com.oracle.js.parser.ErrorManager$ThrowErrorManager");
      assertEquals(c("year") + "(1)", rw("year(1)"));
   }

   @Test
   void rewritesBareCallsOnly() {
      assertEquals(c("year") + "(a) + year", rw("year(a) + year"));
      assertEquals("year + 1", rw("year + 1"));
      assertEquals(c("YEAR") + "(x) + " + c("dateAdd") + "('d',1,y)",
                   rw("YEAR(x) + dateAdd('d',1,y)"));
      assertEquals(c("year") + "(" + c("year") + "(x))", rw("year(year(x))"));
      assertEquals("foo(1)", rw("foo(1)"));
   }

   @Test
   void leavesPropertyNewOptionalAndEvalAlone() {
      assertEquals("a.year(1)", rw("a.year(1)"));
      assertEquals("a['year'](1)", rw("a['year'](1)"));
      assertEquals("new year(1)", rw("new year(1)"));
      assertEquals("year?.(1)", rw("year?.(1)"));
      assertEquals("eval('1')", FormulaCalleeRewriter.rewrite("eval('1')", n -> true));
   }

   @Test
   void stringsCommentsTemplatesAndRegexAreNotCalls() {
      assertEquals("'year(1)' + year", rw("'year(1)' + year"));
      assertEquals("// year(1)\nyear", rw("// year(1)\nyear"));
      assertEquals("/* year(1) */ year", rw("/* year(1) */ year"));
      assertEquals("`year(1)`", rw("`year(1)`"));
      assertEquals(c("year") + "(1) + `${" + c("year") + "(2)}`", rw("year(1) + `${year(2)}`"));
      assertEquals("/year(1)/.test(s)", rw("/year(1)/.test(s)"));
   }

   @Test
   void offsetsSurviveCrlfAndNonBmpCharacters() {
      assertEquals("var s='\uD83D\uDE00';\r\n" + c("year") + "(1)\r\n/*\u4e2d*/ + " + c("YEAR") + "(2)",
                   rw("var s='\uD83D\uDE00';\r\nyear(1)\r\n/*\u4e2d*/ + YEAR(2)"));
   }

   @Test
   void parseFailureReturnsTheInput() {
      assertEquals("return year(1)", rw("return year(1)"));
      assertEquals("year(1", rw("year(1"));
      assertEquals("year(1) +", rw("year(1) +"));
      assertNull(rw(null));
   }

   @Test
   void noParenIsAFastPath() {
      assertEquals("year", rw("year"));
      assertEquals("", rw(""));
   }

   @Test
   void helperNameIsIdempotent() {
      String once = rw("year(1)");
      assertEquals(once, rw(once));
      assertEquals("__inetsoft_callee__(1)",
                   FormulaCalleeRewriter.rewrite("__inetsoft_callee__(1)", n -> true));
   }
}
