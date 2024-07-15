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
package inetsoft.report.gui.viewsheet;

import inetsoft.report.StyleConstants;
import inetsoft.report.internal.MetaImage;
import inetsoft.util.Tool;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

/**
 * ImageManager for view sheet.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ImageManager {

   /**
    * Overloading getBufferedImage method.
    */
   public static BufferedImage getBufferedImage(Image image, Color bg,
      int align, boolean isScaleImage, boolean isMaintainAspectRatio,
      Insets scale9, Dimension imageSize, boolean translucent)
   {
      if(image == null) {
         return (BufferedImage) image;
      }

      // ensures that all the pixels in the image are loaded
      Tool.waitForImage(image);

      // get the object size to scale the image
      int imageWidth = (int) imageSize.getWidth();
      int imageHeight = (int) imageSize.getHeight();

      BufferedImage bimage = null;

      try {
         GraphicsEnvironment ge =
            GraphicsEnvironment.getLocalGraphicsEnvironment();

         // Create the buffered image
         GraphicsDevice gs = ge.getDefaultScreenDevice();
         GraphicsConfiguration gc = gs.getDefaultConfiguration();

         int trans = -1;

         if(translucent) {
            trans = Transparency.TRANSLUCENT;
         }
         else {
            trans = Transparency.BITMASK;
         }

         bimage = gc.createCompatibleImage(imageWidth, imageHeight, trans);
      }
      catch(Exception ex) {
         // ignore
      }

      if(bimage == null) {
         bimage = new BufferedImage(imageWidth, imageHeight,
                                    BufferedImage.TYPE_INT_ARGB);
      }

      if(isScaleImage) {
         Canvas canvas = new Canvas();
         int ow = image.getWidth(canvas);
         int oh = image.getHeight(canvas);

         if(isMaintainAspectRatio) {
            double xratio = (double) imageWidth / ow;
            double yratio = (double) imageHeight / oh;
            double ratio = xratio < yratio ? xratio : yratio;
            image = image.getScaledInstance((int) (ow * ratio),
               (int) (oh * ratio), Image.SCALE_FAST);
         }
         else {
            image = getScale9(image, ow, oh, imageWidth, imageHeight,
                              scale9, canvas);
         }
      }
      else {
         image = getClippedInstance(image, imageWidth, imageHeight, align);
      }

      // ensures that all the pixels in the image are loaded
      Tool.waitForImage(image);

      Graphics g = bimage.createGraphics();

      if(bg != null) {
         g.setColor(bg);
         g.fillRect(0, 0, imageWidth, imageHeight);
      }

      g.drawImage(image, 0, 0, null);
      g.dispose();

      return bimage;
   }
   /**
    * Get the scale9 image.
    * @param img the image get scale9 from.
    * @param ow the width of the image.
    * @param oh the height of the image.
    * @param w the object width.
    * @param h the object height.
    * @param scale9 the scale9 of the image.
    * @param observer the image observer.
    * @return the scale9 image.
    */
   private static Image getScale9(Image img, int ow, int oh, int w, int h,
                                  Insets scale9, ImageObserver observer)
   {
      if(scale9 == null || scale9.left < 0 || scale9.right < 0 ||
         scale9.top < 0 || scale9.bottom < 0 ||
         scale9.left + scale9.right > ow || scale9.top + scale9.bottom > oh)
      {
         return img.getScaledInstance(w, h, Image.SCALE_FAST);
      }
      else {
         Image buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
         Graphics g = buf.getGraphics();
         int[] r = {0, scale9.top, oh - scale9.bottom, oh};
         int[] c = {0, scale9.left, ow - scale9.right, ow};

         int[] dr = {0, scale9.top, h - scale9.bottom, h};
         int[] dc = {0, scale9.left, w - scale9.right, w};

         for(int i = 0; i < 3; i++) {
            for(int j = 0; j < 3; j++) {
               Rectangle orec =
                  new Rectangle(c[i], r[j], c[i + 1] - c[i], r[j + 1] - r[j]);
               Rectangle drec =
                  new Rectangle(dc[i], dr[j], dc[i + 1] - dc[i],
                                dr[j + 1] - dr[j]);
               g.drawImage(img, drec.x,drec.y ,drec.x + drec.width,
                           drec.y + drec.height, orec.x, orec.y,
                           orec.width + orec.x, orec.y + orec.height, observer);
            }
         }

         g.dispose();

         return buf;
      }
   }

   /**
    * Clip an image to the specified size.
    * @param img the image.
    * @param w the object width.
    * @param h the object height.
    * @param align the alignment of the image.
    * @return the buffered image.
    */
   private static Image getClippedInstance(Image img, int w, int h, int align) {
      Image buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics g = buf.getGraphics();
      int ix = 0, iy = 0;

      if(img instanceof MetaImage) {
         img = ((MetaImage) img).getImage();

         if(img == null) {
            return buf;
         }
      }

      Tool.waitForImage(img);

      // alignment
      if((align & StyleConstants.V_CENTER) != 0) {
         iy = (h - img.getHeight(null)) / 2;
      }
      else if((align & StyleConstants.V_BOTTOM) != 0) {
         iy = h - img.getHeight(null);
      }

      if((align & StyleConstants.H_CENTER) != 0) {
         ix = (w - img.getWidth(null)) / 2;
      }
      else if((align & StyleConstants.H_RIGHT) != 0) {
         ix = w - img.getWidth(null);
      }

      g.drawImage(img, Math.max(0, ix), Math.max(0, iy), null);
      g.dispose();

      return buf;
   }
}
