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

import inetsoft.report.*;
import inetsoft.report.internal.info.SectionBandInfo;

import java.awt.*;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * Handles the printing of a section.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class SectionPaintable extends BasePaintable {
   /**
    * Create a default section paintable
    */
   public SectionPaintable() {
      super();
   }

   /**
    * Create a section paintable.
    */
   public SectionPaintable(float x, float y, float w, SectionBandInfo[] infos,
                           int[] rows, int[] brows, ReportElement elem) {
      super(elem);
      this.x = x;
      this.y = y;
      this.width = w;
      this.infos = infos;
      this.rows = rows;
      this.brows = brows;
       ((BaseElement) elem).getReport();
       this.designtime = false;
   }

   public StylePage getPage() {
      return page;
   }

   public void setPage(StylePage page) {
      this.page = page;
   }

   /**
    * Paint the section.
    */
   @Override
   public void paint(Graphics g) {
      float y2 = y;
      Color color = g.getColor();
      float underlayBottom = 0;
      int underlayLevel = 0;
      int ix = (int) x;
      int iw = (int) (width + x - ix);

      for(int i = 0; i < infos.length; i++) {
         float h = getHeight(i);

         Color bg = null;
         y2 += getAdvance(i);

         if(underlayBottom > y2 &&
            (infos[i].getLevel() < underlayLevel ||
                    (infos[i].getLevel() == underlayLevel &&
                            infos[i].getType().equals(BindingInfo.HEADER))))
         {
            y2 = underlayBottom;
         }

         boolean lastBandOfUnderlay =
            (i == infos.length - 1 && underlayBottom > 0) ||
            (i < infos.length - 1 &&
             underlayBottom > y2 + infos[i + 1].getAdvance() + h &&
             (infos[i + 1].getLevel() < underlayLevel ||
              (infos[i + 1].getLevel() == underlayLevel &&
               infos[i + 1].getType().equals(BindingInfo.HEADER))));

         int iy = (int) y2;
         int ih = (int) (h + y2 - iy);

         float bottomY = lastBandOfUnderlay ? underlayBottom - (float) 0 / 2:
            iy + ih - (float) 0 / 2;

         if((bg = infos[i].getBackground()) != null) {
            g.setColor(bg);
            Common.fillRect(g, ix, iy - (float) 0 / 2, iw - (float) 0,
                            ih + (float) 0 / 2 - (float) 0 / 2);
         }

         if(designtime) {
            y2 += h;
         }
         else {
            underlayBottom = y2 + h;
            underlayLevel = infos[i].getLevel();
         }
      }

      g.setColor(color);
   }

   /**
    * Return the bounds of this paintable area.
    * @return area bounds or null if element does not occupy an area.
    */
   @Override
   public final Rectangle getBounds() {
      // to calculate bounds is a very time consuming task.
      // Here we cache it to achieve a much better performance
      if(bounds == null) {
         bounds = getBounds0();
      }

      return bounds;
   }

   /**
    * Return the bounds of this paintable area.
    * @return area bounds or null if element does not occupy an area.
    */
   private Rectangle getBounds0() {
      float h = 0;

      if(designtime && infos.length == 0) {
         h = 20;
      }

      float minH = 0;
      int underlayLevel = 0;

      for(int i = 0; i < infos.length; i++) {
         if(minH > h && infos[i].getLevel() <= underlayLevel &&
            infos[i].getType().equals(BindingInfo.HEADER))
         {
            h = minH;
         }

         if(designtime) {
            h += infos[i].getHeight() + infos[i].getAdvance();
         }
         else {
            minH = h + infos[i].getHeight() + infos[i].getAdvance();
            underlayLevel = infos[i].getLevel();
         }
      }

      h = Math.max(h, minH);

      return new Rectangle((int) x, (int) y,
                           (int) Math.ceil(width), (int) Math.ceil(h));
   }

   /**
    * Set the location to this paintable.
    */
   @Override
   public void setLocation(Point loc) {
      x = loc.x;
      y = loc.y;

      bounds = null;
   }

   /**
    * Get the location of this paintable.
    */
   @Override
   public final Point getLocation() {
      return new Point((int) x, (int) y);
   }

   /**
    * Set the section width. This is only used internally.
    */
   public void setWidth(float width) {
      this.width = width;
      bounds = null;
   }

   /**
    * Find the section band where the y location is contained. The rectangle
    * is adjusted to relative to the section band upon returning from this
    * function.
    */
   public final SectionBand findSectionBand(Rectangle box) {
      int idx = findSectionBandIndex(box);

      return (idx >= 0) ? infos[idx].band : null;
   }

   /**
    * Find the section band where the y location is contained. The rectangle
    * is adjusted to relative to the section band upon returning from this
    * function.
    */
   public final int findSectionBandIndex(Rectangle box) {
      // use mid point
      float half = box.height / 2;
      float yp = box.y + half - y;

      for(int i = 0; i < infos.length; i++) {
         float h = infos[i].getHeight();

         if(yp <= h) {
            box.y = (int) (yp - half + infos[i].getOffset());
            return i;
         }

         yp -= h;
      }

      return -1;
   }

   /**
    * Get the section band index.
    */
   public final int indexOfSectionBand(SectionBand band) {
      for(int i = 0; i< infos.length; i++) {
         if(infos[i].band == band) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Find the bounds of a band.
    */
   public final Rectangle getSectionBandBounds(SectionBand band) {
      if(band == null) {
         return null;
      }

      float yp = y;

      for(int i = 0; i < infos.length && infos[i].band != band; i++) {
         float h = infos[i].band.getHeight() * 72;
         yp += h + infos[i].getAdvance();
      }

      return new Rectangle((int) x, (int) yp, (int) width,
         (int) (band.getHeight() * 72));
   }

   /**
    * Find the bounds of a band.
    */
   public final Rectangle getSectionBandBounds(int n) {
      float yp = y;
      float underlayBottom = 0;
      int underlayLevel = 0;
      int start = 0;

      if(n > lastN && lastN >= 0) {
         yp = lastYP;
         underlayBottom = lastunderlayBottom;
         underlayLevel = lastunderlayLevel;
         start = lastN + 1;
      }

      for(int i = start; i < infos.length; i++) {
         yp += infos[i].getAdvance();

         if(underlayBottom > yp &&
            (infos[i].getLevel() < underlayLevel ||
             (i > 0 && false) ||
             (infos[i].getLevel() == underlayLevel &&
              infos[i].getType().equals(BindingInfo.HEADER))))
         {
            yp = underlayBottom;
         }

         float height = infos[i].getHeight();

         if(i == n) {
            lastYP = yp;
            lastN = n;
            lastunderlayBottom = underlayBottom;
            lastunderlayLevel = underlayLevel;

            if(designtime) {
               lastYP += height;
            }
            else {
               lastunderlayBottom = lastYP + height;
               lastunderlayLevel = infos[i].getLevel();
            }

            return new Rectangle((int) x, (int) yp, (int) width, (int) height);
         }

         if(designtime) {
            yp += height;
         }
         else {
            underlayBottom = yp + height;
            underlayLevel = infos[i].getLevel();
         }
      }

      throw new RuntimeException("Invalid index found: " + n);
   }

   /**
    * Get the section band height. This may be different from the band's
    * declared height. The height is returned as points.
    */
   public final float getHeight(int i) {
      return infos[i].getHeight();
   }

   /**
    * Get the Y advance amount for this band.
    */
   public final float getAdvance(int i) {
      return infos[i].getAdvance();
   }

   /**
    * Get the table row.
    */
   public final int getRow(int i) {
      return rows == null ? -1 : rows[i];
   }

   public final int getBaseRow(int i) {
      return brows == null ? -1 : brows[i];
   }

   /**
    * Get the band background color.
    */
   public final Color getBackground(int i) {
      return infos[i].getBackground();
   }

   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException
   {
      s.defaultReadObject();
      elem = new BaseElement();
      ((BaseElement) elem).readObjectMin(s);
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
      stream.defaultWriteObject();
      ((BaseElement) elem).writeObjectMin(stream);
   }

   public static final float MIN_MARGIN = 1f; // inch
   private float x, y, width;
    private SectionBandInfo[] infos;
   private transient int[] rows;
   private transient int[] brows;
   private transient boolean designtime = false;
   private transient Rectangle bounds = null;
   private transient StylePage page;

   private float lastYP = -1, lastunderlayBottom = -1;
   private int lastN = -1, lastunderlayLevel = -1;
}
