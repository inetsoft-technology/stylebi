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
      String text = IntStream.rangeClosed(1, 240).mapToObj(i -> "w" + i)
         .collect(Collectors.joining(" "));
      List<Integer> heights = heights(padded().keepRowsWhole().wrappedRow(3, text).regions());
      List<Integer> unkept = heights(padded().wrappedRow(3, text).regions());

      assertTrue(heights.stream().mapToInt(Integer::intValue).sum() > 25,
                 "the tall row's pieces add rows: " + heights);
      // only the tall row is cut; each 38pt row that would straddle a break moves whole instead
      assertTrue(heights.stream().mapToInt(Integer::intValue).sum()
                 < unkept.stream().mapToInt(Integer::intValue).sum(),
                 "the short rows stay whole: " + heights + " vs " + unkept);
   }

   private static PrintTableFixture padded() {
      return new PrintTableFixture().rows(25).rowHeight(38).cellInsets(new Insets(12, 4, 12, 4));
   }

   private static List<Integer> heights(List<TablePaintable> regions) {
      return regions.stream().map(r -> r.getTableRegion().height).toList();
   }
}
