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

import inetsoft.graph.aesthetic.BluesColorFrame;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.*;

import java.util.List;

public class ContourScatterChartFilter extends ScatterChartFilter {
   public ContourScatterChartFilter(AssetEntry[] entries, VSChartInfo temp,
                                    List<List<ChartRef>> hgroup, boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(comb.getInsideCount() > 0) {
         return false;
      }

      return super.isValid(comb);
   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      VSChartInfo info = super.createChartInfo(comb);

      if(info != null) {
         info.setChartType(GraphTypes.CHART_SCATTER_CONTOUR);
         info.setColorFrame(new BluesColorFrame());
      }

      return info;
   }
}
