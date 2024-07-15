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
package inetsoft.report;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;

/**
 * A SectionBand contains the elements in a section frame. The section
 * frame always takes the entire page width, with the specified height.
 * All elements in a frame are drawn at fixed position and size relative
 * to the frame.
 * <p>
 * A section band can be declared as 'Shrink to Fit'. If this is true,
 * the height of the band could shrink if the elements in the band does
 * not use its occupied space. This is normally used to handle elements
 * with wide range of size. Those elements can be allocated a large size
 * at design time. The band will dynamically adjust its height to adopt
 * to the actual element size.
 * <p>
 * A section band can also contain a subreport. A subreport is a complete
 * report embedded inside a section. Section band fields can be used to
 * supply subreport parameter values. The subreport is printed inside
 * a band. The band automatically grows with the subreport, and could
 * potentially span across multiple pages.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class SectionBand extends FixedContainer {
   /**
    * Create a section band. The report must be the report where the
    * section band will be used.
    * @param report the report this band is associated with.
    */
   public SectionBand(ReportSheet report) {
      super(report);
   }

   /**
    * Get the height of the frame.
    * @return height in inches.
    */
   public float getHeight() {
      return height;
   }

   /**
    * Set the frame height.
    * @param inch frame height in inches.
    */
   public void setHeight(float inch) {
      this.height = Math.max(0, inch); // no negative height
   }

   /**
    * Check if this band should be repeated at top of every page the group is
    * printed. This flag is only recognized for header bands.
    */
   public boolean isRepeatHeader() {
      return repeatIt;
   }

   /**
    * Set whether this band should be repeated at top of every page.
    */
   public void setRepeatHeader(boolean flag) {
      repeatIt = flag;
   }

   /**
    * Check if this band can be broken into regions across page boundary.
    * @return true if band can span across pages. Default to true.
    */
   public boolean isBreakable() {
      return breakable;
   }

   /**
    * Control if a band can span across pages.
    * @param breakable true to enable spanning across pages.
    */
   public void setBreakable(boolean breakable) {
      this.breakable = breakable;
   }

   /**
    * Check if this band is visible.
    */
   public boolean isVisible() {
      return visible;
   }

   /**
    * Show or hide this band.
    * @param vis false to hide this band.
    */
   public void setVisible(boolean vis) {
      this.visible = vis;
   }

   /**
    * Check if this band is visible.
    */
   public boolean isPrintable() {
      return visible;
   }

   /**
    * Check if this band should always start at the top of a page.
    */
   public boolean isPageBefore() {
      return pageBefore;
   }

   /**
    * Set whether a new page should be advanced to before this band
    * is printed.
    */
   public void setPageBefore(boolean pg) {
      pageBefore = pg;
   }

   /**
    * Check if a new page should be advanced to after this band is printed.
    */
   public boolean isPageAfter() {
      return pageAfter;
   }

   /**
    * Set whether a new page should be advanced to after this band
    * is printed.
    */
   public void setPageAfter(boolean pg) {
      pageAfter = pg;
   }

   /**
    * Return the section band background.
    */
   public Color getBackground() {
      return bg;
   }

   /**
    * Set the section band background. The band is transparent if the
    * background is null.
    */
   public void setBackground(Color bg) {
      this.bg = bg;
   }

   @Override
   public Object clone() {
      SectionBand container = (SectionBand) super.clone();
      return container;
   }

   /**
    * Force the band to be breakable.
    */
   public void forceBreakable() {
      forcebreak = true;
   }

   /**
    * Reset the band to not be force broken.
    */
   public void resetForceBreakable() {
      forcebreak = false;
   }

   /**
    * Check whether the band was force to be broken.
    */
   public boolean isForceBreakable() {
      return forcebreak;
   }

   /**
    * This method selectively calls reset() only when a band
    * is continued on the next page.
    * @param continued Specifies if continued at top of next page
    */
   public void reset(boolean continued) {
      // @by stephenwebster, fix bug1400873156569
      // The Override on reset() was implemented for bug1380057168718
      // which essentially saves the state of resized elements in script.
      // This caused a backwards compatibility issue.  It appears we only
      // need to save the state when we continue printing on a new page.
      if(continued) {
         reset();
      }
      else {
         super.reset();
      }
   }

   /**
    * Reset the internal state so it's ready for next printing.
    */
   @Override
   public void reset() {
      List<Rectangle> oprintBounds = new ArrayList<>();

      if(!isBreakable()) {
         for(int i = 0; i < getElementCount(); i++) {
            oprintBounds.add(getPrintBounds(i));
         }
      }

      super.reset();

      // keep the size so change made by script won't be lost. script
      // will not be executed again in continued band but the reset()
      // will be called if band is not breakable
      if(!isBreakable()) {
         for(int i = 0; i < getElementCount(); i++) {
            Rectangle obox = oprintBounds.get(i);
            Rectangle nbox = getPrintBounds(i);

            nbox.width = obox.width;
            nbox.height = obox.height;
            setPrintBounds(i, nbox);
         }
      }
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      StringBuilder sb = new StringBuilder(super.toString());
      sb.append("[");

      for(int i = 0; i < getElementCount(); i++) {
         if(i > 0) {
            sb.append(",");
         }

         ReportElement elem = getElement(i);
         sb.append(elem.toString());

         if(elem instanceof TextElement) {
            sb.append("(");
            sb.append(((TextElement) elem).getText());
            sb.append(")");
         }
      }

      sb.append("]");

      return sb.toString();
   }

   /**
    * Separator is used for describe a vertical seperator in section.
    * Includes the seperator's line style, position and color.
    */
   public static class Separator
      implements Serializable, Cloneable, Comparer, XMLSerializable {
      /**
       * Create a section vertial separator based on the position border
       * and color.
       */
      public Separator() {
      }

      /**
       * Create a section vertial separator based on the position border and
       * color.
       */
      public Separator(int position, int border, Color c) {
         this.position = position;
         this.border = border;
         this.color = c;
      }

      /**
       * Create a section vertial separator based on another separator.
       */
      public Separator(Separator s) {
         this(s.getPosition(), s.getBorder(), s.getColor());
      }

      /**
       * Get position of the vertical separator.
       */
      public int getPosition() {
         return position;
      }

      /**
       * Set position of the vertical separator.
       */
      public void setPosition(int i) {
         position = i;
      }

      /**
       * Get border of the vertical separator.
       */
      public int getBorder() {
         return border;
      }

      /**
       * Set border of the vertical separator.
       */
      public void setBorder(int i) {
         border = i;
      }

      /**
       * Get color of the vertical separator.
       */
      public Color getColor() {
         return color;
      }

      /**
       * Set color of the vertical separator.
       */
      public void setColor(Color c) {
         color = c;
      }

      /**
       * Clone method of the vertical separator.
       */
      @Override
      public Object clone() {
         Separator s = new Separator(position, border, color);

         return s;
      }

      /**
       * Compare two separator regarding their position.
       */
      @Override
      public int compare(Object obj1, Object obj2) {
         if(obj1 instanceof SectionBand.Separator &&
            obj2 instanceof SectionBand.Separator)
         {
            SectionBand.Separator sep1 = (SectionBand.Separator) obj1;
            SectionBand.Separator sep2 = (SectionBand.Separator) obj2;

            return (sep1.getPosition() - sep2.getPosition());
         }

         return 0;
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public int compare(double v1, double v2) {
         if(v1 == Tool.NULL_DOUBLE || v2 == Tool.NULL_DOUBLE) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_DOUBLE ? -1 : 1);
         }

         double val = v1 - v2;

         if(val < NEGATIVE_DOUBLE_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_DOUBLE_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public int compare(float v1, float v2) {
         if(v1 == Tool.NULL_FLOAT || v2 == Tool.NULL_FLOAT) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_FLOAT ? -1 : 1);
         }

         float val = v1 - v2;

         if(val < NEGATIVE_FLOAT_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_FLOAT_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public int compare(long v1, long v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public int compare(int v1, int v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public int compare(short v1, short v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * Writes this object as an XML entity to the specified print writer.
       */
      @Override
      public void writeXML(PrintWriter writer) {
         writer.print("<SectionVerticalSeparator RightPosition=\"" + position +
            "\" Border=\"" + border + "\" Color=\"" + color.getRGB() + "\"");
         writer.print("/>");
      }

      /**
       * Reads in the properties from the specified XML Element.
       */
      @Override
      public void parseXML(Element elem) throws IOException {
         String val;

         if((val = Tool.getAttribute(elem, "RightPosition")) != null) {
            position = Integer.parseInt(val);
         }

         if((val = Tool.getAttribute(elem, "Border")) != null) {
            border = Integer.parseInt(val);
         }

         if((val = Tool.getAttribute(elem, "Color")) != null) {
            color = new Color(Integer.parseInt(val));
         }
      }

      public boolean equals(Object obj) {
         if(obj instanceof Separator) {
            Separator sep = (Separator) obj;

            return position == sep.position && border == sep.border &&
               (color == sep.color ||
               color != null && sep.color != null && color.equals(sep.color));
         }

         return false;
      }

      private int position = 0;
      private int border = StyleConstants.NO_BORDER;
      private Color color = Color.black;
   }

   private float height = 0.3f;
   private boolean pageBefore = false; // pg break before
   private boolean pageAfter = false; // pg break after
   private boolean visible = true; // band visible
   private boolean breakable = true; // across pages
   private Color bg = null; // background
   private boolean repeatIt = false; // repeat this band on top of every page
   private transient boolean forcebreak = false;
}
