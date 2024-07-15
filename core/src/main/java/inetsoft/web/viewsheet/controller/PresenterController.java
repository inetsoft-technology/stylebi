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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.Size;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Bounds;
import inetsoft.report.internal.Common;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.painter.CCWRotatePresenter;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.CoreTool;
import inetsoft.util.ObjectWrapper;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.factory.RemainingPath;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.security.Principal;

/**
 * Controller that provides endpoints for presenter related actions.
 */
@Controller
public class PresenterController {
   /**
    * Creates a new instance of <tt>PresenterController</tt>.
    */
   @Autowired
   public PresenterController(ViewsheetService viewsheetService,
                              VSLayoutService vsLayoutService)
   {
      this.viewsheetService = viewsheetService;
      this.vsLayoutService = vsLayoutService;
   }

   /**
    * Get table cell presenter image.
    *
    * @param assembly  the table name
    * @param row       the table row
    * @param column    the table column
    * @param width     the cell width
    * @param height    the cell height
    * @param runtimeId the viewsheet runtime id
    * @param response  the http response
    */
   @GetMapping(value = "/vs/table/presenter/{assembly}/{row}/{col}/{width}/{height}/**")
   public void getPresenterImage(@PathVariable("assembly") String assembly,
                                 @PathVariable("row") int row,
                                 @PathVariable("col") int column,
                                 @PathVariable("width") int width,
                                 @PathVariable("height") int height,
                                 @RemainingPath String runtimeId,
                                 HttpServletResponse response,
                                 Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();

      PresenterRef.CONVERTER.set(VSUtil.getPreConverter(vs));
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      VSTableLens lens;

      try {
         box.lockWrite();
         lens = box.getVSTableLens(assembly, false);
      }
      finally {
         box.unlockWrite();
      }

      if(lens != null) {
         Graphics2D g = (Graphics2D) image.getGraphics();
         Object obj;

         // @by stephenwebster, For Bug #16195
         // Due to the nature of table painting on Flex, a background request
         // for an image (presenter) may request for a row/col outside the
         // bounds of the table (due to selection on VS).  The end result is
         // a unimportant message on the client side.
         try {
            obj = lens.getObject(row, column);
         }
         catch(Exception e) {
            LOG.debug("The object at ["+row+","+column+"] could not be loaded.", e);
            return;
         }

         if(obj instanceof ObjectWrapper) {
            obj = ((ObjectWrapper) obj).unwrap();
         }

         if(obj instanceof Image) {
            g.drawImage((Image) obj, 0, 0, null);
            g.dispose();
         }
         else if(obj instanceof PresenterPainter) {
            PresenterPainter ptr = (PresenterPainter) obj;
            Color bg = lens.getBackground(row, column) != null ?
               lens.getBackground(row, column) : new Color(255, 255, 255, 0);
            g.setFont(lens.getFont(row, column));
            g.setColor(bg);
            g.fillRect(0, 0, width, height);
            Color fg = lens.getForeground(row, column);
            fg = fg == null ? Color.black : fg;
            g.setColor(fg);

            Font font = lens.getFont(row, column);

            if(font != null) {
               g.setFont(font);
               ptr.getPresenter().setFont(font);
            }

            // @by stephenwebster, For bug1426021152663.
            // Same as ExportUtil.getPainterImage
            // Removed artificial padding around image.  This code should not
            // be concerned with positioning / sizing calculations.  It should
            // simply return the image from the presenter. Calling code needs to
            // handle the positioning based on output's needs.
            Bounds b = new Bounds(0, 0, width, height);
            Dimension psize = ptr.getPresenter().getPreferredSize(ptr.getObject());
            b = !ptr.getPresenter().isFill() && psize.width > 0 ? Common.alignPresenterCell(
               b, new Size(psize), lens.getAlignment(row, column)) : b;
            int alpha = lens.getAlpha(row, column);

            if(alpha < 0) {
               alpha = lens.getTable().getAlpha(row, column);
            }

            if(alpha != 100) {
               g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 100f));
            }

            if(ptr.getPresenter() instanceof CCWRotatePresenter) {
               Common.paint(g, b.x, b.y, b.width, b.height, ptr,
                            -2f, -2f, b.width, b.height, b.width, b.height,
                            fg, bg, -b.y, height);
            }
            else {
               Common.paint(g, b.x, b.y, b.width, b.height, ptr,
                            0f, 0f, b.width, b.height, b.width, b.height,
                            fg, bg, -b.y, height);
            }

            g.dispose();
         }
      }

      response.setContentType("image/png");
      OutputStream out = response.getOutputStream();
      CoreTool.writePNG(image, out);
      out.flush();
   }

   /**
    * Get presenter image for VSText.
    *
    * @param assembly  the text assembly name
    * @param width     the text width
    * @param height    the text height
    * @param runtimeId the viewsheet runtime id
    * @param response  the http response
    */
   @GetMapping(value = "/vs/text/presenter/{assembly}/{width}/{height}/{layout}/{layoutRegion}/**")
   public void getPresenterImage(@PathVariable("assembly") String assembly,
                                 @PathVariable("width") int width,
                                 @PathVariable("height") int height,
                                 @PathVariable("layout") boolean layout,
                                 @PathVariable("layoutRegion") int layoutRegion,
                                 @RemainingPath String runtimeId,
                                 HttpServletResponse response,
                                 Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();
      TextVSAssembly text;

      if(layout) {
         PrintLayout printLayout = vs.getLayoutInfo().getPrintLayout();
         VSAssemblyLayout layoutAssembly =
            vsLayoutService.findAssemblyLayout(printLayout, assembly, layoutRegion)
               .orElse(null);

         if(!(layoutAssembly instanceof VSEditableAssemblyLayout)) {
            return;
         }

         text = new TextVSAssembly(vs, layoutAssembly.getName());
         text.setVSAssemblyInfo(((VSEditableAssemblyLayout) layoutAssembly).getInfo());
      }
      else {
         text = (TextVSAssembly) vs.getAssembly(assembly);
      }


      if(box == null) {
         return;
      }

      PresenterRef.CONVERTER.set(VSUtil.getPreConverter(vs));
      PresenterPainter painter = VSUtil.createPainter(text);

      if(painter == null) {
         return;
      }

      VSCompositeFormat fmt = text.getVSAssemblyInfo().getFormat();

      BufferedImage image = new BufferedImage(
         width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = (Graphics2D) image.getGraphics();
      g.setColor(fmt.getBackground());
      g.fillRect(0, 0, width, height);
      Color fg = fmt.getForeground();
      fg = fg == null ? Color.black : fg;
      g.setColor(fg);

      Bounds b = new Bounds(1, 1, width - 2, height - 2);
      Dimension psize = painter.getPresenter().getPreferredSize(
         painter.getObject());
      b = !painter.getPresenter().isFill() ?
         Common.alignCell(b, new Size(psize), fmt.getAlignment()) : b;

      if(painter.getPresenter() instanceof CCWRotatePresenter) {
         Common.paint(g, b.x, b.y, b.width, b.height, painter,
                      -2f, -2f, b.width, b.height, b.width, b.height,
                      fg, fmt.getBackground(), -b.y, height);
      }
      else {
         Common.paint(g, b.x, b.y, b.width, b.height, painter,
                      0f, 0f, b.width, b.height, b.width, b.height,
                      fg, fmt.getBackground(), -b.y, height);
      }

      g.dispose();
      response.setContentType("image/png");
      OutputStream out = response.getOutputStream();
      CoreTool.writePNG(image, out);
      out.flush();
   }

   private static final Logger LOG = LoggerFactory.getLogger(PresenterController.class);
   private final ViewsheetService viewsheetService;
   private final VSLayoutService vsLayoutService;
}
