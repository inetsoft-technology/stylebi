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
import inetsoft.graph.coord.TileCoordTest.SC;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.internal.ICoordinate;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TileCoord} layout: the bounds of the inner coordinates of a facet cell, unit counts,
 * the grid boundary lines, and the tiles of a real facet chart.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class TileCoordLayoutTest {
   private static final int TOP = ICoordinate.TOP_AXIS;
   private static final int LEFT = ICoordinate.LEFT_AXIS;
   private static final int BOTTOM = ICoordinate.BOTTOM_AXIS;
   private static final int RIGHT = ICoordinate.RIGHT_AXIS;
   private static final double EPS = 1e-6;

   private static CategoricalScale cat(String field) {
      CategoricalScale xs = new CategoricalScale(field);
      xs.init("A", "B", "C");
      return xs;
   }

   /** x categorical A,B,C (3 units), y linear (1 unit). */
   private static RectCoord rectLinearY() {
      LinearScale ys = new LinearScale("m");
      ys.setMin(0);
      ys.setMax(100);
      return new RectCoord(cat("Cat"), ys);
   }

   /** x and y categorical A,B,C (3 units each). */
   private static RectCoord rectCatY() {
      return new RectCoord(cat("Cat"), cat("Cat2"));
   }

   private static Rect25Coord r25(double depth) {
      LinearScale ys = new LinearScale("m");
      ys.setMin(0);
      ys.setMax(100);
      Rect25Coord coord = new Rect25Coord(cat("Cat"), ys);
      coord.setDepth(depth);
      return coord;
   }

   private static void sizeAxes(SC sc, double l, double r, double t, double b) {
      sc.size[LEFT] = l;
      sc.size[RIGHT] = r;
      sc.size[TOP] = t;
      sc.size[BOTTOM] = b;
   }

   private static void assertBounds(double x, double y, double w, double h, Coordinate coord) {
      Rectangle2D b = coord.getCoordBounds();
      assertNotNull(b);
      assertEquals(x, b.getX(), EPS, "x");
      assertEquals(y, b.getY(), EPS, "y");
      assertEquals(w, b.getWidth(), EPS, "width");
      assertEquals(h, b.getHeight(), EPS, "height");
   }

   // ---------------------------------------------------------------- layout arithmetic

   @Test
   @DisplayName("vertical layout adds the axis sizes to the cell size")
   void verticalLayoutAddsAxisSizes() {
      SC sc = new SC();
      sizeAxes(sc, 10, 20, 5, 7);
      TileCoord.vertical(sc).layout(3, 4, 100, 50);
      assertBounds(3, 4, 130, 62, sc);
   }

   @Test
   @DisplayName("vertical layout stacks the tiles with no gap and no overlap")
   void verticalLayoutStack() {
      SC[] cs = { new SC(), new SC(), new SC() };
      sizeAxes(cs[0], 10, 20, 5, 7);
      sizeAxes(cs[2], 10, 20, 3, 4);
      TileCoord.vertical(cs).layout(3, 4, 100, 50);

      assertBounds(3, 4, 130, 62, cs[0]);
      assertBounds(3, 66, 100, 50, cs[1]);
      assertBounds(3, 116, 130, 57, cs[2]);
   }

   @Test
   @DisplayName("horizontal layout places the tiles side by side with no gap and no overlap")
   void horizontalLayoutStack() {
      SC[] cs = { new SC(), new SC(), new SC() };
      sizeAxes(cs[0], 10, 20, 5, 7);
      sizeAxes(cs[2], 10, 20, 3, 4);
      TileCoord.horizontal(cs).layout(3, 4, 100, 50);

      assertBounds(3, 4, 130, 62, cs[0]);
      assertBounds(133, 4, 100, 50, cs[1]);
      assertBounds(233, 4, 130, 57, cs[2]);
   }

   @Test
   @DisplayName("vertical layout of 2.5D coordinates adds the depth to every tile's width")
   void verticalLayoutWithDepth() {
      Rect25Coord c0 = r25(15);
      Rect25Coord c1 = r25(15);
      TileCoord.vertical(c0, c1).layout(0, 0, 100, 50);

      assertBounds(0, 0, 315, 50, c0);
      assertBounds(0, 50, 315, 50, c1);
   }

   @Test
   @DisplayName("horizontal layout of one 2.5D coordinate adds the depth to its width")
   void horizontalLayoutWithDepthSingleTile() {
      Rect25Coord c0 = r25(15);
      TileCoord.horizontal(c0).layout(0, 0, 100, 50);
      assertBounds(0, 0, 315, 50, c0);
   }

   @Test
   @DisplayName("horizontal layout of two 2.5D coordinates neither cuts the depth from the "
      + "cell nor adds it to the tile")
   void horizontalLayoutWithDepthTwoTiles() {
      Rect25Coord c0 = r25(15);
      Rect25Coord c1 = r25(15);
      TileCoord.horizontal(c0, c1).layout(0, 0, 100, 50);

      assertBounds(0, 0, 300, 50, c0);
      assertBounds(300, 0, 300, 50, c1);
   }

   @Test
   @DisplayName("horizontal layout of two coordinates without depth: widths are the unit count "
      + "times the cell width")
   void horizontalLayoutNoDepth() {
      RectCoord c0 = rectLinearY();
      RectCoord c1 = rectLinearY();
      TileCoord.horizontal(c0, c1).layout(0, 0, 100, 50);

      assertBounds(0, 0, 300, 50, c0);
      assertBounds(300, 0, 300, 50, c1);
   }

   // ---------------------------------------------------------------- unit counts

   @Test
   @DisplayName("vertical unit counts: x is the first tile's, y is the sum over the tiles")
   void verticalUnitCountsLinearY() {
      TileCoord tile = TileCoord.vertical(rectLinearY(), rectLinearY(), rectLinearY());
      assertEquals(3, tile.getXUnitCount());
      assertEquals(3, tile.getYUnitCount());
   }

   @Test
   @DisplayName("vertical unit counts with categorical y: y adds the categories of each tile")
   void verticalUnitCountsCategoricalY() {
      TileCoord tile = TileCoord.vertical(rectCatY(), rectCatY(), rectCatY());
      assertEquals(3, tile.getXUnitCount());
      assertEquals(9, tile.getYUnitCount());
   }

   @Test
   @DisplayName("horizontal unit counts: x is the sum over the tiles")
   void horizontalUnitCounts() {
      TileCoord tile = TileCoord.horizontal(rectLinearY(), rectLinearY(), rectLinearY());
      assertEquals(9, tile.getXUnitCount());
   }

   @Test
   @DisplayName("horizontal unit counts: the panels share one row, so y is 1")
   void horizontalYUnitCount() {
      TileCoord tile = TileCoord.horizontal(rectLinearY(), rectLinearY(), rectLinearY());
      assertEquals(1, tile.getYUnitCount());
   }

   @Test
   @DisplayName("vertical layout agrees with the unit counts: widths and total height")
   void verticalLayoutMatchesUnitCounts() {
      RectCoord c0 = rectCatY();
      RectCoord c1 = rectCatY();
      TileCoord tile = TileCoord.vertical(c0, c1);
      tile.layout(0, 0, 40, 30);

      assertEquals(tile.getXUnitCount() * 40, c0.getCoordBounds().getWidth(), EPS);
      assertEquals(tile.getXUnitCount() * 40, c1.getCoordBounds().getWidth(), EPS);
      assertEquals(tile.getYUnitCount() * 30,
                   c0.getCoordBounds().getHeight() + c1.getCoordBounds().getHeight(), EPS);
   }

   @Test
   @DisplayName("horizontal layout agrees with the unit counts: total width and heights")
   void horizontalLayoutMatchesUnitCounts() {
      RectCoord c0 = rectCatY();
      RectCoord c1 = rectCatY();
      TileCoord tile = TileCoord.horizontal(c0, c1);
      tile.layout(0, 0, 40, 30);

      assertEquals(tile.getXUnitCount() * 40,
                   c0.getCoordBounds().getWidth() + c1.getCoordBounds().getWidth(), EPS);
      assertEquals(tile.getYUnitCount() * 30, c0.getCoordBounds().getHeight(), EPS);
      assertEquals(tile.getYUnitCount() * 30, c1.getCoordBounds().getHeight(), EPS);
   }

   @Test
   @DisplayName("layout with a zero cell leaves only the axis sizes and no NaN")
   void layoutZeroCell() {
      for(boolean vertical : new boolean[] { true, false }) {
         SC[] cs = { new SC(), new SC(), new SC() };

         for(SC c : cs) {
            sizeAxes(c, 10, 20, 5, 7);
         }

         TileCoord tile = vertical ? TileCoord.vertical(cs) : TileCoord.horizontal(cs);
         tile.layout(0, 0, 0, 0);

         for(SC c : cs) {
            Rectangle2D b = c.getCoordBounds();
            assertEquals(30, b.getWidth(), EPS);
            assertEquals(12, b.getHeight(), EPS);
            assertFalse(Double.isNaN(b.getX()) || Double.isNaN(b.getY()));
         }
      }
   }

   @Test
   @DisplayName("layout sets each child graph's bounds to the child's coordinate bounds")
   void layoutSetsChildGraphBounds() {
      SC[] cs = { new SC(), new SC() };
      sizeAxes(cs[0], 10, 20, 5, 7);
      VGraph[] graphs = { new VGraph(cs[0]), new VGraph(cs[1]) };
      TileCoord.vertical(cs).layout(3, 4, 100, 50);

      for(int i = 0; i < 2; i++) {
         assertEquals(cs[i].getCoordBounds(), graphs[i].getBounds());
      }
   }

   // ---------------------------------------------------------------- facet chain

   private static List<Coordinate> allTileChildren(FacetCoord facet) {
      List<Coordinate> list = new ArrayList<>();

      for(TileCoord[] row : facet.getTileCoords()) {
         for(TileCoord tile : row) {
            list.addAll(Arrays.asList(tile.getCoordinates()));
         }
      }

      return list;
   }

   private static void assertInside(Rectangle2D outer, Rectangle2D inner) {
      assertTrue(inner.getX() >= outer.getX() - EPS, "left edge " + inner + " in " + outer);
      assertTrue(inner.getY() >= outer.getY() - EPS, "top edge " + inner + " in " + outer);
      assertTrue(inner.getMaxX() <= outer.getMaxX() + EPS, "right edge " + inner + " in " + outer);
      assertTrue(inner.getMaxY() <= outer.getMaxY() + EPS, "bottom edge " + inner + " in " + outer);
   }

   private static void assertDisjoint(List<Coordinate> coords) {
      for(int i = 0; i < coords.size(); i++) {
         for(int j = i + 1; j < coords.size(); j++) {
            Rectangle2D inter = coords.get(i).getCoordBounds()
               .createIntersection(coords.get(j).getCoordBounds());
            assertTrue(inter.getWidth() <= EPS || inter.getHeight() <= EPS,
                       "tiles " + i + " and " + j + " overlap: " + inter);
         }
      }
   }

   /** Facet of two measure panels, vertical or horizontal, as the chart generators build it. */
   private static FacetCoord plotMeasures(boolean vertical) {
      Object[][] rows = {
         { "Cat", "m1", "m2" }, { "A", 10.0, 100.0 }, { "B", 20.0, 200.0 }, { "C", 30.0, 300.0 }
      };
      CategoricalScale xs1 = new CategoricalScale("Cat");
      CategoricalScale xs2 = new CategoricalScale("Cat");
      LinearScale ys1 = new LinearScale("m1");
      LinearScale ys2 = new LinearScale("m2");
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("Cat", "m1"));
      graph.addElement(new IntervalElement("Cat", "m2"));
      graph.setScale("Cat", xs1);
      graph.setScale("m1", ys1);
      graph.setScale("m2", ys2);
      graph.setCoordinate(new FacetCoord(null, new Coordinate[] {
         new RectCoord(xs1, ys1), new RectCoord(xs2, ys2) }, vertical));
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 600, 400);
      return (FacetCoord) vg.getCoordinate();
   }

   @Test
   @DisplayName("chart with two measures: panels are stacked with the same x and width and no gap")
   void twoMeasuresStacked() {
      FacetCoord facet = plotMeasures(true);
      Coordinate[] inner = facet.getTileCoords()[0][0].getCoordinates();

      assertEquals(2, inner.length);
      Rectangle2D b0 = inner[0].getCoordBounds();
      Rectangle2D b1 = inner[1].getCoordBounds();
      assertEquals(b0.getX(), b1.getX(), EPS);
      assertEquals(b0.getWidth(), b1.getWidth(), EPS);
      assertEquals(b0.getY() + b0.getHeight(), b1.getY(), EPS);
      assertTrue(b0.getY() < b1.getY());
      Rectangle2D union = b0.createUnion(b1);
      assertInside(facet.getCoordBounds(), union);
   }

   @Test
   @DisplayName("horizontal facet of two measures: panels share y and height and touch")
   void twoMeasuresSideBySide() {
      FacetCoord facet = plotMeasures(false);
      Coordinate[] inner = facet.getTileCoords()[0][0].getCoordinates();

      assertEquals(2, inner.length);
      Rectangle2D b0 = inner[0].getCoordBounds();
      Rectangle2D b1 = inner[1].getCoordBounds();
      assertEquals(b0.getY(), b1.getY(), EPS);
      assertEquals(b0.getHeight(), b1.getHeight(), EPS);
      assertEquals(b0.getX() + b0.getWidth(), b1.getX(), EPS);
      assertTrue(b0.getX() < b1.getX());
      assertInside(facet.getCoordBounds(), b0.createUnion(b1));
   }

   private static FacetCoord plotRegions(int regions) {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Region", "Cat", "m" });

      for(int r = 1; r <= regions; r++) {
         rows.add(new Object[] { "R" + r, "A", 10.0 * r });
         rows.add(new Object[] { "R" + r, "B", 20.0 * r });
      }

      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("Region", "Cat", "m"));
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(
         new DefaultDataSet(rows.toArray(new Object[0][])), 0, 0, 600, 400);
      return (FacetCoord) vg.getCoordinate();
   }

   private static FacetCoord plotGrid() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "R1", "R2", "Cat", "m" });

      for(int r1 = 1; r1 <= 2; r1++) {
         for(int r2 = 1; r2 <= 2; r2++) {
            rows.add(new Object[] { "P" + r1, "Q" + r2, "A", 10.0 * r1 + r2 });
            rows.add(new Object[] { "P" + r1, "Q" + r2, "B", 20.0 * r1 + r2 });
         }
      }

      EGraph graph = new EGraph();
      IntervalElement elem = new IntervalElement();
      elem.addDim("R1");
      elem.addDim("R2");
      elem.addDim("Cat");
      elem.addVar("m");
      graph.addElement(elem);
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(
         new DefaultDataSet(rows.toArray(new Object[0][])), 0, 0, 600, 400);
      return (FacetCoord) vg.getCoordinate();
   }

   @Test
   @DisplayName("facet row of three: tiles do not overlap and stay inside the facet")
   void facetRowOfThree() {
      FacetCoord facet = plotRegions(3);
      List<Coordinate> tiles = allTileChildren(facet);

      assertEquals(3, tiles.size());
      assertDisjoint(tiles);

      for(Coordinate c : tiles) {
         assertInside(facet.getCoordBounds(), c.getCoordBounds());
      }
   }

   private static void assertEqualNetSizes(List<Coordinate> tiles) {
      Coordinate first = tiles.get(0);
      double w = netWidth(first);
      double h = netHeight(first);

      for(Coordinate c : tiles) {
         assertEquals(w, netWidth(c), 1, "width without axes");
         assertEquals(h, netHeight(c), 1, "height without axes");
      }
   }

   private static double netWidth(Coordinate c) {
      return c.getCoordBounds().getWidth() - c.getAxisSize(LEFT) - c.getAxisSize(RIGHT);
   }

   private static double netHeight(Coordinate c) {
      return c.getCoordBounds().getHeight() - c.getAxisSize(TOP) - c.getAxisSize(BOTTOM);
   }

   @Test
   @DisplayName("facet row of three: tiles have equal sizes once the axis sizes are removed")
   void facetRowOfThreeEqualSizes() {
      assertEqualNetSizes(allTileChildren(plotRegions(3)));
   }

   @Test
   @DisplayName("2x2 facet grid: disjoint tiles inside the facet, row 0 and column 0 first")
   void facetGridTwoByTwo() {
      FacetCoord facet = plotGrid();
      TileCoord[][] grid = facet.getTileCoords();
      assertEquals(2, grid.length);
      assertEquals(2, grid[0].length);

      List<Coordinate> tiles = allTileChildren(facet);
      assertEquals(4, tiles.size());
      assertDisjoint(tiles);

      for(Coordinate c : tiles) {
         assertInside(facet.getCoordBounds(), c.getCoordBounds());
      }

      Rectangle2D r0c0 = grid[0][0].getCoordinates()[0].getCoordBounds();
      Rectangle2D r1c0 = grid[1][0].getCoordinates()[0].getCoordBounds();
      Rectangle2D r0c1 = grid[0][1].getCoordinates()[0].getCoordBounds();
      assertTrue(r0c0.getY() < r1c0.getY(), "row 0 has the lowest y");
      assertTrue(r0c0.getX() < r0c1.getX(), "column 0 has the lowest x");
   }

   @Test
   @DisplayName("2x2 facet grid: tiles have equal sizes once the axis sizes are removed")
   void facetGridEqualSizes() {
      assertEqualNetSizes(allTileChildren(plotGrid()));
   }

   @Test
   @DisplayName("x boundaries of a rect tile: two lines over the facet height, or only the right "
      + "one when the left side is not wanted")
   void xBoundariesRectChain() {
      FacetCoord facet = plotRegions(3);
      TileCoord tile = facet.getTileCoords()[0][1];
      Rectangle2D box = facet.getCoordBounds();

      Line2D[] both = tile.getXBoundaries(box, box, true);
      assertEquals(2, both.length);

      for(Line2D line : both) {
         assertEquals(line.getX1(), line.getX2(), EPS, "vertical line");
         assertEquals(box.getY(), Math.min(line.getY1(), line.getY2()), EPS);
         assertEquals(box.getMaxY() - 1, Math.max(line.getY1(), line.getY2()), EPS);
      }

      assertTrue(both[0].getX1() < both[1].getX1());

      Line2D[] right = tile.getXBoundaries(box, box, false);
      assertEquals(1, right.length);
      assertEquals(both[1].getX1(), right[0].getX1(), EPS);
   }

   @Test
   @DisplayName("y boundaries of a rect tile: two lines over the facet width, or only the top one "
      + "when the bottom is not wanted")
   void yBoundariesRectChain() {
      FacetCoord facet = plotRegions(3);
      TileCoord tile = facet.getTileCoords()[0][1];
      Rectangle2D box = facet.getCoordBounds();

      Line2D[] both = tile.getYBoundaries(box, box, true);
      assertEquals(2, both.length);

      for(Line2D line : both) {
         assertEquals(line.getY1(), line.getY2(), EPS, "horizontal line");
         assertEquals(box.getX(), Math.min(line.getX1(), line.getX2()), EPS);
         assertEquals(box.getMaxX(), Math.max(line.getX1(), line.getX2()), EPS);
      }

      Line2D[] top = tile.getYBoundaries(box, box, false);
      assertEquals(1, top.length);
      assertEquals(both[1].getY1(), top[0].getY1(), EPS, "top line is the second line");
   }

   // ---------------------------------------------------------------- boundaries, non-rect

   private static SC scWithBounds(double x, double y, double w, double h) {
      SC sc = new SC();
      new VGraph(sc).setBounds(x, y, w, h);
      return sc;
   }

   private static void assertLine(double x1, double y1, double x2, double y2, Line2D line) {
      assertEquals(x1, line.getX1(), EPS, "x1");
      assertEquals(y1, line.getY1(), EPS, "y1");
      assertEquals(x2, line.getX2(), EPS, "x2");
      assertEquals(y2, line.getY2(), EPS, "y2");
   }

   private static final Rectangle2D TBOX = new Rectangle2D.Double(10, 20, 100, 50);
   private static final Rectangle2D PBOX = new Rectangle2D.Double(0, 0, 300, 200);

   @Test
   @DisplayName("horizontal tile of a non-rect coordinate: x boundaries")
   void horizontalXBoundariesNonRect() {
      TileCoord tile = TileCoord.horizontal(scWithBounds(10, 20, 100, 50));
      Line2D[] withLeft = tile.getXBoundaries(TBOX, PBOX, true);
      assertEquals(2, withLeft.length);
      assertLine(10, 20, 10, 199, withLeft[0]);
      assertLine(110, 20, 110, 199, withLeft[1]);

      Line2D[] noLeft = tile.getXBoundaries(TBOX, PBOX, false);
      assertEquals(1, noLeft.length);
      assertLine(110, 20, 110, 199, noLeft[0]);
   }

   @Test
   @DisplayName("pinned behavior: vertical tile of a non-rect coordinate returns the left line "
      + "even without the left side (it repeats the previous column's right line)")
   void verticalXBoundariesNonRectWithoutLeftPinned() {
      TileCoord tile = TileCoord.vertical(scWithBounds(10, 20, 100, 50));
      Line2D[] noLeft = tile.getXBoundaries(TBOX, PBOX, false);
      assertEquals(2, noLeft.length);
      assertLine(10, 20, 10, 199, noLeft[0]);
      assertLine(110, 20, 110, 199, noLeft[1]);
   }

   @Test
   @DisplayName("horizontal tile of a non-rect coordinate: y boundaries")
   void horizontalYBoundariesNonRect() {
      TileCoord tile = TileCoord.horizontal(scWithBounds(10, 20, 100, 50));
      Line2D[] withBottom = tile.getYBoundaries(TBOX, PBOX, true);
      assertEquals(2, withBottom.length);
      assertLine(0, 20, 110, 20, withBottom[0]);
      assertLine(0, 70, 110, 70, withBottom[1]);

      Line2D[] noBottom = tile.getYBoundaries(TBOX, PBOX, false);
      assertEquals(1, noBottom.length);
      assertLine(0, 70, 110, 70, noBottom[0]);
   }

   @Test
   @DisplayName("vertical tile of two non-rect coordinates: y boundaries")
   void verticalYBoundariesNonRect() {
      SC c0 = scWithBounds(0, 0, 100, 50);
      SC c1 = scWithBounds(0, 50, 100, 50);
      c0.setCoordBounds(new Rectangle2D.Double(0, 0, 100, 50));
      c1.setCoordBounds(new Rectangle2D.Double(0, 50, 100, 50));
      TileCoord tile = TileCoord.vertical(c0, c1);
      Rectangle2D tbox = new Rectangle2D.Double(0, 0, 100, 100);

      Line2D[] withBottom = tile.getYBoundaries(tbox, PBOX, true);
      assertEquals(3, withBottom.length);
      assertLine(0, 0, 100, 0, withBottom[0]);
      assertLine(0, 50, 100, 50, withBottom[1]);
      assertLine(0, 100, 100, 100, withBottom[2]);

      Line2D[] noBottom = tile.getYBoundaries(tbox, PBOX, false);
      assertEquals(2, noBottom.length);
      assertLine(0, 50, 100, 50, noBottom[0]);
      assertLine(0, 100, 100, 100, noBottom[1]);
   }
}
