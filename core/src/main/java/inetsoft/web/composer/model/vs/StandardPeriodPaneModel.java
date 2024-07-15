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
package inetsoft.web.composer.model.vs;

import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.internal.DateComparisonPeriods;
import inetsoft.uql.viewsheet.internal.StandardPeriods;

public class StandardPeriodPaneModel {
   public StandardPeriodPaneModel() {
      super();
   }

   public StandardPeriodPaneModel(StandardPeriods standardPeriods) {
      super();

      if(standardPeriods != null) {
         setDateLevel(new DynamicValueModel(standardPeriods.getDateLevelValue()));
         setPreCount(new DynamicValueModel(standardPeriods.getPreCountValue()));
         setToDate(standardPeriods.isToDate());
         setInclusive(standardPeriods.isInclusive());
         setToDayAsEndDay(standardPeriods.isToDayAsEndDay());
         setEndDay(new DynamicValueModel(standardPeriods.getEndDateValue()));
      }
   }

   public DateComparisonPeriods toDateComparisonPeriods() {
      StandardPeriods standardPeriods = new StandardPeriods();

      if(getDateLevel() != null) {
         standardPeriods.setDateLevelValue(getDateLevel().convertToValue());
      }

      if(getPreCount() != null) {
         standardPeriods.setPreCountValue(getPreCount().convertToValue());
      }

      if(getEndDay() != null) {
         standardPeriods.setEndDateValue(getEndDay().convertToValue());
      }

      standardPeriods.setToDate(isToDate());
      standardPeriods.setInclusive(isInclusive());
      standardPeriods.setToDayAsEndDay(isToDayAsEndDay());

      return standardPeriods;
   }

   public DynamicValueModel getPreCount() {
      return preCount;
   }

   public void setPreCount(DynamicValueModel preCount) {
      this.preCount = preCount;
   }

   public DynamicValueModel getDateLevel() {
      return dateLevel;
   }

   public void setDateLevel(DynamicValueModel dateLevel) {
      this.dateLevel = dateLevel;
   }

   public boolean isToDate() {
      return toDate;
   }

   public void setToDate(boolean toDate) {
      this.toDate = toDate;
   }

   public DynamicValueModel getEndDay() {
      return endDay;
   }

   public void setEndDay(DynamicValueModel endDay) {
      this.endDay = endDay;
   }

   public boolean isToDayAsEndDay() {
      return toDayAsEndDay;
   }

   public void setToDayAsEndDay(boolean toDayAsEndDay) {
      this.toDayAsEndDay = toDayAsEndDay;
   }

   public boolean isInclusive() {
      return inclusive;
   }

   public void setInclusive(boolean inclusive) {
      this.inclusive = inclusive;
   }

   private DynamicValueModel preCount = new DynamicValueModel(2, DynamicValueModel.VALUE);
   private DynamicValueModel dateLevel =
      new DynamicValueModel(XConstants.YEAR_DATE_GROUP, DynamicValueModel.VALUE);
   private boolean toDate = true;
   private DynamicValueModel endDay = new DynamicValueModel(null, DynamicValueModel.VALUE);
   private boolean toDayAsEndDay = true;
   private boolean inclusive = true;
}