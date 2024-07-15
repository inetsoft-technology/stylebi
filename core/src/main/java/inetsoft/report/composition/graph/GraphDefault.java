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
package inetsoft.report.composition.graph;

import inetsoft.graph.AxisSpec;
import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.SizeFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.BoxDataSet;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.*;
import inetsoft.graph.scale.*;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.report.composition.graph.calc.ValueOfColumn;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.XAggregateRef;
import inetsoft.uql.viewsheet.graph.*;

import java.awt.*;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;

/**
 * This class contains logic for setting various graph default settings.
 *
 * @author InetSoft Technology Corp.
 * @since  13.2
 */
public class GraphDefault {
   /**
    * Set default scale options based on chart type.
    */
   public static void setScaleOptions(GraphGenerator gen, RectCoord rect,
                                      Scale xscale, Scale yscale)
   {
      // @by larryl, per Steve Few recommendation, only start at 0 for
      // bar and bar-like graphs
      if(gen.isGraphType(GraphTypes.CHART_POINT, GraphTypes.CHART_SCATTER_CONTOUR,
                         GraphTypes.CHART_STOCK, GraphTypes.CHART_LINE,
                         GraphTypes.CHART_STEP, GraphTypes.CHART_JUMP,
                         GraphTypes.CHART_CANDLE, GraphTypes.CHART_BOXPLOT))
      {
         if(yscale instanceof LinearScale) {
            yscale.setScaleOption(Scale.TICKS | Scale.GAPS);
         }

         if(xscale instanceof LinearScale) {
            xscale.setScaleOption(Scale.TICKS | Scale.GAPS);
         }
      }
      else if(gen.isGraphType(GraphTypes.CHART_AREA) ||
         gen.isGraphType(GraphTypes.CHART_STEP_AREA))
      {
         if(yscale instanceof LinearScale) {
            yscale.setScaleOption(Scale.TICKS | Scale.GAPS | Scale.ZERO);
         }
      }
      else if(gen.isGraphType(GraphTypes.CHART_PARETO)) {
         LinearScale percentScale = new LinearScale();
         AxisSpec yaxis = yscale.getAxisSpec();

         yscale.setScaleOption(Scale.ZERO);
         percentScale.setMax(1);
         percentScale.setMin(0);
         percentScale.getAxisSpec().getTextSpec().setFormat(NumberFormat.getPercentInstance());
         percentScale.getAxisSpec().getTextSpec().setColor(yaxis.getTextSpec().getColor());
         percentScale.getAxisSpec().getTextSpec().setBackground(yaxis.getTextSpec().getBackground());
         percentScale.getAxisSpec().getTextSpec().setFont(yaxis.getTextSpec().getFont());
         percentScale.getAxisSpec().setGridStyle(yaxis.getGridStyle());
         percentScale.getAxisSpec().setGridColor(yaxis.getGridColor());
         percentScale.getAxisSpec().setLineColor(yaxis.getLineColor());

         if(yscale instanceof LinearScale) {
            percentScale.setReversed(((LinearScale) yscale).isReversed());
         }

         rect.setYScale2(percentScale);
      }
      else {
         yscale.setScaleOption(yscale.getScaleOption() | Scale.TICKS | Scale.ZERO);
      }

      if(xscale != null) {
         Boolean change = isChangeNext(gen.getData());

         if(change != null) {
            xscale.setVars(getAllVars(gen.graph));

            if(change) {
               xscale.setScaleOption(xscale.getScaleOption() | Scale.NO_TRAILING_NULL_VAR);
            }
            else {
               xscale.setScaleOption(xscale.getScaleOption() | Scale.NO_LEADING_NULL_VAR);
            }
         }

         if(gen.isGraphType(GraphTypes.CHART_GANTT)) {
            AxisSpec xaxis = xscale.getAxisSpec();

            if(xaxis.getTextSpec().getRotation() == 90) {
               xaxis.getTextSpec().setRotation(0);
            }

            if(xscale instanceof CategoricalScale) {
               ((CategoricalScale) xscale).setReversed(true);
            }
         }
      }
   }

   // return true if change from next, false if change from previous, null if no change calc.
   private static Boolean isChangeNext(DataSet data) {
      if(data instanceof BoxDataSet) {
         data = ((BoxDataSet) data).getDataSet();
      }

      return data.getCalcColumns().stream()
         .filter(calc -> calc instanceof ValueOfColumn)
         .map(calc -> {
            ValueOfColumn change = (ValueOfColumn) calc;
            return change.getChangeType() == ChangeCalc.NEXT ||
               change.getChangeType() == ChangeCalc.LAST;
         })
         .findFirst().orElse(null);
   }

   private static String[] getAllVars(EGraph graph) {
      Set<String> vars = new HashSet<>();

      for(int i = 0; i < graph.getElementCount(); i++) {
         for(String var : graph.getElement(i).getVars()) {
            vars.add(var);
         }
      }

      return vars.toArray(new String[0]);
   }

   /**
    * Check if the element binding requires inPlot.
    */
   public static boolean isInPlot(PlotDescriptor plot, GraphElement elem, EGraph graph) {
      if(!plot.isInPlot()) {
         return false;
      }

      if(plot.getTrendline() != StyleConstants.NONE && plot.getProjectTrendLineForward() > 0) {
         return true;
      }

      boolean explicitMinMax = GraphUtil.getAllScales(graph.getCoordinate()).stream()
         .filter(a -> a instanceof LinearScale)
         .map(a -> (LinearScale) a)
         .anyMatch(s -> s.getUserMin() != null || s.getUserMax() != null);

      // explicitly set min/max may cut off the vo
      if(explicitMinMax) {
         return true;
      }

      // always inside plot, no need to scale
      if(elem instanceof TreemapElement || elem instanceof MekkoElement) {
         return false;
      }

      if(elem.getTextFrame() != null) {
         return true;
      }

      // bar doesn't protrude outside
      if(elem instanceof IntervalElement) {
         // time scale doesn't reverse space on sides so half of the bars at two ends
         // would be outside
         boolean noGap = Arrays.stream(elem.getDims())
            .map(graph::getScale).anyMatch(s ->
               s instanceof TimeScale ||
                  s instanceof CategoricalScale && ((CategoricalScale) s).isFill()
            );

         if(!noGap) {
            return false;
         }
      }

      SizeFrame sizes = elem.getSizeFrame();

      // no size binding, the GAP in y should make the element inside plot already
      if(sizes == null) {
         return false;
      }

      return true;
   }

   public static void setDefaultFormulas(ChartInfo oinfo, ChartInfo info) {
      if(info == null || oinfo == null) {
         return;
      }

      List<XAggregateRef> xmeasures = GraphUtil.getMeasures(info.getXFields());
      List<XAggregateRef> ymeasures = GraphUtil.getMeasures(info.getYFields());
      List<XAggregateRef> oxmeasures = GraphUtil.getMeasures(oinfo.getXFields());
      List<XAggregateRef> oymeasures = GraphUtil.getMeasures(oinfo.getYFields());

      boolean xchanged = !oxmeasures.equals(xmeasures);
      boolean ychanged = !oymeasures.equals(ymeasures);

      // if x or y measures changed, check if need to update the default formula
      if(!xchanged && !ychanged) {
         return;
      }

      boolean nscattered = xmeasures.size() > 0 && ymeasures.size() > 0;
      boolean oscattered = oxmeasures.size() > 0 && oymeasures.size() > 0;
      boolean boxChanged =
         GraphTypes.isBoxplot(oinfo.getChartType()) != GraphTypes.isBoxplot(info.getChartType());

      // if the binding didn't change between scattered and non-scattered
      // chart, there is no need to change the default formula
      if(nscattered == oscattered && !boxChanged) {
         return;
      }

      if(GraphTypes.isAuto(oinfo.getChartType())) {
         return;
      }

      if(nscattered) {
         if(xchanged) {
            setDefaultFormulaNone(xmeasures, ymeasures);
         }
         else if(ychanged) {
            setDefaultFormulaNone(ymeasures, xmeasures);
         }
      }
      else {
         setDefaultFormula2(xmeasures, ymeasures, info);
      }
   }

   /**
    * Set the default formula for ref.
    */
   private static void setDefaultFormulaNone(List<XAggregateRef> refs,
                                      List<XAggregateRef> others)
   {
      boolean found = false;

      for(XAggregateRef field : others) {
         if(field.isAggregateEnabled()) {
            found = true;
            break;
         }
      }

      // default to non-aggregate if there are measures in both x and y
      if(found) {
         for(XAggregateRef ref : refs) {
            if(ref.isAggregateEnabled()) {
               if(ref instanceof VSAggregateRef) {
                  ((VSAggregateRef)ref).setFormulaValue("None");
               }
            }

            break;
         }
      }
   }

   private static void setDefaultFormula2(List<XAggregateRef> xmeasures,
                                          List<XAggregateRef> ymeasures, ChartInfo info)
   {
      boolean xmeasure = xmeasures.size() > 0;
      boolean ymeasure = ymeasures.size() > 0;

      if(!xmeasure && !ymeasure || xmeasure && ymeasure) {
         return;
      }

      List<XAggregateRef> arr = xmeasure ? xmeasures : ymeasures;

      for(XAggregateRef field : arr) {
         if((field.getRefType() & AbstractDataRef.CUBE) != 0) {
            continue;
         }

         if(!field.isAggregateEnabled()) {
            if((info instanceof MapInfo)) {
               continue;
            }

            AggregateFormula fl = AssetUtil.getDefaultFormula(field);

            if(field instanceof VSAggregateRef) {
               VSAggregateRef agg = (VSAggregateRef) field;
               agg.setFormulaValue(fl.getFormulaName());
            }
         }
      }
   }

   // add a white outline to point/line if it's on top of bar or area.
   public static void setDefaultOutlines(EGraph graph) {
      boolean blockElem = false;

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         if(elem instanceof IntervalElement || elem instanceof AreaElement) {
            blockElem = true;
         }
         else if(blockElem) {
            if(elem instanceof PointElement) {
               ((PointElement) elem).setOutlineColor(Color.WHITE);
            }
            else if(elem instanceof LineElement) {
               ((LineElement) elem).setOutlineColor(Color.WHITE);
            }
         }
      }
   }
}
