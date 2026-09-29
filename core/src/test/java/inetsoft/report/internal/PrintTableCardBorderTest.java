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
package inetsoft.report.internal;

import inetsoft.report.ReportSheet;
import inetsoft.report.StyleConstants;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Print layout hides an outer cell's own border where the table border would double it. An edge
 * with a card inset puts the two apart, so it keeps the cell border.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintTableCardBorderTest {
   @Test
   void theLeftAndBottomInsetsKeepTheOuterCellBorders() throws Exception {
      TablePaintable region = new PrintTableFixture().inset(16, 16, 16).regions().get(0);
      int[][] ver = matrix(region, "ver");
      int[][] hor = matrix(region, "hor");

      assertEquals(StyleConstants.THIN_LINE, ver[1][0], "the first column's left border");
      assertEquals(StyleConstants.THIN_LINE, hor[hor.length - 2][1], "the last row's bottom border");
   }

   @Test
   void withoutAnInsetTheTableBorderReplacesTheOuterCellBorders() throws Exception {
      TablePaintable region = new PrintTableFixture().regions().get(0);
      int[][] ver = matrix(region, "ver");
      int[][] hor = matrix(region, "hor");

      assertEquals(StyleConstants.NO_BORDER, ver[1][0]);
      assertEquals(StyleConstants.NO_BORDER, hor[hor.length - 2][1]);
   }

   @Test
   void theColumnsAreMatchedWithTheGridNotTheCard() {
      // borderless columns fill the 368 grid; a match takes 1pt off the last to end with the title
      TablePaintable region =
         new PrintTableFixture().inset(16, 16, 16).noCellBorders().regions().get(0);

      assertEquals(368 / 3f - 1, region.getColWidth(2), 0.01);
   }

   @Test
   void withoutAnInsetTheColumnsAreMatchedWithTheCard() {
      TablePaintable region = new PrintTableFixture().noCellBorders().regions().get(0);

      assertEquals(400 / 3f - 1, region.getColWidth(2), 0.01);
   }

   @Test
   void theRightInsetKeepsTheLastColumnsBorderWhenTheColumnsFillTheGrid() throws Exception {
      // 368 of columns fills the 368 grid; refreshLastCol takes 1pt off the last, and init's
      // recheck still matches, which is the case that hides the right cell border
      TablePaintable region = new PrintTableFixture().inset(16, 16, 16)
         .layout(ReportSheet.TABLE_FIT_CONTENT).widths(122, 123, 123).regions().get(0);
      int[][] ver = matrix(region, "ver");

      assertEquals(StyleConstants.THIN_LINE, ver[1][ver[1].length - 2]);
   }

   @Test
   void withoutAnInsetColumnsFillingTheCardHideTheRightCellBorder() throws Exception {
      // 400 of columns fills the 400 card, and still matches after refreshLastCol's 1pt
      TablePaintable region = new PrintTableFixture()
         .layout(ReportSheet.TABLE_FIT_CONTENT).widths(133, 133, 134).regions().get(0);
      int[][] ver = matrix(region, "ver");

      assertEquals(StyleConstants.NO_BORDER, ver[1][ver[1].length - 2]);
   }

   static int[][] matrix(TablePaintable region, String name) throws Exception {
      Field field = TablePaintable.class.getDeclaredField(name);
      field.setAccessible(true);
      return (int[][]) field.get(region);
   }
}
