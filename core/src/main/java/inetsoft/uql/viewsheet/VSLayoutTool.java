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
package inetsoft.uql.viewsheet;

import inetsoft.report.*;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.report.filter.CrossCalcFilter;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.*;
import inetsoft.report.internal.layout.GroupableTool;
import inetsoft.report.internal.table.CalcAttr;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utilities API for change viewsheet table layout.
 *
 * @version 11.3
 * @author InetSoft Technology Corp
 */
public class VSLayoutTool extends LayoutTool {
   /**
    * Create calc table lens.
    */
   public static void createCalcLens(CalcTableVSAssembly assembly,
                                     TableLens base, VariableTable vars,
                                     boolean crossTabSupported)
   {
      createCalcLayout(assembly, vars, crossTabSupported);
      createDataPathMapping(assembly.getTableLayout(), assembly.getBaseTable());
   }

   /**
    * Generate table/crosstab layout.
    * @param lens the meta table lens.
    */
   public static TableLayout generateLayout(TableDataVSAssembly assembly,
                                            TableLens lens, Viewsheet vs)
   {
      TableLayout layout = new TableLayout();

      if(assembly instanceof CrosstabVSAssembly) {
         CrosstabVSAssembly cassembly = (CrosstabVSAssembly) assembly;
         VSCrosstabInfo cinfo = cassembly.getVSCrosstabInfo();
         SourceInfo sourceInfo = assembly.getSourceInfo();
         VSAssembly sourceAssembly = null;

         if(sourceInfo != null && sourceInfo.getType() == XSourceInfo.VS_ASSEMBLY && vs != null) {
            sourceAssembly = vs.getAssembly(VSUtil.getVSAssemblyBinding(sourceInfo.getSource()));
         }

         // no aggregate, treat as normal table
         if(cinfo == null || cinfo.getRuntimeAggregates().length == 0) {
            if(lens == null) {
               layout = createLayout(new CalcTableLens(2, 1));
            }
            else if(sourceAssembly instanceof CrosstabVSAssembly) {
               layout = createLayout(new DefaultTableLens(lens));
            }
            else {
               layout = createLayout(lens);
            }

            layout.setMode(TableLayout.NORMAL);
         }
         else {
            layout = generateCrosstabLayout(
               (CrosstabVSAssemblyInfo) cassembly.getInfo(), lens, lens);
         }
      }
      else if(assembly instanceof TableVSAssembly) {
         TableVSAssembly tassembly = (TableVSAssembly) assembly;
         layout = generateNormalLayout(
            (TableVSAssemblyInfo) tassembly.getInfo(), lens);
      }

      return layout;
   }

   /**
    * Create a default table layout for calc table.
    */
   public static TableLayout createDefaultLayout() {
      return createLayout(new CalcTableLens(2, 2));
   }

   /**
    * Create a table layout by table lens.
    * @param lens the meta table lens.
    */
   static TableLayout createLayout(TableLens lens) {
      return createLayout(lens, null);
   }

   /**
    * Create a table layout by table lens.
    * @param sample the meta table lens.
    */
   private static TableLayout createLayout(TableLens sample, VSCrosstabInfo cinfo) {
      TableLayout layout = new TableLayout();

      if(sample == null) {
         return layout;
      }

      TableLens metadata = sample;

      if(metadata instanceof CrossCalcFilter) {
         metadata = ((CrossCalcFilter) metadata).getTable();
      }

      if(metadata instanceof CrossTabFilter) {
         ((CrossTabFilter) metadata).setCondition(null);
      }

      metadata.moreRows(Integer.MAX_VALUE);
      TableDataDescriptor desc = metadata.getDescriptor();

      if(cinfo != null && cinfo.getRuntimeAggregates().length > 1) {
         // @by jasons, add detail rows for crosstab from last to first so that
         // the regions are in the same order as the aggregates
         addLayoutRegion(0, metadata.getHeaderRowCount(), metadata, layout);

         // @by davyc, table layout should fully match the table lens, otherwise
         //  all place use table layout will be error, such as format pane in
         //  adhoc, your will find the layout is error, not match the table
         addLayoutRegion(
            metadata.getHeaderRowCount(), metadata.getRowCount(), metadata, layout);
      }
      else {
         // create regions
         addLayoutRegion(0, metadata.getRowCount(), metadata, layout);
      }

      // set column count
      layout.setColCount(metadata.getColCount());
      AggregateInfo ainfo = cinfo == null ? null : cinfo.getAggregateInfo();

      // create v regions
      if(ainfo != null && (ainfo.getGroupCount() > 0 || ainfo.getAggregateCount() > 0)) {
         for(int i = 0; i < metadata.getColCount(); i++) {
            TableDataPath cpath = desc.getColDataPath(i);
            TableLayout.VRegion region = layout. new VRegion();
            layout.addVRegion(cpath, region);
         }
      }

      if(desc.getType() == TableDataDescriptor.CROSSTAB_TABLE) {
         createCrosstabCellBinding(metadata, sample, layout);
         formatCrossSymbol(cinfo, layout, metadata);
      }
      else {
         String[] hdrs = new String[metadata.getColCount()];

         for(int i = 0; i < hdrs.length; i++) {
            Object hdr = Util.getHeader(metadata, i);

            if(hdr != null) {
               hdrs[i] = hdr.toString();
            }
         }

         // generate cell binding, span
         for(int i = 0; i < metadata.getRowCount(); i++) {
            TableDataPath rpath = desc.getRowDataPath(i);
            BaseLayout.Region region = layout.getRegion(rpath);

            for(int j = 0; j < metadata.getColCount(); j++) {
               CellBinding binding = null;
               boolean summary = isCrosstab(cinfo) &&
                  desc.getCellDataPath(i, j).getType() == TableDataPath.TRAILER;
               boolean plain = desc.getType() == TableDataDescriptor.NORMAL_TABLE;

               if(metadata.getObject(i, j) != null || summary || plain) {
                  binding = createCellBinding(getBType(region), hdrs[j]);
                  region.setCellBinding(rpath.getIndex(), j, binding);
               }
            }
         }

         // set span, span is import for format symbol
         for(int i = 0; i < metadata.getRowCount(); i++) {
            for(int j = 0; j < metadata.getColCount(); j++) {
               layout.setSpan(i, j, metadata.getSpan(i, j));
            }
         }

         formatNormalSymbol(layout, sample);
      }

      return layout;
   }

   /**
    * Check is crosstab or not.
    */
   private static boolean isCrosstab(VSCrosstabInfo cinfo) {
      if(cinfo == null) {
         return false;
      }

      AggregateInfo ainfo = cinfo.getAggregateInfo();

      if(ainfo != null && ainfo.isCrosstab()) {
         return true;
      }

      return cinfo.getRuntimeAggregates().length > 0 &&
             (cinfo.getRuntimeRowHeaders().length > 0 ||
             cinfo.getRuntimeColHeaders().length > 0);
   }

   /**
    * Create a calc table layout by TableLayout.
    */
   private static void createCalcLayout(CalcTableVSAssembly assembly,
                                        VariableTable vars,
                                        boolean crossTabSupported)
   {
      fillCalcTableLens(assembly, vars, crossTabSupported);
   }

   /**
    * Generate crosstab layout.
    */
   private static TableLayout generateCrosstabLayout(
      CrosstabVSAssemblyInfo info, TableLens lens, TableLens metadata)
   {
      VSCrosstabInfo cinfo = info.getVSCrosstabInfo();

      if(cinfo == null) {
         return createLayout(new CalcTableLens(2, 1));
      }

      AggregateInfo ainfo = cinfo.getAggregateInfo();

      if(ainfo == null || ainfo.isEmpty()) {
         return createLayout(new CalcTableLens(2, 1));
      }

      ainfo.setCrosstab(true);
      TableLayout layout = createLayout(lens, cinfo);
      layout.setMode(TableLayout.CROSSTAB);

      return layout;
   }

   /**
    * Generate normal table layout.
    */
   private static TableLayout generateNormalLayout(TableVSAssemblyInfo info,
                                                   TableLens lens) {
      if(info == null || info.getColumnSelection().getAttributeCount() <= 0) {
         return createLayout(new CalcTableLens(2, 1));
      }

      TableLayout nlayout = createLayout(lens);
      nlayout.setMode(TableLayout.NORMAL);
      return nlayout;
   }

   /**
    * Format cross table symbol, for example, expansion, group or aggregate.
    */
   private static void formatCrossSymbol(VSCrosstabInfo cinfo,
                                         TableLayout layout,
                                         TableLens lens)
   {
      if(cinfo == null) {
         return;
      }

      GroupRef[] rgrefs = getGroupRefs(
         cinfo, cinfo.getRuntimeRowHeaders(), false);
      GroupRef[] cgrefs = getGroupRefs(
         cinfo, cinfo.getRuntimeColHeaders(), false);
      GroupRef[] grps = new GroupRef[rgrefs.length + cgrefs.length];
      System.arraycopy(rgrefs, 0, grps, 0, rgrefs.length);
      System.arraycopy(cgrefs, 0, grps, rgrefs.length, cgrefs.length);
      AggregateRef[] sums = getAggregateRefs(
         cinfo, cinfo.getRuntimeAggregates(), false);
      formatCrossSymbol(grps, sums, layout, lens);
   }

   /**
    * Get group refs.
    */
   public static GroupRef[] getGroupRefs(VSCrosstabInfo cinfo, DataRef[] refs,
                                         boolean convert)
   {
      if(refs == null) {
         return new GroupRef[0];
      }

      GroupRef[] grefs = new GroupRef[refs.length];

      for(int i = 0; i < refs.length; i++) {
         VSDimensionRef dim = (VSDimensionRef) refs[i];
         grefs[i] = dim.createGroupRef(null);

         if(convert) {
            prepareOrderInfo(cinfo, dim, grefs[i]);
            prepareTopNInfo(cinfo, dim, grefs[i]);
            grefs[i].setTimeSeries(dim.isTimeSeries());
         }
      }

      return grefs;
   }

   /**
    * Prepere OrderInfo.
    */
   private static void prepareOrderInfo(VSCrosstabInfo cinfo,
                                        VSDimensionRef dim, GroupRef group)
   {
      OrderInfo order = new OrderInfo();
      order = new OrderInfo();
      order.setInterval(1, dim.getRealDateLevel());
      order.setOrder(dim.getOrder());

      if(dim.getOrder() == OrderInfo.SORT_VALUE_ASC ||
         dim.getOrder() == OrderInfo.SORT_VALUE_DESC)
      {
         AggregateRef[] arefs = getAggregateRefs(cinfo,
            cinfo.getRuntimeAggregates(), true);
         int idx = findAttributeIndex(arefs, dim.getSortByCol());
         order.setSortByCol(idx < 0 ? 0 : idx);
      }
      else if(dim.getOrder() == OrderInfo.SORT_SPECIFIC) {
         order.setManualOrder(dim.getManualOrderList());
      }

      group.setOrderInfo(order);
   }

   /**
    * Prepere OrderInfo.
    */
   private static void prepareTopNInfo(VSCrosstabInfo cinfo,
                                       VSDimensionRef dim, GroupRef group)
   {
      TopNInfo topN = new TopNInfo();

      if(dim.getRankingOption() == XCondition.TOP_N ||
         dim.getRankingOption() == XCondition.BOTTOM_N)
      {
         topN.setTopN(dim.getRankingN());
      }

      topN.setTopNReverse(dim.getRankingOption() == XCondition.BOTTOM_N);
      topN.setOthers(dim.isGroupOthers());

      String rankingCol = dim.getRankingCol();
      rankingCol = trimCalculate(cinfo, rankingCol);

      if(dim.getRankingOption() == XCondition.BOTTOM_N ||
         dim.getRankingOption() == XCondition.TOP_N)
      {
         AggregateRef[] arefs = getAggregateRefs(cinfo,
            cinfo.getRuntimeAggregates(), true);
         int idx = findAttributeIndex(arefs, rankingCol);

         // bc problem, because in previous version, the ranking column without
         // formula. eg. orderon -- sum(orderon)
         if(idx == -1) {
            for(int i = 0; i < arefs.length; i++) {
               if(rankingCol.equals(trimFormula(arefs[i]))) {
                  idx = i;
                  break;
               }
            }
         }

         topN.setTopNSummaryCol(idx);
      }

      group.setTopN(topN);
   }

   private static String trimCalculate(VSCrosstabInfo cinfo, String refName) {
      DataRef[] runtimeAggregates = cinfo.getAggregates();

      for(DataRef dataRef : runtimeAggregates) {
         VSAggregateRef aggref = (VSAggregateRef) dataRef;

         if(Tool.equals(refName, aggref.getFullName())) {
            return aggref.getFullName(false);
         }
      }

      return refName;
   }

   /**
    * Get aggregate refs.
    */
   public static AggregateRef[] getAggregateRefs(VSCrosstabInfo cinfo,
                                                 DataRef[] refs,
                                                 boolean convert)
   {
      if(refs == null) {
         return new AggregateRef[0];
      }

      AggregateRef[] arefs = new AggregateRef[refs.length];

      for(int i = 0; i < refs.length; i++) {
         arefs[i] = ((VSAggregateRef) refs[i]).createAggregateRef(null);
      }

      return arefs;
   }

   /**
    * Format cross table symbol, for example, expansion, group or aggregate.
    */
   private static void formatNormalSymbol(TableLayout layout,
                                          TableLens lens) {
      if(lens instanceof CalcTableLens) {
         return;
      }

      TableDataDescriptor desc = lens.getDescriptor();
      lens.moreRows(Integer.MAX_VALUE);

      for(int i = 0; i < lens.getRowCount(); i++) {
         TableDataPath rpath = desc.getRowDataPath(i);
         BaseLayout.Region region = layout.getRegion(rpath);
         int grow = layout.locateRow(rpath);
         int rrow = layout.convertToRegionRow(grow);
         int type = rpath.getType();

         for(int j = 0; j < lens.getColCount(); j++) {
            CellBinding bind = region.getCellBinding(rrow, j);

            if(!(bind instanceof TableCellBinding) ||
               bind.getType() != CellBinding.BIND_COLUMN)
            {
               continue;
            }

            TableCellBinding cbind = (TableCellBinding) bind;

            if(type == HEADER || type == FOOTER) {
               GroupableTool.formatDetailSymbol(cbind,
                  GroupableCellBinding.EXPAND_NONE);
            }
            else if(type == DETAIL) {
               GroupableTool.formatDetailSymbol(cbind,
                  GroupableCellBinding.EXPAND_V);
            }
         }
      }
   }

   /**
    * Find field used for current cell binding.
    */
   public static DataRef findAttribute(FormulaTable table,
                                       TableCellBinding binding,
                                       String cname)
   {
      if(!(table instanceof TableDataVSAssembly) || binding == null) {
         return null;
      }

      TableDataVSAssembly assembly = (TableDataVSAssembly) table;
      String tname = assembly.getTableName();
      Viewsheet vs = assembly.getViewsheet();
      Worksheet ws = vs.getBaseWorksheet();

      if(vs == null || ws == null) {
         return null;
      }

      TableAssembly tb = VSAQuery.getVSTableAssembly(tname, true, vs, ws);

      if(tb == null) {
         return null;
      }

      ColumnSelection columns = tb.getColumnSelection();
      XSourceInfo source = assembly.getSourceInfo();
      String value = binding.getValue();
      DataRef ref = null;

      CalculateRef[] calcs = vs.getCalcFields(tname);

      for(int i = 0; calcs != null && i < calcs.length; i++) {
         if(!calcs[i].isBaseOnDetail() && calcs[i].getName().equals(value)) {
            ref = new AttributeRef(value);

            if(calcs[i].getDataRef() instanceof ExpressionRef) {
               binding.setExpression(
                  ((ExpressionRef) calcs[i].getDataRef()).getExpression());
            }

            return ref;
         }
      }

      String formula = BindingTool.getFormulaString(binding.getFormula());
      value = source.getType() == XSourceInfo.MODEL ?
         source.getPrefix() + "." + value : value;
      ref = columns.getAttribute(value);

      // remove formula
      if(ref == null && formula != null && formula.length() != 0) {
         value = binding.getValue();

         if(value != null && value.startsWith(formula)) {
            value = value.substring(formula.length() + 1, value.length() - 1);
         }
      }
      // remove date group
      else {
         value = getOriginalColumn(value);
      }

      if(ref == null) {
         value = source.getType() == XSourceInfo.MODEL ?
               source.getPrefix() + "." + value : value;
         ref = columns.getAttribute(value);
      }

      if(ref == null && cname != null) {
         ref = columns.getAttribute(cname);
      }

      if(ref == null) {
         int option = binding.getDateOption();

         if(option > 0 && cname.endsWith("_" + DateRangeRef.getRangeValue(option))) {
            int idx = cname.indexOf("_" + DateRangeRef.getRangeValue(option));
            ref = columns.getAttribute(cname.substring(0, idx));
         }
      }

      if(ref == null && cname != null && cname.lastIndexOf("_") != -1) {
         int idx = cname.lastIndexOf("_");
         String dupNum = cname.substring(idx + 1);

         try {
            Integer.parseInt(dupNum);
            ref = findAttribute(table, binding, cname.substring(0, idx));
         }
         catch(NumberFormatException ignore) {
         }
      }

      if(ref == null) {
         String fname = getFieldNameByScript(value);

         if(fname != null && !Tool.equals(fname, cname)) {
            ref = findAttribute(table, binding, fname);
         }
      }

      return ref;
   }

   /**
    * Find a field by its name from the specifield fields.
    */
   public static DataRef findAttribute(DataRef[] refs, String name) {
      int idx = findAttributeIndex(refs, name);
      return idx == -1 ? null : refs[idx];
   }

   /**
    *  Find an aggregate field which may appling percent calc.
    */
   public static AggregateRef findAggregateRef(DataRef[] refs, String name, int percentageByOption) {
      for(int i = 0; i < refs.length; i++) {
         if(!(refs[i] instanceof AggregateRef)) {
            continue;
         }

         AggregateRef aref = (AggregateRef) refs[i];

         if(aref.getPercentageType() == StyleConstants.PERCENTAGE_NONE) {
            continue;
         }

         PercentCalc percentCalc = new PercentCalc();
         percentCalc.setPercentageByValue(percentageByOption + "");
         int ptype = aref.getPercentageType();
         boolean subTotal = ptype == XConstants.PERCENTAGE_OF_GROUP ||
            ptype == XConstants.PERCENTAGE_OF_ROW_GROUP ||
            ptype == XConstants.PERCENTAGE_OF_COL_GROUP;
         percentCalc.setLevel(subTotal ? PercentCalc.SUB_TOTAL : PercentCalc.GRAND_TOTAL);
         String prefix = percentCalc.getPrefix();

         if(Tool.equals(prefix + BindingTool.getFieldName(aref), name) ||
            Tool.equals(prefix + guessPreferName(refs, aref, i), name))
         {
            return aref;
         }
      }

      return null;
   }

   /**
    * Find a field by its name from the specifield fields idx.
    */
   private static int findAttributeIndex(DataRef[] refs, String name) {
      return LayoutTool.findFieldIndex(refs, name);
   }

   /**
    * Get original column.
    * @param col the column name.
    * @param ref the dataref of the target col.
    */
   public static String getOriginalColumn(String col, DataRef ref) {
      if(ref instanceof GroupRef) {
         GroupRef gref = (GroupRef) ref;

         if(getAlias(gref) == null) {
            DataRef range = getDateRangeRef(gref);

            if(range != null && ((DateRangeRef) range).getDataRef() != null) {
               return ((DateRangeRef) range).getDataRef().getName();
            }
         }

         if(col != null && !col.startsWith("") &&
            gref.getDateGroup() == XConstants.NONE_DATE_GROUP)
         {
            return col;
         }
      }

      return getOriginalColumn(col);
   }

   /**
    * Get original column.
    */
   public static String getOriginalColumn(String col) {
      if(col == null) {
         return null;
      }

      int left = col.indexOf("(");

      if(left >= 0) {
         col = col.substring(left + 1);
      }

      int right = col.lastIndexOf(")");

      if(right >= 0) {
         return col.substring(0, right);
      }

      return col;
   }

   private static String getAlias(DataRef ref) {
      if(ref == null) {
         return null;
      }

      if(ref instanceof ColumnRef) {
         return ((ColumnRef) ref).getAlias();
      }

      if(ref instanceof DataRefWrapper) {
         return getAlias(((DataRefWrapper) ref).getDataRef());
      }

      return null;
   }

   private static DataRef getDateRangeRef(DataRef ref) {
      if(ref == null || ref instanceof DateRangeRef) {
         return ref;
      }

      if(ref instanceof DataRefWrapper) {
         return getDateRangeRef(((DataRefWrapper) ref).getDataRef());
      }

      return null;
   }

   /**
    * @return if the group field is timeseries.
    */
   public static boolean isTimeSeries(GroupRef ref) {
      if(ref == null || !ref.isTimeSeries()) {
         return false;
      }

      OrderInfo order = ref == null ? null : ref.getOrderInfo();

      if(order != null && order.getOption() == DateRangeRef.NONE_INTERVAL) {
         return false;
      }

      return true;
   }

   /**
    * Get aggregate column.
    */
   public static String getAggregateColumn(String col, String aggrColName) {
      return getAggregateColumn(col, aggrColName, null);
   }

   /**
    * Get aggregate column.
    */
   public static String getAggregateColumn(String col, String aggrColName,
                                           AggregateFormula aggregateFormula)
   {
      if(col == null) {
         return null;
      }

      String original = getOriginalColumn(col);

      if(Tool.equals(original, aggrColName)) {
         return original;
      }

      original = Tool.replaceAll(original, "[", "");
      original = Tool.replaceAll(original, "]", "");

      if(aggregateFormula == null || aggregateFormula.isTwoColumns() || aggregateFormula.hasN()) {
         String[] columns = Tool.split(original, ',');

         return columns[0];
      }


      return original;
   }

   /**
    * Sync format.
    */
   public static void syncCellFormat(CalcTableVSAssembly cassembly,
                                     TableLens source, TableLens target,
                                     boolean crosstab, List<String> clearCalcs,
                                     boolean vsAssemblyBinding)
   {
      if(cassembly == null || target == null) {
         return;
      }

      VSAssemblyInfo info = (VSAssemblyInfo) cassembly.getInfo();
      FormatInfo finfo = info.getFormatInfo();

      if(finfo == null) {
         return;
      }

      FormatInfo newFormatInfo = finfo.clone();

      // Remove all cell formats from the format info to avoid having formats that
      // no longer apply to the freehand table
      for(TableDataPath formatPath : newFormatInfo.getPaths()) {
         VSCompositeFormat fmt = newFormatInfo.getFormat(formatPath);

         if(formatPath.isCell()) {
            newFormatInfo.setFormat(formatPath, null);
         }
         else {
            updateCssType(fmt);
         }
      }

      // empty source table, e.g. no data bound yet
      if(source == null) {
         info.setFormatInfo(newFormatInfo);
         return;
      }

      Map fmtmap = finfo.getFormatMap();
      TableDataPath path = new TableDataPath(-1, TableDataPath.OBJECT);
      VSCompositeFormat objfmt = (VSCompositeFormat) fmtmap.get(path);

      if(objfmt == null && path != null && path.getType() == path.DETAIL) {
         path = new TableDataPath(0, path.SUMMARY,
            path.getDataType(), path.getPath());
         objfmt = (VSCompositeFormat) fmtmap.get(path);
      }

      boolean objAlignment = false;

      if(objfmt != null) {
         objAlignment = objfmt.getUserDefinedFormat().isAlignmentDefined();
      }

      TableDataDescriptor sdeac = source.getDescriptor();
      TableDataDescriptor tdeac = target.getDescriptor();
      boolean crosstabStyle = false;

      if(crosstab && "Default Style".equals(cassembly.getTableStyleValue())) {
         cassembly.setTableStyleValue("inetsoft.report.style.CrosstabStyle");
         crosstabStyle = true;
      }

      for(int r = 0; source.moreRows(r); r++) {
         for(int c = 0; c < source.getColCount(); c++) {
            TableDataPath spath = sdeac.getCellDataPath(r, c);
            TableDataPath tpath = tdeac.getCellDataPath(r, c);

            VSCompositeFormat vfmt = spath == null ? null :
               (VSCompositeFormat) fmtmap.get(spath);

            // clone just in case since fmtmap has formats from source assembly
            if(vfmt != null) {
               vfmt = vfmt.clone();
            }

            if((crosstab || vsAssemblyBinding) && vfmt == null) {
               vfmt = new VSCompositeFormat();
            }

            if(vfmt != null) {
               boolean clearFormat = false;
               String[] paths = spath.getPath();

               if(clearCalcs != null) {
                  clearFormat = Arrays.stream(paths).filter(p -> clearCalcs.indexOf(p) != -1 ||
                     p.indexOf(".") != -1 && clearCalcs.indexOf(p.substring(0, p.lastIndexOf("."))) != -1)
                     .collect(Collectors.toList()).size() > 0;
               }

               // copy default format (e.g. Percent of Change, 47879)
               if(vfmt.getFormat() == null) {
                  XMetaInfo cell = source.getDescriptor().getXMetaInfo(spath);

                  if(cell != null && cell.getXFormatInfo() != null &&
                     !"DateFormat".equals(cell.getXFormatInfo().getFormat())
                     && !clearFormat && crosstab)
                  {
                     vfmt.getUserDefinedFormat().setFormatValue(cell.getXFormatInfo().getFormat());
                     vfmt.getUserDefinedFormat().setFormatExtentValue(cell.getXFormatInfo().getFormatSpec());
                  }
               }

               // changed to use CrosstabStyle to set the default alignment
               // header cell default to center in crosstab
               if(crosstab && !crosstabStyle) {
                  boolean header = r < source.getHeaderRowCount() || c < source.getHeaderColCount();
                  VSFormat userFmt = vfmt.getUserDefinedFormat();
                  VSCSSFormat cssFmt = vfmt.getCSSFormat();

                  if(!objfmt.getUserDefinedFormat().isAlignmentValueDefined() &&
                     !objfmt.getCSSFormat().isAlignmentValueDefined() &&
                     !userFmt.isAlignmentValueDefined() && !objAlignment &&
                     !cssFmt.isAlignmentValueDefined())
                  {
                     if(header) {
                        userFmt.setAlignmentValue(StyleConstants.H_CENTER | StyleConstants.V_TOP);
                     }
                     else {
                        userFmt.setAlignmentValue(StyleConstants.H_RIGHT | StyleConstants.V_TOP);
                     }
                  }
               }

               updateCssType(vfmt);
               newFormatInfo.setFormat(tpath, vfmt);
            }
         }
      }

      info.setFormatInfo(newFormatInfo);
   }

   private static boolean isTimeFormat(XFormatInfo fmt) {
      // the time formats defined in XUtil.DEFAULT_PATTERN
      return fmt != null && fmt.getFormatSpec() != null &&
         ("HH".equals(fmt.getFormatSpec()) || "HH:mm".equals(fmt.getFormatSpec()) ||
            "HH:mm:ss".equals(fmt.getFormatSpec()));
   }

   private static void updateCssType(VSCompositeFormat fmt) {
      VSCSSFormat cssFormat = fmt.getCSSFormat();

      if(cssFormat != null && cssFormat.getCSSType() != null) {
         String cssType = cssFormat.getCSSType();

         if(cssType.startsWith(CSSConstants.TABLE)) {
            cssType = CSSConstants.CALC_TABLE + cssType.substring(CSSConstants.TABLE.length());
         }
         else if(cssType.startsWith(CSSConstants.CROSSTAB)) {
            cssType = CSSConstants.CALC_TABLE + cssType.substring(CSSConstants.CROSSTAB.length());
         }

         cssFormat.setCSSType(cssType);
      }
   }

   /**
    * Get parent cell.
    */
   public static XNode getParentNode(XNode node, TableLayout layout,
                                     TableCellBinding binding) {
      XNode parent = null;

      for(int i = 0; i < node.getChildCount(); i++) {
         XNode child = node.getChild(i);
         CalcAttr attr = (CalcAttr) child.getValue();
         CellBinding cell = layout.getCellBinding(attr.getRow(), attr.getCol());

         if(cell == binding) {
            return child.getParent();
         }

         parent = getParentNode(child, layout, binding);

         if(parent != null) {
            return parent;
         }
      }

      return parent;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(LayoutTool.class);
}
