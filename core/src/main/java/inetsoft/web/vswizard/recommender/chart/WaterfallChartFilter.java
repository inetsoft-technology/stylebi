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
package inetsoft.web.vswizard.recommender.chart;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.*;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;

public class WaterfallChartFilter extends BarChartFilter {
   public WaterfallChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                               boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * Waterfall/pareto chart only for simple binding:
    * 1. one dim on x and one measure on y, at most 1 aesthetic
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(dateDimCount > 1) {
         return false;
      }

      IntList x = comb.getX();
      IntList y = comb.getY();
      IntList inside = comb.getInside();

      return inside.size() == 0 && x.size() == 1 && y.size() == 1 &&
         getDimensionCount(x) == 1 && getMeasureCount(y) == 1;
   }

   @Override
   protected boolean shouldRotate(ChartRefCombination comb) {
      return false;
   }

   @Override
   protected int getChartType(ChartRefCombination comb) {
      return GraphTypes.CHART_WATERFALL;
   }

   @Override
   protected int getScore(ChartInfo chart) {
      return OTHER_SCORE;
   }
}
