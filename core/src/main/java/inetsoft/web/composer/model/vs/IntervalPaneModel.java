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

import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonInterval;

public class IntervalPaneModel {
   public IntervalPaneModel() {
   }

   public IntervalPaneModel(DateComparisonInterval interval) {
      super();

      if(interval != null) {
         setInclusive(interval.isInclusive());
         setIntervalEndDate(new DynamicValueModel(interval.getIntervalEndDateValue()));
         setLevel(new DynamicValueModel(interval.getLevelValue()));
         setGranularity(new DynamicValueModel(interval.getGranularityValue()));
         setEndDayAsToDate(interval.isEndDayAsToDate());
         setContextLevel(new DynamicValueModel(interval.getContextLevelValue()));
      }
   }

   public DateComparisonInterval toDateComparisonInterval() {
      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setInclusive(isInclusive());

      if(getIntervalEndDate() != null) {
         interval.setIntervalEndDateValue(getIntervalEndDate().convertToValue());
      }

      if(getLevel() != null) {
         interval.setLevelValue(getLevel().convertToValue());
      }

      if(getContextLevel() != null) {
         interval.setContextLevelValue(getContextLevel().convertToValue());
      }

      if(getGranularity() != null) {
         interval.setGranularityValue(getGranularity().convertToValue());
      }

      interval.setEndDayAsToDate(isEndDayAsToDate());

      return interval;
   }

   public DynamicValueModel getLevel() {
      return level;
   }

   public void setLevel(DynamicValueModel level) {
      this.level = level;
   }

   public DynamicValueModel getGranularity() {
      return granularity;
   }

   public void setGranularity(DynamicValueModel granularity) {
      this.granularity = granularity;
   }

   public boolean isEndDayAsToDate() {
      return endDayAsToDate;
   }

   public void setEndDayAsToDate(boolean endDayAsToDate) {
      this.endDayAsToDate = endDayAsToDate;
   }

   public DynamicValueModel getIntervalEndDate() {
      return intervalEndDate;
   }

   public void setIntervalEndDate(DynamicValueModel intervalEndDate) {
      this.intervalEndDate = intervalEndDate;
   }

   public boolean isInclusive() {
      return inclusive;
   }

   public void setInclusive(boolean inclusive) {
      this.inclusive = inclusive;
   }

   public DynamicValueModel getContextLevel() {
      return contextLevel;
   }

   public void setContextLevel(DynamicValueModel contextLevel) {
      this.contextLevel = contextLevel;
   }

   private boolean endDayAsToDate = true;
   private boolean inclusive = true;
   private DynamicValueModel level = new DynamicValueModel(DateComparisonInfo.YEAR,
      DynamicValueModel.VALUE);
   private DynamicValueModel granularity = new DynamicValueModel(DateComparisonInfo.YEAR,
                                                                 DynamicValueModel.VALUE);
   private DynamicValueModel intervalEndDate = new DynamicValueModel(null, DynamicValueModel.VALUE);
   private DynamicValueModel contextLevel = new DynamicValueModel(DateComparisonInfo.YEAR,
      DynamicValueModel.VALUE);

}
