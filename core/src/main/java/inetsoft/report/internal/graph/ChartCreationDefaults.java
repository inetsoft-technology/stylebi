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
package inetsoft.report.internal.graph;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.util.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * The binding a chart gets when it is created or changes type under the modern mark. Every rule
 * returns at once on a chart without the mark.
 */
public final class ChartCreationDefaults {
   private ChartCreationDefaults() {
   }

   /** Reads a category's values in source order, or null when they cannot be read. */
   @FunctionalInterface
   public interface SourceOrder {
      List<Object> valuesInSourceOrder(VSDimensionRef dim);

      SourceOrder NONE = dim -> null;
   }

   /** The most distinct values saved as a manual order. */
   public static final int MAX_ORDERED_VALUES = 100;

   /** The level a new date field starts at. */
   public static int newDateLevel(String dataType, boolean axisShelf, VizContext ctx) {
      if(XSchema.TIME.equals(dataType)) {
         return DateRangeRef.HOUR_INTERVAL;
      }

      return ctx.modern && axisShelf ? DateRangeRef.MONTH_INTERVAL : DateRangeRef.YEAR_INTERVAL;
   }

   /** The type an area pick becomes: stacked when it enters the area family on a marked chart. */
   public static int stackedAreaType(int oldType, int newType, VizContext ctx) {
      if(ctx.modern && (newType == GraphTypes.CHART_AREA ||
                        newType == GraphTypes.CHART_STEP_AREA) && !GraphTypes.isArea(oldType))
      {
         return stackedCounterpart(newType);
      }

      return newType;
   }

   public static int stackedCounterpart(int type) {
      switch(type) {
      case GraphTypes.CHART_AREA:
         return GraphTypes.CHART_AREA_STACK;
      case GraphTypes.CHART_STEP_AREA:
         return GraphTypes.CHART_STEP_AREA_STACK;
      default:
         return type;
      }
   }

   /** Whether a marked candle refuses a dimension on Color. */
   public static boolean refusesColorDimension(ChartInfo info, int dropRegion, DataRef ref,
                                               VizContext ctx)
   {
      return ctx.modern && GraphTypes.isCandle(info.getChartType()) &&
         dropRegion == ChartConstants.DROP_REGION_COLOR && GraphUtil.isDimension(ref);
   }

   /** Sorts each pie's slices largest first when the slice is on the default order. */
   public static void applyPieOrder(ChartInfo info, VizContext ctx) {
      if(!ctx.modern) {
         return;
      }

      if(info.isMultiStyles()) {
         for(VSDataRef ref : info.getAggregateRefs()) {
            if(ref instanceof ChartAggregateRef) {
               ChartAggregateRef aggr = (ChartAggregateRef) ref;

               if(GraphTypes.isPie(aggr.getChartType())) {
                  sortSlice(aggr, aggr.getFullName());
               }
            }
         }
      }
      else if(GraphTypes.isPie(info.getChartType())) {
         sortSlice(info, firstMeasureName(info));
      }
   }

   private static String firstMeasureName(ChartInfo info) {
      for(ChartRef[] fields : new ChartRef[][] { info.getYFields(), info.getXFields() }) {
         for(ChartRef field : fields) {
            if(field instanceof ChartAggregateRef) {
               return field.getFullName();
            }
         }
      }

      return null;
   }

   private static void sortSlice(ChartBindable bindable, String measure) {
      if(measure == null) {
         return;
      }

      DataRef slice = sliceDimension(bindable);

      if(!(slice instanceof VSDimensionRef)) {
         return;
      }

      VSDimensionRef dim = (VSDimensionRef) slice;

      if(dim.isDateTime() || !isDefaultOrder(dim)) {
         return;
      }

      dim.setOrder(XConstants.SORT_VALUE_DESC);
      dim.setSortByColValue(measure);
   }

   private static DataRef sliceDimension(ChartBindable bindable) {
      for(AestheticRef aes : new AestheticRef[] { bindable.getColorField(),
                                                  bindable.getShapeField(),
                                                  bindable.getSizeField() })
      {
         if(aes != null && GraphUtil.isDimension(aes.getDataRef())) {
            return aes.getDataRef();
         }
      }

      return null;
   }

   private static boolean isDefaultOrder(VSDimensionRef dim) {
      List<?> manual = dim.getManualOrderList();
      return dim.getOrder() == XConstants.SORT_ASC && (manual == null || manual.isEmpty());
   }

   /** Saves a waterfall category's source order as its manual order. */
   public static void applyWaterfallOrder(ChartInfo info, VizContext ctx, SourceOrder order) {
      if(!ctx.modern || order == null || !GraphTypes.isWaterfall(info.getChartType())) {
         return;
      }

      ChartRef[] fields = info.isInvertedGraph() ? info.getYFields() : info.getXFields();
      VSDimensionRef dim = null;

      for(int i = fields.length - 1; i >= 0; i--) {
         if(GraphUtil.isDimension(fields[i])) {
            dim = fields[i] instanceof VSDimensionRef ? (VSDimensionRef) fields[i] : null;
            break;
         }
      }

      if(dim == null || dim.isDateTime() || dim.isNameGroup() || !isDefaultOrder(dim)) {
         return;
      }

      List<Object> values = order.valuesInSourceOrder(dim);

      if(values == null || values.isEmpty() || values.size() > MAX_ORDERED_VALUES) {
         return;
      }

      List<String> strings = new ArrayList<>();

      for(Object value : values) {
         if(value != null) {
            strings.add(Tool.getDataString(value));
         }
      }

      if(strings.isEmpty()) {
         return;
      }

      dim.setOrder(XConstants.SORT_SPECIFIC);
      dim.setManualOrderList(strings);
   }

   /** Colours a treemap family chart by its top level. */
   public static void applyTopLevelColor(ChartInfo info, VizContext ctx) {
      if(GraphTypes.isTreemap(info.getChartType())) {
         colorByFirstGroup(info, ctx);
      }
   }

   /** Colours a marimekko by its break by. */
   public static void applyBreakByColor(ChartInfo info, VizContext ctx) {
      if(GraphTypes.isMekko(info.getChartType())) {
         colorByFirstGroup(info, ctx);
      }
   }

   private static void colorByFirstGroup(ChartInfo info, VizContext ctx) {
      if(!ctx.modern || info.getColorField() != null || info.getGroupFieldCount() == 0) {
         return;
      }

      ChartRef group = info.getGroupFields()[0];

      if(!(group instanceof VSDimensionRef)) {
         return;
      }

      AestheticRef color = new VSAestheticRef();
      color.setDataRef((DataRef) ((VSDimensionRef) group).clone());
      color.setVisualFrame(new CategoricalColorFrame());
      info.setColorField(color);
      GraphUtil.fixVisualFrames(info, ctx);
   }

   /** Moves a candle's Color dimension to the end of Y, where it makes a panel. */
   public static void moveColorDimensionToPanels(ChartInfo info, VizContext ctx) {
      if(!ctx.modern || !GraphTypes.isCandle(info.getChartType())) {
         return;
      }

      AestheticRef color = info.getColorField();

      if(color == null || !GraphUtil.isDimension(color.getDataRef()) ||
         !(color.getDataRef() instanceof ChartRef))
      {
         return;
      }

      info.addYField((ChartRef) color.getDataRef());
      info.setColorField(null);
   }

   /** Applies the rule of each family the chart enters when its type changes. */
   public static void afterTypeChange(ChartInfo info, int oldType, int newType, VizContext ctx,
                                      SourceOrder order)
   {
      if(!ctx.modern) {
         return;
      }

      if(!GraphTypes.isPie(oldType) && GraphTypes.isPie(newType)) {
         applyPieOrder(info, ctx);
      }

      if(!GraphTypes.isWaterfall(oldType) && GraphTypes.isWaterfall(newType)) {
         applyWaterfallOrder(info, ctx, order);
      }

      if(!GraphTypes.isTreemap(oldType) && GraphTypes.isTreemap(newType)) {
         applyTopLevelColor(info, ctx);
      }

      if(!GraphTypes.isMekko(oldType) && GraphTypes.isMekko(newType)) {
         applyBreakByColor(info, ctx);
      }

      if(!GraphTypes.isCandle(oldType) && GraphTypes.isCandle(newType)) {
         moveColorDimensionToPanels(info, ctx);
      }
   }

   /** Applies the rules a drop triggers, by the shelf the fields landed on. */
   public static void afterDrop(ChartInfo info, int dropRegion, List<? extends DataRef> landed,
                                VizContext ctx, SourceOrder order)
   {
      if(!ctx.modern || landed == null || landed.isEmpty()) {
         return;
      }

      boolean dimension = false;
      boolean measure = false;
      DataRef firstDimension = null;

      for(DataRef ref : landed) {
         if(GraphUtil.isDimension(ref)) {
            dimension = true;

            if(firstDimension == null) {
               firstDimension = ref;
            }
         }
         else {
            measure = true;
         }
      }

      boolean aesthetic = dropRegion == ChartConstants.DROP_REGION_COLOR ||
         dropRegion == ChartConstants.DROP_REGION_SHAPE ||
         dropRegion == ChartConstants.DROP_REGION_SIZE;

      if(hasPie(info) && (dimension && aesthetic || measure)) {
         applyPieOrder(info, ctx);
      }

      if(dimension && GraphTypes.isWaterfall(info.getChartType())) {
         boolean category = dropRegion == ChartConstants.DROP_REGION_X ||
            dropRegion == ChartConstants.DROP_REGION_X2 ||
            info.isInvertedGraph() && (dropRegion == ChartConstants.DROP_REGION_Y ||
                                       dropRegion == ChartConstants.DROP_REGION_Y2);

         if(category) {
            applyWaterfallOrder(info, ctx, order);
         }
      }

      if(dimension && dropRegion == ChartConstants.DROP_REGION_GROUP) {
         if(GraphTypes.isTreemap(info.getChartType()) && isFirstGroup(info, landed)) {
            applyTopLevelColor(info, ctx);
         }
         else if(GraphTypes.isMekko(info.getChartType())) {
            applyBreakByColor(info, ctx);
         }
      }
   }

   /** Applies the rules the wizard's new chart does not already get. */
   public static void afterWizard(ChartInfo info, VizContext ctx, SourceOrder order) {
      applyPieOrder(info, ctx);
      applyWaterfallOrder(info, ctx, order);
   }

   /** Applies the rules to the copy a reset gives a chart that gets the mark. */
   public static void forResetCopy(ChartInfo info, VizContext ctx, SourceOrder order) {
      if(!ctx.modern) {
         return;
      }

      info.setChartType(stackedCounterpart(info.getChartType()));

      if(info.isMultiStyles()) {
         for(VSDataRef ref : info.getAggregateRefs()) {
            if(ref instanceof ChartAggregateRef) {
               ChartAggregateRef aggr = (ChartAggregateRef) ref;
               aggr.setChartType(stackedCounterpart(aggr.getChartType()));
            }
         }
      }

      applyPieOrder(info, ctx);
      applyWaterfallOrder(info, ctx, order);
      applyTopLevelColor(info, ctx);
      applyBreakByColor(info, ctx);
      moveColorDimensionToPanels(info, ctx);
   }

   private static boolean hasPie(ChartInfo info) {
      if(!info.isMultiStyles()) {
         return GraphTypes.isPie(info.getChartType());
      }

      for(VSDataRef ref : info.getAggregateRefs()) {
         if(ref instanceof ChartAggregateRef &&
            GraphTypes.isPie(((ChartAggregateRef) ref).getChartType()))
         {
            return true;
         }
      }

      return false;
   }

   private static boolean isFirstGroup(ChartInfo info, List<? extends DataRef> landed) {
      if(info.getGroupFieldCount() == 0) {
         return false;
      }

      String first = info.getGroupFields()[0].getName();

      for(DataRef ref : landed) {
         if(GraphUtil.isDimension(ref) && first != null && first.equals(ref.getName())) {
            return true;
         }
      }

      return false;
   }
}
