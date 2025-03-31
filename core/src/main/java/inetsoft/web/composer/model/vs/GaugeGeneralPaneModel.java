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
 * Data transfer object that represents the {@link GaugePropertyDialogModel} for the
 * gauge property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GaugeGeneralPaneModel implements Serializable {
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

   public NumberRangePaneModel getNumberRangePaneModel() {
      if(numberRangePaneModel == null) {
         numberRangePaneModel = new NumberRangePaneModel();
      }

      return numberRangePaneModel;
   }

   public void setNumberRangePaneModel(
      NumberRangePaneModel numberRangePaneModel)
   {
      this.numberRangePaneModel = numberRangePaneModel;
   }

   public FacePaneModel getFacePaneModel() {
      if(facePaneModel == null) {
         facePaneModel = new FacePaneModel();
      }

      return facePaneModel;
   }

   public void setFacePaneModel(
      FacePaneModel facePaneModel)
   {
      this.facePaneModel = facePaneModel;
   }

   @Override
   public String toString() {
      return "GaugeGeneralPaneModel{" +
         "outputGeneralPaneModel=" + outputGeneralPaneModel +
         ", numberRangePaneModel=" + numberRangePaneModel +
         ", facePaneModel=" + facePaneModel +
         ", sizePositionPaneModel=" + sizePositionPaneModel +
         '}';
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

   public TipPaneModel getTipPaneModel() {
      if(tipPaneModel == null) {
         tipPaneModel = new TipPaneModel();
      }

      return tipPaneModel;
   }

   public void setTipPaneModel(TipPaneModel tipPaneModel) {
      this.tipPaneModel = tipPaneModel;
   }

   private OutputGeneralPaneModel outputGeneralPaneModel;
   private NumberRangePaneModel numberRangePaneModel;
   private FacePaneModel facePaneModel;
   private SizePositionPaneModel sizePositionPaneModel;
   private PaddingPaneModel paddingPaneModel;
   private TipPaneModel tipPaneModel;
}
