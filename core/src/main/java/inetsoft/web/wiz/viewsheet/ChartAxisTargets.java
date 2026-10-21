/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.web.wiz.viewsheet;

import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.RadarVSChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.viewsheet.controller.chart.VSChartAxesVisibilityService;

import java.util.*;

/**
 * Resolves the column name that {@code set_chart_element_visibility} hides an axis by, before the
 * runtime is touched.
 *
 * <p><b>Why this exists.</b> {@code VSChartAxesVisibilityService.hideAxis} is the Composer UI's
 * handler, whose names always come from a real axis, so for a name that matches nothing it falls
 * back to a chart-level descriptor: on a merged layout that is the primary (or, with
 * {@code secondary}, the secondary) measure axis. Through the tool a typo, a number or the raw
 * column of a date-grouped dimension therefore hid a different axis and reported success. The
 * legend path of the same endpoint already refuses an unresolved name; this is the axis
 * counterpart.
 *
 * <p><b>One lookup.</b> The name is resolved by {@link VSChartAxesVisibilityService#findAxisRef}
 * and its special-name predicates, the very functions {@code hideAxis} uses, so a name accepted
 * here is exactly a name the handler resolves. Matching is exact and case-sensitive, as there.
 *
 * <p><b>The secondary flag.</b> On the merged-layout branch the handler ignores a measure's own
 * axis and lets {@code secondary} alone pick the descriptor, so a valid name with the wrong flag
 * hides the other axis while naming this one. That mismatch is refused too. A separated layout
 * ignores the flag, so it is not checked there.
 *
 * <p>The axis type names ({@code x}, {@code y}, {@code y2}) are not column names and are refused
 * like any other unresolved name; the message says so.
 */
final class ChartAxisTargets {
   private ChartAxisTargets() {
   }

   /**
    * @throws IllegalArgumentException if {@code target} would not hide the axis it names.
    */
   static void requireAxisTarget(ChartVSAssembly chart, String target, boolean secondary) {
      ChartVSAssemblyInfo info = chart.getChartInfo();
      VSChartInfo chartInfo = chart.getVSChartInfo();

      if(VSChartAxesVisibilityService.isRadarLabelAxis(chartInfo, target) ||
         VSChartAxesVisibilityService.isMeasureNameAxis(chartInfo, target))
      {
         return;
      }

      ChartRef ref = VSChartAxesVisibilityService.findAxisRef(info, chartInfo, target);

      if(ref == null) {
         throw new IllegalArgumentException(unresolved(chartInfo, target));
      }

      if(ref.isMeasure() && VSChartAxesVisibilityService.usesMergedAxisBranch(chartInfo) &&
         ref instanceof ChartAggregateRef agg && agg.isSecondaryY() != secondary)
      {
         throw new IllegalArgumentException(
            "The axis for '" + ref.getFullName() + "' is the " +
            (agg.isSecondaryY() ? "secondary" : "primary") + " one on this chart, but " +
            "'secondary' was " + secondary + ". On this layout 'secondary' alone picks which " +
            "measure axis is hidden, so this call would hide the " +
            (secondary ? "secondary" : "primary") + " axis instead of the one you named. Pass " +
            "secondary: " + agg.isSecondaryY() + ".");
      }
   }

   /**
    * The column names an axis can be hidden by on this chart.
    */
   static List<String> axisNames(VSChartInfo chartInfo) {
      Set<String> names = new LinkedHashSet<>();

      for(ChartRef ref : chartInfo.getBindingRefs(false)) {
         if(ref != null) {
            names.add(ref.getFullName());
         }
      }

      for(ChartRef[] refs : new ChartRef[][] { chartInfo.getRTXFields(),
                                               chartInfo.getRTYFields() })
      {
         if(refs != null) {
            for(ChartRef ref : refs) {
               if(ref != null) {
                  names.add(ref.getFullName());
               }
            }
         }
      }

      if(chartInfo instanceof RadarVSChartInfo) {
         names.add("_Parallel_Label_");
      }

      return new ArrayList<>(names);
   }

   private static String unresolved(VSChartInfo chartInfo, String target) {
      List<String> names = axisNames(chartInfo);
      StringBuilder msg = new StringBuilder();
      msg.append("This chart has no axis for '").append(target).append("', so nothing was hidden. ");

      List<String> close = new ArrayList<>();

      for(ChartRef ref : chartInfo.getBindingRefs(false)) {
         if(ref != null && target.equals(ref.getName()) && !target.equals(ref.getFullName())) {
            close.add(ref.getFullName());
         }
      }

      if(close.size() == 1) {
         msg.append("Did you mean '").append(close.get(0)).append("'? ");
      }

      msg.append("An axis is named by its column's full name (e.g. 'Month(Order:Date)' for a ")
         .append("date-grouped dimension), not by the raw column or an axis type (x, y, x2, y2). ");

      if(names.isEmpty()) {
         msg.append("This chart has no axes to hide.");
      }
      else {
         msg.append("Axes on this chart: ").append(String.join(", ", names)).append(".");
      }

      return msg.toString();
   }
}
