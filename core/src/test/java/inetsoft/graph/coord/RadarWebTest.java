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
import inetsoft.graph.GraphConstants;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.AreaElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.guide.axis.GridLine;
import inetsoft.graph.guide.axis.PolarAxis;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.VisualObject;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.graphics.SVGSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * The rings of a radar: even steps on unequal spokes, and polygons for a one-measure radar.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RadarWebTest {
   @Test
   void equalSpokesRingAtTheirTicks() {
      double[] max = { 100, 100, 100 };
      int plain = spokes(false, max, GraphConstants.THIN_LINE).get(0).getGridLineCount();
      List<DefaultAxis> spokes = spokes(true, max, GraphConstants.THIN_LINE);

      assertTrue(plain > 1, "premise: several ticks");

      // the layout drops the ring on the outer edge, which the polar border draws
      int drawn = spokes.get(0).getScale().getTicks().length - 1;

      for(DefaultAxis spoke : spokes) {
         assertEquals(plain, spoke.getGridLineCount());
         assertEquals(drawn, spoke.getGridLineCount());
      }

      List<Double> radii = radii(spokes);
      double outer = radii.get(radii.size() - 1);
      double[] ticks = spokes.get(0).getScale().getTicks();

      assertEquals(drawn, radii.size(), "one radius per tick: " + radii);

      for(int i = 0; i < drawn; i++) {
         assertEquals(ticks[i] / ticks[drawn - 1], radii.get(i) / outer, 0.001, "tick " + i);
      }
   }

   @Test
   void unequalSpokesGetQuarterRings() {
      List<DefaultAxis> spokes = spokes(true, new double[] { 100, 7, 2000 },
                                        GraphConstants.THIN_LINE);

      // four rings are made; the layout drops the one on the outer edge, which the border draws
      for(DefaultAxis spoke : spokes) {
         assertEquals(3, spoke.getGridLineCount());
      }

      List<Double> radii = radii(spokes);
      assertEquals(3, radii.size(), "one distinct radius per ring: " + radii);
      double[] expected = { 0.25, 0.5, 0.75 };

      for(int i = 0; i < 3; i++) {
         assertEquals(expected[i] / expected[2], radii.get(i) / radii.get(2), 0.001, "ring " + i);
      }

   }

   @Test
   void unequalSpokesWithoutTheFlagKeepTodaysRings() {
      List<DefaultAxis> spokes = spokes(false, new double[] { 100, 7, 2000 },
                                        GraphConstants.THIN_LINE);
      boolean notFour = false;

      for(DefaultAxis spoke : spokes) {
         assertEquals(spoke.getScale().getTicks().length - 1, spoke.getGridLineCount(),
                      "max " + spoke.getScale().getMax() + " follows its own ticks, less the outer edge");
         notFour |= spoke.getGridLineCount() != 3;
      }

      assertTrue(notFour, "not the even steps");
   }

   @Test
   void aOneMeasureRadarDrawsPolygons() {
      List<Shape> on = ringShapes(oneVar(true));
      List<Shape> off = ringShapes(oneVar(false));

      assertFalse(on.isEmpty(), "premise: ring segments");
      assertFalse(off.isEmpty(), "premise: ring segments");
      assertTrue(on.stream().noneMatch(RadarWebTest::curved), "polygon chords");
      assertTrue(off.stream().allMatch(RadarWebTest::curved), "arcs");
   }

   @Test
   void ringsDrawUnderTheSeries() {
      VGraph vgraph = vgraph(true, new double[] { 100, 7, 2000 }, GraphConstants.THIN_LINE);
      int rings = 0;

      for(DefaultAxis spoke : spokesOf(vgraph)) {
         for(int i = 0; i < spoke.getGridLineCount(); i++) {
            assertEquals(GDefaults.GRIDLINE_Z_INDEX, spoke.getGridLine(i).getZIndex());
            rings++;
         }
      }

      assertEquals(9, rings);
      assertFalse(GTool.getVOs(vgraph).isEmpty(), "premise: a series");

      for(VisualObject vo : GTool.getVOs(vgraph)) {
         assertTrue(vo.getZIndex() > GDefaults.GRIDLINE_Z_INDEX, "series z " + vo.getZIndex());
      }
   }

   @Test
   void classicRadarIsUnchanged() {
      List<DefaultAxis> spokes = spokes(false, new double[] { 100, 7, 2000 }, GraphConstants.NONE);

      assertEquals(3, spokes.size());

      for(DefaultAxis spoke : spokes) {
         assertEquals(0, spoke.getGridLineCount());
      }
   }

   @Test
   void modernQuarterRingsSitAtTheirFraction() {
      for(int[] size : new int[][] { { 400, 240 }, { 1200, 1200 } }) {
         VGraph vgraph = vgraph(true, new double[] { 100, 7, 2000 }, GraphConstants.THIN_LINE,
                                true, size[0], size[1]);
         double outer = outerRadius(vgraph);

         for(DefaultAxis spoke : spokesOf(vgraph)) {
            assertEquals(3, spoke.getGridLineCount());

            for(int k = 0; k < 3; k++) {
               assertEquals((k + 1) / 4.0 * outer, paintedRadius(spoke, k), 0.5,
                            size[0] + "x" + size[1] + " ring " + k);
            }
         }
      }
   }

   @Test
   void modernSharedTickRingsSitAtTheirValue() {
      for(int[] size : new int[][] { { 400, 240 }, { 1200, 1200 } }) {
         VGraph vgraph = vgraph(true, new double[] { 100, 100, 100 }, GraphConstants.THIN_LINE,
                                true, size[0], size[1]);
         double outer = outerRadius(vgraph);

         for(DefaultAxis spoke : spokesOf(vgraph)) {
            double[] ticks = spoke.getScale().getTicks();
            assertTrue(spoke.getGridLineCount() > 1, "premise: several rings");

            for(int k = 0; k < spoke.getGridLineCount(); k++) {
               assertEquals(ticks[k] / 100 * outer, paintedRadius(spoke, k), 0.5,
                            size[0] + "x" + size[1] + " tick " + ticks[k]);
            }
         }
      }
   }

   @Test
   void aTwoSpokeModernRadarRingsSitAtTheirFraction() {
      VGraph vgraph = vgraph(true, new double[] { 100, 2000 }, GraphConstants.THIN_LINE,
                             true, 400, 240);
      double outer = outerRadius(vgraph);

      for(DefaultAxis spoke : spokesOf(vgraph)) {
         assertEquals(3, spoke.getGridLineCount());

         for(int k = 0; k < 3; k++) {
            assertEquals((k + 1) / 4.0 * outer, paintedRadius(spoke, k), 0.5, "arc " + k);
         }
      }
   }

   @Test
   void aOneMeasureModernRadarRingsSitAtTheirRadius() {
      VGraph vgraph = oneVar(true, true, 400, 240);
      double outer = outerRadius(vgraph);
      List<DefaultAxis> spokes = spokesOf(vgraph);

      assertEquals(5, spokes.size(), "premise: a spoke per category");

      for(DefaultAxis spoke : spokes) {
         double[] ticks = spoke.getScale().getTicks();
         double max = ((LinearScale) spoke.getScale()).getMax();
         assertTrue(spoke.getGridLineCount() > 1, "premise: several rings");

         for(int k = 0; k < spoke.getGridLineCount(); k++) {
            assertEquals(ticks[k] / max * outer, paintedRadius(spoke, k), 0.5,
                         "tick " + ticks[k]);
         }
      }
   }

   @Test
   void aOneMeasureModernRadarHasAnOuterPolygonThroughItsSpokes() {
      VGraph vgraph = oneVar(true, true, 400, 240);
      double outer = outerRadius(vgraph);
      Point2D centre = centre(vgraph);
      List<GridLine> spokeLines = spokeWebLines(vgraph);

      assertEquals(5, spokeLines.size(), "one spoke web line per category");

      List<Point2D> ends = new ArrayList<>();

      for(GridLine line : spokeLines) {
         List<Point2D> pts = vertices(painted(line));
         Point2D end = pts.get(0).distance(centre) > pts.get(pts.size() - 1).distance(centre) ?
            pts.get(0) : pts.get(pts.size() - 1);
         assertEquals(outer, end.distance(centre), 0.5, "a spoke ends at the outer radius");
         ends.add(end);
      }

      List<Point2D> corners = vertices(painted(outerWebLine(vgraph)));
      // a closed path repeats its first vertex
      int n = corners.size();

      if(n > 1 && corners.get(0).distance(corners.get(n - 1)) < 0.5) {
         n--;
      }

      assertEquals(5, n, "one vertex per spoke");

      for(int i = 0; i < n; i++) {
         Point2D corner = corners.get(i);
         assertEquals(outer, corner.distance(centre), 0.5, "a corner at the outer radius");
         assertTrue(ends.stream().anyMatch(e -> e.distance(corner) <= 0.5),
                    "a corner is a spoke end");
      }
   }

   @Test
   void classicRingsLandOnTheDataRadius() {
      // Bug #82032 (main) fixed the grid rings being scaled to the plot size twice, for every
      // polar chart. The epic used to keep the classic rings at that doubly scaled radius
      // (outer^2 / 250 for ring 0) and exempt only the modern web; the merge of main into the
      // epic took main's fix for the classic rings too, so ring k is at (k + 1) / 4 of the
      // outer radius, where the data and the ticks are.
      VGraph vgraph = vgraph(true, new double[] { 100, 7, 2000 }, GraphConstants.THIN_LINE,
                             false, 400, 240);
      double outer = outerRadius(vgraph);

      for(DefaultAxis spoke : spokesOf(vgraph)) {
         for(int k = 0; k < 3; k++) {
            assertEquals((k + 1) / 4.0 * outer, paintedRadius(spoke, k), 0.5, "ring " + k);
         }
      }
   }

   @Test
   void modernWebLinesDrawUnderTheSeries() {
      VGraph vgraph = vgraph(true, new double[] { 100, 7, 2000 }, GraphConstants.THIN_LINE,
                             true, 400, 240);
      List<GridLine> web = webLines(vgraph);
      Axis[] axes = vgraph.getCoordinate().getAxes(false);

      assertEquals(4, web.size(), "three spokes and the outer ring");
      assertEquals(4, axes.length, "premise: three spokes and the outer ring axis");
      assertFalse(GTool.getVOs(vgraph).isEmpty(), "premise: a series");

      for(GridLine line : web) {
         assertEquals(GDefaults.GRIDLINE_Z_INDEX, line.getZIndex());

         for(VisualObject vo : GTool.getVOs(vgraph)) {
            assertTrue(vo.getZIndex() > line.getZIndex(), "series z " + vo.getZIndex());
         }
      }

      for(Axis axis : axes) {
         int z = axis instanceof PolarAxis ? GDefaults.AXIS_Z_INDEX : GDefaults.VO_Z_INDEX + 1;

         assertEquals(z, axis.getZIndex(), axis.toString());
         assertTrue(axis.isLineAsGrid(), axis.toString());
         assertTrue(axis.isLineVisible(), axis.toString());
      }
   }

   @Test
   void modernSpokesRunFromTheCentreToTheOuterRing() {
      VGraph vgraph = vgraph(true, new double[] { 100, 7, 2000 }, GraphConstants.THIN_LINE,
                             true, 400, 240);
      double outer = outerRadius(vgraph);
      Point2D c = centre(vgraph);
      List<GridLine> spokes = spokeWebLines(vgraph);
      List<Point2D> ring = vertices(painted(outerWebLine(vgraph)));

      assertEquals(3, spokes.size());
      assertEquals(3, ring.size(), "outer ring vertices " + ring);

      for(Point2D v : ring) {
         assertEquals(outer, v.distance(c), 0.5, "vertex " + v);
      }

      for(GridLine spoke : spokes) {
         List<Point2D> ends = vertices(painted(spoke));
         assertEquals(2, ends.size());
         Point2D end = ends.get(1);

         assertEquals(0, ends.get(0).distance(c), 0.5, "starts at the centre");
         assertEquals(outer, end.distance(c), 0.5, "ends on the outer ring");
         assertTrue(ring.stream().anyMatch(v -> v.distance(end) <= 0.5),
                    "end " + end + " on a vertex of " + ring);
      }
   }

   @Test
   void modernYGridNoneKeepsSpokesAndOuterRing() {
      VGraph vgraph = vgraph(true, new double[] { 100, 7, 2000 }, GraphConstants.NONE,
                             true, 400, 240);

      for(DefaultAxis spoke : spokesOf(vgraph)) {
         assertEquals(0, spoke.getGridLineCount(), "no inner rings");
      }

      assertEquals(3, spokeWebLines(vgraph).size());
      assertNotNull(outerWebLine(vgraph));
   }

   @Test
   void aTwoSpokeModernRadarHasACircularOuterRing() {
      VGraph vgraph = vgraph(true, new double[] { 100, 2000 }, GraphConstants.THIN_LINE,
                             true, 400, 240);
      double outer = outerRadius(vgraph);
      Point2D c = centre(vgraph);
      Shape ring = painted(outerWebLine(vgraph));
      Rectangle2D box = ring.getBounds2D();

      assertEquals(2, spokeWebLines(vgraph).size());
      assertTrue(curved(ring), "a circle, not a chord");
      assertEquals(outer, box.getWidth() / 2, 0.5, "x radius");
      assertEquals(outer, box.getHeight() / 2, 0.5, "y radius");
      assertEquals(c.getX(), box.getCenterX(), 0.5, "x centre");
      assertEquals(c.getY(), box.getCenterY(), 0.5, "y centre");
   }

   @Test
   void classicWebGridHasNoWebLines() {
      VGraph vgraph = vgraph(false, new double[] { 100, 7, 2000 }, GraphConstants.THIN_LINE,
                             false, 400, 240);
      PolarCoord polar = (PolarCoord) vgraph.getCoordinate();
      Axis[] axes = polar.getAxes(false);

      assertTrue(polar.isWebGrid(), "premise: a scripted web grid");
      assertTrue(polar.getWebLines().isEmpty());
      assertEquals(4, axes.length, "premise: three spokes and the outer ring axis");

      for(Axis axis : axes) {
         int z = axis instanceof PolarAxis ? GDefaults.AXIS_Z_INDEX : GDefaults.VO_Z_INDEX + 1;

         assertFalse(axis.isLineAsGrid(), axis.toString());
         assertEquals(z, axis.getZIndex(), axis.toString());
      }
   }

   @Test
   void modernAxesPaintTheirTicksButNotTheirLines() {
      VGraph vgraph = vgraph(true, new double[] { 100, 7, 2000 }, GraphConstants.THIN_LINE,
                             true, 400, 240);
      // a stub for Batik, which the core tests lack
      SVGSupport saved = SVGSupport._CACHE[0];
      SVGSupport._CACHE[0] = mock(SVGSupport.class);

      try {
         for(Axis axis : vgraph.getCoordinate().getAxes(false)) {
            int grid = inked(axis);
            axis.setLineAsGrid(false);
            int line = inked(axis);
            axis.setLineAsGrid(true);

            assertTrue(grid < line,
                       axis + ": " + grid + " pixels as a grid, " + line + " as a line");

            if(axis instanceof DefaultAxis) {
               assertTrue(grid > 0, axis + " ticks still paint");
            }
         }
      }
      finally {
         SVGSupport._CACHE[0] = saved;
      }
   }

   @Test
   void aHiddenAxisHidesItsWebLine() {
      // the first spoke axis is hidden before layout
      Function<Coordinate, PolarCoord> hiding = inner -> new PolarCoord(inner) {
         @Override
         public void createAxis(VGraph vgraph) {
            super.createAxis(vgraph);
            getAxes(false)[0].setZIndex(-1);
         }
      };
      VGraph vgraph = vgraph(true, new double[] { 100, 7, 2000 }, GraphConstants.THIN_LINE,
                             true, 400, 240, hiding);
      List<GridLine> web = webLines(vgraph);
      Axis hidden = vgraph.getCoordinate().getAxes(false)[0];

      assertEquals(4, web.size(), "three spokes and the outer ring");
      assertEquals(-1, hidden.getZIndex(), "premise: a hidden spoke axis");

      for(GridLine line : web) {
         int z = line.getAxis() == hidden ? -1 : GDefaults.GRIDLINE_Z_INDEX;
         assertEquals(z, line.getZIndex(), line.getAxis().toString());
      }
   }

   @Test
   void webLinesLeaveTheLabelLayoutAlone() {
      double[] max = { 100, 7, 2000 };
      List<Rectangle2D> modern =
         labelBounds(vgraph(true, max, GraphConstants.THIN_LINE, true, 400, 240));
      List<Rectangle2D> classic =
         labelBounds(vgraph(true, max, GraphConstants.THIN_LINE, false, 400, 240));

      assertFalse(modern.isEmpty(), "premise: labels");
      assertEquals(classic, modern);
   }

   private static List<GridLine> webLines(VGraph vgraph) {
      return ((PolarCoord) vgraph.getCoordinate()).getWebLines();
   }

   private static List<GridLine> spokeWebLines(VGraph vgraph) {
      return webLines(vgraph).stream().filter(l -> l.getAxis() instanceof DefaultAxis).toList();
   }

   // the outer ring's web line
   private static GridLine outerWebLine(VGraph vgraph) {
      List<GridLine> outer =
         webLines(vgraph).stream().filter(l -> l.getAxis() instanceof PolarAxis).toList();

      assertEquals(1, outer.size(), "one outer ring");
      return outer.get(0);
   }

   // a web line as painted
   private static Shape painted(GridLine line) {
      return line.getAxis().getGridLineTransform().createTransformedShape(line.getShape());
   }

   // the plot centre, from the outer ring axis's own layout
   private static Point2D centre(VGraph vgraph) {
      PolarAxis axis = polarAxis(vgraph);
      Point2D mid = new Point2D.Double(axis.getWidth() / 2, axis.getHeight() / 2);

      return axis.getScreenTransform().transform(mid, null);
   }

   // the end points of a shape's straight segments
   private static List<Point2D> vertices(Shape shape) {
      List<Point2D> points = new ArrayList<>();
      double[] pt = new double[6];

      for(PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
         int type = it.currentSegment(pt);

         if(type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
            points.add(new Point2D.Double(pt[0], pt[1]));
         }
      }

      return points;
   }

   // the pixels an axis line paints
   private static int inked(Axis axis) {
      BufferedImage img = new BufferedImage(400, 240, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      int n = 0;

      axis.getAxisLine().paint(g);
      g.dispose();

      for(int y = 0; y < img.getHeight(); y++) {
         for(int x = 0; x < img.getWidth(); x++) {
            n += (img.getRGB(x, y) >>> 24) != 0 ? 1 : 0;
         }
      }

      return n;
   }

   // every axis label's bounds, axis by axis
   private static List<Rectangle2D> labelBounds(VGraph vgraph) {
      List<Rectangle2D> bounds = new ArrayList<>();

      for(Axis axis : vgraph.getCoordinate().getAxes(false)) {
         for(VLabel label : axis.getLabels()) {
            bounds.add(label.getBounds());
         }
      }

      return bounds;
   }

   private static PolarAxis polarAxis(VGraph vgraph) {
      for(Axis axis : vgraph.getCoordinate().getAxes(false)) {
         if(axis instanceof PolarAxis polarAxis) {
            return polarAxis;
         }
      }

      throw new AssertionError("no polar axis");
   }

   // the outer ring radius after layout
   private static double outerRadius(VGraph vgraph) {
      return polarAxis(vgraph).getWidth() / 2;
   }

   // the painted distance from the centre to a ring of a spoke
   private static double paintedRadius(DefaultAxis spoke, int k) {
      AffineTransform trans = spoke.getGridLineTransform();
      Point2D c = trans.transform(new Point2D.Double(0, 0), null);
      Point2D p = start(trans.createTransformedShape(spoke.getGridLine(k).getShape()));

      return p.distance(c);
   }

   // the distinct ring radii in ascending order; the centre is the mean of the segment starts
   private static List<Double> radii(List<DefaultAxis> spokes) {
      List<Point2D> starts = new ArrayList<>();

      for(DefaultAxis spoke : spokes) {
         for(int i = 0; i < spoke.getGridLineCount(); i++) {
            starts.add(start(spoke.getGridLine(i).getShape()));
         }
      }

      double cx = 0;
      double cy = 0;

      for(Point2D p : starts) {
         cx += p.getX() / starts.size();
         cy += p.getY() / starts.size();
      }

      List<Double> radii = new ArrayList<>();

      for(Point2D p : starts) {
         double r = p.distance(cx, cy);
         boolean dup = false;

         for(double r0 : radii) {
            dup |= Math.abs(r0 - r) < 0.5;
         }

         if(!dup) {
            radii.add(r);
         }
      }

      radii.sort(null);
      return radii;
   }

   // the first point of a shape
   private static Point2D start(Shape shape) {
      double[] pt = new double[6];
      PathIterator it = shape.getPathIterator(null);
      it.currentSegment(pt);
      return new Point2D.Double(pt[0], pt[1]);
   }

   // whether a shape has a curved segment, as an arc does
   private static boolean curved(Shape shape) {
      double[] pt = new double[6];

      for(PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
         int type = it.currentSegment(pt);

         if(type == PathIterator.SEG_QUADTO || type == PathIterator.SEG_CUBICTO) {
            return true;
         }
      }

      return false;
   }

   private static List<Shape> ringShapes(VGraph vgraph) {
      List<Shape> shapes = new ArrayList<>();

      for(Axis axis : vgraph.getCoordinate().getAxes(false)) {
         for(int i = 0; i < axis.getGridLineCount(); i++) {
            shapes.add(axis.getGridLine(i).getShape());
         }
      }

      return shapes;
   }

   private static List<DefaultAxis> spokes(boolean even, double[] max, int gridStyle) {
      return spokesOf(vgraph(even, max, gridStyle));
   }

   // a 400x400 radar over one measure per max, each spoke from 0 to its max
   private static VGraph vgraph(boolean even, double[] max, int gridStyle) {
      return vgraph(even, max, gridStyle, false, 400, 400);
   }

   private static VGraph vgraph(boolean even, double[] max, int gridStyle, boolean modern,
                                int width, int height)
   {
      return vgraph(even, max, gridStyle, modern, width, height, PolarCoord::new);
   }

   private static VGraph vgraph(boolean even, double[] max, int gridStyle, boolean modern,
                                int width, int height, Function<Coordinate, PolarCoord> polarOf)
   {
      int n = max.length;
      String[] names = new String[n];
      Scale[] scales = new Scale[n];
      Object[] low = new Object[n];
      Object[] high = new Object[n];

      for(int i = 0; i < n; i++) {
         names[i] = "m" + i;
         LinearScale scale = new LinearScale(names[i]);
         scale.setMin(0);
         scale.setMax(max[i]);
         scale.getAxisSpec().setGridStyle(gridStyle);
         scales[i] = scale;
         low[i] = max[i] / 4;
         high[i] = max[i] / 2;
      }

      EGraph graph = new EGraph();
      AreaElement area = new AreaElement();

      for(String name : names) {
         area.addDim(name);
      }

      graph.addElement(area);
      ParallelCoord inner = new ParallelCoord(scales);
      inner.setEvenRings(even);
      // the outer ring line a generated radar shows
      inner.getAxisLabelScale().getAxisSpec().setLineVisible(true);
      PolarCoord polar = polarOf.apply(inner);
      polar.setWebGrid(true);
      polar.setModernWeb(modern);
      graph.setCoordinate(polar);
      Object[][] rows = { names, low, high };

      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, width,
                                                     height);
   }

   private static VGraph oneVar(boolean sides) {
      return oneVar(sides, false, 400, 400);
   }

   private static VGraph oneVar(boolean sides, boolean modern, int width, int height) {
      EGraph graph = new EGraph();
      graph.addElement(new AreaElement("cat", "m"));
      Object[][] rows = new Object[6][];
      rows[0] = new Object[] { "cat", "m" };

      for(int i = 1; i < 6; i++) {
         rows[i] = new Object[] { "c" + i, i * 10.0 };
      }

      DefaultDataSet data = new DefaultDataSet(rows);
      LinearScale scale = new LinearScale("m");
      scale.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
      OneVarParallelCoord inner = new OneVarParallelCoord("m", "cat", scale);
      inner.init(data);
      PolarCoord polar = new PolarCoord(inner);
      polar.setWebGrid(true);
      polar.setSidesFromSpokes(sides);
      polar.setModernWeb(modern);
      graph.setCoordinate(polar);

      return Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, width, height);
   }

   private static List<DefaultAxis> spokesOf(VGraph vgraph) {
      List<DefaultAxis> spokes = new ArrayList<>();

      for(Axis axis : vgraph.getCoordinate().getAxes(false)) {
         if(axis instanceof DefaultAxis a) {
            spokes.add(a);
         }
      }

      return spokes;
   }
}
