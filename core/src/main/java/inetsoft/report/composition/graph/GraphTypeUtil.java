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
package inetsoft.report.composition.graph;

import inetsoft.graph.EGraph;
import inetsoft.graph.Visualizable;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.*;
import inetsoft.graph.element.*;
import inetsoft.graph.visual.LineVO;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.sree.security.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This class contains utility methods for graph type related logic.
 *
 * @author InetSoft Technology Corp.
 * @since  13.7
 */
public class GraphTypeUtil {
   /**
    * Check if is waterfall graph.
    */
   public static boolean isWaterfall(ChartInfo info) {
      List<ChartAggregateRef> aggrs = new ArrayList<>();

      for(ChartRef[] refs :
             new ChartRef[][]{ info.getYFields(), info.getXFields() }) {
         for(ChartRef ref : refs) {
            if(ref instanceof ChartAggregateRef) {
               aggrs.add((ChartAggregateRef) ref);
            }
         }
      }

      //@by ClementWang, keep the same logic with previous adhoc(GraphUtil.as).
      /*if(aggrs.size() == 0) {
        return false;
        }*/

      if(!info.isMultiStyles()) {
         return GraphTypes.isWaterfall(info.getChartType());
      }

      if(info instanceof MergedChartInfo) {
         return false;
      }

      for(ChartAggregateRef ref : aggrs) {
         if(GraphTypes.isWaterfall(ref.getChartType())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if is merged graph.
    */
   public static boolean isMergedGraphType(ChartInfo info) {
      List<ChartAggregateRef> aggrs = new ArrayList<>();

      if(!info.isMultiStyles()) {
         return GraphTypes.isMergedGraphType(info.getChartType());
      }

      if(info instanceof MergedChartInfo) {
         return false;
      }

      for(ChartRef[] refs :
             new ChartRef[][]{ info.getYFields(), info.getXFields() }) {
         for(ChartRef ref : refs) {
            if(ref instanceof ChartAggregateRef) {
               aggrs.add((ChartAggregateRef) ref);
            }
         }
      }

      if(aggrs.size() == 0) {
         return false;
      }

      for(ChartAggregateRef ref : aggrs) {
         if(GraphTypes.isMergedGraphType(ref.getChartType())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the chart is polar.
    *
    * @param pie true to only return true if it's a pie.
    */
   public static boolean isPolar(ChartInfo cinfo, boolean pie) {
      try {
         return isChartType(cinfo, t -> pie ? GraphTypes.isPie(t) : GraphTypes.isPolar(t));
      }
      catch(Exception ex) {
         LOG.warn("Failed to determine if a chart uses polar coordinates", ex);
         return false;
      }
   }

   /**
    * Check if the chart is 3D coord.
    */
   public static boolean is3DBar(ChartInfo cinfo) {
      try {
         return isChartType(cinfo, t -> GraphTypes.is3DBar(t));
      }
      catch(Exception ex) {
         LOG.warn("Failed to determine if a chart uses 3-D coordinates", ex);
         return false;
      }
   }

   public static boolean isChartType(ChartInfo cinfo, final int... types) {
      return isChartType(cinfo, (type) -> {
            for(int t : types) {
               if(t == type) {
                  return true;
               }
            }

            return false;
         });
   }

   /**
    * Check if it's the chart type.
    *
    * @param func a static method the take an integer chart type and return boolean.
    */
   public static boolean isChartType(ChartInfo cinfo, Function<Integer, Boolean> func) {
      return isChartType(cinfo, false, func);
   }

   /**
    * Check if it's the chart type.
    *
    * @param func a static method the take an integer chart type and return boolean.
    */
   public static boolean isChartType(ChartInfo cinfo, boolean rt, Function<Integer, Boolean> func) {
      if(cinfo == null) {
         return false;
      }

      if(!cinfo.isMultiStyles()) {
         return func.apply(cinfo.getRTChartType());
      }

      ChartRef[] fields = rt ? cinfo.getRTXFields() : cinfo.getXFields();

      for(int i = 0; i < fields.length; i++) {
         if(!(fields[i] instanceof ChartAggregateRef)) {
            continue;
         }

         int ctype = ((ChartAggregateRef) (fields[i])).getRTChartType();

         if(func.apply(ctype)) {
            return true;
         }
      }

      fields = rt ? cinfo.getRTYFields() : cinfo.getYFields();

      for(int i = 0; i < fields.length; i++) {
         if(!(fields[i] instanceof ChartAggregateRef)) {
            continue;
         }

         int ctype = ((ChartAggregateRef) (fields[i])).getRTChartType();

         if(func.apply(ctype)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the chart is map.
    */
   public static boolean isMap(ChartInfo info) {
      return info instanceof MapInfo;
   }

   /**
    * Get the runtime chart type.
    */
   public static int getRTChartType(ChartInfo cinfo) {
      if(!cinfo.isMultiStyles()) {
         return cinfo.getRTChartType();
      }

      ChartRef[] refs = cinfo.getRTYFields();
      ChartRef ref = GraphUtil.getLastField(refs);

      if(ref instanceof ChartAggregateRef) {
         return ((ChartAggregateRef) ref).getRTChartType();
      }

      refs = cinfo.getRTXFields();
      ref = GraphUtil.getLastField(refs);

      if(ref instanceof ChartAggregateRef) {
         return ((ChartAggregateRef) ref).getRTChartType();
      }

      return -1;
   }

   /**
    * Check if chart with line chart type.
    */
   public static boolean hasLineChartType(ChartInfo cinfo) {
      return hasChartType(cinfo, GraphTypes.CHART_LINE) ||
         hasChartType(cinfo, GraphTypes.CHART_LINE_STACK) ||
         hasChartType(cinfo, GraphTypes.CHART_STEP) ||
         hasChartType(cinfo, GraphTypes.CHART_STEP_STACK) ||
         hasChartType(cinfo, GraphTypes.CHART_JUMP);
   }

   /**
    * Check if chart with point chart type.
    */
   public static boolean hasVSPointChartType(ChartInfo cinfo) {
      return hasVSChartType(cinfo, GraphTypes.CHART_POINT) ||
         hasVSChartType(cinfo, GraphTypes.CHART_POINT_STACK);
   }

   /**
    * Get the available chart type.
    */
   public static boolean hasVSChartType(ChartInfo cinfo, int ctype) {
      if(cinfo == null) {
         return false;
      }

      int type = -1;

      if(cinfo.isMultiStyles()) {
         ChartRef[] yrefs = cinfo.getRTYFields();

         for(int i = 0; i < yrefs.length; i++) {
            if(yrefs[i] instanceof ChartAggregateRef) {
               if(((ChartAggregateRef) yrefs[i]).getChartType() == GraphTypes.CHART_AUTO) {
                  type = ((ChartAggregateRef) yrefs[i]).getRTChartType();
               }
               else {
                  type = ((ChartAggregateRef) yrefs[i]).getChartType();
               }

               if(type == ctype) {
                  return true;
               }
            }
         }

         ChartRef[] xrefs = cinfo.getRTXFields();

         for(int i = 0; i < xrefs.length; i++) {
            if(xrefs[i] instanceof ChartAggregateRef) {
               if(((ChartAggregateRef) xrefs[i]).getChartType() == GraphTypes.CHART_AUTO) {
                  type = ((ChartAggregateRef) xrefs[i]).getRTChartType();
               }
               else {
                  type = ((ChartAggregateRef) xrefs[i]).getChartType();
               }

               if(type == ctype) {
                  return true;
               }
            }
         }

         // handle no measure case
         Object xref = xrefs.length == 0 ? null : xrefs[xrefs.length - 1];
         Object yref = yrefs.length == 0 ? null : yrefs[yrefs.length - 1];
         Boolean xdim = xref instanceof XDimensionRef;
         Boolean ydim = yref instanceof XDimensionRef;

         if(xref == null) {
            type = ydim ? GraphTypes.CHART_POINT : GraphTypes.CHART_BAR;
         }
         else if(yref == null) {
            type = xdim ? GraphTypes.CHART_POINT : GraphTypes.CHART_BAR;
         }
         else if(xdim && ydim) {
            type = GraphTypes.CHART_POINT;
         }

         return type == ctype;
      }

      type = (cinfo.getChartType() == GraphTypes.CHART_AUTO) ?
         cinfo.getRTChartType() : cinfo.getChartType();
      return type == ctype;
   }

   /**
    * Check if chart with specified chart type.
    */
   public static boolean hasChartType(ChartInfo cinfo, int type) {
      if(!cinfo.isMultiStyles()) {
         int ctype = cinfo.getRTChartType();
         return ctype == type;
      }

      //since the chart has multiple style you must check all of the chartref for the type
      ChartRef[] refs = cinfo.getRTYFields();

      for(ChartRef ref : refs) {
         if(ref instanceof ChartAggregateRef) {
            int ctype = ((ChartAggregateRef) ref).getRTChartType();

            if(ctype == type) {
               return true;
            }
         }
      }

      refs = cinfo.getRTXFields();

      for(ChartRef ref : refs) {
         if(ref instanceof ChartAggregateRef) {
            int ctype = ((ChartAggregateRef) ref).getRTChartType();

            if(ctype == type) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if measure is bound to both x and y.
    */
   public static boolean isXYChart(ChartInfo info) {
      if(info == null) {
         return false;
      }

      ChartRef[][] xyfields = { info.getXFields(), info.getYFields() };

      for(ChartRef[] fields : xyfields) {
         if(fields == null || fields.length == 0) {
            return false;
         }

         if(!fields[fields.length - 1].isMeasure()) {
            return false;
         }
      }

      return true;
   }

   public static boolean isWordCloud(ChartInfo info) {
      return isWordCloud(info, false);
   }

   public static boolean isWordCloud(ChartInfo info, boolean ignoreShape) {
      // condition should match setWordCloud() (48081).
      if(info != null && GraphTypes.isPoint(info.getRTChartType()) &&
         info.getTextField() != null &&
         info.getTextField().getDataRef() instanceof ChartDimensionRef &&
         (info.getSizeField() == null && (!info.isSizeChanged() ||
                                          info.getShapeFrame() instanceof StaticShapeFrame &&
                                          info.getShapeFrame().getShape(null) == GShape.NIL) ||
          info.getSizeField() != null) &&
         info.getShapeField() == null && (!info.isShapeChanged() ||
                                          info.getShapeFrame().getShape(null) == GShape.NIL || ignoreShape))
      {
         ChartRef[] xrefs = info.getXFields();
         ChartRef[] yrefs = info.getYFields();

         return !containsMeasure(xrefs) && !containsMeasure(yrefs);
      }

      return false;
   }

   public static boolean isDotPlot(ChartInfo info) {
      // don't support facet for now. combination seems to be strange. will just stick
      // with regular point or heat map.
      if(info == null || info.getXFieldCount() + info.getYFieldCount() != 1) {
         return false;
      }

      // wordcloud and heatmap has higher priority.
      if(!isHeatMapish(info) || isWordCloud(info) || isHeatMap(info)) {
         return false;
      }

      // only draw dot plot if not agggated.
      List<ChartAggregateRef> aggrs = Arrays.stream(info.getAestheticRefs(false))
         .filter(a -> a != null)
         .map(a -> a.getDataRef())
         .filter(a -> a instanceof ChartAggregateRef)
         .map(a -> (ChartAggregateRef) a)
         .collect(Collectors.toList());
      return aggrs.isEmpty() || aggrs.stream().anyMatch(a -> !a.isAggregated());
   }

   /**
    * Check if contains measure.
    */
   static boolean containsMeasure(ChartRef[] refs) {
      if(refs == null || refs.length == 0) {
         return false;
      }

      return refs[refs.length - 1].isMeasure();
   }

   public static boolean checkType(ChartInfo cinfo, Predicate<Integer> func) {
      return checkType(cinfo, false, func);
   }

   public static boolean checkType(ChartInfo cinfo, boolean rt, Predicate<Integer> func) {
      if(null == cinfo) {
         return false;
      }

      int ctype;

      boolean appliedDc = cinfo instanceof VSChartInfo &&
         ((VSChartInfo) cinfo).isAppliedDateComparison();

      if(!cinfo.isMultiStyles()) {
         return func.test((cinfo.getChartType() == GraphTypes.CHART_AUTO) ?
                          cinfo.getRTChartType() : cinfo.getChartType());
      }
      else {
         ChartRef[] fields = rt ? cinfo.getRTXFields() : cinfo.getXFields();

         for(int i = 0; i < fields.length; i++) {
            if(fields[i] instanceof ChartAggregateRef) {
               ctype = (rt && appliedDc ? ((ChartAggregateRef) fields[i]).getChartType() :
                        cinfo.getChartType()) == GraphTypes.CHART_AUTO ?
                  ((ChartAggregateRef) fields[i]).getRTChartType() :
                  ((ChartAggregateRef) fields[i]).getChartType();

               if(func.test(ctype)) {
                  return true;
               }
            }
         }

         fields = rt ? cinfo.getRTYFields() : cinfo.getYFields();

         for(int i = 0; i < fields.length; i++) {
            if(fields[i] instanceof ChartAggregateRef) {
               ctype = (rt && appliedDc ? ((ChartAggregateRef) fields[i]).getChartType() :
                        cinfo.getChartType()) == GraphTypes.CHART_AUTO ?
                  ((ChartAggregateRef) fields[i]).getRTChartType() :
                  ((ChartAggregateRef) fields[i]).getChartType();

               if(func.test(ctype)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Check whether the specified chart supports stack measures.
    */
   public static boolean isStackMeasuresSupported(ChartInfo cinfo) {
      if(cinfo == null) {
         return false;
      }

      List<ChartRef[]> axisFields = new ArrayList();
      axisFields.add(cinfo.getXFields());
      axisFields.add(cinfo.getYFields());
      int mcnt = 0;
      Set secondaries = new HashSet();
      Set chartStyles = new HashSet();

      for(int i = 0; i < axisFields.size(); i++) {
         DataRef[] fields = axisFields.get(i);

         for(int j = 0; j < fields.length; j++) {
            DataRef field = fields[j];

            if(field instanceof ChartAggregateRef) {
               secondaries.add(((ChartAggregateRef) field).isSecondaryY());
               chartStyles.add(((ChartAggregateRef) field).getRTChartType());
               mcnt++;
            }
         }
      }

      // this should match the ChartTypeButton.isStackMeasuresEnabled() logic. (59394)
      if(cinfo.isMultiStyles() && chartStyles.size() > 1) {
         return false;
      }

      return mcnt > 1 && secondaries.size() < 2;
   }

   /**
    * Get the chart type of an aggregate.
    */
   protected static int getChartType(String fld, ChartInfo info) {
      int type = getChartType(info, fld, true);

      if(type == -1) {
         boolean appliedDC = false;

         if(info instanceof VSChartInfo) {
            appliedDC = ((VSChartInfo) info).isAppliedDateComparison();
         }

         int chartType = getChartType(info, fld, false);
         boolean measureOnXY = GraphUtil.hasMeasureOnX(info) && GraphUtil.hasMeasureOnY(info);

         if(GraphTypes.isAuto(chartType) && measureOnXY) {
            chartType = GraphTypes.CHART_POINT;
         }

         Catalog catalog = Catalog.getCatalog();
         String msg = appliedDC ? catalog.getString("chartTypes.dateComparison.noPermission") :
            GraphTypes.isAuto(chartType) ? catalog.getString("chartTypes.auto.noPermission") :
            catalog.getString("chartTypes.user.noPermission",
                              GraphTypes.getDisplayName(chartType) + " " + catalog.getString("Chart"));
         MessageException exception =
            new MessageException(msg, LogLevel.INFO, false, MessageCommand.INFO);
         exception.setKeywords("INVALID_CHART_TYPE");
         throw exception;
      }

      return type;
   }

   /**
    * Get the chart type of an aggregate.
    */
   private static int getChartType(ChartInfo info, String fld, boolean runtime) {
      if(!info.isMultiStyles()) {
         return runtime ? info.getRTChartType() : info.getChartType();
      }

      if(!containsAggregate(fld, info)) {
         return GraphTypes.CHART_POINT;
      }

      ChartRef[] xrefs = info.getRTXFields();
      ChartRef[] yrefs = info.getRTYFields();

      for(ChartRef[] refs : new ChartRef[][] {yrefs, xrefs}) {
         for(ChartRef ref : refs) {
            if(!GraphUtil.isMeasure(ref)) {
               continue;
            }

            ChartAggregateRef mref = (ChartAggregateRef) ref;

            if(GraphUtil.equalsName(mref, fld)) {
               return runtime ? mref.getRTChartType() : mref.getChartType();
            }
         }
      }

      // default to point if only discrete measure (same as no measure)
      return GraphTypes.CHART_POINT;
   }

   /**
    * Check if contains the specified aggregate.
    */
   static boolean containsAggregate(String fld, ChartInfo info) {
      ChartRef ref = info.getFieldByName(fld, true);
      return ref instanceof ChartAggregateRef;
   }

   /**
    * Check if stack measures enabled AND applied.
    */
   public static boolean isStackMeasures(ChartInfo info, ChartDescriptor desc) {
      return isStackMeasures0(info, desc) && !isMultiType(info) &&
         (!info.isMultiStyles() && GraphTypes.isStack(info.getRTChartType()) ||
          info.isMultiStyles() && checkType(info, c -> GraphTypes.isStack(c)));
   }

   private static boolean isStackMeasures0(ChartInfo chartInfo, ChartDescriptor desc) {
      if(chartInfo == null) {
         return false;
      }

      ChartDescriptor chartDesc = desc != null ? desc : chartInfo.getChartDescriptor();
      PlotDescriptor plotDesc = chartDesc == null ? null : chartDesc.getPlotDescriptor();

      if(plotDesc == null) {
         return false;
      }

      if(!isStackMeasuresSupported(chartInfo)) {
         return false;
      }

      if(desc != null && desc.getPlotDescriptor() != null &&
         desc.getPlotDescriptor().isStackMeasures())
      {
         return true;
      }

      return plotDesc.isStackMeasures();
   }

   private static boolean isMultiType(ChartInfo info) {
      if(!info.isMultiStyles()) {
         return false;
      }

      List<String> ymeasures = Arrays.stream(info.getYFields())
         .filter(a -> a.isMeasure()).map(a -> a.getFullName()).collect(Collectors.toList());

      if(ymeasures.size() > 1) {
         return isMultiType(info, ymeasures);
      }

      List<String> xmeasures = Arrays.stream(info.getXFields())
         .filter(a -> a.isMeasure()).map(a -> a.getFullName()).collect(Collectors.toList());
      return isMultiType(info, xmeasures);
   }

   private static boolean isMultiType(ChartInfo info, List<String> measures) {
      return measures.stream()
         .mapToInt(m -> getChartType(m, info))
         .distinct()
         .count() > 1;
   }

   public static int getChartType(ChartInfo cinfo) {
      if(cinfo == null) {
         return -1;
      }

      // get the first measure's chart type
      if(cinfo.isMultiStyles()) {
         ChartRef[] yrefs = cinfo.getRTYFields();

         for(int i = 0; i < yrefs.length; i++) {
            if(yrefs[i] instanceof ChartAggregateRef) {
               ChartAggregateRef aref = (ChartAggregateRef) yrefs[i];

               if(aref.getChartType() == GraphTypes.CHART_AUTO) {
                  return aref.getRTChartType();
               }

               return aref.getChartType();
            }
         }

         ChartRef[] xrefs = cinfo.getRTXFields();

         for(int i = 0; i < xrefs.length; i++) {
            if(xrefs[i] instanceof ChartAggregateRef) {
               ChartAggregateRef aref = (ChartAggregateRef) xrefs[i];

               if(aref.getChartType() == GraphTypes.CHART_AUTO) {
                  return aref.getRTChartType();
               }

               return aref.getChartType();
            }
         }

         // handle no measure case
         ChartRef xref = xrefs.length == 0 ? null : xrefs[xrefs.length - 1];
         ChartRef yref = yrefs.length == 0 ? null : yrefs[yrefs.length - 1];
         boolean xdim = xref instanceof XDimensionRef;
         boolean ydim = yref instanceof XDimensionRef;

         if(xref == null) {
            return ydim ? GraphTypes.CHART_POINT : GraphTypes.CHART_BAR;
         }
         else if(yref == null) {
            return xdim ? GraphTypes.CHART_POINT : GraphTypes.CHART_BAR;
         }
         else if(xdim && ydim) {
            return GraphTypes.CHART_POINT;
         }
      }

      return (cinfo.getChartType() == GraphTypes.CHART_AUTO) ?
         cinfo.getRTChartType() : cinfo.getChartType();
   }

   /**
    * For auto chart type, get an available chart type.
    */
   public static int getAvailableAutoChartType(int type, boolean auto, boolean stack,
                                               boolean measureOnXY)
   {
      if(!auto || measureOnXY && type == GraphTypes.CHART_POINT) {
         return checkChartStylePermission(type) ? type : -1;
      }

      if(checkChartStylePermission(type)) {
         return type;
      }

      int[] chartTypes = stack ? stackAutoChartTypes : autoChartTypes;

      for(int ctype : chartTypes) {
         if(ctype == type) {
            continue;
         }

         int nonStackType = ctype;

         if(stack) {
            String[] style = getChartNonStackStyle(ctype);

            if(style != null) {
               nonStackType = Integer.parseInt(style[0]);
            }
         }

         if(checkChartStylePermission(nonStackType)) {
            return ctype;
         }
      }

      return -1;
   }

   public static boolean checkChartStylePermission(int type) {
      if(type == GraphTypes.CHART_AUTO) {
         return true;
      }

      Principal principal = ThreadContext.getContextPrincipal();

      String[] style = getChartNonStackStyle(type);

      if(style == null) {
         return false;
      }

      int typeNum = Integer.parseInt(style[0]);
      String typeStr = style[1];
      String resource = getChartStylePath(typeNum, typeStr);

      return checkChartStylePermission(
         resource, ResourceType.CHART_TYPE, ResourceAction.READ, principal);
   }

   /**
    * Check whether the user has permission to the specified chart type.
    */
   public static boolean checkChartStylePermission(String resource, ResourceType type,
                                                   ResourceAction action, Principal principal)
   {
      try {
         return SecurityEngine.getSecurity().checkPermission(principal, type, resource, action);
      }
      catch(Exception ex) {
         LOG.debug("Failed to get chart styles", ex);
         return false;
      }
   }

   /**
    * Get the chart type path for permission control.
    */
   public static String getChartStylePath(int typeNum, String typeStr) {
      Catalog catalog = Catalog.getCatalog();

      switch(typeNum){
         case GraphTypes.CHART_BAR:
         case GraphTypes.CHART_3D_BAR:
         case GraphTypes.CHART_INTERVAL:
            return "Bar/" + typeStr;
         case GraphTypes.CHART_LINE:
         case GraphTypes.CHART_STEP:
         case GraphTypes.CHART_JUMP:
            return "Line/" + typeStr;
         case GraphTypes.CHART_AREA:
         case GraphTypes.CHART_STEP_AREA:
            return "Area/" + typeStr;
         case GraphTypes.CHART_PIE:
         case GraphTypes.CHART_3D_PIE:
         case GraphTypes.CHART_DONUT:
            return "Pie/" + typeStr;
         case GraphTypes.CHART_RADAR:
         case GraphTypes.CHART_FILL_RADAR:
            return "Radar/" + typeStr;
         case GraphTypes.CHART_STOCK:
         case GraphTypes.CHART_CANDLE:
         case GraphTypes.CHART_BOXPLOT:
            return "Schema/" + typeStr;
         case GraphTypes.CHART_MAP:
         case GraphTypes.CHART_MAP_CONTOUR:
            return "Map/" + typeStr;
         case GraphTypes.CHART_TREEMAP:
         case GraphTypes.CHART_SUNBURST:
         case GraphTypes.CHART_CIRCLE_PACKING:
         case GraphTypes.CHART_ICICLE:
            return "Treemap/" + typeStr;
         case GraphTypes.CHART_TREE:
         case GraphTypes.CHART_NETWORK:
         case GraphTypes.CHART_CIRCULAR:
            return "Relation/" + typeStr;
         case GraphTypes.CHART_FUNNEL:
         case GraphTypes.CHART_GANTT:
         case GraphTypes.CHART_MEKKO:
         case GraphTypes.CHART_PARETO:
         case GraphTypes.CHART_SCATTER_CONTOUR:
         case GraphTypes.CHART_WATERFALL:
            return "Others/" + typeStr;
         case GraphTypes.CHART_POINT:
            return typeStr;
         default:
            return null;
      }
   }

   public static String[] getChartNonStackStyle(int type) {
      Map<String, String> chartTypes = GraphTypes.getAllChartTypes();
      String typeNum = null;
      String typeStr = null;

      switch(type) {
         case GraphTypes.CHART_BAR_STACK:
            typeNum = String.valueOf(GraphTypes.CHART_BAR);
            typeStr = chartTypes.get(String.valueOf(GraphTypes.CHART_BAR));
            break;
         case GraphTypes.CHART_3D_BAR_STACK:
            typeNum = String.valueOf(GraphTypes.CHART_3D_BAR);
            typeStr = chartTypes.get(String.valueOf(GraphTypes.CHART_3D_BAR));
            break;
         case GraphTypes.CHART_LINE_STACK:
            typeNum = String.valueOf(GraphTypes.CHART_LINE);
            typeStr = chartTypes.get(String.valueOf(GraphTypes.CHART_LINE));
            break;
         case GraphTypes.CHART_AREA_STACK:
            typeNum = String.valueOf(GraphTypes.CHART_AREA);
            typeStr = chartTypes.get(String.valueOf(GraphTypes.CHART_AREA));
            break;
         case GraphTypes.CHART_POINT_STACK:
            typeNum = String.valueOf(GraphTypes.CHART_POINT);
            typeStr = chartTypes.get(String.valueOf(GraphTypes.CHART_POINT));
            break;
         case GraphTypes.CHART_STEP_STACK:
            typeNum = String.valueOf(GraphTypes.CHART_STEP);
            typeStr = chartTypes.get(String.valueOf(GraphTypes.CHART_STEP));
            break;
         case GraphTypes.CHART_STEP_AREA_STACK:
            typeNum = String.valueOf(GraphTypes.CHART_STEP_AREA);
            typeStr = chartTypes.get(String.valueOf(GraphTypes.CHART_STEP_AREA));
            break;
         default:
            typeNum = String.valueOf(type);
            typeStr = chartTypes.get(String.valueOf(type));
      }

      if(typeNum != null && typeStr != null) {
         return new String[] {typeNum, typeStr};
      }

      return null;
   }

   /**
    * Used for auto chart type.
    */
   private static final int[] autoChartTypes = {
      GraphTypes.CHART_BAR, GraphTypes.CHART_LINE, GraphTypes.CHART_POINT,
      GraphTypes.CHART_3D_BAR, GraphTypes.CHART_AREA, GraphTypes.CHART_STEP,
      GraphTypes.CHART_STEP_AREA, GraphTypes.CHART_JUMP, GraphTypes.CHART_INTERVAL
   };

   private static final int[] stackAutoChartTypes = {
      GraphTypes.CHART_BAR_STACK, GraphTypes.CHART_LINE_STACK, GraphTypes.CHART_POINT_STACK,
      GraphTypes.CHART_3D_BAR_STACK, GraphTypes.CHART_AREA_STACK, GraphTypes.CHART_STEP_STACK,
      GraphTypes.CHART_STEP_AREA_STACK, GraphTypes.CHART_JUMP, GraphTypes.CHART_INTERVAL
   };

   private static final Logger LOG = LoggerFactory.getLogger(GraphUtil.class);

   public static boolean isScatterMatrix(ChartInfo info) {
      if(info != null && (info.getXFields().length < 2 ||
         info.getXFields().length != info.getYFields().length))
      {
         return false;
      }

      return Arrays.equals(Arrays.stream(info.getXFields()).map(f -> f.getFullName()).toArray(),
                           Arrays.stream(info.getYFields()).map(f -> f.getFullName()).toArray()) &&
         !GraphUtil.hasDimensionOnX(info) && !GraphUtil.hasDimensionOnY(info);
   }

    /**
     * Check if the element support the shape frame.
     *
     * @param egraph the chart element graph.
     */
    public static boolean supportShapeFrame(EGraph egraph) {
       if(egraph == null) {
          return true;
       }

       for(int i = 0; i < egraph.getElementCount(); i++) {
          GraphElement elem = egraph.getElement(i);

          if(elem.getShapeFrame() == null) {
             break;
          }

          if(!(elem instanceof PointElement || elem instanceof PolygonElement ||
             (elem instanceof IntervalElement &&
                !is3DCoord(egraph.getCoordinate()))))
          {
             return false;
          }
       }

       return true;
    }

    /**
     * Check if this is a 3d coordinate.
     *
     * @param coord the graph coordinate.
     */
    public static boolean is3DCoord(Coordinate coord) {
       if(coord instanceof PolarCoord) {
          coord = ((PolarCoord) coord).getCoordinate();
       }

       if(coord instanceof FacetCoord) {
          Coordinate[] inners = ((FacetCoord) coord).getInnerCoordinates();

          for(int i = 0; i < inners.length; i++) {
             if(is3DCoord(inners[i])) {
                return true;
             }
          }
       }

       return coord instanceof Rect25Coord;
    }

    /**
     * Check if the element support the specified frame.
     */
    public static boolean supportsFrame(int chartType, VisualFrame frame, PlotDescriptor plotDescriptor) {
       if(frame == null) {
          return false;
       }

       final boolean isPointLine = plotDescriptor != null && plotDescriptor.isPointLine();
       final boolean isPointRadar = chartType == GraphTypes.CHART_RADAR && isPointLine;

       if(GraphTypes.isStock(chartType) && (frame instanceof LineFrame ||
          frame instanceof ShapeFrame || frame instanceof TextureFrame ||
          frame instanceof SizeFrame))
       {
          return false;
       }

       if(frame instanceof LineFrame && !GraphTypes.isLine(chartType) &&
          !GraphTypes.isArea(chartType) && !GraphTypes.isRelation(chartType) &&
          (!GraphTypes.isRadar(chartType) || isPointRadar))
       {
          return false;
       }

       if(frame instanceof ShapeFrame && !GraphTypes.isPoint(chartType) && !isPointRadar) {
          return false;
       }

       if(frame instanceof TextureFrame && (GraphTypes.isLine(chartType) ||
          GraphTypes.isArea(chartType) || GraphTypes.isRadar(chartType) ||
          GraphTypes.isPoint(chartType) || GraphTypes.isRelation(chartType)))
       {
          return false;
       }

       return true;
    }

    /**
     * Check if the element support the specified frame.
     */
    public static boolean supportsFrame(ChartInfo info, int chartType, VisualFrame frame,
                                        GraphElement elem, PlotDescriptor plotDescriptor)
    {
       if(frame == null || GraphTypes.isPareto(chartType) && elem instanceof LineElement) {
          return false;
       }

       if(GraphTypes.isContour(chartType) && frame instanceof ShapeFrame) {
          return false;
       }

       if(info instanceof MapInfo && GraphTypes.isPolygon(chartType)) {
          // size is showing as point on map
          if(elem instanceof PolygonElement && frame instanceof SizeFrame) {
             return false;
          }

          if(frame instanceof ColorFrame) {
             List<VisualFrame> colors = GraphUtil.getColorFrames(frame);

             for(int i = 0; i < colors.size(); i++) {
                Object color = colors.get(i);

                if(color instanceof HLColorFrame || color instanceof GeoColorFrame) {
                   return true;
                }
             }

             return GraphUtil.containsMapPoint(info);
          }
          else if(frame instanceof TextFrame) {
             return elem instanceof PointElement ? true :
                GraphUtil.containsOnlyPolygon(info);
          }
       }

       return supportsFrame(chartType, frame, plotDescriptor);
    }

    /**
     * Check if the specified info supports inverted chart.
     */
    public static boolean supportsInvertedChart(ChartInfo info) {
       if(info.isMultiStyles()) {
          ChartRef[] yfs = info.getYFields();

          for(int i = 0; i < yfs.length; i++) {
             if(yfs[i] instanceof ChartAggregateRef) {
                int ctype = ((ChartAggregateRef) yfs[i]).getChartType();

                if(!GraphTypes.supportsInvertedChart(ctype)) {
                   return false;
                }
             }
          }
       }
       else {
          return GraphTypes.supportsInvertedChart(info.getChartType());
       }

       return true;
    }

    /**
     * Check if the info support shape aesthetic.
     */
    public static boolean supportsSize(ChartInfo info, int type) {
       return type == GraphTypes.CHART_MAP ? GraphUtil.containsMapPoint(info) :
          GraphTypes.supportsSize(type);
    }

    /**
     * Whether support select part point of line or area.
     */
    public static boolean supportPoint(Visualizable v) {
       return v instanceof LineVO && !((LineVO) v).isRadar();
    }

   /**
    * Check if this chart is like a heat map. this is used mostly in gui to control
    * what property/icon to show.
    */
    public static boolean isHeatMapish(ChartInfo info) {
       if(info == null) {
          return false;
       }

       int chartType = info.getChartType() == GraphTypes.CHART_AUTO ?
          info.getRTChartType() : info.getChartType();

       return chartType == GraphTypes.CHART_POINT && !GraphUtil.hasMeasure(info);
    }

   /**
    * Check if a chart is heatmap, binding measure to color, dimension to x y.
    * This is used at runtime to control how chart is constructed.
    */
    public static boolean isHeatMap(ChartInfo info) {
       if(!isHeatMapish(info)) {
          return false;
       }

       AestheticRef colorField = info.getColorField();
       // don't use getRTColorField() since it may be null if the colorField is not found
       // in the columns and resulting in change in output from earlier version. (56088)
       DataRef colorRef = colorField != null ? colorField.getDataRef() : null;
       ChartRef[] xfields = info.getXFields();
       ChartRef[] yfields = info.getYFields();
       boolean noSize = info.getRTSizeField() == null && !info.isSizeChanged() &&
          info.getRTShapeField() == null;
       boolean xDim = Arrays.stream(xfields).anyMatch(a -> GraphUtil.isDimension(a));
       boolean yDim = Arrays.stream(yfields).anyMatch(a -> GraphUtil.isDimension(a));
       boolean wordCloud = isWordCloud(info);
       // default or empty shape
       boolean noShape = !info.isShapeChanged() ||
          info.getShapeFrame() instanceof StaticShapeFrame &&
             ((StaticShapeFrame) info.getShapeFrame()).getShape() == GShape.NIL;
       // color is defined
       boolean hasColor = colorRef instanceof ChartDimensionRef ||
          colorRef instanceof ChartAggregateRef &&
             // if not aggregated, should display as a scatter plot
             ((ChartAggregateRef) colorRef).isAggregateEnabled();

       return noSize && (xDim || yDim) && !wordCloud && noShape && hasColor;
    }

    public static boolean isBreakByRadar(ChartInfo cinfo) {
       return cinfo instanceof RadarChartInfo && cinfo.getYFields().length == 1 &&
          cinfo.getGroupFields().length >= 1;
    }

    /**
     * Check whether change time series property or not.
     *
     * @param info the report chart info.
     */
    public static boolean supportsTimeSeries(ChartInfo info) {
       return !isWaterfall(info) && !isPolar(info, false) &&
          !GraphTypes.isScatteredContour(info.getChartType());
    }
}
