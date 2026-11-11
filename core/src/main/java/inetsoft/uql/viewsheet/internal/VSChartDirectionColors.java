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

import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;

/**
 * When a candle, stock or waterfall draws its up, down and total colours instead of a single
 * colour.
 */
public final class VSChartDirectionColors {
   private VSChartDirectionColors() {
   }

   public static boolean isDirectionType(int type) {
      return GraphTypes.isCandle(type) || GraphTypes.isStock(type) || GraphTypes.isWaterfall(type);
   }

   /**
    * True for a marked chart of a direction type with no colour field and no author or
    * stylesheet single colour; a stock chart also needs its Open field, a waterfall one measure.
    */
   public static boolean draws(VizContext ctx, ChartInfo info) {
      if(ctx == null || !ctx.modern || info == null) {
         return false;
      }

      int type = ResolvedChartType.of(info).type();

      if(!isDirectionType(type) || info.getColorField() != null) {
         return false;
      }

      ColorFrameWrapper single;

      if(GraphTypes.isWaterfall(type)) {
         // the frame replaces the chart's, which a second measure would read too
         ChartAggregateRef measure = onlyMeasure(info);

         if(measure == null || measure.getColorField() != null) {
            return false;
         }

         single = measure.getColorFrameWrapper();
      }
      else {
         single = info.getColorFrameWrapper();

         if(GraphTypes.isStock(type) &&
            (!(info instanceof CandleChartInfo) || ((CandleChartInfo) info).getOpenField() == null))
         {
            return false;
         }
      }

      if(!(single instanceof StaticColorFrameWrapper)) {
         return false;
      }

      StaticColorFrameWrapper wrapper = (StaticColorFrameWrapper) single;

      if(wrapper.getUserColor() != null) {
         return false;
      }

      return !(wrapper.getVisualFrame() instanceof StaticColorFrame) ||
         ((StaticColorFrame) wrapper.getVisualFrame()).getCssColor() == null;
   }

   // the chart's measure, or null when it has none or more than one
   private static ChartAggregateRef onlyMeasure(ChartInfo info) {
      ChartAggregateRef only = null;

      for(ChartRef ref : info.getModelRefs(false)) {
         if(ref instanceof ChartAggregateRef) {
            if(only != null) {
               return null;
            }

            only = (ChartAggregateRef) ref;
         }
      }

      return only;
   }
}
