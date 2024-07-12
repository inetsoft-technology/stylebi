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
package inetsoft.web.vswizard.event;

import inetsoft.web.vswizard.model.recommender.VSRecommendType;

public class ChangeVisualizationTypeEvent {
   /**
    * Set the target visualization type.
    */
   public void setType(VSRecommendType type) {
      this.type = type;
   }

   /**
    * Get the target visualization type.
    */
   public VSRecommendType getType() {
      return this.type;
   }

   /**
    * Set sub type index of the visualization type.
    */
   public void setSubTypeIndex(int subTypeIndex) {
      this.subTypeIndex = subTypeIndex;
   }

   /**
    * Get sub type index of visualization type.
    */
   public int getSubTypeIndex() {
      return this.subTypeIndex;
   }

   private VSRecommendType type;
   private int subTypeIndex;
}
