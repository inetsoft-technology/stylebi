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

import inetsoft.graph.*;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.*;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.*;
import inetsoft.graph.visual.ElementVO;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.graph.BrushDataSet;
import inetsoft.uql.viewsheet.graph.AllChartAggregateRef;
import inetsoft.report.io.viewsheet.excel.ExcelContext;
import inetsoft.uql.ConditionList;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xddf.usermodel.text.*;
import org.apache.poi.xssf.usermodel.*;
import org.openxmlformats.schemas.drawingml.x2006.chart.*;
import org.openxmlformats.schemas.drawingml.x2006.main.*;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.List;
import java.util.*;

/**
 * Special adapter class of XSSFChartElement for exporting chart.
 * Used by ExcelVSExporter.
 *
 * @version 12.2, 7/19/2015
 * @author InetSoft Technology Corp
 */
public class XSSFChartElement {
   /**
    * Constructor, create a new XSSFChartElement for vs chart export.
    */
   public XSSFChartElement() {
   }

   /**
    * Constructor, create a new XSSFChartElement for vs chart export.
    * @param assembly ChartVSAssembly to be drawn.
    * @param data the chart dataset.
    * @param vgraph the this level vgraph.
    * @param root the top level vgraph.
    * @param econtext the ExcelContext for poi export.
    */
   public XSSFChartElement(ChartVSAssembly assembly, DataSet data,
                           VGraph vgraph, VGraph root,
                           ExcelContext econtext)
   {
      DataSet dataset = getSortedDataSet(data, vgraph);
      DataSet odataset = dataset;
      DataSet rdataset = getSortedDataSet(data, root);
      BrushDataSet brushDataSet = getBrushDataSet(dataset);

      if(brushDataSet != null) {
         isbrush = true;
         DataSet allData = brushDataSet.getDataSet(true);
         suncount = brushDataSet.getDataSet(false).getRowCount();

         if(brushDataSet == dataset) {
            dataset = allData;
         }
         else {
            allData.prepareCalc(null, null, true);
            dataset = new Sub2((DataSetFilter) dataset, allData);
         }
      }

      boolean hasBrush = isbrush;

      if(!hasBrush) {
         ConditionList bconds = assembly.getBrushConditionList(null, true);

         if(bconds != null && !bconds.isEmpty()) {
            hasBrush = true;
         }
      }

      this.root = root;
      this.vgraph = vgraph;
      this.econtext = econtext;
      this.descriptor = assembly.getChartDescriptor();

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) assembly.getInfo();
      cinfo = info.getVSChartInfo();
      format = info.getFormat();

      charthelper = new ExcelChartHelper(dataset, odataset, rdataset, vgraph,
                        root, descriptor, cinfo, isbrush, hasBrush, suncount);
   }

   /**
    * Get the workbook.
    */
   protected XSSFWorkbook getWorkbook() {
      return (XSSFWorkbook) econtext.getWorkbook();
   }

   /**
    * Get drawing anchor.
    */
   protected XSSFClientAnchor getAnchor() {
      return new XSSFClientAnchor(0, 0, 0, 0, (short) 1, 1, (short) 7, 21);
   }

   /**
    * Create an excel chart in an excelsheet.
    * @param sheetname the excel sheet name.
    */
   public void generateChart(String sheetname) throws Exception {
      XSSFWorkbook wb = getWorkbook();
      XSSFSheet sheet = wb.getSheet(sheetname);
      sheet = sheet == null ? wb.createSheet(sheetname) : sheet;

      if(charthelper.getSeriesCount() == 0) {
         return;
      }

      XSSFDrawing drawing = sheet.createDrawingPatriarch();
      xchart = drawing.createChart(getAnchor());
      XSSFChartData data = createChartData(xchart,false, 0);
      PlotDescriptor plotDesc = descriptor.getPlotDescriptor();

      if(plotDesc.isFillTimeGap()) {
         CTDispBlanksAs dispBlanksAs = CTDispBlanksAs.Factory.newInstance();
         dispBlanksAs.setVal(plotDesc.isFillZero() ?
            STDispBlanksAs.ZERO : STDispBlanksAs.GAP);
         xchart.getCTChart().setDispBlanksAs(dispBlanksAs);
      }

      chartspace = xchart.getCTChartSpace();
      plotArea = xchart.getCTChart().getPlotArea();

      if(charthelper.isPieChart()) {
         data.fillChart(null, null);
         xchart.plot(data);
      }
      else {
         plotData(data);

         if(charthelper.containsSecondaryY()) {
            plotSecondaryData(data);
         }
      }

      addChartTitle();
      addLegend();
      addChartFormat();
   }

   /**
    * Plots the chartdata on the chart.
    */
   private void plotData(XSSFChartData data) {
      CTCatAx xAxis = null;
      CTValAx yAxis = null;
      CTValAx scateXAxis = null;
      boolean isXYScate = charthelper.isXYScate();
      int style = cinfo.getRTChartType();
      int step = GraphTypes.isRadar(style) ? 1 : 2;

      for(int i = 0; i < vgraph.getAxisCount(); i += step) {
         Axis axis = vgraph.getAxis(i);
         boolean isY = axis instanceof DefaultAxis ?
            !"x".equals(((DefaultAxis) axis).getAxisType()) : false;

         if(!isY) {
            if(isXYScate) {
               scateXAxis =
                  getValAx(axis, plotArea, CATAX_ID, VALAX_ID, false, false);
            }
            else if(xAxis == null) {
               xAxis = getCatAx(axis, plotArea, CATAX_ID, VALAX_ID);
            }
         }
         else {
            List<XDDFNumericalDataSource<Number>> vax = charthelper.getValueAxisDatas();

            for(int j = 0; j < vax.size(); j++) {
               SerieInfo sinfo = charthelper.getSerieInfo(j, style);

               if(yAxis == null && !sinfo.isSecondaryY()){
                  yAxis = getValAx(axis, plotArea, VALAX_ID, CATAX_ID, false);
               }
            }
         }
      }

      if(isXYScate) {
         scateXAxis = scateXAxis != null ?
            scateXAxis : getValAx(plotArea, CATAX_ID, VALAX_ID, isXYScate);
         catAxis = new XDDFValueAxis(scateXAxis);
      }
      else {
         xAxis = xAxis != null ?
            xAxis : getCatAx(plotArea, CATAX_ID, VALAX_ID);
         catAxis = new XDDFCategoryAxis(xAxis);
      }

      xchart.getAxes(); // parse

      if(yAxis != null) {
         valAxis = new XDDFValueAxis(yAxis);
         data.fillChart(catAxis, valAxis);
         xchart.plot(data);
      }
   }

   /**
    * Plots the chartdata on the secondery y of the chart .
    * @param data
    */
   private void plotSecondaryData(XDDFChartData data) {
      int serieCount = data.getSeriesCount();
      boolean isXYScate = charthelper.isXYScate();
      XSSFChartData data2 = createChartData(xchart, true, serieCount);

      if(data2 == null) {
         return;
      }

      CTCatAx xAxis = null;
      CTValAx yAxis = null;
      CTValAx scateXAxis = null;

      if(valAxis != null) {
         if(isXYScate) {
            scateXAxis = getValAx(vgraph.getAxis(1), plotArea,
               SECONDARY_CATAX_ID, SECONDARY_VALAX_ID, false, false);
         }
         else {
            xAxis = getCatAx(vgraph.getAxis(1), plotArea,
               SECONDARY_CATAX_ID, SECONDARY_VALAX_ID);
         }
      }

      yAxis = getValAx(vgraph.getAxis(vgraph.getAxisCount() - 1),
         plotArea, valAxis == null ? VALAX_ID : SECONDARY_VALAX_ID,
         valAxis == null ? CATAX_ID : SECONDARY_CATAX_ID, true);

      secondaryValAxis = new XDDFValueAxis(yAxis);
      XDDFChartAxis catAxis2 = catAxis;

      if(isXYScate && scateXAxis != null) {
         catAxis2 = new XDDFValueAxis(scateXAxis);
      }
      else if(!isXYScate && xAxis != null) {
         catAxis2 = new XDDFCategoryAxis(xAxis);
      }

      data2.fillChart(catAxis2, secondaryValAxis);
      xchart.plot(data2);
   }

   /**
    * Add title for the chart.
    */
   private void addChartTitle() {
      CTChart ctchart = xchart.getCTChart();
      CTTitle title = ctchart.addNewTitle();
      // vs chart have no title, so add empty title then excel will
      // not display the serie name as chart title.
      addTitle(title, new VLabel(""));
   }

   /**
    * Get default invisible CTCatAx.
    */
   private CTCatAx getCatAx(CTPlotArea plotArea, long cid, long vid) {
      CTCatAx catAx = plotArea.addNewCatAx();
      catAx.addNewAxId().setVal(cid);
      catAx.addNewScaling().addNewOrientation().setVal(STOrientation.MIN_MAX);
      catAx.addNewAxPos().setVal(needSwap() ? STAxPos.L : STAxPos.B);
      catAx.addNewDelete().setVal(true);
      catAx.addNewDelete().setVal(false);
      catAx.addNewCrossAx().setVal(vid);
      catAx.addNewCrosses().setVal(STCrosses.AUTO_ZERO);
      catAx.addNewLblAlgn().setVal(STLblAlgn.CTR);
      catAx.addNewLblOffset().setVal(100);

      return catAx;
   }

   /**
    * Return if need swap the catax and valax.
    */
   private boolean needSwap() {
      int style = charthelper.getChartStyle();

      if(cinfo.isMultiStyles()) {
         List<ChartAggregateRef> aggrs = AllChartAggregateRef.getXYAggregateRefs(cinfo, false);
         AllChartAggregateRef ref = new AllChartAggregateRef(cinfo, aggrs);
         style = ref.getRTChartType();
      }

      return GraphTypes.isBar(style) && charthelper.isSwapped();
   }

   /**
    * Get CTCatAx.
    * @param axis the chart axis.
    * @param cid the category axis id.
    * @param vid the category axis cross as id.
    */
   private CTCatAx getCatAx(Axis axis, CTPlotArea plotArea, long cid, long vid)
   {
      CTCatAx catAx = getCatAx(plotArea, cid, vid);
      boolean isSecondaryX = cid == SECONDARY_CATAX_ID;
      boolean showTicks = axis.isTickVisible();
      // secondary x visible is false.
      catAx.getDelete().setVal(isSecondaryX ? true : false);
      catAx.addNewMajorTickMark().setVal(
         showTicks ? STTickMark.IN : STTickMark.NONE);
      catAx.addNewMinorTickMark().setVal(
         showTicks ? STTickMark.IN : STTickMark.NONE);

      Scale scale = axis.getScale();
      AxisSpec aspec = scale.getAxisSpec();
      TextSpec tspec = aspec.getTextSpec();
      double rot = tspec.getRotation();
      Format tformat = tspec.getFormat();
      Color bg = tspec.getBackground();

      if(tformat != null) {
         catAx.setNumFmt(XSSFChartUtil.getNumberFormat(tformat));
      }

      if(scale instanceof TimeScale) {
         TimeScale ts = (TimeScale) scale;
         int increment = ts.getIncrement() == null ?
            1 : ts.getIncrement().intValue();
         catAx.addNewTickLblSkip().setVal(increment);
      }

      boolean hideAxisLine = descriptor != null && descriptor.isSparkline() ||
         !aspec.isLineVisible();
      catAx.addNewTickLblPos().setVal(
         aspec.isLabelVisible() ? STTickLblPos.LOW : STTickLblPos.NONE);
      CTShapeProperties sppr = catAx.addNewSpPr();
      XSSFChartUtil.initShapeProperty(sppr, hideAxisLine, aspec.getLineColor(),
                                      StyleConstants.THIN_LINE, bg);
      CTTextBody txpr = catAx.addNewTxPr();
      XSSFChartUtil.initTextProperty(txpr, tspec.getFont(), tspec.getColor(),
                                     rot);
      CTChartLines gridlines = catAx.addNewMajorGridlines();
      XSSFChartUtil.initGridlineProperty(gridlines, aspec.getGridColor(),
                                         aspec.getGridStyle());

      VLabel tlabel = null;

      if(charthelper.isSwapped()) {
         tlabel = isSecondaryX ? vgraph.getY2Title() : vgraph.getYTitle();
      }
      else {
         tlabel = isSecondaryX ? vgraph.getX2Title() : vgraph.getXTitle();
      }

      if(tlabel != null && tlabel.getLabel() != null &&
         !"".equals(tlabel.getLabel().toString()))
      {
         CTTitle title = catAx.addNewTitle();
         addTitle(title, tlabel);
      }

      return catAx;
   }

   private CTValAx getValAx(CTPlotArea plotArea, long vid, long cid,
      boolean isXYScate)
   {
      CTValAx valAx = plotArea.addNewValAx();
      valAx.addNewAxId().setVal(vid);
      valAx.addNewDelete().setVal(false);
      valAx.addNewAxPos().setVal(STAxPos.B);
      valAx.addNewMajorTickMark().setVal(STTickMark.NONE);
      valAx.addNewMinorTickMark().setVal(STTickMark.NONE);
      valAx.addNewCrossAx().setVal(cid);
      valAx.addNewCrosses().setVal(STCrosses.AUTO_ZERO);
      valAx.addNewCrossBetween().setVal(STCrossBetween.BETWEEN);

      if(isXYScate) {
         valAx.addNewScaling().addNewOrientation().setVal(STOrientation.MIN_MAX);
      }

      return valAx;
   }

   /**
    * Get CTValAx.
    * @param axis the chart axis.
    * @param vid the value axis id.
    * @param cid the value axis cross as id.
    */
   private CTValAx getValAx(Axis axis, CTPlotArea plotArea, long vid, long cid,
                            boolean isSecondaryY)
   {
      return getValAx(axis, plotArea, vid, cid, isSecondaryY, true);
   }
   /**
    * Get CTValAx.
    * @param axis the chart axis.
    * @param vid the value axis id.
    * @param cid the value axis cross as id.
    */
   private CTValAx getValAx(Axis axis, CTPlotArea plotArea, long vid, long cid,
                            boolean isSecondaryY, boolean isY)
   {
      boolean containsSecondaryY = charthelper.containsSecondaryY();
      boolean showTicks = axis.isTickVisible();
      CTValAx valAx = plotArea.addNewValAx();
      valAx.addNewAxId().setVal(vid);
      valAx.addNewDelete().setVal(false);
      valAx.addNewAxPos().setVal(needSwap() ? isSecondaryY ?
         STAxPos.T : STAxPos.B : STAxPos.L);
      valAx.addNewMajorTickMark().setVal(
         showTicks ? STTickMark.IN : STTickMark.NONE);
      valAx.addNewMinorTickMark().setVal(
         showTicks ? STTickMark.IN : STTickMark.NONE);
      valAx.addNewCrossAx().setVal(cid);
      valAx.addNewCrosses().setVal(
         isSecondaryY ? STCrosses.MAX : STCrosses.AUTO_ZERO);
      valAx.addNewCrossBetween().setVal(STCrossBetween.BETWEEN);

      Scale scale = axis.getScale();
      AxisSpec aspec = scale.getAxisSpec();
      TextSpec tspec = aspec.getTextSpec();
      double rot = tspec.getRotation();
      Format tformat = tspec.getFormat();
      Color bg = tspec.getBackground();

      if(tformat != null && tformat instanceof DecimalFormat) {
         valAx.setNumFmt(XSSFChartUtil.getNumberFormat(tformat));
      }

      valAx.addNewTickLblPos().setVal(
         aspec.isLabelVisible() ? STTickLblPos.NEXT_TO : STTickLblPos.NONE);

      boolean islog = scale instanceof LogScale;
      double min = 0;
      double max = 0;
      double major = 0;
      double minor = 0;

      if(scale instanceof LinearScale) {
         LinearScale ls = (LinearScale) scale;
         min = ls.getUserMin() == null ? 0 : ls.getUserMin().doubleValue();
         max = ls.getUserMax() == null ? 0 : ls.getUserMax().doubleValue();
         major = ls.getIncrement() == null ? 0 :
            ls.getIncrement().doubleValue();
         minor = ls.getMinorIncrement() == null ? 0 :
            ls.getMinorIncrement().doubleValue();
         islog &= ls.getMin() >= 0;
         String flds[] = scale.getFields();

         // for a fake column max is 2 means point at middle,
         // so we shouldn't set max here.
         if(flds.length > 0 && flds[0].equals("value") && max == 2) {
            max = 0;
         }
      }

      XSSFChartUtil.addValueRange(valAx, min, max, major, minor, islog);
      boolean hideAxisLine = descriptor != null && descriptor.isSparkline() ||
         !aspec.isLineVisible();
      CTShapeProperties sppr = valAx.addNewSpPr();
      XSSFChartUtil.initShapeProperty(sppr, hideAxisLine, aspec.getLineColor(),
                                      StyleConstants.THIN_LINE, bg);
      CTTextBody txpr = valAx.addNewTxPr();
      XSSFChartUtil.initTextProperty(txpr, tspec.getFont(), tspec.getColor(),
                                     rot);

      if(!containsSecondaryY || containsSecondaryY && isSecondaryY) {
         CTChartLines gridlines = valAx.addNewMajorGridlines();
         XSSFChartUtil.initGridlineProperty(gridlines, aspec.getGridColor(),
                                            aspec.getGridStyle());
      }

      VLabel tlabel = null;

      if(charthelper.isSwapped() || !isY) {
         tlabel = isSecondaryY ? vgraph.getX2Title() : vgraph.getXTitle();
      }
      else {
         tlabel = isSecondaryY ? vgraph.getY2Title() : vgraph.getYTitle();
      }

      if(tlabel != null && tlabel.getLabel() != null &&
         !"".equals(tlabel.getLabel().toString()))
      {
         CTTitle title = valAx.addNewTitle();
         addTitle(title, tlabel);
      }

      return valAx;
   }

   /**
    * Get corresponding chartdata.
    */
   private XSSFChartData createChartData(XSSFChart chart, boolean secondaryY, int num) {
      if(cinfo.isMultiStyles() && !charthelper.isPieChart()) {
         return createMultiChartData(chart, secondaryY, num);
      }

      int style = charthelper.getChartStyle();
      XSSFChartData chartdata =
         XSSFChartUtil.createChartData(chart, style, charthelper.isXYScate());

      XDDFCategoryDataSource xs = charthelper.getCategoryAxisData();
      List<XDDFNumericalDataSource<Number>> ys = charthelper.getValueAxisDatas();
      String[] seriesTitle = charthelper.getSeriesName();

      for(int i = 0; i < ys.size(); i++) {
         SerieInfo sinfo = charthelper.getSerieInfo(i, style);

         if(secondaryY != sinfo.isSecondaryY()) {
            continue;
         }

         chartdata.addSeries(num, num, sinfo, xs, ys.get(i));
         num++;
      }

      if(chartdata instanceof XSSFBarChartData) {
         ((XSSFBarChartData) chartdata).setSwapped(needSwap());
      }

      return chartdata;
   }

   /**
    * Get the multi chartdata.
    */
   private XSSFMultiChartData createMultiChartData(XSSFChart chart, boolean secondaryY, int num) {
      XSSFMultiChartData multiChartData = new XSSFMultiChartData(chart);
      List<Object> chartstyles = charthelper.getChartStyles();
      XDDFCategoryDataSource xs = charthelper.getCategoryAxisData();

      if(chartstyles.size() == 0) {
         return multiChartData;
      }

      List<Integer> list = new ArrayList<>();

      for(int i = 0; i < chartstyles.size(); i++) {
         int style = (Integer) ((Object[]) chartstyles.get(i))[1];

         if(list.contains(style)) {
            continue;
         }

         list.add(style);
         int[] indexes = charthelper.getSerieIndexs(style);
         String[] titles = charthelper.getSeriesName(style);

         for(int j = 0; j < indexes.length; j++) {
            XDDFNumericalDataSource<Number> ys =
               charthelper.getValueAxisData(indexes[j]);
            SerieInfo sinfo = charthelper.getSerieInfo(indexes[j], style);

            if(secondaryY != sinfo.isSecondaryY()) {
               continue;
            }

            multiChartData.addSeries(num, num, sinfo, xs, ys);
            num++;
         }
      }

      multiChartData.setSwapped(needSwap());

      return multiChartData;
   }

   /**
    * Add legend for excel chart.
    */
   private void addLegend() {
      LegendsDescriptor desc =
         descriptor == null ? null : descriptor.getLegendsDescriptor();
      LegendGroup lgroup = getLegendGroup();
      boolean visible = false;

      if(desc == null || lgroup == null) {
         return;
      }

      // don't show legend if the legends are not for this sub-graph
      for(int i = 0; i < lgroup.getLegendCount(); i++) {
         String field = lgroup.getLegend(i).getVisualFrame().getField();

         // If measures as the legend, show the legend.
         if(field == null) {
            if(charthelper.getSeriesCount() > 1) {
               visible = true;
               break;
            }
         }
         else {
            if(charthelper.getFields().contains(field)) {
               visible = true;
               break;
            }
         }
      }

      if(!visible && charthelper.getSeriesRefCnt() >
         charthelper.getSeriesIdx().size() ||
         charthelper.getChartStyles().size() > 0)
      {
         visible = true;
      }

      if(!visible) {
         return;
      }

      visible = false;
      LegendDescriptor legends[] = {desc.getShapeLegendDescriptor(),
                                    desc.getColorLegendDescriptor(),
                                    desc.getSizeLegendDescriptor()};

      for(LegendDescriptor legend : legends) {
         if(legend != null && legend.isVisible()) {
            visible = true;
            break;
         }
      }

      if(!visible && charthelper.getSeriesRefCnt() >
         charthelper.getSeriesIdx().size() ||
         charthelper.getChartStyles().size() > 0)
      {
         visible = true;
      }

      if(!visible) {
         return;
      }

      LegendPosition pos = null;

      switch(lgroup.getLayout()) {
      case GraphConstants.NONE:
         return;
      case GraphConstants.LEFT:
         pos = LegendPosition.LEFT;
         break;
      case GraphConstants.TOP :
         pos = LegendPosition.TOP;
         break;
      case GraphConstants.BOTTOM :
         pos = LegendPosition.BOTTOM;
         break;
      default:
         pos = LegendPosition.RIGHT;
         break;
      }

      final XDDFChartLegend legend = xchart.getOrAddLegend();
      legend.setPosition(pos);
      LegendSpec legendSpec = null;

      if(lgroup != null && lgroup.getLegendCount() > 0) {
         legendSpec = lgroup.getLegend(0).getVisualFrame().
            getLegendSpec();
      }

      if(legendSpec == null) {
         return;
      }

      TextSpec spec = legendSpec.getTextSpec();
      int scount = charthelper.getSeriesCount();

      // set font and foreground for every legend entry.
      for(int i = 0; i < scount; i++) {
         final XDDFLegendEntry legendEntry = legend.addEntry();
         legendEntry.setIndex(i);
         addLegendEntryFormat(legendEntry, spec);
      }

      final CTShapeProperties spPr = CTShapeProperties.Factory.newInstance();
      legend.setShapeProperties(spPr);

      // set background for legend group.
      if(spec.getBackground() != null) {
         spPr.setSolidFill(XSSFChartUtil.getSolidFill(spec.getBackground()));
      }

      // set border for legend group.
      if(legendSpec.getBorder() != StyleConstants.NO_BORDER) {
         CTLineProperties ln = spPr.addNewLn();
         ln.setPrstDash(XSSFChartUtil.getBorderStyle(legendSpec.getBorder()));
         Color bcolor = legendSpec.getBorderColor();

         if(bcolor != null) {
            ln.setSolidFill(XSSFChartUtil.getSolidFill(bcolor));
         }
      }
   }

   /**
    * Set font and foreground for legend entry.
    */
   private void addLegendEntryFormat(XDDFLegendEntry legendEntry, TextSpec spec) {
      Color fcolor = spec.getColor();
      Font font = spec.getFont();
      Format fmt = spec.getFormat();

      final XDDFTextBody txPr = new XDDFTextBody(legendEntry);
      legendEntry.setTextBody(txPr);

      final XDDFBodyProperties bodyPr = txPr.getBodyProperties();
      final XDDFTextParagraph p = txPr.addNewParagraph();
      final XDDFRunProperties defRPr = p.addDefaultRunProperties();

      if(fcolor != null) {
         defRPr.setFillProperties(new XDDFSolidFillProperties(XSSFChartUtil.getSolidFill(fcolor)));
      }

      if(font != null) {
         XSSFChartUtil.addFontProperties(defRPr, font);
      }
   }

   /**
    * Add format for the
    */
   private void addChartFormat() {
      Color bg = format.getBackground();
      bg = bg == null ? Color.white : bg;

      // set bg for chartspace.
      CTShapeProperties chartspaceSpPr = chartspace.addNewSpPr();
      chartspaceSpPr.setSolidFill(XSSFChartUtil.getSolidFill(bg));

      // set bg for plotarea.
      PlotDescriptor plotDesc = descriptor.getPlotDescriptor();
      CTShapeProperties plotAreaSpPr = plotArea.addNewSpPr();
      Color plotBg = plotDesc.getBackground();

      if(plotBg == null) {
         plotAreaSpPr.addNewNoFill();
      }
      else {
         plotAreaSpPr.setSolidFill(XSSFChartUtil.getSolidFill(plotBg));
      }

      // set border for chartspace.
      Color bcolor = format.getBorderColors() == null ? null :
         format.getBorderColors().topColor;
      int lineStyle = format.getBorders() == null ? StyleConstants.NONE :
         format.getBorders().top;

      if(lineStyle != StyleConstants.NONE) {
         CTLineProperties ln = chartspaceSpPr.addNewLn();

         if(lineStyle == StyleConstants.DOUBLE_LINE) {
            ln.setCmpd(STCompoundLine.DBL);
            ln.setW(25400);
         }

         ln.setSolidFill(XSSFChartUtil.getSolidFill(bcolor));
         ln.setPrstDash(XSSFChartUtil.getBorderStyle(lineStyle));
      }
   }

   /**
    * Set axistitle properites.
    */
   private void addTitle(CTTitle ctitle, VLabel vlabel) {
      TextSpec spec = vlabel.getTextSpec();
      Color color = spec.getColor();
      Color bg = spec.getBackground();
      Font font = spec.getFont();
      Format fmt = spec.getFormat();
      double rotation = spec.getRotation();
      String text = vlabel.getLabel() + "";

      if(fmt != null) {
         text = GTool.format(fmt, text);
      }

      CTTx tx = ctitle.addNewTx();
      CTTextBody rich = tx.addNewRich();
      CTTextBodyProperties bodyPr = rich.addNewBodyPr();
      bodyPr.setRot(XSSFChartUtil.getRotation(rotation));
      CTTextParagraph p = rich.addNewP();
      CTTextParagraphProperties pPr = p.addNewPPr();
      CTTextCharacterProperties defRPr = pPr.addNewDefRPr();

      // create a run element, run element is the lowest level text seperation
      // mechanism within a text body. A text run can contain text run
      // properties associated with the run. if no properties are listed
      // then properties specified in the defRPr element are uesd.
      CTRegularTextRun r = p.addNewR();
      r.setT(text);
      CTTextCharacterProperties rPr = r.addNewRPr();

      // set font color for current text run.
      if(color != null) {
         rPr.setSolidFill(XSSFChartUtil.getSolidFill(color));
      }

      XSSFChartUtil.addFontProperties(new XDDFRunProperties(rPr), font);

      if(bg != null) {
         CTShapeProperties spPr = ctitle.addNewSpPr();
         spPr.setSolidFill(XSSFChartUtil.getSolidFill(bg));
      }

      ctitle.addNewOverlay().setVal(false);
   }

   /**
    * If a vgraph is a child graph, get legend group from it's parent.
    */
   private LegendGroup getLegendGroup() {
      LegendGroup group = vgraph.getLegendGroup();

      if(group == null) {
         group = root.getLegendGroup();
      }

      return group;
   }

   /**
    * Get brush dataSet.
    */
   private BrushDataSet getBrushDataSet(DataSet dataset) {
      if(dataset instanceof BrushDataSet) {
         return (BrushDataSet) dataset;
      }
      else if(dataset instanceof DataSetFilter) {
         return getBrushDataSet(((DataSetFilter) dataset).getDataSet());
      }
      else {
         return null;
      }
   }

   /**
    * Get the sorted data set.
    */
   protected DataSet getSortedDataSet(DataSet data, VGraph vgraph) {
      return getSortedDataSet0(data, vgraph);
   }

   /**
    * Get the sorted data set.
    */
   protected DataSet getSortedDataSet0(DataSet data, VGraph vgraph) {
      data = vgraph.getCoordinate().getDataSet();

        // not support show Total of waterfall
      if(data instanceof SumDataSet) {
         data = ((SumDataSet) data).getDataSet();
      }

      if(vgraph.getCoordinate() instanceof RectCoord) {
         RectCoord rcoord = (RectCoord) vgraph.getCoordinate();
         Scale xscale = rcoord.getXScale();
         Scale yscale = rcoord.getYScale();
         String[] fields = null;

         if(xscale != null && !(xscale instanceof LinearScale)) {
            fields = xscale.getFields();
         }
         else if(yscale != null && !(yscale instanceof LinearScale)) {
            fields = yscale.getFields();
         }

         if(fields != null && fields.length > 0) {
            return new SortedDataSet(data, fields[0]);
         }
      }

      return data;
   }

   /**
    * Apply the start/end row.
    */
   private DataSet getSubDataSet(VGraph vgraph) {
      DataSet data = vgraph.getCoordinate().getDataSet();
      int start = Integer.MAX_VALUE;
      int end = -1;

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable vi = vgraph.getVisual(i);

         if(vi instanceof ElementVO) {
            ElementVO elemVO = (ElementVO) vi;
            GraphElement elem = (GraphElement) elemVO.getGraphable();

            if(elem == null) {
               continue;
            }

            if(elem.getStartRow() >= 0) {
               start = Math.min(start, elem.getStartRow());
            }

            if(elem.getEndRow() > 0) {
               end = Math.max(end, elem.getEndRow());
            }
         }
      }

      if(start >= 0 && start < Integer.MAX_VALUE && end >= start) {
         data = new SubDataSet(data, start, end);
      }

      return data;
   }

   private class Sub2 extends AbstractDataSet {
      DataSetFilter sub;
      DataSet all;
      ArrayList rows = new ArrayList();

      public Sub2(DataSetFilter sub, DataSet all) {
         this.sub = sub;
         this.all = all;
         //fixed bug #24105 that about sub's ArrayIndexOutOfBoundsException
         int rowCount = sub instanceof AbstractDataSet ?
            ((AbstractDataSet)sub).getRowCountUnprojected() : sub.getRowCount();

         for(int i = 0; i < rowCount; i++) {
            if(isAllDataSetRow(sub, i)) {
               rows.add(i);
            }
         }
      }

      /**
       * Check if the specified row in all data set.
       */
      private boolean isAllDataSetRow(DataSet dataset, int r) {
         if(r < 0) {
            return true;
         }

         if(dataset instanceof BrushDataSet) {
            return ((BrushDataSet) dataset).getBaseRow(r) < 0;
         }
         else if(dataset instanceof DataSetFilter) {
            DataSetFilter filter = (DataSetFilter) dataset;
            r = ((DataSetFilter) dataset).getBaseRow(r);
            return isAllDataSetRow(filter.getDataSet(), r);
         }

         return true;
      }

      @Override
      protected int getColCount0() {
         return all.getColCount();
      }

      @Override
      protected Comparator getComparator0(String col) {
         return all.getComparator(col);
      }

      @Override
      protected Object getData0(int col, int row) {
         int r = getRow(row);

         if(r < 0) {
            return null;
         }

         return all.getData(col, r);
      }

      /**
       * Mapping row in dataSet filter to all dataSet.
       */
      private int getRow(int row) {
         if(row >= rows.size()) {
            return -1;
         }

         row = ((Integer) rows.get(row)).intValue();
         return getRow0(sub, row);
      }

      private int getRow0(DataSetFilter filter, int row) {
         DataSet data = filter.getDataSet();
         row = filter.getBaseRow(row);

         if(data instanceof BrushDataSet) {
            return row - ((BrushDataSet) data).getDataSet().getRowCount();
         }
         else if(data instanceof DataSetFilter) {
            return getRow0((DataSetFilter) data, row);
         }

         return -1;
      }

      @Override
      protected String getHeader0(int col) {
         return all.getHeader(col);
      }

      @Override
      protected int getRowCount0() {
         return rows.size();
      }

      // placeholder for now, unprojected count would be non-trivial,
      // TBD if needed
      @Override
      protected int getRowCountUnprojected0() {
         return getRowCount0();
      }

      @Override
      protected Class getType0(String col) {
         return all.getType(col);
      }

      @Override
      protected int indexOfHeader0(String col, boolean all) {
         if(this.all instanceof AbstractDataSet) {
            return ((AbstractDataSet) this.all).indexOfHeader(col, all);
         }

         return this.all.indexOfHeader(col);
      }

      @Override
      protected boolean isMeasure0(String col) {
         return all.isMeasure(col);
      }
   }

   private boolean isbrush;
   private int chartStyle = -1;
   private int suncount;

   protected VGraph vgraph;
   protected VGraph root;
   protected ChartDescriptor descriptor;
   protected ChartInfo cinfo;
   protected VSCompositeFormat format;

   private XSSFChart xchart;
   private XDDFChartAxis catAxis;
   private XDDFValueAxis valAxis;
   private XDDFValueAxis secondaryValAxis;
   private CTChartSpace chartspace;
   private CTPlotArea plotArea;

   protected ExcelContext econtext;
   protected ExcelChartHelper charthelper = null;
   private static final long CATAX_ID = 0;
   private static final long VALAX_ID = 1;
   private static final long SECONDARY_CATAX_ID = 2;
   private static final long SECONDARY_VALAX_ID = 3;
}
