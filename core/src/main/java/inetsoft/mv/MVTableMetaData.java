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

import inetsoft.mv.trans.TableMetaDataTransformer;
import inetsoft.report.TableLens;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.report.composition.execution.*;
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class provides table meta data from a materialized view.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public final class MVTableMetaData extends TableMetaData {
   /**
    * Create an instance of MVTableMetaData.
    */
   public MVTableMetaData(String name, RuntimeMV rmv, ViewsheetSandbox box) {
      super(name);

      this.box = box;
      this.ws = box.getWorksheet();
      Viewsheet vs = box.getViewsheet();
      this.oname = name;
      this.ormv = rmv;
      this.tname = TableMetaDataTransformer.PREFIX + name;

      MVManager mgr = MVManager.getManager();

      // try metadata mv
      try {
         // don't pass vs to prevent the metadata table being transformed
         meta_rmv = mgr.findRuntimeMV(box.getAssetEntry(), null, "", tname,
                                      (XPrincipal) box.getUser(), box,
                                      box.isRuntime());
      }
      catch(Exception ex) {
         LOG.error("Failed to RuntimeMV: " + tname, ex);
      }

      if(meta_rmv != null) {
         TableAssembly table = (TableAssembly) ws.getAssembly(oname);

         // if a selection is made on a sub-table of this table,
         // the selection list value should be filtered (12.1),
         // using the ASSOCIATION MV would cause the list to stay
         // the same. This behavior may not be so desirable so
         // we may considering changing it in the future.
         if(AssetUtil.containsRuntimeCondition(table, true)) {
            meta_rmv = null;
         }
      }

      if(meta_rmv == null) {
         // revert back to full mv
         revertToFullMV();
      }
      else {
         LOG.info("Use metadata MV for association processing: " + name);
      }

      if(this.ws != null && vs.isDirectSource()) {
         this.ws = new WorksheetWrapper(ws);
         VSUtil.shrinkTable(vs, this.ws);
      }
   }

   /**
    * Don't use the meta data table (which is an optimization) and
    * use the full MV table instead.
    * @return true if reverted.
    */
   private boolean revertToFullMV() {
      this.tname = VSAssembly.SELECTION + oname;

      if(meta_rmv != null) {
         LOG.warn("Association MV failed, reverting back to full MV: " + meta_rmv);
         meta_rmv = null;
         return true;
      }

      return false;
   }

   /**
    * Get a distinct column(s) table from the meta data.
    */
   @Override
   public XTable getColumnTable(String vname, String[] columns) {
      // Verify that the requested column(s) are present in the association
      if(meta_rmv != null && meta_rmv.getMV() != null) {
         MVManager mgr = MVManager.getManager();
         MVDef mvdef = mgr.get(meta_rmv.getMV());
         List<MVColumn> mvcols = mvdef.getColumns();

         for(String col: columns) {
            boolean found = false;

            for(MVColumn mvcol: mvcols) {
               if(mvcol.getName().equals(col)) {
                  found = true;
                  break;
               }
            }

            if(!found) {
               meta_rmv.getEntry().setProperty("mv_column_missing", "true");
               revertToFullMV();
            }
         }
      }

      TableAssembly table = null;

      try {
         table = createTempTable(vname, createUniqueName("MVCOL"), meta_rmv);
         transform(table, columns, null, null);

         TableLens data = getTableLens(table);

         if(data != null) {
            for(int i = 0; i < columns.length; i++) {
               typemap.put(columns[i], Tool.getDataType(data.getColType(i)));
            }
         }

         return data;
      }
      catch(MessageException | ConfirmException e) {
         throw e;
      }
      catch(Exception ex) {
         // the metadata tble may be out of sync (missing columns?)
         if(revertToFullMV()) {
            return getColumnTable(vname, columns);
         }

         // if MV failed for selection, it's because MV is not possible for this selection.
         // this situation may arise from adhoc filter. it would be dangerous to run a
         // live query. just show a message ignore the adhoc filter. (53475)
         if(ex instanceof MVExecutionException) {
            CoreTool.addUserMessage(ex.getMessage());
         }

         LOG.error("Failed to get column table for assembly: " + vname, ex);
      }
      finally {
         removeTempTable(table);
      }

      return null;
   }

   /**
    * Create a table to apply association MV.
    */
   private TableAssembly createTempTable(String vname, String name, RuntimeMV meta_rmv)
      throws Exception
   {
      TableAssembly table;
      Viewsheet vs = box.getViewsheet();
      XPrincipal user = (XPrincipal) box.getUser();

      if(meta_rmv != null) {
         // use the SELECTION table to avoid selection conditions
         // to be used when retrieving selection list values (#8588)
         TableAssembly otable = (TableAssembly) ws.getAssembly(Assembly.SELECTION + oname);
         RuntimeMV ormv0 = otable.getRuntimeMV();

         // Bug #25590, calc field may not be present at this point, which would cause
         // the table validation to fail in createMirror. call appendCalcFields explicitly
         // to ensure they are there
         ColumnSelection sel = otable.getColumnSelection(true);
         VSUtil.appendCalcFields(sel, oname, vs);

         otable.setRuntimeMV(ormv);
         table = TableMetaDataTransformer.createMirror(otable);
         table.setRuntimeMV(meta_rmv);
         table.setProperty("force.transform", "true");
         table = MVTransformer.transform(user, table);
         otable.setRuntimeMV(ormv0);
      }
      else {
         String tname = VSAssembly.SELECTION + oname;
         table = ws.getVSTableAssembly(tname);
         table = box.getBoundTable(table, name, false);
         ColumnSelection sel = table.getColumnSelection(true);
         VSUtil.appendCalcFields(sel, oname, vs);

         RuntimeMV rmv = table.getRuntimeMV();

         if(rmv != null && vname != null) {
            rmv = new RuntimeMV(rmv.getEntry(), rmv.getViewsheet(), vname,
                                rmv.getBoundTable(), rmv.getMV(), rmv.isSub(),
                                rmv.getMVLastUpdateTime(), rmv.getParentVsIds());
            table.setRuntimeMV(rmv);
         }

         // Bug #53708, transform so that RuntimeMV is set on sub tables
         table = MVTransformer.transform(user, table);
      }

      VSAQuery.normalizeTable(table);
      Lock lock;

      // lock ws before add so we don't intersperse the add/remove
      synchronized(templocks) {
         lock = templocks.get(table.getName());

         if(lock == null) {
            templocks.put(table.getName(), lock = new ReentrantLock());
         }
      }

      lock.lock();
      ws.addAssembly(table);
      return table;
   }

   /**
    * Remove the temp table created with createTempTable.
    */
   private void removeTempTable(TableAssembly table) {
      if(table != null) {
         ws.removeAssembly(table);

         Lock lock = templocks.get(table.getName());

         if(lock != null) {
            lock.unlock();

            synchronized(templocks) {
               templocks.remove(table.getName());
            }
         }
      }
   }

  /**
   * Add condition to this table, and also add grouping information.
   * @return true if measure aggregate column is added to the table.
   */
   private boolean transform(TableAssembly table, Object[] cols, String measure,
                             ConditionList conds)
      throws ColumnNotFoundException
   {
      ColumnSelection columns = table.getColumnSelection(true);
      AggregateInfo ainfo = new AggregateInfo();
      ColumnSelection csel = table.getColumnSelection().clone();
      boolean rc = false;

      for(Object cname : cols) {
         ColumnRef col = findColumn(cname, columns);
         GroupRef ref = new GroupRef(col);
         ainfo.addGroup(ref);
         csel.addAttribute(col);
      }

      if(measure != null && measure.length() > 0) {
         AggregateFormula formula = AggregateFormula.getFormula(measure);

         if(formula != null) {
            String mstr = measure.substring(formula.getFormulaName().length());
            mstr = mstr.substring(1, mstr.length() - 1);
            ColumnRef col = findColumn(mstr, columns);

            if(col != null) {
               AggregateRef aref = new AggregateRef(col, formula);

               // handle aggregate calc field (50644)
               if(col instanceof CalculateRef && !((CalculateRef) col).isBaseOnDetail()) {
                  Viewsheet vs = box.getViewsheet();
                  String expression = ((ExpressionRef) col.getDataRef()).getExpression();
                  List<String> matchNames = new ArrayList<>();
                  List<AggregateRef> aggs = VSUtil.findAggregate(vs, oname, matchNames, expression);

                  aggs.forEach(ainfo::addAggregate);
               }
               else {
                  // use alias so same column can be used for group and measure
                  col = new ColumnRef(new AliasDataRef(aref.toString(), col));

                  // keep the column data type for max(date)/min(date), tec.
                  if(aref.getDataType() != null) {
                     col.setDataType(aref.getDataType());
                  }
               }

               csel.addAttribute(col);
               ainfo.addAggregate(new AggregateRef(col, formula));
               rc = true;
            }
         }
      }

      // condition columns (association) needs to be included or condition will be cleared. (52635)
      if(conds != null) {
         conds.stream()
            .filter(item -> item instanceof ConditionItem)
            .map(item -> ((ConditionItem) item).getAttribute())
            .map(ref -> ref != null ? findColumn(ref, columns) : null)
            .filter(ref -> ref != null)
            .forEach(ref -> csel.addAttribute(ref));
      }

      // the order of the columns are important. (50215)
      table.setColumnSelection(csel);
      table.setAggregateInfo(ainfo);
      table.setPreRuntimeConditionList(conds);

      if(conds != null) {
         int clen = conds.getSize();

         for(int i = 0; i < clen; i += 2) {
            ConditionItem cond = conds.getConditionItem(i);
            DataRef ref = cond.getAttribute();
            DataRef nref = findColumn(ref, columns);

            if(nref == null) {
               nref = findColumn(ref.getAttribute(), columns);
            }

            if(nref == null) {
               throw new ColumnNotFoundException("Column not found: " + ref + " in " + columns);
            }

            cond.setAttribute(nref);
         }
      }

      table.resetColumnSelection();
      return rc;
   }

   /**
    * Find column by name.
    */
   private ColumnRef findColumn(Object cname, ColumnSelection cols) {
      int count = cols.getAttributeCount();
      String name = cname.toString();
      ColumnRef ref2 = null;

      for(int i = 0; i < count; i++) {
         ColumnRef ref = (ColumnRef) cols.getAttribute(i);
         String attr = ref.getAttribute();
         String alias = ref.getAlias();
         alias = attr.equals(alias) ? null : alias;

         if(name.equals(alias) || name.equals(ref.toString())) {
            return ref;
         }

         if(name.equals(attr) && (alias == null || alias.length() == 0)) {
            ref2 = ref;
         }
      }

      return ref2;
   }

   /**
    * Get the table data for the table assembly.
    */
   private TableLens getTableLens(TableAssembly tbl) throws Exception {
      AssetQuerySandbox wbox = box.getAssetQuerySandbox();
      return AssetDataCache.getData(box.getID(), tbl, wbox, null,
                                    AssetQuerySandbox.RUNTIME_MODE, false, box.getTouchTimestamp(), null);
      /*
      long ts = System.currentTimeMillis();
      AssetQuery qry = AssetQuery.createAssetQuery(
         tbl, AssetQuerySandbox.RUNTIME_MODE, box.getAssetQuerySandbox(),
         true, ts, true);
      return qry.getTableLens(box.getVariableTable());
      */
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public SelectionSet getAssociatedValues(String vname,
                                           Map<String, Collection<Object>> selections,
                                           DataRef[] refs,
                                           String measure,
                                           SelectionMeasureAggregation measureAggregation)
      throws Exception
   {
      TableAssembly table = createTempTable(vname, createUniqueName("MVASS"), meta_rmv);
      ConditionList conds = getConditionList(selections, refs, table);
      SelectionSet aset = new SelectionSet(); // associated values
      Formula mformula = getAggregateFormula(measure);
      final Map<SelectionSet.Tuple, Formula> formulaMap = measureAggregation.getFormulas();

      try {
         boolean maggr;

         try {
            // if need to calculate selection measure, can't use association MV since
            // it only contains distinct rows of dim/measure columns. force to use full mv.
            if(measure != null && meta_rmv != null) {
               throw new ColumnNotFoundException();
            }

            maggr = transform(table, refs, measure, conds);
         }
         catch(ColumnNotFoundException ex) {
            // condition column not in association mv, try full mv
            removeTempTable(table);
            table = createTempTable(vname, createUniqueName("MVASS"), null);
            conds = getConditionList(selections, refs, table);
            maggr = transform(table, refs, measure, conds);
         }

         TableLens lens = getTableLens(table);
         final Map<Object, Object> mvalues = measureAggregation.getMeasures();
         // column may not always be in the order of refs (48852).

         // could be cancelled
         if(lens != null) {
            int[] refCols = new int[refs.length];
            ColumnIndexMap idxmap = new ColumnIndexMap(lens, true);

            for(int i = 0; i < refs.length; i++) {
               if((refCols[i] = Util.findColumn(lens, refs[i], idxmap)) < 0) {
                  throw new ColumnNotFoundException("Column missing in associated values: " +
                                                    refs[i] + " in " + idxmap.getStrHeaderKeySet());
               }
            }

            // iterate through the tuples to find matches with the selection bitmap
            for(int r = 1; lens.moreRows(r); r++) {
               Object[] values = new Object[refs.length];

               for(int i = 0; i < values.length; i++) {
                  values[i] = lens.getObject(r, refCols[i]);
                  values[i] = SelectionSet.normalize(values[i]);
               }

               for(int i = 1; i <= refs.length; i++) {
                  SelectionSet.Tuple tuple = new SelectionSet.Tuple(values, i);
                  aset.add(tuple);
               }

               if(maggr) {
                  Object mvalue = lens.getObject(r, lens.getColCount() - 1);

                  if(mvalue instanceof Number) {
                     Number mval = (Number) mvalue;

                     measureAggregation.setMaxIfBigger(mval.doubleValue());
                     measureAggregation.setMinIfSmaller(mval.doubleValue());
                  }

                  for(int i = 1; i <= refs.length; i++) {
                     SelectionSet.Tuple tuple = new SelectionSet.Tuple(values, i);

                     if(mformula != null) {
                        Formula form = formulaMap.get(tuple);

                        if(form == null) {
                           formulaMap.put(tuple, form = (Formula) mformula.clone());
                        }

                        form.addValue(mvalue);
                     }
                  }
               }
            }
         }
      }
      catch(Exception ex) {
         // the metadata table may be out of sync (missing columns?)
         if(revertToFullMV()) {
            return getAssociatedValues(vname, selections, refs, measure, measureAggregation);
         }

         LOG.error("Failed to get associated values for assembly: " + vname, ex);
      }
      finally {
         removeTempTable(table);
      }

      return aset;
   }

   /**
    * Get the formula for aggregating measure values.
    */
   private Formula getAggregateFormula(String measure) {
      if(measure == null) {
         return null;
      }

      AggregateFormula aform = AggregateFormula.getFormula(measure);

      if(aform == null) {
         LOG.warn("Invalid formula: " + measure);
         return null;
      }

      Formula form = Util.createFormula(null, aform.getFormulaName());

      if(!(form instanceof MaxFormula) && !(form instanceof MinFormula)) {
         // aggregate on aggregate for count and sum
         form = new SumFormula();
      }

      return form;
   }

   /**
    * Get the condition list.
    */
   private ConditionList getConditionList(Map<String, Collection<Object>> selections,
                                          DataRef[] refs, TableAssembly table)
   {
      ColumnSelection cols = table.getColumnSelection();
      Map<String, Collection<Object>> tmap = new HashMap<>(selections);
      Map<String, Collection<Object>> valuemap = new HashMap<>(); // col -> value list
      final ArrayList<ConditionList> conds = new ArrayList<>();

      for(final String col : tmap.keySet()) {
         // selection path is used to make sure the association is properly
         // restricted by tree selections. See logic in find()
         if(col.startsWith(SelectionVSAssembly.SELECTION_PATH)) {
            // build a condition list for a selection tree Bug #52677
            final String key = col.substring(SelectionVSAssembly.SELECTION_PATH.length());
            final String[] refNames = VSUtil.parseSelectionKey(key);
            Collection<Object> treeSelections = tmap.get(col);

            if(treeSelections == null) {
               continue;
            }

            List<String> parentSelections = new ArrayList<>();

            // find all parent selections first and sort them by length in descending order
            for(Object selection : treeSelections) {
               String strSelection = selection + "";
               int index = strSelection.indexOf("/CHILD_SELECTION_EXISTS");

               if(index > 0) {
                  parentSelections.add(strSelection.substring(0, index));
               }
            }

            parentSelections.sort(Comparator.comparingInt(String::length).reversed());

            List<ConditionList> treeConds = new ArrayList<>();
            final String separator = "_^/^_";

            // Now we know the boundaries of each selection and can replace the / with a custom
            // separator so that we can then just use split to get a value for each ref.
            // Doing it this way allows for a selection value to contain a / in it.
            for(Object selection : treeSelections) {
               String strSelection = selection + "";

               if(!strSelection.contains("/CHILD_SELECTION_EXISTS") &&
                  !parentSelections.contains(strSelection))
               {
                  for(String parentSelection : parentSelections) {
                     // replace the / on the next char from where the parent selection ends
                     if(strSelection.startsWith(parentSelection) &&
                        strSelection.length() > parentSelection.length() &&
                        strSelection.charAt(parentSelection.length()) == '/')
                     {
                        strSelection = strSelection.substring(0, parentSelection.length()) +
                           separator + strSelection.substring(parentSelection.length() + 1);
                     }
                  }

                  // split by the custom separator
                  String[] refValues = Tool.split(strSelection, separator, false);
                  List<ConditionList> refConds = new ArrayList<>();

                  // build the condition list
                  for(int i = 0; i < refValues.length; i++) {
                     ColumnRef ref = findColumn(refNames[i], cols);
                     refConds.add(VSUtil.createConditionList(
                        ref, Collections.singletonList(refValues[i])));
                  }

                  treeConds.add(VSUtil.mergeConditionList(refConds, JunctionOperator.AND));
               }
            }

            conds.add(VSUtil.mergeConditionList(treeConds, JunctionOperator.OR));
            continue;
         }
         else if(col.startsWith(SelectionVSAssembly.RANGE)) {
            List<Object> rconds = (List<Object>) tmap.get(col);

            for(int i = 0; i < rconds.size(); ) {
               List<ConditionList> list = new ArrayList<>();
               int end = i + 1;
               RangeCondition rcond = (RangeCondition) rconds.get(i);
               String id = rcond.getId();
               list.add(rcond.createConditionList());

               for(; end < rconds.size(); end++) {
                  RangeCondition rcond2 = (RangeCondition) rconds.get(end);
                  String id2 = rcond2.getId();

                  if(!Objects.equals(id, id2)) {
                     break;
                  }

                  list.add(rcond2.createConditionList());
               }

               // date range conditions (from calendar) are OR conditions. (46883)
               ConditionList nconds = VSUtil.mergeConditionList(list, JunctionOperator.OR);

               if(nconds != null && nconds.getSize() > 0) {
                  conds.add(nconds);
               }

               i = end;
            }

            continue;
         }

         List<Object> values = (List<Object>) valuemap.get(col);

         // create a column condition list
         if(values == null) {
            values = new ArrayList<>();
            valuemap.put(col, values);
         }

         values.addAll(tmap.get(col));
      }

      // build conditions
      for(String col : valuemap.keySet()) {
         List<Object> values = (List<Object>) valuemap.get(col);
         ColumnRef ref = findColumn(col, cols);
         ConditionList colcond = VSUtil.createConditionList(ref, values);
         conds.add(colcond);
      }

      return VSUtil.mergeConditionList(conds, JunctionOperator.AND);
   }

   /**
    * Get the column type.
    */
   @Override
   public String getType(String column) {
      // this assumes getColumnTable has been called on this column.
      // Otherwise we need to force it to be loaded.
      return typemap.get(column);
   }

   /**
    * Check if the data is valid or not.
    */
   public boolean isValid() {
      return true;
   }

   @Override
   public void process(XTable table, String[] columns, List<AggregateRef> aggrs) {
      // for realtime meta data only
   }

   /**
    * Create an unique name.
    */
   private synchronized String createUniqueName(String prefix) {
      return prefix + "-" + (counter++);
   }

   private static final Logger LOG = LoggerFactory.getLogger(MVTableMetaData.class);
   private final Map<String, Lock> templocks = new ConcurrentHashMap<>();
   private final Map<String, String> typemap = new HashMap<>();
   private ViewsheetSandbox box;
   private Worksheet ws;
   private String tname;
   private int counter;
   private String oname; // the original table
   private RuntimeMV ormv; // the original table RMV
   private RuntimeMV meta_rmv = null; // the rmv for the meta data table
}
