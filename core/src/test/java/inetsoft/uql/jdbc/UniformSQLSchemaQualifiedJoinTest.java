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
import inetsoft.uql.XNode;
import inetsoft.uql.util.XUtil;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77518, a join between schema-qualified, unaliased tables whose condition qualifies the
 * columns with the bare table names (from s.a left join s.b on a.id = b.id) must be recorded
 * between the FROM tables s.a and s.b, so XJoin.getTable1/2(sql) return s.a and s.b instead of
 * a and b, which are not FROM tables.
 * <p>
 * Before #6034 the reported query regenerated as "from a LEFT OUTER JOIN b .., s.a, s.b". Since
 * #6034, SQL generation resolves a bare name to the FROM table itself (getJoinTableIndex), so
 * most of the SQL expectations here also hold on main, and those tests pin getTable1/2(sql)
 * through the joins() assertions. The SQL differs from main for:
 * <ul>
 *    <li>a legacy WHERE outer join with mixed qualifiers, whose WHERE comparison main moves into
 *    the ON (wrong rows), see whereOuterJoinMixedQualifiersKeepFilterInWhere;</li>
 *    <li>the mixed-qualifier ON (and c.s.a/c.s.b qualified by s.a/s.b), which main refuses.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, CredentialService.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLSchemaQualifiedJoinTest {
   private static final String DEFAULT = "default";
   private static final String POSTGRESQL = "postgresql";
   private static final String POSTGRESQL_ANSI = "postgresql-ansi";
   private static final String ORACLE = "oracle";
   private static final String ORACLE_ANSI = "oracle-ansi";

   @ParameterizedTest
   @ValueSource(strings = {
      "select * from s.a left join s.b on a.id = b.id",
      "select * from s.a left join s.b on b.id = a.id"
   })
   void leftJoinUsesFromTables(String text) throws Exception {
      UniformSQL sql = parse(text, DEFAULT);
      assertEquals(List.of("s.a *= s.b"), joins(sql));
      assertEquals("select * from s.a LEFT OUTER JOIN s.b ON a.id = b.id", regenerate(sql));
      assertRoundTrip(sql, DEFAULT);
   }

   // the WHERE comparison is between the outer joined tables s.a and s.b, so it is stored as a
   // plain condition rather than an XJoin (#77478) and stays in the where clause, where it still
   // drops the null extended rows. That needs the ON's bare qualifiers resolved to s.a and s.b
   @Test
   void reportedQuery() throws Exception {
      UniformSQL sql = parse(
         "select * from s.a left join s.b on a.id = b.id where s.a.k = s.b.k", DEFAULT);
      assertEquals(List.of("s.a *= s.b"), joins(sql));
      assertEquals(List.of("s.a.k = s.b.k"), conditions(sql));
      assertEquals("select * from s.a LEFT OUTER JOIN s.b ON a.id = b.id where s.a.k = s.b.k",
                   regenerate(sql));
      assertRoundTrip(sql, DEFAULT);
   }

   // a WHERE (or later inner join ON) comparison between the outer joined tables, written with
   // any mix of bare and schema qualifiers, stays a filter after the outer join (#77478)
   static Stream<Arguments> outerPairComparisons() {
      return Stream.of(
         Arguments.of(DEFAULT, "select * from s.a left join s.b on b.id = a.id where s.a.k = s.b.k",
                      "select * from s.a LEFT OUTER JOIN s.b ON a.id = b.id where s.a.k = s.b.k"),
         Arguments.of(DEFAULT, "select * from s.a left join s.b on a.id = b.id where a.k = b.k",
                      "select * from s.a LEFT OUTER JOIN s.b ON a.id = b.id where a.k = b.k"),
         Arguments.of(DEFAULT, "select * from s.a left join s.b on a.id = b.id where s.a.k = b.k",
                      "select * from s.a LEFT OUTER JOIN s.b ON a.id = b.id where s.a.k = b.k"),
         Arguments.of(DEFAULT,
                      "select * from s.a left join s.b on a.id = b.id join s.c on c.id = a.id " +
                      "and a.k = b.k",
                      "select * from (s.a LEFT OUTER JOIN s.b ON a.id = b.id ) INNER JOIN s.c " +
                      "ON c.id = a.id where a.k = b.k"),
         Arguments.of(ORACLE, "select * from s.a left join s.b on b.id = a.id where s.a.k = s.b.k",
                      "select * from s.a, s.b where a.id = b.id(+) and s.a.k = s.b.k"));
   }

   @ParameterizedTest
   @MethodSource("outerPairComparisons")
   void outerPairComparisonStaysInWhere(String helper, String text, String expected)
      throws Exception
   {
      UniformSQL sql = parse(text, helper);
      assertEquals(List.of("s.a *= s.b"), joins(sql).stream()
         .filter(join -> join.startsWith("s.a ") && join.endsWith(" s.b")).toList());
      assertEquals(1, conditions(sql).size());
      assertEquals(expected, regenerate(sql));
      assertRoundTrip(sql, helper);
   }

   // a legacy WHERE outer join ((+) or *=) between schema-qualified tables, with a WHERE
   // comparison between the same tables that uses the other qualifier spelling. The
   // comparison is on the outer joined pair, so it must stay a filter after the join (#77478).
   // Without the bare qualifiers resolved to s.a and s.b, it is not recognized as on the
   // pair and is folded into the ON, which keeps the null extended rows it should drop
   static Stream<Arguments> whereOuterJoinMixedQualifiers() {
      String id = "select * from s.a LEFT OUTER JOIN s.b ON a.id = b.id where s.a.k = s.b.k";
      String schemaId = "select * from s.a LEFT OUTER JOIN s.b ON s.a.id = s.b.id where a.k = b.k";
      return Stream.of(
         Arguments.of(ORACLE_ANSI, "select * from s.a, s.b where a.id = b.id(+) and s.a.k = s.b.k", id),
         Arguments.of(ORACLE_ANSI, "select * from s.a, s.b where s.a.k = s.b.k and a.id = b.id(+)", id),
         Arguments.of(ORACLE_ANSI, "select * from s.a, s.b where s.a.id = s.b.id(+) and a.k = b.k", schemaId),
         Arguments.of(ORACLE_ANSI, "select * from s.a, s.b where a.k = b.k and s.a.id = s.b.id(+)", schemaId),
         Arguments.of(DEFAULT, "select * from s.a, s.b where a.id = b.id(+) and s.a.k = s.b.k", id),
         Arguments.of(DEFAULT, "select * from s.a, s.b where a.id *= b.id and s.a.k = s.b.k", id),
         Arguments.of(DEFAULT, "select * from s.a, s.b where s.a.k = s.b.k and a.id *= b.id", id),
         Arguments.of(DEFAULT, "select * from s.a, s.b where s.a.id *= s.b.id and a.k = b.k", schemaId),
         Arguments.of(DEFAULT, "select * from s.a, s.b where a.k = b.k and s.a.id *= s.b.id", schemaId));
   }

   @ParameterizedTest
   @MethodSource("whereOuterJoinMixedQualifiers")
   void whereOuterJoinMixedQualifiersKeepFilterInWhere(String helper, String text, String expected)
      throws Exception
   {
      // a where clause outer join is refused with a data source that writes ANSI joins (Bug
      // #77548), so its structure is parsed without one, and generated with the data source
      if(dataSource(helper) != null) {
         assertThrows(antlr.SemanticException.class, () -> parse(text, helper));
      }

      UniformSQL sql = parse(text, DEFAULT);
      sql.setDataSource(dataSource(helper));

      // the sql first: it is what runs, and what main got wrong
      assertEquals(expected, regenerate(sql));
      assertRoundTrip(sql, helper);
      assertEquals(List.of("s.a *= s.b"), joins(sql));
      assertEquals(1, conditions(sql).size());
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "select * from s.a right join s.b on a.id = b.id",
      "select * from s.a right join s.b on b.id = a.id"
   })
   void rightJoinUsesFromTables(String text) throws Exception {
      UniformSQL sql = parse(text, DEFAULT);
      assertEquals(List.of("s.a =* s.b"), joins(sql));
      assertEquals("select * from s.a RIGHT OUTER JOIN s.b ON a.id = b.id", regenerate(sql));
      assertRoundTrip(sql, DEFAULT);
   }

   static Stream<Arguments> otherQualifiers() {
      return Stream.of(
         // one side qualified as written in FROM
         Arguments.of("select * from s.a left join s.b on s.a.id = b.id",
                      "s.a *= s.b", "select * from s.a LEFT OUTER JOIN s.b ON s.a.id = b.id"),
         // quoted names keep their quotes (#77569)
         Arguments.of("select * from \"s\".\"a\" left join \"s\".\"b\" on \"a\".id = \"b\".id",
                      "s.a *= s.b",
                      "select * from \"s\".\"a\" LEFT OUTER JOIN \"s\".\"b\" ON \"a\".id = \"b\".id"),
         // catalog.schema.table qualified by the table or by schema.table
         Arguments.of("select * from c.s.a left join c.s.b on a.id = b.id",
                      "c.s.a *= c.s.b", "select * from c.s.a LEFT OUTER JOIN c.s.b ON a.id = b.id"),
         Arguments.of("select * from c.s.a left join c.s.b on b.id = a.id",
                      "c.s.a *= c.s.b", "select * from c.s.a LEFT OUTER JOIN c.s.b ON a.id = b.id"),
         Arguments.of("select * from c.s.a left join c.s.b on s.a.id = s.b.id",
                      "c.s.a *= c.s.b",
                      "select * from c.s.a LEFT OUTER JOIN c.s.b ON s.a.id = s.b.id"),
         // several columns
         Arguments.of("select * from s.a left join s.b on a.id = b.id and a.k = b.k",
                      "s.a *= s.b",
                      "select * from s.a LEFT OUTER JOIN s.b ON a.id = b.id AND a.k = b.k")
      );
   }

   @ParameterizedTest
   @MethodSource("otherQualifiers")
   void otherQualifiersUseFromTables(String text, String join, String expected) throws Exception {
      UniformSQL sql = parse(text, DEFAULT);
      assertFalse(joins(sql).isEmpty());

      for(String recorded : joins(sql)) {
         assertEquals(join, recorded);
      }

      assertEquals(expected, regenerate(sql));
      assertRoundTrip(sql, DEFAULT);
   }

   @Test
   void chainedJoinsUseFromTables() throws Exception {
      UniformSQL sql = parse(
         "select * from s.a left join s.b on a.id = b.id join s.c on b.id = c.id", DEFAULT);
      assertEquals(List.of("s.a *= s.b", "s.b = s.c"), joins(sql));
      assertEquals("select * from (s.a LEFT OUTER JOIN s.b ON a.id = b.id ) " +
                   "INNER JOIN s.c ON b.id = c.id", regenerate(sql));
      assertRoundTrip(sql, DEFAULT);

      sql = parse("select * from s.a left join s.b on b.id = a.id left join s.c on c.id = b.id",
                  DEFAULT);
      assertEquals(List.of("s.a *= s.b", "s.b *= s.c"), joins(sql));
      assertEquals("select * from (s.a LEFT OUTER JOIN s.b ON a.id = b.id ) " +
                   "LEFT OUTER JOIN s.c ON b.id = c.id", regenerate(sql));
      assertRoundTrip(sql, DEFAULT);
   }

   // the two qualifiers name the same tables, so this is one outer join, not two (it failed with
   // "Unsupported outer join condition")
   @Test
   void mixedQualifiersParse() throws Exception {
      UniformSQL sql = parse(
         "select * from s.a left join s.b on a.id = b.id and s.a.k = s.b.k", DEFAULT);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals(List.of("s.a *= s.b", "s.a *= s.b"), joins(sql));
      assertEquals("select * from s.a LEFT OUTER JOIN s.b ON a.id = b.id AND s.a.k = s.b.k",
                   regenerate(sql));
      assertRoundTrip(sql, DEFAULT);
   }

   static Stream<Arguments> postgresql() {
      return Stream.of(
         Arguments.of("select * from s.a left join s.b on a.id = b.id",
                      "select * from \"s\".\"a\" LEFT OUTER JOIN \"s\".\"b\" ON \"a\".\"id\" = \"b\".\"id\""),
         Arguments.of("select * from s.a left join s.b on b.id = a.id",
                      "select * from \"s\".\"a\" LEFT OUTER JOIN \"s\".\"b\" ON \"a\".\"id\" = \"b\".\"id\""),
         Arguments.of("select * from s.a left join s.b on s.a.id = b.id",
                      "select * from \"s\".\"a\" LEFT OUTER JOIN \"s\".\"b\" ON \"s\".\"a\".\"id\" = \"b\".\"id\""),
         Arguments.of("select * from c.s.a left join c.s.b on b.id = a.id",
                      "select * from \"c\".\"s\".\"a\" LEFT OUTER JOIN \"c\".\"s\".\"b\" ON \"a\".\"id\" = \"b\".\"id\""),
         Arguments.of("select * from a left join s.a on a.id = s.a.id",
                      "select * from \"a\" LEFT OUTER JOIN \"s\".\"a\" ON \"a\".\"id\" = \"s\".\"a\".\"id\"")
      );
   }

   // PostgreSQL stores unaliased tables quoted ("s"."a")
   @ParameterizedTest
   @MethodSource("postgresql")
   void postgresqlUsesFromTables(String text, String expected) throws Exception {
      for(String helper : new String[] { POSTGRESQL, POSTGRESQL_ANSI }) {
         UniformSQL sql = parse(text, helper);
         assertEquals(expected, regenerate(sql), helper);
         assertRoundTrip(sql, helper);
      }
   }

   // with ANSI joins on, PostgreSQL writes inner joins in FROM too
   @Test
   void postgresqlAnsiInnerJoinUsesFromTables() throws Exception {
      UniformSQL sql = parse("select * from s.a join s.b on a.id = b.id", POSTGRESQL_ANSI);
      assertEquals("select * from \"s\".\"a\" INNER JOIN \"s\".\"b\" ON \"a\".\"id\" = \"b\".\"id\"",
                   regenerate(sql));
      assertRoundTrip(sql, POSTGRESQL_ANSI);
   }

   static Stream<Arguments> oracle() {
      return Stream.of(
         Arguments.of("select * from s.a left join s.b on a.id = b.id",
                      "select * from s.a, s.b where a.id = b.id(+)"),
         Arguments.of("select * from s.a left join s.b on b.id = a.id",
                      "select * from s.a, s.b where a.id = b.id(+)"),
         Arguments.of("select * from s.a right join s.b on b.id = a.id",
                      "select * from s.a, s.b where a.id (+)= b.id"),
         Arguments.of("select * from c.s.a left join c.s.b on b.id = a.id",
                      "select * from c.s.a, c.s.b where a.id = b.id(+)"),
         Arguments.of("select * from s.a left join s.b on b.id = a.id left join s.c on c.id = b.id",
                      "select * from s.a, s.b, s.c where a.id = b.id(+) and b.id = c.id(+)")
      );
   }

   // Oracle without ANSI joins writes (+) on the null-supplying side b, for either ON
   // orientation
   @ParameterizedTest
   @MethodSource("oracle")
   void oraclePreservesLeftTable(String text, String expected) throws Exception {
      UniformSQL sql = parse(text, ORACLE);
      assertEquals(expected, regenerate(sql));
      assertRoundTrip(sql, ORACLE);
   }

   static Stream<Arguments> selfJoins() {
      return Stream.of(
         // the same table preserved and joined under an alias, qualified by its bare name, by
         // its name as written in FROM, or without a schema, in both ON orientations
         Arguments.of("select * from s.a left join s.a x on a.id = x.pid", "s.a *= x",
                      "select * from s.a LEFT OUTER JOIN s.a x ON a.id = x.pid"),
         Arguments.of("select * from s.a left join s.a x on x.pid = a.id", "s.a *= x",
                      "select * from s.a LEFT OUTER JOIN s.a x ON a.id = x.pid"),
         Arguments.of("select * from s.a right join s.a x on a.id = x.pid", "s.a =* x",
                      "select * from s.a RIGHT OUTER JOIN s.a x ON a.id = x.pid"),
         Arguments.of("select * from s.a right join s.a x on x.pid = a.id", "s.a =* x",
                      "select * from s.a RIGHT OUTER JOIN s.a x ON a.id = x.pid"),
         Arguments.of("select * from s.a left join s.a x on s.a.id = x.pid", "s.a *= x",
                      "select * from s.a LEFT OUTER JOIN s.a x ON s.a.id = x.pid"),
         Arguments.of("select * from a left join a x on a.id = x.pid", "a *= x",
                      "select * from a LEFT OUTER JOIN a x ON a.id = x.pid"),
         Arguments.of("select * from a left join a x on x.pid = a.id", "a *= x",
                      "select * from a LEFT OUTER JOIN a x ON a.id = x.pid"),
         // the alias on the preserved side
         Arguments.of("select * from s.a x left join s.a on x.pid = a.id", "x *= s.a",
                      "select * from s.a x LEFT OUTER JOIN s.a ON x.pid = a.id"),
         Arguments.of("select * from s.a x left join s.a on a.id = x.pid", "x *= s.a",
                      "select * from s.a x LEFT OUTER JOIN s.a ON x.pid = a.id")
      );
   }

   // a self-join keeps the preserved side when the bare qualifier a resolves to s.a. The SQL is
   // the same on main, which orients outer joins by FROM table index (#6034), so these pin
   // the join pairs recorded with getTable1/2(sql)
   @ParameterizedTest
   @MethodSource("selfJoins")
   void selfJoinKeepsPreservedSide(String text, String join, String expected) throws Exception {
      UniformSQL sql = parse(text, DEFAULT);
      assertEquals(List.of(join), joins(sql));
      assertEquals(expected, regenerate(sql));
      assertRoundTrip(sql, DEFAULT);
   }

   static Stream<Arguments> selfJoinDialects() {
      return Stream.of(
         Arguments.of(ORACLE, "select * from s.a left join s.a x on a.id = x.pid",
                      "select * from s.a, s.a x where a.id = x.pid(+)"),
         Arguments.of(ORACLE, "select * from s.a left join s.a x on x.pid = a.id",
                      "select * from s.a, s.a x where a.id = x.pid(+)"),
         Arguments.of(ORACLE, "select * from s.a right join s.a x on a.id = x.pid",
                      "select * from s.a, s.a x where a.id (+)= x.pid"),
         Arguments.of(ORACLE, "select * from s.a right join s.a x on x.pid = a.id",
                      "select * from s.a, s.a x where a.id (+)= x.pid"),
         Arguments.of(ORACLE, "select * from s.a left join s.a x on s.a.id = x.pid",
                      "select * from s.a, s.a x where s.a.id = x.pid(+)"),
         Arguments.of(ORACLE, "select * from a left join a x on x.pid = a.id",
                      "select * from a, a x where a.id = x.pid(+)"),
         Arguments.of(POSTGRESQL_ANSI, "select * from s.a left join s.a x on a.id = x.pid",
                      "select * from \"s\".\"a\" LEFT OUTER JOIN \"s\".\"a\" x ON \"a\".\"id\" = \"x\".\"pid\""),
         Arguments.of(POSTGRESQL_ANSI, "select * from s.a left join s.a x on x.pid = a.id",
                      "select * from \"s\".\"a\" LEFT OUTER JOIN \"s\".\"a\" x ON \"a\".\"id\" = \"x\".\"pid\""),
         Arguments.of(POSTGRESQL_ANSI, "select * from s.a right join s.a x on x.pid = a.id",
                      "select * from \"s\".\"a\" RIGHT OUTER JOIN \"s\".\"a\" x ON \"a\".\"id\" = \"x\".\"pid\"")
      );
   }

   @ParameterizedTest
   @MethodSource("selfJoinDialects")
   void selfJoinDialectKeepsPreservedSide(String helper, String text, String expected)
      throws Exception
   {
      UniformSQL sql = parse(text, helper);
      assertEquals(expected, regenerate(sql));
      assertRoundTrip(sql, helper);
   }

   // the joins of a parenthesized joined table on the right keep the preserved side when the
   // ON's bare qualifier a names the preserved s.a. The ANSI output is not round-trip stable
   // (a re-parse writes the ON operands as x.pid = a.id), the same as for aliased tables on
   // main, so only the joins and the SQL are checked. Only a PostgreSQL ANSI data source
   // accepts this shape: #77434 refuses it without a data source, and on Oracle, whose
   // "x.id = b.id and a.id = x.pid(+)" drops the null extended rows of s.a
   static Stream<Arguments> parenthesizedSelfJoins() {
      return Stream.of(
         Arguments.of(POSTGRESQL_ANSI,
                      "select * from s.a left join (s.a x join s.b on x.id = b.id) on a.id = x.pid",
                      "select * from (\"s\".\"a\" x INNER JOIN \"s\".\"b\" ON \"x\".\"id\" = " +
                         "\"b\".\"id\" ) RIGHT OUTER JOIN \"s\".\"a\" ON \"a\".\"id\" = \"x\".\"pid\""),
         Arguments.of(POSTGRESQL_ANSI,
                      "select * from s.a left join (s.b join s.a x on x.id = b.id) on a.id = x.pid",
                      // Bug #77546, the nested join's tables are in from order
                      "select * from (\"s\".\"b\" INNER JOIN \"s\".\"a\" x ON \"x\".\"id\" = " +
                         "\"b\".\"id\" ) RIGHT OUTER JOIN \"s\".\"a\" ON \"a\".\"id\" = \"x\".\"pid\"")
      );
   }

   @ParameterizedTest
   @MethodSource("parenthesizedSelfJoins")
   void parenthesizedSelfJoinKeepsPreservedSide(String helper, String text, String expected)
      throws Exception
   {
      UniformSQL sql = parse(text, helper);
      assertTrue(joins(sql).contains("\"s\".\"a\" *= x"), joins(sql).toString());
      assertEquals(expected, regenerate(sql));
   }

   // an aliased joined table referenced by its alias still gets its join type
   @Test
   void aliasedTableByAliasKeepsPreservedSide() throws Exception {
      UniformSQL sql = parse("select * from s.a left join s.b y on y.id = a.id", DEFAULT);
      assertEquals(List.of("s.a *= y"), joins(sql));
      assertEquals("select * from s.a LEFT OUTER JOIN s.b y ON a.id = y.id", regenerate(sql));
   }

   // shapes that the outer join rules of #6034 refuse, so the original SQL runs as written.
   // The table name of an aliased table (s.b.id for "s.b y") and the bare name of two FROM
   // tables (a for s.a and t.a) don't name a FROM table and are invalid for the database
   // too, and a nested join on the right of an outer join is refused without a data source
   // or on Oracle (#77434), which would regenerate it with different rows
   static Stream<Arguments> refused() {
      return Stream.of(
         Arguments.of(DEFAULT, "select * from s.a left join s.b y on s.b.id = a.id",
                      "Unsupported outer join condition"),
         Arguments.of(DEFAULT, "select * from s.a left join t.a on a.id = a.k",
                      "Unsupported outer join condition"),
         Arguments.of(DEFAULT,
                      "select * from s.a left join (s.a x join s.b on x.id = b.id) on a.id = x.pid",
                      "Unsupported nested join"),
         Arguments.of(DEFAULT,
                      "select * from s.a left join (s.b join s.a x on x.id = b.id) on a.id = x.pid",
                      "Unsupported nested join"),
         Arguments.of(ORACLE,
                      "select * from s.a left join (s.a x join s.b on x.id = b.id) on a.id = x.pid",
                      "Unsupported nested join"),
         Arguments.of(ORACLE,
                      "select * from s.a left join (s.b join s.a x on x.id = b.id) on a.id = x.pid",
                      "Unsupported nested join")
      );
   }

   @ParameterizedTest
   @MethodSource("refused")
   void refusedShapeKeepsOriginalSql(String helper, String text, String message) {
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text, helper));
      assertTrue(ex.getMessage().contains(message), ex.getMessage());

      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource(helper));
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
      assertEquals(text, sql.getSQLString());

      UniformSQL fresh = new UniformSQL();
      fresh.setDataSource(dataSource(helper));
      fresh.setSQLString(text, false);
      assertEquals(text, fresh.getSQLString());

      // the lazy lossy check of a fresh object runs the grammar only, which refuses the outer
      // join condition. The nested join is refused after the parse, by the join order check
      // of UniformSQL.parse (#77434), which the lazy check doesn't run
      if(message.startsWith("Unsupported outer join condition")) {
         assertTrue(fresh.isLossy());
      }

      // a query without a data source is never merged, so check the merge on a real one
      JDBCDataSource ds = DEFAULT.equals(helper) ? h2DataSource() : dataSource(helper);
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      assertFalse(XUtil.isQueryMergeable(query));
   }

   private static JDBCDataSource h2DataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77518-h2");
      ds.setDriver("org.h2.Driver");
      ds.setURL("jdbc:h2:mem:test");
      return ds;
   }

   // a quoted name that contains a dot is one table, not schema s and table a
   @Test
   void quotedDottedNameIsNotSchemaQualified() throws Exception {
      UniformSQL sql = parse("select * from \"s.a\" left join s.b on a.id = b.id", DEFAULT);
      assertEquals(List.of("a *= s.b"), joins(sql));
   }

   // a bare name of two FROM tables (s.a and t.a) is ambiguous and stays unresolved
   @Test
   void ambiguousBareNameStaysUnresolved() throws Exception {
      UniformSQL sql = parse("select * from s.a, t.a, s.b where a.id = b.id", DEFAULT);
      assertEquals(List.of("a = s.b"), joins(sql));
      assertEquals("select * from s.a, t.a, s.b where a.id = b.id", regenerate(sql));
   }

   static Stream<Arguments> unchanged() {
      return Stream.of(
         // a FROM table named as written wins over a schema-qualified one
         Arguments.of(DEFAULT, "select * from a left join s.a on a.id = s.a.id",
                      "select * from a LEFT OUTER JOIN s.a ON a.id = s.a.id"),
         Arguments.of(DEFAULT, "select * from s.a x left join s.b y on x.id = y.id",
                      "select * from s.a x LEFT OUTER JOIN s.b y ON x.id = y.id"),
         // inner joins only, the same as before the fix
         Arguments.of(DEFAULT, "select a.id, count(b.x) from s.a, s.b where a.id = b.id " +
                         "group by a.id having count(b.x) > 1",
                      "select a.id, count(b.x) from s.a, s.b where a.id = b.id group by a.id " +
                         "having count(b.x) > 1"),
         Arguments.of(DEFAULT, "select a.ID, b.Name from s.a, s.b where a.ID = b.ID",
                      "select a.ID, b.Name from s.a, s.b where a.ID = b.ID"),
         Arguments.of(DEFAULT, "select * from s.a join s.b on a.id = b.id",
                      "select * from s.a, s.b where a.id = b.id"),
         Arguments.of(DEFAULT, "select a.id, b.id from c.s.a join c.s.b on a.id = b.id where a.k = 1",
                      "select a.id, b.id from c.s.a, c.s.b where a.id = b.id and a.k = 1"),
         Arguments.of(ORACLE, "select a.id, count(b.x) from s.a, s.b where a.id = b.id " +
                         "group by a.id having count(b.x) > 1",
                      "select A.ID, count(b.x) from s.a, s.b where a.id = b.id group by a.id " +
                         "having count(b.x) > 1"),
         Arguments.of(ORACLE, "select a.ID, b.Name from s.a, s.b where a.ID = b.ID",
                      "select A.ID, B.NAME from s.a, s.b where a.ID = b.ID"),
         Arguments.of(ORACLE, "select * from s.a join s.b on a.id = b.id",
                      "select * from s.a, s.b where a.id = b.id"),
         Arguments.of(POSTGRESQL, "select a.id, count(b.x) from s.a, s.b where a.id = b.id " +
                         "group by a.id having count(b.x) > 1",
                      "select \"a\".\"id\", count(\"b\".\"x\") from \"s\".\"a\", \"s\".\"b\" " +
                         "where \"a\".\"id\" = \"b\".\"id\" group by \"a\".\"id\" " +
                         "having count(\"b\".\"x\") > 1"),
         Arguments.of(POSTGRESQL, "select a.ID, b.Name from s.a, s.b where a.ID = b.ID",
                      "select \"a\".\"ID\", \"b\".\"Name\" from \"s\".\"a\", \"s\".\"b\" " +
                         "where \"a\".\"ID\" = \"b\".\"ID\""),
         Arguments.of(POSTGRESQL, "select * from s.a join s.b on a.id = b.id",
                      "select * from \"s\".\"a\", \"s\".\"b\" where \"a\".\"id\" = \"b\".\"id\"")
      );
   }

   @ParameterizedTest
   @MethodSource("unchanged")
   void unchangedOutput(String helper, String text, String expected) throws Exception {
      assertEquals(expected, regenerate(parse(text, helper)));
   }

   private static void assertRoundTrip(UniformSQL sql, String helper) throws Exception {
      String generated = regenerate(sql);
      assertEquals(generated, regenerate(parse(generated, helper)));
   }

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static List<String> joins(UniformSQL sql) {
      List<XJoin> list = new ArrayList<>();
      collect(sql.getWhere(), list);
      List<String> joins = new ArrayList<>();

      for(XJoin join : list) {
         joins.add(join.getTable1(sql) + " " + join.getOp() + " " + join.getTable2(sql));
      }

      return joins;
   }

   // the column comparisons stored as plain conditions instead of XJoins
   private static List<String> conditions(UniformSQL sql) {
      List<String> list = new ArrayList<>();
      collectConditions(sql.getWhere(), list);
      return list;
   }

   private static void collectConditions(Object node, List<String> list) {
      if(node instanceof XBinaryCondition cond && !(node instanceof XJoin)) {
         list.add(cond.toString());
      }
      else if(node instanceof XNode xnode) {
         for(int i = 0; i < xnode.getChildCount(); i++) {
            collectConditions(xnode.getChild(i), list);
         }
      }
   }

   private static void collect(Object node, List<XJoin> list) {
      if(node instanceof XJoin) {
         list.add((XJoin) node);
      }
      else if(node instanceof XNode xnode) {
         for(int i = 0; i < xnode.getChildCount(); i++) {
            collect(xnode.getChild(i), list);
         }
      }
   }

   private static JDBCDataSource dataSource(String helper) {
      if(DEFAULT.equals(helper)) {
         return null;
      }

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77518-" + helper);

      if(ORACLE.equals(helper) || ORACLE_ANSI.equals(helper)) {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:orcl");
         ds.setRuntimeProductName("oracle");
         // don't connect to read the version
         ds.setProductVersion("19");
         ds.setAnsiJoin(ORACLE_ANSI.equals(helper));
      }
      else {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
         ds.setRuntimeProductName("postgresql");
         ds.setAnsiJoin(POSTGRESQL_ANSI.equals(helper));
      }

      return ds;
   }

   private static UniformSQL parse(String text, String helper) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource(helper));
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }
}
