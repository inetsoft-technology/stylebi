package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.VSTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static inetsoft.report.io.viewsheet.TableExportFixtures.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The last column fills the grid, 400 - 32 = 368 wide, instead of the 400px card. A width-less
 * last column does not keep the fill the lens gave it to reach the card.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableExportColumnWidthsTest {
   @Test
   void theLastColumnFillsTheGrid() {
      assertArrayEquals(new int[] { 100, 100, 168 }, pixelWidths(COMFORTABLE, 100, 100, 100));
   }

   @Test
   void withoutAnInsetTheLastColumnFillsTheCard() {
      assertArrayEquals(new int[] { 100, 100, 200 }, pixelWidths(NONE, 100, 100, 100));
   }

   // 150 + 130 + the 100px default = 380, past the 368 grid: the last column is cut at the grid
   @Test
   void columnsPastTheGridAreCutAtTheGrid() {
      assertArrayEquals(new int[] { 150, 130, 88 },
                        pixelWidths(COMFORTABLE, 150, 130, Double.NaN));
   }

   // the lens fills the width-less last column to the 400 card: 120, and the columns fit
   @Test
   void withoutAnInsetTheColumnsFitTheCard() {
      assertArrayEquals(new int[] { 150, 130, 120 },
                        pixelWidths(NONE, 150, 130, Double.NaN));
   }

   @Test
   void theWrappedLineCountsUseGridWidths() {
      TableVSAssembly table = table(COMFORTABLE, new Dimension(400, 250), 100, 100, 100);
      VSTableLens lens = lens(table, 3);
      new RecordingTableHelper(table, exporter(COMFORTABLE, false)).write(table, lens);

      assertArrayEquals(new double[] { 100, 100, 168 }, lens.getColWidths(), 0.001);
   }

   private static int[] pixelWidths(Insets inset, double... widths) {
      TableVSAssembly table = table(inset, new Dimension(400, 250), widths);
      VSTableLens lens = lens(table, 3);
      RecordingTableHelper helper = new RecordingTableHelper(table, exporter(inset, false));
      helper.write(table, lens);
      return helper.columnPixelW;
   }
}
