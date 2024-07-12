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
package inetsoft.util;

import com.github.zafarkhaja.semver.Version;

/**
 * Enumeration that provides the current version of the plugin extension APIs.
 */
enum ApiVersion {
   /**
    * The materialized view extension API.
    */
   MV("inetsoft.mv", "13.3.0"),

   /**
    * The tabular data source extension API.
    */
   TABULAR("inetsoft.uql.tabular", "13.3.0");

   private final String id;
   private final Version version;

   ApiVersion(String id, String version) {
      this.id = id;
      this.version = Version.valueOf(version);
   }

   /**
    * Determines if this API version satisfies the specified version requirement.
    *
    * @param requirement a valid semantic version expression.
    *
    * @return <tt>true</tt> if this version satisfies the requirement;
    *         <tt>false</tt> otherwise.
    */
   boolean satisfies(String requirement) {
      return version.satisfies(requirement);
   }

   /**
    * Gets the API version with the specified identifier.
    *
    * @param id the API identifier.
    *
    * @return the API version.
    */
   static ApiVersion forId(String id) {
      ApiVersion version = null;

      for(ApiVersion v : values()) {
         if(v.id.equals(id)) {
            version = v;
            break;
         }
      }

      if(version == null) {
         throw new IllegalArgumentException("Invalid API identifier: " + id);
      }

      return version;
   }
}
