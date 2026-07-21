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

      // Page 1: title + recap header block.
      vsLayouts.add(textLayout("wizExportTitleRecap", buildTitleRecapText(title, recap),
         new Point(0, 0), new Dimension(PAGE_CONTENT_WIDTH_PT, TITLE_BLOCK_HEIGHT_PT)));

      for(int i = 0; i < ordered.size(); i++) {
         ChartCaption c = ordered.get(i);
         VSAssembly assembly = topLevel.get(i);
         int pageTop = page * PAGE_HEIGHT_PT;
         int captionY = i == 0 ? TITLE_BLOCK_HEIGHT_PT : pageTop;
         int chartY = captionY + CAPTION_HEIGHT_PT;

         // Build caption text: title always shown, caption appended after an em-dash when present.
         String captionText = c.title() +
            (c.caption() != null && !c.caption().isBlank() ? " — " + c.caption() : "");
         vsLayouts.add(textLayout("wizExportCaption_" + i, captionText,
            new Point(0, captionY), new Dimension(PAGE_CONTENT_WIDTH_PT, CAPTION_HEIGHT_PT)));
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

   private String buildTitleRecapText(String title, String recap) {
      StringBuilder sb = new StringBuilder(title == null ? "" : title);

      if(recap != null && !recap.isBlank()) {
         sb.append("\n").append(recap);
      }

      return sb.toString();
   }

   private VSEditableAssemblyLayout textLayout(String name, String text, Point pos, Dimension size) {
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.setTextValue(text);
      info.setText(text);
      return new VSEditableAssemblyLayout(info, name, pos, size);
   }

   /** Points-per-inch used by VsToReportConverter's print-layout-to-report conversion
    *  (see VsToReportConverter.java:3226, INCH_POINT = 72) — VSAssemblyLayout position/size
    *  values feed that same pipeline, so page/content geometry below is expressed in points.
    *  CONFIRM in Task 3's integration test that content doesn't clip/overlap across the page
    *  boundary; adjust these constants if it does. */
   private static final int PAGE_HEIGHT_PT = 11 * 72;   // ~A4/Letter portrait height, conservative
   private static final int PAGE_CONTENT_WIDTH_PT = 8 * 72;
   private static final int TITLE_BLOCK_HEIGHT_PT = 100;
   private static final int CAPTION_HEIGHT_PT = 30;
   private static final int CHART_HEIGHT_PT = 400;
   private static final int INSIGHTS_HEIGHT_PT = 150;
}
