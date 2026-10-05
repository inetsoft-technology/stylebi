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
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77556, a condition of an inner join ON that isn't a column-to-column join (an
 * expression or function operand, a literal, IS NULL, IN, BETWEEN, NOT, an OR'd ON) is parsed
 * as a plain condition and ANDed into WHERE without a join clause, and SQLHelper writes it in
 * WHERE. On a table that a later RIGHT or FULL join, or the outer join of a nested operand,
 * null-extends, WHERE removes the null-extended rows the ON kept. Since the join order check
 * of Bug #77434 such a query fails the parse, so the original sql runs, and a query saved
 * before the check is lossy once its data source is set (Bug #77488). Where no later join
 * null-extends the table, WHERE returns the same rows and the query is still accepted.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLOnNonJoinConditionRefuseTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class UniformSQLOnNonJoinConditionRefuseTest {
   // a data source with a real driver and URL needs the credential service, and the JDBC
   // driver types of Config to pick its SQL helper
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      // the derby helper asks the repository for the database version
      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   private static final String SEL = "select a.id ai, a.k ak, b.id bi, b.k bk, c.id ci, c.k ck ";
   private static final String SEL4 =
      "select a.id ai, a.k ak, b.id bi, b.k bk, c.id ci, c.k ck, d.id di, d.k dk ";
   private static final String[] TYPES = {
      "h2", "h2-ansi", "derby", "derby-ansi", "postgresql-ansi", "oracle", "oracle-ansi", "mongo"
   };

   // the non-join conditions of the inner ON of a and c, every op and both operand sides
   private static final String[] CONDITIONS = {
      "a.k + 1 != c.k", "a.k + 1 <> c.k", "a.k + 1 = c.k", "a.k + 1 < c.k", "a.k + 1 <= c.k",
      "a.k + 1 > c.k", "a.k + 1 >= c.k", "a.k != c.k + 0", "c.k + 0 = a.k", "abs(a.k) = c.k",
      "c.k = 1", "1 = c.k", "c.k is null", "c.k is not null", "c.k in (1, 2)",
      "c.k between 0 and 1", "not (a.k + 1 = c.k)", "(c.k = 1 or a.k = 2)"
   };

   // the positions where a later join null-extends a and c, with the refusal message
   private static final String[][] POSITIONS = {
      { "from a join c on %s right join b on a.id = b.id", "Unsupported RIGHT or FULL join" },
      { "from a join c on %s full join b on a.id = b.id", "Unsupported RIGHT or FULL join" },
      { "from b left join (a join c on %s) on b.id = a.id", "Unsupported nested join" },
      { "from b left join a join c on %s on b.id = a.id", "Unsupported nested join" },
      { "from b right join (a join c on %s) on b.id = a.id", "Unsupported nested join" },
   };

   static Stream<String[]> refusedShapes() {
      List<String[]> shapes = new ArrayList<>();

      for(String[] position : POSITIONS) {
         for(String cond : CONDITIONS) {
            shapes.add(new String[] {
               SEL + String.format(position[0], "a.id = c.id and " + cond), position[1] });
         }

         // the whole ON is an OR, which also leaves no join between a and c
         shapes.add(new String[] {
            SEL + String.format(position[0], "(a.id = c.id and a.k = c.k) or c.j = 1"),
            position[1] });
      }

      // the join order check runs for every query level
      String right = "from a join c on a.id = c.id and a.k + 1 != c.k right join b on a.id = b.id";
      shapes.add(new String[] { "select x.ai from (" + SEL + right + ") x",
                                "Unsupported RIGHT or FULL join" });
      shapes.add(new String[] { "select d.id from d where exists (select 1 " + right +
                                   " where b.id = d.id)", "Unsupported RIGHT or FULL join" });
      shapes.add(new String[] { "select d.id from d where d.id in (select b.id " + right + ")",
                                "Unsupported RIGHT or FULL join" });
      return shapes.stream();
   }

   // the parse fails on every helper and the original sql is kept, so it runs as written and
   // isn't merged. A fresh object with the sql string is lossy, and without a data source the
   // parse fails too and lossy isn't decided until the data source is set
   @ParameterizedTest
   @MethodSource("refusedShapes")
   void nonJoinOnConditionUnderNullExtendingJoinIsRefused(String text, String message)
      throws Exception
   {
      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         RecognitionException ex = assertThrows(RecognitionException.class,
                                                () -> parse(text, ds), type + ": " + text);
         assertTrue(ex.getMessage().contains(message), type + ": " + ex.getMessage());

         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         new SQLProcessor(sql).parse(text);
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type + ": " + text);
         assertEquals(text, sql.getSQLString(), type);
         assertFalse(XUtil.isParsedSQL(sql), type);
         assertTrue(sql.isLossy(), type + ": " + text);
         assertFalse(XUtil.isQueryMergeable(query(sql, ds)), type + ": " + text);
         assertEquals(text, sql.getSQLString(), type);

         UniformSQL fresh = new UniformSQL();
         fresh.setDataSource(ds);
         fresh.setSQLString(text, false);
         assertTrue(fresh.isLossy(), type + ": " + text);
      }

      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
      assertEquals(text, sql.getSQLString());

      // isLossy() skips the join order check without a data source and doesn't keep that
      // result, so the data source's helper decides it
      UniformSQL fresh = new UniformSQL();
      fresh.setSQLString(text, false);
      assertFalse(fresh.isLossy(), text);
      fresh.setDataSource(dataSource("derby"));
      assertTrue(fresh.isLossy(), text);
   }

   // what main did before the join order check: the same parse without the check writes the
   // condition in WHERE, which returns other rows. A query saved that way is lossy once its
   // data source is set, with or without a saved lossy="false", keeps its sql string and
   // isn't merged
   @ParameterizedTest
   @ValueSource(strings = {
      SEL + "from a join c on a.id = c.id and a.k + 1 != c.k right join b on a.id = b.id",
      SEL + "from a join c on a.id = c.id and a.k != c.k + 0 right join b on a.id = b.id",
      SEL + "from a join c on a.id = c.id and c.k = 1 right join b on a.id = b.id",
      SEL + "from a join c on a.id = c.id and c.k is null right join b on a.id = b.id",
      SEL + "from a join c on a.id = c.id or a.k = c.k right join b on a.id = b.id",
      SEL + "from b left join (a join c on a.id = c.id and a.k + 1 != c.k) on b.id = a.id"
   })
   void savedBeforeJoinOrderCheckIsLossy(String text) throws Exception {
      UniformSQL unchecked = parseWithoutJoinOrderCheck(text);
      unchecked.setDataSource(dataSource("derby"));
      unchecked.clearSQLString();
      String generated = normalize(unchecked.getSQLString());
      assertTrue(generated.contains(" where "), generated);
      assertTrue(SQLHelperWhereOrOuterJoinTest.RowCompare.diffCount(text, generated, 120) > 0,
                 generated);

      for(boolean savedLossy : new boolean[] { false, true }) {
         Element xml = save(parseWithoutJoinOrderCheck(text));

         if(savedLossy) {
            xml.setAttribute("lossy", "false");
         }

         for(String type : new String[] { "h2-ansi", "derby", "oracle", "oracle-ansi" }) {
            JDBCDataSource ds = dataSource(type);
            UniformSQL sql = new UniformSQL();
            sql.parseXML(xml);
            assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type);
            assertTrue(XUtil.isParsedSQL(sql), type);

            // not decided without the data source (BoundQuery asks before setting it)
            assertFalse(sql.isLossy(), type + ": " + text);
            sql.setDataSource(ds);
            assertTrue(sql.isLossy(), type + ": " + text);
            assertFalse(XUtil.isQueryMergeable(query(sql, ds)), type + ": " + text);
            assertEquals(text, sql.getSQLString(), type);
            assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type);
         }
      }
   }

   // where no later join null-extends the condition's table, WHERE keeps the same rows and
   // the query is accepted: inner joins only, the preserved side of a LEFT join, an inner
   // join after a LEFT or RIGHT join, a nested outer join under an inner join, and an inner
   // join after a nested outer join
   @ParameterizedTest
   @ValueSource(strings = {
      SEL + "from a join c on a.id = c.id and a.k + 1 != c.k, b where b.id = c.id",
      SEL + "from a join c on a.id = c.id and a.k + 1 != c.k join b on c.id = b.id",
      SEL + "from a join c on a.id = c.id and c.k is null join b on c.id = b.id",
      SEL + "from a join c on a.id = c.id and a.k + 1 != c.k left join b on c.id = b.id",
      SEL + "from a join c on a.id = c.id and c.k between 0 and 1 left join b on a.id = b.id",
      SEL + "from a join c on (a.id = c.id or a.k = c.k) left join b on a.id = b.id",
      SEL + "from b left join a on b.id = a.id join c on a.id = c.id and a.k + 1 != c.k",
      SEL + "from b left join a on b.id = a.id join c on a.id = c.id and a.k + 1 <> c.k",
      SEL + "from b left join a on b.id = a.id join c on b.id = c.id and a.k is null",
      SEL + "from b left join a on b.id = a.id join c on b.id = c.id and a.k in (1, 2)",
      SEL + "from b left join a on b.id = a.id join c on b.id = c.id and not (a.k = 1)",
      SEL + "from b left join a on b.id = a.id join c on a.k + 1 = c.k",
      SEL + "from b left join a on b.id = a.id join c on c.k = 1",
      SEL + "from a right join b on a.id = b.id join c on b.id = c.id and a.k + 1 != c.k",
      SEL + "from a right join b on a.id = b.id join c on b.id = c.id and a.k is null",
      SEL + "from a join (b left join c on b.id = c.id) on a.id = b.id and a.k + 1 != c.k",
      SEL + "from a join (b left join c on b.id = c.id) on a.id = b.id and c.k is null",
      SEL4 + "from a left join (b left join c on b.id = c.id) on a.id = b.id " +
         "join d on c.id = d.id and c.k is null",
      SEL4 + "from b left join (a join c on a.id = c.id) on b.id = a.id " +
         "join d on d.id = b.id and c.k is null",
      SEL4 + "from a left join b on a.id = b.id join c on c.id = a.id and b.k is null " +
         "left join d on b.id = d.id",
   })
   void nonJoinOnConditionWithoutNullExtendingJoinIsAccepted(String text) throws Exception {
      assertAccepted(text);
   }

   // a subquery in the inner ON after a LEFT join, also correlated to the null-extended table
   @ParameterizedTest
   @ValueSource(strings = {
      SEL + "from b left join a on b.id = a.id join c on b.id = c.id and " +
         "c.k in (select d.k from d)",
      SEL + "from b left join a on b.id = a.id join c on b.id = c.id and " +
         "a.k in (select d.k from d)",
      SEL + "from b left join a on b.id = a.id join c on b.id = c.id and " +
         "c.k not in (select d.k from d where d.k is not null)",
      SEL + "from b left join a on b.id = a.id join c on b.id = c.id and " +
         "a.k not in (select d.k from d where d.k is not null)",
      SEL + "from b left join a on b.id = a.id join c on b.id = c.id and " +
         "exists (select 1 from d where d.id = c.k)",
      SEL + "from b left join a on b.id = a.id join c on b.id = c.id and " +
         "exists (select 1 from d where d.id = a.k)",
      SEL + "from b left join a on b.id = a.id join c on b.id = c.id and " +
         "not exists (select 1 from d where d.id = a.k)",
   })
   void subqueryOnConditionWithoutNullExtendingJoinIsAccepted(String text) throws Exception {
      assertAccepted(text);
   }

   private static void assertAccepted(String text) throws Exception {
      Set<String> compared = new HashSet<>();

      for(String type : new String[] { "h2", "h2-ansi", "derby", "derby-ansi" }) {
         JDBCDataSource ds = dataSource(type);
         String generated = generate(text, ds);
         assertRoundTrip(generated, ds);

         // the helpers mostly write the same sql, compare the rows of each one once
         if(compared.add(generated)) {
            assertEquals(0, SQLHelperWhereOrOuterJoinTest.RowCompare.diffCount(
               text, generated, 120), type + ": " + text + "\n" + generated);
         }
      }
   }

   // the regenerated sql re-parses to itself. A RIGHT OUTER JOIN written for a nested outer
   // join swaps its ON operands once on the first round trip (#77475), so the second
   // generation must be the fixed point
   private static void assertRoundTrip(String generated, JDBCDataSource ds) throws Exception {
      String again = generate(generated, ds);

      if(!again.equals(generated)) {
         assertEquals(again, generate(again, ds), "second round trip of " + generated);
      }
   }

   private static String generate(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = parse(text, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   // parse the way a query of the data source is parsed, with the join order check
   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }

   // the parse of a build before the join order check: the grammar only, a successful parse
   // result and the sql string kept
   private static UniformSQL parseWithoutJoinOrderCheck(String text) throws Exception {
      SQLParser parser = new SQLParser(new SQLLexer(new StringReader(text)));
      parser.setTime(UniformSQL.PARSE_PERIOD);
      UniformSQL sql = new UniformSQL();
      parser.direct_select_stmt_n_rows(sql);
      sql.setParseResult(UniformSQL.PARSE_SUCCESS);
      sql.setSQLString(text, false);
      return sql;
   }

   private static Element save(UniformSQL sql) throws Exception {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement();
   }

   private static JDBCQuery query(UniformSQL sql, JDBCDataSource ds) {
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      return query;
   }

   private static JDBCDataSource dataSource(String type) {
      return SQLHelperNotEqualJoinTest.RowCompare.dataSource(type);
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }
}
