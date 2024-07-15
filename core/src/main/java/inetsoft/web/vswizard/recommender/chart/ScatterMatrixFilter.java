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

public class ScatterMatrixFilter extends ChartTypeFilter {
   public ScatterMatrixFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                              boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * ScatterMatrix rule:
    * no facet
    * m >= 2.
    * inside <= 3 (color/shape/size).
    * m can only on y, m can't on x.
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(hasAggCalc(temp)) {
         return false;
      }

      if(dateDimCount > 1) {
         return false;
      }

      return comb.getX().size() == 0 && isScatterMatrixValid(comb);
   }

   protected boolean isScatterMatrixValid(ChartRefCombination comb) {
      int i = comb.getInsideCount();

      if(i > 3) {
         return false;
      }

      int d = getDimCount();
      int m = getMeaCount();

      if(m < 2 || d > 3) {
         return false;
      }

      IntList x = comb.getX();

      return x.size() == 0;

   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      List<ChartRef> refs = getAllRefs(true);
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_POINT);
      comb = new ChartRefCombination(comb.getY(), comb.getY(), comb.getInside());
      comb.setX(comb.getY());
      addXFields(info, comb.getX(), refs);
      addYFields(info, comb.getY(), refs);
      addInsideField(info, comb, refs);
      info.setClearedFormula(clearFormula(info));

      return getClassyInfo(info);
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
      else if(info.getSizeField() == null) {
         info.setSizeField(aes);
      }
   }

   // ScatterMatrix.
   @Override
   protected int getScore(ChartInfo chart) {
      int score = SPECIAL_PURPOSE_SCORE;
      VSChartInfo info = (VSChartInfo) chart;
      int xdims = ChartRecommenderUtil.getDimensions(info.getXFields()).size();
      int ydims = ChartRecommenderUtil.getDimensions(info.getYFields()).size();
      int xmeasures = ChartRecommenderUtil.getMeasures(info.getXFields()).size();
      int ymeasures = ChartRecommenderUtil.getMeasures(info.getYFields()).size();

      // requires symmetric measures on x/y
      if(xdims > 0 || ydims > 0) {
         score += -1000;
      }

      if(xmeasures != ymeasures || xmeasures < 3) {
         score += -1000;
      }

      // prefer to put all measures on x/y
      score += xmeasures;
      score += getAestheticScore(info);

      return score;
   }
}
