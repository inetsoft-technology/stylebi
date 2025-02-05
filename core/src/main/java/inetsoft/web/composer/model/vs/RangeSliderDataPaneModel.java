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

import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.TreeNodeModel;

import java.util.List;

public class RangeSliderDataPaneModel {
   public String getSelectedTable() {
      return selectedTable;
   }

   public void setSelectedTable(String selectedTable) {
      this.selectedTable = selectedTable;
   }

   public boolean isAssemblySource() {
      return this.assemblySource;
   }

   public void setAssemblySource(boolean assemblySource) {
      this.assemblySource = assemblySource;
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

   public TreeNodeModel getCompositeTargetTree() {
      return compositeTargetTree;
   }

   public void setTargetTree(TreeNodeModel targetTree) {
      this.targetTree = targetTree;
   }

   public void setCompositeTargetTree(TreeNodeModel compositeTargetTree) {
      this.compositeTargetTree = compositeTargetTree;
   }

   public boolean isComposite() {
      return composite;
   }

   public void setComposite(boolean composite) {
      this.composite = composite;
   }

   public DataRefModel[] getGrayedOutFields() {
      return grayedOutFields;
   }

   public void setGrayedOutFields(DataRefModel[] grayedOutFields) {
      this.grayedOutFields = grayedOutFields;
   }

   private String selectedTable;
   private boolean assemblySource;
   private List<String> additionalTables;
   private OutputColumnRefModel[] selectedColumns = new OutputColumnRefModel[0];
   private TreeNodeModel targetTree;
   private TreeNodeModel compositeTargetTree;
   private boolean composite;
   private DataRefModel[] grayedOutFields;
}
