/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.gui.viewsheet;

import inetsoft.report.internal.Graphics2DWrapper;
import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * VSFloatable class for view sheet.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class VSFloatable extends VSObject {
   /**
    * Constructor.
    */
   public VSFloatable(Viewsheet vs) {
      super(vs);
   }

   /**
    * Return true if the image is already scaled to high dpi.
    */
   protected boolean isHighDPI() {
      return false;
   }

   /**
    * Get the image for exporting.
    * @return the image for exporting.
    */
   public Image getImage(boolean isExport) {
      Dimension size = getImageSize();
      int w = size.width;
      int h = size.height;

      if(isShadow()) {
         w += 6;
         h += 6;
      }

      w = isExport ? w * 2 : w;
      h = isExport ? h * 2 : h;

      if(w == 0 || h == 0) {
         return null;
      }

      BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
      Graphics2DWrapper g = new Graphics2DWrapper((Graphics2D) image.getGraphics(), false);

      if(!isHighDPI() && isExport) {
         g.scale(2, 2);
      }

      paint(g);
      g.dispose();

      return image;
   }

   /**
    * Check if drop shadow should be drawn.
    */
   protected boolean isShadow() {
      VSAssemblyInfo info = getAssemblyInfo();

      return info instanceof OutputVSAssemblyInfo &&
         ((OutputVSAssemblyInfo) info).isShadow() ||
         info instanceof ShapeVSAssemblyInfo &&
         ((ShapeVSAssemblyInfo) info).isShadow();
   }

   /**
    * Paint the VSFloatable.
    */
   public final void paint(Graphics2D g) {
      Point pos = getBorderGap();
      int startx = Math.max(pos.x, 0);
      int starty = Math.max(pos.y, 0);

      g.translate(startx, starty);
      drawBackground(g);
      paintComponent(g);
      g.translate(-startx, -starty);
      drawBorders(g);
   }

   /**
    * Paint the content of the image.
    */
   protected abstract void paintComponent(Graphics2D g);

   /**
    * Get content x.
    * @return the content x position.
    */
   @Override
   public int getContentX() {
      return 0;
   }

   /**
    * Get content y.
    * @return the content y position.
    */
   @Override
   public int getContentY() {
      return 0;
   }

   /**
    * Get content width.
    * @return the content width.
    */
   @Override
   public int getContentWidth() {
      return getPixelSize().width;
   }

   /**
    * Get content height.
    * @return the content height.
    */
   @Override
   public int getContentHeight() {
      return getPixelSize().height;
   }

   /**
    * Get object background.
    */
   protected Color getBackground() {
      VSAssemblyInfo vinfo = getAssemblyInfo();

      if(vinfo != null) {
         VSCompositeFormat format = vinfo.getFormat();
         return format != null ? format.getBackground() : null;
      }

      return null;
   }

   /**
    * Draw Background.
    */
   protected void drawBackground(Graphics g) {
      Dimension size = getSize();
      Color bg = getBackground();

      if(bg != null) {
         if(isHighDPI()) {
            size = new Dimension(size.width * 2, size.height * 2);
         }

         Point start = new Point(0, 0);
         VSAssemblyInfo vinfo = getAssemblyInfo();
         VSCompositeFormat format = vinfo.getFormat();

         ExportUtil.drawBackground(g, start, size, bg, format.getRoundCorner());
      }
   }

   /**
    * Get size not including margin.
    */
   protected Dimension getSize() {
      return getPixelSize();
   }

   /**
    * Get original size of the image.
    * @return the content height.
    */
   protected Dimension getImageSize() {
      imageSize = getPixelSize();
      return imageSize;
   }

   /**
    * Get the top & left border gap.
    * @return the top & left border gap.
    */
   protected Point getBorderGap() {
      return new Point((int) getBW(LEFT), (int) getBW(TOP));
   }

   protected Dimension imageSize;
}
