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
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
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

   /** Builds a single-slide deck with one real picture shape (a 1x1 PNG), for the tests that need
    *  to assert an actual XSLFPictureShape survives importContent alongside the merger's own
    *  content, not just an opaque text-box stand-in. */
   private static byte[] oneSlideDeckWithPicture() throws Exception {
      byte[] png = {
         (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49,
         0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06, 0x00, 0x00,
         0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
         0x54, 0x78, (byte) 0x9C, 0x62, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D,
         (byte) 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60,
         (byte) 0x82
      };

      try(XMLSlideShow show = new XMLSlideShow()) {
         XSLFSlide slide = show.createSlide();
         var pictureData = show.addPicture(png, org.apache.poi.sl.usermodel.PictureData.PictureType.PNG);
         XSLFPictureShape picture = slide.createPicture(pictureData);
         picture.setAnchor(new Rectangle2D.Double(10, 10, 400, 100));
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

   /** Concatenates text from every textbox on the slide whose ENTIRE text is made of whole
    *  "WORD" tokens — isolates a chunked long-insights block from whatever else shares its slide
    *  (chart marker text, caption, insights title), since only that block's text matches. */
   private static String wordChunkText(XSLFSlide slide) {
      StringBuilder sb = new StringBuilder();
      slide.getShapes().forEach(s -> {
         if(s instanceof XSLFTextBox tb && tb.getText() != null &&
            tb.getText().trim().matches("(WORD ?)+"))
         {
            sb.append(tb.getText()).append('\n');
         }
      });
      return sb.toString();
   }

   /**
    * LIVE BUG, seen in an exported deck: the slide caption ran over the chart, covering the plot's
    * own title and top bars.
    *
    * <p>The caption is drawn ON TOP of the imported chart slide, in a fixed 44pt band at a fixed
    * 22pt. That band holds about two lines; a caption made of real prose wraps to three or more and
    * everything past the band lands on the chart. It now shrinks to fit, and truncates only if it
    * still will not fit at the floor.
    */
   @Test
   void aLongCaptionIsFittedToItsBandInsteadOfRunningOverTheChart() throws Exception {
      String title = "bar";
      String caption = "Portfolio shape: planned hours per project. Heavily right-tailed — one " +
         "programme carries roughly a third of all planned effort, and the mean sits at more than " +
         "double the median, so an average-project figure would describe none of them.";

      byte[] deck = merger.mergeSlides("Board", null, List.of(
         new PptxDeckMerger.ChartSlide(title, caption, oneSlideDeckWithText("chart"), false)));

      try(XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(deck))) {
         XSLFTextBox captionBox = show.getSlides().get(1).getShapes().stream()
            .filter(sh -> sh instanceof XSLFTextBox)
            .map(sh -> (XSLFTextBox) sh)
            .filter(b -> b.getText() != null && b.getText().startsWith("bar —"))
            .findFirst().orElseThrow(() -> new AssertionError("no caption box on the chart slide"));

         double fontPt = captionBox.getTextParagraphs().get(0).getTextRuns().get(0).getFontSize();
         double bandPt = captionBox.getAnchor().getHeight();
         double needed = PoiPptxDeckMerger.textHeightPt(captionBox.getText(), fontPt,
                                                        captionBox.getAnchor().getWidth());

         assertTrue(needed <= bandPt,
            "the caption must fit its " + bandPt + "pt band (needs " + needed + "pt at " + fontPt +
            "pt) — anything taller is painted over the chart");
         assertTrue(fontPt <= 22.0 && fontPt >= 12.0,
            "shrunk to fit but not below the readable floor (got " + fontPt + "pt)");
      }
   }

   @Test
   void aShortCaptionKeepsTheFullSizeAndIsNotTruncated() throws Exception {
      // The fit logic must not shrink or clip a caption that already fits.
      byte[] deck = merger.mergeSlides("Board", null, List.of(
         new PptxDeckMerger.ChartSlide("Revenue", "by region", oneSlideDeckWithText("chart"), false)));

      try(XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(deck))) {
         XSLFTextBox captionBox = show.getSlides().get(1).getShapes().stream()
            .filter(sh -> sh instanceof XSLFTextBox)
            .map(sh -> (XSLFTextBox) sh)
            .filter(b -> b.getText() != null && b.getText().startsWith("Revenue"))
            .findFirst().orElseThrow();

         assertEquals("Revenue — by region", captionBox.getText(), "short caption kept verbatim");
         assertEquals(22.0,
            captionBox.getTextParagraphs().get(0).getTextRuns().get(0).getFontSize(), 0.01,
            "no shrink when it already fits");
      }
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

   /** bug-76110 round 2: a chart's own short insights must land on the SAME slide as its
    *  imported picture instead of starting a separate slide — reducing this repro shape from 3
    *  slides (title, chart, insights) to 2 (title, chart-with-insights). */
   @Test
   void shortInsightsShareTheChartsOwnSlideInsteadOfAFreshOne() throws Exception {
      byte[] chart1 = oneSlideDeckWithText("CHART_MARKER");
      List<PptxDeckMerger.ChartSlide> slides = List.of(
         new PptxDeckMerger.ChartSlide("First", "cap", chart1, false,
            "Premium pricing drives most of the category revenue.")
      );

      byte[] merged = merger.mergeSlides("Board", null, slides);

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         assertEquals(2, result.getSlides().size(),
            "title + one combined chart-and-insights slide, no separate insights slide");
         String chartSlideText = allText(result.getSlides().get(1));
         assertTrue(chartSlideText.contains("CHART_MARKER"), "imported chart content present: " + chartSlideText);
         assertTrue(chartSlideText.contains("Premium pricing drives most of the category revenue."),
            "insights text present on the same slide: " + chartSlideText);
      }
   }

   /** The chart's own slide contains both the imported picture and its insights text/bullets in
    *  the same XSLFSlide -- the combining behavior itself, not just the text content. */
   @Test
   void chartSlideCombinesImportedPictureAndItsOwnInsightsBullets() throws Exception {
      byte[] chart1 = oneSlideDeckWithPicture();
      List<PptxDeckMerger.ChartSlide> slides = List.of(
         new PptxDeckMerger.ChartSlide("First", "cap", chart1, false,
            "Summary finding.\n- first bullet\n- second bullet")
      );

      byte[] merged = merger.mergeSlides("Board", null, slides);

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         assertEquals(2, result.getSlides().size(), "title + one combined chart-and-insights slide");
         XSLFSlide chartSlide = result.getSlides().get(1);

         boolean hasPicture = chartSlide.getShapes().stream()
            .anyMatch(sh -> sh instanceof XSLFPictureShape);
         assertTrue(hasPicture, "imported picture survives on the combined slide");

         String text = allText(chartSlide);
         assertTrue(text.contains("Summary finding."), "insights paragraph present: " + text);
         assertTrue(text.contains("first bullet") && text.contains("second bullet"),
            "insights bullets present: " + text);
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
         // title slide + chart slide (carrying the first chunk) + N continuation insights slides
         assertTrue(result.getSlides().size() > 2,
            "insights this long must still overflow into continuation slides, not fit or vanish");

         int totalWords = 0;
         for(int i = 1; i < result.getSlides().size(); i++) {
            String text = wordChunkText(result.getSlides().get(i)).trim();

            if(text.isEmpty()) {
               continue; // slide 1's caption/imported-marker text isn't part of the WORD chunk
            }

            assertTrue(text.matches("(WORD ?)+"),
               "slide " + i + " word-chunk box must contain only whole WORD tokens, got: " + text);
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
         assertEquals(2, result.getSlides().size(), "title + one combined chart-and-insights slide");
         String insightsText = allText(result.getSlides().get(1));
         assertTrue(insightsText.contains("Insights: Revenue by Region"),
            "insights heading titled with chart name, on the chart's own slide: " + insightsText);
         assertFalse(insightsText.contains("cont'd"),
            "the chart's own combined slide has no continuation marker");
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
         String insightsText = allText(result.getSlides().get(1));
         assertTrue(insightsText.contains("Insights"), "bare Insights heading present: " + insightsText);
         assertFalse(insightsText.contains("Insights:"), "no dangling colon with no title: " + insightsText);
      }
   }

   /**
    * bug-76110: an insight bullet whose text contained an emoji rendered as a missing-glyph box
    * in the exported PPTX, because no run this merger creates ever set an explicit font family —
    * without one, OOXML falls back to the deck's theme font (Calibri) by inheritance. The fix
    * sets an explicit family ({@link inetsoft.report.StyleFont#getDefaultFontFamily()}, "Roboto"
    * outside a live server context — same default the PDF export path resolves to) on every run,
    * matching this codebase's own PPTX-export convention ({@code PPTValueHelper}, bug #75992) of
    * never leaving typeface resolution to the reader. This does not by itself guarantee the
    * emoji glyph renders (that depends on whether the resolved family has emoji coverage on the
    * machine that opens the deck); it only guarantees every run carries an explicit,
    * intentional typeface instead of an inherited one.
    */
   @Test
   void everyRunCarriesAnExplicitFontFamilyInsteadOfThemeInheritance() throws Exception {
      byte[] chart1 = oneSlideDeckWithText("CHART_MARKER");
      List<PptxDeckMerger.ChartSlide> slides = List.of(
         new PptxDeckMerger.ChartSlide("Revenue by Region", "cap", chart1, false,
            "**Growth accelerating.**\n- 📈 Premium pricing drives most of the revenue.")
      );

      byte[] merged = merger.mergeSlides("Q39 Board", "Premium units run the business.", slides);

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         // Title slide: exercises styleBox() (title box) and appendBlock()'s recap paragraph.
         XSLFTextBox titleBox = result.getSlides().get(0).getShapes().stream()
            .filter(sh -> sh instanceof XSLFTextBox tb && "Q39 Board".equals(tb.getText()))
            .map(sh -> (XSLFTextBox) sh)
            .findFirst().orElseThrow();
         assertEquals("Roboto",
            titleBox.getTextParagraphs().get(0).getTextRuns().get(0).getFontFamily(),
            "styleBox() must set an explicit family instead of leaving the title run to theme "
               + "inheritance");

         // Insights, sharing the chart's own slide (bug-76110 round 2): exercises appendBlock()'s
         // bullet-marker run and appendSpans()'s span run (the run that would actually carry the
         // emoji-containing text).
         XSLFSlide insightsSlide = result.getSlides().get(1);
         boolean sawBulletMarkerFamily = false;
         boolean sawEmojiSpanFamily = false;

         for(var shape : insightsSlide.getShapes()) {
            if(shape instanceof XSLFTextBox tb) {
               for(var paragraph : tb.getTextParagraphs()) {
                  for(var run : paragraph.getTextRuns()) {
                     String t = run.getRawText();

                     if(t.contains("•")) {
                        assertEquals("Roboto", run.getFontFamily(),
                           "bullet-marker run in appendBlock() must set an explicit family");
                        sawBulletMarkerFamily = true;
                     }

                     if(t.contains("Premium pricing")) {
                        assertEquals("Roboto", run.getFontFamily(),
                           "span run in appendSpans() must set an explicit family");
                        sawEmojiSpanFamily = true;
                     }
                  }
               }
            }
         }

         assertTrue(sawBulletMarkerFamily, "expected to find the bullet-marker run");
         assertTrue(sawEmojiSpanFamily, "expected to find the emoji-adjacent span run");
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
         assertTrue(result.getSlides().size() > 2, "must span at least one continuation slide");

         // The chart's own slide carries the first (unmarked) insights heading + chunk.
         String firstInsightsText = allText(result.getSlides().get(1));
         assertTrue(firstInsightsText.contains("Insights: Revenue by Region"));
         assertFalse(firstInsightsText.contains("cont'd"),
            "the chart's own combined slide has no continuation marker");

         for(int i = 2; i < result.getSlides().size(); i++) {
            String text = allText(result.getSlides().get(i));
            assertTrue(text.contains("Insights: Revenue by Region (cont'd)"),
               "slide " + i + " must carry the continuation title: " + text);
         }
      }
   }

   /**
    * Promoted from a throwaway test written during this bug's diagnosis/refute rounds (both
    * independently wrote and deleted their own version of this scenario, run live, before this
    * revision made it permanent): a multi-chart board's per-chart slide-plus-its-own-insights
    * grouping stays strictly sequential, never interleaved, once insights share the chart's own
    * slide.
    */
   @Test
   void multiChartSlidesGroupEachChartWithItsOwnInsightsNotInterleaved() throws Exception {
      byte[] chart1 = oneSlideDeckWithText("CHART_ONE_MARKER");
      byte[] chart2 = oneSlideDeckWithText("CHART_TWO_MARKER");
      List<PptxDeckMerger.ChartSlide> slides = List.of(
         new PptxDeckMerger.ChartSlide("First", "cap one", chart1, false, "Finding about chart one."),
         new PptxDeckMerger.ChartSlide("Second", "cap two", chart2, false, "Finding about chart two.")
      );

      byte[] merged = merger.mergeSlides("Board", null, slides);

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         assertEquals(3, result.getSlides().size(),
            "title + chart1-with-its-insights + chart2-with-its-insights, no separate insights slides");

         String slide1Text = allText(result.getSlides().get(1));
         assertTrue(slide1Text.contains("CHART_ONE_MARKER"), "chart1 content: " + slide1Text);
         assertTrue(slide1Text.contains("Finding about chart one."), "chart1 insights: " + slide1Text);
         assertFalse(slide1Text.contains("chart two"), "chart2's insights must not leak onto chart1's slide");

         String slide2Text = allText(result.getSlides().get(2));
         assertTrue(slide2Text.contains("CHART_TWO_MARKER"), "chart2 content: " + slide2Text);
         assertTrue(slide2Text.contains("Finding about chart two."), "chart2 insights: " + slide2Text);
         assertFalse(slide2Text.contains("chart one"), "chart1's insights must not leak onto chart2's slide");
      }
   }

   /** Promoted the same way as the test above (originally the refuter's throwaway edge-case
    *  test): charts with no insights of their own must not disturb the sequencing around a chart
    *  that does. */
   @Test
   void chartsWithoutInsightsDontDisruptSequencingAroundAChartThatHasThem() throws Exception {
      byte[] alpha = oneSlideDeckWithText("ALPHA_MARKER");
      byte[] beta = oneSlideDeckWithText("BETA_MARKER");
      byte[] gamma = oneSlideDeckWithText("GAMMA_MARKER");
      List<PptxDeckMerger.ChartSlide> slides = List.of(
         new PptxDeckMerger.ChartSlide("Alpha", "cap a", alpha, false),
         new PptxDeckMerger.ChartSlide("Beta", "cap b", beta, false, "Finding about Beta."),
         new PptxDeckMerger.ChartSlide("Gamma", "cap g", gamma, false)
      );

      byte[] merged = merger.mergeSlides("Board", null, slides);

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         assertEquals(4, result.getSlides().size(), "title, Alpha, Beta-with-its-insights, Gamma");

         assertTrue(allText(result.getSlides().get(1)).contains("ALPHA_MARKER"));

         String betaText = allText(result.getSlides().get(2));
         assertTrue(betaText.contains("BETA_MARKER"));
         assertTrue(betaText.contains("Finding about Beta."));
         assertFalse(betaText.contains("GAMMA_MARKER"),
            "Gamma's marker must not leak onto Beta's own combined slide");

         String gammaText = allText(result.getSlides().get(3));
         assertTrue(gammaText.contains("GAMMA_MARKER"));
         assertFalse(gammaText.contains("Finding about Beta."),
            "Beta's insights must not leak onto Gamma's slide");
      }
   }

   /** Promoted the same way as the two tests above (originally the refuter's second throwaway
    *  edge case): insights so long they span continuation slides must not swallow, reorder, or
    *  corrupt the very next chart's own slide -- that chart's slide stays the last slide, and
    *  every slide in between is one of the long-insights chart's own continuations. */
   @Test
   void longInsightsContinuationSlidesStayBeforeTheNextChartsOwnSlide() throws Exception {
      byte[] chart1 = oneSlideDeckWithText("FIRST_MARKER");
      byte[] chart2 = oneSlideDeckWithText("SECOND_MARKER");
      String longInsights = "WORD ".repeat(6000).trim();
      List<PptxDeckMerger.ChartSlide> slides = List.of(
         new PptxDeckMerger.ChartSlide("First", "cap", chart1, false, longInsights),
         new PptxDeckMerger.ChartSlide("Second", "cap two", chart2, false)
      );

      byte[] merged = merger.mergeSlides("Board", null, slides);

      try(XMLSlideShow result = new XMLSlideShow(new ByteArrayInputStream(merged))) {
         int lastIndex = result.getSlides().size() - 1;
         assertTrue(lastIndex > 1, "first chart's long insights must add at least one continuation slide");

         String lastSlideText = allText(result.getSlides().get(lastIndex));
         assertTrue(lastSlideText.contains("SECOND_MARKER"),
            "chart2's own slide must be the very last slide: " + lastSlideText);

         for(int i = 1; i < lastIndex; i++) {
            String text = allText(result.getSlides().get(i));
            assertFalse(text.contains("SECOND_MARKER"),
               "chart2 must not appear before its own slide (slide " + i + "): " + text);

            if(i > 1) {
               assertTrue(text.contains("Insights: First (cont'd)"),
                  "slide " + i + " must be one of chart1's own continuation slides: " + text);
            }
         }
      }
   }
}
