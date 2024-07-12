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
package inetsoft.uql.viewsheet.graph;

import inetsoft.util.Tool;

/**
 * Data structure that encapsulates a map feature.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public class MapFeature {
   /**
    * Constructure.
    * @param name map feature name.
    * @param geoCode map feature geographic code.
    */
   public MapFeature(String name, String geoCode) {
      this.name = name;
      this.geoCode = geoCode;
   }

   /**
    * Get name.
    */
   public String getName() {
      return name;
   }

   /**
    * Get geographic code.
    */
   public String getGeoCode() {
      return geoCode;
   }

   /**
    * Generate a hashcode for this object.
    * @return a hashcode for this object.
    */
   public int hashCode() {
      int hash = 0;

      if(name != null) {
         hash += name.hashCode();
      }

      if(geoCode != null) {
         hash += geoCode.hashCode();
      }

      return hash;
   }

   /**
    * Check if equals another object.
    * @param obj the specified opject to compare.
    * @return <code>true</code> if equals, <code>false</code> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof MapFeature)) {
         return false;
      }

      MapFeature feature = (MapFeature) obj;

      return Tool.equals(name, feature.getName()) &&
         Tool.equals(geoCode, feature.getGeoCode());
   }

   /**
    * Returns a string representation of this map feature.
    */
   public String toString() {
      return "MapFeature[" + name + "," + geoCode + "]";
   }

   private String name;
   private String geoCode;
}