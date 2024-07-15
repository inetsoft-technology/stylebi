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

import java.util.List;

public class BoxChartFilter extends ChartTypeFilter {
   public BoxChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                         boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * Box chart rule:
    * x and y can't have measure in the same time.
    * inside == 0
    * x + y > 0 (x or y have one ref at least)
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      IntList x = comb.getX();
      IntList y = comb.getY();

      return x.size() <= 1 && y.size() <= 1 && isBoxValid(comb);
   }

   protected boolean isBoxValid(ChartRefCombination comb) {
      if(hasAggCalc(temp)) {
         return false;
      }

      if(getDimCount() == 0 || getMeaCount() == 0) {
         return false;
      }

      IntList inside = comb.getInside();

      if(inside.size() > 0) {
         return false;
      }

      IntList x = comb.getX();
      IntList y = comb.getY();

      if(x.size() + y.size() == 0) {
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
      info.setChartType(GraphTypes.CHART_BOXPLOT);
      List<ChartRef> refs = getAllRefs(true);

      addXFields(info, comb.getX(), refs);
      addYFields(info, comb.getY(), refs);
      addInsideField(info, comb, refs);

      return getClassyInfo(info);
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

      if(!autoOrder) {
         return score;
      }

      score += getAestheticScore(info);

      return score;
   }
}
