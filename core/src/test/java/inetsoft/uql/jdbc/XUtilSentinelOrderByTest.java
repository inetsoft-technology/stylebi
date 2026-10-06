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

import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.util.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77706 (D4), a parameter holding NULL_VALUE, EMPTY_STRING or NULL_STRING in a scalar
 * subquery of the order by list was bound as the text of the sentinel. The parser stores such
 * a subquery as the text of the order by item, and XUtil.validateConditions didn't look at the
 * order by list.
 * <p>
 * A parameter left in the sql is bound here the way VarSQL binds it, as the text of its
 * value, so the row order shows a missed rewrite. Table b has a row holding the text
 * NULL_VALUE, one holding '' and one holding 'null', all for a.id 5.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  XUtilSentinelOrderByTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUtilSentinelOrderByTest {
   private static final String DB = "memory:bug77706orderby";
   private static final String NULL_VALUE = XConstants.CONDITION_NULL_VALUE;
   private static final String EMPTY_STRING = XConstants.CONDITION_EMPTY_STRING;
   private static final String NULL_STRING = XConstants.CONDITION_NULL_STRING;
   // the number of rows of b of an a.id with k = p
   private static final String SUB =
      "(select count(*) from b where b.id = a.id and b.k = $(p))";
   private static final String D4A = "select a.id from a order by " + SUB + " desc, a.id";
   // SQLHelper, DerbyHelper, OracleSQLHelper, PostgreSQLHelper and H2Helper
   private static final String[] TYPES = { "default", "derby", "derby-ansi", "oracle",
                                           "oracle-ansi", "postgresql", "h2" };
   // the helpers whose sql runs on Derby
   private static final String[] DERBY_TYPES = { "default", "derby", "derby-ansi" };

   @Configuration
   static class JdbcConfig {
      // JDBCDataSource's constructor needs CredentialService, whose constructor is
      // package-private
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster,
                            eventPublisher);
      }

      // the pool returns a Derby data source directly
      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         DataSource ds = derby();
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenReturn(ds);
         return factory;
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory) {
         return new Drivers(plugins, connectionPoolFactory);
      }

      @Bean
      public inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      // the derby helper asks the repository for the database version
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @BeforeEach
   void createTables() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "a", "b", "q" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(SQLException ignore) {
               // first run
            }
         }

         stmt.executeUpdate("create table a (id int)");
         stmt.executeUpdate("insert into a values (1), (3), (5)");
         // k is VARCHAR: a sentinel bound as text against a numeric column is a type error
         stmt.executeUpdate("create table b (id int, k varchar(20))");
         stmt.executeUpdate("insert into b values (1, '2'), (1, '2'), (3, '2'), (3, '3'), " +
                               "(1, null), (3, null), (5, ''), (5, 'null'), " +
                               "(5, 'NULL_VALUE'), (7, null)");
         // quoted mixed case names, which a case-folding database finds only quoted
         stmt.executeUpdate("create table q (\"Id\" int, \"MixedK\" varchar(20))");
         stmt.executeUpdate("insert into q values (1, null), (3, null), (3, null), " +
                               "(5, ''), (5, 'NULL_VALUE'), (5, 'NULL_VALUE')");
      }
   }

   // the reported shape (diagnosis D4a) through XSessionManager and JDBCHandler on Derby.
   // With the sentinel bound as text the order was 5, 1, 3 for NULL_VALUE.
   @ParameterizedTest
   @MethodSource("d4aCases")
   void reportedShapeThroughJDBCHandler(String p, List<String> expected) throws Exception {
      VariableTable vars = new VariableTable();
      vars.put("p", p);
      assertEquals(expected, ordered(run(parsed(D4A), vars), 1), "p=" + p);
   }

   static Stream<Arguments> d4aCases() {
      return Stream.of(
         // rows of b with a null k: 1 -> 1, 3 -> 1, 5 -> 0
         Arguments.of(NULL_VALUE, List.of("1", "3", "5")),
         // rows of b with k '': 5 -> 1
         Arguments.of(EMPTY_STRING, List.of("5", "1", "3")),
         // rows of b with k 'null': 5 -> 1
         Arguments.of(NULL_STRING, List.of("5", "1", "3")),
         // a plain value: 3 -> 1
         Arguments.of("3", List.of("3", "1", "5")));
   }

   // a positional order by keeps the parsed query's sql string past the cache normalizer,
   // so the select-list subquery is rewritten by JDBCHandler's kept-string gate (#77708)
   @ParameterizedTest
   @MethodSource("positionalCases")
   void positionalOrderByThroughJDBCHandler(String p, List<String> expected) throws Exception {
      VariableTable vars = new VariableTable();
      vars.put("p", p);
      String sql = "select a.id, " + SUB + " from a order by 2 desc, 1";
      assertEquals(expected, ordered(run(parsed(sql), vars), 1), "p=" + p);
   }

   static Stream<Arguments> positionalCases() {
      return Stream.of(
         Arguments.of(NULL_VALUE, List.of("1", "3", "5")),
         Arguments.of(EMPTY_STRING, List.of("5", "1", "3")),
         Arguments.of(NULL_STRING, List.of("5", "1", "3")),
         Arguments.of("3", List.of("3", "1", "5")));
   }

   // the same saved query run with a sentinel, a plain value, the sentinel again and another
   // sentinel: no run reuses the rewrite of another, and the saved query is unchanged
   @Test
   void runSequenceThroughJDBCHandler() throws Exception {
      UniformSQL usql = parsed(D4A);
      Object[][] runs = {
         { NULL_VALUE, List.of("1", "3", "5") },
         { "3", List.of("3", "1", "5") },
         { NULL_VALUE, List.of("1", "3", "5") },
         { EMPTY_STRING, List.of("5", "1", "3") },
         { "3", List.of("3", "1", "5") },
      };

      for(Object[] run : runs) {
         VariableTable vars = new VariableTable();
         vars.put("p", run[0]);
         assertEquals(run[1], ordered(run(usql, vars), 1), "p=" + run[0]);
         // the saved query may get its sql string regenerated, never with the rewrite
         String now = usql.getSQLString();
         assertTrue(now.contains("$(p)"), now);
         assertFalse(now.toUpperCase().contains("IS NULL"), now);
         assertNoOrderBySQL(usql);
      }
   }

   // order by the subquery with other select columns: the result headers (named by the
   // column text) and the column order restored by the sorted column map are the same
   // whatever the parameter, and the rows follow the rewritten order by
   @Test
   void headersAndSortMapThroughJDBCHandler() throws Exception {
      // the select columns aren't in sorted order, so the sorted column map moves them
      String sql = "select (select count(*) from b where b.k = '2'), a.id, " + SUB +
         " from a order by " + SUB + " desc, a.id";
      UniformSQL usql = parsed(sql);
      List<String> columns = columns(usql);
      Object[] orderBy = usql.getOrderByFields().clone();
      String[][] runs = {
         { NULL_VALUE, "3|1|1", "3|3|1", "3|5|0" },
         { EMPTY_STRING, "3|5|1", "3|1|0", "3|3|0" },
         { "3", "3|3|1", "3|1|0", "3|5|0" },
         { NULL_VALUE, "3|1|1", "3|3|1", "3|5|0" },
      };

      for(String[] run : runs) {
         VariableTable vars = new VariableTable();
         vars.put("p", run[0]);
         TableLens table = run(usql, vars);
         assertEquals(3, table.getColCount());

         for(int c = 0; c < 3; c++) {
            String header = String.valueOf(table.getObject(0, c));
            assertTrue(header.equalsIgnoreCase(columns.get(c)) ||
                          c == 1 && header.equalsIgnoreCase("id"),
                       "header " + c + ": " + header + " expected " + columns.get(c));
         }

         assertEquals(List.of(run[1], run[2], run[3]), ordered(table, 3), "p=" + run[0]);
         // the saved order by items keep their text, which the sorted column map reads
         assertArrayEquals(orderBy, usql.getOrderByFields());
      }
   }

   // sql with $(p) in the order by list (and maybe the select list), checked on every helper
   static Stream<Arguments> generationCases() {
      return Stream.of(
         // the reported shape
         Arguments.of(D4A),
         // not correlated
         Arguments.of("select a.id from a order by (select count(*) from b where " +
                         "b.k = $(p)) desc, a.id"),
         // the same subquery in the select list, unaliased and aliased
         Arguments.of("select a.id, " + SUB + " from a order by " + SUB + " desc, a.id"),
         Arguments.of("select a.id, " + SUB + " as cnt from a order by " + SUB +
                         " desc, a.id"),
         // ordered by the alias of the subquery: oracle sorts an alias by its select column
         // (!supportsAliasSorting), which is generated with the rewritten sql of the column
         Arguments.of("select a.id, " + SUB + " as cnt from a order by cnt desc, a.id"),
         Arguments.of("select distinct a.id, " + SUB + " as cnt from a order by cnt desc"),
         // a positional item next to the subquery item
         Arguments.of("select a.id from a order by " + SUB + " desc, 1"),
         Arguments.of("select a.id, " + SUB + " from a order by 2 desc, " + SUB + ", 1"),
         // distinct: derby sorts a select column by its alias (!supportsFieldSorting)
         Arguments.of("select distinct a.id, " + SUB + " as cnt from a order by " + SUB +
                         " desc, a.id"),
         Arguments.of("select distinct a.id, " + SUB + " from a order by " + SUB +
                         " desc, a.id"),
         // quoted mixed case names, kept quoted (postgresql stores the text quoted)
         Arguments.of("select q.\"Id\" from q order by (select count(*) from q q2 where " +
                         "q2.\"Id\" = q.\"Id\" and q2.\"MixedK\" = $(p)) desc, q.\"Id\""));
   }

   // Derby refuses a distinct query ordered by a subquery that isn't a select alias (42879),
   // also with the sentinel written in the sql
   static Stream<Arguments> derbyCases() {
      return generationCases().filter(args -> {
         String sql = (String) args.get()[0];
         return !sql.startsWith("select distinct") || sql.contains(" as cnt ");
      });
   }

   // the order by sql is rewritten on every helper as if the sentinel was written in the sql:
   // no $(p) is left, and nothing else of the generated sql changes
   @ParameterizedTest
   @MethodSource("generationCases")
   void rewrittenOnEveryHelper(String sql) throws Exception {
      for(String type : TYPES) {
         String original = generate(parse(sql, type));
         assertTrue(original.contains("$(p)"), type + " " + original);

         String generated = validate(parse(sql, type), NULL_VALUE);
         assertFalse(generated.contains("$(p)"), type + " not rewritten: " + generated);
         assertEquals(original.replace("= $(p)", "IS NULL"), generated, type);

         generated = validate(parse(sql, type), EMPTY_STRING);
         assertEquals(original.replace("= $(p)", "= ''"), generated, type);
      }
   }

   // the rewritten sql of the helpers that run on Derby returns the rows in the order of the
   // sql with the sentinel written in it
   @ParameterizedTest
   @MethodSource("derbyCases")
   void sameOrderOnDerby(String sql) throws Exception {
      try(Connection conn = derby().getConnection()) {
         for(String type : DERBY_TYPES) {
            for(String[] p : new String[][] { { NULL_VALUE, "is null" },
                                              { EMPTY_STRING, "= ''" },
                                              { NULL_STRING, "= 'null'" } })
            {
               List<String> expected =
                  rows(conn, generate(parse(sql.replace("= $(p)", p[1]), type)));
               String generated = validate(parse(sql, type), p[0]);
               assertEquals(expected, rows(conn, bind(generated, p[0])),
                            type + " p=" + p[0] + "\ngenerated: " + generated);
            }
         }
      }
   }

   // postgresql stores the subquery text with quoted names: the stored item is replaced
   @Test
   void postgresqlQuotedText() throws Exception {
      String sql = "select q.\"Id\" from q order by (select count(*) from q q2 where " +
         "q2.\"Id\" = q.\"Id\" and q2.\"MixedK\" = $(p)) desc, q.\"Id\"";
      UniformSQL usql = parse(sql, "postgresql");
      String item = (String) usql.getOrderByFields()[0];
      assertTrue(item.contains("q2.\"MixedK\" = $(p)"), item);

      String generated = validate(usql, NULL_VALUE);
      String orderBy = generated.substring(generated.toLowerCase().indexOf(" order by "));
      assertFalse(orderBy.contains("$(p)"), orderBy);
      assertTrue(orderBy.contains("q2.\"MixedK\" IS NULL"), orderBy);
      String override = usql.getOrderBySQL(0).replaceAll("\\s+", " ");
      assertTrue(override.contains("q2.\"MixedK\" IS NULL"), override);
   }

   // embedded derby sorts a distinct query by the alias of the select column
   // (!supportsFieldSorting): the alias is generated, and the select column is rewritten
   @Test
   void derbyFieldSortingAlias() throws Exception {
      String sql = "select distinct a.id, " + SUB + " as cnt from a order by " + SUB +
         " desc, a.id";
      UniformSQL usql = parse(sql, "derby");
      assertFalse(SQLHelper.getSQLHelper(usql).supportsFieldSorting());

      String generated = validate(usql, NULL_VALUE);
      String lower = generated.toLowerCase();
      assertFalse(generated.contains("$(p)"), generated);
      assertTrue(lower.contains(" order by cnt desc"), generated);
      assertTrue(lower.contains("b.k is null ) as cnt"), generated);

      try(Connection conn = derby().getConnection()) {
         // cnt|id: a.id 1 and 3 have a b row with a null k, 5 has none
         assertEquals(List.of("1|1|", "1|3|", "0|5|"), rows(conn, generated), generated);
      }
   }

   // access sorts by the select column of an alias (AccessSQLHelper.getOrderByColumn), which
   // is generated with the rewritten sql of the column
   @Test
   void accessHelper() throws Exception {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds_access");
      ds.setDriver("net.ucanaccess.jdbc.UcanaccessDriver");
      ds.setURL("jdbc:ucanaccess://c:/x.accdb");
      assertEquals("access", SQLHelper.getSQLHelper(ds).getSQLHelperType());
      String[] sqls = {
         D4A,
         "select a.id, " + SUB + " as cnt from a order by cnt desc, a.id",
         "select a.id, " + SUB + " as cnt from a order by " + SUB + " desc, a.id",
      };

      for(String sql : sqls) {
         for(String[] p : new String[][] { { NULL_VALUE, "IS NULL" }, { EMPTY_STRING, "= ''" },
                                           { NULL_STRING, "= 'null'" } })
         {
            UniformSQL original = new UniformSQL();
            original.setDataSource(ds);
            original.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
            assertEquals(UniformSQL.PARSE_SUCCESS, original.getParseResult(), sql);
            String text = generate(original);
            assertTrue(text.contains("$(p)"), text);

            UniformSQL usql = new UniformSQL();
            usql.setDataSource(ds);
            usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
            usql.clearSQLString();
            String generated = validate(usql, p[0]);
            assertFalse(generated.contains("$(p)"), "not rewritten: " + generated);
            assertEquals(text.replace("= $(p)", p[1]), generated, sql);
         }
      }
   }

   // the select list and the order by list copy of a subquery get the same sql
   @Test
   void selectListAndOrderByCopiesIdentical() throws Exception {
      String sql = "select a.id, " + SUB + " from a order by " + SUB + " desc";

      for(String type : TYPES) {
         UniformSQL usql = parse(sql, type);
         validate(usql, NULL_VALUE);
         JDBCSelection selection = (JDBCSelection) usql.getSelection();
         int idx = selection.getColumn(0).startsWith("(") ? 0 : 1;
         String column = selection.getColumnSQL(idx);
         assertNotNull(column, type);
         assertTrue(column.contains("IS NULL"), column);
         assertEquals(column, usql.getOrderBySQL(0), type);
      }
   }

   // Bug #77706 (product decision): the subqueries of generationCases are count(*) without
   // group by, which return one row anyway, so the condition of an unset parameter is removed
   // from them, in the order by list as in the select list. Before, it was left as it is
   // (bound as null).
   @ParameterizedTest
   @MethodSource("generationCases")
   void unsetSingleRowConditionRemoved(String sql) throws Exception {
      String removed = removeP(sql);
      assertFalse(removed.contains("$(p)"), removed);

      for(String type : TYPES) {
         UniformSQL usql = parse(sql, type);
         Object[] orderBy = usql.getOrderByFields().clone();
         XUtil.validateConditions(null, usql, new VariableTable(), true, false);
         // the order by items keep their text (checked before the sql is generated, which
         // may change the items of a distinct query on oracle)
         assertArrayEquals(orderBy, usql.getOrderByFields(), type);
         String generated = generate(usql);
         assertFalse(generated.contains("$(p)"), type + " not removed: " + generated);
         assertEquals(generate(parse(removed, type)), generated, type);
      }
   }

   // the order by subquery that may return more than one row keeps the condition of an unset
   // parameter (bound as null), as in the select list
   @Test
   void unsetNotSingleRowUnchanged() throws Exception {
      String[] sqls = {
         "select a.id from a order by (select count(*) from b where b.id = a.id and " +
            "b.k = $(p) group by b.k) desc, a.id",
         "select a.id from a order by (select count(*) + 1 from b where b.id = a.id and " +
            "b.k = $(p)) desc, a.id",
         "select a.id, (select max(b.id) from b where b.id = a.id and b.k = $(p) " +
            "having max(b.id) > 1) from a order by (select max(b.id) from b where " +
            "b.id = a.id and b.k = $(p) having max(b.id) > 1) desc, a.id"
      };

      for(String sql : sqls) {
         for(String type : TYPES) {
            String original = generate(parse(sql, type));
            UniformSQL usql = parse(sql, type);
            XUtil.validateConditions(null, usql, new VariableTable(), true, false);
            assertEquals(original, generate(usql), type);

            // the item is reached, a sentinel is rewritten
            String generated = validate(parse(sql, type), NULL_VALUE);
            assertFalse(generated.contains("$(p)"), type + " not rewritten: " + generated);
         }
      }
   }

   // the select list and the order by list copy of a subquery get the same sql with an unset
   // parameter too
   @Test
   void unsetSelectListAndOrderByCopiesIdentical() throws Exception {
      String sql = "select a.id, " + SUB + " from a order by " + SUB + " desc";

      for(String type : TYPES) {
         UniformSQL usql = parse(sql, type);
         XUtil.validateConditions(null, usql, new VariableTable(), true, false);
         JDBCSelection selection = (JDBCSelection) usql.getSelection();
         int idx = selection.getColumn(0).startsWith("(") ? 0 : 1;
         String column = selection.getColumnSQL(idx);
         assertNotNull(column, type);
         assertFalse(column.contains("$(p)"), column);
         assertEquals(column, usql.getOrderBySQL(0), type);
      }
   }

   // through JDBCHandler, the subquery as a select column and an order by item, unset: both
   // copies count all rows of b up to the id (3, 6, 9). Were they different, the order and
   // the values wouldn't match: with the condition bound as null all are 0, in a.id order.
   @Test
   void unsetSelectListAndOrderByThroughJDBCHandler() throws Exception {
      String sub = "(select count(*) from b where b.id <= a.id and b.k = $(p))";
      String[] sqls = {
         "select a.id, " + sub + " from a order by " + sub + " desc, a.id",
         "select a.id, " + sub + " as cnt from a order by " + sub + " desc, a.id",
         "select a.id, " + sub + " as cnt from a order by cnt desc, a.id",
         "select a.id from a order by " + sub + " desc, a.id"
      };

      for(String sql : sqls) {
         UniformSQL usql = parsed(sql);
         Object[][] runs = {
            { null, List.of("5|9", "3|6", "1|3") },
            { NULL_VALUE, List.of("3|2", "5|2", "1|1") },
            { null, List.of("5|9", "3|6", "1|3") },
            { "2", List.of("3|3", "5|3", "1|2") },
         };

         for(Object[] run : runs) {
            VariableTable vars = new VariableTable();

            if(run[0] != null) {
               vars.put("p", run[0]);
            }

            TableLens table = run(usql, vars);
            List<String> rows = ordered(table, table.getColCount() > 1 ? 2 : 1);
            List<String> expected = (List<String>) run[1];

            if(table.getColCount() == 1) {
               expected = expected.stream().map(r -> r.substring(0, r.indexOf('|'))).toList();
            }

            assertEquals(expected, rows, sql + " p=" + run[0]);
            assertNoOrderBySQL(usql);
         }
      }
   }

   // the sql without the condition of p, as generationCases write it
   private static String removeP(String sql) {
      return sql.replace(" and b.k = $(p)", "")
         .replace(" where b.k = $(p))", ")")
         .replace(" and q2.\"MixedK\" = $(p)", "");
   }

   // a value that isn't a sentinel is left as it is
   @Test
   void plainValueUnchanged() throws Exception {
      for(String type : TYPES) {
         UniformSQL usql = parse(D4A, type);
         assertEquals(generate(parse(D4A, type)), validate(usql, "2"), type);
         assertNoOrderBySQL(usql);
      }
   }

   // the same query object validated with a sentinel and then with a plain value
   @Test
   void sameObjectRunTwice() throws Exception {
      UniformSQL usql = parse(D4A, "derby");
      String plain = generate(parse(D4A, "derby"));

      assertFalse(validate(usql, NULL_VALUE).contains("$(p)"));
      assertEquals(plain, validate(usql, "3"));
      assertFalse(validate(usql, EMPTY_STRING).contains("$(p)"));
   }

   // the order by subquery is rewritten on the vpm path too, which only rewrites sentinels
   @Test
   void vpmPath() throws Exception {
      UniformSQL usql = parse(D4A, "derby");
      VariableTable vars = new VariableTable();
      vars.put("p", NULL_VALUE);
      XUtil.validateConditions(null, usql, vars, true, true);
      assertEquals(generate(parse(D4A, "derby")).replace("= $(p)", "IS NULL"),
                   generate(usql));
   }

   // a copy of the validated query generates the rewritten sql, the saved query doesn't keep it
   @Test
   void cloneCarriesRewriteXmlDoesNot() throws Exception {
      UniformSQL usql = parse(D4A, "derby");
      String rewritten = validate(usql, NULL_VALUE);
      assertFalse(rewritten.contains("$(p)"), rewritten);
      assertEquals(rewritten, generate(usql.clone()));

      java.io.StringWriter buf = new java.io.StringWriter();
      java.io.PrintWriter writer = new java.io.PrintWriter(buf);
      usql.writeXML(writer);
      writer.flush();
      assertTrue(buf.toString().contains("$(p)"), buf.toString());
      assertFalse(buf.toString().contains("IS NULL"), buf.toString());
   }

   // the item is changed, moved or the list cleared: the rewritten sql isn't used
   @Test
   void rewriteFollowsItem() throws Exception {
      UniformSQL usql = parse(D4A, "default");
      validate(usql, NULL_VALUE);
      Object field = usql.getOrderByFields()[0];
      String rewritten = usql.getOrderBySQL(0);
      assertNotNull(rewritten);
      assertTrue(rewritten.contains("IS NULL"), rewritten);

      // another item at the index
      usql.insertOrderBy(0, "a.id", "asc");
      assertNull(usql.getOrderBySQL(0));
      assertNull(usql.getOrderBySQL(1));
      assertTrue(generate(usql).contains("$(p)"));

      usql.removeAllOrderByFields();
      usql.setOrderBy(field, "desc");
      assertNull(usql.getOrderBySQL(0));
      assertTrue(generate(usql).contains("$(p)"));
   }

   // a query that keeps its sql string isn't validated at all, as before
   @Test
   void keptSqlStringUnchanged() throws Exception {
      UniformSQL usql = parse(D4A, "derby");
      usql.setSQLString(D4A, false);
      VariableTable vars = new VariableTable();
      vars.put("p", NULL_VALUE);
      XUtil.validateConditions(null, usql, vars, true, false);
      assertEquals(D4A, usql.getSQLString());
      assertNoOrderBySQL(usql);
   }

   // text that isn't one subquery as the parser generates it is left as it is (a follow-up)
   @Test
   void expressionUnchanged() throws Exception {
      String sql = "select a.id from a order by " + SUB + " + 1 desc, a.id";

      for(String type : TYPES) {
         assertEquals(generate(parse(sql, type)), validate(parse(sql, type), NULL_VALUE), type);
      }
   }

   // an order by subquery of a derived table is rewritten too
   @Test
   void derivedTable() throws Exception {
      String sql = "select d.id from (select a.id from a order by " + SUB + " desc) d";

      for(String type : TYPES) {
         String original = generate(parse(sql, type));
         assertEquals(original.replace("= $(p)", "IS NULL"),
                      validate(parse(sql, type), NULL_VALUE), type);
      }
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection("jdbc:derby:" + DB + ";drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   private static void assertNoOrderBySQL(UniformSQL usql) {
      Object[] fields = usql.getOrderByFields();

      for(int i = 0; fields != null && i < fields.length; i++) {
         assertNull(usql.getOrderBySQL(i), String.valueOf(fields[i]));
      }
   }

   // the rows in the order of the result
   private static List<String> rows(Connection conn, String sql) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            StringBuilder row = new StringBuilder();

            for(int i = 1; i <= count; i++) {
               row.append(rs.getString(i)).append('|');
            }

            rows.add(row.toString());
         }
      }

      return rows;
   }

   // the rows of a table lens in their order, the first count columns joined by |
   private static List<String> ordered(TableLens table, int count) {
      List<String> rows = new ArrayList<>();

      for(int r = 1; table.moreRows(r); r++) {
         StringBuilder row = new StringBuilder();

         for(int c = 0; c < count; c++) {
            row.append(c > 0 ? "|" : "").append(table.getObject(r, c));
         }

         rows.add(row.toString());
      }

      return rows;
   }

   // a parameter left in the sql is bound by VarSQL as its value, as text here
   private static String bind(String sql, String p) {
      return sql.replace("$(p)", "'" + p + "'");
   }

   private static List<String> columns(UniformSQL usql) {
      List<String> columns = new ArrayList<>();
      XSelection selection = usql.getSelection();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         columns.add(selection.getColumn(i));
      }

      return columns;
   }

   private static String validate(UniformSQL usql, String p) {
      VariableTable vars = new VariableTable();
      vars.put("p", p);
      XUtil.validateConditions(null, usql, vars, true, false);
      return generate(usql);
   }

   private static String generate(UniformSQL usql) {
      usql.clearSQLString();
      return usql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   // a new query for every case and run, the rewrite changes the parsed query in place
   private static UniformSQL parse(String text, String type) throws Exception {
      UniformSQL sql = new UniformSQL();

      if(!"default".equals(type)) {
         sql.setDataSource(SQLHelperNotEqualJoinTest.RowCompare.dataSource(type));
      }

      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.clearSQLString();
      return sql;
   }

   // a query of a data source as it's saved: parsed, with its sql string
   private static UniformSQL parsed(String sql) throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(dataSource());
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult());
      return usql;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77706orderby");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   // run a query through XSessionManager and JDBCHandler, with the cache normalizer
   private static TableLens run(UniformSQL usql, VariableTable vars) throws Exception {
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77706orderby");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);

      XDataService dataService = mock(XDataService.class);
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> {
            XQuery q = inv.getArgument(1);
            JDBCHandler handler = new JDBCHandler();
            handler.connect(q.getDataSource(), inv.getArgument(2));
            return handler.execute(q, inv.getArgument(2), inv.getArgument(3),
                                   inv.getArgument(5));
         });
      XSessionManager session = new XSessionManager(
         dataService, ConfigurationContext.getContext().getSpringBean(XSessionService.class),
         null);
      session.setCacheData(false);

      try {
         TableLens table = session.getXNodeTableLens(query, vars, null, null, null, -1);
         assertNotNull(table, "query failed, see log");
         table.moreRows(Integer.MAX_VALUE);
         return table;
      }
      finally {
         session.tearDown();
      }
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
