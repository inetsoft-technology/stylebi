/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.composition.execution;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #82470: a cube member name containing "]" (escaped "]]" in the MDX unique name)
 * was displayed with a doubled bracket.
 */
@Tag("core")
class VSCubeTableLensDisplayValueTest {
   private static String show(String s) {
      return assertDoesNotThrow(() -> VSCubeTableLens.getDisplayValue(s));
   }

   @Test
   void escapedBracketInMondrianUniqueName() {
      assertEquals("Widget [Pro]", show("[Product].[Tools].[Widget [Pro]]]"));
   }

   @Test
   void escapedBracketInSqlServerStrippedUniqueName() {
      assertEquals("Widget [Pro]", show("[Tools].[Widget [Pro]]]"));
      assertEquals("Widget [Pro]", show("[Widget [Pro]]]"));
   }

   @Test
   void literalBracketRuns() {
      assertEquals("x]", show("[x]]]"));
      assertEquals("x]]", show("[Product].[Tools].[x]]]]]"));
      assertEquals("a]b", show("[Product].[Tools].[a]]b]"));
      assertEquals("a.[b]", show("[Product].[Tools].[a.[b]]]"));
   }

   @Test
   void namesWithoutBracketsAreUnchanged() {
      assertEquals("Widget", show("[Product].[Tools].[Widget]"));
      assertEquals("O'Brien's Tea", show("[Product].[Tools].[O'Brien's Tea]"));
      assertEquals("Salt & Pepper", show("[Product].[Food].[Salt & Pepper]"));
      assertEquals("Dot.Name, Inc.", show("[Product].[Food].[Dot.Name, Inc.]"));
      assertEquals("B", show("[A].[B]"));
      assertEquals("B", show("[B]"));
      assertEquals("B", show("[A] [B]"));
      assertEquals("plain", show("plain"));
      assertEquals("", show(""));
   }

   @Test
   void legacyBehaviorKeptForOddStrings() {
      // caption-only string, no "]]": old behavior
      assertEquals("Pro", show("Widget [Pro]"));
      // contains "]]" but is not wholly a bracketed unique name: falls back, never throws
      assertEquals("[B", show("[A]].[B"));
      assertEquals("Dim [A] [B]]", show("[Product].[Dim [A] [B]]]"));
      assertEquals("x]", show("[x]]"));
   }
}
