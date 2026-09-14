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

import inetsoft.report.StyleFont;
import inetsoft.web.wiz.service.MarkdownModel;
import inetsoft.web.wiz.service.PptxDeckMerger;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;

import java.awt.Color;
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
 *
 * <p>Each chart's own insights text shares that chart's slide (bug-76110 round 2): the imported
 * chart picture is pre-sized upstream ({@code WizViewsheetExportController.enlargeChartForSlide})
 * to occupy only the top of the slide, and {@link #addInsightsSlides} packs as much of the
 * chart's insightsMarkdown as fits into the reserved region below it, spilling anything left
 * over into additional "(cont'd)" insights-only slides. A failed chart's placeholder slide
 * reserves no such region, so its insights always get their own dedicated slide(s).
 *
 * <p>The board's own title/recap shares the FIRST chart's slide as a compact header (bug-76110
 * round 3), mirroring {@code WizPrintLayoutBuilder}'s PDF-export convention of sharing page 1's
 * header with the first chart ({@code i == 0 ? headerBottom : pageTop}), rather than always
 * getting its own standalone slide the way it did before this round. See {@link #addBoardHeader}
 * for the shared budget this requires on that one slide. This applies whether the first chart
 * succeeded or failed to render — a failed chart's placeholder has ample free room, and the
 * board's title is worth keeping visible regardless of that one chart's own render outcome. Every
 * later chart keeps its own slide(s) exactly as before. {@link #addTitleSlide} (a full standalone
 * slide) survives only as the fallback for an empty {@code slides} list, where there is no chart
 * slide to share a header with — unreachable from the production pptx export endpoint, which
 * rejects an empty {@code charts[]} before ever calling {@link #mergeSlides}, but kept for
 * {@code mergeSlides} itself as a well-defined general-purpose result.
 */
public class PoiPptxDeckMerger implements PptxDeckMerger {
   @Override
   public byte[] mergeSlides(String title, String recap, List<ChartSlide> slides) throws Exception {
      try(XMLSlideShow merged = new XMLSlideShow()) {
         merged.setPageSize(new Dimension(SLIDE_WIDTH_PT, SLIDE_HEIGHT_PT));

         if(slides.isEmpty()) {
            addTitleSlide(merged, title, recap);
         }

         boolean isFirst = true;

         for(ChartSlide slide : slides) {
            boolean sharesBoardHeader = isFirst;
            isFirst = false;

            // Only a successfully-imported chart slide reserves a region for its own insights
            // (addFailurePlaceholder draws no picture and leaves nothing to reserve room below —
            // out of scope here, Redmine #76535). Left null for a failed chart so
            // addInsightsSlides falls back to giving its insights their own dedicated slide(s).
            XSLFSlide chartSlide = null;

            if(slide.failed()) {
               XSLFSlide target = merged.createSlide();
               addFailurePlaceholder(target, slide.title(), sharesBoardHeader);
               addCaption(target, slide.title(), slide.caption(), sharesBoardHeader);

               if(sharesBoardHeader) {
                  addBoardHeader(target, title, recap);
               }
            }
            else {
               try(XMLSlideShow source =
                  new XMLSlideShow(new ByteArrayInputStream(slide.singleSlideDeckBytes())))
               {
                  chartSlide = merged.createSlide();
                  chartSlide.importContent(source.getSlides().get(0));

                  // Added AFTER importContent, not before: empirically verified (see this
                  // task's report) that importContent REPLACES the target slide's shape tree
                  // wholesale — a caption added before importContent is wiped out. Adding it
                  // after is the only ordering under which it survives.
                  addCaption(chartSlide, slide.title(), slide.caption(), sharesBoardHeader);

                  if(sharesBoardHeader) {
                     addBoardHeader(chartSlide, title, recap);
                  }
               }
            }

            if(slide.insightsMarkdown() != null && !slide.insightsMarkdown().isBlank()) {
               addInsightsSlides(merged, chartSlide, slide.title(), slide.insightsMarkdown());
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
         SLIDE_WIDTH_PT - 2 * MARGIN_PT, 120));
      titleBox.setText(title == null || title.isBlank() ? "Analysis Report" : title);
      styleBox(titleBox, true, TITLE_FONT_PT, ACCENT);

      if(recap != null && !recap.isBlank()) {
         // Render the recap's markdown (headers/bullets/bold/italic) as styled paragraphs+runs
         // rather than stripping it to plain text. Short enough to fit the cover box.
         XSLFTextBox recapBox = slide.createTextBox();
         recapBox.setAnchor(new Rectangle2D.Double(MARGIN_PT, MARGIN_PT + 130,
            SLIDE_WIDTH_PT - 2 * MARGIN_PT, SLIDE_HEIGHT_PT - 2 * MARGIN_PT - 130));

         for(MarkdownModel.Block block : MarkdownModel.parse(recap)) {
            appendBlock(recapBox, block, RECAP_FONT_PT);
         }
      }
   }

   /** Places the board's own title/recap as a compact header at the top of the FIRST chart's
    *  slide (bug-76110 round 3), sharing that slide with the chart's own caption/picture/insights
    *  instead of getting a standalone slide the way {@link #addTitleSlide} does. The header's
    *  budget ({@link #BOARD_HEADER_HEIGHT_PT}) is fixed and much smaller than the standalone
    *  slide's, since it now competes with that chart's own caption band, picture, and (when
    *  present) insights region on one fixed-height slide -- unlike a PDF page, a PPTX slide has no
    *  continuous-flow overflow to fall back on. The title is a single shrink-then-truncated line
    *  (mirroring {@link #addCaption}'s own approach). The recap is stripped to plain text rather
    *  than rendered as rich markdown the way the standalone slide's recap is: with only
    *  {@link #BOARD_RECAP_HEIGHT_PT} of room (a couple of lines), there isn't a safe budget to
    *  render an open-ended number of markdown blocks the way the full-height standalone box
    *  could, so it gets the same shrink-then-truncate treatment as the caption/title instead. */
   private void addBoardHeader(XSLFSlide slide, String title, String recap) {
      double boxWidthPt = SLIDE_WIDTH_PT - 2 * MARGIN_PT;

      String titleText = title == null || title.isBlank() ? "Analysis Report" : title;
      double titleFontPt = BOARD_TITLE_FONT_PT;

      while(titleFontPt > CAPTION_MIN_FONT_PT &&
            textHeightPt(titleText, titleFontPt, boxWidthPt) > BOARD_TITLE_HEIGHT_PT)
      {
         titleFontPt -= 1.0;
      }

      if(textHeightPt(titleText, titleFontPt, boxWidthPt) > BOARD_TITLE_HEIGHT_PT) {
         titleText = truncateToFit(titleText, titleFontPt, boxWidthPt, BOARD_TITLE_HEIGHT_PT);
      }

      XSLFTextBox titleBox = slide.createTextBox();
      titleBox.setAnchor(new Rectangle2D.Double(MARGIN_PT, BOARD_HEADER_TOP_PT, boxWidthPt,
         BOARD_TITLE_HEIGHT_PT));
      titleBox.setText(titleText);
      styleBox(titleBox, true, titleFontPt, ACCENT);

      if(recap != null && !recap.isBlank()) {
         StringBuilder plain = new StringBuilder();

         for(MarkdownModel.Block block : MarkdownModel.parse(recap)) {
            if(plain.length() > 0) {
               plain.append(' ');
            }

            plain.append(block.plainText());
         }

         String recapText = plain.toString();
         double recapFontPt = BOARD_RECAP_FONT_PT;

         while(recapFontPt > CAPTION_MIN_FONT_PT &&
               textHeightPt(recapText, recapFontPt, boxWidthPt) > BOARD_RECAP_HEIGHT_PT)
         {
            recapFontPt -= 1.0;
         }

         if(textHeightPt(recapText, recapFontPt, boxWidthPt) > BOARD_RECAP_HEIGHT_PT) {
            recapText = truncateToFit(recapText, recapFontPt, boxWidthPt, BOARD_RECAP_HEIGHT_PT);
         }

         XSLFTextBox recapBox = slide.createTextBox();
         recapBox.setAnchor(new Rectangle2D.Double(MARGIN_PT,
            BOARD_HEADER_TOP_PT + BOARD_TITLE_HEIGHT_PT, boxWidthPt, BOARD_RECAP_HEIGHT_PT));
         recapBox.setText(recapText);
         styleBox(recapBox, false, recapFontPt, BODY_COLOR);
      }
   }

   private void addCaption(XSLFSlide slide, String title, String caption, boolean sharesBoardHeader) {
      String text = (title == null ? "" : title) +
         (caption != null && !caption.isBlank() ? " — " + caption : "");
      double boxWidthPt = SLIDE_WIDTH_PT - 2 * MARGIN_PT;

      // FIT THE CAPTION TO ITS BAND. The caption is drawn ON TOP of the imported chart slide, so
      // anything that does not fit the reserved band lands on the chart itself. At a fixed
      // CAPTION_FONT_PT the band holds about two lines; a caption of real prose wraps to three or
      // more and overprinted the plot -- observed in an exported deck, the heading covering the
      // chart's own title and top bars.
      //
      // Shrink first (a smaller caption is still fully readable and loses nothing), and only
      // truncate if it still does not fit at the floor. The untruncated caption remains on the
      // board and in the PDF export, which reserve room for it.
      double fontPt = CAPTION_FONT_PT;

      while(fontPt > CAPTION_MIN_FONT_PT &&
            textHeightPt(text, fontPt, boxWidthPt) > CAPTION_BOX_HEIGHT_PT)
      {
         fontPt -= 1.0;
      }

      if(textHeightPt(text, fontPt, boxWidthPt) > CAPTION_BOX_HEIGHT_PT) {
         text = truncateToFit(text, fontPt, boxWidthPt, CAPTION_BOX_HEIGHT_PT);
      }

      // On the first chart's slide the board header (bug-76110 round 3) already occupies the band
      // this caption would otherwise sit in, so the caption moves below it instead.
      double topPt = sharesBoardHeader
         ? BOARD_HEADER_TOP_PT + BOARD_HEADER_HEIGHT_PT : MARGIN_PT / 2.0;

      XSLFTextBox captionBox = slide.createTextBox();
      captionBox.setAnchor(new Rectangle2D.Double(MARGIN_PT, topPt, boxWidthPt, CAPTION_BOX_HEIGHT_PT));
      captionBox.setText(text);
      styleBox(captionBox, true, fontPt, ACCENT);
   }

   /** Wrapped height of {@code text} at {@code fontPt} within {@code widthPt}, greedily by word.
    *  Uses the same headless java.awt FontMetrics approach as chunkInsightsText. */
   static double textHeightPt(String text, double fontPt, double widthPt) {
      if(text == null || text.isBlank()) {
         return 0;
      }

      Font font = new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.round(fontPt));
      BufferedImage measuring = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = measuring.createGraphics();
      FontMetrics fm = g.getFontMetrics(font);
      g.dispose();

      int lines = 1;
      StringBuilder line = new StringBuilder();

      for(String word : text.trim().split("\\s+")) {
         String candidate = line.length() == 0 ? word : line + " " + word;

         if(fm.stringWidth(candidate) > widthPt && line.length() > 0) {
            lines++;
            line.setLength(0);
            line.append(word);
         }
         else {
            line.setLength(0);
            line.append(candidate);
         }
      }

      return lines * (double) fm.getHeight();
   }

   /** Drop whole words from the end until the text fits, then mark the elision. */
   private static String truncateToFit(String text, double fontPt, double widthPt, double heightPt) {
      String candidate = text.trim();

      while(candidate.contains(" ") && textHeightPt(candidate + "…", fontPt, widthPt) > heightPt) {
         candidate = candidate.substring(0, candidate.lastIndexOf(' ')).trim();
      }

      return candidate + "…";
   }

   /** Apply a uniform bold/size/color to every run in every paragraph of a text box.
    *  Also sets an explicit font family: without one, a run has no Latin-typeface element at
    *  all and resolves purely by OOXML theme inheritance (the deck's theme font), which
    *  {@link #appendBlock} and {@link #appendSpans} below rely on this same explicit-family
    *  approach for too. This matches this codebase's other PPTX-export convention
    *  ({@code PPTValueHelper}, bug #75992) of always writing a resolved family name rather than
    *  leaving typeface resolution to the reader. */
   private void styleBox(XSLFTextBox box, boolean bold, double fontSize, Color color) {
      for(XSLFTextParagraph paragraph : box.getTextParagraphs()) {
         for(XSLFTextRun run : paragraph.getTextRuns()) {
            run.setBold(bold);
            run.setFontSize(fontSize);
            run.setFontColor(color);
            run.setFontFamily(StyleFont.getDefaultFontFamily());
         }
      }
   }

   private void addFailurePlaceholder(XSLFSlide slide, String title, boolean sharesBoardHeader) {
      // On the first chart's slide, stack below the board header + the caption addCaption() just
      // placed under it (8pt gap, matching the gap convention CHART_INSIGHTS_TOP_PT uses below an
      // imported chart picture); otherwise keep the original fixed position.
      double topPt = sharesBoardHeader
         ? BOARD_HEADER_TOP_PT + BOARD_HEADER_HEIGHT_PT + CAPTION_BOX_HEIGHT_PT + 8.0
         : MARGIN_PT + 90;

      XSLFTextBox box = slide.createTextBox();
      box.setAnchor(new Rectangle2D.Double(MARGIN_PT, topPt, SLIDE_WIDTH_PT - 2 * MARGIN_PT, 60));
      box.setText("Failed to render: " + (title == null ? "" : title));
   }

   /** Places a chart's insightsMarkdown as far as it fits into the reserved region below its own
    *  imported picture ({@code chartSlide}, non-null — see {@link #CHART_INSIGHTS_TOP_PT}/
    *  {@link #CHART_INSIGHTS_HEIGHT_PT}), then appends one or more additional insights-only
    *  "(cont'd)" slides for anything left over. When {@code chartSlide} is null (a failed
    *  chart's placeholder reserves no such region), every block goes straight to dedicated
    *  insights-only slide(s), matching the pre-bug-76110-round-2 behavior. Either way, blocks are
    *  rendered as styled paragraphs/bullets/headers (bold + italic runs preserved) and packed by
    *  estimated height so nothing is truncated; a single block taller than a whole slide falls
    *  back to a plain word-split across slides. The first page carries a title identifying the
    *  chart it belongs to ("Insights: <chart title>", or bare "Insights" with no chart title);
    *  every later page appends " (cont'd)" so it's clear a continuation slide is still the same
    *  chart's insights, not a new topic — this holds whether the first page landed on the chart's
    *  own slide or, for a failed chart, on a dedicated one. */
   private void addInsightsSlides(XMLSlideShow show, XSLFSlide chartSlide, String chartTitle,
                                   String insightsMarkdown)
   {
      List<MarkdownModel.Block> blocks = MarkdownModel.parse(insightsMarkdown);

      if(blocks.isEmpty()) {
         return;
      }

      double boxWidthPt = SLIDE_WIDTH_PT - 2 * MARGIN_PT;
      // Content budget shrinks by the title box's reserved height: the title now occupies space
      // the content box used to have exclusive use of.
      double fullPageHeightPt = SLIDE_HEIGHT_PT - 2 * MARGIN_PT - INSIGHTS_TITLE_HEIGHT_PT;
      double firstPageHeightPt = chartSlide != null
         ? CHART_INSIGHTS_CONTENT_HEIGHT_PT : fullPageHeightPt;

      List<List<MarkdownModel.Block>> pages = new ArrayList<>();
      List<MarkdownModel.Block> current = new ArrayList<>();
      double used = 0;
      double capacity = firstPageHeightPt;

      for(MarkdownModel.Block block : blocks) {
         double h = estimateBlockHeightPt(block, boxWidthPt);

         if(h > fullPageHeightPt) {
            // Pathological single block taller than even a full slide: flush, then split its
            // plain text. Judged against fullPageHeightPt, not the current (possibly smaller)
            // capacity — a block that only doesn't fit the chart-slide's reduced budget belongs
            // on the next full page, not chunked.
            if(!current.isEmpty()) {
               pages.add(current);
               current = new ArrayList<>();
               used = 0;
               capacity = fullPageHeightPt;
            }

            // capacity still reflects whatever page this chunked block's first chunk actually
            // lands on (the chart slide's smaller region, if nothing was flushed above and this
            // is still page 0; a full page otherwise) — chunkInsightsText sizes only its first
            // chunk to that budget, and every later chunk to a full page.
            for(String chunk : chunkInsightsText(block.plainText(), capacity)) {
               pages.add(List.of(new MarkdownModel.Block(MarkdownModel.BlockType.PARAGRAPH, 0,
                  List.of(new MarkdownModel.Span(chunk, false, false)))));
            }

            capacity = fullPageHeightPt; // later blocks, if any, start a fresh full page
            continue;
         }

         if(used + h > capacity && !current.isEmpty()) {
            pages.add(current);
            current = new ArrayList<>();
            used = 0;
            capacity = fullPageHeightPt; // every page after the first is a full-height slide
         }

         current.add(block);
         used += h;
      }

      if(!current.isEmpty()) {
         pages.add(current);
      }

      String heading = chartTitle == null || chartTitle.isBlank()
         ? "Insights" : "Insights: " + chartTitle;

      for(int i = 0; i < pages.size(); i++) {
         String pageHeading = i == 0 ? heading : heading + " (cont'd)";

         if(i == 0 && chartSlide != null) {
            addInsightsTitle(chartSlide, pageHeading, CHART_INSIGHTS_TOP_PT);

            XSLFTextBox box = chartSlide.createTextBox();
            box.setAnchor(new Rectangle2D.Double(MARGIN_PT,
               CHART_INSIGHTS_TOP_PT + INSIGHTS_TITLE_HEIGHT_PT, boxWidthPt,
               CHART_INSIGHTS_CONTENT_HEIGHT_PT));

            for(MarkdownModel.Block block : pages.get(i)) {
               appendBlock(box, block, INSIGHTS_FONT_SIZE_PT);
            }
         }
         else {
            XSLFSlide slide = show.createSlide();
            addInsightsTitle(slide, pageHeading, MARGIN_PT);

            XSLFTextBox box = slide.createTextBox();
            box.setAnchor(new Rectangle2D.Double(MARGIN_PT, MARGIN_PT + INSIGHTS_TITLE_HEIGHT_PT,
               boxWidthPt, fullPageHeightPt));

            for(MarkdownModel.Block block : pages.get(i)) {
               appendBlock(box, block, INSIGHTS_FONT_SIZE_PT);
            }
         }
      }
   }

   /** Title box for an insights page, styled like the chart caption boxes (bold, ACCENT), placed
    *  at {@code topPt} — {@link #MARGIN_PT} for a dedicated insights-only slide, or
    *  {@link #CHART_INSIGHTS_TOP_PT} when it shares the chart's own slide. */
   private void addInsightsTitle(XSLFSlide slide, String text, double topPt) {
      XSLFTextBox titleBox = slide.createTextBox();
      titleBox.setAnchor(new Rectangle2D.Double(MARGIN_PT, topPt,
         SLIDE_WIDTH_PT - 2 * MARGIN_PT, INSIGHTS_TITLE_HEIGHT_PT));
      titleBox.setText(text);
      styleBox(titleBox, true, INSIGHTS_TITLE_FONT_PT, ACCENT);
   }

   /** Append one markdown block to a text box as a styled paragraph (with per-span bold/italic). */
   private void appendBlock(XSLFTextBox box, MarkdownModel.Block block, double bodySize) {
      XSLFTextParagraph p = box.addNewTextParagraph();
      p.setSpaceAfter(6.0);

      switch(block.type()) {
      case HEADING:
         appendSpans(p, block.spans(), headingFontPt(block.level(), bodySize), ACCENT, true);
         break;
      case BULLET:
         p.setLeftMargin(20.0);
         p.setIndent(-14.0);
         XSLFTextRun bullet = p.addNewTextRun();
         bullet.setText("•  ");
         bullet.setFontSize(bodySize);
         bullet.setFontColor(ACCENT);
         bullet.setBold(true);
         bullet.setFontFamily(StyleFont.getDefaultFontFamily());
         appendSpans(p, block.spans(), bodySize, BODY_COLOR, false);
         break;
      default:
         appendSpans(p, block.spans(), bodySize, BODY_COLOR, false);
      }
   }

   private void appendSpans(XSLFTextParagraph p, List<MarkdownModel.Span> spans, double size,
                            Color color, boolean forceBold)
   {
      for(MarkdownModel.Span span : spans) {
         if(span.text().isEmpty()) {
            continue;
         }

         XSLFTextRun run = p.addNewTextRun();
         run.setText(span.text());
         run.setFontSize(size);
         run.setFontColor(color);
         run.setBold(forceBold || span.bold());
         run.setItalic(span.italic());
         run.setFontFamily(StyleFont.getDefaultFontFamily());
      }
   }

   private double headingFontPt(int level, double bodySize) {
      return Math.max(bodySize + 2, 26 - Math.max(0, level - 1) * 3);
   }

   private double estimateBlockHeightPt(MarkdownModel.Block block, double boxWidthPt) {
      double fontPt = block.type() == MarkdownModel.BlockType.HEADING
         ? headingFontPt(block.level(), INSIGHTS_FONT_SIZE_PT) : INSIGHTS_FONT_SIZE_PT;
      Font font = new Font(Font.SANS_SERIF, Font.PLAIN, (int) Math.round(fontPt));
      BufferedImage measuring = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = measuring.createGraphics();
      FontMetrics fm = g.getFontMetrics(font);
      g.dispose();

      double avgCharWidthPt = fm.stringWidth("abcdefghijklmnopqrstuvwxyz") / 26.0;
      double usableWidth = block.type() == MarkdownModel.BlockType.BULLET ? boxWidthPt - 20 : boxWidthPt;
      int charsPerLine = Math.max(1, (int) (usableWidth / avgCharWidthPt));
      int lines = Math.max(1, (int) Math.ceil(block.plainText().length() / (double) charsPerLine));
      return lines * fm.getHeight() + BLOCK_SPACING_PT;
   }

   /** Estimates a per-slide character budget from real font metrics (measured headlessly via
    *  java.awt.Font/FontMetrics — confirmed to work without a display) and splits plainText into
    *  that many characters per chunk, snapping each split point back to the nearest preceding
    *  space so a word is never broken across two slides. The one exception is a single token
    *  longer than a chunk's own budget, which is hard-split (pathological input, not expected
    *  from real insights text). Only the first produced chunk is sized to {@code
    *  firstChunkHeightPt} (the chart slide's smaller reserved region, or a full page's height when
    *  there is no such region to reuse); every later chunk gets a full page's budget, since each
    *  one becomes its own full-height insights slide. */
   private List<String> chunkInsightsText(String plainText, double firstChunkHeightPt) {
      Font font = new Font(Font.SANS_SERIF, Font.PLAIN, (int) INSIGHTS_FONT_SIZE_PT);
      BufferedImage measuring = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = measuring.createGraphics();
      FontMetrics fm = g.getFontMetrics(font);
      g.dispose();

      double boxWidthPt = SLIDE_WIDTH_PT - 2 * MARGIN_PT;
      double fullPageHeightPt = SLIDE_HEIGHT_PT - 2 * MARGIN_PT - INSIGHTS_TITLE_HEIGHT_PT;
      double avgCharWidthPt = fm.stringWidth("abcdefghijklmnopqrstuvwxyz") / 26.0;
      int charsPerLine = Math.max(1, (int) (boxWidthPt / avgCharWidthPt));
      int charsForFirstChunk =
         charsPerLine * Math.max(1, (int) (firstChunkHeightPt / fm.getHeight()));
      int charsPerFullSlide =
         charsPerLine * Math.max(1, (int) (fullPageHeightPt / fm.getHeight()));

      List<String> chunks = new ArrayList<>();
      String remaining = plainText.trim();
      boolean first = true;

      while(!remaining.isEmpty()) {
         int budget = first ? charsForFirstChunk : charsPerFullSlide;
         first = false;

         if(remaining.length() <= budget) {
            chunks.add(remaining);
            break;
         }

         int splitAt = remaining.lastIndexOf(' ', budget);

         if(splitAt <= 0) {
            splitAt = budget; // pathological: no space within budget — hard split
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
   // Slate-blue accent (matches the chart palette + the PDF report), used for the cover title and
   // per-slide captions; recap/body stays a dark near-black.
   private static final Color ACCENT = new Color(0x3B6EA5);
   private static final Color BODY_COLOR = new Color(0x2B2B2B);
   private static final double TITLE_FONT_PT = 34.0;
   private static final double RECAP_FONT_PT = 17.0;
   private static final double CAPTION_FONT_PT = 22.0;
   private static final double BLOCK_SPACING_PT = 8.0;   // approx paragraph gap for height estimation
   private static final double INSIGHTS_TITLE_FONT_PT = 22.0;
   private static final int INSIGHTS_TITLE_HEIGHT_PT = 40;
   /** The caption band reserved above the imported chart; anything beyond it overlays the plot. */
   private static final int CAPTION_BOX_HEIGHT_PT = 44;
   /** Floor for caption auto-shrink — below this it stops being a readable heading. */
   private static final double CAPTION_MIN_FONT_PT = 12.0;
   /** Top of the region reserved below the imported chart picture for that chart's own insights
    *  (bug-76110 round 2). Must stay in sync with
    *  {@code WizViewsheetExportController.PPTX_CHART_Y_PX}/{@code PPTX_CHART_H_PX}, which is what
    *  actually leaves this area free by pre-rendering the chart shorter than the full slide. */
   private static final double CHART_INSIGHTS_TOP_PT = 305;
   /** Total height of the reserved region below the chart, including the insights title box. */
   private static final double CHART_INSIGHTS_HEIGHT_PT = 195;
   /** Height left for insights body content once the title box's own height is subtracted. */
   private static final double CHART_INSIGHTS_CONTENT_HEIGHT_PT =
      CHART_INSIGHTS_HEIGHT_PT - INSIGHTS_TITLE_HEIGHT_PT;

   /** Top of the board title/recap header shared with the first chart's slide (bug-76110 round
    *  3) -- same Y the per-chart caption band used to start at on every slide before this round. */
   private static final double BOARD_HEADER_TOP_PT = MARGIN_PT / 2.0;
   private static final double BOARD_TITLE_FONT_PT = 24.0;
   private static final double BOARD_TITLE_HEIGHT_PT = 30;
   private static final double BOARD_RECAP_FONT_PT = 13.0;
   private static final double BOARD_RECAP_HEIGHT_PT = 45;
   /** Total header height (title + recap bands). Must stay in sync with
    *  {@code WizViewsheetExportController.PPTX_FIRST_CHART_HEADER_OFFSET_PX}, which shifts the
    *  first chart's own imported picture down by this same amount (and shrinks it by the same
    *  amount) so its bottom edge lands at exactly the Y every other chart's picture already
    *  does -- {@link #CHART_INSIGHTS_TOP_PT} needs no first-chart-specific variant as a result. */
   private static final double BOARD_HEADER_HEIGHT_PT = BOARD_TITLE_HEIGHT_PT + BOARD_RECAP_HEIGHT_PT;
}
