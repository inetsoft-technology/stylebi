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
package inetsoft.web.composer.ws.event;

import inetsoft.web.binding.drm.ColumnRefModel;

public class WSResizeColumnEvent {

   public String getTableName() {
      return tableName;
   }

   public void setTableName(String tableName) {
      this.tableName = tableName;
   }

   public ColumnRefModel getColumnRef() {
      return columnRef;
   }

   public void setColumnRef(ColumnRefModel columnRef) {
      this.columnRef = columnRef;
   }

   public int getWidth() {
      return width;
   }

   public void setWidth(int width) {
      this.width = width;
   }

   private String tableName;
   private ColumnRefModel columnRef;
   private int width;
}
