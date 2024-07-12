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

import java.util.HashSet;
import java.util.Set;

/**
 * Helper class of UniformSQL. This class generates SQL statements
 * for a Sybase database.
 *
 * @author  InetSoft Technology
 * @since   6.0
 */
public class SybaseHelper extends SQLHelper {
   /**
    * Get the sql helper type.
    * @return the sql helper type.
    */
   @Override
   public String getSQLHelperType() {
      return "sybase";
   }

   /**
    * Get the function used for converting to lower case.
    */
   public String getDbLowerCaseFunction() {
      return "LOWER";
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
      return jsel.getValidAlias(col, alias, this);
   }

   /**
    * Get a sql string for replacing the table name so a row limit is added
    * to restrict the number of rows from the table.
    */
   @Override
   protected String getTableWithLimit(String tbl, int maxrows) {
      // this clause is not supported in subquery by informix
      // return "select top " + maxrows + " * from " + quoteTableName(tbl);
      return null;
   }

   /**
    * Create a query that limits the output to the specified number of rows.
    */
   @Override
   protected String generateMaxRowsClause(String sql, int maxrows) {
      // this clause is not supported in subquery by informix
      /*
      return "select top " + maxrows + " * from (" + sql + ") inner" +
         System.currentTimeMillis();
      */
      return sql;
   }

   private static Set keywords = new HashSet(); // keywords

   static {
      keywords.add("arith_overflow");
      keywords.add("break");
      keywords.add("browse");
      keywords.add("bulk");
      keywords.add("char_convert");
      keywords.add("checkpoint");
      keywords.add("clustered");
      keywords.add("compute");
      keywords.add("confirm");
      keywords.add("controlrow");
      keywords.add("data_pgs");
      keywords.add("database");
      keywords.add("dbcc");
      keywords.add("deterministic");
      keywords.add("disk");
      keywords.add("dummy");
      keywords.add("dump");
      keywords.add("endtran");
      keywords.add("errlvl");
      keywords.add("errorexit");
      keywords.add("exclusive");
      keywords.add("exit");
      keywords.add("fillfactor");
      keywords.add("holdlock");
      keywords.add("identity_insert");
      keywords.add("if");
      keywords.add("inout");
      keywords.add("kill");
      keywords.add("lineno");
      keywords.add("load");
      keywords.add("lock");
      keywords.add("mirror");
      keywords.add("mirrorexit");
      keywords.add("modify");
      keywords.add("noholdlock");
      keywords.add("nonclustered");
      keywords.add("numeric_truncation");
      keywords.add("off");
      keywords.add("offsets");
      keywords.add("once");
      keywords.add("online");
      keywords.add("out");
      keywords.add("over");
      keywords.add("perm");
      keywords.add("permanent");
      keywords.add("plan");
      keywords.add("print");
      keywords.add("proc");
      keywords.add("processexit");
      keywords.add("raiserror");
      keywords.add("readtext");
      keywords.add("reconfigure");
      keywords.add("remove");
      keywords.add("replace");
      keywords.add("replication");
      keywords.add("reserved_pgs");
      keywords.add("return");
      keywords.add("returns");
      keywords.add("role");
      keywords.add("rowcnt");
      keywords.add("rowcount");
      keywords.add("rule");
      keywords.add("save");
      keywords.add("setuser");
      keywords.add("shared");
      keywords.add("shutdown");
      keywords.add("statistics");
      keywords.add("stripe");
      keywords.add("syb_identity");
      keywords.add("syb_restree");
      keywords.add("temp");
      keywords.add("textsize");
      keywords.add("tran");
      keywords.add("trigger");
      keywords.add("truncate");
      keywords.add("tsequal");
      keywords.add("use");
      keywords.add("used_pgs");
      keywords.add("user_option");
      keywords.add("waitfor");
      keywords.add("while");
      keywords.add("writetext");
   }
}
