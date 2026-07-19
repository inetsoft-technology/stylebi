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
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.vslayout.PrintInfo;
import inetsoft.uql.viewsheet.vslayout.PrintLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link PrintLayout} for the board-export "Component A" endpoint
 * (POST /api/wiz/viewsheet/export-report): a title/recap header block, followed by each kept
 * chart on its own page with a caption line, rebuilt fresh (idempotent) on every export call.
 */
public class WizPrintLayoutBuilder {
   public record ChartCaption(String title, String caption, int order) {}

   public PrintLayout build(Viewsheet dashboard, String pageSize, String title, String recap,
                             List<ChartCaption> charts)
   {
      PrintLayout layout = new PrintLayout();
      layout.setPrintInfo(buildPrintInfo(pageSize));
      layout.setHeaderLayouts(new ArrayList<>());
      layout.setFooterLayouts(new ArrayList<>());
      layout.setVSAssemblyLayouts(new ArrayList<>());
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
}
