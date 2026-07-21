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
package inetsoft.web.wiz.service;

import inetsoft.graph.internal.DimensionD;
import inetsoft.report.Margin;
import inetsoft.report.Size;
import inetsoft.report.StyleConstants;
import inetsoft.report.internal.PaperSize;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.AnnotationVSUtil;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.uql.viewsheet.vslayout.PrintInfo;
import inetsoft.uql.viewsheet.vslayout.PrintLayout;
import inetsoft.uql.viewsheet.vslayout.VSAssemblyLayout;
import inetsoft.uql.viewsheet.vslayout.VSEditableAssemblyLayout;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link PrintLayout} for the board-export "Component A" endpoint
 * (POST /api/wiz/viewsheet/export-report): a title/recap header block, followed by each kept
 * chart on its own page with a caption line, rebuilt fresh (idempotent) on every export call.
 */
@Component
public class WizPrintLayoutBuilder {
   public record ChartCaption(String title, String caption, int order, String insightsMarkdown) {
      public ChartCaption(String title, String caption, int order) {
         this(title, caption, order, null);
      }
   }

   public PrintLayout build(Viewsheet dashboard, String pageSize, String title, String recap,
                             List<ChartCaption> charts)
   {
      PrintLayout layout = new PrintLayout();
      layout.setPrintInfo(buildPrintInfo(pageSize));
      // scaleFont defaults to 0f on a bare PrintLayout; AbstractLayout.apply() then stamps
      // RScaleFont=0 onto every assembly's cell formats, and VSCompositeFormat.getFont()
      // multiplies the font size by rscaleFont — so table/crosstab cells render at font size 0
      // (invisible text, and zero-width auto columns) while charts (painted by the graph engine)
      // are unaffected. 1f == "no font scaling", matching an interactively-created print layout.
      layout.setScaleFont(1f);
      layout.setHeaderLayouts(new ArrayList<>());
      layout.setFooterLayouts(new ArrayList<>());

      List<VSAssembly> topLevel = resolveTopLevelAssemblies(dashboard);

      if(topLevel.size() != charts.size()) {
         throw new IllegalStateException(
            "Dashboard has " + topLevel.size() + " top-level assemblies but " + charts.size() +
            " charts were requested — the composed dashboard and the board's curation have " +
            "desynced (see WizPrintLayoutBuilder's Javadoc / the plan's Global Constraints risk note)");
      }

      List<ChartCaption> ordered = charts.stream()
         .sorted(java.util.Comparator.comparingInt(ChartCaption::order))
         .collect(java.util.stream.Collectors.toList());

      List<VSAssemblyLayout> vsLayouts = new ArrayList<>();
      int page = 0;
      // Stride each subsequent chart to the real printable page height (paper minus top/bottom
      // margins), not a fixed 11in — otherwise later pages drift down and leave awkward whitespace.
      int pageStride = pageContentHeightPt(pageSize);

      // Page 1: report-style header — a prominent title, a "Generated <date>" line closed by a
      // thin rule, and the session recap as a markdown-stripped summary. Split into separate text
      // boxes because a single box can only carry one font. Returns the y where the body begins.
      int headerBottom = addReportHeader(vsLayouts, title, recap);

      for(int i = 0; i < ordered.size(); i++) {
         ChartCaption c = ordered.get(i);
         VSAssembly assembly = topLevel.get(i);
         int pageTop = page * pageStride;
         int captionY = i == 0 ? headerBottom : pageTop;
         int chartY = captionY + CAPTION_HEIGHT_PT;

         // Build caption text: title always shown, caption appended after an em-dash when present.
         // Styled as a slate-blue bold section header with a thin accent rule above the chart.
         String captionText = c.title() +
            (c.caption() != null && !c.caption().isBlank() ? " — " + c.caption() : "");
         vsLayouts.add(styledTextLayout("wizExportCaption_" + i, captionText,
            new Point(0, captionY), new Dimension(PAGE_CONTENT_WIDTH_PT, CAPTION_HEIGHT_PT),
            Font.BOLD, CAPTION_FONT_PT, CAPTION_COLOR, true));
         vsLayouts.add(new VSAssemblyLayout(assembly.getName(), new Point(0, chartY),
            new Dimension(PAGE_CONTENT_WIDTH_PT, CHART_HEIGHT_PT)));

         if(c.insightsMarkdown() != null && !c.insightsMarkdown().isBlank()) {
            int insightsY = chartY + CHART_HEIGHT_PT;
            vsLayouts.add(textLayout("wizExportInsights_" + i, MarkdownPlainText.strip(c.insightsMarkdown()),
               new Point(0, insightsY), new Dimension(PAGE_CONTENT_WIDTH_PT, INSIGHTS_HEIGHT_PT)));
         }

         page++;
      }

      layout.setVSAssemblyLayouts(vsLayouts);
      return layout;
   }

   /** Printable content height in points: paper height minus top/bottom margins. */
   private int pageContentHeightPt(String pageSize) {
      String canonicalName = PAGE_SIZE_NAMES.get(pageSize == null ? "" : pageSize.toLowerCase());
      Size size = PaperSize.getSize(canonicalName);
      return (int) Math.round((size.height - MARGIN_TOP_IN - MARGIN_BOTTOM_IN) * 72);
   }

   private PrintInfo buildPrintInfo(String pageSize) {
      String canonicalName = PAGE_SIZE_NAMES.get(pageSize == null ? "" : pageSize.toLowerCase());

      if(canonicalName == null) {
         throw new IllegalArgumentException("Unknown pageSize: " + pageSize + " (expected a4 or letter)");
      }

      Size size = PaperSize.getSize(canonicalName);
      PrintInfo info = new PrintInfo();
      info.setPaperType(canonicalName);
      info.setUnit("inches");
      info.setSize(new DimensionD(size.width, size.height));
      info.setMargin(new Margin(MARGIN_TOP_IN, MARGIN_LEFT_IN, MARGIN_BOTTOM_IN, MARGIN_RIGHT_IN));
      return info;
   }

   private static final Map<String, String> PAGE_SIZE_NAMES = Map.of(
      "a4", "A4 [210x297 mm]",
      "letter", "Letter [8.5x11 in]"
   );

   /** ~14mm top/bottom, ~12mm left/right (design spec), converted to inches (PrintInfo's native unit). */
   private static final float MARGIN_TOP_IN = 0.55f;
   private static final float MARGIN_BOTTOM_IN = 0.55f;
   private static final float MARGIN_LEFT_IN = 0.47f;
   private static final float MARGIN_RIGHT_IN = 0.47f;

   /** Mirrors AbstractLayout.apply()'s own filter for which assemblies need a layout entry
    *  (annotations and container children are skipped there too — see AbstractLayout.java). */
   private List<VSAssembly> resolveTopLevelAssemblies(Viewsheet dashboard) {
      List<VSAssembly> result = new ArrayList<>();

      if(dashboard.getAssemblies() == null) {
         return result;
      }

      for(Assembly a : dashboard.getAssemblies()) {
         if(!(a instanceof VSAssembly vsAssembly)) {
            continue;
         }

         if(AnnotationVSUtil.isAnnotation(vsAssembly) || vsAssembly.getContainer() != null) {
            continue;
         }

         result.add(vsAssembly);
      }

      result.sort(java.util.Comparator.comparingInt(
         a -> a.getVSAssemblyInfo().getPixelOffset().y));
      return result;
   }

   /**
    * Emits the page-1 report header (title, generated-date line + rule, recap summary) and
    * returns the y-coordinate (in points) where the body content should start.
    */
   private int addReportHeader(List<VSAssemblyLayout> vsLayouts, String title, String recap) {
      String heading = title == null || title.isBlank() ? "Analysis Report" : title.trim();
      int y = 0;

      vsLayouts.add(styledTextLayout("wizExportTitle", heading, new Point(0, y),
         new Dimension(PAGE_CONTENT_WIDTH_PT, TITLE_HEIGHT_PT),
         Font.BOLD, TITLE_FONT_PT, TITLE_COLOR, false));
      y += TITLE_HEIGHT_PT;

      vsLayouts.add(styledTextLayout("wizExportDate", generatedDateLine(), new Point(0, y),
         new Dimension(PAGE_CONTENT_WIDTH_PT, DATE_HEIGHT_PT),
         Font.PLAIN, DATE_FONT_PT, DATE_COLOR, true));
      y += DATE_HEIGHT_PT;

      String summary = recap == null ? "" : MarkdownPlainText.strip(recap).trim();

      if(!summary.isEmpty()) {
         y += SUMMARY_GAP_PT;
         vsLayouts.add(styledTextLayout("wizExportSummary", summary, new Point(0, y),
            new Dimension(PAGE_CONTENT_WIDTH_PT, SUMMARY_HEIGHT_PT),
            Font.PLAIN, SUMMARY_FONT_PT, SUMMARY_COLOR, false));
         y += SUMMARY_HEIGHT_PT;
      }

      return y + HEADER_BOTTOM_GAP_PT;
   }

   private VSEditableAssemblyLayout textLayout(String name, String text, Point pos, Dimension size) {
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.setTextValue(text);
      info.setText(text);
      return new VSEditableAssemblyLayout(info, name, pos, size);
   }

   /** "Generated <Month D, YYYY>" for the current server date. */
   String generatedDateLine() {
      return "Generated " + java.time.LocalDate.now()
         .format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.US));
   }

   private VSEditableAssemblyLayout styledTextLayout(String name, String text, Point pos,
                                                     Dimension size, int fontStyle, int fontSize,
                                                     String foreground, boolean bottomRule)
   {
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.setTextValue(text);
      info.setText(text);

      inetsoft.uql.viewsheet.VSFormat fmt = info.getFormat().getDefaultFormat();
      fmt.setFontValue(inetsoft.uql.viewsheet.internal.VSAssemblyInfo.getDefaultFont(fontStyle, fontSize));
      fmt.setForegroundValue(foreground);
      fmt.setAlignmentValue(StyleConstants.H_LEFT | StyleConstants.V_CENTER);
      fmt.setWrappingValue(true);

      if(bottomRule) {
         fmt.setBordersValue(new java.awt.Insets(0, 0, StyleConstants.THIN_LINE, 0));
         fmt.setBorderColorsValue(new inetsoft.uql.viewsheet.BorderColors(
            RULE_COLOR, RULE_COLOR, RULE_COLOR, RULE_COLOR));
      }

      return new VSEditableAssemblyLayout(info, name, pos, size);
   }

   /** Points-per-inch used by VsToReportConverter's print-layout-to-report conversion
    *  (see VsToReportConverter.java:3226, INCH_POINT = 72) — VSAssemblyLayout position/size
    *  values feed that same pipeline, so page/content geometry below is expressed in points.
    *  CONFIRM in Task 3's integration test that content doesn't clip/overlap across the page
    *  boundary; adjust these constants if it does. */
   private static final int PAGE_CONTENT_WIDTH_PT = 8 * 72;
   private static final int CAPTION_HEIGHT_PT = 30;
   private static final int CAPTION_FONT_PT = 13;
   private static final String CAPTION_COLOR = "0x3B6EA5";   // slate-blue accent
   private static final int CHART_HEIGHT_PT = 400;
   private static final int INSIGHTS_HEIGHT_PT = 150;

   // Report-header geometry + type (points / font sizes / hex colors).
   // TITLE_HEIGHT_PT fits a two-line wrapped title at TITLE_FONT_PT so the date line below it
   // is never crowded; a short one-line title just centers with extra whitespace.
   private static final int TITLE_HEIGHT_PT = 54;
   private static final int TITLE_FONT_PT = 20;
   private static final String TITLE_COLOR = "0x1a1a1a";
   private static final int DATE_HEIGHT_PT = 18;
   private static final int DATE_FONT_PT = 9;
   private static final String DATE_COLOR = "0x888888";
   private static final int SUMMARY_GAP_PT = 6;
   private static final int SUMMARY_HEIGHT_PT = 64;
   private static final int SUMMARY_FONT_PT = 11;
   private static final String SUMMARY_COLOR = "0x2b2b2b";
   private static final int HEADER_BOTTOM_GAP_PT = 12;
   // Slate-blue accent (matches the chart palette + PPTX): the header rule and caption rules.
   private static final Color RULE_COLOR = new Color(0x3B6EA5);
}
