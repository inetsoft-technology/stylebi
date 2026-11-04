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

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.internal.GDefaults;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.report.StyleFont;
import inetsoft.util.css.CSSParameter;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;

/**
 * Rebuilds the default text formats of a chart's sub-components from the chart's own format.
 */
public final class ChartFormatDefaults {
   private ChartFormatDefaults() {
   }

   /**
    * Copies any user or css defined format settings of the given Chart to
    * the default format of its sub-components. Also, sets parent css
    * parameter in CSSTextFormat.
    */
   public static void fix(ChartVSAssemblyInfo info) {
      if(info == null) {
         return;
      }

      info.updateChartTypeCssAttribute();
      FormatInfo formatInfo = info.getFormatInfo();
      VSCompositeFormat objFmt = formatInfo.getFormat(VSAssemblyInfo.OBJECTPATH);

      if(objFmt == null) {
         return;
      }

      ArrayList<CSSParameter> parentParams = info.getCssParentParameters();
      ChartDescriptor desc = info.getChartDescriptor();
      VizContext ctx = VizContext.of(info);

      if(desc != null) {
         LegendsDescriptor legendsDesc = desc.getLegendsDescriptor();

         if(legendsDesc != null) {
            legendsDesc.initDefaultFormat(ctx);
            copyDefaultFormat(legendsDesc.getTitleTextFormat().getDefaultFormat(), objFmt);
            CSSTextFormat legendTitle = legendsDesc.getTitleTextFormat().getCSSFormat();
            legendTitle.setParentCSSParams(parentParams);
            LegendDescriptor colorDesc = legendsDesc.getColorLegendDescriptor();

            if(colorDesc != null) {
               colorDesc.initDefaultFormat(ctx);
               copyDefaultFormat(colorDesc.getContentTextFormat().getDefaultFormat(), objFmt);
               colorDesc.getContentTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            LegendDescriptor shapeDesc = legendsDesc.getShapeLegendDescriptor();

            if(shapeDesc != null) {
               shapeDesc.initDefaultFormat(ctx);
               copyDefaultFormat(shapeDesc.getContentTextFormat().getDefaultFormat(), objFmt);
               shapeDesc.getContentTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            LegendDescriptor sizeDesc = legendsDesc.getSizeLegendDescriptor();

            if(sizeDesc != null) {
               sizeDesc.initDefaultFormat(ctx);
               copyDefaultFormat(sizeDesc.getContentTextFormat().getDefaultFormat(), objFmt);
               sizeDesc.getContentTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }
         }

         TitlesDescriptor titlesDesc = desc.getTitlesDescriptor();

         if(titlesDesc != null) {
            TitleDescriptor xDesc = titlesDesc.getXTitleDescriptor();

            if(xDesc != null) {
               xDesc.initDefaultFormat(ctx);
               copyDefaultFormat(xDesc.getTextFormat().getDefaultFormat(), objFmt);
               xDesc.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            TitleDescriptor xDesc2 = titlesDesc.getX2TitleDescriptor();

            if(xDesc2 != null) {
               xDesc2.initDefaultFormat(ctx);
               copyDefaultFormat(xDesc2.getTextFormat().getDefaultFormat(), objFmt);
               xDesc2.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            TitleDescriptor yDesc = titlesDesc.getYTitleDescriptor();

            if(yDesc != null) {
               yDesc.initDefaultFormat(ctx);
               copyDefaultFormat(yDesc.getTextFormat().getDefaultFormat(), objFmt);
               yDesc.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            TitleDescriptor yDesc2 = titlesDesc.getY2TitleDescriptor();

            if(yDesc2 != null) {
               yDesc2.initDefaultFormat(ctx);
               copyDefaultFormat(yDesc2.getTextFormat().getDefaultFormat(), objFmt);
               yDesc2.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }
         }

         PlotDescriptor plotDesc = desc.getPlotDescriptor();

         if(plotDesc != null) {
            plotDesc.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            plotDesc.getErrorFormat().getCSSFormat().setParentCSSParams(parentParams);
            plotDesc.initDefaultFormat(ctx);
            copyDefaultFormat(plotDesc.getTextFormat().getDefaultFormat(), objFmt);
         }

         for(int i = 0; i < desc.getTargetCount(); i++) {
            GraphTarget graphTarget = desc.getTarget(i);
            graphTarget.initDefaultFormat(ctx);
            copyDefaultFormat(graphTarget.getTextFormat().getDefaultFormat(), objFmt);
            graphTarget.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
         }
      }

      ChartDescriptor rdesc = info.getRTChartDescriptor();

      if(rdesc != null) {
         LegendsDescriptor legendsDesc = rdesc.getLegendsDescriptor();

         if(legendsDesc != null) {
            legendsDesc.initDefaultFormat(ctx);
            copyDefaultFormat(legendsDesc.getTitleTextFormat().getDefaultFormat(), objFmt);
            CSSTextFormat legendTitle = legendsDesc.getTitleTextFormat().getCSSFormat();
            legendTitle.setParentCSSParams(parentParams);
            LegendDescriptor colorDesc = legendsDesc.getColorLegendDescriptor();

            if(colorDesc != null) {
               colorDesc.initDefaultFormat(ctx);
               copyDefaultFormat(colorDesc.getContentTextFormat().getDefaultFormat(), objFmt);
               colorDesc.getContentTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            LegendDescriptor shapeDesc = legendsDesc.getShapeLegendDescriptor();

            if(shapeDesc != null) {
               shapeDesc.initDefaultFormat(ctx);
               copyDefaultFormat(shapeDesc.getContentTextFormat().getDefaultFormat(), objFmt);
               shapeDesc.getContentTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            LegendDescriptor sizeDesc = legendsDesc.getSizeLegendDescriptor();

            if(sizeDesc != null) {
               sizeDesc.initDefaultFormat(ctx);
               copyDefaultFormat(sizeDesc.getContentTextFormat().getDefaultFormat(), objFmt);
               sizeDesc.getContentTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }
         }

         TitlesDescriptor titlesDesc = rdesc.getTitlesDescriptor();

         if(titlesDesc != null) {
            TitleDescriptor xDesc = titlesDesc.getXTitleDescriptor();

            if(xDesc != null) {
               xDesc.initDefaultFormat(ctx);
               copyDefaultFormat(xDesc.getTextFormat().getDefaultFormat(), objFmt);
               xDesc.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            TitleDescriptor xDesc2 = titlesDesc.getX2TitleDescriptor();

            if(xDesc2 != null) {
               xDesc2.initDefaultFormat(ctx);
               copyDefaultFormat(xDesc2.getTextFormat().getDefaultFormat(), objFmt);
               xDesc2.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            TitleDescriptor yDesc = titlesDesc.getYTitleDescriptor();

            if(yDesc != null) {
               yDesc.initDefaultFormat(ctx);
               copyDefaultFormat(yDesc.getTextFormat().getDefaultFormat(), objFmt);
               yDesc.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            TitleDescriptor yDesc2 = titlesDesc.getY2TitleDescriptor();

            if(yDesc2 != null) {
               yDesc2.initDefaultFormat(ctx);
               copyDefaultFormat(yDesc2.getTextFormat().getDefaultFormat(), objFmt);
               yDesc2.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }
         }

         PlotDescriptor plotDesc = rdesc.getPlotDescriptor();

         if(plotDesc != null) {
            plotDesc.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            plotDesc.getErrorFormat().getCSSFormat().setParentCSSParams(parentParams);
            plotDesc.initDefaultFormat(ctx);
            copyDefaultFormat(plotDesc.getTextFormat().getDefaultFormat(), objFmt);
         }

         for(int i = 0; i < rdesc.getTargetCount(); i++) {
            GraphTarget graphTarget = rdesc.getTarget(i);
            graphTarget.initDefaultFormat(ctx);
            copyDefaultFormat(graphTarget.getTextFormat().getDefaultFormat(), objFmt);
            graphTarget.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
         }
      }

      VSChartInfo chartInfo = info.getVSChartInfo();

      if(chartInfo instanceof RadarChartInfo) {
         AxisDescriptor axisDesc = ((RadarChartInfo) chartInfo).getLabelAxisDescriptor();

         if(axisDesc != null) {
            axisDesc.initDefaultFormat(ctx);
            copyDefaultFormat(axisDesc.getAxisLabelTextFormat().getDefaultFormat(), objFmt);
            axisDesc.getAxisLabelTextFormat().getCSSFormat().setParentCSSParams(parentParams);

            for(String col : axisDesc.getColumnLabelTextFormatColumns()) {
               CompositeTextFormat colFmt = axisDesc.getColumnLabelTextFormat(col);

               if(colFmt != null) {
                  copyDefaultFormat(colFmt.getDefaultFormat(), objFmt);
                  colFmt.getCSSFormat().setParentCSSParams(parentParams);
               }
            }
         }
      }

      if(chartInfo != null) {
         ChartRef[][] nrefs = {chartInfo.getXFields(), chartInfo.getYFields(),
            chartInfo.getGroupFields(), chartInfo.getBindingRefs(true)};

         for(ChartRef[] refs : nrefs) {
            int index = 0;

            for(ChartRef ref : refs) {
               ref.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
               AxisDescriptor axisDesc = ref.getAxisDescriptor();

               if(axisDesc != null) {
                  axisDesc.initDefaultFormat(ctx);
                  copyDefaultFormat(axisDesc.getAxisLabelTextFormat().getDefaultFormat(),
                                    objFmt);
                  axisDesc.getAxisLabelTextFormat().getCSSFormat().setParentCSSParams(parentParams);

                  for(String col : axisDesc.getColumnLabelTextFormatColumns()) {
                     CompositeTextFormat colFmt = axisDesc.getColumnLabelTextFormat(col);

                     if(colFmt != null) {
                        if(StyleFont.isDefaultFont(colFmt.getDefaultFormat().getFont())) {
                           colFmt.getDefaultFormat().setFont(axisDesc.getAxisLabelTextFormat()
                                                                .getDefaultFormat().getFont());
                        }

                        initDefaultFormat(colFmt, ctx);
                        copyDefaultFormat(colFmt.getDefaultFormat(), objFmt);
                        colFmt.getCSSFormat().setParentCSSParams(parentParams);
                     }
                  }
               }

               if(ref instanceof VSChartAggregateRef) {
                  VSChartAggregateRef aggr = (VSChartAggregateRef) ref;
                  aggr.initDefaultFormat(ctx);
                  ColorFrame colorFrame = aggr.getColorFrame();

                  if(aggr.getTextField() != null &&
                     aggr.getTextField().getDataRef() instanceof ChartRef)
                  {
                     ChartRef textfield = (ChartRef) aggr.getTextField().getDataRef();
                     initDefaultFormat(textfield, ctx);
                     copyDefaultFormat(textfield.getTextFormat().getDefaultFormat(), objFmt);
                  }

                  if(colorFrame instanceof StaticColorFrame) {
                     StaticColorFrame staticFrame = (StaticColorFrame) colorFrame;
                     staticFrame.setIndex(index);
                     staticFrame.setParentParams(parentParams);
                     index++;
                  }
               }

               copyDefaultFormat(ref.getTextFormat().getDefaultFormat(), objFmt);
            }
         }

         VSDataRef[] vsDataRefs = chartInfo.getFields();

         if(chartInfo instanceof VSMapInfo) {
            vsDataRefs = (VSDataRef[]) ArrayUtils.addAll(vsDataRefs, ((VSMapInfo) chartInfo).getRTGeoFields());
         }

         for(VSDataRef ref : vsDataRefs) {
            if(ref instanceof VSChartDimensionRef) {
               VSChartDimensionRef chartDimRef = (VSChartDimensionRef) ref;
               chartDimRef.initDefaultFormat(ctx);
               copyDefaultFormat(chartDimRef.getTextFormat().getDefaultFormat(), objFmt);
               chartDimRef.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }
         }

         for(boolean runtime: new boolean[] { false, true }) {
            for(AestheticRef ref : chartInfo.getAestheticRefs(runtime)) {
               ref.getLegendDescriptor().initDefaultFormat(ctx);
               copyDefaultFormat(ref.getLegendDescriptor().getContentTextFormat()
                                 .getDefaultFormat(), objFmt);
               applyColorFrameCSS(ref, parentParams, ctx);
            }
         }

         // A relation chart keeps a VSDimensionRef-backed node color out of getAestheticRefs() so
         // its dimension stays out of the GROUP BY (bug #75253), so the loop above cannot reach
         // it. Render is the only place that can restore this: parentParams is not persisted by
         // CategoricalColorFrameWrapper, so without this a reloaded tree chart's node colors stop
         // answering a format.css palette override until someone re-binds the field.
         if(chartInfo instanceof RelationChartInfo) {
            applyColorFrameCSS(((RelationChartInfo) chartInfo).getNodeColorField(),
                               parentParams, ctx);
         }

         Arrays.stream(chartInfo.getRuntimeDateComparisonRefs())
            .filter(ref -> ref instanceof ChartAggregateRef)
               .forEach(ref -> {
                  AestheticRef[] arefs = {
                     ((ChartAggregateRef) ref).getColorField(),
                     ((ChartAggregateRef) ref).getShapeField(),
                     ((ChartAggregateRef) ref).getSizeField(),
                  };

                  for(AestheticRef aref : arefs) {
                     if(aref != null) {
                        CompositeTextFormat fmt = aref.getLegendDescriptor().getContentTextFormat();
                        initDefaultFormat(fmt, ctx);
                        copyDefaultFormat(fmt.getDefaultFormat(), objFmt);
                     }
                  }
               });

         AxisDescriptor axisDesc = chartInfo.getAxisDescriptor();
         AxisDescriptor axisDesc2 = chartInfo.getAxisDescriptor2();

         copyDefaultFormat(objFmt, parentParams, axisDesc, ctx);
         copyDefaultFormat(objFmt, parentParams, axisDesc2, ctx);

         // the value axis renders from the runtime descriptor, cloned during execution before this
         // formatting pass, so seed it too or its labels keep the legacy default color. Gated so
         // gate-off leaves the runtime descriptor untouched, exactly as today.
         if(ctx.modern) {
            copyDefaultFormat(objFmt, parentParams, chartInfo.getRTAxisDescriptor(), ctx);
            copyDefaultFormat(objFmt, parentParams, chartInfo.getRTAxisDescriptor2(), ctx);
         }

         if(chartInfo.getTextField() != null && chartInfo.getTextField().getDataRef() instanceof ChartRef) {
            ChartRef ref = (ChartRef) chartInfo.getTextField().getDataRef();

            if(ref != null) {
               initDefaultFormat(ref, ctx);
               copyDefaultFormat(ref.getTextFormat().getDefaultFormat(), objFmt);
            }
         }
      }

      CSSChartStyles.apply(info.getChartDescriptor(), info.getVSChartInfo(), null, parentParams, VizContext.of(info));
   }

   private static void applyColorFrameCSS(AestheticRef ref, ArrayList<CSSParameter> parentParams,
                                   VizContext ctx)
   {
      if(ref == null) {
         return;
      }

      if(ref.getVisualFrame() instanceof CategoricalColorFrame) {
         CategoricalColorFrame ccf = (CategoricalColorFrame) ref.getVisualFrame();
         ccf.setParentParams(parentParams);
         VSChartPaletteDefaults.applyModernPalette(ccf, ctx);
      }
      else if(ref.getVisualFrame() instanceof GradientColorFrame) {
         ((GradientColorFrame) ref.getVisualFrame()).setParentParams(parentParams);
      }
      else if(ref.getVisualFrame() instanceof HSLColorFrame) {
         ((HSLColorFrame) ref.getVisualFrame()).setParentParams(parentParams);
      }
   }

   private static void copyDefaultFormat(VSCompositeFormat objFmt, ArrayList<CSSParameter> parentParams,
                                  AxisDescriptor axisDesc, VizContext ctx)
   {
      if(axisDesc != null) {
         axisDesc.initDefaultFormat(ctx);
         copyDefaultFormat(axisDesc.getAxisLabelTextFormat().getDefaultFormat(), objFmt);
         axisDesc.getAxisLabelTextFormat().getCSSFormat().setParentCSSParams(parentParams);

         // the measure axis resolves its labels through a per-column format, not the axis-wide one;
         // seed those too, unconditionally, or they keep the legacy default color (mirrors the
         // per-ref loop). initDefaultFormat writes the context's own value on both branches, so a
         // cleared mark restores the legacy label color here on the next render, same as per-ref.
         for(String col : axisDesc.getColumnLabelTextFormatColumns()) {
            CompositeTextFormat colFmt = axisDesc.getColumnLabelTextFormat(col);

            if(colFmt != null) {
               if(StyleFont.isDefaultFont(colFmt.getDefaultFormat().getFont())) {
                  colFmt.getDefaultFormat().setFont(
                     axisDesc.getAxisLabelTextFormat().getDefaultFormat().getFont());
               }

               initDefaultFormat(colFmt, ctx);
               copyDefaultFormat(colFmt.getDefaultFormat(), objFmt);
               colFmt.getCSSFormat().setParentCSSParams(parentParams);
            }
         }
      }
   }

   private static void initDefaultFormat(ChartRef ref, VizContext ctx) {
      if(ref instanceof VSChartDimensionRef) {
         ((VSChartDimensionRef) ref).initDefaultFormat(ctx);
      }
      else if(ref instanceof VSChartAggregateRef) {
         ((VSChartAggregateRef) ref).initDefaultFormat(ctx);
      }
   }

   private static void initDefaultFormat(CompositeTextFormat format, VizContext ctx) {
      if(format == null) {
         return;
      }

      TextFormat deffmt = format.getDefaultFormat();
      // match the modern chrome label default so per-column axis-label formats (e.g. the measure
      // axis, which resolves through a per-column format) aren't left on the legacy color
      deffmt.setColor(ctx.modern ?
                         VSChartChromeDefaults.labelColor(ctx) : GDefaults.DEFAULT_TEXT_COLOR);
   }

   /**
    * Copies font and color settings from Chart's VSCompositeFormat to the
    * specified TextFormat.
    */
   private static void copyDefaultFormat(TextFormat tfmt, VSCompositeFormat cfmt) {
      VSCSSFormat cssFormat = cfmt.getCSSFormat();
      VSFormat userFormat = cfmt.getUserDefinedFormat();

      if(cssFormat.isForegroundValueDefined() || userFormat.isForegroundValueDefined()) {
         tfmt.setColor(cfmt.getForeground());
      }

      if(cssFormat.isFontValueDefined() || userFormat.isFontValueDefined()) {
         tfmt.setFont(cfmt.getFont());
      }
   }
}
