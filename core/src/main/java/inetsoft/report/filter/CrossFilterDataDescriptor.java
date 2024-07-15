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
package inetsoft.report.filter;

import inetsoft.report.*;
import inetsoft.report.internal.Util;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.XDimensionRef;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Crosstab data descriptor.
 */
public abstract class CrossFilterDataDescriptor implements TableDataDescriptor {
   /**
    * @param table  the target crossfilter of the descriptor.
    */
   public CrossFilterDataDescriptor(CrossFilter table) {
      this.table = table;
   }

   /**
    * Get table data path of a specified table column.
    * @param col the specified table column
    * @return table data path of the table column
    */
   @Override
   public TableDataPath getColDataPath(int col) {
      if(table == null) {
         return null;
      }

      table.checkInit();

      int level = 0;
      int type = TableDataPath.GROUP_HEADER;
      int[] rowh = table.getRowHeaderIndexes();
      int[] colh = table.getColHeaderIndexes();
      String[] path = null;

      // header
      if(col < table.getHeaderColCount()) {
         // summary header
         if(col >= rowh.length || rowh[0] == -1) {
            level = 0;
            type = TableDataPath.SUMMARY_HEADER;
         }
         // row group header
         else {
            level = col;
            type = TableDataPath.GROUP_HEADER;
            path = new String[col + 1];

            for(int i = 0; i < path.length; i++) {
               path[i] = table.getHeader(rowh[i], i + 100);
            }
         }
      }
      else {
         // get actual detail column
         int row = table.getHeaderRowCount();

         if(table.isRowTotalOnTop()) {
            row = table.getRowCount() - 1;
         }

         TableDataPath cpath = getCellDataPath(row, col);

         if(cpath != null) {
            level = cpath.getLevel();
            type = cpath.getType();

            // all row header and col header as path cell on the detail
            if(type == TableDataPath.SUMMARY) {
               level = calcLevel(cpath.getPath());

               if(level == 0) {
                  type = TableDataPath.DETAIL;
               }
               else {
                  level = colh.length - level - 1;

                  if(colh.length == 1 && colh[0] == -1) {
                     level--;
                  }
               }
            }

            path = shrinkPath(true, !table.isSummarySideBySide(), cpath.getPath());
         }
      }

      path = path == null ? new String[0] : path;
      return new TableDataPath(level, type, null, path, false, true);
   }

   /**
    * Get table data path of a specified table row.
    * @param row the specified table row
    * @return table data path of the table row
    */
   @Override
   public TableDataPath getRowDataPath(int row) {
      if(table == null) {
         return null;
      }

      table.checkInit();

      int level = 0;
      int type = TableDataPath.GROUP_HEADER;
      int[] rowh = table.getRowHeaderIndexes();
      int[] colh = table.getColHeaderIndexes();
      String[] path = null;

      // header
      if(row < table.getHeaderRowCount()) {
         // summary header
         if(row >= colh.length || colh[0] == -1) {
            level = 0;
            type = TableDataPath.SUMMARY_HEADER;
         }
         // column group header
         else {
            level = row;
            type = TableDataPath.GROUP_HEADER;
            path = new String[row + 1];

            for(int i = 0; i < path.length; i++) {
               path[i] = table.getHeader(colh[i], i + rowh.length + 100);
            }
         }
      }
      else {
         // get actual detail column
         int col = table.getHeaderColCount();

         if(table.isColumnTotalOnFirst()) {
            col = table.getColCount() - 1;
         }

         TableDataPath cpath = getCellDataPath(row, col);
         level = cpath.getLevel();
         type = cpath.getType();
         path = shrinkPath(false, table.isSummarySideBySide(), cpath.getPath());

         // all row header and col header as path cell on the detail
         if(type == TableDataPath.SUMMARY) {
            level = calcLevel(cpath.getPath());

            if(level == 0) {
               type = TableDataPath.DETAIL;
            }
            else {
               level = rowh.length - level - 1;

               if(rowh.length == 1 && rowh[0] == -1) {
                  level--;
               }
            }
         }
      }

      path = path == null ? new String[0] : path;
      return new TableDataPath(level, type, null, path, true, false);
   }

   /**
    * Get table data path of a specified table cell.
    * @param row the specified table cell row
    * @param col the specified table cell col
    * @return table data path of the table cell
    */
   @Override
   public TableDataPath getCellDataPath(int row, int col) {
      if(table == null) {
         return null;
      }

      table.checkInit();

      boolean sideBySide = table.isSummarySideBySide();
      // deal with the first map table
      int[] dcols = table.getDataIndexes();
      int dcount = dcols.length;
      int ccount = table.getColHeaderCount();
      int rcount = table.getRowHeaderCount();
      int hrowmore = table.getHeaderRowCount() - ccount;
      int hcolmore = table.getHeaderColCount() - rcount;

      CrossFilter.Tuple rtuple = table.getRowTuple(row);
      CrossFilter.Tuple ctuple = table.getColTuple(col);

      List<String> list = createList(rtuple, col, ctuple, row);
      // with freehand layout, the row/col may be outside the current
      // table lens range
      Object val = table.getObject(row, col);
      Class<?> cls = val == null ? null : val.getClass();
      String dtype = Util.getDataType(cls);
      int type;

      // is invalid cell
      if(rtuple == null && ctuple == null) {
         // @by larryl, using absolute position to allow the empty cells'
         // formats to be set
         if(row < table.getHeaderRowCount() && col < table.getHeaderColCount()) {
            return new TableDataPath(-1, TableDataPath.HEADER, dtype,
               new String[] {"Cell [" + row + "," + col + "]"});
         }

         return null;
      }
      // @by larryl, if a summary header row, add the summary header to path.
      // The summary header cell path contains all other header cells on the
      // same dimension, appended with the original summary column header.
      // This means if the side by side setting is changed and the headers
      // are moved to the other dimension, the data path would be different.
      // This is necessary since crosstab logic (e.g. highlight) assumes the
      // path captures the nesting sequence.
      else if(row >= ccount && row < ccount + hrowmore && sideBySide) {
         int index = (col - table.getHeaderColCount()) % dcount;

         // @by larryl, if same column is used for multiple summaries, we
         // need to append a sequence number to distinguish the path
         String header = getDataHeader(dcols, index, index);
         type = TableDataPath.HEADER;
         list.add(header);
      }
      // @by larryl, if a summary header col, add the summary header to path
      else if(col >= rcount && col < rcount + hcolmore && !sideBySide) {
         int index = (row - table.getHeaderRowCount()) % dcount;

         // @by larryl, if same column is used for multiple summaries, we
         // need to append a sequence number to distinguish the path
         String header = getDataHeader(dcols, index, index);
         type = TableDataPath.HEADER;
         list.add(header);
      }
      // is header row cell
      else if(rtuple == null && ctuple != null) {
         if(ctuple.getRow().length == 0) {
            type = TableDataPath.GRAND_TOTAL;
         }
         else if(ctuple.getRow().length < ccount &&
            row >= ctuple.getRow().length) {
            type = TableDataPath.SUMMARY;
         }
         else {
            type = TableDataPath.GROUP_HEADER;
            fixSummaryHeaderPath(list, row, col);
         }
      }
      // is header col cell
      else if(rtuple != null && ctuple == null) {
         if(rtuple.getRow().length == 0) {
            type = TableDataPath.GRAND_TOTAL;
         }
         else if(rtuple.getRow().length < rcount && col >= rtuple.getRow().length) {
            type = TableDataPath.SUMMARY;
         }
         else {
            type = TableDataPath.GROUP_HEADER;
            fixSummaryHeaderPath(list, row, col);
         }
      }
      // is summary cell or grand total cell
      else {
         type = rtuple.getRow().length == 0 || ctuple.getRow().length == 0 ?
            TableDataPath.GRAND_TOTAL :  TableDataPath.SUMMARY;
         int index = sideBySide ?
            (col - table.getHeaderColCount()) % dcount: (row - table.getHeaderRowCount()) % dcount;

         // @by larryl, if same column is used for multiple summaries, we
         // need to append a sequence number to distinguish the path
         String header = getDataHeader(dcols, index, index);
         list.add(header);
      }

      return new TableDataPath(-1, type, dtype, list.toArray(new String[0]));
   }

   /**
    * @param dcol   the data col indexes.
    * @param index  the aggregate index.
    * @return the aggregate data header.
    */
   private String getDataHeader(int[] dcol, int index, int didx) {
      // get aggregate header by aggr refs, so don't need the original index.
      if(table instanceof CrossTabFilter && ((CrossTabFilter) table).isCalcAggregate(index)) {
         CrossTabFilter crossTabFilter = (CrossTabFilter) table;

         return crossTabFilter.getDataHeader(crossTabFilter.getMeasureName(index), dcol[index],
            didx);
      }
      else {
         return table.getHeader(dcol[index], didx);
      }
   }

   /**
    * Check if a column belongs to a table data path.
    * @param col the specified table col
    * @param path the specified table data path
    * @return true if the col belongs to the table data path, false otherwise
    */
   @Override
   public boolean isColDataPath(int col, TableDataPath path) {
      // table data path in crosstab for col is meaningless
      return false;
   }

   /**
    * Check if a row belongs to a table data path.
    * @param row the specified table row
    * @param path the specified table data path
    * @return true if the row belongs to the table data path, false otherwise
    */
   @Override
   public boolean isRowDataPath(int row, TableDataPath path) {
      // table data path for row in crosstab is meaningless
      return false;
   }

   /**
    * Check if a cell belongs to a table data path in a loose way.
    * Note: for crosstab, we always check table data path in a strick way.
    * @param row the specified table cell row
    * @param col the specified table cell col
    * @param path the specified table data path
    * @return true if the cell belongs to the table data path,
    * false otherwise
    */
   @Override
   public boolean isCellDataPathType(int row, int col, TableDataPath path) {
      TableDataPath npath = getCellDataPath(row, col);
      return npath != null && npath.equals(path);
   }

   /**
    * Check if a cell belongs to a table data path.
    * Note: for crosstab, we always check table data path in a strick way.
    * @param row the specified table cell row
    * @param col the specified table cell col
    * @param path the specified table data path
    * @return true if the cell belongs to the table data path,
    * false otherwise
    */
   @Override
   public boolean isCellDataPath(int row, int col, TableDataPath path) {
      return isCellDataPathType(row, col, path);
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
      return CROSSTAB_TABLE;
   }

   /**
    * Create a list stores path.
    * @param rtuple the specified row tuple
    * @param col the specified col
    * @param ctuple the specified col tuple
    * @param row the specified row
    * @return a list stores path
    */
   private List<String> createList(CrossFilter.Tuple rtuple, int col, CrossFilter.Tuple ctuple, int row) {
      List<String> list = new ArrayList<>();
      int[] rowh = table.getRowHeaderIndexes();

      if(rtuple != null) {
         // distinguish between null tuple and grand total tuple
         if(rtuple.getRow().length == 0) {
            list.add(CrossTabFilter.ROW_GRAND_TOTAL_HEADER);
         }

         for(int i = 0; i <= col && i < rtuple.getRow().length; i++) {
            if(rowh[i] == -1) {
               continue;
            }

            list.add(table.getHeader(rowh[i], i + 100));
         }
      }

      if(ctuple != null) {
         int[] colh = table.getColHeaderIndexes();

         // distinguish between null tuple and grand total tuple
         if(ctuple.getRow().length == 0) {
            list.add(CrossTabFilter.COL_GRAND_TOTAL_HEADER);
         }

         for(int i = 0; i <= row && i < ctuple.getRow().length; i++) {
            if(colh[i] == -1) {
               continue;
            }

            list.add(table.getHeader(colh[i], i + rowh.length + 100));
         }
      }

      return list;
   }

   /**
    * Check the path is cover by all crosstab field binding.
    */
   private int calcLevel(String[] path) {
      int[] rowh = table.getRowHeaderIndexes();
      int[] colh = table.getColHeaderIndexes();
      int match = path.length - rowh.length - colh.length - 1;

      if(rowh.length == 1 && rowh[0] == -1) {
         match++;
      }

      if(colh.length == 1 && colh[0] == -1) {
         match++;
      }

      return Math.abs(match);
   }

   /**
    * Shrink table data path's path.
    */
   private String[] shrinkPath(boolean shrinkrow, boolean shrinksummary,
                               String[] path) {
      if(path == null) {
         return path;
      }

      int[] rowh = table.getRowHeaderIndexes();
      int[] colh = table.getColHeaderIndexes();

      Set<String> headers = new HashSet<>();
      List<String> list = new ArrayList<>();

      if(shrinkrow) {
         for(int i = 0; i < rowh.length; i++) {
            if(rowh[i] == -1) {
               continue;
            }

            headers.add(table.getHeader(rowh[i], i + 100));
         }

         headers.add(CrossTabFilter.ROW_GRAND_TOTAL_HEADER);
      }
      else {
         for(int i = 0; i < colh.length; i++) {
            if(colh[i] == -1) {
               continue;
            }

            headers.add(table.getHeader(colh[i], i + rowh.length + 100));
         }

         headers.add(CrossTabFilter.COL_GRAND_TOTAL_HEADER);
      }

      int[] dcol = table.getDataIndexes();

      if(shrinksummary) {
         for(int i = 0; i < dcol.length; i++) {
            headers.add(getDataHeader(dcol, i, 0));
         }
      }

      for(int i = 0; i < path.length; i++) {
         if(!headers.contains(path[i])) {
            list.add(path[i]);
         }
      }

      return path.length == list.size() ? path : list.toArray(new String[0]);
   }

   /**
    * Fix summary header path if need.
    */
   private void fixSummaryHeaderPath(List<String> list, int row, int col) {
      // if have two aggreage and no column header, summary sidebyside
      // is true, avoid two header cell have the same table data path
      if(list.size() == 0) {
         Object val = table.getObject(row, col);

         if(val != null) {
            Object theader = table.getHeaderMaps().get(val);
            val = theader == null ? val : theader;
         }

         String value = val == null ? null : val.toString();

         // Fixed bug #32315: this should add when value contains "/",
         // that need datapath's path when change this column width.
         if(value != null) {
            list.add(value);
         }
      }
   }

   /**
    * Create key value paires for hyperlink and condition to use.
    *
    * @param row the specified row
    * @param col the specified col
    * @param map the specified map, null if should create a new one
    * @return a map stores key value paries
    */
   public Map<Object, Object> getKeyValuePairs(int row, int col, Map<Object, Object> map) {
      table.checkInit();
      map = map == null ? new Object2ObjectOpenHashMap<>() : map;

      CrossFilter.Tuple rtuple = table.getRowTuple(row);
      CrossFilter.Tuple ctuple = table.getColTuple(col);

      // row grand total header cell
      if(rtuple != null && rtuple.getRow().length == 0) {
         String key = CrossFilter.ROW_GRAND_TOTAL_HEADER;
         Object val = table.getGrandTotalLabel();
         map.put(key, val);
      }

      // col grand total header cell
      if(ctuple != null && ctuple.getRow().length == 0) {
         String key = CrossFilter.COL_GRAND_TOTAL_HEADER;
         Object val = table.getGrandTotalLabel();
         map.put(key, val);
      }

      int[] rowh = table.getRowHeaderIndexes();
      int[] colh = table.getColHeaderIndexes();

      // get row header key-value pairs
      for(int i = 0; rtuple != null && i <= col && i < rtuple.getRow().length; i++) {
         if(rowh[i] == -1) {
            continue;
         }

         String key = table.getHeader(rowh[i], i + 100);
         Object val = table.getObject(row, i);
         map.put(key, val == null || StringUtils.isEmpty(val) ? null : val);
      }

      // get col header key-value pairs
      for(int i = 0; ctuple != null && i <= row && i < ctuple.getRow().length; i++) {
         if(colh[i] == -1) {
            continue;
         }

         String key = table.getHeader(colh[i], i + rowh.length + 100);
         Object val = table.getObject(i, col);
         map.put(key, val == null || StringUtils.isEmpty(val) ? null : val);
      }

      int[] dcol = table.getDataIndexes();

      // get data cell key-value pairs
      if(rtuple != null && ctuple != null) {
         // Find the starting row or column for this set of aggregates
         int startRowOrCol = table.isSummarySideBySide() ?
            col - (col - table.getHeaderColCount()) % dcol.length :
            row - (row - table.getHeaderRowCount()) % dcol.length;

         Object[] headers = table.getHeaders();

         // Iterate through all of the aggregates
         for(int i = 0; dcol != null && i < dcol.length; i++) {
            Object key = headers[i];

            if(table instanceof CrossTabFilter && ((CrossTabFilter) table).isCalcAggregate(i)) {
               key = ((CrossTabFilter) table).getDataHeader(key.toString(), dcol[i], i);
            }

            Object val = table.isSummarySideBySide() ?
               table.getObject(row, startRowOrCol + i) :
               table.getObject(startRowOrCol + i, col);
            map.put(key, val);
         }

         Object val = table.getObject(row, col);
         map.put(StyleConstants.COLUMN, val);
      }

      return map;
   }

   /**
    * Get available fields of a crosstab cell.
    * @param row the specified row
    * @param col the specified col
    * @return available fields for crosstab condition evaluation, the
    * string representation of a field is 'dataType^dateLevel_fieldName'
    */
   public String[] getAvailableFields(int row, int col) {
      table.checkInit();
      List<String> list = new ArrayList<>();

      CrossFilter.Tuple rtuple = table.getRowTuple(row);
      CrossFilter.Tuple ctuple = table.getColTuple(col);

      // row grand total header cell
      if(rtuple != null && rtuple.getRow().length == 0) {
         String fld = XSchema.STRING  + "^0_" + CrossFilter.ROW_GRAND_TOTAL_HEADER;
         list.add(fld);
      }

      // col grand total header cell
      if(ctuple != null && ctuple.getRow().length == 0) {
         String fld = XSchema.STRING + "^0_" + CrossFilter.COL_GRAND_TOTAL_HEADER;
         list.add(fld);
      }

      int[] rowh = table.getRowHeaderIndexes();
      int[] colh = table.getColHeaderIndexes();

      // get row header key-value pairs
      for(int i = 0; rtuple != null && i <= col && i < rtuple.getRow().length; i++) {
         if(rowh[i] == -1) {
            continue;
         }

         String key = table.getHeader(rowh[i], i + 100);
         Object val = table.getObject(row, i);
         Class<?> cls = val == null ? null : val.getClass();
         String type = Util.getDataType(cls);
         String fld = type + "^" + getDateLevel(row, i) + "_" + key;
         list.add(fld);
         addMergePartCellFields(val, list, row, i);
      }

      // get col header key-value pairs
      for(int i = 0; ctuple != null && i <= row && i < ctuple.getRow().length; i++) {
         if(colh[i] == -1) {
            continue;
         }

         String key = table.getHeader(colh[i], i + rowh.length + 100);
         Object val = table.getObject(i, col);
         Class<?> cls = val == null ? null : val.getClass();
         String type = Util.getDataType(cls);
         String fld = type + "^" + getDateLevel(i, col) + "_" + key;
         list.add(fld);
         addMergePartCellFields(val, list, row, i);
      }

      int[] dcol = table.getDataIndexes();

      // get data cell key-value pairs
      if(rtuple != null && ctuple != null) {
         // Find the starting row or column for this set of aggregates
         int startRowOrCol = table.isSummarySideBySide() ?
            col - (col - table.getHeaderColCount()) % dcol.length :
            row - (row - table.getHeaderRowCount()) % dcol.length;

         // Iterate through all of the aggregates
         for(int i = 0; dcol != null && i < dcol.length; i++)
         {
            String key = getDataHeader(dcol, i, i);
            Object val = table.isSummarySideBySide() ?
               table.getObject(row, startRowOrCol + i) :
               table.getObject(startRowOrCol + i, col);

            Class<?> cls = val == null ? null : val.getClass();
            String type = Util.getDataType(cls);
            String fld = type + "^0_" + key;
            list.add(fld);
         }
      }

      return list.toArray(new String[0]);
   }

   private void addMergePartCellFields(Object value, List<String> fieldsList, int row, int col) {
      if(value instanceof DCMergeDatePartFilter.MergePartCell) {
         DCMergeDatePartFilter.MergePartCell cell = (DCMergeDatePartFilter.MergePartCell) value;

         List<XDimensionRef> mergedRefs = cell.getMergedRefs();

         for(int j = 0; j < mergedRefs.size(); j++) {
            if(mergedRefs.get(j) == null) {
               continue;
            }

            Object val = cell.getValue(j);
            Class<?> cls = val == null ? null : val.getClass();
            String type = Util.getDataType(cls);
            String fld = type + "^" + getDateLevel(row, col) + "_" + mergedRefs.get(j).getFullName();
            fieldsList.add(fld);
         }
      }
   }

   private int getDateLevel(int row, int col) {
      if(!(table instanceof CrossTabFilter)) {
         return 0;
      }

      TableDataPath path = getCellDataPath(row, col);
      Integer level = ((CrossTabFilter) table).levels.get(path);
      return level == null ? 0 : level;
   }

   private final CrossFilter table;
}