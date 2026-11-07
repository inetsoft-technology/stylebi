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

import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataPath;
import inetsoft.report.internal.graph.SwapXYBindingProcessor;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * The per-type chrome seeds: what a chart's resolved type writes, and what it leaves to the author.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartChromeTypeSeedTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   // ---- the value-axis line ---------------------------------------------------------------

   @Test
   void aModernBarChartsValueAxisHasNoLine() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      VSChartDimensionRef state = dim("state");
      VSChartAggregateRef v = agg("v");
      bind(info, GraphTypes.CHART_BAR, refs(state), refs(v));
      seed(info);

      VSChartInfo cinfo = info.getVSChartInfo();
      assertFalse(cinfo.getAxisDescriptor().isLineVisible(), "the value axis");
      assertFalse(cinfo.getAxisDescriptor2().isLineVisible(), "the secondary value axis");
      assertTrue(state.getAxisDescriptor().isLineVisible(), "the category baseline");
      assertFalse(v.getAxisDescriptor().isLineVisible(), "the measure's own axis");
      assertNoFlag(cinfo);
   }

   @Test
   void aHorizontalBarsValueAxisHasNoLine() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      VSChartDimensionRef state = dim("state");
      VSChartAggregateRef v = agg("v");
      bind(info, GraphTypes.CHART_BAR, refs(v), refs(state));
      seed(info);

      assertFalse(info.getVSChartInfo().getAxisDescriptor().isLineVisible(), "the value axis");
      assertTrue(state.getAxisDescriptor().isLineVisible(), "the category baseline");
      assertFalse(v.getAxisDescriptor().isLineVisible(), "the measure's own axis");
   }

   @Test
   void aSeparatedChartsMeasureAxesHaveNoLine() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      VSChartAggregateRef v = agg("v");
      VSChartAggregateRef w = agg("w");
      info.getVSChartInfo().setSeparatedGraph(true);
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(v, w));
      seed(info);

      assertFalse(v.getAxisDescriptor().isLineVisible(), "Sum(v)");
      assertFalse(w.getAxisDescriptor().isLineVisible(), "Sum(w)");
   }

   @Test
   void aScatterKeepsBothLines() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      VSChartAggregateRef w = agg("w");
      VSChartAggregateRef v = agg("v");
      bind(info, GraphTypes.CHART_POINT, refs(w), refs(v));
      seed(info);

      assertTrue(info.getVSChartInfo().getAxisDescriptor().isLineVisible(), "the chart's axis");
      assertTrue(w.getAxisDescriptor().isLineVisible(), "Sum(w)");
      assertTrue(v.getAxisDescriptor().isLineVisible(), "Sum(v)");
   }

   @Test
   void aClassicChartKeepsEveryLine() throws Exception {
      gateOff();
      ChartVSAssembly chart = chartOn(new Viewsheet());
      ChartVSAssemblyInfo info = chart.getChartInfo();
      VSChartAggregateRef v = agg("v");
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(v));

      try(MockedStatic<GraphTypeUtil> types = permitChartTypes()) {
         render(chart);
      }

      // a legacy pass over the rendered chart, as a restore runs
      seed(info);

      VSChartInfo cinfo = info.getVSChartInfo();
      ChartRef rt = runtime(cinfo, v);
      assertNotNull(rt, "premise: the render built the runtime refs");
      assertNotSame(v, rt, "premise: the runtime ref is its own object");
      assertEveryLineOn(cinfo);
      assertNoFlag(cinfo);
   }

   @Test
   void anAuthorsLinesSurviveModernizeAndRevert() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      VSChartDimensionRef state = dim("state");
      bind(info, GraphTypes.CHART_BAR, refs(state), refs(agg("v")));
      seed(info);
      AxisDescriptor chartAxis = info.getVSChartInfo().getAxisDescriptor();
      state.getAxisDescriptor().setAuthorLineVisible(false);
      chartAxis.setAuthorLineVisible(true);

      modernize(info);
      assertFalse(state.getAxisDescriptor().isLineVisible(), "Modernize, the author's off");
      assertTrue(chartAxis.isLineVisible(), "Modernize, the author's on");

      revert(info);
      assertFalse(state.getAxisDescriptor().isLineVisible(), "Revert, the author's off");
      assertTrue(chartAxis.isLineVisible(), "Revert, the author's on");
   }

   @Test
   void anOldFilesLineOffSurvivesModernizeAndRevert() throws Exception {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      VSChartDimensionRef state = dim("state");
      bind(info, GraphTypes.CHART_BAR, refs(state), refs(agg("v")));
      seed(info);
      state.getAxisDescriptor().setLineVisible(false);
      String xml = writeXml(info);
      String old = xml.replaceAll("\\s+userLineVisible=\"[a-z]*\"", "");
      assertNotEquals(xml, old, "premise: the line wrote its flag");

      ChartVSAssemblyInfo loaded = parseInfo(old);

      modernize(loaded);
      assertFalse(stateAxis(loaded).isLineVisible(), "Modernize keeps the old file's off");
      revert(loaded);
      assertFalse(stateAxis(loaded).isLineVisible(), "Revert keeps the old file's off");
   }

   @Test
   void modernizeThenRevertRestoresEveryLine() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(agg("v")));
      seed(info);

      modernize(info);
      assertFalse(info.getVSChartInfo().getAxisDescriptor().isLineVisible(),
                  "premise: Modernize took the value line");
      revert(info);

      assertEveryLineOn(info.getVSChartInfo());
      assertNoFlag(info.getVSChartInfo());
   }

   @Test
   void aMeasureDroppedOnAMarkedChartIsSeeded() throws Exception {
      gateOn();
      ChartVSAssembly chart = chartOn(new Viewsheet());
      ChartVSAssemblyInfo info = chart.getChartInfo();
      assertNotNull(info.getVizMark(), "premise: the chart is marked");
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(agg("v")));

      try(MockedStatic<GraphTypeUtil> types = permitChartTypes()) {
         render(chart);

         VSChartAggregateRef w = agg("w");
         info.getVSChartInfo().addYField(w);
         render(chart);

         ChartRef rt = runtime(info.getVSChartInfo(), w);
         assertNotNull(rt, "premise: the render built the runtime ref");
         assertNotSame(w.getAxisDescriptor(), rt.getAxisDescriptor(),
                       "premise: the runtime ref carries its own descriptor");
         assertFalse(w.getAxisDescriptor().isLineVisible(), "the design ref");
         assertFalse(rt.getAxisDescriptor().isLineVisible(), "the runtime ref");
      }
   }

   @Test
   void aTypeReseedKeepsTheRuntimeDescriptor() {
      gateOn();
      ChartVSAssemblyInfo marked = scatter();
      // a script may already have written it this render
      ChartDescriptor rdesc = new ChartDescriptor();
      marked.setRTChartDescriptor(rdesc);

      toBar(marked);
      marked.reseedIfResolvedTypeChanged();

      assertFalse(marked.getVSChartInfo().getAxisDescriptor().isLineVisible(),
                  "premise: the per-type seed ran");
      assertSame(rdesc, marked.getRTChartDescriptor());
   }

   @Test
   void aTypeReseedWritesTheScriptsRuntimeAxisDescriptors() {
      gateOn();
      ChartVSAssemblyInfo marked = scatter();
      VSChartInfo cinfo = marked.getVSChartInfo();
      VSChartAggregateRef v = (VSChartAggregateRef) cinfo.getYField(0);
      // copies made as a script makes them, while the scatter's lines were on
      cinfo.setRTAxisDescriptor(cinfo.getAxisDescriptor().clone());
      cinfo.setRTAxisDescriptor2(cinfo.getAxisDescriptor2().clone());
      v.setRTAxisDescriptor(v.getAxisDescriptor().clone());
      assertTrue(cinfo.getRTAxisDescriptor().isLineVisible(), "premise: copied with the line on");

      toBar(marked);
      marked.reseedIfResolvedTypeChanged();

      assertFalse(cinfo.getRTAxisDescriptor().isLineVisible(), "the chart's runtime axis");
      assertFalse(cinfo.getRTAxisDescriptor2().isLineVisible(), "the chart's runtime axis 2");
      assertFalse(v.getRTAxisDescriptor().isLineVisible(), "the measure's runtime axis");
   }

   @Test
   void aScriptsAuthoredRuntimeLineSurvivesATypeReseed() {
      gateOn();
      ChartVSAssemblyInfo marked = scatter();
      VSChartInfo cinfo = marked.getVSChartInfo();
      AxisDescriptor rt = cinfo.getAxisDescriptor().clone();
      rt.setAuthorLineVisible(true);
      cinfo.setRTAxisDescriptor(rt);

      toBar(marked);
      marked.reseedIfResolvedTypeChanged();

      assertFalse(cinfo.getAxisDescriptor().isLineVisible(), "premise: the per-type seed ran");
      assertTrue(rt.isLineVisible());
   }

   // ---- the axis titles ---------------------------------------------------------------------

   @Test
   void modernTitlesFollowTheResolvedType() {
      gateOn();

      for(int type : TITLE_TYPES) {
         ChartVSAssemblyInfo info = newChart();
         bindForTitles(info, type);
         seed(info);

         for(String axis : AXES) {
            TitleDescriptor title = title(info.getChartDescriptor(), axis);
            assertEquals(VSChartTypeDefaults.axisTitleVisible(
                            ResolvedChartType.of(info.getVSChartInfo()), axis),
                         defaultVisible(title), axis + " of " + type);
            assertFalse(title.hasVisibleUserValue(), axis + " of " + type);
         }
      }

      // a box plot names its value axis, whichever way it lies
      ChartVSAssemblyInfo vertical = newChart();
      bind(vertical, GraphTypes.CHART_BOXPLOT, refs(dim("state")), refs(agg("v")));
      seed(vertical);
      assertFalse(defaultVisible(title(vertical.getChartDescriptor(), "x")), "vertical box x");
      assertTrue(defaultVisible(title(vertical.getChartDescriptor(), "y")), "vertical box y");

      ChartVSAssemblyInfo horizontal = newChart();
      bind(horizontal, GraphTypes.CHART_BOXPLOT, refs(agg("v")), refs(dim("state")));
      seed(horizontal);
      assertTrue(defaultVisible(title(horizontal.getChartDescriptor(), "x")), "horizontal box x");
      assertFalse(defaultVisible(title(horizontal.getChartDescriptor(), "y")), "horizontal box y");
      assertFalse(title(horizontal.getChartDescriptor(), "x").hasVisibleUserValue(),
                  "horizontal box x");
   }

   @Test
   void aClassicChartShowsEveryTitle() {
      gateOff();

      for(int type : TITLE_TYPES) {
         ChartVSAssemblyInfo info = newChart();
         bindForTitles(info, type);
         seed(info);

         for(String axis : AXES) {
            TitleDescriptor title = title(info.getChartDescriptor(), axis);
            assertTrue(title.isVisible(), axis + " of " + type);
            assertFalse(title.hasVisibleUserValue(), axis + " of " + type);
         }
      }
   }

   @Test
   void aTypedTitleFromAnOldFileStaysVisibleThroughModernizeAndRevert() throws Exception {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(agg("v")));
      seed(info);
      title(info.getChartDescriptor(), "x").setTitleValue("Revenue");
      String xml = writeXml(info);
      String old = xml.replaceAll("\\s+userTitle=\"[a-z]*\"", "");
      assertNotEquals(xml, old, "premise: the text wrote its flag");

      ChartVSAssemblyInfo loaded = parseInfo(old);
      TitlesDescriptor titles = loaded.getChartDescriptor().getTitlesDescriptor();

      modernize(loaded);
      assertTrue(titles.getXTitleDescriptor().isVisible(), "Modernize keeps the typed title");
      assertFalse(titles.getYTitleDescriptor().isVisible(), "Modernize hides the generated one");
      revert(loaded);
      assertTrue(titles.getXTitleDescriptor().isVisible(), "Revert, the typed title");
      assertTrue(titles.getYTitleDescriptor().isVisible(), "Revert, the generated one");
   }

   @Test
   void wizardTextDoesNotKeepATitle() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(agg("v")));
      TitleDescriptor y = title(info.getChartDescriptor(), "y");
      // as the wizard writes it
      y.setTitleValue("Frequency");

      seed(info);

      assertFalse(y.isVisible());
   }

   @Test
   void aTitleTheAuthorShowedSurvivesAReseed() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(agg("v")));
      seed(info);
      TitleDescriptor x = title(info.getChartDescriptor(), "x");
      assertFalse(defaultVisible(x), "premise: the seed hid it");
      x.setVisible(true);

      info.getVSChartInfo().setChartType(GraphTypes.CHART_LINE);
      info.reseedIfResolvedTypeChanged();

      assertEquals(GraphTypes.CHART_LINE, info.getSeededType().type(), "premise: the re-seed ran");
      assertTrue(x.isVisible());
   }

   @Test
   void modernizeThenRevertRestoresEveryTitle() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(agg("v")));
      seed(info);

      modernize(info);
      assertFalse(title(info.getChartDescriptor(), "x").isVisible(),
                  "premise: Modernize hid the title");
      revert(info);

      for(String axis : AXES) {
         TitleDescriptor title = title(info.getChartDescriptor(), axis);
         assertTrue(defaultVisible(title), axis);
         assertFalse(title.hasVisibleUserValue(), axis);
      }
   }

   @Test
   void aScriptsRuntimeTitleKeepsItsValueWhileItsDefaultFollowsTheType() {
      gateOn();
      ChartVSAssemblyInfo marked = newChart();
      bind(marked, GraphTypes.CHART_BAR, refs(dim("state")), refs(agg("v")));
      seed(marked);
      // a runtime copy a script has written to
      ChartDescriptor rdesc = (ChartDescriptor) marked.getChartDescriptor().clone();
      TitleDescriptor rx = rdesc.getTitlesDescriptor().getXTitleDescriptor();
      TitleDescriptor ry = rdesc.getTitlesDescriptor().getYTitleDescriptor();
      rx.setVisible(false);
      marked.setRTChartDescriptor(rdesc);
      assertFalse(defaultVisible(rx), "premise: copied while the bar's titles were off");

      // both axes become measures, so the type resolves to point
      VSChartInfo cinfo = marked.getVSChartInfo();
      cinfo.removeXField(0);
      cinfo.addXField(agg("w"));
      cinfo.setChartType(GraphTypes.CHART_POINT);
      marked.reseedIfResolvedTypeChanged();

      assertSame(rdesc, marked.getRTChartDescriptor());
      assertTrue(defaultVisible(rx), "the runtime x title's default follows the type");
      assertTrue(defaultVisible(ry), "the runtime y title's default follows the type");
      assertTrue(rx.hasVisibleUserValue(), "the script's value stays");
      assertFalse(rx.isVisible(), "the script's value still wins");
      assertTrue(ry.isVisible());
      assertTrue(title(marked.getChartDescriptor(), "x").isVisible(), "the design descriptor");
   }

   @Test
   void anAutoChartThatBecomesAPointChartGainsItsTitlesAndXGrid() throws Exception {
      gateOn();
      ChartVSAssembly chart = chartOn(new Viewsheet());
      ChartVSAssemblyInfo info = chart.getChartInfo();
      assertNotNull(info.getVizMark(), "premise: the chart is marked");
      bind(info, GraphTypes.CHART_AUTO, refs(dim("state")), refs(agg("v")));
      TitlesDescriptor titles = info.getChartDescriptor().getTitlesDescriptor();

      try(MockedStatic<GraphTypeUtil> types = permitChartTypes()) {
         render(chart);
         assertEquals(GraphTypes.CHART_BAR, info.getSeededType().type(), "premise: auto drew bars");
         assertFalse(defaultVisible(titles.getXTitleDescriptor()), "premise: bar, x");
         assertFalse(defaultVisible(titles.getYTitleDescriptor()), "premise: bar, y");
         assertEquals(StyleConstants.NONE, defaultXGrid(info.getChartDescriptor()),
                      "premise: bar, grid");

         VSChartInfo cinfo = info.getVSChartInfo();
         cinfo.removeXField(0);
         cinfo.addXField(agg("w"));
         render(chart);
      }

      assertEquals(GraphTypes.CHART_POINT, info.getSeededType().type(),
                   "premise: auto drew points");
      assertTrue(defaultVisible(titles.getXTitleDescriptor()), "x");
      assertTrue(defaultVisible(titles.getYTitleDescriptor()), "y");
      assertEquals(StyleConstants.THIN_LINE, defaultXGrid(info.getChartDescriptor()), "x grid");
   }

   @Test
   void aSwapCarriesATypedTitleToItsNewAxis() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(agg("v")));
      seed(info);
      TitleDescriptor x = title(info.getChartDescriptor(), "x");
      TitleDescriptor y = title(info.getChartDescriptor(), "y");
      x.setAuthorTitleValue("Revenue");
      assertTrue(x.isVisible(), "premise: typing showed the title");

      new SwapXYBindingProcessor(info.getVSChartInfo(), info.getChartDescriptor(),
                                 VizContext.of(info)).process();
      info.reseedIfResolvedTypeChanged();

      assertTrue(info.getSeededType().measureOnX(), "premise: the re-seed ran");
      assertEquals("Revenue", y.getTitleValue());
      assertTrue(y.isUserTitle());
      assertTrue(y.isVisible());
      assertTrue(Tool.isEmptyString(x.getTitleValue()), "x text");
      assertFalse(x.isUserTitle(), "x flag");
      assertFalse(x.hasVisibleUserValue(), "x visibility");
      assertFalse(x.isVisible(), "x follows its seed");
   }

   @Test
   void aClassicSwapMovesTheFlagButNotTheVisibility() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(agg("v")));
      seed(info);
      TitleDescriptor x = title(info.getChartDescriptor(), "x");
      TitleDescriptor y = title(info.getChartDescriptor(), "y");
      x.setAuthorTitleValue("Revenue");
      x.setVisible(false);

      new SwapXYBindingProcessor(info.getVSChartInfo(), info.getChartDescriptor(),
                                 VizContext.of(info)).process();

      assertEquals("Revenue", y.getTitleValue());
      assertTrue(y.isUserTitle());
      assertFalse(x.isUserTitle());
      assertTrue(y.isVisible(), "y as before");
      assertFalse(y.hasVisibleUserValue(), "y as before");
      assertFalse(x.isVisible(), "x as before");
   }

   // ---- the vertical grid ------------------------------------------------------------------

   @Test
   void aModernScatterGetsAVerticalGrid() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_POINT, refs(agg("w")), refs(agg("v")));
      seed(info);

      assertEquals(StyleConstants.THIN_LINE, xGrid(info));
   }

   @Test
   void aPointChartWithADimensionOnXGetsNone() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_POINT, refs(dim("state")), refs(agg("v")));
      seed(info);

      assertEquals(StyleConstants.NONE, xGrid(info));
   }

   @Test
   void aHorizontalPointChartGetsNoCategoryLines() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_POINT, refs(agg("v")), refs(dim("state")));
      seed(info);

      assertEquals(StyleConstants.NONE, xGrid(info));
   }

   @Test
   void aClassicScatterKeepsNone() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_POINT, refs(agg("w")), refs(agg("v")));
      seed(info);

      assertEquals(StyleConstants.NONE, xGrid(info));
   }

   @Test
   void anAuthorsGridSurvivesAReseed() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(agg("v")));
      seed(info);
      info.getChartDescriptor().getPlotDescriptor().setXGridStyle(StyleConstants.THIN_LINE);

      info.getVSChartInfo().setChartType(GraphTypes.CHART_LINE);
      info.reseedIfResolvedTypeChanged();

      assertEquals(GraphTypes.CHART_LINE, info.getSeededType().type(), "premise: the re-seed ran");
      assertEquals(StyleConstants.THIN_LINE, xGrid(info));
   }

   @Test
   void modernizeThenRevertRestoresTheXGrid() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_POINT, refs(agg("w")), refs(agg("v")));
      seed(info);
      assertEquals(StyleConstants.NONE, xGrid(info), "premise: classic");

      modernize(info);
      assertEquals(StyleConstants.THIN_LINE, xGrid(info), "Modernize");
      revert(info);
      assertEquals(StyleConstants.NONE, xGrid(info), "Revert");
   }

   @Test
   void aScriptsRuntimeXGridKeepsItsValueWhileItsDefaultFollowsTheType() {
      gateOn();
      ChartVSAssemblyInfo marked = scatter();
      ChartDescriptor rdesc = (ChartDescriptor) marked.getChartDescriptor().clone();
      PlotDescriptor rplot = rdesc.getPlotDescriptor();
      rplot.setXGridStyle(StyleConstants.DASH_LINE);
      marked.setRTChartDescriptor(rdesc);
      assertEquals(StyleConstants.THIN_LINE, defaultXGrid(rdesc), "premise: copied from a scatter");

      toBar(marked);
      marked.reseedIfResolvedTypeChanged();

      assertSame(rdesc, marked.getRTChartDescriptor());
      assertEquals(StyleConstants.NONE, defaultXGrid(rdesc), "the default follows the type");
      assertEquals(StyleConstants.DASH_LINE, rplot.getXGridStyle(), "the script's value stays");
      assertEquals(StyleConstants.NONE, defaultXGrid(marked.getChartDescriptor()),
                   "the design descriptor");
   }

   // ---- smoothing ---------------------------------------------------------------------------

   @Test
   void modernLinesAndAreasAreStraight() {
      gateOn();
      int[] types = {
         GraphTypes.CHART_LINE, GraphTypes.CHART_STEP, GraphTypes.CHART_JUMP,
         GraphTypes.CHART_AREA, GraphTypes.CHART_STEP_AREA, GraphTypes.CHART_AREA_STACK
      };

      for(int type : types) {
         ChartVSAssemblyInfo info = newChart();
         bind(info, type, refs(dim("state")), refs(agg("v")));
         // an unflagged value, which the seed replaces
         plot(info).setSmoothLines(true);
         seed(info);

         assertFalse(plot(info).isSmoothLines(), "type " + type);
      }
   }

   @Test
   void aModernCircularNetworkStaysCurved() {
      gateOn();
      ChartVSAssemblyInfo info = circularNetwork();

      assertTrue(plot(info).isSmoothLines());
   }

   @Test
   void aClassicAreaStaysSmooth() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSChartInfo().setChartType(GraphTypes.CHART_AREA);
      chart.getVSAssemblyInfo().initDefaultFormat();

      assertTrue(plot(chart.getChartInfo()).isSmoothLines());
   }

   @Test
   void anAuthorsSmoothingSurvivesATypeChange() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_LINE, refs(dim("state")), refs(agg("v")));
      seed(info);
      info.setUserSmoothLines(true);
      plot(info).setSmoothLines(true);

      info.getVSChartInfo().setChartType(GraphTypes.CHART_AREA);
      info.reseedIfResolvedTypeChanged();

      assertEquals(GraphTypes.CHART_AREA, info.getSeededType().type(), "premise: the re-seed ran");
      assertTrue(plot(info).isSmoothLines());
   }

   @Test
   void aTypeReseedWritesTheRuntimeSmoothing() {
      gateOn();
      ChartVSAssemblyInfo marked = circularNetwork();
      // a script-made copy, taken while the chords were curved
      ChartDescriptor rdesc = (ChartDescriptor) marked.getChartDescriptor().clone();
      marked.setRTChartDescriptor(rdesc);
      assertTrue(rdesc.getPlotDescriptor().isSmoothLines(), "premise: copied curved");

      toLine(marked);
      marked.reseedIfResolvedTypeChanged();

      assertSame(rdesc, marked.getRTChartDescriptor());
      assertFalse(plot(marked).isSmoothLines(), "the design descriptor");
      assertFalse(rdesc.getPlotDescriptor().isSmoothLines(), "the runtime descriptor");
   }

   @Test
   void aRuntimeCopyThatMatchesTheDesignFollowsATypeReseed() {
      gateOn();
      ChartVSAssemblyInfo marked = markedLine();
      // unflagged curves, as a marked chart saved before straight lines carries
      plot(marked).setSmoothLines(true);
      ChartDescriptor rdesc = runtimeCopy(marked);

      toArea(marked);

      assertFalse(plot(marked).isSmoothLines(), "the design descriptor");
      assertFalse(rdesc.getPlotDescriptor().isSmoothLines(), "the runtime descriptor");
   }

   @Test
   void aScriptsRuntimeSmoothingSurvivesATypeReseed() {
      gateOn();
      ChartVSAssemblyInfo marked = markedLine();
      ChartDescriptor rdesc = runtimeCopy(marked);
      // the script curves the runtime copy only
      rdesc.getPlotDescriptor().setSmoothLines(true);

      toArea(marked);

      assertEquals(GraphTypes.CHART_AREA, marked.getSeededType().type(),
                   "premise: the re-seed ran");
      assertFalse(plot(marked).isSmoothLines(), "the design descriptor");
      assertTrue(rdesc.getPlotDescriptor().isSmoothLines(), "the script's value stays");
   }

   @Test
   void anAuthorsSmoothingSurvivesATypeReseedOnTheRuntimeDescriptor() {
      gateOn();
      ChartVSAssemblyInfo marked = markedLine();
      marked.setUserSmoothLines(true);
      plot(marked).setSmoothLines(true);
      ChartDescriptor rdesc = runtimeCopy(marked);

      toArea(marked);

      assertEquals(GraphTypes.CHART_AREA, marked.getSeededType().type(),
                   "premise: the re-seed ran");
      assertTrue(plot(marked).isSmoothLines(), "the design descriptor");
      assertTrue(rdesc.getPlotDescriptor().isSmoothLines(), "the runtime descriptor");
   }

   @Test
   void anOldFilesSmoothedLineSurvivesModernizeAndRevert() throws Exception {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_LINE, refs(dim("state")), refs(agg("v")));
      seed(info);
      // ticked in a build that kept no flag
      plot(info).setSmoothLines(true);

      ChartVSAssemblyInfo loaded = parseInfo(withoutSmoothFlag(writeXml(info)));

      assertTrue(loaded.isUserSmoothLines(), "the old file's smoothing is the author's");
      modernize(loaded);
      assertTrue(plot(loaded).isSmoothLines(), "Modernize keeps the curves");
      revert(loaded);
      assertTrue(plot(loaded).isSmoothLines(), "Revert keeps the curves");
   }

   @Test
   void anOldFilesDefaultSmoothAreaIsNoOpinion() throws Exception {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_AREA, refs(dim("state")), refs(agg("v")));
      seed(info);
      assertTrue(plot(info).isSmoothLines(), "premise: a classic area is smooth");

      ChartVSAssemblyInfo loaded = parseInfo(withoutSmoothFlag(writeXml(info)));

      assertFalse(loaded.isUserSmoothLines());
      modernize(loaded);
      assertFalse(plot(loaded).isSmoothLines(), "Modernize straightens it");
      revert(loaded);
      assertTrue(plot(loaded).isSmoothLines(), "Revert curves it again");
   }

   @Test
   void anUneditedClassicFileSavesWhatItLoaded() throws Exception {
      gateOff();

      for(int type : new int[] { GraphTypes.CHART_LINE, GraphTypes.CHART_AREA,
                                 GraphTypes.CHART_BAR })
      {
         ChartVSAssemblyInfo info = newChart();
         bind(info, type, refs(dim("state")), refs(agg("v")));
         seed(info);
         // a saved file, as a build without the flag wrote it
         String old = withoutSmoothFlag(writeXml(parseInfo(writeXml(info))));

         assertEquals(old, writeXml(parseInfo(old)), "type " + type);
      }
   }

   // ---- Modernize then Revert ---------------------------------------------------------------

   @Test
   void aBoundBarsXmlSurvivesModernizeAndRevert() throws Exception {
      assertModernizeThenRevertKeepsTheXml(GraphTypes.CHART_BAR, refs(dim("state")));
   }

   @Test
   void aBoundScattersXmlSurvivesModernizeAndRevert() throws Exception {
      assertModernizeThenRevertKeepsTheXml(GraphTypes.CHART_POINT, refs(agg("w")));
   }

   private void assertModernizeThenRevertKeepsTheXml(int type, ChartRef[] x) throws Exception {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = chartOn(vs);
      ChartVSAssemblyInfo info = chart.getChartInfo();
      bind(info, type, x, refs(agg("v")));

      try(MockedStatic<GraphTypeUtil> types = permitChartTypes()) {
         render(chart);
         String before = writeXml(info);
         gateOn();
         VizModernizeUtil.modernize(vs);
         assertNotNull(info.getVizMark(), "premise: Modernize marked the chart");
         render(chart);
         assertNotEquals(before, writeXml(info), "premise: Modernize changed the XML");
         VizModernizeUtil.revert(vs);

         assertEquals(withoutAlphaDefinedHint(before), withoutAlphaDefinedHint(writeXml(info)));
      }
   }

   // Revert's object alpha write sets this hint bit, as it did before the chart seeds
   private static String withoutAlphaDefinedHint(String xml) {
      Matcher m = Pattern.compile("<type>" + TableDataPath.OBJECT + "</type>\\s*" +
                                  "<dataType>[^<]*</dataType>\\s*<path>\\s*</path>\\s*" +
                                  "</tableDataPath>\\s*<VSCompositeFormat[^>]*>" +
                                  "<defaultFormat><VSFormat [^>]* hint=\"(\\d+)\"").matcher(xml);
      assertTrue(m.find(), "premise: the object format's hint");
      int hint = Integer.parseInt(m.group(1)) & ~VSFormat.TRANS_VAL_DEFINED;
      return xml.substring(0, m.start(1)) + hint + xml.substring(m.end(1));
   }

   // ---- harness -----------------------------------------------------------------------------

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   private ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   private static ChartVSAssembly chartOn(Viewsheet vs) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      chart.getChartInfo().setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "T"));
      return chart;
   }

   private static void bind(ChartVSAssemblyInfo info, int type, ChartRef[] x, ChartRef[] y) {
      VSChartInfo cinfo = info.getVSChartInfo();

      for(ChartRef ref : x) {
         cinfo.addXField(ref);
      }

      for(ChartRef ref : y) {
         cinfo.addYField(ref);
      }

      cinfo.setChartType(type);
   }

   private static void seed(ChartVSAssemblyInfo info) {
      info.seedChromeDefaults(VizContext.of(info));
   }

   private static void modernize(ChartVSAssemblyInfo info) {
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));
   }

   private static void revert(ChartVSAssemblyInfo info) {
      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of(info));
   }

   private static ChartRef[] refs(ChartRef... refs) {
      return refs;
   }

   // a marked scatter, seeded, both lines on
   private ChartVSAssemblyInfo scatter() {
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_POINT, refs(agg("w")), refs(agg("v")));
      seed(info);
      return info;
   }

   // the X measure becomes a dimension, so the resolved type changes
   private static void toBar(ChartVSAssemblyInfo info) {
      VSChartInfo cinfo = info.getVSChartInfo();
      cinfo.removeXField(0);
      cinfo.addXField(dim("state"));
      cinfo.setChartType(GraphTypes.CHART_BAR);
   }

   // a seeded circular network; marked when the gate is on
   private ChartVSAssemblyInfo circularNetwork() {
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(new RelationVSChartInfo());
      info.getVSChartInfo().setChartType(GraphTypes.CHART_CIRCULAR);
      seed(info);
      return info;
   }

   // a type change swaps the network's chart info for a line chart's
   private static void toLine(ChartVSAssemblyInfo info) {
      info.setVSChartInfo(new DefaultVSChartInfo());
      bind(info, GraphTypes.CHART_LINE, refs(dim("state")), refs(agg("v")));
   }

   // a seeded line chart; marked when the gate is on
   private ChartVSAssemblyInfo markedLine() {
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_LINE, refs(dim("state")), refs(agg("v")));
      seed(info);
      return info;
   }

   // a runtime descriptor copied from the design, as a script makes it
   private static ChartDescriptor runtimeCopy(ChartVSAssemblyInfo info) {
      ChartDescriptor rdesc = (ChartDescriptor) info.getChartDescriptor().clone();
      info.setRTChartDescriptor(rdesc);
      return rdesc;
   }

   private static void toArea(ChartVSAssemblyInfo info) {
      info.getVSChartInfo().setChartType(GraphTypes.CHART_AREA);
      info.reseedIfResolvedTypeChanged();
   }

   private static PlotDescriptor plot(ChartVSAssemblyInfo info) {
      return info.getChartDescriptor().getPlotDescriptor();
   }

   // without a principal every chart type is denied
   private static MockedStatic<GraphTypeUtil> permitChartTypes() {
      MockedStatic<GraphTypeUtil> types = mockStatic(GraphTypeUtil.class, CALLS_REAL_METHODS);
      types.when(() -> GraphTypeUtil.checkChartStylePermission(anyInt())).thenReturn(true);
      return types;
   }

   private static ChartRef runtime(VSChartInfo cinfo, ChartRef design) {
      return Arrays.stream(cinfo.getRTYFields())
         .filter(ref -> design.getFullName().equals(ref.getFullName()))
         .findFirst().orElse(null);
   }

   private static AxisDescriptor stateAxis(ChartVSAssemblyInfo info) {
      return info.getVSChartInfo().getXField(0).getAxisDescriptor();
   }

   private static final String[] AXES = { "x", "x2", "y", "y2" };

   // one type per family
   private static final int[] TITLE_TYPES = {
      GraphTypes.CHART_BAR, GraphTypes.CHART_LINE, GraphTypes.CHART_AREA,
      GraphTypes.CHART_INTERVAL, GraphTypes.CHART_WATERFALL, GraphTypes.CHART_PARETO,
      GraphTypes.CHART_POINT, GraphTypes.CHART_SCATTER_CONTOUR, GraphTypes.CHART_BOXPLOT,
      GraphTypes.CHART_CANDLE, GraphTypes.CHART_STOCK, GraphTypes.CHART_MEKKO,
      GraphTypes.CHART_PIE, GraphTypes.CHART_RADAR, GraphTypes.CHART_FUNNEL,
      GraphTypes.CHART_GANTT, GraphTypes.CHART_TREEMAP
   };

   // a measure on both axes where the type plots two, else a dimension by a measure
   private static void bindForTitles(ChartVSAssemblyInfo info, int type) {
      boolean twoMeasures = GraphTypes.isPoint(type) || GraphTypes.isScatteredContour(type);
      bind(info, type, refs(twoMeasures ? agg("w") : dim("state")), refs(agg("v")));
   }

   private static TitleDescriptor title(ChartDescriptor desc, String axis) {
      TitlesDescriptor titles = desc.getTitlesDescriptor();

      return switch(axis) {
         case "x" -> titles.getXTitleDescriptor();
         case "x2" -> titles.getX2TitleDescriptor();
         case "y" -> titles.getYTitleDescriptor();
         default -> titles.getY2TitleDescriptor();
      };
   }

   private static int xGrid(ChartVSAssemblyInfo info) {
      return info.getChartDescriptor().getPlotDescriptor().getXGridStyle();
   }

   private static int defaultXGrid(ChartDescriptor desc) {
      PlotDescriptor copy = (PlotDescriptor) desc.getPlotDescriptor().clone();
      copy.resetCompositeValues(CompositeValue.Type.USER);
      copy.resetCompositeValues(CompositeValue.Type.CSS);
      return copy.getXGridStyle();
   }

   // the DEFAULT tier, read off a copy with the higher tiers cleared
   private static boolean defaultVisible(TitleDescriptor title) {
      TitleDescriptor copy = (TitleDescriptor) title.clone();
      copy.resetCompositeValues(CompositeValue.Type.USER);
      copy.resetCompositeValues(CompositeValue.Type.CSS);
      return copy.isVisible();
   }

   // the chart's descriptors and every design and runtime ref's own, script copies included
   private static List<AxisDescriptor> descriptors(VSChartInfo cinfo) {
      List<AxisDescriptor> list = new ArrayList<>();
      list.add(cinfo.getAxisDescriptor());
      list.add(cinfo.getAxisDescriptor2());
      addIfPresent(list, cinfo.getRTAxisDescriptor());
      addIfPresent(list, cinfo.getRTAxisDescriptor2());

      for(ChartRef[] refs : new ChartRef[][] { cinfo.getXFields(), cinfo.getYFields(),
                                               cinfo.getRTXFields(), cinfo.getRTYFields() }) {
         for(ChartRef ref : refs) {
            list.add(ref.getAxisDescriptor());
            addIfPresent(list, ((VSChartRef) ref).getRTAxisDescriptor());
         }
      }

      return list;
   }

   private static void addIfPresent(List<AxisDescriptor> list, AxisDescriptor desc) {
      if(desc != null) {
         list.add(desc);
      }
   }

   private static void assertEveryLineOn(VSChartInfo cinfo) {
      List<AxisDescriptor> list = descriptors(cinfo);

      for(int i = 0; i < list.size(); i++) {
         assertTrue(list.get(i).isLineVisible(), "descriptor " + i);
      }
   }

   private static void assertNoFlag(VSChartInfo cinfo) {
      List<AxisDescriptor> list = descriptors(cinfo);

      for(int i = 0; i < list.size(); i++) {
         assertFalse(list.get(i).isUserLineVisible(), "descriptor " + i);
      }
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, name));
      dim.setDataType(XSchema.STRING);
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, name));
      agg.setColumnValue(name);
      agg.setFormula(AggregateFormula.SUM);
      return agg;
   }

   private static ColumnSelection columns() {
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column("v", XSchema.DOUBLE));
      columns.addAttribute(column("state", XSchema.STRING));
      columns.addAttribute(column("w", XSchema.DOUBLE));
      return columns;
   }

   private static ColumnRef column(String name, String type) {
      ColumnRef column = new ColumnRef(new AttributeRef(null, name));
      column.setDataType(type);
      return column;
   }

   // the chart tree after the entry point needs a real runtime and NPEs here
   private static void render(ChartVSAssembly chart) throws Exception {
      try {
         chart.getChartInfo().update(chart.getViewsheet(), columns());
      }
      catch(NullPointerException e) {
         boolean chartTree = Arrays.stream(e.getStackTrace())
            .anyMatch(f -> f.getClassName().endsWith("ChartTree"));

         if(!chartTree) {
            throw e;
         }
      }
   }

   private static String writeXml(ChartVSAssemblyInfo info) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   // as a released build wrote it, without the smoothing flag
   private static String withoutSmoothFlag(String xml) {
      return xml.replaceAll("\\s+userSmoothLines=\"[a-z]*\"", "");
   }

   private static ChartVSAssemblyInfo parseInfo(String xml) throws Exception {
      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return restored;
   }
}
