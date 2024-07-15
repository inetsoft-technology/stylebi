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

public class RangeSliderPropertyDialogModel {
   public RangeSliderGeneralPaneModel getRangeSliderGeneralPaneModel() {
      if(rangeSliderGeneralPaneModel == null) {
         rangeSliderGeneralPaneModel = new RangeSliderGeneralPaneModel();
      }

      return rangeSliderGeneralPaneModel;
   }

   public void setRangeSliderGeneralPaneModel(RangeSliderGeneralPaneModel rangeSliderGeneralPaneModel) {
      this.rangeSliderGeneralPaneModel = rangeSliderGeneralPaneModel;
   }

   public RangeSliderDataPaneModel getRangeSliderDataPaneModel() {
      if(rangeSliderDataPaneModel == null) {
         rangeSliderDataPaneModel = new RangeSliderDataPaneModel();
      }

      return rangeSliderDataPaneModel;
   }

   public void setRangeSliderDataPaneModel(RangeSliderDataPaneModel rangeSliderDataPaneModel) {
      this.rangeSliderDataPaneModel = rangeSliderDataPaneModel;
   }

   public RangeSliderAdvancedPaneModel getRangeSliderAdvancedPaneModel() {
      if(rangeSliderAdvancedPaneModel == null) {
         rangeSliderAdvancedPaneModel = new RangeSliderAdvancedPaneModel();
      }

      return rangeSliderAdvancedPaneModel;
   }

   public void setRangeSliderAdvancedPaneModel(RangeSliderAdvancedPaneModel rangeSliderAdvancedPaneModel) {
      this.rangeSliderAdvancedPaneModel = rangeSliderAdvancedPaneModel;
   }

   public VSAssemblyScriptPaneModel getVsAssemblyScriptPaneModel() {
      return vsAssemblyScriptPaneModel;
   }

   public void setVsAssemblyScriptPaneModel(
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel)
   {
      this.vsAssemblyScriptPaneModel = vsAssemblyScriptPaneModel;
   }

   private RangeSliderGeneralPaneModel rangeSliderGeneralPaneModel;
   private RangeSliderDataPaneModel rangeSliderDataPaneModel;
   private RangeSliderAdvancedPaneModel rangeSliderAdvancedPaneModel;
   private VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel;
}
