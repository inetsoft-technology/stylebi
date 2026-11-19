/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.report.composition.graph;

import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DataSetComparator;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VizContext;

import java.util.*;

/**
 * A marked funnel coloured by its stage takes its colour slots from the top stage down.
 */
final class FunnelColorOrder {
   private FunnelColorOrder() {
   }

   /**
    * The runtime stage of a marked funnel coloured by that stage in its default order, else null.
    */
   static XDimensionRef stage(VizContext ctx, VSChartInfo info) {
      if(ctx == null || !ctx.modern || info == null ||
         !GraphTypes.isFunnel(info.getRTChartType()))
      {
         return null;
      }

      AestheticRef color = info.getColorField();
      ChartRef[] ys = info.getRTYFields();

      if(color == null || !(color.getVisualFrame() instanceof CategoricalColorFrame) ||
         ys == null || ys.length == 0 || !(ys[ys.length - 1] instanceof XDimensionRef))
      {
         return null;
      }

      XDimensionRef stage = (XDimensionRef) ys[ys.length - 1];

      if(!Objects.equals(color.getFullName(), stage.getFullName())) {
         return null;
      }

      // any other order on the colour field is the author's
      if(!(color.getDataRef() instanceof XDimensionRef) ||
         ((XDimensionRef) color.getDataRef()).getOrder() != XConstants.SORT_ASC)
      {
         return null;
      }

      return stage;
   }

   /**
    * Whether the funnel keeps its colours to itself: its own Share Colors is off, or no other
    * chart on the viewsheet shares colours on the same column.
    */
   static boolean standalone(VSChartInfo info, Viewsheet vs, String chartName, int sourceType) {
      AestheticRef color = info.getColorField();

      if(vs == null || !isShared(color)) {
         return true;
      }

      String column = columnName(color, sourceType);

      for(Assembly assembly : vs.getAssemblies()) {
         if(!(assembly instanceof ChartVSAssembly) ||
            Objects.equals(chartName, assembly.getAbsoluteName()))
         {
            continue;
         }

         VSChartInfo other = ((ChartVSAssembly) assembly).getVSChartInfo();

         if(other == null) {
            continue;
         }

         for(AestheticRef ref : colorRefs(other)) {
            if(isShared(ref) && Objects.equals(column, columnName(ref, sourceType))) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * The stage values from the top of the funnel down: the reverse of the stage's sort, bound to
    * the whole data set.
    */
   static Comparator<Object> topFirst(XDimensionRef stage, DataSet data) {
      // the query sorts a copy of the stage, so its value sort lives on the data
      Comparator comp = data == null ? null : data.getComparator(stage.getFullName());

      if(comp == null) {
         comp = stage.createComparator(data);
      }

      if(comp == null) {
         return null;
      }

      Comparator<Object> bound = DataSetComparator.getComparator(comp, data);
      return bound.reversed();
   }

   /**
    * List a categorical frame's values in the given order. Colours stay with their values.
    */
   static void sortScale(VisualFrame frame, Comparator<Object> order) {
      if(order != null && frame.getScale() instanceof CategoricalScale) {
         Object[] vals = frame.getScale().getValues().clone();
         Arrays.sort(vals, order);
         ((CategoricalScale) frame.getScale()).init(vals);
      }
   }

   /**
    * List a marked funnel's stage colours from the top again, after a re-sync sorted them.
    *
    * @param stage the stage the generator ordered the colours by, or null.
    */
   static void relist(EGraph graph, VisualFrame frame, XDimensionRef stage, DataSet data) {
      // a graph a script built has no mark order, so its legend still reverses
      if(stage != null && graph.isLegendInMarkOrder() &&
         frame instanceof CategoricalColorFrame &&
         Objects.equals(stage.getFullName(), frame.getField()))
      {
         sortScale(frame, topFirst(stage, data));
      }
   }

   // aesthetic refs, a relation chart's node colour and a multi-style chart's measure colours
   private static List<AestheticRef> colorRefs(VSChartInfo info) {
      List<AestheticRef> refs = new ArrayList<>(AbstractChartInfo.getAestheticRefs(info));

      if(info instanceof RelationChartInfo) {
         refs.add(((RelationChartInfo) info).getNodeColorField());
      }

      if(info.isMultiAesthetic()) {
         for(ChartRef ref : info.getBindingRefs(false)) {
            if(ref instanceof ChartAggregateRef) {
               refs.add(((ChartAggregateRef) ref).getColorField());
            }
         }
      }

      return refs;
   }

   private static boolean isShared(AestheticRef ref) {
      return ref != null && ref.getVisualFrame() instanceof CategoricalColorFrame &&
         ((CategoricalColorFrame) ref.getVisualFrame()).isShareColors();
   }

   private static String columnName(AestheticRef ref, int sourceType) {
      return VSFrameVisitor.resolveColumnName(ref.getFullName(), ref, sourceType);
   }
}
