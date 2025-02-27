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
package inetsoft.report.composition.graph;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.*;
import inetsoft.graph.data.*;
import inetsoft.graph.element.*;
import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.guide.form.*;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.*;
import inetsoft.graph.schema.*;
import inetsoft.graph.visual.ElementVO;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.report.composition.graph.calc.AbstractColumn;
import inetsoft.report.composition.graph.calc.ChangeColumn;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.report.filter.*;
import inetsoft.report.internal.GraphColumns;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.BrushingColor;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameContext;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.text.*;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static inetsoft.uql.viewsheet.graph.GraphTypes.*;

/**
 * GraphGenerator generates element graph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class GraphGenerator {
   /**
    * Get the graph generator.
    */
   public static GraphGenerator getGenerator(
      ChartVSAssemblyInfo chart, DataSet adata, DataSet data, VariableTable vars, DataSet vdata,
      int sourceType, Dimension size)
   {
      VSChartInfo info = chart.getVSChartInfo();
      ChartDescriptor desc = chart.getRTChartDescriptor() == null ?
         chart.getChartDescriptor() : chart.getRTChartDescriptor();
      DateComparisonInfo dateComparison =
         DateComparisonUtil.getDateComparison(chart, chart.getViewsheet());
      prepareDataSet(info, data, adata, desc, dateComparison);
      GraphUtil.fixDefaultFormat(info, desc, data);

      // radar? always not separated
      if(info instanceof RadarVSChartInfo) {
         return new RadarGraphGenerator(chart, adata, data, vars, vdata, sourceType, size);
      }
      // stock? always not separated
      else if(info instanceof StockVSChartInfo) {
         return new StockGraphGenerator(chart, adata, data, vars, vdata, sourceType, size);
      }
      // candle? always not separated
      else if(info instanceof CandleVSChartInfo) {
         return new CandleGraphGenerator(chart, adata, data, vars, vdata, sourceType, size);
      }
      else if(info instanceof VSMapInfo) {
         return new MapGenerator(chart, adata, data, vars, vdata, sourceType, size);
      }
      else if(info instanceof GanttVSChartInfo) {
         return new GanttGraphGenerator(chart, adata, data, vars, vdata, sourceType, size);
      }
      // separated?
      else if(info.isSeparatedGraph()) {
         return new SeparateGraphGenerator(chart, adata, data, vars, vdata, sourceType, size);
      }

      return new DefaultGraphGenerator(chart, adata, data, vars, vdata, sourceType, size);
   }

   /**
    * Get the graph generator.
    */
   public static GraphGenerator getGenerator(ChartInfo info, ChartDescriptor desc, String vsrc,
                                             DataSet adata, DataSet data, VariableTable vars,
                                             int sourceType, Dimension size)
   {
      ((AbstractChartInfo) info).fixTimeSeries();
      prepareDataSet(info, data, adata, desc, null);
      GraphUtil.fixDefaultFormat(info, desc, data);

      // radar? always not separated
      if(info instanceof RadarChartInfo) {
         return new RadarGraphGenerator(info, desc, adata, data, vars, vsrc, sourceType, size);
      }
      // stock? always not separated
      else if(info instanceof StockChartInfo) {
         return new StockGraphGenerator(info, desc, adata, data, vars, vsrc, sourceType, size);
      }
      // candle? always not separated
      else if(info instanceof CandleChartInfo) {
         return new CandleGraphGenerator(info, desc, adata, data, vars, vsrc, sourceType, size);
      }
      else if(info instanceof MapInfo) {
         return new MapGenerator(info, desc, adata, data, vars, vsrc, sourceType, size);
      }
      else if(info instanceof GanttChartInfo) {
         return new GanttGraphGenerator(info, desc, adata, data, vars, vsrc, sourceType, size);
      }
      // separated?
      else if(info.isSeparatedGraph()) {
         return new SeparateGraphGenerator(info, desc, adata, data, vars, vsrc, sourceType, size);
      }

      return new DefaultGraphGenerator(info, desc, adata, data, vars, vsrc, sourceType, size);
   }

   /**
    * Create one combined color frame for graph.
    */
   public static ColorFrame createCombinedColorFrame(EGraph graph) {
      Set<String> vars = new HashSet<>();
      int cnt = graph.getElementCount();

      if(cnt <= 1) {
         return null;
      }

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);
         // don't use getVarCount() so we can ignore SchemaElement too (BoxPlot)
         int vcnt = elem.getVars().length;

         // multi-var chart? do nothing
         if(vcnt != 1) {
            return null;
         }

         vars.add(elem.getVar(0));
      }

      // bound to the same column, ignore
      if(vars.size() == 1) {
         return null;
      }

      // create one categorical color frame
      String[] names = new String[vars.size()];
      vars.toArray(names);
      Color[] colors = new Color[names.length];
      int max = CategoricalColorFrame.COLOR_PALETTE.length;

      for(int i = 0; i < colors.length; i++) {
         colors[i] = CategoricalColorFrame.COLOR_PALETTE[i % max];
      }

      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.init(names, colors);
      return frame;
   }

   /**
    * Create element graph internally.
    */
   protected abstract void createEGraph0();

   /**
    * Get the axis descriptor of a column.
    */
   protected abstract AxisDescriptor getAxisDescriptor(String col);

   /**
    * Constructor.
    * @param chart the specified chart.
    * @param adata the data generated without brushing, null if no brushing.
    * @param data the data generated with brushing.
    * @param vars the specified variable table.
    */
   protected GraphGenerator(ChartVSAssemblyInfo chart, DataSet adata, DataSet data,
                            VariableTable vars, DataSet vdata, int sourceType, Dimension size)
   {
      super();

      this.graphSize = size;
      this.bconds = chart.getBrushConditionList(null, false);
      this.zconds = chart.getZoomConditionList(null);
      adata = getFixedDataSet(chart.getVSChartInfo(), adata, true);
      data = getFixedDataSet(chart.getVSChartInfo(), data, false);
      this.origInfo = chart.getVSChartInfo();
      this.info = getFixedInfo(chart.getVSChartInfo(), data);
      this.desc = chart.getRTChartDescriptor() == null ?
         chart.getChartDescriptor() : chart.getRTChartDescriptor();
      this.desc0 = chart.getChartDescriptor();
      this.dateComparison = DateComparisonUtil.getDateComparison(chart, chart.getViewsheet());

      // check for discrete measures and if found, wrap dataset with an aliased dataset.
      adata = getDiscreteMeasureDataSet(adata, this.info);
      data = getDiscreteMeasureDataSet(data, this.info);
      vdata = getDiscreteMeasureDataSet(vdata, this.info);

      this.adata = getDataSet(adata);
      this.odata = getDataSet(data);
      adata = (DataSet) (adata == null ? null : adata.clone());
      data = data == null ? null : data.clone(true);
      this.data = getBrushDataSet(adata, data);
      this.vars = vars;
      this.graph = new EGraph();
      this.vs = chart.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(chart.getName());
      this.cubeType = assembly == null ? null : VSAQuery.getCubeType(assembly);

      xdims = new ArrayList<>();
      xmeasures = new ArrayList<>();
      ydims = new ArrayList<>();
      ymeasures = new ArrayList<>();
      xdimlabels = new HashMap<>();
      ydimlabels = new HashMap<>();
      xmeasurelabels = new HashMap<>();
      ymeasurelabels = new HashMap<>();
      scales = new HashMap<>();
      rotatedCoords = new HashSet<>();

      if(adata != null) {
         adata = (DataSet) adata.clone();
         adata.prepareCalc(null, null, true);
      }

      if(data != null) {
         data.prepareCalc(null, null, true);
      }

      // fdata is used to initialize the visual frame
      DataSet fdata = adata == null ? data : adata;

      synchronized(info) {
         // for sharing color frames and viewsheet color mappings across charts
         if(vs != null) {
            final CategoricalColorFrameContext context = CategoricalColorFrameContext.getContext();

            if(info.isMultiAesthetic()) {
               List<Map<String, Color>> colorMaps = new ArrayList<>();

               for(ChartRef ref : info.getBindingRefs(true)) {
                  if(ref instanceof ChartAggregateRef) {
                     if(((ChartAggregateRef) ref).getColorField() != null) {
                        final AestheticRef colorField = ((ChartAggregateRef) ref).getColorField();
                        colorMaps.add(vs.getDimensionColors(colorField.getFullName()));
                     }
                  }
               }

               if(colorMaps.size() > 0) {
                  Map<String, Color> mergedMap = colorMaps.stream().reduce(new HashMap<>(), (a, b) -> {
                     a.putAll(b);
                     return a;
                  });

                  context.setDimensionColors(mergedMap);
               }
            }
            else {
               AbstractChartInfo.getAestheticRefs(info)
                  .stream().filter(a -> a.getVisualFrame() instanceof CategoricalColorFrame)
                  .findFirst()
                  .ifPresent(colorField -> {
                     String name = colorField.getFullName();
                     context.setDimensionColors(vs.getDimensionColors(name));
                  });
            }

            context.setSharedFrames(vs.getSharedFrames());
         }

         // create color frame
         boolean global = !info.isMultiAesthetic();
         String vsrc = GraphUtil.getVisualSource(chart);

         cstrategy = new VSColorFrameStrategy(info);
         cvisitor = new VSFrameVisitor(info, fdata, cstrategy, global, vdata, cubeType, vsrc,
            sourceType, dateComparison, getFrameInitializer());

         if(vars != null) {
            try {
               setQuerySandbox(vars.get("querySandbox"));
               vars.remove("querySandbox");
            }
            catch(Exception ex) {
            }
         }

         applyHighlight();
         applyBrushing();

         // create shape frame
         shpstrategy = new VSShapeFrameStrategy(info);
         shpvisitor = new VSFrameVisitor(info, fdata, shpstrategy, global,
            vdata, cubeType, vsrc, sourceType, getFrameInitializer());
         // create texture frame
         tstrategy = new VSTextureFrameStrategy(info);
         tvisitor = new VSFrameVisitor(info, fdata, tstrategy, global, vdata,
            cubeType, vsrc, sourceType, getFrameInitializer());
         // create line frame
         lnstrategy = new VSLineFrameStrategy(info);
         lnvisitor = new VSFrameVisitor(info, fdata, lnstrategy, global, vdata,
            cubeType, vsrc, sourceType, getFrameInitializer());
         // create size frame
         szstrategy = new VSSizeFrameStrategy(info);
         szvisitor = new VSFrameVisitor(info, fdata, szstrategy, global, null,
            cubeType, vsrc, sourceType, getFrameInitializer());
         // create text frame
         txtstrategy = new VSTextFrameStrategy(info);
         txtvisitor = new VSFrameVisitor(info, fdata, txtstrategy, global, null,
            cubeType, vsrc, sourceType, getFrameInitializer());
         maxMode = vs != null && vs.isMaxMode();
      }
   }

   /**
    * Apply brush.
    */
   protected void applyBrushing() {
      // not brushing target?
      if(adata == null) {
         // brushing source?
         if(isBrushingSource()) {
            voComparator = new BrushingComparator();
            setColorFrame(applyBrushing(getColorFrame(null), odata));

            for(String measure : cvisitor.getMeasures()) {
               VisualFrame frame = cvisitor.getMeasureFrame(measure);
               frame = applyBrushing((ColorFrame) frame, odata);
               cvisitor.setMeasureFrame(measure, frame);
            }
         }

         acolor = null;
      }
      // brushing target?
      else {
         // create all color frame - gray
         StaticColorFrame frame2 = new StaticColorFrame();
         frame2.setUserColor(brushDimColor);
         acolor = applyBrushing(getColorFrame(null), frame2);

         frame2 = createBrushingTargetColorFrame(false);
         setColorFrame(applyBrushing(getColorFrame(null), frame2));

         for(String measure : cvisitor.getMeasures()) {
            VisualFrame frame = cvisitor.getMeasureFrame(measure);
            frame = applyBrushing((ColorFrame) frame, frame2);
            cvisitor.setMeasureFrame(measure, frame);
         }
      }
   }

   private StaticColorFrame createBrushingTargetColorFrame(boolean isAll) {
      StaticColorFrame frame2;
      // create brushing color frame - red
      frame2 = new StaticColorFrame();

      // if showing all data, dim all. (53441)
      if(data instanceof BrushDataSet && ((BrushDataSet) data).isBrushedDataEmpty() &&
         ((BrushDataSet) data).isBrushedDataOnly() || isAll)
      {
         frame2.setUserColor(brushDimColor);
      }
      else {
         frame2.setUserColor(brushHLColor);
      }
      return frame2;
   }

   /**
    * Constructor.
    * @param info the specified chart info.
    * @param desc the specified chart descriptor.
    * @param adata the data generated without brushing, null if no brushing.
    * @param data the data generated with brushing.
    * @param vars the specified variable table.
    * @param vsrc the source (worksheet/tbl, query) of the chart.
    */
   protected GraphGenerator(ChartInfo info, ChartDescriptor desc, DataSet adata, DataSet data,
                            VariableTable vars, String vsrc, int sourceType, Dimension size)
   {
      super();

      this.graphSize = size;
      adata = getFixedDataSet(info, adata, true);
      data = getFixedDataSet(info, data, false);
      this.origInfo = info;
      this.info = getFixedInfo(info, data);
      this.desc = desc;
      this.desc0 = desc;
      // check for discrete measures and if found, wrap dataset with an
      // aliased dataset
      adata = getDiscreteMeasureDataSet(adata, this.info);
      data = getDiscreteMeasureDataSet(data, this.info);
      this.adata = getDataSet(adata);
      this.odata = getDataSet(data);
      this.data = getDataSet(data);
      this.vars = vars;
      this.graph = new EGraph();

      xdims = new ArrayList<>();
      xmeasures = new ArrayList<>();
      ydims = new ArrayList<>();
      ymeasures = new ArrayList<>();
      xdimlabels = new HashMap<>();
      ydimlabels = new HashMap<>();
      xmeasurelabels = new HashMap<>();
      ymeasurelabels = new HashMap<>();
      scales = new Hashtable<>();
      rotatedCoords = new HashSet<>();
      // fdata is used to initialize the visual frame
      DataSet fdata = adata == null ? odata : adata;
      // create color frame
      boolean sep = !info.isMultiAesthetic();
      cstrategy = new VSColorFrameStrategy(info);
      cvisitor = new VSFrameVisitor(info, fdata, cstrategy, sep, data,
                                    null, vsrc, sourceType, getFrameInitializer());

      if(vars != null) {
         try {
            setQuerySandbox(vars.get("querySandbox"));
            vars.remove("querySandbox");
         }
         catch(Exception ex) {
         }
      }

      applyHighlight();

      // create shape frame
      shpstrategy = new VSShapeFrameStrategy(info);
      shpvisitor = new VSFrameVisitor(info, fdata, shpstrategy, sep, data,
                                      null, vsrc, sourceType, getFrameInitializer());
      // create texture frame
      tstrategy = new VSTextureFrameStrategy(info);
      tvisitor = new VSFrameVisitor(info, fdata, tstrategy, sep, data,
                                    null, vsrc, sourceType, getFrameInitializer());
      // create line frame
      lnstrategy = new VSLineFrameStrategy(info);
      lnvisitor = new VSFrameVisitor(info, fdata, lnstrategy, sep, data,
                                     null, vsrc, sourceType, getFrameInitializer());
      // create size frame
      szstrategy = new VSSizeFrameStrategy(info);
      szvisitor = new VSFrameVisitor(info, fdata, szstrategy, sep, null,
                                     null, vsrc, sourceType, getFrameInitializer());
      // create text frame
      txtstrategy = new VSTextFrameStrategy(info);
      txtvisitor = new VSFrameVisitor(info, fdata, txtstrategy, sep, null, null, vsrc, sourceType,
                                      getFrameInitializer());
   }

   /**
    * Get the axis descriptor of a scale.
    */
   protected AxisDescriptor getAxisDescriptor(Scale scale, String axisType) {
      String[] fields = scale.getFields();
      boolean mekko = info.getRTChartType() == CHART_MEKKO;

      for(int i = 0; i < fields.length; i++) {
         if((fields[i].startsWith("__") || getChartRef(fields[i], true, null) == null) && !mekko) {
            continue;
         }

         return getAxisDescriptor(ElementVO.getBaseName(fields[i]));
      }

      // throw new RuntimeException("Axis not found for scale: " + scale);
      // fake axis should not show label
      AxisDescriptor desc = new AxisDescriptor();
      desc.setLabelVisible(false);
      desc.getAxisLabelTextFormat().getCSSFormat().setCSSDictionary(null);
      desc.setLineColor(this.desc.getPlotDescriptor().getBorderColor());

      // copy the axis line setting from the inner dim of facet because the
      // axis of the fake axis overlaps the inner-most outer dim.
      // should not use y axis line color on x. (57725)
      if(!info.isInvertedGraph() && ydims.size() > 0 && "y".equals(axisType)) {
         AxisDescriptor desc2 = getAxisDescriptor(ydims.get(ydims.size() - 1));

         if(desc2 != null) {
            desc.setLineColor(desc2.getLineColor());
            desc.setLineVisible(desc2.isLineVisible());
         }
      }

      return desc;
   }

   /**
    * Get the axis descriptor of a chart ref.
    */
   protected AxisDescriptor getAxisDescriptor0(ChartRef ref) {
      AxisDescriptor rdesc = null;

      if(ref instanceof VSChartRef) {
         rdesc = ((VSChartRef) ref).getRTAxisDescriptor();
      }

      return rdesc == null ? ref.getAxisDescriptor() : rdesc;
   }

   /**
    * Get the axis descriptor of a chart info.
    */
   protected AxisDescriptor getAxisDescriptor0() {
      AxisDescriptor rdesc = null;

      if(info instanceof VSChartInfo) {
         rdesc = ((VSChartInfo) info).getRTAxisDescriptor();
      }

      return rdesc == null ? info.getAxisDescriptor() : rdesc;
   }

   /**
    * Check if is rotated.
    */
   protected boolean isRotated(Coordinate coord) {
      return rotatedCoords.contains(coord);
   }

   /**
    * Check if the chart is the brushing source.
    * @return true if the chart is the brushing source, false otherwise.
    */
   public boolean isBrushingSource() {
      return bconds != null && !bconds.isEmpty();
   }

   /**
    * Check if the chart is the brushing target.
    * @return true if the chart is the brushing target, false otherwise.
    */
   public boolean isBrusingTarget() {
      return adata != null;
   }

   /**
    * Check if the chart is zooming.
    * @return true if the chart is zooming, false otherwise.
    */
   public boolean isZooming() {
      return zconds != null && !zconds.isEmpty();
   }

   /**
    * Apply highlight.
    */
   protected void applyHighlight() {
      // don't apply highlight defined on node on edge. (56974)
      if(GraphTypes.isRelation(info.getChartType())) {
         return;
      }

      if(adata == null) {
         if(!isBrushingSource()) {
            setColorFrame(applyHighlight(getColorFrame(null), odata));

            for(String measure : cvisitor.getMeasures()) {
               VisualFrame frame = cvisitor.getMeasureFrame(measure);
               frame = applyHighlight((ColorFrame) frame, odata);
               cvisitor.setMeasureFrame(measure, frame);
            }
         }

         acolor = null;
      }
   }

   /**
    * Apply brushing.
    */
   protected ColorFrame applyBrushing(ColorFrame frame, ColorFrame frame2) {
      if(frame == null) {
         return frame2;
      }

      CompositeColorFrame cframe = new CompositeColorFrame();
      cframe.addFrame(frame2);
      cframe.addFrame(frame);

      return cframe;
   }

   /**
    * Apply brushing.
    */
   protected ColorFrame applyBrushing(ColorFrame color, DataSet data) {
      HLColorFrame hframe = getHLColorFrame(brushHLColor);
      hframe.setDefaultColor(brushDimColor);

      if(color == null) {
         return hframe;
      }

      CompositeColorFrame cframe = new CompositeColorFrame();
      cframe.addFrame(hframe);
      cframe.addFrame(color);
      return cframe;
   }

   /**
    * Get highlight color frame.
    */
   protected HLColorFrame getHLColorFrame(Color color) {
      HighlightGroup group = new HighlightGroup();
      TextHighlight highlight = new TextHighlight();
      ConditionList bconds = this.bconds.clone();
      String col = null;

      if(GraphTypes.isBoxplot(info.getChartType())) {
         for(int i = 0; i < bconds.getConditionSize(); i += 2) {
            ConditionItem item = bconds.getConditionItem(i);

            if(item.getXCondition().getOperation() == XCondition.BETWEEN) {
               DataRef ref = item.getAttribute();
               String attr = ref.getAttribute().trim();

               // between condition is for outlier point
               if(attr.startsWith(BoxDataSet.OUTLIER_PREFIX) &&
                  attr.length() > BoxDataSet.OUTLIER_PREFIX.length())
               {
                  item.setAttribute(new ColumnRef(new AttributeRef(
                     attr.substring(BoxDataSet.OUTLIER_PREFIX.length()))));
               }
               // between condition is for boxplot ranges (min-q25-medium-q45-max)
               else {
                  item.setAttribute(new ColumnRef(new AttributeRef(BoxDataSet.MAX_PREFIX + attr)));
               }
            }
         }
      }
      // tree only highlight the node at the level where brushing condition is applied (46589).
      else if(GraphTypes.isRelation(info.getChartType())) {
         RelationChartInfo info2 = (RelationChartInfo) info;
         String src = info2.getRTSourceField().getFullName();
         String target = info2.getRTTargetField().getFullName();
         Set<String> cols = new HashSet<>();

         for(int i = 0; i < bconds.getConditionSize(); i += 2) {
            ConditionItem item = bconds.getConditionItem(i);
            DataRef ref = item.getAttribute();
            String col2 = null;

            if(ref instanceof ColumnRef) {
               col2 = ((ColumnRef) ref).getCaption();
            }

            col2 = col2 == null ? ref.getName() : col2;

            if(Tool.equals(col2, src) || Tool.equals(col2, target)) {
               cols.add(col2);
            }
         }

         // only apply highlight on selected level if only one of source/target is selected.
         if(cols.size() == 1) {
            col = cols.iterator().next();
         }
      }

      highlight.setForeground(color);
      highlight.setConditionGroup(bconds);
      group.addHighlight("_brush_", highlight);
      highlight.setName("_brush_");
      HLColorFrame hframe = new HLColorFrame(col, group, data, true);
      hframe.setQuerySandbox(querySandbox);

      return hframe;
   }

   /**
    * Getter of asset query sandbox.
    */
   public Object getQuerySandbox() {
      return this.querySandbox;
   }

   /**
    * Setter of asset query sandbox.
    */
   public void setQuerySandbox(Object querySandbox) {
      this.querySandbox = querySandbox;
   }

   /**
    * Replace highlight variables.
    */
   protected void replaceHLVariables(HighlightGroup group, VariableTable vars) {
      group.replaceVariables(vars);

      String[] levels = group.getLevels();

      for(int i = 0; i < levels.length; i++) {
         String[] names = group.getNames(levels[i]);

         for(int j = 0; j < names.length; j++) {
            Highlight highlight = group.getHighlight(levels[i], names[j]);
            highlight.replaceVariables(vars);
         }
      }
   }

   /**
    * Apply highlight.
    */
   protected ColorFrame applyHighlight(ColorFrame color, DataSet data) {
      HighlightRef[] arr = getHighlightRefs();
      List<ColorFrame> list = new ArrayList<>();
      boolean wordCloud = GraphTypeUtil.isWordCloud(info, true);

      for(int i = 0; i < arr.length; i++) {
         applyHighlight(data, arr[i], list);
      }

      if(info instanceof MergedChartInfo || list.isEmpty() && arr.length == 0) {
         HighlightGroup group = info.getHighlightGroup();
         String name = wordCloud ? "value" : null;
         createHLColorFrame(data, list, name, group);
      }

      // contour doesn't support highlight
      if(list.size() > 0 && !GraphTypes.isContour(info.getChartType())) {
         CompositeColorFrame cframe = new CompositeColorFrame();

         // highlight first
         for(int i = 0; i < list.size(); i++) {
            cframe.addFrame(list.get(i));
         }

         if(color != null) {
            cframe.addFrame(color);
         }

         if(cframe.getFrameCount() == 1) {
            color = (ColorFrame) cframe.getFrame(0);
         }
         else {
            color = cframe;
         }
      }

      return color;
   }

   // apply highlight defined in a measure.
   private void applyHighlight(DataSet data, HighlightRef ref, List<ColorFrame> list) {
      HighlightGroup group = replaceHLVariables(ref.getHighlightGroup());
      String name = GraphTypeUtil.isWordCloud(info) ? "value" : ((ChartRef) ref).getFullName();
      createHLColorFrame(data, list, name, group);
   }

   // create a highlight colorframe and add to list.
   private void createHLColorFrame(DataSet data, List<ColorFrame> list, String name,
                                   HighlightGroup group)
   {
      HLColorFrame frame = new HLColorFrame(name, group, data);
      frame.setQuerySandbox(querySandbox);

      if(!frame.isEmpty()) {
         list.add(frame);
      }
   }

   private HighlightGroup replaceHLVariables(HighlightGroup group) {
      if(group != null) {
         group = group.clone();
         replaceHLVariables(group, vars);
      }

      return group;
   }

   private HighlightGroup getTextHighlightGroup(DataRef ref, ChartBindable info) {
      // tree text field is apply on target and not source.
      boolean ignoreTextField = info instanceof RelationChartInfo &&
         Objects.equals(ref, ((RelationChartInfo) info).getSourceField());
      AestheticRef textField = info != null && !ignoreTextField ? info.getTextField() : null;
      HighlightGroup highlight = null;

      if(textField != null && textField.getDataRef() instanceof HighlightRef) {
         highlight = GraphUtil.getTextHighlightGroup(
            (HighlightRef) textField.getDataRef(),
            info instanceof ChartInfo ? (ChartInfo) info : null);
      }
      else if(ref instanceof HighlightRef) {
         highlight = ((HighlightRef) ref).getTextHighlightGroup();
      }
      else if(info instanceof ChartInfo) {
         highlight = ((ChartInfo) info).getTextHighlightGroup();
      }

      if(highlight != null) {
         highlight.setQuerySandbox(getQuerySandbox());
      }

      return replaceHLVariables(highlight);
   }

   /**
    * Create element graph.
    */
   public EGraph createEGraph() {
      fixComparer();
      fixDefaultFormats();
      createEGraph0();
      fixGraphProperties();

      if(dateComparison != null) {
         GraphDefault.setDefaultOutlines(graph);
         DateComparisonUtil.applyDateRange(dateComparison, graph, (VSChartInfo) info, data);
      }

      return graph;
   }

   protected PolarCoord createPolarCoord(int type, Scale xscale) {
      PolarCoord coord;

      if(type == CHART_3D_PIE) {
         Rect25Coord inner = new Rect25Coord(null, xscale);
         coord = new PolarCoord(inner);
      }
      else {
         coord = new PolarCoord(xscale);
      }

      if(GraphTypeUtil.isPolar(info, true)) {
         coord.setPieRatio(desc.getPlotDescriptor().getPieRatio());
      }

      return coord;
   }

   // get the fake measure value if there is no measure.
   protected Integer getFakeVal(boolean xdcontained, boolean ydcontained) {
      boolean pie = GraphTypes.isPie(info.getRTChartType());
      boolean wordcloud = info.getTextField() != null;

      if(GraphTypeUtil.isDotPlot(info)) {
         return 0;
      }

      return (xdcontained || ydcontained || pie || wordcloud ||
         // using null when there is no dim binding would
         // cause the data points to be ignored
         !xdcontained && !ydcontained) ? 1 : null;
   }

   /**
    * Fix the egraph of the specified coord, set the attribute from desc and
    * info.
    */
   protected void fixGraphProperties() {
      LegendsDescriptor legends = desc.getLegendsDescriptor();
      TitlesDescriptor titlesDesc = desc.getTitlesDescriptor();
      PlotDescriptor plotdesc = desc.getPlotDescriptor();

      if(titlesDesc != null) {
         TitleDescriptor xtitle = titlesDesc.getXTitleDescriptor();
         TitleDescriptor ytitle = titlesDesc.getYTitleDescriptor();
         TitleDescriptor x2title = titlesDesc.getX2TitleDescriptor();
         TitleDescriptor y2title = titlesDesc.getY2TitleDescriptor();

         graph.setXTitleSpec(getTitleSpec(xtitle, "x"));
         graph.setX2TitleSpec(getTitleSpec(x2title, "x2"));
         graph.setYTitleSpec(getTitleSpec(ytitle, "y"));
         graph.setY2TitleSpec(getTitleSpec(y2title, "y2"));
      }

      // setup visual frames, need to do before element options since GraphDefault.isInPlot
      // checks existance of TextFrame
      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);
         String var = elem.getVarCount() == 0 ? null : elem.getVar(0);
         boolean interval = var != null && var.contains(IntervalDataSet.TOP_PREFIX);
         var = GraphUtil.getOriginalCol(var);

         ChartRef aggr = getChartRef(var);
         ColorFrame color = elem.getColorFrame();
         SizeFrame size = elem.getSizeFrame();
         VisualFrame shpframe = null;

         if(elem.getTextureFrame() != null) {
            shpframe = elem.getTextureFrame();
         }
         else if(elem.getLineFrame() != null) {
            shpframe = elem.getLineFrame();
         }
         else if(elem.getShapeFrame() != null) {
            shpframe = elem.getShapeFrame();
         }

         for(VisualFrame frame : LegendGroup.getLegendFrames(color)) {
            processColorLegend(legends, elem, frame);
         }

         for(VisualFrame frame : LegendGroup.getLegendFrames(shpframe)) {
            processShapeLegend(legends, var, frame);
         }

         for(VisualFrame frame : LegendGroup.getLegendFrames(size)) {
            processSizeLegend(legends, var, frame, interval);
         }

         boolean forceText = info.getChartType() == CHART_TREE &&
            elem.getTextFrame() == null;
         boolean valuesVisible = plotdesc.isValuesVisible() || forceText;

         // don't show duplicate values.
         if(valuesVisible && info instanceof RelationChartInfo && info.getTextField() != null &&
            ((RelationChartInfo) info).getRTTargetField() != null &&
            info.getRTTextField() instanceof VSDataRef &&
            Tool.equals(((RelationChartInfo) info).getRTTargetField().getFullName(),
               ((VSDataRef) info.getRTTextField()).getFullName()))
         {
            valuesVisible = false;
         }

         // text should be shown for relation chart even with brushing. (56992)
         boolean textOnBrush = valuesVisible && GraphTypes.isRelation(info.getChartType());

         if(valuesVisible && supportShowValue() &&
            !(elem instanceof TreemapElement) &&
            (elem.getHint("overlaid") == null || textOnBrush) &&
            elem.getHint("_sparkline_point_") == null &&
            !"true".equals(elem.getHint("_pareto_")) &&
            elem.getHint("_show_point_") == null)
         {
            TextFrame text2;

            if(plotdesc.isStackValue() && elem instanceof StackableElement &&
               elem.isStack() && ((StackableElement) elem).isStackGroup())
            {
               String[] measures = GraphTypeUtil.isStackMeasures(info, desc) ? elem.getVars()
                  : new String[] { elem.getVar(0) };
               text2 = new StackTextFrame(elem, measures);
            }
            else if(elem instanceof SchemaElement &&
               ((SchemaElement) elem).getPainter() instanceof BoxPainter)
            {
               CompositeTextFormat valueFormat =
                  GraphFormatUtil.getTextFormat(info, aggr, plotdesc, false);
               Format format = GraphUtil.getFormat(valueFormat);
               Format defaultFormat = getDefaultFormat(elem);
               boolean userDefined = valueFormat == null ?
                  false : !valueFormat.getUserDefinedFormat().getFormat().isEmpty();
               text2 = new MultiTextFrame(elem.getVars());
               ((MultiTextFrame) text2).setValueFormat(userDefined ? format : defaultFormat);
            }
            else {
               text2 = new DefaultTextFrame();
            }

            TextFrame text1 = elem.getTextFrame();

            if(text1 != null) {
               TextFrame text0;
               CompositeTextFormat textFmt = info.isMultiStyles()
                  ? GraphFormatUtil.getTextFormat((ChartBindable) aggr, aggr, plotdesc)
                  : GraphFormatUtil.getTextFormat(info, aggr, plotdesc);
               Format textDefFmt = getDefaultFormat(text1);
               Format textFormat = getTextFormat(getTextFiled(aggr));

               // if there is a default format from the data, use it instead of the one
               // in aggr, which may just be the AUTO_FORMAT. (61631)
               if(textDefFmt != null) {
                  textFmt = textFmt.clone();
                  textFmt.getDefaultFormat().setFormat(null);
               }

               Format text1Format = GraphUtil.getFormat(textFmt);

               if(text1Format == null) {
                  text1Format = textDefFmt;
               }

               if(aggr == null) {
                  text0 = new ValueTextFrame(text1, text2, text1Format,
                                         GraphUtil.getFormat(plotdesc.getTextFormat()));
               }
               else {
                  CompositeTextFormat valueFormat = aggr.getTextFormat();

                  if(GraphUtil.getFormat(valueFormat) == null) {
                     AxisDescriptor axisDesc = getAxisDescriptor0(aggr);
                     valueFormat = getAxisLabelFormat(axisDesc, new String[]{ aggr.getFullName() },
                                                      false);
                  }

                  if(text1 instanceof MultiTextFrame) {
                     ((MultiTextFrame) text1).setValueFormat(text1Format);
                  }

                  text0 = new ValueTextFrame(text1, text2, text1Format,
                                         GraphUtil.getFormat(valueFormat));
               }

               text0.setField(text1.getField());
               elem.setTextFrame(text0);
               // text already formatted in TextFrame2
               elem.getTextSpec().setFormat(null);
            }
            else {
               elem.setTextFrame(text2);
            }
         }

         fixTextFrame(plotdesc, elem, aggr);
      }

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);
         // if stack-measure, the last measure is used for setting formats (48341).
         String var = elem.getVarCount() == 0 ? null : elem.getVar(elem.getVarCount() - 1);

         if(elem instanceof RelationElement) {
            var = ((RelationElement) elem).getSourceDim();
         }

         ChartRef cref = info.getFieldByName(GraphUtil.getOriginalCol(var), true);
         CompositeTextFormat format = info.isMultiAesthetic() && cref instanceof ChartBindable
            ? GraphFormatUtil.getTextFormat((ChartBindable) cref, cref, plotdesc)
            : GraphFormatUtil.getTextFormat(info, cref, plotdesc);
         Format fmt = getDefaultFormat(elem);

         if(elem.getTextFrame() != null) {
            TextFrame text = elem.getTextFrame();
            Format fmt2 = getDefaultFormat(text);

            if(fmt2 != null) {
               fmt = fmt2;
            }
         }

         if(elem instanceof TreemapElement) {
            TreemapElement.Type mapType = ((TreemapElement) elem).getMapType();

            for(String tdim : ((TreemapElement) elem).getTreeDims()) {
               ChartRef ref = info.getFieldByName(tdim, false);
               CompositeTextFormat fmt2 = GraphFormatUtil.getTextFormat(info, ref, plotdesc);
               HighlightGroup highlight = getTextHighlightGroup(ref, info);
               TextSpec textSpec = GraphUtil.getTextSpec(fmt2, fmt, highlight);
               Format textFmt = textSpec.getFormat();
               elem.setTextSpec(tdim, textSpec);

               if(elem.getTextFrame() instanceof CurrentTextFrame) {
                  CurrentTextFrame textFrame = (CurrentTextFrame) elem.getTextFrame();
                  textFrame.setIncludeParents(plotdesc.isIncludeParentLabels() &&
                     // parent labels are displayed in its own area and shouldn't be on child
                     mapType != TreemapElement.Type.SUNBURST &&
                     mapType != TreemapElement.Type.ICICLE);

                  if(textFmt != null) {
                     textFrame.setFormat(tdim, textFmt);
                  }
               }

               if(!GDefaults.DEFAULT_TEXT_COLOR.equals(textSpec.getColor()) || highlight != null) {
                  elem.setAutoTextColor(false);
               }
            }
         }
         else if(elem instanceof RelationElement) {
            initRelationElement(plotdesc, (RelationElement) elem);
         }
         else if(GraphTypes.isRadar(info.getChartType())) {
            for(int k = 0; k < elem.getDimCount(); k++) {
               String dim = elem.getDim(k);
               ChartRef ref = info.getFieldByName(dim, false);
               HighlightGroup highlight = getTextHighlightGroup(ref, info);
               CompositeTextFormat fmt2 = GraphFormatUtil.getTextFormat(info, ref, plotdesc);
               elem.setTextSpec(dim, GraphUtil.getTextSpec(fmt2, fmt, highlight));
            }
         }

         HighlightGroup highlight = null;

         if(GraphTypeUtil.isWordCloud(info) || elem instanceof TreemapElement ||
            elem instanceof RelationElement)
         {
            HighlightRef[] hrefs = getHighlightRefs();

            for(HighlightRef href : hrefs) {
               highlight = getTextHighlightGroup(href, info);

               if(highlight != null) {
                  String fullname = ((VSDataRef) href).getFullName();
                  TextSpec ospec = elem.getTextSpec(fullname);
                  CompositeTextFormat format2 =
                     GraphFormatUtil.getTextFormat(info, (ChartRef) href, plotdesc);
                  Format fmt2 = ospec != null ? ospec.getFormat() : fmt;
                  elem.setTextSpec(fullname, GraphUtil.getTextSpec(format2, fmt2, highlight));

                  // mname for wordcloud is 'value'
                  if(GraphTypeUtil.isWordCloud(info)) {
                     elem.setTextSpec("value", elem.getTextSpec(fullname));
                  }
               }
            }
         }

         AestheticRef textField = getTextFiled(cref);

         if(textField != null) {
            // if bind text field, don't use the default format for aggregate, should
            // use the default format for the text field. (49405)
            fmt = getDefaultFormat(textField.getFullName());

            if(highlight == null && textField.getDataRef() instanceof HighlightRef) {
               highlight = ((HighlightRef) textField.getDataRef()).getTextHighlightGroup();
            }
         }

         elem.setHint(GraphElement.HINT_EXPLODED, plotdesc.isExploded() + "");
         elem.setTextSpec(GraphUtil.getTextSpec(format, fmt, highlight));

         // if element wide format exists, let it be inherited by dimension. (53466, 54022)
         if(elem.getTextSpec().getFormat() != null && !(elem instanceof RelationElement)) {
            for(String dim : elem.getTextSpecDims()) {
               TextSpec spec = elem.getTextSpec(dim);

               if(spec.getFormat() == null) {
                  spec.setFormat(elem.getTextSpec().getFormat());
               }
            }
         }

         // already formatted in TextFrame2
         if(elem.getTextFrame() instanceof ValueTextFrame) {
            elem.getTextSpec().setFormat(null);
         }

         if(format.getUserDefinedFormat().isColorDefined()) {
            elem.setAutoTextColor(false);
         }

         applyAlpha(plotdesc, elem);

         // don't rescale sparkline so sparklines and text totals can
         // lineup better regardless of the sizes.
         // gantt chart in-plot option is disabled and should be false.
         elem.setInPlot(GraphDefault.isInPlot(plotdesc, elem, graph) &&
                           // dotplot stacks points on a y axis with a fake (1) value. (60197)
                           (!isSparkline() || fake) &&
                           !GraphTypes.isGantt(info.getChartType()));

         // don't support shine for sparkline bar, it's too small
         if(desc.isApplyEffect() && !isSparkline() && elem.getHint("_show_point_") == null) {
            elem.setHint(GraphElement.HINT_SHINE, "true");
         }

         if(isSparkline()) {
            elem.setHint(GraphElement.HINT_MAX_WIDTH, 60);
            elem.setHint(GraphElement.HINT_MAX_HEIGHT, 20);
         }
      }

      if(legends != null) {
         Dimension2D psize = legends.getPreferredSize();
         legends.setPreferredSize(psize);
         GraphUtil.setLegendOptions(graph, legends);
      }

      // for separator chart, we must use measure to get chart type
      List<String> measures = ymeasures.size() > 0 ?  ymeasures : xmeasures;
      boolean isWaterfall = false;

      if(measures.size() > 0) {
         String measure = measures.get(0);
         isWaterfall = GraphTypes.isWaterfall(GraphTypeUtil.getChartType(measure, info));

         if(!GraphTypes.isRadar(GraphTypeUtil.getChartType(measure, info)) &&
            !GraphTypes.isPie(GraphTypeUtil.getChartType(measure, info)) &&
            !GraphTypes.isFunnel(GraphTypeUtil.getChartType(measure, info)) &&
            !GraphTypes.isContour(GraphTypeUtil.getChartType(measure, info)) &&
            desc.getTargetCount() > 0)
         {
            // Add graph targets
            for(int i = 0; i < desc.getTargetCount(); i++) {
               GraphTarget target = desc.getTarget(i);
               /**
                * In case of multiple chart types, need to get individual
                * chart type for the field of each target.  Otherwise the
                * incorrect chart type could get to the form and the wrong
                * data might be extracted from the dataset.
                */
               int chartType = GraphTypeUtil.getChartType(target.getField(), info);
               addTarget(target, chartType, i, plotdesc);
            }
         }
      }

      if(!isWaterfall && !GraphTypeUtil.isMap(info) && !GraphTypes.isBoxplot(info.getChartType()) &&
         !GraphTypes.isFunnel(info.getChartType()) && !fake)
      {
         // the inPlot property should set for trend line
         addTrendLine(plotdesc);
      }

      if(!GraphTypeUtil.isPolar(info, false) && !GraphTypeUtil.isMap(info)) {
         addGridLines(plotdesc);
      }

      addDensityContour();
      addPlotBanding(plotdesc);
      addPlotBackgroundColor(plotdesc);
   }

   protected void fixTextFrame(PlotDescriptor plotdesc, GraphElement elem, ChartRef aggr) {
      if(elem.getTextFrame() != null) {
         TextFrame text = elem.getTextFrame();

         if(!(text instanceof ValueTextFrame) || hasStackedMeasures(elem)) {
            Format tfmt = getDefaultFormat(text);
            ChartBindable bindable = info.isMultiAesthetic() && aggr instanceof ChartAggregateRef
               ? (ChartBindable) aggr : info;
            CompositeTextFormat dformat = GraphFormatUtil.getTextFormat(bindable, aggr, plotdesc);
            fixTextFieldDefaultFormat(bindable, tfmt, dformat);
            ChartRef highlightAggr = aggr;

            // stack measure highlight defined on the top (last) measure. (61282)
            if(hasStackedMeasures(elem)) {
               highlightAggr = info.getFieldByName(elem.getVar(elem.getVarCount() - 1), true);

               if(bindable == aggr && highlightAggr instanceof ChartBindable) {
                  bindable = (ChartBindable) highlightAggr;
               }
            }

            HighlightGroup highlight = getTextHighlightGroup(highlightAggr, bindable);
            text.getLegendSpec().setTextSpec(GraphUtil.getTextSpec(dformat, tfmt, highlight));

            if(text instanceof MultiTextFrame) {
               MultiTextFrame multiTextFrame = (MultiTextFrame) text;
               Format format = GraphUtil.getFormat(dformat);

               if(format == null) {
                  format = tfmt;
               }

               multiTextFrame.setValueFormat(format);
            }

            // different measure could be set different format. (53292)
            for(int k = 0; k < elem.getVarCount(); k++) {
               String var2 = elem.getVar(k);
               // total label uses the same format as regular value.
               String var3 = GraphTypes.isWaterfall(info.getRTChartType())
                  ? Tool.replace(var2, SumDataSet.SUM_HEADER_PREFIX, "") :
                  GraphTypes.isBoxplot(info.getRTChartType()) ? BoxDataSet.getBaseName(var2) : var2;

               ChartRef aggr2 = getChartRef(var3);
               ChartBindable bindable2 = info.isMultiAesthetic() && aggr2 instanceof ChartAggregateRef
                  ? (ChartBindable) aggr2 : info;
               CompositeTextFormat format = info.isMultiAesthetic()
                  ? GraphFormatUtil.getTextFormat((ChartBindable) aggr2, aggr2, plotdesc)
                  : GraphFormatUtil.getTextFormat(info, aggr2, plotdesc);
               var3 = BoxDataSet.getBaseName(var3);
               ChartRef highlightRef = info.getFieldByName(GraphUtil.getOriginalCol(var3), true);
               highlight = getTextHighlightGroup(highlightRef, bindable2);
               elem.setTextSpec(var2, GraphUtil.getTextSpec(format, tfmt, highlight));
            }
         }
      }
   }

   private void initRelationElement(PlotDescriptor plotdesc, RelationElement elem) {
      // set source format
      String sdim = elem.getSourceDim();
      Format fmt = getDefaultFormat(sdim);
      ChartRef sourceRef = info.getFieldByName(sdim, false);
      CompositeTextFormat fmt2 = GraphFormatUtil.getTextFormat(info, sourceRef, plotdesc, false);
      // relation highlights defined on source/target instead of textfield.
      HighlightGroup highlight = getTextHighlightGroup(sourceRef, null);
      TextSpec textSpec = GraphUtil.getTextSpec(fmt2, fmt, highlight);
      setNodeTextBackground(plotdesc, textSpec, elem);
      elem.setTextSpec(sdim, textSpec);
      boolean applyAestheticsToSource = plotdesc.isApplyAestheticsToSource() &&
         (info.getColorField() == null || info.getColorField().getRTDataRef() == null);
      elem.setApplyAestheticsToSource(applyAestheticsToSource);

      // set target format
      String tdim = elem.getTargetDim();
      ChartRef targetRef = info.getFieldByName(tdim, false);
      Format targetDefaultFmt = getDefaultFormat(tdim);
      fmt2 = GraphFormatUtil.getTextFormat(info, targetRef, plotdesc);
      Format fmt3 = null;

      // don't use dim default format if text field is bound. (51618)
      if(info.getRTTextField() == null) {
         fmt3 = targetDefaultFmt;
      }
      // if fmt is set for target (from text field), use it instead of null. (56953)
      else if(elem.getTextFrame() != null) {
         fmt3 = elem.getTextFrame().getLegendSpec().getTextSpec().getFormat();
      }

      HighlightGroup highlight2 = getTextHighlightGroup(targetRef, info);
      textSpec = GraphUtil.getTextSpec(fmt2, fmt3, highlight2);
      setNodeTextBackground(plotdesc, textSpec, elem);
      elem.setTextSpec(tdim, textSpec);

      // make sure defualt format for target is applied. (56986)
      if(elem.getTextFrame() instanceof ValueTextFrame) {
         Format targetFormat = GraphUtil.getFormat(targetRef.getTextFormat());

         if(targetFormat == null) {
            targetFormat = targetDefaultFmt;
         }

         // if text binding exist, show value use text binding format.
         if(info.getRTTextField() == null && targetDefaultFmt != null) {
            ((ValueTextFrame) elem.getTextFrame()).setValueFormat(targetDefaultFmt);
         }
         // otherwise use target dim format. (61268, 61294)
         else if(targetFormat != null) {
            ((ValueTextFrame) elem.getTextFrame()).setValueFormat(targetFormat);
         }
      }

      RelationChartInfo rinfo = (RelationChartInfo) info;
      ColorFrame colorFrame = null;

      if(rinfo.getNodeColorField() != null) {
         AestheticRef tref = rinfo.getNodeColorField();
         DataRef innerRef = tref.getDataRef();
         String columnName = rinfo.getNodeColorField().getFullName();

         if(vs != null) {
            final CategoricalColorFrameContext context = CategoricalColorFrameContext.getContext();
            context.setDimensionColors(vs.getDimensionColors(columnName));
         }

         colorFrame = (ColorFrame) rinfo.getNodeColorField().getVisualFrame().clone();
         colorFrame.setField(rinfo.getNodeColorField().getFullName());
         colorFrame.getLegendSpec().setTitle(GraphUtil.getCaption(rinfo.getRTNodeColorField()));

         VSFrameVisitor.setLegendComparator(tref, colorFrame, data);

         if(XSchema.isDateType(tref.getDataType())) {
            int dlevel = ((XDimensionRef) tref.getDataRef()).getDateLevel();
            Format dfmt = XUtil.getDefaultDateFormat(dlevel, tref.getDataType());
            TextSpec spec = colorFrame.getLegendSpec().getTextSpec();

            if(spec == null) {
               colorFrame.getLegendSpec().setTextSpec(spec);
            }

            colorFrame.getLegendSpec().getTextSpec().setFormat(dfmt);
         }

         if(colorFrame instanceof CategoricalColorFrame &&
            ((CategoricalColorFrame) colorFrame).isShareColors() && info instanceof VSChartInfo)
         {
            final CategoricalColorFrameContext context = CategoricalColorFrameContext.getContext();
            final VisualFrame sharedFrame = context.getSharedFrame(columnName, innerRef);

            if(sharedFrame != null) {
               colorFrame = (ColorFrame) sharedFrame.clone();
               colorFrame.setField(columnName);
               tref.setVisualFrame(colorFrame);
            }

            VSFrameVisitor.syncCategoricalFrame(colorFrame, data, innerRef, null, false);
            final VisualFrame saveFrame = (VisualFrame) colorFrame.clone();
            context.addSharedFrame(columnName, saveFrame, innerRef);
         }
         else {
            VSFrameVisitor.syncCategoricalFrame(colorFrame, data, innerRef, null, false);
         }
      }
      else {
         colorFrame = (ColorFrame) rinfo.getNodeColorFrameWrapper().getVisualFrame();
         colorFrame = colorFrame != null ? (ColorFrame) colorFrame.clone() : null;

         /* using color to fill node instead.
         // use white fill color for tree.
         if(info.getChartType() == CHART_TREE && elem.isLabelInside()) {
            elem.setFillColor(Color.WHITE);
         }
         */
      }

      colorFrame = applyHighlight(colorFrame, data);

      if(isBrushingSource()) {
         colorFrame = applyBrushing(colorFrame, data);
      }
      else if(isBrusingTarget()) {
         colorFrame = applyBrushing(
            colorFrame, createBrushingTargetColorFrame(elem.getHint("overlaid") != null));
      }

      elem.setNodeColorFrame(colorFrame);

      for(VisualFrame frame : LegendGroup.getLegendFrames(colorFrame)) {
         processColorLegend(desc.getLegendsDescriptor(), elem, frame);
      }

      if(rinfo.getNodeSizeField() != null) {
         AestheticRef tref = rinfo.getNodeSizeField();
         SizeFrame frame = (SizeFrame) tref.getVisualFrame().clone();

         VSFrameVisitor.setLegendComparator(tref, frame, data);
         elem.setNodeSizeFrame(frame);
         frame.setField(rinfo.getNodeSizeField().getFullName());
         frame.setScale(null);
         frame.getLegendSpec().setTitle(GraphUtil.getCaption(rinfo.getRTNodeSizeField()));

         // use all column for size. (57517, 57571)
         if(data instanceof BrushDataSet && !((BrushDataSet) data).isBrushedDataOnly() &&
            // discrete column doesn't have the ALL prefix. (58203)
            !frame.getField().startsWith(ChartAggregateRef.DISCRETE_PREFIX))
         {
            frame.setField(ElementVO.ALL_PREFIX + frame.getField());
         }
      }
      else {
         SizeFrame frame = (SizeFrame) rinfo.getNodeSizeFrameWrapper().getVisualFrame();
         frame = frame != null ? (SizeFrame) frame.clone() : null;
         elem.setNodeSizeFrame(frame);
      }

      String var = elem.getVarCount() == 0 ? null : elem.getVar(0);

      for(VisualFrame frame : LegendGroup.getLegendFrames(elem.getNodeSizeFrame())) {
         processSizeLegend(desc.getLegendsDescriptor(), var, frame, false);
      }

      // we don't track whether the color has changed so just check if it's default.
      // will need to add a flag and a ui button to reset otherwise.
      if(!GDefaults.DEFAULT_TEXT_COLOR.equals(textSpec.getColor()) ||
         highlight != null || highlight2 != null)
      {
         elem.setAutoTextColor(false);
      }

      if(graphSize != null) {
         elem.setLayoutSize(graphSize);
      }
   }

   private AestheticRef getTextFiled(ChartRef aggr) {
      AestheticRef textField = info.getTextField();

      if(info.isMultiAesthetic() && aggr instanceof ChartBindable) {
         textField = ((ChartBindable) aggr).getTextField();
      }

      return textField;
   }

   private Format getTextFormat(AestheticRef textField) {
      if(textField == null) {
         return null;
      }

      DataRef dataRef = textField.getDataRef();
      Format format = null;

      if(dataRef instanceof ChartRef) {
         CompositeTextFormat compositeTextFormat = ((ChartRef) dataRef).getTextFormat();
         format = GraphUtil.getFormat(compositeTextFormat);
      }

      return format;
   }

   private void setNodeTextBackground(PlotDescriptor plotdesc, TextSpec textSpec,
                                      RelationElement elem)
   {
      // set background so line doesn't cut across text label.
      if(textSpec.getBackground() == null && !elem.isLabelInside()) {
         textSpec.setBackground(GTool.applyAlpha(plotdesc.getBackground(), 0.8));
      }
   }

   protected void applyAlpha(PlotDescriptor plotdesc, GraphElement elem) {
      if(plotdesc.getAlpha() >= 0) {
         elem.setHint(GraphElement.HINT_ALPHA, plotdesc.getAlpha());
      }
      else if(elem instanceof AreaElement) {
         // if areas are stacked, shoulding see through the area to the area behind since the
         // value behind the front area is not 'real'.
         if((elem.getCollisionModifier() & GraphElement.MOVE_STACK) != 0) {
            elem.setHint(GraphElement.HINT_ALPHA, null);
         }
      }
   }

   private void addDensityContour() {
      if(GraphTypes.isContour(info.getChartType())) {
         DensityForm form = new DensityForm();
         GraphElement elem = graph.getElement(graph.getElementCount() - 1);
         SizeFrame sizes = elem.getSizeFrame();
         ColorFrame colors = elem.getColorFrame();
         boolean brushing = adata == null && bconds != null && !bconds.isEmpty();

         graph.addForm(form);

         // contour brushing using red, same as other visual objects
         if(adata != null) {
            colors = new RedsColorFrame();
         }
         // brushed chart (not the other charts showing brushing)
         else if(brushing) {
            colors = new StaticColorFrame(brushHLColor);

            // only use brushed points for calculating contour for brushing
            form.setPointSelector((vo, graph) -> {
               ElementGeometry gobj = (ElementGeometry) vo.getGeometry();
               ColorFrame cframe = gobj.getElement().getColorFrame();
               DataSet data = gobj.getVisualModel().getDataSet();
               int tidx = gobj.getTupleIndex();

               if(cframe instanceof CompositeColorFrame) {
                  return ((CompositeColorFrame) cframe).getFrames(HLColorFrame.class).anyMatch(frame ->
                     ((HLColorFrame) frame).getHighlight(data, tidx) != null
                  );
               }

               return true;
            });
         }

         // needs the HLColorFrame in the PointSelector above.
         if(!brushing) {
            // prevent label color to be set to element color in PointVO
            for(int i = 0; i < graph.getElementCount(); i++) {
               if(graph.getElement(i) instanceof PointElement) {
                  graph.getElement(i).setColorFrame(new StaticColorFrame());
               }
            }
         }

         form.setColorFrame(colors);

         PlotDescriptor plot = desc.getPlotDescriptor();

         if(colors instanceof LinearColorFrame) {
            LinearColorFrame colors2 = (LinearColorFrame) colors;
            colors2.setMaxAlpha(Math.abs(plot.getAlpha()));
            colors2.setMinAlpha(Math.min(plot.getContourEdgeAlpha(), 1));

         }

         form.setCellSize(getContourCellSize(plot));
         form.setLevels(plot.getContourLevels());
         form.setBandwidth(plot.getContourBandwidth());
      }
   }

   private int getContourCellSize(PlotDescriptor plot) {
      int size = Math.max(1, plot.getContourCellSize());

      // size is treated as the number of cells if the graph is divided into 100 row/col grid.
      if(graphSize != null) {
         double cellSize = Math.min(graphSize.width, graphSize.height) / 100.0;
         size = (int) (Math.min(size, 100) * cellSize);
      }

      return Math.max(2, size);
   }

   private void processColorLegend(LegendsDescriptor legends, GraphElement elem, VisualFrame color)
   {
      LegendDescriptor colorDesc = null;
      CompositeTextFormat format;
      List<String> vars = Arrays.asList(elem.getVars());

      if(legends != null && color != null) {
         colorDesc = GraphUtil.getLegendDescriptor(info, legends, color.getField(), vars,
            ChartArea.COLOR_LEGEND, GraphUtil.isNodeAestheticFrame(color, elem));
      }

      if(colorDesc != null && color != null) {
         LegendSpec legend = color.getLegendSpec();
         Format cfmt = getDefaultFormat(color);

         GraphUtil.setPositionSize(color, colorDesc, legends);
         format = colorDesc.getContentTextFormat();
         legend.setTextSpec(GraphUtil.getTextSpec(format, cfmt, null));
         setLegendProperties(color, colorDesc);
         boolean donutTotal = isDonutTotal(info, color);
         legend.setVisible(!isSparkline() && !donutTotal &&
                           (maxMode ? colorDesc.isMaxModeVisible() : colorDesc.isVisible()));
         legend.setGap(legends.getGap());
         legend.setPadding(legends.getPadding());

         // if is Composite frame, should set the infos to its guide frame
         if(color instanceof CompositeVisualFrame) {
            VisualFrame gcolor = ((CompositeVisualFrame) color).getGuideFrame();

            if(gcolor != null) {
               LegendSpec gspec = gcolor.getLegendSpec();
               TextSpec spec = legend.getTextSpec();

               if(spec.getFormat() == null) {
                  spec.setFormat(gspec.getTextSpec().getFormat());
               }

               gspec.setTextSpec(legend.getTextSpec());
               GraphUtil.setLegendSpec(gspec, legends);
            }
         }

         // avoid duplicate legend.
         if(elem instanceof LineElement && "true".equals(elem.getHint("_show_point_")) ||
            elem instanceof PointElement && "true".equals(elem.getHint("_show_point_")) ||
            GraphTypes.isContour(info.getChartType()))
         {
            // elem color frame already set, don't change it (StackedMeasuresColorFrame). (61511)
            VisualFrame color0 = elem.getColorFrame();

            if(color0 != null) {
               // need to clone since point/line share the same color frame. (52443)
               color0 = (VisualFrame) color0.clone();
               color0.getLegendSpec().setVisible(false);
               elem.setColorFrame((ColorFrame) color0);
            }
         }
      }
   }

   private boolean isDonutTotal(ChartInfo info, VisualFrame color) {
      if(info.isDonut()) {
         if(color instanceof MultiMeasureColorFrame) {
            return true;
         }
         else if(color instanceof CompositeColorFrame) {
            CompositeColorFrame comp = (CompositeColorFrame) color;

            if(comp.getFrameCount() == 2 && comp.getFrame(1) instanceof MultiMeasureColorFrame)
            {
               return true;
            }
         }
      }

      return false;
   }

   private void processShapeLegend(LegendsDescriptor legends, String var, VisualFrame shpframe) {
      CompositeTextFormat format;
      LegendDescriptor shapeDesc = null;

      if(legends != null && shpframe != null) {
         shapeDesc = GraphUtil.getLegendDescriptor(
            info, legends, shpframe.getField(), var, ChartArea.SHAPE_LEGEND);
      }

      if(shapeDesc != null && shpframe != null) {
         GraphUtil.setPositionSize(shpframe, shapeDesc, legends);
         format = shapeDesc.getContentTextFormat();
         Format ffmt = getDefaultFormat(shpframe);
         LegendSpec legend = shpframe.getLegendSpec();
         legend.setTextSpec(GraphUtil.getTextSpec(format, ffmt, null));
         setLegendProperties(shpframe, shapeDesc);
         legend.setVisible(!isSparkline() &&
                              (maxMode ? shapeDesc.isMaxModeVisible() : shapeDesc.isVisible()));
         legend.setGap(legends.getGap());
         legend.setPadding(legends.getPadding());
      }
   }

   private void processSizeLegend(LegendsDescriptor legends, String var, VisualFrame size,
                                  boolean hide)
   {
      CompositeTextFormat format;
      LegendDescriptor sizeDesc = null;

      if(legends != null && size != null) {
         sizeDesc = GraphUtil.getLegendDescriptor(
            info, legends, size.getField(), var, ChartArea.SIZE_LEGEND);
      }

      if(sizeDesc != null && size != null) {
         LegendSpec legend = size.getLegendSpec();
         Format sfmt = getDefaultFormat(size);

         GraphUtil.setPositionSize(size, sizeDesc, legends);
         format = sizeDesc.getContentTextFormat();
         legend.setTextSpec(GraphUtil.getTextSpec(format, sfmt, null));
         setLegendProperties(size, sizeDesc);
         // if not size binding but no edge size binding.
         boolean nodeSize = info instanceof RelationChartInfo &&
            ((RelationChartInfo) info).getRTNodeSizeField() != null && size.getField() != null &&
            size.getField().equals(BrushDataSet.ALL_HEADER_PREFIX +
                                      ((RelationChartInfo) info).getNodeSizeField().getFullName());
         legend.setVisible(!isSparkline() && !hide && !GraphTypes.isContour(info.getChartType()) &&
                           (maxMode ? sizeDesc.isMaxModeVisible() : sizeDesc.isVisible()) &&
                           size.getField() != null &&
                              (!size.getField().startsWith(BrushDataSet.ALL_HEADER_PREFIX) ||
                               // size legend missing after brushing. (57608, 57623, 57646)
                               nodeSize));
         legend.setGap(legends.getGap());
         legend.setPadding(legends.getPadding());
      }
   }

   /**
    * Transfers the plot banding properties from PlotDescriptor to PlotSpec
    * @param pdesc PlotDescriptor
    */
   private void addPlotBanding(PlotDescriptor pdesc) {
      final PlotSpec pSpec = graph.getCoordinate().getPlotSpec();

      if(GraphUtil.isXBandingEnabled(info)) {
         final Color xColor = pdesc.getXBandColor();
         pSpec.setXBandColor(xColor);
         pSpec.setXBandSize(pdesc.getXBandSize());
      }

      if(GraphUtil.isYBandingEnabled(info)) {
         final Color yColor = pdesc.getYBandColor();
         pSpec.setYBandColor(yColor);
         pSpec.setYBandSize(pdesc.getYBandSize());
      }
   }

   /**
    * Transfers the plot background properties from PlotDescriptor to PlotSpec
    * @param pdesc PlotDescriptor
    */
   private void addPlotBackgroundColor(PlotDescriptor pdesc) {
      // bg for treemap isn't supported
      if(info.getChartType() == CHART_TREEMAP) {
         return;
      }

      PlotSpec pSpec = graph.getCoordinate().getPlotSpec();
      pSpec.setBackground(pdesc.getBackground());
   }

   private void fixDefaultFormats() {
      fixDefaultFormats(ymeasures, info.getYFields());
      fixDefaultFormats(xmeasures, info.getXFields());
   }

   /**
    * Fix default axis formats so that they match the data model's formats.
    *
    * @param measures the measures to fix the formats of.
    * @param fields   the chart's fields to search the refs of.
    */
   private void fixDefaultFormats(List<String> measures, ChartRef[] fields) {
      for(String measure : measures) {
         Format defaultFormat = getDefaultFormat(measure);
         ChartRef measureRef = getChartRef(measure, true, fields);
         AxisDescriptor axisDescriptor = GraphUtil.getAxisDescriptor(info, measureRef);
         String[] measureArr = {measure};
         CompositeTextFormat measureFormat = getAxisLabelFormat(axisDescriptor, measureArr, false);

         if(defaultFormat instanceof DecimalFormat) {
            measureFormat.getDefaultFormat().getFormat()
               .setFormatSpec(((DecimalFormat) defaultFormat).toPattern());
         }
      }
   }

   private ChartAggregateRef getAggregateRef(String var) {
      ChartRef ref = getChartRef(var);

      if(ref instanceof ChartAggregateRef) {
         return (ChartAggregateRef) ref;
      }

      return null;
   }

   private ChartRef getChartRef(String var) {
      if(var == null) {
         return null;
      }

      return info.getFieldByName(GraphUtil.getOriginalCol(BoxDataSet.getBaseName(var)), true);
   }

   /**
    * Fix the scale of the specified dimension according to the specified
    * chart type.
    */
   protected void fixDScale(String dim, boolean date, int... types) {
      Scale scale = scales.get(dim);
      int type = types[0];
      Class dimType = odata.getType(dim);

      // for line/point date, should use time scale instead.
      // time scale disabled on contour so should not be applied.
      if(!GraphTypes.isPolar(type) && !GraphTypes.isWaterfall(type) &&
         !GraphTypes.isFunnel(type) && !GraphTypes.isContour(type) && date)
      {
         int dtype = getDateType(odata, dim);

         if(dtype != -1) {
            TimeScale tscale = scale instanceof TimeScale ? (TimeScale) scale : new TimeScale(dim);
            tscale.setType(dtype);
            scales.put(dim, tscale);
         }
      }
      // if a number is used as dimension but for line/area chart, it may
      // only because the data needs to be sorted on the value and the
      // display should mimic as if linear scale is used instead of leaving
      // gaps at each end.
      else if(dimType != null && scale instanceof CategoricalScale &&
              Number.class.isAssignableFrom(dimType))
      {
         boolean line = true;

         for(int t1 : types) {
            if(!GraphTypes.isLine(t1) && !GraphTypes.isArea(t1)) {
               line = false;
               break;
            }
         }

         if(line) {
            ((CategoricalScale) scale).setFill(true);
         }
      }
   }

   /**
    * Find the time scale type of a date dimension.
    */
   private static int getDateType(DataSet data, String dim) {
      VSDataSet vs = data instanceof VSDataSet ? (VSDataSet) data : null;
      DataSet oodata = data;

      if(vs == null) {
         while(oodata instanceof DataSetFilter) {
            oodata = ((DataSetFilter) oodata).getDataSet();

            if(oodata instanceof VSDataSet) {
               vs = (VSDataSet) oodata;
               break;
            }
         }
      }

      if(vs != null) {
         int index = vs.indexOfHeader(dim);

         if(index >= 0) {
            return vs.getDateType(index);
         }
      }

      return TimeScale.YEAR;
   }

   /**
    * Check if the dimension ref is date type.
    */
   protected static boolean isDate(XDimensionRef dref) {
      boolean others = false;

      // don't use date scale if 'Others' is in the data
      if(dref instanceof VSDimensionRef) {
         RankingCondition cond = ((VSDimensionRef) dref).getRankingCondition();
         others = cond != null && cond.isGroupOthers();
      }

      return dref != null && dref.isDateTime() && dref.getDateLevel() != DateRangeRef.NONE_INTERVAL
         && dref.isTimeSeries() && (dref.getRefType() & DataRef.CUBE) == 0 && !others &&
         DateRangeRef.isDateTime(dref.getDateLevel());
   }

   /**
    * Fix the scale of the specified measure according to the specified
    * chart type.
    */
   protected void fixMScale(String measure, int type) {
      Scale scale0 = scales.get(measure);

      // fix scale for stack chart
      if(GraphTypes.isStack(type) && scale0 instanceof LinearScale) {
         LinearScale scale = (LinearScale) scale0;
         ScaleRange range = scale.getScaleRange();

         // might be a brush range
         if(range instanceof BrushRange) {
            range = ((BrushRange) range).getScaleRange();
         }

         String sname = adata != null ? BrushDataSet.ALL_HEADER_PREFIX + measure : measure;

         if(!info.isSeparatedGraph() && range instanceof StackRange) {
            // merging into one, add field to range
            if(!GraphTypeUtil.isStackMeasures(info, desc)) {
               StackRange range2 = (StackRange) range;
               range2.addStackFields(sname);
            }
         }
         else {
            // apply stack range
            StackRange range2 = new StackRange();
            // don't lose the scale range set for other measures. (59322)
            range2.copyMeasureRanges(scale.getScaleRange());

            if(!info.isSeparatedGraph()) {
               if(!GraphTypeUtil.isStackMeasures(info, desc)) {
                  range2.addStackFields(sname);
               }
            }

            scale.setScaleRange(range2);
         }
      }
      // fix scale for pie
      else if(GraphTypes.isPie(type) && scale0 instanceof LinearScale) {
         LinearScale scale = (LinearScale) scale0;
         // apply stack range
         StackRange range = new StackRange();
         // for pie, always aggregate on absolute value
         range.setAbsoluteValue(true);

         // for pie, do not apply log scale
         if(scale instanceof LogScale) {
            LinearScale scale2 = scale.copyLinearScale();
            List<String> keys = new ArrayList<>(scales.keySet());

            for(int i = 0; i < keys.size(); i++) {
               String key = keys.get(i);
               Object val = scales.get(key);

               if(val.equals(scale)) {
                  scales.put(key, scale2);
               }
            }

            scale = scale2;
            scale0 = scale2;
         }

         // if separate style, we make the pie on top of each other. this is needed to
         // support the donut with a number in middle.
         if(info.isMultiStyles()) {
            range.addStackFields(scale.getFields()[0]);
         }
         // multiple measures in a pie are stacked together
         // see DefaultGraphGenerator.createEGraph0().
         else {
            range.addStackFields(scale.getFields());
         }

         scale.setScaleRange(range);
      }
      // fix scale for waterfall
      else if(GraphTypes.isWaterfall(type) && scale0 instanceof LinearScale) {
         LinearScale scale = (LinearScale) scale0;
         // apply stack scale
         StackRange range = new StackRange();
         // turn off stack negative
         range.setStackNegative(false);
         scale.setScaleRange(range);
      }
      // fix scale for pareto
      else if(GraphTypes.isPareto(type) && scale0 instanceof LinearScale) {
         LinearScale scale = (LinearScale) scale0;
         // apply pareto scale for pareto
         ParetoRange range = new ParetoRange();
         scale.setScaleRange(range);
      }
      else if(GraphTypes.isBoxplot(type)) {
         String[] boxfields = {
            measure,
            BoxDataSet.MIN_PREFIX + measure,
            BoxDataSet.Q25_PREFIX + measure,
            BoxDataSet.MEDIUM_PREFIX + measure,
            BoxDataSet.Q75_PREFIX + measure,
            BoxDataSet.MAX_PREFIX + measure
         };
         scale0.setFields(boxfields);
         scale0.setDataFields(boxfields);
         scale0.setMeasure(measure);
      }

      // all data exists? scale should include all header
      if(adata != null && !measure.startsWith(BrushDataSet.ALL_HEADER_PREFIX)) {
         boolean waterfall = GraphTypes.isWaterfall(type);
         boolean boxplot = GraphTypes.isBoxplot(type);
         boolean gantt = GraphTypes.isGantt(type);
         boolean interval = GraphTypes.isInterval(type);
         String aheader = BrushDataSet.ALL_HEADER_PREFIX + measure;

         if(!containsField(scale0, aheader)) {
            String[] flds = scale0.getFields();
            String[] nflds;

            if(waterfall || interval) {
               nflds = new String[flds.length + 2];
            }
            else if(boxplot) {
               nflds = new String[flds.length + 6];
            }
            else if(gantt) {
               nflds = new String[flds.length * 2];
            }
            else {
               nflds = new String[flds.length * 2];
            }

            System.arraycopy(flds, 0, nflds, 0, flds.length);
            nflds[flds.length] = aheader;
            Scale scale2 = scale0.clone();

            // add all header for sum
            if(waterfall) {
               nflds[nflds.length - 1] = SumDataSet.SUM_HEADER_PREFIX + aheader;
            }
            else if(interval) {
               nflds[nflds.length - 1] = BrushDataSet.ALL_HEADER_PREFIX +
                  IntervalDataSet.TOP_PREFIX + measure;
            }
            else if(boxplot) {
               nflds[nflds.length - 1] = BoxDataSet.MIN_PREFIX + aheader;
               nflds[nflds.length - 2] = BoxDataSet.Q25_PREFIX + aheader;
               nflds[nflds.length - 3] = BoxDataSet.MEDIUM_PREFIX + aheader;
               nflds[nflds.length - 4] = BoxDataSet.Q75_PREFIX + aheader;
               nflds[nflds.length - 5] = BoxDataSet.MAX_PREFIX + aheader;
               scale2.setMeasure(aheader);
            }
            else if(gantt) {
               System.arraycopy(flds, 0, nflds, flds.length, flds.length);

               // place ALL fields in the front since TimeScale.init() uses the first
               // field to determine interval.
               for(int i = 0; i < flds.length; i++) {
                  nflds[i + flds.length] = BrushDataSet.ALL_HEADER_PREFIX + flds[i];
               }
            }
            // need all fields for graph with multiple measures. (50184)
            else {
               for(int i = 0; i < flds.length; i++) {
                  nflds[i + flds.length] = BrushDataSet.ALL_HEADER_PREFIX + flds[i];
               }
            }

            scale2.setFields(nflds);
            scale2.setDataFields(nflds);

            for(int i = 0; i < nflds.length; i++) {
               scales.putIfAbsent(nflds[i], scale2);
            }

            List<String> keys = new ArrayList<>(scales.keySet());

            // see comments before about stacking all vars in pie
            if(GraphTypes.isPie(type) && scale2 instanceof LinearScale &&
               // only stack the first measure in pie (for donut with middle piece)
               ymeasures.indexOf(measure) == 0)
            {
               if(((LinearScale) scale2).getScaleRange() instanceof StackRange) {
                  StackRange range = (StackRange) ((LinearScale) scale2).getScaleRange();
                  // should only include the all fields and not the brushed fields.
                  // otherwise a gap will exist in the pie
                  String[] allFields = Arrays.stream(nflds)
                     .filter(f -> f.startsWith(BrushDataSet.ALL_HEADER_PREFIX))
                     .toArray(String[]::new);
                  range.removeAllStackFields();
                  range.addStackFields(allFields);
               }
            }

            for(int i = 0; i < keys.size(); i++) {
               String key = keys.get(i);
               Object val = scales.get(key);

               if(val.equals(scale0)) {
                  scales.put(key, scale2);
               }
            }
         }
      }

      Scale scale = scales.get(measure);

      if(scale instanceof LinearScale) {
         ScaleRange range = ((LinearScale) scale).getScaleRange();

         // always replace scale for brush
         if(!(range instanceof BrushRange)) {
            range = new BrushRange(range);
            ((BrushRange) range).setStackMeasures(GraphTypeUtil.isStackMeasures(info, desc));
            ((LinearScale) scale).setScaleRange(range);
         }
      }
   }

   /**
    * Test if contains the specified field.
    */
   private boolean containsField(Scale scale, String fld) {
      String[] flds = scale.getFields();

      for(int i = 0; i < flds.length; i++) {
         if(flds[i].equals(fld)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Fix the scales properties of the specified coord,
    * set the attribute from desc and info.
    */
   protected void fixCoordProperties(Coordinate coord, int chartType) {
      PlotDescriptor plotdesc = desc.getPlotDescriptor();

      if(coord instanceof RectCoord) {
         RectCoord rect = (RectCoord) coord;
         Scale xscale = rect.getXScale();
         AxisDescriptor xdesc = null;

         if(xscale != null) {
            xdesc = getAxisDescriptor(xscale, "x");
            assignAxisCSS(xdesc, isRotated(rect) ? "y" : (chartType == 0) ? "x2" : "x");
            String[] flds = xscale.getFields();
            boolean inner = !(xscale instanceof CategoricalScale) ||
               flds.length > 0 && isInnerField("x", flds[0]) &&
                  (!info.isSeparatedGraph() || info.isSeparatedGraph() &&
                     GraphTypes.isRect(info.getRTChartType()));
            CompositeTextFormat format = getAxisLabelFormat(xdesc, flds, false);
            Format fmt2 = GraphUtil.getFormat(format);
            AxisSpec xaxis = createAxisSpec(xdesc, plotdesc, flds, xscale, false, inner, true);

            /* the pie axis can be controlled now so shouldn't change the user set options.
            // fix bug1288772087744, since pie cannot set the property of axis
            // line, so when covert chart type, the "truncate" and "nonull"
            // property should use default value.
            if(isGraphType(GraphTypes.CHART_PIE, GraphTypes.CHART_DONUT, GraphTypes.CHART_3D_PIE)) {
               xaxis.setTruncate(true);
               xdesc.setNoNull(false);
            }
            */

            if(isSparkline()) {
               xaxis.setAxisStyle(AxisSpec.AXIS_NONE);
               xaxis.setGridStyle(0);
               xaxis.setLabelVisible(false);
               xscale.setScaleOption(0);
            }

            if(xscale instanceof TimeScale) {
               ((TimeScale) xscale).setIncrement(xdesc.getIncrement());
               Number min = xdesc.getMinimum();

               if(min != null) {
                  ((TimeScale) xscale).setMin(new Date(min.longValue()));
               }

               Number max = xdesc.getMaximum();

               if(max != null) {
                  ((TimeScale) xscale).setMax(new Date(max.longValue()));
               }

               if(GraphTypeUtil.isHeatMap(info)) {
                  ((TimeScale) xscale).setMaxTicks(GDefaults.AXIS_LABEL_MAX_COUNT);
               }
            }

            if(xscale instanceof LinearScale) {
               applyAxisDescriptor((LinearScale) xscale, xdesc);
            }
            // rotate the labels if auto and too crowded
            else if(xmeasures.size() == 0 && format.getRotation() == null) {
               setDefaultRotation(xaxis, xscale, xdims);
            }

            if(inner) {
               setAbbreviate(xscale, xaxis, fmt2, !isRotated(rect));
            }

            if(info instanceof VSChartInfo && ((VSChartInfo) info).isAppliedDateComparison()) {
               setupDateComparisonAxis(xscale, xdesc, inner, format, xaxis, !isRotated(rect));
            }

            xscale.setAxisSpec(xaxis);

            if(xdesc.isNoNull() ||
               // @by: ChrisSpagnoli bug1425017643634 2015-3-30
               // Also filter nulls from Scale if ProjectTrendLineForward set
               (inner && info.canProjectForward() && plotdesc.getProjectTrendLineForward() > 0))
            {
               xscale.setScaleOption(xscale.getScaleOption() | Scale.NO_NULL);
            }
         }

         Scale yscale = rect.getYScale();

         if(yscale != null) {
            AxisDescriptor ydesc = getAxisDescriptor(yscale, "y");
            assignAxisCSS(ydesc, isRotated(rect) ? "x" : "y");
            boolean inner = setupYScale(yscale, ydesc, rect, false);

            if(inner) {
               GraphDefault.setScaleOptions(this, rect, xscale, yscale);
            }
            // period dim can be on the left of the facet
            else if(info instanceof VSChartInfo && ((VSChartInfo) info).isAppliedDateComparison()) {
               CompositeTextFormat format = getAxisLabelFormat(ydesc, yscale.getFields(), false);
               setupDateComparisonAxis(yscale, ydesc, inner, format, yscale.getAxisSpec(), false);
            }

            if(ydesc.isNoNull()) {
               yscale.setScaleOption(yscale.getScaleOption() | Scale.NO_NULL);
            }

            // axis scale for funnel is not very meaningful since the bar is centered.
            if(isGraphType(GraphTypes.CHART_FUNNEL) && yscale instanceof LinearScale) {
               yscale.getAxisSpec().setLabelVisible(false);

               // axis color can't be controlled by user. use the x axis.
               if(xdesc != null) {
                  yscale.getAxisSpec().setLineColor(xdesc.getLineColor());
                  yscale.getAxisSpec().setLineVisible(xdesc.isLineVisible());
               }
            }
         }

         Scale yscale2 = rect.getYScale2();

         if(yscale2 != null) {
            AxisDescriptor y2desc = getAxisDescriptor(yscale2, "y");

            if(y2desc.isNoNull()) {
               yscale2.setScaleOption(yscale2.getScaleOption() | Scale.NO_NULL);
            }
         }
      }
      else if(coord instanceof AbstractParallelCoord) {
         Scale[] scales = coord.getScales();

         for(int i = 0; i < scales.length; i++) {
            AxisSpec axis = new AxisSpec();
            Scale scale = scales[i];

            if(scale.getFields() == null || scale.getFields().length <= 0) {
               continue;
            }

            if(!GraphTypeUtil.containsAggregate(scale.getFields()[0], info)) {
               continue;
            }

            AxisDescriptor axisD = getAxisDescriptor(scale, null);
            setupAxisSpec(axis, axisD, scale.getFields(), false);

            if(scale instanceof TimeScale) {
               ((TimeScale) scale).setIncrement(axisD.getIncrement());
               Number min = axisD.getMinimum();

               if(min != null) {
                  ((TimeScale) scale).setMin(new Date(min.longValue()));
               }

               Number max = axisD.getMaximum();

               if(max != null) {
                  ((TimeScale) scale).setMax(new Date(max.longValue()));
               }
            }

            if(scale instanceof LinearScale) {
               applyAxisDescriptor((LinearScale) scale, axisD);
            }

            scale.setAxisSpec(axis);
         }
      }
      else if(coord instanceof MekkoCoord) {
         Scale[] scales = coord.getScales();

         for(int i = 0; i < scales.length; i++) {
            Scale scale = scales[i];

            if(scale.getFields() == null || scale.getFields().length <= 0) {
               continue;
            }

            String[] flds = scale.getFields();
            AxisDescriptor axisD = getAxisDescriptor(scale, i == 1 ? "y" : "x");
            boolean isY = scale instanceof LinearScale;
            AxisSpec axis = createAxisSpec(axisD, plotdesc, flds, scale, false, true, !isY);

            setupAxisSpec(axis, axisD, scale.getFields(), false);

            if(axisD.isNoNull()) {
               scale.setScaleOption(scale.getScaleOption() | Scale.NO_NULL);
            }

            if(isY) {
               axis.getTextSpec().setFormat(NumberFormat.getPercentInstance());
            }

            scale.setAxisSpec(axis);
         }
      }
      else if(coord instanceof PolarCoord) {
         final PolarCoord polarCoord = (PolarCoord) coord;
         fixPieCoord(polarCoord);
         final Coordinate underlyingCoordinate = polarCoord.getCoordinate();

         if(getSizeFrame(null) != null && !fake) {
            underlyingCoordinate.transpose();
            polarCoord.setType(PolarCoord.THETA_RHO);

            if(chartType == GraphTypes.CHART_DONUT) {
               underlyingCoordinate.reflect(false);
            }
         }
         else {
            polarCoord.setType(PolarCoord.THETA);
         }
      }
   }

   // setup date comparison axis defaults.
   private void setupDateComparisonAxis(Scale xscale, AxisDescriptor xdesc, boolean inner,
                                        CompositeTextFormat format, AxisSpec xaxis,
                                        boolean horizontal)
   {
      boolean showDates = DateComparisonUtil.showPartAsDates(dateComparison);
      VSChartInfo vsinfo = (VSChartInfo) info;
      ChartRef partCol = Arrays.stream(vsinfo.getRuntimeDateComparisonRefs())
         .filter(a -> a instanceof VSChartDimensionRef)
         .reduce((a, b) -> b).orElse(null);
      String field = xscale.getFields().length > 0 ? xscale.getFields()[0] : null;
      boolean isPartCol = partCol != null && field != null && field.equals(partCol.getFullName());
      boolean multiLine = showDates && isPartCol ||
         dateComparison.getPeriods() instanceof CustomPeriods && (inner || isPartCol);
      ChartRef ref = field == null ? null : vsinfo.getFieldByName(field, true);
      boolean isDcDataGroupCol = ref instanceof VSDimensionRef &&
         ((VSDimensionRef) ref).getNamedGroupInfo() instanceof DCNamedGroupInfo;

      // multi-line labels for dc chart.
      if(multiLine) {
         DateComparisonFormat fmt = null;

         if(showDates && dateComparison.getPeriods() instanceof StandardPeriods) {
            Format userFormat = null;

            if(!format.getUserDefinedFormat().getFormat().isEmpty()) {
               userFormat = xaxis.getTextSpec().getFormat();
            }

            fmt = GraphFormatUtil.getDateComparisonFormat(
               (VSChartInfo) info, dateComparison, data, xscale, userFormat);
            xaxis.getTextSpec().setFormat(fmt);
         }

         if(isPartCol) {
            boolean evenLineSpacing = !dateComparison.isShowMostRecentDateOnly();
            ChartRef[] refs = horizontal ? vsinfo.getRTXFields() : vsinfo.getRTYFields();

            if(evenLineSpacing && refs != null && refs.length > 1 &&
               !Tool.equals(refs[refs.length - 2].getFullName(), xscale.getFields()[0]))
            {
               evenLineSpacing = false;
            }

            // for multi-line label, spread it out so the lines matches the corrresponding bar
            // positions better. the gap is between the bars (which has a size of 25,
            // @see ChartDcProcessor.fixDcSizeFrame).
            xaxis.getTextSpec().setLineSpacing(evenLineSpacing ? 5 / 30.0 : Double.NaN);
         }

         xaxis.setLastOrAll(true);
         setDcFormat(xscale, xaxis);
         boolean autoRotation = xdesc.getAxisLabelTextFormat().getRotation() == null;

         if(horizontal) {
            long dims = Arrays.stream(info.getRTXFields())
               .filter(a -> a instanceof VSChartDimensionRef).count();
            // x (part) axis is now at top, if there are other dimension on the facet
            // (levels between date part, e.g. week-of-year and the bottom date, e.g. year)
            // there will be much more space per date part label, and displaying labels on top
            // horizontally looks better when there is only a single line.
            // if multiple lines, display labels vertically on top.
            boolean onlyShowMostRecentDate = fmt != null ?
               fmt.isDisplayShortDate() : dateComparison.isShowMostRecentDateOnly();
            boolean horLabel = !isDcDataGroupCol &&
               !DateComparisonUtil.isShowFullDay(partCol) && onlyShowMostRecentDate && dims >= 2;

            if(!isDcDataGroupCol && dateComparison.getPeriods() instanceof CustomPeriods &&
               xscale.getValues() != null)
            {
               Object[] values = xscale.getValues();

               List shortLabels = Arrays.stream(values)
                  .filter(v -> v instanceof DCMergeDatesCell)
                  .filter(cell -> ((DCMergeDatesCell) cell).getShortLabelFormat() != null)
                  .collect(Collectors.toList());

               if(shortLabels.size() == values.length) {
                  horLabel = true;
               }
            }

            if(horLabel && autoRotation) {
               xaxis.getTextSpec().setRotation(0);
               //xaxis.setAxisSize(0);
            }
            else if(!horLabel && autoRotation) {
               xaxis.getTextSpec().setRotation(90);
               //xaxis.setAxisSize(0);
            }
         }
         // axis at left, don't rotate
         else {
            if(xaxis.getTextSpec().getRotation() != 0 && autoRotation) {
               xaxis.getTextSpec().setRotation(0);
            }

            // bars are displayed from bottom up sorted by dates, so we should
            // reverse the lines so it displays bottom up instead of top down.
            xaxis.getTextSpec().setReverseLines(true);
         }
      }
   }

   private void fixPieCoord(PolarCoord polar) {
      for(Scale xscale: polar.getScales()) {
         if(xscale != null) {
            AxisDescriptor xdesc = getAxisDescriptor(xscale, null);
            String[] flds = xscale.getFields();
            CompositeTextFormat format = getAxisLabelFormat(xdesc, flds, false);
            Format fmt2 = GraphUtil.getFormat(format);
            xscale.getAxisSpec().getTextSpec().setFormat(fmt2);
         }
      }
   }

   // set AxisSpec from axis descriptor
   protected void setupAxisSpec(AxisSpec axis, AxisDescriptor axisD, String[] flds, boolean secondary) {
      CompositeTextFormat format = getAxisLabelFormat(axisD, flds, secondary);
      // should only use the first field's default format (e.g. date comparison %change&value)
      String fld = flds.length > 0 ? flds[0] : null;

      if(fld != null) {
         fld = ElementVO.getBaseName(fld);
      }

      Format fmt = fld != null ? getDefaultFormat(fld) : null;
      fmt = fmt == null && fld != null ? getDefaultFormat(ChartAggregateRef.getBaseName(fld)) : fmt;
      axis.setLineColor(axisD.getLineColor());
      axis.setLineVisible(!maxMode && axisD.isLineVisible() || maxMode && axisD.isMaxModeLineVisible());
      axis.setTickVisible(axisD.isTicksVisible());
      axis.setTextSpec(GraphUtil.getTextSpec(format, fmt, null));
      axis.setLabelVisible(!maxMode && axisD.isLabelVisible() || maxMode && axisD.isMaxModeLabelVisible());
      axis.setTextFrame(axisD.getTextFrame());
      axis.setTruncate(axisD.isTruncate());
      axis.setLabelGap(axisD.getLabelGap());
   }

   private void assignAxisCSS(AxisDescriptor axisDesc, String axis) {
      axisDesc.setAxisCSS(axis);
   }

   /**
    * Set Y scale spec.
    * @return true if the coord is an inner coord.
    */
   protected boolean setupYScale(Scale yscale, AxisDescriptor ydesc,
                                 RectCoord rect, boolean secondary)
   {
      String[] flds = yscale.getFields();
      CompositeTextFormat format = getAxisLabelFormat(ydesc, flds, secondary);
      Format fmt2 = GraphUtil.getFormat(format);
      boolean inner = !(yscale instanceof CategoricalScale) ||
         flds.length > 0 && isInnerField("y", flds[0]) &&
         GraphTypes.isRect(info.getRTChartType());
      PlotDescriptor plotdesc = desc.getPlotDescriptor();
      AxisSpec yaxis = createAxisSpec(ydesc, plotdesc, flds, yscale, secondary, inner, false);

      if(isSparkline()) {
         yaxis.setAxisStyle(AxisSpec.AXIS_NONE);
         yaxis.setGridStyle(0);

         if(graph.getElementCount() > 0 && graph.getElement(0) instanceof LineElement) {
            yscale.setScaleOption(0);
         }
         else {
            yscale.setScaleOption(Scale.ZERO);
         }
      }

      if(yscale instanceof TimeScale) {
         ((TimeScale) yscale).setIncrement(ydesc.getIncrement());
         Number min = ydesc.getMinimum();

         if(min != null) {
            ((TimeScale) yscale).setMin(new Date(min.longValue()));
         }

         Number max = ydesc.getMaximum();

         if(max != null) {
            ((TimeScale) yscale).setMax(new Date(max.longValue()));
         }
      }

      if(yscale instanceof LinearScale) {
         applyAxisDescriptor((LinearScale) yscale, ydesc);

         // if a fake column, we display a single point for the data and
         // make the output look almost like a table, don't draw y scale
         if(fake && flds.length > 0 && flds[0].equals("value")) {
            Scale xscale = rect.getXScale();

            // the axis line for the fake should be same as the real x.
            // if x line is hidden, we shouldn't draw the fake axis line
            // @by larryl, see getAxisDescriptor(Scale). If ydims exists,
            // the line visibility/color is copied from ydim
            if(xscale != null && !(!info.isInvertedGraph() && ydims.size() > 0)) {
               AxisDescriptor xdesc = getAxisDescriptor(xscale, "x");
               // if facet, leave the grid to be controlled by facet coord
               boolean yline = ydims.size() == 0 && xdesc.isLineVisible() ||
                  ydims.size() > 0 &&
                  getAxisDescriptor(ydims.get(ydims.size() - 1)).isLineVisible();

               yaxis.setLineVisible(yline);
               yaxis.setLineColor(xscale.getAxisSpec().getLineColor());
            }

            yaxis.setLabelVisible(false);
            yaxis.setTickVisible(false);
            yaxis.setGridStyle(0);

            LinearScale linear = (LinearScale) yscale;
            linear.setMin(isStackedGraph() ? 1 : 0);
            linear.setMax(2); // point at middle (value = 1)
         }
      }
      // rotate the labels if auto and too crowded
      else if(xmeasures.size() > 0 && format.getRotation() == null) {
         setDefaultRotation(yaxis, yscale, ydims);
      }

      if(inner) {
         setAbbreviate(yscale, yaxis, fmt2, isRotated(rect));
      }

      yscale.setAxisSpec(yaxis);

      return inner;
   }

   // Apply settings in AxisDescriptor to linear scale
   protected void applyAxisDescriptor(LinearScale linear, AxisDescriptor ydesc) {
      if(GraphTypes.isFunnel(info.getChartType())) {
         return;
      }

      linear.setIncrement(ydesc.getIncrement());
      linear.setMinorIncrement(ydesc.getMinorIncrement());

      if(ydesc.getMaximum() != null) {
         setMax(linear, ydesc.getMaximum());
      }

      if(ydesc.getMinimum() != null) {
         setMin(linear, ydesc.getMinimum());
      }
   }

   // Create AxisSpec from AxisDescriptor and PlotDescriptor
   protected AxisSpec createAxisSpec(AxisDescriptor xdesc, PlotDescriptor plotdesc,
                                     String[] flds, Scale scale, boolean secondary,
                                     boolean inner, boolean x)
   {
      AxisSpec spec = new AxisSpec();
      boolean inverted = info.isInvertedGraph();

      if(x) {
         // should use outer axis color for outer grid line in facet. (57435)
         if(inner) {
            spec.setGridColor(plotdesc.getXGridColor());
         }

         spec.setGridStyle(plotdesc.getXGridStyle());
      }
      else {
         if(inner) {
            spec.setGridColor(plotdesc.getYGridColor());
         }

         spec.setGridStyle(plotdesc.getYGridStyle());
      }

      // facet grid covered by treemap vo. (59666)
      spec.setGridOnTop(GraphTypes.isTreemap(info.getRTChartType()) ||
                           plotdesc.isFacetGrid() && GraphTypeUtil.isHeatMap(info));
      // extend facet grid to between inner axis labels for heatmap.
      // may consider other chart types in the future.
      spec.setFacetGrid(plotdesc.isFacetGrid() && GraphTypeUtil.isHeatMap(info));

      spec.setGridBetween(fake);
      boolean width = inverted && inner && x || !(inverted && inner) && !x;
      spec.setAxisSize(width ? xdesc.getAxisWidth() : xdesc.getAxisHeight());
      spec.setInPlot("true".equals(SreeEnv.getProperty("graph.axis.inplot")));

      if(scale instanceof TimeScale) {
         // if chart is a continuous drawing (instead of discrete points/bars).
         boolean continous = isGraphType(CHART_LINE, CHART_LINE_STACK,
                                         CHART_AREA, CHART_AREA_STACK);
         // increase label spacing if not necessary to have a label per point/bar. (51399, 57512)
         spec.setMaxLabelSpacing(continous ? 3 : 2);
      }

      setupAxisSpec(spec, xdesc, flds, secondary);
      addHighlightToAxis(spec, flds);

      return spec;
   }

   private CompositeTextFormat getAxisLabelFormat(AxisDescriptor desc,
                                                  String[] fields, boolean secondary)
   {
      if(fields == null || fields.length == 0 || secondary) {
         return desc.getAxisLabelTextFormat();
      }

      CompositeTextFormat fmt = null;

      if(info instanceof CandleChartInfo) {
         for(String field : fields) {
            final CompositeTextFormat columnLabelTextFormat = desc.getColumnLabelTextFormat(field);

            if(columnLabelTextFormat != null) {
               fmt = desc.getColumnLabelTextFormat(field);
               break;
            }
         }
      }
      else {
         String col = fields[0];
         // this uses column specific format for x dimension. not sure why y dimension
         // is not applied the same logic (at least for inverted chart). if we change
         // this, will need to transform the xml so it's backward compatible.
         fmt = info.isSeparatedGraph() || xdimlabels.containsKey(col) ?
            desc.getColumnLabelTextFormat(col) : null;
      }

      if(fmt == null) {
         fmt = desc.getAxisLabelTextFormat();
      }

      return fmt;
   }

   /**
    * If using default format and un-rotated label, allow abbreviation.
    * @param hor true if this axis is horizontal
    */
   private void setAbbreviate(Scale scale, AxisSpec axis, Format fmt, boolean hor) {
      Format fmt0 = axis.getTextSpec().getFormat();

      // abbrevate date only if using default format
      if(scale instanceof TimeScale && fmt0 != null && fmt == null) {
         axis.setAbbreviate(true);
      }

      if(fmt == null || axis.getTextSpec().getRotation() != 0) {
         return;
      }

      /*
      if(fmt instanceof DecimalFormat) {
         String percent = ((DecimalFormat) NumberFormat.getPercentInstance()).toPattern();
         String currency = ((DecimalFormat) NumberFormat.getCurrencyInstance()).toPattern();
         String str = ((DecimalFormat) fmt).toPattern();

         if(str.equals(percent)) {
            // only abbreviate percent on horizontal axis otherwise the
            // labels won't line up
            if(hor) {
               axis.setAbbreviate(true);
            }
         }
         // only show currency on the first value
         else if(str.equals(currency)) {
            axis.setAbbreviate(true);
         }
      }
      */
   }

   /**
    * Get inner most dimension or measure.
    */
   private boolean isInnerField(String type, String field) {
      boolean isX = "x".equals(type);
      ArrayList<String> list = new ArrayList<>();

      if(isX && !info.isInvertedGraph() || !isX && info.isInvertedGraph()) {
         if(xmeasures.size() > 0) {
            return xmeasures.contains(field);
         }
         else if(containsOnlyDims(type)) {
            list = xdims;
         }
      }
      else {
         if(ymeasures.size() > 0) {
            return ymeasures.contains(field);
         }
         else if(containsOnlyDims(type)) {
            list = ydims;
         }
      }

      String field0 = list.size() == 0 ? null : list.get(list.size() - 1);
      return Tool.equals(field, field0);
   }

   /**
    * Check if x or y only contains dimensions.
    */
   private boolean containsOnlyDims(String type) {
      boolean isX = "x".equals(type);

      if(isX && !info.isInvertedGraph() || !isX && info.isInvertedGraph()) {
         return xdims.size() > 0 && xmeasures.size() == 0;
      }
      else {
         return ydims.size() > 0 && ymeasures.size() == 0;
      }
   }

   /**
    * Check whether a chartRef should be treated as a dimension during graph
    * construction.
    * @return true if ref should be treated as a dimension
    */
   protected boolean isDimensionRef(ChartRef ref) {
      return GraphUtil.isDimension(ref);
   }

   /**
    * Creates a dim name/label for a chartRef and adds them to the list of dim
    * names, and to the map of dim labels. Also, this will create a Categorical
    * Scale for the dimension name.
    * @param ref dim ChartRef
    * @param names dim names
    * @param labels dim labels
    */
   private void addDimScale(ChartRef ref, List<String> names,
                            Map<String, String> labels)
   {
      String name = GraphUtil.getName(ref);
      String label = GraphUtil.getCaption(ref);
      label = Tool.localize(label);

      // ignore empty ones
      if(name != null && name.length() > 0) {
         names.add(name);
         labels.put(name, label);
         scales.put(name, new CategoricalScale(name));
      }
   }

   /**
    * Add all the x dimensions.
    */
   protected void addXDFields() {
      // add x dimension field
      for(ChartRef ref : info.getRTXFields()) {
         if(isDimensionRef(ref)) {
            addDimScale(ref, xdims, xdimlabels);
         }
      }
   }

   /**
    * Add all the x measures.
    */
   protected void addXMFields(boolean combined) {
      final List<ChartAggregateRef> chartRefs = getChartMeasureRefs(info.getRTXFields());

      if(chartRefs.isEmpty()) {
         return;
      }

      if(!combined) {
         for(ChartAggregateRef ref : chartRefs) {
            final String name = GraphUtil.getName(ref);
            String label = XCube.SQLSERVER.equals(cubeType) ? name :
               GraphUtil.getCaption(ref);
            label = Tool.localize(label);

            if(xmeasures.contains(name)) {
               continue;
            }

            xmeasures.add(name);
            xmeasurelabels.put(name, label);

            int index2 = data.indexOfHeader(name);

            if(index2 < 0) {
               throw new ColumnNotFoundException(
                  Catalog.getCatalog().getString("common.viewsheet.aggrInvalid", name));
            }

            AxisDescriptor axis = getAxisDescriptor(name);
            Scale scale = createScale(axis, data, new String[] {name},
               new ChartAggregateRef[] {ref});
            scales.put(name, scale);
         }
      }
      else {
         List<String> aflds = new ArrayList<>();

         for(ChartAggregateRef ref : chartRefs) {
            final String name = GraphUtil.getName(ref);
            String label = XCube.SQLSERVER.equals(cubeType) ?
               GraphUtil.getName(ref) : GraphUtil.getCaption(ref);
            label = Tool.localize(label);

            int index2 = data.indexOfHeader(name);

            if(index2 < 0) {
               throw new MessageException(
                  Catalog.getCatalog().getString("common.viewsheet.aggrInvalid", label));
            }

            if(xmeasures.contains(name)) {
               continue;
            }

            xmeasures.add(name);
            xmeasurelabels.put(name, label);
            aflds.add(name);
         }

         AxisDescriptor axis = getAxisDescriptor(xmeasures.get(0));

         ChartAggregateRef[] refArray = new ChartAggregateRef[chartRefs.size()];
         Scale xscale = createScale(axis, data,
                                    aflds.toArray(new String[0]),
                                    chartRefs.toArray(refArray));

         for(String afld : aflds) {
            // special case, xmeasure might be y measure too
            scales.putIfAbsent(afld, xscale);
         }
      }
   }

   /**
    * Add all the y dimensions.
    */
   protected void addYDFields() {
      ChartRef[] refs = info.getRTYFields();

      // add y dimension field
      for(int i = 0; i < refs.length; i++) {
         if(isDimensionRef(refs[i])) {
            addDimScale(refs[i], ydims, ydimlabels);
         }
      }
   }

   /**
    * Looks through an array of ChartRefs and extracts those that are needed
    * for measures.
    * @param refs Array of ChartRefs.
    * @return A set containing the ChartAggregateRefs for measure creation.
    */
   private List<ChartAggregateRef> getChartMeasureRefs(ChartRef[] refs) {
      return Arrays.stream(refs)
         .filter(r -> !isDimensionRef(r))
         .map(r -> (ChartAggregateRef) r)
         .collect(Collectors.toList());
   }

   /**
    * Add all the y measures.
    */
   protected void addYMFields(boolean combined) {
      final List<ChartAggregateRef> chartRefs = getChartMeasureRefs(info.getRTYFields());

      if(chartRefs.isEmpty()) {
         return;
      }

      if(!combined) {
         for(ChartAggregateRef ref : chartRefs) {
            String name = GraphUtil.getName(ref);
            String label = XCube.SQLSERVER.equals(cubeType) ? name : GraphUtil.getCaption(ref);
            label = Tool.localize(label);

            if(ymeasures.contains(name)) {
               continue;
            }

            ymeasures.add(name);
            ymeasurelabels.put(name, label);

            int index2 = data.indexOfHeader(name);

            if(index2 < 0) {
               throw new MessageException(
                  Catalog.getCatalog().getString("common.viewsheet.aggrInvalid", name));
            }

            AxisDescriptor axis = getAxisDescriptor(name);
            Scale scale = createScale(axis, data, new String[] {name},
               new ChartAggregateRef[] {ref});
            scales.put(name, scale);
         }
      }
      else {
         List<String> yflds = new ArrayList<>();
         int y2Cnt = 0;

         for(ChartAggregateRef ref : chartRefs) {
            String name = GraphUtil.getName(ref);
            String label = XCube.SQLSERVER.equals(cubeType) ?
               GraphUtil.getName(ref) : GraphUtil.getCaption(ref);
            label = Tool.localize(label);

            int index2 = data.indexOfHeader(name);

            if(index2 < 0) {
               throw new MessageException(
                  Catalog.getCatalog().getString("common.viewsheet.aggrInvalid", label));
            }

            if(ymeasures.contains(name)) {
               continue;
            }

            ymeasurelabels.put(name, label);

            // place secondary y at the end so the default format for it won't be used.
            if(ref.isSecondaryY()) {
               ymeasures.add(name);
               yflds.add(name);
               y2Cnt++;
            }
            else {
               ymeasures.add(ymeasures.size() - y2Cnt, name);
               yflds.add(yflds.size() - y2Cnt, name);
            }
         }

         AxisDescriptor axis = getAxisDescriptor(ymeasures.get(0));
         Scale yscale = createScale(axis, data, yflds.toArray(new String[0]),
                                    chartRefs.toArray(new ChartAggregateRef[0]));

         for(String afld : yflds) {
            scales.put(afld, yscale);
         }
      }
   }

   /**
    * Get the default measure scale for the specified measures.
    */
   private Scale createMeasureScale(DataSet data, String[] cols, XAggregateRef[] refs) {
      // impossible to share categorical scale for multiple measure
      if(refs == null || refs.length > 1 || data == null) {
         return new LinearScale(cols);
      }

      Class cls = data.getType(cols[0]);
      boolean number = AssetUtil.isNumberType(refs[0].getDataType()) ||
         Number.class.isAssignableFrom(cls);
      boolean date = XSchema.isDateType(refs[0].getDataType());
      boolean forceLinear = GraphTypes.isWaterfall(info.getChartType());

      // if date/time and not aggregated (or min/max), use time scale
      // for gantt style charts
      if(Date.class.isAssignableFrom(cls) && date) {
         return new TimeScale(cols);
      }

      if(refs[0].isAggregateEnabled() || number || forceLinear) {
         return new LinearScale(cols);
      }

      // for measure scale, we do not apply time scale
      return new CategoricalScale(cols);
   }

   /**
    * Create the graph element for corresponding chart type, add to graph.
    * @param chartType the chart tyle.
    * @param name the variable name.
    */
   protected final void createElement(int chartType, String name) {
      createElement(chartType, name, null);
   }

   /**
    * Create the graph element for corresponding chart type, add to graph.
    * @param chartType the chart tyle.
    * @param name the variable name.
    * @param xname the xvariable name.
    */
   protected final void createElement(int chartType, String name, String xname) {
      createElement(chartType, new String[] {name}, xname);
   }

   /**
    * Create the graph element for corresponding chart type, add to graph.
    * @param chartType the chart style.
    * @param names the variable names.
    * @param xname the xvariable name.
    */
   protected final void createElement(int chartType, String[] names, String xname) {
      List alls = null;

      if(adata != null) {
         String[] anames = new String[names.length];

         for(int i = 0; i < anames.length; i++) {
            if(GraphTypes.isTreemap(chartType) || GraphTypes.isRelation(chartType)
               || GraphTypes.isRadar(chartType))
            {
               anames[i] = names[i];
            }
            else if(names[i] != null) {
               anames[i] = BrushDataSet.ALL_HEADER_PREFIX + names[i];
            }
         }

         alls = createElement0(chartType, anames, xname, true, null);
      }

      alls = createElement0(chartType, names, xname, false, alls);

      // set geometry max count
      for(int i = 0; i < alls.size(); i++) {
         GraphElement elem = (GraphElement) alls.get(i);

         if(("true").equals(elem.getHint("_waterfall_"))) {
            continue;
         }

         int mcount = GraphTypes.getGeomMaxCount(chartType);
         elem.setHint(GraphElement.HINT_MAX_COUNT, mcount);
      }
   }

   /**
    * Get the default format.
    */
   protected Format getDefaultFormat(VisualFrame frame) {
      if(frame.getLegendSpec().getTextSpec().getFormat() != null) {
         return frame.getLegendSpec().getTextSpec().getFormat();
      }

      if(frame instanceof CompositeVisualFrame) {
         frame = ((CompositeVisualFrame) frame).getGuideFrame();
      }

      String fld = frame == null ? null : frame.getField();

      if(GraphTypes.isBoxplot(info.getRTChartType())) {
         fld = BoxDataSet.getBaseName(fld);
      }

      return getDefaultFormat(fld);
   }

   /**
    * If source setted format, then should use the source default format to replace the
    * default format of text field which created according to the field data type. To make
    * sure the format display in format pane keep same with the applied format in graph.
    *
    * @param bindable       the current chart bindable.
    * @param defaultFmt         the format which contains default format from source.
    * @param textformat         the text format of the text field which may contains a proper
    *                       default format which created when create the field.
    */
   protected void fixTextFieldDefaultFormat(ChartBindable bindable, Format defaultFmt,
                                       CompositeTextFormat textformat)
   {
      AestheticRef textField = bindable == null ? null : bindable.getTextField();

      if(bindable == null || textField == null ||
         defaultFmt == null && !XSchema.isDateType(textField.getDataRef().getDataType()) ||
         !textformat.getUserDefinedFormat().getFormat().isEmpty())
      {
         return;
      }

      TableFormat fmt = new TableFormat();
      fmt.setFormat(defaultFmt);
      textformat.getDefaultFormat().setFormat(new XFormatInfo(fmt.format, fmt.format_spec));
   }

   private Format getDefaultFormat(GraphElement elem) {
      String var = elem.getVarCount() == 0 ? null : elem.getVar(elem.getVarCount() - 1);
      Format fmt = getDefaultFormat(var);

      if(elem instanceof RelationElement) {
         var = ((RelationElement) elem).getSourceDim();
      }

      if(fmt == null) {
         fmt = getDefaultFormat(GraphUtil.getOriginalCol(var));
      }

      if(fmt == null && GraphTypes.isWaterfall(info.getRTChartType())) {
         fmt = getDefaultFormat(Tool.replace(var, SumDataSet.SUM_HEADER_PREFIX, ""));
      }

      if(fmt == null && GraphTypes.isBoxplot(info.getRTChartType())) {
         fmt = getDefaultFormat(BoxDataSet.getBaseName(var));
      }

      return fmt;
   }

   /**
    * Get the default format.
    */
   protected Format getDefaultFormat(String ...cols) {
      return GraphFormatUtil.getDefaultFormat(data, info, desc, cols);
   }

   /**
    * Get color frame for pareto chart.
    * @param names the measure name.
    * @return a color frame.
    */
   private StaticColorFrame getParetoColorFrame(String[] names, boolean all) {
      Color c = null;

      if(names.length > 0) {
         ColorFrame pcolor = all ? acolor : getColorFrame(null);

         try {
            if(pcolor instanceof ColorValueColorFrame) {
               c = desc.getPlotDescriptor().getParetoLineColor();
            }
            else if(pcolor != null && (isBrusingTarget() || isBrushingSource())) {
               c = pcolor.getColor(data, names[0], 0);
            }
            else if(pcolor instanceof StaticColorFrame) {
               c = ((StaticColorFrame) pcolor).getColor();
            }
            else if(pcolor instanceof MultiMeasureColorFrame) {
               c = pcolor.getColor(names[0]);
            }
         }
         catch(Exception ex) {
            // ignore it for there might be no data
         }
      }

      if(c == null) {
         c = desc.getPlotDescriptor().getParetoLineColor();
      }

      return new StaticColorFrame(c);
   }

   /**
    * Set hint for data set ranges.
    */
   private void setRangeHint(GraphElement elem, int chartType, boolean all) {
      DataSet vsdata = data;

      if(data instanceof TopDataSet) {
         vsdata = ((TopDataSet) data).getDataSet();
      }

      int allStart = 0;

      if(adata != null && data instanceof BrushDataSet) {
         BrushDataSet bset = (BrushDataSet) data;
         int from;
         int end;

         if(all) {
            from = bset.getDataSet(false).getRowCount();
            end = bset.getRowCount();
            allStart = from;
         }
         else {
            from = 0;
            end = bset.getDataSet(false).getRowCount();
         }

         vsdata = bset.getDataSet();

         if(elem instanceof LineElement) {
            elem.setHint("line.data.from", from);
            elem.setHint("line.data.to", end);
         }
      }

      // mixed binding, set the range of rows to use in each element to avoid
      // mixing values from other measure
      if(info.getRTFieldGroups().size() > 1) {
         GraphColumns subcols = getSubCols(elem);
         int[] rowRange = null;

         if(vsdata != null && !all) {
            rowRange = VSDataSet.getSubRange(vsdata, subcols);

            if(rowRange != null && rowRange.length == 2) {
               elem.setStartRow(rowRange[0]);
               elem.setEndRow(rowRange[1]);

               // this is needed in AbstractDataSet.getDataProjectedRowDateFromMySubs (46936).
               if(vsdata instanceof VSDataSet) {
                  ((VSDataSet) vsdata).addSubDataSet(new SubDataSet(vsdata, rowRange[0], rowRange[1]));
               }
            }
         }
         else if(adata != null && all) {
            // set brushed range too for _all_. (59409, 59426, 60601)
            rowRange = VSDataSet.getSubRange(adata, subcols.getBaseColumns());

            // only include data in ALL dataset. (60586)
            if(rowRange != null) {
               rowRange = new int[] { rowRange[0] + allStart, rowRange[1] + allStart };
               elem.setStartRow(rowRange[0]);
               elem.setEndRow(rowRange[1]);
            }
         }

         // pareto ranges are accummulative. (59234)
         if(rowRange != null && !GraphTypes.isPareto(chartType)) {
            for(String v : elem.getVars()) {
               Scale scale = scales.get(v);

               // set the rows for each measure for mixed stacking chart (some measures are
               // stacked and others aren't). (59174, 59426)
               if(scale instanceof LinearScale && ((LinearScale) scale).getScaleRange() != null) {
                  ((LinearScale) scale).getScaleRange().setMeasureRange(v, rowRange[0], rowRange[1]);

                  if(isSparkline()) {
                     ((LinearScale) scale).getScaleRange()
                        .setMeasureRange(SparklinePoint.PREFIX + v, rowRange[0], rowRange[1]);
                  }
               }
            }
         }

         if(adata instanceof VSDataSet) {
            DataSet data2 = adata;
            int[] range = ((VSDataSet) adata).getSubRange(subcols);

            if(range != null && (range[0] != 0 || range[1] >= 0)) {
               if(adata instanceof BrushDataSet) {
                  data2 = ((BrushDataSet) adata).getDataSet(true);
               }

               data2 = new SubDataSet(data2, range[0], range[1]);
               elem.setVisualDataSet(data2);
            }
         }
      }
   }

   /**
    * Get all the columns used in this element.
    */
   private GraphColumns getSubCols(GraphElement elem) {
      GraphColumns subcols = new GraphColumns();
      List<String> aggrs = Arrays.asList(elem.getVars());

      subcols.addAll(Arrays.asList(elem.getDims()));
      subcols.addAll(aggrs);

      for(VisualFrame frame : elem.getVisualFrames()) {
         if(frame instanceof MultiplexFrame) {
            for(String field : ((MultiplexFrame<?>) frame).getFields()) {
               subcols.add(field);
            }
         }
         else if(frame != null && frame.getField() != null) {
            subcols.add(frame.getField());
         }
      }

      // @by larryl, this shouldn't be necessary
      // group fields are not in element and needs to be added separately
      for(ChartRef ref : info.getGroupFields()) {
         if(isDimensionRef(ref)) {
            subcols.add(ref.getFullName());
         }
      }

      GraphColumns baseCols = subcols.getBaseColumns();

      // find the top-n specific for this element to ensure we get the
      // correct subset. another element may have the same dim/aggr without the top-n
      for(ChartRef ref : info.getBindingRefs(true)) {
         if(ref instanceof ChartDimensionRef) {
            subcols.setDimensionInfo((ChartDimensionRef) ref);
            continue;
         }

         // also check base columns for brushing. (59409)
         if(!subcols.contains(ref.getFullName()) && !baseCols.contains(ref.getFullName())) {
            continue;
         }

         ChartAggregateRef aggr = (ChartAggregateRef) ref;
         DataRef[] arefs = {
            aggr.getRTColorField(),
            aggr.getRTShapeField(),
            aggr.getRTSizeField(),
            aggr.getRTTextField()};

         for(DataRef aref : arefs) {
            if(aref instanceof ChartDimensionRef) {
               subcols.setDimensionInfo((ChartDimensionRef) aref);
            }
         }
      }

      ChartRef ref = info.getPathField();

      if(isDimensionRef(ref)) {
         subcols.add(ref.getFullName());
      }

      return subcols;
   }

   /**
    * Create the graph element for corresponding chart type, add to graph.
    * @param chartType the chart style.
    * @param names the variable names.
    * @param xname the xvariable name.
    */
   private List<GraphElement> createElement0(int chartType, String[] names, String xname,
                                             boolean all, List<GraphElement> alls)
   {
      List<GraphElement> elements = new ArrayList<>();
      boolean showPoint = false;

      if(GraphTypes.isPoint(chartType)) {
         if(GraphTypeUtil.isHeatMap(info)) {
            PolygonElement poly = new PolygonElement();

            poly.setShapeFrame(new StaticShapeFrame(GShape.FILLED_SQUARE));
            poly.setSizeFrame(null);
            elements.add(poly);
         }
         else {
            PointElement point = new PointElement();
            LineElement line = null;

            if(desc.getPlotDescriptor().isPointLine() &&
               !GraphTypeUtil.isWordCloud(info) &&
               // map only supports pointLine if path is bound
               !(info instanceof MapInfo))
            {
               line = new LineElement();
               showPoint = true;

               line.setFillLineStyle(desc.getPlotDescriptor().isFillGapWithDash()
                                        ? GraphConstants.DASH_LINE : 0);
               line.setHint("_show_point_", "true");
               line.setStackGroup(!desc.getPlotDescriptor().isOneLine());
               elements.add(line);

               // make sure the point is on top and not seen through to line
               point.setHint(GraphElement.HINT_ALPHA, 1);
               point.setHint(GraphElement.HINT_SHINE, "false");
            }
            // optimization, not need to sort scatter plot.
            // if point on map, more important to make the smaller point on top of larger one
            // since ordering on a map doesn't matter.
            else if(GraphTypeUtil.isScatterMatrix(info) || GraphTypes.isGeo(info.getChartType())) {
               point.setSortData(false);
            }

            if(GraphTypes.isStack(chartType)) {
               point.setStackGroup(true);
               // stacking of dot plot is not done by value
               point.setStackValue(!fake);

               if(line != null) {
                  line.setCollisionModifier(GraphElement.MOVE_STACK);
                  point.setCollisionModifier(GraphElement.MOVE_NONE);
               }
            }

            elements.add(point);
         }
      }
      else if(GraphTypes.isPolygon(chartType)) {
         PolygonElement poly = new PolygonElement();
         Color bcolor = GraphUtil.getMapBorderColor();

         // @by ankitmathur, For feature1413298224057, add a new property
         // "map.border.color" to allow the Administrator to specify the color
         // of the border for the Map.
         if(bcolor != null) {
            poly.setBorderColor(bcolor);
         }

         poly.setHint(GraphElement.HINT_CLIP, "true");
         elements.add(poly);
      }
      else if(GraphTypes.isArea(chartType)) {
         AreaElement area = new AreaElement();
         area.setStackGroup(true);

         if(chartType == CHART_STEP_AREA || chartType == CHART_STEP_AREA_STACK) {
            area.setType(LineElement.Type.STEP);
         }

         elements.add(area);
      }
      else if(GraphTypes.isLine(chartType)) {
         LineElement line = new LineElement();
         ChartRef path = info.getRTPathField();

         if(chartType == CHART_STEP || chartType == CHART_STEP_STACK) {
            line.setType(LineElement.Type.STEP);
         }
         else if(chartType == CHART_JUMP) {
            line.setType(LineElement.Type.JUMP);
         }

         line.setFillLineStyle(desc.getPlotDescriptor().isFillGapWithDash()
                                  ? GraphConstants.DASH_LINE : 0);
         line.setStackGroup(true);

         if(path != null && info.supportsPathField()) {
            line.setSortFields(path.getFullName());
         }

         elements.add(line);

         if(isSparkline() && !all) {
            PointElement lastp = new PointElement();

            lastp.setShapeFrame(new StaticShapeFrame(GShape.FILLED_CIRCLE));
            lastp.setSizeFrame(new StaticSizeFrame(0));
            lastp.setHint("_sparkline_point_", "true");

            if(getColorFrame(line) instanceof CategoricalColorFrame) {
               // use red dot as default
               lastp.setColorFrame(new StaticColorFrame(SOFT_RED));
            }
            else {
               // use the highlight/linear color as line
               lastp.setColorFrame(getColorFrame(line));
            }

            if(GraphTypes.isStack(chartType)) {
               lastp.setCollisionModifier(desc.getPlotDescriptor().isPointLine() ?
                  GraphElement.MOVE_NONE : GraphElement.STACK_SYMMETRIC);
               lastp.setStackGroup(true);
               lastp.setStackValue(true);
            }

            elements.add(lastp);
         }

         if(desc.getPlotDescriptor().isPointLine() || dateComparison != null) {
            PointElement point = new PointElement();
            showPoint = true;

            if(path != null && info.supportsPathField()) {
               point.setSortFields(path.getFullName());
            }

            // for date comparison, line uses same color as bar and will 'disappear' into
            // the bar. add point so the change value is visible.
            if(dateComparison != null) {
               point.setShapeFrame(new StaticShapeFrame(GShape.FILLED_CIRCLE));
               point.setSizeFrame(new StaticSizeFrame(1));
            }

            point.setShapeFrame(new StaticShapeFrame(GShape.FILLED_CIRCLE));
            point.setHint("_show_point_", "true");
            // make sure the point is on top and not seen through to line
            point.setHint(GraphElement.HINT_ALPHA, 1);
            point.setHint(GraphElement.HINT_SHINE, "false");

            // stacking and path not supported by point. (52248)
            if(GraphTypes.isStack(chartType) && info.getRTPathField() == null) {
               point.setCollisionModifier(GraphElement.MOVE_NONE);
               point.setStackGroup(true);
               point.setStackValue(true);
            }

            elements.add(point);
         }
      }
      else if(GraphTypes.isPareto(chartType)) {
         LineElement line = new LineElement();
         IntervalElement bar = new IntervalElement();

         line.setHint("editable", "false");
         line.setHint("_pareto_", "true");
         line.setCollisionModifier(GraphElement.STACK_SYMMETRIC);
         line.setColorFrame(getParetoColorFrame(names, all));
         line.setStackNegative(false);
         line.setStackGroup(false);
         bar.setCollisionModifier(GraphElement.STACK_SYMMETRIC);
         bar.setStackGroup(true);

         elements.add(bar);
         elements.add(line);
      }
      else if(GraphTypes.isStock(chartType)) {
         elements.add(new SchemaElement(new StockPainter()));
      }
      else if(GraphTypes.isCandle(chartType)) {
         elements.add(new SchemaElement(new CandlePainter()));
      }
      else if(GraphTypes.isBoxplot(chartType)) {
         PointElement outlier = new PointElement();
         outlier.setSizeFrame(new StaticSizeFrame(1));

         elements.add(new SchemaElement(new BoxPainter()));
         elements.add(outlier);
      }
      else if(GraphTypes.isWaterfall(chartType)) {
         IntervalElement bar = new IntervalElement();
         IntervalElement sumBar = new IntervalElement();

         bar.setCollisionModifier(GraphElement.STACK_SYMMETRIC);
         bar.setStackNegative(false);
         bar.setStackGroup(false);
         boolean stacked = Arrays.stream(info.getAestheticRefs(true))
            .anyMatch(a -> a != null && a.getDataRef() instanceof ChartDimensionRef);

         bar.setVisualStacked(stacked);
         sumBar.setHint("editable", "false");
         sumBar.setHint("_waterfall_", "true");
         elements.add(bar);
         elements.add(sumBar);
      }
      else if(GraphTypes.isRadar(chartType)) {
         LineElement elem;

         if(chartType == GraphTypes.CHART_RADAR) {
            elem = new LineElement();
            elem.setClosed(true);
            elem.setFillLineStyle(desc.getPlotDescriptor().isFillGapWithDash()
                                     ? GraphConstants.DASH_LINE : 0);

            if(desc.getPlotDescriptor().isPointLine()) {
               PointElement point = new PointElement();
               point.setHint("_show_point_", "true");
               // make sure the point is on top and not seen through to line
               point.setHint(GraphElement.HINT_ALPHA, 1);
               point.setHint(GraphElement.HINT_SHINE, "false");

               elements.add(point);
            }
         }
         else {
            elem = new AreaElement();
            elem.setClosed(true);
         }

         if(GraphTypes.isRadarOne(info)) {
            elem.setStackGroup(true);
            elem.setLineGroup(LineElement.LineGroup.AESTHETIC);
         }

         elements.add(elem);
      }
      else if(GraphTypes.isTreemap(chartType)) {
         TreemapElement elem = new TreemapElement();

         switch(chartType) {
         case CHART_SUNBURST:
            elem.setMapType(TreemapElement.Type.SUNBURST);
            break;
         case CHART_TREEMAP:
            elem.setMapType(TreemapElement.Type.TREEMAP);
            break;
         case CHART_CIRCLE_PACKING:
            elem.setMapType(TreemapElement.Type.CIRCLE);

            Color borderColor = desc.getPlotDescriptor().getBorderColor();

            if(!GDefaults.DEFAULT_LINE_COLOR.equals(borderColor) && borderColor != null) {
               elem.setBorderColor(borderColor);
            }

            break;
         case CHART_ICICLE:
            elem.setMapType(TreemapElement.Type.ICICLE);
            break;
         }

         elements.add(elem);
      }
      else if(GraphTypes.isRelation(chartType)) {
         RelationElement elem = new RelationElement();
         elem.setNodeWidth(5);
         elem.setNodeHeight(5);
         elem.setLabelPlacement(GraphConstants.TOP);

         switch(chartType) {
         case CHART_TREE:
            elem.setAlgorithm(RelationElement.Algorithm.COMPACT_TREE);
            elem.setNodeWidth(40);
            elem.setNodeHeight(30);
            elem.setLabelPlacement(GraphConstants.CENTER);
            break;
         case CHART_NETWORK:
            elem.setAlgorithm(RelationElement.Algorithm.ORGANIC);
           break;
         case CHART_CIRCULAR:
            elem.setAlgorithm(RelationElement.Algorithm.CIRCLE);
            break;
         }

         elem.setWidthRatio(info.getUnitWidthRatio());
         elem.setHeightRatio(info.getUnitHeightRatio());
         elements.add(elem);
      }
      else if(GraphTypes.isMekko(chartType)) {
         elements.add(new MekkoElement());
      }
      else if(GraphTypes.isGantt(chartType)) {
         IntervalElement elem = new IntervalElement();
         elem.setLabelPlacement(GraphConstants.CENTER);
         elements.add(elem);

         if(((GanttChartInfo) info).getRTMilestoneField() instanceof ChartAggregateRef) {
            ChartAggregateRef milestone =
               (ChartAggregateRef) ((GanttChartInfo) info).getRTMilestoneField();
            ShapeFrame frame = milestone.getShapeFrame();

            if(frame == null) {
               frame = new StaticShapeFrame(GShape.FILLED_DIAMOND);
            }

            PointElement point = new PointElement();
            point.setShapeFrame(frame);
            elements.add(point);
         }
      }
      else if(fake && !GraphTypes.isBar(chartType) && !GraphTypes.isPie(chartType)) {
         elements.add(new PointElement());
      }
      else if(GraphTypes.isFunnel(chartType)) {
         IntervalElement elem = new IntervalElement();
         elem.setCollisionModifier(GraphElement.MOVE_MIDDLE | GraphElement.MOVE_CENTER |
                                   GraphElement.MOVE_STACK);
         elem.setLabelPlacement(GraphConstants.CENTER);
         elem.setStackGroup(true);
         elements.add(elem);
      }
      else if(GraphTypes.isInterval(chartType)) {
         IntervalElement elem = new IntervalElement();
         elem.setZeroHeight(1);
         elements.add(elem);
      }
      else if(GraphTypes.isScatteredContour(chartType)) {
         elements.add(new PointElement());
      }
      else {
         IntervalElement element = new IntervalElement();

         if(!GraphTypes.isStack(chartType)) {
            element.setStackNegative(false);
         }

         elements.add(element);
      }

      for(int i = 0; i < elements.size(); i++) {
         GraphElement elem = elements.get(i);

         initElement(elem, chartType, names, xname, all);
         setRangeHint(elem, chartType, all);

         if(i == 0 && !all) {
            if(GraphTypes.isWaterfall(chartType)) {
               fixWaterfallData(elem, names[0]);
            }
            else if(GraphTypes.isBoxplot(chartType)) {
               fixBoxplotData(elem, names);
            }
            else if(GraphTypes.isInterval(chartType)) {
               fixIntervalData(elem, names);
            }
         }

         if(GraphTypes.isPie(chartType)) {
            elem.setCollisionModifier(GraphElement.MOVE_STACK);
         }
         else if(GraphTypes.isStack(chartType) &&
            !GraphTypes.isFunnel(chartType) &&
            // stacking and path not supported by point. (52248)
            (info.getRTPathField() == null ||
             // path is not used other than point/line so it should not be applied. (59441)
               !GraphTypes.isPoint(chartType) && !GraphTypes.isLine(chartType)) &&
            elem.getHint("_sparkline_point_") == null &&
            // show point stack setting handled in creation logic already. (52519)
            !(showPoint && elem instanceof PointElement))
         {
            // if as one line, don't stack values. otherwise the values will accumulate like
            // a running total. this will only draw line across one point on each x tick. (62520)
            if(showPoint && desc.getPlotDescriptor().isOneLine() && elem instanceof LineElement) {
               elem.setCollisionModifier(GraphElement.MOVE_NONE);
            }
            else {
               elem.setCollisionModifier(GraphElement.STACK_SYMMETRIC);
            }
         }

         // for data with brushing, editable is false
         if(all) {
            elem.setHint("editable", "false");
         }
         // for data without brushing, overlay stores the target element
         else if(adata != null && alls != null && i < alls.size()) {
            elem.setHint("overlay", alls.get(i));
            alls.get(i).setHint("overlaid", elem);
         }
      }

      AestheticRef sref = info.getSizeField();

      if(sref != null && !sref.isEmpty()) {
         SizeFrame sframe = (SizeFrame) sref.getVisualFrame();
         sframe = sframe == null ? info.getSizeFrame() : sframe;
         fixSizeFrame(sframe, chartType, null);
      }

      return elements;
   }

   /**
    * Get the chart type when not separated.
    */
   protected int getChartType() {
      if(info.isMultiStyles()) {
         throw new MessageException(Catalog.getCatalog().getString(
               "em.common.graph.invalidCaller"), LogLevel.WARN, true,
                                    ConfirmException.INFO);
      }

      return info.getRTChartType();
   }

   // @by ChrisSpagnoli bug1431601287124 2015-5-21
   /**
    * Returns flag if given chart field is a "stacked" chart
    */
   private boolean isStackChart(String field) {
      int chartType = GraphTypeUtil.getChartType(field, info);
      return(chartType == GraphTypes.CHART_BAR_STACK ||
             chartType == GraphTypes.CHART_3D_BAR_STACK ||
             chartType == GraphTypes.CHART_LINE_STACK ||
             chartType == GraphTypes.CHART_AREA_STACK ||
             chartType == CHART_STEP_STACK ||
             chartType == GraphTypes.CHART_STEP_AREA_STACK ||
             chartType == GraphTypes.CHART_POINT_STACK);
   }

   /**
    * Create the graph coordinate use inner coord.
    */
   protected Coordinate createCoord(Coordinate coord) {
      return createCoord(new Coordinate[] {coord}, -1, true);
   }

   /**
    * Create the graph coordinate use inner coord.
    */
   protected Coordinate createCoord(Coordinate coord, int chartType) {
      return createCoord(new Coordinate[] {coord}, chartType, true);
   }

   /**
    * Create the graph coordinate use inner coord.
    */
   protected Coordinate createCoord(Coordinate[] coords, int chartType, boolean vertical) {
      PlotDescriptor plotdesc = desc.getPlotDescriptor();
      Coordinate coordinate;

      if(coords.length > 1) {
         if(vertical) {
            Coordinate[] coords2 = new Coordinate[coords.length];

            for(int i = 0; i < coords2.length; i++) {
               coords2[i] = coords[coords.length - i - 1];
            }

            coords = coords2;
         }

         FacetCoord facet = new FacetCoord(null, coords, vertical);
         coordinate = facet;
         facet.setFacetGrid(plotdesc.isFacetGrid());
         facet.setGridColor(plotdesc.getFacetGridColor());
      }
      else if(coords.length == 1) {
         coordinate = coords[0];
      }
      else {
         throw new MessageException(Catalog.getCatalog().getString(
            "em.common.graph.cooNotEmpty"), LogLevel.WARN, true,
                                    ConfirmException.INFO);
      }

      ArrayList<String> xfields = new ArrayList<>(xdims);
      ArrayList<String> yfields = new ArrayList<>(ydims);

      if(xfields.size() > 0 && isXConsumed(chartType)) {
         xfields.remove(xfields.size() - 1);
      }

      if(yfields.size() > 0 && isYConsumed(chartType)) {
         yfields.remove(yfields.size() - 1);
      }

      int xlength = xfields.size() - 1;
      int ylength = yfields.size() - 1;
      RectCoord innermostCoord = null;
      List<Scale> xscales = new ArrayList<>();
      List<Scale> yscales = new ArrayList<>();

      // xscales/yscales are used to find the inner most axis line style/colors in
      // setAxisGridStyle(), and it should use the inner coord if exists. (57765)
      extractScales(coordinate, xscales, yscales);

      while(xlength >= 0 || ylength >= 0) {
         Scale xscale = xlength < 0 ? null : new CategoricalScale(xfields.get(xlength));
         Scale yscale = ylength < 0 ? null : new CategoricalScale(yfields.get(ylength));

         // create scales here so script could access them and change spec
         if(xscale == null) {
            xscale = GTool.createFakeScale(yscale);
         }

         if(yscale == null) {
            yscale = GTool.createFakeScale(xscale);
         }

         if(isGantt(chartType)) {
            if(yscale instanceof CategoricalScale) {
               ((CategoricalScale) yscale).setReversed(true);
            }
         }

         RectCoord outer = new RectCoord(xscale, yscale);
         boolean created = false;

         ((CategoricalScale) xscale).setProjection(false);
         ((CategoricalScale) yscale).setProjection(false);
         // facet uses unweighted index for overall layout. (53949)
         ((CategoricalScale) xscale).setWeightedScale(false);
         ((CategoricalScale) yscale).setWeightedScale(false);
         fixCoordProperties(outer, 0);

         if(innermostCoord == null) {
            innermostCoord = outer;

            if(GraphTypes.isFunnel(info.getRTChartType())) {
               setFunnelAxisColor(outer, coords);
            }
         }

         // for multi-measure, a facet is created without outer.
         // we should reuse it instead of creating a new facet
         if(coordinate instanceof FacetCoord) {
            FacetCoord fcoord = (FacetCoord) coordinate;

            if(fcoord.getOuterCoordinate() == null) {
               fcoord.setOuterCoordinate(outer);
               created = true;
            }
         }

         if(coordinate == null) {
            // coordinate is null if map has no geo ref
            coordinate = outer;
         }
         else if(!created) {
            FacetCoord facet = new FacetCoord(outer, coordinate);
            facet.setFacetGrid(plotdesc.isFacetGrid());
            facet.setGridColor(plotdesc.getFacetGridColor());
            coordinate = facet;
         }

         Scale prevX = xscales.isEmpty() ? null : xscales.get(xscales.size() - 1);
         Scale prevY = yscales.isEmpty() ? null : yscales.get(yscales.size() - 1);
         xscales.add(xscale);
         yscales.add(yscale);

         setAxisGridStyle(coords, innermostCoord, xscale, "y", xscales, yscales, plotdesc, prevX);
         setAxisGridStyle(coords, innermostCoord, yscale, "x", yscales, xscales, plotdesc, prevY);

         xlength--;
         ylength--;
      }

      if(coordinate instanceof FacetCoord) {
         FacetCoord facet = (FacetCoord) coordinate;

         // create outer coord for script to access
         if(facet.getOuterCoordinate() == null) {
            RectCoord outer = new RectCoord(GTool.createFakeScale(null),
                                            GTool.createFakeScale(null));
            facet.setOuterCoordinate(outer);
         }
      }

      info.setFacet(coordinate instanceof FacetCoord);
      return coordinate;
   }

   // extract scales from coordinate.
   private void extractScales(Coordinate coord, List<Scale> xscales, List<Scale> yscales) {
      // can't use getAxesAt() since at this point axes haven't been created yet.

      if(coord instanceof FacetCoord) {
         TileCoord tile = ((FacetCoord) coord).getProtoInnerCoord();
         Coordinate[] inners = tile.getCoordinates();

         if(((FacetCoord) coord).isVertical()) {
            extractScales(inners[inners.length - 1], xscales, yscales);
         }
         else {
            extractScales(inners[0], xscales, yscales);
         }

         extractScales(((FacetCoord) coord).getOuterCoordinate(), xscales, yscales);
      }
      else if(coord instanceof RectCoord) {
         Scale xscale = ((RectCoord) coord).getXScale();
         Scale yscale = ((RectCoord) coord).getYScale();
         boolean inverted = GTool.getRotation(coord.getCoordTransform()) != 0;

         if(xscale != null) {
            if(inverted) {
               yscales.add(xscale);
            }
            else {
               xscales.add(xscale);
            }
         }

         if(yscale != null) {
            if(inverted) {
               xscales.add(yscale);
            }
            else {
               yscales.add(yscale);
            }
         }
      }
   }

   // copy the outer top axis line color to (hidden) y axis (at bottom). since the
   // labels are always hidden on the y axis so a user can't se tthe axis color,
   // so we apply the top axis color instead.
   private void setFunnelAxisColor(RectCoord outer, Coordinate[] coords) {
      for(Coordinate coord : coords) {
         if(coord instanceof RectCoord) {
            Scale linear = ((RectCoord) coord).getYScale();
            Scale top = ((RectCoord) coord).getXScale();
            Scale topFacetAxis = outer.getXScale();

            if(linear != null) {
               if(top != null) {
                  linear.getAxisSpec().setLineColor(top.getAxisSpec().getLineColor());
                  linear.getAxisSpec().setLineVisible(top.getAxisSpec().isLineVisible());
               }

               if(topFacetAxis != null && topFacetAxis.getFields().length > 0 &&
                  topFacetAxis.getAxisSpec().isLineVisible())
               {
                  linear.getAxisSpec().setLine2Color(topFacetAxis.getAxisSpec().getLineColor());
               }
            }
         }
      }
   }

   // set the facet grid line style/color.
   private void setAxisGridStyle(Coordinate[] coords, RectCoord innermostCoord, Scale scale,
                                 String otherAxis, List<Scale> scales, List<Scale> otherScales,
                                 PlotDescriptor plotdesc, Scale prevScale)
   {
      if(scale != null) {
         // table grid, apply facet grid color to all facet grids since axis is hidden
         // with sparkline and axis line color can't be set.
         // FacetGrid uses axis line color if grid color is not set.
         if(plotdesc.isFacetGrid() && innermostCoord != null && isSparkline()) {
            scale.getAxisSpec().setGridColor(plotdesc.getFacetGridColor());
            scale.getAxisSpec().setGridStyle(StyleConstants.THIN_LINE);
         }
         else if(!isSparkline()) {
            boolean otherLineVisible = isAxisLineVisible(innermostCoord, coords, otherAxis);
            boolean thisLineVisible = isAxisLineVisible(innermostCoord, coords,
                                                        "x".equals(otherAxis) ? "y" : "x");
            ChartRef[] otherFields = "x".equals(otherAxis) ? info.getXFields() : info.getYFields();
            int chartType = info.getRTChartType();
            boolean hasOtherAxis = Arrays.stream(otherFields)
               .filter(a -> !GraphTypes.isRadar(chartType) && !GraphTypes.isPie(chartType) ||
                  isDimensionRef(a))
               .count() > 0;

            // a fake scale may be created when the number of dimensions on x/y of a facet
            // is not balanced. should the real axis line color. (57687)
            if(prevScale != null && isHiddenAxis(scale)) {
               scale.getAxisSpec().setLineColor(prevScale.getAxisSpec().getLineColor());
            }

            // for y (horizontal) grid, show grid line if any of the following is true:
            // 1. any x axis line is visible.
            // 2. there is no x axis binding (so user can't control the axis line visibility),
            //    AND y axis line is visible (so user can turn off all grid lines by hiding
            //    y axis line).
            // vice versa for x grid.

            if(otherLineVisible || thisLineVisible && !hasOtherAxis) {
               scale.getAxisSpec().setGridStyle(StyleConstants.THIN_LINE);
               Color otherLineColor = getLineColor(otherScales);

               if(otherLineVisible) {
                  scale.getAxisSpec().setGridColor(otherLineColor);
               }

               // if this is a fake scale (no binding), users can't control the line color.
               // so if showing line, should use the other axis's line color so it responds
               // to user changes and looks nicer. (57647)
               if(isHiddenAxis(scale) && otherLineColor != null && prevScale == null) {
                  scale.getAxisSpec().setLineColor(otherLineColor);
               }
            }
            else {
               scale.getAxisSpec().setGridStyle(GraphConstants.NONE);
            }
         }

         scale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_DOUBLE);
      }
   }

   private boolean isHiddenAxis(Scale scale) {
      return scale instanceof CategoricalScale && scale.getFields().length == 0 ||
         GraphTypes.isFunnel(info.getRTChartType()) && scale instanceof LinearScale;
   }

   // Get the first visible axis line color. The scales are ordered from the inner-most
   // to outer-most in the facet.
   private Color getLineColor(List<Scale> scales) {
      return scales.stream().filter(s -> s.getAxisSpec().isLineVisible())
         .map(s -> s.getAxisSpec().getLineColor()).findFirst().orElse(null);
   }

   /**
    * Check if the inner axis line is visible.
    */
   private static boolean isAxisLineVisible(Coordinate outer, Coordinate[] coords, String type) {
      for(Coordinate coord : coords) {
         if(coord instanceof RectCoord) {
            if(isAxisLineVisible((RectCoord) coord, type)) {
               return true;
            }
         }
      }

      if(outer instanceof RectCoord) {
         return isAxisLineVisible((RectCoord) outer, type);
      }

      // don't hide facet grid if the axis doesn't exist to control the border
      return true;
   }

   private static boolean isAxisLineVisible(RectCoord rect, String type) {
      return type.equals("x") && isAxisLineVisible(rect.getScaleAt(Coordinate.BOTTOM_AXIS)) ||
         type.equals("y") && isAxisLineVisible(rect.getScaleAt(Coordinate.LEFT_AXIS));
   }

   private static boolean isAxisLineVisible(Scale scale) {
      if(scale == null || scale.getFields().length == 0) {
         return false;
      }

      return scale.getAxisSpec().isLineVisible();
   }

   /**
    * Create a scale.
    */
   protected Scale createScale(AxisDescriptor axis, DataSet data, String[] aflds,
                               ChartAggregateRef[] arefs)
   {
      Scale scale = axis.isLogarithmicScale() ?
         new LogScale(aflds) : createMeasureScale(data, aflds, arefs);

      if(scale instanceof LinearScale) {
         ((LinearScale) scale).setReversed(axis.isReversed());
         ((LinearScale) scale).setSharedRange(axis.isSharedRange());
      }

      return scale;
   }

   /**
    * Get the ChartRef for specified field name.
    * @param fullname true to compare the full name of the chart ref.
    * @param refs if not null, look for the ref on the list. Otherwise look at
    * x and y refs.
    */
   protected ChartRef getChartRef(String fld, boolean fullname, ChartRef[] refs) {
      ChartRef[][] xyrefs;

      if(refs != null) {
         xyrefs = new ChartRef[][] {refs};
      }
      else {
         ChartRef[] yrefs = info.getRTYFields();
         ChartRef[] xrefs = info.getRTXFields();
         xyrefs = new ChartRef[][] {xrefs, yrefs};
      }

      for(int i = 0; i < xyrefs.length; i++) {
         ChartRef[] refs0 = xyrefs[i];

         for(int j = 0; j < refs0.length; j++) {
            if(fullname && GraphUtil.equalsName(refs0[j], fld) ||
               !fullname && fld.equals(refs0[j].getName()))
            {
               return refs0[j];
            }
         }
      }

      return null;
   }

   /**
    * Check if x dimension was consumed.
    * @param chartType the chart style.
    */
   protected boolean isXConsumed(int chartType) {
      if(ymeasures.size() == 0) {
         return false;
      }

      if(GraphTypes.isPolar(chartType) || GraphTypes.isMap(chartType)) {
         return false;
      }

      if(xmeasures.size() > 0) {
         return false;
      }

      return xdims.size() > 0;
   }

   /**
    * Check if y dimension was consumed.
    * @param chartType the chart style.
    */
   protected boolean isYConsumed(int chartType) {
      if(xmeasures.size() == 0) {
         return false;
      }

      if(GraphTypes.isPolar(chartType) || GraphTypes.isMap(chartType)) {
         return false;
      }

      if(ymeasures.size() > 0) {
         return false;
      }

      return ydims.size() > 0;
   }

   /**
    * Init graph element properties.
    * @param elem, the graph element need to add dimensions.
    * @param chartType the chart style.
    * @param names the variable field names.
    * @param xname the xvariable name.
    * @param all true if data without brushing, false otherwise.
    */
   private void initElement(GraphElement elem, int chartType, String[] names,
                            String xname, boolean all)
   {
      if(fake) {
         elem.setHint("_fake_", "true");
      }

      if(elem instanceof PointElement && GraphTypes.isContour(info.getChartType())) {
         // if there is only one row, no contour will be drawn. we just draw a circle to
         // represent it. (52290)
         if(data.getRowCount() == 1) {
            elem.setShapeFrame(new StaticShapeFrame(GShape.FILLED_CIRCLE));
            elem.setSizeFrame(new StaticSizeFrame(1));
         }
         else {
            elem.setShapeFrame(new StaticShapeFrame(GShape.NIL));
         }

         ((PointElement) elem).setContour(true);
      }

      int chartType2 = chartType;
      elem.setAutoTextColor(elem instanceof TreemapElement || elem instanceof RelationElement);

      for(int i = 0; i < names.length; i++) {
         if(GraphTypes.isWaterfall(chartType) && "true".equals(elem.getHint("_waterfall_"))) {
            String var = SumDataSet.SUM_HEADER_PREFIX + names[i];
            Scale scale = scales.get(names[i]);

            elem.addVar(var);

            // for waterfall, we should re-init scale properly
            if(scale instanceof LinearScale) {
               String[] flds = scale.getFields();
               String[] nflds = new String[flds.length + 1];

               System.arraycopy(flds, 0, nflds, 0, flds.length);
               nflds[flds.length] = var;
               scale.setFields(nflds);

               for(int j = 0; j < flds.length; j++) {
                  scales.put(flds[j], scale);
               }

               scales.put(var, scale);
            }

            ColorFrame color = (ColorFrame) cvisitor.getSummaryFrame(names[i]);

            if(color != null && !isBrushingSource() && adata == null &&
               elem.getColorFrame() == null)
            {
               elem.setColorFrame(color);
            }
            // for brush source summary, always apply gray color
            else if(isBrushingSource()) {
               StaticColorFrame frame2 = new StaticColorFrame();
               frame2.setUserColor(brushDimColor);
               elem.setColorFrame(frame2);
            }

            TextureFrame texture = (TextureFrame) tvisitor.getSummaryFrame(names[i]);

            if(texture != null) {
               elem.setTextureFrame(texture);
            }

            StaticSizeFrame sizeFrame = new StaticSizeFrame();
            SizeFrame size0 = szstrategy.getFrame(names[i]);
            double sumSize = size0 instanceof StaticSizeFrame
               ? ((StaticSizeFrame) size0).getSize() : 30;
            sizeFrame.setSize(sumSize, CompositeValue.Type.DEFAULT);
            elem.setSizeFrame(sizeFrame);
         }
         else if("true".equals(elem.getHint("_sparkline_point_"))) {
            CalcColumn pcol = new SparklinePoint(names[i]);

            elem.addVar(pcol.getHeader());
            data.addCalcColumn(pcol);

            // don't fill with 0 for the sparkline point. (58941)
            for(CalcRow calcRow : data.getCalcRows()) {
               if(calcRow instanceof TimeSeriesRow) {
                  ((TimeSeriesRow) calcRow).setMissingValue(pcol.getHeader(), null);
               }
            }

            Scale scale = scales.get(names[i]);
            String[] flds = scale.getFields();
            String[] nflds = new String[flds.length + 1];

            System.arraycopy(flds, 0, nflds, 0, flds.length);
            nflds[flds.length] = pcol.getHeader();
            scale.setFields(nflds);
            scale.setDataFields(nflds);
         }
         else if(GraphTypes.isRadar(chartType)) {
            if(ymeasures.size() > 1) {
               elem.addDim(names[i]);
            }
            else {
               elem.addVar(names[i]);
            }
         }
         else if(GraphTypes.isTreemap(chartType)) {
            TreemapElement treemap = (TreemapElement) elem;
            treemap.addTreeDim(names[i]);

            if(info.getChartType() == CHART_CIRCLE_PACKING) {
               for(int k = 0; k < info.getRTGroupFields().length + 1; k++) {
                  CompositeTextFormat fmt = desc.getPlotDescriptor().getCircleFormat(k);
                  treemap.setBackground(k, fmt.getBackgroundWithAlpha());
               }
            }
         }
         else if(GraphTypes.isRelation(chartType)) {
            if(i == 0) {
               ((RelationElement) elem).setSourceDim(names[i]);
            }
            else {
               ((RelationElement) elem).setTargetDim(names[i]);
            }
         }
         else if(GraphTypes.isGantt((chartType))) {
            // [start, end, milestone]
            if(elem instanceof IntervalElement) {
               chartType2 = CHART_BAR;

               if(i < 2) {
                  if(elem.getVarCount() > 0) {
                     ((IntervalElement) elem).setBaseVar(names[i]);
                  }
                  else {
                     elem.addVar(names[i]);
                  }
               }
            }
            else if(elem instanceof PointElement && i >= 2) {
               chartType2 = CHART_POINT;
               elem.addVar(names[i]);
            }
         }
         else if(GraphTypes.isMekko(chartType)) {
            elem.addVar(names[i]);
         }
         else if(GraphTypes.isBoxplot(chartType)) {
            for(String name : names) {
               if(elem instanceof SchemaElement) {
                  ((SchemaElement) elem).addSchema(BoxDataSet.MAX_PREFIX + name,
                                                   BoxDataSet.Q75_PREFIX + name,
                                                   BoxDataSet.MEDIUM_PREFIX + name,
                                                   BoxDataSet.Q25_PREFIX + name,
                                                   BoxDataSet.MIN_PREFIX + name);
               }
               else {
                  elem.addVar(name);
               }
            }
         }
         else if(GraphTypes.isSchema(chartType)) {
            ((SchemaElement) elem).addSchema(names);
            break;
         }
         else if(GraphTypes.isInterval(chartType) && elem instanceof IntervalElement) {
            String topCol = IntervalDataSet.getTopColumn(names[i]);
            Scale scale = scales.get(names[i]);
            String[] fields = ArrayUtils.add(scale.getFields(), topCol);
            scale.setFields(fields);
            scale.setDataFields(fields);
            ((IntervalElement) elem).addInterval(names[i], topCol);
         }
         else {
            elem.addVar(names[i]);
         }
      }

      // set frames
      if(GraphTypeUtil.supportsFrame(info, chartType2, getColorFrame(elem), elem,
                                     desc.getPlotDescriptor()) && elem.getColorFrame() == null)
      {
         if(all) {
            // if this is multi-styles, the acolor contained the global
            // color frame as the base. we need to get the per measure
            // color frame for base since the global one is meaningless.
            // this is needed to maintain the categorical field in the
            // color frame so the LineElement can find the field for
            // group fields to separate the data for lines.
            if(info.isMultiAesthetic() && acolor instanceof CompositeColorFrame) {
               CompositeColorFrame cframe = (CompositeColorFrame) acolor;
               ColorFrame color0 = getColorFrame(elem);

               if(color0 != null) {
                  while(cframe.getFrameCount() > 1) {
                     cframe.removeFrame(1);
                  }

                  cframe.addFrame(color0);
               }
            }

            elem.setColorFrame(acolor);
            acolor.setBrightness(1);
         }
         else {
            elem.setColorFrame(getColorFrame(elem));
            elem.getColorFrame().setBrightness(1);
         }

         // sparkline bar hardcoded to use red for negative color
         if(isSparkline() && elem instanceof IntervalElement &&
            elem.getColorFrame() instanceof CategoricalColorFrame)
         {
            CategoricalColorFrame c1 = (CategoricalColorFrame) elem.getColorFrame();
            Object[] fields = c1.getScale().getFields();

            if(fields.length == 0) {
               StaticColorFrame c2 = new StaticColorFrame(c1.getColor(0));

               c2.setField(elem.getVar(0));
               c2.setNegativeColor(SOFT_RED);
               elem.setColorFrame(c2);
            }
         }

         switch(chartType2) {
         case GraphTypes.CHART_LINE:
         case GraphTypes.CHART_LINE_STACK:
         case CHART_STEP:
         case CHART_JUMP:
         case GraphTypes.CHART_POINT:
         case GraphTypes.CHART_POINT_STACK:
         case GraphTypes.CHART_RADAR:
         case GraphTypes.CHART_CANDLE:
         case GraphTypes.CHART_STOCK:
         case GraphTypes.CHART_BOXPLOT:
            ColorFrame frame = getColorFrame(elem);

            // darken color for line and points so they stand out more
            if(!info.isColorChanged(elem.getVars()) && !GraphTypeUtil.isHeatMap(info) && dateComparison == null) {
               // multi element should not share a darken color frame
               ColorFrame color0 = (ColorFrame) (all ? acolor : frame).clone();
               setColorFrameBrightness(elem, frame, color0);
               elem.setColorFrame(color0);
            }
         }
      }
      else if(elem instanceof PolygonElement && elem.getColorFrame() == null) {
         elem.setColorFrame(new StaticColorFrame(new Color(242, 239, 233)));
      }

      if(GraphTypeUtil.supportsFrame(info, chartType2, getShapeFrame(elem), elem,
                                     desc.getPlotDescriptor()) &&
         (elem.getShapeFrame() == null || GraphTypes.isGantt(chartType)))
      {
         fixShapeFrame(getShapeFrame(elem), elem);
      }

      if(GraphTypeUtil.supportsFrame(info, chartType2, getSizeFrame(null), elem,
                                     desc.getPlotDescriptor()) &&
         !GraphTypeUtil.isHeatMap(info) && elem.getSizeFrame() == null)
      {
         SizeFrame sizes = (SizeFrame) fixSizeFrame(getSizeFrame(elem), chartType, elem);

         // if overlaying point on line, don't make it too big
         if(elem.getHint("_show_point_") != null && elem instanceof PointElement) {
            if(sizes instanceof StaticSizeFrame) {
               // don't make point too large. (53042)
               double nsize = Math.max(0, sizes.getSize(null) - 2);
               sizes = new StaticSizeFrame((int) nsize);
            }
            else if(sizes instanceof StackedMeasuresSizeFrame) {
               SizeFrame defaultSizes = ((StackedMeasuresSizeFrame) sizes).getDefaultFrame();

               if(defaultSizes instanceof CategoricalSizeFrame) {
                  CategoricalSizeFrame csizes = (CategoricalSizeFrame) defaultSizes;

                  for(Object key : csizes.getStaticValues()) {
                     double size = csizes.getSize(key);
                     csizes.setSize(key, Math.max(1, size - 2));
                  }
               }
            }
         }

         if(GraphTypes.isTreemap(chartType) || GraphTypes.isMekko(chartType)) {
            // for treemap and mekko, size range doesn't make sense. force to use value range.
            if(sizes instanceof LinearSizeFrame) {
               sizes = new TreemapSizeFrame(sizes.getField());
            }
         }

         if((GraphTypes.isTreemap(chartType) || GraphTypes.isRelation(chartType)) && all &&
            (sizes instanceof LinearSizeFrame || sizes instanceof CategoricalSizeFrame))
         {
            String allfield = BrushDataSet.ALL_HEADER_PREFIX + sizes.getField();

            if(data != null && data.indexOfHeader(allfield) >= 0) {
               sizes = (SizeFrame) sizes.clone();
               sizes.setField(allfield);
            }
         }

         elem.setSizeFrame(sizes);
      }

      // for interval, set a shape with border if the size is max so the bars
      // can still be looked apart
      if(elem instanceof IntervalElement) {
         SizeFrame size2 = elem.getSizeFrame();

         if(size2 instanceof StaticSizeFrame) {
            boolean outline = !desc.isApplyEffect() &&
               !GraphTypes.isPie(chartType) &&
               !GraphTypes.isFunnel(chartType) &&
               size2.getSize(null) == size2.getLargest();
            GShape barshape = GShape.SQUARE.create(outline, true);

            elem.setShapeFrame(new StaticShapeFrame(barshape));

            // fill the middle circle with label
            if(GraphTypes.isDonut(info)) {
               if(size2.getSize(null) < size2.getLargest()) {
                  elem.setLabelPlacement(GraphConstants.CENTER_FILL);
               }
               // don't place outer label to the middle. (57561)
               else {
                  elem.setLabelPlacement(GraphConstants.TOP);
               }
            }
         }
      }

      if(elem instanceof PointElement && fake) {
         if(isStackedGraph()) {
            elem.setCollisionModifier(GraphElement.MOVE_STACK);
         }
         // only a single dim, dot plot.
         else if(GraphTypeUtil.isDotPlot(getChartInfo())) {
            elem.setCollisionModifier(GraphElement.MOVE_STACK, false);
         }
         // if no measure, it's a heatmap where point is created at intersection of two dimension
         // shouldn't move it
         else {
            elem.setCollisionModifier(GraphElement.MOVE_NONE);
         }
      }

      // outliers of boxplot should not be moved around
      if(elem instanceof PointElement && GraphTypes.isBoxplot(chartType)) {
         elem.setCollisionModifier(GraphElement.MOVE_NONE);
      }

      if(GraphTypeUtil.supportsFrame(chartType2, getTextureFrame(elem), desc.getPlotDescriptor()) &&
         elem.getTextureFrame() == null)
      {
         TextureFrame textureFrame = getTextureFrame(elem);

         elem.setTextureFrame(textureFrame);

         if(elem instanceof TreemapElement && (textureFrame instanceof CategoricalTextureFrame ||
            textureFrame instanceof LinearTextureFrame))
         {
            elem.setAutoTextColor(false);
         }
      }

      if(GraphTypeUtil.supportsFrame(chartType2, getLineFrame(elem), desc.getPlotDescriptor()) &&
         elem.getLineFrame() == null)
      {
         elem.setLineFrame(getLineFrame(elem));
      }

      // ignore text if it's the base of brushed data, only show on brushed data
      if(GraphTypeUtil.supportsFrame(info, chartType2, getTextFrame(elem), elem,
                                     desc.getPlotDescriptor()) &&
         // don't show middle text in donut pie when brushed
         (adata == null || !GraphTypes.isPie(info.getChartType()) ||
          elem.getVarCount() > 0 && ymeasures.indexOf(elem.getVar(0)) < 1) &&
         // tree overlay draws highlight in-place so should apply the text to _all_. (52347)
         (!all || elem instanceof RelationElement) &&
         elem.getTextFrame() == null)
      {
         if(!"true".equals(elem.getHint("_pareto_")) &&
            !"true".equals(elem.getHint("_sparkline_point_")) &&
            // map contour labels are drawn on PointElement if it exists
            // (see MapGenerator.createEGraph0).
            !(info.getChartType() == GraphTypes.CHART_MAP_CONTOUR &&
               elem instanceof PolygonElement && !GraphUtil.containsMapPoint(info)) &&
            !"true".equals(elem.getHint("_show_point_")))
         {
            elem.setTextFrame(getTextFrame(elem, all));
         }
      }

      if(elem instanceof TreemapElement && elem.getTextFrame() == null) {
         DataSet data = this.adata != null ? this.adata : this.data;
         CurrentTextFrame textFrame = new CurrentTextFrame(((TreemapElement) elem).getTreeDims());

         if(data instanceof VSDataSet && data.getRowCount() > 0) {
            for(ChartRef ref : info.getRTGroupFields()) {
               String name = ref.getFullName();
               textFrame.setFormat(name, ((VSDataSet) data).getFormat(name, 0));
            }
         }

         elem.setTextFrame(textFrame);
      }

      initElementDimension(elem, chartType, xname, all);

      if(GraphTypes.isStack(chartType2)) {
         if(elem instanceof IntervalElement) {
            ((IntervalElement) elem).setStackGroup(true);
         }

         fixStackElementScale(elem);
      }

      List<String> tiprefs = new ArrayList<>();

      if(info.supportsGroupFields() && info.getGroupFieldCount() > 0) {
         // add tooltip
         ChartRef[] grefs = info.getRTGroupFields();

         if(grefs != null) {
            for(ChartRef ref : grefs) {
               tiprefs.add(ref.getFullName());
            }
         }

         List<String> gdims = new ArrayList<>();

         for(ChartRef ref : grefs) {
            if(GraphUtil.isDimension(ref)) {
               gdims.add(ref.getFullName());
            }
         }

         // add border around each piece otherwise it's not distinguishable
         if(elem instanceof IntervalElement) {
            if(elem.getLineFrame() == null && gdims.size() > 0) {
               elem.setLineFrame(new StaticLineFrame(new GLine(1)));
            }
         }
         // make sure the lines are broken-up by group fields
         else if(elem instanceof LineElement) {
            ((LineElement) elem).setGroupFields(gdims.toArray(new String[0]));
         }
         else if(elem instanceof MekkoElement && gdims.size() > 0) {
            ((MekkoElement) elem).setInnerDimension(gdims.get(0));
         }
      }

      // if text frame is removed to only show the column as tooltip, we should
      // still include the column in the tips
      TextFrame txtframe = getTextFrame(elem);

      if(txtframe != null) {
         tiprefs.add(txtframe.getField());
      }

      String[] paths = {};

      if(elem instanceof LineElement || elem instanceof PointElement) {
         paths = elem.getSortFields();
      }

      String[] tipcols = new String[tiprefs.size() + paths.length];

      for(int i = 0; i < tiprefs.size(); i++) {
         tipcols[i] = tiprefs.get(i);
      }

      System.arraycopy(paths, 0, tipcols, tiprefs.size(), paths.length);
      elem.setHint("_tip_columns_", tipcols);

      if(elem instanceof PointElement && voComparator != null) {
         elem.setComparator(voComparator);
      }

      initElementTextID(elem, chartType);
      graph.addElement(elem);
   }

   private void setColorFrameBrightness(GraphElement elem, ColorFrame frame, ColorFrame color0) {
      boolean hasColorValueColorFrame = frame instanceof ColorValueColorFrame ||
         frame instanceof CompositeVisualFrame &&
            ((CompositeVisualFrame) frame).getFrames(ColorValueColorFrame.class)
               .findAny().isPresent();

      // color frames used by elements can be shared so we need to set the
      // brightness individually so bar color legend won't be changed because
      // there is a line element on the chart. (52863)
      if(color0 instanceof MultiMeasureColorFrame && !GraphTypeUtil.isScatterMatrix(info)) {
         ShapeFrame shapes = getShapeFrame(elem);
         boolean imageShape = shapes.getField() == null &&
            shapes.getShape(elem.getVar(0)) instanceof GShape.ImageShape ||
            shapes.getField() != null && shapes.getValues() != null &&
               Arrays.stream(shapes.getValues())
                  .anyMatch(v -> shapes.getShape(v) instanceof GShape.ImageShape);

         if(!imageShape) {
            ((MultiMeasureColorFrame) color0).setBrightness(elem.getVar(0), 0.75);
         }
      }
      else if(!hasColorValueColorFrame) {
         color0.setBrightness(0.75);
      }
   }

   /**
    * Init graph element text.
    * @param elem, the graph element need to add dimensions.
    */
   private void initElementTextID(GraphElement elem, int chartType) {
      Map<String, String> localMap = ((AbstractChartInfo) info).getLocalMap();
      localMap = localMap == null ? new HashMap<>() : localMap;
      VisualFrame color = elem.getColorFrame();
      VisualFrame shape = GraphTypeUtil.supportsFrame(chartType,
                                                      getShapeFrame(elem), desc.getPlotDescriptor()) ? elem.getShapeFrame() :
         elem.getTextureFrame();
      VisualFrame size = elem.getSizeFrame();
      String colorTitle = localMap.get("Color Legend Axis Title");
      String shapeTitle = localMap.get("Shape Legend Axis Title");
      String sizeTitle = localMap.get("Size Legend Axis Title");

      if(color != null && colorTitle != null) {
         if(Tool.localizeTextID(colorTitle) != null) {
            color.getLegendSpec().setTitle(Tool.localize(colorTitle));
         }
      }

      if(shape != null && shapeTitle != null) {
         if(Tool.localizeTextID(shapeTitle) != null) {
            shape.getLegendSpec().setTitle(Tool.localize(shapeTitle));
         }
      }

      if(size != null && sizeTitle != null) {
         if(Tool.localizeTextID(sizeTitle) != null) {
            size.getLegendSpec().setTitle(Tool.localize(sizeTitle));
         }
      }
   }

   /**
    * Fix the size frame.
    * @param frame the legend frame need to init size value.
    * @param chartType the specified chart type.
    * @param elem the specified graph element that the frame set for, if the
    * frame is original size frame, the elem will be null.
    */
   private VisualFrame fixSizeFrame(VisualFrame frame, int chartType, GraphElement elem) {
      if(frame == null) {
         return frame;
      }

      if(GraphTypes.isInterval(chartType) && frame instanceof LinearSizeFrame) {
         ((LinearSizeFrame) frame).setSmallest(((LinearSizeFrame) frame).getLargest() - 1);
         return frame;
      }

      if(info.isSizeChanged((elem != null) ? elem.getVars() : new String[0])) {
         return frame;
      }

      SizeFrame sframe = (SizeFrame) frame;
      ShapeFrame shape = elem == null ? null : elem.getShapeFrame();
      AestheticRef sref = info.getSizeField();

      if(sref != null) {
         SizeFrame size = (SizeFrame) sref.getVisualFrame();

         if(size != null) {
            fixSizeFrameValues(elem, chartType, shape, size);
         }
      }

      fixSizeFrameValues(elem, chartType, shape, sframe);
      fixAggregatesSizeFrame(elem, chartType);

      return sframe;
   }

   /**
    * Fix size frame of the aggregates of the chart.
    */
   private void fixAggregatesSizeFrame(GraphElement elem, int globalChartType) {
      ChartRef[] xrefs = info.getXFields();
      ChartRef[] yrefs = info.getYFields();

      for(int i = 0; i < xrefs.length; i++) {
         if(xrefs[i] instanceof VSChartAggregateRef) {
            fixAggregateSizeFrame((VSChartAggregateRef) xrefs[i],
                                  elem, globalChartType);
         }
      }

      for(int i = 0; i < yrefs.length; i++) {
         if(yrefs[i] instanceof VSChartAggregateRef) {
            fixAggregateSizeFrame((VSChartAggregateRef) yrefs[i],
                                  elem, globalChartType);
         }
      }
   }

   /**
    * Fix size frame of a aggregate of the chart.
    */
   private void fixAggregateSizeFrame(VSChartAggregateRef agg,
                                      GraphElement elem, int globalChartType)
   {
      if(agg == null) {
         return;
      }

      if(agg.isSizeChanged()) {
         return;
      }

      AestheticRef sref = agg.getSizeField();
      SizeFrame frame = agg.getSizeFrame();
      boolean isStatic = true;

      if(sref != null && !sref.isEmpty()) {
         frame = (SizeFrame) sref.getVisualFrame();
         isStatic = frame == null;
         frame = isStatic ? agg.getSizeFrame() : frame;
      }

      int chartType = info.isMultiStyles() ? agg.getRTChartType() : globalChartType;
      ShapeFrame shape = agg.getShapeFrame();
      fixSizeFrameValues(elem, chartType, shape, frame);
   }

   /**
    * Fix the size frame's values such as size, largest, smallest.
    */
   private void fixSizeFrameValues(GraphElement elem, int chartType,
      ShapeFrame shape, SizeFrame frame)
   {
      double nsize = 1;
      double min = 1;
      double max = 30;

      switch(chartType) {
      case GraphTypes.CHART_BAR:
      case GraphTypes.CHART_BAR_STACK:
      case GraphTypes.CHART_3D_BAR:
      case GraphTypes.CHART_3D_BAR_STACK:
      case GraphTypes.CHART_WATERFALL:
      case GraphTypes.CHART_INTERVAL:
         // bar defaults to 1/2 of the max size
         nsize = 15;
         min = 10;
         max = 25;
         break;
      case GraphTypes.CHART_DONUT:
         nsize = 15;
         break;
      case CHART_SUNBURST:
      case CHART_TREEMAP:
      case CHART_CIRCLE_PACKING:
      case CHART_ICICLE:
         nsize = 30;
         break;
      case GraphTypes.CHART_PIE:
      case GraphTypes.CHART_3D_PIE:
         // pie defaults to the max size
         nsize = 30;
         break;
      case GraphTypes.CHART_PARETO:
         // bar defaults to 1/2 of the max size
         if(elem == null || elem instanceof IntervalElement) {
            nsize = 15;
            min = 10;
            max = 25;
         }

         break;
      case GraphTypes.CHART_LINE:
      case GraphTypes.CHART_LINE_STACK:
      case GraphTypes.CHART_STEP:
      case GraphTypes.CHART_STEP_STACK:
      case GraphTypes.CHART_JUMP:
         // line defaults to thin line
         nsize = isSparkline() ? 1 : 3;
         max = 15;
         break;
      case GraphTypes.CHART_POINT:
      case GraphTypes.CHART_POINT_STACK:
         if(GraphTypes.isContour(info.getChartType())) {
            nsize = 15;
         }
         else if(shape != null) {
            nsize = getDefaultPointSize(shape);
         }

         max = 15;
         break;
      case GraphTypes.CHART_BOXPLOT:
         nsize = 5;
         break;
      case CHART_GANTT:
         nsize = elem instanceof IntervalElement ? 20 : 9;
         break;
      case GraphTypes.CHART_FUNNEL:
         nsize = 30;
         break;
      case GraphTypes.CHART_SCATTER_CONTOUR:
      case GraphTypes.CHART_MAP_CONTOUR:
         nsize = 15;
         break;
      default:
         max = 15;
         break;
      }

      boolean fake = ChangeChartProcessor.isFake(info);

      // force to point type.
      // don't change size for other (e.g. bar) chart types. (56382)
      if(fake && (chartType == GraphTypes.CHART_AUTO || GraphTypes.isPoint(chartType))) {
         nsize = getDefaultPointSize(shape);
         min = 1;
         max = 15;
      }

      // for static size frame we should not set smallest and largest,
      // for users could not change the two values
      if(frame instanceof StaticSizeFrame) {
         ((StaticSizeFrame) frame).setSize(nsize, CompositeValue.Type.DEFAULT);

         if(elem != null) {
            SizeFrame frame0 = getSizeFrame(origInfo, elem);

            if(frame0 instanceof StaticSizeFrame) {
               ((StaticSizeFrame) frame0).setSize(nsize, CompositeValue.Type.DEFAULT);
            }
         }
      }
      // stacked measure is similar to StaticSizeFrame when no size binding exist. (50315)
      else if(!(frame instanceof StackedMeasuresSizeFrame) &&
      // interval smallest/largest is set in VSSizeFrameStrategy and shouldn't be changed (50454).
              !GraphTypes.isInterval(chartType))
      {
         frame.setSmallest(min);
         frame.setLargest(max);
      }
   }

   private static double getDefaultPointSize(ShapeFrame shape) {
      if(shape instanceof LinearShapeFrame ||
         // image shape defaults to 16 px
         shape instanceof StaticShapeFrame &&
         ((StaticShapeFrame) shape).getShape() instanceof GShape.ImageShape)
      {
         return 3;
      }

      return 1;
   }

   /**
    * Fix the shape frame for color and size.
    */
   protected void fixShapeFrame(ShapeFrame shape, GraphElement elem) {
      String[] vars = GraphTypeUtil.isScatterMatrix(info)
         ? Arrays.stream(info.getRTXFields()).map(f -> f.getFullName()).toArray(String[]::new)
         : elem.getVars();

      boolean colorChanged = info.isColorChanged(vars) ||
         elem.getColorFrame() instanceof LinearColorFrame ||
         elem.getColorFrame() instanceof CategoricalFrame &&
            // don't apply color if there is no binding (54618)
            elem.getColorFrame().getVisualField() != null;
      double alpha = desc.getPlotDescriptor().getAlpha();
      boolean alphaChanged = alpha >= 0 && alpha < 1;
      boolean applySize = info.isSizeChanged(vars) ||
         elem.getSizeFrame() instanceof LinearSizeFrame ||
         elem.getSizeFrame() instanceof CategoricalSizeFrame &&
            elem.getSizeFrame().getVisualField() != null;
      boolean applyColor = isApplyColor(elem, colorChanged, alphaChanged) || adata != null;

      // shape frame may be shared and changing applySize should be applied individually. (59288)
      // if this is a shape frame created in VSShapeFrameStrategy.createCombinedFrame(),
      // it has a shape for each measure and it should be shared (since each shape is modified
      // individually so sharing won't cause the change to impact each other). we check for
      // getField() to identify that case. (59518)
      if(shape instanceof CategoricalFrame && shape.getField() != null && (applyColor || applySize)) {
         shape = (ShapeFrame) shape.clone();
      }

      elem.setShapeFrame(shape);

      // if text is defined and the user didn't set any shape, show text
      // only in a table like display
      if(preferWordCloud(shape, elem)) {
         StaticShapeFrame frame = (StaticShapeFrame) shape;
         PointElement point = (PointElement) elem;
         frame.setShape(GShape.NIL);
         point.setFontScale(desc.getPlotDescriptor().getWordCloudFontScale());

         if(info.getRTTextField() instanceof ChartDimensionRef) {
            point.setWordCloud(true);
         }
      }
      else if(shape instanceof StackedMeasuresShapeFrame) {
         setShapeColorSize(elem, ((StackedMeasuresShapeFrame) shape).getDefaultFrame(),
                           colorChanged, alphaChanged, applyColor, applySize);
      }
      else {
         setShapeColorSize(elem, shape, colorChanged, alphaChanged, applyColor, applySize);
      }
   }

   private void setShapeColorSize(GraphElement elem, ShapeFrame shape, boolean colorChanged,
                                  boolean alphaChanged, boolean applyColor, boolean applySize)
   {
      if(shape instanceof StaticShapeFrame) {
         StaticShapeFrame frame = (StaticShapeFrame) shape;
         frame.setShape(fixShape(frame.getShape(), colorChanged, alphaChanged, applyColor,
                                 applySize, elem));
      }
      else if(shape instanceof CategoricalShapeFrame) {
         CategoricalShapeFrame frame = (CategoricalShapeFrame) shape;

         if(frame.getField() != null || elem.getVarCount() == 0 ||
            elem.getVar(0).equals(PairsDataSet.YMEASURE_VALUE))
         {
            // fix the shape in the palette
            for(int i = 0; i < frame.getShapeCount(); i++) {
               fixShapeAt(elem, colorChanged, alphaChanged, applySize, applyColor, frame, i);
            }

            // fix the per value shape
            for(Object val : frame.getValues()) {
               fixShapeFor(elem, colorChanged, alphaChanged, applySize, applyColor, frame, val);
            }
         }
         // multi-measure frame, should only apply to the shape for the measure. (59238)
         else {
            for(String v : elem.getVars()) {
               fixShapeForMeasure(elem, colorChanged, alphaChanged, applySize, applyColor, frame, v);
            }
         }
      }
   }

   // apply the shape setting for the measure.
   private void fixShapeForMeasure(GraphElement elem, boolean colorChanged, boolean alphaChanged,
                            boolean applySize, boolean applyColor, CategoricalShapeFrame frame,
                            Object val)
   {
      fixShapeFor(elem, colorChanged, alphaChanged, applySize, applyColor, frame, val);
      int idx = (int) frame.getScale().map(val);

      if(idx >= 0) {
         fixShapeAt(elem, colorChanged, alphaChanged, applySize, applyColor, frame, idx);
      }
   }

   // apply the shape setting for the value in categorical scale.
   private void fixShapeFor(GraphElement elem, boolean colorChanged, boolean alphaChanged,
                            boolean applySize, boolean applyColor, CategoricalShapeFrame frame,
                            Object val)
   {
      if(frame.isStatic(val)) {
         GShape shp = frame.getShape(val);

         shp = fixShape(shp, colorChanged, alphaChanged, applyColor, applySize, elem);
         frame.setShape(val, shp);
      }
   }

   // apply the shape setting for the shape at the specific idx.
   private void fixShapeAt(GraphElement elem, boolean colorChanged, boolean alphaChanged,
                           boolean applySize, boolean applyColor, CategoricalShapeFrame frame,
                           int idx)
   {
      GShape shp = frame.getShape(idx);

      shp = fixShape(shp, colorChanged, alphaChanged, applyColor, applySize, elem);
      frame.setShape(idx, shp);
   }

   private boolean isApplyColor(GraphElement elem, boolean colorChanged, boolean alphaChanged) {
      boolean applyColor = colorChanged || alphaChanged;

      // check for brushing/highlight
      if(!applyColor) {
         ColorFrame colors = elem.getColorFrame();

         if(colors instanceof MultiMeasureColorFrame) {
            // apply color to image icon if there are more than one color
            // (e.g. for multiple measure)
            applyColor = colors.getScale().getValues().length > 1 &&
               hasSharedImageShapeWithDifferentColors(info, (MultiMeasureColorFrame) colors);

            if(!applyColor) {
               if(info.isMultiAesthetic()) {
                  // or if color has changed.
                  applyColor = !colors.getColor(elem.getVar(0)).equals(CategoricalColorFrame.COLOR_PALETTE[0]);
               }
               else {
                  // must cast to MultiMeasureColorFrame to call getColor(int), otherwise it
                  // would call getColor(Object) instead.
                  MultiMeasureColorFrame mcolors = (MultiMeasureColorFrame) colors;
                  int measureIdx = (int) colors.getScale().map(elem.getVar(0));
                  // if not multi-style, the measures are assigned a color from the palette
                  // (on combined-color-pane) for their corresponding index, so we compare
                  // the color with the color at that index. (59286)
                  applyColor = !mcolors.getColor(measureIdx)
                     .equals(CategoricalColorFrame.COLOR_PALETTE[measureIdx]);
               }
            }
         }
         else if(colors instanceof CompositeColorFrame) {
            CompositeColorFrame cframe = (CompositeColorFrame) colors;

            for(int i = 0; i < cframe.getFrameCount(); i++) {
               ColorFrame frame = (ColorFrame) cframe.getFrame(i);

               if(frame instanceof HLColorFrame) {
                  applyColor = true;
                  break;
               }
            }
         }
      }
      return applyColor;
   }

   private boolean hasSharedImageShapeWithDifferentColors(ChartInfo info,
                                                          MultiMeasureColorFrame colors)
   {
      boolean multiColor = colors.getMeasures().stream().map(m -> colors.getColor(m))
         .collect(Collectors.toSet()).size() > 1;

      if(multiColor && info.isMultiStyles()) {
         List<ShapeFrame> shapes = Arrays.stream(info.getModelRefs(false))
            .filter(a -> a instanceof ChartAggregateRef)
            .map(a -> (ChartAggregateRef) a)
            .filter(a -> isPoint(a.getRTChartType()) && a.getRTShapeField() == null)
            .map(a -> a.getShapeFrame()).collect(Collectors.toList());

         int count = 0;
         Set images = new HashSet();

         for(ShapeFrame f : shapes) {
            if(f instanceof StaticShapeFrame &&
               ((StaticShapeFrame) f).getShape() instanceof GShape.ImageShape)
            {
               GShape.ImageShape img = (GShape.ImageShape) ((StaticShapeFrame) f).getShape();
               count++;
               images.add(img.getImageId());
            }
         }

         // if more than one images shape sharing the same image.
         return count > 1 && images.size() == 1;
      }

      return false;
   }

   private boolean preferWordCloud(ShapeFrame shape, GraphElement elem) {
      boolean xyMeasureMatch = xmeasures.size() == 0 && ymeasures.size() == 1 ||
                               ymeasures.size() == 0 && xmeasures.size() == 1;

      if(!xyMeasureMatch || !fake || !(elem instanceof PointElement) ||
         info.getRTTextField() == null || !(shape instanceof StaticShapeFrame))
      {
         return false;
      }

      DataRef sizeField = info.getRTSizeField();
      boolean noShape = shape.getShape(null) == GShape.NIL;

      return (sizeField != null || sizeField == null && (!info.isSizeChanged() || noShape)) ||
         (!info.isShapeChanged() || noShape);
   }

   /**
    * Fix image shape to not apply resize/color if it's not changed or bound.
    */
   private GShape fixShape(GShape shape, boolean colorChanged, boolean alphaChanged,
                           boolean applyColor, boolean applySize, GraphElement elem)
   {
      if(shape instanceof GShape.ImageShape) {
         GShape.ImageShape shape0 = (GShape.ImageShape) shape.clone();
         shape0.setApplyColor(applyColor);
         shape0.setApplySize(applySize);

         // if the color is not set, ignore the default only (and
         // apply highlight colors).
         // shouldn't ignore the default color if alpha is changed otherwise
         // the alpha will not be applied to not highlighted points
         if(!colorChanged && !alphaChanged) {
            ColorFrame colors = elem.getColorFrame();

            if(colors instanceof CompositeColorFrame) {
               CompositeColorFrame cframe = (CompositeColorFrame) colors;

               for(int i = 0; i < cframe.getFrameCount(); i++) {
                  VisualFrame frame = cframe.getFrame(i);

                  if(frame instanceof StaticColorFrame) {
                     shape0.setIgnoredColor(((StaticColorFrame) frame).getColor());
                     break;
                  }
                  else if(frame instanceof CategoricalColorFrame && frame.getField() == null) {
                     shape0.setIgnoredColor(((CategoricalColorFrame) frame).getColor(
                                               frame.getScale().getValues()[0]));
                     break;
                  }
               }
            }
         }

         return shape0;
      }

      return shape;
   }

   /**
    * Add graph element dimensions.
    * @param element, the graph element need to add dimensions.
    * @param chartType the chart style.
    * @param xname the xvariable name.
    */
   protected void initElementDimension(GraphElement element, int chartType,
                                       String xname, boolean all)
   {
      if(isXConsumed(chartType)) {
         for(int i = 0; i < xdims.size() - 1; i++) {
            element.addDim(xdims.get(i));
         }

         for(int i = 0; i < ydims.size(); i++) {
            element.addDim(ydims.get(i));
         }

         element.addDim(xdims.get(xdims.size() - 1));
      }
      else if(isYConsumed(chartType)) {
         for(int i = 0; i < ydims.size() - 1; i++) {
            element.addDim(ydims.get(i));
         }

         for(int i = 0; i < xdims.size(); i++) {
            element.addDim(xdims.get(i));
         }

         if(ydims.size() > 0) {
            element.addDim(ydims.get(ydims.size() - 1));
         }
      }
      else {
         for(int i = 0; i < xdims.size(); i++) {
            element.addDim(xdims.get(i));
         }

         for(int i = 0; i < ydims.size(); i++) {
            element.addDim(ydims.get(i));
         }

         if(GraphTypes.isRadarOne(info) && info.getRTGroupFields().length == 1) {
            element.addDim(info.getRTGroupFields()[0].getFullName());
         }
      }

      // add measure as dimension for point
      if((GraphTypes.isPoint(chartType) || GraphTypes.isLine(chartType) ||
          GraphTypes.isArea(chartType) || GraphTypes.isScatteredContour(chartType)) &&
         xmeasures.size() > 0 && ymeasures.size() > 0)
      {
         String xmeasure = xname == null ? xmeasures.get(0) : xname;
         String[] vars = element.getVars();
         boolean contained = false;

         // one measure to both x and y
         if(!ymeasures.contains(xmeasure)) {
            for(int i = 0; i < vars.length; i++) {
               if(vars[i].equals(xmeasure)) {
                  contained = true;
                  break;
               }
            }
         }

         if(!contained) {
            if(all) {
               xmeasure = BrushDataSet.ALL_HEADER_PREFIX + xmeasure;
            }

            element.addDim(xmeasure);
         }
      }
   }

   /**
    * Fix stack graph element.
    * @param element, the graph element need to be fix.
    */
   private void fixStackElementScale(GraphElement element) {
      if(element.getDimCount() > 0 && element.getVarCount() > 0) {
         String fld = element.getDim(element.getDimCount() - 1);
         String varfld = element.getVar(element.getVarCount() - 1);
         Object scale = scales.get(varfld);

         if(scale instanceof LinearScale) {
            ScaleRange range = ((LinearScale) scale).getScaleRange();

            // might be a brush range
            if(range instanceof BrushRange) {
               range = ((BrushRange) range).getScaleRange();
            }

            ((StackRange) range).setGroupField(fld);
         }
      }
   }

   /**
    * Fix the waterfall data.
    */
   private void fixWaterfallData(GraphElement bar, String measure) {
      int dcount = bar.getDimCount();
      String[] measures = new String[adata == null ? 1 : 2];

      measures[0] = measure;

      if(adata != null) {
         measures[1] = BrushDataSet.ALL_HEADER_PREFIX + measure;
      }

      int didx = dcount - 1;
      String field = didx < 0 ? null : bar.getDim(didx);
      TextFrame text = getTextFrame(bar);

      SumDataSet data = new SumDataSet(this.data, measures, field);
      this.data = data;

      for(int i = 0; i < bar.getDimCount(); i++) {
         if(bar.getDim(i).startsWith(ChartAggregateRef.DISCRETE_PREFIX)) {
            data.setDimension(bar.getDim(i), true);
         }
      }

      if(text == null) {
         return;
      }

      field = text.getField();

      if(field == null) {
         return;
      }

      field = Tool.replace(field, SumDataSet.ALL_HEADER_PREFIX, "");
      text.setField(SumDataSet.ALL_HEADER_PREFIX + field);
      data.addAllColumn(field);
   }

   private void fixBoxplotData(GraphElement bar, String[] measures) {
      String[] dims = new String[bar.getDimCount()];

      for(int i = 0; i < dims.length; i++) {
         dims[i] = bar.getDim(i);
      }

      BoxDataSet bdata = null;

      if(!(data instanceof BoxDataSet)) {
         bdata = new BoxDataSet(data, dims, measures);
         data = bdata;
      }
      else {
         bdata = (BoxDataSet) data;

         for(String measure : measures) {
            ((BoxDataSet) data).addVar(measure);
         }
      }

      if(bdata.getDataSet() instanceof BrushDataSet) {
         for(String measure : measures) {
            bdata.addVar(BrushDataSet.ALL_HEADER_PREFIX + measure);
         }
      }

      VisualFrame[] frames = {bar.getColorFrame(), bar.getShapeFrame(),
                              bar.getSizeFrame(), bar.getTextFrame(),
                              bar.getTextureFrame(), bar.getLineFrame()};

      for(VisualFrame frame : frames) {
         if(frame != null && frame.getField() != null) {
            if(bdata.indexOfHeader(BoxDataSet.MEDIUM_PREFIX + frame.getField()) >= 0) {
               String col = frame.getField();

               // if measure used in text frame, use the quantile values for text frame
               if(frame instanceof DefaultTextFrame) {
                  bar.setTextFrame(new MultiTextFrame(BoxDataSet.MAX_PREFIX + col,
                                                      BoxDataSet.Q75_PREFIX + col,
                                                      BoxDataSet.MEDIUM_PREFIX + col,
                                                      BoxDataSet.Q25_PREFIX + col,
                                                      BoxDataSet.MIN_PREFIX + col));
               }
               // if measure used in visual frame, use the medium values for visual frame
               else {
                  frame.setField(BoxDataSet.MEDIUM_PREFIX + col);
               }
            }
            else {
               bdata.addDim(frame.getField());
            }
         }
      }
   }

   private void fixIntervalData(GraphElement bar, String[] measures) {
      IntervalDataSet bdata = null;
      VisualFrame size = bar.getSizeFrame();
      String interval = size != null ? size.getField() : null;

      for(String measure : measures) {
         if(!(data instanceof IntervalDataSet)) {
            bdata = new IntervalDataSet(data);
            bdata.addInterval(measure, interval);

            this.data = bdata;
         }
         else {
            bdata = (IntervalDataSet) data;
            bdata.addInterval(measure, interval);
         }

         if(bdata.getDataSet() instanceof BrushDataSet) {
            String allMeasure = BrushDataSet.ALL_HEADER_PREFIX + measure;
            String allInterval = BrushDataSet.ALL_HEADER_PREFIX + interval;
            bdata.addInterval(allMeasure, allInterval);
         }
      }
   }

   /**
    * Create guides to implement target.
    */
   private void addTarget(GraphTarget target, int chartType, int index,
                          PlotDescriptor plotdesc)
   {
      boolean statistic = target.getStrategy() != null &&
         !(target.getStrategy().unwrap() == null ||
            target.getStrategy().unwrap() instanceof DynamicLineStrategy);

      // box plot doesn't support statistics
      if(GraphTypes.isBoxplot(chartType) && statistic) {
         return;
      }
      else if(GraphTypes.isGeo(chartType)) {
         return;
      }

      if(target.getField() != null &&
         !GraphUtil.getMeasuresName(info, true).contains(target.getField()))
      {
         return;
      }

      TargetForm gtf = new TargetForm();

      // never force the target to be in-plot if zoomed
      gtf.setInPlot(plotdesc.isInPlot() && !isZooming());
      TextFormat defaultFmt = target.getTextFormat().getDefaultFormat();
      defaultFmt.setFormat(new XFormatInfo(getDefaultFormat(target.getField())));
      target.initializeForm(gtf, data);
      gtf.setStacked(GraphTypes.isStack(chartType) || GraphTypes.isPareto(chartType));
      gtf.setXFieldTarget(xmeasures.contains(gtf.getField()) && !ymeasures.isEmpty() ||
         xdims.contains(gtf.getField()) || ydims.contains(gtf.getField()));

      // target not supported for stock measures
      if(GraphTypes.isStock(chartType) && ymeasures.contains(gtf.getField())) {
         return;
      }

      // don't support fill for 3D coord
      if(GraphTypeUtil.is3DBar(info)) {
         target.setFillAbove(null);
         target.setFillBelow(null);
         gtf.setFillAbove(null);
         gtf.setFillBelow(null);
         gtf.setInPlot(false); // min-max in 3d coord causes bar to shift
      }
      // target on scatter matrix causes shifting (49654)
      else if(GraphTypeUtil.isScatterMatrix(info)) {
         gtf.setInPlot(false);
      }

      // ignore targets added in script since they can't be changed
      // from the gui
      if(index < desc0.getTargetCount()) {
         gtf.setHint("target.index", index);
      }

      graph.addForm(gtf);
   }

   /**
    * Add trend line.
    */
   private void addTrendLine(PlotDescriptor plotdesc) {
      if(plotdesc.getTrendline() == GraphConstants.NONE) {
         return;
      }

      Map<Object, GraphElement> elemmap = new HashMap<>();

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         if("true".equals(elem.getHint("_pareto_"))) {
            continue;
         }

         for(String var : elem.getVars()) {
            elemmap.put(var, elem);
         }
      }

      Set added = new HashSet();
      Set excludedMeasures = plotdesc.getTrendLineExcludedMeasures();
      // in case measure is on both x and y, we plot the trend for y
      List<String> measures = ymeasures.size() > 0 ? ymeasures : xmeasures;

      // create one line per measure to support secondary axis
      for(String measure : measures) {
         if(added.contains(measure) || excludedMeasures.contains(measure)) {
            continue;
         }

         added.add(measure);
         addTrendLineForm(plotdesc, measure, elemmap.get(measure));
      }
   }

   /**
    * Add trend line form.
    */
   private void addTrendLineForm(PlotDescriptor plotdesc, String measure, GraphElement elem) {
      LineForm lineForm = new LineForm();
      lineForm.setLineEquation(getLineEquation(plotdesc.getTrendline()));
      lineForm.setLine(plotdesc.getTrendLineStyle());
      lineForm.setZIndex(GDefaults.TREND_LINE_Z_INDEX);
      lineForm.setInPlot(plotdesc.isInPlot());
      lineForm.setMeasure(measure);
      lineForm.setStackChart(isStackChart(measure));
      // @by ChrisSpagnoli bug1434331259187 2015-6-17
      lineForm.setHint(GraphElement.HINT_CLIP, "true");

      // lines are broken up by dimensions, we should draw one trend line
      // for each data line
      if(elem != null) {
         ColorFrame color = elem.getColorFrame();

         // ignore highlight frame
         if(color instanceof CompositeColorFrame) {
            CompositeColorFrame frame = (CompositeColorFrame) color;

            for(int i = 0; i < frame.getFrameCount(); i++) {
               if(frame.getFrame(i) instanceof CategoricalColorFrame) {
                  color = (ColorFrame) frame.getFrame(i);
                  break;
               }
            }
         }

         // only break into multiple lines by dimension
         if(plotdesc.isTrendPerColor() && color.getField() != null &&
            (color instanceof CategoricalColorFrame || color instanceof StackedMeasuresFrame))
         {
            lineForm.setGroupFields(color.getField());
         }

         lineForm.setColorFrame(color);

         LineFrame lineFrame = elem.getLineFrame();

         // NONE treated as AUTO (which uses the measure line style or 1) since specifying
         // trend line and then make it none is meaningless.
         if(lineForm.getLine() < 0) {
            GLine line = lineFrame != null ? lineFrame.getLine(measure) : null;
            lineForm.setLine(line != null ? line.getStyle() : 1);
         }
      }

      // if no color frame, use static color
      if(!plotdesc.isTrendPerColor() &&
         !Objects.equals(plotdesc.getTrendLineColor(), GDefaults.DEFAULT_TARGET_LINE_COLOR) ||
         lineForm.getColorFrame() == null)
      {
         lineForm.setColor(plotdesc.getTrendLineColor());
      }

      graph.addForm(lineForm);
   }

   /**
    * Add grid lines.
    */
   private void addGridLines(PlotDescriptor plotdesc) {
      if(GraphTypes.isMekko(info.getChartType())) {
         return;
      }

      if(plotdesc.getDiagonalStyle() != GraphConstants.NONE) {
         Object[] tuple1 = {Scale.MIN_VALUE, Scale.MIN_VALUE};
         Object[] tuple2 = {Scale.MAX_VALUE, Scale.MAX_VALUE};
         LineForm lineForm = new LineForm(tuple1, tuple2);
         lineForm.setLine(plotdesc.getDiagonalStyle());
         lineForm.setZIndex(GDefaults.GRIDLINE_Z_INDEX + 1);
         lineForm.setInPlot(false);

         if(plotdesc.getDiagonalColor() != null) {
            lineForm.setColor(plotdesc.getDiagonalColor());
         }

         graph.addForm(lineForm);
      }

      boolean xempty = xdims.size() == 0 && xmeasures.size() == 0;
      boolean yempty = ydims.size() == 0 && ymeasures.size() == 0;
      // don't add quadrant line if x or y is not bound
      boolean quandrant = !yempty && (!xempty || GraphTypes.isGantt(info.getChartType()));

      if(plotdesc.getQuadrantStyle() != GraphConstants.NONE && quandrant) {
         // mid value on a scale
         Scale.Value mid = new MidValue();
         // min value on a scale account of possible rescale
         Scale.Value min2 = new MinValue();
         // max value on a scale account of possible rescale
         Scale.Value max2 = new MaxValue();
         Object[] tuple1 = {mid, min2};
         Object[] tuple2 = {mid, max2};
         Object[] tuple3 = {min2, mid};
         Object[] tuple4 = {max2, mid};
         LineForm[] lines = {new LineForm(tuple1, tuple2), new LineForm(tuple3, tuple4)};

         for(LineForm lineForm : lines) {
            lineForm.setLine(plotdesc.getQuadrantStyle());
            lineForm.setZIndex(GDefaults.GRIDLINE_Z_INDEX + 1);
            lineForm.setHint(GraphElement.HINT_CLIP, "true");
            lineForm.setInPlot(false);

            if(plotdesc.getQuadrantColor() != null) {
               lineForm.setColor(plotdesc.getQuadrantColor());
            }

            graph.addForm(lineForm);
         }
      }
   }

   /**
    * Get line equation.
    */
   private LineEquation getLineEquation(int trendLine) {
      switch(trendLine) {
      case 1:
         return new PolynomialLineEquation.Linear();
      case 2:
         return new PolynomialLineEquation.Quadratic();
      case 3:
         return new PolynomialLineEquation.Cubic();
      case 4:
         return new ExponentialLineEquation();
      case 5:
         return new LogarithmicLineEquation();
      case 6:
         return new PowerLineEquation();
      default:
         throw new MessageException(Catalog.getCatalog().getString(
            "em.common.graph.invalidTrendLine", "" + trendLine),
                                    LogLevel.WARN, true, ConfirmException.INFO);
      }
   }

   /**
    * Get all the measures.
    */
   private HighlightRef[] getHighlightRefs() {
      if(GraphTypeUtil.isScatterMatrix(info)) {
         return new HighlightRef[0];
      }

      if(GraphTypeUtil.isWordCloud(info, true)) {
         AestheticRef text = info.getTextField();

         if(text != null && text.getDataRef() instanceof HighlightRef) {
            return new HighlightRef[] {(HighlightRef) text.getDataRef()};
         }
      }

      ChartRef[] rxrefs = info.getRTXFields();
      ChartRef[] ryrefs = info.getRTYFields();

      if(GraphTypes.isTreemap(info.getRTChartType())) {
         return getHighlightRefs(info.getRTGroupFields());
      }
      else if(GraphTypes.isRelation(info.getRTChartType())) {
         return getHighlightRefs(((RelationChartInfo) info).getRTTargetField(),
                                 ((RelationChartInfo) info).getRTSourceField());
      }
      else if(GraphTypes.isGantt(info.getChartType())) {
         return getHighlightRefs(((GanttChartInfo) info).getRTStartField(),
                                 ((GanttChartInfo) info).getRTEndField(),
                                 ((GanttChartInfo) info).getRTMilestoneField());
      }

      return getHighlightRefs(ArrayUtils.addAll(rxrefs, ryrefs));
   }

   /**
    * Get all the measures.
    */
   private HighlightRef[] getHighlightRefs(ChartRef... refs) {
      if(refs == null) {
         return new ChartAggregateRef[0];
      }

      List<HighlightRef> list = new ArrayList<>();

      for(int i = refs.length - 1; i >= 0; i--) {
         if(refs[i] instanceof HighlightRef &&
            ((HighlightRef) refs[i]).getHighlightGroup() != null)
         {
            list.add(0, (HighlightRef) refs[i]);
            continue;
         }

         if(refs[i] instanceof ChartAggregateRef && refs[i].isMeasure()) {
            list.add(0, (ChartAggregateRef) refs[i]);
         }
      }

      return list.toArray(new HighlightRef[0]);
   }

   /**
    * Check if support show value or not.
    */
   private boolean supportShowValue() {
      return !(this instanceof MergedGraphGenerator) || this instanceof RadarGraphGenerator;
   }

   /**
    * Get title spec.
    */
   private TitleSpec getTitleSpec(TitleDescriptor titleDesc, String type) {
      TitleSpec tSpec = new TitleSpec();
      String title = titleDesc.getTitle();
      int ctype = getFirstChartType();
      boolean xconsumed = isXConsumed(ctype);
      String label;
      boolean axis2 = !info.isSeparatedGraph() && GraphTypes.supportsSecondaryAxis(ctype);
      Map<String, String> localMap = ((AbstractChartInfo) info).getLocalMap();
      localMap = localMap == null ? new HashMap<>() : localMap;
      String textId = null;

      if(isSparkline()) {
         return tSpec;
      }

      List<String> labels = new ArrayList<>();

      if(type.equals("x")) {
         if(xdims.size() > 0 && xconsumed) {
            label = getRefLabel(xdims.get(xdims.size() - 1));
            label = xdimlabels.get(label) == null ? label : xdimlabels.get(label);

            if(!isHiddenTitleLabel(label)) {
               labels.add(label);
            }
         }

         for(int i = 0; i < xmeasures.size(); i++) {
            ChartAggregateRef agg = getAggregateRef(xmeasures.get(i));

            if(agg == null || agg.isSecondaryY() && axis2) {
               continue;
            }

            label = getRefLabel(xmeasures.get(i));

            if(!isHiddenTitleLabel(label)) {
               label = xmeasurelabels.get(label) == null ? label : xmeasurelabels.get(label);
               labels.add(label);
            }
         }

         textId = localMap.get("X Axis Title");
      }
      else if(type.equals("x2")) {
         if(xdims.size() > 1 || !xconsumed) {
            int last = !xconsumed ? xdims.size() : xdims.size() - 1;

            for(int i = 0; i < last; i++) {
               label = xdims.get(i);

               if(!isHiddenTitleLabel(label)) {
                  label = xdimlabels.get(label) == null ? label : xdimlabels.get(label);
                  labels.add(label);
               }
            }
         }

         if(axis2) {
            for(int i = xmeasures.size() - 1; i >= 0; i--) {
               ChartAggregateRef agg = getAggregateRef(xmeasures.get(i));

               if(agg == null || !agg.isSecondaryY()) {
                  continue;
               }

               label = getRefLabel(xmeasures.get(i));

               if(!isHiddenTitleLabel(label)) {
                  label = xmeasurelabels.get(label) == null ? label : xmeasurelabels.get(label);
                  labels.add(label);
               }
            }
         }

         textId = localMap.get("Secondary X Axis Title");
      }
      else if(type.equals("y")) {
         for(int i = 0; i < ydims.size(); i++) {
            label = ydims.get(i);

            if(!isHiddenTitleLabel(label)) {
               label = ydimlabels.get(label) == null ? label : ydimlabels.get(label);
               labels.add(label);
            }
         }

         for(int i = ymeasures.size() - 1; i >= 0; i--) {
            ChartAggregateRef agg = getAggregateRef(ymeasures.get(i));

            if(agg == null || agg.isSecondaryY() && axis2) {
               continue;
            }

            label = getRefLabel(ymeasures.get(i));

            if(!isHiddenTitleLabel(label)) {
               label = ymeasurelabels.get(label) == null ? label :
                  ymeasurelabels.get(label);
               labels.add(label);
            }
         }

         textId = localMap.get("Y Axis Title");
      }
      else if(type.equals("y2")) {
         // don't show y2 title if chart doesn't support y2 axis. (57552)
         if(!axis2) {
            return tSpec;
         }

         for(int i = ymeasures.size() - 1; i >= 0; i--) {
            ChartAggregateRef agg = getAggregateRef(ymeasures.get(i));

            if(agg == null || !agg.isSecondaryY()) {
               continue;
            }

            label = getRefLabel(ymeasures.get(i));
            label = ymeasurelabels.get(label) == null ? label : ymeasurelabels.get(label);
            labels.add(label);
         }

         textId = localMap.get("Secondary Y Axis Title");
      }

      boolean titleOK = labels.size() > 0;

      if((title != null && title.length() > 0) &&
         ((!maxMode && titleDesc.isVisible()) || (maxMode && titleDesc.isMaxModeVisible())))
      {
         boolean isfmt  = Pattern.matches(".*\\{[0-9]+\\}.*", title);

         if(isfmt && titleOK) {
            title = java.text.MessageFormat.format(title, labels.toArray());
         }

         tSpec.setLabel(title);

         CompositeTextFormat tfmt = titleDesc.getTextFormat();
         tSpec.setTextSpec(GraphUtil.getTextSpec(tfmt, null, null));
         return tSpec;
      }

      if(titleOK &&
         ((!maxMode && titleDesc.isVisible()) || (maxMode && titleDesc.isMaxModeVisible())))
      {
         StringBuilder buffer = new StringBuilder();

         for(int i = 0; i < labels.size(); i++) {
            if(buffer.length() > 0) {
               buffer.append(" / ");
            }

            buffer.append(labels.get(i));
         }

         String titleName = buffer.toString();
         tSpec.setLabel(Tool.localizeTextID(textId) == null ? titleName : Tool.localize(textId));
         CompositeTextFormat tfmt = titleDesc.getTextFormat();
         tSpec.setTextSpec(GraphUtil.getTextSpec(tfmt, null, null));
      }
      else {
         tSpec.setLabel(null);
      }

      tSpec.setLabelGap(titleDesc.getLabelGap());

      return tSpec;
   }

   /**
    * Check if the label should be hidden from title.
    */
   private boolean isHiddenTitleLabel(String label) {
      if(label != null) {
         switch(label) {
         case PairsDataSet.XMEASURE_NAME:
         case PairsDataSet.YMEASURE_NAME:
         case PairsDataSet.XMEASURE_VALUE:
         case PairsDataSet.YMEASURE_VALUE:
            return true;
         }
      }

      return false;
   }

   /**
    * Get displayed measure name in default title.
    */
   protected String getRefLabel(String measure) {
      ChartRef ref = info.getFieldByName(measure, true);
      return (ref != null) ? GraphUtil.getLabel(ref, info.isAggregated()) : measure;
   }

   /**
    * Get the data.
    */
   public DataSet getData() {
      return data;
   }

   /**
    * Get the normal data with brushing.
    */
   public DataSet getNormalData() {
      return odata;
   }

   /**
    * Get the data without brushing.
    */
   public DataSet getAllData() {
      return adata;
   }

   /**
    * Check if all charts are one of the types.
    */
   protected boolean isGraphType(int... types) {
      List<String> measures = new ArrayList<>(xmeasures);
      measures.addAll(ymeasures);

      // fake value
      if(measures.size() == 0) {
         measures.add("value");
      }

      for(int i = 0; i < measures.size(); i++) {
         String name = measures.get(i);
         int ctype = GraphTypeUtil.getChartType(name, info);
         boolean ok = false;

         for(int type : types) {
            if(ctype == type) {
               ok = true;
            }
         }

         if(!ok) {
            return false;
         }
      }

      return true;
   }

   /**
    * Get the first chart type.
    */
   protected int getFirstChartType() {
      List<String> measures = ymeasures.size() > 0 ? ymeasures : xmeasures;

      if(measures.size() == 0) {
         return -1;
      }

      String measure = measures.get(0);
      return GraphTypeUtil.getChartType(measure, info);
   }

   /**
    * Get brush data set.
    * @param adata the data generated without brushing, null if no brushing.
    * @param data the data generated with brushing.
    */
   protected DataSet getBrushDataSet(DataSet adata, DataSet data) {
      if(adata == null) {
         return data;
      }

      prepareForBrushDataSet(data, adata);

      BrushDataSet bdata = new BrushDataSet(adata, data);

      if(GraphTypes.isTreemap(info.getChartType()) || GraphTypes.isRelation(info.getChartType())) {
         // null size skipped in treemap so it's ok to brush if size is aggregate
         // since the brushed dataset would have null for excluded regions
         boolean hasMeasure = info.getSizeField() != null &&
            info.getSizeField().getDataRef() instanceof ChartAggregateRef;

         // if there is no measure, then there is no way to differentiate all data and
         // brushed data. this is a special case for treemap. we just draw the brushed
         // data and ignore the all data here.
         if(!hasMeasure) {
            bdata.setBrushedDataOnly(true);
         }
      }

      return bdata;
   }

   /**
    * Prepare data set before create BrushDataSet.
    */
   protected void prepareForBrushDataSet(DataSet data, DataSet adata) {
      if(data != null) {
         data.removeCalcValues();
      }

      if(adata != null) {
         adata.removeCalcValues();
      }
   }

   /**
    * Get data set.
    * @param data the data set.
    */
   protected DataSet getDataSet(DataSet data) {
      return data;
   }

   /**
    * Get the default rotation.
    */
   private void setDefaultRotation(AxisSpec axis, Scale scale, List<String> dims) {
      TextSpec spec = axis.getTextSpec();
      String[] fields = scale.getFields();
      int idx = dims.size() - 1;
      String dim = idx < 0 ? null : dims.get(idx);
      data.prepareCalc(dim, null, true);
      scale.getAxisSpec().getTextSpec().setFormat(spec.getFormat());
      scale.init(data);
      Object[] vals = scale.getValues();

      // always rotate if inner coord of facet with more than a couple of items
      if(dims.size() > 1) {
         if(fields.length > 0 && dims.get(dims.size() - 1).equals(fields[fields.length - 1])) {
            // if it's CJK, rotate it so the characters are drawn from
            // top-down vertically
            int rotate = (vals.length > 1) ? (isCJK(vals) ? -90 : 90) : 0;
            spec.setRotation(rotate);
         }
         // never rotate outer dimensions
         else {
            spec.setRotation(0);
         }

         return;
      }

      // size of the graph
      int w = (graphSize != null) ? graphSize.width : 1000;
      Format fmt = spec.getFormat();
      int charW = Math.max(GTool.getFontMetrics(spec.getFont()).charWidth('A'), 1);
      int max = 0;
      AxisDescriptor desc = getAxisDescriptor(scale, null);

      for(Object val : vals) {
         String lbl = GTool.format(fmt, val);
         String key = Tool.getDataString(val);
         String alias = desc == null ? null : desc.getLabelAlias(key);
         lbl = alias != null ? alias : lbl;
         max = Math.max(max, lbl.length());
      }

      // subtract left label (rough estimate)
      w -= Math.min(w * 0.1, 50);

      if(vals.length * max > w / charW && max > 1) {
         spec.setRotation(isCJK(vals) ? -90 : 90);
      }
      else {
         spec.setRotation(0);
      }
   }

   // apply scale format to merge dates cell.
   private void setDcFormat(Scale scale, AxisSpec axis) {
      scale.init(data);
      Object[] vals = scale.getValues();
      TextSpec spec = axis.getTextSpec();
      Format fmt = spec.getFormat();

      AxisDescriptor axisD = getAxisDescriptor(scale, null);
      CompositeTextFormat format = getAxisLabelFormat(axisD, scale.getFields(), false);
      XFormatInfo defaultFormat = format.getDefaultFormat().getFormat();
      XFormatInfo userFormat = format.getUserDefinedFormat().getFormat();
      boolean userChangeFormat = !userFormat.isEmpty() && !Tool.equals(defaultFormat, userFormat);

      for(Object val : vals) {
         if(val instanceof DCMergeDatesCell) {
            ((DCMergeDatesCell) val).setFormat(fmt, userChangeFormat);
         }
      }
   }

   /**
    * Check if the values contains CJK characters.
    */
   private boolean isCJK(Object[] vals) {
      for(Object val : vals) {
         if(GTool.isCJK(val + "")) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the chart info used for the chart generation.
    */
   public ChartInfo getChartInfo() {
      return info;
   }

   /**
    * Get fixed dataset.
    */
   protected DataSet getFixedDataSet(ChartInfo info, DataSet data, boolean all) {
      return data;
   }

   /**
    * Get fixed chart info.
    */
   protected ChartInfo getFixedInfo(ChartInfo info, DataSet data) {
      return info;
   }

   /**
    * Check if graph should be drawn as sparkline.
    */
   private boolean isSparkline() {
      return desc.isSparkline() &&
         isGraphType(GraphTypes.CHART_BAR, GraphTypes.CHART_BAR_STACK,
                     GraphTypes.CHART_LINE, GraphTypes.CHART_LINE_STACK,
                     CHART_STEP, GraphTypes.CHART_STEP_AREA,
                     CHART_STEP_STACK, GraphTypes.CHART_STEP_AREA_STACK,
                     CHART_JUMP,
                     GraphTypes.CHART_AREA, GraphTypes.CHART_AREA_STACK,
                     // text only point used together as sparkline total
                     GraphTypes.CHART_POINT, GraphTypes.CHART_POINT_STACK,
                     GraphTypes.CHART_INTERVAL);
   }

   /**
    * Fix value order comparer, for brushing target chart, measure header does
    * not contains enough data, fix value column to all measure header.
    */
   private void fixComparer() {
      boolean stack = isStackedGraph();

      for(int i = 0; i < data.getColCount(); i++) {
         String col = data.getHeader(i);
         Comparator comp = data.getComparator(col);

         if(comp instanceof ValueOrderComparer) {
            ValueOrderComparer vocomp = (ValueOrderComparer) comp;

            if(isBrusingTarget()) {
               String vcol = vocomp.getValueCol();

               if(!vcol.contains(BrushDataSet.ALL_HEADER_PREFIX)) {
                  vcol = BrushDataSet.ALL_HEADER_PREFIX + vcol;
                  vocomp.setValueCol(vcol);
               }
            }

            if(stack) {
               vocomp.setFormula(new SumFormula());
            }
         }
      }
   }

   /**
    * Merge the alias into legend.
    */
   private void setLegendProperties(VisualFrame frame, LegendDescriptor desc) {
      LegendSpec legend = frame.getLegendSpec();
      String fld = frame.getField();
      legend.setTitleVisible(desc.isTitleVisible());

      if(desc.isNotShowNull()) {
         frame.setScaleOption(Scale.NO_NULL);
      }

      if(desc.isIncludeZero()) {
         frame.setScaleOption(frame.getScaleOption() | Scale.ZERO);
         frame.setScale(null);
         szvisitor.initFrame(frame, null);
      }

      TextFrame text = desc.getTextFrame();

      if(desc.getTitle() != null && !"".equals(desc.getTitle())) {
         legend.setTitle(desc.getTitle());
      }

      if(frame.getScale() instanceof LinearScale) {
         ((LinearScale) frame.getScale()).setReversed(false);
      }

      if(!(frame instanceof CategoricalFrame) && (desc.isLogarithmicScale() || desc.isReversed())) {
         Scale scale = desc.isLogarithmicScale() ? new LogScale() : new LinearScale();
         scale.setFields(frame.getField());
         frame.setScale(scale);

         // reversed size scale not supported for treemap.
         if(!GraphTypes.isTreemap(info.getChartType()) || !(frame instanceof SizeFrame)) {
            ((LinearScale) scale).setReversed(desc.isReversed());
         }
      }

      if(text == null) {
         return;
      }

      DefaultTextFrame text1 = (DefaultTextFrame) legend.getTextFrame();

      if(text1 == null) {
         legend.setTextFrame(text);
         return;
      }

      for(Object key : text.getKeys()) {
         text1.setText(key, text.getText(key));
      }
   }

   /**
    * Prepare data set with inner dim.
    */
   private static void prepareDataSet(ChartInfo info, DataSet data,
                                      DataSet adata, ChartDescriptor desc, DateComparisonInfo dateComparison)
   {
      List<XDimensionRef> dims = GraphUtil.getSeriesDimensionsForCalc(info);
      VSDataRef innerFld = (dims.size() > 0) ? dims.get(dims.size() - 1) : null;
      String innerDim = (innerFld != null) ? innerFld.getFullName() : null;
      PlotDescriptor plotdesc = desc.getPlotDescriptor();

      data.removeCalcRows(TimeSeriesRow0.class);
      data.removeCalcValues();

      if(adata != null) {
         adata.removeCalcRows(TimeSeriesRow0.class);
         adata.removeCalcValues();
      }

      boolean timeFilled = false;
      // calc (e.g. previous value) need to be applied to filled in values on time series. (51770)
      boolean hasPreColCalc = Arrays.stream(info.getAggregateRefs())
         .anyMatch(a -> {
            Calculator calc = ((CalculateAggregate) a).getCalculator();
            return calc != null && (calc.getType() == Calculator.CHANGE ||
               calc.getType() == Calculator.VALUE);
         });
      boolean appliedDC = info instanceof VSChartInfo &&
         ((VSChartInfo) info).isAppliedDateComparison();
      // week grouping ("wy") goes across months and filling in intervals causes the result
      // to have gaps. disable time series as the usefulness is questionable. (64055)
      boolean mixedInterval = appliedDC && dateComparison != null &&
         dateComparison.getInterval().getGranularity() == DateComparisonInfo.WEEK;

      if((plotdesc.isFillTimeGap() || hasPreColCalc) && innerFld != null &&
         GraphUtil.shouldFillTimeGap(info) && !mixedInterval)
      {
         XDimensionRef dim = (XDimensionRef) innerFld;

         if(isDate(dim) && dim.isTimeSeries()) {
            // @by stephenwebster, For bug1402949727054
            // Filling time series gap for bars is new.
            // Existing interfaces do not support setting this option,
            // but you can in script.  Filling a gap with a zero does not
            // make sense for bar graphs as you would end up with a zero size
            // section of bar.  I believe it is better not to show it.
            // The primary use case for this is for running totals
            // and it is a little bit of a stretch from the current implementation
            boolean bar = GraphTypeUtil.isChartType(info, appliedDC, type ->
               GraphTypes.isBar(type) || GraphTypes.is3DBar(type));

            // don't fill zero if fill-zero is not explicitly specified. (57350)
            Object missval = plotdesc.isFillTimeGap() && plotdesc.isFillZero() ? 0 : null;
            missval = bar && missval == null ? 1 : missval;

            int dtype = getDateType(data, innerDim);
            TimeSeriesRow rows = new TimeSeriesRow0(innerDim, dtype, missval, info);

            // calc (e.g. previous value) need to be applied to filled in values on time series.
            // so we should have the rows added before processColCalc. (51770)
            // don't calculate missing value if it's filled. (51913)
            rows.setPreColumn(hasPreColCalc);
            rows.setGroupFields(getGroupFields(data, innerDim));
            rows.setOuterFields(getOuterFields(info, data, innerDim));
            data.addCalcRow(rows);
            timeFilled = true;

            if(adata != null) {
               adata.addCalcRow(rows);
            }
         }
      }

      Set changeCalcFields = new HashSet();

      for(DataSet dset : new DataSet[] {data, adata}) {
         if(dset != null) {
            List<CalcColumn> allcalcs = dset.getCalcColumns();

            for(CalcColumn calcCol : allcalcs) {
               if(calcCol instanceof ChangeColumn) {
                  changeCalcFields.add(((ChangeColumn) calcCol).getDim());
               }

               if(calcCol instanceof AbstractColumn) {
                  ((AbstractColumn) calcCol).setDimensions(dims);
                  ((AbstractColumn) calcCol).setInnerDim(innerDim);
               }
            }
         }
      }

      // if an aesthetic field (e.g. year) is used for change comparison (e.g. from previous year),
      // we fill the ranges so the comparisons are uniform. for example, if a product has
      // comparison between 2018 and 2019, and one has no data for 2019, we should still compare
      // 2018 and 2019 (assume 0).
      if(!timeFilled && GraphUtil.shouldFillTimeGap(info) && !mixedInterval) {
         for(AestheticRef aRef : info.getAestheticRefs(true)) {
            if(aRef != null && aRef.getRTDataRef() instanceof XDimensionRef) {
               XDimensionRef dim = (XDimensionRef) aRef.getRTDataRef();
               String dateCol = dim.getFullName();

               // this is only applied if as time series
               if(isDate(dim) && changeCalcFields.contains(dateCol)) {
                  int dtype = getDateType(data, dim.getFullName());

                  TimeSeriesRow rows = new TimeSeriesRow0(dateCol, dtype, 0, info);
                  rows.setGroupFields(getGroupFields(data, dateCol));
                  rows.setOuterFields(getOuterFields(info, data, dateCol));
                  rows.setPreColumn(true);
                  rows.setGlobalRange(true);
                  data.addCalcRow(rows);

                  if(adata != null) {
                     adata.addCalcRow(rows);
                  }
                  break;
               }
            }
         }
      }

      if(data != null) {
         data.prepareCalc(innerDim, null, true);
      }

      if(adata != null) {
         // not prepare all data calc here, the field scale is not ready
         // adata.prepareCalc(innerDim, null);
      }
   }

   /**
    * Find all the dims other than the inner dim.
    */
   private static String[] getGroupFields(DataSet data, String innerDim) {
      Set<String> dims = new HashSet<>();

      for(int i = 0; i < data.getColCount(); i++) {
         String hdr = data.getHeader(i);

         if(hdr != null && !data.isMeasure(hdr) && !hdr.equals(innerDim)) {
            dims.add(hdr);
         }
      }

      return dims.toArray(new String[0]);
   }

   /**
    * Find all the outer dims.
    */
   private static String[] getOuterFields(ChartInfo info, DataSet data,
                                          String innerDim)
   {
      Set<String> dims = new HashSet<>();
      ChartRef[][] rfields = { info.getRTXFields(), info.getRTYFields() };

      for(ChartRef[] fields : rfields) {
         for(ChartRef ref : fields) {
            String hdr = ref.getFullName();

            if(hdr != null && !data.isMeasure(hdr) && !hdr.equals(innerDim)) {
               dims.add(hdr);
            }
         }
      }

      return dims.toArray(new String[0]);
   }

   /**
    * Create a fake dimension and add to dataset and scale.
    */
   DataSet createFakeDimension(DataSet data) {
      ExpandableDataSet eset = new ExpandableDataSet(data);

      eset.addDimension("Dimension", null);
      scales.put("Dimension", new CategoricalScale("Dimension"));
      return eset;
   }

   /**
    * Set the min/max pre-reverse.
    */
   private void setMin(LinearScale linear, Number min) {
      if(linear.isReversed()) {
         linear.setMax(min);
      }
      else {
         linear.setMin(min);
      }
   }

   /**
    * Set the min/max pre-reverse.
    */
   private void setMax(LinearScale linear, Number max) {
      if(linear.isReversed()) {
         linear.setMin(max);
      }
      else {
         linear.setMax(max);
      }
   }

   /**
    * Check the validity.
    */
   protected boolean checkValidity() {
      return info.isMultiStyles() ? checkValidityMulti() : checkValidityGlobal();
   }

   /**
    * Check the validity.
    * @return true if valid, false invalid (no warn for better user experience).
    * It will throw exception to warn user when serious problem found.
    */
   private boolean checkValidityMulti() {
      // check if supports inverted
      if(ymeasures.size() == 0) {
         for(int i = 0; i < xmeasures.size(); i++) {
            String xname = xmeasures.get(i);
            int ctype = GraphTypeUtil.getChartType(xname, info);

            if(!GraphTypes.supportsInvertedChart(ctype)) {
               throw new MessageException(Catalog.getCatalog().getString(
                  "em.common.graph.invalidTypeFound",
                  GraphTypes.getDisplayName(ctype)), LogLevel.WARN, false, ConfirmException.INFO);
            }
         }
      }

      // check if x has more than measures when y as measure
      if(ymeasures.size() > 0 && xmeasures.size() > 1) {
         throw new MessageException(Catalog.getCatalog().getString(
            "em.common.graph.tooManyXMeasure"), LogLevel.WARN, false,
                                    ConfirmException.INFO);
      }

      // check if both x and y have measures
      if(xmeasures.size() > 0 && ymeasures.size() > 0 && !isXYSupported()) {
         throw new MessageException(Catalog.getCatalog().getString(
            "em.common.graph.XYNotSupported"), LogLevel.INFO, false,
                                    ConfirmException.INFO);
      }

      int ctype = getFirstChartType();
      List measures = ymeasures.size() > 0 ? ymeasures : xmeasures;

      // check if the chart types are compatible
      for(int i = 0; i < measures.size(); i++) {
         String measure = (String) measures.get(i);
         ctype = GraphTypeUtil.getChartType(measure, info);

         for(int j = i + 1; j < measures.size(); j++) {
            String measure2 = (String) measures.get(j);
            int ctype2 = GraphTypeUtil.getChartType(measure2, info);

            if(!GraphTypes.isCompatible(ctype, ctype2)) {
               throw new MessageException(Catalog.getCatalog().getString(
                  "em.common.graph.incompatibleTypes",
                  GraphTypes.getDisplayName(ctype),
                  GraphTypes.getDisplayName(ctype2)), LogLevel.WARN, false, ConfirmException.INFO);
            }
         }
      }

      // binding more than one measure is not allowed for waterfall chart
      if(ctype == GraphTypes.CHART_WATERFALL && ymeasures.size() > 1) {
         throw new MessageException(Catalog.getCatalog().getString(
                "em.common.graph.measureForWaterfall"), LogLevel.WARN, false,
                                    ConfirmException.INFO);
      }

      return true;
   }

   /**
    * Check the validity.
    * @return true if valid, false invalid (no warn for better user experience).
    * It will throw exception to warn user when serious problem found.
    */
   private boolean checkValidityGlobal() {
      int ctype = getChartType();

      // check if supports inverted
      if(ymeasures.size() == 0 && xmeasures.size() > 0 &&
         !GraphTypes.supportsInvertedChart(ctype))
      {
         throw new MessageException(Catalog.getCatalog().getString(
            "em.common.graph.invalidTypeFound",
            GraphTypes.getDisplayName(ctype)), LogLevel.WARN, false, ConfirmException.INFO);
      }

      // check if both x and y have measures
      if(xmeasures.size() > 0 && ymeasures.size() > 0 &&
         !GraphTypes.isPoint(ctype) && !GraphTypes.isLine(ctype) &&
         !GraphTypes.isScatteredContour(ctype))
      {
         throw new MessageException(Catalog.getCatalog().getString(
            "em.common.graph.XYNotSupported"), LogLevel.INFO, false,
                                    ConfirmException.INFO);
      }

      // binding more than one measure is not allowed for waterfall chart
      if(ctype == GraphTypes.CHART_WATERFALL && ymeasures.size() > 1) {
         throw new MessageException(Catalog.getCatalog().getString(
                "em.common.graph.measureForWaterfall"), LogLevel.INFO, false,
                                    ConfirmException.INFO);
      }

      return true;
   }

   /**
    * Check if measure on both X and Y is supported.
    */
   private boolean isXYSupported() {
      return isGraphType(GraphTypes.CHART_POINT, GraphTypes.CHART_LINE,
                         CHART_STEP, CHART_JUMP, GraphTypes.CHART_STEP_AREA,
                         CHART_STEP_STACK, GraphTypes.CHART_STEP_AREA_STACK,
                         GraphTypes.CHART_LINE_STACK, GraphTypes.CHART_AREA,
                         GraphTypes.CHART_AREA_STACK, GraphTypes.CHART_SCATTER_CONTOUR,
                         GraphTypes.CHART_MAP_CONTOUR);
   }

   /**
    * Check if the graph is stacked.
    */
   private boolean isStackedGraph() {
      if(GraphTypeUtil.isWordCloud(info)) {
         return false;
      }

      return isGraphType(GraphTypes.CHART_POINT_STACK,
                         GraphTypes.CHART_BAR_STACK,
                         GraphTypes.CHART_3D_BAR_STACK,
                         GraphTypes.CHART_LINE_STACK,
                         CHART_STEP_STACK,
                         GraphTypes.CHART_STEP_AREA_STACK,
                         GraphTypes.CHART_AREA_STACK);
   }

   /**
    * Get the color frame for the chart.
    * @param elem find the frame for this element or return the global
    * frame if elem is null.
    */
   protected ColorFrame getColorFrame(GraphElement elem) {
      if(elem != null) {
         if(hasStackedMeasures(elem)) {
            ColorFrame color = (ColorFrame) cvisitor.getFrame();

            if(color == null) {
               color = info.getColorFrame();
            }

            StackedMeasuresColorFrame stackedFrame = new StackedMeasuresColorFrame(color);
            setupStackedMeasureFrame(elem, stackedFrame, cvisitor);
            return stackedFrame;
         }

         for(int i = 0; i < elem.getVarCount(); i++) {
            ColorFrame frame = (ColorFrame) cvisitor.getMeasureFrame(elem.getVar(i));

            if(frame != null) {
               return frame;
            }
         }
      }

      ColorFrame color = (ColorFrame) cvisitor.getFrame();

      if(color == null) {
         color = info.getColorFrame();
      }

      return color;
   }

   private static <T extends VisualFrame> void setupStackedMeasureFrame(
      GraphElement elem, StackedMeasuresFrame<T> stackedFrame, VSFrameVisitor cvisitor)
   {
      boolean first = true;

      for(int i = 0; i < elem.getVarCount(); i++) {
         VisualFrame frame = cvisitor.getMeasureFrame(elem.getVar(i));

         if(frame != null) {
            stackedFrame.setFrame(elem.getVar(i), (T) frame);

            // need to apply the legend label format from actual frame. (50342)
            if(first) {
               first = false;
               ((VisualFrame) stackedFrame).setLegendSpec(frame.getLegendSpec());
            }
         }
      }
   }

   /**
    * Get the color frame for the chart.
    */
   protected void setColorFrame(ColorFrame color) {
      cvisitor.setFrame(color);
   }

   /**
    * Set the color frame for the un-brushed (all) data.
    */
   protected void setAllColorFrame(ColorFrame color) {
      this.acolor = color;
   }

   /**
    * @param elem find the frame for this element or return the global
    * frame if elem is null.
    */
   private ShapeFrame getShapeFrame(GraphElement elem) {
      if(elem != null) {
         if(hasStackedMeasures(elem)) {
            ShapeFrame shape = (ShapeFrame) shpvisitor.getFrame();

            if(shape == null) {
               shape = info.getShapeFrame();
            }

            StackedMeasuresShapeFrame stackedFrame = new StackedMeasuresShapeFrame(shape);
            setupStackedMeasureFrame(elem, stackedFrame, shpvisitor);
            return stackedFrame;
         }

         for(int i = 0; i < elem.getVarCount(); i++) {
            ShapeFrame frame = (ShapeFrame) shpvisitor.getMeasureFrame(elem.getVar(i));

            if(frame != null) {
               return frame;
            }
         }
      }

      ShapeFrame shape = (ShapeFrame) shpvisitor.getFrame();

      if(shape == null) {
         shape = info.getShapeFrame();
      }

      return shape;
   }

   /**
    * @param elem find the frame for this element or return the global
    * frame if elem is null.
    */
   private LineFrame getLineFrame(GraphElement elem) {
      if(elem != null) {
         if(hasStackedMeasures(elem)) {
            LineFrame shape = (LineFrame) lnvisitor.getFrame();

            if(shape == null) {
               shape = info.getLineFrame();
            }

            StackedMeasuresLineFrame stackedFrame = new StackedMeasuresLineFrame(shape);
            setupStackedMeasureFrame(elem, stackedFrame, lnvisitor);
            return stackedFrame;
         }

         for(int i = 0; i < elem.getVarCount(); i++) {
            LineFrame frame = (LineFrame)
               lnvisitor.getMeasureFrame(elem.getVar(i));

            if(frame != null) {
               return frame;
            }
         }
      }

      LineFrame shape = (LineFrame) lnvisitor.getFrame();

      if(shape == null) {
         shape = info.getLineFrame();
      }

      return shape;
   }

   /**
    * @param elem find the frame for this element or return the global
    * frame if elem is null.
    */
   private TextureFrame getTextureFrame(GraphElement elem) {
      if(elem != null) {
         if(hasStackedMeasures(elem)) {
            TextureFrame shape = (TextureFrame) tvisitor.getFrame();

            if(shape == null) {
               shape = info.getTextureFrame();
            }

            StackedMeasuresTextureFrame stackedFrame = new StackedMeasuresTextureFrame(shape);
            setupStackedMeasureFrame(elem, stackedFrame, tvisitor);
            return stackedFrame;
         }

         for(int i = 0; i < elem.getVarCount(); i++) {
            TextureFrame frame = (TextureFrame) tvisitor.getMeasureFrame(elem.getVar(i));

            if(frame != null) {
               return frame;
            }
         }
      }

      TextureFrame shape = (TextureFrame) tvisitor.getFrame();

      if(shape == null) {
         shape = info.getTextureFrame();
      }

      return shape;
   }

   /**
    * @param elem find the frame for this element or return the global
    * frame if elem is null.
    */
   private TextFrame getTextFrame(GraphElement elem) {
      if(elem != null) {
         if(hasStackedMeasures(elem)) {
            StackedMeasuresTextFrame stackedFrame =
               new StackedMeasuresTextFrame((TextFrame) txtvisitor.getFrame());
            setupStackedMeasureFrame(elem, stackedFrame, txtvisitor);

            if(stackedFrame.getFrames().size() > 0) {
               return stackedFrame;
            }
         }
         else {
            for(int i = 0; i < elem.getVarCount(); i++) {
               TextFrame frame = (TextFrame) txtvisitor.getMeasureFrame(elem.getVar(i));

               if(frame != null) {
                  return frame;
               }
            }
         }

         // multi aesthetic waterfall, try to get from its base field
         if("true".equals(elem.getHint("_waterfall_"))) {
            for(int i = 0; i < elem.getVarCount(); i++) {
               String var = elem.getVar(i);
               var = Tool.replace(var, SumDataSet.SUM_HEADER_PREFIX, "");
               TextFrame frame = (TextFrame) txtvisitor.getMeasureFrame(var);

               if(frame != null) {
                  String field = frame.getField();

                  if(field != null && !field.startsWith(SumDataSet.ALL_HEADER_PREFIX)) {
                     frame.setField(SumDataSet.ALL_HEADER_PREFIX + field);
                  }

                  return frame;
               }
            }
         }
      }

      return (TextFrame) txtvisitor.getFrame();
   }

   /**
    * Get text frame for the element.
    * @param all true if data without brushing, false otherwise.
    * @return the specified text frame.
    */
   private TextFrame getTextFrame(GraphElement elem, boolean all) {
      TextFrame text = getTextFrame(elem);

      // change column to _all_ for brushing.
      if(!all || text == null ||
         // tree overlay draws highlight node on top and it should use the regular column
         // instead of the _all_ column. (52347, 53614)
         elem instanceof RelationElement && data instanceof BrushDataSet &&
            ((BrushDataSet) data).isBrushedDataOnly())
      {
         return text;
      }

      String field = text.getField();
      boolean measure = data.isMeasure(field);

      if(!measure) {
         return text;
      }

      if(field != null && field.startsWith(SumDataSet.ALL_HEADER_PREFIX)) {
         field = field.substring(SumDataSet.ALL_HEADER_PREFIX.length());
      }

      TextFrame allText = (TextFrame) text.clone();
      allText.setField(BrushDataSet.ALL_HEADER_PREFIX + field);
      return allText;
   }

   /**
    * Find the original size frame for the element.
    */
   private SizeFrame getSizeFrame(GraphElement elem) {
      if(elem != null) {
         if(hasStackedMeasures(elem)) {
            SizeFrame size = (SizeFrame) szvisitor.getFrame();

            if(size == null) {
               size = info.getSizeFrame();
            }

            StackedMeasuresSizeFrame stackedFrame = new StackedMeasuresSizeFrame(size);
            setupStackedMeasureFrame(elem, stackedFrame, szvisitor);
            return stackedFrame;
         }

         for(int i = 0; i < elem.getVarCount(); i++) {
            SizeFrame frame = (SizeFrame) szvisitor.getMeasureFrame(elem.getVar(i));

            if(frame == null) {
               frame = szstrategy.getFrame(elem.getVar(i));
            }

            if(frame != null) {
               return frame;
            }
         }
      }

      SizeFrame size = (SizeFrame) szvisitor.getFrame();

      if(size == null) {
         size = info.getSizeFrame();
      }

      return size;
   }

   /**
    * Find the original size frame.
    */
   private SizeFrame getSizeFrame(ChartInfo info, GraphElement elem) {
      for(int i = 0; i < elem.getVarCount(); i++) {
         ChartRef ref = info.getFieldByName(elem.getVar(i), false);

         if(ref instanceof ChartAggregateRef) {
            return ((ChartAggregateRef) ref).getSizeFrame();
         }
      }

      return info.getSizeFrame();
   }

   /**
    * Looks for any discrete measures, and if found, wraps a dataset in an
    * AliasedColumnDataSet with the discrete column names added.  Otherwise,
    * the discrete measures may not be found in the chart's dataset at runtime.
    * @param orig The original dataset.
    * @param info ChartInfo
    * @return An DataSet with new names added, or, the original DataSet if the
    * chart has no discrete measures.
    */
   private static DataSet getDiscreteMeasureDataSet(DataSet orig, ChartInfo info) {
      return orig;
      /*
      if(orig == null) {
         return orig;
      }

      DataRef[] fields = info.getRTFields();
      Map<String, String> discreteRefMappings = new HashMap<String, String>();

      for(DataRef ref : fields) {
         addDiscreteColMapping(discreteRefMappings, ref);
      }

      return discreteRefMappings.isEmpty() ? orig :
         new AliasedColumnDataSet(orig, discreteRefMappings);
      */
   }

   private static void addDiscreteColMapping(Map<String, String> map, DataRef ref) {
      if(!(ref instanceof ChartAggregateRef)) {
         return;
      }

      ChartAggregateRef cRef = (ChartAggregateRef) ref;

      if(cRef.isDiscrete()) {
         String fullname = GraphUtil.getName(cRef);
         ChartAggregateRef cRefCopy = (ChartAggregateRef) cRef.clone();
         cRefCopy.setDiscrete(false);
         String actualHeader = GraphUtil.getName(cRefCopy);
         map.put(fullname, actualHeader);
      }
   }

   /**
    * For graph with no measure, check if the graph should be inverted.
    */
   protected boolean isInvertedFake(DataSet data) {
      if(ydims.size() > 0 && xdims.size() == 0) {
         return true;
      }

      // stacked point (dot plot) is best drawn horizontally when
      // inside a facet (the height is too short)
      if(xdims.size() > 0 && ydims.size() > 0 && isStackedGraph()) {
         Scale xinner = scales.get(xdims.get(xdims.size() - 1));
         Scale yinner = scales.get(ydims.get(ydims.size() - 1));

         xinner.init(data);
         yinner.init(data);
         return xinner.getMax() < yinner.getMax() * 0.8;
      }

      return false;
   }

   ChartRef findHighlightRef(String[] flds) {
      if(flds == null || flds.length == 0) {
         return null;
      }

      final ChartRef chartRef = (ChartRef)
         // radar-one outer axis is group ref. (57035)
         Arrays.stream(info.getRTFields(true, false, false, GraphTypes.isRadarOne(info)))
            .filter(f -> flds[0].equals(f.getFullName()))
            .findFirst().orElse(null);

      if(chartRef instanceof HighlightRef &&
         (!chartRef.isMeasure() || XSchema.STRING.equals(chartRef.getDataType())) &&
         ((HighlightRef) chartRef).getHighlightGroup() != null)
      {
         return chartRef;
      }

      if(GraphTypes.isRadarOne(info)) {
         AestheticRef textField = info.getTextField();
         DataRef textRef = textField == null ? null : textField.getDataRef();

         if(!(textRef instanceof ChartRef) ||
            !Tool.equals(((ChartRef) textRef).getFullName(), flds[0]) ||
            !(textRef instanceof HighlightRef) ||
            (!((ChartRef) textRef).isMeasure() && !XSchema.STRING.equals(textRef.getDataType())))
         {
            return null;
         }

         return (ChartRef) textRef;
      }

      return null;
   }

   void addHighlightToAxis(AxisSpec axisSpec, String[] flds) {
      final ChartRef chartRef = findHighlightRef(flds);
      final HighlightGroup group = chartRef instanceof HighlightRef ?
         ((HighlightRef) chartRef).getHighlightGroup() : null;

      if(group != null) {
         group.replaceVariables(vars);
         group.setQuerySandbox(querySandbox);

         ColorFrame colorFrame = new AxisHLColorFrame(group, chartRef);
         FontFrame fontFrame = new AxisFontFrame(group);

         axisSpec.setColorFrame(colorFrame);
         axisSpec.setFontFrame(fontFrame);
      }
   }

   // is stack measure checked and applied.
   private boolean hasStackedMeasures(GraphElement elem) {
      return elem != null && elem.getVarCount() > 1 && GraphTypeUtil.isStackMeasures(info, desc);
   }

   protected VSFrameVisitor.FrameInitializer getFrameInitializer() {
      return null;
   }

   // mark the class so it could be removed to avoid accumulation of calc rows
   private static class TimeSeriesRow0 extends TimeSeriesRow {
      public TimeSeriesRow0(String tcol, int dtype, Object missingval, ChartInfo cinfo) {
         super(tcol, dtype, missingval);
         this.cinfo = cinfo;
      }

      @Override
      protected boolean isFillTimeGap(String dim) {
         if(!cinfo.isMultiStyles()) {
            return true;
         }

         VSDataRef ref = cinfo.getRTFieldByFullName(dim);

         if(ref instanceof ChartAggregateRef) {
            int type = ((ChartAggregateRef) ref).getRTChartType();
            return GraphTypes.supportsFillTimeGap(type);
         }

         return true;
      }

      // running total needs to carry over previous value. (52186, 52332)
      @Override
      protected boolean isRunningTotal(String dim) {
         VSDataRef ref = cinfo.getRTFieldByFullName(ElementVO.getBaseName(dim));

         if(ref instanceof ChartAggregateRef) {
            Calculator calc = ((ChartAggregateRef) ref).getCalculator();

            if(calc != null) {
               if(calc.getType() == Calculator.RUNNINGTOTAL) {
                  return true;
               }
            }
         }

         return false;
      }

      private final ChartInfo cinfo;
   }

   private static class MidValue extends Scale.Value {
      @Override
      public double getValue(Scale scale) {
         double min = scale.getMin();
         double max = scale.getMax();
         return min + (max - min) / 2;
      }
   }

   private static class MinValue extends Scale.Value {
      @Override
      public double getValue(Scale scale) {
         double min = scale.getMin();
         double max = scale.getMax();
         return min - (max - min);
      }
   }

   private static class MaxValue extends Scale.Value {
      @Override
      public double getValue(Scale scale) {
         double min = scale.getMin();
         double max = scale.getMax();
         return max + (max - min);
      }
   }

   private static class AxisHLColorFrame extends HLColorFrame {
      private final HighlightGroup group;
      private final ChartRef chartRef;

      public AxisHLColorFrame(HighlightGroup group, ChartRef chartRef) {
         super(null, group, null);
         this.group = group;
         this.chartRef = chartRef;
      }

      @Override
      public Color getColor(DataSet data, String col, int row) {
         return getColor(data.getData(col, row));
      }

      @Override
      public Color getColor(Object val) {
         TextHighlight highlight = (TextHighlight) group.findGroup(val);

         if(highlight != null) {
            return highlight.getForeground();
         }

         return null;
      }

      @Override
      public boolean isHighlighted(String hlname, DataSet data) {
         for(int r = 0; r < data.getRowCount(); r++) {
            Object val = data.getData(chartRef.getFullName(), r);

            if(group.findGroup(val) != null) {
               return true;
            }
         }

         return false;
      }

      @Override
      public Highlight[] getHighlights() {
         return Arrays.stream(group.getNames())
            .map(n -> group.getHighlight(n)).toArray(Highlight[]::new);
      }
   }

   private static class AxisFontFrame extends FontFrame {
      private final HighlightGroup group;

      public AxisFontFrame(HighlightGroup group) {
         this.group = group;
      }

      @Override
      public Font getFont(Object val) {
         TextHighlight highlight = (TextHighlight) group.findGroup(val);

         if(highlight != null) {
            return highlight.getFont();
         }

         return null;
      }
   }

   // RED for sparkline
   private static final Color SOFT_RED = new Color(212, 71, 71);

   protected ArrayList<String> xdims; // x dimensions
   protected ArrayList<String> xmeasures; // x measures
   protected ArrayList<String> ydims; // y dimensions
   protected ArrayList<String> ymeasures; // y measures
   protected Map<String, String> xdimlabels; // x dimension labels
   protected Map<String, String> ydimlabels; // y dimension labels
   protected Map<String, String> xmeasurelabels; // x measure labels
   protected Map<String, String> ymeasurelabels; // y measure labels
   protected Map<String, Scale> scales; // col -> scale
   protected boolean fake; // fake chart indicator
   protected ChartInfo info; // chart info stores data info
   protected final ChartDescriptor desc; // chart descriptor stores view info
   protected final ChartDescriptor desc0; // original (non-runtime) chart descriptor
   protected EGraph graph; // element graph
   protected DataSet adata; // all data if brushing exists, null otherwise
   protected DataSet odata; // normal data
   protected DataSet data; // data include brushing and all
   protected ConditionList bconds; // brush condition list
   protected Comparator voComparator; // comparator to force brush point on top
   protected Set<Coordinate> rotatedCoords;
   protected VariableTable vars; // variable table
   protected String cubeType;

   private final ChartInfo origInfo; // the original chart info
   protected Dimension graphSize = null;
   private ConditionList zconds; // zoom condition list

   private ColorFrame acolor; // color frame for all data if brushing exists
   private final VSFrameVisitor cvisitor; // color visitor
   private final VSFrameVisitor tvisitor; // texture visitor
   private final VSFrameVisitor shpvisitor; // shape visitor
   private final VSFrameVisitor lnvisitor; // line visitor
   private final VSFrameVisitor szvisitor; // shape visitor
   private final VSFrameVisitor txtvisitor; // shape visitor
   private final VSColorFrameStrategy cstrategy;
   private final VSTextureFrameStrategy tstrategy;
   private final VSShapeFrameStrategy shpstrategy;
   private final VSLineFrameStrategy lnstrategy;
   private final VSSizeFrameStrategy szstrategy;
   private final VSTextFrameStrategy txtstrategy;
   private Object querySandbox;
   private DateComparisonInfo dateComparison;
   protected Color brushHLColor = BrushingColor.getHighlightColor();
   protected Color brushDimColor = BrushingColor.getDimColor();
   protected boolean maxMode = false;
   private transient Viewsheet vs;

   private static final Logger LOG = LoggerFactory.getLogger(GraphGenerator.class);
}
