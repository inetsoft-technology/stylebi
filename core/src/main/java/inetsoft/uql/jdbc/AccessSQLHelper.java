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

import inetsoft.util.Tool;

import java.util.HashSet;
import java.util.Set;

/**
 * Helper class of UniformSQL. This class generate SQL statement
 * from Access Database.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class AccessSQLHelper extends SQLHelper {
   /**
    * Get the sql helper type.
    * @return the sql helper type.
    */
   @Override
   public String getSQLHelperType() {
      return "access";
   }

   /**
    * Get the order by column of a field.
    */
   @Override
   protected String getOrderByColumn(String field) {
      return getAliasColumn(field);
   }

   /**
    * The alias has special character should be quoted as "alias".
    * @param same true if the alias is the same as the column string.
    * @param part true if the alias is the same as the column part.
    */
   @Override
   protected String quoteColumnAlias(String alias, boolean same, boolean part){
      String val;

      if(!same) {
         // @by larryl, is passing ', the alias is always quoted, which is not
         // correct and can cause problems for oracle
         val = super.quoteColumnAlias(alias, same, part);
      }
      else {
         // Use own supported quote symbol to quote column alias
         val = getQuote() + alias + getQuote();
      }

      // @by larryl, if a query is generated from model, the column alias is
      // set to 'entity.attribute'. Access does not allow punctuations in the
      // alias. That could cause the query to fail. Since the JDBCTableNode
      // uses the alias in the XSelect as the column header, the alias returned
      // from the query is actually not important to the out. So this should
      // solve that problem.
      return Tool.replaceAll(val, ".", "_");
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

   @Override
   public String getQuote() {
      return "`";
   }

   private static Set keywords = new HashSet(); // keywords

   static {
      keywords.add("alphanumeric");
      keywords.add("binary");
      keywords.add("byte");
      keywords.add("currency");
      keywords.add("database");
      keywords.add("datetime");
      keywords.add("distinctrow");
      keywords.add("float4");
      keywords.add("float8");
      keywords.add("general");
      keywords.add("guid");
      keywords.add("ieeedouble");
      keywords.add("ieeesingle");
      keywords.add("integer1");
      keywords.add("integer2");
      keywords.add("integer4");
      keywords.add("logical");
      keywords.add("logical1");
      keywords.add("long");
      keywords.add("longbinary");
      keywords.add("longtext");
      keywords.add("money");
      keywords.add("note");
      keywords.add("number");
      keywords.add("owneraccess");
      keywords.add("parameters");
      keywords.add("percent");
      keywords.add("pivot");
      keywords.add("short");
      keywords.add("single");
      keywords.add("string");
      keywords.add("tableid");
      keywords.add("text");
      keywords.add("top");
      keywords.add("transform");
      keywords.add("varbinary");
      keywords.add("yesno");
   }
}
