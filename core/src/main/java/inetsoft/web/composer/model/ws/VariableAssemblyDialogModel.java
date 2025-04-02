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

import com.fasterxml.jackson.annotation.*;
import inetsoft.web.composer.model.condition.ExpressionValueModel;
import inetsoft.web.composer.model.vs.VariableListDialogModel;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

/**
 * Data transfer object that represents the {@link VariableAssemblyDialogModel} for the
 * variable assembly dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VariableAssemblyDialogModel implements Serializable {
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

   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      getVariableListDialogModel().setDataType(type);
      this.type = type;
   }

   public Object getDefaultValue() {
      return defaultValue;
   }

   public void setDefaultValue(Object defaultValue) {
      this.defaultValue = defaultValue;
   }

   public String getSelectionList() {
      return selectionList;
   }

   public void setSelectionList(String selectionList) {
      this.selectionList = selectionList;
   }

   public int getDisplayStyle() {
      return displayStyle;
   }

   public void setDisplayStyle(int displayStyle) {
      this.displayStyle = displayStyle;
   }

   public boolean isNone() {
      return none;
   }

   public void setNone(boolean none) {
      this.none = none;
   }

   public VariableListDialogModel getVariableListDialogModel() {
      if(variableListDialogModel == null) {
         variableListDialogModel = new VariableListDialogModel();
      }

      return variableListDialogModel;
   }

   public void setVariableListDialogModel(
      VariableListDialogModel variableListDialogModel)
   {
      this.variableListDialogModel = variableListDialogModel;
   }

   public VariableTableListDialogModel getVariableTableListDialogModel() {
      if(variableTableListDialogModel == null) {
         variableTableListDialogModel = new VariableTableListDialogModel();
      }

      return variableTableListDialogModel;
   }

   public void setVariableTableListDialogModel(
      VariableTableListDialogModel variableTableListDialogModel)
   {
      this.variableTableListDialogModel = variableTableListDialogModel;
   }

   public List<String> getOtherVariables() {
      return otherVariables;
   }

   @Nullable
   public void setOtherVariables(List<String> otherVariables) {
      this.otherVariables = otherVariables;
   }

   private String newName;
   private String oldName;
   private String label;
   private String type;
   @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "jsonType")
   @JsonSubTypes({ @JsonSubTypes.Type(value = ExpressionValueModel.class, name = "expression")})
   private Object defaultValue;
   private String selectionList;
   private int displayStyle;
   private boolean none;
   private VariableListDialogModel variableListDialogModel;
   private VariableTableListDialogModel variableTableListDialogModel;
   private List<String> otherVariables;
}
