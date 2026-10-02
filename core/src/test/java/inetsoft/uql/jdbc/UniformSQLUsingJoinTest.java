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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77436, a JOIN ... USING keeps its LEFT, RIGHT or FULL join type. A USING join
 * that the parsed model can't represent, a nested joined table or a column that an
 * earlier LEFT or FULL USING join merged, fails the parse.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLUsingJoinTest {
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      // reporter's example and the other outer join types
      "select a.x from a left join b using (id) | a.id *= b.id",
      "select a.x from a left outer join b using (id) | a.id *= b.id",
      "select a.x from a right join b using (id) | a.id =* b.id",
      "select a.x from a right outer join b using (id) | a.id =* b.id",
      "select a.x from a full join b using (id) | a.id *=* b.id",
      "select a.x from a full outer join b using (id) | a.id *=* b.id",
      "select a.x from a LEFT JOIN b USING (id) | a.id *= b.id",
      // controls, inner joins are unchanged
      "select a.x from a join b using (id) | a.id = b.id",
      "select a.x from a inner join b using (id) | a.id = b.id",
      // several columns, aliases, schemas and a derived joined table
      "select d.x from d left join e using (id, k) | d.id *= e.id, d.k *= e.k",
      "select d.x from d full join e using (id, k) | d.id *=* e.id, d.k *=* e.k",
      "select t1.x from a t1 left join b t2 using (id) | t1.id *= t2.id",
      "select a.x from sa.a left join sa.b using (id) | sa.a.id *= sa.b.id",
      "select a.x from a left join (select id from b) t using (id) | a.id *= t.id",
      "select a.x from a join (select id from b) t using (id) | a.id = t.id",
      // a comma-listed earlier table
      "select a.x from z, a left join b using (id) | a.id *= b.id",
      // chains the model represents: after inner or RIGHT USING joins of the column. A
      // USING join of another column after a join fails the parse (Bug #77490)
      "select a.x from a join b using (id) join c using (id) | a.id = b.id, b.id = c.id",
      "select a.x from a join b using (id) left join c using (id) | a.id = b.id, b.id *= c.id",
      "select a.x from a right join b using (id) left join c using (id) | " +
         "a.id =* b.id, b.id *= c.id",
      // a merged column in another join expression of the from clause
      "select a.x from a left join b using (id), c join d using (id) | " +
         "a.id *= b.id, c.id = d.id",
      // a merged column of a subquery's join
      "select a.x from a join b using (id) where exists " +
         "(select 1 from c left join d using (id) where c.k = 1) | a.id = b.id",
      // a column merged by a subquery's join before the outer join expression is not
      // merged in the outer join expression
      "select t.z from (select b.id, c.z from b left join c using (id)) t join a using (id) | " +
         "t.id = a.id",
      "select (select max(c.z) from c left join b using (id)), a.x from a join b using (id) " +
         "join c using (id) | a.id = b.id, b.id = c.id"
   })
   void usingJoinKeepsJoinType(String text, String expected) throws Exception {
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals(expected, joins(sql));

      // the generated sql parses back to the same joins, a comma-listed join expression
      // can be generated in another order
      UniformSQL sql2 = parse(normalize(sql.getSQLString()));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql2.getParseResult());
      assertEquals(sorted(expected), sorted(joins(sql2)));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select a.x from a left join b using (id) | a LEFT OUTER JOIN b ON a.id = b.id",
      "select a.x from a right join b using (id) | a RIGHT OUTER JOIN b ON a.id = b.id",
      "select a.x from a full join b using (id) | a FULL OUTER JOIN b ON a.id = b.id",
      "select d.x from d left join e using (id, k) | " +
         "d LEFT OUTER JOIN e ON d.id = e.id AND d.k = e.k",
      "select t1.x from a t1 left join b t2 using (id) | a t1 LEFT OUTER JOIN b t2 ON t1.id = t2.id"
   })
   void usingJoinGeneratesOuterJoin(String text, String expected) throws Exception {
      String generated = normalize(parse(text).getSQLString());
      assertTrue(generated.contains(expected), generated);
      assertFalse(generated.contains(" where "), generated);
   }

   @ParameterizedTest
   @ValueSource(strings = {
      // a nested joined table, the joins would be between the nested tables only
      "select a.x from a join (b join c using (id)) using (id)",
      "select a.x from a left join (b join c using (id)) using (id)",
      "select a.x from a right join (b left join c using (id)) using (id)",
      "select a.x from a left join b join c using (id) using (id)",
      "select a.x from a join (b join c on b.id = c.id) using (id)"
   })
   void usingJoinToNestedJoinFailsCleanly(String text) {
      assertFailsCleanly(text, "the joined table is a nested join");
   }

   @ParameterizedTest
   @ValueSource(strings = {
      // the left operand's column is a.id (LEFT) or coalesce(a.id, b.id) (FULL), not b.id
      "select a.x from a left join b using (id) join c using (id)",
      "select a.x from a left join b using (id) left join c using (id)",
      "select a.x from a left outer join b using (id) right join c using (id)",
      "select a.x from a full join b using (id) join c using (id)",
      "select a.x from a full join b using (id) full join c using (id)",
      "select a.x from a left join b using (k, id) join c using (id)",
      "select a.x from a left join b using (id) join c using (k, ID)",
      "select a.x from (a left join b using (id)) left join c using (id)",
      "select a.x from a join b using (id) left join c using (id) join d using (id)",
      "select a.x from a left join b using (id) join c on b.k = c.k join d using (id)",
      "select a.x from z, a left join b using (id) join c using (id)",
      "select a.x from a where exists " +
         "(select 1 from b left join c using (id) join d using (id) where b.id = a.id)"
   })
   void usingJoinOfMergedColumnFailsCleanly(String text) {
      assertFailsCleanly(text, "the column is merged by an earlier outer join");
   }

   /**
    * A correlated subquery with an outer USING join keeps its correlation in its where
    * clause, as the ON form does (#77480), instead of joining the outer table in its
    * from clause.
    */
   @ParameterizedTest
   @ValueSource(strings = {
      "select a.x from a join b using (id) where exists " +
         "(select 1 from c left join d using (id) where c.id = a.id)",
      "select a.x from a where exists (select 1 from c left join b using (id) where c.id = a.id)"
   })
   void correlatedSubqueryWithOuterUsingJoinKeepsCorrelation(String text) throws Exception {
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());

      String generated = normalize(sql.getSQLString());
      assertTrue(generated.contains("LEFT OUTER JOIN") && generated.contains("where c.id = a.id)"),
                 generated);
      assertFalse(generated.contains("JOIN a "), generated);
   }

   /**
    * The generated sql returns the rows of the original sql on a database.
    */
   @ParameterizedTest
   @ValueSource(strings = {
      "select a.x from a left join b using (id)",
      "select a.x from a left outer join b using (id)",
      "select b.y from a right join b using (id)",
      "select t1.x from a t1 left join b t2 using (id)",
      "select d.y from b d left join c e using (id, k)",
      "select a.x, c.z from a join b using (id) left join c using (id)",
      "select b.y, c.z from a right join b using (id) left join c using (id)",
      "select a.x, t.z from (select b.id, c.z from b left join c using (id)) t left join a using (id)",
      // a correlated subquery with an outer USING join
      "select a.x from a join b using (id) where exists " +
         "(select 1 from c left join d using (id) where c.id = a.id)",
      "select a.x from a where exists (select 1 from c left join b using (id) where c.id = a.id)"
      // UniformSQLUsingJoinLeftOperandTest has an inner join filter between outer joined
      // tables before a RIGHT USING join (#77478)
   })
   void usingJoinGeneratesSameRows(String text) throws Exception {
      String generated = normalize(parse(text).getSQLString());

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77436;create=true")) {
         createTables(conn);
         assertEquals(rows(conn, text), rows(conn, generated), generated);
      }
   }

   private static void createTables(Connection conn) throws SQLException {
      try(Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "a", "b", "c", "d" }) {
            try {
               stmt.execute("drop table " + table);
            }
            catch(SQLException ignore) {
               // not created yet
            }
         }

         stmt.execute("create table a (id int, x int)");
         stmt.execute("create table b (id int, k int, y int)");
         stmt.execute("create table c (id int, k int, z int)");
         stmt.execute("create table d (id int, z int, w int)");
         stmt.execute("insert into a values (1, 10), (2, 20), (3, 30)");
         stmt.execute("insert into b values (1, 5, 100), (2, 6, 200), (4, 7, 400)");
         stmt.execute("insert into c values (1, 5, 1000), (4, 6, 4000)");
         stmt.execute("insert into d values (1, 1000, 1), (2, 9000, 2)");
      }
   }

   private static List<String> rows(Connection conn, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            StringBuilder row = new StringBuilder();

            for(int i = 1; i <= count; i++) {
               row.append(rs.getObject(i)).append(',');
            }

            rows.add(row.toString());
         }
      }

      Collections.sort(rows);
      return rows;
   }

   private static void assertFailsCleanly(String text, String message) {
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text));
      assertTrue(ex.getMessage().contains("Unsupported USING join"), ex.getMessage());
      assertTrue(ex.getMessage().contains(message), ex.getMessage());

      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
   }

   private static String joins(UniformSQL sql) {
      return Arrays.stream(sql.getJoins())
         .map(j -> j.getExpression1().getValue() + " " + j.getOp() + " " +
            j.getExpression2().getValue())
         .collect(Collectors.joining(", "));
   }

   private static List<String> sorted(String joins) {
      List<String> list = new ArrayList<>(Arrays.asList(joins.split(", ")));
      Collections.sort(list);
      return list;
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      // Bug #77434 refuses a RIGHT or FULL join mixed with an inner join without a data
      // source, as the sql helper that would generate it is unknown
      sql.setDataSource(GenericJDBCDataSource.create());
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }
}
