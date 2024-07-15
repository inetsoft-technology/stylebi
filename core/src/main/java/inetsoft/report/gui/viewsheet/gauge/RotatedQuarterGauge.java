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
package inetsoft.report.gui.viewsheet.gauge;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

/**
 * The quarter gauge that is rotated 180 degree.
 *
 * @version 8.5, 2006-6-27
 * @author InetSoft Technology Corp
 */
public class RotatedQuarterGauge extends DefaultQuarterVSGauge {
   /**
    * Caculate the needle's display radian.
    */
   @Override
   protected double calculateNeedleRadian() {
      double radian = super.calculateNeedleRadian();
      return radian - Math.PI;
   }

   /**
    * Draw a string on the panel.
    * @param g the graphics object to draw.
    * @param center the panel's center point.
    * @param radian the string's center point's radian relative to
    * the panel's center point.
    * @param radius the string's center point's radius relative to
    * the panel's center point.
    * @param font the string's font.
    * @param color the string's color.
    * @param content the string itself.
    */
   @Override
   protected void drawString(Graphics2D g, Point2D center, double radian,
      double radius, String content)
   {
      Font font = labelFmt.getFont();
      Color color = labelFmt.getForeground();
      Font originFont = g.getFont();
      Color originColor = g.getColor();

      FontMetrics fm = g.getFontMetrics();
      double stringWidth = fm.stringWidth(content);
      double stringRadin = stringWidth / radius;

      radian = radian + stringRadin / 2;

      for(int i = content.length() - 1; i >= 0; i--) {
         String contentChar = content.substring(i, i + 1);
         double charWidth = fm.stringWidth(contentChar) - 1;
         double charRadian = stringRadin * charWidth / stringWidth;

         radian = i == content.length() - 1 ?
            radian - 2 * charRadian : radian - charRadian;

         Point2D startPoint =
            getStringStartPoint(g, center, radian,
                                radius + fm.getHeight() - fm.getDescent(),
                                contentChar, font);
         AffineTransform transform =
            AffineTransform.getRotateInstance(Math.PI * 1.5 - radian -
                                              charWidth / radius);
         Font drawingFont = font.deriveFont(transform);

         g.setFont(drawingFont);
         g.setColor(color);
         g.drawString(contentChar, (float) startPoint.getX(),
            (float) startPoint.getY());
      }

      g.setFont(originFont);
      g.setColor(originColor);
   }
}
