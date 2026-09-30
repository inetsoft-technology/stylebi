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
import inetsoft.uql.viewsheet.internal.RectangleVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77011: the rectangle background must be filled with the same outline the border
 * is stroked with. A fill translated by (1,1) poked out of a round border on the
 * bottom/right, which over a shadow looked like a second border, and left a translucent
 * gap inside the border on the top/left.
 *
 * Renders through the exporters' entry point, {@code VSFloatable.getImage(true)}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSRectangleBackgroundTest {
   private static final int PAGE_BG = 0xf5f5f5;

   /**
    * The reported configuration (244x324, 1px border, round corner 264, black shadow at
    * distance 0, blur 50), plus 2px and 3px borders: over the page background there
    * must be no light band between the border and the shadow. The 2px cases guard against
    * simply filling (0, 0, w-1, h-1), which lights up the outer edge of an even border.
    */
   @ParameterizedTest
   @CsvSource({ "4097, 264", "4097, 20", "4098, 264", "4098, 20", "4099, 264" })
   void noLightBandBetweenBorderAndShadow(int style, int corner) {
      RectangleVSAssemblyInfo info = rectangle(244, 324, style, corner);
      ShapeShadow shadow = new ShapeShadow();
      shadow.setColor("#000000");
      shadow.setAlpha(100);
      shadow.setDirection(ShapeShadow.SOUTH_EAST);
      shadow.setDistance(0);
      shadow.setBlur(50);
      info.setShadow(true);
      info.setShadowInfo(shadow);

      BufferedImage img = render(info);
      Insets in = ShapeShadowUtil.getScaledShadowInsets(info);
      BufferedImage page = new BufferedImage(img.getWidth(), img.getHeight(),
                                             BufferedImage.TYPE_INT_RGB);
      Graphics2D g = page.createGraphics();
      g.setColor(new Color(PAGE_BG));
      g.fillRect(0, 0, page.getWidth(), page.getHeight());
      g.drawImage(img, 0, 0, null);
      g.dispose();

      int linew = (int) GTool.getLineWidth(style);
      int halfw = linew / 2;
      int bands = 0;

      // walk outward along the normal of the border's centerline, all the way round
      for(double[] pn : outline(halfw, halfw, 244 - linew, 324 - linew, corner * 2)) {
         bands += lightAfterBorder(page, in, pn);
      }

      assertEquals(0, bands, "light band between the border and the shadow");
   }

   /**
    * Without a shadow, compare the real image against the border alone: no fill may
    * reach the region outside the border, and nothing enclosed by the border (or on its
    * inner edge) may be translucent. Covers line widths/styles and corners, square too.
    */
   @ParameterizedTest
   @CsvSource({
      "4097, 264", "4097, 140", "4097, 20", "4097, 0",
      "4098, 264", "4098, 20", "4098, 0",
      "4099, 264", "4099, 20", "4099, 0",
      // DOUBLE_LINE
      "8195, 264", "8195, 20", "8195, 0"
   })
   void fillStaysInsideTheBorder(int style, int corner) {
      RectangleVSAssemblyInfo info = rectangle(244, 324, style, corner);
      BufferedImage full = render(new VSRectangle(info.getViewsheet()), info);
      BufferedImage fill = render(new FillOnly(info.getViewsheet()), info);
      BufferedImage border = render(new BorderOnly(info.getViewsheet()), info);
      int w = full.getWidth(), h = full.getHeight();
      boolean[][] outside = outside(border);
      int leaks = 0, gaps = 0;

      for(int y = 0; y < h; y++) {
         for(int x = 0; x < w; x++) {
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

   @Test
   void noBorderFillCoversTheWholeShape() {
      BufferedImage img = render(rectangle(100, 60, 0, 30));

      // the ellipse-ish outline reaches all four edges of the 2x image
      assertEquals(255, img.getRGB(100, 0) >>> 24, "top");
      assertEquals(255, img.getRGB(100, 119) >>> 24, "bottom");
      assertEquals(255, img.getRGB(0, 60) >>> 24, "left");
      assertEquals(255, img.getRGB(199, 60) >>> 24, "right");
   }

   private static RectangleVSAssemblyInfo rectangle(int w, int h, int style, int corner) {
      Viewsheet vs = new Viewsheet();
      RectangleVSAssembly asm = new RectangleVSAssembly(vs, "Rectangle1");
      RectangleVSAssemblyInfo info = (RectangleVSAssemblyInfo) asm.getVSAssemblyInfo();
      info.setPixelSize(new Dimension(w, h));
      info.setLineStyle(style);
      VSFormat fmt = info.getFormat().getUserDefinedFormat();
      fmt.setRoundCorner(corner);
      fmt.setBackground(Color.WHITE);
      fmt.setForeground(new Color(0x555555));
      return info;
   }

   private static BufferedImage render(RectangleVSAssemblyInfo info) {
      return render(new VSRectangle(info.getViewsheet()), info);
   }

   private static BufferedImage render(VSRectangle rect, RectangleVSAssemblyInfo info) {
      rect.setViewsheet(info.getViewsheet());
      rect.setAssemblyInfo(info);
      return (BufferedImage) rect.getImage(true);
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
    * Points on a round rectangle outline, clamped like RoundRectangle2D, with their unit
    * outward normals: {x, y, nx, ny} in shape pixels.
    */
   private static java.util.List<double[]> outline(double x, double y, double w, double h,
                                                    double arc)
   {
      double a = Math.min(w, arc) / 2, b = Math.min(h, arc) / 2;
      double[][] centers = { { x + w - a, y + h - b }, { x + a, y + h - b },
                             { x + a, y + b }, { x + w - a, y + b } };
      java.util.List<double[]> points = new java.util.ArrayList<>();

      for(int q = 0; q < 4; q++) {
         for(int deg = 0; deg <= 90; deg++) {
            double t = Math.toRadians(q * 90 + deg);
            double nx = Math.cos(t) / a, ny = Math.sin(t) / b;
            double len = Math.hypot(nx, ny);
            points.add(new double[] { centers[q][0] + a * Math.cos(t),
                                      centers[q][1] + b * Math.sin(t),
                                      nx / len, ny / len });
         }
      }

      return points;
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
   private static class FillOnly extends VSRectangle {
      FillOnly(Viewsheet vs) {
         super(vs);
      }

      @Override
      protected void paintShape(Graphics2D g) {
         // background only
      }
   }

   /** The real border, no background. */
   private static class BorderOnly extends VSRectangle {
      BorderOnly(Viewsheet vs) {
         super(vs);
      }

      @Override
      protected void drawBackground(Graphics g) {
         // border only
      }
   }
}
