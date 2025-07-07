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
package inetsoft.report.script;

import inetsoft.graph.data.DataSet;
import inetsoft.report.Hyperlink;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.graph.*;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.report.filter.*;
import inetsoft.report.internal.AllCompositeTextFormat;
import inetsoft.report.internal.AllLegendDescriptor;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.util.script.JSObject;
import inetsoft.util.script.JavaScriptEngine;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Process the common things of chart.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ChartProcessor extends ScriptableObject {
   /**
    * Create a processor for adding properties to the scriptable.
    */
   public ChartProcessor(CommonChartScriptable scriptable) {
      this.scriptable = scriptable;
   }

   @Override
   public String getClassName() {
      return "ChartProcessor";
   }

   /**
    * Add the common properties of charts to the scriptable.
    * @param desc a bean that holds the top-level attributes of the chart.
    */
   public void addProperties(ChartInfo info, final ChartDescriptor desc) {
      if(desc == null || scriptable == null) {
         return;
      }

      this.desc = desc;
      this.info = info;

      TitleDescriptor xtitle = desc.getTitlesDescriptor().getXTitleDescriptor();
      TitleDescriptor x2title = desc.getTitlesDescriptor().getX2TitleDescriptor();
      TitleDescriptor ytitle = desc.getTitlesDescriptor().getYTitleDescriptor();
      TitleDescriptor y2title = desc.getTitlesDescriptor().getY2TitleDescriptor();
      PlotDescriptor plot = desc.getPlotDescriptor();
      LegendsDescriptor legends = desc.getLegendsDescriptor();
      CompositeTextFormat valuefmt = new AllCompositeTextFormat(info, plot);

      scriptable.addProperty("glossyEffect", "isApplyEffect", "setApplyEffect",
         boolean.class, ChartDescriptor.class, desc);

      scriptable.addProperty("highlighted", new ChartHighlightedArray(scriptable));

      // value format
      scriptable.addProperty("valueFormats", new TextFormatArray(info, plot));

      TitleScriptable.TitleGetter xtitleG = new TitleScriptable.TitleGetter() {
         @Override
         public TitleDescriptor getTitle() {
            ChartDescriptor desc2 = scriptable.getRTChartDescriptor();
            desc2 = desc2 == null ? desc : desc2;
            return desc2.getTitlesDescriptor().getXTitleDescriptor();
         }
      };

      TitleScriptable.TitleGetter x2titleG = new TitleScriptable.TitleGetter() {
         @Override
         public TitleDescriptor getTitle() {
            ChartDescriptor desc2 = scriptable.getRTChartDescriptor();
            desc2 = desc2 == null ? desc : desc2;
            return desc2.getTitlesDescriptor().getX2TitleDescriptor();
         }
      };

      TitleScriptable.TitleGetter ytitleG = new TitleScriptable.TitleGetter() {
         @Override
         public TitleDescriptor getTitle() {
            ChartDescriptor desc2 = scriptable.getRTChartDescriptor();
            desc2 = desc2 == null ? desc : desc2;
            return desc2.getTitlesDescriptor().getYTitleDescriptor();
         }
      };

      TitleScriptable.TitleGetter y2titleG = new TitleScriptable.TitleGetter() {
         @Override
         public TitleDescriptor getTitle() {
            ChartDescriptor desc2 = scriptable.getRTChartDescriptor();
            desc2 = desc2 == null ? desc : desc2;
            return desc2.getTitlesDescriptor().getY2TitleDescriptor();
         }
      };

      // title
      scriptable.addProperty("xTitle", new TitleScriptable(xtitleG));
      scriptable.addProperty("x2Title", new TitleScriptable(x2titleG));
      scriptable.addProperty("yTitle", new TitleScriptable(ytitleG));
      scriptable.addProperty("y2Title", new TitleScriptable(y2titleG));

      // plot
      scriptable.addProperty("valueFont", "getFont", "setFont", Font.class,
         CompositeTextFormat.class, valuefmt);
      scriptable.addProperty("valueRotation", "getRotation", "setRotation",
         Number.class, CompositeTextFormat.class, valuefmt);
      scriptable.addProperty("valueColor", "getColor", "setColor", Color.class,
         CompositeTextFormat.class, valuefmt);
      scriptable.addProperty("valueFormat", "getFormat", "setFormat",
         XFormatInfo.class, CompositeTextFormat.class, valuefmt);
      scriptable.addProperty("valueVisible", "isValuesVisible",
         "setValuesVisible", boolean.class, PlotDescriptor.class, plot);
      scriptable.addProperty("pieExploded", "isExploded", "setExploded",
         boolean.class, PlotDescriptor.class, plot);
      scriptable.addProperty("plotAlpha", "getAlpha", "setAlpha", double.class,
         PlotDescriptor.class, plot);
      scriptable.addProperty("wordCloudFontScale", "getWordCloudFontScale",
         "setWordCloudFontScale", double.class, PlotDescriptor.class, plot);
      scriptable.addProperty("plotBackground", "getBackground", "setBackground",
         Color.class, PlotDescriptor.class, plot);
      scriptable.addProperty("mapDefaultColor", "getEmptyColor", "setEmptyColor",
         Color.class, PlotDescriptor.class, plot);
      scriptable.addProperty("borderColor", "getBorderColor", "setBorderColor",
         Color.class, PlotDescriptor.class, plot);
      scriptable.addProperty("paretoLineColor", "getParetoLineColor", "setParetoLineColor",
         Color.class, PlotDescriptor.class, plot);
      scriptable.addProperty("xGridColor", "getXGridColor", "setXGridColor",
         Color.class, PlotDescriptor.class, plot);
      scriptable.addProperty("xGridStyle", "getXGridStyle", "setXGridStyle",
         int.class, PlotDescriptor.class, plot);
      scriptable.addProperty("yGridColor", "getYGridColor", "setYGridColor",
         Color.class, PlotDescriptor.class, plot);
      scriptable.addProperty("yGridStyle", "getYGridStyle", "setYGridStyle",
         int.class, PlotDescriptor.class, plot);
      scriptable.addProperty("diagonalColor", "getDiagonalColor", "setDiagonalColor",
         Color.class, PlotDescriptor.class, plot);
      scriptable.addProperty("diagonalStyle", "getDiagonalStyle", "setDiagonalStyle",
         int.class, PlotDescriptor.class, plot);
      scriptable.addProperty("quadrantColor", "getQuadrantColor", "setQuadrantColor",
         Color.class, PlotDescriptor.class, plot);
      scriptable.addProperty("quadrantStyle", "getQuadrantStyle", "setQuadrantStyle",
         int.class, PlotDescriptor.class, plot);
      scriptable.addProperty("fillTimeGap", "isFillTimeGap", "setFillTimeGap",
         boolean.class, PlotDescriptor.class, plot);
      scriptable.addProperty("fillZero", "isFillZero", "setFillZero",
         boolean.class, PlotDescriptor.class, plot);
      scriptable.addProperty("xBandColor", "getXBandColor", "setXBandColor",
         Color.class, PlotDescriptor.class, plot);
      scriptable.addProperty("yBandColor", "getYBandColor", "setYBandColor",
         Color.class, PlotDescriptor.class, plot);
      scriptable.addProperty("xBandSize", "getXBandSize", "setXBandSize",
         int.class, PlotDescriptor.class, plot);
      scriptable.addProperty("yBandSize", "getYBandSize", "setYBandSize",
         int.class, PlotDescriptor.class, plot);
      scriptable.addProperty("facetGrid", "isFacetGrid", "setFacetGrid",
         boolean.class, PlotDescriptor.class, plot);
      scriptable.addProperty("facetGridColor", "getFacetGridColor",
         "setFacetGridColor", Color.class, PlotDescriptor.class, plot);
      scriptable.addProperty("inPlot", "isInPlot",
         "setInPlot", boolean.class, PlotDescriptor.class, plot);
      scriptable.addProperty("polygonColor", "isPolygonColor",
         "setPolygonColor", boolean.class, PlotDescriptor.class, plot);

      // added in 13.5
      scriptable.addProperty("sortOthersLast", "isSortOthersLast", "setSortOthersLast",
         boolean.class, ChartDescriptor.class, desc);
      scriptable.addProperty("rankPerGroup", "isRankPerGroup", "setRankPerGroup",
         boolean.class, ChartDescriptor.class, desc);
      scriptable.addProperty("trendLine", "getTrendline",
                             "setTrendline", int.class, PlotDescriptor.class, plot);
      scriptable.addProperty("trendLineColor", "getTrendLineColor",
         "setTrendLineColor", Color.class, PlotDescriptor.class, plot);
      scriptable.addProperty("trendLineStyle", "getTrendLineStyle",
         "setTrendLineStyle", int.class, PlotDescriptor.class, plot);
      scriptable.addProperty("projectTrendLineForward", "getProjectTrendLineForward",
         "setProjectTrendLineForward", int.class, PlotDescriptor.class, plot);
      scriptable.addProperty("trendPerColor", "isTrendPerColor",
         "setTrendPerColor", boolean.class, PlotDescriptor.class, plot);
      scriptable.addProperty("referenceLineVisible", "isReferenceLineVisible",
         "setReferenceLineVisible", boolean.class, PlotDescriptor.class, plot);
      scriptable.addProperty("pointLine", "isPointLine",
         "setPointLine", boolean.class, PlotDescriptor.class, plot);
      scriptable.addProperty("stackValue", "isStackValue",
         "setStackValue", boolean.class, PlotDescriptor.class, plot);
      scriptable.addProperty("stackMeasures", "isStackMeasures",
         "setStackMeasures", boolean.class, PlotDescriptor.class, plot);
      scriptable.addProperty("webMap", "isWebMap",
         "setWebMap", boolean.class, PlotDescriptor.class, plot);
      scriptable.addProperty("webMapStyle", "getWebMapStyleName",
         "setWebMapStyleName", String.class, PlotDescriptor.class, plot);
      scriptable.addProperty("zoom", "getZoom",
         "setZoom", double.class, PlotDescriptor.class, plot);
      scriptable.addProperty("zoomLevel", "getZoomLevel",
         "setZoomLevel", int.class, PlotDescriptor.class, plot);
      scriptable.addProperty("panX", "getPanX",
         "setPanX", double.class, PlotDescriptor.class, plot);
      scriptable.addProperty("panY", "getPanY",
         "setPanY", double.class, PlotDescriptor.class, plot);
      scriptable.addProperty("contourLevels", "getContourLevels",
         "setContourLevels", int.class, PlotDescriptor.class, plot);
      scriptable.addProperty("contourBandwidth", "getContourBandwidth",
         "setContourBandwidth", int.class, PlotDescriptor.class, plot);
      scriptable.addProperty("contourCellSize", "getContourCellSize",
                             "setContourCellSize", int.class, PlotDescriptor.class, plot);
      scriptable.addProperty("contourEdgeAlpha", "getContourEdgeAlpha",
         "setContourEdgeAlpha", double.class, PlotDescriptor.class, plot);
      scriptable.addProperty("includeParentLabels", "isIncludeParentLabels",
         "setIncludeParentLabels", boolean.class, PlotDescriptor.class, plot);
      scriptable.addProperty("applyAestheticsToSource", "isApplyAestheticsToSource",
         "setApplyAestheticsToSource", boolean.class, PlotDescriptor.class, plot);

      scriptable.addProperty("fillGapWithDash", "isFillGapWithDash",
                             "setFillGapWithDash", boolean.class, PlotDescriptor.class, plot);
      scriptable.addProperty("pieRatio", "getPieRatio",
                             "setPieRatio", double.class, PlotDescriptor.class, plot);
      scriptable.addProperty("oneLine", "isOneLine",
                             "setOneLine", boolean.class, PlotDescriptor.class, plot);

      // tooltips
      scriptable.addProperty("toolTip", "getToolTip",
                             "setToolTip", String.class, ChartInfo.class, info);
      scriptable.addProperty("tooltipVisible", "isTooltipVisible",
                             "setTooltipVisible", boolean.class, ChartInfo.class, info);

      // legend
      scriptable.addProperty("legendPosition", "getLayout", "setLayout",
         int.class, LegendsDescriptor.class, legends);
      scriptable.addProperty("legendTitleColor", "getColor", "setColor",
         Color.class, CompositeTextFormat.class, legends.getTitleTextFormat());
      scriptable.addProperty("legendTitleFont", "getFont", "setFont", Font.class,
         CompositeTextFormat.class, legends.getTitleTextFormat());

      LegendDescriptor colorLegend = new AllLegendDescriptor(info, legends, ChartArea.COLOR_LEGEND);
      LegendDescriptor shapeLegend = new AllLegendDescriptor(info, legends, ChartArea.SHAPE_LEGEND);
      LegendDescriptor sizeLegend = new AllLegendDescriptor(info, legends, ChartArea.SIZE_LEGEND);

      // legend arrays
      scriptable.addProperty("colorLegends", new LegendArray(info, legends,
                                                             ChartArea.COLOR_LEGEND));
      scriptable.addProperty("shapeLegends", new LegendArray(info, legends,
                                                             ChartArea.SHAPE_LEGEND));
      scriptable.addProperty("sizeLegends", new LegendArray(info, legends,
                                                            ChartArea.SIZE_LEGEND));

      // global legends
      scriptable.addProperty("colorLegend", new LegendScriptable(colorLegend));
      scriptable.addProperty("shapeLegend", new LegendScriptable(shapeLegend));
      scriptable.addProperty("sizeLegend", new LegendScriptable(sizeLegend));

      scriptable.addProperty("legendBorderColor", "getBorderColor",
         "setBorderColor", Color.class, LegendsDescriptor.class, legends);
      scriptable.addProperty("legendBorder", "getBorder", "setBorder",
         int.class, LegendsDescriptor.class, legends);

      // **************************************************************************
      // BC, the following properties are for backward compatibility (11.4) title
      // **************************************************************************
      scriptable.addProperty("applyEffect", "isApplyEffect", "setApplyEffect",
         boolean.class, ChartDescriptor.class, desc);
      scriptable.addProperty("xTitle.text", "getTitle", "setTitle",
         String.class, TitleDescriptor.class, xtitle);
      scriptable.addProperty("x2Title.text", "getTitle", "setTitle",
         String.class, TitleDescriptor.class, x2title);
      scriptable.addProperty("yTitle.text", "getTitle", "setTitle",
         String.class, TitleDescriptor.class, ytitle);
      scriptable.addProperty("y2Title.text", "getTitle", "setTitle",
         String.class, TitleDescriptor.class, y2title);
      scriptable.addProperty("xTitle.visible", "isVisible", "setVisible",
         boolean.class, TitleDescriptor.class, xtitle);
      scriptable.addProperty("x2Title.visible", "isVisible", "setVisible",
         boolean.class, TitleDescriptor.class, x2title);
      scriptable.addProperty("yTitle.visible", "isVisible", "setVisible",
         boolean.class, TitleDescriptor.class, ytitle);
      scriptable.addProperty("y2Title.visible", "isVisible", "setVisible",
         boolean.class, TitleDescriptor.class, y2title);
      scriptable.addProperty("xTitle.font", "getFont", "setFont", Font.class,
         CompositeTextFormat.class, xtitle.getTextFormat());
      scriptable.addProperty("x2Title.font", "getFont", "setFont", Font.class,
         CompositeTextFormat.class, x2title.getTextFormat());
      scriptable.addProperty("yTitle.font", "getFont", "setFont", Font.class,
         CompositeTextFormat.class, ytitle.getTextFormat());
      scriptable.addProperty("y2Title.font", "getFont", "setFont", Font.class,
         CompositeTextFormat.class, y2title.getTextFormat());
      scriptable.addProperty("xTitle.rotation", "getRotation", "setRotation",
         Number.class, CompositeTextFormat.class, xtitle.getTextFormat());
      scriptable.addProperty("x2Title.rotation", "getRotation", "setRotation",
         Number.class, CompositeTextFormat.class, x2title.getTextFormat());
      scriptable.addProperty("yTitle.rotation", "getRotation", "setRotation",
         Number.class, CompositeTextFormat.class, ytitle.getTextFormat());
      scriptable.addProperty("y2Title.rotation", "getRotation", "setRotation",
         Number.class, CompositeTextFormat.class, y2title.getTextFormat());
      scriptable.addProperty("xTitle.color", "getColor", "setColor",
         Color.class, CompositeTextFormat.class, xtitle.getTextFormat());
      scriptable.addProperty("x2Title.color", "getColor", "setColor",
         Color.class, CompositeTextFormat.class, x2title.getTextFormat());
      scriptable.addProperty("yTitle.color", "getColor", "setColor",
         Color.class, CompositeTextFormat.class, ytitle.getTextFormat());
      scriptable.addProperty("y2Title.color", "getColor", "setColor",
         Color.class, CompositeTextFormat.class, y2title.getTextFormat());

      scriptable.addProperty("legend.position", "getLayout", "setLayout",
         int.class, LegendsDescriptor.class, legends);
      scriptable.addProperty("legend.color", "getColor", "setColor",
         Color.class, CompositeTextFormat.class, legends.getTitleTextFormat());
      scriptable.addProperty("legend.font", "getFont", "setFont", Font.class,
         CompositeTextFormat.class, legends.getTitleTextFormat());

      scriptable.addProperty("colorLegend.title", "getTitle", "setTitle",
         String.class, LegendDescriptor.class, colorLegend);
      scriptable.addProperty("colorLegend.titleVisible", "isTitleVisible",
         "setTitleVisible", boolean.class, LegendDescriptor.class, colorLegend);
      scriptable.addProperty("colorLegend.color", "getColor", "setColor",
         Color.class, CompositeTextFormat.class,
         colorLegend.getContentTextFormat());
      scriptable.addProperty("colorLegend.font", "getFont", "setFont",
         Font.class, CompositeTextFormat.class,
         colorLegend.getContentTextFormat());
      scriptable.addProperty("colorLegend.format", "getFormat", "setFormat",
         XFormatInfo.class, CompositeTextFormat.class,
         colorLegend.getContentTextFormat());
      scriptable.addProperty("colorLegend.noNull", "isNotShowNull",
         "setNotShowNull", boolean.class, LegendDescriptor.class, colorLegend);

      scriptable.addProperty("shapeLegend.title", "getTitle", "setTitle",
         String.class, LegendDescriptor.class, shapeLegend);
      scriptable.addProperty("shapeLegend.titleVisible", "isTitleVisible",
         "setTitleVisible", boolean.class, LegendDescriptor.class, shapeLegend);
      scriptable.addProperty("shapeLegend.color", "getColor", "setColor",
         Color.class, CompositeTextFormat.class,
         shapeLegend.getContentTextFormat());
      scriptable.addProperty("shapeLegend.font", "getFont", "setFont",
         Font.class, CompositeTextFormat.class,
         shapeLegend.getContentTextFormat());
      scriptable.addProperty("shapeLegend.format", "getFormat", "setFormat",
         XFormatInfo.class, CompositeTextFormat.class,
         shapeLegend.getContentTextFormat());
      scriptable.addProperty("shapeLegend.noNull", "isNotShowNull",
         "setNotShowNull", boolean.class, LegendDescriptor.class, shapeLegend);

      scriptable.addProperty("sizeLegend.title", "getTitle", "setTitle",
         String.class, LegendDescriptor.class, sizeLegend);
      scriptable.addProperty("sizeLegend.titleVisible", "isTitleVisible",
         "setTitleVisible", boolean.class, LegendDescriptor.class, sizeLegend);
      scriptable.addProperty("sizeLegend.color", "getColor", "setColor",
         Color.class, CompositeTextFormat.class,
         sizeLegend.getContentTextFormat());
      scriptable.addProperty("sizeLegend.font", "getFont", "setFont",
         Font.class, CompositeTextFormat.class,
         sizeLegend.getContentTextFormat());
      scriptable.addProperty("sizeLegend.format", "getFormat", "setFormat",
         XFormatInfo.class, CompositeTextFormat.class,
         sizeLegend.getContentTextFormat());
      scriptable.addProperty("sizeLegend.noNull", "isNotShowNull",
         "setNotShowNull", boolean.class, LegendDescriptor.class, sizeLegend);

      scriptable.addProperty("plot.alpha", "getAlpha", "setAlpha", double.class,
         PlotDescriptor.class, plot);

      scriptable.addProperty("value.font", "getFont", "setFont", Font.class,
         CompositeTextFormat.class, valuefmt);
      scriptable.addProperty("value.rotation", "getRotation", "setRotation",
         Number.class, CompositeTextFormat.class, valuefmt);
      scriptable.addProperty("value.color", "getColor", "setColor", Color.class,
         CompositeTextFormat.class, valuefmt);
      scriptable.addProperty("value.format", "getFormat", "setFormat",
         XFormatInfo.class, CompositeTextFormat.class, valuefmt);
      scriptable.addProperty("value.visible", "isValuesVisible",
         "setValuesVisible", boolean.class, PlotDescriptor.class, plot);

      scriptable.addProperty("legend.borderColor", "getBorderColor",
         "setBorderColor", Color.class, LegendsDescriptor.class, legends);
      scriptable.addProperty("legend.border", "getBorder", "setBorder",
         int.class, LegendsDescriptor.class, legends);

      // **************************************************************************
      // end of BC
      // **************************************************************************
   }

   /**
    * Add the function properties.
    */
   public void addFunctions(ChartInfo info, ChartDescriptor desc) {
      if(desc == null || scriptable == null) {
         return;
      }

      this.desc = desc;
      this.info = info;
      PlotDescriptor plot = desc.getPlotDescriptor();

      try {
         Scriptable script = (Scriptable) scriptable;
         Class[] params = {String.class, String.class};

         scriptable.addFunctionProperty(script.getClass(), "addTargetLine",
           Object.class, Object.class, Object.class, Object.class);
         scriptable.addFunctionProperty(script.getClass(), "addTargetBand",
               Object.class, Object.class, Object.class, Object.class);
         scriptable.addFunctionProperty(script.getClass(), "setHyperlink",
            new Class[]{ int.class, Object.class });
         scriptable.addFunctionProperty(script.getClass(), "addPercentageTarget",
               Object.class, Object.class, Object.class, Object.class);
         scriptable.addFunctionProperty(script.getClass(), "addPercentileTarget",
               Object.class, Object.class, Object.class, Object.class);
         scriptable.addFunctionProperty(script.getClass(), "addQuantileTarget",
               Object.class, Object.class, Object.class, Object.class);
         scriptable.addFunctionProperty(script.getClass(), "addStandardDeviationTarget",
               Object.class, Object.class, Object.class, Object.class);
         scriptable.addFunctionProperty(script.getClass(), "addConfidenceIntervalTarget",
               Object.class, Object.class, Object.class, Object.class);
         scriptable.addFunctionProperty(script.getClass(), "setTextID", params);
         scriptable.addFunctionProperty(script.getClass(), "getTextID", String.class);
         scriptable.addFunctionProperty(script.getClass(), "clearTargets");

         scriptable.addFunctionProperty(script.getClass(), "setTrendLineExcludedMeasures",
                                        Object.class);

         // **************************************************************************
         // Backwards compatibility
         // **************************************************************************
         scriptable.addFunctionProperty(script.getClass(), "addTarget",
            String.class, String.class, Object.class, Object.class, Object.class);
         scriptable.addFunctionProperty(script.getClass(), "setLabelAliasOfColorLegend", params);
         scriptable.addFunctionProperty(script.getClass(), "setLabelAliasOfShapeLegend", params);
         scriptable.addFunctionProperty(script.getClass(), "setLabelAliasOfSizeLegend", params);
      }
      catch(Throwable ex) {
         LOG.error("Failed to register chart properties and functions", ex);
      }
   }

   /**
    * Hide properties included for backward compatibility.
    */
   public static boolean isPublicProperty(Object name) {
      return !bcNames.contains(name);
   }

   /**
    * Backwards compatibility for <= 11.4 add target script function.  This
    * cannot be done in transformer
    */
   public void addBCTarget(String value, String label, Object style, Object clr,
                           Object measure)
   {
      GraphTarget target = new GraphTarget();

      // Line color
      Color color = (clr == null) ? Color.BLACK
         : (Color) PropertyDescriptor.convert(clr, Color.class);
      target.setLineColor(color);

      // Line style
      style = JavaScriptEngine.unwrap(style);
      double line = (style == null) ? StyleConstants.THIN_LINE :
         Double.parseDouble(style.toString());
      target.setLineStyle((int) line);

      // Line value
      target.setLabelFormats(value == null ? "" : value);
      DynamicLineWrapper strategy = new DynamicLineWrapper(value);
      target.setStrategy(strategy);

      // Label
      if(label != null) {
         target.setLabelFormats(label);
      }

      // Field
      measure = JavaScriptEngine.unwrap(measure);

      if(measure != null) {
         target.setField((String)measure);
      }

      desc.addTarget(target);
   }

   /**
    * Add a target band to the chart.
    */
   public void addTarget(Object field, TargetType type, Object clrs,
                         Object values, Object options)
   {
      GraphTarget target = new GraphTarget();
      clrs = JavaScriptEngine.unwrap(clrs);

      // Set up the strategy
      TargetStrategyWrapper strategy = pickStrategy(type);
      String[] vals = (String[]) PropertyDescriptor.convert(values, String[].class);
      TargetParameterWrapper[] params = parseParameters(vals);
      strategy.setParameters(params);
      target.setStrategy(strategy);

      // Fill colors
      Color[] colors = parseColors(clrs);
      CategoricalColorFrameWrapper colorFrame = target.getBandFill();

      for(int i = 0; i < colors.length; i++) {
         colorFrame.setColor(i, colors[i]);
      }

      // Set the field
      field = JavaScriptEngine.unwrap(field);

      if(field != null) {
         target.setField((String) field);
         VSDataRef vref = info.getFieldByName((String) field, true);

         if(vref == null) {
            vref = info.getFieldByName((String) field, false);
         }

         if(vref != null) {
            boolean dateField = XSchema.isDateType(vref.getDataType());
            boolean timeField = XSchema.TIME.equals(vref.getDataType());
            target.setDateField(dateField);
            target.setTimeField(timeField);

            for(TargetParameterWrapper param : params) {
               param.setDateField(dateField);
               param.setTimeField(timeField);
            }
         }
      }

      // Add the rest of the options
      addOptions(target, options, vals.length > 1);
      desc.addTarget(target);
   }

   public void clearTargets() {
      desc.clearTargets();
   }

   private Color[] parseColors(Object colors) {
      if(colors instanceof Color) {
         return new Color[] {(Color) colors};
      }

      Color[] ret;
      Object[] trans; // transitional

      // Try color array
      try {
         trans = (Object[])PropertyDescriptor.convert(colors, Object[].class);
         ret = new Color[trans.length];

         for(int i = 0; i < trans.length; i++) {
            ret[i] = (Color)PropertyDescriptor.convert(trans[i], Color.class);
         }
      }
      catch(Exception e) {
         // Try single color
         try {
            ret = new Color[] {
               (Color)PropertyDescriptor.convert(colors, Color.class)};
         }
         catch(Exception ex) {
            ret = new Color[0];
         }
      }

      return ret;
   }

   private String[] parseLabels(Object labels, boolean multiLabels) {
      String[] ret;

      // if only one value is specified for target (which is the common case), there
      // should be only one label, so don't try to split the string (it may contain
      // ',' if the label is an actual value).
      // Try String array
      if(multiLabels || JavaScriptEngine.isArray(labels)) {
         try {
            Object[] trans = (Object[]) PropertyDescriptor.convert(labels, Object[].class);
            ret = new String[trans.length];

            for(int i = 0; i < trans.length; i++) {
               ret[i] = (String) PropertyDescriptor.convert(trans[i], String.class);
            }

            return ret;
         }
         catch(Exception e) {
         }
      }

      // Try single String
      try {
         ret = new String[] { (String) PropertyDescriptor.convert(labels, String.class) };
      }
      catch(Exception ex) {
         ret = new String[] {""};
      }

      return ret;
   }

   /**
    * Add options to the Graph Target
    * @param multiValue true if there are multiple values so there needs to be multiple labels.
    */
   private void addOptions(GraphTarget target, Object options, boolean multiValue) {
      if(!(options instanceof Scriptable)) {
         if(options != null) {
            LOG.error("Invalid target option: " + options);
         }

         return;
      }

      Scriptable opts = (Scriptable) options;
      Color fillAbove = (Color)
         PropertyDescriptor.convert(JSObject.get(opts, "fillAbove"), Color.class);
      Color fillBelow = (Color)
         PropertyDescriptor.convert(JSObject.get(opts, "fillBelow"), Color.class);
      String[] labelTemplates = parseLabels(JSObject.get(opts, "label"), multiValue);
      Color lineColor = (Color)
         PropertyDescriptor.convert(JSObject.get(opts, "lineColor"), Color.class);
      Integer lineStyle = (Integer)
         PropertyDescriptor.convert(JSObject.get(opts, "lineStyle"), Integer.class);
      Font font = (Font)
         PropertyDescriptor.convert(JSObject.get(opts, "labelFont"), Font.class);
      Color labelColor = (Color)
         PropertyDescriptor.convert(JSObject.get(opts, "labelColor"), Color.class);
      String percentageAggregate = (String)
         PropertyDescriptor.convert(JSObject.get(opts, "percentageAggregate"), String.class);

      if(fillAbove != null) {
         target.setFillAbove(fillAbove);
      }

      if(fillBelow != null) {
         target.setFillBelow(fillBelow);
      }

      if(lineColor != null) {
         target.setLineColor(lineColor);
      }

      if(lineStyle != null) {
         target.setLineStyle(lineStyle);
      }

      if(labelTemplates != null) {
         target.setLabelFormats(labelTemplates);
      }

      if(font != null) {
         target.getTextFormat().setFont(font);
      }

      if(labelColor != null) {
         target.getTextFormat().setColor(labelColor);
      }

      // Set the aggregate value for percentage strategy here
      if(percentageAggregate != null && target.getStrategy() instanceof PercentageWrapper) {
         PercentageWrapper strat = (PercentageWrapper) target.getStrategy();
         strat.setAggregate(parseParameter(percentageAggregate));
      }
   }

   /**
    * Select a target strategy wrapper class based on the requested target type
    * @param type the target type
    * @return the appropriate strategy wrapper object for the chosen type
    */
   private TargetStrategyWrapper pickStrategy(TargetType type) {
      switch(type) {
         case PERCENTAGE:
            return new PercentageWrapper();
         case PERCENTILE:
            return new PercentileWrapper();
         case STANDARD_DEVIATION:
            return new StandardDeviationWrapper();
         case QUANTILE:
            return new QuantileWrapper();
         case CONFIDENCE_INTERVAL:
            return new ConfidenceIntervalWrapper();
         case LINE:
         default:
            return new DynamicLineWrapper();
      }
   }

   // Convert array of string parameters from script into an array of parameters
   private TargetParameterWrapper[] parseParameters(String... parameters) {
      TargetParameterWrapper[] ret =
         new TargetParameterWrapper[parameters.length];

      for(int i = 0; i < parameters.length; i++) {
         ret[i] = parseParameter(parameters[i]);
      }

      return ret;
   }

   private TargetParameterWrapper parseParameter(String param) {
      if(param.equalsIgnoreCase("max") || param.equalsIgnoreCase("maximum")) {
         return new TargetParameterWrapper(new MaxFormula());
      }
      else if(param.equalsIgnoreCase("min") ||
         param.equalsIgnoreCase("minimum"))
      {
         return new TargetParameterWrapper(new MinFormula());
      }
      else if(param.equalsIgnoreCase("sum") ||
         param.equalsIgnoreCase("summation"))
      {
         return new TargetParameterWrapper(new SumFormula());
      }
      else if(param.equalsIgnoreCase("avg")|| param.equalsIgnoreCase("average")
         || param.equalsIgnoreCase("mean"))
      {
         return new TargetParameterWrapper(new AverageFormula());
      }
      else if(param.equalsIgnoreCase("med") || param.equalsIgnoreCase("median"))
      {
         return new TargetParameterWrapper(new MedianFormula());
      }

      // Not a known formula
      return new TargetParameterWrapper(param);
   }

   /**
    * Set the label alias for the color legend descriptor.
    */
   public void setLabelAliasOfColorLegend(String label, String alias) {
      setLabelAlias(label, alias, ChartArea.COLOR_LEGEND);
   }

   /**
    * Set the label alias for the shape legend descriptor.
    */
   public void setLabelAliasOfShapeLegend(String label, String alias) {
      setLabelAlias(label, alias, ChartArea.SHAPE_LEGEND);
      setLabelAlias(label, alias, ChartArea.LINE_LEGEND);
      setLabelAlias(label, alias, ChartArea.TEXTURE_LEGEND);
   }

   /**
    * Set the label alias for the size legend descriptor.
    */
   public void setLabelAliasOfSizeLegend(String label, String alias) {
      setLabelAlias(label, alias, ChartArea.SIZE_LEGEND);
   }

   /**
    * Set the alias on all legends with the specified type.
    */
   private void setLabelAlias(String label, String alias, String type) {
      LegendsDescriptor ld = desc.getLegendsDescriptor();

      for(LegendDescriptor legend :
          GraphUtil.getLegendDescriptors(info, ld, type))
      {
         if(legend != null && label != null) {
            legend.setLabelAlias(label, alias);
         }
      }
   }

   public void setTrendLineExcludedMeasures(Object measures) {
      Set mset = desc.getPlotDescriptor().getTrendLineExcludedMeasures();
      mset.clear();
      Collections.addAll(mset, JavaScriptEngine.split(measures));
   }

   /**
    * Get the bindable for getting aesthetic fields.
    */
   public static ChartBindable getChartBindable(ChartInfo info, String aggr) {
      if(!info.isMultiAesthetic()) {
         return info;
      }

      ChartRef ref = aggr != null ? info.getFieldByName(aggr, false) : null;

      if(ref instanceof ChartBindable) {
         return (ChartBindable) ref;
      }

      List aggrs = AllChartAggregateRef.getXYAggregateRefs(info, false);
      return new AllChartAggregateRef(info, aggrs);
   }

   /**
    * Get the field that matches the name argument, either precisely by
    * full name, or loosely by name (as long as it is unambiguous).
    *
    * @param info    the chart info
    * @param name   the field full name or name to find
    * @return  the field that matches the name, or null if name not found
    *          or if the name was ambiguous.
    */
   public static ChartRef getRuntimeField(ChartInfo info, String name) {
      // 1. Match by Full Name (e.g. Year(Order Date))
      ChartRef resultRef = info.getFieldByName(name, true);

      // 2. If no match, then Match by Name (e.g. Order Date) so long as it is
      //    unambiguous.
      if(resultRef == null) {
         ChartRef[][] arrs = new ChartRef[][] {
            info.getRTXFields(), info.getRTYFields(), info.getRTGroupFields()
         };

         // Iterate through all runtime fields
         for(ChartRef[] arr : arrs) {
            for(ChartRef tempRef : arr) {
               if(tempRef.getName().equals(name)) {
                  // If a ref was already found, then finding another
                  // represents ambiguity
                  if(resultRef != null) {
                     return null;
                  }

                  resultRef = tempRef;
               }
            }
         }
      }

      return resultRef;
   }

   public static void setHyperlink(int col, Object link, DataSet dataset) {
      try {
         link = JavaScriptEngine.unwrap(link);

         if(link != null && !(link instanceof Hyperlink)) {
            link = PropertyDescriptor.convert(link, Hyperlink.class);
         }

         if(dataset instanceof VSDataSet) {
             ((VSDataSet) dataset).setHyperlink(col, (Hyperlink) link);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to set hyperlink on column [" + col + "]: " + link, ex);
      }
   }

   private static Set bcNames = new HashSet();
   static {
      String[] names = {
         "x2Title.color", "x2Title.font", "x2Title.rotation", "x2Title.text",
         "x2Title.visible", "xTitle.color", "xTitle.font", "xTitle.rotation",
         "xTitle.text", "xTitle.visible", "yTitle.color", "yTitle.font",
         "yTitle.rotation", "yTitle.text", "yTitle.visible", "plot.alpha",
         "y2Title.color", "y2Title.font", "y2Title.rotation", "y2Title.text",
         "y2Title.visible", "value.color", "value.font", "value.format",
         "value.rotation", "value.visible", "colorLegend.color",
         "colorLegend.font", "colorLegend.format", "colorLegend.title",
         "colorLegend.titleVisible", "colorLegend.noNull", "legend.border",
         "legend.borderColor", "legend.color", "legend.font",
         "legend.position", "sizeLegend.color", "sizeLegend.font",
         "sizeLegend.format", "sizeLegend.title", "sizeLegend.titleVisible",
         "sizeLegend.noNull", "shapeLegend.color", "shapeLegend.font",
         "shapeLegend.format", "shapeLegend.title", "shapeLegend.titleVisible",
         "shapeLegend.noNull", "applyEffect"};
      bcNames.addAll(Arrays.asList(names));
   }

   private CommonChartScriptable scriptable;
   private ChartInfo info;
   private ChartDescriptor desc;
   public static enum TargetType { LINE, CONFIDENCE_INTERVAL, PERCENTILE, PERCENTAGE,
                                   STANDARD_DEVIATION, QUANTILE }

   private static final Logger LOG = LoggerFactory.getLogger(ChartProcessor.class);
}
