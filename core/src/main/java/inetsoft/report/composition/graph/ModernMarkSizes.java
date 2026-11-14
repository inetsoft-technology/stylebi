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
package inetsoft.report.composition.graph;

import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.uql.viewsheet.graph.GraphTypes;

/**
 * The default mark sizes of a chart under the modern mark.
 */
final class ModernMarkSizes {
   static final double BUBBLE_LARGEST = 8;
   static final double EDGE_LARGEST = 6;
   static final double MILESTONE_SIZE = 3;
   static final double RADAR_LINE_SIZE = 3;

   private ModernMarkSizes() {
   }

   // the largest size of the size frame; word clouds and contours keep theirs
   static double largest(int chartType, boolean wordCloud, boolean contour, double classic) {
      if(GraphTypes.isPoint(chartType) && !wordCloud && !contour ||
         chartType == GraphTypes.CHART_MAP)
      {
         return BUBBLE_LARGEST;
      }

      switch(chartType) {
      case GraphTypes.CHART_TREE:
      case GraphTypes.CHART_NETWORK:
      case GraphTypes.CHART_CIRCULAR:
         return EDGE_LARGEST;
      default:
         return classic;
      }
   }

   // the default of a static size frame
   static double staticSize(int chartType, GraphElement elem, double classic) {
      if(chartType == GraphTypes.CHART_GANTT && !(elem instanceof IntervalElement)) {
         return MILESTONE_SIZE;
      }

      if(chartType == GraphTypes.CHART_RADAR || chartType == GraphTypes.CHART_FILL_RADAR) {
         return RADAR_LINE_SIZE;
      }

      return classic;
   }
}
