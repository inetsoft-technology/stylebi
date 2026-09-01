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
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSFormat;

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
 * does. Instead applyModernDefaults substitutes the modern neutral on the DEFAULT tier at read time,
 * for the types that have not yet converted to seeding their title chrome at creation. A
 * converted type is skipped here and carries its values in the stored format instead, so they
 * survive an asset export and resolve on bookmark restore. applyModernDefaults returns a clone
 * (never mutates the source); applyModernDefaultsInPlace mutates directly, for the export copy
 * where the viewsheet is already cloned.
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
    * The modern title foreground as a stored format value, for the creation seed. A seeded type is
    * skipped by the read-time substitution, so the colour has to be written rather than resolved.
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

   /**
    * Return a title format with the modern neutrals substituted, or the original format unchanged
    * (legacy context, or already customized). Applied to the DEFAULT tier of a clone, so the stored
    * format is never mutated or serialized and a user (USER tier) or format.css (CSS tier) title color still
    * wins. Substitution is keyed on whether the user / format.css has set the value — NOT on matching
    * a specific default color — because legacy title backgrounds vary by widget (white, #f5f5f5,
    * transparent); modern mode gives them all one consistent title bar.
    */
   public static VSCompositeFormat applyModernDefaults(VSCompositeFormat titleFmt, VizContext ctx) {
      return applyModernDefaults(titleFmt, ctx, null);
   }

   public static VSCompositeFormat applyModernDefaults(VSCompositeFormat titleFmt, VizContext ctx,
                                                        VSAssemblyInfo info)
   {
      if(!ctx.modern || titleFmt == null || isSeededTitle(info)) {
         return titleFmt;
      }

      boolean bg = !isBackgroundCustomized(titleFmt);
      boolean fg = !isForegroundCustomized(titleFmt);

      if(!bg && !fg) {
         return titleFmt;
      }

      VSCompositeFormat clone = titleFmt.clone();
      applyTo(clone.getDefaultFormat(), bg, fg, ctx);
      return clone;
   }

   /**
    * In-place variant for the export copy (the viewsheet is cloned before export): mutate the title
    * format's DEFAULT tier to the modern neutrals so every per-widget / per-format export title draw
    * resolves the modern chrome from the one shared format rather than needing each draw site wrapped.
    * No-op for a legacy context, or when the value is already user / format.css customized; never
    * touches a persisted format (export clones upstream).
    */
   public static void applyModernDefaultsInPlace(VSCompositeFormat titleFmt, VizContext ctx) {
      applyModernDefaultsInPlace(titleFmt, ctx, null);
   }

   public static void applyModernDefaultsInPlace(VSCompositeFormat titleFmt, VizContext ctx,
                                                  VSAssemblyInfo info)
   {
      if(!ctx.modern || titleFmt == null || isSeededTitle(info)) {
         return;
      }

      applyTo(titleFmt.getDefaultFormat(),
              !isBackgroundCustomized(titleFmt), !isForegroundCustomized(titleFmt), ctx);
   }

   private static void applyTo(VSFormat def, boolean bg, boolean fg, VizContext ctx) {
      boolean dark = ctx.dark;

      if(bg) {
         def.setBackgroundValue(toValue(dark ? TITLE_BG_DARK : TITLE_BG));
      }

      if(fg) {
         def.setForegroundValue(toValue(dark ? TITLE_FG_DARK : TITLE_FG));
      }
   }

   // these types carry their title chrome in the stored format, written by seedChromeDefaults at
   // creation; checkbox, radio button and calendar are still substituted here. When the last one
   // converts this is true for every titled type and both entry points, with all their call sites,
   // can go
   //
   // this skip is what makes a seeded value stick: FormatInfo.getFormat(TITLEPATH, false) still
   // copies the object format down onto the title's DEFAULT tier (copyDefaultFormat), each field
   // guarded by !tfmt.isXxxValueDefined() - a seeded value survives only because its setter marks
   // that field defined, setBackgroundValue(null) included. Borders survive because
   // copyDefaultFormat never copies them at all
   private static boolean isSeededTitle(VSAssemblyInfo info) {
      // TimeSliderVSAssemblyInfo is a sibling of SelectionBaseVSAssemblyInfo, not a subclass, so
      // it needs its own branch; CurrentSelectionVSAssemblyInfo is a container and shares neither
      return info instanceof ChartVSAssemblyInfo
         || info instanceof TableDataVSAssemblyInfo
         || info instanceof SelectionBaseVSAssemblyInfo
         || info instanceof TimeSliderVSAssemblyInfo
         || info instanceof CurrentSelectionVSAssemblyInfo;
   }

   // A title color counts as customized (and is preserved) only when the user picker (USER tier) or a
   // format.css TITLE class (CSS tier) sets it; a bare default — of any color — is modernized.
   private static boolean isBackgroundCustomized(VSCompositeFormat f) {
      return f.getUserDefinedFormat().isBackgroundValueDefined() ||
         f.getCSSFormat().isBackgroundValueDefined();
   }

   private static boolean isForegroundCustomized(VSCompositeFormat f) {
      return f.getUserDefinedFormat().isForegroundValueDefined() ||
         f.getCSSFormat().isForegroundValueDefined();
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
