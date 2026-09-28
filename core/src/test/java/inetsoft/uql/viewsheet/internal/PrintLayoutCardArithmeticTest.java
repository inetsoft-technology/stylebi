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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.ReportSheet;
import inetsoft.report.internal.TableElementDef;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The converter sizes a padded table's columns to the grid, the card less its side insets, and
 * counts the top and bottom insets in the height it predicts.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintLayoutCardArithmeticTest {
   @Test
   void theLastColumnGivesBackTheLensFillThenFillsTheGrid() throws Exception {
      // the lens filled its last column to the 400 card; 32 of that fill is inset
      assertArrayEquals(new int[] { 100, 100, 168 },
                        new PrintLayoutConverterFixture().inset(16, 16, 16, 16).columnWidths());
   }

   @Test
   void withoutAnInsetTheLastColumnKeepsTheLensFill() throws Exception {
      assertArrayEquals(new int[] { 100, 100, 200 }, new PrintLayoutConverterFixture().columnWidths());
   }

   @Test
   void setColumnsFillTheGrid() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      fixture.setColumnWidths(80);

      // 240 of columns in the 368 grid: the last takes the 128 left over
      assertArrayEquals(new int[] { 80, 80, 208 }, fixture.columnWidths());
   }

   @Test
   void withoutAnInsetSetColumnsFillTheCard() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture();
      fixture.setColumnWidths(80);

      assertArrayEquals(new int[] { 80, 80, 240 }, fixture.columnWidths());
   }

   @Test
   void theTabsHeightAddsTheTopAndBottomInsets() throws Exception {
      int plain = new PrintLayoutConverterFixture().printHeight();
      int inset = new PrintLayoutConverterFixture().inset(16, 16, 12, 16).printHeight();

      assertEquals(plain + 16 + 12, inset);
   }

   @Test
   void theFitPageChoiceComparesTheColumnsWithTheGrid() throws Exception {
      // 100 + 100 + 168 fill the 368 grid, so the table is not switched to fit page width
      TableElementDef table =
         new PrintLayoutConverterFixture().inset(16, 16, 16, 16).tableElement();

      assertEquals(ReportSheet.TABLE_FIT_CONTENT_PAGE, table.getLayout());
   }

   @Test
   void withoutAnInsetTheFitPageChoiceComparesTheColumnsWithTheCard() throws Exception {
      assertEquals(ReportSheet.TABLE_FIT_CONTENT_PAGE,
                   new PrintLayoutConverterFixture().tableElement().getLayout());
   }
}
