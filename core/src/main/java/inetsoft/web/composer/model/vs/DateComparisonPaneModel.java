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

import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.model.graph.aesthetic.VisualFrameModel;

public class DateComparisonPaneModel {
   public DateComparisonPaneModel() {
   }

   public DateComparisonPaneModel(DateComparisonInfo info) {
      super();

      if(info == null) {
         return;
      }

      DateComparisonPeriods datePeriods = info.getPeriods();

      if(datePeriods != null) {
         periodPaneModel.setCustom(info.getPeriods() instanceof CustomPeriods);

         if(datePeriods instanceof StandardPeriods) {
            periodPaneModel.setStandardPeriodPaneModel(
               new StandardPeriodPaneModel((StandardPeriods) datePeriods));
         }
         else if(datePeriods instanceof CustomPeriods) {
            periodPaneModel.setCustomPeriodPaneModel(
               new CustomPeriodPaneModel((CustomPeriods) datePeriods));
         }
      }

      if(info.getInterval() != null) {
         setIntervalPaneModel(new IntervalPaneModel(info.getInterval()));
      }

      setComparisonOption(info.getComparisonOption());
      setUseFacet(info.isUseFacet());
      setOnlyShowMostRecentDate(info.isShowMostRecentDateOnly());
   }

   public DateComparisonInfo toDateComparisonInfo()
      throws Exception
   {
      DateComparisonInfo info = new DateComparisonInfo();
      info.setComparisonOption(getComparisonOption());
      info.setUseFacet(isUseFacet());
      info.setShowMostRecentDateOnly(isOnlyShowMostRecentDate());
      info.setDateComparisonPeriods(getPeriodPaneModel().toDateComparisonPeriods());
      info.setDateComparisonInterval(getIntervalPaneModel().toDateComparisonInterval());

      if(visualFrameModel != null) {
         info.setDcColorFrameWrapper(VisualFrameWrapper.wrap(visualFrameModel.createVisualFrame()));
      }

      return info;
   }

   public PeriodPaneModel getPeriodPaneModel() {
      if(periodPaneModel == null) {
         periodPaneModel = new PeriodPaneModel();
      }

      return periodPaneModel;
   }

   public void setPeriodPaneModel(PeriodPaneModel periodPaneModel) {
      this.periodPaneModel = periodPaneModel;
   }

   public IntervalPaneModel getIntervalPaneModel() {
      if(intervalPaneModel == null) {
         intervalPaneModel = new IntervalPaneModel();
      }

      return intervalPaneModel;
   }

   public void setIntervalPaneModel(IntervalPaneModel intervalPaneModel) {
      this.intervalPaneModel = intervalPaneModel;
   }

   public int getComparisonOption() {
      return comparisonOption;
   }

   public void setComparisonOption(int comparisonOption) {
      this.comparisonOption = comparisonOption;
   }

   public boolean isUseFacet() {
      return useFacet;
   }

   public void setUseFacet(boolean useFacet) {
      this.useFacet = useFacet;
   }

   public boolean isOnlyShowMostRecentDate() {
      return onlyShowMostRecentDate;
   }

   public void setOnlyShowMostRecentDate(boolean onlyShowMostRecentDate) {
      this.onlyShowMostRecentDate = onlyShowMostRecentDate;
   }

   public VisualFrameModel getVisualFrameModel() {
      return visualFrameModel;
   }

   public void setVisualFrameModel(VisualFrameModel visualFrameModel) {
      this.visualFrameModel = visualFrameModel;
   }

   private PeriodPaneModel periodPaneModel = new PeriodPaneModel();
   private IntervalPaneModel intervalPaneModel = new IntervalPaneModel();
   private int comparisonOption = Calculator.VALUE;
   private boolean useFacet = false;
   private boolean onlyShowMostRecentDate = true;

   private VisualFrameModel visualFrameModel;
}
