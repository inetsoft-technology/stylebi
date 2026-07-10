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
package inetsoft.uql.viewsheet.internal;

import org.junit.jupiter.api.*;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class VSTableStructureDefaultsTest {
   @Test
   void bodyGridlineRemappedToModern() {
      assertEquals(MODERN_GRIDLINE, rgb(VSTableStructureDefaults.modernGridline(new Color(LEGACY_GRIDLINE))));
   }

   @Test
   void outerAndHeaderBorderRemappedToModern() {
      // #CCCCCC (header separator + outer frame) unifies onto the same gridline token
      assertEquals(MODERN_GRIDLINE, rgb(VSTableStructureDefaults.modernGridline(new Color(LEGACY_BORDER))));
   }

   @Test
   void nonDefaultBorderLeftUntouched() {
      Color custom = new Color(0x123456);
      assertSame(custom, VSTableStructureDefaults.modernGridline(custom));
   }

   @Test
   void nullBorderLeftUntouched() {
      assertNull(VSTableStructureDefaults.modernGridline(null));
   }

   @Test
   void headerBackgroundRemapped() {
      assertEquals(MODERN_HEADER_BG,
                   rgb(VSTableStructureDefaults.modernHeaderBackground(new Color(LEGACY_HEADER_BG))));
   }

   @Test
   void headerForegroundRemapped() {
      assertEquals(MODERN_HEADER_FG,
                   rgb(VSTableStructureDefaults.modernHeaderForeground(new Color(LEGACY_HEADER_FG))));
   }

   @Test
   void nonDefaultHeaderColorsLeftUntouched() {
      Color bg = new Color(0x223344);
      Color fg = new Color(0x556677);
      assertSame(bg, VSTableStructureDefaults.modernHeaderBackground(bg));
      assertSame(fg, VSTableStructureDefaults.modernHeaderForeground(fg));
   }

   @Test
   void headerBackgroundIsNotTreatedAsGridlineAndViceVersa() {
      // white is a header-bg default, not a gridline default; the gridline default is not a header bg
      assertSame(WHITE, VSTableStructureDefaults.modernGridline(WHITE));
      Color gridline = new Color(LEGACY_GRIDLINE);
      assertSame(gridline, VSTableStructureDefaults.modernHeaderBackground(gridline));
   }

   private static int rgb(Color c) {
      return c.getRGB() & 0xFFFFFF;
   }

   private static final Color WHITE = new Color(0xFFFFFF);
   private static final int LEGACY_GRIDLINE = 0xE6E6E6;
   private static final int LEGACY_BORDER = 0xCCCCCC;
   private static final int LEGACY_HEADER_BG = 0xFFFFFF;
   private static final int LEGACY_HEADER_FG = 0x404040;
   private static final int MODERN_GRIDLINE = 0xE8E5DE;
   private static final int MODERN_HEADER_BG = 0xF1EFEA;
   private static final int MODERN_HEADER_FG = 0x6A685F;
}
