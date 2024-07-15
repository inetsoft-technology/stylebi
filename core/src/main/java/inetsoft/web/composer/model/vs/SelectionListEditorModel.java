/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SelectionListEditorModel {
   public String getTable() {
      return table;
   }

   public void setTable(String table) {
      this.table = table;
   }

   public String getColumn() {
      return column;
   }

   public void setColumn(String column) {
      this.column = column;
   }

   public String getValue() {
      return value;
   }

   public void setValue(String value) {
      this.value = value;
   }

   public String getDataType() {
      return dataType;
   }

   public void setDataType(String dataType) {
      this.dataType = dataType;
   }

   public String[] getTables() {
      return tables;
   }

   public void setTables(String[] tables) {
      this.tables = tables;
   }

   public String[] getLocalizedTables() {
      return localizedTables;
   }

   public void setLocalizedTables(String[] localizedTables) {
      this.localizedTables = localizedTables;
   }

   public String[] getLTableDescriptions() {
      return ltableDescriptions;
   }

   public void setLTablesDescription(String[] ltableDescriptions) {
      this.ltableDescriptions = ltableDescriptions;
   }

   public boolean isForm() {
      return form;
   }

   public void setForm(boolean form) {
      this.form = form;
   }

   private String table;
   private String column;
   private String value;
   private String dataType;
   private String[] tables;
   private String[] localizedTables;
   private String[] ltableDescriptions;
   private boolean form;
}