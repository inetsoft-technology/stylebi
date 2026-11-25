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
package inetsoft.uql.hive;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.jdbc.JDBCHandler;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Class that provides Hive database connection and query execution utility
 */
@SuppressWarnings("unused")
public class HiveRuntime extends TabularRuntime {
   /**
    * Execute a Hive query.
    *
    * @param query  a tabular query.
    * @param params parameters for query.
    *
    * @return the result of the query.
    */
   @Override
   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      HiveQuery query0 = (HiveQuery) query;
      HiveDataSource ds = (HiveDataSource) query0.getDataSource();
      String sql = query0.getQueryString().trim();
      ResultSet res;
      Connection conn = null;

      try{
         // Bug #82333: the connector is read-only; refuse a write or DDL statement before
         // anything is sent to Hive
         requireReadOnlyQuery(sql);
         conn = getConnection(ds);
         Statement stmt = conn.createStatement();
         res = stmt.executeQuery(sql);
         return new HiveTable(res);
      }
      catch (Exception ex) {
         LOG.warn("Failed to execute Hive query: " + sql, ex);
         Tool.addUserMessage("Failed to execute Hive query: " + sql +
                                " (" + ex.getMessage() + ")");
         handleError(params, ex, () -> null);
      }

      if(conn != null) {
         try {
            conn.close();
         }
         catch(Exception ex) {
            LOG.warn("Failed to close connection: " + ds.getName(), ex);
         }
      }

      return null;
   }

   /**
    * Test if the data source is correct (connection). Throws an exception
    * if the data source can be connected.
    *
    * @param ds Hive tabular datasource
    * @param params parameters
    */
   @Override
   public void testDataSource(TabularDataSource ds, VariableTable params)
      throws Exception {
      Connection conn = getConnection((HiveDataSource) ds);

      try {
         Statement stmt = conn.createStatement();
         stmt.close();
      }
      finally {
         conn.close();
      }
   }

   /**
    * Obtain a connection from the JDBC driver
    *
    * @return Connection
    * @throws Exception
    */
   // package-private so tests can prove a refused statement never opens a connection
   Connection getConnection(HiveDataSource ds) throws Exception{
      Driver hiveDriver = getDriver(ds);
      String url = constructUrl(ds);
      Properties props = new Properties();

      String user = ds.getUser();
      String pwd = ds.getPassword();

      props.put("user", user == null ? "" : user);
      props.put("password", pwd == null ? "" : pwd);

      assert hiveDriver != null;
      return hiveDriver.connect(url, props);
   }

   /**
    * construct the JDBC url
    *
    * @return JDBC url
    */
   private String constructUrl(HiveDataSource ds) {
      return "jdbc:hive2://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDbName();
   }

   /**
    * get the appropriate driver class based on user selection
    *
    * @return JDBC driver class
    */
   private Driver getDriver(HiveDataSource ds) throws Exception{
      try {
         return new org.apache.hive.jdbc.HiveDriver();
      }
      catch (Exception ex){
         LOG.warn("Failed to obtain Hive JDBC driver", ex);
         throw ex;
      }
   }

   /**
    * Bug #82333: only a single read-only statement is allowed: SELECT, WITH ... SELECT,
    * SHOW, DESCRIBE or DESC. Leading whitespace and comments (-- and block comments),
    * keyword case and a trailing semicolon are tolerated. Any other statement, a WITH
    * clause that feeds an INSERT, or any content after the first statement terminator is
    * refused. Applied to the final query text (after variable substitution).
    *
    * @throws IllegalArgumentException if the text is not a single read-only statement.
    */
   static void requireReadOnlyQuery(String sql) {
      if(sql == null) {
         throw new IllegalArgumentException(READ_ONLY_MESSAGE);
      }

      int n = sql.length();
      int i = 0;
      String first = null;
      boolean terminated = false;

      while(i < n) {
         char c = sql.charAt(i);
         char c2 = i + 1 < n ? sql.charAt(i + 1) : ' ';

         if(Character.isWhitespace(c)) {
            i++;
         }
         else if(c == '-' && c2 == '-') {
            while(i < n && sql.charAt(i) != '\n' && sql.charAt(i) != '\r') {
               i++;
            }
         }
         else if(c == '/' && c2 == '*') {
            int end = sql.indexOf("*/", i + 2);

            if(end < 0) {
               throw new IllegalArgumentException(READ_ONLY_MESSAGE);
            }

            i = end + 2;
         }
         else if(terminated) {
            // content after the statement terminator
            throw new IllegalArgumentException(READ_ONLY_MESSAGE);
         }
         else if(c == ';') {
            terminated = true;
            i++;
         }
         else if(c == '\'' || c == '"') {
            // string literal; a backslash escapes the next character
            i = skipQuoted(sql, i, c, true);
         }
         else if(c == '`') {
            // quoted identifier; a doubled backtick is an escaped backtick
            i = skipQuoted(sql, i, c, false);
         }
         else if(Character.isLetter(c) || c == '_') {
            int start = i;

            while(i < n && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) {
               i++;
            }

            // Locale.ROOT: under a Turkish locale "insert".toUpperCase() is not "INSERT"
            String word = sql.substring(start, i).toUpperCase(Locale.ROOT);

            if(first == null) {
               if(!READ_ONLY_KEYWORDS.contains(word)) {
                  throw new IllegalArgumentException(READ_ONLY_MESSAGE);
               }

               first = word;
            }
            else if("WITH".equals(first) && "INSERT".equals(word)) {
               // WITH ... INSERT INTO/OVERWRITE writes
               throw new IllegalArgumentException(READ_ONLY_MESSAGE);
            }
         }
         else if(first == null) {
            // the statement must start with a keyword
            throw new IllegalArgumentException(READ_ONLY_MESSAGE);
         }
         else {
            i++;
         }
      }

      if(first == null) {
         throw new IllegalArgumentException(READ_ONLY_MESSAGE);
      }
   }

   /**
    * Returns the index just past the quoted text that starts at {@code start}.
    */
   private static int skipQuoted(String sql, int start, char quote, boolean backslashEscape) {
      int n = sql.length();
      int i = start + 1;

      while(i < n) {
         char c = sql.charAt(i);

         if(backslashEscape && c == '\\') {
            i += 2;
         }
         else if(c == quote) {
            if(!backslashEscape && i + 1 < n && sql.charAt(i + 1) == quote) {
               i += 2;
            }
            else {
               return i + 1;
            }
         }
         else {
            i++;
         }
      }

      // unterminated quote
      throw new IllegalArgumentException(READ_ONLY_MESSAGE);
   }

   private static final String READ_ONLY_MESSAGE =
      "Only a single SELECT statement is allowed; the Hive connector is read-only.";
   private static final Set<String> READ_ONLY_KEYWORDS =
      Set.of("SELECT", "WITH", "SHOW", "DESCRIBE", "DESC");
   private static final Logger LOG = LoggerFactory.getLogger(HiveRuntime.class.getName());
}
