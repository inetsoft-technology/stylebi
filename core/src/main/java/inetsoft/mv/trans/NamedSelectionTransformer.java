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
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XNamedGroupInfo;

import java.util.HashSet;

/**
 * NamedSelectionTransformer transforms one table assembly by transforming its
 * named selections if any, so that the table assembly could hit mv.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class NamedSelectionTransformer extends AbstractTransformer {
   /**
    * Constructor.
    */
   public NamedSelectionTransformer() {
      super();
   }

   /**
    * Transform the table assembly.
    * @return true if successful, false otherwise.
    */
   @Override
   public boolean transform(TransformationDescriptor desc) {
      if(desc.isAnalytic()) {
         return true;
      }

      TableAssembly top = desc.getTable(false);
      TableAssembly mvtable = desc.getMVTable();
      TableAssembly table = findTable(top, mvtable);

      if(table == null) {
         return true;
      }

      // for named condition, post condition should not exist because
      // named ref only exist in viewsheet table
      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();
      ConditionList conds = wrapper.getConditionList();

      for(int i = 0; i < conds.getSize(); i += 2) {
         fixCondition(table, (ConditionItem) conds.getItem(i));
      }

      desc.addInfo(getInfo(table.getName()));

      return true;
   }

   /**
    * Check if contains named selection.
    */
   private boolean containsNamedSelection(TableAssembly table) {
      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();

      if(isEmptyFilter(wrapper)) {
         return false;
      }

      ConditionList conds = wrapper.getConditionList();

      for(int i = 0; i < conds.getSize(); i += 2) {
         ConditionItem citem = (ConditionItem) conds.getItem(i);
         ColumnRef column = (ColumnRef) citem.getAttribute();
         DataRef ref = column.getDataRef();

         if(ref instanceof NamedRangeRef) {
            return true;
         }
      }

      return false;
   }

   /**
    * Find the table to transform named selection.
    */
   private TableAssembly findTable(TableAssembly top, TableAssembly bottom) {
      Worksheet ws = top.getWorksheet();

      if(containsNamedSelection(top)) {
         return top;
      }
      else if(top.getName().equals(bottom.getName()) ||
         !(top instanceof MirrorTableAssembly))
      {
         return null;
      }

      MirrorTableAssembly mtable = (MirrorTableAssembly) top;
      String aname = mtable.getAssemblyName();
      TableAssembly child = (TableAssembly) ws.getAssembly(aname);
      return findTable(child, bottom);
   }

   /**
    * Fix condition by renaming column and transforming condition values.
    */
   private void fixCondition(TableAssembly table, ConditionItem item) {
      ColumnRef column = (ColumnRef) item.getAttribute();
      DataRef ref = column.getDataRef();
      ColumnSelection cols = table.getColumnSelection(false);

      if(!(ref instanceof NamedRangeRef)) {
         return;
      }

      NamedRangeRef rref = (NamedRangeRef) ref;
      DataRef base = rref.getDataRef();
      ColumnRef ncolumn = (ColumnRef) cols.findAttribute(base);
      item.setAttribute(ncolumn);
      XNamedGroupInfo range = rref.getNamedGroupInfo();
      Condition cond = (Condition) item.getXCondition();
      HashSet set = new HashSet();

      for(int i = 0; i < cond.getValueCount(); i++) {
         Object val = cond.getValue(i);
         boolean found = false;

         for(String group : range.getGroups()) {
            if(group.equals(val)) {
               found = true;
               ConditionList clist = range.getGroupCondition(group);

               for(int j = 0; clist != null && j < clist.getSize(); j += 2) {
                  ConditionItem citem = (ConditionItem) clist.getItem(j);
                  Condition condition = (Condition) citem.getXCondition();

                  for(int k = 0; k < condition.getValueCount(); k++) {
                     set.add(condition.getValue(k));
                  }
               }

               break;
            }
         }

         if(!found) {
            set.add(val);
         }
      }

      Object[] vals = new Object[set.size()];
      set.toArray(vals);

      if(vals.length > 1) {
         cond.removeAllValues();

         for(Object val : vals) {
            cond.addValue(val);
         }

         cond.setOperation(XCondition.ONE_OF);
      }
      else {
         cond.setValue(0, vals[0]);
      }

      // maintain proper data type
      cond.setType(ncolumn.getDataType());
   }

   /**
    * Get the transformation message for this transformer.
    * @param block the data block.
    */
   @Override
   protected TransformationInfo getInfo(String block) {
      return TransformationInfo.namedSelection(block);
   }
}
