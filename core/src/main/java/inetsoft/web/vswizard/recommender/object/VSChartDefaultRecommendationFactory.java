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
package inetsoft.web.vswizard.recommender.object;


import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.VSWizardData;
import inetsoft.web.vswizard.model.recommender.VSChartRecommendation;
import inetsoft.web.vswizard.model.recommender.VSSubType;
import inetsoft.web.vswizard.recommender.ChartRecommenderUtil;
import inetsoft.web.vswizard.recommender.chart.ChartCombinationUtil;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public final class VSChartDefaultRecommendationFactory implements VSChartRecommendationFactory {
   @Autowired
   public VSChartDefaultRecommendationFactory(RuntimeViewsheetRef runtimeViewsheetRef,
                                              ViewsheetService viewsheetService,
                                              VSWizardBindingHandler bindingHandler,
                                              VSWizardTemporaryInfoService temporaryInfoService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.viewsheetService = viewsheetService;
      this.bindingHandler = bindingHandler;
      this.temporaryInfoService = temporaryInfoService;
   }

   /**
    * Get the strategy name of the current recommendation factory.
    */
   @Override
   public String getStrategyName() {
      return STRATEGY_NAME;
   }

   /**
    * Return the VSRecommendObject for target selections.
    */
   @Override
   public VSChartRecommendation recommend(VSWizardData wizardData, Principal principal) {
      ColumnSelection geoCols = getGeoCols(bindingHandler.getTempChart(wizardData));
      AssetEntry[] entries = wizardData.getSelectedEntries();
      VSChartInfo temp;

      try {
         temp = bindingHandler.getTempChart(wizardData);
      }
      catch(Exception ex) {
         LOG.error("Failed to get the temp chart", ex);
         return null;
      }

      boolean autoOrder = isAutoOrder(principal);
      // create all valid recommedation.
      List<ChartInfo> infos = ChartCombinationUtil.getChartInfos(entries, temp, geoCols, autoOrder);
      // changed to control how many is showing on the client.
      // get the top recommendation.
      //infos = infos.stream().limit(12).collect(Collectors.toList());
      VSChartRecommendation chart = new VSChartRecommendation();
      chart.setSubTypes(getSubTypes(infos));
      infos.forEach(info -> fixChartInfo((VSChartInfo) info, temp));
      chart.setChartInfos(infos);

      return infos.size() > 0 ? chart : null;
   }

   /**
    * Callback to modify the list of chart infos
    */
   private void fixChartInfo(VSChartInfo info, VSChartInfo temp) {
      fixStaticColorFrame(info);
      copyAggregateInfo(info, temp);
      copyMeasureMapType(info, temp);
   }

   private void fixStaticColorFrame(ChartInfo info) {
      ChartRef[] arefs = info.getModelRefs(false);

      GraphUtil.fixStaticColorFrame(Arrays.asList(arefs), null, null);
      GraphUtil.syncWorldCloudColor(info);
   }

   /**
    * Synchronize measure map type with other chart infos so it's not lost
    * when switching to the full editor
    */
   private void copyMeasureMapType(VSChartInfo info, VSChartInfo temp) {
      info.setMeasureMapType(temp.getMeasureMapType());
   }

   private void copyAggregateInfo(VSChartInfo info, VSChartInfo temp) {
      info.setAggregateInfo(temp.getAggregateInfo());
   }

   private boolean isAutoOrder(Principal principal) {
      try {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(
            this.runtimeViewsheetRef.getRuntimeId(), principal);

         if(rvs == null) {
            return true;
         }

         return temporaryInfoService.getVSTemporaryInfo(rvs).isAutoOrder();
      }
      catch(Exception ex) {
         LOG.error("Failed to get geo is auto order", ex);
         return true;
      }
   }

   private ColumnSelection getGeoCols(VSChartInfo chartInfo) {
      try {
         if(chartInfo == null) {
            return null;
         }

         return chartInfo.getGeoColumns();
      }
      catch(Exception ex) {
         LOG.error("Failed to get geo columnselection", ex);
         return null;
      }
   }

   private List<VSSubType> getSubTypes(List<ChartInfo> infos) {
      return infos.stream().map(ChartRecommenderUtil::createSubType).collect(Collectors.toList());
   }

   public static final String STRATEGY_NAME = "default_chart_recommender";
   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
   private final VSWizardBindingHandler bindingHandler;
   private final VSWizardTemporaryInfoService temporaryInfoService;
}
