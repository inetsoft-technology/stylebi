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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parse time of nested derived tables and joins in the FROM clause (Bug #77791). The FROM
 * rules parsed every nesting level again in each syntactic predicate, and grammar actions
 * regenerated the SQL of every derived table while it was parsed, so the parse time grew
 * exponentially with the nesting depth and held the single server parser thread for many
 * seconds, past the parse deadline. The depths here take at least 4 s on the old parser,
 * and a few milliseconds when the parse is linear. The parse results must not change, so
 * each shape is also checked at a small depth against the SQL the old parser regenerated.
 * <p>
 * Don't call toString() or getSQLString() on a deeply nested result: SQL generation of
 * nested derived tables is itself exponential, which this test doesn't cover.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLNestedDerivedTableParseTimeTest {
   @Test
   void nestedDerivedTables() throws Exception {
      assertRegenerated(nested(3), "select * from ( select * from ( select * from ( " +
         "select * from t) x3) x2) x1");

      UniformSQL sql = parseInTime(nested(12));

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      UniformSQL level = sql;

      for(int i = 1; i <= 12; i++) {
         assertEquals(List.of("x" + i), aliases(level));
         level = derived(level, "x" + i);
      }

      assertEquals(List.of("t"), aliases(level));
   }

   // each derived table is marked parsed, which the parse used to do as a side effect of
   // generating its sql in a grammar action
   @Test
   void nestedDerivedTablesAreParsed() throws Exception {
      UniformSQL sql = parseInTime(nested(4));
      UniformSQL level = sql;

      for(int i = 1; i <= 4; i++) {
         level = derived(level, "x" + i);
         assertEquals(UniformSQL.PARSE_SUCCESS, level.getParseResult(), "x" + i);
      }
   }

   @Test
   void unbalancedNestedDerivedTables() throws Exception {
      warmUp();
      UniformSQL sql = new UniformSQL();
      String input = "select * from " + "(select * from ".repeat(30) + "t";

      Exception ex = assertTimeoutPreemptively(BUDGET, () -> assertThrows(Exception.class,
         () -> sql.parse(input, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD)),
         () -> "parse took over " + BUDGET.toMillis() + " ms: " + input);

      // a syntax error, not ParserStoppedException
      assertInstanceOf(RecognitionException.class, ex);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
   }

   @Test
   void derivedTableOnRightOfJoin() throws Exception {
      assertRegenerated(joinRight(2), "select * from ( select * from t a0, ( select * from t a1, " +
         "t where 1 = 1) x1 where 1 = 1) x0");

      UniformSQL sql = parseInTime(joinRight(10));

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      UniformSQL level = derived(sql, "x0");

      for(int i = 0; i < 9; i++) {
         assertEquals(List.of("a" + i, "x" + (i + 1)), aliases(level));
         level = derived(level, "x" + (i + 1));
      }

      assertEquals(List.of("a9", "t"), aliases(level));
   }

   @Test
   void commaSeparatedDerivedTables() throws Exception {
      assertRegenerated(commaDerived(2), "select * from ( select * from ( select * from t) x1, " +
         "( select * from t) y1) x0, ( select * from t) y0");

      UniformSQL sql = parseInTime(commaDerived(11));

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      UniformSQL level = sql;

      for(int i = 0; i < 11; i++) {
         assertEquals(List.of("x" + i, "y" + i), aliases(level));
         derived(level, "y" + i);
         level = derived(level, "x" + i);
      }

      assertEquals(List.of("t"), aliases(level));
   }

   @Test
   void derivedTablesOnBothSidesOfJoin() throws Exception {
      assertRegenerated(joinBoth(2), "select * from ( select * from ( select * from t, " +
         "( select * from t) k1 where 1 = 1) x1, ( select * from t) k0 where 1 = 1) x0");

      UniformSQL sql = parseInTime(joinBoth(11));

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      UniformSQL level = derived(sql, "x0");

      for(int i = 1; i < 11; i++) {
         assertEquals(List.of("x" + i, "k" + (i - 1)), aliases(level));
         derived(level, "k" + (i - 1));
         level = derived(level, "x" + i);
      }

      assertEquals(List.of("t", "k10"), aliases(level));
   }

   // a join condition on a column of a derived table compares its qualifier with each table
   // of the top-level query, which generated the sql of each derived table there
   @Test
   void joinConditionOnDerivedTableColumns() throws Exception {
      assertRegenerated(derivedJoinOn(2), "select * from ( select * from ( select * from t x1, " +
         "t w1 where x1.a = w1.a) x0, t w0 where x0.a = w0.a) z, t w where z.a = w.a");

      UniformSQL sql = parseInTime(derivedJoinOn(9));

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals(List.of("z", "w"), aliases(sql));
      UniformSQL level = derived(sql, "z");

      for(int i = 0; i < 8; i++) {
         assertEquals(List.of("x" + i, "w" + i), aliases(level));
         level = derived(level, "x" + i);
      }

      assertEquals(List.of("x8", "w8"), aliases(level));
   }

   @Test
   void rightNestedParenthesizedJoins() throws Exception {
      assertRegenerated(rightNestedJoins(3), "select * from t0, t1, t2, t3 where t2.a = t3.a " +
         "and t1.a = t2.a and t0.a = t1.a");

      UniformSQL sql = parseInTime(rightNestedJoins(25));

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      List<String> expected = new ArrayList<>();

      for(int i = 0; i <= 25; i++) {
         expected.add("t" + i);
      }

      assertEquals(expected, aliases(sql));
   }

   /**
    * select * from (select * from (... (select * from t) xn ...) x2) x1
    */
   private static String nested(int n) {
      String from = "t";

      for(int i = n; i >= 1; i--) {
         from = "(select * from " + from + ") x" + i;
      }

      return "select * from " + from;
   }

   /**
    * select * from (select * from t a0 join (select * from t a1 join ... t on 1=1) x1 on 1=1) x0
    */
   private static String joinRight(int n) {
      return "select * from " + repeat("(select * from t a# join ", "t", " on 1=1) x#", n);
   }

   /**
    * select * from (select * from (... t xn-1 join t wn-1 on xn-1.a = wn-1.a ...) x0
    * join t w0 on x0.a = w0.a) z join t w on z.a = w.a
    */
   private static String derivedJoinOn(int n) {
      return "select * from " +
         repeat("(select * from ", "t", " x# join t w# on x#.a = w#.a)", n) +
         " z join t w on z.a = w.a";
   }

   /**
    * select * from (select * from (... t ...) x1, (select * from t) y1) x0, (select * from t) y0
    */
   private static String commaDerived(int n) {
      return "select * from " +
         repeat("(select * from ", "t", ") x#, (select * from t) y#", n);
   }

   /**
    * select * from (select * from (... t ... join (select * from t) k1 on 1=1) x1
    * join (select * from t) k0 on 1=1) x0
    */
   private static String joinBoth(int n) {
      return "select * from " +
         repeat("(select * from ", "t", " join (select * from t) k# on 1=1) x#", n);
   }

   /**
    * select * from t0 join (t1 join (... (tn-1 join tn on ...) ...) on t1.a = t2.a) on t0.a = t1.a
    */
   private static String rightNestedJoins(int n) {
      String from = "t" + n;

      for(int i = n - 1; i >= 0; i--) {
         from = "t" + i + " join " + (i == n - 1 ? from : "(" + from + ")") +
            " on t" + i + ".a = t" + (i + 1) + ".a";
      }

      return "select * from " + from;
   }

   /**
    * n copies of open, then mid, then n copies of close, innermost last. # is replaced with
    * the level, 0 for the outermost.
    */
   private static String repeat(String open, String mid, String close, int n) {
      StringBuilder str = new StringBuilder();

      for(int i = 0; i < n; i++) {
         str.append(open.replace("#", Integer.toString(i)));
      }

      str.append(mid);

      for(int i = n - 1; i >= 0; i--) {
         str.append(close.replace("#", Integer.toString(i)));
      }

      return str.toString();
   }

   private static UniformSQL parseInTime(String input) throws Exception {
      warmUp();
      UniformSQL sql = new UniformSQL();

      assertTimeoutPreemptively(
         BUDGET, () -> sql.parse(input, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD),
         () -> "parse took over " + BUDGET.toMillis() + " ms: " + input);

      return sql;
   }

   // load the parser classes outside the time budget
   private static void warmUp() throws Exception {
      new UniformSQL().parse(nested(1), UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
   }

   /**
    * Check the regenerated sql of a shallow statement, and that it regenerates to itself.
    */
   private static void assertRegenerated(String input, String expected) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(input, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      String generated = sql.toString();
      assertEquals(expected, normalize(generated));

      UniformSQL reparsed = new UniformSQL();
      reparsed.parse(generated, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(expected, normalize(reparsed.toString()));
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static List<String> aliases(UniformSQL sql) {
      List<String> aliases = new ArrayList<>();

      for(int i = 0; i < sql.getTableCount(); i++) {
         aliases.add(sql.getSelectTable(i).getAlias());
      }

      return aliases;
   }

   // don't pass the derived table to an assertion message, which would generate its sql
   private static UniformSQL derived(UniformSQL sql, String alias) {
      Object name = sql.getTableName(alias);
      assertTrue(name instanceof UniformSQL, alias + " is not a derived table");
      return (UniformSQL) name;
   }

   // a linear parse takes a few milliseconds, the old parser at least 4 s
   private static final Duration BUDGET = Duration.ofSeconds(1);
}
