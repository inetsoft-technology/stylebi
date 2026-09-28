package inetsoft.report.internal;

import inetsoft.report.ReportSheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A print-layout table with a card inset lays its columns out in the grid, the card less its
 * side insets, on every page.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintTableCardGridTest {
   @Test
   void theCellsStartInsideTheLeftInset() {
      TablePaintable region = new PrintTableFixture().inset(16, 16, 16).regions().get(0);

      // the box at 57 + 16, plus the first column's 1pt left cell border
      assertEquals(74, region.getPrintBounds(1, 0, false).x, 0.01);
   }

   @Test
   void withoutAnInsetTheCellsStartAtTheCardEdge() {
      TablePaintable region = new PrintTableFixture().regions().get(0);

      assertEquals(58, region.getPrintBounds(1, 0, false).x, 0.01);
   }

   @Test
   void fitPageWidthScalesTheColumnsToTheGrid() {
      // the 368 grid, less the right cell border and its 1pt gap
      assertEquals(366, totalWidth(new PrintTableFixture().inset(16, 16, 16).regions().get(0)),
                   0.01);
   }

   @Test
   void withoutAnInsetFitPageWidthScalesTheColumnsToTheCard() {
      assertEquals(398, totalWidth(new PrintTableFixture().regions().get(0)), 0.01);
   }

   @Test
   void fitContentsCutsTheColumnsAtTheGridWidth() {
      // 390 fits the 400 card but not the 368 grid
      List<TablePaintable> regions = new PrintTableFixture().inset(16, 16, 16)
         .layout(ReportSheet.TABLE_FIT_CONTENT).widths(130, 130, 130).regions();

      assertEquals(List.of(2, 1), regions.stream().map(r -> r.getTableRegion().width).toList());
   }

   @Test
   void withoutAnInsetFitContentsKeepsOneSegment() {
      List<TablePaintable> regions = new PrintTableFixture()
         .layout(ReportSheet.TABLE_FIT_CONTENT).widths(130, 130, 130).regions();

      assertEquals(List.of(3), regions.stream().map(r -> r.getTableRegion().width).toList());
   }

   @Test
   void aLaterPageCutsAtTheGridWidthToo() {
      // 60 rows reach a third page, whose area comes from the next-page frame, not
      // calcRemainingArea
      List<TablePaintable> regions = new PrintTableFixture().inset(16, 16, 16).rows(60)
         .layout(ReportSheet.TABLE_FIT_CONTENT).widths(130, 130, 130).regions();

      assertTrue(regions.stream().anyMatch(r -> r.getTableRegion().y > 34),
                 "the table reaches rows past the first page");
      assertTrue(regions.stream().allMatch(r -> r.getTableRegion().width <= 2),
                 "no segment is wider than the grid");
   }

   @Test
   void calcHeaderWidthShrinksAgainstTheGrid() {
      // header total 380 sits between the 368 grid and 400 - 1, so only a grid-aware check
      // shrinks it
      List<TablePaintable> regions = new PrintTableFixture().inset(16, 16, 16)
         .widths(380, 100, 100).headerCols(1).layout(ReportSheet.TABLE_FIT_CONTENT).regions();

      assertEquals(368 * 5 / 6f, regions.get(0).getColWidth(0), 0.01,
                   "the header column shrinks to 5/6 of the 368 grid");

      for(TablePaintable region : regions) {
         for(int c = 1; c < 3; c++) {
            assertTrue(region.getColWidth(c) > 0, "data column " + c + " has width left");
         }
      }
   }

   @Test
   void withoutAnInsetCalcHeaderWidthUsesTheFullPage() {
      List<TablePaintable> regions = new PrintTableFixture()
         .widths(380, 100, 100).headerCols(1).layout(ReportSheet.TABLE_FIT_CONTENT).regions();

      assertEquals(380, regions.get(0).getColWidth(0), 0.01,
                   "380 is under 400 - 1, so today's code does not shrink it");
   }

   @Test
   @Timeout(60)
   void aCardNarrowerThanItsInsetStillPrints() {
      // 250 + 250 of side inset leaves the 400 card no grid at all
      assertFalse(new PrintTableFixture().inset(250, 16, 250).regions().isEmpty());
   }

   private static float totalWidth(TablePaintable region) {
      float total = 0;

      for(int c = 0; c < 3; c++) {
         total += region.getColWidth(c);
      }

      return total;
   }
}
