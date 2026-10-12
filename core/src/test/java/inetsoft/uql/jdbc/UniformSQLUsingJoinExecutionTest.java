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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77482, the SQL that the query cache normalizer leaves on a query with a JOIN ... USING
 * returns the same columns and rows as the original on a real database (in-memory Derby).
 * Before the fix the normalizer regenerated it as a comma join, so select * returned both
 * copies of the USING column and an unqualified USING column was ambiguous.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLUsingJoinExecutionTest {
   private static final String URL = "jdbc:derby:memory:usingJoinExecution;create=true";
   private static Connection conn;

   @BeforeAll
   static void createTables() throws Exception {
      Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver");
      conn = DriverManager.getConnection(URL);

      try(Statement st = conn.createStatement()) {
         st.execute("create table a(id int, k int, x int)");
         st.execute("create table b(id int, k int, y int)");
         st.execute("create table c(id int, k int, z int)");
         st.execute("insert into a values (1,1,10),(2,1,20),(3,2,30),(null,1,40)");
         st.execute("insert into b values (1,1,100),(2,2,200),(4,1,400),(null,1,500)");
         st.execute("insert into c values (1,1,1000),(3,2,3000),(4,1,4000),(5,2,5000)");
      }
   }

   @AfterAll
   static void dropDatabase() {
      try {
         conn.close();
         DriverManager.getConnection("jdbc:derby:memory:usingJoinExecution;drop=true");
      }
      catch(SQLException ignore) {
         // derby reports a successful drop as an exception
      }
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "select * from a join b using (id)",
      "select a.x, b.y from a left join b using (id)",
      "select id from a join b using (id) where id > 1",
      "select t.x from (select a.x from a left join b using (id)) t",
      "select a.x from a where a.id in (select id from b left join c using (id))"
   })
   void normalizedSqlReturnsOriginalResult(String text) throws Exception {
      JDBCQuery query = query(text);
      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      // the original must run, or a query failing on both sides would compare equal
      String expected = assertDoesNotThrow(() -> run(text), "original: " + text);
      new JDBCQueryCacheNormalizer(query);
      String executed = sql.getSQLString();
      String actual = assertDoesNotThrow(() -> run(executed), "executed: " + executed);

      assertEquals(expected, actual);
   }

   private static JDBCQuery query(String text) {
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);

      // no data source: regenerate with the generic helper, since the derby helper needs
      // beans this context lacks
      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(sql);
      return query;
   }

   // column count plus the sorted rows
   private static String run(String text) throws SQLException {
      try(Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(text)) {
         int count = rs.getMetaData().getColumnCount();
         List<String> rows = new ArrayList<>();

         while(rs.next()) {
            StringJoiner row = new StringJoiner(",");

            for(int i = 1; i <= count; i++) {
               row.add(String.valueOf(rs.getObject(i)));
            }

            rows.add(row.toString());
         }

         Collections.sort(rows);
         return count + " columns " + rows;
      }
   }
}
