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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.LegendScriptable;
import inetsoft.test.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A script that sets the legend position or a legend title is the author's choice, and the flags
 * land on the runtime descriptors the script writes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LegendScriptAuthorTest {
   @BeforeEach
   void setUp() {
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vsLegend");

      ChartVSAssembly chart = new ChartVSAssembly();
      info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setName("Chart1");
      info.setVizMark(VizMark.MODERN_LIGHT);

      VSChartDimensionRef state = new VSChartDimensionRef(new AttributeRef(null, "state"));
      VSChartAggregateRef sales = new VSChartAggregateRef();
      sales.setDataRef(new AttributeRef(null, "sales"));
      DefaultVSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setSeparatedGraph(false);
      cinfo.setRTXFields(new ChartRef[] { state });
      cinfo.setRTYFields(new ChartRef[] { sales });
      info.setVSChartInfo(cinfo);
      viewsheet.addAssembly(chart);

      ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
      when(sandbox.getID()).thenReturn("vsLegend");
      when(sandbox.getViewsheet()).thenReturn(viewsheet);

      scriptable = new ChartVSAScriptable(sandbox);
      scriptable.setAssembly("Chart1");
      scriptable.addProperties();
   }

   @Test
   void legendPositionStampsTheRuntimeLayout() {
      LegendsDescriptor legends = scriptable.getRTChartDescriptor().getLegendsDescriptor();
      assertFalse(legends.isUserLayout(), "premise");

      scriptable.putMember("legendPosition", LegendsDescriptor.LEFT);

      assertEquals(LegendsDescriptor.LEFT, legends.getLayout());
      assertTrue(legends.isUserLayout());
      assertFalse(info.getChartDescriptor().getLegendsDescriptor().isUserLayout(),
                  "the design descriptor is not touched");
   }

   @Test
   void colorLegendTitleStampsTheRuntimeTitle() {
      LegendDescriptor legend = scriptable.getRTChartDescriptor().getLegendsDescriptor()
         .getColorLegendDescriptor();
      legend.setTitleVisible(false);
      assertFalse(legend.isUserTitleVisible(), "premise");

      LegendScriptable color = (LegendScriptable) scriptable.getMember("colorLegend");
      color.putMember("title", "Region");

      assertEquals("Region", legend.getTitle());
      assertTrue(legend.isTitleVisible());
      assertTrue(legend.isUserTitleVisible());
   }

   @Test
   void colorLegendTitleKeepsAnAuthorsHiddenTitleHidden() {
      LegendDescriptor legend = scriptable.getRTChartDescriptor().getLegendsDescriptor()
         .getColorLegendDescriptor();
      legend.setAuthorTitleVisible(false);

      LegendScriptable color = (LegendScriptable) scriptable.getMember("colorLegend");
      color.putMember("title", "Region");

      assertEquals("Region", legend.getTitle());
      assertFalse(legend.isTitleVisible());
   }

   @Test
   void titleVisibleStampsTheRuntimeVisibility() {
      LegendDescriptor legend = scriptable.getRTChartDescriptor().getLegendsDescriptor()
         .getColorLegendDescriptor();
      LegendScriptable color = (LegendScriptable) scriptable.getMember("colorLegend");

      color.putMember("titleVisible", false);

      assertFalse(legend.isTitleVisible());
      assertTrue(legend.isUserTitleVisible());
   }

   private ChartVSAssemblyInfo info;
   private ChartVSAScriptable scriptable;
}
