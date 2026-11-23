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

import inetsoft.graph.VGraph;
import inetsoft.graph.coord.PolarChartFixtures.Pie;
import inetsoft.graph.coord.PolarChartFixtures.Radar;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.guide.axis.PolarAxis;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.BarVO;
import inetsoft.graph.visual.LineVO;
import inetsoft.graph.visual.VisualObject;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Shape;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static inetsoft.graph.coord.PolarChartFixtures.extent;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PolarCoord} inside a real chart: pies (radius, centre, slices, labels), radial wedges and
 * radar charts (axes, angles, vertices), plotted through {@code Plotter.plotAndLayout}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class PolarCoordChartPathTest {
   private static final double EPS = 1e-6;
   private static final String LONG_PREFIX = "L".repeat(60);

   private static double minSide(PolarCoord coord) {
      Rectangle2D b = coord.getCoordBounds();
      return Math.min(b.getWidth(), b.getHeight());
   }

   /** direction of a slice's middle as seen on the screen, degrees in [0, 360). */
   private static double sliceDirection(BarVO vo, PolarCoord coord) {
      Shape shape = vo.getShape();
      Arc2D arc = (Arc2D) shape;
      double a = Math.toRadians(arc.getAngleStart() + arc.getAngleExtent() / 2);
      Point2D unit = new Point2D.Double(250 * Math.cos(a), -250 * Math.sin(a));
      Point2D screen = vo.getScreenTransform().transform(unit, null);
      Rectangle2D b = coord.getCoordBounds();

      return norm(Math.toDegrees(Math.atan2(screen.getY() - b.getCenterY(),
                                            screen.getX() - b.getCenterX())));
   }

   private static double norm(double deg) {
      double d = deg % 360;
      return d < 0 ? d + 360 : d;
   }

   private static double angleDiff(double a, double b) {
      double d = norm(a - b);
      return d > 180 ? d - 360 : d;
   }

   private static double axisAngle(DefaultAxis axis) {
      AffineTransform t = axis.getScreenTransform();

      return norm(Math.toDegrees(Math.atan2(t.getShearY(), t.getScaleX())));
   }

   private static Point2D axisStart(DefaultAxis axis) {
      AffineTransform t = axis.getScreenTransform();

      return new Point2D.Double(t.getTranslateX(), t.getTranslateY());
   }

   private static double axisScreenLength(DefaultAxis axis) {
      AffineTransform t = axis.getScreenTransform();

      return axis.getLength() * Math.hypot(t.getScaleX(), t.getShearY());
   }

   private static Rectangle2D screenBounds(BarVO vo) {
      return vo.getScreenTransform().createTransformedShape(vo.getShape()).getBounds2D();
   }

   // ---- pie radius

   @ParameterizedTest(name = "{0}x{1}")
   @CsvSource({ "600,400", "400,600", "300,300" })
   @DisplayName("a pie without labels has radius min(w,h)/2 - 2 of its coordinate bounds")
   void pieRadiusWithoutLabels(int w, int h) {
      Pie pie = new Pie();

      pie.width = w;
      pie.height = h;
      pie.plot();

      assertEquals(minSide(pie.coord()) / 2 - 2, pie.coord().getRadius(), 1e-9);
   }

   @ParameterizedTest(name = "ratio={0}")
   @ValueSource(doubles = { 0.25, 0.5, 0.9, 1.0, 2.0 })
   @DisplayName("a fixed pie ratio sets the radius, capped at the edge gap")
   void pieRadiusFixedRatio(double ratio) {
      Pie pie = new Pie();

      pie.pieRatio = ratio;
      pie.plot();

      double side = minSide(pie.coord());
      double expected = Math.min(side * ratio / 2 - 1, side / 2 - 2);

      assertEquals(expected, pie.coord().getRadius(), 1e-9);
   }

   @ParameterizedTest(name = "{0}x{1}")
   @ValueSource(strings = { "600x400", "40x40" })
   @DisplayName("a tiny pie ratio never gives a zero or negative radius")
   void pieRadiusLowerBound(String size) {
      Pie pie = new Pie();

      pie.width = Integer.parseInt(size.split("x")[0]);
      pie.height = Integer.parseInt(size.split("x")[1]);
      pie.pieRatio = 0.001;
      pie.plot();

      double r = pie.coord().getRadius();

      assertTrue(r >= 1 && Double.isFinite(r), "radius " + r);
   }

   @Test
   @DisplayName("labels are movable only with a fixed pie ratio")
   void pieLabelMoveable() {
      assertFalse(new Pie().plot().coord().isLabelMoveable());

      Pie fixed = new Pie();

      fixed.pieRatio = 0.5;

      assertTrue(fixed.plot().coord().isLabelMoveable());
   }

   // ---- pie slices

   @Test
   @DisplayName("slices of 10, 30, 60 span 36, 108 and 216 degrees and sum to 360")
   void pieSlicesProportional() {
      List<BarVO> bars = new Pie().plot().bars();
      List<Double> extents = new ArrayList<>();
      double sum = 0;

      for(BarVO bar : bars) {
         extents.add(extent(bar.getShape()));
         sum += extent(bar.getShape());
      }

      assertEquals(3, bars.size());
      java.util.Collections.sort(extents);
      assertEquals(36, extents.get(0), EPS);
      assertEquals(108, extents.get(1), EPS);
      assertEquals(216, extents.get(2), EPS);
      assertEquals(360, sum, EPS);
   }

   @Test
   @DisplayName("pie slices are contiguous: each starts where the previous ends")
   void pieSlicesContiguous() {
      List<BarVO> bars = new Pie().plot().bars();
      double[][] arcs = new double[bars.size()][];

      for(int i = 0; i < arcs.length; i++) {
         Arc2D a = (Arc2D) bars.get(i).getShape();

         arcs[i] = new double[] { norm(-a.getAngleStart()), Math.abs(a.getAngleExtent()) };
      }

      Arrays.sort(arcs, (x, y) -> Double.compare(x[0], y[0]));

      for(int i = 0; i < arcs.length; i++) {
         double[] next = arcs[(i + 1) % arcs.length];

         assertEquals(0, angleDiff(arcs[i][0] + arcs[i][1], next[0]), EPS, "gap after slice " + i);
      }
   }

   @Test
   @DisplayName("pie slices are centred on the coordinate bounds with diameter twice the radius")
   void pieCentredAndSized() {
      Pie pie = new Pie().plot();
      PolarCoord coord = pie.coord();
      Rectangle2D b = coord.getCoordBounds();

      for(BarVO bar : pie.bars()) {
         Arc2D arc = (Arc2D) bar.getShape();
         AffineTransform st = bar.getScreenTransform();
         Point2D centre = st.transform(new Point2D.Double(arc.getCenterX(), arc.getCenterY()), null);

         assertEquals(2 * coord.getRadius(), arc.getWidth() * st.getScaleX(), EPS);
         assertEquals(2 * coord.getRadius(), arc.getHeight() * st.getScaleY(), EPS);
         assertEquals(b.getCenterX(), centre.getX(), EPS);
         assertEquals(b.getCenterY(), centre.getY(), EPS);
      }
   }

   @ParameterizedTest(name = "{0}x{1}")
   @ValueSource(strings = { "600x400", "400x600", "300x300", "150x100" })
   @DisplayName("every pie slice stays inside the coordinate bounds")
   void pieInsideBounds(String size) {
      Pie pie = new Pie().labels("Category");

      pie.width = Integer.parseInt(size.split("x")[0]);
      pie.height = Integer.parseInt(size.split("x")[1]);
      pie.plot();

      Rectangle2D b = pie.coord().getCoordBounds();
      Rectangle2D padded = new Rectangle2D.Double(b.getX() - EPS, b.getY() - EPS,
                                                  b.getWidth() + 2 * EPS, b.getHeight() + 2 * EPS);

      for(BarVO bar : pie.bars()) {
         assertTrue(padded.contains(screenBounds(bar)), screenBounds(bar) + " not in " + b);
      }
   }

   @Test
   @DisplayName("a single slice is a full ellipse of diameter twice the radius")
   void pieSingleSlice() {
      Pie pie = new Pie();

      pie.cats = new String[] { "A" };
      pie.values = new Double[] { 100.0 };
      pie.plot();

      assertEquals(1, pie.bars().size());
      assertInstanceOf(Ellipse2D.class, pie.bars().get(0).getShape());

      Ellipse2D e = (Ellipse2D) pie.bars().get(0).getShape();

      assertEquals(2 * pie.coord().getRadius(),
                   e.getWidth() * pie.bars().get(0).getScreenTransform().getScaleX(), EPS);
   }

   @Test
   @DisplayName("a zero value gives a zero-extent slice; a null value gives no slice")
   void pieZeroAndNullValues() {
      Pie zero = new Pie();

      zero.values = new Double[] { 0.0, 50.0, 50.0 };
      zero.plot();

      List<Double> extents = new ArrayList<>();

      for(BarVO bar : zero.bars()) {
         extents.add(extent(bar.getShape()));
         assertTrue(Double.isFinite(screenBounds(bar).getWidth()));
      }

      assertEquals(Arrays.asList(0.0, 180.0, 180.0), extents);

      Pie nul = new Pie();

      nul.values = new Double[] { null, 50.0, 50.0 };
      nul.plot();

      extents.clear();

      for(BarVO bar : nul.bars()) {
         extents.add(extent(bar.getShape()));
         assertTrue(Double.isFinite(screenBounds(bar).getWidth()));
      }

      assertEquals(Arrays.asList(180.0, 180.0), extents);
   }

   @Test
   @DisplayName("a pie of all zeros plots without exception and without NaN bounds (#80312)")
   void pieAllZero() {
      Pie pie = new Pie();

      pie.values = new Double[] { 0.0, 0.0, 0.0 };

      assertDoesNotThrow(pie::plot);

      for(BarVO bar : pie.bars()) {
         Rectangle2D b = screenBounds(bar);

         assertFalse(Double.isNaN(b.getX()) || Double.isNaN(b.getY()) ||
                     Double.isNaN(b.getWidth()) || Double.isNaN(b.getHeight()), b.toString());
      }
   }

   // ---- pie labels

   @Test
   @DisplayName("long labels apply the minimum ratio: radius is a quarter of the side minus one")
   void pieLongLabels() {
      Pie pie = new Pie().labels(LONG_PREFIX).plot();
      double side = minSide(pie.coord());

      assertEquals(side * 0.25 - 1, pie.coord().getRadius(), EPS);
      assertTrue(pie.coord().isLabelMoveable());
   }

   @Test
   @DisplayName("longer labels shrink the pie but never below the minimum ratio")
   void pieLabelsShrinkRadius() {
      Pie none = new Pie().plot();
      Pie shortLabels = new Pie().labels("A").plot();
      Pie longLabels = new Pie().labels(LONG_PREFIX).plot();
      double rNone = none.coord().getRadius();
      double rShort = shortLabels.coord().getRadius();
      double rLong = longLabels.coord().getRadius();

      assertTrue(rNone >= rShort, rNone + " < " + rShort);
      assertTrue(rShort >= rLong, rShort + " < " + rLong);
      assertTrue(rLong >= minSide(longLabels.coord()) * 0.25 - 1 - EPS);

      for(Pie pie : new Pie[] { none, shortLabels, longLabels }) {
         assertTrue(pie.coord().getRadius() <= minSide(pie.coord()) / 2 - 2 + EPS);
      }
   }

   @Test
   @DisplayName("rotate(90) turns every slice by the same quarter turn, rotate(360) changes nothing")
   void pieRotate() {
      Pie base = new Pie().plot();
      Pie quarter = new Pie();
      Pie full = new Pie();

      quarter.rotate = 90;
      full.rotate = 360;
      quarter.plot();
      full.plot();

      double first = Double.NaN;

      for(int i = 0; i < 3; i++) {
         double d0 = sliceDirection(base.bars().get(i), base.coord());
         double d90 = sliceDirection(quarter.bars().get(i), quarter.coord());
         double d360 = sliceDirection(full.bars().get(i), full.coord());
         double turn = norm(d90 - d0);

         assertTrue(Math.abs(turn - 90) < 1e-6 || Math.abs(turn - 270) < 1e-6, "turn " + turn);
         assertEquals(0, angleDiff(d360, d0), 1e-6);

         if(i == 0) {
            first = turn;
         }
         else {
            assertEquals(first, turn, 1e-6);
         }
      }

      assertEquals(base.coord().getRadius(), quarter.coord().getRadius(), EPS);
      assertEquals(base.coord().getCoordBounds().getCenterX(),
                   quarter.coord().getCoordBounds().getCenterX(), EPS);
   }

   @Test
   @DisplayName("reflect mirrors every slice about the horizontal or the vertical axis")
   void pieReflect() {
      Pie base = new Pie().plot();
      Pie vertical = new Pie();
      Pie horizontal = new Pie();

      vertical.reflectVertical = true;
      horizontal.reflectVertical = false;
      vertical.plot();
      horizontal.plot();

      for(int i = 0; i < 3; i++) {
         double d0 = sliceDirection(base.bars().get(i), base.coord());

         assertEquals(0, angleDiff(sliceDirection(vertical.bars().get(i), vertical.coord()), -d0),
                      1e-6, "reflect(true) slice " + i);
         assertEquals(0, angleDiff(sliceDirection(horizontal.bars().get(i), horizontal.coord()),
                                   180 - d0), 1e-6, "reflect(false) slice " + i);
         assertEquals(extent(base.bars().get(i).getShape()),
                      extent(vertical.bars().get(i).getShape()), EPS);
         assertEquals(extent(base.bars().get(i).getShape()),
                      extent(horizontal.bars().get(i).getShape()), EPS);
      }
   }

   // ---- radial wedges

   @Test
   @DisplayName("radial wedges: three 120 degree wedges with radii 40:80:60 within the pie radius")
   void radialWedges() {
      VGraph vg = PolarChartFixtures.wedges();
      PolarCoord coord = (PolarCoord) vg.getCoordinate();
      List<BarVO> bars = new ArrayList<>();

      for(VisualObject vo : GTool.getVOs(vg)) {
         bars.add((BarVO) vo);
      }

      assertEquals(3, bars.size());

      double[] widths = new double[3];

      for(int i = 0; i < 3; i++) {
         Arc2D arc = (Arc2D) bars.get(i).getShape();

         widths[i] = arc.getWidth();
         assertEquals(120, Math.abs(arc.getAngleExtent()), 1e-6, "wedge " + i);
         assertTrue(arc.getWidth() / 2 * bars.get(i).getScreenTransform().getScaleX()
                    <= coord.getRadius() + EPS);
      }

      assertEquals(80.0 / 40.0, widths[1] / widths[0], 1e-6);
      assertEquals(60.0 / 40.0, widths[2] / widths[0], 1e-6);
   }

   @Test
   @DisplayName("radial wedges: one polar axis labelled A B C of pie diameter and a radius axis")
   void radialWedgeAxes() {
      VGraph vg = PolarChartFixtures.wedges();
      PolarAxis polar = null;
      int radius = 0;

      for(int i = 0; i < vg.getAxisCount(); i++) {
         if(vg.getAxis(i) instanceof PolarAxis) {
            assertNull(polar, "more than one PolarAxis");
            polar = (PolarAxis) vg.getAxis(i);
         }
         else if(vg.getAxis(i) instanceof DefaultAxis) {
            radius++;
         }
      }

      assertNotNull(polar);
      assertTrue(radius >= 1);
      assertEquals(Arrays.asList("A", "B", "C"), labels(polar));
      assertEquals(((PolarCoord) vg.getCoordinate()).getRadius() * 2, polar.getWidth(), EPS);
   }

   private static List<Object> labels(PolarAxis axis) {
      List<Object> list = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         list.add(label.getValue());
      }

      return list;
   }

   // ---- radar axes

   @Test
   @DisplayName("a 3-measure radar has one axis per measure and a polar axis labelled with the measures")
   void radarAxes() {
      Radar radar = new Radar().plot();

      assertEquals(3, radar.measureAxes().size());
      assertNotNull(radar.polarAxis());
      assertEquals(Arrays.asList("m1", "m2", "m3"), labels(radar.polarAxis()));

      for(int i = 0; i < 3; i++) {
         assertEquals("m" + (i + 1), radar.measureAxes().get(i).getScale().getFields()[0]);
      }
   }

   @ParameterizedTest(name = "n={0}")
   @ValueSource(ints = { 2, 3, 4, 5, 6 })
   @DisplayName("radar axis i points at (i + 0.5) * 360 / n degrees, neighbours differ by 360 / n")
   void radarAxisAngles(int n) {
      Radar radar = new Radar().n(n).plot();
      List<DefaultAxis> axes = radar.measureAxes();

      assertEquals(n, axes.size());

      for(int i = 0; i < n; i++) {
         assertEquals(0, angleDiff(axisAngle(axes.get(i)), (i + 0.5) * 360.0 / n), 1e-6, "axis " + i);

         if(i > 0) {
            assertEquals(360.0 / n, norm(axisAngle(axes.get(i)) - axisAngle(axes.get(i - 1))), 1e-6);
         }
      }
   }

   @ParameterizedTest(name = "n={0}")
   @ValueSource(ints = { 3, 5 })
   @DisplayName("radar axes start at the centre and have equal length within the radius")
   void radarAxesMeetAtCentre(int n) {
      Radar radar = new Radar().n(n).plot();
      Rectangle2D b = radar.coord().getCoordBounds();
      double radius = radar.coord().getRadius();
      double length = axisScreenLength(radar.measureAxes().get(0));

      for(DefaultAxis axis : radar.measureAxes()) {
         Point2D start = axisStart(axis);

         assertEquals(b.getCenterX(), start.getX(), EPS);
         assertEquals(b.getCenterY(), start.getY(), EPS);
         assertEquals(length, axisScreenLength(axis), EPS);
         assertTrue(axisScreenLength(axis) <= radius + EPS);
      }
   }

   @Test
   @DisplayName("radar axes hide the minimum tick")
   void radarMinTick() {
      for(DefaultAxis axis : new Radar().plot().measureAxes()) {
         assertFalse(axis.isMinTickVisible());
      }
   }

   @ParameterizedTest(name = "axis style {0}: min tick visible {1}")
   @CsvSource({ "1, true", "3, false" })
   @DisplayName("a polar chart keeps the minimum tick with one radius axis and hides it with two")
   void radiusAxisCountDecidesMinTick(int style, boolean visible) {
      PolarChartFixtures.WedgeSpec spec = new PolarChartFixtures.WedgeSpec();

      spec.axisStyle = style;

      VGraph wedges = PolarChartFixtures.wedges(spec);
      int count = 0;

      for(int i = 0; i < wedges.getAxisCount(); i++) {
         if(wedges.getAxis(i) instanceof DefaultAxis) {
            count++;
            assertEquals(visible, ((DefaultAxis) wedges.getAxis(i)).isMinTickVisible());
         }
      }

      assertEquals(style == 1 ? 1 : 2, count);
   }

   @Test
   @DisplayName("radar rotate(90) shifts every axis by a quarter turn, rotate(360) changes nothing")
   void radarRotate() {
      Radar base = new Radar().plot();
      Radar quarter = new Radar();
      Radar full = new Radar();

      quarter.rotate = 90;
      full.rotate = 360;
      quarter.plot();
      full.plot();

      double first = Double.NaN;

      for(int i = 0; i < 3; i++) {
         double a0 = axisAngle(base.measureAxes().get(i));
         double turn = norm(axisAngle(quarter.measureAxes().get(i)) - a0);

         assertTrue(Math.abs(turn - 90) < 1e-6 || Math.abs(turn - 270) < 1e-6, "turn " + turn);
         assertEquals(0, angleDiff(axisAngle(full.measureAxes().get(i)), a0), 1e-6);
         assertEquals(axisStart(base.measureAxes().get(i)).getX(),
                      axisStart(quarter.measureAxes().get(i)).getX(), EPS);
         assertEquals(axisScreenLength(quarter.measureAxes().get(0)),
                      axisScreenLength(quarter.measureAxes().get(i)), EPS);
         assertTrue(axisScreenLength(quarter.measureAxes().get(i)) <= quarter.coord().getRadius() + EPS);
         assertEquals(axisScreenLength(base.measureAxes().get(i)),
                      axisScreenLength(full.measureAxes().get(i)), EPS);

         if(i == 0) {
            first = turn;
         }
         else {
            assertEquals(first, turn, 1e-6);
         }
      }
   }

   @Test
   @DisplayName("a 2-measure radar plots two vertices per row on axes at 90 and 270 degrees")
   void radarTwoMeasures() {
      Radar radar = new Radar().n(2);

      assertDoesNotThrow(radar::plot);
      assertEquals(2, radar.measureAxes().size());
      assertEquals(90, axisAngle(radar.measureAxes().get(0)), 1e-6);
      assertEquals(270, axisAngle(radar.measureAxes().get(1)), 1e-6);

      for(LineVO line : radar.lines()) {
         assertEquals(2, line.getPoints().length);
      }
   }

   @Test
   @DisplayName("a filled radar has the same axes as the line radar and three vertices per row")
   void filledRadar() {
      Radar line = new Radar().plot();
      Radar area = new Radar();

      area.area = true;

      assertDoesNotThrow(area::plot);
      assertEquals(line.measureAxes().size(), area.measureAxes().size());
      assertEquals(labels(line.polarAxis()), labels(area.polarAxis()));
      assertFalse(area.lines().isEmpty());

      for(LineVO vo : area.lines()) {
         assertEquals(3, vo.getPoints().length);
      }
   }

   @Test
   @DisplayName("a radar vertex of half the scale lies on its axis at half the axis length")
   void radarVertexOnAxis() {
      Radar radar = new Radar();

      radar.fixedScale = true;
      radar.plot();

      assertEquals(1, radar.lines().size());

      LineVO line = radar.lines().get(0);
      Point2D[] pts = line.getPoints();
      Rectangle2D b = radar.coord().getCoordBounds();

      for(int i = 0; i < 3; i++) {
         Point2D screen = line.getScreenTransform().transform(pts[i], null);
         DefaultAxis axis = radar.measureAxes().get(i);
         double dx = screen.getX() - b.getCenterX();
         double dy = screen.getY() - b.getCenterY();

         assertEquals(0.5 * axisScreenLength(axis), Math.hypot(dx, dy), 1e-3, "distance of vertex " + i);
         assertEquals(0, angleDiff(Math.toDegrees(Math.atan2(dy, dx)), axisAngle(axis)), 1e-3,
                      "direction of vertex " + i);
      }
   }

   @ParameterizedTest(name = "{0}x{1}")
   @ValueSource(strings = { "600x400", "300x300", "150x100" })
   @DisplayName("radar radius stays between 1 and the coordinate bounds")
   void radarRadiusWithinBounds(String size) {
      Radar radar = new Radar();

      radar.width = Integer.parseInt(size.split("x")[0]);
      radar.height = Integer.parseInt(size.split("x")[1]);
      radar.plot();

      double r = radar.coord().getRadius();

      assertTrue(r >= 1, "radius " + r);
      assertTrue(r <= minSide(radar.coord()) / 2 - 2 + EPS, "radius " + r);
   }

   @Test
   @DisplayName("longer measure names do not give a larger radar radius")
   void radarLabelsShrinkRadius() {
      Radar shortNames = new Radar().plot();
      Radar longNames = new Radar();

      longNames.namePrefix = "A_measure_name_of_30_chars_";
      longNames.plot();

      assertTrue(longNames.coord().getRadius() <= shortNames.coord().getRadius() + EPS);

      for(Radar r : new Radar[] { shortNames, longNames }) {
         assertTrue(r.coord().getRadius() <= minSide(r.coord()) / 2 - 2 + EPS);
      }
   }

   @Test
   @DisplayName("hiding the radar labels gives the full radius of the bounds")
   void radarLabelsHidden() {
      Radar visible = new Radar().plot();
      Radar hidden = new Radar();

      hidden.labelVisible = false;
      hidden.plot();

      assertTrue(hidden.coord().getRadius() >= visible.coord().getRadius() - EPS);
      assertEquals(minSide(hidden.coord()) / 2 - 2, hidden.coord().getRadius(), EPS);
   }

   @Test
   @DisplayName("a fixed pie ratio on a radar takes priority over the labels")
   void radarFixedPieRatio() {
      Radar radar = new Radar();

      radar.pieRatio = 0.5;
      radar.plot();

      assertEquals(minSide(radar.coord()) * 0.25 - 1, radar.coord().getRadius(), EPS);
   }

   @Test
   @DisplayName("a radar over an empty data set plots no axes and no visuals")
   void radarEmptyData() {
      Radar radar = new Radar();

      radar.emptyData = true;

      assertDoesNotThrow(radar::plot);
      assertEquals(0, radar.vg.getAxisCount());
      assertEquals(0, GTool.getVOs(radar.vg).size());
   }

   @Test
   @DisplayName("a radar row with a null measure plots; the other vertices stay on their axes (#75278)")
   void radarNullMeasure() {
      Radar radar = new Radar();

      radar.fixedScale = true;
      radar.nullableRows = new Double[][] { { 50.0, null, 50.0 } };

      assertDoesNotThrow(radar::plot);

      LineVO line = radar.lines().get(0);
      Rectangle2D b = radar.coord().getCoordBounds();

      for(int i : new int[] { 0, 2 }) {
         Point2D screen = line.getScreenTransform().transform(line.getPoints()[i], null);
         DefaultAxis axis = radar.measureAxes().get(i);
         double dx = screen.getX() - b.getCenterX();
         double dy = screen.getY() - b.getCenterY();

         assertEquals(0.5 * axisScreenLength(axis), Math.hypot(dx, dy), 1e-3, "vertex " + i);
         assertEquals(0, angleDiff(Math.toDegrees(Math.atan2(dy, dx)), axisAngle(axis)), 1e-3);
      }
   }

   @ParameterizedTest(name = "{0}x{1}")
   @ValueSource(strings = { "600x400", "300x300" })
   @DisplayName("radar polar axis labels lie inside the coordinate bounds")
   void radarLabelsInsideBounds(String size) {
      Radar radar = new Radar();

      radar.width = Integer.parseInt(size.split("x")[0]);
      radar.height = Integer.parseInt(size.split("x")[1]);
      radar.plot();

      Rectangle2D b = radar.coord().getCoordBounds();
      Rectangle2D padded = new Rectangle2D.Double(b.getX() - 1, b.getY() - 1,
                                                  b.getWidth() + 2, b.getHeight() + 2);

      for(VLabel label : radar.polarAxis().getLabels()) {
         assertTrue(padded.contains(label.getBounds()), label.getBounds() + " not in " + b);
      }
   }

   // ---- unit sizes

   @Test
   @DisplayName("a pie without labels has unit sizes 40 40 96 96")
   void unitSizesWithoutLabels() {
      PolarCoord coord = new Pie().plot().coord();

      assertEquals(40, coord.getUnitMinWidth(), EPS);
      assertEquals(40, coord.getUnitMinHeight(), EPS);
      assertEquals(96, coord.getUnitPreferredWidth(), EPS);
      assertEquals(96, coord.getUnitPreferredHeight(), EPS);
   }

   @Test
   @DisplayName("labels widen the pie's preferred unit size; a radar stays above the minimum sizes")
   void unitSizesWithLabels() {
      PolarCoord plain = new Pie().plot().coord();
      PolarCoord labelled = new Pie().labels(LONG_PREFIX).plot().coord();
      PolarCoord radar = new Radar().plot().coord();

      for(PolarCoord coord : new PolarCoord[] { labelled, radar }) {
         assertTrue(coord.getUnitMinWidth() >= 40);
         assertTrue(coord.getUnitPreferredWidth() >= 96);
         assertTrue(coord.getUnitPreferredWidth() >= coord.getUnitMinWidth());
      }

      assertTrue(labelled.getUnitPreferredWidth() > plain.getUnitPreferredWidth());
   }

   // ---- state

   @Test
   @DisplayName("each clone of an unplotted graph gets the radius of its own bounds")
   void radiusPerClonedGraph() {
      List<VGraph> graphs = new Pie().plotClones(new int[][] { { 600, 400 }, { 150, 100 } });

      for(VGraph vg : graphs) {
         PolarCoord coord = (PolarCoord) vg.getCoordinate();

         assertEquals(minSide(coord) / 2 - 2, coord.getRadius(), 1e-9);
      }

      assertNotEquals(((PolarCoord) graphs.get(0).getCoordinate()).getRadius(),
                      ((PolarCoord) graphs.get(1).getCoordinate()).getRadius());
   }

}
