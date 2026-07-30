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
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * New charts created under the modern gate carry a seeded bar corner radius; charts created with the
 * gate off, and charts loaded from saved XML, stay square.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartVSAssemblyInfoBarRoundingTest {
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

   private PlotDescriptor newChartPlot() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      return info.getChartDescriptor().getPlotDescriptor();
   }

   @Test
   void barRadiusSeededUnderGate() {
      withGate("true", () -> {
         PlotDescriptor plot = newChartPlot();
         assertEquals(0.3, plot.getBarCornerRadius(), 1e-9);
         assertTrue(plot.isModernCornerSeed(), "seeded value is marked as gate-owned");
      });
   }

   @Test
   void barRadiusNotSeededGateOff() {
      withGate("false", () -> {
         PlotDescriptor plot = newChartPlot();
         assertEquals(0.0, plot.getBarCornerRadius(), 1e-9);
         assertFalse(plot.isModernCornerSeed());
      });
   }

   @Test
   void seededBarRadiusRevertsWhenGateTurnedOff() {
      PlotDescriptor[] holder = new PlotDescriptor[1];
      withGate("true", () -> holder[0] = newChartPlot());

      withGate("false", () -> assertEquals(0.0, holder[0].getBarCornerRadius(), 1e-9,
                                           "a chart made under the gate goes square once it is off"));
      withGate("true", () -> assertEquals(0.3, holder[0].getBarCornerRadius(), 1e-9,
                                          "and rounds again when the gate returns"));
   }

   @Test
   void roundAllCornersStaysOffByDefault() {
      withGate("true", () -> assertFalse(newChartPlot().isBarRoundAllCorners(),
                                         "standard bars round the value end only"));
   }

   @Test
   void legendRoundCornersStillSeeded() {
      // guards the pre-existing new-chart default sitting on the line above the new seed
      withGate("true", () -> {
         ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
         info.initDefaultFormat();
         assertTrue(info.getChartDescriptor().getLegendsDescriptor().isRoundCorners());
      });
   }

   @Test
   void smoothLinesSeededUnderGate() {
      withGate("true", () -> {
         PlotDescriptor plot = newChartPlot();
         assertTrue(plot.isSmoothLines());
         assertTrue(plot.isModernSmoothSeed(), "seeded value is marked as gate-owned");
      });
   }

   @Test
   void smoothLinesNotSeededGateOff() {
      withGate("false", () -> {
         PlotDescriptor plot = newChartPlot();
         assertFalse(plot.isSmoothLines());
         assertFalse(plot.isModernSmoothSeed());
      });
   }

   @Test
   void seededSmoothLinesRevertsWhenGateTurnedOff() {
      PlotDescriptor[] holder = new PlotDescriptor[1];
      withGate("true", () -> holder[0] = newChartPlot());

      withGate("false", () -> assertFalse(holder[0].isSmoothLines(),
                                          "a chart made under the gate goes straight once it is off"));
      withGate("true", () -> assertTrue(holder[0].isSmoothLines(),
                                        "and smooths again when the gate returns"));
   }
}
