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
package inetsoft.uql;

import inetsoft.sree.SreeEnv;
import inetsoft.util.Catalog;
import inetsoft.util.ThreadContext;

import java.rmi.RemoteException;
import java.util.*;

/**
 * DataSourceListing describes a type of data source and creates a template instance of the data
 * source.
 */
public abstract class DataSourceListing {
   /**
    * Creates a new instance of DataSourceListing.
    *
    * @param name     the name of the data source type.
    * @param category the category of data source.
    * @param icon     the URL of the icon.
    */
   protected DataSourceListing(String name, String category, String icon) {
      this.name = name;
      this.category = category;
      this.icon = icon;
   }

   /**
    * Gets the name of the data source type.
    *
    * @return the name.
    */
   public String getName() {
      return name;
   }

   /**
    * Gets the display name of the data source type.
    *
    * @return the display name.
    */
   public String getDisplayName() {
      return getDisplayName(ThreadContext.getLocale());
   }

   /**
    * Gets the display name of the data source type.
    *
    * @param locale the locale used to translate the name.
    *
    * @return the display name.
    */
   public String getDisplayName(Locale locale) {
      String displayName;

      try {
         displayName = getResourceBundle(locale).getString(name);
      }
      catch(MissingResourceException ignore) {
         String resource = SreeEnv.getProperty(Catalog.DEFAULT_BUNDLE, Catalog.DEFAULT_BUNDLE);
         Catalog catalog = Catalog.getResCatalog(resource, Catalog.DEFAULT_BUNDLE, locale);
         displayName = catalog.getString(name);
      }

      if(displayName == null) {
         return name;
      }

      return displayName;
   }

   /**
    * Gets the category of the data source type.
    *
    * @return the category.
    */
   public String getCategory() {
      return category;
   }

   /**
    * Gets the URL of the icon.
    *
    * @return the icon URL.
    */
   public String getIcon() {
      return icon;
   }

   /**
    * Gets keywords for searching.
    * @return
    */
   public String[] getKeywords() {
      return keywords.toArray(new String[0]);
   }

   /**
    * Add a keyword
    * @param keyword
    */
   public void addKeyword(String keyword) {
      keywords.add(keyword);
   }

   /**
    * Add keywords
    * @param keywords
    */
   public void addKeywords(List<String> keywords) {
      keywords.addAll(keywords);
   }

   /**
    * Creates a new instance of the data source.
    *
    * @return a new data source.
    */
   public abstract XDataSource createDataSource() throws Exception;

   /**
    * Gets the next available name for a data source.
    *
    * @return the next available name.
    *
    * @throws RemoteException if an error occurs querying the repository.
    */
   protected String getAvailableName() throws RemoteException {
      String base = getDisplayName()
         .replaceAll("[/?\"*:<>|.\\\\]", " ")
         .replaceAll("\\s+", " ")
         .trim();
      String newName = base;
      XRepository repository = XFactory.getRepository();
      final List<String> names = Arrays.asList(repository.getDataSourceNames());
      int i = 1;

      while(names.contains(newName)) {
         newName = base + i++;
      }

      return newName;
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
   protected ResourceBundle getResourceBundle(Locale locale) {
      String baseName = getClass().getPackage().getName() + ".Bundle";
      return ResourceBundle.getBundle(baseName, locale, getClass().getClassLoader());
   }

   private final String name;
   private final String category;
   private final String icon;
   private final Set<String> keywords = new HashSet<>();
}
