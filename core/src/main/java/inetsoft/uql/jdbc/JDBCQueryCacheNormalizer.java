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
import inetsoft.uql.util.SQLQuoteScanner;
import inetsoft.uql.util.XUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

      if(noneContainsSortByDistinctSql(usql) || hasPositionalReference(usql)) {
         return false;
      }

      if(StringUtils.isEmpty(sqlString)) {
         return true;
      }

      if(usql.isParseSQL() && hasMaxRow(sqlString)) {
         return false;
      }

      return usql.getParseResult() != UniformSQL.PARSE_FAILED;
   }

   /**
    * Check if the sql has a row limit keyword (top, limit, fetch first, rownum &lt;) as a
    * whole word outside string literals and quoted names. A keyword in a literal or quoted
    * name ('no limit set', "the top one") is not a row limit. (#77698)
    */
   static boolean hasMaxRow(String sql) {
      Matcher matcher = MAX_ROW_KEYWORD.matcher(sql);
      boolean[] quoted = null;

      while(matcher.find()) {
         if(quoted == null) {
            quoted = SQLQuoteScanner.findQuoted(sql);
         }

         if(!quoted[matcher.start()] && !quoted[matcher.end() - 1]) {
            return true;
         }
      }

      return false;
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
    * Check if the order by or group by of this query level refers to a select list column by
    * its position, e.g. 'order by 1' or 'group by 1, 3'. The parser stores an order by ordinal
    * as an Integer, while a query loaded from xml and a group by ordinal hold it as a String.
    */
   private static boolean hasPositionalReference(UniformSQL usql) {
      return containsPositional(usql.getOrderByFields()) || containsPositional(usql.getGroupBy());
   }

   private static boolean containsPositional(Object[] fields) {
      if(fields == null) {
         return false;
      }

      for(Object field : fields) {
         if(field instanceof Number) {
            return true;
         }

         if(field instanceof String) {
            String str = ((String) field).trim();

            if(!str.isEmpty() && str.chars().allMatch(c -> c >= '0' && c <= '9')) {
               return true;
            }
         }
      }

      return false;
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
      // an ordinal in order by or group by refers to a select list position, so sorting the
      // select list would change what it refers to. Checked ahead of the sorted hint, which may
      // be stale or inherited by a subquery from its parent. (Bug #77557)
      if(hasPositionalReference(usql)) {
         return null;
      }

      // a wildcard left in the select list (not expanded from the metadata) is one item for
      // many result columns, so the map of the items doesn't fit the result (Bug #77617)
      if(UniformSQL.hasWildcard(usql.getSelection())) {
         return null;
      }

      boolean noSortDistinct = noneContainsSortByDistinctSql(usql);

      if(!Boolean.TRUE.equals(usql.getHint(UniformSQL.HINT_SORTED_SQL, !noSortDistinct)) &&
         !isApplicable(usql) ||
         Boolean.TRUE.equals(usql.getHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, !noSortDistinct)))
      {
         return null;
      }

      // a parse-off sql string is sent as written: never clear it, and never sort its columns,
      // since its selection may be stale and doesn't describe the sql. (Bug #77483)
      if(!usql.isParseSQL() && usql.sqlstring != null) {
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

   // a row limit keyword as a whole word, in any case. A letter of any language, a digit,
   // _, $ or # next to it makes it part of a name (credit_limit, a non-ascii name)
   private static final Pattern MAX_ROW_KEYWORD = Pattern.compile(
      "(?<![\\p{L}\\p{M}\\p{N}_$#])(top|limit|fetch\\s+first)(?![\\p{L}\\p{M}\\p{N}_$#])|" +
      "(?<![\\p{L}\\p{M}\\p{N}_$#])rownum\\s*<",
      Pattern.CASE_INSENSITIVE);

   private JDBCQuery query;
   private int[] sortedColumnMap;
   private int[] originalColumnMap;
}
