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

import inetsoft.report.filter.Formula;
import inetsoft.report.filter.Formula2;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
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
      assertTrue(formulas.size() >= 20, "user formulas: " + formulas);

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
