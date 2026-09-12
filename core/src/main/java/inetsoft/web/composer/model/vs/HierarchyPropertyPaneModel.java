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

import java.io.Serializable;

public class HierarchyPropertyPaneModel implements Serializable {
   public HierarchyEditorModel getHierarchyEditorModel() {
      if(hierarchyEditorModel == null) {
         hierarchyEditorModel = new HierarchyEditorModel();
      }

      return hierarchyEditorModel;
   }

   public void setHierarchyEditorModel(HierarchyEditorModel hierarchyEditorModel) {
      this.hierarchyEditorModel = hierarchyEditorModel;
   }

   public boolean isCube() {
      return isCube;
   }

   public void setCube(boolean cube) {
      isCube = cube;
   }

   public OutputColumnRefModel[] getColumnList() {
      return columnList;
   }

   public void setColumnList(OutputColumnRefModel[] columnList) {
      this.columnList = columnList;
   }

   public VSDimensionModel[] getDimensions() {
      return dimensions;
   }

   public void setDimensions(VSDimensionModel[] dimensions) {
      this.dimensions = dimensions;
   }

   public DataRefModel[] getGrayedOutFields() {
      return grayedOutFields;
   }

   public void setGrayedOutFields(DataRefModel[] grayedOutFields) {
      this.grayedOutFields = grayedOutFields;
   }

   private HierarchyEditorModel hierarchyEditorModel;
   private boolean isCube;
   private OutputColumnRefModel[] columnList;
   private VSDimensionModel[] dimensions;
   private DataRefModel[] grayedOutFields;
}
