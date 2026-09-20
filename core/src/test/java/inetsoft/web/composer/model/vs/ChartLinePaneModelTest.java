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
package inetsoft.web.composer.model.vs;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code updateChartLinePaneModel}'s trend-line handling assumed every caller supplies a model
 * built by this class's own two-arg constructor -- which always populates
 * {@code trendLineType}/{@code measures}/{@code trendLineMeasures} from a real
 * {@code PlotDescriptor}. A caller that instead round-trips a bare (no-arg-constructed or
 * partially patched) {@code ChartLinePaneModel} -- confirmed live via composer-chat's
 * set_assembly_properties, which reads the current dialog model, patches only the field the
 * caller asked for (e.g. just the chart's title), and posts the result back -- leaves those
 * three fields at their Java default of null. {@code String.equals} called ON a null
 * {@code trendLineType} throws instead of the "no match -> index 0 (NONE)" fallback this class's
 * own {@code getIndexByName} already gives any other unrecognised token, turning an ordinary
 * property write unrelated to the Line tab into a 500 for every chart it's tried against.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class ChartLinePaneModelTest {
   @Test
   void updateChartLinePaneModelToleratesANullTrendLineType() {
      ChartLinePaneModel model = new ChartLinePaneModel();
      PlotDescriptor plotDesc = new PlotDescriptor();

      assertDoesNotThrow(() -> model.updateChartLinePaneModel(new VSChartInfo(), plotDesc),
                          "a bare ChartLinePaneModel (trendLineType/measures/trendLineMeasures " +
                          "all null) must not NPE -- it's exactly what a partial property patch " +
                          "round-trips");
      assertEquals(0, plotDesc.getTrendline(),
                    "a null trendLineType must fall through to index 0 (NONE), the same " +
                    "silent-fallback treatment any other unrecognised token already gets");
   }

   @Test
   void updateChartLinePaneModelStillResolvesARealTrendLineType() {
      ChartLinePaneModel model = new ChartLinePaneModel();
      model.setTrendLineType("Linear");
      model.setMeasures(new String[] { "Sum(Revenue)" });
      model.setTrendLineMeasures(new String[] { "Sum(Revenue)" });
      PlotDescriptor plotDesc = new PlotDescriptor();

      model.updateChartLinePaneModel(new VSChartInfo(), plotDesc);

      assertEquals(1, plotDesc.getTrendline(), "'Linear' must still resolve to its real index, " +
                   "not be swallowed by the null-tolerance guard");
      assertEquals(0, plotDesc.getTrendLineExcludedMeasures().size(),
                    "a measure present in both measures and trendLineMeasures must not be " +
                    "excluded from the trend line");
   }
}
