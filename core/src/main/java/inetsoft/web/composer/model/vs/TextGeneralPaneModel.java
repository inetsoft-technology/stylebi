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
import inetsoft.uql.viewsheet.internal.PopVSAssemblyInfo.PopLocation;

import java.io.Serializable;

/**
 * Data transfer object that represents the {@link TextPropertyDialogModel} for the
 * text property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TextGeneralPaneModel implements Serializable {
   public String getPopComponent() {
      return popComponent;
   }

   public void setPopComponent(String popComponent) {
      this.popComponent = popComponent;
   }

   public PopLocation getPopLocation() { return popLocation;}

   public void setPopLocation(PopLocation popLocation) {this.popLocation = popLocation;}

   public String[] getPopComponents() {
      return popComponents;
   }

   public void setPopComponents(String[] popComponents) {
      this.popComponents = popComponents;
   }

   public String getAlpha() {
      return alpha;
   }

   public void setAlpha(String alpha) {
      this.alpha = alpha;
   }

   public OutputGeneralPaneModel getOutputGeneralPaneModel() {
      if(outputGeneralPaneModel == null) {
         outputGeneralPaneModel = new OutputGeneralPaneModel();
      }

      return outputGeneralPaneModel;
   }

   public void setOutputGeneralPaneModel(
      OutputGeneralPaneModel outputGeneralPaneModel)
   {
      this.outputGeneralPaneModel = outputGeneralPaneModel;
   }

   public TextPaneModel getTextPaneModel() {
      if(textPaneModel == null) {
         textPaneModel = new TextPaneModel();
      }

      return textPaneModel;
   }

   public void setTextPaneModel(
      TextPaneModel textPaneModel)
   {
      this.textPaneModel = textPaneModel;
   }

   public TipPaneModel getTipPaneModel() {
      if(tipPaneModel == null) {
         tipPaneModel = new TipPaneModel();
      }

      return tipPaneModel;
   }

   public void setTipPaneModel(TipPaneModel tipPaneModel) {
      this.tipPaneModel = tipPaneModel;
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

   public PaddingPaneModel getPaddingPaneModel() {
      if(paddingPaneModel == null) {
         paddingPaneModel = new PaddingPaneModel();
      }

      return paddingPaneModel;
   }

   public void setPaddingPaneModel(PaddingPaneModel paddingPaneModel) {
      this.paddingPaneModel = paddingPaneModel;
   }

   private String popComponent;
   private String[] popComponents;
   private PopLocation popLocation;
   private String alpha;
   private OutputGeneralPaneModel outputGeneralPaneModel;
   private TextPaneModel textPaneModel;
   private TipPaneModel tipPaneModel;
   private SizePositionPaneModel sizePositionPaneModel;
   private PaddingPaneModel paddingPaneModel;
}
