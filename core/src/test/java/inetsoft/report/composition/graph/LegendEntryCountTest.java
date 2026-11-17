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
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.CategoricalShapeFrame;
import inetsoft.graph.aesthetic.GradientColorFrame;
import inetsoft.graph.aesthetic.StackedMeasuresColorFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.guide.legend.LegendItem;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The legend entry count that decides whether a modern legend is crowded.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LegendEntryCountTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void sixColourValuesAreSix() {
      IntervalElement bar = bar();
      bar.setColorFrame(colorOn("State"));

      assertEquals(6, LegendEntryCount.count(graphOf(bar)));
   }

   @Test
   void aHiddenValueIsNotCounted() {
      IntervalElement bar = bar();
      CategoricalColorFrame color = colorOn("State");
      color.getLegendSpec().setVisible("NY", false);
      bar.setColorFrame(color);

      assertEquals(5, LegendEntryCount.count(graphOf(bar)));
   }

   @Test
   void colourAndShapeAdd() {
      IntervalElement bar = bar();
      bar.setColorFrame(colorOn("Region"));
      CategoricalShapeFrame shape = new CategoricalShapeFrame("Tier");
      shape.init(DATA);
      bar.setShapeFrame(shape);
      EGraph graph = graphOf(bar);

      assertEquals(6, LegendEntryCount.count(graph));
      assertTrue(LegendEntryCount.crowded(graph));
   }

   @Test
   void aRampCountsNothing() {
      IntervalElement bar = bar();
      GradientColorFrame ramp = new GradientColorFrame("m1");
      ramp.init(DATA);
      bar.setColorFrame(ramp);
      EGraph graph = graphOf(bar);
      assertEquals(1, graph.getVisualFrames().length, "premise: the ramp is a legend frame");

      assertEquals(0, LegendEntryCount.count(graph));
      assertFalse(LegendEntryCount.crowded(graph));
   }

   @Test
   void fiveIsNotCrowded() {
      IntervalElement bar = bar();
      bar.setColorFrame(colorOn("Five"));
      EGraph graph = graphOf(bar);

      assertEquals(5, LegendEntryCount.count(graph));
      assertFalse(LegendEntryCount.crowded(graph));
   }

   @Test
   void stackedMeasuresCountEachDrawnLegend() {
      IntervalElement bar = bar();
      StackedMeasuresColorFrame stacked = new StackedMeasuresColorFrame(new CategoricalColorFrame());
      stacked.setFrame("m1", colorOn("Region"));
      stacked.setFrame("m2", colorOn("Zone"));
      bar.setColorFrame(stacked);
      EGraph graph = graphOf(bar);
      assertEquals(3, stacked.getLabels().length, "premise: the frame alone lists 3 values");
      assertEquals(2, LegendGroup.getLegendFrames(stacked).length, "premise: two legends");

      int counted = LegendEntryCount.count(graph);

      assertEquals(6, counted);
      assertTrue(LegendEntryCount.crowded(graph));
      assertEquals(drawnItems(graph), counted, "the drawn legend items");
   }

   @Test
   void aLegendTwoElementsShareCountsOnce() {
      IntervalElement first = bar();
      first.setColorFrame(colorOn("Five"));
      IntervalElement second = bar();
      CategoricalColorFrame other = colorOn("Five");
      // unequal frame, same legend
      other.setShareColors(false);
      second.setColorFrame(other);
      EGraph graph = graphOf(first, second);
      assertEquals(2, graph.getVisualFrames().length, "premise: two legend frames");

      int counted = LegendEntryCount.count(graph);

      assertEquals(5, counted);
      assertFalse(LegendEntryCount.crowded(graph));
      assertEquals(drawnItems(graph), counted, "the drawn legend items");
   }

   private static IntervalElement bar() {
      IntervalElement element = new IntervalElement();
      element.addDim("Cat");
      element.addVar("m1");
      return element;
   }

   private static CategoricalColorFrame colorOn(String field) {
      CategoricalColorFrame color = new CategoricalColorFrame(field);
      color.init(DATA);
      return color;
   }

   private static EGraph graphOf(IntervalElement... elements) {
      CategoricalScale xScale = new CategoricalScale("Cat");
      xScale.init(DATA);
      LinearScale yScale = new LinearScale("m1");
      yScale.init(DATA);

      EGraph graph = new EGraph();

      for(IntervalElement element : elements) {
         graph.addElement(element);
      }

      graph.setCoordinate(new RectCoord(xScale, yScale));
      return graph;
   }

   // the items the plotted legends hold
   private static int drawnItems(EGraph graph) {
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(DATA, 0, 0, 600, 400);
      LegendGroup group = vgraph.getLegendGroup();
      int items = 0;

      for(int i = 0; group != null && i < group.getLegendCount(); i++) {
         for(LegendItem[] row : group.getLegend(i).getItems()) {
            for(LegendItem item : row) {
               if(item != null) {
                  items++;
               }
            }
         }
      }

      return items;
   }

   // State: 6 values, Region, Zone and Tier: 3 each (Zone the same as Region), Five: 5
   private static final DefaultDataSet DATA = new DefaultDataSet(new Object[][] {
      { "Cat", "State", "Region", "Zone", "Tier", "Five", "m1" },
      { "A", "CA", "West", "East", "Gold", "p", 10.0 },
      { "B", "NY", "East", "South", "Silver", "q", 20.0 },
      { "C", "TX", "South", "West", "Bronze", "r", 15.0 },
      { "D", "FL", "South", "East", "Gold", "s", 25.0 },
      { "E", "WA", "West", "South", "Silver", "t", 30.0 },
      { "F", "IL", "East", "West", "Bronze", "t", 12.0 },
   });
}
