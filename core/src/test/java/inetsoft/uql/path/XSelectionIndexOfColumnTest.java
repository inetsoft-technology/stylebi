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
package inetsoft.uql.path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80138: {@code XSelection.indexOfColumn}'s {@code indexmap} cache was keyed by {@code name}
 * alone, ignoring the {@code upperCasedAlias}/{@code ignoreCase} flags, and (in the suffix-match
 * branch) keyed by a {@code name} that had already been mutated to upper case before being
 * cached. Either defect lets one call's cached result leak into an unrelated call that differs
 * only in those flags, or in literal string case. {@code setAlias} also never invalidated the
 * cache, so a stale alias-dependent lookup survived an alias change.
 */
@Tag("core")
class XSelectionIndexOfColumnTest {
   @Test
   void caseSensitiveMissDoesNotPoisonIgnoreCaseLookup() {
      XSelection selection = new XSelection();
      selection.addColumn("NAME");

      assertEquals(-1, selection.indexOfColumn("name"));
      assertEquals(0, selection.indexOfColumn("name", false, true));
   }

   @Test
   void ignoreCaseSuffixMatchDoesNotPoisonUnrelatedCaseSensitiveLookup() {
      XSelection selection = new XSelection();
      selection.addColumn("FOO.name");

      assertEquals(0, selection.indexOfColumn("Name", false, true));
      assertEquals(-1, selection.indexOfColumn("NAME", false, false));
   }

   @Test
   void setAliasInvalidatesCachedAliasLookup() {
      XSelection selection = new XSelection();
      selection.addColumn("col1");
      selection.setAlias(0, "oldAlias");

      assertEquals(0, selection.indexOfColumn("oldAlias"));

      selection.setAlias(0, "newAlias");

      assertEquals(-1, selection.indexOfColumn("oldAlias"));
      assertEquals(0, selection.indexOfColumn("newAlias"));
   }
}
