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
package inetsoft.report.script;

import inetsoft.report.*;
import inetsoft.report.filter.CrossFilter;
import inetsoft.report.internal.*;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.lens.SubTableLens;
import inetsoft.report.internal.Util;
import inetsoft.report.script.formula.CellRange;
import inetsoft.uql.XTable;
import inetsoft.util.Tool;
import inetsoft.util.script.ArrayObject;
import inetsoft.util.script.graal.ScriptArrayScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This represents an array of table rows.
 */
public class TableArray implements ArrayObject, ScriptArrayScope {
   /**
    * Create a table array directly from a table.
    */
   public TableArray(XTable table) {
      this.table = table;
   }

   /**
    * Create a table array.
    * @param property property name, e.g. Object, Background.
    * @param property type, e.g. Object.class, Color.class.
    */
   public TableArray(String property, Class pType, boolean data) {
      this.data = data;
      this.property = property;
      this.pType = pType;
      min = (property.indexOf("Border") >= 0) ? -1 : 0;
   }

   /**
    * Get array element type.
    */
   @Override
   public Class getType() {
      return null;
   }

   public String getClassName() {
      return "Table";
   }

   @Override
   public boolean hasMember(String id) {
      if(id == null) {
         return false;
      }

      if(id.equals("length") || id.equals("size") || members.containsKey(id)) {
         return true;
      }

      XTable lens = getTable();

      if(lens == null) {
         return false;
      }

      // Unlike Rhino (whose get() was always invoked on read), GraalJS only
      // calls getMember when hasMember reports the member present. A calc cell
      // formula reads columns dynamically as data['Col'] (and data['Col@grp?cond'],
      // data['*@...']) which are resolved lazily in getMember. Report those as
      // present so the read is dispatched — otherwise data['Col'] reads as
      // undefined and group expansion collapses to zero rows. Mirrors the
      // column-existence check in TableRow.hasMember. (#75423)
      if(id.indexOf('@') >= 0 || id.indexOf('?') >= 0 || id.indexOf(':') >= 0 ||
         id.indexOf("^_^") >= 0 || id.startsWith("*"))
      {
         return true;
      }

      // A crosstab flattens its own row/col dimension values into headers, so a bare
      // reference to the dimension's name (e.g. data['city']) never appears as literal
      // column/header text and Util.findColumn below can't find it -- only the
      // dimension's values (e.g. "Aachen") do. Defer to getMember, which resolves
      // dimension names directly against the crosstab (see NamedCellRange).
      if(lens instanceof CrossFilter) {
         return true;
      }

      return Util.findColumn(lens, id) >= 0;
   }

   /**
    * The following names are supported.
    * - length (row count)
    * - size (column count)
    * - column@groups?condition (cell values)
    * - *@groups?condition (sub table)
    */
   @Override
   public Object getMember(String id) {
      if(id.equals("length")) {
         XTable lens = getTable();

         if(lens == null) {
            return Integer.valueOf(0);
         }

         lens.moreRows(Integer.MAX_VALUE);
         return Integer.valueOf(lens.getRowCount());
      }
      else if(id.equals("size")) {
         XTable lens = getTable();

         if(lens == null) {
            return Integer.valueOf(0);
         }

         return Integer.valueOf(lens.getColCount());
      }

      try {
         CellRange range = CellRange.parse(id);
         id = Tool.replaceAll(id, "^_^", ":");
         range.setProcessCalc(calcArray);
         boolean subtable = id.startsWith("*");
         Collection cells = range.getCells(getTable(), subtable);

         if(subtable) {
            XTable sub = new SubTableLens((TableLens) getTable(), getRows(cells),
                                          (int[]) null)
            {
               /**
                * Make sure the row/column headers are included.
                * Always show the table headers.
                */
               @Override
               protected void adjustHeader(boolean keepHR, boolean keepHC) {
                  TableLens table = getTable();

                  int hrow = table.getHeaderRowCount();

                  if(rows != null && keepHR && hrow > 0) {
                     int[] nrows = new int[rows.length + hrow];

                     System.arraycopy(rows, 0, nrows, hrow, rows.length);
                     for(int i = 0; i < hrow; i++) {
                        nrows[i] = i;
                     }

                     rows = nrows;
                  }

                  int hcol = table.getHeaderColCount();

                  if(cols != null && keepHC && hcol > 0) {
                     int[] ncols = new int[cols.length + hcol];

                     System.arraycopy(cols, 0, ncols, hcol, cols.length);
                     for(int i = 0; i < hcol; i++) {
                        ncols[i] = i;
                     }

                     cols = ncols;
                  }
               }
            };

            return new TableArray(sub);
         }
         else {
            return range.getCollectionValue(cells, calcArray);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to get table property: " + id, ex);
      }

      return members.get(id);
   }

   /**
    * Get the rows from cell position.
    */
   private static int[] getRows(Collection cells) {
      int[] rows = new int[cells.size()];

      for(int i = 0; i < rows.length; i++) {
         rows[i] = ((Point) ((List) cells).get(i)).y;
      }

      return rows;
   }

   @Override
   public Object getArrayElement(long lindex) {
      int index = (int) lindex;
      XTable lens = getTable();

      if(lens != null && min <= index && lens.moreRows(index)) {
         int cacheIdx = index - min;
         int rowsIdx = cacheIdx - rowsStartIdx;
         TableRow row = null;

         if(rowsIdx >= 0 && rowsIdx < MAX_ROWS) {
            row = rows[rowsIdx];
         }

         if(row == null) {
            row = new TableRow(getTable(), index, property, pType);

            if(rowsIdx < 0 || rowsIdx >= MAX_ROWS) {
               rowsStartIdx = cacheIdx;
               rowsIdx = 0;

               for(int i = 0; i < MAX_ROWS; i++) {
                  rows[i] = null;
               }
            }

            rows[rowsIdx] = row;
         }

         return row;
      }
      else {
         if(LOG.isDebugEnabled()) {
            LOG.warn(
               "Row[{}] not found in: {} {} rows", index, lens.getClass(), lens.getRowCount(),
               new ArrayIndexOutOfBoundsException(index));
         }
         else {
            LOG.warn(
               "Row[{}] not found in: {} {} rows", index, lens.getClass(), lens.getRowCount());
         }
      }

      return null;
   }

   @Override
   public long getArraySize() {
      XTable lens = getTable();

      if(lens == null) {
         return 0;
      }

      lens.moreRows(Integer.MAX_VALUE);
      return lens.getRowCount();
   }

   /**
    * Set an indexed property in this object. Ported from the Rhino
    * {@code put(int, Scriptable, Object)}, which was a no-op. (#75423)
    */
   @Override
   public void setArrayElement(long index, Object value) {
      // do nothing
   }

   @Override
   public void putMember(String id, Object value) {
      // Ignore assignments to "length"--it's readonly.
      if(!id.equals("length") && !id.equals("size")) {
         members.put(id, value);
      }
      else {
         XTable lens = getTable();
         Integer len = Integer.valueOf("" + value);

         try {
            String prop = id.equals("length") ? "Row" : "Col";
            Method mtd = lens.getClass().getMethod("set" + prop + "Count",
               new Class[] {int.class });

            mtd.invoke(lens, new Object[] {len});
         }
         catch(Exception e) {
         }
      }
   }

   @Override
   public Object[] getMemberKeys() {
      XTable lens = getTable();
      int length = 0;

      if(lens != null) {
         lens.moreRows(Integer.MAX_VALUE);
         length = lens.getRowCount();
      }

      Object[] result = new Object[length + 1 + 1];

      for(int i = 0; i < length; i++) {
         result[i] = Integer.valueOf(i);
      }

      result[length] = "length";
      result[length + 1] = "size";

      return result;
   }

   /**
    * Get the table associated with this element.
    */
   public XTable getElementTable() {
      return table;
   }

   /**
    * Unwrap the object by returning the wrapped value.
    * @return a wrapped value
    */
   public Object unwrap() {
      return getTable();
   }

   /**
    * Get the table lens for this table.
    */
   public XTable getTable() {
      XTable ntable = getElementTable();

      // if a new table, clear cache
      if(ntable != cached) {
         for(int i = 0; i < MAX_ROWS; i++) {
            rows[i] = null;
         }

         rowsStartIdx = 0;
         cached = ntable;
         table = ntable;
      }

      return table;
   }

   /**
    * Get display suffix.
    */
   @Override
   public String getDisplaySuffix() {
      return "[row][col]";
   }

   /**
    * Get suffix.
    */
   @Override
   public String getSuffix() {
      return "[][]";
   }

   /**
    * Set this array used for calc.
    */
   public void setCalcArray(boolean calc) {
      this.calcArray = calc;
   }

   /**
    * Clear the cached data.
    */
   protected void clearCache() {
      for(int i = 0; i < rows.length; i++) {
         rows[i] = null;
      }
   }

   private static final int MAX_ROWS = 100;
   protected boolean data = false; // true if use data (non-filter) table

   protected final Map<String, Object> members = new LinkedHashMap<>();
   private XTable table = null; // table lens
   private TableRow[] rows = new TableRow[MAX_ROWS]; // cached TableRow
   private int rowsStartIdx = 0; // starting row index corresponds to rows[0]
   private String property = "Object"; // property type
   private Class pType = Object.class; // property type
   private int min = 0;
   private boolean calcArray = false;

   // chart corresponding to the rows (cached)
   private transient XTable cached = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(TableArray.class);
}
