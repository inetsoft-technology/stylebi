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
 * Unit tests for VSWizardBindingHandler.applyWizardSmoothLines, the ungated re-assertion that
 * must keep wizard-created Area/Circular charts gate-unowned even though initDefaultFormat's
 * gated seed already ran and marked them gate-owned.
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

   private void assertUngatedSmooth(int chartType) {
      ChartVSAssembly assembly = newChart(chartType);
      // reproduce the gated seed
      assembly.initDefaultFormat();
      HANDLER.applyWizardSmoothLines(assembly);
      PlotDescriptor plotDesc = assembly.getChartDescriptor().getPlotDescriptor();
      assertTrue(plotDesc.isSmoothLinesValue());
      assertFalse(plotDesc.isModernSmoothSeed(),
                  "area/circular stay ungated after the wizard hook re-asserts");
   }

   @Test
   void areaStaysUngatedInBothGateStates() {
      withGate("true", () -> assertUngatedSmooth(GraphTypes.CHART_AREA));
      withGate("false", () -> assertUngatedSmooth(GraphTypes.CHART_AREA));
   }

   @Test
   void areaStackStaysUngatedInBothGateStates() {
      withGate("true", () -> assertUngatedSmooth(GraphTypes.CHART_AREA_STACK));
      withGate("false", () -> assertUngatedSmooth(GraphTypes.CHART_AREA_STACK));
   }

   @Test
   void circularStaysUngatedInBothGateStates() {
      withGate("true", () -> assertUngatedSmooth(GraphTypes.CHART_CIRCULAR));
      withGate("false", () -> assertUngatedSmooth(GraphTypes.CHART_CIRCULAR));
   }

   @Test
   void lineChartSeedIsLeftAlone() {
      // Line is not one of the ungated types; the wizard hook must not touch the gated seed
      withGate("true", () -> {
         ChartVSAssembly assembly = newChart(GraphTypes.CHART_LINE);
         assembly.initDefaultFormat();
         HANDLER.applyWizardSmoothLines(assembly);
         PlotDescriptor plotDesc = assembly.getChartDescriptor().getPlotDescriptor();
         assertTrue(plotDesc.isSmoothLinesValue(), "gated seed already set this");
         assertTrue(plotDesc.isModernSmoothSeed(), "Line stays gate-owned; the hook is a no-op here");
      });
   }
}
