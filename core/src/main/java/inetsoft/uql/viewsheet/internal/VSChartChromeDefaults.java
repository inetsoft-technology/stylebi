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

import inetsoft.graph.internal.GDefaults;
import inetsoft.sree.SreeEnv;

import java.awt.Color;

/**
 * Supplies the modern in-graph chart-chrome palette for viewsheet charts, gated by the org
 * modern-visualization setting: gridline / facet-grid / legend-border colors, and the axis/legend
 * label and title text colors.
 *
 * The gridline and legend-border colors are written to the CSS tier of the descriptor CompositeValues
 * (in CSSChartStyles.apply) as a baseline that the format.css dictionary overrides and a user picker
 * (USER tier) beats; the CSS tier is not serialized, so this behaves as a default without dirtying
 * saved charts. The label/title text colors seed descriptor default formats. Mirrors
 * VSTableStructureDefaults' gate.
 */
public final class VSChartChromeDefaults {
   private VSChartChromeDefaults() {
   }

   /**
    * Whether modern chart chrome is active: the modern-visualization gate plus its chrome toggle,
    * which defaults on when modern is enabled.
    */
   public static boolean isModern() {
      // default on when the modern gate is on; only an explicit "false" opts out (there is no
      // default-value overload on getBooleanProperty, so read the raw property)
      return VSDensityDefaults.isModern() &&
         !"false".equals(SreeEnv.getProperty("viewsheet.modernChartChrome", false, true));
   }

   /** Interior gridline / facet-grid color — matches the table gridline so chrome reads as one system. */
   public static Color gridlineColor() {
      return VSDensityDefaults.isDark() ? GRIDLINE_DARK : GRIDLINE;
   }

   /** Legend border color — the same hairline neutral as the gridlines. */
   public static Color legendBorderColor() {
      return VSDensityDefaults.isDark() ? GRIDLINE_DARK : GRIDLINE;
   }

   /** Chrome label text color (axis tick labels, legend content) — quiet muted neutral. */
   public static Color labelColor() {
      return VSDensityDefaults.isDark() ? LABEL_DARK : LABEL;
   }

   /** Chrome title text color (axis titles, legend title) — slightly stronger than labels. */
   public static Color titleColor() {
      return VSDensityDefaults.isDark() ? TITLE_DARK : TITLE;
   }

   /**
    * Legend background default: a dark surface in dark mode so the light legend text stays legible;
    * white otherwise (light modern and legacy unchanged). Seeded only in the modern chart context,
    * mirroring the label/title color gate.
    */
   public static Color legendBackground() {
      return VSDensityDefaults.isDark() ? LEGEND_BG_DARK : Color.WHITE;
   }

   /**
    * Resolve an axis-line color: when the gate is on and the color is still the legacy default,
    * substitute the modern gridline neutral so the axis line unifies with the gridlines; otherwise
    * (a customer/user color, or gate off) leave it unchanged. Compared against the hardcoded fallback
    * so a format.css or user-picker color, which resolves to something else, is preserved.
    */
   public static Color resolveAxisLineColor(Color current) {
      return isModern() && GDefaults.DEFAULT_LINE_COLOR.equals(current)
         ? (VSDensityDefaults.isDark() ? GRIDLINE_DARK : GRIDLINE) : current;
   }

   /**
    * Resolve a gridline color for display: when the gate is on and the color is still the legacy
    * default, substitute the modern gridline neutral; otherwise (a customer/user color, or gate off)
    * leave it unchanged. Compared against the hardcoded fallback so a format.css or user-picker color
    * is preserved.
    */
   public static Color resolveGridlineColor(Color current) {
      return isModern() && GDefaults.DEFAULT_GRIDLINE_COLOR.equals(current)
         ? (VSDensityDefaults.isDark() ? GRIDLINE_DARK : GRIDLINE) : current;
   }

   /**
    * Resolve a legend-border color for display: modern neutral iff the gate is on and the color is
    * still the legacy default; otherwise unchanged.
    */
   public static Color resolveLegendBorderColor(Color current) {
      return isModern() && GDefaults.DEFAULT_LINE_COLOR.equals(current)
         ? (VSDensityDefaults.isDark() ? GRIDLINE_DARK : GRIDLINE) : current;
   }

   // modern warm-neutral chrome. Warmer/subtler than the legacy GDefaults #EEEEEE, and equal to
   // VSTableStructureDefaults.gridlineColor().
   private static final Color GRIDLINE = new Color(0xE8E5DE);
   // label = shell muted text (= table headerForeground); title = shell default text. Quieter than
   // today's GDefaults #4B4B4B label / #2B2B2B title while staying legible.
   private static final Color LABEL = new Color(0x6A685F);
   private static final Color TITLE = new Color(0x35342F);

   // dark chrome; gridline matches the table gridline, label/title lift for on-dark legibility
   private static final Color GRIDLINE_DARK = new Color(0x3A383D);
   private static final Color LABEL_DARK = new Color(0xCAC4D0);
   private static final Color TITLE_DARK = new Color(0xE6E0E9);
   // dark legend panel = --dark-surface-default, so it reads as part of the dark chart card
   private static final Color LEGEND_BG_DARK = new Color(0x252428);
}
