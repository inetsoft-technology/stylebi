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
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;

public class FacetBoxChartFilter extends BoxChartFilter implements FacetChartFilter {
   public FacetBoxChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                               boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * Box chart rule:
    * Same as line chart, in addition, color bind to dimension
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(hasAggCalc(temp)) {
         return false;
      }

      IntList x = comb.getX();
      IntList y = comb.getY();

      if(dateDimCount <= 1) {
         return (x.size() > 1 || y.size() > 1) && isBoxValid(comb);
      }

      // If has some date leves, move all date to x. And sort by level.
      if(allDateDimension(x) && dateDimCount == x.size()) {
         return (x.size() > 1 || y.size() > 1) && isBoxValid(comb);
      }

      return false;
   }

   @Override
   protected VSChartInfo getClassyInfo(VSChartInfo info) {
      return super.getClassyInfo(setupFacet(info));
   }

   @Override
   protected int getScore(ChartInfo chart) {
      int score = SECOND_SCORE;
      VSChartInfo info = (VSChartInfo) chart;

      if(!isValidFacet(info, hierarchyGroups)) {
         return -1000;
      }

      if(!autoOrder) {
         return score;
      }

      if(hierarchyGroups.size() == 1) {
         // If has hierarchy, only show hierarchy on x.
         if(isMatchHierarchy(info.getXFields(), hierarchyGroups.get(0))) {
            score += 3;
         }
         else {
            score -= 3;
         }
      }
      else if(hierarchyGroups.size() == 2) {
         if(dateDimCount > 1) {
            return -1000;
         }
         else if(isMatchHierarchy(info.getXFields(), hierarchyGroups.get(0)) &&
            isMatchHierarchy(info.getYFields(), hierarchyGroups.get(1)))
         {
            score += 3;
         }
         else {
            score -= 3;
         }
      }

      score += getAestheticScore(info);

      return score;
   }
}
