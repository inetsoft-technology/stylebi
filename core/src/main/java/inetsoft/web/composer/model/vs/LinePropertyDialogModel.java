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

/**
 * Data transfer object that represents the {@link LinePropertyDialogModel} for the
 * line property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LinePropertyDialogModel {
   public ShapeGeneralPaneModel getShapeGeneralPaneModel() {
      if(shapeGeneralPaneModel == null) {
         shapeGeneralPaneModel = new ShapeGeneralPaneModel();
      }

      return shapeGeneralPaneModel;
   }

   public void setShapeGeneralPaneModel(
      ShapeGeneralPaneModel shapeGeneralPaneModel)
   {
      this.shapeGeneralPaneModel = shapeGeneralPaneModel;
   }

   public LinePropertyPaneModel getLinePropertyPaneModel() {
      if(linePropertyPaneModel == null) {
         linePropertyPaneModel = new LinePropertyPaneModel();
      }

      return linePropertyPaneModel;
   }

   public void setLinePropertyPaneModel(
      LinePropertyPaneModel linePropertyPaneModel)
   {
      this.linePropertyPaneModel = linePropertyPaneModel;
   }

   public VSAssemblyScriptPaneModel getVsAssemblyScriptPaneModel() {
      return vsAssemblyScriptPaneModel;
   }

   public void setVsAssemblyScriptPaneModel(VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel) {
      this.vsAssemblyScriptPaneModel = vsAssemblyScriptPaneModel;
   }

   @Override
   public String toString() {
      return "LinePropertyDialogModel{" +
         " shapeGeneralPaneModel=" + shapeGeneralPaneModel +
         ", linePropertyPaneModel=" + linePropertyPaneModel +
         ", vsAssemblyScriptPaneModel=" + vsAssemblyScriptPaneModel +
         '}';
   }

   private ShapeGeneralPaneModel shapeGeneralPaneModel;
   private LinePropertyPaneModel linePropertyPaneModel;
   private VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel;
}
