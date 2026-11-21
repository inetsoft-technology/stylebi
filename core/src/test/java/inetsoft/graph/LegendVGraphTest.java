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
package inetsoft.graph;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.CategoricalShapeFrame;
import inetsoft.graph.coord.FacetCoord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.PointElement;
import inetsoft.graph.guide.legend.Legend;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.guide.legend.LegendItem;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.visual.GraphVO;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Legends of a graph that went through the real chain: Plotter creates the VGraph, which asks the
 * EGraph for the legend frames, builds the legend group and places it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class LegendVGraphTest {
   private static DataSet data() {
      return new DefaultDataSet(new Object[][] {
         { "State", "Region", "Total", "Kind" },
         { "CA", "West", 1.0, "k" }, { "NY", "East", 2.0, "k" }, { "TX", "South", 3.0, "k" }
      });
   }

   private static CategoricalColorFrame stateColors(DataSet data) {
      CategoricalColorFrame color = new CategoricalColorFrame("State");
      color.init(data);
      return color;
   }

   private static EGraph graph(DataSet data, CategoricalColorFrame color) {
      EGraph graph = new EGraph();
      PointElement elem = new PointElement("State", "Total");
      elem.setColorFrame(color);
      graph.addElement(elem);
      return graph;
   }

   private static List<String> labels(Legend legend) {
      List<String> labels = new ArrayList<>();

      for(LegendItem[] row : legend.getItems()) {
         for(LegendItem item : row) {
            if(item != null) {
               labels.add(item.getLabel());
            }
         }
      }

      return labels;
   }

   @Test
   void colorFrameBecomesOneLegendWithTheItemsOfTheValues() {
      DataSet data = data();
      CategoricalColorFrame color = stateColors(data);
      VGraph vgraph = Plotter.getPlotter(graph(data, color)).plotAndLayout(data, 0, 0, 600, 400);
      LegendGroup group = vgraph.getLegendGroup();

      assertNotNull(group);
      assertEquals(1, group.getLegendCount());
      assertEquals(List.of("CA", "NY", "TX"), labels(group.getLegend(0)));
      assertSame(color, group.getLegend(0).getVisualFrame());
   }

   @Test
   void noLegendLayoutHasNoLegendGroup() {
      DataSet data = data();
      EGraph graph = graph(data, stateColors(data));
      graph.setLegendLayout(GraphConstants.NONE);

      assertNull(Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 600, 400).getLegendGroup());
   }

   @Test
   void hiddenFrameHasNoLegendGroup() {
      DataSet data = data();
      CategoricalColorFrame color = stateColors(data);
      color.getLegendSpec().setVisible(false);

      assertNull(Plotter.getPlotter(graph(data, color)).plotAndLayout(data, 0, 0, 600, 400)
                    .getLegendGroup());
   }

   @Test
   void colorAndShapeFramesGiveTwoLegendsColorFirst() {
      DataSet data = data();
      CategoricalColorFrame color = stateColors(data);
      CategoricalShapeFrame shape = new CategoricalShapeFrame("Region");
      shape.init(data);
      EGraph graph = graph(data, color);
      graph.getElement(0).setShapeFrame(shape);
      LegendGroup group = Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 600, 400)
         .getLegendGroup();

      assertEquals(2, group.getLegendCount());
      assertSame(color, group.getLegend(0).getVisualFrame());
      assertSame(shape, group.getLegend(1).getVisualFrame());
      assertEquals(List.of("CA", "NY", "TX"), labels(group.getLegend(0)));
      assertEquals(List.of("West", "East", "South"), labels(group.getLegend(1)));
   }

   @ParameterizedTest
   @ValueSource(ints = { GraphConstants.LEFT, GraphConstants.RIGHT, GraphConstants.TOP,
                         GraphConstants.BOTTOM })
   void legendGroupLiesInsideTheGraphAndOutsideTheContent(int layout) {
      DataSet data = data();
      EGraph graph = graph(data, stateColors(data));
      graph.setLegendLayout(layout);
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 600, 400);
      Rectangle2D legends = vgraph.getLegendGroup().getBounds();
      Rectangle2D whole = vgraph.getBounds();
      Rectangle2D content = vgraph.getContentBounds();

      assertTrue(legends.getX() >= whole.getX() - 1 && legends.getY() >= whole.getY() - 1 &&
                    legends.getMaxX() <= whole.getMaxX() + 1 &&
                    legends.getMaxY() <= whole.getMaxY() + 1,
                 "legends " + legends + " graph " + whole);

      Rectangle2D overlap = legends.createIntersection(content);
      boolean overlaps = overlap.getWidth() > 1 && overlap.getHeight() > 1;
      assertFalse(overlaps, "legends " + legends + " content " + content);
   }

   @Test
   void identicallyBuiltGraphsGiveTheSameLegends() {
      DataSet data = data();
      LegendGroup first = Plotter.getPlotter(graph(data, stateColors(data)))
         .plotAndLayout(data, 0, 0, 600, 400).getLegendGroup();
      LegendGroup second = Plotter.getPlotter(graph(data, stateColors(data)))
         .plotAndLayout(data, 0, 0, 600, 400).getLegendGroup();

      assertEquals(first.getLegendCount(), second.getLegendCount());
      assertEquals(labels(first.getLegend(0)), labels(second.getLegend(0)));
      assertEquals(List.of("CA", "NY", "TX"), labels(second.getLegend(0)));
   }

   @Test
   void facetGraphHasTheLegendOnTheOuterGraphOnly() {
      DataSet data = data();
      EGraph graph = new EGraph();
      PointElement elem = new PointElement("State", "Total");
      elem.setColorFrame(stateColors(data));
      graph.addElement(elem);
      graph.setCoordinate(new FacetCoord(
         new RectCoord(new CategoricalScale("Region"), new CategoricalScale("Kind")),
         new RectCoord()));
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 600, 400);
      int inner = 0;

      assertNotNull(vgraph.getLegendGroup());

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof GraphVO vo) {
            inner++;
            assertNull(vo.getVGraph().getLegendGroup());
         }
      }

      assertTrue(inner > 0, "no inner graph found");
   }
}
