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

import inetsoft.graph.GraphConstants;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.graphics.SVGSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The pseudo 3D axis: z index, grid lines shifted by the depth, tick positions compared with
 * DefaultAxis, and the painted fill between the axis and its depth offset.
 *
 * Fixture: A1 is a horizontal axis of length 1000 (identity), A2 the same shifted by
 * translate(0, 1000); lines are created by A2.createGridLines(A1).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class Axis2DTest {
   private static final double L = 1000;
   private static final double EPS = 1e-6;

   private VGraph vg;
   private Graphics2D g;
   private MockedStatic<SVGSupport> svgStatic;

   @BeforeEach
   void setUp() {
      vg = new VGraph(new RectCoord());
      mockSvgSupport();
   }

   @AfterEach
   void tearDown() {
      if(g != null) {
         g.dispose();
         g = null;
      }

      if(svgStatic != null) {
         svgStatic.close();
         svgStatic = null;
      }
   }

   /**
    * The batik based SVGSupport is not on the core test classpath and painting asks it whether
    * the graphics is a vector graphics; answer no for the BufferedImage graphics.
    */
   private void mockSvgSupport() {
      SVGSupport support = mock(SVGSupport.class);
      when(support.isSVGGraphics(any())).thenReturn(false);
      svgStatic = mockStatic(SVGSupport.class);
      svgStatic.when(SVGSupport::getInstance).thenReturn(support);
   }

   private static LinearScale lin() {
      LinearScale scale = new LinearScale("m");
      scale.setMin(0);
      scale.setMax(100);
      scale.setIncrement(25);
      scale.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
      return scale;
   }

   private static CategoricalScale cat3() {
      CategoricalScale scale = new CategoricalScale("Cat");
      scale.init("A", "B", "C");
      scale.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
      return scale;
   }

   private Axis2D axis2d(Scale scale, AffineTransform transform) {
      Axis2D axis = new Axis2D(scale, vg);
      axis.setLength(L);
      axis.setScreenTransform(transform);
      return axis;
   }

   private DefaultAxis axis(Scale scale, AffineTransform transform) {
      DefaultAxis axis = new DefaultAxis(scale, vg);
      axis.setLength(L);
      axis.setScreenTransform(transform);
      return axis;
   }

   private static AffineTransform top() {
      return new AffineTransform();
   }

   private static AffineTransform bottom() {
      return AffineTransform.getTranslateInstance(0, 1000);
   }

   private static List<Line2D> shapes(DefaultAxis axis) {
      List<Line2D> result = new ArrayList<>();

      for(int i = 0; i < axis.getGridLineCount(); i++) {
         result.add((Line2D) axis.getGridLine(i).getShape());
      }

      return result;
   }

   @Test
   @DisplayName("both constructors give the axis the 2D axis z index")
   void zIndex() {
      assertEquals(45, new Axis2D().getZIndex());
      assertEquals(45, new Axis2D(lin(), vg).getZIndex());
   }

   @Test
   @DisplayName("grid lines need another Axis2D as the partner")
   void wrongPartnerType() {
      Scale scale = lin();
      Axis2D axis = axis2d(scale, bottom());
      DefaultAxis other = axis(scale, top());

      assertFalse(axis.createGridLines(other, true));
      assertEquals(0, axis.getGridLineCount());
   }

   @Test
   @DisplayName("linear grid lines are shifted by the default depth of 15 in both directions")
   void linearGridLinesWithDepth() {
      Scale scale = lin();
      Axis2D a1 = axis2d(scale, top());
      Axis2D a2 = axis2d(scale, bottom());

      assertTrue(a2.createGridLines(a1, true));

      List<Line2D> lines = shapes(a2);
      assertEquals(5, lines.size());

      for(int k = 0; k < 5; k++) {
         assertEquals(250 * k + 15, lines.get(k).getX1(), EPS);
         assertEquals(1015, lines.get(k).getY1(), EPS);
         assertEquals(250 * k + 15, lines.get(k).getX2(), EPS);
         assertEquals(15, lines.get(k).getY2(), EPS);
      }
   }

   @Test
   @DisplayName("setDepth moves the grid lines by the new depth")
   void depthMovesLines() {
      Scale scale = lin();
      Axis2D a1 = axis2d(scale, top());
      Axis2D a2 = axis2d(scale, bottom());

      assertEquals(15, a2.getDepth(), 0);
      a2.setDepth(30);
      assertEquals(30, a2.getDepth(), 0);

      a2.createGridLines(a1, true);

      List<Line2D> lines = shapes(a2);
      assertEquals(5, lines.size());

      for(int k = 0; k < 5; k++) {
         assertEquals(250 * k + 30, lines.get(k).getX1(), EPS);
         assertEquals(250 * k + 30, lines.get(k).getX2(), EPS);
      }
   }

   @Test
   @DisplayName("descending grid pairs tick i with tick n-1-i, both shifted by the depth, like DefaultAxis")
   void descendingGridLikeDefaultAxis() {
      Scale scale = lin();
      Axis2D a1 = axis2d(scale, top());
      Axis2D a2 = axis2d(scale, bottom());
      a2.createGridLines(a1, false);

      Scale scale2 = lin();
      DefaultAxis d1 = axis(scale2, top());
      DefaultAxis d2 = axis(scale2, bottom());
      d2.createGridLines(d1, false);

      List<Line2D> actual = shapes(a2);
      List<Line2D> expected = shapes(d2);
      assertEquals(5, actual.size());
      assertEquals(expected.size(), actual.size());

      for(int i = 0; i < 5; i++) {
         assertEquals(expected.get(i).getX1(), actual.get(i).getX1() - 15, EPS);
         assertEquals(expected.get(i).getY1(), actual.get(i).getY1() - 15, EPS);
         assertEquals(expected.get(i).getX2(), actual.get(i).getX2() - 15, EPS);
         assertEquals(expected.get(i).getY2(), actual.get(i).getY2() - 15, EPS);
         assertEquals(250 * (4 - i) + 15, actual.get(i).getX2(), EPS);
      }
   }

   @Test
   @DisplayName("linear and categorical tick positions equal those of DefaultAxis")
   void tickPositionsLikeDefaultAxis() {
      for(boolean linear : new boolean[] { true, false }) {
         Axis2D a1 = axis2d(linear ? lin() : cat3(), top());
         Axis2D a2 = axis2d(linear ? lin() : cat3(), bottom());
         a2.createGridLines(a1, true);
         DefaultAxis d1 = axis(linear ? lin() : cat3(), top());
         DefaultAxis d2 = axis(linear ? lin() : cat3(), bottom());
         d2.createGridLines(d1, true);

         List<Line2D> actual = shapes(a2);
         List<Line2D> expected = shapes(d2);

         assertEquals(expected.size(), actual.size(), "linear=" + linear);

         for(int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).getX1(), actual.get(i).getX1() - 15, EPS,
                         "linear=" + linear + " line " + i);
         }
      }
   }

   @Test
   @DisplayName("filled categorical scale without grid gives the same tick positions as DefaultAxis")
   @Disabled("Not filed (only a developer can decide: Axis2D may center filled categorical ticks on purpose in 2.5D charts, no report found): Axis2D.getTickLocations adds 0.5 for any filled CategoricalScale: first grid line is at x=250 (before the depth shift) where DefaultAxis puts it at 0; expected equal positions [0, 500, 1000]")
   void filledCategoricalLikeDefaultAxis() {
      CategoricalScale s1 = cat3();
      s1.setFill(true);
      CategoricalScale s2 = cat3();
      s2.setFill(true);
      Axis2D a1 = axis2d(s1, top());
      Axis2D a2 = axis2d(s1, bottom());
      a2.createGridLines(a1, true);
      DefaultAxis d1 = axis(s2, top());
      DefaultAxis d2 = axis(s2, bottom());
      d2.createGridLines(d1, true);

      List<Line2D> actual = shapes(a2);
      List<Line2D> expected = shapes(d2);

      assertEquals(3, expected.size());
      assertEquals(expected.size(), actual.size());

      for(int i = 0; i < 3; i++) {
         assertEquals(L * i / 2, expected.get(i).getX1(), EPS, "default axis " + i);
         assertEquals(expected.get(i).getX1(), actual.get(i).getX1() - 15, EPS, "line " + i);
      }
   }

   @Test
   @DisplayName("a zero range scale gives finite grid line coordinates")
   @Disabled("Not filed (not reached: the scales widen a zero range before the axis is built, so min never equals max on a 2.5D axis): Axis2D.getTickLocations divides by max-min = 0 and has no zero-range guard, so the grid line x coordinate is NaN; expected finite coordinates")
   void zeroRangeFinite() {
      LinearScale scale = new LinearScale("m");
      scale.setMin(5);
      scale.setMax(5);
      scale.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
      Axis2D a1 = axis2d(scale, top());
      Axis2D a2 = axis2d(scale, bottom());

      a2.createGridLines(a1, true);

      for(Line2D line : shapes(a2)) {
         assertTrue(Double.isFinite(line.getX1()) && Double.isFinite(line.getY1()) &&
                    Double.isFinite(line.getX2()) && Double.isFinite(line.getY2()), "line " + line.getP1());
      }
   }

   private BufferedImage paint(Axis2D axis) {
      BufferedImage image = new BufferedImage(1200, 200, BufferedImage.TYPE_INT_ARGB);
      g = image.createGraphics();
      g.translate(50, 50);
      axis.paint(g);
      g.dispose();
      g = null;
      return image;
   }

   @Test
   @DisplayName("painting fills the band between the axis and its depth offset, hidden axis paints nothing")
   void paintFill() {
      Axis2D axis = new Axis2D(lin(), vg);
      axis.setLength(L);
      axis.setLabelVisible(false);
      axis.setLineColor(Color.BLACK);
      axis.setScreenTransform(top());

      // inside the parallelogram (0,0) (1000,0) (1015,15) (15,15), clear of the ticks
      int rgb = paint(axis).getRGB(50 + 400, 50 + 7);

      assertEquals(255, rgb >>> 24);
      assertEquals(new Color(237, 237, 237).getRGB(), rgb);

      axis.setZIndex(-1);
      BufferedImage hidden = paint(axis);

      for(int x = 0; x < 1200; x++) {
         for(int y = 0; y < 200; y++) {
            assertEquals(0, hidden.getRGB(x, y) >>> 24, "pixel " + x + "," + y);
         }
      }
   }
}
