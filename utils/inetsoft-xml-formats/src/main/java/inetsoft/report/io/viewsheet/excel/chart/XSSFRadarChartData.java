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

import java.util.List;

/**
 * Represents DrawingML radar charts.
 *
 * @version 12.2, 7/19/2015
 * @author InetSoft Technology Corp
 */
public class XSSFRadarChartData extends XSSFChartData {
   /**
    * Constructor, create a XSSFRadarChartData.
    *
    * @param style current chart style.
    */
   public XSSFRadarChartData(XSSFChart chart, int style) {
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
      CTRadarChart radarChart = plotArea.addNewRadarChart();
      radarChart.setVaryColors(XSSFChartUtil.getVaryColor());

      CTRadarStyle type = radarChart.addNewRadarStyle();
      boolean isfilled = chartStyle == GraphTypes.CHART_FILL_RADAR;
      type.setVal(isfilled ? STRadarStyle.FILLED : STRadarStyle.STANDARD);

      for(Series s : series) {
         ((Serie) s).addToChart(radarChart);
      }

      radarChart.addNewAxId().setVal(category.getId());
      radarChart.addNewAxId().setVal(values.getId());
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
       * Add current serie to target radarchart.
       */
      protected void addToChart(CTRadarChart ctRadarChart) {
         CTRadarSer ctRadarSer = ctRadarChart.addNewSer();
         this.series = ctRadarSer;
         ctRadarSer.addNewIdx().setVal(id);
         ctRadarSer.addNewOrder().setVal(order);

         CTAxDataSource catDS = ctRadarSer.addNewCat();
         XSSFChartUtil.buildAxDataSource(catDS, category);
         CTNumDataSource valueDS = ctRadarSer.addNewVal();
         XSSFChartUtil.buildNumDataSource(valueDS, values);

         if(serieInfo != null) {
            ctRadarSer.addNewTx();
            setTitle(serieInfo.getTitle(), null);
            ctRadarSer.setTx(getSeriesText());

            if(serieInfo.isShowValue()) {
               ctRadarSer.setDLbls(getDataLabelsProperties());
            }

            CTShapeProperties spPr = getDefaultShapeProperties();

            if(spPr != null) {
               ctRadarSer.setSpPr(spPr);
            }

            CTMarker marker = getDefaultMarker();

            if(marker != null) {
               ctRadarSer.setMarker(marker);
            }

            CTDPt[] dPts = getDataPointsProperties();

            if(dPts != null) {
               ctRadarSer.setDPtArray(dPts);
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

      private CTRadarSer series;

      private final XDDFDataSource<?> category;
      private final XDDFNumericalDataSource<? extends Number> values;
   }
}
