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
import inetsoft.report.internal.*;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.lens.SubTableLens;
import inetsoft.report.script.formula.CellRange;
import inetsoft.uql.XTable;
import inetsoft.util.Tool;
import inetsoft.util.script.ArrayObject;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 * This represents an array of table rows.
 */
public class TableArray extends ScriptableObject implements ArrayObject, Wrapper {
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

   @Override
   public String getClassName() {
      return "Table";
   }

   @Override
   public boolean has(String id, Scriptable start) {
      return id.equals("length") || id.equals("size") || super.has(id, start);
   }

   @Override
   public boolean has(int index, Scriptable start) {
      return getTable() != null && min <= index && getTable().moreRows(index);
   }

   /**
    * The following names are supported.
    * - length (row count)
    * - size (column count)
    * - column@groups?condition (cell values)
    * - *@groups?condition (sub table)
    */
   @Override
   public Object get(String id, Scriptable start) {
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

      return super.get(id, start);
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
   public Object get(int index, Scriptable start) {
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

      return Undefined.instance;
   }

   @Override
   public void put(String id, Scriptable start, Object value) {
      // Ignore assignments to "length"--it's readonly.
      if(!id.equals("length") && !id.equals("size")) {
         super.put(id, start, value);
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
   public void put(int index, Scriptable start, Object value) {
   }

   @Override
   public Object getDefaultValue(Class hint) {
      if(hint == ScriptRuntime.BooleanClass) {
         return Boolean.TRUE;
      }

      if(hint == ScriptRuntime.NumberClass) {
         return ScriptRuntime.NaNobj;
      }

      return this;
   }

   @Override
   public Object[] getIds() {
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

   @Override
   public boolean hasInstance(Scriptable value) {
      return false;
   }

   @Override
   public Scriptable getPrototype() {
      return prototype;
   }

   @Override
   public void setPrototype(Scriptable prototype) {
      this.prototype = prototype;
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
   @Override
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

   private Scriptable prototype;
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
