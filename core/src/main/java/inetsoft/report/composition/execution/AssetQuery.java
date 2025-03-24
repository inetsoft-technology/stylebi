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
package inetsoft.report.composition.execution;

import inetsoft.mv.*;
import inetsoft.mv.formula.CompositeVarianceFormula;
import inetsoft.mv.trans.NamedGroupTransformer;
import inetsoft.mv.trans.TransformationDescriptor;
import inetsoft.report.*;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.report.internal.binding.*;
import inetsoft.report.internal.table.*;
import inetsoft.report.lens.*;
import inetsoft.report.script.formula.AssetQueryScope;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.log.LogLevel;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.script.*;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Format;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Asset query executes a table assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class AssetQuery extends PreAssetQuery {
   /**
    * Create the asset query of a table assembly.
    * @param table the specified table.
    * @param mode the specified mode.
    * @param box the specified sandbox.
    * @param stable <tt>true</tt> if is a sub table.
    * @param touchtime touch timestamp of data changes.
    * @param root if this query is not a sub query.
    * @param metadata true if this is only used to retrieve metadata.
    */
   public static AssetQuery createAssetQuery(TableAssembly table, int mode,
                                             AssetQuerySandbox box,
                                             boolean stable, long touchtime,
                                             boolean root, boolean metadata)
      throws Exception
   {
      AssetQuery query = null;
      RuntimeMV rrinfo = null;
      XPrincipal user = (XPrincipal) box.getUser();

      if(root) {
         box.resetDefaultColumnSelection(table.getName(), table.getWorksheet());

         TableAssembly ntable = null;
         TableAssembly rmvtable = MVTransformer.findMVTable(table);

         // don't apply ws mv during ws design as per Bug #33943
         /*
         if(rmvtable == null && !box.isActive() && SreeEnv.getBooleanProperty("ws.mv.enabled")) {
            applyWSRuntimeMV(table, box, user);
            rmvtable = MVTransformer.findMVTable(table);
         }
         */

         rrinfo = rmvtable == null ? null : rmvtable.getRuntimeMV();

         try {
            // check if child assemblies contain ws mv and if so then pass this table through
            // the worksheet mv transformer
            if(WSMVTransformer.containsWSRuntimeMV(table)) {
               if(!AssetQuerySandbox.isDesignMode(mode)) {
                  table = WSMVTransformer.transform(table);
                  TransformationDescriptor.clearPseudoFilter(table, TransformationDescriptor.RUN_MODE);
               }
            }
            else {
               ntable = MVTransformer.transform(user, table);
               TransformationDescriptor.clearPseudoFilter(ntable, TransformationDescriptor.RUN_MODE);
            }

            if(rmvtable != null && ntable != null) {
               TableAssembly mvtable = MVTransformer.findMVTable(ntable);

               // Bug #40884, if mvtable is not combinable then aggregate data in post process
               if(mvtable instanceof MirrorTableAssembly &&
                  !MVTransformer.isCombinable(mvtable, true))
               {
                  table = getNoAggregateTable((MirrorTableAssembly) mvtable, ntable, table, box);
               }
            }
         }
         catch(MVExecutionException mex) {
            if(mex.isNeedRecreateMV()) {
               throw mex;
            }

            table.setRuntimeMV(null);
         }
         catch(Exception ex) {
            boolean required = "true".equals(SreeEnv.getProperty("mv.required"));

            if(required) {
               LOG.error("MV missing for " + table, ex);
               String msg = Catalog.getCatalog().getString("vs.mv.missing");
               throw new MessageException(msg, LogLevel.ERROR);
            }
            else {
               LOG.warn("MV not supported for " + table, ex);
               table.setRuntimeMV(null);
            }
         }

         if(ntable != table && ntable != null) {
            TableAssembly mvtable = MVTransformer.findMVTable(ntable);

            try {
               // mv table is combinable (spark supports all formulas)?
               // replace the original table with new table, then mv could be hit
               if(mvtable != null && MVTransformer.isCombinable(mvtable, true)) {
                  copyColumns(table, ntable);
                  table = ntable;
                  box.resetDefaultColumnSelection(table.getName(), table.getWorksheet());

                  if("true".equals(SreeEnv.getProperty("mv.debug"))) {
                     LOG.debug("Asset query mvtable: {}, {}", mvtable, mvtable.addr());
                     StringBuilder sb = new StringBuilder();
                     table.print(0, sb);
                     LOG.debug("AssetQuery mv table is: {}", sb);
                  }
               }
               // mv table is not combinable?
               else if(mvtable != null) {
                  // get details from MV and aggregate data in post process
                  if(mvtable instanceof MirrorTableAssembly) {
                     table = getNoAggregateTable((MirrorTableAssembly) mvtable, ntable, table, box);
                  }
                  // remove MV otherwise result is wrong (aggregate on aggregate)
                  else {
                     MVTransformer.removeMV(table);
                     LOG.warn("MV ignored because aggregate is not combinable.");
                  }

                  if("true".equals(SreeEnv.getProperty("mv.debug"))) {
                     StringBuilder sb = new StringBuilder();
                     table.print(0, sb);
                     LOG.debug("mv table is not combinable: {}", sb);
                  }
               }
            }
            catch(MVExecutionException ex) {
               // @by ChrisSpagnoli, for Bug #6584
               // Need to propagate MVExecutionException for mv.ondemand
               if("true".equals(SreeEnv.getProperty("mv.ondemand"))) {
                  throw ex;
               }

               boolean required = "true".equals(SreeEnv.getProperty("mv.required"));

               if(required) {
                  LOG.error("Retry, MV missing for " + table, ex);
                  String msg = Catalog.getCatalog().getString("vs.mv.missing");
                  throw new MessageException(msg, LogLevel.ERROR);
               }
               else {
                  LOG.warn("Retry, MV not supported for " + table, ex);
                  table.setRuntimeMV(null);
               }
            }
         }
      }
      else {
         // this is for the special case of discrete measure in chart. normally the
         // sub-table MV transformation is already performed so the selections would already
         // be transformed. in the case of ChartVSAQuery.applyDiscreteAggregates(), a new
         // join table is created to process the discrete joins. however, the
         // applyDiscreteAggregates() is only called at runtime (and not done for MV analysis).
         // we won't need this special logic if applyDiscreteAggregates() is performed
         // during MV analysis. however, the method is quite complex and it would add
         // a lot of complexity to the overall MV flow. (48175)
         if(table instanceof MirrorTableAssembly && table.getPreRuntimeConditionList() == null) {
            TableAssembly base = ((MirrorTableAssembly) table).getTableAssembly();

            if(base != null) {
               table.setPreRuntimeConditionList(base.getPreRuntimeConditionList());
            }
         }

         // discrete measure in chart creates a join table from the original table. the mv
         // is on the original table. since the sub-table (non-root) is not transformed,
         // named group on the sub-table is not properly handled in MV. we do a special
         // transformation here. the alternative is the have the NamedGroupTransformer
         // handling nested composite table (instead of just mirrors). since it's not
         // necessary for non-discrete chart query, we just leave the NamedGroupTransformer
         // to handle the simple case. (50295, 50310)
         if(mode != AssetQuerySandbox.DESIGN_MODE && MVTransformer.findMVTable(table) != null &&
            "true".equals(table.getProperty("discrete_sub_table")))
         {
            NamedGroupTransformer transformer = new NamedGroupTransformer();
            TransformationDescriptor desc =
               new TransformationDescriptor(table, TransformationDescriptor.RUN_MODE);
            ConditionListWrapper conds = table.getPreRuntimeConditionList();
            transformer.transform(desc);
            table = desc.getTable(true);
            // since filtering not transformed, explicitly restore the condition. (50338)
            table.setPreRuntimeConditionList(conds);
            TransformationDescriptor.clearPseudoFilter(table, TransformationDescriptor.RUN_MODE);
         }
      }

      RuntimeMV rinfo = table.getRuntimeMV();
      MVDef def = null;

      if(rinfo != null && rinfo.isPhysical()) {
         VariableTable vars = box.getVariableTable();
         def = MVManager.getManager().get(rinfo.getMV());
         ViewsheetSandbox vbox = box.getViewsheetSandbox();
         boolean detail = "true".equals(table.getProperty("isDetail"));

         // def could be null if MV is invoked from the API
         if((def == null || def.isValidMV(table)) && (vbox == null || vbox.isMVEnabled(detail))) {
            query = MVAssetQuery.createQuery(table, user, vars, stable, mode);
         }
      }

      boolean cannotHitMV = query != null && def != null && !def.canHitMV(table);

      if(box != null && box.getMVProcessor() != null && box.getMVProcessor().needCheck()) {
         // this mv is physical mv, but cannot create query or cannot hit mv
         if(rinfo != null && rinfo.isPhysical() &&
            (query == null || def != null && !def.canHitMV(table)) ||
            // root table contains mv, after transform, no physical mv
            rrinfo != null && !MVTransformer.containsRuntimeMV(table, true))
         {
            rinfo = rinfo != null ? rinfo : rrinfo;
            String vname = rinfo.getVSAssembly();

            // detail table and cannot hit mv?
            if(!"true".equals(table.getProperty("isDetail"))) {
               box.getMVProcessor().notHitMV(vname);
            }
         }
      }

      if(cannotHitMV && "true".equals(table.getProperty("isDetail"))) {
         query = null;
         table.setRuntimeMV(null);
      }

      // materialized view?
      if(query != null) {
         // do not create query
      }
      // data table?
      else if(table instanceof DataTableAssembly) {
         query = new DataQuery(mode, box, (DataTableAssembly) table, stable, metadata);
      }
      // embedded table?
      else if(table instanceof EmbeddedTableAssembly) {
         ((EmbeddedTableAssembly) table).setForMetadata(metadata);
         query = new EmbeddedQuery(mode, box, (EmbeddedTableAssembly) table, stable, metadata);
      }
      // query bound table?
      else if(table instanceof QueryBoundTableAssembly) {
         query = new BoundQuery(mode, box, (QueryBoundTableAssembly) table,
                                stable, metadata);
      }
      // physical table bound table?
      else if(table instanceof PhysicalBoundTableAssembly) {
         query = new PhysicalBoundQuery(mode, box, (PhysicalBoundTableAssembly) table,
                                        stable, metadata);
      }
      // cube table?
      else if(table instanceof CubeTableAssembly) {
         query = new CubeQuery(mode, box, (CubeTableAssembly) table, stable, metadata);
      }
      // sql bound table?
      else if(table instanceof SQLBoundTableAssembly) {
         query = new SQLBoundQuery(mode, box, (SQLBoundTableAssembly) table,
                                   stable, metadata);
      }
      // tabular table?
      else if(table instanceof TabularTableAssembly) {
         query = new TabularBoundQuery(mode, box, (TabularTableAssembly) table,
                                       stable, metadata);
      }
      // model bound table?
      else if(table instanceof BoundTableAssembly) {
         query = new LMBoundQuery(mode, box, (BoundTableAssembly) table,
                                  stable, metadata);
      }
      // mirror table?
      else if(table instanceof MirrorTableAssembly) {
         query = new MirrorQuery(mode, box, (MirrorTableAssembly) table,
                                 stable, metadata, touchtime);
      }
      // rotated table?
      else if(table instanceof UnpivotTableAssembly) {
         query = new UnpivotQuery(mode, box, (UnpivotTableAssembly) table,
                                  stable, metadata, touchtime);
      }
      // rotated table?
      else if(table instanceof RotatedTableAssembly) {
         query = new RotatedQuery(mode, box, (RotatedTableAssembly) table,
                                  stable, metadata, touchtime);
      }
      // concatenated table?
      else if(table instanceof ConcatenatedTableAssembly) {
         query = new ConcatenatedQuery(mode, box, (ConcatenatedTableAssembly) table,
                                       stable, metadata, touchtime);
      }
      // join table?
      else if(table instanceof AbstractJoinTableAssembly) {
         query = new JoinQuery(mode, box, (AbstractJoinTableAssembly) table,
                               stable, metadata, touchtime);
      }
      else {
         throw new RuntimeException("Unsupported table found: " + table);
      }

      query.touchtime = touchtime;
      query.mexecuted = metadata;
      query.validate();

      return query;
   }

   private static TableAssembly getNoAggregateTable(MirrorTableAssembly mvtable,
                                                    TableAssembly ntable,
                                                    TableAssembly table,
                                                    AssetQuerySandbox box)
   {
      MirrorTableAssembly noaggr = (MirrorTableAssembly) mvtable.clone();
      noaggr.getInfo().setName(noaggr.getName() + "_no_aggregate");
      mvtable.getWorksheet().addAssembly(noaggr);
      mvtable.setTableAssemblies(new TableAssembly[]{ noaggr });
      noaggr.getAggregateInfo().clear();
      noaggr.resetColumnSelection();
      ColumnSelection mvcols = new ColumnSelection();
      noaggr.getColumnSelection(true).stream()
         // aggregate expression not supported in mv query. (50240)
         .filter(c -> !MVTransformer.isAggregateExpression(c) ||
            // if mv pushed to noaggr, CalculateRef will need to be processed in
            // mvtable so they must be kept. (50833)
            c instanceof CalculateRef && !((CalculateRef) c).isBaseOnDetail())
         .forEach(c -> {
            ColumnRef col = (ColumnRef) c;

            if(col.getEntity() != null) {
               DataRef ref = AssetUtil.getOuterAttribute(noaggr.getName(), c);
               ColumnRef col2 = new ColumnRef(ref);
               col2.setDataType(col.getDataType());
               mvcols.addAttribute(col2);
            }
            else {
               mvcols.addAttribute(col);
            }
         });

      // if aggregate info contains date range, and it's not grouped, we should
      // change the name of the column back to the detail column,
      // (e.g. Year(Date) -> Date). otherwise the DateReangeRef in mirror won't
      // find the column.
      List<DataRef> cols2 = noaggr.getColumnSelection(false).stream()
         // aggregate expression not supported in mv query. (50240)
         .filter(c -> !MVTransformer.isAggregateExpression(c))
         .map(c -> {
            ColumnRef col = (ColumnRef) c;
            if(col.getDataRef() instanceof DateRangeRef) {
               return new ColumnRef(((DateRangeRef) col.getDataRef()).getDataRef());
            }

            return c;
         }).collect(Collectors.toList());
      noaggr.setColumnSelection(new ColumnSelection(cols2));

      mvtable.setColumnSelection(mvcols);
      mvtable.setRuntimeMV(null);

      copyColumns(table, ntable);
      table = ntable;
      box.resetDefaultColumnSelection(table.getName(), table.getWorksheet());
      return table;
   }

   /**
    * Copy the column setting from table to ntable.
    */
   private static void copyColumns(TableAssembly table, TableAssembly ntable) {
      // copy column sorting
      ntable.setSortInfo(table.getSortInfo());

      ColumnSelection cols = table.getColumnSelection();
      ColumnSelection ncols = ntable.getColumnSelection();

      // copy column visibility
      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) cols.getAttribute(i);

         if(!col.isVisible()) {
            ColumnRef ncol = (ColumnRef) ncols.findAttribute(col);

            if(ncol != null) {
               ncol.setVisible(false);
            }
         }
      }
   }

   /**
    * Shuck off format.
    * @param table the specified table lens with format.
    * @return the table lens without format.
    */
   public static TableLens shuckOffFormat(TableLens table) {
      if(table instanceof TableFilter2) {
         TableLens table2 = ((TableFilter2) table).getTable();

         if(table2 instanceof AssetQuery.FormatTableLens) {
            return new TableFilter2(
               ((AssetQuery.FormatTableLens) table2).getTable());
         }
      }
      else if(table instanceof AssetQuery.FormatTableLens) {
         return ((AssetQuery.FormatTableLens) table).getTable();
      }
      else if(table instanceof ColumnMapFilter) {
         // Bug #61776, Bug #61813, also seems to be caused by changes in AssetQueryCacheNormalizer
         // since ColumnMapFilter is now added to rearrange the columns. The following gets rid of
         // the FormatTableLens while keeping the ColumnMapFilter
         ColumnMapFilter filter = (ColumnMapFilter) table;

         if(filter.getTable() instanceof TableFilter2 ||
            filter.getTable() instanceof AssetQuery.FormatTableLens)
         {
            filter.setTable(shuckOffFormat(filter.getTable()));
            return filter;
         }
      }
      else if(table instanceof TextSizeLimitTableLens) {
         return shuckOffFormat(((TextSizeLimitTableLens) table).getTable());
      }

      return table;
   }

   /**
    * Make sure the table column headers matches the column selection. Some
    * database may change the column alias to upper case. This method fix
    * the discrepancies.
    */
   public static TableLens fixColumnHeaders(TableLens base,
                                            ColumnSelection columns)
   {
      AttributeTableLens htable = new AssetTableLens(base);
      boolean changed = false;

      ColumnIndexMap columnIndexMap = new ColumnIndexMap(base);

      // set column identifier
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);
         int col = AssetUtil.findColumn(base, column, columnIndexMap);
         String id = AssetUtil.getAttributeString(column);

         // @by larryl, column may be hidden by vpm
         if(col < 0) {
            continue;
         }

         String header = AssetUtil.format(XUtil.getHeader(base, col));
         id = getColumnIdentifier(id, column,
            "true".equalsIgnoreCase(columns.getProperty("BC_VALIDATE") + ""));
         base.setColumnIdentifier(col, id);
         htable.setColumnIdentifier(col, id);
         String name = column.getAlias();
         name = name != null ? name : column.getAttribute();

         // some databases like db2 will return the uppercase name of an
         // expression, so here we fix the problem by forcing to set object
         if(name.equalsIgnoreCase(header) && !name.equals(header)) {
            changed = true;
            htable.setObject(0, col, name);
         }
         // some databases like db2 will return table.column of a subquery,
         // so here we fix the problem by forcing to set object
         else if(header.toLowerCase().endsWith("." + name.toLowerCase())) {
            changed = true;
            htable.setObject(0, col, name);
         }
      }

      return changed ? htable : base;
   }

   /**
    * Gets the log/audit record for this query.
    *
    * @return the record object.
    */
   protected Collection<?> getLogRecord() {
      return Collections.emptySet();
   }

   /**
    * Get the query time out.
    */
   protected int getQueryTimeOut() {
      return 0;
   }

   /**
    * Get the time out.
    */
   protected int getTimeout() {
      int timeout = getQueryTimeOut();

      if(timeout > 0 && timeout != Integer.MAX_VALUE) {
         return timeout;
      }

      String tname = getTable().getName();

      if(!box.isTimeLimited(tname) && !(this instanceof TabularBoundQuery)) {
         return -1;
      }

      // try runtime or preview
      String propName = (mode & AssetQuerySandbox.RUNTIME_MODE) != 0 ?
         "query.runtime.timeout" : "query.preview.timeout";
      String prop = SreeEnv.getProperty(propName);

      if(prop != null) {
         try {
            return Integer.parseInt(prop);
         }
         catch(Exception ex) {
            LOG.warn("Invalid value for the query timeout (" + propName + "): " + prop, ex);
         }
      }

      // runtime?
      if((mode & AssetQuerySandbox.RUNTIME_MODE) != 0) {
         // try query max time
         prop = SreeEnv.getProperty("query.max.time");

         if(prop != null) {
            try {
               return Integer.parseInt(prop);
            }
            catch(Exception ex) {
               LOG.warn("Invalid value for the query timeout (query.max.time): " +
                  prop, ex);
            }
         }

         // 20 minutes for runtime
         return 1200;
      }
      // design time?
      else {
         // 2 minutes for design time
         return 120;
      }
   }

   /**
    * Create an asset query.
    */
   public AssetQuery(int mode, AssetQuerySandbox box, boolean stable, boolean metadata) {
      super(mode, box, stable);
      this.mexecuted = metadata;
   }

   /**
    * fix bug1393319867115
    * Check if the source is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   public boolean isSourceMergeable0() throws Exception {
      return isSourceMergeable();
   }

   /**
    * Check if the source is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isSourceMergeable() throws Exception {
      final TableAssembly table = getTable();

      if(MVTransformer.containsRuntimeMV(table, true)) {
         return false;
      }

      return table.isSQLMergeable();
   }

   /**
    * Get the post process base table.
    * @param vars the specified variable table.
    * @return the post process base table of the query.
    */
   protected abstract TableLens getPostBaseTableLens(VariableTable vars)
      throws Exception;

   /**
    * Get the table.
    * @param vars the specified variable table.
    * @return the table of the query.
    */
   public TableLens getTableLens(VariableTable vars) throws Exception {
      return GroupedThread.runWithRecordContext(this::getLogRecord,
                                                () -> this.doGetTableLens(vars));
   }

   private TableLens doGetTableLens(VariableTable vars) throws Exception {
      TableLens base;

      // design mode?
      if(AssetQuerySandbox.isDesignMode(mode)) {
         base = getDesignTableLens(vars);
      }
      // live/runtime mode?
      else {
         DataKey key = null;

         try {
            VariableTable extraVars = new VariableTable();

            if(pushdownQuery != null) {
               extraVars.put(PUSH_DOWN_QUERY, pushdownQuery.getSQLAsString());
            }

            key = AssetDataCache.getCacheKey(getTable(), box, null, mode, true,
               extraVars, vars);
         }
         catch(Exception ex) {
            // cache is not allowed
            LOG.debug("Failed to get cached table data", ex);
         }

         base = mexecuted || key == null ? null :
            AssetDataCache.getOrMarkExecutingOrWait(key, touchtime);

         if(AssetDataCache.isDebugData() && !(getTable() instanceof SnapshotEmbeddedTableAssembly)) {
            base = null;
         }

         if(base == null) {
            if(box.isDisposed()) {
               throw new RuntimeException("Asset query sandbox is disposed");
            }

            List<String> infos = XUtil.QUERY_INFOS.get();

            if(infos == null) {
               infos = new ArrayList<>();
               XUtil.QUERY_INFOS.set(infos);
            }

            if(infos.size() == 0) {
               infos.add(box.getWSName());
            }

            try {
               WSExecution.setAssetQuerySandbox(box);
               base = getRuntimeTableLens(vars);

               if(base == null) {
                  if(AssetQuerySandbox.isLiveMode(mode)) {
                     base = getDesignTableLens(vars);
                  }

                  return base;
               }

               if(key != null) {
                  base = AssetDataCache.setCachedData(key, base, getTable());
               }
            }
            catch(ConfirmException | CancelledException | MVExecutionException ex) {
               throw ex;
            }
            // Special handling case needed for when a script/sql expression
            // fails. Need to extract column name information and include in
            // error message
            catch(ExpressionFailedException ex) {
               Throwable expressionException = ex;

               if(AssetQuerySandbox.isLiveMode(mode) || AssetQuerySandbox.isRuntimeMode(mode)) {
                  List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

                  if(exs != null) {
                     int index = ex.getColIndex();
                     String table = ex.getTableName();
                     table = table == null ? getTable().getName() : table;

                     if(table != null && !table.equals("")) {
                        String vsm = Assembly.TABLE_VS + 'M';

                        // V_Mxxx_hart1
                        if(table.startsWith(vsm)) {
                           table = table.substring(vsm.length());
                           int point = table.lastIndexOf('_');

                           if(point >= 0) {
                              table = table.substring(0, point);
                           }
                        }
                        // V_xxx
                        else if(table.startsWith(Assembly.TABLE_VS)) {
                           table = table.substring(Assembly.TABLE_VS.length());
                        }

                        // xxx_O
                        table = VSUtil.stripOuter(table);
                     }

                     String column = ex.getColName();
                     String msg = null;

                     // This will happen for a javascript exception,
                     // where the column name will be present
                     if(column != null) {
                        msg = Catalog.getCatalog().
                           getString("viewer.worksheet.scriptError",
                                     column, table, ex.getMessage());
                     }
                     // This will happen for an SQL expression or other SQL
                     // error which does not provide column information
                     else if(table != null) {
                        msg = Catalog.getCatalog().
                           getString("viewer.worksheet.sqlFailed",
                                     table, ex.getMessage());
                     }
                     else {
                        msg = "Unknown expression error";
                     }

                     Exception exc = new Exception(msg, ex);
                     exs.add(exc);

                     expressionException = exc;
                  }

                  base = getDesignTableLens(vars);
               }
               else {
                  throw ex;
               }

               if(LOG.isDebugEnabled()) {
                  LOG.warn("Failed to get table data, expression failed: {}",
                           expressionException.getMessage(), expressionException);
               }
               else {
                  LOG.warn("Failed to get table data, expression failed: {}",
                           expressionException.getMessage());
               }

               if(!(ex.getOriginalException() instanceof SQLExpressionFailedException)) {
                  CoreTool.addUserWarning("expression failed: " + expressionException.getMessage());
               }
            }
            catch(CrossJoinCellCountBeyondLimitException ex) {
               throw ex;
            }
            catch(Exception ex) {
               Boolean throwEx = THROW_EXECUTE_EXCEPTION.get();

               // if in composer, return meta data so the error can be corrected
               if((throwEx == null || !throwEx) && AssetQuerySandbox.isLiveMode(mode) &&
                  !(ex instanceof ScriptException))
               {
                  List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

                  if(exs != null) {
                     exs.add(ex);
                  }

                  LOG.warn("Failed to get table data: " + ex.getMessage(), ex);
                  base = getDesignTableLens(vars);
               }
               else {
                  throw ex;
               }
            }
            finally {
               AssetDataCache.markExecutingFinished(key);
               WSExecution.setAssetQuerySandbox(null);
            }
         }
         else {
            fixColumnSelection(base, vars);
         }
      }

      return base;
   }

   /**
    * Get the runtime table lens.
    * @param vars the specified variable table.
    * @return the runtime data.
    */
   private TableLens getRuntimeTableLens(VariableTable vars) throws Exception {
      // merge the query
      merge(vars);

      QueryManager qmgr = box.getQueryManager();

      // get base table
      TableLens base = getBaseTableLens(vars);

      if(base == null) {
         return null;
      }

      // replace variables for post process
      getTable().replaceVariables(vars);

      if(isSQLite()) {
         // convert data to the target types to make sure
         // the later generated filter can work well.
         base = getColumnTypeFilter(base);
      }

      // get formula table
      base = getFormulaTableLens(base, vars);

      ColumnSelection cols = getTable().getColumnSelection(false);
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(base);

      // validate data types for they might be invalid
      if(getTable().getAggregateInfo().isEmpty() && !ginfo.isEmpty()) {
         for(int i = 0; base.moreRows(1) && i < cols.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) cols.getAttribute(i);

            // do not sync data type if an aggregate column
            if(ginfo.containsAggregate(column)) {
               continue;
            }

            int index = AssetUtil.findColumn(base, column, columnIndexMap);

            if(index >= 0) {
               String dtype = Tool.getDataType(base.getColType(index));

               // A string data type means that the column actually contains
               // strings or the data type is unknown. The default column type
               // is string, so this has the same effect as setting it when a
               // string or not setting it when unknown
               if(!XSchema.STRING.equals(dtype)) {
                  column.setDataType(dtype);
               }
            }
         }
      }
      else {
         ColumnSelection pub = getTable().getColumnSelection(true);
         validateDataTypes(base, cols, columnIndexMap);
         validateDataTypes(base, pub, columnIndexMap);
      }

      if(vars == null || !"true".equals(vars.get("calc_metadata"))) {
         // get pre condition table lens
         ConditionList preconds = getPreConditionList();
         base = getConditionTableLens(base, vars, preconds);
      }

      AggregateInfo group = getAggregateInfo();
      boolean ranking = true;

      if(group.isEmpty()) {
         // get distinct table lens
         base = getDistinctTableLens(base, vars);
      }

      // get sort table lens
      base = getSortTableLens(base, vars);

      if(base instanceof CancellableTableLens && qmgr != null) {
         qmgr.addPending(base);
      }

      if(!group.isEmpty()) {
         int gcount = group.getGroupCount();

         // summary?
         if(gcount > 0) {
            // get group, aggregate, post condition and ranking table lens
            base = getSummaryTableLens(base, vars);
            // get group sort table lens
            base = getSortSummaryTableLens(base, vars);
         }
         // table summary?
         else {
            // get aggregate table lens
            base = getTableSummaryTableLens(base, vars);
            // ranking is meaningless
            ranking = false;
         }
      }

      // get post condition list for crosstab and rotated table lens
      ConditionListWrapper wrapper = getPostConditionList();
      ConditionList conds = wrapper.getConditionList();
      base = getConditionTableLens(base, vars, conds);
      conds.removeAllItems();

      // ranking?
      if(ranking) {
         // get ranking table lens
         base = getRankingTableLens(base, vars);
      }

      // get visible table lens
      base = getVisibleTableLens(base, vars);

      // group? fix outer column selection
      if(!this.ginfo.isEmpty() && !this.ginfo.isCrosstab()) {
         ColumnSelection ncolumns = new ColumnSelection();
         ColumnSelection icolumns = getTable().getColumnSelection();

         for(int i = 0; i < base.getColCount(); i++) {
            ColumnRef column = AssetUtil.findColumn(base, i, icolumns);

            if(column == null) {
               continue;
            }

            final GroupRef groupRef = ginfo.getGroup(column);

            if(groupRef != null && groupRef.getNamedGroupAssembly() != null &&
               !groupRef.getNamedGroupAssembly().isEmpty())
            {
               column = (ColumnRef) column.clone();
               column.setDataType(XSchema.STRING);
            }

            ncolumns.addAttribute(column);
         }

         ColumnSelection columns = getTable().getColumnSelection(true);
         ncolumns.setProperty("public", "true");
         validateColumnSelection(ncolumns, columns, false, false, false, true);
         columns.setProperty("null", "false");
      }

      validateDataTypes(base, getTable().getColumnSelection(true), null);

      // get max row count table lens
      base = getMaxRowsTableLens(base, vars);

      // get format table lens
      base = getFormatTableLens(base, vars);

      CancellableTableLens cancelTable = (CancellableTableLens) Util.getNestedTable(
         base, CancellableTableLens.class);

      if(cancelTable != null && qmgr != null) {
         qmgr.addPending(cancelTable);
      }

      return base;
   }

   /**
    * Get the design mode table lens.
    * @param vars the specified variable table.
    */
   protected TableLens getDesignTableLens(VariableTable vars) throws Exception {
      AggregateInfo group = getAggregateInfo();
      boolean embedded = AssetQuerySandbox.isEmbeddedMode(mode);
      boolean pub = !group.isEmpty() || embedded || gmerged ||
         "true".equals(getTable().getProperty("metadata"));
      ColumnSelection columns = getTable().getColumnSelection(pub);
      boolean inVS = box.getViewsheetSandbox() != null;


      // is a composed table but not embedded? only expression will be shown
      if(!inVS && !embedded && AssetUtil.isHierarchical(getTable())) {
         ColumnSelection ncolumns = new ColumnSelection();

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) columns.getAttribute(i);

            if(column.isExpression()) {
               ncolumns.addAttribute(column);
            }
         }

         columns = ncolumns;
      }

      if(columns.getAttributeCount() == 0) {
         XEmbeddedTable table = new XEmbeddedTable(new String[0], new Object[0][0]);
         XTableLens designTable = new XTableLens(table);
         // Bug #41691, mark the table as the design table so that it can be detected in
         // AssetQuerySandbox when caching.
         designTable.setProperty(DESIGN_TABLE, true);
         return designTable;
      }

      // sort the column selection by the group order.
      if(group.isCrosstab() && !group.isEmpty()) {
         columns = columns.clone();
         sortColumnSelectionByGroup(columns, group);
      }

      XTypeNode output = getXTypeNode(columns);

      TableLens base = new XNodeMetaTable(output);
      TableDataDescriptor desc = base.getDescriptor();
      XNodeMetaTable.TableDataDescriptor2 desc2 =
         desc instanceof XNodeMetaTable.TableDataDescriptor2
         ? (XNodeMetaTable.TableDataDescriptor2) desc : null;

      for(int i = 0; i < base.getColCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);
         String id = getAttributeString(column);
         String header = AssetUtil.format(XUtil.getHeader(base, i));

         if(!Objects.equals(id, header) && id != null) {
            base.setColumnIdentifier(i, id);
         }

         if(desc2 != null) {
            desc2.setRefType(header, column.getRefType());
            desc2.setDefaultFormula(header, column.getDefaultFormula());
         }
      }

      // return a format table lens, so format will be applied, makes the design
      // table match the runtime table as possible, fix bug1260174571737
      ginfo = (AggregateInfo) getAggregateInfo().clone();
      base = getFormatTableLens(base, vars);
      return base;
   }

   private void sortColumnSelectionByGroup(ColumnSelection columns, AggregateInfo group) {
      GroupRef[] groups = group.getGroups();
      Map<String, Integer> groupIndexCache = new HashMap<>();
      Map<DataRef, Integer> columnIndexCache = new HashMap<>();

      columns.sortBy((a, b) -> {
         Integer indexA = groupIndexCache.get(a.getName());
         Integer indexB = groupIndexCache.get(b.getName());

         if(indexA == null || indexB == null) {
            for(int i = 0; i < groups.length; i++) {
               if(groups[i] == null) {
                  continue;
               }

               if(indexA == null && Tool.equals(groups[i].getName(), a.getName())) {
                  indexA = i;
                  groupIndexCache.put(groups[i].getName(), i);
               }

               if(indexB == null && Tool.equals(groups[i].getName(), b.getName())) {
                  indexB = i;
                  groupIndexCache.put(groups[i].getName(), i);
               }

               if(indexA != null && indexA >= 0 && indexB != null && indexB >= 0) {
                  return indexA - indexB;
               }

               if(indexA != null && indexB != null) {
                  break;
               }
            }
         }
         else if(indexA != null && indexA >= 0 && indexB != null && indexB >= 0) {
            return indexA - indexB;
         }

         indexA = indexA == null ? -1 : indexA;
         indexB = indexB == null ? -1 : indexB;
         groupIndexCache.put(a.getName(), indexA);
         groupIndexCache.put(b.getName(), indexB);

         // keep original order when has column do not find in the group.
         indexA = columnIndexCache.get(a);
         indexB = columnIndexCache.get(b);

         if(indexA == null) {
            indexA = columns.indexOfAttribute(a);
            columnIndexCache.put(a, indexA);
         }

         if(indexB == null) {
            indexB = columns.indexOfAttribute(b);
            columnIndexCache.put(b, indexB);
         }

         return indexA - indexB;
      });
   }

   protected XTypeNode getXTypeNode(ColumnSelection columns) {
      XTypeNode output = new TableTypeNode();
      output.setMinOccurs(0);
      output.setMaxOccurs(XTypeNode.STAR);

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(column.isHiddenParameter()) {
            continue;
         }

         XTypeNode node = column.getTypeNode();
         node = node == null ? new XTypeNode() : node;
         String name = column.getAlias();

         /*
          * @temp yanie: don't use caption since it is inconsistent
         if(name == null) {
            String caption = column.getCaption();

            if((column.getRefType() & DataRef.CUBE) == DataRef.CUBE &&
               caption != null)
            {
               name = caption;
            }
            else {
               name = column.getAttribute();
            }
         }
         */

         name = name == null ? column.getAttribute() : name;
         node.setName(name);
         output.addChild(node);
      }
      return output;
   }

   /**
    * Fixed the column selection if neccessary.
    */
   protected void fixColumnSelection(TableLens table) {
      fixColumnSelection(table, box.getVariableTable());
   }

   /**
    * Fixed the column selection if neccessary.
    */
   protected void fixColumnSelection(TableLens table, VariableTable vars) {
   }

   /**
    * Get the base table.
    * @param vars the specified variable table.
    * @return the base table of the query.
    */
   protected TableLens getBaseTableLens(VariableTable vars) throws Exception {
      ViewsheetSandbox vbox = box.getViewsheetSandbox();

      if(isSourceMergeable() && isMergePreferred()) {
         if(vbox == null) {
            return getMergedBaseTableLens(vars, null);
         }
         else {
            return (TableLens) ProfileUtils.addExecutionBreakDownRecord(
               vbox.getTopBoxID(), ExecutionBreakDownRecord.QUERY_EXECUTION_CYCLE,
               () -> getMergedBaseTableLens(vars, vbox.getID()));
         }
      }

      if(vbox == null) {
         return getPostBaseTableLens0(vars);
      }
      else {
         // for Feature #26586, add time record of data post processing for current vs.
         return (TableLens) ProfileUtils.addExecutionBreakDownRecord(
            vbox.getTopBoxID(), ExecutionBreakDownRecord.POST_PROCESSING_CYCLE,
            () -> getPostBaseTableLens0(vars));
      }
   }

   private TableLens getMergedBaseTableLens(VariableTable vars, String reportName)
      throws Exception
   {
      TableLens lens = getPreBaseTableLens(vars);

      if(lens != null) {
         lens.setProperty(XTable.REPORT_NAME, reportName);
         lens.setProperty(XTable.REPORT_TYPE, ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET);
      }

      return lens;
   }

   private int getAnalysisMaxrow() {
      String maxstr = getTable().getProperty("analysisMaxrow");

      if(maxstr == null) {
         return -1;
      }

      try {
         return Integer.parseInt(maxstr);
      }
      catch(Exception ex) {
         return -1;
      }
   }

   private TableLens getPostBaseTableLens0(VariableTable vars) throws Exception {
      TableLens table = getPostBaseTableLens(vars);
      table = getAnalysisMaxRowTableLens(table);

      if(table == null) {
         return null;
      }

      ViewsheetSandbox vbox = box.getViewsheetSandbox();
      table.setProperty(XTable.REPORT_NAME, vbox == null ? null : vbox.getID());
      table.setProperty(XTable.REPORT_TYPE, ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET);
      fixColumnSelection(table);

      return AssetUtil.applyAlias(getTable(), table);
   }

   /**
    * Get post analysis max row tablelens.
    */
   private TableLens getAnalysisMaxRowTableLens(TableLens table) {
      if(table == null) {
         return null;
      }

      int analysisMaxrow = getAnalysisMaxrow();

      if(analysisMaxrow == -1) {
         return table;
      }

      // apply analysis max row for wizard recommend logic, this should be done for base directly.
      table.setProperty("analysisMaxRowApplied", "true");
      return PostProcessor.maxrows(table, analysisMaxrow);
   }

   private TableLens getColumnTypeFilter(TableLens base) {
      if(base == null || base.getColCount() == 0) {
         return base;
      }

      ColumnSelection cols = getTable().getColumnSelection(false);
      ColumnSelection pubCols = getTable().getColumnSelection(true);
      String[] types = new String[base.getColCount()];

      for(int i = 0; i < base.getColCount(); i++) {
         Object obj = base.getObject(0, i);

         if(obj == null || "".equals(obj)) {
            continue;
         }

         String header = Tool.getDataString(obj);
         DataRef ref = cols.getAttribute(header);
         ref = ref == null ? pubCols.getAttribute(header) : ref;

         if(ref != null) {
            types[i] = ref.getDataType();
         }
      }

      return new ColumnTypeFilter(base, types);
   }

   /**
    * Get the formula table lens.
    * @param base the specified base table.
    * @param vars the specified variable table.
    * @return the formula table lens.
    */
   protected TableLens getFormulaTableLens(TableLens base, VariableTable vars) {
      ColumnSelection columns = getTable().getColumnSelection();
      List<String> scripts = new ArrayList<>();
      List<String> headers = new ArrayList<>();
      List<Boolean> mergeables = new ArrayList<>();
      List<String> ids = new ArrayList<>();
      List<Class<?>> types = new ArrayList<>();
      List<ColumnRef> cols = new ArrayList<>();
      List<FormulaHeaderInfo> hinfos = new ArrayList<>();
      int count = 0;
      formulaCols = null;

      if(base != null) {
         count = base.getColCount();
      }

      ColumnSelection columns0 = new ColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);
         columns0.addAttribute(column);

         if(!column.isExpression() || (column.isVisible() && column.isProcessed())) {
            continue;
         }

         if(column.isProcessed() && !column.isVisible()) {
            AggregateInfo info = getAggregateInfo();
            // group might require this column
            boolean contained = info != null && info.containsGroup(column);

            if(!contained) {
               ConditionList conds = getPreConditionList();

               // condition might require this column
               for(int j = 0; conds != null && j < conds.getSize(); j += 2) {
                  ConditionItem citem = conds.getConditionItem(j);
                  DataRef ref = citem.getAttribute();

                  if(column.equals(ref)) {
                     contained = true;
                     break;
                  }
               }
            }

            if(!contained) {
               continue;
            }
         }

         // column might be executed
         int col = AssetUtil.findColumn(base, column, true);

         if(col >= 0) {
            boolean dateRange = column.getDataRef() instanceof DateRangeRef;

            // for regular expression column, we check for duplicate column name so it's
            // impossible to create an expression with the same name as the base column.
            // for date range, it's possible. for example, a table of 'Date' and 'Month(Date)'
            // column, a mirror can create a month date range of 'Date' column, which has
            // the same name as 'Month(Date)'. in this case, the date range should be
            // calculated again.
            if(!dateRange) {
               continue;
            }
         }

         if((column.getRefType() & DataRef.CUBE) == DataRef.CUBE) {
            DataRef dref = getBaseAttribute(column);

            if(dref instanceof NamedRangeRef) {
               columns0.removeAttribute(column);
               String name0 = ((NamedRangeRef) dref).getDataRef().getName();
               AttributeRef ref0 = new AttributeRef(name0);
               ref0.setRefType(column.getRefType());
               columns0.addAttribute(ref0);
            }
         }

         addExpression(column, columns, scripts, headers, ids, types, cols, hinfos, mergeables);
      }

      if(scripts.size() > 0) {
         String[] harr = headers.toArray(new String[0]);
         String[] sarr = scripts.toArray(new String[0]);
         Boolean[] marr = mergeables.toArray(new Boolean[0]);
         AssetQueryScope scope = box.getScope();
         scope.setVariableTable(vars);
         scope.setMode(mode);
         TableAssembly table = getTable();
         ScriptEnv env = box.getScriptEnv();
         boolean cube = AssetUtil.isCubeTable(table);

         // the special treatment for column header should only be necessary
         // for cubes. adding VSCubeTableLens would break the RDD chain and
         // causes spark post-processing to not be used
         if(cube && columns0.getAttributeCount() > 0) {
            base = new VSCubeTableLens(base, columns0);
         }

         ColumnIndexMap columnIndexMap = new ColumnIndexMap(base, true);

         // fix original name as table header
         for(FormulaHeaderInfo hinfo : hinfos) {
            String oname = hinfo.getOriginalColName();
            int col = Util.findColumn(columnIndexMap, oname);

            if(col >= 0) {
               Object header = Util.getHeader(base, col);

               if(header != null) {
                  hinfo.setOriginalColName(header.toString());
               }
            }
         }

         boolean[] restricted = new boolean[sarr.length];

         for(int i = 0; i < sarr.length; i++) {
            restricted[i] = "true".equals(table.getProperty("adhoc.edit." + harr[i]));
         }

         base = PostProcessor.formula(
            base, harr, sarr, env, box.getScope(), marr,
            getTable().getName(), hinfos, types, restricted);
         formulaCols = new HashSet<>(Arrays.asList(harr));

         for(int i = 0; i < sarr.length; i++) {
            String header = AssetUtil.format(XUtil.getHeader(base, i + count));
            String id = ids.get(i);
            id = getColumnIdentifier(id, i >= cols.size() ? null :
                                     cols.get(i), isBCSpecial());

            // set column identifier
            if(!id.equals(header)) {
               base.setColumnIdentifier(i + count, id);
            }
         }
      }

      return base;
   }

   /**
    * Add expression.
    */
   private void addExpression(ColumnRef column, ColumnSelection columns,
                              List<String> scripts, List<String> headers, List<String> ids,
                              List<Class<?>> types, List<ColumnRef> cols,
                              List<FormulaHeaderInfo> hinfos,
                              List<Boolean> mergetables)
   {
      String name = column.getAlias();
      name = name == null ? column.getAttribute() : name;
      DataRef bref = getBaseAttribute(column);

      if(!(bref instanceof ExpressionRef)) {
         return;
      }

      ExpressionRef exp = (ExpressionRef) bref;

      if(exp.isVirtual()) {
         return;
      }

      @SuppressWarnings("unchecked")
      Enumeration<DataRef> iterator = column.getExpAttributes();
      Set<String> added = new HashSet<>();
      added.add(column.getName());

      while(iterator.hasMoreElements()) {
         DataRef ref = iterator.nextElement();
         int index = columns.indexOfAttribute(ref);

         if(ref == null || added.contains(ref.getName())) {
            continue;
         }

         final ColumnRef col = new ColumnRef(ref);
         XMetaInfo meta = getXMetaInfo(col, col);

         if(meta != null && needKeepMetaInfo(column)) {
            hinfos.add(new FormulaHeaderInfo(column.getName(), ref.getName(),
                                                       true, meta));
         }

         if(index >= 0) {
            ColumnRef column2 = (ColumnRef) columns.getAttribute(index);

            if(column2.isExpression() && !column2.isVisible()) {
               DataRef dref = getBaseAttribute(column2);

               if(!(dref instanceof ExpressionRef)) {
                  continue;
               }

               ExpressionRef exp2 = (ExpressionRef) dref;

               if(!exp2.isSQL()) {
                  String id = getAttributeString(column2);

                  if(ids.contains(id)) {
                     continue;
                  }

                  addExpression(column2, columns, scripts, headers, ids, types,
                                cols, hinfos, mergetables);
                  added.add(column2.getName());
               }
            }
         }
      }

      boolean sql = column.isSQL();
      scripts.add(ExpressionRef.getScriptExpression(sql, exp.getScriptExpression()));
      headers.add(name);
      cols.add(column);

      if(column.isSQL()) {
         ExpressionRef expRef = (ExpressionRef) column.getDataRef();
         String expression = "";

         try {
            expression = expRef.getExpression();
         }
         catch(UnsupportedOperationException ex) {
             // ignore it
         }

         String prefix = "/*script";
         String suffix = "script*/";
         String lowexp = expression.toLowerCase();
         int from = lowexp.indexOf(prefix);
         int to = lowexp.indexOf(suffix);

         try {
            if(!(isSourceMergeable() && isMergePreferred()) && (from < 0 || to < from)) {
               mergetables.add(false);
            }
            else {
               mergetables.add(true);
            }
         }
         catch(Exception e) {
            mergetables.add(true);
         }
      }
      else {
         mergetables.add(true);
      }

      if(exp instanceof DateRangeRef) {
         DateRangeRef rangeRef = (DateRangeRef) exp;
         types.add(rangeRef.getDataClass());
         DataRef attr = rangeRef.getDataRef();
         // always for ColumnRef wrap a DateRangeRef, the ColumnRef.getName()
         // is same as DateRangeRef.getName(), but bc may make it not equals
         // fix bug1261566693054
         String fname = column.getName();
         String oname = attr == null ? null : attr.getName();
         hinfos.add(new FormulaHeaderInfo(fname, oname, rangeRef.isAutoCreate(),
            getDefaultMetaInfo(rangeRef, column)));
      }
      else {
         Class<?> clazz = Tool.getDataClass(column.getDataType());

         if(column.getDataRef() != null && column.getDataRef().isDataTypeSet()) {
            // column.getDataRef() is the expression ref so it's type should be used. (50644)
            clazz = Tool.getDataClass(column.getDataRef().getDataType());
         }

         addDCExpressionInfo(column, hinfos, name);
         types.add(clazz);
      }

      ids.add(getAttributeString(column));
   }

   // add date comparison special expression meta info.
   private static void addDCExpressionInfo(ColumnRef column, List<FormulaHeaderInfo> hinfos,
                                           String name)
   {
      if(column instanceof CalculateRef && ((CalculateRef) column).isDcRuntime()) {
         if(name.startsWith("WeekOfYear(")) {
            XMetaInfo metaInfo = new XMetaInfo();
            metaInfo.setXFormatInfo(new XFormatInfo(TableFormat.DECIMAL_FORMAT, "MMW"));
            hinfos.add(new FormulaHeaderInfo(column.getName(), null, true, metaInfo));
         }
      }
   }

   /**
    * Get default XMetaInfo for a DateRangeRef formula.
    */
   private XMetaInfo getDefaultMetaInfo(DateRangeRef ref, ColumnRef column) {
      if(ref == null) {
         return null;
      }

      // always create a empty meta info, no matter whether the date level
      // has default format, so if the level has no format, such as DayOfMonth,
      // it will use null format but not the original column's format
      XMetaInfo info = new XMetaInfo();
      info = fixMetaInfo(info, ref, column);
      return info;
   }

   private boolean needKeepMetaInfo(ColumnRef column) {
      if(column.getDataRef() instanceof DateRangeRef &&
         ((DateRangeRef) column.getDataRef()).getDateOption() != DateRangeRef.NONE_INTERVAL)
      {
         return false;
      }

      if(VSUtil.isPreparedCalcField(column) && column.getName().startsWith("Range@")) {
         return false;
      }

      // PreAssetQuery.getRefForXMetaInfo gets the meta info for calc field from the
      // referenced columns. this logic does the same if the calc field is pushed to
      // a FormulaTableLens. (53346)
      return !(column instanceof CalculateRef) || !((CalculateRef) column).isDcRuntime();
   }

   /**
    * Get the condition table lens.
    * @param base the specified base table.
    * @param vars the specified variable table.
    * @return the pre condition table lens.
    */
   protected TableLens getConditionTableLens(TableLens base, VariableTable vars,
                                             ConditionList conds)
   {
      if(conds == null || conds.getSize() == 0) {
         return base;
      }

      conds.replaceVariables(vars);
      AssetConditionGroup cgroup = new AssetConditionGroup(base, conds, mode, box, touchtime);

      return PostProcessor.filter(base, cgroup);
   }

   /**
    * Get the sort table lens.
    * @param base the specified base table.
    * @param vars the specified variable table.
    * @return the sort table lens.
    */
   protected TableLens getSortTableLens(TableLens base, VariableTable vars) throws Exception {
      SortInfo info = getTable().getSortInfo();
      SortRef[] sorts = info.getSorts();
      List<Comparer> comparers = new ArrayList<>();
      List<Integer> cols = new ArrayList<>();
      List<Boolean> orders = new ArrayList<>();
      AggregateInfo ginfo = getAggregateInfo();

      // do not apply sorting to save time
      if(ginfo.isCrosstab()) {
         return base;
      }

      // grouping in spark doen't require base being sorted
      /* this optimization may cause sorting to be lost when grouping is
         not done later (8007)
      if(!ginfo.isEmpty() && PostProcessor.isSparkProcessing(true, base)) {
         return base;
      }
      */

      ColumnIndexMap columnIndexMap = new ColumnIndexMap(base);

      for(SortRef sort : sorts) {
         DataRef attr = sort.getDataRef();
         int col = AssetUtil.findColumn(base, attr, columnIndexMap);
         int order = sort.getOrder();

         // do not sort if sort none
         if(order == XConstants.SORT_NONE || order == XConstants.SORT_ORIGINAL) {
            continue;
         }

         if(col < 0) {
            LOG.warn("Sorting column not found: {}", attr);
            continue;
         }

         GroupRef group = ginfo.getGroup(attr);

         // only apply sorting to a group column to save time
         if(group == null && !ginfo.isEmpty()) {
            continue;
         }

         SortOrder comp = (attr.getRefType() & DataRef.CUBE) != DataRef.CUBE ?
            createDateSortOrder(group, order, false) :
            new DimensionSortOrder(order, attr.getRefType(),
                                   Util.getColumnComparator(base, attr));

         comparers.add(comp);
         cols.add(col);
         orders.add(order == XConstants.SORT_ASC);
      }

      if(comparers.size() == 0) {
         return base;
      }

      int[] carr = new int[cols.size()];
      boolean[] sarr = new boolean[cols.size()];

      for(int i = 0; i < carr.length; i++) {
         carr[i] = cols.get(i);
         sarr[i] = orders.get(i);
      }

      return PostProcessor.sort(base, carr, sarr, comparers);
   }

   /**
    * Get the summary table lens.
    * @param base the specified base table.
    * @param vars the specified variable table.
    * @return the summary table lens.
    */
   protected TableLens getSummaryTableLens(TableLens base, VariableTable vars) throws Exception {
      AggregateInfo info = getAggregateInfo();
      List<Integer> glist = new ArrayList<>();
      List<SortOrder> olist = new ArrayList<>();
      List<OrderInfo> sortByVals = new ArrayList<>(); // sort by value
      List<SortOrder> colist = new ArrayList<>();
      List<Integer> slist = new ArrayList<>();
      List<Formula> flist = new ArrayList<>();
      GroupRef[] groups = info.getGroups();
      AggregateRef[] aggregates = info.getAggregates();
      SortInfo sinfo = getTable().getSortInfo();
      SQLHelper helper = AssetUtil.getSQLHelper(getTable());
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(base);

      for(GroupRef group : groups) {
         DataRef attr = group.getDataRef();
         attr = findColumn(attr);
         int col = AssetUtil.findColumn(base, attr, columnIndexMap);

         if(col < 0) {
            LOG.warn("Group column not found: {}", attr);
            continue;
         }

         glist.add(col);
         SortRef sort = sinfo.getSort(attr);

         // try sort
         if(sort == null && info.isCrosstab()) {
            sort = this.sinfo.getSort(attr);
         }

         int type = sort == null ? XConstants.SORT_ASC : sort.getOrder();
         SortOrder order = createSortOrder(group, type, 0);
         olist.add(order);
         order = createSortOrder(group, type, col);
         colist.add(order);
         sortByVals.add(group.getOrderInfo());
      }

      List<Integer> clist = new ArrayList<>();
      TableLens obase = base;
      int ccnt = base.getColCount();

      for(int i = 0; i < ccnt; i++) {
         clist.add(i);
      }

      OUTER:
      for(AggregateRef aref : aggregates) {
         // composite aggregate for single column
         if(aref instanceof CompositeAggregateRef) {
            CompositeAggregateRef cref = (CompositeAggregateRef) aref;
            AggregateFormula formula = cref.getFormula();
            DataRef attr = cref.getDataRef();
            DataRef attr2 = cref.getSecondaryColumn();
            Iterator iterator = cref.getChildAggregates().iterator();
            List<Integer> cols = new ArrayList<>();

            while(iterator.hasNext()) {
               AggregateRef aggregate = (AggregateRef) iterator.next();
               DataRef baseRef = aggregate.getDataRef();
               int col = AssetUtil.findColumn(base, baseRef, columnIndexMap);

               if(col < 0) {
                  LOG.warn("Composite aggregate column not found: {}", baseRef);
                  continue OUTER;
               }

               Integer iobj = col;

               if(!cols.contains(iobj)) {
                  cols.add(iobj);
               }
            }

            int[] carr = new int[cols.size()];

            for(int j = 0; j < carr.length; j++) {
               carr[j] = cols.get(j);
            }

            Formula form = cref.getCompositeFormula(carr);

            // for a composite aggregate formula, the first column will be
            // renamed to the name of the composite aggregate ref. Here we
            // create a new column, and then rename it, in order that the
            // other aggregate formulae could use the column as well
            if(form != null) {
               clist.add(carr[0]);
               int[] arr = new int[clist.size()];

               for(int j = 0; j < arr.length; j++) {
                  arr[j] = clist.get(j);
               }

               TableLens nbase = base;

               base = PostProcessor.mapColumn(obase, arr);

               for(int j = ccnt; j < arr.length - 1; j++) {
                  Object nobj = nbase.getObject(0, j);
                  Object oobj = base.getObject(0, j);

                  if(!Tool.equals(nobj, oobj)) {
                     base.setObject(0, j, nobj);
                  }
               }

               carr[0] = arr.length - 1;
               // rebuild formula basing on the newly created ColumnMapFilter
               form = cref.getCompositeFormula(carr);
            }
            else {
               form = getFormula1(new AggregateRef(attr, attr2, formula), base, helper,
                                  aggregates, vars);
            }

            slist.add(carr[0]);
            flist.add(form);

            ColumnRef column = (attr instanceof ColumnRef) ? (ColumnRef) attr : null;
            String name = column == null ? null : column.getAlias();
            name = name != null && name.length() > 0 ? name : attr.getAttribute();
            base.setObject(0, carr[0], name);
         }
         // single aggregate for single column
         else {
            DataRef attr = aref.getDataRef();
            boolean isAggCalc = VSUtil.isAggregateCalc(attr);
            attr = findColumn(attr);
            int col = AssetUtil.findColumn(base, attr, columnIndexMap);

            if(col < 0 && !isAggCalc) {
               LOG.warn("Aggregate column not found: {}", attr);
               continue;
            }

            Formula form = getFormula1(aref, base, helper, aggregates, vars);

            if(isAggCalc) {
               assert form != null;
               int[] col2 = ((Formula2) form).getSecondaryColumns();

               if(col2.length > 0) {
                  slist.add(col2[0]);
               }
               else {
                  // if the calc aggr doesn't use any base column, just add
                  // any col since slist has to be same length as flist
                  slist.add(0);
               }
            }
            else {
               slist.add(col);
            }

            flist.add(form);
         }
      }

      int[] sarr = new int[slist.size()];
      Formula[] farr = new Formula[flist.size()];

      for(int i = 0; i < slist.size(); i++) {
         sarr[i] = slist.get(i);
         farr[i] = flist.get(i);
      }

      // no group column?
      if(glist.size() == 0) {
         return base;
      }

      // normal summary?
      if(!info.isCrosstab()) {
         int[] garr = new int[glist.size()];
         boolean sorted = false;

         for(int i = 0; i < glist.size(); i++) {
            garr[i] = glist.get(i);
         }

         boolean groupOnFormula = false;
         UniformSQL sql = getUniformSQL();
         OrderByItem[] orderItems = sql == null ? null : sql.getOrderByItems();
         boolean mergedSortBy = orderItems != null && orderItems.length != 0;

         for(int i = 0; i < groups.length; i++) {
            if(formulaCols != null && formulaCols.contains(groups[i].getName())) {
               groupOnFormula = true;
               break;
            }
         }

         // @by larryl, base must be sorted for SummaryFilter to work properly
         // (make sure test named group (e.g. color) in vs chart if removed).
         // If sorting is not defined but there is grouping, that means query result
         // is not sorted so we need to sort it before feeding into SummaryFilter.
         //
         // if group is on formula column, the table may no longer be sorted.
         boolean needSort = !isSortInfoMergeable0(sinfo) || groupOnFormula||
            !mergedSortBy && sinfo.getSortCount() < garr.length;
         boolean[] orders = null;
         List<Comparer> comparers = null;

         if(needSort && base instanceof SortedTable) {
            int[] sortcols = ((SortedTable) base).getSortCols();

            if(sortcols.length >= garr.length &&
               Arrays.equals(Arrays.copyOf(sortcols, garr.length), garr))
            {
               needSort = false;
            }
            // use the sorting order from base if base is partially sorted.
            // otherwise the sorting selected on table ui will be lost.
            else {
               boolean[] orders0 = ((SortedTable) base).getOrders();
               comparers = new ArrayList<>();
               orders = new boolean[garr.length];

               for(int i = 0; i < garr.length; i++) {
                  orders[i] = true;
               }

               for(int i = 0; i < garr.length; i++) {
                  int idx = -1;

                  for(int k = 0; k < sortcols.length; k++) {
                     if(sortcols[k] == garr[i]) {
                        idx = k;
                        break;
                     }
                  }

                  if(idx >= 0) {
                     orders[i] = orders0[idx];
                     comparers.add(((SortedTable) base).getComparer(idx));
                  }
                  else {
                     comparers.add(null);
                  }
               }
            }
         }

         if(needSort) {
            base = PostProcessor.sort(base, garr, orders, comparers);
            sorted = true;
         }

         // apply post condition
         ConditionListWrapper wrapper = getPostConditionList();
         ConditionList conds = wrapper.getConditionList();
         ConditionGroup cgroup = (mexecuted || conds.getSize() == 0) ? null :
            new AssetConditionGroup2(base, conds, mode, box, glist, slist);
         conds.removeAllItems();

         List<String> mheaders = getAggCalcHeader(farr, aggregates);
         String[] mhdrs = mheaders.toArray(new String[] {});
         boolean cube = AssetUtil.isCubeTable(AssetQuery.this.getTable());
         ConditionListWrapper ranking = getRankingConditionList();
         boolean specialGrouping = olist.stream()
            .anyMatch(order -> order.isSpecific() || order.isDatePostProcess());
         TableLens preTbl = null;

         // ranking condition is applied in SummaryFilter, and may need to aggregate
         // the details to check ranking condition, can't pre-aggregate
         if(ranking.isEmpty() && !specialGrouping) {
            preTbl = PostProcessor.preSummarize(base, garr, sarr, farr);
         }

         if(preTbl != null) {
            base = preTbl;
            // spark aggregate result may not be sorted
            //sorted = true;

            Formula[] farr2 = new Formula[farr.length];
            garr = new int[garr.length];
            sarr = new int[sarr.length];

            // already aggregated, changed to no-op
            for(int i = 0; i < farr.length; i++) {
               if(farr[i] != null) {
                  farr2[i] = new NoneFormula();
               }

               // index based on summary table
               sarr[i] = i + garr.length;
            }

            farr = farr2;

            glist.clear();

            // group index from 0
            for(int i = 0; i < garr.length; i++) {
               garr[i] = i;
               glist.add(i);
            }

            // create SummaryFilter2 since additional (e.g. topN, asset
            // condition) is not supported in Spark

            // spark aggregate is not sorted so we should sort the result. (50275)
            sorted = false;
            needSort = true;
         }

         boolean timeSeries = false;
         GroupRef groupRef = groups[groups.length - 1];

         if(groupRef != null) {
            timeSeries = groupRef.isTimeSeries();
         }

         // if not grouped and sorted, need to sort it to pass to SummaryFilter
         if(!sorted && needSort) {
            base = PostProcessor.sort(base, garr, null, null);
         }

         int timeSeriesLevel = getGroupDateLevel(groupRef);
         SummaryFilter2 stable = new SummaryFilter2(base, garr, sarr, farr, cube, cgroup, mhdrs);
         stable.setTimeSeries(timeSeries);
         stable.setTimeSeriesLevel(timeSeriesLevel);
         stable.setSortOthersLast(groupRef.isSortOthersLast());
         Map<String, Integer> smap = new HashMap<>();

         // init header
         if(stable.moreRows(0)) {
            for(int c = 0; c < sarr.length; c++) {
               String header = Util.getHeader(stable, c + garr.length).toString();
               smap.put(header, c);
            }
         }

         // apply sort order
         for(int i = 0; i < olist.size(); i++) {
            SortOrder order = olist.get(i);

            if(order != null) {
               stable.setGroupOrder(garr[i], order);
            }

            // apply sort by value
            OrderInfo sortByVal = sortByVals.get(i);

            if(sortByVal != null && sortByVal.isSortByVal()) {
               int sindex = sortByVal.getSortByCol();

               if(sindex < 0) {
                  String scol = sortByVal.getSortByColValue();

                  if(scol != null) {
                     Integer colInt = smap.get(scol);
                     sindex = colInt == null ? -1 : colInt;
                  }
               }

               if(sindex >= 0) {
                  stable.setSortByValInfo(i, sindex, sortByVal.isSortByValAsc());
               }
            }
         }

         // apply ranking

         ranking = getRankingConditionList();

         if(ranking != null) {
            ranking.replaceVariables(vars);
         }

         assert ranking != null;
         conds = ranking.getConditionList();
         boolean rankingApplied = false;

         for(int i = 0; i < conds.getSize(); i += 2) {
            ConditionItem titem = conds.getConditionItem(i);
            RankingCondition tcond = (RankingCondition) titem.getXCondition();
            DataRef attr = titem.getAttribute();
            DataRef aggregate = tcond.getDataRef() != null ? tcond.getDataRef() : attr;
            int gcol = AssetUtil.findColumn(base, attr, columnIndexMap);

            if(gcol < 0) {
               LOG.warn("Group ranking column not found: {}", attr);
               continue;
            }

            int gindex = glist.indexOf(gcol);

            if(gindex < 0) {
               continue;
            }

            int scol = AssetUtil.findColumn(base, aggregate, columnIndexMap);
            int sindex = -1;

            if(scol < 0) {
               for(int m = 0; m < aggregates.length; m++) {
                  if(aggregates[m].getDataRef().getAttribute().equals(aggregate.getAttribute())) {
                     sindex = m;
                  }
               }

               if(sindex < 0) {
                  LOG.warn("Aggregate ranking column not found: {}", aggregate);
                  continue;
               }
            }
            else {
               sindex = slist.indexOf(scol);
            }

            if(sindex < 0) {
               continue;
            }

            Object n = tcond.getN();

            if(!(n instanceof Number)) {
               LOG.warn("Invalid ranking n found in summary table: {}", n);
               continue;
            }

            boolean reverse = tcond.getOperation() != XCondition.TOP_N;
            stable.setTopN(gindex, sindex, ((Number) n).intValue(), reverse,
                           false, tcond.isGroupOthers());
            stable.putGroupRef(gindex, attr);
            rankingApplied = true;
         }

         // @by davyc, clear ranking conditions if ranking applied
         // fix bug1301373101475
         // only ranking is applied correctly, clear ranking
         // bug1302773987736
         if(rankingApplied) {
            conds.removeAllItems();
         }

         return stable;
      }
      // crosstab summary?
      else {
         if(slist.size() == 0 || glist.size() < 1) {
            return base;
         }

         int[] colh = new int[1];
         int[] rowh = new int[glist.size() - 1];
         int lastRowDateLevel = XConstants.NONE_DATE_GROUP;
         int lastColDateLevel = XConstants.NONE_DATE_GROUP;
         boolean rowTimeseries = false;
         boolean colTimeseries = false;

         for(int i = 0; i < glist.size(); i++) {
            if(i == 0) {
               colh[i] = glist.get(i);
               lastColDateLevel = getGroupDateLevel(groups[i]);
               colTimeseries = groups[i].isTimeSeries();
            }
            else {
               rowh[i - 1] = glist.get(i);
               lastRowDateLevel = getGroupDateLevel(groups[i]);
               rowTimeseries = groups[i].isTimeSeries();
            }
         }

         CrossTabFilter stable = new CrossTabFilter(base, rowh, colh, sarr, farr,
            rowTimeseries, colTimeseries, lastRowDateLevel, lastColDateLevel);
         stable.setSuppressRowGrandTotal(true);
         stable.setSuppressColumnGrandTotal(true);
         stable.setKeepColumnHeaders(true);
         ConditionListWrapper rwrapper = getRankingConditionList();

         if(rwrapper != null) {
            rwrapper.replaceVariables(vars);
         }

         ConditionList rconds = rwrapper == null ? null : rwrapper.getConditionList();

         if(glist.size() == groups.length && slist.size() == aggregates.length) {
            for(int i = 0; i < glist.size(); i++) {
               CrosstabRanking rank = createCrosstabRanking(i, groups, aggregates, rconds);

               if(i == 0 && rank != null) {
                  stable.setColTopN(i, rank.didx, rank.topn, rank.reverse, false);
               }
               else if(rank != null) {
                  stable.setRowTopN(i - 1, rank.didx, rank.topn, rank.reverse, false);
               }
            }

            if(rconds != null) {
               rconds.removeAllItems();
            }
         }

         // apply sort order
         for(int i = 0; i < colist.size(); i++) {
            SortOrder order = colist.get(i);

            if(order != null) {
               if(i == 0) {
                  stable.setColHeaderComparer(i, order);
               }
               else {
                  stable.setRowHeaderComparer(i - 1, order);
               }
            }
         }

         // format header, fix outer column selection and set column identifier
         ColumnSelection ncolumns = new ColumnSelection();
         int cfmt = getDateGroup(groups[0]);

         for(int i = 0; i < stable.getColCount(); i++) {
            // row header?
            if(i < rowh.length) {
               ColumnRef column = (ColumnRef) groups[i + 1].getDataRef();
               column = findColumn(column);

               if(column.isVisible()) {
                  ncolumns.addAttribute(column);
               }

               String id = getAttributeString(column);
               id = getColumnIdentifier(id, column, isBCSpecial());
               String header = AssetUtil.format(XUtil.getHeader(stable, i));

               // set column identifier
               if(!id.equals(header)) {
                  stable.setColumnIdentifier(i, id);
               }
            }
            // dynamic header?
            else {
               Object obj = XUtil.getHeader(stable, i);

               if(cfmt != XConstants.NONE_DATE_GROUP &&
                  (cfmt & XConstants.PART_DATE_GROUP) == 0 &&
                  obj instanceof Date)
               {
                  Format fmt = XUtil.getDefaultDateFormat(cfmt,
                     AssetUtil.getOriginalType(groups[0]));

                  if(fmt != null) {
                     stable.setObject(0, i, fmt.format(obj));
                  }
               }

               String header = AssetUtil.format(XUtil.getHeader(stable, i));
               String dtype = Tool.getDataType(stable.getColType(i));
               AttributeRef attr = new AttributeRef(header);
               ColumnRef column = new ColumnRef(attr);
               column.setDataType(dtype);
               ncolumns.addAttribute(column);
            }
         }

         ColumnSelection columns = getTable().getColumnSelection(true);
         ncolumns.setProperty("public", "true");
         validateColumnSelection(ncolumns, columns, false, false, false, false);

         return stable;
      }
   }

   /**
    * Get date level of group.
    */
   private int getGroupDateLevel(GroupRef group) {
      int dateLevel = -1;
      DataRef ref = group.getDataRef();
      ref = ((ColumnRef) ref).getDataRef();

      if(ref instanceof DateRangeRef) {
         dateLevel = ((DateRangeRef) ref).getDateOption();
      }

      return dateLevel;
   }

   /**
    * Create crosstab ranking.
    */
   private CrosstabRanking createCrosstabRanking(int idx, GroupRef[] groups,
      AggregateRef[] aggrs, ConditionList rconds)
   {
      ConditionItem citem = findRanking(groups[idx], rconds);

      if(citem == null) {
         return null;
      }

      RankingCondition tcondition = (RankingCondition) citem.getXCondition();
      DataRef aggregate = tcondition.getDataRef() != null ?
         tcondition.getDataRef() : citem.getAttribute();
      int didx = -1;

      for(int i = 0; i < aggrs.length; i++) {
         if(aggrs[i].equals(aggregate)) {
            didx = i;
            break;
         }
      }

      if(didx < 0) {
         return null;
      }

      Object n = tcondition.getN();

      if(!(n instanceof Number)) {
         LOG.warn("Invalid ranking n found in crosstab table: {}", n);
         return null;
      }

      boolean reverse = tcondition.getOperation() != XCondition.TOP_N;
      return new CrosstabRanking(didx, ((Number) n).intValue(), reverse);
   }

   /**
    * Find ranking.
    */
   private ConditionItem findRanking(GroupRef group, ConditionList rconds) {
      if(rconds == null) {
         return null;
      }

      for(int i = 0; i < rconds.getSize(); i += 2) {
         ConditionItem citem = rconds.getConditionItem(i);
         DataRef ref = citem.getAttribute();

         if(group.getDataRef().equals(ref)) {
            return citem;
         }
      }

      return null;
   }

   /**
    * Get the sort summary table lens.
    * @param base the specified base table.
    * @param vars the specified variable table.
    * @return the sort summary table lens.
    */
   protected TableLens getSortSummaryTableLens(TableLens base, VariableTable vars) throws Exception
   {
      AggregateInfo ginfo = getAggregateInfo();
      SortRef[] sorts = this.sinfo.getSorts();
      List<Integer> cols = new ArrayList<>();
      List<SortOrder> comps = new ArrayList<>();
      List<Boolean> orders = new ArrayList<>();
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(base);
      boolean saggregated = false;

      for(SortRef sort : sorts) {
         DataRef attr = sort.getDataRef();
         int col = AssetUtil.findColumn(base, attr, columnIndexMap);

         if(col < 0) {
            continue;
         }

         int order = sort.getOrder();
         GroupRef group = ginfo.getGroup(sort);
         AggregateRef aggregate = ginfo.getAggregate(sort);

         if(group != null) {
            SortOrder comp = createDateSortOrder(group, order, false);
            cols.add(col);
            comps.add(comp);
            orders.add(order == XConstants.SORT_ASC);
         }
         else if(aggregate != null) {
            cols.add(col);
            comps.add(null);
            orders.add(order == XConstants.SORT_ASC);
            saggregated = true;
         }
      }

      // no aggregate sort specified? sorting is already done in SummaryFilter
      if(cols.size() == 0 || !saggregated && getCubeType() != null) {
         return base;
      }

      int[] carr = new int[cols.size()];
      boolean[] sarr = new boolean[cols.size()];

      for(int i = 0; i < carr.length; i++) {
         carr[i] = cols.get(i);
         sarr[i] = orders.get(i);
      }

      return PostProcessor.sort(base, carr, sarr, comps);
   }

   /**
    * Get the table summary table lens.
    * @param base the specified base table.
    * @param vars the specified variable table.
    * @return the table summary table lens.
    */
   protected TableLens getTableSummaryTableLens(TableLens base, VariableTable vars) {
      List<Integer> slist = new ArrayList<>();
      List<Formula> flist = new ArrayList<>();
      AggregateInfo info = getAggregateInfo();
      AggregateRef[] aggregates = info.getAggregates();
      boolean def = aggregates.length > 0;

      for(AggregateRef aggr : aggregates) {
         if(aggr.getFormula() != null) {
            def = false;
            break;
         }
      }

      // not aggregated? do nothing
      if(def) {
         return base;
      }

      SQLHelper helper = AssetUtil.getSQLHelper(getTable());
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(base);

      OUTER:
      for(AggregateRef aref : aggregates) {
         // composite aggregate for single column
         if(aref instanceof CompositeAggregateRef) {
            CompositeAggregateRef cref = (CompositeAggregateRef) aref;
            AggregateFormula formula = cref.getFormula();
            DataRef attr = cref.getDataRef();
            Iterator iterator = cref.getChildAggregates().iterator();
            List<Integer> cols = new ArrayList<>();

            while(iterator.hasNext()) {
               AggregateRef aggregate = (AggregateRef) iterator.next();
               DataRef attr2 = aggregate.getDataRef();
               int col = AssetUtil.findColumn(base, attr2, columnIndexMap);

               if(col < 0) {
                  LOG.warn("Composite aggregate column not found in table summary: {}", attr2);
                  continue OUTER;
               }

               Integer iobj = col;

               if(!cols.contains(iobj)) {
                  cols.add(iobj);
               }
            }

            int[] carr = new int[cols.size()];

            for(int j = 0; j < carr.length; j++) {
               carr[j] = cols.get(j);
            }

            Formula form = cref.getCompositeFormula(carr);

            if(form == null) {
               form = getFormula1(new AggregateRef(attr, formula), base, helper, aggregates, vars);
            }

            slist.add(carr[0]);
            flist.add(form);

            ColumnRef column = (attr instanceof ColumnRef) ? (ColumnRef) attr : null;
            String name = column == null ? null : column.getAlias();
            name = name != null && name.length() > 0 ? name : attr.getAttribute();
            base.setObject(0, carr[0], name);
         }
         // single aggregate for single column
         else {
            DataRef attr = aref.getDataRef();
            boolean isAggCalc = VSUtil.isAggregateCalc(attr);
            attr = findColumn(attr);
            int col = AssetUtil.findColumn(base, attr, columnIndexMap);

            if(col < 0 && !isAggCalc) {
               LOG.warn("Aggregate column not found in table summary: {}", attr);
               continue;
            }

            Formula form = getFormula1(aref, base, helper, aggregates, vars);

            if(isAggCalc) {
               int[] cols2 = ((Formula2) form).getSecondaryColumns();

               if(cols2 != null && cols2.length > 0) {
                  slist.add(cols2[0]);
               }
            }
            else {
               slist.add(col);
            }

            flist.add(form);
         }
      }

      if(slist.size() == 0) {
         return base;
      }

      int[] carr = new int[slist.size()];
      Formula[] farr = new Formula[flist.size()];

      for(int i = 0; i < carr.length; i++) {
         carr[i] = slist.get(i);
         farr[i] = flist.get(i);
      }

      TableLens ctable = PostProcessor.tableSummary(base, carr, farr);
      ctable = PostProcessor.mapColumn(ctable, carr);

      List<String> mheaders = getAggCalcHeader(farr, aggregates);

      for(int i = 0; i < mheaders.size(); i++) {
         if(mheaders.get(i) != null) {
            ctable.setObject(0, i, mheaders.get(i));
            ctable.setColumnIdentifier(i, mheaders.get(i));
         }
      }

      return ctable;
   }

   /**
    * Modifeid the aggregate calc column header.
    */
   private List<String> getAggCalcHeader(Formula[] farr, DataRef[] aggregates) {
      List<String> mheaders = new ArrayList<>();

      // change the calc field header name and identitier
      for(int i = 0; i < farr.length; i++) {
         // Bug #62428, if one of the aggregates is a calculated ref then set header names
         // for all the aggregates so that later columns are mapped correctly.
         if(farr[i] instanceof CalcFieldFormula) {
            DataRef ref = aggregates[i];

            if(ref instanceof DataRefWrapper) {
               ref = ((DataRefWrapper) ref).getDataRef();
            }

            mheaders.add(ref.getName());
         }
         else {
            mheaders.add(null);
         }
      }

      return mheaders;
   }

   /**
    * Get the ranking table lens.
    * @param base the specified base table.
    * @param vars the specified variable table.
    * @return the ranking table lens.
    */
   protected TableLens getRankingTableLens(TableLens base, VariableTable vars) {
      AggregateInfo info = getAggregateInfo();

      // crosstab? do not apply ranking
      if(!info.isEmpty() && info.isCrosstab()) {
         return base;
      }

      ConditionListWrapper wrapper = getRankingConditionList();

      if(wrapper == null) {
         return base;
      }

      wrapper.replaceVariables(vars);
      ConditionList conds = wrapper.getConditionList();

      if(conds.getSize() == 0) {
         return base;
      }

      // viewsheet top-n should be per group. use SummaryFilter since it's topN
      // already handles hierarchy. (49223, 49232)
      // worksheet top-n is flat (not per group). (49245)
      if(getTable().getName().startsWith(Assembly.TABLE_VS) && info.getGroupCount() > 0) {
         ColumnIndexMap columnIndexMap = new ColumnIndexMap(base, true);
         int[] groupcols = Arrays.stream(info.getGroups())
            .mapToInt(g -> Util.findColumn(base, g.getDataRef(), columnIndexMap)).toArray();
         int[] sumcols = Arrays.stream(info.getAggregates())
            .mapToInt(g -> Util.findColumn(base, g.getDataRef(), columnIndexMap)).toArray();
         boolean valid = !Arrays.stream(groupcols).anyMatch(i -> i < 0) &&
            !Arrays.stream(sumcols).anyMatch(i -> i < 0);

         if(valid) {
            // the base table should already be aggregated so need to do aggregate on aggregate
            Formula[] formulas = Arrays.stream(info.getAggregates()).map(a -> {
               AggregateFormula formula = a.getFormula();
               AggregateFormula parent = formula != null ? formula.getParentFormula() : null;
               return parent != null ? Util.createFormula(null, parent.getFormulaName())
                  : (XSchema.isNumericType(a.getDataType()) ? new SumFormula() : new MaxFormula());
            }).toArray(Formula[]::new);
            SummaryFilter sumtbl = new SummaryFilter(base, groupcols, sumcols, formulas, null);

            for(int i = 0; i < conds.getConditionSize(); i++) {
               ConditionItem item = conds.getConditionItem(i);

               if(item != null && item.getXCondition() instanceof RankingCondition) {
                  RankingCondition ranking = (RankingCondition) item.getXCondition();
                  DataRef rref = item.getAttribute();
                  DataRef sref = ranking.getDataRef();
                  int gcol = ArrayUtils.indexOf(groupcols, Util.findColumn(base, rref, columnIndexMap));
                  int scol = ArrayUtils.indexOf(sumcols, Util.findColumn(base, sref, columnIndexMap));

                  if(gcol < 0) {
                     LOG.warn("Ranking group column not found, ignored: " + rref);
                     continue;
                  }
                  else if(scol < 0) {
                     LOG.warn("Ranking summary column not found, ignored: " + sref);
                     continue;
                  }

                  sumtbl.setTopN(gcol, scol, ((Number) ranking.getN()).intValue(),
                                 ranking.getOperation() == XCondition.BOTTOM_N,
                                 ranking.isEqual(), ranking.isGroupOthers());
               }
            }

            return sumtbl;
         }
         else {
            LOG.error("TopN columns not found in base table: " + info);
         }
      }

      ConditionItem titem = conds.getConditionItem(0);
      RankingCondition condition = (RankingCondition) titem.getXCondition();
      DataRef attr = condition.getDataRef() != null ? condition.getDataRef() : titem.getAttribute();

      if(attr == null) {
         return base;
      }

      int col = AssetUtil.findColumn(base, attr);

      if(col < 0) {
         LOG.warn("Ranking column not found: {}", attr);
         return base;
      }

      Object n = condition.getN();

      if(!(n instanceof Number)) {
         LOG.warn("Invalid ranking n found in ranking table: {}", n);
         return base;
      }

      RankingTableLens rtable = new RankingTableLens(base);
      rtable.setRankingColumn(col);
      rtable.setEqualityKept(false);
      rtable.setRankingN(((Number) n).intValue());
      rtable.setTopRanking(condition.getOperation() == XCondition.TOP_N);

      return rtable;
   }

   /**
    * Get the visible table lens.
    * @param base the specified base table.
    * @param vars the specified variable table.
    * @return the visible table lens.
    */
   protected TableLens getVisibleTableLens(TableLens base, VariableTable vars)
      throws Exception
   {
      ColumnSelection selection = getVisibleColumnSelection();

      // check if it's necessary to apply visible filter
      if(!AssetQuerySandbox.isDesignMode(mode) && isQueryMergeable(false)) {
         int viscnt = 0;
         AggregateInfo ainfo = getAggregateInfo();
         ColumnSelection tselection = selection;

         // in case of vpm
         if(!ainfo.isEmpty()) {
            tselection = getTable().getColumnSelection(true);

            if(tselection.getAttributeCount() == 0) {
               tselection = selection;
            }
         }

         for(int i = 0; i < tselection.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) tselection.getAttribute(i);

            if(column.isVisible() && !column.isHiddenParameter()) {
               viscnt++;
            }
         }

         if(base.getColCount() == viscnt) {
            return base;
         }

         if("true".equals(vars.get("fromReport"))) {
            return base;
         }
      }

      List<Integer> cols = new ArrayList<>();
      AggregateInfo _ginfo = this.ginfo;

      // fix Bug #37218, Bug #37217,
      // for mirror created by viewsheet, should get aggregdocsate info from the real table.
      if(getTable() instanceof MirrorTableAssembly &&
         "true".equals(getTable().getProperty(Viewsheet.VS_MIRROR_TABLE)))
      {
         MirrorTableAssembly mirror = (MirrorTableAssembly) getTable();
         TableAssembly real = mirror.getTableAssembly();

         if(real != null) {
            _ginfo = real.getAggregateInfo();
         }
      }

      // crosstab data?
      if(_ginfo != null && !_ginfo.isEmpty() && _ginfo.isCrosstab()) {
         int rhcount = _ginfo.getGroupCount() - 1;

         for(int i = 0; i < base.getColCount(); i++) {
            if(i >= rhcount) {
               cols.add(i);
            }
            else {
               ColumnRef column = AssetUtil.findColumn(base, i, selection, true);

               if(column == null || column.isVisible()) {
                  cols.add(i);
               }
            }
         }
      }
      // normal data?
      else {
         AggregateInfo ainfo = getAggregateInfo();
         List<ColumnRef> ignoreDc = new ArrayList<>();

         // in case of vpm
         if(!ainfo.isEmpty()) {
            selection = getTable().getColumnSelection(true);

            if(selection.getAttributeCount() == 0) {
               selection = getTable().getColumnSelection(false);
            }
         }

         ColumnIndexMap columnIndexMap = new ColumnIndexMap(base);
         boolean creatingMV = box != null && box.isCreatingMV();
         // columns found by index, e.g. Column [1]
         BitSet columnsByIndex = new BitSet();

         for(int i = 0; i < selection.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) selection.getAttribute(i);

            if(!column.isVisible() || column.isHiddenParameter()) {
               continue;
            }

            // spark df may not have exact id. (51386)
            int col = AssetUtil.findColumn(base, column, !creatingMV, true, columnIndexMap);

            if(col < 0) {
               if(this instanceof TabularBoundQuery) {
                  DataRef baseRef = column;

                  if(!(column instanceof CalculateRef) && column.getAlias() != null) {
                     baseRef = selection.getAttribute(column.getDataRef().getAttribute(), true);

                  }

                  if(!(baseRef instanceof CalculateRef)) {
                     String msg = Catalog.getCatalog().getString(
                        "viewer.worksheet.columnMissing", column.getAttribute());

                     // VSQueryController.runQuery will update the columns in tabular assembly.
                     CoreTool.addUserMessage(msg);
                  }
               }

               continue;
            }

            if(column.getAttribute().equals(XUtil.getDefaultColumnName(col))) {
               columnsByIndex.set(col);
            }

            // if a column was found by column index (e.g. 'Column [1]'), and it's found
            // again by name, should ignore the column to avoid duplicates. (52631)
            if(columnsByIndex.get(col) && cols.contains(col)) {
               continue;
            }

            // for bc(10.1 to 10.2), group column name alias is same as
            // the base column attribute, here to make sure only map one
            if(cols.contains(col) && isBCSpecial()) {
               continue;
            }

            cols.add(col);
         }
      }

      int[] carr = cols.stream().mapToInt(i -> i).toArray();
      return PostProcessor.mapColumn(base, carr);
   }

   /**
    * Get visible column selection.
    */
   protected ColumnSelection getVisibleColumnSelection() {
      return getTable().getColumnSelection();
   }

   /**
    * Get the distinct table lens.
    * @param base the specified base table.
    * @param vars the specified variable table.
    * @return the distinct table lens.
    */
   protected TableLens getDistinctTableLens(TableLens base, VariableTable vars)
      throws Exception
   {
      TableAssembly table = getTable();
      boolean distinct = table.isDistinct();

      if(isQueryMergeable(false) || !distinct) {
         return base;
      }

      // @by yuz, fix bug1241607756587, should not reorder if time dimension
      ColumnSelection selection = getTable().getColumnSelection();
      List<Integer> list = new ArrayList<>();
      List<Integer> list2 = new ArrayList<>();
      List<Comparator> compsList = new ArrayList<>();
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(base);

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) selection.getAttribute(i);

         if(!column.isVisible()) {
            continue;
         }

         int index = AssetUtil.findColumn(base, column, columnIndexMap);

         if(index < 0) {
            LOG.warn("Distinct column not found: {}", column);
            continue;
         }

         list.add(index);

         if((column.getRefType() & DataRef.CUBE) == 0) {
            continue;
         }

         list2.add(column.getRefType());
         compsList.add(Util.getColumnComparator(base, column));
      }

      int[] arr = new int[list.size()];
      int[] dimTypes = list2.size() == list.size() ?
         new int[list.size()] : null;
      Comparator[] comps = dimTypes != null ?
         new Comparator[dimTypes.length] : null;

      for(int i = 0; i < arr.length; i++) {
         arr[i] = list.get(i);

         if(dimTypes != null) {
            dimTypes[i] = list2.get(i);
         }

         if(comps != null) {
            comps[i] = compsList.get(i);
         }
      }

      return PostProcessor.distinct(base, arr, dimTypes, comps);
   }

   /**
    * Get the max row count table lens.
    * @param base the specified base table.
    * @param vars the specified variable table.
    * @return the max row count table lens.
    */
   protected TableLens getMaxRowsTableLens(TableLens base, VariableTable vars) throws Exception {
      int max = getMaxRows(true);

      if(isQueryMergeable(false) || max <= 0) {
         return base;
      }

      return PostProcessor.maxrows(base, max);
   }

   /**
    * Get the format table lens.
    * @param base the specified base table.
    * @param vars the specified variable table.
    * @return the formatted table lens.
    */
   protected TableLens getFormatTableLens(TableLens base, VariableTable vars)
      throws Exception
   {
      AssetQuery.FormatTableLens ftable = new AssetQuery.FormatTableLens(base);
      boolean changed = false;
      AggregateInfo ginfo = getBasedAggregateInfo();
      TableAssembly table = getTable();

      // get the aggregate info from the untransformed table to format the table correctly
      if(ginfo.isEmpty() && table != null && "true".equals(table.getProperty("aggregate.down"))) {
         Worksheet ws = box.getWorksheet();
         table = ws != null ? (TableAssembly) ws.getAssembly(table.getName()) : null;
         ginfo = table != null ? table.getAggregateInfo() : ginfo;
      }

      ColumnIndexMap columnIndexMap = new ColumnIndexMap(base, true);

      for(int i = 0; i < ginfo.getGroupCount(); i++) {
         GroupRef gref = ginfo.getGroup(i);
         DataRef ref = ((ColumnRef) gref.getDataRef()).getDataRef();

         if(ref instanceof DateRangeRef) {
            // for bc(10.1 to 10.2), the identifier has been changed, so here
            // use ColumnRef to find column
            int col =
               Util.findColumn(base, isBCSpecial() ? gref.getDataRef() : gref, columnIndexMap);

            if(col == -1) {
               ColumnSelection cols = table.getColumnSelection();
               col = findColumByAlias(base, gref.getDataRef(), cols, columnIndexMap);
            }

            int option = ((DateRangeRef) ref).getDateOption();

            if(option != XConstants.NONE_DATE_GROUP && col >= 0) {
               Format fmt = XUtil.getDefaultDateFormat(
                  option, ((DateRangeRef) ref).getOriginalType());
               ftable.setFormat(col, fmt);
               changed = true;
            }
         }
      }

      return changed ? ftable : base;
   }

   private int findColumByAlias(TableLens base, DataRef ref, ColumnSelection cols,
                                ColumnIndexMap columnIndexMap) {
      DataRef aref = null;

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         if(Tool.equals(ref.getName(), cols.getAttribute(i).getAttribute())) {
            aref = cols.getAttribute(i);
            break;
         }
      }

      if(aref != null) {
         return Util.findColumn(base, aref, columnIndexMap);
      }

      return -1;
   }

   /**
    * gets the aggregate info to apply date range format
    */
   protected AggregateInfo getBasedAggregateInfo() {
      return this.ginfo;
   }

   /**
    * Get the formula object.
    * @param aggregate the specified aggregate.
    * @return the associated formula object of the aggregate formula.
    */
   private Formula getFormula1(AggregateRef aggregate, TableLens lens,
                               SQLHelper helper, AggregateRef[] allagg,
                               VariableTable vars)
   {
      if(!isWorksheetCube() && XCube.SQLSERVER.equals(getCubeType())) {
         return new CubeMeasureFormula();
      }

      AggregateFormula aform = aggregate.getFormula();
      boolean percent = aggregate.isPercentage();
      String fstr = aform == null ? "Default" : aform.getFormulaName();
      DataRef ref2 = aggregate.getSecondaryColumn();

      if(ref2 == null && aform != null && aform.isTwoColumns()) {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "common.report.composition.execution.requireFormula",
               aform.getFormulaName()));
      }

      if(ref2 != null && aform != null && aform.isTwoColumns()) {
         int col = AssetUtil.findColumn(lens, ref2);

         if(col < 0 && VSUtil.requiresTwoColumns(aform)) {
            throw new RuntimeException("Formula column not found: " + ref2);
         }

         fstr += "(" + col + ")";
      }

      if(aform != null && aform.hasN()) {
         fstr += "(" + aggregate.getN() + ")";
      }

      DataRef baseRef = aggregate.getDataRef();

      if(fstr == null || fstr.toLowerCase().startsWith(SummaryAttr.NONE_FORMULA) &&
         !VSUtil.isAggregateCalc(baseRef))
      {
         return (aggregate.getRefType() & DataRef.CUBE_MEASURE) == DataRef.CUBE_MEASURE ?
            new CubeMeasureFormula() : fstr == null ? null : new NoneFormula();
      }

      Formula form = null;

      try {
         DataRef bbref = getBaseAttribute(baseRef);

         // for on aggregate expression, should use CalcFieldFormula to calc
         if(bbref instanceof ExpressionRef && (((ExpressionRef) bbref).isOnAggregate())) {
            ExpressionRef eref = (ExpressionRef) bbref;
            String expression = eref.getExpression();
            List<String> matchNames = new ArrayList<>();
            List<AggregateRef> aggs = VSUtil.findAggregate(allagg, matchNames, expression);
            Formula[] forms = new Formula[aggs.size()];
            String[] names = new String[aggs.size()];
            names = matchNames.toArray(names);
            List<Integer> colidx = new ArrayList<>();
            ColumnIndexMap columnIndexMap = new ColumnIndexMap(lens);

            for(int i = 0; i < aggs.size(); i++) {
               AggregateRef aref = aggs.get(i);
               forms[i] = getFormula1(aref, lens, helper, allagg, vars);
               DataRef bref = aref.getDataRef();
               int col = AssetUtil.findColumn(lens, bref, columnIndexMap);

               // the base column may be changed to an alias with the full name of the aggregate
               // by calling VSUtil.createAliasAgg in ChartVSAQuery. (53311)
               if(col < 0) {
                  col = AssetUtil.findColumn(lens, aref, columnIndexMap);
               }

               if(col < 0) {
                  List<Object> headers = IntStream.range(0, lens.getColCount())
                     .mapToObj(c -> lens.getObject(0, c)).collect(Collectors.toList());
                  LOG.warn("Column not found: " + bref + " in " + headers);
                  throw new ColumnNotFoundException(Catalog.getCatalog().getString(
                     "common.invalidTableColumn", bref));
               }

               colidx.add(col);

               DataRef sub2 = aref.getSecondaryColumn();

               if(sub2 == null && forms[i] instanceof Formula2) {
                  throw new RuntimeException("Formula requires two columns: " + forms[i]);
               }

               if(sub2 != null && aref.getFormula().isTwoColumns()) {
                  col = AssetUtil.findColumn(lens, sub2, columnIndexMap);

                  if(col < 0) {
                     List<Object> headers = IntStream.range(0, lens.getColCount())
                        .mapToObj(c -> lens.getObject(0, c)).collect(Collectors.toList());
                     LOG.warn("Secondary column not found: " + bref + " in " + headers);
                     throw new ColumnNotFoundException(Catalog.getCatalog().getString(
                        "common.invalidTableColumn", bref));
                  }

                  colidx.add(col);
               }
            }

            int[] cols = new int[colidx.size()];

            for(int i = 0; i < colidx.size(); i++) {
               cols[i] = colidx.get(i);
            }

            AssetQueryScope scope = box.getScope();
            scope.setVariableTable(vars);
            scope.setMode(mode);
            getTable(); // kept because it could have side effects
            ScriptEnv env = box.getScriptEnv();
            form = new CalcFieldFormula(expression, names, forms, cols, env, box.getScope());
         }
         else {
            form = Util.createFormula(lens, fstr);
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to create formula", e);
      }

      if(form == null) {
         throw new RuntimeException("Unsupported aggregate formula found: " + aform);
      }

      if(percent && form instanceof PercentageFormula) {
         ((PercentageFormula) form).setPercentageType(aggregate.getPercentageOption());
      }

      if(form instanceof CompositeVarianceFormula && helper != null) {
         ((CompositeVarianceFormula) form).setDBType(helper.getSQLHelperType());
      }

      return form;
   }

   /**
    * Get cube type.
    */
   protected String getCubeType() {
      CubeTableAssembly cube = AssetUtil.getBaseCubeTable(getTable());

      if(cube == null) {
         return null;
      }

      SourceInfo sinfo = cube.getSourceInfo();
      return AssetUtil.getCubeType(sinfo.getPrefix(), sinfo.getSource());
   }

   /**
    * Check if it is woksheet cube.
    */
   protected boolean isWorksheetCube() {
      return AssetUtil.isWorksheetCube(getTable());
   }

   /**
    * Create sort order for a group.
    * @param group the specified group.
    * @param order the specified order.
    * @param specificIncluded <tt>true</tt> if apply sorting even when it's a
    * specific group (named group or date group).
    * @return the date sort order of the group.
    */
   protected SortOrder createDateSortOrder(GroupRef group, int order,
                                           boolean specificIncluded) {
      if(group == null) {
         return null;
      }

      NamedGroupInfo info = (NamedGroupInfo) group.getNamedGroupInfo();

      if(info != null && !specificIncluded) {
         return null;
      }

      int date = getDateGroup(group);

      if(date == XConstants.NONE_DATE_GROUP && !specificIncluded) {
         return null;
      }

      SortOrder sorder = new SortOrder(order);
      sorder.setInterval(1, date);
      sorder.setDataType(AssetUtil.getOriginalType(group));
      sorder.setDatePostProcess(false);
      return sorder;
   }

   /**
    * Create sort order for a group.
    * @param group the specified group.
    * @param order the specified order.
    * @param index the specified index.
    * @return the sort order of the group.
    */
   private SortOrder createSortOrder(GroupRef group, int order, int index) {
      SortOrder sorder = new SortOrder(order);

      sorder.setDataType(AssetUtil.getOriginalType(group));
      sorder.setDatePostProcess(false);
      int date = getDateGroup(group);

      if(date != XConstants.NONE_DATE_GROUP) {
         sorder.setInterval(1, date);
      }
      else {
         /*
         if(XSchema.TIME.equals(group.getDataType()) ||
            (group.getRefType() & DataRef.CUBE) != 0)
         {
            sorder.setInterval(1, SortOrder.SECOND_DATE_GROUP);
         }
         else {
            sorder.setInterval(1, SortOrder.DAY_DATE_GROUP);
         }
         */

         sorder.setInterval(1, SortOrder.NONE_DATE_GROUP);
      }

      NamedGroupInfo info = (NamedGroupInfo) group.getNamedGroupInfo();

      if(info != null && !info.isEmpty()) {
         String[] groups = info.getGroups();

         for(String grp : groups) {
            ConditionList cond = info.getGroupCondition(grp);
            ConditionGroup cgroup = new ConditionGroup(index,
                                                       (ConditionList) cond.clone());
            sorder.addGroupCondition(grp, cgroup);
         }

         sorder.setSpecific(true);
         sorder.setOthers(info.getOthers());
      }

      return sorder;
   }

   /**
    * check groupby columns is the vpm hidden columns(data type is date).
    * @return sql is groupby hidden column(date type).
    */
   private boolean isGroupedByHiddenCols(JDBCQuery query, VariableTable vars,
      String groupByCols) throws Exception
   {
      String[] tables = new String[0];
      String[] columns = new String[0];

      XDataSource dx = query.getDataSource();
      String dname = dx == null ? null : dx.getFullName();
      XDataModel model = XFactory.getRepository().getDataModel(dname);

      if(model == null) {
         return false;
      }

      String[] names = model.getVirtualPrivateModelNames();
      String[] result =  null;

      for(String name : names) {
         VirtualPrivateModel vpm = model.getVirtualPrivateModel(name);
         HiddenColumns hcolumns = vpm.getHiddenColumns();

         if(hcolumns != null) {
            result = hcolumns.evaluate(tables, columns, vars, box.getUser(), false,
                                       query.getPartition());
         }
      }

      int index = groupByCols.indexOf('(');

      if(index > 0) {
         groupByCols = groupByCols.substring(index + 1,
            groupByCols.length() - 1);
      }

      if(result == null) {
         return false;
      }

      for(String aResult : result) {
         if(aResult.indexOf(groupByCols) > 0) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the target jdbc query to merge into.
    * @return the target jdbc query to merge into.
    */
   @Override
   public JDBCQuery getQuery() throws Exception {
      return pushdownQuery != null ? pushdownQuery : getQuery0();
   }

   /**
    * @param query the jdbc query which pushdowned grouping/aggregation for report-worksheet.
    */
   public void setPushdownQuery(JDBCQuery query) {
      this.pushdownQuery = query;
   }

   /**
    * Get the post process base table.
    * @return the post process base table of the query.
    */
   protected TableLens getPreBaseTableLens(VariableTable vars) throws Exception {
      JDBCQuery query = getQuery();
      ColumnSelection columns = gmerged ? gcolumns : used;

      if(isGroupedByHiddenCols(query, vars, columns.toString())) {
         return null;
      }

      if(columns.getAttributeCount() == 0) {
         XEmbeddedTable table =
            new XEmbeddedTable(new String[0], new Object[0][0]);
         return new XTableLens(table);
      }

      UniformSQL nsql = getUniformSQL();
      SQLHelper helper = getSQLHelper(nsql);

      if("true".equals(getTable().getProperty("MV"))) {
         nsql.setHint(UniformSQL.HINT_STATIC_SQL, Boolean.TRUE);
      }

      boolean maxrows = helper.supportsOperation(SQLHelper.MAXROWS);
      boolean cached = isDriversDataCached();
      int analysic_maxrow = getAnalysisMaxrow();

      if(analysic_maxrow > 0 && maxrows) {
         nsql.setHint(UniformSQL.HINT_OUTPUT_MAXROWS, String.valueOf(analysic_maxrow));
         vars.put(XQuery.HINT_MAX_ROWS, String.valueOf(analysic_maxrow));
      }

      nsql.setVPMCondition(hasVPMCondition());

      // @by larryl, if in live mode (design), limit the detail tables
      // so each query won't take forever.
      if(mode == AssetQuerySandbox.LIVE_MODE && maxrows ||
         AssetQuerySandbox.isLiveMode(mode) && cached)
      {
         int max = box.getWorksheet().getWorksheetInfo().getDesignMaxRows();

         // set property regardless whether it's 0 so the cache knows it's design time
         if(cached) {
            // let spark cache select samples from cached query
            vars.put("__HINT_CACHE_SAMPLES__", max + "");
         }

         if(max > 0) {
            if(!cached) {
               nsql.setHint(UniformSQL.HINT_INPUT_MAXROWS, max + "");
            }

            // @by larryl, this is added so the cache key would be unique
            vars.put(UniformSQL.HINT_INPUT_MAXROWS, max + "");
         }
      }

      XSessionManager manager = XSessionManager.getSessionManager();
      long timeout = getTimeout();

      if(timeout > 0) {
         vars.put(XQuery.HINT_TIMEOUT, timeout + "");
      }

      if("true".equals(SreeEnv.getProperty("mv.debug"))) {
         LOG.debug("Execute generated asset query: {}, VariableTable: {}", query, vars);
      }

      // a query might belong to more than one query manager. For example,
      // in an asset query sandbox, there is one query manager manages
      // all the queries; meanwhile, when a chart is reexecuted, we need
      // to cancel the previous execution if any, so there might be a
      // specific query manager
      query.setProperty("queryManager", box.getQueryManager());
      query.setProperty("queryManager2", getQueryManager());

      if(query.getSQLDefinition() instanceof UniformSQL) {
         ((UniformSQL) query.getSQLDefinition()).setVpmUser(box.getVPMUser());
      }

      TableLens base = null;

      WSExecution.setAssetQuerySandbox(box);

      try {
         base = manager.getXNodeTableLens(query, vars, box.getUser(),
                                          null, null, touchtime);
      }
      catch(SQLExpressionFailedException ex) {
         if(mode != AssetQuerySandbox.RUNTIME_MODE) {
            throw new ExpressionFailedException(-1, null, getTable().getName(), ex);
         }
      }
      finally {
         WSExecution.setAssetQuerySandbox(null);
      }

      columns.setProperty("BC_VALIDATE",
         "true".equalsIgnoreCase(getTable().getProperty("BC_VALIDATE")) + "");
      return fixColumnHeaders(base, columns);
   }

   /**
    * Set whether to limit the query execution time.
    */
   public void setTimeLimited(boolean limit) {
      this.limitTime = limit;
   }

   /**
    * Check whether to limit the query execution time.
    */
   public boolean isTimeLimited() {
      return limitTime;
   }

   /**
    * Set the specific query manager.
    */
   public void setQueryManager(QueryManager qmgr) {
      this.qmgr = qmgr;
   }

   /**
    * Get the specific query manager.
    */
   public QueryManager getQueryManager() {
      return qmgr;
   }

   /**
    * Validate data types.
    * @param base the specified table.
    * @param columns the specified column selection.
    */
   protected void validateDataTypes(XTable base, ColumnSelection columns,
                                    ColumnIndexMap columnIndexMap)
   {
      int rcnt = base.getRowCount();

      //if(!base.moreRows(1)) {
      // don't use moreRows(1) or it may block until the full table is loaded
      // if SortFilter is in the chain
      if(rcnt == -1 || rcnt == -2) {
         return;
      }

      if(columnIndexMap == null) {
         columnIndexMap = new ColumnIndexMap(base);
      }

      boolean isSQLite = isSQLite();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);
         int index = AssetUtil.findColumn(base, column, columnIndexMap);
         String colType = column.getDataType();

         // since SQLite don't support boolean/date/time/datetime,
         // so keep the column types, since they will be need in
         // FixColumnType filter to convert the data to the target types.
         if(isSQLite && !XSchema.INTEGER .equals(colType)) {
            continue;
         }

         if(index >= 0) {
            Tuple typeKey = new Tuple(base, index);
            Class dtypeClass = colTypes.get(typeKey);

            if(dtypeClass == null) {
               // @by jasonshobe, bug1327684949496, if all values of a column are
               // null, getColType normally defaults to string. In the case of
               // preview live data when editing a worksheet, this will cause the
               // defined column type to be lost. So here, if a column type is
               // defined, use that type as the default for cases with no data.
               dtypeClass = Util.getColType(
                  base, index, Tool.getDataClass(column.getDataType()), 1000);
            }

            colTypes.put(typeKey, dtypeClass);
            String dtype = Tool.getDataType(dtypeClass);

            // getDataType now returns STRING for both string data and unknown
            // data. This used to check for UNKNOWN, but it doesn't matter
            // anyway, because an UNKNOWN column has no values and won't be
            // parsed or formatted anyway
            DataRef dataRef = column.getDataRef();
            boolean isValueRange = dataRef instanceof RangeRef &&
                                   !(dataRef instanceof DateRangeRef);

            if(dataRef instanceof DateRangeRef) {
               DateRangeRef dateRangeRef = (DateRangeRef) dataRef;

               if(XSchema.TIME.equals(dateRangeRef.getOriginalType()) &&
                  dateRangeRef.getDateOption() != DateRangeRef.HOUR_OF_DAY_DATE_GROUP)
               {
                  dtype = dateRangeRef.getOriginalType();
               }
            }

            if(!(XSchema.BOOLEAN.equals(column.getDataType()) &&
               (XSchema.STRING.equals(dtype)) || XSchema.INTEGER.equals(dtype)))
            {
               column.setDataType(isValueRange ? XSchema.STRING : dtype);
            }
         }
      }
   }

   /**
    * Get the real bound table name. If this query is for viewsheet, try to trim
    * the prefix and suffix.
    */
   protected String getTableDescription(String tname) {
      if(tname == null) {
         return "";
      }

      // this logic is not safe, becasue some table in query names
      // V_xxxxx, then will cause problem, detail see bug1373424186186
      // strip off the prefix VS_
      if(tname.startsWith(Assembly.TABLE_VS) &&
         tname.length() > Assembly.TABLE_VS.length() + 1)
      {
         String temp = tname.substring(Assembly.TABLE_VS.length() + 1);

         if(temp.lastIndexOf('_') > 0 &&
            temp.length() - temp.lastIndexOf('_') > 5)
         {
            temp = temp.substring(0, temp.lastIndexOf('_'));
            tname = temp;
         }
      }

      // strip off the prefix S_
      if(tname.startsWith(Assembly.SELECTION)) {
         tname = tname.substring(Assembly.SELECTION.length());
      }

      // strip of the suffix _O
      tname = VSUtil.stripOuter(tname);

      return tname;
   }

   /**
    * Get a unique identifier for the VPM condition and hidden columns applyed
    * to this query.
    */
   public String getVPMKey() throws Exception {
      StringBuilder sbuf = new StringBuilder();

      getVPMKey0(this, sbuf);
      return sbuf.toString();
   }

   /**
    * Get the VPM key for the query tree.
    */
   private void getVPMKey0(AssetQuery query, StringBuilder sbuf) throws Exception {
      if(query instanceof BoundQuery) {
         XQuery xquery = query.getQuery();
         Object vpmcond = xquery.getProperty("vpmCondition");
         Object vpmcols = xquery.getProperty("vpmHiddenColumns");

         if(vpmcond != null && vpmcols != null) {
            sbuf.append(":");
         }

         if(vpmcond != null) {
            sbuf.append(vpmcond.toString());
         }

         if(vpmcols != null) {
            sbuf.append(vpmcols.toString());
         }
      }

      for(int i = 0; i < query.getChildCount(); i++) {
         getVPMKey0(query.getChild(i), sbuf);
      }
   }

   /**
    * Get the parameters used in the vpm that applyed
    * to this query.
    */
   public Set<String> getVPMUsedParameters() throws Exception {
      Set<String> parameters = new HashSet<>();
      getVPMUsedParameters0(this, parameters);

      return parameters;
   }

   /**
    * Get the parameters used in the vpm that applyed
    * to this query.
    */
   private void getVPMUsedParameters0(AssetQuery query, Set<String> parameters)
      throws Exception
   {
      if(query instanceof BoundQuery && !(query instanceof TabularBoundQuery)) {
         XQuery xquery = query.getQuery();
         Object vpmParameters = xquery.getProperty("vpmUsedParameters");

         if(vpmParameters instanceof Set) {
            parameters.addAll((Set) vpmParameters);
         }
      }

      for(int i = 0; i < query.getChildCount(); i++) {
         getVPMUsedParameters0(query.getChild(i), parameters);
      }
   }

   /**
    * Check the table assembly is from bc(version 10.1 to version 10.2),
    * and need special process, such as column identifier....
    */
   private boolean isBCSpecial() {
      return AssetUtil.isBCSpecial(getTable());
   }

   /**
    * Fix the column identifier value, only for a version 10.1 to version 10.2
    * bc problem.
    */
   private static String getColumnIdentifier(String id, ColumnRef column, boolean bc) {
      return AssetUtil.getColumnIdentifier(id, column, bc);
   }

   /**
    * Another summary filter.
    */
   private static class SummaryFilter2 extends SummaryFilter {
      /**
       * Constructor.
       * @param table the specified base table.
       * @param gcols the specified group columns.
       * @param scols the specified summary columns.
       * @param forms the specified formulas.
       * @param cgroup the specified condition group.
       */
      SummaryFilter2(TableLens table, int[] gcols, int[] scols, Formula[] forms, boolean cube,
                     ConditionGroup cgroup, String[] mheaders)
      {
         super(table, gcols, scols, forms, null, false);
         setMeasureNames(mheaders);
         this.cgroup = cgroup;
         groups = new HashMap<>();
         this.cube = cube;
      }

      /**
       * Put group ref.
       */
      void putGroupRef(int gindex, DataRef group) {
         groups.put(gindex, group);
      }

      /**
       * Evaluate the detail node.
       * @return <tt>true</tt> if satisfies condition, <tt>false</tt> otherwise.
       */
      @Override
      protected boolean evaluate(GroupNode node) {
         return cgroup == null || cgroup.evaluate(node.getObjects());
      }

      /**
       * Restore to original order if necessary.
       */
      @Override
      protected void resetOrder(List<GroupNode> nodes0, List<GroupNode> nodes1, int gidx) {
         if(!cube) {
            return;
         }

         DataRef ref = groups.get(gidx);

         if(ref == null || (ref.getRefType() & DataRef.CUBE_TIME_DIMENSION) !=
            DataRef.CUBE_TIME_DIMENSION)
         {
            return;
         }

         nodes1.sort(Comparator.comparingInt(nodes0::indexOf));
      }

      private ConditionGroup cgroup;
      private Map<Integer, DataRef> groups;
      private final boolean cube;
   }

   /**
    * Another asset condition group.
    */
   private class AssetConditionGroup2 extends AssetConditionGroup {
      /**
       * Construct a new instance of Condition Group.
       * @param table the specified table lens.
       * @param list the specified condition list.
       * @param mode the specified asset query mode.
       * @param box the specified asset query sandbox.
       * @param glist the specified group list.
       * @param slist the specified summary list.
       */
      AssetConditionGroup2(TableLens table, ConditionList list, int mode, AssetQuerySandbox box,
                           List glist, List slist)
      {
         this.glist = glist;
         this.slist = slist;
         this.mode = fixSubQueryMode(mode);
         List<AssetCondition> sconds = new ArrayList<>();
         this.table = table;
         this.mtable = new XArrayTable();

         for(int i = 0; i < list.getSize(); i++) {
            HierarchyItem item = list.getItem(i);

            if(item instanceof ConditionItem) {
               ConditionItem citem = (ConditionItem) item;
               DataRef attr = citem.getAttribute();
               int col = findColumn(table, attr);
               XCondition cond = citem.getXCondition();

               if(cond instanceof AssetCondition) {
                  AssetCondition acond = (AssetCondition) cond;
                  acond.reset();
                  SubQueryValue sub = acond.getSubQueryValue();

                  if(sub != null) {
                     TableAssembly tassembly = sub.getTable();
                     tassembly.setDistinct(true);
                     sub.setOperation(acond.getOperation());

                     try {
                        AssetQuery query = AssetQuery.createAssetQuery(
                           tassembly, this.mode, box, true, touchtime, false, mexecuted);
                        query.setSubQuery(false);
                        VariableTable vtable = (VariableTable) box.getVariableTable().clone();
                        TableLens stable = query.getTableLens(vtable);
                        stable = AssetQuery.shuckOffFormat(stable);
                        acond.initSubTable(stable);
                        sconds.add(acond);
                     }
                     catch(Exception ex) {
                        LOG.warn("Failed to execute condition sub-query", ex);

                        // ignore the condition item
                        col = -1;
                     }
                  }
                  else {
                     for(int j = 0; j < acond.getValueCount(); j++) {
                        if(!(acond.getValue(j) instanceof ExpressionValue)) {
                           continue;
                        }

                        ExpressionValue eval = (ExpressionValue) acond.getValue(j);
                        String exp = eval.getExpression();
                        ScriptEnv senv = box.getScriptEnv();
                        VariableTable vtable = box.getVariableTable();
                        String varName = null;
                        Object vval; // variable/parameter value
                        Object val;

                        if(eval.getType().equals(ExpressionValue.SQL)) {
                           int idx1 = exp.indexOf("$(");
                           int idx2 = exp.indexOf(')');

                           if(idx1 >= 0 && idx2 > idx1 + 2) {
                              varName = exp.substring(idx1 + 2, idx2);
                              exp = exp.substring(0, idx1) + "parameter." + varName +
                                 exp.substring(idx2 + 1);
                           }
                        }
                        else {
                           int idx1 = exp.indexOf("parameter.");

                           if(idx1 >= 0) {
                              int k = 0;
                              varName = exp.substring(idx1 + 10);

                              for(; k < varName.length(); k++) {
                                 char c = varName.charAt(k);

                                 if(!Character.isLetterOrDigit(c) && c != 95) {
                                    break;
                                 }
                              }

                              varName = varName.substring(0, k);
                           }
                        }

                        try {
                           vval = vtable.get(varName);
                        }
                        catch(Exception ex) {
                           vval = null;
                        }

                        AssetQueryScope scope = null;

                        try {
                           ViewsheetSandbox vbox = box.getViewsheetSandbox();
                           Viewsheet vs = vbox == null ? null : vbox.getViewsheet();
                           val = varName != null && vval == null ? attr :
                              senv.exec(senv.compile(exp), scope = box.getScope(), null, vs);
                        }
                        catch(Exception ex) {
                           String suggestion = senv.getSuggestion(ex, null, scope);
                           String msg = "Script error: " + ex.getMessage() +
                              (suggestion != null ? "\nTo fix: " + suggestion : "") +
                              "\nScript failed:\n" + XUtil.numbering(exp);

                           if(LOG.isDebugEnabled()) {
                              LOG.debug(msg, ex);
                           }
                           else {
                              LOG.warn(msg);
                           }

                           String scriptMsg = msg;

                           if(eval.getType().equals(ExpressionValue.SQL)) {
                              scriptMsg = Catalog.getCatalog().getString("JavaScript error") +
                                 ": " + ex.getMessage();
                              scriptMsg = Catalog.getCatalog().getString(
                                 "common.conditionMerge") + scriptMsg;
                           }

                           throw new ScriptException(scriptMsg);
                        }

                        if(val instanceof Object[]) {
                           Object[] objs = (Object[]) val;

                           for(int k = 0; k < objs.length; k++) {
                              objs[k] = getScriptValue(objs[k], cond.getType());
                           }
                        }
                        else if(!(val instanceof ColumnRef)) {
                           val = getScriptValue(val, cond.getType());
                        }

                        acond.setValue(j, val);
                     }
                  }
               }

               addCondition(col, cond, citem.getLevel());
            }

            if(item instanceof JunctionOperator) {
               JunctionOperator op = (JunctionOperator) item;
               addOperator(op.getJunction(), op.getLevel());
            }
         }

         sarr = new AssetCondition[sconds.size()];
         sconds.toArray(sarr);
      }

      /**
       * Evaluate the condition group with a give object array.
       * @param values the object array used for evaluation.
       * @return the evaluating result.
       */
      @Override
      public boolean evaluate(Object[] values) {
         mtable.setData(values);

         for(AssetCondition cond : sarr) {
            DataRef mref = cond.getMainAttribute();
            int col = mref == null ? -1 : findColumn(mtable, mref);

            try {
               cond.initMainTable(mtable, col);
            }
            catch(Exception ex) {
               LOG.debug("Failed to initialize main table for condition", ex);
               return false;
            }

            cond.setCurrentRow(0);
         }

         return AssetQuerySandbox.isDesignMode(mode) || super.evaluate(values);
      }

      /**
       * Find the column.
       * @param table the specified table.
       * @param attr the specified attribute.
       * @return the found column.
       */
      @Override
      protected int findColumn(XTable table, DataRef attr) {
         int col = AssetUtil.findColumn(this.table, attr);

         if(col < 0) {
            LOG.warn("Condition column not found: {}", attr);
            return -1;
         }

         Integer iobj = col;

         // check group header first
         int index = glist.indexOf(iobj);

         if(index >= 0) {
            return index;
         }

         // check aggregate header second
         index = slist.indexOf(col);

         if(index < 0) {
            return -1;
         }

         return glist.size() + index;
      }

      private int mode;
      private List glist;
      private List slist;
      private XTable table;
      private XArrayTable mtable;
   }

   /**
    * Array table.
    */
   private static class XArrayTable implements XTable {
      /**
       * Constructor.
       */
      XArrayTable() {
         super();
      }

      /**
       * Set the data.
       * @param arr the specified array.
       */
      public void setData(Object[] arr) {
         this.arr = arr;
      }

      /**
       * Check if there are more rows. The row index is the row that will be
       * accessed. This method must block until the row is available, or
       * return false if the row does not exist in the table. This method is
       * used to iterate through the table, and allow partial table to be
       * accessed in report processing.
       * @param row row number. If EOT is passed in, this method should wait
       * until the table is fully loaded.
       * @return true if the row exists, or false if no more rows.
       */
      @Override
      public boolean moreRows(int row) {
         return row == 0 && arr != null;
      }

      /**
       * Return the number of rows in the table. The number of rows includes
       * the header rows. If the table is loading in background and loading
       * is not done, return the negative number of loaded rows minus 1.
       * @return number of rows in table.
       */
      @Override
      public int getRowCount() {
         return arr != null ? 1 : 0;
      }

      /**
       * Return the number of columns in the table. The number of columns
       * includes the header columns.
       * @return number of columns in table.
       */
      @Override
      public int getColCount() {
         return arr.length;
      }

      /**
       * Return the number of rows on the top of the table to be treated
       * as header rows.
       * @return number of header rows.  Default is 1.
       */
      @Override
      public int getHeaderRowCount() {
         return 0;
      }

      /**
       * Return the number of columns on the left of the table to be
       * treated as header columns.
       */
      @Override
      public int getHeaderColCount() {
         return 0;
      }

      /**
       * Return the number of rows on the bottom of the table to be treated
       * as trailer rows.
       * @return number of header rows.
       */
      @Override
      public int getTrailerRowCount() {
         return 0;
      }

      /**
       * Return the number of columns on the right of the table to be
       * treated as trailer columns.
       */
      @Override
      public int getTrailerColCount() {
         return 0;
      }

      /**
       * Check if is primitive.
       * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
       */
      @Override
      public final boolean isPrimitive(int col) {
         return false;
      }

      /**
       * Check if the value at one cell is null.
       * @param r the specified row index.
       * @param c column number.
       * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
       */
      @Override
      public final boolean isNull(int r, int c) {
         return getObject(r, c) == null;
      }

      /**
       * Return the value at the specified cell.
       * @param r row number.
       * @param c column number.
       * @return the value at the location.
       */
      @Override
      public Object getObject(int r, int c) {
         return arr != null && r == 0 && c >= 0 && c < arr.length ? arr[c] :
            null;
      }

      /**
       * Get the double value in one row.
       * @param r the specified row index.
       * @param c column number.
       * @return the double value in the specified row.
       */
      @Override
      public final double getDouble(int r, int c) {
         return 0D;
      }

      /**
       * Get the float value in one row.
       * @param r the specified row index.
       * @param c column number.
       * @return the float value in the specified row.
       */
      @Override
      public final float getFloat(int r, int c) {
         return 0F;
      }

      /**
       * Get the long value in one row.
       * @param r the specified row index.
       * @param c column number.
       * @return the long value in the specified row.
       */
      @Override
      public final long getLong(int r, int c) {
         return 0L;
      }

      /**
       * Get the int value in one row.
       * @param r the specified row index.
       * @param c column number.
       * @return the int value in the specified row.
       */
      @Override
      public final int getInt(int r, int c) {
         return 0;
      }

      /**
       * Get the short value in one row.
       * @param r the specified row index.
       * @param c column number.
       * @return the short value in the specified row.
       */
      @Override
      public final short getShort(int r, int c) {
         return 0;
      }

      /**
       * Get the byte value in one row.
       * @param r the specified row index.
       * @param c column number.
       * @return the byte value in the specified row.
       */
      @Override
      public final byte getByte(int r, int c) {
         return 0;
      }

      /**
       * Get the boolean value in one row.
       * @param r the specified row index.
       * @param c column number.
       * @return the boolean value in the specified row.
       */
      @Override
      public final boolean getBoolean(int r, int c) {
         return false;
      }

      /**
       * Set the cell value. For table filters, the setObject() call should
       * be forwarded to the base table if possible. An implementation should
       * throw a runtime exception if this method is not supported. In that
       * case, data in a table can not be modified in scripts.
       * @param r row number.
       * @param c column number.
       * @param v cell value.
       */
      @Override
      public void setObject(int r, int c, Object v) {
         // do nothing
      }

      /**
       * Get the current column content type.
       * @param col column number.
       * @return column type.
       */
      @Override
      public Class getColType(int col) {
         Object obj = getObject(0, col);
         return obj == null ? String.class : obj.getClass();
      }

      /**
       * Dispose the table to clear up temporary resources.
       */
      @Override
      public void dispose() {
         // do nothing
      }

      /**
       * Get the column identifier of a column.
       * @param col the specified column index.
       * @return the column indentifier of the column. The identifier might be
       * different from the column name, for it may contain more locating
       * information than the column name.
       */
      @Override
      public String getColumnIdentifier(int col) {
         return null;
      }

      /**
       * Set the column identifier of a column.
       * @param col the specified column index.
       * @param identifier the column indentifier of the column. The identifier
       * might be different from the column name, for it may contain more
       * locating information than the column name.
       */
      @Override
      public void setColumnIdentifier(int col, String identifier) {
         // do nothing
      }

      /**
       * Get internal table data descriptor which contains table structural
       * infos.
       * @return table data descriptor
       */
      @Override
      public TableDataDescriptor getDescriptor() {
         return new DefaultTableDataDescriptor(this);
      }

      /**
       * Get the value of a property.
       * @param key the specified property name.
       * @return the value of the property.
       */
      @Override
      public Object getProperty(String key) {
         return prop.get(key);
      }

      /**
       * Set the value a property.
       * @param key the property name.
       * @param value the property value, null to remove the property.
       */
      @Override
      public void setProperty(String key, Object value) {
         if(value == null) {
            prop.remove(key);
         }
         else {
            prop.put(key, value);
         }
      }

      /**
       * @return the report/vs name which this filter was created for,
       * and will be used when insert audit record.
       */
      @Override
      public String getReportName() {
         Object name = getProperty(XTable.REPORT_NAME);
         return name == null ? null : name + "";
      }

      /**
       * @return the report type which this filter was created for:
       * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
       * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
       */
      @Override
      public String getReportType() {
         Object type = getProperty(XTable.REPORT_TYPE);
         return type == null ? null : type + "";
      }

      private Object[] arr;
      private Properties prop;
   }

   /**
    * Searches for the named property in this query and all subqueries and returns the first match if it is found.
    *
    * @param name the query property to get
    * @return the value of the property if it is found, null otherwise
    */
   protected Object getQueryProperty(String name) {
      JDBCQuery query = null;

      try {
         query = getQuery();
      }
      catch(Exception ignore) {
      }

      return query != null ? query.getProperty(name) : null;
   }

   private boolean hasVPMCondition() {
      return getQueryProperty("vpmCondition") != null;
   }

   /**
    * Format table lens.
    */
   private static class FormatTableLens extends DefaultTableFilter {
      /**
       * Constructor.
       */
      public FormatTableLens(TableLens base) {
         super(base);
      }

      /**
       * Set the base table.
       */
      @Override
      public void setTable(TableLens base) {
         super.setTable(base);
         formats = new Format[base.getColCount()];
      }

      /**
       * Set the format.
       */
      public void setFormat(int col, Format fmt) {
         formats[col] = fmt;
      }

      /**
       * Get the object.
       * @param r the specified row index.
       * @param c the specified col index.
       * @return the object.
       */
      @Override
      public Object getObject(int r, int c) {
         Object obj = super.getObject(r, c);

         if(r < getHeaderRowCount() || formats[c] == null) {
            return obj;
         }

         return format(formats[c], obj);
      }

      /**
       * Format an object.
       * @param fmt the specified format.
       * @param obj the specified object.
       * @return the formatted result.
       */
      private static Object format(Format fmt, Object obj) {
         if(obj instanceof Number) {
            obj = new Date(((Number) obj).longValue());
         }
         else if(!(obj instanceof Date)) {
            return obj;
         }

         return fmt.format(obj);
      }

      private Format[] formats;
   }

   private static class CrosstabRanking {
      CrosstabRanking(int didx, int topn, boolean reverse) {
         super();

         this.didx = didx;
         this.topn = topn;
         this.reverse = reverse;
      }

      private int didx;
      private int topn;
      private boolean reverse;
   }

   private static final class TableTypeNode extends XTypeNode {
      TableTypeNode() {
         super("table");
      }

      @Override
      protected XNode checkDuplicate(XNode child) {
         return child;
      }
   }

   protected long touchtime = -1L;
   private boolean limitTime = true;
   private QueryManager qmgr = null;
   private boolean mexecuted; // true if this is for meta data only
   private Set<String> formulaCols;
   private JDBCQuery pushdownQuery; // the query pushdowned grouping/aggregation for report-worksheet.
   private final static String PUSH_DOWN_QUERY = "_asset_push_down_query_";
   // col type cache, (table, column) -> Class
   private Map<Tuple, Class> colTypes = new ConcurrentHashMap<>();

   static final String DESIGN_TABLE = AssetQuery.class.getName() + ".designTable";
   public static ThreadLocal<Boolean> THROW_EXECUTE_EXCEPTION = ThreadLocal.withInitial(() -> Boolean.FALSE);
   private static final Logger LOG = LoggerFactory.getLogger(AssetQuery.class);
}
