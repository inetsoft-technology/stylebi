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

import inetsoft.report.StyleConstants;
import org.openxmlformats.schemas.drawingml.x2006.chart.STMarkerStyle;

/**
 * This class contains all the information of a chart serie value.
 *
 * @version 12.2, 7/19/2015
 * @author InetSoft Technology Corp
 */
public class MarkerInfo {
   /**
    * @param size    the shape size.
    * @param shapeId the GShape id.
    */
   public MarkerInfo(int size, int shapeId) {
      this.size = size;
      this.shapeId = shapeId;
   }

   /**
    * @param size   marker size.
    * @param mstyle the marker style.
    */
   public MarkerInfo(int size, STMarkerStyle.Enum mstyle) {
      this.size = size;
      this.markerStyle = mstyle;
   }

   /**
    * Get marker size.
    */
   public void setSize(int size) {
      this.size = size;
   }

   /**
    * Set marker size.
    */
   public int getSize() {
      return size;
   }

   /**
    * Get marker size.
    */
   public void setShapeId(int id) {
      this.shapeId = id;
   }

   /**
    * Set marker size.
    */
   public int getShapeId() {
      return shapeId;
   }

   /**
    * Check if target shape is unfilled style.
    */
   public boolean isFillShape() {
      if(shapeId == StyleConstants.NIL) {
         return true;
      }

      if(shapeId == StyleConstants.FILLED_CIRCLE ||
         shapeId == StyleConstants.FILLED_TRIANGLE ||
         shapeId == StyleConstants.FILLED_SQUARE ||
         shapeId == StyleConstants.FILLED_DIAMOND)
      {
         return true;
      }

      return false;
   }

   /**
    * Set marker symbol style.
    */
   public void setMarkerStyle(STMarkerStyle.Enum mstyle) {
      this.markerStyle = mstyle;
   }

   /**
    * Get marker symbol style.
    */
   public STMarkerStyle.Enum getMarkerStyle() {
      if(markerStyle == STMarkerStyle.NONE && shapeId != StyleConstants.NIL) {
         return XSSFChartUtil.getMarkerStyle(shapeId);
      }

      return markerStyle;
   }

   private int size = 5;
   private int shapeId = StyleConstants.NIL;
   private STMarkerStyle.Enum markerStyle = STMarkerStyle.NONE;
}
