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
package inetsoft.report.lens;

import inetsoft.report.painter.BarPresenter;
import inetsoft.report.painter.IconCounterPresenter;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.DecimalFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A table lens gives a Bar presenter the formatted text of a negative cell, which the bar cannot
 * draw, and leaves every other cell's presenter as it was.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AttributeTableLensValueTextTest {
   private static final int SALES = 1;

   @Test
   void aNegativeCellGetsItsFormattedText() {
      AttributeTableLens lens = lens();
      lens.setFormat(SALES, new DecimalFormat("#,##0.0"));

      assertEquals("-1,013.7", textOf(lens, 1));
   }

   @Test
   void withNoFormatTheTextIsTheValue() {
      assertEquals("-1013.7", textOf(lens(), 1));
   }

   @Test
   void aNaNCellGetsItsText() {
      assertEquals("NaN", textOf(lens(), 4));
   }

   @Test
   void aTextCellIsAPlainStringNotAPresenter() {
      // the presenter takes numbers only, so a text or date value in the column shows as its text
      AttributeTableLens lens = lens();
      lens.setFormat(SALES, new DecimalFormat("#,##0.0"));

      assertEquals("n/a", lens.getObject(5, SALES));
      assertEquals("2024-01-01", lens.getObject(6, SALES));
   }

   @Test
   void aBlankCellStaysEmpty() {
      assertNull(lens().getObject(7, SALES));
   }

   @Test
   void positiveAndZeroCellsKeepThePresenterAsItIs() {
      AttributeTableLens lens = lens();
      BarPresenter shared = (BarPresenter) lens.getPresenter(2, SALES);

      assertNull(((BarPresenter) ((PresenterPainter) lens.getObject(2, SALES)).getPresenter()).valueText());
      assertNull(((BarPresenter) ((PresenterPainter) lens.getObject(3, SALES)).getPresenter()).valueText());
      assertNotNull(shared);
      assertNull(shared.valueText(), "the column's presenter is never changed");
   }

   @Test
   void aNegativeCellDoesNotChangeTheColumnsPresenter() {
      AttributeTableLens lens = lens();
      BarPresenter shared = (BarPresenter) lens.getPresenter(1, SALES);

      lens.getObject(1, SALES);

      assertNull(shared.valueText());
   }

   @Test
   void aNegativeStarsCellGetsItsTextAndOtherCellsKeepThePresenter() {
      // Bug #82288: a Stars presenter has no star for a negative value either
      AttributeTableLens lens = lens();
      IconCounterPresenter shared = new IconCounterPresenter();
      lens.setPresenter(SALES, shared);
      lens.setFormat(SALES, new DecimalFormat("#,##0.0"));

      PresenterPainter negative = (PresenterPainter) lens.getObject(1, SALES);
      PresenterPainter positive = (PresenterPainter) lens.getObject(2, SALES);

      assertEquals("-1,013.7", ((IconCounterPresenter) negative.getPresenter()).valueText());
      assertSame(shared, positive.getPresenter());
      assertNull(shared.valueText(), "the column's presenter is never changed");
   }

   private static String textOf(AttributeTableLens lens, int row) {
      PresenterPainter painter = (PresenterPainter) lens.getObject(row, SALES);
      return ((BarPresenter) painter.getPresenter()).valueText();
   }

   private static AttributeTableLens lens() {
      AttributeTableLens lens = new AttributeTableLens(new DefaultTableLens(new Object[][] {
         { "Name", "Sales" },
         { "a", -1013.7 },
         { "b", 50.0 },
         { "c", 0.0 },
         { "d", Double.NaN },
         { "e", "n/a" },
         { "f", "2024-01-01" },
         { "g", null },
      }));
      lens.setPresenter(SALES, new BarPresenter());
      return lens;
   }
}
