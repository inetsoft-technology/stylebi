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
package inetsoft.uql.jdbc;

import inetsoft.report.TableLens;
import inetsoft.report.filter.ColumnMapFilter;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.util.XUtil;
import org.apache.commons.lang.StringUtils;

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

      if(StringUtils.isEmpty(sqlString)) {
         return true;
      }

      sqlString = sqlString.toLowerCase();
      boolean hasMaxRow = sqlString.indexOf(" top ") != -1 || sqlString.indexOf(" limit ") != -1 ||
         sqlString.indexOf(" fetch first ") != -1 || sqlString.indexOf(" rownum < ") != -1;

      if(usql.isParseSQL() && hasMaxRow) {
         return false;
      }

      return true;
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
      if(columnCount > 0 && (XUtil.isParsedSQL(usql) || !usql.isParseSQL())) {
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
