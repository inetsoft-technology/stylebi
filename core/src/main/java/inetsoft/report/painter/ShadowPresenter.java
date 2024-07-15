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

import inetsoft.report.*;
import inetsoft.report.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.awt.*;

/**
 * The ShadowPresenter paints a string value inside a 3D shadow border.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ShadowPresenter implements Presenter, StyleConstants {
   /**
    * Create a default ShadowPresenter.
    */
   public ShadowPresenter() {
   }

   /**
    * Create a shadow presenter with specified font.
    * @param shadowfont font for shadow label.
    */
   public ShadowPresenter(Font shadowfont) {
      font = shadowfont;
   }

   /**
    * Set the shadow font.
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
    * Set the shading color. The shading is the area where the text is
    * drawn.
    * @param shading shading color.
    */
   public void setShading(Color shading) {
      this.shading = shading;
   }

   /**
    * Get the shading color.
    * @return shading color.
    */
   public Color getShading() {
      return shading;
   }

   /**
    * Set the text color.
    * @param textcolor color of the text.
    */
   public void setTextColor(Color textcolor) {
      textC = textcolor;
   }

   /**
    * Get the text color.
    * @return text color.
    */
   public Color getTextColor() {
      return textC;
   }

   /**
    * Set the shadow width.
    */
   public void setShadowWidth(int shadow) {
      this.shadow = shadow;
   }

   /**
    * Get the shadow width.
    */
   public int getShadowWidth() {
      return shadow;
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
      Color c = g.getColor();
      Font f = g.getFont();

      g.setColor(Color.white);
      g.fillRect(x, y, w, h);
      g.setColor(Color.lightGray);
      g.fillRect(x + shadow, y + shadow, w - shadow, h - shadow);
      g.setColor(shading);
      g.fillRect(x, y, w - shadow, h - shadow);
      g.setColor(Color.gray);
      g.drawRect(x, y, w - shadow, h - shadow);

      if(v != null) {
         g.setColor(textC);
         g.setFont(font);
         Common.paintText(g, Tool.toString(v),
            new Bounds(x, y, w - shadow, h - shadow), H_CENTER | V_CENTER,
            true, false, 0);
      }

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

      Size d = StyleCore.getTextSize(v.toString(), font, 0);

      return new Dimension((int) d.width + shadow + 8,
                           (int) d.height + shadow + 2);
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
      return obj != null;
   }

   /**
    * Check if this presenter should always fill the entire area of a cell.
    */
   @Override
   public boolean isFill() {
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
      return Catalog.getCatalog().getString("Shadow");
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

   private Color bg = null;
   private Font font = new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.BOLD, 10);
   private FontMetrics fm = Common.getFontMetrics(font);
   private int shadow = 4;
   private Color textC = Color.black;
   private Color shading = Color.white;
}