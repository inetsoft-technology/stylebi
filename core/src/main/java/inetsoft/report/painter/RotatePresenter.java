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
package inetsoft.report.painter;

import inetsoft.report.*;
import inetsoft.report.internal.Common;

import java.awt.*;

/**
 * This presenter rotates text in a table cell.
 * @author InetSoft Technology Corp.
 * @since 4.5
 */
abstract class RotatePresenter implements Presenter {
   /**
    * Calculate the preferred size of the object representation.
    * @param v object value.
    * @return preferred size.
    */
   @Override
   public Dimension getPreferredSize(Object v) {
      FontMetrics fm = Common.getFontMetrics(font);

      return new Dimension((int) Common.getHeight(font),
         (int) Common.stringWidth(v == null ? "" : v.toString(), font, fm));
   }

   /**
    * Check if this presenter should always fill the entire area of a cell.
    * @return <code>true</code> if this presenter should fill it's cell;
    * <code>false</code> otherwise.
    */
   @Override
   public boolean isFill() {
      return false;
   }

   /**
    * Check if the presenter can handle this type of objects.
    * @param type object type.
    * @return <code>true</code> if the presenter can handle this type.
    */
   @Override
   public boolean isPresenterOf(Class type) {
      return true;
   }

   /**
    * Check if the presenter can handle this particular object. Normally
    * a presenter handles a class of objects, which is checked by the
    * isPresenterOf(Class) method. If this presenter does not care about
    * the value in the object, it can just call the isPresenterOf() with
    * the class of the object, e.g.<pre>
    *   if(type == null) {
    *      return false;
    *   }
    *   return isPresenterOf(obj.getClass());
    * </pre>
    * @param obj object type.
    * @return true if the presenter can handle this type.
    */
   @Override
   public boolean isPresenterOf(Object obj) {
      // @by larryl, a null value may be replaced if a format is defined on
      // a cell, and changed to a non-null at runtime
      return true;
   }

   /**
    * Paint an object at the specified location.
    * @param g graphical context.
    * @param v object value.
    * @param x x coordinate of the left edge of the paint area.
    * @param y y coordinate of the upper edge of the paint area.
    * @param w area width.
    * @param h area height.
    */
   @Override
   public void paint(Graphics g, Object v, int x, int y, int w, int h) {
      if(v == null) {
         return;
      }

      Painter painter = new InternalPainter(v);

      Common.paintRotate(painter, g, (float) x, (float) y, (float) w,
         (float) h, getAngle());
   }

   /**
    * Set the font to use for this presenter. A table calls this function
    * before the cell is printed when a presenter is used.
    * @param font the font with which this presenter will render text.
    */
   @Override
   public void setFont(Font font) {
      this.font = font;
   }

   /**
    * Determine if this Presenter requires raw (unformatted) data.
    *
    * @return <code>false</code>.
    */
   @Override
   public boolean isRawDataRequired() {
      return false;
   }

   /**
    * Get the angle, in degrees, to rotate the text.
    * @return the angle.
    */
   abstract int getAngle();

   class InternalPainter implements Painter {
      /**
       * Creates a new instance of InternalPainter.
       * @param value the value to paint.
       */
      public InternalPainter(Object value) {
         this.value = value;
      }

      /**
       * Return the preferred size of this painter. If the width and height
       * are negative, the preferred size is specified as 1/1000 of the
       * available page width (minus margins). For example, (-900, -500)
       * generates a size of 90% of the page width as the preferred width,
       * and 1/2 of the page width as the preferred height.
       * @return size.
       */
      @Override
      public Dimension getPreferredSize() {
         FontMetrics fm = Common.getFontMetrics(RotatePresenter.this.font);

         return new Dimension(fm.stringWidth(value.toString()), fm.getHeight());
      }

      /**
       * If scalable is false, the painter is always sized to the preferred
       * size. If the size on the page is different from the preferred
       * size, the painter image is scaled(by pixels) to fit the page area.
       * If scalable is true, the painter will be printed in the actual size
       * on page, which may or may not be the same as the preferred size.
       * The painter needs to check the width and height in the paint()
       * method to know the actual size, and do the scaling by itself in paint().
       * @return scalable option.
       */
      @Override
      public boolean isScalable() {
         return false;
      }

      /**
       * Paint contents at the specified location.
       * @param g graphical context.
       * @param x x coordinate of the left edge of the paint area.
       * @param y y coordinate of the upper edge of the paint area.
       * @param w area width.
       * @param h area height.
       */
      @Override
      public void paint(Graphics g, int x, int y, int w, int h) {
         Font ofont = g.getFont();
         Font font = RotatePresenter.this.font;
         FontMetrics fm = Common.getFontMetrics(font);
         int y0 = y + fm.getHeight() - fm.getDescent();

         g.setFont(font);
         Common.drawString(g, value.toString(), x, y0);
         g.setFont(ofont);
      }

      private Object value = null;
   }

   /**
    * Set the background.
    */
   @Override
   public void setBackground(Color bg) {
      this.bg = bg;
   }

   /**
    * Get the background.
    */
   public Color getBackground() {
      return bg;
   }

   private Color bg = null;
   private Font font = new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, 0, 10);
}
