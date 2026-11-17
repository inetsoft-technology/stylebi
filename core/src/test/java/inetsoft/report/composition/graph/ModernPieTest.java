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

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.internal.Donut;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.ArcVOText;
import inetsoft.graph.visual.BarVO;
import inetsoft.graph.visual.VOText;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
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

import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pie rules GraphGenerator hands a polar coordinate under the modern mark.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernPieTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedPieDonutAnd3DPieAreCapped() {
      for(int type : new int[] { GraphTypes.CHART_PIE, GraphTypes.CHART_DONUT,
                                 GraphTypes.CHART_3D_PIE })
      {
         assertEquals(0.8, coord(type, VizMark.MODERN_LIGHT, d -> { }).getPieCapRatio(), 0.0001,
                      "type " + type);
      }
   }

   @Test
   void classicPiesAreNotCapped() {
      for(int type : new int[] { GraphTypes.CHART_PIE, GraphTypes.CHART_DONUT,
                                 GraphTypes.CHART_3D_PIE })
      {
         assertEquals(0, coord(type, null, d -> { }).getPieCapRatio(), 0.0001, "type " + type);
      }
   }

   @Test
   void anAuthorRatioStillReachesTheCoord() {
      PolarCoord coord = coord(GraphTypes.CHART_PIE, VizMark.MODERN_LIGHT,
                               d -> d.getPlotDescriptor().setPieRatio(0.5));

      assertEquals(0.5, coord.getPieRatio(), 0.0001);
   }

   @Test
   void aDonutAndNot3DPie() {
      for(int type : new int[] { GraphTypes.CHART_PIE, GraphTypes.CHART_DONUT }) {
         PolarCoord coord = coord(type, VizMark.MODERN_LIGHT, d -> { });

         assertTrue(coord.isClockwiseFromTop(), "type " + type);
         assertTrue(coord.drawsClockwise(), "type " + type + " is not turned");
      }

      assertFalse(coord(GraphTypes.CHART_3D_PIE, VizMark.MODERN_LIGHT, d -> { })
                     .isClockwiseFromTop());
   }

   @Test
   void classicPiesKeepTheClassicStart() {
      for(int type : new int[] { GraphTypes.CHART_PIE, GraphTypes.CHART_DONUT,
                                 GraphTypes.CHART_3D_PIE })
      {
         assertFalse(coord(type, null, d -> { }).isClockwiseFromTop(), "type " + type);
      }
   }

   @Test
   void aRadarIsNotTurned() {
      assertFalse(coord(GraphTypes.CHART_RADAR, VizMark.MODERN_LIGHT, d -> { })
                     .isClockwiseFromTop());
   }

   @Test
   void aWholeDonutKeepsItsValueInTheMiddle() {
      for(VizMark mark : new VizMark[] { null, VizMark.MODERN_LIGHT }) {
         VGraph vgraph = oneStateDonut(mark, false);
         BarVO ring = ring(vgraph);
         VOText value = ring.getVOText();
         Rectangle2D box = ring.getBounds();
         Rectangle2D text = value.getBounds();

         assertInstanceOf(Donut.class, ring.getShape(), "premise: a ring, mark " + mark);
         assertFalse(value instanceof ArcVOText, "mark " + mark);
         assertEquals(box.getCenterX(), text.getCenterX(), 1, "mark " + mark);
         assertEquals(box.getCenterY(), text.getCenterY(), 1, "mark " + mark);
      }
   }

   @Test
   void aWholeDonutDoesNotExplode() {
      for(VizMark mark : new VizMark[] { null, VizMark.MODERN_LIGHT }) {
         Rectangle2D plain = ring(oneStateDonut(mark, false)).getBounds();
         Rectangle2D exploded = ring(oneStateDonut(mark, true)).getBounds();

         assertEquals(plain, exploded, "mark " + mark);
      }
   }

   private static BarVO ring(VGraph vgraph) {
      return (BarVO) GTool.getVOs(vgraph).stream()
         .filter(BarVO.class::isInstance).findFirst().orElseThrow();
   }

   // the polar coordinate of a State-on-color pie of Sum(Sales)
   static PolarCoord coord(int type, VizMark mark, Consumer<ChartDescriptor> setup) {
      VSChartInfo pie = new DefaultVSChartInfo();
      VSChartAggregateRef sales = agg("Sales", "Sum");
      pie.addYField(sales);
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(dim("State"));
      pie.setColorField(color);
      pie.setChartType(type);
      pie.setRTChartType(type);
      pie.updateChartType(false);
      Object[][] rows = {
         { "State", GraphUtil.getName(sales) },
         { "CA", 1.0 },
         { "NY", 2.0 },
         { "TX", 3.0 },
      };
      ChartDescriptor desc = new ChartDescriptor();
      setup.accept(desc);
      Coordinate coord = graph(pie, rows, mark, desc).getCoordinate();
      assertInstanceOf(PolarCoord.class, coord, "premise: a polar coordinate");
      return (PolarCoord) coord;
   }

   // a one-state donut of Sum(Sales) showing its value, laid out at 400 x 400
   private static VGraph oneStateDonut(VizMark mark, boolean exploded) {
      VSChartInfo donut = new DefaultVSChartInfo();
      VSChartAggregateRef sales = agg("Sales", "Sum");
      donut.addYField(sales);
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(dim("State"));
      donut.setColorField(color);
      donut.setChartType(GraphTypes.CHART_DONUT);
      donut.setRTChartType(GraphTypes.CHART_DONUT);
      donut.updateChartType(false);
      Object[][] rows = {
         { "State", GraphUtil.getName(sales) },
         { "CA", 1.0 },
      };
      ChartDescriptor desc = new ChartDescriptor();
      desc.getPlotDescriptor().setValuesVisible(true);
      desc.getPlotDescriptor().setExploded(exploded);
      GraphGenerator gen = generator(donut, rows, mark, desc);
      return Plotter.getPlotter(gen.createEGraph()).plotAndLayout(gen.getData(), 0, 0, 400, 400);
   }

   private static EGraph graph(VSChartInfo cinfo, Object[][] rows, VizMark mark,
                               ChartDescriptor desc)
   {
      return generator(cinfo, rows, mark, desc).createEGraph();
   }

   private static GraphGenerator generator(VSChartInfo cinfo, Object[][] rows, VizMark mark,
                                           ChartDescriptor desc)
   {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(desc);
      assemblyInfo.setVizMark(mark);
      return GraphGenerator.getGenerator(assemblyInfo, null, new DefaultDataSet(rows),
                                         new VariableTable(), null, XSourceInfo.NONE,
                                         new Dimension(400, 400));
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
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
