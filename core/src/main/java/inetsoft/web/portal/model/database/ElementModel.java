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
package inetsoft.web.portal.model.database;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
   include = JsonTypeInfo.As.PROPERTY,
   use = JsonTypeInfo.Id.NAME,
   property = "elementType"
)
@JsonSubTypes({
   @JsonSubTypes.Type(value = XAttributeModel.class, name = "attributeElement"),
   @JsonSubTypes.Type(value = EntityModel.class, name = "entityElement")
})
public class ElementModel {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public String getErrorMessage() {
      return errorMessage;
   }

   public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
   }

   public boolean isLeaf() {
      return leaf;
   }

   public void setLeaf(boolean leaf) {
      this.leaf = leaf;
   }

   public boolean isBaseElement() {
      return baseElement;
   }

   public void setBaseElement(boolean baseElement) {
      this.baseElement = baseElement;
   }

   public String getOldName() {
      return this.oldName;
   }

   public void setOldName(String oname) {
      this.oldName = oname;
   }

   public boolean isVisible() {
      return visible;
   }

   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   private String name;
   private String description;
   private String type = "entity";
   private String errorMessage = null;
   private boolean leaf = false;
   private boolean baseElement;
   private String oldName;
   private boolean visible = true;
}
