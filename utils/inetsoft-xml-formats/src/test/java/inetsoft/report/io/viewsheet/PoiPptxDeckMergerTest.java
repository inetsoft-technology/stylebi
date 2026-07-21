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

   /** Concatenates text from every textbox on the slide EXCEPT one starting with "Insights"
    *  (the title box this feature adds) — lets tests assert on body content in isolation. */
   private static String contentTextExcludingInsightsTitle(XSLFSlide slide) {
      StringBuilder sb = new StringBuilder();
      slide.getShapes().forEach(s -> {
         if(s instanceof XSLFTextBox tb && !tb.getText().startsWith("Insights")) {
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
   void titleSlideRecapIsMarkdownStripped() throws Exception {
      byte[] merged = merger.mergeSlides("Q39 Board",
         "**Premium units run the business.** The $1,500+ band is ~69%.\n- top band in every category", List.of());

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         String titleSlideText = allText(result.getSlides().get(0));
         assertTrue(titleSlideText.contains("Premium units run the business."),
            "recap text kept: " + titleSlideText);
         assertFalse(titleSlideText.contains("**"), "raw markdown emphasis stripped: " + titleSlideText);
         assertTrue(titleSlideText.contains("top band in every category"), "bullet text kept: " + titleSlideText);
         assertFalse(titleSlideText.contains("- top band"), "raw bullet dash stripped: " + titleSlideText);
      }
   }

   @Test
   void titleSlideRecapPreservesBoldRunsAndBulletMarkers() throws Exception {
      byte[] merged = merger.mergeSlides("Board",
         "**Bold lead.** then normal text.\n- a bullet point", List.of());

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         boolean hasBoldLead = false;
         boolean hasNormal = false;
         boolean hasBulletMarker = false;

         for(var shape : result.getSlides().get(0).getShapes()) {
            if(shape instanceof XSLFTextBox tb) {
               for(var paragraph : tb.getTextParagraphs()) {
                  for(var run : paragraph.getTextRuns()) {
                     String t = run.getRawText();

                     if(t.contains("Bold lead") && run.isBold()) {
                        hasBoldLead = true;
                     }

                     if(t.contains("normal text") && !run.isBold()) {
                        hasNormal = true;
                     }

                     if(t.contains("•")) {
                        hasBulletMarker = true;
                     }
                  }
               }
            }
         }

         assertTrue(hasBoldLead, "bold lead-in preserved as a bold run");
         assertTrue(hasNormal, "trailing prose stays non-bold");
         assertTrue(hasBulletMarker, "bullet marker rendered");
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

   @Test
   void blankInsightsAddNoExtraSlide() throws Exception {
      byte[] chart1 = oneSlideDeckWithText("CHART_MARKER");
      List<PptxDeckMerger.ChartSlide> slides = List.of(
         new PptxDeckMerger.ChartSlide("First", "cap", chart1, false, "   ")
      );

      byte[] merged = merger.mergeSlides("Board", null, slides);

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         assertEquals(2, result.getSlides().size(), "title + chart slide only, matching today's behavior");
      }
   }

   @Test
   void shortInsightsProduceExactlyOneFollowingSlide() throws Exception {
      byte[] chart1 = oneSlideDeckWithText("CHART_MARKER");
      List<PptxDeckMerger.ChartSlide> slides = List.of(
         new PptxDeckMerger.ChartSlide("First", "cap", chart1, false,
            "Premium pricing drives most of the category revenue.")
      );

      byte[] merged = merger.mergeSlides("Board", null, slides);

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         assertEquals(3, result.getSlides().size(), "title + chart + exactly one insights slide");
         assertTrue(allText(result.getSlides().get(2))
            .contains("Premium pricing drives most of the category revenue."));
      }
   }

   @Test
   void longInsightsSpanMultipleSlidesWithoutSplittingWords() throws Exception {
      byte[] chart1 = oneSlideDeckWithText("CHART_MARKER");
      String longInsights = "WORD ".repeat(6000).trim();
      List<PptxDeckMerger.ChartSlide> slides = List.of(
         new PptxDeckMerger.ChartSlide("First", "cap", chart1, false, longInsights)
      );

      byte[] merged = merger.mergeSlides("Board", null, slides);

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         // title slide + chart slide + N insights-only slides
         assertTrue(result.getSlides().size() > 2, "long insights must span more than one slide");

         int totalWords = 0;
         for(int i = 2; i < result.getSlides().size(); i++) {
            String text = contentTextExcludingInsightsTitle(result.getSlides().get(i)).trim();
            assertTrue(text.matches("(WORD ?)+"),
               "slide " + i + " must contain only whole WORD tokens, got: " + text);
            totalWords += text.split("\\s+").length;
         }
         assertEquals(6000, totalWords, "no words lost or duplicated across the split");
      }
   }

   @Test
   void failedChartInsightsStillGetTheirOwnSlide() throws Exception {
      List<PptxDeckMerger.ChartSlide> slides = List.of(
         new PptxDeckMerger.ChartSlide("Broken", "n/a", null, true, "Finding survives despite the render failure.")
      );

      byte[] merged = merger.mergeSlides("Board", null, slides);

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         assertEquals(3, result.getSlides().size(), "title + placeholder + insights slide");
         assertTrue(allText(result.getSlides().get(2)).contains("Finding survives despite the render failure."));
      }
   }

   @Test
   void insightsSlideTitledWithChartName() throws Exception {
      byte[] chart1 = oneSlideDeckWithText("CHART_MARKER");
      List<PptxDeckMerger.ChartSlide> slides = List.of(
         new PptxDeckMerger.ChartSlide("Revenue by Region", "cap", chart1, false,
            "Premium pricing drives most of the category revenue.")
      );

      byte[] merged = merger.mergeSlides("Board", null, slides);

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         assertEquals(3, result.getSlides().size(), "title + chart + one insights slide");
         String insightsText = allText(result.getSlides().get(2));
         assertTrue(insightsText.contains("Insights: Revenue by Region"),
            "insights slide titled with chart name: " + insightsText);
         assertFalse(insightsText.contains("cont'd"), "single insights slide has no continuation marker");
      }
   }

   @Test
   void insightsSlideFallsBackToBareInsightsWhenChartHasNoTitle() throws Exception {
      byte[] chart1 = oneSlideDeckWithText("CHART_MARKER");
      List<PptxDeckMerger.ChartSlide> slides = List.of(
         new PptxDeckMerger.ChartSlide(null, null, chart1, false, "A short finding.")
      );

      byte[] merged = merger.mergeSlides("Board", null, slides);

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         String insightsText = allText(result.getSlides().get(2));
         assertTrue(insightsText.contains("Insights"), "bare Insights heading present: " + insightsText);
         assertFalse(insightsText.contains("Insights:"), "no dangling colon with no title: " + insightsText);
      }
   }

   @Test
   void continuationInsightsSlidesMarkedContd() throws Exception {
      byte[] chart1 = oneSlideDeckWithText("CHART_MARKER");
      String longInsights = "WORD ".repeat(6000).trim();
      List<PptxDeckMerger.ChartSlide> slides = List.of(
         new PptxDeckMerger.ChartSlide("Revenue by Region", "cap", chart1, false, longInsights)
      );

      byte[] merged = merger.mergeSlides("Board", null, slides);

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         assertTrue(result.getSlides().size() > 3, "must span more than one insights slide");

         String firstInsightsText = allText(result.getSlides().get(2));
         assertTrue(firstInsightsText.contains("Insights: Revenue by Region"));
         assertFalse(firstInsightsText.contains("cont'd"), "first insights slide has no continuation marker");

         for(int i = 3; i < result.getSlides().size(); i++) {
            String text = allText(result.getSlides().get(i));
            assertTrue(text.contains("Insights: Revenue by Region (cont'd)"),
               "slide " + i + " must carry the continuation title: " + text);
         }
      }
   }
}
