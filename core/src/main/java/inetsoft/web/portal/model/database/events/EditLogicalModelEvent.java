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
package inetsoft.web.portal.model.database.events;

import inetsoft.web.portal.model.database.LogicalModelDefinition;

import javax.annotation.Nullable;

public class EditLogicalModelEvent {
   public String getDatabase() {
      return database;
   }

   public void setDatabase(String database) {
      this.database = database;
   }

   public LogicalModelDefinition getModel() {
      return model;
   }

   public void setModel(LogicalModelDefinition model) {
      this.model = model;
   }

   public String getPhysicalModel() {
      return physicalModel;
   }

   public void setPhysicalModel(String physicalModel) {
      this.physicalModel = physicalModel;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getParent() {
      return parent;
   }

   @Nullable
   public void setParent(String parent) {
      this.parent = parent;
   }

   private String database;
   private LogicalModelDefinition model;
   public String physicalModel;
   private String name;
   private String parent;
}
