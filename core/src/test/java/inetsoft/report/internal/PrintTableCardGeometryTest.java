package inetsoft.report.internal;

import inetsoft.report.ReportSheet;
import inetsoft.report.StylePage;
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
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A print-layout table region reports and paints its card: the grid box grown by the side
 * insets, and by the bottom inset on the last region only.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintTableCardGeometryTest {
   @Test
   void theBoundsDescribeTheCard() {
      TablePaintable region = new PrintTableFixture().inset(16, 16, 16).regions().get(0);

      // the box at (73, 46), 366 x 220, grown by 16 left and right and by 16 below
      assertEquals(new Rectangle(57, 46, 398, 236), region.getBounds());
      assertEquals(new Rectangle(57, 46, 398, 236), region.getBounds2());
      assertEquals(236, region.getHeight(), 0.01);
   }

   @Test
   void withoutAnInsetTheBoundsAreTodays() {
      TablePaintable region = new PrintTableFixture().regions().get(0);

      assertEquals(new Rectangle(57, 46, 398, 219), region.getBounds());
      assertEquals(new Rectangle(57, 46, 398, 220), region.getBounds2());
      assertEquals(220, region.getHeight(), 0.01);
   }

   @Test
   void onlyTheLastRegionCarriesTheBottomInset() {
      // 40 rows print 34 on the first page and 6 on the second
      List<TablePaintable> regions = new PrintTableFixture().inset(16, 16, 16).rows(40).regions();

      assertEquals(700, regions.get(0).getHeight(), 0.01);
      assertEquals(156, regions.get(1).getHeight(), 0.01);
   }

   @Test
   void withoutAnInsetEveryRegionIsItsRows() {
      List<TablePaintable> regions = new PrintTableFixture().rows(40).regions();

      assertEquals(700, regions.get(0).getHeight(), 0.01);
      assertEquals(140, regions.get(1).getHeight(), 0.01);
   }

   @Test
   void onlyTheLastFitContentsSegmentCarriesTheBottomInset() {
      List<TablePaintable> regions = new PrintTableFixture().inset(16, 16, 16)
         .layout(ReportSheet.TABLE_FIT_CONTENT).widths(130, 130, 130).regions();

      assertEquals(220, regions.get(0).getHeight(), 0.01);
      assertEquals(236, regions.get(1).getHeight(), 0.01);
   }

   @Test
   void theLocationIsTheCardOrigin() {
      TablePaintable region = new PrintTableFixture().inset(16, 16, 16).regions().get(0);
      Rectangle bounds = region.getBounds();
      double cellY = region.getPrintBounds(1, 0, false).y;

      assertEquals(new Point(57, 46), region.getLocation());

      region.setLocation(region.getLocation());
      assertEquals(bounds, region.getBounds(), "moving a region to where it is moves nothing");

      region.setLocation(new Point(57, 146));
      assertEquals(new Rectangle(57, 146, 398, 236), region.getBounds());
      assertEquals(74, region.getPrintBounds(1, 0, false).x, 0.01, "the cells moved with it");
      assertEquals(cellY + 100, region.getPrintBounds(1, 0, false).y, 0.01,
                   "the cells moved down with it too");
   }

   @Test
   void theElementBelowIsPushedDownByTheCardsGrowth() {
      StylePage inset = new PrintTableFixture().inset(16, 16, 16).textBelow().print().get(0);
      StylePage plain = new PrintTableFixture().textBelow().print().get(0);
      int growth = table(inset).getBounds().height - table(plain).getBounds().height;

      assertEquals(17, growth, "B, plus the last row's own bottom border, which the inset keeps");
      assertEquals(text(plain).getBounds().y + growth, text(inset).getBounds().y);
   }

   private static TablePaintable table(StylePage page) {
      return PrintTableFixture.paintables(page, TablePaintable.class).get(0);
   }

   private static TextPaintable text(StylePage page) {
      return PrintTableFixture.paintables(page, TextPaintable.class).get(0);
   }

   @Test
   void theBackgroundCoversTheInsetBands() {
      BufferedImage page =
         new PrintTableFixture().inset(16, 16, 16).background(Color.YELLOW).renderFirstPage();

      // the card spans 56..456 across and 46..282 down; the grid runs 73..439 and 46..266
      assertEquals(Color.YELLOW.getRGB(), page.getRGB(64, 100), "left band");
      assertEquals(Color.YELLOW.getRGB(), page.getRGB(448, 100), "right band");
      assertEquals(Color.YELLOW.getRGB(), page.getRGB(200, 274), "bottom band");
   }

   @Test
   void withoutAnInsetNothingIsPaintedBelowTheRows() {
      BufferedImage page = new PrintTableFixture().background(Color.YELLOW).renderFirstPage();

      assertEquals(Color.WHITE.getRGB(), page.getRGB(200, 274));
   }

   @Test
   void theFillReachesTheNonLastSegmentsFullContentHeight() {
      // two Fit Contents segments stacked on one page; the first segment's content ends at
      // y = 266 (46 + 220), one point below where the unpatched fill stops
      BufferedImage page = new PrintTableFixture().inset(16, 16, 16)
         .noCellBorders().layout(ReportSheet.TABLE_FIT_CONTENT).widths(130, 130, 130)
         .background(Color.YELLOW).renderFirstPage();

      assertEquals(Color.YELLOW.getRGB(), page.getRGB(200, 265));
   }

   @Test
   void aTranslucentBackgroundFillsTheGridAndBandsOnce() {
      BufferedImage page = new PrintTableFixture().inset(16, 16, 16)
         .background(new Color(255, 255, 0, 128)).renderFirstPage();

      // (64, 100) is the left band; (180, 100) is clear of text and row borders inside cell 0
      assertEquals(page.getRGB(64, 100), page.getRGB(180, 100),
                  "the grid and the bands share one fill");
   }

   @Test
   void theInsetSurvivesAPageSwap() throws Exception {
      // a swapped page restores its element as a bare BaseElement, so the inset must be the
      // paintable's own
      TablePaintable region =
         new PrintTableFixture().inset(16, 16, 16).noCellBorders().regions().get(0);
      TablePaintable back = roundTrip(region);

      assertEquals(new Rectangle(57, 46, 398, 235), region.getBounds());
      assertEquals(region.getBounds(), back.getBounds());
      assertEquals(new Point(57, 46), back.getLocation());
   }

   private static TablePaintable roundTrip(TablePaintable region) throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(region);
      }

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         return (TablePaintable) in.readObject();
      }
   }
}
