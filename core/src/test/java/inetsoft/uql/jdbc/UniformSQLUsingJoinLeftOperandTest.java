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
import inetsoft.uql.util.XUtil;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Bug #77490, a JOIN ... USING whose left operand has several tables recorded its column
 * against the last table of the left operand. When the column is in an earlier table,
 * e.g. x of a in {@code a join b using (id) join c using (x)}, the regenerated sql (VPM
 * regenerates it, as the parse is lossy) failed with "Column 'B.X' is not in any table".
 * When several tables have the column the original is ambiguous, but the regenerated sql
 * ran. The parser has no column metadata, so the table of the column is only known for a
 * left operand of one table, or for a column of an earlier inner or RIGHT USING join of
 * the same left operand. Any other USING join of a multi-table left operand fails the
 * parse, and the original sql runs.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLUsingJoinLeftOperandTest {
   private static final String URL = "jdbc:derby:memory:usingJoinLeftOperand;create=true";
   private static Connection conn;
   private static JDBCDataSource genericSource;
   // sends every query through ANSI join generation, including inner-only joins
   private static JDBCDataSource ansiSource;

   @BeforeAll
   static void setUp() throws Exception {
      genericSource = GenericJDBCDataSource.create();
      CredentialService credentials = mock(CredentialService.class);
      when(credentials.createCredential(any(), anyBoolean()))
         .thenAnswer(inv -> new LocalPasswordCredential());

      try(MockedStatic<CredentialService> service = mockStatic(CredentialService.class)) {
         service.when(CredentialService::getInstance).thenReturn(credentials);
         ansiSource = new JDBCDataSource();
         ansiSource.setName("h2 ansi");
         ansiSource.setRuntimeProductName("h2");
         ansiSource.setAnsiJoin(true);
         // a known version, so getting the sql helper doesn't connect to the database
         ansiSource.setProductVersion("19.0");
      }

      Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver");
      conn = DriverManager.getConnection(URL);

      try(Statement st = conn.createStatement()) {
         // x is only in a and f, y only in b and e
         st.execute("create table a (id int, x int)");
         st.execute("create table b (id int, y int)");
         st.execute("create table c (x int, z int)");
         st.execute("create table d (id int, w int)");
         st.execute("create table e (y int, v int)");
         st.execute("create table f (id int, x int, u int)");
         st.execute("insert into a values (1, 10), (2, 20), (3, 30), (null, 40)");
         st.execute("insert into b values (1, 100), (2, 200), (4, 400), (null, 500)");
         st.execute("insert into c values (10, 1), (20, 2), (50, 5), (null, 6)");
         st.execute("insert into d values (1, 7), (5, 8), (null, 9)");
         st.execute("insert into e values (100, 1), (300, 3)");
         st.execute("insert into f values (1, 10, 11), (2, 99, 22), (null, 40, 33)");
      }
   }

   @AfterAll
   static void tearDown() {
      try {
         conn.close();
         DriverManager.getConnection("jdbc:derby:memory:usingJoinLeftOperand;drop=true");
      }
      catch(SQLException ignore) {
         // derby reports a successful drop as an exception
      }
   }

   static Stream<Arguments> unknownTableQueries() {
      String[] queries = {
         // reporter's example and the other join types, x is only in a
         "select a.x, b.y, c.z from a join b using (id) join c using (x)",
         "select a.x, b.y, c.z from a join b using (id) inner join c using (x)",
         "select a.x, b.y, c.z from a join b using (id) left join c using (x)",
         "select a.x, b.y, c.z from a join b using (id) left outer join c using (x)",
         "select a.x, b.y, c.z from a join b using (id) right join c using (x)",
         "select a.x, b.y, c.z from a join b using (id) full join c using (x)",
         // after an ON join and a parenthesized left operand
         "select a.x, b.y, c.z from a join b on a.id = b.id join c using (x)",
         "select a.x, b.y, c.z from a left join b on a.id = b.id join c using (x)",
         "select a.x, b.y, c.z from (a join b on a.id = b.id) join c using (x)",
         // the column is in the last table, the parser can't tell it from the shape above
         "select a.x, b.y, e.v from a join b using (id) join e using (y)",
         "select a.x, b.y, e.v from a left join b on a.id = b.id left join e using (y)",
         // the column is in several tables of the left operand, the original is ambiguous
         "select a.x, f.u, c.z from a join f on a.id = f.id join c using (x)",
         // one column of several is unknown
         "select a.x, b.y, f.u from a join b using (id) join f using (id, x)",
         // the earlier USING join of id is in a nested join, its left operand is only a
         "select b.y, c.z, d.w from c join (a join b using (id)) on c.x = a.x join d using (id)",
         // a comma-listed earlier table, a subquery and a derived table
         "select a.x, c.z, d.w from d, a join b using (id) join c using (x)",
         "select d.w from d where exists " +
            "(select 1 from a join b using (id) join c using (x) where a.id = d.id)",
         "select t.z from (select c.z from a join b using (id) join c using (x)) t",
         // an inner join filter between outer joined tables before a RIGHT USING join (#77478)
         "select a.x, b.y, c.z, d.w from a left join b on a.id = b.id " +
            "join c on c.x = a.x and b.y = c.z right join d using (id)",
         // a column of another table than an earlier USING join's
         "select a.x from a left join b using (id) join c using (k)",
         "select a.x from a left join b on a.id = b.id left join c using (k)",
         "select a.x from z join a on z.k = a.k left join b using (id)"
      };
      return withDataSources(queries);
   }

   /**
    * The table of the column is unknown, the parse fails and the original sql runs.
    */
   @ParameterizedTest
   @MethodSource("unknownTableQueries")
   void usingJoinOfUnknownTableFailsParse(String text, String source) {
      JDBCDataSource ds = dataSource(source);
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text, ds));
      assertTrue(ex.getMessage().contains("Unsupported USING join, the left operand table " +
                                          "of the column is unknown"), ex.getMessage());

      UniformSQL sql = process(text, ds);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
      assertEquals(text, sql.getSQLString());
      assertFalse(XUtil.isQueryMergeable(query(sql)));

      UniformSQL fresh = new UniformSQL();
      fresh.setSQLString(text, false);
      assertTrue(fresh.isLossy());
   }

   static Stream<Arguments> knownTableQueries() {
      String[] queries = {
         // a left operand of one table is unchanged
         "select a.x, b.y from a join b using (id) | a.id = b.id",
         "select a.x, f.u from a join f using (id, x) | a.id = f.id, a.x = f.x",
         "select t.x, b.y from (select id, x from a) t join b using (id) | t.id = b.id",
         "select a.x, b.y, c.z from c, a join b using (id) | a.id = b.id",
         // the column of an earlier inner USING join is the joined table's column
         "select a.x, b.y, d.w from a join b using (id) join d using (id) | " +
            "a.id = b.id, b.id = d.id",
         "select a.x, b.y, d.w from a join b using (id) left join d using (id) | " +
            "a.id = b.id, b.id *= d.id",
         "select a.x, b.y, d.w from (a join b using (id)) join d using (id) | " +
            "a.id = b.id, b.id = d.id",
         "select a.x, f.u, g.u from a join f using (id, x) join f g using (id, x) | " +
            "a.id = f.id, a.x = f.x, f.id = g.id, f.x = g.x",
         "select a.x, b.y, c.z, d.w from c, a join b using (id) join d using (id) | " +
            "a.id = b.id, b.id = d.id",
         // c has no id, the last table before the join is not the table of the column
         "select a.x, b.y, c.z, d.w from a join b using (id) join c on a.x = c.x " +
            "join d using (id) | a.id = b.id, a.x = c.x, b.id = d.id",
         "select c.z from c where exists " +
            "(select 1 from a join b using (id) join d using (id) where a.x = c.x) | "
      };
      return withDataSources(queries);
   }

   static Stream<Arguments> rightJoinQueries() {
      // a RIGHT join mixed with an inner join is refused without a data source (#77434)
      String[] queries = {
         // the column of a RIGHT USING join is the coalesce, which is the joined table's
         "select a.x, b.y, d.w from a right join b using (id) join d using (id) | " +
            "a.id =* b.id, b.id = d.id",
         "select a.x, b.y, d.w from a join b using (id) right join d using (id) | " +
            "a.id = b.id, b.id =* d.id",
         // an inner join filter between outer joined tables before a RIGHT USING join is
         // not a where condition (#77478)
         "select a.x, b.y, c.z, e.v, d.w from a join b using (id) left join c on c.x = a.x " +
            "join e on e.y = b.y and c.z = e.v right join d using (id) | " +
            "a.id = b.id, a.x *= c.x, e.y = b.y, c.z = e.v, b.id =* d.id"
      };

      return Arrays.stream(queries)
         .flatMap(q -> Stream.of(Arguments.of(q.split(" \\| ")[0], q.split(" \\| ")[1], "generic"),
                                 Arguments.of(q.split(" \\| ")[0], q.split(" \\| ")[1], "ansi")));
   }

   /**
    * The table of the column is known: the joins are recorded against it, and the sql
    * regenerated from the structure (as VPM does) returns the original rows and parses
    * back to itself.
    */
   @ParameterizedTest
   @MethodSource({ "knownTableQueries", "rightJoinQueries" })
   void usingJoinOfKnownTableReturnsSameRows(String text, String joins, String source)
      throws Exception
   {
      JDBCDataSource ds = dataSource(source);
      UniformSQL sql = process(text, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals(sorted(joins), sorted(joins(sql)));

      String generated = regenerate(sql);
      String expected = assertDoesNotThrow(() -> rows(text), "original: " + text);
      assertEquals(expected, rows(generated), generated);

      UniformSQL sql2 = process(generated, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql2.getParseResult(), generated);
      assertEquals(generated, regenerate(sql2));
   }

   /**
    * Derby has no FULL join, check the recorded joins only.
    */
   @ParameterizedTest
   @ValueSource(strings = { "generic", "ansi" })
   void fullUsingJoinOfKnownTable(String source) throws Exception {
      String text = "select a.x, b.y, d.w from a join b using (id) full join d using (id)";
      UniformSQL sql = process(text, dataSource(source));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals("a.id = b.id, b.id *=* d.id", joins(sql));
   }

   private static Stream<Arguments> withDataSources(String[] queries) {
      return Arrays.stream(queries).flatMap(q -> {
         String[] parts = q.split(" \\| ", -1);
         return Stream.of("none", "generic", "ansi").map(source -> parts.length == 1 ?
            Arguments.of(parts[0], source) : Arguments.of(parts[0], parts[1], source));
      });
   }

   private static JDBCDataSource dataSource(String source) {
      return "generic".equals(source) ? genericSource : "ansi".equals(source) ? ansiSource : null;
   }

   // regenerate the sql from the parsed structure, as VPM does after changing it
   private static String regenerate(UniformSQL sql) {
      UniformSQL copy = (UniformSQL) sql.clone();
      copy.clearSQLString();
      return copy.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static String joins(UniformSQL sql) {
      XJoin[] joins = sql.getJoins();
      return joins == null ? "" : Arrays.stream(joins)
         .map(j -> j.getExpression1().getValue() + " " + j.getOp() + " " +
            j.getExpression2().getValue())
         .collect(Collectors.joining(", "));
   }

   private static List<String> sorted(String joins) {
      List<String> list = new ArrayList<>(Arrays.asList(joins.split(", ")));
      Collections.sort(list);
      return list;
   }

   // parse the way setSQLString does, keeping the sql string
   private static UniformSQL process(String text, JDBCDataSource source) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(source);
      new SQLProcessor(sql).parse(text);
      return sql;
   }

   private static UniformSQL parse(String text, JDBCDataSource source) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(source);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }

   private static JDBCQuery query(UniformSQL sql) {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getDatabaseType()).thenReturn(JDBCDataSource.JDBC_ODBC);
      when(ds.getRuntimeProductName()).thenReturn("h2");
      // the merge decision runs on a clone of the query, which clones its data source
      when(ds.clone()).thenReturn(ds);

      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      return query;
   }

   // the sorted rows, by column name so a regenerated select list order doesn't matter
   private static String rows(String text) throws SQLException {
      try(Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(text)) {
         ResultSetMetaData meta = rs.getMetaData();
         int count = meta.getColumnCount();
         List<String> rows = new ArrayList<>();

         while(rs.next()) {
            List<String> row = new ArrayList<>();

            for(int i = 1; i <= count; i++) {
               row.add(meta.getColumnLabel(i) + "=" + rs.getObject(i));
            }

            Collections.sort(row);
            rows.add(row.toString());
         }

         Collections.sort(rows);
         return count + " columns " + rows;
      }
   }
}
