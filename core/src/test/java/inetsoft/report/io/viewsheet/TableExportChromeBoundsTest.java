package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.io.viewsheet.html.HTMLCoordinateHelper;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;

import static inetsoft.report.io.viewsheet.TableExportFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The chrome, meaning the border, the background and the round clip, stays on the card whatever
 * the inset. A shrunk card wraps its columns plus the inset.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableExportChromeBoundsTest {
   @Test
   void theChromeIsTheCard() {
      assertEquals(new Rectangle2D.Double(40, 60, 400, 250),
                   chrome(COMFORTABLE, new Dimension(400, 250), false, 3).getBounds2D());
   }

   // 3 x 100 columns, unfilled when shrunk
   @Test
   void aShrunkCardWrapsTheColumnsAndTheInset() {
      assertEquals(332, chrome(COMFORTABLE, new Dimension(400, 250), true, 3).getWidth(), 0.001);
      assertEquals(300, chrome(NONE, new Dimension(400, 250), true, 3).getWidth(), 0.001);
   }

   @Test
   void aShrunkCardIsTallerByTheVerticalInset() {
      double withInset = chrome(COMFORTABLE, new Dimension(400, 250), true, 3).getHeight();
      double without = chrome(NONE, new Dimension(400, 250), true, 3).getHeight();

      assertEquals(32, withInset - without, 0.001);
   }

   private static Rectangle2D chrome(Insets inset, Dimension size, boolean shrink, int rows) {
      TableVSAssembly table = table(inset, size, 100, 100, 100);
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();
      info.setShrinkValue(shrink);
      VSTableLens lens = lens(table, rows);
      RecordingTableHelper helper = new RecordingTableHelper(table, exporter(inset, false));
      helper.write(table, lens);
      HTMLCoordinateHelper vHelper = new HTMLCoordinateHelper();
      vHelper.setViewsheet(table.getViewsheet());
      return helper.getObjectPixelBounds(info, lens, vHelper);
   }
}
