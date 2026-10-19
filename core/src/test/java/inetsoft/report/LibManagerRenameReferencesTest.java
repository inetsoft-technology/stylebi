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
package inetsoft.report;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #78759: renaming a library function rewrites its callers by identifier, not by substring,
 * and only in code.
 */
@Tag("core")
class LibManagerRenameReferencesTest {
   private static String rename(String text) {
      return LibManager.renameFunctionReferences("ab", "cd", text);
   }

   @Test
   void leavesLongerIdentifiersAlone() {
      String text = "T.label = abX(1) + fooab(2) + $ab(3) + ab_(4) + label;";
      assertEquals(text, rename(text));
   }

   @Test
   void renamesCallsWhateverTheirForm() {
      assertEquals("x = cd(1);", rename("x = ab(1);"));
      assertEquals("x = cd (1);", rename("x = ab (1);"));
      assertEquals("x = new cd(1);", rename("x = new ab(1);"));
      assertEquals("x = cd(cd(1));", rename("x = ab(ab(1));"));
      assertEquals("x = y ? cd(1) : 2;", rename("x = y ? ab(1) : 2;"));
      assertEquals("[1].map(cd);", rename("[1].map(ab);"));
   }

   @Test
   void leavesStringsCommentsAndTemplateTextAlone() {
      String text = "x = 'ab(1)' + \"label-ab\";\n// call ab(1)\n/* ab(2) */\ny = `ab ${ab(1)} ab`;";
      assertEquals("x = 'ab(1)' + \"label-ab\";\n// call ab(1)\n/* ab(2) */\ny = `ab ${cd(1)} ab`;",
                   rename(text));
   }

   @Test
   void leavesRegexLiteralsAlone() {
      String text = "x = /ab(1)/.test(s) ? ab(2) : 0;";
      assertEquals("x = /ab(1)/.test(s) ? cd(2) : 0;", rename(text));
   }

   @Test
   void leavesMemberCallsAlone() {
      String text = "obj.ab(1); o.p.ab(2); ab(3);";
      assertEquals("obj.ab(1); o.p.ab(2); cd(3);", rename(text));
   }

   @Test
   void anApostropheInACommentDoesNotHideARealCall() {
      assertEquals("// it's ab(1)\nx = cd(2);", rename("// it's ab(1)\nx = ab(2);"));
   }

   @Test
   void renamesWithoutNeedingADeclaration() {
      // a caller never declares the function, unlike the self rename
      assertEquals("function h(v) { return cd(v); }",
                   rename("function h(v) { return ab(v); }"));
   }

   @Test
   void toleratesNull() {
      assertEquals(null, LibManager.renameFunctionReferences("ab", "cd", null));
      assertEquals("x", LibManager.renameFunctionReferences(null, "cd", "x"));
   }
}
