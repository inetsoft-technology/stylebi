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
package inetsoft.graph.internal;

import inetsoft.report.internal.Common;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.awt.*;
import java.util.Map;
import java.util.Properties;

/**
 * This implementation provides a default implementation without dependency on
 * StyleReport classes.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class DefaultImpl implements GImpl {
   /**
    * Get the font metrics of the font.
    */
   @Override
   public synchronized FontMetrics getFontMetrics(Font fn) {
      // optimized so consecutive calls with same font returns immediately
      if(fn == lastFn) {
         return lastFm;
      }

      FontMetrics fm = fmcache.get(fn);

      if(fm == null) {
         fm = Common.getFractionalFontMetrics(fn);
         fmcache.put(fn, fm);
      }

      lastFn = fn;
      lastFm = fm;

      return fm;
   }

   /**
    * Calculate the width of the string.
    */
   @Override
   public double stringWidth(String str, Font fn) {
      return getFontMetrics(fn).stringWidth(str);
   }

   /**
    * Break the text into lines.
    */
   @Override
   public String[] breakLine(String text, Font fn, int spacing,
                             float width, float height)
   {
      return new String[] {text};
   }

   /**
    * Draw a string on the graphics output.
    */
   @Override
   public void drawString(Graphics2D g, String str, double x, double y) {
      if(str == null || str.isEmpty()) {
         return;
      }

      Common.drawString(g, str, (float) x, (float) y);
   }

   /**
    * Get a property defined in the configuration.
    */
   @Override
   public String getProperty(String name, String def) {
      return prop.getProperty(name, def);
   }

   /**
    * Set a property defined in the configuration.
    */
   @Override
   public void setProperty(String name, String val) {
      prop.setProperty(name, val);
   }

   /**
    * Get a localized string.
    */
   @Override
   public String getString(String str, Object... params) {
      return str;
   }

   /**
    * Unwrap a script value.
    */
   @Override
   public Object unwrap(Object val) {
      return val;
   }

   private static Font lastFn = null;
   private static FontMetrics lastFm = null;
   private static Map<Object, FontMetrics> fmcache = new Object2ObjectOpenHashMap<>();
   private static Properties prop = new Properties(System.getProperties());
}
