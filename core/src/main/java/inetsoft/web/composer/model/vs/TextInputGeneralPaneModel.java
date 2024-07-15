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

@JsonIgnoreProperties(ignoreUnknown = true)
public class TextInputGeneralPaneModel {
   public GeneralPropPaneModel getGeneralPropPaneModel() {
      if(generalPropPaneModel == null) {
         generalPropPaneModel = new GeneralPropPaneModel();
         generalPropPaneModel.getBasicGeneralPaneModel().setShowRefreshCheckbox(true);
      }

      return generalPropPaneModel;
   }

   public void setGeneralPropPaneModel(
      GeneralPropPaneModel generalPropPaneModel)
   {
      this.generalPropPaneModel = generalPropPaneModel;
   }

   public SizePositionPaneModel getSizePositionPaneModel() {
      if(sizePositionPaneModel == null) {
         sizePositionPaneModel = new SizePositionPaneModel();
      }

      return sizePositionPaneModel;
   }

   public void setSizePositionPaneModel(SizePositionPaneModel sizePositionPaneModel) {
      this.sizePositionPaneModel = sizePositionPaneModel;
   }

   public String getToolTip() {
      return toolTip;
   }

   public void setToolTip(String toolTip) {
      this.toolTip = toolTip;
   }

   public String getDefaultText() {
      return defaultText;
   }

   public void setDefaultText(String defaultText) {
      this.defaultText = defaultText;
   }

   public boolean isInsetStyle() {
      return insetStyle;
   }

   public void setInsetStyle(boolean insetStyle) {
      this.insetStyle = insetStyle;
   }

   public boolean isMultiLine() {
      return multiLine;
   }

   public void setMultiLine(boolean multiLine) {
      this.multiLine = multiLine;
   }

   private GeneralPropPaneModel generalPropPaneModel;
   private SizePositionPaneModel sizePositionPaneModel;
   private String toolTip;
   private String defaultText;
   private boolean insetStyle;
   private boolean multiLine;
}
