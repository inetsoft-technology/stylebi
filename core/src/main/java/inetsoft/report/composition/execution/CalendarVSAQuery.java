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

import inetsoft.mv.*;
import inetsoft.report.TableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * CalendarVSAQuery, the calendar viewsheet assembly query.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CalendarVSAQuery extends AbstractSelectionVSAQuery {
   /**
    * Create a calendar viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param vname the specified viewsheet assembly to be processed.
    */
   public CalendarVSAQuery(ViewsheetSandbox box, String vname) {
      super(box, vname);
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
    * @param odata the specified input data.
    */
   private void refreshSelectionValue0(Object odata) throws Exception {
      CalendarVSAssembly assembly = (CalendarVSAssembly) getAssembly();
      Object[] data = (Object[]) odata;

      if(data == null || data.length != 2) {
         assembly.setRange(null);
         return;
      }

      String[] range = new String[2];
      Calendar cal = new GregorianCalendar();

      for(int i = 0; i < data.length; i++) {
         cal.setTime((Date) data[i]);

         // month range is [0-11]
         String label = cal.get(Calendar.YEAR) + "-" +
            cal.get(Calendar.MONTH) + "-" + cal.get(Calendar.DAY_OF_MONTH);
         range[i] = label;
      }

      assembly.setRange(range);
   }

   /**
    * Get the table assembly that contains binding info.
    */
   private TableAssembly getTableAssembly() throws Exception {
      Worksheet ws = getWorksheet();

      if(ws == null) {
         return null;
      }

      CalendarVSAssembly assembly = (CalendarVSAssembly) getAssembly();
      String tname = assembly.getSelectionTableName();
      addCalcToSelectionTable(ws);
      TableAssembly tassembly = getVSTableAssembly(tname);

      if(tassembly == null) {
         return null;
      }

      tassembly = box.getBoundTable(tassembly, vname, isDetail());
      normalizeTable(tassembly);
      ws.addAssembly(tassembly);
      ColumnRef column = (ColumnRef) assembly.getDataRef();
      DataRef ref = column == null ? null : column.getDataRef();
      column = VSUtil.getVSColumnRef(column);
      ColumnSelection columns = tassembly.getColumnSelection(false);

      if(AssetUtil.isCubeTable(tassembly)) {
         column = (ColumnRef) columns.getAttribute(column.getName());

         if(column == null && ref != null) {
            column = (ColumnRef) columns.getAttribute(ref.getName());
         }

         if(column == null && ref instanceof AttributeRef) {
            column = (ColumnRef) columns.getAttribute(
               ((AttributeRef) ref).getCaption());
         }

         if(column == null) {
            LOG.warn(
               "Column not found in cube table assembly \"" + tname + "\": " +
               assembly.getDataRef());
            return null;
         }

         columns = new ColumnSelection();
         columns.addAttribute(column);
         int refType = column.getRefType();

         if(refType != DataRef.NONE) {
            SortInfo sortinfo = new SortInfo();
            SortRef sort = new SortRef(column);
            sort.setOrder(XConstants.SORT_ASC);
            sortinfo.addSort(sort);
            tassembly.setSortInfo(sortinfo);
         }

         TableAssembly cubeTable = AssetUtil.getBaseCubeTable(tassembly);
         cubeTable.setProperty("noEmpty", "false");
         tassembly.setColumnSelection(columns);
         return tassembly;
      }

      // replace with both max and min
      AggregateFormula min = AggregateFormula.MIN;
      AggregateFormula max = AggregateFormula.MAX;
      column = column == null ? null :
         (ColumnRef) columns.getAttribute(column.getName());

      if(column == null) {
         LOG.warn("Column not found in table assembly \"" + tname + "\": " +
            assembly.getDataRef());
         return null;
      }

      RuntimeMV rmv = tassembly.getRuntimeMV();

      // optimization
      // this causes the calendar range to be fixed (instead of depending on
      // the other selections). Not doing so requires 1-2 extra queries to
      // extract the associated range. That is quite expensive for a feature
      // that seems to be generally ignored by users. Also, this behavior
      // has ben for a long time (at least since 10.2)
      if(rmv != null) {
         MVDef def = MVManager.getManager().get(rmv.getMV());
         VariableTable vars = getAssetQuerySandbox().getVariableTable();

         if(def != null && !isDynamicFilter(tassembly, vars)) {
            def = def.update();
            DateMVColumn col = getDateMVColumn(column, def);

            if(col != null) {
               Date omax = col.getMax();
               Date omin = col.getMin();

               Object[][] arr = new Object[][] {{"max", "min"}, {omax, omin}};
               String[] types = {XSchema.TIME_INSTANT, XSchema.TIME_INSTANT};
               XEmbeddedTable table = new XEmbeddedTable(types, arr);
               DataTableAssembly dtable = new DataTableAssembly(ws, "RANGE_" + vname);
               dtable.setData(table);
               return dtable;
            }
         }
      }

      /* see TimeSliderVSAQuery.getTableAssembly()
      if(rmv != null && rmv.isPhysical()) {
         tassembly.setRuntimeMV(null);
      }
      */

      AggregateInfo info = new AggregateInfo();
      AliasDataRef maxexpr = new AliasDataRef(column.getAttribute() + "__MAX", column);
      maxexpr.setDataType(column.getDataType());
      ColumnRef maxcol = new ColumnRef(maxexpr);
      columns.addAttribute(maxcol);
      info.addAggregate(new AggregateRef(maxcol, null, max));

      AliasDataRef minexpr = new AliasDataRef(column.getAttribute() + "__MIN", column);
      minexpr.setDataType(column.getDataType());
      ColumnRef mincol = new ColumnRef(minexpr);
      columns.addAttribute(mincol);
      info.addAggregate(new AggregateRef(mincol, null, min));

      tassembly.setAggregateInfo(info);
      tassembly.setColumnSelection(columns);

      return tassembly;
   }

   public static DateMVColumn getDateMVColumn(ColumnRef column, MVDef def) {
      String rattr = column.getAttribute();
      // MV creates columns for year/month/week/day, with min/max rounded to the beginning
      // of intervals (e.g. 2023-01-01), so using year min/max will miss the days in the middle
      // of the year. (60240)
      int[] dlevels = { DateRangeRef.DAY_INTERVAL, DateRangeRef.MONTH_INTERVAL,
                        DateRangeRef.YEAR_INTERVAL };
      DateMVColumn col = null;

      for(int i = 0; i < dlevels.length && col == null; i++) {
         int doption = dlevels[i];
         String rname = DateMVColumn.getRangeName(rattr, doption);
         col = (DateMVColumn) def.getColumn(rname, false, true);

         if(col == null) {
            col = (DateMVColumn) def.getColumn(rname, true, true);
         }
      }

      return col;
   }

   /**
    * Get the data.
    * @return the data of the query.
    */
   @Override
   public Object getData() throws Exception {
      TableAssembly tassembly = getTableAssembly();

      if(tassembly == null) {
         return null;
      }

      TableLens table = getTableLens(tassembly);

      if(table == null) {
         return null;
      }

      boolean cubeDate = AssetUtil.isCubeTable(tassembly);
      table.moreRows(Integer.MAX_VALUE);
      checkMaxRowLimit(table);

      if(table.getRowCount() <= 1 || (!cubeDate && table.getColCount() < 2)) {
         return null;
      }

      Object max = table.getObject(table.getRowCount() - 1, 0);
      Object min = getMin(table, cubeDate);

      if(!(max instanceof Date) || !(min instanceof Date)) {
         return null;
      }

      return new Object[] {min, max};
   }

   private Object getMin(TableLens lens, boolean cubeDate) {
      if(cubeDate) {
         for(int i = 1; i < lens.getRowCount(); i++) {
            Object obj = lens.getObject(i, 0);

            if(obj != null) {
               return obj;
            }
         }
      }

      return lens.getObject(lens.getRowCount() - 1, 1);
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(CalendarVSAQuery.class);
}
