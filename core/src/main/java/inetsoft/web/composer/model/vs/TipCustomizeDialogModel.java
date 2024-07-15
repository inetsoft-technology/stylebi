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
public class TipCustomizeDialogModel {
   public enum TipFormat { DEFAULT, CUSTOM, NONE };

   public boolean isChart() {
      return chart;
   }

   public void setChart(boolean chart) {
      this.chart = chart;
   }

   public TipFormat getCustomRB() {
      return customRB;
   }

   public void setCustomRB(TipFormat customRB) {
      this.customRB = customRB;
   }

   public boolean isCombinedTip() {
      return combinedTip;
   }

   public void setCombinedTip(boolean combinedTip) {
      this.combinedTip = combinedTip;
   }

   public boolean isLineChart() {
      return lineChart;
   }

   public void setLineChart(boolean lineChart) {
      this.lineChart = lineChart;
   }

   public String getCustomTip() {
      return customTip;
   }

   public void setCustomTip(String customTip) {
      this.customTip = customTip;
   }

   public String[] getDataRefList() {
      return dataRefList;
   }

   public void setDataRefList(String[] dataRefList) {
      this.dataRefList = dataRefList;
   }

   public String[] getAvailableTipValues() {
      return availableTipValues;
   }

   public void setAvailableTipValues(String[] availableTipValues) {
      this.availableTipValues = availableTipValues;
   }

   private TipFormat customRB;
   private boolean combinedTip;
   private boolean lineChart;
   private boolean chart;
   private String customTip;
   private String[] dataRefList;
   private String[] availableTipValues;
}
