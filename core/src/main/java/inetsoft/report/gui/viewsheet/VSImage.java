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

import inetsoft.graph.internal.GTool;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * VSImagable component for viewsheet.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class VSImage extends VSImageable {
   /**
    * Constructor.
    */
   public VSImage(Viewsheet vs) {
      super(vs);
   }

   /**
    * Draw Background.
    */
   @Override
   protected void drawBackground(Graphics g) {
      // do nothing
   }

   /**
    * Get format Background.
    */
   private Color getBackground(VSCompositeFormat format) {
      Color color = format.getBackground();

      if(isDataTipView() && format.getAlpha() >= 80) {
         int alpha = Math.round(80 * 255f / 100);
         return color == null ? new Color(255, 255, 255, alpha) :
            new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
      }
      else {
         return format.getBackground();
      }
   }

   /**
    * Get the content image.
    * @return image.
    */
   @Override
   public BufferedImage getContentImage() {
      VSAssemblyInfo info = getAssemblyInfo();
      Image img0 = rimage;
      Color highlightC = ((ImageVSAssemblyInfo) info).getHighlightForeground();
      Color bg = ((ImageVSAssemblyInfo) info).getHighlightBackground();
      VSCompositeFormat fmt = info.getFormat();
      highlightC = highlightC == null ? fmt.getForeground() : highlightC;
      highlightC = Color.BLACK.equals(highlightC) ? null : highlightC;

      if(highlightC != null) {
         img0 = GTool.changeHue(img0, highlightC);
      }

      if(bg == null) {
         bg = getBackground(fmt);
      }

      try {
         return getBufferedImage(img0, bg);
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
    * Get original size of the image.
    * @return the content height.
    */
   @Override
   protected Dimension getImageSize() {
      Dimension imageSize = super.getImageSize();
      return VSUtil.getScaledSize(imageSize,
         ((ImageVSAssemblyInfo) getAssemblyInfo()).getScalingRatio());
   }

   /**
    * Convert to buffered image.
    * @param image the image.
    * @param bg the back ground color.
    */
   private BufferedImage getBufferedImage(Image image, Color bg) {
      ImageVSAssemblyInfo info = (ImageVSAssemblyInfo) getAssemblyInfo();
      Dimension nsize = VSUtil.getScaledSize(getSize(), info.getScalingRatio());

      if(info.isTile()) {
         image = getTiledImage(image, nsize);
      }

      return ImageManager.getBufferedImage(image, bg,
         info.getFormat().getAlignment(), info.isScaleImage(),
         info.isMaintainAspectRatio(), info.getScale9(), nsize, true);
   }

   /**
    * Get tiled image.
    */
   private BufferedImage getTiledImage(Image image, Dimension size) {
      BufferedImage image2 = new BufferedImage(size.width, size.height,
                                               BufferedImage.TYPE_INT_ARGB);
      Graphics g = image2.getGraphics();
      int imgw = image.getWidth(null);
      int imgh = image.getHeight(null);

      for(int y = 0; y < size.height; y += imgh) {
         for(int x = 0; x < size.width; x += imgw) {
            g.drawImage(image, x, y, null);
         }
      }

      g.dispose();
      return image2;
   }

   private Image rimage;
   private static final Logger LOG =
      LoggerFactory.getLogger(VSImage.class);
}
