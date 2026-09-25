package inetsoft.report.io.viewsheet;

import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.report.io.viewsheet.html.HTMLVSExporter;
import inetsoft.report.io.viewsheet.svg.PNGVSExporter;
import inetsoft.report.io.viewsheet.svg.SVGVSExporter;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A table's export inset is its padding in every format that paints a card, and zero in the
 * formats that cannot represent one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableCardInsetResolverTest {
   @Test
   void cardFormatsResolveTheTablePadding() {
      for(AbstractVSExporter exporter : List.of(
         new SVGVSExporter(new ByteArrayOutputStream()),
         new PNGVSExporter(new ByteArrayOutputStream()),
         new HTMLVSExporter(new ByteArrayOutputStream())))
      {
         assertEquals(new Insets(16, 12, 8, 4),
                      exporter.getTableCardInset(padded(new Insets(16, 12, 8, 4))),
                      exporter.getClass().getSimpleName());
      }
   }

   @Test
   void csvResolvesZero() {
      assertEquals(new Insets(0, 0, 0, 0),
                   new CSVVSExporter(new ByteArrayOutputStream(), null)
                      .getTableCardInset(padded(new Insets(16, 16, 16, 16))));
   }

   @Test
   void aNullPaddingResolvesZero() {
      assertEquals(new Insets(0, 0, 0, 0),
                   new SVGVSExporter(new ByteArrayOutputStream()).getTableCardInset(padded(null)));
   }

   @Test
   void theResolvedInsetIsACopy() {
      TableVSAssemblyInfo info = padded(new Insets(16, 16, 16, 16));
      new SVGVSExporter(new ByteArrayOutputStream()).getTableCardInset(info).left = 0;

      assertEquals(16, info.getPadding().left, "a caller must not be able to change the padding");
   }

   private static TableVSAssemblyInfo padded(Insets padding) {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setPadding(padding);
      return info;
   }
}
