/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.vswizard.recommender.chart;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.vswizard.recommender.ChartRecommenderUtil;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;

public class FacetScatterChartFilter extends ScatterChartFilter implements FacetChartFilter {
   public FacetScatterChartFilter(AssetEntry[] entries, VSChartInfo temp,
                                List<List<ChartRef>> hgroup, boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   @Override
   protected VSChartInfo getClassyInfo(VSChartInfo info) {
      return super.getClassyInfo(setupFacet(info));
   }

   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(hasAggCalc(temp)) {
         return false;
      }

      IntList x = comb.getX();
      IntList y = comb.getY();

      if(!isPointValid(comb)) {
         return false;
      }

      if(dateDimCount <= 1) {
         return x.size() > 1 || y.size() > 1;
      }

      // If has some date leves, move all date to x. And sort by level.
      if(matchDateDimension(x) && dateDimCount == x.size() - 1) {
         return x.size() > 1 || y.size() > 1;
      }

      return false;
   }

   private boolean matchDateDimension(IntList list) {
      if(getMeasureCount(list) != 1) {
         return false;
      }

      return list.stream().allMatch(i -> isMeasure(i) || isDateDimension(i));
   }

   // For facet point:
   // put all m on y, all hierarchy d on y.
   @Override
   protected int getScore(ChartInfo chart) {
      int score = PRIMARY_SCORE;
      VSChartInfo info = (VSChartInfo) chart;

      if(!isValidFacet(info, hierarchyGroups)) {
         return -1000;
      }

      int xdims = ChartRecommenderUtil.getDimensions(info.getXFields()).size();
      int ydims = ChartRecommenderUtil.getDimensions(info.getYFields()).size();

      if(!autoOrder) {
         return score;
      }

      if(dateDimCount >= 1) {
         if(hierarchyGroups.size() == 0) {
            // If no hierarchy, only put one for x/y/color/shape
            if(ydims == 1) {
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
         if(hierarchyGroups.size() == 0) {
            // Should put shape/color before x/y for dimensions
            if(xdims + ydims > 0 && (info.getShapeField() == null || info.getColorField() == null)) {
               score -= 3;
            }
            // Should put x before y for dimensions.
            else if(xdims == 0 && ydims > 0){
               score -= 3;
            }
            else {
               score += 3;
            }
         }
         else if(hierarchyGroups.size() == 1) {
            if(isMatchHierarchy(info.getXFields(), hierarchyGroups.get(0))) {
               score += 3;
            }
            else {
               score -= 3;
            }
         }
         else if(hierarchyGroups.size() > 1) {
            // If has 2 hierarchy, show hierarchy on x/y.
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
}
