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
package inetsoft.report.script.formula;

import inetsoft.report.internal.Util;
import inetsoft.report.script.TableRow;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An selector for selecting rows within specified groups.
 *
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
abstract class AbstractGroupRowSelector extends RangeSelector {
   /**
    * Create a selector for the groups.
    */
   public AbstractGroupRowSelector(XTable table, Map groupspecs) {
      init(table, groupspecs);
   }

   /**
    * Check if any expression is used as group specifier (group name part).
    */
   public boolean hasGroupExpression() {
      for(int i = 0; i < exprs.length; i++) {
         if(exprs[i] != null) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the group value at the specified row.
    * @param idx the index of the group specification.
    */
   public Object getValue(XTable table, int row, int idx) {
      if(exprs[idx] != null) {
         if(tableRow == null) {
            tableRow = new TableRow(table, row);
         }
         else {
            tableRow.setRow(row);
         }

         // @by larryl, we really should use rowValue in @ expression to be
         // consistent with ? expression, but for backward compatibility,
         // we will allow field[] to be used
         String name = (exprs[idx].indexOf("rowValue[") >= 0) ? "rowValue" : "field";

         return FormulaEvaluator.exec(exprs[idx], null, name, tableRow);
      }

      if(gcolumns[idx] < 0) {
         return null;
      }

      return table.getObject(row, gcolumns[idx]);
   }

   /**
    * Find column.
    */
   private static int findColumn(XTable table, ColumnIndexMap columnIndexMap, Object group) {
      lock.lock();

      try {
         if(AbstractGroupRowSelector.table.get() != table) {
            AbstractGroupRowSelector.table = new WeakReference(table);
            map.clear();
         }

         Integer idx = map.get(group);

         if(idx == null) {
            idx = Util.findColumn(columnIndexMap, group);
            map.put(group, idx);
         }

         return idx;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Initialize the group columns and values.
    */
   protected void init(XTable table, Map groupspecs) {
      Iterator iter = groupspecs.keySet().iterator();
      gcolumns = new int[groupspecs.size()];
      exprs = new String[gcolumns.length];
      values = new Object[gcolumns.length];
      ColumnIndexMap columnIndexMap = this.columnIndexMap;

      if(columnIndexMap == null || columnIndexMap.getTable() != table) {
         columnIndexMap = this.columnIndexMap = new ColumnIndexMap(table, true);
      }

      for(int i = 0; iter.hasNext(); i++) {
         Object group = iter.next();
         NamedCellRange.GroupSpec spec = (NamedCellRange.GroupSpec) groupspecs.get(group);

         if(group.toString().startsWith("=")) {
            exprs[i] = group.toString().substring(1);
            gcolumns[i] = -1;
         }
         else {
            int idx = findColumn(table, columnIndexMap, group);

            if(idx < 0) {
               missingCol = true;
               LOG.warn(
                  "Group cell:" + group + " is not " +
                  "suitable. Please verify that all fields are from " +
                  "the same data source or have been properly joined.");
            }

            gcolumns[i] = idx;
         }

         if(!spec.isByValue()) {
            LOG.debug(
               "Table has not grouping, force group " +
               "qualification to be by value");
         }

         values[i] = spec.getValue();
      }
   }

   private static final Map<Object, Integer> map = new HashMap();
   private static final Lock lock = new ReentrantLock();
   private static WeakReference table = new WeakReference(null);
   protected String[] exprs; // group columns expressions
   protected int[] gcolumns; // group columns
   protected Object[] values; // group values
   protected boolean missingCol = false; // if a column is not found
   private transient TableRow tableRow;
   private transient ColumnIndexMap columnIndexMap;
   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractGroupRowSelector.class);
}
