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

/**
 * Interface GeoRef.
 *
 * @version 10.2
 * @author InetSoft Technology Corp.
 */
public interface GeoRef extends ChartDimensionRef {
   public static final String PREFIX = "Geo(";

   public static String wrapGeoName(String name) {
      return PREFIX + name + ")";
   }

   public static String getBaseName(String name) {
      if(name != null && name.startsWith(PREFIX) && name.endsWith(")")) {
         return name.substring(PREFIX.length(), name.length() - 1);
      }

      return name;
   }

   /**
    * Get the geo graphic option.
    * @return the geo graphic option.
    */
   public GeographicOption getGeographicOption();

   /**
    * Set the geo graphic option.
    * @param option the geo graphic option.
    */
   public void setGeographicOption(GeographicOption option);
}