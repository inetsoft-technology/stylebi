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
package inetsoft.web.binding.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.annotation.Nullable;

/**
 * Data transfer object that represents the {@link ColorMappingDialogModel} for the
 * Color Mapping dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ColorMappingDialogModel {
   public ColorMappingDialogModel() {
   }

   public ColorMappingDialogModel(ColorMapModel[] colorMaps, ValueLabelModel[] dimensionData) {
      this.colorMaps = colorMaps;
      this.dimensionData = dimensionData;
   }

   public ColorMapModel[] getColorMaps() {
      return this.colorMaps;
   }

   public void setColorMaps(ColorMapModel[] colorMaps) {
      this.colorMaps = colorMaps;
   }

   public ValueLabelModel[] getDimensionData() {
      return this.dimensionData;
   }

   public void setDimensionData(ValueLabelModel[] dimensionData) {
      this.dimensionData = dimensionData;
   }

   public ColorMappingDialogModel getGlobalModel() {
      return globalModel;
   }

   public void setGlobalModel(ColorMappingDialogModel globalModel) {
      this.globalModel = globalModel;
   }

   public boolean isUseGlobal() {
      return useGlobal;
   }

   public void setUseGlobal(boolean useGlobal) {
      this.useGlobal = useGlobal;
   }

   public boolean isShareColors() {
      return shareColors;
   }

   public void setShareColors(boolean shareColors) {
      this.shareColors = shareColors;
   }

   public String getBrowseDataErrorMsg() {
      return browseDataErrorMsg;
   }

   @Nullable
   public void setBrowseDataErrorMsg(String browseDataErrorMsg) {
      this.browseDataErrorMsg = browseDataErrorMsg;
   }

   private ColorMapModel[] colorMaps;
   private ValueLabelModel[] dimensionData;
   private ColorMappingDialogModel globalModel;
   private boolean useGlobal = true;
   private boolean shareColors = true;
   private String browseDataErrorMsg;
}
