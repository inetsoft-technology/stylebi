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

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartGeneralPaneModel implements Serializable {
   public GeneralPropPaneModel getGeneralPropPaneModel() {
      if(generalPropPaneModel == null) {
         generalPropPaneModel = new GeneralPropPaneModel();
      }

      return generalPropPaneModel;
   }

   public void setGeneralPropPaneModel(GeneralPropPaneModel generalPropPaneModel) {
      this.generalPropPaneModel = generalPropPaneModel;
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

   public TitlePropPaneModel getTitlePropPaneModel() {
      if(titlePropPaneModel == null) {
         titlePropPaneModel = new TitlePropPaneModel();
      }

      return titlePropPaneModel;
   }

   public void setTitlePropPaneModel(TitlePropPaneModel titlePropPaneModel) {
      this.titlePropPaneModel = titlePropPaneModel;
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

   private GeneralPropPaneModel generalPropPaneModel;
   private TipPaneModel tipPaneModel;
   private SizePositionPaneModel sizePositionPaneModel;
   private TitlePropPaneModel titlePropPaneModel;
   private PaddingPaneModel paddingPaneModel;
}
