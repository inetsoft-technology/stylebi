package inetsoft.report.io.viewsheet.excel;

import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.Insets;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Excel's fixed row grid cannot represent a card inset, so both Excel exporters resolve zero.
 */
class TableCardInsetOfficeResolverTest {
   @Test
   void excelExportersResolveZero() {
      TableVSAssemblyInfo info = padded();

      assertEquals(new Insets(0, 0, 0, 0),
                   new PoiExcelVSExporter(Mockito.mock(ExcelContext.class),
                                          new ByteArrayOutputStream()).getTableCardInset(info));
      assertEquals(new Insets(0, 0, 0, 0),
                   new OfflineExcelVSExporter(Mockito.mock(ExcelContext.class),
                                              new ByteArrayOutputStream()).getTableCardInset(info));
   }

   private static TableVSAssemblyInfo padded() {
      TableVSAssemblyInfo info = Mockito.mock(TableVSAssemblyInfo.class);
      when(info.getPadding()).thenReturn(new Insets(16, 16, 16, 16));
      return info;
   }
}
