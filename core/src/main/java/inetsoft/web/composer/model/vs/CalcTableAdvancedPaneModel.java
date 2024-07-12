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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CalcTableAdvancedPaneModel {
   public TipPaneModel getTipPaneModel() {
      if(tipPaneModel == null) {
         tipPaneModel = new TipPaneModel();
      }

      return tipPaneModel;
   }

   public void setTipPaneModel(TipPaneModel tipPaneModel) {
      this.tipPaneModel = tipPaneModel;
   }

   public boolean isShrink() {
      return shrink;
   }

   public void setShrink(boolean shrink) {
      this.shrink = shrink;
   }

   public int getHeaderRowCount() {
      return headerRowCount;
   }

   public void setHeaderRowCount(int headerRowCount) {
      this.headerRowCount = headerRowCount;
   }

   public int getHeaderColCount() {
      return headerColCount;
   }

   public void setHeaderColCount(int headerColCount) {
      this.headerColCount = headerColCount;
   }

   public double getApproxVisibleRows() {
      return approxVisibleRows;
   }

   public void setApproxVisibleRows(double approxVisibleRows) {
      this.approxVisibleRows = approxVisibleRows;
   }

   public double getApproxVisibleCols() {
      return approxVisibleCols;
   }

   public void setApproxVisibleCols(double approxVisibleCols) {
      this.approxVisibleCols = approxVisibleCols;
   }

   public int getRowCount() {
      return rowCount;
   }

   public void setRowCount(int rowCount) {
      this.rowCount = rowCount;
   }

   public int getColCount() {
      return colCount;
   }

   public void setColCount(int colCount) {
      this.colCount = colCount;
   }

   public int getTrailerRowCount() {
      return trailerRowCount;
   }

   public void setTrailerRowCount(int trailerRowCount) {
      this.trailerRowCount = trailerRowCount;
   }

   public int getTrailerColCount() {
      return trailerColCount;
   }

   public void setTrailerColCount(int trailerColCount) {
      this.trailerColCount = trailerColCount;
   }

   public boolean isFillBlankWithZero() {
      return fillBlankWithZero;
   }

   public void setFillBlankWithZero(boolean fillBlankWithZero) {
      this.fillBlankWithZero = fillBlankWithZero;
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

   private boolean shrink;
   private int headerRowCount;
   private double approxVisibleRows;
   private int rowCount;
   private int headerColCount;
   private double approxVisibleCols;
   private int colCount;
   private int trailerRowCount;
   private int trailerColCount;
   private TipPaneModel tipPaneModel;
   private boolean fillBlankWithZero;
   private boolean sortOthersLast;
   private boolean sortOthersLastEnabled;
}
