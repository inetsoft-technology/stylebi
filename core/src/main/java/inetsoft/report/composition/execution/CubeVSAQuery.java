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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.report.filter.DCMergeDateFilter;
import inetsoft.report.internal.Util;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * CubeVSAQuery, the cube viewsheet assembly query.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class CubeVSAQuery extends DataVSAQuery {
   /**
    * Create a cube viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param cname the specified cube to be processed.
    * @param detail <tt>true</tt> if show detail, <tt>false</tt> otherwise.
    */
   public CubeVSAQuery(ViewsheetSandbox box, String cname, boolean detail) {
      super(box, cname, detail);
   }

   /**
    * Create the base plain table assembly.
    * @return the created base plain table assembly.
    */
   protected final TableAssembly createBaseTableAssembly0(boolean analysis)
      throws Exception
   {
      Worksheet ws = getWorksheet();

      if(ws == null) {
         return null;
      }

      String tname = VSUtil.getTableName(getSourceTable());

      if(tname == null || tname.length() == 0) {
         return null;
      }

      SourceInfo sourceInfo = ((DataVSAssembly) getAssembly()).getSourceInfo();
      TableAssembly table;

      if(sourceInfo.getType() == SourceInfo.VS_ASSEMBLY) {
         table = createAssemblyTable(tname);

         if(table == null) {
            throw new BoundTableNotFoundException(Catalog.getCatalog().getString
               ("common.notTable", tname));
         }
      }
      else {
         table = getVSTableAssembly(tname, analysis);

         if(table == null) {
            LOG.error(Catalog.getCatalog().getString("common.notTable", tname));
            return null;
         }

         table = box.getBoundTable(table, vname, isDetail());
         AssetQuerySandbox wbox = box.getAssetQuerySandbox();

         if(wbox != null) {
            // Bug #38117, when called due to ViewsheetSandbox.reset(), any VPM hidden columns will not
            // have been applied to the table assembly's column selections. This does not happen until
            // the asset query is created. If this is not done prior to appending the aggregate fields
            // and one of the aggregate fields is hidden, it will generate an invalid query. So create
            // an asset query here to make sure the table assembly's column selection is correct. This
            // is a little hacky, but it only takes ~2ms.
            AssetQuery.createAssetQuery(
               table, AssetQuerySandbox.DESIGN_MODE, wbox, false, box.getTouchTimestamp(),
               true, false);
         }

         normalizeTable(table);
      }

      WorksheetWrapper wrapper = new WorksheetWrapper(ws);
      wrapper.addAssembly(table);

      if(isPostSort()) {
         table.setProperty("post.sort", "true");
      }

      appendAggCalcField(table, tname);

      if(!analysis) {
         ChartVSAssembly chart = box.getBrushingChart(vname);
         setSharedCondition(chart, table);
      }

      return table;
   }

   protected boolean isPostSort() {
      return false;
   }

   /**
    * Get the default asset column selection.
    * @return the default asset column selection.
    */
   protected ColumnSelection getDefaultAssetColumnSelection() throws Exception {
      TableAssembly table = createBaseTableAssembly0(true);

      if(table == null) {
         return new ColumnSelection();
      }

      return table.getColumnSelection(false);
   }

   /**
    * Get the predefined dimension/measure information.
    */
   public abstract AggregateInfo getAggregateInfo();

   /**
    * Fix aggregate info for cube table.
    */
   protected static void fixAggregateInfo(TableAssembly table, VSAssembly assembly) {
      if(!AssetUtil.isCubeTable(table) || VSUtil.isWorksheetCube(assembly)) {
         return;
      }

      TableAssembly topTable = getAggregateTable(table);
      AggregateInfo ainfo = topTable.getAggregateInfo();
      ColumnSelection topCols = topTable.getColumnSelection();

      table = getBaseTable(table);
      AggregateInfo cubeAggInfo = table.getAggregateInfo();
      ColumnSelection cols = table.getColumnSelection();
      resetAggregateInfo(cubeAggInfo, cols);

      if(!isMergeable(ainfo, assembly)) {
         return;
      }

      for(int i = 0; i < ainfo.getGroupCount(); i++) {
         GroupRef group = ainfo.getGroup(i);
         ColumnRef column = (ColumnRef) group.getDataRef();

         cubeAggInfo.addGroup(group);
         moveColumn(topCols, cols, column);
      }

      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         AggregateRef aggr = ainfo.getAggregate(i);
         ColumnRef column = (ColumnRef) aggr.getDataRef();

         cubeAggInfo.addAggregate(aggr);
         moveColumn(topCols, cols, column);
      }

      table.setColumnSelection(cols, false);
      topTable.setProperty(AGGREGATE_MERGED, "true");
      ainfo.clear();
   }

   /**
    * Get base cube table.
    */
   protected static TableAssembly getBaseTable(TableAssembly table) {
      while(table instanceof MirrorTableAssembly) {
         table = ((MirrorTableAssembly) table).getTableAssembly();
      }

      return table;
   }

   /**
    * Check if detail condition is valid in the given column selection.
    */
   protected abstract boolean isDetailConditionValid(ColumnSelection cols);

   /**
    * Find the table for show detail.
    */
   protected TableAssembly findDetailTable(TableAssembly table) {
      if(!(table instanceof MirrorTableAssembly)) {
         return (TableAssembly) table.clone();
      }

      AggregateInfo ainfo = table.getAggregateInfo();

      // contains aggregate? clear aggregate and return it as detail table
      if(!ainfo.isEmpty()) {
         table = (TableAssembly) table.clone();
         ainfo.clear();
         return table;
      }

      ConditionListWrapper clist = table.getPreRuntimeConditionList();

      // contains runtime filtering? do not delegate to base table.
      // If we delegate to base, the runtime filtering will be ignored
      if(clist != null && !clist.isEmpty()) {
         return table;
      }

      String name = table.getName();

      // worksheet table with condition? keep it
      if(name != null && !name.startsWith(Assembly.TABLE_VS)) {
         clist = table.getPreConditionList();

         if(clist != null && !clist.isEmpty()) {
            return table;
         }

         clist = table.getPostConditionList();

         if(clist != null && !clist.isEmpty()) {
            return table;
         }

         clist = table.getRankingConditionList();

         if(clist != null && !clist.isEmpty()) {
            return table;
         }
      }

      TableAssembly btable = ((MirrorTableAssembly) table).getTableAssembly();
      AggregateInfo binfo = btable.getAggregateInfo();

      // base table is crosstab? do not delegate to base table.
      // If we delegate to base, the column in filtering will not be found
      if(binfo.isCrosstab()) {
         return (TableAssembly) table.clone();
      }

      // fix bug1298519611443
      // base table is BoundTableAssembly and contains aggregate,
      // do not delegate to base table, if we delegate to base,
      // the column in detail filtering will not be found
      if(btable instanceof BoundTableAssembly && !binfo.isEmpty()) {
         return (TableAssembly) table.clone();
      }

      ColumnSelection cols = table.getColumnSelection(true);
      int ccnt = cols.getAttributeCount();

      for(int i = 0; i < ccnt; i++) {
         ColumnRef col = (ColumnRef) cols.getAttribute(i);

         // contains detail calc? do not delegate to base table.
         if(col instanceof CalculateRef) {
            return (TableAssembly) table.clone();
         }
      }

      cols = btable.getColumnSelection(true);

      // base table does not provide valid column selection? do not delegate to
      // base table. If we delegate to base, the column in detail filtering
      // will not be found
      if(!isDetailConditionValid(cols)) {
         return (TableAssembly) table.clone();
      }

      ccnt = cols.getAttributeCount();

      for(int i = 0; i < ccnt; i++) {
         ColumnRef col = (ColumnRef) cols.getAttribute(i);
         String alias = col.getAlias();

         // base table contains aliased colum? do not delegate to base table.
         // If we delegate to base, the column in filtering will not be found
         if(alias != null && alias.length() != 0) {
            return (TableAssembly) table.clone();
         }
      }

      return findDetailTable(btable);
   }

   protected TableLens getDcMergeDateTableLens(TableLens base, TableAssembly table, AggregateInfo aggrInfo) {
      AggregateInfo aggInfo = aggrInfo != null ? aggrInfo : table.getAggregateInfo();
      GroupRef dateRangeGroup = null;
      GroupRef dateMergeGroup = null;

      if(aggInfo != null && aggInfo.getGroupCount() > 0) {
         GroupRef[] groups = aggInfo.getGroups();

         if(groups == null) {
            return base;
         }

         for(GroupRef group : groups) {
            if(group == null) {
               continue;
            }

            if(group.isDcRangeCol()) {
               dateRangeGroup = group;
            }

            if(group.getDcMergeGroup() != null) {
               dateMergeGroup = group;
            }

            if(dateRangeGroup != null && dateMergeGroup != null) {
               break;
            }
         }
      }

      if(dateMergeGroup != null && dateRangeGroup != null) {
         ColumnIndexMap columnIndexMap = new ColumnIndexMap(base, true);
         int dateMergeCol = Util.findColumn(columnIndexMap, dateMergeGroup);
         int dateRangeCol = Util.findColumn(columnIndexMap, dateRangeGroup);

         if(dateMergeCol == -1 || dateRangeCol == -1) {
            return base;
         }

         DCMergeDateFilter dcMergeDateFilter = new DCMergeDateFilter(
            base, dateMergeCol, dateRangeCol, dateMergeGroup.getDcMergeGroup(),
            getAssembly() instanceof ChartVSAssembly);

         return dcMergeDateFilter;
      }

      return base;
   }

   /**
    * Get aggregate table in mirror table.
    */
   private static TableAssembly getAggregateTable(TableAssembly table) {
      if(!"true".equals(table.getProperty("SQLCUBE")) ||
         !(table instanceof MirrorTableAssembly))
      {
         return table;
      }

      return ((MirrorTableAssembly) table).getTableAssembly();
   }

   /**
    * Check if is MS SQLServer Analysis Service.
    */
   protected static boolean isSQLCube(VSAssembly assembly) {
      String ctype = getCubeType(assembly);
      return XCube.SQLSERVER.equals(ctype);
   }

   /**
    * Check if drill down/up should be merged into MDX.
    */
   protected boolean isCubeDrill() {
      String ctype = getCubeType(getAssembly());
      return XCube.SQLSERVER.equals(ctype) || XCube.ESSBASE.equals(ctype) ||
         XCube.MONDRIAN.equals(ctype) || XCube.SAP.equals(ctype);
   }

   /**
    * Execute a table assembly and return a table lens.
    * @param table the specified table assembly.
    * @return the table lens as the result.
    */
   @Override
   protected TableLens getTableLens(TableAssembly table) throws Exception {
      TableLens lens = super.getTableLens(table);
      boolean cube = AssetUtil.isCubeTable(table);

      if(table instanceof ConcatenatedTableAssembly) {
         ConcatenatedTableAssembly concatenated = (ConcatenatedTableAssembly) table;
         TableAssembly[] tables = concatenated.getTableAssemblies();

         for(int i = 0; i < tables.length; i++) {
            if(AssetUtil.isCubeTable(tables[i])) {
               cube = true;
               break;
            }
         }
      }

      if(lens == null || !cube || isWorksheetCube() ||
         "true".equalsIgnoreCase(table.getProperty("showDetail")))
      {
         return lens;
      }

      if("true".equals(table.getProperty(AGGREGATE_MERGED))) {
         table.setProperty(AGGREGATE_MERGED, null);
         table = getBaseTable(table);
      }

      return new VSCubeTableLens(lens, table.getColumnSelection(true));
   }

   /**
    * Get the sub aggregates in aggregate calcs.
    */
   protected List<AggregateRef> getCalcSubAggregates(IAggregateRef[] arefs) {
      List<AggregateRef> all = new ArrayList<>();
      Viewsheet vs = getViewsheet();
      List<String> names = new ArrayList<>();
      String tname = getSourceTable();

      for(int i = 0; i < arefs.length; i++) {
         if(VSUtil.isAggregateCalc(arefs[i].getDataRef())) {
            CalculateRef cref = (CalculateRef) arefs[i].getDataRef();
            ExpressionRef eref = (ExpressionRef) cref.getDataRef();
            List<AggregateRef> sub = VSUtil.findAggregate(vs, tname, names, eref.getExpression());

            for(AggregateRef aref : sub) {
               all.add(VSUtil.createAliasAgg(aref, true));
            }
         }
      }

      return all;
   }

   /**
    * Check if aggregate info could be merged into MDX.
    */
   private static boolean isMergeable(AggregateInfo ainfo, VSAssembly assembly)    {
      return isSQLCube(assembly) && AssetUtil.isMergeable(ainfo);
   }

   /**
    * Reset columns by aggregate info.
    */
   private static void resetAggregateInfo(AggregateInfo ainfo,
      ColumnSelection cols)
   {
      if(ainfo == null) {
         return;
      }

      for(int i = 0; i < ainfo.getGroupCount(); i++) {
         GroupRef group = ainfo.getGroup(i);
         removeColumn(cols, group.getDataRef());
      }

      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         AggregateRef aggr = ainfo.getAggregate(i);
         removeColumn(cols, aggr.getDataRef());
      }

      ainfo.clear();
   }

   /**
    * Remove a column form column selection.
    */
   private static void removeColumn(ColumnSelection cols, DataRef ref) {
      if(cols.containsAttribute(ref)) {
         cols.removeAttribute(ref);
      }
   }

   /**
    * Move a column from top table to cube table.
    */
   private static void moveColumn(ColumnSelection src, ColumnSelection dest,
                           ColumnRef column)
   {
      column.setVisible(true);
      removeColumn(src, column);

      if(!dest.containsAttribute(column)) {
         dest.addAttribute(column);
      }
   }

   private static final String AGGREGATE_MERGED = "Aggregate_Merged";
   private static final Logger LOG = LoggerFactory.getLogger(CubeVSAQuery.class);
}
