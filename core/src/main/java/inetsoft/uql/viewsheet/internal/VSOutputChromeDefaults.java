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

/**
 * Supplies the modern KPI/control-chrome defaults for server-rendered output assemblies, for the
 * VizContext it is handed.
 *
 * Two surfaces so far:
 *  - Slider painter (VSSlider) chrome — track / handle / tick colors. Pure server-render constants with
 *    no user VSFormat behind them, so each accessor returns the legacy color for a legacy context
 *    (byte-identical) and the modern warm-neutral for a modern one. The neutrals equal the live-view
 *    CSS (vs-slider.component.scss)
 *    so the exported slider and the live slider agree.
 *  - KPI text/output value defaults — foreground and border. These are seeded onto the DEFAULT tier
 *    at creation time (TextVSAssemblyInfo.seedChromeDefaults), not resolved at read time, so a user
 *    format (USER tier) or a format.css class (CSS tier) still outranks the seed by ordinary tier
 *    precedence; a HighlightGroup emphasis, applied above on a higher tier, also still wins.
 *    Weight/size are intentionally not changed. Mirrors VSTitleChromeDefaults.
 */
public final class VSOutputChromeDefaults {
   private VSOutputChromeDefaults() {
   }

   // ── Slider painter chrome ──────────────────────────────────────────────────

   /** Slider inactive-track color — legacy mid-light gray, modern warm gridline neutral, dark neutral when dark mode is on. */
   public static Color sliderInactiveTrack(VizContext ctx) {
      return ctx.dark ? SLIDER_INACTIVE_DARK
         : ctx.modern ? SLIDER_INACTIVE_MODERN : SLIDER_INACTIVE_LEGACY;
   }

   /** Slider active (filled) track color — legacy mid gray, modern warm structural neutral, dark neutral when dark mode is on. */
   public static Color sliderActiveTrack(VizContext ctx) {
      return ctx.dark ? SLIDER_ACTIVE_DARK
         : ctx.modern ? SLIDER_ACTIVE_MODERN : SLIDER_ACTIVE_LEGACY;
   }

   /** Slider handle color — legacy mid gray, modern strong warm neutral, dark neutral when dark mode is on. */
   public static Color sliderHandle(VizContext ctx) {
      return ctx.dark ? SLIDER_HANDLE_DARK
         : ctx.modern ? SLIDER_HANDLE_MODERN : SLIDER_HANDLE_LEGACY;
   }

   /** Slider tick-dot color — legacy ~38% black, modern strong warm neutral, dark neutral when dark mode is on. */
   public static Color sliderTick(VizContext ctx) {
      return ctx.dark ? SLIDER_TICK_DARK
         : ctx.modern ? SLIDER_TICK_MODERN : SLIDER_TICK_LEGACY;
   }

   // ── KPI text/output value chrome ───────────────────────────────────────────

   /** Modern primary-value foreground — the strong warm neutral (equals the chart title color), dark neutral when dark mode is on. */
   public static Color valueForeground(VizContext ctx) {
      return ctx.dark ? VALUE_FG_DARK : VALUE_FG;
   }

   /** Modern KPI/output border — the shared structural neutral (equals the title/table border), dark neutral when dark mode is on. */
   public static Color valueBorderColor(VizContext ctx) {
      return ctx.dark ? VALUE_BORDER_DARK : VALUE_BORDER;
   }

   /**
    * The value emphasis foreground as a stored-format hex string: valueForeground(ctx) for a
    * modern context, the legacy near-black otherwise - exactly the ternary
    * TextVSAssemblyInfo.seedChromeDefaults writes. A single supplier so the seed and its test call
    * the same code rather than each formatting the color separately, which is what let the two
    * drift apart before.
    */
   public static String valueForegroundValue(VizContext ctx) {
      return ctx.modern ? toValue(valueForeground(ctx)) : LEGACY_VALUE_FG_VALUE;
   }

   private static String toValue(Color c) {
      return String.format("0x%06x", c.getRGB() & 0xFFFFFF);
   }

   // legacy slider chrome — the pre-modern VSSlider constants, kept exact for non-modern parity
   private static final Color SLIDER_INACTIVE_LEGACY = new Color(224, 224, 224);
   private static final Color SLIDER_ACTIVE_LEGACY = new Color(158, 158, 158);
   private static final Color SLIDER_HANDLE_LEGACY = new Color(158, 158, 158);
   private static final Color SLIDER_TICK_LEGACY = new Color(0, 0, 0, 97); // ~38% opacity

   // modern warm-neutral chrome; coordinated with VSTitleChromeDefaults and VSChartChromeDefaults so
   // KPI value, title bar, table, and chart chrome read as one system.
   private static final Color SLIDER_INACTIVE_MODERN = new Color(0xE8E5DE);
   private static final Color SLIDER_ACTIVE_MODERN = new Color(0xC8C2B7);
   private static final Color SLIDER_HANDLE_MODERN = new Color(0x6A685F);
   private static final Color SLIDER_TICK_MODERN = new Color(0x6A685F);

   // value foreground = VSChartChromeDefaults.TITLE (strong text); border = VSTitleChromeDefaults border
   private static final Color VALUE_FG = new Color(0x35342F);
   private static final Color VALUE_BORDER = new Color(0xD9D5CC);

   // the legacy value foreground: the near-black TextVSAssemblyInfo's setDefaultFormat has always
   // written, kept as the exact string rather than round-tripped through a Color
   private static final String LEGACY_VALUE_FG_VALUE = "0x2b2b2b";

   // dark KPI/slider chrome; neutrals track the shared dark structure palette
   private static final Color SLIDER_INACTIVE_DARK = new Color(0x3A383D);
   private static final Color SLIDER_ACTIVE_DARK = new Color(0x49454F);
   private static final Color SLIDER_HANDLE_DARK = new Color(0xCAC4D0);
   private static final Color SLIDER_TICK_DARK = new Color(0xCAC4D0);
   private static final Color VALUE_FG_DARK = new Color(0xE6E0E9);
   private static final Color VALUE_BORDER_DARK = new Color(0x49454F);
}
