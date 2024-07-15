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
package inetsoft.uql.jdbc;

import java.util.HashSet;
import java.util.Set;

/**
 * Helper class of UniformSQL. This class generates SQL statements
 * for a SQL Server database.
 *
 * @author  InetSoft Technology
 * @since   6.0
 */
public class SQLServerHelper extends SQLHelper {
   /**
    * Get the sql helper type.
    * @return the sql helper type.
    */
   @Override
   public String getSQLHelperType() {
      return "sql server";
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
      buffer.append("select top ");
      buffer.append(maxrows);
      buffer.append(" * from ");
      buffer.append(getQuotedTableName(tbl));
      return buffer.toString();
   }

   /**
    * Create a query that limits the output to the specified number of rows.
    */
   @Override
   protected String generateMaxRowsClause(String sql, int maxrows) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("select top ");
      buffer.append(maxrows);
      buffer.append(" * from (");
      buffer.append(sql);
      buffer.append(") inner");
      buffer.append(System.currentTimeMillis());
      return buffer.toString();
   }

   /**
    * Get the selection option.
    */
   @Override
   protected String getSelectionOption() {
      if(!uniformSql.isSubQuery() && outmaxrows <= 0) {
         return null;
      }

      Object[] orders = uniformSql.getOrderByFields();

      if(orders == null || orders.length == 0) {
         return null;
      }

      return "top 999999999";
   }

   /**
    * Check if supports an operation.
    * @param op the specified operation.
    * @param info the advanced information.
    * @return <tt>true</tt> if supports, <tt>false</tt> otherwise.
    */
   @Override
   public boolean supportsOperation(String op, String info) {
      if(CONCATENATION_TABLE.equals(op)) {
         return UNION_TABLE.equals(info);
      }

      return super.supportsOperation(op, info);
   }

   @Override
   protected boolean alisDuplicateTableName() {
      return true;
   }

   @Override
   protected String transformDate(String str) {
      str = str.trim();

      if(str.startsWith("{t") && !str.startsWith("{ts")) {
         return str.substring(2, str.length() - 1).trim();
      }

      return super.transformDate(str);
   }

   private static Set keywords = new HashSet();

   static {
      keywords.add("backup");
      keywords.add("break");
      keywords.add("browse");
      keywords.add("bulk");
      keywords.add("checkpoint");
      keywords.add("clustered");
      keywords.add("compute");
      keywords.add("contains");
      keywords.add("containstable");
      keywords.add("cube");
      keywords.add("current_path");
      keywords.add("current_role");
      keywords.add("cycle");
      keywords.add("data");
      keywords.add("database");
      keywords.add("dbcc");
      keywords.add("deny");
      keywords.add("depth");
      keywords.add("deref");
      keywords.add("destroy");
      keywords.add("destructor");
      keywords.add("deterministic");
      keywords.add("dictionary");
      keywords.add("disk");
      keywords.add("distributed");
      keywords.add("dummy");
      keywords.add("dump");
      keywords.add("dynamic");
      keywords.add("each");
      keywords.add("equals");
      keywords.add("errlvl");
      keywords.add("every");
      keywords.add("exit");
      keywords.add("file");
      keywords.add("fillfactor");
      keywords.add("flase");
      keywords.add("free");
      keywords.add("freetext");
      keywords.add("freetexttable");
      keywords.add("function");
      keywords.add("general");
      keywords.add("grouping");
      keywords.add("holdlock");
      keywords.add("host");
      keywords.add("identity_insert");
      keywords.add("identitycol");
      keywords.add("if");
      keywords.add("ignore");
      keywords.add("initialize");
      keywords.add("inout");
      keywords.add("iterate");
      keywords.add("kill");
      keywords.add("large");
      keywords.add("lateral");
      keywords.add("less");
      keywords.add("limit");
      keywords.add("lineno");
      keywords.add("load");
      keywords.add("localtime");
      keywords.add("localtimestamp");
      keywords.add("locator");
      keywords.add("map");
      keywords.add("modifies");
      keywords.add("modify");
      keywords.add("nclob");
      keywords.add("new");
      keywords.add("nocheck");
      keywords.add("nonclustered");
      keywords.add("object");
      keywords.add("off");
      keywords.add("offsets");
      keywords.add("old");
      keywords.add("opendatasource");
      keywords.add("openquery");
      keywords.add("openrowset");
      keywords.add("openxml");
      keywords.add("operation");
      keywords.add("ordinality");
      keywords.add("out");
      keywords.add("over");
      keywords.add("parameter");
      keywords.add("parameters");
      keywords.add("path");
      keywords.add("percent");
      keywords.add("plan");
      keywords.add("postfix");
      keywords.add("prefix");
      keywords.add("preorder");
      keywords.add("print");
      keywords.add("proc");
      keywords.add("raiserror");
      keywords.add("reads");
      keywords.add("readtext");
      keywords.add("reconfigure");
      keywords.add("recursive");
      keywords.add("ref");
      keywords.add("referencing");
      keywords.add("replication");
      keywords.add("restore");
      keywords.add("result");
      keywords.add("return");
      keywords.add("returns");
      keywords.add("role");
      keywords.add("rollup");
      keywords.add("routine");
      keywords.add("row");
      keywords.add("rowcount");
      keywords.add("rowguidcol");
      keywords.add("rule");
      keywords.add("save");
      keywords.add("savepoint");
      keywords.add("scope");
      keywords.add("search");
      keywords.add("sequence");
      keywords.add("sets");
      keywords.add("setuser");
      keywords.add("shutdown");
      keywords.add("specific");
      keywords.add("specifictype");
      keywords.add("sqlexception");
      keywords.add("start");
      keywords.add("state");
      keywords.add("statement");
      keywords.add("static");
      keywords.add("statistics");
      keywords.add("structure");
      keywords.add("terminate");
      keywords.add("textsize");
      keywords.add("than");
      keywords.add("top");
      keywords.add("tran");
      keywords.add("treat");
      keywords.add("trigger");
      keywords.add("truncate");
      keywords.add("tsequal");
      keywords.add("under");
      keywords.add("unnest");
      keywords.add("updatetext");
      keywords.add("use");
      keywords.add("variable");
      keywords.add("waitfor");
      keywords.add("while");
      keywords.add("without");
      keywords.add("writetext");
      keywords.add("merge");
      keywords.add("pivot");
      keywords.add("revert");
      keywords.add("semantickeyphrasetable");
      keywords.add("semanticsimilaritydetailstable");
      keywords.add("semanticsimilaritytable");
      keywords.add("tablesample");
      keywords.add("try_convert");
      keywords.add("unpivot");
   }
}
