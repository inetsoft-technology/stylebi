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
package inetsoft.graph.guide.axis;

import inetsoft.graph.*;
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.VMeasureTitle;
import inetsoft.graph.internal.DimensionD;
import inetsoft.graph.internal.PolarUtil;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Polar (circular) axis and its oval axis line: tick angles, labels, sizes, layout, painting of
 * the oval and ticks, and the chain through a polar coordinate.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class PolarAxisTest {
   private VGraph vg;
   private Graphics2D g;

   @BeforeEach
   void setUp() {
      vg = new VGraph(new RectCoord());
   }

   @AfterEach
   void tearDown() {
      if(g != null) {
         g.dispose();
         g = null;
      }
   }

   private static CategoricalScale cat(String... values) {
      CategoricalScale scale = new CategoricalScale("Cat");
      scale.init((Object[]) values);
      return scale;
   }

   private static LinearScale lin() {
      LinearScale scale = new LinearScale("m");
      scale.setMin(0);
      scale.setMax(100);
      scale.setIncrement(25);
      return scale;
   }

   private PolarAxis axis(Scale scale) {
      PolarAxis axis = new PolarAxis(scale, vg);
      axis.setLineColor(Color.BLACK);
      return axis;
   }

   /** Axis with its visuals reduced to the oval and ticks, sized and placed at (50, 50). */
   private PolarAxis drawable(Scale scale, double w, double h) {
      PolarAxis axis = axis(scale);
      // rebuilding the axis starts with a fresh line, so flags first and size last
      axis.setLabelVisible(false);
      axis.setWidth(w);
      axis.setHeight(h);
      return axis;
   }

   private BufferedImage paint(PolarAxis axis) {
      axis.setScreenTransform(AffineTransform.getTranslateInstance(50, 50));
      BufferedImage image = new BufferedImage(260, 260, BufferedImage.TYPE_INT_ARGB);
      g = image.createGraphics();
      axis.getAxisLine().paint(g);
      g.dispose();
      g = null;
      return image;
   }

   private static boolean painted(BufferedImage image, int x0, int x1, int y0, int y1) {
      for(int x = x0; x <= x1; x++) {
         for(int y = y0; y <= y1; y++) {
            if((image.getRGB(x, y) >>> 24) != 0) {
               return true;
            }
         }
      }

      return false;
   }

   private static void assertLabelValues(PolarAxis axis, Scale scale) {
      Object[] values = scale.getValues();
      VLabel[] labels = axis.getLabels();

      assertEquals(values.length, labels.length);

      for(int i = 0; i < values.length; i++) {
         assertEquals(values[i], labels[i].getValue(), "label " + i);
      }
   }

   @Test
   @DisplayName("tick angles of three categories are a third, a half and five thirds of the way round")
   void tickAngles() {
      CategoricalScale scale = cat("A", "B", "C");
      PolarAxis axis = axis(scale);

      double[] angles = PolarUtil.getTickLocations(axis.getScale(), axis.getScale().getTicks());

      assertArrayEquals(new double[] { Math.PI / 3, Math.PI, 5 * Math.PI / 3 }, angles, 1e-9);
   }

   @Test
   @DisplayName("labels carry the scale values in order, categorical and linear")
   void labelsFollowScale() {
      CategoricalScale c = cat("A", "B", "C");
      LinearScale l = lin();

      assertLabelValues(axis(c), c);
      assertLabelValues(axis(l), l);
   }

   @Test
   @DisplayName("min size covers the oval and min never exceeds preferred size")
   void minAndPreferredSize() {
      PolarAxis axis = axis(cat("A", "B", "C"));
      axis.setWidth(200);
      axis.setHeight(200);
      axis.setScreenTransform(new AffineTransform());

      assertTrue(axis.getMinWidth() >= 200, "min width " + axis.getMinWidth());
      assertTrue(axis.getMinHeight() >= 200, "min height " + axis.getMinHeight());
      assertTrue(axis.getMinWidth() <= axis.getPreferredWidth());
      assertTrue(axis.getMinHeight() <= axis.getPreferredHeight());

      // each label sits beyond its tick point on the outer side; the size is the box around the
      // oval (0, 0, 200, 200) and the outer corners of the labels
      double[] angles = { Math.PI / 3, Math.PI, 5 * Math.PI / 3 };

      for(boolean min : new boolean[] { true, false }) {
         Rectangle box = new Rectangle(0, 0, 200, 200);

         for(int i = 0; i < angles.length; i++) {
            VLabel label = axis.getLabels()[i];
            double x = 100 + 100 * Math.cos(angles[i]);
            double y = 100 + 100 * Math.sin(angles[i]);
            double w = min ? label.getMinWidth() : label.getPreferredWidth();
            double h = min ? label.getMinHeight() : label.getPreferredHeight();

            box.add((int) (x + (x < 100 ? -w : w)), (int) (y + (y < 100 ? -h : h)));
         }

         assertEquals(box.width, min ? axis.getMinWidth() : axis.getPreferredWidth(), 1e-9,
                      "width, min=" + min);
         assertEquals(box.height, min ? axis.getMinHeight() : axis.getPreferredHeight(), 1e-9,
                      "height, min=" + min);
      }
   }

   @Test
   @DisplayName("an axis with hidden labels has zero min and preferred size")
   void hiddenLabelsZeroSize() {
      PolarAxis axis = axis(cat("A", "B", "C"));
      axis.setLabelVisible(false);
      axis.setWidth(200);
      axis.setHeight(200);
      axis.setScreenTransform(new AffineTransform());

      assertEquals(0, axis.getMinWidth(), 0);
      assertEquals(0, axis.getMinHeight(), 0);
      assertEquals(0, axis.getPreferredWidth(), 0);
      assertEquals(0, axis.getPreferredHeight(), 0);
   }

   @Test
   @DisplayName("width and height are passed on to the axis line")
   void widthAndHeightReachAxisLine() {
      PolarAxis axis = axis(cat("A", "B", "C"));

      axis.setWidth(200);
      axis.setHeight(100);

      assertEquals(200, axis.getWidth(), 0);
      assertEquals(100, axis.getHeight(), 0);
      assertEquals(200, axis.getAxisLine().getMinWidth(), 0);
      assertEquals(100, axis.getAxisLine().getMinHeight(), 0);
   }

   private PolarAxis laidOut(Scale scale, boolean minTick, boolean maxTick) {
      PolarAxis axis = axis(scale);
      axis.setMinTickVisible(minTick);
      axis.setMaxTickVisible(maxTick);
      axis.setWidth(200);
      axis.setHeight(200);
      axis.setPlotSize(new DimensionD(400, 400));
      axis.setScreenTransform(AffineTransform.getTranslateInstance(100, 100));
      vg.setBounds(0, 0, 400, 400);
      axis.layout(new Rectangle2D.Double(0, 0, 400, 400));
      return axis;
   }

   @Test
   @DisplayName("layout hides the first and last label when the min and max ticks are hidden")
   void layoutHidesMinAndMaxLabels() {
      PolarAxis axis = laidOut(lin(), false, false);
      VLabel[] labels = axis.getLabels();

      assertEquals(5, labels.length);
      assertTrue(labels[0].getZIndex() < 0, "first label");
      assertTrue(labels[4].getZIndex() < 0, "last label");

      for(int i = 1; i < 4; i++) {
         assertTrue(labels[i].getZIndex() >= 0, "label " + i);
      }
   }

   @Test
   @DisplayName("layout puts every label farther from the center than the circle radius")
   void layoutLabelsOutsideCircle() {
      PolarAxis axis = laidOut(cat("A", "B", "C", "D"), true, true);
      VLabel[] labels = axis.getLabels();
      Point2D center = new Point2D.Double(200, 200);

      assertEquals(4, labels.length);

      for(int i = 0; i < labels.length; i++) {
         Rectangle2D bounds = labels[i].getBounds();
         Point2D c = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());

         assertTrue(c.distance(center) > 100, "label " + i + " center " + c);
      }
   }

   @Test
   @DisplayName("the oval is painted when the line is visible and not when it is hidden")
   void ovalPainted() {
      PolarAxis shown = drawable(cat("A", "B", "C"), 100, 100);
      shown.setTickVisible(false);
      shown.setWidth(100);
      shown.setHeight(100);
      BufferedImage image = paint(shown);

      assertTrue(painted(image, 99, 101, 49, 51), "top of the oval");
      assertTrue(painted(image, 99, 101, 149, 151), "bottom of the oval");
      assertFalse(painted(image, 98, 102, 98, 102), "center");

      PolarAxis hidden = drawable(cat("A", "B", "C"), 100, 100);
      hidden.setLineVisible(false);
      hidden.setTickVisible(false);
      hidden.setWidth(100);
      hidden.setHeight(100);

      assertFalse(painted(paint(hidden), 0, 259, 0, 259));
   }

   @Test
   @DisplayName("ticks point outward when tickDown is set and inward when it is not")
   void tickDirection() {
      for(boolean down : new boolean[] { true, false }) {
         PolarAxis axis = axis(lin());
         axis.setLabelVisible(false);
         axis.setLineVisible(false);
         axis.setTickDown(down);
         axis.setWidth(100);
         axis.setHeight(100);
         // the tick at value 25 is a quarter turn round: the bottom of the circle, (100, 150)
         BufferedImage image = paint(axis);

         assertEquals(down, painted(image, 99, 101, 152, 153), "outside, tickDown=" + down);
         assertEquals(!down, painted(image, 99, 101, 147, 148), "inside, tickDown=" + down);
      }
   }

   @Test
   @DisplayName("painting the axis with minor ticks leaves the graphics color and stroke unchanged")
   void paintRestoresGraphics() {
      PolarAxis axis = drawable(lin(), 100, 100);
      axis.setScreenTransform(AffineTransform.getTranslateInstance(50, 50));
      BufferedImage image = new BufferedImage(260, 260, BufferedImage.TYPE_INT_ARGB);
      g = image.createGraphics();
      Stroke stroke = new BasicStroke(3f);
      g.setColor(Color.BLUE);
      g.setStroke(stroke);

      axis.paint(g);

      assertEquals(Color.BLUE, g.getColor());
      assertEquals(stroke, g.getStroke());
   }

   @Test
   @DisplayName("axis line bounds start at the transformed origin and have the oval size")
   void lineBounds() {
      PolarAxis axis = axis(cat("A", "B", "C"));
      axis.setWidth(100);
      axis.setHeight(60);
      axis.setScreenTransform(AffineTransform.getTranslateInstance(10, 20));

      assertEquals(new Rectangle2D.Double(10, 20, 100, 60), axis.getAxisLine().getBounds());
   }

   @Test
   @DisplayName("a polar chart gets a polar axis with one label per category and a default axis for the radius")
   void polarChartChain() {
      Object[][] rows = { { "Cat", "m" }, { "A", 40.0 }, { "B", 80.0 }, { "C", 60.0 } };
      DefaultDataSet data = new DefaultDataSet(rows);
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("Cat", "m"));
      CategoricalScale xs = new CategoricalScale("Cat");
      LinearScale ys = new LinearScale("m");
      ys.setMin(0);
      ys.setMax(100);
      ys.setIncrement(25);
      graph.setScale("Cat", xs);
      graph.setScale("m", ys);
      graph.setCoordinate(new PolarCoord(new RectCoord(xs, ys)));

      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 600, 400);
      PolarAxis polar = null;
      boolean defaultAxis = false;

      for(int i = 0; i < vgraph.getAxisCount(); i++) {
         Axis a = vgraph.getAxis(i);

         if(a instanceof PolarAxis) {
            polar = (PolarAxis) a;
         }
         else if(a instanceof DefaultAxis) {
            defaultAxis = true;
         }
      }

      assertNotNull(polar, "no PolarAxis in the graph");
      assertEquals(3, polar.getLabels().length);
      assertTrue(defaultAxis, "no DefaultAxis for the radius");
   }

   @Test
   @DisplayName("pinned: getMaxLength returns the available width")
   void pinnedMaxLength() {
      assertEquals(300, axis(cat("A", "B", "C")).getMaxLength(300), 0);
   }
}
