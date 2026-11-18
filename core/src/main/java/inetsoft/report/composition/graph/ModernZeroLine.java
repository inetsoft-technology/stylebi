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

import inetsoft.graph.AxisSpec;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.internal.VizContext;

/**
 * The zero line of a value axis under the modern mark.
 */
final class ModernZeroLine {
   private ModernZeroLine() {
   }

   /**
    * Whether a chart type draws a zero line.
    */
   static boolean appliesTo(int type) {
      return (GraphTypes.isBar(type) && !GraphTypes.is3DBar(type)) ||
         GraphTypes.isInterval(type) || GraphTypes.isLine(type) ||
         GraphTypes.isStep(type) || GraphTypes.isArea(type) || GraphTypes.isStepArea(type) ||
         GraphTypes.isPoint(type) || GraphTypes.isScatteredContour(type) ||
         GraphTypes.isWaterfall(type) || GraphTypes.isPareto(type) ||
         GraphTypes.isCandle(type) || GraphTypes.isStock(type) || GraphTypes.isBoxplot(type);
   }

   /**
    * Turns the zero line on for a primary linear axis; any other axis keeps it off.
    */
   static void apply(AxisSpec spec, int type, Scale scale, boolean x, boolean secondary,
                     VizContext ctx)
   {
      if(!ctx.modern || secondary || !(scale instanceof LinearScale) || !appliesTo(type)) {
         return;
      }

      spec.setZeroLineColor(spec.getLineColor());
      spec.setZeroLineCrossing(x);
   }
}
