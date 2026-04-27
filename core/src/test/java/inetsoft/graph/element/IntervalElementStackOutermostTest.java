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
package inetsoft.graph.element;

import inetsoft.graph.EGraph;
import inetsoft.graph.GGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.geometry.IntervalGeometry;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that IntervalElement.createGeometry() correctly marks:
 * <ul>
 *   <li>the outermost (farthest from baseline) segment per bar as stackOutermost</li>
 *   <li>the innermost (closest to baseline) segment per bar as stackInnermost</li>
 * </ul>
 * These flags are used by BarVO and GraphBuilder to apply rounded corners to the correct
 * ends of each stacked bar, and to support the roundAllCorners mode.
 */
@SreeHome
class IntervalElementStackOutermostTest {

   /**
    * In a 2-category x 2-measure stacked bar, the outermost segment per category
    * (m2, stacked on top of m1) must have isStackOutermost() == true.
    * Tested for both stackGroup=false (non-stackGroup) and stackGroup=true paths.
    */
   @ParameterizedTest
   @ValueSource(booleans = {false, true})
   void stackOutermost_isSetOnLastSegmentPerBar(boolean stackGroup) {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "Cat", "m1",  "m2"  },
         { "A",   10.0,  20.0  },
         { "B",   30.0,  40.0  },
      });

      CategoricalScale xScale = new CategoricalScale("Cat");
      xScale.init(data);

      LinearScale yScale = new LinearScale("m1", "m2");
      yScale.init(data);

      IntervalElement element = new IntervalElement();
      element.addDim("Cat");
      element.addVar("m1");
      element.addVar("m2");
      element.setCollisionModifier(GraphElement.MOVE_STACK); // enables stack
      element.setStackGroup(stackGroup);

      EGraph egraph = new EGraph();
      egraph.addElement(element);

      RectCoord coord = new RectCoord(xScale, yScale);
      egraph.setCoordinate(coord);

      GGraph ggraph = egraph.createGGraph(coord, data);

      int geomCount = ggraph.getGeometryCount();
      assertEquals(4, geomCount, "Expected 4 geometries: 2 categories x 2 measures");

      List<IntervalGeometry> geoms = new ArrayList<>();

      for(int i = 0; i < geomCount; i++) {
         geoms.add((IntervalGeometry) ggraph.getGeometry(i));
      }

      // Exactly one outermost per category = 2 total
      long outermostCount = geoms.stream().filter(IntervalGeometry::isStackOutermost).count();
      assertEquals(2, outermostCount, "One outermost segment per category expected");

      // For each row (0=A, 1=B): the outermost must be m2 (colIndex 2), not m1 (colIndex 1)
      for(int rowIdx = 0; rowIdx <= 1; rowIdx++) {
         final int r = rowIdx;
         List<IntervalGeometry> rowGeoms = geoms.stream()
            .filter(g -> g.getRowIndex() == r)
            .collect(Collectors.toList());

         assertEquals(2, rowGeoms.size(), "Expected 2 segments for row " + rowIdx);

         IntervalGeometry outermost = rowGeoms.stream()
            .filter(IntervalGeometry::isStackOutermost)
            .findFirst().orElse(null);
         IntervalGeometry inner = rowGeoms.stream()
            .filter(g -> !g.isStackOutermost())
            .findFirst().orElse(null);

         assertNotNull(outermost, "Row " + rowIdx + " must have one outermost segment");
         assertNotNull(inner, "Row " + rowIdx + " must have one inner segment");

         // DefaultDataSet assigns column indices by declaration order: "Cat"=0, "m1"=1, "m2"=2.
         // m2 (colIndex 2) is stacked on top of m1 (colIndex 1), so it is farthest from the
         // baseline and must be the outermost segment. colIndex reliably reflects stacking order
         // because measures are iterated in declaration order in createGeometry().
         assertTrue(inner.getColIndex() < outermost.getColIndex(),
                    "m1 (inner, colIndex 1) must have lower colIndex than m2 (outermost, colIndex 2)" +
                    " for row " + rowIdx);
      }
   }

   /**
    * With all negative values (negGrp=true by default), the outermost segment per bar
    * is the one farthest below the baseline — i.e., the last negative segment added
    * (tracked via lastNegative). One outermost per category expected.
    */
   @Test
   void stackOutermost_allNegativeValues() {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "Cat", "m1",    "m2"    },
         { "A",   -10.0,  -20.0   },
         { "B",   -30.0,  -40.0   },
      });

      CategoricalScale xScale = new CategoricalScale("Cat");
      xScale.init(data);

      LinearScale yScale = new LinearScale("m1", "m2");
      yScale.init(data);

      IntervalElement element = new IntervalElement();
      element.addDim("Cat");
      element.addVar("m1");
      element.addVar("m2");
      element.setCollisionModifier(GraphElement.MOVE_STACK);
      // negGrp defaults to true — negative values stack downward separately

      EGraph egraph = new EGraph();
      egraph.addElement(element);

      RectCoord coord = new RectCoord(xScale, yScale);
      egraph.setCoordinate(coord);

      GGraph ggraph = egraph.createGGraph(coord, data);

      assertEquals(4, ggraph.getGeometryCount());

      List<IntervalGeometry> geoms = new ArrayList<>();

      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         geoms.add((IntervalGeometry) ggraph.getGeometry(i));
      }

      long outermostCount = geoms.stream().filter(IntervalGeometry::isStackOutermost).count();
      assertEquals(2, outermostCount, "One outermost negative segment per category expected");

      long innermostCount = geoms.stream().filter(IntervalGeometry::isStackInnermost).count();
      assertEquals(2, innermostCount, "One innermost negative segment per category expected");

      // The outermost negative segment (farthest below baseline) is m2, which is
      // stacked further down than m1. The innermost is m1, closest to the baseline.
      for(int rowIdx = 0; rowIdx <= 1; rowIdx++) {
         final int r = rowIdx;
         IntervalGeometry outermost = geoms.stream()
            .filter(g -> g.getRowIndex() == r && g.isStackOutermost())
            .findFirst().orElse(null);
         IntervalGeometry innermost = geoms.stream()
            .filter(g -> g.getRowIndex() == r && g.isStackInnermost())
            .findFirst().orElse(null);

         assertNotNull(outermost, "Row " + rowIdx + " must have one outermost segment");
         assertNotNull(innermost, "Row " + rowIdx + " must have one innermost segment");

         // DefaultDataSet assigns column indices by declaration order: "Cat"=0, "m1"=1, "m2"=2.
         // m2 (colIndex 2) is stacked below m1 (colIndex 1), so it is farthest from the
         // baseline and must be the outermost segment.
         // m1 (colIndex 1) is stacked directly from the baseline, so it must be innermost.
         assertTrue(innermost.getColIndex() < outermost.getColIndex(),
                    "m1 (innermost) must have lower colIndex than m2 (outermost) for row " + rowIdx);
      }
   }

   /**
    * With mixed positive and negative values, lastPositive and lastNegative are
    * tracked independently. Both the topmost positive and the bottommost negative
    * segment per bar must be marked outermost.
    */
   @Test
   void stackOutermost_mixedPositiveAndNegative() {
      // Row A: m1=10 (positive), m2=-5 (negative), m3=15 (positive)
      // Row B: m1=-8 (negative), m2=20 (positive), m3=-3 (negative)
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "Cat", "m1",   "m2",  "m3"  },
         { "A",   10.0,  -5.0,  15.0  },
         { "B",  -8.0,   20.0,  -3.0  },
      });

      CategoricalScale xScale = new CategoricalScale("Cat");
      xScale.init(data);

      LinearScale yScale = new LinearScale("m1", "m2", "m3");
      yScale.init(data);

      IntervalElement element = new IntervalElement();
      element.addDim("Cat");
      element.addVar("m1");
      element.addVar("m2");
      element.addVar("m3");
      element.setCollisionModifier(GraphElement.MOVE_STACK);

      EGraph egraph = new EGraph();
      egraph.addElement(element);

      RectCoord coord = new RectCoord(xScale, yScale);
      egraph.setCoordinate(coord);

      GGraph ggraph = egraph.createGGraph(coord, data);

      assertEquals(6, ggraph.getGeometryCount());

      List<IntervalGeometry> geoms = new ArrayList<>();

      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         geoms.add((IntervalGeometry) ggraph.getGeometry(i));
      }

      // Each bar has both positive and negative outermost segments,
      // so total outermost count = 4 (2 categories x 2 stacks each)
      long outermostCount = geoms.stream().filter(IntervalGeometry::isStackOutermost).count();
      assertEquals(4, outermostCount,
                   "Each bar must have one outermost positive and one outermost negative segment");

      // Each bar also has one innermost per sign: the first positive and first negative segment.
      // Row A (m1=10, m2=-5, m3=15): firstPositive=m1(col 1), firstNegative=m2(col 2) → 2 innermosts
      // Row B (m1=-8, m2=20, m3=-3): firstNegative=m1(col 1), firstPositive=m2(col 2) → 2 innermosts
      long innermostCount = geoms.stream().filter(IntervalGeometry::isStackInnermost).count();
      assertEquals(4, innermostCount,
                   "Each bar must have one innermost positive and one innermost negative segment");

      // Verify: exactly 2 outermost and 2 innermost segments per row
      for(int rowIdx = 0; rowIdx <= 1; rowIdx++) {
         final int r = rowIdx;
         long rowOutermost = geoms.stream()
            .filter(g -> g.getRowIndex() == r && g.isStackOutermost())
            .count();
         assertEquals(2, rowOutermost,
                      "Row " + rowIdx + " must have exactly 2 outermost segments (one positive, one negative)");

         long rowInnermost = geoms.stream()
            .filter(g -> g.getRowIndex() == r && g.isStackInnermost())
            .count();
         assertEquals(2, rowInnermost,
                      "Row " + rowIdx + " must have exactly 2 innermost segments (one positive, one negative)");
      }
   }

   /**
    * In a 2-category x 2-measure stacked bar, the innermost segment per category
    * (m1, closest to the baseline) must have isStackInnermost() == true.
    * Tested for both stackGroup=false (non-stackGroup) and stackGroup=true paths.
    */
   @ParameterizedTest
   @ValueSource(booleans = {false, true})
   void stackInnermost_isSetOnFirstSegmentPerBar(boolean stackGroup) {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "Cat", "m1",  "m2"  },
         { "A",   10.0,  20.0  },
         { "B",   30.0,  40.0  },
      });

      CategoricalScale xScale = new CategoricalScale("Cat");
      xScale.init(data);

      LinearScale yScale = new LinearScale("m1", "m2");
      yScale.init(data);

      IntervalElement element = new IntervalElement();
      element.addDim("Cat");
      element.addVar("m1");
      element.addVar("m2");
      element.setCollisionModifier(GraphElement.MOVE_STACK);
      element.setStackGroup(stackGroup);

      EGraph egraph = new EGraph();
      egraph.addElement(element);

      RectCoord coord = new RectCoord(xScale, yScale);
      egraph.setCoordinate(coord);

      GGraph ggraph = egraph.createGGraph(coord, data);

      int geomCount = ggraph.getGeometryCount();
      assertEquals(4, geomCount, "Expected 4 geometries: 2 categories x 2 measures");

      List<IntervalGeometry> geoms = new ArrayList<>();

      for(int i = 0; i < geomCount; i++) {
         geoms.add((IntervalGeometry) ggraph.getGeometry(i));
      }

      // Exactly one innermost per category = 2 total
      long innermostCount = geoms.stream().filter(IntervalGeometry::isStackInnermost).count();
      assertEquals(2, innermostCount, "One innermost segment per category expected");

      // For each row (0=A, 1=B): the innermost must be m1 (colIndex 1), not m2 (colIndex 2)
      for(int rowIdx = 0; rowIdx <= 1; rowIdx++) {
         final int r = rowIdx;
         List<IntervalGeometry> rowGeoms = geoms.stream()
            .filter(g -> g.getRowIndex() == r)
            .collect(Collectors.toList());

         assertEquals(2, rowGeoms.size(), "Expected 2 segments for row " + rowIdx);

         IntervalGeometry innermost = rowGeoms.stream()
            .filter(IntervalGeometry::isStackInnermost)
            .findFirst().orElse(null);
         IntervalGeometry outer = rowGeoms.stream()
            .filter(g -> !g.isStackInnermost())
            .findFirst().orElse(null);

         assertNotNull(innermost, "Row " + rowIdx + " must have one innermost segment");
         assertNotNull(outer, "Row " + rowIdx + " must have one non-innermost segment");

         // m1 (colIndex 1) is stacked directly on the baseline, so it is innermost.
         // m2 (colIndex 2) is stacked on top of m1 and must NOT be innermost.
         assertTrue(innermost.getColIndex() < outer.getColIndex(),
                    "m1 (innermost, colIndex 1) must have lower colIndex than m2 (outer, colIndex 2)" +
                    " for row " + rowIdx);
      }
   }

   /**
    * A single-measure stacked bar has only one segment per bar, which is simultaneously
    * the closest and farthest segment from the baseline. Both stackOutermost and
    * stackInnermost must be true on that segment.
    */
   @Test
   void singleSegmentStack_bothOutermostAndInnermostAreSet() {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "Cat", "m1"  },
         { "A",   10.0  },
         { "B",   30.0  },
      });

      CategoricalScale xScale = new CategoricalScale("Cat");
      xScale.init(data);

      LinearScale yScale = new LinearScale("m1");
      yScale.init(data);

      IntervalElement element = new IntervalElement();
      element.addDim("Cat");
      element.addVar("m1");
      element.setCollisionModifier(GraphElement.MOVE_STACK);

      EGraph egraph = new EGraph();
      egraph.addElement(element);

      RectCoord coord = new RectCoord(xScale, yScale);
      egraph.setCoordinate(coord);

      GGraph ggraph = egraph.createGGraph(coord, data);

      int geomCount = ggraph.getGeometryCount();
      assertEquals(2, geomCount, "Expected 2 geometries: 2 categories x 1 measure");

      for(int i = 0; i < geomCount; i++) {
         IntervalGeometry geom = (IntervalGeometry) ggraph.getGeometry(i);
         assertTrue(geom.isStackOutermost(),
                    "Single-segment stack must be marked outermost (geometry " + i + ")");
         assertTrue(geom.isStackInnermost(),
                    "Single-segment stack must be marked innermost (geometry " + i + ")");
      }
   }

   // -----------------------------------------------------------------------
   // totalStackInterval and cumulativeStackInterval
   // -----------------------------------------------------------------------

   /**
    * All segments in the same stack must share the same totalStackInterval.
    */
   @ParameterizedTest
   @ValueSource(booleans = {false, true})
   void totalStackInterval_sameForAllSegmentsInStack(boolean stackGroup) {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "Cat", "m1",  "m2",  "m3"  },
         { "A",   10.0,  20.0,  30.0  },
         { "B",    5.0,  15.0,  25.0  },
      });

      GGraph ggraph = buildStackedGraph(data, stackGroup, "m1", "m2", "m3");

      assertEquals(6, ggraph.getGeometryCount());

      for(int rowIdx = 0; rowIdx <= 1; rowIdx++) {
         final int r = rowIdx;
         List<IntervalGeometry> rowGeoms = collectGeoms(ggraph).stream()
            .filter(g -> g.getRowIndex() == r)
            .collect(Collectors.toList());

         double total = rowGeoms.get(0).getTotalStackInterval();
         assertTrue(total > 0, "totalStackInterval must be positive for row " + rowIdx);

         for(IntervalGeometry g : rowGeoms) {
            assertEquals(total, g.getTotalStackInterval(), 1e-9,
                         "All segments in row " + rowIdx + " must share the same totalStackInterval");
         }
      }
   }

   /**
    * cumulativeStackInterval must increase strictly across segments in stacking order.
    */
   @Test
   void cumulativeStackInterval_increasesMonotonically() {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "Cat", "m1",  "m2",  "m3"  },
         { "A",   10.0,  20.0,  30.0  },
      });

      GGraph ggraph = buildStackedGraph(data, true, "m1", "m2", "m3");

      List<IntervalGeometry> geoms = collectGeoms(ggraph).stream()
         .sorted((a, b) -> Integer.compare(a.getColIndex(), b.getColIndex()))
         .collect(Collectors.toList());

      assertEquals(3, geoms.size());

      for(int i = 1; i < geoms.size(); i++) {
         assertTrue(geoms.get(i).getCumulativeStackInterval() >
                    geoms.get(i - 1).getCumulativeStackInterval(),
                    "cumulativeStackInterval must be strictly increasing at index " + i);
      }
   }

   /**
    * The outermost segment's cumulativeStackInterval must equal its totalStackInterval.
    */
   @Test
   void lastSegment_cumulativeEqualsTotal() {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "Cat", "m1",  "m2"  },
         { "A",   10.0,  20.0  },
      });

      GGraph ggraph = buildStackedGraph(data, true, "m1", "m2");

      IntervalGeometry outermost = collectGeoms(ggraph).stream()
         .filter(IntervalGeometry::isStackOutermost)
         .findFirst().orElseThrow();

      assertEquals(outermost.getTotalStackInterval(),
                   outermost.getCumulativeStackInterval(), 1e-9,
                   "Outermost segment's cumulative must equal total");
   }

   /**
    * Negative-value stacks must have positive totalStackInterval (uses Math.abs).
    */
   @Test
   void negativeValues_totalStackIntervalIsPositive() {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "Cat", "m1",    "m2"    },
         { "A",   -10.0,  -20.0   },
      });

      GGraph ggraph = buildStackedGraph(data, true, "m1", "m2");

      for(IntervalGeometry g : collectGeoms(ggraph)) {
         assertTrue(g.getTotalStackInterval() > 0,
                    "totalStackInterval must be positive even for negative values");
         assertTrue(g.getCumulativeStackInterval() > 0,
                    "cumulativeStackInterval must be positive even for negative values");
      }
   }

   // -----------------------------------------------------------------------
   // Helper methods
   // -----------------------------------------------------------------------

   private GGraph buildStackedGraph(DefaultDataSet data, boolean stackGroup,
                                    String... measures)
   {
      CategoricalScale xScale = new CategoricalScale("Cat");
      xScale.init(data);

      LinearScale yScale = new LinearScale(measures);
      yScale.init(data);

      IntervalElement element = new IntervalElement();
      element.addDim("Cat");

      for(String m : measures) {
         element.addVar(m);
      }

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

   // -----------------------------------------------------------------------
   // Outermost/Innermost flag tests (original)
   // -----------------------------------------------------------------------

   /**
    * Non-stacked bars must not have any segment marked as outermost, since the
    * concept is only meaningful when segments are visually stacked.
    */
   @Test
   void nonStackedBar_noSegmentIsOutermost() {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "Cat", "m1"  },
         { "A",   10.0  },
         { "B",   30.0  },
      });

      CategoricalScale xScale = new CategoricalScale("Cat");
      xScale.init(data);

      LinearScale yScale = new LinearScale("m1");
      yScale.init(data);

      IntervalElement element = new IntervalElement();
      element.addDim("Cat");
      element.addVar("m1");
      // stack not enabled — default is false

      EGraph egraph = new EGraph();
      egraph.addElement(element);

      RectCoord coord = new RectCoord(xScale, yScale);
      egraph.setCoordinate(coord);

      GGraph ggraph = egraph.createGGraph(coord, data);

      int geomCount = ggraph.getGeometryCount();
      assertEquals(2, geomCount);

      for(int i = 0; i < geomCount; i++) {
         IntervalGeometry geom = (IntervalGeometry) ggraph.getGeometry(i);
         assertFalse(geom.isStackOutermost(),
                     "Non-stacked bar segments must not be marked as outermost");
         assertFalse(geom.isStackInnermost(),
                     "Non-stacked bar segments must not be marked as innermost");
      }
   }
}
