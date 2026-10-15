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
 * Bug #78622: the exported year view keeps every month cell's outer edges inside the
 * calendar's border instead of laying them out on the border pixel.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSCalendarYearViewBorderTest {
   @Test
   void singleYearViewKeepsEveryMonthCellBorderInside() throws Exception {
      BufferedImage img = paint(CalendarVSAssemblyInfo.SINGLE_CALENDAR_MODE, 300);

      // 4 months per row, each with a left and right edge
      assertEquals(8, grayRuns(img, true, FIRST_ROW_Y));
      // 3 rows of months, each with a top and bottom edge
      assertEquals(6, grayRuns(img, false, FIRST_COLUMN_X));
      assertEquals(BORDER, img.getRGB(img.getWidth() - 1, FIRST_ROW_Y));
      assertEquals(BORDER, img.getRGB(FIRST_COLUMN_X, img.getHeight() - 1));
   }

   @Test
   void doubleYearViewKeepsEveryMonthCellBorderInside() throws Exception {
      BufferedImage img = paint(CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE, 600);

      assertEquals(16, grayRuns(img, true, FIRST_ROW_Y + DOUBLE_HEADER));
      assertEquals(6, grayRuns(img, false, FIRST_COLUMN_X));
      assertEquals(BORDER, img.getRGB(img.getWidth() - 1, FIRST_ROW_Y + DOUBLE_HEADER));
   }

   private static BufferedImage paint(int viewMode, int width) throws Exception {
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

      int line = StyleConstants.THIN_LINE;
      VSFormat fmt = info.getFormat().getUserDefinedFormat();
      fmt.setBordersValue(new Insets(line, line, line, line));
      fmt.setBorders(new Insets(line, line, line, line));
      BorderColors red = new BorderColors(Color.RED, Color.RED, Color.RED, Color.RED);
      fmt.setBorderColorsValue(red);
      fmt.setBorderColors(red);

      VSCalendar calendar = new VSCalendar(new Viewsheet());
      calendar.setTheme(new FlexTheme(PortalThemesManager.getColorTheme()));
      calendar.setAssemblyInfo(info);
      calendar.setPixelSize(new Dimension(width, 300));
      return (BufferedImage) calendar.getImage(false);
   }

   // number of separate runs of the month cell border colour along one row or column
   private static int grayRuns(BufferedImage img, boolean alongRow, int at) {
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

   private static final int CELL_BORDER = new Color(128, 128, 128).getRGB();
   private static final int BORDER = Color.RED.getRGB();
   // below the 1px border, the 18px year header and the cell inset, above the month text
   private static final int FIRST_ROW_Y = 24;
   // right of the 1px border and the first month's left edge
   private static final int FIRST_COLUMN_X = 6;
   // the range title strip a double calendar draws above both years
   private static final int DOUBLE_HEADER = 5;
}
