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
package inetsoft.report.composition.execution;

import inetsoft.graph.internal.GTool;
import inetsoft.mv.*;
import inetsoft.report.MemberObjectTableLens;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.lens.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.xmla.*;
import inetsoft.util.MessageFormat;
import inetsoft.util.*;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.log.LogLevel;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.script.JavaScriptEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Time;
import java.text.*;
import java.util.*;

/**
 * TimeSliderVSAQuery, the time slider viewsheet assembly query.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class TimeSliderVSAQuery extends AbstractSelectionVSAQuery {
   /**
    * Create a selection tree viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param vname the specified viewsheet assembly to be processed.
    */
   public TimeSliderVSAQuery(ViewsheetSandbox box, String vname) {
      super(box, vname);
   }

   /**
    * Get the table assembly that contains binding info.
    */
   private TableAssembly getTableAssembly() throws Exception {
      TimeSliderVSAssembly assembly = (TimeSliderVSAssembly) getAssembly();
      TimeInfo tinfo = assembly.getTimeInfo();
      Worksheet ws = getWorksheet();

      if(ws == null) {
         return null;
      }

      TableAssembly tassembly;

      if(assembly.getSourceType() == XSourceInfo.VS_ASSEMBLY) {
         assembly.getVSAssemblyInfo().resetRuntimeValues();
         tassembly = createAssemblyTable(assembly);
      }
      else {
         tassembly = getWSTableAssembly();
      }

      if(tassembly == null) {
         SelectionList selectionList = assembly.getSelectionList();

         if(selectionList != null) {
            selectionList.clear();
         }

         return null;
      }

      // normal time info?
      if(tinfo instanceof SingleTimeInfo) {
         SingleTimeInfo sinfo = (SingleTimeInfo) tinfo;
         ColumnRef column = (ColumnRef) sinfo.getDataRef();
         DataRef ref = column == null ? null : column.getDataRef();
         int unit = sinfo.getRangeType();
         ColumnSelection columns = tassembly.getColumnSelection(false);
         column = VSUtil.getVSColumnRef(column);
         column = (ColumnRef) columns.getAttribute(column.getName());

         if(column == null && ref != null) {
            column = (ColumnRef) columns.getAttribute(ref.getName());
         }

         if(column == null && ref instanceof AttributeRef) {
            column = (ColumnRef) columns.getAttribute(
               ((AttributeRef) ref).getCaption());
         }

         if(column == null) {
            Catalog log = Catalog.getCatalog();
            LOG.warn("Column not found: " + sinfo.getDataRef());
            return null;
         }

         if(sinfo.getMin() != null && sinfo.getMax() != null) {
            return createEmbeddedTable(
               ws, vname, sinfo.getMin(), sinfo.getMax(), column.getDataType(), null);
         }
         else if(unit == TimeInfo.MEMBER || !isWorksheetCube() && AssetUtil.isCubeTable(tassembly))
         {
            columns.clear();
            columns.addAttribute(column);
            int refType = column.getRefType();

            if(refType != DataRef.NONE) {
               SortInfo sortinfo = new SortInfo();
               SortRef sort = new SortRef(column);
               sort.setOrder(XConstants.SORT_ASC);
               sortinfo.addSort(sort);
               tassembly.setSortInfo(sortinfo);
            }
         }
         else {
            if(column instanceof CalculateRef) {
               columns.addAttribute(column);
            }

            AggregateFormula min = AggregateFormula.MIN;
            AggregateFormula max = AggregateFormula.MAX;

            // @by billh, this table can not hit mv for aggregate on expression,
            // here add one special case to support this type of usage (range)
            RuntimeMV mv = tassembly.getRuntimeMV();
            VariableTable vars = getAssetQuerySandbox().getVariableTable();

            if(mv != null && !isDynamicFilter(tassembly, vars)) {
               MVDef def = MVManager.getManager().get(mv.getMV());

               if(def != null) {
                  def = def.update();
                  String rattr = column.getAttribute();

                  if(sinfo.isDateTime()) {
                     DateMVColumn col = CalendarVSAQuery.getDateMVColumn(column, def);

                     if(col != null) {
                        Date omin = col.getMin();
                        Date omax = col.getMax();

                        if(omin != null && omax != null) {
                           return createEmbeddedTable(
                              ws, vname, omin, omax, XSchema.TIME_INSTANT, col.getXMetaInfo());
                        }
                     }
                  }
                  else {
                     MVColumn col = def.getColumn(rattr, false, false);

                     if(col != null) {
                        Number omax = col.getOriginalMax();
                        Number omin = col.getOriginalMin();

                        if(omin != null && omax != null) {
                           XMetaInfo meta = col.getXMetaInfo();
                           return createEmbeddedTable(
                              ws, vname, omin, omax, XSchema.DOUBLE, meta);
                        }
                     }
                  }
               }
            }

            /* @by larryl, not sure why clearing out mv here. should be able to use mv
               if it exists. if the expressions are the problem, we can create a mirror
            RuntimeMV rmv = tassembly.getRuntimeMV();

            if(rmv != null && rmv.isPhysical()) {
               tassembly.setRuntimeMV(null);
            }
            */

            // @by larryl, we use expressionRef here so we can have both max and
            // min aggregate on the same column
            AggregateInfo info = new AggregateInfo();
            AliasDataRef maxexpr = new AliasDataRef(column.getAttribute() + "__MAX", column);
            maxexpr.setDataType(column.getDataType());
            ColumnRef maxcol = new ColumnRef(maxexpr);
            maxcol.setDataType(column.getDataType());
            columns.addAttribute(maxcol);
            info.addAggregate(new AggregateRef(maxcol, null, max));

            AliasDataRef minexpr = new AliasDataRef(column.getAttribute() + "__MIN", column);
            minexpr.setDataType(column.getDataType());
            ColumnRef mincol = new ColumnRef(minexpr);
            mincol.setDataType(column.getDataType());
            columns.addAttribute(mincol);
            info.addAggregate(new AggregateRef(mincol, null, min));

            tassembly.setAggregateInfo(info);
            // ignore null otherwise a null could cause a numeric column not
            // usable as in a range slider
            ConditionList conds = new ConditionList();
            Condition cond = new Condition(column.getDataType());
            cond.setNegated(true);
            cond.setOperation(Condition.NULL);
            conds.append(new ConditionItem(column, cond, 0));
            tassembly.setPreRuntimeConditionList(conds);
         }

         tassembly.setColumnSelection(columns);
      }
      // composite time info?
      else {
         // we produce a groupBy table for MV so it can be pre-aggregated.
         // A plain 'select distinct' is not supported by materialized view
         CompositeTimeInfo cinfo = (CompositeTimeInfo) tinfo;
         DataRef[] refs = cinfo.getDataRefs();

         ColumnSelection columns = tassembly.getColumnSelection(false);
         ColumnSelection columns2 = columns.clone();
         columns.clear();
         SortInfo sinfo = new SortInfo();
         AggregateInfo ainfo = new AggregateInfo();

         for(DataRef dataRef : refs) {
            ColumnRef column = (ColumnRef) dataRef;
            DataRef ref = column == null ? null : column.getDataRef();
            column = VSUtil.getVSColumnRef((ColumnRef) dataRef);
            String name = column.getName();
            column = (ColumnRef) columns2.getAttribute(name);

            if(column == null && ref != null) {
               column = (ColumnRef) columns2.getAttribute(ref.getName());
            }

            if(column == null && ref instanceof AttributeRef) {
               column = (ColumnRef) columns2.getAttribute(
                  ((AttributeRef) ref).getCaption());
            }

            if(column == null) {
               Catalog log = Catalog.getCatalog();
               LOG.warn("Column not found: " + name);
               continue;
            }

            column.setVisible(true);
            columns.addAttribute(column);
            ainfo.addGroup(new GroupRef(column));
            SortRef sort = new SortRef(column);
            sort.setOrder(XConstants.SORT_ASC);
            sinfo.addSort(sort);
         }

         for(int i = 0; i < columns2.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) columns2.getAttribute(i);

            if(!columns.containsAttribute(column)) {
               column.setVisible(false);
               columns.addAttribute(column);
            }
         }

         tassembly.setColumnSelection(columns);
         tassembly.setAggregateInfo(ainfo);
         tassembly.setSortInfo(sinfo);
      }

      return tassembly;
   }

   private TableAssembly createAssemblyTable(TimeSliderVSAssembly slider) throws Exception {
      Viewsheet vs = getViewsheet();
      String assembly = slider.getTableName();
      TimeInfo tinfo = slider.getTimeInfo();
      DataRef[] refs = null;

      if(tinfo instanceof SingleTimeInfo) {
         DataRef ref = ((SingleTimeInfo) tinfo).getDataRef();

         if(ref != null) {
            refs = new DataRef[] {ref};
         }
      }
      else {
         refs = ((CompositeTimeInfo) tinfo).getDataRefs();
      }

      if(assembly == null || refs == null || refs.length <= 0) {
         return null;
      }

      VSAssembly vassembly = (VSAssembly) vs.getAssembly(assembly);

      if(vassembly == null) {
         return null;
      }

      box.updateAssembly(assembly);
      VSAQuery query = VSAQuery.createVSAQuery(box, vassembly, DataMap.NORMAL);

      if(!(query instanceof BindableVSAQuery)) {
         return null;
      }

      if(query instanceof AbstractCrosstabVSAQuery) {
         ((AbstractCrosstabVSAQuery) query).setPushDown(true);
      }

      TableAssembly table = ((BindableVSAQuery) query).createBindableTable();

      if(table == null) {
         return table;
      }

      Worksheet ws = table.getWorksheet();
      // name should start with V_, otherwise PreAssetQuery.getAggregateInfo
      // will return empty
      table.setProperty("Component_Binding_Table", "true");
      String mname = Assembly.TABLE_VS + slider.getName() + "_" + table.getName();
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, mname, table);
      ColumnSelection columns = mirror.getColumnSelection();
      ws.addAssembly(mirror);

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ((ColumnRef) columns.getAttribute(i)).setVisible(false);
      }

      for(DataRef dataRef : refs) {
         DataRef ref = dataRef;
         String alias = ref.getAttribute();
         ref = columns.getAttribute(ref.getAttribute());

         if(ref == null) {
            throw new RuntimeException("column \"" + alias +
               "\" not found in assembly \"" + assembly +
               "\" for \"" + slider.getAbsoluteName() + "\" with columns " +
               getColumnSelectionStr(columns));
         }

         ((ColumnRef) ref).setAlias(alias);
         ((ColumnRef) ref).setVisible(true);
      }

      mirror.setColumnSelection(columns);
      return mirror;
   }

   private String getColumnSelectionStr(ColumnSelection columns) {
      if(columns == null || columns.isEmpty()) {
         return "";
      }

      StringBuffer columnsStr = new StringBuffer();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef attribute = columns.getAttribute(i);

         if(attribute == null) {
            continue;
         }

         columnsStr.append("\"");
         columnsStr.append(attribute.getAttribute());
         columnsStr.append("\"");

         if(i < columns.getAttributeCount() - 1) {
            columnsStr.append(" ,");
         }
      }

      return columnsStr.toString();
   }

   /**
    * Create an embedded table assembly of min and max values.
    */
   private DataTableAssembly createEmbeddedTable(Worksheet ws, String vname,
                                                 Object min, Object max,
                                                 String type, XMetaInfo meta)
   {
      Object[][] arr = {{"max", "min"}, {max, min}};
      String[] types = new String[] {type, type};
      XEmbeddedTable table = new XEmbeddedTable(types, arr);
      DataTableAssembly dtable = new DataTableAssembly(ws, "RANGE_" + vname);

      if(meta != null && meta.getXFormatInfo() != null) {
         // refreshSingleSelectionValue supplies a default date format depending on the
         // interval type. the default format from meta should not be used. (53446, 53446, 53530)
         boolean defaultFmt = "yyyy".equals(meta.getXFormatInfo().getFormatSpec());

         if(!defaultFmt || !(min instanceof Date && max instanceof Date)) {
            table.setXMetaInfo("max", meta);
            table.setXMetaInfo("min", meta);
         }
      }

      dtable.setData(table);
      return dtable;
   }

   /**
    * Don't execute script. It's done in getData for range slider.
    */
   @Override
   protected void refreshViewSelectionValue0() throws Exception {
   }

   /**
    * Get the data.
    * @return the data of the query.
    */
   @Override
   public Object getData() throws Exception {
      TimeSliderVSAssembly assembly = (TimeSliderVSAssembly) getAssembly();

      if(assembly == null) {
         return null;
      }

      // execute script so max/min set from script would be used here
      box.executeScript(assembly);
      TimeInfo tinfo = assembly.getTimeInfo();
      TableAssembly tassembly = getTableAssembly();
      if(tassembly == null) {
         return null;
      }

      // clear default format
      assembly.setDefaultFormat(null);

      // normal time info?
      if(tinfo instanceof SingleTimeInfo) {
         SingleTimeInfo sinfo = (SingleTimeInfo) tinfo;
         int unit = sinfo.getRangeType();
         TableLens table = getTableLens(tassembly);
         boolean cubeData = !isWorksheetCube() && AssetUtil.isCubeTable(tassembly);

         if(table == null) {
            return null;
         }

         table.moreRows(Integer.MAX_VALUE);
         checkMaxRowLimit(table);

         if(unit == TimeInfo.MEMBER || cubeData) {
            if(table.getRowCount() < 2 || !cubeData && table.getColCount() != 1) {
               return null;
            }

            // if MEMBER type, process it as Composite
            return table;
         }
         else if(table.getRowCount() <= 1 || (!cubeData && table.getColCount() < 2)) {
            return null;
         }

         Object max = table.getObject(table.getRowCount() - 1, 0);
         Object min = getMin(table, cubeData, unit);
         Format dfmt = table.getDefaultFormat(table.getRowCount() - 1, 0);
         Date omax = null; //original max date
         // apply default format
         assembly.setDefaultFormat(dfmt);

         if(max == null || min == null) {
            return null;
         }

         TimeSliderVSAssemblyInfo assemblyInfo = ((TimeSliderVSAssemblyInfo) assembly.getVSAssemblyInfo());
         DataRef[] refs = assemblyInfo.getDataRefs();
         String dataType = refs.length == 1 && refs[0] != null ? refs[0].getDataType() : null;
         Object maxVal = Tool.getData(dataType, max);
         Object minVal = Tool.getData(dataType, min);
         max = max instanceof Date || !(maxVal instanceof Date) ? max : maxVal;
         min = min instanceof Date || !(minVal instanceof Date) ? min : minVal;

         if(max instanceof Date) {
            Date dmax = (Date) max;
            omax = dmax;
            Calendar calendar = new GregorianCalendar();
            calendar.setTime(dmax);

            if(unit == TimeInfo.MONTH) {
               calendar.add(Calendar.MONTH, 1);
            }
            else if(unit == TimeInfo.YEAR) {
               calendar.add(Calendar.YEAR, 1);
            }
            else if(unit == TimeInfo.DAY) {
               calendar.add(Calendar.DATE, 1);
            }
            else if(unit == TimeInfo.HOUR) {
               calendar.add(Calendar.HOUR_OF_DAY, 1);
            }
            else if(unit == TimeInfo.MINUTE) {
               calendar.add(Calendar.MINUTE, 1);
            }
            else if(unit == TimeInfo.HOUR_OF_DAY) {
               Calendar ncalendar = new GregorianCalendar();
               ncalendar.setTime(calendar.getTime());
               ncalendar.add(Calendar.HOUR_OF_DAY, 1);

               if(ncalendar.get(Calendar.DAY_OF_MONTH) == calendar.get(Calendar.DAY_OF_MONTH)) {
                  calendar.add(Calendar.HOUR_OF_DAY, 1);
               }
            }
            else if(unit == TimeInfo.MINUTE_OF_DAY) {
               Calendar ncalendar = new GregorianCalendar();
               ncalendar.setTime(calendar.getTime());
               ncalendar.add(Calendar.MINUTE, 1);

               if(ncalendar.get(Calendar.DAY_OF_MONTH) == calendar.get(Calendar.DAY_OF_MONTH)) {
                  calendar.add(Calendar.MINUTE, 1);
               }
            }

            max = calendar.getTime();
         }

         if(omax != null) {
            return new Object[] {min, max, omax};
         }

         return new Object[] {min, max};
      }
      // composite time info?
      else {
         TableMetaData meta = box.getTableMetaData(assembly.getName());

         // try to use association meta data
         RuntimeMV mv = tassembly.getRuntimeMV();

         if(meta != null && mv != null) {
            AggregateInfo ainfo = tassembly.getAggregateInfo();
            GroupRef[] groups = ainfo.getGroups();
            String[] columns = new String[groups.length];

            for(int i = 0; i < groups.length; i++) {
               columns[i] = groups[i].getAttribute();
            }

            XTable tbl = meta.getColumnTable(assembly.getName(), columns);

            if(tbl != null) {
               return tbl;
            }
         }

         return getTableLens(tassembly);
      }
   }

   private Object getMin(TableLens lens, boolean cubeData, int unit) {
      if(cubeData) {
         for(int i = 1; i < lens.getRowCount(); i++) {
            Object obj = lens.getObject(i, 0);

            if(obj != null) {
               return obj;
            }
         }
      }

      return unit == TimeInfo.MEMBER ?
         lens.getObject(lens.getRowCount() - 2, 0) :
         lens.getObject(lens.getRowCount() - 1, 1);
   }

   /**
    *  Get the col type from target tablelens, this function is specifically to find right col type
    *  when binding source is donut chart. Donut chart use a union table with two tables, and the
    *  first table only contains the prepared aggregate which cannot be used in as assembly binding.
    */
   private String getColType(TableLens lens, int col, String field) {
      String dtype = Tool.getDataType(lens.getColType(col));

      if(!XSchema.STRING.equals(dtype) || field == null) {
         return dtype;
      }

      TimeSliderVSAssembly assembly = (TimeSliderVSAssembly) getAssembly();

      if(assembly.getSourceType() != XSourceInfo.VS_ASSEMBLY) {
         return dtype;
      }

      Viewsheet vs = assembly.getViewsheet();
      String table = assembly.getTableName();
      VSAssembly vassembly = vs.getAssembly(table);

      if(!(vassembly instanceof ChartVSAssembly)) {
         return dtype;
      }

      TableLens tlens = Util.getNestedTable(lens, UnionTableLens.class);

      if(tlens == null) {
         return dtype;
      }

      UnionTableLens union = (UnionTableLens) tlens;

      if(union.getTableCount() > 1) {
         TableLens rtable = union.getTable(1);

         int colIdx = Util.findColumn(rtable, field);

         if(colIdx != -1) {
            Class<?> cls = rtable.getColType(colIdx);
            return Tool.getDataType(cls);
         }
      }

      return dtype;
   }

   /**
    * Execute a table assembly and return a table lens.
    * @param table the specified table assembly.
    * @return the table lens as the result.
    */
   @Override
   protected TableLens getTableLens(TableAssembly table) throws Exception {
      TimeSliderVSAssembly assembly = (TimeSliderVSAssembly) getAssembly();
      TimeInfo tinfo = assembly.getTimeInfo();
      TableLens lens = super.getTableLens(table);

      if(lens != null && !isWorksheetCube() && AssetUtil.isCubeTable(table)) {
         lens = new VSCubeTableLens(lens, table.getColumnSelection(true), XMLAUtil.isMetadata(box));
      }

      Object min = tinfo.getMin();
      Object max = tinfo.getMax();

      if(tinfo instanceof CompositeTimeInfo) {
         if(lens != null) {
            DataRef[] refs = assembly.getDataRefs();
            ColumnIndexMap columnIndexMap = new ColumnIndexMap(lens);

            for(DataRef ref : refs) {
               int col = AssetUtil.findColumn(lens, ref, columnIndexMap);

               if(col >= 0 && ref instanceof ColumnRef) {
                  String dtype = getColType(lens, col, ref.getName());

                  if(XSchema.STRING.equals(dtype)) {
                     ((ColumnRef) ref).setDataType(dtype);
                  }
               }
            }
         }

         Object[] minarr = (min instanceof Object[]) ? (Object[]) min : null;
         Object[] maxarr = (max instanceof Object[]) ? (Object[]) max : null;

         if(minarr != null) {
            for(int i = 0; i < minarr.length; i++) {
               minarr[i] = JavaScriptEngine.unwrap(minarr[i]);
            }
         }

         if(maxarr != null) {
            for(int i = 0; i < maxarr.length; i++) {
               maxarr[i] = JavaScriptEngine.unwrap(maxarr[i]);
            }
         }

         if(minarr != null || maxarr != null) {
            if(minarr != null && minarr.length != lens.getColCount() ||
               maxarr != null && maxarr.length != lens.getColCount())
            {
               LOG.debug(
                  "Max/min is ignored since it doesn't match the column count: "
                  + assembly.getName());
               return lens;
            }

            Integer sidx = null, eidx = null;

            for(int r = 1; lens.moreRows(r); r++) {
               // looking for the starting row
               if(sidx == null && minarr != null) {
                  int rc = compareRow(minarr, lens, r);

                  if(rc <= 0) {
                     sidx = r;
                  }
               }
               // looking for the ending row
               else if(maxarr != null) {
                  int rc = compareRow(maxarr, lens, r);

                  if(rc == 0) {
                     eidx = r + 1;
                     break;
                  }
                  else if(rc < 0) {
                     eidx = r;
                     break;
                  }
               }
            }

            if(sidx != null || eidx != null) {
               if(sidx == null) {
                  sidx = 1;
               }

               if(eidx == null) {
                  eidx = lens.getRowCount();
               }

               lens = new SubTableLens(lens, sidx, -1, eidx - sidx, -1);
            }
         }
      }
      else if(min != null && max != null) {
         // single, it's not necessary when both min&max are set
         // the min/max is already returned in the table
      }
      // single, set min/max
      else if(min != null || max != null) {
         DefaultTableLens def = new DefaultTableLens(lens);

         if(min != null) {
            def.setObject(1, 1, min);
         }

         if(max != null) {
            def.setObject(1, 0, max);
         }

         lens = def;
      }

      checkMaxRowLimit(lens);

      return lens;
   }

   /**
    * Compare the tuple with the row.
    */
   private int compareRow(Object[] arr, TableLens lens, int r) {
      for(int i = 0; i < arr.length; i++) {
         int rc = Tool.compare(arr[i], lens.getObject(r, i), true, true);

         if(rc != 0) {
            return rc;
         }
      }

      return 0;
   }

   /**
    * Refresh the selection value.
    * @param data the specified input data.
    */
   public void refreshSelectionValue(Object data) throws Exception {
      // for Feature #26586, add execution breakdown record.

      ProfileUtils.addExecutionBreakDownRecord(getID(),
         ExecutionBreakDownRecord.UI_PROCESSING_CYCLE, args -> {
            refreshSelectionValue0(data);
         });

      //refreshSelectionValue0(data);
   }

   /**
    * Refresh the selection value.
    * @param data the specified input data.
    */
   private void refreshSelectionValue0(Object data) throws Exception {
      TimeSliderVSAssembly assembly = (TimeSliderVSAssembly) getAssembly();

      if(data == null || assembly == null) {
         return;
      }

      TimeInfo tinfo = assembly.getTimeInfo();

      //by @nickgovus 2023-10-31, set total to actual total if not exiting metadata where total = 1, else set notify as metadata
      int total = assembly.getTotalLength() > 1 ? assembly.getTotalLength() : 2;

      int curr = assembly.getCurrentPos();
      int length = tinfo.getLength();
      int hint = NORMAL;
      boolean all = length >= total - 1 && curr == 0;

      // preserve all
      if(all) {
         hint = ALL;
      }
      else {
         boolean first_n = curr == 0 && length > 0 && length < total;

         // preserve first n
         if(first_n) {
            hint = FIRST_N;
         }
         else {
            boolean last_n = curr >= 0 && length > 0 && length < total &&
               curr + length + 1 == total;

            // preserve last n
            if(last_n) {
               hint = LAST_N;
            }
         }
      }
      //by @nickgovus 2023-10-31, prevent position overwriting in metadata
      if(assembly.getTotalLength() == 1 && total > 1 && curr == 0) {
         hint=NORMAL;
      }

      if(tinfo instanceof SingleTimeInfo) {
         refreshSingleSelectionValue(data, hint);
      }

      else {
         refreshCompositeSelectionValue((XTable) data, hint);
      }
   }

   /**
    * Refresh the selection value for single time info.
    * @param varData the specified data, Array or XTable.
    */
   private void refreshSingleSelectionValue(Object varData, int hint)
         throws Exception
   {
      TimeSliderVSAssembly assembly = (TimeSliderVSAssembly) getAssembly();
      TimeSliderSelection sliderSelection = ((TimeSliderVSAssemblyInfo) assembly.getInfo()).getTimeSliderSelection();
      Format dfmt = assembly.getDefaultFormat();
      boolean excludeRight = !assembly.isUpperInclusive();
      SingleTimeInfo tinfo = (SingleTimeInfo) assembly.getTimeInfo();
      int length = tinfo.getLength();
      double maxsize = tinfo.getMaxRangeSize();
      double rsize0 = tinfo.getRangeSize();
      rsize0 = Double.isNaN(rsize0) ? 0 : rsize0;
      boolean rsizeDefined = rsize0 > 0;
      // maximum number of ticks
      int maxlength = (maxsize > 0 && rsize0 > 0)
         ? (int) Math.max(1, maxsize / rsize0) : Integer.MAX_VALUE;

      if(tinfo.isDateTime()) {
         tinfo.setRangeSize(1); // range size is 1 for dates
      }

      if(varData instanceof XTable) {
         refreshDimensionsValue((XTable) varData, hint,
                                new DataRef[] {tinfo.getDataRef()}, excludeRight);
         length = tinfo.getLength();

         if(!getViewsheet().getViewsheetInfo().isMetadata()) {
            tinfo.setLengthValue(Math.max(1, Math.min(length, maxlength)));
         }

         return;
      }

      Object[] data = (Object[]) varData;
      VSCompositeFormat vsfmt = assembly.getVSAssemblyInfo().getFormat();
      Format fmt = null;

      if(vsfmt != null) {
         fmt = TableFormat.getFormat(vsfmt.getFormat(), vsfmt.getFormatExtent());
      }

      fmt = fmt == null ? dfmt : fmt;

      if(data == null) {
         assembly.setSelectionList(null);
         assembly.setStateSelectionList(null);
         return;
      }

      DataRef ref = tinfo.getDataRef();
      String dtype = ref == null || !ref.isDataTypeSet() ? null : ref.getDataType();
      Object min = fixValue(data[0], dtype);
      Object max = fixValue(data[1], dtype);
      Object omax = data.length == 3 ? fixValue(data[2], dtype) : null;
      Object minObj = assembly.getRuntimeMin();

      if(minObj == null) {
         minObj = assembly.getSelectedMin();
      }

      int unit = tinfo.getRangeType();
      int olength = length; // old length
      SelectionList slist = new SelectionList();
      int counter = 0;
      int pos = -1;
      int[] dateLevels = {};

      if(unit == TimeInfo.NUMBER && (!(min instanceof Number) || !(max instanceof Number))) {
         if(getViewsheet().getViewsheetInfo().isMetadata()) {
            min = 0;
            max = 100;
         }
         else {
            throw new MessageException("Numeric value is required!", LogLevel.INFO, false);
         }
      }

      if(unit == TimeInfo.NUMBER && assembly.isLogScale()) {
         double mind = ((Number) min).doubleValue();
         double maxd = ((Number) max).doubleValue();
         double[] ticks = TimeSliderVSAssembly.getPreferredTicks(mind, maxd,
            -1, excludeRight, assembly.isLogScale(), Double.NaN);
         double curr = (minObj == null) ? ticks[0] :
            ((Number) minObj).doubleValue();
         int inc = TimeSliderVSAssembly.getLogIncrment(mind, maxd);
         curr = TimeSliderVSAssembly.roundToPow(curr, inc, false);

         for(double tick : ticks) {
            if(pos == -1 && curr == tick) {
               pos = counter;
            }

            String val = Tool.toString(tick);
            String label = (fmt != null) ? fmt.format(tick) : Tool.toString(tick);

            SelectionValue sval = new SelectionValue(label, val);
            sval.setLevel(0);
            slist.addSelectionValue(sval);
            counter++;
         }

         maxlength = Integer.MAX_VALUE; // not supported for log
      }
      else if(unit == TimeInfo.NUMBER) {
         RangeMVColumn rangecol = null;
         MVDef def = !box.isMVEnabled() ? null :
            MVManager.getManager().findMV(box.getAssetEntry(),
                                          assembly.getTableName(),
                                          (XPrincipal) box.getUser(), null,
                                          box.getParentVsIds());

         if(def != null) {
            def = def.update();
            ColumnRef column = (ColumnRef) tinfo.getDataRef();
            TableAssembly tassembly = getWSTableAssembly();

            if(tassembly == null) {
               throw new RuntimeException("Base work sheet not found: ");
            }

            ColumnSelection columns = tassembly.getColumnSelection(false);
            column = VSUtil.getVSColumnRef(column);
            column = (ColumnRef) columns.getAttribute(column.getName());
            boolean newCalc = false;

            if(column == null) {
               Catalog log = Catalog.getCatalog();
               TableAssembly temptable = (TableAssembly) tassembly.clone();
               appendDetailCalcField(temptable, assembly.getTableName());
               ColumnSelection cols = temptable.getColumnSelection();
               column = (ColumnRef) tinfo.getDataRef();
               column = VSUtil.getVSColumnRef(column);
               column = (ColumnRef) cols.getAttribute(column.getName());

               if(column != null) {
                  newCalc = true;
                  LOG.debug("Added calculated field which did not exist in " +
                            "materialized view: " + tinfo.getDataRef());
               }
               else {
                  LOG.warn("Column not found: " + tinfo.getDataRef());
                  throw new ColumnNotFoundException(
                     log.getString("common.invalidTableColumn", tinfo.getDataRef()));
               }
            }

            if(!newCalc) {
               String rattr = column.getAttribute();
               String rname = RangeMVColumn.getRangeName(rattr);
               MVColumn mvcol = def.getColumn(rname, true, true);

               if(mvcol instanceof RangeMVColumn) {
                  rangecol = (RangeMVColumn) mvcol;

                  if(rangecol.isLogScale() != assembly.isLogScale()) {
                     rangecol = null;
                  }
               }
            }
         }

         int tickN = 50; // default value
         boolean init = rsize0 == 0;
         double mind = ((Number) min).doubleValue();
         double maxd = ((Number) max).doubleValue();
         double curr = (minObj == null) ? mind : ((Number) minObj).doubleValue();

         if(rangecol != null && rsize0 != 0) {
            Number gap = rangecol.getInterval();
            gap = gap == null ? 1 : gap;
            mind = rangecol.getMin().doubleValue();
            maxd = rangecol.getMax().doubleValue();

            if(mind > maxd) {
               mind = maxd;
            }

            String str = Tool.toString(gap);
            int dot = str.indexOf('.');
            int dp = str.length() - dot - 1;
            double factor = Math.pow(10, dp);
            long ngap = Math.round(gap.doubleValue() * factor);
            long nrsize = Math.round(rsize0 * factor);
            rsize0 = ngap == 0 ? rsize0 : (nrsize / ngap) * gap.doubleValue();

            if(rsize0 != 0) {
               if(ngap != 0) {
                  tickN = rangecol.getTickCount() / ((int) (nrsize / ngap));
               }
            }
            else {
               rsize0 = gap.doubleValue();
            }

            if(rsize0 > tinfo.getRangeSizeValue()) {
               tinfo.setRangeSize(rsize0);
            }
            else {
               rsize0 = tinfo.getRangeSize();
            }
         }
         else {
            int tickCount = tickN;

            if(rsize0 != 0) {
               tickCount = (int) Math.ceil(Math.min((maxd - mind) / rsize0, 200));
               tickCount = Math.max(3, tickCount);
            }

            double[] numbers = TimeSliderVSAssembly.getNiceNumbers(mind, maxd, tickCount);

            if(maxd > mind) {
               mind = numbers[0];
               maxd = numbers[1];

               rsize0 = numbers[2];
            }

            // modified so it resize better (only if not explicitly defined)
            if(!rsizeDefined && rsize0 < 1 && maxd - mind >= 2) {
               tickN = (int) Math.ceil(maxd - mind);
               numbers = TimeSliderVSAssembly.getNiceNumbers(mind, maxd, tickN);
               mind = numbers[0];
               maxd = numbers[1];
               rsize0 = numbers[2];
            }

            if(rsize0 > tinfo.getRangeSizeValue()) {
               if(init) {
                  tinfo.setRangeSizeValue(rsize0);
               }
               else {
                  tinfo.setRangeSize(rsize0);
               }
            }
            else {
               rsize0 = tinfo.getRangeSize();
            }
         }

         // defaults to select 20% of the range
         if(init) {
            length = (int) ((maxd - mind) * 0.2 / (rsize0));

            if(!getViewsheet().getViewsheetInfo().isMetadata()) {
               tinfo.setLengthValue(Math.max(1, length));
            }
         }

         if(maxsize > 0 && length * rsize0 > maxsize) {
            maxlength = (int) Math.ceil(maxsize / rsize0);
         }

         long rsize = (long) rsize0;
         double factor = 1;

         // handle decimal ranges
         if(!GTool.isInteger(rsize0)) {
            String str = Tool.toString(rsize0);
            int dot = str.indexOf('.');
            int dp = str.length() - dot - 1;
            factor = Math.pow(10, dp);
            rsize = Math.round(rsize0 * factor);
            curr = Math.round(curr * factor);
         }

         double[] ticks = TimeSliderVSAssembly.getPreferredTicks(mind, maxd,
            tickN, excludeRight, assembly.isLogScale(), rsize0);

         if(maxd <= mind) {
            ticks = new double[] {mind};
         }

         long currV = rsize != 0 ? ((long) (curr - ticks[0] * factor) / rsize) : -1;

         sliderSelection.setIncrement(rsize0);
         sliderSelection.setLabelFormat(fmt);

         for(double tick : ticks) {
            if(rsize != 0 && pos == -1 &&
               Math.round((tick - ticks[0]) * factor) / rsize == currV)
            {
               pos = counter;
            }

            String val = Tool.toString(tick);
            String label = (fmt != null && !(fmt instanceof DateFormat))
               ? fmt.format(tick) : Tool.toString(tick);
            SelectionValue sval = new SelectionValue(label, val);
            sval.setLevel(0);
            slist.addSelectionValue(sval);
            counter++;
         }
      }
      else if(min instanceof Date && max instanceof Date) {
         Date mind = (Date) min;
         Date maxd = (Date) max;
         Date omaxd = (Date) omax;
         int incr = 1;

         int[][] limits = {
            {TimeInfo.DAY, 1000 * 60 * 60 * 24},
            {TimeInfo.HOUR, 1000 * 60 * 60},
            {TimeInfo.MINUTE, 1000 * 60}
         };
         final double MAX_TICKS = 1500;

         // make sure we don't have too many ticks
         for(int[] limit : limits) {
            if(unit == limit[0]) {
               long n = (maxd.getTime() - mind.getTime()) / limit[1];

               if(n > MAX_TICKS) {
                  incr = (int) Math.ceil(n / MAX_TICKS);
               }

               break;
            }
         }

         Locale local = Catalog.getCatalog().getLocale();
         local = local == null ? Locale.getDefault() : local;
         fmt = fmt instanceof DurationFormat ? null : fmt;

         if(unit == TimeInfo.MONTH) {
            dateLevels = new int[] {Calendar.MONTH, Calendar.YEAR};
            pos = createSelectionList(
               mind, maxd, omaxd, slist,
               (fmt instanceof DateFormat) ? fmt
                  : Tool.createDateFormat(TimeSliderVSAssembly.LABEL_MONTH_PATTERN, local),
               TimeSliderVSAssembly.VALUE_MONTH_FORMAT.get(), sliderSelection, excludeRight, incr,
               (Date) minObj, dateLevels);
         }
         else if(unit == TimeInfo.DAY) {
            dateLevels = new int[] {Calendar.DATE, Calendar.MONTH, Calendar.YEAR};
            pos = createSelectionList(
               mind, maxd, omaxd, slist,
               (fmt instanceof DateFormat) ? fmt
                  : Tool.createDateFormat(TimeSliderVSAssembly.LABEL_DAY_PATTERN, local),
               TimeSliderVSAssembly.VALUE_DAY_FORMAT.get(), sliderSelection, excludeRight, incr,
               (Date) minObj, dateLevels);
         }
         else if(unit == TimeInfo.HOUR) {
            dateLevels = new int[] {Calendar.HOUR_OF_DAY, Calendar.DATE,
                                    Calendar.MONTH, Calendar.YEAR};
            pos = createSelectionList(
               mind, maxd, omaxd, slist,
               (fmt instanceof DateFormat) ? fmt
                  : Tool.createDateFormat(TimeSliderVSAssembly.LABEL_HOUR_PATTERN, local),
               TimeSliderVSAssembly.VALUE_HOUR_FORMAT.get(), sliderSelection, excludeRight, incr,
               (Date) minObj, dateLevels);
         }
         else if(unit == TimeInfo.MINUTE) {
            dateLevels = new int[] {Calendar.MINUTE, Calendar.HOUR_OF_DAY, Calendar.DATE,
                                    Calendar.MONTH, Calendar.YEAR};
            pos = createSelectionList(
               mind, maxd, omaxd, slist,
               (fmt instanceof DateFormat) ? fmt
                  : Tool.createDateFormat(TimeSliderVSAssembly.LABEL_MINUTE_PATTERN, local),
               TimeSliderVSAssembly.VALUE_MINUTE_FORMAT.get(), sliderSelection, excludeRight, incr,
               (Date) minObj, dateLevels);
         }
         else if(unit == TimeInfo.HOUR_OF_DAY) {
            dateLevels = new int[] {Calendar.HOUR_OF_DAY, Calendar.DAY_OF_YEAR};
            // including day in the levels to handle the case where the
            // max overflows into the next day, e.g. Jan 2nd, 00:01:00
            pos = createSelectionList(
               mind, maxd, omaxd, slist,
               (fmt instanceof DateFormat) ? fmt
                  : Tool.createDateFormat(TimeSliderVSAssembly.LABEL_HOUR_OF_DAY_PATTERN, local),
               TimeSliderVSAssembly.VALUE_HOUR_OF_DAY_FORMAT.get(), sliderSelection, excludeRight,
               incr, (Date) minObj, dateLevels);
         }
         else if(unit == TimeInfo.MINUTE_OF_DAY) {
            dateLevels = new int[] {Calendar.MINUTE, Calendar.HOUR_OF_DAY, Calendar.DAY_OF_YEAR};
            // including day in the levels to handle the case where the
            // max overflows into the next day,
            // e.g. omaxd is Jan 1st 23:59:59 and maxd is Jan 2nd, 00:00:59
            pos = createSelectionList(
               mind, maxd, omaxd, slist,
               (fmt instanceof DateFormat) ? fmt
                  : Tool.createDateFormat(TimeSliderVSAssembly.LABEL_MINUTE_OF_DAY_PATTERN, local),
               TimeSliderVSAssembly.VALUE_MINUTE_OF_DAY_FORMAT.get(), sliderSelection, excludeRight,
               incr, (Date) minObj, dateLevels);
         }
         else {
            dateLevels = new int[] {Calendar.YEAR};
            pos = createSelectionList(
               mind, maxd, omaxd, slist,
               (fmt instanceof DateFormat) ? fmt
                  : Tool.createDateFormat(TimeSliderVSAssembly.LABEL_YEAR_PATTERN, local),
               TimeSliderVSAssembly.VALUE_YEAR_FORMAT.get(), sliderSelection, excludeRight, incr,
               (Date) minObj, dateLevels);
         }
      }

      SelectionList slist2 = new SelectionList();
      boolean runtimeMode = box.getMode() == RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE;

      // if script set max, calculate the length by applying the max
      if(assembly.getRuntimeMax() instanceof Date) {
         Calendar maxDate = new GregorianCalendar();
         Calendar cal = new GregorianCalendar();
         maxDate.setTime((Date) assembly.getRuntimeMax());

         // if min is not set, select from start
         if(assembly.getRuntimeMin() == null) {
         pos = 0;
         }

         for(int k = 0; k < slist.getSelectionValueCount(); k++) {
            cal.setTime(AssetUtil.getDate(AssetUtil.RANGE_SLIDER_START, slist.getSelectionValue(k).getValue()));

            if(compareLevel(cal, maxDate, dateLevels) == 0) {
               length = k + 1 - pos;
            }
         }
         tinfo.setLengthValue(Math.max(1, length));
      }
      else if((hint & ALL) == ALL) {
         pos = 0;
         tinfo.setLengthValue(Math.max(1, length = slist.getSelectionValueCount() - 1));
      }
      else if((hint & FIRST_N) == FIRST_N) {
         length = Math.min(slist.getSelectionValueCount() - 1, olength);
         pos = 0;

         if(runtimeMode || !getViewsheet().getViewsheetInfo().isMetadata()) {
            tinfo.setLengthValue(Math.max(1, length));
         }
      }
      else if((hint & LAST_N) == LAST_N) {
         length = Math.min(slist.getSelectionValueCount() - 1, olength);
         length = Math.min(length, maxlength);
         pos = slist.getSelectionValueCount() - 1 - length;

         if(runtimeMode || !getViewsheet().getViewsheetInfo().isMetadata()) {
            tinfo.setLengthValue(Math.max(1, length));
         }
      }
      // if script set min but not max, select the min to end
      else if(assembly.getRuntimeMin() != null && assembly.getRuntimeMax() == null) {
         length = slist.getSelectionValueCount() - pos - 1;
         tinfo.setLengthValue(Math.max(1, length));
      }

      if(pos < 0) {
         //fix bug1295840324493, when the position is -1, select all range value
         // and clear all state selection

         if(!getViewsheet().getViewsheetInfo().isMetadata()) {
            LOG.warn("Invalid position, clearing all state selection");
            tinfo.setLengthValue(Math.max(1, length = slist.getSelectionValueCount() - 1));
         }
      }
      // make sure the length doesn't exceed the max (shared filter)
      else {
         length = Math.min(length, slist.getSelectionValueCount() - 1 - pos);
      }

      if(runtimeMode || !getViewsheet().getViewsheetInfo().isMetadata()) {
         tinfo.setLengthValue(length = Math.max(1, Math.min(length, maxlength)));
      }

      if(pos >= 0) {
         int cnt = pos + length + 1;
         cnt = Math.min(slist.getSelectionValueCount(), cnt);

         for(int i = pos; i < cnt; i++) {
            SelectionValue value = slist.getSelectionValue(i);
            value.setState(SelectionValue.STATE_SELECTED);
            slist2.addSelectionValue(value);
         }
      }

      assembly.setSelectionList(slist);

      if(runtimeMode || !getViewsheet().getViewsheetInfo().isMetadata()) {
         assembly.setStateSelectionList(slist2);
         slist2.complete();
      }

      slist.complete();
   }

   /**
    * fix time value to ignore the year month.
    * @param value
    * @return
    */
   private Object fixValue(Object value, String dtype) {
      if(value instanceof Time || value instanceof Date && XSchema.TIME.equals(dtype)) {
         Calendar cal = new GregorianCalendar();
         cal.setTime((Date) value);
         cal.set(Calendar.YEAR, 1970);
         cal.set(Calendar.MONTH, Calendar.JANUARY);
         cal.set(Calendar.DAY_OF_MONTH, 1);
         return new Time(cal.getTimeInMillis());
      }

      return Tool.getData(dtype, value);
   }

   /**
    * Create the selection list for date.
    * @param mind minimum date.
    * @param maxd maximum date.
    * @param omaxd original maximun date.
    * @param slist selection value list.
    * @param labelfmt label format.
    * @param valuefmt value format.
    * @param excludeRight exclusive right range.
    * @param incr increment at the date level for each tick.
    * @param curr current value.
    * @param datelevels date levels to create date values.
    * @return the index of the current date in the value list.
    */
   private int createSelectionList(Date mind, Date maxd, Date omaxd,
                                   SelectionList slist, Format labelfmt,
                                   Format valuefmt, TimeSliderSelection sliderSelection,
                                   boolean excludeRight, int incr, Date curr, int... datelevels) {
      Calendar calendar = new GregorianCalendar();
      Calendar maxcal = new GregorianCalendar();
      Calendar omaxcal = null;
      Calendar currcal = null;
      int counter = 0;
      int pos = -1;

      sliderSelection.setLabelFormat(labelfmt);
      sliderSelection.setValueFormat(valuefmt);
      sliderSelection.setIncrement(incr);
      sliderSelection.setDateLevels(datelevels);

      maxcal.setTime(maxd);

      if(excludeRight) {
         maxcal.add(datelevels[0], 1);
      }

      calendar.setTime(mind);

      if(curr != null) {
         currcal = new GregorianCalendar();
         currcal.setTime(curr);
      }

      if(omaxd != null) {
         omaxcal = new GregorianCalendar();
         omaxcal.setTime(omaxd);
      }

      while(compareLevel(calendar, maxcal, datelevels) < 0) {
         if(pos == -1 && currcal != null) {
            if(compareLevel(calendar, currcal, datelevels) == 0) {
               pos = counter;
            }
         }

         Object obj = calendar.getTime();
         String val = valuefmt.format(obj);
         String label = null;

         try {
            // parse the value so if interval is month, the value 1/15/2020 would be
            // {m '2020-01-15'}, and parsed back to 1/1/2020. this matches the actual
            // condition for the range. otherwise the label on the slider may be different
            // from the condition.
            label = labelfmt.format(valuefmt.parseObject(val));
         }
         catch(ParseException ex) {
            label = labelfmt.format(obj);
         }

         SelectionValue sval = new SelectionValue(label, val);

         sval.setLevel(0);
         slist.addSelectionValue(sval);
         calendar.add(datelevels[0], incr);

         counter++;
      }

      if(omaxcal != null) {
         calendar.add(datelevels[0], -incr);
         // make the max in range always >= the max in the data
         if(compareLevel(calendar, omaxcal, datelevels) < 0) {
            calendar.add(datelevels[0], incr);
            Object obj = calendar.getTime();
            String label = labelfmt.format(obj);
            String val = valuefmt.format(obj);
            SelectionValue sval = new SelectionValue(label, val);

            sval.setLevel(0);
            slist.addSelectionValue(sval);
         }
      }

      return pos;
   }

   /**
    * Compare two dates at the specified levels.
    * @return -1 if cal is less, 0 if equal, or 1 if greater than cal2.
    */
   private int compareLevel(Calendar cal1, Calendar cal2,
                            int... datelevels)
   {
      // date levels are arranged from low to high
      for(int i = datelevels.length - 1; i >= 0; i--) {
         int level = datelevels[i];
         int rc = cal1.get(level) - cal2.get(level);

         if(rc != 0) {
            return rc;
         }
      }

      return 0;
   }

   /**
    * Refresh the selection value for composite time info.
    * @param data the specified data.
    */
   private void refreshCompositeSelectionValue(XTable data, int hint) {
      TimeSliderVSAssembly assembly = (TimeSliderVSAssembly) getAssembly();
      CompositeTimeInfo cinfo = (CompositeTimeInfo) assembly.getTimeInfo();
      DataRef[] refs = cinfo.getDataRefs();
      refreshDimensionsValue(data, hint, refs, !assembly.isUpperInclusive());
   }

   /**
    * Refresh the values for dimension.
    */
   private void refreshDimensionsValue(XTable data, int hint, DataRef[] refs, boolean excludeRight)
   {
      TimeSliderVSAssembly assembly = (TimeSliderVSAssembly) getAssembly();
      SelectionList slist = new SelectionList();
      TimeInfo tinfo = assembly.getTimeInfo();
      VSCompositeFormat vsfmt = assembly.getVSAssemblyInfo().getFormat();
      Format fmt = TableFormat.getFormat(vsfmt.getFormat(),
                                         vsfmt.getFormatExtent());
      int length = tinfo.getLength();
      int olength = length;
      int pos = -1;
      int curr = assembly.getCurrentPos();
      int counter = 0;
      Object mobj = assembly.getSelectedMin();
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(data, true);

      for(int i = 1; data.moreRows(i); i++) {
         StringBuilder label = new StringBuilder();
         StringBuilder vstr = new StringBuilder();
         StringBuilder caption = new StringBuilder();
         Object[] objs = new Object[refs.length];
         MemberObject memberObj = null;

         for(int idx = 0; idx < refs.length; idx++) {
            int j = Util.findColumn(data, refs[idx], columnIndexMap);

            if(j == -1) {
               continue;
            }

            if(vstr.length() > 0) {
               vstr.append("::");
               caption.append("::");
               label.append(",");
            }

            Object obj = data instanceof MemberObjectTableLens ?
               ((MemberObjectTableLens) data).getMemberObject(i, j) :
               data.getObject(i, j);

            if(refs[idx].getRefType() == DataRef.CUBE_MODEL_DIMENSION &&
               refs[idx] instanceof ColumnRef && obj != null)
            {
               String dtype = Tool.getDataType(obj.getClass());
               ((ColumnRef) refs[idx]).setDataType(dtype);
            }

            String str = obj == null ? CoreTool.FAKE_NULL : AbstractCondition.getValueString(obj,
               refs[idx].getDataType(), false);
            objs[idx] = obj;

            if(obj instanceof CubeDate && ((CubeDate) obj).getMemberObject() != null) {
               str = ((CubeDate) obj).getMemberObject().getCaption();
            }

            vstr.append(str);

            if(obj instanceof MemberObject) {
               memberObj = (MemberObject) obj;
            }

            caption.append(memberObj != null ? memberObj.getFullCaption() : str);
            String lbl = data.getObject(i, j) == null ?
                    "" : Tool.getDataString(data.getObject(i, j));

            // not set format, use default format for each column ref
            if(fmt == null && data instanceof TableLens) {
               Format dfmt = ((TableLens) data).getDefaultFormat(i, j);

               if(dfmt != null && obj != null) {
                     try {
                        lbl = dfmt.format(data.getObject(i, j));
                     }
                     catch(Exception ex) {
                        LOG.info("Failed to format label value: " + obj, ex);
                     }
               }
            }

            label.append(lbl);
         }

         if(vstr.length() == 0 && memberObj == null) {
            continue;
         }

         if(vstr.toString().equals(mobj) || caption.toString().equals(mobj) ||
           memberObj != null && memberObj.isIdentity(mobj))
         {
            pos = counter;
         }

         if(MessageFormat.isMessageFormat(fmt)) {
            label = new StringBuilder(fmt.format(objs));
         }

         SelectionValue sval = new SelectionValue(label.toString(), vstr.toString());
         sval.setLevel(0);
         slist.addSelectionValue(sval);
         counter++;
      }

      if(excludeRight) {
         SelectionValue sval = new SelectionValue.UpperExclusiveEndValue();
         sval.setLevel(0);
         slist.addSelectionValue(sval);
      }

      SelectionList slist2 = new SelectionList();

      if((hint & ALL) == ALL) {
         pos = 0;

         if(!getViewsheet().getViewsheetInfo().isMetadata()) {
            tinfo.setLengthValue(Math.max(1, length = slist.getSelectionValueCount() - 1));
         }
      }
      else if((hint & FIRST_N) == FIRST_N) {
         length = Math.min(slist.getSelectionValueCount() - 1, olength);
         pos = 0;

         if(!getViewsheet().getViewsheetInfo().isMetadata()) {
            tinfo.setLengthValue(Math.max(1, length));
         }
      }
      else if((hint & LAST_N) == LAST_N) {
         length = Math.min(slist.getSelectionValueCount() - 1, olength);
         pos = slist.getSelectionValueCount() - 1 - length;

         if(!getViewsheet().getViewsheetInfo().isMetadata()) {
            tinfo.setLengthValue(Math.max(1, length));
         }
      }
      // @by davyc, when data changed, we may not found the pos,
      // now set it to first, same as TimeSliderVSAssemblyInfo.getCurrentPos
      // fix bug1397117774401
      else if(pos < 0 && curr > 0) {
         pos = 0;
      }

      // make sure the range doesn't exceed max
      if(pos + length >= slist.getSelectionValueCount()) {
         pos = Math.max(0, slist.getSelectionValueCount() - 1 - length);
         length = Math.min(length, slist.getSelectionValueCount() - 1);

         if(!getViewsheet().getViewsheetInfo().isMetadata()) {
            tinfo.setLengthValue(Math.max(1, length));
         }
      }

      if(pos >= 0) {
         int cnt = excludeRight ? pos + length : pos + length + 1;
         cnt = Math.min(slist.getSelectionValueCount(), cnt);

         for(int i = pos; i < cnt; i++) {
            SelectionValue value = slist.getSelectionValue(i);
            value.setState(SelectionValue.STATE_SELECTED);
            slist2.addSelectionValue(value);
         }
      }

      assembly.setSelectionList(slist);

      if(!getViewsheet().getViewsheetInfo().isMetadata()) {
         assembly.setStateSelectionList(slist2);
      }
   }

   /**
    * Get base ws table assembly.
    */
   private TableAssembly getWSTableAssembly() throws Exception {
      Worksheet ws = getWorksheet();

      if(ws == null) {
         return null;
      }

      TimeSliderVSAssembly assembly = (TimeSliderVSAssembly) getAssembly();
      String tname = assembly.getSelectionTableName();
      addCalcToSelectionTable(ws);
      TableAssembly tassembly = getVSTableAssembly(tname);

      if(tassembly == null) {
         return null;
      }

      tassembly = box.getBoundTable(tassembly, vname, isDetail());
      normalizeTable(tassembly);
      ws.addAssembly(tassembly);
      TableAssembly innerTable = tassembly;

      if(tassembly instanceof MirrorTableAssembly) {
         innerTable = ((MirrorTableAssembly) tassembly).getTableAssembly();
      }

      if(innerTable instanceof CubeTableAssembly) {
         innerTable.setProperty("noEmpty", "false");
      }

      return tassembly;
   }

   private static final int NORMAL = 0; // normal selection
   private static final int ALL = 1; // all selection
   private static final int FIRST_N = 2; // first n selection
   private static final int LAST_N = 4; // last n selection

   private static final Logger LOG = LoggerFactory.getLogger(TimeSliderVSAQuery.class);
}
