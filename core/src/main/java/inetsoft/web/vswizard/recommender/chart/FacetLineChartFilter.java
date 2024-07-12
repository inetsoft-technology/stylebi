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

import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.vswizard.recommender.ChartRecommenderUtil;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;

public class FacetLineChartFilter extends LineChartFilter implements FacetChartFilter {
   public FacetLineChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                               boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   @Override
   protected VSChartInfo getClassyInfo(VSChartInfo info) {
      return super.getClassyInfo(setupFacet(info));
   }

   /**
    * Line chart rule:
    * x/y has date column.
    * inside <= 2 (color/shape).
    * date and m should on x and y(date on x and m on y; date on y and m on x)
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      IntList x = comb.getX();
      IntList y = comb.getY();

      if(dateDimCount <= 1) {
         return (x.size() > 1 || y.size() > 1) && isLineValid(comb);
      }

      // If has some date leves, move all date to x. And sort by level.
      if(allDateDimension(x) && dateDimCount == x.size()) {
         return (x.size() > 1 || y.size() > 1) && isLineValid(comb);
      }

      return false;
   }

   // Facet Line chart.
   // all date on x, all m on y. Other d on shape/color/y.
   // if has hierarchy, hierarchy on y. Others put Random.
   @Override
   protected int getScore(ChartInfo chart) {
      int score = PRIMARY_SCORE;
      VSChartInfo info = (VSChartInfo) chart;

      if(!isValidFacet(info, hierarchyGroups)) {
         return -1000;
      }

      int ydims = ChartRecommenderUtil.getDimensions(info.getYFields()).size();

      if(!autoOrder) {
         return score;
      }

      if(info.getShapeField() != null) {
         ChartRef line = (ChartRef) info.getShapeField().getDataRef();
         int dintinctS = getCardinality(line);

         // only 5 distinct line styles
         if(dintinctS > 5) {
            return -1000;
         }
      }

      if(hierarchyGroups.size() == 0) {
         // If no hierarchy, only put one for x/y/color/shape
         if(ydims <= 1) {
            score += 3;
         }
      }
      else if(hierarchyGroups.size() >= 1) {
         // If has hierarchy, only show hierarchy on x.
         if(isMatchHierarchy(info.getYFields(), hierarchyGroups.get(0))) {
            score += 3;
         }
         else {
            score -= 3;
         }
      }

      // All m should on y.
      if(!GraphUtil.hasMeasureOnX(info) &&
         !ChartRecommenderUtil.hasMeasureOnAesthetic(info))
      {
         score += 3;
      }

      score += getAestheticScore(info);

      return score;
   }
}
