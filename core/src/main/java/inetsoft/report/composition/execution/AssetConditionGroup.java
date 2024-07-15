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
    * Evaluate the condition group with a specified table lens row.
    * @param lens the table lens used for evaluation.
    * @param row the row number of the table lens.
    */
   @Override
   public boolean evaluate(TableLens lens, int row) {
      for(int i = 0; i < sarr.length; i++) {
         sarr[i].setCurrentRow(row);
      }

      return super.evaluate(lens, row);
   }

   protected AssetCondition[] sarr; // sub query asset conditions
   private transient ColumnIndexMap columnIndexMap = null;
   private static final Logger LOG = LoggerFactory.getLogger(AssetConditionGroup.class);
}
