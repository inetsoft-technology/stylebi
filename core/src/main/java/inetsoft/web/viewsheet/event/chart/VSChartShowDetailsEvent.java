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
package inetsoft.web.viewsheet.event.chart;

import inetsoft.uql.asset.DetailDndInfo;
import inetsoft.web.adhoc.model.FormatInfoModel;
import inetsoft.web.composer.model.SortInfoModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Show details event data
 */
public class VSChartShowDetailsEvent extends VSChartEvent {
   public String getSelected() {
      return selected;
   }

   public void setSelected(String selected) {
      this.selected = selected;
   }

   public void setDndInfo(DetailDndInfo dnd) {
      this.dndInfo = dnd;
   }


   public DetailDndInfo getDndInfo() {
      return dndInfo;
   }

   public boolean isRangeSelection() {
      return rangeSelection;
   }

   public void setRangeSelection(boolean rangeSelection) {
      this.rangeSelection = rangeSelection;
   }

   public SortInfoModel getSortInfo() {
      return sortInfo;
   }

   public String getWorksheetId() {
      return worksheetId;
   }
   
   public String getDetailStyle() {
      return style;
   }

   public void setWorksheetId(String worksheetId) {
      this.worksheetId = worksheetId;
   }

   public FormatInfoModel getFormat() {
      return format;
   }

   public int getColumn() {
      if(columns.size() > 0) {
         return columns.get(0);
      }

      return 0;
   }

   public void setColumn(int column) {
      this.columns = new ArrayList<>();
      this.columns.add(column);
   }

   public List<Integer> getColumns() {
      return columns;
   }

   public void setColumns(List<Integer> column) {
      this.columns = column;
   }
   
   public void setDetailStyle(String style) {
      this.style = style;
   }

   public String getNewColName() {
      return newColName;
   }

   public void setNewColName(String newColName) {
      this.newColName = newColName;
   }

   public boolean isToggleHide() {
      return toggleHide;
   }

   public void setToggleHide(boolean toggleHide) {
      this.toggleHide = toggleHide;
   }


   // Concatenated string of selected regions and associated indexes
   private String selected;

   // True if more than 1 region is selected
   private boolean rangeSelection;

   private SortInfoModel sortInfo;
   private String worksheetId;
   private FormatInfoModel format;
   private List<Integer> columns;
   private String style;
   private DetailDndInfo dndInfo;
   private String newColName;
   private boolean toggleHide;
}
