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
package inetsoft.uql.jdbc;

import inetsoft.report.TableLens;
import inetsoft.report.filter.ColumnMapFilter;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.util.XUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

/**
 * Transforms the jdbc sql query such that it hits the cache more often
 */
public class JDBCQueryCacheNormalizer {
   public JDBCQueryCacheNormalizer(JDBCQuery query) {
      this.query = query;
      prepareQuery();
   }

   /**
    * Prepare the jdbc query
    */
   private void prepareQuery() {
      SQLDefinition def = query.getSQLDefinition();

      if(def instanceof UniformSQL) {
         UniformSQL usql = (UniformSQL) def;

         if(!isApplicable(usql)) {
            return;
         }

         usql.setHint(UniformSQL.HINT_SORTED_SQL, true);
         usql.clearCachedString();
         sortedColumnMap = generateSortedColumnMap(usql);
         originalColumnMap = generateOriginalColumnMap(sortedColumnMap);
      }
   }

   /**
    * Don't support sorted column for sql query with maxrow setting, since the sql string maybe
    * complex and difficult to extract the correct maxrow.
    */
   private static boolean isApplicable(UniformSQL usql) {
      String sqlString = usql.sqlstring;
      Object[] orderByFields = usql.getOrderByFields();

      if(noneContainsSortByDistinctSql(usql)) {
         return false;
      }

      // Sorting the SELECT columns reorders them, which invalidates any positional GROUP BY
      // <ordinal> reference (the parser keeps group-by ordinals literal — unlike ORDER BY
      // ordinals, which are resolved to column names). Reordering "select a, count(*) ... group
      // by 1" to "select count(*), a ... group by 1" makes the ordinal point at the aggregate
      // and the database rejects it. Skip column-sort normalization for such queries so the
      // original column order (and the ordinal it refers to) is preserved. An ordinal is held as
      // an Integer in-memory but is deserialized as a numeric String when the worksheet is
      // reloaded (UniformSQL.parseXML), so guard against both forms.
      Object[] groupBy = usql.getGroupBy();

      if(groupBy != null) {
         for(Object g : groupBy) {
            if(g instanceof Integer ||
               g instanceof String && ((String) g).matches("\\d+"))
            {
               return false;
            }
         }
      }

      if(StringUtils.isEmpty(sqlString)) {
         return true;
      }

      sqlString = sqlString.toLowerCase();
      boolean hasMaxRow = sqlString.indexOf(" top ") != -1 || sqlString.indexOf(" limit ") != -1 ||
         sqlString.indexOf(" fetch first ") != -1 || sqlString.indexOf(" rownum < ") != -1;

      if(usql.isParseSQL() && hasMaxRow) {
         return false;
      }

      return usql.getParseResult() != UniformSQL.PARSE_FAILED;
   }

   public boolean isClearedSqlString() {
      SQLDefinition def = query.getSQLDefinition();

      if(def instanceof UniformSQL) {
         UniformSQL usql = (UniformSQL) def;
         return Boolean.TRUE.equals(usql.getHint(UniformSQL.HINT_CLEARED_SQL_STRING, true));
      }

      return false;
   }

   /**
    * Transform the resulting table lens to match the original sql
    */
   public TableLens transformTableLens(TableLens tableLens) {
      if(originalColumnMap == null || originalColumnMap.length == 0) {
         return tableLens;
      }

      return new ColumnMapFilter(tableLens, originalColumnMap);
   }

   public int[] getSortedColumnMap() {
      return sortedColumnMap;
   }

   public int[] getOriginalColumnMap() {
      return originalColumnMap;
   }

   private static boolean noneContainsSortByDistinctSql(UniformSQL usql) {
      Object[] orderByFields = usql.getOrderByFields();

      return usql.isDistinct() && (orderByFields == null || orderByFields.length == 0);
   }

   /**
    * Returns an index mapping that can be used to transform a sql selection such that the
    * columns are sorted.
    * <p>
    * For example, given a sql like so 'select b, a, c from ...', the mapping returned will be:
    * [0] = 1
    * [1] = 0
    * [2] = 2
    * in order to transform it into a sql that looks like this 'select a, b, c from ...'
    */
   public static int[] generateSortedColumnMap(UniformSQL usql) {
      boolean noSortDistinct = noneContainsSortByDistinctSql(usql);

      if(!Boolean.TRUE.equals(usql.getHint(UniformSQL.HINT_SORTED_SQL, !noSortDistinct)) &&
         !isApplicable(usql) ||
         Boolean.TRUE.equals(usql.getHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, !noSortDistinct)))
      {
         return null;
      }

      XSelection selection = usql.getSelection();
      int columnCount = selection.getColumnCount();

      // don't clear sqlstring for sql query.
      // also don't clear for lossy queries (e.g. ClickHouse map key access m['key']) because
      // the raw SQL cannot be accurately regenerated from the column definitions. (Bug #72243)
      if(columnCount > 0 && (XUtil.isParsedSQL(usql) || !usql.isParseSQL()) && !usql.isLossy()) {
         usql.sqlstring = null;
         usql.setHint(UniformSQL.HINT_CLEARED_SQL_STRING, true);
      }

      Integer[] columnsIndex = new Integer[columnCount];

      for(int i = 0; i < columnCount; i++) {
         columnsIndex[i] = i;
      }

      Arrays.sort(columnsIndex,
         (index1, index2) -> selection.getColumn(index1).compareTo(selection.getColumn(index2)));

      return Arrays.stream(columnsIndex).mapToInt(v -> v.intValue()).toArray();
   }

   /**
    * Given the sql with sorted columns, this method returns the index mapping that will transform
    * the sql back to its original column ordering.
    */
   public static int[] generateOriginalColumnMap(int[] sortedColumnMap) {
      if(sortedColumnMap == null) {
         return null;
      }

      int[] map = new int[sortedColumnMap.length];

      for(int i = 0; i < map.length; i++) {
         for(int j = 0; j < sortedColumnMap.length; j++) {
            if(sortedColumnMap[j] == i) {
               map[i] = j;
               break;
            }
         }
      }

      return map;
   }

   private JDBCQuery query;
   private int[] sortedColumnMap;
   private int[] originalColumnMap;
}
