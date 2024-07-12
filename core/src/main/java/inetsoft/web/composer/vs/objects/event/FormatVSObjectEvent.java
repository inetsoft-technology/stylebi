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
package inetsoft.web.composer.vs.objects.event;

import inetsoft.report.TableDataPath;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;

import java.util.ArrayList;

/**
 * Class that encapsulates the parameters for changing object formats.
 *
 * @since 12.3
 */
public class FormatVSObjectEvent {
   public VSObjectFormatInfoModel getFormat() {
      return format;
   }

   public void setFormat(VSObjectFormatInfoModel format) {
      this.format = format;
   }

   public VSObjectFormatInfoModel getOrigFormat() {
      return (origFormat != null) ? origFormat : new VSObjectFormatInfoModel();
   }

   public void setOrigFormat(VSObjectFormatInfoModel format) {
      this.origFormat = format;
   }

   public String[] getObjects() {
      if(objects == null) {
         return new String[]{};
      }

      return objects;
   }

   public void setObjects(String[] objects) {
      this.objects = objects;
   }

   public ArrayList<TableDataPath[]> getData() {
      return data;
   }

   public void setData(ArrayList<TableDataPath[]> data) {
      this.data = data;
   }

   public String[] getCharts() {
      return charts;
   }

   public void setCharts(String[] charts) {
      this.charts = charts;
   }

   public String[] getRegions() {
      return regions;
   }

   public void setRegions(String[] regions) {
      this.regions = regions;
   }

   public int[][] getIndexes() {
      return indexes;
   }

   public void setIndexes(int[][] indexes) {
      this.indexes = indexes;
   }

   public String[][] getColumnNames() {
      return columnNames;
   }

   public void setColumnNames(String[][] columnNames) {
      this.columnNames = columnNames;
   }

   public boolean isLayout() {
      return layout;
   }

   public void setLayout(boolean layout) {
      this.layout = layout;
   }

   public int getLayoutRegion() {
      return layoutRegion;
   }

   public void setLayoutRegion(int layoutRegion) {
      this.layoutRegion = layoutRegion;
   }

   // if this is for show-value text only (not text binding).
   public boolean isValueText() {
      return valueText;
   }

   public void setValueText(boolean valueText) {
      this.valueText = valueText;
   }

   public boolean isReset() {
      return reset;
   }

   public void setReset(boolean reset) {
      this.reset = reset;
   }

   public boolean isCopyFormat() {
      return copyFormat;
   }

   public void setCopyFormat(boolean copyFormat) {
      this.copyFormat = copyFormat;
   }

   @Override
   public String toString() {
      return "FormatVSObjectEvent{" +
         "format='" + format + '\'' +
         "objects='" + objects + '\'' +
         "data='" + data + '\'' +
         "charts='" + charts + '\'' +
         "regions='" + regions + '\'' +
         "indexes='" + indexes + '\'' +
         "layout='" + layout + '\'' +
         "layoutRegion='" + layoutRegion + '\'' +
         "reset='" + reset + '\'' +
         "copyFormat='" + copyFormat + '\'' +
         '}';
   }

   private VSObjectFormatInfoModel format;
   private VSObjectFormatInfoModel origFormat;
   private String[] objects;
   private ArrayList<TableDataPath[]> data;
   private String[] charts;
   private String[] regions;
   private int[][] indexes;
   private String[][] columnNames;
   private boolean layout;
   private int layoutRegion;
   private boolean valueText;
   private boolean reset;
   private boolean copyFormat;
}
