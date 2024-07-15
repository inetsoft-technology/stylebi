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
package inetsoft.report.filter;

import inetsoft.report.*;
import inetsoft.report.internal.CellTableLens;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableTool;
import inetsoft.uql.*;
import inetsoft.uql.erm.DataRef;

import java.util.*;

/**
 * A freehand table condition group provides the ability to calculate a
 * freehand table condition list in a runtime freehand table.
 *
 * @version 11.0
 * @author InetSoft Technology Corp
 */
class FreehandConditionGroup extends ConditionGroup {
   /**
    * Construct a new instance of FreehandConditionGroup using freehand table.
    * @param table the specified freehand table.
    * @param row the specified row.
    * @param col the specified col.
    * @param list the specified condition list.
    */
   public FreehandConditionGroup(CellTableLens table, int row, int col,
                                 ConditionList list, Object box)
   {
      this.table = table;
      this.row = row;
      this.col = col;
      this.list = list;
      this.box = box;
   }

   /**
    * Evaluate the freehand condition group.
    * @return <tt>true</tt> if satisfies condition, <tt>false</tt> otherwise.
    */
   public boolean evaluate() {
      valueCols = addConditionItems();
      List<String> cols = new ArrayList<>(valueCols);
      CellBinding[] bindings = table.getCellBindings(row);
      GroupedTable groupedTable = (GroupedTable)
         Util.getNestedTable(table, GroupedTable.class);
      Map<Object, Integer> gcmap = null;
      int bgrow = -1;
      Object[] values = new Object[cols.size()];

      // @by davidd feature1293418255668, Always get the value of the current
      // cell from the table instead of the binding to support duplicate fields
      // that are bound as Detail AND Group.
      int idx = bindings[col] == null ?
         -1 : cols.indexOf(bindings[col].getValue());

      if(idx >= 0) {
         values[idx] = table.getObject(row, col);
         cols.set(idx, null);
      }

      // Iterate through all column-bindings and populate the values array
      // with the corresponding values to be used when evaluating the condition.
      for(int c = 0; c < bindings.length; c++) {
         if(bindings[c] != null && bindings[c].getType() == CellBinding.BIND_TEXT) {
            continue;
         }

         String value = bindings[c] == null ? null : bindings[c].getValue();
         idx = value == null ? -1 : cols.indexOf(value);

         // not contain this value
         if(idx < 0) {
            continue;
         }

         if(values[idx] == null) {
            values[idx] = table.getObject(row, c);
         }

         // for current column binding, just use the value directly, don't
         // drill to find the really value for it, which is same as plain
         // table, see comment in ConditonGroup.evaluate
         if(values[idx] == null && groupedTable != null && col != c) {
            if(gcmap == null) {
               gcmap = TableTool.createColMap(groupedTable);
               bgrow = TableTool.getBaseRowIndex(table, groupedTable, row);
            }

            Integer cidx = gcmap.get(value);

            if(cidx != null) {
               int gclvl = groupedTable.getGroupColLevel(cidx);

               if(gclvl >= 0) {
                  int gfr = groupedTable.getGroupFirstRow(bgrow, gclvl);

                  if(gfr >= 0) {
                     values[idx] = groupedTable.getObject(gfr, cidx);
                  }
               }
            }
         }

         // mark processed
         cols.set(idx, null);
      }

      // any value is not processed? get it from base table
      for(int i = 0; i < cols.size(); i++) {
         // processed?
         if(cols.get(i) == null) {
            continue;
         }

         values[i] = getObject(cols.get(i));
      }

      return evaluate(values);
   }

   /**
    * Build condition items.
    * @return all used columns.
    */
   private List<String> addConditionItems() {
      List<String> cols = new ArrayList<>();

      // Build the ConditionGroup data structure from the ConditionList
      for(int i = 0; i < list.getSize(); i++) {
         HierarchyItem item = (HierarchyItem) list.getItem(i);

         if(item instanceof ConditionItem) {
            ConditionItem cond = (ConditionItem) item;
            DataRef attr = cond.getAttribute();
            String entity = attr.getEntity();
            String apath = entity == null ? attr.getAttribute() :
               entity + "." + attr.getAttribute();

            if(!cols.contains(apath)) {
               cols.add(apath);
            }

            int index = cols.indexOf(apath);
            condition = (XCondition) cond.getXCondition().clone();
            addCondition(index, condition, cond.getLevel());
            execExpressionValues(attr, condition, box);
         }

         if(item instanceof JunctionOperator) {
            JunctionOperator op = (JunctionOperator) item;
            addOperator(op.getJunction(), op.getLevel());
         }
      }

      // this is needed if the value of the condition reference a field (see findValueIndex).
      // (47352)
      if(hasField) {
         for(DataRef[] refs : fieldmap) {
            for(DataRef ref : refs) {
               cols.add(ref.getName());
            }
         }
      }

      return cols;
   }

   /**
    * Get object value for a specified column in current row.
    */
   private Object getObject(String col) {
      TableLens base = table.getTable();

      while(base != null) {
         int bcol = Util.findColumn(base, col);

         if(bcol >= 0) {
            int brow = TableTool.getBaseRowIndex(table, base, row);

            if(brow >= 0) {
               return base.getObject(brow, bcol);
            }
         }

         base = base instanceof TableFilter ?
            ((TableFilter) base).getTable() : null;
      }

      return null;
   }

   @Override
   protected int findValueIndex(XTable table, DataRef attr) {
      if(table == null) {
         int idx = valueCols.indexOf(attr.getName());

         if(idx >= 0) {
            return idx;
         }
      }

      return super.findValueIndex(table, attr);
   }

   private CellTableLens table = null;
   private int row = -1;
   private int col = -1;
   private ConditionList list;
   private XCondition condition;
   private Object box;
   private List<String> valueCols = new ArrayList<>();
}
