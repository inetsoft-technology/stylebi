/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.model.vs;

import inetsoft.uql.viewsheet.internal.*;

import java.util.ArrayList;
import java.util.List;

public class CustomPeriodPaneModel {
   public CustomPeriodPaneModel() {
      super();
   }

   public CustomPeriodPaneModel(CustomPeriods customPeriods) {
      super();

      if(customPeriods == null) {
         return;
      }

      List<DatePeriod> periods = customPeriods.getDatePeriods();
      List<DatePeriodModel> periodModels = new ArrayList<>();

      if(periods != null) {
         for(DatePeriod datePeriod : periods) {
            if(datePeriod == null) {
               continue;
            }

            periodModels.add(new DatePeriodModel(datePeriod));
         }
      }

      setDatePeriods(periodModels);
   }

   public DateComparisonPeriods toDateComparisonPeriods() {
      CustomPeriods customPeriods = new CustomPeriods();
      List<DatePeriod> datePeriods = new ArrayList<>();

      if(getDatePeriods() != null) {
         for(DatePeriodModel periodModel : getDatePeriods()) {
            if(periodModel == null) {
               continue;
            }

            DatePeriod datePeriod = new DatePeriod();

            if(periodModel.getStart() != null) {
               datePeriod.setStartValue(periodModel.getStart().convertToValue());
            }

            if(periodModel.getEnd() != null) {
               datePeriod.setEndValue(periodModel.getEnd().convertToValue());
            }

            datePeriods.add(datePeriod);
         }
      }

      customPeriods.setDatePeriods(datePeriods);

      return customPeriods;
   }

   public List<DatePeriodModel> getDatePeriods() {
      return datePeriods;
   }

   public void setDatePeriods(List<DatePeriodModel> datePeriods) {
      this.datePeriods = datePeriods;
   }

   private List<DatePeriodModel> datePeriods = new ArrayList<>();
}
