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

import inetsoft.report.StyleConstants;
import inetsoft.uql.viewsheet.BorderColors;

import java.awt.Color;
import java.awt.Insets;

/**
 * Supplies the modern object-title-bar palette (background / foreground / bottom-border) for titled
 * viewsheet assemblies, for the VizContext it is handed.
 *
 * The title bar is rendered from the server title VSFormat (getFormatInfo().getFormat(TITLEPATH))
 * with no browser CSS hook and is re-drawn in export, so it is server-owned. Unlike chart chrome, the
 * title VSFormat's CSS tier is dictionary-backed (VSCSSFormat reads format.css on demand) and has no
 * writable slot, so the modern default cannot be written to the CSS tier the way VSChartChromeDefaults
 * does. This class is a pure palette supplier now, not a read-time substituter: every titled type
 * seeds its own chrome lane at creation, writing these colours into the stored format directly, so
 * they survive an asset export and resolve on bookmark restore without any resolution step at read
 * time.
 */
public final class VSTitleChromeDefaults {
   private VSTitleChromeDefaults() {
   }

   /** Title-bar background — quiet warm neutral, equal to the table header background so chrome reads as one system; dark neutral when dark mode is on. */
   public static Color titleBackground(VizContext ctx) {
      return ctx.dark ? TITLE_BG_DARK : TITLE_BG;
   }

   /** Title-bar text color — muted, equal to the table header / chart label foreground; dark neutral when dark mode is on. */
   public static Color titleForeground(VizContext ctx) {
      return ctx.dark ? TITLE_FG_DARK : TITLE_FG;
   }

   /** Title→body bottom-border color — the shared structural border (matches the table header rule); dark neutral when dark mode is on. */
   public static Color titleBorderColor(VizContext ctx) {
      return ctx.dark ? TITLE_BORDER_DARK : TITLE_BORDER;
   }

   /**
    * The modern title lane's borders: a bottom rule only. A fresh Insets every call, because
    * Insets is mutable and the caller installs it on a format.
    */
   public static Insets titleRuleBorders() {
      return new Insets(StyleConstants.NONE, StyleConstants.NONE,
                        StyleConstants.THIN_LINE, StyleConstants.NONE);
   }

   /**
    * The modern title foreground as a stored format value, for the creation seed.
    */
   public static String titleForegroundValue(VizContext ctx) {
      return toValue(titleForeground(ctx));
   }

   /**
    * The rule's colour on all four sides, though only the bottom carries a width. A report text
    * box keeps one border colour and discards the rest, so one colour four times is what makes
    * that setter lossless here.
    */
   public static BorderColors titleRuleColors(VizContext ctx) {
      Color c = titleBorderColor(ctx);
      return new BorderColors(c, c, c, c);
   }

   private static String toValue(Color c) {
      return String.format("0x%06x", c.getRGB() & 0xFFFFFF);
   }

   // modern warm-neutral title chrome. Coordinated with VSTableStructureDefaults (header
   // background/foreground/separator) and VSChartChromeDefaults so the title bar, table header, and
   // chart chrome read as one warm-neutral system.
   private static final Color TITLE_BG = new Color(0xF1EFEA);
   private static final Color TITLE_FG = new Color(0x6A685F);
   private static final Color TITLE_BORDER = new Color(0xD9D5CC);

   // dark title chrome; coordinated with the table header and chart chrome dark palette
   private static final Color TITLE_BG_DARK = new Color(0x2D2B30);
   private static final Color TITLE_FG_DARK = new Color(0xCAC4D0);
   private static final Color TITLE_BORDER_DARK = new Color(0x49454F);
}
