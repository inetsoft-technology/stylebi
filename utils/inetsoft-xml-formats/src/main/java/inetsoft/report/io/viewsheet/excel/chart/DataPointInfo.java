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
package inetsoft.report.io.viewsheet.excel.chart;

import java.awt.Color;
import org.openxmlformats.schemas.drawingml.x2006.main.STPresetPatternVal;

/**
 * This class contains all the information of a chart serie value.
 *
 * @version 12.2, 7/19/2015
 * @author InetSoft Technology Corp
 */
public class DataPointInfo {
   public DataPointInfo() {
   }

   /**
    * Get fill color of the value shape.
    */
   public void setFillColor(Color color) {
      this.fillColor = color;
   }

   /**
    * Get fill color of the value shape.
    */
   public STPresetPatternVal.Enum getPattFillPrst() {
      return prst;
   }

   /**
    * Get fill color of the value shape.
    */
   public void setPattFillPrst(STPresetPatternVal.Enum prst) {
      this.prst = prst;
   }

   /**
    * Get fill color of the value shape.
    */
   public Color getFillColor() {
      return fillColor;
   }

   /**
    * Get line info.
    */
   public LineInfo getLineInfo() {
      return lineInfo;
   }

   /**
    * Set line info.
    */
   public void setLineInfo(LineInfo info) {
      this.lineInfo = info;
   }

   /**
    * Get marker info.
    */
   public MarkerInfo getMarkerInfo() {
      return markerInfo;
   }

   /**
    * Set line info.
    */
   public void setMarkerInfo(MarkerInfo info) {
      this.markerInfo = info;
   }

   private Color fillColor;
   private STPresetPatternVal.Enum prst = null;
   private LineInfo lineInfo;
   private MarkerInfo markerInfo;
}
