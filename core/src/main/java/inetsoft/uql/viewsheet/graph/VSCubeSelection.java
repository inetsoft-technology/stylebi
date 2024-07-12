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
 * VSCubeSelection stores multiple VSPoints for cube data.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class VSCubeSelection extends VSSelection {
   /**
    * Constructor.
    */
   public VSCubeSelection() {
      super();
   }

   /**
    * Check if equals another objects.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof VSCubeSelection)) {
         return false;
      }

      return super.equals(obj);
   }

   /**
    * Check if is a logical range.
    */
   @Override
   public boolean isLogicalRange() {
      return false;
   }

   /**
    * Check if is a physical range.
    */
   @Override
   public boolean isPhysicalRange() {
      return false;
   }

   /**
    * Get the range option.
    */
   @Override
   public int getRange() {
      return NONE_RANGE;
   }
}