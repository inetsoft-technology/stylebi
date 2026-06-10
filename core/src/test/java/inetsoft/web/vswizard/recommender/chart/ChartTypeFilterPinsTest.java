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
package inetsoft.web.vswizard.recommender.chart;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic unit tests for the strict-pin support added to {@link ChartTypeFilter}:
 *  - aestheticCaps() cardinality thresholds per chart type (static, no instance needed), and
 *  - satisfiesPins() per-pin set containment (exercised through a concrete base-class instance,
 *    which transitively covers the private boundFieldNames/isPinnedTo helpers).
 *
 * Uses the Spring/@SreeHome harness because constructing a VSChartInfo with dimension/aggregate
 * refs triggers GDefaults/SreeEnv init that throws ShutdownException in a plain JVM.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartTypeFilterPinsTest {
   // ── aestheticCaps (public static) ─────────────────────────────────────────────
   @Test
   void aestheticCapsDefaultsForBarChart() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);

      assertArrayEquals(new int[]{ 40, 8, 10 }, ChartTypeFilter.aestheticCaps(info),
         "default caps {color 40, shape 8, size 10}");
   }

   @Test
   void aestheticCapsShapeFiveForLine() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);

      assertArrayEquals(new int[]{ 40, 5, 10 }, ChartTypeFilter.aestheticCaps(info),
         "line chart caps shape cardinality at 5");
   }

   @Test
   void aestheticCapsShapeSixteenForPoint() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_POINT);

      assertArrayEquals(new int[]{ 40, 16, 10 }, ChartTypeFilter.aestheticCaps(info),
         "point chart caps shape cardinality at 16");
   }

   @Test
   void aestheticCapsSizeTwoHundredForWordCloud() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_POINT);

      VSAestheticRef text = new VSAestheticRef();
      text.setDataRef(dimension("category"));
      info.setTextField(text);

      assertArrayEquals(new int[]{ 40, 16, 200 }, ChartTypeFilter.aestheticCaps(info),
         "point + text field (word cloud) raises size cap to 200");
   }

   @Test
   void aestheticCapsNullInfoReturnsDefaults() {
      assertArrayEquals(new int[]{ 40, 8, 10 }, ChartTypeFilter.aestheticCaps(null),
         "null info returns the default caps without dereferencing it");
   }

   // ── satisfiesPins (protected; needs a concrete instance) ──────────────────────
   @Test
   void satisfiesPinsContainmentOnColorSlot() {
      ChartTypeFilter filter = newFilter();

      VSChartInfo candidate = new VSChartInfo();
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(dimension("country"));
      candidate.setColorField(color);

      assertTrue(filter.satisfiesPins(candidate, Map.of("color", Set.of("country"))),
         "color=country satisfies a pin for color:{country}");
      assertFalse(filter.satisfiesPins(candidate, Map.of("color", Set.of("region"))),
         "color=country does NOT satisfy a pin for color:{region}");
   }

   @Test
   void satisfiesPinsEmptyPinSetTriviallySatisfied() {
      ChartTypeFilter filter = newFilter();

      // candidate has nothing bound to color, but the pin asks for no specific field
      VSChartInfo candidate = new VSChartInfo();

      assertTrue(filter.satisfiesPins(candidate, Map.of("color", Set.of())),
         "an empty pin set (color:{}) is trivially satisfied (containsAll of empty)");
   }

   @Test
   void satisfiesPinsFieldBoundTwiceStillSatisfiesSinglePin() {
      ChartTypeFilter filter = newFilter();

      // The doc comment's case: a field appearing twice in one slot still satisfies a single
      // pin for it. This is containment, not a count — duplicates must not over-shoot and drop
      // a satisfying candidate. boundFieldNames is a Set, so two x-fields named "month" collapse
      // to one name, and the pin for x:{month} is satisfied.
      VSChartInfo candidate = new VSChartInfo();
      candidate.addXField(dimension("month"));
      candidate.addXField(aggregate("month", "Count"));

      assertTrue(filter.satisfiesPins(candidate, Map.of("x", Set.of("month"))),
         "a field bound twice in one slot still satisfies a single pin for that field");
   }

   /**
    * The base ChartTypeFilter is concrete; satisfiesPins reads only the candidate passed to it,
    * never the temp template. So a minimal empty temp is enough to construct an instance
    * (initValues -> getDateCount iterates the empty getXFields()).
    */
   private static ChartTypeFilter newFilter() {
      return new ChartTypeFilter(new AssetEntry[0], new VSChartInfo(), true);
   }

   private static VSChartDimensionRef dimension(String field) {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue(field);
      return dim;
   }

   private static VSChartAggregateRef aggregate(String column, String formula) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue(column);
      agg.setFormulaValue(formula);
      agg.setAggregated(true);
      return agg;
   }
}
