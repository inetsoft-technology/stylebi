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

import java.util.ArrayList;
import java.util.List;

public class BarChartFilter extends ChartTypeFilter {
   public BarChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                         boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * Bar chart rule:
    * x and y can't have measure in the same time.
    * inside <= 2 (color/shape).
    * x + y > 0 (x or y only have one ref at least)
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(dateDimCount > 1) {
         return false;
      }

      IntList x = comb.getX();
      IntList y = comb.getY();

      return x.size() <= 1 && y.size() <= 1 && isBarValid(comb);
   }

   protected boolean isBarValid(ChartRefCombination comb) {
      if(getDimCount() == 0 || getMeaCount() == 0) {
         return false;
      }

      if(this instanceof FacetChartFilter) {
         if(comb.getInsideCount() > 1) {
            return false;
         }
      }
      else {
         if(comb.getInsideCount() > 2) {
            return false;
         }
      }

      IntList x = comb.getX();
      IntList y = comb.getY();

      if(x.size() + y.size() == 0) {
         return false;
      }

      if(hasXMeasure(comb) || hasInsideMeasure(comb)) {
         return false;
      }

      return hasXDimension(comb) && hasYMeasure(comb);
   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(getChartType(comb));
      List<ChartRef> refs = getAllRefs(true);

      if(shouldRotate(comb)) {
         addXFields(info, comb.getY(), refs);
         addYFields(info, comb.getX(), refs);
      }
      else {
         addXFields(info, comb.getX(), refs);
         addYFields(info, comb.getY(), refs);
      }

      addInsideField(info, comb, refs);

      return getClassyInfo(info);
   }

   protected int getChartType(ChartRefCombination comb) {
      return shouldStacked(comb) ? GraphTypes.CHART_BAR_STACK : GraphTypes.CHART_BAR;
   }

   protected boolean shouldStacked(ChartRefCombination comb) {
      return comb.getInside().stream().anyMatch(index -> isDimension(index));
   }

   // show horizontal bar by default, except:
   // 1. x dim is ordinal (date or number)
   // 2. x dimension cardinality is small
   protected boolean shouldRotate(ChartRefCombination comb) {
      if(getDimCount() > 0) {
         if(dateDimCount > 0) {
            return false;
         }

         IntList x = comb.getX();

         // For bar chart's is valid will put dim to x, mea to y. So check dim on x is ok.
         // For facet bar, dims on x will be more. So check all dims on x.
         for(int i = 0; i < x.size(); i++) {
            if(getCardinality(getDim(x.get(i))) > 10) {
               return true;
            }
         }

         return false;
      }

      return true;
   }

   @Override
   protected void putInside(VSChartInfo info, ChartRef ref) {
      VSAestheticRef aes = createAestheticRef(ref);

      if(info.getColorField() == null) {
         info.setColorField(aes);
      }
      else if(info.getShapeField() == null) {
         info.setShapeField(aes);
      }
   }

   // For bar chart: sort by this
   // 1 facet chart has hierarchy +3 (1 d has hierarchy  2 d==1 m>1)
   // 2 d on x and color, m on y. +1
   // 3 others. 0
   @Override
   protected int getScore(ChartInfo chart) {
      VSChartInfo info = (VSChartInfo) chart;
      int score = PRIMARY_SCORE;
      int xdims = ChartRecommenderUtil.getDimensions(info.getXFields()).size();
      int ydims = ChartRecommenderUtil.getDimensions(info.getYFields()).size();

      if(!autoOrder) {
         return score;
      }

      if(info.getShapeField() != null) {
         score = SECOND_SCORE - 1;
      }

      if(hierarchyGroups.size() == 0) {
         // If no hierarchy, only put one for x/y/color/shape
         if(xdims <= 1 && ydims <= 1) {
            score += 3;
         }
      }

      if(hierarchyGroups.size() > 0) {
         List<ChartRef> list = new ArrayList<>();

         if(info.getXFieldCount() > 0 && info.getColorField() != null) {
            list.add(info.getXField(0));
            list.add((ChartRef)info.getColorField().getDataRef());

            if(info.getShapeField() != null) {
               list.add((ChartRef)info.getShapeField().getDataRef());
            }
         }

         ChartRef[] refs = new ChartRef[list.size()];

         if(isMatchHierarchy(list.toArray(refs), hierarchyGroups.get(0))) {
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
