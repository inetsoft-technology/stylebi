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

import inetsoft.web.portal.model.database.PhysicalModelTreeNodeModel;
import inetsoft.web.portal.model.database.PhysicalTableModel;

import javax.annotation.Nullable;

public class EditTableEvent {
   public PhysicalTableModel getTable() {
      return table;
   }

   public void setTable(PhysicalTableModel table) {
      this.table = table;
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getOldName() {
      return oldName;
   }

   @Nullable
   public void setOldName(String oldName) {
      this.oldName = oldName;
   }

   public PhysicalModelTreeNodeModel getNode() {
      return node;
   }

   public void setNode(PhysicalModelTreeNodeModel node) {
      this.node = node;
   }

   private PhysicalTableModel table;
   private String id;
   private String oldName;
   private PhysicalModelTreeNodeModel node; // for add table.
}
