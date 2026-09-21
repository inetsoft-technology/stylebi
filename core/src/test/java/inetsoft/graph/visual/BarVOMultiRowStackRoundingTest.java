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

import inetsoft.graph.EGraph;
import inetsoft.graph.GGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.geometry.IntervalGeometry;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * bug-76870: barRoundAllCorners removes rounding on a COLOR-aesthetic-driven stacked bar
 * (single var, one x-category split into N&gt;=2 rows) instead of extending it.
 *
 * <p>Every previously-committed test for the e640de186 rounding rewrite ({@code
 * IntervalElementStackOutermostTest}/{@code BarVOStackRoundingTest}) builds its stack from
 * multiple {@code var}s bound to ONE row per category. A COLOR-channel-driven stacked bar
 * instead binds a SINGLE var and splits one x-category across multiple ROWS (one per color
 * category) &mdash; a shape none of the existing tests construct. This test builds exactly
 * that shape, with a long-tail value distribution (one dominant category, several tiny ones),
 * and drives it all the way through {@link IntervalElement#createGeometry} +
 * {@link BarVO#computeArcZones} + {@code BarVO.applyStackRounding} to check whether the
 * resulting rounded shape for the dominant (outermost) segment is degenerate.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BarVOMultiRowStackRoundingTest {

   /**
    * Builds a single-var, multi-row stacked bar (the COLOR-aesthetic shape) with two
    * x-categories, each split into {@code valuesPerCategory.length} rows. Mirrors how a real
    * chart with X=Year, Y=Sum(Total), color=Product:Category is rendered internally: ONE
    * element dim ("Cat"), ONE var ("m1"), and multiple DATA ROWS sharing the same "Cat" value.
    *
    * <p>{@code stackGroup} matters: {@code GraphGenerator.initElement()}
    * (core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java, the
    * {@code GraphTypes.isStack(chartType2)} block) unconditionally calls {@code
    * setStackGroup(true)} on every {@code IntervalElement} for any stacked bar chart type,
    * including CHART_BAR_STACK — so {@code true} is the value real production code actually
    * uses for this bug's own chart type, not the default {@code false}. Both values are
    * exercised here (mirroring {@link IntervalElementStackOutermostTest}'s own convention) so
    * a difference between the two paths is visible rather than silently assumed.</p>
    */
   private GGraph buildMultiRowStack(double[] cat1Values, double[] cat2Values, boolean stackGroup) {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Cat", "m1" });

      for(double v : cat1Values) {
         rows.add(new Object[] { "A", v });
      }

      for(double v : cat2Values) {
         rows.add(new Object[] { "B", v });
      }

      DefaultDataSet data = new DefaultDataSet(rows.toArray(new Object[0][]));

      CategoricalScale xScale = new CategoricalScale("Cat");
      xScale.init(data);

      LinearScale yScale = new LinearScale("m1");
      yScale.init(data);

      // Single dim, single var — exactly the element shape a COLOR-aesthetic-driven
      // single-measure stacked bar produces (the color dimension is NOT an element dim).
      IntervalElement element = new IntervalElement();
      element.addDim("Cat");
      element.addVar("m1");
      element.setCollisionModifier(GraphElement.MOVE_STACK);
      element.setStackGroup(stackGroup);

      EGraph egraph = new EGraph();
      egraph.addElement(element);

      RectCoord coord = new RectCoord(xScale, yScale);
      egraph.setCoordinate(coord);

      return egraph.createGGraph(coord, data);
   }

   private List<IntervalGeometry> collectGeoms(GGraph ggraph) {
      List<IntervalGeometry> geoms = new ArrayList<>();

      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         geoms.add((IntervalGeometry) ggraph.getGeometry(i));
      }

      return geoms;
   }

   /**
    * Sanity check on the untested shape itself: with 7 rows sharing "Cat"="A" and 7 more
    * sharing "Cat"="B", each bar must independently have exactly one outermost/innermost
    * segment, and totalStackInterval must equal THAT bar's own sum — not leak/accumulate
    * across bars (there is no explicit reset of the "top" accumulator between categories in
    * the non-stackGroup case, so this is the concrete risk the diagnosis flagged).
    */
   @ParameterizedTest
   @ValueSource(booleans = {false, true})
   void multiRowSingleVar_perBarAccountingIsIsolated(boolean stackGroup) {
      double[] catA = { 100.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0 }; // sum = 106
      double[] catB = { 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0 };   // sum = 35

      GGraph ggraph = buildMultiRowStack(catA, catB, stackGroup);

      assertEquals(14, ggraph.getGeometryCount(), "7 rows x 2 categories = 14 segments");

      List<IntervalGeometry> geoms = collectGeoms(ggraph);

      // Each of the 7 rows per category is its own DATA ROW (getRowIndex() is the data row
      // index, 0-13 here) — unlike the multi-var tests, getRowIndex() does NOT identify the
      // category; data rows [0, catA.length) share x-category "A", the rest share "B".
      List<IntervalGeometry> rowAGeoms = geoms.stream()
         .filter(g -> g.getRowIndex() < catA.length).collect(Collectors.toList());
      List<IntervalGeometry> rowBGeoms = geoms.stream()
         .filter(g -> g.getRowIndex() >= catA.length).collect(Collectors.toList());

      for(List<IntervalGeometry> rowGeoms : List.of(rowAGeoms, rowBGeoms)) {
         assertEquals(7, rowGeoms.size(), "Expected 7 segments for this category");

         long outermostCount = rowGeoms.stream().filter(IntervalGeometry::isStackOutermost).count();
         long innermostCount = rowGeoms.stream().filter(IntervalGeometry::isStackInnermost).count();
         assertEquals(1, outermostCount, "Exactly one outermost segment for this category");
         assertEquals(1, innermostCount, "Exactly one innermost segment for this category");
      }

      double totalA = rowAGeoms.get(0).getTotalStackInterval();
      double totalB = rowBGeoms.get(0).getTotalStackInterval();

      assertEquals(106.0, totalA, 1e-9,
                   "Category A's totalStackInterval must be its own 7-row sum, not " +
                   "contaminated by category B's rows");
      assertEquals(35.0, totalB, 1e-9,
                   "Category B's totalStackInterval must be its own 7-row sum, not " +
                   "contaminated by category A's rows");

      for(IntervalGeometry g : rowAGeoms) {
         assertEquals(totalA, g.getTotalStackInterval(), 1e-9,
                      "All 7 segments of category A must share the same totalStackInterval");
      }

      for(IntervalGeometry g : rowBGeoms) {
         assertEquals(totalB, g.getTotalStackInterval(), 1e-9,
                      "All 7 segments of category B must share the same totalStackInterval");
      }

      // cumulativeStackInterval must be strictly increasing within each bar, in row-processing
      // order (colIndex ordering matches insertion order here since there's a single var).
      for(List<IntervalGeometry> rowGeoms : List.of(rowAGeoms, rowBGeoms)) {
         List<IntervalGeometry> sorted = rowGeoms.stream()
            .sorted((a, b) -> Integer.compare(a.getSubRowIndex(), b.getSubRowIndex()))
            .collect(Collectors.toList());

         for(int i = 1; i < sorted.size(); i++) {
            assertTrue(sorted.get(i).getCumulativeStackInterval() >
                       sorted.get(i - 1).getCumulativeStackInterval(),
                       "cumulativeStackInterval must increase monotonically within a bar");
         }
      }
   }

   /**
    * The diagnosed mechanism: for the OUTERMOST segment of a long-tail multi-row stack (one
    * dominant category on top, several tiny ones below it), the pixel height of everything
    * BELOW the outermost segment can be smaller than the corner arc radius. With
    * roundAllCorners=true this makes computeArcZones() report BOTH inOuterArcZone and
    * inInnerArcZone true for that segment even though it is not a true single-segment stack
    * (isStackOutermost() &amp;&amp; isStackInnermost() is false). Confirms whether that double
    * activation, fed into applyStackRounding()'s nested Area.intersect ("both ends") branch,
    * produces a degenerate (near-zero-area) shape for the dominant segment.
    */
   @ParameterizedTest
   @ValueSource(booleans = {false, true})
   void longTailStack_outermostSegment_doubleArcZoneDoesNotProduceDegenerateShape(
      boolean stackGroup)
      throws Exception
   {
      // 6 tiny segments (sum = 3.0) then 1 dominant segment (1000.0) on top — the dominant
      // segment is processed last, so it becomes stackOutermost; the 6 tiny segments below it
      // sum to far less than a typical corner radius, matching the diagnosis's precondition.
      double[] longTail = { 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1000.0 };
      double[] otherBar = { 10.0, 10.0 };

      GGraph ggraph = buildMultiRowStack(longTail, otherBar, stackGroup);
      List<IntervalGeometry> rowAGeoms = collectGeoms(ggraph).stream()
         .filter(g -> g.getRowIndex() < longTail.length)
         .sorted((a, b) -> Integer.compare(a.getSubRowIndex(), b.getSubRowIndex()))
         .collect(Collectors.toList());

      assertEquals(7, rowAGeoms.size());

      IntervalGeometry outermost = rowAGeoms.stream()
         .filter(IntervalGeometry::isStackOutermost)
         .findFirst().orElseThrow();

      assertEquals(1000.0, outermost.getInterval(), 1e-9,
                   "The dominant (last-processed) segment must be the one marked outermost");
      assertFalse(outermost.isStackInnermost(),
                  "The dominant segment must NOT also be innermost — this is a genuine " +
                  "multi-segment stack, not the single-segment-stack fast path");

      // Build a screen-space rectangle for the outermost segment proportional to its own
      // interval, using a 1:1 data-unit-to-pixel scale (matches how computeArcZones/
      // applyStackRounding derive "scale" purely from THIS segment's own interval/segDim).
      double barWidth = 100;
      double segDim = outermost.getInterval(); // 1000px tall segment
      double r = 0.3; // barCornerRadius from the report
      boolean roundAllCorners = true;

      BarVO.ArcZoneInfo zones = BarVO.computeArcZones(
         outermost, r, barWidth, segDim, roundAllCorners);

      assertTrue(zones.inOuterArcZone(),
                 "Outermost segment must always be in the outer arc zone");
      assertTrue(zones.inInnerArcZone(),
                 "With roundAllCorners=true and a long tail below it, the outermost segment " +
                 "must ALSO be classified into the inner arc zone (the double-activation " +
                 "precondition this bug depends on)");

      // segBounds: vertical bar, standard orientation, open at top (positive, non-negative).
      // y=0 is this segment's own bottom edge (i.e. cumulative - interval, in pixels);
      // height = segDim (its own interval, 1:1 scale).
      Rectangle2D segBounds = new Rectangle2D.Double(0, 0, barWidth, segDim);

      IntervalElement ielem = (IntervalElement) outermost.getElement();
      ielem.setRoundAllCorners(true);

      Method applyStackRounding = BarVO.class.getDeclaredMethod(
         "applyStackRounding", Shape.class, IntervalGeometry.class, IntervalElement.class,
         double.class, int.class, boolean.class);
      applyStackRounding.setAccessible(true);

      int openDir = 1; // standard orientation, positive value: open at top
      boolean stdOrientation = true;

      Shape rounded = (Shape) applyStackRounding.invoke(
         null, segBounds, outermost, ielem, r, openDir, stdOrientation);

      Rectangle2D roundedBounds = rounded.getBounds2D();

      assertTrue(roundedBounds.getWidth() > 0 && roundedBounds.getHeight() > 0,
                 "Rounded shape bounds must not collapse to zero: " + roundedBounds);

      // The rounded shape must still cover (approximately) the segment's own screen rectangle:
      // its bounding box must span at least the full width and very close to the full height
      // (only the rounded-corner notches, a few tens of pixels at most, may be shaved off).
      assertEquals(segBounds.getWidth(), roundedBounds.getWidth(), 1e-6,
                   "Rounded shape must retain the segment's full width");
      assertTrue(roundedBounds.getHeight() >= segBounds.getHeight() - zones.arc() - 1e-6,
                 "Rounded shape's height collapsed by more than one arc radius: bounds=" +
                 roundedBounds + ", segBounds=" + segBounds + ", arc=" + zones.arc());

      // The segment's own top edge (its real, visible outer/value end) must still be present
      // in the shape at full width — i.e. corners near the top may be rounded (small notches)
      // but the top edge itself is not eaten away by the inner-zone rounding meant for the
      // (irrelevant, far below) true baseline.
      double topY = segBounds.getY() + segBounds.getHeight();
      assertTrue(rounded.contains(barWidth / 2, topY - 0.5),
                 "The segment's own top-center point must remain inside the rounded shape");

      // Area sanity: the rounded shape must retain the vast majority of the segment's area —
      // losing more than what two arc-radius corner notches (2 * arc^2, worst case, applied at
      // both the outer AND inner zone) could account for would indicate a geometry defect
      // (e.g. the inner-zone rounding, sized for the whole stack's arc, overshooting into the
      // dominant segment's own body because the 6 tiny segments below it are thinner than the
      // arc radius).
      java.awt.geom.Area area = new java.awt.geom.Area(rounded);
      double segArea = segBounds.getWidth() * segBounds.getHeight();
      double roundedArea = computeArea(area, segBounds);
      double maxAcceptableLoss = 2 * zones.arc() * zones.arc() * 2; // two corners x two zones
      assertTrue(segArea - roundedArea <= maxAcceptableLoss + 1e-6,
                 "Rounded shape lost too much area to corner rounding: segArea=" + segArea +
                 ", roundedArea=" + roundedArea + ", maxAcceptableLoss=" + maxAcceptableLoss);
   }

   /**
    * Approximates the area of an {@link java.awt.geom.Area} by flattening it and using the
    * shoelace formula per sub-path, since {@code Area} has no direct area accessor.
    */
   private double computeArea(java.awt.geom.Area area, Rectangle2D bounds) {
      java.awt.geom.PathIterator it = area.getPathIterator(null, 0.5);
      double total = 0;
      double startX = 0, startY = 0, prevX = 0, prevY = 0;
      double[] coords = new double[6];

      while(!it.isDone()) {
         int type = it.currentSegment(coords);

         switch(type) {
            case java.awt.geom.PathIterator.SEG_MOVETO:
               startX = prevX = coords[0];
               startY = prevY = coords[1];
               break;
            case java.awt.geom.PathIterator.SEG_LINETO:
               total += prevX * coords[1] - coords[0] * prevY;
               prevX = coords[0];
               prevY = coords[1];
               break;
            case java.awt.geom.PathIterator.SEG_CLOSE:
               total += prevX * startY - startX * prevY;
               prevX = startX;
               prevY = startY;
               break;
         }

         it.next();
      }

      return Math.abs(total) / 2.0;
   }
}
