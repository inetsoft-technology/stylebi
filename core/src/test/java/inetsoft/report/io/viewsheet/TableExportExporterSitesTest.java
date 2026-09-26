package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.io.viewsheet.html.HTMLVSExporter;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;

import static inetsoft.report.io.viewsheet.TableExportFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Expansion, the match-layout region and the bottom-tabs shift size a padded table's card as
 * its grid plus the inset.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableExportExporterSitesTest {
   @Test
   void anExpandedCardIsTheRowsPlusTheVerticalInset() {
      TableVSAssembly padded = table(COMFORTABLE, new Dimension(400, 250), 100, 100, 100);
      TableVSAssembly plain = table(NONE, new Dimension(400, 250), 100, 100, 100);

      assertEquals(exporter().getExpandTableHeight(plain, lens(plain, 40)) + 32,
                   exporter().getExpandTableHeight(padded, lens(padded, 40)));
   }

   // 600 of columns in a 400 card: the card widens to the columns plus 32
   @Test
   void anExpandedCardIsTheColumnsPlusTheHorizontalInset() {
      assertEquals(632, expandedWidth(COMFORTABLE, 200, 200, 200));
      assertEquals(600, expandedWidth(NONE, 200, 200, 200));
   }

   // 390 of columns fit the 400 card but not the 368 grid
   @Test
   void columnsThatFitTheCardButNotTheGridStillExpand() {
      assertEquals(422, expandedWidth(COMFORTABLE, 100, 100, 190));
      assertEquals(400, expandedWidth(NONE, 100, 100, 190));
   }

   // three 100px columns: 300 reaches the 278 grid at the 3rd; the 310 card is never reached,
   // so the count runs one past the last column
   @Test
   void theMatchLayoutRegionIsTheGridsColumns() {
      assertEquals(3, regionCols(COMFORTABLE));
      assertEquals(4, regionCols(NONE));
   }

   @Test
   void theMatchLayoutRegionIsTheGridsRows() {
      TableVSAssembly padded = table(COMFORTABLE, new Dimension(400, 250), 100, 100, 100);
      TableVSAssembly shorter = table(NONE, new Dimension(400, 218), 100, 100, 100);
      HTMLVSExporter exporter = exporter();
      exporter.setMatchLayout(true);

      assertEquals(exporter.getRegionRowCount(shorter, lens(shorter, 30)),
                   exporter.getRegionRowCount(padded, lens(padded, 30)));
   }

   @Test
   void aShrunkBottomTabsCardMovesDownLessByTheVerticalInset() {
      assertEquals(shift(NONE) - 32, shift(COMFORTABLE));
   }

   // a 10px card cannot hold its 32px inset, 20px title and header: no rows, and no throw
   @Test
   void aCardShorterThanItsInsetFitsNoRows() {
      TableVSAssembly table = table(COMFORTABLE, new Dimension(400, 10), 100, 100, 100);
      HTMLVSExporter exporter = exporter();
      exporter.setMatchLayout(true);

      assertEquals(0, exporter.getRegionRowCount(table, lens(table, 30)));
   }

   private static HTMLVSExporter exporter() {
      return new HTMLVSExporter(new ByteArrayOutputStream());
   }

   private static int expandedWidth(Insets inset, double... widths) {
      TableVSAssembly table = table(inset, new Dimension(400, 250), widths);
      exporter().expandTable(table, lens(table, 3), false);
      return table.getPixelSize().width;
   }

   private static int regionCols(Insets inset) {
      TableVSAssembly table = table(inset, new Dimension(310, 250), 100, 100, 100);
      HTMLVSExporter exporter = exporter();
      exporter.setMatchLayout(true);
      return exporter.getRegionColCount(table, lens(table, 3));
   }

   // a shrunk 3-row table in a 400px-tall slot of a bottom-tabs container
   private static int shift(Insets inset) {
      TableVSAssembly real = table(inset, new Dimension(400, 400), 100, 100, 100);
      TableDataVSAssemblyInfo info = real.getTableDataVSAssemblyInfo();
      info.setShrinkValue(true);
      VSTableLens lens = lens(real, 3);

      TabVSAssemblyInfo tabInfo = mock(TabVSAssemblyInfo.class);
      when(tabInfo.isBottomTabs()).thenReturn(true);
      TabVSAssembly tabs = mock(TabVSAssembly.class);
      when(tabs.getVSAssemblyInfo()).thenReturn(tabInfo);
      TableVSAssembly table = mock(TableVSAssembly.class);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      when(table.getContainer()).thenReturn(tabs);

      VSTableDataHelper.applyShrunkBottomTabsShift(table, lens, inset);
      return info.getPixelOffset().y - 60;
   }
}
