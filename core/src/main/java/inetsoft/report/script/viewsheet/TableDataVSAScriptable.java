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
package inetsoft.report.script.viewsheet;

import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.*;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.script.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.script.XTableRow;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.util.script.ArrayObject;
import inetsoft.util.script.JavaScriptEngine;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.text.Format;
import java.util.List;
import java.util.*;

/**
 * The table data viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class TableDataVSAScriptable extends DataVSAScriptable implements CompositeVSAScriptable {
   /**
    * Create a viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public TableDataVSAScriptable(ViewsheetSandbox box) {
      super(box);
      addFunctions();
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "TableDataVSA";
   }

   /**
    * Add user defined functions to the scope.
    */
   protected void addFunctions() {
      try {
         addFunctionProperty(getClass(), "setHyperlink", int.class, int.class, Object.class);
         addFunctionProperty(getClass(), "setPresenter", Object.class, Object.class, Object.class);
         addFunctionProperty(getClass(), "setColumnWidth", int.class, double.class);
         addFunctionProperty(getClass(), "setColumnWidthAll", int.class, double.class);
         addFunctionProperty(getClass(), "setRowHeight", int.class, double.class);

         addProperty("cellFormat", new TableArray2("Format", Format.class, false));
         addProperty("colFormat", new TableColumns2("Format", Format.class));
      }
      catch(Exception ex) {
         LOG.warn("Failed to register the table data properties and functions", ex);
      }
   }

   /**
    * Get the tablelens object.
    * @return the tablelens object.
    */
   public TableLens getTable() {
      return table;
   }

   /**
    * Set the view tablelens
    */
   public void setTable(TableLens lens) {
      if(lens instanceof TableFilter2) {
         lens = ((TableFilter2) lens).getTable();
      }

      if(lens != null && table != null && table.getTable() == lens) {
         return;
      }

      if(table != null) {
         table = (TableFilter2) table.clone();
         table.setTable(lens);
      }
      else {
         table = new TableFilter2(lens);
      }

      FormTableLens ftable = initFormTableLens();

      if(ftable != null) {
         tarr = new TableArray(ftable);
      }
      else {
         // find the AttributeTableLens in VSTableLens. see DataVSAQuery.getViewTableLens().
         while(lens instanceof VSFormatTableLens ||
            lens instanceof TableHighlightAttr.HighlightTableLens)
         {
            lens = ((AttributeTableLens) lens).getTable();
         }

         // if alias (column header) is applied, we should use the attribute table
         // for script so column header can be changed by script. (55938)
         // it's probably better to just use lens instead of calling getTable0(false)
         // for all cases. only use lens for AttributeTableLens to narrow the scope
         // of change and reduce the chance of backward compatibility problem.
         // may consider removing this and use lens in the future. (13.5)
         if(lens instanceof AttributeTableLens) {
            tarr = new TableArray(lens);
         }
         else {
            tarr = new TableArray(lens != null ? lens : getTable0(false));
         }
      }
   }

   /**
    * Get the cell value.
    * @return the cell value, <tt>NULL</tt> no cell value.
    */
   @Override
   public Object getCellValue() {
      XTable table = getTableArray().getTable();
      final int row = TableDataVSAScriptable.row.get();
      final int col = TableDataVSAScriptable.col.get();

      if(row >= 0) {
         table.moreRows(row);
      }

      if(table != null && col >= 0 && col < table.getColCount()) {
         if(row >= 0 && table.moreRows(row)) {
            return table.getObject(row, col);
         }
      }

      return NULL;
   }

   /**
    * Set the cell value.
    * @param val the specified cell value, <tt>NULL</tt> clear cell value.
    */
   @Override
   public void setCellValue(Object val) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Get the row value.
    * @return the row value.
    */
   public int getRow() {
      return row.get();
   }

   /**
    * Set the row value.
    * @param row the specified row.
    */
   public void setRow(int row) {
      TableDataVSAScriptable.row.set(row);
   }

   /**
    * Get the col value.
    * @return the col value.
    */
   public int getCol() {
      return col.get();
   }

   /**
    * Set the col value.
    * @param col the specfied col.
    */
   public void setCol(int col) {
      TableDataVSAScriptable.col.set(col);
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      if(!(getVSAssemblyInfo() instanceof TableDataVSAssemblyInfo)) {
         return Undefined.instance;
      }

      if(!has(name, start)) {
         return super.get(name, start);
      }

      if("value".equals(name)) {
         if(!initTableArray()) {
            return super.get(name, start);
         }

         Object cval = getCellValue();

         if(cval != NULL) {
            return cval;
         }
      }
      else if("field".equals(name)) {
         if(!initTableArray()) {
            return super.get(name, start);
         }

         int row = TableDataVSAScriptable.row.get();

         // default to the first row
         if(row == -1) {
            XTable table = getTableArray().getTable();

            if(table.moreRows(0)) {
               row = 0;
            }
         }

         return getTableArray().get(row, this);
      }
      else if("row".equals(name)) {
         int row = TableDataVSAScriptable.row.get();

         // make sure row/col is meaningful in calc cell formulas. (59015)
         if(row < 0) {
            row = CalcTableLens.getRow();
         }

         if(row >= 0) {
            return row;
         }
         else {
            if(!initTableArray()) {
               return super.get(name, start);
            }

            table.moreRows(Integer.MAX_VALUE);
            return table.getRowCount();
         }
      }
      else if("col".equals(name)) {
         int col = TableDataVSAScriptable.col.get();

         if(col < 0) {
            col = CalcTableLens.getCol();
         }

         if(col >= 0) {
            return col;
         }
         else {
            if(!initTableArray()) {
               return super.get(name, start);
            }

            return table.getColCount();
         }
      }
      else if("data".equals(name) || "table".equals(name)) {
         if(!initTableArray()) {
            return super.get(name, start);
         }

         if("table".equals(name)) {
            return getTableArray();
         }

         return getTableData();
      }
      else if("data.length".equals(name)) {
         if(!initTableArray()) {
            return super.get(name, start);
         }

         return getTableData().get("length", null);
      }
      else if("data.size".equals(name)) {
         if(!initTableArray()) {
            return super.get(name, start);
         }

         return getTableData().get("size", null);
      }
      else if("tablelens".equals(name)) {
         if(!initTableArray()) {
            return super.get(name, start);
         }

         if(table == null) {
            return new TableFilter2((TableLens) getTableArray().getTable());
         }

         return table;
      }
      else if("highlighted".equals(name)) {
         if(highlighted == null) {
            if(!initTableArray()) {
               return super.get(name, start);
            }

            // set highlighted to empty array to prevent infinite recursion
            highlighted = new TableHighlightedArray((XTable) null);
            // get the highted table (this could trigger another script)
            highlighted = new TableHighlightedArray(getTable0(true));
         }

         return highlighted;
      }
      else if("dataConditions".equals(name)) {
         return getTipConditions();
      }

      return super.get(name, start);
   }

   /**
    * Get table lens and initialize table array.
    * @return true if the table is available.
    */
   private boolean initTableArray() {
      // get table on-demand so the properties work in onLoad and other elems
      if(!recursive.get() || (table != null && table.isDirty())) {
         recursive.set(true);

         try {
            if(table == null || table.isDirty() || getTableArray().getTable() == null) {
               if(table != null) {
                  table.setIsDirty(false);
               }

               VSTableLens vstbl = getVSTable();

               if(vstbl != null) {
                  setTable(vstbl.getTable());
               }
            }
         }
         catch(Exception ex) {
            String msg = "Failed to get table for: " + getVSAssemblyInfo().getAbsoluteName();

            if(LOG.isDebugEnabled()) {
               LOG.debug(msg, ex);
            }
            else {
               LOG.error(msg + ex.getMessage());
            }
         }
         finally {
            recursive.remove();
         }
      }

      // query failed or binding is empty
      return table != null && getTableArray().getTable() != null;
   }

   /**
    * Add the assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) getVSAssemblyInfo();

      addProperty("wrapping", "isWrapping", "setWrapping", boolean.class, getClass(), this);

      addProperty("shrink", "isShrink", "setShrink", boolean.class, info.getClass(), info);
      addProperty("title", "getTitle", "setTitle", String.class, info.getClass(), info);
      addProperty("titleVisible", "isTitleVisible", "setTitleVisible",
                  boolean.class, info.getClass(), info);
      addProperty("tableStyle", "getTableStyle", "setTableStyle", String.class,
                  info.getClass(), info);
      addProperty("tipView", "getTipView", "setTipView", String.class, getClass(), this);
      addProperty("tipAlpha", "getAlpha", "setAlpha", String.class, info.getClass(), info);
      addProperty("flyoverViews", "getFlyoverViews", "setFlyoverViews",
                  String[].class, info.getClass(), info);
      addProperty("flyOnClick", "isFlyOnClick", "setFlyOnClick", boolean.class,
                  info.getClass(), info);
      addProperty("keepRowHeightOnPrint", "isKeepRowHeightOnPrint", "setKeepRowHeightOnPrint",
                  boolean.class, info.getClass(), info);

      addProperty("value", null);
      addProperty("field", null);
      addProperty("row", null);
      addProperty("col", null);
      addProperty("table", null);
      addProperty("tablelens", null);
      addProperty("data", null);
      addProperty("data.length", null);
      addProperty("data.size", null);
      addProperty("highlighted", null);
      addProperty("dataConditions", null);
   }

   /**
    * Set a hyperlink on a cell.
    */
   public void setHyperlink(int row, int col, Object link) {
      try {
         link = JavaScriptEngine.unwrap(link);

         if(link != null && !(link instanceof Hyperlink.Ref)) {
            link = PropertyDescriptor.convert(link, Hyperlink.Ref.class);

            if(link instanceof Hyperlink.Ref) {
               Hyperlink.Ref link0 = (Hyperlink.Ref) link;
               int type = link0.getLinkType();
               String newLink = link0.getLink();

               if(!newLink.startsWith("1^128^__NULL__^") &&
                  type == Hyperlink.VIEWSHEET_LINK)
               {
                  link0.setLink("1^128^__NULL__^" + newLink);
               }
            }
         }

         if(table == null) {
            table = new TableFilter2((TableLens) getTableArray().getTable());
         }

         table.setHyperlink(row, col, (Hyperlink.Ref) link);
      }
      catch(Exception ex) {
         String msg = "Failed to set hyperlink on cell [" + row + "," + col + "] to " + link;

         if(LOG.isDebugEnabled()) {
            LOG.debug(msg, ex);
         }
         else {
            LOG.error(msg + ex.getMessage());
         }
      }
   }

   public void setTipView(String tipView) {
      if(!isSupportFlyOverOrTipView()) {
         return;
      }

      getInfo().setTipOption(ChartVSAssemblyInfo.VIEWTIP_OPTION);
      getInfo().setTipView(tipView);
   }

   public void setTipViewValue(String tipView) {
      if(!isSupportFlyOverOrTipView()) {
         return;
      }

      getInfo().setTipOptionValue(ChartVSAssemblyInfo.VIEWTIP_OPTION);
      getInfo().setTipViewValue(tipView);
   }

   public String getTipView() {
      if(!isSupportFlyOverOrTipView()) {
         return null;
      }

      return getInfo().getTipView();
   }

   /**
    * Get the assembly info of current element.
    */
   private TableDataVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof TableDataVSAssemblyInfo) {
         return (TableDataVSAssemblyInfo) getVSAssemblyInfo();
      }

      return null;
   }

   public CalcTableVSAssemblyInfo getCalcInfo() {
      return (CalcTableVSAssemblyInfo) getInfo();
   }

   /**
    * Set column width.
    */
   public void setColumnWidth(int col, double n) {
      getInfo().setColumnWidth(col, n);
   }

   public void setColumnWidthAll(int col, double n) {
      getInfo().setColumnWidth2(col, n, getTable());
   }

   /**
    * Set row height.
    */
   public void setRowHeight(int row, double n) {
      if(getInfo() instanceof TableVSAssemblyInfo) {
         if(row >= 1) {
            row = 1;
         }
      }

      getInfo().setRowHeight(row, n);

      if(n < getInfo().getHeaderRowHeightsLength()) {
         getInfo().getHeaderRowHeights();
      }
   }

   /**
    * Set the size.
    * @param dim the dimension of size.
    */
   @Override
   public void setSize(Dimension dim) {
      TableDataVSAssemblyInfo info = getInfo();

      if(box.isRuntime()) {
         if(!(info instanceof CalcTableVSAssemblyInfo) &&
            !(info instanceof CrosstabVSAssemblyInfo))
         {
            Dimension odim = info.getPixelSize();
            XTable table = getTable0(false);

            // if dimension width is larger, need adjust the last col width.
            if(table != null && odim != null && dim.width > odim.width) {
               Viewsheet vs = box.getViewsheet();
               int lastCol = table.getColCount() - 1;
               double lastWidth = info.getColumnWidth(lastCol);

               if(Double.isNaN(lastWidth)) {
                  lastWidth = AssetUtil.defw;
               }

               info.setColumnWidth(lastCol,
                  lastWidth + dim.width - odim.width);
            }
         }

         super.setSize(dim);
      }
   }


   /**
    * Set a presenter on a cell.
    */
   public void setPresenter(Object p1, Object p2, Object p3) {
      int row = -1;
      int col = -1;
      String header = null;
      Object pobj;

      p1 = JavaScriptEngine.unwrap(p1);
      p2 = JavaScriptEngine.unwrap(p2);
      p3 = JavaScriptEngine.unwrap(p3);

      if(p1 instanceof Number && p2 instanceof Number) {
         row = ((Number) p1).intValue();
         col = ((Number) p2).intValue();
         pobj = p3;
      }
      else if(p1 instanceof Number) {
         col = ((Number) p1).intValue();
         pobj = p2;
      }
      else if(p1 instanceof String) {
         header = (String) p1;
         pobj = p2;
      }
      else {
         throw new RuntimeException("Invalid parameters for setPresenter: " +
                                    p1 + ", " + p2 + ", " + p3);
      }

      try {
         pobj = JavaScriptEngine.unwrap(pobj);

         if(pobj != null && !(pobj instanceof Presenter)) {
            pobj = PropertyDescriptor.convert(pobj, Presenter.class);
         }

         if(table == null) {
            table = new TableFilter2((TableLens) getTableArray().getTable());
         }

         if(header != null) {
            table.setPresenter(header, (Presenter) pobj);
         }
         else if(row >= 0 && col >= 0) {
            // in case of calc table, make sure the table is expanded before setting
            // presenter or the presenter will pick up the formula value instead of result
            table.getObject(row, col);

            table.setPresenter(row, col, (Presenter) pobj);
         }
         else {
            table.setPresenter(col, (Presenter) pobj);
         }
      }
      catch(Exception ex) {
         String msg = "Failed to set table presenter: [" + p1 + ", " + p2 + ", " + p3 + "]";

         if(LOG.isDebugEnabled()) {
            LOG.debug(msg, ex);
         }
         else {
            LOG.error(msg + ex.getMessage());
         }
      }
   }

   /**
    * Get the suffix of a property, may be "" or ().
    * @param prop the property.
    */
   @Override
   public String getSuffix(Object prop) {
      if("setHyperlink".equals(prop)) {
         return "()";
      }

      if("highlighted".equals(prop)) {
         return "";
      }

      if("data".equals(prop) || "cellFormat".equals(prop) ||
          "table".equals(prop) ) {
         return "[][]";
      }

      if("colFormat".equals(prop) || "highlighted".equals(prop) ||
         get(prop + "", this) instanceof ArrayObject)
      {
         return "[]";
      }

      if("setColumnWidth".equals(prop) || "setRowHeight".equals(prop)) {
         return "()";
      }

      return super.getSuffix(prop);
   }

   /**
    * Get the table data.
    * @param vslens true to get the outer-most table lens, false to
    * get the undecoreated lens.
    */
   private XTable getTable0(boolean vslens) {
      Viewsheet vs = box.getViewsheet();
      boolean reset = false;
      long ts = System.currentTimeMillis();

      if(!(vs.getAssembly(assembly) instanceof TableDataVSAssembly)) {
         LOG.warn("Table assembly is not found: " + assembly);
         return null;
      }

      try {
         // script call should not effect normal data process sequence
         if(VSAQuery.Q_CANCEL.get() != Boolean.FALSE) {
            VSAQuery.Q_CANCEL.set(Boolean.FALSE);
            reset = true;
         }

         return vslens ? box.getVSTableLens(assembly, false) : box.getTableData(assembly);
      }
      catch(Exception ex) {
         if(!box.isCancelled(ts)) {
            if(LOG.isDebugEnabled()) {
               LOG.debug("Failed to get table data", ex);
            }
            else {
               LOG.error("Failed to get table data: " + ex.getMessage());
            }
         }

         return null;
      }
      finally {
         if(reset) {
            VSAQuery.Q_CANCEL.remove();
         }
      }
   }

   // Filter the column selection after get table lens before summary filter.
   private TableLens getFilterBaseTable(TableLens result) {
      // The base has all columns in source, not filter binding columns. should filter by binding.
      TableLens base = null;
      SummaryFilter summary = (SummaryFilter)Util.getNestedTable(result, SummaryFilter.class);

      if(summary == null) {
         // If crosstab is merge group to db, it will not create summary filter, so using crosstab
         // to get base table, base table is aggregated data.
         CrossTabFilter cross = (CrossTabFilter)Util.getNestedTable(result, CrossTabFilter.class);

         if(cross == null) {
            return null;
         }

         base = cross.getTable();
      }
      else {
         base = summary.getTable();
      }

      Viewsheet vs = box.getViewsheet();
      VSAssembly table = vs.getAssembly(assembly);

      if(!(table instanceof TableDataVSAssembly)) {
         return null;
      }

      // For crosstab, filter column orders as order: row/col/aggregate.
      if(table instanceof CrosstabVSAssembly) {
         CrosstabVSAssembly crosstab = (CrosstabVSAssembly) table;
         VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
         return new ColumnMapFilter(base, getColMap(base, cinfo));
      }

      return null;
   }

   private int[] getColMap(TableLens base, VSCrosstabInfo cinfo) {
      DataRef[] rows = cinfo.getRowHeaders();
      DataRef[] cols = cinfo.getColHeaders();
      DataRef[] aggs = cinfo.getAggregates();
      List<Integer> list = new ArrayList<>();
      initColMap(base, rows, list);
      initColMap(base, cols, list);
      initColMap(base, aggs, list);

      int[] map = new int[list.size()];

      for(int i = 0; i < list.size(); i++) {
         map[i] = list.get(i);
      }

      return map;
   }

   private void initColMap(TableLens base, DataRef[] refs, List<Integer> list) {
      if(refs == null) {
         return;
      }

      for(int i = 0; i < refs.length; i++) {
         int idx = Util.findColumn(base, refs[i]);

         if(idx == -1) {
            idx = Util.findColumn(base, refs[i].getName());
         }

         if(idx == -1) {
            if(refs[i] instanceof VSDimensionRef) {
               idx = Util.findColumn(base, ((VSDimensionRef) refs[i]).getFullName());
            }
            else if(refs[i] instanceof VSAggregateRef) {
               idx = Util.findColumn(base, ((VSAggregateRef) refs[i]).getFullName());
            }
         }

         if(idx != -1) {
            list.add(idx);
         }
      }
   }

   /**
    * Get table from box.
    */
   private VSTableLens getVSTable() throws Exception {
      int times = 10;
      VSTableLens table = null;
      boolean reset = false;

      try {
         // script call should not effect normal data process sequence
         if(VSAQuery.Q_CANCEL.get() != Boolean.FALSE) {
            VSAQuery.Q_CANCEL.set(Boolean.FALSE);
            reset = true;
         }

         while(times > 0) {
            times--;

            try {
               table = box.getVSTableLens(
                  getVSAssemblyInfo().getAbsoluteName(), false);
               break;
            }
            catch(inetsoft.util.CancelledException cex) {
               if(times <= 0) {
                  throw cex;
               }

               // cancelled? try again
               try {
                  Thread.sleep(150);
               }
               catch(Exception ex) {
                  // ignore it
               }

               continue;
            }
         }
      }
      finally {
         if(reset) {
            VSAQuery.Q_CANCEL.remove();
         }
      }

      return table;
   }

   /**
    * Get FormTableLens.
    */
   private FormTableLens initFormTableLens() {
      Viewsheet vs = box.getViewsheet();

      if(!(vs.getAssembly(assembly) instanceof TableVSAssembly)) {
         return null;
      }

      if(!formIniting) {
         synchronized(this) {
            if(!formIniting) {
               formIniting = true;

               try {
                  ftable = box.getFormTableLens(assembly);
               }
               catch(Exception ex) {
                  if(LOG.isDebugEnabled()) {
                     LOG.debug("Failed to get form table lens", ex);
                  }
                  else {
                     LOG.error("Failed to get form table lens: " + ex.getMessage());
                  }
               }

               formIniting = false;
            }
         }
      }

      return ftable;
   }

   /**
    * Set the name of the viewsheet assembly.
    * @param assembly the name of the viewsheet assembly contained in this
    * scriptable.
    */
   @Override
   public void setAssembly(String assembly) {
      super.setAssembly(assembly);
      tarr = null;
      highlighted = null;
   }

   /**
    * Clear cache.
    */
   @Override
   public void clearCache() {
      clearCache(ALL);
   }

   /**
    * Clear cache.
    * @param type result type defined in DataMap.
    */
   @Override
   public void clearCache(int type) {
      if(table != null) {
         table.setIsDirty(true);
      }

      tarr = null;
      highlighted = null;

      if(assembly != null) {
         box.resetDataMap(assembly, type);
      }
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   public void setObject(int r, int c, Object v) {
      if(!canModifyForm()) {
         return;
      }

      FormTableLens ftable = initFormTableLens();

      if(ftable != null) {
         ftable.setObject(r, c, v);
         ftable.setLabel(r, c, Tool.toString(v));
         ftable.addChangedCell(r, c);
      }
      else if(table != null) {
         table.setObject(r, c, v);
      }
   }

   /**
    * Get form table row.
    */
   private FormTableRow getFormRow0(int r) {
      FormTableLens ftable = initFormTableLens();
      return ftable != null ? ftable.row(r) : null;
   }

   /**
    * Get form table row.
    */
   public TableFormRowScriptable getFormRow(int r) {
      if(disableForm) {
         return convertFormTableRow(new FormTableRow(0));
      }

      FormTableRow row = getFormRow0(r);
      TableFormRowScriptable scriptable = convertFormTableRow(row);
      return scriptable;
   }

   /**
    * Get the specified rows, if state equals to UNDEFINED, return all.
    * @param state the table row state.
    */
   public TableFormRowScriptable[] getFormRows(Object state) {
      if(disableForm) {
         return new TableFormRowScriptable[0];
      }

      FormTableLens ftable = initFormTableLens();

      if(ftable != null) {
         FormTableRow[] rows = null;

         if(state instanceof Undefined) {
            rows = ftable.rows();
         }
         else if(state instanceof Number) {
            rows = ftable.rows(((Number) state).intValue());
         }
         else if(state instanceof String) {
            rows = ftable.rows(getRowState((String) state));
         }

         return convertFormTableRows(rows);
      }

      return null;
   }

   /**
    * Get hide column value in specifical row.
    * @param col col name.
    * @param row row number.
    */
   public Object getHiddenColumnValue(String col, int row) {
      FormTableLens ftable = initFormTableLens();

      if(ftable != null) {
         ColumnMapFilter filter = (ColumnMapFilter)
            Util.getNestedTable(ftable, ColumnMapFilter.class);
         TableLens table = filter.getTable();
         TableVSAssemblyInfo info = (TableVSAssemblyInfo) getInfo();
         ColumnSelection columns = info.getColumnSelection();
         ColumnRef column = (ColumnRef) columns.getAttribute(col);

         if(column == null || column.isVisible()) {
            return null;
         }

         int c = AssetUtil.findColumn(table, column, false);

         if(c >= 0) {
            return table.getObject(row, c);
         }
      }

      return null;
   }

   /**
    * Append an empty row below the specfied row and change row state to added.
    * @param r row number.
    */
   public void appendRow(int r) {
      if(!canModifyForm()) {
         return;
      }

      FormTableLens ftable = initFormTableLens();

      if(ftable != null) {
         ftable.appendRow(r);
      }
   }

   /**
    * Insert an empty row above the specified row and change row state to added.
    * @param r row number.
    */
   public void insertRow(int r) {
      if(!canModifyForm()) {
         return;
      }

      FormTableLens ftable = initFormTableLens();

      if(ftable != null) {
         ftable.insertRow(r);
      }
   }

   /**
    * Delete the specified row.
    * @param r row number.
    */
   public void deleteRow(int r) {
      if(!canModifyForm()) {
         return;
      }

      FormTableLens ftable = initFormTableLens();

      if(ftable != null) {
         ftable.deleteRow(r);
      }
   }

   /**
    * Commit the specified state rows.
    * @param state the row state, if the state is a number , it indicates that
    * commint a specified row, if the state equals "undefined", it indicates
    * that commiting all.
    */
   public void commit(Object state) {
      if(!canModifyForm()) {
         return;
      }

      FormTableRow[] rows = null;
      FormTableLens ftable = initFormTableLens();

      if(state instanceof Undefined) {
         rows = ftable.rows();
         commitRows(rows);
      }
      else if(state instanceof String) {
         rows = ftable.rows(getRowState((String) state));
         commitRows(rows);
      }
      else if(state instanceof Number) {
         int stateVal = ((Number) state).intValue();

         if(stateVal >= 0) {
            FormTableRow row = getFormRow0(stateVal);

            if(row != null) {
               row.commit();
            }
         }
         else {
            rows = ftable.rows(((Number) state).intValue());
            commitRows(rows);
         }
      }
   }

   /**
    * Commit the specified rows.
    */
   private void commitRows(FormTableRow[] rows) {
      if(rows != null) {
         FormTableLens ftable = initFormTableLens();

         for(FormTableRow row : rows) {
            if(row.getRowState() == FormTableRow.DELETED) {
               ftable.removeDeletedRow(row);
            }

            row.commit();
         }
      }
   }

   /**
    * Convert the string state to int state, if you pass the wrong param state
    * string ,it throw exception.
    * @param state the string state.
    */
   private int getRowState(String state) {
      if(OLD.equals(state)) {
         return FormTableRow.OLD;
      }
      else if(CHANGED.equals(state)) {
         return FormTableRow.CHANGED;
      }
      else if(ADDED.equals(state)) {
         return FormTableRow.ADDED;
      }
      else if(DELETED.equals(state)) {
         return FormTableRow.DELETED;
      }

      throw new RuntimeException("Unsupported state : " + state);
   }

   /**
    * Check the table whether to support the data tip view or fly over view.
    */
   private boolean isSupportFlyOverOrTipView() {
      TableDataVSAssemblyInfo info = getInfo();

      if(info == null || info instanceof EmbeddedTableVSAssemblyInfo ||
         ((info instanceof TableVSAssemblyInfo) &&
         ((TableVSAssemblyInfo) info).isForm()))
      {
         return false;
      }

      return true;
   }

   /**
    * Convert FromTableRow to TableFormRowScriptable used by script.
    */
   private TableFormRowScriptable convertFormTableRow(FormTableRow row) {
      return convertFormTableRows(new FormTableRow[] {row})[0];
   }

   /**
    * Convert FromTableRow[] to TableFormRowScriptable[] used by script.
    */
   private TableFormRowScriptable[] convertFormTableRows(FormTableRow[] rows) {
      if(getTable() == null) {
         initTableArray();
      }

      XTable table = getTable();
      Map map = new HashMap();

      for(int i = 0; i < table.getColCount(); i++) {
         String header = XUtil.getHeader(table, i).toString();
         map.put(header, Integer.valueOf(i));
      }

      TableFormRowScriptable[] scriptRows = new TableFormRowScriptable[rows.length];

      for(int i = 0; i < rows.length; i++) {
         scriptRows[i] = new TableFormRowScriptable(table, map, rows[i]);
      }

      return scriptRows;
   }

   /**
    * Scripts could use the AttributeTableLens API to control cell level
    * settings.expose the explicitly set attributes.
    */
   private class TableFilter2 extends AttributeTableLens {
      public TableFilter2(TableLens table) {
         super(table);
      }

      @Override
      public void setData(int r, int c, Object val) {
         // delegate setData to setObject, so the data will be cached in
         // current table, to prevent object set to sub table, this may
         // cause sub table data path changed
         // fix bug1304696725564
         super.setObject(r, c, val);
      }

      /**
       * Return the number of rows in the table. The number of rows includes
       * the header rows.
       * @return number of rows in table.
       */
      @Override
      public int getRowCount() {
         FormTableLens ftable = initFormTableLens();
         return ftable == null ? super.getRowCount() : ftable.getRowCount();
      }

      @Override
      public void setPresenter(String header, Presenter p) {
         if(attritable != null) {
            attritable.setPresenter(header, p);
         }
         else {
            super.setPresenter(header, p);
         }
      }

      @Override
      public void setPresenter(int r, int c, Presenter presenter) {
         if(attritable != null) {
            attritable.setPresenter(r, c, presenter);
         }
         else {
            super.setPresenter(r, c, presenter);
         }
      }

      @Override
      public void setPresenter(int col, Presenter p) {
         if(attritable != null) {
            attritable.setPresenter(col, p);
         }
         else {
            super.setPresenter(col, p);
         }
      }

      @Override
      public void setHyperlink(int r, int c, Hyperlink.Ref link) {
         if(attritable != null) {
            attritable.setHyperlink(r, c, link);
         }
         else {
            super.setHyperlink(r, c, link);
         }
      }

      /**
       * Check if this table is dirty
       * @return
       */
      public boolean isDirty() {
         return dirty;
      }

      /**
       * Set this table dirty or not.
       * @param dirty
       */
      public void setIsDirty(boolean dirty) {
         this.dirty = dirty;
      }

      boolean dirty = false;
   }

   /**
    * Script could user the object to show and operator cell. it is formatted
    * by FormTableRow
    */
   private class TableFormRowScriptable extends XTableRow {
      /**
       * Constructor.
       */
      public TableFormRowScriptable(XTable table, Map map, FormTableRow formRow) {
         super(table, -1, map);

         this.rowState = formRow.getRowState();
         this.formRow = formRow;
      }

      /**
       * Get a named property from the object.
       */
      @Override
      public Object get(String name, Scriptable start) {
         if(name.equals("rowState")) {
            return rowState;
         }
         else if(name.equals("length")) {
            return Integer.valueOf(table.getColCount());
         }
         else if(name.equals("rowIndex")) {
            return formRow.getBaseRowIndex();
         }

         Integer index = (Integer) map.get(name);

         if(index != null && index >= 0) {
            return formRow.get(index.intValue());
         }

         return Undefined.instance;
      }

      /**
       * Get a property from the object selected by an integral index.
       */
      @Override
      public Object get(int index, Scriptable start) {
         if(index >= 0 && index < table.getColCount()) {
            return formRow.get(index);
         }

         return Undefined.instance;
      }

      private int rowState;
      private FormTableRow formRow;
   }

   /**
    * Check if the script already run once.
    */
   public boolean isDisableForm() {
      return disableForm;
   }

   /**
    * Set the script whether run once.
    */
   public void setDisableForm(boolean disableForm) {
      this.disableForm = disableForm;
   }

   private TableArray getTableArray() {
      TableArray tarr = this.tarr;

      if(tarr != null) {
         return tarr;
      }

      return this.tarr = new TableArray(getTable0(false));
   }

   protected boolean isCrosstabOrCalc() {
      return false;
   }

   protected TableArray getTableData() {
      if(!isCrosstabOrCalc()) {
         return getTableArray();
      }

      if(darr != null) {
         return darr;
      }

      XTable lens = getTable0(false);

      // calc table, use the runtime calc so values set by script can be used in the
      // calc processing. (62584, 62888)
      if(lens instanceof RuntimeCalcTableLens) {
         // use RuntimeCalcTableLens
      }
      else if(lens instanceof TableLens) {
         TableLens base = getFilterBaseTable((TableLens) lens);

         if(base != null) {
            darr = new TableArray(base);
            return darr;
         }
      }

      darr = new TableArray(lens);

      return darr;
   }

   public void clearDataArray() {
      this.darr = null;
   }

   private class TableArray2 extends TableArray {
      public TableArray2(String property, Class pType, boolean data) {
         super(property, pType, data);
      }

      @Override
      public XTable getElementTable() {
         return getFormatTable();
      }
   }

   private class TableColumns2 extends TableColumns {
      public TableColumns2(String property, Class pType) {
         super(property, pType);
      }

      @Override
      protected TableLens getElementTable() {
         return getFormatTable();
      }
   }

   private TableLens getFormatTable() {
      FormTableLens ftable = initFormTableLens();

      if(ftable != null) {
         return ftable;
      }

      if(table == null) {
         table = new TableFilter2((TableLens) getTableArray().getTable());
      }

      TableLens temp = table.getTable();

      while(temp != null) {
         if(temp instanceof FormatTableLens) {
            return temp;
         }

         temp = temp instanceof TableFilter ? ((TableFilter) temp).getTable() : null;
      }

      return table;
   }

   private boolean canModifyForm() {
      return !disableForm && (ViewsheetSandbox.exportRefresh.get() == null
         || !ViewsheetSandbox.exportRefresh.get());
   }

   private static final ThreadLocal<Integer> row = ThreadLocal.withInitial(() -> -1);
   private static final ThreadLocal<Integer> col = ThreadLocal.withInitial(() -> -1);
   private TableArray tarr;
   private TableArray darr;
   private TableFilter2 table;
   private TableHighlightedArray highlighted;
   private FormTableLens ftable;
   private boolean disableForm = false;
   private static boolean formIniting = false;
   private static final String OLD = "old";
   private static final String CHANGED = "changed";
   private static final String ADDED = "added";
   private static final String DELETED = "deleted";
   private static final int ALL = 0;
   private static final ThreadLocal<Boolean> recursive = ThreadLocal.withInitial(() -> false);
   private static final Logger LOG = LoggerFactory.getLogger(TableDataVSAScriptable.class);
}
