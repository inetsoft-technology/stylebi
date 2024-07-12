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

import inetsoft.mv.MVTool;
import inetsoft.mv.RuntimeMV;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.Viewsheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * SelectionDownTransformer transforms one table assembly by pulling up its
 * runtime selections up to its base table assembly, so that this table assembly
 * could hit mv.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class SelectionUpTransformer extends AbstractTransformer {
   /**
    * Create an instance of SelectionUpTransformer.
    */
   public SelectionUpTransformer() {
      super();
   }

   /**
    * Transform the table assembly.
    * @return true if successful, false otherwise.
    */
   @Override
   public boolean transform(TransformationDescriptor desc) {
      Worksheet ws = desc.getWorksheet();
      String assembly = desc.getTableAssembly();
      XNode tree = TableNode.create(ws, assembly, desc);
      copyToList(tree, list, false);

      // sort the list so we start from the bottom and move the selections up
      list.sort((node1, node2) -> {
         if(node1.isAncestor(node2)) {
            return 1;
         }
         else if(node2.isAncestor(node1)) {
            return -1;
         }
         else {
            return 0;
         }
      });

      // for nested selection, we could not move selection up,
      // for there is dependency between selections
      for(int i = 0; i < list.size(); i++) {
         TableNode node = (TableNode) list.get(i);
         TableAssembly table = node.getTable();
         boolean snested = desc.isSelectionNested(table.getName());
         node.setSelectionNested(snested);
      }

      checkMVInfo(desc, list);

      OUTER:
      for(int i = 0; i < list.size(); i++) {
         TableNode node = (TableNode) list.get(i);
         String name = node.getName();
         String mvAssembly = desc.getMVAssembly();

         // mv table? it's time to break transformation
         if(name.equals(mvAssembly)) {
            TableAssembly tbl = (TableAssembly) node.getValue();
            AggregateInfo ainfo = tbl.getAggregateInfo();
            // aggregate formula is DistinctCount do not move up and defined
            // named group
            if(!validateAggregate(ainfo)) {
               recreate = false;
            }

            if((tbl instanceof MirrorTableAssembly)) {
               MirrorTableAssembly mirror = (MirrorTableAssembly) tbl;

               if(mirror.getTableAssembly().isMVForceAppendUpdates()) {
                  tbl.setMVForceAppendUpdates(true);
               }
            }

            break;
         }

         TableAssembly mirror = (TableAssembly) mtables.get(name);

         if(mirror != null) {
            TableNode mirrorNode = new TableNode(mirror);
            XNode pnode = node.getParent();

            if(pnode != null) {
               pnode.setChild(pnode.getChildIndex(node), mirrorNode);
               mirrorNode.addChild(node);
            }

            list.add(i + 1, mirrorNode);
            continue;
         }

         // already processed? do not move up
         if(names.contains(name)) {
            continue;
         }

         names.add(name);
         List cols = desc.getSelectionColumns(name, false);

         // no selection column? do not move up
         if(cols.size() == 0) {
            continue;
         }

         List<XNode> pnodes = new ArrayList<>();
         findParentNodes(tree, node.getName(), pnodes);

         // no available parent? do not move up
         if(pnodes.size() == 0) {
            continue;
         }

         // selection can be pulled up if and only if it can be pulled up
         // to all the parent tables
         if(!isMoveableToParents(pnodes, node, desc)) {
            continue;
         }

         TableAssembly table = node.getTable();

         // at runtime, if this table has a MV and there is not MV on parent nodes, don't
         // move up condition. for one, it's useless to move the condition up. in the worst
         // case, moving up condition could result in formula table being applied to unfiltered
         // data, resulting in more expensive processing. for example, a condition could limit
         // the data to a small fraction of data, then the result would be fed into a formula
         // table. if condition is moved up (and formula is not materialized), then the
         // raw data is processed by formula table, which can be very expensive.
         if(desc.isRuntime() && table.getRuntimeMV() != null &&
            table.getRuntimeMV().isPhysical() && !hasParentMV(node))
         {
            continue;
         }

         // selection on aggregate? do not move up
         /*
         if(isSelectionOnAggregate(table, desc)) {
            continue;
         }
         */

         // child has max row? do not move up for the
         // transformation could not return equivalent result
         if(table.getMaxRows() > 0) {
            addFault(desc, TransformationFault.containsMaxRows(table.getName()),
                     table.getName(), null);
            continue;
         }

         // if this table contains ranking, cannot move up
         // for ranking, if is variable ranking, it should only have
         // one of ranking or runtime ranking, won't be both exist
         if(containsRanking(table) && !rankOnBound(node, mvAssembly)) {
            addFault(desc, TransformationFault.containsRanking(name), table.getName(), null);
            continue;
         }

         AggregateInfo tainfo = table.getAggregateInfo();

         // if contains aggregate ranking, cannot move up
         if(!tainfo.isEmpty() && containsAggregateRanking(table)) {
            addFault(desc, TransformationFault.containsAggregateRanking(name),
               table.getName(), null);
            continue;
         }

         if(!tainfo.isEmpty() && tainfo.isCrosstab()) {
            addFault(desc, TransformationFault.containsCrosstab(table.getName()),
                     table.getName(), null);
            continue;
         }

         if(table instanceof RotatedTableAssembly) {
            addFault(desc, TransformationFault.containsRotated(table.getName()),
                     table.getName(), null);
            continue;
         }

         if(table instanceof UnpivotTableAssembly) {
            addFault(desc, TransformationFault.containsUnpivot(table.getName()),
                     table.getName(), null);
            continue;
         }

         for(int j = 0; j < pnodes.size(); j++) {
            XNode pnode = pnodes.get(j);
            TableAssembly ptbl = (TableAssembly) pnode.getValue();
            String pname = ptbl.getName();

            /*
            // parent table is a newly created mirror table? do not
            // create mirror table recursively
            if(pname.equals(rootMirror)) {
               continue OUTER;
            }
            */

            // post condition not support, post runtime supported
            ConditionListWrapper wrapper = ptbl.getPostConditionList();
            ConditionList conds = wrapper == null ? null : wrapper.getConditionList();

            // contains aggregate condition? do not move up
            for(int k = 0; conds != null && k < conds.getSize(); k += 2) {
               ConditionItem citem = conds.getConditionItem(k);
               DataRef attr = citem.getAttribute();

               if(attr instanceof AggregateRef) {
                  addFault(desc, TransformationFault.containsPostCondition(table.getName(), pname),
                           table.getName(), pname);
                  continue OUTER;
               }
            }
         }

         for(int j = 0; j < cols.size(); j++) {
            WSColumn column = (WSColumn) cols.get(j);

            /*
            // mirror column? do not move up
            if(mcols.contains(column)) {
               continue OUTER;
            }
            */

            boolean postColumn = desc.isPostColumn(table.getName(), column);
            DataRef col = (DataRef) column.getDataRef().clone();

            for(int k = 0; k < pnodes.size(); k++) {
               XNode pnode = pnodes.get(k);
               TableAssembly ptbl = (TableAssembly) pnode.getValue();
               AggregateInfo ainfo = ptbl.getAggregateInfo();

               // do not move up if:
               //  1. the selection col is not on the parent table
               //  2. aggregate is not AoA capable
               if(!ainfo.isEmpty() && !isSelectionVisible(ptbl, table, desc) &&
                  (!isAggregateCombinable(ptbl, ainfo) ||
                   findMirror(pnode, node, desc, false) == null &&
                   !ptbl.getName().equals(rootMirror)))
               {
                  String tname = table.getName();
                  String pname = ptbl.getName();
                  addFault(desc, TransformationFault.selectionHiddenAggregate(
                     tname, pname, isAggregateCombinable(ptbl, ainfo)),
                     tname, pname);
                  continue OUTER;
               }

               // one column could not move? do not move up
               if(!isMoveableColumn(table, ptbl, col, postColumn, desc)) {
                  continue OUTER;
               }

               // aggregate formula is DistinctCount do not move up and defined
               // named group
               if(!validateAggregate(ainfo)) {
                  recreate = false;
               }
            }
         }

         for(int j = 0; j < pnodes.size(); j++) {
            moveSelectionUp0(pnodes.get(j), node, desc);
         }

         table.setPreRuntimeConditionList(new ConditionList());
         table.setPostRuntimeConditionList(new ConditionList());
         table.setRankingRuntimeConditionList(new ConditionList());

         desc.recordMVCondition(table);

         // @by jasons, keep the mv update pre-condition on the original table
         // assembly since this is typically the bound assembly. If the
         // condition is not applied to the bound assembly (usually sql), we
         // lose most of the benefit of incremental updates
         if(MVTool.isMVConditionParent(table)) {
            table.setMVUpdatePreConditionList(new ConditionList());
         }
         else {
            MVTool.setMVConditionMoved(table, true);
         }

         table.setMVUpdatePostConditionList(new ConditionList());
         table.setMVDeletePreConditionList(new ConditionList());
         table.setMVDeletePostConditionList(new ConditionList());
         cols.clear();
      }

      if(rootMirror != null) {
         MirrorTableAssembly mirror =
            (MirrorTableAssembly) ws.getAssembly(rootMirror);
         desc.setAnalyzeMVAssembly(mirror.getAssemblyName());

         // for runtime, set mirror table as the mv table
         if(desc.isRuntime()) {
            String mvtassembly = desc.getMVAssembly();
            TableAssembly mvtable = (TableAssembly) ws.getAssembly(mvtassembly);
            RuntimeMV rinfo = mvtable.getRuntimeMV();
            mvtable.setRuntimeMV(null);
            mirror.setRuntimeMV(rinfo);
            desc.setMVAssembly(rootMirror);
         }
         // for analysis, set the base table of mirror table as the mv table
         else {
            String bname = mirror.getAssemblyName();
            desc.setMVAssembly(bname);
         }
      }

      TableAssembly table = rootMirror != null ?
         (TableAssembly) ws.getAssembly(rootMirror) : desc.getMVTable();
      table = table == null ? desc.getTable(false) : table;

      XNode fnode = findNode(list, table.getName());

      if(fnode == null) {
         throw new RuntimeException("MV node not found: " + table.getName());
      }

      return !desc.isSelectionOnChildren(fnode) &&
             !containsSubSelection(desc, table) &&
             !containsGroupRanking(table);
   }

   /**
    * Ranking works if it's bound to the outer assembly
    */
   private boolean rankOnBound(TableNode node, String mvAssembly) {
      final String name = node.getName();
      final XNode parent = node.getParent();
      final TableAssembly table = node.getTable();

      return table instanceof BoundTableAssembly &&
         mvAssembly != null &&
         parent != null &&
         Objects.equals(parent.getName(), mvAssembly) &&
         name.startsWith(mvAssembly) &&
         name.endsWith(Viewsheet.OUTER_TABLE_SUFFIX);
   }

   // check if any parent node contains a usable MV
   private boolean hasParentMV(TableNode node) {
      TableNode parent = (TableNode) node.getParent();

      if(parent == null) {
         return false;
      }

      RuntimeMV rmv = parent.getTable().getRuntimeMV();

      if(rmv != null && rmv.isPhysical()) {
         return true;
      }

      return hasParentMV(parent);
   }

   /**
    * Check if contains aggregate ranking.
    */
   private boolean containsAggregateRanking(TableAssembly table) {
      ConditionListWrapper wrapper = table.getRankingRuntimeConditionList();

      if(wrapper != null) {
         ConditionList conds = wrapper.getConditionList();

         for(int i = 0; i < conds.getSize(); i += 2) {
            ConditionItem citem = conds.getConditionItem(i);
            DataRef ref = citem.getAttribute();

            if(ref instanceof AggregateRef) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check mv condition defined infomation.
    */
   private void checkMVInfo(TransformationDescriptor desc, List<XNode> list)
   {
      Set<String> processed = new HashSet<>();

      for(int i = 0; i < list.size(); i++) {
         TableNode node = (TableNode) list.get(i);
         TableAssembly table = node.getTable();
         String name = table.getName();

         // is mv table?
         if(name.equals(desc.getMVAssembly())) {
            break;
         }

         if(processed.contains(name)) {
            continue;
         }

         processed.add(name);
         List selections = desc.getSelectionColumns(name, false);

         // have selections?
         if(selections.size() > 0 &&
            !desc.mvSelectionDefineOnly(table.getName()))
         {
            break;
         }

         if(selections.size() > 0) {
            for(int j = i + 1; j < list.size(); j++) {
               TableNode pnode = (TableNode) list.get(j);
               TableAssembly ptable = pnode.getTable();
               String pname = ptable.getName();
               List pselections = desc.getSelectionColumns(pname, false);

               // if parent table has selections, warning it
               if(pselections.size() > 0 &&
                  !desc.mvSelectionDefineOnly(ptable.getName()) &&
                  !desc.vsConditionDefineOnly(ptable.getName()) &&
                  isParentTable(ptable, table, true))
               {
                  /*
                  desc.addWarning(
                     TransformationInfo.mvConditionUnderSelection(name));
                  */
               }
            }
         }
      }
   }

   private boolean isParentTable(TableAssembly ptable, TableAssembly stable,
                                 boolean root)
   {
      if(!root && ptable == stable) {
         return true;
      }

      if(ptable instanceof ComposedTableAssembly) {
         ComposedTableAssembly ctable = (ComposedTableAssembly) ptable;
         TableAssembly[] tables = ctable.getTableAssemblies(false);

         for(TableAssembly tbl : tables) {
            if(isParentTable(tbl, stable, false)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Verify the availability of aggregate.
    */
   private boolean validateAggregate(AggregateInfo ainfo) {
      if(ainfo == null || ainfo.getAggregates().length == 0 ||
         ainfo.getGroups().length == 0)
      {
         return true;
      }

      boolean hasNamedGroup = false;

      for(int i = 0; i < ainfo.getGroupCount(); i++) {
         GroupRef gref = ainfo.getGroup(i);
         DataRef ref = gref.getDataRef();

         if(ref instanceof ColumnRef) {
            ref = ((ColumnRef) ref).getDataRef();
         }

         if(ref instanceof NamedRangeRef) {
            hasNamedGroup = true;
            break;
         }
      }

      for(int i = 0; hasNamedGroup && i < ainfo.getAggregateCount(); i++) {
         AggregateRef aref = ainfo.getAggregate(i);
         AggregateFormula formula = aref.getFormula();

         if(formula != null &&
            formula.getFormulaName().equals(XConstants.DISTINCTCOUNT_FORMULA))
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Find the node for the specified name.
    */
   private XNode findNode(List list, String name) {
      int len = list.size();

      for(int i = 0; i < len; i++) {
         XNode node = (XNode) list.get(i);

         if(node.getName().equals(name)) {
            return node;
         }
      }

      return null;
   }

   /**
    * The detail actions of moving selection up.
    */
   private void moveSelectionUp0(XNode pnode, XNode node, TransformationDescriptor desc) {
      TableAssembly ptbl = ((TableNode) pnode).getTable();
      TableAssembly tbl = ((TableNode) node).getTable();
      AggregateInfo group = ptbl.getAggregateInfo();
      TableAssembly mirror = null;

      if(!group.isEmpty() && !isSelectionVisible(ptbl, tbl, desc)) {
         if(!ptbl.getName().equals(rootMirror)) {
            mirror = findMirror(pnode, node, desc, true);

            if(mirror == null) {
               throw new RuntimeException("Mirror MV Table not found: " + pnode.getName());
            }
         }
      }

      boolean ignoredUnion = false;

      // union table? merge sub conditions by OR
      if(umap.containsKey(ptbl.getName())) {
         if(!uset.contains(ptbl.getName())) {
            ConcatenatedTableAssembly ctable = (ConcatenatedTableAssembly) ptbl;
            TableAssembly first = ctable.getTableAssemblies(false)[0];
            uset.add(ptbl.getName());

            ConditionListWrapper wrapper = ptbl.getPreRuntimeConditionList();
            ConditionList preconds = getRuntimeConditions(ctable, true);
            wrapper = mergeConditions(ptbl, first.getName(), preconds, wrapper,
                                      desc);
            ConditionList postconds = getRuntimeConditions(ctable, false);
            wrapper = mergeConditions(ptbl, first.getName(), postconds, wrapper,
                                      desc);
            ptbl.setPreRuntimeConditionList(wrapper);

            // merge sub tables' mv update pre/post and parent table's
            // mv update pre conditions together
            wrapper = ptbl.getMVUpdatePreConditionList();
            preconds = getMVConditions(ctable, true);
            wrapper = mergeConditions(ptbl, first.getName(), preconds, wrapper,
                                      JunctionOperator.AND, desc);
            ptbl.setMVUpdatePreConditionList(wrapper);
            MVTool.setMVConditionParent(ptbl, true);

            wrapper = ptbl.getMVDeletePreConditionList();
            preconds = getMVConditions(ctable, false);
            wrapper = mergeConditions(ptbl, first.getName(), preconds, wrapper,
                                      JunctionOperator.OR, desc);
            ptbl.setMVDeletePreConditionList(wrapper);

            ptbl.setMVForceAppendUpdates(
               ptbl.isMVForceAppendUpdates() ||
               ctable.isMVForceAppendUpdates());
         }
         else {
            ignoredUnion = true;
         }
      }
      else {
         ConditionListWrapper wrapper = ptbl.getPreRuntimeConditionList();
         ConditionListWrapper swrapper = tbl.getPreRuntimeConditionList();
         wrapper = mergeConditions(ptbl, tbl.getName(), swrapper, wrapper, desc);
         swrapper = tbl.getPostRuntimeConditionList();
         wrapper = mergeConditions(ptbl, tbl.getName(), swrapper, wrapper, desc);
         ptbl.setPreRuntimeConditionList(wrapper);
         tbl.setPreRuntimeConditionList(null);
         tbl.setPostRuntimeConditionList(null);

         wrapper = ptbl.getRankingRuntimeConditionList();
         swrapper = tbl.getRankingRuntimeConditionList();
         wrapper = mergeConditions(ptbl, tbl.getName(), swrapper, wrapper, desc);
         ptbl.setRankingRuntimeConditionList(wrapper);
         tbl.setRankingRuntimeConditionList(null);

         // merge sub table's mv update pre/post and parent table's mv update
         // pre conditions together
         // for mv condition, use OR seems more reasonable
         wrapper = ptbl.getMVUpdatePreConditionList();
         swrapper = tbl.getMVUpdateConditionList();
         wrapper = mergeConditions(ptbl, tbl.getName(), swrapper, wrapper,
                                   JunctionOperator.AND, desc);
         ptbl.setMVUpdatePreConditionList(wrapper);
         MVTool.setMVConditionParent(ptbl, true);

         wrapper = ptbl.getMVDeletePreConditionList();
         swrapper = tbl.getMVDeleteConditionList();
         wrapper = mergeConditions(ptbl, tbl.getName(), swrapper, wrapper,
                                   JunctionOperator.OR, desc);
         ptbl.setMVDeletePreConditionList(wrapper);

         ptbl.setMVForceAppendUpdates(
            ptbl.isMVForceAppendUpdates() || tbl.isMVForceAppendUpdates());
      }

      if(!ignoredUnion) {
         moveSelectionColumns(desc, tbl, ptbl);
      }
   }

   /**
    * Get mv conditions from unit table.
    */
   private ConditionList getMVConditions(ConcatenatedTableAssembly ctable,
                                         boolean update)
   {
      TableAssembly[] tables = ctable.getTableAssemblies(false);

      if(tables.length < 1) {
         return null;
      }

      TableAssembly first = tables[0];
      ColumnSelection fcols = first.getColumnSelection(true);
      List list = new ArrayList();

      for(int i = 0; i < tables.length; i++) {
         ConditionListWrapper wrapper = update ?
            tables[i].getMVUpdateConditionList() :
            tables[i].getMVDeleteConditionList();

         if(isEmptyFilter(wrapper)) {
            continue;
         }

         ColumnSelection cols = tables[i].getColumnSelection(true);
         ConditionList conds = wrapper.getConditionList();
         conds = (ConditionList) conds.clone();
         replaceColumn(fcols, cols, conds);
         list.add(conds);
      }

      if(list.size() == 0) {
         return null;
      }

      return ConditionUtil.mergeConditionList(list, JunctionOperator.OR);
   }

   /**
    * Get runtime condition list for union table, the sub condition lists will
    * be or-ed.
    */
   private ConditionList getRuntimeConditions(ConcatenatedTableAssembly ctable,
                                              boolean preruntime)
   {
      TableAssembly[] tables = ctable.getTableAssemblies(false);

      if(tables.length < 1) {
         return null;
      }

      TableAssembly first = tables[0];
      ColumnSelection fcols = first.getColumnSelection(true);
      List list = new ArrayList();

      for(int i = 0; i < tables.length; i++) {
         ConditionListWrapper wrapper = preruntime ?
            tables[i].getPreRuntimeConditionList() :
            tables[i].getPostRuntimeConditionList();

         if(isEmptyFilter(wrapper)) {
            continue;
         }

         ColumnSelection cols = tables[i].getColumnSelection(true);
         ConditionList conds = wrapper.getConditionList();
         conds = (ConditionList) conds.clone();
         replaceColumn(fcols, cols, conds);
         list.add(conds);
      }

      if(list.size() == 0) {
         return null;
      }

      return ConditionUtil.mergeConditionList(list, JunctionOperator.OR);
   }

   /**
    * Replace condition columns.
    */
   private void replaceColumn(ColumnSelection fcols, ColumnSelection cols,
                              ConditionList conds)
   {
      // replace data ref with that of the first table
      for(int i = 0; i < conds.getSize(); i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         DataRef ref = citem.getAttribute();
         int index = cols.indexOfAttribute(ref);

         if(index < 0 || index >= fcols.getAttributeCount()) {
            continue;
         }

         ref = fcols.getAttribute(index);
         citem.setAttribute(ref);
      }
   }

   /**
    * Create mirror table.
    */
   private MirrorTableAssembly createMirror(XNode pnode, XNode node,
                                            TransformationDescriptor desc) {
      TableAssembly ptbl = ((TableNode) pnode).getTable();

      // rename parent table
      String oname = ptbl.getName();
      Worksheet ws = ptbl.getWorksheet();
      String nname = "ST_" + oname;

      // do not create mirror table for times
      if(mtables.containsKey(nname)) {
         return (MirrorTableAssembly) mtables.get(nname);
      }

      ptbl.getInfo().setName(nname);
      pnode.setName(nname);

      // create mirror table to replace the role of the parent table
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, oname, ptbl);
      ws.addAssembly(mirror);

      // change context information accordingly
      names.remove(oname);
      names.add(nname);
      mtables.put(nname, mirror);
      desc.renameSelectionColumns(oname, nname);

      return mirror;
   }

   /**
    * Check if the table assembly contains aggregate conditions.
    */
   private boolean containsPostCondition(TableAssembly table) {
      return containsAggregateCondition(table.getPostConditionList()) ||
             containsAggregateCondition(table.getPostRuntimeConditionList()) ||
             containsAggregateCondition(table.getMVUpdatePostConditionList()) ||
             containsAggregateCondition(table.getMVDeletePostConditionList()) ||
             containsRanking(table) ;
   }

   private boolean containsAggregateCondition(ConditionListWrapper wrapper) {
      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();

      // contains aggregate condition? do not move up
      for(int k = 0; conds != null && k < conds.getSize(); k += 2) {
         ConditionItem citem = conds.getConditionItem(k);
         DataRef attr = citem.getAttribute();

         if(attr instanceof AggregateRef) {
            return true;
         }
      }

      return false;
   }

   /**
    * Find mirror table if there is aggregate defined in parent table.
    */
   private TableAssembly findMirror(XNode pnode, XNode node, TransformationDescriptor desc,
                                    boolean transform)
   {
      String mvname = desc.getMVAssembly();

      if(mvname == null) {
         return null;
      }

      // we should not create mirror if the table contains aggregate
      // condition, this will cause data error
      if(containsPostCondition(((TableNode) pnode).getTable())) {
         return null;
      }

      Worksheet ws = desc.getWorksheet();

      // handle both runtime mode and analysis mode. At runtime mode,
      // mv table is 'VS_t'; and at analysis mode, mv table is just 't'
      if(mvname.startsWith(Assembly.TABLE_VS)) {
         MirrorTableAssembly mvtable = (MirrorTableAssembly) ws.getAssembly(mvname);
         mvname = mvtable.getAssemblyName();
      }

      TableAssembly mvtable = (TableAssembly) ws.getAssembly(mvname);
      String pname = pnode.getName();

      // not a mirror table? do nothing
      if(!(mvtable instanceof MirrorTableAssembly) ||
         // if parent table is already the mv table, we should not
         // allow it move up(sub query binding may be not exist the _O table)
         pname.equals(mvname) && !pname.endsWith("_O"))
      {
         return null;
      }

      TableAssembly mirror = mvtable;

      // mv table is not the mirror of parent table? it's time to create mirror
      // table now
      // mv table contains calculate fields? create mirror, and move aggregate to
      // the mirror, otherwise the calculate fields will be invisible
      if(!((MirrorTableAssembly) mirror).getAssemblyName().equals(pname) ||
         containsCalculateFields(mirror) || containsSubTableAggregateCondition(mirror))
      {
         // not transform? create one fake table
         if(!transform) {
            mirror = new DataTableAssembly(ws, "fake");
         }
         // transform? create mirror for it
         else {
            mirror = createMirror(pnode, node, desc);
         }
      }
      else {
         rootMirror = mirror.getName();
      }

      if(transform) {
         TableAssembly ptbl = ((TableNode) pnode).getTable();
         boolean transformed = "true".equals(mirror.getProperty("mirror.transformed"));

         // do not transform for times
         if(!transformed) {
            // copy aggregate info to mirror table and transform it
            AggregateInfo group = ptbl.getAggregateInfo();
            mirror.setDistinct(ptbl.isDistinct());
            mirror.setMaxRows(ptbl.getMaxRows());
            ptbl.setDistinct(false);
            ptbl.setMaxRows(0);
            AggregateInfo minfo = (AggregateInfo) group.clone();
            mirror.setAggregateInfo(minfo);
            fixParentAggregateInfo(mirror, ptbl);
            rewriteFormula((MirrorTableAssembly) mirror, false);
            mirror.setProperty("mirror.transformed", "true");
         }

         TableAssembly tbl = ((TableNode) node).getTable();
         String oname = ptbl.getName();
         List<WSColumn> cols = desc.getSelectionColumns(tbl.getName(), false);
         ColumnSelection columns = tbl.getColumnSelection();
         ColumnSelection pcolumns = ptbl.getColumnSelection();
         AggregateInfo group = ptbl.getAggregateInfo();

         // it's time to add group to ptable
         for(int i = 0; i < cols.size(); i++) {
            WSColumn column = cols.get(i);
            DataRef col = column.getDataRef();
            ColumnRef col2 = (ColumnRef) normalizeColumn(col, columns);
            DataRef pcol = getParentColumn(pcolumns, tbl.getName(), col2);
            group.addGroup(new GroupRef(pcol));
            ptbl.resetColumnSelection();
         }

         ptbl.resetColumnSelection();
      }

      return mirror;
   }

   // if the mirror contains a pre-condition, and the base is aggregated, should not
   // use the mirror for transformation, which would cause the aggregate being combined
   // into the mirror. since the pre-condition of mirror should be applied to the
   // result of base, when pre-condition AND the aggregate are placed on the same
   // table, it would be applied before the aggregation is performed. (43498)
   protected boolean containsSubTableAggregateCondition(TableAssembly table) {
      ConditionListWrapper pre = table.getPreRuntimeConditionList();

      if(pre != null && !pre.isEmpty()) {
         TableAssembly base = ((MirrorTableAssembly) table).getTableAssembly();
         AggregateInfo ainfo = base.getAggregateInfo();

         return ainfo != null && !ainfo.isEmpty();
      }

      return false;
   }

   /**
    * Rewrite the formula of the top table to recompose the result from the
    * aggregate of child tables. Child tables should not have any aggregate.
    *
    * @param top table.
    * @param groupFix <tt>true</tt> to push down selection columns along with
    *                 group, <tt>false</tt> otherwise
    */
   protected void rewriteFormula(ComposedTableAssembly top, boolean groupFix) {
      TableAssembly[] children = top.getTableAssemblies(false);
      AggregateInfo aggr = top.getAggregateInfo();
      AggregateInfo aggr2 = createChildAggregates(aggr, top);
      setChildrenAggregates(top, aggr2);

      if(groupFix) {
         fixGrouping(children);
      }

      for(int i = 0; i < children.length; i++) {
         children[i].resetColumnSelection();
      }

      top.setAggregateInfo(aggr);
      top.resetColumnSelection();
   }

   /**
    * Add additional columns to grouping if aggregate was pushed down.
    */
   private void fixGrouping(TableAssembly[] tables) {
      for(int i = 0; i < tables.length; i++) {
         TableAssembly table = tables[i];
         AggregateInfo info = table.getAggregateInfo();

         if(info.isEmpty()) {
            continue;
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
   }

   /**
    * Set the aggregate info in the children nodes.
    */
   private void setChildrenAggregates(ComposedTableAssembly ctable, AggregateInfo aggr0) {
      TableAssembly[] children = ctable.getTableAssemblies(false);
      ColumnSelection fcolumns = children.length == 0 ?
         new ColumnSelection() : children[0].getColumnSelection();
      ColumnSelection fcolumns2 = children.length == 0 ?
         new ColumnSelection() : children[0].getColumnSelection(true);
      String fname = children.length == 0 ? "" : children[0].getName();
      boolean concatenated = ctable instanceof ConcatenatedTableAssembly;

      for(int i = children.length - 1; i >= 0; i--) {
         TableAssembly table = children[i];
         AggregateInfo ninfo = new AggregateInfo();
         ColumnSelection columns = table.getColumnSelection();
         ColumnSelection columns2 = table.getColumnSelection(true);
         String tname = table.getName();
         AggregateInfo aggr = (AggregateInfo) aggr0.clone();

         // copy groups down
         for(int j = 0; j < aggr.getGroupCount(); j++) {
            DataRef ref = aggr.getGroup(j).getDataRef();
            ref = (DataRef) ref.clone();
            DataRef oref = ref;

            if(concatenated && i > 0) {
               ColumnRef[] carr = getColumnRef(fcolumns, fname, ref);
               DataRef fref = carr[0];
               DataRef dref = ((ColumnRef) fref).getDataRef();

               if(dref instanceof AliasDataRef) {
                  dref = ((AliasDataRef) dref).getDataRef();
                  fref = dref;
               }

               int index = fcolumns.indexOfAttribute(fref);
               ref = columns2.getAttribute(index);
               ref = AssetUtil.getOuterAttribute(tname, ref);
               ref = new ColumnRef(ref);
               replaceDataRef(oref, ref);
               ref = oref;
            }

            ColumnRef[] carr = getColumnRef(columns, tname, ref);
            ColumnRef column = carr[0];

            if(column != null) {
               ninfo.addGroup(new GroupRef(column));
            }
         }

         // copy aggregates down
         for(int j = 0; j < aggr.getAggregateCount(); j++) {
            AggregateRef aref = aggr.getAggregate(j);
            DataRef ref = aref.getDataRef();
            ref = (DataRef) ref.clone();
            DataRef oref = ref;

            if(concatenated && i > 0) {
               ColumnRef[] carr = getColumnRef(fcolumns, fname, ref);
               DataRef fref = carr[0];
               DataRef dref = ((ColumnRef) fref).getDataRef();

               if(dref instanceof AliasDataRef) {
                  dref = ((AliasDataRef) dref).getDataRef();
                  fref = dref;
               }

               int index = fcolumns.indexOfAttribute(fref);
               ref = columns2.getAttribute(index);
               ref = AssetUtil.getOuterAttribute(tname, ref);
               ref = new ColumnRef(ref);
               replaceDataRef(oref, ref);
               ref = oref;
            }

            ColumnRef[] carr = getColumnRef(columns, tname, ref);
            ColumnRef column = carr[0];

            if(column != null) {
               aref.setDataRef(column);
               DataRef ref2 = aref.getSecondaryColumn();
               ref2 = ref2 == null ? null : (DataRef) ref2.clone();
               DataRef oref2 = ref2;

               if(ref2 != null && concatenated && i > 0) {
                  ColumnRef[] carr2 = getColumnRef(fcolumns, fname, ref2);
                  DataRef fref2 = carr2[0];
                  DataRef dref2 = ((ColumnRef) fref2).getDataRef();

                  if(dref2 instanceof AliasDataRef) {
                     dref2 = ((AliasDataRef) dref2).getDataRef();
                     fref2 = dref2;
                  }

                  int index = fcolumns.indexOfAttribute(fref2);
                  ref2 = columns2.getAttribute(index);
                  ref2 = AssetUtil.getOuterAttribute(tname, ref2);
                  ref2 = new ColumnRef(ref2);
                  replaceDataRef(oref2, ref2);
                  ref2 = oref2;
               }

               if(ref2 != null) {
                  ColumnRef[] carr2 = getColumnRef(columns, tname, ref2);
                  ColumnRef column2 = carr2[0];
                  aref.setSecondaryColumn(column2);
               }

               ninfo.addAggregate(aref);
            }
         }

         table.setAggregateInfo(ninfo);
      }
   }

   /**
    * Create the sub-aggregate for aggregates in the aggregate info. The
    * aggregate passed into the function is modified on return to contain
    * aggregates for re-calculating results from sub-aggregates in the
    * returned aggregate info.
    */
   private AggregateInfo createChildAggregates(AggregateInfo aggr,
                                               TableAssembly top)
   {
      ColumnSelection columns = top.getColumnSelection(false);
      ColumnSelection columns2 = top.getColumnSelection(true);
      AggregateInfo sub = new AggregateInfo();

      for(int i = 0; i < aggr.getGroupCount(); i++) {
         sub.addGroup((GroupRef) aggr.getGroup(i).clone());
      }

      for(int i = 0; i < aggr.getAggregateCount(); i++) {
         AggregateRef ref = aggr.getAggregate(i);
         Collection subs = ref.getSubAggregates();

         if(subs.size() == 0) {
            sub.addAggregate(ref);
         }
         else {
            Iterator iter = subs.iterator();
            List<AggregateRef> aggregates = new ArrayList<>();

            while(iter.hasNext()) {
               AggregateRef subref = (AggregateRef) iter.next();
               sub.addAggregate(subref);
               aggregates.add(subref);
            }

            AggregateRef nref = new CompositeAggregateRef(ref, aggregates);
            aggr.setAggregate(i, nref);

            fixPostConditionItem(top, ref, nref);

            ColumnRef column = (ColumnRef) ref.getDataRef();
            String name = column.getAlias();

            if(name == null || name.length() == 0) {
               name = column.getAttribute();
            }

            String entity = column.getEntity();
            ExpressionRef eref = new ExpressionRef(null, entity, name);
            eref.setExpression("");
            eref.setVirtual(true);
            int index = columns.indexOfAttribute(eref);
            ColumnRef ocolumn = column;
            column = new ColumnRef(eref);

            if(index >= 0) {
               columns.removeAttribute(index);
               columns.addAttribute(index, column);
            }
            else {
               columns.addAttribute(column);
            }

            index = columns2.indexOfAttribute(column);

            if(index >= 0) {
               columns2.removeAttribute(index);
               columns2.addAttribute(index, column);
            }
            else {
               columns2.addAttribute(column);
            }

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
    * Fix post condition item ref.
    */
   private void fixPostConditionItem(TableAssembly top, AggregateRef oref,
                                     AggregateRef nref)
   {
      ConditionListWrapper wrapper = top.getPostConditionList();
      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();
      replaceItem(conds, oref, nref);

      wrapper = top.getMVUpdatePostConditionList();
      conds = wrapper == null ? null : wrapper.getConditionList();
      replaceItem(conds, oref, nref);

      wrapper = top.getMVDeletePostConditionList();
      conds = wrapper == null ? null : wrapper.getConditionList();
      replaceItem(conds, oref, nref);
   }

   private void replaceItem(ConditionList conds, DataRef oref,  DataRef nref) {
      if(conds == null) {
         return;
      }

      for(int i = 0; i < conds.getSize(); i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         DataRef attr = citem.getAttribute();

         if(attr instanceof AggregateRef && attr.equals(oref)) {
            citem.setAttribute(nref);
         }
      }
   }

   /**
    * Replace data ref.
    * @param cref the specified container ref.
    * @param iref the specified contained ref.
    */
   private void replaceDataRef(DataRef cref, DataRef iref) {
      cref = getBaseDataRef(cref);

      if(cref instanceof AliasDataRef) {
         DataRef cref2 = ((AliasDataRef) cref).getDataRef();

         if(cref2 instanceof AliasDataRef) {
            cref = cref2;
         }

         ((AliasDataRef) cref).setDataRef(iref);
      }
   }

   /**
    * Merge conditions.
    */
   private ConditionListWrapper mergeConditions(TableAssembly table,
                                                String sname,
                                                ConditionListWrapper wrapper,
                                                ConditionListWrapper pwrapper,
                                                TransformationDescriptor desc)
   {
      return mergeConditions(table, sname, wrapper, pwrapper,
                             JunctionOperator.AND, desc);
   }

   private ConditionListWrapper mergeConditions(TableAssembly table,
                                                String sname,
                                                ConditionListWrapper wrapper,
                                                ConditionListWrapper pwrapper,
                                                int op,
                                                TransformationDescriptor desc)
   {
      if(isEmptyFilter(wrapper)) {
         return pwrapper;
      }

      ColumnSelection columns = table.getColumnSelection();
      ConditionList conds = (ConditionList) wrapper.getConditionList().clone();

      for(int i = 0; i < conds.getSize(); i += 2)  {
         ConditionItem item = conds.getConditionItem(i);
         DataRef cref = item.getAttribute();
         DataRef pcol = AssetUtil.getOuterAttribute(sname, cref);
         pcol = getOrAddColumn(pcol, columns);

         cref = pcol;
         item.setAttribute(cref);

         XCondition cond = item.getXCondition();

         if(cond instanceof RankingCondition) {
            cref = ((RankingCondition) cond).getDataRef();

            if(cref != null) {
               if(cref instanceof AggregateRef) {
                  pcol = AssetUtil.getOuterAttribute(sname, ((AggregateRef) cref).getDataRef());
               }
               else {
                  pcol = AssetUtil.getOuterAttribute(sname, cref);
               }

               pcol = getOrAddColumn(pcol, columns);

               if(cref instanceof AggregateRef) {
                  ((AggregateRef) cref).setDataRef(pcol);
               }
               else {
                  cref = pcol;
               }

               ((RankingCondition) cond).setDataRef(cref);
            }
         }
      }

      if(!isEmptyFilter(pwrapper)) {
         List<ConditionList> list = new ArrayList<>();
         list.add(conds);
         list.add(pwrapper.getConditionList());
         pwrapper = ConditionUtil.mergeConditionList(list, op);
      }
      else {
         pwrapper = conds;
      }

      return pwrapper;
   }

   private DataRef getOrAddColumn(DataRef pcol, ColumnSelection columns) {
      int pindex = columns.indexOfAttribute(pcol);

      if(pindex == -1) {
         pcol = new ColumnRef(pcol);
         ((ColumnRef) pcol).setVisible(false);
         columns.addAttribute(pcol);
      }
      else {
         pcol = columns.getAttribute(pindex);
      }

      return pcol;
   }

   /**
    * Check if the selection can be moved up from the table.
    * @param tbl current table.
    * @param ptbl parent table.
    * @param col selection column.
    */
   private boolean isMoveableColumn(TableAssembly tbl, TableAssembly ptbl,
                                    DataRef col, boolean postColumn,
                                    TransformationDescriptor desc) {
      ColumnSelection columns = tbl.getColumnSelection();
      ColumnRef col2 = (ColumnRef) normalizeColumn(col, columns);
      int index = columns.indexOfAttribute(col2);

      if(index < 0) {
         LOG.warn("Column not found in selection: " + col2 + " in " + columns);
         return false;
      }

      col2 = (ColumnRef) columns.getAttribute(index);

      // for mirror table without aggregate, we may consider moving up
      // selection even when the column is invisible in child table assembly
      if(!col2.isVisible()) {
         addFault(desc, TransformationFault.
            containsHiddenSelection(tbl.getName(), ptbl.getName()),
            tbl.getName(), ptbl.getName());
         return false;
      }

      AggregateInfo ainfo = tbl.getAggregateInfo();

      // no aggregate info, and selection is hidden in parent table?
      if(ainfo.isEmpty()) {
         ColumnSelection pcols = ptbl.getColumnSelection();
         ColumnRef pcol = getParentColumn(pcols, tbl.getName(), col2);

         if(!pcol.isVisible()) {
            addFault(desc, TransformationFault.
               containsParentHiddenSelection(tbl.getName(), ptbl.getName(),
                  pcol.getName()), tbl.getName(), ptbl.getName());
         }
      }

      if(!ainfo.isEmpty() && ainfo.isCrosstab()) {
         addFault(desc, TransformationFault.containsCrosstab(tbl.getName()),
                  tbl.getName(), ptbl.getName());
         return false;
      }

      GroupRef group = ainfo.getGroup(col2);

      // could not move up to parent if the meaning of this column is changed
      if(group != null) {
         String name = group.getNamedGroupAssembly();

         if(name != null) {
            addFault(desc,
               TransformationFault.containsNamedGroup(tbl.getName()),
               tbl.getName(), ptbl.getName());
            return false;
         }
      }

      AggregateRef aggregate = ainfo.getAggregate(col2);

      // for selection only used in mv selevtion and post selection, don't check
      // because we only support group and aggregate columns for group table
      if(!postColumn && aggregate != null) {
         addFault(desc, TransformationFault.aggregateSelection(tbl.getName()),
                  tbl.getName(), ptbl.getName());
         return false;
      }

      AggregateInfo painfo = ptbl.getAggregateInfo();
      ColumnSelection pcols = ptbl.getColumnSelection(true);
      ColumnRef pcol = getParentColumn(pcols, tbl.getName(), col2);

      if(pcol != null) {
         String mvtassembly = desc.getMVAssembly();
         String boundtable = desc.getBoundTable();
         GroupRef pgroup = painfo.getGroup(pcol);
         String name = pgroup == null ? null : pgroup.getNamedGroupAssembly();

         if(name != null) {
            addFault(desc,
               TransformationFault.containsNamedGroup(ptbl.getName()),
               tbl.getName(), ptbl.getName());
            return false;
         }

         AggregateRef paggr = painfo.getAggregate(pcol);

         // for top table, do not apply this restriction
         // in runtime, the mvtable (e.g. V_MQuery1) is a mirror of the
         // bound table (e.g. Query1). In mv creation, both are Query1,
         // so we check both to determine whether it's top table. Otherwise
         // the transformation may produce different result between mv
         // creation and runtime, and causing a MV created for a table to
         // not hit at runtime
         if(paggr != null && (!desc.isRuntime() ||
            !ptbl.getName().equals(mvtassembly) &&
            !ptbl.getName().equals(boundtable)))
         {
            addFault(desc,
               TransformationFault.aggregateSelection(tbl.getName()),
               tbl.getName(), ptbl.getName());
            return false;
         }
      }

      return true;
   }

   /**
    * Check if the condition can be moved to parents.
    * @param pnodes parent nodes.
    * @param node the current table node.
    */
   private boolean isMoveableToParents(List pnodes, XNode node,
                                       TransformationDescriptor desc) {
      boolean ignoreNested = "true".equals(SreeEnv.getProperty("mv.ignore.nestedSelection"));

      for(int i = 0; i < pnodes.size(); i++) {
         TableNode pnode = (TableNode) pnodes.get(i);
         boolean snested = pnode.isSelectionNested();

         // if(snested && !desc.isRuntime()) {
         if(snested && !ignoreNested) {
            TableAssembly ptbl = pnode.getTable();
            TableAssembly tbl = ((TableNode) node).getTable();
            addFault(desc, TransformationFault.
               nestedSelection(tbl.getName(), ptbl.getName()),
               tbl.getName(), ptbl.getName());
            return false;
         }

         if(!isMoveableToParent(pnode, (TableNode) node, desc)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if is selection visible in parent table.
    */
   private boolean isSelectionVisible(TableAssembly ptable, TableAssembly table,
                                      TransformationDescriptor desc) {
      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();

      if(!isSelectionVisible(ptable, table, wrapper, desc)) {
         return false;
      }

      wrapper = table.getRankingRuntimeConditionList();

      if(!isSelectionVisible(ptable, table, wrapper, desc)) {
         return false;
      }

      wrapper = table.getPostRuntimeConditionList();

      if(!isSelectionVisible(ptable, table, wrapper, desc)) {
         return false;
      }

      // for delete condition, if column not found in parent table public
      // columns, when run delete condition in mv data, will cause column
      // not found problem
      wrapper = table.getMVConditionList();

      if(!isSelectionVisible(ptable, table, wrapper, desc)) {
         return false;
      }

      return true;
   }

   /**
    * Check if the selection is visible in parent table.
    */
   private boolean isSelectionVisible(TableAssembly ptable, TableAssembly table,
                                      ConditionListWrapper wrapper,
                                      TransformationDescriptor desc) {
      ColumnSelection pcols = ptable.getColumnSelection(true);
      ColumnSelection cols = ptable.getColumnSelection(false);
      String pname = ptable.getName();
      String sname = table.getName();
      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();
      int length = conds == null ? 0 : conds.getSize();

      for(int i = 0; i < length; i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         DataRef ref = citem.getAttribute();
         DataRef pref = getParentColumn(pcols, sname, ref);

         if(!pcols.containsAttribute(pref) &&
            // if runtime, parent is mv table and the column found in
            // parent's private columns, we should allow to move selection up
            !(desc.isRuntime() && pname.equals(desc.getMVAssembly()) &&
              cols.containsAttribute(pref)))
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if the selections defined on tbl can be moved up to its parent.
    * @param pnode the parent query tree node.
    * @param node the query tree node of the table the selection column belongs.
    * @param desc transformation descriptor. If it is passed in and the select
    * can't be moved, the reason is added to the descriptor.
    */
   private boolean isMoveableToParent(TableNode pnode, TableNode node,
                                      TransformationDescriptor desc) {
      TableAssembly ptbl = pnode.getTable();
      TableAssembly tbl = node.getTable();
      String pname = ptbl.getName();
      String name = tbl.getName();
      String mvtable = desc.getMVAssembly();
      AggregateInfo ainfo = ptbl.getAggregateInfo();

      boolean subRanking = desc.getRankingSelectionColumns(name).size() > 0;

      // if this table is bound to input assembly, don't create MV since the value may
      // change at runtime
      if(desc.isInputDynamicTable(name)) {
         return false;
      }

      if(subRanking) {
         if(containsCondition(ptbl)) {
            addFault(desc, TransformationFault.
               subRankingParentCondition(tbl.getName(), ptbl.getName()),
               tbl.getName(), ptbl.getName());
            return false;
         }

         if(!ainfo.isEmpty()) {
            addFault(desc, TransformationFault.
               subRankingParentGrouped(tbl.getName(), ptbl.getName()),
               tbl.getName(), ptbl.getName());
            return false;
         }

         // ranking column not visible in parent table, always invalid
         if(!isSelectionVisible(ptbl, tbl, desc)) {
            addFault(desc, TransformationFault.
               subRankingInvisibleInParent(tbl.getName(), ptbl.getName()),
               tbl.getName(), ptbl.getName());
            return false;
         }

         AggregateInfo ainfo2 = tbl.getAggregateInfo();

         if(ainfo2.getGroupCount() > 1 && ainfo2.getAggregateCount() > 0) {
            boolean hasVariable = Stream
               .of(tbl.getRankingConditionList(), tbl.getRankingRuntimeConditionList())
               .filter(Objects::nonNull)
               .map(ConditionListWrapper::getConditionList)
               .filter(l -> l.getSize() > 0)
               .flatMap(l -> IntStream.range(0, l.getSize())
                  .filter(l::isConditionItem)
                  .mapToObj(l::getConditionItem))
               .map(ConditionItem::getXCondition)
               .map(XCondition::getAllVariables)
               .anyMatch(v -> v != null && v.length > 0);

            if(hasVariable) {
               // ranking condition contains variable and multiple group columns
               addFault(desc,
                        TransformationFault.subRankingGroupedWithVariable(
                           tbl.getName(), ptbl.getName()),
                        tbl.getName(), ptbl.getName());
               return false;
            }
         }
      }

      // runtime mode we allow move it up because now the pre
      // condition will still generate before ranking
      if(containsRanking(ptbl) && !desc.isRuntime()) {
         addFault(desc, TransformationFault.containsParentRanking(name, pname), name, pname);

         if(pnode.getParent() == null) {
            recreate = false;
         }

         return false;
      }

      if(!ainfo.isEmpty() && ainfo.isCrosstab()) {
         addFault(desc, TransformationFault.containsParentCrosstab(name, pname), name, pname);
         return false;
      }

      if(ptbl instanceof RotatedTableAssembly) {
         addFault(desc, TransformationFault.containsParentRotated(name, pname), name, pname);
         return false;
      }

      if(ptbl instanceof UnpivotTableAssembly) {
         addFault(desc, TransformationFault.containsParentUnpivot(name, pname), name, pname);
         return false;
      }

      if(!ainfo.isEmpty() && ainfo.containsPercentage()) {
         addFault(desc, TransformationFault.containsParentPercentage(name, pname), name, pname);
         return false;
      }

      if(!ainfo.isEmpty() && ainfo.containsNamedGroup()) {
         addFault(desc, TransformationFault.containsParentNamedGroup(name, pname), name, pname);
         return false;
      }

      // this does not work like before. Before, the goal was to make sure that
      // there was no selection below aggregate, so that mv could be created.
      // Now, the goal is to make sure that there is no selection below mv
      // table, so that mv could be hit
      if(!ainfo.isEmpty() &&
         !isSelectionVisible(ptbl, tbl, desc) &&
         (!desc.isRuntime() || !pname.equals(mvtable)) &&
         findMirror(pnode, node, desc, false) == null && !pname.equals(rootMirror))
      {
         addFault(desc, TransformationFault.selectionHiddenOnParent(name, pname), name, pname);
         return false;
      }

      if(ptbl instanceof MirrorTableAssembly) {
         return true;
      }
      else if(subRanking) {
         return false;
      }
      else if(ptbl instanceof AbstractJoinTableAssembly) {
         AbstractJoinTableAssembly jtable = (AbstractJoinTableAssembly) ptbl;
         Enumeration iter = jtable.getOperatorTables();
         boolean up = "true".equals(SreeEnv.getProperty("mv.outer.moveUp"));
         // left table name and left with condition
         boolean lcond = false;

         while(iter.hasMoreElements()) {
            String[] tbls = (String[]) iter.nextElement();
            lcond = lcond || desc.getSelectionColumns(tbls[0], false).size() > 0;
            boolean rcond = desc.getSelectionColumns(tbls[1], false).size() > 0;
            TableAssemblyOperator top = jtable.getOperator(tbls[0], tbls[1]);

            if(top == null) {
               continue;
            }

            int op = top.getKeyOperation();
            boolean left = op == TableAssemblyOperator.LEFT_JOIN;
            boolean right = op == TableAssemblyOperator.RIGHT_JOIN;

            // left join with right condition
            // right join with left condition
            if(left && rcond || right && lcond) {
               // move up outer table as well if mv.outer.moveUp is true
               if(up) {
                  if(left && name.equals(tbls[1]) ||
                     right && name.equals(tbls[0]))
                  {
                     boolean done = tbl.getProperty("outer.processed") != null;

                     if(!done && desc.isRuntime()) {
                        addOuterCondition(tbl);
                        tbl.setProperty("outer.processed", "true");
                     }
                  }

                  continue;
               }
            }
            // left join without right condition
            // right join without left condition
            else if(left && !rcond || right && !lcond) {
               continue;
            }

            boolean outer = top.isOuterJoin();

            if(outer) {
               addFault(desc, TransformationFault.outerJoin(name, pname), name, pname);
               return false;
            }
            else if(op == TableAssemblyOperator.MERGE_JOIN) {
               addFault(desc, TransformationFault.mergeJoin(name, pname), name, pname);
               return false;
            }
         }

         return true;
      }
      else if(ptbl instanceof ConcatenatedTableAssembly) {
         ConcatenatedTableAssembly ptbl2 = (ConcatenatedTableAssembly) ptbl;

         if(ptbl2.getTableAssemblyCount() <= 1) {
            return false;
         }

         Enumeration iter = ptbl2.getOperators();

         if(iter.hasMoreElements()) {
            TableAssemblyOperator op = (TableAssemblyOperator)
               iter.nextElement();
            int type = op.getKeyOperation();

            switch(type) {
            case TableAssemblyOperator.UNION:
               Boolean result = (Boolean) umap.get(pname);

               if(result == null) {
                  // only distinct union could move up selection
                  result = !op.isDistinct() ? Boolean.FALSE :
                     Boolean.valueOf(isMoveableToUnionTable(ptbl2));
                  umap.put(pname, result);
               }

               if(!result.booleanValue()) {
                  // theoretically, if identical selections are defined on both
                  // sub-tables, the selections can be moved up. But that is
                  // extremely unlikely since it makes no sense at all to define
                  // two selection assemblies pointing to the same column
                  addFault(desc, TransformationFault.moveUpUnion(tbl, ptbl), name, pname);
                  return false;
               }

               return true;
            case TableAssemblyOperator.INTERSECT:
            case TableAssemblyOperator.MINUS:
               TableAssembly left = ptbl2.getTableAssemblies(false)[0];
               // only moveable if the selection is on the left table
               boolean rc = name.equals(left.getName());

               if(!rc) {
                  addFault(desc, TransformationFault.moveUpConcatenated(tbl, ptbl),
                           name, pname);
               }

               return rc;
            }
         }
      }

      return false;
   }

   private boolean containsCondition(TableAssembly table) {
      ConditionListWrapper wrapper = table.getPreConditionList();

      if(wrapper != null && !wrapper.isEmpty()) {
         return true;
      }

      wrapper = table.getPreRuntimeConditionList();

      if(wrapper != null && !wrapper.isEmpty()) {
         return true;
      }

      wrapper = table.getPostConditionList();

      if(wrapper != null && !wrapper.isEmpty()) {
         return true;
      }

      wrapper = table.getPostRuntimeConditionList();

      if(wrapper != null && !wrapper.isEmpty()) {
         return true;
      }

      wrapper = table.getRankingConditionList();

      if(wrapper != null && !wrapper.isEmpty()) {
         return true;
      }

      wrapper = table.getRankingRuntimeConditionList();

      if(wrapper != null && !wrapper.isEmpty()) {
         return true;
      }

      wrapper = table.getMVConditionList();

      if(wrapper != null && !wrapper.isEmpty()) {
         return true;
      }

      return false;
   }

   /**
    * Add outer condition to table.
    */
   private void addOuterCondition(TableAssembly table) {
      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();
      table.setPreRuntimeConditionList(addOuterCondition0(wrapper));
      wrapper = table.getPostRuntimeConditionList();
      table.setPostRuntimeConditionList(addOuterCondition0(wrapper));
   }

   /**
    * Add outer condition to table.
    */
   private ConditionListWrapper addOuterCondition0(ConditionListWrapper wrapper)
   {
      if(isEmptyFilter(wrapper)) {
         return wrapper;
      }

      ConditionList conds = wrapper.getConditionList();
      ConditionItem citem = conds.getConditionItem(0);
      citem = (ConditionItem) citem.clone();
      DataRef ref = citem.getAttribute();

      if(citem == null) {
         return wrapper;
      }

      Condition cond = citem.getCondition();
      int op = cond.getOperation();

      if(op == Condition.PSEUDO) {
         return wrapper;
      }

      cond = new Condition(ref.getDataType());
      cond.setOperation(Condition.NULL);
      citem.setCondition(cond);
      ConditionList conds2 = new ConditionList();
      conds2.append(citem);
      List list = new ArrayList();
      list.add(conds);
      list.add(conds2);
      conds = ConditionUtil.mergeConditionList(list, JunctionOperator.OR);
      return conds;
   }

   /**
    * Check if the selections defined on tbl can be moved up to its parent
    * as a union table.
    */
   private boolean isMoveableToUnionTable(ConcatenatedTableAssembly ptable) {
      TableAssembly[] tarr = ptable.getTableAssemblies(false);

      if(tarr == null) {
         return false;
      }

      int attrcnt = -1;

      for(int i = 0; i < tarr.length; i++) {
         ColumnSelection cols = tarr[i].getColumnSelection(true);
         int cnt = cols.getAttributeCount();

         if(i == 0) {
            attrcnt = cnt;
         }

         if(attrcnt != cnt) {
            return false;
         }
      }

      // this (true) is only valid if the selection column in the
      // sub-tables contains disjoint values. If they share common
      // values, then the selection has to be applied on the sub-table
      // and not the union
      boolean up = "true".equals(SreeEnv.getProperty("mv.union.moveUp"));

      if(!up) {
         return false;
      }

      for(int i = 0; i < attrcnt; i++) {
	 String[] condTypes = {"pre", "post", "update", "delete"};

	 for(String condType : condTypes) {
	    boolean defined = false;

	    for(int j = 0; j < tarr.length; j++) {
	       ColumnSelection cols = tarr[j].getColumnSelection(true);
	       DataRef attr = cols.getAttribute(i);
	       ConditionListWrapper wrapper = null;

	       switch(condType) {
	       case "pre":
		  wrapper = tarr[j].getPreRuntimeConditionList();
		  break;
	       case "post":
		  wrapper = tarr[j].getPostRuntimeConditionList();
		  break;
	       case "update":
		  wrapper = tarr[j].getMVUpdateConditionList();
		  break;
	       case "delete":
		  wrapper = tarr[j].getMVDeleteConditionList();
		  break;
	       }

	       boolean temp = isSelectionDefined(tarr[j], attr, wrapper);

	       if(j == 0) {
		  defined = temp;
	       }

	       if(defined != temp) {
		  return false;
	       }
	    }
	 }
      }

      return true;
   }

   /**
    * Check if selection is defined.
    */
   private boolean isSelectionDefined(TableAssembly table, DataRef ref,
                                      ConditionListWrapper wrapper)
   {
      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();

      if(conds == null || ref == null) {
         return false;
      }

      int size = conds.getSize();

      for(int i = 0; i < size; i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         DataRef attr = citem.getAttribute();

         if(ref.equals(attr)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Put the node and its children to arraylist.
    */
   private void copyToList(XNode node, List<XNode> list, boolean ignore) {
      for(int i = 0; i < node.getChildCount(); i++) {
         XNode snode = node.getChild(i);
         copyToList(snode, list, true);
      }

      // make sure that children are put together
      for(int i = 0; i < node.getChildCount(); i++) {
         XNode snode = node.getChild(i);

         if(!list.contains(snode)) {
            list.add(snode);
         }
      }

      if(!ignore && !list.contains(node)) {
         list.add(node);
      }
   }

   /**
    * Find parent nodes of the given node.
    */
   private void findParentNodes(XNode node, String name, List<XNode> pnodes) {
      if(node.getName().equals(name) && node.getParent() != null) {
         pnodes.add(node.getParent());
      }

      for(int i = 0; i < node.getChildCount(); i++) {
         findParentNodes(node.getChild(i), name, pnodes);
      }
   }

   /**
    * Get the mirror table contains aggregate info.
    */
   public String getRootMirror() {
      return rootMirror;
   }

   /**
    * Get the transformation message for this transformer.
    * @param child child data block.
    */
   @Override
   protected TransformationInfo getInfo(String child) {
      return TransformationInfo.selectionUp(child);
   }

   /**
    * Is need recreate mv or not.
    */
   public boolean isNeedRecreateMV() {
      return recreate;
   }

   private static final Logger LOG = LoggerFactory.getLogger(SelectionUpTransformer.class);

   private List<XNode> list = new ArrayList<>();
   private Set<String> names = new HashSet<>();
   private Map mtables = new HashMap();
   private Map umap = new HashMap();
   private Set uset = new HashSet();
   private String rootMirror = null;
   private boolean recreate = true;
}
