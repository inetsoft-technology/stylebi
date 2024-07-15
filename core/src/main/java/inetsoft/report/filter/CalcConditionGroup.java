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
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.script.formula.FormulaFunctions;
import inetsoft.uql.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.util.Tool;

import java.awt.*;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

/**
 * A calc table condition group provides the ability to calculate a calc table
 * condition list in a runtime calc table.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
class CalcConditionGroup extends ConditionGroup {
   /**
    * Construct a new instanceof CalcConditionGroup using calc table.
    * @param table the specified calc table.
    * @param row the specified row.
    * @param col the specified col.
    * @param list the specified condition list.
    */
   public CalcConditionGroup(RuntimeCalcTableLens table, int row, int col,
                             ConditionList list, Object box) {
      TableDataDescriptor descriptor = table.getDescriptor();

      this.table = table;
      this.row = row;
      this.col = col;
      this.fields = table.getCellNames();
      this.namedGroupFields = getNamedGroupFields();

      for(int i = 0; i < list.getSize(); i++) {
         HierarchyItem item = (HierarchyItem) list.getItem(i);

         if(item instanceof ConditionItem) {
            ConditionItem cond = (ConditionItem) item;
            DataRef attr = cond.getAttribute();
            int index = -1;
            conditionCells.add(attr.getAttribute());
            XCondition xcond = cond.getXCondition();

            if(xcond instanceof Condition) {
               List values = ((Condition) xcond).getValues();

               if(values != null) {
                  for(Object val : values) {
                     if(val instanceof DataRef) {
                        conditionCells.add(((DataRef) val).getAttribute());
                     }
                  }
               }
            }

            for(int j = 0; j < fields.length; j++) {
               if(fields[j].equals(attr.getAttribute())) {
                  index = j;
                  break;
               }
            }

            condition = (XCondition) cond.getXCondition().clone();
            colIdx = index;
            addCondition(index, condition, cond.getLevel());
            execExpressionValues(attr, condition, box);
         }

         if(item instanceof JunctionOperator) {
            JunctionOperator op = (JunctionOperator) item;
            addOperator(op.getJunction(), op.getLevel());
         }
      }
   }

   /**
    * Evaluate the crosstab condition group.
    * @return <tt>true</tt> if satisfies condition, <tt>false</tt> otherwise.
    */
   public boolean evaluate() {
      List keys = new ArrayList();
      List vals = new ArrayList();
      Map keyValues = table.getKeyValuePairs(row, col, conditionCells);

      for(int i = 0; i < fields.length; i++) {
         String key = fields[i];
         Object val = keyValues.get(key);
         keys.add(key);

         // when exist namedgroup, condition value is string.
         if(namedGroupFields.contains(key) && !(val instanceof String)) {
            // since highlight condition is base on RuntimeCalcTablelens which data
            // haven't be formated, but user may add highlight condition according to what
            // they see which have already applied the default date format, so here apply
            // default date format to make sure highlight be rightly hitted.
            if(formatInfoMap.containsKey(key) && val instanceof Date) {
               try {
                  Format fmt = formatInfoMap.get(key);
                  val = fmt.format(val);
               }
               catch(Exception ignore) {
               }
            }

            val = Tool.getData(String.class, val);
         }

         vals.add(val);
      }

      Object[] values = vals.toArray(new Object[vals.size()]);

      // @by yanie: bug1418942478612
      // If the highlight condition contains field as value, replace the
      // the value of condition with field value
      // Note: the field value condition can be defined in vs
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
         for(int i = 0; i < fieldmap.size(); i++) {
            int[] cols = colmap[i];

            if(cols.length == 0) {
               continue;
            }

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

      return evaluate(values);
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

   private List<String> getNamedGroupFields() {
      List<String> namedGroupFields = new ArrayList<>();
      FormulaTable element = table.getElement();
      ColumnSelection cols = null;

      if(element instanceof CalcTableVSAssembly) {
         CalcTableVSAssembly assembly = (CalcTableVSAssembly) element;
         cols = assembly.getColumnSelection((CalcTableVSAssemblyInfo) assembly.getVSAssemblyInfo());
      }

      TableLayout layout = table.getElement().getTableLayout();

      for(int i = 0; i < fields.length; i++) {
         Point pos = table.getCellLocation(fields[i]);
         int row = pos.y;
         int col = pos.x;
         CellBinding binding = layout.getCellBinding(row, col);

         if(binding.getBType() != TableCellBinding.GROUP) {
            continue;
         }

         if(binding.getType() == TableCellBinding.BIND_FORMULA) {
            String formula = binding.getValue();

            if(formula == null || !formula.startsWith("mapList(")) {
               continue;
            }

            namedGroupFields.add(fields[i]);
            int idx = formula.indexOf("rounddate");

            if(idx == -1) {
               continue;
            }

            String subStr = formula.substring(idx);
            String[] arr = subStr.split(",");

            if(arr.length > 0 && arr[0].indexOf("=") != -1) {
               String roundDate = arr[0].substring(arr[0].indexOf("=") + 1);
               int dlevel = FormulaFunctions.getCalendarLevel(roundDate);
               DataRef dref = cols == null ? null : cols.getAttribute(fields[i]);
               String dtype = dref == null ? XSchema.DATE : dref.getDataType();
               SimpleDateFormat dfmt = XUtil.getDefaultDateFormat(dlevel, dtype);

               if(dfmt != null) {
                  formatInfoMap.put(fields[i], dfmt);
               }
            }

            continue;
         }

         TableCellBinding cellBinding = (TableCellBinding) binding;

         if(cellBinding.getOrderInfo(false) != null &&
            cellBinding.getOrderInfo(false).getRealNamedGroupInfo() != null)
         {
            namedGroupFields.add(fields[i]);

            int dlevel = cellBinding.getDateOption();
            Format fmt = table.getDefaultFormat(row, col);

            if(dlevel > 0 && fmt != null) {
               formatInfoMap.put(fields[i], fmt);
            }
         }
      }

      return namedGroupFields;
   }

   private RuntimeCalcTableLens table = null;
   private String[] fields;
   List<String> namedGroupFields;
   HashMap<String, Format> formatInfoMap = new HashMap<>();
   private int row = -1;
   private int col = -1;
   private int colIdx = -1;
   private XCondition condition;
   // all cell names used in conditions
   private Set<String> conditionCells = new HashSet<>();
}
