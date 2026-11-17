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
package inetsoft.graph.coord;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.DefaultTextFrame;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pie cap bounds an automatic pie's diameter by a share of the plot's shorter side.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PieCapTest {
   @Test
   void aCappedPieIsAtMost80PercentOfTheShorterSide() {
      PolarCoord coord = pie(0.8, 0, false, 400, 200);
      double shorter = Math.min(coord.getCoordBounds().getWidth(),
                                coord.getCoordBounds().getHeight());

      assertTrue(2 * coord.getRadius() <= 0.8 * shorter, "" + coord.getRadius());
   }

   @Test
   void aTallPlotCapsByItsWidth() {
      PolarCoord coord = pie(0.8, 0, false, 180, 400);
      double width = coord.getCoordBounds().getWidth();

      assertTrue(width < coord.getCoordBounds().getHeight(), "premise: a tall plot");
      assertTrue(2 * coord.getRadius() <= 0.8 * width, "" + coord.getRadius());
   }

   @Test
   void withoutTheCapThePieIsTodays() {
      PolarCoord none = pie(0, 0, false, 400, 200);
      PolarCoord unset = pie(-1, 0, false, 400, 200);
      double shorter = Math.min(none.getCoordBounds().getWidth(),
                                none.getCoordBounds().getHeight());

      assertEquals(unset.getRadius(), none.getRadius(), 0.0001);
      assertTrue(none.getRadius() > 0.8 * shorter / 2, "premise: the cap binds a plain pie");
   }

   @Test
   void anAuthorRatioWinsOverTheCap() {
      PolarCoord coord = pie(0.8, 0.6, false, 400, 200);
      double shorter = Math.min(coord.getCoordBounds().getWidth(),
                                coord.getCoordBounds().getHeight());

      assertEquals(shorter * 0.6 / 2 - 1, coord.getRadius(), 0.0001);
   }

   @Test
   void aPieThatLabelsShrinkIsUnchanged() {
      PolarCoord capped = pie(0.8, 0, true, 300, 150);
      PolarCoord plain = pie(0, 0, true, 300, 150);

      assertEquals(plain.getRadius(), capped.getRadius(), 0.0001);
   }

   @Test
   void theCapLeavesTheLabelModeAlone() {
      assertFalse(pie(0.8, 0, false, 400, 200).isLabelMoveable());
   }

   @Test
   void theCapTakesPartInEquality() {
      PolarCoord a = new PolarCoord(new LinearScale("Sales"));
      PolarCoord b = new PolarCoord(new LinearScale("Sales"));
      b.setPieCapRatio(0.8);

      assertFalse(a.equalsContent(b));
      a.setPieCapRatio(0.8);
      assertTrue(a.equalsContent(b));
   }

   // a pie of three states laid out at w x h; a negative cap leaves the setter uncalled
   private static PolarCoord pie(double cap, double ratio, boolean labels, int w, int h) {
      EGraph graph = new EGraph();
      IntervalElement element = new IntervalElement("State", "Sales");

      if(labels) {
         element.setTextFrame(new DefaultTextFrame("State"));
      }

      graph.addElement(element);
      PolarCoord coord = new PolarCoord(new CategoricalScale("State"), new LinearScale("Sales"));
      coord.setType(PolarCoord.THETA);

      if(cap >= 0) {
         coord.setPieCapRatio(cap);
      }

      coord.setPieRatio(ratio);
      graph.setCoordinate(coord);
      Object[][] rows = {
         { "State", "Sales" },
         { "Alabama Long Category Name", 10.0 },
         { "Massachusetts Long Category Name", 20.0 },
         { "Mississippi Long Category Name", 30.0 },
      };
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, w, h);
      return (PolarCoord) vgraph.getCoordinate();
   }
}
