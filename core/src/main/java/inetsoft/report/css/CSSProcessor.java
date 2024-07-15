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
package inetsoft.report.css;

import inetsoft.graph.aesthetic.*;
import inetsoft.report.*;
import inetsoft.report.composition.graph.GraphTarget;
import inetsoft.report.internal.*;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.util.css.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * CSSProcessor is the main class to use to interact with the css package.
 * It's main method is used to apply css style to a ReportSheet.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class CSSProcessor {
   /**
    * Convenience method to retrieve the CSSDictionary for a given report.
    * @param report the report to get the CSSDictionary for.
    */
   public CSSDictionary getCSSDictionary(ReportSheet report) {
      return CSSDictionary.getDictionary(report.getCSSLocation());
   }

   /**
    * The main method that is used to apply a css style to a report.
    * @param report the report to apply the style too.
    */
   public void applyCSS(ReportSheet report) {
      applyCSS(report, true);
   }

   /**
    * The main method that is used to apply a css style to a report.
    *
    * @param report the report to apply the style too.
    * @param applyStyle flag indicating if the style should be applied to the elements.
    */
   public void applyCSS(ReportSheet report, boolean applyStyle) {
      CSSDictionary cssDict = getCSSDictionary(report);

      if(!applyStyle) {
         return;
      }

      CSSDictionary parentCssDict = null;
      applyCSS(report, cssDict, parentCssDict);
   }

   private void applyCSS(ReportSheet report, CSSDictionary cssDict, CSSDictionary parentCssDict) {
      CSSParameter reportCSSParam = getReportCSSParam(report);
      Color bg = cssDict.getBackground(reportCSSParam);

      // keep parent css bg if this bg is null
      if(bg != null || parentCssDict == null) {
         report.setCSSBackgroundColor(bg);
      }

      Enumeration iter = ElementIterator.elements(report);
      ReportElement elem;

      while(iter.hasMoreElements()) {
         elem = (ReportElement) iter.nextElement();

         // children get the merged style, union of self and parent style
         if(elem instanceof BaseElement) {
            Enumeration c = ElementIterator.elements(elem);

            while(c.hasMoreElements()) {
               ReportElement child = (ReportElement) c.nextElement();
               applyStyle(child, elem, cssDict);
            }

            Object parent = ((BaseElement) elem).getParent();

            if(parent != null && parent instanceof ReportElement) {
               applyStyle(elem, (ReportElement) parent, cssDict);
            }
            else {
               applyStyle(elem, cssDict);
            }
         }
      }
   }

   /**
    * Gets the list of CSS classes which are defined for a given report,
    * mostly as a convenience for the UI.
    */
   public String[] getCSSClasses(ReportSheet report, String type) {
      CSSDictionary cssDict = getCSSDictionary(report);

      if(cssDict == null) {
         return null;
      }

      return cssDict.getCSSClasses(type);
   }

   /**
    * Applies a style to a report elements as was specified in the css file.
    * @param elem the element to apply the style to.
    * @param cssDict contains the style to be applied.
    */
   public void applyStyle(ReportElement elem, CSSDictionary cssDict) {
      applyStyle(elem, null, cssDict);
   }

   /**
    * Applies a style to a report elements as was specified in the css file.
    * @param elem the element to apply the style to.
    * @param parent of the element to apply the style to.
    * @param cssDict contains the style to be applied.
    */
   private void applyStyle(ReportElement elem, ReportElement parent, CSSDictionary cssDict) {
      if(elem == null) {
         return;
      }

      if(elem instanceof ChartElement) {
         applyCSS((ChartElement) elem, cssDict);
         ((ChartElementDef) elem).setFontByCSS(false);
         ((ChartElementDef) elem).setForegroundByCSS(false);
      }

      if(cssDict == null) {
         return;
      }

      final String elemId = elem.getID();
      final String elemClass = elem.getCSSClass();
      CSSParameter reportCSSParam = null;

      if(elem instanceof BaseElement) {
         ReportSheet report = ((BaseElement) elem).getReport();
         reportCSSParam = getReportCSSParam(report);
      }

      if(elem instanceof CSSApplyer) {
         final CSSStyle style = cssDict.getStyle(reportCSSParam,
                                                 new CSSParameter(null, elemId, elemClass, null));

         if(style != null) {
            ((CSSApplyer) elem).applyCSSStyle(style);
         }
      }
      else {
         final String parentId = (parent != null ? parent.getID() : null);
         final String parentClass = (parent != null ? parent.getCSSClass() : null);
         String cssType = null;
         CSSAttr attr = null;

         if(elem instanceof BaseElement) {
            cssType = ((BaseElement) elem).getCSSType();
         }

         if(elem instanceof ChartElement) {
            CSSChartStyles.ChartType chartType = CSSChartStyles.getChartType(
               ((ChartElement) elem).getChartInfo());
            attr = new CSSAttr("type", chartType.getCssName());
         }

         CSSParameter parentCSSParam = new CSSParameter(cssType, parentId, parentClass, null);
         CSSParameter cssParam = new CSSParameter(cssType, elemId, elemClass, attr);

         // Background
         if(cssDict.isBackgroundDefined(reportCSSParam, cssParam)) {
            elem.setBackground(cssDict.getBackground(reportCSSParam, cssParam));
         }
         else if(parent != null &&
            cssDict.isBackgroundDefined(reportCSSParam, parentCSSParam))
         {
            elem.setBackground(cssDict.getBackground(reportCSSParam, parentCSSParam));
         }

         // Foreground
         if(cssDict.isForegroundDefined(reportCSSParam, cssParam)) {
            if(elem instanceof ChartElementDef) {
               ((ChartElementDef) elem).setForegroundByCSS(true);
            }

            elem.setForeground(cssDict.getForeground(reportCSSParam, cssParam));
         }
         else if(parent != null &&
            cssDict.isForegroundDefined(reportCSSParam, parentCSSParam))
         {
            if(elem instanceof ChartElementDef) {
               ((ChartElementDef) elem).setForegroundByCSS(true);
            }

            elem.setForeground(cssDict.getForeground(reportCSSParam, parentCSSParam));
         }

         // Font
         if(cssDict.isFontDefined(reportCSSParam, cssParam)) {
            if(elem instanceof ChartElementDef) {
               ((ChartElementDef) elem).setFontByCSS(true);
            }

            elem.setFont(cssDict.getFont(reportCSSParam, cssParam));
         }
         else if(parent != null &&
            cssDict.isFontDefined(reportCSSParam, parentCSSParam))
         {
            if(elem instanceof ChartElementDef) {
               ((ChartElementDef) elem).setFontByCSS(true);
            }

            elem.setFont(cssDict.getFont(reportCSSParam, parentCSSParam));
         }

         // Alignment
         if(cssDict.isAlignmentDefined(reportCSSParam, cssParam)) {
            elem.setAlignment(cssDict.getAlignment(reportCSSParam, cssParam));
         }
         else if(parent != null &&
            cssDict.isAlignmentDefined(reportCSSParam, parentCSSParam))
         {
            elem.setAlignment(cssDict.getAlignment(reportCSSParam, parentCSSParam));
         }

         // Border
         if(elem instanceof BorderedElement) {
            BorderedElement borderedElem = (BorderedElement)elem;

            if(cssDict.isBorderDefined(reportCSSParam, cssParam)) {
               borderedElem.setBorders(cssDict.getBorders(reportCSSParam, cssParam));
            }

            if(cssDict.isBorderColorDefined(reportCSSParam, cssParam)) {
               borderedElem.setBorderColors(cssDict.getBorderColors(reportCSSParam, cssParam));
           }
         }
      }
   }

   private void fixCSSTextFormat(CSSTextFormat ctf,
      final CSSDictionary cssDict, final List<CSSParameter> parentParams)
   {
      ctf.setCSSDictionary(cssDict);
      ctf.setParentCSSParams(parentParams);
   }

   public List<CSSParameter> getParentParams(ReportElement elem) {
      ArrayList<CSSParameter> parentParams = new ArrayList<>();
      CSSParameter reportCSSParam = null;

      if(elem instanceof BaseElement) {
         ReportSheet report = ((BaseElement) elem).getReport();
         reportCSSParam = getReportCSSParam(report);
      }

      CSSAttr attr = null;

      if(elem instanceof ChartElement) {
         CSSChartStyles.ChartType chartType = CSSChartStyles.getChartType(
            ((ChartElement) elem).getChartInfo());
         attr = new CSSAttr("type", chartType.getCssName());
      }

      parentParams.add(reportCSSParam);
      parentParams.add(new CSSParameter(elem.getCSSType(), elem.getID(), elem.getCSSClass(), attr));
      parentParams.trimToSize();
      return parentParams;
   }

   public void applyCSS(ChartElement elem, CSSDictionary cssDict) {
      List<CSSParameter> parentParams = getParentParams(elem);
      ChartDescriptor chartDesc = elem.getChartDescriptor();

      applyCSS(chartDesc, cssDict, parentParams);
      applyCSS(elem.getChartInfo(), cssDict, parentParams);
      CSSChartStyles.apply(chartDesc, elem.getChartInfo(), cssDict, parentParams);
   }

   public void applyCSS(ChartDescriptor chartDesc, CSSDictionary cssDict,
                        List<CSSParameter> parentParams)
   {
      if(chartDesc != null) {
         LegendsDescriptor legendsDesc = chartDesc.getLegendsDescriptor();

         if(legendsDesc != null) {
            fixCSSTextFormat(legendsDesc.getTitleTextFormat().getCSSFormat(),
                             cssDict, parentParams);

            LegendDescriptor colorDesc = legendsDesc.getColorLegendDescriptor();

            if(colorDesc != null) {
               fixCSSTextFormat(colorDesc.getContentTextFormat().getCSSFormat(),
                                cssDict, parentParams);
            }

            LegendDescriptor shapeDesc = legendsDesc.getShapeLegendDescriptor();

            if(shapeDesc != null) {
               fixCSSTextFormat(shapeDesc.getContentTextFormat().getCSSFormat(),
                                cssDict, parentParams);
            }

            LegendDescriptor sizeDesc = legendsDesc.getSizeLegendDescriptor();

            if(sizeDesc != null) {
               fixCSSTextFormat(sizeDesc.getContentTextFormat().getCSSFormat(),
                                cssDict, parentParams);
            }
         }

         TitlesDescriptor titlesDesc = chartDesc.getTitlesDescriptor();

         if(titlesDesc != null) {
            TitleDescriptor xTitleDesc = titlesDesc.getXTitleDescriptor();

            if(xTitleDesc != null) {
               fixCSSTextFormat(xTitleDesc.getTextFormat().getCSSFormat(),
                                cssDict, parentParams);
            }

            TitleDescriptor x2TitleDesc = titlesDesc.getX2TitleDescriptor();

            if(x2TitleDesc != null) {
               fixCSSTextFormat(x2TitleDesc.getTextFormat().getCSSFormat(),
                                cssDict, parentParams);
            }

            TitleDescriptor yTitleDesc = titlesDesc.getYTitleDescriptor();

            if(yTitleDesc != null) {
               fixCSSTextFormat(yTitleDesc.getTextFormat().getCSSFormat(),
                                cssDict, parentParams);
            }

            TitleDescriptor y2TitleDesc = titlesDesc.getY2TitleDescriptor();

            if(y2TitleDesc != null) {
               fixCSSTextFormat(y2TitleDesc.getTextFormat().getCSSFormat(),
                                cssDict, parentParams);
            }
         }

         PlotDescriptor plotDesc = chartDesc.getPlotDescriptor();

         if(plotDesc != null) {
            fixCSSTextFormat(plotDesc.getTextFormat().getCSSFormat(), cssDict, parentParams);
            fixCSSTextFormat(plotDesc.getErrorFormat().getCSSFormat(), cssDict, parentParams);
         }

         for(int i = 0; i < chartDesc.getTargetCount(); i++) {
            GraphTarget graphTarget = chartDesc.getTarget(i);
            fixCSSTextFormat(graphTarget.getTextFormat().getCSSFormat(),
                             cssDict, parentParams);
         }
      }
   }

   public void applyCSS(ChartInfo cinfo, CSSDictionary cssDict, List<CSSParameter> parentParams) {
      if(cinfo instanceof RadarChartInfo) {
         AxisDescriptor axisDesc = ((RadarChartInfo) cinfo).getLabelAxisDescriptor();

         if(axisDesc != null) {
            fixCSSTextFormat(axisDesc.getAxisLabelTextFormat().getCSSFormat(),
                             cssDict, parentParams);

            for(String col : axisDesc.getColumnLabelTextFormatColumns()) {
               CompositeTextFormat colFmt = axisDesc.getColumnLabelTextFormat(col);

               if(colFmt != null) {
                  fixCSSTextFormat(colFmt.getCSSFormat(), cssDict, parentParams);
               }
            }
         }
      }

      if(cinfo != null) {
         int index = 0;

         for(Object wrapper : ((AbstractChartInfo) cinfo).getColorFrameMap().values()) {
            if(wrapper instanceof StaticColorFrameWrapper) {
               StaticColorFrameWrapper staticWrapper =
                  (StaticColorFrameWrapper) wrapper;

               if(staticWrapper.getVisualFrame() instanceof StaticColorFrame) {
                  StaticColorFrame staticFrame = (StaticColorFrame)
                     staticWrapper.getVisualFrame();

                  staticFrame.setCSSDictionary(cssDict);
                  staticFrame.setIndex(index);
                  staticFrame.setParentParams(parentParams);
                  index++;
               }
            }
         }

         ChartRef[][] nrefs = {cinfo.getXFields(), cinfo.getYFields(),
            cinfo.getGroupFields(), cinfo.getBindingRefs(true)};

         for(ChartRef[] refs : nrefs) {
            for(ChartRef ref : refs) {
               fixCSSTextFormat(ref.getTextFormat().getCSSFormat(),
                                cssDict, parentParams);
               AxisDescriptor axisDesc = ref.getAxisDescriptor();

               if(axisDesc != null) {
                  fixCSSTextFormat(
                     axisDesc.getAxisLabelTextFormat().getCSSFormat(),
                     cssDict, parentParams);

                  for(String col : axisDesc.getColumnLabelTextFormatColumns()) {
                     CompositeTextFormat colFmt = axisDesc.getColumnLabelTextFormat(col);

                     if(colFmt != null) {
                        fixCSSTextFormat(colFmt.getCSSFormat(), cssDict, parentParams);
                     }
                  }
               }
            }
         }

         VSDataRef[] vsDataRefs = cinfo.getFields();

         for(VSDataRef ref : vsDataRefs) {
            if(ref instanceof ChartDimensionRef) {
               ChartDimensionRef chartDimRef = (ChartDimensionRef) ref;
               fixCSSTextFormat(chartDimRef.getTextFormat().getCSSFormat(), cssDict, parentParams);
               fixCSSTextFormat(chartDimRef.getAxisDescriptor().getAxisLabelTextFormat().getCSSFormat(),
                                cssDict, parentParams);
            }

            if(ref instanceof ChartAggregateRef) {
               ChartAggregateRef rcaRef = (ChartAggregateRef) ref;
               fixCSSTextFormat(rcaRef.getAxisDescriptor().
                  getAxisLabelTextFormat().getCSSFormat(), cssDict, parentParams);
            }
         }

         for(AestheticRef ref : cinfo.getAestheticRefs(false)) {
            fixCSSTextFormat(ref.getLegendDescriptor().getContentTextFormat().getCSSFormat(),
                             cssDict, parentParams);

            if(ref.getVisualFrame() instanceof CategoricalColorFrame) {
               CategoricalColorFrame ccf = (CategoricalColorFrame) ref.
                  getVisualFrame();
               ccf.setCSSDictionary(cssDict);
               ccf.setParentParams(parentParams);
            }
            else if(ref.getVisualFrame() instanceof GradientColorFrame) {
               GradientColorFrame gcf = (GradientColorFrame) ref.
                  getVisualFrame();
               gcf.setCSSDictionary(cssDict);
               gcf.setParentParams(parentParams);
            }
            else if(ref.getVisualFrame() instanceof HSLColorFrame) {
               HSLColorFrame hslcf = (HSLColorFrame) ref.getVisualFrame();
               hslcf.setCSSDictionary(cssDict);
               hslcf.setParentParams(parentParams);
            }

            DataRef dataRef = ref.getDataRef();

            if(dataRef instanceof ChartRef) {
               fixCSSTextFormat(((ChartRef) dataRef).getTextFormat().getCSSFormat(),
                                cssDict, parentParams);
            }
         }

         AxisDescriptor axisDescY = cinfo.getAxisDescriptor();

         if(axisDescY != null) {
            fixCSSTextFormat(axisDescY.getAxisLabelTextFormat().getCSSFormat(),
                             cssDict, parentParams);

            for(String col : axisDescY.getColumnLabelTextFormatColumns()) {
               CompositeTextFormat colFmt = axisDescY.getColumnLabelTextFormat(col);

               if(colFmt != null) {
                  fixCSSTextFormat(colFmt.getCSSFormat(), cssDict, parentParams);
               }
            }
         }

         AxisDescriptor axisDescY2 = cinfo.getAxisDescriptor2();

         if(axisDescY2 != null) {
            fixCSSTextFormat(axisDescY2.getAxisLabelTextFormat().getCSSFormat(),
                             cssDict, parentParams);

            for(String col : axisDescY2.getColumnLabelTextFormatColumns()) {
               CompositeTextFormat colFmt = axisDescY2.getColumnLabelTextFormat(col);

               if(colFmt != null) {
                  fixCSSTextFormat(colFmt.getCSSFormat(), cssDict, parentParams);
               }
            }
         }
      }
   }

   public static CSSParameter getReportCSSParam(ReportSheet report) {
      String reportCSSClass = "";
      String reportCSSId = "";
      String reportFormat = null;

      if(report != null) {
         if(report.getCSSClass() != null) {
            reportCSSClass = report.getCSSClass();
         }

         if(report.getCSSId() != null) {
            reportCSSId = report.getCSSId();
         }

         reportFormat = report.getProperty("report.format");
      }

      return new CSSParameter(CSSConstants.REPORT, reportCSSId, reportCSSClass,
                              new CSSAttr("print", (reportFormat != null) + "",
                                          "format", reportFormat));
   }

   public static boolean hasCSS(ReportSheet report) {
      if(report == null) {
         return false;
      }

      String cssFile = report.getCSSLocation();

      if(cssFile == null) {
         cssFile = SreeEnv.getProperty("css.location");
      }

      return cssFile != null;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(CSSProcessor.class);
}
