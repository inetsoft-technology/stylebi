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
package inetsoft.mv;

import inetsoft.mv.data.GroupedTableBlock;
import inetsoft.mv.data.MVQueryBuilder;
import inetsoft.mv.trans.*;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MVTransformer, transform one table assembly which should hit mv, to make
 * sure that it is able to hit mv.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class MVTransformer {
   /**
    * Find the used mv table.
    */
   public static TableAssembly findMVTable(TableAssembly table) {
      return TransformationDescriptor.findMVTable(table);
   }

   /**
    * Check if this table assembly contains runtime mv.
    */
   public static boolean containsRuntimeMV(TableAssembly table, boolean phy) {
      if(table == null) {
         return false;
      }

      RuntimeMV info = table.getRuntimeMV();

      if(info != null && (!phy || info.isPhysical())) {
         return true;
      }

      if(!(table instanceof ComposedTableAssembly)) {
         return false;
      }

      ComposedTableAssembly ctable = (ComposedTableAssembly) table;
      TableAssembly[] tables = ctable.getTableAssemblies(false);

      for(int i = 0; i < tables.length; i++) {
         if(containsRuntimeMV(tables[i], phy)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the table assembly is combinable in aggregate.
    */
   public static boolean isCombinable(TableAssembly table, boolean root) throws Exception {
      RuntimeMV rmv = table.getRuntimeMV();

      if(rmv == null && root) {
         return false;
      }

      boolean physical = rmv != null && rmv.isPhysical();

      if(physical) {
         MVDef def = MVManager.getManager().get(rmv.getMV());

         if(def == null) {
            return false;
         }

         boolean lm = def.isLogicalModel();
         boolean combinable = isCombinable0(table);

         if(!combinable) {
            return false;
         }

         if(!lm) {
            return true;
         }

         String missingCol = containsTableColumns(table, def);

         if(missingCol != null) {
            throw new MVExecutionException("Column doesn't exist in MV. Please regenerate MV: " +
                                              missingCol);
         }

         return true;
      }

      if(!(table instanceof ComposedTableAssembly)) {
         return true;
      }

      ComposedTableAssembly ctable = (ComposedTableAssembly) table;
      TableAssembly[] tables = ctable.getTableAssemblies(false);

      for(int i = 0; i < tables.length; i++) {
         if(!isCombinable(tables[i], false)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if the table assembly is combinable in aggregate.
    */
   private static boolean isCombinable0(TableAssembly table) {
      AggregateInfo info = table == null ? null : table.getAggregateInfo();

      if(info == null) {
         return true;
      }

      if(info.isEmpty()) {
         ColumnSelection pcols = table.getColumnSelection(true);

         for(int i = 0; i < pcols.getAttributeCount(); i++) {
            DataRef ref = pcols.getAttribute(i);

            if(!isDataRefSupported(ref)) {
               return false;
            }
         }
      }

      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();
      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();

      for(int i = 0; conds != null && i < conds.getSize(); i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         DataRef attr = citem.getAttribute();

         if(!isDataRefSupported(attr)) {
            return false;
         }
      }

      return isCombinable(info, null);
   }

   private static DataRef normalize(DataRef ref) {
      if(ref instanceof ColumnRef) {
         ColumnRef cref = (ColumnRef) ref;
         DataRef tmp = cref.getDataRef();

         if(tmp instanceof AliasDataRef) {
            ref = ((AliasDataRef) tmp).getDataRef();
         }

         if(tmp instanceof NamedRangeRef) {
            ref = ((NamedRangeRef) tmp).getDataRef();
         }
      }

      return ref;
   }

   /**
    * Check if the aggregate is combinable.
    */
   public static boolean isCombinable(AggregateInfo info, List<String> desc) {
      if(info == null || info.isEmpty()) {
         return true;
      }

      GroupRef[] groups = info.getGroups();
      boolean combinable = true;

      for(int i = 0; i < groups.length; i++) {
         DataRef ref = groups[i].getDataRef();

         if(!isDataRefSupported(ref)) {
            if(desc != null) {
               desc.add(TransformationInfo.namedGroup(normalize(ref)));
            }

            combinable = false;
         }
      }

      AggregateRef[] arr = info.getAggregates();
      int failcnt = 0; // non-combinable formula count
      int distcnt = 0; // distinct count count

      for(int i = 0; i < arr.length; i++) {
         AggregateFormula form = arr[i].getFormula();

         if(form != null) {
            boolean f_combinable = form.isCombinable();

            if(!f_combinable) {
               failcnt++;

               if(form == AggregateFormula.COUNT_DISTINCT) {
                  distcnt++;
               }
               else if(desc != null) {
                  DataRef ref = normalize(arr[i].getDataRef());
                  desc.add(TransformationInfo.formulaNotCombinable(ref, form));
               }
            }
         }
      }

      if(distcnt > 1 && desc != null) {
         desc.add(TransformationInfo.multiDistinctCount());
      }

      if(failcnt > 0) {
         // if only a single distinct count is not combinable, we can still
         // distribute the calculation by breaking blocks at the distinct
         // value boundary (after sorting the table on the distinct col).
         if(failcnt > 1 || distcnt != 1) {
            combinable = false;
         }
      }

      return combinable;
   }

   /**
    * Check if all the columns in the table exist in mv.
    * @return name of column that is missing in the MV.
    */
   private static String containsTableColumns(TableAssembly table, MVDef def) {
      ColumnSelection cols = table.getColumnSelection(true);
      AggregateInfo ainfo = table.getAggregateInfo();

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         DataRef col = cols.getAttribute(i);

         if(MVQueryBuilder.isCalculateRef(col, table)) {
            continue;
         }

         DataRef bcol = getBaseRef(col);
         String attr = MVDef.getMVHeader(bcol);
         MVColumn mcol = def.getColumn(attr, true);
         mcol = mcol != null ? mcol : def.getColumn(attr, false);

         if(mcol == null && ainfo.containsAggregate(col)) {
            col = GroupedTableBlock.getDataRef(col);
            attr = MVDef.getMVHeader(col);
            mcol = def.getColumn(attr, false);
         }

         // try to get the column by the ref name
         if(mcol == null) {
            attr = bcol.getName();
            mcol = def.getColumn(attr, true);
            mcol = mcol != null ? mcol : def.getColumn(attr, false);
         }

         if(mcol == null) {
            LOG.warn("Column not found in materialized view: " + col + " in " + def.getName() +
               " columns: " + def.getColumns().stream()
               .map(c -> c.getColumn() + "").collect(Collectors.joining(",")));
            return bcol.getAttribute();
         }
      }

      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();
      ConditionList conds = wrapper == null ? null : wrapper.getConditionList();

      for(int i = 0; conds != null && i < conds.getSize(); i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         DataRef col = citem.getAttribute();
         String attr = MVDef.getMVHeader(col);
         MVColumn mcol = def.getColumn(attr, true);
         mcol = mcol != null ? mcol : def.getColumn(attr, false);

         if(mcol == null) {
            LOG.warn("Filter column not found in materialized view: " + col + ", " +
               def.getName());
            return col.getAttribute();
         }
      }

      return null;
   }

   /**
    * Get the base data ref.
    */
   public static DataRef getBaseRef(DataRef ref) {
      DataRef bref = ref;

      if(bref instanceof ColumnRef) {
         bref = ((ColumnRef) bref).getDataRef();

         if(bref instanceof AliasDataRef) {
            AliasDataRef aref = (AliasDataRef) bref;
            bref = aref.getDataRef();
            ref = bref;
         }
      }

      return ref;
   }

   /**
    * Check if the data ref is supported.
    */
   private static boolean isDataRefSupported(DataRef ref) {
      if(ref instanceof ColumnRef) {
         ref = ((ColumnRef) ref).getDataRef();
      }

      if(ref instanceof NamedRangeRef) {
         return false;
      }

      return true;
   }

   /**
    * Transform the specified table assembly generated by the specified
    * viewsheet.
    */
   public static TableAssembly transform(XPrincipal user, TableAssembly table) throws Exception {
      TableAssembly mvtable = TransformationDescriptor.findMVTable(table);

      // mv table not found? ignore it
      if(mvtable == null) {
         return table;
      }

      // don't transform if there is no associated vs
      if(mvtable.getRuntimeMV() != null &&
         mvtable.getRuntimeMV().getViewsheet() == null &&
         // association mv is not passed a vs but needs to be transformed
         // to support variable condition
         !"true".equals(mvtable.getProperty("force.transform")))
      {
         return mvtable;
      }

      /*
      // if the exist new add calc field will not hit the mv
      if(containsInvalidSelection(vs, ws)) {
         return table;
      }
      */

      int mode = TransformationDescriptor.RUN_MODE;
      TransformationDescriptor desc = new TransformationDescriptor(table, mode);
      TableAssembly ntable = desc.getTable(false);

      if(ntable == null) {
         // This seems to happen when an out-of-sync worksheet clone is used when an asset query is
         // being created to bring the column selection of a mirror table in sync with the
         // underlying query. Removing the call will break other use cases, and the worksheet here
         // isn't really used--it is transformed correctly in a subsequent call. Just break out to
         // prevent errors.
         // should not use mvtable because the MV is probably not valid for mvtable if the
         // transformer failed. just return the original table. (49816)
         return table;
      }

      ntable.update();

      // 1. move up selection to mv table
      SelectionUpTransformer stransformer = new SelectionUpTransformer();
      boolean success = stransformer.transform(desc);
      boolean cmirror = stransformer.getRootMirror() != null;

      if(!success) {
         RuntimeMV rmv = mvtable.getRuntimeMV();
         mvtable = desc.getMVTable();

         if(mvtable != null) {
            // a physical mv? need convert it to non-physical mv
            if(rmv.isPhysical()) {
               mvtable.setRuntimeMV(new RuntimeMV(rmv.getEntry(),
                                                  rmv.getViewsheet(), rmv.getVSAssembly(),
                                                  rmv.getBoundTable(), null, true,
                                                  rmv.getMVLastUpdateTime(), rmv.getParentVsIds()));
            }

            addSubMV(user, rmv, mvtable, desc, true, rmv.isPhysical());

            if(!containsRuntimeMV(mvtable, true)) {
               String msg = "No sub-mv found for: " + rmv + ".";

               if(stransformer.isNeedRecreateMV()) {
                  msg += " Recreate the MV ";
               }

               MVExecutionException mex = new MVExecutionException(msg);
               LOG.warn(msg);
               mex.setNeedRecreate(stransformer.isNeedRecreateMV());
               throw mex;
            }
         }
      }
      // transform success, but not physical mv, cannot hit mv, recreate
      // fix bug1312786390099
      else {
         RuntimeMV rmv = mvtable.getRuntimeMV();

         if(!rmv.isPhysical()) {
            // try to reuse sub mv
            if(mvtable != null) {
               addSubMV(user, rmv, mvtable, desc, true, false);
               success = false;
            }

            if(!containsRuntimeMV(mvtable, true)) {
               throw new MVExecutionException(new RuntimeException("MV table " +
                  mvtable + " need physical mv, but contains non-physical mv: " +
                  rmv));
            }

            // should return transformed table (from desc->ws) since filters may be moved
            // at this point. otherwise the transformed selection may be lost
            //return table;
            return desc.getTable(true);
         }
      }

      AbstractTransformer transformer;

      // 2. rename named selection and modify filter value
      if(success) {
         transformer = new NamedSelectionTransformer();
         transformer.transform(desc);
      }

      // 3. decomposite named group
      if(!cmirror && success) {
         transformer = new NamedGroupTransformer();
         transformer.transform(desc);
      }

      // 4. move down selection to mv table
      if(success) {
         transformer = new SelectionDownTransformer();
         transformer.transform(desc);
      }

      // 5. move aggregate down to mv table
      if(!cmirror && success) {
         transformer = new AggregateDownTransformer();
         transformer.transform(desc);
      }

      // 6. rename range selection and modify filter value
      transformer = new RangeSelectionTransformer();
      transformer.transform(desc);

      // 7. create mirror for table meta data
      // (for associated selection queries)
      /* this is done individually in MVAnalyer and MVTableMetaData
      transformer = new TableMetaDataTransformer();
      transformer.transform(desc);
      */

      // 7. run mv on detail and perform calc field on the result
      /*transformer = new CalcFieldTransformer();
      success = transformer.transform(desc);

      if(!success) {
         mvtable = TransformationDescriptor.findMVTable(table);
         LOG.info("Cannot hit mv for: " + mvtable.getRuntimeMV());
         removeMV(table);
         return table;
      }*/

      if("true".equals(SreeEnv.getProperty("mv.debug"))) {
         String info = desc.getInfo();
         LOG.debug("Transformation info: " + info);
      }

      return desc.getTable(true);
   }

   /**
    * Remove mv information for it's not combinable.
    */
   public static void removeMV(TableAssembly table) {
      table.setRuntimeMV(null);

      if(!(table instanceof ComposedTableAssembly)) {
         return;
      }

      ComposedTableAssembly ctable = (ComposedTableAssembly) table;
      TableAssembly[] tables = ctable.getTableAssemblies(false);

      for(int i = 0; i < tables.length; i++) {
         removeMV(tables[i]);
      }
   }

   /**
    * Check if have new added calc field which not exist when create mv.
    */
   private static boolean containsInvalidSelection(Viewsheet vs, Worksheet ws) {
      Assembly[] objs = vs.getAssemblies();

      for(int i = 0; i < objs.length; i++) {
         if(objs[i] instanceof SelectionVSAssembly) {
            SelectionVSAssembly sassembly = (SelectionVSAssembly) objs[i];
            String tname = sassembly.getTableName();
            CalculateRef[] calcs = vs.getCalcFields(tname);
            TableAssembly tbl = (TableAssembly) ws.getAssembly(tname);

            if(calcs == null || tbl == null) {
               continue;
            }

            List<CalculateRef> crefs = Arrays.asList(calcs);
            DataRef[] refs = sassembly.getDataRefs();
            ColumnSelection cols = tbl.getColumnSelection();

            for(int j = 0; j < refs.length; j++) {
               if(crefs.contains(refs[j]) && !cols.containsAttribute(refs[j])) {
                  LOG.warn(
                     "Table contains calculated field which does not exist " +
                     "in materialized view: " + refs[j]);
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Add sub mv information to sub tables if any.
    */
   private static void addSubMV(XPrincipal user, RuntimeMV rmv,
                                TableAssembly table,
                                TransformationDescriptor desc,
                                boolean root, boolean physical) throws MVExecutionException
   {
      AssetEntry entry = rmv.getEntry();
      String vassembly = rmv.getVSAssembly();
      String otable = rmv.getBoundTable();
      String ptable = table.getName();
      Viewsheet vs = rmv.getViewsheet();
      MVManager mgr = MVManager.getManager();

      if(mgr == null) {
         return;
      }

      // only use a sub-mv if there is no selection on children, otherwise
      // the selection condition would be ignored.
      MVDef def = (root || desc.isSelectionOnChildren(ptable)) ? null
         : mgr.findMV(entry, otable, user, ptable, rmv.getParentVsIds());

      if(def == null && physical) {
         def = (root || desc.isSelectionOnChildren(ptable)) ? null
            : mgr.findMV(entry, otable, user, null, rmv.getParentVsIds());
      }

      if(def != null) {
         checkColumns(def, table.getColumnSelection(true));
         String mv = def.getName();
         RuntimeMV rinfo = new RuntimeMV(entry, vs, vassembly, otable, mv, true,
                                         def.getLastUpdateTime(), def.getParentVsIds());
         table.setRuntimeMV(rinfo);
         return;
      }

      if(!(table instanceof ComposedTableAssembly)) {
         return;
      }

      ComposedTableAssembly ctable = (ComposedTableAssembly) table;
      TableAssembly[] tables = ctable.getTableAssemblies(false);

      for(int i = 0; i < tables.length; i++) {
         addSubMV(user, rmv, tables[i], desc, false, physical);
      }
   }

   /**
    * Check if all columns are contained in the MV.
    */
   private static void checkColumns(MVDef def, ColumnSelection cols) {
      final List<MVColumn> mvcols = def.getColumns();

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         DataRef col = cols.getAttribute(i);

         // calculate ref handled in post processing, ignore. (54071)
         if(col instanceof CalculateRef) {
            continue;
         }

         boolean matches = false;
         String colName;

         if((col instanceof ColumnRef) && ((ColumnRef) col).isApplyingAlias() &&
            ((ColumnRef) col).getAlias() != null)
         {
            colName = ((ColumnRef) col).getAlias();
         }
         else {
            colName = col.getAttribute();
         }

         for(MVColumn mvcol : mvcols) {
            if(mvcol.matches(colName, mvcol.isDimension())) {
               matches = true;
               break;
            }
         }

         // if selection changed (e.g. adding selection in selection container), the columns
         // may no longer match and the MV will need to be recreated
         if(!matches) {
            throw new MVExecutionException("Table column doesn't exist in MV: " + colName +
                                           " in " + def.getBoundTable());
         }
      }
   }

   public static boolean isAggregateExpression(DataRef ref) {
      if(ref instanceof AggregateRef) {
         return isAggregateExpression(((AggregateRef) ref).getDataRef());
      }
      else if(ref instanceof ColumnRef) {
         return isAggregateExpression(((ColumnRef) ref).getDataRef());
      }
      else if(ref instanceof AliasDataRef) {
         return isAggregateExpression(((AliasDataRef) ref).getDataRef());
      }
      else if(ref instanceof ExpressionRef) {
         return ((ExpressionRef) ref).isOnAggregate();
      }

      return false;
   }

   private static final Logger LOG = LoggerFactory.getLogger(MVTransformer.class);
}
