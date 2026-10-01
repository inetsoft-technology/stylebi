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
 * Bug #77518, an outer join between schema-qualified, unaliased tables whose ON qualifies the
 * columns with the bare table names (from s.a left join s.b on a.id = b.id) must be recorded
 * between the FROM tables s.a and s.b. It was recorded between a and b, which are not FROM
 * tables, so it regenerated as "from a LEFT OUTER JOIN b .., s.a, s.b", and "on b.id = a.id"
 * kept b as the preserved side (Oracle "b.id = a.id(+)").
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

   @Test
   void reportedQuery() throws Exception {
      UniformSQL sql = parse(
         "select * from s.a left join s.b on a.id = b.id where s.a.k = s.b.k", DEFAULT);
      assertEquals(List.of("s.a *= s.b", "s.a = s.b"), joins(sql));
      assertEquals("select * from s.a LEFT OUTER JOIN s.b ON a.id = b.id where s.a.k = s.b.k",
                   regenerate(sql));
      assertRoundTrip(sql, DEFAULT);
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
         // quoted names
         Arguments.of("select * from \"s\".\"a\" left join \"s\".\"b\" on \"a\".id = \"b\".id",
                      "s.a *= s.b", "select * from s.a LEFT OUTER JOIN s.b ON a.id = b.id"),
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

   // Oracle without ANSI joins writes (+) on the null-supplying side, which was b.id = a.id(+)
   // for "on b.id = a.id"
   @ParameterizedTest
   @MethodSource("oracle")
   void oraclePreservesLeftTable(String text, String expected) throws Exception {
      UniformSQL sql = parse(text, ORACLE);
      assertEquals(expected, regenerate(sql));
      assertRoundTrip(sql, ORACLE);
   }

   static Stream<Arguments> unchanged() {
      return Stream.of(
         // a bare name of two FROM tables is ambiguous and stays unresolved
         Arguments.of(DEFAULT, "select * from s.a left join t.a on a.id = a.k",
                      "select * from a LEFT OUTER JOIN a ON a.id = a.k , s.a, t.a"),
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

      if(ORACLE.equals(helper)) {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:orcl");
         ds.setRuntimeProductName("oracle");
         // don't connect to read the version
         ds.setProductVersion("19");
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
