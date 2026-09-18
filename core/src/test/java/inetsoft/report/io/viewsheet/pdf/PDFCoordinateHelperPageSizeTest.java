/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.io.viewsheet.pdf;

import inetsoft.report.PDFPrinter;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76780: the 200 inch PDF page maximum was reported for height only, so a
 * viewsheet that overflowed it sideways was clipped in complete silence — no
 * message on the page and nothing in the log — and produced a structurally valid
 * PDF that gave no sign anything was wrong.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PDFCoordinateHelperPageSizeTest {
   /** 200 inches at 72 dpi, in points — the maximum the PDF specification allows. */
   private static final int MAX = 200 * PDFPrinter.RESOLUTION;

   private static PDFCoordinateHelper helper() {
      return new PDFCoordinateHelper(new ByteArrayOutputStream());
   }

   /**
    * The page size in points. {@code setPageSize(int, int)} takes points but
    * {@code getPageSize()} reports inches, the same conversion
    * {@code createPage} itself applies when it fills the page background.
    */
   private static double widthPt(PDFCoordinateHelper helper) {
      return helper.getPrinter().getPageSize().width * PDFPrinter.RESOLUTION;
   }

   private static double heightPt(PDFCoordinateHelper helper) {
      return helper.getPrinter().getPageSize().height * PDFPrinter.RESOLUTION;
   }

   @Test
   void ordinaryPageIsNotFlagged() {
      PDFCoordinateHelper helper = helper();
      helper.createPage(new Dimension(1177, 1128));

      assertFalse(helper.getPrinter().isOutOfMaxPageSize());
      assertFalse(helper.isOutOfMaxPageWidth());
      // the +1 border allowance (bug #20360 / #23060) is preserved
      assertEquals(1178, widthPt(helper), 0.001);
      assertEquals(1129, heightPt(helper), 0.001);
   }

   @Test
   void overlongPageIsFlaggedAndClamped() {
      PDFCoordinateHelper helper = helper();
      helper.createPage(new Dimension(1663, MAX + 5000));

      assertTrue(helper.getPrinter().isOutOfMaxPageSize());
      assertFalse(helper.isOutOfMaxPageWidth(), "height overflowed, not width");
      assertEquals(MAX, heightPt(helper), 0.001);
   }

   // The regression this test exists for: before the fix an over-wide page set no
   // flag at all, so PDFVSExporter.writeAdditionalTipMessage drew nothing.
   @Test
   void overwidePageIsFlaggedAndClamped() {
      PDFCoordinateHelper helper = helper();
      helper.createPage(new Dimension(MAX + 5000, 1408));

      assertTrue(helper.getPrinter().isOutOfMaxPageSize(),
                 "an over-wide page must be reported, not silently clipped");
      assertTrue(helper.isOutOfMaxPageWidth());
      assertEquals(MAX, widthPt(helper), 0.001);
   }

   @Test
   void bothAxesOverflowing() {
      PDFCoordinateHelper helper = helper();
      helper.createPage(new Dimension(MAX + 1, MAX + 1));

      assertTrue(helper.getPrinter().isOutOfMaxPageSize());
      assertTrue(helper.isOutOfMaxPageWidth());
      assertEquals(MAX, widthPt(helper), 0.001);
      assertEquals(MAX, heightPt(helper), 0.001);
   }

   // Exactly at the limit the +1 border allowance is what would exceed it, and the
   // clamp absorbs that; the page is at the maximum but nothing is lost, so it
   // must not be reported as truncated.
   @Test
   void exactlyAtTheLimitIsNotFlagged() {
      PDFCoordinateHelper helper = helper();
      helper.createPage(new Dimension(MAX, MAX));

      assertFalse(helper.getPrinter().isOutOfMaxPageSize());
      assertFalse(helper.isOutOfMaxPageWidth());
      assertEquals(MAX, widthPt(helper), 0.001);
      assertEquals(MAX, heightPt(helper), 0.001);
   }
}
