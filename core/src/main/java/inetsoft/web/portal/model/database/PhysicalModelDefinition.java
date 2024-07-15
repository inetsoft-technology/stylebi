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

import com.fasterxml.jackson.annotation.JsonIgnore;
import inetsoft.uql.util.DefaultMetaDataProvider;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class PhysicalModelDefinition {

   public PhysicalModelDefinition() {
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getFolder() {
      return folder;
   }

   public void setFolder(String folder) {
      this.folder = folder;
   }

   public List<PhysicalTableModel> getTables() {
      if(tables == null) {
         tables = new ArrayList<>();
      }

      return tables;
   }

   public void setTables(List<PhysicalTableModel> tables) {
      this.tables = tables;
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public DefaultMetaDataProvider getMetaData() {
      return metaData;
   }

   public void setMetaData(DefaultMetaDataProvider metaData) {
      this.metaData = metaData;
   }

   public String getConnection() {
      return connection;
   }

   @Nullable
   public void setConnection(String connection) {
      this.connection = connection;
   }

   @Override
   public String toString() {
      return "PhysicalModelDefinition{" +
         "name='" + name + '\'' +
         ", folder=" + folder +
         ", tables=" + tables +
         ", id='" + id + '\'' +
         ", description='" + description + '\'' +
         ", connection='" + connection + '\'' +
         '}';
   }

   private String name;
   private String folder;
   private List<PhysicalTableModel> tables;
   private String id;
   private String description;
   private String connection;
   @JsonIgnore
   private transient DefaultMetaDataProvider metaData;
}