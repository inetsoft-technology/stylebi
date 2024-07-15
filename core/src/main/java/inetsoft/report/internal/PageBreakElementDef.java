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
 * Page break, no-op.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class PageBreakElementDef extends BaseElement
   implements inetsoft.report.PageBreakElement
{
   /**
    * Create an page break.
    */
   public PageBreakElementDef(ReportSheet report) {
      super(report, true);
   }

   /**
    * Advance printHead.
    */
   @Override
   public boolean print(StylePage pg, final ReportSheet report) {
      pg.addPaintable(new PageBreakPaintable(this, report));

      return super.print(pg, report);
   }

   public String toString() {
      return getID();
   }

   @Override
   public String getType() {
      return "PageBreak";
   }

   @Override
   public Size getPreferredSize() {
      return new Size(60, 15);
   }

   /**
    * Get the sheet name.
    */
   @Override
   public String getSheetName() {
      return sheetName;
   }

   /**
    * Set the sheet name.
    */
   @Override
   public void setSheetName(String sheetName) {
      this.sheetName = sheetName;
   }

   @Override
   public String getCreatePageOption() {
      return createPageOption;
   }

   @Override
   public void setCreatePageOption(String createPageOption) {
      this.createPageOption = createPageOption;
   }

   public static final class PageBreakPaintable extends BasePaintable {
      Rectangle box;

      public PageBreakPaintable(PageBreakElement element, ReportSheet report) {
         super(element);
         box = new Rectangle((int) (report.printHead.x + report.printBox.x),
            (int) (report.printHead.y + report.printBox.y),
            report.printBox.width, 20);
      }

      @Override
      public void paint(Graphics g) {
      }

      @Override
      public Rectangle getBounds() {
         return box;
      }

      @Override
      public void setLocation(Point loc) {
         box.x = loc.x;
         box.y = loc.y;
      }

      @Override
      public Point getLocation() {
         return new Point(box.x, box.y);
      }
   }

   private boolean sheetbreak = false;
   private String sheetName = "";
   private String createPageOption = NO_CREATE;
   public static final String NO_CREATE = "no";
   public static final String ALWAYS_CREATE = "always";
   public static final String ODD_CREATE = "odd";
   public static final String EVEN_CREATE = "even";
}
