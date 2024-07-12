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

import inetsoft.uql.viewsheet.graph.GraphTypes;

import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.openxmlformats.schemas.drawingml.x2006.chart.*;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties;

import java.awt.Color;
import java.util.List;

/**
 * Represents DrawingML bar charts.
 *
 * @version 12.2, 7/19/2015
 * @author InetSoft Technology Corp
 */
public class XSSFBarChartData extends XSSFChartData {
   /**
    * Constructor, create a XSSFBarChartData.
    *
    * @param style current chart style.
    */
   public XSSFBarChartData(XSSFChart chart, int style) {
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
    * Create and fill a barchart/bar3dchart with the added datas.
    *
    * @param category the category datasource
    * @param values   the values datasource
    */
   @Override
   public void fillChart(XDDFChartAxis category, XDDFValueAxis values) {
      XDDFChart xssfChart = getChart();
      CTPlotArea plotArea = xssfChart.getCTChart().getPlotArea();

      if(chartStyle == GraphTypes.CHART_BAR ||
         chartStyle == GraphTypes.CHART_BAR_STACK ||
         chartStyle == GraphTypes.CHART_WATERFALL ||
         chartStyle == GraphTypes.CHART_PARETO)
      {
         fillBarChart(plotArea, category, values);
      }

      if(chartStyle == GraphTypes.CHART_3D_BAR ||
         chartStyle == GraphTypes.CHART_3D_BAR_STACK)
      {
         prepareBar3DChart(xssfChart);
         fillBar3DChart(plotArea, category, values);
      }
   }

   /**
    * prepare 3D bar chart to property some attributes.
    */
   private void prepareBar3DChart(XDDFChart xssfChart) {
      CTChart ctchart = xssfChart.getCTChart();
      CTView3D view3D = ctchart.addNewView3D();
      view3D.addNewRotX().setVal((byte) 15);
      view3D.addNewRotY().setVal((byte) 20);
      view3D.addNewRAngAx().setVal(true);

      CTShapeProperties sppr = CTShapeProperties.Factory.newInstance();
      sppr.setSolidFill(XSSFChartUtil.getSolidFill(Color.white));
      ctchart.addNewFloor().setSpPr(sppr);
      ctchart.addNewSideWall().setSpPr(sppr);
   }

   /**
    * Create and fill a barchart with the added datas.
    */
   private void fillBarChart(CTPlotArea plotArea, XDDFChartAxis category, XDDFValueAxis values) {
      CTBarChart barChart = plotArea.addNewBarChart();
      barChart.setVaryColors(XSSFChartUtil.getVaryColor());

      CTBarGrouping grouping = barChart.addNewGrouping();
      boolean isStack = isStack();
      grouping.setVal(isStack ?
         STBarGrouping.STACKED : STBarGrouping.CLUSTERED);

      if(isStack) {
         // set overlap to 100, then the bars will overlapped like our product.
         barChart.addNewOverlap().setVal((byte) 100);
      }

      CTBarDir barDir = barChart.addNewBarDir();
      barDir.setVal(isSwapped() ? STBarDir.BAR : STBarDir.COL);

      for(Series s : series) {
         ((Serie) s).addToChart(barChart);
      }

      barChart.addNewAxId().setVal(category.getId());
      barChart.addNewAxId().setVal(values.getId());
   }

   /**
    * Create and fill a bar3dchart with the added datas.
    */
   private void fillBar3DChart(CTPlotArea plotArea, XDDFChartAxis category, XDDFValueAxis values) {
      CTBar3DChart barChart = plotArea.addNewBar3DChart();
      barChart.setVaryColors(XSSFChartUtil.getVaryColor());

      CTBarGrouping grouping = barChart.addNewGrouping();
      grouping.setVal(isStack() ?
         STBarGrouping.STACKED : STBarGrouping.CLUSTERED);

      CTBarDir barDir = barChart.addNewBarDir();
      barDir.setVal(isSwapped() ? STBarDir.BAR: STBarDir.COL);

      for(Series s : series) {
         ((Serie) s).addToChart(barChart);
      }

      barChart.addNewAxId().setVal(category.getId());
      barChart.addNewAxId().setVal(values.getId());
   }

   private boolean isStack() {
      if(GraphTypes.isPareto(chartStyle) ||
         GraphTypes.isWaterfall(chartStyle))
      {
         return true;
      }

      return GraphTypes.isStack(chartStyle);
   }

   /**
    * Return if x/y swapped.
    */
   public boolean isSwapped() {
      return swap;
   }

   /**
    * Set if x/y swapped.
    */
   public void setSwapped(boolean swap) {
      this.swap = swap;
   }

   class Serie extends AbstractXSSFChartSerie0 {
      /**
       * Constructor, create a new Serie for CTLineChart.
       *
       * @param id       the serie id.
       * @param order    the serie order.
       * @param sinfo    the serie info.
       * @param category the category datasource
       * @param values   the values datasource
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
       * Add current serie to target barchart.
       */
      protected void addToChart(CTBarChart ctBarChart) {
         CTBarSer ctBarSer = ctBarChart.addNewSer();
         addSerieContent(ctBarSer);
      }

      /**
       * Add current serie to target barchart.
       */
      protected void addToChart(CTBar3DChart ctBar3DChart) {
         CTBarSer ctBarSer = ctBar3DChart.addNewSer();
         addSerieContent(ctBarSer);
      }

      /**
       * Add the serie content.
       */
      public void addSerieContent(CTBarSer ctBarSer) {
         this.series = ctBarSer;
         ctBarSer.addNewIdx().setVal(id);
         ctBarSer.addNewOrder().setVal(order);

         CTAxDataSource catDS = series.addNewCat();
         XSSFChartUtil.buildAxDataSource(catDS, category);
         CTNumDataSource valueDS = series.addNewVal();
         XSSFChartUtil.buildNumDataSource(valueDS, values);

         if(serieInfo != null) {
            ctBarSer.addNewTx();
            setTitle(serieInfo.getTitle(), null);
            ctBarSer.setTx(getSeriesText());

            CTShapeProperties spPr = getDefaultShapeProperties();

            if(spPr != null) {
               series.setSpPr(spPr);
            }

            if(serieInfo.isShowValue()) {
               series.setDLbls(getDataLabelsProperties());
            }

            CTDPt[] dPts = getDataPointsProperties();

            if(dPts != null) {
               series.setDPtArray(dPts);
            }

            int trendline = serieInfo.getTrendline();

            if(trendline > 0) {
               CTTrendline ctTreandLine = series.addNewTrendline();
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

      private CTBarSer series;

      private final XDDFDataSource<?> category;
      private final XDDFNumericalDataSource<? extends Number> values;
   }

   private boolean swap;
}
