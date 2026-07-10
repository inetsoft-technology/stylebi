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

import inetsoft.sree.SreeEnv;

import java.awt.Color;

/**
 * Supplies the modern table-structure palette (interior gridline, header background/text, grand-total
 * background) for viewsheet table assemblies, gated by the org modern-visualization setting.
 *
 * The colors are overlaid onto a per-assembly clone of the shipped "Default Style" in
 * DataVSAQuery, so they behave as DEFAULTS: user cell/column/row formats merge on top and win, and a
 * table assigned a non-default style is left untouched. Mirrors VSDensityDefaults' gate.
 */
public final class VSTableStructureDefaults {
   private VSTableStructureDefaults() {
   }

   /**
    * Whether modern table structure is active: the modern-visualization gate plus its structure
    * toggle, which defaults on when modern is enabled.
    */
   public static boolean isModern() {
      // default on when the modern gate is on; only an explicit "false" opts out (there is no
      // default-value overload on getBooleanProperty, so read the raw property)
      return VSDensityDefaults.isModern() &&
         !"false".equals(SreeEnv.getProperty("viewsheet.modernTableStructure", false, true));
   }

   /** Interior gridline + outer border color (unifies the legacy #E6E6E6 body and #CCCCCC frame). */
   public static Color gridlineColor() {
      return GRIDLINE;
   }

   /** Header-row and header-column background. */
   public static Color headerBackground() {
      return HEADER_BG;
   }

   /** Header-row and header-column text color. */
   public static Color headerForeground() {
      return HEADER_FG;
   }

   /** Grand-total (trailer row/column) background. */
   public static Color totalBackground() {
      return TOTAL_BG;
   }

   // modern warm-neutral structure palette; light mode only, dark deferred
   private static final Color GRIDLINE = new Color(0xE8E5DE);
   private static final Color HEADER_BG = new Color(0xF1EFEA);
   private static final Color HEADER_FG = new Color(0x6A685F);
   private static final Color TOTAL_BG = new Color(0xE9E4DA);
}
