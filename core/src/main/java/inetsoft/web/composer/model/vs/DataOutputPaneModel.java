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
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.TreeNodeModel;

/**
 * Data transfer object that represents the {@link TextPropertyDialogModel} for the
 * text property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataOutputPaneModel {
   public String getColumn() {
      return column;
   }

   public void setColumn(String column) {
      this.column = column;
   }

   public String getAggregate() {
      return aggregate;
   }

   public void setAggregate(String aggregate) {
      this.aggregate = aggregate;
   }

   public String getNum() {
      try {
         int n = Integer.parseInt(num);
         return Math.max(1, n) + "";
      }
      catch(Exception ex) {
         return num;
      }
   }

   public void setNum(String num) {
      this.num = num;
   }

   public String getWith() {
      return with;
   }

   public void setWith(String with) {
      this.with = with;
   }

   public String getTable() {
      return table;
   }

   public void setTable(String table) {
      this.table = table;
   }

   public String getColumnType() {
      return columnType;
   }

   public void setColumnType(String columnType) {
      this.columnType = columnType;
   }

   public int getMagnitude() {
      return magnitude;
   }

   public void setMagnitude(int magnitude) {
      this.magnitude = magnitude;
   }

   public TreeNodeModel getTargetTree() {
      return targetTree;
   }

   public void setTargetTree(TreeNodeModel targetTree) {
      this.targetTree = targetTree;
   }

   public boolean isLogicalModel() {
      return model;
   }

   public void setLogicalModel(boolean model) {
      this.model = model;
   }

   public String getTableType() {
      return tableType;
   }

   public void setTableType(String tableType) {
      this.tableType = tableType;
   }

   public DataRefModel[] getGrayedOutFields() {
      return grayedOutFields;
   }

   public void setGrayedOutFields(DataRefModel[] grayedOutFields) {
      this.grayedOutFields = grayedOutFields;
   }

   @Override
   public String toString() {
      return "DataOutputPaneModel{}";
   }

   private String column;
   private String aggregate;
   private String with;
   private String table;
   private String tableType;
   private String columnType;
   private String num = "1";
   private int magnitude = 1;
   private TreeNodeModel targetTree;
   private boolean model;
   private DataRefModel[] grayedOutFields;
}
