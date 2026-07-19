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

import inetsoft.web.wiz.service.MarkdownPlainText;
import inetsoft.web.wiz.service.PptxDeckMerger;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextRun;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a multi-slide .pptx from independently-exported single-chart decks (POST
 * /api/wiz/viewsheet/export-report, format=pptx). Merges at this wiz layer rather than
 * extending PPTVSExporter itself — PPTVSExporter has no multi-slide mechanism today (it calls
 * XMLSlideShow.createSlide() exactly once) and is shared by every PowerPoint export in the
 * product, not just wiz's.
 */
public class PoiPptxDeckMerger implements PptxDeckMerger {
   @Override
   public byte[] mergeSlides(String title, String recap, List<ChartSlide> slides) throws Exception {
      try(XMLSlideShow merged = new XMLSlideShow()) {
         merged.setPageSize(new Dimension(SLIDE_WIDTH_PT, SLIDE_HEIGHT_PT));

         addTitleSlide(merged, title, recap);

         for(ChartSlide slide : slides) {
            if(slide.failed()) {
               XSLFSlide target = merged.createSlide();
               addFailurePlaceholder(target, slide.title());
               addCaption(target, slide.title(), slide.caption());
            }
            else {
               try(XMLSlideShow source =
                  new XMLSlideShow(new ByteArrayInputStream(slide.singleSlideDeckBytes())))
               {
                  XSLFSlide target = merged.createSlide();
                  target.importContent(source.getSlides().get(0));

                  // Added AFTER importContent, not before: empirically verified (see this
                  // task's report) that importContent REPLACES the target slide's shape tree
                  // wholesale — a caption added before importContent is wiped out. Adding it
                  // after is the only ordering under which it survives.
                  addCaption(target, slide.title(), slide.caption());
               }
            }

            if(slide.insightsMarkdown() != null && !slide.insightsMarkdown().isBlank()) {
               addInsightsSlides(merged, slide.insightsMarkdown());
            }
         }

         ByteArrayOutputStream out = new ByteArrayOutputStream();
         merged.write(out);
         return out.toByteArray();
      }
   }

   private void addTitleSlide(XMLSlideShow show, String title, String recap) {
      XSLFSlide slide = show.createSlide();
      XSLFTextBox titleBox = slide.createTextBox();
      titleBox.setAnchor(new Rectangle2D.Double(MARGIN_PT, MARGIN_PT,
         SLIDE_WIDTH_PT - 2 * MARGIN_PT, 80));
      titleBox.setText(title == null ? "" : title);

      if(recap != null && !recap.isBlank()) {
         XSLFTextBox recapBox = slide.createTextBox();
         recapBox.setAnchor(new Rectangle2D.Double(MARGIN_PT, MARGIN_PT + 90,
            SLIDE_WIDTH_PT - 2 * MARGIN_PT, SLIDE_HEIGHT_PT - 2 * MARGIN_PT - 90));
         recapBox.setText(recap);
      }
   }

   private void addCaption(XSLFSlide slide, String title, String caption) {
      XSLFTextBox captionBox = slide.createTextBox();
      captionBox.setAnchor(new Rectangle2D.Double(MARGIN_PT, MARGIN_PT / 2.0,
         SLIDE_WIDTH_PT - 2 * MARGIN_PT, 40));
      String text = (title == null ? "" : title) +
         (caption != null && !caption.isBlank() ? " — " + caption : "");
      captionBox.setText(text);
   }

   private void addFailurePlaceholder(XSLFSlide slide, String title) {
      XSLFTextBox box = slide.createTextBox();
      box.setAnchor(new Rectangle2D.Double(MARGIN_PT, MARGIN_PT + 90,
         SLIDE_WIDTH_PT - 2 * MARGIN_PT, 60));
      box.setText("Failed to render: " + (title == null ? "" : title));
   }

   /** Appends one or more insights-only slides (no chart image, just a text box near the full
    *  slide) so long insights are never truncated — each slide holds as much stripped text as
    *  fits per {@link #chunkInsightsText}. */
   private void addInsightsSlides(XMLSlideShow show, String insightsMarkdown) {
      String stripped = MarkdownPlainText.strip(insightsMarkdown);

      for(String chunk : chunkInsightsText(stripped)) {
         XSLFSlide slide = show.createSlide();
         XSLFTextBox box = slide.createTextBox();
         box.setAnchor(new Rectangle2D.Double(MARGIN_PT, MARGIN_PT,
            SLIDE_WIDTH_PT - 2 * MARGIN_PT, SLIDE_HEIGHT_PT - 2 * MARGIN_PT));
         XSLFTextRun run = box.setText(chunk);
         run.setFontSize(INSIGHTS_FONT_SIZE_PT);
      }
   }

   /** Estimates a per-slide character budget from real font metrics (measured headlessly via
    *  java.awt.Font/FontMetrics — confirmed to work without a display) and splits plainText into
    *  that many characters per chunk, snapping each split point back to the nearest preceding
    *  space so a word is never broken across two slides. The one exception is a single token
    *  longer than an entire slide's budget, which is hard-split (pathological input, not expected
    *  from real insights text). */
   private List<String> chunkInsightsText(String plainText) {
      Font font = new Font(Font.SANS_SERIF, Font.PLAIN, (int) INSIGHTS_FONT_SIZE_PT);
      BufferedImage measuring = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = measuring.createGraphics();
      FontMetrics fm = g.getFontMetrics(font);
      g.dispose();

      double boxWidthPt = SLIDE_WIDTH_PT - 2 * MARGIN_PT;
      double boxHeightPt = SLIDE_HEIGHT_PT - 2 * MARGIN_PT;
      double avgCharWidthPt = fm.stringWidth("abcdefghijklmnopqrstuvwxyz") / 26.0;
      int charsPerLine = Math.max(1, (int) (boxWidthPt / avgCharWidthPt));
      int linesPerSlide = Math.max(1, (int) (boxHeightPt / fm.getHeight()));
      int charsPerSlide = charsPerLine * linesPerSlide;

      List<String> chunks = new ArrayList<>();
      String remaining = plainText.trim();

      while(!remaining.isEmpty()) {
         if(remaining.length() <= charsPerSlide) {
            chunks.add(remaining);
            break;
         }

         int splitAt = remaining.lastIndexOf(' ', charsPerSlide);

         if(splitAt <= 0) {
            splitAt = charsPerSlide; // pathological: no space within budget — hard split
         }

         chunks.add(remaining.substring(0, splitAt).trim());
         remaining = remaining.substring(splitAt).trim();
      }

      return chunks;
   }

   /** 16:9 widescreen in points (1in = 72pt): 13.33in x 7.5in. */
   private static final int SLIDE_WIDTH_PT = 960;
   private static final int SLIDE_HEIGHT_PT = 540;
   private static final int MARGIN_PT = 40;
   private static final double INSIGHTS_FONT_SIZE_PT = 16.0;
}
