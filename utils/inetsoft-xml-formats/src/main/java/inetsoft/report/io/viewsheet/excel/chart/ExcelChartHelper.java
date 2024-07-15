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

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.data.*;
import inetsoft.graph.element.*;
import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.geometry.LineGeometry;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.*;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.graph.BrushDataSet;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.viewsheet.graph.AllChartAggregateRef;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.util.*;
import org.apache.poi.xddf.usermodel.chart.*;
import org.openxmlformats.schemas.drawingml.x2006.chart.STMarkerStyle;
import org.openxmlformats.schemas.drawingml.x2006.main.STCompoundLine;
import org.openxmlformats.schemas.drawingml.x2006.main.STPresetLineDashVal;

import java.awt.Color;
import java.awt.Point;
import java.math.BigDecimal;
import java.text.*;
import java.util.*;

/**
 * Chart exporting helper. Used by XSSFChartElement.
 *
 * @version 12.2, 7/19/2015
 * @author InetSoft Technology Corp
 */
public class ExcelChartHelper {
   /**
    * Constructor, create a new VSExportChartHelper for chart export.
    * @param dataset the chart dataset.
    * @param vgraph the this level vgraph.
    * @param root the top level vgraph.
    * @param descriptor the chart descriptor.
    * @param cinfo chart info.
    * @param isbrush if chart is brushed.
    * @param hasBrush if has brush(brush or part brush) or not.
    * @param brushAdjust the brush sub data actually row count.
    */
   public ExcelChartHelper(DataSet dataset, DataSet odataset, DataSet rdataset,
      VGraph vgraph, VGraph root, ChartDescriptor descriptor, ChartInfo cinfo,
      boolean isbrush, boolean hasBrush, int brushAdjust)
   {
      this.dataset = dataset;
      this.odataset = odataset;
      this.rdataset = rdataset;
      this.vgraph = vgraph;
      this.root = root;
      this.descriptor = descriptor;
      this.cinfo = cinfo;
      this.isbrush = isbrush;
      this.hasBrush = hasBrush;
      this.brushAdjust = brushAdjust;

      initVar();
      initInnerAxisMap();

      DataSet realDataset = isbrush ? odataset : dataset;

      // optimization, avoid looping through dataset to find original row from base row (45454).
      if(realDataset instanceof SortedDataSet) {
         int cnt = realDataset.getRowCount();
         int cnt0 = ((SortedDataSet) realDataset).getRowCount0();
         baseRowMap = new int[cnt];

         for(int i = 0; i < cnt; i++) {
            int base = i < cnt0 ? ((SortedDataSet) realDataset).getBaseRow(i) : i;
            baseRowMap[base] = i;
         }
      }

      data = createChartData();
      stacked = dimensions.size() >= 2;

      if(stacked) {
         for(int i = 0; i < dimensions.size(); i++) {
            if(allMStack) {
               int ocol = getCol(true, i);
               String header = odataset.getHeader(ocol);
               header = BrushDataSet.ALL_HEADER_PREFIX + header;
               allMStack = odataset.indexOfHeader(header) >= 0;
            }
         }
      }

      int chartStyle = getChartStyle();

      for(int i = 0; i < getSeriesCount(); i++) {
         for(int j = 0; j < vgraph.getVisualCount(); j++) {
            Visualizable vi = vgraph.getVisual(j);

            if(!(vi instanceof ElementVO)) {
               continue;
            }

            ElementVO vo = (ElementVO) vi;
            int idx = getColIndex(vo);

            if(idx < 0) {
               continue;
            }

            int[] rows = getElementRows(vo, chartStyle);

            LOOP:
            for(int m = 0; m < rows.length; m++) {
               int row = rows[m];
               int seriesIdx = -1;
               int categoryIdx = -1;

               if(GraphTypes.isRadar(chartStyle) ||
                  GraphTypes.isPie(chartStyle))
               {
                  Object[] objs = new Object[1];
                  objs[0] = dataset.getHeader(getColIndex(vo));

                  int cateCnt = data.getCategoriesSize();
                  int scnt = getSeriesRefCnt();

                  // if only one category column and the column type is measure,
                  // means no dimension field, so don't use it to create
                  // categories data or series name.
                  if(cateCnt == 1 &&
                     dataset.isMeasure(dataset.getHeader(data.getCategoriesIndex(0))))
                  {
                     cateCnt = 0;
                  }

                  Object[] tmp = new Object[cateCnt + scnt];

                  for(int k = 0; k < cateCnt; k++) {
                     tmp[k] = dataset.getData(data.getCategoriesIndex(k), row);
                     tmp[k] = data.getText(tmp[k], data.getCategoriesIndex(k));
                  }

                  for(int s = 0; s < scnt; s++) {
                     tmp[s + cateCnt] = dataset.getData(data.seriesIdx.get(s), row);
                     tmp[s + cateCnt] = data.getText(tmp[s + cateCnt], data.seriesIdx.get(s));
                  }

                  // for radar chart,
                  // categories are numerical field name.
                  // series are (dims data) * (break by data) * (visual data).
                  if(GraphTypes.isRadar(chartStyle)) {
                     categoryIdx = data.getCategoryIndex(objs, new Tuple(objs));
                     seriesIdx = data.getSeriesIndex(new Tuple(tmp));
                  }
                  // for pie chart
                  // categories are (dims data) * (break by data) *(visual data)
                  // series are numerical field name
                  else if(GraphTypes.isPie(chartStyle)) {
                     categoryIdx = data.getCategoryIndex(tmp, new Tuple(tmp));
                     seriesIdx = data.getSeriesIndex(new Tuple(objs));
                  }
               }
               // for multi chart, serie design is same with bar chart.
               // special for multi chart:
               // 1. line and area type serie don't support measure serie field.
               // 2. different numerical field have different legend field.
               else if(needMultiData()) {
                  ChartMultiData mdata = (ChartMultiData) data;

                  if(mdata.ingoreRow(idx, row)) {
                     continue;
                  }

                  DataSet dataset0 = dataset;

                  if(cinfo.isSeparatedGraph() && row >= dataset.getRowCount()) {
                     dataset0 = rdataset;
                  }

                  Object[] tmp = new Object[data.getCategoriesSize()];

                  for(int k = 0; k < tmp.length; k++) {
                     tmp[k] = dataset0.getData(data.getCategoriesIndex(k), row);
                     tmp[k] = data.getText(tmp[k], data.getCategoriesIndex(k), false);
                  }

                  ArrayList list = new ArrayList();

                  for(int s = 0; s < getSeriesRefCnt(); s++) {
                     // if the serie field is not added for current numerical
                     // field, then the serie value in this row should be null
                     // else this row is not for current numerical field.
                     if(!mdata.containsSerie(idx, data.seriesIdx.get(s)) ||
                        mdata.unSupportLegend(idx, data.seriesIdx.get(s)))
                     {
                        continue;
                     }

                     Object obj = dataset0.getData(data.seriesIdx.get(s), row);
                     list.add(data.getText(obj, data.seriesIdx.get(s)));
                  }

                  list.add(dataset.getHeader(idx));
                  Tuple tuple = new Tuple(list.toArray());

                  seriesIdx = data.getSeriesIndex(tuple);
                  categoryIdx = data.getCategoryIndex(tmp, new Tuple(tmp));
               }
               // for bar/area/line/point/waterfall/pareto
               // categories are (dim0 data * dimn data)
               // series are (break by data) * (visual data) *
               // (numerical fields name)
               else {
                  int size = data.getSeriesRefCnt();
                  Object[] objs = new Object[size];
                  Object[] cates = null;

                  if(row < 0 || row >= dataset.getRowCount()) {
                     continue;
                  }

                  for(int n = 0; n < size; n++) {
                     objs[n] =
                        dataset.getData(data.seriesIdx.get(n), row);
                     objs[n] = data.getText(objs[n], data.seriesIdx.get(n));
                  }

                  cates = new Object[data.getCategoriesSize()];

                  for(int cidx = 0; cidx < cates.length; cidx++) {
                     cates[cidx] =
                        dataset.getData(data.getCategoriesIndex(cidx), row);
                     cates[cidx] = data.getText(cates[cidx],
                        data.getCategoriesIndex(cidx), false);
                  }

                  Object[] tmp = new Object[objs.length + 1];
                  System.arraycopy(objs, 0, tmp, 0, objs.length);
                  tmp[tmp.length - 1] = dataset.getHeader(getColIndex(vo));

                  seriesIdx = data.getSeriesIndex(new Tuple(tmp));
                  categoryIdx = data.getCategoryIndex(cates, new Tuple(cates));
               }

               if(seriesIdx == -1 || categoryIdx == -1 ||
                  data.datas.get(categoryIdx, seriesIdx) == null)
               {
                  continue;
               }

               Point pos = new Point(seriesIdx, categoryIdx);
               vsfmtMap.put(pos, row);
               index.put(pos, vo);
            }
         }
      }
   }

   /**
    * Get row array of the target elementvo.
    */
   protected int[] getElementRows(ElementVO vo, int chartStyle) {
      int[] rows = new int[0];

      if(vo == null || GraphTypes.isPareto(chartStyle) && vo instanceof LineVO) {
         return rows;
      }

      int idx = getColIndex(vo);

      if(idx < 0) {
         return rows;
      }

      if((GraphTypes.isArea(chartStyle) || GraphTypes.isLine(chartStyle))
         && (vo instanceof LineVO) || vo instanceof Pie3DVO)
      {
         int[] subrows = vo.getSubRowIndexes();
         rows = new int[subrows.length];

         // getSubRowIndexes returns the row indexes of the root dataset,
         // but the dataset may not be same with the root, so we need to
         // get the baserow here.
         for(int r = 0; r < subrows.length; r++) {
            rows[r] = baseRowMap != null ? baseRowMap[subrows[r]] : subrows[r];
         }
      }
      else {
         // the surow index is from the base dataset.
         int rowidx = vo.getSubRowIndex();
         rowidx = baseRowMap != null ? baseRowMap[rowidx] : rowidx;
         rows = new int[] {rowidx};
      }

      return rows;
   }

   /**
    * Initialize all the varibles.
    */
   private void initVar() {
      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable vi = vgraph.getVisual(i);

         if(vi instanceof ElementVO) {
            ElementVO elemVO = (ElementVO) vi;
            GraphElement elem = (GraphElement) elemVO.getGraphable();

            fields.addAll(Arrays.asList(elem.getDims()));
            fields.addAll(Arrays.asList(elem.getVars()));

            for(VisualFrame frame : elem.getVisualFrames()) {
               if(frame != null) {
                  String field = frame.getField();

                  if(field != null) {
                     fields.add(field);
                  }
               }
            }
         }
      }

      DataRef[] refs = cinfo == null ? new DataRef[0] : cinfo.getRTXFields();

      for(int i = 0; i < refs.length; i++) {
         if(refs[i] instanceof ChartAggregateRef) {
            inverted = true;
            break;
         }
      }

      if(inverted) {
         refs = cinfo.getRTYFields();

         for(int i = 0; i < refs.length; i++) {
            if(refs[i] instanceof ChartAggregateRef) {
               inverted = false;
               break;
            }
         }
      }

      refs = cinfo == null ? new DataRef[0] : cinfo.getRTFields();

      if(cinfo != null && !cinfo.isMultiStyles() &&
         cinfo.getRTChartType() != 0)
      {
         chartStyle = cinfo.getRTChartType();
      }
      else if(cinfo != null && cinfo.isMultiStyles()) {
         HashSet<Integer> chartFmt = new HashSet<>();
         HashSet<Integer> secChartFmt = new HashSet<>();

         for(int i = 0; i < refs.length; i++) {
            DataRef dref = refs[i];
            refsMap.put(GraphUtil.getName(dref), dref);
            ChartAggregateRef cref = null;

            if(dref instanceof ChartAggregateRef) {
               cref = (ChartAggregateRef) dref;
               int cType = cref.getRTChartType();
               String name = cref.getFullName();
               Object[] obj = new Object[]{name, cType};

               if(GraphTypes.isAuto(cType)) {
                  continue;
               }

               if(containsSecondaryY() && cref.isSecondaryY()) {
                  secChartStyles.add(obj);
               }
               else {
                  chartStyles.add(obj);
               }
            }
         }
      }

      dimensions = new ArrayList();
      aggregates = new ArrayList();
      // bug1336465717828, GraphVO need get children ElementVO.
      List visuals = GTool.getVOs(vgraph);

      for(int i = 0; i < refs.length; i++) {
         DataRef ref = refs[i];
         String name = GraphUtil.getName(ref);
         boolean found = !(ref instanceof ChartAggregateRef);

         if(!fields.contains(name)) {
            continue;
         }

         if(cinfo instanceof MergedChartInfo) {
            found = true;
         }

         for(Object vi : visuals) {
            if(vi instanceof ElementVO) {
               ElementVO vo = (ElementVO) vi;

               if(this.vo == null) {
                  this.vo = vo;
               }

               if(found) {
                  break;
               }

               String measure = ((ElementVO) vi).getMeasureName();
               String all = BrushDataSet.ALL_HEADER_PREFIX;

               if(measure == null || measure.replaceAll(all, "").equals(name)) {
                  found = true;
               }
            }
         }

         if(cinfo.isMultiStyles() && ref instanceof ChartAggregateRef) {
            if(chartStyle != -1 &&
               chartStyle != ((ChartAggregateRef) ref).getChartType()) {
               chartStyle = ((ChartAggregateRef) ref).getChartType();
            }
         }

         if(!found && (!GraphTypes.isPoint(chartStyle) ||
            dimensions.size() > 0))
         {
            continue;
         }

         for(int j = 0; j < dataset.getColCount(); j++) {
            if(name.equals(dataset.getHeader(j))) {
               Integer idx = j;

               if(ref instanceof ChartAggregateRef) {
                  if(!aggregates.contains(idx)) {
                     aggregates.add(idx);
                  }

                  if(chartStyle == -1) {
                     int type = ((ChartAggregateRef) ref).getChartType();

                     if(type != 0) {
                        chartStyle = type;
                     }
                  }
               }
               else {
                  if(!dimensions.contains(idx)) {
                     dimensions.add(idx);
                  }
               }

               break;
            }
         }
      }

      if(chartStyle == -1) {
         chartStyle = getChartStyleByGraph();
         chartStyle = chartStyle == -1 ? GraphTypes.CHART_BAR : chartStyle;
      }

      // if the chart type is waterfall or pareto, it is processed as stack bar
      // in GraphGenerator, so we will keep the same behaviors here
      else if(chartStyle == GraphTypes.CHART_WATERFALL ||
              chartStyle == GraphTypes.CHART_PARETO) {
         chartStyle = GraphTypes.CHART_BAR_STACK;
      }

      // if aggregate is empty, try to get the aggregate from vgraph
      if(aggregates.size() == 0 &&
         (chartStyle == GraphTypes.CHART_POINT ||
          chartStyle == GraphTypes.CHART_POINT_STACK))
      {
         getBindingFromVGraph(aggregates);
      }

      xyScate = GraphTypes.isPoint(chartStyle) && dimensions.size() == 0 &&
         aggregates.size() > 1;
      voColor = getColor(vo, 0, 0);
   }

   /**
    * Get category datasource for excel chart.
    */
   public XDDFCategoryDataSource getCategoryAxisData() {
      return XDDFDataSourcesFactory.fromArray(data.getCategoriesText());
   }

   /**
    * Get datasource array for all the series.
    */
   public List<XDDFNumericalDataSource<Number>> getValueAxisDatas() {
      int count = getSeriesCount();
      List<XDDFNumericalDataSource<Number>> datas = new ArrayList<>(count);

      for(int i = 0; i < count; i++) {
         datas.add(getValueAxisData(i));
      }

      return datas;
   }

   /**
    * Get serie data of target index.
    * @return
    */
   public XDDFNumericalDataSource<Number> getValueAxisData(int idx) {
      if(idx < 0 || idx >= getSeriesCount()) {
         return XDDFDataSourcesFactory.fromArray(new Number[0]);
      }

      double[] sdata = getSeriesData(idx);
      Number[] values = new Number[sdata.length];

      for(int i = 0; i < sdata.length;i++) {
         values[i] = sdata[i];
      }

      return XDDFDataSourcesFactory.fromArray(values);
   }

   /**
    * This is for multi style chart.
    * Get serie indexes of the target chart style.
    */
   public int[] getSerieIndexs(int chartstyle) {
      if(chartstyle == -1 || !needMultiData()) {
         return new int[0];
      }

      ChartMultiData multiData = (ChartMultiData) data;
      ArrayList<Integer> list = new ArrayList<Integer>();

      for(int i = 0; i < getSeriesCount(); i++) {
         // the serie name is composed by the serie fields data and
         // the numerical field name, so here, we need to get the
         // numerical field name.
         String name = multiData.getNumericalNameInSerie(i);
         DataRef ref = GraphUtil.getChartRef(cinfo, name, false);

         if(ref instanceof ChartAggregateRef) {
            int cType = ((ChartAggregateRef) ref).getRTChartType();

            if(cType == chartstyle) {
               list.add(i);
            }
         }
      }

      int[] indexes = new int[list.size()];

      for(int i = 0; i < list.size(); i++) {
         indexes[i] = list.get(i);
      }

      return indexes;
   }

   /**
    * This is for multi style chart. Get serie names of target chart style.
    */
   public String[] getSeriesName(int charttype) {
      if(charttype == -1 || !needMultiData()) {
         return new String[0];
      }

      ChartMultiData multiData = (ChartMultiData) data;
      ArrayList<String> list = new ArrayList<>();

      for(short i = 0; i < getSeriesCount(); i++) {
         String name = multiData.getNumericalNameInSerie(i);
         DataRef ref = refsMap.get(name);

         if(ref instanceof ChartAggregateRef) {
            int cType = ((ChartAggregateRef) ref).getRTChartType();

            if(cType == charttype) {
               list.add(multiData.getSerieName(i));
            }
         }
      }

      String[] titles = new String[list.size()];

      for(int j = 0; j < list.size(); j++) {
         titles[j] = list.get(j);
      }

      return titles;
   }

   /**
    * Get serie properties.
    * @param idx serie index.
    * @param style serie style.
    */
   public SerieInfo getSerieInfo(int idx, int style) {
      Iterator it = index.keySet().iterator();
      SerieInfo sinfo = new SerieInfo();
      sinfo.setTitle(getSerieName(idx));
      sinfo.setStyle(style);

      if(!GraphTypes.isStock(style) && !GraphTypes.isCandle(style)) {
         sinfo.setShowValue(descriptor.getPlotDescriptor().isValuesVisible());
      }

      PlotDescriptor plotDesc = descriptor.getPlotDescriptor();
      int trendline = plotDesc.getTrendline();

      if(trendline > 0) {
         sinfo.setTrendline(trendline);
         sinfo.setTrendLineColor(plotDesc.getTrendLineColor());
         sinfo.setTrendLineStyle(plotDesc.getTrendLineStyle());
      }

      if(GraphTypes.isStock(style)) {
         sinfo.setDefaultDataPointInfo(getStockDataPointInfo(idx));
         return sinfo;
      }

      if(GraphTypes.isCandle(style)) {
         sinfo.setDefaultDataPointInfo(getCandleDataPointInfo());
         return sinfo;
      }

      boolean dataLabelSetted = false;
      String name = data.getNumericalNameInSerie(idx);
      boolean issecondaryY = isSecondary(name);
      sinfo.setSecondaryY(issecondaryY);

      while(it.hasNext()) {
         Point point = (Point) it.next();

         if(point.x != idx) {
            continue;
         }

         ElementVO vo = (ElementVO) index.get(point);
         VOText text = null;

         if((GraphTypes.isArea(style) || GraphTypes.isLine(style))
            && (vo instanceof LineVO) || vo instanceof Pie3DVO)
         {
            VOText[] texts = vo.getVOTexts();
            text = texts[point.y >= texts.length ? 0 : point.y];
         }
         else {
            text = vo.getVOText();
         }

         if(text != null && !dataLabelSetted) {
            dataLabelSetted = true;
            TextSpec spec = text.getTextSpec();
            sinfo.setDataLabelColor(spec.getColor());
            sinfo.setDataLabelFont(spec.getFont());
            sinfo.setDataLabelFillColor(spec.getBackground());
            sinfo.setDataLabelFormat(spec.getFormat());
         }

         DataPointInfo dinfo = createDataPointInfo(vo, style,
            point.x, point.y, isSinglePointLine(style, idx));

         // set default datapointinfo, to avoid bugs when export stack chart.
         if(sinfo.getDefaultDataPointInfo() == null && (!hasBrush ||
            hasBrush && !brushHLColor.equals(dinfo.getFillColor())))
         {
            sinfo.setDefaultDataPointInfo(dinfo);
         }

         sinfo.addDataPointInfo(point.y, dinfo);
      }

      if(sinfo.getDefaultDataPointInfo() == null &&
         sinfo.getDataPointMapKeySet().size() != 0)
      {
         sinfo.setDefaultDataPointInfo(sinfo.getDataPointInfo(0));
      }

      if(GraphTypes.isPie(style)) {
         sinfo.setExploded(plotDesc.isExploded());
      }

      if(GraphTypes.isBar(style) && !GraphTypes.is3DBar(style)
         || GraphTypes.isPoint(style)
         || GraphTypes.isPie(style) && (style != GraphTypes.CHART_3D_PIE)
         || GraphTypes.isWaterfall(style)
         || GraphTypes.isPareto(style))
      {
         sinfo.setGlossyEffect(descriptor.isApplyEffect());
      }

      sinfo.setSparkline(descriptor.isSparkline());
      sinfo.setAlpha(plotDesc.getAlpha());

      return sinfo;
   }

   /**
    * @param serieIdx the serie index.
    * @Return data point info of stock chart.
    */
   private DataPointInfo getStockDataPointInfo(int serieIdx) {
      DataPointInfo dinfo = new DataPointInfo();
      // we use line chart pretend to stock/candle chart when export to excel,
      // then we cannot set different color for every pretend vo, so use the
      // first vo color in the vs chart as the series color.
      dinfo.setFillColor(voColor);
      MarkerInfo minfo = null;
      CandleChartInfo info = (CandleChartInfo) cinfo;

      String name = dataset.getHeader(getSeriesIdx().get(serieIdx));
      DataRef open = info.getOpenField();
      DataRef close = info.getCloseField();

      if(open != null && GraphUtil.equalsName(open, name)) {
         minfo = new MarkerInfo(5, STMarkerStyle.DASH);
      }
      else if(close != null && GraphUtil.equalsName(close, name)) {
         minfo = new MarkerInfo(5, STMarkerStyle.DOT);
      }
      else {
         minfo = new MarkerInfo(0, STMarkerStyle.NONE);
      }

      dinfo.setMarkerInfo(minfo);

      return dinfo;
   }

   private DataPointInfo getCandleDataPointInfo() {
      DataPointInfo dinfo = new DataPointInfo();
      // we use line chart pretend to stock/candle chart when export to excel,
      // then we cannot set different color for every pretend vo, so use the
      // first vo color in the vs chart as the series color.
      dinfo.setFillColor(voColor);
      dinfo.setMarkerInfo(new MarkerInfo(0, STMarkerStyle.NONE));

      return dinfo;
   }

   /**
    * Check if is line serie with only one point.
    * @param style the serie style.
    * @param serieIndex the serie index.
    */
   private boolean isSinglePointLine(int style, int serieIndex) {
      if(!GraphTypes.isLine(style)) {
         return false;
      }

      double[] sdata = getSeriesData(serieIndex);
      int size = 0;

      for(int i = 0; i < sdata.length; i++) {
         if(!Double.isNaN(sdata[i]) && sdata[i] != 0) {
            size++;
         }
      }

      return size == 1;
   }

   /**
    * Create serie value info.
    * @param vo the current element vo.
    * @param style style of the serie.
    * @param serieIdx serie index.
    * @param valIdx value index in a serie.
    */
   private DataPointInfo createDataPointInfo(ElementVO vo, int style,
      int serieIdx, int valIdx, boolean isSinglePointLine)
   {
      DataPointInfo dinfo = new DataPointInfo();
      Color fillColor = getColor(vo, serieIdx, valIdx);
      dinfo.setFillColor(fillColor);

      ElementGeometry gobj = (ElementGeometry) vo.getGeometry();
      boolean isPointLine = descriptor.getPlotDescriptor().isPointLine();

      // if it is a point, we should get the radius as its size
      double size = vo instanceof PointVO ?
         vo.getUnscaledSize(0, null) : gobj.getSize(0);
      GShape shape = null;
      GTexture texture = null;

      if((GraphTypes.isArea(style) || GraphTypes.isLine(style))
         && (vo instanceof LineVO) || vo instanceof Pie3DVO)
      {
         texture = gobj.getTexture(valIdx);
         shape = gobj.getShape(valIdx);
      }
      else {
         texture = gobj.getTexture(0);
         shape = gobj.getShape(0);
      }

      // fix bug1255607891898, if no shape, use circle, see PointVO getShape
      if(GraphTypes.isPoint(style) &&
         (shape == null || shape == GShape.NIL))
      {
         if(shape == GShape.NIL && size == 0) {
            // the size detemine the circle's size.
            size = 3.0;
         }

         shape = GShape.FILLED_CIRCLE;
      }

      MarkerInfo minfo = null;
      GraphElement elem = (GraphElement) vo.getGraphable();

      if(shape != null &&
         (elem instanceof PointElement || elem instanceof PolygonElement))
      {
         String id = null;

         try {
            id = ShapeFrameWrapper.getID(shape);
         }
         catch(Exception e) {
         }

         int id2 = StyleConstants.FILLED_CIRCLE;

         if(id != null) {
            try {
               id2 = Integer.parseInt(id);
            }
            catch(Exception ex) {
               // not a number, custom shape
            }
         }

         // excel only support nine type of point style, index exceed the 910
         // will result the file popup the data lost error, bug1244787829973
         id2 = (id2 - StyleConstants.CIRCLE) % 11 + StyleConstants.CIRCLE;
         minfo = new MarkerInfo((int) size * 2, id2);
      }
      else if(texture != null && !GraphTypes.isCandle(style)) {
         int id = TextureFrameWrapper.getID(texture);
         // if the texture is not supported, we specify one at random
         id = id < 0 ? Math.abs(random.nextInt() % textureCount) : id;
         dinfo.setPattFillPrst(XSSFChartUtil.getPrst(id));
      }

      GLine line = getLine(vo, serieIdx, valIdx);
      int lnstyle = StyleConstants.NONE;

      if(line != null) {
         lnstyle = line.getStyle();

         if(style == GraphTypes.CHART_CANDLE || style == GraphTypes.CHART_STOCK
            || GraphTypes.isPoint(style) && !isPointLine)
         {
            lnstyle = StyleConstants.NONE;
         }
      }
      else if(GraphTypes.isPoint(style) && isPointLine) {
         lnstyle = StyleConstants.THIN_LINE;
      }

      if(texture != null && !GraphTypes.isCandle(style)) {
         lnstyle = StyleConstants.THIN_LINE;
      }

      if(lnstyle != StyleConstants.NONE) {
         LineInfo lnInfo = new LineInfo();
         int linew = XSSFChartUtil.getLineWidth(lnstyle);
         STCompoundLine.Enum lnCmpd = XSSFChartUtil.getLineCompound(lnstyle);
         STPresetLineDashVal.Enum lnPrstDash =
            XSSFChartUtil.getPresetLineValue(lnstyle);

         lnInfo.setLineWidth(linew);
         lnInfo.setLineCompound(lnCmpd);
         lnInfo.setLinePrstDash(lnPrstDash);
         dinfo.setLineInfo(lnInfo);
      }

      if(GraphTypes.isLine(style) || GraphTypes.isRadar(style)) {
         // excel multi chart don't support display blank as span,
         // so single point of line style in single multi chart should set
         // marker style to null when show point is false.
         if(isPointLine || (!cinfo.isMultiStyles() ||
            cinfo.isSeparatedGraph()) && isSinglePointLine)
         {
            minfo = new MarkerInfo(3, STMarkerStyle.CIRCLE);
         }
         else {
            minfo = new MarkerInfo(0, STMarkerStyle.NONE);
         }
      }

      if(minfo != null) {
         dinfo.setMarkerInfo(minfo);
      }

      return dinfo;
   }

   public List<Object> getChartStyles() {
      if(!containsSecondaryY()) {
         return chartStyles;
      }

      List<Object> styles = new ArrayList<>();
      styles.addAll(chartStyles);
      styles.addAll(secChartStyles);

      return styles;
   }

   public int getSeriesRefCnt() {
      return data.getSeriesRefCnt();
   }

   public List<Integer> getSeriesIdx() {
      return data.seriesIdx;
   }

   public String[] getSeriesName() {
      String[] names = new String[getSeriesCount()];

      for(short i = 0; i < getSeriesCount(); i++) {
         String name = getSerieName(i);
         names[i] = name;
      }

      return names;
   }

   /**
    * Get the series number.
    */
   public int getSeriesCount() {
      return data.getSeriesCount();
   }

   /**
    * Get categories data number of the excel chart.
    */
   private int getCategoriesDataSize() {
      return data.getCategoriesDataSize();
   }

   /**
    * Get the categories text.
    */
   private String[] getCategoriesText() {
      return data.getCategoriesText();
   }

   /**
    * Get the series value of target index.
    */
   private double[] getSeriesData(int idx) {
      return data.getSeriesData(idx);
   }

   /**
    * Get serie name of target index.
    */
   private String getSerieName(int idx) {
      return data.getSerieName(idx);
   }

   public boolean isShowAxis() {
      // temp
      return true;
   }

   /**
    * Check whether current chart is a multi style chart.
    */
   private boolean needMultiData() {
      // pie chart have different way to orangize the excel chart data,
      // so if multistyle chart contains pie style should create piechartdata.
      if(cinfo.isMultiStyles() && !isPieChart()) {
         return true;
      }

      return false;
   }

   protected boolean isMeasure(DataRef ref) {
      if(GraphUtil.isMeasure(ref)) {
         return true;
      }

      if(ref instanceof AestheticRef) {
         if(((AestheticRef) ref).isMeasure()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Init the axis map to get axis alias.
    */
   private void initInnerAxisMap() {
      for(int i = 0; i < vgraph.getAxisCount(); i ++) {
         Scale scale = vgraph.getAxis(i).getScale();
         String[] flds = scale.getFields();

         if(flds != null && flds.length > 0) {
            innerAxisMap.put(flds[0], scale.getAxisSpec().getTextFrame());
         }
      }
   }

   public boolean isPieChart() {
      if(cinfo.isMultiStyles()) {
         List<ChartAggregateRef> aggrs = AllChartAggregateRef.getXYAggregateRefs(cinfo, false);
         AllChartAggregateRef ref = new AllChartAggregateRef(cinfo, aggrs);
         return GraphTypes.isPie(ref.getRTChartType());
      }

      return GraphTypes.isPie(chartStyle);
   }

   public ChartXData createChartData() {
      if(needMultiData()) {
         return new ChartMultiData();
      }

      chartStyles.clear();
      secChartStyles.clear();

      switch(chartStyle) {
         case GraphTypes.CHART_PIE:
         case GraphTypes.CHART_DONUT:
         case GraphTypes.CHART_3D_PIE:
            return new ChartPieData();
         case GraphTypes.CHART_POINT:
         case GraphTypes.CHART_POINT_STACK:
            return new ChartPointData();
         case GraphTypes.CHART_RADAR:
         case GraphTypes.CHART_FILL_RADAR:
            return new ChartRadarData();
         case GraphTypes.CHART_STOCK:
         case GraphTypes.CHART_CANDLE:
            return new ChartCandleData();
         case GraphTypes.CHART_LINE:
         case GraphTypes.CHART_LINE_STACK:
         case GraphTypes.CHART_STEP:
         case GraphTypes.CHART_STEP_STACK:
         case GraphTypes.CHART_JUMP:
            return new ChartLineData();
         case GraphTypes.CHART_AREA:
         case GraphTypes.CHART_AREA_STACK:
         case GraphTypes.CHART_STEP_AREA:
         case GraphTypes.CHART_STEP_AREA_STACK:
            return new ChartAreaData();
         default:
            return new ChartXData();
      }
   }

   /**
    * Check whether need to write the categories text on the principal axis.
    */
   public boolean onPrincipal() {
      return data.onPrincipal();
   }

   private int getColIndex(ElementVO vo) {
      if(vo == null) {
         return -1;
      }

      String measureName = vo.getMeasureName();

      if(measureName == null && GraphTypes.isRadar(getChartStyle()) && vo instanceof LineVO) {
         return vo.getColIndex();
      }

      return getColIndex(measureName);
   }

   private int getColIndex(String name) {
      if(name == null) {
         return -1;
      }

      name = GraphUtil.getOriginalCol(name);
      Integer res = colnames.get(name);

      if(res == null) {
         for(int i = 0; i < dataset.getColCount(); i++) {
            if(name.equals(dataset.getHeader(i))) {
               res = i;
               colnames.put(name, res);
               break;
            }
         }
      }

      return res == null ? -1 : res;
   }

   public int getChartStyle() {
      int style = -1;

      if(cinfo != null && !cinfo.isMultiStyles() &&
         cinfo.getRTChartType() != 0)
      {
         style = cinfo.getRTChartType();
      }
      else if(cinfo != null && cinfo.isMultiStyles()) {
         List<ChartAggregateRef> aggrs = AllChartAggregateRef.getXYAggregateRefs(cinfo, false);
         AllChartAggregateRef ref = new AllChartAggregateRef(cinfo, aggrs);
         style = ref.getRTChartType();
      }

      if(style == -1) {
         style = getChartStyleByGraph();
      }

      return style == -1 ? GraphTypes.CHART_BAR : style;
   }

   /**
    * Get chart style from the vgraph.
    */
   public int getChartStyleByGraph() {
      int count = vgraph.getVisualCount();
      boolean isStack = false;

      for(int i = 0; i < count; i++) {
         Visualizable vi = vgraph.getVisual(i);

         if(vi instanceof ElementVO) {
            ElementVO vo = (ElementVO) vi;
            GraphElement elem = ((ElementGeometry) vo.getGeometry()).getElement();
            isStack = elem.isStack();
         }

         if(vi instanceof AreaVO) {
            chartStyle =
               isStack ? GraphTypes.CHART_AREA_STACK : GraphTypes.CHART_AREA;
         }
         else if(vi instanceof LineVO) {
            chartStyle = isStack ? GraphTypes.CHART_LINE_STACK : GraphTypes.CHART_LINE;
         }
         else if(vi instanceof Bar3DVO) {
            chartStyle = isStack ?
               GraphTypes.CHART_3D_BAR_STACK : GraphTypes.CHART_3D_BAR;
         }
         else if(vi instanceof BarVO) {
            chartStyle = vgraph.getCoordinate() instanceof PolarCoord ?
               GraphTypes.CHART_PIE :
               isStack ? GraphTypes.CHART_BAR_STACK : GraphTypes.CHART_BAR;
         }
         else if(vi instanceof PointVO) {
            chartStyle = GraphTypes.CHART_POINT;
         }
         else if(vi instanceof Pie3DVO) {
            chartStyle = GraphTypes.CHART_3D_PIE;
         }

         if(chartStyle != -1) {
            break;
         }
      }

      return chartStyle;
   }

   /**
    * Check whether the specific ref is a secondary field.
    */
   private boolean isSecondary(String name) {
      List<ChartRef> refs = Arrays.asList(
         inverted ? cinfo.getRTXFields() : cinfo.getRTYFields());

      for(ChartRef ref : refs) {
         if(ref instanceof ChartAggregateRef &&
            GraphUtil.getName(ref).equals(name))
         {
            return ((ChartAggregateRef) ref).isSecondaryY();
         }
      }

      return false;
   }

   /**
    * Check whether to contains the measure which on the secondary Y axis.
    */
   public boolean containsSecondaryY() {
      if(cinfo.isSeparatedGraph()) {
         return false;
      }

      if(!cinfo.isMultiStyles() && (
         chartStyle == GraphTypes.CHART_3D_PIE ||
         chartStyle == GraphTypes.CHART_PIE ||
         chartStyle == GraphTypes.CHART_DONUT ||
         chartStyle == GraphTypes.CHART_3D_BAR ||
         chartStyle == GraphTypes.CHART_RADAR ||
         chartStyle == GraphTypes.CHART_FILL_RADAR ||
         chartStyle == GraphTypes.CHART_STOCK ||
         chartStyle == GraphTypes.CHART_CANDLE))
      {
         return false;
      }

      DataRef[] refs = cinfo.getRTFields();

      for(int i = 0; i < refs.length; i++) {
         DataRef ref = refs[i];

         if(ref instanceof ChartAggregateRef) {
            ChartAggregateRef ref0 = (ChartAggregateRef) ref;

            if(ref0.getChartType() == GraphTypes.CHART_3D_BAR ||
               ref0.getChartType() == GraphTypes.CHART_3D_PIE)
            {
               return false;
            }
            else if(ref0.isSecondaryY()) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Try to ger the aggregate from vgraph.
    */
   private void getBindingFromVGraph(List list) {
      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable vi = vgraph.getVisual(i);

         if(vi instanceof ElementVO) {
            ElementVO vo = (ElementVO) vi;
            String name = vo.getMeasureName();

            for(int j = 0; j < dataset.getColCount(); j++) {
               if(name.equals(dataset.getHeader(j))) {
                  Integer idx = j;

                  if(!list.contains(idx)) {
                     list.add(idx);
                     break;
                  }
               }
            }
         }

         if(list.size() >= 0) {
            break;
         }
      }
   }

   private class Tuple {
      public Tuple(Object[] dims) {
         StringBuilder buf = new StringBuilder();

         for(int i = 0; i < dims.length; i++) {
            if(dims[i] != null) {
               buf.append(dims[i]);
            }
         }

         value = buf.toString();
         this.dims = dims;
      }

      public int hashCode() {
         return value.hashCode();
      }

      public String toString() {
         return value;
      }

      public int indexOf(Object obj) {
         for(int i = 0; i < dims.length; i++) {
            if(Tool.equals(obj, dims[i])) {
               return i;
            }
         }

         return -1;
      }

      /**
       * Check if equals another object.
       */
      public boolean equals(Object obj) {
         if(!(obj instanceof Tuple)) {
            return false;
         }

         return Objects.equals(value, ((Tuple) obj).value);
      }

      String value = null;
      Object[] dims = null;
   }

   private class AxisIdxComparator implements Comparator<DataRef> {
      /**
       * Sort the series, make primary axises always are before the secondary
       * axises. But within the same axises(primary or secondary),the sequence
       * is optional.
       */
      @Override
      public int compare(DataRef ref1, DataRef ref2) {
         if(!(ref1 instanceof ChartAggregateRef) ||
            !(ref2 instanceof ChartAggregateRef))
         {
            return 0;
         }

         ChartAggregateRef cref1 = (ChartAggregateRef) ref1;
         ChartAggregateRef cref2 = (ChartAggregateRef) ref2;
         boolean isSecondaryY1 = cref1.isSecondaryY();
         boolean isSecondaryY2 = cref2.isSecondaryY();

         if(!(isSecondaryY1 ^ isSecondaryY2)) {
            return 0;
         }
         else if(isSecondaryY1) {
            return 1;
         }

         return -1;
      }

      public boolean equals(ChartAggregateRef obj)  {
         return true;
      }
   }

   /**
    * Normal chart data.
    */
   public class ChartXData {
      public ChartXData() {
         init();
      }

      /**
       * Init chart binding info and data.
       */
      private void init() {
         // optimization, get frames could be expensive because of ColorFrame.isMultiItem (45454).
         EGraph egraph = vgraph.getEGraph();
         frames = egraph == null ? null : egraph.getVisualFrames();

         initContainer();
         initBinding();
         initData();
      }

      /**
       * Init chart binding info container.
       */
      protected void initContainer() {
         numericalsIdx = new ArrayList2<>();
         seriesIdx = new ArrayList2<>();
         categoriesIdx = new ArrayList2<>();
         seriesData = new ArrayList<>();
         categoriesData = new ArrayList<>();
         datas = new SparseIndexedMatrix();
      }

      /**
       * Init chart binding info.
       */
      protected void initBinding() {
         ChartRef[][] refs = new ChartRef[2][];

         if(inverted) {
            refs[0] = cinfo.getRTYFields();
            refs[1] = cinfo.getRTXFields();
         }
         else {
            refs[0] = cinfo.getRTXFields();
            refs[1] = cinfo.getRTYFields();
         }

         String name = null;

         if(refs[1] != null && refs[1].length > 0) {
            for(int i = 0; i < refs[1].length; i++) {
               name = GraphUtil.getName(refs[1][i]);

               // dimension refs which binding in y field will not be removed
               // for a single chart in fixNumericalIndex function, we'd better
               // add the ref as a category field before the x fields to make
               // sure the exported chart is meaningful.
               if(refs[1][i] instanceof XDimensionRef) {
                  categoriesIdx.add(dataset.indexOfHeader(name));
               }
               else {
                  numericalsIdx.add(dataset.indexOfHeader(name));
               }
            }
         }

         if(refs[0] != null && refs[0].length > 0) {
            for(int i = 0; i < refs[0].length; i++) {
               name = GraphUtil.getName(refs[0][i]);
               categoriesIdx.add(dataset.indexOfHeader(name));
            }
         }

         if(getCategoriesSize() == 0 && refs[1] != null && refs[1].length > 0) {
            name = GraphUtil.getName(refs[1][0]);
            categoriesIdx.add(dataset.indexOfHeader(name));
         }

         initSeries();
         fixNumericalIndex();
      }

      /**
       * Add series by chart binding.
       */
      protected void initSeries() {
         addSeries(cinfo.getColorField());
         addSeries(cinfo.getShapeField());
         addSeries(cinfo.getSizeField());
         addLengends();
         addGroups();
      }

      /**
       * Add break by fields.
       */
      protected void addGroups() {
         ChartRef[] refs = cinfo.getGroupFields();

         for(ChartRef ref : refs) {
            addSeries(ref);
         }
      }

      /**
       * For multi style, the lengends are stored in the aggregate fields.
       */
      private void addLengends() {
         if(!cinfo.isMultiStyles()) {
            return;
         }

         ChartRef[] refs =
            inverted ? cinfo.getRTXFields() : cinfo.getRTYFields();

         int i = 0;
         for(ChartRef ref : refs) {
            int idx = dataset.indexOfHeader(GraphUtil.getName(ref));

            if(!numericalsIdx.contains(idx)) {
               continue;
            }

            DataRef color = null;
            DataRef shape = null;
            DataRef size = null;

            if(ref instanceof VSChartAggregateRef) {
               VSChartAggregateRef agg = (VSChartAggregateRef) ref;
               color = agg.getColorField();
               shape = agg.getShapeField();
               size = agg.getSizeField();
            }

            i++;

            ArrayList list = new ArrayList();

            if(color != null) {
               list.add(dataset.indexOfHeader(GraphUtil.getName(color)));
            }

            if(shape != null) {
               list.add(dataset.indexOfHeader(GraphUtil.getName(shape)));
            }

            if(size != null) {
               list.add(dataset.indexOfHeader(GraphUtil.getName(size)));
            }

            // It seems multi style chart can support both color and shape
            // properly when export to excel chart. So we add both the two
            // legends.
            legendsMap.put(idx, list);
            addSeries(color);
            addSeries(shape);
            addSeries(size);
         }
      }

      /**
       * Add the field which will be used to design the chart series.
       */
      protected void addSeries(DataRef ref) {
         seriesIdx.add(dataset.indexOfHeader(GraphUtil.getName(ref)));
      }

      /**
       * Init categories data, and design series, and init series data.
       */
      protected void initData() {
         int size = dataset.getRowCount();
         int scnt = getSeriesRefCnt();
         Object[] tmp = null;
         Tuple cates = null;
         Tuple tuple = null;
         boolean waterfall = GraphTypes.isWaterfall(getChartStyle());
         List totalRows = new ArrayList();

         for(int row = 0; row < size; row++) {
            tmp = new Object[scnt + 1];

            for(int i = 0; i < scnt; i++) {
               tmp[i] = dataset.getData(seriesIdx.get(i), row);
               tmp[i] = getText(tmp[i], seriesIdx.get(i));
            }

            for(int j = 0; j < numericalsIdx.size(); j++) {
               Object[] objs = new Object[tmp.length];
               System.arraycopy(tmp, 0, objs, 0, scnt);
               objs[scnt] = dataset.getHeader(numericalsIdx.get(j));
               tuple = new Tuple(objs);

               if(!seriesData.contains(tuple)) {
                  seriesData.add(tuple);
               }

               if(waterfall && !totalRows.contains(row) &&
                  dataset.getData(SumDataSet.SUM_HEADER_PREFIX + objs[scnt], row) != null)
               {
                  totalRows.add(row);
               }
            }
         }

         if(scnt > 0) {
            sortSeriesData();
         }

         for(int row = 0; row < size; row++) {
            // ignore total row of waterfall, excel don't support it.
            if(waterfall && totalRows.contains(row)) {
               continue;
            }

            tmp = new Object[getCategoriesSize()];

            for(int idx = 0; idx < tmp.length; idx++) {
               tmp[idx] = dataset.getData(getCategoriesIndex(idx), row);
               tmp[idx] = getText(tmp[idx], getCategoriesIndex(idx), false);
            }

            cates = new Tuple(tmp);

            if(!categoriesData.contains(cates)) {
               categoriesData.add(cates);
            }

            tmp = new Object[scnt + 1];

            for(int i = 0; i < scnt; i++) {
               tmp[i] = dataset.getData(seriesIdx.get(i), row);
               tmp[i] = getText(tmp[i], seriesIdx.get(i));
            }

            for(int j = 0; j < numericalsIdx.size(); j++) {
               Object[] objs = new Object[tmp.length];
               System.arraycopy(tmp, 0, objs, 0, scnt);
               objs[scnt] = dataset.getHeader(numericalsIdx.get(j));
               tuple = new Tuple(objs);

               if(!seriesData.contains(tuple)) {
                  seriesData.add(tuple);
               }

               Tuple tuple0 = tuple;
               Object val = dataset.getData(numericalsIdx.get(j), row);
               datas.set(categoriesData.indexOf(cates),
                         seriesData.indexOf(tuple0), val);
            }
         }
      }

      /**
       * Fix the numerical index binding for separated chart.
       * Because separated graph will be exported as sub charts for each
       * numerical field, so here only add current numerical index to the list.
       */
      protected void fixNumericalIndex() {
         if(numericalsIdx.size() < 2 || !cinfo.isSeparatedGraph()) {
            return;
         }

         int idx = -1;

         for(int i = 0; i < vgraph.getVisualCount(); i++) {
            Visualizable vi = vgraph.getVisual(i);
            boolean inbrush = true;

            if(!(vi instanceof ElementVO)) {
               continue;
            }

            ElementVO vo = (ElementVO) vi;
            idx = getColIndex(vo);

            if(idx > -1) {
               break;
            }
         }

         if(idx < 0) {
            return;
         }

         numericalsIdx.clear();
         numericalsIdx.add(idx);
      }

      /**
       * Sort series data if needed.
       */
      protected void sortSeriesData() {
         Collections.sort(seriesData, new Comparator<Tuple>() {
            @Override
            public int compare(Tuple o1, Tuple o2) {
               if(o1 == null || o1.dims.length == 0) {
                  return -1;
               }

               if(o2 == null || o2.dims.length == 0) {
                  return 1;
               }

               Object n1 = o1.dims[o1.dims.length - 1];
               Object n2 = o2.dims[o2.dims.length - 1];

               if(n1 == null) {
                  return -1;
               }

               if(n2 == null) {
                  return 1;
               }

               int r = n1.toString().compareTo(n2.toString());

               if(r == 0) {
                  return o1.toString().compareTo(o2.toString());
               }

               return r;
            }
         });
      }

      /**
       * Get the series ref size.
       */
      protected int getSeriesRefCnt() {
         return seriesIdx.size();
      }

      /**
       * Get the series number.
       */
      protected int getSeriesCount() {
         return seriesData.size();
      }

      /**
       * Get categories data number of the excel chart.
       */
      protected int getCategoriesDataSize() {
         return categoriesData.size();
      }

      /**
       * Get the categories text.
       */
      protected String[] getCategoriesText() {
         String[] res = new String[getCategoriesDataSize()];

         for(int i = 0; i < res.length; i++) {
            Tuple tuple = categoriesData.get(i);
            StringBuilder buffer = new StringBuilder();

            for(int j = 0; j < tuple.dims.length; j++) {
               buffer.append(buffer.length() == 0 ? "" : ":");
               buffer.append(tuple.dims[j]);
            }

            res[i] = buffer.toString();
         }

         return res;
      }

       /**
       * Get the numerical field name in target serie.
       * We need the numerical field name to judge the chart style
       * of the target serie.
       */
      protected String getNumericalNameInSerie(int idx) {
         if(seriesData.size() > idx) {
            Tuple tuple = seriesData.get(idx);
            Object[] dims = tuple.dims;

            if(dims != null && dims.length > 0) {
               return dims[dims.length - 1] + "";
            }
         }

         return "";
      }

      /**
       * Check whether need to write the categories text on the principal axis.
       */
      protected boolean onPrincipal() {
         // This method exist for Error 2 in bug1346054384082.
         return numericalsIdx.size() == 1 &&
            containsSecondaryY() && seriesIdx.size() > 0;
      }

      /**
       * Get serie name of target index.
       */
      protected String getSerieName(int idx) {
         Tuple tuple = seriesData.get(idx);
         StringBuilder buffer = new StringBuilder();

         // if only one numerical field, then use numerical field header
         // as serie name to make the excel chart display more similar
         // to our vs chart.
         int cnt = numericalsIdx.size() == 1 && !cinfo.isFacet() ?
            tuple.dims.length - 1: tuple.dims.length;

         for(int j = 0; j < cnt; j++) {
            Object str = tuple.dims[j];

            // use alias for legend when binding no visual field.
            if(j == tuple.dims.length - 1) {
               str = getText(str, getColIndex(str + ""), false, true);
            }

            if(buffer.length() > 0) {
               buffer.append("|");
            }

            buffer.append(str);
         }

         return buffer.toString();
      }

      /**
       * Get the series value of target index.
       */
      protected double[] getSeriesData(int idx) {
         double[] vals = new double[getCategoriesDataSize()];

         for(int i = 0; i < vals.length; i++) {
            vals[i] = formatData(datas.get(i, idx));
         }

         return vals;
      }

      /**
       * Format the number.
       */
      protected double formatData(Object val) {
         if(isFloat(val)) {
            try {
               val = DecimalFormat.getInstance().parse(DECIMAL_FORMAT.format(val));
            }
            catch(ParseException pe) {
               return 0;
            }
         }

         if(val instanceof Number) {
            return ((Number) val).doubleValue();
         }

         try {
            return Double.parseDouble(val.toString());
         }
         catch(Exception e) {
            return Double.NaN;
         }
      }

      /**
       * Check if is float.
       */
      private boolean isFloat(Object obj) {
         return obj instanceof Double || obj instanceof Float ||
            obj instanceof BigDecimal;
      }

      protected int getCategoryIndex(Object[] arr, Object obj) {
         return categoriesData.indexOf(obj);
      }

      /**
       * Get the series tuple index in series data.
       */
      protected int getSeriesIndex(Tuple tuple) {
         return seriesData.indexOf(tuple);
      }

      /**
       * Get the categories field size.
       */
      protected int getCategoriesSize() {
         return categoriesIdx.size();
      }

      protected int getCategoriesIndex(int idx) {
         return getCategoriesSize() > idx ? categoriesIdx.get(idx) : -1;
      }

      protected boolean isMeasure(DataRef ref) {
         if(GraphUtil.isMeasure(ref)) {
            return true;
         }

         if(ref instanceof AestheticRef) {
            if(((AestheticRef) ref).isMeasure()) {
               return true;
            }
         }

         return false;
      }

      protected boolean isMeasure(int idx) {
         return dataset.isMeasure(dataset.getHeader(idx));
      }

      /**
       * Get the formated text.
       */
      protected String getText(Object val, int idx) {
         return getText(val, idx, true, true);
      }

      /**
       * Get the formated text.
       */
      protected String getText(Object val, int idx, boolean legend) {
         return getText(val, idx, true, legend);
      }

      /**
       * Get the formated text.
       */
      protected String getText(
         Object val, int idx, boolean format, boolean legend)
      {
         String name = idx == -1 ? "" : dataset.getHeader(idx);

         return getText(
            format ? getFormat(name) : null, val, name, idx, legend);
      }

      /**
       * Get format with the specified column name from chart info.
       */
      private Format getFormat(String name) {
         DataRef[] refs = cinfo == null ? new DataRef[0] : cinfo.getRTFields();
         ChartRef ref = null;
         SimpleDateFormat fmt = null;

         for(int i = 0; i < refs.length; i++) {
            if(name.equals(GraphUtil.getName(refs[i]))) {
               if(refs[i] instanceof ChartRef) {
                  ref = (ChartRef) refs[i];
               }

               if(refs[i] instanceof XDimensionRef &&
                  XSchema.isDateType(refs[i].getDataType()))
               {
                  int lvl = refs[i] instanceof VSDimensionRef ?
                     ((VSDimensionRef) refs[i]).getRealDateLevel() :
                     ((XDimensionRef) refs[i]).getDateLevel();
                  String type = refs[i].getDataType();
                  fmt = XUtil.getDefaultDateFormat(lvl, type);
               }

               break;
            }
         }

         if(ref == null) {
            return null;
         }

         XFormatInfo finfo = textFormatMap.get(name);

         if(finfo == null) {
            finfo = getXFormatInfo(ref);
            // put a new formatinfo instead of null to avoid repetitive work.
            textFormatMap.put(name, finfo != null ? finfo : new XFormatInfo());
         }

         if(finfo != null && finfo.getFormat() != null) {
            return TableFormat.getFormat(
               finfo.getFormat(), finfo.getFormatSpec());
         }

         return fmt;
      }

      /**
       * Get the format option.
       * @return the format option of the target ref.
       */
      private XFormatInfo getXFormatInfo(ChartRef ref) {
         if(ref == null) {
            return null;
         }

         String name = GraphUtil.getName(ref);
         CompositeTextFormat tfmt = null;
         AxisDescriptor rdesc = null;

         if(ref instanceof VSChartRef) {
            rdesc = ((VSChartRef) ref).getRTAxisDescriptor();
         }

         rdesc = rdesc == null ? ref.getAxisDescriptor() : rdesc;
         tfmt = rdesc != null ? rdesc.getColumnLabelTextFormat(name) : null;

         if(tfmt == null) {
            tfmt = rdesc.getAxisLabelTextFormat();
         }

         if(tfmt != null && tfmt.getFormat() != null &&
            tfmt.getFormat().getFormat() != null)
         {
            return tfmt.getFormat();
         }

         LegendsDescriptor descs =
            descriptor == null ? null : descriptor.getLegendsDescriptor();

         if(descs == null) {
            return null;
         }

         LegendDescriptor desc = null;

         if(Tool.equals(name, GraphUtil.getName(cinfo.getShapeField()))) {
            desc = descs.getShapeLegendDescriptor();
         }

         if(Tool.equals(name, GraphUtil.getName(cinfo.getSizeField()))) {
            desc = descs.getSizeLegendDescriptor();
         }

         if(Tool.equals(name, GraphUtil.getName(cinfo.getColorField()))) {
            desc = descs.getColorLegendDescriptor();
         }

         tfmt = desc != null ? desc.getContentTextFormat() : null;

         if(tfmt != null && tfmt.getFormat() != null &&
            tfmt.getFormat().getFormat() != null)
         {
            return tfmt.getFormat();
         }

         return null;
      }

      /**
       * Get the formated text with the format.
       */
      private String getText(
         Format fmt, Object label, String name, int col, boolean legend)
      {
         TextFrame textFrame = innerAxisMap.get(name);
         label = textFrame != null ? textFrame.getText(label) : label;
         Object alias = getAliasLabel(label, col, legend);

         if(alias != null && !alias.equals(label)) {
            return CoreTool.toString(alias);
         }

         String fmtLabel = null;

         // don't format number, or the axis is wrong for numeric axis
         if(fmt != null && (legend || !(label instanceof Number))) {
            return GTool.format(fmt, label);
         }

         if(label instanceof Date) {
            if(label instanceof java.sql.Time) {
               fmtLabel = new SimpleDateFormat("HH:mm:ss").format(label);
            }

            fmtLabel = new SimpleDateFormat("yyyy-MM-dd").format(label);
         }

         if(fmtLabel != null) {
            return CoreTool.toString(fmtLabel);
         }

         return CoreTool.toString(label);
      }

      /**
       * Get alias label.
       */
      private Object getAliasLabel(Object label, int col, boolean legend) {
         return legend ? getLegendLabel(label, col) : getAxisLabel(label, col);
      }

      /**
       * Get alias label from the axis.
       */
      protected Object getAxisLabel(Object label, int col) {
         return label;
      }

      /**
       * Get alias label from the legend.
       */
      private Object getLegendLabel(Object label, int col) {
         LegendsDescriptor descs =
            descriptor == null ? null : descriptor.getLegendsDescriptor();

         if(descs == null || col < 0) {
            return label;
         }

         Object text = null;
         String header = dataset.getHeader(col);

         if(header == null) {
            return label;
         }

         if(Tool.equals(header, GraphUtil.getName(cinfo.getShapeField()))) {
            text = getLabel(descs.getShapeLegendDescriptor(), label);
         }
         else if(Tool.equals(header, GraphUtil.getName(cinfo.getSizeField()))) {
            text = getLabel(descs.getSizeLegendDescriptor(), label);
         }
         else if(Tool.equals(header, GraphUtil.getName(cinfo.getColorField()))) {
            text = getLabel(descs.getColorLegendDescriptor(), label);
         }
         else {
            if(frames == null) {
               return label;
            }

            for(int i = 0; i < frames.length; i++) {
               VisualFrame frame = frames[i];

               // find alias in mapping visualframe.
               if(frame.getField() != null && !header.equals(frame.getField())) {
                  continue;
               }

               if((text = findAlias(frame, label)) != null) {
                  return text;
               }
            }
         }

         return text == null ? label : text;
      }

      /**
       * Find legend alias in the target visualframe.
       */
      private Object findAlias(VisualFrame frame, Object label) {
         if(frame == null) {
            return null;
         }

         Object[] labels = frame.getLabels();
         Object[] values = frame.getValues();

         for(int i = 0; i < values.length; i++) {
            boolean find = label == null && values[i] == null;

            if(!find && label != null && values[i] != null) {
               find = label.equals(values[i]);
            }

            if(find && labels != null && i < labels.length) {
               return labels[i];
            }
         }

         return null;
      }

      /**
       * Get label from the legend descriptor.
       */
      private Object getLabel(LegendDescriptor desc, Object label) {
         return desc == null ? null : desc.getTextFrame().getText(label);
      }

      protected List<Integer> categoriesIdx;
      protected List<Integer> numericalsIdx;
      protected List<Integer> seriesIdx;
      protected List<Tuple> seriesData;
      protected List<Tuple> categoriesData;
      protected SparseIndexedMatrix datas;
      protected final Format DECIMAL_FORMAT = new DecimalFormat("0.####");
      protected VisualFrame[] frames;
   }

   /**
    * Chart area data.
    */
   private class ChartAreaData extends ChartXData {
      @Override
      protected void addSeries(DataRef ref) {
         // measure legends or group by fields are meaningless for area chart
         // when exporting to excel, so just ignore.
         if(ref == null || isMeasure(ref)) {
            return;
         }

         super.addSeries(ref);
      }
   }

   /**
    * Chart line and stack line data.
    */
   private class ChartLineData extends ChartXData {
      @Override
      protected void addSeries(DataRef ref) {
         // measure legends or group by fields is meaningless for line chart
         // when export to excel, so just ignore.
         if(ref == null || isMeasure(ref)) {
            return;
         }

         super.addSeries(ref);
      }
   }

   /**
    * Chart data for pie and 3D pie chart.
    */
   private class ChartPieData extends ChartXData {
      @Override
      protected void initBinding() {
         super.initBinding();

         addSeries(cinfo.getTextField());
      }

      @Override
      protected void initData() {
         int size = dataset.getRowCount();
         int scnt = getSeriesRefCnt();
         int cateCnt = getCategoriesSize();
         Object[] tmp = null;

         // if only one category column and the column type is measure,
         // means no dimension field for pie, so don't use it to create
         // categories data.
         if(cateCnt == 1 &&
            dataset.isMeasure(dataset.getHeader(getCategoriesIndex(0))))
         {
            cateCnt = 0;
         }

         for(int row = 0; row < size; row++) {
            tmp = new Object[cateCnt + scnt];

            for(int i = 0; i < cateCnt; i++) {
               tmp[i] = dataset.getData(getCategoriesIndex(i), row);
               tmp[i] = getText(tmp[i], getCategoriesIndex(i), true);
            }

            for(int j = 0; j < scnt; j++) {
               tmp[j + cateCnt] = dataset.getData(seriesIdx.get(j), row);
               tmp[j + cateCnt] = getText(tmp[j + cateCnt], seriesIdx.get(j));
            }

            Tuple tuple = new Tuple(tmp);


            if(!categoriesData.contains(tuple)) {
               categoriesData.add(tuple);
            }

            for(int j = 0; j < numericalsIdx.size(); j++) {
               Object[] objs = new Object[1];
               objs[0] = dataset.getHeader(numericalsIdx.get(j));
               Tuple tuple0 = new Tuple(objs);

               if(!seriesData.contains(tuple0)) {
                  seriesData.add(tuple0);
               }

               Object val = dataset.getData(numericalsIdx.get(j), row);
               datas.set(categoriesData.indexOf(tuple),
                  seriesData.indexOf(tuple0), val);
            }
         }
      }

      /**
       * Get the series tuple index in series data.
       * Excel pie chart only support one serie.
       */
      @Override
      protected int getSeriesIndex(Tuple tuple) {
         return 0;
      }

      /**
       * Get the categories text.
       */
      @Override
      protected String[] getCategoriesText() {
         String[] texts = new String[getCategoriesDataSize()];

         for(int i = 0; i < texts.length; i++) {
            Tuple tuple = categoriesData.get(i);
            StringBuilder buffer = new StringBuilder();

            for(int j = 0; j < tuple.dims.length; j++) {
               buffer.append(buffer.length() == 0 ? "" : ":");
               buffer.append(tuple.dims[j]);
            }

            texts[i] = buffer.toString();
         }

         return texts;
      }

      /**
       * Get serie name of target index.
       */
      @Override
      protected String getSerieName(int idx) {
         if(numericalsIdx.size() > idx) {
            String header = dataset.getHeader(numericalsIdx.get(idx));
            return getText(header, numericalsIdx.get(idx));
         }

         return "";
      }

      /**
       * Get the series value of target index.
       */
      @Override
      protected double[] getSeriesData(int idx) {
         double[] vals = new double[getCategoriesDataSize()];
         int scnt = getSeriesRefCnt();
         Object[] tmp = null;

          for(int i = 0; i < vals.length; i++) {
            Object val = datas.get(i, idx);
            vals[i] = formatData(val);
         }

         return vals;
      }

      /**
       * Get the series number.
       * For pie chart, one numerical field will used as a serie in excel chart.
       */
      @Override
      protected int getSeriesCount() {
         return numericalsIdx.size();
      }

     /**
      * Get categories data number of the excel chart.
      */
      @Override
      protected int getCategoriesDataSize() {
         return categoriesData.size();
      }

      /**
       * Get the category object index in categories data.
       */
      @Override
      protected int getCategoryIndex(Object[] arr, Object obj) {
         return categoriesData.indexOf(new Tuple(arr));
      }
   }

   /**
    * Chart data for point chart.
    */
   private class ChartPointData extends ChartXData {
      /**
       * Init chart binding info container.
       */
      @Override
      protected void initContainer() {
         super.initContainer();

         numerics = new ArrayList2<>();
         numericalsIdx = new ArrayList2<Integer>() {
            @Override
            public boolean add(Integer val) {
               if(!isMeasure(val)) {
                  numerics.add(val);

                  return false;
               }

               return super.add(val);
            }
         };
      }

      /**
       * Init chart binding info.
       */
      @Override
      protected void initBinding() {
         super.initBinding();

         if(numericalsIdx.size() == 0) {
            xyDims = true;
            getBindingFromVGraph(numericalsIdx);
         }
      }

      /**
       * Get serie name of target index.
       */
      @Override
      protected String getSerieName(int idx) {
         if(seriesIdx.size() > 0) {
            return super.getSerieName(idx);
         }

         if(xyDims && numerics.size() > 0) {
            StringBuilder buffer = new StringBuilder();

            for(int col : numerics) {
               String text = getText(dataset.getData(col, idx), col);

               if(buffer.length() > 0) {
                  buffer.append("|");
               }

               buffer.append(text);
            }

            return buffer.toString();
         }

         return super.getSerieName(idx);
      }

      private boolean xyDims; // only dimensions binding in x/y axis.
      private ArrayList<Integer> numerics;
   }

   /**
    * Chart data for radar
    */
   private class ChartRadarData extends ChartXData {
      /**
       * Init chart binding info.
       */
      @Override
      protected void initBinding() {
         initXYBinding(cinfo.getRTXFields());
         initXYBinding(cinfo.getRTYFields());
         initSeries();
      }

      private void initXYBinding(ChartRef[] refs) {
         if(refs == null || refs.length == 0) {
            return;
         }

         String name = null;

         for(int i = 0; i < refs.length; i++) {
            name = GraphUtil.getName(refs[i]);

            if(refs[i] instanceof XDimensionRef) {
               addSeries(refs[i]);
            }
            else {
               numericalsIdx.add(dataset.indexOfHeader(name));
            }
         }
      }

      /**
       * Get categories data number of the excel chart.
       * For radar chart, numerical fields will used as categories data
       * of the excel chart.
       */
      @Override
      protected int getCategoriesDataSize() {
         return numericalsIdx.size();
      }

      /**
       * Get the categories text.
       */
      @Override
      protected String[] getCategoriesText() {
         String[] res = new String[getCategoriesDataSize()];

         for(int i = 0; i < res.length; i++) {
            int col = numericalsIdx.get(i);
            res[i] = getText(dataset.getHeader(col), col, false);
         }

         return res;
      }

      /**
       * Get the series value of target index.
       */
      @Override
      protected double[] getSeriesData(int idx) {
         double[] vals = new double[getCategoriesDataSize()];

         for(int i = 0; i < vals.length; i++) {
            vals[i] = formatData(dataset.getData(numericalsIdx.get(i), idx));
         }

         return vals;
      }

      /**
       * Fix the numerical index binding for separated chart.
       */
      @Override
      protected void fixNumericalIndex() {
      }

      /**
       * Get alias label from axis.
       */
      @Override
      protected Object getAxisLabel(Object label, int col) {
         AxisDescriptor des = ((RadarChartInfo) cinfo).getLabelAxisDescriptor();
         Object alias = des.getLabelAlias(label);

         return alias == null ? label : alias;
      }

      /**
       * Init categories data, and design series, and init series data.
       */
      @Override
      protected void initData() {
         int size = dataset.getRowCount();
         int scnt = getSeriesRefCnt();
         int cateCnt = getCategoriesSize();

         Object[] tmp = null;
         Tuple cates = null;
         Tuple tuple = null;

         // if only one category column and the column type is measure,
         // means no dimension field, so don't use it to create serie name.
         if(cateCnt == 1 &&
            dataset.isMeasure(dataset.getHeader(getCategoriesIndex(0))))
         {
            cateCnt = 0;
         }

         for(int row = 0; row < size; row++) {
            tmp = new Object[cateCnt + scnt];

            for(int i = 0; i < cateCnt; i++) {
               tmp[i] = dataset.getData(getCategoriesIndex(i), row);
               tmp[i] = getText(tmp[i], getCategoriesIndex(i), false);
            }

            for(int j = 0; j < scnt; j++) {
               tmp[j + cateCnt] = dataset.getData(seriesIdx.get(j), row);
               tmp[j + cateCnt] = getText(tmp[j + cateCnt], seriesIdx.get(j));
            }

            tuple = new Tuple(tmp);

            // radar serie names are generated by
            // (break by data) *(legends data)* (cates data)
            if(!seriesData.contains(tuple)) {
               seriesData.add(tuple);
            }

            for(int k = 0; k < numericalsIdx.size(); k++) {
               Object[] objs = new Object[1];
               objs[0] = dataset.getHeader(numericalsIdx.get(k));
               cates = new Tuple(objs);

               if(!categoriesData.contains(cates)) {
                  categoriesData.add(cates);
               }

               Object val = dataset.getData(numericalsIdx.get(k), row);
               datas.set(categoriesData.indexOf(cates),
                  seriesData.indexOf(tuple), val);
            }
         }
      }
   }

   /**
    * Candle and Stock chart data.
    */
   private class ChartCandleData extends ChartXData {
      @Override
      protected void initBinding() {
         CandleChartInfo info = (CandleChartInfo) cinfo;
         seriesIdx.add(getRefIdx(info.getRTOpenField()));
         seriesIdx.add(getRefIdx(info.getRTHighField()));
         seriesIdx.add(getRefIdx(info.getRTLowField()));
         seriesIdx.add(getRefIdx(info.getRTCloseField()));
         numericalsIdx.add(getRefIdx(info.getRTOpenField()));
         numericalsIdx.add(getRefIdx(info.getRTHighField()));
         numericalsIdx.add(getRefIdx(info.getRTLowField()));
         numericalsIdx.add(getRefIdx(info.getRTCloseField()));

         ChartRef[] refs = cinfo.getRTXFields();

         if(refs != null && refs.length > 0) {
            for(int i = 0; i < refs.length; i++) {
               categoriesIdx.add(getRefIdx(refs[i]));
            }
         }
      }

      @Override
      protected void initData() {
         for(int i = 0; i < seriesIdx.size(); i++) {
            Tuple tuple =
               new Tuple(new String[]{dataset.getHeader(seriesIdx.get(i))});
            seriesData.add(tuple);
         }

         int size = dataset.getRowCount();

         for(int row = 0; row < size; row++) {
            Object[] cates = new Object[getCategoriesSize()];

            for(int idx = 0; idx < cates.length; idx++) {
               cates[idx] = dataset.getData(getCategoriesIndex(idx), row);
               cates[idx] = getText(cates[idx], getCategoriesIndex(idx));
            }

            Tuple tp = new Tuple(cates);

            if(!categoriesData.contains(tp)) {
               categoriesData.add(tp);
            }

            for(int idx = 0; idx < numericalsIdx.size(); idx++) {
               int col = numericalsIdx.get(idx);
               Object obj0 = dataset.getData(col, row);
               Tuple tuple =  new Tuple(new String[]{dataset.getHeader(col)});
               datas.set(
                  categoriesData.indexOf(tp), seriesData.indexOf(tuple), obj0);
            }
         }
      }

      private int getRefIdx(ChartRef ref) {
         return dataset.indexOfHeader(GraphUtil.getName(ref));
      }
   }

   /**
    * Multi style chart data.
    * When we create a multi style chart in microsoft excel, actually it will
    * use the series to make the chart like a multi style chart. So we cannot
    * support a chart in style report not only has multi series but also has
    * Color... series. Here will ignore the Color.. legend when export.
    */
   private class ChartMultiData extends ChartXData {
      /**
       * For mixed visual chart.
       */
      private void initMixedRows() {
         mixedRowsMap = new HashMap<>();

         for(int i = 0; i < getSeriesRefCnt(); i++) {
            for(int j = 0; j < vgraph.getVisualCount(); j++) {
               Visualizable vi = vgraph.getVisual(j);

               if(!(vi instanceof ElementVO)) {
                  continue;
               }

               ElementVO vo = (ElementVO) vi;

               int idx = getColIndex(vo);

               if(idx < 0) {
                  continue;
               }

               DataRef ref = GraphUtil.getChartRef(cinfo,
                  dataset.getHeader(idx), false);

               int cType = -1;

               if(ref instanceof ChartAggregateRef) {
                  cType = ((ChartAggregateRef) ref).getRTChartType();
               }

               int[] rows = getElementRows(vo, cType);
               ArrayList2 list = mixedRowsMap.get(idx);

               if(list == null) {
                  list = new ArrayList2();
                  mixedRowsMap.put(idx, list);
               }

               for(int r : rows) {
                  list.add(r);
               }
            }
         }
      }

      /**
       * Init categories data, and design series, and init series data.
       */
      @Override
      protected void initData() {
         initMixedRows();
         int size = dataset.getRowCount();
         int scnt = getSeriesRefCnt();
         Object[] tmp = null;
         Tuple cates = null;
         Tuple tuple = null;

         for(int row = 0; row < size; row++) {
            tmp = new Object[scnt + 1];

            LOOP0:
            for(int j = 0; j < numericalsIdx.size(); j++) {
               ArrayList list = new ArrayList();

               for(int i = 0; i < scnt; i++) {
                  // if this row is not for current numerical field,
                  // just loop to next row.
                  if(ingoreRow(numericalsIdx.get(j), row)) {
                     continue LOOP0;
                  }

                  // don't use this serie if the serie field is not added for
                  // current numerical field, or not supported by current
                  // numerical field.
                  if(!containsSerie(numericalsIdx.get(j), seriesIdx.get(i)) ||
                     unSupportLegend(numericalsIdx.get(j), seriesIdx.get(i)))
                  {
                     continue;
                  }

                  Object obj = dataset.getData(seriesIdx.get(i), row);
                  list.add(getText(obj, seriesIdx.get(i)));
               }

               list.add(dataset.getHeader(numericalsIdx.get(j)));
               tuple = new Tuple(list.toArray());

               if(!seriesData.contains(tuple)) {
                  seriesData.add(tuple);
               }
            }
         }

         sortSeriesData();

         for(int row = 0; row < size; row++) {
            tmp = new Object[getCategoriesSize()];

            for(int idx = 0; idx < tmp.length; idx++) {
               tmp[idx] = dataset.getData(getCategoriesIndex(idx), row);
               tmp[idx] = getText(tmp[idx], getCategoriesIndex(idx), false);
            }

            cates = new Tuple(tmp);

            if(!categoriesData.contains(cates)) {
               categoriesData.add(cates);
            }

            LOOP:
            for(int j = 0; j < numericalsIdx.size(); j++) {
               ArrayList list = new ArrayList();

               for(int i = 0; i < scnt; i++) {
                  // if this row is not for current numerical field,
                  // just loop to next row.
                  if(ingoreRow(numericalsIdx.get(j), row)) {
                     continue LOOP;
                  }

                  // don't use this serie if the serie field is not added for
                  // current numerical field, or not supported by current
                  // numerical field.
                  if(!containsSerie(numericalsIdx.get(j), seriesIdx.get(i)) ||
                     unSupportLegend(numericalsIdx.get(j), seriesIdx.get(i)))
                  {
                     continue;
                  }

                  Object obj = dataset.getData(seriesIdx.get(i), row);
                  list.add(getText(obj, seriesIdx.get(i)));
               }

               list.add(dataset.getHeader(numericalsIdx.get(j)));
               tuple = new Tuple(list.toArray());

               if(!seriesData.contains(tuple)) {
                  seriesData.add(tuple);
               }

               Tuple tuple0 = tuple;
               Object val = dataset.getData(numericalsIdx.get(j), row);

               if(val != null) {
                  datas.set(categoriesData.indexOf(cates),
                            seriesData.indexOf(tuple0), val);
               }
            }
         }
      }

      /**
       * Check if the row is not in the subdataset of the numerical field.
       */
      private boolean ingoreRow(int numericalIdx, int row) {
         List list = mixedRowsMap.get(numericalIdx);
         return list != null && !list.contains(row);
      }

      /**
       * Check if measure asc field for line/area.
       */
      private boolean unSupportLegend(int numericalIdx, int serieIdx) {
         DataRef ref = GraphUtil.getChartRef(cinfo,
            dataset.getHeader(numericalIdx), false);

         if(ref instanceof ChartAggregateRef) {
            int cType = ((ChartAggregateRef) ref).getRTChartType();

            // measure legend will cause line and area be divided to the
            // series which only contains one data, that will make the
            // excel chart displays meaningless.
            if((GraphTypes.isLine(cType) || GraphTypes.isArea(cType)) &&
               isMeasure(serieIdx))
            {
               return true;
            }
         }

         return false;
      }

      /**
       * Check if the legend field is added for the numerical field.
       */
      private boolean containsSerie(int numericalIdx, int serieIdx) {
         if(GraphUtil.getChartGroupRef(
            cinfo, dataset.getHeader(serieIdx)) != null)
         {
            return true;
         }

         DataRef ref = GraphUtil.getChartAesRef(cinfo,
            dataset.getHeader(serieIdx));

         if(ref != null && ref instanceof AestheticRef) {
            List list = legendsMap.get(numericalIdx);

            return list != null && list.contains(serieIdx);
         }

         return true;
      }
   }

   /**
    * Get dataref of the target index column.
    * @param idx, the column index.
    * @param includeAsc, if include the asc field.
    */
   private DataRef getDataRef(int idx, boolean includeAsc) {
      if(idx >= dataset.getColCount()) {
         return null;
      }

      return GraphUtil.getChartRef(cinfo,
         dataset.getHeader(idx), includeAsc, includeAsc);
   }

   /**
    * Get bursh dataset.
    */
   private BrushDataSet getBrushDataSet() {
      DataSet bursh = odataset;

      while(bursh instanceof DataSetFilter) {
         if(bursh instanceof BrushDataSet) {
            break;
         }

         bursh = ((DataSetFilter) bursh).getDataSet();
      }

      return bursh instanceof BrushDataSet ? (BrushDataSet) bursh : null;
   }

   private int getCol(boolean isDimension, int idx) {
      Integer obj = isDimension ? dimensions.get(idx) : aggregates.get(idx);
      return obj.intValue();
   }

    /**
    * Get line for the specified vo.
    */
   private GLine getLine(ElementVO vo, int serieIdx, int valIdx) {
      if(vo == null) {
         return GLine.THIN_LINE;
      }

      valIdx = valIdx < 0 ? 0 : valIdx;
      ElementGeometry gobj = (ElementGeometry) vo.getGeometry();
      GLine line = gobj.getLine(0);

      if(vo instanceof Pie3DVO) {
         line = gobj.getLine(valIdx);
      }
      else if(vo instanceof LineVO || vo instanceof AreaVO) {
         try {
            line = gobj.getLine(serieIdx);

            LineGeometry lineObj = (LineGeometry) gobj;
            int[] tidxs = lineObj.getTupleIndexes();
            VisualModel vm = gobj.getVisualModel();
            String[] vars = gobj.getVars();

            if(tidxs != null && tidxs.length > 0) {
               GLine li = vm.getLine(vars == null ? null : vars[0], tidxs[0]);
               line = li == null ? line : li;
            }
         }
         catch(Exception e) {
            // ignore
         }
      }

      return line;
   }

   /**
    * Get color for the specified vo.
    */
   private Color getColor(ElementVO vo, int serieIdx, int valIdx) {
      if(vo == null) {
         return Color.white;
      }

      valIdx = valIdx < 0 ? isbrush ? serieIdx :
         0 : valIdx;
      ElementGeometry gobj = (ElementGeometry) vo.getGeometry();
      Color color = gobj.getColor(0);

      if(vo instanceof Pie3DVO) {
         color = gobj.getColor(valIdx);
      }
      else if(vo instanceof LineVO) {
         try {
            color = gobj.getColor(valIdx);

            LineGeometry lineObj = (LineGeometry) gobj;
            int[] tidxs = lineObj.getTupleIndexes();
            VisualModel vm = gobj.getVisualModel();
            String[] vars = gobj.getVars();
            // idxs not include empty point index, and the indexes in idxs
            // are the points indexes in vschart(not same with the serie
            // data indexes). so we should get the color by the not empty id
            // of the valIdx, it may be not very clear, but it's a right way.
            int id = getNotEmptyId(serieIdx, valIdx);

            if(tidxs != null && tidxs.length > id) {
               Color clr = vm.getColor(vars == null ? null : vars[0], tidxs[id]);
               color = clr == null ? color : clr;
            }
         }
         catch(Exception e) {
            // ignore
         }
      }

      ColorFrame frame = gobj.getVisualModel().getColorFrame();

      if(isbrush && frame instanceof CompositeColorFrame) {
         CompositeColorFrame cframe = (CompositeColorFrame) frame;

         if(cframe.getFrameCount() > 1) {
            ColorFrame frame2 = (ColorFrame) cframe.getFrame(1);
            String var = gobj.getVar();

            var = GraphUtil.getOriginalCol(var);

            if(stacked && !allMStack) {
               Integer fvo =
                  vsfmtMap.get(new Point(serieIdx, valIdx));

               if(fvo != null) {
                  color = frame2.getColor(odataset, var, fvo.intValue());
               }
               else {
                  color = frame2.getColor(odataset, var, vo.getSubRowIndex());
               }

               if(color == null) {
                  color = frame2.getColor(dataset, var, valIdx);
               }
            }
            else if(serieIdx == 0 && valIdx == 0) {
               int sindex = vo.getSubRowIndex();
               int idx = sindex - getBrushAdjust();
               idx = idx < 0 ? 0 : idx;
               Color ncolor = frame2.getColor(dataset, var, idx);
               color = ncolor == null ? color : ncolor;
            }
            else {
               Color ncolor = frame2.getColor(dataset, var, valIdx);
               color = ncolor == null ? color : ncolor;
            }
         }
      }

      return color;
   }

   /**
    * Get the mapping index of the valIdx in the non-NAN values of serie.
    */
   private int getNotEmptyId(int serieIdx, int valIdx) {
      double[] data = getSeriesData(serieIdx);

      int idx = 0;

      for(int i = 0; i < data.length; i++) {
         if(i < valIdx && !Double.isNaN(data[i])) {
            idx++;
         }
      }

      return idx;
   }

   /**
    * Get the sub data actually row count.
    */
   private int getBrushAdjust() {
      return brushAdjust;
   }

   /**
    * Get fields used in this vgraph
    */
   public Set getFields() {
      return fields;
   }

   /**
    * Check if xyScate chart.
    */
   public boolean isXYScate() {
      return xyScate;
   }

   /**
    * Get if the chart x y is swapped.
    */
   public boolean isSwapped() {
      return inverted;
   }

   /**
    * To store the binding info index.
    */
   protected class ArrayList2<T> extends ArrayList<Integer> {
      @Override
      public boolean add(Integer val) {
         if(val == null || val < 0 || contains(val)) {
            return false;
         }

         return super.add(val);
      }
   }

   private boolean stacked;
   // the stack column base on aes measure refs
   private boolean allMStack = true;
   private boolean inverted;
   private boolean xyScate;
   private boolean isbrush;
   private boolean hasBrush;

   private int chartStyle = -1;
   private int brushAdjust = 0;
   private VGraph root;
   private VGraph vgraph;
   private ChartXData data;
   private DataSet dataset;
   private DataSet odataset;
   private DataSet rdataset;
   private ChartInfo cinfo;
   private ChartDescriptor descriptor;
   protected Color voColor; // for stock and candle chart
   protected ElementVO vo;

   private Set fields = new HashSet();
   private HashMap<String, Integer> colnames = new HashMap<>();
   private ArrayList<Integer> dimensions = new ArrayList<>();
   private ArrayList<Integer> aggregates = new ArrayList<>();

   private HashMap<String, TextFrame> innerAxisMap = new HashMap<>();
   // save column name and column format pair
   private HashMap<String, XFormatInfo> textFormatMap = new HashMap<>();
   private List<Object> chartStyles = new ArrayList<>();
   private List<Object> secChartStyles = new ArrayList<>();
   private Map<String, DataRef> refsMap = new HashMap<>();
   private HashMap<Point, Integer> vsfmtMap = new HashMap<>();
   private HashMap<Integer, ArrayList2> mixedRowsMap = new HashMap<>();
   private HashMap<Integer, ArrayList> legendsMap = new HashMap<>();
   private static final Random random = new Random();
   private static final int textureCount =
      TextureFrameWrapper.getTextureCount();
   // @by dayvc, when export stack chart, for each row's(series) color we
   // just use one of the row's vo's color, so if some column(s) have no
   // data, then excel will use the row's(series) color to append a vo, so if
   // use HashMap, the exported excel we can not make sure the color for
   // those vo(s) which no data always same, here i changed it to
   // LinkedHashMap to prevent the problem, but some bugs still exist,
   // seems not have better solution, see bug1241595367124
   // @by walle, here is a bug, bug1344263753063, vo hash codes are same.
   private LinkedHashMap index = new LinkedHashMap();// vo -> i:j
   private Color brushHLColor = BrushingColor.getHighlightColor();
   private int[] baseRowMap = null;
}
