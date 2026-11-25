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
package inetsoft.report.filter;

import inetsoft.report.composition.graph.calc.*;
import inetsoft.test.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static inetsoft.report.filter.CrossTabFilterTestSupport.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
/**
 * Formulas that need all rows of a tuple (a second column, a rank or a spread) must give
 * the same result in a group total as the formula fed with the rows of that tuple.
 */
class CrossTabFilterTupleAggregateTest {
   // columns: R1, R2, C1, X, Y; two rows per existing (R1, R2, C1) combination
   private Object[][] data() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "R1", "R2", "C1", "X", "Y" });
      int n = 0;

      for(String r1 : new String[] { "a", "b" }) {
         for(String r2 : new String[] { "p", "q" }) {
            for(String c1 : new String[] { "c", "d" }) {
               if(r1.equals("b") && r2.equals("q") && c1.equals("d")) {
                  continue;
               }

               for(int k = 0; k < 2; k++) {
                  n++;
                  rows.add(new Object[] { r1, r2, c1, (double) ((n * 37) % 23 + k),
                                          (double) ((n * 11) % 17 + 2 * k) });
               }
            }
         }
      }

      return rows.toArray(new Object[0][]);
   }

   private void assertEveryCellEqualsTheOracle(Supplier<Formula> crosstabFormula,
                                               Function<List<Object[]>, Object> oracle)
   {
      Object[][] data = data();
      int[] rowCols = { 0, 1 };
      int[] colCols = { 2 };
      CrossTabFilter f = crosstab(data, rowCols, colCols, new int[] { 3 },
                                  new Formula[] { crosstabFormula.get() });
      f.moreRows(Integer.MAX_VALUE);
      int totals = 0;

      for(Cell cell : readCells(f)) {
         List<Object[]> rows = matching(data, rowCols, cell.rowKey(), colCols, cell.colKey());
         Object expected = rows.isEmpty() ? null : oracle.apply(rows);

         if(cell.rowKey().length < 2 || cell.colKey().length < 1) {
            totals++;
         }

         if(expected instanceof Number || expected == null) {
            assertTrue(sameNumber(expected, cell.value()), cell + " expected=" + expected);
         }
         else {
            assertEquals(expected, cell.value(), cell.toString());
         }
      }

      assertTrue(totals > 0, "no total cell was read");
   }

   private static Object feed(Formula formula, List<Object[]> rows, boolean pair) {
      for(Object[] r : rows) {
         formula.addValue(pair ? new Object[] { r[3], r[4] } : r[3]);
      }

      return formula.getResult();
   }

   @Test
   void covarianceOfATotalIsTheCovarianceOfTheRowsOfTheTuple() {
      assertEveryCellEqualsTheOracle(() -> new CovarianceFormula(4),
                                     rows -> feed(new CovarianceFormula(4), rows, true));
   }

   @Test
   void secondSmallestOfATotalIsTheSecondSmallestOfTheRowsOfTheTuple() {
      assertEveryCellEqualsTheOracle(() -> new NthSmallestFormula(2), rows -> {
         List<Double> sorted = new ArrayList<>();
         rows.forEach(r -> sorted.add(((Number) r[3]).doubleValue()));
         Collections.sort(sorted);
         return sorted.size() < 2 ? null : sorted.get(1);
      });
   }

   @Test
   void standardDeviationOfATotalIsTheDeviationOfTheRowsOfTheTuple() {
      assertEveryCellEqualsTheOracle(StandardDeviationFormula::new,
                                     rows -> feed(new StandardDeviationFormula(), rows, false));
   }

   @Test
   void productOfATotalIsTheProductOfTheRowsOfTheTuple() {
      assertEveryCellEqualsTheOracle(ProductFormula::new, rows -> {
         double product = 1;

         for(Object[] r : rows) {
            product *= ((Number) r[3]).doubleValue();
         }

         return product;
      });
   }

   @Test
   void concatOfATotalIsTheConcatOfTheRowsOfTheTuple() {
      assertEveryCellEqualsTheOracle(ConcatFormula::new,
                                     rows -> feed(new ConcatFormula(), rows, false));
   }
}
