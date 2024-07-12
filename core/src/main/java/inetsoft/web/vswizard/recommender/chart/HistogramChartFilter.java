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

import inetsoft.util.data.CommonKVModel;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticSizeFrameWrapper;

import java.util.ArrayList;
import java.util.List;

public class HistogramChartFilter extends ChartTypeFilter {
   public HistogramChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                               boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * Histogram is for a single measure
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(hasAggCalc(temp)) {
         return false;
      }

      return getDimCount() == 0 && getMeaCount() == 1 &&
         comb.getYCount() == 1 && isNumbericAggreate();
   }

   private boolean isNumbericAggreate() {
      ChartRef ref = getAllRefs(false).get(0);
      String dtype = ref.getDataType();

      if(ref instanceof VSChartAggregateRef) {
         DataRef dref = ((VSChartAggregateRef) ref).getDataRef();
         dtype = dref == null ? dtype : dref.getDataType();
      }

      return XSchema.isNumericType(dtype);
   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      List<ChartRef> refs = getAllRefs(true);
      addYFields(info, comb.getY(), refs);
      VSChartAggregateRef yref = (VSChartAggregateRef) info.getYField(0);
      List<CommonKVModel<String, String>> clearedFormula = new ArrayList<>();
      clearedFormula.add(new CommonKVModel<>(yref.getFullName(), "Count"));
      info.setClearedFormula(clearedFormula);
      yref.setFormulaValue("Count");
      StaticSizeFrameWrapper size = new StaticSizeFrameWrapper();
      size.setSize(30, CompositeValue.Type.DEFAULT);
      yref.setSizeFrameWrapper(size);

      VSChartDimensionRef range = new VSChartDimensionRef();
      range.setGroupColumnValue("Range@" + yref.getColumnValue());
      range.setDataType(yref.getDataType());
      range.setRefType(yref.getRefType());
      info.addXField(range);

      return getClassyInfo(info);
   }

   @Override
   protected int getScore(ChartInfo chart) {
      int score = SECOND_SCORE;
      return score;
   }
}
