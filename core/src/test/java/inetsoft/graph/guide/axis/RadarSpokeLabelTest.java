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
package inetsoft.graph.guide.axis;

import inetsoft.graph.AxisSpec;
import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.ParallelCoord;
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The spoke names of a radar: cut at the label limit, a generated text instead of the value,
 * and an author alias left whole.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RadarSpokeLabelTest {
   private static final String LONG = "Sum(AVeryLongMeasureNameForTheSpoke)";
   private static final String SHORT = "Sum(Short)";

   @Test
   void aLongSpokeNameEndsInAnEllipsisAtTheLimit() {
      PolarAxis axis = axis(LONG, 66, spec -> { });
      VLabel label = axis.getLabels()[2];

      assertEquals(LONG, ((VDimensionLabel) label).getValue());
      assertTrue(text(label).endsWith("…"), text(label));
      assertTrue(width(label) <= 66 + 0.5, "width " + width(label));
   }

   @Test
   void aCutSpokeNameGivesTheRadarItsRoom() {
      double longCut = axis(LONG, 66, spec -> { }).getWidth();
      double shortCut = axis(SHORT + "x", 66, spec -> { }).getWidth();
      double none = axis(LONG, 0, spec -> { }).getWidth();

      assertEquals(shortCut, longCut, 0.5);
      assertTrue(longCut > none, "cut " + longCut + " none " + none);
   }

   @Test
   void anAuthorAliasShowsInFull() {
      String alias = "An Author Chosen Alias For This Spoke";
      DefaultTextFrame frame = new DefaultTextFrame();
      frame.setText(LONG, alias);
      VLabel label = axis(LONG, 66, spec -> spec.setTextFrame(frame)).getLabels()[2];

      assertFalse(text(label).contains("…"), text(label));
      assertTrue(width(label) > 66 + 0.5, "width " + width(label));
   }

   @Test
   void displayTextsChangeOnlyTheText() {
      VLabel label = axis(LONG, 0, spec -> spec.setDisplayTexts(Map.of(LONG, "Price")))
         .getLabels()[2];

      assertEquals("Price", text(label));
      assertEquals(LONG, ((VDimensionLabel) label).getValue());
   }

   @Test
   void anAuthorAliasBeatsTheDisplayText() {
      DefaultTextFrame frame = new DefaultTextFrame();
      frame.setText(LONG, "Mine");
      VLabel label = axis(LONG, 0, spec -> {
         spec.setTextFrame(frame);
         spec.setDisplayTexts(Map.of(LONG, "Price"));
      }).getLabels()[2];

      assertEquals("Mine", text(label));
   }

   @Test
   void classicSpokeNamesUnchanged() {
      VLabel label = axis(SHORT, 0, spec -> { }).getLabels()[2];

      assertEquals(SHORT, text(label));
      assertFalse(text(axis(LONG, 0, spec -> { }).getLabels()[2]).contains("…"));
   }

   // the spoke names of a 400x400 radar over three measures, the third named name
   private static PolarAxis axis(String name, double limit, Consumer<AxisSpec> setup) {
      ParallelCoord parallel = new ParallelCoord(new LinearScale("Sum(Price)"),
                                                 new LinearScale("Avg(Quantity)"),
                                                 new LinearScale(name));
      AxisSpec spec = parallel.getAxisLabelScale().getAxisSpec();
      spec.setLabelLimit(limit);
      setup.accept(spec);

      EGraph graph = new EGraph();
      graph.addElement(new LineElement("Sum(Price)", "Avg(Quantity)", name));
      graph.setCoordinate(new PolarCoord(parallel));
      Object[][] rows = {
         { "Sum(Price)", "Avg(Quantity)", name },
         { 1.0, 2.0, 3.0 },
         { 3.0, 2.0, 1.0 },
      };
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 400);
      Coordinate coord = vgraph.getCoordinate();

      for(var a : coord.getAxes(false)) {
         if(a instanceof PolarAxis polar && polar.getLabels().length == 3) {
            return polar;
         }
      }

      return fail("premise: a spoke-name axis");
   }

   private static String text(VLabel label) {
      return String.join("", label.getDisplayLabel());
   }

   private static double width(VLabel label) {
      return GTool.stringWidth(label.getDisplayLabel(), label.getFont());
   }
}
