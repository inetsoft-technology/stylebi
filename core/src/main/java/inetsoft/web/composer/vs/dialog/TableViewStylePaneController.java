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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.ReportGenerator;
import inetsoft.report.internal.StyleTreeModel;
import inetsoft.report.internal.TablePaintable;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.style.TableStyle;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.security.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import inetsoft.web.composer.model.TreeNodeModel;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.security.Principal;
import java.util.List;
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
               CoreTool.writePNG(img, out);
            }
            else {
               Rectangle rect = pt.getBounds();
               BufferedImage img = new BufferedImage(
                  rect.width + 1, rect.height + 1, BufferedImage.TYPE_INT_ARGB);
               Graphics g = img.getGraphics();
               g.translate(-rect.x, -rect.y);
               pt.paint(g);
               CoreTool.writePNG(img, out);
            }
         }

         out.flush();
      }
      catch(Exception e) {
         LOG.error("Failed to process request", e);
      }
   }

   /**
    * Get Tree model for images, mimic of GetTableStyleEvent
    *
    * @param rvs The runtime viewsheet
    *
    * @param freehand
    * @return the image tree model
    */
   public TreeNodeModel getStyleTree(RuntimeViewsheet rvs, Principal principal, boolean freehand) {
      Viewsheet viewsheet = rvs.getViewsheet();
      Catalog catalog = Catalog.getCatalog();
      SecurityEngine securityEngine = SecurityEngine.getSecurity();
      LibManager mgr = LibManager.getManager();

      if(viewsheet == null) {
         return TreeNodeModel.builder().build();
      }

      TreeNodeModel root = TreeNodeModel.builder()
         .label(catalog.getString("Styles"))
         .type("folder")
         .leaf(false)
         .children(getChildNodes(null, mgr, securityEngine, principal, freehand))
         .build();

      return TreeNodeModel.builder().
         children(Collections.singletonList(root))
         .build();
   }

   public TreeNodeModel getStyleTree(Principal principal) throws Exception {
      Catalog catalog = Catalog.getCatalog();
      SecurityEngine securityEngine = SecurityEngine.getSecurity();
      LibManager mgr = LibManager.getManager();

      TreeNodeModel root = TreeNodeModel.builder()
         .label(catalog.getString("Styles"))
         .type("folder")
         .leaf(false)
         .children(getChildNodes(null, mgr, securityEngine, principal, false))
         .build();

      return TreeNodeModel.builder()
         .children(Collections.singletonList(root))
         .build();
   }

   private List<TreeNodeModel> getChildNodes(String parentData, LibManager mgr,
                                             SecurityEngine securityEngine,
                                             Principal principal, boolean freehand)
   {
      List<TreeNodeModel> children = new ArrayList<>();
      List<String> folders = Arrays.asList(mgr.getTableStyleFolders(parentData, true));
      folders.sort(comparator);

      for(String folder : folders) {
         try {
            if(!securityEngine.checkPermission(
               principal, ResourceType.TABLE_STYLE, folder, ResourceAction.READ))
            {
               continue;
            }
         }
         catch(Exception exc) {
            LOG.warn("Failed to check table style folder permission", exc);
         }

         List<TreeNodeModel> folderChildren =
            getChildNodes(folder, mgr, securityEngine, principal, false);

         if(!folderChildren.isEmpty()) {
            children.add(TreeNodeModel.builder()
               .label(getDisplayName(folder))
               .data(folder)
               .type("folder")
               .leaf(false)
               .children(folderChildren)
               .build());
         }
      }

      List<XTableStyle> tstyles = Arrays.asList(mgr.getTableStyles(parentData, true));
      tstyles.sort(comparator);

      for(XTableStyle style : tstyles) {
         try {
            if(!securityEngine.checkPermission(
               principal, ResourceType.TABLE_STYLE, style.getName(), ResourceAction.READ))
            {
               continue;
            }
         }
         catch(Exception exc) {
            LOG.warn("Failed to check table style folder permission", exc);
         }

         children.add(TreeNodeModel.builder()
            .label(getDisplayName(style.getName()))
            .data(style.getID())
            .icon("fa fa-table")
            .type("style")
            .leaf(true)
            .build());
      }

      if(freehand) {
         children.add(TreeNodeModel.builder()
                         .label("Crosstab Style")
                         .data("inetsoft.report.style.CrosstabStyle")
                         .icon("fa fa-table")
                         .type("style")
                         .leaf(true)
                         .build());
      }

      return children;
   }

   private Comparator comparator = (o1, o2) -> {
      String path1;
      String path2;

      if(o1 instanceof XTableStyle && o2 instanceof XTableStyle) {
         XTableStyle style1 = (XTableStyle) o1;
         XTableStyle style2 = (XTableStyle) o2;
         path1 = style1.getName();
         path2 = style2.getName();
         int idx1 = path1.indexOf(StyleTreeModel.SEPARATOR);
         int idx2 = path2.indexOf(StyleTreeModel.SEPARATOR);

         // always put top level styles at the bottom
         if(idx1 >= 0 && idx2 < 0) {
            return -1;
         }
         else if(idx1 < 0 && idx2 >= 0) {
            return 1;
         }
      }
      else {
         path1 = o1 + "";
         path2 = o2 + "";
      }

      return path1.compareTo(path2);
   };

   private String getDisplayName(String path) {
      int index = path.lastIndexOf(LibManager.SEPARATOR);
      String name;
      Catalog catalog = Catalog.getCatalog();

      if(index < 0) {
         name = path;
      }
      else {
         name = path.substring(index + 1);
      }

      return catalog.getString(name);
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(TableViewStylePaneController.class);
}
