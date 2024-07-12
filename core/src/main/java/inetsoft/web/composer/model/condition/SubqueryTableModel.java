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
package inetsoft.web.composer.model.condition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.web.binding.drm.DataRefModel;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SubqueryTableModel {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String desc) {
      this.description = desc;
   }

   public DataRefModel[] getColumns() {
      return columns;
   }

   public void setColumns(DataRefModel[] columns) {
      this.columns = columns;
   }

   public boolean isCurrentTable() {
      return currentTable;
   }

   public void setCurrentTable(boolean currentTable) {
      this.currentTable = currentTable;
   }

   private String name;
   private String description;
   private DataRefModel[] columns;
   private boolean currentTable;
}
