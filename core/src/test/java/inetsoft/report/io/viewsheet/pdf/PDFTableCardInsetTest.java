package inetsoft.report.io.viewsheet.pdf;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A table too tall for one PDF page is capped to the rows that fit; the height written back is
 * the card's, so it carries the vertical inset on top of those rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PDFTableCardInsetTest {
   // 2000 rows pass the 15000px page cap
   @Test
   void theCappedCardCarriesTheVerticalInset() {
      assertEquals(cappedHeight(new Insets(0, 0, 0, 0)) + 32,
                   cappedHeight(new Insets(16, 16, 16, 16)));
   }

   private static int cappedHeight(Insets inset) {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setPixelOffset(new Point(0, 0));
      info.setPixelSize(new Dimension(400, 250));
      info.setPadding(inset);
      // pin this as the customer's own height, not the type's density default
      info.setUserTitleHeight(true);

      Object[][] data = new Object[2001][];
      data[0] = new Object[] { "A", "B", "C" };

      for(int r = 1; r < data.length; r++) {
         data[r] = new Object[] { r, r, r };
      }

      DefaultTableLens data0 = new DefaultTableLens(data);
      // no font is set on this bare fixture; skip wrapping so line-count sizing
      // doesn't need font metrics
      data0.setLineWrap(false);
      VSTableLens lens = new VSTableLens(data0);
      lens.initTableGrid(info);

      VSExporter exporter = mock(VSExporter.class);
      when(exporter.getTableCardInset(any())).thenAnswer(i -> (Insets) inset.clone());
      PDFTableHelper helper = new PDFTableHelper(
         new PDFCoordinateHelper(new ByteArrayOutputStream()), vs, table);
      helper.setExporter(exporter);
      helper.getTableRectangle(info, lens);
      return info.getPixelSize().height;
   }
}
