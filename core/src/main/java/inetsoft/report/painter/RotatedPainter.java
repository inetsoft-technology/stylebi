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
package inetsoft.report.painter;

import inetsoft.report.Painter;
import inetsoft.report.internal.Common;

import java.awt.*;

/**
 * This painter rotated a painter output 90 degrees clockwise.
 * 
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class RotatedPainter implements Painter {
   /**
    * Create a rotated painter from a component.
    * @param comp component to paint.
    */
   public RotatedPainter(Component comp) {
      this.painter = new ComponentPainter(comp, ComponentPainter.UPDATE);
   }

   /**
    * Create a rotated image.
    * @param image original image.
    */
   public RotatedPainter(Image image) {
      this(new ImagePainter(image));
   }

   /**
    * Create a rotated painter from another painter.
    * @param painter area to be rotated and painted.
    */
   public RotatedPainter(Painter painter) {
      this.painter = painter;
   }

   /**
    * Create a rotated painter from another painter.
    * @param painter area to be rotated and painted.
    * @param rotation degrees to rotate, 90 or 270.
    */
   public RotatedPainter(Painter painter, int rotation) {
      this.painter = painter;
      this.rotation = rotation;
   }

   /**
    * Return the preferred size of this painter.
    * @return size.
    */
   @Override
   public Dimension getPreferredSize() {
      Dimension d = painter.getPreferredSize();

      return new Dimension(d.height, d.width);
   }

   /**
    * Paint contents at the specified location.
    * @param g graphical context.
    * @param x x coordinate of the left edge of the paint area.
    * @param y y coordinate of the upper edge of the paint area.
    * @param w area width.
    * @param h area height.
    */
   @Override
   public void paint(Graphics g, int x, int y, int w, int h) {
      Common.paintRotate(painter, g, x, y, w, h, rotation);
   }

   /**
    * Same as Painter scalable.
    */
   @Override
   public boolean isScalable() {
      return painter.isScalable();
   }

   private Painter painter;
   private int rotation = 90;
}

