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
 * Represents DrawingML area charts.
 *
 * @version 12.2, 7/19/2015
 * @author InetSoft Technology Corp
 */
public class XSSFAreaChartData extends XSSFChartData {
   /**
    * Constructor, create a XSSFAreaChartData.
    *
    * @param style current chart style.
    */
   public XSSFAreaChartData(XSSFChart chart, int style) {
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
    * Create and fill a areachart with the added datas.
    *
    * @param category the category datasource
    * @param values   the values datasource
    */
   @Override
   public void fillChart(XDDFChartAxis category, XDDFValueAxis values) {
      XSSFChart xssfChart = getChart();
      CTPlotArea plotArea = xssfChart.getCTChart().getPlotArea();
      CTAreaChart areaChart = plotArea.addNewAreaChart();
      areaChart.setVaryColors(XSSFChartUtil.getVaryColor());

      CTGrouping grouping = areaChart.addNewGrouping();
      boolean isStack = chartStyle == GraphTypes.CHART_AREA_STACK;
      grouping.setVal(isStack ? STGrouping.STACKED : STGrouping.STANDARD);
      CTChart ctchart = xssfChart.getCTChart();

      // for blank data, use line connect the data points as our product does.
      if(!ctchart.isSetDispBlanksAs()) {
         CTDispBlanksAs dispBlanksAs = CTDispBlanksAs.Factory.newInstance();
         dispBlanksAs.setVal(STDispBlanksAs.GAP);
         ctchart.setDispBlanksAs(dispBlanksAs);
      }

      for(Series s : series) {
         ((Serie) s).addToChart(areaChart);
      }

      areaChart.addNewAxId().setVal(category.getId());
      areaChart.addNewAxId().setVal(values.getId());
   }

   class Serie extends AbstractXSSFChartSerie0 {
      /**
       * Constructor, create a new Serie for CTLineChart.
       *
       * @param id         the serie id.
       * @param order      the serie order.
       * @param sinfo      the serie info.
       * @param category the category datasource
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
       * Add current serie to target linechart.
       */
      protected void addToChart(CTAreaChart ctAreaChart) {
         CTAreaSer ctAreaSer = ctAreaChart.addNewSer();
         this.series = ctAreaSer;
         ctAreaSer.addNewIdx().setVal(id);
         ctAreaSer.addNewOrder().setVal(order);

         CTAxDataSource catDS = ctAreaSer.addNewCat();
         XSSFChartUtil.buildAxDataSource(catDS, category);
         CTNumDataSource valueDS = ctAreaSer.addNewVal();
         XSSFChartUtil.buildNumDataSource(valueDS, values);

         if(serieInfo != null) {
            ctAreaSer.addNewTx();
            setTitle(serieInfo.getTitle(), null);
            ctAreaSer.setTx(getSeriesText());
         }

         if(serieInfo != null) {
            CTShapeProperties spPr = getDefaultShapeProperties();

            if(spPr != null) {
               ctAreaSer.setSpPr(spPr);
            }

            if(serieInfo.isShowValue()) {
               ctAreaSer.setDLbls(getDataLabelsProperties());
            }

            CTDPt[] dPts = getDataPointsProperties();

            if(dPts != null) {
               ctAreaSer.setDPtArray(dPts);
            }

            int trendline = serieInfo.getTrendline();

            if(trendline > 0) {
               CTTrendline ctTreandLine = ctAreaSer.addNewTrendline();
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

      private CTAreaSer series;

      private final XDDFDataSource<?> category;
      private final XDDFNumericalDataSource<? extends Number> values;
   }
}
