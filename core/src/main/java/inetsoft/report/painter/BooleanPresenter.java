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

import inetsoft.report.Presenter;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * The BooleanPresenter can be used to present the value of a boolean
 * as two different icons. By default, a check mark is used to present
 * the TRUE value, and an empty box is used to present the FALSE value.
 * The icon can be changed by supplying the image explicitly. This is
 * normally used in a table to display boolean values as check marks.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class BooleanPresenter implements Presenter {
   /**
    * Create a default boolean presenter.
    */
   public BooleanPresenter() {
   }

   /**
    * Create a boolean presenter with specified images.
    * @param true_image icon for TRUE.
    * @param false_image icon for FALSE.
    */
   public BooleanPresenter(Image true_image, Image false_image) {
      true_mark = true_image;
      false_mark = false_image;
   }

   /**
    * Paint the value.
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

      Image img = null;

      if(v instanceof Number) {
         img = Tool.isZero((Number)v)? false_mark : true_mark;
      }
      else if(v instanceof String) {
         img = ((String) v).equalsIgnoreCase("true") ? true_mark : false_mark;
      }
      else {
         img = (v != null && ((Boolean) v).booleanValue()) ?
            true_mark :
            false_mark;
      }

      if(img != null) {
         int iw = img.getWidth(null), ih = img.getHeight(null);
         Shape clip = g.getClip();

         g.clipRect(x, y, w, h);
         x += (w - iw) / 2;
         y += (h - ih) / 2;
         g.drawImage(img, x, y, null);
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

      Dimension d = (true_mark != null) ?
         new Dimension(true_mark.getWidth(null), true_mark.getHeight(null)) :
         ((false_mark != null) ?
         new Dimension(false_mark.getWidth(null), false_mark.getHeight(null)) :
         new Dimension(0, 0));

      return d;
   }

   /**
    * Check if the presenter can handle this type of objects.
    * @param type object type.
    * @return true if the presenter can handle this type.
    */
   @Override
   public boolean isPresenterOf(Class type) {
      return Boolean.class.isAssignableFrom(type) ||
         Number.class.isAssignableFrom(type) ||
         String.class.isAssignableFrom(type);
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
      if(obj == null) {
         return true;
      }

      if(isPresenterOf(obj.getClass())) {
         if(obj instanceof String) {
            return obj.toString().equalsIgnoreCase("true") ||
               obj.toString().equalsIgnoreCase("false");
         }

         return true;
      }

      return false;
   }

   /**
    * Check if this presenter should always fill the entire area of a cell.
    */
   @Override
   public boolean isFill() {
      return false;
   }

   /**
    * Set the font to use for this presenter. A table calls this function
    * before the cell is printed when a presenter is used.
    */
   @Override
   public void setFont(Font font) {
   }

   /**
    * make sure the images are fully loaded.
    */
   private void loadImages() {
      if(loaded) {
         return;
      }

      loaded = true;

      if(true_mark == null) {
         try {
            true_mark = Tool.getImage(this, "images/checkon.gif");
         }
         catch(Exception e) {
            LOG.error("Failed to load \"check on\" image", e);
         }
      }

      if(false_mark == null) {
         try {
            false_mark = Tool.getImage(this, "images/checkoff.gif");
         }
         catch(Exception e) {
            LOG.error("Failed to load \"check off\" image", e);
         }
      }

      Tool.waitForImage(true_mark);
      Tool.waitForImage(false_mark);
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
      return Catalog.getCatalog().getString("Check mark");
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

   private transient boolean loaded = false;
   private transient Image true_mark = null;
   private transient Image false_mark = null;
   private Color bg = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(BooleanPresenter.class);
}
