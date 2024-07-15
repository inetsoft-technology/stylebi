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
package inetsoft.web.binding.model.table;

import inetsoft.report.StyleConstants;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSCrosstabInfo;

public class CrosstabOptionInfo extends BindingOptionInfo {
   /**
    * Constructor.
    */
   public CrosstabOptionInfo() {
   }

   /**
    * Constructor.
    */
    public CrosstabOptionInfo(CrosstabVSAssembly assembly) {
      super();
      VSCrosstabInfo crossInfo = assembly.getVSCrosstabInfo();
      setPercentageByValue(crossInfo.getPercentageByValue() != null ?
         crossInfo.getPercentageByValue(): "1" );
      setRowTotalVisibleValue(crossInfo.getRowTotalVisibleValue() != null ?
         crossInfo.getRowTotalVisibleValue(): "false");
      setColTotalVisibleValue(crossInfo.getColTotalVisibleValue() != null ?
         crossInfo.getColTotalVisibleValue(): "false");
      setSummarySideBySide(crossInfo.isSummarySideBySide() ?
         crossInfo.isSummarySideBySide(): false);
    }

   /**
    * Set percentageof value.
    */
   public void setPercentageByValue(String percentage) {
      this.percentageByValue = percentage;
   }

   /**
    * Get percentageof value.
    * @return the percentageof value.
    */
   public String getPercentageByValue() {
      return this.percentageByValue;
   }

   /**
    * Set row total.
    * @param row the row total value.
    */
   public void setRowTotalVisibleValue(String rowVisible) {
      this.rowTotalVisibleValue = rowVisible;
   }

   /**
    * Get row total
    * @return row total.
    */
   public String getRowTotalVisibleValue() {
      return this.rowTotalVisibleValue;
   }

   /**
    * Set col total.
    * @param col the col total value.
    */
   public void setColTotalVisibleValue(String colVisible) {
      this.colTotalVisibleValue = colVisible;
   }

   /**
    * Get col total
    * @return col total.
    */
   public String getColTotalVisibleValue() {
      return this.colTotalVisibleValue;
   }

   /**
    * Get SummarySideBySide
    */
   public void setSummarySideBySide(boolean horizontal) {
      sideBySide = horizontal;
   }

   /**
    * Get SummarySideBySide
    * @return SummarySideBySide.
    */
   public boolean isSummarySideBySide() {
      return sideBySide;
   }

   private String colTotalVisibleValue = "false";
   private String rowTotalVisibleValue = "false";
   private String percentageByValue = StyleConstants.PERCENTAGE_BY_COL + "";
   private boolean sideBySide = false;
}
