package inetsoft.report.io.viewsheet.html;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An HTML table with a card inset nests its title and data in a box inside the card's div; at a
 * zero inset no box is written.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HTMLTableCardInsetTest {
   private static final Insets INSET = new Insets(16, 16, 16, 16);
   private static final Insets NONE = new Insets(0, 0, 0, 0);

   @Test
   void theGridBoxSitsInsideTheCard() {
      assertTrue(writeTable(INSET, false).contains(
         "<div style='position:absolute;left:16px;top:16px;width:368px;height:218px'>"));
   }

   @Test
   void withoutAnInsetThereIsNoGridBox() {
      assertFalse(writeTable(NONE, false).contains("left:0px;top:0px;width:"));
   }

   // 250 - 20 title - 32 inset
   @Test
   void theDataHeightIsTheGrids() {
      assertTrue(writeTable(INSET, false).contains(";height:198'>"));
      assertTrue(writeTable(NONE, false).contains(";height:230'>"));
   }

   // 3 x 100 columns plus 32
   @Test
   void aShrunkCardWrapsTheColumnsAndTheInset() {
      assertTrue(writeTable(INSET, true).contains("width:332.0px"));
      assertTrue(writeTable(NONE, true).contains("width:300.0px"));
   }

   @Test
   void theCrosstabLastColumnFillsTheGrid() {
      assertEquals(168, crosstabLastColumn(INSET));
      assertEquals(200, crosstabLastColumn(NONE));
   }

   private static String writeTable(Insets inset, boolean shrink) {
      TableVSAssembly table = new TableVSAssembly(new Viewsheet(), "Table1");
      table.getViewsheet().addAssembly(table);
      VSTableLens lens = configure(table, inset, shrink);
      HTMLCoordinateHelper vHelper = new HTMLCoordinateHelper();
      vHelper.setViewsheet(table.getViewsheet());
      HTMLTableHelper helper = new HTMLTableHelper(vHelper, table.getViewsheet(), table);
      helper.setCardInset(inset);
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      helper.write(writer, table, lens);
      writer.flush();
      return out.toString();
   }

   private static int crosstabLastColumn(Insets inset) {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(new Viewsheet(), "Crosstab1");
      crosstab.getViewsheet().addAssembly(crosstab);
      VSTableLens lens = configure(crosstab, inset, false);
      HTMLCoordinateHelper vHelper = new HTMLCoordinateHelper();
      vHelper.setViewsheet(crosstab.getViewsheet());
      HTMLCrosstabHelper helper = new HTMLCrosstabHelper(vHelper, crosstab.getViewsheet(),
                                                         crosstab);
      helper.setCardInset(inset);
      helper.write(new PrintWriter(new StringWriter()), crosstab, lens);
      return helper.columnWidths[helper.columnWidths.length - 1];
   }

   private static VSTableLens configure(TableDataVSAssembly table, Insets inset, boolean shrink) {
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();
      info.setPixelOffset(new Point(40, 60));
      info.setPixelSize(new Dimension(400, 250));
      info.setPadding(inset);
      info.setTitleVisibleValue(true);
      info.setTitleHeightValue(20);
      info.setUserTitleHeight(true);
      info.setShrinkValue(shrink);

      for(int i = 0; i < 3; i++) {
         info.setColumnWidthValue(i, 100);
      }

      DefaultTableLens defaultLens = new DefaultTableLens(new Object[][] {
         { "A", "B", "C" },
         { 1, 2, 3 }
      });
      defaultLens.setLineWrap(false);
      VSTableLens lens = new VSTableLens(defaultLens);
      lens.initTableGrid(info);
      return lens;
   }
}
