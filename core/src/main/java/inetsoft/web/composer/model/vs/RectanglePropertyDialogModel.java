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

/**
 * Data transfer object that represents the {@link RectanglePropertyDialogModel} for the
 * rect property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RectanglePropertyDialogModel {
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

   public RectanglePropertyPaneModel getRectanglePropertyPaneModel() {
      if(rectanglePropertyPaneModel == null) {
         rectanglePropertyPaneModel = new RectanglePropertyPaneModel();
      }

      return rectanglePropertyPaneModel;
   }

   public void setRectanglePropertyPaneModel(
      RectanglePropertyPaneModel rectanglePropertyPaneModel)
   {
      this.rectanglePropertyPaneModel = rectanglePropertyPaneModel;
   }

   public VSAssemblyScriptPaneModel getVsAssemblyScriptPaneModel() {
      return vsAssemblyScriptPaneModel;
   }

   public void setVsAssemblyScriptPaneModel(VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel) {
      this.vsAssemblyScriptPaneModel = vsAssemblyScriptPaneModel;
   }

   @Override
   public String toString() {
      return "RectanglePropertyDialogModel{" +
         " shapeGeneralPaneModel=" + shapeGeneralPaneModel +
         ", rectanglePropertyPaneModel=" + rectanglePropertyPaneModel +
         ", vsAssemblyScriptPaneModel=" + vsAssemblyScriptPaneModel +
         '}';
   }

   private ShapeGeneralPaneModel shapeGeneralPaneModel;
   private RectanglePropertyPaneModel rectanglePropertyPaneModel;
   private VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel;
}
