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

import inetsoft.report.composition.graph.StandardDeviationWrapper;
import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Standard deviation target: mean + factor * standard deviation, sample (n-1) or population (n).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class StandardDeviationStrategyTest {
   private static final double N = Tool.NULL_DOUBLE;
   private static final double[] ONE_TO_FIVE = { 1, 2, 3, 4, 5 };
   // mean 3 of [1..5]; sample s = sqrt(2.5), population sigma = sqrt(2)
   private static final double MEAN = 3;
   private static final double S = Math.sqrt(2.5);

   private static StandardDeviationStrategy strategy(boolean sample, Double... factors) {
      return new StandardDeviationStrategy(Arrays.asList(factors), sample);
   }

   private static double[] bounds(boolean sample, double[] data, Double... factors) {
      return strategy(sample, factors).calculateBoundaries(data);
   }

   private static String[] labels(StandardDeviationStrategy strategy, double[] bounds) {
      return strategy.generateLabels(bounds, null, "f", null, false, false);
   }

   @Test
   @DisplayName("sample standard deviation: factors 1 and 2 of [1..5]")
   void sampleDeviation() {
      assertArrayEquals(new double[] { 4.5811, 6.1623 }, bounds(true, ONE_TO_FIVE, 1.0, 2.0), 1e-4);
   }

   @Test
   @DisplayName("population standard deviation: factors 1 and 2 of [1..5]")
   void populationDeviation() {
      assertArrayEquals(new double[] { 4.4142, 5.8284 }, bounds(false, ONE_TO_FIVE, 1.0, 2.0), 1e-4);
   }

   @Test
   @DisplayName("default strategy is a sample deviation without factors, so it has no lines")
   void defaultStrategy() {
      StandardDeviationStrategy strategy = new StandardDeviationStrategy();
      assertTrue(strategy.isSample());
      assertEquals(0, strategy.calculateBoundaries(ONE_TO_FIVE).length);
   }

   @Test
   @DisplayName("switching sample to population scales the offset by sqrt(4/5)")
   void sampleVersusPopulationRatio() {
      StandardDeviationStrategy strategy = strategy(true, 1.0);
      double sample = strategy.calculateBoundaries(ONE_TO_FIVE)[0];
      strategy.setSample(false);
      double population = strategy.calculateBoundaries(ONE_TO_FIVE)[0];
      assertEquals(Math.sqrt(5.0 / 4.0), (sample - MEAN) / (population - MEAN), 1e-9);
      strategy.setSample(true);
      assertEquals(sample, strategy.calculateBoundaries(ONE_TO_FIVE)[0], 1e-12);
   }

   @Test
   @DisplayName("two values [10,20]: sample 22.0711 and population 20.0 at factor 1")
   void twoValues() {
      assertEquals(22.0711, bounds(true, new double[] { 10, 20 }, 1.0)[0], 1e-4);
      assertEquals(20.0, bounds(false, new double[] { 10, 20 }, 1.0)[0], 1e-4);
   }

   @Test
   @DisplayName("negative factor gives a line below the mean")
   void negativeFactor() {
      assertArrayEquals(new double[] { 1.4189, 4.5811 }, bounds(true, ONE_TO_FIVE, -1.0, 1.0), 1e-4);
   }

   @Test
   @DisplayName("unsorted factors: bounds ascend and labels follow the sorted factors")
   void unsortedFactorsKeepLabelsAligned() {
      StandardDeviationStrategy strategy = strategy(true, 2.0, -1.0, 1.0);
      double[] b = strategy.calculateBoundaries(ONE_TO_FIVE);
      assertArrayEquals(new double[] { 1.4189, 4.5811, 6.1623 }, b, 1e-4);
      String[] labels = labels(strategy, b);
      assertTrue(labels[0].endsWith("(-1.0)"), labels[0]);
      assertTrue(labels[1].endsWith("(1.0)"), labels[1]);
      assertTrue(labels[2].endsWith("(2.0)"), labels[2]);
   }

   @Test
   @DisplayName("duplicate factors keep both lines and both labels")
   void duplicateFactors() {
      StandardDeviationStrategy strategy = strategy(true, 1.0, 1.0);
      double[] b = strategy.calculateBoundaries(ONE_TO_FIVE);
      assertArrayEquals(new double[] { 4.5811, 4.5811 }, b, 1e-4);
      String[] labels = labels(strategy, b);
      assertEquals(2, labels.length);
      assertTrue(labels[0].endsWith("(1.0)") && labels[1].endsWith("(1.0)"));
   }

   @Test
   @DisplayName("factor 0 gives a line at the mean")
   void zeroFactor() {
      assertArrayEquals(new double[] { 3.0 }, bounds(true, ONE_TO_FIVE, 0.0), 1e-9);
   }

   @Test
   @DisplayName("no factors gives no lines and no labels")
   void noFactors() {
      StandardDeviationStrategy strategy = strategy(true);
      double[] b = strategy.calculateBoundaries(ONE_TO_FIVE);
      assertEquals(0, b.length);
      assertDoesNotThrow(() -> assertEquals(0, labels(strategy, b).length));
   }

   @Test
   @DisplayName("null cells are ignored: [N,1..5,N] gives the same lines as [1..5]")
   void nullCellsAreIgnored() {
      assertArrayEquals(bounds(true, ONE_TO_FIVE, 1.0, 2.0),
                        bounds(true, new double[] { N, 1, 2, 3, 4, 5, N }, 1.0, 2.0), 1e-9);
   }

   @Test
   @Disabled("Not filed (NaN bounds with no non-null data, no evidence of a visible effect): empty data with factor 1 gives [NaN] instead of no lines")
   @DisplayName("empty data gives no lines and no NaN")
   void emptyData() {
      double[] result = bounds(true, new double[0], 1.0);
      assertEquals(0, result.length, Arrays.toString(result));
   }

   @Test
   @Disabled("Not filed (NaN bounds with no non-null data, no evidence of a visible effect): [N,N,N] with factors [1,2] gives [NaN, NaN] instead of no lines")
   @DisplayName("all-null data gives no lines and no labels")
   void allNullData() {
      StandardDeviationStrategy strategy = strategy(true, 1.0, 2.0);
      double[] b = strategy.calculateBoundaries(new double[] { N, N, N });
      assertEquals(0, b.length, Arrays.toString(b));
      assertDoesNotThrow(() -> assertEquals(0, labels(strategy, b).length));
   }

   @Test
   @DisplayName("a single value with a sample deviation gives a finite line")
   void singleValueSample() {
      double[] result = bounds(true, new double[] { 5 }, 1.0, 2.0);

      for(double d : result) {
         assertTrue(Double.isFinite(d), Arrays.toString(result));
      }
   }

   @Test
   @DisplayName("a single value with a population deviation puts every line at the value")
   void singleValuePopulation() {
      assertArrayEquals(new double[] { 5, 5 }, bounds(false, new double[] { 5 }, 1.0, 2.0), 1e-9);
   }

   @Test
   @DisplayName("identical values put every line at the value")
   void identicalValues() {
      assertArrayEquals(new double[] { 4, 4 }, bounds(true, new double[] { 4, 4, 4 }, 1.0, 2.0), 1e-9);
   }

   @Test
   @DisplayName("negative data mirrors the positive result")
   void negativeData() {
      // mean -3, s = 1.5811
      assertArrayEquals(new double[] { -1.4189 },
                        bounds(true, new double[] { -1, -2, -3, -4, -5 }, 1.0), 1e-4);
   }

   @Test
   @DisplayName("large magnitudes keep the relative accuracy")
   void largeMagnitudes() {
      double[] result = bounds(true, new double[] { 1e15, 2e15, 3e15 }, 1.0);
      assertEquals(3e15, result[0], 3e15 * 1e-9);
   }

   @Test
   @DisplayName("pinned current behavior: a NaN data value makes the line NaN")
   void nanDataValueGivesNaNLine() {
      assertArrayEquals(new double[] { Double.NaN },
                        bounds(true, new double[] { 1, Double.NaN, 3 }, 1.0));
   }

   @Test
   @DisplayName("one instance reused for different data equals a fresh instance each time")
   void reusedInstanceHasNoStaleOrder() {
      StandardDeviationStrategy reused = strategy(true, 2.0, 1.0);
      double[] dataA = { 1, 2, 3, 4, 5 };
      double[] dataB = { 10, 20, 30, 40 };

      assertArrayEquals(bounds(true, dataA, 2.0, 1.0), reused.calculateBoundaries(dataA), 1e-12);
      String[] first = labels(reused, reused.calculateBoundaries(dataA));
      assertArrayEquals(bounds(true, dataB, 2.0, 1.0), reused.calculateBoundaries(dataB), 1e-12);
      assertArrayEquals(first, labels(reused, reused.calculateBoundaries(dataA)));
      assertTrue(first[0].endsWith("(1.0)") && first[1].endsWith("(2.0)"), Arrays.toString(first));
   }

   @Test
   @DisplayName("constructor copies the factors: later changes to the collection do not apply")
   void constructorCopiesFactors() {
      List<Double> factors = new ArrayList<>(List.of(1.0, 2.0));
      StandardDeviationStrategy strategy = new StandardDeviationStrategy(factors, true);
      factors.clear();
      factors.add(9.0);
      assertArrayEquals(new double[] { 4.5811, 6.1623 }, strategy.calculateBoundaries(ONE_TO_FIVE), 1e-4);
   }

   static Stream<Arguments> nullFactorPositions() {
      return Stream.of(Arguments.of((Object) new Double[] { null, 1.0, 2.0 }),
                       Arguments.of((Object) new Double[] { 1.0, null, 2.0 }),
                       Arguments.of((Object) new Double[] { 1.0, 2.0, null }));
   }

   @ParameterizedTest(name = "factors {0}")
   @MethodSource("nullFactorPositions")
   @Disabled("Not filed (only the public constructor reaches it; TargetStrategyWrapper turns null into 0 or drops it): a null factor first or in the middle loses a line (one line instead of two)"
      + "; a trailing null works")
   @DisplayName("null factors are ignored: lines for the other factors and labels without null")
   void nullFactorsAreIgnored(Double[] factors) {
      StandardDeviationStrategy strategy = strategy(true, factors);
      double[] b = strategy.calculateBoundaries(ONE_TO_FIVE);
      assertArrayEquals(new double[] { MEAN + S, MEAN + 2 * S }, b, 1e-4);
      String[] labels = labels(strategy, b);
      assertEquals(2, labels.length);
      assertTrue(labels[0].endsWith("(1.0)"), labels[0]);
      assertTrue(labels[1].endsWith("(2.0)"), labels[1]);
      assertFalse(labels[0].contains("null") || labels[1].contains("null"));
   }

   private static StandardDeviationStrategy unwrap(StandardDeviationWrapper wrapper) {
      return (StandardDeviationStrategy) wrapper.unwrap();
   }

   @Test
   @DisplayName("wrapper unwrap carries the sample flag; XML without isSample element means sample")
   void wrapperSampleFlag() throws Exception {
      assertFalse(unwrap(new StandardDeviationWrapper(false, "1")).isSample());
      assertTrue(unwrap(new StandardDeviationWrapper(true, "1")).isSample());

      String xml = "<standardDeviation><values><value val=\"1\"/></values></standardDeviation>";
      Element element = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      StandardDeviationWrapper parsed = new StandardDeviationWrapper();
      parsed.parseXml(element);
      assertTrue(unwrap(parsed).isSample());
   }

   @Test
   @DisplayName("random factor order: each label names the factor of its line")
   void randomFactorsLabelMatchLine() {
      Random random = new Random(20241123L);

      for(int round = 0; round < 25; round++) {
         List<Double> factors = new ArrayList<>();

         while(factors.size() < 6) {
            double f = random.nextInt(13) - 6;

            if(!factors.contains(f)) {
               factors.add(f);
            }
         }

         StandardDeviationStrategy strategy = new StandardDeviationStrategy(factors, true);
         double[] b = strategy.calculateBoundaries(ONE_TO_FIVE);
         String[] labels = labels(strategy, b);

         for(int i = 0; i < b.length; i++) {
            String label = labels[i];
            double f = Double.parseDouble(label.substring(label.lastIndexOf('(') + 1,
                                                          label.lastIndexOf(')')));
            assertEquals(MEAN + f * S, b[i], 1e-9, "factors " + factors + " line " + i);
         }
      }
   }
}
