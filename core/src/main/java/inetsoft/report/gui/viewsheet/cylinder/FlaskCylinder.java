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
package inetsoft.report.gui.viewsheet.cylinder;

import inetsoft.uql.viewsheet.internal.CylinderVSAssemblyInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * VSFlaskCylinder.
 *
 * @version 8.5, 2006-6-29
 * @author InetSoft Technology Corp
 */
public class FlaskCylinder extends VSCylinder implements Cloneable {
   /**
    * Draw the cylinder.
    */
   @Override
   protected void drawCylinder(Graphics2D g) {
      drawCanvas(g);

      CylinderVSAssemblyInfo info = getCylinderAssemblyInfo();
      double value = getValue();

      if(value > info.getMax()) {
         value = info.getMax();
      }
      else if(value < info.getMin()) {
         value = info.getMin();
      }

      double rate = (value - info.getMin()) / (info.getMax() - info.getMin());
      double maskDrawingHeight = rate * cylinderHeight;
      double sx = scaleX;
      double sy = (maskDrawingHeight > bottomMaskHeight ?
         (maskDrawingHeight - bottomMaskHeight) : 0) / maskHeight;
      AffineTransform scaleT = AffineTransform.getScaleInstance(sx, sy);
      BufferedImage mask = null;
      BufferedImage bottomMask = null;
      BufferedImage ellipse = null;

      try {
         if(scaleT.getScaleY() != 0) {
            mask = getImageByURI(valueMaskPath, scaleT);
         }
         bottomMask = getImageByURI(valueBottomMask, getScale());
         ellipse = getImageByURI(valueEllipsePath, getScale());
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
         return;
      }

      int maskY = (int) Math.ceil(cylinderHeight -
         maskDrawingHeight + ellipseHeight / 2);

      // draw the mask
      if(mask != null) {
         g.drawImage(mask, 0, maskY, null);
      }

      int cutHeight = (int) (bottomMaskHeight - maskDrawingHeight
         - ellipseHeight / 2);
      int bottomMaskY = 0;

      if(maskDrawingHeight != 0) {
         // draw the bottom mask
         if(bottomMask != null) {
            if(cutHeight > 0) {
               bottomMask = bottomMask.getSubimage(0, cutHeight,
                  bottomMask.getWidth(), bottomMask.getHeight() - cutHeight);
            }

            int bottomMaskX = 0;
            bottomMaskY = (int) (cylinderHeight - bottomMaskHeight +
                  (ellipseHeight / 2) + (cutHeight > 0 ? cutHeight : 0));
            g.drawImage(bottomMask, bottomMaskX, bottomMaskY, null);
         }

         // draw the ellipse
         Image ellipse2 = ellipse;
         int ellipseY;

         if(cutHeight > 0) {
            double bottomMaskWidth = cylinderWidth;
            double ellipseWidth = cylinderWidth;

            double newEllipseWidth = Math.sqrt(bottomMaskHeight
               * bottomMaskHeight - cutHeight * cutHeight) * bottomMaskWidth
                  / bottomMaskHeight;
            double scaleE = newEllipseWidth / ellipseWidth;

            // if the ellipseWidth less than zero, draw it is useless.
            if(Double.isNaN(ellipseWidth) || ellipseWidth <= 0) {
               return;
            }

            ellipse2 = ellipse.getScaledInstance((int)
               Math.ceil(scaleE * ellipse.getWidth()),
                  (int) Math.ceil(scaleE * ellipse.getHeight()),
                     Image.SCALE_FAST);
            // the bottomMask has a transparent 1 pixel border
            ellipseY = (int) (bottomMaskY - ellipseHeight / 2) + 1;

            g.drawImage(ellipse2,
               (int) ((bottomMaskWidth - newEllipseWidth) / 2), ellipseY, null);
         }
         else {
            ellipseY = (int) (maskY - ellipseHeight / 2) + 1;
            g.drawImage(ellipse2, 0, ellipseY, null);
         }
      }

      // draw the panel
      drawPanel(g);
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   protected void parseXML(Element node) throws Exception {
      super.parseXML(node);

      Element imageNode = Tool.getChildNodeByTagName(node, "image");
      Element bottomMaskNode =
         Tool.getChildNodeByTagName(imageNode, "bottomMask");
      valueBottomMask = Tool.getValue(bottomMaskNode);
      bottomMaskHeight =
         Double.parseDouble(Tool.getAttribute(bottomMaskNode, "maskHeight"));
   }

   /**
    * Set the scale of the image.
    */
   @Override
   protected void setScale(double scaleX, double scaleY) {
      super.setScale(scaleX, scaleY);
      bottomMaskHeight = bottomMaskHeight * scaleY;
   }

   protected double bottomMaskHeight;
   protected String valueBottomMask;

   private static final Logger LOG =
      LoggerFactory.getLogger(FlaskCylinder.class);
}
