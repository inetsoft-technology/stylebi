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

import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.PolarAxis;
import inetsoft.graph.coord.PolarChartFixtures.Pie;
import inetsoft.graph.coord.PolarChartFixtures.Radar;
import inetsoft.graph.visual.ArcVOText;
import inetsoft.graph.visual.BarVO;
import inetsoft.graph.visual.Pie3DVO;
import inetsoft.graph.visual.VOText;
import inetsoft.graph.visual.VisualObject;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.report.internal.Common;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PolarUtil}: label-driven pie radius, tick angles, ellipse x, label sizes, fillCircle and
 * text on an arc.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class PolarUtilTest {
   private static final double EPS = 1e-6;
   private static final String SHORT = "A";
   private static final String LONG_LABEL =
      "A very long label text that does not fit next to a small pie at all, never ever";
   private static final String MEDIUM = "Revenue by region";

   // ---- getPieRadius

   @Test
   @DisplayName("without a label the pie radius is where the ray hits the plot edge")
   void pieRadiusWithoutLabel() {
      assertEquals(200, PolarUtil.getPieRadius(400, 300, null, 0), EPS);
      assertEquals(150, PolarUtil.getPieRadius(400, 300, null, Math.PI / 2), EPS);
      assertEquals(200 * Math.sqrt(2), PolarUtil.getPieRadius(400, 400, null, Math.PI / 4), EPS);
   }

   @ParameterizedTest(name = "t={0}")
   @ValueSource(doubles = { 0.3, 1.0 })
   @DisplayName("pie radius is symmetric in the four quadrants")
   void pieRadiusSymmetry(double t) {
      double base = PolarUtil.getPieRadius(400, 300, null, t);

      assertEquals(base, PolarUtil.getPieRadius(400, 300, null, -t), 1e-9);
      assertEquals(base, PolarUtil.getPieRadius(400, 300, null, Math.PI - t), 1e-9);
      assertEquals(base, PolarUtil.getPieRadius(400, 300, null, t + Math.PI), 1e-9);
   }

   @Test
   @DisplayName("a label reserves its own preferred width and height in the radius")
   void pieRadiusWithLabel() {
      VLabel label = new VLabel("Label");
      double angle = 0.5;
      double expected = Math.min((400 / 2.0 - label.getPreferredWidth()) / Math.abs(Math.cos(angle)),
                                 (300 / 2.0 - label.getPreferredHeight()) / Math.abs(Math.sin(angle)));

      assertEquals(expected, PolarUtil.getPieRadius(400, 300, label, angle), EPS);
      assertTrue(expected < PolarUtil.getPieRadius(400, 300, null, angle));
   }

   @ParameterizedTest(name = "angle={0}")
   @ValueSource(doubles = { 0.5, 1.2 })
   @DisplayName("a longer label never gives a larger radius")
   void pieRadiusLabelGrowth(double angle) {
      double shortR = PolarUtil.getPieRadius(400, 300, new VLabel(SHORT), angle);
      double longR = PolarUtil.getPieRadius(400, 300, new VLabel("AAAAAAAAAAAAAAAAAAAA"), angle);
      double none = PolarUtil.getPieRadius(400, 300, null, angle);

      assertTrue(longR <= shortR, longR + " > " + shortR);
      assertTrue(shortR <= none, shortR + " > " + none);
   }

   @Test
   @DisplayName("a label wider than half the plot gives a negative raw radius")
   void pieRadiusLabelWiderThanPlot() {
      VLabel label = new VLabel(LONG_LABEL);

      assertTrue(label.getPreferredWidth() > 50);
      assertTrue(PolarUtil.getPieRadius(100, 100, label, 0) < 0);
   }

   @Test
   @DisplayName("default radius is half the smaller side (the floor never exceeds it)")
   void defaultRadius() {
      assertEquals(150, PolarUtil.getDefaultRadius(400, 300), EPS);
      assertEquals(5, PolarUtil.getDefaultRadius(10, 10), EPS);
      assertEquals(500, PolarUtil.getDefaultRadius(1000, 1000), EPS);
      assertEquals(25, PolarUtil.getDefaultRadius(50, 2000), EPS);
      assertEquals(0, PolarUtil.getDefaultRadius(0, 0), EPS);
   }

   // ---- tick locations

   @Test
   @DisplayName("linear ticks are spread over the full circle")
   void tickLocationsLinear() {
      LinearScale scale = new LinearScale("m");

      scale.setMin(0);
      scale.setMax(100);
      scale.setIncrement(25);
      scale.init(new DefaultDataSet(new Object[][] { { "m" }, { 0.0 }, { 100.0 } }));

      double[] angles = PolarUtil.getTickLocations(scale, new double[] { 0, 25, 50, 75, 100 });

      assertArrayEquals(new double[] { 0, Math.PI / 2, Math.PI, 3 * Math.PI / 2, 2 * Math.PI },
                        angles, 1e-9);
   }

   @Test
   @DisplayName("categorical ticks are centred in their sector")
   void tickLocationsCategorical() {
      CategoricalScale scale = categorical("A", "B", "C", "D");
      double[] angles = PolarUtil.getTickLocations(scale, new double[] { 0.5, 1.5, 2.5, 3.5 });

      assertArrayEquals(new double[] { Math.PI / 4, 3 * Math.PI / 4, 5 * Math.PI / 4, 7 * Math.PI / 4 },
                        angles, 1e-9);
   }

   @Test
   @DisplayName("tick locations are computed in place: the returned array is the input array")
   void tickLocationsInPlace() {
      CategoricalScale scale = categorical("A", "B", "C", "D");
      double[] input = { 0.5, 1.5, 2.5, 3.5 };
      double[] result = PolarUtil.getTickLocations(scale, input);

      assertSame(input, result);
   }

   @Test
   @DisplayName("tick locations of a scale do not corrupt the scale's own ticks")
   void tickLocationsDoNotCorruptScale() {
      CategoricalScale scale = categorical("A", "B", "C", "D");
      double[] before = scale.getTicks().clone();

      PolarUtil.getTickLocations(scale, scale.getTicks());
      PolarUtil.getTickLocations(scale, scale.getTicks());

      assertArrayEquals(before, scale.getTicks(), 1e-12);
   }

   @Test
   @DisplayName("a scale without values has no tick locations")
   void tickLocationsEmpty() {
      CategoricalScale scale = categorical();

      assertEquals(0, PolarUtil.getTickLocations(scale, scale.getTicks()).length);
   }

   private static CategoricalScale categorical(String... values) {
      CategoricalScale scale = new CategoricalScale("Cat");
      Object[][] rows = new Object[values.length + 1][];

      rows[0] = new Object[] { "Cat" };

      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[] { values[i] };
      }

      scale.init(new DefaultDataSet(rows));
      return scale;
   }

   // ---- calcXonEllipse

   @Test
   @DisplayName("x on the ellipse a=5, b=3 at several heights; the sign of y does not matter")
   void ellipseX() {
      assertEquals(5, PolarUtil.calcXonEllipse(5, 3, 0), EPS);
      assertEquals(0, PolarUtil.calcXonEllipse(5, 3, 3), EPS);
      assertEquals(0, PolarUtil.calcXonEllipse(5, 3, -3), EPS);
      assertEquals(5 * Math.sqrt(0.75), PolarUtil.calcXonEllipse(5, 3, 1.5), EPS);
      assertEquals(PolarUtil.calcXonEllipse(5, 3, 1.5), PolarUtil.calcXonEllipse(5, 3, -1.5), 0);
   }

   @ParameterizedTest(name = "r={0}")
   @ValueSource(doubles = { 1, 50, 400 })
   @DisplayName("on a circle x squared plus y squared is r squared")
   void ellipseOnCircle(double r) {
      Random random = new Random(5);

      for(int i = 0; i < 100; i++) {
         double y = (random.nextDouble() * 2 - 1) * r * 0.999999;
         double x = PolarUtil.calcXonEllipse(r, r, y);

         assertEquals(r * r, x * x + y * y, EPS);
      }
   }

   @Test
   @DisplayName("pinned: a y beyond the ellipse height gives NaN")
   void ellipseOutsideIsNaN() {
      assertTrue(Double.isNaN(PolarUtil.calcXonEllipse(5, 3, 4)));
   }

   // ---- label sizes

   @ParameterizedTest(name = "moveable={0}")
   @ValueSource(booleans = { false, true })
   @DisplayName("pie label width stays within its bounds, shrinks with the radius and with the angle")
   void pieLabelWidthBounds(boolean moveable) {
      VLabel label = new VLabel(MEDIUM);
      double min = label.getMinWidth();
      double pref = label.getPreferredWidth();

      for(double angle : new double[] { 0, 0.8, Math.PI / 2 }) {
         double last = Double.MAX_VALUE;

         for(double r : new double[] { 50, 100, 150 }) {
            double w = PolarUtil.getPieLabelWidth(400, r, angle, label, moveable);

            assertTrue(w >= min - EPS && w <= Math.max(min, pref) + EPS, "width " + w);
            assertTrue(w <= last + EPS, "not non-increasing at r=" + r);
            last = w;
         }
      }

      for(double r : new double[] { 50, 100, 150 }) {
         assertTrue(PolarUtil.getPieLabelWidth(400, r, 0, label, moveable) <=
                    PolarUtil.getPieLabelWidth(400, r, Math.PI / 2, label, moveable) + EPS);
      }
   }

   @Test
   @DisplayName("a moveable label may use half the plot width minus its height")
   void pieLabelWidthMoveable() {
      VLabel label = new VLabel(MEDIUM);
      double fixed = PolarUtil.getPieLabelWidth(400, 190, 0, label, false);
      double moving = PolarUtil.getPieLabelWidth(400, 190, 0, label, true);
      double expected = Math.max(label.getMinWidth(),
                                 Math.min(label.getPreferredWidth(), 200 - label.getPreferredHeight()));

      assertTrue(moving >= fixed);
      assertEquals(expected, moving, EPS);
   }

   @Test
   @DisplayName("pie label height stays within its bounds, shrinks with the radius and with the angle")
   void pieLabelHeightBounds() {
      VLabel label = new VLabel(MEDIUM);
      double min = label.getMinHeight();
      double pref = label.getPreferredHeight();

      for(double angle : new double[] { 0, 0.8, Math.PI / 2 }) {
         double last = Double.MAX_VALUE;

         for(double r : new double[] { 50, 100, 150 }) {
            double h = PolarUtil.getPieLabelHeight(300, r, angle, label);

            assertTrue(h >= min - EPS && h <= Math.max(min, pref) + EPS, "height " + h);
            assertTrue(h <= last + EPS, "not non-increasing at r=" + r);
            last = h;
         }
      }

      for(double r : new double[] { 50, 100, 150 }) {
         assertTrue(PolarUtil.getPieLabelHeight(300, r, Math.PI / 2, label) <=
                    PolarUtil.getPieLabelHeight(300, r, 0, label) + EPS);
      }
   }

   @ParameterizedTest(name = "r={0}")
   @ValueSource(doubles = { 50, 100, 150 })
   @DisplayName("3D pie label sizes equal the 2D sizes at angle 0")
   void pie3dLabelSizeAtAngleZero(double r) {
      VLabel label = new VLabel(MEDIUM);

      assertEquals(PolarUtil.getPieLabelWidth(400, r, 0, label, false),
                   PolarUtil.get3DPieLabelWidth(400, r, 0, label, false), EPS);
      assertEquals(PolarUtil.getPieLabelHeight(300, r, 0, label),
                   PolarUtil.get3DPieLabelHeight(300, r, 0, label), EPS);
   }

   @ParameterizedTest(name = "r={0}")
   @ValueSource(doubles = { 50, 100, 150 })
   @DisplayName("3D pie label sizes at pi/2 equal the 2D sizes at half the radius")
   void pie3dLabelSizeAtQuarterTurn(double r) {
      VLabel label = new VLabel(MEDIUM);

      assertEquals(PolarUtil.getPieLabelWidth(400, r / 2, Math.PI / 2, label, false),
                   PolarUtil.get3DPieLabelWidth(400, r, Math.PI / 2, label, false), EPS);
      assertEquals(PolarUtil.getPieLabelHeight(300, r / 2, Math.PI / 2, label),
                   PolarUtil.get3DPieLabelHeight(300, r, Math.PI / 2, label), EPS);
   }

   @Test
   @DisplayName("3D pie label width at pi/4 equals the 2D width at radius / sqrt(2.5) when the plot binds")
   void pie3dLabelWidthBindsAtEighthTurn() {
      VLabel label = new VLabel(MEDIUM);
      double w = 400;
      double ratio = Math.sqrt(0.5 + 4 * 0.5);
      double target = (label.getMinWidth() + label.getPreferredWidth()) / 2;
      // choose r so that the available width w/2 - cos(pi/4) * r / ratio lies between min and preferred
      double r = (w / 2 - target) * ratio / Math.cos(Math.PI / 4);
      double expected = PolarUtil.getPieLabelWidth(w, r / ratio, Math.PI / 4, label, false);

      assertTrue(expected > label.getMinWidth() && expected < label.getPreferredWidth(),
                 "fixture does not bind: " + expected);
      assertEquals(expected, PolarUtil.get3DPieLabelWidth(w, r, Math.PI / 4, label, false), EPS);
   }

   @Test
   @DisplayName("3D pie label height where the ratio binds: the preferred height, unlike the 2D height at the same radius")
   void pie3dLabelHeightRatioBinds() {
      inetsoft.graph.TextSpec spec = new inetsoft.graph.TextSpec();

      spec.setFont(new Font("Dialog", Font.PLAIN, 8));

      VLabel label = new VLabel("A" + (char) 10 + "B", spec);
      double h2d = PolarUtil.getPieLabelHeight(300, 250, Math.PI / 2, label);
      double h3d = PolarUtil.get3DPieLabelHeight(300, 250, Math.PI / 2, label);

      assertTrue(label.getMinHeight() < label.getPreferredHeight());
      assertTrue(label.getPreferredHeight() <= 25, "preferred " + label.getPreferredHeight());
      assertEquals(PolarUtil.getPieLabelHeight(300, 125, Math.PI / 2, label), h3d, EPS);
      assertEquals(label.getPreferredHeight(), h3d, EPS);
      assertNotEquals(h2d, h3d, EPS);
   }

   // ---- fillCircle

   @ParameterizedTest(name = "text=\"{0}\"")
   @ValueSource(strings = { "A", "Revenue", "" })
   @DisplayName("fillCircle keeps the size within [start, max], leaves radius 0 alone and grows with radius")
   void fillCircleBounds(String text) {
      Font font = new Font("Dialog", Font.PLAIN, 10);
      int last = 10;

      assertEquals(10, PolarUtil.fillCircle(0, text, font, 40).getSize());

      for(double r : new double[] { 0, 5, 50, 500 }) {
         int size = PolarUtil.fillCircle(r, text, font, 40).getSize();

         assertTrue(size >= 10 && size <= 40, "size " + size + " at radius " + r);
         assertTrue(size >= last, "size decreased at radius " + r);
         last = size;
      }

      assertEquals(40, PolarUtil.fillCircle(500, text.isEmpty() ? "A" : text, font, 40).getSize());
   }

   @Test
   @DisplayName("fillCircle returns the smallest font whose text height fits the circle")
   void fillCircleIsMinimalFit() {
      Font font = new Font("Dialog", Font.PLAIN, 10);
      double r = 60;
      String text = "Revenue";
      Font result = PolarUtil.fillCircle(r, text, font, 100);
      int size = result.getSize();

      assertTrue(fitHeight(r, text, result) <= Common.getHeight(result) + 2, "result does not fit");

      Assumptions.assumeTrue(size > 10 && size < 100,
                             "font metrics put the result on the search boundary: " + size);

      Font smaller = result.deriveFont(result.getSize2D() - 1f);

      assertTrue(fitHeight(r, text, smaller) > Common.getHeight(smaller) + 2,
                 "size " + (size - 1) + " would fit as well");
   }

   // text height filling a circle of radius r: 2 r fh / sqrt(fw^2 + fh^2)
   private static double fitHeight(double r, String text, Font font) {
      double fw = Common.stringWidth(text, font);
      double fh = Common.getHeight(font);

      return 2 * r * fh / Math.sqrt(fw * fw + fh * fh);
   }

   // ---- label-driven radius on real charts

   private static final String LONG_PREFIX = "L".repeat(60);

   @ParameterizedTest(name = "{0}x{1}")
   @CsvSource({ "600,400", "400,600", "100,100", "10,10" })
   @DisplayName("without labels the text pie radius is half the smaller side")
   void textPieRadiusWithoutLabels(int w, int h) {
      Pie pie = new Pie().plot();

      assertEquals(Math.min(w, h) / 2.0, PolarUtil.getTextPieRadius(w, h, pie.vg), EPS);
   }

   @Test
   @DisplayName("long labels at 400x300: the radius is the tightest laid-out label's radius, at least the floor 20")
   void textPieRadiusLongLabels() {
      Pie pie = new Pie().labels(LONG_PREFIX).plot();
      double expected = 200;

      for(BarVO bar : pie.bars()) {
         assertInstanceOf(ArcVOText.class, bar.getVOText());

         Arc2D arc = (Arc2D) bar.getShape();
         double angle = Math.toRadians(arc.getAngleStart() + arc.getAngleExtent() / 2);

         expected = Math.min(expected, PolarUtil.getPieRadius(400, 300, bar.getVOText(), angle));
      }

      expected = Math.max(expected, 20);

      assertEquals(expected, PolarUtil.getTextPieRadius(400, 300, pie.vg), EPS);
   }

   @Test
   @DisplayName("long labels drive the text pie radius at 40x40 to the floor of 10")
   void textPieRadiusFloorSmallPlot() {
      Pie pie = new Pie().labels(LONG_PREFIX).plot();

      assertEquals(10, PolarUtil.getTextPieRadius(40, 40, pie.vg), EPS);
   }

   @Test
   @DisplayName("short labels shrink the text pie radius between the floor and the default radius")
   void textPieRadiusLabelEffect() {
      double none = PolarUtil.getTextPieRadius(600, 400, new Pie().plot().vg);
      double shortLabels = PolarUtil.getTextPieRadius(600, 400, new Pie().labels("A").plot().vg);
      double longLabels = PolarUtil.getTextPieRadius(600, 400, new Pie().labels(LONG_PREFIX).plot().vg);

      assertTrue(shortLabels >= 20 && shortLabels <= 200, "short " + shortLabels);
      assertTrue(shortLabels <= none, "short " + shortLabels + " none " + none);
      assertTrue(shortLabels >= longLabels, "short " + shortLabels + " long " + longLabels);
   }

   // expected radius of a radar: tightest label, floor 20 (or a quarter of the side)
   private static double expectedLabelRadius(PolarAxis axis, double w, double h, double rotation) {
      double r = Math.min(w / 2, h / 2);
      VLabel[] labels = axis.getLabels();

      for(int j = 0; j < labels.length; j++) {
         double angle = (j + 0.5) * 2 * Math.PI / labels.length + rotation;

         r = Math.min(r, PolarUtil.getPieRadius(w, h, labels[j], angle));
      }

      return Math.max(r, Math.min(20, Math.min(w / 4, h / 4)));
   }

   private static PolarAxis polarAxis(PolarCoord coord) {
      for(Axis axis : coord.getAxes(false)) {
         if(axis instanceof PolarAxis) {
            return (PolarAxis) axis;
         }
      }

      return null;
   }

   @Test
   @DisplayName("radar label radius is the tightest label's radius, within half the side")
   void labelPieRadiusOfRadar() {
      PolarCoord coord = new Radar().plot().coord();
      double r = PolarUtil.getLabelPieRadius(300, 300, coord);

      assertEquals(expectedLabelRadius(polarAxis(coord), 300, 300, 0), r, EPS);
      assertTrue(r <= 150);
   }

   @ParameterizedTest(name = "n={0} rotate={1}")
   @CsvSource({ "3,0", "3,90", "3,180", "3,270", "3,360", "4,0", "4,90", "4,180", "4,270", "4,360" })
   @DisplayName("radar label radius follows the label angles shifted by the rotation")
   void labelPieRadiusWithRotation(int n, double rotate) {
      Radar radar = new Radar().n(n);

      radar.rotate = rotate;

      PolarCoord coord = radar.plot().coord();

      assertEquals(expectedLabelRadius(polarAxis(coord), 300, 300, Math.toRadians(rotate)),
                   PolarUtil.getLabelPieRadius(300, 300, coord), EPS);
   }

   @ParameterizedTest(name = "rotate={0}")
   @ValueSource(doubles = { 30, 90, 180, 270 })
   @DisplayName("radar with long measure names: label radius follows the rotated label angles")
   void labelPieRadiusWithRotationLongNames(double rotate) {
      Radar radar = new Radar().n(3);

      radar.namePrefix = "Quarterly_revenue_";
      radar.rotate = rotate;

      PolarCoord coord = radar.plot().coord();

      assertEquals(expectedLabelRadius(polarAxis(coord), 300, 300, Math.toRadians(rotate)),
                   PolarUtil.getLabelPieRadius(300, 300, coord), EPS);
   }

   private static double[] longNameRadii(double[] rotations) {
      double[] radius = new double[rotations.length];

      for(int k = 0; k < rotations.length; k++) {
         Radar radar = new Radar().n(3);

         radar.namePrefix = "A_measure_name_of_30_chars_";
         radar.rotate = rotations[k];

         PolarCoord coord = radar.plot().coord();

         radius[k] = PolarUtil.getLabelPieRadius(300, 300, coord);
         assertEquals(expectedLabelRadius(polarAxis(coord), 300, 300, Math.toRadians(rotations[k])),
                      radius[k], EPS);
      }

      return radius;
   }

   @Test
   @DisplayName("with 30 character names the label radius equals the formula at rotation 0 and 90")
   void labelPieRadiusLongNames() {
      longNameRadii(new double[] { 0, 90 });
   }

   @Test
   @DisplayName("rotate(360) gives the same label radius as no rotation")
   void labelPieRadiusFullTurn() {
      Radar plain = new Radar().n(4);
      Radar full = new Radar().n(4);

      full.rotate = 360;

      assertEquals(PolarUtil.getLabelPieRadius(300, 300, plain.plot().coord()),
                   PolarUtil.getLabelPieRadius(300, 300, full.plot().coord()), EPS);
   }

   @Test
   @DisplayName("without a polar axis the label radius is half the smaller side")
   void labelPieRadiusWithoutPolarAxis() {
      PolarCoord coord = new Pie().plot().coord();

      assertNull(polarAxis(coord));
      assertEquals(100, PolarUtil.getLabelPieRadius(300, 200, coord), EPS);
   }

   // ---- 3D pie

   @Test
   @DisplayName("3D pie radius at a label angle of -pi/2 gets the full ratio of 2")
   void text3dPieRadiusLabelAngle() {
      Pie pie = new Pie();

      pie.threeD = true;
      pie.labels = true;
      pie.cats = new String[] { "A", "B" };
      pie.values = new Double[] { 50.0, 50.0 };
      pie.plot();

      Pie3DVO vo = null;

      for(VisualObject v : GTool.getVOs(pie.vg)) {
         if(v instanceof Pie3DVO) {
            vo = (Pie3DVO) v;
         }
      }

      assertNotNull(vo);

      double ph = Double.MAX_VALUE;

      for(VOText text : vo.getVOTexts()) {
         assertInstanceOf(ArcVOText.class, text);
         ph = Math.min(ph, text.getPreferredHeight());
      }

      assertEquals(Math.min(50, 2 * (50 - ph)), PolarUtil.getText3DPieRadius(1000, 100, pie.vg), EPS);
   }

   @ParameterizedTest(name = "{0}x{1}")
   @CsvSource({ "600,400", "10,10" })
   @DisplayName("a 3D pie without labels has the default radius")
   void text3dPieRadiusWithoutLabels(int w, int h) {
      Pie pie = new Pie();

      pie.threeD = true;
      pie.plot();

      assertEquals(Math.min(w, h) / 2.0, PolarUtil.getText3DPieRadius(w, h, pie.vg), EPS);
      assertEquals(PolarUtil.getDefaultRadius(w, h), PolarUtil.getText3DPieRadius(w, h, pie.vg), EPS);
   }
}
