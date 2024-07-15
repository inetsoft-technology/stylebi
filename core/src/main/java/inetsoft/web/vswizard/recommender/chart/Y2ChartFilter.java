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

import java.util.Arrays;
import java.util.List;

public class Y2ChartFilter extends ChartTypeFilter implements FacetChartFilter {
   public Y2ChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                        boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * Y2 chart rule:
    * x and y can't have measure in the same time.
    * inside <= 0 (having aesthetic binding makes it hard to differentiate 1st and 2nd y measure).
    * x + y > 0 (x or y only have one ref at least)
    * d > 0, m = 2 or 3.
    * d to x/y/color   m to y(2) and color.
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(comb.getInsideCount() > 0) {
         return false;
      }

      if(getDimCount() == 0 || getMeaCount() != 2) {
         return false;
      }

      IntList x = comb.getX();
      IntList y = comb.getY();

      if(x.size() == 0 || y.size() < 2) {
         return false;
      }

      if(hasXMeasure(comb)) {
         return false;
      }

      return hasXDimension(comb) && hasYMeasure(comb);

   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(getChartStyle(comb));
      info.setSeparatedGraph(false);
      List<ChartRef> refs = getAllRefs(true);

      addXFields(info, comb.getX(), refs);
      addYFields(info, comb.getY(), refs);
      ((VSChartAggregateRef) info.getYField(info.getYFieldCount() - 1)).setSecondaryY(true);

      addInsideField(info, comb, refs);

      if(hasYDimension(comb)) {
         info = setupFacet(info);
      }

      return getClassyInfo(info);
   }

   private int getChartStyle(ChartRefCombination comb) {
      return shouldStacked(comb) ? GraphTypes.CHART_BAR_STACK : GraphTypes.CHART_BAR;
   }

   protected boolean shouldStacked(ChartRefCombination comb) {
      return comb.getInside().stream().anyMatch(index -> isDimension(index));
   }

   @Override
   protected void putInside(VSChartInfo info, ChartRef ref) {
      VSAestheticRef aes = createAestheticRef(ref);

      if(info.getColorField() == null) {
         info.setColorField(aes);
      }
   }

   @Override
   protected int getScore(ChartInfo chart) {
      int score = SECOND_SCORE;
      VSChartInfo info = (VSChartInfo) chart;
      boolean xdate = Arrays.asList(info.getXFields()).stream().anyMatch(ref -> isDateRef(ref));
      boolean ydate = Arrays.asList(info.getYFields()).stream().anyMatch(ref -> isDateRef(ref));

      if(xdate && ydate) {
         score -= 3;
      }

      // If has hierarchy, only show hierarchy on y.
      if(hierarchyGroups.size() == 1) {
         if(isMatchHierarchy(info.getXFields(), hierarchyGroups.get(0)) ||
            isMatchHierarchy(info.getYFields(), hierarchyGroups.get(0)))
         {
            score += 3;
         }
         else {
            score -= 3;
         }
      }
      else if(hierarchyGroups.size() > 1) {
         if(isMatchHierarchy(info.getXFields(), hierarchyGroups.get(0)) &&
            isMatchHierarchy(info.getYFields(), hierarchyGroups.get(1)))
         {
            score += 3;
         }
         else {
            return -1000;
         }
      }

      score += getAestheticScore(info);
      return score;
   }
}
