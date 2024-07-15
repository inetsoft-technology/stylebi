/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.model.database.cube.xmla;

import inetsoft.web.portal.model.database.cube.XDimensionModel;

public class CubeDimensionModel extends XDimensionModel {
   public CubeDimensionModel() {
   }

   public String getDimensionName() {
      return dimensionName;
   }

   public void setDimensionName(String dimensionName) {
      this.dimensionName = dimensionName;
   }

   public String getUniqueName() {
      return uniqueName;
   }

   public void setUniqueName(String uniqueName) {
      this.uniqueName = uniqueName;
   }

   public String getCaption() {
      return caption;
   }

   public void setCaption(String caption) {
      this.caption = caption;
   }

   public boolean isOriginalOrder() {
      return originalOrder;
   }

   public void setOriginalOrder(boolean originalOrder) {
      this.originalOrder = originalOrder;
   }

   private String dimensionName = null;
   private String uniqueName = null;
   private String caption = null;
   private boolean originalOrder;
}
