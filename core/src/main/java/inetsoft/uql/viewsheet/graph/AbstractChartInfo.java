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
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.graph.calc.RunningTotalCalc;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.SubColumns;
import inetsoft.report.internal.binding.DimensionRef;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.AbstractCondition;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.util.ContentObject;
import inetsoft.util.Tool;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Interface for all chart info classes which maintains binding info of a chart.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public abstract class AbstractChartInfo implements ChartInfo, AssetObject {
   public AbstractChartInfo() {
      chartType = GraphTypes.CHART_AUTO;
      axisDesc = new AxisDescriptor();
      axisDesc.setAxisCSS("y");
      axisDesc2 = new AxisDescriptor();
      axisDesc2.setAxisCSS("y2");
      cFrame = new StaticColorFrameWrapper();
      sFrame = new StaticSizeFrameWrapper();
      spFrame = new StaticShapeFrameWrapper();
      tFrame = new StaticTextureFrameWrapper();
      lFrame = new StaticLineFrameWrapper();
      clearRuntime();

      String astr = SreeEnv.getProperty("viewsheet.chart.adhoc");
      adhoc = astr == null || "true".equals(astr);
   }

   /**
    * Clear the runtime fields.
    */
   @Override
   public void clearRuntime() {
      rxrefs = new ChartRef[0];
      ryrefs = new ChartRef[0];
      rgrefs = new ChartRef[0];
   }

   /**
    * Get the color frame wrapper.
    * @return the color frame wrapper.
    */
   @Override
   public ColorFrameWrapper getColorFrameWrapper() {
      return cFrame;
   }

   /**
    * Set the color frame wrapper.
    * @param wrapper the color frame wrapper.
    */
   @Override
   public void setColorFrameWrapper(ColorFrameWrapper wrapper) {
      this.cFrame = wrapper;
   }

   /**
    * Get the size frame wrapper.
    * @return the size frame wrapper.
    */
   @Override
   public SizeFrameWrapper getSizeFrameWrapper() {
      return sFrame;
   }

   /**
    * Set the size frame wrapper.
    * @param wrapper the size frame wrapper.
    */
   @Override
   public void setSizeFrameWrapper(SizeFrameWrapper wrapper) {
      this.sFrame = wrapper;
   }

   /**
    * Get the shape frame wrapper.
    * @return the shape frame wrapper.
    */
   @Override
   public ShapeFrameWrapper getShapeFrameWrapper() {
      return spFrame;
   }

   /**
    * Get chart aggregate model refs, for no shape aesthetic ref.
    */
   @Override
   public ChartRef[] getModelRefs(boolean runtime) {
      ChartRef[] yrefs = getModelRefsY(runtime);

      if(yrefs != null && yrefs.length > 0) {
         return yrefs;
      }

      return getModelRefsX(runtime);
   }

   /**
    * Get the aggregates with aesthetic binding.
    */
   public List<ChartAggregateRef> getAestheticAggregateRefs(boolean runtime) {
      return Arrays.stream(getModelRefs(runtime)).filter(a -> a instanceof ChartAggregateRef)
         .map(a -> (ChartAggregateRef) a)
         .collect(Collectors.toList());
   }

   /**
    * Get the dimensions with aesthetic binding.
    */
   public List<ChartDimensionRef> getAestheticDimensionRefs(boolean runtime) {
      return Arrays.stream(getModelRefs(runtime)).filter(a -> a instanceof ChartDimensionRef)
         .map(a -> (ChartDimensionRef) a)
         .collect(Collectors.toList());
   }

   /**
    * Get chart aggregate model Y refs, for no shape aesthetic ref.
    */
   @Override
   public ChartRef[] getModelRefsY(boolean runtime) {
      ArrayList<ChartRef> refs = new ArrayList<>();
      ArrayList<String> names = new ArrayList<>();

      for(ChartRef ref : runtime ? getRTYFields() : getYFields()) {
         if(ref != null && ref.isMeasure() && !names.contains(ref.getFullName())) {
            refs.add(ref);
            names.add(ref.getFullName());
         }
      }

      return refs.toArray(new ChartRef[0]);
   }

   // @by ChrisSpagnoli bug1429047354060 2015-4-16
   // Support retrieving X measures too, even if Y measures exist.
   /**
    * Get chart aggregate model X refs, for no shape aesthetic ref.
    */
   @Override
   public ChartRef[] getModelRefsX(boolean runtime) {
      ArrayList<ChartRef> refs = new ArrayList<>();
      Set<String> names = new HashSet<>();

     for(ChartRef ref : runtime ? getRTXFields() : getXFields()) {
         if(ref != null && ref.isMeasure() && !names.contains(ref.getFullName())) {
            refs.add(ref);
            names.add(ref.getFullName());
         }
      }

      return refs.toArray(new ChartRef[0]);
   }

   /**
    * Create color model map, for no color aesthetic ref.
    */
   public abstract Map getColorFrameMap();

   /**
    * Get Aggregate refs (including aesthetics).
    */
   @Override
   public abstract VSDataRef[] getAggregateRefs();

   /**
    * Get all the binding refs (x, y, group).
    */
   @Override
   public abstract ChartRef[] getBindingRefs(boolean runtime);

   /**
    * Get all the aggregaterefs which contains the specifed dataref.
    * @param aesthetic if contains aesthetic refs.
    */
   @Override
   public abstract XAggregateRef[] getAggregates(DataRef fld, boolean aesthetic);

   /**
    * Get all the dimensionrefs which contains the specifed dataref.
    * @param aesthetic if contains aesthetic refs.
    */
   public abstract XDimensionRef[] getDimensions(DataRef fld, boolean aesthetic);

   /**
    * Get all the AestheticRefs.
    */
   @Override
   public AestheticRef[] getAestheticRefs(boolean runtime) {
      List<AestheticRef> list;
      boolean multi = false;

      if(!runtime && this instanceof VSChartInfo && ((VSChartInfo) this).isAppliedDateComparison()) {
         multi = ((VSChartInfo) this).isMultiStyles(true);
      }
      else {
         multi = isMultiAesthetic();
      }

      if(multi) {
         list = getAggregateAestheticRefs(runtime);
      }
      else {
         list = getAestheticRefs(this);
      }

      return list.toArray(new AestheticRef[list.size()]);
   }

   /**
    * Get the aesthetic field in the all aggregates from X/Y binding.
    * @param runtime true to get the runtime fields.
    */
   public List<AestheticRef> getAggregateAestheticRefs(boolean runtime) {
      List<AestheticRef> list = new ArrayList<>();

      if(!isMultiAesthetic()) {
         return list;
      }

      for(ChartAggregateRef ref : getAestheticAggregateRefs(runtime)) {
         list.addAll(getAestheticRefs(ref));
      }

      return list;
   }

   /**
    * Get the aesthetic field in the aggregate.
    * @hidden
    */
   public static List<AestheticRef> getAestheticRefs(ChartBindable aggr) {
      ArrayList<AestheticRef> list = new ArrayList<>();

      list.add(aggr.getColorField());
      list.add(aggr.getShapeField());
      list.add(aggr.getSizeField());
      list.add(aggr.getTextField());

      if(aggr instanceof RelationChartInfo) {
         list.add(((RelationChartInfo) aggr).getNodeColorField());
         list.add(((RelationChartInfo) aggr).getNodeSizeField());
      }

      return list.stream().filter(a -> a != null).collect(Collectors.toList());
   }

   /**
    * Set the shape frame wrapper.
    * @param wrapper the shape frame wrapper.
    */
   @Override
   public void setShapeFrameWrapper(ShapeFrameWrapper wrapper) {
      this.spFrame = wrapper;
   }

   /**
    * Get the texture frame wrapper.
    * @return the texture frame wrapper.
    */
   @Override
   public TextureFrameWrapper getTextureFrameWrapper() {
      return tFrame;
   }

   /**
    * Set the texture frame wrapper.
    * @param wrapper the texture frame wrapper.
    */
   @Override
   public void setTextureFrameWrapper(TextureFrameWrapper wrapper) {
      this.tFrame = wrapper;
   }

   /**
    * Get the line frame wrapper.
    * @return the line frame wrapper.
    */
   @Override
   public LineFrameWrapper getLineFrameWrapper() {
      return lFrame;
   }

   /**
    * Set the line frame wrapper.
    * @param wrapper the line frame wrapper.
    */
   @Override
   public void setLineFrameWrapper(LineFrameWrapper wrapper) {
      this.lFrame = wrapper;
   }

   /**
    * Get the color frame.
    * @return the color frame.
    */
   @Override
   public ColorFrame getColorFrame() {
      return (ColorFrame) cFrame.getVisualFrame();
   }

   /**
    * Set the color frame.
    * @param frame the color frame.
    */
   @Override
   public void setColorFrame(ColorFrame frame) {
      if(cFrame != null && cFrame.getVisualFrame().getClass() == frame.getClass()) {
         cFrame.setVisualFrame(frame);
      }
      else {
         try {
            cFrame = (ColorFrameWrapper) VisualFrameWrapper.wrap(frame);
         }
         catch(Exception e) {
            LOG.error("Failed to wrap visual frame: " + e, e);
         }
      }
   }

   /**
    * Get the size frame.
    * @return the size frame.
    */
   @Override
   public SizeFrame getSizeFrame() {
      return (SizeFrame) sFrame.getVisualFrame();
   }

   /**
    * Set the size frame.
    * @param frame the size frame.
    */
   @Override
   public void setSizeFrame(SizeFrame frame) {
      if(sFrame != null && sFrame.getVisualFrame().getClass() == frame.getClass()) {
         sFrame.setVisualFrame(frame);
      }
      else {
         try {
            sFrame = (SizeFrameWrapper) VisualFrameWrapper.wrap(frame);
         }
         catch(Exception e) {
            LOG.error("Failed to wrap visual frame: " + e, e);
         }
      }
   }

   /**
    * Get the shape frame.
    * @return the shape frame.
    */
   @Override
   public ShapeFrame getShapeFrame() {
      return (ShapeFrame) spFrame.getVisualFrame();
   }

   /**
    * Set the shape frame.
    * @param frame the shape frame.
    */
   @Override
   public void setShapeFrame(ShapeFrame frame) {
      if(spFrame != null && spFrame.getVisualFrame().getClass() == frame.getClass()) {
         spFrame.setVisualFrame(frame);
      }
      else {
         try {
            spFrame = (ShapeFrameWrapper) VisualFrameWrapper.wrap(frame);
         }
         catch(Exception e) {
            LOG.error("Failed to wrap visual frame: " + e, e);
         }
      }
   }

   /**
    * Get the texture frame.
    * @return the texture frame.
    */
   @Override
   public TextureFrame getTextureFrame() {
      return (TextureFrame) tFrame.getVisualFrame();
   }

   /**
    * Set the texture frame.
    * @param frame the texture frame.
    */
   @Override
   public void setTextureFrame(TextureFrame frame) {
      if(tFrame != null && tFrame.getVisualFrame().getClass() == frame.getClass()) {
         tFrame.setVisualFrame(frame);
      }
      else {
         try {
            tFrame = (TextureFrameWrapper) VisualFrameWrapper.wrap(frame);
         }
         catch(Exception e) {
            LOG.error("Failed to wrap visual frame: " + e, e);
         }
      }
   }

   /**
    * Get the line frame.
    * @return the line frame.
    */
   @Override
   public LineFrame getLineFrame() {
      return (LineFrame) lFrame.getVisualFrame();
   }

   /**
    * Set the line frame.
    * @param frame the line frame.
    */
   @Override
   public void setLineFrame(LineFrame frame) {
      if(lFrame != null && lFrame.getVisualFrame().getClass() == frame.getClass()) {
         lFrame.setVisualFrame(frame);
      }
      else {
         try {
            lFrame = (LineFrameWrapper) VisualFrameWrapper.wrap(frame);
         }
         catch(Exception e) {
            LOG.error("Failed to wrap visual frame: " + e, e);
         }
      }
   }

   /**
    * Get axis descriptor for measures. All measures share an axis descriptor.
    * @return the axis descriptor.
    */
   @Override
   public AxisDescriptor getAxisDescriptor() {
      return axisDesc;
   }

   /**
    * Set the axis descriptor for measures.
    * @param desc the axis descriptor.
    */
   @Override
   public void setAxisDescriptor(AxisDescriptor desc) {
      this.axisDesc = desc;
   }

   /**
    * Get secondary axis descriptor.
    */
   @Override
   public AxisDescriptor getAxisDescriptor2() {
      return axisDesc2;
   }

   /**
    * Set the secondary axis descriptor.
    */
   @Override
   public void setAxisDescriptor2(AxisDescriptor desc) {
      this.axisDesc2 = desc;
   }

   /**
    * Set the separate graph mode.
    * @param separated the specified separate graph mode.
    */
   @Override
   public void setSeparatedGraph(boolean separated) {
      this.separated = separated;
   }

   /**
    * Check if separated graph.
    * @return the separated graph mode.
    */
   @Override
   public boolean isSeparatedGraph() {
      return separated;
   }

   /**
    * Set whether each measure should has its own style.
    */
   @Override
   public void setMultiStyles(boolean multi) {
      this.multi = multi;

      if(multi) {
         setCombinedToolTip(false);
      }
   }

   /**
    * Check whether each measure should has its own style.
    */
   @Override
   public boolean isMultiStyles() {
      return multi;
   }

   /**
    * Check if aesthetics are stored in aggregates.
    */
   @Override
   public boolean isMultiAesthetic() {
      return isMultiStyles();
   }

   /**
    * Get the type of the chart.
    * @return the chart type.
    */
   @Override
   public int getChartType() {
      return chartType;
   }

   /**
    * Set the type of the chart.
    * @param type the style of the chart.
    */
   @Override
   public void setChartType(int type) {
      if(this.chartType != type) {
         this.chartType = type;
         this.rtype = type;
      }
   }

   /**
    * Remove the chart binding x axis fields.
    */
   @Override
   public void removeXFields() {
      xrefs.clear();
   }

   /**
    * Remove the chart binding y axis fields.
    */
   @Override
   public void removeYFields() {
      yrefs.clear();
   }

   /**
    * Remove the idx th x axis field.
    * @param idx the index of field in x axis.
    */
   @Override
   public void removeGroupField(int idx) {
      grefs.remove(idx);
   }

   /**
    * Get the field at the specified x axis index.
    * @param idx the index of field in x axis.
    * @return the field at the specified index in x axis.
    */
   @Override
   public ChartRef getXField(int idx) {
      if(idx < 0 || idx >= xrefs.size()) {
         return null;
      }

      return xrefs.get(idx);
   }

   /**
    * Get the field at the specified y axis index.
    * @param idx the index of field in y axis.
    * @return the field at the specified index in y axis.
    */
   @Override
   public ChartRef getYField(int idx) {
      if(idx < 0 || idx >= yrefs.size()) {
         return null;
      }

      return yrefs.get(idx);
   }

   /**
    * Get the fields at x axis.
    * @return the fields at the x axis.
    */
   @Override
   public ChartRef[] getXFields() {
      return xrefs.toArray(new ChartRef[0]);
   }

   /**
    * Get the fields at y axis.
    * @return the fields at the y axis.
    */
   @Override
   public ChartRef[] getYFields() {
      return yrefs.toArray(new ChartRef[0]);
   }

   /**
    * Get the number of x axis fields.
    * @return the x axis fields size.
    */
   @Override
   public int getXFieldCount() {
      return xrefs.size();
   }

   /**
    * Get the number of y axis fields.
    * @return the y axis fields size.
    */
   @Override
   public int getYFieldCount() {
      return yrefs.size();
   }

   /**
    * Get the runtime chart type of the chart.
    * @return the runtime chart type.
    */
   @Override
   public int getRTChartType() {
      return rtype;
   }

   /**
    * Set the runtime chart type of the chart.
    * @param type the runtime chart type of the chart.
    */
   @Override
   public void setRTChartType(int type) {
      if(type != rtype) {
         setCombinedToolTip(false);
      }

      this.rtype = type;
   }

   /**
    * Get the color field.
    */
   @Override
   public AestheticRef getColorField() {
      return colorField;
   }

   /**
    * Set the color field.
    */
   @Override
   public void setColorField(AestheticRef field) {
      this.colorField = field;
   }

   /**
    * Get the shape field.
    */
   @Override
   public AestheticRef getShapeField() {
      return shapeField;
   }

   /**
    * Set the shape field.
    */
   @Override
   public void setShapeField(AestheticRef field) {
      this.shapeField = field;
   }

   /**
    * Get the size field.
    */
   @Override
   public AestheticRef getSizeField() {
      return sizeField;
   }

   /**
    * Set the size field.
    */
   @Override
   public void setSizeField(AestheticRef field) {
      this.sizeField = field;
   }

   /**
    * Get the text field.
    */
   @Override
   public AestheticRef getTextField() {
      return textField;
   }

   /**
    * Set the text field.
    */
   @Override
   public void setTextField(AestheticRef field) {
      this.textField = field;
   }

   /**
    * Get the field at the specified x axis index.
    * @param idx the index of field in x axis.
    * @return the field at the specified index in x axis.
    */
   @Override
   public ChartRef getGroupField(int idx) {
      if(idx < 0 || idx >= grefs.size()) {
         return null;
      }

      return grefs.get(idx);
   }

   /**
    * Get the fields at x axis.
    * @return the fields at the x axis.
    */
   @Override
   public ChartRef[] getGroupFields() {
      return grefs.toArray(new ChartRef[0]);
   }

   /**
    * Get the number of x axis fields.
    * @return the x axis fields size.
    */
   @Override
   public int getGroupFieldCount() {
      return grefs.size();
   }

   /**
    * Get the facet coord flag for the chart.
    * @return true if chart contain facet coord .
    */
   @Override
   public boolean isFacet() {
      return isFacet;
   }

   /**
    * Set the facet coord flag for the chart.
    * @param facet true chart contains facet coord, false otherwise.
    */
   @Override
   public void setFacet(boolean facet) {
      this.isFacet = facet;
   }

   /**
    * Check whether is aggregated.
    * @return true if is aggregated, false otherwise.
    */
   @Override
   public boolean isAggregated() {
      return aggregated;
   }

   /**
    * Get the highlight definitions.
    */
   @Override
   public HighlightGroup getHighlightGroup() {
      return highlightGroup;
   }

   /**
    * Set the highlight definitions.
    */
   @Override
   public void setHighlightGroup(HighlightGroup highlightGroup) {
      this.highlightGroup = highlightGroup;
   }

   @Override
   public HighlightGroup getTextHighlightGroup() {
      return textHL;
   }

   @Override
   public void setTextHighlightGroup(HighlightGroup highlightGroup) {
      this.textHL = highlightGroup;
   }

   /**
    * Get hyperlink.
    */
   @Override
   public Hyperlink getHyperlink() {
      return null;
   }

   @Override
   public boolean isInvertedGraph() {
      ChartRef ref;

      for(int i = 0; i < getYFieldCount(); i++) {
         ref = getYField(i);

         if(GraphUtil.isMeasure(ref)) {
            return false;
         }
      }

      for(int i = 0; i < getXFieldCount(); i++) {
         ref = getXField(i);

         if(GraphUtil.isMeasure(ref) &&
            !GraphTypes.isPolar(((ChartAggregateRef) ref).getRTChartType()))
         {
            return true;
         }
      }

      return getXFieldCount() == 0 && getYFieldCount() > 0;
   }

   /**
    * Get the runtime chart type.
    * @param ctype the specified chart type.
    * @param xref the specified x ref.
    * @param yref the specified y ref.
    * @param mcount the specified measure count.
    */
   @Override
   public int getRTChartType(int ctype, ChartRef xref, ChartRef yref, int mcount) {
      boolean auto = GraphTypes.isAuto(getChartType()) && !isMultiStyles() ||
         isMultiStyles() && GraphTypes.isAuto(ctype);
      int rtChartType = getRTChartType(ctype, xref, yref, mcount, true);
      return GraphTypeUtil.getAvailableAutoChartType(rtChartType, auto,
         isStack(xref, yref), GraphUtil.isMeasure(xref) && GraphUtil.isMeasure(yref));
   }

   /**
    * Get the runtime chart type.
    * @param ctype the specified chart type.
    * @param xref the specified x ref.
    * @param yref the specified y ref.
    * @param mcount the specified measure count.
    */
   public int getRTChartType(int ctype, ChartRef xref, ChartRef yref, int mcount, boolean checkPer) {
      if(ctype != GraphTypes.CHART_AUTO) {
         return ctype;
      }

      if(isMultiStyles()) {
         List<Integer> ctypes = Arrays.stream(getBindingRefs(true))
            .filter(r -> r instanceof ChartAggregateRef)
            .map(r -> ((ChartAggregateRef) r).getChartType())
            .collect(Collectors.toList());
         int[] pies = new int[] {GraphTypes.CHART_PIE, GraphTypes.CHART_DONUT,
                                 GraphTypes.CHART_3D_PIE};

         if(ctypes.stream().anyMatch(t -> GraphTypes.isPie(t))) {
            for(int pie : pies) {
               if(ctypes.stream().allMatch(t -> t == pie || t == GraphTypes.CHART_AUTO)) {
                  return pie;
               }
            }
         }
      }

      boolean xdim = GraphUtil.isDimension(xref);
      boolean ydim = GraphUtil.isDimension(yref);
      boolean wordcloud = getTextField() != null;

      if(xref == null) {
         return ydim || wordcloud ? GraphTypes.CHART_POINT : GraphTypes.CHART_BAR;
      }
      else if(yref == null) {
         return xdim || wordcloud ? GraphTypes.CHART_POINT : GraphTypes.CHART_BAR;
      }

      boolean stack = isStack(xref, yref);

      // x dimension and y dimension
      if(xdim && ydim) {
         return GraphTypes.CHART_POINT; // match default in graph generator
      }
      else if(!xdim && ydim) {
         XDimensionRef dref = yref instanceof XDimensionRef ? (XDimensionRef) yref : null;

         // @by davyc, if user first add column to group refs, to keep
         //  the group visible, and change style to bar seems more better
         if(dref != null && dref.isDateTime() && dref.isTimeSeries() &&
            (dref.getRefType() & DataRef.CUBE) == 0 &&
            (XSchema.DATE.equals(DateRangeRef.getDataType(dref.getDateLevel())) ||
               XSchema.TIME_INSTANT.equals(DateRangeRef.getDataType(dref.getDateLevel()))))
         {
            // show step chart for running total (8540)
            if(xref instanceof ChartAggregateRef &&
               isUseStep((ChartAggregateRef) xref, getXFields()))
            {
               return GraphTypes.CHART_STEP;
            }

            // do not return stack line for bug1239372122942
            return GraphTypes.CHART_LINE;
         }
         else {
            return stack ? GraphTypes.CHART_BAR_STACK : GraphTypes.CHART_BAR;
         }
      }
      else if(xdim && !ydim) {
         XDimensionRef dref = xref instanceof XDimensionRef ? (XDimensionRef) xref : null;

         if(dref != null && dref.isDateTime() && dref.isTimeSeries() &&
            (dref.getRefType() & DataRef.CUBE) == 0 &&
            (XSchema.DATE.equals(DateRangeRef.getDataType(dref.getDateLevel())) ||
               XSchema.TIME_INSTANT.equals(DateRangeRef.getDataType(dref.getDateLevel()))))
         {
            // show step chart for running total (8540)
            if(yref instanceof ChartAggregateRef &&
               isUseStep((ChartAggregateRef) yref, getYFields()))
            {
               return GraphTypes.CHART_STEP;
            }

            // do not return stack line for bug bug1239372122942
            return GraphTypes.CHART_LINE;
         }
         else {
            return stack ? GraphTypes.CHART_BAR_STACK :GraphTypes.CHART_BAR;
         }
      }
      else {
         return GraphTypes.CHART_POINT;
      }
   }

   private boolean isStack(ChartRef xref, ChartRef yref) {
      ChartBindable bindable = this;

      if(isMultiStyles()) {
         if(yref instanceof ChartBindable) {
            bindable = (ChartBindable) yref;
         }
         else if(xref instanceof ChartBindable) {
            bindable = (ChartBindable) xref;
         }
      }

      boolean aesthetic = bindable.getColorField() != null &&
         !bindable.getColorField().isMeasure() ||
         bindable.getShapeField() != null &&
            !bindable.getShapeField().isMeasure() ||
         bindable.getSizeField() != null &&
            !bindable.getSizeField().isMeasure();
      boolean dimgroup = false;

      if(supportsGroupFields()) {
         for(ChartRef ref : getGroupFields()) {
            if(!ref.isMeasure()) {
               dimgroup = true;
               break;
            }
         }
      }

      return aesthetic || dimgroup;
   }

   private boolean isUseStep(ChartAggregateRef yref, ChartRef[] yfields) {
      boolean useStep = yref.getCalculator() instanceof RunningTotalCalc;

      if(!useStep) {
         useStep = Arrays.stream(yfields)
            .filter(r -> r instanceof ChartAggregateRef &&
               ((ChartAggregateRef) r).getCalculator() instanceof RunningTotalCalc)
            .findAny()
            .isPresent();
      }

      return useStep;
   }

   /**
    * Check if breakdown-by fields are supported.
    */
   @Override
   public boolean supportsGroupFields() {
      if(!multi) {
         return supportsGroupFields0(rtype);
      }
      else {
         List refs = new ArrayList();

         refs.addAll(Arrays.asList(getRTXFields()));
         refs.addAll(Arrays.asList(getRTYFields()));

         for(int i = 0; i < refs.size(); i++) {
            Object ref = refs.get(i);

            if(!(ref instanceof ChartAggregateRef)) {
               continue;
            }

            int type0 = ((ChartAggregateRef) ref).getRTChartType();

            if(!supportsGroupFields0(type0)) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Check if chart type is compatible with group fields.
    */
   private boolean supportsGroupFields0(int type) {
      return type == GraphTypes.CHART_POINT ||
         type == GraphTypes.CHART_POINT_STACK ||
         type == GraphTypes.CHART_BAR ||
         type == GraphTypes.CHART_BAR_STACK ||
         type == GraphTypes.CHART_3D_BAR ||
         type == GraphTypes.CHART_3D_BAR_STACK ||
         type == GraphTypes.CHART_LINE ||
         type == GraphTypes.CHART_LINE_STACK ||
         type == GraphTypes.CHART_STEP ||
         type == GraphTypes.CHART_STEP_STACK ||
         type == GraphTypes.CHART_JUMP ||
         type == GraphTypes.CHART_STEP_AREA ||
         type == GraphTypes.CHART_STEP_AREA_STACK ||
         type == GraphTypes.CHART_AREA ||
         type == GraphTypes.CHART_AREA_STACK ||
         type == GraphTypes.CHART_PIE ||
         type == GraphTypes.CHART_DONUT ||
         type == GraphTypes.CHART_SUNBURST ||
         type == GraphTypes.CHART_TREEMAP ||
         type == GraphTypes.CHART_CIRCLE_PACKING ||
         type == GraphTypes.CHART_ICICLE ||
         type == GraphTypes.CHART_3D_PIE ||
         type == GraphTypes.CHART_RADAR ||
         type == GraphTypes.CHART_FILL_RADAR ||
         type == GraphTypes.CHART_MEKKO ||
         type == GraphTypes.CHART_SCATTER_CONTOUR ||
         type == GraphTypes.CHART_INTERVAL ||
         type == -1;
   }

   /**
    * Check if path field is supported.
    */
   @Override
   public boolean supportsPathField() {
      if(!multi) {
         return supportsPathField0(getRTChartType());
      }
      else {
         List refs = new ArrayList();

         refs.addAll(Arrays.asList(getXFields()));
         refs.addAll(Arrays.asList(getYFields()));

         for(int i = 0; i < refs.size(); i++) {
            Object ref = refs.get(i);

            if(!(ref instanceof ChartAggregateRef)) {
               continue;
            }

            int type0 = ((ChartAggregateRef) ref).getRTChartType();

            if(!supportsPathField0(type0)) {
               return false;
            }
         }
      }

      return true;
   }

   protected boolean supportsPathField0(int type) {
      return GraphTypes.isLine(type) || GraphTypes.isMap(type);
   }

   /**
    * Get the runtime fields in x dimension.
    */
   @Override
   public ChartRef[] getRTXFields() {
      return rxrefs != null && rxrefs.length > 0 ? rxrefs : getXFields();
   }

   /**
    * Set the runtime x fields.
    */
   @Override
   public void setRTXFields(ChartRef[] refs) {
      this.rxrefs = refs;
   }

   /**
    * Get the runtime fields in y dimension.
    */
   @Override
   public ChartRef[] getRTYFields() {
      return ryrefs != null && ryrefs.length > 0 ? ryrefs : getYFields();
   }

   /**
    * Set the runtime y fields.
    */
   @Override
   public void setRTYFields(ChartRef[] refs) {
      this.ryrefs = refs;
   }

   /**
    * Get the runtime fields at x axis.
    * @return the fields at the x axis.
    */
   @Override
   public ChartRef[] getRTGroupFields() {
      return rgrefs != null && rgrefs.length > 0 ? rgrefs : getGroupFields();
   }

   /**
    * Set the runtime group fields.
    */
   protected void setRTGroupFields(ChartRef[] refs) {
      this.rgrefs = refs;
   }

   /**
    * Remove the idx th x axis field.
    * @param idx the index of field in x axis.
    */
   @Override
   public void removeXField(int idx) {
      xrefs.remove(idx);
   }

   /**
    * Remove the idx th y axis field.
    * @param idx the index of field in y axis.
    */
   @Override
   public void removeYField(int idx) {
      yrefs.remove(idx);
   }

   /**
    * Get xref visibility.  Used mainly for drilling operations for
    * ChartRefs
    * @param idx index of ref to retrieve
    */
   public boolean getXFieldVisibility(int idx) {
      return xrefs.get(idx).isDrillVisible();
   }

   /**
    * Set xref visibility.  Used mainly for drilling operations for
    * ChartRefs
    * @param idx index of ref to update
    * @param isVisible The visibility of the data ref.
    */
   public void setXFieldVisibility(int idx, boolean isVisible) {
      xrefs.get(idx).setDrillVisible(isVisible);
   }

   /**
    * Get yref visibility.  Used mainly for drilling operations for
    * ChartRefs
    * @param idx index of ref to retrieve
    */
   public boolean getYFieldVisibility(int idx) {
      return yrefs.get(idx).isDrillVisible();
   }

   /**
    * Set yref visibility.  Used mainly for drilling operations for
    * ChartRefs
    * @param idx index of ref to update
    * @param isVisible The visibility of the data ref.
    */
   public void setYFieldVisibility(int idx, boolean isVisible) {
      yrefs.get(idx).setDrillVisible(isVisible);
   }

   /**
    * Add a field to be used as x axis.
    * @param field the specified field to be added to x axis.
    */
   @Override
   public void addXField(ChartRef field) {
      xrefs.add(field);
   }

   /**
    * Add a field to be used as y axis.
    * @param field the specified field to be added to y axis.
    */
   @Override
   public void addYField(ChartRef field) {
      yrefs.add(field);
   }

   /**
    * Add a field to the break-by list.
    */
   @Override
   public void addGroupField(ChartRef field) {
      grefs.add(field);
   }

   /**
    * Add a field to be used as x axis.
    * @param idx the index of the x axis.
    * @param field the specified field to be added to x axis.
    */
   @Override
   public void addXField(int idx, ChartRef field) {
      if(idx < 0 || idx > xrefs.size() - 1) {
         xrefs.add(field);
      }
      else {
         xrefs.add(idx, field);
      }
   }

   /**
    * Add a field to be used as y axis.
    * @param idx the index of the y axis.
    * @param field the specified field to be added to y axis.
    */
   @Override
   public void addYField(int idx, ChartRef field) {
      if(idx < 0 || idx > yrefs.size() - 1) {
         yrefs.add(field);
      }
      else {
         yrefs.add(idx, field);
      }
   }

   /**
    * Add a field to be used as group axis.
    * @param idx the index of the group axis.
    * @param field the specified field to be added to group axis.
    */
   @Override
   public void addGroupField(int idx, ChartRef field) {
      if(idx < 0 || idx > grefs.size() - 1) {
         grefs.add(field);
      }
      else {
         grefs.add(idx, field);
      }
   }

   /**
    * Set the field at the specified index in x axis.
    * @param idx the index of the x axis.
    * @param field the specified field to be added to x axis.
    */
   @Override
   public void setXField(int idx, ChartRef field) {
      xrefs.set(idx, field);
   }

   /**
    * Set the field at the specified index in y axis.
    * @param idx the index of the y axis.
    * @param field the specified field to be added to y axis.
    */
   @Override
   public void setYField(int idx, ChartRef field) {
      yrefs.set(idx, field);
   }

   /**
    * Set the field at the specified index in group axis.
    * @param idx the index of the group axis.
    * @param field the specified field to be added to group axis.
    */
   @Override
   public void setGroupField(int idx, ChartRef field) {
      grefs.set(idx, field);
   }

   /**
    * Update the chart type.
    * @param seperate the seperate status of the chart.
    */
   @Override
   public abstract void updateChartType(boolean seperate);

   /**
    * Get the runtime color field.
    */
   @Override
   public DataRef getRTColorField() {
      return colorField != null ? colorField.getRTDataRef() : null;
   }

   /**
    * Get the runtime shape field.
    */
   @Override
   public DataRef getRTShapeField() {
      return shapeField != null ? shapeField.getRTDataRef() : null;
   }

   /**
    * Get the runtime size field.
    */
   @Override
   public DataRef getRTSizeField() {
      return sizeField != null ? sizeField.getRTDataRef() : null;
   }

   /**
    * Get the runtime text field.
    */
   @Override
   public DataRef getRTTextField() {
      return textField != null ? textField.getRTDataRef() : null;
   }

   /**
    * Get all fields, including x, y and aesthetic fields.
    */
   @Override
   public VSDataRef[] getFields() {
      List list = new ArrayList();

      if(containYMeasure()) {
         list.addAll(yrefs);
         list.addAll(xrefs);
      }
      else {
         list.addAll(xrefs);
         list.addAll(yrefs);
      }

      DataRef ref = (colorField != null) ? colorField.getDataRef() : null;

      if(ref != null) {
         list.add(ref);
      }

      ref = (shapeField != null) ? shapeField.getDataRef() : null;

      if(ref != null) {
         list.add(ref);
      }

      ref = (sizeField != null) ? sizeField.getDataRef() : null;

      if(ref != null) {
         list.add(ref);
      }

      ref = (textField != null) ? textField.getDataRef() : null;

      if(ref != null) {
         list.add(ref);
      }

      for(AestheticRef aref : getAggregateAestheticRefs(false)) {
         if(aref.getDataRef() != null) {
            list.add(aref.getDataRef());
         }
      }

      // only point supports breakdown field for now
      if(supportsGroupFields()) {
         list.addAll(grefs);
      }

      VSDataRef[] arr = new VSDataRef[list.size()];
      list.toArray(arr);
      return arr;
   }

   /**
    * Get all runtime fields, including x, y and aesthetic fields.
    */
   @Override
   public VSDataRef[] getRTFields() {
      return getRTFields(true, true, true, true);
   }

   /**
    * Get all runtime fields in common groups. If an aggregate has dimensions
    * bound to aesthetic fields, it may result in different grouping from
    * the default query.
    * @return a map from groups to aggregates for each unique grouping.
    */
   @Override
   public Map<Set,Set> getRTFieldGroups() {
      return getRTFieldGroups(null);
   }

   @Override
   public Map<Set, Set> getRTFieldGroups(DataRef[] extraGroups) {
      Map<Set,Set> dims2aggrs = new HashMap<>();

      if(extraGroups != null) {
         extraGroups = Arrays.stream(extraGroups)
            .filter(VSDataRef.class::isInstance)
            .toArray(VSDataRef[]::new);
      }

      // not aggregated, use a single query directly
      // ignore this logic, fix bug1344335964563
      if(!isMultiStyles()) {// || !isAggregated()) {
         VSDataRef[] refs = getRTFields();
         refs = (VSDataRef[]) ArrayUtils.addAll(refs, extraGroups);
         Set dims = new ListSet();
         Set aggs = new ListSet();

         for(VSDataRef ref : refs) {
            if(ref instanceof XDimensionRef) {
               dims.add(ref);
            }
            else {
               aggs.add(ref);
            }
         }

         dims2aggrs.put(dims, aggs);
         return dims2aggrs;
      }

      VSDataRef[] refs = getRTFields(true, false, false, false);
      refs = (VSDataRef[]) ArrayUtils.addAll(refs, extraGroups);
      List<VSDataRef> dims = new ArrayList<>();
      // others should be placed at last, otherwise will cause data not matched
      // between multi style and non-multi style charts, fix bug1343989398469
      List<VSDataRef> others = new ArrayList<>();
      // shared aggregates
      List<VSDataRef> aggrs = new ArrayList<>();
      // the measured on x/y
      List<XAggregateRef> xyAggrs = new ArrayList<>();

      for(VSDataRef ref : refs) {
         if(ref instanceof XAggregateRef) {
            // discrete measure is used as dimension in chart and should
            // be included in all queries
            if(ref instanceof ChartAggregateRef && ((ChartAggregateRef) ref).isDiscrete()) {
               aggrs.add(ref);
            }
            else {
               xyAggrs.add((XAggregateRef) ref);
            }
         }
         else if(ref instanceof XDimensionRef) {
            dims.add(ref);
            aggrs.addAll(getSortRankingsRefs((XDimensionRef) ref));
         }
      }

      for(VSDataRef ref : getRTFields(false, true, false, true)) {
         if(ref instanceof XAggregateRef) {
            aggrs.add(ref);
         }
         else if(ref instanceof XDimensionRef) {
            others.add(ref);
            aggrs.addAll(getSortRankingsRefs((XDimensionRef) ref));
         }
      }

      if(xyAggrs.size() <= 0) {
         dims.addAll(others);
         dims2aggrs.put(new ListSet(dims), new ListSet(aggrs));
      }
      else {
         for(XAggregateRef xaggr : xyAggrs) {
            if(!(xaggr instanceof ChartBindable)) {
               continue;
            }

            ChartBindable aggr = (ChartBindable) xaggr;
            Set dims2 = new ListSet(dims);
            Set aggrs2 = new ListSet(aggrs);
            aggrs2.add(aggr);
            aggr = isMultiAesthetic() ? aggr : this;

            for(AestheticRef aref : getAestheticRefs(aggr)) {
               VSDataRef ref = (VSDataRef) aref.getRTDataRef();

               if(ref instanceof XDimensionRef) {
                  dims2.add(ref);

                  // topN in per aggregate dim
                  if(isMultiAesthetic() && ref instanceof XDimensionRef) {
                     aggrs2.addAll(getSortRankingsRefs((XDimensionRef) ref));
                  }
               }
               else if(ref != null) {
                  aggrs2.add(ref);
               }
            }

            dims2.addAll(others);
            Set aggrs0 = dims2aggrs.get(dims2);

            if(aggrs0 == null) {
               dims2aggrs.put(dims2, aggrs2);
            }
            else {
               aggrs0.addAll(aggrs2);
            }
         }
      }

      return dims2aggrs;
   }

   @Override
   public Map<Set,Set> getRTDiscreteAggregates() {
      ChartRef[][] allfields = {getRTXFields(), getRTYFields()};
      Map<Set,Set> map = new HashMap<>();

      for(ChartRef[] fields: allfields) {
         ListSet groups = new ListSet();
         ListSet aggrs = new ListSet();

         for(ChartRef ref: fields) {
            if(ref instanceof XDimensionRef) {
               groups.add(ref);
               aggrs = new ListSet();
            }
            else { // XAggregateRef
               ChartAggregateRef aggr = (ChartAggregateRef) ref;

               // trend-and-comparison is calculated on the data inside dataset, and can't be
               // pushed to query. so if it's defined, we ignore it as discrete in query
               // generation. it will just be calculated as usual but treated as dimension
               // in the chart. (43784)
               if(aggr.isDiscrete() && aggr.getCalculator() == null) {
                  if(aggrs.size() == 0) {
                     map.put((Set) groups.clone(), aggrs);
                  }

                  aggrs.add(aggr);
               }
            }
         }
      }

      return map;
   }

   /**
    * Get the refs used in sort-by and ranking.
    */
   private List<VSDataRef> getSortRankingsRefs(XDimensionRef dim) {
      List<VSDataRef> aggrs = new ArrayList<>();
      String sortBy = OrderInfo.isSortByVal(dim.getOrder()) ? dim.getSortByCol() : null;
      String ranking = (dim.getRankingOption() != XCondition.NONE) ? dim.getRankingCol() : null;

      // add sort-by and top-n fields to all queries so it
      // would work for all dimensions
      for(String name : new String[] {sortBy, ranking}) {
         if(name != null) {
            VSDataRef ref2 = getRTFieldByColName(name);

            if(ref2 != null) {
               aggrs.add(ref2);
            }
         }
      }

      return aggrs;
   }

   /**
    * Get the runtime fields.
    * @param xy true to include x/y fields.
    * @param aesthetic true to include global aesthetic fields.
    * @param aggrAesthetic true to include aesthetic fields in aggregates.
    * @param others true to include group (break-by) and path fields.
    */
   public VSDataRef[] getRTFields(boolean xy, boolean aesthetic,
                                  boolean aggrAesthetic, boolean others)
   {
      List<VSDataRef> list = new ArrayList<>();

      if(rxrefs == null || ryrefs == null) {
         return new VSDataRef[0];
      }

      if(xy) {
         if(containYMeasure()) {
            list.addAll(Arrays.asList(getRTYFields()));
            list.addAll(Arrays.asList(getRTXFields()));
         }
         else {
            list.addAll(Arrays.asList(getRTXFields()));
            list.addAll(Arrays.asList(getRTYFields()));
         }
      }

      // time series are never applied for outer dimensions
      /* @by larryl, we don't need to change the property in the ref since
         the timeseries is ignored if it's nested. If we change the value here,
         the timeseries setting of nested dimension will be changed when date
         is added and removed from the binding, without the user's knowledge.
      for(int i = 0; i < list.size() - 1; i++) {
         if(list.get(i) instanceof XDimensionRef) {
            ((XDimensionRef) list.get(i)).setTimeSeries(false);
         }
      }
      */

      if(others) {
         // only point supports breakdown field for now
         if(supportsGroupFields()) {
            list.addAll(Arrays.asList(getRTGroupFields()));
         }

         if(supportsPathField()) {
            ChartRef ref = getRTPathField();

            if(ref != null) {
               list.add(ref);
            }
         }
      }

      if(aesthetic && !isMultiAesthetic()) {
         for(AestheticRef aref : getAestheticRefs(this)) {
            if(aref.getRTDataRef() != null) {
               list.add((VSDataRef) aref.getRTDataRef());
            }
         }
      }

      if(aggrAesthetic && isMultiAesthetic()) {
         List arefs = new ArrayList();

         for(AestheticRef aref : getAggregateAestheticRefs(true)) {
            DataRef baseref = aref.getRTDataRef();

            if(baseref != null && !containsSameAestheticField(list, baseref)) {
               arefs.add(baseref);
            }
         }

         list.addAll(arefs);
      }

      return list.stream().filter(a -> a != null).toArray(VSDataRef[]::new);
   }

   private boolean containsSameAestheticField(List list, DataRef target) {
      if(!(target instanceof VSDataRef)) {
         return list.contains(target);
      }

      int t_rankingOption = target instanceof XDimensionRef ?
         ((XDimensionRef) target).getRankingOption() : -1;
      int t_rankingN = target instanceof XDimensionRef ?
         ((XDimensionRef) target).getRankingOption() : -1;

      for(int i = 0; i < list.size(); i++) {
         DataRef ref = (DataRef) list.get(i);

         if(ref instanceof VSDataRef) {
            if(!Tool.equals(((VSDataRef) ref).getFullName(), ((VSDataRef) target).getFullName())) {
               continue;
            }

            int rankingOption = ref instanceof XDimensionRef ?
               ((XDimensionRef) ref).getRankingOption() : -1;
            int rankingN = ref instanceof XDimensionRef ?
               ((XDimensionRef) ref).getRankingOption() : -1;

            // make sure not losing ranking (59387).
            if(rankingOption == t_rankingOption && rankingN == t_rankingN) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Get all runtime axis fields.
    */
   @Override
   public VSDataRef[] getRTAxisFields() {
      List<ChartRef> list = new ArrayList<>();
      ChartRef[] rxrefs = getRTXFields();
      ChartRef[] ryrefs = getRTYFields();

      // add outer y dimension first
      for(int i = 0; i < ryrefs.length - 1; i++) {
         if(ryrefs[i] instanceof XDimensionRef) {
            list.add(ryrefs[i]);
         }
         else {
            break;
         }
      }

      // add outer x dimension first
      for(int i = 0; i < rxrefs.length - 1; i++) {
         if(rxrefs[i] instanceof XDimensionRef) {
            list.add(rxrefs[i]);
         }
         else {
            break;
         }
      }

      // add the x remainder
      for(int i = 0; i < rxrefs.length; i++) {
         if(!isRefContained(list, rxrefs[i])) {
            list.add(rxrefs[i]);
         }
      }

      // add the y remainder
      for(int i = 0; i < ryrefs.length; i++) {
         if(!isRefContained(list, ryrefs[i])) {
            list.add(ryrefs[i]);
         }
      }

      VSDataRef[] arr = new VSDataRef[list.size()];
      list.toArray(arr);

      return arr;
   }

   /**
    * Check if the chartref is in the list.
    */
   private boolean isRefContained(List<ChartRef> list, ChartRef ref) {
      if(!(ref instanceof XDimensionRef)) {
         return list.contains(ref);
      }

      if(!XSchema.isDateType(ref.getDataType())) {
         return list.contains(ref);
      }

      int dateLevel = ((XDimensionRef) ref).getDateLevel();

      return GraphUtil.containsDateLevelValue(ref,
         list.toArray(new ChartRef[] {}), dateLevel);
   }

   /**
    * Get ratio to increase or decrease unit width.
    */
   @Override
   public double getUnitWidthRatio() {
      return unitWidthRatio;
   }

   /**
    * Set ratio to increase or decrease unit width.
    */
   @Override
   public void setUnitWidthRatio(double ratio) {
      this.unitWidthRatio = ratio;
   }

   public double getInitialWidthRatio() {
      return initialWidthRatio;
   }

   public void setInitialWidthRatio(double initialWidthRatio) {
      this.initialWidthRatio = initialWidthRatio;
   }

   public double getInitialHeightRatio() {
      return initialHeightRatio;
   }

   public void setInitialHeightRatio(double initialHeightRatio) {
      this.initialHeightRatio = initialHeightRatio;
   }

   /**
    * Get ratio to increase or decrease unit height.
    */
   @Override
   public double getUnitHeightRatio() {
      return unitHeightRatio;
   }

   /**
    * Set ratio to increase or decrease unit height.
    */
   @Override
   public void setUnitHeightRatio(double ratio) {
      this.unitHeightRatio = ratio;
   }

   /**
    * Get the current effective width ratio.
    */
   @Override
   public double getEffectiveWidthRatio() {
      return currWidthRatio;
   }

   /**
    * Set the current effective width ratio.
    */
   @Override
   public void setEffectiveWidthRatio(double ratio) {
      this.currWidthRatio = ratio;
   }

   /**
    * Get the current effective height ratio.
    */
   @Override
   public double getEffectiveHeightRatio() {
      return currHeightRatio;
   }

   /**
    * Set the current effective height ratio.
    */
   @Override
   public void setEffectiveHeightRatio(double ratio) {
      this.currHeightRatio = ratio;
   }

   /**
    * Check if the unit width has been resized by user.
    */
   @Override
   public boolean isWidthResized() {
      return widthResized;
   }

   /**
    * Set if the unit width has been resized by user.
    */
   @Override
   public void setWidthResized(boolean resized) {
      this.widthResized = resized;
   }

   /**
    * Check if the unit height has been resized by user.
    */
   @Override
   public boolean isHeightResized() {
      return heightResized;
   }

   /**
    * Set if the unit height has been resized by user.
    */
   @Override
   public void setHeightResized(boolean resized) {
      this.heightResized = resized;
   }

   /**
    * Get the default measure of the chart if no default measure exists,
    * null will be returned.
    */
   @Override
   public String getDefaultMeasure() {
      for(int i = 0; i < yrefs.size(); i++) {
         if(yrefs.get(i).isMeasure()) {
            return yrefs.get(i).getFullName();
         }
      }

      for(int i = 0; i < xrefs.size(); i++) {
         if(xrefs.get(i).isMeasure()) {
            return xrefs.get(i).getFullName();
         }
      }

      return null;
   }

   /**
    * Set the tool tip.
    * @param toolTip the specified tool tip.
    */
   @Override
   public void setToolTip(String toolTip) {
      if(toolTip != null && !toolTip.isEmpty()) {
         customTooltip = toolTip;
      }
   }

   /**
    * Get the tool tip.
    * @return the specified tool tip.
    */
   @Override
   public String getToolTip() {
      return null;
   }

   public String getCustomTooltip() {
      return customTooltip == null ? "" : customTooltip;
   }

   /**
    * Set if the tool tip should display data from other lines in a line/area chart.
    */
   @Override
   public void setCombinedToolTip(boolean combinedTooltip) {
   }

   /**
    * Get if the tool tip should display data from other lines in a line/area chart.
    */
   @Override
   public boolean isCombinedToolTip() {
      return false;
   }

   /**
    * Remove the chart binding breakable fields.
    */
   @Override
   public void removeGroupFields() {
      grefs.clear();
   }

   /**
    * Remove the chart binding fields.
    */
   @Override
   public void removeFields() {
      ainfo = new AggregateInfo();
      xrefs.clear();
      yrefs.clear();
      grefs.clear();
      colorField = null;
      shapeField = null;
      sizeField = null;
      textField = null;
      pathRef = null;
   }

   /**
    * Check if color frame information has been modified from the default.
    */
   @Override
   public boolean isColorChanged(String... vars) {
      if(!isMultiAesthetic()) {
         // static frame is not used if field is bound
         if(colorField == null &&
            (!supportsColorFieldFrame() || !containsMeasure()) &&
            cFrame != null && cFrame.isChanged())
         {
            return true;
         }

         if(colorField != null && colorField.isChanged()) {
            return true;
         }
      }

      Set vset = new HashSet(Arrays.asList(vars));

      for(ChartAggregateRef aggr : getAestheticAggregateRefs(false)) {
         if((isMultiStyles() && vset.isEmpty() || vset.contains(aggr.getFullName())) &&
            aggr.isColorChanged())
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if shape frame information has been modified from the default.
    */
   @Override
   public boolean isShapeChanged(String... vars) {
      if(!isMultiAesthetic()) {
         // static frame is not used if field is bound
         if(shapeField == null &&
            (!supportsShapeFieldFrame() || !containsMeasure()) &&
            spFrame != null && spFrame.isChanged())
         {
            return true;
         }

         if(shapeField != null && shapeField.isChanged()) {
            return true;
         }
      }

      Set vset = new HashSet(Arrays.asList(vars));

      for(ChartAggregateRef aggr : getAestheticAggregateRefs(false)) {
         if((isMultiStyles() && vset.isEmpty() || vset.contains(aggr.getFullName())) &&
            aggr.isShapeChanged())
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if size frame information has been modified from the default.
    */
   @Override
   public boolean isSizeChanged(String... vars) {
      // @by larryl, we shouldn't mark the size as changed if it's not 1
      // since the auto-size will set it to 15 for bar, and if it's marked
      // as changed, the size would stay at 15 even when switching to line

      // static frame is not used if field is bound (same as VSFrameVisitor)
      if(!isMultiAesthetic()) {
         if(sizeField == null &&
            (!supportsSizeFieldFrame() || !containsMeasure()) &&
            sFrame != null && sFrame.isChanged())
         {
            return true;
         }

         if(sizeField != null && sizeField.isChanged()) {
            return true;
         }

         if(GraphTypes.isContour(getChartType())) {
            return false;
         }
      }

      Set vset = new HashSet(Arrays.asList(vars));

      for(ChartAggregateRef aggr : getAestheticAggregateRefs(false)) {
         if((isMultiStyles() && vset.isEmpty() || vset.contains(aggr.getFullName())) &&
            aggr.isSizeChanged())
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if any measure is bound to X/Y.
    */
   private boolean containsMeasure() {
      ChartRef[][] xyrefs = { getRTXFields(), getRTYFields() };

      for(ChartRef[] refs : xyrefs) {
         for(ChartRef ref : refs) {
            if(ref instanceof XAggregateRef) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if the color frame is per measure.
    */
   @Override
   public boolean supportsColorFieldFrame() {
      return !GraphTypes.isContour(getChartType());
   }

   /**
    * Check if the shape frame is per measure.
    */
   @Override
   public boolean supportsShapeFieldFrame() {
      return !GraphTypes.isContour(getChartType());
   }

   /**
    * Check if the size frame is per measure.
    */
   @Override
   public boolean supportsSizeFieldFrame() {
      return !GraphTypes.isContour(getChartType());
   }

   @Override
   public ChartDescriptor getChartDescriptor() {
      return chartDescriptor;
   }

   @Override
   public void setChartDescriptor(ChartDescriptor chartDescriptor) {
      this.chartDescriptor = chartDescriptor;
   }

   @Override
   public AbstractChartInfo clone() {
      try {
         AbstractChartInfo obj = (AbstractChartInfo) super.clone();

         if(ainfo != null) {
            obj.ainfo = (AggregateInfo) ainfo.clone();
         }

         if(axisDesc != null) {
            obj.axisDesc = (AxisDescriptor) axisDesc.clone();
         }

         if(axisDesc2 != null) {
            obj.axisDesc2 = (AxisDescriptor) axisDesc2.clone();
         }

         if(colorField != null) {
            obj.colorField = (AestheticRef) colorField.clone();
         }

         if(shapeField != null) {
            obj.shapeField = (AestheticRef) shapeField.clone();
         }

         if(sizeField != null) {
            obj.sizeField = (AestheticRef) sizeField.clone();
         }

         if(textField != null) {
            obj.textField = (AestheticRef) textField.clone();
         }

         if(sFrame != null) {
            obj.sFrame = (SizeFrameWrapper) sFrame.clone();
         }

         if(cFrame != null) {
            obj.cFrame = (ColorFrameWrapper) cFrame.clone();
         }

         if(spFrame != null) {
            obj.spFrame = (ShapeFrameWrapper) spFrame.clone();
         }

         if(tFrame != null) {
            obj.tFrame = (TextureFrameWrapper) tFrame.clone();
         }

         if(lFrame != null) {
            obj.lFrame = (LineFrameWrapper) lFrame.clone();
         }

         if(pathRef != null) {
            obj.pathRef = (ChartRef) pathRef.clone();
         }

         if(highlightGroup != null) {
            obj.highlightGroup = highlightGroup.clone();
         }

         if(textHL != null) {
            obj.textHL = textHL.clone();
         }

         obj.xrefs = Tool.deepCloneSynchronizedList(xrefs, new ArrayList<>());
         obj.yrefs = Tool.deepCloneSynchronizedList(yrefs, new ArrayList<>());
         obj.grefs = Tool.deepCloneSynchronizedList(grefs, new ArrayList<>());

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone AbstractChartInfo", ex);
         return null;
      }
   }

   /**
    * Check if equals another object in content.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(obj == null || !getClass().equals(obj.getClass())) {
         return false;
      }

      AbstractChartInfo chartInfo = (AbstractChartInfo) obj;

      if(isAdhocEnabled() != chartInfo.isAdhocEnabled()) {
         return false;
      }

      if(chartType != chartInfo.chartType) {
         return false;
      }

      if(separated != chartInfo.separated) {
         return false;
      }

      if(multi != chartInfo.multi) {
         return false;
      }

      if(!Tool.equalsContent(axisDesc, chartInfo.axisDesc)) {
         return false;
      }

      if(!Tool.equalsContent(axisDesc2, chartInfo.axisDesc2)) {
         return false;
      }

      AestheticRef colorField2 = chartInfo.colorField;

      if(!equalsContent(colorField, colorField2)) {
         return false;
      }

      AestheticRef shapeField2 = chartInfo.shapeField;

      if(!equalsContent(shapeField, shapeField2)) {
         return false;
      }

      AestheticRef sizeField2 = chartInfo.sizeField;

      if(!equalsContent(sizeField, sizeField2)) {
         return false;
      }

      AestheticRef textField2 = chartInfo.textField;

      if(!equalsContent(textField, textField2)) {
         return false;
      }

      SizeFrameWrapper sFrame2 = chartInfo.sFrame;

      if(!Tool.equals(sFrame, sFrame2)) {
         return false;
      }

      ColorFrameWrapper cFrame2 = chartInfo.cFrame;

      if(!Tool.equals(cFrame, cFrame2)) {
         return false;
      }

      ShapeFrameWrapper spFrame2 = chartInfo.spFrame;

      if(!Tool.equals(spFrame, spFrame2)) {
         return false;
      }

      TextureFrameWrapper tFrame2 = chartInfo.tFrame;

      if(!Tool.equals(tFrame, tFrame2)) {
         return false;
      }

      LineFrameWrapper lFrame2 = chartInfo.lFrame;

      if(!Tool.equals(lFrame, lFrame2)) {
         return false;
      }

      List<ChartRef> xrefs2 = chartInfo.xrefs;

      if(getXFieldCount() != xrefs2.size()) {
         return false;
      }

      for(int i = 0; i < getXFieldCount(); i++) {
         if(!equalsContent(getXField(i), xrefs2.get(i))) {
            return false;
         }
      }

      List<ChartRef> yrefs2 = chartInfo.yrefs;

      if(getYFieldCount() != yrefs2.size()) {
         return false;
      }

      for(int i = 0; i < getYFieldCount(); i++) {
         if(!equalsContent(getYField(i), yrefs2.get(i))) {
            return false;
         }
      }

      List<ChartRef> grefs2 = chartInfo.grefs;

      if(grefs.size() != grefs2.size()) {
         return false;
      }

      for(int i = 0; i < grefs.size(); i++) {
         if(!equalsContent(getGroupField(i), grefs2.get(i))) {
            return false;
         }
      }

      AggregateInfo ainfo2 = chartInfo.ainfo;

      if(!Tool.equalsContent(ainfo, ainfo2)) {
         return false;
      }

      if(!Tool.equals(chartInfo.mapType, mapType)) {
         return false;
      }

      if(pathRef != null && chartInfo.pathRef != null) {
         if(!equalsContent(pathRef, chartInfo.pathRef)) {
            return false;
         }
      }
      else if(pathRef != chartInfo.pathRef) {
         return false;
      }

      HighlightGroup highlightGroup2 = chartInfo.highlightGroup;

      if(!Tool.equals(highlightGroup, highlightGroup2)) {
         return false;
      }

      if(!Tool.equals(textHL, chartInfo.textHL)) {
         return false;
      }

      return tooltipVisible == chartInfo.tooltipVisible;
   }

   /**
    * Compare two objects by content.
    */
   protected boolean equalsContent(Object v1, Object v2) {
      if(v1 instanceof ContentObject) {
         return ((ContentObject) v1).equalsContent(v2);
      }

      return Tool.equals(v1, v2);
   }

   /**
    * Write attributes.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" class=\"" + getClass().getName() + "\"");
      writer.print(" chartType=\"" + getChartType() + "\"");
      writer.print(" separated=\"" + isSeparatedGraph() + "\"");
      writer.print(" multi=\"" + multi + "\"");
      writer.print(" adhocEnabled=\"" + isAdhocEnabled() + "\"");
      writer.print(" isFacet=\"" + isFacet() + "\"");
      writer.print(" rtype=\"" + getRTChartType() + "\"");
      writer.print(" unitWidthRatio=\"" + getUnitWidthRatio() + "\"");
      writer.print(" unitHeightRatio=\"" + getUnitHeightRatio() + "\"");
      writer.print(" currWidthRatio=\"" + getEffectiveWidthRatio() + "\"");
      writer.print(" currHeightRatio=\"" + getEffectiveHeightRatio() + "\"");
      writer.print(" widthResized=\"" + isWidthResized() + "\"");
      writer.print(" heightResized=\"" + isHeightResized() + "\"");
      writer.print(" donut=\"" + donut + "\" ");
      writer.print(" tooltipVisible=\"" + tooltipVisible + "\" ");

      if(mapType != null && !"null".equals(mapType)) {
         writer.print(" mapType=\"" + mapType + "\" ");
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      if(ainfo != null) {
         writer.println("<ainfo>");
         ainfo.writeXML(writer);
         writer.println("</ainfo>");
      }

      if(customTooltip != null) {
         writer.println("<customTooltip><![CDATA[");
         writer.println(customTooltip);
         writer.println("]]></customTooltip>");
      }

      if(getAxisDescriptor() != null) {
         getAxisDescriptor().writeXML(writer);
      }

      if(getAxisDescriptor2() != null) {
         writer.println("<yaxis2>");
         getAxisDescriptor2().writeXML(writer);
         writer.println("</yaxis2>");
      }

      if(getColorField() != null && !getColorField().isRuntime()) {
         writer.println("<color>");
         getColorField().writeXML(writer);
         writer.println("</color>");
      }

      if(getShapeField() != null && !getShapeField().isRuntime()) {
         writer.println("<shape>");
         getShapeField().writeXML(writer);
         writer.println("</shape>");
      }

      if(getSizeField() != null) {
         writer.println("<size>");
         getSizeField().writeXML(writer);
         writer.println("</size>");
      }

      if(getTextField() != null) {
         writer.println("<text>");
         getTextField().writeXML(writer);
         writer.println("</text>");
      }

      if(sFrame != null) {
         writer.print("<sizeVisualFrame>");
         sFrame.writeXML(writer);
         writer.print("</sizeVisualFrame>");
      }

      if(cFrame != null) {
         writer.print("<colorVisualFrame>");
         cFrame.writeXML(writer);
         writer.print("</colorVisualFrame>");
      }

      if(spFrame != null) {
         writer.print("<shapeVisualFrame>");
         spFrame.writeXML(writer);
         writer.print("</shapeVisualFrame>");
      }

      if(tFrame != null) {
         writer.print("<textureVisualFrame>");
         tFrame.writeXML(writer);
         writer.print("</textureVisualFrame>");
      }

      if(lFrame != null) {
         writer.print("<lineVisualFrame>");
         lFrame.writeXML(writer);
         writer.print("</lineVisualFrame>");
      }

      if(xrefs.size() > 0) {
         writer.println("<xrefs>");

         for(int i = 0; i < xrefs.size(); i++) {
            ChartRef ref = xrefs.get(i);
            ref.writeXML(writer);
         }

         writer.println("</xrefs>");
      }

      if(yrefs.size() > 0) {
         writer.println("<yrefs>");

         for(int i = 0; i < yrefs.size(); i++) {
            ChartRef ref = yrefs.get(i);
            ref.writeXML(writer);
         }

         writer.println("</yrefs>");
      }

      if(rxrefs != null && rxrefs.length > 0) {
         writer.println("<rxrefs>");

         for(int i = 0; i < rxrefs.length; i++) {
            ChartRef ref = rxrefs[i];
            ref.writeXML(writer);
         }

         writer.println("</rxrefs>");
      }

      if(ryrefs != null && ryrefs.length > 0) {
         writer.println("<ryrefs>");

         for(int i = 0; i < ryrefs.length; i++) {
            ChartRef ref = ryrefs[i];
            ref.writeXML(writer);
         }

         writer.println("</ryrefs>");
      }

      if(grefs.size() > 0) {
         writer.println("<grefs>");

         for(int i = 0; i < grefs.size(); i++) {
            ChartRef ref = grefs.get(i);
            ref.writeXML(writer);
         }

         writer.println("</grefs>");
      }

      if(rgrefs != null && rgrefs.length > 0) {
         writer.println("<rgrefs>");

         for(int i = 0; i < rgrefs.length; i++) {
            ChartRef ref = rgrefs[i];
            ref.writeXML(writer);
         }

         writer.println("</rgrefs>");
      }

      if(pathRef != null) {
         writer.println("<pathRef>");
         pathRef.writeXML(writer);
         writer.println("</pathRef>");
      }

      if(highlightGroup != null) {
         writer.print("<highlightGroup>");
         highlightGroup.writeXML(writer);
         writer.println("</highlightGroup>");
      }

      if(textHL != null) {
         writer.print("<textHL>");
         textHL.writeXML(writer);
         writer.println("</textHL>");
      }
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);

      ChartRef[] refs = isInvertedGraph() ? getYFields() : getXFields();

      // @by ChrisSpagnoli bug1430357109900 2015-4-30
      // May not be accurate, but "good enough" until corrected by next DrillDown event
      if(refs.length > 1) {
         ChartRef parent = refs[refs.length - 2];
         ChartRef child = refs[refs.length - 1];

         if(parent instanceof ChartDimensionRef && child instanceof ChartDimensionRef) {
            ChartDimensionRef pdim = (ChartDimensionRef) parent;
            ChartDimensionRef cdim = (ChartDimensionRef) child;

            if(XSchema.isDateType(parent.getDataType()) &&
               XSchema.isDateType(child.getDataType()) &&
               GraphUtil.getDrillDownDateLevel(pdim.getDateLevel()) == cdim.getDateLevel())
            {
               setDrillLevel(refs.length - 1);
            }
         }
      }
   }

   /**
    * Parse attributes.
    */
   protected void parseAttributes(Element elem) {
      String val;

      if((val = Tool.getAttribute(elem, "chartType")) != null) {
         setChartType(Integer.parseInt(val));
      }

      if((val = Tool.getAttribute(elem, "rtype")) != null) {
         setRTChartType(Integer.parseInt(val));
      }

      if((val = Tool.getAttribute(elem, "separated")) != null) {
         setSeparatedGraph(Boolean.parseBoolean(val));
      }

      if((val = Tool.getAttribute(elem, "multi")) != null) {
         setMultiStyles(Boolean.parseBoolean(val));
      }

      if((val = Tool.getAttribute(elem, "adhocEnabled")) != null) {
         setAdhocEnabled(Boolean.parseBoolean(val));
      }

      if((val = Tool.getAttribute(elem, "isFacet")) != null) {
         setFacet(Boolean.parseBoolean(val));
      }

      String unitWidth = Tool.getAttribute(elem, "unitWidthRatio");
      String unitHeight = Tool.getAttribute(elem, "unitHeightRatio");

      if(unitWidth != null && unitHeight != null) {
         setUnitWidthRatio(Double.parseDouble(unitWidth));
         setUnitHeightRatio(Double.parseDouble(unitHeight));
      }

      String currWidth = Tool.getAttribute(elem, "currWidthRatio");
      String currHeight = Tool.getAttribute(elem, "currHeightRatio");

      if(currWidth != null && currHeight != null) {
         setEffectiveWidthRatio(Double.parseDouble(currWidth));
         setEffectiveHeightRatio(Double.parseDouble(currHeight));
      }

      widthResized = "true".equals(Tool.getAttribute(elem, "widthResized"));
      heightResized = "true".equals(Tool.getAttribute(elem, "heightResized"));
      mapType = Tool.getAttribute(elem, "mapType");
      donut = "true".equals(Tool.getAttribute(elem, "donut"));
      tooltipVisible = !"false".equals(Tool.getAttribute(elem, "tooltipVisible"));
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      Element node = Tool.getChildNodeByTagName(elem, "customTooltip");
      String customTooltip = node != null ? Tool.getValue(node) : "";

      if(!customTooltip.isEmpty()) {
         this.customTooltip = customTooltip;
      }

      Element anode = Tool.getChildNodeByTagName(elem, "ainfo");

      if(anode != null) {
         ainfo = new AggregateInfo();
         ainfo.parseXML(Tool.getFirstChildNode(anode));
      }

      Element axisnode = Tool.getChildNodeByTagName(elem, "axisDescriptor");

      if(axisnode != null) {
         axisDesc = new AxisDescriptor();
         axisDesc.setAxisCSS("y");
         axisDesc.parseXML(axisnode);
      }

      Element axis2 = Tool.getChildNodeByTagName(elem, "yaxis2");

      if(axis2 != null) {
         axisnode = Tool.getChildNodeByTagName(axis2, "axisDescriptor");
         axisDesc2 = new AxisDescriptor();
         axisDesc2.setAxisCSS("y2");
         axisDesc2.parseXML(axisnode);
      }

      Element colorNode = Tool.getChildNodeByTagName(elem, "color");

      if(colorNode != null) {
         colorField = createAestheticRef();
         colorField.parseXML(Tool.getFirstChildNode(colorNode));
      }

      Element shapeNode = Tool.getChildNodeByTagName(elem, "shape");

      if(shapeNode != null) {
         shapeField = createAestheticRef();
         shapeField.parseXML(Tool.getFirstChildNode(shapeNode));
      }

      Element sizeNode = Tool.getChildNodeByTagName(elem, "size");

      if(sizeNode != null) {
         sizeField = createAestheticRef();
         sizeField.parseXML(Tool.getFirstChildNode(sizeNode));
      }

      Element textNode = Tool.getChildNodeByTagName(elem, "text");

      if(textNode != null) {
         textField = createAestheticRef();
         textField.parseXML(Tool.getFirstChildNode(textNode));
      }

      node = Tool.getChildNodeByTagName(elem, "sizeVisualFrame");

      if(node != null) {
         sFrame = (SizeFrameWrapper) VisualFrameWrapper.createVisualFrame(
            Tool.getFirstChildNode(node));
      }

      node = Tool.getChildNodeByTagName(elem, "colorVisualFrame");

      if(node != null) {
         cFrame = (ColorFrameWrapper) VisualFrameWrapper.createVisualFrame(
            Tool.getFirstChildNode(node));
      }

      node = Tool.getChildNodeByTagName(elem, "shapeVisualFrame");

      if(node != null) {
         spFrame = (ShapeFrameWrapper) VisualFrameWrapper.createVisualFrame(
            Tool.getFirstChildNode(node));
      }

      node = Tool.getChildNodeByTagName(elem, "lineVisualFrame");

      if(node != null) {
         lFrame = (LineFrameWrapper) VisualFrameWrapper.createVisualFrame(
            Tool.getFirstChildNode(node));
      }

      node = Tool.getChildNodeByTagName(elem, "textureVisualFrame");

      if(node != null) {
         tFrame = (TextureFrameWrapper) VisualFrameWrapper.createVisualFrame(
            Tool.getFirstChildNode(node));
      }

      Element xsnode = Tool.getChildNodeByTagName(elem, "xrefs");
      xrefs.clear();

      if(xsnode != null) {
         NodeList xnodes = Tool.getChildNodesByTagName(xsnode, "dataRef");

         for(int i = 0; i < xnodes.getLength(); i++) {
            Element xnode = (Element) xnodes.item(i);
            ChartRef ref = (ChartRef) AbstractDataRef.createDataRef(xnode);
            xrefs.add(ref);
         }
      }

      Element ysnode = Tool.getChildNodeByTagName(elem, "yrefs");
      yrefs.clear();

      if(ysnode != null) {
         NodeList ynodes = Tool.getChildNodesByTagName(ysnode, "dataRef");

         for(int i = 0; i < ynodes.getLength(); i++) {
            Element ynode = (Element) ynodes.item(i);
            ChartRef ref = (ChartRef) AbstractDataRef.createDataRef(ynode);
            yrefs.add(ref);
         }
      }

      xsnode = Tool.getChildNodeByTagName(elem, "grefs");
      grefs.clear();

      if(xsnode != null) {
         NodeList xnodes = Tool.getChildNodesByTagName(xsnode, "dataRef");

         for(int i = 0; i < xnodes.getLength(); i++) {
            Element xnode = (Element) xnodes.item(i);
            ChartRef ref = (ChartRef) AbstractDataRef.createDataRef(xnode);
            grefs.add(ref);
         }
      }

      node = Tool.getChildNodeByTagName(elem, "pathRef");

      if(node != null) {
         node = Tool.getChildNodeByTagName(node, "dataRef");
         pathRef = (ChartRef) AbstractDataRef.createDataRef(node);
      }

      Element hnode = Tool.getChildNodeByTagName(elem, "highlightGroup");

      if(hnode != null) {
         highlightGroup = new HighlightGroup();
         highlightGroup.parseXML((Element) hnode.getFirstChild());
      }

      hnode = Tool.getChildNodeByTagName(elem, "textHL");

      if(hnode != null) {
         textHL = new HighlightGroup();
         textHL.parseXML((Element) hnode.getFirstChild());
      }
   }

   /**
    * Create a new aesthetic ref.
    */
   protected abstract AestheticRef createAestheticRef();

   /**
    * Get the adhoc enabled flag for the chart.
    */
   @Override
   public boolean isAdhocEnabled() {
      return adhoc;
   }

   /**
    * Set the adhoc enabled flag for the chart.
    * @param adhoc true to enable adhoc on chart, false otherwise.
    */
   @Override
   public void setAdhocEnabled(boolean adhoc) {
      this.adhoc = adhoc;
   }

   /**
    * Set the aggregate info.
    * @param info the specified aggregate info.
    */
   public void setAggregateInfo(AggregateInfo info) {
      this.ainfo = info == null ? new AggregateInfo() : info;
   }

   /**
    * Get the aggregate info.
    * @return the aggregate info.
    */
   public AggregateInfo getAggregateInfo() {
      return ainfo;
   }

   /**
    * Get the map type.
    * @return the map type.
    */
   @Override
   public String getMapType() {
      return mapType;
   }

   /**
    * Set the map type.
    * @param type the map type.
    */
   @Override
   public void setMeasureMapType(String type) {
      this.mapType = type;
   }

   @Override
   public String getMeasureMapType() {
      return mapType;
   }

   /**
    * Contains y measure or not.
    */
   public boolean containYMeasure() {
      ChartRef[] yflds = ryrefs == null || ryrefs.length == 0 ? getYFields() : ryrefs;
      return yflds.length > 0 && yflds[yflds.length - 1] instanceof ChartAggregateRef;
   }

   public boolean containXMeasure() {
      ChartRef[] xflds = rxrefs == null || rxrefs.length == 0 ? getXFields() : rxrefs;
      return xflds.length > 0 && xflds[xflds.length - 1] instanceof ChartAggregateRef;
   }
   /**
    * Get runtime field by a full name.
    * @param name the specified field full name or name.
    * @return the runtime field.
    */
   @Override
   public VSDataRef getRTFieldByFullName(String name) {
      VSDataRef[] arr = getRTFields();

      for(int i = 0; i < arr.length; i++) {
         if(arr[i].getFullName().equals(name)) {
            return arr[i];
         }
      }

      return null;
   }


   /**
    * Get the field that precisely matches the name.
    *
    * @param name the specified field full name
    * @param rt true to search runtime fields and normal field, otherwise only
    * search non-runtime fields.
    * @return the field that exactly matches the full name; null if not found
    */
   @Override
   public ChartRef getFieldByName(String name, boolean rt) {
      return getFieldByName(name, rt, false);
   }

   @Override
   public ChartRef getFieldByName(String name, boolean rt, boolean ignoreDataGroup) {
      // @by davidd, FYI: ChartProcessor.getRuntimeField() is similar, but also
      // performs non-precise matching.

      ChartRef[][] arrs = rt
         ? new ChartRef[][] {getRTXFields(), getRTYFields(), getRTGroupFields(), getXFields(),
                             getYFields(), getGroupFields()}
         : new ChartRef[][] {getXFields(), getYFields(), getGroupFields()};

      for(ChartRef[] arr : arrs) {
         for(ChartRef ref : arr) {
            if(isSameField(ref, name, ignoreDataGroup)) {
               return ref;
            }
         }
      }

      if(!rt) {
         for(AestheticRef aref : getAestheticRefs(false)) {
            if(aref.getDataRef() instanceof ChartRef &&
               isSameField((ChartRef) aref.getDataRef(), name, ignoreDataGroup))
            {
               return (ChartRef) aref.getDataRef();
            }
         }

         for(AestheticRef aref : getAggregateAestheticRefs(false)) {
            if(aref.getDataRef() instanceof ChartRef &&
               isSameField((ChartRef) aref.getDataRef(), name, ignoreDataGroup))
            {
               return (ChartRef) aref.getDataRef();
            }
         }
      }

      return null;
   }

   /**
    * Check if the target chartref is the ref with the target name.
    * @param ignoreDataGroup true to ignore the DataGroup prefix, else false.
    */
   public static boolean isSameField(ChartRef ref, String name, boolean ignoreDataGroup) {
      if(ref == null || name == null) {
         return false;
      }

      if(Objects.equals(ref.getFullName(), name)) {
         return true;
      }

      if(!ignoreDataGroup || !(ref instanceof VSDimensionRef)) {
         return false;
      }

      VSDimensionRef dim = (VSDimensionRef) ref;

      if(!dim.isNameGroup() || dim.getGroupType() == null) {
         return false;
      }

      return Objects.equals(ref.getFullName(),
         NamedRangeRef.getName(name, Integer.parseInt(dim.getGroupType())));
   }

   /**
    * Get the field to used to sort the points into a path (line).
    */
   @Override
   public ChartRef getPathField() {
      return pathRef;
   }

   /**
    * Get the runtime field to used to sort the points into a path (line).
    */
   @Override
   public ChartRef getRTPathField() {
      return pathRef;
   }

   /**
    * Set the field to used to sort the points into a path (line).
    */
   @Override
   public void setPathField(ChartRef ref) {
      this.pathRef = ref;
   }

   /**
    * Update chart type by generating runtime chart type.
    * @param separated true if the chart types are maintained in aggregate,
    * false otherwise.
    */
   @Override
   public void updateChartType(boolean separated, ChartRef[] xrefs, ChartRef[] yrefs) {
      ChartRef xref = getLastField(xrefs);
      ChartRef yref = getLastField(yrefs);

      // if runtime refs have not been populated, don't change the runtime type
      // which could be incorrect since getRTChartType() uses ref types.
      // support word cloud for text field binding only.
      if(xrefs.length == 0 && yrefs.length == 0 &&
         getTextField() == null && !supportUpdateChartType())
      {
         return;
      }

      // separated chart? only one chart type
      if(separated) {
         int ctype = getChartType();
         ctype = getRTChartType(ctype, xref, yref, 1);
         setRTChartType(ctype);
      }
      // not separated chart? one chart type per measure
      else {
         // make sure the RTChartType is set in the design time ref
         // so the gui could get the right chart type per aggregate ref
         updateFieldChartTypes(getXFields(), getYFields());

         updateFieldChartTypes(xrefs, yrefs);

         // if group fields existing, update the type again after the rt chart
         // type is updated so the supportsGroupFields is correct.
         if(getGroupFieldCount() > 0 && supportsGroupFields()) {
            updateFieldChartTypes(xrefs, yrefs);
         }
      }
   }

   public boolean supportUpdateChartType() {
      return rgrefs != null && rgrefs.length > 0 || getAestheticRefs(true).length > 0;
   }

   /**
    * Update the individual chart type of aggregate fields.
    */
   private void updateFieldChartTypes(ChartRef[] xrefs, ChartRef[] yrefs) {
      ChartRef xref = getLastField(xrefs);
      ChartRef yref = getLastField(yrefs);
      int ctype = 0;

      if(yref != null && yref.isMeasure()) {
         int mcount = 0;

         for(int i = yrefs.length - 1; i >= 0; i--) {
            if(!(yrefs[i] instanceof ChartAggregateRef)) {
               break;
            }

            mcount++;
         }

         for(int i = yrefs.length - 1; i >= 0; i--) {
            if(!(yrefs[i] instanceof ChartAggregateRef)) {
               break;
            }

            ChartAggregateRef measure = (ChartAggregateRef) yrefs[i];
            ctype = measure.getChartType();
            ctype = getRTChartType(ctype, xref, measure, mcount);
            measure.setRTChartType(ctype);
         }
      }
      else if(xref != null && xref.isMeasure()) {
         int mcount = 0;

         for(int i = xrefs.length - 1; i >= 0; i--) {
            if(!(xrefs[i] instanceof ChartAggregateRef)) {
               break;
            }

            mcount++;
         }

         for(int i = xrefs.length - 1; i >= 0; i--) {
            if(!(xrefs[i] instanceof ChartAggregateRef)) {
               break;
            }

            ChartAggregateRef measure = (ChartAggregateRef) xrefs[i];
            ctype = measure.getChartType();
            ctype = getRTChartType(ctype, measure, yref, mcount);
            measure.setRTChartType(ctype);
         }
      }
   }

   /**
    * Get the last chart ref.
    */
   public static ChartRef getLastField(ChartRef[] refs) {
      if(refs == null) {
         return null;
      }

      for(int i = refs.length - 1; i >= 0; i--) {
         if(refs[i].isDrillVisible()) {
            return refs[i];
         }
      }

      return null;
   }

   /**
    * Update the aggregate ref status. If there is any non-aggregated
    * aggregateRef, no grouping is applied and we should mark the ref as
    * not aggregated, so the title on the chart would not say Sum of aggr.
    */
   protected void updateAggregateStatus() {
      // fix bug1359448019481, we should update aggregate ref status for
      // both design time fields and runtime fields, so AllChartAggregateRef can
      // get aeshetic ref properly.
      VSDataRef[] rtFields = getRTFields();
      VSDataRef[] fields = getFields();
      int rlen = rtFields.length;
      DataRef[] arr = (DataRef[]) Tool.mergeArray(rtFields, fields);
      boolean hasAggrCalc = false;
      // logic for determining aggregated should match the AggregateInfo.isRealAggregated()
      // otherwise logic depending on whether a column is aggregated in VSDataSet would
      // not work (47946).
      aggregated = true;
      boolean anyAggregated = false;

      for(int i = 0; i < arr.length; i++) {
         if(!(arr[i] instanceof ChartAggregateRef)) {
            continue;
         }

         if((arr[i].getRefType() & DataRef.CUBE) == DataRef.CUBE) {
            return;
         }

         ChartAggregateRef aref = (ChartAggregateRef) arr[i];

         if(!shouldAggregate(aref)) {
            aref.setAggregated(false);
         }
         // this sets the internal aggregated flag to true
         else {
            aref.setAggregated(true);
         }

         if((aref.getRefType() & DataRef.AGG_CALC) != 0) {
            hasAggrCalc = true;
         }

         // for designtime ref, it is not updated, so status will
         // be wrong, fix bug1366866343895
         if(i < rlen) {
            // isAggregated() checks if the aref's formula is an aggregate if the internal
            // aggregated flag is true; it is not symmetrical to setAggregated().
            // is has aggregate calc field, force aggregate (47946)
            // (same as AggregateInfo.isRealAggregated)
            aggregated = aggregated && isAggregateEnabled(aref) || hasAggrCalc;
            anyAggregated = aref.isAggregateEnabled();
         }
      }

      // if only lon/lat in map, and no other aggregated measure, the chart is not aggregated.
      if(!anyAggregated) {
         aggregated = false;
      }

      if(!aggregated) {
         for(int i = 0; i < arr.length; i++) {
            if(!(arr[i] instanceof XAggregateRef)) {
               continue;
            }

            XAggregateRef aref = (XAggregateRef) arr[i];
            aref.setAggregated(false);
         }
      }
   }

   /**
    * Check whether the Aggregate is enabled.
    */
   protected boolean isAggregateEnabled(ChartAggregateRef aref) {
      return aref != null && aref.isAggregateEnabled();
   }

   /**
    * Set the local text id map.
    * @param map the local map.
    */
   public void setLocalMap(Map<String, String> map) {
      this.localMap = map;
   }

   /**
    * Get the local text id map.
    * @return the local map.
    */
   public Map<String, String> getLocalMap() {
      return localMap;
   }

   /**
    * Check a data ref support as time series. Turn off time-series flag
    * for dimensions on facet graph.
    */
   public void fixTimeSeries() {
      // update time series
      ChartRef[] xrefs = getRTXFields();

      for(ChartRef xref : xrefs) {
         if(xref instanceof XDimensionRef) {
            fixTimeSeries0((XDimensionRef) xref, true);
         }
      }

      ChartRef[] yrefs = getRTYFields();

      for(ChartRef yref : yrefs) {
         if(yref instanceof XDimensionRef) {
            fixTimeSeries0((XDimensionRef) yref, false);
         }
      }
   }

   public int getDimensionDateLevel(XDimensionRef dref) {
      return dref.getDateLevel();
   }

   protected boolean isDateDimension(XDimensionRef dref, int level) {
      String type = dref.getDataType();
      return (XSchema.isDateType(type) && DateRangeRef.isDateTime(level)) &&
             (dref.getRefType() & DataRef.CUBE) == 0;
   }

   private void fixTimeSeries0(XDimensionRef dref, boolean xtype) {
      /*int level = getDimensionDateLevel(dref);
      boolean dateType = isDateDimension(dref, level);
      boolean outer = isOuterDimRef(dref, xtype);

      if(outer || !dateType) {
         dref.setTimeSeries(false);
      }*/
   }

   /**
    * Judge whether the date type dimension is an outer dimension.
    */
   public boolean isOuterDimension(XDimensionRef ref) {
      return isOuterDimRef(ref, true) || isOuterDimRef(ref, false);
   }

   // @by ChrisSpagnoli bug1422602265526 2015-2-5
   /**
    * Determine whether the data in this chart can be projected forward
    */
   @Override
   public boolean canProjectForward() {
      return canProjectForward(false);
   }

   @Override
   public boolean canProjectForward(boolean rt) {
      // Disallow by chart type:
      // @by ChrisSpagnoli bug1427783978948 2015-4-1
      // @by ChrisSpagnoli bug1428049101562 2015-4-3
      if(getChartType() == GraphTypes.CHART_WATERFALL ||
         getChartType() == GraphTypes.CHART_PARETO ||
         getChartType() == GraphTypes.CHART_PIE ||
         getChartType() == GraphTypes.CHART_DONUT ||
         getChartType() == GraphTypes.CHART_3D_PIE ||
         getChartType() == GraphTypes.CHART_SUNBURST ||
         getChartType() == GraphTypes.CHART_TREEMAP ||
         getChartType() == GraphTypes.CHART_CIRCLE_PACKING ||
         getChartType() == GraphTypes.CHART_ICICLE ||
         getChartType() == GraphTypes.CHART_RADAR ||
         getChartType() == GraphTypes.CHART_FILL_RADAR ||
         getChartType() == GraphTypes.CHART_BOXPLOT ||
         getChartType() == GraphTypes.CHART_SCATTER_CONTOUR ||
         getChartType() == GraphTypes.CHART_FUNNEL ||
         GraphTypes.isRelation(getChartType()) ||
         GraphTypes.isGeo(getChartType()))
      {
         return false;
      }

      if(this instanceof VSChartInfo && ((VSChartInfo) this).isAppliedCustomPeriodsDc()) {
         return false;
      }

      // @by ChrisSpagnoli bug1430357109900 2015-4-30
      // Disallow by "primary" axis data type:
      ChartRef cr = getPrimaryDimension(rt);

      if(cr == null) {
         return false;
      }

      String dataType = cr.getDataType();

      if(cr instanceof XAggregateRef) {
         // @by ClementWang, for bug #7803,
         // keep the same logic with viewsheet, only if a date dimension
         // or number dimension.
         return false;
      }

      if(!dataType.equals("byte") &&
         !dataType.equals("short") &&
         !dataType.equals("integer") &&
         !dataType.equals("long") &&
         !dataType.equals("double") &&
         !dataType.equals("float") &&
         !dataType.equals("date") &&
         !dataType.equals("timeInstant"))
      {
         return false;
      }

      // @by ChrisSpagnoli bug1433742694533 2015-6-9
      // Disallow if "Top" ranking, or ranking level of <= 2
      if(cr instanceof ChartDimensionRef) {
         ChartDimensionRef cdr = (ChartDimensionRef) cr;

         if(!XSchema.isNumericType(dataType) && !XSchema.isDateType(dataType) ||
            cdr.getRankingOption() == AbstractCondition.TOP_N ||
            cdr.getRankingOption() == AbstractCondition.BOTTOM_N)
         {
            return false;
         }

         if(cdr.getRankingOption() != AbstractCondition.NONE && cdr.getRankingN() <= 2) {
            return false;
         }
      }

      // @by ChrisSpagnoli bug1429496186187 2015-4-20
      // Disallow if "drilled-down".
      if(drillLevel > 0) {
         return false;
      }

      // @by ChrisSpagnoli bug1433751789709 2015-6-9
      // Disallow if too many "levels" of sub-chart involved
      ChartRef[] secondaryFields;

      if(isInvertedGraph()) {
         secondaryFields = rt ? getRTXFields() : getXFields();
      }
      else {
         secondaryFields = rt ? getRTYFields() : getYFields();
      }

      long nestLevels = Arrays.stream(secondaryFields)
         .filter(a -> a instanceof DimensionRef).count();

      if(nestLevels >= 3) {
         return false;
      }

      return true;
   }

   @Override
   public boolean replaceField(ChartRef oldFiled, ChartRef newFiled) {
      int index = getIndex(getXFields(), oldFiled);

      if(index >= 0) {
         setXField(index, newFiled);

         return true;
      }

      index = getIndex(getYFields(), oldFiled);

      if(index >= 0) {
         setYField(index, newFiled);

         return true;
      }

      index = getIndex(getGroupFields(), oldFiled);

      if(index >= 0) {
         setGroupField(index, newFiled);

         return true;
      }

      if(isMultiAesthetic()) {
         List<ChartAggregateRef> aAestheticAggregateRefs = getAestheticAggregateRefs(false);

         if(aAestheticAggregateRefs != null) {
            for(ChartAggregateRef ref : getAestheticAggregateRefs(false)) {
               replaceAestheticRefs(ref, oldFiled, newFiled);
            }
         }
      }
      else {
         replaceAestheticRefs(this, oldFiled, newFiled);
      }

      return false;
   }

   private boolean replaceAestheticRefs(ChartBindable aggr, ChartRef oldFiled, ChartRef newFiled) {
      if(aggr == null || oldFiled == null || newFiled == null) {
         return false;
      }

      if(aggr.getColorField() != null &&
         Tool.equals(aggr.getColorField().getFullName(), oldFiled.getFullName()))
      {
         aggr.getColorField().setDataRef(newFiled);

         return true;
      }

      if(aggr.getShapeField() != null &&
         Tool.equals(aggr.getShapeField().getFullName(), oldFiled.getFullName()))
      {
         aggr.getShapeField().setDataRef(newFiled);

         return true;
      }

      if(aggr.getSizeField() != null &&
         Tool.equals(aggr.getSizeField().getFullName(), oldFiled.getFullName()))
      {
         aggr.getSizeField().setDataRef(newFiled);

         return true;
      }

      if(aggr.getTextField() != null &&
         Tool.equals(aggr.getTextField().getFullName(), oldFiled.getFullName()))
      {
         aggr.getTextField().setDataRef(newFiled);

         return true;
      }

      return false;
   }

   private int getIndex(ChartRef[] refs, ChartRef ref) {
      if(refs != null && ref != null) {
         for(int i = 0; i < refs.length; i++) {
            if(refs[i] != null && Tool.equals(refs[i].getFullName(), ref.getFullName())) {
               return i;
            }
         }
      }

      return -1;
   }

   // @by ChrisSpagnoli bug1429507986738 bug1429496186187 2015-4-24
   // track the drill level, in order to suppress project forward.
   private int drillLevel = 0;

   // used by DrillEvent, on a drilldown
   public void incrementDrillLevel() {
      ++drillLevel;
   }

   public void decrementDrillLevel() {
      --drillLevel;
   }


   // used by DrillEvent, after a drillup, to recalculate the drill level
   public void setDrillLevel(int d) {
      drillLevel = d;
   }

   /**
    * Check if this is a special donut chart, where a middle label is added to show total.
    */
   @Override
   public boolean isDonut() {
      return donut;
   }

   /**
    * Set whether this is a special donut chart.
    */
   @Override
   public void setDonut(boolean donut) {
      this.donut = donut;
   }

   @Override
   public boolean isTooltipVisible() {
      return tooltipVisible;
   }

   @Override
   public void setTooltipVisible(boolean tooltipVisible) {
      this.tooltipVisible = tooltipVisible;
   }

   /**
    * Judge whether the date type dimension is an outer dimension.
    */
   private boolean isOuterDimRef(XDimensionRef ref, boolean xtype) {
      if(xtype) {
         for(int i = 0; i < getXFieldCount(); i++) {
            ChartRef ref2 = getXField(i);

            if(!(ref2 instanceof XDimensionRef)) {
               break;
            }

            if(ref.equals(ref2)) {
               return i != getVisibleXFieldCount() - 1;
            }
         }

         for(int i = 0; i < getRTXFields().length; i++) {
            ChartRef ref2 = getRTXFields()[i];

            if(!(ref2 instanceof XDimensionRef)) {
               break;
            }

            if(ref.equals(ref2)) {
               return i != getRTXFields().length - 1;
            }
         }
      }
      else {
         boolean hasMeasure = GraphUtil.getMeasures(getRTXFields()).size() > 0;

         for(int i = 0; i < getYFieldCount(); i++) {
            ChartRef ref2 = getYField(i);

            if(!(ref2 instanceof XDimensionRef)) {
               break;
            }

            if(ref.equals(ref2)) {
               if(i == getYFieldCount() - 1) {
                  int ctype = getChartType();

                  // for candle and stock, dimension in y is a outer dimension
                  return ctype == GraphTypes.CHART_STOCK ||
                     ctype == GraphTypes.CHART_CANDLE ||
                     getXFieldCount() > 0 && !hasMeasure;
               }
               else {
                  return true;
               }
            }
         }

         for(int i = 0; i < getRTYFields().length; i++) {
            ChartRef ref2 = getRTYFields()[i];

            if(!(ref2 instanceof XDimensionRef)) {
               break;
            }

            if(ref.equals(ref2)) {
               if(i == getRTYFields().length - 1) {
                  int ctype = getRTChartType();

                  // for candle and stock, dimension in y is a outer dimension
                  return ctype == GraphTypes.CHART_STOCK ||
                     ctype == GraphTypes.CHART_CANDLE ||
                     getXFieldCount() > 0 && !hasMeasure;
               }
               else {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private int getVisibleXFieldCount() {
      int count = 0;

      for(int i = 0; i < getXFieldCount(); i++) {
         ChartRef ref2 = getXField(i);

         if(!(ref2.isDrillVisible())) {
            continue;
         }

         count++;

      }

      return count;
   }

   @Override
   public String toString() {
      String str = super.toString() + "(" + GraphTypes.getDisplayName(getChartType()) + ",";

      if(xrefs.size() > 0) {
         str += " x: " + xrefs + ";";
      }

      if(yrefs.size() > 0) {
         str += " y: " + yrefs + ";";
      }

      if(grefs.size() > 0) {
         str += " g: " + grefs + ";";
      }

      if(colorField != null && colorField.getDataRef() != null) {
         str += " color: " + colorField.getDataRef() + ";";
      }

      if(shapeField != null && shapeField.getDataRef() != null) {
         str += " shape: " + shapeField.getDataRef() + ";";
      }

      if(sizeField != null && sizeField.getDataRef() != null) {
         str += " size: " + sizeField.getDataRef() + ";";
      }

      return str + ")";
   }

   private static class ListSet extends ArrayList implements Set {
      public ListSet() {
         super();
      }

      public ListSet(Collection c) {
         super(c);
      }

      @Override
      public boolean add(Object e) {
         int idx = indexOf(e);

         if(idx < 0) {
            super.add(e);
         }

         return idx < 0;
      }

      @Override
      public boolean remove(Object o) {
         int idx = indexOf(o);

         if(idx >= 0) {
            super.remove(idx);
         }

         return idx >= 0;
      }

      @Override
      public boolean containsAll(Collection c) {
         if(c == null) {
            return false;
         }

         Iterator ite = c.iterator();

         while(ite.hasNext()) {
            Object obj = ite.next();
            int idx = indexOf(obj);

            if(idx < 0) {
               return false;
            }
         }

         return true;
      }

      @Override
      public boolean addAll(Collection c) {
         if(c == null) {
            return false;
         }

         boolean added = false;

         for(Object obj : c) {
            added = add(obj) || added;
         }

         return added;
      }

      @Override
      public boolean removeAll(Collection c) {
         if(c == null) {
            return false;
         }

         boolean removed = false;

         for(Object obj : c) {
            removed = remove(obj) || removed;
         }

         return removed;
      }

      @Override
      public int indexOf(Object obj) {
         for(int i = 0; i < size(); i++) {
            Object data = get(i);

            if(SubColumns.equalsRef(data, obj)) {
               return i;
            }
         }

         return -1;
      }

      @Override
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         List list = (List) obj;

         for(int i = 0; i < size(); i++) {
            if(!SubColumns.equalsRef(get(i), list.get(i))) {
               return false;
            }
         }

         return true;
      }
   }

   protected AxisDescriptor axisDesc; // primary axis
   protected AxisDescriptor axisDesc2; // secondary axis
   private Map<String, String> localMap;
   private String mapType = "";
   private AggregateInfo ainfo = new AggregateInfo(); // aggregate info
   private int chartType;
   private int rtype; // runtime chart type
   private boolean separated = true;
   private boolean multi = false;
   private boolean isFacet = true;
   private boolean adhoc = true;
   private double unitWidthRatio = 1; // user set ratio
   private double unitHeightRatio = 1;
   private double currWidthRatio = 1; // current display ratio
   private double currHeightRatio = 1;
   private double initialWidthRatio = 1;
   private double initialHeightRatio = 1;
   private boolean widthResized;
   private boolean heightResized;
   private AestheticRef colorField;
   private AestheticRef shapeField;
   private AestheticRef sizeField;
   private AestheticRef textField;
   private ColorFrameWrapper cFrame;
   private SizeFrameWrapper sFrame;
   private ShapeFrameWrapper spFrame;
   private TextureFrameWrapper tFrame;
   private LineFrameWrapper lFrame;
   private List<ChartRef> xrefs = Collections.synchronizedList(new ArrayList<>()); // x refs
   private List<ChartRef> yrefs = Collections.synchronizedList(new ArrayList<>()); // y fields
   private List<ChartRef> grefs = Collections.synchronizedList(new ArrayList<>()); // break-by fields
   private ChartRef[] rxrefs; // runtime x chart refs
   private ChartRef[] ryrefs; // runtime y chart refs
   private ChartRef[] rgrefs; // runtime group refs
   private ChartRef pathRef;
   private boolean aggregated; // boolean aggregated
   private boolean donut = false; // if donut chart (with a middle total label)
   private transient ChartDescriptor chartDescriptor;
   private String customTooltip;
   private boolean tooltipVisible = true;
   private HighlightGroup highlightGroup;
   private HighlightGroup textHL;

   private static final Logger LOG = LoggerFactory.getLogger(AbstractChartInfo.class);
}
