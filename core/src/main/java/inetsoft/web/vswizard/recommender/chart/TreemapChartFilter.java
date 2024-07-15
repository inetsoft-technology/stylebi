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

import java.util.ArrayList;
import java.util.List;

public class TreemapChartFilter extends ChartTypeFilter {
   public TreemapChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                             boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * TreeMap rule:
    * only split refs to two groups. Only put refs to two places(T field and color).
    * using y to T, inside to size/text.
    * d >= 1  0 < m < 3.
    * inside <= 2 (2m put to size/text).
    * color field put top dimension.
    * y only has dim, inside only has mea.
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      IntList x = comb.getX();
      IntList y = comb.getY();

      if(x.size() != 0 || y.size() == 0) {
         return false;
      }

      int d = getDimCount();
      int m = getMeaCount();

      if(d == 0 || m == 0 || m > 2) {
         return false;
      }

      IntList inside = comb.getInside();

      // y n-d, put to t fields
      // inside, put to size/color
      return !hasYMeasure(comb) && !hasInsideDimension(comb);

   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      List<ChartRef> refs = getAllRefs(true);
      VSChartInfo info = new VSChartInfo();
      info.setChartType(getChartType());
      addTField(info, comb, refs);
      addColor(info, comb);
      addInsideField(info, comb, refs);

      return getClassyInfo(info);
   }

   protected int getChartType() {
      return GraphTypes.CHART_TREEMAP;
   }

   @Override
   protected void putInside(VSChartInfo info, ChartRef ref) {
      VSAestheticRef aes = createAestheticRef(ref);

      if(info.getSizeField() == null) {
         info.setSizeField(aes);
      }
      // measure on color
      else {
         info.setColorField(aes);
      }
   }

   // check if the group columns are in a hierarchy
   protected boolean isHierarchy(ChartInfo info) {
      List<ChartRef> refs = new ArrayList<>();

      for(int i = 0; i < info.getGroupFieldCount(); i++) {
         refs.add(info.getGroupField(i));
      }

      return hierarchyGroups.stream().anyMatch(hier -> hier.containsAll(refs));
   }

   protected void addColor(VSChartInfo info, ChartRefCombination comb) {
      if(info.getGroupFieldCount() > 0) {
         info.setColorField(createAestheticRef(info.getGroupField(0)));
      }
   }

   protected void addTField(VSChartInfo info, ChartRefCombination comb, List<ChartRef> refs) {
      boolean onlyOneHierarchy = hierarchyGroups.size() == 1 &&
         comb.getY().size() == hierarchyGroups.get(0).size();

      if(autoOrder || onlyOneHierarchy) {
         getSortRefs(comb.getY(), refs).forEach(ref -> info.addGroupField(ref));
      }
      // custom order, could be useful when dimensions are not in strict hierarchy
      else {
         getRefs(comb.getY(), refs).forEach(ref -> info.addGroupField(ref));
      }
   }

   @Override
   protected int getScore(ChartInfo chart) {
      int score = SECOND_SCORE;
      VSChartInfo info = (VSChartInfo) chart;

      if(hierarchyGroups.size() == 1) {
         score = SPECIAL_PURPOSE_SCORE + 1;
      }

      // only allow deeply nested dimensions if they are in one hierarchy
      if(chart.getGroupFieldCount() > 5) {
         if(hierarchyGroups.size() != 1) {
            return -1000;
         }

         if(hierarchyGroups.get(0).size() != chart.getGroupFieldCount()) {
            return -1000;
         }
      }

      if(!autoOrder) {
         return score;
      }

      if(hierarchyGroups.size() == 1) {
         if(isMatchHierarchy(info.getGroupFields(), hierarchyGroups.get(0)) &&
            info.getGroupFields().length != hierarchyGroups.get(0).size())
         {
            score += 3;
         }
      }

      score += getAestheticScore((VSChartInfo) chart);
      return score;
   }
}
