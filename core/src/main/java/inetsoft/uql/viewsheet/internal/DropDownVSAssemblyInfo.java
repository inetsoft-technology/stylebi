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
package inetsoft.uql.viewsheet.internal;

/**
 * DropDownVSAssemblyInfo, the assembly info of a drop down assembly.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public interface DropDownVSAssemblyInfo {
   /**
    * Get the list height.
    * @return the list height.
    */
   int getListHeight();

   /**
    * Set the list height.
    * @param height the list height.
    */
   void setListHeight(int height);

   /**
    * Gets the scaling ratio used for the list height.
    *
    * @return the list height ratio.
    */
   double getListHeightScale();

   /**
    * Sets the scaling ratio used for the list height.
    *
    * @param listHeightScale the list height ratio.
    */
   void setListHeightScale(double listHeightScale);
}
