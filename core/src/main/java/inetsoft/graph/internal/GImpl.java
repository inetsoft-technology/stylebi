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
package inetsoft.graph.internal;

import java.awt.*;

/**
 * This interface defines calls that may be implemented by class out side of
 * the inetsoft.graph package.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public interface GImpl {
   /**
    * Get the font metrics of the font.
    */
   public FontMetrics getFontMetrics(Font font);

   /**
    * Calculate the width of the string.
    */
   public double stringWidth(String str, Font fn);

   /**
    * Break the text into lines.
    */
   public String[] breakLine(String text, Font fn, int spacing,
                             float width, float height);

   /**
    * Draw a string on the graphics output.
    */
   public void drawString(Graphics2D g, String str, double x, double y);

   /**
    * Get a property defined in the configuration.
    */
   public String getProperty(String name, String def);

   /**
    * Set a property defined in the configuration.
    */
   public void setProperty(String name, String val);

   /**
    * Get a localized string.
    */
   public String getString(String str, Object... params);

   /**
    * Unwrap a script value.
    */
   public Object unwrap(Object val);
}
