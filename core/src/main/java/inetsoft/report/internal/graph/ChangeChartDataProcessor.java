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
package inetsoft.report.internal.graph;

import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.uql.viewsheet.graph.*;

import java.util.*;

/**
 * Fix information when chart data is changed.
 */
public class ChangeChartDataProcessor extends ChangeChartProcessor {
   /**
    * Constructor.
    */
   public ChangeChartDataProcessor() {
      super();
   }

   /**
    * Constructor.
    */
   public ChangeChartDataProcessor(ChartInfo info) {
      this(info, true);
   }

   /**
    * Constructor.
    */
   public ChangeChartDataProcessor(ChartInfo info, boolean fixSorting) {
      super();

      this.info = info;
      this.fixSorting = fixSorting;
   }

   /**
    * Process.
    */
   public ChartInfo process() {
      // e.g. bar chart and changed to measures on x and y, switch to auto
      // this is same as changing dim/measure to dim/dim and swith to auto
      if(!isXYSupported() && GraphTypeUtil.isXYChart(info)) {
         info = (new ChangeChartTypeProcessor(
            info.getChartType(), GraphTypes.CHART_AUTO, null, info)).process();
      }

      sortRefs(info);
      fixShapeField(info, info, getChartType(info, null));
      fixSizeFrame(info);
      syncTopN(info);
      fixAggregateRefs(info);
      fixMapDimensionRefs(info);

      if(fixSorting) {
         new ChangeChartProcessor().fixParetoSorting(info);
      }

      // design setting may have changed, clear runtime refs to avoid out of sync
      info.clearRuntime();

      return info;
   }

   public void sortRefs(ChartInfo cinfo) {
      sortRefs(cinfo, false);
   }

   /**
    * Fix binding info.
    */
   public static void sortRefs(ChartInfo cinfo, boolean rt) {
      List<ChartRef> xrefs = Arrays.asList(rt ? cinfo.getRTXFields() : cinfo.getXFields());
      List<ChartRef> yrefs = Arrays.asList(rt ? cinfo.getRTYFields() : cinfo.getYFields());

      Collections.sort(xrefs, new ChangeChartProcessor.VComparator());
      Collections.sort(yrefs, new ChangeChartProcessor.VComparator());

      if(rt) {
         cinfo.setRTXFields(xrefs.toArray(new ChartRef[0]));
         cinfo.setRTYFields(yrefs.toArray(new ChartRef[0]));
         return;
      }

      cinfo.removeXFields();
      cinfo.removeYFields();

      for(int i = 0; i < xrefs.size(); i++) {
         cinfo.addXField(i, xrefs.get(i));
      }

      for(int i = 0; i < yrefs.size(); i++) {
         cinfo.addYField(i, yrefs.get(i));
      }
   }

   /**
    * Check if the chart supports measure bounds on both x and y.
    */
   private boolean isXYSupported() {
      if(info.isMultiStyles()) {
         return true;
      }

      int type = info.getRTChartType();

      switch(type) {
      case GraphTypes.CHART_POINT:
      case GraphTypes.CHART_POINT_STACK:
      case GraphTypes.CHART_LINE:
      case GraphTypes.CHART_LINE_STACK:
      case GraphTypes.CHART_STEP:
      case GraphTypes.CHART_JUMP:
      case GraphTypes.CHART_STEP_AREA:
      case GraphTypes.CHART_STEP_STACK:
      case GraphTypes.CHART_STEP_AREA_STACK:
      case GraphTypes.CHART_AREA:
      case GraphTypes.CHART_AREA_STACK:
      case GraphTypes.CHART_MAP:
      case GraphTypes.CHART_SCATTER_CONTOUR:
      case GraphTypes.CHART_MAP_CONTOUR:
         return true;
      }

      return false;
   }

   private ChartInfo info;
   private boolean fixSorting = true;
}
