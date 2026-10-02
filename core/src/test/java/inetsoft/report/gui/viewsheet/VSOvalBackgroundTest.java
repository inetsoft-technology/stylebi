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
package inetsoft.report.gui.viewsheet;

import inetsoft.graph.internal.GTool;
import inetsoft.report.io.viewsheet.ShapeShadowUtil;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.OvalVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77337: the oval background must be filled with the same ellipse the border is
 * stroked on. paintShape() insets a border of width L by L-1, so a fill on the box edge
 * poked out of a 2px or thicker border, which over a shadow looked like a light ring.
 *
 * Renders through the exporters' entry point, {@code VSFloatable.getImage(true)}, which
 * runs drawBackground() twice (outer canvas and shadow source).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSOvalBackgroundTest {
   private static final int PAGE_BG = 0xf5f5f5;

   /**
    * No fill pixel may lie outside the stroke's outer edge. Solid 2px/3px, scripted 5px
    * and 10px, and a scripted 3px dash (open border, so checked by geometry only).
    */
   @ParameterizedTest
   @CsvSource({
      "244, 324, 4098", "244, 324, 4099", "244, 324, 4101", "244, 324, 4106",
      "101, 101, 4098", "101, 101, 4099", "200, 120, 4101", "244, 324, 4147"
   })
   void fillStaysInsideTheStrokeOuterEdge(int w, int h, int style) {
      OvalVSAssemblyInfo info = oval(w, h, style);
      BufferedImage fill = render(new FillOnly(info.getViewsheet()), info);
      assertEquals(0, fillOutsideStroke(fill, info, w, h, style),
                   "fill pixels outside the stroke's outer edge");
   }

   /**
    * Compared with the border alone: no fill where the border leaves the outside
    * transparent, and nothing enclosed by the border (or on its inner edge) translucent.
    */
   @ParameterizedTest
   @CsvSource({
      "244, 324, 4097", "244, 324, 4098", "244, 324, 4099", "244, 324, 4101",
      "244, 324, 4106", "101, 101, 4098", "101, 101, 4099", "200, 120, 4101",
      // DOUBLE_LINE
      "244, 324, 8195", "101, 101, 8195"
   })
   void fillHasNoLeakAndNoSeam(int w, int h, int style) {
      OvalVSAssemblyInfo info = oval(w, h, style);
      BufferedImage full = render(new VSOval(info.getViewsheet()), info);
      BufferedImage fill = render(new FillOnly(info.getViewsheet()), info);
      BufferedImage border = render(new BorderOnly(info.getViewsheet()), info);
      boolean[][] outside = outside(border);
      int leaks = 0, gaps = 0;

      for(int y = 0; y < full.getHeight(); y++) {
         for(int x = 0; x < full.getWidth(); x++) {
            if(outside[y][x]) {
               if(alpha(fill, x, y) > 0) {
                  leaks++;
               }
            }
            // the non-antialiased fill meets the antialiased border at pixel precision,
            // so an inner border pixel can be partly covered with no fill below it
            else if(alpha(full, x, y) < 160 &&
               (alpha(border, x, y) == 0 || touchesEnclosed(border, outside, x, y)))
            {
               gaps++;
            }
         }
      }

      assertEquals(0, leaks, "fill pixels outside the border");
      assertEquals(0, gaps, "see-through pixels inside the border");
   }

   /**
    * The reported case: over the page, no light pixel between the border and a black
    * shadow at distance 0, blur 50. THIN is the unaffected control. Shapes are kept large
    * enough that the blur does not light up the whole neighbourhood.
    */
   @ParameterizedTest
   @CsvSource({ "244, 324, 4097", "244, 324, 4098", "244, 324, 4099", "244, 324, 4101",
                "101, 101, 4099" })
   void noLightRingBetweenBorderAndShadow(int w, int h, int style) {
      OvalVSAssemblyInfo info = oval(w, h, style);
      ShapeShadow shadow = new ShapeShadow();
      shadow.setColor("#000000");
      shadow.setAlpha(100);
      shadow.setDirection(ShapeShadow.SOUTH_EAST);
      shadow.setDistance(0);
      shadow.setBlur(50);
      info.setShadow(true);
      info.setShadowInfo(shadow);

      BufferedImage img = render(new VSOval(info.getViewsheet()), info);
      Insets in = ShapeShadowUtil.getScaledShadowInsets(info);
      BufferedImage page = new BufferedImage(img.getWidth(), img.getHeight(),
                                             BufferedImage.TYPE_INT_RGB);
      Graphics2D g = page.createGraphics();
      g.setColor(new Color(PAGE_BG));
      g.fillRect(0, 0, page.getWidth(), page.getHeight());
      g.drawImage(img, 0, 0, null);
      g.dispose();

      Ellipse2D center = centerline(w, h, style);
      double cx = center.getCenterX(), cy = center.getCenterY();
      double a = center.getWidth() / 2, b = center.getHeight() / 2;
      int bands = 0;

      // walk outward along the normal of the border's centerline, all the way round
      for(int deg = 0; deg < 360; deg++) {
         double t = Math.toRadians(deg);
         double nx = Math.cos(t) / a, ny = Math.sin(t) / b;
         double len = Math.hypot(nx, ny);
         bands += lightAfterBorder(page, in, new double[] {
            cx + a * Math.cos(t), cy + b * Math.sin(t), nx / len, ny / len });
      }

      assertEquals(0, bands, "light ring between the border and the shadow");
   }

   /**
    * Styles whose fill must not move: no border, 1px solid and dashed, DOUBLE_LINE (its
    * outer ring is on the box edge although its width is 3), and fractional widths, which
    * a script can set and whose (int) L - 1 would be -1 without the clamp.
    */
   @ParameterizedTest
   @CsvSource({
      // NONE, THIN, DOT, DASH
      "244, 324, 0", "244, 324, 4097", "244, 324, 4113", "244, 324, 4145",
      "244, 324, 8195", "101, 101, 8195",
      // THIN_THIN (0.5), ULTRA_THIN (0.25)
      "244, 324, 528384", "244, 324, 266240"
   })
   void unchangedStylesKeepTheBoxFill(int w, int h, int style) {
      OvalVSAssemblyInfo info = oval(w, h, style);
      BufferedImage fill = render(new FillOnly(info.getViewsheet()), info);
      BufferedImage box = render(new BoxFillOnly(info.getViewsheet()), info);
      int diff = 0;

      for(int y = 0; y < fill.getHeight(); y++) {
         for(int x = 0; x < fill.getWidth(); x++) {
            if(fill.getRGB(x, y) != box.getRGB(x, y)) {
               diff++;
            }
         }
      }

      assertEquals(0, diff, "pixels changed against the (0, 0, w-1, h-1) fill");
   }

   private static OvalVSAssemblyInfo oval(int w, int h, int style) {
      Viewsheet vs = new Viewsheet();
      OvalVSAssembly asm = new OvalVSAssembly(vs, "Oval1");
      OvalVSAssemblyInfo info = (OvalVSAssemblyInfo) asm.getVSAssemblyInfo();
      info.setPixelSize(new Dimension(w, h));
      info.setLineStyle(style);
      VSFormat fmt = info.getFormat().getUserDefinedFormat();
      fmt.setBackground(Color.WHITE);
      fmt.setForeground(new Color(0x555555));
      return info;
   }

   private static BufferedImage render(VSOval oval, OvalVSAssemblyInfo info) {
      oval.setViewsheet(info.getViewsheet());
      oval.setAssemblyInfo(info);
      return (BufferedImage) oval.getImage(true);
   }

   /** The ellipse VSOval.paintShape() strokes a single line on, in shape pixels. */
   private static Ellipse2D centerline(int w, int h, int style) {
      int gap = (int) GTool.getLineWidth(style) - 1;
      return new Ellipse2D.Double(gap, gap, w - 1 - 2 * gap, h - 1 - 2 * gap);
   }

   /** Fill pixels whose centre lies outside the stroke's geometric outer edge. */
   private static int fillOutsideStroke(BufferedImage fill, OvalVSAssemblyInfo info,
                                        int w, int h, int style)
   {
      Ellipse2D center = centerline(w, h, style);
      // solid stroke of the same width: dash gaps are bounded by the same outer edge
      Area inside = new Area(new BasicStroke(GTool.getLineWidth(style))
                                .createStrokedShape(center));
      inside.add(new Area(center));
      Insets in = ShapeShadowUtil.getScaledShadowInsets(info);
      int count = 0;

      for(int y = 0; y < fill.getHeight(); y++) {
         for(int x = 0; x < fill.getWidth(); x++) {
            if(alpha(fill, x, y) > 0 &&
               !inside.contains((x + 0.5) / 2 - in.left, (y + 0.5) / 2 - in.top))
            {
               count++;
            }
         }
      }

      return count;
   }

   private static int alpha(BufferedImage img, int x, int y) {
      return img.getRGB(x, y) >>> 24;
   }

   /** Transparent pixels reachable from the image edge without crossing the border. */
   private static boolean[][] outside(BufferedImage img) {
      int w = img.getWidth(), h = img.getHeight();
      boolean[][] seen = new boolean[h][w];
      java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();

      for(int y = 0; y < h; y++) {
         for(int x = 0; x < w; x++) {
            boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1;

            if(edge && alpha(img, x, y) == 0) {
               seen[y][x] = true;
               queue.add(new int[] { x, y });
            }
         }
      }

      while(!queue.isEmpty()) {
         int[] p = queue.poll();

         for(int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            int x = p[0] + d[0], y = p[1] + d[1];

            if(x >= 0 && y >= 0 && x < w && y < h && !seen[y][x] && alpha(img, x, y) == 0) {
               seen[y][x] = true;
               queue.add(new int[] { x, y });
            }
         }
      }

      return seen;
   }

   /** A border pixel next to a transparent pixel the border encloses (its inner edge). */
   private static boolean touchesEnclosed(BufferedImage border, boolean[][] outside,
                                          int x, int y)
   {
      for(int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
         int nx = x + d[0], ny = y + d[1];

         if(nx >= 0 && ny >= 0 && nx < border.getWidth() && ny < border.getHeight() &&
            !outside[ny][nx] && alpha(border, nx, ny) == 0)
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Count light ridges outside the border along one normal: from the darkest sample (the
    * border) outward the page only gets lighter as the shadow fades, so any sample
    * noticeably lighter than one a little further out is a band.
    */
   private static int lightAfterBorder(BufferedImage page, Insets in, double[] pn) {
      int n = 41;
      int[] red = new int[n];
      int darkest = 0;

      // from 4 shape pixels inside the centerline to 6 outside, in quarter pixels
      for(int i = 0; i < n; i++) {
         double t = -4 + i * 0.25;
         int x = (int) Math.floor((pn[0] + pn[2] * t + in.left) * 2);
         int y = (int) Math.floor((pn[1] + pn[3] * t + in.top) * 2);
         red[i] = (page.getRGB(x, y) >> 16) & 0xff;

         if(red[i] < red[darkest]) {
            darkest = i;
         }
      }

      for(int i = darkest + 1; i < n; i++) {
         for(int j = i + 1; j <= i + 8 && j < n; j++) {
            if(red[i] - red[j] > 0x20) {
               return 1;
            }
         }
      }

      return 0;
   }

   /** The real background, no border. */
   private static class FillOnly extends VSOval {
      FillOnly(Viewsheet vs) {
         super(vs);
      }

      @Override
      protected void paintShape(Graphics2D g) {
         // background only
      }
   }

   /** The real border, no background. */
   private static class BorderOnly extends VSOval {
      BorderOnly(Viewsheet vs) {
         super(vs);
      }

      @Override
      protected void drawBackground(Graphics g) {
         // border only
      }
   }

   /** The fill before Bug #77337, on the box edge, no border. */
   private static class BoxFillOnly extends VSOval {
      BoxFillOnly(Viewsheet vs) {
         super(vs);
      }

      @Override
      protected void paintShape(Graphics2D g) {
         // background only
      }

      @Override
      protected void drawBackground(Graphics g) {
         g.setColor(getBackground());
         Dimension size = getShapePixelSize();
         g.fillOval(0, 0, size.width - 1, size.height - 1);
      }
   }
}
