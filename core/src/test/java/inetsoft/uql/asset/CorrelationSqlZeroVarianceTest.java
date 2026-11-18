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

import inetsoft.report.filter.CorrelationFormula;
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
 * Bug #81096: on a database without a native correlation function the pushed-down correlation is
 * covariance / (stddevp(x) * stddevp(y)). A group with one row, or with a constant x or y, has a
 * zero standard deviation, so the division fails (Derby: "Attempt to divide by zero.") and takes
 * the whole query down. The in-memory {@code CorrelationFormula} returns null for those groups,
 * so the pushed-down result must be NULL too.
 * <p>
 * Runs the generated SQL on embedded Derby (no native correlation, errors on division by zero)
 * and compares each group with the in-memory formula.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CorrelationSqlZeroVarianceTest {
   private static AggregateHelper helper(String type) {
      return new AggregateHelper() {
         @Override
         public String getColumnString(DataRef column) {
            return null;
         }

         @Override
         public String getDBType() {
            return type;
         }

         @Override
         public String getAggregateFunction(String func) {
            return null;
         }
      };
   }

   // group -> rows of {x, y}
   private static Map<Integer, double[][]> data() {
      Map<Integer, double[][]> data = new LinkedHashMap<>();
      data.put(1, new double[][] { { 4, 6 } });                           // one row
      data.put(2, new double[][] { { 5, 1 }, { 5, 2 }, { 5, 3 } });       // constant x
      data.put(3, new double[][] { { 1, 2 }, { 2, 4 }, { 3, 5 }, { 4, 9 } }); // control
      data.put(4, new double[][] { { 1, 7 }, { 2, 7 }, { 3, 7 } });       // constant y
      data.put(5, new double[][] { { 0.1, 1 }, { 0.1, 2 }, { 0.1, 3 } }); // constant inexact x
      data.put(6, new double[][] { { 1, 1 }, { 2, 2 }, { 3, 3 } });       // exact +1
      // constant inexact doubles: n * sum(x^2) - sum(x)^2 can be a tiny negative value on Derby,
      // which fails the SQRT of the variance
      data.put(7, new double[][] { { 0.7, 1 }, { 0.7, 2 }, { 0.7, 4 }, { 0.7, 8 }, { 0.7, 9 } });
      data.put(8, new double[][] { { 1, 0.3 }, { 2, 0.3 }, { 4, 0.3 }, { 7, 0.3 }, { 9, 0.3 },
                                   { 11, 0.3 }, { 12, 0.3 } });
      data.put(9, new double[][] { { 123456.789, 3 }, { 123456.789, 5 }, { 123456.789, 8 } });
      return data;
   }

   private static Double inMemory(double[][] rows) {
      CorrelationFormula f = new CorrelationFormula(1);

      for(double[] row : rows) {
         f.addValue(new Object[] { row[0], row[1] });
      }

      return (Double) f.getResult();
   }

   private static Map<Integer, Double> run(String expression) throws Exception {
      String url = "jdbc:derby:memory:b81096" + System.nanoTime() + ";create=true";
      Map<Integer, Double> result = new LinkedHashMap<>();

      try(Connection conn = DriverManager.getConnection(url)) {
         try(Statement st = conn.createStatement()) {
            st.execute("create table obs(g int, x double, y double)");
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
   void oneRowAndZeroVarianceGroupsGiveNullInsteadOfFailingTheQuery() throws Exception {
      String sql = AggregateFormula.CORRELATION.getExpression("x", "y", helper("derby"));
      assertMatchesInMemory(run(sql));
   }

   @Test
   void controlGroupsKeepTheirCorrelation() throws Exception {
      String sql = AggregateFormula.CORRELATION.getExpression("x", "y", helper("derby"));
      Map<Integer, Double> result = run(sql);
      assertEquals(inMemory(data().get(3)), result.get(3), 1.0E-9);
      assertEquals(1.0, result.get(6), 1.0E-9);
   }

   @Test
   void subExpressionIsGuardedToo() {
      String sub = AggregateFormula.CORRELATION.getExpressionSub("x", "y", helper("derby"));
      assertTrue(sub.contains("/ NULLIF(SQRT(CASE WHEN ("), sub);
      assertTrue(sub.endsWith(" END), 0)"), sub);
      assertEquals(sub, AggregateFormula.CORRELATION.getExpressionSub("x", "y", null));
   }

   @Test
   void covarianceAndVarianceTemplatesAreUnchanged() {
      String var = AggregateFormula.POPULATION_VARIANCE.getExpression("x", null, helper("derby"));
      assertFalse(var.contains("NULLIF"), var);
      assertFalse(var.contains("CASE"), var);
   }

   @Test
   void nativeCorrelationIsUsedWhenTheDialectHasOne() {
      AggregateHelper oracle = new AggregateHelper() {
         @Override
         public String getColumnString(DataRef column) {
            return null;
         }

         @Override
         public String getDBType() {
            return "oracle";
         }

         @Override
         public String getAggregateFunction(String func) {
            return "correl".equals(func) ? "corr" : null;
         }
      };

      assertEquals("corr(x, y)", AggregateFormula.CORRELATION.getExpression("x", "y", oracle));
   }
}
