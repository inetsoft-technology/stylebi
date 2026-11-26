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
 * Bug #82442: a pushed-down weighted average must skip a pair when either value is null, like the
 * in-memory {@code WeightedAverageFormula}. The weight sum used to include the weights of rows
 * whose value is null.
 * <p>
 * Runs the generated SQL on embedded Derby and compares each group with the in-memory formula.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WeightedAverageSqlNullPairTest {
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
   private static Map<Integer, Double[][]> data() {
      Map<Integer, Double[][]> data = new LinkedHashMap<>();
      data.put(1, new Double[][] { { 10.0, 1.0 }, { 20.0, 3.0 }, { null, 100.0 } }); // x null
      data.put(2, new Double[][] { { 10.0, 1.0 }, { 20.0, 3.0 }, { 30.0, null } });  // w null
      data.put(3, new Double[][] { { 10.0, 1.0 }, { 20.0, 3.0 }, { null, null } });  // both null
      data.put(4, new Double[][] { { null, 5.0 }, { null, 7.0 } });                  // all x null
      data.put(5, new Double[][] { { 10.0, 0.0 }, { null, 100.0 } });                // zero weight
      data.put(6, new Double[][] { { 10.0, 1.0 }, { 20.0, 3.0 } });                  // control
      data.put(7, new Double[][] { { null, null } });                                // all null
      return data;
   }

   private static Double inMemory(Double[][] rows) {
      WeightedAverageFormula f = new WeightedAverageFormula(1);

      for(Double[] row : rows) {
         f.addValue(new Object[] { row[0], row[1] });
      }

      return (Double) f.getResult();
   }

   private static void setNullable(PreparedStatement ps, int idx, Double v, int type)
      throws SQLException
   {
      if(v == null) {
         ps.setNull(idx, type);
      }
      else if(type == Types.INTEGER) {
         ps.setInt(idx, v.intValue());
      }
      else {
         ps.setDouble(idx, v);
      }
   }

   private static Map<Integer, Double> run(String expression, boolean ints) throws Exception {
      String url = "jdbc:derby:memory:b82442" + System.nanoTime() + ";create=true";
      String type = ints ? "int" : "double";
      int sqlType = ints ? Types.INTEGER : Types.DOUBLE;
      Map<Integer, Double> result = new LinkedHashMap<>();

      try(Connection conn = DriverManager.getConnection(url)) {
         try(Statement st = conn.createStatement()) {
            st.execute("create table obs(g int, x " + type + ", w " + type + ")");
         }

         try(PreparedStatement ps = conn.prepareStatement("insert into obs values(?,?,?)")) {
            for(Map.Entry<Integer, Double[][]> e : data().entrySet()) {
               for(Double[] row : e.getValue()) {
                  ps.setInt(1, e.getKey());
                  setNullable(ps, 2, row[0], sqlType);
                  setNullable(ps, 3, row[1], sqlType);
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
      for(Map.Entry<Integer, Double[][]> e : data().entrySet()) {
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
   void nullValueDropsItsWeight() throws Exception {
      String sql = AggregateFormula.WEIGHTED_AVG.getExpression("x", "w", helper());
      Map<Integer, Double> result = run(sql, false);
      assertEquals(17.5, result.get(1), 1.0E-9);
      assertMatchesInMemory(result);
   }

   @Test
   void everyNullCombinationMatchesInMemory() throws Exception {
      String sql = AggregateFormula.WEIGHTED_AVG.getExpression("x", "w", helper());
      Map<Integer, Double> result = run(sql, false);
      assertEquals(17.5, result.get(2), 1.0E-9);
      assertEquals(17.5, result.get(3), 1.0E-9);
      assertNull(result.get(4));
      assertNull(result.get(5));
      assertEquals(17.5, result.get(6), 1.0E-9);
      assertNull(result.get(7));
   }

   @Test
   void integerColumnsKeepFractions() throws Exception {
      String sql = AggregateFormula.WEIGHTED_AVG.getExpression("x", "w", helper());
      Map<Integer, Double> result = run(sql, true);
      assertEquals(17.5, result.get(1), 1.0E-9);
      assertMatchesInMemory(result);
   }

   @Test
   void expressionColumnsMatchInMemory() throws Exception {
      String sql = AggregateFormula.WEIGHTED_AVG.getExpression("x + 0", "w + 0", helper());
      assertMatchesInMemory(run(sql, false));
   }

   @Test
   void weightSumIsPairFiltered() {
      String sql = AggregateFormula.WEIGHTED_AVG.getExpression("x", "w", helper());
      assertTrue(sql.contains("x IS NOT NULL"), sql);
   }
}
