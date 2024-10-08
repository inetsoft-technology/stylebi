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
package inetsoft.web.portal.model.database.events;

import inetsoft.web.portal.model.database.PhysicalModelDefinition;

import javax.annotation.Nullable;

public class AddPhysicalModelEvent {
   public String getDatabase() {
      return database;
   }

   public void setDatabase(String database) {
      this.database = database;
   }

   public PhysicalModelDefinition getModel() {
      return model;
   }

   public void setModel(PhysicalModelDefinition model) {
      this.model = model;
   }

   @Nullable
   public String getParent() {
      return parent;
   }

   public void setParent(String parent) {
      this.parent = parent;
   }

   private String database;
   private PhysicalModelDefinition model;
   private String parent;
}
