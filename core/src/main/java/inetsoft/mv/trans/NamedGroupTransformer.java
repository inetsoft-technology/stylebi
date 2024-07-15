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

import inetsoft.mv.MVTransformer;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.util.Tool;

import java.util.*;

/**
 * NamedGroupTransformer transforms one table assembly by transforming its named
 * groups, so that the table assembly could hit mv.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class NamedGroupTransformer extends AbstractTransformer {
   /**
    * Create an instance of NamedGroupTransformer.
    */
   public NamedGroupTransformer() {
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
         MirrorTableAssembly ptable = (MirrorTableAssembly) table;
         table = (ptable).getTableAssembly();

         if(containsNamedRangeAggregate(ptable)) {
            moveDown(ptable, table, desc);
            return true;
         }
      }

      TableAssembly atable = mvtable;

      // if there is no grouping and there is named group in a mirror, we can
      // change the MV to the sub-table so the MV could be used and the named
      // group is processed in post processing
      if(containsNamedRangeDetail(atable) && atable instanceof MirrorTableAssembly) {
         MirrorTableAssembly mirror = (MirrorTableAssembly) atable;
         desc.setMVAssembly(mirror.getAssemblyName());
         desc.getMVTable().setRuntimeMV(atable.getRuntimeMV());
         atable.setRuntimeMV(null);
      }
      else if(containsNamedRangeAggregate(atable)) {
         TableAssembly mirror = createMirror(atable, desc.getWorksheet());
         desc.setMVAssembly(atable.getName());
         moveDown(mirror, atable, desc);
      }

      return true;
   }

   /**
    * Create mirror table and perform aggregation/filtering in the mirror.
    */
   private TableAssembly createMirror(TableAssembly table, Worksheet ws) {
      final String tname = table.getName();
      String nname = tname;

      if(nname.startsWith(Assembly.TABLE_VS)) {
         nname = nname.substring(Assembly.TABLE_VS.length());
      }

      nname = "NG_" + nname;
      AggregateInfo ainfo = table.getAggregateInfo();
      table.setAggregateInfo(new AggregateInfo());
      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();
      table.setPreRuntimeConditionList(new ConditionList());
      ColumnSelection cols = table.getColumnSelection();

      for(int i = cols.getAttributeCount() - 1; i >= 0; i--) {
         ColumnRef col = (ColumnRef) cols.getAttribute(i);
         DataRef ref = col.getDataRef();

         if(ref instanceof NamedRangeRef) {
            NamedRangeRef range = (NamedRangeRef) ref;
            DataRef bref = range.getDataRef();
            int index = cols.indexOfAttribute(bref);

            if(index >= 0) {
               bref = cols.getAttribute(index);
            }

            if(bref instanceof ColumnRef) {
               ((ColumnRef) bref).setVisible(true);
            }

            cols.removeAttribute(i);
         }
         // aggregate calc with named group can't be pushed to mv query. (50240)
         else if(MVTransformer.isAggregateExpression(ref)) {
            cols.removeAttribute(i);
         }
      }

      table.resetColumnSelection();
      table.getInfo().setName(nname);
      ws.addAssembly(table);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, tname, table);
      ws.addAssembly(mirror);
      mirror.setAggregateInfo(ainfo);
      mirror.setPreRuntimeConditionList(wrapper);
      cols = mirror.getColumnSelection();

      for(GroupRef gref : ainfo.getGroups()) {
         DataRef ref = getParentCol(cols, nname, (ColumnRef) gref.getDataRef());

         if(!cols.containsAttribute(ref)) {
            cols.addAttribute(ref);
         }

         gref.setDataRef(ref);
      }

      for(AggregateRef aref : ainfo.getAggregates()) {
         DataRef ref = getParentCol(cols, nname, (ColumnRef) aref.getDataRef());
         aref.setDataRef(ref);

         if(!cols.containsAttribute(ref)) {
            cols.addAttribute(ref);
         }

         DataRef second = aref.getSecondaryColumn();

         if(second != null) {
            ref = getParentCol(cols, nname, (ColumnRef) second);
            aref.setSecondaryColumn(ref);

            if(!cols.containsAttribute(ref)) {
               cols.addAttribute(ref);
            }
         }
      }

      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();

      for(int i = 0; conds != null && i < conds.getSize(); i+= 2) {
         ConditionItem citem = conds.getConditionItem(i);
         DataRef cref = citem.getAttribute();
         cref = getParentCol(cols, nname, (ColumnRef) cref);

         if(!cols.containsAttribute(cref)) {
            cols.addAttribute(cref);
         }

         citem.setAttribute(cref);
      }

      return mirror;
   }

   /**
    * Get the parent column ref.
    * @param pcolumns the specified parent columns.
    * @param cname the specified child table name.
    * @param col the specified child data ref.
    * @return the found parent column ref.
    */
   private ColumnRef getParentCol(ColumnSelection pcolumns, String cname, ColumnRef col) {
      if(col == null || pcolumns == null) {
         return null;
      }

      String name = getName(col);
      DataRef ref = col.getDataRef();
      AliasDataRef aref = null;
      RangeRef rref = null;

      if(ref instanceof AliasDataRef) {
         aref = (AliasDataRef) ref;
         ref = aref.getDataRef();
         name = getName(ref);
      }
      else if(ref instanceof RangeRef) {
         rref = (RangeRef) ref;
         ref = rref.getDataRef();
         name = getName(ref);
      }

      ColumnRef pcol = null;

      for(int i = 0; i < pcolumns.getAttributeCount(); i++) {
         ColumnRef tcolumn = (ColumnRef) pcolumns.getAttribute(i);
         DataRef pref = tcolumn.getDataRef();
         String entity = pref.getEntity();
         String attribute = pref.getAttribute();

         if(!Tool.equals(entity, cname)) {
            continue;
         }

         if(Tool.equals(attribute, name)) {
            pcol = tcolumn;
            break;
         }
      }

      if(pcol == null) {
         pcol = new ColumnRef(new AttributeRef(cname, name));
      }

      if(aref != null) {
         aref.setDataRef(pcol.getDataRef());
         col.setDataRef(aref);
      }
      else if(rref != null) {
         rref.setDataRef(pcol.getDataRef());
         col.setDataRef(rref);
      }
      else {
         col = pcol;
      }

      return col;
   }

   /**
    * Get the name of a data ref.
    */
   private String getName(DataRef ref) {
      String name = ref.getAttribute();

      if(ref instanceof ColumnRef) {
         String alias = ((ColumnRef) ref).getAlias();

         if(alias != null && alias.length() > 0) {
            name = alias;
         }
      }

      return name;
   }

   /**
    * Check whether the required information is valid for this tranformer.
    */
   private boolean isValid(TableAssembly table, TableAssembly mvtable,
                           TransformationDescriptor desc) {
      while(true) {
         if(table == mvtable) {
            return true;
         }

         if(table instanceof MirrorTableAssembly) {
            table = ((MirrorTableAssembly) table).getTableAssembly();
         }
         else {
            return false;
         }
      }
   }

   /**
    * Check if the assembly contains named range as detail column.
    */
   private boolean containsNamedRangeDetail(TableAssembly table) {
      AggregateInfo ainfo = table.getAggregateInfo();

      if(ainfo.isEmpty()) {
         ColumnSelection cols = table.getColumnSelection();

         for(int i = 0; i < cols.getAttributeCount(); i++) {
            if(isNamedRangeRef(cols.getAttribute(i))) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Move down aggregation from mirror table to base table.
    */
   private void moveDown(TableAssembly ptable, TableAssembly table,
                         TransformationDescriptor desc) {
      AggregateInfo cinfo = table.getAggregateInfo();

      if(!cinfo.isEmpty()) {
         return;
      }

      // if named group, and contains distinct count, should not move down,
      // otherwsie will cause wrong result
      if(containsNonCombinable(ptable.getAggregateInfo())) {
         return;
      }

      AggregateInfo aggr2 = createChildAggregates(ptable, ptable);
      setChildrenAggregates(ptable, table, aggr2);
      table.resetColumnSelection();
      ptable.resetColumnSelection();
      desc.addInfo(getInfo(table.getName()));
      moveSelectionColumns(desc, ptable, table);
      // fixGrouping(table);
   }

   /**
    * Create the sub-aggregate for aggregates in the aggregate info. The
    * aggregate passed into the function is modified on return to contain
    * aggregates for re-calculating results from sub-aggregates in the
    * returned aggregate info.
    */
   private AggregateInfo createChildAggregates(TableAssembly ptable, TableAssembly top) {
      AggregateInfo paggr = ptable.getAggregateInfo();
      ColumnSelection columns = top.getColumnSelection(false);
      ColumnSelection columns2 = top.getColumnSelection(true);
      AggregateInfo sub = new AggregateInfo();

      for(GroupRef gref : paggr.getGroups()) {
         sub.addGroup(gref);
      }

      // add column used in condition to sub-table grouping so it's available for condition (45181).
      ConditionListWrapper pwrapper = ptable.getPreRuntimeConditionList();

      if(pwrapper != null) {
         ConditionList conds = pwrapper.getConditionList();

         for(int j = 0; conds != null && j < conds.getSize(); j += 2) {
            ConditionItem citem = conds.getConditionItem(j);
            DataRef attr = citem.getAttribute();

            if(sub.getGroup(attr) == null) {
               sub.addGroup(new GroupRef(attr));
            }
         }
      }

      ConditionListWrapper wrapper = top.getPostConditionList();
      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();

      for(int i = 0; i < paggr.getAggregateCount(); i++) {
         AggregateRef aref = paggr.getAggregate(i);
         Collection<AggregateRef> subs = aref.getSubAggregates();
         DataRef ref2 = aref.getSecondaryColumn();

         // need secondary column in sub-table or it will be missing in mirror aggregate. (50296)
         if(ref2 != null) {
            sub.addGroup(new GroupRef(ref2));
         }

         if(subs.size() == 0) {
            sub.addAggregate(aref);
         }
         else {
            Iterator<AggregateRef> iter = subs.iterator();
            List<AggregateRef> aggregates = new ArrayList<>();

            while(iter.hasNext()) {
               AggregateRef subref = iter.next();
               sub.addAggregate(subref);
               aggregates.add(subref);
            }

            AggregateRef nref = new CompositeAggregateRef(aref, aggregates);
            paggr.setAggregate(i, nref);

            for(int j = 0; conds != null && j < conds.getSize(); j += 2) {
               ConditionItem citem = conds.getConditionItem(j);
               DataRef attr = citem.getAttribute();

               if(attr instanceof AggregateRef && attr.equals(aref)) {
                  citem.setAttribute(nref);
               }
            }

            ColumnRef column = (ColumnRef) aref.getDataRef();
            String name = column.getAlias();

            if(name == null || name.length() == 0) {
               name = column.getAttribute();
            }

            String entity = column.getEntity();
            ExpressionRef eref = new ExpressionRef(null, entity, name);
            eref.setExpression("");
            eref.setVirtual(true);
            ColumnRef ocolumn = column;
            column = new ColumnRef(eref);
            fixColumn(columns, eref, column);
            fixColumn(columns2, column, column);
            SortInfo sinfo = top.getSortInfo();
            SortRef sort = sinfo.getSort(ocolumn);

            if(sort != null) {
               sort.setDataRef(column);
            }
         }
      }

      return sub;
   }

   /**
    * Fix column.
    */
   private void fixColumn(ColumnSelection cols, DataRef col1, DataRef col2) {
      int idx = cols.indexOfAttribute(col1);

      if(idx >= 0) {
         cols.removeAttribute(idx);
         cols.addAttribute(idx, col2);
      }
      else {
         cols.addAttribute(col2);
      }
   }

   /**
    * Set the aggregate info in the children nodes.
    */
   private void setChildrenAggregates(TableAssembly ptable, TableAssembly table,
                                      AggregateInfo aggr0) {
      AggregateInfo ninfo = new AggregateInfo();
      ColumnSelection pcols = ptable.getColumnSelection();
      ColumnSelection cols = table.getColumnSelection();
      String tname = table.getName();
      AggregateInfo aggr = (AggregateInfo) aggr0.clone();

      for(GroupRef gref : aggr.getGroups()) {
         DataRef pcol = gref.getDataRef();
         ColumnRef[] arr = getChildColumn(pcols, cols, tname, pcol);
         ninfo.addGroup(new GroupRef(arr[0]));
      }

      for(AggregateRef aref : aggr.getAggregates()) {
         aref = (AggregateRef) aref.clone();
         DataRef ref = aref.getDataRef();
         ColumnRef[] arr = getColumnRef(cols, tname, ref);
         aref.setDataRef(arr[0]);
         DataRef ref2 = aref.getSecondaryColumn();

         if(ref2 != null) {
            arr = getColumnRef(cols, tname, ref2);
            aref.setSecondaryColumn(arr[0]);
         }

         ninfo.addAggregate(aref);
      }

      table.setAggregateInfo(ninfo);

      // if group contains date range, and it's pushed down, the name of the column changes
      // (e.g. Date -> Year(Date)). we need to change the group column in the parent to just
      // group on the result instead of do another date range grouping on details (which
      // doesn't exist anymore).
      AggregateInfo paggr = ptable.getAggregateInfo();

      for(GroupRef gref : paggr.getGroups()) {
         if(gref.getDataRef() instanceof ColumnRef) {
            ColumnRef col = (ColumnRef) gref.getDataRef();

            if(col.getDataRef() instanceof DateRangeRef) {
               col.setDataRef(new AttributeRef(gref.getName()));
            }
         }
      }
   }

   /**
    * Add additional columns to grouping if aggregate was pushed down.
    */
   private void fixGrouping(TableAssembly table) {
      AggregateInfo info = table.getAggregateInfo();

      if(info.isEmpty()) {
         return;
      }

      ColumnSelection columns = table.getColumnSelection(true);

      for(int j = 0; j < columns.getAttributeCount(); j++) {
         DataRef ref = columns.getAttribute(j);

         if(!info.containsGroup(ref) && !info.containsAggregate(ref) &&
            !info.containsAliasAggregate(ref))
         {
            info.addGroup(new GroupRef(ref));
         }
      }
   }

   /**
    * Get the transformation message for this transformer.
    * @param block the data block.
    */
   @Override
   protected TransformationInfo getInfo(String block) {
      return TransformationInfo.namedGroup(block);
   }

}
