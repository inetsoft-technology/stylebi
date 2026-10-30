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
package inetsoft.report.internal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #79830: renaming a column must rewrite the member read field['old'] inside a lookup-key
 * string literal, but not the bare name, which on the left of the condition is a column of the
 * looked-up table.
 */
@Tag("core")
class UtilRenameScriptRef79830Test {
   @Test
   void lookupKeyKeepsTheLookedUpTablesOwnColumn() {
      String script = "CATEGORIES[\"CATEGORY_NAME? CATEGORY_ID==field['CATEGORY_ID']\"]";

      assertEquals("CATEGORIES[\"CATEGORY_NAME? CATEGORY_ID==field['CID_X']\"]",
                   Util.renameScriptRefDepended("CATEGORY_ID", "CID_X", script));
   }

   @Test
   void everyMemberReadInsideOneLiteralIsRewritten() {
      String script = "T[\"N? A==field['A'] && B==field['A']\"]";

      assertEquals("T[\"N? A==field['X'] && B==field['X']\"]",
                   Util.renameScriptRefDepended("A", "X", script));
   }

   @Test
   void plainFieldReadStillRenamed() {
      assertEquals("field['X'] + 1", Util.renameScriptRefDepended("A", "X", "field['A'] + 1"));
   }

   @Test
   void literalWithoutAMemberReadOfTheNameIsUntouched() {
      String script = "T[\"N? A==1\"]";

      assertEquals(script, Util.renameScriptRefDepended("A", "X", script));
   }
}
