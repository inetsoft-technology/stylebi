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
package inetsoft.uql.viewsheet.internal;

import java.awt.*;

public abstract class MaxModeSelectionVSAssemblyInfo extends SelectionVSAssemblyInfo
   implements MaxModeSupportAssemblyInfo
{

   @Override
   public Dimension getMaxSize() {
      return maxSize;
   }

   @Override
   public void setMaxSize(Dimension maxSize) {
      this.maxSize = maxSize;
   }

   /**
    * @return the z-index value when in max mode
    */
   @Override
   public int getMaxModeZIndex() {
      return maxModeZIndex > 0 ? maxModeZIndex : getZIndex();
   }

   /**
    * Set the z-index value when in max mode
    */
   @Override
   public void setMaxModeZIndex(int maxModeZIndex) {
      this.maxModeZIndex = maxModeZIndex;
   }

   // max mode
   private Dimension maxSize = null;
   private int maxModeZIndex = -1;
}
