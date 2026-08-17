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

import inetsoft.uql.viewsheet.VSCompositeFormat;

import java.awt.Color;

/**
 * Supplies the modern object-chrome default colors (object-frame border + viewsheet page background),
 * gated by the org modern-visualization setting. Applied as DESIGN-TIME defaults: the gated seed is
 * written to the assembly's default format at creation (VSAssemblyInfo.setDefaultFormat and
 * ViewsheetVSAssemblyInfo.setDefaultFormat), so a new object created under the gate carries the modern
 * default (visible in the format editor, effective in the viewer and export). A user format or a
 * format.css class still overrides it via the normal USER > CSS > DEFAULT tier precedence.
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

   /** Object-card corner radius default, in pixels. Matches the annotation-rectangle radius. */
   public static int cardCornerRadius() {
      return CARD_CORNER_RADIUS;
   }

   /**
    * Gate-strip a DEFAULT-tier corner radius: when the value is our seed and the gate is off, the
    * object reverts to square. Keyed on exact equality with the seed so a format.css TableStyle radius
    * written to the same tier survives. Callers must pass only DEFAULT-tier values; VSCompositeFormat
    * additionally exempts tab formats, whose default tier can hold a laundered user radius.
    */
   public static int resolveSeededCorner(int radius) {
      // reads the gate directly: the only caller is a VSFormat getter with no context to hand
      return radius == CARD_CORNER_RADIUS && !VSDensityDefaults.isModern() ? 0 : radius;
   }

   /**
    * Dark-mode light text color as a CSS hex string, or null when not in dark mode. For object text
    * whose default is a fixed dark color (black) and would be dark-on-dark otherwise; callers apply it
    * only when the user/CSS has not set a foreground. Must be applied server-side so exports match.
    */
   public static String textForegroundCss(VizContext ctx) {
      return ctx.dark ? String.format("#%06x", TEXT_FG_DARK.getRGB() & 0xFFFFFF) : null;
   }

   /**
    * Return the given object format with a light text foreground substituted on the DEFAULT tier of a
    * clone in dark mode, or the original unchanged (not dark, or already user/CSS customized). Lets a
    * bare-default object text (fixed black) stay legible on the dark canvas; a user or format.css color
    * still wins. Never mutates the source. Applied at both live model build and the export painter.
    */
   public static VSCompositeFormat applyDarkForeground(VSCompositeFormat fmt, VizContext ctx) {
      if(!ctx.dark || fmt == null) {
         return fmt;
      }

      if(fmt.getUserDefinedFormat().isForegroundValueDefined() ||
         fmt.getCSSFormat().isForegroundValueDefined())
      {
         return fmt;
      }

      VSCompositeFormat clone = fmt.clone();
      clone.getDefaultFormat().setForegroundValue(
         String.format("0x%06x", TEXT_FG_DARK.getRGB() & 0xFFFFFF));
      return clone;
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

   // modern object-card corner radius, px; = the annotation-rectangle radius
   private static final int CARD_CORNER_RADIUS = 12;
}
