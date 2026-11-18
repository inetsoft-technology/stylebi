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

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81095: covariance and correlation pushed down to databases without a native function
 * must only use the rows where both columns are not null. The string tests pin the shape; the
 * arithmetic tests execute the generated SQL on embedded Derby.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AggregateFormulaCovarianceSqlTest {
   private static Connection conn;

   private static AggregateHelper helper(String type, boolean nativeFuncs) {
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
            if(nativeFuncs && "covar".equals(func)) {
               return "COVAR_POP($1, $2)";
            }

            if(nativeFuncs && "correl".equals(func)) {
               return "CORR($1, $2)";
            }

            return null;
         }
      };
   }

   @BeforeAll
   static void setup() throws Exception {
      conn = DriverManager.getConnection("jdbc:derby:memory:cv81095;create=true");

      try(Statement st = conn.createStatement()) {
         st.execute("CREATE TABLE T (G INT, X DOUBLE, Y DOUBLE)");
         st.execute("CREATE TABLE TI (G INT, X INT, Y INT)");
      }

      // case 1: one-sided null in x
      insert(1, new Double[] { 1d, null, 3d, 4d, 10d }, new Double[] { 2d, 3d, 4d, 8d, 1d });
      // case 2: nulls in both columns on different rows
      insert(2, new Double[] { 1d, null, 3d, 4d, 10d, 6d },
             new Double[] { 2d, 3d, null, 8d, 1d, 9d });
      // control: no nulls
      insert(3, new Double[] { 1d, 2d, 3d, 4d, 10d }, new Double[] { 2d, 3d, 4d, 8d, 1d });
      // no complete pair
      insert(4, new Double[] { 1d, null }, new Double[] { null, 2d });
      // single complete pair
      insert(5, new Double[] { 1d, null, 7d }, new Double[] { 2d, 3d, null });

      // integer columns with a one-sided null
      try(Statement st = conn.createStatement()) {
         st.execute("INSERT INTO TI VALUES(1,1,2),(1,NULL,3),(1,3,4),(1,4,8),(1,10,1)");
      }
   }

   private static void insert(int g, Double[] x, Double[] y) throws SQLException {
      try(PreparedStatement ps = conn.prepareStatement("INSERT INTO T VALUES(?,?,?)")) {
         for(int i = 0; i < x.length; i++) {
            ps.setInt(1, g);
            ps.setObject(2, x[i], Types.DOUBLE);
            ps.setObject(3, y[i], Types.DOUBLE);
            ps.executeUpdate();
         }
      }
   }

   @AfterAll
   static void teardown() throws Exception {
      conn.close();

      try {
         DriverManager.getConnection("jdbc:derby:memory:cv81095;drop=true");
      }
      catch(SQLException ignore) {
         // Derby reports a successful drop as an exception
      }
   }

   private static Double eval(String table, String expr, int group) throws SQLException {
      try(Statement st = conn.createStatement();
          ResultSet rs = st.executeQuery("SELECT " + expr + " FROM " + table + " WHERE G = " +
                                         group))
      {
         assertTrue(rs.next());
         double d = rs.getDouble(1);
         return rs.wasNull() ? null : d;
      }
   }

   private static Double cov(int group) throws SQLException {
      return eval("T", AggregateFormula.COVARIANCE.getExpression("X", "Y", helper("derby", false)),
                  group);
   }

   private static Double corr(int group) throws SQLException {
      return eval("T", AggregateFormula.CORRELATION.getExpression("X", "Y",
                                                                  helper("derby", false)),
                  group);
   }

   @Test
   void compositeSqlRestrictsToCompletePairs() {
      for(String type : new String[] { "derby", "mysql" }) {
         AggregateHelper h = helper(type, false);
         String cov = AggregateFormula.COVARIANCE.getExpression("x", "y", h);
         String cor = AggregateFormula.CORRELATION.getExpression("x", "y", h);

         assertTrue(cov.contains("COUNT(CASE WHEN y IS NOT NULL THEN x END)"), cov);
         assertTrue(cov.contains("SUM(CASE WHEN y IS NOT NULL THEN x END)"), cov);
         assertTrue(cov.contains("SUM(CASE WHEN x IS NOT NULL THEN y END)"), cov);
         assertTrue(cov.contains("SUM(x * y)"), cov);
         assertFalse(cov.contains("COUNT(x)"), cov);
         assertTrue(cov.contains("NULLIF("), cov);
         assertTrue(cor.contains("COUNT(CASE WHEN x IS NOT NULL THEN y END)"), cor);
         assertFalse(cor.contains("COUNT(y)"), cor);
         assertFalse(cor.contains("COUNT(x)"), cor);
      }
   }

   @Test
   void expressionArgumentsAreParenthesized() {
      String cov = AggregateFormula.COVARIANCE.getExpression("a + 1", "b - 1",
                                                             helper("mysql", false));
      assertTrue(cov.contains("COUNT(CASE WHEN (b - 1) IS NOT NULL THEN (a + 1) END)"), cov);
   }

   @Test
   void nativeFunctionsAreKept() {
      AggregateHelper h = helper("oracle", true);
      String cov = AggregateFormula.COVARIANCE.getExpression("x", "y", h);
      String cor = AggregateFormula.CORRELATION.getExpression("x", "y", h);

      assertFalse(cov.contains("CASE"), cov);
      assertFalse(cor.contains("CASE"), cor);
      assertTrue(cov.contains("COVAR_POP"), cov);
      assertTrue(cor.contains("CORR"), cor);
   }

   @Test
   void oneSidedNullInX() throws Exception {
      assertEquals(-2.875, cov(1), 1e-9);
      assertEquals(-0.31972210, corr(1), 1e-7);
   }

   @Test
   void nullsOnDifferentRows() throws Exception {
      assertEquals(-1.75, cov(2), 1e-9);
      assertEquals(-0.15140666, corr(2), 1e-7);
   }

   @Test
   void noNullsControl() throws Exception {
      assertEquals(-2.0, cov(3), 1e-9);
      assertEquals(-0.26171196, corr(3), 1e-7);
   }

   @Test
   void noCompletePairGivesNull() throws Exception {
      assertNull(cov(4));
      assertNull(corr(4));
   }

   @Test
   void singleCompletePair() throws Exception {
      assertEquals(0.0, cov(5), 1e-12);
      assertNull(corr(5));
   }

   @Test
   void integerColumnsDoNotTruncate() throws Exception {
      AggregateHelper h = helper("derby", false);
      assertEquals(-2.875, eval("TI", AggregateFormula.COVARIANCE.getExpression("X", "Y", h), 1),
                   1e-9);
      assertEquals(-0.31972210,
                   eval("TI", AggregateFormula.CORRELATION.getExpression("X", "Y", h), 1), 1e-7);
   }
}
