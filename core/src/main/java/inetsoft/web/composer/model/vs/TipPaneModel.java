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
public class TipPaneModel implements Serializable {
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

   /**
    * Clamps to 0-100 (an opacity percentage), mirroring the frontend's own AlphaDropdown
    * widget (Math.min(Math.max(0, alpha), 100)) so a caller that reaches this model directly
    * -- bypassing the widget, e.g. the wiz plugin's set_assembly_properties -- can't persist
    * a value the UI itself would never allow through (Redmine #76759 VCG-006).
    */
   public void setAlpha(String alpha) {
      if(alpha != null && !alpha.isEmpty()) {
         try {
            double value = Double.parseDouble(alpha);
            double clamped = Math.max(0, Math.min(100, value));

            if(clamped != value) {
               alpha = clamped == Math.floor(clamped)
                  ? String.valueOf((int) clamped) : String.valueOf(clamped);
            }
         }
         catch(NumberFormatException ignore) {
            // Not a plain number (e.g. a variable reference) -- leave as-is, unrelated to
            // this range clamp.
         }
      }

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

   public boolean isTipOnClick() {
      return tipOnClick;
   }

   public void setTipOnClick(boolean tipOnClick) {
      this.tipOnClick = tipOnClick;
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
   private boolean tipOnClick;
   private String[] popComponents;
   private String[] flyoverComponents;
   private TipCustomizeDialogModel tipCustomizeDialogModel;
   private boolean dataViewEnabled;
}
