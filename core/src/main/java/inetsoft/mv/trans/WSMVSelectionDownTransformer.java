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
package inetsoft.mv.trans;

import inetsoft.mv.WSMVTransformer;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.*;
import inetsoft.util.Tool;

import java.util.*;

public class WSMVSelectionDownTransformer extends AbstractTransformer {
   @Override
   public boolean transform(TransformationDescriptor desc) {
      TableAssembly table = desc.getTable(false);

      if(!(table instanceof ComposedTableAssembly)) {
         return false;
      }

      Map<Integer, List<ComposedTableAssembly>> composedTableMap = new HashMap<>();
      addTables(composedTableMap, 0, (ComposedTableAssembly) table);
      // the last level at which we can find a composed table assembly
      int lastLevel = composedTableMap.keySet().size() - 1;
      Set<String> cannotMoveDownSet = new HashSet<>();

      // Incrementally move down the selections starting with the composed assemblies
      // closest to the bound table assemblies
      for(int i = lastLevel; i >= 0; i--) {
         for(int j = i; j <= lastLevel; j++) {
            moveDown(j, composedTableMap, cannotMoveDownSet, desc);
         }
      }

      return true;
   }

   /**
    * Add the composed tables to the map and their respective level at which they were
    * found in the composition tree
    */
   private void addTables(Map<Integer, List<ComposedTableAssembly>> composedTableMap,
                          int level, ComposedTableAssembly table)
   {
      // if this composed table assembly doesn't have any bound table assemblies that
      // contain ws runtime mv then don't add it
      if(!WSMVTransformer.containsWSRuntimeMV(table)) {
         return;
      }

      List<ComposedTableAssembly> tableAssemblies =
         composedTableMap.computeIfAbsent(level, k -> new ArrayList<>());

      tableAssemblies.add(table);

      for(TableAssembly childTable : table.getTableAssemblies(false)) {
         if(childTable instanceof ComposedTableAssembly) {
            addTables(composedTableMap, level + 1, (ComposedTableAssembly) childTable);
         }
      }
   }

   /**
    * Move down the selections from this level
    */
   private void moveDown(int level,
                         Map<Integer, List<ComposedTableAssembly>> composedTableMap,
                         Set<String> cannotMoveDownSet, TransformationDescriptor desc)
   {
      List<ComposedTableAssembly> tableAssemblies = composedTableMap.get(level);

      for(ComposedTableAssembly parentTable : tableAssemblies) {
         // no need to check again once it's no longer possible to move down
         // from this parent assembly
         if(cannotMoveDownSet.contains(parentTable.getName())) {
            continue;
         }

         TableAssembly[] childTables = parentTable.getTableAssemblies(false);
         boolean canMoveDown = canMoveDown(parentTable);

         if(canMoveDown) {
            for(TableAssembly childTable : childTables) {
               moveDown(parentTable, childTable, desc);
            }

            // clear the conditions from the parent after pushing down all the conditions
            parentTable.setPreRuntimeConditionList(new ConditionList());
         }
         else {
            cannotMoveDownSet.add(parentTable.getName());
         }
      }
   }

   /**
    * Move down selection from the parent table to the base table.
    */
   private boolean moveDown(TableAssembly ptable, TableAssembly table,
                            TransformationDescriptor desc)
   {
      ConditionListWrapper wrapper = ptable.getPreRuntimeConditionList();

      if(isEmptyFilter(wrapper)) {
         return true;
      }

      wrapper = (ConditionListWrapper) wrapper.clone();
      ConditionList parentConditions = wrapper.getConditionList();
      String tname = table.getName();

      if(ptable instanceof AbstractJoinTableAssembly) {
         if(isConditionOnOneTable(parentConditions)) {
            DataRef ref = parentConditions.getAttribute(0);
            String entity = getEntity(ref);

            if(!Tool.equals(entity, tname)) {
               return false;
            }
         }
         else {
            parentConditions = ConditionUtil.filter(parentConditions,
                                                attr -> Tool.equals(getEntity(attr), tname));
         }

         if(isEmptyFilter(parentConditions)) {
            return true;
         }
      }

      replaceConditionListAttributes(parentConditions, ptable, table);

      ConditionListWrapper cwrapper = table.getPreRuntimeConditionList();

      if(!isEmptyFilter(cwrapper)) {
         List list = new ArrayList();
         list.add(parentConditions);
         list.add(cwrapper.getConditionList());
         cwrapper = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
      }
      else {
         cwrapper = parentConditions;
      }

      // condition pushed down, don't modify it in validateConditionList()
      table.setProperty("selection.down", "true");
      table.setPreRuntimeConditionList(cwrapper);
      table.resetColumnSelection();
      desc.addInfo(getInfo(tname));
      return true;
   }

   @Override
   protected TransformationInfo getInfo(String block) {
      return TransformationInfo.selectionDown(block);
   }

   /**
    * Condition list is simple if it there is no indentation and only contains AND junctions.
    * In addition, the condition cannot reference a column from another table.
    */
   private boolean isConditionListSimple(ConditionListWrapper conditionList) {
      for(int i = 0; i < conditionList.getConditionSize(); i++) {
         if(conditionList.isConditionItem(i)) {
            ConditionItem conditionItem = conditionList.getConditionItem(i);

            if(conditionItem.getLevel() != 0) {
               return false;
            }

            Set<String> entities = new HashSet<>();
            entities.add(getEntity(conditionItem.getAttribute()));
            XCondition cond = conditionItem.getXCondition();

            if(cond instanceof AssetCondition) {
               DataRef[] refs = ((AssetCondition) cond).getDataRefValues();

               for(DataRef ref : refs) {
                  entities.add(getEntity(ref));
               }
            }

            if(entities.size() > 1) {
               return false;
            }
         }

         if(conditionList.isJunctionOperator(i) &&
            conditionList.getJunctionOperator(i).getJunction() !=
               JunctionOperator.AND)
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Determines whether all the conditions reference the same table
    */
   private boolean isConditionOnOneTable(ConditionListWrapper conditionList) {
      Set<String> entities = new HashSet<>();

      for(int i = 0; i < conditionList.getConditionSize(); i++) {
         if(conditionList.isConditionItem(i)) {
            ConditionItem conditionItem = conditionList.getConditionItem(i);
            entities.add(getEntity(conditionItem.getAttribute()));

            XCondition cond = conditionItem.getXCondition();

            if(cond instanceof AssetCondition) {
               DataRef[] refs = ((AssetCondition) cond).getDataRefValues();

               for(DataRef ref : refs) {
                  entities.add(getEntity(ref));
               }
            }
         }
      }

      return entities.size() == 1;
   }

   /**
    * Determines whether all the conditions on the parent table can be moved down
    */
   private boolean canMoveDown(ComposedTableAssembly table) {
      if(table instanceof RotatedTableAssembly || table instanceof UnpivotTableAssembly) {
         return false;
      }

      if(table instanceof AbstractJoinTableAssembly) {
         AbstractJoinTableAssembly joinTable = (AbstractJoinTableAssembly) table;
         ConditionListWrapper conditionList = table.getPreRuntimeConditionList();

         if(isEmptyFilter(conditionList)) {
            return true;
         }

         Enumeration tables = joinTable.getOperatorTables();
         boolean outer = false;

         while(tables.hasMoreElements()) {
            String[] pair = (String[]) tables.nextElement();
            String ltable = pair[0];
            String rtable = pair[1];
            TableAssemblyOperator top = joinTable.getOperator(ltable, rtable);

            if(top.isOuterJoin()) {
               outer = true;
               break;
            }
         }

         if(outer) {
            return false;
         }

         if(!(isConditionListSimple(conditionList) || isConditionOnOneTable(conditionList))) {
            return false;
         }
      }

      ConditionListWrapper conditionList = table.getPreRuntimeConditionList();

      if(conditionList != null) {
         for(int i = 0; i < conditionList.getConditionSize(); i++) {
            ConditionItem conditionItem = conditionList.getConditionItem(i);

            if(conditionItem != null) {
               XCondition cond = conditionItem.getXCondition();

               if(cond instanceof AssetCondition) {
                  SubQueryValue val = ((AssetCondition) cond).getSubQueryValue();

                  if(val != null) {
                     return false;
                  }
               }

               if(cond instanceof Condition) {
                  for(Object val : ((Condition) cond).getValues()) {
                     if(val instanceof DataRef) {
                        // data ref value is not supported
                        return false;
                     }
                  }
               }
            }
         }
      }

      for(TableAssembly childTable : table.getTableAssemblies(false)) {
         AggregateInfo ainfo = childTable.getAggregateInfo();

         if(!ainfo.isEmpty()) {
            if(ainfo.isCrosstab()) {
               return false;
            }

            if(childTable instanceof BoundTableAssembly || conditionList == null) {
               continue;
            }

            // can't move down condition if the attribute is an aggregate in the child table
            for(int i = 0; i < conditionList.getConditionSize(); i++) {
               ConditionItem conditionItem = conditionList.getConditionItem(i);

               if(conditionItem != null) {
                  DataRef[] refs = getColumnRef(childTable.getColumnSelection(),
                                                childTable.getName(),
                                                conditionItem.getAttribute());

                  if(refs != null && refs.length > 0 && ainfo.containsAggregate(refs[0])) {
                     return false;
                  }
               }
            }
         }
      }

      return true;
   }

   private void replaceConditionListAttributes(ConditionList conditionList,
                                               TableAssembly ptable, TableAssembly table)
   {
      ColumnSelection pcolumns = ptable.getColumnSelection();
      ColumnSelection columns = table.getColumnSelection();

      for(int i = 0; i < conditionList.getConditionSize(); i++) {
         if(conditionList.isConditionItem(i)) {
            DataRef ref = conditionList.getAttribute(i);
            DataRef childCol = null;

            if(ptable instanceof ConcatenatedTableAssembly) {
               int index = pcolumns.indexOfAttribute(ref);

               if(index >= 0) {
                  childCol = columns.getAttribute(index);
               }
            }
            else {
               ColumnRef[] arr = getColumnRef(columns, table.getName(), ref);

               if(arr != null && arr[0] != null) {
                  // if column is aliased, the aliased column is stored in MV.
                  // use the alias instead of the original column name to avoid
                  // column not found
                  if(arr[0].getAlias() != null) {
                     childCol = new ColumnRef(new AttributeRef(ptable.getName(),
                                                               arr[0].getAlias()));
                  }
                  else {
                     childCol = arr[0];
                  }
               }
            }

            if(childCol != null) {
               ConditionItem conditionItem = conditionList.getConditionItem(i);
               conditionItem.setAttribute(childCol);
            }
         }
      }
   }

   private String getEntity(DataRef ref) {
      if(ref != null) {
         String entity = ref.getEntity();

         if(entity == null && ref instanceof DataRefWrapper) {
            entity = getEntity(((DataRefWrapper) ref).getDataRef());
         }

         return entity;
      }

      return null;
   }
}
