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

import inetsoft.web.portal.model.database.QueryTableModel;

import java.util.List;

public class EditQueryTableEvent {
   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getOldName() {
      return oldName;
   }

   public void setOldName(String oldName) {
      this.oldName = oldName;
   }

   public List<QueryTableModel> getTables() {
      return tables;
   }

   public void setTables(List<QueryTableModel> tables) {
      this.tables = tables;
   }

   private String id;
   private String oldName;
   private List<QueryTableModel> tables;
}
