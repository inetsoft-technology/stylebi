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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A padded row's height is mostly its cell insets, so splitting it at a page break leaves two
 * pieces too short for its text, and the row prints blank on both pages. A table that keeps its
 * rows whole moves such a row to the next page instead; a row taller than a whole page still
 * splits. The split path builds swappable row lists, hence the swapper configuration.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintTableRowSplitTest {
   @Test
   void aPaddedRowThatDoesNotFitMovesWholeToTheNextPage() {
      // a header and 17 rows fill 684 of the 710; row 18 finds 26 left, short of its 38
      assertEquals(List.of(17, 8), heights(padded().keepRowsWhole().regions()));
   }

   @Test
   void withoutKeepingRowsWholeThePaddedRowIsSplitAcrossTheBreak() {
      // row 18 is split in two, and each page's region counts one piece
      assertEquals(List.of(18, 8), heights(padded().regions()));
   }

   @Test
   void aRowTallerThanAWholePageIsStillSplit() {
      // one word a line, so the row's height follows the font's line height, not its glyph widths
      String text = IntStream.rangeClosed(1, TALL_LINES).mapToObj(i -> "w" + i)
         .collect(Collectors.joining("\n"));
      PrintTableFixture kept = tall(text).keepRowsWhole();
      float lineH = lineHeight(kept.element(), 3);

      assertTrue(TALL_LINES * lineH > PAGE_H,
                 "the tall row outgrows a " + PAGE_H + "pt page at " + lineH + "pt a line");

      List<Integer> heights = heights(kept.regions());
      List<Integer> unkept = heights(tall(text).regions());

      assertTrue(heights.stream().mapToInt(Integer::intValue).sum() > TALL_ROWS,
                 "the tall row's pieces add rows: " + heights);
      // only the tall row is cut; each 38pt row that would straddle a break moves whole instead
      assertTrue(heights.stream().mapToInt(Integer::intValue).sum()
                 < unkept.stream().mapToInt(Integer::intValue).sum(),
                 "the short rows stay whole: " + heights + " vs " + unkept);
   }

   private static PrintTableFixture padded() {
      return new PrintTableFixture().rows(25).rowHeight(38).cellInsets(new Insets(12, 4, 12, 4));
   }

   // enough short rows after the tall one for two page breaks, wherever its last piece ends
   private static PrintTableFixture tall(String text) {
      return padded().rows(TALL_ROWS).wrappedRow(3, text);
   }

   private static List<Integer> heights(List<TablePaintable> regions) {
      return regions.stream().map(r -> r.getTableRegion().height).toList();
   }

   /** The height of one line in the font the engine measures the row's first cell in. */
   private static float lineHeight(TableElementDef element, int row) {
      Font font = element.getBaseTable().getFont(row, 0);
      return Common.getHeight(font == null ? element.getFont() : font);
   }

   private static final int TALL_LINES = 120;
   private static final int TALL_ROWS = 45;
   // US Letter's printable height inside 0.5in margins
   private static final int PAGE_H = 720;
}
