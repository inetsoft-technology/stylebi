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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.filter.ConditionGroup;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.DataRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A single deferred JAVASCRIPT condition value that references field[...]/field. and therefore
 * cannot be resolved once per query like every other condition value -- it is instead
 * re-evaluated against the current row on every call to {@link AssetConditionGroup#evaluate}
 * (WBS-042).
 */
final class FieldExprBinding {
   FieldExprBinding(AssetCondition cond, int index, ExpressionValue eval, String type,
                    AssetQuerySandbox box)
   {
      this.cond = cond;
      this.index = index;
      this.eval = eval;
      this.type = type;
      this.box = box;
   }

   final AssetCondition cond;
   final int index;
   final ExpressionValue eval;
   final String type;
   final AssetQuerySandbox box;
}

/**
 * Asset condition group executes asset conditions.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class AssetConditionGroup extends ConditionGroup {
   /**
    * Construct a new instance of Condition Group.
    */
   public AssetConditionGroup() {
      super();
   }

   /**
    * Construct a new instance of Condition Group.
    * @param list the specified condition list.
    * @param mode the specified asset query mode.
    * @param box the specified asset query sandbox.
    */
   public AssetConditionGroup(ConditionList list, int mode, AssetQuerySandbox box) {
      this(null, list, mode, box, -1);
   }

   /**
    * Construct a new instance of Condition Group.
    * @param table the specified table lens.
    * @param list the specified condition list.
    * @param mode the specified asset query mode.
    * @param box the specified asset query sandbox.
    * @param ts touch timestamp of data changes.
    */
   public AssetConditionGroup(TableLens table, ConditionList list, int mode,
                              AssetQuerySandbox box, long ts) {
      super();
      list = list.clone();
      list = ConditionUtil.expandBetween(list, null);
      list.validate(true);
      List sconds = new ArrayList();

      for(int i = 0; i < list.getSize(); i++) {
         HierarchyItem item = list.getItem(i);

         if(item instanceof ConditionItem) {
            ConditionItem citem = (ConditionItem) item;
            DataRef attr = citem.getAttribute();
            int col = findColumn(table, attr);
            XCondition cond = citem.getXCondition();

            if(cond instanceof AssetCondition) {
               AssetCondition acond = (AssetCondition) cond;
               acond.reset();
               SubQueryValue sub = acond.getSubQueryValue();

               if(sub != null) {
                  TableAssembly tassembly = sub.getTable();
                  tassembly.setDistinct(true);
                  sub.setOperation(acond.getOperation());

                  try {
                     AssetQuery query = AssetQuery.createAssetQuery(
                        tassembly, AssetQuery.fixSubQueryMode(mode),
                        box, true, ts, true, false);
                     query.setSubQuery(false);
                     VariableTable vtable = (VariableTable) box.getVariableTable().clone();
                     TableLens stable = query.getTableLens(vtable);
                     stable = AssetQuery.shuckOffFormat(stable);
                     acond.initMainTable(table);
                     acond.initSubTable(stable);
                     sconds.add(acond);
                  }
                  catch(Exception ex) {
                     LOG.warn("Failed to execution condition sub-query", ex);

                     // ignore the condition item
                     col = -1;
                  }
               }
               else {
                  execExpressionValues(acond, box, attr, cond.getType());
               }
            }

            addCondition(col, cond, citem.getLevel());
         }

         if(item instanceof JunctionOperator) {
            JunctionOperator op = (JunctionOperator) item;
            addOperator(op.getJunction(), op.getLevel());
         }
      }

      sarr = new AssetCondition[sconds.size()];
      sconds.toArray(sarr);
   }

   /**
    * Find the column.
    * @param table the specified table.
    * @param attr the specified attribute.
    * @return the found column.
    */
   @Override
   protected int findColumn(XTable table, DataRef attr) {
      if(columnIndexMap == null) {
         columnIndexMap = new ColumnIndexMap(table);
      }

      return table != null ? AssetUtil.findColumn(table, attr, columnIndexMap) : -1;
   }

   /**
    * Same as the base class, except a JAVASCRIPT-typed value referencing field[...]/field. is
    * deferred (not resolved here) into {@link #fieldExprBindings}, to be re-evaluated against a
    * real row on every call to {@link #evaluate} instead of being resolved once, like every other
    * condition value, before any row is known (WBS-042). Overridden here rather than in the
    * shared {@link ConditionGroup} base class deliberately -- see the "Scope decision" in
    * bug-wbs042/DESIGN.md for why this stays narrowly opt-in to worksheet/asset conditions.
    */
   @Override
   protected void execExpressionValues(AssetCondition acond, AssetQuerySandbox box,
                                       DataRef attr, String type)
   {
      for(int i = 0; i < acond.getValueCount(); i++) {
         Object raw = acond.getValue(i);

         if(!(raw instanceof ExpressionValue)) {
            continue;
         }

         ExpressionValue eval = (ExpressionValue) raw;

         if(eval.referencesField()) {
            fieldExprBindings.add(new FieldExprBinding(acond, i, eval, type, box));
            continue;
         }

         boolean dateRange = acond.getOperation() == XCondition.DATE_IN;
         Object val = getExpressionVal(eval, box, attr, type, dateRange);
         acond.setDynamicValue(i, val, false);
      }
   }

   /**
    * Evaluate the condition group with a specified table lens row.
    * @param lens the table lens used for evaluation.
    * @param row the row number of the table lens.
    */
   @Override
   public boolean evaluate(TableLens lens, int row) {
      for(int i = 0; i < sarr.length; i++) {
         sarr[i].setCurrentRow(row);
      }

      for(FieldExprBinding binding : fieldExprBindings) {
         boolean dateRange = binding.cond.getOperation() == XCondition.DATE_IN;
         Object val = evalFieldExpression(
            binding.eval, binding.box, binding.type, dateRange, lens, row);
         binding.cond.clearCache();

         // AssetCondition.evaluate(Object) short-circuits a ONE_OF/CONTAINS condition through its
         // own one-value cache (lvalue/lresult) whenever isOptimized() is true, bypassing
         // clearCache() above entirely (that only resets Condition's sortedValues). A field[...]
         // value is an ExpressionValue, not a DataRef, so ConditionGroup#addCondition's hasField
         // detection -- the mechanism that already disables this same cache for the pre-existing
         // "field-as-value" case -- never sees it and never turns optimization off. Do so here,
         // every row, so two consecutive rows that happen to share the same tested column value
         // can never wrongly reuse each other's cached result merely because this condition's own
         // field[...]-bound value differed between them.
         binding.cond.setOptimized(false);
         binding.cond.setValue(binding.index, val);
      }

      return super.evaluate(lens, row);
   }

   protected AssetCondition[] sarr; // sub query asset conditions
   private final List<FieldExprBinding> fieldExprBindings = new ArrayList<>();
   private transient ColumnIndexMap columnIndexMap = null;
   private static final Logger LOG = LoggerFactory.getLogger(AssetConditionGroup.class);
}
