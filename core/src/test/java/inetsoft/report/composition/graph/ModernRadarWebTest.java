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
import inetsoft.graph.coord.AbstractParallelCoord;
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.internal.GDefaults;
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
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
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

   // the seed is written as the chrome seed would, the helper not running it
   private static EGraph build(VizMark mark, Color seed, Consumer<ChartDescriptor> descSetup,
                               Consumer<RadarChartInfo> infoSetup)
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
      return GraphGenerator.getGenerator(assemblyInfo, null, new DefaultDataSet(rows),
                                         new VariableTable(), null, XSourceInfo.NONE,
                                         new Dimension(400, 400)).createEGraph();
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
