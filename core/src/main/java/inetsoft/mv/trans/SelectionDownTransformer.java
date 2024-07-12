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
package inetsoft.mv.trans;

import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.CalculateRef;

import java.util.*;

/**
 * SelectionDownTransformer transforms one table assembly by pushing its
 * runtime selections down to its base table assembly, so that this table
 * assembly could hit mv, and more over, the selection could be evaluated by
 * mv rather than post process.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class SelectionDownTransformer extends AbstractTransformer {
   /**
    * Create an instance of SelectionDownTransformer.
    */
   public SelectionDownTransformer() {
      super();
   }

   /**
    * Transform the table assembly.
    * @return true if successful, false otherwise.
    */
   @Override
   public boolean transform(TransformationDescriptor desc) {
      TableAssembly table = desc.getTable(false);
      TableAssembly mvtable = desc.getMVTable();
      mvtable = mvtable == null ? table : mvtable;
      boolean valid = isValid(table, mvtable, desc);

      if(!valid) {
         return false;
      }

      while(table != mvtable) {
         MirrorTableAssembly mtable = (MirrorTableAssembly) table;
         table = ((MirrorTableAssembly) mtable).getTableAssembly();
         // continue move down not matter successful or not,
         // so the AggregateDownTransformer will work correct
         moveDown(mtable, table, desc);
      }

      return true;
   }

   /**
    * Move down selection from mirror table to base table.
    */
   public boolean moveDown(TableAssembly ptable, TableAssembly table,
                           TransformationDescriptor desc)
   {
      // post condition always post process, and cannot be converted
      // to pre condition, so here we don't need care about it
      ConditionListWrapper wrapper = ptable.getPreRuntimeConditionList();

      if(isEmptyFilter(wrapper)) {
         return true;
      }

      ColumnSelection pcolumns = ptable.getColumnSelection();
      ColumnSelection columns = table.getColumnSelection();
      String tname = table.getName();
      Map<DataRef, ColumnRef[]> pmap = new HashMap();
      AggregateInfo cainfo = table.getAggregateInfo();
      wrapper = (ConditionListWrapper) wrapper.clone();

      for(int i = 0; i < wrapper.getConditionSize(); i += 2) {
         ConditionItem item = wrapper.getConditionItem(i);
         DataRef pcol = item.getAttribute();

         // calc field condition not supported in mv. (51346)
         if(pcol instanceof CalculateRef) {
            return false;
         }

         // child ref must be found
         ColumnRef[] arr = getChildColumn(pcolumns, columns, tname, pcol);

         if(arr == null) {
            continue;
         }

         // if parent table condition column used sub-table's aggregate column,
         // do not move down, fix bug1309172158406
         if(cainfo != null && cainfo.getAggregate(arr[0]) != null) {
            return false;
         }

         // this selection comes from operations such as brush, zoom,
         // and the like, so we need not move down WSColumn (no WSColumn)
         item.setAttribute(arr[0]);

         // replace parent column with new column (e.g. date range ref)
         if(arr[1] != null) {
            pmap.put(pcol, arr);
         }
      }

      AggregateInfo ainfo = ptable.getAggregateInfo();
      // clone it, so original will not be changed if failed
      ainfo = (AggregateInfo) ainfo.clone();
      pcolumns = (ColumnSelection) pcolumns.clone();

      for(DataRef pcol : pmap.keySet()) {
         ColumnRef[] arr = pmap.get(pcol);
         int index = pcolumns.indexOfAttribute(pcol);
         DataRef childcol = arr[0].getDataRef();
         ColumnRef npcol = arr[1];

         pcolumns.setAttribute(index, npcol);
         // group on a column should be replaced by the new column that's
         // created for the condition
         replaceGroup(ainfo, pcol, npcol);

         // if a date range column is created in the child, the group
         // info must be in sync or the column won't be visible to ptable
         if(childcol instanceof DateRangeRef) {
            DataRef baseref = ((DateRangeRef) childcol).getDataRef();
            AggregateInfo ainfo0 = table.getAggregateInfo();

            // if child table contains aggregate, we should not add this
            // column to group directly, because this will cause the child
            // table get different result compare with before
            if(ainfo0 != null && !ainfo0.isEmpty() && (ainfo0.getGroup(childcol) == null)) {
               return false;
            }
            // if child table without aggregate, and this column should be
            // visible in parent table, we have two choice:
            // 1: create aggregate info, and make all column as group
            // 2: don't push down aggregate from parent table to this table
            else if(ainfo0 == null || ainfo0.isEmpty()) {
               table.setProperty("aggregate.acceptable", "false");
            }

            /*
            if(ainfo0.getGroup(childcol) == null) {
               table.getAggregateInfo().addGroup(new GroupRef(childcol));
            }
            */
         }
      }

      ptable.setAggregateInfo(ainfo);
      ptable.setColumnSelection(pcolumns);
      ConditionList conditions = wrapper.getConditionList();
      ConditionListWrapper cwrapper = table.getPreRuntimeConditionList();

      if(!isEmptyFilter(cwrapper)) {
         List list = new ArrayList();
         list.add(conditions);
         list.add(cwrapper.getConditionList());
         cwrapper = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
      }
      else {
         cwrapper = conditions;
      }

      table.setPreRuntimeConditionList(cwrapper);
      ptable.setPreRuntimeConditionList(new ConditionList());

      ColumnSelection pcols0 = ptable.getColumnSelection(true);

      ptable.resetColumnSelection();
      table.resetColumnSelection();

      if(ptable instanceof MirrorTableAssembly && ptable.getAggregateInfo().isEmpty()) {
         // keep public columns like date range ref
         ((MirrorTableAssembly) ptable).updateColumnSelection(true);
         ColumnSelection pcols2 = ptable.getColumnSelection(true);

         // if a selection is pushed down, the column name could be changed
         // for example, a table with columns: orderdate,quantity
         // if a condition (e.g. brushing) is pushed down:
         //    Year(orderdate) == 2001
         // the child table would contain a date range ref of Year(orderdate)
         // and the ptable would return columns as: Year(orderdate),quantity
         // but the assumption is that after transformation, the table should
         // return the same columns as before, so we mark the Year(orderdate)
         // to have an alias of the original name, e.g. orderdate
         for(DataRef pcol : pmap.keySet()) {
            DataRef ref = ((ColumnRef) pcol).getDataRef();

            if(ref instanceof DateRangeRef) {
               DateRangeRef dref = (DateRangeRef) ref;
               DataRef baseref = dref.getDataRef();
               String dname = dref.getName();
               String bname = baseref.getName();
               DataRef dref0 = pcols0.getAttribute(dname, true);
               DataRef bref0 = pcols0.getAttribute(bname, true);
               DataRef dref2 = pcols2.getAttribute(dname, true);
               DataRef bref2 = pcols2.getAttribute(bname, true);

               if(dref0 == null && bref0 != null &&
                  dref2 != null && bref2 == null)
               {
                  ((ColumnRef) dref2).setAlias(bname);
               }
            }
         }
      }

      desc.addInfo(getInfo(tname));
      return true;
   }

   /**
    * Replace the column in the group with the new column
    */
   private void replaceGroup(AggregateInfo ainfo, DataRef col, DataRef ncol) {
      GroupRef group = ainfo.getGroup(col);

      if(group != null) {
         group.setDataRef(ncol);
      }
   }

   /**
    * Check whether the required information is valid for this tranformer.
    */
   private boolean isValid(TableAssembly table, TableAssembly mvtable,
                           TransformationDescriptor desc)
   {
      TableAssembly otable = table;

      while(true) {
         if(table == mvtable) {
            return true;
         }

         if(table instanceof MirrorTableAssembly) {
            table = ((MirrorTableAssembly) table).getTableAssembly();
         }
         else {
            TransformationFault fault =
               TransformationFault.moveDownSelection(otable, mvtable);
            addFault(desc, fault, otable.getName(), null);
            return false;
         }
      }
   }

   /**
    * Get the transformation message for this transformer.
    * @param block the data block.
    */
   @Override
   protected TransformationInfo getInfo(String block) {
      return TransformationInfo.selectionDown(block);
   }
}
