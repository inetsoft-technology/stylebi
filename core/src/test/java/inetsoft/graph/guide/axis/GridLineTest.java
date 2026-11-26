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
import inetsoft.graph.scale.LinearScale;
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
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The grid line visual: construction from an axis, accessors, the distance used to thin out
 * crowded lines, and painting (clip handling, graphics state).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class GridLineTest {
   private BufferedImage image;
   private Graphics2D g;
   private MockedStatic<SVGSupport> svgStatic;

   @BeforeEach
   void setUp() {
      mockSvgSupport();
   }

   /**
    * The batik based SVGSupport is not on the core test classpath and painting asks it whether
    * the graphics is a vector graphics; answer no for the BufferedImage graphics (thread local
    * static mock, closed after each test).
    */
   private void mockSvgSupport() {
      SVGSupport support = mock(SVGSupport.class);
      when(support.isSVGGraphics(any())).thenReturn(false);
      svgStatic = mockStatic(SVGSupport.class);
      svgStatic.when(SVGSupport::getInstance).thenReturn(support);
   }

   @AfterEach
   void tearDown() {
      if(svgStatic != null) {
         svgStatic.close();
         svgStatic = null;
      }

      if(g != null) {
         g.dispose();
         g = null;
      }
   }

   private void newImage() {
      image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
      g = image.createGraphics();
   }

   private static DefaultAxis axisWithGrid(Color color, int style) {
      LinearScale scale = new LinearScale("m");
      scale.setMin(0);
      scale.setMax(100);
      scale.setIncrement(25);
      scale.getAxisSpec().setGridColor(color);
      scale.getAxisSpec().setGridStyle(style);
      return new DefaultAxis(scale, new VGraph(new RectCoord()));
   }

   private boolean anyPainted(int x0, int x1, int y0, int y1) {
      for(int x = x0; x <= x1; x++) {
         for(int y = y0; y <= y1; y++) {
            if((image.getRGB(x, y) >>> 24) != 0) {
               return true;
            }
         }
      }

      return false;
   }

   @Test
   @DisplayName("a grid line built with an axis takes color and style from the axis grid spec")
   void constructorReadsAxisSpec() {
      DefaultAxis axis = axisWithGrid(Color.RED, GraphConstants.THIN_LINE);
      GridLine line = new GridLine(new Line2D.Double(0, 0, 0, 10), axis, 7);

      assertEquals(Color.RED, line.getColor());
      assertEquals(GraphConstants.THIN_LINE, line.getStyle());
      assertEquals(7, line.getZIndex());
      assertSame(axis, line.getAxis());
   }

   @Test
   @DisplayName("a grid line without an axis keeps the shape and z index and has no axis")
   void constructorWithoutAxis() {
      GridLine line = assertDoesNotThrow(() -> new GridLine(new Line2D.Double(0, 0, 0, 10), null, 5));

      assertNull(line.getAxis());
      assertEquals(5, line.getZIndex());
   }

   @Test
   @DisplayName("a grid line reports no bounds and zero min and preferred sizes")
   void sizesAreZero() {
      GridLine line = new GridLine(new Line2D.Double(0, 0, 0, 10), null, 5);

      assertNull(line.getBounds());
      assertEquals(0, line.getMinWidth(), 0);
      assertEquals(0, line.getMinHeight(), 0);
      assertEquals(0, line.getPreferredWidth(), 0);
      assertEquals(0, line.getPreferredHeight(), 0);
   }

   @Test
   @DisplayName("distance is measured between the top-left corners of the line bounds")
   void distanceBetweenTopLeftCorners() {
      GridLine a = new GridLine(new Line2D.Double(0, 0, 0, 10), null, 1);
      GridLine b = new GridLine(new Line2D.Double(3, 4, 3, 14), null, 1);

      assertEquals(5.0, a.distance(b), 1e-9);
      assertEquals(5.0, b.distance(a), 1e-9);
      assertEquals(0.0, a.distance(a), 1e-9);
   }

   @Test
   @DisplayName("accessors return the values that were set")
   void accessors() {
      GridLine line = new GridLine(new Line2D.Double(0, 0, 0, 10), null, 1);
      Line2D shape = new Line2D.Double(1, 2, 3, 4);

      line.setShape(shape);
      line.setColor(Color.BLUE);
      line.setStyle(GraphConstants.THIN_LINE);
      line.setFacetGrid(true);

      assertSame(shape, line.getShape());
      assertEquals(Color.BLUE, line.getColor());
      assertEquals(GraphConstants.THIN_LINE, line.getStyle());
      assertTrue(line.isFacetGrid());

      line.setFacetGrid(false);
      assertFalse(line.isFacetGrid());
   }

   @Test
   @DisplayName("a line entirely outside the clip is not painted")
   void lineOutsideClipSkipped() {
      newImage();
      GridLine line = new GridLine(new Line2D.Double(0, 100, 50, 100), null, 1);
      line.setColor(Color.RED);
      line.setStyle(GraphConstants.THIN_LINE);
      g.setClip(0, 0, 50, 50);

      line.paint(g);

      assertFalse(anyPainted(0, 199, 0, 199));
   }

   @Test
   @DisplayName("a facet grid line is painted outside the clip and the clip is restored")
   void facetGridIgnoresClip() {
      newImage();
      GridLine line = new GridLine(new Line2D.Double(0, 100, 50, 100), null, 1);
      line.setColor(Color.RED);
      line.setStyle(GraphConstants.THIN_LINE);
      line.setFacetGrid(true);
      g.setClip(0, 0, 50, 50);

      line.paint(g);

      assertTrue(anyPainted(10, 40, 99, 100));
      assertEquals(new Rectangle(0, 0, 50, 50), g.getClipBounds());
   }

   @Test
   @DisplayName("paint leaves the graphics color and stroke as they were")
   void paintRestoresGraphicsState() {
      newImage();
      GridLine line = new GridLine(new Line2D.Double(10, 20, 40, 20), null, 1);
      line.setColor(Color.RED);
      line.setStyle(GraphConstants.THIN_LINE);
      Stroke stroke = new BasicStroke(3f);
      g.setColor(Color.BLUE);
      g.setStroke(stroke);

      line.paint(g);

      assertEquals(Color.BLUE, g.getColor());
      assertEquals(stroke, g.getStroke());
   }

   @Test
   @DisplayName("a line inside the clip is painted in its color along its own row only")
   void lineInsideClipPainted() {
      newImage();
      GridLine line = new GridLine(new Line2D.Double(10, 20, 40, 20), null, 1);
      line.setColor(Color.RED);
      line.setStyle(GraphConstants.THIN_LINE);
      g.setClip(0, 0, 50, 50);

      line.paint(g);

      int rgb = image.getRGB(25, 20) & 0xffffff;
      int rgbAbove = image.getRGB(25, 19) & 0xffffff;
      boolean red = (image.getRGB(25, 20) >>> 24) != 0 && rgb == 0xff0000 ||
         (image.getRGB(25, 19) >>> 24) != 0 && rgbAbove == 0xff0000;

      assertTrue(red, "no red pixel on the line row");
      assertFalse(anyPainted(0, 199, 0, 17), "pixels above the line");
      assertFalse(anyPainted(0, 199, 22, 199), "pixels below the line");
      assertFalse(anyPainted(0, 8, 0, 199), "pixels left of the line");
      assertFalse(anyPainted(42, 199, 0, 199), "pixels right of the line");
   }
}
