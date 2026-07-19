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
package inetsoft.report.io.viewsheet;

import inetsoft.web.wiz.service.PptxDeckMerger;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class PoiPptxDeckMergerTest {
   private final PptxDeckMerger merger = new PoiPptxDeckMerger();

   /** Builds a single-slide deck with one text box, simulating a real chart export's output
    *  well enough to exercise importContent — the real chart content is opaque shapes to us. */
   private static byte[] oneSlideDeckWithText(String text) throws Exception {
      try(XMLSlideShow show = new XMLSlideShow()) {
         XSLFSlide slide = show.createSlide();
         XSLFTextBox box = slide.createTextBox();
         box.setAnchor(new Rectangle2D.Double(10, 10, 400, 100));
         box.setText(text);
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         show.write(out);
         return out.toByteArray();
      }
   }

   private static String allText(XSLFSlide slide) {
      StringBuilder sb = new StringBuilder();
      slide.getShapes().forEach(s -> {
         if(s instanceof XSLFTextBox tb) {
            sb.append(tb.getText()).append('\n');
         }
      });
      return sb.toString();
   }

   @Test
   void mergesTitleSlidePlusOnePerChart() throws Exception {
      byte[] chart1 = oneSlideDeckWithText("CHART_ONE_MARKER");
      byte[] chart2 = oneSlideDeckWithText("CHART_TWO_MARKER");
      List<PptxDeckMerger.ChartSlide> slides = List.of(
         new PptxDeckMerger.ChartSlide("First", "cap one", chart1, false),
         new PptxDeckMerger.ChartSlide("Second", "cap two", chart2, false)
      );

      byte[] merged = merger.mergeSlides("Q39 Board", "Premium drives revenue.", slides);

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         assertEquals(3, result.getSlides().size(), "title slide + 2 chart slides");

         String titleSlideText = allText(result.getSlides().get(0));
         assertTrue(titleSlideText.contains("Q39 Board"));
         assertTrue(titleSlideText.contains("Premium drives revenue."));

         String slide1Text = allText(result.getSlides().get(1));
         assertTrue(slide1Text.contains("First"), "caption title present: " + slide1Text);
         assertTrue(slide1Text.contains("cap one"), "caption text present: " + slide1Text);
         assertTrue(slide1Text.contains("CHART_ONE_MARKER"), "imported chart content present: " + slide1Text);

         String slide2Text = allText(result.getSlides().get(2));
         assertTrue(slide2Text.contains("CHART_TWO_MARKER"));
      }
   }

   @Test
   void failedChartGetsPlaceholderSlideInsteadOfImportedContent() throws Exception {
      List<PptxDeckMerger.ChartSlide> slides = List.of(
         new PptxDeckMerger.ChartSlide("Broken", "n/a", null, true)
      );

      byte[] merged = merger.mergeSlides("Board", null, slides);

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         assertEquals(2, result.getSlides().size());
         String slideText = allText(result.getSlides().get(1));
         assertTrue(slideText.toLowerCase().contains("failed"), "placeholder text present: " + slideText);
         assertTrue(slideText.contains("Broken"), "chart title present in placeholder: " + slideText);
      }
   }

   @Test
   void noRecapOmitsRecapTextButStillShowsTitle() throws Exception {
      byte[] merged = merger.mergeSlides("Board Only", null, List.of());

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         assertEquals(1, result.getSlides().size(), "title slide only, no charts");
         assertTrue(allText(result.getSlides().get(0)).contains("Board Only"));
      }
   }
}
