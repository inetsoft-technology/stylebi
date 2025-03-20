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

public class AssetListBrowseModel {
   public AssetItem[] getItems() {
      return items;
   }

   public void setItems(AssetItem[] items) {
      this.items = items;
   }

   public boolean isDeletable() {
      return deletable;
   }

   public void setDeletable(boolean deletable) {
      this.deletable = deletable;
   }

   public boolean isEditable() {
      return editable;
   }

   public void setEditable(boolean editable) {
      this.editable = editable;
   }

   public String[] getNames() {
      return names;
   }

   public void setNames(String[] names) {
      this.names = names;
   }

   public String getDateFormat() {
      return dateFormat;
   }

   public void setDateFormat(String format) {
      this.dateFormat = format;
   }

   public int getDbPartitionCount() {
      return dbPartitionCount;
   }

   public void setDbPartitionCount(int dbPartitionCount) {
      this.dbPartitionCount = dbPartitionCount;
   }

   private boolean editable;
   private boolean deletable;
   private AssetItem[] items;
   private String[] names;
   private int dbPartitionCount;
   private String dateFormat;
}
