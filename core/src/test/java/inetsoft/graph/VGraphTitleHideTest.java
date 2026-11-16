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

import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A flagged axis title is dropped for a layout when its text is longer than its axis.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VGraphTitleHideTest {
   private static final String LONG = "A very long generated axis title that keeps going well past the end of a short axis, "
      + "and then some more words";

   @Test
   void aLongGeneratedXTitleIsHidden() {
      VGraph vgraph = layout(graph("x", LONG, 0, true), 300, 200);
      VGraph none = layout(graph("x", null, 0, true), 300, 200);

      assertNull(vgraph.getXTitle());
      assertEquals(none.getPlotBounds().getHeight(), vgraph.getPlotBounds().getHeight(), 0.01);
   }

   @Test
   void aTitleThatFitsStays() {
      VGraph vgraph = layout(graph("x", LONG, 0, true), 1200, 400);

      assertNotNull(vgraph.getXTitle());
      assertEquals(LONG, String.join("", vgraph.getXTitle().getDisplayLabel()));
   }

   @Test
   void aLongYTitleIsHiddenAndThePlotWidens() {
      VGraph vgraph = layout(graph("y", LONG, 90, true), 400, 150);
      VGraph none = layout(graph("y", null, 90, true), 400, 150);

      assertNull(vgraph.getYTitle());
      assertEquals(none.getPlotBounds().getWidth(), vgraph.getPlotBounds().getWidth(), 0.01);
   }

   @Test
   void aYTitleAtMinus90CountsAsAlongItsAxis() {
      assertNull(layout(graph("y", LONG, -90, true), 400, 150).getYTitle());
   }

   @Test
   void withoutTheFlagTheTitleIsCut() {
      VGraph vgraph = layout(graph("x", LONG, 0, false), 300, 200);

      assertNotNull(vgraph.getXTitle());
      assertTrue(String.join("", vgraph.getXTitle().getDisplayLabel()).endsWith(".."),
                 Arrays.toString(vgraph.getXTitle().getDisplayLabel()));
   }

   @Test
   void aTitleNotAlongItsAxisIsNeverHidden() {
      assertNotNull(layout(graph("x", LONG, 45, true), 300, 200).getXTitle());
   }

   @Test
   void aTitleNotAlongItsAxisSurvivesATinyGraph() {
      assertNotNull(layout(graph("x", LONG, 45, true), 8, 8).getXTitle());
      assertNotNull(layout(graph("y", LONG, 45, true), 8, 8).getYTitle());
   }

   @Test
   void aHiddenTitleReturnsAtALargerLayout() {
      assertNull(layout(graph("x", LONG, 0, true), 300, 200).getXTitle());
      assertNotNull(layout(graph("x", LONG, 0, true), 1200, 200).getXTitle());
   }

   @Test
   void aFollowingGraphHidesTheSourcesTitles() {
      DefaultDataSet data = new DefaultDataSet(rows());
      VGraph source = Plotter.getPlotter(graph("x", LONG, 0, true)).plotAndLayout(data, 0, 0, 300, 200);
      assertNull(source.getXTitle(), "premise");
      assertTrue(source.getDroppedTitles().contains("x"));

      VGraph follower = Plotter.getPlotter(graph("x", LONG, 0, true)).plot(data);
      follower.followTitles(source);
      follower.layout(0, 0, 1200, 200);
      assertNull(follower.getXTitle());

      VGraph shown = Plotter.getPlotter(graph("x", LONG, 0, true))
         .plotAndLayout(data, 0, 0, 1200, 200);
      assertTrue(shown.getDroppedTitles().isEmpty());
      VGraph follower2 = Plotter.getPlotter(graph("x", LONG, 0, true)).plot(data);
      follower2.followTitles(shown);
      follower2.layout(0, 0, 300, 200);
      assertNotNull(follower2.getXTitle());
   }

   @Test
   void theFlagTakesPartInEquality() {
      TitleSpec a = new TitleSpec();
      TitleSpec b = new TitleSpec();
      b.setHideWhenLonger(true);

      assertNotEquals(a, b);
      a.setHideWhenLonger(true);
      assertEquals(a, b);
   }

   private static VGraph layout(EGraph graph, int w, int h) {
      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows()), 0, 0, w, h);
   }

   private static Object[][] rows() {
      return new Object[][] { { "State", "Sales" }, { "A", 10.0 }, { "B", 20.0 }, { "C", 30.0 } };
   }

   private static EGraph graph(String axis, String text, int rotation, boolean hide) {
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("State", "Sales"));
      graph.setCoordinate(new RectCoord(new CategoricalScale("State"), new LinearScale("Sales")));
      TitleSpec spec = axis.equals("x") ? graph.getXTitleSpec() : graph.getYTitleSpec();
      spec.setLabel(text);
      spec.getTextSpec().setRotation(rotation);
      spec.setHideWhenLonger(hide);
      return graph;
   }
}
