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
import inetsoft.graph.geo.GeoDataSet;
import inetsoft.graph.geo.MappedDataSet;
import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.*;
import inetsoft.graph.internal.*;
import inetsoft.graph.scale.*;
import inetsoft.graph.visual.*;
import inetsoft.report.*;
import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.report.composition.region.*;
import inetsoft.report.filter.*;
import inetsoft.report.internal.*;
import inetsoft.report.internal.graph.*;
import inetsoft.report.internal.table.ParamTableLens;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.web.binding.model.graph.OriginalDescriptor;

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.geom.Point2D;
import java.text.Format;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for graph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class GraphUtil {
   /**
    * The mapped column prefix.
    */
   public static final String MAPPED_HEADER_PREFIX = "_mapped_";
   /**
    * Map empty data land color.
    */
   public static final Color MAP_EMPTY_DATA_COLOR = new Color(242, 239, 233);
   /**
    * Map default land color.
    */
   public static final Color MAP_DEFAULT_COLOR = new Color(251, 210, 154);
   /**
    * Color palette default color before 12.2.
    */
   public static final Color OLD_COLOR_PALETTE_DEFAULT_COLOR = new Color(143, 193, 108);

   /**
    * Check if a ref should treat as dimension.
    */
   public static boolean isDimension(DataRef ref) {
      if(ref instanceof ChartRef) {
         return !((ChartRef) ref).isMeasure();
      }

      return ref instanceof XDimensionRef;
   }

   /**
    * Check if a ref should treat as measure.
    */
   public static boolean isMeasure(DataRef ref) {
      if(ref instanceof ChartRef) {
         return ((ChartRef) ref).isMeasure();
      }

      return ref instanceof XAggregateRef;
   }

   /**
    * Get the color frame.
    */
   public static ColorFrame getColorFrame(EGraph graph) {
      if(graph == null) {
         return null;
      }

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);
         ColorFrame frame = elem.getColorFrame();

         if(frame != null) {
            return frame;
         }
      }

      return null;
   }

   /**
    * Set the color frame.
    */
   public static void setColorFrame(EGraph graph, ColorFrame color) {
      if(graph == null || color == null) {
         return;
      }

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);
         elem.setColorFrame(color);
      }
   }

   /**
    * Get the caption of a data ref.
    */
   public static String getCaption(DataRef ref) {
      if(ref == null) {
         return null;
      }

      String name = getLabel(ref, true);
      String caption = null;

      if(ref instanceof VSAestheticRef) {
         ref = ((VSAestheticRef) ref).getDataRef();
      }

      if(ref instanceof VSDimensionRef) {
         caption = ((VSDimensionRef) ref).getCaption();
      }
      else if(ref instanceof VSAggregateRef) {
         caption = ((VSAggregateRef) ref).getCaption();
      }

      if(caption != null) {
         return caption;
      }

      int open = name.indexOf('(');

      if(open > 0 && name.indexOf(':') > 0 && name.endsWith(")")) {
         int close = name.lastIndexOf(')');
         String name0 = name.substring(open + 1, close);

         name = name.substring(0, open + 1) +
            VSUtil.trimEntity(name0, null) + name.substring(close);
      }

      return (ref instanceof VSDataRef) ? name : ref.getAttribute();
   }

   /**
    * Get the name of a data ref.
    */
   public static String getName(DataRef ref) {
      if(ref instanceof VSDataRef) {
         return ((VSDataRef) ref).getFullName();
      }

      return (ref != null) ? ref.getName() : null;
   }

   /**
    * Get the labelfor ref.
    */
   public static String getLabel(DataRef ref, boolean aggregated) {
      if(ref == null) {
         return null;
      }

      String label = ref.toView();
      String name = getName(ref);

      if(!isChartAggregated(ref)) {
         if(isDiscrete(ref)) {
            label = ChartAggregateRef.getBaseName(name);
         }
         // not aggregated, don't shown as Sum(col)
         else {
            label = name;
         }
      }
      else if(ref instanceof VSChartAggregateRef) {
         DataRef dref = ((VSChartAggregateRef) ref).getDataRef();

         if(VSUtil.isAggregateCalc(dref)) {
            return name;
         }
      }

      return label;
   }

   /**
    * Check if the ref is discrete.
    */
   public static boolean isDiscrete(DataRef ref) {
      if(ref instanceof ChartAggregateRef) {
         return ((ChartAggregateRef) ref).isDiscrete();
      }

      if(ref instanceof VSAestheticRef) {
         return isDiscrete(((VSAestheticRef) ref).getDataRef());
      }

      return false;
   }

   /**
    * Check if the ChartAggregateRef is aggregated.
    */
   private static boolean isChartAggregated(DataRef ref) {
      if(ref instanceof AestheticRef) {
         DataRef temp = ((AestheticRef) ref).getRTDataRef();

         if(temp != null) {
            return isChartAggregated(temp);
         }
      }

      if(ref instanceof ChartAggregateRef) {
         return ((ChartAggregateRef) ref).isAggregateEnabled();
      }

      return false;
   }

   /**
    * Check if the name of the specified data ref and the specified field is
    * equal.
    */
   public static boolean equalsName(DataRef ref, String fld) {
      return Tool.equals(getName(ref), fld);
   }

   /**
    * Parse color from a string.
    */
   public static Color parseColor(String str) throws Exception {
      Color clr = null;

      // 1. The Hex String with "#", for example "#008000".
      // 2. The CSS-Color name, for example "Green"(or "green").
      // 3. The Hex String without "#", for example "008000".
      // NOTE: This same logic is added to the getMapEmptyColor() method.
      if(str.startsWith("#")) {
         clr = Tool.getColorFromHexString(str);
      }
      else {
         clr = Tool.getColorFromHexString(colormap.get(str.toLowerCase().trim()));

         if(clr == null) {
            clr = Tool.getColorFromHexString(str);
         }
      }

      return clr;
   }

   /**
    * Get the User specific color for Map shapes which contain data.
    */
   public static Color getMapDefaultColor() {
      String defaultColor = SreeEnv.getProperty("map.default.color");

      if(defaultColor != null) {
         try {
            // @by ankitmathur, For feature1408091348722, Adding support for
            // three following configuration options for the "map.default.color"
            // and "map.empty.color" properties:
            Color setColor = parseColor(defaultColor);

            if(setColor != null) {
               return setColor;
            }
         }
         catch(Exception ex) {
            LOG.debug("Failed to determine color for set value: " + defaultColor, ex);

            return MAP_DEFAULT_COLOR;
         }
      }

      return MAP_DEFAULT_COLOR;
   }

   /**
    * Get the User specific color for Map shapes which do not contain
    * data.
    */
   public static Color getMapEmptyColor(PlotDescriptor plotDesc) {
      if(plotDesc != null && plotDesc.getEmptyColor() != null) {
         return plotDesc.getEmptyColor();
      }

      String emptyColor = SreeEnv.getProperty("map.empty.color");

      if(emptyColor != null) {
         try {
            Color setColor = parseColor(emptyColor);

            if(setColor != null) {
               return setColor;
            }
         }
         catch(Exception ex) {
            LOG.debug("Failed to determine color for set value: " + emptyColor, ex);

            return MAP_EMPTY_DATA_COLOR;
         }
      }

      return MAP_EMPTY_DATA_COLOR;
   }

   /**
    * Get the User specific color for the Map border.
    */
   public static Color getMapBorderColor() {
      String borderColor = SreeEnv.getProperty("map.border.color");

      if(borderColor != null) {
         try {
            Color setColor = parseColor(borderColor);

            if(setColor != null) {
               return setColor;
            }
         }
         catch(Exception ex) {
            LOG.debug("Failed to determine color for set value: " + borderColor, ex);

            return null;
         }
      }

      return null;
   }

   /**
    * Get the format.
    */
   public static Format getFormat(CompositeTextFormat format) {
      XFormatInfo info = format == null ? null : format.getFormat();

      return getFormat(info);
   }

   /**
    * Get the format.
    */
   public static Format getFormat(XFormatInfo info) {
      if(info != null && !info.isEmpty()) {
         XPrincipal user = (XPrincipal) ThreadContext.getContextPrincipal();
         String locstr = user == null ? null :
            user.getProperty(XPrincipal.LOCALE);
         Locale loc = locstr == null ? null : Catalog.parseLocale(locstr);
         loc = loc != null ? loc : Locale.getDefault();
         return TableFormat.getFormat(info.getFormat(), info.getFormatSpec(), loc);
      }

      return null;
   }

   /**
    * Get the TextSpec from the TextFormat.
    *
    * @param dfmt the specified default format.
    */
   public static TextSpec getTextSpec(CompositeTextFormat format, Format dfmt,
                                      HighlightGroup hgroup)
   {
      if(format == null) {
         if(dfmt != null) {
            TextSpec spec = hgroup != null ? new HLTextSpec(hgroup) : new TextSpec();
            spec.setFormat(dfmt);
            return spec;
         }

         return null;
      }

      TextSpec spec = hgroup != null && !hgroup.isEmpty() ? new HLTextSpec(hgroup) : new TextSpec();

      spec.setAlignment(format.getAlignment());
      spec.setColor(format.getColor());
      spec.setFont(format.getFont());
      spec.setBackground(format.getBackgroundWithAlpha());

      if(format.getRotation() != null) {
         spec.setRotation(format.getRotation().doubleValue());
      }

      Format fmt2 = getFormat(format);
      boolean userDefined = !format.getUserDefinedFormat().getFormat().isEmpty();

      if(fmt2 != null && (userDefined || dfmt == null)) {
         spec.setFormat(fmt2);
      }

      if(spec.getFormat() == null && dfmt != null) {
         spec.setFormat(dfmt);
      }

      return spec;
   }

   /**
    * Get the LegendSpec from the LegendDescriptor.
    */
   public static void setLegendOptions(EGraph graph, LegendsDescriptor legends) {
      graph.setLegendLayout(legends.getLayout());
      setLegendSize(graph, legends);
   }

   /**
    * Set the legend preferred size.
    */
   public static void setLegendSize(EGraph graph, LegendsDescriptor legends) {
      Dimension2D dim = legends.getPreferredSize();

      // not to set default value for dim, for some times content bounds is not
      // null, but legend bounds is null, so if give a default value (20, 20) to
      // the legend bound, will cause legend real size is (20, 20)
      if(dim != null) {
         boolean ver = graph.getLegendLayout() == GraphConstants.TOP ||
            graph.getLegendLayout() == GraphConstants.BOTTOM;

         graph.setLegendPreferredSize(ver ? dim.getHeight() : dim.getWidth());
      }
   }

   /**
    * Fix the legends preferred size to ratio.
    */
   public static void fixLegendsRatio(ChartInfo info, LegendsDescriptor legends, int w, int h) {
      if(legends == null || w <= 0 || h <= 0) {
         return;
      }

      Dimension2D d = legends.getPreferredSize();
      d = fixDimensionFromSizeToRatio(d, w, h);
      legends.setPreferredSize(d);

      for(LegendDescriptor legend : getLegendDescriptors(info, legends, null)) {
         fixLegendRatio(legend, w, h);
      }
   }

   /**
    *
    */
   private static Dimension2D fixDimensionFromSizeToRatio(Dimension2D d, int w, int h) {
      if(d != null && (d.getWidth() > 1 || d.getHeight() > 1)) {
         double dw = d.getWidth() / w;
         dw = (dw > 1) ? 1 : dw;
         double dh = d.getHeight() / h;
         dh = (dh > 1) ? 1 : dh;

         d = new DimensionD(dw, dh);
      }

      return d;
   }

   /**
    * Fix legend position and size to ratio.
    */
   private static void fixLegendRatio(LegendDescriptor desc, int w, int h) {
      if(desc == null || w <= 0 || h <= 0) {
         return;
      }

      Point2D p = desc.getPosition();

      if(p != null && (p.getX() > 1 || p.getY() > 1)) {
         p = new Point2D.Double(Math.min(1, p.getX() / w), Math.min(1, p.getY() / h));
      }

      desc.setPosition(p);
      Dimension2D d = desc.getPreferredSize();
      d = fixDimensionFromSizeToRatio(d, w, h);
      desc.setPreferredSize(d);
   }

   /**
    * Change from screen position to chart space position.
    */
   public static void setPositionSize(VisualFrame frame, LegendDescriptor desc,
                                      LegendsDescriptor legends)
   {
      Point2D pos = (desc != null) ? desc.getPosition() : null;
      Point2D epos = (desc != null) ? desc.getPlotPosition() : null;
      Dimension2D size = (desc != null) ? desc.getPreferredSize() : null;

      GraphUtil.setLegendSpec(frame.getLegendSpec(), legends);
      frame.getLegendSpec().setPreferredSize(size);

      // change to from top of the graph
      if(pos != null) {
         if(size != null) {
            if(pos.getX() > 1 || pos.getY() > 1) {
               pos = new Point2D.Double(pos.getX(), -(pos.getY() + size.getHeight()));
            }
            else if(pos.getX() <= 1 && pos.getY() <= 1 &&
               size.getWidth() <= 1 && size.getHeight() <= 1)
            {
               pos = new Point2D.Double(pos.getX(), 1 - pos.getY() - size.getHeight());
            }
         }
         else if(pos.getX() <= 1 && pos.getY() <= 1 && epos == null) {
            // pos is the position of the top of the legend instead of bottom
            pos = new Point2D.Double(pos.getX(), -pos.getY());
            frame.getLegendSpec().setTopY(true);
         }
      }

      if(epos != null) {
         epos = new Point2D.Double(epos.getX(), -epos.getY());
      }

      frame.getLegendSpec().setPosition(pos);
      frame.getLegendSpec().setPlotPosition(epos);

      // if is Composite frame, should set the position and size to its guide
      // frame
      if(frame instanceof CompositeVisualFrame) {
         VisualFrame gcolor = ((CompositeVisualFrame) frame).getGuideFrame();

         if(gcolor != null) {
            LegendSpec gspec = gcolor.getLegendSpec();
            gspec.setPosition(frame.getLegendSpec().getPosition());
            gspec.setPreferredSize(frame.getLegendSpec().getPreferredSize());
         }
      }
   }

   /**
    * Set the legend spec from descriptor.
    */
   public static void setLegendSpec(LegendSpec spec, LegendsDescriptor legends) {
      CompositeTextFormat format = legends.getTitleTextFormat();
      spec.setBorder(legends.getBorder());
      spec.setBorderColor(legends.getBorderColor());
      spec.setTitleTextSpec(GraphUtil.getTextSpec(format, null, null));
      spec.setPartial(true);
   }

   /**
    * Create view column selection for view side usage, e.g. highlight,
    * hyperlink, etc.
    *
    * @param aggr            the full name of the aggregate ref this columns should be
    *                        associated with (for multi-aesthetics).
    * @param applyDiscrete   whether to apply discrete for aggregate.
    */
   public static ColumnSelection createViewColumnSelection(
      VSChartInfo info, String aggr, boolean applyDiscrete, ChartDescriptor desc)
   {
      ColumnSelection cols = new ColumnSelection();
      Set added = new HashSet();
      VSDataRef[] refs = info.getRTFields();
      refs = (VSDataRef[]) ArrayUtils.addAll(refs, info.getDcTempGroups());
      refs = getGroupFields(info, refs, aggr, desc);

      if(info.getChartType() == GraphTypes.CHART_SUNBURST ||
         info.getChartType() == GraphTypes.CHART_ICICLE)
      {
         boolean found = false;

         for(ChartRef ref : info.getRTGroupFields()) {
            // ignore child labels for parent area in sunburst and icicle
            if(found) {
               added.add(ref.getFullName());
            }
            else if(aggr != null && aggr.equals(ref.getFullName())) {
               found = true;
            }
         }
      }
      else if(info instanceof RelationVSChartInfo) {
         VSDataRef sourceRef = ((RelationVSChartInfo) info).getRTSourceField();
         boolean isSourceCol = aggr.equals(sourceRef.getFullName());

         if(isSourceCol) {
            // facet dimensions
            refs = info.getRTFields(true, false, false, false);
            refs = (VSDataRef[]) ArrayUtils.add(refs, sourceRef);
         }
      }

      for(int i = 0; i < refs.length; i++) {
         VSDataRef ref = refs[i];

         // period column? ignore it
         // Bug #37843. support period column.
//         if(ref instanceof VSDimensionRef) {
//            String[] dates = ((VSDimensionRef) ref).getDates();
//
//            if(dates != null && dates.length > 0) {
//               continue;
//            }
//         }

         String name2 = null;

         if(ref instanceof ChartAggregateRef) {
            ChartAggregateRef tref = (ChartAggregateRef) ref.clone();

            if(!applyDiscrete) {
               tref.setDiscrete(false);
            }

            // if not aggregated, column name won't have the formula. (55021)
            if(!((ChartAggregateRef) ref).isAggregated()) {
               tref.setFormula(AggregateFormula.NONE);
            }

            name2 = tref instanceof VSAggregateRef ?
               ((VSAggregateRef) tref).getFullName2() : tref.getFullName();
         }

         String name = ref instanceof VSAggregateRef ?
            ((VSAggregateRef) ref).getFullName2() : ref.getFullName();
         name2 = name2 == null ? name : name2;

         if(added.contains(name2)) {
            continue;
         }

         added.add(name2);
         ColumnRef column = null;

         if(ref instanceof VSChartDimensionRef) {
            VSChartDimensionRef dim = (VSChartDimensionRef) ref;
            DataRef dataRef = dim.getDataRef();

            // DcRange is a string, so don't set it to date range. (54257)
            if(dataRef instanceof ColumnRef &&
               !(dim.getNamedGroupInfo() instanceof DCNamedGroupInfo) &&
               XSchema.isDateType(dataRef.getDataType()))
            {
               String odtype = dataRef.getDataType();
               dataRef = new DateRangeRef(name, ((ColumnRef) dataRef).getDataRef(),
                                          dim.getDateLevel());
               ((DateRangeRef) dataRef).setOriginalType(odtype);
               column = new ColumnRef(dataRef);
            }
            else if(dataRef instanceof ColumnRef && dim.isNameGroup()) {
               GroupRef gref = dim.createGroupRef(null);
               column = (ColumnRef) gref.getDataRef();
            }
         }

         if(column == null) {
            AttributeRef attr = new AttributeRef(null, name2);
            attr.setRefType(ref.getRefType());
            attr.setDefaultFormula(ref.getDefaultFormula());
            attr.setCaption(getCaption(ref));
            column = new ColumnRef(attr);
         }

         column.setDataType((ref.getRefType() & DataRef.MEASURE) != 0 ?
                               XSchema.DOUBLE : ref.getDataType());

         if(ref instanceof VSDimensionRef) {
            VSDimensionRef dim = (VSDimensionRef) ref;

            if(dim.isDate() && (dim.getDateLevel() & XConstants.PART_DATE_GROUP) != 0) {
               column.setDataType(XSchema.INTEGER);
            }
            else if(dim.isNameGroup() && dim.getDataRef() != null) {
               column.setDataType(XSchema.STRING);
            }

            if(dim.isDateTime()) {
               String locStr = Tool.localize(name);
               column.setView(locStr);
               cols.setProperty("View_" + locStr, locStr);
            }
         }

         // take care: when browsed is true, means in vs we can not support
         // browse data, seems so strange
         boolean browsed = true;

         if(ref instanceof VSAggregateRef) {
            VSAggregateRef aref = (VSAggregateRef) ref;
            browsed = aref.getFormula() != null &&
               !aref.getFormula().equals(AggregateFormula.NONE);
         }
         else if(ref instanceof VSDimensionRef) {
            String type = AssetUtil.getOriginalType(ref);
            browsed = XSchema.isDateType(type);
         }

         // fix bug1256118256831
         cols.setProperty(name, browsed);
         cols.addAttribute(column);
      }

      return cols;
   }

   /**
    * Get the fields in the same group as aggr (for multi-aesthetic).
    */
   private static VSDataRef[] getGroupFields(ChartInfo chartInfo, VSDataRef[] refs, String aggr,
                                             ChartDescriptor desc)
   {
      Map<Set, Set> groups = chartInfo.getRTFieldGroups();

      if(aggr == null || groups.size() <= 1 || GraphTypeUtil.isStackMeasures(chartInfo, desc)) {
         return refs;
      }

      ChartRef aggrRef = chartInfo.getFieldByName(aggr, true);

      for(Set group : groups.keySet()) {
         Set aggrs = groups.get(group);

         if(!aggrs.contains(aggrRef)) {
            continue;
         }

         List<VSDataRef> list = new ArrayList<>();
         list.addAll(group);
         list.addAll(aggrs);
         return list.toArray(new VSDataRef[list.size()]);
      }

      return refs;
   }

   /**
    * Check if the shape frame is nil.
    */
   public static boolean isNil(ChartBindable info) {
      if(info == null || info.getShapeField() != null) {
         return false;
      }

      ShapeFrame shape = info.getShapeFrame();

      return shape instanceof StaticShapeFrame &&
         ((StaticShapeFrame) shape).getShape() == GShape.NIL;
   }

   /**
    * Check if the shape frame contains nil.
    */
   public static boolean containsNil(ChartInfo info) {
      ShapeFrame shape = info.getShapeFrame();

      return shape instanceof StaticShapeFrame &&
         ((StaticShapeFrame) shape).getShape() == GShape.NIL;
   }

   /**
    * Get color frames.
    */
   public static List<VisualFrame> getColorFrames(VisualFrame frame) {
      List<VisualFrame> colors = new ArrayList<>();

      if(!(frame instanceof ColorFrame)) {
         return colors;
      }

      if(frame instanceof CompositeColorFrame) {
         CompositeColorFrame cframe = (CompositeColorFrame) frame;

         for(int i = 0; i < cframe.getFrameCount(); i++) {
            colors.addAll(getColorFrames(cframe.getFrame(i)));
         }
      }
      else {
         colors.add(frame);
      }

      return colors;
   }

   /**
    * Get the summary filter.
    */
   public static SummaryFilter getSummaryFilter(TableLens table) {
      while(table instanceof TableFilter) {
         if(table instanceof SummaryFilter) {
            return (SummaryFilter) table;
         }

         table = ((TableFilter) table).getTable();
      }

      return null;
   }

   /**
    * Make sure the visual frames matches the aesthetic field types.
    *
    * @return true if visual frames are changed.
    */
   public static boolean fixVisualFrames(ChartInfo cinfo) {
      return fixVisualFrames(cinfo, false);
   }

   /**
    * Make sure the visual frames matches the aesthetic field types.
    *
    * @return true if visual frames are changed.
    */
   public static boolean fixVisualFrames(ChartInfo cinfo, boolean rt) {
      boolean rc = false;

      if(cinfo.isMultiAesthetic()) {
         for(ChartAggregateRef aggr : cinfo.getAestheticAggregateRefs(rt)) {
            rc = fixVisualFrames0(aggr, cinfo) || rc;
         }
      }
      else {
         rc = fixVisualFrames0(cinfo, cinfo);
      }

      return rc;
   }

   public static boolean fixVisualFrames0(ChartBindable bindable, ChartInfo cinfo) {
      boolean rc = false;

      rc = GraphUtil.fixVisualFrame(bindable.getColorField(),
                                    ChartConstants.AESTHETIC_COLOR,
                                    bindable.getRTChartType(), cinfo) || rc;
      rc = GraphUtil.fixVisualFrame(bindable.getShapeField(),
                                    ChartConstants.AESTHETIC_SHAPE,
                                    bindable.getRTChartType(), cinfo) || rc;
      rc = GraphUtil.fixVisualFrame(bindable.getSizeField(),
                                    ChartConstants.AESTHETIC_SIZE,
                                    bindable.getRTChartType(), cinfo) || rc;
      rc = GraphUtil.fixVisualFrame(bindable.getTextField(),
                                    ChartConstants.AESTHETIC_TEXT,
                                    bindable.getRTChartType(), cinfo) || rc;

      if(bindable instanceof RelationChartInfo) {
         rc = GraphUtil.fixVisualFrame(((RelationChartInfo) bindable).getNodeColorField(),
                                       ChartConstants.AESTHETIC_COLOR,
                                       bindable.getRTChartType(), cinfo) || rc;
         rc = GraphUtil.fixVisualFrame(((RelationChartInfo) bindable).getNodeSizeField(),
                                       ChartConstants.AESTHETIC_SIZE,
                                       bindable.getRTChartType(), cinfo) || rc;
      }

      return rc;
   }

   /**
    * Fix legend frame of VSAestheticRef.
    */
   public static boolean fixVisualFrame(AestheticRef ref, int type, int chartType, ChartInfo info) {
      if(ref == null) {
         return false;
      }

      VisualFrame frame = ref.getVisualFrameWrapper() == null ? null : ref.getVisualFrame();
      VisualFrame oframe = frame;
      DataRef dref = ref.getDataRef();

      if(type == ChartConstants.AESTHETIC_COLOR) {
         if(isCategorical(dref)) {
            if(!(frame instanceof CategoricalFrame)) {
               String col = dref instanceof VSDimensionRef ?
                  ((VSDimensionRef) dref).getGroupColumnValue() : null;
               boolean foundSharedColors = false;

               // share colors is true by default so use share colors if they exist for this field
               if(col != null) {
                  String columnName = col;

                  if(col.indexOf(':') > -1 && !col.endsWith(":")) {
                     columnName = col.split(":")[1];
                  }

                  // pull colors from other categorical color frames referencing the same column
                  final CategoricalColorFrameContext context =
                     CategoricalColorFrameContext.getContext();
                  final VisualFrame sharedFrame = context.getSharedFrame(columnName, dref);

                  if(sharedFrame != null) {
                     frame = (VisualFrame) sharedFrame.clone();
                     frame.setField(col);
                     foundSharedColors = true;
                  }
               }

               if(!foundSharedColors) {
                  frame = new CategoricalColorFrame();
               }
            }
            else if(!(frame instanceof CategoricalColorFrame)) {
               frame = new CategoricalColorFrame();
            }
         }
         else if(dref instanceof XAggregateRef) {
            if(!(frame instanceof LinearColorFrame)) {
               frame = new BluesColorFrame();
            }
         }
      }
      else if(type == ChartConstants.AESTHETIC_SHAPE) {
         if(isCategorical(dref)) {
            if(GraphTypes.supportsPoint(chartType, info)) {
               if(!(frame instanceof CategoricalShapeFrame)) {
                  frame = new CategoricalShapeFrame();
               }
            }
            else if(GraphTypes.supportsTexture(chartType)) {
               if(!(frame instanceof CategoricalTextureFrame)) {
                  frame = new CategoricalTextureFrame();
               }
            }
            else if(GraphTypes.supportsLine(chartType, info)) {
               if(!(frame instanceof CategoricalLineFrame)) {
                  frame = new CategoricalLineFrame();
               }
            }
         }
         else if(dref instanceof XAggregateRef) {
            if(GraphTypes.supportsPoint(chartType, info)) {
               if(!(frame instanceof LinearShapeFrame)) {
                  frame = new FillShapeFrame();
               }
            }
            // texture
            else if(GraphTypes.supportsTexture(chartType)) {
               if(!(frame instanceof LinearTextureFrame)) {
                  frame = new LeftTiltTextureFrame();
               }
            }
            // line
            else if(GraphTypes.supportsLine(chartType, info)) {
               if(!(frame instanceof LinearLineFrame)) {
                  frame = new LinearLineFrame();
               }
            }
         }
      }
      else if(type == ChartConstants.AESTHETIC_SIZE) {
         if(isCategorical(dref)) {
            if(!(frame instanceof CategoricalSizeFrame)) {
               frame = new CategoricalSizeFrame();
            }
         }
         else if(dref instanceof XAggregateRef) {
            if(!(frame instanceof LinearSizeFrame)) {
               // need LinearSizeFrameWrapper frame to handle brushing. (49779)
               frame = new LinearSizeFrameWrapper().getVisualFrame();
            }
         }
      }
      else if(type == ChartConstants.AESTHETIC_TEXT) {
         if(!(frame instanceof DefaultTextFrame)) {
            frame = new DefaultTextFrame();
         }
      }

      if(oframe != frame) {
         ref.setVisualFrame(frame);
         return true;
      }

      return false;
   }

   /**
    * Fix static color frame of VSAestheticRef and get the color from
    * CategoricalColorFrame.COLOR_PALETTE, when insert measure.
    * If replace an existing field, don't thinks about the static color
    * in that field
    *
    * @param ref the object which should be fixed.
    */
   public static void fixStaticColorFrame(Object ref, ChartInfo info, ChartAggregateRef movedRef) {
      StaticColorFrameWrapper temp;

      if(ref instanceof List<?>) {
         List<?> vref = (List<?>) ref;
         Set<Color> usedColors = new HashSet<>();
         Color[] clrs = CategoricalColorFrame.COLOR_PALETTE;

         for(int i = 0; i < vref.size(); i++) {
            if(GraphUtil.isDimension((DataRef) vref.get(i))) {
               continue;
            }

            temp = (StaticColorFrameWrapper) ((ChartAggregateRef) vref.get(i)).getColorFrameWrapper();
            int idx = getColorIndex(usedColors, clrs, i);
            usedColors.add(clrs[idx]);
            temp.setDefaultColor(clrs[idx]);
         }
      }
      else if(ref instanceof ChartAggregateRef) {
         fixDuplicateColor((ChartAggregateRef) ref, info != null ? info.getModelRefs(false) : null,
                           movedRef, false);
      }
   }

   public static void fixDuplicateColor(ChartAggregateRef ref, ChartRef[] arefs,
                                        ChartAggregateRef movedRef, boolean runtimeUpdate)
   {
      StaticColorFrameWrapper frame;
      Set<Color> usedColors = new HashSet<>();
      Color[] clrs = CategoricalColorFrame.COLOR_PALETTE;
      int aggrIdx = 0;

      if(arefs != null) {
         aggrIdx = arefs.length;

         for(int i = 0; i < arefs.length; i++) {
            ChartAggregateRef aref = (ChartAggregateRef) arefs[i];
            boolean sameRef = Tool.equals(aref, movedRef);
            sameRef = sameRef && aref.equalsContent(movedRef);
            frame = (StaticColorFrameWrapper) aref.getColorFrameWrapper();

            if(ref != null && aref.getFullName().equals(ref.getFullName())) {
               aggrIdx = i;
            }

            // if checking for dup on existing binding(not dnd), only check the previous
            // aggregates and not the subsequent (they will be checked later). (61828)
            if(ref == arefs[i]) {
               continue;
            }
            else if(sameRef) {
               continue;
            }

            if(frame.getUserColor() != null) {
               usedColors.add(frame.getColor());
            }
            else {
               usedColors.add(frame.getDefaultColor());
            }
         }
      }

      frame = (StaticColorFrameWrapper) ref.getColorFrameWrapper();

      if(usedColors.isEmpty() && frame.getColor().equals(OLD_COLOR_PALETTE_DEFAULT_COLOR)) {
         return;
      }

      if(runtimeUpdate && !usedColors.contains(frame.getDefaultColor())) {
         return;
      }

      int idx = getColorIndex(usedColors, clrs, aggrIdx);
      frame.setDefaultColor(new Color(clrs[idx].getRGB()));
   }

   /**
    * Get the new color which is not occupied.
    */
   private static int getColorIndex(Set<Color> usedColors, Color[] colors, int aggrIdx) {
      for(int i = 0; i < colors.length; i++) {
         if(!usedColors.contains(colors[i])) {
            return i;
         }
      }

      // cycling through the color palette if colors run out instead of assign the 1st color.
      return aggrIdx % colors.length;
   }

   /**
    * Get the last chart ref.
    */
   public static ChartRef getLastField(ChartRef[] refs) {
      return refs == null || refs.length == 0 ? null : refs[refs.length - 1];
   }

   /**
    * Get the aggregate formula for the column.
    */
   public static Formula getFormula(DataSet data, String col) {
      while(data instanceof GeoDataSet || data instanceof MappedDataSet) {
         data = ((AbstractDataSetFilter) data).getDataSet();
      }

      if(data instanceof VSDataSet && col != null && col.length() > 0) {
         VSDataRef ref = ((VSDataSet) data).getDataRef(col);

         if(ref == null) {
            return null;
         }

         if(ref instanceof XAggregateRef && (ref.getRefType() & DataRef.CUBE) == 0) {
            if(!((XAggregateRef) ref).isAggregateEnabled()) {
               return null;
            }

            AggregateFormula f1 = ((XAggregateRef) ref).getFormula();

            if(f1 == null) {
               return null;
            }

            // aggregate calc field, we don't know how the aggregation is done so assuming
            // sum is most reasonable. (49730)
            if(f1 == AggregateFormula.NONE &&
               ((XAggregateRef) ref).getDataRef() instanceof CalculateRef &&
               !((CalculateRef) ((XAggregateRef) ref).getDataRef()).isBaseOnDetail())
            {
               return new SumFormula();
            }

            String fname = f1.getFormulaName();

            if(f1.isTwoColumns()) {
               DataRef ref2 = ((XAggregateRef) ref).getSecondaryColumn();
               int idx2 = -1;
               String refName = null;

               if(ref2 instanceof VSDataRef) {
                  refName = ((VSDataRef) ref2).getFullName();
               }
               else if(ref2 != null) {
                  refName = ref2.getAttribute();
               }

               idx2 = data.indexOfHeader(refName);

               if(idx2 < 0) {
                  try {
                     idx2 = getDateRangeColumn(data, refName);
                  }
                  catch(Exception ignore) {
                  }
               }

               if(idx2 >= 0) {
                  fname += "(" + idx2 + ")";
               }
               // for correlaction/covariance/weightedAvg, if the secondary
               // column is not found, using the average for calculation the
               // aggregate of aggregate. This is for sorting a group in graph
               // where it is really applied on the aggregated values.
               else {
                  return new AverageFormula();
               }
            }

            try {
               return Util.createFormula(null, fname);
            }
            catch(Exception ex) {
               LOG.warn("Failed to create formula: " + fname, ex);
            }
         }
      }

      return new SumFormula();
   }

   /**
    * Get the date column from a base column name. For example, if the basename is 'OrderDate',
    * any date range column (e.g. Day(OrderDate)) would match as a date column.
    */
   private static int getDateRangeColumn(DataSet dataset, String basename) {
      String postfix = "(" + basename + ")";

      for(int i = 0; i < dataset.getColCount(); i++) {
         String header = dataset.getHeader(i);

         if(header.endsWith(postfix)) {
            String prefix = header.substring(0, header.lastIndexOf('('));

            if(DateRangeRef.getDateRangeOption(prefix) >= 0) {
               return i;
            }
         }
      }

      return -1;
   }

   /**
    * Get the items of a legend.
    *
    * @return values as map from value to value string.
    */
   public static Map<String, String> getLegendItems(String type, String field,
                                                    List<String> targetFields,
                                                    ChartArea area)
   {
      LegendArea[] legends = area.getLegendsArea().getLegendAreas();
      Map<String, String> values = new HashMap<>();

      for(LegendArea legend : legends) {
         if(!legend.getAestheticType().equals(type) ||
            !Tool.equals(legend.getField(), field) ||
            !legend.getTargetFields().equals(targetFields))
         {
            continue;
         }

         LegendContentArea content = legend.getContent();

         if(!(content instanceof ListLegendContentArea)) {
            continue;
         }

         DefaultArea[] items = content.getAllAreas();

         for(DefaultArea item : items) {
            if(item instanceof LegendItemArea) {
               LegendItemArea item2 = (LegendItemArea) item;
               values.put(item2.getValue(), item2.getValueText());
            }
         }
      }

      return values;
   }

   /**
    * Get the items of an axis.
    *
    * @return values as map from value to value stirng.
    */
   public static String[][] getAxisItems(String col, ChartArea area) {
      VDimensionLabel[] vlabels = area.getVDimensionLabels();
      List<String[]> values = new ArrayList<>();

      for(int i = 0; i < vlabels.length; i++) {
         VDimensionLabel label = vlabels[i];

         if(Tool.equals(label.getDimensionName(), col)) {
            String lb = label.getLabel() == null ? "" : Tool.getDataString(label.getLabel());
            String val = label.getValue() == null ? null : Tool.getDataString(label.getValue());
            values.add(new String[] { lb, val });
         }
      }

      return values.toArray(new String[0][]);
   }

   /**
    * Get polygon field.
    *
    * @param info map info.
    */
   public static GeoRef[] getPolygonFields(MapInfo info) {
      List<GeoRef> list = new ArrayList<>();
      ChartRef[] refs = info.getRTGeoFields().length > 0
         ? info.getRTGeoFields() : info.getGeoFields();

      for(ChartRef ref : refs) {
         GeoRef geoRef = (GeoRef) ref;

         if(geoRef != null) {
            GeographicOption geographicOption = geoRef.getGeographicOption();

            if(geographicOption != null && !MapData.isPointLayer(geographicOption.getLayer())) {
               list.add(geoRef);
            }
         }
      }

      GeoRef[] geoRefs = new GeoRef[list.size()];
      list.toArray(geoRefs);

      return geoRefs;
   }

   /**
    * Get polygon field.
    *
    * @param info map info.
    */
   public static GeoRef getPolygonField(MapInfo info) {
      GeoRef[] flds = getPolygonFields(info);

      return flds.length > 0 ? flds[0] : null;
   }

   /**
    * Get point fields.
    *
    * @param info map info.
    */
   public static GeoRef[] getPointFields(MapInfo info) {
      List<GeoRef> list = new ArrayList<>();
      ChartRef[] refs = info.getRTGeoFields().length > 0
         ? info.getRTGeoFields() : info.getGeoFields();

      for(ChartRef ref : refs) {
         if(ref instanceof GeoRef) {
            GeoRef geoRef = (GeoRef) ref;
            int layer = geoRef.getGeographicOption().getLayer();

            if(MapData.isPointLayer(layer)) {
               list.add(geoRef);
            }
         }
      }

      GeoRef[] geoRefs = new GeoRef[list.size()];
      list.toArray(geoRefs);

      return geoRefs;
   }

   /**
    * Check if the chart contains map point field.
    *
    * @param info map info.
    */
   public static boolean containsMapPointField(ChartInfo info) {
      if(!GraphTypeUtil.isMap(info)) {
         return false;
      }

      if(!getMeasures(info.getXFields()).isEmpty() && !getMeasures(info.getYFields()).isEmpty()) {
         return true;
      }

      return getPointFields((MapInfo) info).length > 0;
   }

   /**
    * Check if the chart contains map point element.
    *
    * @param info map info.
    */
   public static boolean containsMapPoint(ChartInfo info) {
      return containsMapPointField(info) || containsAnchorPoint(info);
   }

   /**
    * Check if the chart contains map polygon field.
    */
   public static boolean containsMapPolygonField(ChartInfo info) {
      if(!GraphTypeUtil.isMap(info)) {
         return false;
      }

      return getPolygonField((MapInfo) info) != null;
   }

   /**
    * Check if the chart contains map polygon element.
    */
   public static boolean containsMapPolygon(ChartInfo info) {
      return containsMapPolygonField(info);
   }

   /**
    * Get the measures in the field list.
    */
   public static List<XAggregateRef> getMeasures(ChartRef[] refs) {
      ArrayList<XAggregateRef> list = new ArrayList<>();

      for(ChartRef ref : refs) {
         if(ref instanceof ChartAggregateRef && !((ChartAggregateRef) ref).isDiscrete()) {
            list.add((XAggregateRef) ref);
         }
      }

      return list;
   }

   /**
    * Check if the info support size aesthetic.
    * Get the geo ref from map info determined by ref name.
    */
   public static GeoRef getRTGeoRefByName(MapInfo info, String name) {
      for(ChartRef ref : info.getRTGeoFields()) {
         if(name.equals(ref.getName())) {
            return (GeoRef) ref;
         }
      }

      return null;
   }

   public static GeoRef getGeoRefByName(MapInfo info, String name) {
      for(ChartRef ref : info.getGeoFields()) {
         if(name.equals(ref.getName())) {
            return (GeoRef) ref;
         }
      }

      return null;
   }

   /**
    * Check if the field is latitude field.
    */
   public static boolean isLatitudeField(String field) {
      String postfix = GeoDataSet.getLatitudeField("");

      return field != null && field.endsWith(postfix);
   }

   /**
    * Check if the field is latitude field.
    */
   public static boolean isLongitudeField(String field) {
      String postfix = GeoDataSet.getLongitudeField("");

      return field != null && field.endsWith(postfix);
   }

   /**
    * Get geo field by latitude or longitude field name.
    */
   public static String getGeoField(String field) {
      if(field == null) {
         return null;
      }

      String prefix = MAPPED_HEADER_PREFIX;
      String postfix = GeoDataSet.getLatitudeField("");
      int sidx = field.startsWith(prefix) ? prefix.length() : 0;
      int eidx = field.endsWith(postfix) ? field.length() - postfix.length() : -1;

      if(eidx == -1) {
         postfix = GeoDataSet.getLongitudeField("");
         eidx = field.endsWith(postfix) ? field.length() - postfix.length() :
            field.length();
      }

      return field.substring(sidx, eidx);
   }

   /**
    * Check if mapped head.
    */
   public static boolean isMappedHeader(String header) {
      return header != null && header.startsWith(MAPPED_HEADER_PREFIX);
   }

   /**
    * Get geo field by name.
    */
   public static GeoRef getGeoFieldByName(ChartInfo info, String refName) {
      if(!(info instanceof MapInfo)) {
         return null;
      }

      MapInfo minfo = (MapInfo) info;
      ChartRef[] gflds = minfo.getRTGeoFields();

      for(int i = 0; i < gflds.length; i++) {
         GeoRef gfld = (GeoRef) gflds[i];

         if(refName.equals(gfld.getName())) {
            return gfld;
         }

         if(gfld instanceof ChartDimensionRef) {
            DataRef group = gfld.getDataRef();

            if(group != null && refName.equals(group.getName())) {
               return gfld;
            }
         }
      }

      return null;
   }

   /**
    * Check if the specified info contains anchor point.
    */
   public static boolean containsAnchorPoint(ChartInfo info) {
      if(!(info instanceof MapInfo)) {
         return false;
      }

      MapInfo minfo = (MapInfo) info;

      return containsMapPolygonField(minfo) && !containsMapPointField(minfo) &&
         (!isNil(minfo) || minfo.getRTPathField() != null);
   }

   /**
    * Check if the specified info supports nil.
    */
   public static boolean supportsNil(ChartAggregateRef aggr, ChartInfo info) {
      if(info == null) {
         return false;
      }

      if(info instanceof MapInfo) {
         return GraphUtil.containsMapPolygonField(info) &&
            !GraphUtil.containsMapPointField(info) &&
            info.getShapeFrame() instanceof StaticShapeFrame &&
            info.getShapeField() == null;
      }

      Object shapeFrame = (aggr != null) ? aggr.getShapeFrame()
         : info.getShapeFrame();
      Object shapeField = (aggr != null) ? aggr.getShapeField()
         : info.getShapeField();
      Object textField = (aggr != null) ? aggr.getTextField()
         : info.getTextField();

      // point with text as mark (no shape)
      return shapeFrame instanceof StaticShapeFrame &&
         shapeField == null && textField != null;
   }

   /**
    * Check if the specified info contains polygon but does not contain point.
    */
   public static boolean containsOnlyPolygon(ChartInfo info) {
      return containsMapPolygon(info) && !containsMapPoint(info);
   }

   /**
    * Check if the specified info contains point but does not contain polygon.
    */
   public static boolean containsOnlyPoint(ChartInfo info) {
      return !containsMapPolygon(info) && containsMapPoint(info);
   }

   /**
    * Check if the specified info contains explicit point but does not contain
    * geo field point and polygon.
    */
   public static boolean containsOnlyExpPoint(MapInfo info) {
      return !containsMapPolygon(info) && containsMapPoint(info) &&
         getPointFields(info).length == 0;
   }

   /**
    * Draw no data label.
    */
   public static void drawNoDataLabel(Graphics g, VGraph vgraph, ChartInfo info)
   {
      DataSet data = vgraph.getCoordinate().getDataSet();
      VSDataRef[] flds = (info == null) ? new VSDataRef[0] : info.getRTFields();

      if(data instanceof GeoDataSet && flds.length == 0) {
         FontMetrics fm = g.getFontMetrics();
         String label = Catalog.getCatalog().getString("No Data");
         int strw = fm.stringWidth(label);
         int strh = fm.getHeight();
         double w = vgraph.getBounds().getWidth();
         double h = vgraph.getBounds().getHeight();
         int strx = (int) (w - strw) / 2;
         int stry = (int) (h / 2 - fm.getAscent());

         g.setColor(new Color(255, 255, 255, 204));
         g.fillRoundRect(strx, stry, strw, strh, strh / 2, strh / 2);

         g.setColor(Color.black);
         g.drawString(label, strx, stry + fm.getAscent());
      }
   }

   /**
    * Get the specified header index.
    */
   public static int indexOfHeader(DataSet source, String header) {
      if(source == null) {
         return -1;
      }

      int idx = source.indexOfHeader(header);

      if(idx == -1 && header.contains(".")) {
         int sidx = header.lastIndexOf('.');
         header = header.substring(sidx + 1);
         idx = source.indexOfHeader(header);
      }

      if(idx == -1 && source instanceof VSDataSet) {
         TableLens base = ((VSDataSet) source).getTable();

         while(base instanceof TableFilter) {
            base = ((TableFilter) base).getTable();
         }

         for(int i = 0; i < base.getColCount(); i++) {
            if(header.equals(XUtil.getHeader(base, i).toString())) {
               return i;
            }
         }
      }

      return idx;
   }

   /**
    * Check if contains the specified date level.
    */
   public static Boolean containsDateLevelValue(Object obj, ChartRef[] refs,
                                                int level)
   {
      if(level < 0) {
         return false;
      }

      for(int i = 0; i < refs.length; i++) {
         if(!(refs[i] instanceof XDimensionRef)) {
            continue;
         }

         if(!((XDimensionRef) obj).getName().equals(
            ((XDimensionRef) refs[i]).getName()))
         {
            continue;
         }

         int nlevel = ((XDimensionRef) refs[i]).getDateLevel();

         if(nlevel == level) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the next date grouping level for a new date ref.
    *
    * @param obj     the target dimension to get next date level.
    * @param objName the dataref name of the target dimension, add this arguments
    *                to avoid the dimension dataref is temporay empty(e.g. create the
    *                VSChartDimensionRef by dnd in web).
    * @param refs    the dimension refs in chart binding.
    * @param idx     the index of the target dimension.
    */
   public static int getNextDateLevelValue(Object obj, String objName,
                                           List<?> refs, int idx)
   {
      int level = ((XDimensionRef) obj).getDateLevel();
      int level2 = -1;

      if(refs != null && objName != null) {
         for(int i = 0; i < idx && i < refs.size(); i++) {
            if(refs.get(i) instanceof XDimensionRef) {
               if(objName.equals(((XDimensionRef) refs.get(i)).getName())) {
                  level2 = ((XDimensionRef) refs.get(i)).getDateLevel();
               }
            }
         }
      }

      if(refs != null && level2 < 0) {
         return level;
      }

      int level0 = getNextDateLevel(level2 < 0 ? level : level2);
      String type = ((XDimensionRef) obj).getDataType();

      if(XSchema.DATE.equals(type) &&
         level0 == DateRangeRef.HOUR_OF_DAY_PART)
      {
         return level;
      }

      if(level0 >= 0) {
         return level0;
      }

      if(XSchema.TIME.equals(type)) {
         return DateRangeRef.HOUR_INTERVAL;
      }

      return DateRangeRef.YEAR_INTERVAL;
   }

   /**
    * Get the next date grouping level for a new date ref.
    */
   public static int getNextDateLevelValue(Object obj, List<?> refs, int idx) {
      return getNextDateLevelValue(obj, ((XDimensionRef) obj).getName(), refs, idx);
   }

   /**
    * Find the date group with the next level of detail.
    */
   public static int getNextDateLevel(int level) {
      for(int i = 0; i < dateLevel.length; i++) {
         if(dateLevel[i][0] == level) {
            return dateLevel[i][1];
         }
      }

      return -1;
   }

   /**
    * Find the date group with the lower level of detail for drilling.
    */
   public static int getDrillDownDateLevel(int level) {
      for(int i = 0; i < drillLevel.length; i++) {
         if(drillLevel[i][0] == level) {
            return drillLevel[i][1];
         }
      }

      return -1;
   }

   /**
    * Find the date group with the higher level of detail for drilling.
    */
   public static int getDrillUpDateLevel(int level) {
      for(int i = 0; i < drillLevel.length; i++) {
         if(drillLevel[i][1] == level) {
            return drillLevel[i][0];
         }
      }

      return -1;
   }

   /**
    * Get the date level ranking which can be used to compare the detail
    * level of the date grouping.
    */
   public static int getDateLevelRanking(int level) {
      Integer val = dateRank.get(level);
      return (val == null) ? 0 : val;
   }

   /**
    * Check if the specified field is polygon.
    */
   public static boolean isPolygonField(MapInfo info, String field) {
      GeoRef[] refs = getPolygonFields(info);

      for(int i = 0; i < refs.length; i++) {
         if(field.equals(refs[i].getName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Process on vgraph to add underline for hyperlink text.
    */
   public static void processHyperlink(ChartInfo info, VGraph evgraph, DataSet data) {
      if(info == null || data == null) {
         return;
      }

      if(!"true".equals(SreeEnv.getProperty("hyperlink.indicator"))) {
         return;
      }

      List<?> vec = GTool.getVOs(evgraph);

      for(int i = 0; i < vec.size(); i++) {
         VisualObject vo = (VisualObject) vec.get(i);

         if(vo instanceof ElementVO) {
            if(!hasHyperlink((ElementVO) vo, evgraph)) {
               continue;
            }

            VOText[] texts = ((ElementVO) vo).getVOTexts();

            for(int j = 0; j < texts.length; j++) {
               VOText vtext = texts[j];

               if(vtext == null) {
                  continue;
               }

               TextSpec spec = vtext.getTextSpec();
               spec = (TextSpec) spec.clone();
               vtext.setTextSpec(spec);
               StyleFont font = new StyleFont(vtext.getFont());
               font = (StyleFont) font.deriveFont(font.getStyle() | StyleFont.UNDERLINE);
               vtext.getTextSpec().setFont(font);
            }
         }
      }

      // fix dimension hyperlink text format
      Coordinate coord = evgraph.getCoordinate();
      Axis[] tops = coord.getAxesAt(ICoordinate.TOP_AXIS);
      Axis[] bottoms = coord.getAxesAt(ICoordinate.BOTTOM_AXIS);
      Axis[] lefts = coord.getAxesAt(ICoordinate.LEFT_AXIS);
      Axis[] rights = coord.getAxesAt(ICoordinate.RIGHT_AXIS);
      Axis[] radarAxes = new Axis[0];

      if(coord instanceof PolarCoord) {
         radarAxes = coord.getAxes(false);
      }

      // hyperlink not allowed on top axis of mekko, same as gui. (49677)
      if(GraphTypes.isMekko(info.getChartType())) {
         tops = new Axis[0];
      }

      Axis[][] axes = { tops, bottoms, lefts, rights, radarAxes };

      for(int i = 0; i < axes.length; i++) {
         Axis[] axis = axes[i];

         for(int j = 0; j < axis.length; j++) {
            Scale scale = axis[j].getScale();

            if(scale instanceof LinearScale) {
               continue;
            }

            String[] flds = scale.getFields();
            String field = flds == null || flds.length <= 0 ? null : flds[0];
            DataRef ref = getChartRef(info, field, false, axis[j] instanceof PolarAxis);

            if(ref instanceof HyperlinkRef) {
               HyperlinkRef dim = (HyperlinkRef) ref;
               Hyperlink hyper = dim.getHyperlink();

               if(hyper != null && isHyperlinkValid(hyper, data) || containsDrill(data, field)) {
                  VLabel[] lbs = axis[j].getLabels();

                  for(int k = 0; k < lbs.length; k++) {
                     TextSpec spec = lbs[k].getTextSpec();
                     spec = (TextSpec) spec.clone();
                     lbs[k].setTextSpec(spec);
                     StyleFont font = null;

                     if(lbs[k].getFont() instanceof StyleFont) {
                        font = (StyleFont) lbs[k].getFont();
                     }
                     else {
                        font = new StyleFont(lbs[k].getFont());
                     }

                     lbs[k].setFont(font.deriveFont(font.getStyle() | StyleFont.UNDERLINE));
                  }
               }
            }
         }
      }
   }

   // check if hyperlink parameters exist in dataset
   private static boolean isHyperlinkValid(Hyperlink link, DataSet data) {
      return link.getParameterNames().stream()
         .allMatch(name -> data.indexOfHeader(link.getParameterField(name)) >= 0 ||
            link.isParameterHardCoded(name));
   }

   /**
    * Check if has hyperlink.
    */
   private static boolean hasHyperlink(ElementVO elemVO, VGraph graph) {
      HRef ref = null;
      HRef[] refs = null;

      DataSet data = graph.getCoordinate().getDataSet();
      data = data instanceof DataSetFilter ?
         ((DataSetFilter) data).getRootDataSet() : data;

      if(data instanceof AttributeDataSet) {
         // Word cloud chart should also be included to judge has hyperlink.
         String measureName = GraphUtil.getHyperlinkMeasure(elemVO, true);
         ref = elemVO.getMeasureName() == null ? null :
            ((AttributeDataSet) data).getHyperlink(measureName, elemVO.getRowIndex());
         refs = elemVO.getMeasureName() == null ? null :
            ((AttributeDataSet) data).getDrillHyperlinks(measureName, elemVO.getRowIndex());
      }

      return ref != null || refs != null && refs.length > 0;
   }

   /**
    * Update hyperlink parameter with inner most data set.
    */
   public static HRef getHyperlink(Hyperlink link, DataSet data, int ridx) {
      if(link == null || data == null) {
         return null;
      }

      Map<String, Object> map = new HashMap<>();
      int acount = data.getColCount();

      for(int i = 0; i < acount; i++) {
         String header = data.getHeader(i);
         map.put(header, data.getData(i, ridx));

         if(data instanceof GeoDataSet && header.startsWith(GeoRef.PREFIX)) {
            header = GeoRef.getBaseName(header);
            map.put(header, data.getData(i, ridx));
         }
      }

      return new Hyperlink.Ref(link, map);
   }

   /**
    * Get drill links.
    */
   public static Hyperlink.Ref[] getDrillLinks(String field, Map<String, Object> param,
                                               DataSet dataset, boolean lightWeight)
   {
      // light weight? drill useless
      if(param == null || field == null || lightWeight) {
         return null;
      }

      DataSet data = dataset;

      if(data instanceof BoxDataSet) {
         data = ((BoxDataSet) data).getDataSet();
      }

      TableLens lens = data instanceof VSDataSet ?
         ((VSDataSet) data).getTable() : null;
      int col = data.indexOfHeader(field);

      // discrete measure
      if(lens == null || col < 0 || col >= lens.getColCount() ||
         !lens.moreRows(lens.getHeaderRowCount()))
      {
         return null;
      }

      XDrillInfo dinfo = lens.getXDrillInfo(lens.getHeaderRowCount(), col);

      if(dinfo == null) {
         return null;
      }

      DataRef dcol = dinfo.getColumn();

      int size = dinfo.getDrillPathCount();
      Hyperlink.Ref[] refs = new Hyperlink.Ref[size];
      TableLens drillLens = ParamTableLens.create(param, lens);
      col = Util.findColumn(drillLens, field);

      if(col < 0) {
         return null;
      }

      for(int k = 0; k < size; k++) {
         DrillPath path = dinfo.getDrillPath(k);
         refs[k] = new Hyperlink.Ref(path, drillLens, 1, col);
         DrillSubQuery query = path.getQuery();
         String queryParam = null;

         if(query != null) {
            refs[k].setParameter(StyleConstants.SUB_QUERY_PARAM,
                                 drillLens.getObject(1, col));

            if(dcol != null) {
               queryParam = Util.findSubqueryVariable(query,
                                                      dcol.getName());
            }

            if(queryParam == null) {
               String tableHeader = drillLens.getColumnIdentifier(col);
               tableHeader = tableHeader == null ?
                  (String) Util.getHeader(drillLens, col) : tableHeader;
               queryParam = Util.findSubqueryVariable(query, tableHeader);
            }

            if(queryParam != null) {
               refs[k].setParameter(Hyperlink.getSubQueryParamVar(queryParam),
                                    drillLens.getObject(1, col));
            }

            for(Iterator<String> iter = query.getParameterNames(); iter.hasNext(); ) {
               String qvar = iter.next();

               if(Tool.equals(qvar, queryParam)) {
                  continue;
               }

               String header = query.getParameter(qvar);
               int cidx = ((ParamTableLens) drillLens).findColumn(header);

               if(cidx < 0) {
                  continue;
               }

               refs[k].setParameter(Hyperlink.getSubQueryParamVar(qvar),
                                    drillLens.getObject(1, cidx));
            }
         }
      }

      return refs;
   }

   /**
    * Get hyperlink parameter.
    */
   public static Map<String, Object> getHyperlinkParam(Axis axis, VDimensionLabel label, int pos,
                                                       EGraph evgraph, DataSet data)
   {
      String[] fields = axis == null || axis.getScale() == null ? null :
         axis.getScale().getFields();
      String field = fields == null || fields.length <= 0 ? null : fields[0];
      Coordinate coord = evgraph.getCoordinate();
      coord = axis.getCoordinate() == null ? coord : axis.getCoordinate();
      String dimName = label == null ? null : label.getDimensionName();
      Object val = label == null ? null : label.getValue();

      if(field == null || dimName == null || val == null || coord == null) {
         return null;
      }

      boolean isx = pos == ICoordinate.TOP_AXIS ||
         pos == ICoordinate.BOTTOM_AXIS;
      Map<String, Object> param = coord.getParentValues(isx);
      param = param == null ? new HashMap<>() :
         new HashMap<>(param);

      if(axis.getScale() instanceof TimeScale) {
         TimeScale tscale = (TimeScale) axis.getScale();
         Class dateClass = getDateType(data, tscale.getFields());

         if(dateClass != null) {
            val = Tool.getData(dateClass, val);
         }
      }

      param.put(dimName, val);
      return param;
   }

   /**
    * Get the actual date type.
    */
   public static Class getDateType(DataSet data, String[] fields) {
      for(String field : fields) {
         for(int i = 0; i < data.getRowCount(); i++) {
            Object val = data.getData(field, i);

            if(val instanceof Date) {
               return val.getClass();
            }
         }
      }

      return null;
   }

   /**
    * Get visual data set.
    */
   public static DataRef getChartRef(ChartInfo info, String fld,
                                     boolean includeAes)
   {
      return getChartRef(info, fld, includeAes, false);
   }

   /**
    * Get the ase ref with the target name.
    */
   public static DataRef getChartAesRef(ChartInfo info, String fld) {
      return GraphUtil.getChartAesRef(info, fld, true);
   }

   /**
    * Get the ase ref with the target name.
    */
   public static DataRef getChartAesRef(ChartInfo info, String fld, boolean runtime) {
      AestheticRef[] aesRefs = info.getAestheticRefs(runtime);

      if(aesRefs == null || aesRefs.length == 0) {
         return null;
      }

      for(int i = 0; i < aesRefs.length; i++) {
         AestheticRef ref = aesRefs[i];

         if(ref != null && GraphUtil.equalsName(ref, fld)) {
            return ref;
         }
      }

      return null;
   }

   /**
    * Get the group ref with the target name.
    */
   public static DataRef getChartGroupRef(ChartInfo info, String fld) {
      ChartRef[] refs = info.getRTGroupFields();

      for(int i = 0; i < refs.length; i++) {
         if(GraphUtil.equalsName(refs[i], fld)) {
            return refs[i];
         }
      }

      return null;
   }

   /**
    * Get visual data set.
    */
   public static DataRef getChartRef(ChartInfo info, String fld,
                                     boolean includeAes, boolean includeGroup)
   {
      ChartRef[] refs = info.getRTXFields();

      for(ChartRef chartRef : refs) {
         if(GraphUtil.equalsName(chartRef, fld)) {
            return chartRef;
         }
      }

      refs = info.getRTYFields();

      for(ChartRef chartRef : refs) {
         if(GraphUtil.equalsName(chartRef, fld)) {
            return chartRef;
         }
      }

      if(info instanceof GanttChartInfo) {
         ChartRef start = ((GanttChartInfo) info).getRTStartField();

         if(GraphUtil.equalsName(start, fld)) {
            return start;
         }

         ChartRef end = ((GanttChartInfo) info).getRTEndField();

         if(GraphUtil.equalsName(end, fld)) {
            return end;
         }

         ChartRef milestone = ((GanttChartInfo) info).getRTMilestoneField();

         if(GraphUtil.equalsName(milestone, fld)) {
            return milestone;
         }
      }

      if(includeGroup) {
         refs = info.getRTGroupFields();

         for(ChartRef ref : refs) {
            if(GraphUtil.equalsName(ref, fld)) {
               return ref;
            }
         }
      }

      if(includeAes) {
         if(info.isMultiAesthetic()) {
            AestheticRef[] aesRefs = info.getAestheticRefs(true);

            if(aesRefs == null || aesRefs.length == 0) {
               return null;
            }

            for(AestheticRef ref : aesRefs) {
               if(ref != null && GraphUtil.equalsName(ref, fld)) {
                  return ref;
               }
            }

            return null;
         }

         if(info.getRTColorField() != null &&
            GraphUtil.equalsName(info.getRTColorField(), fld))
         {
            return info.getRTColorField();
         }

         if(info.getRTShapeField() != null &&
            GraphUtil.equalsName(info.getRTShapeField(), fld))
         {
            return info.getRTShapeField();
         }

         if(info.getRTSizeField() != null &&
            GraphUtil.equalsName(info.getRTSizeField(), fld))
         {
            return info.getRTSizeField();
         }

         if(info.getRTTextField() != null &&
            GraphUtil.equalsName(info.getRTTextField(), fld))
         {
            return info.getRTTextField();
         }
      }

      return null;
   }

   /**
    * Check contains drill.
    */
   private static boolean containsDrill(DataSet data, String field) {
      if(!(data instanceof VSDataSet)) {
         while(data instanceof DataSetFilter) {
            data = ((DataSetFilter) data).getDataSet();

            if(data instanceof VSDataSet) {
               break;
            }
         }

         if(!(data instanceof VSDataSet)) {
            return false;
         }
      }

      TableLens lens = ((VSDataSet) data).getTable();
      int col = data.indexOfHeader(field);
      int row = lens.getHeaderRowCount();

      if(!lens.moreRows(row) || col < 0 || col >= lens.getColCount()) {
         return false;
      }

      XDrillInfo drillInfo = lens.getXDrillInfo(row, col);

      return drillInfo != null && !drillInfo.isEmpty();
   }

   /**
    * Get measure name.
    */
   public static String getHyperlinkMeasure(ElementVO elemVO, boolean text) {
      String measure = elemVO.getMeasureName();

      if(measure == null) {
         return null;
      }

      if(GraphUtil.isLatitudeField(measure)) {
         measure = GraphUtil.getGeoField(measure);
      }

      GraphElement elem = ((ElementGeometry) elemVO.getGeometry()).getElement();

      if("value".equals(measure) && text) {
         TextFrame frame = elem.getTextFrame();

         if(frame != null) {
            measure = frame.getField();
         }
      }

      return GraphUtil.getOriginalCol(measure);
   }

   /**
    * Get measure name for InteractiveArea.
    */
   public static String getHyperlinkMeasure(InteractiveArea iarea) {
      String measureName = iarea.getMeasureName();

      if(iarea instanceof inetsoft.report.composition.region.TextArea) {
         Visualizable visual = iarea.getVisualizable();

         if(visual instanceof VOText) {
            GraphElement elem = ((VOText) visual).getGraphElement();

            if("value".equals(measureName) && elem.getTextFrame() != null) {
               measureName = elem.getTextFrame().getField();
            }
         }
      }

      return measureName;
   }


   /**
    * Get row index.
    */
   public static int getRowIndex(ElementVO elemVO, int col) {
      int[] indexes = elemVO.getRowIndexes();
      return col == -1 || indexes.length <= col ? indexes[0] : indexes[col];
   }

   /**
    * Get sub row index.
    */
   public static int getSubRowIndex(ElementVO elemVO, int col) {
      int[] indexes = elemVO.getSubRowIndexes();

      if(indexes == null) {
         return 0;
      }

      return col == -1 || indexes.length <= col ? indexes[0] : indexes[col];
   }

   /**
    * Get the inner most dimension ref in the graph elements.
    */
   public static XDimensionRef getInnerDimRef(ChartInfo info, boolean rt) {
      if(info == null || GraphTypeUtil.isPolar(info, false)) {
         return null;
      }

      boolean ymeasure = info.getYFieldCount() > 0 &&
         info.getYField(info.getYFieldCount() - 1) instanceof XAggregateRef;

      ChartRef[][] all = {};

      if(rt) {
         if(ymeasure) {
            all = new ChartRef[][]{ info.getRTXFields(), info.getRTYFields() };
         }
         else {
            all = new ChartRef[][]{ info.getRTYFields(), info.getRTXFields() };
         }
      }
      else {
         if(ymeasure) {
            all = new ChartRef[][]{ info.getXFields(), info.getYFields() };
         }
         else {
            all = new ChartRef[][]{ info.getYFields(), info.getXFields() };
         }
      }

      for(ChartRef[] refs : all) {
         for(int i = refs.length - 1; i >= 0; i--) {
            if(refs[i] instanceof XDimensionRef) {
               String name = ((XDimensionRef) refs[i]).getFullName();

               if(name != null && name.length() > 0 && refs[i].isDrillVisible()) {
                  return (XDimensionRef) refs[i];
               }
            }
         }
      }

      return null;
   }

   /**
    * Get the inner most dimension ref name in the graph elements.
    */
   public static List<XDimensionRef> getSeriesDimensionsForCalc(ChartInfo info) {
      List<ChartRef[]> refs = new ArrayList<>();

      // this is used to find inner dim for running totals, so it should return the innermost
      // dimension as the last item.
      if(info.isInvertedGraph()) {
         refs.add(info.getRTXFields());
         refs.add(info.getRTYFields());
      }
      else {
         refs.add(info.getRTYFields());
         refs.add(info.getRTXFields());
      }

      if(GraphTypes.isTreemap(info.getChartType()) || GraphTypes.isRadar(info.getChartType())) {
         refs.add(info.getRTGroupFields());
      }
      // pie uses color... to break into slices (49968)
      else if(GraphTypes.isPie(info.getChartType()) || GraphTypes.isRadar(info.getChartType())) {
         refs.add(Arrays.stream(info.getAestheticRefs(true))
                     .map(a -> a.getRTDataRef()).toArray(ChartRef[]::new));
      }
      else if(GraphTypes.isMap(info.getChartType())) {
         refs.add(((MapInfo) info).getRTGeoFields());
      }
      else if(info instanceof RelationChartInfo) {
         refs.add(new ChartRef[] {
            ((RelationChartInfo) info).getRTSourceField(),
            ((RelationChartInfo) info).getRTTargetField()
         });
      }

      List<XDimensionRef> vec = new ArrayList<>();

      for(ChartRef[] arr : refs) {
         for(ChartRef ref : arr) {
            if(ref instanceof XDimensionRef) {
               vec.add((XDimensionRef) ref);
            }
         }
      }

      return vec;
   }

   public static List<XDimensionRef> getXYDimensions(ChartInfo info) {
      List<XDimensionRef> dims = new ArrayList<>();
      DataRef[] bindingRefs = info.getBindingRefs(true);

      for(DataRef ref : bindingRefs) {
         if(ref instanceof XDimensionRef) {
            dims.add((XDimensionRef) ref);
         }
      }

      return dims;
   }

   public static List<XDimensionRef> getAllDimensions(ChartInfo info, boolean runtime) {
      DataRef[] bindingRefs = info.getBindingRefs(runtime);
      DataRef[] aestheticRefs = info.getAestheticRefs(runtime);

      List<XDimensionRef> dims = getAllDimensions(bindingRefs, runtime);
      dims.addAll(getAllDimensions(aestheticRefs, runtime));

      return dims;
   }

   private static List<XDimensionRef> getAllDimensions(DataRef[] refs, boolean runtime) {
      List<XDimensionRef> dims = new ArrayList<>();

      for(DataRef ref : refs) {
         if(ref instanceof XDimensionRef) {
            dims.add((XDimensionRef) ref);
         }
         else if(ref instanceof AestheticRef) {
            AestheticRef aref = (AestheticRef) ref;
            DataRef ref0 = runtime ? aref.getRTDataRef() : aref.getDataRef();

            if(ref0 instanceof XDimensionRef) {
               dims.add((XDimensionRef) ref0);
            }
         }
      }

      return dims;
   }

   /**
    * Whether support select part select of 3d pie.
    */
   public static boolean supportSubSection(Visualizable v) {
      return v instanceof Pie3DVO;
   }

   /**
    * Check the chart should fill time gap?
    */
   public static boolean shouldFillTimeGap(ChartInfo cinfo) {
      if(!cinfo.isMultiStyles()) {
         return GraphTypes.supportsFillTimeGap(cinfo.getRTChartType());
      }

      ChartRef[] yrefs = cinfo.getRTYFields();
      ChartRef[] xrefs = cinfo.getRTXFields();

      // for MultiStyles, if one dimension support fillTimeGap, return true
      for(ChartRef[] refs : new ChartRef[][]{ yrefs, xrefs }) {
         for(ChartRef ref : refs) {
            if(ref instanceof ChartAggregateRef) {
               int type = ((ChartAggregateRef) ref).getRTChartType();

               if(GraphTypes.supportsFillTimeGap(type)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Check if a categorical frame should be used for an aesthetic field.
    */
   public static boolean isCategorical(DataRef dref) {
      if(isDimension(dref)) {
         return true;
      }

      if(dref instanceof XAggregateRef) {
         XAggregateRef aggr = (XAggregateRef) dref;

         // post-aggregate formula returning string should use categorical
         // so we can create color mapping from aggregate value ranges
         return (!aggr.isAggregateEnabled() && aggr.getFormula() == null ||
            aggr.getFormula() == AggregateFormula.NONE) &&
            XSchema.STRING.equals(aggr.getOriginalDataType());
      }

      return true;
   }

   /**
    * Get calculator by the percentage option.
    *
    * @param option the percentage option.
    */
   public static Calculator getCalculator(int option) {
      if(option == XConstants.PERCENTAGE_OF_GRANDTOTAL) {
         PercentCalc cal = new PercentCalc();
         cal.setLevel(PercentCalc.GRAND_TOTAL);
         cal.setAlias("Percent of Grand Total");
         return cal;
      }
      else if(option == XConstants.PERCENTAGE_OF_GROUP) {
         PercentCalc cal = new PercentCalc();
         cal.setLevel(PercentCalc.SUB_TOTAL);
         cal.setAlias("Percent of Subtotal");
         return cal;
      }
      else {
         return null;
      }
   }

   /**
    * Get percentage option by the calculator.
    */
   public static int getPercentageOption(Calculator cal) {
      if(cal == null) {
         return XConstants.PERCENTAGE_NONE;
      }
      else if(cal.getType() == Calculator.PERCENT) {
         PercentCalc pcal = (PercentCalc) cal;

         if(pcal.getLevel() == PercentCalc.GRAND_TOTAL) {
            return XConstants.PERCENTAGE_OF_GRANDTOTAL;
         }
         else if(pcal.getLevel() == PercentCalc.SUB_TOTAL) {
            return XConstants.PERCENTAGE_OF_GROUP;
         }
         else {
            return XConstants.PERCENTAGE_NONE;
         }
      }
      else {
         return XConstants.PERCENTAGE_NONE;
      }
   }

   /**
    * Get all chart descriptors in the chart.
    *
    * @type aesthetic type defined in ChartArea, e.g. COLOR_LEGEND. Pass null
    * to include all aesthetic types.
    */
   public static List<LegendDescriptor> getLegendDescriptors(
      ChartInfo cinfo, LegendsDescriptor desc, String type)
   {
      List<LegendDescriptor> list = new ArrayList<>();

      if(type == null || type.equals(ChartArea.COLOR_LEGEND)) {
         list.add(desc.getColorLegendDescriptor());

         if(cinfo instanceof RelationChartInfo &&
            ((RelationChartInfo) cinfo).getNodeColorField() != null)
         {
            list.add(((RelationChartInfo) cinfo).getNodeColorField().getLegendDescriptor());
         }

      }

      if(type == null || type.equals(ChartArea.SHAPE_LEGEND)) {
         list.add(desc.getShapeLegendDescriptor());
      }

      if(type == null || type.equals(ChartArea.SIZE_LEGEND)) {
         list.add(desc.getSizeLegendDescriptor());

         if(cinfo instanceof RelationChartInfo &&
            ((RelationChartInfo) cinfo).getNodeSizeField() != null)
         {
            list.add(((RelationChartInfo) cinfo).getNodeSizeField().getLegendDescriptor());
         }
      }

      String[] types = { type };

      if(type == null) {
         types = new String[]{ ChartArea.COLOR_LEGEND, ChartArea.SHAPE_LEGEND,
                               ChartArea.SIZE_LEGEND };
      }

      if(cinfo.isMultiAesthetic()) {
         for(VSDataRef aggr : cinfo.getAggregateRefs()) {
            for(String type2 : types) {
               list.add(getLegendDescriptor(cinfo, desc, "", aggr.getFullName(), type2));
            }
         }
      }
      else {
         for(String type2 : types) {
            list.add(getLegendDescriptor(cinfo, desc, null, (String) null, type2));
         }
      }

      while(list.remove(null)) {
         // remove all null
      }

      return list;
   }

   /**
    * Find the legend descriptor.
    */
   public static LegendDescriptor getLegendDescriptor(
      ChartInfo info, LegendsDescriptor legends,
      String field, String varname, String aestheticType)
   {
      List<String> list = new ArrayList<>();
      list.add(varname);
      return getLegendDescriptor(info, legends, field, list, aestheticType);
   }

   /**
    * Find the legend descriptor.
    *
    * @param field    the field the legend frame is bound to. Return the
    *                 global (in LegendsDescriptor) if field is null. Ignore field if
    *                 the value is empty string.
    * @param varnames the measure this legend is for.
    */
   public static LegendDescriptor getLegendDescriptor(
      ChartInfo info, LegendsDescriptor legends,
      String field, List<String> varnames, String aestheticType)
   {
      return getLegendDescriptor(info, legends, field, varnames, aestheticType, null);
   }

   /**
    * Find the legend descriptor.
    *
    * @param field    the field the legend frame is bound to. Return the
    *                 global (in LegendsDescriptor) if field is null. Ignore field if
    *                 the value is empty string.
    * @param varnames the measure this legend is for.
    */
   public static LegendDescriptor getLegendDescriptor(
      ChartInfo info, LegendsDescriptor legends,
      String field, List<String> varnames, String aestheticType, Boolean nodeAesthetic)
   {
      if(info instanceof RelationChartInfo) {
         if(ChartArea.COLOR_LEGEND.equals(aestheticType)) {
            if(nodeAesthetic != null) {
               AestheticRef aref = nodeAesthetic ? ((RelationChartInfo) info).getNodeColorField() :
                  info.getColorField();

               if(isMatchAesthetic(aref, field)) {
                  return aref.getLegendDescriptor();
               }
               else if(aref != null) {
                  return null;
               }
            }
            else {
               AestheticRef aref = info.getColorField();

               if(isMatchAesthetic(aref, field)) {
                  return aref.getLegendDescriptor();
               }

               aref = ((RelationChartInfo) info).getNodeColorField();

               if(isMatchAesthetic(aref, field)) {
                  return aref.getLegendDescriptor();
               }
            }
         }
         else if(ChartArea.SIZE_LEGEND.equals(aestheticType)) {
            AestheticRef aref = info.getSizeField();

            if(isMatchAesthetic(aref, field)) {
               return aref.getLegendDescriptor();
            }

            aref = ((RelationChartInfo) info).getNodeSizeField();

            if(isMatchAesthetic(aref, field)) {
               return aref.getLegendDescriptor();
            }
         }
      }

      if(!info.isMultiAesthetic() || varnames == null || field == null) {
         syncLabelAliases(info, legends);

         if(ChartArea.COLOR_LEGEND.equals(aestheticType)) {
            return legends.getColorLegendDescriptor();
         }
         else if(ChartArea.SHAPE_LEGEND.equals(aestheticType) ||
            ChartArea.LINE_LEGEND.equals(aestheticType) ||
            ChartArea.TEXTURE_LEGEND.equals(aestheticType))
         {
            return legends.getShapeLegendDescriptor();
         }
         else if(ChartArea.SIZE_LEGEND.equals(aestheticType)) {
            return legends.getSizeLegendDescriptor();
         }
      }
      else {
         List<LegendDescriptor> list = new ArrayList<>();
         boolean isMultiAesthetic = info.isMultiAesthetic();
         VSDataRef[] aggrRefs;

         if(isMultiAesthetic && info instanceof VSChartInfo) {
            VSChartInfo vInfo = (VSChartInfo) info;
            aggrRefs = vInfo.getBindingRefs(false);
            VSDataRef[] v = vInfo.getAestheticRefs(false);
            aggrRefs = Stream.concat(
                  Arrays.stream(aggrRefs)
                     .filter((s) -> s instanceof VSChartAggregateRef),
                  Arrays.stream(v).filter((s) -> s instanceof VSAggregateRef))
               .toArray(VSDataRef[]::new);
         }
         else {
            aggrRefs = info.getAggregateRefs();
         }

         if(info instanceof VSChartInfo) {
            ChartRef[] comparisonRefs = ((VSChartInfo) info).getRuntimeDateComparisonRefs();

            if(comparisonRefs != null) {
               VSDataRef[] comparisonAggs = Arrays.stream(comparisonRefs)
                  .filter(VSChartAggregateRef.class::isInstance)
                  .toArray(VSDataRef[]::new);
               aggrRefs = (VSDataRef[]) ArrayUtils.addAll(comparisonAggs, aggrRefs);
            }
         }

         for(VSDataRef aggr : aggrRefs) {
            String aggrName = aggr.getFullName();
            // check for date comparison ref too. (63178)
            String aggrName2 = ((ChartAggregateRef) aggr).getFullName(false);

            if(varnames.contains(aggrName) || varnames.contains(aggrName2) ||
               varnames.contains(BrushDataSet.ALL_HEADER_PREFIX + aggrName) ||
               varnames.contains(BrushDataSet.ALL_HEADER_PREFIX + aggrName2) ||
               isMultiAesthetic && aggr instanceof ChartBindable &&
                  GraphTypes.isInterval(((ChartBindable) aggr).getRTChartType()) &&
                  varnames.contains(IntervalDataSet.TOP_PREFIX + aggrName))
            {
               AestheticRef aref = null;

               if(ChartArea.COLOR_LEGEND.equals(aestheticType)) {
                  aref = ((ChartAggregateRef) aggr).getColorField();
               }
               else if(ChartArea.SHAPE_LEGEND.equals(aestheticType) ||
                  ChartArea.LINE_LEGEND.equals(aestheticType) ||
                  ChartArea.TEXTURE_LEGEND.equals(aestheticType))
               {
                  aref = ((ChartAggregateRef) aggr).getShapeField();
               }
               else if(ChartArea.SIZE_LEGEND.equals(aestheticType)) {
                  aref = ((ChartAggregateRef) aggr).getSizeField();
               }

               if(isMatchAesthetic(aref, field)) {
                  list.add(aref.getLegendDescriptor());
               }
            }
         }

         if(list.size() == 1) {
            return list.get(0);
         }
         else if(list.size() > 1) {
            return new AllLegendDescriptor(list);
         }
      }

      return null;
   }

   private static boolean isMatchAesthetic(AestheticRef aref, String field) {
      if(aref == null) {
         return false;
      }

      String fullName = aref.getFullName();

      if(fullName != null && GraphUtil.isDiscrete(aref)) {
         fullName = ChartAggregateRef.getBaseName(fullName);
      }

      field = NamedRangeRef.getBaseName(field);
      fullName = NamedRangeRef.getBaseName(fullName);

      return field == null || field.isEmpty() || field.equals(fullName);
   }

   // Merge label aliases between color and shape legend descriptors. When there is no binding
   // for color and shape, the colors and shapes of two measures are combined into one legend.
   // To users, this appears to be the same. As user switches between different shapes or colors,
   // we don't want the label aliases to be 'lost'. This forces the two to be in sync in this
   // special case (the special handling for color and shape is in Legend/LegendItem classes).
   private static void syncLabelAliases(ChartInfo info, LegendsDescriptor legends) {
      if(info.getColorField() != null || info.getShapeField() != null) {
         return;
      }

      LegendDescriptor colorDesc = legends.getColorLegendDescriptor();
      LegendDescriptor shapeDesc = legends.getShapeLegendDescriptor();

      colorDesc.getAliasedLabels().forEach(key -> {
         if(shapeDesc.getLabelAlias(key) == null) {
            shapeDesc.setLabelAlias(key, colorDesc.getLabelAlias(key));
         }
      });

      shapeDesc.getAliasedLabels().forEach(key -> {
         if(colorDesc.getLabelAlias(key) == null) {
            colorDesc.setLabelAlias(key, shapeDesc.getLabelAlias(key));
         }
      });
   }

   /**
    * Find the var associated with this frame.
    */
   public static List<String> getTargetFields(VisualFrame frame, EGraph egraph) {
      List<String> fields = new ArrayList<>();

      for(int i = 0; i < egraph.getElementCount(); i++) {
         GraphElement elem = egraph.getElement(i);

         for(VisualFrame frame0 : elem.getVisualFrames()) {
            if(frame0 == null) {
               continue;
            }

            if(frame0 == frame || frame0.getLegendFrame() == frame) {
               addTargetFields(elem, frame0, frame, fields);
            }
            else if(frame0 instanceof CompositeVisualFrame) {
               frame0 = ((CompositeVisualFrame) frame0).getGuideFrame();

               if(frame0 == frame || frame0 != null && frame0.getLegendFrame() == frame) {
                  addTargetFields(elem, frame0, frame, fields);
               }
            }
            else if(frame0 instanceof StackedMeasuresFrame) {
               VisualFrame[] legendFrames = ((StackedMeasuresFrame) frame0).getLegendFrames();

               for(VisualFrame legendFrame : legendFrames) {
                  if(legendFrame == frame ||
                     legendFrame != null && legendFrame.getLegendFrame() == frame)
                  {
                     addTargetFields(elem, legendFrame, frame, fields);
                  }
               }
            }
         }
      }

      return fields;
   }

   private static void addTargetFields(GraphElement elem, VisualFrame frame0, VisualFrame frame,
                                       List<String> fields)
   {
      for(int k = 0; k < elem.getVarCount(); k++) {
         // primary frame should get priority
         if(frame0 == frame) {
            fields.add(k, elem.getVar(k));
         }
         else {
            fields.add(elem.getVar(k));
         }
      }
   }

   /**
    * Add sub columns.
    */
   public static void addSubCol(ChartAggregateRef ref, SubColumns cols) {
      Set set = new HashSet();
      addSubCol(ref, set);

      for(Object obj : set) {
         cols.addAggregate((String) obj);
      }
   }

   /**
    * Add sub columns.
    */
   public static void addSubCol(ChartAggregateRef ref, Set cols) {
      if(ref.isDiscrete()) {
         ChartAggregateRef ref2 = (ChartAggregateRef) ref.clone();
         ref2.setDiscrete(false);
         cols.add(ref2.getFullName());
      }

      cols.add(ref.getFullName());
   }

   /**
    * Get original name for chart ref, is it is an aggregate ref, discard
    * its calculator and discrete prefix.
    */
   public static String getFullNameNoCalc(DataRef ref) {
      if(ref instanceof XAggregateRef) {
         return ((XAggregateRef) ref).getFullName(false);
      }

      if(ref instanceof XDimensionRef) {
         return ((XDimensionRef) ref).getFullName();
      }

      return ref.getName();
   }

   /**
    * Get the name with discrete prefix.
    */
   public static String getBaseName(ChartRef ref) {
      if(ref == null) {
         return null;
      }
      else if(ref instanceof ChartAggregateRef) {
         ref = (ChartRef) ref.clone();
         ((ChartAggregateRef) ref).setDiscrete(false);
      }

      return ref.getFullName();
   }

   /**
    * Strip out the prefix/suffix to get the original name.
    */
   public static String getFullNameNoCalc(String name) {
      name = ChartAggregateRef.getBaseName(name);
      return getOriginalNameWithDiscrete(name);
   }

   /**
    * Get the name to be used on tooltip.
    */
   public static String getOriginalNameForTip(String name) {
      if(name.contains(")")) {
         return name;
      }

      return getOriginalNameWithDiscrete(name);
   }

   private static String getOriginalNameWithDiscrete(String name) {
      int dot = name.lastIndexOf('.');

      // remove "xxx.1"
      if(dot >= 0) {
         String suffix = name.substring(dot + 1);

         try {
            Integer.parseInt(suffix);
            name = name.substring(0, dot);
            dot = name.lastIndexOf('.');
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      return dot >= 0 ? name.substring(dot + 1) : name;
   }

   /**
    * Check if the graph is scrollable.
    *
    * @param info if not null, only scrollable if explicitly resized.
    */
   public static boolean isScrollable(VGraph graph, ChartInfo info) {
      if(graph.getCoordinate() instanceof FacetCoord) {
         return true;
      }

      return isVScrollable(graph, info) || isHScrollable(graph, info);
   }

   /**
    * Check if possible to scroll vertically.
    *
    * @param info if not null, only scrollable if explicitly resized.
    */
   public static boolean isVScrollable(VGraph graph, ChartInfo info) {
      if(graph == null) {
         return false;
      }

      if(graph.getCoordinate() instanceof RelationCoord ||
         graph.getCoordinate() instanceof ParaboxCoord ||
         // allow word cloud to be enlarged so words won't be clipped.
         GraphTypeUtil.isWordCloud(info) || GraphTypeUtil.isDotPlot(info))
      {
         return true;
      }

      if(GTool.getUnitCount(graph.getCoordinate(), Coordinate.LEFT_AXIS, false) <= 1) {
         return false;
      }

      if(graph.getCoordinate() instanceof FacetCoord) {
         if(countCat(graph.getAxesAt(Coordinate.LEFT_AXIS)) > 0) {
            return true;
         }
      }

      final boolean timeScale = Arrays.stream(graph.getEGraph().getCoordinate().getAxes(true))
         .anyMatch(a -> a.getScale() instanceof TimeScale);
      // unit count for time scale can be very large and is not very meaningful
      final boolean forceScrollEnable = !timeScale;
      final double FORCE_SCROLL_MULTIPLE = 1.5;
      final double minPlotHeight = graph.getMinPlotHeight();
      final double height = graph.getSize().getHeight();

      // if the size of chart is much smaller than the min size, we force it to be scrollable
      // otherwise bars will be squeezed to be very small
      if(forceScrollEnable && minPlotHeight > height * FORCE_SCROLL_MULTIPLE) {
         return true;
      }

      return info == null || info.isHeightResized();
   }

   /**
    * Check if possible to scroll horizontally.
    *
    * @param info if not null, only scrollable if explicitly resized.
    */
   public static boolean isHScrollable(VGraph graph, ChartInfo info) {
      if(graph == null) {
         return false;
      }

      if(graph.getCoordinate() instanceof RelationCoord ||
         graph.getCoordinate() instanceof ParaboxCoord ||
         // allow word cloud to be enlarged so words won't be clipped.
         GraphTypeUtil.isWordCloud(info) || GraphTypeUtil.isDotPlot(info))
      {
         return true;
      }

      if(GTool.getUnitCount(graph.getCoordinate(), Coordinate.TOP_AXIS, false) <= 1) {
         return false;
      }

      if(graph.getCoordinate() instanceof FacetCoord) {
         if(countCat(graph.getAxesAt(Coordinate.TOP_AXIS)) > 0) {
            return true;
         }

         if(GraphTypeUtil.isHeatMapish(info) && countCat(graph.getAxesAt(Coordinate.BOTTOM_AXIS)) > 0) {
            return true;
         }
      }

      boolean bars = true;
      EGraph egraph = graph.getEGraph();

      for(int i = 0; i < egraph.getElementCount(); i++) {
         if(!(egraph.getElement(i) instanceof IntervalElement)) {
            bars = false;
            break;
         }
      }

      // only force scroll for bar chart.
      final boolean forceScrollEnable = bars &&
         // unit count for time scale can be very large and is not very meaningful.
         !Arrays.stream(graph.getEGraph().getCoordinate().getAxes(true))
            .anyMatch(a -> a.getScale() instanceof TimeScale);
      final double FORCE_SCROLL_MULTIPLE = 1.5;
      double minPlotWidth = graph.getMinPlotWidth();
      final double width = graph.getSize().getWidth();

      if(info instanceof VSChartInfo) {
         VSChartInfo vsinfo = (VSChartInfo) info;

         if(vsinfo.isAppliedDateComparison() && vsinfo.isDcBaseDateOnX()) {
            minPlotWidth *= 2.5;
         }
      }

      // if the size of chart is much smaller than the min size, we force it to be scrollable
      // otherwise bars will be squeezed to be very small
      if(forceScrollEnable && minPlotWidth > width * FORCE_SCROLL_MULTIPLE) {
         return true;
      }

      return info == null || info.isWidthResized();
   }

   /**
    * Check if support resizing vertically.
    */
   public static boolean isVResizable(VGraph graph, ChartInfo info) {
      if(graph == null) {
         return false;
      }

      if(graph.getCoordinate() instanceof RelationCoord ||
         graph.getCoordinate() instanceof ParaboxCoord ||
         // allow word cloud to be enlarged so words won't be clipped. (similar to dotplot)
         GraphTypeUtil.isWordCloud(info) || GraphTypeUtil.isDotPlot(info))
      {
         return true;
      }

      if(GTool.getUnitCount(graph.getCoordinate(), Coordinate.LEFT_AXIS, false) <= 1) {
         return false;
      }

      return true;
   }

   /**
    * Check if support resizing horizontally.
    */
   public static boolean isHResizable(VGraph graph, ChartInfo info) {
      if(graph == null) {
         return false;
      }

      if(graph.getCoordinate() instanceof RelationCoord ||
         graph.getCoordinate() instanceof ParaboxCoord ||
         // allow word cloud to be enlarged so words won't be clipped. (similar to dotplot)
         GraphTypeUtil.isWordCloud(info) || GraphTypeUtil.isDotPlot(info))
      {
         return true;
      }

      if(GTool.getUnitCount(graph.getCoordinate(), Coordinate.TOP_AXIS, false) <= 1) {
         return false;
      }

      return true;
   }

   /**
    * Count the number of categorical axis.
    */
   private static int countCat(DefaultAxis[] axes) {
      int cnt = 0;

      for(DefaultAxis axis : axes) {
         if(axis.getScale() instanceof CategoricalScale) {
            if(axis.getScale().getFields().length > 0) {
               cnt++;
            }
         }
      }

      return cnt;
   }

   /**
    * Text value color changed changed.
    * In point chart, if the point is hidden, the point color is used to draw
    * the text label. This causes the color set on the text to not take effect.
    * We copy the text color to the color frame to avoid this problem.
    */
   public static void textColorChanged(ChartInfo info, String aggrName, Color clr) {
      ChartBindable bindable = info;

      if(!(info instanceof MergedChartInfo)) {
         ChartRef ref = info.getFieldByName(aggrName, false);

         if(ref instanceof ChartBindable) {
            bindable = (ChartBindable) ref;
         }
      }

      if(isNil(bindable) && !GraphTypeUtil.isMap(info) &&
         !GraphTypes.isRadar(info.getChartType()) &&
         !GraphTypes.isTreemap(info.getChartType()) &&
         !GraphTypes.isRelation(info.getChartType()))
      {
         Object frame = bindable.getColorFrame();

         if(frame instanceof StaticColorFrame) {
            ((StaticColorFrame) frame).setUserColor(clr);
         }
      }
   }

   /**
    * Fix the entire chart foreground if foreground changed by variable or
    * expression.
    */
   public static void fixChartForeground(ChartVSAssemblyInfo vinfo) {
      VSCompositeFormat cfmt = vinfo.getFormat();
      List values = cfmt.getDynamicValues();

      if(values == null || values.size() != 3) {
         return;
      }

      // check foreground dynamic value
      DynamicValue dval = (DynamicValue) values.get(1);
      String dvalue = dval.getDValue();

      if(!VSUtil.isVariableValue(dvalue) && !VSUtil.isScriptValue(dvalue)) {
         return;
      }

      List<CompositeTextFormat> list = new ArrayList<>();
      ChartDescriptor desc = vinfo.getRTChartDescriptor();

      if(desc == null) {
         ChartDescriptor rdesc = vinfo.getChartDescriptor() == null ?
            new ChartDescriptor() : (ChartDescriptor) vinfo.getChartDescriptor().clone();
         vinfo.setRTChartDescriptor(rdesc);
      }

      VSChartInfo cinfo = vinfo.getVSChartInfo();
      GraphFormatUtil.getChartFormat(cinfo, vinfo.getRTChartDescriptor(),
                                     cinfo.getAxisDescriptor(), list);

      for(CompositeTextFormat fmt : list) {
         if(fmt != null) {
            fmt.setColor(cfmt.getForeground());
         }
      }
   }

   /**
    * Gets whether show axis line in axis property dialog.
    */
   public static boolean isShowAxisLineEnabled(ChartInfo cInfo, boolean isFacetGrid,
                                               String columnName, boolean isOuter, String axisType,
                                               ChartArea chartArea)
   {
      if(!isOuter) {
         return true;
      }
      else if(isFacetGrid) {
         return isShowAxisLineEnabled(columnName, axisType, chartArea, cInfo);
      }

      // this should return false if we are in ad hoc mode, but this dialog is always called
      // from vs, so return true
      return true;
   }

   /**
    * Check if the show axis line is available or not.
    */
   public static boolean isShowAxisLineEnabled(String columnName, String axisType,
                                               ChartArea chartArea, ChartInfo cinfo)
   {
      if(chartArea == null) {
         return false;
      }

      if(GraphTypes.isGeo(cinfo.getChartType()) || GraphTypes.isPolar(cinfo.getChartType()) ||
         GraphTypes.isTreemap(cinfo.getChartType()) || GraphTypes.isRelation(cinfo.getChartType()))
      {
         return true;
      }

      if(Tool.equals(axisType, ChartArea.X_AXIS) || "top_x_axis".equals(axisType)) {
         AxisArea top = chartArea.getTopXAxisArea();

         if(top == null) {
            return false;
         }

         DefaultArea[] topAreas = top.getAllAreas();

         if(topAreas.length == 0) {
            return false;
         }

         if(cinfo.getXFields().length == 1) {
            return true;
         }

         DefaultArea lastTopArea = topAreas[topAreas.length - 1];

         if(lastTopArea instanceof DimensionLabelArea) {
            return !Tool.equals(columnName, ((DimensionLabelArea) lastTopArea).getDimensionName());
         }
         else if(lastTopArea instanceof MeasureLabelsArea) {
            return !Tool.equals(columnName, ((MeasureLabelsArea) lastTopArea).getMeasureName());
         }
      }
      else if(Tool.equals(axisType, ChartArea.Y_AXIS) || "left_y_axis".equals(axisType)) {
         AxisArea left = chartArea.getLeftYAxisArea();

         if(left == null) {
            return false;
         }

         DefaultArea[] leftAreas = left.getAllAreas();

         if(leftAreas.length == 0) {
            return false;
         }

         for(int i = leftAreas.length - 1; i < leftAreas.length; i--) {
            DefaultArea area = leftAreas[i];

            if(area instanceof AxisLineArea) {
               continue;
            }

            if(area instanceof DimensionLabelArea) {
               if(!Tool.equals(columnName, ((DimensionLabelArea) area).getDimensionName())) {
                  return true;
               }

               // on x measure, this is an outer dim (with a fake Value
               // measure at the inner Y) and visibility can't be controlled
               if(getMeasures(cinfo.getXFields()).size() == 0) {
                  return false;
               }

               AxisArea right = chartArea.getRightYAxisArea();

               if(right == null || right.getAllAreas().length == 0) {
                  return true;
               }

               return false;
            }
            else if(area instanceof MeasureLabelsArea) {
               return true;
            }

            break;
         }
      }

      return false;
   }

   /**
    * Get the source (worksheet/table) prefix for the visual index.
    */
   public static String getVisualSource(DataVSAssemblyInfo info) {
      Viewsheet vs = info.getViewsheet();
      AssetEntry wentry = (vs != null) ? vs.getBaseEntry() : null;
      SourceInfo src = info.getSourceInfo();
      String path = (wentry != null) ? wentry.toIdentifier() : "";

      if(src != null) {
         path += "_" + src.getSource();
      }

      return path;
   }

   /**
    * Get all the scales used in the coordinate.
    */
   public static Collection<Scale> getAllScales(Coordinate coord) {
      Collection<Scale> scales = new HashSet<>();
      getAllScales0(coord, scales);

      return scales;
   }

   /**
    * Get all the scales recursively.
    */
   private static void getAllScales0(Coordinate coord, Collection<Scale> scales) {
      if(coord == null) {
         return;
      }

      scales.addAll(Arrays.asList(coord.getScales()));

      if(coord instanceof PolarCoord) {
         getAllScales0(((PolarCoord) coord).getCoordinate(), scales);
      }
      else if(coord instanceof AbstractParallelCoord) {
         scales.add(((AbstractParallelCoord) coord).getAxisLabelScale());
      }
      else if(coord instanceof FacetCoord) {
         FacetCoord facet = (FacetCoord) coord;

         getAllScales0(facet.getOuterCoordinate(), scales);

         for(Coordinate cobj : facet.getInnerCoordinates()) {
            getAllScales0(cobj, scales);
         }
      }
   }

   /**
    * Judge whether the date type dimension is an outer dimension.
    */
   public static boolean isOuterDimRef(XDimensionRef ref, ChartInfo cInfo) {
      return isOuterDimRef(ref, cInfo, false);
   }

   /**
    * Judge whether the date type dimension is an outer dimension.
    */
   public static boolean isOuterDimRef(XDimensionRef ref, ChartInfo cInfo, boolean rt) {
      ChartRef ref2;
      ChartRef[] xrefs = rt ? cInfo.getRTXFields() : cInfo.getXFields();
      ChartRef[] yrefs = rt ? cInfo.getRTYFields() : cInfo.getYFields();

      for(int i = 0; xrefs != null && i < xrefs.length; i++) {
         ref2 = xrefs[i];

         if(!(ref2 instanceof XDimensionRef)) {
            break;
         }

         if(!ref.getFullName().equals(ref2.getFullName())) {
            continue;
         }

         // radar chart use y dims for parallel coord axes, so x dims are all outer (46010).
         return cInfo instanceof RadarChartInfo || i != xrefs.length - 1;
      }

      for(int i = 0; yrefs != null && i < yrefs.length; i++) {
         ref2 = yrefs[i];

         if(!(ref2 instanceof XDimensionRef)) {
            break;
         }

         if(!ref.getFullName().equals(ref2.getFullName())) {
            continue;
         }

         if(i == yrefs.length - 1) {
            int ctype = cInfo.getChartType();

            // for candle and stock, the dimension in y is a outer dimension
            if(ctype == GraphTypes.CHART_STOCK || ctype == GraphTypes.CHART_CANDLE) {
               return true;
            }

            // no measure, use fake Y column so this is outer
            return getMeasures(xrefs).size() == 0 && xrefs.length > 0;
         }
         else {
            return true;
         }
      }

      // the ref have not added in info now
      return false;
   }

   public static boolean hasMeasure(ChartInfo info) {
      return hasMeasureOnX(info) || hasMeasureOnY(info);
   }

   public static boolean hasMeasureOnX(ChartInfo info) {
      boolean hasMeasure = false;

      for(int i = 0; i < info.getXFieldCount(); i++) {
         if(info.getXField(i).isMeasure()) {
            hasMeasure = true;
            break;
         }
      }

      return hasMeasure;
   }

   public static boolean hasMeasureOnY(ChartInfo info) {
      boolean hasMeasure = false;

      for(int i = 0; i < info.getYFieldCount(); i++) {
         if(info.getYField(i).isMeasure()) {
            hasMeasure = true;
            break;
         }
      }

      return hasMeasure;
   }

   /**
    * Build Aggregate Paths.
    *
    * @param rows  all rows
    * @param cols  all cols
    * @param field agg field
    */
   public static List<String> getAggregatesPath(VSDataRef[] rows, VSDataRef[] cols, String field) {
      List<String> result = new ArrayList<>();
      Arrays.stream(rows).forEach(row -> result.add(row.getFullName()));
      Arrays.stream(cols).forEach(col -> result.add(col.getFullName()));
      result.add(field);

      return result;
   }

   /**
    * Check if drag or drop type is high/low/open/close region.
    */
   public static boolean isHighLowRegion(int type) {
      return type == ChartConstants.DROP_REGION_HIGH ||
         type == ChartConstants.DROP_REGION_LOW ||
         type == ChartConstants.DROP_REGION_OPEN ||
         type == ChartConstants.DROP_REGION_CLOSE;
   }

   public static boolean isTreeRegion(int type) {
      return type == ChartConstants.DROP_REGION_SOURCE ||
         type == ChartConstants.DROP_REGION_TARGET;
   }

   public static boolean isGanttRegion(int type) {
      return type == ChartConstants.DROP_REGION_START ||
         type == ChartConstants.DROP_REGION_END ||
         type == ChartConstants.DROP_REGION_MILESTONE;
   }

   public static int getHighLowRegionType(String name) {
      if(OriginalDescriptor.HIGH.equalsIgnoreCase(name)) {
         return ChartConstants.DROP_REGION_HIGH;
      }
      else if(OriginalDescriptor.LOW.equalsIgnoreCase(name)) {
         return ChartConstants.DROP_REGION_LOW;
      }
      else if(OriginalDescriptor.OPEN.equalsIgnoreCase(name)) {
         return ChartConstants.DROP_REGION_OPEN;
      }
      else if(OriginalDescriptor.CLOSE.equalsIgnoreCase(name)) {
         return ChartConstants.DROP_REGION_CLOSE;
      }

      return -1;
   }

   public static AxisDescriptor getAxisDescriptor(ChartInfo info, ChartRef ref) {
      AxisDescriptor axisDes;

      if(ref != null && "_Parallel_Label_".equals(ref.getFullName()) &&
         info instanceof RadarChartInfo)
      {
         axisDes = ((RadarChartInfo) info).getLabelAxisDescriptor();
      }
      else if(ref != null && (info instanceof RadarChartInfo ||
         ref instanceof ChartAggregateRef && info.isSeparatedGraph()))
      {
         axisDes = ref.getAxisDescriptor();
      }
      else if(ref != null && info.isSeparatedGraph() && !(info instanceof CandleChartInfo)) {
         axisDes = ref.getAxisDescriptor();
      }
      else if(ref instanceof ChartAggregateRef && ((ChartAggregateRef) ref).isSecondaryY()) {
         axisDes = info.getAxisDescriptor2();
      }
      // if inseparate graph or candle, stock chart, get dimension descriptor
      // from ref, get shared measure descriptor from info
      else if(ref != null && (info.isSeparatedGraph() || ref instanceof XDimensionRef)) {
         axisDes = ref.getAxisDescriptor();
      }
      else {
         axisDes = info.getAxisDescriptor();
      }

      return axisDes;
   }

   public static DataRef[] getChartBindingRefs(ChartInfo cinfo) {
      ChartRef[] xrefs = cinfo.getXFields();
      ChartRef[] yrefs = cinfo.getYFields();
      DataRef[] allRefs = (DataRef[]) ArrayUtils.addAll(xrefs, yrefs);
      ChartRef[] grefs = cinfo.getGroupFields();
      allRefs = (DataRef[]) ArrayUtils.addAll(allRefs, grefs);
      allRefs = getAestheticFields(allRefs, cinfo);

      if(cinfo.isMultiAesthetic()) {
         List<ChartAggregateRef> aggrs = AllChartAggregateRef.getXYAggregateRefs(cinfo, false);

         for(ChartAggregateRef agg : aggrs) {
            allRefs = getAestheticFields(allRefs, agg);
         }
      }

      if(cinfo.getPathField() != null) {
         allRefs = (DataRef[]) ArrayUtils.add(allRefs, cinfo.getPathField());
      }

      if(cinfo instanceof MapInfo) {
         allRefs = (DataRef[]) ArrayUtils.addAll(allRefs, ((MapInfo) cinfo).getGeoFields());
      }

      if(cinfo instanceof CandleChartInfo) {
         CandleChartInfo candleInfo = (CandleChartInfo) cinfo;
         allRefs = getHighLowFields(allRefs, candleInfo);
      }

      if(cinfo instanceof RelationChartInfo) {
         allRefs = (DataRef[]) ArrayUtils.add(allRefs, ((RelationChartInfo) cinfo).getSourceField());
         allRefs = (DataRef[]) ArrayUtils.add(allRefs, ((RelationChartInfo) cinfo).getTargetField());
      }

      if(cinfo instanceof GanttChartInfo) {
         allRefs = (DataRef[]) ArrayUtils.addAll(allRefs, ((GanttChartInfo) cinfo).getGanttFields(false).toArray());
      }

      DataRef[] arr = Arrays.stream(allRefs)
         .filter(a -> a != null && !VSUtil.isPreparedCalcField(a))
         .toArray(DataRef[]::new);

      final Set<String> names = new HashSet<>();
      final List<DataRef> uniqueRefs = new ArrayList<>();

      for(DataRef ref : arr) {
         if(ref instanceof VSDataRef) {
            final VSDataRef vsDataRef = (VSDataRef) ref;

            if(!names.contains(vsDataRef.getFullName())) {
               uniqueRefs.add(vsDataRef);
               names.add(vsDataRef.getFullName());
            }
         }
         else {
            uniqueRefs.add(ref);
         }
      }

      return uniqueRefs.toArray(new DataRef[0]);
   }

   private static DataRef[] getAestheticFields(DataRef[] allRefs, ChartBindable bindable) {
      if(bindable.getColorField() != null) {
         allRefs = (DataRef[]) ArrayUtils.add(allRefs, bindable.getColorField().getDataRef());
      }

      if(bindable.getShapeField() != null) {
         allRefs = (DataRef[]) ArrayUtils.add(allRefs, bindable.getShapeField().getDataRef());
      }

      if(bindable.getSizeField() != null) {
         allRefs = (DataRef[]) ArrayUtils.add(allRefs, bindable.getSizeField().getDataRef());
      }

      if(bindable.getTextField() != null) {
         allRefs = (DataRef[]) ArrayUtils.add(allRefs, bindable.getTextField().getDataRef());
      }

      return allRefs;
   }

   /**
    * including high, low, close and open field.
    */
   private static DataRef[] getHighLowFields(DataRef[] allRefs, CandleChartInfo candelInfo) {
      if(candelInfo.getCloseField() != null) {
         allRefs = (DataRef[]) ArrayUtils.add(allRefs, candelInfo.getCloseField());
      }

      if(candelInfo.getOpenField() != null) {
         allRefs = (DataRef[]) ArrayUtils.add(allRefs, candelInfo.getOpenField());
      }

      if(candelInfo.getHighField() != null) {
         allRefs = (DataRef[]) ArrayUtils.add(allRefs, candelInfo.getHighField());
      }

      if(candelInfo.getLowField() != null) {
         allRefs = (DataRef[]) ArrayUtils.add(allRefs, candelInfo.getLowField());
      }

      return allRefs;
   }

   public static boolean hasDimensionOnX(ChartInfo info) {
      boolean hasDimension = false;

      for(int i = 0; i < info.getXFieldCount(); i++) {
         if(info.getXField(i) instanceof ChartDimensionRef) {
            hasDimension = true;
            break;
         }
      }

      return hasDimension;
   }

   public static boolean hasDimensionOnY(ChartInfo info) {
      boolean hasDimension = false;

      for(int i = 0; i < info.getYFieldCount(); i++) {
         if(info.getYField(i) instanceof ChartDimensionRef) {
            hasDimension = true;
            break;
         }
      }

      return hasDimension;
   }

   /**
    * Get the original column.
    */
   public static String getOriginalCol(String col) {
      if(col == null) {
         return col;
      }

      if(col.startsWith(BrushDataSet.ALL_HEADER_PREFIX)) {
         col = col.substring(BrushDataSet.ALL_HEADER_PREFIX.length());
      }

      if(col.startsWith(IntervalDataSet.TOP_PREFIX)) {
         col = col.substring(IntervalDataSet.TOP_PREFIX.length());
      }

      if(col.startsWith(SumDataSet.SUM_HEADER_PREFIX)) {
         col = col.substring(SumDataSet.SUM_HEADER_PREFIX.length());
      }

      return col;
   }

   /**
    * Get the brush data set on dataset filter chain.
    */
   public static BrushDataSet getBrushDataSet(DataSet data) {
      while(data instanceof DataSetFilter) {
         if(data instanceof BrushDataSet) {
            break;
         }

         data = ((DataSetFilter) data).getDataSet();
      }

      return data instanceof BrushDataSet ? (BrushDataSet) data : null;
   }

   public static boolean isTimeSeriesVisible(ChartInfo info, DataRef ref) {
      if(ref == null || !XSchema.isDateType(ref.getDataType())) {
         return false;
      }

      return info == null || !GraphTypeUtil.isWaterfall(info) &&
         !GraphTypeUtil.isPolar(info, false) && !GraphTypes.isScatteredContour(info.getChartType()) &&
         (!GraphTypeUtil.isMergedGraphType(info) || info.getChartType() == GraphTypes.CHART_STOCK ||
            info.getChartType() == GraphTypes.CHART_CANDLE ||
            info.getChartType() == GraphTypes.CHART_BOXPLOT);
   }

   /**
    * Get the measures in the chart.
    *
    * @param info chart info.
    * @param rt   whether get runtime measures.
    *
    * @return chart measures.
    */
   public static List<String> getMeasuresName(ChartInfo info, boolean rt) {
      List<String> measures = new ArrayList<>();
      HashSet<String> yFields = new HashSet<>();
      HashSet<String> xFields = new HashSet<>();
      ChartRef[] chartRefsY = info.getModelRefsY(rt);

      for(ChartRef chartRefY : chartRefsY) {
         final String name = chartRefY.getFullName();
         yFields.add(name);
         measures.add(name);
      }

      ChartRef[] chartRefsX = info.getModelRefsX(rt);

      for(ChartRef chartRefX : chartRefsX) {
         final String name = chartRefX.getFullName();

         if(!yFields.contains(name)) {
            xFields.add(name);
            measures.add(name);
         }
      }

      VSDataRef[][] bindingrefs;

      if(rt) {
         bindingrefs = new VSDataRef[][]{ info.getRTXFields(), info.getRTYFields() };
      }
      else {
         bindingrefs = new VSDataRef[][]{ info.getXFields(), info.getYFields() };
      }

      for(VSDataRef[] refs : bindingrefs) {
         for(VSDataRef vsdr : refs) {
            if(!(vsdr instanceof XDimensionRef)) {
               continue;
            }

            XDimensionRef dim = (XDimensionRef) vsdr;
            boolean others = false;

            if(dim.isDateTime() && dim.isTimeSeries() && GraphTypeUtil.supportsTimeSeries(info) &&
               !others && !GraphUtil.isOuterDimRef(dim, info, rt))
            {
               int dlevel = dim.getDateLevel();

               // time series of date part is meaningless
               if((dlevel & XConstants.PART_DATE_GROUP) == 0) {
                  String name = dim.getFullName();

                  if(!yFields.contains(name) && !xFields.contains(name)) {
                     measures.add(name);
                  }
               }
            }
         }
      }

      return measures;
   }

   /**
    * The default format defined in source cannot be know when create the aggregate field,
    * use default format from source to replace the default format which created according
    * to the data type, to avoid the display text format different with the formats pane.
    */
   public static void fixDefaultFormat(ChartInfo cinfo, ChartDescriptor chartDesc, DataSet data) {
      AestheticRef textField = cinfo.getTextField();
      DataRef dataRef = textField == null ? null : textField.getDataRef();
      fixDefaultFormatForText(cinfo, chartDesc, data, dataRef);
      fixDefaultFormatForText(cinfo, chartDesc, data, cinfo.getRTTextField());
      List<ChartAggregateRef> aggrs = cinfo == null ? null : cinfo.getAestheticAggregateRefs(true);

      if(cinfo instanceof CandleVSChartInfo) {
         String measure = cinfo.getDefaultMeasure();
         ChartRef ref = cinfo.getFieldByName(measure, true);

         if(ref instanceof ChartAggregateRef) {
            aggrs.add((ChartAggregateRef) ref);
         }
      }

      if(aggrs == null || aggrs.size() == 0) {
         return;
      }

      AxisDescriptor axisDesc = cinfo.getAxisDescriptor();
      AxisDescriptor axisDesc2 = cinfo.getAxisDescriptor2();

      for(int i = 0; i < aggrs.size(); i++) {
         ChartAggregateRef ref = aggrs.get(i);
         AxisDescriptor axisDes3 = ref.getAxisDescriptor();

         for(AxisDescriptor axis : new AxisDescriptor[] { axisDesc, axisDesc2, axisDes3}) {
            CompositeTextFormat textFormat = axis != null
               ? axis.getColumnLabelTextFormat(ref.getFullName()) : null;

            if(textFormat == null) {
               continue;
            }

            XFormatInfo userDefined = textFormat.getUserDefinedFormat().getFormat();

            if(userDefined != null && !userDefined.isEmpty()) {
               continue;
            }

            // default format from source.
            Format dfmt = GraphFormatUtil.getDefaultFormat(data, cinfo, chartDesc, ref.getFullName());

            if(dfmt != null) {
               textFormat.getDefaultFormat().setFormat(new XFormatInfo(dfmt));
            }
         }
      }
   }

   private static void fixDefaultFormatForText(ChartInfo cinfo, ChartDescriptor chartDesc,
                                               DataSet data, DataRef textField)
   {
      if(textField instanceof ChartAggregateRef) {
         ChartAggregateRef aggr = (ChartAggregateRef) textField;
         Format dfmt = GraphFormatUtil.getDefaultFormat(data, cinfo, chartDesc,  aggr.getFullName());

         if(dfmt != null) {
            aggr.getTextFormat().getDefaultFormat().setFormat(new XFormatInfo(dfmt));
         }
      }
   }

   public static boolean isNodeAestheticFrame(VisualFrame frame, GraphElement elem) {
      if(elem instanceof RelationElement) {
         ColorFrame nodeColor = ((RelationElement) elem).getNodeColorFrame();
         SizeFrame nodeSize = ((RelationElement) elem).getNodeSizeFrame();

         if(nodeColor == frame || nodeSize == frame) {
            return true;
         }

         if(nodeColor instanceof CompositeColorFrame) {
            return ((CompositeColorFrame) nodeColor).getFrames(ColorFrame.class)
               .anyMatch(f -> f == frame);
         }
      }

      return false;
   }

   private static String getAestheticType(int dropType) {
      if(dropType == ChartConstants.DROP_REGION_COLOR) {
         return ChartArea.COLOR_LEGEND;
      }
      else if(dropType == ChartConstants.DROP_REGION_SHAPE) {
         return ChartArea.SHAPE_LEGEND;
      }
      else if(dropType == ChartConstants.DROP_REGION_SIZE) {
         return ChartArea.SIZE_LEGEND;
      }

      return null;
   }

   public static boolean isXBandingEnabled(ChartInfo info) {
      if(!isXYBindingSupportType(info)) {
         return false;
      }

      if(GraphTypes.isRadar(info.getRTChartType()) ||
         GraphTypes.isRelation(info.getRTChartType()))
      {
         ChartRef[] xRefs = info.getRTXFields();
         return Arrays.stream(xRefs).anyMatch(r -> r instanceof XDimensionRef);
      }

      return true;
   }

   public static boolean isYBandingEnabled(ChartInfo info) {
      if(!isXYBindingSupportType(info)) {
         return false;
      }

      if(GraphTypes.isRadar(info.getRTChartType()) ||
         GraphTypes.isRelation(info.getRTChartType()))
      {
         ChartRef[] yRefs = info.getRTYFields();
         return Arrays.stream(yRefs).anyMatch(r -> r instanceof XDimensionRef);
      }

      return true;
   }

   private static boolean isXYBindingSupportType(ChartInfo info) {
      if(GraphTypes.isMekko(info.getRTChartType()) || GraphTypeUtil.isWordCloud(info) ||
         info.getRTChartType() == GraphTypes.CHART_TREEMAP ||
         info.getRTChartType() == GraphTypes.CHART_ICICLE)
      {
         return false;
      }

      return true;
   }

   /**
    * Copy static color to word cloud text color.
    */
   public static void syncWorldCloudColor(ChartInfo ncinfo) {
      if(GraphTypeUtil.isWordCloud(ncinfo)) {
         ColorFrameWrapper color = ncinfo.getColorFrameWrapper();
         AestheticRef textField = ncinfo.getTextField();

         if(!(color instanceof StaticColorFrameWrapper) || textField == null ||
            !(textField.getDataRef() instanceof ChartRef))
         {
            return;
         }

         DataRef ref = textField.getDataRef();
         TextFormat userfmt = ((ChartRef) ref).getTextFormat().getUserDefinedFormat();

         if(!userfmt.isColorDefined()) {
            userfmt.setColor(((StaticColorFrameWrapper) color).getColor(), true);
         }
      }
   }

   public static boolean preferWordCloud(ChartInfo info) {
      if(info == null || !GraphTypes.isPoint(info.getRTChartType()) || info.isMultiAesthetic() ||
         GraphTypeUtil.containsMeasure(info.getXFields()) || GraphTypeUtil.containsMeasure(info.getYFields()) ||
         info.getShapeField() != null || info.getTextField() == null ||
         !(info.getTextField().getDataRef() instanceof ChartDimensionRef) ||
         !(info.getShapeFrame() instanceof StaticShapeFrame) ||
         info.isShapeChanged() && info.getShapeFrame().getShape(null) != GShape.NIL)
      {
         return false;
      }

      AestheticRef size = info.getSizeField();
      StaticShapeFrame shapeFrame = (StaticShapeFrame) info.getShapeFrame();

      if(size != null ||
         size == null && (!info.isSizeChanged() || shapeFrame.getShape(null) == GShape.NIL))
      {
         return true;
      }

      return false;
   }

   public static HighlightGroup getTextHighlightGroup(HighlightRef ref, ChartInfo info) {
      // word cloud highlight uses regular highlighting instead of text highlighting.
      if(GraphTypeUtil.isWordCloud(info)) {
         return ref.getHighlightGroup();
      }
      else {
         return ref.getTextHighlightGroup();
      }
   }

   public static void setTextHighlightGroup(HighlightRef ref, ChartInfo info, HighlightGroup hl) {
      if(GraphTypeUtil.isWordCloud(info)) {
         ref.setHighlightGroup(hl);
      }
      else {
         ref.setTextHighlightGroup(hl);
      }
   }

   /**
    * For the high/low/open/close regions of the Candle and Stock charts, get the default formula.
    */
   public static AggregateFormula getDefaultFormula(ChartInfo info, int dropRegion) {
      if(info == null) {
         return null;
      }

      AggregateFormula formula = null;
      List<ChartDimensionRef> refs = getXYDateDimensionsRef(info);

      if(refs.size() > 0) {
         if(dropRegion == ChartConstants.DROP_REGION_HIGH) {
            formula = AggregateFormula.MAX;
         }
         else if(dropRegion == ChartConstants.DROP_REGION_LOW) {
            formula = AggregateFormula.MIN;
         }
         else if(dropRegion == ChartConstants.DROP_REGION_OPEN) {
            formula = AggregateFormula.FIRST;
         }
         else if(dropRegion == ChartConstants.DROP_REGION_CLOSE) {
            formula = AggregateFormula.LAST;
         }
      }

      return formula;
   }

   public static List<ChartDimensionRef> getXYDateDimensionsRef(ChartInfo info) {
      return getXYDimensions(info).stream()
         .filter(XDimensionRef::isDate)
         .map(ChartDimensionRef.class::cast)
         .collect(Collectors.toList());
   }

   /**
    * Check whether the chart is valid.
    */
   public static boolean isValidChart(AbstractChartInfo info) {
      if(info == null) {
         return false;
      }

      if(info.isMultiStyles()) {
         VSDataRef[] refs = info.getAggregateRefs();

         return Arrays.stream(refs)
            .filter(ref -> ref instanceof ChartAggregateRef)
            .map(ref -> (ChartAggregateRef) ref)
            .allMatch(ref -> ref.getRTChartType() != -1);
      }
      else {
         return info.getRTChartType() != -1;
      }
   }

   public static void addDashboardParameters(Hyperlink.Ref ref, VariableTable vtable,
                                       Hashtable<String, SelectionVSAssembly> selections)
   {
      if(ref != null) {
         if(ref.isSendReportParameters()) {
            addLinkParameter(ref, vtable);
         }

         if(ref.isSendSelectionParameters()) {
            VSUtil.addSelectionParameter(ref, selections);
         }
      }
   }

   /**
    * Add hyperlink parameter.
    * @param hlink the hyperlink to be set parameters.
    * @param vtable the variable table from sand box.
    */
   private static void addLinkParameter(Hyperlink.Ref hlink, VariableTable vtable) {
      if(vtable == null) {
         return;
      }

      List<Object> exists = new ArrayList<>();
      Enumeration pnames = hlink.getParameterNames();
      Enumeration vnames = vtable.keys();

      while(pnames.hasMoreElements()) {
         exists.add(pnames.nextElement());
      }

      while(vnames.hasMoreElements()) {
         String name = (String) vnames.nextElement();

         if(exists.contains(name) || VariableTable.isContextVariable(name)) {
            continue;
         }

         try {
            hlink.setParameter(name, vtable.get(name));
         }
         catch(Exception e) {
         }
      }
   }

   // date levels used for binding
   private static final int[][] dateLevel = {
      { DateRangeRef.YEAR_INTERVAL, DateRangeRef.QUARTER_OF_YEAR_PART },
      { DateRangeRef.QUARTER_INTERVAL, DateRangeRef.MONTH_OF_YEAR_PART },
      { DateRangeRef.MONTH_INTERVAL, DateRangeRef.DAY_OF_MONTH_PART },
      { DateRangeRef.WEEK_INTERVAL, DateRangeRef.DAY_OF_WEEK_PART },
      { DateRangeRef.DAY_INTERVAL, DateRangeRef.HOUR_OF_DAY_PART },
      { DateRangeRef.HOUR_INTERVAL, DateRangeRef.MINUTE_INTERVAL },
      { DateRangeRef.MINUTE_INTERVAL, DateRangeRef.SECOND_INTERVAL },
      { DateRangeRef.SECOND_INTERVAL, DateRangeRef.SECOND_INTERVAL },
      { DateRangeRef.QUARTER_OF_YEAR_PART, DateRangeRef.MONTH_OF_YEAR_PART },
      { DateRangeRef.MONTH_OF_YEAR_PART, DateRangeRef.DAY_OF_MONTH_PART },
      { DateRangeRef.WEEK_OF_YEAR_PART, DateRangeRef.DAY_OF_WEEK_PART },
      { DateRangeRef.DAY_OF_MONTH_PART, DateRangeRef.HOUR_OF_DAY_PART },
      { DateRangeRef.DAY_OF_WEEK_PART, DateRangeRef.HOUR_OF_DAY_PART },
      { DateRangeRef.HOUR_OF_DAY_PART, DateRangeRef.MINUTE_OF_HOUR_PART },
      { DateRangeRef.MINUTE_OF_HOUR_PART, DateRangeRef.SECOND_OF_MINUTE_PART },
      { DateRangeRef.SECOND_OF_MINUTE_PART, DateRangeRef.SECOND_OF_MINUTE_PART } };

   // date levels used for drilling
   private static final int[][] drillLevel = {
      { DateRangeRef.YEAR_INTERVAL, DateRangeRef.QUARTER_OF_YEAR_PART },
      { DateRangeRef.QUARTER_INTERVAL, DateRangeRef.MONTH_INTERVAL },
      { DateRangeRef.MONTH_INTERVAL, DateRangeRef.DAY_INTERVAL },
      { DateRangeRef.WEEK_INTERVAL, DateRangeRef.DAY_INTERVAL },
      { DateRangeRef.DAY_INTERVAL, DateRangeRef.HOUR_INTERVAL },
      { DateRangeRef.HOUR_INTERVAL, DateRangeRef.MINUTE_INTERVAL },
      { DateRangeRef.MINUTE_INTERVAL, DateRangeRef.SECOND_INTERVAL },
      { DateRangeRef.QUARTER_OF_YEAR_PART, DateRangeRef.MONTH_OF_YEAR_PART },
      { DateRangeRef.MONTH_OF_YEAR_PART, DateRangeRef.DAY_OF_MONTH_PART },
      { DateRangeRef.WEEK_OF_YEAR_PART, DateRangeRef.DAY_OF_WEEK_PART },
      { DateRangeRef.DAY_OF_MONTH_PART, DateRangeRef.HOUR_OF_DAY_PART },
      { DateRangeRef.DAY_OF_WEEK_PART, DateRangeRef.HOUR_OF_DAY_PART },
      { DateRangeRef.HOUR_OF_DAY_PART, DateRangeRef.MINUTE_OF_HOUR_PART },
      { DateRangeRef.MINUTE_OF_HOUR_PART, DateRangeRef.SECOND_OF_MINUTE_PART },
      { DateRangeRef.SECOND_OF_MINUTE_PART, DateRangeRef.SECOND_OF_MINUTE_PART } };

   private static final Map<Object, Integer> dateRank = new HashMap<>();

   static {
      dateRank.put(DateRangeRef.YEAR_INTERVAL, 1);
      dateRank.put(DateRangeRef.QUARTER_INTERVAL, 2);
      dateRank.put(DateRangeRef.MONTH_INTERVAL, 3);
      dateRank.put(DateRangeRef.WEEK_INTERVAL, 4);
      dateRank.put(DateRangeRef.DAY_INTERVAL, 5);
      dateRank.put(DateRangeRef.HOUR_INTERVAL, 6);
      dateRank.put(DateRangeRef.MINUTE_INTERVAL, 7);
      dateRank.put(DateRangeRef.SECOND_INTERVAL, 8);

      dateRank.put(DateRangeRef.QUARTER_OF_YEAR_PART, 2);
      dateRank.put(DateRangeRef.MONTH_OF_YEAR_PART, 3);
      dateRank.put(DateRangeRef.WEEK_OF_YEAR_PART, 4);
      dateRank.put(DateRangeRef.DAY_OF_MONTH_PART, 5);
      dateRank.put(DateRangeRef.DAY_OF_WEEK_PART, 5);
      dateRank.put(DateRangeRef.HOUR_OF_DAY_PART, 6);
      dateRank.put(DateRangeRef.MINUTE_OF_HOUR_PART, 7);
      dateRank.put(DateRangeRef.SECOND_OF_MINUTE_PART, 8);
   }

   // @by ankitmathur, For feature1408091348722, This map is copied from
   // CSSDictionary.VSCSSDocumentHandler. However, it contains revised
   // formatting for the Hex strings so that they can easily be passed into
   // Tool.getColorFromHexString() without too much extra work on the fly.
   private static final HashMap<String, String> colormap = new HashMap<>();

   static {
      colormap.put("aliceblue", "#F0F8FF");
      colormap.put("antiquewhite", "#FAEBD7");
      colormap.put("aqua", "#00FFFF");
      colormap.put("aquamarine", "#7FFFD4");
      colormap.put("azure", "#F0FFFF");
      colormap.put("beige", "#F5F5DC");
      colormap.put("bisque", "#FFE4C4");
      colormap.put("black", "#000000");
      colormap.put("blanchedalmond", "#FFEBCD");
      colormap.put("blue", "#0000FF");
      colormap.put("blueviolet", "#8A2BE2");
      colormap.put("brown", "#A52A2A");
      colormap.put("burlywood", "#DEB887");
      colormap.put("cadetblue", "#5F9EA0");
      colormap.put("chartreuse", "#7FFF00");
      colormap.put("chocolate", "#D2691E");
      colormap.put("coral", "#FF7F50");
      colormap.put("cornflowerblue", "#6495ED");
      colormap.put("cornsilk", "#FFF8DC");
      colormap.put("crimson", "#DC143C");
      colormap.put("cyan", "#00FFFF");
      colormap.put("darkblue", "#00008B");
      colormap.put("darkcyan", "#008B8B");
      colormap.put("darkgoldenrod", "#B8860B");
      colormap.put("darkgray", "#A9A9A9");
      colormap.put("darkgreen", "#006400");
      colormap.put("darkkhaki", "#BDB76B");
      colormap.put("darkmagenta", "#8B008B");
      colormap.put("darkolivegreen", "#556B2F");
      colormap.put("darkorange", "#FF8C00");
      colormap.put("darkorchid", "#9932CC");
      colormap.put("darkred", "#8B0000");
      colormap.put("darksalmon", "#E9967A");
      colormap.put("darkseagreen", "#8FBC8B");
      colormap.put("darkslateblue", "#483D8B");
      colormap.put("darkslategray", "#2F4F4F");
      colormap.put("darkturquoise", "#00CED1");
      colormap.put("darkviolet", "#9400D3");
      colormap.put("deeppink", "#FF1493");
      colormap.put("deepskyblue", "#00BFFF");
      colormap.put("dimgray", "#696969");
      colormap.put("dodgerblue", "#1E90FF");
      colormap.put("firebrick", "#B22222");
      colormap.put("floralwhite", "#FFFAF0");
      colormap.put("forestgreen", "#228B22");
      colormap.put("fuchsia", "#FF00FF");
      colormap.put("gainsboro", "#DCDCDC");
      colormap.put("ghostwhite", "#F8F8FF");
      colormap.put("gold", "#FFD700");
      colormap.put("goldenrod", "#DAA520");
      colormap.put("gray", "#808080");
      colormap.put("green", "#008000");
      colormap.put("greenyellow", "#ADFF2F");
      colormap.put("honeydew", "#F0FFF0");
      colormap.put("hotpink", "#FF69B4");
      colormap.put("indianred", "#CD5C5C");
      colormap.put("indigo", "#4B0082");
      colormap.put("ivory", "#FFFFF0");
      colormap.put("khaki", "#F0E68C");
      colormap.put("lavender", "#E6E6FA");
      colormap.put("lavenderblush", "#FFF0F5");
      colormap.put("lawngreen", "#7CFC00");
      colormap.put("lemonchiffon", "#FFFACD");
      colormap.put("lightblue", "#ADD8E6");
      colormap.put("lightcoral", "#F08080");
      colormap.put("lightcyan", "#E0FFFF");
      colormap.put("lightgoldenrodyellow", "#FAFAD2");
      colormap.put("lightgreen", "#90EE90");
      colormap.put("lightgrey", "#D3D3D3");
      colormap.put("lightpink", "#FFB6C1");
      colormap.put("lightsalmon", "#FFA07A");
      colormap.put("lightseagreen", "#20B2AA");
      colormap.put("lightskyblue", "#87CEFA");
      colormap.put("lightslategray", "#778899");
      colormap.put("lightsteelblue", "#B0C4DE");
      colormap.put("lightyellow", "#FFFFE0");
      colormap.put("lime", "#00FF00");
      colormap.put("limegreen", "#32CD32");
      colormap.put("linen", "#FAF0E6");
      colormap.put("magenta", "#FF00FF");
      colormap.put("maroon", "#800000");
      colormap.put("mediumaquamarine", "#66CDAA");
      colormap.put("mediumblue", "#0000CD");
      colormap.put("mediumorchid", "#BA55D3");
      colormap.put("mediumpurple", "#9370DB");
      colormap.put("mediumseagreen", "#3CB371");
      colormap.put("mediumslateblue", "#7B68EE");
      colormap.put("mediumspringgreen", "#00FA9A");
      colormap.put("mediumturquoise", "#48D1CC");
      colormap.put("mediumvioletred", "#C71585");
      colormap.put("midnightblue", "#191970");
      colormap.put("mintcream", "#F5FFFA");
      colormap.put("mistyrose", "#FFE4E1");
      colormap.put("moccasin", "#FFE4B5");
      colormap.put("navajowhite", "#FFDEAD");
      colormap.put("navy", "#000080");
      colormap.put("oldlace", "#FDF5E6");
      colormap.put("olive", "#808000");
      colormap.put("olivedrab", "#6B8E23");
      colormap.put("orange", "#FFA500");
      colormap.put("orangered", "#FF4500");
      colormap.put("orchid", "#DA70D6");
      colormap.put("palegoldenrod", "#EEE8AA");
      colormap.put("palegreen", "#98FB98");
      colormap.put("paleturquoise", "#AFEEEE");
      colormap.put("palevioletred", "#DB7093");
      colormap.put("papayawhip", "#FFEFD5");
      colormap.put("peachpuff", "#FFDAB9");
      colormap.put("peru", "#CD853F");
      colormap.put("pink", "#FFC0CB");
      colormap.put("plum", "#DDA0DD");
      colormap.put("powderblue", "#B0E0E6");
      colormap.put("purple", "#800080");
      colormap.put("red", "#FF0000");
      colormap.put("rosybrown", "#BC8F8F");
      colormap.put("royalblue", "#4169E1");
      colormap.put("saddlebrown", "#8B4513");
      colormap.put("salmon", "#FA8072");
      colormap.put("sandybrown", "#F4A460");
      colormap.put("seagreen", "#2E8B57");
      colormap.put("seashell", "#FFF5EE");
      colormap.put("sienna", "#A0522D");
      colormap.put("silver", "#C0C0C0");
      colormap.put("skyblue", "#87CEEB");
      colormap.put("slateblue", "#6A5ACD");
      colormap.put("slategray", "#708090");
      colormap.put("snow", "#FFFAFA");
      colormap.put("springgreen", "#00FF7F");
      colormap.put("steelblue", "#4682B4");
      colormap.put("tan", "#D2B48C");
      colormap.put("teal", "#008080");
      colormap.put("thistle", "#D8BFD8");
      colormap.put("tomato", "#FF6347");
      colormap.put("turquoise", "#40E0D0");
      colormap.put("violet", "#EE82EE");
      colormap.put("wheat", "#F5DEB3");
      colormap.put("white", "#FFFFFF");
      colormap.put("whitesmoke", "#F5F5F5");
      colormap.put("yellow", "#FFFF00");
      colormap.put("yellowgreen", "#9ACD32");
   }

   private static final Logger LOG = LoggerFactory.getLogger(GraphUtil.class);
}
