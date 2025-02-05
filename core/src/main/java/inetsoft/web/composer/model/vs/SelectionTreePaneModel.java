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

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SelectionTreePaneModel {
   public SelectionMeasurePaneModel getSelectionMeasurePaneModel() {
      if(selectionMeasurePaneModel == null) {
         selectionMeasurePaneModel = new SelectionMeasurePaneModel();
      }

      return selectionMeasurePaneModel;
   }

   public void setSelectionMeasurePaneModel(
      SelectionMeasurePaneModel selectionMeasurePaneModel)
   {
      this.selectionMeasurePaneModel = selectionMeasurePaneModel;
   }

   public String getSelectedTable() {
      return selectedTable;
   }

   public void setSelectedTable(String selectedTable) {
      this.selectedTable = selectedTable;
   }

   public List<String> getAdditionalTables() {
      return additionalTables;
   }

   public void setAdditionalTables(List<String> additionalTables) {
      this.additionalTables = additionalTables;
   }

   public OutputColumnRefModel[] getSelectedColumns() {
      return selectedColumns;
   }

   public void setSelectedColumns(OutputColumnRefModel[] selectedColumns) {
      this.selectedColumns = selectedColumns;
   }

   public TreeNodeModel getTargetTree() {
      return targetTree;
   }

   public void setTargetTree(TreeNodeModel targetTree) {
      this.targetTree = targetTree;
   }

   public int getMode() {
      return mode;
   }

   public void setMode(int mode) {
      this.mode = mode;
   }

   public boolean isSelectChildren() {
      return selectChildren;
   }

   public void setSelectChildren(boolean selectChildren) {
      this.selectChildren = selectChildren;
   }

   public String getParentId() {
      return parentId;
   }

   public void setParentId(String parentId) {
      this.parentId = parentId;
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public OutputColumnRefModel getParentIdRef() {
      return parentIdRef;
   }

   public void setParentIdRef(OutputColumnRefModel parentIdRef) {
      this.parentIdRef = parentIdRef;
   }

   public OutputColumnRefModel getIdRef() {
      return idRef;
   }

   public void setIdRef(OutputColumnRefModel idRef) {
      this.idRef = idRef;
   }

   public OutputColumnRefModel getLabelRef() {
      return labelRef;
   }

   public void setLabelRef(OutputColumnRefModel labelRef) {
      this.labelRef = labelRef;
   }

   public DataRefModel[] getGrayedOutFields() {
      return grayedOutFields;
   }

   public void setGrayedOutFields(DataRefModel[] grayedOutFields) {
      this.grayedOutFields = grayedOutFields;
   }

   public void setModelSource(boolean isModel) {
      this.modelSource = isModel;
   }

   public boolean isModelSource() {
      return this.modelSource;
   }

   private SelectionMeasurePaneModel selectionMeasurePaneModel;
   private String selectedTable;
   private List<String> additionalTables;
   private OutputColumnRefModel[] selectedColumns = new OutputColumnRefModel[0];
   private TreeNodeModel targetTree;
   private int mode;
   private boolean modelSource = false;
   private boolean selectChildren;
   private String parentId;
   private String id;
   private String label;
   private OutputColumnRefModel parentIdRef;
   private OutputColumnRefModel idRef;
   private OutputColumnRefModel labelRef;
   private DataRefModel[] grayedOutFields;
}
