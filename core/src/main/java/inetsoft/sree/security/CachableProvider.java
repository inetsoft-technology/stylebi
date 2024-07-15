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
package inetsoft.sree.security;

public interface CachableProvider {
   /**
    * Determines if caching is enabled for this provider.
    *
    * @return {@code true} if caching is enabled; {@code false} if disabled.
    */
   default boolean isCacheEnabled() {
      return false;
   }

   /**
    * Clear the cached data.
    */
   default void clearCache() {
   }

   /**
    * Determines if the local cache data is currently being loaded.
    *
    * @return {@code true} if the cache data is loading; {@code false} otherwise.
    */
   default boolean isLoading() {
      return false;
   }

   /**
    * Gets the age of the cached data.
    *
    * @return the cache age in milliseconds.
    */
   default long getCacheAge() {
      return 0L;
   }
}
