/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.geometry.IntervalGeometry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BarVO stack rounding geometry helpers.
 */
@Tag("core")
class BarVOStackRoundingTest {

   // -----------------------------------------------------------------------
   // Vertical bars
   // -----------------------------------------------------------------------

   @Test
   void vertical_openTop_fullBarExtendsAboveSegment() {
      // Segment at y=40, h=20. interval=20, cumulative=50, total=80.
      // scale = 20/20 = 1. stackDim = 80. innerOffset = (50-20)*1 = 30.
      // openDir=1 (top): inner end at bottom → fullBarY = 40 - 30 = 10
      Rectangle2D seg = new Rectangle2D.Double(10, 40, 50, 20);
      Rectangle2D full = BarVO.computeFullBarBounds(seg, true, 1, 20, 50, 80);

      assertEquals(10.0, full.getX(), 1e-9);
      assertEquals(10.0, full.getY(), 1e-9);
      assertEquals(50.0, full.getWidth(), 1e-9);
      assertEquals(80.0, full.getHeight(), 1e-9);
   }

   @Test
   void vertical_openBottom_fullBarExtendsBelowSegment() {
      // Segment at y=40, h=20. interval=20, cumulative=50, total=80.
      // scale=1. stackDim=80. innerOffset=30.
      // openDir=0 (bottom): inner end at top → fullBarY = 40+20+30-80 = 10
      Rectangle2D seg = new Rectangle2D.Double(10, 40, 50, 20);
      Rectangle2D full = BarVO.computeFullBarBounds(seg, true, 0, 20, 50, 80);

      assertEquals(10.0, full.getX(), 1e-9);
      assertEquals(10.0, full.getY(), 1e-9);
      assertEquals(50.0, full.getWidth(), 1e-9);
      assertEquals(80.0, full.getHeight(), 1e-9);
   }

   // -----------------------------------------------------------------------
   // Horizontal bars
   // -----------------------------------------------------------------------

   @Test
   void horizontal_openRight_fullBarExtendsRight() {
      // Segment at x=20, w=30. interval=30, cumulative=60, total=100.
      // scale = 30/30 = 1. stackDim = 100. innerOffset = (60-30)*1 = 30.
      // openDir=2 (right): inner end at left → fullBarX = 20 - 30 = -10
      Rectangle2D seg = new Rectangle2D.Double(20, 5, 30, 40);
      Rectangle2D full = BarVO.computeFullBarBounds(seg, false, 2, 30, 60, 100);

      assertEquals(-10.0, full.getX(), 1e-9);
      assertEquals(5.0, full.getY(), 1e-9);
      assertEquals(100.0, full.getWidth(), 1e-9);
      assertEquals(40.0, full.getHeight(), 1e-9);
   }

   @Test
   void horizontal_openLeft_fullBarExtendsLeft() {
      // Segment at x=20, w=30. interval=30, cumulative=60, total=100.
      // scale=1. stackDim=100. innerOffset=30.
      // openDir=3 (left): inner end at right → fullBarX = 20+30+30-100 = -20
      Rectangle2D seg = new Rectangle2D.Double(20, 5, 30, 40);
      Rectangle2D full = BarVO.computeFullBarBounds(seg, false, 3, 30, 60, 100);

      assertEquals(-20.0, full.getX(), 1e-9);
      assertEquals(5.0, full.getY(), 1e-9);
      assertEquals(100.0, full.getWidth(), 1e-9);
      assertEquals(40.0, full.getHeight(), 1e-9);
   }

   // -----------------------------------------------------------------------
   // Edge case: single-segment stack
   // -----------------------------------------------------------------------

   @Test
   void singleSegment_fullBarEqualsSeg() {
      // cumulative == total == interval → innerOffset = 0, full bar == segment
      Rectangle2D seg = new Rectangle2D.Double(10, 20, 60, 40);
      Rectangle2D full = BarVO.computeFullBarBounds(seg, true, 1, 40, 40, 40);

      assertEquals(seg.getX(), full.getX(), 1e-9);
      assertEquals(seg.getY(), full.getY(), 1e-9);
      assertEquals(seg.getWidth(), full.getWidth(), 1e-9);
      assertEquals(seg.getHeight(), full.getHeight(), 1e-9);
   }

   // -----------------------------------------------------------------------
   // computeArcZones: waterfall negative-delta single-segment stack.
   // IntervalElement carries signed totalStackInterval/cumulative when
   // negGrp=false; computeArcZones must normalize via Math.abs.
   // -----------------------------------------------------------------------

   @Test
   void computeArcZones_negativeSingleSegment_detectsOuterArcZone() {
      IntervalGeometry geom = mock(IntervalGeometry.class);
      when(geom.getInterval()).thenReturn(-72.0);
      when(geom.getTotalStackInterval()).thenReturn(-72.0);
      when(geom.getCumulativeStackInterval()).thenReturn(-72.0);

      BarVO.ArcZoneInfo zones = BarVO.computeArcZones(geom, 0.5, 40, 100, false);

      assertTrue(zones.stackDim() > 0, "stackDim should be positive magnitude");
      assertTrue(zones.arc() > 0, "arc radius should be positive");
      assertTrue(zones.inOuterArcZone(),
                 "single-segment bar must register as inside the outer arc zone");
   }

   @Test
   void computeArcZones_positiveSingleSegment_matchesNegative() {
      IntervalGeometry pos = mock(IntervalGeometry.class);
      when(pos.getInterval()).thenReturn(72.0);
      when(pos.getTotalStackInterval()).thenReturn(72.0);
      when(pos.getCumulativeStackInterval()).thenReturn(72.0);

      IntervalGeometry neg = mock(IntervalGeometry.class);
      when(neg.getInterval()).thenReturn(-72.0);
      when(neg.getTotalStackInterval()).thenReturn(-72.0);
      when(neg.getCumulativeStackInterval()).thenReturn(-72.0);

      // roundAllCorners=true so inInnerArcZone is also exercised by the comparison.
      BarVO.ArcZoneInfo posZones = BarVO.computeArcZones(pos, 0.5, 40, 100, true);
      BarVO.ArcZoneInfo negZones = BarVO.computeArcZones(neg, 0.5, 40, 100, true);

      assertEquals(posZones.stackDim(), negZones.stackDim(), 1e-9);
      assertEquals(posZones.arc(), negZones.arc(), 1e-9);
      assertEquals(posZones.inOuterArcZone(), negZones.inOuterArcZone());
      assertEquals(posZones.inInnerArcZone(), negZones.inInnerArcZone());
   }

   // -----------------------------------------------------------------------
   // Funnel per-facet shaping guard (Bug #75533).
   // A faceted (multi-dimension) funnel chart lays out each facet in its own
   // sub-graph/dodge() pass but they share one IntervalElement instance. The
   // shaping guard must be scoped per coordinate so every facet is shaped, not
   // just the first one — while still de-duplicating repeated calls from the
   // multiple bars within a single facet's pass.
   // -----------------------------------------------------------------------

   @Test
   void markFunnelShaped_scopedPerFacetCoordinate() {
      // Mock the element with a real hint map rather than constructing a concrete
      // GraphElement: GraphElement.<init> pulls in GDefaults, which reads SreeEnv and
      // requires a running Spring context. markFunnelShaped only get/setHint on elem.
      GraphElement elem = mockElementWithHints();
      Coordinate facetA = mock(Coordinate.class);
      Coordinate facetB = mock(Coordinate.class);

      // First bar of facet A: not yet shaped -> caller proceeds with reshaping.
      assertTrue(BarVO.markFunnelShaped(elem, facetA),
                 "first bar of facet A should trigger funnel shaping");
      // Another bar of facet A in the same pass: already shaped -> caller returns early.
      assertFalse(BarVO.markFunnelShaped(elem, facetA),
                  "later bars of facet A must be de-duplicated");

      // Facet B shares the same element but is a distinct coordinate: it must still
      // be shaped. Before the fix an element-level flag suppressed every facet after
      // the first, which is exactly the regression this guards against.
      assertTrue(BarVO.markFunnelShaped(elem, facetB),
                 "facet B must be shaped even though it shares the element with facet A");
      assertFalse(BarVO.markFunnelShaped(elem, facetB),
                  "later bars of facet B must be de-duplicated");
   }

   // -----------------------------------------------------------------------
   // Funnel level grouping / reshaping (Bug #75528).
   // When a text/color aesthetic is bound to a funnel, the aesthetic field
   // becomes a grouping dimension so each category renders as a stacked column
   // of segments. reshapeFunnel must collect every segment (from both the
   // collision-map keys AND their overlapped value lists, since only the bottom
   // segment of a column is a key), group them into levels by x, and reshape
   // each level so only its outer silhouette tapers while inner stacked dividers
   // stay horizontal. cy = GHEIGHT/2 = 500 throughout.
   // -----------------------------------------------------------------------

   @Test
   void reshapeFunnel_singleSegmentLevels_reduceToTaperedTrapezoids() {
      GraphElement elem = mock(GraphElement.class);
      when(elem.getCollisionModifier()).thenReturn(GraphElement.MOVE_MIDDLE);

      // Two single-segment levels: left total height 200, right total height 100.
      BarVO left  = funnelBar(elem, 100, 400, 50, 200);
      BarVO right = funnelBar(elem, 300, 450, 50, 100);

      Map<ElementVO, List<ElementVO>> comap = new LinkedHashMap<>();
      comap.put(left, new ArrayList<>());
      comap.put(right, new ArrayList<>());

      BarVO.reshapeFunnel(comap, elem);

      // Left edge = own height (200 -> cy±100 = 400..600), right edge = the next
      // level's height (100 -> cy±50 = 450..550): a left-to-right tapered trapezoid.
      assertCorners(left, new double[][] {
         {100, 600}, {150, 550}, {150, 450}, {100, 400}
      });
      // Last level has no successor, so both edges use its own height (flat end).
      assertCorners(right, new double[][] {
         {300, 550}, {350, 550}, {350, 450}, {300, 450}
      });
   }

   @Test
   void reshapeFunnel_stackedLevel_keepsInnerDividerHorizontalAndSilhouetteSeamless() {
      GraphElement elem = mock(GraphElement.class);
      when(elem.getCollisionModifier()).thenReturn(GraphElement.MOVE_MIDDLE);

      // Left level: single segment, total height 200.
      BarVO left = funnelBar(elem, 100, 400, 50, 200);
      // Right level: two stacked segments (as a text/color binding produces),
      // total height 100. Only the bottom segment is a collision-map key; the top
      // segment lives in the key's overlapped value list — this is exactly what the
      // fix for #75528 must pick up.
      BarVO rightBottom = funnelBar(elem, 300, 450, 50, 60);
      BarVO rightTop    = funnelBar(elem, 300, 510, 50, 40);

      Map<ElementVO, List<ElementVO>> comap = new LinkedHashMap<>();
      comap.put(left, new ArrayList<>());
      List<ElementVO> overlapped = new ArrayList<>();
      overlapped.add(rightTop);
      comap.put(rightBottom, overlapped);

      BarVO.reshapeFunnel(comap, elem);

      // Left tapers from its own height (200) to the right level's total (100).
      assertCorners(left, new double[][] {
         {100, 600}, {150, 550}, {150, 450}, {100, 400}
      });
      // The stacked right level (total 100, last level so no taper) occupies
      // cy±50 = 450..550. Both segments are plain rectangles separated by a single
      // straight horizontal divider at y = 510 — the upper segment is no longer
      // left as a stray stacked rectangle (the #75528 regression).
      assertBounds(rightBottom, 300, 450, 50, 60);
      assertBounds(rightTop,    300, 510, 50, 40);
      // Divider is shared exactly: bottom segment's top == top segment's bottom.
      assertEquals(510.0, rightBottom.shape.getBounds2D().getMaxY(), 1e-6);
      assertEquals(510.0, rightTop.shape.getBounds2D().getMinY(), 1e-6);
   }

   /**
    * Build a mocked funnel BarVO whose geometry belongs to elem and whose initial
    * shape is the given rectangle. Mockito/Objenesis bypasses the constructor (and
    * its field initializers), so the private cachedShape SoftReference is null on a
    * mock; reshapeFunnel calls cachedShape.clear(), so seed it with a real reference.
    */
   private static BarVO funnelBar(GraphElement elem, double x, double y, double w, double h) {
      BarVO bar = mock(BarVO.class);
      ElementGeometry geom = mock(ElementGeometry.class);
      when(geom.getElement()).thenReturn(elem);
      when(bar.getGeometry()).thenReturn(geom);
      bar.shape = new Rectangle2D.Double(x, y, w, h);

      try {
         Field f = BarVO.class.getDeclaredField("cachedShape");
         f.setAccessible(true);
         f.set(bar, new SoftReference<>(null));
      }
      catch(ReflectiveOperationException ex) {
         throw new IllegalStateException("failed to seed cachedShape", ex);
      }

      return bar;
   }

   /**
    * Assert the reshaped path's four corners (moveTo + 3 lineTo) match, in order.
    */
   private static void assertCorners(BarVO bar, double[][] expected) {
      double[] coords = new double[6];
      List<double[]> pts = new ArrayList<>();
      PathIterator it = bar.shape.getPathIterator(null);

      while(!it.isDone()) {
         int type = it.currentSegment(coords);

         if(type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
            pts.add(new double[] { coords[0], coords[1] });
         }

         it.next();
      }

      assertEquals(expected.length, pts.size(), "corner count");

      for(int i = 0; i < expected.length; i++) {
         assertEquals(expected[i][0], pts.get(i)[0], 1e-6, "corner " + i + " x");
         assertEquals(expected[i][1], pts.get(i)[1], 1e-6, "corner " + i + " y");
      }
   }

   /**
    * A mocked GraphElement whose getHint/setHint are backed by a real map, so
    * markFunnelShaped's hint state persists across calls without constructing a
    * concrete element (which would require the Spring/SreeEnv context).
    */
   private static GraphElement mockElementWithHints() {
      GraphElement elem = mock(GraphElement.class);
      Map<String, Object> hints = new HashMap<>();
      when(elem.getHint(anyString())).thenAnswer(inv -> hints.get(inv.<String>getArgument(0)));
      doAnswer(inv -> {
         hints.put(inv.getArgument(0), inv.getArgument(1));
         return null;
      }).when(elem).setHint(anyString(), any());
      return elem;
   }

   private static void assertBounds(BarVO bar, double x, double y, double w, double h) {
      Rectangle2D b = bar.shape.getBounds2D();
      assertEquals(x, b.getX(), 1e-6, "x");
      assertEquals(y, b.getY(), 1e-6, "y");
      assertEquals(w, b.getWidth(), 1e-6, "width");
      assertEquals(h, b.getHeight(), 1e-6, "height");
   }
}
