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
 * Data transfer object that represents the {@link SliderPropertyDialogModel} for the
 * textinput property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SliderPropertyDialogModel implements Serializable {
   public DataInputPaneModel getDataInputPaneModel() {
      if(dataInputPaneModel == null) {
         dataInputPaneModel = new DataInputPaneModel();
      }

      return dataInputPaneModel;
   }

   public void setDataInputPaneModel(
      DataInputPaneModel dataInputPaneModel)
   {
      this.dataInputPaneModel = dataInputPaneModel;
   }

   public VSAssemblyScriptPaneModel getVsAssemblyScriptPaneModel() {
      return vsAssemblyScriptPaneModel;
   }

   public void setVsAssemblyScriptPaneModel(VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel) {
      this.vsAssemblyScriptPaneModel = vsAssemblyScriptPaneModel;
   }

   public SliderGeneralPaneModel getSliderGeneralPaneModel() {
      if(sliderGeneralPaneModel == null) {
         sliderGeneralPaneModel = new SliderGeneralPaneModel();
      }

      return sliderGeneralPaneModel;
   }

   public void setSliderGeneralPaneModel(
      SliderGeneralPaneModel sliderGeneralPaneModel)
   {
      this.sliderGeneralPaneModel = sliderGeneralPaneModel;
   }

   public SliderAdvancedPaneModel getSliderAdvancedPaneModel() {
      if(sliderAdvancedPaneModel == null) {
         sliderAdvancedPaneModel = new SliderAdvancedPaneModel();
      }

      return sliderAdvancedPaneModel;
   }

   public void setSliderAdvancedPaneModel(SliderAdvancedPaneModel sliderAdvancedPaneModel) {
      this.sliderAdvancedPaneModel = sliderAdvancedPaneModel;
   }

   private SliderGeneralPaneModel sliderGeneralPaneModel;
   private DataInputPaneModel dataInputPaneModel;
   private SliderAdvancedPaneModel sliderAdvancedPaneModel;
   private VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel;
}