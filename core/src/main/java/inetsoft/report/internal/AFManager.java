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
package inetsoft.report.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The AFManager is a singleton class that keeps a cache of the font
 * metrics objects. New font metrics objects are created by parsing
 * the file in the afm directory with the font name appended with
 * '.afm' postfix.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class AFManager implements java.io.Serializable {
   /**
    * Get a font metrics object for the named font and size. 
    * @param name font name, using PS naming.
    * @param size font size in points.
    * @return font metrics.
    */
   public static AFontMetrics getFontMetrics(String name, int size) {
      name = name.toLowerCase().replace('-', '_');
      String cname = "inetsoft.report.afm." + name;

      try {
         AFontMetrics afont = (AFontMetrics) Class.forName(cname).newInstance();

         afont.setSize(size);
         return afont;
      }
      catch(ClassNotFoundException e) {// ignore
      }
      catch(Exception e) {
         LOG.warn("Failed to create font metrics: " + cname, e);
      }

      return null;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(AFManager.class);
}

