package inetsoft.report.io.viewsheet;

import inetsoft.report.io.viewsheet.excel.ExcelContext;
import inetsoft.report.io.viewsheet.excel.ExcelCrosstabHelper;
import inetsoft.report.io.viewsheet.excel.ExcelTableHelper;
import inetsoft.report.io.viewsheet.excel.PoiExcelVSExporter;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.*;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Through either Excel helper a padded table's grid is its card, so no Excel geometry can move.
 */
class ExcelTableCardInsetTest {
   @Test
   void theExcelTableGridIsTheCard() {
      XSSFWorkbook book = new XSSFWorkbook();
      ExcelTableHelper helper =
         new ExcelTableHelper(book, book.createSheet(), Mockito.mock(VSAssembly.class));
      helper.setExporter(new PoiExcelVSExporter(Mockito.mock(ExcelContext.class),
                                                new ByteArrayOutputStream()));
      TableVSAssemblyInfo info = padded(helper);

      assertEquals(helper.getCardBounds(info), helper.getGridBounds(info));
   }

   @Test
   void theExcelCrosstabGridIsTheCard() {
      XSSFWorkbook book = new XSSFWorkbook();
      ExcelCrosstabHelper helper =
         new ExcelCrosstabHelper(book, book.createSheet(), Mockito.mock(VSAssembly.class));
      helper.setExporter(new PoiExcelVSExporter(Mockito.mock(ExcelContext.class),
                                                new ByteArrayOutputStream()));
      TableVSAssemblyInfo info = padded(helper);

      assertEquals(helper.getCardBounds(info), helper.getGridBounds(info));
   }

   private static TableVSAssemblyInfo padded(VSTableDataHelper helper) {
      Viewsheet vs = Mockito.mock(Viewsheet.class);
      TableVSAssemblyInfo info = Mockito.mock(TableVSAssemblyInfo.class);
      when(info.getPadding()).thenReturn(new Insets(16, 16, 16, 16));
      when(info.getPixelOffset()).thenReturn(new Point(40, 60));
      when(vs.getPixelPosition(any(Point.class))).thenReturn(new Point(40, 60));
      when(vs.getPixelSize(info)).thenReturn(new Dimension(400, 250));
      helper.setViewsheet(vs);
      return info;
   }
}
