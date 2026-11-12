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
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.SQLHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The statistical aggregate formulas (standard deviation, variance, covariance, correlation) in
 * SQL.
 * <p>
 * A database that has a function for the formula gets the call of that function; the SQL
 * function names are checked against the manuals of the databases (sample versus population is
 * what is easy to swap). A database without one gets an expression made of {@code SUM}, {@code
 * COUNT} and arithmetic. That expression is run on the embedded Derby and compared with the
 * statistics computed in Java.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AggregateFormulaStatSqlTest {
   private static final String URL =
      "jdbc:derby:memory:aggregatestat_" + UUID.randomUUID().toString().replace("-", "");

   /** The helper of a database type, as the query builder gives it to the formula. */
   private static final class DbHelper extends AggregateHelper {
      DbHelper(String type) {
         this.type = type;
      }

      @Override
      public String getColumnString(DataRef column) {
         return column.getName();
      }

      @Override
      public String getDBType() {
         return type;
      }

      @Override
      public String getAggregateFunction(String func) {
         return SQLHelper.getSQLHelper(type).getAggregateFunction(func);
      }

      private final String type;
   }

   private static AggregateFormula formula(String field) throws ReflectiveOperationException {
      return (AggregateFormula) AggregateFormula.class.getField(field).get(null);
   }

   // ----------------------------------------------------------------------------------------
   // the SQL function of the databases that have one

   // function names of the manuals: Oracle STDDEV and VARIANCE are the sample ones, STDDEV_POP
   // and VAR_POP the population ones, CORR the correlation, COVAR_POP the population covariance;
   // MySQL STDDEV_SAMP / STDDEV_POP / VAR_SAMP / VAR_POP; SQL Server STDEV (sample), STDEVP,
   // VAR (sample), VARP; DB2 CORRELATION and COVARIANCE (population)
   @ParameterizedTest(name = "{0} {1}")
   @CsvSource(delimiter = '|', value = {
      "oracle|STANDARD_DEVIATION|stddev(x)",
      "oracle|POPULATION_STANDARD_DEVIATION|stddev_pop(x)",
      "oracle|VARIANCE|variance(x)",
      "oracle|POPULATION_VARIANCE|var_pop(x)",
      "oracle|CORRELATION|corr(x, y)",
      "oracle|COVARIANCE|covar_pop(x, y)",
      "mysql|STANDARD_DEVIATION|stddev_samp(x)",
      "mysql|POPULATION_STANDARD_DEVIATION|stddev_pop(x)",
      "mysql|VARIANCE|var_samp(x)",
      "mysql|POPULATION_VARIANCE|var_pop(x)",
      "sql server|STANDARD_DEVIATION|stdev(x)",
      "sql server|POPULATION_STANDARD_DEVIATION|stdevp(x)",
      "sql server|VARIANCE|var(x)",
      "sql server|POPULATION_VARIANCE|varp(x)",
      "db2|CORRELATION|correlation(x, y)",
      "db2|COVARIANCE|covariance(x, y)"
   })
   void nativeFunction(String database, String field, String expected) throws Exception {
      AggregateFormula formula = formula(field);
      boolean two = expected.contains(",");
      String expression = formula.getExpression("x", two ? "y" : null, new DbHelper(database));
      assertEquals(expected, expression.toLowerCase(Locale.ROOT), database + " " + field);
   }

   // the databases that do not have the function get the expression made of SUM and COUNT, the
   // same for the formula of a population as for the Derby one that is run below
   @ParameterizedTest(name = "{0} {1}")
   @CsvSource(delimiter = '|', value = {
      "mysql|CORRELATION", "mysql|COVARIANCE",
      "sql server|CORRELATION", "sql server|COVARIANCE",
      "db2|STANDARD_DEVIATION", "db2|VARIANCE", "db2|POPULATION_VARIANCE",
      "postgresql|STANDARD_DEVIATION", "postgresql|VARIANCE", "postgresql|CORRELATION",
      "informix|STANDARD_DEVIATION", "informix|COVARIANCE",
      "derby|STANDARD_DEVIATION", "derby|POPULATION_VARIANCE",
      "clickhouse|VARIANCE", "h2|VARIANCE"
   })
   void withoutNativeFunctionTheExpressionIsMadeOfSumAndCount(String database, String field)
      throws Exception
   {
      boolean two = field.equals("CORRELATION") || field.equals("COVARIANCE");
      String expression = formula(field).getExpression("x", two ? "y" : null, new DbHelper(database));

      assertTrue(expression.contains("SUM("), database + " " + field + ": " + expression);
      assertTrue(expression.contains("COUNT("), database + " " + field + ": " + expression);
   }

   // ----------------------------------------------------------------------------------------
   // the expression of Derby, run on the embedded Derby

   @BeforeAll
   static void createDatabase() throws SQLException {
      DriverManager.getConnection(URL + ";create=true").close();
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection(URL + ";drop=true").close();
      }
      catch(SQLException ex) {
         // Derby reports the drop of a database as an error
      }
   }

   /** A data set: the values of x and y, null for a missing value. */
   private record Data(String name, Double[] x, Double[] y) {
      int size() {
         return x.length;
      }
   }

   private static final Data[] DATA = {
      new Data("five rows", new Double[] {1.0, 2.0, 3.0, 4.0, 5.0},
               new Double[] {2.0, 4.0, 5.0, 4.0, 5.0}),
      new Data("negative and fractional", new Double[] {-3.5, 0.25, 8.0, 1000.0, -12.75, 0.0},
               new Double[] {10.0, -2.5, 3.125, 7.0, 0.5, -9.0}),
      new Data("two rows", new Double[] {1.0, 3.0}, new Double[] {5.0, 9.0}),
      new Data("one row", new Double[] {4.0}, new Double[] {6.0}),
      new Data("a null in x", new Double[] {1.0, null, 3.0, 4.0, 10.0},
               new Double[] {2.0, 3.0, 4.0, 8.0, 1.0}),
      // x and y miss different rows: a pair with a missing value is left out of the
      // covariance and the correlation
      new Data("nulls in different rows", new Double[] {1.0, null, 3.0, 4.0, 10.0, 6.0},
               new Double[] {2.0, 3.0, null, 8.0, 1.0, 9.0}),
      new Data("no rows", new Double[0], new Double[0]),
      new Data("only nulls", new Double[] {null, null}, new Double[] {null, null})
   };

   private static Double run(String expression, Data data) throws SQLException {
      try(Connection conn = DriverManager.getConnection(URL);
          Statement st = conn.createStatement())
      {
         st.execute("create table obs (x double, y double)");

         try {
            try(PreparedStatement ps = conn.prepareStatement("insert into obs values (?, ?)")) {
               for(int i = 0; i < data.size(); i++) {
                  setValue(ps, 1, data.x()[i]);
                  setValue(ps, 2, data.y()[i]);
                  ps.executeUpdate();
               }
            }

            try(ResultSet rs = st.executeQuery("select " + expression + " from obs")) {
               assertTrue(rs.next());
               double value = rs.getDouble(1);
               return rs.wasNull() ? null : value;
            }
         }
         finally {
            st.execute("drop table obs");
         }
      }
   }

   private static void setValue(PreparedStatement ps, int index, Double value)
      throws SQLException
   {
      if(value == null) {
         ps.setNull(index, Types.DOUBLE);
      }
      else {
         ps.setDouble(index, value);
      }
   }

   // ---- the statistics, computed in Java ----

   private static List<Double> present(Double[] values) {
      List<Double> list = new ArrayList<>();

      for(Double value : values) {
         if(value != null) {
            list.add(value);
         }
      }

      return list;
   }

   /** The sum of the squares of the differences from the mean, null when there is no value. */
   private static Double sumOfSquares(List<Double> values) {
      if(values.isEmpty()) {
         return null;
      }

      double mean = values.stream().mapToDouble(Double::doubleValue).sum() / values.size();
      return values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
   }

   private static Double sampleVariance(Double[] x) {
      List<Double> values = present(x);
      return values.size() < 2 ? null : sumOfSquares(values) / (values.size() - 1);
   }

   private static Double populationVariance(Double[] x) {
      List<Double> values = present(x);
      return values.isEmpty() ? null : sumOfSquares(values) / values.size();
   }

   /** The pairs where both values are there. */
   private static List<double[]> pairs(Data data) {
      List<double[]> list = new ArrayList<>();

      for(int i = 0; i < data.size(); i++) {
         if(data.x()[i] != null && data.y()[i] != null) {
            list.add(new double[] {data.x()[i], data.y()[i]});
         }
      }

      return list;
   }

   private static Double populationCovariance(Data data) {
      List<double[]> pairs = pairs(data);

      if(pairs.isEmpty()) {
         return null;
      }

      double mx = pairs.stream().mapToDouble(p -> p[0]).sum() / pairs.size();
      double my = pairs.stream().mapToDouble(p -> p[1]).sum() / pairs.size();
      return pairs.stream().mapToDouble(p -> (p[0] - mx) * (p[1] - my)).sum() / pairs.size();
   }

   private static Double correlation(Data data) {
      List<double[]> pairs = pairs(data);

      if(pairs.size() < 2) {
         return null;
      }

      Double cov = populationCovariance(data);
      Double vx = populationVariance(pairs.stream().map(p -> p[0]).toArray(Double[]::new));
      Double vy = populationVariance(pairs.stream().map(p -> p[1]).toArray(Double[]::new));

      // a column without any variation has no correlation
      return vx == 0.0 || vy == 0.0 ? null : cov / Math.sqrt(vx * vy);
   }

   private static void check(List<String> problems, Data data, String what, Double expected,
                             Double actual)
   {
      boolean same = expected == null ? actual == null :
         actual != null && Math.abs(actual - expected) <= 1e-9 * Math.max(1.0, Math.abs(expected));

      if(!same) {
         problems.add(data.name() + ": " + what + " expected " + expected + " but was " + actual);
      }
   }

   private void checkAll(String field, boolean two, Function<Data, Double> expected)
      throws Exception
   {
      checkAll(field, two, expected, d -> true);
   }

   private void checkAll(String field, boolean two, Function<Data, Double> expected,
                         Predicate<Data> include)
      throws Exception
   {
      String expression = formula(field).getExpression("x", two ? "y" : null, new DbHelper("derby"));
      List<String> problems = new ArrayList<>();

      for(Data data : DATA) {
         if(!include.test(data)) {
            continue;
         }

         try {
            check(problems, data, field, expected.apply(data), run(expression, data));
         }
         catch(SQLException ex) {
            problems.add(data.name() + ": " + field + " failed in the database: " + ex.getMessage());
         }
      }

      assertTrue(problems.isEmpty(), String.join("\n", problems) + "\nSQL: " + expression);
   }

   @Test
   void sampleVariance() throws Exception {
      checkAll("VARIANCE", false, d -> sampleVariance(d.x()));
   }

   @Test
   void populationVariance() throws Exception {
      checkAll("POPULATION_VARIANCE", false, d -> populationVariance(d.x()));
   }

   @Test
   void sampleStandardDeviation() throws Exception {
      checkAll("STANDARD_DEVIATION", false, d -> {
         Double v = sampleVariance(d.x());
         return v == null ? null : Math.sqrt(v);
      });
   }

   @Test
   void populationStandardDeviation() throws Exception {
      checkAll("POPULATION_STANDARD_DEVIATION", false, d -> {
         Double v = populationVariance(d.x());
         return v == null ? null : Math.sqrt(v);
      });
   }

   /** The data sets where a missing value is in one of the two columns only, or one row. */
   private static boolean pairSpecial(Data d) {
      return d.name().equals("a null in x") || d.name().equals("nulls in different rows")
         || d.name().equals("one row");
   }

   @Test
   void populationCovariance() throws Exception {
      checkAll("COVARIANCE", true, AggregateFormulaStatSqlTest::populationCovariance,
               d -> !pairSpecial(d) || d.name().equals("one row"));
   }

   @Test
   void correlation() throws Exception {
      checkAll("CORRELATION", true, AggregateFormulaStatSqlTest::correlation,
               d -> !pairSpecial(d));
   }

   // the expression counts the rows where x is there and sums y over all rows, so a row with
   // only one of the two values changes the means; the covariance and the correlation of the
   // rows that have both values is what the other databases give
   @Test
   void covarianceLeavesOutRowsWithAMissingValue() throws Exception {
      checkAll("COVARIANCE", true, AggregateFormulaStatSqlTest::populationCovariance,
               d -> d.name().equals("a null in x") || d.name().equals("nulls in different rows"));
   }

   @Test
   void correlationLeavesOutRowsWithAMissingValue() throws Exception {
      checkAll("CORRELATION", true, AggregateFormulaStatSqlTest::correlation,
               d -> d.name().equals("a null in x") || d.name().equals("nulls in different rows"));
   }

   // one pair has no correlation: the other databases give null, Derby fails with a division
   // by zero
   @Test
   void correlationOfOneRowIsNull() throws Exception {
      checkAll("CORRELATION", true, AggregateFormulaStatSqlTest::correlation,
               d -> d.name().equals("one row"));
   }
}
