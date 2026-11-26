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
package inetsoft.graph.internal;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.coord.Rect25Coord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.StackRange;
import inetsoft.graph.visual.*;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for the 3D pie radius computed from the value labels
 * (Bug #82033).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PolarUtilRegressionTest {
   // Bug #82033: a label above the pie must only limit the vertical radius, which is
   // half the horizontal radius in a 3D pie, so the label leaves room for r = 50.
   @Test
   void labelAbovePieDoesNotShrinkRadiusTwice() {
      double ph = 14;
      VGraph vgraph = singleSliceGraph(45, 40, ph); // label mid angle 90 degrees
      double r = PolarUtil.getText3DPieRadius(1000, 100, vgraph);

      assertEquals(Math.min(50, 2 * (100 / 2.0 - ph)), r, 1e-6);
   }

   // A label below the pie is also moved down by the 3D depth (radius / 12), so it
   // limits the radius more than the same label above the pie.
   @Test
   void labelBelowPieLeavesRoomForDepth() {
      double ph = 14;
      double h = 60;
      double above = PolarUtil.getText3DPieRadius(1000, h, singleSliceGraph(-135, 40, ph));
      double below = PolarUtil.getText3DPieRadius(1000, h, singleSliceGraph(45, 40, ph));

      assertEquals(Math.min(h / 2, 2 * (h / 2 - ph)), above, 1e-6);
      assertEquals((h / 2 - ph) / (0.5 + 1.0 / 12), below, 1e-6);
   }

   // A label beside the pie is limited by the plot width only, as before.
   @Test
   void labelBesidePieUnchanged() {
      double pw = 20;
      VGraph vgraph = singleSliceGraph(-45, pw, 14); // label mid angle 0 degrees
      double r = PolarUtil.getText3DPieRadius(100, 1000, vgraph);

      assertEquals(100 / 2.0 - pw, r, 1e-6);
   }

   // Bug #82033 end to end: two slices put the labels above and below a wide, short
   // 3D pie; the pie now uses the height instead of shrinking to about 34.5.
   @Test
   void twoSliceWidePieUsesHeight() {
      VGraph vgraph = render(new Object[][] { { "x", "y" }, { "A", 10 }, { "B", 10 } },
                             "y", 1000, 100);
      PolarCoord coord = (PolarCoord) vgraph.getCoordinate();
      Rectangle2D bounds = coord.getCoordBounds();

      assertEquals(Math.min(bounds.getWidth(), bounds.getHeight()) / 2 - 2,
                   coord.getRadius(), 1e-6);
      assertLabelsInside(vgraph, bounds);
   }

   // In a very short plot the label below the pie is also pushed down by the 3D depth.
   @Test
   void twoSliceShortPieKeepsBottomLabelInside() {
      VGraph vgraph = render(new Object[][] { { "x", "y" }, { "A", 10 }, { "B", 10 } },
                             "y", 1000, 50);
      PolarCoord coord = (PolarCoord) vgraph.getCoordinate();

      assertLabelsInside(vgraph, coord.getCoordBounds());
   }

   // Labels at oblique angles sit at (cos * r, sin * r / 2), so long labels beside a
   // narrow, tall pie must keep limiting the radius by the full horizontal distance.
   // Treating the label as if it sat on the ellipse at that angle lets the radius grow
   // to the plot cap and pushes the right-hand labels about 9 px out of the plot.
   @Test
   void fourSliceNarrowPieKeepsLongLabelsInside() {
      VGraph vgraph = render(new Object[][] {
         { "x", "y" }, { "Category Alpha", 10 }, { "Category Bravo", 10 },
         { "Category Charlie", 10 }, { "Category Delta", 10 } }, "x", 500, 2000);
      PolarCoord coord = (PolarCoord) vgraph.getCoordinate();

      assertLabelsInside(vgraph, coord.getCoordBounds());
   }

   private static VGraph singleSliceGraph(double start, double pw, double ph) {
      ArcVOText text = mock(ArcVOText.class);
      when(text.getPreferredWidth()).thenReturn(pw);
      when(text.getPreferredHeight()).thenReturn(ph);
      Pie3DVO pvo = mock(Pie3DVO.class);
      when(pvo.getShapes()).thenReturn(new Shape[] {
         new Arc2D.Double(0, 0, 100, 100, start, 90, Arc2D.PIE) });
      when(pvo.getVOTexts()).thenReturn(new VOText[] { text });
      VGraph vgraph = mock(VGraph.class);
      when(vgraph.getVisualCount()).thenReturn(1);
      when(vgraph.getVisual(0)).thenReturn(pvo);
      return vgraph;
   }

   private static VGraph render(Object[][] rows, String textField, int w, int h) {
      EGraph graph = new EGraph();
      IntervalElement elem = new IntervalElement("y");
      elem.setCollisionModifier(GraphElement.MOVE_STACK);
      elem.setColorFrame(new CategoricalColorFrame("x"));
      elem.setTextFrame(new DefaultTextFrame(textField));
      graph.addElement(elem);
      LinearScale yscale = new LinearScale("y");
      yscale.setScaleRange(new StackRange());
      yscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
      PolarCoord coord = new PolarCoord(new Rect25Coord(null, yscale));
      coord.setType(PolarCoord.THETA);
      graph.setCoordinate(coord);

      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, w, h);
   }

   // Every value label lies within the coord bounds. The 0.5 px slack covers the
   // pie depth, which Pie3DVO measures from integer bounds.
   private static void assertLabelsInside(VGraph vgraph, Rectangle2D bounds) {
      List<String> outside = new ArrayList<>();
      int count = 0;

      for(Object obj : GTool.getVOs(vgraph)) {
         if(!(obj instanceof Pie3DVO)) {
            continue;
         }

         for(VOText text : ((Pie3DVO) obj).getVOTexts()) {
            if(text == null) {
               continue;
            }

            count++;
            Rectangle2D tb = text.getBounds();
            double over = Math.max(Math.max(bounds.getMinX() - tb.getMinX(),
                                            tb.getMaxX() - bounds.getMaxX()),
                                   Math.max(bounds.getMinY() - tb.getMinY(),
                                            tb.getMaxY() - bounds.getMaxY()));

            if(over > 0.5) {
               outside.add(text.getText() + " " + tb + " is " + over + " px outside " + bounds);
            }
         }
      }

      assertTrue(count > 0, "no value labels were laid out");
      assertTrue(outside.isEmpty(), String.join("; ", outside));
   }
}
