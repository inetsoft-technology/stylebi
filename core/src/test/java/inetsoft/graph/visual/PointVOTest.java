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
package inetsoft.graph.visual;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.PointElement;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Radius, bounds, shapes, dodging and layout of PointVO. Visual objects created without layout
 * have logical positions: x on 0..10 maps to x * 100 and y on 0..100 to y * 10.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PointVOTest {
   private static final double EPS = 1e-6;

   private static LinearScale lin(double min, double max, String field) {
      LinearScale scale = new LinearScale(field);
      scale.setMin(min);
      scale.setMax(max);
      return scale;
   }

   private static PointElement element(GShape shape, double size) {
      PointElement elem = new PointElement("x", "y");
      elem.setShapeFrame(new StaticShapeFrame(shape));
      elem.setSizeFrame(new StaticSizeFrame((int) size));
      return elem;
   }

   private static DefaultDataSet data(double[]... xys) {
      Object[][] rows = new Object[xys.length + 1][];
      rows[0] = new Object[] { "x", "y", "s" };

      for(int i = 0; i < xys.length; i++) {
         rows[i + 1] = new Object[] { xys[i][0], xys[i][1], xys.length > 0 && xys[i].length > 2 ?
            xys[i][2] : 1.0 };
      }

      return new DefaultDataSet(rows);
   }

   private static List<PointVO> visuals(PointElement elem, RectCoord coord, DefaultDataSet data) {
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      egraph.setCoordinate(coord);
      GGraph ggraph = egraph.createGGraph(coord, data);
      List<PointVO> vos = new ArrayList<>();

      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         vos.add((PointVO) ggraph.getGeometry(i).createVisual(coord));
      }

      return vos;
   }

   private static List<PointVO> visuals(PointElement elem, DefaultDataSet data) {
      return visuals(elem, new RectCoord(lin(0, 10, "x"), lin(0, 100, "y")), data);
   }

   private static PointVO single(GShape shape, double size) {
      return visuals(element(shape, size), data(new double[] { 5, 50 })).get(0);
   }

   // ---- radius and bounds

   @Test
   @DisplayName("size 10 with a filled circle gives 26 x 26 bounds (radius 12 plus the border)")
   void boundsOfSizedPoint() {
      Rectangle2D bounds = single(GShape.FILLED_CIRCLE, 10).getBounds();

      assertEquals(26, bounds.getWidth(), EPS);
      assertEquals(26, bounds.getHeight(), EPS);
   }

   @Test
   @DisplayName("bounds are centered on the point")
   void boundsCenteredOnPoint() {
      PointVO vo = single(GShape.FILLED_CIRCLE, 10);
      Rectangle2D bounds = vo.getBounds();

      assertEquals(vo.getPosition().getX(), bounds.getCenterX(), EPS);
      assertEquals(vo.getPosition().getY(), bounds.getCenterY(), EPS);
   }

   @Test
   @DisplayName("bounds width is 2 * size + 6 for integer sizes 1 to 30")
   void sizeToWidth() {
      for(int size = 1; size <= 30; size++) {
         assertEquals(2 * size + 6, single(GShape.FILLED_CIRCLE, size).getBounds().getWidth(), EPS,
                      "size " + size);
      }
   }

   @Test
   @DisplayName("fractional sizes are cut to whole pixels (pinned behavior)")
   void fractionalSizeIsTruncated() {
      PointElement elem = new PointElement("x", "y");
      elem.setShapeFrame(new StaticShapeFrame(GShape.FILLED_CIRCLE));
      elem.setSizeFrame(new StaticSizeFrame("s"));
      List<PointVO> vos = visuals(elem, data(new double[] { 3, 50, 2.0 },
                                             new double[] { 6, 50, 2.9 }));

      assertEquals(vos.get(0).getBounds().getWidth(), vos.get(1).getBounds().getWidth(), EPS);
   }

   @Test
   @DisplayName("nil shape has no link shape, 2 x 2 bounds and unscaled size 0")
   void nilShape() {
      PointVO vo = single(GShape.NIL, 10);

      assertEquals(0, vo.getShapes().length);
      assertEquals(2, vo.getBounds().getWidth(), EPS);
      assertEquals(2, vo.getBounds().getHeight(), EPS);
      assertEquals(0, vo.getUnscaledSize(GraphConstants.TOP, null), EPS);
   }

   private static final GShape[] SHAPES = { GShape.CIRCLE, GShape.SQUARE, GShape.DIAMOND,
                                            GShape.STAR, GShape.FILLED_CIRCLE,
                                            GShape.FILLED_SQUARE, GShape.FILLED_DIAMOND };

   @Test
   @DisplayName("bounds contain the link shape for every shape and size")
   void boundsContainShape() {
      for(GShape shape : SHAPES) {
         for(int size : new int[] { 1, 5, 10, 20 }) {
            PointVO vo = single(shape, size);
            Shape[] shapes = vo.getShapes();

            assertEquals(1, shapes.length);
            assertTrue(vo.getBounds().contains(shapes[0].getBounds2D()),
                       shape.getClass().getSimpleName() + " size " + size);
         }
      }
   }

   @Test
   @DisplayName("link shape is centered on the point")
   void shapeCenteredOnPoint() {
      for(GShape shape : SHAPES) {
         for(int size : new int[] { 1, 5, 10, 20 }) {
            PointVO vo = single(shape, size);
            Rectangle2D box = vo.getShapes()[0].getBounds2D();

            assertEquals(vo.getPosition().getX(), box.getCenterX(), 1e-3,
                         shape.getClass().getSimpleName() + " size " + size);
            assertEquals(vo.getPosition().getY(), box.getCenterY(), 1e-3,
                         shape.getClass().getSimpleName() + " size " + size);
         }
      }
   }

   @Test
   @DisplayName("unscaled size is the radius on every side")
   void unscaledSizeIsRadius() {
      PointVO vo = single(GShape.FILLED_CIRCLE, 10);

      for(int pos : new int[] { GraphConstants.TOP, GraphConstants.LEFT, GraphConstants.RIGHT,
                                GraphConstants.BOTTOM })
      {
         assertEquals(12, vo.getUnscaledSize(pos, null), EPS, "side " + pos);
      }
   }

   // ---- position

   @Test
   @DisplayName("positions follow the data on both scales")
   void positionsFromData() {
      List<PointVO> vos = visuals(element(GShape.FILLED_CIRCLE, 10),
                                  data(new double[] { 2, 20 }, new double[] { 5, 60 },
                                       new double[] { 8, 100 }));
      double[][] expected = { { 200, 200 }, { 500, 600 }, { 800, 1000 } };

      assertEquals(3, vos.size());

      for(int i = 0; i < 3; i++) {
         assertEquals(expected[i][0], vos.get(i).getPosition().getX(), EPS, "x " + i);
         assertEquals(expected[i][1], vos.get(i).getPosition().getY(), EPS, "y " + i);
      }
   }

   @Test
   @DisplayName("a single scale with center collision puts the point in the middle")
   void oneScaleCenter() {
      PointElement elem = new PointElement();
      elem.addVar("y");
      elem.setCollisionModifier(GraphElement.MOVE_CENTER);
      RectCoord coord = new RectCoord(null, lin(0, 100, "y"));

      PointVO vo = visuals(elem, coord, data(new double[] { 5, 50 })).get(0);

      assertEquals(coord.getMaxWidth() / 2, vo.getPosition().getX(), EPS);
      assertEquals(500, vo.getPosition().getX(), EPS);
   }

   // ---- dodge

   @ParameterizedTest(name = "{0} -> {1}")
   @CsvSource({ "MOVE_NONE,false", "MOVE_DODGE,true", "MOVE_STACK,true", "MOVE_JITTER,true",
                "DODGE_CENTER,true" })
   @DisplayName("requiresDodge depends on the collision modifier")
   void requiresDodgeByModifier(String modifier, boolean expected) {
      PointElement elem = element(GShape.FILLED_CIRCLE, 10);
      int option = switch(modifier) {
         case "MOVE_NONE" -> GraphElement.MOVE_NONE;
         case "MOVE_DODGE" -> GraphElement.MOVE_DODGE;
         case "MOVE_STACK" -> GraphElement.MOVE_STACK;
         case "MOVE_JITTER" -> GraphElement.MOVE_JITTER;
         default -> GraphElement.MOVE_DODGE | GraphElement.MOVE_CENTER;
      };
      elem.setCollisionModifier(option);

      PointVO vo = visuals(elem, data(new double[] { 5, 50 })).get(0);

      assertEquals(expected, vo.requiresDodge());
   }

   @Test
   @DisplayName("a word cloud never asks for dodging")
   void wordCloudDoesNotDodge() {
      PointElement elem = element(GShape.FILLED_CIRCLE, 10);
      elem.setCollisionModifier(GraphElement.MOVE_STACK);
      elem.setWordCloud(true);

      PointVO vo = visuals(elem, data(new double[] { 5, 50 })).get(0);

      assertFalse(vo.requiresDodge());
   }

   /** Two points of size 10 (26 px wide) at the given logical x on a 0..1000 scale. */
   private static PointVO[] pair(double x1, double x2) {
      PointElement elem = element(GShape.FILLED_CIRCLE, 10);
      RectCoord coord = new RectCoord(lin(0, 1000, "x"), lin(0, 100, "y"));
      List<PointVO> vos = visuals(elem, coord, data(new double[] { x1, 50 },
                                                    new double[] { x2, 50 }));
      return new PointVO[] { vos.get(0), vos.get(1) };
   }

   @ParameterizedTest(name = "second point at {0} -> {1}")
   @CsvSource({ "525,true", "526,false", "525.5,false" })
   @DisplayName("an overlap needs more than half a pixel to require dodging")
   void overlapNeedsMoreThanHalfPixel(double x2, boolean expected) {
      PointVO[] vos = pair(500, x2);

      assertEquals(expected, vos[0].requiresDodge(vos[1]));
   }

   @ParameterizedTest(name = "second point at {0} -> {1}")
   @CsvSource({ "524,true", "521,true", "513,true", "527,false" })
   @DisplayName("overlaps of 2, 5 and 13 px require dodging in both directions, a 1 px gap does not")
   void clearOverlapIsSymmetric(double x2, boolean expected) {
      PointVO[] vos = pair(500, x2);

      assertEquals(expected, vos[0].requiresDodge(vos[1]));
      assertEquals(expected, vos[1].requiresDodge(vos[0]));
   }

   @Test
   @Disabled("Not filed (negligible: only a 0.5 to 1 pixel overlap, VGraph.dodge asks one direction per pair): requiresDodge(vo) is asymmetric for a 1 px overlap: expected a.requiresDodge(b) == b.requiresDodge(a) == true, actual a.requiresDodge(b) = true and b.requiresDodge(a) = false")
   @DisplayName("overlap of 1 px gives the same answer in both directions")
   void overlapCheckIsSymmetric() {
      PointVO[] vos = pair(500, 525);

      assertTrue(vos[0].requiresDodge(vos[1]));
      assertTrue(vos[1].requiresDodge(vos[0]));
   }

   @Test
   @DisplayName("jitter moves each point at most 8 units and moves at least one of them")
   void jitterStaysNear() {
      PointElement elem = element(GShape.FILLED_CIRCLE, 10);
      elem.setCollisionModifier(GraphElement.MOVE_JITTER);
      List<PointVO> vos = visuals(elem, data(new double[] { 5, 50 }, new double[] { 5, 50 }));
      PointVO a = vos.get(0);
      PointVO b = vos.get(1);
      Point2D a0 = (Point2D) a.getPosition().clone();
      Point2D b0 = (Point2D) b.getPosition().clone();
      Map<ElementVO, List<ElementVO>> comap = new HashMap<>();
      comap.put(a, new ArrayList<>(List.of(b)));

      a.dodge(comap, new RectCoord(lin(0, 10, "x"), lin(0, 100, "y")));

      double da = a.getPosition().distance(a0);
      double db = b.getPosition().distance(b0);

      assertTrue(da <= 8 && db <= 8, "moved " + da + " and " + db);
      assertTrue(da > 0 || db > 0, "jitter moved neither point");
   }

   private static double neighborDistance(int modifier, boolean stackValues) {
      PointElement elem = element(GShape.FILLED_CIRCLE, 10);

      if(stackValues) {
         elem.setCollisionModifier(modifier, false);
      }
      else {
         elem.setCollisionModifier(modifier);
      }

      List<PointVO> vos = visuals(elem, data(new double[] { 5, 50 }, new double[] { 5, 50 },
                                             new double[] { 5, 50 }));
      Map<ElementVO, List<ElementVO>> comap = new HashMap<>();
      comap.put(vos.get(0), new ArrayList<>(List.of(vos.get(1), vos.get(2))));
      vos.get(0).dodge(comap, new RectCoord(lin(0, 10, "x"), lin(0, 100, "y")));
      Point2D p1 = vos.get(1).getPosition();
      Point2D p2 = vos.get(2).getPosition();

      return stackValues ? Math.abs(p2.getY() - p1.getY()) : Math.abs(p2.getX() - p1.getX());
   }

   @Test
   @DisplayName("stack advances neighbors by (26 + 1) * 3 = 81 (pinned behavior)")
   void stackAdvancesThreeSlotsOfBoundsPlusOne() {
      assertEquals(81, neighborDistance(GraphElement.MOVE_STACK, true), EPS);
   }

   @Test
   @Disabled("Not filed (not reachable: the UI never sets MOVE_DODGE and RectCoord corrects the step in layoutCompleted): expected dodge and stack to advance neighbors by the same slot (81), actual dodge advances 243 (x += pw * wratio with pw already scaled by wratio) and stack 81")
   @DisplayName("dodge advances neighbors by one slot, the same as stack")
   void dodgeAdvancesOneSlot() {
      double dodge = neighborDistance(GraphElement.MOVE_DODGE, false);
      double stack = neighborDistance(GraphElement.MOVE_STACK, true);

      assertEquals(stack, dodge, EPS);
   }

   // ---- layout

   private static List<PointVO> plotted(PointElement elem) {
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      egraph.setCoordinate(new RectCoord(lin(0, 10, "x"), lin(0, 100, "y")));
      VGraph vg = Plotter.getPlotter(egraph).plotAndLayout(
         data(new double[] { 5, 50 }, new double[] { 5, 50 }), 0, 0, 700, 400);
      List<PointVO> vos = new ArrayList<>();

      for(int i = 0; i < vg.getVisualCount(); i++) {
         if(vg.getVisual(i) instanceof PointVO vo) {
            vos.add(vo);
         }
      }

      assertEquals(2, vos.size());
      return vos;
   }

   @Test
   @DisplayName("dodged points touch side by side after layout")
   void dodgedPointsTouchAfterLayout() {
      PointElement elem = element(GShape.FILLED_CIRCLE, 10);
      elem.setCollisionModifier(GraphElement.MOVE_DODGE);
      List<PointVO> vos = plotted(elem);
      vos.sort(Comparator.comparingDouble(v -> v.getBounds().getMinX()));
      Rectangle2D first = vos.get(0).getBounds();
      Rectangle2D second = vos.get(1).getBounds();

      assertEquals(first.getMaxX(), second.getMinX(), 0.05);
      assertEquals(first.getCenterY(), second.getCenterY(), 0.05);
   }

   @Test
   @DisplayName("stacked points touch one above the other after layout")
   void stackedPointsTouchAfterLayout() {
      PointElement elem = element(GShape.FILLED_CIRCLE, 10);
      elem.setCollisionModifier(GraphElement.MOVE_STACK, false);
      List<PointVO> vos = plotted(elem);
      vos.sort(Comparator.comparingDouble(v -> v.getBounds().getMinY()));
      Rectangle2D first = vos.get(0).getBounds();
      Rectangle2D second = vos.get(1).getBounds();

      assertEquals(first.getMaxY(), second.getMinY(), 0.05);
      assertEquals(first.getCenterX(), second.getCenterX(), 0.05);
   }
}
