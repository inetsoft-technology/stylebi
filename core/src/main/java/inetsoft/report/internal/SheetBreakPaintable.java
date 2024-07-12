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
import inetsoft.report.ReportSheet;

import java.awt.*;

/**
 * Class to paint a actual table region.
 *
 * @version 10.2, 7/17/2009
 * @author InetSoft Technology Corp
 */
public class SheetBreakPaintable extends BasePaintable {
   /**
    * Create a paintable, set width and height to 0 to make it invisible.
    */
   public SheetBreakPaintable(final ReportSheet report, ReportElement elem) {
      super(elem);
      x = report.printHead.x + report.printBox.x;
      y = report.printHead.y + report.printBox.y;
      w = 0;
      h = 0;
      box = new Rectangle((int) x, (int) y, (int) w, (int) h);
   }

   /**
    * Create a paintable, set width and height to 0 to make it invisible.
    */
   public SheetBreakPaintable(final ReportSheet report, ReportElement elem,
      String sheetName)
  {
      super(elem);

      this.sheetName = sheetName;
      x = report.printHead.x + report.printBox.x;
      y = report.printHead.y + report.printBox.y;
      w = 0;
      h = 0;
      box = new Rectangle((int) x, (int) y, (int) w, (int) h);
   }

   /**
    * Paint nothing, so it will not been seen.
    */
   @Override
   public void paint(Graphics g) {
   }

   /**
    * Return the bounds of this paintable.
    * @return area bounds.
    */
   @Override
   public Rectangle getBounds() {
      return box;
   }

   /**
    * Get the sheet name.
    */
   public String getSheetName() {
      return sheetName;
   }

   /**
    * Set the location of this paintable area.
    * This is used internally for small adjustments during printing.
    * @param loc new location for this paintable.
    */
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

   private float x;
   private float y;
   private float w;
   private float h;
   private Rectangle box;
   private String sheetName;
}
