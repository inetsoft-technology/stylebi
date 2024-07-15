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
package inetsoft.web.composer.model.ws;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TablePropertyDialogModel {

   public static int convertRows(Integer row) {
      if(row == null) {
         return -1;
      }

      return row;
   }

   public String getNewName() {
      return newName;
   }

   public void setNewName(String newName) {
      this.newName = newName;
   }

   public String getOldName() {
      return oldName;
   }

   public void setOldName(String oldName) {
      this.oldName = oldName;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public boolean getVisibleInViewsheet() {
      return visibleInViewsheet;
   }

   public void setVisibleInViewsheet(boolean visibleInViewsheet) {
      this.visibleInViewsheet = visibleInViewsheet;
   }

   public Integer getMaxRows() {
      return maxRows;
   }

   public void setMaxRows(Integer maxRows) {
      if(maxRows != null && maxRows > 0) {
         this.maxRows = maxRows;
      }
      else {
         this.maxRows = null;
      }
   }

   public boolean getReturnDistinctValues() {
      return returnDistinctValues;
   }

   public void setReturnDistinctValues(boolean returnDistinctValues) {
      this.returnDistinctValues = returnDistinctValues;
   }

   public boolean getMergeSql() {
      return mergeSql;
   }

   public void setMergeSql(boolean mergeSql) {
      this.mergeSql = mergeSql;
   }

   public boolean getSourceMergeable() {
      return sourceMergeable;
   }

   public void setSourceMergeable(boolean sourceMergeable) {
      this.sourceMergeable = sourceMergeable;
   }

   public Integer getRowCount() {
      return rowCount;
   }

   public void setRowCount(int rowCount) {
      if(rowCount > 0) {
         this.rowCount = rowCount;
      }
      else {
         this.rowCount = null;
      }
   }

   private String newName;
   private String oldName;
   private String description;
   private boolean visibleInViewsheet;
   private Integer maxRows;
   private boolean returnDistinctValues;
   private boolean mergeSql;
   private boolean sourceMergeable;

   // Only exists for embedded table assemblies
   private Integer rowCount;
}
