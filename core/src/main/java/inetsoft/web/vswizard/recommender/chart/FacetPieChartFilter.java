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

import java.util.*;

public class FacetPieChartFilter extends PieChartFilter implements FacetChartFilter {
   public FacetPieChartFilter(AssetEntry[] entries, VSChartInfo temp,
                              List<List<ChartRef>> hgroup, boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   @Override
   protected VSChartInfo getClassyInfo(VSChartInfo info) {
      return super.getClassyInfo(setupFacet(info));
   }

   /**
    * Pie chart rule:
    * 4 > d > 0  3 > m > 0.
    * 0 < inside <= 2 (color/shape). color can only binding dimension.
    * y has 1m at least.
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(dateDimCount > 1) {
         return false;
      }

      if(!(comb.getY().size() > 1 && isPieValid(comb))) {
         return false;
      }

      // pie looks bad in high dimensional facet
      return getDimensionCount(comb.getX()) + getDimensionCount(comb.getY()) <= 2;

   }

   // For facet pie:
   // d<3, put them on y/color/text
   // put according cardinality: y < color < text.
   // put all m on y.
   // color and text will order by cardinality, so no need to score for it.
   @Override
   protected int getScore(ChartInfo chart) {
      int score = SECOND_SCORE;
      VSChartInfo info = (VSChartInfo) chart;

      if(!isValidFacet(info, hierarchyGroups)) {
         return -1000;
      }

      List<VSChartDimensionRef> ydims = ChartRecommenderUtil.getDimensions(info.getYFields());

      if(info.getColorField() == null) {
         return -1000;
      }

      ChartRef color = (ChartRef) info.getColorField().getDataRef();
      int distinctC = getCardinality(color);

      if(autoOrder && distinctC > 30 || distinctC > 50) {
         return -1000;
      }

      if(ydims.size() == 0) {
         return -1000;
      }

      if(!autoOrder) {
         return score;
      }

      ChartRef yfld = info.getYField(0);
      int distinctY = getCardinality(yfld);

      if(hierarchyGroups.size() == 0) {
         // no hierarchy, if both can be used as color, use the lower cardinality dim as color
         if(autoOrder && distinctY < 30 && distinctC < 30) {
            if(distinctY < distinctC) {
               score -= 3;
            }
            else {
               score += 3;
            }
         }
      }
      // If has hierarchy, should x < color
      else if(hierarchyGroups.size() == 1) {
         List<ChartRef> hierarchy = hierarchyGroups.get(0);
         List<ChartRef> list = new ArrayList<>();
         list.addAll(ydims);
         list.add((ChartRef)info.getColorField().getDataRef());
         ChartRef[] refs = new ChartRef[list.size()];

         if(isAbsulateMatch(list.toArray(refs), hierarchy)) {
            score += 3;
         }
         else {
            score -= 3;
         }
      }
      else if(hierarchyGroups.size() == 2) {
         List<ChartRef> hierarchy0 = hierarchyGroups.get(0);
         List<ChartRef> hierarchy1 = hierarchyGroups.get(1);
         List<ChartRef> list = new ArrayList<>();
         list.addAll(Arrays.asList(info.getXFields()));
         list.add((ChartRef)info.getColorField().getDataRef());
         ChartRef[] refs = new ChartRef[list.size()];

         if(isMatchHierarchy(info.getYFields(), hierarchy0) &&
            (isMatchHierarchy(info.getXFields(), hierarchy1) ||
            isMatchHierarchy(list.toArray(refs), hierarchy1)))
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

   // If only one hiearchy, put on hierarchy on y+color, so y+color = hiearchy.
   // If one hierarchy+1dim, put one hierarchy on y, other dim on color, so y+color=hierarchy+1.
   private boolean isAbsulateMatch(ChartRef[] refs, List<ChartRef> hierarchy) {
      if(getDimCount() == hierarchy.size() && refs.length == hierarchy.size() ||
         refs.length == hierarchy.size() + 1)
      {
         return isMatchHierarchy(refs, hierarchy);
      }

      return false;
   }
}
