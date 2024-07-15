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
package inetsoft.mv.trans;

import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.DataRefWrapper;

import java.util.*;

public class WSMVAggregateDownTransformer extends AbstractTransformer {
   @Override
   public boolean transform(TransformationDescriptor desc) {
      TableAssembly table = desc.getTable(false);

      if(table instanceof BoundTableAssembly) {
         return true;
      }

      setUpTables(table);

      for(BoundTableAssembly boundTable : boundTables) {
         moveDownTo(boundTable, desc);
      }

      return true;
   }

   @Override
   protected TransformationInfo getInfo(String block) {
      return TransformationInfo.aggregateDown(block);
   }

   private void moveDownTo(BoundTableAssembly boundTable, TransformationDescriptor desc) {
      List<TableAssembly> moveDownList = new ArrayList<>();
      moveDownList.add(boundTable);
      TableAssembly parentTable = parentMap.get(boundTable.getName());
      boolean foundAgg = false;

      while(parentTable instanceof MirrorTableAssembly) {
         moveDownList.add(parentTable);

         if(!parentTable.getAggregateInfo().isEmpty()) {
            foundAgg = true;
            break;
         }
         else {
            parentTable = parentMap.get(parentTable.getName());
         }
      }

      if(foundAgg) {
         for(int i = moveDownList.size() - 1; i > 0; i--) {
            moveDown(moveDownList.get(i), moveDownList.get(i - 1), desc);
         }
      }
   }

   /**
    * Move down selection from mirror table to base table.
    */
   private void moveDown(TableAssembly ptable, TableAssembly table, TransformationDescriptor desc) {
      AggregateInfo pinfo = (AggregateInfo) ptable.getAggregateInfo().clone();

      // parent aggregate is empty? useless
      if(pinfo.isEmpty() || pinfo.isCrosstab() || pinfo.containsPercentage()) {
         return;
      }

      // has named range aggregate and have distinct count?
      if(containsNamedRangeAggregate(ptable)) {
         return;
      }

      // named group (e.g. using a group assembly in ws)
      if(containsNamedGroup(ptable)) {
         return;
      }

      String tname = table.getName();
      ColumnSelection pcolumns = ptable.getColumnSelection();
      ColumnSelection columns = table.getColumnSelection();
      GroupRef[] groups = pinfo.getGroups();
      AggregateRef[] aggregates = pinfo.getAggregates();
      Map<DataRef, DataRef> parentChildRefMap = new HashMap<>();

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
         if(arr == null || arr[0] == null) {
            return;
         }

         parentChildRefMap.put((DataRef) group.clone(), arr[0]);
         group.setDataRef(arr[0]);

         // replace parent column with new column
         int index = pcolumns.indexOfAttribute(pcol);
         ColumnRef npcol = AbstractTransformer.getParentColumn(pcolumns, tname, group);
         pcolumns.setAttribute(index, npcol);
      }

      for(int i = 0; i < aggregates.length; i++) {
         AggregateRef aggregate = aggregates[i];
         DataRef pcol = aggregate.getDataRef();
         // child ref must be found
         ColumnRef[] arr = getChildColumn(pcolumns, columns, tname, pcol);
         parentChildRefMap.put((DataRef) aggregate.clone(), arr[0]);
         aggregate.setDataRef(arr[0]);

         // replace parent column with new column
         int index = pcolumns.indexOfAttribute(pcol);
         ColumnRef npcol = AbstractTransformer.getParentColumn(pcolumns, tname, aggregate);
         pcolumns.setAttribute(index, npcol);
      }

      ConditionListWrapper postCondWrapper1 = ptable.getPostConditionList();
      ConditionListWrapper postCondWrapper2 = ptable.getPostRuntimeConditionList();
      ConditionListWrapper rankingCondWrapper1 = ptable.getRankingConditionList();
      ConditionListWrapper rankingCondWrapper2 = ptable.getRankingRuntimeConditionList();

      replaceCondRef(postCondWrapper1, parentChildRefMap);
      replaceCondRef(postCondWrapper2, parentChildRefMap);
      replaceCondRef(rankingCondWrapper1, parentChildRefMap);
      replaceCondRef(rankingCondWrapper2, parentChildRefMap);

      ptable.setColumnSelection(pcolumns, false);
      ptable.setAggregateInfo(new AggregateInfo());
      ptable.setPostConditionList(new ConditionList());
      ptable.setPostRuntimeConditionList(new ConditionList());
      ptable.setRankingConditionList(new ConditionList());
      ptable.setRankingRuntimeConditionList(new ConditionList());

      table.setAggregateInfo(pinfo);
      // move post, post runtime conditions from parent table to child
      // table post runtime conditions
      ArrayList<ConditionList> postCondList = new ArrayList<>();
      ConditionListWrapper postCondWrapper = table.getPostRuntimeConditionList();
      postCondList.add(postCondWrapper == null ? null : postCondWrapper.getConditionList());
      postCondList.add(postCondWrapper1 == null ? null : postCondWrapper1.getConditionList());
      postCondList.add(postCondWrapper2 == null ? null : postCondWrapper2.getConditionList());
      ConditionList postConds =
         ConditionUtil.mergeConditionList(postCondList, JunctionOperator.AND);
      table.setPostRuntimeConditionList(postConds);

      // move ranking, raking runtime conditions from parent table to child
      // table ranking runtime conditions
      ArrayList<ConditionList> rankingCondList = new ArrayList<>();
      ConditionListWrapper rankingCondWrapper = table.getRankingRuntimeConditionList();
      rankingCondList
         .add(rankingCondWrapper == null ? null : rankingCondWrapper.getConditionList());
      rankingCondList
         .add(rankingCondWrapper1 == null ? null : rankingCondWrapper1.getConditionList());
      rankingCondList
         .add(rankingCondWrapper2 == null ? null : rankingCondWrapper2.getConditionList());
      ConditionList rankingConds =
         ConditionUtil.mergeConditionList(rankingCondList, JunctionOperator.AND);
      table.setRankingRuntimeConditionList(rankingConds);

      table.resetColumnSelection();
      ptable.resetColumnSelection();
      table.setProperty("aggregate.down", "true");
      ptable.setProperty("aggregate.down", "true");
      desc.addInfo(getInfo(tname));
   }

   /**
    * Replace condition ref.
    */
   private void replaceCondRef(ConditionListWrapper wrapper,
                               Map<DataRef, DataRef> parentChildRefMap)
   {
      if(wrapper == null || wrapper.isEmpty()) {
         return;
      }

      ConditionList list = wrapper.getConditionList();

      for(int i = 0; i < list.getSize(); i += 2) {
         ConditionItem citem = (ConditionItem) list.getItem(i);
         DataRef ref = citem.getAttribute();
         DataRef childRef = parentChildRefMap.get(ref);

         if(childRef != null) {
            ((DataRefWrapper) ref).setDataRef(childRef);
         }

         XCondition xcond = citem.getXCondition();

         if(xcond instanceof RankingCondition) {
            RankingCondition rankingCond = (RankingCondition) xcond;
            DataRef rankRef = rankingCond.getDataRef();
            childRef = parentChildRefMap.get(rankRef);

            if(childRef != null) {
               ((DataRefWrapper) rankRef).setDataRef(childRef);
            }
         }
      }
   }

   private void setUpTables(TableAssembly table) {
      if(table instanceof ComposedTableAssembly) {
         TableAssembly[] childTables = ((ComposedTableAssembly) table).getTableAssemblies(false);

         for(TableAssembly childTable : childTables) {
            parentMap.put(childTable.getName(), table);

            if(childTable instanceof BoundTableAssembly) {
               AggregateInfo ainfo = childTable.getAggregateInfo();

               if(ainfo.isEmpty()) {
                  boundTables.add((BoundTableAssembly) childTable);
               }
            }

            setUpTables(childTable);
         }
      }
   }

   private Map<String, TableAssembly> parentMap = new HashMap<>();
   private List<BoundTableAssembly> boundTables = new ArrayList<>();
}
