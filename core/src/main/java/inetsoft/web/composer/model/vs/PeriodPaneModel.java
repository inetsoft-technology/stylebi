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

import inetsoft.uql.viewsheet.internal.*;

import java.io.Serializable;

public class PeriodPaneModel implements Serializable {
   public PeriodPaneModel() {
      super();
   }

   public PeriodPaneModel(DateComparisonPeriods datePeriods) {
      super();

      if(datePeriods != null) {
         setCustom(datePeriods instanceof CustomPeriods);

         if(datePeriods instanceof StandardPeriods) {
            setStandardPeriodPaneModel(
               new StandardPeriodPaneModel((StandardPeriods) datePeriods));
         }
         else if(datePeriods instanceof CustomPeriods) {
            setCustomPeriodPaneModel(
               new CustomPeriodPaneModel((CustomPeriods) datePeriods));
         }
      }
   }

   public DateComparisonPeriods toDateComparisonPeriods() {
      return isCustom() ? getCustomPeriodPaneModel().toDateComparisonPeriods() :
         getStandardPeriodPaneModel().toDateComparisonPeriods();
   }

   public boolean isCustom() {
      return custom;
   }

   public void setCustom(boolean custom) {
      this.custom = custom;
   }

   public StandardPeriodPaneModel getStandardPeriodPaneModel() {
      if(standardPeriodPaneModel == null) {
         standardPeriodPaneModel = new StandardPeriodPaneModel();
      }

      return standardPeriodPaneModel;
   }

   public void setStandardPeriodPaneModel(StandardPeriodPaneModel standardPeriodPaneModel) {
      this.standardPeriodPaneModel = standardPeriodPaneModel;
   }

   public CustomPeriodPaneModel getCustomPeriodPaneModel() {
      if(customPeriodPaneModel == null) {
         customPeriodPaneModel = new CustomPeriodPaneModel();
      }

      return customPeriodPaneModel;
   }

   public void setCustomPeriodPaneModel(CustomPeriodPaneModel customPeriodPaneModel) {
      this.customPeriodPaneModel = customPeriodPaneModel;
   }

   private boolean custom;
   private StandardPeriodPaneModel standardPeriodPaneModel = new StandardPeriodPaneModel();
   private CustomPeriodPaneModel customPeriodPaneModel = new CustomPeriodPaneModel();
}
