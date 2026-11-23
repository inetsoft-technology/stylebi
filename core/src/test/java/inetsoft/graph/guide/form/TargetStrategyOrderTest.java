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
package inetsoft.graph.guide.form;

import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boundaries are returned ascending and each label must describe its own line, for unsorted
 * parameter lists of the percentile, percentage, standard deviation and line targets.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TargetStrategyOrderTest {
   private static String[] labels(TargetStrategy strategy, double[] bounds) {
      return strategy.generateLabels(bounds, null, "f", null, false, false);
   }

   @Test
   @DisplayName("percentile target with unsorted percentiles: ascending values, labels follow")
   void percentileOrder() {
      PercentileStrategy strategy = new PercentileStrategy(Arrays.asList(75.0, 25.0, 50.0));
      double[] b = strategy.calculateBoundaries(new double[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
      assertArrayEquals(new double[] { 2.75, 5.5, 8.25 }, b, 1e-9);
      String[] labels = labels(strategy, b);
      assertTrue(labels[0].startsWith("25"), labels[0]);
      assertTrue(labels[1].startsWith("50"), labels[1]);
      assertTrue(labels[2].startsWith("75"), labels[2]);
   }

   @Test
   @DisplayName("percentage target with unsorted percentages: ascending values, labels follow")
   void percentageOrder() {
      PercentageStrategy strategy = new PercentageStrategy(Arrays.asList(50.0, 10.0, 100.0), null);
      double[] b = strategy.calculateBoundaries(new double[] { 3, 9, 6 });
      assertArrayEquals(new double[] { 0.9, 4.5, 9.0 }, b, 1e-9);
      String[] labels = labels(strategy, b);
      assertTrue(labels[0].startsWith("10"), labels[0]);
      assertTrue(labels[1].startsWith("50"), labels[1]);
      assertTrue(labels[2].startsWith("100"), labels[2]);
   }

   @Test
   @DisplayName("percentage target with a negative percentage sorts it first")
   void percentageWithNegative() {
      PercentageStrategy strategy = new PercentageStrategy(Arrays.asList(25.0, -50.0), null);
      double[] b = strategy.calculateBoundaries(new double[] { 3, 9, 6 });
      assertArrayEquals(new double[] { -4.5, 2.25 }, b, 1e-9);
      String[] labels = labels(strategy, b);
      assertTrue(labels[0].startsWith("-50"), labels[0]);
      assertTrue(labels[1].startsWith("25"), labels[1]);
   }

   private static List<Double> distinctFactors(Random random, int min, int max, int count) {
      List<Double> factors = new ArrayList<>();

      while(factors.size() < count) {
         double f = min + random.nextInt(max - min + 1);

         if(!factors.contains(f)) {
            factors.add(f);
         }
      }

      return factors;
   }

   private static void assertAscending(double[] b) {
      for(int i = 1; i < b.length; i++) {
         assertTrue(b[i - 1] <= b[i], Arrays.toString(b));
      }
   }

   @Test
   @DisplayName("random factor order: bounds ascend and label i names the i-th smallest factor")
   void randomOrderLabelsMatchLines() {
      Random random = new Random(20241123L);
      double[] data = new double[100];

      for(int i = 0; i < data.length; i++) {
         data[i] = i + 1;
      }

      for(int round = 0; round < 25; round++) {
         // percentile: data 1..100, factors 1..100
         List<Double> factors = distinctFactors(random, 1, 100, 6);
         List<Double> sorted = new ArrayList<>(factors);
         Collections.sort(sorted);
         PercentileStrategy percentile = new PercentileStrategy(factors);
         double[] b = percentile.calculateBoundaries(data);
         assertEquals(6, b.length);
         assertAscending(b);
         String[] labels = labels(percentile, b);

         for(int i = 0; i < 6; i++) {
            assertTrue(labels[i].startsWith(String.valueOf(sorted.get(i).intValue())),
                       "percentile " + factors + " label " + i + ": " + labels[i]);
         }

         // percentage of Max = 100
         PercentageStrategy percentage = new PercentageStrategy(factors, null);
         b = percentage.calculateBoundaries(data);
         assertAscending(b);
         labels = labels(percentage, b);

         for(int i = 0; i < 6; i++) {
            assertEquals(sorted.get(i), b[i], 1e-9);
            assertTrue(labels[i].startsWith(sorted.get(i).intValue() + "%"),
                       "percentage " + factors + " label " + i + ": " + labels[i]);
         }

         // standard deviation of 1..100
         factors = distinctFactors(random, -6, 6, 6);
         sorted = new ArrayList<>(factors);
         Collections.sort(sorted);
         StandardDeviationStrategy deviation = new StandardDeviationStrategy(factors, true);
         b = deviation.calculateBoundaries(data);
         assertAscending(b);
         labels = labels(deviation, b);

         for(int i = 0; i < 6; i++) {
            assertTrue(labels[i].endsWith("(" + sorted.get(i) + ")"),
                       "deviation " + factors + " label " + i + ": " + labels[i]);
         }

         // constant lines
         TargetParameter[] params = factors.stream().map(TargetParameter::new)
            .toArray(TargetParameter[]::new);
         DynamicLineStrategy line = new DynamicLineStrategy(params);
         b = line.calculateBoundaries(data);
         assertAscending(b);
         labels = labels(line, b);

         for(int i = 0; i < 6; i++) {
            assertEquals(sorted.get(i), b[i], 1e-9);
            assertEquals(String.valueOf(sorted.get(i)), labels[i],
                         "line " + factors + " label " + i);
         }
      }
   }
}
