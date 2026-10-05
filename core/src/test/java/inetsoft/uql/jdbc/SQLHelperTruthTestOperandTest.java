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
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.StringReader;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77572, the operand of x IS [NOT] TRUE/FALSE/UNKNOWN was generated without parentheses.
 * IS binds tighter than NOT, so a negated operand, (not (a.k = 1)) is true, was generated as
 * not (a.k = 1) is true, which negates the whole test and returns different rows when the
 * operand is null. A same-relation nested test, ((a.k = 1) is true) is true, was generated
 * as a.k = 1 is true is true, which doesn't parse. The operand is now always parenthesized.
 * <p>
 * A statement with a truth test fails to parse now (#77735), so a truth-test tree comes only
 * from a parse saved before that, or from condition text. {@link #parse} builds the tree a
 * statement parse built before the refusal: it parses the statement without its WHERE and
 * HAVING, and those as conditions.
 * <p>
 * Derby has no IS TRUE and HSQLDB evaluates null IS TRUE as null, so the rows are compared
 * on PostgreSQL only, in {@link #samePostgresRows}, which runs when the system property
 * truthtest.pg.url names a database, e.g.
 * -Dtruthtest.pg.url=jdbc:postgresql://localhost:5432/postgres -Dtruthtest.pg.password=..
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperTruthTestOperandTest {
   private static final String SEL = "select a.id ai, a.k ak, b.id bi, b.k bk from a, b ";
   private static final String SEL_A = "select a.id ai, a.k ak from a ";
   private static final String HAVING = "select a.k ak, count(*) n from a group by a.k having ";
   private static final String[] TYPES = {
      "default", "h2", "h2-ansi", "oracle", "oracle-ansi", "postgresql" };
   // helpers whose re-parsed tree is comparable to the original one. The others quote
   // names or move ON conditions, which changes the tree but not the text round trip.
   private static final Set<String> TREE_TYPES = Set.of("default", "h2", "oracle");

   static String[] shapes() {
      return new String[] {
         // the reported shape, and every truth value with a negated operand
         SEL + "where not (not (a.k = 1)) is not true",
         SEL_A + "where (not (a.k = 1)) is true",
         SEL_A + "where (not (a.k = 1)) is not true",
         SEL_A + "where (not (a.k = 1)) is false",
         SEL_A + "where (not (a.k = 1)) is not false",
         SEL_A + "where (not (a.k = 1)) is unknown",
         SEL_A + "where (not (a.k = 1)) is not unknown",
         SEL_A + "where (not (a.k = 1)) Is Not Unknown",
         SEL_A + "where not (not (a.k = 1)) is true",
         SEL_A + "where (not (not (not (a.k = 1)))) is not true",
         // negated IN, BETWEEN, EXISTS, arithmetic and column comparison (XJoin) operands
         SEL_A + "where (a.k not in (1, 2)) is not true",
         SEL_A + "where (not (a.k in (1, 2))) is true",
         SEL_A + "where (a.k not between 1 and 2) is not true",
         SEL_A + "where (not (a.k between 1 and 2)) is false",
         SEL_A + "where (not (a.k in (select b.k from b))) is true",
         SEL_A + "where (not exists (select 1 from b where b.id = a.id and b.k = 1)) is not true",
         SEL_A + "where (not (a.k + 1 > a.id)) is true",
         SEL + "where (not (a.id = b.k)) is not true",
         SEL + "where (a.id = b.k) is unknown",
         // position: under AND and OR, in a join ON, after an outer join, in a subquery
         SEL_A + "where (not (a.k = 1)) is not true and a.id is not null",
         SEL_A + "where a.id = 0 or (not (a.k = 1)) is not true",
         SEL + "where a.id = b.id and (not (a.k = b.k)) is not true",
         "select a.id ai, a.k ak, b.id bi, b.k bk from a left join b on a.id = b.id " +
            "where (not (b.k = 1)) is not true",
         SEL_A + "where a.k in (select b.k from b where (not (b.id = 1)) is true)",
         // HAVING
         HAVING + "(not (a.k = 1)) is false",
         HAVING + "(not (count(*) > 1)) is not true",
         HAVING + "(not (sum(a.id) > 1)) is not unknown",
         // nested truth tests, with and without NOT
         SEL_A + "where ((a.k = 1) is true) is true",
         SEL_A + "where ((a.k = 1) is not true) is not true",
         SEL_A + "where (not ((a.k = 1) is true)) is true",
         SEL_A + "where ((a.k = 1) is true) is not true",
         // operands that were already right, which must stay right
         SEL_A + "where not (a.k = 1) is true",
         SEL_A + "where (a.k = 1) is not true",
         SEL_A + "where (a.k is not null) is not true",
         SEL_A + "where (not (a.k is null)) is false",
         SEL_A + "where (not (a.k = 1 and a.id = 2)) is not true",
         SEL_A + "where (a.k = 1 or not (a.id = 2)) is unknown",
      };
   }

   @ParameterizedTest
   @MethodSource("shapes")
   void roundTrip(String text) throws Exception {
      for(String type : TYPES) {
         String generated = generate(text, type);
         assertEquals(generated, generate(generated, type), type + " round trip: " + text);

         if(TREE_TYPES.contains(type)) {
            List<String> tests = tree(text, type);
            assertFalse(tests.isEmpty(), text);
            assertEquals(tests, tree(generated, type),
                         type + " tree: " + text + " -> " + generated);
         }
      }
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "where (not (a.k = 1)) is true | where ((not (a.k = 1)) is true)",
      "where (not (a.k = 1)) is not unknown | where ((not (a.k = 1)) is not unknown)",
      "where (a.k = 1) is false | where ((a.k = 1) is false)",
      "where ((a.k = 1) is true) is true | where (((a.k = 1) is true) is true)",
      "where (not ((a.k = 1) is true)) is true | where ((not ((a.k = 1) is true)) is true)",
      // an operand that is already parenthesized isn't wrapped twice
      "where (a.k = 1 and a.id = 2) is true | where ((a.k = 1 and a.id = 2) is true)",
      "where ((a.k = 1) is true) is not true | where (((a.k = 1) is true) is not true)",
   })
   void generatedText(String where, String expected) throws Exception {
      String generated = generate(SEL_A + where, "default");
      assertEquals(expected, generated.substring(generated.indexOf("where ")));
   }

   @Test
   void oracleNonAnsiNegation() throws Exception {
      String generated = generate(SEL_A + "where (not (a.k = 1)) is not true", "oracle");
      assertTrue(generated.endsWith("where ((not a.k = 1) is not true)"), generated);
   }

   // an ANSI outer join operand is moved to FROM and generates as "", which must not become
   // "()". The parser refuses an outer join under IS (#77481), so the tree is built here.
   // A *= is refused with a data source that writes ANSI joins (Bug #77548), so the sql is
   // parsed without one
   @Test
   void emptyOperandIsNotWrapped() throws Exception {
      UniformSQL sql = parseStatement(SEL + "where a.id *= b.id and a.k = 1", "default");
      sql.setDataSource(SQLHelperNotEqualJoinTest.RowCompare.dataSource("h2-ansi"));
      XJoin join = findJoin(sql.getWhere());
      assertNotNull(join, "outer join");
      XNode parent = join.getParent();
      XUnaryCondition truth = new XUnaryCondition();
      XExpression value = new XExpression();
      value.setValue("true", XExpression.EXPRESSION);
      truth.setExpression1(value);
      truth.setOp("");
      XSet test = new XSet("is");
      parent.removeChild(join);
      test.addChild(join);
      test.addChild(truth);
      parent.addChild(test);
      sql.clearSQLString();
      String generated = normalize(sql.getSQLString());

      assertTrue(generated.contains("LEFT OUTER JOIN"), generated);
      assertTrue(generated.endsWith("and (true)"), generated);
      assertFalse(generated.contains("()"), generated);
   }

   @ParameterizedTest
   @MethodSource("shapes")
   @EnabledIfSystemProperty(named = "truthtest.pg.url", matches = ".+")
   void samePostgresRows(String text) throws Exception {
      List<String> generated = new ArrayList<>();

      for(String type : new String[] { "default", "h2-ansi", "oracle", "postgresql" }) {
         String sql = generate(text, type);

         // oracle non-ansi writes an outer join as (+), which postgresql can't run
         if(!sql.contains("(+)")) {
            generated.add(sql);
         }
      }

      try(Connection con = DriverManager.getConnection(
         System.getProperty("truthtest.pg.url"), System.getProperty("truthtest.pg.user", "postgres"),
         System.getProperty("truthtest.pg.password")))
      {
         assertEquals("[]", diffs(con, text, generated).toString(), text);
      }
   }

   // the statement itself is refused, see the class comment. The shapes have no WHERE or
   // HAVING keyword before the outer one, and none after the HAVING.
   private static UniformSQL parse(String text, String type) throws Exception {
      UniformSQL refused = new UniformSQL();
      new SQLProcessor(refused).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, refused.getParseResult(), text);

      String statement = text;
      String where = null;
      String having = null;
      int index = statement.toLowerCase(Locale.ROOT).indexOf(" having ");

      if(index >= 0) {
         having = statement.substring(index + 8);
         statement = statement.substring(0, index);
      }

      index = statement.toLowerCase(Locale.ROOT).indexOf(" where ");

      if(index >= 0) {
         where = statement.substring(index + 7);
         statement = statement.substring(0, index);
      }

      UniformSQL sql = parseStatement(statement, type);

      if(where != null) {
         XFilterNode condition = condition(where);
         condition.setClause(XFilterNode.WHERE);
         markJoins(condition);
         sql.combineWhereByAnd(condition);
      }

      if(having != null) {
         XFilterNode condition = condition(having);
         condition.setClause(XFilterNode.HAVING);
         sql.setHaving(condition);
      }

      return sql;
   }

   private static UniformSQL parseStatement(String text, String type) throws Exception {
      UniformSQL sql = new UniformSQL();

      if(!"default".equals(type)) {
         sql.setDataSource(SQLHelperNotEqualJoinTest.RowCompare.dataSource(type));
      }

      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      return sql;
   }

   private static XFilterNode condition(String text) throws Exception {
      return new SQLParser(new SQLLexer(new StringReader(text))).search_condition();
   }

   // as the parser marks the joins of a where clause
   private static void markJoins(XNode node) {
      if(node instanceof XJoin join) {
         join.setJoinClause(XJoin.WHERE_CLAUSE);
      }
      else if(node instanceof XSet) {
         for(int i = 0; i < node.getChildCount(); i++) {
            markJoins(node.getChild(i));
         }
      }
   }

   private static String generate(String text, String type) throws Exception {
      UniformSQL sql = parse(text, type);
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   // the truth tests of the WHERE and HAVING trees. The rest of the tree can change shape when
   // ON conditions are moved, which the text round trip and the rows cover.
   private static List<String> tree(String text, String type) throws Exception {
      UniformSQL sql = parse(text, type);
      List<String> tests = new ArrayList<>();
      truthTests(sql.getWhere(), tests);
      truthTests(sql.getHaving(), tests);
      Collections.sort(tests);
      return tests;
   }

   private static void truthTests(XNode node, List<String> tests) {
      if(node instanceof XSet set && SQLHelper.isTruthTest(set)) {
         tests.add(dump(set));
      }
      else if(node instanceof XBinaryCondition condition &&
         condition.getExpression2().getValue() instanceof UniformSQL subquery)
      {
         truthTests(subquery.getWhere(), tests);
      }
      else if(node != null) {
         for(int i = 0; i < node.getChildCount(); i++) {
            truthTests(node.getChild(i), tests);
         }
      }
   }

   private static String dump(XNode node) {
      String not = node instanceof XFilterNode filter && filter.isIsNot() ? "not " : "";

      if(node instanceof XSet set) {
         StringBuilder sb = new StringBuilder(not).append("set[")
            .append(set.getRelation().toLowerCase(Locale.ROOT)).append("](");

         for(int i = 0; i < set.getChildCount(); i++) {
            sb.append(i > 0 ? ", " : "").append(dump(set.getChild(i)));
         }

         return sb.append(")").toString();
      }

      return not + node.getClass().getSimpleName() + "{" + normalize(node.toString()) + "}";
   }

   private static XJoin findJoin(XNode node) {
      if(node instanceof XJoin join) {
         return join;
      }

      for(int i = 0; node != null && i < node.getChildCount(); i++) {
         XJoin join = findJoin(node.getChild(i));

         if(join != null) {
            return join;
         }
      }

      return null;
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   // compares the rows of the original and each generated query on random small datasets
   // of a(id, k) and b(id, k), as multisets, and returns the generated queries that differ.
   // The comparison runs in the database to keep the round trips few.
   private static Set<String> diffs(Connection con, String original, List<String> generated)
      throws SQLException
   {
      Random random = new Random(77572);
      Integer[] values = { null, 0, 1, 2 };
      Set<String> diffs = new LinkedHashSet<>();
      StringBuilder compare = new StringBuilder("select ");

      for(int i = 0; i < generated.size(); i++) {
         compare.append(i > 0 ? ", " : "")
            .append("(select count(*) from ((").append(original).append(") except all (")
            .append(generated.get(i)).append(")) x) + (select count(*) from ((")
            .append(generated.get(i)).append(") except all (").append(original).append(")) y)");
      }

      try(Statement st = con.createStatement()) {
         // the planner assumes large tables, and compiling the comparison takes seconds
         st.execute("set jit = off");
         // temp tables hide any real a and b, and are dropped with the connection
         st.execute("create temp table a (id int, k int); create temp table b (id int, k int)");

         for(int n = 0; n < 100; n++) {
            StringBuilder data = new StringBuilder("delete from a; delete from b;");

            for(String table : new String[] { "a", "b" }) {
               for(int r = random.nextInt(5); r > 0; r--) {
                  data.append(" insert into ").append(table).append(" values (")
                     .append(values[random.nextInt(4)]).append(", ")
                     .append(values[random.nextInt(4)]).append(");");
               }
            }

            st.execute(data.toString());

            try(ResultSet rs = st.executeQuery(compare.toString())) {
               rs.next();

               for(int i = 0; i < generated.size(); i++) {
                  if(rs.getLong(i + 1) != 0) {
                     diffs.add(generated.get(i));
                  }
               }
            }
         }
      }

      return diffs;
   }
}
