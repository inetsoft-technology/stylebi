package inetsoft.report.io.viewsheet.ppt;

import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.Insets;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * PowerPoint paints a card, so it resolves the table's padding as its inset.
 */
class TableCardInsetPPTResolverTest {
   @Test
   void powerPointResolvesThePadding() {
      TableVSAssemblyInfo info = Mockito.mock(TableVSAssemblyInfo.class);
      when(info.getPadding()).thenReturn(new Insets(16, 16, 16, 16));

      assertEquals(new Insets(16, 16, 16, 16),
                   new PPTVSExporter(Mockito.mock(PPTContext.class), new ByteArrayOutputStream())
                      .getTableCardInset(info));
   }
}
