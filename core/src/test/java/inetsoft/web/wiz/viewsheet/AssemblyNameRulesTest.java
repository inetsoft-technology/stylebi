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
package inetsoft.web.wiz.viewsheet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #78655: the Composer assembly-name rule, ported from the Angular validators. */
@Tag("core")
class AssemblyNameRulesTest {
   @Test
   void acceptsNamesTheComposerAccepts() {
      String[] valid = {
         "abc", "Sales Chart!", "_x", "#x", "@x", "a b", "x-1", "Q&A, 100%",
         "销售图", "ＡＢ", "it's", "a：b"
      };

      for(String name : valid) {
         assertNull(AssemblyNameRules.violation(name), name);
         assertTrue(AssemblyNameRules.isValid(name), name);
      }
   }

   @Test
   void rejectsNamesTheComposerRejects() {
      String[] invalid = {
         "a/b", "a.b", "a\\b", "a\bb","a\"b", "a(b)", "a:b", "a;b", "a<b", "a|b", "a[b", "a\tb",
         "1Chart", "9", "'q'", "''", "'", "   ", " ", "", "abc\n", "abc\n\n", "name：",
         "name？"
      };

      for(String name : invalid) {
         assertNotNull(AssemblyNameRules.violation(name), "[" + name + "]");
         assertFalse(AssemblyNameRules.isValid(name), "[" + name + "]");
      }

      assertNotNull(AssemblyNameRules.violation(null));
   }

   @Test
   void requireValidNamesTheOffendingNameAndTheRule() {
      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> AssemblyNameRules.requireValid("Edit op 'rename': 'newName'", "a/b"));

      assertTrue(thrown.getMessage().contains("\"a/b\""), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("not a valid assembly name"),
                 thrown.getMessage());
      assertTrue(thrown.getMessage().contains("must not start with a digit"),
                 thrown.getMessage());
      assertDoesNotThrow(() -> AssemblyNameRules.requireValid("ctx", "Sales Chart!"));
   }
}
