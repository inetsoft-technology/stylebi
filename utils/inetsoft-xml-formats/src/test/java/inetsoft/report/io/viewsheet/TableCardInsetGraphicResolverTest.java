package inetsoft.report.io.viewsheet;

import inetsoft.report.io.viewsheet.svg.PNGVSExporter;
import inetsoft.report.io.viewsheet.svg.SVGVSExporter;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.Insets;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * SVG and PNG both paint a card, so both resolve the table's padding as their inset.
 */
class TableCardInsetGraphicResolverTest {
   @Test
   void svgAndPngResolveThePadding() {
      TableVSAssemblyInfo info = padded();

      assertEquals(new Insets(16, 12, 8, 4),
                   new SVGVSExporter(new ByteArrayOutputStream()).getTableCardInset(info));
      assertEquals(new Insets(16, 12, 8, 4),
                   new PNGVSExporter(new ByteArrayOutputStream()).getTableCardInset(info));
   }

   private static TableVSAssemblyInfo padded() {
      TableVSAssemblyInfo info = Mockito.mock(TableVSAssemblyInfo.class);
      when(info.getPadding()).thenReturn(new Insets(16, 12, 8, 4));
      return info;
   }
}
