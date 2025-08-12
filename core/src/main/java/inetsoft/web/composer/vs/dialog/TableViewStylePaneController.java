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
package inetsoft.web.composer.vs.dialog;

import inetsoft.graph.internal.GDefaults;
import inetsoft.report.*;
import inetsoft.report.internal.ReportGenerator;
import inetsoft.report.internal.StyleTreeModel;
import inetsoft.report.internal.TablePaintable;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.style.TableStyle;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.Organization;
import inetsoft.util.*;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.*;

@Controller
public class TableViewStylePaneController {
   /**
    * Get preview style, code from FlashWebHandler.process
    * @param style      image style
    * @param isStyle    image isStyle
    * @param response   the http response
    */
   @GetMapping(value = "composer/vs/table-view-style-pane/preview/{style}/{isStyle}")
   public void getImagePreview(@PathVariable("style") String style,
                               @PathVariable("isStyle") boolean isStyle,
                               HttpServletResponse response)
   {
      try {
         response.setContentType("image/png");
         OutputStream out = response.getOutputStream();
         TabularSheet sheet = new TabularSheet();
         style = Tool.byteDecode(style);

         if(isStyle) {
            sheet.setPageSize(new Size(3.8, 8));
         }
         else {
            sheet.setPageSize(new Size(1.5, 7));
         }

         sheet.setMargin(new Margin());
         sheet.setHeaderFromEdge(0);
         sheet.setFooterFromEdge(0);

         if(style == null || "noStyle".equals(style) || "null".equals(style)) {
            String[][] data = new String[5][1];
            data[2][0] = Catalog.getCatalog().getString("No Style");
            DefaultTableLens lens = new DefaultTableLens(data);
            lens.setRowHeight(16);
            lens.setFont(GDefaults.DEFAULT_TEXT_FONT);
            lens.setAlignment(StyleConstants.V_CENTER);
            lens.setAlignment(StyleConstants.H_CENTER);
            lens.setColBorder(StyleConstants.NO_BORDER);
            lens.setRowBorder(StyleConstants.NO_BORDER);
            sheet.addTable(0, 0, lens);
         }
         else {
               TableStyle tabStyle = StyleTreeModel.get(style);

               if(tabStyle == null) {
                  //handle globally visible styles
                  if(SUtil.isDefaultVSGloballyVisible()) {
                     tabStyle = StyleTreeModel.get(style, Organization.getDefaultOrganizationID());
                  }
               }

               if(tabStyle == null) {

                  if(!style.startsWith("inetsoft.report.style.")) {
                     style = "inetsoft.report.style." + style;
                  }

                  if(Class.forName(style) != null) {
                     tabStyle = (TableStyle) Class.forName(style).newInstance();
                  }
               }

            String[][] data = isStyle ? new String[5][4] : new String[4][3];

            for(int i = 0; i < data.length; i++) {
               for(int j = 0; j < data[i].length; j++) {
                  data[i][j] = isStyle ? i + "," + j : j + "";
               }
            }

            DefaultTableLens lens = new DefaultTableLens(data);

            lens.setHeaderColCount(1);
            tabStyle.setTable(lens);
            sheet.addTable(0, 0, tabStyle);
         }

         Enumeration enumeration = ReportGenerator.generate(sheet);

         if(enumeration != null && enumeration.hasMoreElements()) {
            StylePage stylePage = (StylePage) enumeration.nextElement();
            Paintable pt = null;

            for(int i = 0; i < stylePage.getPaintableCount(); i++) {
               pt = stylePage.getPaintable(i);

               if(pt instanceof TablePaintable) {
                  break;
               }
            }

            if(!isStyle) {
               BufferedImage img =
                  new BufferedImage(110, 67, BufferedImage.TYPE_INT_ARGB);
               Graphics g = img.getGraphics();
               g.translate(0, 1);
               pt.paint(g);

               //write to in-memory buffer first to prevent issues with Spring async response output stream
               ByteArrayOutputStream buffer = new ByteArrayOutputStream();
               CoreTool.writePNG(img, buffer);
               IOUtils.write(buffer.toByteArray(), out);
            }
            else {
               Rectangle rect = pt.getBounds();
               BufferedImage img = new BufferedImage(
                  rect.width + 1, rect.height + 1, BufferedImage.TYPE_INT_ARGB);
               Graphics g = img.getGraphics();
               g.translate(-rect.x, -rect.y);
               pt.paint(g);

               //write to in-memory buffer first to prevent issues with Spring async response output stream
               ByteArrayOutputStream buffer = new ByteArrayOutputStream();
               CoreTool.writePNG(img, buffer);
               IOUtils.write(buffer.toByteArray(), out);
            }
         }

         out.flush();
      }
      catch(Exception e) {
         LOG.error("Failed to process request", e);
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(TableViewStylePaneController.class);
}
