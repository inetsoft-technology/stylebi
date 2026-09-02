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

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.CompositeValue;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Chart Line pane reads and writes one stored gridline colour. Before the values were seeded
 * it resolved them on both sides, so the pane displayed the modern colour while the canvas drew
 * the legacy one; the change-detection guard on the write side depended on that same resolution,
 * which is why both sides had to move together.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartLinePaneModelTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   @Test
   void thePaneReportsTheSeededDarkGridlineTheCanvasWillDraw() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      ChartVSAssemblyInfo info = newChart();
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      ChartLinePaneModel model = new ChartLinePaneModel(info.getVSChartInfo(), plot);

      // the accessor really is getyGridLineColor - lowercase after "get", matching the field
      assertEquals("#3a383d", model.getyGridLineColor().toLowerCase(),
                   "the pane must report the stored value, which is what the canvas draws");
   }

   @Test
   void resubmittingTheReportedColourWritesNoUserTier() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      ChartVSAssemblyInfo info = newChart();
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      ChartLinePaneModel model = new ChartLinePaneModel(info.getVSChartInfo(), plot);

      // an Apply with nothing changed: the guard must not promote the default into the USER tier
      model.updateChartLinePaneModel(info.getVSChartInfo(), plot);

      // the discriminating probe: overwrite the DEFAULT tier and see whether it shows through.
      // A USER tier written by the Apply would mask this, so a stale green means the guard broke.
      // Do NOT probe by clearing USER instead - a USER copy of the same colour is indistinguishable
      // from no USER tier at all, so that version of this test passes vacuously either way.
      plot.setYGridColor(Color.GREEN, CompositeValue.Type.DEFAULT);
      assertEquals(Color.GREEN, plot.getYGridColor(),
                   "an unchanged Apply must not leave a USER tier masking the default");
   }
}
