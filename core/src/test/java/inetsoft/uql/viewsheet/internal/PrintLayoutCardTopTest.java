/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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

      // the lane starts below the THIN top border
      assertEquals(new Rectangle(20, 10, 400, 1 + 16 + titleH), fixture.bounds(top));
      assertEquals(new Insets(THIN, THIN, NONE, THIN), top.getBorders());
      assertEquals(Color.YELLOW, top.getBackground());
      assertEquals(new Rectangle(37, 27, 367, titleH), fixture.bounds(title));
      assertEquals(new Rectangle(20, 10 + 1 + 16 + titleH - 1, 400, 250 - 1 - 16 - titleH),
                   fixture.bounds(table));
      assertEquals(new Insets(0, 16, 16, 16), table.getCardInset());
   }

   @Test
   void theTitleSitsInsideTheCardBorderAsTheChartTitleDoes() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      List<ReportElement> elements = fixture.addTable();
      int titleH = fixture.info.getTitleHeight();

      // the THIN border's 1px comes before the inset on the top and the left
      assertEquals(new Rectangle(37, 27, 367, titleH), fixture.bounds(elements.get(1)));
   }

   @Test
   void aBorderlessCardPutsTheTitleAtTheInset() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      fixture.info.getFormat().getUserDefinedFormat().setBorders(new Insets(NONE, NONE, NONE, NONE));
      List<ReportElement> elements = fixture.addTable();

      assertEquals(new Rectangle(36, 26, 368, fixture.info.getTitleHeight()),
                   fixture.bounds(elements.get(1)));
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
      assertEquals(new Rectangle(20, 10, 400, 1 + 16), fixture.bounds(top));
      assertEquals(new Insets(THIN, THIN, NONE, THIN), top.getBorders(),
                   "a padded card keeps its top border with the title hidden");
      // the hidden-title height still comes off, as it did before the inset
      assertEquals(new Rectangle(20, 10 + 1 + 16 - 1, 400, 250 - 1 - 16 - titleH),
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

      assertEquals(new Rectangle(20, 10, 400, 1 + 8 + titleH), fixture.bounds(elements.get(0)));
      assertEquals(new Rectangle(45, 19, 375, titleH), fixture.bounds(elements.get(1)));
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

   @Test
   void aThickObjectBorderMovesTheCardTopTitleAndTableByItsWidth() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      fixture.info.getFormat().getUserDefinedFormat()
         .setBorders(new Insets(THICK, THICK, THICK, THICK));
      List<ReportElement> elements = fixture.addTable();
      int titleH = fixture.info.getTitleHeight();

      // bt = bl = 3, so the lane starts at 3 + 16 and the title at x + 3 + 16, W - 19 - 16 wide
      assertEquals(new Rectangle(20, 10, 400, 3 + 16 + titleH), fixture.bounds(elements.get(0)));
      assertEquals(new Rectangle(39, 29, 365, titleH), fixture.bounds(elements.get(1)));
      assertEquals(new Rectangle(20, 10 + 3 + 16 + titleH - 1, 400, 250 - 3 - 16 - titleH),
                   fixture.bounds(elements.get(2)));
   }

   @Test
   void aZeroTopInsetKeepsTheTableBelowTheCardTopBorder() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(0, 16, 16, 16);
      fixture.info.setTitleVisibleValue(false);
      List<ReportElement> elements = fixture.addTable();
      int titleH = fixture.info.getTitleHeight();

      // the box is the THIN border alone, and the table starts under it, not on it
      assertEquals(new Rectangle(20, 10, 400, 1), fixture.bounds(cardTop(elements)));
      assertEquals(new Rectangle(20, 11, 400, 250 - 1 - titleH),
                   fixture.bounds(table(elements)));
   }

   @Test
   void aZeroTopInsetOnABorderlessCardStartsTheTableAtTheCardTop() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(0, 16, 16, 16);
      fixture.info.getFormat().getUserDefinedFormat().setBorders(new Insets(NONE, NONE, NONE, NONE));
      fixture.info.setTitleVisibleValue(false);
      List<ReportElement> elements = fixture.addTable();
      int titleH = fixture.info.getTitleHeight();

      // no border and no lane, so there is nothing above the card to climb into
      assertEquals(new Rectangle(20, 10, 400, 250 - titleH), fixture.bounds(table(elements)));
   }

   @Test
   void aZeroTopInsetStillJoinsAVisibleTitle() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(0, 16, 16, 16);
      List<ReportElement> elements = fixture.addTable();
      int titleH = fixture.info.getTitleHeight();

      assertEquals(new Rectangle(20, 10, 400, 1 + titleH), fixture.bounds(elements.get(0)));
      assertEquals(new Rectangle(37, 11, 367, titleH), fixture.bounds(elements.get(1)));
      // the lane absorbs the overlap, so the join is unchanged
      assertEquals(new Rectangle(20, 10 + titleH, 400, 250 - 1 - titleH),
                   fixture.bounds(table(elements)));
   }

   private static ReportElement table(List<ReportElement> elements) {
      return elements.stream().filter(e -> e instanceof TableElementDef)
         .findFirst().orElseThrow();
   }

   private static ReportElement cardTop(List<ReportElement> elements) {
      return elements.stream().filter(e -> e instanceof TextBoxElementDef)
         .findFirst().orElseThrow();
   }

   private static final int THICK = StyleConstants.THICK_LINE;
   private static final int THIN = StyleConstants.THIN_LINE;
   private static final int NONE = StyleConstants.NO_BORDER;
}
