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
import java.awt.geom.Rectangle2D;

import static inetsoft.report.io.viewsheet.TableExportFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Cells and the title start at the grid's origin, inside the card inset, and the cells stop at
 * the grid's bottom. The table sits at (40, 60) with a 20px title.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableExportGridOriginTest {
   @Test
   void cellsStartAtTheGridOrigin() {
      Rectangle2D first = write(COMFORTABLE, new Dimension(400, 250), 3).cell(0, 0).bounds();

      assertEquals(56, first.getX(), 0.001, "40 + 16");
      assertEquals(96, first.getY(), 0.001, "60 + 16 + the 20px title");
   }

   @Test
   void withoutAnInsetCellsStartAtTheCard() {
      Rectangle2D first = write(NONE, new Dimension(400, 250), 3).cell(0, 0).bounds();

      assertEquals(40, first.getX(), 0.001);
      assertEquals(80, first.getY(), 0.001);
   }

   @Test
   void theTitleStartsAtTheGridOriginAndSpansTheGrid() {
      Rectangle title = write(COMFORTABLE, new Dimension(400, 250), 3).title;

      assertEquals(new Point(56, 76), title.getLocation());
      assertEquals(368, title.width, "400 - 16 - 16");
   }

   @Test
   void withoutAnInsetTheTitleSpansTheCard() {
      Rectangle title = write(NONE, new Dimension(400, 250), 3).title;

      assertEquals(new Point(40, 60), title.getLocation());
      assertEquals(400, title.width);
   }

   // 20 rows overflow a 100px card; no cell reaches past 60 + 100 - 16
   @Test
   void cellsStopAtTheGridBottom() {
      double bottom = write(COMFORTABLE, new Dimension(400, 100), 20).cells.stream()
         .mapToDouble(c -> c.bounds().getMaxY()).max().orElseThrow();

      assertEquals(144, bottom, 0.001);
   }

   @Test
   void withoutAnInsetCellsStopAtTheCardBottom() {
      double bottom = write(NONE, new Dimension(400, 100), 20).cells.stream()
         .mapToDouble(c -> c.bounds().getMaxY()).max().orElseThrow();

      assertEquals(160, bottom, 0.001);
   }

   @Test
   void anAsymmetricInsetMovesOnlyItsOwnEdges() {
      RecordingTableHelper helper = write(new Insets(0, 24, 0, 0), new Dimension(400, 250), 3);

      assertEquals(64, helper.cell(0, 0).bounds().getX(), 0.001, "40 + 24");
      assertEquals(80, helper.cell(0, 0).bounds().getY(), 0.001, "no top inset");
      assertEquals(376, helper.title.width, "only the left edge comes off");
   }

   @Test
   void aCardSmallerThanItsInsetClampsTheGrid() {
      TableVSAssembly table = table(COMFORTABLE, new Dimension(20, 20), 100, 100, 100);
      RecordingTableHelper helper = new RecordingTableHelper(table, exporter(COMFORTABLE, false));

      assertDoesNotThrow(() -> helper.write(table, lens(table, 3)));
      Rectangle grid = helper.getGridBounds(table.getTableDataVSAssemblyInfo());
      assertEquals(0, grid.width);
      assertEquals(0, grid.height);
   }

   private static RecordingTableHelper write(Insets inset, Dimension size, int rows) {
      TableVSAssembly table = table(inset, size, 100, 100, 100);
      VSTableLens lens = lens(table, rows);
      RecordingTableHelper helper = new RecordingTableHelper(table, exporter(inset, false));
      helper.write(table, lens);
      return helper;
   }
}
