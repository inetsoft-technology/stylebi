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
}
