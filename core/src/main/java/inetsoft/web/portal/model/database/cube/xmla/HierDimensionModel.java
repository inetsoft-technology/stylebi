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

public class HierDimensionModel extends CubeDimensionModel {
   public String getHierarchyName() {
      return hierarchyName;
   }

   public void setHierarchyName(String hierarchyName) {
      this.hierarchyName = hierarchyName;
   }

   public String getHierarchyUniqueName() {
      return hierarchyUniqueName;
   }

   public void setHierarchyUniqueName(String hierarchyUniqueName) {
      this.hierarchyUniqueName = hierarchyUniqueName;
   }

   public String getHierCaption() {
      return hierCaption;
   }

   public void setHierCaption(String hierCaption) {
      this.hierCaption = hierCaption;
   }

   public String getParentCaption() {
      return parentCaption;
   }

   public void setParentCaption(String parentCaption) {
      this.parentCaption = parentCaption;
   }

   public boolean isUserDefined() {
      return userDefined;
   }

   public void setUserDefined(boolean userDefined) {
      this.userDefined = userDefined;
   }

   private String hierarchyName = null;
   private String hierarchyUniqueName = null;
   private String hierCaption = null;
   private String parentCaption = null;
   private boolean userDefined = false;
}
