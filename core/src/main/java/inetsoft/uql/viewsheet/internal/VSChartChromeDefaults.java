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
      return GRIDLINE;
   }

   /** Legend border color — the same hairline neutral as the gridlines. */
   public static Color legendBorderColor() {
      return GRIDLINE;
   }

   /** Chrome label text color (axis tick labels, legend content) — quiet muted neutral. */
   public static Color labelColor() {
      return LABEL;
   }

   /** Chrome title text color (axis titles, legend title) — slightly stronger than labels. */
   public static Color titleColor() {
      return TITLE;
   }

   // modern warm-neutral chrome; light mode only, dark deferred. Warmer/subtler than the legacy
   // GDefaults #EEEEEE, and equal to VSTableStructureDefaults.gridlineColor().
   private static final Color GRIDLINE = new Color(0xE8E5DE);
   // label = shell muted text (= table headerForeground); title = shell default text. Quieter than
   // today's GDefaults #4B4B4B label / #2B2B2B title while staying legible.
   private static final Color LABEL = new Color(0x6A685F);
   private static final Color TITLE = new Color(0x35342F);
}
