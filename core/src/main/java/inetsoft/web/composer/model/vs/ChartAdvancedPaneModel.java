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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.XCondition;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonUtil;
import inetsoft.util.Catalog;
import inetsoft.web.graph.model.dialog.ChartPlotOptionsPaneModel;

import java.util.Arrays;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartAdvancedPaneModel {
   public ChartAdvancedPaneModel() {
   }

   public ChartAdvancedPaneModel(ChartVSAssemblyInfo chartAssemblyInfo) {
      VSChartInfo info = chartAssemblyInfo.getVSChartInfo();
      ChartDescriptor chartDescriptor = chartAssemblyInfo.getChartDescriptor();
      this.adhocVisible = !chartAssemblyInfo.isEmbedded();
      this.enableAdhocEditing = info.isAdhocEnabled();
      this.enableDrilling = Boolean.valueOf(chartAssemblyInfo.getDrillEnabledValue());
      this.glossyEffect = chartDescriptor.isApplyEffect();
      this.sparkline = chartDescriptor.isSparkline();
      this.sortOthersLast = chartDescriptor.isSortOthersLast();
      this.sortOthersLastEnabled = isSortOthersEnabled(info);
      this.rankPerGroup = chartDescriptor.isRankPerGroup();
      this.rankPerGroupLabel = getRankPerGroupLabel(info);
      this.dateComparisonEnabled = "true".equals(chartAssemblyInfo.getDateComparisonEnabledValue());
      this.dateComparisonSupport =
         DateComparisonUtil.supportDateComparison(chartAssemblyInfo.getVSChartInfo(), true);

      PlotDescriptor plotDesc = chartDescriptor.getPlotDescriptor();

      if(plotDesc == null) {
         return;
      }

      chartPlotOptionsPaneModel = new ChartPlotOptionsPaneModel(info, plotDesc);
   }

   public static boolean isRankingSupported(ChartInfo info) {
      return !GraphTypes.isBoxplot(info.getRTChartType());
   }

   public static boolean isSortOthersEnabled(ChartInfo info) {
      if(!isRankingSupported(info)) {
         return false;
      }

      List<XDimensionRef> dims = GraphUtil.getAllDimensions(info, true);

      // funnel sort others not applied to y dimension
      if(GraphTypes.isFunnel(info.getChartType())) {
         dims.removeAll(Arrays.asList(info.getBindingRefs(true)));
      }

      return dims.stream().anyMatch(ref -> ref != null && ref.isRankingGroupOthers());
   }

   public static String getRankPerGroupLabel(ChartInfo info) {
      if(!isRankingSupported(info)) {
         return null;
      }

      VSDataRef[] refs = info.getRTFields();

      if(refs.length > 1) {
         VSDataRef firstDim = null;
         VSDataRef firstTopN = null;

         for(VSDataRef ref : refs) {
            if(ref instanceof ChartDimensionRef) {
               if(firstDim == null) {
                  firstDim = ref;
               }

               boolean topN = ((ChartDimensionRef) ref).getRankingOption() != XCondition.NONE &&
                  ((ChartDimensionRef) ref).getRankingN() > 0;

               if(topN) {
                  firstTopN = ref;
                  break;
               }
            }
         }

         if(firstDim != firstTopN && firstTopN != null) {
            return Catalog.getCatalog().getString("graph.rank.per.group", firstDim.getFullName());
         }
      }

      return null;
   }

   public void updateChartAdvancedPaneModel(ChartVSAssemblyInfo chartAssemblyInfo) {
      VSChartInfo info = chartAssemblyInfo.getVSChartInfo();
      ChartDescriptor chartDescriptor = chartAssemblyInfo.getChartDescriptor();
      chartAssemblyInfo.setDrillEnabledValue(this.enableDrilling + "");
      info.setAdhocEnabled(this.enableAdhocEditing);
      chartDescriptor.setApplyEffect(this.glossyEffect);
      chartDescriptor.setSortOthersLast(this.sortOthersLast);
      chartDescriptor.setRankPerGroup(this.rankPerGroup);
      chartAssemblyInfo.setDateComparisonEnabledValue(this.dateComparisonEnabled + "");

      if(!chartAssemblyInfo.isDateComparisonEnabled()) {
         chartAssemblyInfo.setDateComparisonInfo(null);
      }

      if(this.sparklineSupported) {
         chartDescriptor.setSparkline(this.sparkline);
      }

      PlotDescriptor plotDesc = chartDescriptor.getPlotDescriptor();

      if(plotDesc == null) {
         return;
      }

      chartPlotOptionsPaneModel.updateChartPlotOptionsPaneModel(info, plotDesc);
   }

   public boolean isAdhocVisible() {
      return adhocVisible;
   }

   public void setAdhocVisible(boolean adhocVisible) {
      this.adhocVisible = adhocVisible;
   }

   public boolean isEnableAdhocEditing() {
      return enableAdhocEditing;
   }

   public void setEnableAdhocEditing(boolean enableAdhocEditing) {
      this.enableAdhocEditing = enableAdhocEditing;
   }

   public boolean isGlossyEffectSupported() {
      return glossyEffectSupported;
   }

   public void setGlossyEffectSupported(boolean glossyEffectSupported) {
      this.glossyEffectSupported = glossyEffectSupported;
   }

   public boolean isGlossyEffect() {
      return glossyEffect;
   }

   public void setGlossyEffect(boolean glossyEffect) {
      this.glossyEffect = glossyEffect;
   }

   public boolean isSparklineSupported() {
      return sparklineSupported;
   }

   public void setSparklineSupported(boolean sparklineSupported) {
      this.sparklineSupported = sparklineSupported;
   }

   public boolean isSparkline() {
      return sparkline;
   }

   public void setSparkline(boolean sparkline) {
      this.sparkline = sparkline;
   }

   public boolean isEnableDrilling() {
      return enableDrilling;
   }

   public void setEnableDrilling(boolean enableDrilling) {
      this.enableDrilling = enableDrilling;
   }

   public boolean isSortOthersLast() {
      return sortOthersLast;
   }

   public void setSortOthersLast(boolean sortOthersLast) {
      this.sortOthersLast = sortOthersLast;
   }

   public boolean isSortOthersLastEnabled() {
      return sortOthersLastEnabled;
   }

   public void setSortOthersLastEnabled(boolean sortOthersLastEnabled) {
      this.sortOthersLastEnabled = sortOthersLastEnabled;
   }

   public boolean isRankPerGroup() {
      return rankPerGroup;
   }

   public void setRankPerGroup(boolean rankPerGroup) {
      this.rankPerGroup = rankPerGroup;
   }

   public String getRankPerGroupLabel() {
      return rankPerGroupLabel;
   }

   public void setRankPerGroupLabel(String rankPerGroupLabel) {
      this.rankPerGroupLabel = rankPerGroupLabel;
   }

   public ChartPlotOptionsPaneModel getChartPlotOptionsPaneModel() {
      return chartPlotOptionsPaneModel;
   }

   public void setChartPlotOptionsPaneModel(ChartPlotOptionsPaneModel chartPlotOptionsPaneModel) {
      this.chartPlotOptionsPaneModel = chartPlotOptionsPaneModel;
   }

   public ChartTargetLinesPaneModel getChartTargetLinesPaneModel() {
      if(this.targetLinesPaneModel == null) {
         this.targetLinesPaneModel = new ChartTargetLinesPaneModel();
      }

      return this.targetLinesPaneModel;
   }

   public void setChartTargetLinesPaneModel(ChartTargetLinesPaneModel model) {
      this.targetLinesPaneModel = model;
   }

   public boolean isDateComparisonEnabled() {
      return dateComparisonEnabled;
   }

   public void setDateComparisonEnabled(boolean dateComparisonEnabled) {
      this.dateComparisonEnabled = dateComparisonEnabled;
   }

   public boolean isDateComparisonSupport() {
      return dateComparisonSupport;
   }

   private boolean enableAdhocEditing;
   private boolean adhocVisible;
   private boolean enableDrilling;
   private boolean glossyEffectSupported;
   private boolean glossyEffect;
   private boolean sparklineSupported;
   private boolean sparkline;
   private boolean sortOthersLast;
   private boolean sortOthersLastEnabled;
   private boolean rankPerGroup;
   private String rankPerGroupLabel;
   private boolean dateComparisonEnabled;
   private boolean dateComparisonSupport;
   private ChartPlotOptionsPaneModel chartPlotOptionsPaneModel;
   private ChartTargetLinesPaneModel targetLinesPaneModel;
}
