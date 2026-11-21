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

import inetsoft.graph.VGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.graphics.SVGSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The axis line visual: size rules, bounds, tick positions, trend line tick projection and the
 * painted line and ticks (checked on a BufferedImage).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class AxisLineTest {
   private static final double L = 1000;
   private static final AffineTransform VT = new AffineTransform(0, 1, 1, 0, 0, 0);

   private RectCoord coord;
   private VGraph vg;
   private Graphics2D g;
   private MockedStatic<SVGSupport> svgStatic;

   @BeforeEach
   void setUp() {
      coord = new RectCoord();
      vg = new VGraph(coord);
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

   private static LinearScale lin() {
      LinearScale scale = new LinearScale("m");
      scale.setMin(0);
      scale.setMax(100);
      scale.setIncrement(25);
      return scale;
   }

   private DefaultAxis newAxis(Scale scale) {
      DefaultAxis axis = new DefaultAxis(scale, vg);
      axis.setLength(L);
      axis.setLineColor(Color.BLACK);
      return axis;
   }

   /** Places the axis; visibility flags must be set before, they rebuild the axis line. */
   private static DefaultAxis place(DefaultAxis axis, AffineTransform transform) {
      axis.setScreenTransform(transform);
      return axis;
   }

   private DefaultAxis hz() {
      return place(newAxis(lin()), new AffineTransform());
   }

   private DefaultAxis vt() {
      return place(newAxis(lin()), VT);
   }

   private BufferedImage paint(DefaultAxis axis, boolean horizontal) {
      BufferedImage image = horizontal ? new BufferedImage(1200, 120, BufferedImage.TYPE_INT_ARGB)
         : new BufferedImage(120, 1200, BufferedImage.TYPE_INT_ARGB);
      g = image.createGraphics();
      g.translate(50, 50);
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

   private static void setTrendLine(DefaultAxis axis, RectCoord coord, double min, double max) {
      coord.setTrendLineMin(min);
      coord.setTrendLineMax(max);
      axis.setCoordinate(coord);
   }

   @ParameterizedTest(name = "tickDown={0} tickVisible={1} lineVisible={2} -> {3}")
   @CsvSource({ "true,true,true,5", "true,true,false,4", "false,true,true,1", "true,false,true,1",
                "false,false,false,0" })
   @DisplayName("min height is the line width plus the tick length that are shown")
   void minHeight(boolean tickDown, boolean tickVisible, boolean lineVisible, double expected) {
      DefaultAxis axis = newAxis(lin());
      axis.setTickVisible(tickVisible);
      axis.setLineVisible(lineVisible);
      axis.setTickDown(tickDown);
      place(axis, new AffineTransform());

      assertEquals(expected, axis.getAxisLine().getMinHeight(), 1e-9);
   }

   @Test
   @DisplayName("pinned: min and preferred width are 50 and the preferred height equals the min height")
   void pinnedConstants() {
      AxisLine line = hz().getAxisLine();

      assertEquals(50, line.getMinWidth(), 1e-9);
      assertEquals(50, line.getPreferredWidth(), 1e-9);
      assertEquals(line.getMinHeight(), line.getPreferredHeight(), 1e-9);
   }

   @Test
   @DisplayName("bounds span the axis length along the axis and the line height across it")
   void bounds() {
      DefaultAxis h = hz();
      h.getAxisLine().setHeight(5);
      DefaultAxis v = vt();
      v.getAxisLine().setHeight(5);

      assertEquals(new Rectangle2D.Double(0, 0, 1000, 5), h.getAxisLine().getBounds());
      assertEquals(new Rectangle2D.Double(0, 0, 5, 1000), v.getAxisLine().getBounds());
   }

   @Test
   @DisplayName("tick position applies the axis transform to (x, offset)")
   void tickPosition() {
      Point2D h = hz().getAxisLine().getTickPosition(250, -4);
      Point2D v = vt().getAxisLine().getTickPosition(250, -4);

      assertEquals(250, h.getX(), 1e-9);
      assertEquals(-4, h.getY(), 1e-9);
      assertEquals(-4, v.getX(), 1e-9);
      assertEquals(250, v.getY(), 1e-9);
   }

   @Test
   @DisplayName("without a trend line the ticks are returned unchanged")
   void noTrendLine() {
      double[] ticks = { 0, 10, 20, 30 };
      DefaultAxis axis = hz();

      assertSame(ticks, axis.getAxisLine().projectTicksTrendLine(ticks));

      axis.setCoordinate(coord);

      assertSame(ticks, axis.getAxisLine().projectTicksTrendLine(ticks));
      assertArrayEquals(new double[] { 0, 10, 20, 30 }, ticks, 0);
   }

   @Test
   @DisplayName("a trend line above the last tick adds ticks at the tick spacing")
   void projectForward() {
      DefaultAxis axis = hz();
      setTrendLine(axis, coord, Double.POSITIVE_INFINITY, 55);

      double[] result = axis.getAxisLine().projectTicksTrendLine(new double[] { 0, 10, 20, 30 });

      assertArrayEquals(new double[] { 0, 10, 20, 30, 40, 50 }, result, 1e-9);
      assertEquals(2, axis.getAxisLine().getProjectForward());
      assertEquals(0, axis.getAxisLine().getProjectBackward());
   }

   @Test
   @DisplayName("a trend line below the first tick adds ticks downward at the tick spacing")
   void projectBackward() {
      DefaultAxis axis = hz();
      setTrendLine(axis, coord, -25, Double.NEGATIVE_INFINITY);

      double[] result = axis.getAxisLine().projectTicksTrendLine(new double[] { 0, 10, 20, 30 });

      assertArrayEquals(new double[] { -20, -10, 0, 10, 20, 30 }, result, 1e-9);
      assertEquals(2, axis.getAxisLine().getProjectBackward());
      assertEquals(0, axis.getAxisLine().getProjectForward());
   }

   @ParameterizedTest(name = "trend line max {0} adds {1} ticks")
   @CsvSource({ "40,0", "45,1", "49.9,1", "50,1", "55,2" })
   @DisplayName("projection count at the boundaries, never above the trend line max")
   void projectBoundary(double max, int added) {
      DefaultAxis axis = hz();
      setTrendLine(axis, coord, Double.POSITIVE_INFINITY, max);

      double[] result = axis.getAxisLine().projectTicksTrendLine(new double[] { 0, 10, 20, 30 });

      assertEquals(4 + added, result.length);
      assertTrue(result[result.length - 1] <= Math.max(max, 30));
   }

   @Test
   @DisplayName("one tick, equal ticks and null ticks are returned unchanged even with a trend line")
   void projectDegenerate() {
      DefaultAxis axis = hz();
      setTrendLine(axis, coord, -25, 55);
      double[] one = { 5 };
      double[] equal = { 10, 10, 10 };

      assertSame(one, axis.getAxisLine().projectTicksTrendLine(one));
      assertSame(equal, axis.getAxisLine().projectTicksTrendLine(equal));
      assertNull(axis.getAxisLine().projectTicksTrendLine(null));
   }

   @Test
   @DisplayName("horizontal axis paints the line on row 50 and a 4 px major tick on row 46 at every major tick")
   void paintHorizontal() {
      BufferedImage image = paint(hz(), true);

      assertTrue(painted(image, 110, 110, 50, 50), "axis line");

      for(int k = 0; k < 5; k++) {
         int x = 50 + 250 * k;
         assertTrue(painted(image, x - 1, x + 1, 46, 46), "tick " + k);
      }

      // the 2 px minor tick halfway between two major ticks stops below row 46
      assertFalse(painted(image, 175, 175, 46, 46), "row 46 at the minor tick");
   }

   @Test
   @DisplayName("tick direction decides the side of the line the ticks are drawn on")
   void tickDirection() {
      DefaultAxis down = newAxis(lin());
      down.setTickDown(true);
      DefaultAxis up = newAxis(lin());
      up.setTickDown(false);
      BufferedImage imgDown = paint(place(down, new AffineTransform()), true);
      BufferedImage imgUp = paint(place(up, new AffineTransform()), true);

      assertTrue(painted(imgDown, 299, 301, 46, 48));
      assertFalse(painted(imgDown, 299, 301, 52, 54));
      assertTrue(painted(imgUp, 299, 301, 52, 54));
      assertFalse(painted(imgUp, 299, 301, 46, 48));
   }

   @Test
   @DisplayName("hidden min and max ticks are not drawn above the line, the others are")
   void minMaxTickHidden() {
      DefaultAxis axis = newAxis(lin());
      axis.setMinTickVisible(false);
      axis.setMaxTickVisible(false);
      BufferedImage image = paint(place(axis, new AffineTransform()), true);

      assertFalse(painted(image, 49, 51, 44, 47), "min tick");
      assertFalse(painted(image, 1049, 1051, 44, 47), "max tick");
      assertTrue(painted(image, 299, 301, 46, 48), "inner tick");
      assertTrue(painted(image, 549, 551, 46, 48), "inner tick");
   }

   @Test
   @DisplayName("a hidden max tick leaves no tick pixel at all above the line")
   @Disabled("Bug #82028: Hiding the maximum tick still leaves a small minor tick mark")
   void hiddenMaxTickLeavesNoStub() {
      DefaultAxis axis = newAxis(lin());
      axis.setMinTickVisible(false);
      axis.setMaxTickVisible(false);
      BufferedImage image = paint(place(axis, new AffineTransform()), true);

      assertFalse(painted(image, 49, 51, 44, 49), "min tick");
      assertFalse(painted(image, 1049, 1051, 44, 49), "max tick");
   }

   @Test
   @DisplayName("with the line hidden row 50 is empty away from the ticks and the major ticks remain")
   void lineHidden() {
      DefaultAxis axis = newAxis(lin());
      axis.setLineVisible(false);
      BufferedImage image = paint(place(axis, new AffineTransform()), true);

      assertFalse(painted(image, 110, 110, 50, 50), "axis row");

      for(int k = 0; k < 5; k++) {
         int x = 50 + 250 * k;
         assertTrue(painted(image, x - 1, x + 1, 46, 46), "tick " + k);
      }
   }

   @Test
   @DisplayName("with the ticks hidden only the line remains")
   void ticksHidden() {
      DefaultAxis axis = newAxis(lin());
      axis.setTickVisible(false);
      BufferedImage image = paint(place(axis, new AffineTransform()), true);

      assertTrue(painted(image, 175, 175, 49, 50), "axis line");
      assertFalse(painted(image, 0, 1199, 40, 47), "ticks");
   }

   @Test
   @DisplayName("a null line color paints nothing")
   void nullLineColor() {
      DefaultAxis axis = hz();
      axis.setLineColor(null);
      BufferedImage image = paint(axis, true);

      assertFalse(painted(image, 0, 1199, 0, 119));
   }

   @Test
   @DisplayName("an explicit minor increment draws short ticks between the major ticks")
   void minorTicks() {
      LinearScale scale = lin();
      scale.setMinorIncrement(12.5);
      BufferedImage image = paint(place(newAxis(scale), new AffineTransform()), true);

      assertTrue(painted(image, 174, 176, 48, 48), "minor tick");
      assertFalse(painted(image, 0, 47, 0, 119), "left of the axis");
      assertFalse(painted(image, 1053, 1199, 0, 119), "right of the axis");
   }

   @Test
   @DisplayName("without a minor increment a crowded axis draws no minor ticks")
   void crowdedAxisSkipsDefaultMinorTicks() {
      DefaultAxis axis = newAxis(lin());
      axis.setLength(40);
      BufferedImage image = paint(place(axis, new AffineTransform()), true);

      // major ticks are 10 px apart, the minor tick halfway (x = 55) would be 5 px from them
      assertTrue(painted(image, 59, 61, 46, 48), "major tick");
      assertFalse(painted(image, 54, 56, 46, 49), "minor tick");
   }

   @Test
   @DisplayName("vertical axis draws its ticks to the left of the line at every major tick")
   void paintVertical() {
      BufferedImage image = paint(vt(), false);

      for(int k = 0; k < 5; k++) {
         int y = 50 + 250 * k;
         assertTrue(painted(image, 46, 48, y - 1, y + 1), "tick " + k);
      }

      assertTrue(painted(image, 49, 50, 175, 175), "axis line");
      assertFalse(painted(image, 52, 119, 0, 1199), "right of the line");
   }
}
