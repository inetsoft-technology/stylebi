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
 * Supplies the modern in-graph chart-chrome palette (gridline, facet grid, legend border) for
 * viewsheet charts, gated by the org modern-visualization setting.
 *
 * The colors are written to the CSS tier of the descriptor CompositeValues in CSSChartStyles.apply,
 * as a baseline the format.css dictionary overrides and a user picker (USER tier) beats. The CSS tier
 * is not serialized, so this behaves as a default without dirtying saved charts. Mirrors
 * VSTableStructureDefaults' gate. Text colors (B2) and the plain-Color axis line (B3) are separate.
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

   // modern warm-neutral chrome; light mode only, dark deferred. Warmer/subtler than the legacy
   // GDefaults #EEEEEE, and equal to VSTableStructureDefaults.gridlineColor().
   private static final Color GRIDLINE = new Color(0xE8E5DE);
}
