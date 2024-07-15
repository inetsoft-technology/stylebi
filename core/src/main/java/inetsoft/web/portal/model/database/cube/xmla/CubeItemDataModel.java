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

public class CubeItemDataModel {
   public String getUniqueName() {
      return uniqueName;
   }

   public void setUniqueName(String uniqueName) {
      this.uniqueName = uniqueName;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public String getCubeName() {
      return cubeName;
   }

   public void setCubeName(String cubeName) {
      this.cubeName = cubeName;
   }

   public boolean isHierarchy() {
      return hierarchy;
   }

   public void setHierarchy(boolean hierarchy) {
      this.hierarchy = hierarchy;
   }

   public boolean isUserDefined() {
      return userDefined;
   }

   public void setUserDefined(boolean userDefined) {
      this.userDefined = userDefined;
   }

   private String uniqueName;
   private String cubeName;
   private String type;
   private boolean hierarchy;
   private boolean userDefined;
}
