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

/**
 * Resolves whether chart plot areas render as inline SVG in the page DOM rather than as an
 * image. Inline SVG is what makes the chart interactions reachable — hover dimming, snap and
 * series dimming, and the card tooltip tail — so it follows the org modern-visualization gate.
 *
 * graph.svg.inline is an explicit override in both directions: set it to opt one org (or the
 * whole install) out while keeping modern chrome, or to turn inline SVG on with modern off.
 * Unlike the chrome sub-gates, which only ever switch a modern feature off, this one has to
 * support inline SVG without modern, so it is not an isModern() && != "false" expression.
 */
public final class VSChartInteractionDefaults {
   private VSChartInteractionDefaults() {
   }

   /**
    * Whether the chart plot area is delivered as inline SVG. An explicit graph.svg.inline wins;
    * unset follows the modern gate. Read org-scoped, so a per-org override resolves before the
    * global value.
    */
   public static boolean isInlineSvg() {
      String prop = SreeEnv.getProperty("graph.svg.inline", false, true);

      if(prop != null && !prop.isEmpty()) {
         return "true".equals(prop);
      }

      return VSDensityDefaults.isModern();
   }
}
