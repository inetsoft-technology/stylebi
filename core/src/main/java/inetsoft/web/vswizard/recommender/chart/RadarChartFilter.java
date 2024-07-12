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
import inetsoft.web.vswizard.recommender.ChartRecommenderUtil;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;

public class RadarChartFilter extends ChartTypeFilter {
   public RadarChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                           boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * Radar rule:
    * m >= 3.
    * inside <= 2 (color/shape).
    * m can only on y, m can't on x and inside.
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      return comb.getX().size() == 0 && getDimCount() <= 2 && isRadarValid(comb);
   }

   protected boolean isRadarValid(ChartRefCombination comb) {
      if(dateDimCount > 1) {
         return false;
      }

      int i = comb.getInsideCount();

      if(i > 2) {
         return false;
      }

      int m = getMeaCount();

      if(m < 3) {
         return false;
      }

      IntList x = comb.getX();
      IntList inside = comb.getInside();

      return x.size() == 0 && !hasInsideMeasure(comb);

   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      List<ChartRef> refs = getAllRefs(true);
      VSChartInfo info = new RadarVSChartInfo();
      info.setChartType(GraphTypes.CHART_RADAR);
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
      else if(info.getShapeField() == null) {
         info.setShapeField(aes);
      }
   }

   // Radar.
   // x has dimension and color shape has dimension score ++.
   // x has dimension and color has no dimension score --.
   // color/shape value > 10 score--
   @Override
   protected int getScore(ChartInfo chart) {
      int score = SPECIAL_PURPOSE_SCORE;
      VSChartInfo info = (VSChartInfo) chart;
      int xdims = ChartRecommenderUtil.getDimensions(info.getXFields()).size();
      int ydims = ChartRecommenderUtil.getDimensions(info.getYFields()).size();

      if(xdims == 0 && ydims == 0) {
         score += 3;
      }

      score += getAestheticScore(info);

      return score;
   }
}
