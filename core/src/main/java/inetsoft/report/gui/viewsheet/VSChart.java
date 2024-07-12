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

import inetsoft.uql.viewsheet.Viewsheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * VSChart component for viewsheet.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class VSChart extends VSImageable {
   /**
    * Constructor.
    */
   public VSChart(Viewsheet vs) {
      super(vs);
   }

   protected boolean isHighDPI() {
      return true;
   }

   /**
    * Get the content image.
    * @return image.
    */
   @Override
   public BufferedImage getContentImage() {
      try {
         return getBufferedImage(rimage);
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }

      return new BufferedImage(0, 0, BufferedImage.TYPE_INT_ARGB);
   }

   /**
    * Get the raw image.
    * @return the specified raw image.
    */
   public Image getRawImage() {
      return this.rimage;
   }

   /**
    * Set the raw image.
    * @param image the specified raw image.
    */
   public void setRawImage(Image image) {
      this.rimage = image;
   }

   /**
    * Convert to buffered image.
    * @param the image.
    */
   private BufferedImage getBufferedImage(Image image) {
      if(image instanceof BufferedImage || image == null) {
         return (BufferedImage) image;
      }

      // ensures that all the pixels in the image are loaded
      image = new ImageIcon(image).getImage();

      // get the object size to scale the image
      java.awt.Dimension imageSize = getSize();
      int imageWidth = (int) imageSize.getWidth();
      int imageHeight = (int) imageSize.getHeight();

      BufferedImage bimage = null;

      try {
         GraphicsEnvironment ge =
            GraphicsEnvironment.getLocalGraphicsEnvironment();

         // Create the buffered image
         GraphicsDevice gs = ge.getDefaultScreenDevice();
         GraphicsConfiguration gc = gs.getDefaultConfiguration();
         bimage = gc.createCompatibleImage(imageWidth, imageHeight,
                                           Transparency.BITMASK);
      }
      catch(Exception ex) {
         // ignore
      }

      if(bimage == null) {
         bimage = new BufferedImage(imageWidth, imageHeight,
                                    BufferedImage.TYPE_INT_ARGB);
      }

      image = image.getScaledInstance(imageWidth, imageHeight,
                                      Image.SCALE_FAST);

      // ensures that all the pixels in the image are loaded
      image = new ImageIcon(image).getImage();

      Graphics g = bimage.createGraphics();
      g.drawImage(image, 0, 0, null);
      g.dispose();

      return bimage;
   }

   private Image rimage;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSChart.class);
}
