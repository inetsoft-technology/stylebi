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

import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.*;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;

public class MekkoChartFilter extends ChartTypeFilter {
   public MekkoChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                         boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * Mekko chart rule:
    * 1 dimension on x, 1 measure on y, and 1 dimension for group field (inner dimension)
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      IntList x = comb.getX();
      IntList y = comb.getY();
      IntList inside = comb.getInside();

      return x.size() == 1 && y.size() == 1 && inside.size() == 1 &&
         getDimensionCount(x) == 1 && getMeasureCount(y) == 1 &&
         getDimensionCount(inside) == 1;
   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_MEKKO);
      List<ChartRef> refs = getAllRefs(true);

      addXFields(info, comb.getX(), refs);
      addYFields(info, comb.getY(), refs);
      getRefs(comb.getInside(), refs).forEach(f -> {
         info.addGroupField(f);
         info.setColorField(createAestheticRef(f));
         info.setTextField(createAestheticRef(f));
      });

      GraphUtil.fixVisualFrames(info);
      return getClassyInfo(info);
   }

   @Override
   protected int getScore(ChartInfo chart) {
      int score = SPECIAL_PURPOSE_SCORE;

      if(!autoOrder) {
         return score;
      }

      // prefer lower cardinality on x
      if(getCardinality(chart.getXField(0)) < getCardinality(chart.getGroupField(0))) {
         score += 1;
      }

      score += getAestheticScore((VSChartInfo) chart);
      return score;
   }
}
