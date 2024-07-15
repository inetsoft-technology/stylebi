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
package inetsoft.report.painter;

import inetsoft.report.ReportElement;
import inetsoft.report.internal.ExpandablePainter;

import java.awt.*;

/**
 * HTML Painter is not a real painter. It does not define any logic for
 * painting a report area. Instead it contains HTML tag that can be
 * placed in generated HTML output for the reserved area. This can only be used
 * in Style Report/EE HTML viewing mode.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class HTMLSource implements ExpandablePainter {
   /**
    * Create an empty html painter. The html should be set using setHTML before
    * this object is used.
    */
   public HTMLSource() {
   }

   /**
    * Create a painter for the specified html.
    */
   public HTMLSource(String html) {
      setHTML(html);
   }

   /**
    * Set the HTML code to be used in this area.
    */
   public void setHTML(String html) {
      this.html = html;
   }

   /**
    * Get the HTML code to be used in this area.
    */
   public String getHTML() {
      return html;
   }

   /**
    * Check if this painter is expandable. A painter may implement the
    * ExpandablePainter interface but not expandable at rendering time.
    */
   @Override
   public boolean isExpandable() {
      return true;
   }

   /**
    * Return the preferred size of this painter.
    */
   @Override
   public Dimension getPreferredSize() {
      if(elem != null) {
	 presenter.setFont(elem.getFont());
      }

      return presenter.getPreferredSize(html);
   }

   /**
    * Calculate the preferred size of the object representation.
    * @param width the maximum width of the painter.
    * @return preferred size.
    */
   @Override
   public Dimension getPreferredSize(float width) {
      if(elem != null) {
	 presenter.setFont(elem.getFont());
      }

      return presenter.getPreferredSize(html, width);
   }

   /**
    * Draw an empty box with label HTML Painter
    */
   @Override
   public void paint(Graphics g, int x, int y, int w, int h) {
      paint(g, x, y, w, h, 0, -1);
   }

   /**
    * Paint contents at the specified location.
    * @param g graphical context.
    * @param x x coordinate of the left edge of the paint area.
    * @param y y coordinate of the upper edge of the paint area.
    * @param w area width.
    * @param h area height.
    * @param bufy if the painter is drawn across pages, bufy is the height
    * already consumed in previous pages.
    * @param bufh is the height available on the current page.
    */
   @Override
   public void paint(Graphics g, int x, int y, int w, int h,
                     float bufy, float bufh) {
      if(elem != null) {
	 presenter.setFont(elem.getFont());
      }

      presenter.paint(g, html, x, y, w, h, bufy, bufh);
   }

   /**
    * Get the adjustment on height if the height is adjusted to line boundary.
    */
   @Override
   public float getHeightAdjustment(ReportElement elem, Dimension pd,
                                    float bufy, float bufw, float bufh) {
      presenter.setFont(elem.getFont());
      return presenter.getHeightAdjustment(html, elem, pd, bufy, bufw, bufh);
   }

   /**
    * HTML source is not scalable.
    */
   @Override
   public boolean isScalable() {
      return true;
   }

   /**
    * Set the element that is associated with this painter.
    */
   public void setContext(ReportElement elem) {
      this.elem = elem;
   }

   /**
    * Set the origin offset for rotated painter.
    */
   public void setOriginOffset(int offset) {
      this.originOffset = offset;
   }

   /**
    * Get the origin offset.
    */
   public int getOriginOffset() {
      return this.originOffset;
   }

   private HTMLPresenter presenter = new HTMLPresenter();
   private Dimension psize = new Dimension(100, 100);
   private String html = "";
   private ReportElement elem;
   private int originOffset;
}

