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


public class CrosstabAdvancedPaneModel {
   public boolean isFillBlankWithZero() {
      return fillBlankWithZero;
   }

   public void setFillBlankWithZero(boolean fillBlankWithZero) {
      this.fillBlankWithZero = fillBlankWithZero;
   }

   public boolean isSummarySideBySide() {
      return summarySideBySide;
   }

   public void setSummarySideBySide(boolean summarySideBySide) {
      this.summarySideBySide = summarySideBySide;
   }

   public boolean isMergeSpan() {
      return mergeSpan;
   }

   public void setMergeSpan(boolean merge) {
      this.mergeSpan = merge;
   }

   public boolean isShrink() {
      return shrink;
   }

   public void setShrink(boolean shrink) {
      this.shrink = shrink;
   }

   public boolean isDrillEnabled() {
      return drillEnabled;
   }

   public void setDrillEnabled(boolean drillEnabled) {
      this.drillEnabled = drillEnabled;
   }

   public boolean isEnableAdhoc() {
      return enableAdhoc;
   }

   public void setEnableAdhoc(boolean enableAdhoc) {
      this.enableAdhoc = enableAdhoc;
   }

   public boolean isCrosstabInfoNull() {
      return crosstabInfoNull;
   }

   public void setCrosstabInfoNull(boolean crosstabInfoNull) {
      this.crosstabInfoNull = crosstabInfoNull;
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

   public boolean isDateComparisonEnabled() {
      return dateComparisonEnabled;
   }

   public void setDateComparisonEnabled(boolean dateComparisonEnabled) {
      this.dateComparisonEnabled = dateComparisonEnabled;
   }

   public boolean isSortOthersLast() {
      return sortOthersLast;
   }

   public void setSortOthersLast(boolean sortOthersLast) {
      this.sortOthersLast = sortOthersLast;
   }

   public boolean isSortOthersLastEnabled() {
      return sortOthersLastEnabled;
   }

   public void setSortOthersLastEnabled(boolean sortOthersLastEnabled) {
      this.sortOthersLastEnabled = sortOthersLastEnabled;
   }

   public boolean isDateComparisonSupport() {
      return dateComparisonSupport;
   }

   public void setDateComparisonSupport(boolean dateComparisonSupport) {
      this.dateComparisonSupport = dateComparisonSupport;
   }

   public boolean isCalculateTotal() {
      return calculateTotal;
   }

   public void setCalculateTotal(boolean calculateTotal) {
      this.calculateTotal = calculateTotal;
   }

   private boolean crosstabInfoNull;
   private boolean fillBlankWithZero;
   private boolean summarySideBySide;
   private boolean shrink;
   private boolean drillEnabled;
   private boolean enableAdhoc;
   private boolean mergeSpan = true;
   private boolean dateComparisonEnabled = true;
   private boolean sortOthersLast;
   private boolean sortOthersLastEnabled;
   private TipPaneModel tipPaneModel;
   private boolean dateComparisonSupport;
   private boolean calculateTotal;
}
