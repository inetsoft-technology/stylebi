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

public class LineChartFilter extends ChartTypeFilter {
   public LineChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                          boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * Line chart rule:
    * x/y has date column.
    * inside <= 2 (color/shape).
    * date and m should on x and y(date on x and m on y; date on y and m on x)
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(dateDimCount > 1) {
         return false;
      }

      IntList x = comb.getX();
      IntList y = comb.getY();

      return x.size() <= 1 && y.size() <= 1 && isLineValid(comb);
   }

   protected boolean isLineValid(ChartRefCombination comb) {
      if(getDimCount() == 0 || getMeaCount() == 0) {
         return false;
      }

      if(comb.getInsideCount() > 2) {
         return false;
      }

      IntList x = comb.getX();
      IntList y = comb.getY();
      IntList inside = comb.getInside();

      if(x.size() + y.size() == 0) {
         return false;
      }

      if(hasXMeasure(comb) || hasInsideMeasure(comb)) {
         return false;
      }

      if(hasYDate(comb) || hasInsideDate(comb)) {
         return false;
      }

      return allDateDimension(x) && hasXDate(comb) && hasYMeasure(comb);

   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      List<ChartRef> refs = getAllRefs(true);
      VSChartInfo info = new VSChartInfo();
      info.setChartType(getChartType());
      addXFields(info, comb.getX(), refs);
      addYFields(info, comb.getY(), refs);
      addInsideField(info, comb, refs);

      return getClassyInfo(info);
   }

   protected int getChartType() {
      return GraphTypes.CHART_LINE;
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

   @Override
   protected int getScore(ChartInfo chart) {
      VSChartInfo info = (VSChartInfo) chart;
      int score = PRIMARY_SCORE;

      // only a date dim, should put line before bar.
      if(getDimCount() == 1 && getMeaCount() == 1) {
         score += 10;
      }

      score += getAestheticScore(info);
      return score;
   }
}
