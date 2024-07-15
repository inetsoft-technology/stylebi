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
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.HierarchyListModel;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;

import java.util.*;

/**
 * CalcFieldTransformer pushes MV to the sub-table of a table with CalcField
 * since the calc fields are not in the MV, and need to be post processed.
 *
 * @version 11.1
 * @author InetSoft Technology Corp
 */
public final class CalcFieldTransformer extends AbstractTransformer {
   /**
    * Create an instance of CalcFieldTransformer.
    */
   public CalcFieldTransformer() {
      super();
   }

   /**
    * Transform the table assembly.
    * @return true if successful, false otherwise.
    */
   @Override
   public boolean transform(TransformationDescriptor desc) {
      TableAssembly mvtable = desc.getMVTable();

      if(mvtable == null) {
         return true;
      }

      /*String amvassembly = desc.getAnalyzeMVAssembly();
      TableAssembly amvtable = amvassembly == null ?
         null : desc.getTable(amvassembly);

      // mv table and analyze mv table same?
      if(amvtable == null) {
         return true;
      }*/

      calcs = getCalculateFields(mvtable, desc.getViewsheet());

      // mv table not contains calculate fields?
      if(calcs.size() <= 0) {
         return true;
      }

      // calc field is not in use, just remove it
      if(!isCalcInUse(desc)) {
         removeCalcs(mvtable);
         return true;
      }

      calcBase = VSUtil.getCalcBaseRef(calcs);
      oncalcs = getFieldsOnCalc(mvtable, calcs);

      AggregateInfo ainfo = mvtable.getAggregateInfo();

      if(ainfo == null || ainfo.isEmpty()) {
         transformTable(desc, false);
         return true;
      }

      return transformAggregateTable(desc);
   }

   /**
    * Check aggregate table.
    */
   private boolean transformAggregateTable(TransformationDescriptor desc) {
      TableAssembly mvtable = desc.getMVTable();
      AggregateInfo ainfo = mvtable.getAggregateInfo();

      // contains composite aggregate? then the aggregate info must be
      // transformed from "_O" table, the aggregate info should not
      // contains calc fields, and the calc field in this table is not
      // valid, instead it should be moved to parent table, but we cannot
      // make sure all the calc base fields are in the aggregate info
      if(isCompositeAggregate(ainfo)) {
         return transformComposite(desc);
      }

      return transformTable(desc, false);
   }

   /**
    * Transform aggregate table which contains composite aggregate ref.
    */
   private boolean transformComposite(TransformationDescriptor desc) {
      TableAssembly mvtable = desc.getMVTable();
      AggregateInfo ainfo = mvtable.getAggregateInfo();
      // add selection to group
      List selections = desc.getSelectionColumns(mvtable.getName(), false);

      // calc field base refs not used in aggregate info, in order to support
      // calc field correct, we need to add them to groups, but if their not
      // exist any aggregate info in parent table or the aggregate not support
      // aoa, will cause data error if contains post or ranking or calc
      // pre condition, result will also error is we add new groups
      if((!containsAll(ainfo, calcBase) || !containsAll(ainfo, selections)) &&
         (!parentSupportsAOA(desc) || containsInValidConditions(mvtable)))
      {
         return false;
      }

      ColumnSelection sel = mvtable.getColumnSelection();

      // append calc base refs to group
      for(DataRef ref : calcBase) {
         if(!ainfo.containsGroup(ref) && ainfo.getAggregate(ref) == null) {
            ColumnRef col = getColumn(mvtable, ref);
            int index = sel.indexOfAttribute(col);

            if(index >= 0) {
               col = (ColumnRef) sel.getAttribute(index);
            }
            else {
               sel.addAttribute(col);
            }

            col.setVisible(true);
            ainfo.addGroup(new GroupRef(col));
         }
      }

      for(int i = 0; i < selections.size(); i++) {
         WSColumn wscol = (WSColumn) selections.get(i);
         ColumnRef col = (ColumnRef) wscol.getDataRef();

         if(!ainfo.containsGroup(col) && ainfo.getAggregate(col) == null) {
            int index = sel.indexOfAttribute(col);

            if(index >= 0) {
               col = (ColumnRef) sel.getAttribute(index);
            }
            else {
               sel.addAttribute(col);
            }

            ainfo.addGroup(new GroupRef(col));
         }
      }

      mvtable.setColumnSelection(sel);
      return transformTable(desc, true);
   }

   /**
    * Check if the table contains all base calcs in aggregate info.
    */
   private boolean containsAll(AggregateInfo ainfo, List base) {
      for(int i =0; i < base.size(); i++) {
         Object obj = base.get(i);
         DataRef ref = null;

         if(obj instanceof WSColumn) {
            ref = ((WSColumn) obj).getDataRef();
         }
         else {
            ref = (DataRef) obj;
         }

         if(!ainfo.containsGroup(ref) && ainfo.getAggregate(ref) == null) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if contains aggregate info which is supported aoa in parent table.
    */
   private boolean parentSupportsAOA(TransformationDescriptor desc) {
      TableAssembly table = desc.getTable(false);
      TableAssembly mvtable = desc.getMVTable();
      AggregateInfo lastAgg = null;

      while(table != mvtable && table != null) {
         AggregateInfo ainfo = table.getAggregateInfo();

         if(ainfo != null && !ainfo.isEmpty()) {
            lastAgg = ainfo;
         }

         table = table instanceof MirrorTableAssembly ?
            ((MirrorTableAssembly) table).getTableAssembly() : null;
      }

      return lastAgg == null ? false : lastAgg.supportsAOA();
   }

   /**
    * Contains post or ranking, or calc pre.
    */
   private boolean containsInValidConditions(TableAssembly table) {
      ConditionListWrapper wrapper = table.getPostConditionList();

      if(wrapper != null && !wrapper.isEmpty()) {
         return true;
      }

      wrapper = table.getRankingConditionList();

      if(wrapper != null && !wrapper.isEmpty()) {
         return true;
      }

      wrapper = table.getPreRuntimeConditionList();

      // pre condition contains calc ref, will be moved up, so here
      // check the column is used in aggregate or not
      if(isConditionOnCalc(wrapper)) {
         AggregateInfo ainfo = table.getAggregateInfo();
         ConditionList conds = wrapper.getConditionList();

         for(int i = 0; i < conds.getSize(); i += 2) {
            ConditionItem citem = conds.getConditionItem(i);
            DataRef attr = citem.getAttribute();

            if(ainfo.getAggregate(attr) != null) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Transform table.
    */
   private boolean transformTable(TransformationDescriptor desc,
                                  boolean composite)
   {
      TableAssembly mvtable = desc.getMVTable();
      AggregateInfo ainfo = mvtable.getAggregateInfo();

      if(!composite) {
         mvtable.setAggregateInfo(new AggregateInfo());
      }

      String name = mvtable.getName();
      String nname = "CT_" + name;
      mvtable.getInfo().setName(nname);
      desc.setMVAssembly(nname);

      fixMVTableColumnSelection(mvtable);

      Worksheet ws = desc.getWorksheet();
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, name, mvtable);
      ws.addAssembly(mirror);

      boolean fixedWithOrder = false;

      if(desc.getViewsheet() != null) {
         Assembly vsAssembly =
            desc.getViewsheet().getAssembly(desc.getVSAssembly());

         if(vsAssembly != null && vsAssembly instanceof TableVSAssembly &&
            vsAssembly.getInfo() instanceof TableVSAssemblyInfo)
         {
            //@by ankitmathur, for VS Table Assemblies, maintain column order.
            TableVSAssemblyInfo tableInfo =
               (TableVSAssemblyInfo) vsAssembly.getInfo();
            ColumnSelection tableSelction = tableInfo.getColumnSelection();
            fixMirrorTableCSWithOrder(mirror, tableSelction);
            fixedWithOrder = true;
         }
      }

      if(!fixedWithOrder) {
         fixMirrorTableColumnSelection(mirror);
      }

      // for composite, its aggregate should not move up
      /*if(!composite) {
         moveUpAggregate(mirror, mvtable);
      }

      ConditionListWrapper wrapper = mvtable.getPreRuntimeConditionList();

      // condition on calc, move to mirror
      if(isConditionOnCalc(wrapper)) {
         wrapper = moveUpCondition(mirror, mvtable, wrapper);
         mvtable.setPreRuntimeConditionList(new ConditionList());
         mirror.setPreRuntimeConditionList(wrapper);
      }

      // post condition should be moved up to mirror
      wrapper = mvtable.getPostConditionList();
      moveUpCondition(mirror, mvtable, wrapper);
      mvtable.setPostConditionList(new ConditionList());
      mirror.setPostConditionList(wrapper);

      // ranking should be moved up to mirror
      wrapper = mvtable.getRankingConditionList();
      moveUpCondition(mirror, mvtable, wrapper);
      mvtable.setRankingConditionList(new ConditionList());
      mirror.setRankingConditionList(wrapper);*/

      return true;
   }

   /**
    * Fix mv table column selection.
    */
   private void fixMVTableColumnSelection(TableAssembly mvtable) {
      // remove all calcs
      removeCalcs(mvtable);
      ColumnSelection sel = mvtable.getColumnSelection();
      /*String stname = mvtable instanceof MirrorTableAssembly ?
         ((MirrorTableAssembly) mvtable).getAssemblyName() : null;*/

      // append calc base refs
      for(DataRef ref : calcBase) {
         ColumnRef col = getColumn(mvtable, ref);
         int index = sel.indexOfAttribute(col);

         if(index >= 0) {
            col = (ColumnRef) sel.getAttribute(index);
         }
         else {
            sel.addAttribute(col);
         }

         col.setVisible(true);
      }

      /*ConditionListWrapper wrapper = mvtable.getPreRuntimeConditionList();

      // condition on calc? will be moved up, so here add it to columns
      if(isConditionOnCalc(wrapper)) {
         appendWrapper(mvtable, wrapper);
      }

      wrapper = mvtable.getRankingConditionList();
      appendWrapper(mvtable, wrapper);*/
      mvtable.setColumnSelection(sel);
   }

   /**
    * Append columns from condition to column selection.
    */
   private void appendWrapper(TableAssembly mvtable, ConditionListWrapper wrap) {
      if(wrap == null || wrap.isEmpty()) {
         return;
      }

      ColumnSelection sel = mvtable.getColumnSelection();
      ConditionList conds = wrap.getConditionList();

      for(int i = 0; i < conds.getSize(); i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         DataRef attr = citem.getAttribute();

         if(isOnCalcField(attr)) {
            continue;
         }

         ColumnRef col = getColumn(mvtable, attr);
         int index = sel.indexOfAttribute(col);

         if(index >= 0) {
            col = (ColumnRef) sel.getAttribute(index);
         }
         else {
            sel.addAttribute(col);
         }

         col.setVisible(true);
      }
   }

   /**
    * Create column ref for the table.
    */
   private ColumnRef getColumn(TableAssembly table, DataRef attr) {
      if(attr instanceof ColumnRef) {
         return (ColumnRef) attr;
      }

      String sname = table instanceof MirrorTableAssembly ?
         ((MirrorTableAssembly) table).getAssemblyName() : null;
      String dtype = attr.getDataType();

      if(sname != null && !"".equals(sname)) {
         attr = AssetUtil.getOuterAttribute(sname, attr);
      }

      ColumnRef col = new ColumnRef(attr);
      col.setDataType(dtype);
      return col;
   }

   /**
    * Fix mirror table column selection.
    */
   private void fixMirrorTableColumnSelection(TableAssembly mirror) {
      ColumnSelection sel = mirror.getColumnSelection();

      // add calc fields
      for(DataRef ref : calcs) {
         sel.addAttribute(ref);
      }

      // add fields base on calcs
      for(DataRef ref : oncalcs) {
         sel.addAttribute(ref);
      }

      mirror.setColumnSelection(sel);
   }

   /**
    * Fix mirror table column selection and maintain column order.
    */
   private void fixMirrorTableCSWithOrder(
      TableAssembly mirror, ColumnSelection vsTableColumnSelection)
   {
      ColumnSelection sel = mirror.getColumnSelection();

      // add calc fields
      for(DataRef ref : calcs) {
         DataRef vsTableRef = vsTableColumnSelection.getAttribute(ref.getName());
         int index = vsTableColumnSelection.indexOfAttribute(vsTableRef);

         if(index >= 0) {
            sel.addAttribute(index, ref);
         }
         else {
            sel.addAttribute(ref);
         }
      }

      // add fields base on calcs
      for(DataRef ref : oncalcs) {
         DataRef vsTableRef = vsTableColumnSelection.getAttribute(ref.getName());
         int index = vsTableColumnSelection.indexOfAttribute(vsTableRef);

         if(index >= 0) {
            sel.addAttribute(index, ref);
         }
         else {
            sel.addAttribute(ref);
         }
      }

      mirror.setColumnSelection(sel);
   }

   /**
    * Move aggregate from child table up to parent table.
    */
   private void moveUpAggregate(TableAssembly ptbl, TableAssembly ctbl) {
      AggregateInfo ainfo = ptbl.getAggregateInfo();
      GroupRef[] groups = ainfo.getGroups();

      for(int i = 0; i < groups.length; i++) {
         transformGroup(ptbl, ctbl, groups[i]);
      }

      AggregateRef[] aggrs = ainfo.getAggregates();
      boolean composite = false;

      for(int i = 0; i < aggrs.length; i++) {
         if(aggrs[i] instanceof CompositeAggregateRef) {
            composite = true;
         }

         transformAggregate(ptbl, ctbl, aggrs[i]);
      }
   }

   /**
    * Move selection up.
    */
   private ConditionListWrapper moveUpCondition(TableAssembly ptbl,
                                                TableAssembly tbl,
                                                ConditionListWrapper wrapper)
   {
      if(wrapper == null || wrapper.isEmpty()) {
         return new ConditionList();
      }

      ConditionList conds = wrapper.getConditionList();
      clearPseudo(conds);

      for(int i = 0; i < conds.getSize(); i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         DataRef attr = citem.getAttribute();

         if(attr instanceof GroupRef) {
            attr = transformGroup(ptbl, tbl, (GroupRef) attr);
         }
         else if(attr instanceof AggregateRef) {
            attr = transformAggregate(ptbl, tbl, (AggregateRef) attr);
         }
         else {
            attr = transformDataRef(ptbl, tbl, attr);
         }

         citem.setAttribute(attr);
      }

      return wrapper;
   }

   /**
    * Clear pseudo filter.
    */
   private void clearPseudo(ConditionList conds) {
      HierarchyListModel model = new HierarchyListModel(conds);

      for(int i = model.getSize() - 1; i >= 0; i -= 2) {
         ConditionItem item = (ConditionItem) model.getElementAt(i);
         XCondition cond = item.getXCondition();

         if(cond.getOperation() == XCondition.PSEUDO) {
            model.removeConditionItem(i);
         }
      }
   }

   /**
    * Transform aggregate field from child table to parent table.
    */
   private AggregateRef transformAggregate(TableAssembly ptbl,
                                           TableAssembly ctbl,
                                           AggregateRef afield)
   {
      DataRef ref = afield.getDataRef();
      ref = transformDataRef(ptbl, ctbl, ref);
      afield.setDataRef(ref);
      return afield;
   }

   /**
    * Transform group field from child table to parent table.
    */
   private GroupRef transformGroup(TableAssembly ptbl, TableAssembly ctbl,
                                   GroupRef gfield)
   {
      DataRef ref = gfield.getDataRef();
      ref = transformDataRef(ptbl, ctbl, ref);
      gfield.setDataRef(ref);
      return gfield;
   }

   /**
    * Transform data ref from child to parent.
    */
   private DataRef transformDataRef(TableAssembly ptbl, TableAssembly ctbl,
                                    DataRef ref)
   {
      if(isOnCalcField(ref)) {
         return ref;
      }

      ColumnSelection pcolumns = ptbl.getColumnSelection();
      String sname = ctbl.getName();
      return getParentColumn(pcolumns, sname, ref);
   }


   /**
    * Check if aggregate contains composite aggregate ref.
    */
   private boolean isCompositeAggregate(AggregateInfo ainfo) {
      AggregateRef[] arefs = ainfo.getAggregates();

      for(AggregateRef ref : arefs) {
         if(ref instanceof CompositeAggregateRef) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if calc used in the mvtable.
    */
   private boolean isCalcInUse(TransformationDescriptor desc) {
      TableAssembly mvtable = desc.getMVTable();
      TableAssembly table = desc.getTable(false);
      boolean find = false;

      while(!find && table != null) {
         find = table == mvtable;

         if(isCalcInUse(table, find)) {
            return true;
         }

         table = table instanceof MirrorTableAssembly ?
            ((MirrorTableAssembly) table).getTableAssembly() : null;
      }

      return false;
   }

   /**
    * Check if calculate field used in the table.
    */
   private boolean isCalcInUse(TableAssembly table, boolean mvtable) {
      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();

      if(isConditionOnCalc(wrapper)) {
         return true;
      }

      wrapper = table.getPostRuntimeConditionList();

      if(isConditionOnCalc(wrapper)) {
         return true;
      }

      wrapper = table.getRankingConditionList();

      if(isConditionOnCalc(wrapper)) {
         return true;
      }

      if(!mvtable) {
         ColumnSelection selection = table.getColumnSelection();

         for(int i = 0; i < selection.getAttributeCount(); i++) {
            DataRef ref = selection.getAttribute(i);

            if(isOnCalcField(ref)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if calculate field used in condition.
    */
   private boolean isConditionOnCalc(ConditionListWrapper wrapper) {
      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();

      if(conds != null) {
         for(int i = 0; i < conds.getSize(); i += 2) {
            ConditionItem citem = conds.getConditionItem(i);
            DataRef ref = citem.getAttribute();

            if(isOnCalcField(ref)) {
               return true;
            }
         }
      }

      return true;
   }

   /**
    * Remove all calcs from table.
    */
   private void removeCalcs(TableAssembly table) {
      ColumnSelection selection = table.getColumnSelection();

      for(int i = selection.getAttributeCount() - 1; i >= 0; i--) {
         DataRef ref = selection.getAttribute(i);

         if(isOnCalcField(ref) && !nonDependentCalcs.contains(ref)) {
            selection.removeAttribute(i);
         }
      }

      table.setColumnSelection(selection);
   }

   /**
    * Get Calculate fields from the table if exist.
    *
    * @param table the MV table which we are analyzing to find CalculateRefs.
    * @param vs    the Viewsheet.
    *
    * @return The List of CalculateFields which need to be post-processed.
    */
   private List<CalculateRef> getCalculateFields(TableAssembly table,
                                                 Viewsheet vs)
   {
      List<CalculateRef> calcs = new ArrayList<>();
      /*ColumnSelection selection = table.getColumnSelection(false);

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         DataRef ref = selection.getAttribute(i);

         if(ref instanceof ColumnRef) {
            CalculateRef calcRef = ((ColumnRef) ref).getMvCalcRef();

            if(calcRef != null) {
               ExpressionRef expressionRef = (ExpressionRef) calcRef.getDataRef();
               // @by ankitmathur, Find and only re-add CalculateRef's which are
               // dependent on other elements in the Viewsheet. All other
               // CalculateRef's should use already Materialized data
               // (see nonDependentCalcs).
               if(expressionRef != null && !expressionRef.isSQL()) {
                  String script = expressionRef.getScriptExpression();
                  Set<AssemblyRef> dependents = new HashSet<AssemblyRef>();
                  VSUtil.getScriptDependeds(script, dependents, vs, null);

                  if(dependents.size() > 0) {
                     calcs.add(calcRef);
                  }
                  else {
                     nonDependentCalcs.add(calcRef);
                  }
               }
            }
         }
      }*/

      return calcs;
   }

   /**
    * Get all fields which is not calculate fields but base on calculate fields.
    */
   private List<DataRef> getFieldsOnCalc(TableAssembly table,
                                         List<CalculateRef> calcs)
   {
      List<DataRef> list = new ArrayList<>();
      ColumnSelection sel = table.getColumnSelection(false);

      for(int i = 0; i < sel.getAttributeCount(); i++) {
         DataRef ref = sel.getAttribute(i);

         if(!(ref instanceof CalculateRef) && isOnCalcField(ref)) {
            list.add(ref);
         }
      }

      return list;
   }

   /**
    * Check if the field is a calc field.
    */
   private boolean isOnCalcField(DataRef ref) {
      return VSUtil.isOnCalcField(ref, calcs);
   }

   /**
    * Get the transformation message for this transformer.
    * @param block the data block.
    */
   @Override
   protected TransformationInfo getInfo(String block) {
      return TransformationInfo.calcFields(block);
   }

   private List<CalculateRef> calcs;
   private Set<CalculateRef> nonDependentCalcs = new HashSet<>();
   private List<DataRef> calcBase;
   private List<DataRef> oncalcs;
}
