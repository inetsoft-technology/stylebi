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
public class TipPaneModel {
   public boolean isChart() {
      return chart;
   }

   public void setChart(boolean chart) {
      this.chart = chart;
   }

   public boolean isTipOption() {
      return tipOption;
   }

   public void setTipOption(boolean tipOption) {
      this.tipOption = tipOption;
   }

   public String getTipView() {
      return tipView;
   }

   public void setTipView(String tipView) {
      this.tipView = tipView;
   }

   public String getAlpha() {
      return alpha;
   }

   public void setAlpha(String alpha) {
      this.alpha = alpha;
   }

   public String[] getFlyOverViews() {
      return flyOverViews;
   }

   public void setFlyOverViews(String[] flyOverViews) {
      this.flyOverViews = flyOverViews;
   }

   public boolean isFlyOnClick() {
      return flyOnClick;
   }

   public void setFlyOnClick(boolean flyOnClick) {
      this.flyOnClick = flyOnClick;
   }

   public String[] getPopComponents() {
      return popComponents;
   }

   public void setPopComponents(String[] popComponents) {
      this.popComponents = popComponents;
   }

   public String[] getFlyoverComponents() {
      return flyoverComponents;
   }

   public void setFlyoverComponents(String[] flyoverComponents) {
      this.flyoverComponents = flyoverComponents;
   }

   public TipCustomizeDialogModel getTipCustomizeDialogModel() {
      if(tipCustomizeDialogModel == null) {
         tipCustomizeDialogModel = new TipCustomizeDialogModel();
      }

      return tipCustomizeDialogModel;
   }

   public void setTipCustomizeDialogModel(
      TipCustomizeDialogModel tipCustomizeDialogModel)
   {
      this.tipCustomizeDialogModel = tipCustomizeDialogModel;
   }

   public boolean isDataViewEnabled() {
      return dataViewEnabled;
   }

   public void setDataViewEnabled(boolean dataViewEnabled) {
      this.dataViewEnabled = dataViewEnabled;
   }

   private boolean chart;
   private boolean tipOption;
   private String tipView;
   private String alpha;
   private String[] flyOverViews;
   private boolean flyOnClick;
   private String[] popComponents;
   private String[] flyoverComponents;
   private TipCustomizeDialogModel tipCustomizeDialogModel;
   private boolean dataViewEnabled;
}
