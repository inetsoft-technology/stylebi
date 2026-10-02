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
import inetsoft.uql.XRepository;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Plugins;
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

import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77550, GROUP BY ROLLUP, CUBE, GROUPING SETS and a comma list of grouping sets
 * ("group by (), a", "group by cube(a), b") parsed as a non-lossy success with an empty
 * group by list, so the regenerated sql had no GROUP BY and returned different rows (or
 * failed). UniformSQL can't represent grouping sets, so the parse must fail and the original
 * sql runs. "group by ()" alone is the same as no GROUP BY and still parses.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLGroupingSetsRefuseTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLGroupingSetsRefuseTest {
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

   private static final String HSQLDB_URL = "jdbc:hsqldb:mem:bug77550";

   // every alternative of group_by_clause that matched grouping syntax without recording it
   private static final String[] REFUSED_GROUP_BY = {
      "rollup(a)",
      "ROLLUP (a, b)",
      "all rollup(a)",
      "cube(a)",
      "Cube(a, b)",
      "grouping sets ((a), (b))",
      "GROUPING SETS (a, b)",
      "grouping sets ((a, b), ())",
      "grouping sets (cube(a), b)",
      "grouping sets (rollup(a), b)",
      "(), a",
      "a, ()",
      "cube(a), b",
      "(a, b), b",
      "rollup(a), b",
      "a, rollup(b)",
   };

   static Stream<Arguments> refused() {
      Stream.Builder<Arguments> args = Stream.builder();

      for(String group : REFUSED_GROUP_BY) {
         // top level, with an aggregate, aggregate-only, aggregate-free, with HAVING and ORDER BY
         args.add(Arguments.of("select a, b, count(*) from t group by " + group));
         args.add(Arguments.of("select count(*) from t group by " + group));
         args.add(Arguments.of("select a from t group by " + group));
         args.add(Arguments.of("select a, count(*) from t where b > 0 group by " + group +
                                  " having count(*) > 1 order by a"));
         // derived table, IN, scalar and EXISTS subqueries
         args.add(Arguments.of("select * from (select a, count(*) c from t group by " + group + ") d"));
         args.add(Arguments.of("select * from u where u.x in (select max(a) from t group by " + group + ")"));
         args.add(Arguments.of("select u.x, (select max(a) from t group by " + group + ") m from u"));
         args.add(Arguments.of("select * from u where exists (select 1 from t where t.a = u.x group by " +
                                  group + ")"));
      }

      return args.build();
   }

   @ParameterizedTest
   @MethodSource("refused")
   void groupingSetsFailParse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);

      UniformSQL fresh = new UniformSQL();
      fresh.setSQLString(text, false);
      assertEquals(text, fresh.getSQLString());
      assertTrue(fresh.isLossy(), text);

      for(String type : new String[] { "h2", "h2-ansi", "postgresql", "oracle" }) {
         assertFalse(XUtil.isQueryMergeable(query(text, dataSource(type))), type + ": " + text);
      }

      // the production (asynchronous) parse
      UniformSQL async = parseAsync(text);
      assertEquals(UniformSQL.PARSE_FAILED, async.getParseResult(), text);
      assertFalse(XUtil.isParsedSQL(async), text);
      assertEquals(text, async.getSQLString());
   }

   // the original sql runs unchanged on the data cache path, and returns the grouping set rows
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select count(*) from t group by rollup(a)|4",
      "select sum(a) from t group by grouping sets ((a), ())|4",
      "select a from t group by rollup(a)|4",
      "select a, count(*) from t group by rollup(a)|4",
      "select a, b, count(*) from t group by cube(a), b|7",
      "select a, count(*) from t group by (), a|3",
      "select a, b, count(*) from t group by rollup(a), b|7",
      "select * from u where exists (select 1 from t where t.a = u.x group by cube(t.a))|3",
      "select * from u where u.x in (select count(*) from t group by rollup(a))|3",
   })
   void originalSqlRunsOnHsqldb(String text, int expectedRows) throws Exception {
      UniformSQL sql = parseAsync(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);

      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(sql);
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);
      assertFalse(normalizer.isClearedSqlString(), text);
      assertEquals(text, sql.getSQLString());

      try(Connection conn = hsqldb(); Statement stmt = conn.createStatement()) {
         assertEquals(expectedRows, rows(stmt, sql.getSQLString()).size(), text);
      }
   }

   // plain GROUP BY lists, including parenthesized single columns and "()", still parse,
   // are recorded, regenerate to themselves and return the same rows
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select a, count(*) from t group by a|select a, count(*) from t group by a|a",
      "select a, b, count(*) from t group by a, b|select a, b, count(*) from t group by a, b|a,b",
      "select a, count(*) from t group by all a|select a, count(*) from t group by all a|a",
      "select a, count(*) from t group by (a)|select a, count(*) from t group by (a)|(a)",
      "select a, b, count(*) from t group by (a), b|select a, b, count(*) from t group by (a), b|(a),b",
      "select a, b, count(*) from t group by a, (b)|select a, b, count(*) from t group by a, (b)|a,(b)",
      "select a+1, b, count(*) from t group by (a+1), b|" +
         "select a+1, b, count(*) from t group by (a+1), b|(a+1),b",
      "select t.a, max(t.b) from t where t.b > 0 group by t.a having count(*) > 1 order by t.a|" +
         "select max(t.b), t.a from t where t.b > 0 group by t.a having count(*) > 1 order by t.a asc|t.a",
      "select count(*) from t group by ()|select count(*) from t|",
   })
   void plainGroupByParses(String text, String expected, String groupBy) throws Exception {
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      assertEquals(groupBy == null ? List.of() : List.of(groupBy.split(",")),
                   Arrays.stream(sql.getGroupBy()).map(Object::toString).toList(), text);

      sql.clearSQLString();
      String generated = normalize(sql.getSQLString());
      assertEquals(expected, generated);

      // round trip
      UniformSQL reparsed = parse(generated);
      reparsed.clearSQLString();
      assertEquals(generated, normalize(reparsed.getSQLString()), "round trip");

      try(Connection conn = hsqldb(); Statement stmt = conn.createStatement()) {
         assertEquals(sorted(rows(stmt, text)), sorted(rows(stmt, generated)), text + " -> " + generated);
      }
   }

   // a plain GROUP BY on an ansi-join and a quoting data source
   @ParameterizedTest
   @ValueSource(strings = { "h2-ansi", "postgresql", "oracle" })
   void plainGroupByParsesOnDataSources(String type) throws Exception {
      String text = "select t.a, count(*) from t left join u on t.a = u.x group by t.a";
      UniformSQL sql = parse(text, dataSource(type));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.clearSQLString();
      String generated = normalize(sql.getSQLString());
      // postgresql stores the column quoted ("t"."a")
      assertEquals(1, sql.getGroupBy().length, type);
      assertEquals("t.a", sql.getGroupBy()[0].toString().replace("\"", ""), type);
      assertTrue(generated.contains(" group by "), type + ": " + generated);

      // only the GROUP BY is compared: an unaliased table on postgresql doesn't round trip
      // its FROM, which is unrelated to grouping
      UniformSQL reparsed = parse(generated, dataSource(type));
      assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), generated);
      assertFalse(reparsed.isLossy(), generated);
      reparsed.clearSQLString();
      assertEquals(groupBy(generated), groupBy(normalize(reparsed.getSQLString())), type + " round trip");
   }

   // the GROUP BY StyleBI writes for a query built in the editor must still parse
   @ParameterizedTest
   @ValueSource(strings = { "default", "h2", "h2-ansi", "postgresql", "oracle" })
   void generatedGroupByReparses(String type) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.addTable("t");
      sql.addTable("u");
      sql.getSelection().addColumn("t.a");
      sql.getSelection().addColumn("count(*)");
      sql.addJoin(new XJoin(new XExpression("t.a", XExpression.FIELD),
                            new XExpression("u.x", XExpression.FIELD), "*="));
      sql.setGroupBy(new Object[] { "t.a", "t.b" });
      sql.setDataSource("default".equals(type) ? null : dataSource(type));
      sql.clearSQLString();
      String generated = normalize(sql.getSQLString());
      assertTrue(generated.contains("group by "), generated);

      UniformSQL reparsed = parse(generated, sql.getDataSource());
      assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), type + ": " + generated);
      assertFalse(reparsed.isLossy(), generated);
      assertEquals(2, reparsed.getGroupBy().length, generated);
      reparsed.clearSQLString();
      assertEquals(groupBy(generated), groupBy(normalize(reparsed.getSQLString())), type + " round trip");
   }

   private static String groupBy(String sql) {
      return sql.substring(sql.indexOf(" group by "));
   }

   /**
    * A SQL expression (a calc field, a VPM condition) that holds a ROLLUP/CUBE/GROUPING SETS
    * scalar subquery is now invalid, since the subquery can't be parsed. It was valid while
    * the subquery's GROUP BY was dropped.
    */
   @Test
   void scalarSubqueryExpressionIsInvalid() {
      assertFalse(XUtil.isSQLExpressionValid("(select max(a) from t group by rollup(a))"));
      assertFalse(XUtil.isSQLExpressionValid("(select max(a) from t group by cube(a), b)"));
      assertTrue(XUtil.isSQLExpressionValid("(select max(a) from t group by a)"));
      assertTrue(XUtil.isSQLExpressionValid("(select max(a) from t group by ())"));
   }

   @AfterAll
   static void dropHsqldb() throws SQLException {
      try(Connection conn = DriverManager.getConnection(HSQLDB_URL);
          Statement stmt = conn.createStatement())
      {
         stmt.execute("shutdown");
      }
   }

   // t = (1,1),(1,2),(2,1),(null,3), u = (1),(2),(4)
   private static Connection hsqldb() throws SQLException {
      Connection conn = DriverManager.getConnection(HSQLDB_URL);

      try(Statement stmt = conn.createStatement()) {
         stmt.execute("drop table t if exists");
         stmt.execute("drop table u if exists");
         stmt.execute("create table t (a int, b int)");
         stmt.execute("create table u (x int)");
         stmt.execute("insert into t values (1, 1), (1, 2), (2, 1), (null, 3)");
         stmt.execute("insert into u values (1), (2), (4)");
      }

      return conn;
   }

   private static List<List<Object>> rows(Statement stmt, String query) throws SQLException {
      List<List<Object>> rows = new ArrayList<>();

      try(ResultSet rs = stmt.executeQuery(query)) {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            List<Object> row = new ArrayList<>();

            for(int i = 1; i <= count; i++) {
               row.add(rs.getObject(i));
            }

            rows.add(row);
         }
      }

      return rows;
   }

   // the regenerated select list may be reordered, so compare each row's values as a multiset
   private static List<String> sorted(List<List<Object>> rows) {
      return rows.stream()
         .map(row -> row.stream().map(String::valueOf).sorted().toList().toString())
         .sorted()
         .toList();
   }

   private static JDBCQuery query(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      sql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      return query;
   }

   private static JDBCDataSource dataSource(String type) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds_" + type);
      ds.setProductVersion("19.0");

      switch(type.replace("-ansi", "")) {
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
      default -> throw new IllegalArgumentException(type);
      }

      ds.setAnsiJoin(type.endsWith("-ansi"));
      String helper = SQLHelper.getSQLHelper(ds).getSQLHelperType();
      assertEquals(type.replace("-ansi", ""), helper, "helper for " + type);
      return ds;
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text) throws Exception {
      return parse(text, null);
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }

   private static UniformSQL parseAsync(String text) throws Exception {
      UniformSQL sql = new UniformSQL();

      synchronized(sql) {
         sql.setSQLString(text);
         sql.wait(20000);
      }

      return sql;
   }
}
