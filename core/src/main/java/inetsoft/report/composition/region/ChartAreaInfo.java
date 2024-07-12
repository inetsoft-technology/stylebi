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
package inetsoft.report.composition.region;

import java.util.HashMap;

/**
 * The ChartAreaInfo is used to identify the specific part of a chart.
 */
public class ChartAreaInfo {
   /**
    * The title area.
    */
   public static final String TITLE = "title";
   /**
    * The axis area.
    */
   public static final String AXIS = "axis";
   /**
    * The text area.
    */
   public static final String TEXT = "text";
   /**
    * The legend area.
    */
   public static final String LEGEND = "legend";
   /**
    * The plot area.
    */
   public static final String PLOT = "plot";
   /**
    * The vo area.
    */
   public static final String VO = "vo";
   /**
    * The label area.
    */
   public static final String LABEL = "label";

   /**
    * Create a chart area info for label.
    */
   public static ChartAreaInfo createLabel(int targetIndex) {
      ChartAreaInfo cainfo = new ChartAreaInfo();
      cainfo.ctype = "label";
      cainfo.areaType = LABEL;
      cainfo.setProperty("targetIndex", targetIndex);

      return cainfo;
   }

   /**
    * Create a chart area info for an axis title.
    */
   public static ChartAreaInfo createAxisTitle() {
      ChartAreaInfo cainfo = new ChartAreaInfo();
      cainfo.ctype = "axisTitle";
      cainfo.areaType = TITLE;

      return cainfo;
   }

   /**
    * Create a chart area info for an axis line.
    */
   public static ChartAreaInfo createAxisLine() {
      ChartAreaInfo cainfo = new ChartAreaInfo();
      cainfo.ctype = "axisLine";
      cainfo.areaType = AXIS;

      return cainfo;
   }

   /**
    * Create a chart area info for an axis label.
    */
   public static ChartAreaInfo createAxisLabel() {
      ChartAreaInfo cainfo = new ChartAreaInfo();
      cainfo.ctype = "axisLabel";
      cainfo.areaType = AXIS;

      return cainfo;
   }

   /**
    * Create a chart area info for a measure label.
    */
   public static ChartAreaInfo createMeasureLabel() {
      ChartAreaInfo cainfo = new ChartAreaInfo();
      cainfo.ctype = "measureLabel";
      cainfo.areaType = AXIS;

      return cainfo;
   }

   /**
    * Create a chart area info for text labels.
    * @param valueText true if this is a VOText for show-values only (and not text binding).
    */
   public static ChartAreaInfo createText(boolean valueText) {
      ChartAreaInfo cainfo = new ChartAreaInfo();
      cainfo.ctype = "text";
      cainfo.areaType = TEXT;

      if(valueText) {
         cainfo.setProperty("valueText", true);
      }

      return cainfo;
   }

   /**
    * Create a chart area info for legend title.
    */
   public static ChartAreaInfo createLegendTitle() {
      ChartAreaInfo cainfo = new ChartAreaInfo();
      cainfo.ctype = "legendTitle";
      cainfo.areaType = LEGEND;

      return cainfo;
   }

   /**
    * Create a chart area info for legend title.
    */
   public static ChartAreaInfo createLegendContent() {
      ChartAreaInfo cainfo = new ChartAreaInfo();
      cainfo.ctype = "legendContent";
      cainfo.areaType = LEGEND;

      return cainfo;
   }

   /**
    * Create a chart area info for plot.
    */
   public static ChartAreaInfo createPlot() {
      ChartAreaInfo cainfo = new ChartAreaInfo();
      cainfo.ctype = "plot";
      cainfo.areaType = PLOT;

      return cainfo;
   }

   /**
    * Create a chart area for the VO area.
    */
   public static ChartAreaInfo createVO() {
      ChartAreaInfo cainfo = new ChartAreaInfo();
      cainfo.ctype = "vo";
      cainfo.areaType = VO;

      return cainfo;
   }

   /**
    * Get the area type.
    */
   public String getAreaType() {
      return areaType;
   }

   /**
    * Set the property.
    */
   public void setProperty(String name, Object value) {
      map.put(name, value);
   }

   /**
    * Get the property.
    */
   public Object getProperty(String name) {
      return map.get(name);
   }

   /**
    * Remove the property.
    */
   public void removeProperty(String name) {
      map.remove(name);
   }

   /**
    * Get the id of this info.
    */
   public String getID() {
      String str = ctype + "," + areaType;

      if(map.size() > 0) {
         str += "," + map.toString();
      }

      return str;
   }

   @Override
   public String toString() {
      return super.toString() + "(" + ctype + "," + areaType + "," + map + ")";
   }

   private String ctype;
   private String areaType;
   private HashMap<String, Object> map = new HashMap<>();
}
