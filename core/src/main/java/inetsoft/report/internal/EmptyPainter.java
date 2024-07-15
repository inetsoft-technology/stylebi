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
package inetsoft.report.internal;

import inetsoft.report.Painter;
import inetsoft.util.Catalog;

import java.awt.*;

/**
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class EmptyPainter implements Painter {
   public EmptyPainter() {
      this(Catalog.getCatalog().getString("Painter"), 80, 80);
   }

   public EmptyPainter(String title, int w, int h) {
      psize = new Dimension(w, h);
      this.title = title;
   }
   
   /**
    * Paint the painter.
    */
   @Override
   public void paint(Graphics g, int x, int y, int w, int h) {
      if(title != null) {
         Color c = g.getColor();
         Font fn = g.getFont();

         g.setColor(new Color(150, 150, 150));
         g.fillRect(x, y, w, h);
         g.setFont(new Font("Serif", Font.PLAIN, 10));
         g.setColor(Color.white);
         g.drawString(title, x + 2, y + 12);
         g.setColor(c);
         g.setFont(fn);
      }
   }

   /**
    * Get preferred size.
    */
   @Override
   public Dimension getPreferredSize() {
      return psize;
   }

   /**
    * Check if is scalable.
    */
   @Override
   public boolean isScalable() {
      return true;
   }

   Dimension psize;
   String title = null;
}
