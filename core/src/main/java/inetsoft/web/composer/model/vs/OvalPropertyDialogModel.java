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

import java.io.Serializable;

/**
 * Data transfer object that represents the {@link OvalPropertyDialogModel} for the
 * oval property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OvalPropertyDialogModel implements Serializable {
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

   public OvalPropertyPaneModel getOvalPropertyPaneModel() {
      if(ovalPropertyPaneModel == null) {
         ovalPropertyPaneModel = new OvalPropertyPaneModel();
      }

      return ovalPropertyPaneModel;
   }

   public void setOvalPropertyPaneModel(
      OvalPropertyPaneModel ovalPropertyPaneModel)
   {
      this.ovalPropertyPaneModel = ovalPropertyPaneModel;
   }

   public VSAssemblyScriptPaneModel getVsAssemblyScriptPaneModel() {
      return vsAssemblyScriptPaneModel;
   }

   public void setVsAssemblyScriptPaneModel(VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel) {
      this.vsAssemblyScriptPaneModel = vsAssemblyScriptPaneModel;
   }

   @Override
   public String toString() {
      return "OvalPropertyDialogModel{" +
         "shapeGeneralPaneModel=" + shapeGeneralPaneModel +
         ", ovalPropertyPaneModel=" + ovalPropertyPaneModel +
         ", vsAssemblyScriptPaneModel=" + vsAssemblyScriptPaneModel +
         '}';
   }

   private ShapeGeneralPaneModel shapeGeneralPaneModel;
   private OvalPropertyPaneModel ovalPropertyPaneModel;
   private VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel;
}
