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

import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.vswizard.recommender.ChartRecommenderUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class DotPlotFilter extends ChartTypeFilter implements FacetChartFilter {
   public DotPlotFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                        boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(comb.getXCount() == 1 && comb.getYCount() == 0 && comb.getInsideCount() <= 1) {
         int dataSize = ChartRecommenderUtil.getDataSetSize(entries);

         // don't recommend dot plot for large data.
         if(dataSize > 0 && dataSize < 500) {
            return true;
         }
      }

      return false;
   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      List<ChartRef> refs = getAllRefs(true);
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_POINT);
      addXFields(info, comb.getX(), refs);
      addYFields(info, comb.getY(), refs);
      addInsideField(info, comb, refs);

      return getClassyInfo(setupFacet(info));
   }

   @Override
   protected void putInside(VSChartInfo info, ChartRef ref) {
      VSAestheticRef aes = createAestheticRef(ref);

      if(info.getShapeField() == null) {
         info.setShapeField(aes);
      }
      else if(info.getColorField() == null) {
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

      if(!GraphTypeUtil.isDotPlot(chart)) {
         return -1;
      }

      ChartRef[] xrefs = info.getXFields();
      ChartRef[] yrefs = info.getYFields();
      AestheticRef[] arefs = info.getAestheticRefs(false);
      ChartRef[] refs = Stream.concat(Stream.concat(Arrays.stream(xrefs), Arrays.stream(yrefs)),
                                      Arrays.stream(arefs).map(a -> (ChartRef) a.getDataRef()))
         .filter(a -> a != null)
         .toArray(ChartRef[]::new);

      // dot plot should put the higher category on x and break-by lower dimension.
      if(hierarchyGroups.size() == 1 && refs.length == 2) {
         if(isMatchHierarchy(refs, hierarchyGroups.get(0))) {
            score += 2;
         }
      }

      return score;
   }
}
