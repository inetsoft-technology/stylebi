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

import inetsoft.graph.LegendSpec;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.*;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.Scale;
import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.filter.DimensionComparer;
import inetsoft.report.filter.MetaTableFilter;
import inetsoft.report.internal.SubColumns;
import inetsoft.uql.XConstants;
import inetsoft.uql.XCube;
import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameContext;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import inetsoft.uql.viewsheet.internal.StandardPeriods;
import inetsoft.util.DefaultComparator;
import inetsoft.util.Tool;

import java.awt.*;
import java.text.Format;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * VSFrame visitor.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class VSFrameVisitor {
   /**
    * Constructor.
    *
    * @param vdata       the full data set without zoom condition.
    * @param initializer
    */
   public VSFrameVisitor(ChartInfo info, DataSet data, VSFrameStrategy strategy,
                         boolean separated, DataSet vdata, String cubeType,
                         String src, int sourceType, FrameInitializer initializer)
   {
      this(info, data, strategy, separated, vdata, cubeType, src, sourceType, null, initializer);
   }

   /**
    * Constructor.
    *
    * @param vdata       the full data set without zoom condition.
    * @param initializer
    */
   public VSFrameVisitor(ChartInfo info, DataSet data, VSFrameStrategy strategy,
                         boolean separated, DataSet vdata, String cubeType,
                         String src, int sourceType, DateComparisonInfo dc,
                         FrameInitializer initializer)
   {
      super();
      this.info = info;
      this.data = data;
      this.vdata = vdata;
      this.strategy = strategy;
      this.separated = separated;
      this.smap = new HashMap<>();
      this.mmap = new HashMap<>();
      this.cubeType = cubeType;
      this.src = src;
      this.sourceType = sourceType;
      this.dc = dc;
      this.initializer = initializer;
      this.frame = createFrame();
   }

   /**
    * Initialize frame with visual dataset.
    */
   public void initFrame(VisualFrame frame, AestheticRef tref) {
      frame.init(getVisualDataSet(frame.getField(), tref, this.data));
   }

   /**
    * Create legend frame.
    */
   private VisualFrame createFrame() {
      AestheticRef tref = strategy.getAestheticRef(null);
      ChartAggregateRef[] arr = getAggregates();
      VisualFrame frame = null;

      // maintain summary properly
      if(arr != null && arr.length > 0) {
         List<String> nlist = new ArrayList<>();

         for(int i = 0; i < arr.length; i++) {
            String name = arr[i].getFullName();

            if(nlist.contains(name)) {
               continue;
            }

            nlist.add(name);
            VisualFrame sframe = strategy.getSummaryFrame(arr[i]);

            if(sframe != null) {
               smap.put(name, sframe);
            }

            if(info.isMultiAesthetic() || GraphTypes.isStack(info.getRTChartType())) {
               AestheticRef mref = info.isMultiAesthetic() ?
                  strategy.getAestheticRef(arr[i]) : strategy.getAestheticRef(null);

               if(mref != null && !mref.isEmpty()) {
                  mmap.put(name, createVisualFrame(mref));
               }
            }
         }
      }

      if(tref != null && !tref.isEmpty()) {
         frame = createVisualFrame(tref);
         setLegendComparator(tref, frame, data);
      }
      else if(strategy.supportsFieldFrame()) {
         if(arr.length > 0) {
            List<String> nlist = new ArrayList<>();
            List<VisualFrame> flist = new ArrayList<>();
            List<String> clist = new ArrayList<>();
            boolean eq = true;
            int cnt = 0;
            int last = -1;

            for(int i = 0; i < arr.length; i++) {
               String name = arr[i].getFullName();
               String caption = XCube.SQLSERVER.equals(cubeType)
                  ? name : GraphUtil.getCaption(arr[i]);

               // fix measure colors if static colors of measures are used.
               if(strategy instanceof VSColorFrameStrategy && !info.isMultiAesthetic()) {
                  GraphUtil.fixDuplicateColor(arr[i], arr, null, true);
               }

               if(nlist.contains(name)) {
                  continue;
               }

               // if the measure has it's own legend, don't include it
               // in the static legend
               if(mmap.containsKey(name)) {
                  continue;
               }

               cnt++;

               VisualFrame frame2 = strategy.getFieldFrame(arr[i]);
               int type = !separated ? arr[i].getRTChartType() : info.getRTChartType();
               final PlotDescriptor plotDescriptor = Optional.ofNullable(info.getChartDescriptor())
                  .map(ChartDescriptor::getPlotDescriptor)
                  .orElse(null);

               if(!GraphTypeUtil.supportsFrame(type, frame2, plotDescriptor)) {
                  continue;
               }

               if(eq && last >= 0) {
                  if(!equaslAggregareRefForValue(arr[last], arr[i])) {
                     eq = false;
                  }
               }

               last = i;
               nlist.add(name);
               flist.add(frame2);
               clist.add(caption);
            }

            if(nlist.size() == 0) {
               return null;
            }

            eq = eq && nlist.size() > 1;
            String[] names = new String[nlist.size()];
            VisualFrame[] frames = new VisualFrame[flist.size()];
            DefaultTextFrame texts = new DefaultTextFrame();

            nlist.toArray(names);
            flist.toArray(frames);
            final boolean force = (cnt > 1 || info.isMultiAesthetic()) && mmap.size() > 0;
            frame = strategy.createCombinedFrame(names, frames, eq, force);
            frame.getLegendSpec().setTextFrame(texts);

            for(int i = 0; i < clist.size(); i++) {
               texts.setText(names[i], clist.get(i));
            }
         }
      }
      else if(strategy.supportsGeneralFrame()) {
         frame = strategy.getGeneralFrame();
      }

      return frame;
   }

   // set the sorting order of aesthetic field to legend.
   public static void setLegendComparator(AestheticRef tref, VisualFrame frame, DataSet data) {
      if(tref.getRTDataRef() instanceof ChartDimensionRef) {
         Comparator comp = ((ChartDimensionRef) tref.getRTDataRef()).createComparator(data);

         // DataSetComparator doesn't work in legend. (57790)
         if(comp != null && !(comp instanceof DataSetComparator)) {
            frame.setComparator(comp);
         }
      }
   }

   /**
    * Get the last chart ref.
    */
   private ChartAggregateRef[] getAggregates() {
      if(GraphTypes.isGantt(info.getChartType())) {
         GanttChartInfo ginfo = (GanttChartInfo) info;
         return getAggregates(ginfo.getRTStartField(), ginfo.getRTMilestoneField());
      }
      else {
         ChartRef[] rxrefs = info.getRTXFields();
         ChartRef[] ryrefs = info.getRTYFields();

         if(containsMeasure(ryrefs)) {
            return getAggregates(ryrefs);
         }
         else {
            return getAggregates(rxrefs);
         }
      }
   }

   /**
    * Get the last chart ref.
    */
   private ChartAggregateRef[] getAggregates(ChartRef... refs) {
      if(refs == null) {
         return new ChartAggregateRef[0];
      }

      List list = new ArrayList();

      for(int i = refs.length - 1; i >= 0; i--) {
         if(refs[i] == null) {
            continue;
         }
         else if(!refs[i].isMeasure()) {
           break;
         }

         list.add(0, refs[i]);
      }

      ChartAggregateRef[] arr = new ChartAggregateRef[list.size()];
      list.toArray(arr);
      return arr;
   }

   private boolean equaslAggregareRefForValue(ChartAggregateRef ref, ChartAggregateRef ref2) {
      if(ref == null || ref2 == null) {
         return false;
      }

      VSChartAggregateRef aggref = (VSChartAggregateRef) ref;
      VSChartAggregateRef aggref2 = (VSChartAggregateRef) ref2;

      return aggref.isVariable() && aggref2.isVariable() &&
         Tool.equals(aggref.getColumnValue(), aggref2.getColumnValue()) ||
         aggref.isScript() && aggref2.isScript() &&
         Tool.equals(aggref.getColumnValue(), aggref2.getColumnValue()) ||
         aggref.equalsContent(aggref2);
   }

   /**
    * Check if contains measure.
    */
   private boolean containsMeasure(ChartRef[] refs) {
      if(refs == null || refs.length == 0) {
         return false;
      }

      return refs[refs.length - 1].isMeasure();
   }

   /**
    * Get the global frame.
    */
   public VisualFrame getFrame() {
      return frame;
   }

   /**
    * Set the global frame.
    */
   public void setFrame(VisualFrame frame) {
      this.frame = frame;
   }

   /**
    * Get the summary frame.
    */
   public VisualFrame getSummaryFrame(String name) {
      return smap.get(GraphUtil.getOriginalCol(name));
   }

   /**
    * Get the per measure frame.
    */
   public VisualFrame getMeasureFrame(String name) {
      return mmap.get(GraphUtil.getOriginalCol(name));
   }

   /**
    * Set the per measure frame.
    */
   public void setMeasureFrame(String name, VisualFrame frame) {
      mmap.put(name, frame);
   }

   /**
    * Get the names of measures that have visual frames.
    */
   public Set<String> getMeasures() {
      return mmap.keySet();
   }

   /**
    * Create legend from for a column.
    */
   private VisualFrame createVisualFrame(AestheticRef tref) {
      VisualFrame frame = tref.getVisualFrame();
      String col = tref.getFullName();
      DataRef ref = tref.getRTDataRef();
      ref = ref != null ? ref : tref.getDataRef();
      int rTOrder = -1;

      if(ref instanceof ChartAggregateRef && ((ChartAggregateRef) ref).isDiscrete()) {
         col = ChartAggregateRef.getBaseName(col);
      }

      frame = (VisualFrame) frame.clone();
      // legendSpec is only set at runtime by graph generator, clear it so no
      // leftovers from last run
      frame.setLegendSpec(new LegendSpec());
      frame.setField(col);
      Format dfmt = null;

      if(initializer != null) {
         initializer.initialize(frame);
      }

      if(!tref.isMeasure()) {
         if(tref.getRTDataRef() instanceof XDimensionRef) {
            XDimensionRef dref = (XDimensionRef) tref.getRTDataRef();
            dref = dref != null ? dref : (XDimensionRef) tref.getDataRef();
            dfmt = getDefaultFormat(dref);
            rTOrder = dref.getOrder();
            dref.setOrder(((XDimensionRef) tref.getDataRef()).getOrder());
         }
      }

      frame.getLegendSpec().getTextSpec().setFormat(dfmt);

      if(col != null && frame instanceof CategoricalColorFrame &&
         ((CategoricalColorFrame) frame).isShareColors())
      {
         String columnName = col;

         if(sourceType == XSourceInfo.MODEL) {
            final String attribute = tref.getAttribute();

            if(attribute != null) {
               columnName = attribute.indexOf(':') > -1 ? attribute.split(":")[1] : attribute;
            }
         }

         // pull colors from other categorical color frames referencing the same column
         final CategoricalColorFrameContext context = CategoricalColorFrameContext.getContext();
         final VisualFrame sharedFrame = context.getSharedFrame(columnName, ref);

         if(sharedFrame != null) {
            frame = (VisualFrame) sharedFrame.clone();
            frame.setField(col);
            tref.setVisualFrame(frame);
         }

         // need to sync for both original shared frame and frames using the shared frame.
         // otherwise the color assignment may be different. (52245)
         syncCategoricalFrame(frame, data, ref, dc, tref.isRuntime());

         final VisualFrame saveFrame = (VisualFrame) frame.clone();
         final Optional<TextFrame> textFrame = Optional.of(saveFrame)
                                                       .map(VisualFrame::getLegendSpec)
                                                       .map(LegendSpec::getTextFrame);

         if(textFrame.isPresent()) {
            final TextFrame tFrame = textFrame.get();
            tFrame.getKeys().clear();
         }

         context.addSharedFrame(columnName, saveFrame, ref);
      }
      else {
         syncCategoricalFrame(frame, data, ref, dc, tref.isRuntime());
      }

      // treemap areas should always be zero based
      if(frame instanceof LinearSizeFrame && GraphTypes.isTreemap(info.getChartType())) {
         frame.setScaleOption(Scale.ZERO);
      }

      if(rTOrder != -1) {
         XDimensionRef dref = (XDimensionRef) tref.getRTDataRef();
         dref = dref != null ? dref : (XDimensionRef) tref.getDataRef();
         dfmt = getDefaultFormat(dref);
         dref.setOrder(rTOrder);
      }

      initFrame(frame, tref);
      syncCategoricalFrame(frame, data, ref, dc, tref.isRuntime());
      frame.getLegendSpec().setTitle(XCube.SQLSERVER.equals(cubeType) &&
                                     tref.isMeasure() ? col : GraphUtil.getCaption(ref));

      return frame;
   }

   private static boolean isMetaData(DataSet data) {
      if(data instanceof DataSetFilter) {
         data = ((DataSetFilter) data).getRootDataSet();
      }

      if(data instanceof VSDataSet) {
         TableLens lens = ((VSDataSet) data).getTable();

         while(lens != null) {
            if(lens instanceof MetaTableFilter) {
               return true;
            }

            lens = lens instanceof TableFilter ?
               ((TableFilter) lens).getTable() : null;
         }
      }

      return false;
   }

   /**
    * Make sure categorical values are initialized with full dataset.
    */
   public static void syncCategoricalFrame(VisualFrame frame, DataSet data) {
      syncCategoricalFrame(frame, data, null, null, false);
   }

   /**
    * Make sure categorical values are initialized with full dataset.
    */
   public static void syncCategoricalFrame(VisualFrame frame, DataSet data,
                                            DataRef aestheticDataRef, DateComparisonInfo dc,
                                            boolean isDcRuntime)
   {
      data = getVisualDataSet(frame.getField(), data);

      if(isMetaData(data)) {
         return;
      }

      // track the categorical model for share
      if(frame instanceof CategoricalColorFrame) {
         if(frame.getField() != null) {
            syncColors((CategoricalColorFrame) frame, data, aestheticDataRef, dc, isDcRuntime);

            // userColors should be explicitly ignored if sync occurs
            frame = (CategoricalColorFrame) frame.clone();
            ((CategoricalColorFrame) frame).clearUserColors();
         }
      }
      else if(frame instanceof CategoricalShapeFrame) {
         if(frame.getField() != null) {
            syncShapes((CategoricalShapeFrame) frame, data, aestheticDataRef);
         }
      }
      else if(frame instanceof CategoricalTextureFrame) {
         if(frame.getField() != null) {
            syncTextures((CategoricalTextureFrame) frame, data, aestheticDataRef);
         }
      }
      else if(frame instanceof CategoricalLineFrame) {
         if(frame.getField() != null) {
            syncLines((CategoricalLineFrame) frame, data, aestheticDataRef);
         }
      }
      else if(frame instanceof CategoricalSizeFrame) {
         if(frame.getField() != null) {
            syncSizes((CategoricalSizeFrame) frame, data, aestheticDataRef);
         }
      }
   }

   /**
    * Get the data for the specifield field.
    */
   private DataSet getVisualDataSet(String field, AestheticRef aref, DataSet data) {
      DataSet dataset = getVisualDataSet(field, data);

      if(aref == null) {
         ChartRef fieldRef = info.getFieldByName(field, true);
         aref = strategy.getAestheticRef(
            fieldRef instanceof ChartAggregateRef ? (ChartAggregateRef) fieldRef : null);
      }

      // if a dimension is bound to both x and color, the sorting order of x dimension will
      // be used in VSDataSet since it comes first, and the aesthetic dimension sorting is
      // ignored. we find and apply the aesthetic dimension sorting here. (43336)
      if(aref != null && aref.getRTDataRef() instanceof ChartDimensionRef) {
         Comparator comp = ((ChartDimensionRef) aref.getRTDataRef()).createComparator(data);
         Comparator comp0 = dataset.getComparator(field);

         if(comp instanceof DimensionComparer && comp0 instanceof DimensionComparer) {
            ((DimensionComparer) comp).setComparator(((DimensionComparer) comp0).getComparator());
         }

         SortedDataSet sorted = new SortedDataSet(dataset, field);
         sorted.setComparator(field, comp);
         return sorted;
      }

      return dataset;
   }

   public static DataSet getVisualDataSet(String field, DataSet data) {
      if(data instanceof IntervalDataSet) {
         data = ((IntervalDataSet) data).getDataSet();
      }

      if(field == null || !(data instanceof VSDataSet)) {
         return data;
      }

      VSDataSet data0 = (VSDataSet) data;
      Set<SubColumns> subcols = data0.getSubColumns();

      if(subcols.size() <= 1) {
         return data;
      }

      List<DataSet> subs = new ArrayList<>();

      for(SubColumns cols : subcols) {
         if(cols.contains(field)) {
            int[] range = data0.getSubRange(cols);
            subs.add(new SubDataSet(data, range[0], range[1]));
         }
      }

      if(subs.size() == 0) {
         // this should never happen
         return data;
      }

      data = subs.get(0);

      for(int i = 1; i < subs.size(); i++) {
         data = new UnionDataSet(data, subs.get(i));
      }

      return data;
   }

   /**
    * Make the frame display the same color as the full data set.
    */
   private static void syncColors(CategoricalColorFrame frame, DataSet data, DataRef aestheticRef,
                                  DateComparisonInfo dc, boolean isDcRuntime)
   {
      frame.init(data);
      String field = getBaseName(frame.getField());
      Scale scale = frame.getScale();
      Object[] vals = scale.getValues();

      if(isDcRuntime && dc != null && dc.getPeriods() instanceof StandardPeriods &&
         dc.getComparisonOption() != DateComparisonInfo.VALUE)
      {
         Date startDate = dc.getStartDate();

         if(startDate != null && vals != null) {
            vals = Arrays.stream(vals)
               .filter(value -> !(value instanceof Date) || Tool.compare(startDate, value) <= 0)
               .toArray(Object[]::new);
         }

      }

      // sort values so the ordering of values are predictable and not
      // dependent on the sorting/grouping of the current binding.
      sortValues(data, field, vals, frame, aestheticRef);

      if(frame.isUseGlobal()) {
         applyGlobalColors(frame);
      }

      syncColors(frame, scale, vals);
   }

   // sync colors
   private static void syncColors(CategoricalColorFrame frame, Scale scale, Object[] vals) {
      Set<Color> staticColors = new HashSet<>();
      final Map<String, Color> dimColors = frame.isUseGlobal() ?
         CategoricalColorFrameContext.getContext().getDimensionColors() : null;

      for(Object val : vals) {
         if(!frame.isStatic(val)) {
            continue;
         }

         Color color = frame.getColor(val);

         // color collision, clear the duplicate color assignment. (61424)
         if(staticColors.contains(color) && (dimColors == null || dimColors.get(Tool.getDataString(val)) == null)) {
            frame.setColor(val, null);
         }
         // track assigned colors.
         else {
            staticColors.add(color);
         }
      }

      for(int i = 0; i < vals.length; i++) {
         double idx = scale.map(vals[i]);

         if(!Double.isNaN(idx)) {
            Color clr = frame.getColor((int) idx);

            if(clr != null && !frame.isStatic(vals[i])) {
               if(staticColors.contains(clr)) {
                  // just leave the val with color collision unassigned and continue.
                  // CategoricalColorFrame will check for color collision and
                  // assign a different color.
                  continue;
               }

               frame.setColor(vals[i], clr);
            }
         }
      }
   }

   private static void applyGlobalColors(CategoricalColorFrame frame) {
      final CategoricalColorFrameContext frameContext = CategoricalColorFrameContext.getContext();
      final Map<String, Color> dimensionColors = frameContext.getDimensionColors();

      /* clearing static means once an explicit color assignment is made, other colors
         can't be synced across charts.
      if(dimensionColors.size() != 0) {
         frame.clearStatic();
      }
      */

      for(Map.Entry<String, Color> entry : dimensionColors.entrySet()) {
         final Color color = entry.getValue();
         frame.setColor(entry.getKey(), new Color(color.getRGB()));
      }
   }

   /**
    * Make the frame display the same shape as the full data set.
    */
   private static VisualFrame syncShapes(CategoricalShapeFrame frame, DataSet data,
                                         DataRef aestheticDataRef)
   {
      frame.init(data);
      Scale scale = frame.getScale();
      Object[] vals = scale.getValues();
      String field = getBaseName(frame.getField());

      // sort values so the ordering of values are predictable and not
      // dependent on the sorting/grouping of the current binding.
      sortValues(data, field, vals, frame, aestheticDataRef);

      if(!syncShapes(frame, scale, vals)) {
         frame.clearStatic();
         syncShapes(frame, scale, vals);
      }

      return frame;
   }

   private static boolean syncShapes(CategoricalShapeFrame frame, Scale scale, Object[] vals) {
      Set<GShape> staticShapes = Arrays.stream(vals)
         .filter(v -> frame.isStatic(v)).map(v -> frame.getShape(v)).collect(Collectors.toSet());

      for(int i = 0; i < vals.length; i++) {
         double idx = scale.map(vals[i]);

         if(!Double.isNaN(idx)) {
            GShape shape = frame.getShape((int) idx);

            if(shape != null && !frame.isStatic(vals[i])) {
               if(staticShapes.contains(shape)) {
                  return false;
               }

               frame.setShape(vals[i], shape);
            }
         }
      }

      return true;
   }

   /**
    * Make the frame display the same texture as the full data set.
    */
   private static VisualFrame syncTextures(CategoricalTextureFrame frame, DataSet data,
                                           DataRef aestheticDataRef)
   {
      frame.init(data);
      Scale scale = frame.getScale();
      Object[] vals = scale.getValues();
      String field = getBaseName(frame.getField());

      // sort values so the ordering of values are predictable and not
      // dependent on the sorting/grouping of the current binding.
      sortValues(data, field, vals, frame, aestheticDataRef);

      if(!syncTextures(frame, scale, vals)) {
         frame.clearStatic();
         syncTextures(frame, scale, vals);
      }

      return frame;
   }

   private static boolean syncTextures(CategoricalTextureFrame frame, Scale scale, Object[] vals) {
      Set<GTexture> staticTextures = Arrays.stream(vals)
         .filter(v -> frame.isStatic(v)).map(v -> frame.getTexture(v)).collect(Collectors.toSet());

      for(int i = 0; i < vals.length; i++) {
         double idx = scale.map(vals[i]);

         if(!Double.isNaN(idx)) {
            GTexture shape = frame.getTexture((int) idx);

            if(shape != null && !frame.isStatic(vals[i])) {
               if(staticTextures.contains(shape)) {
                  return false;
               }

               frame.setTexture(vals[i], shape);
            }
         }
      }

      return true;
   }

   /**
    * Make the frame display the same line as the full data set.
    */
   private static VisualFrame syncLines(CategoricalLineFrame frame, DataSet data,
                                        DataRef aestheticDataRef)
   {
      frame.init(data);
      Scale scale = frame.getScale();
      Object[] vals = scale.getValues();
      String field = getBaseName(frame.getField());

      // sort values so the ordering of values are predictable and not
      // dependent on the sorting/grouping of the current binding.
      sortValues(data, field, vals, frame, aestheticDataRef);

      if(!syncLines(frame, scale, vals)) {
         frame.clearStatic();
         syncLines(frame, scale, vals);
      }

      return frame;
   }

   private static boolean syncLines(CategoricalLineFrame frame, Scale scale, Object[] vals) {
      Set<GLine> staticLines = Arrays.stream(vals)
         .filter(v -> frame.isStatic(v)).map(v -> frame.getLine(v)).collect(Collectors.toSet());

      for(int i = 0; i < vals.length; i++) {
         double idx = scale.map(vals[i]);

         if(!Double.isNaN(idx)) {
            GLine shape = frame.getLine((int) idx);

            if(shape != null && !frame.isStatic(vals[i])) {
               if(staticLines.contains(shape)) {
                  return false;
               }

               frame.setLine(vals[i], shape);
            }
         }
      }

      return true;
   }

   /**
    * Make the frame display the same size as the full data set.
    */
   private static VisualFrame syncSizes(CategoricalSizeFrame frame, DataSet data,
                                        DataRef aestheticDataRef)
   {
      frame.init(data);
      Scale scale = frame.getScale();
      Object[] vals = scale.getValues();
      String field = getBaseName(frame.getField());

      // sort values so the ordering of values are predictable and not
      // dependent on the sorting/grouping of the current binding.
      sortValues(data, field, vals, frame, aestheticDataRef);

      return frame;
   }

   /**
    * Sort the values for visual frame.
    * @param vdata  the target dataset.
    * @param field  the field name of the visual frame.
    * @param vals   the values of the visual frame.
    * @param frame  the target visual frame.
    * @param aestheticDataRef  the target dataref of aesthetic field.
    */
   private static void sortValues(DataSet vdata, String field, Object[] vals, VisualFrame frame,
                                  DataRef aestheticDataRef)
   {
      Comparator comp = null;
      boolean sorted = false;

      // see getVisualDataSet(). (43336)
      if(aestheticDataRef instanceof XDimensionRef) {
         XDimensionRef dim = (XDimensionRef) aestheticDataRef;
         comp = dim.createComparator(vdata);
         // if dim is explicitly not sorted (NONE), keep the original order so the color
         // assignment matches the color legend item order. (60479)
         sorted = dim.getOrder() == XConstants.SORT_NONE;

         if(dim.getNamedGroupInfo() != null) {
            sorted = true;
         }
      }

      if(comp == null) {
         comp = vdata.getComparator(field);
      }

      if(comp == null && !sorted) {
         comp = new DefaultComparator();
      }

      if(comp != null) {
         // needed for sort by value
         comp = DataSetComparator.getComparator(comp, vdata);
         Arrays.sort(vals, comp);
      }

      // order changed, re-init
      if(frame.getScale() instanceof CategoricalScale) {
         ((CategoricalScale) frame.getScale()).init(vals);
      }
   }

   /**
    * Get the default format for a field.
    */
   public static Format getDefaultFormat(XDimensionRef dref) {
      String type = AssetUtil.getOriginalType(dref);

      if(XSchema.isDateType(type) && (dref.getRefType() & DataRef.CUBE) == 0) {
         return XUtil.getDefaultDateFormat(dref.getDateLevel(),
                                           dref.getDataType());
      }

      if(dref.getDates() != null && dref.getDates().length >= 2) {
         return XUtil.getDefaultDateFormat(dref.getDateLevel(), XSchema.DATE);
      }

      return null;
   }

   // strip the DataGroup/ColorGroup/... prefix.
   private static String getBaseName(String name) {
      if(name == null) {
         return "__NULL__";
      }

      return NamedRangeRef.getBaseName(name);
   }

   // initialize visual frame when it's first created.
   public interface FrameInitializer {
      void initialize(VisualFrame frame);
   }

   private final ChartInfo info;
   private final String src;
   private final DataSet data;
   private final DataSet vdata;
   private final VSFrameStrategy strategy;
   private final boolean separated; // flag indicates separate or not
   private VisualFrame frame; // normal frame
   private final Map<String,VisualFrame> smap; // summary frames
   private final Map<String,VisualFrame> mmap; // per measure frames
   private final String cubeType;
   private final int sourceType;
   private final DateComparisonInfo dc;
   private FrameInitializer initializer;
}
