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
import inetsoft.uql.VariableTable;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.XUtil;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77642, a binary minus (or {@code &}) after a qualified column, as the only argument of
 * a function in the select list or order by ({@code abs(a.V - 5)}), or as a group by item
 * ({@code group by a.V - 5}), was regenerated as one quoted column ({@code abs(a."V-5")}),
 * which the database rejects. {@code XUtil.isQualifiedName} accepts {@code -} and {@code &}
 * as name characters, and SQLHelper used it to decide that the text is a column path. The
 * parser keeps a column whose name has one of them quoted, so outside quotes it is an operator.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SQLHelperFunctionArgOperatorTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperFunctionArgOperatorTest {
   @BeforeAll
   static void createTables() throws Exception {
      Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver");

      try(Connection conn = DriverManager.getConnection(DERBY_URL + ";create=true");
          Statement stmt = conn.createStatement())
      {
         stmt.execute("create table a (id int, V int, W int, price int, cost int, s varchar(10), " +
                         "\"my-col\" int)");
         stmt.execute("insert into a values (1, 2, 1, 10, 4, 'x', 7), (2, 9, 3, 20, 8, 'y', 8), " +
                         "(3, 4, 6, 15, 15, 'z', 7)");
         stmt.execute("create table b (id int, W int, k varchar(10))");
         stmt.execute("insert into b values (1, 3, 'u'), (2, 6, 'v'), (3, 8, 'u')");
      }
   }

   @AfterAll
   static void dropTables() {
      try {
         DriverManager.getConnection(DERBY_URL + ";drop=true");
      }
      catch(SQLException ex) {
         // derby reports a successful drop as an exception
      }
   }

   /**
    * The reported shapes are written as parsed on every helper that stores the segments
    * unquoted. Was abs(a."V-5"), sum(a."price-a.cost"), group by a."V-5" and so on.
    */
   @Test
   void operatorArgumentIsWrittenAsParsed() throws Exception {
      for(String key : helpers().keySet()) {
         for(String[] shape : SHAPES) {
            String generated = regenerate(parse(shape[0], key));
            String label = key + ": " + shape[0] + " -> " + generated;

            assertFalse(QUOTED_OPERATOR.matcher(generated).find(), label);

            if(!key.equals("postgresql")) {
               assertTrue(generated.contains(shape[1]), label);
            }

            // round-trip guard
            assertEquals(generated, regenerate(parse(generated, key)), label);
         }
      }
   }

   /**
    * The exact output of the reported shapes. PostgreSQL quotes every segment when it parses,
    * so its output was already right and is unchanged.
    */
   @Test
   void operatorArgumentOutput() throws Exception {
      for(String[] row : EXPECTED) {
         List<String> keys = row[0].equals("*") ? NOT_POSTGRESQL : List.of(row[0]);

         for(String key : keys) {
            if(row[0].equals("*") && OVERRIDDEN.contains(key + "|" + row[1])) {
               continue;
            }

            assertEquals(row[2], regenerate(parse(row[1], key)), key + ": " + row[1]);
         }
      }
   }

   /**
    * {@code &} is bitwise and on SQL Server and MySQL. Was abs(a."V & 5") and abs(a.`V & 5`).
    */
   @Test
   void ampersandIsAnOperator() throws Exception {
      for(String key : new String[] { "sqlserver", "mysql", "default", "derby", "h2" }) {
         assertEquals("select abs(a.V & 5) from a", regenerate(parse("select abs(a.V & 5) from a", key)), key);
         assertEquals("select sum(a.flags & 4) from a group by a.id",
                      regenerate(parse("select sum(a.flags & 4) from a group by a.id", key)), key);
         assertEquals("select a.V & 5, count(*) from a group by a.V & 5",
                      regenerate(parse("select a.V & 5, count(*) from a group by a.V & 5", key)), key);
         assertEquals("select a.V from a order by abs(a.V & 5) asc",
                      regenerate(parse("select a.V from a order by abs(a.V & 5)", key)), key);
      }

      assertEquals("select A.V from a order by abs(a.V & 5) asc",
                   regenerate(parse("select a.V from a order by abs(a.V & 5)", "oracle")));
      assertEquals("select abs(\"a\".\"V\" & 5) from \"a\"",
                   regenerate(parse("select abs(a.V & 5) from a", "postgresql")));
   }

   /**
    * The regenerated sql returns the rows of the sql as written on Derby, with the Derby
    * helper. Each of the reported shapes failed with "Column 'A.V-5' is either not in any table".
    */
   @Test
   void rowsMatchOnDerby() throws Exception {
      String[] queries = {
         "select abs(a.V - 5) from a",
         "select abs(a.V - 5) as x from a",
         "select sum(a.V - 5) from a group by a.id",
         "select sum(a.price - a.cost) from a",
         "select abs(a.V - 5 - a.W) from a",
         "select sum(a.V - b.W) from a, b where a.id = b.id",
         "select count(distinct a.V - 5) from a",
         "select a.id, count(distinct a.V - 5) from a group by a.id",
         "select a.V - 5, count(*) from a group by a.V - 5",
         "select count(*) from a group by a.V - 5",
         "select a.V from a order by abs(a.V - 5)",
         "select abs(a.V - 5) as x from a order by abs(a.V - 5)",
         "select max(a.V - 5) as m from a order by max(a.V - 5)",
         "select a.id from a where a.V in (select abs(b.W - 5) from b)",
         "select (select max(b.W - 1) from b where b.id = a.id), a.id from a",
         "select x.m from (select abs(a.V - 5) as m from a) x",
         "select x.m from (select sum(a.price - a.cost) as m from a group by a.id) x",
         // controls, unchanged
         "select abs(a.V + a.W) from a",
         "select mod(a.V * 2, 7) from a",
         "select abs(a.V / a.W) from a",
         "select upper(a.s || 'x') from a",
         "select abs(-a.V) from a",
         "select abs(abs(a.V - 5)) from a",
         "select coalesce(a.V - 5, 0) from a",
         "select a.V - 5 from a",
         "select abs(a.V) - 5 from a",
         "select abs(5 - a.V) from a",
         "select a.id from a where abs(a.V - 5) > 1",
         "select a.id, sum(a.W) from a group by a.id having sum(a.W - 5) > -3",
         // a quoted hyphenated column, parsed
         "select sum(a.\"my-col\") from a",
         "select max(a.\"my-col\") from a order by max(a.\"my-col\")",
         "select a.\"my-col\", count(*) from a group by a.\"my-col\"",
         "select count(*) from a group by a.\"my-col\"",
         "select a.id from a order by a.\"my-col\", a.id",
      };

      try(Connection conn = DriverManager.getConnection(DERBY_URL);
          Statement stmt = conn.createStatement())
      {
         for(String query : queries) {
            UniformSQL sql = parse(query, "derby");
            assertEquals("DerbyHelper", SQLHelper.getSQLHelper(sql).getClass().getSimpleName());
            String generated = regenerate(sql);
            List<String> expected = rows(stmt, query);

            assertFalse(expected.isEmpty() || expected.get(0).startsWith("ERR"), query + " " + expected);
            assertEquals(expected, rows(stmt, generated), query + " -> " + generated);
         }
      }
   }

   /**
    * A text that isn't a column path any more goes on to the later group by branches, which
    * write it as is: Derby (10.3 and later) groups by the expression.
    */
   @Test
   void groupByExpressionOnDerby() throws Exception {
      assertEquals("select a.V-5, count(*) from a group by a.V-5",
                   regenerate(parse("select a.V - 5, count(*) from a group by a.V - 5", "derby")));
      assertEquals("select a.V-5 as d, count(*) from a group by a.V-5",
                   regenerate(parse("select a.V - 5 as d, count(*) from a group by a.V - 5", "derby")));
      assertEquals("select count(*) from a group by a.price-a.cost",
                   regenerate(parse("select count(*) from a group by a.price - a.cost", "derby")));
   }

   /**
    * An unset parameter removes the condition of a single row select list subquery (#77706),
    * which is then regenerated. Was (select sum(b."W-5") from b).
    */
   @Test
   void selectListSubqueryWithUnsetParameter() throws Exception {
      String query = "select a.id, (select sum(b.W - 5) from b where b.k = $(p)) as s from a";

      for(String key : new String[] { "default", "derby", "oracle" }) {
         UniformSQL sql = parse(query, key);
         XUtil.validateConditions(null, sql, new VariableTable(), true, false);
         String generated = regenerate(sql);

         assertTrue(generated.contains("(select sum(b.W-5) from b )"), key + ": " + generated);
         assertFalse(generated.contains("$(p)"), key + ": " + generated);
      }
   }

   /**
    * A hyphenated column of a query built in the query editor, which records the table of
    * every column it adds, is still quoted in the select list and the group by.
    */
   @Test
   void editorBuiltHyphenatedColumnIsQuoted() throws Exception {
      for(String key : new String[] { "default", "derby", "oracle", "sqlserver", "mysql" }) {
         UniformSQL sql = editorBuilt(key);
         String q = key.equals("mysql") ? "`" : "\"";
         String generated = regenerate(sql);

         assertTrue(generated.endsWith("group by a." + q + "my-col" + q), key + ": " + generated);
         assertTrue(generated.contains("a." + q + "my-col" + q + ","), key + ": " + generated);
      }

      try(Connection conn = DriverManager.getConnection(DERBY_URL);
          Statement stmt = conn.createStatement())
      {
         String generated = regenerate(editorBuilt("derby"));
         assertEquals(rows(stmt, "select a.\"my-col\", count(*) from a group by a.\"my-col\""),
                      rows(stmt, generated), generated);
      }
   }

   /**
    * A worksheet merged into its query: an aggregate of a column is quoted by PreAssetQuery
    * (sum(a."my-col")) and the group column is recorded with its table, in the selection or in
    * the backup selection when it is hidden. Both are still quoted.
    */
   @Test
   void mergedHyphenatedColumnIsQuoted() throws Exception {
      try(Connection conn = DriverManager.getConnection(DERBY_URL);
          Statement stmt = conn.createStatement())
      {
         for(boolean visible : new boolean[] { true, false }) {
            for(String key : new String[] { "default", "derby", "oracle", "sqlserver" }) {
               String generated = regenerate(merged(key, visible));

               assertTrue(generated.contains("sum(a.\"my-col\")"), key + ": " + generated);
               assertTrue(generated.endsWith("group by a.\"my-col\""), key + ": " + generated);
            }

            String generated = regenerate(merged("derby", visible));
            String expected = visible ?
               "select a.\"my-col\", sum(a.\"my-col\") from a group by a.\"my-col\"" :
               "select sum(a.\"my-col\") from a group by a.\"my-col\"";
            assertEquals(rows(stmt, expected), rows(stmt, generated), generated);
         }
      }
   }

   /**
    * Known limit: an unquoted hyphenated path with no table record and no quote record can't
    * be told from a minus, so it is written as is (was quoted). No producer of it is known: the
    * parser keeps a hyphenated column quoted, the query editor and the worksheet merge record
    * the table or quote the column.
    */
   @Test
   void unrecordedHyphenatedPathIsWrittenAsIs() throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource("derby"));
      sql.addTable("a");
      XSelection selection = sql.getSelection();
      selection.addColumn("sum(a.my-col)");
      sql.setGroupBy(new Object[] { "a.my-col" });

      assertEquals("select sum(a.my-col) from a group by a.my-col", regenerate(sql));
   }

   // the selection and group by of the query editor: QueryManagerService.addColumns records
   // the table of every column it adds
   private static UniformSQL editorBuilt(String key) throws Exception {
      UniformSQL sql = new UniformSQL();
      JDBCDataSource ds = dataSource(key);

      if(ds != null) {
         sql.setDataSource(ds);
      }

      sql.addTable("a");
      JDBCSelection selection = (JDBCSelection) sql.getSelection();
      selection.addColumn("a.my-col");
      selection.setTable("a.my-col", "a");
      int index = selection.addColumn("count(*)");
      selection.setExpression(index, true);
      sql.setGroupBy(new Object[] { "a.my-col" });
      return sql;
   }

   // the query of a worksheet table grouped by a.my-col with sum(a.my-col), as
   // PreAssetQuery.merge leaves it: the original selection is the backup selection, an
   // aggregate is quoted by getColumnString, a group column is written unquoted with its table
   private static UniformSQL merged(String key, boolean visible) throws Exception {
      UniformSQL sql = new UniformSQL();
      JDBCDataSource ds = dataSource(key);

      if(ds != null) {
         sql.setDataSource(ds);
      }

      sql.addTable("a");
      JDBCSelection backup = new JDBCSelection();
      backup.addColumn("a.my-col");
      backup.setTable("a.my-col", "a");
      sql.setBackupSelection(backup);

      JDBCSelection selection = (JDBCSelection) sql.getSelection();

      if(visible) {
         selection.addColumn("a.my-col");
         selection.setTable("a.my-col", "a");
      }

      int index = selection.addColumn("sum(a.\"my-col\")");
      selection.setExpression(index, false);
      selection.setAggregate("sum(a.\"my-col\")", true);
      sql.setGroupBy(new Object[] { "a.my-col" });
      return sql;
   }

   // the input, and the function text the output must contain on helpers that store the
   // segments unquoted
   private static final String[][] SHAPES = {
      { "select abs(a.V - 5) from a", "abs(a.V-5)" },
      { "select abs(a.v-5) from a", "abs(a.v-5)" },
      { "select sum(a.V - 5) from a group by a.id", "sum(a.V-5)" },
      { "select sum(a.price - a.cost) from a", "sum(a.price-a.cost)" },
      { "select abs(a.V - b.W) from a, b where a.id = b.id", "abs(a.V-b.W)" },
      { "select abs(a.V - 5 - a.W) from a", "abs(a.V-5-a.W)" },
      { "select count(distinct a.V - 5) from a", "count(distinct a.V-5)" },
      { "select abs(a.V - 5) as x from a", "abs(a.V-5)" },
      { "select max(a.V - 5) as m from a order by max(a.V - 5)", "max(a.V-5)" },
      { "select a.V from a order by abs(a.V - 5)", "order by abs(a.V-5) asc" },
      { "select a.id from a where a.V in (select abs(b.W - 5) from b)", "select abs(b.W-5) from b" },
      { "select a.id, (select max(b.W - 1) from b where b.id = a.id) from a", "(select max(b.W-1) from b" },
      { "select x.m from (select abs(a.V - 5) as m from a) x", "select abs(a.V-5)" },
      { "select a.V - 5, count(*) from a group by a.V - 5", "group by a.V-5" },
      { "select count(*) from a group by a.price - a.cost", "group by a.price-a.cost" },
      { "select abs(a.V & 5) from a", "abs(a.V & 5)" },
      { "select a.V & 5, count(*) from a group by a.V & 5", "group by a.V & 5" },
   };

   // a quoted segment holding an operator, a.`V-5`, a."V & 5", a."price-a.cost"
   private static final Pattern QUOTED_OPERATOR = Pattern.compile("\\.[\"`][^\"`]*[-&][^\"`]*[\"`]");

   // helper (* for all but postgresql), input, output. The output of the controls is the
   // output before this change
   private static final String[][] EXPECTED = {
      { "*", "select abs(a.V - 5) from a", "select abs(a.V-5) from a" },
      { "*", "select sum(a.price - a.cost) from a", "select sum(a.price-a.cost) from a" },
      { "*", "select count(distinct a.V - 5) from a", "select count(distinct a.V-5) from a" },
      { "*", "select a.V from a order by abs(a.V - 5)", "select a.V from a order by abs(a.V-5) asc" },
      { "*", "select a.V - 5, count(*) from a group by a.V - 5", "select a.V-5, count(*) from a group by a.V-5" },
      { "*", "select a.id from a where a.V in (select abs(b.W - 5) from b)",
        "select a.id from a where a.V IN ( select abs(b.W-5) from b)" },
      { "*", "select a.id, (select max(b.W - 1) from b where b.id = a.id) from a",
        "select (select max(b.W-1) from b where b.id = a.id ), a.id from a" },
      { "*", "select x.m from (select abs(a.V - 5) as m from a) x",
        "select x.m from ( select abs(a.V-5) as m from a) x" },
      { "oracle", "select a.V from a order by abs(a.V - 5)", "select A.V from a order by abs(a.V-5) asc" },
      { "oracle", "select a.id from a where a.V in (select abs(b.W - 5) from b)",
        "select A.ID from a where a.V IN ( select abs(b.W-5) from b)" },
      { "oracle", "select a.id, (select max(b.W - 1) from b where b.id = a.id) from a",
        "select (select max(b.W-1) from b where b.id = a.id ), A.ID from a" },
      { "oracle", "select x.m from (select abs(a.V - 5) as m from a) x",
        "select x.\"m\" from ( select abs(a.V-5) as \"m\" from a) x" },
      { "oracle", "select max(a.V - 5) as m from a order by max(a.V - 5)",
        "select max(a.V-5) as \"m\" from a order by max(a.V-5) asc" },
      { "postgresql", "select abs(a.V - 5) from a", "select abs(\"a\".\"V\"-5) from \"a\"" },
      { "postgresql", "select sum(a.price - a.cost) from a", "select sum(\"a\".\"price\"-\"a\".\"cost\") from \"a\"" },
      { "postgresql", "select a.V - 5, count(*) from a group by a.V - 5",
        "select \"a\".\"V\"-5, count(*) from \"a\" group by \"a\".\"V\"-5" },
      // controls
      { "*", "select abs(a.V + a.W) from a", "select abs(a.V+a.W) from a" },
      { "*", "select round(a.V * 2, 1) from a", "select round(a.V*2,1) from a" },
      { "*", "select abs(a.V / a.W) from a", "select abs(a.V/a.W) from a" },
      { "*", "select upper(a.s || 'x') from a", "select upper(a.s || 'x') from a" },
      { "*", "select abs(-a.V) from a", "select abs(- a.V) from a" },
      { "*", "select abs(abs(a.V - 5)) from a", "select abs(abs(a.V-5)) from a" },
      { "*", "select coalesce(a.V - 5, 0) from a", "select coalesce(a.V-5,0) from a" },
      { "*", "select a.V - 5 from a", "select a.V-5 from a" },
      { "*", "select abs(a.V) - 5 from a", "select abs(a.V)-5 from a" },
      { "*", "select abs(V - 5) from a", "select abs(V-5) from a" },
      { "*", "select abs(5 - a.V) from a", "select abs(5-a.V) from a" },
      { "*", "select sum(a.V) from a", "select sum(a.V) from a" },
      { "*", "select a.id from a where abs(a.V - 5) > 1", "select a.id from a where abs(a.V-5) > 1" },
      { "*", "select a.id, sum(a.W) from a group by a.id having sum(a.W - 5) > 1",
        "select a.id, sum(a.W) from a group by a.id having sum(a.W-5) > 1" },
      { "*", "select sum(a.\"my-col\") from a", "select sum(a.\"my-col\") from a" },
      { "*", "select max(a.\"my-col\") from a order by max(a.\"my-col\")",
        "select max(a.\"my-col\") from a order by max(a.\"my-col\") asc" },
      { "*", "select a.\"my-col\", count(*) from a group by a.\"my-col\"",
        "select a.\"my-col\", count(*) from a group by a.\"my-col\"" },
      { "*", "select count(*) from a group by a.\"my-col\"", "select count(*) from a group by a.\"my-col\"" },
      { "*", "select a.id from a order by a.\"my-col\"", "select a.id from a order by a.\"my-col\" asc" },
      { "oracle", "select a.id from a where abs(a.V - 5) > 1", "select A.ID from a where abs(a.V-5) > 1" },
      { "oracle", "select a.id, sum(a.W) from a group by a.id having sum(a.W - 5) > 1",
        "select A.ID, sum(a.W) from a group by a.id having sum(a.W-5) > 1" },
      { "oracle", "select a.id from a order by a.\"my-col\"", "select A.ID from a order by a.\"my-col\" asc" },
      { "mysql", "select sum(a.\"my-col\") from a", "select sum(a.`my-col`) from a" },
      { "mysql", "select max(a.\"my-col\") from a order by max(a.\"my-col\")",
        "select max(a.`my-col`) from a order by max(a.`my-col`) asc" },
      { "mysql", "select a.\"my-col\", count(*) from a group by a.\"my-col\"",
        "select a.`my-col`, count(*) from a group by a.`my-col`" },
      { "mysql", "select count(*) from a group by a.\"my-col\"", "select count(*) from a group by a.`my-col`" },
      { "mysql", "select a.id from a order by a.\"my-col\"", "select a.id from a order by a.`my-col` asc" },
      { "postgresql", "select sum(a.\"my-col\") from a", "select sum(\"a\".\"my-col\") from \"a\"" },
      { "postgresql", "select coalesce(a.V - 5, 0) from a", "select coalesce(\"a\".\"V\"-5,0) from \"a\"" },
   };

   // the helper|input rows of EXPECTED that a helper-specific row replaces
   private static final Set<String> OVERRIDDEN = new HashSet<>();

   static {
      for(String[] row : EXPECTED) {
         if(!row[0].equals("*")) {
            OVERRIDDEN.add(row[0] + "|" + row[1]);
         }
      }
   }

   private static final List<String> NOT_POSTGRESQL =
      List.of("default", "generic", "derby", "oracle", "h2", "h2-ansi", "sqlserver", "mysql");

   private static Map<String, JDBCDataSource> helpers() {
      Map<String, JDBCDataSource> helpers = new LinkedHashMap<>();

      for(String key : NOT_POSTGRESQL) {
         helpers.put(key, dataSource(key));
      }

      helpers.put("postgresql", dataSource("postgresql"));
      return helpers;
   }

   // a data source with a real driver and url, so the helper of the database is used
   private static JDBCDataSource dataSource(String key) {
      return switch(key) {
         case "default" -> null;
         case "generic" -> dataSource("com.example.jdbc.Driver", "jdbc:example://localhost/db", "generic", false);
         case "derby" -> dataSource("org.apache.derby.jdbc.EmbeddedDriver", "jdbc:derby:memory:x", "derby", false);
         case "oracle" -> dataSource("oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:x", "oracle",
                                     false);
         case "h2" -> dataSource("org.h2.Driver", "jdbc:h2:mem:x", "h2", false);
         case "h2-ansi" -> dataSource("org.h2.Driver", "jdbc:h2:mem:x", "h2", true);
         case "sqlserver" -> dataSource("com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver://localhost",
                                        "sql server", false);
         case "mysql" -> dataSource("com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost/db", "mysql", false);
         case "postgresql" -> dataSource("org.postgresql.Driver", "jdbc:postgresql://localhost/db", "postgresql",
                                         false);
         default -> throw new IllegalArgumentException(key);
      };
   }

   private static JDBCDataSource dataSource(String driver, String url, String product, boolean ansiJoin) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77642" + product + ansiJoin);
      ds.setDriver(driver);
      ds.setURL(url);
      ds.setRuntimeProductName(product);
      // otherwise the mysql and oracle helpers ask the repository for it
      ds.setProductVersion("10.0");
      ds.setAnsiJoin(ansiJoin);
      return ds;
   }

   private static final Map<String, String> HELPERS = Map.of(
      "default", "SQLHelper", "generic", "SQLHelper", "derby", "DerbyHelper", "oracle", "OracleSQLHelper",
      "h2", "H2Helper", "h2-ansi", "H2Helper", "sqlserver", "SQLServerHelper", "mysql", "MySQLHelper",
      "postgresql", "PostgreSQLHelper");

   private static UniformSQL parse(String text, String key) throws Exception {
      UniformSQL sql = new UniformSQL();
      JDBCDataSource ds = dataSource(key);

      if(ds != null) {
         sql.setDataSource(ds);
      }

      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      assertEquals(HELPERS.get(key), SQLHelper.getSQLHelper(sql).getClass().getSimpleName(), key);
      return sql;
   }

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   // the rows as sorted cells, the regenerated select list may be in another order. In the
   // order of the result if the query has an order by
   private static List<String> rows(Statement stmt, String query) {
      List<String> rows = new ArrayList<>();

      try(ResultSet rs = stmt.executeQuery(query)) {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            List<String> row = new ArrayList<>();

            for(int i = 1; i <= count; i++) {
               row.add(String.valueOf(rs.getObject(i)));
            }

            Collections.sort(row);
            rows.add(String.join("|", row));
         }
      }
      catch(SQLException ex) {
         return List.of("ERR " + ex.getMessage());
      }

      if(!query.toLowerCase(Locale.ROOT).contains(" order by ")) {
         Collections.sort(rows);
      }

      return rows;
   }

   private static final String DERBY_URL = "jdbc:derby:memory:bug77642";

   // a JDBCDataSource creates its credential when it is constructed, and the derby helper
   // reads the product version through the repository (expression group by from 10.3)
   @Configuration
   static class Beans {
      @Bean
      Config config() {
         Config config = mock(Config.class);
         when(config.getJDBCType(any())).thenReturn("H2");
         return config;
      }

      @Bean
      XRepository xRepository() throws Exception {
         XRepository repository = mock(XRepository.class);
         when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
            XNode node = new XNode("properties");
            node.setAttribute("DBProductVersion", "10.17");
            return node;
         });
         return repository;
      }

      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean())).thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }
   }
}
