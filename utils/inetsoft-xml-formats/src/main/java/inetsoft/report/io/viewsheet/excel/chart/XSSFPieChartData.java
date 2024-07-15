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

import inetsoft.uql.viewsheet.graph.GraphTypes;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.openxmlformats.schemas.drawingml.x2006.chart.*;

import java.util.List;

/**
 * Represents DrawingML pie charts.
 *
 * @version 12.2, 7/19/2015
 * @author InetSoft Technology Corp
 */
public class XSSFPieChartData extends XSSFChartData {

   /**
    * Constructor, create a XSSFPieChartData.
    */
   public XSSFPieChartData(XSSFChart chart, int style) {
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
    * Create and fill a piechart/pie3dchart with the added datas.
    *
    * @param category the category datasource
    * @param values   the values datasource
    */
   @Override
   public void fillChart(XDDFChartAxis category, XDDFValueAxis values) {
      XSSFChart xssfChart = getChart();
      CTPlotArea plotArea = xssfChart.getCTChart().getPlotArea();

      if(chartStyle == GraphTypes.CHART_PIE) {
         fillPieChart(plotArea);
      }
      else if(chartStyle == GraphTypes.CHART_DONUT) {
         fillDonutChart(plotArea);
      }
      else if(chartStyle == GraphTypes.CHART_3D_PIE) {
         CTView3D view3D = xssfChart.getCTChart().addNewView3D();
         view3D.addNewRotX().setVal((byte) 30);
         view3D.addNewHPercent().setVal(72);
         view3D.addNewDepthPercent().setVal(100);
         view3D.addNewPerspective().setVal((short) 0);
         fillPie3DChart(plotArea);
      }
   }

   /**
    * Create and fill a piechart with the added datas.
    */
   private void fillPieChart(CTPlotArea plotArea) {
      CTPieChart pieChart = plotArea.addNewPieChart();
      pieChart.setVaryColors(XSSFChartUtil.getVaryColor());

      for(Series s : series) {
         ((Serie) s).addToChart(pieChart);
      }
   }

   /**
    * Create and fill a doughnutchart with the added datas.
    */
   private void fillDonutChart(CTPlotArea plotArea) {
      CTDoughnutChart doughnutChart = plotArea.addNewDoughnutChart();
      doughnutChart.setVaryColors(XSSFChartUtil.getVaryColor());
      doughnutChart.setHoleSize(XSSFChartUtil.getHoleSize());

      for(Series s : series) {
         ((Serie) s).addToChart(doughnutChart);
      }
   }

   /**
    * Create and fill a pie3dchart with the added datas.
    */
   private void fillPie3DChart(CTPlotArea plotArea) {
      CTPie3DChart pieChart = plotArea.addNewPie3DChart();
      pieChart.setVaryColors(XSSFChartUtil.getVaryColor());

      for(Series s : series) {
         ((Serie) s).addToChart(pieChart);
      }
   }

   class Serie extends AbstractXSSFChartSerie0 {
      /**
       * Constructor, create a new Serie for CTLineChart.
       * @param id the serie id.
       * @param order the serie order.
       * @param sinfo the serie info.
       * @param category the categories datasource
       * @param values the values datasource
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
      protected void addToChart(CTPieChart ctPieChart) {
         CTPieSer ctPieSer = ctPieChart.addNewSer();
         addSerieContent(ctPieSer);
      }

      /**
       * Add current serie to target barchart.
       */
      protected void addToChart(CTDoughnutChart ctDoughnutChart) {
         CTPieSer ctPieSer = ctDoughnutChart.addNewSer();
         addSerieContent(ctPieSer);
      }

      /**
       * Add current serie to target barchart.
       */
      protected void addToChart(CTPie3DChart ctPie3DChart) {
         CTPieSer ctPieSer = ctPie3DChart.addNewSer();
         addSerieContent(ctPieSer);
      }

      /**
       * Add the serie content.
       */
      private void addSerieContent(CTPieSer ctPieSer) {
         this.series = ctPieSer;
         ctPieSer.addNewIdx().setVal(id);
         ctPieSer.addNewOrder().setVal(order);
         ctPieSer.addNewExplosion().setVal(serieInfo.isExploded() ? 5 : 0);

         CTAxDataSource catDS = ctPieSer.addNewCat();
         XSSFChartUtil.buildAxDataSource(catDS, category);
         CTNumDataSource valueDS = ctPieSer.addNewVal();
         XSSFChartUtil.buildNumDataSource(valueDS, values);

         if(serieInfo != null) {
            ctPieSer.addNewTx();
            setTitle(serieInfo.getTitle(), null);
            ctPieSer.setTx(getSeriesText());

            if(serieInfo.isShowValue()) {
               ctPieSer.setDLbls(getDataLabelsProperties());
            }

            CTDPt[] dPts = getDataPointsProperties();

            if(dPts != null) {
               ctPieSer.setDPtArray(dPts);
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

      private CTPieSer series;

      private final XDDFDataSource<?> category;
      private final XDDFNumericalDataSource<? extends Number> values;
   }
}
