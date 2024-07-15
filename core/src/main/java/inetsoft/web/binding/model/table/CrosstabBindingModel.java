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

import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.DateComparableModel;

import java.util.*;

public class CrosstabBindingModel extends BaseTableBindingModel implements DateComparableModel {
   /**
    * Set crosstab option.
    * @param option the crosstab option.
    */
   public void setOption(CrosstabOptionInfo option) {
      this.option = option;
   }

   /**
    * Get the crosstab option.
    * @return crosstab option.
    */
   public CrosstabOptionInfo getOption() {
      return option;
   }

   /**
    * Add crosstab row header.
    * @param col the crosstab row header.
    */
   public void addRow(BDimensionRefModel row) {
      rows.add(row);
   }

   /**
    * Get the crosstab row headers.
    * @return crosstab row headers.
    */
   public List<BDimensionRefModel> getRows() {
      return rows;
   }

   /**
    * Set crosstab row headers.
    * @param rows the crosstab row headers.
    */
   public void setRows(List<BDimensionRefModel> rows) {
      this.rows = rows;
   }

   /**
    * Add crosstab col header.
    * @param col the crosstab col header.
    */
   public void addCol(BDimensionRefModel col) {
      cols.add(col);
   }

   /**
    * Get the crosstab col headers.
    * @return crosstab col headers.
    */
   public List<BDimensionRefModel> getCols() {
      return cols;
   }

   /**
    * Set crosstab col headers.
    * @param cols the crosstab col headers.
    */
   public void setCols(List<BDimensionRefModel> cols) {
      this.cols = cols;
   }

   public Hashtable<String, Boolean> getSuppressGroupTotal() {
      return this.suppressGroupTotal;
   }

   public void setSuppressGroupTotal(Hashtable<String, Boolean> suppressGroupTotal) {
      this.suppressGroupTotal = suppressGroupTotal;
   }

   @Override
   public boolean isHasDateComparison() {
      return hasDateComparison;
   }

   @Override
   public void setHasDateComparison(boolean hasDateComparison) {
      this.hasDateComparison = hasDateComparison;
   }

   private boolean hasDateComparison;
   private CrosstabOptionInfo option = null;
   private List<BDimensionRefModel> rows = new ArrayList<>();
   private List<BDimensionRefModel> cols = new ArrayList<>();
   private Hashtable<String, Boolean> suppressGroupTotal = new Hashtable<>();
}
