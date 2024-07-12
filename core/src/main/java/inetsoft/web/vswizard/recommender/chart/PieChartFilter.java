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

public class PieChartFilter extends ChartTypeFilter {
   public PieChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                         boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * Pie chart rule:
    * d > 0  3 > m > 0.
    * 0 < inside <= 2 (color/shape). color can only binding dimension.
    * x/y has 1m at least.
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      int d = getDimCount();

      if(d == 0 || d > 2) {
         return false;
      }

      return comb.getY().size() == 1 && comb.getX().size() == 0 && isPieValid(comb);
   }

   protected boolean isPieValid(ChartRefCombination comb) {
      int i = comb.getInsideCount();

      if(i == 0 || i > 2 || dateDimCount > 1) {
         return false;
      }

      int m = getMeaCount();

      if(m == 0 || m > 2) {
         return false;
      }

      IntList x = comb.getX();
      IntList y = comb.getY();
      IntList inside = comb.getInside();

      if(getDimensionCount(inside) > 1) {
         return false;
      }

      // if i==2 inside should be 1d 1m.
      if(i == 2 && !hasInsideMeasure(comb)) {
         return false;
      }

      if(!hasInsideDimension(comb)) {
         return false;
      }

      // Can't binding measure to x.
      if(hasXMeasure(comb)) {
         return false;
      }

      return hasYMeasure(comb);
   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      List<ChartRef> refs = getAllRefs(true);
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_PIE);
      addXFields(info, comb.getX(), refs);
      addYFields(info, comb.getY(), refs);
      addInsideField(info, comb, refs);

      return getClassyInfo(info);
   }

   @Override
   protected void putInside(VSChartInfo info, ChartRef ref) {
      VSAestheticRef aes = createAestheticRef(ref);

      if(ref.isMeasure()) {
         // size
         if(info.getSizeField() == null) {
            info.setSizeField(aes);
         }
      }
      else {
         if(info.getColorField() == null) {
            info.setColorField(aes);
         }
      }
   }

   @Override
   protected int getScore(ChartInfo chart) {
      VSChartInfo info = (VSChartInfo) chart;

      if(info.getColorField() == null) {
         return -1000;
      }

      if(info.getXFieldCount() > 0) {
         return -1000;
      }

      int score = SECOND_SCORE;
      score += getAestheticScore(info);
      return score;
   }
}
