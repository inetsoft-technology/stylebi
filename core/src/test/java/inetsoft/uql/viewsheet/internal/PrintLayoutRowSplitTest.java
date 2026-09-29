package inetsoft.uql.viewsheet.internal;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A modern table's rows carry their cell padding in their height, so the converter asks the report
 * engine to move them whole at a page break; a legacy table keeps today's row splitting.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintLayoutRowSplitTest {
   @Test
   void aModernTableKeepsItsRowsWhole() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      fixture.info.setVizMark(VizMark.MODERN_LIGHT);

      assertTrue(fixture.tableElement().isKeepRowsWhole());
   }

   @Test
   void aModernTableWithoutACardInsetStillKeepsItsRowsWhole() throws Exception {
      // the cell padding, not the card inset, is what makes a split row unreadable
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture();
      fixture.info.setVizMark(VizMark.MODERN_DARK);

      assertTrue(fixture.tableElement().isKeepRowsWhole());
   }

   @Test
   void aLegacyTableLetsItsRowsSplit() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture();
      fixture.info.setVizMark(null);

      assertFalse(fixture.tableElement().isKeepRowsWhole());
   }
}
