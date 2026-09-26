package inetsoft.report.io.viewsheet;

import inetsoft.report.Hyperlink;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;

import static inetsoft.report.io.viewsheet.TableExportFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The rows and columns an export fits into a padded table are those of the grid, not the card.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableExportBudgetTest {
   // 200 + 200 overflows the 368 grid: the column widths cut the second column to 168 and leave
   // the third at 100, and the cull clips that third column to nothing at the grid edge
   @Test
   void theHorizontalCullClipsAtTheGrid() {
      assertArrayEquals(new int[] { 200, 168, 0 }, culledWidths(COMFORTABLE));
   }

   // without an inset the second column fills the 400 card and the third is already 0
   @Test
   void withoutAnInsetTheCullClipsAtTheCard() {
      assertArrayEquals(new int[] { 200, 200, 0 }, culledWidths(NONE));
   }

   @Test
   void theVisibleRowsAreThoseOfTheGrid() {
      TableVSAssembly padded = table(COMFORTABLE, new Dimension(400, 250), 100, 100, 100);
      TableVSAssembly shorter = table(NONE, new Dimension(400, 218), 100, 100, 100);

      assertEquals(
         VSTableHelper.getVisibleRowCount(shorter.getTableDataVSAssemblyInfo(), lens(shorter, 30), NONE),
         VSTableHelper.getVisibleRowCount(padded.getTableDataVSAssemblyInfo(), lens(padded, 30),
                                          COMFORTABLE));
   }

   @Test
   void theCrosstabDataBudgetIsTheGridHeight() {
      assertEquals(budget(NONE, new Dimension(400, 218)),
                   budget(COMFORTABLE, new Dimension(400, 250)), 0.001);
   }

   private static double budget(Insets inset, Dimension size) {
      CrosstabVSAssembly crosstab = crosstab(inset, size);
      RecordingCrosstabHelper helper = new RecordingCrosstabHelper(crosstab, exporter(inset, false));
      // getTablePixelHeight measures the first cell, which reads the pixel column widths
      helper.columnPixelW = new int[] { 100 };
      return helper.getDataHeightBudget(crosstab.getTableDataVSAssemblyInfo());
   }

   // the clipped widths the cull hands the header row's third cell
   private static int[] culledWidths(Insets inset) {
      TableVSAssembly table = table(inset, new Dimension(400, 250), 200, 200, 100);
      CullRecordingHelper helper = new CullRecordingHelper(table, exporter(inset, false));
      helper.write(table, lens(table, 3));
      return helper.culled;
   }

   private static final class CullRecordingHelper extends RecordingTableHelper {
      CullRecordingHelper(TableDataVSAssembly table, VSExporter exporter) {
         super(table, exporter);
      }

      @Override
      protected void writeTableCell(int startX, int startY, Dimension span,
                                    Rectangle2D pixelbounds, int row, int col,
                                    VSCompositeFormat format, String dispText, Object dispObj,
                                    Hyperlink.Ref hyperlink, VSCompositeFormat parentformat,
                                    Rectangle rec, String fmtPattern, int[] columnPixelW,
                                    Insets padding)
      {
         if(row == 0 && col == 2) {
            culled = columnPixelW.clone();
         }

         super.writeTableCell(startX, startY, span, pixelbounds, row, col, format, dispText,
                              dispObj, hyperlink, parentformat, rec, fmtPattern, columnPixelW,
                              padding);
      }

      int[] culled;
   }

   private static CrosstabVSAssembly crosstab(Insets inset, Dimension size) {
      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      vs.addAssembly(crosstab);
      configure(crosstab.getTableDataVSAssemblyInfo(), inset, size);
      return crosstab;
   }
}
