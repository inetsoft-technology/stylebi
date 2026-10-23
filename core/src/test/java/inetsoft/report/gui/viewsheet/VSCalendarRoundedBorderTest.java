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

import inetsoft.report.StyleConstants;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78671: a thick or double border with rounded corners is drawn one pixel further
 * inside than a square one, and used to cover the outer edges of the year view's last
 * month column and row in the 2x export image.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSCalendarRoundedBorderTest {
   private static final int[][] SIZES = {
      {300, 300}, {320, 320}, {301, 301}, {300, 301}, {303, 303}, {301, 300}
   };

   @Test
   void singleYearViewKeepsAllMonthEdgesWithRoundedThickBorder() throws Exception {
      for(int style : new int[] { StyleConstants.THICK_LINE, StyleConstants.DOUBLE_LINE }) {
         for(int[] size : SIZES) {
            assertAllEdges(CalendarVSAssemblyInfo.SINGLE_CALENDAR_MODE, style, 10,
                           size[0], size[1], 4, 3);
         }
      }
   }

   @Test
   void doubleYearViewKeepsAllMonthEdgesWithRoundedThickBorder() throws Exception {
      for(int style : new int[] { StyleConstants.THICK_LINE, StyleConstants.DOUBLE_LINE }) {
         for(int[] size : new int[][] {{600, 300}, {640, 300}, {601, 301}, {600, 301}}) {
            assertAllEdges(CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE, style, 10,
                           size[0], size[1], 8, 3);
         }
      }
   }

   @Test
   void thinMediumAndSquareOutputIsIndependentOfRoundedGap() throws Exception {
      // the gap only applies to a 3px side with a rounded corner, so the grid of every other
      // combination must be identical to the one without any rounding
      int[] styles = { StyleConstants.THIN_LINE, StyleConstants.MEDIUM_LINE };

      for(int style : styles) {
         for(int[] size : SIZES) {
            assertArrayEquals(
               gridMask(render(CalendarVSAssemblyInfo.SINGLE_CALENDAR_MODE, style, 0,
                               size[0], size[1], true)),
               gridMask(render(CalendarVSAssemblyInfo.SINGLE_CALENDAR_MODE, style, 10,
                               size[0], size[1], true)),
               "style " + style + " " + size[0] + "x" + size[1]);
         }
      }
   }

   @Test
   void squareThickBorderStillKeepsAllEdges() throws Exception {
      for(int[] size : SIZES) {
         assertAllEdges(CalendarVSAssemblyInfo.SINGLE_CALENDAR_MODE,
                        StyleConstants.THICK_LINE, 0, size[0], size[1], 4, 3);
      }
   }

   // every month cell must keep its four edges: cols * 2 vertical edges along a row of month
   // cells, rows * 2 horizontal edges along a column, and no gray edge pixel is lost to the border
   private void assertAllEdges(int mode, int style, int radius, int w, int h, int edgeCells,
                               int rows) throws Exception
   {
      BufferedImage with = render(mode, style, radius, w, h, true);
      BufferedImage without = render(mode, style, radius, w, h, false);
      String msg = "mode " + mode + " style " + style + " r" + radius + " " + w + "x" + h;

      // the border colour never overwrites a pixel the grid drew
      boolean[] expected = gridMask(without);
      boolean[] actual = gridMask(with);
      int missing = 0;

      for(int i = 0; i < expected.length; i++) {
         if(expected[i] && !actual[i]) {
            missing++;
         }
      }

      assertEquals(0, missing, msg + ": grid pixels covered by the border");

      // horizontal scan through the first row of months, vertical scan through the first column
      int rowY = firstGridRowY(without);
      int colX = firstGridColumnX(without);
      assertEquals(edgeCells * 2, runs(with, true, rowY), msg + ": month cell vertical edges");
      assertEquals(rows * 2, runs(with, false, colX), msg + ": month cell horizontal edges");
   }

   private static BufferedImage render(int viewMode, int style, int radius, int w, int h,
                                       boolean borderVisible) throws Exception
   {
      CalendarVSAssembly assembly = new CalendarVSAssembly();
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setYearViewValue(true);
      info.setYearView(true);
      info.setViewModeValue(viewMode);
      info.setViewMode(viewMode);
      info.setTitleVisibleValue(false);
      info.setTitleVisible(false);
      info.setCurrentDate1("2026");
      info.setCurrentDate2("2027");

      VSFormat fmt = info.getFormat().getUserDefinedFormat();
      fmt.setBordersValue(new Insets(style, style, style, style));
      fmt.setBorders(new Insets(style, style, style, style));
      Color color = borderVisible ? Color.RED : new Color(0, 0, 0, 0);
      BorderColors colors = new BorderColors(color, color, color, color);
      fmt.setBorderColorsValue(colors);
      fmt.setBorderColors(colors);
      fmt.setRoundCornerValue(radius);
      fmt.setRoundCorner(radius);

      VSCalendar calendar = new VSCalendar(new Viewsheet());
      calendar.setTheme(new FlexTheme(PortalThemesManager.getColorTheme()));
      calendar.setAssemblyInfo(info);
      calendar.setPixelSize(new Dimension(w, h));
      return (BufferedImage) calendar.getImage(true);
   }

   private static boolean[] gridMask(BufferedImage img) {
      boolean[] mask = new boolean[img.getWidth() * img.getHeight()];

      for(int y = 0; y < img.getHeight(); y++) {
         for(int x = 0; x < img.getWidth(); x++) {
            // the rounded corner arc itself may legitimately cross a corner pixel
            boolean corner = (x < CORNER || x >= img.getWidth() - CORNER) &&
               (y < CORNER || y >= img.getHeight() - CORNER);
            mask[y * img.getWidth() + x] = !corner && img.getRGB(x, y) == CELL_BORDER;
         }
      }

      return mask;
   }

   // the first image row that has month cell edge pixels at least 40px into the image
   private static int firstGridRowY(BufferedImage img) {
      for(int y = 10; y < img.getHeight(); y++) {
         int n = 0;

         for(int x = 0; x < img.getWidth(); x++) {
            if(img.getRGB(x, y) == CELL_BORDER) {
               n++;
            }
         }

         // a row through the month cells (not their top/bottom edge) has only the short edges
         if(n > 0 && n < img.getWidth() / 4) {
            return y + 6;
         }
      }

      throw new AssertionError("no month cells found");
   }

   private static int firstGridColumnX(BufferedImage img) {
      for(int x = 4; x < img.getWidth(); x++) {
         int n = 0;

         for(int y = 0; y < img.getHeight(); y++) {
            if(img.getRGB(x, y) == CELL_BORDER) {
               n++;
            }
         }

         if(n > 0 && n < img.getHeight() / 4) {
            return x + 6;
         }
      }

      throw new AssertionError("no month cells found");
   }

   private static int runs(BufferedImage img, boolean alongRow, int at) {
      int length = alongRow ? img.getWidth() : img.getHeight();
      int runs = 0;
      boolean inRun = false;

      for(int i = 0; i < length; i++) {
         int rgb = alongRow ? img.getRGB(i, at) : img.getRGB(at, i);
         boolean gray = rgb == CELL_BORDER;

         if(gray && !inRun) {
            runs++;
         }

         inRun = gray;
      }

      return runs;
   }

   // image pixels at each corner ignored by the pixel comparisons
   private static final int CORNER = 32;
   private static final int CELL_BORDER = new Color(128, 128, 128).getRGB();
}
