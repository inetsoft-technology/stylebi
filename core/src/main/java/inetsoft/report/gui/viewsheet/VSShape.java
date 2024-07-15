/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.gui.viewsheet;

import inetsoft.uql.viewsheet.GradientColor;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * VSShape component for viewsheet.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public abstract class VSShape extends VSFloatable {
   /**
    * Contructor.
    */
   public VSShape(Viewsheet vs) {
      super(vs);
   }

   @Override
   protected void paintComponent(Graphics2D g) {
      Dimension size = getImageSize();
      BufferedImage image = new BufferedImage(size.width, size.height,
                                              BufferedImage.TYPE_4BYTE_ABGR);
      Graphics2D g2 = (Graphics2D) image.getGraphics();

      drawBackground(g2);
      paintShape(g2);
      g2.dispose();

      if(isShadow()) {
         image = VSFaceUtil.addShadow(image, 6);
      }

      g.drawImage(image, 0, 0, null);
   }

   /**
    * Get original size of the image.
    * @return the content height.
    */
   @Override
   protected Dimension getImageSize() {
      Dimension imageSize = super.getImageSize();
      ShapeVSAssemblyInfo info = getInfo();

      if(info instanceof AnnotationLineVSAssemblyInfo ||
         info instanceof AnnotationRectangleVSAssemblyInfo)
      {
         return imageSize;
      }

      return VSUtil.getScaledSize(imageSize, info.getScalingRatio());
   }

   /**
    * Get assembly info.
    */
   private ShapeVSAssemblyInfo getInfo() {
      return (ShapeVSAssemblyInfo) getAssemblyInfo();
   }

   /**
    * Paint the shape on the graphics.
    */
   protected abstract void paintShape(Graphics2D g);

   /**
    * Get the line style.
    */
   protected int getLineStyle() {
      ShapeVSAssemblyInfo info = (ShapeVSAssemblyInfo) getAssemblyInfo();
      return info.getLineStyle();
   }

   /**
    * Get the background color.
    * @return the background color.
    */
   protected Color getBackground() {
      ShapeVSAssemblyInfo info = (ShapeVSAssemblyInfo) getAssemblyInfo();
      int alpha = info.getFormat().getAlpha();
      Color bg = info.getFormat().getBackground();

      if(bg == null) {
         return null;
      }

      return new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), Math.round(alpha * 255f / 100));
   }

   protected Paint getGradientColor() {
      ShapeVSAssemblyInfo info = (ShapeVSAssemblyInfo) getAssemblyInfo();
      GradientColor gradient = info.getFormat().getGradientColor();

      if(gradient != null && gradient.isApply()) {
         if(gradient.getColors() == null || gradient.getColors().length == 0) {
            return null;
         }

         if(gradient.getColors().length == 1) {
            return gradient.getColorArray()[0];
         }

         gradient.setAlpha(info.getFormat().getAlpha());
         Dimension imageSize = super.getImageSize();
         return gradient.getPaint(imageSize.width, imageSize.height);
      }

      return null;
   }

   /**
    * Get the foreground color.
    * @return the foreground color.
    */
   protected Color getForeground() {
      ShapeVSAssemblyInfo info = (ShapeVSAssemblyInfo) getAssemblyInfo();
      return info.getFormat().getForeground();
   }

   /**
    * Get the alpha.
    * @return the shape color's alpha.
    */
   protected double getAlpha() {
      ShapeVSAssemblyInfo info = (ShapeVSAssemblyInfo) getAssemblyInfo();
      return info.getFormat().getAlpha() / 100.0;
   }

   /**
    * Get the pixelSize.
    * @return the shape's pixel size.
    */
   protected Dimension getShapePixelSize() {
      ShapeVSAssemblyInfo info = getInfo();

      if(info instanceof AnnotationLineVSAssemblyInfo ||
         info instanceof AnnotationRectangleVSAssemblyInfo)
      {
         return info.getPixelSize();
      }

      return VSUtil.getScaledSize(info.getPixelSize(), info.getScalingRatio());
   }

   /**
    * Draw Borders.
    */
   @Override
   protected void drawBorders(Graphics g) {
      // do nothing
   }

   /**
    * Get the top & left border gap.
    * @return the top & left border gap.
    */
   @Override
   protected Point getBorderGap() {
      return new Point(0, 0);
   }
}
