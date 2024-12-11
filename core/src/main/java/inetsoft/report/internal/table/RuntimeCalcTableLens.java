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
package inetsoft.report.internal.table;

import inetsoft.report.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.TopNInfo;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The RuntimeCalcTableLens is the expanded calc table. It is only used during
 * runtime to handle the execution of calc table formulas.
 *
 * @version 7.0, 2/20/2005
 * @author InetSoft Technology Corp
 */
public class RuntimeCalcTableLens extends CalcTableLens implements MappedTableLens {
   /**
    * Create a copy of a table lens.
    */
   public RuntimeCalcTableLens(CalcTableLens lens) {
      super(lens, true);
      lens.cloneAttributes(this);
      setFillBlankWithZero(lens.isFillBlankWithZero());
      this.calc = lens;
   }

   /**
    * Get the calc table this runtime table is created from.
    */
   public CalcTableLens getCalcTableLens() {
      return calc;
   }

   /**
    * Get all cell names (name set through setCellName()).
    */
   @Override
   public String[] getCellNames() {
      return calc.getCellNames();
   }

   @Override
   public CalcAttr getCalcAttr(int row, int col) {
      return calc.getCalcAttr(row, col);
   }

   @Override
   public void addCalcAttr(CalcAttr attr) {
      calc.addCalcAttr(attr);
   }

   /**
    * Get the cell location with the specified cell name.
    * @param name The name of the cell
    * @return cell location where name is the cell name.
    */
   public Point getCellLocation(String name) {
      return calc.getCellLocation(name);
   }

   /**
    * Return the data at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the data at the specified cell.
    */
   @Override
   public Object getData(int r, int c) {
      Object obj = super.getData(r, c);

      if(obj instanceof Formula) {
         obj = getObject(r, c);
      }

      return obj;
   }

   // @by larryl, for runtime calc table, we don't need to keep the formula
   // objects in the default table. Instead of using a separate cache for
   // formula, we replace the formula objects in the original table to conserve
   // memory.

   /**
    * Get cached cell value.
    */
   @Override
   protected Object getCachedValue(int r, int c) {
      Object val = super.getData(r, c);

      return (val instanceof Formula) ? SparseMatrix.NULL : val;
   }

   /**
    * Set the cached value.
    */
   @Override
   protected void setCachedValue(int r, int c, Object obj) {
      // optimization, setting cached value should not trigger change event
      eventEnabled = false;

      setObject(r, c, obj);

      eventEnabled = true;
   }

   @Override
   public void setXMetaInfo(int row, int col, XMetaInfo minfo) {
      mmap.put(getDescriptor().getCellDataPath(row, col), minfo);
   }

   /**
    * Set the meta info for cells.
    */
   public void setXMetaInfo(TableDataPath path, XMetaInfo minfo) {
      mmap.put(path, minfo);
   }

   /**
    * Fire change event when filtered table changed.
    */
   @Override
   protected void fireChangeEvent() {
      if(eventEnabled) {
         super.fireChangeEvent();
      }
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public synchronized TableDataDescriptor getDescriptor() {
      if(cdescriptor == null) {
         cdescriptor = new RuntimeCalcTableDataDescriptor(calc.getDescriptor());
      }

      return cdescriptor;
   }

   public XFormatInfo createXFormatInfo(int layoutR, int layoutC) {
      RuntimeCalcTableDataDescriptor desc =
         (RuntimeCalcTableDataDescriptor) getDescriptor();
      return desc.createXFormatInfo(layoutR, layoutC);
   }

   /**
    * Set the row mapping from this table to the original calc table.
    */
   public void setRowMap(IndexMap rowmap) {
      this.rowmap = rowmap;

      if(cellmap != null) {
         cellmap.reset();
      }

      if(!this.mergeCellCache.isEmpty()) {
         this.mergeCellCache.clear();
      }
   }

   /**
    * Set the column mapping from this table to the original calc table.
    */
   public void setColMap(IndexMap colmap) {
      this.colmap = colmap;

      if(cellmap != null) {
         cellmap.reset();
      }

      if(!this.mergeCellCache.isEmpty()) {
         this.mergeCellCache.clear();
      }
   }

   /**
    * This method is called after the processing is done.
    */
   public void complete() {
      postProcess();

      // clean up cached data
      if(cellmap != null) {
         cellmap.clearCache();
      }
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      return super.getAlignment(getRow(r), getCol(c));
   }

   /**
    * Handle the cell merging and page after.
    */
   private void postProcess() {
      Set merged = new HashSet();
      int nrow = getRowCount();
      int ncol = getColCount();
      // @optimize
      Map<Point, Boolean> pageAfters = new HashMap<>();
      Map<Point, Boolean> merges = new HashMap<>();
      calc.retrieve(pageAfters, merges);
      boolean hasPageAfter = pageAfters.size() > 0;
      boolean hasMerge = merges.size() > 0;
      final Map<String, Point> pmap = new HashMap<>();
      final Point ppoint = new Point(-1, -1);

      // processing cell merging
      for(int i = 0; i < nrow; i++) {
         for(int j = 0; j < ncol; j++) {
            int row = getRow(i);
            int col = getCol(j);
            int pageAfterRow = row;
            boolean rowBreakEnable = false;

            for(int index = row; index >=0; index--) {
               if(pageAfters.containsKey(new Point(col, index))) {
                  pageAfterRow = index;
                  rowBreakEnable = true;

                  break;
               }
            }

            String cid0 = calc.getRowGroup(pageAfterRow, col) == null ?
               calc.getCellID(pageAfterRow, col) : calc.getRowGroup(pageAfterRow, col);
            CalcCellContext context01 = rowmap.getCellContext(i);
            CalcCellContext.Group group01 = context01.getGroup(cid0);

            if(group01 == null) {
               rowBreakEnable = pageAfters.containsKey(new Point(col, row));
               pageAfterRow = row;
            }

            // check for page after
            if(hasPageAfter && i < nrow - 1 && rowBreakEnable) {
               String cid = calc.getRowGroup(pageAfterRow, col);

               if(cid == null) {
                  cid = calc.getCellID(pageAfterRow, col);
               }

               CalcCellContext context0 = rowmap.getCellContext(i);
               CalcCellContext.Group group0 = context0.getGroup(cid);

               // if the cell is not a group, we check the next value of the
               // cell. If it's different, we add a pagebreak, so this behavior
               // is consistent with group cells.
               if(group0 == null) {
                  // limit look ahead for performance
                  int lookahead = Math.min(nrow, i + 300);
                  Object val = null;
                  boolean found = false;

                  for(int k = i + 1; k < lookahead; k++) {
                     if(getRow(k) == pageAfterRow) {
                        found = true;
                        val = getObject(k, j);
                        break;
                     }
                  }

                  if(!found || found && !Tool.equals(val, getObject(i, j))) {
                     setRowBorder(i, 0, getRowBorder(i, 0) | TableLens.BREAK_BORDER);
                  }
               }
               else {
                  CalcCellContext context1 = rowmap.getCellContext(i + 1);
                  CalcCellContext.Group group1 = context1.getGroup(cid);

                  // we allow non-expanding cell to break as well
                  if(group1 == null ||
                     group0.getPosition() != group1.getPosition())
                  {
                     setRowBorder(i, 0, getRowBorder(i, 0) | TableLens.BREAK_BORDER);
                  }
               }
            }

            // already merged
            if(merged.contains(new Point(j, i))) {
               continue;
            }

            // merge cells
            if(hasMerge && merges.containsKey(new Point(col, row))) {
               Dimension span = calc.getSpan(row, col);
               int spanH = (span == null) ? 1 : span.height;
               int spanW = (span == null) ? 1 : span.width;

               {//Row
                  CalcCellContext context0 = rowmap.getCellContext(i);
                  String gname = calc.getMergeRowGroup(row, col);

                  ArrayList groups = new ArrayList();
                  ArrayList gnames = new ArrayList();

                  if(gname != null && context0 != null) {
                     String rgrp = calc.getRowGroup(row, col);

                     groups.add(0, context0.getGroup(gname));
                     gnames.add(0, gname);

                     while(rgrp != null && !gnames.contains(rgrp)) {
                        groups.add(0, context0.getGroup(rgrp));
                        gnames.add(0, rgrp);
                        Point p = pmap.get(rgrp);

                        if(p == null) {
                           p = calc.getCellLocation(rgrp);
                           pmap.put(rgrp, p == null ? ppoint : p);
                        }
                        else {
                           p = p == ppoint ? null : p;
                        }

                        if(p == null) {
                           break;
                        }

                        rgrp = calc.getRowGroup(p.y, p.x);
                     }
                  }

                  int inc = (span == null) ? 1 : span.height;

                  // find the merging range
                  // don't increment by inc since the cell may not be expanded
                  // in multiples of span if there are nested sub-groups
                  for(int r = i + inc; r < getRowCount(); r += 1) {
                     if(getRow(r) != row && getRow(r) >= row + inc) {
                        break;
                     }

                     if(groups.size() > 0) {
                        CalcCellContext context = rowmap.getCellContext(r);

                        if(context != null) {
                           ArrayList group2s = new ArrayList();

                           for(int g = 0; g < gnames.size(); g++) {
                              String s = (String) gnames.get(g);
                              group2s.add(context.getGroup(s));
                           }

                           boolean equals = true;

                           for(int g = 0; g < groups.size(); g++) {
                              CalcCellContext.Group group =
                                 (CalcCellContext.Group) groups.get(g);

                              CalcCellContext.Group group2 =
                                 (CalcCellContext.Group) group2s.get(g);

                              if(group == null && group2 == null) {
                                 continue;
                              }

                              if(group == null || group2 == null ||
                                 !Tool.equals(group.getValue(context),
                                              group2.getValue(context)))
                              {
                                 equals = false;
                                 break;
                              }
                           }

                           if(!equals) {
                              break;
                           }
                        }
                     }

                     spanH = Math.max(spanH, r - i + 1);
                  }
               }

               {///Column
                  CalcCellContext context0 = colmap.getCellContext(j);
                  String gname = calc.getMergeColGroup(row, col);

                  ArrayList groups = new ArrayList();
                  ArrayList gnames = new ArrayList();

                  if(gname != null && context0 != null) {
                     String rgrp = calc.getColGroup(row, col);
                     groups.add(0, context0.getGroup(gname));
                     gnames.add(0, gname);

                     while(rgrp != null && !gnames.contains(rgrp)) {
                        groups.add(0, context0.getGroup(rgrp));
                        gnames.add(0, rgrp);
                        Point p = pmap.get(rgrp);

                        if(p == null) {
                           p = calc.getCellLocation(rgrp);
                           pmap.put(rgrp, p == null ? ppoint : p);
                        }
                        else {
                           p = p == ppoint ? null : p;
                        }

                        if(p == null) {
                           break;
                        }

                        rgrp = calc.getColGroup(p.y, p.x);
                     }
                  }

                  int inc = (span == null) ? 1 : span.width;

                  for(int c = j + inc; c < getColCount(); c += 1) {
                     if(getCol(c) != col && getCol(c) >= col + inc) {
                        break;
                     }

                     if(groups.size() > 0) {
                        CalcCellContext context = colmap.getCellContext(c);

                        if(context != null) {
                           ArrayList group2s = new ArrayList();

                           for(int g = 0; g < gnames.size(); g++) {
                              String s = (String) gnames.get(g);
                              group2s.add(context.getGroup(s));
                           }

                           boolean equals = true;

                           for(int g = 0; g < groups.size(); g++) {
                              CalcCellContext.Group group =
                                 (CalcCellContext.Group) groups.get(g);

                              CalcCellContext.Group group2 =
                                 (CalcCellContext.Group) group2s.get(g);

                              if(group == null && group2 == null) {
                                 continue;
                              }

                              if(group == null || group2 == null ||
                                 !Tool.equals(group.getValue(context),
                                              group2.getValue(context))) {
                                 equals = false;
                                 break;
                              }
                           }

                           if(!equals) {
                              break;
                           }
                        }
                     }

                     spanW = Math.max(spanW, c - j + 1);
                  }
               }

               if(spanW > 1 || spanH > 1) {
                  Dimension span0 = getSpan(i, j);

                  if(span0 != null) {
                     spanW = Math.max(spanW, span0.width);
                     spanH = Math.max(spanH, span0.height);
                  }

                  setSpan(i, j, new Dimension(spanW, spanH));

                  for(int k = i; k < i + spanH; k++) {
                     for(int m = j; m < j + spanW; m++) {
                        merged.add(new Point(m, k));
                     }
                  }
               }
            }
            // finished merge cell handling
         }
      }
   }

   /**
    * Get the previous logical row index. The definition of logical row
    * depends on table filter. For example, the freehand logical row is a
    * region. The CalcRuntimeTableLens's logical row is the row that maps
    * to the same original row before the expansion.
    */
   @Override
   public int getLastLogicalRow(int row) {
      int orow = rowmap.get(row);

      for(int i = row - 1; i >= 0; i--) {
         if(rowmap.get(i) == orow) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Get the generated cell context.
    */
   public CalcCellContext getCellContext(int row, int col) {
      if(rowmap == null || rowmap.getCellContext(row) == null) {
         if(colmap != null) {
            return colmap.getCellContext(col);
         }
      }
      else if(colmap == null || colmap.getCellContext(col) == null) {
         if(rowmap != null) {
            return rowmap.getCellContext(row);
         }
      }

      if(rowmap != null && colmap != null) {
         String key = getMergeCellKey(row, col);
         CalcCellContext context = mergeCellCache.get(key);

         if(context != null) {
            return context;
         }
         else {
            CalcCellContext merge = CalcCellContext.merge(rowmap.getCellContext(row),
                                                          colmap.getCellContext(col));
            mergeCellCache.put(key, merge);

            return merge;
         }
      }

      return null;
   }

   // @by ChrisSpagnoli feature1414607346853 2014-11-4
   // Retrieve content from child crosstabs, then insert into combined crosstab.
   public CalcCellContext getColumnCellContext(int col) {
      if(colmap != null) {
         return colmap.getCellContext(col);
      }
      return null;
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      return getValue(r, c);
   }

   /**
    * Get the cell name to location mapping for this table.
    */
   public CalcCellMap getCalcCellMap() {
      if(cellmap == null) {
         cellmap = new CalcCellMap(this);
      }

      return cellmap;
   }

   /**
    * Get all referenceable cell name and value pairs for the specified cell.
    * @param cellNames if this is passed in, only names in this set is included
    *                  in the key-value map. this is for optimization purpose.
    */
   public Map getKeyValuePairs(int row, int col, Set<String> cellNames) {
      Object kvKey = row + ":" + col + cellNames;

      // getKeyValuePairs may be called many times if there are multiple highlights. (63654)
      if(this.kvKey0 != null && this.kvKey0.equals(kvKey)) {
         return kvmap0;
      }

      String[] names = calc.getCellNames();
      CalcCellContext context = getCellContext(row, col);
      Set<String> nameset = new HashSet<>();
      Map kvmap = new HashMap();

      // create nameset, which contains all named cells that has not be added
      // to the key-value map
      for(int i = 0; i < names.length; i++) {
         nameset.add(names[i]);
      }

      if(context != null) {
         // add a group values
         for(CalcCellContext.Group group : context.getGroups()) {
            if(group.getName() != null) {
               kvmap.put(group.getName(), group.getValue(context));
               nameset.remove(group.getName());
            }
         }
      }

      if(cellNames != null) {
         nameset.retainAll(cellNames);
      }

      // add the named cell value. If the context exists we try to find a cell
      // matches the exact same context. If no match is found, we use the first
      // cell value
      // @by mikec, here we assume the context match if one of the cell group
      // matches, since each cell can have at most 2 group, one for hierachy
      // and one for vertical, when we evaluate a cell we only interested in
      // one direction. Otherwise an expanded cell(in both direction) will
      // not have correct cell value found.
      // @see bug1182438215294

      for(String name : nameset) {
         CalcCellMap locmap = getCalcCellMap();
         Point[] locs = locmap.getLocations(name);
         boolean found = false;

         // if this cell is the named cell, use the current cell value
         for(Point loc : locs) {
            if(loc.x == col && loc.y == row) {
               kvmap.put(name, getObject(row, col));
               found = true;
               break;
            }
         }

         if(context != null && !found) {
            for(int i = 0; i < locs.length; i++) {
               if(context.equals(getCellContext(locs[i].y, locs[i].x))) {
                  kvmap.put(name, getObject(locs[i].y, locs[i].x));
                  kvmap.put(StyleConstants.COLUMN,
                     getObject(locs[i].y, locs[i].x));
                  found = true;
                  break;
               }
            }
         }

         if(!found && locs.length > 0) {
            kvmap.put(name, getObject(locs[0].y, locs[0].x));
            kvmap.put(StyleConstants.COLUMN, getObject(locs[0].y, locs[0].x));
         }
      }

      this.kvKey0 = kvKey;
      this.kvmap0 = kvmap;
      return kvmap;
   }

   /**
    * Get the row index in the original calc table.
    */
   public int getRow(int row) {
      return (rowmap != null) ? rowmap.get(row) : row;
   }

   /**
    * Get the column index in the original calc table.
    */
   public int getCol(int col) {
      return (colmap != null) ? colmap.get(col) : col;
   }

   /**
    * Get the row indexs in the runtime calc table by the design row index.
    */
   public int[] getRuntimeRows(int row) {
      return rowmap.getReverseIndexs(row);
   }

   /**
    * Get the column indexs in the runtime calc table by the design col index.
    */
   public int[] getRuntimeCols(int col) {
      return colmap.getReverseIndexs(col);
   }

   /**
    * Make a copy of this table lens.
    */
   @Override
   public RuntimeCalcTableLens clone() {
      RuntimeCalcTableLens tbl = (RuntimeCalcTableLens) super.clone();

      tbl.cellmap = cellmap;

      if(rowmap != null) {
         tbl.rowmap = (IndexMap) rowmap.clone();
      }

      if(colmap != null) {
         tbl.colmap = (IndexMap) colmap.clone();
      }

      return tbl;
   }

   /**
    * FreehandTableLens data descriptor.
    */
   private class RuntimeCalcTableDataDescriptor implements TableDataDescriptor {
      public RuntimeCalcTableDataDescriptor(TableDataDescriptor desc) {
         this.desc = desc;
      }

      /**
       * Get date option of formula cell in calc table by cell binding value.
       * @param value the specified cell binding value.
       */
      private static int getFormulaDateOption(String value) {
         int option = -1;
         String date = null;
         String dround = null;
         String[] arr = value.split(",");

         for(int i = 0; i < arr.length; i++) {
            if(arr[i].startsWith("date=")) {
               String subStr = arr[i].substring(arr[i].indexOf("date=") + "date=".length());
               date = subStr.endsWith(")") ? subStr.substring(0, subStr.length() - 2) : subStr;
            }
            else if(arr[i].startsWith("rounddate=")) {
               String subStr = arr[i].substring(arr[i].indexOf("rounddate=") + "rounddate=".length());
               dround = subStr.endsWith(")") ? subStr.substring(0, subStr.length() - 2) : subStr;
            }
         }

         if(date != null) {
            if("year".equalsIgnoreCase(date)) {
               option = DateRangeRef.YEAR_INTERVAL;
            }
            else if("quarter".equalsIgnoreCase(date)) {
               option = DateRangeRef.QUARTER_OF_YEAR_PART;
            }
            else if("month".equalsIgnoreCase(date)) {
               option = DateRangeRef.MONTH_OF_YEAR_PART;
            }
            else if("weekday".equalsIgnoreCase(date)) {
               option = DateRangeRef.DAY_OF_WEEK_PART;
            }
         }
         else if(dround != null) {
            if("year".equalsIgnoreCase(dround)) {
               option = DateRangeRef.YEAR_INTERVAL;
            }
            else if("quarter".equalsIgnoreCase(dround)) {
               option = DateRangeRef.QUARTER_INTERVAL;
            }
            else if("month".equalsIgnoreCase(dround)) {
               option = DateRangeRef.MONTH_INTERVAL;
            }
            else if("week".equalsIgnoreCase(dround)) {
               option = DateRangeRef.WEEK_INTERVAL;
            }
            else if("day".equalsIgnoreCase(dround)) {
               option = DateRangeRef.DAY_INTERVAL;
            }
            else if("hour".equalsIgnoreCase(dround)) {
               option = DateRangeRef.HOUR_INTERVAL;
            }
            else if("minute".equalsIgnoreCase(dround)) {
               option = DateRangeRef.MINUTE_INTERVAL;
            }
            else if("second".equalsIgnoreCase(dround)) {
               option = DateRangeRef.SECOND_INTERVAL;
            }
         }

         return option;
      }

      @Override
      public TableDataPath getColDataPath(int col) {
         return desc.getColDataPath(getCol(col));
      }

      @Override
      public TableDataPath getRowDataPath(int row) {
         return desc.getRowDataPath(getRow(row));
      }

      @Override
      public TableDataPath getCellDataPath(int row, int col) {
         return desc.getCellDataPath(getRow(row), getCol(col));
      }

      @Override
      public boolean isColDataPath(int col, TableDataPath path) {
         return desc.isColDataPath(getCol(col), path);
      }

      @Override
      public boolean isRowDataPath(int row, TableDataPath path) {
         return desc.isRowDataPath(getRow(row), path);
      }

      @Override
      public boolean isCellDataPathType(int row, int col, TableDataPath path) {
         return desc.isCellDataPathType(getRow(row), getCol(col), path);
      }

      @Override
      public boolean isCellDataPath(int row, int col, TableDataPath path) {
         return desc.isCellDataPath(getRow(row), getCol(col), path);
      }

      @Override
      public int getRowLevel(int row) {
         return desc.getRowLevel(getRow(row));
      }

      @Override
      public int getType() {
         return desc.getType();
      }

      /**
       * Create the format info.
       */
      private void createXFormatInfo() {
         FormulaTable table = calc.getElement();
         TableLayout layout = table.getTableLayout();
         TableLens lens = getParentTable();
         fmtinfos = new XFormatInfo[layout.getRowCount()][layout.getColCount()];
         aggregateCells =
            new boolean[layout.getRowCount()][layout.getColCount()];

         if(lens == null) {
            return;
         }

         Map<String, CalcAttr> nameLoc = calc.createNameMap();
         Map<Point, Point> locs = new HashMap<>();

         for(int r = 0; r < layout.getRowCount(); r++) {
            for(int c = 0; c < layout.getColCount(); c++) {
               CellBinding bind = layout.getCellBinding(r, c);

               if(bind != null && bind.getType() == CellBinding.BIND_FORMULA) {
                  String val = bind.getValue();

                  if(val != null && val.startsWith("$")) {
                     String name = val.substring(1);
                     CalcAttr attr = nameLoc.get(name);

                     if(attr != null) {
                        int tr = attr.getRow();
                        int tc = attr.getCol();
                        locs.put(new Point(c, r), new Point(tc, tr));
                     }
                  }
               }
            }
         }

         for(int r = 0; r < layout.getRowCount(); r++) {
            for(int c = 0; c < layout.getColCount(); c++) {
               CellBinding bind = layout.getCellBinding(r, c);

               if(bind == null || bind.getBType() != TableCellBinding.GROUP ||
                  (bind.getType() != CellBinding.BIND_COLUMN &&
                     bind.getType() != CellBinding.BIND_FORMULA))
               {
                  fmtinfos[r][c] = null;
                  aggregateCells[r][c] = true;
                  continue;
               }

               String value = bind.getValue();
               int level = bind.getType() == CellBinding.BIND_COLUMN ?
                  ((TableCellBinding) bind).getDateOption() : getFormulaDateOption(value);

               // ws created date group's level is 0,fmt should be keep when convert
               if(level <= 0 || XUtil.getDefaultDateFormat(level) == null || value == null) {
                  fmtinfos[r][c] = null;
                  continue;
               }

               XFormatInfo finfo = createXFormatInfo(r, c);

               if(fmtinfos[r][c] == null) {
                  fmtinfos[r][c] = finfo;
                  mexisting = true;
               }
            }
         }

         Iterator<Point> keys = locs.keySet().iterator();

         while(keys.hasNext()) {
            Point key = keys.next();
            Point target = locs.get(key);
            fmtinfos[key.y][key.x] = fmtinfos[target.y][target.x];
         }
      }

      public XFormatInfo createXFormatInfo(int layoutR, int layoutC) {
         FormulaTable table = calc.getElement();
         TableLayout layout = table.getTableLayout();
         CellBinding bind = layout.getCellBinding(layoutR, layoutC);

         if(bind == null || bind.getBType() != TableCellBinding.GROUP ||
            (bind.getType() != CellBinding.BIND_COLUMN &&
               bind.getType() != CellBinding.BIND_FORMULA))
         {
            return null;
         }

         int level = bind.getType() == CellBinding.BIND_COLUMN ?
            ((TableCellBinding) bind).getDateOption() : getFormulaDateOption(bind.getValue());
         TableLens lens = getParentTable();
         int col = bind.getType() == CellBinding.BIND_FORMULA ? -1 :
            getCellCol(layoutR, layoutC, lens);
         Class cls2 = col < 0 ? null : lens.getColType(col);

         if(col == -1) {
            if(getElement() instanceof CalcTableVSAssembly) {
               CalcTableVSAssembly assembly = ((CalcTableVSAssembly) getElement());
               Viewsheet vs = assembly.getViewsheet();
               XSourceInfo sinfo = assembly.getSourceInfo();
               String source = sinfo == null ? null : sinfo.getSource();

               if(!StringUtils.isEmpty(source)) {
                  CalculateRef calculateRef = vs.getCalcField(source, bind.getValue());
                  String dtype = calculateRef == null ? null : calculateRef.getDataType();
                  cls2 = dtype == null ? null : Tool.getDataClass(dtype);
               }
            }
         }

         if(col == -1 && cls2 == null && table instanceof CalcTableVSAssembly) {
            ColumnSelection cols = VSUtil.getBaseColumns((CalcTableVSAssembly) table, true);

            if(cols.getAttribute(bind.getValue()) != null) {
               DataRef ref = cols.getAttribute(bind.getValue());
               String dtype = ref.getDataType();
               cls2 = dtype == null ? null : Tool.getDataClass(dtype);
            }
         }

         Class<?> cls = null;
         Object val = null;

         // the base table may not contain data
         if(!moreRows(layoutR) || layoutC >= getColCount()) {
            if(col < 0) {
               return null;
            }
         }
         else {
            int calcRow = -1;

            if(rowmap != null) {
               final int reverse = rowmap.getReverse(layoutR, true);

               if(reverse != -1) {
                  calcRow = reverse;
               }
            }

            int calcCol = -1;

            if(colmap != null) {
               final int reverse = colmap.getReverse(layoutC);

               if(reverse != -1) {
                  calcCol = reverse;
               }
            }

            if(calcRow >= 0 && calcCol >= 0) {
               val = getObject(calcRow, calcCol);
               cls = val == null ? null : val.getClass();
            }

            Object equalsOthersValue = bind.getType() == CellBinding.BIND_FORMULA &&
               val instanceof String ? ((String) val).trim() : val;

            // If val is 'Others' and applied topn filter, it will get a wrong type.
            // So try to get the correct value type by the next value.
            if(Tool.equals(equalsOthersValue, Catalog.getCatalog().getString("Others"))) {
               TopNInfo topN = null;

               if(bind instanceof TableCellBinding) {
                  topN = ((TableCellBinding) bind).getTopN(false);

                  if((topN != null && !topN.isBlank() && topN.isOthers()) ||
                     bind.getType() == CellBinding.BIND_FORMULA)
                  {
                     boolean vertical =
                        ((TableCellBinding) bind).getExpansion() == TableCellBinding.EXPAND_V;
                     int rowNum = vertical ? calcRow + 1 : calcRow;
                     int colNum = !vertical ? calcCol + 1 : calcCol;

                     if(rowNum < getRowCount() && colNum < getColCount()) {
                        val = getObject(rowNum, colNum);
                        cls = val == null ? null : val.getClass();
                     }
                  }
               }
            }
         }

         if(cls == null && col < 0) {
            return null;
         }
         else if(cls == null || !(val instanceof Date) && cls2 != null && !cls2.isInstance(val)) {
            cls = cls2;
         }
         // refix Bug #53924, since use strick datatype of DateRangeRef may cause lots of
         // bc issues(like Bug #54131), so rollback the last change, and fix here to make
         // sure time be format rightly for freehand.
         else if(cls2 == java.sql.Time.class && cls == java.sql.Timestamp.class) {
            cls = cls2;
         }

         String dtype = Util.getDataType(cls);

         // do not apply default date format to non-date type column
         // fix bug1370224630687
         if(cls == null || !XSchema.isDateType(dtype) && !XSchema.isNumericType(dtype)) {
            return null;
         }

         SimpleDateFormat dfmt = XUtil.getDefaultDateFormat(level, dtype);

         if(dfmt == null) {
            return null;
         }

         String fmt = dfmt.toPattern();
         return new XFormatInfo(TableFormat.DATE_FORMAT, fmt);
      }

      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         if(mmap.containsKey(path)) {
            return mmap.get(path);
         }

         XMetaInfo minfo = desc.getXMetaInfo(path);

         if(path == null) {
            return minfo;
         }

         String cell = path.getPath()[0];
         int idx1 = cell.lastIndexOf("[");
         int idx2 = cell.indexOf("]");

         if(idx1 > 0 && idx2 > idx1) {
            String[] pair = Tool.split(cell.substring(idx1 + 1, idx2), ',');

            if(pair.length != 2) {
               return minfo;
            }

            int lr = Integer.parseInt(pair[0]);
            int lc = Integer.parseInt(pair[1]);
            TableLayout layout = calc.getElement().getTableLayout();
            TableCellBinding bind = (TableCellBinding)
               layout.getCellBinding(lr, lc);

            if(bind != null) {
               if(minfo == null) {
                  minfo = new XMetaInfo();
               }
               else {
                  minfo = (XMetaInfo) minfo.clone();
               }

               // @by ChrisSpagnoli feature1414607346853 2014-10-27
               // Guard against null fmtinfos
               if(fmtinfos != null) {
                  if(fmtinfos[lr][lc] != null) {
                     minfo.setXFormatInfo(fmtinfos[lr][lc]);
                  }

                  if(fmtinfos[lr][lc] != null) {
                     minfo.setProperty("autoCreatedFormat", "true");
                  }
                  else if(aggregateCells[lr][lc] &&
                     "true".equals(minfo.getProperty("autoCreatedFormat")))
                  {
                     minfo.setXFormatInfo(null);
                  }
               }

               // do not apply auto drill on aggregated column
               if(minfo != null && (bind.getType() == CellBinding.BIND_COLUMN &&
                  bind.getBType() == TableCellBinding.SUMMARY))
               {
                  minfo = (XMetaInfo) minfo.clone();
                  minfo.setXDrillInfo(null);
               }

               return minfo;
            }

            return minfo;
         }

         return minfo;
      }

      @Override
      public List<TableDataPath> getXMetaInfoPaths() {
         List<TableDataPath> list = new ArrayList<>();

         if(!mmap.isEmpty()) {
            list.addAll(mmap.keySet());
         }

         return list;
      }

      @Override
      public boolean containsFormat() {
         if(fmtinfos == null) {
            createXFormatInfo();
         }

         return desc.containsFormat() || mexisting;
      }

      /**
       * Get the column number from the table for the layout value.
       */
      private int getCellCol(int r, int c, TableLens lens) {
         FormulaTable table = calc.getElement();
         TableLayout layout = table.getTableLayout();
         CellBinding bind = layout.getCellBinding(r, c);
         String value = bind.getValue();
         int level = ((TableCellBinding) bind).getDateOption();
         ColumnIndexMap columnIndexMap = new ColumnIndexMap(lens, true);
         int col = Util.findColumn(columnIndexMap, value, false);

         if(XUtil.getDefaultDateFormat(level) == null || value == null) {
            return col;
         }

         if(col < 0) {
            int idx = value.lastIndexOf("(");
            int last = value.indexOf(")");

            if(idx > 0) {
               value = value.substring(idx + 1, last);
            }
            else {
               value = DateRangeRef.getName(value, level);
            }

            col = Util.findColumn(columnIndexMap, value, false);
         }

         if(col < 0) {
            value = bind.getValue();
            int idx = value.lastIndexOf(".");

            if(idx > 0) {
               try {
                  String suffix = value.substring(idx + 1);
                  Integer.parseInt(suffix);
                  value = value.substring(0, idx);
                  col = Util.findColumn(columnIndexMap, value, false);
               }
               catch(Exception ex) {
                  // ignore it
               }
            }
         }

         return col;
      }

      /**
       * Get the main table from parent.
       */
      private TableLens getParentTable() {
         FormulaTable table = calc.getElement();
         TableLens lens = table.getScriptTable();
         FormulaTableLens formula = lens == null ? null : (FormulaTableLens)
            Util.getNestedTable(lens, FormulaTableLens.class);

         if(formula != null) {
            lens = formula.getTable();

            FormulaTableLens fm = lens == null ? null : (FormulaTableLens)
               Util.getNestedTable(lens, FormulaTableLens.class);

            if(fm != null) {
               lens = fm.getTable();
            }
         }

         return lens;
      }

      @Override
      public boolean containsDrill() {
         return desc.containsDrill();
      }

      private TableDataDescriptor desc;
      private XFormatInfo[][] fmtinfos;
      private boolean[][] aggregateCells;
      private boolean mexisting = false;
   }

   /**
    * Data structure used to hold per row/column information.
    */
   private static class IndexObject {
      public IndexObject(int index, CalcCellContext context) {
         this.index = index;
         this.context = context;
      }

      int index;
      CalcCellContext context;
   }

   /**
    * This class handles row/column maps.
    */
   public static class IndexMap implements Cloneable {
      public IndexMap(int count) {
         map.setSize(count);
         len = count;

         for(int i = 0; i < len; i++) {
            map.set(i, new IndexObject(i, new CalcCellContext()));
         }
      }

      /**
       * Get the map size.
       */
      public int size() {
         return len;
      }

      /**
       * Get the mapping for the specified index.
       */
      public int get(int idx) {
         return (idx < len) ? map.get(idx).index : idx;
      }

      /**
       * Get the index of the value.
       */
      public int getReverse(int val) {
         for(int i = 0; i < len; i++) {
            if(get(i) == val) {
               return i;
            }
         }

         return -1;
      }

      /**
       * Get the index of the value.
       */
      public int getReverse(int val, boolean valueNotEmpty) {
         int matchCount = 0;
         int firstMatch = 0;
         final int maxTryMatchCount = 3;

         for(int i = 0; i < len; i++) {
            CalcCellContext cellContext = getCellContext(i);

            if(get(i) == val) {
               if(!valueNotEmpty) {
                  return i;
               }
               if(matchCount == 0) {
                  firstMatch = i;
               }

               matchCount++;
               boolean allGroupHasValue = cellContext.getGroups()
                  .stream().allMatch(g -> g == null || g.getValue(cellContext) != null);

               if(allGroupHasValue || matchCount >= maxTryMatchCount) {
                  return i;
               }
            }
            else if(matchCount != 0) {
               return firstMatch;
            }
         }

         return -1;
      }

      /**
       * Get the index of the value.
       */
      public int[] getReverseIndexs(int val) {
         IntList list = new IntArrayList();

         for(int i = 0; i < len; i++) {
            if(get(i) == val) {
               list.add(i);
            }
         }

         return list.toIntArray();
      }

      /**
       * Get the index of the value.
       */
      public int getReverse(int val, int start, int inc) {
         for(int i = start; i < len && i >= 0; i += inc) {
            if(get(i) == val) {
               return i;
            }
         }

         return -1;
      }

      /**
       * Get the cell context at the row/column.
       */
      public CalcCellContext getCellContext(int idx) {
         return idx < len ? map.get(idx).context : null;
      }

      /**
       * Insert a value at the specified position.
       */
      public void insert(int pos, int val, CalcCellContext context) {
         map.add(pos, new IndexObject(val, context));
         len++;
      }

      /**
       * Remove the mapping at the specified index.
       */
      public void remove(int pos) {
         map.remove(pos);
         len--;
      }

      /**
       * Get an array of the index map.
       */
      public int[] getMapArray() {
         int[] arr = new int[len];

         for(int i = 0; i < len; i++) {
            arr[i] = map.get(i).index;
         }

         return arr;
      }

      @Override
      public Object clone() {
         IndexMap obj = new IndexMap(0);
         obj.map = (Vector<IndexObject>) map.clone();
         return obj;
      }

      @Override
      public String toString() {
         return super.toString() + map.stream().map(iobj -> iobj.index)
            .collect(Collectors.toList());
      }

      private Vector<IndexObject> map = new Vector<>(); // IndexObject
      private int len = 0;
   }

   // @by ChrisSpagnoli feature1414607346853 2014-11-4
   // Support adding columns to the initial crosstab,
   // and not increasing the span of previous columns when adding a new one
   /**
    *  Extend the addColumn() of DefaultTableLens to extend the colmap.
    */
   public void addCalcColumn(int colRef, CalcCellContext context) {
      int tc = getTrailerColCount();
      super.insertColumn(getColCount(), 1, false);
      colmap.insert(getColCount() - 1, colRef, context);
      setTrailerColCount(tc);

      final CalcTableLens calcTableLens = getCalcTableLens();
      tc = calcTableLens.getTrailerColCount();
      calcTableLens.insertColumn(calcTableLens.getColCount(), 1, false);
      calcTableLens.setTrailerColCount(tc);
   }

   /**
    *  Get the field postion in table lens.
    */
   public Point getFieldRowCol(String field, int row, int col) {
      if(elem == null) {
         return null;
      }

      int plr = -1;
      int plc = -1;
      int lr = getRow(row);
      int lc = getCol(col);
      TableLayout layout = elem.getTableLayout();
      boolean found = false;

      outer:
      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            TableCellBinding group =
               (TableCellBinding) layout.getCellBinding(r, c);

            if(group == null) {
               continue;
            }

            String cellName = layout.getRuntimeCellName(group);

            if(!field.equals(cellName)) {
               continue;
            }

            plr = r;
            plc = c;
            found = true;
            break outer;
         }
      }

      if(!found) {
         return null;
      }

      boolean top = plr <= lr;
      boolean left = plc <= lc;
      int step = top ? -1 : 1;
      int prow = -1;

      while(row >= 0 && moreRows(row)) {
         int brow = getRow(row);

         if(brow == plr) {
            prow = row;
            break;
         }

         row += step;
      }

      if(prow < 0) {
         return null;
      }

      step = left ? -1 : 1;
      int pcol = -1;

      while(col >= 0 && col < getColCount()) {
         int bcol = getCol(col);

         if(bcol == plc) {
            pcol = col;
            break;
         }

         col += step;
      }

      if(pcol < 0) {
         return null;
      }

      return new Point(prow, pcol);
   }

   public String getMergeCellKey(int row, int col) {
      StringBuilder buf = new StringBuilder();
      buf.append(row);
      buf.append("-");
      buf.append(col);

      return buf.toString();
   }

   private TableDataDescriptor cdescriptor;
   private CalcTableLens calc;
   private IndexMap rowmap;
   private IndexMap colmap;
   private transient CalcCellMap cellmap = null;
   private transient boolean eventEnabled = true;
   private transient Map<TableDataPath, XMetaInfo> mmap = new HashMap<>();
   // cached value
   private transient Map kvmap0;
   private transient Object kvKey0;
   private transient Map<String, CalcCellContext> mergeCellCache = new HashMap<>();
}
