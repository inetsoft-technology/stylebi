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
package inetsoft.web.vswizard.recommender;

import inetsoft.analytic.AnalyticAssistant;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.sree.AnalyticRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSBindingHelper;
import inetsoft.web.vswizard.model.recommender.ChartSubType;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.execution.IntervalExecutor;
import inetsoft.web.vswizard.recommender.execution.WizardDataExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.*;

/**
 * Utility methods for wizard chart recommender.
 *
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public final class ChartRecommenderUtil {
   public static ChartRef createChartRef(AssetEntry entry, RuntimeViewsheet rvs,
                                         VSTemporaryInfo tempInfo, Map<String, VSFormat> formatMap)
   {
      String columnValue = WizardRecommenderUtil.getFieldName(entry);
      ChartVSAssembly tempChart = tempInfo.getTempChart();
      SourceInfo sinfo = tempChart.getSourceInfo();
      String tname = entry.getProperty("assembly");
      String dtype = entry.getProperty("dtype");
      String rtype = entry.getProperty("refType");
      String caption = entry.getProperty("caption");
      int refType = rtype == null ? AbstractDataRef.NONE : Integer.parseInt(rtype);

      if(WizardRecommenderUtil.isDimension(entry)) {
         // fix geo column
         VSChartDimensionRef dim = new VSChartDimensionRef();
         dim.setGroupColumnValue(columnValue);
         dim.setDataType(dtype);
         dim.setRefType(refType);
         dim.setDataRef(WizardRecommenderUtil.createColumnRef(entry));

         if(caption != null) {
            dim.setCaption(caption);
         }

         // if it's from predefined info, set the date level
         if(XSchema.isDateType(dim.getDataType())) {
            WizardRecommenderUtil.setDefaultDateLevel(dim, entry);
            dim.setTimeSeries(true);
         }

         return dim;
      }
      else {
         VSChartAggregateRef agg = new VSChartAggregateRef();
         agg.setRefType(refType);
         agg.setColumnValue(columnValue);
         agg.setDataRef(WizardRecommenderUtil.createColumnRef(entry));

         if(caption != null) {
            agg.setCaption(caption);
         }

         if((refType & AbstractDataRef.AGG_CALC) == AbstractDataRef.AGG_CALC ||
            (refType & AbstractDataRef.AGG_EXPR) == AbstractDataRef.AGG_EXPR)
         {
            agg.setFormulaValue("None");
         }
         else {
            try {
               AnalyticRepository analyticRepository = AnalyticAssistant.getAnalyticAssistant().getAnalyticRepository();
               String formula = VSBindingHelper.getModelDefaultFormula(entry, sinfo, rvs, analyticRepository);

               if(formula != null) {
                  agg.setFormulaValue(formula);
               }
               else {
                  setDefaultAggregate(agg, dtype, rvs, tempInfo, formatMap);
               }
            }
            catch (RemoteException e) {
               setDefaultAggregate(agg, dtype, rvs, tempInfo, formatMap);
            }
         }

         return agg;
      }
   }

   private static void setDefaultAggregate(VSChartAggregateRef ref, String dtype,
                                           RuntimeViewsheet rvs, VSTemporaryInfo tempInfo,
                                           Map<String, VSFormat> formatMap)
   {
      ref.setFormulaValue(AggregateFormula.getDefaultFormula(dtype).getFormulaName());
   }

   /**
    * Calculate a score for chart or crosstab. Table is used as the fallback, with a
    * base score set in VSDefaultRecommandationFactory. The score implements a gradual
    * fallback to table. If chart/crosstab has a score less than table, it will still
    * be recommended as long as it's above minimum score of 0. This allows a chart/crosstab
    * to not be primary but still selectable.
    * @param entries selected columns.
    * @param groups dimension refs.
    * @param chart true to score chart, false to score crosstab.
    * @return a score for the type of assembly (chart/crosstab). < 0 if the type should
    * not be recommended.
    */
   public static int scoreGroupAssembly(AssetEntry[] entries, List<ChartRef> groups, boolean chart)
   {
      if(isTableOnly(entries, chart)) {
         return -1;
      }

      List<AssetEntry> dimensions = WizardRecommenderUtil.getDimensionFields(entries);
      List<AssetEntry> measures = WizardRecommenderUtil.getMeasureFields(entries);
      List<AssetEntry> dates = WizardRecommenderUtil.getDateFields(entries);
      List<List<AssetEntry>> hierarchies =
         WizardRecommenderUtil.getSortedHierarchyLists(entries, false);
      int d = dimensions.size();
      int m = measures.size();
      boolean hasGeo = hasGeoDimension(entries);
      int hierDimCnt = hierarchies.stream().mapToInt(a -> a.size()).sum();

      // at most 3 hierarchy/dimension on crosstab
      if(!chart && dimensions.size() - (hierDimCnt - hierarchies.size()) > 3) {
         return -1;
      }

      if(dates.size() >= 2) {
         return -1;
      }

      if(entries.length == 1 && WizardDataExecutor.isCountableSqlType(entries[0]) &&
         (isDimGeo(entries[0]) || groups.size() == 1 &&
          WizardRecommenderUtil.isStringType(entries[0])))
      {
         // map with single geo dimension
      }
      else if(hasGeo && m <= 2) {
         // map can support dimensions without measure
      }
      // For no measure, we can recommend to word cloud chart. Maybe using table is better.
      else {
         // allow word cloud and dot plot
         if(m == 0 && d > 2) {
            return -1;
         }

         if(m == 0 && d == 1 && !WizardRecommenderUtil.isStringType(dimensions.get(0))) {
            return -1;
         }
      }

      int score = chart ? 12 : 10;

      int pref_cardinality = chart ? 300 : 500;
      int data_cardinality = Arrays.stream(entries)
         .mapToInt(entry -> entry.getProperty("cardinality") == null ? 0 :
               Integer.parseInt(entry.getProperty("cardinality")))
         .max().orElse(0);

      score -= data_cardinality / pref_cardinality;

      int pref_d = chart ? 3 : 2;
      int pref_m = chart ? 2 : 1;

      score -= Math.max(0, d - pref_d);
      score -= Math.max(0, m - pref_m);

      return score;
   }

   private static boolean isDimGeo(AssetEntry entry) {
      return entry != null &&
         "true".equals(entry.getProperty("isGeo")) && WizardRecommenderUtil.isDimension(entry);
   }

   public static boolean hasGeoDimension(AssetEntry[] entries) {
      return Arrays.stream(entries).anyMatch(entry ->
         "true".equals(entry.getProperty("isGeo")));
   }

   /**
    * Check if chart or crosstab not valid.
    * @param entries the selected fields.
    */
   private static boolean isTableOnly(AssetEntry[] entries, boolean chart) {
      long dimCnt = Arrays.stream(entries).filter(WizardRecommenderUtil::isDimension).count();

      // too many columns doesn't show well on chart/crosstab and can cause oom due to the
      // exponential growth of combinations
      if(chart) {
         if(dimCnt > 7 || entries.length > 10) {
            return true;
         }
      }
      // crosstab, the crosstab client is kind of slow so don't allow large crosstab
      else if(dimCnt > 6 || entries.length > 8) {
         return true;
      }

      return Arrays.stream(entries).anyMatch(ChartRecommenderUtil::isTableOnly);
   }

   private static boolean isTableOnly(AssetEntry entry) {
      try {
         if(!WizardRecommenderUtil.isDimension(entry) ||
            WizardRecommenderUtil.isDateType(entry))
         {
            return false;
         }

         if(!WizardDataExecutor.isSupportedSqlType(entry)) {
            return true;
         }
      }
      catch(NumberFormatException ex) {
         LOG.error("Failed to parse the cardinality of the field", ex);
         return true;
      }

      return false;
   }

   /**
    * The recommend order should as this:
    *   1 recommend the chart style according to the selected fields.
    *   2 recommend the binding accordind the style and fields.
    *   3 recommend the subType accordind the style and the binding info.
    * The sub type should calculate from chart info by this:
    *   1 type -> chart style
    *   2 facet -> multi dimension/measure on x/y
    *   3 word cloud -> text field is not null
    *   4 heat map  -> x/y has one dimension, color has measure(x/y have no measure).
    *   5 dual axis  -> the first measure in y is secondy.
    */
   public static ChartSubType createSubType(ChartInfo info) {
      VSChartInfo cinfo = (VSChartInfo) info;
      int type = cinfo.getChartType();
      ChartSubType sub = new ChartSubType(type + "");
      sub.setChartInfo(cinfo);

      if(type == GraphTypes.CHART_POINT) {
         sub.setWordCloud(cinfo.getTextField() != null);
         sub.setDotplot(GraphTypeUtil.isDotPlot(cinfo));
         // in GraphGenerator (runtime), a heatmap results in a PolygonElemnt. but
         // the HeatMapFilter creates a point (with size setting) so it's not considered
         // a heat map by runtime, but treated as a heatmap in wizard.
         sub.setHeatMap(GraphTypeUtil.isHeatMapish(cinfo) && !sub.isDotplot() && !sub.isWordCloud());
         sub.setScatterMatrix(GraphTypeUtil.isScatterMatrix(cinfo));
         sub.setScatter(isScatter(cinfo));
      }

      if(type == GraphTypes.CHART_BAR || type == GraphTypes.CHART_BAR_STACK) {
         sub.setRotated(isRotated(cinfo));
         sub.setHistogram(isHistogram(cinfo));
      }

      if(type == GraphTypes.CHART_RADAR) {
         int xdims = ChartRecommenderUtil.getDimensions(info.getXFields()).size();
         int ydims = ChartRecommenderUtil.getDimensions(info.getYFields()).size();
         sub.setFacet(xdims + ydims > 0);
      }
      else if(type == GraphTypes.CHART_LINE || type == GraphTypes.CHART_BAR ||
         type == GraphTypes.CHART_STEP || type == GraphTypes.CHART_JUMP ||
         type == GraphTypes.CHART_STEP_AREA_STACK ||
         type == GraphTypes.CHART_BAR_STACK || type == GraphTypes.CHART_AREA_STACK)
      {
         if(!info.isSeparatedGraph()) {
            sub.setDualAxis(isSecondy(cinfo));
         }
         else {
            sub.setFacet(cinfo.getXFieldCount() > 1 || cinfo.getYFieldCount() > 1);
         }
      }
      else if(type == GraphTypes.CHART_PIE) {
         if(cinfo.isMultiStyles()) {
            sub.setDonut(true);
         }
         else {
            sub.setFacet(cinfo.getXFieldCount() + cinfo.getYFieldCount() > 1);
         }
      }
      else if(type == GraphTypes.CHART_POINT) {
         if(!sub.isScatterMatrix()) {
            sub.setFacet(cinfo.getXFieldCount() + cinfo.getYFieldCount() > 2);
         }
      }
      else if(type == GraphTypes.CHART_BOXPLOT) {
         sub.setFacet(cinfo.getXFieldCount() + cinfo.getYFieldCount() > 2);
      }

      return sub;
   }

   public static boolean isHistogram(VSChartInfo info) {
      return info.getChartType() == GraphTypes.CHART_BAR && GraphUtil.hasMeasure(info) &&
         info.getXFieldCount() == 1 && info.getYFieldCount() == 1 &&
         info.getXField(0) instanceof VSDimensionRef &&
         ((VSDimensionRef) info.getXField(0)).getGroupColumnValue().startsWith("Range@");
   }

   public static boolean isScatter(VSChartInfo info) {
      return GraphUtil.hasMeasureOnX(info) && GraphUtil.hasMeasureOnY(info) && !GraphTypeUtil.isScatterMatrix(info);
   }

   private static boolean isRotated(VSChartInfo info) {
      return GraphUtil.hasMeasureOnX(info);
   }

   private static boolean isSecondy(VSChartInfo info) {
      List<VSChartAggregateRef> list = getMeasures(info.getYFields());

      if(list.size() == 2) {
         return list.get(1).isSecondaryY();
      }

      list = getMeasures(info.getXFields());

      if(list.size() == 2) {
         return list.get(1).isSecondaryY();
      }

      return false;
   }

   public static List<VSChartAggregateRef> getMeasures(ChartRef[] refs) {
      ArrayList<VSChartAggregateRef> list = new ArrayList<>();

      for(ChartRef ref : refs) {
         if(ref.isMeasure()) {
            list.add((VSChartAggregateRef) ref);
         }
      }

      return list;
   }

   public static boolean isGeoRef(AssetEntry[] entries, ChartRef ref) {
      String groupName = WizardRecommenderUtil.getRefName(ref);

      for(AssetEntry entry : entries) {
         if("true".equals(entry.getProperty("isGeo"))) {
            String entryName = WizardRecommenderUtil.getFieldName(entry);

            if(Tool.equals(groupName, entryName)) {
               return true;
            }
         }
      }

      return false;
   }

   public static boolean hasMeasureOnAesthetic(VSChartInfo info) {
      return info.getColorField() != null &&
             ((ChartRef)info.getColorField().getDataRef()).isMeasure() ||
             info.getShapeField() != null &&
             ((ChartRef)info.getShapeField().getDataRef()).isMeasure() ||
             info.getSizeField() != null &&
             ((ChartRef)info.getSizeField().getDataRef()).isMeasure() ||
             info.getTextField() != null &&
             ((ChartRef)info.getTextField().getDataRef()).isMeasure();
   }

   public static List<VSChartDimensionRef> getDimensions(ChartRef[] refs, boolean includeDate) {
      ArrayList<VSChartDimensionRef> list = new ArrayList<>();

      for(ChartRef ref : refs) {
         if(ref instanceof VSChartDimensionRef &&
            (includeDate || !XSchema.isDateType(ref.getDataType())))
         {
            list.add((VSChartDimensionRef) ref);
         }
      }

      return list;
   }

   public static List<VSChartDimensionRef> getDimensions(ChartRef[] refs) {
      return getDimensions(refs, true);
   }

   public static List<List<ChartRef>> getHierarchyGroups(List<ChartRef> groups,
                                                         List<List<AssetEntry>> hierarchies)
   {
      List<List<ChartRef>> list = new ArrayList<>();

      for(List<AssetEntry> hierarchy : hierarchies) {
         list.add(getHierarchyGroup(groups, hierarchy));
      }

      return list;
   }

   private static List<ChartRef> getHierarchyGroup(List<ChartRef> groups, List<AssetEntry> list) {
      List<ChartRef> refs = new ArrayList<>();

      for(AssetEntry entry : list) {
         ChartRef ref = findGroup(entry, groups);

         if(ref != null) {
            refs.add(ref);
         }
      }

      return refs;
   }

   private static ChartRef findGroup(AssetEntry entry, List<ChartRef> groups) {
      for(ChartRef group : groups) {
         if(!(group instanceof VSChartDimensionRef)) {
            continue;
         }

         String groupName = WizardRecommenderUtil.getRefName(group);
         String entryName = WizardRecommenderUtil.getFieldName(entry);

         if(Tool.equals(groupName, entryName)) {
            return group;
         }
      }

      return null;
   }

   public static boolean isAggCalc(ChartRef ref, AssetEntry[] entries) {
      if(!(ref instanceof VSChartAggregateRef)) {
         return false;
      }

      for(AssetEntry entry : entries) {
         String columnValue = WizardRecommenderUtil.getFieldName(entry);
         String refName = WizardRecommenderUtil.getRefName(ref);

         if(Tool.equals(columnValue, refName)) {
            if("true".equals(entry.getProperty("isCalc")) &&
               !"true".equalsIgnoreCase(entry.getProperty("basedOnDetail")))
            {
               return true;
            }
         }
      }

      return false;
   }

   public static int getCardinality(ChartRef ref, AssetEntry[] entries) {
      for(AssetEntry entry : entries) {
         String columnValue = WizardRecommenderUtil.getFieldName(entry);
         String refName = WizardRecommenderUtil.getRefName(ref);

         if(Tool.equals(columnValue, refName)) {
            if(WizardRecommenderUtil.isDimension(entry) &&
               XSchema.isDateType(entry.getProperty("dtype"))) {
               return getDateCardinality(ref, entry);
            }

            Optional<String> cardinality = Optional.ofNullable(entry.getProperty("cardinality"));
            return Integer.parseInt(cardinality.orElse("0"));
         }
      }

      return 0;
   }

   private static int getDateCardinality(ChartRef ref, AssetEntry entry) {
      if(!(ref instanceof VSDimensionRef) || entry == null) {
         return 0;
      }

      try {
         int level = ((VSDimensionRef) ref).getDateLevel();
         Date startDate = new Date(Long.parseLong(entry.getProperty("startDate")));
         Date endDate = new Date(Long.parseLong(entry.getProperty("endDate")));
         return (int) IntervalExecutor.getTimeRange(startDate, endDate, level);
      }
      catch(NumberFormatException ex) {
         return 0;
      }
   }

   public static int getDataSetSize(AssetEntry[] entries) {
      return Arrays.stream(entries).map(e -> e.getProperty("dataSetSize"))
         .filter(a -> a != null)
         .mapToInt(a -> Integer.parseInt(a))
         .findFirst()
         .orElse(-1);
   }

   // find a matching hierarchy, where any hierarchy matches the prefix of refs
   public static List<ChartRef> findHierarchy(ChartRef[] refs, List<List<ChartRef>> hierarchies) {
      return hierarchies.stream()
         .filter(hier -> isMatchHierarchy(refs, hier)).findFirst().orElse(null);
   }

   public static boolean isMatchHierarchy(ChartRef[] refs, List<ChartRef> hierarchy) {
      if(refs.length < hierarchy.size()) {
         return false;
      }

      for(int i = 0; i < hierarchy.size(); i++) {
         if(!Tool.equals(refs[i], hierarchy.get(i))) {
            return false;
         }
      }

      return true;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ChartRecommenderUtil.class);
}
