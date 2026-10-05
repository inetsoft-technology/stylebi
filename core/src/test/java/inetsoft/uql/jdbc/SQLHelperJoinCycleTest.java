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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77489, the ANSI FROM clause generation groups joins per pair of tables. When the joins
 * form a cycle (e.g. a = b, b = c, a = c), the last pair of a cycle joins two tables that are
 * already joined, and it was written as a join of its second table again, so the table was in
 * the FROM clause twice and the SQL failed. Such a pair is a condition of the joined tables:
 * it goes into the ON of the last join step if that keeps its meaning, otherwise into the
 * where clause. A join between columns of one table is a condition too.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperJoinCycleTest {
   private static final String COLS3 = "select a.id, b.id, c.id ";
   private static final String COLS = "select a.id, b.id, c.id, d.id ";
   private static final String COLS5 = "select a.id, b.id, c.id, d.id, e.id ";
   private static final String DB = "jdbc:derby:memory:bug77489";
   private static final String HSQLDB = "jdbc:hsqldb:mem:bug77489";

   // parsed queries with a cycle in their joins. With the join clauses cleared (a query saved
   // before they were recorded) or on an ANSI join data source, they use the join matrix walk
   static Stream<String> cycleQueries() {
      return Stream.of(
         // the report query, with the conditions in both orders
         COLS3 + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k",
         COLS3 + "from a left join b on a.id = b.id join c on a.k = c.k and b.id = c.id",
         COLS3 + "from a right join b on a.id = b.id join c on b.id = c.id and a.k = c.k",
         // all inner triangle and square
         COLS3 + "from a join b on a.id = b.id join c on b.id = c.id and a.k = c.k",
         COLS + "from a join b on a.id = b.id join c on b.id = c.id join d on c.id = d.id " +
            "and a.k = d.k",
         // an outer join after the cycle
         COLS + "from a join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "right join d on a.id = d.id",
         COLS + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "left join d on a.id = d.id",
         // the last join step before the cycle edge is an outer join
         COLS + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "left join d on c.id = d.id",
         // ... with a where clause
         COLS + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "left join d on c.id = d.id where a.id > 1 or b.k is null",
         // an outer join before the cycle
         COLS + "from a left join b on a.id = b.id join c on b.id = c.id join d on c.id = d.id " +
            "and a.k = d.k",
         COLS + "from a join b on a.id = b.id left join c on b.id = c.id join d on c.id = d.id " +
            "and a.k = d.k",
         // an outer join in the cycle
         COLS + "from a join c on a.k = c.k left join b on a.id = b.id join d on b.id = d.id " +
            "and c.k = d.k",
         // an unrelated table
         COLS + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k, d " +
            "where d.k > 1",
         // a right join step between the tables of the cycle edge
         COLS + "from a join b on a.id = b.id right join c on b.k = c.k join d on c.id = d.id " +
            "and a.k = d.k",
         // two cycle edges in one on
         COLS + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "and b.k = c.k left join d on b.id = d.id",
         // five tables, the cycle edge closes after an outer join step
         COLS5 + "from a join b on a.id = b.id left join c on b.id = c.id join d on c.id = d.id " +
            "join e on d.id = e.id and b.k = e.k",
         COLS5 + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "left join d on c.id = d.id left join e on d.id = e.id"
      );
   }

   // a right join after the outer step before the cycle edge null-extends the group, so the
   // edge can't be a where condition. It goes into the ON of the right join
   static Stream<String> rightJoinCycleQueries() {
      return Stream.of(
         COLS + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "right join d on c.id = d.id",
         COLS + "from a right join b on a.id = b.id join c on a.id = c.id and b.k = c.k " +
            "right join d on c.id = d.id",
         COLS5 + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "right join d on c.id = d.id left join e on d.id = e.id",
         COLS5 + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "join d on c.id = d.id right join e on d.k = e.k",
         // an inner step with its own cycle edge between the held condition and the right join
         COLS5 + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "join d on c.id = d.id and b.k = d.k right join e on d.id = e.id",
         // the first of two right joins takes the pending condition
         COLS5 + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "right join d on c.id = d.id right join e on d.k = e.k",
         // a second cycle edge after an inner step is in its ON, the pending one in the right join
         COLS5 + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "join d on c.id = d.id and a.id = d.id right join e on d.k = e.k",
         // a left join step between the outer step and the right join
         COLS5 + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "left join d on c.id = d.id right join e on d.k = e.k",
         // ... with a where clause
         COLS + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "right join d on c.id = d.id where d.k > 1 or a.id is null"
      );
   }

   @Test
   void editorRightJoinAfterWhereRouteKeepsAllRightRows() throws Exception {
      // G RIGHT JOIN d ON c.id = d.id, with b.k = c.k filtering G first, written as d LEFT JOIN G
      String generated = generate(editor("a.id = b.id", "a.id *= c.id", "b.k = c.k", "c.id =* d.id"),
                                  true);
      String reference = COLS + "from d left join (a join b on a.id = b.id left join c on " +
         "a.id = c.id) on c.id = d.id and b.k = c.k";
      assertSameRows(reference, generated);
      assertNull(rowMismatch(HSQLDB, reference, generated), generated);
   }

   // queries with a full join after a cycle, run on HSQLDB (Derby has no full join)
   static Stream<String> fullJoinCycleQueries() {
      return Stream.of(
         // the cycle edge is in the ON of an inner step
         COLS + "from a join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "full join d on c.id = d.id",
         // the cycle edge follows an outer step, and no condition placement keeps its meaning
         // once the full join null-extends the group
         COLS + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "full join d on c.id = d.id",
         COLS + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
            "left join d on c.id = d.id full join e on d.id = e.id"
      );
   }

   static Stream<Arguments> cycleQueryCases() {
      return Stream.concat(cycleQueries(), rightJoinCycleQueries()).flatMap(text -> Stream.of(
         Arguments.of(text, false, false), Arguments.of(text, false, true),
         Arguments.of(text, true, false), Arguments.of(text, true, true)));
   }

   @ParameterizedTest
   @MethodSource("cycleQueryCases")
   void cycleReturnsSameRowsOnDerby(String text, boolean legacy, boolean ansiJoin) throws Exception {
      String generated = generate(parse(text, legacy), ansiJoin);
      assertSameRows(text, generated);
   }

   @ParameterizedTest
   @MethodSource("cycleQueryCases")
   void cycleReturnsSameRowsOnHsqldb(String text, boolean legacy, boolean ansiJoin) throws Exception {
      String generated = generate(parse(text, legacy), ansiJoin);
      List<String> mismatch = rowMismatch(HSQLDB, text, generated);
      assertNull(mismatch, "expected: " + text + "\ngenerated: " + generated + "\n" + mismatch);
   }

   static Stream<Arguments> fullJoinCycleQueryCases() {
      return fullJoinCycleQueries().flatMap(text -> Stream.of(
         Arguments.of(text, false, false), Arguments.of(text, false, true),
         Arguments.of(text, true, false), Arguments.of(text, true, true)));
   }

   @ParameterizedTest
   @MethodSource("fullJoinCycleQueryCases")
   void fullJoinAfterCycleIsNeverSilentlyWrong(String text, boolean legacy, boolean ansiJoin)
      throws Exception
   {
      // with a condition pending after an outer step, a later full join leaves no placement
      // that keeps its meaning, so the joins are generated as before: the table of the cycle
      // edge is joined again, which databases reject (Derby, PostgreSQL, MySQL, SQL Server).
      // HSQLDB accepts a table twice, so a mismatch there must be that sql
      String generated = generate(parse(text, legacy), ansiJoin);
      List<String> mismatch = rowMismatch(HSQLDB, text, generated);

      assertTrue(mismatch == null || hasDuplicateTable(generated),
                 "expected: " + text + "\ngenerated: " + generated + "\n" + mismatch);
   }

   @Test
   void fullJoinAfterFlushedConditionsKeepsTheRows() throws Exception {
      // the held condition is in the ON of the right join, so nothing is held at the full join
      // and the cycle is still written as a condition
      String text = COLS5 + "from a left join b on a.id = b.id join c on b.id = c.id and " +
         "a.k = c.k right join d on c.id = d.id full join e on d.id = e.id";

      for(boolean legacy : new boolean[] { false, true }) {
         for(boolean ansiJoin : new boolean[] { false, true }) {
            String generated = generate(parse(text, legacy), ansiJoin);
            assertFalse(hasDuplicateTable(generated), generated);
            assertNull(rowMismatch(HSQLDB, text, generated), generated);
         }
      }
   }

   @Test
   void heldConditionsGoIntoOneRightJoin() throws Exception {
      // a.k = c.k and d.k = c.k both follow the outer join of c, and the right join of e gets
      // both of them
      String generated = generate(editor("a.id = b.id", "b.id = d.id", "a.k = c.k", "d.k = c.k",
                                         "b.id *= c.id", "d.id =* e.id"), true);
      assertTrue(generated.endsWith("RIGHT OUTER JOIN e ON d.id = e.id AND a.k = c.k AND d.k = c.k"),
                 generated);
      assertSameRows("select t.ai, t.bi, t.ci, t.di, e.id from (select a.id ai, b.id bi, c.id ci, " +
                     "d.id di from a join b on a.id = b.id join d on b.id = d.id left join c " +
                     "on b.id = c.id where a.k = c.k and d.k = c.k) t right join e on t.di = e.id",
                     generated);
   }

   static Stream<String> legacyCycleQueries() {
      return Stream.of(cycleQueries(), rightJoinCycleQueries(), fullJoinCycleQueries())
         .flatMap(q -> q);
   }

   @ParameterizedTest
   @MethodSource("legacyCycleQueries")
   void legacyCycleKeepsTheSqlString(String text) throws Exception {
      // a legacy parsed cycle query can't be regenerated from its structure with the right
      // rows, so it is lossy: the callers that clear the sql string only do so for a query
      // that is not lossy, and the text, which returns the right rows, is kept. With an outer
      // join, the join clauses of the sql string are recorded instead (Bug #77548), and the
      // structure is regenerated as the parsed query
      UniformSQL legacy = savedQuery(text, true);
      boolean outer = Arrays.stream(legacy.getJoins()).anyMatch(XJoin::isOuterJoin);
      assertEquals(!outer, legacy.isLossy(), text);
      assertEquals(text, legacy.getSQLString());

      if(outer) {
         assertEquals(generate(parse(text, false), true), generate(legacy, true), text);
      }

      // with the clauses recorded, the structure is regenerated and returns the same rows
      UniformSQL recorded = savedQuery(text, false);
      assertFalse(recorded.isLossy(), text);
   }

   @Test
   void legacyQueryWithoutCycleIsNotLossy() throws Exception {
      assertFalse(savedQuery(COLS3 + "from a left join b on a.id = b.id join c on b.id = c.id",
                             true).isLossy());
      // two conditions between the same tables are one join step, not a cycle
      assertFalse(savedQuery(COLS3 + "from a join b on a.id = b.id and a.k = b.k", true)
                     .isLossy());
   }

   @Test
   void legacyCycleIsNotLossyOnceTheSqlStringIsCleared() throws Exception {
      UniformSQL sql = savedQuery(COLS3 + "from a join b on a.id = b.id join c on b.id = c.id " +
                                  "and a.k = c.k", true);
      assertTrue(sql.isLossy());
      sql.clearSQLString();
      assertFalse(sql.isLossy());
   }

   @Test
   void legacyCycleKeepsTheOuterLastOrder() throws Exception {
      // Known risk: a parsed query saved before #77475 has no recorded join clauses, so it
      // can't be told from joins built in the query editor. A cycle makes getLoyalJoins fail,
      // so its joins take the outer-last order, which keeps the a rows that the text's inner
      // join to d removes. It used to fail with a duplicate table; now it runs with that
      // order. This pins the sql, so a fix of the order is a deliberate change
      String text = COLS5 + "from a left join b on a.id = b.id left join c on a.id = c.id " +
         "join d on c.id = d.id join e on c.id = e.id and d.k = e.k";
      String generated = generate(parse(text, true), true);
      assertTrue(generated.endsWith(LEGACY_OUTER_LAST), generated);
      assertRuns(generated);

      // with the join clauses recorded (the sql parsed again), the text order is kept
      assertSameRows(text, generate(parse(text, false), true));
   }

   private static final String LEGACY_OUTER_LAST = "from (((c INNER JOIN d ON c.id = d.id ) " +
      "INNER JOIN e ON c.id = e.id AND d.k = e.k ) RIGHT OUTER JOIN a ON a.id = c.id ) " +
      "LEFT OUTER JOIN b ON a.id = b.id";

   @Test
   void fullJoinAfterInnerCycleStepKeepsTheCondition() throws Exception {
      // the cycle edge is in the ON of the inner step, inside the full join
      String text = COLS + "from a join b on a.id = b.id join c on b.id = c.id and a.k = c.k " +
         "full join d on c.id = d.id";
      String generated = generate(parse(text, true), true);
      assertTrue(generated.contains("INNER JOIN c ON a.k = c.k AND b.id = c.id ) FULL OUTER JOIN d"),
                 generated);
      assertNull(rowMismatch(HSQLDB, text, generated), generated);
   }

   @Test
   void conditionAfterOuterStepIsInTheOnOfALaterRightJoin() throws Exception {
      String text = COLS + "from a left join b on a.id = b.id join c on b.id = c.id and " +
         "a.k = c.k right join d on c.id = d.id";
      String generated = generate(parse(text, true), true);
      assertTrue(generated.endsWith("RIGHT OUTER JOIN d ON c.id = d.id AND a.k = c.k"), generated);
      assertSameRows(text, generated);

      // a.id *= c.id is the last step before b.k = c.k, and c.id =* d.id keeps all d rows
      generated = generate(editor("a.id = b.id", "a.id *= c.id", "b.k = c.k", "c.id =* d.id"), true);
      assertTrue(generated.endsWith("LEFT OUTER JOIN c ON a.id = c.id ) RIGHT OUTER JOIN d ON " +
                                    "c.id = d.id AND b.k = c.k"), generated);
      assertSameRows("select t.ai, t.bi, t.ci, d.id from (select a.id ai, b.id bi, c.id ci " +
                     "from a join b on a.id = b.id left join c on a.id = c.id where b.k = c.k) t " +
                     "right join d on t.ci = d.id", generated);
   }

   @ParameterizedTest
   @MethodSource("cycleQueries")
   void regeneratedCycleRoundTrips(String text) throws Exception {
      // a reparsed outer join can swap its ON operands once, so require a fixed point after
      // the first regeneration
      String generated = generate(parse(text, true), true);
      String regenerated = generate(parse(generated, true), true);
      assertEquals(regenerated, generate(parse(regenerated, true), true));
      assertSameRows(text, regenerated);
   }

   @ParameterizedTest
   @MethodSource("rightJoinCycleQueries")
   void rightJoinWithCycleConditionIsNotReparsed(String text) throws Exception {
      // the parser refuses an outer join ON between more than one pair of tables, so the
      // regenerated sql fails to parse (and runs as written) instead of parsing differently
      String generated = generate(parse(text, true), true);
      Exception ex = assertThrows(Exception.class, () -> parse(generated, false));
      assertTrue(String.valueOf(ex.getMessage()).contains("Unsupported outer join condition"),
                 String.valueOf(ex.getMessage()));
   }

   @Test
   void innerCycleEdgeAfterInnerStepIsInTheOn() throws Exception {
      String generated = generate(editor("a.id = b.id", "b.id = c.id", "a.k = c.k"), true);
      assertTrue(generated.endsWith("from (a INNER JOIN b ON a.id = b.id ) INNER JOIN c ON " +
                                    "a.k = c.k AND b.id = c.id"), generated);
      // same rows as the comma and where form
      assertSameRows(generate(editor("a.id = b.id", "b.id = c.id", "a.k = c.k"), false), generated);

      UniformSQL square = editor("a.id = b.id", "b.id = c.id", "c.id = d.id", "d.k = a.k");
      generated = generate(square, true);
      assertTrue(generated.endsWith("INNER JOIN d ON c.id = d.id AND d.k = a.k"), generated);
      assertSameRows(generate(square, false), generated);

      // the report query saved before the join clauses were recorded
      generated = generate(parse(COLS3 + "from a left join b on a.id = b.id join c on " +
                                 "b.id = c.id and a.k = c.k", true), false);
      assertTrue(generated.endsWith("from (a LEFT OUTER JOIN b ON a.id = b.id ) INNER JOIN c ON " +
                                    "a.k = c.k AND b.id = c.id"), generated);
   }

   @Test
   void innerCycleEdgeAfterOuterStepIsInTheWhere() throws Exception {
      // in the ON of the outer join, the condition would keep the null-extended rows
      String generated = generate(editor("a.id = b.id", "a.id *= c.id", "b.k = c.k"), true);
      assertTrue(generated.endsWith("from (a INNER JOIN b ON a.id = b.id ) LEFT OUTER JOIN c ON " +
                                    "a.id = c.id where b.k = c.k"), generated);
      assertRuns(generated);

      String text = COLS + "from a left join b on a.id = b.id join c on b.id = c.id and " +
         "a.k = c.k left join d on c.id = d.id where a.id > 1 or b.k is null";
      generated = generate(parse(text, true), true);
      assertTrue(generated.endsWith("RIGHT OUTER JOIN a ON a.id = b.id ) LEFT OUTER JOIN d ON " +
                                    "c.id = d.id where (a.id > 1 or b.k is null) AND a.k = c.k"),
                 generated);
      assertSameRows(text, generated);
   }

   @Test
   void outerCycleEdgeOfTheLastOuterJoinIsInTheOn() throws Exception {
      // a is the preserved table of the last join step and of a *= c
      String generated = generate(editor("a.id *= b.id", "a.id *= c.id", "b.k = c.k"), true);
      assertTrue(generated.endsWith("from (b INNER JOIN c ON b.k = c.k ) RIGHT OUTER JOIN a ON " +
                                    "a.id = b.id AND a.id = c.id"), generated);
      assertRuns(generated);
   }

   @Test
   void otherOuterCycleEdgeIsAnInnerCondition() throws Exception {
      // joins with a join order (data model) are not reordered. a *= c can't be written
      // after a and c are inner joined, so it's written as an inner condition
      UniformSQL sql = editor("b.id = c.id", "a.id = b.id", "a.id *= c.id");
      setOrders(sql);
      String generated = generate(sql, true);
      assertTrue(generated.endsWith("from (b INNER JOIN c ON b.id = c.id ) INNER JOIN a ON " +
                                    "a.id = b.id AND a.id = c.id"), generated);
      assertRuns(generated);

      // after (a = c) RIGHT JOIN b, b is preserved, so c *= b (b null supplying) can't be written
      sql = editor("a.id = c.id", "c.id *= b.id", "b.k *= a.k");
      setOrders(sql);
      generated = generate(sql, true);
      assertTrue(generated.endsWith("from (a INNER JOIN c ON a.id = c.id ) RIGHT OUTER JOIN b ON " +
                                    "b.k = a.k where c.id = b.id"), generated);
      assertRuns(generated);
   }

   @Test
   void editorCycleEdgesKeepTheirMeaning() throws Exception {
      // the rows of each placement, compared with a hand-written query of the joins
      assertSameRows(COLS3 + "from a left join b on a.id = b.id join c on b.id = c.id and a.k = c.k",
                     generate(editor("a.id *= b.id", "b.id = c.id", "a.k = c.k"), true));
      assertSameRows(COLS + "from a left join b on a.id = b.id join c on b.id = c.id " +
                     "join d on c.id = d.id and d.k = a.k",
                     generate(editor("a.id *= b.id", "b.id = c.id", "c.id = d.id", "d.k = a.k"), true));
      // inner cycle edge after an outer step (where clause)
      assertSameRows(COLS3 + "from a join b on a.id = b.id left join c on a.id = c.id where b.k = c.k",
                     generate(editor("a.id = b.id", "a.id *= c.id", "b.k = c.k"), true));
      // outer cycle edge of the last outer join (on)
      assertSameRows(COLS3 + "from a left join (b join c on b.k = c.k) on a.id = b.id and a.id = c.id",
                     generate(editor("a.id *= b.id", "a.id *= c.id", "b.k = c.k"), true));
   }

   @Test
   void joinTypeChangedInTheEditorRuns() throws Exception {
      // the shape of SQLHelperTextJoinOrderTest.joinTypeChangedInTheEditorKeepsTheOldOrder,
      // which wrote c twice
      UniformSQL sql = parse(COLS + "from a left join b on a.id = b.id join c on b.id = c.id " +
                             "and a.id = c.id left join d on a.id = d.id", false);

      for(XJoin join : sql.getJoins()) {
         if("a.id".equals(join.getExpression1().getValue()) &&
            "c.id".equals(join.getExpression2().getValue()))
         {
            join.setOp("*=");
         }
      }

      String generated = generate(sql, false);
      assertTrue(generated.endsWith("from ((b INNER JOIN c ON b.id = c.id ) RIGHT OUTER JOIN a ON " +
                                    "a.id = b.id AND a.id = c.id ) LEFT OUTER JOIN d ON a.id = d.id"),
                 generated);
      assertRuns(generated);
   }

   @Test
   void oneTableJoinIsACondition() throws Exception {
      // at the start of a group it would join the table to itself. It's a condition of the
      // group, here in the ON of the inner join
      String generated = generate(editor("a.k = a.id", "a.id = b.id"), true);
      assertTrue(generated.endsWith("from a INNER JOIN b ON a.id = b.id AND a.k = a.id"),
                 generated);
      assertRuns(generated);

      // in a group of its own
      generated = generate(editor("a.id = b.id", "c.k = c.id"), true);
      assertTrue(generated.endsWith("from a INNER JOIN b ON a.id = b.id , c where c.k = c.id"),
                 generated);
      assertRuns(generated);

      // after the table is joined
      generated = generate(editor("a.id = b.id", "b.id = c.id", "c.k = c.id"), true);
      assertTrue(generated.endsWith("INNER JOIN c ON b.id = c.id AND c.k = c.id"), generated);
      assertRuns(generated);
   }

   @Test
   void unrelatedJoinsAreNotChanged() throws Exception {
      String generated = generate(editor("a.id = b.id", "b.id = c.id", "a.k = c.k", "d.id = e.id"),
                                  true);
      assertTrue(generated.endsWith("INNER JOIN c ON a.k = c.k AND b.id = c.id , " +
                                    "d INNER JOIN e ON d.id = e.id"), generated);
      assertRuns(generated);

      // no cycle, the joins of one pair are ANDed in one ON
      generated = generate(editor("a.id = b.id", "a.k = b.k", "b.id = c.id"), true);
      assertTrue(generated.endsWith("from (a INNER JOIN b ON a.id = b.id AND a.k = b.k ) " +
                                    "INNER JOIN c ON b.id = c.id"), generated);

      // the comma and where form has no cycle problem
      generated = generate(editor("a.id = b.id", "b.id = c.id", "a.k = c.k"), false);
      assertTrue(generated.endsWith("from a, b, c where a.id = b.id and b.id = c.id and a.k = c.k"),
                 generated);
   }

   @Test
   void joinUnderOrIsNotWrittenAsAnAnd() throws Exception {
      // the join under the or is flattened into the from clause (#77479). Writing the cycle as
      // a condition would turn the or into an and and return wrong rows, so the sql must
      // either return the same rows as the text or fail
      String text = COLS + "from a left join b on a.id = b.id join c on b.id = c.id and " +
         "(a.k = c.k or c.k is null), d";

      for(boolean legacy : new boolean[] { false, true }) {
         for(boolean ansiJoin : new boolean[] { false, true }) {
            String generated = generate(parse(text, legacy), ansiJoin);
            List<String> mismatch = null;

            try {
               mismatch = rowMismatch(text, generated);
            }
            catch(SQLException ignore) {
               // the sql fails, which is not a silent wrong result
            }

            assertNull(mismatch, "legacy=" + legacy + " ansiJoin=" + ansiJoin + "\ngenerated: " +
               generated + "\n" + mismatch);
         }
      }
   }

   @Test
   void whereConditionsAreInTheWhereClauseOfTheHelper() {
      // appendLimitClause (Oracle, VPM) looks for generateWhereClause() in the generated sql
      UniformSQL sql = editor("a.id = b.id", "a.id *= c.id", "b.k = c.k");
      SQLHelper helper = new SQLHelper();
      helper.setUniformSql(sql);
      helper.setAnsiJoin(true);
      String generated = helper.generateSentence();
      String where = helper.generateWhereClause();

      assertEquals("where b.k = c.k", normalize(where));
      assertTrue(generated.contains(where), generated);
   }

   @Test
   void oracleRowLimitKeepsTheWhereConditions() {
      UniformSQL sql = editor("a.id = b.id", "a.id *= c.id", "b.k = c.k");
      sql.setVPMCondition(true);
      OracleSQLHelper helper = new OracleSQLHelper();
      helper.setUniformSql(sql);
      helper.setAnsiJoin(true);
      helper.setInputMaxRows(5);
      String generated = normalize(helper.generateSentence());

      assertTrue(generated.endsWith("LEFT OUTER JOIN c ON a.id = c.id where (b.k = c.k) AND " +
                                    "rownum <= 5"), generated);
   }

   @BeforeAll
   static void createTables() throws SQLException {
      for(String url : new String[] { DB + ";create=true", HSQLDB }) {
         try(Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement())
         {
            for(String table : TABLES) {
               stmt.executeUpdate("create table " + table + " (id int, k int)");
            }
         }
      }
   }

   @AfterAll
   static void dropDatabase() throws SQLException {
      try(Connection conn = DriverManager.getConnection(HSQLDB);
          Statement stmt = conn.createStatement())
      {
         stmt.execute("shutdown");
      }

      try {
         DriverManager.getConnection(DB + ";drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   private static final String[] TABLES = { "a", "b", "c", "d", "e" };

   /**
    * Runs both queries on random data with nulls, and requires the same rows from both.
    */
   private static void assertSameRows(String expected, String generated) throws SQLException {
      List<String> mismatch = rowMismatch(expected, generated);
      assertNull(mismatch, "expected: " + expected + "\ngenerated: " + generated + "\n" + mismatch);
   }

   // the rows of the first dataset that differs, or null if every dataset is the same
   private static List<String> rowMismatch(String expected, String generated) throws SQLException {
      return rowMismatch(DB, expected, generated);
   }

   private static List<String> rowMismatch(String url, String expected, String generated)
      throws SQLException
   {
      Random random = new Random(77489);

      try(Connection conn = DriverManager.getConnection(url)) {
         for(int i = 0; i < 200; i++) {
            fillTables(conn, random);
            List<String> rows1 = rows(conn, expected);
            List<String> rows2 = rows(conn, generated);

            if(!rows1.equals(rows2)) {
               return List.of("dataset " + i, rows1.toString(), rows2.toString());
            }
         }
      }

      return null;
   }

   // check if a table is in the from clause more than once
   private static boolean hasDuplicateTable(String sql) {
      java.util.regex.Matcher matcher =
         java.util.regex.Pattern.compile("(?:from \\(*|JOIN )(\\w+)").matcher(sql);
      Set<String> tables = new HashSet<>();

      while(matcher.find()) {
         if(!tables.add(matcher.group(1))) {
            return true;
         }
      }

      return false;
   }

   private static void assertRuns(String sql) throws SQLException {
      try(Connection conn = DriverManager.getConnection(DB)) {
         fillTables(conn, new Random(77489));
         rows(conn, sql);
      }
   }

   private static void fillTables(Connection conn, Random random) throws SQLException {
      try(Statement stmt = conn.createStatement()) {
         for(String table : TABLES) {
            stmt.executeUpdate("delete from " + table);
         }
      }

      for(String table : TABLES) {
         try(PreparedStatement insert =
                conn.prepareStatement("insert into " + table + " values (?, ?)"))
         {
            int count = random.nextInt(5);

            for(int i = 0; i < count; i++) {
               for(int column = 1; column <= 2; column++) {
                  int value = random.nextInt(4);

                  if(value == 0) {
                     insert.setNull(column, Types.INTEGER);
                  }
                  else {
                     insert.setInt(column, value);
                  }
               }

               insert.addBatch();
            }

            // hsqldb rejects an empty batch
            if(count > 0) {
               insert.executeBatch();
            }
         }
      }
   }

   // the result rows as a sorted multiset
   private static List<String> rows(Connection conn, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Statement stmt = conn.createStatement(); ResultSet result = stmt.executeQuery(query)) {
         int columns = result.getMetaData().getColumnCount();

         while(result.next()) {
            StringBuilder row = new StringBuilder();

            for(int i = 1; i <= columns; i++) {
               row.append(result.getObject(i)).append('|');
            }

            rows.add(row.toString());
         }
      }

      Collections.sort(rows);
      return rows;
   }

   /**
    * Build a query the way the query editor or a data model does, with no recorded join
    * clauses.
    * @param joins the joins, e.g. "a.id *= b.id".
    */
   private static UniformSQL editor(String... joins) {
      Set<String> tables = new TreeSet<>();

      for(String join : joins) {
         String[] parts = join.split(" ");
         tables.add(parts[0].substring(0, parts[0].indexOf('.')));
         tables.add(parts[2].substring(0, parts[2].indexOf('.')));
      }

      UniformSQL sql = new UniformSQL();
      JDBCSelection selection = new JDBCSelection();

      for(String table : tables) {
         selection.addColumn(table + ".id");
      }

      sql.setSelection(selection);

      for(String table : tables) {
         sql.addTable(table);
      }

      for(String join : joins) {
         String[] parts = join.split(" ");
         sql.addJoin(new XJoin(new XExpression(parts[0], XExpression.FIELD),
                               new XExpression(parts[2], XExpression.FIELD), parts[1]));
      }

      return sql;
   }

   // give the joins a join order (as a data model does), so they are walked in that order
   private static void setOrders(UniformSQL sql) {
      XJoin[] joins = sql.getJoins();

      for(int i = 0; i < joins.length; i++) {
         joins[i].setOrder(i + 1);
      }
   }

   private static String generate(UniformSQL sql, boolean ansiJoin) {
      SQLHelper helper = new SQLHelper();
      helper.setUniformSql(sql);
      helper.setAnsiJoin(ansiJoin);
      return normalize(helper.generateSentence());
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   /**
    * A parsed query with its sql string, like one loaded from storage.
    * @param legacy true to clear the recorded join clauses.
    */
   private static UniformSQL savedQuery(String text, boolean legacy) throws Exception {
      // the structure is parsed (not on the parser pool) and the text is kept, as in a saved query
      UniformSQL sql = parse(text, legacy);
      sql.setSQLString(text, false);
      return sql;
   }

   /**
    * Parse sql text.
    * @param legacy true to clear the recorded join clauses, like a query saved before they
    *               were recorded.
    */
   private static UniformSQL parse(String text, boolean legacy) throws Exception {
      UniformSQL sql = new UniformSQL();
      // a RIGHT or FULL join mixed with an inner join is refused without a data source
      sql.setDataSource(GenericJDBCDataSource.create());
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);

      if(legacy) {
         for(XJoin join : sql.getJoins()) {
            join.setJoinClause(XJoin.UNKNOWN_CLAUSE);
         }
      }

      return sql;
   }
}
