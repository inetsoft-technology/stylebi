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
package inetsoft.report.composition.execution;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.PluginsTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static inetsoft.report.composition.execution.MergeableFixture.column;
import static inetsoft.report.composition.execution.MergeableFixture.integer;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Which aggregate formulas of a worksheet table are pushed down to the SQL of the database
 * ({@link PreAssetQuery#isAggregateMergeable}). The rules are the ones of the code: the formulas
 * that the SQL cannot express (mode, first, last, product, concatenation, nth largest, nth
 * smallest, nth most frequent, percentile), a percentage and a missing formula are never pushed
 * down, the median only on Oracle 10 or later, the average of an integer column not on Derby (the
 * SQL would lose the decimals), and every other formula when the database supports it, which
 * {@code sqlhelper.xml} says: Sybase has no variance, standard deviation, covariance, correlation
 * and sum of squares, the other databases of the tests have all of them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  MergeableFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PreAssetQueryAggregateMergeableTest {
   private static final Map<String, AggregateFormula> FORMULAS = Map.ofEntries(
      Map.entry("sum", AggregateFormula.SUM), Map.entry("max", AggregateFormula.MAX),
      Map.entry("min", AggregateFormula.MIN), Map.entry("avg", AggregateFormula.AVG),
      Map.entry("count", AggregateFormula.COUNT_ALL),
      Map.entry("distinct count", AggregateFormula.COUNT_DISTINCT),
      Map.entry("median", AggregateFormula.MEDIAN), Map.entry("mode", AggregateFormula.MODE),
      Map.entry("first", AggregateFormula.FIRST), Map.entry("last", AggregateFormula.LAST),
      Map.entry("product", AggregateFormula.PRODUCT), Map.entry("concat", AggregateFormula.CONCAT),
      Map.entry("nth largest", AggregateFormula.NTH_LARGEST),
      Map.entry("nth smallest", AggregateFormula.NTH_SMALLEST),
      Map.entry("nth most frequent", AggregateFormula.NTH_MOST_FREQUENT),
      Map.entry("percentile", AggregateFormula.PTH_PERCENTILE),
      Map.entry("variance", AggregateFormula.VARIANCE),
      Map.entry("standard deviation", AggregateFormula.STANDARD_DEVIATION),
      Map.entry("population variance", AggregateFormula.POPULATION_VARIANCE),
      Map.entry("population standard deviation",
                AggregateFormula.POPULATION_STANDARD_DEVIATION),
      Map.entry("sum of squares", AggregateFormula.SUMSQ),
      Map.entry("covariance", AggregateFormula.COVARIANCE),
      Map.entry("correlation", AggregateFormula.CORRELATION));

   /** Group by k and aggregate v with the formula, on the database; a column of that type. */
   private static boolean mergeable(String db, String version, String formula, String type)
      throws Exception
   {
      MergeableFixture.Built built = MergeableFixture.simple(db, version);
      AggregateFormula f = FORMULAS.get(formula);
      assertNotNull(f, formula);
      ColumnRef v = column("v", type);
      AggregateRef aggregate = f.isTwoColumns() ?
         new AggregateRef(v, integer("k"), f) : new AggregateRef(v, f);
      return mergeable(built, aggregate);
   }

   private static boolean mergeable(MergeableFixture.Built built, AggregateRef aggregate)
      throws Exception
   {
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(integer("k")));
      info.addAggregate(aggregate);
      built.table.setAggregateInfo(info);

      PreAssetQuery query = built.query();
      boolean one = query.isAggregateMergeable(query.getAggregateInfo().getAggregate(0));
      // the group by of the table agrees, as every other part of the table can be pushed down
      assertEquals(one, built.query().isAggregateInfoMergeable());
      return one;
   }

   private static boolean mergeable(String db, String formula) throws Exception {
      return mergeable(db, null, formula, XSchema.DOUBLE);
   }

   // ---- the formulas that the SQL cannot express ----

   @ParameterizedTest(name = "{1} on {0}")
   @CsvSource({
      "derby,mode", "derby,first", "derby,last", "derby,product", "derby,concat",
      "derby,nth largest", "derby,nth smallest", "derby,nth most frequent", "derby,percentile",
      "oracle,mode", "oracle,first", "oracle,last", "oracle,product", "oracle,concat",
      "oracle,nth largest", "oracle,nth smallest", "oracle,nth most frequent",
      "oracle,percentile",
      "postgresql,mode", "postgresql,first", "postgresql,last", "postgresql,product",
      "postgresql,concat", "postgresql,nth largest", "postgresql,nth smallest",
      "postgresql,nth most frequent", "postgresql,percentile"
   })
   void formulaTheSqlCannotExpressIsNeverPushedDown(String db, String formula) throws Exception {
      assertFalse(mergeable(db, formula));
   }

   @Test
   void tableWithoutAFormulaIsNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("derby");

      assertFalse(mergeable(built, new AggregateRef(integer("v"), (AggregateFormula) null)));
   }

   @Test
   void percentageIsNotPushedDown() throws Exception {
      MergeableFixture.Built built = MergeableFixture.simple("postgresql");
      AggregateRef sum = new AggregateRef(integer("v"), AggregateFormula.SUM);
      sum.setPercentageOption(XConstants.PERCENTAGE_OF_GROUP);

      assertFalse(mergeable(built, sum));
      assertTrue(mergeable(MergeableFixture.simple("postgresql"),
                           new AggregateRef(integer("v"), AggregateFormula.SUM)));
   }

   // ---- the ordinary formulas ----

   @ParameterizedTest(name = "{1} on {0}")
   @CsvSource({
      "derby,sum", "derby,max", "derby,min", "derby,count", "derby,distinct count",
      "h2,sum", "h2,max", "h2,min", "h2,avg", "h2,count", "h2,distinct count",
      "postgresql,sum", "postgresql,max", "postgresql,min", "postgresql,avg",
      "postgresql,count", "postgresql,distinct count",
      "mysql,sum", "mysql,max", "mysql,min", "mysql,avg", "mysql,count", "mysql,distinct count",
      "oracle,sum", "oracle,max", "oracle,min", "oracle,avg", "oracle,count",
      "oracle,distinct count",
      "sql server,sum", "sql server,max", "sql server,min", "sql server,avg",
      "sql server,count", "sql server,distinct count"
   })
   void ordinaryFormulaIsPushedDown(String db, String formula) throws Exception {
      assertTrue(mergeable(db, formula));
   }

   // ---- the average of an integer column on Derby ----

   @Test
   void averageOfAnIntegerColumnIsNotPushedDownOnDerby() throws Exception {
      assertFalse(mergeable("derby", null, "avg", XSchema.INTEGER));
      // a column with decimals, or another database, or another formula is fine
      assertTrue(mergeable("derby", null, "avg", XSchema.DOUBLE));
      assertTrue(mergeable("postgresql", null, "avg", XSchema.INTEGER));
      assertTrue(mergeable("derby", null, "sum", XSchema.INTEGER));
   }

   // ---- the median ----

   @ParameterizedTest(name = "median on {0}")
   @CsvSource({ "derby", "h2", "postgresql", "mysql", "sql server" })
   void medianIsNotPushedDownOnOtherDatabases(String db) throws Exception {
      assertFalse(mergeable(db, "median"));
   }

   // only Oracle, and only from version 10 (the number before the first dot)
   @Test
   void medianIsPushedDownOnOracleFromVersionTen() throws Exception {
      assertTrue(mergeable("oracle", "19.0", "median", XSchema.DOUBLE));
      assertTrue(mergeable("oracle", "10.2", "median", XSchema.DOUBLE));
      assertFalse(mergeable("oracle", "9.2", "median", XSchema.DOUBLE));
   }

   // the SQL helper learns the version of an Oracle database from the database, and only when the
   // data source has no version of its own (SQLHelper.getSQLHelper). A data source with a version
   // (the "version" attribute of its definition) is not asked, the helper has none, and the median
   // is not pushed down. The product sets the version of a data source only for MySQL, which
   // reads it from the data source, so this is for a definition that has one for Oracle.
   // Pins the current behavior.
   @Test
   void medianIsNotPushedDownOnOracleWhenTheDataSourceHasAVersionOfItsOwn() throws Exception {
      // the data source of the fixture has the version 19.0 unless the database reports it
      assertFalse(mergeable("oracle", "median"));
   }

   // ---- what a database does not support ----

   @ParameterizedTest(name = "{1} on sybase")
   @CsvSource({ "variance", "standard deviation", "population variance",
                "population standard deviation", "covariance", "correlation" })
   void sybaseHasNoVarianceStandardDeviationCovarianceAndCorrelation(String formula)
      throws Exception
   {
      assertFalse(mergeable("sybase", formula));
   }

   @ParameterizedTest(name = "{0} on {1}")
   @CsvSource({ "variance,derby", "standard deviation,postgresql", "population variance,oracle",
                "population standard deviation,mysql", "sum of squares,sql server",
                "covariance,derby", "correlation,postgresql" })
   void otherDatabasesPushDownVarianceStandardDeviationCovarianceAndCorrelation(
      String formula, String db) throws Exception
   {
      assertTrue(mergeable(db, formula));
   }

   // sqlhelper.xml lists "sumsq" as unsupported on Sybase, but the code asks for the name of the
   // formula in lower case, "sumsqformula", so the entry never applies and the sum of squares is
   // pushed down. The formula is an internal one (the SQL of a variance is made from it), the user
   // cannot choose it. Pins the current behavior.
   @Test
   void sybaseSumOfSquaresIsPushedDownDespiteTheEntryOfTheSqlHelperList() throws Exception {
      assertTrue(mergeable("sybase", "sum of squares"));
   }

   // Sybase pushes down the formulas that it has
   @Test
   void sybasePushesDownTheOrdinaryFormulas() throws Exception {
      assertTrue(mergeable("sybase", "sum"));
      assertTrue(mergeable("sybase", "avg"));
      assertTrue(mergeable("sybase", "count"));
   }
}
