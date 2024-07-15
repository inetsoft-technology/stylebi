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

import org.openxmlformats.schemas.drawingml.x2006.chart.STMarkerStyle;
import org.openxmlformats.schemas.drawingml.x2006.main.STCompoundLine;
import org.openxmlformats.schemas.drawingml.x2006.main.STPresetLineDashVal;

/**
 * This class contains all the information of a chart serie value.
 *
 * @version 12.2, 7/19/2015
 * @author InetSoft Technology Corp
 */
public class LineInfo {
   public LineInfo() {
      super();
   }

   /**
    * Get the line width.
    */
   public void setLineWidth(int w) {
      this.lnWidth = w;
   }

   /**
    * Set the line width.
    */
   public int getLineWidth() {
      return lnWidth;
   }

   /**
    * Set the line compound style.
    */
   public void setLineCompound(STCompoundLine.Enum cmpd) {
      this.lnCmpd = cmpd;
   }

   /**
    * Get the line compound style.
    */
   public STCompoundLine.Enum getLineCompound() {
      return lnCmpd;
   }

   /**
    * Set line style.
    */
   public void setLinePrstDash(STPresetLineDashVal.Enum prstDash) {
      this.prstDash = prstDash;
   }

   /**
    * Get line style.
    */
   public STPresetLineDashVal.Enum getLinePrstDash() {
      return prstDash;
   }

   private int lnWidth = 0;
   private STCompoundLine.Enum lnCmpd;
   private STPresetLineDashVal.Enum prstDash;
   private STMarkerStyle.Enum markerStyle = STMarkerStyle.NONE;
}