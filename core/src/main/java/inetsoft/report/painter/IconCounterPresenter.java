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

import inetsoft.graph.internal.GTool;
import inetsoft.report.Presenter;
import inetsoft.report.internal.MetaImage;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * The IconCounterPresenter presents an integer number as a number of
 * icons. It is commonly used to represent ratings or other small scale
 * numbers. The image used in the presenter can be changed by the users.
 * This can be used in a table to display numbers as number of icons.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class IconCounterPresenter implements Presenter {
   /**
    * Create a default counter presenter.
    */
   public IconCounterPresenter() {
   }

   /**
    * Create a counter presenter using the specified image as the icon.
    * @param image icon image.
    */
   public IconCounterPresenter(Image image) {
      icon = image;
   }

   /**
    * Set the icon used in this presenter.
    */
   public void setIcon(Image icon) {
      this.icon = icon;
   }

   /**
    * Get the icon used in this presenter.
    */
   public Image getIcon() {
      return icon;
   }

   /**
    * Set the icon color. The following color icons are builtin: red, green,
    * blue, rust, yellow.
    */
   public void setIconColor(Color color) {
      this.color = color;
   }

   /**
    * Get the icon color theme.
    */
   public Color getIconColor() {
      return color;
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
      loadImages();

      Image img = icon;

      if(img instanceof MetaImage) {
         img = ((MetaImage) img).getImage();
      }

      if(img != null && v instanceof Number) {
         Shape clip = g.getClip();

         double n = ((Number) v).doubleValue();
         int iw = icon.getWidth(null), ih = icon.getHeight(null);

         g.clipRect(x, y, (int) (n * (iw + 2) + 2), h);

         int cnt = (int) Math.min(w / iw + 1, Math.ceil(n));
         x += 2;
         y += (h - ih) / 2;

         for(int i = 0; i < cnt; i++, x += iw + 2) {
            g.drawImage(img, x, y, null);
         }

         g.setClip(clip);
      }
   }

   /**
    * Calculate the preferred size of the object representation.
    * @param v object value.
    * @return preferred size.
    */
   @Override
   public Dimension getPreferredSize(Object v) {
      loadImages();

      if(icon != null && v != null && v instanceof Number) {
         int n = (int) Math.ceil(((Number) v).doubleValue());
         int iconw = icon.getWidth(null);
         int iconh = icon.getHeight(null);

         if(iconw > 0 && iconh > 0) {
            return new Dimension(n * (iconw + 2) + 2, iconh + 2);
         }
      }

      return new Dimension(0, 0);
   }

   /**
    * Check if this presenter should always fill the entire area of a cell.
    */
   @Override
   public boolean isFill() {
      return false;
   }

   /**
    * Check if the presenter can handle this type of objects.
    * @param type object type.
    * @return true if the presenter can handle this type.
    */
   @Override
   public boolean isPresenterOf(Class type) {
      return Number.class.isAssignableFrom(type);
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
      return (obj == null) ? false : isPresenterOf(obj.getClass());
   }

   /**
    * Make sure the images are fully loaded.
    */
   private void loadImages() {
      if(icon == null) {
         String gif = "images/beancount.gif";

         if(color != null && color.getAlpha() != 0) {
            gif = "images/star.gif";
         }

         try {
            icon = Tool.getImage(this, gif);

            if(color != null && color.getAlpha() != 0) {
               icon = GTool.changeHue(icon, color);
            }
         }
         catch(Exception e) {
            LOG.error("Failed to load image: " + gif, e);
         }
      }

      if(icon != null) {
         Tool.waitForImage(icon);
      }
   }

   /**
    * Set the font to use for this presenter. A table calls this function
    * before the cell is printed when a presenter is used.
    */
   @Override
   public void setFont(Font font) {
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
      return Catalog.getCatalog().getString("Stars (Counter)");
   }

   /**
    * Determine if this Presenter requires raw (unformatted) data.
    *
    * @return <code>true</code>.
    */
   @Override
   public boolean isRawDataRequired() {
      return true;
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
   private Color color = null;
   private transient Image icon = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(IconCounterPresenter.class);
}
