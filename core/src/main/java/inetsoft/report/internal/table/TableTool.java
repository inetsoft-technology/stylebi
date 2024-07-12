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
package inetsoft.report.internal.table;

import inetsoft.report.*;
import inetsoft.report.filter.GroupedTable;
import inetsoft.report.internal.*;
import inetsoft.report.internal.binding.*;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Utility methods for manipulating table element and table lens.
 *
 * @version 7.0, 1/25/2005
 * @author InetSoft Technology Corp
 */
public class TableTool {

   /**
    * Create column map for a table.
    */
   public static Map<Object, Integer> createColMap(TableLens lens) {
      Map<Object, Integer> cmap = new HashMap<>();

      if(lens != null) {
         for(int c = 0; c < lens.getColCount(); c++) {
            Object header = Util.getHeader(lens, c);

            if(header != null) {
               cmap.put(header, c);
            }
         }
      }

      return cmap;
   }

   /**
    * Convert table lens' row to table layout's row.
    */
   private static int convertToLayoutRow(TableElement elem, int row) {
      TableLayout layout = elem.getTableLayout();

      if(layout.isCalc()) {
         return row;
      }

      TableLens table = elem.getTable();

      // make sure table is processed
      if(!table.moreRows(row)) {
         if(row > 0) {
            throw new RuntimeException("Referencing nonexistent row: " + row);
         }
         else {
            return -1;
         }
      }

      TableDataDescriptor desc = table.getDescriptor();
      TableDataPath path = desc.getRowDataPath(row);

      if(path == null) {
         return -1;
      }

      return elem.getTableLayout().locateRow(path);
   }

   /**
    * Get the cell binding at the specified cell.
    */
   public static TableCellBinding getCellBinding(TableElement table,
                                                 int r, int c) {
      int or = r;
      r = convertToLayoutRow(table, r);

      if(r < 0) {
         throw new RuntimeException("Row " + or + " in table lens can not " +
            "convert to table layout row.");
      }

      return ReportLayoutTool.getCellBinding(table, r, c);
   }

   /**
    * Get the column index from the data path's path string.
    */
   public static int getCol(TableLens lens, String path) {
      if(lens != null) {
         if(lens instanceof CalcTableLens) {
            int res = findCalcCol((CalcTableLens) lens, path);

            if(res >= 0) {
               return res;
            }
         }

         for(int i = 0; i < lens.getColCount(); i++) {
            if(getCol(lens, i, path)) {
               return i;
            }
         }

         try {
            if(path.startsWith("Column [")) {
               int i1 = path.indexOf('[') + 1;
               int i2 = path.indexOf(']');

               return Integer.parseInt(path.substring(i1, i2));
            }
            else if(path.startsWith("Cell [")) {
               int i1 = path.indexOf(',') + 1;
               int i2 = path.indexOf(']');

               return Integer.parseInt(path.substring(i1, i2));
            }
         }
         catch(Exception ex) {
            LOG.debug("Invalid column or cell index: " + path, ex);
         }
      }

      return -1;
   }

   /**
    * Find column from calc table, if the table is runtime calc, return the
    * base column index.
    */
   private static int findCalcCol(CalcTableLens lens, String path) {
      int res = -1;

      for(int c = 0; c < lens.getColCount(); c++) {
         res = getCol0(lens, c, path);

         if(res >= 0) {
            break;
         }
      }

      // @see TableAttr.getColumns
      if(res >= 0 && lens instanceof RuntimeCalcTableLens) {
         res = ((RuntimeCalcTableLens) lens).getCol(res);
      }

      return res;
   }

   /**
    * Get the column index from the data path's path string.
    */
   private static boolean getCol(TableLens lens, int c, String path) {
      int col = -1;

      while(c != -1 && (col = getCol0(lens, c, path)) < 0) {
         if(lens instanceof TableFilter) {
            c = ((TableFilter) lens).getBaseColIndex(c);
            lens = ((TableFilter) lens).getTable();
         }
         else {
            break;
         }
      }

      return col != -1;
   }

   /**
    * Get the column index from the data path's path string.
    */
   private static int getCol0(TableLens table, int c, String path) {
      if(table != null) {
         // check actual data path, the header may be changed by script
         TableDataDescriptor desc = table.getDescriptor();

         if(c < table.getColCount()) {
            TableDataPath tpath = desc.getColDataPath(c);
            String[] cpath = tpath != null ? tpath.getPath() : new String[0];

            if(cpath.length > 0 && path.equals(cpath[0])) {
               return c;
            }
         }
      }

      return -1;
   }

   /**
    * Get the data table for the specified row.
    *
    * @param lens top lens to start from
    * @param base base lens whose row index to get
    * @param row  row of top lens to trace from
    * @return  the row index of the base row corresponding to the top lens row
    *          or -1 if no path exists from top lens to base lens for the row.
    */
   public static int getBaseRowIndex(TableLens lens, TableLens base, int row) {
      while(lens != base) {
         if(!(lens instanceof TableFilter) || !lens.moreRows(row)) {
            return -1;
         }

         TableFilter filter = (TableFilter) lens;
         row = filter.getBaseRowIndex(row);
         lens = filter.getTable();
      }

      return row;
   }

   /**
    * Get group row index.
    */
   public static int getGroupRowIndex(TableElementDef table, TableLens lens,
                                      GroupedTable group, int row, int col,
                                      int grow) {
      if(!lens.moreRows(row)) {
         return -1;
      }

      //@by davyc, find correct row in lens whose base row is
      // same as the r in the grouped table, so when get object,
      // format and value will be same as the group value in the
      // lens, for example, when apply freehand, the group value
      // may be not same as the group filter's group value, so
      // we need to apply the correct group value in the lens
      // optimize
      if(TableTool.getBaseRowIndex(lens, group, row) == grow &&
         lens.getObject(row, col) != null)
      {
         return row;
      }

      TableLayout.RegionIndex ridx = getGroupRegionIndex(table, lens, row, col);
      int type = ridx == null ? -1 : ridx.getRegion().getPath().getType();
      int level = ridx == null ? -1 : ridx.getRegion().getPath().getLevel();
      int index = ridx == null ? -1 : ridx.getRow();
      TableDataDescriptor desc = lens.getDescriptor();
      int hcnt = lens.getHeaderRowCount();
      boolean checked = false;
      int back = -1;

      for(int r = row; r >= hcnt; r--) {
         TableDataPath path = desc.getRowDataPath(r);

         if(ridx == null || (path.getType() == type &&
            path.getLevel() == level))
         {
            boolean sameIndex =  path.getIndex() == index;
            int brow = TableTool.getBaseRowIndex(lens, group, r);

            if(brow < grow) {
               break;
            }

            if(brow == grow && sameIndex) {
               checked = true;

               if(lens.getObject(r, col) != null) {
                  return r;
               }
            }
            // for freehand, detail binding group, and the group is
            // "as group"(option set in option pane), only the first detail
            // for each group has value
            // fix bug1287116957341
            else {
               if(lens.getObject(r, col) != null) {
                  back = r;

                  if(checked) {
                     return r;
                  }
               }
            }
         }
         else if(checked) {
            break;
         }
      }

      return back;
   }

   /**
    * Get base group col.
    */
   public static int getBaseGroupColIndex(TableElementDef table, TableLens lens,
                                          TableLens base, ColumnIndexMap columnIndexMap,
                                          int lrow, int lcol)
   {
      TableLayout layout = table.getTableLayout();

      // no table lalyout? use base col directly, may be wrong
      if(layout == null) {
         return TableTool.getBaseColIndex(lens, base, lcol);
      }

      TableLayout.RegionIndex ridx = getGroupRegionIndex(table, lens, lrow, lcol);

      if(ridx == null) {
         return -1;
      }

      CellBinding cell = ridx.getRegion().getCellBinding(ridx.getRow(), lcol);
      return Util.findColumn(columnIndexMap, cell.getValue());
   }

   private static TableLayout.RegionIndex getGroupRegionIndex(
      TableElementDef table, TableLens lens, int lrow, int lcol)
   {
      TableLayout layout = table.getTableLayout();

      if(layout == null) {
         return null;
      }

      TableDataDescriptor desc = lens.getDescriptor();
      TableDataPath path = desc.getRowDataPath(lrow);
      TableLayout.RegionIndex ridx = layout.getRegionIndex(path);

      if(ridx == null) {
         return null;
      }

      for(int i = ridx.getRow(); i >= 0; i--) {
         TableCellBinding binding = (TableCellBinding)
            ridx.getRegion().getCellBinding(i, lcol);

         if(isValidGroup(binding)) {
            return new TableLayout.RegionIndex(ridx.getRegion(), i);
         }
      }

      List<BaseLayout.Region> pregions = new ArrayList<>();

      for(int i = 0; i < layout.getRegionCount(); i++) {
         BaseLayout.Region region = layout.getRegion(i);

         if(region == ridx.getRegion()) {
            break;
         }

         pregions.add(region);
      }

      for(int i = pregions.size() - 1; i >= 0; i--) {
         BaseLayout.Region region = pregions.get(i);

         for(int r = region.getRowCount() - 1; r >= 0; r--) {
            TableCellBinding binding = (TableCellBinding)
               region.getCellBinding(r, lcol);

            if(isValidGroup(binding)) {
               return new TableLayout.RegionIndex(region, r);
            }
         }
      }

      return null;
   }

   private static boolean isValidGroup(TableCellBinding binding) {
      return binding != null && binding.getType() == CellBinding.BIND_COLUMN &&
             binding.getBType() == TableCellBinding.GROUP &&
             binding.getValue() != null && !"".equals(binding.getValue());
   }

   /**
    * Get the data table for the specified row.
    */
   public static int getBaseColIndex(TableLens lens, TableLens base, int col) {
      while(lens != base && col != -1) {
         if(!(lens instanceof TableFilter)) {
            return -1;
         }

         TableFilter filter = (TableFilter) lens;
         col = col >= filter.getColCount() ? col : filter.getBaseColIndex(col);
         lens = filter.getTable();
      }

      return col;
   }

   /**
    * Get calc table cell's position according to table data path for vs.
    */
   public static Point getVSCalcCellLocation(TableDataPath dpath) {
      String[] paths = dpath.getPath();

      // invalid
      if(paths.length != 1) {
         return null;
      }

      String path = paths[0];
      int idx1 = path.indexOf("Cell [");
      int idx2 = path.indexOf("]");

      // invalid
      if(idx1 < 0 || idx2 < 0 || idx2 <= idx1) {
         return null;
      }

      String rowcol = path.substring(idx1 + 6, idx2);
      String[] arr = rowcol.split(",");

      return getCalcCellLocation(arr);
   }

   private static Point getCalcCellLocation(String[] arr) {
      // invalid
      if(arr.length != 2) {
         return null;
      }

      try {
         int row = Integer.parseInt(arr[0].trim());
         int col = Integer.parseInt(arr[1].trim());

         return new Point(col, row);
      }
      catch(Exception ex) {
         return null;
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(TableTool.class);
}
