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
package inetsoft.uql.viewsheet.internal;

import java.awt.Color;
import java.awt.Insets;

/**
 * Supplies the modern object-chrome default colors (object-frame border + viewsheet page background)
 * for the VizContext it is handed. Applied as DESIGN-TIME defaults: the seed is written to the
 * assembly's default format at creation (VSAssemblyInfo.setDefaultFormat and
 * ViewsheetVSAssemblyInfo.setDefaultFormat), so an object created modern carries the modern default
 * (visible in the format editor, effective in the viewer and export). A user format or a format.css
 * class still overrides it via the normal USER > CSS > DEFAULT tier precedence.
 */
public final class VSObjectChromeDefaults {
   private VSObjectChromeDefaults() {
   }

   /** Object-frame border default — the shared structural neutral (= --border-default), dark in dark mode. */
   public static Color objectBorderColor(VizContext ctx) {
      return ctx.dark ? OBJECT_BORDER_DARK : OBJECT_BORDER;
   }

   /** Viewsheet page/canvas background default, as a CSS hex string (= --surface-canvas), dark in dark mode. */
   public static String pageBackgroundCss(VizContext ctx) {
      Color bg = ctx.dark ? PAGE_BG_DARK : PAGE_BG;
      return String.format("#%06x", bg.getRGB() & 0xFFFFFF);
   }

   /**
    * Object-card background default as a CSS hex string. White in legacy and light modern (modern
    * keeps white cards); a lifted dark surface in dark mode so light chart/output chrome stays legible
    * on the card (= --dark-surface-default, one step above the darker page).
    */
   public static String cardBackgroundCss(VizContext ctx) {
      Color bg = ctx.dark ? CARD_BG_DARK : CARD_BG;
      return String.format("#%06x", bg.getRGB() & 0xFFFFFF);
   }

   /** Object-card corner radius default, in pixels; the top step of the DOM radius scale. */
   public static int cardCornerRadius() {
      return CARD_CORNER_RADIUS;
   }

   /**
    * The chart's creation-default inset, which is what the card-inset resolver treats as "no
    * opinion" and what a dialog stores when its author hands the padding back to the default. A
    * fresh object every call: Insets is mutable and the constant must not escape by reference.
    */
   public static Insets legacyChartPadding() {
      return new Insets(LEGACY_CHART_PADDING.top, LEGACY_CHART_PADDING.left,
                        LEGACY_CHART_PADDING.bottom, LEGACY_CHART_PADDING.right);
   }

   /**
    * The modern card inset, seeded at creation. A fresh object every call: Insets is mutable and the
    * constant must not escape by reference.
    */
   public static Insets modernChartPadding() {
      return new Insets(MODERN_CARD_INSET, MODERN_CARD_INSET, MODERN_CARD_INSET,
                        MODERN_CARD_INSET);
   }

   /**
    * The dark-mode light text value. Public because the selection-cell and slider seeds write it
    * into a stored DEFAULT tier at creation rather than substituting it at read time.
    */
   public static String darkForegroundValue() {
      return String.format("0x%06x", TEXT_FG_DARK.getRGB() & 0xFFFFFF);
   }

   /**
    * The cell foreground a gate-off creation writes, and what the legacy branch of the seed
    * restores. Excel's list and tree exporters substitute it back over a seeded dark value: their
    * cells are unfilled white, so the light neutral would be invisible there.
    */
   public static String legacyCellForegroundValue() {
      return String.format("0x%06x", LEGACY_CELL_FG.getRGB() & 0xFFFFFF);
   }

   // modern warm-neutral object chrome (light mode)
   private static final Color OBJECT_BORDER = new Color(0xD9D5CC);
   private static final Color PAGE_BG = new Color(0xF8F7F4);
   private static final Color CARD_BG = new Color(0xFFFFFF);

   // dark object chrome; page = --dark-surface-canvas, card = --dark-surface-default, border = --dark-border-default
   private static final Color OBJECT_BORDER_DARK = new Color(0x49454F);
   private static final Color PAGE_BG_DARK = new Color(0x1C1B1F);
   private static final Color CARD_BG_DARK = new Color(0x252428);
   // dark object text = strong light neutral (matches table body / calendar text)
   private static final Color TEXT_FG_DARK = new Color(0xE6E0E9);
   // the selection cell's creation default, and Excel's deliberate exception
   private static final Color LEGACY_CELL_FG = new Color(0x2b2b2b);

   // modern object-card corner radius, px; = --inet-radius-xl, the DOM scale's top step
   private static final int CARD_CORNER_RADIUS = 6;

   // the chart's creation-default padding (ChartVSAssemblyInfo.setDefaultFormat), which is what the
   // card inset resolver treats as "no opinion"
   private static final Insets LEGACY_CHART_PADDING = new Insets(10, 10, 10, 10);
   // modern card inset, px; = --inet-space-5. One value governs all four edges: the title lane, the
   // axis title and the legend column add no edge padding of their own.
   private static final int MODERN_CARD_INSET = 12;
}
