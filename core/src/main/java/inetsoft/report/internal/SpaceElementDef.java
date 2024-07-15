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

import inetsoft.report.*;

import java.awt.*;

/**
 * Space element.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class SpaceElementDef extends BaseElement
   implements inetsoft.report.SpaceElement {
   /**
    * Space in pixels.
    */
   public SpaceElementDef(ReportSheet report, int pixels) {
      super(report, false);
      this.pixels = pixels;
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
    * Return the size that is needed for this element.
    */
   @Override
   public Size getPreferredSize() {
      return new Size(pixels, Common.getHeight(getFont()));
   }

   /**
    * Print the element at the printHead location.
    * @return true if the element is not completed printed and
    * need to be called again.
    */
   @Override
   public boolean print(StylePage pg, final ReportSheet report) {
      super.print(pg, report);

      if(!checkVisible()) {
         return false;
      }

      final int h = (pixels == 0) ? 0 : (int) Common.getHeight(getFont()) + getSpacing();
      Rectangle box = new Rectangle((int) (report.printHead.x +
         report.printBox.x),
         (int) (report.printHead.y + report.printBox.y),
         pixels,
         h);

       pg.addPaintable(new SpacePaintable(this, box, false));
      report.advance(pixels, h);
      return false;
   }

   /**
    * Get the space in number of pixels.
    * @return number of pixels.
    */
   @Override
   public int getSpace() {
      return pixels;
   }

   /**
    * Set the space in number of pixels.
    * @param pixels space in pixels.
    */
   @Override
   public void setSpace(int pixels) {
      this.pixels = pixels;
   }

   public String toString() {
      return getID();
   }

   @Override
   public String getType() {
      return "Space";
   }

   private int pixels;
}

