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
package inetsoft.report.painter;

import inetsoft.report.Presenter;
import inetsoft.report.StyleFont;
import inetsoft.report.internal.Common;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.awt.*;
import java.lang.reflect.Array;

/**
 * The HeaderPresenter displays a header field with two to three labels.
 * The cell rectangle is divided into two or three fields by drawing
 * diagonal lines from top-left to bottom right.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class HeaderPresenter implements Presenter {
   /**
    * Create a default header presenter.
    */
   public HeaderPresenter() {
   }

   /**
    * Set the label font.
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
      if(v == null) {
         return;
      }

      FontMetrics fm = Common.getFontMetrics(font);
      // draw the three labels
      Graphics2D g2 = (Graphics2D) g.create();

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                          RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                          RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setFont(font);
      g2.clipRect(x, y, w, h);
      g2.translate(x, y);

      String[] vals = getValues(v);
      int cnt = vals.length;
      int ty = fm.getAscent() - fm.getHeight() / 2;

      if(vals.length == 2) {
         double angle1 = Math.atan2(h, w);
         double angle2 = Math.atan2(w, h);
         float dist = (float) (fm.getHeight() / (2 * Math.sin(angle1)));

         Common.rotate(g2, angle1 / 2);
         Common.drawString(g2, vals[0], dist + 1, ty);

         Common.rotate(g2, angle1 / 2);
         g2.drawLine(0, 0, 2 * (w + h), 0);

         dist = (float) (fm.getHeight() / (2 * Math.sin(angle2)));
         Common.rotate(g2, angle2 / 2);
         Common.drawString(g2, vals[1], dist + 1, ty);
      }
      else {
         double angle = Math.PI / (2 * cnt);
         float dist = (float) (fm.getHeight() / (2 * Math.sin(angle)));

         Common.rotate(g2, angle / 2);

         for(int i = 0; i < cnt; i++) {
            Common.drawString(g2, vals[i], dist + 1, ty);
            Common.rotate(g2, angle / 2);

            if(i < cnt - 1) {
               g2.drawLine(0, 0, 2 * (w + h), 0);
               Common.rotate(g2, angle / 2);
            }
         }
      }
      //fix Bug #22858, when paint table head finish, the rotation should reset to 0.
      Common.rotate(g2, 0);
      g2.dispose();
   }

   /**
    * Calculate the preferred size of the object representation.
    * @param v object value.
    * @return preferred size.
    */
   @Override
   public Dimension getPreferredSize(Object v) {
      if(v == null) {
         return new Dimension();
      }

      String[] vals = getValues(v);
      int cnt = vals.length;
      FontMetrics fm = Common.getFontMetrics(font);
      double angle = Math.PI / (2 * cnt);
      Dimension d = new Dimension();
      double dist = fm.getHeight() / (2 * sin15);

      for(int i = 0; i < cnt; i++) {
         float w = Common.stringWidth(vals[i], font, fm);

         d.width = Math.max(d.width,
            (int) ((w + dist + fm.getHeight() / 2) *
            Math.cos(angle * (i + 0.5))));
         d.height = Math.max(d.height,
            (int) ((w + dist + fm.getHeight() / 2) *
            Math.sin(angle * (i + 0.5))));
      }

      return d;
   }

   /**
    * Check if the presenter can handle this type of objects.
    * @param type object type.
    * @return true if the presenter can handle this type.
    */
   @Override
   public boolean isPresenterOf(Class type) {
      return true; // type.isArray() || type == String.class;
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
      return true; // (obj == null) ? false : isPresenterOf(obj.getClass());
   }

   /**
    * Check if this presenter should always fill the entire area of a cell.
    */
   @Override
   public boolean isFill() {
      return true;
   }

   /**
    * Get two or three values from an array or string.
    */
   public String[] getValues(Object val) {
      if(val.getClass().isArray()) {
         String[] arr = new String[Array.getLength(val)];

         for(int i = 0; i < arr.length; i++) {
            Object item = Array.get(val, i);

            arr[i] = (item == null) ? "" : item.toString();
         }

         return arr;
      }

      String str = val.toString();

      return Tool.split(str, delim, true);
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
      return Catalog.getCatalog().getString("Multi-Header");
   }

   /**
    * Determine if this Presenter requires raw (unformatted) data.
    *
    * @return <code>true</code>.
    */
   @Override
   public boolean isRawDataRequired() {
      // @by davy, why need raw data? sounds unreasonable
      // fix bug1392103441466
      return false;
   }

   /**
    * Get the delimitor used to parse the string into headers.
    */
   public String getDelimitor() {
      return delim;
   }

   /**
    * Set the delimitor used to parse the string into headers.
    */
   public void setDelimitor(String delim) {
      this.delim = delim;
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

   private Font font = new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.BOLD, 10);
   private String delim = "|";
   private static double sin30 = Math.sin(Math.PI * 30 / 180);
   private static double cos30 = Math.cos(Math.PI * 30 / 180);
   private static double sin15 = Math.sin(Math.PI * 15 / 180);
   private Color bg = null;
}
