package inetsoft.report.io.viewsheet;

import inetsoft.report.StyleConstants;
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
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A card with a medium border and an inset keeps that border at the card edge; the outer cells no
 * longer copy it, or a second frame would appear at the grid edge. The plain lens rules every
 * cell edge thin, so a cell that does not copy the card border keeps THIN_LINE.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableExportBorderCopyTest {
   @Test
   void anInsetEdgeKeepsTheBorderOffTheOuterCells() {
      RecordingTableHelper helper = write(COMFORTABLE);

      assertEquals(StyleConstants.THIN_LINE, left(helper, 0, 0));
      assertEquals(StyleConstants.THIN_LINE, right(helper, 0, 2));
   }

   @Test
   void withoutAnInsetTheOuterCellsCopyTheBorder() {
      RecordingTableHelper helper = write(NONE);

      assertEquals(StyleConstants.MEDIUM_LINE, left(helper, 0, 0));
      assertEquals(StyleConstants.MEDIUM_LINE, right(helper, 0, 2));
   }

   @Test
   void anEdgeWithoutAnInsetStillCopiesTheBorder() {
      RecordingTableHelper helper = write(new Insets(0, 24, 0, 0));

      assertEquals(StyleConstants.THIN_LINE, left(helper, 0, 0), "the left edge has an inset");
      assertEquals(StyleConstants.MEDIUM_LINE, right(helper, 0, 2), "the right edge has none");
   }

   private static RecordingTableHelper write(Insets inset) {
      TableVSAssembly table = table(inset, new Dimension(400, 250), 100, 100, 100);
      int medium = StyleConstants.MEDIUM_LINE;
      table.getTableDataVSAssemblyInfo().getFormat().getUserDefinedFormat()
         .setBorders(new Insets(medium, medium, medium, medium));
      RecordingTableHelper helper = new RecordingTableHelper(table, exporter(inset, false));
      helper.write(table, lens(table, 3));
      return helper;
   }

   private static int left(RecordingTableHelper helper, int row, int col) {
      return helper.cell(row, col).format().getUserDefinedFormat().getBorders().left;
   }

   private static int right(RecordingTableHelper helper, int row, int col) {
      return helper.cell(row, col).format().getUserDefinedFormat().getBorders().right;
   }
}
