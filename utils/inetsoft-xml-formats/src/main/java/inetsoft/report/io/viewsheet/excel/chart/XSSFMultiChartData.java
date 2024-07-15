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

import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.XSSFChart;

import java.util.HashMap;
import java.util.Set;

/**
 * Represents DrawingML multi style charts.
 *
 * @version 12.2, 7/19/2015
 * @author InetSoft Technology Corp
 */
public class XSSFMultiChartData extends XSSFChartData {
   /**
    * Constructor, create a XSSFBarChartData.
    *
    * @param chart
    */
   public XSSFMultiChartData(XSSFChart chart) {
      super(chart);
      dataMap = new HashMap<>();
   }

   @Override
   public AbstractXSSFChartSerie0 addSeries(XDDFDataSource<?> category,
                          XDDFNumericalDataSource<? extends Number> values,
                          SerieInfo sinfo)
   {
      final int size = series.size();
      return addSeries(size, size, sinfo, category, values);
   }

   /**
    * @param id the serie id.
    * @param order the serie order.
    * @param sinfo the serie info.
    * @param category the categories datasource
    * @param values the values datasource
    */
   public AbstractXSSFChartSerie0 addSeries(int id, int order, SerieInfo sinfo,
                          XDDFDataSource<?> category,
                          XDDFNumericalDataSource<? extends Number> values)
   {
      int style = sinfo.getStyle();
      AbstractXSSFChartSerie0 added;

      if(!dataMap.containsKey(style)) {
         XSSFChartData chartdata = XSSFChartUtil.createChartData(getChart(), style, false);
         dataMap.put(style, chartdata);
         added = chartdata.addSeries(id, order, sinfo, category, values);
      }
      else {
         added = dataMap.get(style).addSeries(id, order, sinfo, category, values);
      }

      series.add(added);
      return added;
   }

   /**
    * Create and fill chart with the added datas.
    *
    * @param category the category datasource
    * @param values   the values datasource
    */
   @Override
   public void fillChart(XDDFChartAxis category, XDDFValueAxis values) {
      if(dataMap.size() == 0) {
         return;
      }

      Set<Integer> styles = dataMap.keySet();

      for(Integer style : styles) {
         XSSFChartData chartdata = dataMap.get(style);

         if(chartdata instanceof XSSFBarChartData) {
            ((XSSFBarChartData) chartdata).setSwapped(isSwapped());
         }

         chartdata.fillChart(category, values);
      }
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

   private boolean swap;

   private final HashMap<Integer, XSSFChartData> dataMap;
}
