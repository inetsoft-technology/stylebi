/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import inetsoft.cluster.*;
import inetsoft.report.Size;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Bounds;
import inetsoft.report.internal.Common;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.painter.CCWRotatePresenter;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.ObjectWrapper;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.service.BinaryTransferService;
import org.apache.commons.io.output.DeferredFileOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.security.Principal;
import java.util.Optional;

@Service
@ClusterProxy
public class PresenterService {

   public PresenterService(ViewsheetService viewsheetService,
                           VSLayoutService vsLayoutService,
                           BinaryTransferService binaryTransferService)
   {
      this.viewsheetService = viewsheetService;
      this.vsLayoutService = vsLayoutService;
      this.binaryTransferService = binaryTransferService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public BinaryTransfer getPresenterImage(@ClusterProxyKey String runtimeId, String assembly, int row, int column, int width,
                                           int height, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();

      PresenterRef.CONVERTER.set(VSUtil.getPreConverter(vs));
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      VSTableLens lens = null;

      if(box.isPresent()) {
         box.get().lockWrite();

         try {
            lens = box.get().getVSTableLens(assembly, false);
         }
         finally {
            box.get().unlockWrite();
         }
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
            return null;
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

            // A modern table paints the cell's own background under the image. Filling the image
            // with it too laid a translucent tint on twice, and the image is inset by the cell's
            // padding, so it showed as a darker box a little smaller than the cell.
            Assembly tableAssembly = vs.getAssembly(assembly);
            VSAssemblyInfo tableInfo = tableAssembly instanceof VSAssembly ?
               ((VSAssembly) tableAssembly).getVSAssemblyInfo() : null;
            boolean modern = tableInfo != null && VizContext.of(tableInfo).modern;
            Color fill = presenterFill(lens.getBackground(row, column), modern);

            if(fill != null) {
               g.setColor(fill);
               g.fillRect(0, 0, width, height);
            }

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

            alpha = presenterAlpha(alpha, lens.getBackground(row, column), modern);

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

      String key = "/" + PresenterService.class.getName() + "_" + runtimeId + "_" + assembly + "_" + row + "_" + column;
      BinaryTransfer data = binaryTransferService.createBinaryTransfer(key);
      DeferredFileOutputStream out = binaryTransferService.createOutputStream(data);

      ImageIO.write(image, "png", out);  // "jpeg" is also fine
      binaryTransferService.closeOutputStream(data, out);
      return data;
   }

   /**
    * The alpha a presenter is painted with. A modern table's cell takes its alpha from the
    * translucent card tint of its background, which the background fill already carries, so the
    * presenter itself is painted opaque. A classic table keeps the alpha as it is.
    */
   static int presenterAlpha(int alpha, Color background, boolean modern) {
      // the alpha getAlpha derives from the background color's own alpha, not one the author set
      if(modern && alpha != 100 && background != null &&
         alpha == (int) ((background.getAlpha() / 255.0) * 100))
      {
         return 100;
      }

      return alpha;
   }

   /**
    * The color a presenter's image is filled with before the presenter paints, or null for no
    * fill. A modern table's cell already paints its own background, so the image is left clear
    * and does not double it. A classic table fills the image with the cell's background, or
    * with a clear color when it has none, as it always did.
    */
   static Color presenterFill(Color background, boolean modern) {
      if(modern) {
         return null;
      }

      return background != null ? background : new Color(255, 255, 255, 0);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public BinaryTransfer getPresenterImage(@ClusterProxyKey String runtimeId, String assembly,
                                 int width, int height, boolean layout, int layoutRegion,
                                 Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();
      TextVSAssembly text;

      if(layout) {
         PrintLayout printLayout = vs.getLayoutInfo().getPrintLayout();
         VSAssemblyLayout layoutAssembly =
            vsLayoutService.findAssemblyLayout(printLayout, assembly, layoutRegion)
               .orElse(null);

         if(!(layoutAssembly instanceof VSEditableAssemblyLayout)) {
            return null;
         }

         text = new TextVSAssembly(vs, layoutAssembly.getName());
         text.setVSAssemblyInfo(((VSEditableAssemblyLayout) layoutAssembly).getInfo());
      }
      else {
         text = (TextVSAssembly) vs.getAssembly(assembly);
      }


      if(box.isEmpty()) {
         return null;
      }

      PresenterRef.CONVERTER.set(VSUtil.getPreConverter(vs));
      PresenterPainter painter = VSUtil.createPainter(text);

      if(painter == null) {
         return null;
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

      String key = "/" + PresenterService.class.getName() + "_" + runtimeId + "_" + assembly + "_" + layoutRegion;
      BinaryTransfer data = binaryTransferService.createBinaryTransfer(key);
      DeferredFileOutputStream out = binaryTransferService.createOutputStream(data);

      ImageIO.write(image, "png", out);  // "jpeg" is also fine
      binaryTransferService.closeOutputStream(data, out);
      return data;
   }

   private static final Logger LOG = LoggerFactory.getLogger(PresenterController.class);
   private final ViewsheetService viewsheetService;
   private final VSLayoutService vsLayoutService;
   private final BinaryTransferService binaryTransferService;
}
