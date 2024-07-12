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

import java.util.ArrayList;

/**
 * AggregateDownTransformer transforms one table assembly by pushing down its
 * aggregates to its base table assembly.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class AggregateDownTransformer extends AbstractTransformer {
   /**
    * Create an instance of AggregateDownTransformer.
    */
   public AggregateDownTransformer() {
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
         table = mtable.getTableAssembly();
         moveDown(mtable, table, desc);
      }

      return true;
   }

   /**
    * Move down selection from mirrow table to base table.
    */
   private void moveDown(TableAssembly ptable, TableAssembly table,
                         TransformationDescriptor desc) {
      AggregateInfo pinfo = ptable.getAggregateInfo();
      AggregateInfo cinfo = table.getAggregateInfo();

      // parent aggregate is empty? useless
      if(pinfo.isEmpty()) {
         return;
      }

      // child aggregate is empty? ignore it for it should be generated
      // by NamedGroupTransformer or some similar transformer else
      if(!cinfo.isEmpty()) {
         return;
      }

      if("false".equals(table.getProperty("aggregate.acceptable"))) {
         return;
      }

      // has named range aggregate and have distinct count?
      if(containsNamedRangeAggregate(ptable) && containsNonCombinable(pinfo)) {
         return;
      }

      String tname = table.getName();
      ColumnSelection pcolumns = ptable.getColumnSelection();
      ColumnSelection columns = table.getColumnSelection();
      ColumnSelection cols = new ColumnSelection();
      GroupRef[] groups = pinfo.getGroups();
      AggregateRef[] aggregates = pinfo.getAggregates();

      for(int i = 0; i < groups.length; i++) {
         GroupRef group = groups[i];
         DataRef pcol = group.getDataRef();

         // avoid too quick click
         if(pcol == null) {
            return;
         }

         // child ref must be found
         ColumnRef[] arr = getChildColumn(pcolumns, columns, tname, pcol);

         // avoid too quick click
         if(arr == null || arr[0] == null || arr[1] == null) {
            return;
         }

         group.setDataRef(arr[0]);

         // replace parent column with new column
         int index = pcolumns.indexOfAttribute(pcol);
         ColumnRef npcol = arr[1];
         pcolumns.setAttribute(index, npcol);
         cols.addAttribute(npcol);
      }

      ConditionListWrapper wrapper1 = ptable.getPostConditionList();
      ConditionListWrapper wrapper2 = ptable.getPostRuntimeConditionList();

      for(int i = 0; i < aggregates.length; i++) {
         AggregateRef aggregate = aggregates[i];
         DataRef pcol = aggregate.getDataRef();
         // child ref must be found
         ColumnRef[] arr = getChildColumn(pcolumns, columns, tname, pcol);
         replaceCondRef(wrapper1, aggregate, arr[0]);
         replaceCondRef(wrapper2, aggregate, arr[0]);
         aggregate.setDataRef(arr[0]);

         // replace parent column with new column
         int index = pcolumns.indexOfAttribute(pcol);
         ColumnRef npcol = arr[1];
         pcolumns.setAttribute(index, npcol);
         cols.addAttribute(npcol);
      }

      int pcnt = pcolumns.getAttributeCount();

      for(int i = 0; i < pcnt; i++) {
         ColumnRef col = (ColumnRef) pcolumns.getAttribute(i);

         if(!cols.containsAttribute(col)) {
            col.setVisible(false);
            cols.addAttribute(col);
         }
      }

      ptable.setColumnSelection(cols, false);
      ptable.setAggregateInfo(new AggregateInfo());
      ptable.setPostConditionList(new ConditionList());
      ptable.setPostRuntimeConditionList(new ConditionList());

      table.setAggregateInfo(pinfo);
      // move post, post runtime conditions from parent table to child
      // table post runtime conditions
      ArrayList list = new ArrayList();
      ConditionListWrapper wrapper = table.getPostRuntimeConditionList();
      list.add(wrapper == null ? null : wrapper.getConditionList());
      list.add(wrapper1 == null ? null : wrapper1.getConditionList());
      list.add(wrapper2 == null ? null : wrapper2.getConditionList());
      ConditionList conds =
         ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
      table.setPostRuntimeConditionList(conds);

      ptable.resetColumnSelection();
      table.resetColumnSelection();
      desc.addInfo(getInfo(tname));
   }

   /**
    * Replace condition ref.
    */
   private void replaceCondRef(ConditionListWrapper wrapper, AggregateRef aref,
                               DataRef nref)
   {
      if(wrapper == null || wrapper.isEmpty()) {
         return;
      }

      ConditionList list = wrapper.getConditionList();

      for(int i = 0; i < list.getSize(); i += 2) {
         ConditionItem citem = (ConditionItem) list.getItem(i);
         DataRef ref = citem.getAttribute();

         if(ref instanceof AggregateRef && ref.equals(aref)) {
            ((AggregateRef) ref).setDataRef(nref);
         }
      }
   }

   /**
    * Check whether the required information is valid for this tranformer.
    */
   private boolean isValid(TableAssembly table, TableAssembly mvtable,
                           TransformationDescriptor desc) {
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
               TransformationFault.moveDownAggregate(otable, mvtable);
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
      return TransformationInfo.aggregateDown(block);
   }
}
