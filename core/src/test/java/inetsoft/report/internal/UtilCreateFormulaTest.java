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
package inetsoft.report.internal;

import inetsoft.report.filter.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Util#createFormula(inetsoft.uql.XTable, String)}, the factory AssetQuery uses for an
 * aggregate (Test Case #80957, vscalc). AssetQuery passes the formula name of the
 * {@link AggregateFormula}, followed by "(column)" for a two-column formula or "(n)" for a
 * formula with N. Viewsheet aggregates must get the {@code inetsoft.report.filter}
 * implementations: names that end in "Formula" (the composite and SumSQ / SumWT / Sum2
 * formulas) are internal to the materialized-view rewrite and resolve to {@code mv.*}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UtilCreateFormulaTest {
   @Test
   void everyUserFormula_createsTheReportFilterImplementation() {
      List<AggregateFormula> formulas = userFormulas();
      // W9: the explicit list of the AggregateFormula constants a user can pick
      java.util.Set<String> names = new java.util.TreeSet<>();
      formulas.forEach(f -> names.add(f.getFormulaName()));
      assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
         "Average", "Concat", "Correlation", "Count", "Covariance", "DistinctCount", "First",
         "Last", "Max", "Median", "Min", "Mode", "NthLargest", "NthMostFrequent",
         "NthSmallest", "PopulationStandardDeviation", "PopulationVariance", "Product",
         "PthPercentile", "StandardDeviation", "Sum", "Variance", "WeightedAverage")), names);

      for(AggregateFormula aform : formulas) {
         Formula formula = Util.createFormula(null, spec(aform));
         assertNotNull(formula, aform.getFormulaName());
         assertEquals("inetsoft.report.filter." + aform.getFormulaName() + "Formula",
                      formula.getClass().getName(), aform.getFormulaName());
      }
   }

   // a two-column formula is a Formula2 that reads the given secondary column; a one-column
   // formula is not
   @Test
   void twoColumnFormulas_getTheSecondaryColumn() {
      for(AggregateFormula aform : userFormulas()) {
         Formula formula = Util.createFormula(null, spec(aform));

         if(aform.isTwoColumns()) {
            assertInstanceOf(Formula2.class, formula, aform.getFormulaName());
            assertArrayEquals(new int[] { 3 }, ((Formula2) formula).getSecondaryColumns(),
                              aform.getFormulaName());
         }
         else {
            assertFalse(formula instanceof Formula2, aform.getFormulaName());
         }
      }
   }

   // the N of NthLargest / NthSmallest / NthMostFrequent / PthPercentile reaches the
   // formula; "Aggregate" (an aggregate calculated field, built as CalcFieldFormula) is not
   // created here
   @Test
   void formulasWithN_getTheN_andAggregateIsNotCreated() {
      Formula largest = Util.createFormula(null, "NthLargest(2)");
      Formula smallest = Util.createFormula(null, "NthSmallest(2)");
      Formula frequent = Util.createFormula(null, "NthMostFrequent(2)");
      Formula percentile = Util.createFormula(null, "PthPercentile(50)");

      for(Object v : new Object[] { 5, 9, 7, 9, 1 }) {
         largest.addValue(v);
         smallest.addValue(v);
         frequent.addValue(v);
         percentile.addValue(v);
      }

      // distinct values 1, 5, 7, 9: 2nd largest 7, 2nd smallest 5
      assertEquals(7, ((Number) largest.getResult()).intValue());
      assertEquals(5, ((Number) smallest.getResult()).intValue());
      // 9 twice, then 1, 5, 7 once each; ties go to the smaller value
      assertEquals(1, ((Number) frequent.getResult()).intValue());
      // the median of 1, 5, 7, 9, 9
      assertEquals(7, ((Number) percentile.getResult()).intValue());

      assertNull(Util.createFormula(null, AggregateFormula.AGGREGATE.getFormulaName()));
   }

   @Test
   @DisplayName("U2: a percentage suffix sets the percentage type of a percentage formula")
   void u2_percentageSuffix() {
      PercentageFormula sum = (PercentageFormula) Util.createFormula(null, "Sum<2>");
      assertEquals(2, sum.getPercentageType());
      PercentageFormula count = (PercentageFormula) Util.createFormula(null, "Count<1>");
      assertEquals(1, count.getPercentageType());
      PercentageFormula distinct =
         (PercentageFormula) Util.createFormula(null, "DistinctCount<2>");
      assertEquals(2, distinct.getPercentageType());

      // a two-column formula has no percentage: the suffix is ignored, the column stays
      Formula correlation = Util.createFormula(null, "Correlation(3)<2>");
      assertInstanceOf(CorrelationFormula.class, correlation);
      assertFalse(correlation instanceof PercentageFormula);
      assertArrayEquals(new int[] { 3 }, ((Formula2) correlation).getSecondaryColumns());

      // a suffix that is not a number is ignored
      PercentageFormula ignored = (PercentageFormula) Util.createFormula(null, "Sum<abc>");
      assertEquals(0, ignored.getPercentageType());
   }

   @Test
   @DisplayName("U3: a column name resolves against the table headers")
   void u3_tableBasedSecondaryColumn() {
      DefaultTableLens table = new DefaultTableLens(new Object[][] { { "a", "price" },
                                                                    { 1, 2 } });
      Formula formula = Util.createFormula(table, "Correlation(price)");
      assertInstanceOf(CorrelationFormula.class, formula);
      assertArrayEquals(new int[] { 1 }, ((Formula2) formula).getSecondaryColumns());
   }

   @Test
   @DisplayName("PIN U1 (current behavior, pending dev #80953): an unknown formula name gives " +
      "a SumFormula")
   void u1_pin_unknownNameGivesSum() {
      assertInstanceOf(SumFormula.class, Util.createFormula(null, "Bogus"));
   }

   @Test
   @DisplayName("PIN U4 (current behavior, pending dev #80953): a column name without a table " +
      "gives a SumFormula")
   void u4_pin_columnNameWithoutTableGivesSum() {
      assertInstanceOf(SumFormula.class, Util.createFormula(null, "Correlation(price)"));
   }

   @Test
   @DisplayName("U5: a class name is never instantiated as a formula")
   void u5_classNameInjection_fallsBackToSum() {
      for(String name : new String[] { "java.io.File", "java.lang.Runtime",
                                       InjectionProbe.class.getName() })
      {
         Formula formula = Util.createFormula(null, name);
         assertEquals(SumFormula.class, formula.getClass(), name);
      }

      // a class that is not a Formula is not even instantiated
      assertFalse(InjectionProbe.created);
   }

   /** A class with a public constructor whose side effect shows that it was instantiated. */
   public static class InjectionProbe {
      static volatile boolean created;

      public InjectionProbe() {
         created = true;
      }
   }

   @Test
   @DisplayName("U6: n at or below the boundary falls back to the smallest valid n")
   void u6_boundaryN() {
      assertEquals(NthMostFrequentFormula.class,
                   Util.createFormula(null, "NthMostFrequent(0)").getClass());
      assertEquals(NthLargestFormula.class,
                   Util.createFormula(null, "NthLargest(0)").getClass());
      assertEquals(NthSmallestFormula.class,
                   Util.createFormula(null, "NthSmallest(0)").getClass());
      assertEquals(PthPercentileFormula.class,
                   Util.createFormula(null, "PthPercentile(150)").getClass());
      assertEquals(PthPercentileFormula.class,
                   Util.createFormula(null, "PthPercentile(0)").getClass());

      // n = 1 and 0 rank the same
      Formula zero = Util.createFormula(null, "NthMostFrequent(0)");
      Formula one = Util.createFormula(null, "NthMostFrequent(1)");

      for(Object v : new Object[] { "a", "a", "b" }) {
         zero.addValue(v);
         one.addValue(v);
      }

      assertEquals("a", zero.getResult());
      assertEquals("a", one.getResult());
   }

   @Disabled("Bug #81211, mismatch U6b")
   @Test
   @DisplayName("U6b: a negative n is read like n = 1, not as a Sum")
   void u6b_negativeN() {
      assertEquals(NthMostFrequentFormula.class,
                   Util.createFormula(null, "NthMostFrequent(-1)").getClass());
      assertEquals(NthLargestFormula.class,
                   Util.createFormula(null, "NthLargest(-1)").getClass());
   }

   /**
    * The formulas a viewsheet aggregate can use: not None, not Aggregate (a calculated
    * field), not the materialized-view internal ones whose name ends in "Formula".
    */
   private static List<AggregateFormula> userFormulas() {
      List<AggregateFormula> list = new ArrayList<>();

      for(AggregateFormula aform : AggregateFormula.getFormulas()) {
         String name = aform.getFormulaName();

         if(aform != AggregateFormula.AGGREGATE && !name.endsWith("Formula") &&
            !"none".equalsIgnoreCase(name))
         {
            list.add(aform);
         }
      }

      return list;
   }

   /** The spec AssetQuery.getFormula1 builds: column 3 as the second column, N = 2. */
   private static String spec(AggregateFormula aform) {
      String spec = aform.getFormulaName();

      if(aform.isTwoColumns()) {
         spec += "(3)";
      }
      else if(aform.hasN()) {
         spec += "(2)";
      }

      return spec;
   }
}
