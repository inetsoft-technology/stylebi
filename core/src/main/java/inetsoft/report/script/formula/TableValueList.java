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
package inetsoft.report.script.formula;

import inetsoft.report.TableLens;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.ValueList;
import inetsoft.report.script.TableRow;
import org.mozilla.javascript.Scriptable;

/**
 * Value list for cell expansion.
 */
public class TableValueList extends ValueList {
   /**
    * Create a value list from a table column.
    */
   public TableValueList(TableLens table, String colexpr, boolean expression, 
                         int[] idxs, Scriptable scope) {
      this.table = table;
      this.idxs = idxs;
      this.colexpr = colexpr;
      this.expression = expression;
      this.scope = scope;

      if(!expression) {
         col = Util.findColumn(table, colexpr);
         
         if(col < 0) {
            throw new RuntimeException("Column not found in table: " + colexpr);
         }
      }
   }
   
   /**
    * Get the number of items in the list.
    */
   @Override
   public int size() {
      return idxs.length;
   }

   /**
    * Get the specified item from the list.
    */
   @Override
   public Object get(int i) {
      if(idxs[i] < 0) {
         return null;
      }
      
      if(expression) {
         return FormulaEvaluator.exec(colexpr, scope, "rowValue", 
                                      (Scriptable) getScope(i));
      }
      
      return table == null ? null : table.getObject(idxs[i], col);
   }

   /**
    * Get all values as an array.
    */
   @Override
   public Object[] getValues() {
      Object[] arr = new Object[idxs.length];
      
      for(int i = 0; i < arr.length; i++) {
         arr[i] = get(i);
      }

      return arr;
   }
 
   /**
    * Set the length of the list.
    */
   @Override
   public void setSize(int size) {
      int[] narr = new int[size];

      System.arraycopy(idxs, 0, narr, 0, Math.min(idxs.length, narr.length));

      for(int i = idxs.length; i < narr.length; i++) {
         narr[i] = -1;
      }
      
      idxs = narr;
   }
  
   /**
    * Get a scope for the 'field' variable for the specified value.
    */
   @Override
   public Object getScope(int i) {
      if(idxs[i] < 0) {
         return null;
      }
      
      if(tableRow == null) {
         tableRow = new TableRow(table, idxs[i]);
      }
      else {
         tableRow.setRow(idxs[i]);
      }
      
      return tableRow;
   }

   private transient TableLens table;
   private int[] idxs;
   private String colexpr;
   private int col;
   private boolean expression;
   private TableRow tableRow;
   private transient Scriptable scope;
}
