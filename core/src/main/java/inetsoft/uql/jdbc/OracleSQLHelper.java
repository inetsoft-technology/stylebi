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
 * Helper class of UniformSQL. This class generate SQL statement
 * for Oracle Database.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */

class OracleSQLHelper extends SQLHelper {
   /**
    * Get the sql helper type.
    * @return the sql helper type.
    */
   @Override
   public String getSQLHelperType() {
      return "oracle";
   }

   /**
    * Check if supports alias sorting.
    */
   @Override
   public boolean supportsAliasSorting() {
      return false;
   }

   /**
    * Get the function used for converting to lower case.
    */
   public String getDbLowerCaseFunction() {
      return "LOWER";
   }

   /**
    * Check if requires uppercased alias in a sub query.
    * @param alias the specified alias.
    * @return <tt>true</tt> if requires uppercased alias.
    */
   @Override
   public boolean requiresUpperCasedAlias(String alias) {
      String alias2 = XUtil.quoteAlias(alias, this);

      // will be quoted? do not apply uppercase
      if(!Tool.equals(alias2, alias)) {
         return false;
      }

      return true;
   }

   /**
    * Get the condition op per sql helper.
    */
   protected String getConditionOp(String op) {
      return op;
   }

   /**
    * Generate condition string for XBinaryCondition.
    * @param condition
    * @return condition string
    */
   @Override
   protected String buildConditionString(XBinaryCondition condition) {
       // @by peterx@inetsoft.com. Make oracle datasoruce support ansi join.
      if(this.isAnsiJoin()) {
         return super.buildConditionString(condition);
      }

      String op = condition.getOp();
      StringBuilder buffer = new StringBuilder();
      XExpression expression1 = condition.getExpression1();
      XExpression expression2 = condition.getExpression2();
      boolean fld1 = condition instanceof XJoin || !having &&
         expression1.getType().equals(XExpression.FIELD);
      boolean fld2 = condition instanceof XJoin || !having &&
         expression2.getType().equals(XExpression.FIELD);

      if(XBinaryCondition.isPrefixOp(op)) {
         if(condition.isIsNot()) {
            buffer.append("not ");
         }

         buffer.append(op.toString());
         buffer.append(" ");
         buffer.append(buildExpressionString(expression1, fld1));
         buffer.append(" ");
         buffer.append(buildExpressionString(expression2, fld2));
      }
      else if(XBinaryCondition.isPostfixOp(op)) {
         if(condition.isIsNot()) {
            buffer.append("not ");
         }

         buffer.append(buildExpressionString(expression1, fld1));
         buffer.append(" ");
         buffer.append(buildExpressionString(expression2, fld2));
         buffer.append(" ");
         buffer.append(op.toString());
      }
      else {
         String str1 = buildExpressionString(expression1, fld1);
         String str2 = buildExpressionString(expression2, fld2);

         if(condition.isIsNot()) {
            buffer.append("not ");
         }

         // right outer join should be "(+)="
         if(op.equals("=*")) {
            op = "(+)=";
            buffer.append(str1);
            buffer.append(" ");
            buffer.append(op);
            buffer.append(" ");
            buffer.append(str2);
         }
         // left outer join should be "=xxx(+)"
         else if(op.equals("*=")) {
            buffer.append(str1);
            buffer.append(" ");
            buffer.append("= ");
            buffer.append(str2);
            buffer.append("(+) ");
         }
         else {
            // @by larryl, if a variable is used with IN and no parenthesis
            // is added around the variable, add it automatically so the sql
            // can work correctly
	    if(op.equalsIgnoreCase("in") &&
               !(str2.startsWith("(") && str2.endsWith(")")))
            {
               str2 = "(" + str2 + ")";
            }

            buffer.append(str1);
            buffer.append(" ");
            buffer.append(op);
            buffer.append(" ");
            buffer.append(str2);
         }
      }

      return buffer.toString();
   }

   /**
    * Append the join clause to the from.
    */
   protected void appendJoinClause(StringBuilder from,
      XBinaryCondition condition, String op, String table1, String table2)
   {
      String clause = makeJoinClause(condition, op, table1, table2, null);

      // if the join is not the first one, the previous join clause needs not
      // to be put inside parens
      if(from.length() > 0 && clause.startsWith(op)) {
         if(from.toString().endsWith(", ")) {
            from.setLength(from.length() - 2);
         }
      }

      from.append(clause);
   }

   /**
    * Quote all alias for safety. Oracle has too many
    * keywords and they need to be quoted if used as alias.
    * @param same true if the alias is the same as the column string.
    * @param part true if the alias is the same as the column part.
    */
   @Override
   protected String quoteColumnAlias(String alias, boolean same, boolean part){
      // @by larryl, if the alias is the same as the column part, quoting it
      // could cause oracle invalid identifier error
      if(!part && !alias.startsWith("\"") && !alias.endsWith("\"")) {
         return '"' + alias + '"';
      }

      return super.quoteColumnAlias(alias, same, part);
   }

   /**
    * The alias is the column part of table.calias pattern.
    */
   @Override
   protected String quoteColumnPartAlias(String alias) {
      return XUtil.quoteAlias(alias, this);
   }

   /**
    * Check if alias length is limited.
    */
   @Override
   public boolean isLimitAlias() {
      return true;
   }

   /**
    * Oralce have limitation on alias length, validate it.
    */
   @Override
   protected String getValidAlias(JDBCSelection jsel, int col, String alias) {
      boolean sub = uniformSql.isSubQuery();

      if(sub) {
         boolean upper = alias == null ? false :
            requiresUpperCasedAlias(alias) &&
            JDBCSelection.isValidAlias(alias, this);

         if(upper) {
            alias = alias.toUpperCase();
         }
      }

      return jsel.getValidAlias(col, alias, this);
   }

   /**
    * Transform date format.
    */
   @Override
   protected String transformDate(String str) {
      String str_oracle = null;
      str = str.trim();

      if(str.startsWith("{d")) {
         str_oracle = str.substring(2, str.length()-1).trim();

         if(str.indexOf('-') > -1 && str.indexOf(':') > -1) {
            str_oracle = "To_Date(" + str_oracle + ", 'YYYY-MM-DD HH24:Mi:SS')";
         }
         else if(str.indexOf('-') > -1) {
      	    str_oracle = "To_Date(" + str_oracle + ", 'YYYY-MM-DD')";
         }
         else if(str.indexOf(':') > -1) {
      	    str_oracle = "To_Date(" + str_oracle + ", 'HH24:Mi:SS')";
         }
      }
      else if(str.startsWith("{ts")) {
      	 str_oracle = str.substring(3, str.length()-1).trim();
         str_oracle = "To_Date(" + str_oracle + ", 'YYYY-MM-DD HH24:Mi:SS')";
      }
      else if(str.startsWith("{t")) {
         str_oracle = str.substring(2, str.length()-1).trim();
         str_oracle = "To_Date(" + str_oracle + ", 'HH24:Mi:SS')";
      }
      else {
      	 return str;
      }

      return str_oracle;
   }

   /**
    * Check if a word is a keyword.
    * @param word the specified keyword.
    * @return <tt>true</tt> is a keyword, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isKeyword(String word) {
      return super.isKeyword(word) || keywords.contains(word);
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
      buffer.append(" where rownum <= ");
      buffer.append(maxrows);
      return buffer.toString();
   }

   /**
    * Create a query that limits the output to the specified number of rows.
    */
   @Override
   protected String generateMaxRowsClause(String sql, int maxrows) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("select * from (");
      buffer.append(sql);
      buffer.append(") inner");
      buffer.append(System.currentTimeMillis());
      buffer.append(" where rownum <= ");
      buffer.append(maxrows);
      return buffer.toString();
   }

   private static Set keywords = new HashSet(); // keywords

   static {
      keywords.add("access");
      keywords.add("account");
      keywords.add("activate");
      keywords.add("admin");
      keywords.add("advise");
      keywords.add("after");
      keywords.add("all_rows");
      keywords.add("analyze");
      keywords.add("archive");
      keywords.add("archivelog");
      keywords.add("array");
      keywords.add("arraylen");
      keywords.add("clob");
      keywords.add("clone");
      keywords.add("close_cached_open_cursors");
      keywords.add("cluster");
      keywords.add("cobol");
      keywords.add("columns");
      keywords.add("comment");
      keywords.add("committed");
      keywords.add("compatibility");
      keywords.add("compile");
      keywords.add("complete");
      keywords.add("composite_limit");
      keywords.add("compress");
      keywords.add("compute");
      keywords.add("connect_time");
      keywords.add("contents");
      keywords.add("controlfile");
      keywords.add("cost");
      keywords.add("cpu_per_call");
      keywords.add("cpu_per_session");
      keywords.add("current_schema");
      keywords.add("cycle");
      keywords.add("dangling");
      keywords.add("database");
      keywords.add("events");
      keywords.add("exceptions");
      keywords.add("exchange");
      keywords.add("excluding");
      keywords.add("exclusive");
      keywords.add("expire");
      keywords.add("explain");
      keywords.add("extent");
      keywords.add("extents");
      keywords.add("externally");
      keywords.add("failed_login_attempts");
      keywords.add("fast");
      keywords.add("file");
      keywords.add("first_rows");
      keywords.add("flagger");
      keywords.add("flob");
      keywords.add("flush");
      keywords.add("force");
      keywords.add("freelist");
      keywords.add("freelists");
      keywords.add("function");
      keywords.add("global_name");
      keywords.add("globally");
      keywords.add("isolation_level");
      keywords.add("keep");
      keywords.add("kill");
      keywords.add("label");
      keywords.add("layer");
      keywords.add("less");
      keywords.add("library");
      keywords.add("limit");
      keywords.add("link");
      keywords.add("list");
      keywords.add("lists");
      keywords.add("lob");
      keywords.add("lock");
      keywords.add("locked");
      keywords.add("log");
      keywords.add("logfile");
      keywords.add("logging");
      keywords.add("logical_reads_per_call");
      keywords.add("logical_reads_per_session");
      keywords.add("long");
      keywords.add("manage");
      keywords.add("manual");
      keywords.add("master");
      keywords.add("maxarchlogs");
      keywords.add("maxdatafiles");
      keywords.add("maxextents");
      keywords.add("maxinstances");
      keywords.add("maxlogfiles");
      keywords.add("maxloghistory");
      keywords.add("maxlogmembers");
      keywords.add("noorder");
      keywords.add("nooverride");
      keywords.add("noparallel");
      keywords.add("noresetlogs");
      keywords.add("noreverse");
      keywords.add("normal");
      keywords.add("nosort");
      keywords.add("notfound");
      keywords.add("nothing");
      keywords.add("nowait");
      keywords.add("number");
      keywords.add("nvarchar2");
      keywords.add("object");
      keywords.add("objno");
      keywords.add("objno_reuse");
      keywords.add("off");
      keywords.add("offline");
      keywords.add("oid");
      keywords.add("oidindex");
      keywords.add("old");
      keywords.add("online");
      keywords.add("opcode");
      keywords.add("optimal");
      keywords.add("optimizer_goal");
      keywords.add("organization");
      keywords.add("overflow");
      keywords.add("own");
      keywords.add("quote");
      keywords.add("range");
      keywords.add("raw");
      keywords.add("rba");
      keywords.add("rebuild");
      keywords.add("recover");
      keywords.add("recoverable");
      keywords.add("recovery");
      keywords.add("ref");
      keywords.add("referencing");
      keywords.add("refresh");
      keywords.add("rename");
      keywords.add("replace");
      keywords.add("reset");
      keywords.add("resetlogs");
      keywords.add("resize");
      keywords.add("resource");
      keywords.add("restricted");
      keywords.add("return");
      keywords.add("returning");
      keywords.add("reuse");
      keywords.add("reverse");
      keywords.add("role");
      keywords.add("roles");
      keywords.add("row");
      keywords.add("rowid");
      keywords.add("rowlabel");
      keywords.add("rownum");
      keywords.add("rule");
      keywords.add("sample");
      keywords.add("savepoint");
      keywords.add("sb4");
      keywords.add("statement_id");
      keywords.add("statistics");
      keywords.add("stop");
      keywords.add("storage");
      keywords.add("store");
      keywords.add("structure");
      keywords.add("successful");
      keywords.add("switch");
      keywords.add("synonym");
      keywords.add("sys_op_enforce_not_null$");
      keywords.add("sys_op_ntcimg$");
      keywords.add("sysdate");
      keywords.add("sysdba");
      keywords.add("sysoper");
      keywords.add("system");
      keywords.add("tables");
      keywords.add("tablespace");
      keywords.add("tablespace_no");
      keywords.add("tabno");
      keywords.add("than");
      keywords.add("the");
      keywords.add("thread");
      keywords.add("toplevel");
      keywords.add("trace");
      keywords.add("tracing");
      keywords.add("transitional");
      keywords.add("trigger");
      keywords.add("triggers");
      keywords.add("truncate");
      keywords.add("xid");
   }
}
