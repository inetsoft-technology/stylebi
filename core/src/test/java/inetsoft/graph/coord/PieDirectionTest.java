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
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.StackRange;
import inetsoft.graph.visual.ArcVOText;
import inetsoft.graph.visual.BarVO;
import inetsoft.graph.visual.VOText;
import inetsoft.graph.visual.VisualObject;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Shape;
import java.awt.geom.*;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A pie that runs clockwise from twelve o'clock.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PieDirectionTest {
   @Test
   void twoSlicesSplitLeftAndRight() {
      VGraph on = pie(true, 2, 0, false, 400, 400, c -> { }, e -> { });
      Point2D c = centre(on);
      Point2D a = sliceCentre(slices(on).get(0));

      assertTrue(a.getX() > c.getX(), a + " vs " + c);
      assertEquals(c.getY(), a.getY(), 2, a + " vs " + c);

      VGraph off = pie(false, 2, 0, false, 400, 400, c0 -> { }, e -> { });
      Point2D a0 = sliceCentre(slices(off).get(0));

      assertTrue(a0.getY() > centre(off).getY(), a0 + " vs " + centre(off));
   }

   @Test
   void slicesRunClockwiseFromTwelve() {
      VGraph on = pie(true, 3, 0, false, 400, 400, c -> { }, e -> { });
      Point2D c = centre(on);
      List<BarVO> slices = slices(on);
      double[] expected = { 30, -90, 150 };
      assertEquals(3, slices.size(), "premise: three slices");

      for(int i = 0; i < slices.size(); i++) {
         double angle = angle(c, sliceCentre(slices.get(i)));
         assertEquals(0, angleDiff(angle, expected[i]), 2, "slice " + i + " at " + angle);

         if(i > 0) {
            double prev = angle(c, sliceCentre(slices.get(i - 1)));
            double step = ((prev - angle) % 360 + 360) % 360;
            assertEquals(120, step, 2, "slice " + i + " steps clockwise");
         }
      }
   }

   @Test
   void eachSliceSweepsClockwise() {
      Arc2D on = BarVO.getOuterArc(
         slices(pie(true, 2, 0, false, 400, 400, c -> { }, e -> { })).get(0).getShape());
      Arc2D off = BarVO.getOuterArc(
         slices(pie(false, 2, 0, false, 400, 400, c -> { }, e -> { })).get(0).getShape());

      assertNotNull(on);
      assertNotNull(off);
      assertNotEquals(0, on.getAngleExtent());
      assertEquals(-Math.signum(off.getAngleExtent()), Math.signum(on.getAngleExtent()),
                   on.getAngleExtent() + " vs " + off.getAngleExtent());
   }

   @Test
   void outsideLabelsSitBesideTheirSlices() {
      for(double ratio : new double[] { 0, 0.7 }) {
         VGraph on = pie(true, 6, ratio, true, 400, 300, c -> { }, e -> { });
         Point2D c = centre(on);
         List<BarVO> slices = slices(on);
         List<Rectangle2D> bounds = new ArrayList<>();

         for(BarVO slice : slices) {
            VOText text = slice.getVOText();
            assertInstanceOf(ArcVOText.class, text, "premise: an outside label");
            Rectangle2D box = text.getBounds();
            double labelAngle = angle(c, new Point2D.Double(box.getCenterX(), box.getCenterY()));
            double sliceAngle = angle(c, sliceCentre(slice));

            assertTrue(Math.abs(angleDiff(labelAngle, sliceAngle)) <= 45,
                       "ratio " + ratio + ", " + text.getText() + " at " + labelAngle +
                       ", slice at " + sliceAngle);
            bounds.add(box);
         }

         for(int i = 0; i < bounds.size(); i++) {
            for(int j = i + 1; j < bounds.size(); j++) {
               assertFalse(bounds.get(i).intersects(bounds.get(j)),
                           "ratio " + ratio + ", labels " + i + " and " + j + ": " +
                           bounds.get(i) + " " + bounds.get(j));
            }
         }
      }
   }

   @Test
   void labelsGetTheClassicExtentSign() {
      List<BarVO> on = slices(pie(true, 6, 0.7, true, 400, 300, c -> { }, e -> { }));
      List<BarVO> off = slices(pie(false, 6, 0.7, true, 400, 300, c -> { }, e -> { }));

      for(int i = 0; i < off.size(); i++) {
         double extent = ((ArcVOText) on.get(i).getVOText()).getExtent();
         double classic = ((ArcVOText) off.get(i).getVOText()).getExtent();

         assertNotEquals(0, classic, "premise: a slice extent");
         assertEquals(Math.signum(classic), Math.signum(extent), "label " + i);
         assertEquals(Math.abs(classic), Math.abs(extent), 0.001, "label " + i);
      }
   }

   @Test
   void anExplodedSliceMovesAlongItsMiddle() {
      VGraph plain = pie(true, 3, 0, false, 400, 400, c -> { }, e -> { });
      VGraph exploded = pie(true, 3, 0, false, 400, 400, c -> { },
                            e -> e.setHint(GraphElement.HINT_EXPLODED, "true"));
      Point2D c = centre(plain);
      List<BarVO> before = slices(plain);
      List<BarVO> after = slices(exploded);

      assertEquals(centre(exploded), c, "premise: the same centre");

      for(int i = 0; i < before.size(); i++) {
         Point2D p0 = sliceCentre(before.get(i));
         Point2D p1 = sliceCentre(after.get(i));

         assertTrue(p1.distance(c) > p0.distance(c), "slice " + i + ": " + p1 + " vs " + p0);
         assertEquals(0, angleDiff(angle(c, p1), angle(c, p0)), 2, "slice " + i);
      }
   }

   @Test
   void aSingleSliceIsAWholeCircle() {
      VGraph on = pie(true, 1, 0, false, 400, 400, c -> { }, e -> { });
      VGraph off = pie(false, 1, 0, false, 400, 400, c -> { }, e -> { });
      List<BarVO> slices = slices(on);

      assertEquals(1, slices.size());
      Rectangle2D box = slices.get(0).getBounds();
      Rectangle2D circle = slices(off).get(0).getBounds();

      assertEquals(circle.getX(), box.getX(), 0.5);
      assertEquals(circle.getY(), box.getY(), 0.5);
      assertEquals(circle.getWidth(), box.getWidth(), 0.5);
      assertEquals(circle.getHeight(), box.getHeight(), 0.5);
      assertEquals(box.getWidth(), box.getHeight(), 0.5, "a circle: " + box);
   }

   @Test
   void aRotatedCoordKeepsTheClassicStart() {
      VGraph on = pie(true, 2, 0, false, 400, 400, c -> c.rotate(30), e -> { });
      VGraph off = pie(false, 2, 0, false, 400, 400, c -> c.rotate(30), e -> { });
      List<BarVO> rotated = slices(on);
      List<BarVO> classic = slices(off);

      assertFalse(((PolarCoord) on.getCoordinate()).drawsClockwise(), "a script turned it");

      for(int i = 0; i < classic.size(); i++) {
         Point2D p1 = sliceCentre(rotated.get(i));
         Point2D p0 = sliceCentre(classic.get(i));

         assertEquals(p0.getX(), p1.getX(), 0.5, "slice " + i);
         assertEquals(p0.getY(), p1.getY(), 0.5, "slice " + i);
      }
   }

   @Test
   void theFlagTakesPartInEquality() {
      PolarCoord a = new PolarCoord(new LinearScale("Sales"));
      PolarCoord b = new PolarCoord(new LinearScale("Sales"));
      b.setClockwiseFromTop(true);

      assertFalse(a.equalsContent(b));
      a.setClockwiseFromTop(true);
      assertTrue(a.equalsContent(b));
   }

   // a pie of n equal states laid out at w x h, stacked on color as the generator builds it
   private static VGraph pie(boolean clockwise, int n, double ratio, boolean labels, int w, int h,
                             Consumer<PolarCoord> coordSetup, Consumer<IntervalElement> elemSetup)
   {
      EGraph graph = new EGraph();
      IntervalElement element = new IntervalElement("Sales");

      if(labels) {
         element.setTextFrame(new DefaultTextFrame("State"));
      }

      element.setColorFrame(new CategoricalColorFrame("State"));
      element.setCollisionModifier(GraphElement.MOVE_STACK);
      elemSetup.accept(element);
      graph.addElement(element);
      LinearScale sales = new LinearScale("Sales");
      sales.setScaleRange(new StackRange());
      PolarCoord coord = new PolarCoord(sales);
      coord.setPieRatio(ratio);
      coord.setClockwiseFromTop(clockwise);
      coordSetup.accept(coord);
      graph.setCoordinate(coord);
      String[] states = { "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado" };
      Object[][] rows = new Object[n + 1][];
      rows[0] = new Object[] { "State", "Sales" };

      for(int i = 0; i < n; i++) {
         rows[i + 1] = new Object[] { states[i], 10.0 };
      }

      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, w, h);
   }

   // the slices in data order
   private static List<BarVO> slices(VGraph vgraph) {
      List<BarVO> slices = new ArrayList<>();

      for(VisualObject vo : GTool.getVOs(vgraph)) {
         if(vo instanceof BarVO) {
            slices.add((BarVO) vo);
         }
      }

      slices.sort(Comparator.comparingInt(BarVO::getSubRowIndex));
      return slices;
   }

   private static Point2D centre(VGraph vgraph) {
      Rectangle2D box = vgraph.getCoordinate().getCoordBounds();
      return new Point2D.Double(box.getCenterX(), box.getCenterY());
   }

   // the area centroid of the drawn slice, which lies on its bisector
   private static Point2D sliceCentre(BarVO slice) {
      Shape shape = slice.getScreenTransform().createTransformedShape(slice.getShape());
      double area = 0, cx = 0, cy = 0;
      double[] pt = new double[6];
      double x0 = 0, y0 = 0, px = 0, py = 0;

      for(PathIterator it = shape.getPathIterator(null, 0.01); !it.isDone(); it.next()) {
         int seg = it.currentSegment(pt);

         if(seg == PathIterator.SEG_MOVETO) {
            x0 = px = pt[0];
            y0 = py = pt[1];
            continue;
         }

         double x = seg == PathIterator.SEG_CLOSE ? x0 : pt[0];
         double y = seg == PathIterator.SEG_CLOSE ? y0 : pt[1];
         double cross = px * y - x * py;
         area += cross;
         cx += (px + x) * cross;
         cy += (py + y) * cross;
         px = x;
         py = y;
      }

      return new Point2D.Double(cx / (3 * area), cy / (3 * area));
   }

   private static double angle(Point2D c, Point2D p) {
      return Math.toDegrees(Math.atan2(p.getY() - c.getY(), p.getX() - c.getX()));
   }

   // the signed difference a - b folded into [-180, 180)
   private static double angleDiff(double a, double b) {
      return ((a - b) % 360 + 540) % 360 - 180;
   }
}
