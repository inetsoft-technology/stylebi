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

import inetsoft.report.*;
import inetsoft.util.Catalog;

import java.awt.*;

/**
 * Conditional page break.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class CondPageBreakElementDef extends BaseElement
   implements inetsoft.report.CondPageBreakElement {
   /**
    * New conditional page break in pixels.
    */
   public CondPageBreakElementDef(ReportSheet report, int pixels) {
      super(report, true);
      this.pixels = pixels;
   }

   /**
    * New conditional page break in inches.
    */
   public CondPageBreakElementDef(ReportSheet report, double inch) {
      super(report, true);
      this.inch = inch;
   }

   /**
    * Return true if this element is a flow control element with
    * no concrete visible contents.
    */
   @Override
   public boolean isFlowControl() {
      return true;
   }

   /**
    * Advance printHead.
    */
   @Override
   public boolean print(StylePage pg, final ReportSheet report) {
      super.print(pg, report);

      if(!checkVisible()) {
         return false;
      }

       if(false) {
         final float h = Common.getHeight(defFont);

         pg.addPaintable(new BasePaintable(this) {
            float x = report.printHead.x + report.printBox.x;
            float y = report.printHead.y + report.printBox.y;
            float w = report.printBox.width;
            Rectangle box = new Rectangle((int) x, (int) y + 4, (int) w,
               (int) h - 8);
            @Override
            public void paint(Graphics g) {
               String label =
                  Catalog.getCatalog().getString("Conditional Page Break");

               g.setColor(Color.gray);
               float sw = Common.stringWidth(label, defFont);
               float x1 = x + (w - sw) / 2;
               float x2 = x1 + sw;

               Common.drawHLine(g, y + h / 2, x, x1, StyleConstants.DASH_LINE,
                  StyleConstants.NO_BORDER, StyleConstants.NO_BORDER);
               g.setFont(defFont);
               Common.drawString(g, label, x1, y + Common.getAscent(defFont));
               Common.drawHLine(g, y + h / 2, x2, x + w,
                  StyleConstants.DASH_LINE, StyleConstants.NO_BORDER,
                  StyleConstants.NO_BORDER);
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
               x = loc.x;
               y = loc.y;
            }

            @Override
            public Point getLocation() {
               return new Point((int) x, (int) y + 4);
            }
         });
      }

      return false;
   }

   /**
    * Get the minimum space requirement of this conditional page break.
    */
   public int getMinimumHeight() {
      return (pixels > 0) ? pixels : (int) (inch * 72);
   }

   /**
    * Get the conditional height.
    */
   @Override
   public double getCondHeight() {
      return (pixels > 0) ? (pixels / (double) 72) : inch;
   }

   /**
    * Set the conditional height.
    */
   @Override
   public void setCondHeight(double inch) {
      this.inch = inch;
      pixels = 0;
   }

   public String toString() {
      return getID();
   }

   @Override
   public String getType() {
      return "CondPageBreak";
   }

   private int pixels = 0;
   private double inch = 0;
   static final Font defFont = Util.DEFAULT_FONT;
}

