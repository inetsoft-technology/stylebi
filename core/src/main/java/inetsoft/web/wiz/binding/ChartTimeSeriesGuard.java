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
package inetsoft.web.wiz.binding;

import inetsoft.uql.XConstants;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.model.graph.calc.ChangeCalcInfo;
import inetsoft.web.wiz.binding.model.FieldRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses an explicit {@code timeSeries: true} on a chart dimension where the native Composer
 * never offers the "Time Series" option (bug #78214).
 *
 * <p>The rule is a port of the Composer's own {@code dimension-editor.component.ts}
 * ({@code timeSeriesSupported()} + {@code isTimeVisible()}) and {@code chart-fieldmc.component.ts}
 * ({@code isOuterDimRef()}), evaluated on the <em>post-write</em> {@link ChartBindingModel} -- the
 * model the mutator edits is the same DTO those components read, whereas the chart's live
 * {@code VSChartInfo} is still the pre-write one at that point. Refs built by
 * {@link FieldRefFactory#toChartRef} carry no {@code fullName}, so every match here is positional.
 *
 * <p>An unsupported {@code true} is not merely cosmetic: the server never gap-fills such a
 * dimension, but {@code ChartVSAQuery} still forces an ascending sort from the raw flag and
 * {@code VSChartDimensionRef} drops a sort-by-value, so a stored flag silently overrides the
 * caller's sort. Only an <em>explicit</em> {@code true} is refused: {@code false} is always
 * allowed, an omitted flag keeps the existing carry-forward behaviour, and a {@code true} that
 * merely re-states a flag the same column + date level already stores on that shelf is allowed
 * (A1 echo exemption) because native drag-and-drop stores {@code true} on outer/pie/waterfall
 * dimensions and {@code get_binding} echoes it back.
 *
 * <p>Deliberate leniencies, each documented where it applies: a y dimension that is outer only
 * because the x shelf holds no measure is not refused (not decidable at single-shelf write time --
 * the end state depends on the x write that may follow); dynamic ({@code $(x)}/{@code =expr}) date
 * levels are allowed for a date-typed (or not yet typed) column, as the Composer allows them; and
 * the CHANGE-calculator exception for aesthetic channels matches the calculator's
 * {@code columnName} against either the bare column or a {@code Level(column)} full name.
 */
final class ChartTimeSeriesGuard {
   /** Where the dimension is being bound. */
   enum Place { X, Y, GROUP, SINGLE, AESTHETIC }

   private ChartTimeSeriesGuard() {
   }

   /**
    * Throws if {@code field} explicitly asks for {@code timeSeries: true} on {@code dim} and the
    * post-write {@code model} makes that unsupported, unless it merely echoes a stored-true flag.
    *
    * @param previous the refs this write replaces on the same shelf/channel (pre-write), searched
    *                 for the echo exemption.
    */
   static void require(ChartBindingModel model, Place place, String shelf, int index,
                       ChartDimensionRefModel dim, FieldRef field,
                       List<? extends ChartRefModel> previous)
   {
      if(!Boolean.TRUE.equals(field.timeSeries())) {
         return;
      }

      for(ChartRefModel old : previous) {
         if(old instanceof ChartDimensionRefModel oldDim && oldDim.isTimeSeries() &&
            ChartBindingMutator.matches(oldDim, field))
         {
            return;
         }
      }

      String reason = unsupportedReason(model, place, shelf, index, dim);

      if(reason != null) {
         String column = field.column();
         throw new IllegalArgumentException(
            "timeSeries:true is refused for '" + column + "' on " + describe(place, shelf) +
            ": " + reason + ". The Composer only offers 'Time Series' on a date dimension " +
            "(year/quarter/month/week/day or a time level) that is the innermost x or y " +
            "dimension of a chart type that supports it. Stored anyway it would never fill " +
            "gaps in the axis, and it would force this dimension's sort back to ascending. " +
            "Send timeSeries:false (or omit it) for '" + column + "'.");
      }
   }

   /**
    * Whether {@code dim}'s stored timeSeries flag is one the Composer would honour: the flag is
    * set AND every part of the support rule holds.
    */
   static boolean isEffective(ChartBindingModel model, Place place, String shelf, int index,
                              ChartDimensionRefModel dim)
   {
      return dim.isTimeSeries() && unsupportedReason(model, place, shelf, index, dim) == null;
   }

   /** The first reason time series is unsupported for {@code dim}, or {@code null} if supported. */
   static String unsupportedReason(ChartBindingModel model, Place place, String shelf, int index,
                                   ChartDimensionRefModel dim)
   {
      String chart = chartTypeReason(model);

      if(chart != null) {
         return chart;
      }

      String column = columnOf(dim);
      String level = dateLevelReason(model, dim, column);

      if(level != null) {
         return level;
      }

      return switch(place) {
         case GROUP -> "it is on the group shelf, which never supports time series";
         case SINGLE -> "'" + shelf + "' is a single-field shelf, which never supports time " +
            "series";
         case AESTHETIC -> isChangeCalcDim(model, column) ? null :
            "an aesthetic channel dimension only supports time series when it is the " +
               "'columnName' of a CHANGE calculator measure on x/y";
         case X -> isOuter(model.getXFields(), index, false, model) ?
            "it is an outer dimension on the x shelf (only the last dimension, with nothing " +
               "after it, can be time series)" : null;
         case Y -> isOuter(model.getYFields(), index, true, model) ?
            "it is an outer dimension on the y shelf" : null;
      };
   }

   // ── chart type ───────────────────────────────────────────────────────────────────────────

   /** {@code isTimeVisible()} -- waterfall, polar and merged types (except stock/candle/boxplot). */
   private static String chartTypeReason(ChartBindingModel model) {
      if(anyType(model, GraphTypes::isWaterfall)) {
         return "a waterfall chart does not support time series";
      }

      if(anyType(model, ChartTimeSeriesGuard::isPolar)) {
         return "a pie/donut/radar chart does not support time series";
      }

      int top = model.getChartType();
      boolean schemaException = top == GraphTypes.CHART_STOCK || top == GraphTypes.CHART_CANDLE ||
         top == GraphTypes.CHART_BOXPLOT;

      if(anyType(model, ChartTimeSeriesGuard::isMerged) && !schemaException) {
         return "this chart type (radar, map, treemap, sunburst, gantt, funnel, relation, " +
            "mekko, scatter contour ...) does not support time series";
      }

      return null;
   }

   /** The TS {@code GraphTypes.isPolar}: pie family + radar (not sunburst, which is merged). */
   private static boolean isPolar(int type) {
      return GraphTypes.isPie(type) || GraphTypes.isRadar(type);
   }

   /** The TS {@code GraphTypes.isMergedGraphType}: Java's list plus scatter contour. */
   private static boolean isMerged(int type) {
      return GraphTypes.isMergedGraphType(type) || GraphTypes.isScatteredContour(type);
   }

   /** {@code GraphUtil.isChartType}: the chart type, or under multi-styles any x/y measure's. */
   private static boolean anyType(ChartBindingModel model, java.util.function.IntPredicate test) {
      if(!model.isMultiStyles()) {
         return test.test(resolve(model.getChartType(), model.getRTChartType()));
      }

      for(ChartAggregateRefModel aggregate : xyAggregates(model)) {
         if(test.test(resolve(aggregate.getChartType(), aggregate.getRTChartType()))) {
            return true;
         }
      }

      return false;
   }

   private static int resolve(int type, int runtimeType) {
      return type == GraphTypes.CHART_AUTO ? runtimeType : type;
   }

   // ── column / date level ──────────────────────────────────────────────────────────────────

   /** {@code dateType}: a date-typed column at a truncating (non-part, non-none) level. */
   private static String dateLevelReason(ChartBindingModel model, ChartDimensionRefModel dim,
                                         String column)
   {
      String type = dim.getDataType();

      if(type == null) {
         type = FieldRefFactory.dataTypeOf(model, column);
      }

      if(type != null && !XSchema.isDateType(type)) {
         return "'" + column + "' is a " + type + " column, not a date column";
      }

      String level = dim.getDateLevel();

      if(level != null && DateLevels.isDynamicValue(level.trim())) {
         // The Composer parses a dynamic level to NaN, which its date test treats as a date
         // level -- so a dynamic level on a date-typed column is allowed.
         return null;
      }

      int value;

      try {
         value = level == null || level.isBlank() ? -1 : Integer.parseInt(level.trim());
      }
      catch(NumberFormatException e) {
         value = -1;
      }

      if(value == XConstants.NONE_DATE_GROUP || value < 0) {
         return "'" + column + "' has no date level -- set dateLevel to year, quarter, month, " +
            "week, day, hour, minute or second";
      }

      if((value & XConstants.PART_DATE_GROUP) != 0) {
         return "date level '" + DateLevels.name(level) + "' is a part-of-date level (it groups " +
            "by a component, e.g. month_of_year), which has no continuous time axis to fill";
      }

      return null;
   }

   // ── placement ────────────────────────────────────────────────────────────────────────────

   /**
    * {@code isOuterDimRef()}: scans the shelf's leading run of dimensions; the dimension at
    * {@code index} is outer iff it is in that run and is not the shelf's last field. On y, stock
    * and candle always count it outer. The Composer also counts a y dimension outer when x has
    * dimensions but no measure; that depends on an x write that may follow, so it is not refused
    * here.
    */
   private static boolean isOuter(List<ChartRefModel> refs, int index, boolean y,
                                  ChartBindingModel model)
   {
      for(int i = 0; i < refs.size(); i++) {
         if(!(refs.get(i) instanceof ChartDimensionRefModel)) {
            return false;
         }

         if(i != index) {
            continue;
         }

         int top = model.getChartType();

         return i != refs.size() - 1 ||
            y && (top == GraphTypes.CHART_STOCK || top == GraphTypes.CHART_CANDLE);
      }

      return false;
   }

   /**
    * {@code isChangeCalcDim()}: some x/y measure carries a CHANGE calculator whose
    * {@code columnName} names this dimension. The calculator stores the dimension's full name
    * (e.g. {@code Month(Order Date)}), so the bare column and a {@code Level(column)} form both match.
    */
   private static boolean isChangeCalcDim(ChartBindingModel model, String column) {
      if(column == null) {
         return false;
      }

      for(ChartAggregateRefModel aggregate : xyAggregates(model)) {
         if(aggregate.getCalculateInfo() instanceof ChangeCalcInfo change &&
            change.getColumnName() != null)
         {
            String name = change.getColumnName();

            if(name.equalsIgnoreCase(column) ||
               name.toLowerCase().endsWith("(" + column.toLowerCase() + ")"))
            {
               return true;
            }
         }
      }

      return false;
   }

   private static List<ChartAggregateRefModel> xyAggregates(ChartBindingModel model) {
      List<ChartAggregateRefModel> aggregates = new ArrayList<>();

      for(List<ChartRefModel> refs : List.of(model.getXFields(), model.getYFields())) {
         for(ChartRefModel ref : refs) {
            if(ref instanceof ChartAggregateRefModel aggregate) {
               aggregates.add(aggregate);
            }
         }
      }

      return aggregates;
   }

   private static String columnOf(ChartDimensionRefModel dim) {
      return dim.getColumnValue() == null ? dim.getName() : dim.getColumnValue();
   }

   private static String describe(Place place, String shelf) {
      return switch(place) {
         case AESTHETIC -> "the " + shelf + " channel";
         default -> "the " + shelf + " shelf";
      };
   }
}
