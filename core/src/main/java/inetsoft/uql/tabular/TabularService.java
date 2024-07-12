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
package inetsoft.uql.tabular;

import inetsoft.util.gui.SVGIcon;
import inetsoft.util.ThreadContext;

import java.util.*;

/**
 * This class is the API of a service (to be loaded by ServiceLoader) that
 * defines the implementation of a tabular data source.
 *
 * @version 12.0, 11/15/2013
 * @author InetSoft Technology Corp
 */
public abstract class TabularService {
   /**
    * The data source type is an unique identifier to identify this
    * data source type. In addition, it's displayed on the GUI as the
    * label for this data source type.
    */
   public abstract String getDataSourceType();

   /**
    * Get the fully qualified name of the data source class.
    */
   public abstract String getDataSourceClass();

   /**
    * Get the fully qualified name of the query class.
    */
   public abstract String getQueryClass();

   /**
    * Get the fully qualified name of the runtime class that implements
    * the TabularQuery API.
    */
   public abstract String getRuntimeClass();

   /**
    * Get the label for display the data source type on GUI.
    */
   public String getDisplayLabel() {
      return getDisplayLabel(ThreadContext.getLocale());
   }

   /**
    * Gets the display label for this type of data source.
    *
    * @param locale the locale used to translate the label.
    *
    * @return the display label.
    */
   @SuppressWarnings("UnusedParameters")
   public String getDisplayLabel(Locale locale) {
      return getDataSourceType();
   }
   
   /**
    * Get the icon for representing the data source on GUI.
    */
   public String getIcon() {
      return SVGIcon.getPath("tabular-data.svg");
   }

   /**
    * Get the descriptive text for the data source type.
    */
   public String getDescription() {
      String description;

      try {
         ResourceBundle bundle = getResourceBundle();
         String key = getDataSourceClass() + ".description";
         description = bundle.getString(key);
      }
      catch(MissingResourceException ignore) {
         description = "";
      }

      return description;
   }

   /**
    * Gets the resource bundle that provides localized strings for this type of
    * data source.
    *
    * @return the resource bundle.
    */
   public ResourceBundle getResourceBundle() {
      return getResourceBundle(ThreadContext.getLocale());
   }

   /**
    * Gets the resource bundle that provides localized strings for this type of
    * data source.
    * <p>
    * By default, it is expected that a resource bundle with the base name of
    * <tt>getClass().getPackage().getName() + ".Bundle"</tt> be provided by
    * implementing classes. If it is not, this method needs to be overridden to
    * provide an alternative.
    *
    * @return the resource bundle.
    */
   public ResourceBundle getResourceBundle(Locale locale) {
      String baseName = getClass().getPackage().getName() + ".Bundle";
      return ResourceBundle.getBundle(baseName, locale, getClass().getClassLoader());
   }
}
