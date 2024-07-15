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

import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticSizeFrameWrapper;

import java.awt.*;
import java.util.List;

public class DonutChartFilter extends ChartTypeFilter {
   public DonutChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup) {
      super(entries, temp, hgroup);
   }

   /**
    * Donut chart rule:
    * d > 0  3 > m > 0.
    * 0 < inside <= 2 (color/shape). color can only binding dimension.
    * x/y has 1m at least.
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(dateDimCount > 1) {
         return false;
      }

      return comb.getY().size() == 1 && comb.getInsideCount() == 1 &&
         comb.getX().size() == 0 && isDonutValid(comb);
   }

   protected boolean isDonutValid(ChartRefCombination comb) {
      if(!(hasYMeasure(comb) && hasInsideDimension(comb))) {
         return false;
      }

      VSChartAggregateRef yref = (VSChartAggregateRef) temp.getYField(0);

      if(yref.getCalculator() != null) {
         return false;
      }

      return AggregateFormula.SUM.getFormulaName().equals(yref.getFormulaValue());
   }

   @Override
   protected void putInside(VSChartInfo info, ChartRef ref) {
      VSChartAggregateRef m1 = (VSChartAggregateRef) info.getYField(0);

      // color
      if(m1 != null) {
         m1.setColorField(createAestheticRef(ref));
      }
   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      List<ChartRef> refs = getAllRefs(true);
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_PIE);
      info.setDonut(true);
      info.setMultiStyles(true);
      info.setSeparatedGraph(false);

      addYFields(info, comb.getY(), refs);
      addInsideField(info, comb, refs);

      VSChartAggregateRef yref = (VSChartAggregateRef) info.getYField(0);
      VSChartAggregateRef total = new VSChartAggregateRef();
      StaticColorFrameWrapper color = new StaticColorFrameWrapper();
      StaticSizeFrameWrapper size = new StaticSizeFrameWrapper();
      color.setUserColor(Color.WHITE);
      size.setSize(15, CompositeValue.Type.USER);

      total.setColumnValue("Total@" + yref.getColumnValue());
      total.setFormulaValue(yref.getFormulaValue());
      total.setSecondaryColumnValue(yref.getSecondaryColumnValue());
      total.setColorFrameWrapper(color);
      total.setSizeFrameWrapper(size);
      total.setOriginalDataType(yref.getDataType());
      VSAestheticRef textfield = createAestheticRef(total);
      textfield.setVisualFrame(new DefaultTextFrame());
      total.setTextField(textfield);
      info.addYField(total);

      yref.setChartType(GraphTypes.CHART_PIE);
      total.setChartType(GraphTypes.CHART_PIE);

      return getClassyInfo(info);
   }

   // For donut chart:
   // 1 dimension and 1 measure
   @Override
   protected int getScore(ChartInfo info) {
      if(info.getYFieldCount() > 0) {
         ChartRef yfield = info.getYField(0);

         if(yfield instanceof ChartAggregateRef) {
            AestheticRef colorRef = ((ChartAggregateRef) yfield).getColorField();

            if(colorRef != null) {
               ChartRef color = (ChartRef) colorRef.getDataRef();
               int dintinctc = getCardinality(color);

               if(autoOrder && dintinctc > 30 || dintinctc > 60) {
                  return -1000;
               }
            }
         }
      }

      int score = SECOND_SCORE;
      score += getAestheticScore((VSChartInfo) info);
      return score;
   }
}
