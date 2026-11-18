/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.asset;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79479: executes the SQL generated for DB2 population variance / standard deviation.
 * A string test cannot catch this bug, because the failure is in the arithmetic.
 *
 * <p>DB2 itself was NOT reachable. The generated expression is run on embedded Derby, which
 * shares the relevant typing rule (DECIMAL op DOUBLE gives DOUBLE, 0.0E0 is a DOUBLE literal,
 * SQRT of a negative fails) and supports CASE. This proves that the generated SQL is valid,
 * evaluates, gives exact 0 for constant DECIMAL groups, keeps full precision for non-constant
 * integer data, and never feeds SQRT a negative number, including for DOUBLE columns. It does
 * not prove DB2 10.5 decimal scale/precision rules; the real DB2 run (0.05 x11 -> 0,
 * 0.2 x7 -> 0, DISCOUNT 0.1 -> 104.6875 / 10.2317, table loads) remains for a human.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PopulationVarianceDb2ArithmeticTest {
   private static final int[] INTS = { 1, 5, 9, 14, 20, 27, 35, 44, 54, 65, 77, 90, 104, 119, 135,
                                       152 };
   private static Connection conn;

   private static AggregateHelper db2() {
      return new AggregateHelper() {
         @Override
         public String getColumnString(DataRef column) {
            return null;
         }

         @Override
         public String getDBType() {
            return "db2";
         }

         @Override
         public String getAggregateFunction(String func) {
            return null;
         }
      };
   }

   @BeforeAll
   static void setup() throws Exception {
      conn = DriverManager.getConnection("jdbc:derby:memory:pv79479;create=true");

      try(Statement st = conn.createStatement()) {
         st.execute("CREATE TABLE T (G INT, D DECIMAL(3,2), I INT, F DOUBLE)");
      }

      // group 1: 0.05 x11, 2: 0.2 x7, 3: 0.05 x1 (single row), 4: 0.1 x24 (DOUBLE column noise)
      insertConstant(1, "0.05", 11);
      insertConstant(2, "0.20", 7);
      insertConstant(3, "0.05", 1);
      insertConstant(4, "0.10", 24);

      // group 5: integers with a fractional population variance
      try(PreparedStatement ps = conn.prepareStatement("INSERT INTO T VALUES(5,0.5,?,?)")) {
         for(int v : INTS) {
            ps.setInt(1, v);
            ps.setDouble(2, v);
            ps.executeUpdate();
         }
      }

      // group 6: all NULL
      try(Statement st = conn.createStatement()) {
         st.execute("INSERT INTO T VALUES(6,NULL,NULL,NULL)");
      }
   }

   private static void insertConstant(int g, String v, int n) throws SQLException {
      try(PreparedStatement ps = conn.prepareStatement("INSERT INTO T VALUES(?,?,?,?)")) {
         for(int i = 0; i < n; i++) {
            ps.setInt(1, g);
            ps.setBigDecimal(2, new BigDecimal(v));
            ps.setInt(3, 1);
            ps.setDouble(4, Double.parseDouble(v));
            ps.executeUpdate();
         }
      }
   }

   @AfterAll
   static void teardown() throws Exception {
      conn.close();

      try {
         DriverManager.getConnection("jdbc:derby:memory:pv79479;drop=true");
      }
      catch(SQLException ignore) {
         // Derby reports a successful drop as an exception
      }
   }

   private static double eval(String expr, int group) throws SQLException {
      try(Statement st = conn.createStatement();
          ResultSet rs = st.executeQuery("SELECT " + expr + " FROM T WHERE G = " + group))
      {
         assertTrue(rs.next());
         return rs.getDouble(1);
      }
   }

   private static double variance(String col, int group) throws SQLException {
      return eval(AggregateFormula.POPULATION_VARIANCE.getExpression("T." + col, null, db2()),
                  group);
   }

   private static double stddev(String col, int group) throws SQLException {
      return eval(AggregateFormula.POPULATION_STANDARD_DEVIATION
                     .getExpression("T." + col, null, db2()), group);
   }

   @Test
   void constantDecimalGroupsGiveExactlyZero() throws Exception {
      for(int g : new int[] { 1, 2, 3 }) {
         assertEquals(0.0, variance("D", g), "variance g" + g);
         assertEquals(0.0, stddev("D", g), "stddev g" + g);
      }
   }

   @Test
   void doubleColumnNeverRaisesSqrtError() throws Exception {
      assertTrue(variance("F", 4) >= 0.0);
      double s = stddev("F", 4);
      assertTrue(s >= 0.0 && s < 1e-6, "stddev " + s);
   }

   @Test
   void fullPrecisionKeptForIntegerColumn() throws Exception {
      BigDecimal n = BigDecimal.valueOf(INTS.length);
      BigDecimal sum = BigDecimal.ZERO;
      BigDecimal sq = BigDecimal.ZERO;

      for(int v : INTS) {
         sum = sum.add(BigDecimal.valueOf(v));
         sq = sq.add(BigDecimal.valueOf((long) v * v));
      }

      double expected = sq.multiply(n).subtract(sum.multiply(sum))
         .divide(n.multiply(n), 30, RoundingMode.HALF_EVEN).doubleValue();
      assertNotEquals(Math.floor(expected * 10) / 10, expected, "data must need > 1 decimal");

      assertEquals(expected, variance("I", 5), 1e-9);
      assertEquals(Math.sqrt(expected), stddev("I", 5), 1e-9);
   }

   @Test
   void allNullGroupBehavesAsBefore() {
      // the pre-fix (unclamped) DB2 shape; the clamp must not change the NULL/NULL-denominator case
      String oldShape = "((0.0+SUM((0.0+T.D) * (0.0+T.D))) * COUNT(T.D) - ((0.0 + SUM(T.D)) * "
         + "SUM(T.D) ))/((0.0E0 + COUNT(T.D)) * COUNT(T.D))";
      assertEquals(runNullable(oldShape), runNullable(
         AggregateFormula.POPULATION_VARIANCE.getExpression("T.D", null, db2())));
   }

   private static String runNullable(String expr) {
      try(Statement st = conn.createStatement();
          ResultSet rs = st.executeQuery("SELECT " + expr + " FROM T WHERE G = 6"))
      {
         rs.next();
         double d = rs.getDouble(1);
         return rs.wasNull() ? "NULL" : String.valueOf(d);
      }
      catch(SQLException e) {
         return "ERR " + e.getSQLState();
      }
   }
}
