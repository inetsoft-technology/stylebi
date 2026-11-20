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
package inetsoft.report.composition.graph;

import inetsoft.graph.AxisSpec;
import inetsoft.graph.EGraph;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.AbstractParallelCoord;
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.guide.axis.GridLine;
import inetsoft.graph.guide.axis.PolarAxis;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.Scale;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The grid, spokes and web a marked radar is built with.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernRadarWebTest {
   private static final Color LIGHT = Color.decode("#D6D2C8");
   private static final Color DARK = Color.decode("#4A484F");

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedRadarRingsInTheGridColour() {
      Color grid = seeded(LIGHT);

      for(AxisSpec spec : spokeSpecs(VizMark.MODERN_LIGHT, LIGHT, d -> { }, r -> { })) {
         assertEquals(GraphConstants.THIN_LINE, spec.getGridStyle());
         assertEquals(grid, spec.getGridColor());
         assertEquals(LIGHT, spec.getGridColor());
         assertEquals(LIGHT, spec.getLineColor());
      }
   }

   @Test
   void aDarkMarkUsesTheDarkNeutral() {
      for(AxisSpec spec : spokeSpecs(VizMark.MODERN_DARK, DARK, d -> { }, r -> { })) {
         assertEquals(DARK, spec.getGridColor());
         assertEquals(DARK, spec.getLineColor());
      }
   }

   @Test
   void anAuthorsGridStyleWins() {
      Consumer<ChartDescriptor> none =
         d -> d.getPlotDescriptor().setYGridStyle(GraphConstants.NONE);

      for(AxisSpec spec : spokeSpecs(VizMark.MODERN_LIGHT, LIGHT, none, r -> { })) {
         assertEquals(GraphConstants.NONE, spec.getGridStyle());
      }
   }

   @Test
   void anAuthorsSpokeColourWins() {
      Consumer<RadarChartInfo> red = r ->
         ((VSChartAggregateRef) r.getAggregateRefs()[0]).getAxisDescriptor()
            .setLineColor(Color.RED);
      List<AxisSpec> specs = spokeSpecs(VizMark.MODERN_LIGHT, LIGHT, d -> { }, red);

      // the spokes' order is the coordinate's, so count rather than index
      assertEquals(1, specs.stream().filter(sp -> Color.RED.equals(sp.getLineColor())).count());
      assertEquals(2, specs.stream().filter(sp -> LIGHT.equals(sp.getLineColor())).count());
   }

   @Test
   void theWebIsOnForMarkedRadars() {
      PolarCoord polar =
         (PolarCoord) build(VizMark.MODERN_LIGHT, LIGHT, d -> { }, r -> { }).getCoordinate();

      assertTrue(polar.isWebGrid());
      assertTrue(polar.isSidesFromSpokes());
      assertTrue(polar.isModernWeb());
      assertTrue(((AbstractParallelCoord) polar.getCoordinate()).isEvenRings());
   }

   @Test
   void classicRadarsAreUnchanged() {
      PolarCoord polar = (PolarCoord) build(null, null, d -> { }, r -> { }).getCoordinate();
      AbstractParallelCoord inner = (AbstractParallelCoord) polar.getCoordinate();

      assertFalse(polar.isWebGrid());
      assertFalse(polar.isSidesFromSpokes());
      assertFalse(polar.isModernWeb());
      assertFalse(inner.isEvenRings());

      int checked = 0;

      for(Scale scale : inner.getScales()) {
         AxisSpec spec = scale.getAxisSpec();

         if(spec != null) {
            assertEquals(GraphConstants.NONE, spec.getGridStyle());
            assertEquals(GDefaults.DEFAULT_LINE_COLOR, spec.getLineColor());
            checked++;
         }
      }

      assertEquals(3, checked, "premise: one spec per spoke");
   }

   @Test
   void anAuthorMeasureAxisColourReachesEverySpoke() {
      Consumer<RadarChartInfo> red = r -> {
         for(VSDataRef ref : r.getAggregateRefs()) {
            ((VSChartAggregateRef) ref).getAxisDescriptor().setLineColor(Color.RED);
         }
      };
      List<GridLine> spokes = spokeLines(plot(VizMark.MODERN_LIGHT, LIGHT, d -> { }, red));

      assertEquals(3, spokes.size());

      for(GridLine line : spokes) {
         assertEquals(Color.RED, line.getColor());
      }
   }

   @Test
   void anAuthorLabelAxisColourReachesTheOuterRing() {
      Consumer<RadarChartInfo> blue = r -> r.getLabelAxisDescriptor().setLineColor(Color.BLUE);
      VGraph vgraph = plot(VizMark.MODERN_LIGHT, LIGHT, d -> { }, blue);
      List<GridLine> spokes = spokeLines(vgraph);

      assertEquals(Color.BLUE, outerLine(vgraph).getColor());
      assertEquals(3, spokes.size());

      for(GridLine line : spokes) {
         assertEquals(LIGHT, line.getColor());
      }
   }

   @Test
   void webLinesDefaultToTheGridColour() {
      for(VizMark mark : new VizMark[] { VizMark.MODERN_LIGHT, VizMark.MODERN_DARK }) {
         Color grid = mark == VizMark.MODERN_DARK ? DARK : LIGHT;
         List<GridLine> web = webLines(plot(mark, grid, d -> { }, r -> { }));

         assertEquals(4, web.size(), mark + ": three spokes and the outer ring");

         for(GridLine line : web) {
            assertEquals(grid, line.getColor(), mark.toString());
         }
      }
   }

   @Test
   void theYGridColourReachesOnlyTheRings() {
      Consumer<ChartDescriptor> green = d -> d.getPlotDescriptor().setYGridColor(Color.GREEN);
      VGraph vgraph = plot(VizMark.MODERN_LIGHT, LIGHT, green, r -> { });
      List<GridLine> web = webLines(vgraph);
      int rings = 0;

      for(Axis axis : vgraph.getCoordinate().getAxes(false)) {
         for(int i = 0; i < axis.getGridLineCount(); i++) {
            assertEquals(Color.GREEN, axis.getGridLine(i).getColor());
            rings++;
         }
      }

      assertTrue(rings > 0, "premise: inner rings");
      assertEquals(4, web.size(), "three spokes and the outer ring");

      for(GridLine line : web) {
         assertEquals(LIGHT, line.getColor());
      }
   }

   @Test
   void aHiddenLabelAxisLineHasNoOuterRing() {
      Consumer<RadarChartInfo> hide = r -> r.getLabelAxisDescriptor().setLineVisible(false);
      VGraph vgraph = plot(VizMark.MODERN_LIGHT, LIGHT, d -> { }, hide);
      PolarAxis outer = null;

      for(Axis axis : vgraph.getCoordinate().getAxes(false)) {
         if(axis instanceof PolarAxis polar) {
            outer = polar;
         }
      }

      assertNotNull(outer, "premise: the spoke names' axis");
      assertTrue(webLines(vgraph).stream().noneMatch(l -> l.getAxis() instanceof PolarAxis),
                 "no outer web line");
      assertEquals(3, spokeLines(vgraph).size());
      assertTrue(outer.getZIndex() >= 0, "the spoke names' axis draws");
      assertEquals(3, outer.getLabels().length, "one name per spoke");

      for(VLabel label : outer.getLabels()) {
         assertTrue(label.getZIndex() >= 0, "a spoke name draws");
      }
   }

   @Test
   void aHiddenMeasureAxisLineHasNoSpokes() {
      Consumer<RadarChartInfo> hide = r -> {
         for(VSDataRef ref : r.getAggregateRefs()) {
            ((VSChartAggregateRef) ref).getAxisDescriptor().setLineVisible(false);
         }
      };
      VGraph vgraph = plot(VizMark.MODERN_LIGHT, LIGHT, d -> { }, hide, false);

      assertNoSpokesKeepingLabels(vgraph, false);
   }

   @Test
   void aMaxModeHiddenMeasureAxisLineHasNoSpokes() {
      Consumer<RadarChartInfo> hide = r -> {
         for(VSDataRef ref : r.getAggregateRefs()) {
            AxisDescriptor axisDesc = ((VSChartAggregateRef) ref).getAxisDescriptor();
            assertTrue(axisDesc.isLineVisible(), "premise: the line is on");
            axisDesc.setMaxModeLineVisible(false);
         }
      };
      VGraph vgraph = plot(VizMark.MODERN_LIGHT, LIGHT, d -> { }, hide, true);

      assertNoSpokesKeepingLabels(vgraph, true);
   }

   @Test
   void aHiddenLabelAxisLineStaysHiddenInMaxMode() {
      Consumer<RadarChartInfo> hide = r -> r.getLabelAxisDescriptor().setLineVisible(false);
      VGraph vgraph = plot(VizMark.MODERN_LIGHT, LIGHT, d -> { }, hide, true);

      assertTrue(webLines(vgraph).stream().noneMatch(l -> l.getAxis() instanceof PolarAxis),
                 "no outer web line");
      assertEquals(3, spokeLines(vgraph).size());
   }

   private static void assertNoSpokesKeepingLabels(VGraph vgraph, boolean maxMode) {
      assertTrue(spokeLines(vgraph).isEmpty(), "no spoke web lines");
      assertEquals(1, webLines(vgraph).stream().filter(l -> l.getAxis() instanceof PolarAxis)
                   .count(), "the outer web line stays");

      int drawn = drawnSpokeLabels(vgraph);

      assertTrue(drawn > 0, "premise: spoke value labels");
      assertEquals(drawnSpokeLabels(plot(VizMark.MODERN_LIGHT, LIGHT, d -> { }, r -> { },
                                         maxMode)),
                   drawn, "the spoke value labels still draw");
   }

   // the value labels the spoke axes keep after overlap removal
   private static int drawnSpokeLabels(VGraph vgraph) {
      int drawn = 0;

      for(Axis axis : vgraph.getCoordinate().getAxes(false)) {
         if(axis instanceof DefaultAxis) {
            for(VLabel label : axis.getLabels()) {
               drawn += label.getZIndex() >= 0 ? 1 : 0;
            }
         }
      }

      return drawn;
   }

   private static List<GridLine> webLines(VGraph vgraph) {
      return ((PolarCoord) vgraph.getCoordinate()).getWebLines();
   }

   private static List<GridLine> spokeLines(VGraph vgraph) {
      return webLines(vgraph).stream().filter(l -> l.getAxis() instanceof DefaultAxis).toList();
   }

   private static GridLine outerLine(VGraph vgraph) {
      List<GridLine> outer =
         webLines(vgraph).stream().filter(l -> l.getAxis() instanceof PolarAxis).toList();

      assertEquals(1, outer.size(), "one outer ring");
      return outer.get(0);
   }

   // a 400x240 layout of the built radar
   private static VGraph plot(VizMark mark, Color seed, Consumer<ChartDescriptor> descSetup,
                              Consumer<RadarChartInfo> infoSetup)
   {
      return plot(mark, seed, descSetup, infoSetup, false);
   }

   private static VGraph plot(VizMark mark, Color seed, Consumer<ChartDescriptor> descSetup,
                              Consumer<RadarChartInfo> infoSetup, boolean maxMode)
   {
      GraphGenerator gen = generator(mark, seed, descSetup, infoSetup, maxMode);
      VGraph vgraph =
         Plotter.getPlotter(gen.createEGraph()).plotAndLayout(gen.getData(), 0, 0, 400, 240);

      assertFalse(GTool.getVOs(vgraph).isEmpty(), "premise: a series");
      return vgraph;
   }

   private static Color seeded(Color seed) {
      PlotDescriptor plot = new PlotDescriptor();
      plot.setYGridColor(seed, CompositeValue.Type.DEFAULT);
      return plot.getYGridColor();
   }

   private static List<AxisSpec> spokeSpecs(VizMark mark, Color seed,
                                            Consumer<ChartDescriptor> descSetup,
                                            Consumer<RadarChartInfo> infoSetup)
   {
      PolarCoord polar = (PolarCoord) build(mark, seed, descSetup, infoSetup).getCoordinate();
      AbstractParallelCoord inner = (AbstractParallelCoord) polar.getCoordinate();
      List<AxisSpec> specs = new ArrayList<>();

      for(Scale scale : inner.getScales()) {
         if(scale.getAxisSpec() != null) {
            specs.add(scale.getAxisSpec());
         }
      }

      assertEquals(3, specs.size(), "premise: one spec per spoke");
      return specs;
   }

   private static EGraph build(VizMark mark, Color seed, Consumer<ChartDescriptor> descSetup,
                               Consumer<RadarChartInfo> infoSetup)
   {
      return generator(mark, seed, descSetup, infoSetup).createEGraph();
   }

   // the seed is written as the chrome seed would, the helper not running it
   private static GraphGenerator generator(VizMark mark, Color seed,
                                           Consumer<ChartDescriptor> descSetup,
                                           Consumer<RadarChartInfo> infoSetup)
   {
      return generator(mark, seed, descSetup, infoSetup, false);
   }

   private static GraphGenerator generator(VizMark mark, Color seed,
                                           Consumer<ChartDescriptor> descSetup,
                                           Consumer<RadarChartInfo> infoSetup, boolean maxMode)
   {
      RadarVSChartInfo radar = new RadarVSChartInfo();
      VSChartAggregateRef price = agg("Price", "Sum");
      VSChartAggregateRef qty = agg("Quantity", "Average");
      VSChartAggregateRef discount = agg("Discount", "Sum");
      radar.addYField(price);
      radar.addYField(qty);
      radar.addYField(discount);
      radar.setChartType(GraphTypes.CHART_RADAR);
      radar.setRTChartType(GraphTypes.CHART_RADAR);
      radar.updateChartType(false);
      infoSetup.accept(radar);
      Object[][] rows = {
         { GraphUtil.getName(price), GraphUtil.getName(qty), GraphUtil.getName(discount) },
         { 10.0, 20.0, 30.0 },
         { 30.0, 20.0, 10.0 },
      };
      ChartDescriptor desc = new ChartDescriptor();

      if(seed != null) {
         desc.getPlotDescriptor().setYGridColor(seed, CompositeValue.Type.DEFAULT);
      }

      descSetup.accept(desc);
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(radar);
      assemblyInfo.setChartDescriptor(desc);
      assemblyInfo.setVizMark(mark);

      if(maxMode) {
         Viewsheet vs = new Viewsheet();
         vs.setMaxMode(true);
         assemblyInfo.setViewsheet(vs);
      }

      return GraphGenerator.getGenerator(assemblyInfo, null, new DefaultDataSet(rows),
                                         new VariableTable(), null, XSourceInfo.NONE,
                                         new Dimension(400, 400));
   }

   private static VSChartAggregateRef agg(String name, String formula) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      BaseField field = new BaseField(name);
      field.setDataType(XSchema.DOUBLE);
      agg.setDataRef(field);
      agg.setFormulaValue(formula);
      return agg;
   }
}
