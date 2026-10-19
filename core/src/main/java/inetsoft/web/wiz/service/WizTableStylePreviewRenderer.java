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

import inetsoft.report.*;
import inetsoft.report.internal.ReportGenerator;
import inetsoft.report.internal.TablePaintable;
import inetsoft.report.style.TableStyle;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.CoreTool;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Enumeration;

/**
 * Renders an in-memory {@link TableStyle} to a PNG with the same {@code TabularSheet} +
 * {@code ReportGenerator} chain {@code TableViewStylePaneController.getImagePreview} uses for the
 * style picker, but takes the style itself rather than a name to look up, and lets failures
 * propagate instead of answering an empty 200.
 */
@Service
public class WizTableStylePreviewRenderer {
   public WizTableStylePreviewRenderer(LibManagerProvider libManagerProvider, Cluster cluster) {
      this.libManagerProvider = libManagerProvider;
      this.cluster = cluster;
   }

   /**
    * @param style a style already wrapped around its sample table.
    *
    * @return the PNG bytes of the table, cropped to the table's own bounds.
    */
   public byte[] renderPng(TableStyle style) throws Exception {
      TabularSheet sheet = new TabularSheet(libManagerProvider, cluster);
      // Wide enough that the sample table is never split across pages: only the first page is
      // painted.
      sheet.setPageSize(new Size(10, 10));
      sheet.setMargin(new Margin());
      sheet.setHeaderFromEdge(0);
      sheet.setFooterFromEdge(0);
      sheet.addTable(0, 0, style);

      Enumeration<?> pages = ReportGenerator.generate(sheet);

      if(pages == null || !pages.hasMoreElements()) {
         throw new IllegalStateException("The table style preview produced no page.");
      }

      StylePage page = (StylePage) pages.nextElement();
      Paintable table = null;

      for(int i = 0; i < page.getPaintableCount(); i++) {
         Paintable pt = page.getPaintable(i);

         if(pt instanceof TablePaintable) {
            table = pt;
            break;
         }
      }

      if(table == null) {
         throw new IllegalStateException("The table style preview produced no table.");
      }

      Rectangle rect = table.getBounds();
      BufferedImage img = new BufferedImage(
         rect.width + 1, rect.height + 1, BufferedImage.TYPE_INT_ARGB);
      Graphics g = img.getGraphics();

      try {
         g.translate(-rect.x, -rect.y);
         table.paint(g);
      }
      finally {
         g.dispose();
      }

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      CoreTool.writePNG(img, buffer);
      return buffer.toByteArray();
   }

   private final LibManagerProvider libManagerProvider;
   private final Cluster cluster;
}
