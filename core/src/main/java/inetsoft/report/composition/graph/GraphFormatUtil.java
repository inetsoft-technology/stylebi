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

import inetsoft.graph.data.*;
import inetsoft.graph.scale.Scale;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.report.internal.StyleCore;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.ExtendedDecimalFormat;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import inetsoft.web.binding.handler.ChartDndHandler;
import inetsoft.web.binding.model.graph.OriginalDescriptor;
import inetsoft.web.graph.handler.ChartRegionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Format;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class contains utility methods for graph format related logic.
 *
 * @author InetSoft Technology Corp.
 * @since  13.5
 */
public class GraphFormatUtil {
   /**
    * Get the default format from DataSet/ChartInfo/ChartDescriptor.
    */
   public static Format getDefaultFormat(DataSet data, ChartInfo info, ChartDescriptor desc,
                                         String... cols)
   {
      Format fmt;
      AttributeDataSet attr = data instanceof AttributeDataSet ? (AttributeDataSet) data : null;
      List<ChartRef> xyfields = new ArrayList<>();
      xyfields.addAll(Arrays.asList(info.getRTXFields()));
      xyfields.addAll(Arrays.asList(info.getRTYFields()));

      if(data instanceof BoxDataSet) {
         if((fmt = getDefaultFormat(cols, xyfields)) != null) {
            return fmt;
         }

         DataSet dataSet = ((BoxDataSet) data).getDataSet();
         attr = dataSet instanceof AttributeDataSet ? ((AttributeDataSet) dataSet) : null;
      }

      if(attr == null || attr.getRowCount() == 0 || cols == null) {
         return null;
      }

      for(int i = 0; i < cols.length; i++) {
         fmt = attr.getFormat(cols[i], 0);

         if(fmt != null) {
            return fmt;
         }
      }

      // get report format
      Map<Class<?>, Format> formatmap = desc.getFormats();

      if(formatmap != null && !formatmap.isEmpty()) {
         int times = Math.min(100, attr.getRowCount()); // try at most 100 times

         for(int i = 0; i < cols.length; i++) {
            for(int j = 0; j < times; j++) {
               Object val = attr.getData(cols[i], j);

               if(val == null) {
                  continue;
               }

               fmt = StyleCore.getFormat(formatmap, val.getClass());

               if(fmt != null) {
                  return fmt;
               }
            }
         }
      }

      if(info instanceof RelationChartInfo) {
         xyfields.add(((RelationChartInfo) info).getRTSourceField());
         xyfields.add(((RelationChartInfo) info).getRTTargetField());
      }

      if((fmt = getDefaultFormat(cols, xyfields)) != null) {
         return fmt;
      }

      return null;
   }

   /**
    * Get the default format for date columns.
    */
   private static Format getDefaultFormat(String[] cols, List<ChartRef> dims) {
      for(String col : cols) {
         for(ChartRef dim : dims) {
            boolean isSecondaryY = dim instanceof ChartAggregateRef &&
               ((ChartAggregateRef) dim).isSecondaryY();

            if(dim == null || col == null || !col.equals(dim.getFullName())) {
               continue;
            }

            if(dim instanceof XDimensionRef) {
               return VSFrameVisitor.getDefaultFormat((XDimensionRef) dim);
            }
            else if(dim instanceof XAggregateRef && (cols.length < 2 || !isSecondaryY)) {
               Calculator calc = ((XAggregateRef) dim).getCalculator();

               if(calc != null && calc.isPercent()) {
                  return NumberFormat.getPercentInstance();
               }
            }
         }
      }

      return null;
   }

   public static void setDefaultNumberFormat(ChartDescriptor chartDescriptor, ChartInfo chartInfo,
                                             String dtype, ChartAggregateRef agg, int dropType)
   {
      setDefaultNumberFormat(chartDescriptor, chartInfo, dtype, agg, dropType, false, false);
   }

   /**
    * @param dtype
    * @param agg             the target aggregate to set default number format.
    * @param runtime         if need to set default format for runtime composite formats.
    * @param percent         if need to user percent as default number format.
    */
   public static void setDefaultNumberFormat(ChartDescriptor chartDescriptor, ChartInfo chartInfo,
                                             String dtype, ChartAggregateRef agg, int dropType,
                                             boolean runtime, boolean percent)
   {
      try {
         if(XSchema.isNumericType(dtype)) {
            List<XFormatInfo> formats =
               getFormatInfos(chartDescriptor, chartInfo, agg, dropType, runtime);

            formats.forEach(format -> {
               format.setFormat(percent ? TableFormat.PERCENT_FORMAT : TableFormat.DECIMAL_FORMAT);
               format.setFormatSpec(ExtendedDecimalFormat.AUTO_FORMAT);
            });
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to set default format: " + ex, ex);
      }
   }

   private static List<XFormatInfo> getFormatInfos(ChartDescriptor chartDescriptor,
                                                   ChartInfo chartInfo,
                                                   ChartAggregateRef agg, int dropType,
                                                   boolean runtime)
   {
      if(ChartDndHandler.isAestheticRegion(dropType)) {
         LegendsDescriptor legendsDesc = chartDescriptor.getLegendsDescriptor();
         final List<LegendDescriptor> legendDescriptors = new ArrayList<>();

         if(chartInfo instanceof RelationChartInfo) {
            AestheticRef aestheticRef = chartInfo.getColorField();

            if(aestheticRef != null && aestheticRef.getDataRef() instanceof ChartAggregateRef) {
               legendDescriptors.add(aestheticRef.getLegendDescriptor());
            }

            aestheticRef = ((RelationChartInfo) chartInfo).getNodeColorField();

            if(aestheticRef != null && aestheticRef.getDataRef() instanceof ChartAggregateRef) {
               legendDescriptors.add(aestheticRef.getLegendDescriptor());
            }

            aestheticRef = chartInfo.getSizeField();

            if(aestheticRef != null && aestheticRef.getDataRef() instanceof ChartAggregateRef) {
               legendDescriptors.add(aestheticRef.getLegendDescriptor());
            }

            aestheticRef = ((RelationChartInfo) chartInfo).getNodeSizeField();

            if(aestheticRef != null && aestheticRef.getDataRef() instanceof ChartAggregateRef) {
               legendDescriptors.add(aestheticRef.getLegendDescriptor());
            }
         }

         AestheticRef colorField = chartInfo.getColorField();

         if(!(chartInfo instanceof RelationChartInfo) && colorField != null &&
            colorField.getDataRef() instanceof ChartAggregateRef)
         {
            legendDescriptors.add(legendsDesc.getColorLegendDescriptor());
         }

         AestheticRef shapeField = chartInfo.getShapeField();

         if(shapeField != null && shapeField.getDataRef() instanceof ChartAggregateRef) {
            legendDescriptors.add(legendsDesc.getShapeLegendDescriptor());
         }

         AestheticRef sizeField = chartInfo.getSizeField();

         if(!(chartInfo instanceof RelationChartInfo) && sizeField != null &&
            sizeField.getDataRef() instanceof ChartAggregateRef)
         {
            legendDescriptors.add(legendsDesc.getSizeLegendDescriptor());
         }

         final List<XFormatInfo> formats = new ArrayList<>();
         formats.add(agg.getTextFormat().getFormat());

         legendDescriptors.stream()
            .filter(Objects::nonNull)
            .map(legendDescriptor -> {
               CompositeTextFormat format = legendDescriptor.getContentTextFormat();

               if(format == null) {
                  format = new CompositeTextFormat();
                  legendDescriptor.setContentTextFormat(format);
               }

               format.getCSSFormat().setCSSType(CSSConstants.CHART_LEGEND_CONTENT);
               return format.getDefaultFormat().getFormat();
            })
            .forEach(formats::add);

         return formats;
      }

      boolean fixColumnName = GraphUtil.isHighLowRegion(dropType)
         && dropType != ChartConstants.DROP_REGION_HIGH;
      String columnName = fixColumnName ? OriginalDescriptor.HIGH : agg.getFullName();

      final List<CompositeTextFormat> formats = new ArrayList<>();
      getAxisLabelFormats(chartInfo, columnName, agg, dropType, formats, false);

      if(runtime) {
         getAxisLabelFormats(chartInfo, columnName, agg, dropType, formats, true);
      }

      String index = ChartRegionHandler.getXfieldsIndex(chartInfo, columnName, true);
      formats.stream().map(CompositeTextFormat::getCSSFormat)
         .forEach(f -> f.addCSSAttribute("axis", index));

      if(fixColumnName) {
         return formats.stream()
            .map(CompositeTextFormat::getUserDefinedFormat)
            .map(TextFormat::getFormat)
            .collect(Collectors.toList());
      }
      else {
         return formats.stream()
            .map(fmt -> fmt.getDefaultFormat().getFormat())
            .collect(Collectors.toList());
      }
   }

   private static void getAxisLabelFormats(ChartInfo chartInfo, String columnName,
                                           ChartAggregateRef aggr, int dropType,
                                           List<CompositeTextFormat> formats, boolean runtime)
   {
      AxisDescriptor descriptor = GraphUtil.getAxisDescriptor(
         chartInfo, GraphUtil.isHighLowRegion(dropType) ? null : aggr);

      AxisDescriptor rtAxis = null;
      AxisDescriptor rtAxis2 = null;

      if(chartInfo instanceof VSChartInfo && runtime) {
         rtAxis = ((VSChartInfo) chartInfo).getRTAxisDescriptor();
         rtAxis2 = ((VSChartInfo) chartInfo).getRTAxisDescriptor2();
      }

      if(descriptor == chartInfo.getAxisDescriptor() && rtAxis != null) {
         descriptor = rtAxis;
      }

      if(descriptor == chartInfo.getAxisDescriptor2() && rtAxis2 != null) {
         descriptor = rtAxis2;
      }

      AxisDescriptor axis = rtAxis != null ? rtAxis : chartInfo.getAxisDescriptor();
      AxisDescriptor axis2 = rtAxis2 != null ? rtAxis2 : chartInfo.getAxisDescriptor2();

      CompositeTextFormat format;
      CompositeTextFormat format2 = null;

      if(chartInfo.isSeparatedGraph()) {
         format = descriptor.getColumnLabelTextFormat(columnName);
      }
      else {
         format = axis.getAxisLabelTextFormat();
         format2 = axis2.getAxisLabelTextFormat();
      }

      if(chartInfo.isSeparatedGraph()) {
         if(format == null) {
            format = (CompositeTextFormat) descriptor.getAxisLabelTextFormat().clone();
            descriptor.setColumnLabelTextFormat(columnName, format);
         }

         formats.add(format);
      }
      else {
         if(format == null) {
            format = new CompositeTextFormat();
            axis.setAxisLabelTextFormat(format);
         }

         if(format2 == null) {
            format = new CompositeTextFormat();
            axis2.setAxisLabelTextFormat(format);
         }

         if(aggr != null && !aggr.isSecondaryY()) {
            formats.add(format);
         }
         else if(aggr != null && aggr.isSecondaryY()) {
            formats.add(format2);
         }
         else {
            formats.add(format);
            formats.add(format2);
         }
      }
   }

   /**
    * Get chart format from the descriptors.
    */
   public static void getChartFormat(VSChartInfo vinfo, ChartDescriptor desc,
                                     AxisDescriptor rAxisDesc, List<CompositeTextFormat> list)
   {
      if(vinfo == null || desc == null || list == null) {
         return;
      }

      TitleDescriptor xtitle = desc.getTitlesDescriptor().getXTitleDescriptor();
      TitleDescriptor x2title = desc.getTitlesDescriptor().getX2TitleDescriptor();
      TitleDescriptor ytitle = desc.getTitlesDescriptor().getYTitleDescriptor();
      TitleDescriptor y2title = desc.getTitlesDescriptor().getY2TitleDescriptor();
      PlotDescriptor plot = desc.getPlotDescriptor();
      LegendsDescriptor legends = desc.getLegendsDescriptor();
      LegendDescriptor colorLegend = legends.getColorLegendDescriptor();
      LegendDescriptor shapeLegend = legends.getShapeLegendDescriptor();
      LegendDescriptor sizeLegend = legends.getSizeLegendDescriptor();

      list.add(plot.getTextFormat());
      list.add(xtitle.getTextFormat());
      list.add(x2title.getTextFormat());
      list.add(ytitle.getTextFormat());
      list.add(y2title.getTextFormat());
      list.add(legends.getTitleTextFormat());
      list.add(colorLegend.getContentTextFormat());
      list.add(shapeLegend.getContentTextFormat());
      list.add(sizeLegend.getContentTextFormat());

      if(rAxisDesc != null) {
         list.add(rAxisDesc.getAxisLabelTextFormat());
      }

      if(vinfo.getRTAxisDescriptor2() != null) {
         list.add(vinfo.getRTAxisDescriptor2().getAxisLabelTextFormat());
      }

      // Bug #812, when open viewsheet, execute oninit scrip to set format,
      // runtime fields is null, format can not apply.
      ChartRef[] refs = vinfo.getRTXFields();
      refs = refs == null || refs.length == 0 ? vinfo.getXFields() : refs;
      getFieldsFormat(desc, refs, list);
      refs = vinfo.getRTYFields();
      refs = refs == null || refs.length == 0 ? vinfo.getYFields() : refs;
      getFieldsFormat(desc, refs, list);
      refs = vinfo.getRTGroupFields();
      refs = refs == null || refs.length == 0 ? vinfo.getGroupFields() : refs;
      getFieldsFormat(desc, refs, list);
   }

   /**
    * Get chart format from the fields.
    */
   private static List<CompositeTextFormat> getFieldsFormat(
      ChartDescriptor desc, ChartRef[] refs, List<CompositeTextFormat> list)
   {
      for(ChartRef ref : refs) {
         if(ref instanceof VSChartRef) {
            VSChartRef cref = (VSChartRef) ref;

            if(cref.getRTAxisDescriptor() == null) {
               AxisDescriptor rAxisDesc = cref.getAxisDescriptor() == null ?
                  new AxisDescriptor() : cref.getAxisDescriptor().clone();
               cref.setRTAxisDescriptor(rAxisDesc);
            }

            list.add(cref.getRTAxisDescriptor().getAxisLabelTextFormat());

            for(String col : cref.getRTAxisDescriptor().getColumnLabelTextFormatColumns()) {
               list.add(cref.getRTAxisDescriptor().getColumnLabelTextFormat(col));
            }
         }

         if(ref instanceof ChartAggregateRef) {
            PlotDescriptor plot = desc.getPlotDescriptor();
            list.add(getTextFormat((ChartAggregateRef) ref, ref, plot));
         }
      }

      return list;
   }

   /**
    * Get the current text format for this measure. If the text field is
    * bound to an aggregte, the text format of the aggregate is returned.
    * Otherwise the text format of this aggregate (or dimension for treemap) is returned.
    */
   public static CompositeTextFormat getTextFormat(ChartBindable bindable,
                                                   ChartRef aggr, PlotDescriptor plot)
   {
      return getTextFormat(bindable, aggr, plot, true);
   }

   /**
    * Get the current text format for this measure. If the text field is
    * bound to an aggregte, the text format of the aggregate is returned.
    * Otherwise the text format of this aggregate (or dimension for treemap) is returned.
    */
   public static CompositeTextFormat getTextFormat(ChartBindable bindable,
                                                   ChartRef aggr, PlotDescriptor plot,
                                                   boolean applyTextFormat)
   {
      // tree text field is apply on target and not source.
      boolean ignoreTextField = bindable instanceof RelationChartInfo &&
         Objects.equals(aggr, ((RelationChartInfo) bindable).getSourceField());

      if(!ignoreTextField && bindable != null && applyTextFormat) {
         AestheticRef aref = bindable.getTextField();

         if(aref != null) {
            DataRef ref = aref.getDataRef();

            if(ref instanceof ChartRef) {
               return ((ChartRef) ref).getTextFormat();
            }
         }
      }

      if(aggr != null) {
         return aggr.getTextFormat();
      }

      return plot.getTextFormat();
   }

   /**
    * Set the current text format for this measure.
    */
   public static void setTextFormat(ChartBindable bindable, ChartRef aggr, PlotDescriptor plot,
                                    CompositeTextFormat fmt)
   {
      setTextFormat(bindable, aggr, plot, fmt, true);
   }

   /**
    * Set the current text format for this measure.
    */
   public static void setTextFormat(ChartBindable bindable, ChartRef aggr, PlotDescriptor plot,
                                    CompositeTextFormat fmt, boolean applyTextFormat)
   {
      if(bindable != null && applyTextFormat) {
         AestheticRef aref = bindable.getTextField();

         if(aref != null) {
            DataRef ref = aref.getDataRef();

            if(ref instanceof ChartRef) {
               ((ChartRef) ref).setTextFormat(fmt);

               if(bindable instanceof AllChartAggregateRef) {
                  bindable.setTextField(aref);
               }

               return;
            }
         }
      }

      if(aggr != null) {
         aggr.setTextFormat(fmt);
      }
      else {
         plot.setTextFormat(fmt);
      }
   }

   // this is used when editing text format
   public static CompositeTextFormat getBindingTextFormat(ChartBindable bindable, ChartRef aggr,
                                                          PlotDescriptor plot, boolean textField)
   {
      if(bindable instanceof RelationChartInfo) {
         RelationChartInfo tree = (RelationChartInfo) bindable;

         if(aggr != null && tree.getSourceField() != null &&
            Tool.equals(aggr.getFullName(), tree.getSourceField().getFullName()))
         {
            return aggr.getTextFormat();
         }
      }

      // support setting background on packing circle containers.
      if(bindable.getChartType() == GraphTypes.CHART_CIRCLE_PACKING) {
         AestheticRef textFieldRef = bindable.getTextField();

         // don't return vo format when selected the textfield
         if(textFieldRef == null || !Tool.equals(textFieldRef.getDataRef(), aggr)) {
            CompositeTextFormat fmt = getVOTextFormat(aggr != null ? aggr.getFullName() : null,
                                                      (ChartInfo) bindable, plot, true);

            if(fmt != null) {
               return fmt;
            }
         }
      }

      // if show-values and selected text on plot, always edit show-value format.
      // this means the format of show-value is controlled by the format pane
      // and the format of the text field is controlled by the edit in aesthetic binding.
      // NOTE: since we only use one color for the label (show values and text binding),
      // when both exist the color will be controlled by text field format from binding
      // pane only.
      // treemap doesn't support show values so should always use textfield if bound.
      // if textField is true, this is triggered from the 'Edit' button on the text
      // aesthetic field, so we should always edit the format of the aesthetic text field.
      if(!textField && (plot.isValuesVisible() && !GraphTypes.isTreemap(bindable.getChartType()) ||
         bindable.getTextField() == null))
      {
         if(aggr != null) {
            return aggr.getTextFormat();
         }

         return plot.getTextFormat();
      }

      return getTextFormat(bindable, aggr, plot, true);
   }

   // this is used when editing text format (for all text format only)
   public static void setBindingTextFormat(ChartBindable bindable, ChartRef aggr,
                                           PlotDescriptor plot, CompositeTextFormat fmt,
                                           boolean textField)
   {
      setBindingTextFormat(bindable, aggr, plot, fmt, textField, true);
   }

   // this is used when editing text format (for all text format only)
   public static void setBindingTextFormat(ChartBindable bindable, ChartRef aggr,
                                           PlotDescriptor plot, CompositeTextFormat fmt,
                                           boolean textField, boolean applyTextFormat)
   {
      if(bindable instanceof RelationChartInfo) {
         RelationChartInfo tree = (RelationChartInfo) bindable;

         if(aggr != null && tree.getSourceField() != null &&
            Tool.equals(aggr.getFullName(), tree.getSourceField().getFullName()))
         {
            aggr.setTextFormat(fmt);
            return;
         }
      }

      if(!textField && plot.isValuesVisible() || bindable.getTextField() == null || !applyTextFormat) {
         if(bindable.getChartType() == GraphTypes.CHART_CIRCLE_PACKING) {
            setVOTextFormat(aggr != null ? aggr.getFullName() : null, (ChartInfo) bindable, plot, fmt);
            return;
         }

         if(aggr != null) {
            aggr.setTextFormat(fmt);
            return;
         }

         plot.setTextFormat(fmt);
         return;
      }

      setTextFormat(bindable, aggr, plot, fmt, applyTextFormat);
   }

   /**
    * Get the format for circle packing container or the inner most circle/label.
    * @param containerOnly true if only return container (and null for inner-most circle/text).
    */
   public static CompositeTextFormat getVOTextFormat(String colname, ChartInfo cinfo,
                                                     PlotDescriptor desc, boolean containerOnly)
   {
      int level = 0;

      if(colname != null) {
         ChartRef[] refs = cinfo.getRTGroupFields();

         for(int i = 0; i < refs.length; i++) {
            if(Objects.equals(colname, refs[i].getFullName())) {
               // inner-most circle contains text and can support full formatting.
               // return null and let the default logic to get the format from
               // inner dimension ref or texst field ref. (59972)
               if(i == refs.length - 1) {
                  return containerOnly ? null
                     : cinfo.getGroupField(cinfo.getGroupFieldCount() - 1).getTextFormat();
               }

               level = i + 1;
               break;
            }
         }
      }

      return desc.getCircleFormat(level);
   }

   /**
    * Set the format for circle packing container or the inner most circle/label.
    */
   private static void setVOTextFormat(String colname, ChartInfo cinfo, PlotDescriptor desc,
                                       CompositeTextFormat format)
   {
      int level = 0;

      if(colname != null) {
         ChartRef[] refs = cinfo.getRTGroupFields();

         for(int i = 0; i < refs.length; i++) {
            if(Objects.equals(colname, refs[i].getFullName())) {
               if(i == refs.length - 1) {
                  cinfo.getGroupField(cinfo.getGroupFieldCount() - 1).setTextFormat(format);
                  return;
               }

               level = i + 1;
               break;
            }
         }
      }

      desc.setCircleFormat(level, format);
   }

   /**
    * Get the option text format.
    */
   public static CompositeTextFormat getTextFormat(Object aref) {
      return aref instanceof ChartRef ? ((ChartRef) aref).getTextFormat() : null;
   }

   /**
    * set the option text format.
    */
   public static void setTextFormat(Object aref, CompositeTextFormat fmt) {
      if(fmt == null || !(aref instanceof ChartRef)) {
         return;
      }

      ((ChartRef) aref).setTextFormat(fmt);
   }

   // If fix all aggregate refs for number format.
   public static void fixDefaultNumberFormat(ChartDescriptor chartDescriptor, ChartInfo info) {
      List<VSDataRef> aggs = new ArrayList<>();
      addXYAggregates(info.getXFields(), aggs);
      addXYAggregates(info.getYFields(), aggs);
      addXYAggregates(info.getRTXFields(), aggs);
      addXYAggregates(info.getRTYFields(), aggs);

      for(int i = 0; i < aggs.size(); i++) {
         List<XFormatInfo> formats = getAggregateNumberFomrat(info, aggs.get(i));
         setDefaultNumberFormat(formats);

         // make sure new format is applied. (53504)
         if(aggs.get(i) instanceof VSChartAggregateRef) {
            VSChartAggregateRef aref = (VSChartAggregateRef) aggs.get(i);

            if(aref.isDynamicBinding()) {
               aref.setRTAxisDescriptor(aref.getAxisDescriptor());
            }
         }
      }

      fixAllAestheticAggregates(chartDescriptor, info);
   }

   private static void fixAllAestheticAggregates(ChartDescriptor chartDescriptor, ChartInfo info) {
      if(chartDescriptor == null) {
         return;
      }

      List<XFormatInfo> formats = new ArrayList<>();
      LegendsDescriptor legendsDesc = chartDescriptor.getLegendsDescriptor();
      fixAestheticAggregates(info.getColorField(), legendsDesc.getColorLegendDescriptor(), formats);
      fixAestheticAggregates(info.getShapeField(), legendsDesc.getShapeLegendDescriptor(), formats);
      fixAestheticAggregates(info.getSizeField(), legendsDesc.getSizeLegendDescriptor(), formats);

      if(info instanceof RelationChartInfo) {
         AestheticRef aestheticRef = info.getColorField();

         if(aestheticRef != null) {
            fixAestheticAggregates(aestheticRef, aestheticRef.getLegendDescriptor(), formats);
         }

         aestheticRef = ((RelationChartInfo) info).getNodeColorField();

         if(aestheticRef != null) {
            fixAestheticAggregates(aestheticRef, aestheticRef.getLegendDescriptor(), formats);
         }

         aestheticRef = info.getSizeField();

         if(aestheticRef != null) {
            fixAestheticAggregates(aestheticRef, aestheticRef.getLegendDescriptor(), formats);
         }

         aestheticRef = ((RelationChartInfo) info).getNodeSizeField();

         if(aestheticRef != null) {
            fixAestheticAggregates(aestheticRef, aestheticRef.getLegendDescriptor(), formats);
         }
      }

      ChartAggregateRef text = getAestheticAggregate(info.getTextField());

      if(text != null) {
         formats.add(text.getTextFormat().getFormat());
      }

      if(info.isMultiAesthetic() && info instanceof AbstractChartInfo) {
         List<AestheticRef> arefs = ((AbstractChartInfo) info).getAggregateAestheticRefs(false);

         for(AestheticRef ref : arefs) {
            fixAestheticAggregates(ref, ref.getLegendDescriptor(), formats);
         }
      }

      setDefaultNumberFormat(formats);
   }

   // set default number format.
   private static void setDefaultNumberFormat(List<XFormatInfo> formats) {
      formats.stream()
         .filter(format -> format.getFormat() == null)
         .forEach(format -> {
            format.setFormat(TableFormat.DECIMAL_FORMAT);
            format.setFormatSpec(ExtendedDecimalFormat.AUTO_FORMAT);
         });
   }

   public static void setDefaultNumberFormat(XFormatInfo format) {
      if(format == null) {
         return;
      }

      format.setFormat(TableFormat.DECIMAL_FORMAT);
      format.setFormatSpec(ExtendedDecimalFormat.AUTO_FORMAT);
   }

   private static void addXYAggregates(VSDataRef[] refs, List<VSDataRef> aggs) {
      for(int i = 0; i < refs.length; i++) {
         if(refs[i] instanceof XAggregateRef) {
            aggs.add(refs[i]);
         }
      }
   }

   private static ChartAggregateRef getAestheticAggregate(AestheticRef aref) {
      if(aref == null || aref.getDataRef() == null) {
         return null;
      }

      if(!(aref.getDataRef() instanceof ChartAggregateRef)) {
         return null;
      }

      return (ChartAggregateRef) aref.getDataRef();
   }

   private static void fixAestheticAggregates(AestheticRef aref, LegendDescriptor descriptor,
                                              List<XFormatInfo> formats)
   {
      CompositeTextFormat format = descriptor.getContentTextFormat();

      // if binding is not aggregate, should clear the setted default decimal format.
      if(getAestheticAggregate(aref) == null) {
         if(format == null) {
            return;
         }

         XFormatInfo finfo = format.getDefaultFormat().getFormat();

         if(finfo != null && TableFormat.DECIMAL_FORMAT.equals(finfo.getFormat()) &&
            ExtendedDecimalFormat.AUTO_FORMAT.equals(finfo.getFormatSpec()))
         {
            finfo.setFormat(null);
            finfo.setFormatSpec(null);
         }

         return;
      }

      if(format == null) {
         format = new CompositeTextFormat();
         descriptor.setContentTextFormat(format);
      }

      format.getCSSFormat().setCSSType(CSSConstants.CHART_LEGEND_CONTENT);
      formats.add(format.getDefaultFormat().getFormat());
   }

   private static List<XFormatInfo> getAggregateNumberFomrat(ChartInfo cinfo, VSDataRef agg) {
      String columnName = agg.getFullName();
      AxisDescriptor descriptor = GraphUtil.getAxisDescriptor(cinfo, (ChartRef) agg);
      CompositeTextFormat format;
      CompositeTextFormat format2 = null;

      if(cinfo.isSeparatedGraph()) {
         format = descriptor.getColumnLabelTextFormat(columnName);
      }
      else {
         format = cinfo.getAxisDescriptor().getAxisLabelTextFormat();
         format2 = cinfo.getAxisDescriptor2().getAxisLabelTextFormat();
      }

      final List<CompositeTextFormat> formats = new ArrayList<>(2);

      if(cinfo.isSeparatedGraph()) {
         if(format == null) {
            format = (CompositeTextFormat) descriptor.getAxisLabelTextFormat().clone();
            descriptor.setColumnLabelTextFormat(columnName, format);
         }

         formats.add(format);
      }
      else {
         if(format == null) {
            format = new CompositeTextFormat();
            cinfo.getAxisDescriptor().setAxisLabelTextFormat(format);
         }

         if(format2 == null) {
            format = new CompositeTextFormat();
            cinfo.getAxisDescriptor2().setAxisLabelTextFormat(format);
         }

         formats.add(format);
         formats.add(format2);
      }

      if(GraphTypes.isBoxplot(cinfo.getRTChartType())) {
         String measure = agg.getName();
         String[] boxfields = {
            measure,
            BoxDataSet.MIN_PREFIX + measure,
            BoxDataSet.Q25_PREFIX + measure,
            BoxDataSet.MEDIUM_PREFIX + measure,
            BoxDataSet.Q75_PREFIX + measure,
            BoxDataSet.MAX_PREFIX + measure
         };

         CompositeTextFormat oformat = descriptor.getColumnLabelTextFormat(agg.getFullName());

         for(int j = 0; j < boxfields.length; j++) {
            CompositeTextFormat fmt = descriptor.getColumnLabelTextFormat(boxfields[j]);

            if(fmt == null) {
               fmt = oformat != null ? oformat.clone() : new CompositeTextFormat();
               descriptor.setColumnLabelTextFormat(boxfields[j], fmt);
            }

            formats.add(fmt);
         }
      }

      String index = ChartRegionHandler.getXfieldsIndex(cinfo, columnName, true);
      formats.stream()
         .map(CompositeTextFormat::getCSSFormat)
         .forEach(f -> f.addCSSAttribute("axis", index));

      return formats.stream()
         .map(fmt -> fmt.getDefaultFormat().getFormat())
         .collect(Collectors.toList());
   }

   public static DateComparisonFormat getDateComparisonFormat(
      VSChartInfo info, DateComparisonInfo dateComparison, DataSet data,
      Scale xscale, Format format)
   {
      if(!(dateComparison.getPeriods() instanceof StandardPeriods)) {
         return null;
      }

      StandardPeriods periods = (StandardPeriods) dateComparison.getPeriods();
      int granularity = dateComparison.getInterval().getGranularity();
      ChartRef[] refs = info.getRuntimeDateComparisonRefs();
      String xfield = null;

      for(ChartRef ref : refs) {
         String dtype = ref.getDataType();

         if(ref instanceof VSDimensionRef &&
            ((VSDimensionRef) ref).getDateLevel() != DateRangeRef.NONE &&
            (XSchema.isDateType(dtype) || XSchema.isNumericType(dtype)))
         {
            xfield = ref.getFullName();
            break;
         }
      }

      String dcCalcCol = null;

      if(dateComparison.getComparisonOption() == DateComparisonInfo.CHANGE ||
         dateComparison.getComparisonOption() == DateComparisonInfo.PERCENT)
      {
         List<ChartAggregateRef> aggrs = info.getAestheticAggregateRefs(true);

         // only need find the first aggregate which has calculator.
         ChartAggregateRef aggr = aggrs.stream().filter(
            ref -> ref.getCalculator() != null).findFirst().orElse(null);
         dcCalcCol = aggr == null ? null : aggr.getFullName(true);
      }

      String partCol = xscale.getFields()[0];
      ChartRef ref = info.getFieldByName(partCol, true);
      int partLevel = DateRangeRef.NONE_DATE_GROUP;
      DataRef dataRef = ref instanceof VSDimensionRef ? ((VSDimensionRef) ref).getDataRef() : null;

      if(dataRef instanceof CalculateRef && ((CalculateRef) dataRef).isDcRuntime() &&
         dataRef.getName() != null)
      {
         if(dataRef.getName().startsWith("MonthOfQuarter(")) {
            partLevel = DateRangeRef.MONTH_OF_QUARTER_PART;
         }
         else if(dataRef.getName().startsWith("MonthOfQuarterOfWeek(")) {
            partLevel = DateRangeRef.MONTH_OF_QUARTER_FULL_WEEK_PART;
         }
      }

      // if there are extra grouping not on x/y, there will be multiple vo for each point
      // (e.g. multiple quarters for week-of-quarter for each year on x) so showing one
      // week on top axis won't be appropriate.
      boolean hasExtraGroups = info.getDcTempGroups() != null && info.getDcTempGroups().length > 0
         && periods.getDateLevel() != dateComparison.getInterval().getContextLevel();

      return new DateComparisonFormat(data, xscale.getGraphDataSelector(),
                                      periods.getDateLevel(), granularity, partLevel,
                                      partCol, xfield, dcCalcCol, xscale.getValues(), format,
                                      dateComparison.isShowMostRecentDateOnly(), !hasExtraGroups);
   }

   /**
    * This is used by the GUI to change axis label formats.
    */
   public static CompositeTextFormat getAxisLabelTextFormat(
      String columnName, ChartInfo chartInfo, AxisDescriptor descriptor, boolean isX)
   {
      CompositeTextFormat format;
      // keep same with GraphGenerator.getAxisLabelFormat function,
      // incase there's column label text format in axis fmtmap which was added
      // when chart was edited before.
      ChartRef xref = chartInfo.getFieldByName(columnName, true);
      boolean xdim = isX && xref != null && !xref.isMeasure();

      if(chartInfo.isSeparatedGraph() || xdim) {
         format = descriptor.getColumnLabelTextFormat(columnName);

         if(format == null) {
            format = descriptor.getAxisLabelTextFormat().clone();
            descriptor.setColumnLabelTextFormat(columnName, format);
         }

      }
      else {
         format = descriptor.getAxisLabelTextFormat();
      }

      return format;
   }

   /**
    * Copy format (exception FormatInfo) from text field to/from aggregate (for show-values).
    * This is necessary since when editing show-value formats with text binding, only the
    * format (FormatInfo) is used on the different labels but they share other format
    * attributes (such as color and font), which is from text field. So when a show-value
    * format is edited, it's changes need to be copied to the text field text format.
    */
   public static void syncAggregateTextFormat(ChartInfo info, ChartRef ref, boolean fromTextField) {
      AestheticRef textField = info.isMultiAesthetic() && ref instanceof ChartAggregateRef
         ? ((ChartAggregateRef) ref).getTextField() : info.getTextField();

      if(textField != null) {
         DataRef aref = textField.getDataRef();

         if(aref instanceof ChartRef) {
            CompositeTextFormat textFormat = ((ChartRef) aref).getTextFormat();

            if(ref == null) {
               return;
            }

            CompositeTextFormat valueFormat = ref.getTextFormat();

            if(fromTextField) {
               copyTextFormat(ref, textFormat, valueFormat);
            }
            else {
               copyTextFormat(((ChartRef) aref), valueFormat, textFormat);
            }
         }
      }
   }

   // copy all except Format
   private static void copyTextFormat(ChartRef ref, CompositeTextFormat newFmt,
                                      CompositeTextFormat oldFmt)
   {
      newFmt = newFmt.clone();
      newFmt.getUserDefinedFormat().setFormat(oldFmt.getUserDefinedFormat().getFormat());
      ref.setTextFormat(newFmt);
   }

   private static final Logger LOG = LoggerFactory.getLogger(GraphFormatUtil.class);

}
