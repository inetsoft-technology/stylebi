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
import inetsoft.uql.util.Config;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
 * Bug #77546, the first join of a text order step that adds two new tables is written with its
 * tables in from order, not in the order of its ON operands. A FULL join saved as b *=* a, or an
 * inner join written later table first (a join b on b.id = a.id), was regenerated as b JOIN a,
 * which returns the same rows but changes the column order of a bare select *.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  SQLHelperTextJoinSymmetricOrderTest.DataSourceConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperTextJoinSymmetricOrderTest {
   private static final String DERBY = "jdbc:derby:memory:bug77546";
   private static final String HSQLDB = "jdbc:hsqldb:mem:bug77546";
   // not on the core test classpath; checked when they are added to it
   private static final String H2 = "jdbc:h2:mem:bug77546;DB_CLOSE_DELAY=-1";
   private static final String SQLITE = "jdbc:sqlite::memory:";
   private static final String[] TABLES = { "a", "b", "c", "d" };
   private static final Map<String, Connection> connections = new LinkedHashMap<>();

   /**
    * The parsed shapes: the sql, the join step it must contain in every ANSI helper, and the
    * table whose columns a bare select * must return before those of the other table.
    */
   static Stream<Arguments> parsedShapes() {
      return Stream.of(
         Arguments.of("select * from a join b on b.id = a.id left join c on c.id = a.id",
                      "from (a INNER JOIN b ON b.id = a.id ) LEFT OUTER JOIN c", "A", "B"),
         Arguments.of("select * from a x join b y on y.id = x.id left join c on c.id = x.id",
                      "from (a x INNER JOIN b y ON y.id = x.id ) LEFT OUTER JOIN c", "A", "B"),
         Arguments.of("select * from s.a join s.b on s.b.id = s.a.id left join s.c on s.c.id = s.a.id",
                      "from (s.a INNER JOIN s.b ON s.b.id = s.a.id ) LEFT OUTER JOIN s.c", "A", "B"),
         Arguments.of("select * from a join b on b.id = a.id and b.k = a.k left join c on c.id = a.id",
                      "from (a INNER JOIN b ON b.id = a.id AND b.k = a.k ) LEFT OUTER JOIN c",
                      "A", "B"),
         // only the first step of a chain is a step of two new tables
         Arguments.of("select * from a join b on b.id = a.id join c on c.id = b.id " +
                         "left join d on d.id = a.id",
                      "from ((a INNER JOIN b ON b.id = a.id ) INNER JOIN c ON c.id = b.id ) " +
                         "LEFT OUTER JOIN d", "A", "B"),
         // the comma table c is still written after the join group, so only a and b are checked
         Arguments.of("select * from c, a join b on b.id = a.id left join d on d.id = a.id",
                      "(a INNER JOIN b ON b.id = a.id ) LEFT OUTER JOIN d", "A", "B"),
         // a parenthesized left operand whose ON names the earlier table second
         Arguments.of("select * from (b join a on a.id = b.id) left join c on c.id = a.id",
                      "from (b INNER JOIN a ON a.id = b.id ) LEFT OUTER JOIN c", "B", "A"),
         // a nested right operand is written first; its own tables are in from order
         Arguments.of("select * from a left join (c join b on b.id = c.id) on a.id = b.id",
                      "from (c INNER JOIN b ON b.id = c.id ) RIGHT OUTER JOIN a", "C", "B"),
         // new parses of outer joins are already oriented by the parser, so they are unchanged
         Arguments.of("select * from a full join b on b.id = a.id",
                      "from a FULL OUTER JOIN b ON a.id = b.id", "A", "B"),
         Arguments.of("select * from a right join b on b.id = a.id left join c on c.id = b.id",
                      "from (a RIGHT OUTER JOIN b ON a.id = b.id ) LEFT OUTER JOIN c", "A", "B"),
         Arguments.of("select * from a left join b on b.id = a.id",
                      "from a LEFT OUTER JOIN b ON a.id = b.id", "A", "B"),
         // already in from order
         Arguments.of("select * from a join b on a.id = b.id left join c on c.id = a.id",
                      "from (a INNER JOIN b ON a.id = b.id ) LEFT OUTER JOIN c", "A", "B")
      );
   }

   // the helpers that write the outer join queries with ANSI joins
   static Stream<String> ansiHelpers() {
      return Stream.of("default", "h2", "h2 ansi", "postgresql", "sql server", "oracle ansi");
   }

   static Stream<Arguments> parsedShapeCases() {
      return ansiHelpers().flatMap(helper -> parsedShapes().map(shape -> {
         Object[] args = shape.get();
         return Arguments.of(helper, args[0], args[1]);
      }));
   }

   @ParameterizedTest(name = "{0}: {1}")
   @MethodSource("parsedShapeCases")
   void parsedJoinStepIsInFromOrder(String helper, String text, String step) throws Exception {
      UniformSQL sql = parse(text, dataSource(helper));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      String generated = generate(sql);

      assertTrue(unquote(generated).contains(step), generated);
      assertRoundTrip(generated, helper);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("parsedShapes")
   void parsedShapeReturnsSameRows(String text, String step, String first, String second)
      throws Exception
   {
      for(String helper : new String[] { "default", "h2 ansi" }) {
         String generated = generate(parse(text, dataSource(helper)));
         assertSameRows(text, generated);
         assertColumnsBefore(generated, first, second);
      }
   }

   @Test
   void oracleWritesSavedFullJoinInFromOrder() throws Exception {
      // oracle without ansi join writes a left join with (+), and a full join with ANSI
      JDBCDataSource oracle = dataSource("oracle");
      UniformSQL sql = savedReversed("select * from a full join b on a.id = b.id", oracle);
      assertEquals("select * from a FULL OUTER JOIN b ON b.id = a.id", normalize(generate(sql)));

      sql = parse("select * from a left join b on b.id = a.id", oracle);
      assertEquals("select * from a, b where a.id = b.id(+)", normalize(generate(sql)));
   }

   /**
    * A full join parsed before Bug #77440 oriented outer joins by from order is saved with
    * the later table first, b *=* a.
    */
   @ParameterizedTest(name = "{0}")
   @MethodSource("ansiHelpers")
   void savedReversedFullJoinIsInFromOrder(String helper) throws Exception {
      JDBCDataSource ds = dataSource(helper);
      String text = "select * from a full join b on a.id = b.id";
      String generated = generate(savedReversed(text, ds));
      assertEquals("select * from a FULL OUTER JOIN b ON b.id = a.id", unquote(normalize(generated)));

      generated = generate(savedReversed(text + " left join c on c.id = a.id", ds));
      assertTrue(unquote(generated).contains(
         "from (a FULL OUTER JOIN b ON b.id = a.id ) LEFT OUTER JOIN c"), generated);
   }

   @Test
   void savedReversedFullJoinReturnsSameRows() throws Exception {
      for(String text : new String[] {
         "select * from a full join b on a.id = b.id",
         "select * from a full join b on a.id = b.id left join c on c.id = a.id" })
      {
         for(String helper : new String[] { "default", "h2 ansi" }) {
            String generated = generate(savedReversed(text, dataSource(helper)));
            assertSameRows(text, generated);
            assertColumnsBefore(generated, "A", "B");
         }
      }
   }

   /**
    * A left join saved with the wrong orientation before Bug #77440 (b *= a, b preserved) keeps
    * its saved meaning: b LEFT OUTER JOIN a is now written a RIGHT OUTER JOIN b.
    */
   @Test
   void savedReversedLeftJoinKeepsItsMeaning() throws Exception {
      for(String helper : new String[] { "default", "h2 ansi" }) {
         String generated = generate(savedReversed("select * from a left join b on a.id = b.id",
                                                   dataSource(helper)));
         assertEquals("select * from a RIGHT OUTER JOIN b ON b.id = a.id", normalize(generated));
         assertSameRows("select * from b left join a on b.id = a.id", generated);
      }
   }

   /**
    * A left join saved as b =* a (a preserved) keeps its meaning: b RIGHT OUTER JOIN a is now
    * written a LEFT OUTER JOIN b.
    */
   @Test
   void savedReversedRightJoinKeepsItsMeaning() throws Exception {
      for(String helper : new String[] { "default", "h2 ansi" }) {
         String generated = generate(savedReversed("select * from a right join b on a.id = b.id",
                                                   dataSource(helper)));
         assertEquals("select * from a LEFT OUTER JOIN b ON b.id = a.id", normalize(generated));
         assertSameRows("select * from a left join b on a.id = b.id", generated);
         assertColumnsBefore(generated, "A", "B");
      }
   }

   @Test
   void joinsWithoutRecordedClauseKeepTheirOrder() throws Exception {
      // a full join saved before Bug #77475 recorded the join clauses is generated by the join
      // matrix, which this doesn't change
      UniformSQL legacy = savedReversed("select * from a full join b on a.id = b.id",
                                        dataSource("default"));
      String xml = toXml(legacy).replaceAll(" joinClause=\"-?\\d+\"", "");
      assertEquals("select * from b FULL OUTER JOIN a ON b.id = a.id",
                   normalize(generate(fromXml(xml, dataSource("default")))));

      // a join added in the query editor to a parsed query is written as before
      UniformSQL sql = parse("select * from a left join b on a.id = b.id, c, d",
                             dataSource("default"));
      sql.addJoin(new XJoin(new XExpression("d.id", XExpression.FIELD),
                            new XExpression("c.id", XExpression.FIELD), "="));
      String generated = normalize(generate(sql));
      assertTrue(generated.contains("d INNER JOIN c ON d.id = c.id"), generated);
   }

   /**
    * MongoHelper can't write join parentheses (56305), and writes an inner join of a group and
    * a chain that starts with the step's table as one flat chain (#77581). That needs the
    * chain's first table to be its first join's table1, so it keeps the operand order.
    */
   @Test
   void mongoKeepsTheOperandOrderForFlatGroups() throws Exception {
      for(String type : new String[] { "mongo", "mongo ansi" }) {
         String text = "select * from a left join b on a.id = b.id join (c join d on d.id = c.id) " +
            "on b.id = d.id";
         String generated = normalize(generate(parse(text, dataSource(type))));
         assertEquals("select * from a LEFT OUTER JOIN b ON a.id = b.id INNER JOIN d ON b.id = d.id " +
                         "INNER JOIN c ON d.id = c.id", generated, type);
         assertSameRows(text, generated);

         text = "select * from a join b on b.id = a.id left join c on c.id = a.id";
         generated = normalize(generate(parse(text, dataSource(type))));
         assertEquals("select * from b INNER JOIN a ON b.id = a.id LEFT OUTER JOIN c ON a.id = c.id",
                      generated, type);
         assertSameRows(text, generated);
      }
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
            if(url == SQLITE) {
               stmt.executeUpdate("attach database ':memory:' as s");
            }
            else {
               stmt.executeUpdate("create schema s");
            }

            for(String table : TABLES) {
               stmt.executeUpdate("create table " + table + " (id int, k int, tn varchar(1))");
               stmt.executeUpdate("create table s." + table + " (id int, k int, tn varchar(1))");
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
         DriverManager.getConnection("jdbc:derby:memory:bug77546;drop=true").close();
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

   /**
    * Runs both queries on random data with nulls, and requires the same rows from both. The
    * columns of each row are compared by their table, so the select * column order of the
    * two queries may differ. Derby and H2 have no FULL join.
    */
   private static void assertSameRows(String expected, String generated) throws SQLException {
      boolean full = expected.contains(" full ");

      for(Map.Entry<String, Connection> entry : connections.entrySet()) {
         String url = entry.getKey();

         if(full && (url == H2 || url.startsWith("jdbc:derby"))) {
            continue;
         }

         Random random = new Random(77546);

         for(int i = 0; i < 200; i++) {
            fillTables(entry.getValue(), random);
            assertEquals(rows(entry.getValue(), expected), rows(entry.getValue(), generated),
                         url + " dataset " + i + "\nexpected: " + expected +
                         "\ngenerated: " + generated);
         }
      }
   }

   // the columns of the first table are before the columns of the second table
   private static void assertColumnsBefore(String sql, String first, String second)
      throws SQLException
   {
      // hsqldb reports the columns of a parenthesized join as of a subquery, so the tables are
      // found from their name column in a dataset of one row per table, which every join keeps
      Connection conn = connections.get(HSQLDB);
      fillTables(conn, null);

      try(Statement stmt = conn.createStatement(); ResultSet result = stmt.executeQuery(sql)) {
         assertTrue(result.next(), sql);
         List<String> tables = new ArrayList<>();

         for(int i = 3; i <= result.getMetaData().getColumnCount(); i += 3) {
            tables.add(result.getString(i).toUpperCase());
         }

         int index1 = tables.indexOf(first);
         int index2 = tables.indexOf(second);
         assertTrue(index1 >= 0 && index2 >= 0 && index1 < index2, tables + " " + sql);
      }
   }

   // random rows with nulls, or one row (1, 1) per table without a random
   private static void fillTables(Connection conn, Random random) throws SQLException {
      for(String schema : new String[] { "", "s." }) {
         for(String table : TABLES) {
            try(Statement stmt = conn.createStatement()) {
               stmt.executeUpdate("delete from " + schema + table);
            }

            try(PreparedStatement insert =
                   conn.prepareStatement("insert into " + schema + table + " values (?, ?, ?)"))
            {
               int count = random == null ? 1 : random.nextInt(5);

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
   }

   // the result rows of a select * as a sorted multiset. Each table has the columns id, k and
   // its name, so the columns of a row are compared by table, and the column order of the two
   // queries may differ
   private static List<String> rows(Connection conn, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Statement stmt = conn.createStatement(); ResultSet result = stmt.executeQuery(query)) {
         int columns = result.getMetaData().getColumnCount();

         while(result.next()) {
            List<String> tables = new ArrayList<>();

            for(int i = 1; i <= columns; i += 3) {
               // a table on the null side of an outer join is all nulls, and sorts last
               Object name = result.getObject(i + 2);
               tables.add((name == null ? "~" : name) + ":" + result.getObject(i) + "|" +
                          result.getObject(i + 1));
            }

            Collections.sort(tables);
            rows.add(String.join(",", tables));
         }
      }

      Collections.sort(rows);
      return rows;
   }

   // the regenerated sql re-parses to a fixed point. A reparsed outer join can swap its ON
   // operands once, e.g. a outer join written after a nested group (c join b) RIGHT OUTER JOIN a
   private static void assertRoundTrip(String generated, String helper) throws Exception {
      String regenerated = generate(parse(generated, dataSource(helper)));
      assertEquals(normalize(regenerated),
                   normalize(generate(parse(regenerated, dataSource(helper)))));
   }

   /**
    * A query parsed and saved with the operands of its first join in the other order, as the
    * parser recorded a full join before Bug #77440.
    */
   private static UniformSQL savedReversed(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = parse(text, ds);
      XJoin join = sql.getJoins()[0];
      XExpression expression1 = join.getExpression1();
      join.setExpression1(join.getExpression2());
      join.setExpression2(expression1);
      return fromXml(toXml(sql), ds);
   }

   private static String generate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString();
   }

   private static String toXml(UniformSQL sql) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      sql.writeXML(writer);
      writer.flush();
      return buffer.toString();
   }

   private static UniformSQL fromXml(String xml, JDBCDataSource ds) throws Exception {
      Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      UniformSQL sql = new UniformSQL();
      sql.parseXML(root);
      sql.setDataSource(ds);
      return sql;
   }

   private static JDBCDataSource dataSource(String type) {
      if("default".equals(type)) {
         return GenericJDBCDataSource.create();
      }

      boolean ansi = type.endsWith(" ansi");
      String product = ansi ? type.substring(0, type.length() - 5) : type;
      String[] driver = switch(product) {
         case "h2" -> new String[] { "org.h2.Driver", "jdbc:h2:mem:db" };
         case "postgresql" -> new String[] { "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/db" };
         case "sql server" -> new String[] { "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                                             "jdbc:sqlserver://localhost;databaseName=db" };
         case "oracle" -> new String[] { "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:db" };
         case "mongo" -> new String[] { "mongodb.jdbc.MongoDriver", "jdbc:mongo://localhost:27017/test" };
         default -> throw new IllegalArgumentException(type);
      };

      JDBCDataSource ds = new JDBCDataSource();
      ds.setAnsiJoin(ansi);
      ds.setName("bug77546");
      ds.setDriver(driver[0]);
      ds.setURL(driver[1]);
      // a version so the helper lookup doesn't query the database
      ds.setProductVersion("19.0");
      assertEquals(product, SQLHelper.getProductName(ds));
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

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
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
