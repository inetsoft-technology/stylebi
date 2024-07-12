/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.vswizard.recommender.chart;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.*;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;

public class AreaChartFilter extends LineChartFilter {
   public AreaChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                          boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * Area chart rule:
    * Same as line chart, in addition, color bind to dimension
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      return isValid0(comb, this) && super.isValid(comb);
   }

   static boolean isValid0(ChartRefCombination comb, ChartTypeFilter filter) {
      IntList inside = comb.getInside();

      // break-up by one dimension to create stacked area
      return inside.size() == 1 && filter.hasInsideDimension(comb);

   }

   @Override
   protected int getChartType() {
      return GraphTypes.CHART_AREA_STACK;
   }
}
