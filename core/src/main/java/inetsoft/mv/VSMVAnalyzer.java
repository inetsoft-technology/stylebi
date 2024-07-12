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
package inetsoft.mv;

import inetsoft.mv.trans.*;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.report.composition.execution.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionBaseVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * VSMVAnalyzer, analyzes a viewsheet and generate mv candidates.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class VSMVAnalyzer implements MVAnalyzer {
   /**
    * Create an instance of MVDef.
    */
   public VSMVAnalyzer(String vsId, Viewsheet vs, Identity user, ViewsheetSandbox box,
                       boolean bypass)
   {
      super();

      this.vsId = vsId;
      this.vs = vs;
      this.user = user;
      this.box = box;
      this.bypass = bypass;
   }

   /**
    * Get the viewsheet to be analyzed.
    */
   public String getViewsheet() {
      return vsId;
   }

   public String getVsPath() {
      return AssetEntry.createAssetEntry(vsId).getPath();
   }

   /**
    * Get the owner of this MVAnalyzer.
    */
   public Identity getUser() {
      return user;
   }

   /**
    * Check if there exist not hiv mv info.
    */
   @Override
   public boolean isNotHitMVWarned() {
      return desc != null && desc.getNotHitMVHints().size() > 0;
   }

   /**
    * Get the transformation descriptor.
    */
   @Override
   public TransformationDescriptor getDescriptor() {
      if(desc == null) {
         int mode = TransformationDescriptor.ANALYTIC_MODE;
         desc = new TransformationDescriptor(vs, mode);
      }

      return desc;
   }

   /**
    * Analyze this viewsheet.
    */
   @Override
   public MVDef[] analyze() throws Exception {
      synchronized(vs) {
         // reset cache
         tmap.clear();
         desc = null;
         prepare();

         List<MVDefInfo> defs = new ArrayList<>();
         int mode = TransformationDescriptor.ANALYTIC_MODE;
         Worksheet ws = vs.getBaseWorksheet();
         Map<String, TableInfo> process = new HashMap<>(tmap);

         while(process.size() > 0) {
            for(Map.Entry<String, TableInfo> entry : process.entrySet()) {
               final String tname = entry.getKey();
               TableInfo info = entry.getValue();

               if(info.calcs != null && !info.calcs.isEmpty()) {
                  TableAssembly table = (TableAssembly) ws.getAssembly(tname);
                  TableAssembly stable = (TableAssembly)
                     ws.getAssembly(Assembly.SELECTION + tname);

                  appendDetailCalc(table, info.calcs);
                  appendDetailCalc(stable, info.calcs);
               }
            }

            ctmap = new HashMap<>();

            for(Map.Entry<String, TableInfo> entry : process.entrySet()) {
               final String tname = entry.getKey();
               TableInfo info = entry.getValue();
               desc = desc == null ? new TransformationDescriptor(vs, mode) :
                  new TransformationDescriptor(desc);
               String vassembly = info.getVSAssembly();
               desc.reset(vassembly, tname, tname, tname);

               if(!isAcceptable(ws, info, desc)) {
                  // desc.clear(vassembly);
                  continue;
               }

               SelectionUpTransformer transformer = new SelectionUpTransformer();
               boolean success = transformer.transform(desc);
               boolean rootMirror = transformer.getRootMirror() != null;

               // @by arlinex, the detail fault will handle in the
               // SelectionUpTransformer, if can't find any fault but transformation
               // failed, add this fault
               if(!success && desc.isEmptyFault()) {
                  desc.addInfo(TransformationInfo.selectionUpToParent(tname));
                  desc.addFault(TransformationFault.selectionUp(tname));
               }

               TableAssembly table = desc.getMVTable();
               table.resetColumnSelection();
               TableAssembly otable = desc.getTable(false);
               // prepare all sub table infomations, to make sure
               // subquery table can reuse mv(isSelectionOnly)
               addSubQueryTable(desc.getTable(false), true);
               boolean created = createMV(table, otable, desc, vassembly, tname,
                                          true, defs, info, rootMirror, success, true);
               desc.addResult(created);
            }

            process = ctmap;
         }

         MVDef[] arr = new MVDef[defs.size()];
         TableInfo[] infos = new TableInfo[defs.size()];
         // @by stephenwebster, For Bug #9587
         // Add support for getting variables from current viewsheet sandbox
         // into MV creation process.  The logical solution seemed to track
         // this in the MVDef as it is available throughout the process.
         VariableTable allVariables = box.getAllVariables();

         for(int i = 0; i < arr.length; i++) {
            if(!defs.get(i).isCorrelated()) {
               arr[i] = defs.get(i).createDef();
               arr[i].setRuntimeVariables(allVariables);
               infos[i] = defs.get(i).info;
            }
            else {
               throw new Exception(catalog.getString(
                  "can not support condition defined on the subquery"));
            }
         }

         checkCombinable(arr, infos);

         return arr;
      }
   }

   /**
    * Create sub mv.
    */
   private boolean createMV(TableAssembly table, TableAssembly otable,
                            TransformationDescriptor desc,
                            String vassembly, String tname,
                            boolean root, List<MVDefInfo> defs, TableInfo info,
                            boolean rootMirror, boolean success, boolean association)
   {
      desc.retrieveMVCondition(table);

      if(!isSelectionHidden(table, vassembly, root, success) &&
         !AbstractTransformer.containsSubSelection(desc, table) &&
         !AbstractTransformer.containsGroupRanking(table) &&
         !(AbstractTransformer.containsRanking(table) &&
         !desc.getSelectionColumns(table.getName(), false).isEmpty()) &&
         !useDynamicTable(table, desc) &&
         isSuitableTable(table, desc))
      {
         AggregateInfo ainfo = table.getAggregateInfo();

         // only create mv if aggregate is defined, and no selection
         // on aggregate, and there is no percentage. Why no percentage?
         // Percentage is always based on all rows. However, when user
         // opens this viewsheet, and selects some items, percentage is
         // based on rows match condition
         if(root || ainfo.isEmpty() ||
            !ainfo.isEmpty() && !ainfo.containsPercentage() &&
            !AbstractTransformer.isSelectionOnAggregate(table, desc))
         {
            addMVConditionInfo(desc, table);
            addSubQueryTable(table, false);
            MVDefInfo def = new MVDefInfo(vsId, table, tname, desc,
                                          root, rootMirror, info, bypass);
            defs.add(def);

            if(otable != table) {
               // creating MV if there is sql formula could potentially produce
               // wrong result (when the result of js is different from sql, e.g.
               // null * 2 is null in sql but 0 in js), we should consider not
               // allow it?!
               checkSQLFormula(otable, table, desc);
            }

            if(association) {
               TableMetaDataTransformer trans = new TableMetaDataTransformer();

               if(trans.transform(desc)) {
                  TableAssembly metadataTbl = trans.getMetaDataTable();

                  // ignore empty table (46707).
                  if(!metadataTbl.getColumnSelection(false).isEmpty()) {
                     TableInfo info2 = new TableInfo(metadataTbl.getName());

                     defs.add(new MVDefInfo(vsId, metadataTbl, metadataTbl.getName(),
                                            desc, root, rootMirror, info2, bypass));
                     tmap.put(metadataTbl.getName(), info2);
                  }
               }
            }

            return true;
         }
      }

      if(!(table instanceof ComposedTableAssembly)) {
         return false;
      }

      // if MV not created for table, try creating MV for sub-tables
      // and MV can still be used as source for post processing
      ComposedTableAssembly ctable = (ComposedTableAssembly) table;
      TableAssembly[] tables = ctable.getTableAssemblies(false);
      boolean created = false;

      for(final TableAssembly tableAssembly : tables) {
         boolean createAssociation = isAssociationCompatible(ctable, tableAssembly);
         boolean result = createMV(tableAssembly, otable, desc, vassembly, tname,
                                   false, defs, info, rootMirror, success, createAssociation);
         desc.addResult(result);
         created = result || created;
      }

      return created;
   }

   private static boolean isAssociationCompatible(TableAssembly parent, TableAssembly child) {
      // association MV only makes sense for non-sub MV since it's
      // a direct cache of the current table, and it won't be used
      // for sub-MV processing (unless it's a direct mirror (46484)).
      if(!(parent instanceof MirrorAssembly) || parent.getMaxRows() > 0) {
         return false;
      }

      // check if the columns in sub is same as parent. only create association MV if they
      // are the same.

      ColumnSelection parentCols = parent.getColumnSelection(true);
      ColumnSelection childCols = child.getColumnSelection(true);

      if(parentCols.getAttributeCount() == childCols.getAttributeCount()) {
         return true;
      }

      // <s>should only create when completely identical (46755, 46757, 46758, 46782).</s>
      // the above statement modified, see below:

      // count number of columns that are not aggregate calc fields. checked the 4 bugs
      // in the previous comments and they all worked without the check for identical
      // columns. instread of completely remove the check, limited the change to
      // ignore calc fields. since calc fields are not used in MV so this should be safe.
      // (53263, 54063)
      long parentNoCalc = parentCols.stream()
         .filter(a -> !(a instanceof CalculateRef))
         .count();

      return parentNoCalc == childCols.getAttributeCount();
   }

   // check if this table uses a dynamic table (table that is bound to input assemblies).
   private boolean useDynamicTable(TableAssembly table, TransformationDescriptor desc) {
      if(!desc.hasDynamicTable()) {
         return false;
      }

      Set<AssemblyRef> deps = new HashSet<>();
      table.getDependeds(deps);
      return deps.stream().anyMatch(a -> desc.isInputDynamicTable(a.getEntry().getName()));
   }

   /**
    * Add subquery table to map.
    */
   private void addSubQueryTable(TableAssembly table, boolean analyze) {
      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();
      addSubQueryTable(wrapper, analyze);
      wrapper = table.getPostRuntimeConditionList();
      addSubQueryTable(wrapper, analyze);

      if(!analyze) {
         return;
      }

      if(table instanceof ComposedTableAssembly) {
         ComposedTableAssembly composed = (ComposedTableAssembly) table;
         TableAssembly[] subs = composed.getTableAssemblies(false);

         for(TableAssembly sub : subs) {
            addSubQueryTable(sub, true);
         }
      }
   }

   private void addSubQueryTable(ConditionListWrapper wrapper, boolean analyze) {
      Worksheet ws = vs == null ? null : vs.getBaseWorksheet();

      if(wrapper == null || wrapper.isEmpty() || vs == null || ws == null) {
         return;
      }

      ConditionList conds = wrapper.getConditionList();

      for(int i = 0; conds != null && i < conds.getSize(); i += 2) {
         Object cond = conds.getXCondition(i);

         if(!(cond instanceof AssetCondition)) {
            continue;
         }

         AssetCondition acnd = (AssetCondition) cond;
         SubQueryValue val = acnd.getSubQueryValue();

         if(val == null) {
            continue;
         }

         String query = val.getQuery();
         TableAssembly stable = query == null ?
            null : (TableAssembly) ws.getAssembly(query);

         if(stable == null) {
            continue;
         }

         String name = stable.getName();
         subqueries.add(name);

         if(!analyze && !tmap.containsKey(name)) {
            TableInfo tinfo = new TableInfo(name) {
               @Override
               public String getVSAssembly() {
                  return SUB_QUERY;
               }

               @Override
               public boolean isSelectionOnly() {
                  return false;
               }
            };

            tinfo.initDetailCalc(vs.getCalcFields(name));
            ctmap.put(name, tinfo);
            tmap.put(name, tinfo);
         }
      }
   }

   /**
    * Add mv condition info.
    */
   private void addMVConditionInfo(TransformationDescriptor desc,
                                   TableAssembly mvtable)
   {
      List<TableAssembly> chain = new ArrayList<>();
      TableAssembly table = desc.getTable(false);

      while(table != null && table != mvtable) {
         chain.add(table);
         table = getSubTable(table, mvtable);
      }

      for(TableAssembly tbl : chain) {
         String name = desc.getMVConditionTable(tbl);

         if(name != null) {
            desc.addWarning(TransformationInfo.mvConditionUseless(name));
         }
      }
   }

   /**
    * Get sub table.
    */
   private TableAssembly getSubTable(TableAssembly ptbl, TableAssembly tbl) {
      if(ptbl == tbl) {
         return null;
      }

      if(!(ptbl instanceof ComposedTableAssembly)) {
         return null;
      }

      ComposedTableAssembly composed = (ComposedTableAssembly) ptbl;
      TableAssembly[] tables = composed.getTableAssemblies(false);

      for(TableAssembly table : tables) {
         if(isParent(table, tbl)) {
            return table;
         }
      }

      return null;
   }

   /**
    * Check if ptbl is parent of table tbl.
    */
   private boolean isParent(TableAssembly ptbl, TableAssembly tbl) {
      if(ptbl == tbl) {
         return true;
      }

      if(!(ptbl instanceof ComposedTableAssembly)) {
         return false;
      }

      ComposedTableAssembly composed = (ComposedTableAssembly) ptbl;
      TableAssembly[] tables = composed.getTableAssemblies(false);

      for(TableAssembly table : tables) {
         if(isParent(table, tbl)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if sql formula exists.
    * @return true if the table contains SQL formula.
    */
   private boolean checkSQLFormula(TableAssembly table, TableAssembly ctable,
                                   TransformationDescriptor desc)
   {
      boolean found = false;

      if(table == ctable || table.getName().equals(ctable.getName())) {
         return found;
      }

      found = checkSQLFormula0(table, desc);

      if(table instanceof ComposedTableAssembly) {
         ComposedTableAssembly composed = (ComposedTableAssembly) table;
         TableAssembly[] tables = composed.getTableAssemblies(false);

         for(TableAssembly t : tables) {
            if(t == ctable || t.getName().equals(ctable.getName())) {
               return found;
            }
         }

         for(TableAssembly t : tables) {
            found = checkSQLFormula(t, ctable, desc) || found;
         }
      }

      return found;
   }

   private boolean checkSQLFormula0(TableAssembly table, TransformationDescriptor desc) {
      ColumnSelection cols = table.getColumnSelection(true);
      int cnt = cols.getAttributeCount();
      boolean found = false;

      for(int i = 0; i < cnt; i++) {
         ColumnRef col = (ColumnRef) cols.getAttribute(i);
         DataRef ref = col.getDataRef();

         if(ref instanceof ExpressionRef) {
            ExpressionRef eref = (ExpressionRef) ref;

            if((col.isSQL() || eref.isSQL()) && !eref.isVirtual() &&
               eref.isExpressionEditable())
            {
               desc.addWarning(TransformationInfo.containsSQLFormula(table, col));
               String msg = catalog.getString("mv.sql.formula.up",
                  VSUtil.stripOuter(table.getName()), col.getName());
               UserInfo info = new UserInfo(getVsPath(), table.getName(), msg);
               desc.addUserInfo(info);
               found = true;
            }
         }
      }

      return found;
   }

   /**
    * Shrink table assembly to get thin mv.
    */
   private boolean shrinkTable(TableAssembly table, TableInfo info) {
      if(!info.isSelectionOnly() || vs.getViewsheetInfo().isFullData()) {
         return false;
      }

      AggregateInfo ainfo = table.getAggregateInfo();

      if(ainfo.isCrosstab()) {
         return false;
      }

      ColumnSelection cols = table.getColumnSelection(false);
      ColumnSelection pcols = table.getColumnSelection(true);
      int cnt = cols.getAttributeCount();
      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();
      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();

      ConditionListWrapper mwrapper = table.getMVConditionList();
      ConditionList mvconds = mwrapper == null ? null : mwrapper.getConditionList();

      for(int i = 0; i < cnt; i++) {
         ColumnRef col = (ColumnRef) cols.getAttribute(i);
         ColumnRef vcol = VSUtil.getVSColumnRef(col);

         if(info.isSelectionBound(col) || info.isSelectionBound(vcol) ||
            containsSelection(conds, col) || containsSelection(mvconds, col))
         {
            continue;
         }

         // hide the useless column
         col.setVisible(false);
         int index = pcols.indexOfAttribute(col);

         if(index >= 0) {
            pcols.removeAttribute(index);
         }
      }

      return true;
   }

   /**
    * Get logical model bound tables.
    */
   public static String[] getLMBoundTables(Viewsheet vs, ViewsheetSandbox box) {
      Worksheet ws = vs.getBaseWorksheet();
      ws = ws == null ? box.getWorksheet() : ws;

      if(!vs.isLMSource()) {
         return new String[0];
      }

      if(ws == null) {
         return new String[0];
      }

      ws = new WorksheetWrapper(ws);
      VSUtil.shrinkTable(vs, ws);
      Assembly[] arr = ws.getAssemblies();
      BoundTableAssembly btable = null;

      for(final Assembly assembly : arr) {
         if(assembly instanceof BoundTableAssembly) {
            btable = (BoundTableAssembly) assembly;
            break;
         }
      }

      if(btable == null || box.getAssetQuerySandbox() == null) {
         return new String[0];
      }

      LMBoundQuery query = null;

      try {
         query = new LMBoundQuery(AssetQuerySandbox.DESIGN_MODE, box.getAssetQuerySandbox(),
                                  btable, false, true);
         query.merge(new VariableTable());
      }
      catch(Exception ex) {
         LOG.error("Failed to merge query for table assembly: " +
            btable.getAssemblyEntry(), ex);
      }

      if(query == null) {
         return new String[0];
      }

      return query.getBoundTables();
   }

   /**
    * Prepare table map.
    */
   private void prepare() {
      lmtables = getLMBoundTables(vs, box);
      Assembly[] varr = vs.getAssemblies();

      for(final Assembly assembly : varr) {
         String bound = ((VSAssembly) assembly).getTableName();

         if(VSUtil.isVSAssemblyBinding(bound)) {
            continue;
         }

         if(assembly instanceof InputVSAssembly) {
            if(bound != null && bound.length() > 0) {
               TableInfo info = tmap2.get(bound);

               if(info == null) {
                  info = new TableInfo(bound);
                  info.initDetailCalc(vs.getCalcFields(bound));
                  tmap2.put(bound, info);
               }

               info.add((VSAssembly) assembly);
            }

            bound = null;

            if(assembly instanceof ListInputVSAssembly) {
               bound = ((ListInputVSAssembly) assembly).getBoundTableName();
            }
         }

         if(bound == null || bound.length() == 0) {
            continue;
         }

         TableInfo info = tmap.get(bound);

         if(info == null) {
            info = new TableInfo(bound);
            info.initDetailCalc(vs.getCalcFields(bound));
            tmap.put(bound, info);
         }

         info.add((VSAssembly) assembly);
      }
   }

   /**
    * Check if the table info is acceptable.
    */
   private boolean isAcceptable(Worksheet ws, TableInfo info, TransformationDescriptor desc) {
      String tname = info.getTable();
      TableAssembly tassembly = (TableAssembly) ws.getAssembly(tname);

      if(tassembly == null) {
         LOG.warn("Materialized view analyzer could not find table assembly {}", tname);
         return false;
      }

      String vassembly = info.getVSAssembly();

      // cube table? not accepted
      if(tassembly instanceof CubeTableAssembly) {
         UserInfo uinfo = new UserInfo(getVsPath(), tname,
                                       catalog.getString("mv.vs.cube", getVsPath()));
         desc.addUserInfo(uinfo);
         desc.addFault(TransformationFault.cubeTable(tname, vassembly));
         return false;
      }

      // embedded table? there should be no input vsassembly or
      // embedded table vsassembly binding to this table
      if(containsBoundEmbeddedTable(tassembly)) {
         UserInfo uinfo = new UserInfo(getVsPath(), tname,
                                       catalog.getString("mv.vs.embed.table", getVsPath()));
         desc.addUserInfo(uinfo);
         desc.addFault(TransformationFault.containsEmbedded(tname, vassembly));
         return false;
      }

      // physical query with variable?
      if(containsQueryVariable(tassembly)) {
         UserInfo uinfo =
            new UserInfo(getVsPath(), tname, catalog.getString("mv.vs.query.var", getVsPath()));
         desc.addUserInfo(uinfo);
         desc.addFault(TransformationFault.containsQueryVariable(tname, vassembly));
         return false;
      }

      if(containsNamedGroupVariable(tassembly)) {
         UserInfo uinfo =
            new UserInfo(getVsPath(), tname, catalog.getString("mv.vs.namedgroup.var", getVsPath()));
         desc.addUserInfo(uinfo);
         desc.addFault(TransformationFault.containsQueryVariable(tname, vassembly));
         return false;
      }

      return true;
   }

   /**
    * Check if selection is hidden.
    */
   private boolean isSelectionHidden(TableAssembly table, String assembly,
                                     boolean root, boolean success) {
      if(root && !success) {
         return true;
      }

      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();

      if(isSelectionHidden(table, assembly, wrapper, root)) {
         return true;
      }

      wrapper = table.getPostRuntimeConditionList();

      if(isSelectionHidden(table, assembly, wrapper, root)) {
         return true;
      }

      // for mv delete condition, if the column is hidden, when delete condition
      // run in mv data, will cause column not found problem
      wrapper = table.getMVConditionList();

      if(isSelectionHidden(table, assembly, wrapper, root)) {
         return true;
      }

      return false;
   }

   private boolean isSelectionHidden(TableAssembly table, String assembly,
                                     ConditionListWrapper wrapper, boolean root)
   {
      ColumnSelection cols = table.getColumnSelection(true);
      String tname = table.getName();
      ConditionList list = wrapper == null ? null : wrapper.getConditionList();

      for(int i = 0; list != null && i < list.getSize(); i += 2) {
         ConditionItem citem = list.getConditionItem(i);
         DataRef attr = citem.getAttribute();

         if(attr instanceof AggregateRef) {
            attr = ((AggregateRef) attr).getDataRef();
         }

         if(!cols.containsAttribute(attr)) {
            if(root) {
               desc.addFault(TransformationFault.selectionHidden(tname, assembly));
            }

            return true;
         }
      }

      return false;
   }

   /**
    * Check if the table assembly is a suitable table use to create mv.
    */
   private boolean isSuitableTable(TableAssembly table, TransformationDescriptor desc) {
      if(this.desc.isInputDynamicTable(table.getName())) {
         desc.addFault(TransformationFault.containsInputDynamicTable(table.getName()));
         return false;
      }

      if(containsPostProcessCalc(table, desc)) {
         desc.addFault(TransformationFault.containsPostAggrCalc(table.getName()));
         return false;
      }

      if(table.getMaxRows() > 0 && table instanceof ComposedTableAssembly &&
         hasSelectionsOnSubTables((ComposedTableAssembly) table))
      {
         desc.addFault(TransformationFault.containsMaxRowsWithSubSelection(table.getName()));
         return false;
      }

      if(table.getMVUpdateConditionList() == null ||
         // if the mv update condition is moved to parent (it may still be
         // kept for the bound table for optimization, we should treat it
         // as non-existant for the purpose of analyzing MV
         MVTool.isMVConditionMoved(table))
      {
         return true;
      }

      ConditionList conds = table.getMVUpdateConditionList().getConditionList();
      boolean hasAppend = conds != null && !conds.isEmpty();

      if(!hasAppend) {
         return true;
      }

      AggregateInfo ainfo = table.getAggregateInfo();

      if(table instanceof RotatedTableAssembly ||
         table instanceof UnpivotTableAssembly ||
         ainfo.isCrosstab())
      {
         desc.addFault(TransformationFault.transformedWithMVUpdate(table.getName()));
         return false;
      }

      return true;
   }

   /**
    * Check if table contains Expression fields which need to be post-processed.
    */
   private boolean containsPostProcessCalc(TableAssembly table, TransformationDescriptor desc) {
      ColumnSelection colSel = table.getColumnSelection();

      if(colSel != null) {
         for(int i = 0; i < colSel.getAttributeCount(); i++) {
            DataRef tcolumn = colSel.getAttribute(i);

            if(tcolumn instanceof CalculateRef &&
               ((CalculateRef) tcolumn).getDataRef() instanceof ExpressionRef)
            {
               CalculateRef calcRef = (CalculateRef) tcolumn;
               ExpressionRef expRef = (ExpressionRef) calcRef.getDataRef();

               // dynamically created calc field used in association (or measure) is
               // currently not supported. (49776)
               if(expRef.isVirtual()) {
                  List<WSColumn> associateCols =
                     desc.getAssociatedSelectionColumns(table.getName());
                  boolean isAssociated = associateCols.stream()
                     .anyMatch(c -> c.getDataRef().getName().equals(tcolumn.getName()));

                  if(isAssociated) {
                     return true;
                  }
               }

               String script = expRef.getExpression();

               if(!MVTool.isExpressionMVCompatible(script) ||
                  // calc contains aggregate field (49777)
                  MVTool.containsAggregateField(script))
               {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Check if contains selection in the condition list.
    */
   private boolean containsSelection(ConditionList conds, DataRef ref) {
      int size = conds == null ? 0 : conds.getSize();

      for(int i = 0; i < size; i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         DataRef attr = citem.getAttribute();

         if(attr.equals(ref)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if contains bound embedded table assembly.
    */
   private boolean containsBoundEmbeddedTable(TableAssembly table) {
      TableInfo tinfo = tmap.get(table.getName());
      VSAssembly[] arr = tinfo == null ? new VSAssembly[0] : tinfo.list();

      for(final VSAssembly vsAssembly : arr) {
         if(vsAssembly instanceof EmbeddedTableVSAssembly) {
            return true;
         }
      }

      tinfo = tmap2.get(table.getName());
      arr = tinfo == null ? new VSAssembly[0] : tinfo.list();

      for(final VSAssembly vsAssembly : arr) {
         if(vsAssembly instanceof InputVSAssembly) {
            return true;
         }
      }

      // if sub-table is embedded with input binding, allow the subtable of composite to be
      // materialized (but not the embedded table). (44162)
      if(table instanceof ComposedTableAssembly) {
         ComposedTableAssembly ctable = (ComposedTableAssembly) table;
         TableAssembly[] tables = ctable.getTableAssemblies(false);

         for(final TableAssembly tableAssembly : tables) {
            if(!containsBoundEmbeddedTable(tableAssembly)) {
               return false;
            }
         }

         // if all sub tables are embedded, no need to materialize. (44162)
         return true;
      }

      return false;
   }

   private boolean containsQueryVariable(TableAssembly table) {
      if(table instanceof SQLBoundTableAssembly) {
         SQLBoundTableAssembly stable = (SQLBoundTableAssembly) table;
         JDBCQuery query = ((SQLBoundTableAssemblyInfo) stable.getInfo()).getQuery();
         return query.getAllDefinedVariables().hasMoreElements();
      }
      else if(table instanceof ComposedTableAssembly) {
         for(TableAssembly parent : ((ComposedTableAssembly) table).getTableAssemblies(false)) {
            if(containsQueryVariable(parent)) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean containsNamedGroupVariable(TableAssembly table) {
      AggregateInfo ainfo = table.getAggregateInfo();

      for(GroupRef group : ainfo.getGroups()) {
         if(group.getNamedGroupInfo() != null &&
            group.getNamedGroupInfo().getAllVariables().length > 0)
         {
            return true;
         }
      }

      if(table instanceof ComposedTableAssembly) {
         for(TableAssembly parent : ((ComposedTableAssembly) table).getTableAssemblies(false)) {
            if(containsNamedGroupVariable(parent)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Append the detail calc to it's binding table or s_table.
    */
   private void appendDetailCalc(TableAssembly tbl, List<CalculateRef> calcs) {
      if(tbl != null) {
         ColumnSelection sel = tbl.getColumnSelection();

         for(CalculateRef ref : calcs) {
            sel.addAttribute((DataRef) ref.clone());
         }

         VSUtil.addCalcBaseRefs(sel, null, calcs);
         tbl.resetColumnSelection();
      }
   }

   /**
    * If any VSAssembly is not combinable, show warning.
    */
   private void checkCombinable(MVDef[] defs, TableInfo[] infos) {
      Assembly[] varr = vs.getAssemblies();

      for(Assembly assembly : varr) {
         String name = assembly.getName();
         MVDef def = null;

         for(int j = 0; j < infos.length; j++) {
            if(infos[j].contains(name)) {
               def = defs[j];
               break;
            }
         }

         List<String> reasons = checkCombinable((VSAssembly) assembly, def);

         if(!reasons.isEmpty()) {
            desc.addNotHitMVHint(assembly.getName(), reasons.toArray(new String[0]));
         }
      }
   }

   /**
    * Check if the VSAssembly is Combinable.
    * @return list of messages describing non-combinable aggregates.
    */
   private static List<String> checkCombinable(VSAssembly assembly, MVDef def) {
      List<String> desc = new ArrayList<>();
      AggregateInfo ainfo = MVDef.getAggregateInfo(assembly);
      MVTransformer.isCombinable(ainfo, desc);

      if(def != null && !def.isValidMV(ainfo)) {
         String multiDistinct = TransformationInfo.multiDistinctCount();

         if(!desc.contains(multiDistinct)) {
            desc.add(multiDistinct);
         }
      }

      return desc;
   }

   /**
    * Table info, stores one table and its binding viewsheet assemblies.
    */
   private static class TableInfo {
      TableInfo(String table) {
         this.table = table;
         this.set = new HashSet<>();
      }

      @Override
      public boolean equals(Object obj) {
         TableInfo info2 = (TableInfo) obj;
         return table.equals(info2.table);
      }

      @Override
      public int hashCode() {
         return table.hashCode();
      }

      public void add(VSAssembly assembly) {
         set.add(assembly);
      }

      void initDetailCalc(CalculateRef[] calculates) {
         if(calculates != null) {
            calcs = new ArrayList<>();

            for(CalculateRef ref : calculates) {
               if(ref.isBaseOnDetail()) {
                  calcs.add(ref);
               }
            }
         }
      }

      public VSAssembly[] list() {
         VSAssembly[] arr = new VSAssembly[set.size()];
         set.toArray(arr);
         return arr;
      }

      public boolean isSelectionOnly() {
         for(VSAssembly assembly : set) {
            if(!(assembly instanceof SelectionVSAssembly)) {
               return false;
            }

            // don't shrink to selection only if selection measure specified
            if(assembly.getInfo() instanceof SelectionBaseVSAssemblyInfo) {
               SelectionBaseVSAssemblyInfo info = (SelectionBaseVSAssemblyInfo) assembly.getInfo();

               if(info.getMeasure() != null && info.getMeasure().length() > 0) {
                  return false;
               }
            }
         }

         return !set.isEmpty();
      }

      boolean isSelectionBound(ColumnRef col) {
         for(VSAssembly assembly : set) {
            if(!(assembly instanceof SelectionVSAssembly)) {
               continue;
            }

            SelectionVSAssembly selection = (SelectionVSAssembly) assembly;
            DataRef[] refs = selection.getDataRefs();

            for(int i = 0; refs != null && i < refs.length; i++) {
               if(col.equals(refs[i])) {
                  return true;
               }
            }
         }

         return false;
      }

      public String getVSAssembly() {
         StringBuilder sb = new StringBuilder();

         for(VSAssembly assembly : set) {
            if(sb.length() > 0) {
               sb.append(',');
            }

            sb.append(assembly.getName());
         }

         return sb.toString();
      }

      public String getTable() {
         return table;
      }

      public String toString() {
         return "TableInfo[" + table + "<-" + set + ']';
      }

      public boolean contains(String name) {
         for(VSAssembly assembly : set) {
            if(name.equals(assembly.getName())) {
               return true;
            }
         }

         return false;
      }

      private List<CalculateRef> calcs;
      private final String table;
      private final Set<VSAssembly> set;
   }

   /**
    * Get assemblies that bind to the table.
    */
   public String getAssemblies(String table) {
      TableInfo info = tmap.get(table);
      return info.getVSAssembly();
   }

   /**
    * Get display infomation for user.
    */
   @Override
   public String getInfo(MVDef[] defs) {
      final String blank = "     ";
      StringBuilder buf = new StringBuilder();

      // 1: top mv infomations
      List<MVDef> tops = new ArrayList<>();
      listMV(defs, tops, false);

      if(!tops.isEmpty()) {
         buf.append(blank).append("Top MV:");
         Map<String, List<MVDef>> v2defs = sync(tops);
         Iterator<String> assemblies = v2defs.keySet().iterator();
         int idx = 1;

         while(assemblies.hasNext()) {
            String vassembly = assemblies.next();
            buf.append("\n").append(blank).append(blank).append(idx).append(". ").append(vassembly);
            buf.append(",").append(blank).append("[base table:");
            List<MVDef> defs2 = v2defs.get(vassembly);

            for(int i = 0; i < defs2.size(); i++) {
               MVDef def = defs2.get(i);

               buf.append(def.getMetaData().getWsPath()).append("/")
                  .append(def.getMetaData().getBoundTable());

               buf.append(i != defs2.size() - 1 ? "," : "]");
            }

            idx++;
         }
      }

      // 2: sub mv infomations
      List<MVDef> subs = new ArrayList<>();
      listMV(defs, subs, true);

      if(!subs.isEmpty()) {
         if(!tops.isEmpty()) {
            buf.append("\n\n");
         }

         buf.append(blank).append("Sub MV:");
         Map<String, List<MVDef>> v2defs = sync(subs);
         Iterator<String> assemblies = v2defs.keySet().iterator();
         int idx = 1;

         while(assemblies.hasNext()) {
            String vassembly = assemblies.next();
            buf.append("\n").append(blank).append(blank).append(idx).append(". ").append(vassembly);
            buf.append(",").append(blank).append("[base table:");
            List<MVDef> defs2 = v2defs.get(vassembly);

            for(int i = 0; i < defs2.size(); i++) {
               MVDef def = defs2.get(i);

               buf.append(def.getMetaData().getWsPath()).append("/")
                  .append(def.getMetaData().getBoundTable());

               buf.append(i != defs2.size() - 1 ? "," : "]");
            }

            appendFaults(buf, vassembly, blank + blank + blank);
            idx++;
         }
      }

      // 3: none-mv infomations
      List<String> noneMV = listNonMV(defs);

      if(!noneMV.isEmpty()) {
         if(!tops.isEmpty() || !subs.isEmpty()) {
            buf.append("\n\n");
         }

         buf.append(blank).append("No MV:");

         for(int i = 0; i < noneMV.size(); i++) {
            String table = noneMV.get(i);
            String vassembly = getAssemblies(table);
            buf.append("\n").append(blank).append(blank).append(i + 1).append(". ")
               .append(vassembly);
            appendFaults(buf, vassembly, blank + blank + blank);
         }
      }

      // 4: warning infomations
      int index = 1;

      for(TableInfo info : tmap.values()) {
         if(info == null) {
            continue;
         }

         String vassembly = info.getVSAssembly();

         if(vassembly == null || vassembly.length() <= 0 || vassembly.equals(SUB_QUERY)) {
            continue;
         }

         List winfos = desc.getWarnings(vassembly);

         if(winfos == null || winfos.isEmpty()) {
            continue;
         }

         buf.append("\n\n").append(blank).append("Warnings:");
         buf.append("\n").append(blank).append(blank).append(index).append(". ").append(vassembly);

         for(int i = 0; i < winfos.size(); i++) {
            buf.append("\n").append(blank).append(blank).append(blank).append(i + 1).append(") ")
               .append(winfos.get(i));
         }
      }

      // 5: may not hit mv info
      Map<String, List<String>> mayNotHitMV = getDescriptor().getNotHitMVHints();

      if(mayNotHitMV.size() > 0) {
         buf.append("\n\n").append(blank).append("May Not Hit MV:");
         index = 1;

         for(String name : mayNotHitMV.keySet()) {
            List reasons = mayNotHitMV.get(name);
            buf.append("\n").append(blank).append(blank).append(index).append(". ").append(name);
            index++;

            for(int j = 0; j < reasons.size(); j++) {
               buf.append("\n").append(blank).append(blank).append(blank).append(j + 1).append(") ")
                  .append(reasons.get(j));
            }
         }
      }

      return buf.toString();
   }

   private Map<String, List<MVDef>> sync(List<MVDef> defs) {
      Map<String, List<MVDef>> v2defs = new HashMap<>();

      for(MVDef def : defs) {
         String table = def.getBoundTable();
         String vassembly = getAssemblies(table);
         List<MVDef> sdefs = v2defs.computeIfAbsent(vassembly, (key) -> new ArrayList<>());
         sdefs.add(def);
      }

      return v2defs;
   }

   public List<String> getFaults(MVDef[] defs) {
      List<String> list = new ArrayList<>();
      Map<String, List<String>> mayNotHitMV = getDescriptor().getNotHitMVHints();

      for(MVDef def : defs) {
         String table = def.getBoundTable();
         String vassembly = getAssemblies(table);
         List faults = getDescriptor().getFaults(vassembly);

         for(int i = 0; i < faults.size(); i++) {
            TransformationFault fault = (TransformationFault) faults.get(i);

            if(!list.contains(fault.getReason())) {
               list.add(fault.getReason());
            }
         }
      }

      if(mayNotHitMV.size() > 0) {
         for(String name : mayNotHitMV.keySet()) {
            List reasons = mayNotHitMV.get(name);

            for(int j = 0; j < reasons.size(); j++) {
               list.add(reasons.get(j).toString());
            }
         }
      }

      return list;
   }

   private void appendFaults(StringBuilder buf, String vassembly, String blank) {
      List faults = getDescriptor().getFaults(vassembly);

      if(faults == null || faults.isEmpty()) {
         buf.append("\n").append(blank).append("none.");
         return;
      }

      for(int i = 0; i < faults.size(); i++) {
         String index = (i + 1) + ") ";
         TransformationFault fault = (TransformationFault) faults.get(i);
         buf.append("\n").append(blank).append(index).append(fault.getDescription());
         buf.append("\n").append(blank);

         for(int j = 0; j < index.length(); j++) {
            buf.append(' ');
         }

         buf.append(" Reason: ").append(fault.getReason());
      }
   }

   private void listMV(MVDef[] defs, List<MVDef> ldefs, boolean sub) {
      for(MVDef def : defs) {
         if(def.isSub() == sub) {
            ldefs.add(def);
         }
      }
   }

   private List<String> listNonMV(MVDef[] defs) {
      List<String> tables = new ArrayList<>();

      for(String key : tmap.keySet()) {
         boolean found = false;

         for(MVDef def : defs) {
            if(key.equals(def.getBoundTable())) {
               found = true;
               break;
            }
         }

         if(!found) {
            tables.add(key);
         }
      }

      return tables;
   }

   private boolean hasSelectionsOnSubTables(ComposedTableAssembly tableAssembly) {
      for(TableAssembly assembly : tableAssembly.getTableAssemblies(false)) {
         if(desc.hasSelection(assembly.getName())) {
            // selections exist for this table assembly
            return true;
         }
         else if(assembly instanceof ComposedTableAssembly) {
            if(hasSelectionsOnSubTables((ComposedTableAssembly) assembly)) {
               return true;
            }
         }
      }

      return false;
   }

   private class MVDefInfo {
      MVDefInfo(String vsId, TableAssembly table, String otname,
                TransformationDescriptor desc, boolean root,
                boolean rootMirror, TableInfo info, boolean bypass)
      {
         this.vsId = vsId;
         this.table = table;
         this.otname = otname;
         this.ws = desc.getWorksheet();
         this.desc = desc.cloneSelections();
         this.root = root;
         this.rootMirror = rootMirror;
         this.info = info;
         this.odesc = desc;
         this.bypass = bypass;
      }

      MVDef createDef() {
         // root and selection only, we may hide the other column selections,
         // then the created mv will be much smaller than the original one
         if(root && !rootMirror && isSelectionOnly()) {
            shrinkTable(table, info);
         }

         String defaultCycle = MVManager.getManager().getDefaultCycle();
         String wsId = null;

         if(vs != null && !vs.isDirectSource() && vs.getBaseEntry() != null) {
            wsId = vs.getBaseEntry().toIdentifier();
         }

         MVDef def = new MVDef(vsId, wsId, table.getName(), otname, vs, ws,
                               user, desc, !root, isSelectionOnly(), bypass);

         if(def.isAssociationMV() &&
            hasCalcField(((MirrorTableAssembly) table).getTableAssembly()))
         {
            def.setShareable(false);
         }

         if(vs.getBaseEntry() != null) {
            def.setLogicalModel(vs.getBaseEntry().isLogicModel());
            def.setLMBoundTables(lmtables);
         }

         if(defaultCycle != null && !defaultCycle.equals("")) {
            def.setCycle(defaultCycle);
         }

         odesc.clearPseudoFilter();
         return def;
      }

      // check if table contains calc field
      private boolean hasCalcField(TableAssembly tbl) {
         ColumnSelection cols = tbl.getColumnSelection(true);

         for(int i = 0; i < cols.getAttributeCount(); i++) {
            ColumnRef col = (ColumnRef) cols.getAttribute(i);

            if(col.getDataRef() instanceof ExpressionRef) {
               return true;
            }
         }

         return false;
      }

      private Boolean isCorrelated() {
         List<ConditionListWrapper> clws = new ArrayList<>();
         clws.add(table.getPreRuntimeConditionList());
         clws.add(table.getPostRuntimeConditionList());

         for(ConditionListWrapper clw : clws) {
            if(clw == null) {
               continue;
            }

            boolean isCorrelated = checkSubQueryValue(clw);

            if(isCorrelated) {
               return isCorrelated;
            }
         }

         return false;
      }

      private boolean checkSubQueryValue(ConditionListWrapper wrapper) {
         ConditionList conds = wrapper.getConditionList();

         for(int i = 0; conds != null && i < conds.getSize(); i += 2) {
            Object cond = conds.getXCondition(i);

            if(!(cond instanceof AssetCondition)) {
               continue;
            }

            AssetCondition acnd = (AssetCondition) cond;
            SubQueryValue val = acnd.getSubQueryValue();

            if(val == null) {
               continue;
            }

            if(val.isCorrelated()) {
               return true;
            }
         }
         return false;
      }

      private boolean isSelectionOnly() {
         if(!info.isSelectionOnly()) {
            return false;
         }

         return !subqueries.contains(info.table);
      }

      private final String vsId;
      private final TableAssembly table;
      private final String otname;
      private final Worksheet ws;
      private final boolean root;
      private final boolean rootMirror;
      private final TableInfo info;
      private final TransformationDescriptor desc;
      private final TransformationDescriptor odesc;
      private final boolean bypass;
   }

   private Map<String, TableInfo> ctmap; // current table map
   private String[] lmtables;
   private TransformationDescriptor desc;

   private final Map<String, TableInfo> tmap = new HashMap<>();
   private final Map<String, TableInfo> tmap2 = new HashMap<>();
   private final Vector<String> subqueries = new Vector<>();
   private final Catalog catalog = Catalog.getCatalog();
   private final Viewsheet vs;
   private final String vsId;
   private final Identity user;
   private final ViewsheetSandbox box;
   private final boolean bypass;

   private static final Logger LOG = LoggerFactory.getLogger(VSMVAnalyzer.class);
   private static final String SUB_QUERY = Catalog.getCatalog().getString("SubQuery Condition");
}
