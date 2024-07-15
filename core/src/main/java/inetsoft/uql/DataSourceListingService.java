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
package inetsoft.uql;

import inetsoft.sree.SreeEnv;
import inetsoft.util.Plugins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

/**
 * Interface for classes that provide data source listings.
 */
public interface DataSourceListingService {
   /**
    * Gets the data source listings provided by this service.
    *
    * @return the listings.
    */
   List<DataSourceListing> getDataSourceListings();

   /**
    * Gets an input stream for a listing's icon.
    *
    * @param listing the data source listing.
    *
    * @return an input stream of the icon content.
    */
   default InputStream getIcon(DataSourceListing listing) {
      return getClass().getResourceAsStream(listing.getIcon());
   }

   /**
    * Gets all data source listings available on the classpath and in plugins.
    *
    * @return the listings.
    */
   static List<DataSourceListing> getAllDataSourceListings(boolean applyFilters) {
      Set<String> includeFilter = new HashSet<>();
      Set<String> excludeFilter = new HashSet<>();
      String property;

      if(applyFilters && (property = SreeEnv.getProperty("visible.datasource.types")) != null &&
         !property.trim().isEmpty())
      {
         for(String name : property.split(",")) {
            includeFilter.add(name.trim());
         }
      }

      if(applyFilters && (property = SreeEnv.getProperty("hidden.datasource.types")) != null &&
         !property.trim().isEmpty())
      {
         for(String name : property.split(",")) {
            excludeFilter.add(name.trim());
         }
      }

      List<DataSourceListing> listings = new ArrayList<>();
      ServiceLoader<DataSourceListingService> loader = ServiceLoader
         .load(DataSourceListingService.class, DataSourceListingService.class.getClassLoader());

      for(DataSourceListingService service : loader) {
         for(DataSourceListing listing: service.getDataSourceListings()) {
            if((includeFilter.isEmpty() || includeFilter.contains(listing.getName())) &&
               (excludeFilter.isEmpty() || !excludeFilter.contains(listing.getName())))
            {
               listings.add(listing);
            }
         }
      }

      for(DataSourceListingService service :
         Plugins.getInstance().getServices(DataSourceListingService.class, null))
      {
         for(DataSourceListing listing: service.getDataSourceListings()) {
            if((includeFilter.isEmpty() || includeFilter.contains(listing.getName())) &&
               (excludeFilter.isEmpty() || !excludeFilter.contains(listing.getName())))
            {
               listings.add(listing);
            }
         }
      }

      return listings;
   }

   /**
    * Gets the data source listing with the specified name.
    *
    * @param listingName the name of the listing.
    *
    * @return the listing or {@code null} if it does not exist.
    */
   static DataSourceListing getDataSourceListing(String listingName) {
      return DataSourceListingService
         .getAllDataSourceListings(true)
         .stream()
         .filter(l -> listingName.equals(l.getDisplayName()))
         .findFirst()
         .orElse(null);
   }

   /**
    * Gets the icon for the data source listing with the specified name.
    *
    * @param listingName the name of the listing.
    *
    * @return an input stream for the icon or {@code null} if it does not exist.
    */
   static InputStream getDataSourceListingIcon(String listingName) {
      ServiceLoader<DataSourceListingService> loader = ServiceLoader
         .load(DataSourceListingService.class, DataSourceListingService.class.getClassLoader());

      for(DataSourceListingService service : loader) {
         for(DataSourceListing listing : service.getDataSourceListings()) {
            if(listing.getName().equals(listingName)) {
               return service.getIcon(listing);
            }
         }
      }

      for(DataSourceListingService service :
         Plugins.getInstance().getServices(DataSourceListingService.class, null))
      {
         for(final DataSourceListing listing : service.getDataSourceListings()) {
            if(listing.getName().equals(listingName)) {
               return service.getIcon(listing);
            }
         }
      }

      return null;
   }

   Logger LOG = LoggerFactory.getLogger(DataSourceListingService.class);
}
