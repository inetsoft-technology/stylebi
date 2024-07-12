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
import inetsoft.report.internal.info.ElementInfo;
import inetsoft.report.internal.info.NewlineElementInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * Newline element.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class NewlineElementDef extends BaseElement
   implements inetsoft.report.NewlineElement {
   /**
    * @param conti true if the next line is a continuation of the current
    * line. In this case, the hanging indent is not reset.
    */
   public NewlineElementDef(ReportSheet report, int cnt, boolean conti) {
      super(report, !conti);

      setCount(cnt);
      setContinuation(conti);
   }

   /**
    * Create a proper element info to save the attribute of this element.
    */
   @Override
   protected ElementInfo createElementInfo() {
      return new NewlineElementInfo();
   }

   /**
    * Get the number of linefeeds.
    * @return number of linefeeds.
    */
   @Override
   public int getCount() {
      return ((NewlineElementInfo)einfo).getCount();
   }

   /**
    * Set the number of line feeds.
    * @param count number of line feeds.
    */
   @Override
   public void setCount(int count) {
      ((NewlineElementInfo)einfo).setCount(count);
   }

   /**
    * Check if this is a regular newline or a break.
    */
   @Override
   public boolean isBreak() {
      return !isBlock();
   }

   /**
    * Set this to be break or newline.
    */
   @Override
   public void setBreak(boolean linefeed) {
      setBlock(!linefeed);
   }

   /**
    * Set whether neglectable.
    */
   public void setNeglectable(boolean neg) {
      this.neg = neg;
   }

   /**
    * Check if neglectable.
    */
   public boolean isNeglectable() {
      return neg;
   }

   /**
    * Skip one newline.
    */
   public void skip() {
      skipped++;
   }

   /**
    * Get the remaining newlines (after skipping).
    */
   public int getRemain() {
      return getCount() - skipped;
   }

   /**
    * Reset the printing so the any remaining portion of the painter
    * is ignored, and the next call to print start fresh.
    */
   @Override
   public void resetPrint() {
      super.resetPrint();
      skipped = 0;
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
    * Return true if this element is the last one on a line.
    */
   @Override
   public boolean isLastOnLine() {
      return true;
   }

   /**
    * The preferred height of a newline is the height of font plus
    * the line spacing.
    */
   @Override
   public Size getPreferredSize() {
      return new Size(6, Common.getHeight(getFont()) + getSpacing());
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

      if(newlineMarker == null) {
         try {
            newlineMarker = Tool.getImage(this,
               "/inetsoft/report/images/paramarker.gif");
            Tool.waitForImage(newlineMarker);
            markerW = newlineMarker.getWidth(null);
            markerH = newlineMarker.getHeight(null);
         }
         catch(Exception e) {
            LOG.error("Failed to load new line marker image", e);
         }
      }

      // first element of this line and neglectable ? neglect it
      if(report.printHead.x == 0 && neg) {
         return false;
      }

      final float h = Common.getHeight(getFont()) + getSpacing();
      final int w = (newlineMarker != null) ? newlineMarker.getWidth(null) : 1;
      float adv = 0; // advanced height
      float oX = report.printHead.x;
      int i = 0;

      do {
         pg.addPaintable(new NewlinePaintable(this, report, w, h));

         if(i < getCount() - skipped) {
            report.advance(0, h);
            adv += h;

            if(adv < report.lineH) {
               report.printHead.x = oX;
            }
         }

         i++;
      }
      while(i < getCount() - skipped);

      return false;
   }

   public String toString() {
      return getID();
   }

   @Override
   public String getType() {
      return "Newline";
   }

   // can not be static, why?
   private static Image newlineMarker = null;
   private static int markerW = 0;
   private static int markerH = 0;

   private int skipped = 0;
   private boolean neg = false;

   private static final Logger LOG = LoggerFactory.getLogger(NewlineElementDef.class);

   public static final class NewlinePaintable extends BasePaintable {
      private final boolean designTime;
      private final float h;
      Rectangle box;

      public NewlinePaintable(NewlineElement element, ReportSheet report, int w, float h) {
         super(element);
          this.designTime = false;
         this.h = h;
         box = new Rectangle((int) (report.printHead.x + report.printBox.x),
                             (int) (report.printHead.y + report.printBox.y),
                             w, Common.round(h));
      }

      @Override
      public void paint(Graphics g) {
         if(designTime && newlineMarker != null) {
            int ih = Math.max(Math.min(markerH, (int) h), 4);

            g.drawImage(newlineMarker, box.x, box.y, markerW, ih, null);
         }
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

      public Image getNewlineMarker() {
         return newlineMarker;
      }
   }
}
