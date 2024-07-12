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
import java.awt.Color;
import java.awt.Font;
import java.text.Format;
import java.util.HashMap;
import java.util.Set;

/**
 * This class contains all the information of a chart serie.
 *
 * @version 12.2, 7/19/2015
 * @author InetSoft Technology Corp
 */
public class SerieInfo {
   public SerieInfo() {
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public String getTitle() {
      return title == null ? "" : title;
   }

   /**
    * Set serie style.
    */
   public void setStyle(int style) {
      this.style = style;
   }

   /**
    * Get serie style.
    */
   public int getStyle() {
      return style;
   }

   /**
    * Set if show in secondaryy.
    */
   public void setSecondaryY(boolean secondaryy) {
      this.secondaryy = secondaryy;
   }

   /**
    * Check if show in secondaryy.
    */
   public Boolean isSecondaryY() {
      return secondaryy;
   }

   /**
    * Get default data point info.
    */
   public DataPointInfo getDefaultDataPointInfo() {
      return defaultDataInfo;
   }

   /**
    * Set default data point info.
    */
   public void setDefaultDataPointInfo(DataPointInfo dinfo) {
      this.defaultDataInfo = dinfo;
   }

   /**
    * Add data point info.
    *
    * @param idx  data point index.
    * @param info data point info.
    */
   public void addDataPointInfo(int idx, DataPointInfo info) {
      dataPointMap.put(idx, info);
   }

   /**
    * Set data point info.
    *
    * @param data point index.
    */
   public DataPointInfo getDataPointInfo(int idx) {
      return dataPointMap.get(idx);
   }

   public Set getDataPointMapKeySet() {
      return dataPointMap.keySet();
   }

   /**
    * Check if show data label.
    */
   public boolean isShowValue() {
      return showValue;
   }

   /**
    * Set if show data label.
    */
   public void setShowValue(boolean showVal) {
      this.showValue = showVal;
   }

   /**
    * Set data label foreground color.
    */
   public void setDataLabelColor(Color lcolor) {
      this.dlColor = lcolor;
   }

   /**
    * Get data label foreground color.
    */
   public Color getDataLabelColor() {
      return dlColor;
   }

   /**
    * Set data label background color.
    */
   public void setDataLabelFillColor(Color lcolor) {
      this.dlFillColor = lcolor;
   }

   /**
    * Get data label background color.
    */
   public Color getDataLabelFillColor() {
      return dlFillColor;
   }

   /**
    * Set data label font.
    */
   public void setDataLabelFont(Font font) {
      this.dlFont = font;
   }

   /**
    * Get data label font.
    */
   public Font getDataLabelFont() {
      return dlFont;
   }

   /**
    * Set data label number format.
    */
   public void setDataLabelFormat(Format fmt) {
      this.dlFormat = fmt;
   }

   /**
    * Set data label number format.
    */
   public Format getDataLabelFormat() {
      return dlFormat;
   }

   /**
    * Get the trendline.
    */
   public int getTrendline() {
      return trendline;
   }

   /**
    * Set the trendline.
    *
    * @param trendline the trendline.
    */
   public void setTrendline(int trendline) {
      this.trendline = trendline;
   }

   /**
    * Get the color of the trend line.
    */
   public Color getTrendLineColor() {
      return trendLineColor;
   }

   /**
    * Set the color of the trend line.
    */
   public void setTrendLineColor(Color trendLineColor) {
      this.trendLineColor = trendLineColor;
   }

   /**
    * Get the line style of the trend line.
    */
   public int getTrendLineStyle() {
      return trendLineStyle;
   }

   /**
    * Set the line style of the trend line.
    */
   public void setTrendLineStyle(int style) {
      this.trendLineStyle = style;
   }

   /**
    * Check if it is exploded.
    */
   public boolean isExploded() {
      return exploded;
   }

   /**
    * Set whether it can be exploded.
    */
   public void setExploded(boolean exploded) {
      this.exploded = exploded;
   }

   /**
    * Get the effect flag for the chart.
    *
    * @return true if effect is to be applied.
    */
   public boolean isGlossyEffect() {
      return glossyEffect;
   }

   /**
    * Set the effect flag for the chart.
    *
    * @param effect true to apply effect on chart, false otherwise.
    */
   public void setGlossyEffect(boolean glossyEffect) {
      this.glossyEffect = glossyEffect;
   }

   /**
    * Check if this chart should be set to sparkline mode.
    */
   public boolean isSparkline() {
      return sparkline;
   }

   /**
    * Set if this chart should be set to sparkline mode.
    */
   public void setSparkline(boolean sparkline) {
      this.sparkline = sparkline;
   }

   /**
    * Get the alpha value.
    */
   public double getAlpha() {
      return alpha;
   }

   /**
    * Set the alpha value.
    *
    * @param alpha value from 0 to 1, or -1 to use the default.
    */
   public void setAlpha(double alpha) {
      this.alpha = alpha;
   }

   private int style = -1;
   private String title;
   private boolean showValue;
   private boolean secondaryy;
   private boolean exploded;
   private DataPointInfo defaultDataInfo;

   // chart data labels in our product have some format in one chart,
   // so save data label format info in SerieInfo.
   private Color dlFillColor;
   private Color dlColor;
   private Font dlFont;
   private Format dlFormat;

   private int trendline = StyleConstants.NONE;
   private Color trendLineColor = null;
   private int trendLineStyle = StyleConstants.THIN_LINE;
   private boolean glossyEffect = false;
   private boolean sparkline = false;
   private double alpha = -1;

   private HashMap<Integer, DataPointInfo> dataPointMap = new HashMap<>();
}
