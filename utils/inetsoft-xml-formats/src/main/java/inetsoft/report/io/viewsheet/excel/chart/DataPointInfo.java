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
