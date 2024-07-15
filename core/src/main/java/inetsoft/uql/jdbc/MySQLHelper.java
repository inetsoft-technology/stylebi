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

import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;

import java.util.HashSet;
import java.util.Set;

/**
 * Helper class of UniformSQL. This class generates SQL statements
 * for a MySQL database.
 *
 * @author  InetSoft Technology
 * @since   6.0
 */
public class MySQLHelper extends SQLHelper {
   /**
    * Get the sql helper type.
    * @return the sql helper type.
    */
   @Override
   public String getSQLHelperType() {
      return "mysql";
   }

   /**
    * Check if a word is a keyword.
    * @param word the specified keyword.
    * @return <tt>true</tt> is a keyword, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isKeyword(String word) {
      return super.isKeyword(word) || keywords.contains(word) ||
         (XUtil.isQualifiedName(word) && Tool.containsCJK(word));
   }

   /**
    * Get the quote.
    * @return the quote.
    */
   @Override
   public String getQuote() {
      return "`";
   }

   /**
    * Check if requires alias in having for an expression.
    */
   @Override
   protected boolean requiresAliasInHaving() {
      return true;
   }

   /**
    * Check if supports an operation.
    * @param op the specified operation.
    * @param info the advanced information.
    * @return <tt>true</tt> if supports, <tt>false</tt> otherwise.
    */
   @Override
   public boolean supportsOperation(String op, String info) {
      if(MAXROWS.equals(op) && WHERE_SUBQUERY.equals(info)) {
         return false;
      }

      if(!CONCATENATION_TABLE.equals(op) || !UNION_TABLE.equals(info)) {
         return super.supportsOperation(op, info);
      }

      JDBCDataSource dx = uniformSql == null ?
         null : uniformSql.getDataSource();
      String version = dx == null ? null : dx.getProductVersion();

      if(version == null) {
         return super.supportsOperation(op, info);
      }

      try {
         float val  = Float.parseFloat(version);
         return val > 4.1;
      }
      catch(Exception ex) {
         // ignore it
      }

      return super.supportsOperation(op, info);
   }

   /**
    * Get a sql string for replacing the table name so a row limit is added
    * to restrict the number of rows from the table.
    */
   @Override
   protected String getTableWithLimit(String tbl, int maxrows) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("select * from ");
      buffer.append(getQuotedTableName(tbl));
      buffer.append(" limit ");
      buffer.append(maxrows);
      return buffer.toString();
   }

   /**
    * Create a query that limits the output to the specified number of rows.
    */
   @Override
   protected String generateMaxRowsClause(String sql, int maxrows) {
      // Bug #58830, if it's a mariadb query then don't add the limit clause
      // to subqueries unless it's explicitly set by a user
      if(isMariaDB() && uniformSql != null && uniformSql.isSubQuery() &&
         Boolean.FALSE.equals(uniformSql.getHint(UniformSQL.HINT_USER_MAXROWS, false)))
      {
         return sql;
      }

      StringBuilder buffer = new StringBuilder();
      buffer.append("select * from (");
      buffer.append(sql);
      buffer.append(") inner");
      buffer.append(System.currentTimeMillis());
      buffer.append(" limit ");
      buffer.append(maxrows);
      return buffer.toString();
   }

   @Override
   public boolean isApplyMaxRowsToTopLevel() {
      // add limit to top level query since we remove it from subqueries whenever possible
      return isMariaDB();
   }

   private boolean isMariaDB() {
      JDBCDataSource dx = uniformSql.getDataSource();
      return dx != null && "org.mariadb.jdbc.Driver".equalsIgnoreCase(dx.getDriver());
   }

   private static Set keywords = new HashSet(); // keywords

   static {
      keywords.add("analyze");
      keywords.add("asensitive");
      keywords.add("before");
      keywords.add("bigint");
      keywords.add("binary");
      keywords.add("blob");
      keywords.add("call");
      keywords.add("change");
      keywords.add("condition");
      keywords.add("database");
      keywords.add("databases");
      keywords.add("day_hour");
      keywords.add("day_microsecond");
      keywords.add("day_minute");
      keywords.add("day_second");
      keywords.add("delayed");
      keywords.add("deterministic");
      keywords.add("distinctrow");
      keywords.add("div");
      keywords.add("dual");
      keywords.add("each");
      keywords.add("elseif");
      keywords.add("enclosed");
      keywords.add("escaped");
      keywords.add("exit");
      keywords.add("explain");
      keywords.add("float4");
      keywords.add("float8");
      keywords.add("force");
      keywords.add("fulltext");
      keywords.add("high_priority");
      keywords.add("hour_microsecond");
      keywords.add("hour_minute");
      keywords.add("hour_second");
      keywords.add("if");
      keywords.add("ignore");
      keywords.add("infile");
      keywords.add("inout");
      keywords.add("int1");
      keywords.add("int2");
      keywords.add("int3");
      keywords.add("int4");
      keywords.add("int8");
      keywords.add("iterate");
      keywords.add("keys");
      keywords.add("kill");
      keywords.add("label");
      keywords.add("leave");
      keywords.add("limit");
      keywords.add("lines");
      keywords.add("load");
      keywords.add("localtime");
      keywords.add("localtimestamp");
      keywords.add("lock");
      keywords.add("long");
      keywords.add("longblob");
      keywords.add("longtext");
      keywords.add("loop");
      keywords.add("low_priority");
      keywords.add("mediumblob");
      keywords.add("mediumint");
      keywords.add("mediumtext");
      keywords.add("middleint");
      keywords.add("minute_microsecond");
      keywords.add("minute_second");
      keywords.add("mod");
      keywords.add("modifies");
      keywords.add("no_write_to_binlog");
      keywords.add("optimize");
      keywords.add("optionally");
      keywords.add("out");
      keywords.add("outfile");
      keywords.add("purge");
      keywords.add("raid0");
      keywords.add("range");
      keywords.add("reads");
      keywords.add("regexp");
      keywords.add("release");
      keywords.add("rename");
      keywords.add("repeat");
      keywords.add("replace");
      keywords.add("require");
      keywords.add("return");
      keywords.add("rlike");
      keywords.add("schemas");
      keywords.add("second_microsecond");
      keywords.add("sensitive");
      keywords.add("separator");
      keywords.add("show");
      keywords.add("soname");
      keywords.add("spatial");
      keywords.add("specific");
      keywords.add("sql_big_result");
      keywords.add("sql_calc_found_rows");
      keywords.add("sql_small_result");
      keywords.add("sqlexception");
      keywords.add("ssl");
      keywords.add("starting");
      keywords.add("straight_join");
      keywords.add("terminated");
      keywords.add("tinyblob");
      keywords.add("tinyint");
      keywords.add("tinytext");
      keywords.add("trigger");
      keywords.add("undo");
      keywords.add("unlock");
      keywords.add("unsigned");
      keywords.add("upgrade");
      keywords.add("use");
      keywords.add("utc_date");
      keywords.add("utc_time");
      keywords.add("utc_timestamp");
      keywords.add("varbinary");
      keywords.add("varcharacter");
      keywords.add("while");
      keywords.add("x509");
      keywords.add("xor");
      keywords.add("year_month");
      keywords.add("zerofill");
      keywords.add("cume_dist");
      keywords.add("empty");
      keywords.add("first_value");
      keywords.add("grouping");
      keywords.add("groups");
      keywords.add("json_table");
      keywords.add("lag");
      keywords.add("dense_rank");
      keywords.add("last_value");
      keywords.add("lead");
      keywords.add("nth_value");
      keywords.add("ntile");
      keywords.add("over");
      keywords.add("percent_rank");
      keywords.add("rank");
      keywords.add("recursive");
      keywords.add("rank");
      keywords.add("row_number");
      keywords.add("system");
      keywords.add("window");
   }
}
