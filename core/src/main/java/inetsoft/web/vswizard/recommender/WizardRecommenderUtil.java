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
package inetsoft.web.vswizard.recommender;

import inetsoft.graph.internal.GTool;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.GroupedThread;
import inetsoft.util.Tool;
import inetsoft.web.vswizard.RecommendSequentialContext;
import inetsoft.web.vswizard.model.recommender.*;
import inetsoft.web.vswizard.recommender.execution.*;
import inetsoft.web.vswizard.recommender.execution.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static inetsoft.web.vswizard.model.VSWizardConstants.*;

/**
 * Utility methods for wizard recommender.
 *
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public final class WizardRecommenderUtil {
   /**
    * Return the date type fields.
    */
   public static List<AssetEntry> getDateFields(AssetEntry[] entries) {
      return entries == null ? new ArrayList<>() : Arrays.asList(entries).stream()
         .filter(entry -> XSchema.isDateType(entry.getProperty("dtype")))
         .collect(Collectors.toList());
   }

   /**
    * Return the geographic type fields.
    */
   public static List<AssetEntry> getGeoFields(AssetEntry[] entries) {
      return entries == null ? new ArrayList<>() : Arrays.asList(entries).stream()
         .filter(entry -> entry.getProperty("mappingStatus") != null)
         .collect(Collectors.toList());
   }

   public static boolean isNumericType(AssetEntry entry) {
      return entry != null && XSchema.isNumericType(entry.getProperty("dtype"));
   }

   public static boolean isDateType(AssetEntry entry) {
      return entry != null && XSchema.isDateType(entry.getProperty("dtype"));
   }

   public static boolean isTimeType(AssetEntry entry) {
      return entry != null && XSchema.TIME.equals(entry.getProperty("dtype"));
   }

   public static boolean isStringType(AssetEntry entry) {
      return entry != null && XSchema.STRING.equals(entry.getProperty("dtype"));
   }

   public static boolean isBooleanType(AssetEntry entry) {
      return entry != null && XSchema.BOOLEAN.equals(entry.getProperty("dtype"));
   }

   /**
    * Create ColumnRef by the selection entry.
    */
   public static ColumnRef createColumnRef(AssetEntry entry) {
      if(entry == null) {
         return null;
      }

      String entity = entry.getProperty("entity");
      String attr = entry.getProperty("attribute");
      String caption = entry.getProperty("caption");
      String dtype = entry.getProperty("dtype");
      AttributeRef ref = new AttributeRef(entity, attr);
      ref.setCaption(caption);

      ColumnRef col = new ColumnRef(ref);
      col.setDataType(dtype);

      return col;
   }

   /**
    * Check if the entry is dimension.
    */
   public static boolean isDimension(AssetEntry entry) {
      String refType = entry.getProperty("refType");
      int rtype = refType == null ? AbstractDataRef.NONE : Integer.parseInt(refType);
      String cubeTypeStr = entry.getProperty(AssetEntry.CUBE_COL_TYPE);
      int ctype = cubeTypeStr == null ? 0 : Integer.parseInt(cubeTypeStr);

      return (rtype & AbstractDataRef.DIMENSION) != 0 || (ctype & 1) == 0;
   }

   /**
    * Check if the entry is geo.
    */
   public static boolean isGeoField(AssetEntry entry) {
      return "true".equals(entry.getProperty("isGeo"));
   }

   /**
    * Return the dimension fields.
    */
   public static List<AssetEntry> getDimensionFields(AssetEntry[] entries) {
      return entries == null ? new ArrayList<>() : Arrays.asList(entries).stream()
         .filter(entry -> isDimension(entry))
         .collect(Collectors.toList());
   }

   /**
    * Return the measure fields.
    */
   public static List<AssetEntry> getMeasureFields(AssetEntry[] entries) {
      return entries == null ? new ArrayList<>() : Arrays.asList(entries).stream()
         .filter(entry -> !isDimension(entry))
         .collect(Collectors.toList());
   }

   public static String getTableName(AssetEntry entry) {
      return entry == null ? null : entry.getProperty("assembly");
   }

   /**
    * Get property name from the entity, if the entity is a cube member, should
    * use entity + attribute as the name.
    */
   public static String getFieldName(AssetEntry entry) {
      if(entry == null) {
         return "";
      }

      String cvalue = entry.getName();
      String attribute = entry.getProperty("attribute");

      // normal asset entry not set entity and attribute properties,
      // cube entry set, the name should use entity + attribute
      if(attribute != null) {
         String entity = entry.getProperty("entity");
         cvalue = (entity != null ?  entity + "." : "") + attribute;
      }

      return cvalue;
   }

   /**
    * Check if the target field is a normal dimension which need to
    * consider the cardinality and hierarchy when doing recommendation.
    * @param entry      the target field.
    * @param ignoreGeo  true if geo is treated as normal dimension, else not.
    */
   public static boolean isNormalDimension(AssetEntry entry, boolean ignoreGeo) {
      if(entry == null || !isDimension(entry) || isDateType(entry)) {
         return false;
      }

      return ignoreGeo || !isGeoField(entry);
   }

   /**
    * Sorted the hierarchy lists by the numbers of the hierarchy level.
    */
   public static List<List<AssetEntry>> getSortedHierarchyLists(AssetEntry[] entries) {
      return getSortedHierarchyLists(entries, true);
   }

   /**
    * Sorted the hierarchy lists by the numbers of the hierarchy level.
    * @param ignoreGeo  true if geo is treated as normal dimension, else not.
    */
   public static List<List<AssetEntry>> getSortedHierarchyLists(AssetEntry[] entries,
                                                                boolean ignoreGeo)
   {
      List<AssetEntry> list = Arrays.asList(entries).stream()
         .filter(entry -> isNormalDimension(entry, ignoreGeo))
         .collect(Collectors.toList());
      AssetEntry[] dims = list.toArray(new AssetEntry[list.size()]);
      List<List<AssetEntry>> lists = getHierarchyLists(dims);

      lists.sort((List list0, List list1) -> list1.size() - list0.size());

      return removeDuplicateCol(lists);
   }

   private static List<List<AssetEntry>> removeDuplicateCol(List<List<AssetEntry>> lists) {
      List<List<AssetEntry>> nlists = new ArrayList<>();

      if(lists.size() == 0) {
         return nlists;
      }

      List<AssetEntry> list = lists.get(0);
      nlists.add(list);

      lists.stream().forEach(li -> {
         boolean contains = li.stream().anyMatch(entry -> list.contains(entry));

         if(!contains) {
            nlists.add(li);
         }
      });

      return nlists;
   }

   /**
    * Return all hierarchy field lists.
    * for example, the following list maybe returned.
    *
    *  {
    *     {a, a1},
    *     {a, a2},
    *     {b, b1, b11}
    *  }
    *
    * @param  entries the selected dimension fields.
    */
   public static List<List<AssetEntry>> getHierarchyLists(AssetEntry[] entries) {
      AssetEntry[] entries0 = entries.clone();
      List<List<AssetEntry>> list = new ArrayList<>();

      for(int i = 0; i < entries0.length; i++) {
         if("true".equals(entries0[i].getProperty(DEPENDENCY_CHECKED))) {
            continue;
         }

         list.addAll(getHierarchyFields(entries0[i], entries0));
      }

      return list;
   }

   /**
    * Return the hierarchy field lists for the target parent field.
    * for example, find hierarchy lists for field a, the following list maybe returned.
    *
    *  {
    *     {a, a1, a11},
    *     {a, a1, a12},
    *     {a, a2}
    *  }
    *
    * @param  parent  the target parent field.
    * @param  entries the selected dimension fields.
    */
   private static List<List<AssetEntry>> getHierarchyFields(AssetEntry parent, AssetEntry[] entries)
   {
      List<List<AssetEntry>> list = new ArrayList<>();
      String value = parent.getProperty(CHILDREN_FLAG);

      if(value == null) {
         return list;
      }

      String[] children = value.split(CHILDREN_SPLITER);

      for(int i = 0; i < children.length; i++) {
         AssetEntry child = getField(children[i], entries);

         if(child == null) {
            continue;
         }

         if(!"true".equals(child.getProperty(DEPENDENCY_CHECKED))) {
            child.setProperty(DEPENDENCY_CHECKED, "true");
         }

         List<List<AssetEntry>> clist = getHierarchyFields(child, entries);

         if(clist.size() != 0) {
             clist.stream().forEach(l -> {
               l.add(0, parent);
               list.add(l);
            });
         }
         else {
            List<AssetEntry> list0 = new ArrayList<>();
            list0.add(parent);
            list0.add(child);
            list.add(list0);
         }
      }

      return list;
   }

   /**
    * Find and return the AssetEntry by the field name.
    */
   private static AssetEntry getField(String field, AssetEntry[] entries) {
      for(int i = 0; i < entries.length; i++) {
         if(field.equals(getFieldName(entries[i]))) {
            return entries[i];
         }
      }

      return null;
   }

   /**
    * Set the default date level from dimension ref.
    */
   public static void setDefaultDateLevel(VSDimensionRef dim, AssetEntry entry) {
      Optional<String> interval = Optional.ofNullable(entry.getProperty("interval"));
      String none = String.valueOf(DateRangeRef.NONE_INTERVAL);

      if(none.equals(interval.orElse(none))) {
         int level = (XSchema.TIME.equals(dim.getDataType()) ?
            DateRangeRef.HOUR_INTERVAL : DateRangeRef.YEAR_INTERVAL);
         dim.setDateLevelValue(String.valueOf(level));
      }
      else {
         dim.setDateLevelValue(interval.get());
      }
   }

   /**
    * Get the default interval for date type dimension.
    * @param entries
    * @return
    * @throws Exception
    */
   public static void refreshDateInterval(ViewsheetSandbox box, AssetEntry[] entries,
                                          VSTemporaryInfo temporaryInfo)
      throws Exception
   {
      if(entries == null) {
         return;
      }

      for(int i = 0; i < entries.length; i++) {
         String dtype = entries[i].getProperty("dtype");

         if(isDimension(entries[i]) && XSchema.isDateType(dtype)) {
            String tname = getTableName(entries[i]);
            IntervalData intervalData =
               IntervalExecutor.getData(box, temporaryInfo, entries[i], tname, dtype);

            if(intervalData == null) {
               continue;
            }

            entries[i].setProperty("interval", String.valueOf(intervalData.getDatalevel()));
         }
      }
   }

   public static void refreshStartEndDate(ViewsheetSandbox box, AssetEntry[] entries,
                                          VSTemporaryInfo temporaryInfo)
      throws Exception
   {
      if(entries == null) {
         return;
      }

      for(int i = 0; i < entries.length; i++) {
         String dtype = entries[i].getProperty("dtype");

         if(isDimension(entries[i]) && XSchema.isDateType(dtype)) {
            String field = getFieldName(entries[i]);
            String tname = getTableName(entries[i]);
            IntervalData intervalData =
               IntervalExecutor.getData(box, temporaryInfo, entries[i], tname, dtype);

            if(intervalData == null) {
               continue;
            }

            entries[i].setProperty("startDate", intervalData.getStartDate() + "");
            entries[i].setProperty("endDate", intervalData.getEndDate() + "");
         }
      }
   }

   public static void calcCardinalities(ViewsheetSandbox box, VSTemporaryInfo tempInfo,
                                        AssetEntry[] dimEntries)
   {
      if(dimEntries == null || dimEntries.length == 0) {
         return;
      }

      String tname = getTableName(dimEntries[0]);

      for(int i = 0; i < dimEntries.length; i++) {
         AssetEntry entry = dimEntries[i];

         new GroupedThread() {
            @Override
            protected void doRun() {
               try {
                  box.lockRead();
                  CardinalityExecutor.getData(box, tempInfo, entry, tname);
               }
               catch(Exception ignore) {
               }
               finally {
                  box.unlockRead();
               }
            }
         }.start();
      }
   }

   /**
    * Check hierarchy relationship for the entries,
    * and add children columns for the entries by setting property.
    * @param box     the viewsheetsandbox.
    * @param entries the asset entries which sorted ascending by cardinality percentage
    */
   public static void refreshCardinalityAndHierarchy(ViewsheetSandbox box,
                                                     VSTemporaryInfo temporaryInfo,
                                                     AssetEntry[] entries)
      throws Exception
   {
      long ts = System.currentTimeMillis();

      if(entries == null || entries.length == 0 || box.isCancelled(ts)) {
         return;
      }

      List<AssetEntry> list = Arrays.asList(entries).stream()
         .filter(entry -> isDimension(entry) && !isDateType(entry))
         .collect(Collectors.toList());

      if(list.size() == 0 || box.isCancelled(ts)) {
         return;
      }

      String tname = getTableName(entries[0]);
      list = updateCardinalityInfo(box, temporaryInfo, tname, list);

      if(list.size() == 1 || box.isCancelled(ts)) {
         return;
      }

      updateHierarchyInfo(temporaryInfo, box, tname, list);
   }

   /**
    * Update cardinality information to the entries by setting property,
    * and return sorted entries with ascending order of cardinalityPercentage.
    * @param box     the viewsheet sandbox.
    * @param tname   the table name.
    * @param dimEntries the selected dimension fields.
    */
   private static List<AssetEntry> updateCardinalityInfo(ViewsheetSandbox box,
                                                         VSTemporaryInfo temporaryInfo,
                                                         String tname, List<AssetEntry> dimEntries)
      throws Exception
   {
      List<AssetEntry> list = new ArrayList<>();

      for(int i = 0; i < dimEntries.size(); i++) {
         AssetEntry entry = dimEntries.get(i);
         CardinalityData data = CardinalityExecutor.getData(box, temporaryInfo, entry, tname);

         if(data != null) {
            entry.setProperty("cardinality", data.getCardinality() + "");
            entry.setProperty("cardinalityPercentage", data.getCardinalityPercentage() + "");
            entry.setProperty("dataSetSize", data.getDataSetSize() + "");
            list.add(entry);
         }
      }

      list.sort((AssetEntry entry0, AssetEntry entry1) -> {
         try {
            int v1 = Integer.parseInt(entry0.getProperty("cardinality"));
            int v2 = Integer.parseInt(entry1.getProperty("cardinality"));
            return v1 == v2 ? 0 : v1 - v2 > 0 ? 1 : -1;
         }
         catch(NumberFormatException ex) {
            LOG.error("Failed to compare the entries by the cardinality", ex);
            return -1;
         }
      });

      return list;
   }

   /**
    * Check hierarchy relationship for the entries,
    * and add children columns for the entries by setting property.
    * @param box     the viewsheetsandbox.
    * @param tname   the table name.
    * @param dimEntries the asset entries which sorted ascending by cardinality percentage
    */
   private static void updateHierarchyInfo(VSTemporaryInfo temporaryInfo, ViewsheetSandbox box, String tname,
                                           List<AssetEntry> dimEntries)
      throws Exception
   {
      String[] fields = new String[2];
      long start = System.currentTimeMillis();

      for(int i = 0; i < dimEntries.size() - 1; i++) {
         AssetEntry parent = dimEntries.get(i);
         fields[0] = getFieldName(parent);

         for(int j = i + 1; j < dimEntries.size(); j++) {
            AssetEntry child = dimEntries.get(j);
            fields[1] = getFieldName(child);

            if(box.isCancelled(start)) {
               return;
            }

            if(Integer.parseInt(parent.getProperty("cardinality")) ==
               Integer.parseInt(child.getProperty("cardinality")))
            {
               continue;
            }

            HierarchyData data = HierarchyExecutor.getData(temporaryInfo, box, parent, child, tname);

            if(data != null && data.isHierarchical()) {
               String children = parent.getProperty(CHILDREN_FLAG);
               children = children == null ? fields[1] : children + CHILDREN_SPLITER + fields[1];
               parent.setProperty("__children__", children);
            }
         }
      }
   }

   public static String getRefName(ChartRef ref) {
      if(ref instanceof VSChartAggregateRef) {
         VSChartAggregateRef agg = (VSChartAggregateRef) ref;

         return agg.getName();
      }

      if((ref instanceof VSChartDimensionRef)) {
         VSChartDimensionRef dim = (VSChartDimensionRef) ref;

         return dim.getGroupColumnValue();
      }

      return null;
   }

   /**
    * Return the date type fields.
    */
   public static List<ChartRef> getDateDimensions(List<ChartRef> dims) {
      return dims.stream()
         .filter(dim -> XSchema.isDateType((dim).getDataType()))
         .collect(Collectors.toList());
   }

   /**
    * Return the date type fields.
    */
   public static List<ChartRef> getNoDateDimensions(List<ChartRef> dims) {
      return dims.stream()
              .filter(dim -> !XSchema.isDateType((dim).getDataType()))
              .collect(Collectors.toList());
   }

   public static boolean containsCalc(List<ChartRef> refs, RuntimeViewsheet rvs) {
      CalculateRef[] calcs = getCalcFields(rvs);

      return refs.stream().anyMatch(ref -> isCalcAggregateField(ref, calcs));
   }

   public static boolean allCalc(List<ChartRef> refs, RuntimeViewsheet rvs) {
      CalculateRef[] calcs = getCalcFields(rvs);

      return refs.size() > 0 && refs.stream().allMatch(ref -> isCalcAggregateField(ref, calcs));
   }

   private static CalculateRef[] getCalcFields(RuntimeViewsheet rvs) {
      Viewsheet vs = rvs.getViewsheet();
      ChartVSAssembly assembly = (ChartVSAssembly)
         vs.getAssembly(TEMP_CHART_NAME);

      if(assembly == null) {
         return null;
      }

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo)assembly.getVSAssemblyInfo();
      SourceInfo src = info.getSourceInfo();

      if(src == null) {
         return null;
      }

      return vs.getCalcFields(src.getSource());
   }

   public static boolean isCalcField(RuntimeViewsheet rvs, DataRef ref) {
      return isCalcField(ref, getCalcFields(rvs));
   }

   public static boolean isCalcField(DataRef ref, CalculateRef[] calcs) {
      if(calcs == null) {
         return false;
      }

      return Arrays.asList(calcs).stream().anyMatch(calc ->
         Tool.equals(calc.getName(), ref.getName()));
   }

   public static boolean isCalcAggregateField(RuntimeViewsheet rvs, DataRef ref)
   {
      return isCalcAggregateField(ref, getCalcFields(rvs));
   }

   public static boolean isCalcAggregateField(DataRef ref, CalculateRef[] calcs)
   {
      if(calcs == null) {
         return false;
      }

      return Arrays.asList(calcs).stream().anyMatch(calc ->
         !calc.isBaseOnDetail() && Tool.equals(calc.getName(), ref.getName()));
   }

   public static VSObjectRecommendation getSelectedRecommendation(VSRecommendType type,
                                                                  RuntimeViewsheet rvs)
   {
      if(type == null) { // Recommended type always is not null, reduce once O(n) lookup.
         return null;
      }

      VSTemporaryInfo temporaryInfo = rvs.getVSTemporaryInfo();
      VSRecommendationModel recommendationModel = temporaryInfo.getRecommendationModel();

      if(recommendationModel != null) {
         List<VSObjectRecommendation> list = recommendationModel.getRecommendationList();

         for (VSObjectRecommendation item: list) {
            if(item.getType() == type) {
               return item;
            }
         }
      }

      return null;
   }

   public static String nextPrimaryAssemblyName() {
      return TEMP_ASSEMBLY_PREFIX + System.currentTimeMillis();
   }

   public static VSAssembly getTempAssembly(Viewsheet vs) {
      assert vs != null;
      return vs.getLatestTempAssembly();
   }

   public static boolean isTempDataAssembly(String assemblyName) {
      return !StringUtils.isEmpty(assemblyName)
         && (TEMP_CHART_NAME.equals(assemblyName)
         || assemblyName.startsWith(TEMP_CROSSTAB_NAME));
   }

   public static boolean isTempAssembly(String assemblyName) {
      return !StringUtils.isEmpty(assemblyName)
         && assemblyName.startsWith(TEMP_ASSEMBLY_PREFIX);
   }

   public static boolean isWizardTempAssembly(String assemblyName) {
      return isTempAssembly(assemblyName) || isWizardTempBindingAssembly(assemblyName);
   }

   public static boolean isWizardTempBindingAssembly(String assemblyName) {
      return !StringUtils.isEmpty(assemblyName) &&
         (assemblyName.startsWith(TEMP_CROSSTAB_NAME) || TEMP_CHART_NAME.equals(assemblyName));
   }

   private static boolean isQueryMergeable(RuntimeViewsheet rvs, String tableName) throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      Worksheet ws = vs.getBaseWorksheet();

      if(!VSUtil.isVSAssemblyBinding(tableName)) {
         TableAssembly table = (TableAssembly) ws.getAssembly(tableName);
         AssetQuery query = AssetUtil.handleMergeable(rvs.getRuntimeWorksheet(), table);
         return query != null && query.isQueryMergeable(false);
      }

      return false;
   }

   // create calculate field for alias
   public static void prepareCalculateRefs(RuntimeViewsheet rvs, ChartVSAssemblyInfo ainfo,
                                           VSTemporaryInfo tempInfo)
      throws Exception
   {
      if(rvs == null || ainfo == null) {
         return;
      }

      Viewsheet vs = rvs.getViewsheet();
      VSChartInfo cinfo = ainfo.getVSChartInfo();
      SourceInfo sourceInfo = ainfo.getSourceInfo();
      String source = sourceInfo != null ? sourceInfo.getSource() : null;

      if(StringUtils.isEmpty(source)) {
         return;
      }

      boolean sqlMergeable = isQueryMergeable(rvs, ainfo.getTableName());

      for(ChartRef ref : cinfo.getBindingRefs(false)) {
         if(ref instanceof VSAggregateRef) {
            String refValue = ((VSAggregateRef) ref).getColumnValue();

            if(refValue != null && refValue.startsWith("Total@")) {
               CalculateRef calc = new CalculateRef();
               String col = refValue.substring(6);
               calc.setName(refValue);
               ExpressionRef expr = new ExpressionRef();
               expr.setExpression("field['" + col + "']");
               expr.setName(refValue);
               expr.setDataType(((VSAggregateRef) ref).getOriginalDataType());
               calc.setDataRef(expr);
               calc.setSQL(sqlMergeable);

               ((VSAggregateRef) ref).setDataRef(new ColumnRef(calc));
               vs.addCalcField(source, calc);
               ((VSAggregateRef) ref).setColumnValue(calc.getName());
            }
         }
         else {
            String refValue = ((VSDimensionRef) ref).getGroupColumnValue();

            if(refValue != null && refValue.startsWith("Range@")) {
               createRangeDimension((VSDimensionRef) ref, rvs, source, tempInfo);
            }
         }
      }

      cleanupCalculateRefs(vs, ainfo);
   }

   private static void createRangeDimension(VSDimensionRef dim, RuntimeViewsheet rvs,
                                            String tname, VSTemporaryInfo tempInfo)
         throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      String refValue = dim.getGroupColumnValue();
      String field = refValue.substring(6); // strip off Range@
      IntervalData interval = IntervalExecutor.getData(box, tempInfo, dim, tname, XSchema.DOUBLE);

      double min = interval != null ? interval.getMin() : 0;
      double max = interval != null ? interval.getMax() : 100;
      final int N = 8; // number of ticks to create bins
      double[] vals = GTool.getNiceNumbers(min, max, Double.NaN, Double.NaN, N, false);
      double inc = vals[2];
      final int MAX_PARTITIONS_NUMBER = 20;//max number of partitions

      if((max - min) / inc < 6) {
         inc /= 2;
      }
      else if((max - min) / inc > MAX_PARTITIONS_NUMBER) {
         inc = (max - min) / MAX_PARTITIONS_NUMBER;
      }

      min = vals[0] + inc;
      max = vals[1] > max ? vals[1] - inc : vals[1];
      final int n = (int) Math.round((max - min) / inc) - 1;

      StringBuilder builder = new StringBuilder();
      ArrayList<String> ranges = new ArrayList<>();
      field = field.replace("'", "\\'");

      for(int i = 0; i <= n; min += inc, i++) {
         if(i == 0) {
            String range = "< " + Tool.toString(min);
            builder.append("if(field['" + field + "'] < " + Tool.toString(min) + ") {\n");
            builder.append("  '" + range + "'\n");
            builder.append("}\n");
            ranges.add(range);
         }

         if(i == n) {
            String range = "> " + Tool.toString(min);
            builder.append("else {\n");
            builder.append("  '" + range + "'\n");
            builder.append("}\n");
            ranges.add(range);
         }
         else {
            String range = Tool.toString(min) + " - " + Tool.toString(min + inc);
            builder.append("else if(field['" + field + "'] >= " + Tool.toString(min) +
                              " && field['" + field + "'] < " + Tool.toString(min + inc) + ") {\n");
            builder.append("  '" + range + "'\n");
            builder.append("}\n");
            ranges.add(range);
         }
      }

      CalculateRef calc = new CalculateRef();
      calc.setName(refValue);
      ExpressionRef expr = new ExpressionRef();
      expr.setExpression(builder.toString());
      expr.setName(refValue);
      calc.setDataRef(expr);
      calc.setSQL(false);

      dim.setDataRef(new ColumnRef(calc));
      rvs.getViewsheet().addCalcField(tname, calc);
      dim.setGroupColumnValue(calc.getName());
      dim.setOrder(XConstants.SORT_SPECIFIC);
      dim.setManualOrderList(ranges);
   }

   // remove calculate ref created by prepareCalculateRefs() that are no longer used
   public static void cleanupCalculateRefs(Viewsheet vs, ChartVSAssemblyInfo newInfo) {
      Set<String> used = new HashSet<>();

      for(Assembly vsobj : vs.getAssemblies()) {
         // Bug #54831, skip the chart assembly that is about to be replaced
         if(vsobj instanceof ChartVSAssembly &&
            (newInfo == null || !vsobj.equals(vs.getLatestTempAssembly())))
         {
            getUsedAutoCreatedCal(((ChartVSAssembly) vsobj).getChartInfo(), used);
         }
      }

      if(newInfo != null) {
         getUsedAutoCreatedCal(newInfo, used);
      }

      for(String source : vs.getCalcFieldSources()) {
         CalculateRef[] crefs = vs.getCalcFields(source);

         if(crefs != null) {
            for(CalculateRef cref : crefs) {
               if((cref.getName().startsWith("Total@") || cref.getName().startsWith("Range@"))
                  && !used.contains(source + ":" + cref.getName()))
               {
                  vs.removeCalcField(source, cref.getName());
               }
            }
         }
      }
   }

   private static void getUsedAutoCreatedCal(ChartVSAssemblyInfo aInfo, Set<String> used) {
      SourceInfo source = aInfo.getSourceInfo();
      VSChartInfo cinfo = aInfo.getVSChartInfo();

      if(source != null) {
         getAllFields(cinfo).stream().forEach(ref -> {
            if(ref instanceof VSAggregateRef) {
               String refValue = ((VSAggregateRef) ref).getColumnValue();

               if(refValue != null && refValue.startsWith("Total@")) {
                  used.add(source.getSource() + ":" + refValue);
               }
            }
            else {
               String refValue = ((VSDimensionRef) ref).getGroupColumnValue();

               if(refValue != null && refValue.startsWith("Range@")) {
                  used.add(source.getSource() + ":" + refValue);
               }
            }
         });
      }
   }

   private static List<DataRef> getAllFields(VSChartInfo cinfo) {
      ChartRef[] refs = cinfo.getBindingRefs(false);
      AestheticRef[] arefs = cinfo.getAestheticRefs(false);
      List<DataRef> list = new ArrayList<>();
      list.addAll(Arrays.asList(refs));

      for(AestheticRef aref : arefs) {
         if(aref.getDataRef() != null) {
            list.add(aref.getDataRef());
         }
      }

      if(cinfo.getPathField() != null) {
         list.add(cinfo.getPathField());
      }

      if(cinfo instanceof CandleChartInfo) {
         CandleChartInfo candleInfo = (CandleChartInfo) cinfo;

         if(candleInfo.getHighField() != null) {
            list.add(candleInfo.getHighField());
         }

         if(candleInfo.getCloseField() != null) {
            list.add(candleInfo.getCloseField());
         }

         if(candleInfo.getLowField() != null) {
            list.add(candleInfo.getLowField());
         }

         if(candleInfo.getOpenField() != null) {
            list.add(candleInfo.getOpenField());
         }
      }

      return list;
   }

   public static boolean isLatestRecommend(VSTemporaryInfo temporaryInfo) {
      Date latestRecommendTime = temporaryInfo.getRecommendLatestTime();
      Date recommendTime = RecommendSequentialContext.getStartTime();

      if(latestRecommendTime == null || recommendTime == null) {
         return true;
      }

      return recommendTime.equals(latestRecommendTime) || recommendTime.after(latestRecommendTime);
   }

   public static boolean ignoreRefreshTempAssembly() {
      return ignoreRefreshTempAssembly.get() == null ? false : ignoreRefreshTempAssembly.get();
   }

   public static void setIgnoreRefreshTempAssembly(Boolean ignore) {
      if(ignore == null) {
         ignoreRefreshTempAssembly.remove();
      }
      else {
         ignoreRefreshTempAssembly.set(ignore);
      }
   }

   private static final String CHILDREN_FLAG = "__children__";
   private static final String CHILDREN_SPLITER = "__:::::__";
   private static final String DEPENDENCY_CHECKED = "__dependency_checked__";
   //some operation will case recommend. so TempAssembly will be created new Assembly,
   //This will cause multithreading problems when recommend and refresh is asynchronous.
   private static final ThreadLocal<Boolean> ignoreRefreshTempAssembly = new ThreadLocal<>();
   private static final Logger LOG = LoggerFactory.getLogger(WizardRecommenderUtil.class);
}
