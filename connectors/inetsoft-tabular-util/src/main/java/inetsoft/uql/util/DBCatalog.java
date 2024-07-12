/*
 * inetsoft-tabular-util - StyleBI is a business intelligence web application.
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
package inetsoft.uql.util;

import inetsoft.util.*;
import java.util.*;

/**
 * Map a string from bundle or catalog. The string is retrieved from catalog
 * if it's defined, otherwise it's from the bundle.
 */
public class DBCatalog {
   public synchronized static DBCatalog getCatalog(String bundle) {
      DBCatalog catalog = catalogs.get(bundle);
      
      if(catalog == null) {
         catalog = new DBCatalog(bundle);
         catalogs.put(bundle, catalog);
      }

      return catalog;
   }
   
   private DBCatalog(String bundleResource) {
      bundle = ResourceBundle.getBundle(bundleResource);
      catalog = Catalog.getCatalog();
   }

   public String getString(String id, String... params) {
      String str = catalog.getIDString(id, (Object[]) params);
      
      if(str == null) {
         str = bundle.getString(id);
      }
      
      if(str == null) {
         str = catalog.getString(id, (Object[]) params);
      }
      
      return str;
   }

   private ResourceBundle bundle;
   private Catalog catalog;
   private static Map<String,DBCatalog> catalogs = new HashMap<>();
}
