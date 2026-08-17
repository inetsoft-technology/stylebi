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
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.internal.GDefaults;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.VSChartChromeDefaults;
import inetsoft.uql.viewsheet.internal.VizContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Modern chrome text-color defaults seeded in each descriptor's initDefaultFormat. Gate off keeps the
 * legacy GDefaults colors; gate on (viewsheet path only) applies the modern label/title neutrals to
 * axis labels, axis titles, legend title, and legend content.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartChromeTextColorTest {
   @Test
   void gateOffKeepsLegacyTextColors() {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", "false");

         AxisDescriptor axis = new AxisDescriptor();
         axis.initDefaultFormat(VizContext.ofGate());
         assertEquals(GDefaults.DEFAULT_TEXT_COLOR,
                      axis.getAxisLabelTextFormat().getDefaultFormat().getColor(), "axis label");

         TitleDescriptor title = new TitleDescriptor();
         title.initDefaultFormat(VizContext.ofGate());
         assertEquals(GDefaults.DEFAULT_TITLE_COLOR,
                      title.getTextFormat().getDefaultFormat().getColor(), "axis title");

         LegendsDescriptor legends = new LegendsDescriptor();
         legends.initDefaultFormat(VizContext.ofGate());
         assertEquals(GDefaults.DEFAULT_TEXT_COLOR,
                      legends.getTitleTextFormat().getDefaultFormat().getColor(), "legend title");

         LegendDescriptor legend = new LegendDescriptor();
         legend.initDefaultFormat(VizContext.ofGate());
         assertEquals(GDefaults.DEFAULT_TEXT_COLOR,
                      legend.getContentTextFormat().getDefaultFormat().getColor(), "legend content");
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }

   @Test
   void gateOnAppliesModernTextColors() {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", "true");

         AxisDescriptor axis = new AxisDescriptor();
         axis.initDefaultFormat(VizContext.ofGate());
         assertEquals(VSChartChromeDefaults.labelColor(VizContext.ofGate()),
                      axis.getAxisLabelTextFormat().getDefaultFormat().getColor(), "axis label");

         TitleDescriptor title = new TitleDescriptor();
         title.initDefaultFormat(VizContext.ofGate());
         assertEquals(VSChartChromeDefaults.titleColor(VizContext.ofGate()),
                      title.getTextFormat().getDefaultFormat().getColor(), "axis title");

         LegendsDescriptor legends = new LegendsDescriptor();
         legends.initDefaultFormat(VizContext.ofGate());
         assertEquals(VSChartChromeDefaults.titleColor(VizContext.ofGate()),
                      legends.getTitleTextFormat().getDefaultFormat().getColor(), "legend title");

         LegendDescriptor legend = new LegendDescriptor();
         legend.initDefaultFormat(VizContext.ofGate());
         assertEquals(VSChartChromeDefaults.labelColor(VizContext.ofGate()),
                      legend.getContentTextFormat().getDefaultFormat().getColor(), "legend content");
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }

   @Test
   void reportPathKeepsLegacyWhenGateOn() {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         // gate on, but the report path (vs=false) is scoped out, so it stays legacy
         SreeEnv.setProperty("viewsheet.modernVisualization", "true");

         AxisDescriptor axis = new AxisDescriptor();
         axis.initDefaultFormat(VizContext.LEGACY);
         assertEquals(GDefaults.DEFAULT_TEXT_COLOR,
                      axis.getAxisLabelTextFormat().getDefaultFormat().getColor(),
                      "report path (vs=false) is not modernized");
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }
}
