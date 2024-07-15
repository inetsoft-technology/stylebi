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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.web.composer.model.TreeNodeModel;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataInputPaneModel {
   public String getTable() {
      return table;
   }

   public void setTable(String table) {
      this.table = table;
   }

   public String getTableLabel() {
      return tableLabel;
   }

   public void setTableLabel(String tableLabel) {
      this.tableLabel = tableLabel;
   }

   public String getRowValue() {
      return rowValue;
   }

   public void setRowValue(String rowValue) {
      this.rowValue = rowValue;
   }

   public String getColumnValue() {
      return columnValue;
   }

   public void setColumnValue(String columnValue) {
      this.columnValue = columnValue;
   }

   public String getDefaultValue() {
      return defaultValue;
   }

   public void setDefaultValue(String val) {
      this.defaultValue = val;
   }

   public TreeNodeModel getTargetTree() {
      return targetTree;
   }

   public void setTargetTree(TreeNodeModel targetTree) {
      this.targetTree = targetTree;
   }

   public boolean isVariable() {
      return variable;
   }

   public void setVariable(boolean variable) {
      this.variable = variable;
   }

   public boolean isWriteBackDirectly() {
      return writeBackDirectly;
   }

   public void setWriteBackDirectly(boolean writeBackDirectly) {
      this.writeBackDirectly = writeBackDirectly;
   }

   private String table;
   private String tableLabel;
   private String rowValue;
   private String columnValue;
   private String defaultValue;
   private TreeNodeModel targetTree;
   private boolean variable;
   private boolean writeBackDirectly;
}
