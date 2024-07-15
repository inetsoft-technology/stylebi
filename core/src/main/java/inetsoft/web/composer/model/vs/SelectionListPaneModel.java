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
public class SelectionListPaneModel {
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

   public OutputColumnRefModel getSelectedColumn() {
      return selectedColumn;
   }

   public void setSelectedColumn(OutputColumnRefModel selectedColumn) {
      this.selectedColumn = selectedColumn;
   }

   public TreeNodeModel getTargetTree() {
      return targetTree;
   }

   public void setTargetTree(TreeNodeModel targetTree) {
      this.targetTree = targetTree;
   }

   public DataRefModel[] getGrayedOutFields() {
      return grayedOutFields;
   }

   public void setGrayedOutFields(DataRefModel[] grayedOutFields) {
      this.grayedOutFields = grayedOutFields;
   }

   private SelectionMeasurePaneModel selectionMeasurePaneModel;
   private String selectedTable;
   private List<String> additionalTables;
   private OutputColumnRefModel selectedColumn;
   private TreeNodeModel targetTree;
   private DataRefModel[] grayedOutFields;
}
