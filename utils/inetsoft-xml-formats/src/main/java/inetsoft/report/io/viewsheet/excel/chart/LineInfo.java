/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
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