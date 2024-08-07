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
package inetsoft.web.portal.model.database;

import java.util.List;

public class AutoJoinColumnsModel {
   public List<AutoJoinNameColumnModel> getNameColumns() {
      return nameColumns;
   }

   public void setNameColumns(List<AutoJoinNameColumnModel> nameColumns) {
      this.nameColumns = nameColumns;
   }

   public List<AutoJoinMetaColumnModel> getMetaColumns() {
      return metaColumns;
   }

   public void setMetaColumns(List<AutoJoinMetaColumnModel> metaColumns) {
      this.metaColumns = metaColumns;
   }

   public boolean isMetaAvailable() {
      return metaAvailable;
   }

   public void setMetaAvailable(boolean metaAvailable) {
      this.metaAvailable = metaAvailable;
   }

   private List<AutoJoinNameColumnModel> nameColumns;
   private List<AutoJoinMetaColumnModel> metaColumns;
   private boolean metaAvailable;
}