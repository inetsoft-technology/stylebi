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
import inetsoft.web.vswizard.recommender.ChartRecommenderUtil;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;

public class FacetBarChartFilter extends BarChartFilter implements FacetChartFilter {
   public FacetBarChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                              boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   @Override
   protected VSChartInfo getClassyInfo(VSChartInfo info) {
      return super.getClassyInfo(setupFacet(info));
   }

   /**
    * Bar chart rule:
    * x and y can't have measure in the same time.
    * inside <= 2 (color/shape).
    * x + y > 0 (x or y only have one ref at least)
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      IntList x = comb.getX();
      IntList y = comb.getY();

      if(dateDimCount <= 1) {
         return (x.size() > 1 || y.size() > 1) && isBarValid(comb);
      }

      // If has some date leves, move all date to x. And sort by level.
      if(allDateDimension(x) && dateDimCount == x.size()) {
         return (x.size() > 1 || y.size() > 1) && isBarValid(comb);
      }

      return false;
   }

   // For Facet bar chart: sort by this
   // 1 facet chart has hierarchy +3 (1 d has hierarchy  2 d==1 m>1)
   // 2 d on x and color, m on y. +1
   // 3 others. 0

   // The top1 facet bar should like this:
   // 1 all m on y  2 d one group on x, the other group on y, other on color/shape.
   @Override
   protected int getScore(ChartInfo chart) {
      int score = PRIMARY_SCORE;
      VSChartInfo info = (VSChartInfo) chart;

      if(!isValidFacet(info, hierarchyGroups)) {
         return -1000;
      }

      List<VSChartDimensionRef> xdims = ChartRecommenderUtil.getDimensions(info.getXFields());
      List<VSChartDimensionRef> ydims = ChartRecommenderUtil.getDimensions(info.getYFields());

      if(!autoOrder) {
         return score;
      }

      if(dateDimCount >= 1) {
         if(hierarchyGroups.size() == 0) {
            // If no hierarchy, only put one for x/y/color/shape
            if(ydims.size() == 1) {
               score += 3;
            }
         }
         else if(hierarchyGroups.size() > 0) {
            // If has hierarchy, only show hierarchy on x.
            if(isMatchHierarchy(info.getYFields(), hierarchyGroups.get(0))) {
               score += 3;
            }
            else {
               score -= 3;
            }
         }
      }
      else {
         int nonDateXDims = ChartRecommenderUtil.getDimensions(info.getXFields(), false).size();
         int nonDateYDims = ChartRecommenderUtil.getDimensions(info.getYFields(), false).size();

         if(hierarchyGroups.size() == 0) {
            // If no hierarchy, only put one for x/y/color/shape
            if(nonDateXDims <= 1 && nonDateYDims <= 1 && ydims.size() < xdims.size()) {
               score += 3;
            }
         }
         else if(hierarchyGroups.size() == 1) {
            // If has hierarchy, only show hierarchy on x.
            if(isMatchHierarchy(info.getXFields(), hierarchyGroups.get(0))) {
               score += 3;
            }
            else {
               score -= 3;
            }
         }
         else if(hierarchyGroups.size() == 2) {
            if(isMatchHierarchy(info.getXFields(), hierarchyGroups.get(0)) &&
               isMatchHierarchy(info.getYFields(), hierarchyGroups.get(1)))
            {
               score += 3;
            }
            else {
               score -= 3;
            }
         }
      }

      score += getAestheticScore(info);

      return score;
   }

   @Override
   protected boolean shouldRotate(ChartRefCombination comb) {
      return false;
   }
}
