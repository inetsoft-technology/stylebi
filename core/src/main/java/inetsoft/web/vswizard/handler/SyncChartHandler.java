/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.vswizard.handler;

import inetsoft.report.Hyperlink;
import inetsoft.report.composition.graph.GraphTarget;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.uql.XCube;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonUtil;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.model.chart.ChartFormatConstants;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.vswizard.model.VSWizardOriginalModel;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Sync information of fromChart to targetChart.
 * Need sync information of original chart to wizard chart when edit chart in vs wizard.
 */
@Component
public class SyncChartHandler extends SyncAssemblyHandler {
   public SyncChartHandler(ChartRegionHandler regionHandler) {
      this.regionHandler = regionHandler;
   }

   public void syncChart(VSTemporaryInfo tempInfo, ChartVSAssembly fromChart,
                         ChartVSAssembly targetChart, boolean syncFormat, boolean syncLegend)
   {
      try {
         ChartVSAssemblyInfo fromAssemblyInfo = fromChart.getChartInfo();
         ChartVSAssemblyInfo targetAssemblyInfo = targetChart.getChartInfo();
         ChartDescriptor fromDescriptor = fromAssemblyInfo.getChartDescriptor();
         ChartDescriptor targetDescriptor = targetAssemblyInfo.getChartDescriptor();

         // sync chart properties
         syncChartProperties(tempInfo, fromChart, targetChart);

         // sync plot properties
         syncPlotProperties(fromDescriptor, targetDescriptor);

         // sync legend properties
         if(syncLegend) {
            syncLegendsProperties(fromChart, targetChart);
         }

         // sync title properties
         syncTitleProperties(fromDescriptor, targetDescriptor);

         // sync condition
         syncCondition(fromChart, targetChart);

         if(syncFormat) {
            // sync format
            syncChartFormat(fromChart, targetChart);
         }

         // sync hyperlink
         syncHyperlink(fromChart, targetChart);

         // sync highlight
         syncHighlight(fromChart, targetChart);
      }
      catch (Exception e) {
         LOGGER.info("Sync chart information failed.");
      }
   }

   private void syncChartProperties(VSTemporaryInfo tempInfo, ChartVSAssembly fromChart,
                                    ChartVSAssembly targetChart)
   {
      ChartVSAssemblyInfo fromAssemblyInfo = fromChart.getChartInfo();
      ChartVSAssemblyInfo targetAssemblyInfo = targetChart.getChartInfo();
      VSChartInfo fromChartInfo = fromChart.getVSChartInfo();
      VSChartInfo targetChartInfo = targetChart.getVSChartInfo();
      ChartDescriptor fromDescriptor = fromAssemblyInfo.getChartDescriptor();
      ChartDescriptor targetDescriptor = targetAssemblyInfo.getChartDescriptor();

      // 1. General
      // Name: processed in updateAssemblyByTemporary0
      // Visible
      targetAssemblyInfo.setPrimary(fromAssemblyInfo.isPrimary());
      targetAssemblyInfo.setVisibleValue(fromAssemblyInfo.getVisibleValue());

      // Enabled
      targetAssemblyInfo.setEnabledValue(fromAssemblyInfo.getEnabledValue());

      // Title
      if(tempInfo.getDescription() == null) {
         targetAssemblyInfo.setTitleValue(fromChart.getTitleValue());
         targetAssemblyInfo.setTitleVisibleValue(fromChart.getChartInfo().isTitleVisible());
      }

      // Tip
      targetAssemblyInfo.setTipOptionValue(fromAssemblyInfo.getTipOptionValue());
      targetAssemblyInfo.setAlphaValue(fromAssemblyInfo.getAlphaValue());
      targetAssemblyInfo.setTipViewValue(fromAssemblyInfo.getTipViewValue());

      // Flyover
      targetAssemblyInfo.setFlyoverViewsValue(fromAssemblyInfo.getFlyoverViewsValue());
      targetAssemblyInfo.setFlyOnClickValue(fromAssemblyInfo.getFlyOnClickValue());

      //padding
      targetAssemblyInfo.setPadding(fromAssemblyInfo.getPadding());

      // Layout: processed

      // 2. Advanced
      // Options
      targetDescriptor.setApplyEffect(fromDescriptor.isApplyEffect());
      targetDescriptor.setSparkline(fromDescriptor.isSparkline());
      targetAssemblyInfo.setDrillEnabledValue(fromAssemblyInfo.getDrillEnabledValue());

      // Target Lines
      int targetCount = fromDescriptor.getTargetCount();

      if(targetCount > 0 && targetDescriptor != fromDescriptor) {
         // clear target lines of target chart at first.
         targetDescriptor.clearTargets();

         for(int i = 0; i < targetCount; i++) {
            GraphTarget target = fromDescriptor.getTarget(i);
            boolean bindingExist = targetChartInfo.getFieldByName(target.getField(), true) != null;

            if(bindingExist) {
               targetDescriptor.addTarget((GraphTarget) target.clone());
            }
         }
      }

      // 3. Hierachy
      VSCube fromCube = (VSCube) fromAssemblyInfo.getXCube();

      if(fromCube != null) {
         VSCube targetCube = (VSCube) targetAssemblyInfo.getXCube();

         if(targetCube == null) {
            targetAssemblyInfo.setXCube((XCube) fromCube.clone());
         }
         else {
            targetCube.setDimensions(Collections.list(fromCube.getDimensions()));
         }
      }

      // 4. Script
      String script = fromAssemblyInfo.getScript();

      if(!StringUtils.isEmpty(script)) {
         VSWizardOriginalModel originalModel = tempInfo.getOriginalModel();
         String originalName = originalModel != null ? originalModel.getOriginalName() : null;
         targetAssemblyInfo.setScript(updateScript(script, originalName, targetChart.getName()));
      }

      targetAssemblyInfo.setScriptEnabled(fromAssemblyInfo.isScriptEnabled());
   }

   private void syncPlotProperties(ChartDescriptor odescriptor, ChartDescriptor ndescriptor) {
      PlotDescriptor oPlotDescriptor = (PlotDescriptor) odescriptor.getPlotDescriptor().clone();
      ndescriptor.setPlotDescriptor(oPlotDescriptor);
   }

   private void syncLegendsProperties(ChartVSAssembly fromChart, ChartVSAssembly targetChart) {
      ChartDescriptor odescriptor = fromChart.getChartDescriptor();
      ChartDescriptor ndescriptor = targetChart.getChartDescriptor();

      VSChartInfo oChartInfo = fromChart.getVSChartInfo();
      VSChartInfo nChartInfo = targetChart.getVSChartInfo();

      AestheticRef oColorField = oChartInfo.getColorField();
      AestheticRef nColorField = nChartInfo.getColorField();

      AestheticRef oShapeField = oChartInfo.getShapeField();
      AestheticRef nShapeField = nChartInfo.getShapeField();

      AestheticRef oSizeField = oChartInfo.getSizeField();
      AestheticRef nSizeField = nChartInfo.getSizeField();

      LegendsDescriptor olegendsDescriptor = odescriptor.getLegendsDescriptor();
      LegendsDescriptor nlegendsDescriptor = ndescriptor.getLegendsDescriptor();

      nlegendsDescriptor.setBorder(olegendsDescriptor.getBorder(), false);
      nlegendsDescriptor.setBorderColor(olegendsDescriptor.getBorderColor(), false);
      nlegendsDescriptor.setLayout(olegendsDescriptor.getLayout());

      if(oColorField != null && nColorField != null) {
         syncLegendProperties(olegendsDescriptor.getColorLegendDescriptor(),
                 nlegendsDescriptor.getColorLegendDescriptor(),
                 oColorField.getFullName().equals(nColorField.getFullName()));
      }

      if(oShapeField != null && nShapeField != null) {
         syncLegendProperties(olegendsDescriptor.getShapeLegendDescriptor(),
                 nlegendsDescriptor.getShapeLegendDescriptor(),
                 oShapeField.getFullName().equals(nShapeField.getFullName()));
      }

      if(oSizeField != null && nSizeField != null) {
         syncLegendProperties(olegendsDescriptor.getSizeLegendDescriptor(),
                 nlegendsDescriptor.getSizeLegendDescriptor(),
                 oSizeField.getFullName().equals(nSizeField.getFullName()));
      }
   }

   private void syncLegendProperties(LegendDescriptor olegendDescriptor,
                                     LegendDescriptor nlegendDescriptor,
                                     boolean syncAlias)
   {
      nlegendDescriptor.setTitleValue(olegendDescriptor.getTitleValue());
      nlegendDescriptor.setTitleVisible(olegendDescriptor.isTitleVisible());
      nlegendDescriptor.setNotShowNull(olegendDescriptor.isNotShowNull());
      nlegendDescriptor.setLogarithmicScale(olegendDescriptor.isLogarithmicScale());
      nlegendDescriptor.setReversed(olegendDescriptor.isReversed());
      nlegendDescriptor.setIncludeZero(olegendDescriptor.isIncludeZero());

      //alias
      if(syncAlias) {
         olegendDescriptor.getAliasedLabels().forEach(key -> {
            if(nlegendDescriptor.getLabelAlias(key) == null) {
               nlegendDescriptor.setLabelAlias(key, olegendDescriptor.getLabelAlias(key));
            }
         });
      }
   }

   private void syncTitleProperties(ChartDescriptor from, ChartDescriptor to) {
      syncAxisTitle(from, to, ChartFormatConstants.X_TITLE);
      syncAxisTitle(from, to, ChartFormatConstants.X2_TITLE);
      syncAxisTitle(from, to, ChartFormatConstants.Y_TITLE);
      syncAxisTitle(from, to, ChartFormatConstants.Y2_TITLE);
   }

   private void syncAxisTitle(ChartDescriptor fromDescriptor, ChartDescriptor targetDescriptor,
                              String axis)
   {
      TitleDescriptor fromAxisTitleDesc = regionHandler.getTitleDescriptor(fromDescriptor, axis);
      TitleDescriptor targetAxisTitleDesc = regionHandler.getTitleDescriptor(targetDescriptor, axis);

      targetAxisTitleDesc.getTextFormat().setRotation(fromAxisTitleDesc.getTextFormat().getRotation());
   }

   /**
    * Sync format. only sync chart format now, ignore region format.
    */
   private void syncChartFormat(ChartVSAssembly fromChart, ChartVSAssembly toChart) {
      ChartVSAssemblyInfo fromInfo = fromChart.getChartInfo();
      ChartVSAssemblyInfo toInfo = toChart.getChartInfo();
      syncFormat(fromChart, toChart);
      setChartRegionsFormat(fromInfo, toInfo);
   }

   private void setChartRegionsFormat(ChartVSAssemblyInfo from, ChartVSAssemblyInfo to) {
      ChartDescriptor fromDesc = from.getChartDescriptor();
      ChartDescriptor toDesc = to.getChartDescriptor();

      if(fromDesc == null || toDesc == null) {
         return;
      }

      setLegendsFormat(from, to);
      setTitleFormat(fromDesc, toDesc);
      setPlotFormat(fromDesc, toDesc);
      setAxisFormat(from, to);
   }

   private void setLegendsFormat(ChartVSAssemblyInfo from, ChartVSAssemblyInfo to) {
      ChartDescriptor fromDesc = from.getChartDescriptor();
      ChartDescriptor toDesc = to.getChartDescriptor();

      if(fromDesc == null || toDesc == null) {
         return;
      }

      LegendsDescriptor fromLegends = fromDesc.getLegendsDescriptor();
      LegendsDescriptor toLegends = toDesc.getLegendsDescriptor();

      if(fromLegends == null || toLegends == null) {
         return;
      }

      VSChartInfo fromInfo = from.getVSChartInfo();
      VSChartInfo toInfo = to.getVSChartInfo();

      LegendDescriptor fromColor = fromLegends.getColorLegendDescriptor();
      LegendDescriptor toColor = toLegends.getColorLegendDescriptor();

      if(fromColor != null && toColor != null &&
         shouldSyncAestheticLegend(fromInfo.getColorField(), toInfo.getColorField()))
      {
         toColor.setContentTextFormat((CompositeTextFormat) fromColor.getContentTextFormat().clone());
      }

      LegendDescriptor fromShape = fromLegends.getShapeLegendDescriptor();
      LegendDescriptor toShape = toLegends.getShapeLegendDescriptor();

      if(fromShape != null && toShape != null &&
         shouldSyncAestheticLegend(fromInfo.getShapeField(), toInfo.getShapeField()))
      {
         toShape.setContentTextFormat((CompositeTextFormat) fromShape.getContentTextFormat().clone());
      }

      LegendDescriptor fromSize = fromLegends.getSizeLegendDescriptor();
      LegendDescriptor toSize = toLegends.getSizeLegendDescriptor();

      if(fromSize != null && toSize != null &&
         shouldSyncAestheticLegend(fromInfo.getSizeField(), toInfo.getSizeField()))
      {
         toSize.setContentTextFormat((CompositeTextFormat) fromSize.getContentTextFormat().clone());
      }
   }

   private boolean shouldSyncAestheticLegend(AestheticRef from, AestheticRef to) {
      return !(from != null && to == null || from == null && to != null ||
         from != null && to != null && !Tool.equals(from.getFullName(), to.getFullName()));
   }

   private void setTitleFormat(ChartDescriptor fromDesc, ChartDescriptor toDesc) {
      TitlesDescriptor fromTitles = fromDesc.getTitlesDescriptor();
      TitlesDescriptor toTitles = toDesc.getTitlesDescriptor();

      if(fromTitles == null || toTitles == null) {
         return;
      }

      TitleDescriptor fromXTitle = fromTitles.getXTitleDescriptor();
      TitleDescriptor toXTitle = toTitles.getXTitleDescriptor();

      if(fromXTitle != null && toXTitle != null) {
         toXTitle.setTextFormat((CompositeTextFormat) fromXTitle.getTextFormat().clone());
      }

      TitleDescriptor fromX2Title = fromTitles.getX2TitleDescriptor();
      TitleDescriptor toX2Title = toTitles.getX2TitleDescriptor();

      if(fromX2Title != null && toX2Title != null) {
         toX2Title.setTextFormat((CompositeTextFormat) fromX2Title.getTextFormat().clone());
      }

      TitleDescriptor fromYTitle = fromTitles.getYTitleDescriptor();
      TitleDescriptor toYTitle = toTitles.getYTitleDescriptor();

      if(fromYTitle != null && toYTitle != null) {
         toYTitle.setTextFormat((CompositeTextFormat) fromYTitle.getTextFormat().clone());
      }

      TitleDescriptor fromY2Title = fromTitles.getY2TitleDescriptor();
      TitleDescriptor toY2Title = toTitles.getY2TitleDescriptor();

      if(fromY2Title != null && toY2Title != null) {
         toY2Title.setTextFormat((CompositeTextFormat) fromY2Title.getTextFormat().clone());
      }
   }

   private void setPlotFormat(ChartDescriptor fromDesc, ChartDescriptor toDesc) {
      PlotDescriptor fromPlot = fromDesc.getPlotDescriptor();
      PlotDescriptor toPlot = toDesc.getPlotDescriptor();

      if(fromPlot != null && toPlot != null) {
         toPlot.setTextFormat((CompositeTextFormat) fromPlot.getTextFormat().clone());
      }
   }

   private void setAxisFormat(ChartVSAssemblyInfo from, ChartVSAssemblyInfo to) {
      VSChartInfo fromInfo = from.getVSChartInfo();
      VSChartInfo toInfo = to.getVSChartInfo();
      setAxisFormat(fromInfo.getAxisDescriptor(), toInfo.getAxisDescriptor());
      setAxisFormat(fromInfo.getAxisDescriptor2(), toInfo.getAxisDescriptor2());

      if(fromInfo instanceof RadarChartInfo && toInfo instanceof RadarChartInfo) {
         AxisDescriptor fromAxis = ((RadarChartInfo) fromInfo).getLabelAxisDescriptor();
         AxisDescriptor toAxis = ((RadarChartInfo) toInfo).getLabelAxisDescriptor();
         setAxisFormat(fromAxis, toAxis);
      }

      VSDataRef[] refs = toInfo.getFields();
      final Set<String> copied = new HashSet<>();
      List<VSDataRef> list = new ArrayList<>();
      list.addAll(Arrays.asList(refs));

      if(toInfo.getPeriodField() != null) {
         list.add(toInfo.getPeriodField());
      }

      list.stream().forEach(ref -> {
         if(ref instanceof ChartRef) {
            ChartRef cref = (ChartRef) ref;

            // if a chart bind to a column in two different place (e.g. Y and text in donut),
            // copying format from from (Y) to target (text) would cause the format to be
            // lost. (44775)
            if(!copied.contains(cref.getFullName())) {
               copied.add(cref.getFullName());
               ChartRef[] findRefs = fromInfo.getFields(cref.getFullName(),
                  DateComparisonUtil.appliedDateComparison(from));

               if(findRefs != null && findRefs.length > 0) {
                  CompositeTextFormat fromFmt = findRefs[0].getTextFormat();

                  if(fromFmt != null) {
                     cref.setTextFormat((CompositeTextFormat) fromFmt.clone());
                  }

                  setAxisFormat(findRefs[0].getAxisDescriptor(), cref.getAxisDescriptor());
               }
            }
         }
      });
   }

   private void setAxisFormat(AxisDescriptor fromAxis, AxisDescriptor toAxis) {
      if(fromAxis == null || toAxis == null) {
         return;
      }

      CompositeTextFormat fromFmt = fromAxis.getAxisLabelTextFormat();

      if(fromFmt != null) {
         toAxis.setAxisLabelTextFormat((CompositeTextFormat) fromFmt.clone());
      }

      Set<String> cols = fromAxis.getColumnLabelTextFormatColumns();

      if(cols == null) {
         return;
      }

      Iterator it = cols.iterator();

      while(it.hasNext()) {
         Object obj = it.next();
         String col = obj == null ? null : obj + "";
         CompositeTextFormat fmt = fromAxis.getColumnLabelTextFormat(col + "");

         if(fmt != null) {
            toAxis.setColumnLabelTextFormat(col, (CompositeTextFormat) fmt.clone());
         }
      }
   }

   private void syncHyperlink(ChartVSAssembly fromChart, ChartVSAssembly targetChart) {
      VSChartInfo fromChartInfo = fromChart.getVSChartInfo();
      VSChartInfo targetChartInfo = targetChart.getVSChartInfo();

      Hyperlink hyperlink;
      boolean mergedProcessed = false; // just only process once
      VSDataRef[] fromXYFields = fromChartInfo.getFields();
      VSDataRef[] targetXYFields = targetChartInfo.getFields();

      for (VSDataRef ref: fromXYFields) {
         List<Integer> targetBindingRefs = VSWizardBindingHandler.findIndex(targetXYFields, ref, true);

         // no binding this column now.
         if(targetBindingRefs.size() == 0) {
            continue;
         }

         if(fromChartInfo instanceof MergedVSChartInfo &&
            targetChartInfo instanceof MergedVSChartInfo &&
            !(GraphUtil.isDimension(ref) && !(ref instanceof VSChartGeoRef)))
         {
            hyperlink = fromChartInfo.getHyperlink();

            if(hyperlink != null && !mergedProcessed) {
               ((MergedVSChartInfo) targetChartInfo).setHyperlink((Hyperlink) hyperlink.clone());
               mergedProcessed = true;
            }
         }
         else if(ref instanceof HyperlinkRef
            && (hyperlink = ((HyperlinkRef) ref).getHyperlink()) != null)
         {
            VSDataRef targetRef = targetXYFields[targetBindingRefs.get(0)];

            if(targetRef instanceof HyperlinkRef) {
               ((HyperlinkRef) targetRef).setHyperlink((Hyperlink) hyperlink.clone());
            }
         }
      }
   }

   private void syncHighlight(ChartVSAssembly fromChart, ChartVSAssembly targetChart) {
      VSChartInfo fromChartInfo = fromChart.getVSChartInfo();
      VSChartInfo targetChartInfo = targetChart.getVSChartInfo();

      HighlightGroup highlightGroup;
      boolean mergedProcessed = false; // just only process once
      VSDataRef[] fromXYFields = fromChartInfo.getRTFields(true, false, false, false);
      VSDataRef[] targetXYFields = targetChartInfo.getRTFields(true, false, false, false);

      for (VSDataRef ref: fromXYFields) {
         List<Integer> targetBindingRefs = VSWizardBindingHandler.findIndex(targetXYFields, ref, true);

         // no binding this column now.
         if(targetBindingRefs.size() == 0) {
            continue;
         }

         if(fromChartInfo instanceof MergedVSChartInfo &&
                 targetChartInfo instanceof MergedVSChartInfo &&
                 !(GraphUtil.isMeasure(ref)))
         {
            highlightGroup = fromChartInfo.getHighlightGroup();

            if(highlightGroup != null && !mergedProcessed) {
               ((MergedVSChartInfo) targetChartInfo).setHighlightGroup(highlightGroup.clone());
               mergedProcessed = true;
            }
         }
         else if(ref instanceof HighlightRef) {
            VSDataRef targetRef = targetXYFields[targetBindingRefs.get(0)];

            if(targetRef instanceof HighlightRef) {
               if((highlightGroup = ((HighlightRef) ref).getHighlightGroup()) != null) {
                  ((HighlightRef) targetRef).setHighlightGroup(highlightGroup.clone());
               }

               if((highlightGroup = ((HighlightRef) ref).getTextHighlightGroup()) != null) {
                  ((HighlightRef) targetRef).setTextHighlightGroup(highlightGroup.clone());
               }

               if((((HighlightRef) ref).getHighlightGroup() == null) && ((HighlightRef) ref).getTextHighlightGroup() == null) {
                  targetChartInfo.setHighlightGroup(fromChartInfo.getHighlightGroup());
               }
            }
         }
      }
   }

   private final ChartRegionHandler regionHandler;

   private static final Logger LOGGER = LoggerFactory.getLogger(SyncChartHandler.class);
}
