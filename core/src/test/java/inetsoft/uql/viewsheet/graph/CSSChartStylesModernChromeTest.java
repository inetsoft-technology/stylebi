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
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.internal.VSChartChromeDefaults;
import inetsoft.util.css.CSSDictionary;
import inetsoft.util.css.CSSParameter;
import inetsoft.util.css.CSSStyle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The modern in-graph chrome baseline written by CSSChartStyles.apply. Gate off
 * leaves the legacy GDefaults chrome; gate on recolors gridline/facet/legend-border to the modern
 * neutral on the CSS tier, and a user value (USER tier) still wins.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CSSChartStylesModernChromeTest {
   @Test
   void gateOffKeepsLegacyChrome() {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", "false");
         VSChartInfo info = new VSChartInfo();
         ChartDescriptor desc = new ChartDescriptor();

         CSSChartStyles.apply(desc, info, null, null);

         assertEquals(GDefaults.DEFAULT_GRIDLINE_COLOR, desc.getPlotDescriptor().getXGridColor(),
                      "gate off keeps the legacy gridline color");
         assertEquals(GDefaults.DEFAULT_LINE_COLOR, desc.getLegendsDescriptor().getBorderColor(),
                      "gate off keeps the legacy legend border color");
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }

   @Test
   void gateOnAppliesModernChrome() {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", "true");
         VSChartInfo info = new VSChartInfo();
         ChartDescriptor desc = new ChartDescriptor();

         CSSChartStyles.apply(desc, info, null, null);

         Color modern = VSChartChromeDefaults.gridlineColor();
         assertEquals(modern, desc.getPlotDescriptor().getXGridColor());
         assertEquals(modern, desc.getPlotDescriptor().getYGridColor());
         assertEquals(modern, desc.getPlotDescriptor().getFacetGridColor());
         assertEquals(VSChartChromeDefaults.legendBorderColor(),
                      desc.getLegendsDescriptor().getBorderColor());
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }

   @Test
   void customerFormatCssWinsOverModernChrome() {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", "true");
         VSChartInfo info = new VSChartInfo();
         ChartDescriptor desc = new ChartDescriptor();

         // a customer format.css ChartPlot rule sets only the x gridline color; the modern baseline
         // fills the rest. The dictionary write lands on the CSS tier after the modern baseline, so
         // the customer value must win on x while y keeps the modern default.
         CSSDictionary dict = mock(CSSDictionary.class);
         CSSStyle plotStyle = mock(CSSStyle.class);
         when(plotStyle.getCustomProperties()).thenReturn(Map.of("line_x_color", "#FF0000"));
         when(dict.getStyle(any(CSSParameter[].class))).thenReturn(plotStyle);

         CSSChartStyles.apply(desc, info, dict, null);

         assertEquals(Color.RED, desc.getPlotDescriptor().getXGridColor(),
                      "customer format.css line_x_color overrides the modern baseline");
         assertEquals(VSChartChromeDefaults.gridlineColor(), desc.getPlotDescriptor().getYGridColor(),
                      "y grid, unset by format.css, keeps the modern baseline");
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }

   @Test
   void userValueWinsOverModernChrome() {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", "true");
         VSChartInfo info = new VSChartInfo();
         ChartDescriptor desc = new ChartDescriptor();
         // a user-set grid color (USER tier) must survive the modern CSS-tier baseline
         desc.getPlotDescriptor().setXGridColor(Color.RED, CompositeValue.Type.USER);

         CSSChartStyles.apply(desc, info, null, null);

         assertEquals(Color.RED, desc.getPlotDescriptor().getXGridColor(),
                      "an explicit user grid color beats the modern default");
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }

   @Test
   void axisLineResolvesToModernWhenGateOnAndDefault() {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", "true");
         assertEquals(VSChartChromeDefaults.gridlineColor(),
                      VSChartChromeDefaults.resolveAxisLineColor(GDefaults.DEFAULT_LINE_COLOR),
                      "legacy-default axis line unifies with the gridlines under the gate");
         assertEquals(Color.RED, VSChartChromeDefaults.resolveAxisLineColor(Color.RED),
                      "a customer/user axis-line color is preserved");
         assertNull(VSChartChromeDefaults.resolveAxisLineColor(null),
                    "null (no line color) stays null");
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }

   @Test
   void axisLineUnchangedWhenGateOff() {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", "false");
         assertEquals(GDefaults.DEFAULT_LINE_COLOR,
                      VSChartChromeDefaults.resolveAxisLineColor(GDefaults.DEFAULT_LINE_COLOR),
                      "gate off leaves the legacy axis-line color unchanged");
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }
}
