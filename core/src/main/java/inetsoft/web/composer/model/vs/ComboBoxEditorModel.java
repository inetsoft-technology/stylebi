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

import inetsoft.uql.viewsheet.ColumnOption;

public class ComboBoxEditorModel extends EditorModel {
   public ComboBoxEditorModel() {
      setType(ColumnOption.COMBOBOX);
   }

   public boolean isEmbedded() {
      return embedded;
   }

   public void setEmbedded(boolean embedded) {
      this.embedded = embedded;
   }

   public String getDataType() {
      return dataType;
   }

   public void setDataType(String dataType) {
      this.dataType = dataType;
   }

   public boolean isQuery() {
      return query;
   }

   public void setQuery(boolean query) {
      this.query = query;
   }

   public boolean isValid() {
      return valid;
   }

   public void setValid(boolean valid) {
      this.valid = valid;
   }

   public SelectionListDialogModel getSelectionListDialogModel() {
      if(selectionListDialogModel == null) {
         selectionListDialogModel = new SelectionListDialogModel();
      }

      return selectionListDialogModel;
   }

   public void setSelectionListDialogModel(SelectionListDialogModel selectionListDialogModel) {
      this.selectionListDialogModel = selectionListDialogModel;
   }

   public VariableListDialogModel getVariableListDialogModel() {
      if(variableListDialogModel == null) {
         variableListDialogModel = new VariableListDialogModel();
      }

      return variableListDialogModel;
   }

   public void setVariableListDialogModel(VariableListDialogModel variableListDialogModel) {
      this.variableListDialogModel = variableListDialogModel;
   }

   public boolean isCalendar() {
      return calendar;
   }

   public void setCalendar(boolean calendar) {
      this.calendar = calendar;
   }

   public boolean isServerTZ() {
      return serverTZ;
   }

   public void setServerTZ(boolean serverTZ) {
      this.serverTZ = serverTZ;
   }

   public void setMinDate(String minDate) {
      this.minDate = minDate;
   }

   public String getMinDate() {
      return this.minDate;
   }

   public void setMaxDate(String maxDate) {
      this.maxDate = maxDate;
   }

   public String getMaxDate() {
      return this.maxDate;
   }

   public boolean isNoDefault() {
      return noDefault;
   }

   public void setNoDefault(boolean noDefault) {
      this.noDefault = noDefault;
   }

   public String getDefaultValue() {
      return defaultValue;
   }

   public void setDefaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
   }

   private boolean embedded;
   private boolean query;
   private boolean valid = true;
   private String dataType;
   private SelectionListDialogModel selectionListDialogModel;
   private VariableListDialogModel variableListDialogModel;
   private boolean calendar;
   private boolean serverTZ;
   private String minDate;
   private String maxDate;
   private boolean noDefault;
   private String defaultValue;

}
