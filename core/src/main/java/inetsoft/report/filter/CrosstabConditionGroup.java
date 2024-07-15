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

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.uql.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;

import java.util.*;

/**
 * A crosstab condition group provides the ability to calculate a crosstab
 * condition list in a crosstab filter.
 * <p>
 * The crosstab condition list is relatively special for the available fields
 * are not in a normal table, but in a two-dimension crosstab filter.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
class CrosstabConditionGroup extends ConditionGroup {
   /**
    * Construct a new instanceof CrosstabConditionGroup using crosstab filter.
    * @param table the specified crosstab filter.
    * @param row the specified row.
    * @param col the specified col.
    * @param list the specified condition list.
    */
   public CrosstabConditionGroup(CrossFilter table, int row, int col,
                                 ConditionList list, Object box) {
      TableDataDescriptor descriptor = table.getDescriptor();

      this.table = table;
      this.row = row;
      this.col = col;

      TableDataPath tpath = descriptor.getCellDataPath(row, col);

      // @by davidd, feature1291159027762 Allow conditions to reference
      // all aggregate fields, not just the one located at this data path.
      if(tpath.getType() == TableDataPath.SUMMARY || tpath.getType() == TableDataPath.GRAND_TOTAL) {
         columns = getPathWithAllAggregates(tpath);
      }
      else {
         columns = tpath.getPath();
      }

      for(int i = 0; i < list.getSize(); i++) {
         HierarchyItem item = (HierarchyItem) list.getItem(i);

         if(item instanceof ConditionItem) {
            ConditionItem cond = (ConditionItem) item;
            DataRef attr = cond.getAttribute();
            String entity = attr.getEntity();
            String apath = entity == null ? attr.getAttribute() :
               entity + "." + attr.getAttribute();
            int index = -1;  // column index in columns

            for(int j = 0; j < columns.length; j++) {
               if(isSameField(columns[j], apath)) {
                  index = j;
                  break;
               }
            }

            if(index == -1 && entity != null) {
               apath = attr.getAttribute();

               for(int j = 0; j < columns.length; j++) {
                  if(isSameField(columns[j], apath)) {
                     index = j;
                     break;
                  }
               }
            }

            // for backward compatibility
            if(index == -1 && attr instanceof VSAggregateRef) {
               apath = ((VSAggregateRef) attr).getFullName(false);

               for(int j = 0; j < columns.length; j++) {
                  if(isSameField(columns[j], apath)) {
                     index = j;
                     break;
                  }
               }
            }

            if(index == -1 && attr instanceof VSDimensionRef &&
               (attr.getRefType() & DataRef.CUBE_DIMENSION) == DataRef.CUBE_DIMENSION)
            {
               String fullName = ((VSDimensionRef) attr).getFullName();

               for(int j = 0; j < columns.length; j++) {
                  if(isSameField(columns[j], fullName)) {
                     index = j;
                     break;
                  }
               }
            }

            boolean isMergeCel = false;

            if(index == -1 && table.getDcMergePartRef() != null) {
               String fullName = table.getDcMergePartRef();

               for(int j = 0; j < columns.length; j++) {
                  if(isSameField(columns[j], fullName)) {
                     index = j;
                     isMergeCel = true;
                     break;
                  }
               }
            }

            if(index != -1 && attr instanceof CalculateRef) {
               calcRefs.add(apath);
            }

            XCondition condition = (XCondition) cond.getXCondition().clone();
            colIdx = index;
            addCondition(index, isMergeCel ? apath : null, condition, cond.getLevel());
            conditions.add(condition);
            execExpressionValues(attr, condition, box);
         }

         if(item instanceof JunctionOperator) {
            JunctionOperator op = (JunctionOperator) item;
            addOperator(op.getJunction(), op.getLevel());
         }
      }
   }

   private boolean isSameField(String name, String path) {
      path = path.startsWith("None(") && path.endsWith(")") ? path.substring(5, path.length() - 1) : path;
      name = name.startsWith("None(") && name.endsWith(")") ? name.substring(5, name.length() - 1) : name;

      return Tool.equals(path, name);
   }

   /**
    * Evaluate the crosstab condition group.
    * @return <tt>true</tt> if satisfies condition, <tt>false</tt> otherwise.
    */
   public boolean evaluate() {
      List vals = new ArrayList();
      List keys = new ArrayList();
      Map map = table.getKeyValuePairs(row, col, new HashMap());

      for(int i = 0; i < columns.length; i++) {
         String key = columns[i];
         Object val = map.get(key);

         if(val == null && calcRefs.contains(key)) {
            val = table.getObject(row, col);
         }

         vals.add(val);
         keys.add(key);
      }

      Object[] values = vals.toArray(new Object[vals.size()]);

      if(hasField) {
         if(colmap == null) {
            colmap = new int[fieldmap.size()][];

            for(int i = 0; i < fieldmap.size(); i++) {
               DataRef[] refs = fieldmap.get(i);
               colmap[i] = (refs == null) ? new int[0] : new int[refs.length];

               for(int j = 0; refs != null && j < refs.length; j++) {
                  colmap[i][j] = findColumn(keys, refs[j]);
               }
            }
         }

         // replace condition values with field value
         for(int i = 0, cidx = 0; i < fieldmap.size(); i++) {
            int[] cols = colmap[i];

            if(cols.length == 0) {
               continue;
            }

            XCondition condition = conditions.get(cidx++);

            if(condition instanceof Condition) {
               Condition cond = (Condition) condition;

               for(int k = 0; k < cols.length; k++) {
                  int index = cols[k];

                  if(index == THIS_FIELD) {
                     index = colIdx;
                  }

                  if(values != null && index >= 0 && index < values.length) {
                     cond.setValue(k, values[index]);
                  }
               }
            }
         }
      }

      return evaluate0(values);
   }

   /**
    * Find the column.
    * @param keys the specified keys.
    * @param attr the specified attribute.
    * @return the found column.
    */
   private int findColumn(List keys, DataRef attr) {
      if(attr == null) {
         return -1;
      }

      if("this".equals(attr.toString())) {
         return THIS_FIELD;
      }

      for(int i = 0; i < keys.size(); i++) {
         if(keys.get(i).equals(attr.getName())) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Returns the column names of all relevant group headers and
    * aggregate headers for a specified TableDataPath.
    */
   private String[] getPathWithAllAggregates(TableDataPath tpath) {
      String[] path = tpath.getPath();
      Object[] aggregates = table.getHeaders();
      // @by davyc, why shrink the last path? this cause grand total
      // highlight not apply
      // fix bug1304501370429
      String[] result = new String[path.length + aggregates.length];

      // Copy the group headers into the result
      int groupHeaderCount = path.length;
      System.arraycopy(path, 0, result, 0, groupHeaderCount);
      System.arraycopy(aggregates, 0, result, groupHeaderCount, aggregates.length);

      return result;
   }

   private CrossFilter table = null;
   private String[] columns;  // the columns whose values will be evaluated
   private int row = -1;
   private int col = -1;
   private List<XCondition> conditions = new ArrayList<>();
   private int colIdx = -1;
   private List<String> calcRefs = new ArrayList<>();
}
