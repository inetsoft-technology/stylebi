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
package inetsoft.report.internal;

import inetsoft.report.ReportElement;
import inetsoft.report.StyleConstants;

import java.awt.*;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * Paintable for tab element.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TabPaintable extends BasePaintable {
   /**
    * Create a default tab paintable
    */
   public TabPaintable() {
      this(0.0F, 0.0F, 0.0F, 0.0F, 0, null);
   }

   public TabPaintable(float x, float y, float w, float h, int fill,
      ReportElement elem) {
      super(elem);
      this.w = w;
      this.h = h;
      this.x = x;
      this.y = y;
      this.fill = fill;
   }

   public Image getTabMarker() {
      return tabMarker;
   }

   public void setTabMarker(Image tabMarker) {
      this.tabMarker = tabMarker;
   }

   @Override
   public void paint(Graphics g) {
      g.setColor(elem.getForeground());

      if(fill != StyleConstants.NO_BORDER) {
         float ly = y + Common.getHeight(elem.getFont()) - Common.getLineWidth(fill) - 1;
         Common.drawHLine(g, ly, x, x + w, fill, StyleConstants.NO_BORDER,
            StyleConstants.NO_BORDER);
      }
      else if(tabMarker != null) {
         int iw = tabMarker.getWidth(null);
         int ih = tabMarker.getHeight(null);

         g.drawImage(tabMarker, (int) (x + (w - iw) / 2),
            (int) (y + (h - ih) / 2), null);
      }
   }

   /**
    * Return the bounds of this paintable area.
    * @return area bounds or null if element does not occupy an
    * area.
    */
   @Override
   public Rectangle getBounds() {
      return new Rectangle((int) x, (int) y, (int) w, (int) h);
   }

   @Override
   public void setLocation(Point loc) {
      x = loc.x;
      y = loc.y;
   }

   /**
    * Get the location of this paintable.
    */
   @Override
   public Point getLocation() {
      return new Point((int) x, (int) y);
   }

   /**
    * Get the filling style.
    */
   public int getFill() {
      return fill;
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

   float x, y, w, h;
   int fill;
   transient Image tabMarker;
}
