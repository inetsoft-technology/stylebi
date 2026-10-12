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
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.XUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;
import java.util.regex.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77778, the lexer has no token for a number that starts with a dot, so {@code .5} is
 * a DOT and the number {@code 5}. The parser stored it as {@code . 5}, a syntax error on
 * every database, or as the bare {@code 5} when the data source is SQL Server, which runs
 * with a value 10 (or 100...) times too large.
 *
 * A dot directly followed by a number without a dot of its own is kept as {@code .5} on every
 * database. Anything else after a dot is not valid SQL and is refused, so the original SQL
 * runs.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLLeadingDotDecimalTest {
   private static final String DERBY_URL = "jdbc:derby:memory:bug77778;create=true";
   private static final String[] TYPES =
      { "default", "h2", "h2-ansi", "postgresql", "oracle", "mysql", "sql server", "derby" };

   @BeforeAll
   static void createTables() throws Exception {
      try(Connection conn = derby(); Statement stmt = conn.createStatement()) {
         try {
            stmt.execute("drop table b");
         }
         catch(SQLException ignore) {
            // first run
         }

         stmt.execute("create table b (id int, a decimal(10,2))");
         stmt.execute("insert into b values (6, 6), (9, 6.5), (12, 12), (1, 0.5), (2, 5)");
      }
   }

   @AfterAll
   static void dropDatabase() throws Exception {
      try {
         derbyDriver().connect("jdbc:derby:memory:bug77778;drop=true", new Properties());
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   // the text the default helper writes, with the number kept as written, in every clause
   // and at every query level
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select b.id from b where b.a * .5 = 3|select b.id from b where b.a*.5 = 3",
      "select b.id from b where b.a*.5=3|select b.id from b where b.a*.5 = 3",
      "select b.id from b where b.a = .05|select b.id from b where b.a = .05",
      "select b.id from b where b.a = .50|select b.id from b where b.a = .50",
      "select b.id from b where b.a > .5e-1|select b.id from b where b.a > .5e-1",
      "select b.id from b where b.a > .5E+1|select b.id from b where b.a > .5E+1",
      "select b.id from b where b.a - .5 > 5|select b.id from b where b.a-.5 > 5",
      "select b.id from b where b.a = .5|select b.id from b where b.a = .5",
      "select b.id from b where b.a in (.5, 6)|select b.id from b where b.a IN (.5,6)",
      "select b.id from b where b.a between .5 and 6.5|" +
         "select b.id from b where b.a BETWEEN .5 and 6.5",
      "select b.id from b where b.a > .5 and b.a < 6.|" +
         "select b.id from b where b.a > .5 and b.a < 6.",
      "select case when b.a > 6 then .5 else 1 end as f from b|" +
         "select case when b.a > 6 then .5 else 1 END as f from b",
      "select abs(b.a * .5) as f from b|select abs(b.a*.5) as f from b",
      "select cast(.5 as decimal(4,2)) as f from b|select cast(.5 as decimal(4,2)) as f from b",
      "select b.id from b where {fn abs(b.a)} > .5|select b.id from b where {fn abs(b.a)} > .5",
      "select b.id, (select .5 from b c where c.id = 1) as f from b|" +
         "select (select .5 from b c where c.id = 1 ) as f, b.id from b",
      "select b.id from b where b.a = (.5)|select b.id from b where b.a = (.5)",
      "select .5 as h from b|select .5 as h from b",
      "select b.id from b order by .5|select b.id from b order by .5 asc",
      "select b.id from b where b.id = (select max(c.a) * .5 from b c)|" +
         "select b.id from b where b.id = ( select max(c.a)*.5 from b c)",
      // #77754: the binary minus and the negative operand stay apart
      "select b.id from b where b.a = .5 - -1|select b.id from b where b.a = .5- - 1",
   })
   void numberIsKeptAsWritten(String text, String expected) throws Exception {
      JDBCDataSource ds = dataSource("default");
      String generated = regenerate(parse(text, ds), text);

      assertEquals(expected, generated);
      assertRoundTrip(generated, ds, text);
   }

   // every helper, sql server included, writes the number with its dot. The data source is
   // set before the parse, since the parser read it
   @ParameterizedTest
   @ValueSource(strings = {
      "select b.id from b where b.a * .5 = 3",
      "select b.id from b where b.a = .05 or b.a = .50",
      "select b.id from b where b.a > .5e-1 and b.a < .5E+1",
      "select b.id, b.a * .5 from b where b.a - .5 > 5",
      "select b.id from b where b.a in (.5, 6) or b.a between .5 and 6.5",
      "select b.id, case when b.a > 6 then .5 else 1 end as f from b",
      "select b.id, abs(b.a * .5) as f, cast(.5 as decimal(4,2)) as g from b",
      "select b.id from b where {fn abs(b.a)} > .5",
      "select b.id, (select .5 from b c where c.id = 1) as f from b",
      "select b.id from b where b.a = (.5)",
      "select .5 as h, b.id from b",
      "select .5, b.id from b",
      "select b.id from b order by .5",
      "select b.id from b where b.id = (select max(c.a) * .5 from b c)",
      "select sum(b.a) * .5 from b having sum(b.a) * .5 > 3",
   })
   void everyHelperKeepsTheDot(String text) throws Exception {
      List<String> numbers = numbers(text);
      assertFalse(numbers.isEmpty(), text);

      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         String generated = regenerate(parse(text, ds), text);
         String message = type + ": " + text + " -> " + generated;

         assertEquals(numbers, numbers(generated), message);
         assertFalse(Pattern.compile("\\.\\s+\\d").matcher(generated).find(), message);

         // postgresql writes a select list subquery that re-parses with c."id", which keeps
         // it from being generated to itself whatever its numbers are (see #77754)
         if(!type.equals("postgresql") || !text.contains(", (select")) {
            assertRoundTrip(generated, ds, message);
         }
      }
   }

   // the original and the regenerated SQL return the same rows on Derby, for the SQL the
   // derby, sql server and default helpers write, and as the data cache normalizer runs a
   // plain query
   @ParameterizedTest
   @ValueSource(strings = {
      "select b.id from b where b.a * .5 = 3",
      "select b.id from b where b.a * .5 * 2 = 6",
      "select b.id from b where b.a - .5 > 5",
      "select b.id from b where b.a + .5 > 6",
      "select b.id from b where b.a / .5 = 12",
      "select b.id from b where b.a = .5",
      "select b.id from b where b.a = .50",
      "select b.id from b where b.a < .05 or b.a = 5",
      "select b.id from b where b.a > .5e1",
      "select b.id from b where b.a > .5e-1 and b.a < .5E+1",
      "select b.id from b where b.a in (.5, 6)",
      "select b.id from b where b.a between .5 and 6.5",
      "select b.id from b where b.a > .5 and b.a < 6.",
      "select b.id, b.a * .5 from b",
      "select b.id, b.a * .5 as h from b",
      "select b.id, .5 from b",
      "select b.id, .5 as h from b",
      "select b.id, case when b.a > 6 then .5 else 1 end as f from b",
      "select b.id, abs(b.a * .5) as f from b",
      "select b.id, cast(.5 as decimal(4,2)) as f from b",
      "select b.id from b where {fn abs(b.a)} > .5",
      "select b.id, (select .5 from b c where c.id = 1) as f from b",
      "select b.id from b where b.id = (select max(c.a) * .5 from b c)",
      "select b.id from b where b.a = (.5)",
      "select b.id from b where b.a = .5 - -1",
      "select b.id from b where b.a * .5 = 3 and b.id > 0",
   })
   void sameRowsOnDerby(String text) throws Exception {
      try(Connection conn = derby()) {
         List<String> expected = rows(conn, text);

         for(String type : new String[] { "derby", "sql server", "default" }) {
            JDBCDataSource ds = dataSource(type);
            String generated = generate(parse(text, ds), text);
            assertEquals(expected, rows(conn, generated), type + ": " + text + " -> " + generated);

            String normalized = normalized(text, ds);
            assertEquals(expected, rows(conn, normalized),
                         type + " cache normalizer: " + text + " -> " + normalized);
         }
      }
   }

   // the values of a select list .5, by row
   @Test
   void selectListValues() throws Exception {
      String text = "select b.id, b.a * .5 as h from b";
      String generated = generate(parse(text, dataSource("sql server")), text);

      try(Connection conn = derby()) {
         assertEquals(List.of("0.250|1|", "2.500|2|", "3.000|6|", "3.250|9|", "6.000|12|"),
                      rows(conn, text));
         assertEquals(rows(conn, text), rows(conn, generated), generated);
      }
   }

   // a .5 select item has no table part, unlike 0.5. With the table metadata loaded, the
   // fixed query is still written with the number as written. The column is written in the
   // case of the metadata and moved ahead, as for any expression
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select .5, b.id from b|select .5,b.ID from b",
      "select b.a * .5, b.id from b|select b.ID,b.a*.5 from b",
      "select .5 as h, b.a * .5 as k, b.id from b where b.a * .5 > 1|" +
         "select .5 as h,b.ID,b.a*.5 as k from b where b.a*.5 > 1",
   })
   void tableMetadataLoaded(String text, String expected) throws Exception {
      for(String type : new String[] { "derby", "sql server" }) {
         JDBCDataSource ds = dataSource(type);
         // the table metadata is cached by data source, every query gets another one
         ds.setName(ds.getName() + "_meta_" + (++sources));
         // the derby types qualify a table by the default schema of the root metadata otherwise
         ds.setTableNameOption(JDBCDataSource.TABLE_OPTION);
         UniformSQL sql = parse(text, ds);
         JDBCUtil.fixUniformSQLInfo(sql, repository("ID", "A"), null, ds);
         String generated = regenerate(sql, text);

         assertEquals(expected, generated.replace(", ", ","), type);

         try(Connection conn = derby()) {
            assertEquals(rows(conn, text), rows(conn, generated), type + ": " + generated);
         }
      }
   }

   // the column text names an unaliased select item
   @Test
   void unaliasedSelectColumnText() {
      for(String type : TYPES) {
         UniformSQL sql = parse("select b.a * .5, .5, b.id from b", dataSource(type));
         // postgresql stores the names quoted
         assertTrue(sql.getSelection().getColumn(0).matches("(?i)\"?b\"?\\.\"?a\"?\\*\\.5"),
                    type + ": " + sql.getSelection().getColumn(0));
         assertEquals(".5", sql.getSelection().getColumn(1), type);
      }
   }

   // the check for the number runs after the dot is matched, so the number after it is seen
   // and .5 parses, alone and as an expression
   @Test
   void leadingDotNumberParses() throws Exception {
      for(String type : TYPES) {
         UniformSQL sql = parse("select .5 from b", dataSource(type));
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type);
      }

      assertTrue(XUtil.isSQLExpressionValid(".5"));
      assertTrue(XUtil.isSQLExpressionValid("b.a * .5 + .05"));
   }

   // anything else after a dot is not valid sql, on any database. It is refused, so the
   // original runs, instead of being written as ". x" or, on sql server, without the dot
   @ParameterizedTest
   @ValueSource(strings = {
      "select b.id from b where .a = 6",
      "select b.id from b where b.a * .(5) = 3",
      "select b.id from b where b.a = .?",
      "select b.id from b where b.a = .'5'",
      "select b.id from b where b.a = .\"a\"",
      "select b.id from b where b.a = .b.a",
      "select b.id from b where b.a = .abs(5)",
      "select b.id from b where b.a = .5.5",
      "select b.id from b where b.a = .5.",
      "select b.id from b where b.a = . 5",
      "select b.id from b where b.a = .\n5",
      "select b.id from b where b.a = ./* x */5",
      "select .a, b.id from b",
      "select b.id, (select .c.a from b c) as f from b",
   })
   void otherOperandIsRefused(String text) throws Exception {
      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         new SQLProcessor(sql).parse(text);
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type + ": " + text);
         assertFalse(XUtil.isParsedSQL(sql), type + ": " + text);

         // the original is sent as written on the data cache path
         JDBCQuery query = new JDBCQuery();
         query.setDataSource(ds);
         query.setSQLDefinition(sql);
         assertFalse(new JDBCQueryCacheNormalizer(query).isClearedSqlString(), type);
         assertEquals(text, sql.getSQLString(), type);
      }

      // it is invalid as written too
      try(Connection conn = derby(); Statement stmt = conn.createStatement()) {
         assertThrows(SQLException.class, () -> stmt.executeQuery(text.replace("?", "a")));
      }
   }

   // the dot and the number are found next to each other by their line and column, so tabs,
   // line breaks and comments elsewhere in the query don't change it
   @ParameterizedTest
   @ValueSource(strings = {
      "select b.id\r\nfrom b\r\n\twhere b.a * .5 = 3",
      "select b.id\n\tfrom b\n\t\twhere\tb.a\t*\t.5 = 3",
      "-- c\nselect b.id from b /* c */ where b.a * .5 = 3",
   })
   void leadingDotAfterTabsAndLineBreaks(String text) throws Exception {
      for(String type : new String[] { "default", "sql server" }) {
         String generated = regenerate(parse(text, dataSource(type)), text);
         assertEquals("select b.id from b where b.a*.5 = 3", generated, type);
      }

      try(Connection conn = derby()) {
         assertEquals(List.of("6|"), rows(conn, text));
      }
   }

   // the text of every other number and of every column path is unchanged
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select b.a * 0.5 from b where b.a * 0.5 = 3|select b.a*0.5 from b where b.a*0.5 = 3",
      "select b.a * 5. from b where b.a * 5. = 30|select b.a*5. from b where b.a*5. = 30",
      "select b.a * 1.5 from b where b.a * 1.5 = 9|select b.a*1.5 from b where b.a*1.5 = 9",
      "select b.a * 5.e2 from b where b.a > 0.5e1|select b.a*5.e2 from b where b.a > 0.5e1",
      "select b.a from b where b.a = b.id|select b.a from b where b.a = b.id",
      "select b . a from b where b . a = 6|select b.a from b where b.a = 6",
      "select b.a from b where b .a = 6 and b. id = 6|select b.a from b where b.a = 6 and b.id = 6",
      "select t.c from db..t|select t.c from db..t",
      "select t.c from db..t t where t.c = 1|select t.c from db..t t where t.c = 1",
      "select db.dbo.t.c from db.dbo.t|select db.dbo.t.c from db.dbo.t",
      "select b.a from b where - b.a < 0 and + b.a > 0|select b.a from b where - b.a < 0 and + b.a > 0",
   })
   void otherTextIsUnchanged(String text, String expected) throws Exception {
      for(String type : new String[] { "default", "sql server" }) {
         String generated = regenerate(parse(text, dataSource(type)), text);
         assertEquals(expected, generated.replace(", ", ","), type);
      }
   }

   // the numbers in the text, each with the dot it was written with
   private static List<String> numbers(String text) {
      Matcher matcher = Pattern.compile("(?<![\\w.])\\.?\\d+(\\.\\d*)?([eE][-+]?\\d+)?")
         .matcher(text);
      List<String> numbers = new ArrayList<>();

      while(matcher.find()) {
         numbers.add(matcher.group());
      }

      return numbers;
   }

   // what JDBCQueryCacheNormalizer runs for a parsed, not lossy query: the sql string is
   // cleared and the SQL is regenerated with a sorted select list
   private static String normalized(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = parse(text, ds);
      JDBCQuery query = new JDBCQuery();
      query.setName("q77778");
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);
      assertTrue(normalizer.isClearedSqlString(), text);
      return sql.getSQLString();
   }

   private static List<String> rows(Connection conn, String query) {
      try {
         return SQLHelperNotEqualJoinTest.RowCompare.rows(conn, query);
      }
      catch(SQLException ex) {
         return fail("doesn't run: " + query + ": " + ex.getMessage());
      }
   }

   // the metadata of table b, the columns in the case derby stores them
   private static XRepository repository(String... columns) throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         XTypeNode result = new XTypeNode("Result");

         for(String column : columns) {
            result.addChild(XSchema.createPrimitiveType(column, Integer.class));
         }

         XTypeNode meta = new XTypeNode("meta");
         meta.addChild(result);
         return meta;
      });

      return repository;
   }

   // the regenerated SQL re-parses and is generated to itself
   private static void assertRoundTrip(String generated, JDBCDataSource ds, String message) {
      assertEquals(generated, regenerate(parse(generated, ds), generated),
                   "round trip, " + message);
   }

   // parse the way a query of the data source is parsed
   private static UniformSQL parse(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);

      try {
         sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      }
      catch(Exception ex) {
         fail("parse failed: " + text + ": " + ex.getMessage());
      }

      return sql;
   }

   private static String regenerate(UniformSQL sql, String text) {
      return normalize(generate(sql, text));
   }

   // the SQL as it's generated and sent to the database
   private static String generate(UniformSQL sql, String text) {
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.clearSQLString();
      return sql.getSQLString();
   }

   private static JDBCDataSource dataSource(String type) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77778_" + type);
      ds.setProductVersion("19.0");

      switch(type.replace("-ansi", "")) {
      case "default" -> {
         ds.setDriver("org.example.GenericDriver");
         ds.setURL("jdbc:example://localhost/test");
      }
      case "h2" -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:test");
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:test");
      }
      case "mysql" -> {
         ds.setDriver("com.mysql.cj.jdbc.Driver");
         ds.setURL("jdbc:mysql://localhost:3306/test");
      }
      case "sql server" -> {
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://localhost:1433;databaseName=test");
      }
      case "derby" -> {
         ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
         ds.setURL("jdbc:derby:memory:test;create=true");
         ds.setProductVersion("10.17");
      }
      default -> throw new IllegalArgumentException(type);
      }

      ds.setAnsiJoin(type.endsWith("-ansi"));
      assertEquals(type.replace("-ansi", ""), SQLHelper.getSQLHelper(ds).getSQLHelperType(),
                   "helper for " + type);
      return ds;
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static Driver derbyDriver() throws Exception {
      return (Driver) Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver")
         .getDeclaredConstructor().newInstance();
   }

   private static Connection derby() throws Exception {
      return derbyDriver().connect(DERBY_URL, new Properties());
   }

   private static int sources;
}
