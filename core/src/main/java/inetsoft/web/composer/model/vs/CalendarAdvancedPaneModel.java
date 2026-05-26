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
public class CalendarAdvancedPaneModel implements Serializable {
   public int getShowType() {
      return showType;
   }

   public void setShowType(int showType) {
      this.showType = showType;
   }

   public int getViewMode() {
      return viewMode;
   }

   public void setViewMode(int viewMode) {
      this.viewMode = viewMode;
   }

   public boolean isYearView() {
      return yearView;
   }

   public void setYearView(boolean yearView) {
      this.yearView = yearView;
   }

   public boolean isDaySelection() {
      return daySelection;
   }

   public void setDaySelection(boolean daySelection) {
      this.daySelection = daySelection;
   }

   public boolean isSingleSelection() {
      return singleSelection;
   }

   public void setSingleSelection(boolean singleSelection) {
      this.singleSelection = singleSelection;
   }

   public boolean isSubmitOnChange() {
      return submitOnChange;
   }

   public void setSubmitOnChange(boolean submitOnChange) {
      this.submitOnChange = submitOnChange;
   }

   public DynamicValueModel getMin() {
      return min;
   }

   public void setMin(DynamicValueModel min) {
      this.min = min;
   }

   public DynamicValueModel getMax() {
      return max;
   }

   public void setMax(DynamicValueModel max) {
      this.max = max;
   }

   private int showType;
   private int viewMode;
   private boolean yearView;
   private boolean daySelection;
   private boolean singleSelection;
   private boolean submitOnChange;
   private DynamicValueModel min;
   private DynamicValueModel max;
}
