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
import inetsoft.uql.viewsheet.graph.GraphTypes;

/**
 * The modern chart values that depend on the resolved chart type, one predicate per value.
 */
final class VSChartTypeDefaults {
   private VSChartTypeDefaults() {
   }

   /**
    * Whether a modern chart's value axis draws its line; non-rect types and Gantt keep theirs.
    */
   static boolean valueAxisLineVisible(ResolvedChartType t) {
      return !GraphTypes.isRect(t.type()) || GraphTypes.isGantt(t.type()) ||
         t.measureOnX() && t.measureOnY();
   }

   /**
    * Whether a modern chart shows the axis title; axis is one of x, x2, y and y2.
    */
   static boolean axisTitleVisible(ResolvedChartType t, String axis) {
      int type = t.type();

      if(GraphTypes.isPoint(type) || GraphTypes.isScatteredContour(type) ||
         GraphTypes.isPareto(type) || GraphTypes.isMekko(type))
      {
         return true;
      }

      if(!GraphTypes.isBoxplot(type)) {
         return false;
      }

      // a box plot names its value axis only, on X when it lies horizontally
      boolean xAxis = "x".equals(axis) || "x2".equals(axis);
      return xAxis == (t.measureOnX() && !t.measureOnY());
   }

   /**
    * The modern vertical grid style; a scatter with a measure on each axis gets a thin one.
    */
   static int xGridStyle(ResolvedChartType t) {
      boolean scatter = GraphTypes.isPoint(t.type()) || GraphTypes.isScatteredContour(t.type());
      return scatter && t.measureOnX() && t.measureOnY() ? StyleConstants.THIN_LINE :
         StyleConstants.NONE;
   }
}
