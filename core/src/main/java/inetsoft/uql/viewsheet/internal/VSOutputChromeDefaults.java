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

import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSFormat;

import java.awt.Color;

/**
 * Supplies the modern KPI/control-chrome defaults for server-rendered output assemblies, gated by the
 * org modern-visualization setting plus the shared object-chrome toggle.
 *
 * Two surfaces so far:
 *  - Slider painter (VSSlider) chrome — track / handle / tick colors. Pure server-render constants with
 *    no user VSFormat behind them, so each accessor returns the legacy color gate-off (byte-identical)
 *    and the modern warm-neutral gate-on. The neutrals equal the live-view CSS (vs-slider.component.scss)
 *    so the exported slider and the live slider agree.
 *  - KPI text/output value defaults — foreground and border. applyModernDefaults substitutes the modern
 *    neutral on the DEFAULT tier of a clone at read time (live model build, export text draw), only when
 *    neither the user (USER tier) nor a format.css class (CSS tier) has set the value, so a user format
 *    and a customer format.css class both still win; a HighlightGroup emphasis, applied above on a
 *    higher tier, also still wins. Weight/size are intentionally not changed. Mirrors VSTitleChromeDefaults.
 *
 * Shares the viewsheet.modernObjectChrome gate with VSTitleChromeDefaults so one admin toggle covers all
 * object chrome; mirrors its gate shape.
 */
public final class VSOutputChromeDefaults {
   private VSOutputChromeDefaults() {
   }

   /**
    * Whether modern object chrome is active: the modern-visualization gate plus the shared object-chrome
    * toggle, which defaults on when modern is enabled. Matches VSTitleChromeDefaults' gate.
    */
   public static boolean isModern() {
      // default on when the modern gate is on; only an explicit "false" opts out
      return VSDensityDefaults.isModern() &&
         !"false".equals(SreeEnv.getProperty("viewsheet.modernObjectChrome", false, true));
   }

   // ── Slider painter chrome ──────────────────────────────────────────────────

   /** Slider inactive-track color — legacy mid-light gray, modern warm gridline neutral. */
   public static Color sliderInactiveTrack() {
      return isModern() ? SLIDER_INACTIVE_MODERN : SLIDER_INACTIVE_LEGACY;
   }

   /** Slider active (filled) track color — legacy mid gray, modern warm structural neutral. */
   public static Color sliderActiveTrack() {
      return isModern() ? SLIDER_ACTIVE_MODERN : SLIDER_ACTIVE_LEGACY;
   }

   /** Slider handle color — legacy mid gray, modern strong warm neutral. */
   public static Color sliderHandle() {
      return isModern() ? SLIDER_HANDLE_MODERN : SLIDER_HANDLE_LEGACY;
   }

   /** Slider tick-dot color — legacy ~38% black, modern strong warm neutral. */
   public static Color sliderTick() {
      return isModern() ? SLIDER_TICK_MODERN : SLIDER_TICK_LEGACY;
   }

   // ── KPI text/output value chrome ───────────────────────────────────────────

   /** Modern primary-value foreground — the strong warm neutral (equals the chart title color). */
   public static Color valueForeground() {
      return VALUE_FG;
   }

   /** Modern KPI/output border — the shared structural neutral (equals the title/table border). */
   public static Color valueBorderColor() {
      return VALUE_BORDER;
   }

   /**
    * Return an output value format with the modern value emphasis (foreground + border) substituted on
    * the DEFAULT tier of a clone, or the original unchanged (gate off, or already customized). Weight and
    * size are intentionally not changed. A user (USER tier) or format.css (CSS tier) foreground/border
    * still wins; the source format is never mutated or serialized. Mirrors VSTitleChromeDefaults.
    */
   public static VSCompositeFormat applyModernDefaults(VSCompositeFormat fmt) {
      if(!isModern() || fmt == null) {
         return fmt;
      }

      boolean fg = !isForegroundCustomized(fmt);
      boolean border = !isBorderCustomized(fmt);

      if(!fg && !border) {
         return fmt;
      }

      VSCompositeFormat clone = fmt.clone();
      applyTo(clone.getDefaultFormat(), fg, border);
      return clone;
   }

   /**
    * In-place variant for the export copy (the viewsheet / format is already cloned upstream): mutate the
    * DEFAULT tier directly. No-op when the gate is off or the value is already user / format.css
    * customized; never touches a persisted format.
    */
   public static void applyModernDefaultsInPlace(VSCompositeFormat fmt) {
      if(!isModern() || fmt == null) {
         return;
      }

      applyTo(fmt.getDefaultFormat(), !isForegroundCustomized(fmt), !isBorderCustomized(fmt));
   }

   private static void applyTo(VSFormat def, boolean fg, boolean border) {
      if(fg) {
         def.setForegroundValue(toValue(VALUE_FG));
      }

      if(border) {
         def.setBorderColorsValue(new BorderColors(VALUE_BORDER, VALUE_BORDER, VALUE_BORDER, VALUE_BORDER));
      }
   }

   // a value counts as customized (and is preserved) only when the user picker (USER tier) or a
   // format.css class (CSS tier) sets it; a bare default is modernized.
   private static boolean isForegroundCustomized(VSCompositeFormat f) {
      return f.getUserDefinedFormat().isForegroundValueDefined() ||
         f.getCSSFormat().isForegroundValueDefined();
   }

   private static boolean isBorderCustomized(VSCompositeFormat f) {
      return f.getUserDefinedFormat().isBorderColorsValueDefined() ||
         f.getCSSFormat().isBorderColorsValueDefined();
   }

   private static String toValue(Color c) {
      return String.format("0x%06x", c.getRGB() & 0xFFFFFF);
   }

   // legacy slider chrome — the pre-modern VSSlider constants, kept exact for gate-off parity
   private static final Color SLIDER_INACTIVE_LEGACY = new Color(224, 224, 224);
   private static final Color SLIDER_ACTIVE_LEGACY = new Color(158, 158, 158);
   private static final Color SLIDER_HANDLE_LEGACY = new Color(158, 158, 158);
   private static final Color SLIDER_TICK_LEGACY = new Color(0, 0, 0, 97); // ~38% opacity

   // modern warm-neutral chrome; light mode only, dark deferred. Coordinated with VSTitleChromeDefaults
   // and VSChartChromeDefaults so KPI value, title bar, table, and chart chrome read as one system.
   private static final Color SLIDER_INACTIVE_MODERN = new Color(0xE8E5DE);
   private static final Color SLIDER_ACTIVE_MODERN = new Color(0xC8C2B7);
   private static final Color SLIDER_HANDLE_MODERN = new Color(0x6A685F);
   private static final Color SLIDER_TICK_MODERN = new Color(0x6A685F);

   // value foreground = VSChartChromeDefaults.TITLE (strong text); border = VSTitleChromeDefaults border
   private static final Color VALUE_FG = new Color(0x35342F);
   private static final Color VALUE_BORDER = new Color(0xD9D5CC);
}
