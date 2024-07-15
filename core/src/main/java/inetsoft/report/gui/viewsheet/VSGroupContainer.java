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
import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.GroupContainerVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * VSGroupContainer for view sheet exporting.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class VSGroupContainer extends VSImageable {
   /**
    * Constructor.
    */
   public VSGroupContainer(Viewsheet vs) {
      super(vs);
   }

   /**
    * Draw Background.
    */
   @Override
   protected void drawBackground(Graphics g) {
      if(getRawImage() != null) {
         return;
      }

      Dimension size = getSize();
      VSAssemblyInfo vinfo = getAssemblyInfo();

      if(vinfo != null) {
         VSCompositeFormat format = vinfo.getFormat();

         if(format == null || format.getBackground() == null) {
            return;
         }

         Point start = new Point(0, 0);
         ExportUtil.drawBackground(g, start, size, getBackground(format), format.getRoundCorner());
      }
   }

   /**
    * Get the margin of component.
    */
   public Insets getMargin() {
      return new Insets(0, 0, 0, 0);
   }

   /**
    * Get format background.
    * @param format the group format.
    */
   private Color getBackground(VSCompositeFormat format) {
      Color color = format.getBackground();

      if(isDataTipView() && format.getAlpha() >= 80 && color != null) {
         return new Color(color.getRed(), color.getGreen(), color.getBlue(),
            Math.round(80 * 255f / 100));
      }
      else if(isDataTipView() && color == null) {
         return new Color(238, 238, 238, Math.round(80 * 255f / 100));
      }
      else {
         return format.getBackground();
      }
   }

   /**
    * Get the background image.
    * @return image.
    */
   @Override
   public BufferedImage getContentImage() {
      VSAssemblyInfo info = getAssemblyInfo();
      Image img0 = rimage;
      VSCompositeFormat fmt = info.getFormat();
      Color highlightC = Color.BLACK.equals(fmt.getForeground()) ?
         null : fmt.getForeground();

      if(highlightC != null && img0 != null) {
         img0 = GTool.changeHue(img0, highlightC);
      }

      try {
         return getBufferedImage(img0, getBackground(fmt));
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
    * @param image the image.
    * @param bg the back ground color.
    */
   private BufferedImage getBufferedImage(Image image, Color bg) {
      GroupContainerVSAssemblyInfo info =
         (GroupContainerVSAssemblyInfo) getAssemblyInfo();

      return ImageManager.getBufferedImage(image, bg,
         info.getFormat().getAlignment(), info.isScaleImage(),
         info.isMaintainAspectRatio(), info.getScale9(), getSize(), true);
   }

   private Image rimage;
   private static final Logger LOG =
      LoggerFactory.getLogger(VSGroupContainer.class);
}
