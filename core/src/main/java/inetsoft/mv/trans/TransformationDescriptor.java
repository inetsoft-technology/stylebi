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
import inetsoft.mv.data.XDimIndex;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.util.HierarchyListModel;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * The transformation descriptor captures the transformation performed on a
 * table assembly for one viewsheet. It is used to rewrite table assembly to
 * hit mv.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class TransformationDescriptor {
   /**
    * Constant indicates that transformer is for analytic transformation.
    */
   public static final int ANALYTIC_MODE = 1;
   /**
    * Constant indicates that transformer is for runtime transformation.
    */
   public static final int RUN_MODE = 2;

   /**
    * Constant indicates that it's failed to create materialization.
    */
   public static final int FAILURE = 1;
   /**
    * Constant indicates that it's successful to create materialization.
    */
   public static final int SUCCESS = 2;
   /**
    * Constant indicates that it's partial to create materialization.
    */
   public static final int PARTIAL = SUCCESS | FAILURE;

   /**
    * Find the mv table.
    */
   public static TableAssembly findMVTable(TableAssembly table) {
      if(table.getRuntimeMV() != null) {
         return table;
      }

      if(!(table instanceof MirrorTableAssembly)) {
         return null;
      }

      MirrorTableAssembly ctable = (MirrorTableAssembly) table;
      TableAssembly[] tables = ctable.getTableAssemblies(false);

      for(final TableAssembly tableAssembly : tables) {
         table = findMVTable(tableAssembly);

         if(table != null) {
            return table;
         }
      }

      return null;
   }

   /**
    * Default constructor.
    */
   public TransformationDescriptor() {
      super();
   }

   /**
    * Create a TransformationDescriptor for the specified viewsheet.
    */
   public TransformationDescriptor(TableAssembly table, int mode) {
      TableAssembly mvtable = findMVTable(table);
      RuntimeMV rmv = mvtable.getRuntimeMV();
      Viewsheet vs = rmv.getViewsheet();
      String vassembly = rmv.getVSAssembly();
      this.mode = mode;
      this.vs = vs;
      this.ws = new WorksheetWrapper(table.getWorksheet());

      if(isAnalytic()) {
         VSUtil.shrinkTable(vs, this.ws);
      }

      init();
      reset(vassembly, table.getName(), mvtable.getName(), rmv.getBoundTable());
   }

   /**
    * Create a TransformationDescriptor for the specified viewsheet.
    */
   public TransformationDescriptor(Viewsheet vs, int mode) {
      this(vs, vs.getBaseWorksheet(), mode);
   }

   /**
    * Create a TransformationDescriptor.
    */
   public TransformationDescriptor(Viewsheet vs, Worksheet ws, int mode) {
      this.mode = mode;
      this.vs = vs;
      this.ws = ws == null ? null : new WorksheetWrapper(ws);

      if(isAnalytic()) {
         VSUtil.shrinkTable(vs, this.ws);
      }

      init();
   }

   /**
    * Create a TransformationDescriptor.
    */
   public TransformationDescriptor(TransformationDescriptor desc) {
      super();

      this.mode = desc.mode;
      this.vs = desc.vs;
      Worksheet ws = vs.getBaseWorksheet();
      this.ws = ws == null ? null : new WorksheetWrapper(ws);
      this.transMap = desc.transMap;
      this.warningsMap = desc.warningsMap;
      this.userInfo = desc.userInfo;
      this.faultsMap = desc.faultsMap;
      this.mayNotHitMVMap = desc.mayNotHitMVMap;
      this.dataBlocksMap = desc.dataBlocksMap;
      this.mvResultMap = desc.mvResultMap;

      init();
   }

   /**
    * Intialize this transformation descriptor.
    */
   private void init() {
      Assembly[] assemblies = (vs != null) ? vs.getAssemblies() : new Assembly[0];

      for(final Assembly assembly : assemblies) {
         if(assembly instanceof SelectionVSAssembly) {
            SelectionVSAssembly sassembly = (SelectionVSAssembly) assembly;
            SelectionVSAssemblyInfo sinfo = (SelectionVSAssemblyInfo) sassembly.getInfo();

            String tname = sinfo.getFirstTableName();
            DataRef[] refs = sinfo.getDataRefs();

            // ignore unbound assembly
            // Bug #53973, Bug #40884, skip the processing of selections if the
            // base table is a vs assembly
            if(tname == null || sinfo.getSourceType() == SourceInfo.VS_ASSEMBLY) {
               continue;
            }

            processSelections(tname, refs, sassembly);

            for(String tname2 : sinfo.getAdditionalTableNames()) {
               processSelections(tname2, refs, sassembly);
            }
         }
         // if a (embedded) table is bound to an input assembly, it value could be changed
         // at runtime by user. we treat it as a select so a MV would not be created for
         // table that uses this table.
         else if(assembly instanceof InputVSAssembly) {
            String tname = ((InputVSAssembly) assembly).getTableName();
            DataRef ref = ((InputVSAssembly) assembly).getColumn();

            if(tname != null && ref != null) {
               addPreSelectionColumn(tname, ref);
               dynamicTables.add(tname);
            }
         }
      }

      // handle the case - parameter defined in table assembly condition
      Worksheet base = ((WorksheetWrapper) ws).getWorksheet();
      VariableTable vars = Viewsheet.getVariableTable(base);
      prepareTables((WorksheetWrapper) ws, vars);
      assemblies = ws.getAssemblies();

      for(final Assembly assembly : assemblies) {
         if(!(assembly instanceof TableAssembly)) {
            continue;
         }

         TableAssembly table = (TableAssembly) assembly;
         processDynamicExpressions(table);
         processPreSelections(table, vars);
         processPostSelections(table, vars);
         processRankingSelections(table, vars);
      }

      // handle the case - condition list defined in viewsheet
      assemblies = (vs != null) ? vs.getAssemblies() : new Assembly[0];

      for(final Assembly assembly : assemblies) {
         if(!(assembly instanceof DynamicBindableVSAssembly)) {
            continue;
         }

         DynamicBindableVSAssembly dassembly = (DynamicBindableVSAssembly) assembly;
         String tname = dassembly.getTableName();

         if(tname == null || !ws.containsAssembly(tname)) {
            continue;
         }

         TableAssembly target = (TableAssembly) ws.getAssembly(tname);
         ConditionList conds = dassembly.getPreConditionList();
         ColumnSelection cols = target.getColumnSelection();

         if(conds != null && !conds.isEmpty()) {
            List scolumns = getSelectionColumns(target.getName(), false);

            if(scolumns.isEmpty()) {
               vsselections.add(target.getName());
            }

            conds = VSUtil.normalizeConditionList(cols, conds);
            int size = conds.getSize();

            for(int j = 0; j < size; j += 2) {
               ConditionItem citem = conds.getConditionItem(j);
               XCondition cond = citem.getXCondition();
               int op = cond.getOperation();

               if(!XDimIndex.supportsOperation(op)) {
                  throw new RuntimeException(
                     "Unsupported condition op found in " + cond +
                     " in vsassembly " + dassembly);
               }

               if(cond instanceof Condition) {
                  Condition ccond = (Condition) cond;
                  int cnt = ccond.getValueCount();

                  for(int k = 0; k < cnt; k++) {
                     Object val = ccond.getValue(k);

                     if(val instanceof DataRef) {
                        throw new RuntimeException(
                           "Comparison with a field is not supported: " + cond +
                           " in vsassembly " + dassembly);
                     }
                  }
               }

               DataRef ref = citem.getAttribute();
               ColumnRef col = AbstractTransformer.getSelectionDataRef(ref, true);
               addPreSelectionColumn(target.getName(), col);
            }

            if(!isRuntime()) {
               ConditionListWrapper pwrapper =
                  target.getPreRuntimeConditionList();

               if(pwrapper != null && !pwrapper.isEmpty()) {
                  List<ConditionList> list = new ArrayList<>();
                  list.add(pwrapper.getConditionList());
                  list.add(conds);
                  conds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
               }

               target.setPreRuntimeConditionList(conds);
            }
         }

         /*
         conds = dassembly.getPostConditionList();

         if(conds != null && !conds.isEmpty()) {
            // problematic logic here
            conds = VSUtil.normalizeConditionList(cols, conds);
            ConditionListWrapper pwrapper =
               target.getPostRuntimeConditionList();

            if(pwrapper != null && !pwrapper.isEmpty()) {
               List list = new ArrayList();
               list.add(pwrapper.getConditionList());
               list.add(conds);
               conds = AssetUtil.mergeConditionList(list, JunctionOperator.AND);
            }

            target.setPostRuntimeConditionList(conds);
         }
         */
      }

      assemblies = ws.getAssemblies();

      for(final Assembly assembly : assemblies) {
         if(!(assembly instanceof TableAssembly)) {
            continue;
         }

         TableAssembly table = (TableAssembly) assembly;

         // handle mv conditions in ws table
         addMVSelections(table, true);
         addMVSelections(table, false);
         addMVConditionTable(table);
         addDynamicTableFromDependency(table);
      }

      createPseudoFilter();
   }

   // add this table to dynamic tables if it depends on a dynamic table.
   private void addDynamicTableFromDependency(TableAssembly table) {
      if(!dynamicTables.contains(table.getName())) {
         Assembly[] darr = AssetUtil.getDependedAssemblies(ws, table, true);
         boolean dynamic = Arrays.stream(darr).anyMatch(a -> dynamicTables.contains(a.getName()));

         if(dynamic) {
            dynamicTables.add(table.getName());
         }
      }
   }

   private void processSelections(String tname, DataRef[] refs, SelectionVSAssembly vsobj) {
      RangeInfo range = RangeInfo.getRangeInfo(vsobj);
      TableAssembly base = (TableAssembly) ws.getAssembly(tname);

      if(base == null) {
         throw new RuntimeException(
            String.format("The selection assembly %s references a table that's missing" +
                             " from the worksheet: %s", vsobj.getAbsoluteName(), tname));
      }

      ColumnSelection basecols = base.getColumnSelection();

      for(final DataRef dataRef : refs) {
         DataRef baseref = basecols.getAttribute(dataRef.getName());
         List<DataRef> allused = new ArrayList<>();
         allused.add(dataRef);

         // add fields used by calc since calc itself is not in mv
         if(baseref instanceof CalculateRef) {
            Enumeration iter = ((CalculateRef) baseref).getExpAttributes();
            allused.addAll(Collections.list(iter));
         }

         for(DataRef ref : allused) {
            WSColumn column = addPreSelectionColumn(tname, ref);
            column.setRangeInfo(range);
            WSColumn column2 = addPreSelectionColumn(Assembly.SELECTION + tname, ref);
            column2.setRangeInfo(range);

            if(vsobj instanceof AssociatedSelectionVSAssembly ||
               vsobj instanceof TimeSliderVSAssembly)
            {
               addAssociatedSelectionColumn(tname, ref);

               String measure = vsobj.getMeasureValue();

               // add column used for measure (selection list/tree)
               // not sure why it was not added if the calc field exists. need to recognized
               // it later. (49776)
               // if(measure != null && getViewsheet().getCalcField(tname, measure) == null*/) {
               if(measure != null) {
                  addAssociatedSelectionColumn(tname, new AttributeRef(null, measure));
               }
            }
         }
      }
   }

   // if table contains expression column that are dynamic (e.g. dependent on parameter),
   // it is treated as dynamic. (48975)
   private void processDynamicExpressions(TableAssembly table) {
      ColumnSelection cols = table.getColumnSelection(false);
      boolean dynamic = cols.stream()
         .map(col -> col instanceof ColumnRef ? ((ColumnRef) col).getDataRef() : col)
         .filter(col -> col instanceof ExpressionRef)
         .anyMatch(col -> !MVTool.isExpressionMVCompatible(((ExpressionRef) col).getExpression()));

      if(dynamic) {
         dynamicTables.add(table.getName());
      }
   }

   /**
    * Process pre conditions.
    */
   private void processPreSelections(TableAssembly table, VariableTable vars) {
      ColumnSelection cols = table.getColumnSelection(true);
      ConditionListWrapper wrapper = table.getPreConditionList();

      // dynamic pre condition list?
      if(AbstractTransformer.isDynamicFilter(this, wrapper, vars) || isWsMVNotAllowed(table)) {
         ConditionList conds = wrapper.getConditionList();
         int size = conds.getSize();

         // add selection column as well
         for(int j = 0; j < size; j += 2) {
            ConditionItem citem = conds.getConditionItem(j);
            XCondition cond = citem.getXCondition();
            int op = cond.getOperation();

            if(!XDimIndex.supportsOperation(op)) {
               throw new RuntimeException("Unsupported pre condition found: "
                                          + cond + " in table assembly: "
                                          + table);
            }

            DataRef ref = citem.getAttribute();
            int idx = cols.indexOfAttribute(ref);

            if(idx < 0 && vs != null) {
               throw new RuntimeException(
                  "Column \"" + ref +
                  "\" in condition is not public (invisible) in table \""
                  + table.getName() + "\"." +
                  " To support parameterized condition \"" + citem
                  + "\", the column is required to be visible!" +
                  "Please ensure that the column is visible in table\"" +
                  table.getName() + "\" in worksheet \" " + vs.getBaseEntry() + "\".");
            }

            ColumnRef col = AbstractTransformer.getSelectionDataRef(ref, true);
            addPreSelectionColumn(table.getName(), col);

            // if col is a variable, we need to add it to the association MV table.
            // otherwise when association MV is used, the variable column would be
            // missing and it would be unusable. (41812)
            String baseName = VSUtil.stripOuter(table.getName());
            addAssociatedSelectionColumn(baseName, col);
         }

         // move pre condition list to pre runtime condition list
         table = (TableAssembly) ws.getAssembly(table.getName());
         wrapper = table.getPreConditionList();
         table.setPreConditionList(new ConditionList());
         ConditionListWrapper rwrapper =
            table.getPreRuntimeConditionList();
         List list = new ArrayList();
         list.add(rwrapper == null ? null : rwrapper.getConditionList());
         list.add(wrapper.getConditionList());
         ConditionList mconds =
            ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
         table.setPreRuntimeConditionList(mconds);
      }
   }

   /**
    * Process post selections.
    */
   private void processPostSelections(TableAssembly table, VariableTable vars) {
      ConditionListWrapper wrapper = table.getPostConditionList();

      if(AbstractTransformer.isDynamicFilter(this, wrapper, vars) || isWsMVNotAllowed(table)) {
         ConditionList conds = wrapper.getConditionList();
         int size = conds.getSize();

         // add selection column as well
         for(int j = 0; j < size; j += 2) {
            ConditionItem citem = conds.getConditionItem(j);
            XCondition cond = citem.getXCondition();
            int op = cond.getOperation();

            if(!XDimIndex.supportsOperation(op)) {
               throw new RuntimeException(
                  "Unsupported post condition found: " + cond +
                  " in table assembly: " + table);
            }

            DataRef ref = citem.getAttribute();

            if(ref instanceof AggregateRef) {
               ref = ((AggregateRef) ref).getDataRef();
            }
            else if(ref instanceof GroupRef) {
               ref = ((GroupRef) ref).getDataRef();
            }
            else {
               throw new RuntimeException(
                  "Data ref " + ref + " is not a group or aggregate ref found in table " +
                  table + "'s post condition.");
            }

            ColumnRef col = AbstractTransformer.getSelectionDataRef(ref, true);
            addPostSelectionColumn(table.getName(), col);
         }

         // move pre condition list to pre runtime condition list
         table = (TableAssembly) ws.getAssembly(table.getName());
         wrapper = table.getPostConditionList();
         table.setPostConditionList(new ConditionList());
         ConditionListWrapper rwrapper =
            table.getPostRuntimeConditionList();
         List list = new ArrayList();
         list.add(rwrapper == null ? null : rwrapper.getConditionList());
         list.add(wrapper.getConditionList());
         ConditionList mconds =
            ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
         table.setPostRuntimeConditionList(mconds);
      }
   }

   /**
    * Process ranking conditions.
    */
   private void processRankingSelections(TableAssembly table,
                                         VariableTable vars)
   {
      ColumnSelection cols = table.getColumnSelection(true);
      ConditionListWrapper wrapper = table.getRankingConditionList();

      if(AbstractTransformer.isDynamicFilter(this, wrapper, vars) || isWsMVNotAllowed(table)) {
         ConditionList conds = wrapper.getConditionList();
         int size = conds.getSize();

         // add selection column as well
         for(int j = 0; j < size; j += 2) {
            ConditionItem citem = conds.getConditionItem(j);
            DataRef ref = citem.getAttribute();

            if(ref instanceof AggregateRef) {
               ref = ((AggregateRef) ref).getDataRef();
            }

            int idx = cols.indexOfAttribute(ref);

            if(idx < 0 && vs != null) {
               throw new RuntimeException(
                  "Column \"" + ref +
                  "\" in condition is not public (invisible) in table \""
                  + table.getName() + "\"." +
                  " To support parameterized ranking condition \"" + citem
                  + "\", the column is required to be visible!" +
                  "Please ensure that the column is visible in table\"" +
                  table.getName() +"\" in worksheet \" " +
                  vs.getBaseEntry() + "\".");
            }

            ColumnRef col = AbstractTransformer.getSelectionDataRef(ref, true);
            addRankingSelectionColumn(table.getName(), col);
         }

         // move pre condition list to pre runtime condition list
         table = (TableAssembly) ws.getAssembly(table.getName());
         wrapper = table.getRankingConditionList();
         table.setRankingConditionList(new ConditionList());
         ConditionListWrapper rwrapper =
            table.getRankingRuntimeConditionList();
         List list = new ArrayList();
         list.add(rwrapper == null ? null : rwrapper.getConditionList());
         list.add(wrapper.getConditionList());
         ConditionList mconds =
            ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
         table.setRankingRuntimeConditionList(mconds);
      }
   }

   // this is for WS MV (currently disabled).
   private static boolean isWsMVNotAllowed(TableAssembly table) {
      // WS MV only materialize the buttom bound table (table bound directly to query).
      // if ws mv is revived, should introduce an explicit flag instead of relying on
      // whether vs is set, because a transformer may have both ws MV and vs MV and
      // the presence of vs should not determine which MV is used (either could be used
      // if available.
      //return vs == null && !(table instanceof BoundTableAssembly);
      return false;
   }

   /**
    * Prepare the tables in worksheet.
    */
   private void prepareTables(WorksheetWrapper ws, VariableTable vars) {
      Worksheet base = ((WorksheetWrapper) ws).getWorksheet();
      Assembly[] objs = base.getAssemblies();

      OUTER:
      for(Assembly obj : objs) {
         if(!(obj instanceof TableAssembly)) {
            continue;
         }

         TableAssembly table = (TableAssembly) obj;
         // create table in ws wrapper
         table = (TableAssembly) ws.getAssembly(table.getName());

         // multi thread problem, fix bug1323341598735
         if(table == null) {
            continue;
         }

         ColumnSelection cols = table.getColumnSelection(true);
         ConditionListWrapper wrapper = table.getPreConditionList();

         // not dynamic pre condition list?
         if(!AbstractTransformer.isDynamicFilter(this, wrapper, vars)) {
            continue;
         }

         AggregateInfo group = table.getAggregateInfo();
         boolean composed = table instanceof ComposedTableAssembly;
         ConditionList conds = wrapper.getConditionList();
         int size = conds.getSize();

         for(int j = 0; j < size; j += 2) {
            ConditionItem citem = conds.getConditionItem(j);
            DataRef ref = citem.getAttribute();
            int idx = cols.indexOfAttribute(ref);

            // if not composed table, and with aggregate on pre condition,
            // create detail table to handle the pre conditions, so mv can
            // be created, for composed table, sub mv will be created to
            // support it
            if(idx < 0 || !composed && group != null && !group.isEmpty() &&
               group.containsAggregate(ref))
            {
               // column not in public selections? time to create mirror
               if(createDetail(ws, table)) {
                  continue OUTER;
               }

               return;
            }
         }
      }

      objs = ws.getAssemblies();

      OUTER:
      for(Assembly obj : objs) {
         if(!(obj instanceof TableAssembly)) {
            continue;
         }

         TableAssembly table = (TableAssembly) obj;
         ColumnSelection cols = table.getColumnSelection(true);
         ConditionListWrapper wrapper = table.getPostConditionList();

         // not dynamic post condition list?
         if(!AbstractTransformer.isDynamicFilter(this, wrapper, vars)) {
            continue;
         }

         AggregateInfo group = table.getAggregateInfo();
         boolean composed = table instanceof ComposedTableAssembly;
         ConditionList conds = wrapper.getConditionList();
         int size = conds.getSize();

         for(int j = 0; j < size; j += 2) {
            ConditionItem citem = conds.getConditionItem(j);
            DataRef ref = citem.getAttribute();

            if(ref instanceof AggregateRef) {
               ref = ((AggregateRef) ref).getDataRef();
            }

            int idx = cols.indexOfAttribute(ref);

            if(idx < 0 && !composed) {
               // column not in public selections? time to create mirror
               if(createAggregate(ws, table)) {
                  continue OUTER;
               }

               return;
            }
         }
      }

      objs = ws.getAssemblies();

      OUTER:
      for(Assembly obj : objs) {
         if(!(obj instanceof TableAssembly)) {
            continue;
         }

         TableAssembly table = (TableAssembly) obj;
         ColumnSelection cols = table.getColumnSelection(true);
         ConditionListWrapper wrapper = table.getRankingConditionList();

         // not dynamic post condition list?
         if(!AbstractTransformer.isDynamicFilter(this, wrapper, vars)) {
            continue;
         }

         AggregateInfo group = table.getAggregateInfo();
         boolean composed = table instanceof ComposedTableAssembly;
         ConditionList conds = wrapper.getConditionList();
         int size = conds.getSize();

         for(int j = 0; j < size; j += 2) {
            ConditionItem citem = conds.getConditionItem(j);
            DataRef ref = citem.getAttribute();

            if(!(ref instanceof AggregateRef)) {
               continue;
            }

            // aggregate ranking with variable on non-composed table?
            // time to create detail table for sub mv
            if(!composed) {
               if(createDetail(ws, table)) {
                  continue OUTER;
               }

               return;
            }
         }
      }
   }

   /**
    * Create mirror table.
    */
   private boolean createDetail(WorksheetWrapper ws, TableAssembly table) {
      if(!table.isPlain()) {
         return false;
      }

      AggregateInfo group = table.getAggregateInfo();
      group = group == null ? new AggregateInfo() : group;
      // here we just create a mirror table to contains the aggregate, post
      // conditions which comes from child table, pre conditions just keep
      // in child table, for aggregate down logic will be maintained later
      // in selection up transformer
      String otname = table.getName();
      String ntname = "Detail_" + otname;
      table.getInfo().setName(ntname);
      // copy aggregate info to mirror table and transform it
      AggregateInfo minfo = (AggregateInfo) group.clone();
      table.setAggregateInfo(new AggregateInfo());
      table.resetColumnSelection();
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, otname, table);
      ws.addAssembly(mirror);

      // copy aggregate info to mirror table and transform it
      mirror.setAggregateInfo(minfo);
      AbstractTransformer.fixParentAggregateInfo(mirror, table);
      mirror.resetColumnSelection();
      mirror.setDistinct(table.isDistinct());
      table.setDistinct(false);
      mirror.setMaxRows(table.getMaxRows());
      table.setMaxRows(0);

      ColumnSelection columns = table.getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) columns.getAttribute(i);
         col.setVisible(true);
      }

      table.setColumnSelection(columns);

      /*
      ConditionListWrapper wrapper = table.getPreConditionList();
      mirror.setPreConditionList(replace(mirror, table, wrapper));
      table.setPreConditionList(new ConditionList());

      wrapper = table.getPreRuntimeConditionList();
      mirror.setPreRuntimeConditionList(replace(mirror, table, wrapper));
      table.setPreRuntimeConditionList(new ConditionList());
      */

      ConditionListWrapper wrapper = table.getPostConditionList();
      mirror.setPostConditionList(replace(mirror, table, wrapper));
      table.setPostConditionList(new ConditionList());

      wrapper = table.getPostRuntimeConditionList();
      mirror.setPostRuntimeConditionList(replace(mirror, table, wrapper));
      table.setPostRuntimeConditionList(new ConditionList());

      wrapper = table.getRankingConditionList();
      mirror.setRankingConditionList(replace(mirror, table, wrapper));
      table.setRankingConditionList(new ConditionList());

      wrapper = table.getMVUpdateConditionList();
      mirror.setMVUpdatePreConditionList(replace(mirror, table, wrapper));
      table.setMVUpdatePreConditionList(new ConditionList());

      wrapper = table.getMVUpdatePostConditionList();
      mirror.setMVUpdatePostConditionList(replace(mirror, table, wrapper));
      table.setMVUpdatePostConditionList(new ConditionList());

      wrapper = table.getMVDeletePreConditionList();
      mirror.setMVDeletePreConditionList(replace(mirror, table, wrapper));
      table.setMVDeletePreConditionList(new ConditionList());

      wrapper = table.getMVDeletePostConditionList();
      mirror.setMVDeletePostConditionList(replace(mirror, table, wrapper));
      table.setMVDeletePostConditionList(new ConditionList());

      mirror.setMVForceAppendUpdates(table.isMVForceAppendUpdates());
      return true;
   }

   /**
    * Create a aggregate table.
    */
   private boolean createAggregate(Worksheet ws, TableAssembly table) {
      AggregateInfo ainfo = table.getAggregateInfo();

      if(!table.isPlain() || ainfo == null || ainfo.isEmpty()) {
         return false;
      }

      String otname = table.getName();
      String ntname = "Aggregate_" + otname;
      table.getInfo().setName(ntname);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, otname, table);
      ws.addAssembly(mirror);
      ColumnSelection sel = table.getColumnSelection();

      for(int i = 0; i < ainfo.getGroupCount(); i++) {
         ColumnRef ref = (ColumnRef) ainfo.getGroup(i).getDataRef();
         ref = (ColumnRef) sel.findAttribute(ref);
         ref.setVisible(true);
      }

      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         ColumnRef ref = (ColumnRef) ainfo.getAggregate(i).getDataRef();
         ref = (ColumnRef) sel.findAttribute(ref);
         ref.setVisible(true);
      }

      table.setColumnSelection(sel);
      return true;
   }

   /**
    * Replace condition columns.
    */
   private ConditionListWrapper replace(TableAssembly ptbl, TableAssembly tbl,
                                        ConditionListWrapper wrapper)
   {
      if(wrapper == null || wrapper.isEmpty()) {
         return wrapper == null ? new ConditionList() : wrapper;
      }

      ColumnSelection columns = tbl.getColumnSelection();
      ColumnSelection pcolumns = ptbl.getColumnSelection();
      ConditionList conds = wrapper.getConditionList();
      int size = conds.getSize();

      for(int i = 0; i < size; i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         XCondition xcond = citem.getXCondition();

         if(xcond instanceof RankingCondition) {
            RankingCondition rcond = (RankingCondition) xcond;
            DataRef aggregate = rcond.getDataRef();
            rcond.setDataRef(replace(aggregate, tbl.getName(), columns, pcolumns));
         }

         DataRef ref = citem.getAttribute();
         citem.setAttribute(replace(ref, tbl.getName(), columns, pcolumns));
      }

      return wrapper;
   }

   /**
    * Replace column.
    */
   private DataRef replace(DataRef ref, String stable, ColumnSelection scolumns,
                           ColumnSelection pcolumns)
   {
      GroupRef gref = null;
      AggregateRef aref = null;

      if(ref == null) {
         return null;
      }

      if(ref instanceof AggregateRef) {
         aref = (AggregateRef) ref;
         ref = aref.getDataRef();
      }

      if(ref instanceof GroupRef) {
         gref = (GroupRef) ref;
         ref = gref.getDataRef();
      }

      DataRef col2 = AbstractTransformer.normalizeColumn(ref, scolumns);
      DataRef pcol = AbstractTransformer.getParentColumn(pcolumns, stable, col2);

      if(aref != null) {
         aref.setDataRef(pcol);
         pcol = aref;
      }

      if(gref != null) {
         gref.setDataRef(pcol);
         pcol = gref;
      }

      return pcol;
   }

   /**
    * Add a table which contains mv conditions.
    */
   private void addMVConditionTable(TableAssembly table) {
      if(mvCondsTable.containsKey(table)) {
         return;
      }

      ConditionListWrapper wrapper = table.getMVConditionList();

      if(wrapper == null || wrapper.isEmpty()) {
         return;
      }

      mvCondsTable.put(table, table.getName());
   }

   /**
    * Get the name of the mv table.
    */
   public String getMVConditionTable(TableAssembly table) {
      if(mvCondsTable.containsKey(table)) {
         return mvCondsTable.get(table);
      }

      return null;
   }

   /**
    * Record the table mv conditions.
    */
   public void recordMVCondition(TableAssembly table) {
      mvConds.put(table.getName(), new MVCondition(table));
   }

   /**
    * Retrieve mv conditions.
    */
   public void retrieveMVCondition(TableAssembly table) {
      MVCondition cond = mvConds.get(table.getName());

      if(cond != null) {
         table.setMVUpdatePreConditionList(cond.preUpdate);
         table.setMVUpdatePostConditionList(cond.postUpdate);
         table.setMVDeletePreConditionList(cond.preDel);
         table.setMVDeletePostConditionList(cond.postDel);
      }
   }

   /**
    * Add mv selections.
    */
   private void addMVSelections(TableAssembly table, boolean update) {
      // if the mv update condition is moved to parent (it may still be
      // kept for the bound table for optimization, we should treat it
      // as non-existant for the purpose of analyzing MV
      if(update && MVTool.isMVConditionMoved(table)) {
         return;
      }

      ConditionListWrapper wrapper = update ? table.getMVUpdateConditionList() :
                                              table.getMVDeleteConditionList();

      if(wrapper == null || wrapper.isEmpty()) {
         return;
      }

      ConditionList conds = wrapper.getConditionList();
      int size = conds.getSize();

      // add selection column as well
      for(int j = 0; j < size; j += 2) {
         ConditionItem citem = conds.getConditionItem(j);
         XCondition cond = citem.getXCondition();
         int op = cond.getOperation();

         // update condition will be processed by sql
         if(!update && !XDimIndex.supportsOperation(op)) {
            throw new RuntimeException("Unsupported mv delete condition " +
               "operation found: " + cond + " in table assembly: " + table);
         }

         DataRef ref = citem.getAttribute();
         ColumnRef col = AbstractTransformer.getSelectionDataRef(ref, true);
         addMVSelectionColumn(table.getName(), col);
      }
   }

   /**
    * Create pseudo filter.
    */
   private void createPseudoFilter() {
      // mv selection not need to create, post selection must be existed
      for(String key : preselections.keySet()) {
         List<WSColumn> cols = preselections.get(key);

         for(int i = 0; i < cols.size(); i++) {
            WSColumn wscol = cols.get(i);
            String tname = wscol.getTableName();
            TableAssembly tbl = (TableAssembly) ws.getAssembly(tname);

            // the table might not exist
            if(tbl == null) {
               continue;
            }

            boolean post = !tbl.isPlain();
            ColumnSelection columns = tbl.getColumnSelection(post);
            DataRef ref = wscol.getDataRef();
            ref = AbstractTransformer.normalizeColumn(ref, columns);
            ConditionListWrapper conds0 = post ?
               tbl.getPostRuntimeConditionList() :
               tbl.getPreRuntimeConditionList();

            if(ref == null) {
               // runtime mode? do not care the other assemblies
               if(isRuntime()) {
                  continue;
               }
               else {
                  LOG.debug("Selection col not found: " + wscol +
                           ". Selection condition ignored");
                  continue;
               }
            }

            if(containsDataRef(conds0, ref)) {
               continue;
            }

            ConditionList conds2 = createPseudoConditions(ref);

            if(conds0 == null) {
               conds0 = conds2;
            }
            else {
               ArrayList clists = new ArrayList();
               clists.add(conds0);
               clists.add(conds2);
               conds0 = VSUtil.mergeConditionList(clists, JunctionOperator.AND);
            }

            ConditionList conds = conds0 == null ? null : conds0.getConditionList();

            if(post) {
               tbl.setPostRuntimeConditionList(conds);
            }
            else {
               tbl.setPreRuntimeConditionList(conds);
            }
         }
      }
   }

   /**
    * Clear runtime conditions on table.
    */
   public void clearPseudoFilter() {
      TableAssembly mvtable = (TableAssembly) ws.getAssembly(mvassembly);

      if(mvtable != null) {
         clearPseudoFilter(mvtable, mode);
      }
   }

   /**
    * Clear runtime conditions on table.
    */
   public static void clearPseudoFilter(TableAssembly table, int mode) {
      if(mode == RUN_MODE) {
         clearPseudoFilter(table.getPreRuntimeConditionList());
         clearPseudoFilter(table.getPostRuntimeConditionList());
      }
      else {
         table.setPreRuntimeConditionList(new ConditionList());
         table.setPostRuntimeConditionList(new ConditionList());
         table.setRankingRuntimeConditionList(new ConditionList());
      }

      if(!(table instanceof ComposedTableAssembly)) {
         return;
      }

      ComposedTableAssembly ctable = (ComposedTableAssembly) table;
      TableAssembly[] tables = ctable.getTableAssemblies(false);

      for(int i = 0; i < tables.length; i++) {
         clearPseudoFilter(tables[i], mode);
      }
   }

   /**
    * Clear the created pseudo conditions.
    */
   private static void clearPseudoFilter(ConditionListWrapper wrapper) {
      if(wrapper == null) {
         return;
      }

      HierarchyListModel model = new HierarchyListModel(wrapper.getConditionList());

      for(int i = model.getSize() - 1; i >= 0; i -= 2) {
         ConditionItem item = (ConditionItem) model.getElementAt(i);
         XCondition cond = item.getXCondition();

         if(cond.getOperation() == XCondition.PSEUDO) {
            model.removeConditionItem(i);
         }
      }
   }

   /**
    * Check if contains a data ref in a condition list.
    */
   private boolean containsDataRef(ConditionListWrapper wrapper, DataRef ref) {
      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();

      for(int i = 0; conds != null && i < conds.getSize(); i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         DataRef ref2 = citem.getAttribute();

         if(Tool.equals(ref, ref2)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Create a pseudo condition list.
    */
   private ConditionList createPseudoConditions(DataRef ref) {
      Condition cond = new Condition();
      cond.setOperation(Condition.PSEUDO);
      cond.addValue(null);
      cond.setType(ref.getDataType());
      ConditionItem icond = new ConditionItem(ref, cond, 0);
      ConditionList conds = new ConditionList();
      conds.append(icond);
      return conds;
   }

   /**
    * Add associated selection column.
    */
   private WSColumn addAssociatedSelectionColumn(String name, DataRef col) {
      return addColumn(name, col, assoselections);
   }

   /**
    * Add pre selection column.
    */
   private WSColumn addPreSelectionColumn(String name, DataRef col) {
      return addColumn(name, col, preselections);
   }

   /**
    * Add post selection column.
    */
   private WSColumn addPostSelectionColumn(String name, DataRef col) {
      return addColumn(name, col, postselections);
   }

   /**
    * Add ranking selection column.
    */
   private WSColumn addRankingSelectionColumn(String name, DataRef col) {
      return addColumn(name, col, rankingselections);
   }

   /**
    * Add mv selection column.
    */
   private WSColumn addMVSelectionColumn(String name, DataRef col) {
      return addColumn(name, col, mvselections);
   }

   /**
    * Add selection column.
    */
   private WSColumn addColumn(String name, DataRef col, Map<String, List<WSColumn>> selections) {
      if(col instanceof AggregateRef) {
         col = ((AggregateRef) col).getDataRef();
      }

      if(col instanceof GroupRef) {
         col = ((GroupRef) col).getDataRef();
      }

      WSColumn wcol = addSelectionColumn(name, col);
      List<WSColumn> list = getColumns(selections, name);

      if(!list.contains(wcol)) {
         list.add(wcol);
      }

      return wcol;
   }

   /**
    * Get columns.
    */
   private List<WSColumn> getColumns(Map<String, List<WSColumn>> selections, String name) {
      return selections.computeIfAbsent(name, k -> new ArrayList<>());
   }

   /**
    * Add selection column.
    */
   private WSColumn addSelectionColumn(String name, DataRef col) {
      List<WSColumn> list = getSelectionColumns(name, false);
      WSColumn column = new WSColumn(name, col);
      int index = list.indexOf(column);

      if(index < 0) {
         list.add(column);
      }
      else {
         column = list.get(index);
      }

      return column;
   }

   /**
    * Add a transformation record.
    */
   public void addInfo(TransformationInfo info) {
      if(trans == null) {
         trans = new ArrayList<>();
      }

      if(!trans.contains(info)) {
         trans.add(info);
      }
   }

   /**
    * Add warning.
    */
   public void addWarning(TransformationInfo info) {
      if(warnings == null) {
         warnings = new ArrayList<>();
      }

      if(!warnings.contains(info)) {
         warnings.add(info);
      }
   }

   /**
    * Get warnings.
    */
   public List getWarnings(String vassembly) {
      return warningsMap.get(vassembly);
   }

   /**
    * Add user information.
    */
   public void addUserInfo(UserInfo info) {
      if(!userInfo.contains(info)) {
         userInfo.add(info);
      }
   }

   /**
    * Get user information.
    */
   public List<UserInfo> getUserInfo() {
      return userInfo;
   }

   /**
    * Add a transformation fault. A fault is a condition that prevents a
    * successful transformation.
    */
   public void addFault(TransformationFault fault) {
      if(faults == null) {
         faults = new ArrayList<>();
      }

      if(!faults.contains(fault)) {
         faults.add(fault);
      }
   }

   /**
    * Add a not hit mv hint.
    */
   public void addNotHitMVHint(String assembly, String... reasons) {
      mayNotHitMVMap.computeIfAbsent(assembly, k -> new ArrayList<>())
         .addAll(Arrays.asList(reasons));
   }

   /**
    * Get not hit mv infos.
    */
   public Map<String, List<String>> getNotHitMVHints() {
      return mayNotHitMVMap;
   }

   /**
    * Get all faults for the vs assembly.
    */
   public List getFaults(String vassembly) {
      return faultsMap.get(vassembly);
   }

   /**
    * Check if does not contain any fault.
    */
   public boolean isEmptyFault() {
      if(faults == null) {
         faults = new ArrayList<>();
      }

      return faults.isEmpty();
   }

   /**
    * Add a result of creation materialization.
    */
   public void addResult(boolean success) {
      int result = mvResultMap.get(vsassembly);
      result = success ? result | SUCCESS : result | FAILURE;
      mvResultMap.put(vsassembly, result);
   }

   /**
    * Get transformation info.
    */
   public String getInfo() {
      StringBuilder str = new StringBuilder();
      Iterator iterator = faultsMap.keySet().iterator();
      int result = 0; // the definitive result of the materialization

      while(iterator.hasNext()) {
         String vsassembly = (String) iterator.next();
         String dataBlocks = dataBlocksMap.get(vsassembly);
         dataBlocks = AbstractTransformer.normalizeCubeName(dataBlocks);
         result = mvResultMap.get(vsassembly) | result;
         str.append("Data Block: " + dataBlocks + "\n");
         str.append("Included ViewSheet Assemblies: " + vsassembly + "\n");
         str.append("    Transformations:\n");
         List trans = transMap.get(vsassembly);
         Collections.sort(trans);

         for(int i = 0; i < trans.size(); i++) {
            TransformationInfo info = (TransformationInfo) trans.get(i);
            str.append("        " + info.getDescription() + "\n");
         }

         List warnings = warningsMap.get(vsassembly);

         if(warnings.size() > 0) {
            str.append("    Warnings:\n");
            Collections.sort(warnings);

            for(int i = 0; i < warnings.size(); i++) {
               TransformationInfo info = (TransformationInfo) warnings.get(i);
               str.append("        " + info.getDescription() + "\n");
            }
         }

         List faults = faultsMap.get(vsassembly);

         if(faults.size() == 0) {
            str.append("    Faults: None\n");
         }
         else {
            str.append("    Faults:\n");

            for(int i = 0; i < faults.size(); i++) {
               TransformationFault fault = (TransformationFault) faults.get(i);
               str.append("          Fault: " + fault.getDescription() + "\n");
               str.append("          Reason: " + fault.getReason() + "\n");
            }
         }

         str.append("\n");
      }

      switch(result) {
      case FAILURE:
         str.append("Final Result: Materialization Failure");
         break;
      case SUCCESS:
         str.append("Final Result: Materialization Successful");
         break;
      case PARTIAL:
         str.append("Final Result: Partial Materialization");
         break;
      default:
         break;
      }

      return str.toString();
   }

   /**
    * Get the vs assembly.
    */
   public String getVSAssembly() {
      return vsassembly;
   }

   /**
    * Get the table assembly.
    */
   public String getTableAssembly() {
      return tassembly;
   }

   /**
    * Get the table assembly as the transformation result.
    * @param cfilter true to clear pseudo filter.
    */
   public TableAssembly getTable(boolean cfilter) {
      TableAssembly mvtable = (TableAssembly) ws.getAssembly(mvassembly);

      if(mvtable != null && cfilter) {
         clearPseudoFilter(mvtable, mode);
      }

      TableAssembly table = (TableAssembly) ws.getAssembly(tassembly);
      return table;
   }

   /**
    * Get the table assembly for the specified table name.
    */
   public TableAssembly getTable(String tname) {
      return tname == null ? null : (TableAssembly) ws.getAssembly(tname);
   }

   /**
    * Get the table this MV table was originally bound to.
    */
   public String getBoundTable() {
      return boundtable;
   }

   /**
    * Get the mv assembly for analyze mode, may be null.
    */
   public String getAnalyzeMVAssembly() {
      return analyzeMVAssembly;
   }

   /**
    * Set the mv assembly for analyze mode.
    */
   public void setAnalyzeMVAssembly(String analyzeMVAssembly) {
      this.analyzeMVAssembly = analyzeMVAssembly;
   }

   /**
    * Get the mv assembly.
    */
   public String getMVAssembly() {
      return mvassembly;
   }

   /**
    * Set the mv assembly.
    */
   public void setMVAssembly(String nassembly) {
      mvassembly = nassembly;
   }

   /**
    * Get the mv table assembly.
    */
   public TableAssembly getMVTable() {
      return mvassembly == null ? null : (TableAssembly) ws.getAssembly(mvassembly);
   }

   /**
    * Reset the transformation descriptor with new viewsheet assembly and its
    * table assembly.
    * @param vsassembly the specified viewsheet assembly.
    * @param tassembly the specified table assembly created for this viewsheet
    * assembly.
    * @param mvassembly the specified mv assembly.
    */
   public void reset(String vsassembly, String tassembly, String mvassembly, String boundtable) {
      this.vsassembly = vsassembly;
      this.tassembly = tassembly;
      this.mvassembly = mvassembly;
      this.boundtable = boundtable;

      this.trans = new ArrayList();
      this.warnings = new ArrayList();
      this.faults = new ArrayList();

      mvResultMap.put(this.vsassembly, 0);
      dataBlocksMap.put(this.vsassembly, this.tassembly);

      faultsMap.put(this.vsassembly, this.faults);
      transMap.put(this.vsassembly, this.trans);
      warningsMap.put(this.vsassembly, this.warnings);
   }

   /**
    * Clear out the info from the descritor for an unacceptable viewsheet
    * assembly, so that the optimize plan won't contain any entry about it.
    * For example, for selection elements bound to embedded tables there is no
    * need to add any entry in the optimize plan, as it is pretty obvious they
    * have hard coded values.
    * @param vsassembly the specified viewsheet assembly.
    */
   public void clear(String vsassembly) {
      mvResultMap.remove(vsassembly);
      dataBlocksMap.remove(vsassembly);
      faultsMap.remove(vsassembly);
      transMap.remove(vsassembly);
      warningsMap.remove(vsassembly);
   }

   /**
    * Get viewsheet.
    */
   public Viewsheet getViewsheet() {
      return vs;
   }

   /**
    * Get the base worksheet.
    */
   public Worksheet getWorksheet() {
      return ws;
   }

   /**
    * Get the selection columns for the specified table assembly.
    * @param name the specified table assembly name.
    */
   public List<WSColumn> getSelectionColumns(String name, boolean recursive) {
      if(!recursive) {
         return getColumns(selections, name);
      }

      TableAssembly table = (TableAssembly) ws.getAssembly(name);
      Assembly[] darr = AssetUtil.getDependedAssemblies(ws, table, true);
      List<WSColumn> cols = new ArrayList<>();

      for(int i = 0; i < darr.length; i++) {
         if(!(darr[i] instanceof TableAssembly)) {
            continue;
         }

         List cols2 = getSelectionColumns(darr[i].getName(), false);
         cols.addAll(cols2);
      }

      return cols;
   }

   /**
    * Get associated selection columns, including measure columns.
    */
   public List<WSColumn> getAssociatedSelectionColumns(String name) {
      return getColumns(assoselections, name);
   }

   /**
    * Get pre selection columns.
    */
   public List<WSColumn> getPreSelectionColumns(String name) {
      return getColumns(preselections, name);
   }

   /**
    * Check if only have pre selections defined.
    */
   public boolean preSelectionDefineOnly(String name) {
      return getPostSelectionColumns(name).size() <= 0 &&
             getRankingSelectionColumns(name).size() <= 0 &&
             getMVSelectionColumns(name).size() <= 0 &&
             getPreSelectionColumns(name).size() > 0;
   }

   /**
    * Check if the column will be post processed after pre condition.
    */
   public boolean isPostColumn(String name, WSColumn column) {
      return !getPreSelectionColumns(name).contains(column);
   }

   /**
    * Get post selection columns.
    */
   public List<WSColumn> getPostSelectionColumns(String name) {
      return getColumns(postselections, name);
   }

   /**
    * Check if only have psot selections defined.
    */
   public boolean postSelectionDefineOnly(String name) {
      return getPreSelectionColumns(name).size() <= 0 &&
             getRankingSelectionColumns(name).size() <= 0 &&
             getMVSelectionColumns(name).size() <= 0 &&
             getPostSelectionColumns(name).size() > 0;
   }

   /**
    * Get ranking selection columns.
    */
   public List<WSColumn> getRankingSelectionColumns(String name) {
      return getColumns(rankingselections, name);
   }

   /**
    * Check if only have ranking selections defined.
    */
   public boolean rankingSelectionDefineOnly(String name) {
      return getPreSelectionColumns(name).size() <= 0 &&
             getPostSelectionColumns(name).size() <= 0 &&
             getMVSelectionColumns(name).size() <= 0 &&
             getRankingSelectionColumns(name).size() > 0;
   }

   /**
    * Check if the column is used in ranking.
    */
   public boolean isRankingColumn(String name, WSColumn col) {
      List cols = getRankingSelectionColumns(name);
      return cols.contains(col);
   }

   /**
    * Get mv selection columns.
    */
   public List<WSColumn> getMVSelectionColumns(String name) {
      return getColumns(mvselections, name);
   }

   /**
    * Check if only have mv selections defined.
    */
   public boolean mvSelectionDefineOnly(String name) {
      return getPreSelectionColumns(name).size() <= 0 &&
             getPostSelectionColumns(name).size() <= 0 &&
             getRankingSelectionColumns(name).size() <= 0 &&
             getMVSelectionColumns(name).size() > 0;
   }

   /**
    * Check if only have vs condition defined.
    */
   public boolean vsConditionDefineOnly(String name) {
      return vsselections.contains(name);
   }

   /**
    * Get all the selection columns.
    */
   public boolean hasSelection(String name) {
      return selections.containsKey(name);
   }

   /**
    * Get the selection column if any for the specified worksheet table column.
    */
   public WSColumn getSelectionColumn(String tname, ColumnRef column) {
      List<WSColumn> sel = selections.get(tname);

      if(sel == null) {
         return null;
      }

      column = AbstractTransformer.getSelectionDataRef(column, true);
      WSColumn wscolumn = new WSColumn(tname, column);
      int index = sel.indexOf(wscolumn);
      return index < 0 ? null : sel.get(index);
   }

   /**
    * Rename selection columns.
    */
   public void renameSelectionColumns(String oname, String nname) {
      renameSelectionColumns(selections, oname, nname);
      renameSelectionColumns(preselections, oname, nname);
      renameSelectionColumns(postselections, oname, nname);
      renameSelectionColumns(rankingselections, oname, nname);
      renameSelectionColumns(mvselections, oname, nname);
   }

   /**
    * Rename selection column.
    */
   private void renameSelectionColumns(Map<String, List<WSColumn>> selections,
                                       String oname, String nname)
   {
      List<WSColumn> sel = selections.get(oname);

      if(sel != null) {
         List<WSColumn> nsel = new ArrayList<>();

         for(int i = 0; i < sel.size(); i++) {
            WSColumn column = sel.get(i);
            WSColumn ncolumn = new WSColumn(nname, column.getDataRef());
            ncolumn.setRangeInfo(column.getRangeInfo());
            nsel.add(ncolumn);
         }

         selections.put(nname, nsel);
         selections.remove(oname);
      }
   }

   /**
    * Get the range info for the specified column.
    */
   public RangeInfo getRangeInfo(WSColumn column) {
      List<WSColumn> list = getSelectionColumns(column.getTableName(), false);
      int index = list.indexOf(column);

      if(index < 0) {
         return null;
      }
      else {
         column = list.get(index);
         return column.getRangeInfo();
      }
   }

   /**
    * Clear the range info for the specified column.
    */
   public void clearRangeInfo(WSColumn column) {
      List<WSColumn> list = getSelectionColumns(column.getTableName(), false);
      int index = list.indexOf(column);

      if(index >= 0) {
         column = list.get(index);
         column.setRangeInfo(null);
      }
   }

   /**
    * Check if selection nested. Filtering like zoom/brush is not considered.
    */
   public boolean isSelectionNested(String name) {
      return isSelectionNested(name, false);
   }

   /**
    * Check if selection nested.
    */
   private boolean isSelectionNested(String name, boolean pselection) {
      SNKey key = new SNKey(name, pselection);
      Boolean result = snmap.get(key);

      if(result == null) {
         result = isSelectionNested0(name, pselection);
         snmap.put(key, result);
      }

      return result.booleanValue();
   }

   /**
    * Check if selection nested.
    * @param pselection true if selection on parent table.
    * @return true if the selection exists in a node and its parent nodes.
    */
   private boolean isSelectionNested0(String name, boolean pselection) {
      TableAssembly table = (TableAssembly) ws.getAssembly(name);
      boolean selection = isSelectionOnTable(name);

      if(selection && pselection) {
         return true;
      }

      pselection = pselection || selection;
      Assembly[] darr = AssetUtil.getDependedAssemblies(ws, table, false);

      for(int i = 0; i < darr.length; i++) {
         if(!(darr[i] instanceof TableAssembly)) {
            continue;
         }

         if(isSelectionNested(darr[i].getName(), pselection)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if selection is defined on children, sub query included.
    */
   public boolean isSelectionOnChildren(String name) {
      TableAssembly table = (TableAssembly) ws.getAssembly(name);
      Assembly[] darr = AssetUtil.getDependedAssemblies(ws, table, false);

      for(int i = 0; i < darr.length; i++) {
         if(!(darr[i] instanceof TableAssembly)) {
            continue;
         }

         if(isSelectionOnTable(darr[i].getName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if selection is defined on children, sub query excluded.
    */
   public boolean isSelectionOnChildren(XNode qtree) {
      for(int i = 0; i < qtree.getChildCount(); i++) {
         XNode child = qtree.getChild(i);

         if(isSelectionOnTable(child.getName())) {
            return true;
         }

         if(isSelectionOnChildren(child)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if selection is defined on the specified query.
    */
   public boolean isSelectionOnTable(String table) {
      List list = getSelectionColumns(table, false);
      return list != null && list.size() > 0;
   }

   /**
    * Check if the table has input assembly bound to, which will change through input assembly
    * by user.
    */
   public boolean isInputDynamicTable(String table) {
      return dynamicTables.contains(table);
   }

   /**
    * Check if any table is bound to input assembly.
    */
   public boolean hasDynamicTable() {
      return !dynamicTables.isEmpty();
   }

   /**
    * Get the mode of this transformer.
    */
   public int getMode() {
      return mode;
   }

   /**
    * Check if this transformer is for runtime transformation.
    */
   public boolean isRuntime() {
      return (mode & RUN_MODE) == RUN_MODE;
   }

   /**
    * Check if this transformer is for analytic transformation.
    */
   public boolean isAnalytic() {
      return (mode & ANALYTIC_MODE) == ANALYTIC_MODE;
   }

   /**
    * Just clone selections.
    */
   public TransformationDescriptor cloneSelections() {
      TransformationDescriptor desc = new TransformationDescriptor();
      Iterator<String> tables = selections.keySet().iterator();

      while(tables.hasNext()) {
         String table = tables.next();
         List<WSColumn> vals = selections.get(table);
         List<WSColumn> vals2 = new ArrayList<>();

         if(vals != null) {
            for(WSColumn obj : vals) {
               obj = (WSColumn) obj.clone();
               vals2.add(obj);
            }
         }

         desc.selections.put(table, vals2);
      }

      return desc;
   }

   /**
    * Print the table assembly.
    */
   public void print() {
      if(!"true".equals(SreeEnv.getProperty("mv_debug"))) {
         return;
      }

      TableAssembly table = getTable(false);

      if(table == null) {
         LOG.debug("Null table");
      }
      else {
         StringBuilder sb = new StringBuilder();
         table.print(0, sb);
         LOG.debug(sb.toString());
      }
   }

   /**
    * Key to cache the result of selection nested. For a complex worksheet,
    * it might be very expensive to get result from worksheet directly.
    */
   private static class SNKey {
      public SNKey(String name, boolean selection) {
         this.name = name;
         this.selection = selection;
      }

      public boolean equals(Object obj) {
         SNKey key = (SNKey) obj;
         return name.equals(key.name) && selection == key.selection;
      }

      public int hashCode() {
         return name.hashCode();
      }

      private String name;
      private boolean selection;
      private int hash;
   }

   private static class MVCondition {
      public MVCondition(TableAssembly table) {
         preUpdate = clone(table.getMVUpdatePreConditionList());
         postUpdate = clone(table.getMVUpdatePostConditionList());
         preDel = clone(table.getMVDeletePreConditionList());
         postDel = clone(table.getMVDeletePostConditionList());
      }

      private ConditionListWrapper clone(ConditionListWrapper wrapper) {
         if(wrapper != null) {
            wrapper = (ConditionListWrapper) wrapper.clone();
         }

         return wrapper;
      }

      private ConditionListWrapper preUpdate;
      private ConditionListWrapper postUpdate;
      private ConditionListWrapper preDel;
      private ConditionListWrapper postDel;
   }

   // @by davyc, debug
   public static void printCondition(TableAssembly t) {
      printCondition0(t, "   ");
   }

   private static void printCondition0(TableAssembly t, String tag) {
      printCondition(t, tag);

      if(t instanceof ComposedTableAssembly) {
         TableAssembly[] tables = ((ComposedTableAssembly) t).getTableAssemblies(false);

         for(TableAssembly temp : tables) {
            printCondition0(temp, tag + "   ");
         }
      }
   }

   public static void printCondition(TableAssembly t, String tag) {
      System.err.println(tag + t + " = " + t.getAggregateInfo());
      System.err.println(tag + "pre cond        : " + t.getPreConditionList());
      System.err.println(tag + "preruntime cond : " + t.getPreRuntimeConditionList());
      System.err.println(tag + "post cond       : " + t.getPostConditionList());
      System.err.println(tag + "postruntime cond: " + t.getPostRuntimeConditionList());
      System.err.println(tag + "rank cond       : " + t.getRankingConditionList());
      System.err.println(tag + "rankruntime cond       : " + t.getRankingRuntimeConditionList());
      System.err.println(tag + "mv update pre   : " + t.getMVUpdatePreConditionList());
      System.err.println(tag + "mv update post  : " + t.getMVUpdatePostConditionList());
      System.err.println(tag + "mv delete pre   : " + t.getMVDeletePreConditionList());
      System.err.println(tag + "mv delete post  : " + t.getMVDeletePostConditionList());
   }

   private static final Logger LOG = LoggerFactory.getLogger(TransformationDescriptor.class);
   private String vsassembly;
   private String tassembly;
   private String analyzeMVAssembly;
   private String mvassembly;
   private String boundtable;
   private Viewsheet vs;
   private Worksheet ws;
   private int mode;

   private Set<String> vsselections = new HashSet<>();
   private Map<String, List<WSColumn>> selections = new HashMap<>();
   private Map<String, List<WSColumn>> preselections = new HashMap<>();
   private Map<String, List<WSColumn>> postselections = new HashMap<>();
   private Map<String, List<WSColumn>> rankingselections = new HashMap<>();
   private Map<String, List<WSColumn>> mvselections = new HashMap<>();
   private Map<String, List<WSColumn>> assoselections = new HashMap<>();
   private Map<String, MVCondition> mvConds = new HashMap<>();
   private Map<TableAssembly, String> mvCondsTable = new HashMap<>();
   private Set<String> dynamicTables = new HashSet<>();

   private Map<String, List> transMap = new HashMap<>();
   private Map<String, List> warningsMap = new HashMap<>();
   private Map<String, List> faultsMap = new HashMap<>();
   private Map<String, List<String>> mayNotHitMVMap = new HashMap<>();
   private Map<String, String> dataBlocksMap = new HashMap<>();
   private Map<String, Integer> mvResultMap = new HashMap<>();
   private List<UserInfo> userInfo = new ArrayList<>();
   private Map<SNKey, Boolean> snmap = new HashMap<>();

   private List<TransformationInfo> trans;
   private List<TransformationInfo> warnings;
   private List<TransformationFault> faults;
}
