package inetsoft.uql.viewsheet.internal;

import inetsoft.report.ReportElement;
import inetsoft.report.StyleConstants;
import inetsoft.report.internal.TableElementDef;
import inetsoft.report.internal.TextBoxElementDef;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A padded print-layout table gets a fixed card-top box, T + title tall and framed on three
 * sides, with the title inside the side insets; the table element below carries (0, L, B, R).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintLayoutCardTopTest {
   @Test
   void aPaddedTableGetsACardTopBoxAboveItsTitle() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      List<ReportElement> elements = fixture.addTable();
      int titleH = fixture.info.getTitleHeight();
      TextBoxElementDef top = (TextBoxElementDef) elements.get(0);
      TextBoxElementDef title = (TextBoxElementDef) elements.get(1);
      TableElementDef table = (TableElementDef) elements.get(2);

      assertEquals(new Rectangle(20, 10, 400, 16 + titleH), fixture.bounds(top));
      assertEquals(new Insets(THIN, THIN, NONE, THIN), top.getBorders());
      assertEquals(Color.YELLOW, top.getBackground());
      assertEquals(new Rectangle(36, 26, 368, titleH), fixture.bounds(title));
      assertEquals(new Rectangle(20, 10 + 16 + titleH - 1, 400, 250 - 16 - titleH),
                   fixture.bounds(table));
      assertEquals(new Insets(0, 16, 16, 16), table.getCardInset());
   }

   @Test
   void theTitleInsideTheCardKeepsOnlyItsOwnBorders() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      fixture.titleFormat().setBorders(new Insets(NONE, NONE, THIN, NONE));
      TextBoxElementDef title = (TextBoxElementDef) fixture.addTable().get(1);

      // the object's THIN top, left and right stay on the card-top box
      assertEquals(new Insets(NONE, NONE, THIN, NONE), title.getBorders());
   }

   @Test
   void aHiddenTitleLeavesACardTopBoxAsTallAsTheTopInset() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      fixture.info.setTitleVisibleValue(false);
      List<ReportElement> elements = fixture.addTable();
      int titleH = fixture.info.getTitleHeight();
      TextBoxElementDef top = (TextBoxElementDef) elements.get(0);

      assertEquals(2, elements.size(), "no title box");
      assertEquals(new Rectangle(20, 10, 400, 16), fixture.bounds(top));
      assertEquals(new Insets(THIN, THIN, NONE, THIN), top.getBorders(),
                   "a padded card keeps its top border with the title hidden");
      // the hidden-title height still comes off, as it did before the inset
      assertEquals(new Rectangle(20, 10 + 16 - 1, 400, 250 - 16 - titleH),
                   fixture.bounds(elements.get(1)));
   }

   @Test
   void aBorderlessObjectKeepsTheCardTopAndTheTableBorderless() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      fixture.info.getFormat().getUserDefinedFormat().setBorders(new Insets(NONE, NONE, NONE, NONE));
      List<ReportElement> elements = fixture.addTable();
      TextBoxElementDef top = (TextBoxElementDef) elements.get(0);
      TableElementDef table = (TableElementDef) elements.get(2);

      assertEquals(new Insets(NONE, NONE, NONE, NONE), top.getBorders());
      assertEquals(new Insets(NONE, NONE, NONE, NONE), table.getBorders());
   }

   @Test
   void anAsymmetricObjectBorderReachesTheCardTopAndTheTableUnmutated() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      fixture.info.getFormat().getUserDefinedFormat().setBorders(new Insets(THIN, NONE, THIN, NONE));
      List<ReportElement> elements = fixture.addTable();
      TextBoxElementDef top = (TextBoxElementDef) elements.get(0);
      TableElementDef table = (TableElementDef) elements.get(2);

      assertEquals(new Insets(THIN, NONE, NONE, NONE), top.getBorders());
      assertEquals(new Insets(THIN, NONE, THIN, NONE), table.getBorders());
   }

   @Test
   void anAsymmetricInsetMovesOnlyItsOwnEdges() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(8, 24, 4, 0);
      List<ReportElement> elements = fixture.addTable();
      int titleH = fixture.info.getTitleHeight();

      assertEquals(new Rectangle(20, 10, 400, 8 + titleH), fixture.bounds(elements.get(0)));
      assertEquals(new Rectangle(44, 18, 376, titleH), fixture.bounds(elements.get(1)));
      assertEquals(new Insets(0, 24, 4, 0), ((TableElementDef) elements.get(2)).getCardInset());
   }

   @Test
   void aCardSmallerThanItsInsetClampsAtZero() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      fixture.info.setPixelSize(new Dimension(20, 20));
      fixture.info.setLayoutSize(new Dimension(20, 20));
      List<ReportElement> elements = fixture.addTable();

      assertEquals(0, fixture.bounds(elements.get(1)).width, "the title has no width left");
      assertEquals(0, fixture.bounds(elements.get(2)).height, "the table has no height left");
   }

   @Test
   void withoutAnInsetTheTitleCarriesTheFrameAsToday() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture();
      List<ReportElement> elements = fixture.addTable();
      int titleH = fixture.info.getTitleHeight();
      TextBoxElementDef title = (TextBoxElementDef) elements.get(0);
      TableElementDef table = (TableElementDef) elements.get(1);

      assertEquals(2, elements.size(), "no card-top box");
      assertEquals(new Rectangle(20, 10, 400, titleH), fixture.bounds(title));
      assertEquals(new Insets(THIN, THIN, THIN, THIN), title.getBorders());
      assertEquals(new Rectangle(20, 10 + titleH - 1, 400, 250 - titleH), fixture.bounds(table));
      assertNull(table.getCardInset());
   }

   private static final int THIN = StyleConstants.THIN_LINE;
   private static final int NONE = StyleConstants.NO_BORDER;
}
