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
package inetsoft.web.binding.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.table.*;

import java.util.List;

@JsonTypeInfo(
   use = JsonTypeInfo.Id.NAME,
   visible = true,
   property = "type")
@JsonSubTypes({
   @JsonSubTypes.Type(value = ChartBindingModel.class, name = "chart"),
   @JsonSubTypes.Type(value = TableBindingModel.class, name = "table"),
   @JsonSubTypes.Type(value = CrosstabBindingModel.class, name = "crosstab"),
   @JsonSubTypes.Type(value = CalcTableBindingModel.class, name = "calctable")
})
public class BindingModel {
   /**
    * Set source
    */
   public void setSource(SourceInfo source) {
      this.source = source;
   }

   /**
    * Get source.
    */
   public SourceInfo getSource() {
      return source;
   }

   /**
    * Get availableFields.
    */
   public List<DataRefModel> getAvailableFields() {
      return availableFields;
   }

   /**
    * Set availableFields
    */
   public void setAvailableFields(List<DataRefModel> availableFields) {
      this.availableFields = availableFields;
   }

   /**
    * Check is sql mergeable or not.
    * @return true if sql mergeable.
    */
   public boolean isSqlMergeable() {
      return sqlMergeable;
   }

   /**
    * Set the sql mergeable.
    */
   public void setSqlMergeable(boolean merge) {
      sqlMergeable = merge;
   }

   /**
    * Get type.
    */
   public String getType() {
      return type;
   }

   /**
    * Set type.
    */
   public void setType(String type) {
      this.type = type;
   }

   public void setTables(List<SourceTable> tables) {
      this.tables = tables;
   }

   public List<SourceTable> getTables() {
      return tables;
   }

   public class SourceTable {
      public SourceTable() {
      }

      public String getName() {
         return name;
      }

      public void setName(String name) {
         this.name = name;
      }

      public List<SourceTableColumn> getColumns() {
         return columns;
      }

      /**
       * Set type.
       */
      public void setColumns(List<SourceTableColumn> columns) {
         this.columns = columns;
      }

      private String name;
      private List<SourceTableColumn> columns;
   }

   public class SourceTableColumn {
      public SourceTableColumn(String name, String type) {
         this.name = name;
         this.dataType = type;
      }

      public String getName() {
         return name;
      }

      public void setName(String name) {
         this.name = name;
      }

      public String getDescription() {
         return description;
      }

      public void setDescription(String description) {
         this.description = description;
      }

      public String getDataType() {
         return dataType;
      }

      public void setDataType(String type) {
         this.dataType = type;
      }

      private String name;
      private String dataType;
      private String description;
   }

   private SourceInfo source;
   private List<DataRefModel> availableFields;
   private List<SourceTable> tables;
   private boolean sqlMergeable = true;
   private String type;
}
