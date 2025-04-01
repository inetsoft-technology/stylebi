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
package inetsoft.report.lens;

import inetsoft.mv.formula.SumSQFormula;
import inetsoft.mv.formula.SumWTFormula;
import inetsoft.report.*;
import inetsoft.report.TableLayout.TableCellBindingInfo;
import inetsoft.report.filter.*;
import inetsoft.report.internal.TableElementDef;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.*;
import inetsoft.report.internal.table.*;
import inetsoft.report.script.TableArray;
import inetsoft.report.script.formula.CalcTableScope;
import inetsoft.report.script.viewsheet.VSAScriptable;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.*;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.audit.AuditRecordUtils;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.script.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntStack;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/**
 * The CalcTableLens provides an extended interface for a fixed size table.
 * It can contain formulas on a per cell basis. Each formula can reference
 * any other cell in the table similar to a spread sheet.
 *
 * @version 7.0, 2/20/2005
 * @author InetSoft Technology Corp
 */
public class CalcTableLens extends DefaultTableLens {
   /**
    * Don't expand cell.
    */
   public static final int EXPAND_NONE = 0;
   /**
    * Expand cells horizontally to new columns.
    */
   public static final int EXPAND_HORIZONTAL = 1;
   /**
    * Expand cells vertically to new rows.
    */
   public static final int EXPAND_VERTICAL = 2;

   /**
    * Regular edit mode.
    */
   public static final int DEFAULT_MODE = TableLayout.DEFAULT_MODE;
   /**
    * Edit mode that shows cell names and groups.
    */
   public static final int NAME_MODE = TableLayout.NAME_MODE;
   /**
    * Edit mode that shows full formula string.
    */
   public static final int FORMULA_MODE = TableLayout.FORMULA_MODE;

   /**
    * This is a pseudo group that is a non-repeating root of all groups.
    * It can be used as a row/column group name of a cell to disable the
    * default (using left/top expanding cell as parent group).
    */
   public static final String ROOT_GROUP = "_TABLE_ROOT_";

   /**
    * Create an empty table.
    */
   public CalcTableLens() {
      this(0, 0);
   }

   /**
    * Create a table width specified number of rows and columns.
    * @param rows number of rows.
    * @param cols number of columns.
    */
   public CalcTableLens(int rows, int cols) {
      super(rows, cols);
   }

   /**
    * Create a copy of a table lens.
    */
   public CalcTableLens(TableLens lens) {
      super(lens, false);
   }

   /**
    * Create a copy of a table lens.
    * @param dataonly true if only copy the data from table lens.
    */
   public CalcTableLens(TableLens lens, boolean dataonly) {
      super(lens, dataonly);
   }

   /**
    * Create a table with initial data. The dimension of the table is
    * derived from the data array dimensions.
    * @param data table data.
    */
   public CalcTableLens(Object[][] data) {
      super(data);
   }

   /**
    * Set option to fill blank cell with zero. By default blank cells
    * are left blank. If this is true, the blank cells are filled with zero.
    * @param fill true to fill blank cell with zero.
    */
   public void setFillBlankWithZero(boolean fill) {
      this.fillwithzero = fill;
   }

   /**
    * Check if column headers are kept.
    */
   public boolean isFillBlankWithZero() {
      return fillwithzero;
   }

   /**
    * Set the report associated with this table.
    */
   public void setReport(ReportSheet report) {
      this.report = report;
   }

   /**
    * Get the report associated with this table.
    */
   public ReportSheet getReport() {
      return report;
   }

   /**
    * Set the parent element to be used as the scope of formulas.
    */
   public void setElement(FormulaTable elem) {
      this.elem = elem;
   }

   /**
    * Get the parent element.
    */
   public FormulaTable getElement() {
      return elem;
   }

   /**
    * Set the base table for the calc table.
    * @hidden
    */
   public void setScriptTable(TableLens data) {
      this.data = data;

      synchronized(descLock) {
         cdescriptor = null;
      }
   }
   /**
    * Get the base table for the calc table.
    */
   public TableLens getScriptTable() {
      return data;
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      int userAlign = getUserAlignment(r, c);

      if(userAlign != -1) {
         return userAlign;
      }

      return H_LEFT | V_CENTER;
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      super.invalidate();
      tableScope = null;
      formulaCache = null;
   }

   public RuntimeCalcTableLens process() {
      try {
         // for Feature #26586, add javascript execution time record for current report.

         Object result = ProfileUtils.addExecutionBreakDownRecord(getReportName(),
            ExecutionBreakDownRecord.POST_PROCESSING_CYCLE, () -> {
               return process0();
            });

         //Object result = process0();
         return result == null ? null : (RuntimeCalcTableLens) result;
      }
      catch(Exception ex) {
         LOG.error("Failed to process calctablelens", ex);
         return null;
      }
   }

   /**
    * Process the calc table. If any cell expansion exists in the table,
    * a new table with the expansion is generated to be used for runtime
    * processing.
    */
   public synchronized RuntimeCalcTableLens process0() {
      RuntimeCalcTableLens lens = new RuntimeCalcTableLens(this);

      lens.setReport(getReport());
      lens.setElement(getElement());

      Map<String, CalcAttr> namemap = createNameMap();
      SpanMap spanmap = createSpanMap();

      RowGroupTree rowtree = new RowGroupTree(namemap, spanmap);
      ColGroupTree coltree = new ColGroupTree(namemap, spanmap);

      boolean rowfirst = rowtree.buildTree();
      boolean colfirst = coltree.buildTree();

      RuntimeCalcTableLens.IndexMap rowmap = new RuntimeCalcTableLens.IndexMap(getRowCount());
      RuntimeCalcTableLens.IndexMap colmap = new RuntimeCalcTableLens.IndexMap(getColCount());

      // processors for expanding the rows and columns
      Processor colprocessor = new ColProcessor(coltree.getRoot(), colmap, rowmap);
      Processor rowprocessor = new RowProcessor(rowtree.getRoot(), rowmap, colmap);

      if(colfirst && rowfirst) {
         LOG.warn("Circular dependency on row and column expansion. " +
                  "Expanding columns first.");
      }

      if(rowfirst) {
         rowprocessor.process(lens);
         colprocessor.process(lens);
      }
      else {
         colprocessor.process(lens);
         rowprocessor.process(lens);
      }

      lens.setRowMap(rowmap);
      lens.setColMap(colmap);

      // any post processing should be done in complete
      lens.complete();

      return lens;
   }

   /**
    * Cancel the runtime calc table generation.
    */
   public void cancel() {
      cancelled = true;
   }

   /**
    * Create a map from cell names to CalcAttr.
    */
   public Map<String, CalcAttr> createNameMap() {
      Map<String, CalcAttr> map = new HashMap<>();

      for(int i = 0; i < attrs.size(); i++) {
         CalcAttr attr = attrs.get(i);

         if(attr.getCellName() != null) {
            map.put(attr.getCellName(), attr);
         }
      }

      return map;
   }

   /**
    * Create a spanmap of the table.
    */
   public SpanMap createSpanMap() {
      if(spanMap == null) {
         synchronized(spanMapLock) {
            if(spanMap == null) {
               SpanMap nSanMap = new SpanMap();

               for(int i = 0; moreRows(i); i++) {
                  for(int j = 0; j < getColCount(); j++) {
                     Dimension span = getSpan(i, j);

                     if(span != null) {
                        nSanMap.add(i, j, span.height, span.width);
                     }
                  }
               }

               spanMap = nSanMap;
            }
         }
      }

      return spanMap;
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      synchronized(descLock) {
         if(cdescriptor == null) {
            cdescriptor = new CalcTableLensDataDescriptor();
         }

         return cdescriptor;
      }
   }

   /**
    * Get the current edit mode.
    */
   public int getEditMode() {
      return editMode;
   }

   /**
    * Set to editing mode, which control how the cell contents are shown.
    * @param editMode one of DEFAULT_MODE, NAME_MODE, and FORMULA_MODE.
    */
   public void setEditMode(int editMode) {
      this.editMode = editMode;
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      if(editMode == NAME_MODE) {
         String name = getCellName(r, c);
         String rowGroup = getRowGroup(r, c);
         String colGroup = getColGroup(r, c);

         if(name == null) {
            name = "";
         }

         if(rowGroup != null) {
            name += "@" + rowGroup;
         }

         if(colGroup != null) {
            if(rowGroup != null) {
               name += "," + colGroup;
            }
            else {
               name += "@" + colGroup;
            }
         }

         switch(getExpansion(r, c)) {
         case EXPAND_VERTICAL:
            name = "*" + name;
            break;
         case EXPAND_HORIZONTAL:
            name = "+" + name;
            break;
         }

         return (name.length() > 0) ? '[' + name + ']' : "";
      }

      Object obj = super.getObject(r, c);

      if(obj instanceof Formula) {
         Formula expr = (Formula) obj;
         String str = expr.getFormula();

         if(editMode != FORMULA_MODE) {
            Dimension span = getSpan(r, c);
            int cols = (span == null) ? 1 : span.width;
            int maxchars = 600 / (getColCount() / cols) / 8;

            if(str.length() > maxchars) {
               str = str.substring(0, maxchars) + "...";
            }
         }

         return "=" + str;
      }

      return obj;
   }

   /**
    * Return the value at the specified cell. If this is a formula, the formula
    * is evaluated.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   protected Object getValue(int r, int c) {
      Object obj = super.getObject(r, c);

      if(obj instanceof Formula) {
         Object val = getCachedValue(r, c);

         if(val != SparseMatrix.NULL) {
            return val;
         }

         Formula expr = (Formula) obj;

         // @by larryl, this is to prevent the recursive calling of
         // formula in case the formula triggers a call to the getObject()
         // (such as getting the header cell value for column header
         // if the formula is in header row).
         setCachedValue(r, c, null);

         setCurrent(row, r);
         setCurrent(col, c);

         try {
            obj = evaluate(r, c, expr);
         }
         catch(ScriptException se) {
            obj = "ERROR: " + se.getMessage();
         }
         finally {
            row.get().pop();
            col.get().pop();
         }

         // avoid -0.0
         if(obj instanceof Double) {
            Double dobj = (Double) obj;

            if(dobj == 0.0) {
               obj = 0.0;
            }
         }

         setCachedValue(r, c, obj);
      }

      return obj;
   }

   private static void setCurrent(ThreadLocal<IntStack> row, int r) {
      IntStack stack = row.get();

      if(stack == null) {
         row.set(stack = new IntArrayList());
      }

      stack.push(r);
   }

   /**
    * Get the current row index of the script execution context (thread).
    */
   public static int getRow() {
      return getCurrent(row);
   }

   /**
    * Get the current column index of the script execution context (thread).
    */
   public static int getCol() {
      return getCurrent(col);
   }

   private static int getCurrent(ThreadLocal<IntStack> stack) {
      IntStack row = stack.get();
      return row == null || row.isEmpty() ? -1 : row.topInt();
   }

   /**
    * Get cached cell value.
    */
   protected Object getCachedValue(int r, int c) {
      if(formulaCache != null) {
         Object val = formulaCache.get(r, c);

         return (val != SparseMatrix.NULL) ? val : SparseMatrix.NULL;
      }

      return SparseMatrix.NULL;
   }

   /**
    * Set the cached value.
    */
   protected void setCachedValue(int r, int c, Object obj) {
      if(formulaCache == null) {
         formulaCache = new SparseMatrix();
      }

      formulaCache.set(r, c, obj);
   }

   /**
    * Set a formula on a cell.
    */
   public void setFormula(int r, int c, String formula) {
      if(formula != null) {
         setObject(r, c, new Formula(formula));
      }
   }

   /**
    * Get the formula on a cell.
    */
   public String getFormula(int r, int c) {
      Object obj = getTable().getObject(r, c);

      if(obj instanceof Formula) {
         return ((Formula) obj).getFormula();
      }

      return null;
   }

   /**
    * Insert a new row above the specified row.
    */
   @Override
   public void insertRow(final int row) {
      super.insertRow(row);

      adjust(new Adjuster(row) {
         @Override
         public boolean adjust(CalcCellAttr attr) {
            if(attr.getRow() >= row) {
               attr.setRow(attr.getRow() + 1);
            }

            return true;
         }
      });

      // since the adjuster always applies ++/-- on row or column index.
      // it should still be sorted after the change. This assemption is based
      // on the current implementation and is fragile
   }

   /**
    * Remove the specified row.
    */
   @Override
   public void removeRow(final int row) {
      super.removeRow(row);

      adjust(new Adjuster(row) {
         @Override
         public boolean adjust(CalcCellAttr attr) {
            if(attr.getRow() == row) {
               return false;
            }

            if(attr.getRow() >= row) {
               attr.setRow(attr.getRow() - 1);
            }

            return true;
         }
      });
   }

   /**
    * Insert a new column to the left of the specified column.
    */
   @Override
   public void insertColumn(final int col) {
      super.insertColumn(col);

      adjust(new Adjuster() {
         @Override
         public boolean adjust(CalcCellAttr attr) {
            if(attr.getCol() >= col) {
               attr.setCol(attr.getCol() + 1);
            }

            return true;
         }
      });
   }

   /**
    * Remove a column at the specified location.
    */
   @Override
   public void removeColumn(final int col) {
      super.removeColumn(col);

      adjust(new Adjuster() {
         @Override
         public boolean adjust(CalcCellAttr attr) {
            if(attr.getCol() == col) {
               return false;
            }

            if(attr.getCol() >= col) {
               attr.setCol(attr.getCol() - 1);
            }

            return true;
         }
      });
   }

   /**
    * Get an unique cell identifier.
    */
   public String getCellID(int row, int col) {
      CalcAttr attr = getCalcAttr(row, col, false);

      return (attr == null) ? null : attr.getCellID();
   }

   /**
    * Get the cell location with the specified cell name.
    * @return cell location where x is column and y is row index.
    */
   public Point getCellLocation(String name) {
      for(int i = 0; i < attrs.size(); i++) {
         CalcAttr attr = attrs.get(i);

         if(Objects.equals(name, attr.getCellName())) {
            return new Point(attr.getCol(), attr.getRow());
         }
      }

      return null;
   }

   /**
    * Get the cell name.
    */
   public String getCellName(int row, int col) {
      CalcAttr attr = getCalcAttr(row, col, false);

      return (attr == null) ? null : attr.getCellName();
   }

   /**
    * Set the cell name.
    */
   public void setCellName(int row, int col, String name) {
      String oname = getCellName(row, col);
      CalcAttr attr = getCalcAttr(row, col, true);

      attr.setCellName(name);

      // sync group name
      if(oname != null) {
         for(int i = 0; i < getRowCount(); i++) {
            for(int j = 0; j < getColCount(); j++) {
               if(oname.equals(getRowGroup(i, j))) {
                  setRowGroup(i, j, name);
               }

               if(oname.equals(getColGroup(i, j))) {
                  setColGroup(i, j, name);
               }

               if(oname.equals(getMergeRowGroup(i, j))) {
                  setMergeRowGroup(i, j, name);
               }

               if(oname.equals(getMergeColGroup(i, j))) {
                  setMergeColGroup(i, j, name);
               }
            }
         }
      }
   }

   /**
    * Check if expanded cells should be merged.
    */
   public boolean isMergeCells(int row, int col) {
      CalcAttr attr = getCalcAttr(row, col, false);

      return (attr == null) ? false : attr.isMergeCells();
   }

   /**
    * Set wheter expanded cells should be merged.
    */
   public void setMergeCells(int row, int col, boolean merge) {
      CalcAttr attr = getCalcAttr(row, col, true);

      attr.setMergeCells(merge);
   }

   /**
    * Get the name of the row group cell for merging expanded cells. If it's set,
    * only the cells within the same group are merged.
    */
   public String getMergeRowGroup(int row, int col) {
      CalcAttr attr = getCalcAttr(row, col, false);

      return (attr == null) ? null : attr.getMergeRowGroup();
   }

   /**
    * Set the row group for merging the cells.
    */
   public void setMergeRowGroup(int row, int col, String group) {
      CalcAttr attr = getCalcAttr(row, col, true);

      attr.setMergeRowGroup(group);
   }

   /**
    * Get the name of the column group cell for merging expanded cells.
    * If it's set, only the cells within the same group are merged.
    */
   public String getMergeColGroup(int row, int col) {
      CalcAttr attr = getCalcAttr(row, col, false);

      return (attr == null) ? null : attr.getMergeColGroup();
   }

   /**
    * Set the column group for merging the cells.
    */
   public void setMergeColGroup(int row, int col, String group) {
      CalcAttr attr = getCalcAttr(row, col, true);

      attr.setMergeColGroup(group);
   }

   /**
    * Check if a page break should be inserted after this group.
    */
   public boolean isPageAfter(int row, int col) {
      CalcAttr attr = getCalcAttr(row, col, false);

      return (attr == null) ? false : attr.isPageAfter();
   }

   /**
    * Set the page after flag. This should only be set on a vertically
    * expanding cell.
    */
   public void setPageAfter(int row, int col, boolean pageAfter) {
      CalcAttr attr = getCalcAttr(row, col, true);

      attr.setPageAfter(pageAfter);
   }

   /**
    * Get the OrderInfo for a cell
    */
   public OrderInfo getOrderInfo(int row, int col) {
      CalcAttr attr = getCalcAttr(row, col, false);

      return (attr == null) ? null : attr.getOrderInfo();
   }

   /**
    * Sets the OrderInfo for a cell
    */
   public void setOrderInfo(int row, int col, OrderInfo order) {
      getCalcAttr(row, col, true).setOrderInfo(order);
   }

   /**
    * Get the TopNInfo for a cell
    */
   public TopNInfo getTopN(int row, int col) {
      CalcAttr attr = getCalcAttr(row, col, false);

      return (attr == null) ? null : attr.getTopN();
   }

   /**
    * Set the TopNInfo for a cell
    */
   public void setTopN(int row, int col, TopNInfo topN) {
      getCalcAttr(row, col, true).setTopN(topN);
   }

   /**
    * Gets the flag that determines if a cell is bound to data.
    *
    * @param row the row index of the cell.
    * @param col the column index of the cell.
    *
    * @return <tt>true</tt> if bound; <tt>false</tt> otherwise.
    */
   public boolean isBound(int row, int col) {
      CalcAttr attr = getCalcAttr(row, col, false);
      return (attr == null) ? false : attr.isBound();
   }

   /**
    * Sets the flag that determines if a cell is bound to data.
    *
    * @param row   the row index of the cell.
    * @param col   the column index of the cell.
    * @param bound <tt>true</tt> if bound; <tt>false</tt> otherwise.
    */
   public void setBound(int row, int col, boolean bound) {
      getCalcAttr(row, col, true).setBound(bound);
   }

   /**
    * Get the row group of this cell.
    */
   public String getRowGroup(int row, int col) {
      CalcAttr attr = getCalcAttr(row, col, false);

      return (attr == null) ? null : attr.getRowGroup();
   }

   /**
    * Set the row group of this cell. Setting the row group of a cell makes
    * it a nested group/cell of the parent group. The parent group is
    * expanded first.
    */
   public void setRowGroup(int row, int col, String group) {
      CalcAttr attr = getCalcAttr(row, col, true);

      attr.setRowGroup(group);
   }

   /**
    * Get the column group of this cell.
    */
   public String getColGroup(int row, int col) {
      CalcAttr attr = getCalcAttr(row, col, false);

      return (attr == null) ? null : attr.getColGroup();
   }

   /**
    * Set the column group of this cell. Setting the column group of a
    * cell makes it a nested group/cell of the parent group.
    * The parent group is expanded first.
    */
   public void setColGroup(int row, int col, String group) {
      CalcAttr attr = getCalcAttr(row, col, true);

      attr.setColGroup(group);
   }

   /**
    * Get the cell expansion type.
    */
   public int getExpansion(int row, int col) {
      CalcAttr attr = getCalcAttr(row, col, false);

      return (attr == null) ? EXPAND_NONE : attr.getExpansion();
   }

   /**
    * Set the cell expansion type. Use one of the expansion constants:
    * EXPAND_NONE, EXPAND_HORIZONTAL, EXPAND_VERTICAL.
    */
   public void setExpansion(int row, int col, int expansion) {
      CalcAttr attr = getCalcAttr(row, col, true);

      attr.setExpansion(expansion);
   }

   /**
    * Get all cell names (name set through setCellName()).
    */
   public String[] getCellNames() {
      List<String> names = new ArrayList<>();

      for(int i = 0; i < attrs.size(); i++) {
         CalcAttr attr = attrs.get(i);

         if(attr.getCellName() != null) {
            names.add(attr.getCellName());
         }
      }

      return names.toArray(new String[names.size()]);
   }

   /**
    * Retrieve all page after and merged cells location.
    * @hidden
    */
   public void retrieve(Map<Point, Boolean> pageAfters, Map<Point, Boolean> merges) {
      for(int i = 0; i < attrs.size(); i++) {
         CalcAttr attr = attrs.get(i);

         if(attr == null) {
            continue;
         }

         Point loc = new Point(attr.getCol(), attr.getRow());

         if(attr.isPageAfter()) {
            pageAfters.put(loc, Boolean.TRUE);
         }

         if(attr.isMergeCells()) {
            merges.put(loc, Boolean.TRUE);
         }
      }
   }

   public CalcAttr getCalcAttr(int row, int col) {
      for(int i = 0; i < attrs.size(); i++) {
         CalcAttr attr = attrs.get(i);

         if(attr != null && row == attr.getRow() && col == attr.getCol()) {
            return attr;
         }
      }

      return null;
   }

   public void addCalcAttr(CalcAttr attr) {
      if(Collections.binarySearch(attrs, attr, new CalcAttrComp()) < 0) {
         attrs.add(attr);
      }
   }

   /**
    * Get the cell attributes.
    * @param force true to create a CalcAttr is none exists.
    */
   private CalcAttr getCalcAttr(int row, int col, boolean force) {
      CalcAttr attr = new CalcAttr(row, col);
      int idx = Collections.binarySearch(attrs, attr, new CalcAttrComp());

      if(idx >= 0) {
         return attrs.get(idx);
      }

      if(force) {
         attr = new CalcAttr(this, row, col);
         attrs.add(attr);
         Collections.sort(attrs, new CalcAttrComp());

         return attr;
      }

      return null;
   }

   /**
    * Evaluate a formula for a cell.
    */
   protected Object evaluate(int row, int col, Formula expr) {
      String formula = expr.getFormula();

      if(formula == null) {
         return null;
      }

      ScriptEnv senv = getScriptEnv();
      Object ofield = null;

      if(tableScope == null) {
         synchronized(this) {
            if(tableScope == null) {
               CalcTableScope scope = new CalcTableScope(CalcTableLens.this);

               if(elem != null) {
                  senv.put(elem.getID() + "::calcTableScope", scope);

                  TableLens table = elem.getScriptTable();
                  setDataTable(table);
                  TableArray arr = new TableArray(table);
                  arr.setCalcArray(true);
                  scope.put("data", scope, arr);

                  if(elem instanceof TableElementDef) {
                     TableLens[] tables = ((TableElementDef) elem).getScriptTables();

                     if(tables != null) {
                        for(int i = 0; i < tables.length; i++) {
                           arr = new TableArray(tables[i]);
                           arr.setCalcArray(true);
                           scope.put(String.format("data%d", i + 1), scope, arr);
                        }
                     }
                  }

                  // To vs calc table, the formula cell's scope is
                  // CalcTableScope. Its parent scope is calctable's scope(
                  // TableDataVSAScriptable). The scope levels is this:
                  // (CalcTableScope->TableDataVSAScriptable->ViewsheetScope).
                  if(senv.get("viewsheet") instanceof Scriptable) {
                     String eid = elem.getID();

                     if(eid.indexOf('.') >= 0) {
                        eid = eid.substring(eid.lastIndexOf('.') + 1);
                     }

                     ViewsheetScope vscope = (ViewsheetScope) senv.get("viewsheet");
                     VSAScriptable scriptable = vscope.getVSAScriptable(eid);
                     scope.setParentScope(scriptable);
                  }
               }
               else {
                  senv.put("calcTableScope", scope);
               }

               this.tableScope = scope;
            }
         }
      }

      Consumer<Exception> handler = (Exception ex) -> {
         String suggestion = senv.getSuggestion(ex, "field", tableScope);
         String rname = getContextName();
         String msg = "Script error: " + ex.getMessage() +
            (suggestion != null ? "\nTo fix: " + suggestion : "") +
            "\nFormula failed:\n" + XUtil.numbering(formula);
         msg = rname == null ? msg : rname + msg;

         if(LOG.isDebugEnabled()) {
            LOG.debug(msg, ex);
         }
         else {
            LOG.warn(msg);
         }
      };

      try {
         FormulaContext.pushTable(CalcTableLens.this);
         FormulaContext.pushCellLocation(new Point(col, row));

         Object script = scriptCache.get(formula, senv, handler);
         ofield = tableScope.get("field", tableScope);

         if(this instanceof RuntimeCalcTableLens) {
            CalcCellContext context = ((RuntimeCalcTableLens) this).getCellContext(row, col);

            if(context != null) {
               // find the scopes (field from rowList) and chain them into one.
               // this way multiple rowList can be accessed by children.
               Scriptable field = null;
               Scriptable lastfield = null;

               for(CalcCellContext.Group group : context.getGroups()) {
                  Object scope = group.getScope();

                  if(scope != null) {
                     Scriptable field2 = (Scriptable) scope;

                     if(field == null) {
                        field = field2;
                        lastfield = field;
                        field.setPrototype(null);
                     }
                     else {
                        lastfield.setPrototype(field2);
                        lastfield = field2;
                        lastfield.setPrototype(null);
                     }
                  }
               }

               if(field != null) {
                  tableScope.put("field", tableScope, field);
               }
            }
         }

         tableScope.setRow(row);

         return ProfileUtils.addExecutionBreakDownRecord(getReportName(),
            ExecutionBreakDownRecord.JAVASCRIPT_PROCESSING_CYCLE, args -> {
               return senv.exec(args[0], args[1], null, null);
            }, script, tableScope);

         //return senv.exec(script, tableScope);
      }
      catch(Exception ex) {
         String rname = getContextName();
         String str = Catalog.getCatalog().getString("JavaScript error") +
            ": " + ex.getMessage();
         throw new ScriptException(rname == null ? str : rname + str, ex);
      }
      finally {
         FormulaContext.popTable();
         FormulaContext.popCellLocation();

         // this scope might be reset to null when executing script
         if(tableScope != null) {
            if(ofield == null) {
               tableScope.delete("field");
            }
            else {
               tableScope.put("field", tableScope, ofield);
            }
         }
      }
   }

   /**
    * Get the script environment.
    * @return the script environment.
    */
   private ScriptEnv getScriptEnv() {
      return elem.getScriptEnv();
   }

   /**
    * Get the context name.
    */
   private String getContextName() {
      if(report != null) {
         return report.getContextName() != null ?
            "Report: " + report.getContextName() + "\n" : null;
      }

      return elem != null ? "Assembly: " + elem.getID() + "\n" : null;
   }

   /**
    * Make a copy of this table lens.
    */
   @Override
   public CalcTableLens clone() {
      CalcTableLens tbl = (CalcTableLens) super.clone();

      tbl.report = report;
      tbl.attrs = Tool.deepCloneCollection(attrs);
      tbl.cdescriptor = null;

      if(spanMap != null) {
         tbl.spanMap = (SpanMap) spanMap.clone();
      }

      return tbl;
   }

   /**
    * Perform object level post clone operation.
    */
   @Override
   protected Object cloneObject(Object obj) {
      if(obj instanceof Formula) {
         return new Formula(((Formula) obj).getFormula());
      }

      return obj;
   }

   /**
    * FreehandTableLens data descriptor.
    */
   protected class CalcTableLensDataDescriptor implements TableDataDescriptor {
      public CalcTableLensDataDescriptor() {
      }

      /**
       * Get formula instance by formula name.
       */
      private static inetsoft.report.filter.Formula getFormulaByName(String formulaName) {
         if(formulaName == null) {
            return null;
         }

         inetsoft.report.filter.Formula formula = null;

         if(formulaName.equals(XConstants.NONE_FORMULA)) {
            formula = new NoneFormula();
         }
         else if(formulaName.equals(XConstants.AVERAGE_FORMULA)) {
            formula = new AverageFormula();
         }
         else if(formulaName.equals(XConstants.COUNT_FORMULA)) {
            formula = new CountFormula();
         }
         else if(formulaName.equals(XConstants.DISTINCTCOUNT_FORMULA)) {
            formula = new DistinctCountFormula();
         }
         else if(formulaName.equals(XConstants.MAX_FORMULA)) {
            formula = new MaxFormula();
         }
         else if(formulaName.equals(XConstants.MIN_FORMULA)) {
            formula = new MinFormula();
         }
         else if(formulaName.equals(XConstants.PRODUCT_FORMULA)) {
            formula = new ProductFormula();
         }
         else if(formulaName.equals(XConstants.SUM_FORMULA)) {
            formula = new SumFormula();
         }
         else if(formulaName.equals(XConstants.CONCAT_FORMULA)) {
            formula = new ConcatFormula();
         }
         else if(formulaName.equals(XConstants.STANDARDDEVIATION_FORMULA)) {
            formula = new StandardDeviationFormula();
         }
         else if(formulaName.equals(XConstants.VARIANCE_FORMULA)) {
            formula = new VarianceFormula();
         }
         else if(formulaName.equals(XConstants.POPULATIONSTANDARDDEVIATION_FORMULA)) {
            formula = new PopulationStandardDeviationFormula();
         }
         else if(formulaName.equals(XConstants.POPULATIONVARIANCE_FORMULA)) {
            formula = new PopulationVarianceFormula();
         }
         else if(formulaName.equals(XConstants.CORRELATION_FORMULA)) {
            formula = new CorrelationFormula();
         }
         else if(formulaName.equals(XConstants.COVARIANCE_FORMULA)) {
            formula = new CovarianceFormula();
         }
         else if(formulaName.equals(XConstants.MEDIAN_FORMULA)) {
            formula = new MedianFormula();
         }
         else if(formulaName.equals(XConstants.NTHLARGEST_FORMULA)) {
            formula = new NthLargestFormula();
         }
         else if(formulaName.equals(XConstants.NTHMOSTFREQUENT_FORMULA)) {
            formula = new NthMostFrequentFormula();
         }
         else if(formulaName.equals(XConstants.NTHSMALLEST_FORMULA)) {
            formula = new NthSmallestFormula();
         }
         else if(formulaName.equals(XConstants.PTHPERCENTILE_FORMULA)) {
            formula = new PthPercentileFormula();
         }
         else if(formulaName.equals(XConstants.WEIGHTEDAVERAGE_FORMULA)) {
            formula = new WeightedAverageFormula();
         }
         else if(formulaName.equals(XConstants.SUMWT_FORMULA)) {
            formula = new SumWTFormula();
         }
         else if(formulaName.equals(XConstants.SUMSQ_FORMULA)) {
            formula = new SumSQFormula();
         }
         else if(formulaName.equals(XConstants.FIRST_FORMULA)) {
            formula = new FirstFormula();
         }
         else if(formulaName.equals(XConstants.LAST_FORMULA)) {
            formula = new LastFormula();
         }
         else if(formulaName.equals(XConstants.MODE_FORMULA)) {
            formula = new ModeFormula();
         }

         return formula;
      }

      /**
       * Get table data path of a specified table column.
       * @param col the specified table column
       * @return table data path of the table column
       */
      @Override
      public TableDataPath getColDataPath(int col) {
         return new TableDataPath("Column [" + col + "]");
      }

      /**
       * Get table data path of a specified table row.
       * @param row the specified table row
       * @return table data path of the table row
       */
      @Override
      public TableDataPath getRowDataPath(int row) {
         return new TableDataPath(-1, getRowType(row), calcRowIndex(row));
      }

      /**
       * Get table data path of a specified table cell.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @return table data path of the table cell
       */
      @Override
      public TableDataPath getCellDataPath(int row, int col) {
         String[] paths = {"Cell [" + row + "," + col + "]"};

         return new TableDataPath(-1, getRowType(row), XSchema.STRING, paths);
      }

      /**
       * Check if a column belongs to a table data path.
       * @param col the specified table col
       * @param path the specified table data path
       * @return true if the col belongs to the table data path, false otherwise
       */
      @Override
      public boolean isColDataPath(int col, TableDataPath path) {
         return ("Column [" + col + "]").equals(path.getPath()[0]);
      }

      /**
       * Check if a row belongs to a table data path.
       * @param row the specified table row
       * @param path the specified table data path
       * @return true if the row belongs to the table data path, false otherwise
       */
      @Override
      public boolean isRowDataPath(int row, TableDataPath path) {
         return getRowDataPath(row).equals(path);
      }

      /**
       * Check if a cell belongs to a table data path in a loose way.
       * Note: when cheking, path in the table data path will be ignored.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @param path the specified table data path
       * @return true if the cell belongs to the table data path,
       * false otherwise
       */
      @Override
      public boolean isCellDataPathType(int row, int col, TableDataPath path) {
         if(path.getPath().length > 0) {
            String path0 = path.getPath()[0];

            // for location based path, there is only a single match
            if(path0.startsWith("Cell [")) {
               return isCellDataPath(row, col, path);
            }
         }

         return false;
      }

      /**
       * Check if a cell belongs to a table data path.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @param path the specified table data path
       * @return true if the cell belongs to the table data path,
       * false otherwise
       */
      @Override
      public boolean isCellDataPath(int row, int col, TableDataPath path) {
         TableDataPath path2 = getCellDataPath(row, col);
         return path2.equals(path);
      }

      /**
       * Get level of a specified table row, which is required for nested table.
       * The default value is <tt>-1</tt>.
       * @param row the specified table row
       * @return level of the table row
       */
      @Override
      public int getRowLevel(int row) {
         return -1;
      }

      /**
       * Get table type which is one of the table types defined in table data
       * descriptor like <tt>NORMAL_TABLE</tt>, <tt>CROSSTAB_TABLE</tt>, etc.
       * @return table type
       */
      @Override
      public int getType() {
         return CALC_TABLE;
      }

      /**
       * Get the row data path type.
       */
      private int getRowType(int r) {
         if(r < getHeaderRowCount()) {
            return TableDataPath.HEADER;
         }
         else if(r >= getRowCount() - getTrailerRowCount()) {
            return TableDataPath.TRAILER;
         }

         return TableDataPath.DETAIL;
      }

      /**
       * Calc the row index, convert the global row to region index.
       */
      private int calcRowIndex(int r) {
         if(r < getHeaderRowCount()) {
            return r;
         }
         else if(r >= getRowCount() - getTrailerRowCount()) {
            return r - getRowCount() + getTrailerRowCount();
         }

         return r - getHeaderRowCount();
      }

      /**
       * Get table xmeta info.
       * @param path the specified table data path
       * @return meta info of the table data path
       */
      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         TableDataDescriptor desc = getDesc();

         if(desc != null && elem != null) {
            String cname = path.getPath()[0];

            if(cname.startsWith("Cell [") && cname.endsWith("]")) {
               cname = cname.substring(6, cname.length() - 1);
               String[] pair = Tool.split(cname, ',');
               int row = Integer.parseInt(pair[0]);
               int col = Integer.parseInt(pair[1]);
               TableLayout layout = elem.getTableLayout();

               if(layout != null) {
                  CellBindingInfo binding = layout.getCellInfo(row, col);

                  if(binding != null && binding.getType() == CellBinding.BIND_COLUMN) {
                     String val = binding.getValue();

                     if(binding instanceof TableCellBindingInfo) {
                        int level = ((TableCellBindingInfo) binding).getDateOption();

                        if(XUtil.getDefaultDateFormat(level) != null) {
                           val = DateRangeRef.getName(val, level);
                        }
                     }

                     String oval = val;
                     boolean changed = false;
                     String formula = null;
                     int percentage = 0;

                     // if we use crosstab to support calc, the name should apply
                     // alias
                     if(binding.getBType() == CellBinding.SUMMARY &&
                        (binding instanceof TableCellBindingInfo))
                     {
                        formula = ((TableCellBindingInfo) binding).getFormula();

                        if(formula != null) {
                           int idx1 = formula.indexOf('<');
                           int idx2 = formula.indexOf('>');

                           if(idx1 >= 0 && idx2 >= 0) {
                              percentage = Integer.parseInt(formula.substring(idx1 + 1, idx2));
                              formula = formula.substring(0, idx1) + formula.substring(idx2 + 1);
                           }

                           idx1 = formula.lastIndexOf('(');
                           idx2 = formula.lastIndexOf(')');

                           if(idx2 == formula.length() - 1) {
                              val = formula.substring(0, idx1 + 1) + val + ", " +
                                 formula.substring(idx1 + 1, idx2) + ")";
                           }
                           else {
                              val = formula + "(" + val + ")";
                           }

                           changed = true;
                        }
                     }

                     XMetaInfo minfo = null;
                     TableDataPath opath = null;

                     if(binding.getBType() == CellBinding.GROUP &&
                        binding instanceof TableCellBindingInfo)
                     {
                        opath = new TableDataPath(
                           -1, TableDataPath.GROUP_HEADER, path.getDataType(),
                           new String[]{ binding.getValue() });
                        minfo = desc.getXMetaInfo(opath);
                     }
                     else {
                        opath = new TableDataPath(
                           -1, TableDataPath.DETAIL, path.getDataType(),
                           new String[]{ oval });
                        minfo = desc.getXMetaInfo(opath);
                     }

                     if(minfo == null && changed) {
                        opath = new TableDataPath(
                           -1, TableDataPath.DETAIL, path.getDataType(), new String[] {val});
                        minfo = desc.getXMetaInfo(opath);
                     }

                     if(formula != null && binding.getBType() == CellBinding.SUMMARY) {
                        Util.removeIncompatibleMetaInfo(minfo, getFormulaByName(formula));
                     }

                     if(binding.getBType() == CellBinding.SUMMARY &&
                        binding instanceof TableCellBindingInfo &&
                        percentage != StyleConstants.PERCENTAGE_NONE)
                     {
                        minfo = minfo != null ? minfo.clone() : new XMetaInfo();
                        minfo.setXFormatInfo(new XFormatInfo(TableFormat.PERCENT_FORMAT, ""));
                     }

                     return minfo;
                  }
               }
            }
         }

         return null;
      }

      @Override
      public List<TableDataPath> getXMetaInfoPaths() {
         return null;
      }

      /**
       * Check if contains format.
       * @return true if contains format
       */
      @Override
      public boolean containsFormat() {
         TableDataDescriptor desc = getDesc();
         return desc != null && desc.containsFormat();
      }

      /**
       * Check if contains drill.
       * @return <tt>true</tt> if contains drill, <tt>false</tt> otherwise
       */
      @Override
      public boolean containsDrill() {
         TableDataDescriptor desc = getDesc();
         return desc != null && desc.containsDrill();
      }

      /**
       * Get the string representation.
       */
      public String toString() {
         return super.toString() + "[" + CalcTableLens.this + "]";
      }

      private TableDataDescriptor getDesc() {
         if(desc == null) {
            if(data != null) {
               desc = data.getDescriptor();
            }

            if(desc == null && elem != null) {
               TableLens table = elem.getScriptTable();

               if(table != null) {
                  desc = table.getDescriptor();
               }

               if(desc == this) {
                  desc = null;
               }
            }
         }

         return desc;
      }

      private TableDataDescriptor desc;
   }

   /**
    * Class to hold a cell formula.
    */
   public static class Formula implements Cloneable {
      public Formula(String formula) {
         this.formula = formula;
      }

      public void setFormula(String formula) {
         this.formula = formula;
      }

      public String getFormula() {
         return formula;
      }

      @Override
      public Object clone() {
         try {
            return super.clone();
         }
         catch(CloneNotSupportedException e) {
         }

         return this;
      }

      public String toString() {
         return formula;
      }

      private String formula;
   }

   /**
    * Adjust the row or column index.
    */
   protected void adjust(Adjuster adjuster) {
      for(int i = 0; i < attrs.size(); i++) {
         boolean keep = adjuster.adjust(attrs.get(i));

         if(!keep) {
            attrs.remove(i);
            i--;
         }
      }
   }

   /**
    * Interface for adjusting row/column index.
    */
   protected abstract static class Adjuster {
      /**
       * Create a column adjuster.
       */
      public Adjuster() {
      }

      /**
       * Create a row adjuster.
       */
      public Adjuster(int row) {
         this.row = row;
      }

      /**
       * Get the row index that has changed (inserted/removed).
       * @return row index or -1 if this adjust is for column.
       */
      public int getRow() {
         return row;
      }

      /**
       * @return false to remove the item from the list.
       */
      public abstract boolean adjust(CalcCellAttr attr);

      private int row = -1;
   }

   /**
    * This class contains row/col group nesting tree.
    */
   private abstract class GroupTree {
      /**
       * Create nesting trees.
       * @param namemap a mapping from cell name to CalcAttr.
       */
      public GroupTree(Map<String, CalcAttr> namemap, SpanMap spanmap) {
         this.namemap = namemap;
         this.spanmap = spanmap;
      }

      /**
       * Get the group name of this cell.
       */
      protected abstract String getGroup(CalcAttr attr);

      /**
       * Check if this cell should expand in runtime.
       */
      protected abstract boolean isExpand(CalcAttr attr);

      /**
       * Check if this cell is bound, even if not grouped or expanded.
       */
      protected abstract boolean isBound(CalcAttr attr);

      /**
       * Check if this cell expands in the other dimension.
       */
      protected abstract boolean isExpandOther(CalcAttr attr);

      /**
       * Find the default parent group. Null if no dynamic cell exists to the
       * left or above the cell.
       */
      protected abstract CalcAttr findDefaultGroup(CalcAttr attr);

      /**
       * Get the root of group tree.
       */
      public XNode getRoot() {
         return root;
      }

      /**
       * Build the group tree.
       * @return true if the expansion depends on the other dimension.
       */
      public boolean buildTree() {
         Map<CalcAttr, CalcAttr> parentmap = new HashMap<>();

         for(int i = 0; i < attrs.size(); i++) {
            CalcAttr attr = attrs.get(i);
            String group = getGroup(attr);

            if(group != null) {
               CalcAttr gattr = namemap.get(group);

               if(gattr != null) {
                  addChild(gattr, attr);
                  parentmap.put(attr, gattr);
                  continue;
               }
               else if(ROOT_GROUP.equals(group)) {
                  addChild(null, attr);
                  continue;
               }
               else {
                  LOG.warn("Parent group does not exist, ignored: {}", group);
               }
            }

            // if group is not declared, fine the left/top cell as parent
            // @by jasonshobe, bug1300877438607, also add non-expanding cells
            // that are not attached to a group (e.g. summary w/o group)
            if((isExpand(attr) || isBound(attr)) && isVisible(attr.getRow(), attr.getCol())) {
               CalcAttr pattr = findDefaultGroup(attr);

               // if the default group is an explicitly declared child,
               // don't use it (recursive dependency)
               if(!isAncestor(attr, pattr, parentmap)) {
                  addChild(pattr, attr);
               }
               else {
                  addChild(null, attr); // add to root
               }
            }
         }

         return isDependOnOther(root);
      }

      /**
       * Check if ancestor is an ancestor of attr.
       */
      private boolean isAncestor(CalcAttr ancestor, CalcAttr attr,
                                 Map<CalcAttr,CalcAttr> parentmap)
      {
         if(attr == null || ancestor == null) {
            return false;
         }

         CalcAttr parent = getParent(parentmap, attr);

         while(parent != null) {
            if(parent == ancestor) {
               return true;
            }

            // prevent infinite loop
            if(parent == attr) {
               return false;
            }

            parent = getParent(parentmap, parent);
         }

         return false;
      }

      private CalcAttr getParent(Map<CalcAttr,CalcAttr> parentmap, CalcAttr attr) {
         String grp = getGroup(attr);
         CalcAttr parent = null;

         if(grp != null) {
            parent = namemap.get(grp);
         }

         if(parent == null) {
            parent = parentmap.get(attr);
         }

         return parent;
      }

      /**
       * Check if expansion depends on the other dimension.
       */
      private boolean isDependOnOther(XNode root) {
         for(int i = 0; i < root.getChildCount(); i++) {
            XNode child = root.getChild(i);
            CalcAttr attr = (CalcAttr) child.getValue();

            if(isExpandOther(attr) || isDependOnOther(child)) {
               return true;
            }
         }

         return false;
      }

      /**
       * Add a child to the tree. If the parent does not exist, it is created
       * under the root.
       * @param parent parent cell, null if adding to root.
       */
      protected void addChild(CalcAttr parent, CalcAttr child) {
         XNode pnode = null;

         // if parent group is null, use root
         if(parent == null) {
            pnode = root;
         }
         else {
            // find the parent node in the tree
            String pname = getNodeName(parent);
            pnode = findNode(root, pname);

            // if parent node does not exist, add to root
            if(pnode == null) {
               pnode = new XNode(pname);
               pnode.setValue(parent);

               root.addChild(pnode);
            }
         }

         String cname = getNodeName(child);
         XNode cnode = findNode(root, cname);

         // if child node does not exist, add to parent
         if(cnode == null) {
            cnode = new XNode(cname);
            cnode.setValue(child);
         }
         // if child node already exists, remove from the current parent and
         // add to new group
         else {
            cnode.getParent().removeChild(cnode, false);
         }

         pnode.addChild(cnode);
      }

      /**
       * Check if a cell should be processed (e.g. not in middle of a span).
       */
      protected boolean isVisible(int row, int col) {
         Rectangle span = spanmap.get(row, col);

         return span == null || span.x == 0 && span.y == 0;
      }

      /**
       * Get an unique name for a cell.
       */
      protected String getNodeName(CalcAttr attr) {
         if(attr.getCellName() != null) {
            return attr.getCellName();
         }

         return attr.getRow() + "," + attr.getCol();
      }

      /**
       * Find a node in the tree with the specified name.
       */
      private XNode findNode(XNode node, String name) {
         if(node.getName().equals(name)) {
            return node;
         }

         for(int i = 0; i < node.getChildCount(); i++) {
            XNode child = node.getChild(i);
            XNode val = findNode(child, name);

            if(val != null) {
               return val;
            }
         }

         return null;
      }

      protected XNode root = new XNode("__ROOT__");
      private Map<String, CalcAttr> namemap; // name -> CalcAttr
      private SpanMap spanmap;
   }

   /**
    * Row group tree.
    */
   private class RowGroupTree extends GroupTree {
      /**
       * Create nesting trees.
       * @param namemap a mapping from cell name to CalcAttr.
       */
      public RowGroupTree(Map<String, CalcAttr> namemap, SpanMap spanmap) {
         super(namemap, spanmap);
      }

      /**
       * Get the group name of this cell.
       */
      @Override
      protected String getGroup(CalcAttr attr) {
         String group = attr.getRowGroup();

         // setting group to itself is meaningless
         return (group == null || group.equals(attr.getCellName())) ? null : group;
      }

      /**
       * Check if this cell should expand in runtime.
       */
      @Override
      protected boolean isExpand(CalcAttr attr) {
         return attr != null && attr.getExpansion() == EXPAND_VERTICAL;
      }

      /**
       * {@inheritDoc}
       */
      @Override
      protected boolean isBound(CalcAttr attr) {
         return attr != null && attr.isBound() &&
            attr.getExpansion() == EXPAND_VERTICAL;
      }

      /**
       * Check if this cell expands in the other dimension.
       */
      @Override
      protected boolean isExpandOther(CalcAttr attr) {
         return attr != null && attr.getExpansion() == EXPAND_HORIZONTAL;
      }

      /**
       * Find the default parent group. Null if no dynamic cell exists to the
       * left or above the cell.
       */
      @Override
      protected CalcAttr findDefaultGroup(CalcAttr attr) {
         for(int i = attr.getCol() - 1; i >= 0; i--) {
            CalcAttr attr2 = getCalcAttr(attr.getRow(), i, false);

            if(isExpand(attr2)) {
               return attr2;
            }
         }

         return null;
      }
   }

   /**
    * Column group tree.
    */
   private class ColGroupTree extends GroupTree {
      /**
       * Create nesting trees.
       * @param namemap a mapping from cell name to CalcAttr.
       */
      public ColGroupTree(Map<String, CalcAttr> namemap, SpanMap spanmap) {
         super(namemap, spanmap);
      }

      /**
       * Get the group name of this cell.
       */
      @Override
      protected String getGroup(CalcAttr attr) {
         String group = attr.getColGroup();

         // setting group to itself is meaningless
         return (group == null || group.equals(attr.getCellName())) ? null : group;
      }

      /**
       * Check if this cell should expand in runtime.
       */
      @Override
      protected boolean isExpand(CalcAttr attr) {
         return attr != null && attr.getExpansion() == EXPAND_HORIZONTAL;
      }

      /**
       * Check if this cell expands in the other dimension.
       */
      @Override
      protected boolean isExpandOther(CalcAttr attr) {
         return attr != null && attr.getExpansion() == EXPAND_VERTICAL;
      }

      /**
       * {@inheritDoc}
       */
      @Override
      protected boolean isBound(CalcAttr attr) {
         return false;
      }

      /**
       * Find the default parent group. Null if no dynamic cell exists to the
       * left or above the cell.
       */
      @Override
      protected CalcAttr findDefaultGroup(CalcAttr attr) {
         for(int i = attr.getRow() - 1; i >= 0; i--) {
            CalcAttr attr2 = getCalcAttr(i, attr.getCol(), false);

            if(isExpand(attr2)) {
               return attr2;
            }
         }

         return null;
      }
   }

    /**
    * Ggroup tree with no expansion.
    */
   private class OtherGroupTree extends GroupTree {
      /**
       * Create nesting trees.
       * @param namemap a mapping from cell name to CalcAttr.
       */
      public OtherGroupTree(Map<String, CalcAttr> namemap, SpanMap spanmap) {
         super(namemap, spanmap);
      }

      /**
       * Get the group name of this cell.
       */
      @Override
      protected String getGroup(CalcAttr attr) {
         return null;
      }


      /**
       * Check if this cell should expand in runtime.
       */
      @Override
      protected boolean isExpand(CalcAttr attr) {
         return false;
      }

      /**
       * Check if this cell expands in the other dimension.
       */
      @Override
      protected boolean isExpandOther(CalcAttr attr) {
         return false;
      }

      /**
       * {@inheritDoc}
       */
      @Override
      protected boolean isBound(CalcAttr attr) {
         return false;
      }

      /**
       * Find the default parent group. Null if no dynamic cell exists to the
       * left or above the cell.
       */
      @Override
      protected CalcAttr findDefaultGroup(CalcAttr attr) {
         return null;
      }

      /**
       * Build the group tree.
       * @return true if the expension depends on the other dimension.
       */
      public void buildOtherTree(GroupTree row, GroupTree col) {
         for(int i = 0; i < attrs.size(); i++) {
            CalcAttr attr = attrs.get(i);

            XNode node  = row.findNode(row.getRoot(), getNodeName(attr));

            if(node == null) {
               node  = col.findNode(col.getRoot(), getNodeName(attr));
            }

            if(node != null) {
               continue;
            }

            addChild(null, attr);
         }
      }

      /**
       * Add a child to the tree. If the parent does not exist, it is created
       * under the root.
       * @param parent parent cell, null if adding to root.
       */
      @Override
      protected void addChild(CalcAttr parent, CalcAttr child) {
         if(elem instanceof TableElementDef) {
            int r = child.getRow();
            int c = child.getCol();

            if(!isVisible(r, c)) {
               return;
            }

            TableElementDef telem = (TableElementDef) elem;
            TableCellBinding binding = TableTool.getCellBinding(telem, r, c);

            if(binding.getType() != CellBinding.BIND_COLUMN ||
               (binding.getBType() != TableCellBinding.GROUP &&
               binding.getBType() != TableCellBinding.SUMMARY))
            {
               return;
            }
         }

         XNode pnode = root;
         String cname = getNodeName(child);
         XNode cnode = new XNode(cname);
         cnode.setValue(child);

         pnode.addChild(cnode);
      }
   }

   /**
    * Process row/column expansion.
    */
   private abstract class Processor {
      /**
       * Create a processor for expanding row or column.
       * @param root root of the group tree.
       * @param imap row/column map.
       * @param othermap index map for the other dimension.
       */
      public Processor(XNode root, RuntimeCalcTableLens.IndexMap imap,
                       RuntimeCalcTableLens.IndexMap othermap) {
         this.root = root;
         this.imap = imap;
         this.othermap = othermap;
      }

      /**
       * Check if this cell should expand in runtime.
       */
      protected abstract boolean isExpand(CalcAttr attr);

      /**
       * Get the cell row or column index.
       */
      protected abstract int getRowCol(CalcAttr attr);

      /**
       * Get the cell column or row index.
       */
      protected abstract int getColRow(CalcAttr attr);

      /**
       * Get the table column (row expansion) or row (column expansion) count.
       */
      protected abstract int getColRowCount(RuntimeCalcTableLens table);

      /**
       * Get the table column (column expansion) or row (row expansion) count.
       */
      protected abstract int getRowColCount(RuntimeCalcTableLens table);

      /**
       * Determine if the maximum number of rows or columns has been exceeded.
       */
      protected abstract boolean isLimitExceeded(RuntimeCalcTableLens table);

      /**
       * Get the values of an entire row or column.
       */
      protected abstract ValueList getValues(RuntimeCalcTableLens table,
                                             int idx);

      /**
       * Remove the specified row/column from the table.
       */
      protected abstract void removeRowCol(RuntimeCalcTableLens table, int idx,
                                           int n);

      /**
       * Set a single value in a row/column at the specified cell.
       */
      protected abstract void setRowColValue0(RuntimeCalcTableLens table,
                                              int idx, int cridx,
                                              Object value);

      /**
       * Get a single value in a row/column at the specified cell.
       */
      protected abstract Object getRowColValue0(RuntimeCalcTableLens table,
                                               int idx, int cridx);

      /**
       * Replace the children's group context with the new group.
       */
      protected abstract void replaceGroupContext(RuntimeCalcTableLens table,
                                                  int idx, CalcAttr group,
                                                  int vidx, ValueList values);

      /**
       * Insert a row or column.
       */
      protected abstract void insertRowCol(RuntimeCalcTableLens table,
                                           int idx, CalcAttr attr,
                                           ValueList[] values);

      /**
       * Copy the span setting from the cells below/right.
       */
      protected abstract void copyAttrs(RuntimeCalcTableLens table,
                                        int idx, int cnt, boolean lastone);

      /**
       * Set the row/column map to the runtime calctable.
       */
      protected abstract void setIndexMap(RuntimeCalcTableLens table);

      /**
       * Get the index (row/col) that's covered by span cells the intersects
       * with the specified row/col range.
       */
      protected abstract int[] getSpanRange(RuntimeCalcTableLens table,
                                            CalcAttr attr, int minidx,
                                            int maxidx);

      /**
       * Expansion direction for logging.
       */
      protected abstract String getExpansionType();

      /**
       * Set a single value in a row/column at the specified cell.
       */
      protected void setRowColValue(RuntimeCalcTableLens table,
                                    int idx, CalcAttr attr, Object value) {
         // see comments in getRowColValue
         Object[] arr = (value instanceof Object[]) ?
            (Object[]) value : new Object[] {value};

         // column index may have changed, and may be repeated
         int cridx = getColRow(attr);
         // offset to the row/col that belong to the same group
         int off0 = -1;
         int off1 = -1;

         for(int i = 0, vi = 0; i < othermap.size(); i++) {
            if(othermap.get(i) == cridx) {
               setRowColValue0(table, idx, i, arr[vi % arr.length]);

               // find the row/col range
               if(off0 == -1 && off1 == -1) {
                  CalcCellContext context = othermap.getCellContext(i);
                  off0 = off1 = 0;

                  // row/col with the same context are treated as belonging
                  // to the same group

                  for(int k = i - 1; k >= 0 && othermap.get(k) != cridx; k--) {
                     if(!context.equals(othermap.getCellContext(k))) {
                        break;
                     }

                     off0--;
                  }

                  for(int k = i + 1;
                      k < othermap.size() && othermap.get(k) != cridx; k++)
                  {
                     if(!context.equals(othermap.getCellContext(k))) {
                        break;
                     }

                     off1++;
                  }
               }

               String group = attr.getCellID();

               // set the value index for all the repeated row/col
               for(int k = i + off0; k < i + off1 + 1; k++) {
                  othermap.getCellContext(k).setValueIndex(group, vi);
               }

               vi++;
            }
         }
      }

      /**
       * Get a single value in a row/column at the specified cell.
       */
      protected Object getRowColValue(RuntimeCalcTableLens table,
                                      int idx, CalcAttr attr) {
         // when expanding rows, cell values may already be repeated, e.g.
         //    =[a, b] -> expand right
         //    =...group$top -> expand down
         // after col expansion it will be
         //    a      b
         //    ..@top ..@top
         // the two cells on the second row will return different values.
         // when repeating the row, we need to treat them as one.
         //
         // this is done by returning an array of array from getRowColValue.
         // for example, if the cells on the second row return [1, 2] and
         // [3, 4], the returned array will be [[1, 3], [2, 4]].
         //
         // The setRowColValue must handle this in the same manner so when
         // called by [1, 3], it will set the two cells accordingly

         List<ValueList> lists = new ArrayList<>(); // ValueList
         int max = 0;
         final int attrIndex = getColRow(attr);

         for(int i = 0; i < getColRowCount(table); i++) {
            if(othermap.get(i) == attrIndex) {
               Object obj = getRowColValue0(table, idx, i);
               ValueList vlist = getValueList(obj);

               max = Math.max(max, vlist.size());
               lists.add(vlist);
            }
         }

         // if only one column matches the attr, return a list with
         // scalar values
         if(lists.size() == 1) {
            return lists.get(0);
         }
         else if(lists.size() == 0) {
            return new Object[0];
         }

         final ValueList list0 = lists.get(0);
         // create a two dimensional array
         Object[][] arr = new Object[max][lists.size()];
         boolean identical = true;

         for(int i = 0; i < lists.size(); i++) {
            ValueList vlist = lists.get(i);

            // if size not same, must not identical
            // fix bug1399210691714
            if(identical && i > 0 && lists.get(i - 1).size() != vlist.size()) {
               identical = false;
            }

            for(int j = 0; j < vlist.size(); j++) {
               arr[j][i] = vlist.get(j);

               if(identical && i > 0 && !Tool.equals(arr[j][i], arr[j][i - 1])) {
                  identical = false;
               }
            }
         }

         // if all lists are the same, return a single list
         if(identical) {
            return lists.get(0);
         }

         return new ArrayValueList(arr) {
            // support TableValueList
            @Override
            public Object getScope(int idx) {
               return list0.getScope(idx);
            }
         };
      }

      /**
       * Perform the row/column expansion.
       */
      public void process(RuntimeCalcTableLens table) {
         // the __ROOT__ is not a real group, start from the first level child
         processChildren(table, root);
      }

      /**
       * Process and expand the children nodes.
       */
      private void processChildren(RuntimeCalcTableLens table, XNode node) {
         BitSet processed = new BitSet();

         for(int i = 0; i < node.getChildCount() && !cancelled; i++) {
            if(processed.get(i)) {
               continue;
            }

            XNode child = node.getChild(i);
            CalcAttr attr = (CalcAttr) child.getValue();
            int rowcol = getRowCol(attr);

            // find all children at the same row/column and process them in parallel
            List<XNode> siblings = new ArrayList<>();

            for(int j = i + 1; j < node.getChildCount(); j++) {
               XNode child2 = node.getChild(j);
               CalcAttr attr2 = (CalcAttr) child2.getValue();
               int rowcol2 = getRowCol(attr2);

               if(rowcol == rowcol2 && isExpand(attr2)) {
                  siblings.add(child2);
                  processed.set(j);

                  // add children of child2 to child
                  for(int k = 0; k < child2.getChildCount(); k++) {
                     child.addChild(child2.getChild(k));
                  }
               }
            }

            XNode[] nodes = siblings.toArray(new XNode[siblings.size()]);

            process0(table, child, nodes);
         }
      }

      /**
       * Get a value list from a js object.
       */
      protected ValueList getValueList(Object val) {
         val = JavaScriptEngine.unwrap(val);

         if(val instanceof ValueList) {
            return (ValueList) val;
         }

         Object[] arr = JavaScriptEngine.isArray(val) || val == null ?
            JavaScriptEngine.split(val) : new Object[] {val};

         // if a blank string is returned from the formula, it should be
         // treated as a single value instead of an empty array.
         // for a null value, it means the value is null, and not empty list.
         // only return an empty list if the original value is an array
         // @by davyc, roll back, to fix bug1289585120600,
         // and for bug1283167699460, user should maintain a formula to return
         // a null value instead
         /*
         if(arr.length == 0 && !(val instanceof Object[])) {
            arr = new Object[1];
         }
         */
         if(arr.length == 0 && val != null && !(val instanceof Object[])) {
            arr = new Object[1];
         }

         return new ArrayValueList(arr);
      }

      /**
       * Expand the row/column.
       * @param node the expansion node.
       * @param siblings the siblings at the same level and row/column, and
       * should be expanded in parallel.
       */
      private void process0(RuntimeCalcTableLens table, XNode node, XNode[] siblings) {
         XNode onode = node;
         CalcAttr attr = (CalcAttr) node.getValue();

         // @by larryl, a cell may be on the tree but is not expandable. We
         // can't skip it altogether since we need to recursively process
         // its children. Here we remove from the expansion list but will
         // still call the recursive call at the end of this function.
         while(!isExpand(attr) && siblings.length > 0) {
            XNode[] narr = new XNode[siblings.length - 1];

            System.arraycopy(siblings, 1, narr, 0, narr.length);
            node = siblings[0];
            attr = (CalcAttr) node.getValue();
            siblings = narr;
         }

         if(isExpand(attr) || siblings.length > 0) {
            @SuppressWarnings("unchecked")
            List<ValueList>[] sarrs = new List[siblings.length]; // sibling vals

            // initialize the vectors
            for(int i = 0; i < sarrs.length; i++) {
               sarrs[i] = new ArrayList<>();
            }

            int[][] poscount = expand(table, node, siblings, sarrs);

            // replace the sibling values in the expanded row/column for
            // parallel expansion
            for(int i = 0; i < siblings.length && !cancelled; i++) {
               replace(table, siblings[i], sarrs[i], poscount);
            }
         }

         if(LOG.isDebugEnabled()) {
            int ncnt = getRowColCount(table);

            if(ncnt > lastRowColCnt + 50000) {
               LOG.debug("Expanded to: " + ncnt + " " + getExpansionType());
               lastRowColCnt = ncnt;
            }
         }

         processChildren(table, onode);
      }

      /**
       * Expand into a new row/column for each value in the array.
       * @param sarrs this is an array of vector. Each vector contains the
       * ValueList for each sibling.
       * @return the index where the lists are expanded, and the number of
       * row/columns expanded into.
       */
      private int[][] expand(RuntimeCalcTableLens table, XNode node,
                             XNode[] siblings, List<ValueList>[] sarrs) {
         CalcAttr attr = (CalcAttr) node.getValue();
         int orig = getRowCol(attr);
         List<Integer> expanded = new ArrayList<>(); // expansion index
         List<Integer> counts = new ArrayList<>();   // expansion count
         // ValueList, cached value lists for expanding cells
         List<ValueList> arrvec = new ArrayList<>();

         // optimization, we first get all value lists for expanding in arrvec.
         // When we expand in the next loop, we don't need to call
         // getRowColValue(). This is done to avoid repeated initializing the
         // runtime (e.g. CalcCellMap). Since we are expand cells at the same
         // level, all values can be calculated at this point. If we get
         // one value and then immediately expand, the cached mapping will be
         // invalidated after the expansion. When we request the next value,
         // the runtime will need to be initialized again. By doing it in two
         // phases, we ensure all values are calculated with 1 initialization.

         for(int i = 0; i < imap.size(); i++) {
            if(imap.get(i) == orig) {
               Object val = getRowColValue(table, i, attr);
               ValueList arr = getValueList(val);
               int smax = 0; // max length of sibling lists

               for(int k = 0; k < siblings.length; k++) {
                  CalcAttr attr2 = (CalcAttr) siblings[k].getValue();
                  val = getRowColValue(table, i, attr2);

                  ValueList sibValues = getValueList(val);

                  sarrs[k].add(sibValues);
                  smax = Math.max(sibValues.size(), smax);
               }

               // make sure the main list is longer or equal to sibling lists
               if(smax > arr.size()) {
                  arr.setSize(smax);
               }

               arrvec.add(arr);
            }
         }

         // see above
         for(int i = 0, k = 0; i < imap.size() && !cancelled; i++) {
            if(imap.get(i) == orig) {
               ValueList arr = arrvec.get(k++);
               int n = expand0(table, i, node, arr);
               expanded.add(i);
               counts.add(arr.size());

               // if rows removed, start at the same point (++ later)
               if(n < 0) {
                  i--;
               }
               else {
                  i += n;
               }
            }
         }

         int[][] arr = new int[2][expanded.size()];

         for(int i = 0; i < arr[0].length; i++) {
            arr[0][i] = expanded.get(i);
            arr[1][i] = counts.get(i);
         }

         return arr;
      }

      /**
       * Expand into a new row/column for each value in the array.
       * @return number of rows expanded. If one row is expanded into 3 rows,
       * it returns 2 since there are two additional rows (the existing row
       * is replaced by the first value on the list).
       */
      private int expand0(RuntimeCalcTableLens table, int idx, XNode node, ValueList values) {
         CalcAttr attr = (CalcAttr) node.getValue();
         int[] minmax = getChildRange(table, node, idx);
         int idx0 = minmax[0]; // get left-top most child
         int idx1 = minmax[1]; // get right-bottom most child
         ValueList[] data = new ValueList[idx1 - idx0 + 1];

         // make a copy of the repeating contents
         for(int i = 0; i < data.length; i++) {
            data[i] = getValues(table, i + idx0);
         }

         int[] omap = new int[idx1 - idx0 + 1]; // original mapping
         int count = 0;

         // build the row/col mapping
         for(int i = idx0; i <= idx1; i++) {
            omap[i - idx0] = imap.get(i);
         }

         // repeat the rows/columns for each value
         for(int i = 0; i < values.size() && !cancelled && !isLimitExceeded(table); i++) {
            // create a new context for this group
            CalcCellContext[] contexts2 = new CalcCellContext[idx1 - idx0 + 1];

            for(int k = 0; k < contexts2.length; k++) {
               contexts2[k] = (CalcCellContext) imap.getCellContext(idx0 + k).clone();
               contexts2[k].addGroup(attr.getCellID(), i, values);
            }

            int cnt = repeatCell(table, idx, attr, values.get(i), idx0,
                                 data, omap, contexts2, i == values.size() - 1);
            idx += cnt;
            idx0 += cnt;
            idx1 += cnt;
            count += cnt;
         }

         // remove the repeating contents after repeating them. This way the
         // span info will be correctly handled by the insertRow/Col
         for(int i = idx0; i <= idx1; i++) {
            imap.remove(idx0); // remove row index should not increase
         }

         removeRowCol(table, idx0, idx1 - idx0 + 1);
         count -= idx1 - idx0 + 1;

         // set the row/col map so references used during expansion would work
         setIndexMap(table);

         return count;
      }

      /**
       * Replace the values in already expanded row/colum with the list values.
       * @param vlist ValueLists for each expansion of a sibling.
       * @param poscount the indices of inserting points, and the number of
       * row/column to replace the values, poscount[0] and poscount[1]
       * respectively.
       */
      private void replace(RuntimeCalcTableLens table, XNode node,
                           List<ValueList> vlist, int[][] poscount) {
         CalcAttr attr = (CalcAttr) node.getValue();
         int orig = getRowCol(attr);
         int[] pos = poscount[0];
         int[] count = poscount[1];

         for(int i = 0; i < pos.length; i++) {
            ValueList values = vlist.get(i);
            int n = Math.max(count[i], values.size());
            int row = pos[i];

            for(int j = 0; j < n; j++) {
               Object value = (j < values.size()) ? values.get(j) : null;

               // @by davyc, replace value at correct position
               // fix bug1290670866567
               for(int r = row; r < imap.size() && !cancelled; r++) {
                  if(imap.get(r) == orig) {
                     setRowColValue(table, r, attr, value);
                     replaceGroupContext(table, r, attr, j, values);
                     row = r + 1;
                     break;
                  }
               }
            }
         }
      }

      /**
       * Repeat the range of row/column [min-max] for a group value.
       * @param idx the repeating row's index in the runtime table.
       * @param value the value in the list that is used to repeat the row/col.
       * @param idx0 the starting position of the repeating row/col.
       * @param data the repeating rows/columns original data.
       */
      private int repeatCell(RuntimeCalcTableLens table, int idx,
                             CalcAttr attr, Object value, int idx0,
                             ValueList[] data, int[] omap,
                             CalcCellContext[] context,
                             boolean lastone)
      {
         for(int i = 0; i < data.length; i++) {
            imap.insert(i + idx0, omap[i], context[i]);
         }

         insertRowCol(table, idx0, attr, data);

         // set the value only on the main expanding row
         setRowColValue(table, idx, attr, value);

         // copy the spans from the original row/column (below/right)
         copyAttrs(table, idx0, data.length, lastone);

         return data.length;
      }

      /**
       * Get the minimum/maximum index of all child cells.
       * @param node the expanding node.
       * @param idx the current row/col index
       * @return the [min, max] of row/col index of child cells in the runtime
       * table.
       */
      private int[] getChildRange(RuntimeCalcTableLens table, XNode node, int idx) {
         // oidx is the index of the node in the original table
         int oidx = getRowCol((CalcAttr) node.getValue());
         // min, max, and idx are all index in the runtime table
         int min = idx;
         int max = idx;

         for(int i = 0; i < node.getChildCount() && !isLimitExceeded(table); i++) {
            XNode child = node.getChild(i);
            CalcAttr childAttr = (CalcAttr) child.getValue();
            int childOidx = getRowCol(childAttr);
            int childIdx = idx;

            // find the index in the runtime table from the original index
            if(childOidx != oidx) {
               int inc = childOidx > oidx ? 1 : -1;
               childIdx = imap.getReverse(childOidx, idx + inc, inc);
            }

            int[] minmax = getChildRange(table, child, childIdx);

            min = Math.min(min, minmax[0]);
            max = Math.max(max, minmax[1]);
         }

         return getSpanRange(table, (CalcAttr) node.getValue(), min, max);
      }

      private XNode root;
      protected RuntimeCalcTableLens.IndexMap imap;
      protected RuntimeCalcTableLens.IndexMap othermap;
      protected int lastRowColCnt = 0;
   }

   /**
    * Process row expansion.
    */
   private class RowProcessor extends Processor {
      /**
       * Create a processor for expanding row or column.
       * @param root root of the group tree.
       * @param imap row/column map.
       */
      public RowProcessor(XNode root, RuntimeCalcTableLens.IndexMap imap,
                          RuntimeCalcTableLens.IndexMap rowMap) {
         super(root, imap, rowMap);
      }

      /**
       * Check if this cell should expand in runtime.
       */
      @Override
      protected boolean isExpand(CalcAttr attr) {
         return attr.getExpansion() == EXPAND_VERTICAL;
      }

      /**
       * Get the cell row or column index.
       */
      @Override
      protected int getRowCol(CalcAttr attr) {
         return attr.getRow();
      }

      /**
       * Get the cell column or row index.
       */
      @Override
      protected int getColRow(CalcAttr attr) {
         return attr.getCol();
      }

      /**
       * Get the table row or column count.
       */
      @Override
      protected int getColRowCount(RuntimeCalcTableLens table) {
         return table.getColCount();
      }

      @Override
      protected int getRowColCount(RuntimeCalcTableLens table) {
         return table.getRowCount();
      }

      @Override
      protected boolean isLimitExceeded(RuntimeCalcTableLens table) {
         int rows = table.getRowCount();

         if(rows < 0) {
            rows = Math.abs(rows + 1);
         }

         int maxRows = Util.getRuntimeMaxRows();

         if(maxRows <= 0 || rows <= maxRows) {
            return false;
         }
         else {
            appliedMaxRows = rows;
            return true;
         }
      }

      /**
       * Get the values of an entire row or column.
       */
      @Override
      protected ValueList getValues(RuntimeCalcTableLens table, int idx) {
         TableLens lens = table.getTable();
         Object[] row = new Object[lens.getColCount()];

         for(int i = 0; i < row.length; i++) {
            row[i] = lens.getObject(idx, i);
         }

         return new ArrayValueList(row);
      }

      /**
       * Remove the specified row/column from the table.
       */
      @Override
      protected void removeRowCol(RuntimeCalcTableLens table, int idx, int n) {
         table.removeRow(idx, n);
      }

      /**
       * Set a single value in a row/column at the specified cell.
       */
      @Override
      protected void setRowColValue0(RuntimeCalcTableLens table,
                                     int idx, int cridx, Object value) {
         table.setObject(idx, cridx, value);
      }

      /**
       * Get a single value in a row/column at the specified cell.
       */
      @Override
      protected Object getRowColValue0(RuntimeCalcTableLens table, int idx, int cridx) {
         return table.getObject(idx, cridx);
      }

      /**
       * Replace the children's group context with the new group.
       */
      @Override
      protected void replaceGroupContext(RuntimeCalcTableLens table, int idx,
                                         CalcAttr group, int vidx,
                                         ValueList values) {
         int col = othermap.getReverse(group.getCol());

         if(col >= 0) {
            CalcCellContext context = imap.getCellContext(idx);
            context.addGroup(group.getCellID(), vidx, values);
         }
      }

      /**
       * Insert a row or column.
       */
      @Override
      protected void insertRowCol(RuntimeCalcTableLens table, int idx,
                                  CalcAttr attr, ValueList[] values) {
         table.insertRow(idx, values.length);

         for(int i = 0; i < values.length; i++) {
            for(int j = 0; j < values[i].size(); j++) {
               Object val = values[i].get(j);
               table.setObject(i + idx, j, val);
            }
         }
      }

      /**
       * Copy the span setting from the cells below/right.
       */
      @Override
      protected void copyAttrs(RuntimeCalcTableLens table, int idx, int cnt,
                               boolean lastone) {
         int ncols = table.getColCount();

         for(int i = idx; i < idx + cnt; i++) {
            for(int j = 0; j < ncols; j++) {
               Dimension span2 = table.getSpan(i, j);
               Dimension span = table.getSpan(i + cnt, j);

               if(span2 == null && span != null) {
                  // if the span of the original columns is outside of the
                  // repeating column range, we need to make the copied span
                  // to cover the newly inserted cell and stretch to the end
                  // of the original span
                  if(span.height > cnt) {
                     span = new Dimension(span.width, span.height + cnt);
                     // @by davyc, the below span will be convered by current
                     // span, so here just clear the below span directly,
                     // to reduce span map size, otherwise span map will be out
                     // of space(SparseIndexedMatrix)
                     // fix bug1295861518462, bug1295851727740
                     table.setSpan(i + cnt, j, null);
                  }

                  table.setSpan(i, j, span);
               }

               // do not copy default attribute, for it might generate very
               // very huge SparseIndexedMatrix. If that, it might cause
               // serious performance or memory problem

               int border = table.getRowBorder(i + cnt, j);

               if(border != StyleConstants.THIN_LINE) {
                  table.setRowBorder(i, j, border);
               }

               border = table.getColBorder(i + cnt, j);

               if(border != StyleConstants.THIN_LINE) {
                  table.setColBorder(i, j, border);
               }

               Color color = table.getRowBorderColor(i + cnt, j);

               if(color != Color.black) {
                  table.setRowBorderColor(i, j, color);
               }

               color = table.getColBorderColor(i + cnt, j);

               if(color != Color.black) {
                  table.setColBorderColor(i, j, color);
               }

               int align = table.getAlignment(i + cnt, j);

               if(align != (H_LEFT | V_CENTER)) {
                  table.setAlignment(i, j, align);
               }

               table.setFont(i, j, table.getFont(i + cnt, j));

               boolean wrap = table.isLineWrap(i + cnt, j);

               if(!wrap) {
                  table.setLineWrap(i, j, wrap);
               }

               table.setForeground(i, j, table.getForeground(i + cnt, j));
               table.setBackground(i, j, table.getBackground(i + cnt, j));
            }

            // copy row height
            int w = table.getRowHeight(i + cnt);

            // By Klause, Bug #10953, 2016-4-15.
            // The negative row height should set to the table, otherwise get
            // row height always return -1. The document said:
            // A value of -1 specifies automatic row height calculation.
            // A negative integer value (other than -1) specifies a fixed
            // minimum row height in pixels. They are different.
            //if(w >= 0) {
               table.setRowHeight(i, w);
            //}
         }
      }

      /**
       * Set the row/column map to the runtime calctable.
       */
      @Override
      protected void setIndexMap(RuntimeCalcTableLens table) {
         table.setRowMap(imap);
      }

      /**
       * Get the index (row/col) that's covered by span cells the intersects
       * with the specified row/col range.
       */
      @Override
      protected int[] getSpanRange(RuntimeCalcTableLens table, CalcAttr attr,
                                   int minidx, int maxidx) {
         int idx = othermap.getReverse(attr.getCol());

         if(idx >= 0) {
            for(int i = minidx; i <= maxidx; i++) {
               Dimension span = table.getSpan(i, idx);

               if(span != null) {
                  maxidx = Math.max(maxidx, i + span.height - 1);
               }
            }
         }

         return new int[] {minidx, maxidx};
      }

      @Override
      protected String getExpansionType() {
         return "row";
      }
   }

   /**
    * Process column expansion.
    */
   private class ColProcessor extends Processor {
      /**
       * Create a processor for expanding row or column.
       * @param root root of the group tree.
       * @param imap row/column map.
       */
      public ColProcessor(XNode root, RuntimeCalcTableLens.IndexMap imap,
                          RuntimeCalcTableLens.IndexMap rowmap) {
         super(root, imap, rowmap);
      }

      /**
       * Check if this cell should expand in runtime.
       */
      @Override
      protected boolean isExpand(CalcAttr attr) {
         return attr.getExpansion() == EXPAND_HORIZONTAL;
      }

      /**
       * Get the cell row or column index.
       */
      @Override
      protected int getRowCol(CalcAttr attr) {
         return attr.getCol();
      }

      /**
       * Get the cell column or row index.
       */
      @Override
      protected int getColRow(CalcAttr attr) {
         return attr.getRow();
      }

      /**
       * Get the table row or column count.
       */
      @Override
      protected int getColRowCount(RuntimeCalcTableLens table) {
         return table.getRowCount();
      }

      @Override
      protected int getRowColCount(RuntimeCalcTableLens table) {
         return table.getColCount();
      }

      @Override
      protected boolean isLimitExceeded(RuntimeCalcTableLens table) {
         return false;
      }

      /**
       * Get the values of an entire row or column.
       */
      @Override
      protected ValueList getValues(RuntimeCalcTableLens table, int idx) {
         TableLens lens = table.getTable();
         Object[] row = new Object[lens.getRowCount()];

         for(int i = 0; i < row.length; i++) {
            row[i] = lens.getObject(i, idx);
         }

         return new ArrayValueList(row);
      }

      /**
       * Remove the specified row/column from the table.
       */
      @Override
      protected void removeRowCol(RuntimeCalcTableLens table, int idx, int n) {
         table.removeColumn(idx, n);
      }

      /**
       * Set a single value in a row/column at the specified cell.
       */
      @Override
      protected void setRowColValue0(RuntimeCalcTableLens table,
                                     int idx, int cridx, Object value) {
         table.setObject(cridx, idx, value);
      }

      /**
       * Get a single value in a row/column at the specified cell.
       */
      @Override
      protected Object getRowColValue0(RuntimeCalcTableLens table, int idx, int cridx) {
         return table.getObject(cridx, idx);
      }

      /**
       * Replace the children's group context with the new group.
       */
      @Override
      protected void replaceGroupContext(RuntimeCalcTableLens table, int idx,
                                         CalcAttr group, int vidx,
                                         ValueList values) {
         int row = othermap.getReverse(group.getRow());

         if(row >= 0) {
            CalcCellContext context = imap.getCellContext(idx);
            context.addGroup(group.getCellID(), vidx, values);
         }
      }

      /**
       * Insert a row or column.
       */
      @Override
      protected void insertRowCol(RuntimeCalcTableLens table, int idx,
                                  CalcAttr attr, ValueList[] values) {
         table.insertColumn(idx, values.length);

         for(int i = 0; i < values.length; i++) {
            for(int j = 0; j < values[i].size(); j++) {
               Object val = values[i].get(j);
               table.setObject(j, i + idx, val);
            }
         }
      }

      /**
       * Copy the span setting from the cells below/right.
       */
      @Override
      protected void copyAttrs(RuntimeCalcTableLens table, int idx, int cnt, boolean lastone) {
         for(int i = idx; i < idx + cnt; i++) {
            for(int j = 0; j < table.getRowCount(); j++) {
               Dimension span2 = table.getSpan(j, i);
               Dimension span = table.getSpan(j, i + cnt);

               if(span2 == null && span != null) {
                  // if the span of the original columns is outside of the
                  // repeating column range, we need to make the copied span
                  // to cover the newly inserted cell and stretch to the end
                  // of the original span
                  if(span.width > cnt) {
                     span = new Dimension(span.width + cnt, span.height);
                     table.setSpan(j, i + cnt, null);
                  }

                  table.setSpan(j, i, span);
               }

               // do not copy default attribute, for it might generate very
               // very huge SparseIndexedMatrix. If that, it might cause
               // serious performance or memory problem

               int border = table.getRowBorder(j, i + cnt);

               if(border != StyleConstants.THIN_LINE) {
                  table.setRowBorder(j, i, border);
               }

               border = table.getColBorder(j, i + cnt);

               if(border != StyleConstants.THIN_LINE) {
                  table.setColBorder(j, i, border);
               }

               Color color = table.getRowBorderColor(j, i + cnt);

               if(color != Color.black) {
                  table.setRowBorderColor(j, i, color);
               }

               color = table.getColBorderColor(j, i + cnt);

               if(color != Color.black) {
                  table.setColBorderColor(j, i, color);
               }

               int align = table.getAlignment(j, i + cnt);

               if(align != (H_LEFT | V_CENTER)) {
                  table.setAlignment(j, i, align);
               }

               table.setFont(j, i, table.getFont(j, i + cnt));

               boolean wrap = table.isLineWrap(j, i + cnt);

               if(!wrap) {
                  table.setLineWrap(j, i, wrap);
               }

               table.setForeground(j, i, table.getForeground(j, i + cnt));
               table.setBackground(j, i, table.getBackground(j, i + cnt));
            }

            // copy column width
            int w = table.getColWidth(i + cnt);

            if(w >= 0) {
               table.setColWidth(i, table.getColWidth(i + cnt));
            }
         }
      }

      /**
       * Set the row/column map to the runtime calctable.
       */
      @Override
      protected void setIndexMap(RuntimeCalcTableLens table) {
         table.setColMap(imap);
      }

      /**
       * Get the index (row/col) that's covered by span cells the intersects
       * with the specified row/col range.
       */
      @Override
      protected int[] getSpanRange(RuntimeCalcTableLens table, CalcAttr attr,
                                   int minidx, int maxidx) {
         int idx = othermap.getReverse(attr.getRow());

         if(idx >= 0) {
            for(int i = minidx; i <= maxidx; i++) {
               Dimension span = table.getSpan(idx, i);

               if(span != null) {
                  maxidx = Math.max(maxidx, i + span.width - 1);
               }
            }
         }

         return new int[] {minidx, maxidx};
      }

      @Override
      protected String getExpansionType() {
         return "column";
      }
   }

   /**
    * Clear all page breaks.
    */
   public void clearPageBreaks() {
      for(int i = 0; i < attrs.size(); i++) {
         CalcAttr attr = attrs.get(i);
         attr.setPageAfter(false);
      }
   }

   /**
    * @return the report/vs name which this filter was created for,
    * and will be used when insert audit record.
    */
   @Override
   public String getReportName() {
      String name = super.getReportName();
      name = name != null ? name : table == null ? null : table.getReportName();
      return name != null ? name : AuditRecordUtils.getObjectName(report);
   }

   /**
    * @return the report type which this filter was created for:
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    */
   @Override
   public String getReportType() {
      String type = super.getReportType();
      type = type != null ? type : table == null ? null : table.getReportType();
      return type != null ? type : AuditRecordUtils.getObjectType(report);
   }

   /**
    * Get the table used for getting data in scripts.
    */
   public TableLens getDataTable() {
      return dataTable;
   }

   /**
    * Set the table used for getting data in scripts.
    */
   public void setDataTable(TableLens dataTable) {
      this.dataTable = dataTable;
   }

   public int getAppliedMaxRows() {
      if(appliedMaxRows < 0) {
         return Util.getAppliedMaxRows(getScriptTable());
      }

      return appliedMaxRows;
   }

   private static final class CalcAttrComp implements Comparator<CalcAttr> {
      @Override
      public int compare(CalcAttr o1, CalcAttr o2) {
         return o1.compareTo(o2);
      }
   }

   private List<CalcAttr> attrs = new ArrayList<>();
   private int editMode = DEFAULT_MODE; // DEFAULT_MODE, NAME_MODE,FORMULA_MODE
   private TableDataDescriptor cdescriptor;
   private final byte[] descLock = new byte[0];
   protected FormulaTable elem; //containing element
   private ReportSheet report;
   private CalcTableScope tableScope;
   private TableLens data;
   private SpanMap spanMap;
   private final Object spanMapLock = new byte[0];
   private transient SparseMatrix formulaCache = null; // formula result cache
   private transient boolean cancelled = false;
   private transient TableLens dataTable;
   private boolean fillwithzero = false;
   private int appliedMaxRows = -1;

   private static final ThreadLocal<IntStack> row = new ThreadLocal<>();
   private static final ThreadLocal<IntStack> col = new ThreadLocal<>();
   private static final ScriptCache scriptCache = new ScriptCache(100, 60000);
   private static final Logger LOG = LoggerFactory.getLogger(CalcTableLens.class);
}
