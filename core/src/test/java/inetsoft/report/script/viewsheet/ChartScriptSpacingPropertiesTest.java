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
import inetsoft.report.script.AxisScriptable;
import inetsoft.report.script.ChartArray;
import inetsoft.report.script.TitleScriptable;
import inetsoft.test.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
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
 * Bug #79361: the chart script properties for the axis label distance ({@code labelGap} on
 * {@code xAxis}, {@code yAxis}, {@code y2Axis} and {@code axis['field']}), the axis title
 * distance ({@code labelGap} on the four titles) and the legend distance ({@code legendGap}).
 * Each write goes through the chart scriptable's {@code putMember}, the entry a chart script's
 * assignment reaches, and must land in the runtime descriptor GraphGenerator reads, in the USER
 * tier, so an explicit 0 is kept and the author's value beats the density default.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartScriptSpacingPropertiesTest {
   @BeforeEach
   void setUp() {
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs79361");

      ChartVSAssembly chart = new ChartVSAssembly();
      info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setName("Chart1");
      info.setVizMark(VizMark.MODERN_LIGHT);

      state = new VSChartDimensionRef(new AttributeRef(null, "state"));
      VSChartAggregateRef sales = new VSChartAggregateRef();
      sales.setDataRef(new AttributeRef(null, "sales"));
      VSChartAggregateRef profit = new VSChartAggregateRef();
      profit.setDataRef(new AttributeRef(null, "profit"));
      profit.setSecondaryY(true);

      cinfo = new DefaultVSChartInfo();
      cinfo.setSeparatedGraph(false);
      cinfo.setRTXFields(new ChartRef[] { state });
      cinfo.setRTYFields(new ChartRef[] { sales, profit });
      info.setVSChartInfo(cinfo);
      viewsheet.addAssembly(chart);

      ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
      when(sandbox.getID()).thenReturn("vs79361");
      when(sandbox.getViewsheet()).thenReturn(viewsheet);

      scriptable = new ChartVSAScriptable(sandbox);
      scriptable.setAssembly("Chart1");
      scriptable.addProperties();
      ctx = VizContext.of(info);
      assertTrue(ctx.modern, "the chart must render with modern chrome for density defaults");
   }

   @Test
   void xAxisLabelGapWritesTheDimensionsRuntimeDescriptor() {
      AxisScriptable xAxis = (AxisScriptable) scriptable.getMember("xAxis");
      assertTrue(xAxis.hasMember("labelGap"));

      AxisDescriptor rt = state.getRTAxisDescriptor();
      assertNotNull(rt);
      int density = resolveAxis(rt);
      assertNotEquals(0, density, "a modern chart without an opinion draws a density gap");

      xAxis.putMember("labelGap", 0.0);
      assertAxisUserValue(rt, 0);

      xAxis.putMember("labelGap", 15);
      assertAxisUserValue(rt, 15);

      // the design-time descriptor is not touched by script
      assertFalse(state.getAxisDescriptor().hasLabelGapUserValue());
   }

   @Test
   void yAxisAndY2AxisLabelGapWriteTheSharedMeasureAxes() {
      AxisScriptable yAxis = (AxisScriptable) scriptable.getMember("yAxis");
      AxisScriptable y2Axis = (AxisScriptable) scriptable.getMember("y2Axis");

      yAxis.putMember("labelGap", 0);
      assertAxisUserValue(cinfo.getRTAxisDescriptor(), 0);
      assertEquals(0, ((Number) yAxis.getMember("labelGap")).intValue());

      y2Axis.putMember("labelGap", 11);
      assertAxisUserValue(cinfo.getRTAxisDescriptor2(), 11);
      assertEquals(11, ((Number) y2Axis.getMember("labelGap")).intValue());
      assertEquals(0, cinfo.getRTAxisDescriptor().getLabelGap(), "y2Axis must not move yAxis");
   }

   @Test
   void fieldAxisLabelGapWritesThatFieldsDescriptor() {
      ChartArray axes = (ChartArray) scriptable.getMember("axis");
      AxisScriptable stateAxis = (AxisScriptable) axes.getMember("state");
      assertTrue(stateAxis.hasMember("labelGap"));

      stateAxis.putMember("labelGap", 0);
      assertAxisUserValue(state.getRTAxisDescriptor(), 0);
      assertEquals(0, ((Number) stateAxis.getMember("labelGap")).intValue());

      stateAxis.putMember("labelGap", 5);
      assertAxisUserValue(state.getRTAxisDescriptor(), 5);
      assertEquals(5, ((Number) stateAxis.getMember("labelGap")).intValue());
   }

   @Test
   void titleLabelGapWritesEachTitleDescriptor() {
      TitlesDescriptor titles = scriptable.getRTChartDescriptor().getTitlesDescriptor();
      String[] names = { "xTitle", "x2Title", "yTitle", "y2Title" };
      TitleDescriptor[] descs = { titles.getXTitleDescriptor(), titles.getX2TitleDescriptor(),
                                  titles.getYTitleDescriptor(), titles.getY2TitleDescriptor() };

      for(int i = 0; i < names.length; i++) {
         TitleScriptable title = (TitleScriptable) scriptable.getMember(names[i]);
         TitleDescriptor desc = descs[i];
         assertTrue(title.hasMember("labelGap"), names[i]);
         assertNotEquals(0, resolveTitle(desc), names[i] + " density default");

         title.putMember("labelGap", 0);
         assertTrue(desc.hasLabelGapUserValue(), names[i] + " explicit 0 is a USER value");
         assertEquals(0, desc.getLabelGap(), names[i]);
         assertEquals(0, resolveTitle(desc), names[i] + " explicit 0 beats the density default");

         title.putMember("labelGap", 9 + i);
         assertEquals(9 + i, desc.getLabelGap(), names[i]);
         assertEquals(9 + i, resolveTitle(desc), names[i]);
         assertEquals(9 + i, ((Number) title.getMember("labelGap")).intValue(), names[i]);
      }

      TitleDescriptor xDesign = info.getChartDescriptor().getTitlesDescriptor()
         .getXTitleDescriptor();
      assertFalse(xDesign.hasLabelGapUserValue(), "the design-time title is not touched");
   }

   @Test
   void legendGapWritesTheRuntimeLegendsDescriptor() {
      assertTrue(scriptable.hasMember("legendGap"));
      LegendsDescriptor legends = scriptable.getRTChartDescriptor().getLegendsDescriptor();
      assertNotEquals(0, resolveLegend(legends), "density default");

      scriptable.putMember("legendGap", 0);
      assertTrue(legends.hasGapUserValue(), "explicit 0 is a USER value");
      assertEquals(0, legends.getGap());
      assertEquals(0, resolveLegend(legends), "explicit 0 beats the density default");

      scriptable.putMember("legendGap", 18.0);
      assertEquals(18, legends.getGap());
      assertEquals(18, resolveLegend(legends));
      assertEquals(18, ((Number) scriptable.getMember("legendGap")).intValue());

      assertFalse(info.getChartDescriptor().getLegendsDescriptor().hasGapUserValue(),
                  "the design-time legends descriptor is not touched");
   }

   private void assertAxisUserValue(AxisDescriptor desc, int value) {
      assertTrue(desc.hasLabelGapUserValue(), "script writes the USER tier");
      assertEquals(value, desc.getLabelGap());
      assertEquals(value, resolveAxis(desc), "the author's value beats the density default");
   }

   // the same resolver calls GraphGenerator makes for the axis, title and legend gaps
   private int resolveAxis(AxisDescriptor desc) {
      return VSChartChromeDefaults.resolveAxisLabelGap(
         desc.getLabelGap(), desc.hasLabelGapValue(), ctx);
   }

   private int resolveTitle(TitleDescriptor desc) {
      return VSChartChromeDefaults.resolveAxisTitleGap(
         desc.getLabelGap(), desc.hasLabelGapValue(), true, ctx);
   }

   private int resolveLegend(LegendsDescriptor desc) {
      return VSChartChromeDefaults.resolveLegendGap(desc.getGap(), desc.hasGapValue(), ctx);
   }

   private ChartVSAScriptable scriptable;
   private ChartVSAssemblyInfo info;
   private VSChartInfo cinfo;
   private VSChartDimensionRef state;
   private VizContext ctx;
}
