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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OutputColumnRefModel {
   public String getCaption() {
      return caption;
   }

   public void setCaption(String caption) {
      this.caption = caption;
   }

   public String getEntity() {
      return entity;
   }

   public void setEntity(String entity) {
      this.entity = entity;
   }

   public String getAttribute() {
      return attribute;
   }

   public void setAttribute(String attribute) {
      this.attribute = attribute;
   }

   public String getDataType() {
      return dataType;
   }

   public void setDataType(String dataType) {
      this.dataType = dataType;
   }

   public String getTable() {
      return table;
   }

   public void setTable(String table) {
      this.table = table;
   }

   public String getAlias() {
      return alias;
   }

   public void setAlias(String alias) {
      this.alias = alias;
   }

   public int getRefType() {
      return refType;
   }

   public void setRefType(int refType) {
      this.refType = refType;
   }

   public String getView() {
      return view;
   }

   public void setView(String view) {
      this.view = view;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public Map<String, String> getProperties() {
      return properties;
   }

   public void setProperties(Map<String, String> properties) {
      this.properties = properties;
   }

   public String getPath() {
      return path;
   }

   public void setPath(String path) {
      this.path = path;
   }

   /**
    * Set the description of the attribute.
    *
    * @param desc the specified attribute's description.
    */
   public void setDescription(String desc) {
      this.desc = desc;
   }

   /**
    * Get the description of the attribute.
    *
    * @return the attribute's description.
    */
   public String getDescription() {
      return desc == null ? "" : desc;
   }

   private String name;
   private String view;
   private String caption;
   private String entity;
   private String attribute;
   private String dataType;
   private String table;
   private String alias;
   private int refType;
   private Map<String, String> properties = new HashMap<>();
   private String path;
   private String desc = null;
}
