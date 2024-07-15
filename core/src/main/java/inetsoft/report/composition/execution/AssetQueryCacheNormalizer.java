/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.filter.ColumnMapFilter;
import inetsoft.report.internal.Util;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XQuery;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.jdbc.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforms the given table such that it hits the cache more often and then transforms
 * the resulting table lens to match the original table.
 */
public class AssetQueryCacheNormalizer {
   public AssetQueryCacheNormalizer(TableAssembly table, AssetQuerySandbox box, int mode) {
      this.table = table;
      this.box = box;
      this.mode = mode;
      prepareTable();
   }

   /**
    * Prepare the table.
    */
   private void prepareTable() {
      if(table.getAggregateInfo() != null) {
         ainfo = (AggregateInfo) table.getAggregateInfo().clone();
      }

      aggregated = table.isAggregate();
      distinct = table.isDistinct();
      SortInfo sortInfo = table.getSortInfo();
      this.sortInfo = sortInfo != null ? (SortInfo) sortInfo.clone() : null;

      // please see the PreQuery#mergeOthers
      if(table instanceof BoundTableAssembly && (sortInfo == null || sortInfo.isEmpty())) {
         SourceInfo sinfo = ((BoundTableAssembly) table).getSourceInfo();
         String source = sinfo.getSource();

         try {
            XQuery sourceQuery = BoundQuery.getSourceQuery(source);

            if(sourceQuery instanceof JDBCQuery &&
               ((JDBCQuery) sourceQuery).getSQLDefinition() instanceof UniformSQL
               && ainfo != null && ainfo.isEmpty())
            {
               distinct |= ((UniformSQL) ((JDBCQuery) sourceQuery).getSQLDefinition()).isDistinct();
            }
         }
         catch(Exception e) {
            // do nothing
         }
      }

      pubColumns = table.getColumnSelection(true);
      privColumns = table.getColumnSelection(false);

      if(!isApplicable()) {
         return;
      }

      allColumns = privColumns.clone();
      crosstab = ainfo != null && ainfo.isCrosstab();

      if(!distinct && !crosstab) {
         for(int i = allColumns.getAttributeCount() - 1; i >= 0; i--) {
            DataRef ref = allColumns.getAttribute(i);

            // Make all columns visible
            if(ref instanceof ColumnRef) {
               ColumnRef cref = (ColumnRef) ref;
               cref.setVisible(true);

               // check if the alias can be removed
               if(canRemoveAlias(cref.getAlias())) {
                  cref.setAlias(null);
               }
            }
         }
      }

      // sort the columns so that it hits the cache in asset data cache more often
      allColumns.sortBy(Comparator.comparing(DataRef::getName));

      // set the new columns selection to include all the columns
      table.setColumnSelection(allColumns.clone(), false);
   }

   /**
    * Transform the resulting table lens to match the original table
    */
   public TableLens transformTableLens(TableLens tableLens) {
      if(!isApplicable()) {
         return tableLens;
      }

      List<Integer> visibleColumnIndexes = new ArrayList<>();
      List<String> headers = new ArrayList<>();
      // The conditions for entering this code are as follows:
      // 1. No groups or aggregates.
      // 2. Exist groups or aggregates, but in 'Meta Detail View' or 'Live Detail View'.
      boolean metaView = AssetQuerySandbox.isDesignMode(mode);
      boolean detailView = !ainfo.isEmpty() && !aggregated;
      ColumnSelection cols = metaView && !crosstab || detailView ? privColumns : pubColumns;
      ColumnSelection allCols = metaView && !crosstab || detailView && metaView ? allColumns : getVisibleColumns();
      boolean colCountMatch = allCols.getAttributeCount() == tableLens.getColCount();
      int visibleRowHeaders  = crosstab ? (int) (Arrays.stream(ainfo.getGroups())
         .filter((gref) -> gref.getDataRef() instanceof ColumnRef &&
            ((ColumnRef) gref.getDataRef()).isVisible())
         .count() - 1) : 0; // -1 because one of the group refs is a column header

      if(crosstab) {
         for(int i = 0; i < tableLens.getColCount(); i++) {
            visibleColumnIndexes.add(i);
         }
      }
      else {
         // Loop over the public column selection in the order the columns were originally arranged.
         // If the column is visible then append it to the list of visible column indexes.
         for(int i = 0; i < cols.getAttributeCount(); i++) {
            DataRef ref = cols.getAttribute(i);

            if(ref instanceof ColumnRef && (((ColumnRef) ref).isVisible() || metaView)) {
               String header = ((ColumnRef) ref).getAlias();
               // use header if has no alias incase there's auto created alias for the duplicate columns
               header = header == null ? ref.getAttribute() : header;

               // When aggregate info is set the number of returned columns
               // might not match allColumns.
               if(aggregated || !colCountMatch) {
                  // past the row headers the columns cannot be reordered so just accept them
                  // the way they are positioned in the table lens
                  if(crosstab && i >= visibleRowHeaders) {
                     visibleColumnIndexes.add(i);
                     headers.add(header);
                  }
                  else {
                     int idx = Util.findColumn(tableLens, ref);

                     if(idx >= 0) {
                        visibleColumnIndexes.add(idx);
                        headers.add(header);
                     }
                     else {
                        LOG.debug("Column not found: " + header);
                     }
                  }
               }
               else if(allCols.indexOfAttribute(ref) >= 0) {
                  visibleColumnIndexes.add(allCols.indexOfAttribute(ref));
                  headers.add(header);
               }
            }
         }
      }

      // The filter will hide and also rearrange the columns back to the original order
      tableLens = new ColumnMapFilter(tableLens, visibleColumnIndexes.stream()
         .mapToInt(Integer::intValue).toArray());

      // add the aliases back to the table lens
      for(int i = 0; i < headers.size(); i++) {
         if(headers.get(i) != null) {
            tableLens.setObject(0, i, headers.get(i));
         }
      }

      // set the column selection back to what it was
      table.setColumnSelection(privColumns, false);
      table.setColumnSelection(pubColumns, true);
      return tableLens;
   }

   private ColumnSelection getVisibleColumns() {
      if(!distinct && !crosstab) {
         return allColumns;
      }

      ColumnSelection visibleCols = new ColumnSelection();

      for (int i = 0; i < allColumns.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) allColumns.getAttribute(i);

         if(col.isVisible()) {
            visibleCols.addAttribute(allColumns.getAttribute(i));
         }
      }

      return visibleCols;
   }

   /**
    * Check if the transformations should be applied to this table
    */
   private boolean isApplicable() {
      // do not sort the columns when the table sort filed is none and the table is distinct,
      // because the distinct will sort with asc, sort column will change the sort column.
      if(distinct && (sortInfo == null || sortInfo.isEmpty())) {
         return false;
      }

      if(table == null || isSQLEditQuery()) {
         return false;
      }

      // don't show invisible column for binding. (61402)
      if(AssetQuerySandbox.isDesignMode(mode)) {
         return false;
      }

      return (box.getViewsheetSandbox() == null ||
         box.getViewsheetSandbox().getMode() == AbstractSheet.SHEET_DESIGN_MODE) &&
         !(table instanceof EmbeddedTableAssembly) &&
         !(table instanceof RotatedTableAssembly) && !isColumnIndexesSensitive() &&
         !isVSSnapshotExport();
   }

   /**
    * Columns order for sql edit query only depends on the sql selection, don't need
    * to transform by columnselection of the table.
    */
   private boolean isSQLEditQuery() {
      if(!(table instanceof SQLBoundTableAssembly)) {
         return false;
      }

      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getInfo();
      JDBCQuery query = info.getQuery();
      SQLDefinition sql = query == null ? null : query.getSQLDefinition();
      String sqlStr = sql == null ? null : sql.getSQLString();
      return sqlStr != null;
   }

   private boolean isColumnIndexesSensitive() {
      ColumnSelection cols = pubColumns;

      if(cols == null || cols.getAttributeCount() == 0) {
         return false;
      }

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) cols.getAttribute(i);

         if(!col.isExpression()) {
            continue;
         }

         ExpressionRef ref = (ExpressionRef) col.getDataRef();
         String exp = ref.getExpression();

         if(StringUtils.isEmpty(exp)) {
            continue;
         }

         // incase too complex expression cause StackOverflowError(60238).
         if(exp.length() > 1000) {
            return true;
         }

         Matcher matcher = COL_INDEX_SENSITIVE_EXP_PATTERN.matcher(ref.getExpression());

         if(matcher.matches()) {
            return true;
         }
      }

      return false;
   }

   private boolean canRemoveAlias(String alias) {
      // don't remove alias if aggregated
      if(alias == null || !ainfo.isEmpty()) {
         return false;
      }

      // check if expression doesn't reference the aliased column
      for(int i = 0; i < allColumns.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) allColumns.getAttribute(i);

         if(!col.isExpression()) {
            continue;
         }

         ExpressionRef ref = (ExpressionRef) col.getDataRef();

         if(ref.getExpression().contains("field['" + alias + "']") ||
            ref.getExpression().contains("field[\"" + alias + "\"]"))
         {
            return false;
         }
      }

      return true;
   }

   private boolean isVSSnapshotExport() {
      try {
         return "true".equals(box.getVariableTable().get("__exporting_snapshot__"));
      }
      catch(Exception e) {
         return false;
      }
   }

   public static void clearCache(TableAssembly table, AssetQuerySandbox box) throws Exception {
      int[] modes = new int[]{ AssetQuerySandbox.DESIGN_MODE,
                               AssetQuerySandbox.LIVE_MODE,
                               AssetQuerySandbox.RUNTIME_MODE };


      for(int mode : modes) {
         AssetQueryCacheNormalizer cacheNormalizer = new AssetQueryCacheNormalizer(
            (TableAssembly) table.clone(), box, mode);
         AssetQuery query = AssetQuery.createAssetQuery(
            cacheNormalizer.table, mode, box, false, -1L, true, false);
         DataKey key = AssetDataCache.getCacheKey(query.getTable(), box, null, mode, true);
         AssetDataCache.removeCachedData(key);
      }
   }

   private AssetQuerySandbox box;
   private TableAssembly table;
   private ColumnSelection pubColumns;
   private ColumnSelection privColumns;
   private ColumnSelection allColumns;
   private boolean aggregated;
   private boolean distinct;
   private AggregateInfo ainfo;
   private SortInfo sortInfo;
   private boolean crosstab;
   private int mode;
   // field[row][col]
   private static final Pattern COL_INDEX_SENSITIVE_EXP_PATTERN = Pattern.compile(
      "^(\\s|\\S)*field\\[-{0,1}\\d]\\[\\d\\](\\s|\\S)*$");
   private static final Logger LOG = LoggerFactory.getLogger(AssetQueryCacheNormalizer.class);
}
