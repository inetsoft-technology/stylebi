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
package inetsoft.report.internal;

import inetsoft.report.ReportElement;

import java.awt.*;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * Paintable for space element.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class SpacePaintable extends BasePaintable {
   public SpacePaintable(ReportElement elem, Rectangle box, boolean draw) {
      super(elem);
      this.box = box;
      this.draw = draw;
   }

   @Override
   public void paint(Graphics g) {
      if(draw) {
         g.setColor(Color.gray);
         Rectangle clip = g.getClipBounds();
         Rectangle box2 = (clip == null) ? box : box.intersection(clip);
         int y = box2.y + box2.height / 2;

         for(int x = box2.x + 2; x < box2.x + box2.width; x += 4) {
            Common.drawLine(g, x, y, x, y + 1);
         }
      }
   }

   @Override
   public Rectangle getBounds() {
      return box;
   }

   /**
    * Set the location of this paintable area.
    * This is used internally
    * for small adjustments during printing.
    * @param loc new location for this paintable.
    */
   @Override
   public void setLocation(Point loc) {
      box.x = loc.x;
      box.y = loc.y;
   }

   /**
    * Get the location of this paintable.
    */
   @Override
   public Point getLocation() {
      return new Point(box.x, box.y);
   }

   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException {
      s.defaultReadObject();
      elem = new BaseElement();
      ((BaseElement) elem).readObjectMin(s);
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
      stream.defaultWriteObject();
      ((BaseElement) elem).writeObjectMin(stream);
   }

   Rectangle box;
   transient boolean draw = false;
}
