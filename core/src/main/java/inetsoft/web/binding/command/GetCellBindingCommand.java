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
package inetsoft.web.binding.command;

import inetsoft.web.binding.drm.AggregateRefModel;
import inetsoft.web.binding.model.table.CellBindingInfo;
import inetsoft.web.viewsheet.command.ViewsheetCommand;

/**
 * Command that instructs the client to refresh an assembly object.
 *
 * @since 12.3
 */
public class GetCellBindingCommand implements ViewsheetCommand {
   /**
    * Construstor.
    */
   public GetCellBindingCommand(CellBindingInfo binding) {
      this.binding = binding;
   }

   /**
    * Get the cell binding info.
    * @return the cell binding info.
    */
   public CellBindingInfo getBinding() {
      return binding;
   }

   /**
    * Set cell binding info.
    * @param binding the cell binding info.
    */
   public void setBinding(CellBindingInfo binding) {
      this.binding = binding;
   }

   /**
    * Get the cell name of the calc table cell.
    * @return the cell name of calc table cell.
    */
   public String getCellName() {
      return cellname;
   }

   /**
    * Set the cell name.
    * @param cellname cell name.
    */
   public void setCellName(String cellname) {
      this.cellname = cellname;
   }

   /**
    * Get the all cell names of calc table.
    * @return all cell names of calc table.
    */
   public String[] getCellNames() {
      return cellnames;
   }

   /**
    * Set the assembly names.
    * @param cellnames assembly names.
    */
   public void setCellNames(String[] cellnames) {
      this.cellnames = cellnames;
   }

   /**
    * Get the is row group or not.
    * @return true if is row group.
    */
   public boolean getRowGroup() {
      return rowGroup;
   }

   /**
    * Set the row group.
    * @param rowGroup is row group or not.
    */
   public void setRowGroup(boolean rowGroup) {
      this.rowGroup = rowGroup;
   }

   /**
    * Check if is col group.
    * @return true is is col group.
    */
   public boolean getColGroup() {
      return colGroup;
   }

   /**
    * Set the col group.
    * @param colGroup is column group.
    */
   public void setColGroup(boolean colGroup) {
      this.colGroup = colGroup;
   }

   /**
    * Get the group numbers.
    * @return group numbers.
    */
   public int getGroupNum() {
      return groupNum;
   }

   /**
    * Set the group number.
    * @param groupNum the group number.
    */
   public void setGroupNum(int groupNum) {
      this.groupNum = groupNum;
   }

   /**
    * Get the all the aggregate ref in calc table.
    * @return all the aggregates.
    */
   public AggregateRefModel[] getAggregates() {
      return aggs;
   }

   /**
    * Set the aggregates.
    * @param aggs the aggregates.
    */
   public void setAggregates(AggregateRefModel[] aggs) {
      this.aggs = aggs;
   }

   /**
    * Set the row of cell binding .
    */
   public void setCellRow(int cellRow) {
      this.cellRow = cellRow;
   }

   /**
    * Get the row of cell binding .
    */
   public int getCellRow() {
      return this.cellRow;
   }

   /**
    * Set the col of cell binding .
    */
   public void setCellCol(int cellCol) {
      this.cellCol = cellCol;
   }

   /**
    * Get the col of cell binding .
    */
   public int getcellCol() {
      return this.cellCol;
   }

   private CellBindingInfo binding;
   private String cellname;
   private String[] cellnames;
   private boolean rowGroup;
   private boolean colGroup;
   private int groupNum;
   private AggregateRefModel[] aggs;
   private int cellRow;
   private int cellCol;
}
