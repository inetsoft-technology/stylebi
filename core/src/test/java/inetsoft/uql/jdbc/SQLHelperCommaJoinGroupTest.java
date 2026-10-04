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
import inetsoft.uql.XQuery;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77675, SQLHelper wrote a join group with a RIGHT or FULL join after a comma, e.g.
 * a INNER JOIN b ON .. , c RIGHT OUTER JOIN d ON ... SQLite and HSQLDB give a comma the
 * precedence of a JOIN and read it as (a INNER JOIN b ON .., c) RIGHT OUTER JOIN d ON ..,
 * which returns different rows than a INNER JOIN b ON .., (c RIGHT OUTER JOIN d ON ..), the
 * way the other databases read it.
 * <ul>
 * <li>SQLHelper writes the first such group before the other groups and puts any other one,
 * or every one of a * select list, in parentheses.</li>
 * <li>A parsed query whose regenerated sql needs that is refused, so its sql runs as written,
 * and so is a from item after a comma with a RIGHT or FULL join outside of parentheses, which
 * the databases read differently.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  SQLHelperCommaJoinGroupTest.DataSourceConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperCommaJoinGroupTest {
   private static final String DERBY = "jdbc:derby:memory:bug77675";
   private static final String HSQLDB = "jdbc:hsqldb:mem:bug77675";
   // not on the core test classpath; checked when they are added to it
   private static final String H2 = "jdbc:h2:mem:bug77675;DB_CLOSE_DELAY=-1";
   private static final String SQLITE = "jdbc:sqlite::memory:";
   private static final String[] TABLES = { "a", "b", "c", "d", "e", "x" };
   private static final String COLS4 = "select a.id, b.id, c.id, d.id ";
   private static final String COLS5 = "select a.id, b.id, c.id, d.id, e.id ";
   private static final String COLS6 = "select a.id, b.id, c.id, d.id, e.id, x.id ";
   private static final String COLSX = "select c.id, d.id, x.id ";
   private static final String[] HELPERS = {
      "default", "default-ansi", "h2", "h2-ansi", "mysql", "postgresql", "oracle", "oracle-ansi",
      "sql server", "mongo"
   };
   private static final Map<String, Connection> connections = new LinkedHashMap<>();

   /**
    * Queries whose regenerated sql moved or parenthesized a RIGHT or FULL join group, or whose
    * from items after a comma have a RIGHT or FULL join, at any query level.
    */
   static Stream<String> refusedQueries() {
      return Stream.of(
         // S01 family: a join group without a join condition is a comma separated group
         COLS4 + "from a join b on a.id = b.id join (c right join d on c.id = d.id)",
         "select * from a join b on a.id = b.id join (c right join d on c.id = d.id)",
         COLS4 + "from (a join b on a.id = b.id) join (c right join d on c.id = d.id)",
         COLS4 + "from a left join b on a.id = b.id join (c right join d on c.id = d.id)",
         COLS4 + "from a join b on a.id = b.id join (c full join d on c.id = d.id)",
         COLS5 + "from a join b on a.id = b.id join (c join e on c.id = e.id right join d " +
            "on c.id = d.id)",
         COLS4 + "from c right join d on c.id = d.id join (a right join b on a.id = b.id)",
         // a LEFT join of a nested group is written as a RIGHT join
         COLS5 + "from a join b on a.id = b.id, d left join (c join e on c.id = e.id) " +
            "on d.id = c.id",
         COLS5 + "from a join b on a.id = b.id join (d left join (c join e on c.id = e.id) " +
            "on d.id = c.id)",
         // two FULL groups, without a comma
         COLS4 + "from a full join b on a.id = b.id join (c full join d on c.id = d.id)",
         COLS4 + "from a full join b on a.id = b.id join (c full join d on c.id = d.id) " +
            "on 1 = 1",
         "select x.id from x where exists (select 1 from a full join b on a.id = b.id " +
            "join (c full join d on c.id = d.id))",
         // two LEFT joins of nested groups are two RIGHT join groups
         COLS6 + "from d left join (c join e on c.id = e.id) on d.id = c.id, " +
            "x left join (a join b on a.id = b.id) on x.id = a.id",
         // a from item after a comma with a RIGHT or FULL join
         COLSX + "from x, c right join d on c.id = d.id",
         COLSX + "from x, c full join d on c.id = d.id",
         COLS4 + "from a cross join b, c right join d on c.id = d.id",
         COLS4 + "from a join b on a.id = b.id, c right join d on c.id = d.id",
         COLS4 + "from a join b on a.id = b.id, c full join d on c.id = d.id",
         COLS4 + "from c right join d on c.id = d.id, a right join b on a.id = b.id",
         COLS4 + "from a, b, c right join d on c.id = d.id where a.id = b.id",
         COLS5 + "from a join b on a.id = b.id, c join e on c.id = e.id right join d " +
            "on c.id = d.id",
         COLSX + "from x, (select c.id from c) c right join d on c.id = d.id",
         // in a subquery and a derived table
         COLS4 + "from a join b on a.id = b.id join (c right join d on c.id = d.id) " +
            "where a.id = b.id",
         "select x.id from x where exists (select 1 from a join b on a.id = b.id " +
            "join (c right join d on c.id = d.id) where a.id = x.id)",
         "select x.id from x where exists (select 1 from x, c right join d on c.id = d.id)",
         "select t.aid from x, (select a.id aid from a join b on a.id = b.id " +
            "join (c right join d on c.id = d.id)) t"
      );
   }

   @ParameterizedTest
   @MethodSource("refusedQueries")
   void refusedOnEveryHelper(String text) {
      for(String helper : HELPERS) {
         assertRefused(text, dataSource(helper), helper);
      }
   }

   @Test
   void refusedWithoutDataSource() {
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(COLS4 + "from a join b on a.id = b.id, c full join d on c.id = d.id");
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());

      // the comma group check uses the base helper without a data source
      sql = new UniformSQL();
      new SQLProcessor(sql).parse(COLS4 + "from a, b, c, d where a.id = b.id and c.id =* d.id");
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
   }

   /**
    * A where clause outer join is generated as an ANSI join, except by Oracle without ansi
    * join, which writes (+).
    */
   @Test
   void whereClauseOuterJoinGroupAfterInnerGroupIsRefused() {
      String text = COLS4 + "from a, b, c, d where a.id = b.id and c.id =* d.id";

      for(String helper : HELPERS) {
         if(!"oracle".equals(helper)) {
            assertRefused(text, dataSource(helper), helper);
         }
      }

      text = COLS4 + "from a, b, c, d where a.id = b.id and c.id(+) = d.id";
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource("oracle"));
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertRefused(text, dataSource("oracle-ansi"), "oracle-ansi");
   }

   @Test
   void refusedUnderTurkishLocale() {
      Locale locale = Locale.getDefault();

      try {
         Locale.setDefault(new Locale("tr", "TR"));
         assertRefused("SELECT C.ID, D.ID, X.ID FROM X, C RIGHT JOIN D ON C.ID = D.ID",
                       dataSource("default"), "tr");
         assertRefused("select c.id, d.id, x.id from x, c full outer join d on c.id = d.id",
                       dataSource("h2"), "tr");
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   // a refused query is valid in an expression, e.g. a scalar subquery (#77493)
   @Test
   void refusedQueryIsValidInExpression() {
      assertTrue(XUtil.isSQLExpressionValid(
         "(select count(*) from x, c right join d on c.id = d.id)"));
      assertTrue(XUtil.isSQLExpressionValid(
         "(select count(*) from a join b on a.id = b.id join (c right join d on c.id = d.id))"));
   }

   /**
    * Queries that SQLHelper regenerates with their join groups in place, and that SQLite reads
    * the same way as the other databases.
    */
   static Stream<String> acceptedQueries() {
      return Stream.of(
         // the RIGHT or FULL group is the first from item
         COLS4 + "from c right join d on c.id = d.id, a join b on a.id = b.id",
         COLS4 + "from c full join d on c.id = d.id, a left join b on a.id = b.id",
         COLS4 + "from c right join d on c.id = d.id join (a join b on a.id = b.id)",
         // LEFT join groups
         COLS4 + "from a join b on a.id = b.id, c left join d on c.id = d.id",
         COLS4 + "from a left join b on a.id = b.id, c left join d on c.id = d.id",
         // a LEFT join of a nested group, written as a RIGHT join group, is the only group
         "select c.id, d.id, e.id, x.id from x, d left join (c join e on c.id = e.id) " +
            "on d.id = c.id",
         // the RIGHT join is in parentheses, or nested in the right operand of a join
         COLS5 + "from a join b on a.id = b.id join (c right join d on c.id = d.id " +
            "join e on d.id = e.id)",
         "select a.id, c.id, d.id, x.id from x, a join (c right join d on c.id = d.id) " +
            "on a.id = c.id",
         "select c.id, d.id, e.id, x.id from x, (c right join d on c.id = d.id) " +
            "join e on d.id = e.id"
      );
   }

   @ParameterizedTest
   @MethodSource("acceptedQueries")
   void acceptedQueryReturnsSameRows(String text) throws Exception {
      for(String helper : new String[] { "default", "default-ansi", "h2-ansi", "mysql",
                                         "sql server", "oracle-ansi" })
      {
         UniformSQL sql = parse(text, dataSource(helper));
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), helper + ": " + text);
         assertFalse(sql.isLossy(), helper + ": " + text);
         String generated = regenerate(sql);
         assertRoundTrip(generated, dataSource(helper), helper);

         if(!"sql server".equals(helper) && !"oracle-ansi".equals(helper)) {
            assertSameRows(text, generated);
         }
      }
   }

   /**
    * Queries parsed and saved before Bug #77675 keep their parse result (#77477). Their
    * regenerated sql must return the rows of the original sql.
    */
   static Stream<String> savedQueries() {
      return Stream.of(
         COLS4 + "from a join b on a.id = b.id join (c right join d on c.id = d.id)",
         COLS4 + "from a left join b on a.id = b.id join (c right join d on c.id = d.id)",
         COLS4 + "from a join b on a.id = b.id join (c full join d on c.id = d.id)",
         COLS5 + "from a join b on a.id = b.id, d left join (c join e on c.id = e.id) " +
            "on d.id = c.id",
         COLS4 + "from a full join b on a.id = b.id join (c full join d on c.id = d.id)",
         COLS4 + "from c right join d on c.id = d.id join (a right join b on a.id = b.id)",
         COLS6 + "from d left join (c join e on c.id = e.id) on d.id = c.id, " +
            "x left join (a join b on a.id = b.id) on x.id = a.id",
         "select t.aid, t.did, x.id from x, (select a.id aid, d.id did from a join b " +
            "on a.id = b.id join (c right join d on c.id = d.id)) t"
      );
   }

   @ParameterizedTest
   @MethodSource("savedQueries")
   void savedQueryReturnsSameRows(String text) throws Exception {
      for(String helper : new String[] { "default", "default-ansi", "h2-ansi" }) {
         String generated = regenerate(parseSaved(text, dataSource(helper)));
         assertSameRows(text, generated);

         // a query saved before Bug #77475, without the join clauses, takes the join matrix
         generated = regenerate(withoutJoinClauses(parseSaved(text, dataSource(helper))));
         assertSameRows(text, generated);
      }
   }

   @Test
   void savedQueryWritesRightGroupFirst() throws Exception {
      String text = COLS4 + "from a join b on a.id = b.id join (c right join d on c.id = d.id)";
      String generated = regenerate(parseSaved(text, dataSource("default")));
      assertTrue(generated.contains(
         "from c RIGHT OUTER JOIN d ON c.id = d.id , a INNER JOIN b ON a.id = b.id"), generated);

      // the second FULL join group is in parentheses
      text = COLS4 + "from a full join b on a.id = b.id join (c full join d on c.id = d.id)";
      generated = regenerate(parseSaved(text, dataSource("default")));
      assertTrue(generated.contains(
         "from a FULL OUTER JOIN b ON a.id = b.id , (c FULL OUTER JOIN d ON c.id = d.id )"),
         generated);

      // MongoHelper can't write parentheses, every join of a group is outside of them
      text = COLS5 + "from a join b on a.id = b.id join (c right join d on c.id = d.id " +
         "join e on d.id = e.id)";
      generated = regenerate(parseSaved(text, dataSource("mongo")));
      assertTrue(generated.contains("from c RIGHT OUTER JOIN d ON c.id = d.id INNER JOIN e " +
                                       "ON d.id = e.id , a INNER JOIN b ON a.id = b.id"),
                 generated);
      // the other helpers write the RIGHT join in parentheses, so the group keeps its place
      generated = regenerate(parseSaved(text, dataSource("default")));
      assertTrue(generated.contains("from a INNER JOIN b ON a.id = b.id , (c RIGHT OUTER " +
                                       "JOIN d ON c.id = d.id ) INNER JOIN e"), generated);
   }

   /**
    * A * select list returns the columns in from order, so no group is moved: every RIGHT or
    * FULL join group after the first group is put in parentheses instead.
    */
   @Test
   void starSelectKeepsColumnOrder() throws Exception {
      String text = "select * from a join b on a.id = b.id join (c right join d on c.id = d.id)";

      for(String helper : new String[] { "default", "h2-ansi", "postgresql" }) {
         String generated = regenerate(parseSaved(text, dataSource(helper)));
         assertTrue(unquote(generated).contains(
            "from a INNER JOIN b ON a.id = b.id , (c RIGHT OUTER JOIN d ON c.id = d.id )"),
                    helper + ": " + generated);
      }

      String generated = regenerate(parseSaved(text, dataSource("default")));
      assertSameStarRows(text, generated);
      assertColumnTables(generated, "a", "b", "c", "d");

      generated = regenerate(parseSaved("select a.*, d.* from a join b on a.id = b.id " +
                                           "join (c right join d on c.id = d.id)",
                                        dataSource("default")));
      assertTrue(generated.contains("(c RIGHT OUTER JOIN d"), generated);

      // a query editor model with a * column
      UniformSQL sql = editorModel("a-b:=,c-d:=*", "*");
      sql.setDataSource(dataSource("default"));
      generated = regenerate(sql);
      assertSameStarRows("select * from (a inner join b on a.id = b.id), " +
                            "(c right join d on c.id = d.id)", generated);
      assertColumnTables(generated, "a", "b", "c", "d");
   }

   /**
    * Join models as the query editor builds them, two independent links "a-b:op,c-d:op" with
    * op =, *=, =* or *=*, and three groups.
    */
   static Stream<String> editorModels() {
      List<String> models = new ArrayList<>();
      String[] ops = { "=", "*=", "=*", "*=*" };

      for(String op1 : ops) {
         for(String op2 : ops) {
            models.add("a-b:" + op1 + ",c-d:" + op2);
         }
      }

      models.add("a-b:=,c-d:=,e-x:=*");
      models.add("a-b:=*,c-d:=,e-x:*=*");
      models.add("a-b:*=*,c-d:*=*,e-x:=*");
      models.add("a-b:=,b-c:=,d-e:=*");
      return models.stream();
   }

   @ParameterizedTest
   @MethodSource("editorModels")
   void editorModelReturnsModelRows(String model) throws Exception {
      String expected = modelSql(model);

      for(String helper : new String[] { "default", "h2-ansi", "postgresql", "mongo" }) {
         UniformSQL sql = editorModel(model, null);
         sql.setDataSource(dataSource(helper));
         String generated = regenerate(sql);
         int rightOrFull = countRightOrFullGroups(model);

         // the first group of the from clause has a RIGHT or FULL join, if any group has one
         if(rightOrFull > 0) {
            String first = unquote(generated).replaceFirst("^.* from ", "").split(" , ")[0];
            assertTrue(first.contains(" RIGHT OUTER JOIN ") || first.contains(" FULL OUTER JOIN "),
                       helper + ": " + generated);
         }

         if("postgresql".equals(helper)) {
            continue;
         }

         // MongoHelper's sql runs on the unity driver, whose comma precedence is unknown. It
         // is checked on the databases that bind JOIN tighter than a comma only
         assertSameRows(expected, generated, "mongo".equals(helper));

         // the sql of a model with one RIGHT or FULL join group parses again. A second group
         // is in parentheses, which the parser refuses (Bug #77495), so the sql runs as written
         if(!"mongo".equals(helper)) {
            if(rightOrFull <= 1) {
               assertRoundTrip(generated, dataSource(helper), helper);
            }
            else {
               assertRefused(generated, dataSource(helper), helper);
            }
         }
      }
   }

   @Test
   void editorModelWritesRightGroupFirst() {
      UniformSQL sql = editorModel("a-b:=*,c-d:=", null);
      sql.setDataSource(dataSource("default"));
      assertTrue(regenerate(sql).contains(
         "from a RIGHT OUTER JOIN b ON a.id = b.id , c INNER JOIN d ON c.id = d.id"),
                 regenerate(sql));

      sql = editorModel("a-b:=*,c-d:=*", null);
      sql.setDataSource(dataSource("default"));
      assertTrue(regenerate(sql).contains(
         "from a RIGHT OUTER JOIN b ON a.id = b.id , (c RIGHT OUTER JOIN d ON c.id = d.id )"),
                 regenerate(sql));

      // oracle without ansi join writes (+), and has no join groups
      sql = editorModel("a-b:=*,c-d:=", null);
      sql.setDataSource(dataSource("oracle"));
      assertFalse(regenerate(sql).contains("JOIN"), regenerate(sql));
   }

   // the number of independent links with a RIGHT or FULL join
   private static int countRightOrFullGroups(String model) {
      if(model.startsWith("a-b:=,b-c:=,")) {
         return 1;
      }

      int count = 0;

      for(String link : model.split(",")) {
         if(link.endsWith(":=*") || link.endsWith(":*=*")) {
            count++;
         }
      }

      return count;
   }

   // the model as a sql with each link group in parentheses, which every database reads the
   // same way
   private static String modelSql(String model) {
      StringBuilder from = new StringBuilder();
      Set<String> tables = new TreeSet<>();

      if(model.startsWith("a-b:=,b-c:=,")) {
         tables.addAll(List.of("a", "b", "c", "d", "e"));
         from.append("(a inner join b on a.id = b.id inner join c on b.id = c.id), " +
                        "(d right join e on d.id = e.id)");
      }
      else {
         for(String link : model.split(",")) {
            String[] parts = link.split("[-:]");
            String join = switch(parts[2]) {
               case "=" -> "inner";
               case "*=" -> "left";
               case "=*" -> "right";
               default -> "full";
            };

            from.append(from.length() == 0 ? "" : ", ").append("(").append(parts[0])
               .append(" ").append(join).append(" join ").append(parts[1]).append(" on ")
               .append(parts[0]).append(".id = ").append(parts[1]).append(".id)");
            tables.add(parts[0]);
            tables.add(parts[1]);
         }
      }

      StringBuilder select = new StringBuilder();

      for(String table : tables) {
         select.append(select.length() == 0 ? "select " : ", ").append(table).append(".id");
      }

      return select + " from " + from;
   }

   // a query editor model of the tables of the links, selecting their sorted id columns
   private static UniformSQL editorModel(String model, String column) {
      UniformSQL sql = new UniformSQL();
      Set<String> tables = new TreeSet<>();

      for(String link : model.split(",")) {
         String[] parts = link.split("[-:]");
         tables.add(parts[0]);
         tables.add(parts[1]);
      }

      for(String table : tables) {
         sql.addTable(table);

         if(column == null) {
            sql.getSelection().addColumn(table + ".id");
         }
      }

      if(column != null) {
         sql.getSelection().addColumn(column);
      }

      for(String link : model.split(",")) {
         String[] parts = link.split("[-:]");
         sql.addJoin(new XJoin(new XExpression(parts[0] + ".id", XExpression.FIELD),
                               new XExpression(parts[1] + ".id", XExpression.FIELD), parts[2]));
      }

      return sql;
   }

   @BeforeAll
   static void createTables() throws SQLException {
      for(String url : new String[] { DERBY + ";create=true", HSQLDB, H2, SQLITE }) {
         Connection conn;

         try {
            conn = DriverManager.getConnection(url);
         }
         catch(SQLException ex) {
            // the driver is not on the classpath
            if(url == H2 || url == SQLITE) {
               continue;
            }

            throw ex;
         }

         connections.put(url, conn);

         try(Statement stmt = conn.createStatement()) {
            for(String table : TABLES) {
               stmt.executeUpdate("create table " + table + " (id int, k int, tn varchar(1))");
            }
         }
      }
   }

   @AfterAll
   static void dropDatabases() throws SQLException {
      for(Connection conn : connections.values()) {
         conn.close();
      }

      connections.clear();

      try {
         DriverManager.getConnection(DERBY + ";drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }

      try(Connection conn = DriverManager.getConnection(HSQLDB);
          Statement stmt = conn.createStatement())
      {
         stmt.execute("shutdown");
      }
   }

   private static void assertSameRows(String expected, String generated) throws SQLException {
      assertSameRows(expected, generated, false);
   }

   /**
    * Runs both queries on random data with nulls, and requires the same rows from both. Derby
    * and H2 have no FULL join.
    * @param standard true to run them only on the databases that bind a JOIN tighter than a
    * comma (Derby, H2).
    */
   private static void assertSameRows(String expected, String generated, boolean standard)
      throws SQLException
   {
      boolean full = expected.toLowerCase().contains(" full ");

      for(Map.Entry<String, Connection> entry : connections.entrySet()) {
         String url = entry.getKey();

         if(full && (url == H2 || url.startsWith("jdbc:derby")) ||
            standard && (url == HSQLDB || url == SQLITE))
         {
            continue;
         }

         Random random = new Random(77675);

         for(int i = 0; i < 200; i++) {
            fillTables(entry.getValue(), random);
            assertEquals(rows(entry.getValue(), reference(url, expected), false),
                         rows(entry.getValue(), generated, false),
                         url + " dataset " + i + "\nexpected: " + expected +
                         "\ngenerated: " + generated);
         }
      }
   }

   /**
    * The sql to run for the original sql on a database. Derby and HSQLDB reject a JOIN without
    * a join condition, which H2 and SQLite read as a cross join, so they run the same query
    * with a CROSS JOIN. Each such join of the queries follows the ON of a join.
    */
   private static String reference(String url, String sql) {
      if(url == HSQLDB || url.startsWith("jdbc:derby")) {
         return sql.replaceAll("(\\.id) join \\(", "$1 cross join (");
      }

      return sql;
   }

   // select * rows, compared by table, so the column order is checked separately
   private static void assertSameStarRows(String expected, String generated)
      throws SQLException
   {
      for(Map.Entry<String, Connection> entry : connections.entrySet()) {
         Random random = new Random(77675);

         for(int i = 0; i < 200; i++) {
            fillTables(entry.getValue(), random);
            assertEquals(rows(entry.getValue(), reference(entry.getKey(), expected), true),
                         rows(entry.getValue(), generated, true),
                         entry.getKey() + " dataset " + i + "\nexpected: " + expected +
                         "\ngenerated: " + generated);
         }
      }
   }

   // the tables of the select * columns, in column order, on every database
   private static void assertColumnTables(String sql, String... tables) throws SQLException {
      for(Map.Entry<String, Connection> entry : connections.entrySet()) {
         Connection conn = entry.getValue();
         // one row per table, which every join keeps. HSQLDB reports the columns of a
         // parenthesized join as of a subquery, so the table is found from its name column
         fillTables(conn, null);

         try(Statement stmt = conn.createStatement(); ResultSet result = stmt.executeQuery(sql)) {
            assertTrue(result.next(), sql);
            List<String> names = new ArrayList<>();

            for(int i = 3; i <= result.getMetaData().getColumnCount(); i += 3) {
               names.add(result.getString(i));
               // sqlite labels a repeated column tn:1
               assertTrue(result.getMetaData().getColumnLabel(i).toLowerCase().startsWith("tn"),
                          sql);
            }

            assertEquals(List.of(tables), names, entry.getKey() + ": " + sql);
         }
      }
   }

   // random rows with nulls, or one row (1, 1) per table without a random
   private static void fillTables(Connection conn, Random random) throws SQLException {
      for(String table : TABLES) {
         try(Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("delete from " + table);
         }

         try(PreparedStatement insert =
                conn.prepareStatement("insert into " + table + " values (?, ?, ?)"))
         {
            int count = random == null ? 1 : random.nextInt(4);

            for(int i = 0; i < count; i++) {
               for(int column = 1; column <= 2; column++) {
                  int value = random == null ? 1 : random.nextInt(4);

                  if(value == 0) {
                     insert.setNull(column, Types.INTEGER);
                  }
                  else {
                     insert.setInt(column, value);
                  }
               }

               insert.setString(3, table);
               insert.addBatch();
            }

            // hsqldb rejects an empty batch
            if(count > 0) {
               insert.executeBatch();
            }
         }
      }
   }

   /**
    * The result rows as a sorted multiset.
    * @param byTable true for a select * of tables with the columns id, k and name, whose rows
    * are compared by table, so the column order of the two queries may differ.
    */
   private static List<String> rows(Connection conn, String query, boolean byTable)
      throws SQLException
   {
      List<String> rows = new ArrayList<>();

      try(Statement stmt = conn.createStatement(); ResultSet result = executeQuery(stmt, query)) {
         int columns = result.getMetaData().getColumnCount();

         while(result.next()) {
            List<String> values = new ArrayList<>();

            for(int i = 1; i <= columns; i += byTable ? 3 : 1) {
               if(byTable) {
                  Object name = result.getObject(i + 2);
                  values.add((name == null ? "~" : name) + ":" + result.getObject(i) + "|" +
                                result.getObject(i + 1));
               }
               else {
                  values.add(String.valueOf(result.getObject(i)));
               }
            }

            if(byTable) {
               Collections.sort(values);
            }

            rows.add(String.join(",", values));
         }
      }

      Collections.sort(rows);
      return rows;
   }

   private static ResultSet executeQuery(Statement stmt, String query) throws SQLException {
      try {
         return stmt.executeQuery(query);
      }
      catch(SQLException ex) {
         throw new SQLException(ex.getMessage() + ": " + query, ex);
      }
   }

   private static void assertRefused(String text, JDBCDataSource ds, String type) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type + ": " + text);
      assertEquals(text, sql.getSQLString(), type);

      UniformSQL fresh = new UniformSQL();
      fresh.setDataSource(ds);
      fresh.setSQLString(text, false);
      assertTrue(fresh.isLossy(), type + ": " + text);
      assertFalse(XUtil.isQueryMergeable(query(text, ds)), type + ": " + text);
   }

   // the regenerated sql parses and regenerates to a fixed point. A reparsed outer join can
   // swap its ON operands once, e.g. after a nested group (c join e) RIGHT OUTER JOIN d
   private static void assertRoundTrip(String generated, JDBCDataSource ds, String type)
      throws Exception
   {
      UniformSQL reparsed = parse(generated, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), type + ": " + generated);
      String regenerated = regenerate(reparsed);
      assertEquals(regenerated, regenerate(parse(regenerated, ds)), type + ": round trip");
   }

   private static String regenerate(UniformSQL sql) {
      UniformSQL copy = (UniformSQL) sql.clone();
      copy.clearSQLString();
      return normalize(copy.getSQLString());
   }

   private static XQuery query(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse(text);
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      return query;
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }

   /**
    * Parse a query the way it was parsed and saved before Bug #77675, without the checks
    * that UniformSQL runs after the grammar.
    */
   private static UniformSQL parseSaved(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      SQLParser parser = new SQLParser(new SQLLexer(new StringReader(text)));
      parser.direct_select_stmt_n_rows(sql);
      sql.setParseResult(UniformSQL.PARSE_SUCCESS);
      return sql;
   }

   // the query as saved before Bug #77475 recorded the join clauses
   private static UniformSQL withoutJoinClauses(UniformSQL sql) throws Exception {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      sql.writeXML(writer);
      writer.flush();
      String xml = buffer.toString().replaceAll(" joinClause=\"-?\\d+\"", "");
      Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      UniformSQL saved = new UniformSQL();
      saved.parseXML(root);
      saved.setDataSource(sql.getDataSource());
      return saved;
   }

   private static JDBCDataSource dataSource(String type) {
      boolean ansi = type.endsWith("-ansi");
      String product = type.replace("-ansi", "");

      if("default".equals(product)) {
         JDBCDataSource ds = GenericJDBCDataSource.create();
         ds.setAnsiJoin(ansi);
         assertEquals(SQLHelper.class, SQLHelper.getSQLHelper(ds).getClass());
         return ds;
      }

      String[] driver = switch(product) {
         case "h2" -> new String[] { "org.h2.Driver", "jdbc:h2:mem:db" };
         case "mysql" -> new String[] { "com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost:3306/db" };
         case "postgresql" -> new String[] { "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/db" };
         case "sql server" -> new String[] { "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                                             "jdbc:sqlserver://localhost;databaseName=db" };
         case "oracle" -> new String[] { "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:db" };
         case "mongo" -> new String[] { "mongodb.jdbc.MongoDriver", "jdbc:mongo://localhost:27017/test" };
         default -> throw new IllegalArgumentException(type);
      };

      JDBCDataSource ds = new JDBCDataSource();
      ds.setAnsiJoin(ansi);
      ds.setName("bug77675");
      ds.setDriver(driver[0]);
      ds.setURL(driver[1]);
      // a version so the helper lookup doesn't query the database
      ds.setProductVersion("19.0");
      assertEquals(product, SQLHelper.getSQLHelper(ds).getSQLHelperType(), type);
      return ds;
   }

   // the table names a quoting helper (PostgreSQL) writes quoted
   private static String unquote(String sql) {
      return normalize(sql).replace("\"", "");
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   @Configuration
   static class DataSourceConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean()))
            .thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }

      @Bean
      public Config config() {
         return new Config(mock(Plugins.class));
      }
   }
}
