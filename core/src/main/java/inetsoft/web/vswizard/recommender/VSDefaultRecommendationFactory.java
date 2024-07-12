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
package inetsoft.web.vswizard.recommender;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.VSWizardData;
import inetsoft.web.vswizard.model.recommender.*;
import inetsoft.web.vswizard.recommender.object.*;
import inetsoft.web.vswizard.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;

@Component
public final class VSDefaultRecommendationFactory implements VSRecommendationFactory {
   @Autowired
   public VSDefaultRecommendationFactory(ViewsheetService viewsheetService,
                                         RuntimeViewsheetRef runtimeViewsheetRef,
                                         VSWizardBindingHandler bindingHandler,
                                         VSChartRecommendationFactoryService chartService,
                                         VSOutputRecommendationFactoryService outputService,
                                         VSCrosstabRecommendationFactoryService crosstabService,
                                         VSTableRecommendationFactory tableFactory,
                                         VSFilterRecommendationFactory filterFactory,
                                         VSWizardTemporaryInfoService temporaryInfoService)
   {
      this.viewsheetService = viewsheetService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.bindingHandler = bindingHandler;
      this.chartService = chartService;
      this.outputService = outputService;
      this.crosstabService = crosstabService;
      this.tableFactory = tableFactory;
      this.filterFactory = filterFactory;
      this.temporaryInfoService = temporaryInfoService;
   }

   /**
    * Get the strategy name of the current recommender.
    */
   @Override
   public String getStrategyName() {
      return STRATEGY_NAME;
   }

   /**
    * Return the VSRecommendObject list for target selections.
    */
   @Override
   public VSRecommendationModel recommend(VSWizardData wizardData, Principal principal)
      throws Exception
   {
      long ts = System.currentTimeMillis();
      AssetEntry[] entries = wizardData == null ? null : wizardData.getSelectedEntries();

      if(entries == null || entries.length == 0) {
         return new VSRecommendationModel();
      }

      ViewsheetSandbox box = getViewsheetSandbox(principal);
      VSTemporaryInfo temporaryInfo = getTemporaryInfo(principal);

      if(temporaryInfo == null) {
         return null;
      }

      AssetEntry[] selectedEntries = wizardData.getSelectedEntries();

      // don't recommend chart/crosstab when > 9 so no need to get cardinality
      if(selectedEntries.length <= 9 && !box.isCancelled(ts)) {
         WizardRecommenderUtil.refreshCardinalityAndHierarchy(box, temporaryInfo, selectedEntries);
      }

      if(box.isCancelled(ts)) {
         return null;
      }

      VSRecommendationModel model = new VSRecommendationModel();
      doRecommendation(model, wizardData, principal);

      if(model.getRecommendationList().size() > 0) {
         VSObjectRecommendation objModel = model.getRecommendationList().get(0);
         model.setSelectedType(objModel.getType());
         List<VSSubType> subTypes = objModel.getSubTypes();

         if(subTypes.size() > 0) {
            objModel.setSelectedIndex(0);
         }
      }

      return model;
   }

   private void doRecommendation(VSRecommendationModel model,
                                 VSWizardData wizardData,
                                 Principal principal)
      throws Exception
   {
      AssetEntry[] entries = wizardData.getSelectedEntries();
      VSChartInfo vinfo = bindingHandler.getTempChart(wizardData);

      // only one aggregate column.
      if(entries.length == 1 && vinfo.getYFields().length == 1) {
         appendDataRecommendation(model, wizardData, principal);
         appendOutputRecommendation(model, wizardData, principal);
         appendFilterRecommendation(model, wizardData, principal);
      }
      else if(entries.length == 1 && vinfo.getXFields().length == 1 &&
         WizardRecommenderUtil.isDateType(entries[0]))
      {
         appendFilterRecommendation(model, wizardData, principal);
         appendDataRecommendation(model, wizardData, principal);
      }
      else {
         appendDataRecommendation(model, wizardData, principal);
         appendOutputRecommendation(model, wizardData, principal);
         appendFilterRecommendation(model, wizardData, principal);
      }
   }

   private void appendDataRecommendation(VSRecommendationModel model,
                                         VSWizardData wizardData,
                                         Principal principal)
      throws Exception
   {
      AssetEntry[] entries = wizardData.getSelectedEntries();
      VSChartInfo vinfo = bindingHandler.getTempChart(wizardData);
      List<ChartRef> groups = Arrays.asList(vinfo.getXFields());
      List<ChartRef> aggregates = Arrays.asList(vinfo.getYFields());
      int refCount = groups.size() + aggregates.size();
      boolean allAggCalc = groups.size() == 0 && allCalc(principal, aggregates);
      int chartScore = refCount > 0
         ? ChartRecommenderUtil.scoreGroupAssembly(entries, groups, true) : -1;
      int crosstabScore = aggregates.size() > 0
         ? ChartRecommenderUtil.scoreGroupAssembly(entries, groups, false) : -1;
      // table score is compared to chart/crosstab scores calculated in scoreGroupAssembly
      int tableScore = !allAggCalc ? 8 : -1;
      VSChartRecommendationFactory chartFactory = chartService.getFactory();
      VSCrosstabRecommendationFactory crosstabFactory = crosstabService.getFactory();
      List<VSObjectRecommendationFactory<?>> factories = new ArrayList<>();

      if(chartScore >= 0) {
         factories.add(chartFactory);
      }

      if(crosstabScore >= 0) {
         factories.add(crosstabFactory);
      }

      if(tableScore >= 0) {
         factories.add(tableFactory);
      }

      final Map<Object, Integer> scoreMap = new HashMap<>();
      scoreMap.put(tableFactory, tableScore);
      scoreMap.put(chartFactory, chartScore);
      scoreMap.put(crosstabFactory, crosstabScore);
      factories.sort((a, b) -> scoreMap.get(b) - scoreMap.get(a));

      for(VSObjectRecommendationFactory<?> factory : factories) {
         model.addVSObjectRecommendation(factory.recommend(wizardData, principal));
      }
   }

   private void appendFilterRecommendation(VSRecommendationModel model,
                                           VSWizardData wizardData,
                                           Principal principal)
      throws Exception
   {
      AssetEntry[] entries = wizardData.getSelectedEntries();
      VSChartInfo vinfo = bindingHandler.getTempChart(wizardData);
      List<ChartRef> groups = Arrays.asList(vinfo.getXFields());
      List<ChartRef> aggregates = Arrays.asList(vinfo.getYFields());

      if(entries.length == 1 && !allCalc(principal, aggregates) ||
         // limit cardinality and level of selection tree. (55118)
         groups.size() > 0 && groups.size() <= 3 && aggregates.size() == 0 &&
            isDiscreteDimension(groups))
      {
         model.addVSObjectRecommendation(filterFactory.recommend(wizardData, principal));
      }
   }

   private boolean isDiscreteDimension(List<ChartRef> groups) {
      return groups.stream()
         .allMatch(r -> XSchema.STRING.equals(r.getDataType()) ||
            XSchema.BOOLEAN.equals(r.getDataType()));
   }

   private void appendOutputRecommendation(VSRecommendationModel model,
                                           VSWizardData wizardData,
                                           Principal principal)
   {
      VSChartInfo vinfo = bindingHandler.getTempChart(wizardData);
      List<ChartRef> groups = Arrays.asList(vinfo.getXFields());
      List<ChartRef> aggregates = Arrays.asList(vinfo.getYFields());

      // Should only binding aggregate to text/gauge, not number type data.
      if(groups.size() == 0 && aggregates.size() == 1) {
         model.addVSObjectRecommendation(
            outputService.getFactory(GaugeVSAssembly.class).recommend(wizardData, principal));
      }

      if(groups.size() == 0 && aggregates.size() == 1) {
         model.addVSObjectRecommendation(
            outputService.getFactory(TextVSAssembly.class).recommend(wizardData, principal));
      }
   }

   private ViewsheetSandbox getViewsheetSandbox(Principal principal) throws Exception {
      String id = runtimeViewsheetRef.getRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      return rvs.getViewsheetSandbox();
   }

   private VSTemporaryInfo getTemporaryInfo(Principal principal) throws Exception {
      String id = runtimeViewsheetRef.getRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      return temporaryInfoService.getVSTemporaryInfo(rvs);
   }

   private boolean allCalc(Principal principal, List<ChartRef> aggregates) throws Exception {
      String id = this.runtimeViewsheetRef.getRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      return WizardRecommenderUtil.allCalc(aggregates, rvs);
   }

   public static final String STRATEGY_NAME = "default_recommender";

   private final ViewsheetService viewsheetService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSWizardBindingHandler bindingHandler;
   private final VSChartRecommendationFactoryService chartService;
   private final VSOutputRecommendationFactoryService outputService;
   private final VSCrosstabRecommendationFactoryService crosstabService;
   private final VSTableRecommendationFactory tableFactory;
   private final VSFilterRecommendationFactory filterFactory;
   private final VSWizardTemporaryInfoService temporaryInfoService;
}
