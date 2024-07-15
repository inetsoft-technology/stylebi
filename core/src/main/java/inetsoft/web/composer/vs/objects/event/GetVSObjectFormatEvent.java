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
package inetsoft.web.composer.vs.objects.event;

import inetsoft.report.TableDataPath;

/**
 * Class that encapsulates the parameters for getting a format for the toolbar.
 *
 * @since 12.3
 */
public class GetVSObjectFormatEvent extends VSObjectEvent {
   public TableDataPath getDataPath() {
      return dataPath;
   }

   public void setDataPath(TableDataPath dataPath) {
      this.dataPath = dataPath;
   }

   public int getRow() {
      return row;
   }

   public void setRow(int row) {
      this.row = row;
   }

   public int getColumn() {
      return column;
   }

   public void setColumn(int column) {
      this.column = column;
   }

   public String getRegion() {
      return region;
   }

   public void setRegion(String region) {
      this.region = region;
   }

   public int getIndex() {
      return index;
   }

   public void setIndex(int index) {
      this.index = index;
   }

   public boolean isDimensionColumn() {
      return dimensionColumn;
   }

   public void setDimensionColumn(boolean dimensionColumn) {
      this.dimensionColumn = dimensionColumn;
   }

   public String getColumnName() {
      return columnName;
   }

   public void setColumnName(String columnName) {
      this.columnName = columnName;
   }

   public boolean isLayout() {
      return layout;
   }

   public void setLayout(boolean layout) {
      this.layout = layout;
   }

   public boolean isBinding() {
      return binding;
   }

   public void setBinding(boolean binding) {
      this.binding = binding;
   }

   public int getLayoutRegion() {
      return layoutRegion;
   }

   public void setLayoutRegion(int layoutRegion) {
      this.layoutRegion = layoutRegion;
   }

   // if this is for show-values only (not text binding).
   public boolean isValueText() {
      return valueText;
   }

   public void setValueText(boolean valueText) {
      this.valueText = valueText;
   }

   @Override
   public String toString() {
      return "GetVSObjectFormatEvent{" +
         "dataPath='" + dataPath + '\'' +
         "row='" + row + '\'' +
         "column='" + column + '\'' +
         "region='" + region + '\'' +
         "index='" + index + '\'' +
         '}';
   }

   private TableDataPath dataPath;
   private int row = -1;
   private int column = -1;
   private String region;
   private String columnName;
   private boolean dimensionColumn;
   private int index;
   private int layoutRegion;
   private boolean layout;
   private boolean binding;
   private boolean valueText;
}
