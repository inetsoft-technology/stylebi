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
import inetsoft.report.internal.Bounds;
import inetsoft.report.internal.Common;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.awt.*;

/**
 * The ButtonPresenter paints a string value inside a 3D button border.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ButtonPresenter implements Presenter, StyleConstants {
   /**
    * Create a default ButtonPresenter.
    */
   public ButtonPresenter() {
   }

   /**
    * Create a button presenter with specified button color and font.
    * @param buttoncolor color for button border.
    * @param buttonfont font for button label.
    */
   public ButtonPresenter(Color buttoncolor, Font buttonfont) {
      color = buttoncolor;
      font = buttonfont;
   }

   /**
    * Set the button color.
    * @param color button color.
    */
   public void setColor(Color color) {
      this.color = color;
   }

   /**
    * Get the button color.
    * @return button color.
    */
   public Color getColor() {
      return color;
   }

   /**
    * Set the button font.
    * @param font label font.
    */
   @Override
   public void setFont(Font font) {
      this.font = font;
   }

   /**
    * Get the label font.
    * @return font.
    */
   public Font getFont() {
      return font;
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

      ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                          RenderingHints.VALUE_ANTIALIAS_ON);
      ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                          RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      Color c = g.getColor();
      Font f = g.getFont();

      g.setColor(color);
      g.fill3DRect(x, y, w, h, true);

      g.setColor(Color.black);
      g.setFont(font);

      Common.paintText(g, Tool.toString(v), new Bounds(x, y, w, h),
                       H_CENTER | V_CENTER, true, false, 0);

      g.setColor(c);
      g.setFont(f);
   }

   /**
    * Calculate the preferred size of the object representation.
    * @param v object value.
    * @return preferred size.
    */
   @Override
   public Dimension getPreferredSize(Object v) {
      if(v == null) {
         return new Dimension(0, 0);
      }

      return new Dimension(fm.stringWidth(Tool.toString(v)), fm.getHeight());
   }

   /**
    * Check if this presenter should always fill the entire area of a cell.
    */
   @Override
   public boolean isFill() {
      return true;
   }

   /**
    * Check if the presenter can handle this type of objects.
    * @param type object type.
    * @return true if the presenter can handle this type.
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
      return true;
   }

   /**
    * Get the display name of this presenter.
    *
    * @return a user-friendly name for this presenter.
    *
    * @since 5.1
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("Button");
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

   private Color color = Color.lightGray;
   private Font font = new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.BOLD, 10);
   FontMetrics fm = Common.getFontMetrics(font);
   private Color bg = null;
}