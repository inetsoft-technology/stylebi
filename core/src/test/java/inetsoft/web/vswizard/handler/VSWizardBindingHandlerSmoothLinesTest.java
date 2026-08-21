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
package inetsoft.web.vswizard.handler;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VSWizardBindingHandler.applyWizardSmoothLines, the wizard's own statement that
 * Area/Circular charts are created smooth. initDefaultFormat now writes that same default itself,
 * so the value is forced off before the handler runs: only the handler can turn it back on, which
 * is what keeps these tests sensitive to its body instead of passing on the creation default alone.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSWizardBindingHandlerSmoothLinesTest {
   // no collaborator of VSWizardBindingHandler is touched by applyWizardSmoothLines, so nulls
   // are safe here
   private static final VSWizardBindingHandler HANDLER = new VSWizardBindingHandler(
      null, null, null, null, null, null, null, null, null, null, null, null, null);

   private void withGate(String value, Runnable body) {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", value);
         body.run();
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }

   private ChartVSAssembly newChart(int chartType) {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly assembly = new ChartVSAssembly(vs, "chart1");
      assembly.getVSChartInfo().setChartType(chartType);
      return assembly;
   }

   /**
    * Forces smoothLines off after creation, so the handler is the only thing left that can turn
    * it back on - an emptied handler body would leave this false and fail the assertion.
    */
   private void assertHandlerTurnsSmoothLinesOn(int chartType) {
      ChartVSAssembly assembly = newChart(chartType);
      // creation writes the plot defaults first
      assembly.initDefaultFormat();
      PlotDescriptor plotDesc = assembly.getChartDescriptor().getPlotDescriptor();
      plotDesc.setSmoothLines(false);
      HANDLER.applyWizardSmoothLines(assembly);
      assertTrue(plotDesc.isSmoothLines());
   }

   @Test
   void areaStaysSmoothInBothGateStates() {
      withGate("true", () -> assertHandlerTurnsSmoothLinesOn(GraphTypes.CHART_AREA));
      withGate("false", () -> assertHandlerTurnsSmoothLinesOn(GraphTypes.CHART_AREA));
   }

   @Test
   void areaStackStaysSmoothInBothGateStates() {
      withGate("true", () -> assertHandlerTurnsSmoothLinesOn(GraphTypes.CHART_AREA_STACK));
      withGate("false", () -> assertHandlerTurnsSmoothLinesOn(GraphTypes.CHART_AREA_STACK));
   }

   @Test
   void circularStaysSmoothInBothGateStates() {
      withGate("true", () -> assertHandlerTurnsSmoothLinesOn(GraphTypes.CHART_CIRCULAR));
      withGate("false", () -> assertHandlerTurnsSmoothLinesOn(GraphTypes.CHART_CIRCULAR));
   }

   @Test
   void lineChartCreationDefaultIsLeftAlone() {
      // Line is not one of the types this hook re-asserts, so it must not touch the value
      withGate("true", () -> {
         ChartVSAssembly assembly = newChart(GraphTypes.CHART_LINE);
         assembly.initDefaultFormat();
         PlotDescriptor plotDesc = assembly.getChartDescriptor().getPlotDescriptor();
         plotDesc.setSmoothLines(false);
         HANDLER.applyWizardSmoothLines(assembly);
         assertFalse(plotDesc.isSmoothLines(), "not a smooth-lines default type, so left alone");
      });
   }

   @Test
   void wizardCreatedAreaChartEndsUpSmoothRegardlessOfWhichWriterDidIt() {
      // end-to-end guard on the property both the handler and initDefaultFormat independently
      // assert: an Area chart the wizard creates is smooth, whichever of the two wrote it
      withGate("true", () -> {
         ChartVSAssembly assembly = newChart(GraphTypes.CHART_AREA);
         assembly.initDefaultFormat();
         HANDLER.applyWizardSmoothLines(assembly);
         assertTrue(assembly.getChartDescriptor().getPlotDescriptor().isSmoothLines());
      });
      withGate("false", () -> {
         ChartVSAssembly assembly = newChart(GraphTypes.CHART_AREA);
         assembly.initDefaultFormat();
         HANDLER.applyWizardSmoothLines(assembly);
         assertTrue(assembly.getChartDescriptor().getPlotDescriptor().isSmoothLines());
      });
   }
}
