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
import inetsoft.uql.viewsheet.graph.GraphTypes;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.openxmlformats.schemas.drawingml.x2006.chart.*;
import org.openxmlformats.schemas.drawingml.x2006.main.*;

import java.awt.*;
import java.util.List;

/**
 * Represents DrawingML line charts.
 *
 * @version 12.2, 7/19/2015
 * @author InetSoft Technology Corp
 */
public class XSSFLineChartData extends XSSFChartData {
   /**
    * Constructor, create a XSSFLineChartData.
    *
    * @param style current chart style.
    */
   public XSSFLineChartData(XSSFChart chart, int style) {
      super(chart, style);
   }

   @Override
   public Serie addSeries(XDDFDataSource<?> category,
                          XDDFNumericalDataSource<? extends Number> values,
                          SerieInfo sinfo)
   {
      int size = series.size();
      return addSeries(size, size, sinfo, category, values);
   }

   /**
    * @param id the serie id.
    * @param order the serie order.
    * @param sinfo the serie info.
    * @param category the categories datasource
    * @param values the values datasource
    */
   public Serie addSeries(int id, int order, SerieInfo sinfo,
                          XDDFDataSource<?> category,
                          XDDFNumericalDataSource<? extends Number> values)
   {
      Serie added = new Serie(id, order, sinfo, category, values);
      series.add(added);
      return added;
   }

   /**
    * Create and fill a linechart with the added datas.
    *
    * @param category the category datasource
    * @param values   the values datasource
    */
   @Override
   public void fillChart(XDDFChartAxis category, XDDFValueAxis values) {
      XSSFChart xssfChart = getChart();
      CTPlotArea plotArea = xssfChart.getCTChart().getPlotArea();
      CTLineChart lineChart = plotArea.addNewLineChart();
      lineChart.setVaryColors(XSSFChartUtil.getVaryColor());

      CTGrouping grouping = lineChart.addNewGrouping();
      grouping.setVal(STGrouping.STANDARD);
      CTChart ctchart = xssfChart.getCTChart();

      // for blank data, use line connect the data points as our product does.
      if((GraphTypes.isLine(chartStyle) || GraphTypes.isPoint(chartStyle)) &&
         !ctchart.isSetDispBlanksAs())
      {
         CTDispBlanksAs dispBlanksAs = CTDispBlanksAs.Factory.newInstance();
         dispBlanksAs.setVal(STDispBlanksAs.SPAN);
         ctchart.setDispBlanksAs(dispBlanksAs);
      }

      // here we use line pretend to stock/candle chart, then cannot set
      // different color for every pretend vo, so we use the first vo color
      // in the vs chart as the vos color.
      if(GraphTypes.isStock(chartStyle) || GraphTypes.isCandle(chartStyle)) {
         Color color = getDefaultColor();
         lineChart.setHiLowLines(getHiLowLines(color));

         if(GraphTypes.isCandle(chartStyle)) {
            lineChart.setUpDownBars(getUpDownBars(color));
         }
      }

      for(Series s : series) {
         ((Serie) s).addToChart(lineChart);
      }

      lineChart.addNewAxId().setVal(category.getId());
      lineChart.addNewAxId().setVal(values.getId());
   }

   /**
    * Get default color for stock and candle chart.
    */
   private Color getDefaultColor() {
      Color color = null;

      if(series.size() != 0) {
         SerieInfo sinfo = ((Serie) series.get(0)).getSerieInfo();

         if(sinfo != null && sinfo.getDefaultDataPointInfo() != null) {
            color = sinfo.getDefaultDataPointInfo().getFillColor();
         }
      }

      return color != null ? color : Color.black;
   }

   /**
    * Get hiLowLines for stock and candle chart.
    */
   private CTChartLines getHiLowLines(Color color) {
      CTChartLines hiLowLines = CTChartLines.Factory.newInstance();
      CTShapeProperties spPr = hiLowLines.addNewSpPr();
      spPr.addNewNoFill();

      spPr.setLn(getLineProperties(color));

      return hiLowLines;
   }

   /**
    * Get upDownBars for candle chart.
    */
   private CTUpDownBars getUpDownBars(Color color) {
      CTUpDownBars upDownBars = CTUpDownBars.Factory.newInstance();
      CTUpDownBar upBars = upDownBars.addNewUpBars();
      CTUpDownBar downBars = upDownBars.addNewDownBars();
      upBars.setSpPr(getUpDownBarsShapeProperties(color, true));
      downBars.setSpPr(getUpDownBarsShapeProperties(color, false));

      return upDownBars;
   }

   /**
    * Get shape properties of upDownBars for candle chart.
    */
   private CTShapeProperties getUpDownBarsShapeProperties(Color color,
                                                          boolean up)
   {
      CTShapeProperties spPr = CTShapeProperties.Factory.newInstance();
      spPr.setSolidFill(XSSFChartUtil.getSolidFill(up ? Color.white : color));
      spPr.setLn(getLineProperties(color));

      return spPr;
   }

   /**
    * Get line properties for stock and candle chart.
    */
   private CTLineProperties getLineProperties(Color color) {
      CTLineProperties ln = CTLineProperties.Factory.newInstance();
      ln.setW(XSSFChartUtil.getLineWidth(StyleConstants.THIN_LINE));
      ln.setSolidFill(XSSFChartUtil.getSolidFill(color));
      ln.addNewPrstDash().setVal(STPresetLineDashVal.SOLID);

      return ln;
   }

   class Serie extends AbstractXSSFChartSerie0 {
      /**
       * Constructor, create a new Serie for CTLineChart.
       *
       * @param id         the serie id.
       * @param order      the serie order.
       * @param sinfo      the serie info.
       * @param category   the category datasource
       * @param values     the values datasource
       */
      protected Serie(int id, int order, SerieInfo sinfo,
                      XDDFDataSource<?> category,
                      XDDFNumericalDataSource<? extends Number> values)
      {
         super(category, values);
         this.id = id;
         this.order = order;
         this.serieInfo = sinfo;
         this.category = category;
         this.values = values;
      }

      /**
       * @return the serie info.
       */
      public SerieInfo getSerieInfo() {
         return serieInfo;
      }

      /**
       * Add current serie to target linechart.
       */
      protected void addToChart(CTLineChart ctLineChart) {
         CTLineSer ctLineSer = ctLineChart.addNewSer();
         this.series = ctLineSer;
         ctLineSer.addNewIdx().setVal(id);
         ctLineSer.addNewOrder().setVal(order);

         CTAxDataSource catDS = ctLineSer.addNewCat();
         XSSFChartUtil.buildAxDataSource(catDS, category);
         CTNumDataSource valueDS = ctLineSer.addNewVal();
         XSSFChartUtil.buildNumDataSource(valueDS, values);

         if(serieInfo != null) {
            ctLineSer.addNewTx();
            setTitle(serieInfo.getTitle(), null);
            ctLineSer.setTx(getSeriesText());

            CTShapeProperties spPr = getDefaultShapeProperties();

            if(spPr != null) {
               ctLineSer.setSpPr(spPr);
            }

            CTMarker marker = getDefaultMarker();

            if(marker != null) {
               ctLineSer.setMarker(marker);
            }

            if(serieInfo.isShowValue()) {
               ctLineSer.setDLbls(getDataLabelsProperties());
            }

            CTDPt[] dPts = getDataPointsProperties();

            if(dPts != null) {
               ctLineSer.setDPtArray(dPts);
            }

            int trendline = serieInfo.getTrendline();

            if(trendline > 0) {
               CTTrendline ctTreandLine = ctLineSer.addNewTrendline();
               Color trendLineColor = serieInfo.getTrendLineColor();
               int trendLineStyle = serieInfo.getTrendLineStyle();
               XSSFChartUtil.propertyTrendLine(ctTreandLine, trendline,
                                               trendLineColor, trendLineStyle);
            }
         }
      }

      @Override
      protected CTAxDataSource getAxDS() {
         return series.getCat();
      }

      @Override
      protected CTNumDataSource getNumDS() {
         return series.getVal();
      }

      @Override
      protected CTSerTx getSeriesText() {
         return series.getTx();
      }

      @Override
      protected List<CTDPt> getDPtList() {
         return series.getDPtList();
      }

      private CTLineSer series;

      private final XDDFDataSource<?> category;
      private final XDDFNumericalDataSource<? extends Number> values;
   }
}
