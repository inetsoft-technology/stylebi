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

import inetsoft.report.filter.WeightedAverageFormula;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81182: a pushed-down weighted average whose weights sum to zero must be NULL (empty
 * cell), like the in-memory {@code WeightedAverageFormula} and the composite average, not 0.0.
 * The sub-aggregate form must not divide by zero either.
 * <p>
 * Runs the generated SQL on embedded Derby (errors on division by zero) and compares each group
 * with the in-memory formula.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WeightedAverageSqlZeroWeightTest {
   private static AggregateHelper helper() {
      return new AggregateHelper() {
         @Override
         public String getColumnString(DataRef column) {
            return null;
         }

         @Override
         public String getDBType() {
            return "derby";
         }

         @Override
         public String getAggregateFunction(String func) {
            return null;
         }
      };
   }

   // group -> rows of {x, w}
   private static Map<Integer, double[][]> data() {
      Map<Integer, double[][]> data = new LinkedHashMap<>();
      data.put(1, new double[][] { { 10, 1 }, { 20, -1 } });             // weights sum to 0
      data.put(2, new double[][] { { 10, 1 }, { 20, 3 } });              // control
      data.put(3, new double[][] { { 5, 0 }, { 7, 0 } });                // all weights 0
      data.put(4, new double[][] { { 4, 2 } });                          // one row
      return data;
   }

   private static Double inMemory(double[][] rows) {
      WeightedAverageFormula f = new WeightedAverageFormula(1);

      for(double[] row : rows) {
         f.addValue(new Object[] { row[0], row[1] });
      }

      return (Double) f.getResult();
   }

   private static Map<Integer, Double> run(String expression) throws Exception {
      String url = "jdbc:derby:memory:b81182" + System.nanoTime() + ";create=true";
      Map<Integer, Double> result = new LinkedHashMap<>();

      try(Connection conn = DriverManager.getConnection(url)) {
         try(Statement st = conn.createStatement()) {
            st.execute("create table obs(g int, x double, w double)");
         }

         try(PreparedStatement ps = conn.prepareStatement("insert into obs values(?,?,?)")) {
            for(Map.Entry<Integer, double[][]> e : data().entrySet()) {
               for(double[] row : e.getValue()) {
                  ps.setInt(1, e.getKey());
                  ps.setDouble(2, row[0]);
                  ps.setDouble(3, row[1]);
                  ps.executeUpdate();
               }
            }
         }

         try(Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                "select g, " + expression + " from obs group by g order by g"))
         {
            while(rs.next()) {
               int g = rs.getInt(1);
               double v = rs.getDouble(2);
               result.put(g, rs.wasNull() ? null : v);
            }
         }
      }

      return result;
   }

   private static void assertMatchesInMemory(Map<Integer, Double> sql) {
      for(Map.Entry<Integer, double[][]> e : data().entrySet()) {
         Double expected = inMemory(e.getValue());
         Double actual = sql.get(e.getKey());

         if(expected == null) {
            assertNull(actual, "group " + e.getKey() + " must be NULL, was " + actual);
         }
         else {
            assertNotNull(actual, "group " + e.getKey());
            assertEquals(expected, actual, 1.0E-9, "group " + e.getKey());
         }
      }
   }

   @Test
   void zeroWeightSumGivesNull() throws Exception {
      String sql = AggregateFormula.WEIGHTED_AVG.getExpression("x", "w", helper());
      Map<Integer, Double> result = run(sql);
      assertNull(result.get(1));
      assertNull(result.get(3));
      assertMatchesInMemory(result);
   }

   @Test
   void controlGroupsKeepTheirAverage() throws Exception {
      String sql = AggregateFormula.WEIGHTED_AVG.getExpression("x", "w", helper());
      Map<Integer, Double> result = run(sql);
      assertEquals(17.5, result.get(2), 1.0E-9);
      assertEquals(4.0, result.get(4), 1.0E-9);
   }

   @Test
   void expressionDoesNotFallBackToZero() {
      String sql = AggregateFormula.WEIGHTED_AVG.getExpression("x", "w", helper());
      assertFalse(sql.contains("THEN 0.0"), sql);
   }

   @Test
   void subExpressionIsGuarded() throws Exception {
      String sub = AggregateFormula.WEIGHTED_AVG.getExpressionSub("x", "w", helper());
      assertTrue(sub.contains("NULLIF("), sub);
      // the aggregate of sub-aggregates over single-column data: sumwt column / weight column
      String sql = AggregateFormula.WEIGHTED_AVG.getExpressionSub(
         "sw", "sx", helper());
      assertTrue(sql.contains("NULLIF("), sql);
   }
}
