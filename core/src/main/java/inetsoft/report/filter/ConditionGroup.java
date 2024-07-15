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

import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Catalog;
import inetsoft.util.OrderedMap;
import inetsoft.util.script.*;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;

/**
 * A ConditionGroup stores a list of conditions to be applied to
 * the resulting data.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public class ConditionGroup extends XConditionGroup implements Cloneable, Serializable {
   /**
    * Construct a new instance of Condition Group.
    */
   public ConditionGroup() {
      super();
   }

   /**
    * Construct a new instance of Condition Group.
    */
   public ConditionGroup(ConditionList list) {
      this(0, list);
   }

   /**
    * Construct a new instance of Condition Group. The column index is forcely
    * specified.
    * @param colidx the column index.
    * @param list the condition list.
    */
   public ConditionGroup(int colidx, ConditionList list) {
      this(colidx, list, null);
   }

   public ConditionGroup(ConditionList list, Function<String, Integer> findColumn) {
      list = list.clone();
      list = ConditionUtil.expandBetween(list, null);
      list.validate(true);

      for(int i = 0; i < list.getSize(); i++) {
         HierarchyItem item = list.getItem(i);

         if(item instanceof ConditionItem) {
            ConditionItem citem = (ConditionItem) item;
            DataRef attr = citem.getAttribute();
            String name = attr.getAttribute();
            Object result = findColumn.apply(name);

            if(result == null && attr instanceof ColumnRef) {
               String caption = ((ColumnRef) attr).getCaption();
               result = caption != null ? findColumn.apply(caption) : result;
            }

            int col = result == null ? -1 : (int) result;
            col = col == -1 ? 0 : col;
            XCondition xcond = citem.getXCondition();
            addCondition(col, xcond, citem.getLevel());
         }

         if(item instanceof JunctionOperator) {
            JunctionOperator op = (JunctionOperator) item;
            addOperator(op.getJunction(), op.getLevel());
         }
      }
   }

   public ConditionGroup(ConditionList list, Map<String, Integer> cidx) {
      this(list, (name) -> cidx.get(name));
   }

   /**
    * Construct a new instance of Condition Group. The column index is forcely
    * specified.
    * @param colidx the column index.
    * @param list the condition list.
    */
   public ConditionGroup(int colidx, ConditionList list, Object box) {
      list = list.clone();
      list = ConditionUtil.expandBetween(list, null);
      list.validate(true);

      for(int i = 0; i < list.getSize(); i++) {
         HierarchyItem item = list.getItem(i);

         if(item instanceof ConditionItem) {
            ConditionItem citem = (ConditionItem) item;
            XCondition xcond = citem.getXCondition();
            addCondition(colidx, xcond, citem.getLevel());
         }

         if(item instanceof JunctionOperator) {
            JunctionOperator op = (JunctionOperator) item;
            addOperator(op.getJunction(), op.getLevel());
         }
      }

      if(box != null) {
         execExpressionValues(list, box);
      }
   }

   /**
    * Construct a new instance of Condition Group.
    */
   public ConditionGroup(TableLens table, ConditionList list) {
      this(table, list, null);
   }

   /**
    * Construct a new instance of Condition Group.
    */
   public ConditionGroup(TableLens table, ConditionList list, Object box) {
      list = list.clone();
      list = ConditionUtil.expandBetween(list, null);
      list.validate(true);

      for(int i = 0; i < list.getSize(); i++) {
         HierarchyItem item = list.getItem(i);

         if(item instanceof ConditionItem) {
            ConditionItem cond = (ConditionItem) item;
            DataRef attr = cond.getAttribute();
            int col = findColumn(table, attr);

            // if not found attr on this table, try to found it on table chain
            if(col < 0) {
               if(!hiddenfldmap.containsKey(attr)) {
                  TableLens lens = table;

                  while(lens instanceof TableFilter) {
                     lens = ((TableFilter) lens).getTable();
                     col = Util.findColumn(lens, attr);

                     if(col >= 0) {
                        col = table.getColCount() + hiddenfldmap.size();
                        hiddenfldmap.put(attr, col);
                        break;
                     }
                  }
               }
               else {
                  col = hiddenfldmap.get(attr);
               }
            }

            XCondition xcon = cond.getXCondition();
            addCondition(col, xcon, cond.getLevel());
         }

         if(item instanceof JunctionOperator) {
            JunctionOperator op = (JunctionOperator) item;
            addOperator(op.getJunction(), op.getLevel());
         }
      }

      execExpressionValues(list, box);
   }

   public void execExpressionValues(ConditionList list, Object sandbox) {
      for(int i = 0; i < list.getSize(); i++) {
         ConditionItem cond = list.getConditionItem(i);

         if(cond != null) {
            try {
               execExpressionValues(cond.getAttribute(), cond.getXCondition(), sandbox);
            }
            catch(ScriptException ex) {
               if(sandbox == null) {
                  throw ex;
               }
            }
         }
      }
   }

   /**
    * Init columnIndexMap, should use this map to find column index to avoid nested loop
    * to avoid performance issue.
    */
   private void initColumnIndexMap(XTable table) {
      if(table instanceof CrossTabFilter) {
         columnIndexMap = new ColumnIndexMap2((CrossTabFilter) table);
      }
      else {
         columnIndexMap = new ColumnIndexMap(table, true);
      }
   }

   /**
    * Find column index.
    */
   protected int findColumn(XTable table, DataRef attr) {
      if(attr == null) {
         return -1;
      }

      if("this".equals(attr.getAttribute())) {
         return THIS_FIELD;
      }

      if(columnIndexMap == null) {
         initColumnIndexMap(table);
      }

      if(table instanceof CrossTabFilter) {
         int col = columnIndexMap.getColIndexByHeader(attr.getName());

         if(col != -1) {
            return col;
         }

         return columnIndexMap.getColIndexByHeader2(attr.getName());
      }
      else if(table != null) {
         int col = Util.findColumn(table, attr, columnIndexMap);

         if(col < 0 && attr instanceof VSDataRef) {
            col = Util.findColumn(columnIndexMap, ((VSDataRef) attr).getFullName(), true);
         }

         if(col >= 0) {
            return col;
         }

         Set<Map.Entry<Object, Integer>> entrySet = columnIndexMap.getIdentifierEntrySet();

         if(entrySet != null) {
            for(Map.Entry<Object, Integer> entry : entrySet) {
               if(entry.getKey() == null) {
                  continue;
               }

               String identifier = entry.getKey().toString();

               if(attr.getName().equals(identifier) || identifier.endsWith(attr.getName())) {
                  return entry.getValue();
               }
            }
         }
      }

      return findValueIndex(table, attr);
   }

   /**
    * Find the index of a column (attr) in the values list that will be passed to evaluate().
    */
   protected int findValueIndex(XTable table, DataRef attr) {
      return table == null ? THIS_FIELD : Util.findColumn(table, attr);
   }

   /**
    * Evalu-ate the condition group with a specified table lens row.
    * @param lens the table lens used for evaluation.
    * @param row the row number of the table lens.
    */
   public boolean evaluate(TableLens lens, int row) {
      return evaluate(lens, row, -1);
   }

   /**
    * Evalu-ate the condition group with a specified table lens row.
    * @param lens the table lens used for evaluation.
    * @param row the row number of the table lens.
    * @param col the column number of the table lens.
    */
   public boolean evaluate(TableLens lens, int row, int col) {
      // if field used as values, replace the values with column value
      if(hasField) {
         if(lastTbl != lens) {
            colmap = null;
         }

         lastTbl = lens;

         // optimization, cached column mapping
         if(colmap == null) {
            colmap = new int[fieldmap.size()][];

            for(int i = 0; i < fieldmap.size(); i++) {
               DataRef[] refs = fieldmap.get(i);
               colmap[i] = (refs == null) ? new int[0] : new int[refs.length];

               for(int j = 0; refs != null && j < refs.length; j++) {
                  colmap[i][j] = findColumn(lens, refs[j]);
               }
            }
         }

         // replace condition values with field value
         for(int i = 0; i < fieldmap.size(); i++) {
            int[] cols = colmap[i];

            if(cols.length == 0) {
               continue;
            }

            CondItem item = (CondItem) getItem(i);
            Condition cond = (Condition) item.condition;
            cond.clearCache();

            for(int k = 0; k < cols.length; k++) {
               int index = cols[k];

               if(index == THIS_FIELD) {
                  index = item.getCol();
               }

               if(index >= 0) {
                  cond.setValue(k, lens.getObject(row, index));
               }
            }
         }
      }

      int bcount = lens.getColCount();
      Object[] values = new Object[bcount + hiddenfldmap.size()];

      for(int i = 0; i < bcount; i++) {
         // optimization, avoid unnecessary getObject()
         if(!usedCols.get(i)) {
            continue;
         }

         values[i] = lens.getObject(row, i);

         // for current column, we should use the really value to
         // evaluate the result, for example, a grouped table with
         // group in place, then only the first row of the group will
         // have values, and other rows is empty, and for those empty
         // cell, we should not highlight them
         if(i == col) {
            continue;
         }

         int brow = row;
         int bcol = i;
         TableLens blens = lens;

         // Traverse down through the filters until a non-null value is found
         while(values[i] == null && brow >= 0 && bcol >= 0 && blens instanceof TableFilter) {
            values[i] = blens.getObject(brow, bcol);
            brow = ((TableFilter) blens).getBaseRowIndex(brow);
            bcol = ((TableFilter) blens).getBaseColIndex(bcol);
            blens = ((TableFilter) blens).getTable();
         }
      }

      for(int i = bcount; i < values.length; i++) {
         // optimization, avoid unnecessary getObject()
         if(!usedCols.get(i)) {
            continue;
         }

         DataRef attr = hiddenfldmap.getKey(i - bcount);
         int brow = row;
         int bcol = i;
         TableLens blens = lens;

         while(brow >= 0 && blens instanceof TableFilter) {
            brow = ((TableFilter) blens).getBaseRowIndex(brow);
            blens = ((TableFilter) blens).getTable();
            bcol = Util.findColumn(blens, attr);

            if(bcol >= 0 && brow >= 0) {
               values[i] = blens.getObject(brow, bcol);
               break;
            }
         }
      }

      return evaluate0(values);
   }

   /**
    * Evaluate the condition group with a give object array.
    * @param values the object array used for evaluation.
    */
   public boolean evaluate(Object[] values) {
      // if field used as values, replace the values with column value
      if(hasField) {
         if(colmap == null) {
            colmap = new int[fieldmap.size()][];

            for(int i = 0; i < fieldmap.size(); i++) {
               DataRef[] refs = fieldmap.get(i);
               colmap[i] = (refs == null) ? new int[0] : new int[refs.length];

               for(int j = 0; refs != null && j < refs.length; j++) {
                  colmap[i][j] = findColumn(null, refs[j]);
               }
            }
         }

         // replace condition values with field value
         for(int i = 0; i < fieldmap.size(); i++) {
            int[] cols = colmap[i];

            if(cols.length == 0) {
               continue;
            }

            CondItem item = (CondItem) getItem(i);
            Condition cond = (Condition) item.condition;

            for(int k = 0; k < cols.length; k++) {
               int index = cols[k];

               if(index == THIS_FIELD) {
                  index = item.getCol();
               }

               if(values != null && index >= 0 && index < values.length) {
                  cond.setValue(k, values[index]);
               }
            }
         }
      }

      return evaluate0(values);
   }

   /**
    * Associate a filter condition with the specified column.
    * @param col this column number.
    * @param condition the condition to add
    * @param level the level of the condition will be added
    */
   @Override
   public void addCondition(int col, XCondition condition, int level) {
      addCondition(col, null, condition, level);
   }

   /**
    * Associate a filter condition with the specified column.
    * @param col this column number.
    * @param condition the condition to add
    * @param level the level of the condition will be added
    */
   @Override
   public void addCondition(int col, String subCol, XCondition condition, int level) {
      if(size() == 0 || (getItem(size() - 1) instanceof Operator)) {
         if(condition instanceof Condition) {
            Condition cond = (Condition) condition;
            DataRef[] cols = new DataRef[cond.getValueCount()];

            // build the field list, which is used to replace the value
            // on evaluation
            for(int i = 0; i < cond.getValueCount(); i++) {
               Object val = cond.getValue(i);

               if(val instanceof DataRef) {
                  cols[i] = (DataRef) val;
                  hasField = true;
               }
            }

            fieldmap.setSize(size());
            fieldmap.add(cols);
            cond.setOptimized(!hasField);
         }

         if(col >= 0) {
            usedCols.set(col);
         }

         super.addCondition(col, subCol, condition, level);
      }
   }

   /**
    * Get the value when condition value is ExpressionValue.
    */
   protected void execExpressionValues(DataRef attr, XCondition xcon, Object box) {
      AssetQuerySandbox queryBox = null;

      if(box instanceof AssetQuerySandbox) {
         queryBox = (AssetQuerySandbox) box;
      }
      else {
         queryBox = new AssetQuerySandbox(new Worksheet());
      }

      if(xcon instanceof AssetCondition) {
         AssetCondition acond = (AssetCondition) xcon;
         acond.reset();
         execExpressionValues(acond, queryBox, attr, xcon.getType());
      }
   }

   /**
    * Get the value for condition when the condition value is ExpressionValue.
    * @param acond the condition.
    * @param box the specified asset query sandbox.
    * @param attr the attribute for condition.
    * @param type the data type of the value.
    */
   protected void execExpressionValues(AssetCondition acond, AssetQuerySandbox box,
                                       DataRef attr, String type)
   {
      for(int i = 0; i < acond.getValueCount(); i++) {
         ExpressionValue eval = null;

         if(acond.getValue(i) instanceof ExpressionValue) {
            eval = (ExpressionValue) acond.getValue(i);
         }

         if(eval != null) {
            boolean dateRange = acond.getOperation() == XCondition.DATE_IN;
            Object val = getExpressionVal(eval, box, attr, type, dateRange);
            acond.setDynamicValue(i, val);
         }
      }
   }

   /**
    * Get the value when condition value is ExpressionValue.
    * @param eval the ExpressionValue.
    * @param box the specified asset query sandbox.
    * @param attr the attribute for condition.
    * @param type the data type of the value.
    * @return the expression value.
    */
   private Object getExpressionVal(ExpressionValue eval,
      AssetQuerySandbox box, DataRef attr, String type, boolean dateRange)
   {
      String exp = eval.getExpression();
      ScriptEnv senv = box.getScriptEnv();
      VariableTable vtable = box.getVariableTable();
      String varName = null;
      Object vval = null; // variable/parameter value
      Object val = null;

      if(eval.getType().equals(ExpressionValue.SQL)) {
         int idx1 = exp.indexOf("$(");
         int idx2 = exp.indexOf(")");

         if(idx1 >= 0 && idx2 > idx1 + 2) {
            varName = exp.substring(idx1 + 2, idx2);
            exp = exp.substring(0, idx1) + "parameter." + varName +
               exp.substring(idx2 + 1);
         }
      }
      else {
         int idx1 = exp.indexOf("parameter.");

         if(idx1 >= 0) {
            int j = 0;
            varName = exp.substring(idx1 + 10);

            for(; j < varName.length(); j++) {
               char c = varName.charAt(j);

               if(!Character.isLetterOrDigit(c) && c != 95) {
                  break;
               }
            }

            varName = varName.substring(0, j);
         }
      }

      try {
         vval = vtable.get(varName);
      }
      catch(Exception ex) {
         vval = null;
      }

      Scriptable scope = null;

      try {
         ViewsheetSandbox vbox = box.getViewsheetSandbox();
         Viewsheet vs = vbox == null ? null : vbox.getViewsheet();

         if(varName != null && vval == null) {
            val = attr;
         }
         else {
            final Exception[] ex = { null };
            Object script = scriptCache.get(exp, senv, e -> ex[0] = e);
            // a script may reference a table in vs, which may cause a FormulaTableLens to be
            // created and js expressions evaluated. since rhino is not thread safe, having
            // this recursive execution may cause unpredictable result. create a new scope
            // here to avoid this race condition. (60837)
            scope = box.createAssetQueryScope();
            senv.put("conditionGroupScope", scope);

            val = senv.exec(script, scope, null, vs);

            if(ex[0] != null) {
               throw ex[0];
            }
         }
      }
      catch(Exception ex) {
         String suggestion = senv.getSuggestion(ex, null, scope);
         String msg = "Script error: " + ex.getMessage() +
            (suggestion != null ? "\nTo fix: " + suggestion : "") +
            "\nScript failed:\n" + XUtil.numbering(exp);

         if(LOG.isDebugEnabled()) {
            LOG.debug(msg, ex);
         }
         else {
            LOG.warn(msg);
         }

         String scriptMsg = msg;

         if(eval.getType().equals(ExpressionValue.SQL)) {
            scriptMsg = Catalog.getCatalog().getString(
               "common.conditionMerge") + ex.getMessage();
         }

         throw new ScriptException(scriptMsg);
      }
      finally {
         senv.remove("conditionGroupScope");
      }

      if(val instanceof Object[]) {
         Object[] objs = (Object[]) val;

         for(int j = 0; j < objs.length; j++) {
            objs[j] = getScriptValue(objs[j], type);
         }
      }
      else if(dateRange && val instanceof String) {
         // don't convert, name of date range
      }
      else if(!(val instanceof ColumnRef)) {
         val = getScriptValue(val, type);
      }

      return val;
   }

   /**
    * Get the script value object.
    * @param val the value to be get
    * @param type the data type of the value.
    * @return the script value.
    */
   protected final Object getScriptValue(Object val, String type) {
      if(val instanceof java.util.Date) {
         val = Condition.getDateObject(type, val);
      }
      else if(val != null) {
         val = Condition.getObject(type, val.toString());
      }

      return val;
   }

   @Override
   public ConditionGroup clone() {
      ConditionGroup group = (ConditionGroup) super.clone();
      group.hiddenfldmap = (OrderedMap<DataRef, Integer>) this.hiddenfldmap.clone();
      group.fieldmap = (Vector<DataRef[]>) this.fieldmap.clone();
      group.usedCols = (BitSet) this.usedCols.clone();
      group.colmap = null;
      group.lastTbl = null;
      return group;
   }

   class ColumnIndexMap2 extends ColumnIndexMap {
      public ColumnIndexMap2(CrossTabFilter table) {
         super(table);
      }

      @Override
      protected void init(boolean fuzzy) {
         CrossTabFilter filter = (CrossTabFilter) table;
         int hdrcnt = table.getHeaderRowCount();
         int colCount = table.getColCount();

         if(table.moreRows(hdrcnt)) {
            for(int i = 0; i < table.getColCount(); i++) {
               StringBuilder buffer = new StringBuilder();

               for(int j = 0; j < hdrcnt; j++) {
                  if(j > 0) {
                     buffer.append(".");
                  }

                  buffer.append(table.getObject(j, i));
               }

               String hdr = buffer.toString();

               if(map.get(HEADER) == null) {
                  map.put(HEADER, new HashMap(colCount));
               }

               if(!map.get(HEADER).containsKey(hdr)) {
                  map.get(HEADER).put(hdr, i);
               }

               int hccnt = table.getHeaderColCount();
               String hdr2 = hdr + "." + ((i - hccnt) % filter.getDataColCount());

               if(map.get(HEADER2) == null) {
                  map.put(HEADER2, new HashMap(colCount));
               }

               if(!map.get(HEADER2).containsKey(hdr2)) {
                  map.get(HEADER2).put(hdr2, i);
               }
            }
         }
      }
   }

   private static ScriptCache scriptCache = new ScriptCache(100, 60000);
   protected static final int THIS_FIELD = -2;

   private OrderedMap<DataRef, Integer> hiddenfldmap = new OrderedMap<>();
   protected boolean hasField = false; // true if condition use field as value
   protected Vector<DataRef[]> fieldmap = new Vector<>();
   protected transient int[][] colmap = null; // [] column indices for conditions
   private transient Object lastTbl = null;
   private transient BitSet usedCols = new BitSet();
   private transient ColumnIndexMap columnIndexMap = null;
   private static final Logger LOG = LoggerFactory.getLogger(ConditionGroup.class);
}
