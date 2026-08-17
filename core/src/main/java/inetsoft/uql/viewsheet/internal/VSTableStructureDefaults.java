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

import java.awt.Color;

/**
 * Supplies the modern table-structure palette (interior gridline, header background/text, grand-total
 * background) for viewsheet table assemblies, gated by the org modern-visualization setting.
 *
 * The colors are overlaid onto a per-assembly clone of the shipped "Default Style" in
 * DataVSAQuery, so they behave as DEFAULTS: user cell/column/row formats merge on top and win, and a
 * table assigned a non-default style is left untouched.
 */
public final class VSTableStructureDefaults {
   private VSTableStructureDefaults() {
   }

   /** Interior gridline + outer border color (unifies the legacy #E6E6E6 body and #CCCCCC frame). */
   public static Color gridlineColor(VizContext ctx) {
      return ctx.dark ? GRIDLINE_DARK : GRIDLINE;
   }

   /** Header-row and header-column background. */
   public static Color headerBackground(VizContext ctx) {
      return ctx.dark ? HEADER_BG_DARK : HEADER_BG;
   }

   /** Header-row and header-column text color. */
   public static Color headerForeground(VizContext ctx) {
      return ctx.dark ? HEADER_FG_DARK : HEADER_FG;
   }

   /**
    * Header→body separator rule — stronger than the interior gridline for hierarchy. Equals the
    * shell/DOM structural border (#D9D5CC), so the viewsheet header rule matches the rest of the product.
    */
   public static Color headerSeparator(VizContext ctx) {
      return ctx.dark ? HEADER_SEPARATOR_DARK : HEADER_SEPARATOR;
   }

   /** Grand-total (trailer row/column) background. */
   public static Color totalBackground(VizContext ctx) {
      return ctx.dark ? TOTAL_BG_DARK : TOTAL_BG;
   }

   /** Interior group-subtotal background — lighter than the grand-total band for hierarchy. */
   public static Color subtotalBackground(VizContext ctx) {
      return ctx.dark ? SUBTOTAL_BG_DARK : SUBTOTAL_BG;
   }

   /**
    * Grand-total/subtotal band text color, or null to keep the shipped default. In light mode the
    * bands are light, so their default dark text stays legible and is left untouched (null); in dark
    * mode the bands are dark, so band text must lift to the header neutral to stay readable.
    */
   public static Color bandForeground(VizContext ctx) {
      return ctx.dark ? HEADER_FG_DARK : null;
   }

   /**
    * Data-cell text color, or null to keep the shipped default (#404040) in light/legacy. The shipped
    * Default Style hardcodes dark-gray body text, which is dark-on-dark once the interior darkens, so
    * dark mode lifts it to the strong light neutral (brighter than the muted header text).
    */
   public static Color bodyForeground(VizContext ctx) {
      return ctx.dark ? BODY_FG_DARK : null;
   }

   /**
    * Data-cell background for non-striped rows, or null to keep the shipped default (transparent) in
    * light/legacy. Dark mode fills it with the card surface so body cells are dark independent of the
    * object card (which only re-seeds for newly created tables).
    */
   public static Color bodyBackground(VizContext ctx) {
      return ctx.dark ? BODY_BG_DARK : null;
   }

   /**
    * Alternating-row (zebra) background, or null to keep the shipped light stripe (#F5F5F5) in
    * light/legacy. The zebra is a style Specification that wins over the body background, so dark mode
    * darkens it separately — a subtle lift above the base body so the stripe still reads.
    */
   public static Color zebraBackground(VizContext ctx) {
      return ctx.dark ? ZEBRA_BG_DARK : null;
   }

   // modern warm-neutral structure palette (light mode)
   private static final Color GRIDLINE = new Color(0xE8E5DE);
   private static final Color HEADER_SEPARATOR = new Color(0xD9D5CC);
   private static final Color HEADER_BG = new Color(0xF1EFEA);
   private static final Color HEADER_FG = new Color(0x6A685F);
   private static final Color TOTAL_BG = new Color(0xE9E4DA);
   private static final Color SUBTOTAL_BG = new Color(0xEEEAE1);

   // dark structure palette; total/subtotal bands lifted above the header for total hierarchy
   private static final Color GRIDLINE_DARK = new Color(0x3A383D);
   private static final Color HEADER_SEPARATOR_DARK = new Color(0x49454F);
   private static final Color HEADER_BG_DARK = new Color(0x2D2B30);
   private static final Color HEADER_FG_DARK = new Color(0xCAC4D0);
   private static final Color TOTAL_BG_DARK = new Color(0x35333A);
   private static final Color SUBTOTAL_BG_DARK = new Color(0x302E34);
   // dark data-cell interior: text = strong light neutral, base body = card surface, zebra = subtle lift
   private static final Color BODY_FG_DARK = new Color(0xE6E0E9);
   private static final Color BODY_BG_DARK = new Color(0x252428);
   private static final Color ZEBRA_BG_DARK = new Color(0x2D2B30);
}
