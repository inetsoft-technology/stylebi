/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.internal;

import inetsoft.graph.EGraph;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.*;
import inetsoft.graph.geo.service.WebMapService;
import inetsoft.graph.internal.GDefaults;
import inetsoft.report.*;
import inetsoft.report.composition.graph.*;
import inetsoft.report.composition.graph.calc.AbstractColumn;
import inetsoft.report.filter.MetaTableFilter;
import inetsoft.report.internal.binding.*;
import inetsoft.report.internal.graph.ChangeChartTypeProcessor;
import inetsoft.report.internal.info.ChartElementInfo;
import inetsoft.report.internal.info.ElementInfo;
import inetsoft.report.internal.table.*;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.css.CSSConstants;
import inetsoft.util.profile.ProfileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.lang.reflect.Constructor;
import java.util.List;

/**
 * ChartElementDef stores information and attributes used in the chart element.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ChartElementDef extends PainterElementDef
   implements ChartElement, Tabular, Groupable, BindableElement
{
   /**
    * Create a default chart.
    */
   public ChartElementDef() {
      super();
   }

   /**
    * Construct a new ChartElementDef.
    */
   public ChartElementDef(ReportSheet report) {
      super(report, null);
      setPainter(createPainter(getDataSet(), getChartDescriptor(), getChartInfo(), this));
   }

   /**
    * Construct a new ChartElementDef.
    */
   public ChartElementDef(ReportSheet report, DataSet data) {
      super(report, null);
      setDataSet(data);
   }

   /**
    * create a chart with fixed size.
    */
   public ChartElementDef(ReportSheet report, double winch, double hinch) {
      super(report, null, winch, hinch);
   }

   /**
    * CSS Type to be used when styling this element.
    */
   @Override
   public String getCSSType() {
      return CSSConstants.CHART;
   }

   /**
    * create a chart with fixed size.
    */
   public ChartElementDef(ReportSheet report, DataSet data, double winch, double hinch) {
      super(report, null, winch, hinch);
      setDataSet(data);
   }

   /**
    * Create a proper element info to save the attribute of this element.
    */
   @Override
   public ElementInfo createElementInfo() {
      return new ChartElementInfo();
   }

   /**
    * Get the data set of the chart.
    */
   @Override
   public DataSet getDataSet() {
      // @by larryl, use a topchart to hold the chart created from table
      // instead or share the 'chart' variable. This way the chart can be
      // recreated from the table when the binding info is changed
      if(topchart != null) {
         return topchart;
      }

      if(chart != null) {
         topchart = chart;
      }
      else if(basetable != null || toptable != null) {
         if(toptable == null) {
            toptable = basetable;
         }

         ChartInfo cinfo = getChartInfo();
         VSDataRef[] refs = cinfo.getRTFields();

         // funnel always sort by value
         if(GraphTypes.isFunnel(cinfo.getChartType()) && cinfo.getXFieldCount() > 0 &&
            cinfo.getYFieldCount() > 0)
         {
            VSDataRef lastY = cinfo.getYField(cinfo.getYFieldCount() - 1);

            for(int j = 0; j < cinfo.getXFieldCount(); j++) {
               if(cinfo.getXField(j) instanceof ChartAggregateRef) {
                  XAggregateRef firstX = (XAggregateRef) cinfo.getXField(j);
                  int order = ((ChartDimensionRef) lastY).getOrder();
                  ((ChartDimensionRef) lastY).setOrder((order & XConstants.SORT_SPECIFIC) != 0 ?
                     XConstants.SORT_VALUE_ASC | XConstants.SORT_SPECIFIC : XConstants.SORT_VALUE_ASC);
                  // for using percent of total in funnel,
                  // see ChartVSAQuery.applyFunnelSort(). (56677)
                  ((ChartDimensionRef) lastY).setSortByCol(firstX.getFullName(false));
                  break;
               }
            }
         }

         // @by stone, fix bug1320059405682
         ChartRef path = cinfo.getRTPathField();
         int ctype = cinfo.getRTChartType();
         ChartDescriptor desc = getChartDescriptor();

         if(path instanceof ChartAggregateRef) {
            ((ChartAggregateRef) path).setSupportsLine(GraphTypes.supportsLine(ctype, cinfo));
         }

         desc.setSortOthersLast(cinfo);

         VSDataSet chart = new VSDataSet(toptable, refs);

         // @by ChrisSpagnoli bug1422843580389 2015-2-3
         // @by ChrisSpagnoli bug1422602265526 2015-2-5
         if(cinfo != null && getChartDescriptor() != null) {
            if(cinfo.canProjectForward()) {
               PlotDescriptor plotdesc = desc.getPlotDescriptor();

               if(plotdesc != null && plotdesc.getTrendline() != 0) {
                  chart.setRowsProjectedForward(plotdesc.getProjectTrendLineForward());
                  ChartRef primaryDim = cinfo.getPrimaryDimension(true);
                  chart.setProjectColumn(primaryDim != null ? primaryDim.getFullName() : null);
               }
            }
            // @by ChrisSpagnoli bug1427783978948 2015-4-1
            else {
               chart.setRowsProjectedForward(0);
            }
         }

         if(toptable instanceof MetaTableFilter &&
            ((MetaTableFilter) toptable).getTable() instanceof ConcatTableLens)
         {
            setSubColumns(chart, (ConcatTableLens) ((MetaTableFilter) toptable).getTable());
         }
         else if(toptable instanceof ConcatTableLens) {
            setSubColumns(chart, (ConcatTableLens) toptable);
         }

         topchart = chart;
         this.chart = chart;

         if(chart != null) {
            List<XDimensionRef> dims = GraphUtil.getSeriesDimensionsForCalc(cinfo);
            String inner = (dims.size() > 0)
               ? dims.get(dims.size() - 1).getFullName() : null;
            List<CalcColumn> allcalcs = chart.getCalcColumns();

            for(int i = 0; i < allcalcs.size(); i++) {
               if(allcalcs.get(i) instanceof AbstractColumn) {
                  ((AbstractColumn) allcalcs.get(i)).setDimensions(dims);
                  ((AbstractColumn) allcalcs.get(i)).setInnerDim(inner);
               }
            }

            if(allcalcs.size() != 0 && inner != null) {
               // @y billh, fix bug bug1287993826210. We need to
               // always support script properly, for script is more flexible
               // and dynamic in real life
               int index = chart.indexOfHeader(inner);

               if(index < 0) {
                  LOG.warn("Calculated column not found in chart data set: {}", inner);
               }
               else {
                  chart.prepareCalc(inner, null, true);
               }
            }
         }
      }
      else {
         // avoid null chart lens.
         return new DefaultDataSet(new Object[0][0]);
      }

      ReportSheet report = getReport();

      if(report != null && true) {
         LOG.debug("Chart " + getID() + " finished processing " +
                      (topchart.getRowCount() + 1) + " rows");
      }

      return topchart;
   }

   private void setSubColumns(VSDataSet chart, ConcatTableLens toptable) {
      int row = 0;

      for(TableLens tbl : toptable.getTables()) {
         tbl.moreRows(TableLens.EOT);

         if(tbl instanceof FilledTableLens) {
            SubColumns subcols = ((FilledTableLens) tbl).getSubColumns();
            int cnt = tbl.getRowCount() - 1;
            int[] range = {row, row + cnt};

            chart.setSubRange(subcols, range);
            row += cnt;
         }
      }
   }

   /**
    * Set the chart data.
    */
   @Override
   public void setDataSet(DataSet chart) {
      this.chart = chart;
      this.basetable = null;
      resetFilter();

      setPainter(createPainter(chart, getChartDescriptor(), getChartInfo(), this));
      painter2 = null;
   }

   /**
    * Get the chart descriptor.
    */
   @Override
   public ChartDescriptor getChartDescriptor() {
      if(getBindingAttr() == null || getBindingAttr().getBindingOption() == null) {
         return null;
      }

      ChartOption coption = (ChartOption) getBindingAttr().getBindingOption();
      return coption.getChartDescriptor();
   }

   /**
    * Get the report chart info.
    */
   @Override
   public ChartInfo getChartInfo() {
      BindingAttr info = getBindingAttr0();

      if(info == null || info.getBindingOption() == null) {
         return null;
      }

      ChartOption coption = (ChartOption) info.getBindingOption();
      return coption.getChartInfo();
   }

   /**
    * Set the chart descriptor.
    */
   @Override
   public void setChartDescriptor(ChartDescriptor desc) {
      if(getBindingAttr() == null) {
         ((ChartElementInfo) einfo).setBindingAttr(new BindingAttr(ChartElement.class));
      }

      ((ChartOption) getBindingAttr().getBindingOption()).setChartDescriptor(desc);
      setPainter(createPainter(getDataSet(), getChartDescriptor(), getChartInfo(), this));
      painter2 = null;
   }


   /**
    * Set the data in the tabular element.
    */
   @Override
   public void setData(TableLens model) {
      this.basetable = model;

      if(basetable != null) {
         // for Feature #26586, set report name which will be used when add post processing
         // record when process the filters.
         basetable.setProperty(XTable.REPORT_NAME, ProfileUtils.getReportSheetName(getReport()));
         basetable.setProperty(XTable.REPORT_TYPE, ExecutionBreakDownRecord.OBJECT_TYPE_REPORT);
      }

      this.chart = null;
      resetFilter();
      setPainter(createPainter(getDataSet(), getChartDescriptor(), getChartInfo(), this));
      painter2 = null;
   }

   /**
    * Get the base table lens.
    */
   @Override
   public TableLens getBaseTable() {
      return basetable;
   }

   /**
    * Get the table in the chart.
    */
   @Override
   public TableLens getTable() {
      if(toptable == null) {
         toptable = this.basetable;
      }

      return toptable;
   }

   /**
    * Return element type.
    */
   @Override
   public String getType() {
      return "Chart";
   }

   /**
    * Get the table to be used for printing.
    */
   @Override
   public TableLens getTopTable() {
      return toptable;
   }

   /**
    * Set the table in the chart.
    */
   @Override
   public void setTable(TableLens table) {
      setData(table);
   }

   /**
    * Get the binding attribute.
    */
   @Override
   public BindingAttr getBindingAttr() {
      return getBindingAttr0();
   }

   // avoid recursion
   private BindingAttr getBindingAttr0() {
      return ((ChartElementInfo) einfo).getBindingAttr();
   }

   @Override
   public void rewind(Paintable pt) {
      if(egraph == null) {
         // @by jasonshobe, bug1400187780686: the graph will be lost during
         // print after the enclosing section band is rewound and any changes
         // made by the script will no longer be included in the recreated
         // graph, so keep the current graph to be used on the next call to
         // print
         ChartPainter painter = (ChartPainter) getPainter();

         if(painter != null && painter.containsEgraph()) {
            egraph = painter.getEGraph();
         }
         else if(painter2 != null && painter2.containsEgraph()) {
            egraph = painter2.getEGraph();
         }
      }

      super.rewind(pt);
   }

   /**
    * Print the element at the printHead location.
    * @return true if the element is not completely printed and
    * need to be called again.
    */
   @Override
   public boolean print(StylePage pg, ReportSheet report) {
      if(painter2 != null) {
         setPainter(painter2);
         painter2 = null;
      }
      else if(egraph != null) {
         setPainter(createPainter(egraph, getDataSet(), this));
      }
      else {
         // @by larryl, make sure the painter is using the most current table
         // lens and desc in case they are changed from script
         setPainter(createPainter(getDataSet(), getChartDescriptor(),
                                  getChartInfo(), this));
      }

      return super.print(pg, report);
   }

   /**
    * Create a PainterPaintable.
    */
   @Override
   public PainterPaintable createPaintable(float x, float y,
                                           float painterW, float painterH,
                                           Dimension pd, int prefW, int prefH,
                                           ReportElement elem,
                                           Painter painter,
                                           int offsetX, int offsetY,
                                           int rotation) {
      return new ChartPainterPaintable(x, y, painterW, painterH, pd, prefW,
         prefH, elem, painter, offsetX, offsetY, rotation);
   }

   /**
    * Get the data error.
    */
   String getDataError() {
      TableLens table = getTable();
      BindingAttr binding = getBindingAttr();
      boolean querytimeout = table != null && Util.isTimeoutTable(table);

      if(querytimeout) {
         int timeout = Util.getQueryTimeout(this);

         if(timeout > 0 && timeout < Integer.MAX_VALUE) {
            return Catalog.getCatalog().
               getString("designer.common.timeout", getID(), timeout + "");
         }
      }

      return null;
   }

   /**
    * Create a chart painter.
    * @param context docment context.
    */
   public ChartPainter createPainter(EGraph egraph, DataSet data, ReportElement context) {
      ChartPainter painter = new ChartPainter(egraph, data, (ChartElement) context);
      painter.setVGraph(vgraph);

      return painter;
   }

   /**
    * Create a chart painter.
    * @param context docment context.
    */
   public ChartPainter createPainter(DataSet data, ChartDescriptor desc,
      ChartInfo info, ReportElement context)
   {
      GraphFormatUtil.fixDefaultNumberFormat(desc, info);
      String prop = SreeEnv.getProperty("stylereport.chart.painter");

      if(prop != null) {
         try {
            Class cls = Class.forName(Tool.convertUserClassName(prop));
            Constructor ctr = cls.getConstructor(DataSet.class, ChartDescriptor.class,
                                                 ChartInfo.class, ReportElement.class);

            if(ctr != null) {
               return (ChartPainter) ctr.newInstance(new Object[] { data, desc, info, context});
            }
            else {
               LOG.warn(
                  "ChartPainter implementations must have a constructor with " +
                  "the following parameters: DataSet, ChartDescriptor, " +
                  "ChartInfo, ReportElement: {}", prop);
            }
         }
         catch(Exception ex) {
            LOG.warn("Failed to instantiate chart painter: " + prop, ex);
         }
      }

      // @by billh, clone info and desc, so that they are not shared
      // when chart element is repeated in a section/bean/sub report.
      // User might modify info and desc via script, so delink is required.
      if(info != null) {
         info = (ChartInfo) info.clone();
      }

      if(desc != null) {
         desc = (ChartDescriptor) desc.clone();
      }

      if(info != null && desc != null) {
         info.setChartDescriptor(desc);
      }

      ChartPainter painter = new ChartPainter(data, desc, info, (ChartElement) context);
      painter.setVGraph(vgraph);

      return painter;
   }

   /**
    * Return the string representing this element.
    */
   @Override
   public String toString() {
      return getID();
   }

   /**
    * Clone this object.
    */
   @Override
   public Object clone() {
      try {
         ChartElementDef elem = (ChartElementDef) super.clone();

         if(elem.chart == null) {
            elem.setDataSet(getDataSet());
         }

         if(elem.chart instanceof VSDataSet || elem.chart instanceof DefaultDataSet) {
            elem.chart = (DataSet) elem.chart.clone();
         }

         elem.topchart = null; // clear the cache

         elem.setPainter(createPainter(elem.chart, elem.getChartDescriptor(),
                                  elem.getChartInfo(), elem));
         elem.basetable = basetable;
         elem.painter2 = null;
         return elem;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone chart element", ex);
      }

      return null;
   }

   /**
    * Clear cached data.
    */
   @Override
   public void resetFilter() {
      topchart = null;
      toptable = null;
   }

   public int getChartStyle() {
      if(!getChartInfo().isMultiStyles()) {
         return getChartInfo().getChartType();
      }
      else {
         for(ChartRef ref : getChartInfo().getXYMeasures()) {
            ChartAggregateRef aref = (ChartAggregateRef) ref;
            return aref.getChartType();
         }
      }

      return GraphTypes.CHART_AUTO;
   }

   /**
    * Set the hyper link of this element.
    */
   @Override
   public void setHyperlink(Hyperlink link) {
      super.setHyperlink(link);

      if(getChartInfo() instanceof MergedChartInfo) {
         ((MergedChartInfo) getChartInfo()).setHyperlink(link);
      }
      else {
         for(ChartRef ref : getChartInfo().getXYMeasures()) {
            ((ChartAggregateRef) ref).setHyperlink(link);
         }
      }
   }

   public void setChartStyle(int value) {
      if(!getChartInfo().isMultiStyles()) {
         if(getChartStyle() != value) {
            ChartInfo info = new ChangeChartTypeProcessor(
               getChartStyle(), value, false, false, null, getChartInfo(),
               true, getChartDescriptor()).process();

            if(getBindingAttr() != null &&
               getBindingAttr().getBindingOption() != null)
            {
               ChartOption coption = (ChartOption) getBindingAttr().
                  getBindingOption();
               coption.setChartInfo(info);
            }
         }

         getChartInfo().setChartType(value);
      }
      else {
         for(ChartRef ref : getChartInfo().getXYMeasures()) {
            ChartAggregateRef ref0 = (ChartAggregateRef) ref;
            new ChangeChartTypeProcessor(
               getChartStyle(), value, true, true, ref0, getChartInfo(),
               true, getChartDescriptor()).process();

            ref0.setChartType(value);
         }
      }

      // sorting by value needs to be set
      if(GraphTypes.isFunnel(value)) {
         resetFilter();
         chart = null;
      }

      getChartInfo().updateChartType(!getChartInfo().isMultiStyles());
   }

   /**
    * Set the border color. If the border color is not set, the foreground
    * color is used to draw the border.
    */
   public void setBorderColor(Color color) {
      ((ChartElementInfo) einfo).setBorderColor(color);
   }

   @Override
   public void setBorderColors(BorderColors bcolors) {
      ((ChartElementInfo) einfo).setBorderColor(bcolors.topColor);
   }

   /**
    * Get the border color.
    */
   public Color getBorderColor() {
      return ((ChartElementInfo) einfo).getBorderColor();
   }

   @Override
   public BorderColors getBorderColors() {
      BorderColors borderColors = new BorderColors();
      borderColors.topColor = ((ChartElementInfo) einfo).getBorderColor();
      borderColors.leftColor = ((ChartElementInfo) einfo).getBorderColor();
      borderColors.bottomColor = ((ChartElementInfo) einfo).getBorderColor();
      borderColors.rightColor = ((ChartElementInfo) einfo).getBorderColor();
      return borderColors;
   }

   /**
    * Set the individual border line styles. This overrides the default border
    * setting.
    * @param borders line styles.
    */
   @Override
   public void setBorders(Insets borders) {
      ((ChartElementInfo) einfo).setBorders(borders);
   }

   /**
    * Get the individual border line styles.
    * @return border line style..
    */
   @Override
   public Insets getBorders() {
      return ((ChartElementInfo) einfo).getBorders();
   }

   /**
   * Set vgraph for the chart element. This is for vs converted report, and
   * paint the chart using the setted vgraph to provide a safer convertion
   * between vs chart and report chart.
   */
   public void setVGraph(VGraph vgraph) {
      this.vgraph = vgraph;
   }

   @Override
   public void setFont(Font font) {
      super.setFont(font);
      fixChartFormat();
   }

   @Override
   public void setForeground(Color foreground) {
      super.setForeground(foreground);
      fixChartFormat();
   }

   private void fixChartFormat() {
      ChartDescriptor chartDesc = getChartDescriptor();

      if(chartDesc != null) {
         LegendsDescriptor legendsDesc = chartDesc.getLegendsDescriptor();

         if(legendsDesc != null) {
            legendsDesc.initDefaultFormat();
            fixDefaultFormat(legendsDesc.getTitleTextFormat().
               getDefaultFormat());

            LegendDescriptor colorDesc = legendsDesc.getColorLegendDescriptor();

            if(colorDesc != null) {
               colorDesc.initDefaultFormat();
               fixDefaultFormat(colorDesc.getContentTextFormat().
                  getDefaultFormat());
            }

            LegendDescriptor shapeDesc = legendsDesc.getShapeLegendDescriptor();

            if(shapeDesc != null) {
               shapeDesc.initDefaultFormat();
               fixDefaultFormat(shapeDesc.getContentTextFormat().
                  getDefaultFormat());
            }

            LegendDescriptor sizeDesc = legendsDesc.getSizeLegendDescriptor();

            if(sizeDesc != null) {
               sizeDesc.initDefaultFormat();
               fixDefaultFormat(sizeDesc.getContentTextFormat().
                  getDefaultFormat());
            }
         }

         TitlesDescriptor titlesDesc = chartDesc.getTitlesDescriptor();

         if(titlesDesc != null) {
            TitleDescriptor xDesc = titlesDesc.getXTitleDescriptor();

            if(xDesc != null) {
               xDesc.initDefaultFormat();
               fixDefaultFormat(xDesc.getTextFormat().getDefaultFormat());
            }

            TitleDescriptor xDesc2 = titlesDesc.getX2TitleDescriptor();

            if(xDesc2 != null) {
               xDesc2.initDefaultFormat();
               fixDefaultFormat(xDesc2.getTextFormat().getDefaultFormat());
            }

            TitleDescriptor yDesc = titlesDesc.getYTitleDescriptor();

            if(yDesc != null) {
               yDesc.initDefaultFormat();
               fixDefaultFormat(yDesc.getTextFormat().getDefaultFormat());
            }

            TitleDescriptor yDesc2 = titlesDesc.getY2TitleDescriptor();

            if(yDesc2 != null) {
               yDesc2.initDefaultFormat();
               fixDefaultFormat(yDesc2.getTextFormat().getDefaultFormat());
            }
         }

         PlotDescriptor plotDesc = chartDesc.getPlotDescriptor();

         if(plotDesc != null) {
            plotDesc.initDefaultFormat();
            fixDefaultFormat(plotDesc.getTextFormat().getDefaultFormat());
         }

         for(int i = 0; i < chartDesc.getTargetCount(); i++) {
            GraphTarget graphTarget = chartDesc.getTarget(i);
            graphTarget.initDefaultFormat();
            fixDefaultFormat(graphTarget.getTextFormat().getDefaultFormat());
         }
      }

      ChartInfo chartInfo = getChartInfo();

      if(chartInfo instanceof RadarChartInfo) {
         AxisDescriptor axisDesc = ((RadarChartInfo) chartInfo).getLabelAxisDescriptor();

         if(axisDesc != null) {
            axisDesc.initDefaultFormat();
            fixDefaultFormat(axisDesc.getAxisLabelTextFormat().getDefaultFormat());
         }
      }

      if(chartInfo != null) {
         ChartRef[][] nrefs = {chartInfo.getXFields(), chartInfo.getYFields(),
            chartInfo.getGroupFields(), chartInfo.getBindingRefs(true)};

         for(ChartRef[] refs : nrefs) {
            for(ChartRef ref : refs) {
               AxisDescriptor axisDesc = ref.getAxisDescriptor();

               if(axisDesc != null) {
                  axisDesc.initDefaultFormat();
                  fixDefaultFormat(axisDesc.getAxisLabelTextFormat().
                     getDefaultFormat());

                  for(String col : axisDesc.getColumnLabelTextFormatColumns()) {
                     CompositeTextFormat colFmt = axisDesc.getColumnLabelTextFormat(col);

                     if(colFmt != null) {
                        initDefaultFormat(colFmt);
                        fixDefaultFormat(colFmt.getDefaultFormat());
                     }
                  }
               }

               if(ref instanceof ChartDimensionRef) {
                  ((ChartDimensionRef) ref).initDefaultFormat();
                  fixDefaultFormat(ref.getTextFormat().getDefaultFormat());
               }

               if(ref instanceof ChartAggregateRef) {
                  ChartAggregateRef aggr = (ChartAggregateRef) ref;
                  aggr.initDefaultFormat();
                  fixDefaultFormat(aggr.getTextFormat().getDefaultFormat());

                  if(aggr.getTextField() != null &&
                     aggr.getTextField().getDataRef() instanceof ChartRef)
                  {
                     ChartRef textfield = (ChartRef) aggr.getTextField().getDataRef();
                     initDefaultFormat(textfield);
                     fixDefaultFormat(textfield.getTextFormat().getDefaultFormat());
                  }
               }
            }
         }

         VSDataRef[] vsDataRefs = chartInfo.getFields();

         for(VSDataRef ref : vsDataRefs) {
            if(ref instanceof VSChartDimensionRef) {
               VSChartDimensionRef chartDimRef = (VSChartDimensionRef) ref;
               chartDimRef.initDefaultFormat();
               fixDefaultFormat(chartDimRef.getTextFormat().getDefaultFormat());
            }
         }

         for(boolean runtime: new boolean[] { false, true }) {
            for(AestheticRef ref : chartInfo.getAestheticRefs(runtime)) {
               ref.getLegendDescriptor().initDefaultFormat();
               fixDefaultFormat(ref.getLegendDescriptor().getContentTextFormat()
                                    .getDefaultFormat());
            }
         }

         AxisDescriptor axisDesc = chartInfo.getAxisDescriptor();

         if(axisDesc != null) {
            axisDesc.initDefaultFormat();
            fixDefaultFormat(axisDesc.getAxisLabelTextFormat().
               getDefaultFormat());
         }

         AxisDescriptor axisDesc2 = chartInfo.getAxisDescriptor2();

         if(axisDesc2 != null) {
            axisDesc2.initDefaultFormat();
            fixDefaultFormat(axisDesc2.getAxisLabelTextFormat().
               getDefaultFormat());
         }

         if(chartInfo.getTextField() != null) {
            ChartRef ref = (ChartRef) chartInfo.getTextField().getDataRef();

            if(ref != null) {
               initDefaultFormat(ref);
               fixDefaultFormat(ref.getTextFormat().getDefaultFormat());
            }
         }
      }
   }

   private void initDefaultFormat(ChartRef ref) {
      if(ref instanceof ChartDimensionRef) {
         ((ChartDimensionRef) ref).initDefaultFormat();
      }
      else if(ref instanceof ChartAggregateRef) {
         ((ChartAggregateRef) ref).initDefaultFormat();
      }
   }

   private void initDefaultFormat(CompositeTextFormat format) {
      if(format == null) {
         return;
      }

      TextFormat deffmt = format.getDefaultFormat();
      deffmt.setColor(GDefaults.DEFAULT_TEXT_COLOR);
   }

   private void fixDefaultFormat(TextFormat defaultFormat) {
      if(fontByCSS) {
         defaultFormat.setFont(getFont());
      }

      if(foregroundByCSS) {
         defaultFormat.setColor(getForeground());
      }
   }

   public void setFontByCSS(boolean fontByCSS) {
      this.fontByCSS = fontByCSS;
   }

   public void setForegroundByCSS(boolean foregroundByCSS) {
      this.foregroundByCSS = foregroundByCSS;
   }

   @Override
   protected Dimension processPreferredSize(Dimension d) {
      ChartInfo cinfo = getChartInfo();
      Dimension size = super.processPreferredSize(d);

      if(cinfo instanceof MapInfo && getChartDescriptor().getPlotDescriptor().isWebMap()) {
         WebMapService mapService = WebMapService.getWebMapService(null);

         // limit the preferred size of the chart to the limit imposed by web map service,
         // so web map would work in edit/wizard and in default chart size.
         if(mapService != null) {
            int maxSize = mapService.getMaxSize();
            double xratio = getXRatio();
            double yratio = getYRatio();
            size = new Dimension((int) Math.min(maxSize / xratio, size.width),
                                 (int) Math.min(maxSize / yratio, size.height));
         }
      }

      return size;
   }

   public static double getXRatio() {
      double xratio = 1.3; // x size expanding in HTML vs. Java
      String prop = SreeEnv.getProperty("html.ratio.x");

      if(prop != null) {
         xratio = Double.parseDouble(prop);
      }

      return xratio;
   }

   public static double getYRatio() {
      double yratio = 1.3; // y size expanding in HTML vs. Java
      String prop = SreeEnv.getProperty("html.ratio.y");

      if(prop != null) {
         yratio = Double.parseDouble(prop);
      }

      return yratio;
   }

   // for vs converted report, paint the chart by the setted vgraph.
   private VGraph vgraph = null;

   private DataSet chart;
   transient EGraph egraph;
   private transient ChartPainter painter2;
   private transient TableLens basetable; // raw table
   private transient TableLens toptable; // table on the top
   private transient DataSet topchart; // current chart used for painting
   private transient boolean fontByCSS = false;
   private transient boolean foregroundByCSS = false;

   private static final Logger LOG = LoggerFactory.getLogger(ChartElementDef.class);
}
