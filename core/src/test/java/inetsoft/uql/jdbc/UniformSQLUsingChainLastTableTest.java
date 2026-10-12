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

import antlr.RecognitionException;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78381, a JOIN ... USING chain whose later USING column is in the last table of the
 * left operand (categories join products using (category_id) join order_details using
 * (product_id)) failed the parse since Bug #77490, so VPM rejected it for a restricted
 * user. The column of a multi-table left operand is now taken from the last table of the
 * left operand when it's not known from an earlier USING join, and the quotes of a USING
 * column are kept, so the sql regenerated from the structure (as VPM does) joins
 * "CATEGORIES"."CATEGORY_ID" and not "CATEGORIES".CATEGORY_ID, which PostgreSQL folds to
 * lower case.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLUsingChainLastTableTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLUsingChainLastTableTest {
   // a data source with a real driver and URL needs the credential service, and the JDBC
   // driver types of Config to pick its SQL helper
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   // the helpers whose regenerated sql is run, without and with ANSI join generation
   private static final String[] DATA_SOURCES =
      { "h2", "h2-ansi", "postgresql", "postgresql-ansi" };
   // the helpers whose regenerated sql is run on Derby, HSQLDB and SQLite. PostgreSQLHelper
   // quotes a lower case alias ("t"), which these fold to upper case, so its sql is run on
   // a PostgreSQL stand-in
   private static final String[] UPPER_FOLDING_SOURCES = { "h2", "h2-ansi" };

   private static final String COLS = "select \"CATEGORIES\".\"CATEGORY_NAME\", " +
      "\"PRODUCTS\".\"PRODUCT_NAME\", \"ORDER_DETAILS\".\"QUANTITY\" ";
   private static final String CHAIN = "from \"CATEGORIES\" join \"PRODUCTS\" " +
      "using (\"CATEGORY_ID\") join \"ORDER_DETAILS\" using (\"PRODUCT_ID\")";
   // the reporter's query, its unqualified "PRODUCT_ID" is the merged USING column
   private static final String REPORTED = "select \"CATEGORY_NAME\", \"PRODUCT_ID\" " +
      "from \"CATEGORIES\" join \"PRODUCTS\" using (\"CATEGORY_ID\") " +
      "join \"ORDER_DETAILS\" using (\"PRODUCT_ID\")";

   /**
    * The chains admitted by the last table rule, written with quoted upper case names. The
    * select list has no USING column, which is merged by the database but not by the
    * regenerated ON join.
    */
   static List<String> chains() {
      return List.of(
         // the reported chain, and with the VPM condition of the report
         COLS + CHAIN,
         COLS + CHAIN + " where \"CATEGORIES\".\"CATEGORY_NAME\" = 'Hardware'",
         // inner and RIGHT, in both orders
         COLS + "from \"CATEGORIES\" join \"PRODUCTS\" using (\"CATEGORY_ID\") " +
            "right join \"ORDER_DETAILS\" using (\"PRODUCT_ID\")",
         COLS + "from \"CATEGORIES\" right join \"PRODUCTS\" using (\"CATEGORY_ID\") " +
            "join \"ORDER_DETAILS\" using (\"PRODUCT_ID\")",
         COLS + "from \"CATEGORIES\" right join \"PRODUCTS\" using (\"CATEGORY_ID\") " +
            "right join \"ORDER_DETAILS\" using (\"PRODUCT_ID\")",
         // LEFT as the current or the earlier join
         COLS + "from \"CATEGORIES\" join \"PRODUCTS\" using (\"CATEGORY_ID\") " +
            "left join \"ORDER_DETAILS\" using (\"PRODUCT_ID\")",
         COLS + "from \"CATEGORIES\" left join \"PRODUCTS\" using (\"CATEGORY_ID\") " +
            "left join \"ORDER_DETAILS\" using (\"PRODUCT_ID\")",
         COLS + "from \"CATEGORIES\" left join \"PRODUCTS\" using (\"CATEGORY_ID\") " +
            "join \"ORDER_DETAILS\" using (\"PRODUCT_ID\")",
         COLS + "from \"CATEGORIES\" left outer join \"PRODUCTS\" using (\"CATEGORY_ID\") " +
            "right outer join \"ORDER_DETAILS\" using (\"PRODUCT_ID\")",
         // FULL as the current or the earlier join (not on Derby and H2)
         COLS + "from \"CATEGORIES\" join \"PRODUCTS\" using (\"CATEGORY_ID\") " +
            "full join \"ORDER_DETAILS\" using (\"PRODUCT_ID\")",
         COLS + "from \"CATEGORIES\" full join \"PRODUCTS\" using (\"CATEGORY_ID\") " +
            "join \"ORDER_DETAILS\" using (\"PRODUCT_ID\")",
         // four tables, and a multi-column USING with a column of an earlier USING join
         COLS + ", \"ORDERS\".\"CUSTOMER\" " + CHAIN + " join \"ORDERS\" using (\"ORDER_ID\")",
         COLS + ", \"SHIPMENTS\".\"SHIPPED\" " + CHAIN +
            " join \"SHIPMENTS\" using (\"ORDER_ID\", \"PRODUCT_ID\")",
         COLS + ", \"SHIPMENTS\".\"SHIPPED\" " + CHAIN +
            " left join \"SHIPMENTS\" using (\"PRODUCT_ID\", \"ORDER_ID\")",
         // ON then USING with aliases, USING then ON then USING
         "select c.\"CATEGORY_NAME\", p.\"PRODUCT_NAME\", d.\"QUANTITY\" from \"CATEGORIES\" c " +
            "join \"PRODUCTS\" p on c.\"CATEGORY_ID\" = p.\"CATEGORY_ID\" " +
            "join \"ORDER_DETAILS\" d using (\"PRODUCT_ID\")",
         COLS + ", \"ORDERS\".\"CUSTOMER\" from \"CATEGORIES\" join \"PRODUCTS\" " +
            "using (\"CATEGORY_ID\") join \"ORDER_DETAILS\" " +
            "on \"PRODUCTS\".\"PRODUCT_ID\" = \"ORDER_DETAILS\".\"PRODUCT_ID\" " +
            "join \"ORDERS\" using (\"ORDER_ID\")",
         // a parenthesized left operand
         COLS + "from (\"CATEGORIES\" join \"PRODUCTS\" using (\"CATEGORY_ID\")) " +
            "join \"ORDER_DETAILS\" using (\"PRODUCT_ID\")",
         // a derived table, an EXISTS and an IN subquery
         "select t.\"CATEGORY_NAME\", t.\"QUANTITY\" from (" + COLS + CHAIN + ") t",
         "select \"ORDERS\".\"CUSTOMER\" from \"ORDERS\" where exists (select 1 " + CHAIN +
            " where \"ORDER_DETAILS\".\"ORDER_ID\" = \"ORDERS\".\"ORDER_ID\")",
         "select \"ORDERS\".\"CUSTOMER\" from \"ORDERS\" where \"ORDERS\".\"ORDER_ID\" in " +
            "(select \"ORDER_DETAILS\".\"ORDER_ID\" " + CHAIN +
            " where \"CATEGORIES\".\"CATEGORY_NAME\" = 'Hardware')",
         // a comma item before the join
         COLS + ", \"ORDERS\".\"CUSTOMER\" from \"ORDERS\", \"CATEGORIES\" join \"PRODUCTS\" " +
            "using (\"CATEGORY_ID\") join \"ORDER_DETAILS\" using (\"PRODUCT_ID\") " +
            "where \"ORDERS\".\"ORDER_ID\" = \"ORDER_DETAILS\".\"ORDER_ID\""
      );
   }

   // every chain quoted and unquoted
   static Stream<Arguments> chainsBySource() {
      return chains().stream()
         .flatMap(text -> Stream.of(text, unquote(text)))
         .flatMap(text -> Arrays.stream(UPPER_FOLDING_SOURCES)
            .map(type -> Arguments.of(text, type)));
   }

   // the quoted chains on the PostgreSQL helpers, for a lower case folding database
   static Stream<Arguments> quotedChainsOnPostgreSQL() {
      return chains().stream()
         .flatMap(text -> Stream.of("postgresql", "postgresql-ansi")
            .map(type -> Arguments.of(text, type)));
   }

   private static Connection derby;
   private static Connection hsqldb;
   private static Connection h2Postgres;
   private static Connection sqlite;

   @BeforeAll
   static void createDatabases() throws Exception {
      derby = DriverManager.getConnection("jdbc:derby:memory:bug78381;create=true");
      createTables(derby);
      hsqldb = DriverManager.getConnection("jdbc:hsqldb:mem:bug78381", "SA", "");
      createTables(hsqldb);

      // H2 and SQLite are not on the core test classpath, add them with
      // -Dmaven.test.additionalClasspath to run their rows
      if(hasDriver("org.h2.Driver")) {
         // a stand-in for PostgreSQL, which folds unquoted names to lower case
         h2Postgres = DriverManager.getConnection(
            "jdbc:h2:mem:bug78381;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
         createTables(h2Postgres);
      }

      if(hasDriver("org.sqlite.JDBC")) {
         sqlite = DriverManager.getConnection("jdbc:sqlite::memory:");
         createTables(sqlite);
      }
   }

   @AfterAll
   static void dropDatabases() throws Exception {
      for(Connection conn : new Connection[] { hsqldb, h2Postgres, sqlite }) {
         if(conn != null) {
            conn.close();
         }
      }

      derby.close();

      try {
         DriverManager.getConnection("jdbc:derby:memory:bug78381;drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   /**
    * The chain parses, stays lossy (the original sql runs for every consumer but VPM), and
    * the sql regenerated from the structure returns the original rows and parses back to
    * itself. FULL joins are compared on HSQLDB and SQLite, Derby has none.
    */
   @ParameterizedTest
   @MethodSource("chainsBySource")
   void regeneratedChainReturnsSameRows(String text, String type) throws Exception {
      JDBCDataSource ds = dataSource(type);
      UniformSQL sql = process(text, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertTrue(XUtil.isParsedSQL(sql));
      assertTrue(sql.isLossy());
      assertFalse(XUtil.isQueryMergeable(query(sql, ds)));

      String generated = regenerate(sql);
      assertFalse(generated.toLowerCase(Locale.ROOT).contains("using"), generated);
      assertRoundTrip(generated, ds);

      // Derby has no FULL join, HSQLDB doesn't run a parenthesized USING join
      int compared = assertSameRows(derby, text, generated) + assertSameRows(hsqldb, text, generated);
      assertTrue(compared > 0, "the original sql runs on neither Derby nor HSQLDB: " + text);

      if(sqlite != null) {
         assertEquals(1, assertSameRows(sqlite, text, generated), text);
      }
   }

   /**
    * The quoted chain regenerated by the PostgreSQL helper returns the original rows on a
    * database that folds unquoted names to lower case (H2 in PostgreSQL mode, a stand-in
    * for PostgreSQL). Without the quotes of the USING column, the join was
    * "CATEGORIES".CATEGORY_ID, which is not found. H2 can't run most of the original USING
    * chains in this mode (column not found), so the original rows are Derby's. H2 has no
    * FULL join, so the regenerated sql of a FULL chain is only parsed.
    */
   @ParameterizedTest
   @MethodSource("quotedChainsOnPostgreSQL")
   void quotedChainReturnsSameRowsOnLowerCaseFoldingDatabase(String text, String type)
      throws Exception
   {
      JDBCDataSource ds = dataSource(type);
      UniformSQL sql = process(text, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertTrue(sql.isLossy());

      String generated = regenerate(sql);
      assertFalse(generated.toLowerCase(Locale.ROOT).contains("using"), generated);
      assertRoundTrip(generated, ds);
      Assumptions.assumeTrue(h2Postgres != null, "H2 is not on the classpath");
      Assumptions.assumeFalse(text.contains(" full join "), "H2 has no FULL join");
      assertEquals(rows(derby, text), rows(h2Postgres, generated), generated);
   }

   /**
    * The joins of the reported chain are on the last table of the left operand, with the
    * USING column quoted as written. An unquoted column stays unquoted.
    */
   @ParameterizedTest
   @ValueSource(strings = { "postgresql", "postgresql-ansi", "h2", "h2-ansi" })
   void reportedChainJoinsQuotedColumnsOfLastTable(String type) throws Exception {
      JDBCDataSource ds = dataSource(type);
      UniformSQL sql = process(REPORTED, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      // PostgreSQLHelper keeps an unaliased table quoted, its alias is "CATEGORIES"
      assertEquals("CATEGORIES.CATEGORY_ID = PRODUCTS.CATEGORY_ID, " +
                   "PRODUCTS.PRODUCT_ID = ORDER_DETAILS.PRODUCT_ID",
                   joins(sql).replace("\"", ""));

      String generated = regenerate(sql);
      assertTrue(generated.contains("\"CATEGORIES\".\"CATEGORY_ID\" = " +
                                    "\"PRODUCTS\".\"CATEGORY_ID\""), generated);
      assertTrue(generated.contains("\"PRODUCTS\".\"PRODUCT_ID\" = " +
                                    "\"ORDER_DETAILS\".\"PRODUCT_ID\""), generated);
      assertRoundTrip(generated, ds);

      String plain = regenerate(process(unquote(REPORTED), ds));
      assertFalse(plain.contains(".\"CATEGORY_ID\""), plain);
      assertFalse(plain.contains(".\"PRODUCT_ID\""), plain);
   }

   /**
    * The reporter's select list names the merged USING column unqualified. Without table
    * metadata to qualify it (JDBCUtil.fixSelectionInfo), the regenerated sql is ambiguous,
    * a database error, never other rows.
    */
   @Test
   void unqualifiedMergedColumnFailsWithoutMetadata() throws Exception {
      String generated = regenerate(process(REPORTED, dataSource("postgresql")));
      assertDoesNotThrow(() -> rows(derby, REPORTED));
      SQLException ex = assertThrows(SQLException.class, () -> rows(derby, generated));
      assertTrue(ex.getMessage().contains("more than one table"), ex.getMessage());
   }

   /**
    * A condition added to the parsed structure, as VPM adds its row condition, is applied
    * to the regenerated chain: only the Hardware rows come back.
    */
   @ParameterizedTest
   @ValueSource(strings = { "postgresql", "postgresql-ansi", "h2", "h2-ansi" })
   void addedConditionFiltersChain(String type) throws Exception {
      UniformSQL sql = process(COLS + CHAIN, dataSource(type));
      UniformSQL filtered = process(COLS + CHAIN +
         " where \"CATEGORIES\".\"CATEGORY_NAME\" = 'Hardware'", dataSource(type));
      XFilterNode condition = filtered.getWhere();
      List<XFilterNode> conditions = new ArrayList<>();
      collectConditions(condition, conditions);
      XFilterNode hardware = conditions.stream()
         .filter(c -> !(c instanceof XJoin))
         .findFirst().orElseThrow();
      sql.combineWhereByAnd((XFilterNode) hardware.clone());

      String generated = regenerate(sql);
      String expected = rows(derby, COLS + CHAIN +
         " where \"CATEGORIES\".\"CATEGORY_NAME\" = 'Hardware'");
      assertTrue(expected.contains("Hardware") && !expected.contains("Software"), expected);
      assertEquals(expected, rows(derby, generated), generated);

      if(h2Postgres != null && type.startsWith("postgresql")) {
         assertEquals(expected, rows(h2Postgres, generated), generated);
      }
   }

   /**
    * A USING chain records the same structure as its ON twin, which main already parses
    * and regenerates, so it reaches no other regeneration path.
    */
   @ParameterizedTest
   @MethodSource("onTwins")
   void chainRegeneratesAsItsOnTwin(String using, String on, String type) throws Exception {
      JDBCDataSource ds = dataSource(type);
      UniformSQL sql = process(using, ds);
      UniformSQL twin = process(on, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), using);
      assertEquals(UniformSQL.PARSE_SUCCESS, twin.getParseResult(), on);
      assertEquals(joins(twin), joins(sql));
      assertEquals(tables(twin), tables(sql));
      assertEquals(regenerate(twin), regenerate(sql));
   }

   static Stream<Arguments> onTwins() {
      String on1 = "on \"CATEGORIES\".\"CATEGORY_ID\" = \"PRODUCTS\".\"CATEGORY_ID\"";
      String on2 = "on \"PRODUCTS\".\"PRODUCT_ID\" = \"ORDER_DETAILS\".\"PRODUCT_ID\"";
      String[][] twins = {
         { COLS + CHAIN,
           COLS + "from \"CATEGORIES\" join \"PRODUCTS\" " + on1 + " join \"ORDER_DETAILS\" " + on2 },
         { COLS + "from \"CATEGORIES\" join \"PRODUCTS\" using (\"CATEGORY_ID\") " +
              "right join \"ORDER_DETAILS\" using (\"PRODUCT_ID\")",
           COLS + "from \"CATEGORIES\" join \"PRODUCTS\" " + on1 +
              " right join \"ORDER_DETAILS\" " + on2 },
         { COLS + "from \"CATEGORIES\" join \"PRODUCTS\" using (\"CATEGORY_ID\") " +
              "left join \"ORDER_DETAILS\" using (\"PRODUCT_ID\")",
           COLS + "from \"CATEGORIES\" join \"PRODUCTS\" " + on1 +
              " left join \"ORDER_DETAILS\" " + on2 },
         { COLS + "from \"CATEGORIES\" join \"PRODUCTS\" using (\"CATEGORY_ID\") " +
              "full join \"ORDER_DETAILS\" using (\"PRODUCT_ID\")",
           COLS + "from \"CATEGORIES\" join \"PRODUCTS\" " + on1 +
              " full join \"ORDER_DETAILS\" " + on2 }
      };

      return Arrays.stream(twins).flatMap(t -> Arrays.stream(DATA_SOURCES)
         .map(type -> Arguments.of(t[0], t[1], type)));
   }

   /**
    * After a cross join chain or a parenthesized join, which #77490 refused for a USING
    * join, the chain is refused or regenerated as its ON twin (the last table's column),
    * which main parses and regenerates, so the refusals of Bug #77495 apply to it as they
    * do to the ON twin.
    */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select a.x from a cross join b join c using (y)|select a.x from a cross join b join c on b.y = c.y",
      "select a.x from a cross join b left join c using (y)|" +
         "select a.x from a cross join b left join c on b.y = c.y",
      "select a.x from a cross join b right join c using (y)|" +
         "select a.x from a cross join b right join c on b.y = c.y",
      "select a.x from ((a join b on a.id = b.id)) join c using (y)|" +
         "select a.x from ((a join b on a.id = b.id)) join c on b.y = c.y",
      "select a.x from ((a join b on a.id = b.id)) left join c using (y)|" +
         "select a.x from ((a join b on a.id = b.id)) left join c on b.y = c.y",
      "select a.x from x, a join b using (id) left join c using (y)|" +
         "select a.x from x, a join b on a.id = b.id left join c on b.y = c.y",
      "select a.x from a join b using (id) left join c using (y), x|" +
         "select a.x from a join b on a.id = b.id left join c on b.y = c.y, x"
   })
   void chainAfterCrossJoinOrParenthesesActsAsItsOnTwin(String using, String on) {
      for(String type : DATA_SOURCES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = process(using, ds);
         UniformSQL twin = process(on, ds);
         assertEquals(twin.getParseResult(), sql.getParseResult(), type + ": " + using);

         // PostgreSQLHelper writes an unquoted ON join column quoted ("b"."y") and an
         // unquoted USING column as written ("b".y), which are the same column
         if(twin.getParseResult() == UniformSQL.PARSE_SUCCESS) {
            assertEquals(regenerate(twin).replace("\"", ""), regenerate(sql).replace("\"", ""),
                         type + ": " + using);
         }
      }
   }

   /**
    * A backtick quoted (MySQL) USING column is regenerated with its quotes, and so is a
    * quoted column of a multi-column USING, while the unquoted one stays unquoted.
    */
   @Test
   void quotesOfEachUsingColumnAreKept() throws Exception {
      String generated = regenerate(process(
         "select a.x from a join b using (`id`) join c using (`y`)", dataSource("mysql")));
      assertTrue(generated.contains(".`id` = "), generated);
      assertTrue(generated.contains(".`y` = "), generated);

      UniformSQL sql = process(COLS + CHAIN + " join \"SHIPMENTS\" using (\"ORDER_ID\", PRODUCT_ID)",
                               dataSource("postgresql"));
      generated = regenerate(sql);
      assertTrue(generated.contains("\"ORDER_DETAILS\".\"ORDER_ID\" = \"SHIPMENTS\".\"ORDER_ID\""),
                 generated);
      assertTrue(generated.contains("\"ORDER_DETAILS\".PRODUCT_ID = \"SHIPMENTS\".PRODUCT_ID"),
                 generated);
   }

   /**
    * The quotes of a USING column are saved with the query. A query saved without them
    * (before this fix) loads and regenerates its column unquoted, as before.
    */
   @Test
   void quotedUsingColumnSurvivesXmlRoundTrip() throws Exception {
      JDBCDataSource ds = dataSource("postgresql");
      UniformSQL sql = process(REPORTED, ds);
      String generated = regenerate(sql);
      String xml = save(sql);
      assertTrue(xml.contains("quotedColumn=\"CATEGORY_ID\""), xml);

      UniformSQL loaded = load(xml);
      loaded.setDataSource(ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, loaded.getParseResult());
      assertEquals(generated, regenerate(loaded));

      UniformSQL old = load(xml.replaceAll(" columnQuote=\"[^\"]*\" quotedColumn=\"[^\"]*\"", ""));
      old.setDataSource(ds);
      String oldGenerated = regenerate(old);
      assertTrue(oldGenerated.contains("\"CATEGORIES\".CATEGORY_ID = \"PRODUCTS\".CATEGORY_ID"),
                 oldGenerated);
   }

   /**
    * The refusals of a USING join whose column table is not the last table are kept: a
    * nested join as the joined table, a column merged by an earlier LEFT or FULL USING
    * join, and a NATURAL join.
    */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select a.x from a join (b join c using (y)) using (id) | the joined table is a nested join",
      "select a.x from a left join b using (id) join c using (id) | " +
         "the column is merged by an earlier outer join",
      "select a.x from a full join b using (id) join c using (x) join d using (id) | " +
         "the column is merged by an earlier outer join",
      "select a.x from a natural join b join c using (x) | natural join"
   })
   void otherUsingRefusalsAreKept(String text, String message) throws Exception {
      JDBCDataSource ds = dataSource("postgresql");
      RecognitionException ex = assertThrows(RecognitionException.class, () -> {
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      });
      assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains(message), ex.getMessage());
      assertEquals(UniformSQL.PARSE_FAILED, process(text, ds).getParseResult());
   }

   // compare the rows of the original and the regenerated sql, 0 if the database can't
   // run the original
   private static int assertSameRows(Connection conn, String text, String generated)
      throws SQLException
   {
      String expected;

      try {
         expected = rows(conn, text);
      }
      catch(SQLException ex) {
         return 0;
      }

      assertEquals(expected, rows(conn, generated),
                   conn.getMetaData().getDatabaseProductName() + ": " + text + " -> " + generated);
      return 1;
   }

   // the regenerated sql re-parses (as ON joins) to itself. PostgreSQLHelper writes the
   // table of an ON join column quoted (c."X" as "c"."X", "T".X as "T"."X"), but not the
   // table of a USING column, as before this fix for a single USING join. The sql it then
   // writes returns the same rows and re-parses to itself
   private static void assertRoundTrip(String generated, JDBCDataSource ds)
      throws SQLException
   {
      UniformSQL sql = process(generated, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), generated);
      assertFalse(sql.isLossy(), generated);
      String again = regenerate(sql);

      if(!generated.equals(again)) {
         assertEquals(PostgreSQLHelper.class, SQLHelper.getSQLHelper(ds).getClass(), again);
         assertEquals(again, regenerate(process(again, ds)));

         if(h2Postgres != null) {
            assertSameRows(h2Postgres, generated, again);
         }
      }
   }

   private static void collectConditions(XFilterNode node, List<XFilterNode> list) {
      if(node instanceof XSet set) {
         for(int i = 0; i < set.getChildCount(); i++) {
            collectConditions((XFilterNode) set.getChild(i), list);
         }
      }
      else if(node != null) {
         list.add(node);
      }
   }

   private static void createTables(Connection conn) throws SQLException {
      try(Statement st = conn.createStatement()) {
         st.execute("create table \"CATEGORIES\" (\"CATEGORY_ID\" int, " +
                    "\"CATEGORY_NAME\" varchar(20))");
         st.execute("create table \"PRODUCTS\" (\"PRODUCT_ID\" int, \"CATEGORY_ID\" int, " +
                    "\"PRODUCT_NAME\" varchar(20))");
         st.execute("create table \"ORDER_DETAILS\" (\"ORDER_ID\" int, \"PRODUCT_ID\" int, " +
                    "\"QUANTITY\" int)");
         st.execute("create table \"ORDERS\" (\"ORDER_ID\" int, \"CUSTOMER\" varchar(20))");
         st.execute("create table \"SHIPMENTS\" (\"ORDER_ID\" int, \"PRODUCT_ID\" int, " +
                    "\"SHIPPED\" int)");
         st.execute("insert into \"CATEGORIES\" values (1, 'Hardware'), (2, 'Software'), " +
                    "(3, 'Books'), (null, 'None')");
         st.execute("insert into \"PRODUCTS\" values (10, 1, 'Disk'), (20, 2, 'Editor'), " +
                    "(30, 1, 'Mouse'), (40, 5, 'Orphan'), (null, 1, 'Nil')");
         st.execute("insert into \"ORDER_DETAILS\" values (100, 10, 1), (100, 20, 2), " +
                    "(101, 10, 3), (102, 30, 4), (103, 50, 5), (104, null, 6)");
         st.execute("insert into \"ORDERS\" values (100, 'Ann'), (101, 'Bob'), (105, 'Cy'), " +
                    "(null, 'Dee')");
         st.execute("insert into \"SHIPMENTS\" values (100, 10, 1), (101, 10, 0), " +
                    "(102, 99, 1), (100, 20, 1), (null, 30, 1)");
      }
   }

   private static boolean hasDriver(String name) {
      try {
         Class.forName(name);
         return true;
      }
      catch(ClassNotFoundException ex) {
         return false;
      }
   }

   // the sorted rows, by column name so a regenerated select list order doesn't matter
   private static String rows(Connection conn, String text) throws SQLException {
      try(Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(text)) {
         ResultSetMetaData meta = rs.getMetaData();
         int count = meta.getColumnCount();
         List<String> rows = new ArrayList<>();

         while(rs.next()) {
            List<String> row = new ArrayList<>();

            for(int i = 1; i <= count; i++) {
               row.add(meta.getColumnLabel(i).toUpperCase(Locale.ROOT) + "=" + rs.getObject(i));
            }

            Collections.sort(row);
            rows.add(row.toString());
         }

         Collections.sort(rows);
         return count + " columns " + rows;
      }
   }

   // the chain with unquoted names, which fold to the upper case names of the tables
   private static String unquote(String text) {
      return text.replace("\"", "");
   }

   private static String joins(UniformSQL sql) {
      XJoin[] joins = sql.getJoins();
      return joins == null ? "" : Arrays.stream(joins)
         .map(j -> j.getExpression1().getValue() + " " + j.getOp() + " " +
            j.getExpression2().getValue())
         .sorted()
         .collect(Collectors.joining(", "));
   }

   private static String tables(UniformSQL sql) {
      return Arrays.stream(sql.getSelectTable())
         .map(t -> t.getName() + " " + t.getAlias())
         .collect(Collectors.joining(", "));
   }

   // regenerate the sql from the parsed structure, as VPM does after changing it
   private static String regenerate(UniformSQL sql) {
      UniformSQL copy = (UniformSQL) sql.clone();
      copy.clearSQLString();
      return copy.getSQLString().replaceAll("\\s+", " ").trim();
   }

   // parse the way setSQLString does, keeping the sql string
   private static UniformSQL process(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse(text);
      return sql;
   }

   private static JDBCQuery query(UniformSQL sql, JDBCDataSource ds) {
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      return query;
   }

   private static String save(UniformSQL sql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return buffer.toString();
   }

   private static UniformSQL load(String xml) throws Exception {
      Element elem = Tool.parseXML(new StringReader(xml)).getDocumentElement();
      UniformSQL sql = new UniformSQL();
      sql.parseXML(elem);
      return sql;
   }

   private static JDBCDataSource dataSource(String type) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds78381_" + type);
      ds.setProductVersion("19.0");

      switch(type.replace("-ansi", "")) {
      case "h2" -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:test");
      }
      case "mysql" -> {
         ds.setDriver("com.mysql.cj.jdbc.Driver");
         ds.setURL("jdbc:mysql://localhost:3306/test");
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
      }
      default -> throw new IllegalArgumentException(type);
      }

      ds.setAnsiJoin(type.endsWith("-ansi"));
      String helper = SQLHelper.getSQLHelper(ds).getSQLHelperType();
      assertEquals(type.replace("-ansi", ""), helper, "helper for " + type);
      return ds;
   }

}
