/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.test.*;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.StringReader;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77767, since #77661 (stylebi#6244) an implicit bracket alias such as
 * {@code select t.a [Alias] from t} failed to parse on every data source, because as_clause
 * refused a bracket name without a preceding AS whatever the dialect. On SQL Server, Sybase and
 * Access a bracket is a delimited identifier and there is no subscript syntax, so any
 * {@code expr [x]} there is an alias, whatever the bracket holds. Everywhere else (and with no
 * data source) {@code expr [x]} may be a subscript or map-key access and stays refused.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLBracketAliasDialectTest {
   // the data sources whose helper reads [x] as a delimited identifier
   private static final String[] BRACKET_SOURCES =
      { "mssql", "jtds-sqlserver", "jtds-sybase", "jconnect", "ucanaccess" };
   // the data sources where [x] after an expression may be a subscript, or is unknown
   private static final String[] OTHER_SOURCES =
      { "postgresql", "h2", "derby", "oracle", "sqlite", "trino", "none" };

   // { implicit alias text, the same text with AS, the expected alias or null for "same as AS" }
   private static final String[][] ALIASES = {
      { "select a [b] from t", "select a as [b] from t", "b" },
      { "select t.a [Alias], t.c from t", "select t.a as [Alias], t.c from t", "Alias" },
      { "select sum(t.a) [Total Sales] from t group by t.c",
        "select sum(t.a) as [Total Sales] from t group by t.c", "Total Sales" },
      { "select [t].[a] [Alias] from [dbo].[t] where [t].[c] = 1",
        "select [t].[a] as [Alias] from [dbo].[t] where [t].[c] = 1", "Alias" },
      { "select a [1] from t", "select a as [1] from t", "1" },
      { "select a ['k'] from t", "select a as ['k'] from t", null },
      { "select a [i+1] from t", "select a as [i+1] from t", "i+1" },
   };

   private static final String[] REFUSED = {
      "select a [b] from t",
      "select arr [idx] from t",
      "select arr[idx] from t",
      "select arr [i+1] from t",
      "select a[1] from t",
      "select m['key'] from t",
      "select t.a [Alias], t.c from t",
   };

   // the cause: a bracket name after an expression is an alias on a bracket dialect, whatever
   // the bracket holds, the same as with AS
   @Test
   void implicitBracketAliasParsesOnBracketDialects() {
      List<Executable> rows = new ArrayList<>();

      for(String source : BRACKET_SOURCES) {
         JDBCDataSource ds = dataSource(source);

         for(String[] alias : ALIASES) {
            rows.add(() -> {
               String label = source + ": " + alias[0];
               UniformSQL sql = parseOrFail(alias[0], ds, label);
               String expected = alias[2] != null ?
                  alias[2] : parseOrFail(alias[1], ds, label).getSelection().getAlias(0);

               assertEquals(expected, sql.getSelection().getAlias(0), label);
            });
         }
      }

      assertAll(rows);
   }

   // the implicit alias builds the same model as AS [x], which main already regenerates, and
   // the regenerated sql re-parses to itself
   @Test
   void implicitBracketAliasMatchesExplicitAs() {
      List<Executable> rows = new ArrayList<>();

      for(String source : BRACKET_SOURCES) {
         JDBCDataSource ds = dataSource(source);

         for(String[] alias : ALIASES) {
            rows.add(() -> assertSameModel(alias, ds, source + ": " + alias[0]));
         }
      }

      assertAll(rows);
   }

   private static void assertSameModel(String[] alias, JDBCDataSource ds, String label) {
      UniformSQL implicit = parseOrFail(alias[0], ds, label);
      UniformSQL explicit = parseOrFail(alias[1], ds, label + " (with AS)");

      assertEquals(explicit.getSelection().getColumnCount(),
                   implicit.getSelection().getColumnCount(), label);
      assertEquals(explicit.getSelection().getAlias(0),
                   implicit.getSelection().getAlias(0), label);
      assertEquals(((JDBCSelection) explicit.getSelection()).isAliasQuoted(0),
                   ((JDBCSelection) implicit.getSelection()).isAliasQuoted(0), label);
      assertEquals(lossy(alias[1], ds), lossy(alias[0], ds), label);

      String generated = regenerate(implicit);
      assertEquals(regenerate(explicit), generated, label);
      assertEquals(generated, regenerate(parseOrFail(generated, ds, label + " -> " +
                                                     generated)), label);
   }

   // the AS form, which the implicit form must match, round trips on main
   @Test
   void explicitBracketAliasRoundTrips() {
      for(String source : BRACKET_SOURCES) {
         JDBCDataSource ds = dataSource(source);

         for(String[] alias : ALIASES) {
            String label = source + ": " + alias[1];
            String generated = regenerate(parseOrFail(alias[1], ds, label));
            assertEquals(generated, regenerate(parseOrFail(generated, ds, label + " -> " +
                                                           generated)), label);
         }
      }
   }

   // a subquery has no data source of its own, it is parsed with the statement's, so the
   // implicit [x] alias in a subquery or derived table follows the outer data source
   @Test
   void subqueryFollowsStatementDataSource() {
      String[][] subqueries = {
         { "select x from t where x in (select a [b] from u)",
           "select x from t where x in (select a as [b] from u)" },
         { "select q.b from (select a [b] from u) q", "select q.b from (select a as [b] from u) q" },
      };
      List<Executable> rows = new ArrayList<>();
      JDBCDataSource mssql = dataSource("mssql");

      for(String[] subquery : subqueries) {
         rows.add(() -> {
            String label = "mssql: " + subquery[0];
            String generated = regenerate(parseOrFail(subquery[0], mssql, label));

            assertEquals(regenerate(parseOrFail(subquery[1], mssql, label + " (with AS)")),
                         generated, label);
            assertEquals(generated, regenerate(parseOrFail(generated, mssql, label + " -> " +
                                                           generated)), label);
         });

         for(String source : new String[] { "postgresql", "none" }) {
            rows.add(() -> assertRefused(subquery[0], dataSource(source),
                                         source + ": " + subquery[0]));
         }
      }

      assertAll(rows);
   }

   // the dialect is read from the sql helper type, which a tr_TR default locale must not change
   @Test
   void implicitBracketAliasParsesInTurkishLocale() {
      Locale locale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.forLanguageTag("tr-TR"));
         JDBCDataSource ds = dataSource("mssql");

         List<Executable> rows = new ArrayList<>();

         for(int i = 0; i < 4; i++) {
            String[] alias = ALIASES[i];

            rows.add(() -> {
               UniformSQL sql = parseOrFail(alias[0], ds, "tr_TR: " + alias[0]);
               assertEquals(alias[2], sql.getSelection().getAlias(0), alias[0]);
            });
         }

         assertAll(rows);
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   // where [x] after an expression may be a subscript or map-key access (or the dialect is
   // unknown), it is still refused, as #77661 made it
   @Test
   void implicitBracketAliasStillRefusedElsewhere() {
      List<Executable> rows = new ArrayList<>();

      for(String source : OTHER_SOURCES) {
         JDBCDataSource ds = dataSource(source);

         for(String text : REFUSED) {
            rows.add(() -> assertRefused(text, ds, source + ": " + text));
         }
      }

      assertAll(rows);
   }

   private static void assertRefused(String text, JDBCDataSource ds, String label) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);

      try {
         sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      }
      catch(Exception ignore) {
         // refused
      }

      if(sql.getParseResult() == UniformSQL.PARSE_SUCCESS) {
         label += " -> alias " + sql.getSelection().getAlias(0) + ", " + regenerate(sql);
      }

      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), label);
      assertTrue(lossy(text, ds), label);
   }

   // a parser with no UniformSQL (VPM expression parses) has no dialect, so it fails closed
   @Test
   void expressionParseWithoutDialectStillRefuses() {
      String text = "(select a [b] from t)";
      SQLParser parser = new SQLParser(new SQLLexer(new StringReader(text)));

      assertThrows(Exception.class, parser::value_exp, text);
   }

   // #77661's AS check read a flag set by an action, which doesn't run while the guess-mode
   // retry of isSQLExpressionValid parses, so AS [b] was taken for an implicit bracket alias
   @Test
   void explicitBracketAliasValidInGuessMode() {
      assertTrue(XUtil.isSQLExpressionValid("(select top 1 a as b from t)"));
      assertTrue(XUtil.isSQLExpressionValid("(select top 1 a as [b] from t)"));
   }

   private static UniformSQL parseOrFail(String text, JDBCDataSource ds, String label) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      String error = "";

      try {
         sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      }
      catch(Exception ex) {
         error = ": " + ex.getClass().getSimpleName() + ": " + ex;
      }

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), label + error);
      return sql;
   }

   // lossy on a fresh object, as a saved query re-derives it
   private static boolean lossy(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.setSQLString(text, false);
      return sql.isLossy();
   }

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static JDBCDataSource dataSource(String source) {
      String helper;
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77767_" + source);
      ds.setProductVersion("19.0");

      switch(source) {
      case "mssql" -> {
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://h;databaseName=d");
         helper = "sql server";
      }
      case "jtds-sqlserver" -> {
         ds.setDriver("net.sourceforge.jtds.jdbc.Driver");
         ds.setURL("jdbc:jtds:sqlserver://h/d");
         helper = "sql server";
      }
      case "jtds-sybase" -> {
         ds.setDriver("net.sourceforge.jtds.jdbc.Driver");
         ds.setURL("jdbc:jtds:sybase://h/d");
         helper = "sybase";
      }
      case "jconnect" -> {
         ds.setDriver("com.sybase.jdbc4.jdbc.SybDriver");
         ds.setURL("jdbc:sybase:Tds:h:5000/d");
         helper = "sybase";
      }
      case "ucanaccess" -> {
         ds.setDriver("net.ucanaccess.jdbc.UcanaccessDriver");
         ds.setURL("jdbc:ucanaccess://c:/d.accdb");
         helper = "access";
      }
      // sqlite and trino have no helper of their own, so they share the generic one
      case "sqlite" -> {
         ds.setDriver("org.sqlite.JDBC");
         ds.setURL("jdbc:sqlite:d.db");
         helper = "default";
      }
      case "trino" -> {
         ds.setDriver("io.trino.jdbc.TrinoDriver");
         ds.setURL("jdbc:trino://h:8080/c/s");
         helper = "default";
      }
      case "none" -> {
         return null;
      }
      default -> {
         return SQLHelperNotEqualJoinTest.RowCompare.dataSource(source);
      }
      }

      assertEquals(helper, SQLHelper.getSQLHelper(ds).getSQLHelperType(), "helper for " + source);
      return ds;
   }
}
