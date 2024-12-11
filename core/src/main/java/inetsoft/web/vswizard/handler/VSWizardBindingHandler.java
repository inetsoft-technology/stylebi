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
package inetsoft.web.vswizard.handler;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.util.data.CommonKVModel;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.DataRefWrapper;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.MessageFormat;
import inetsoft.util.*;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.binding.handler.VSChartDataHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.graph.GraphBuilder;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.command.RemoveVSObjectCommand;
import inetsoft.web.viewsheet.controller.chart.VSChartLegendsVisibilityController;
import inetsoft.web.viewsheet.controller.table.BaseTableController;
import inetsoft.web.viewsheet.event.ViewsheetEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import inetsoft.web.vswizard.command.*;
import inetsoft.web.vswizard.model.*;
import inetsoft.web.vswizard.model.recommender.*;
import inetsoft.web.vswizard.recommender.ChartRecommenderUtil;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.security.Principal;
import java.text.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class VSWizardBindingHandler {

   @Autowired
   public VSWizardBindingHandler(VSChartHandler vsChartHandler,
                                 AssetRepository assetRepository,
                                 VSBindingService bindingFactory,
                                 ChartRegionHandler regionHandler,
                                 SyncInfoHandler syncInfoHandler,
                                 ViewsheetService viewsheetService,
                                 VSChartDataHandler chartDataHandler,
                                 PlaceholderService placeholderService,
                                 RuntimeViewsheetRef runtimeViewsheetRef,
                                 ChartRefModelFactoryService chartService,
                                 DataRefModelFactoryService dataRefService,
                                 VSWizardTemporaryInfoService temporaryInfoService)
   {
      this.chartService = chartService;
      this.regionHandler = regionHandler;
      this.dataRefService = dataRefService;
      this.vsChartHandler = vsChartHandler;
      this.bindingFactory = bindingFactory;
      this.assetRepository = assetRepository;
      this.syncInfoHandler = syncInfoHandler;
      this.viewsheetService = viewsheetService;
      this.chartDataHandler = chartDataHandler;
      this.placeholderService = placeholderService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.temporaryInfoService = temporaryInfoService;
   }

   public boolean changeSource(SourceInfo newSource, SourceInfo oldSource, ViewsheetEvent event,
                               VSTemporaryInfo temporaryInfo, Viewsheet vs, String url,
                               CommandDispatcher dispatcher) {
      boolean changeSource = oldSource != null && !Objects.equals(newSource, oldSource);

      if(!event.confirmed() && changeSource) {
         MessageCommand command = new MessageCommand();
         command.setMessage(
            Catalog.getCatalog().getString("viewer.viewsheet.chart.sourceChanged"));
         command.setType(MessageCommand.Type.CONFIRM);
         command.addEvent(url, event);
         dispatcher.sendCommand(command);
         return true;
      }

      //If confirmed is true, source is changed, so set new source.
      if(event.confirmed() || oldSource == null) {
         changeSource(newSource, oldSource, temporaryInfo, vs);
      }

      return false;
   }

   public boolean changeSource(SourceInfo newSource, SourceInfo oldSource,
                               VSTemporaryInfo temporaryInfo, Viewsheet vs) {
      ChartVSAssembly tempChart = temporaryInfo.getTempChart();
      boolean changeSource = sourceChanged(newSource.getSource(), oldSource);

      if(changeSource || oldSource == null) {
         tempChart.setSourceInfo(newSource);
         VSChartInfo chartInfo = tempChart.getVSChartInfo();
         chartInfo.removeFields();
         fixAggregateInfo(temporaryInfo, vs, null);
      }

      return changeSource;
   }

   /**
    * Create the current source based on asset entry
    */
   public SourceInfo getCurrentSource(AssetEntry[] entries, String tableName) {
      return new SourceInfo(getSourceType(entries), null, VSUtil.getTableName(tableName));
   }

   /**
    * Source info changed, fix the aggregate info of the chart info.
    */
   public void fixAggregateInfo(VSTemporaryInfo info, Viewsheet vs, AggregateInfo oainfo) {
      vsChartHandler.fixAggregateInfo(info.getTempChart().getChartInfo(), vs, oainfo);
   }

   private int getSourceType(AssetEntry[] entries) {
      int sourceType = SourceInfo.ASSET;

      if(entries != null && entries.length > 0) {
         String ptype = entries[0].getProperty("type");

         if(ptype != null) {
            try {
               sourceType = Integer.parseInt(ptype);
            }
            catch(NumberFormatException ignore) {
            }
         }
      }

      return sourceType;
   }

   /**
    * Update primary assembly by current recommendation.
    * @param model the recommendation model which contains a list of object recommendation.
    * @param rvs   the current runtime viewsheet.
    */
   public void updatePrimaryAssembly(VSRecommendationModel model, RuntimeViewsheet rvs,
                                     boolean isOriginal, String linkUri,
                                     CommandDispatcher dispatcher)
      throws Exception
   {
      updatePrimaryAssembly(model, rvs, isOriginal, linkUri, dispatcher, false);
   }

   /**
    * Update primary assembly by current recommendation.
    * @param model the recommendation model which contains a list of object recommendation.
    * @param rvs   the current runtime viewsheet.
    * @param reload reload previous assembly info.
    */
   public void updatePrimaryAssembly(VSRecommendationModel model, RuntimeViewsheet rvs,
                                     boolean isOriginal, String linkUri,
                                     CommandDispatcher dispatcher, boolean reload)
      throws Exception
   {
      if(model == null || model.getRecommendationList() == null ||
         model.getRecommendationList().size() == 0)
      {
         Viewsheet vs = rvs.getViewsheet();
         Assembly assembly = WizardRecommenderUtil.getTempAssembly(vs);

         if(assembly != null) {
            RemoveVSObjectCommand command = new RemoveVSObjectCommand();
            command.setName(assembly.getName());
            dispatcher.sendCommand(command);
            RefreshVsWizardBindingCommand refreshBindingCommand = new RefreshVsWizardBindingCommand();
            Assembly tempChart = vs.getAssembly(VSWizardConstants.TEMP_CHART_NAME);
            refreshBindingCommand.setBindingModel(
               getWizardBindingModel(rvs, (VSAssembly) tempChart, isOriginal));
            dispatcher.sendCommand(refreshBindingCommand);
         }

         temporaryInfoService.destroyTempAssembly(rvs);

         return;
      }

      VSObjectRecommendation object = model.findSelectedRecommendation();
      updatePrimaryAssembly(object, rvs, isOriginal, linkUri, dispatcher, reload);
   }

   /**
    * Update primary assembly by target object recommendation.
    * @param model the object recommendation which may contains a list of sub recommendation.
    * @param rvs   the current runtime viewsheet.
    */
   public void updatePrimaryAssembly(VSObjectRecommendation model, RuntimeViewsheet rvs,
                                     boolean isOriginal, String linkUri, CommandDispatcher dispatcher,
                                     boolean reload)
      throws Exception
   {
      if(model == null) {
         return;
      }

      Viewsheet vs = rvs.getViewsheet();
      VSTemporaryInfo tempInfo = temporaryInfoService.getVSTemporaryInfo(rvs);

      if(tempInfo == null || tempInfo.getTempChart() == null) {
         return;
      }

      Assembly oassembly = WizardRecommenderUtil.getTempAssembly(vs);
      SourceInfo source = tempInfo.getTempChart().getSourceInfo();
      VSAssembly assembly = null;

      if(source == null) {
         LOG.error("Wizard source is missing: " + vs.getBaseEntry());
         return;
      }

      if(model instanceof VSOutputRecommendation) {
         assembly = addOutputVSAssembly((VSOutputRecommendation) model, rvs, source);
      }
      else if(model instanceof VSFilterRecommendation) {
         assembly = addFilterVSAssembly((VSFilterRecommendation) model, vs, source);
      }
      else if(model instanceof VSTableRecommendation) {
         assembly = addTableVSAssembly((VSTableRecommendation) model, rvs, source, reload);
      }
      else if(model instanceof VSCrosstabRecommendation) {
         assembly = addCrosstabVSAssembly((VSCrosstabRecommendation) model, rvs, source);
      }
      else if(model instanceof VSChartRecommendation) {
         assembly = addChartVSAssembly((VSChartRecommendation) model, rvs, source, dispatcher);
      }

      if(assembly != null) {
         assembly.initDefaultFormat();
      }

      if(oassembly != null && oassembly.getClass().equals(assembly.getClass())) {
         syncInfoHandler.syncInfo(tempInfo, (VSAssembly) oassembly, assembly);
      }

      if(!tempInfo.isShowLegend() && assembly instanceof ChartVSAssembly) {
         hideAllLegend((ChartVSAssembly)assembly);
      }

      // use the same default as label in insertVsObject, 40 * 0.6
      if(assembly instanceof TextVSAssembly) {
         VSCompositeFormat fmt = assembly.getVSAssemblyInfo().getFormat();
         fmt.getUserDefinedFormat().setFontValue(VSAssemblyInfo.getDefaultFont(Font.PLAIN, 24));
         fmt.getUserDefinedFormat().setWrappingValue(false);
      }

      if(oassembly instanceof TitledVSAssembly && assembly instanceof TitledVSAssembly) {
         String title = ((TitledVSAssembly) oassembly).getTitleValue();
         boolean defaultTitle = "Chart".equals(title) || "Table".equals(title) ||
            "Calendar".equals(title);

         if(assembly instanceof SelectionVSAssembly && !defaultTitle) {
            ((TitledVSAssembly) assembly).setTitleValue(title);
         }
      }

      this.updatePrimaryAssembly(rvs, linkUri, assembly, isOriginal, false, dispatcher);
   }

   private void hideAllLegend(ChartVSAssembly chart) {
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo)chart.getVSAssemblyInfo();
      ChartInfo chartInfo = info.getVSChartInfo();
      boolean maxMode = info.getMaxSize() != null;
      ChartDescriptor chartDescriptor = info.getChartDescriptor();
      LegendsDescriptor legendsDescriptor = chartDescriptor.getLegendsDescriptor();
      VSChartLegendsVisibilityController.showAllDescriptorLegends(chartDescriptor, chartInfo,
         false, maxMode);
      ChartDescriptor runtimeChartDescriptor = info.getRTChartDescriptor();

      //also change the runtime values if they exist, since the values from the runtime
      //chart descriptor are usually used if it exists
      if(runtimeChartDescriptor != null) {
         VSChartLegendsVisibilityController.showAllDescriptorLegends(runtimeChartDescriptor,
            chartInfo, false, maxMode);
      }
   }

   public void updatePrimaryAssembly(RuntimeViewsheet rvs, String linkUri, VSAssembly assembly,
                                     boolean isOriginal, boolean selectOriginal,
                                     CommandDispatcher dispatcher)
      throws Exception
   {
      if(assembly == null) {
         return;
      }

      // cancel the old queries and vgraphs to improve performance.
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.cancel(true);
      assembly.getVSAssemblyInfo().setWizardTemporary(true);
      Viewsheet vs = rvs.getViewsheet();
      VSTemporaryInfo tempInfo = temporaryInfoService.getVSTemporaryInfo(rvs);

      if(!selectOriginal) {
         RefreshVsWizardBindingCommand command = new RefreshVsWizardBindingCommand();
         command.setBindingModel(getWizardBindingModel(rvs, assembly, isOriginal));
         dispatcher.sendCommand(command);
      }

      if(assembly instanceof TableVSAssembly && !isOriginal) {
         checkCalcFields(rvs, dispatcher);
      }

      updateTitle(rvs, assembly, isOriginal);

      // sync information
      VSWizardOriginalModel originalModel = tempInfo.getOriginalModel();
      String originalName = originalModel != null ? originalModel.getOriginalName() : null;
      VSAssembly originalAssembly = vs.getAssembly(originalName);
      syncInfoHandler.syncInfo(tempInfo, originalAssembly, assembly);

      if(!isOriginal && assembly instanceof ChartVSAssembly) {
         GraphUtil.syncWorldCloudColor(((ChartVSAssembly) assembly).getVSChartInfo());
      }

      fixTempAssemblySize(assembly.getVSAssemblyInfo(), rvs);

      // optimization, inPlot is expensive not necessary in preview
      if(!(originalAssembly instanceof ChartVSAssembly) && assembly instanceof ChartVSAssembly) {
         ((ChartVSAssembly) assembly).getChartDescriptor().getPlotDescriptor().setInPlot(false);
      }

      if(isOriginal) {
         ViewsheetInfo vsinfo = vs.getViewsheetInfo();

         // if scaling is to be applied, clear any previous scaling prior to calculating the view size
         if(vsinfo != null && vsinfo.isScaleToScreen()) {
            VSEventUtil.clearScale(vs);
         }

         this.placeholderService.refreshVSAssembly(rvs, assembly, dispatcher);

         if(assembly instanceof TableDataVSAssembly) {
            BaseTableController.loadTableData(
               rvs, assembly.getAbsoluteName(), 0, 0, 100, linkUri, dispatcher);
         }
      }
      else {
         updateFormats(tempInfo, assembly, rvs, dispatcher, linkUri);
         this.placeholderService.refreshVSAssembly(rvs, assembly, dispatcher);
      }

      clearDefaultFormat(tempInfo.getFormatMap());
      reloadFormats(tempInfo, rvs, assembly);
      dispatcher.sendCommand(SetWizardBindingFormatCommand.builder()
         .models(getBindingFormatModel(temporaryInfoService.getVSTemporaryInfo(rvs)))
         .build());
      temporaryInfoService.destroyExpiredTempAssmbly(rvs);
   }

   private void clearDefaultFormat(Map<String, VSTemporaryInfo.TempFieldFormat> map) {
      if(map == null) {
         return;
      }

      map.values().forEach(info -> info.setDefaultFormat(null));
   }

   // For normal point chart, disable formula, for we will clear formula in it.
   private boolean disableFormula(VSTemporaryInfo tempInfo, ChartVSAssembly assembly) {
      VSRecommendationModel model = tempInfo.getRecommendationModel();

      if(model == null) {
         VSChartInfo chartInfo = assembly.getVSChartInfo();
         return disableFormula(chartInfo);
     }

      VSChartInfo cinfo = null;

      if(VSRecommendType.ORIGINAL_TYPE.equals(model.getSelectedType())) {
         Assembly original = getOriginalAssembly(tempInfo);

         if(!(original instanceof ChartVSAssembly)) {
            return false;
         }

         ChartVSAssembly chart = (ChartVSAssembly) original;
         cinfo = chart.getVSChartInfo();
      }
      else {
         VSObjectRecommendation recommand = model.findSelectedRecommendation();

         if(!(recommand instanceof VSChartRecommendation)) {
            return false;
         }

         VSChartRecommendation chart = (VSChartRecommendation) recommand;
         int idx = chart.getSelectedIndex();

         if(idx > -1 && idx < chart.getChartInfos().size()) {
            cinfo = (VSChartInfo) chart.getChartInfos().get(idx);
         }
      }

      return disableFormula(cinfo);
   }

   private Assembly getOriginalAssembly(VSTemporaryInfo tempInfo) {
      VSWizardOriginalModel model = tempInfo.getOriginalModel();

      if(model == null || model.getOriginalName() == null || model.getTempBinding() == null) {
         return null;
      }

      Viewsheet vs = model.getTempBinding().getViewsheet();
      return vs.getAssembly(model.getOriginalName());
   }

   private boolean disableFormula(VSChartInfo cinfo) {
      if(cinfo == null) {
         return false;
      }

      int style = cinfo.getChartStyle();

      if(ChartRecommenderUtil.isHistogram(cinfo) || GraphTypes.isContour(style) ||
         GraphTypes.isBoxplot(style))
      {
         return true;
      }

      if(!GraphTypes.isPoint(style)) {
         return false;
      }

      boolean scatter = GraphTypeUtil.isScatterMatrix(cinfo);
      boolean heatMap = GraphTypeUtil.isHeatMapish(cinfo);
      boolean hasMeasure = GraphUtil.hasMeasureOnX(cinfo) && GraphUtil.hasMeasureOnY(cinfo);
      boolean wordCloud = cinfo.getTextField() != null;
      return (scatter || hasMeasure) && !wordCloud && !heatMap;
   }

   /**
    * sometimes formula will be set 'none' when chart is pointChart, then we will not allowed user
    * to change formula in web, so should hide them.
    */
   private List<CommonKVModel<String, String>> getFixedFormulas(ChartVSAssembly primaryChart) {
      VSChartInfo vsChartInfo = primaryChart.getVSChartInfo();

      return vsChartInfo.getClearedFormula();
   }

   private WizardBindingModel getWizardBindingModel(RuntimeViewsheet rvs, VSAssembly assembly,
                                                    boolean isOriginal)
   {
      String source = VSUtil.getVSAssemblyBinding(assembly.getTableName());
      int assemblyType = assembly.getAssemblyType();
      WizardBindingModel model = new WizardBindingModel(source, assemblyType);
      Viewsheet vs = rvs.getViewsheet();
      VSTemporaryInfo tempInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
      model.setAutoOrder(tempInfo.isAutoOrder());
      model.setShowLegend(tempInfo.isShowLegend());
      ChartVSAssembly chart;

      if(isOriginal) {
         chart = tempInfo.getOriginalModel().getTempBinding();
      }
      else {
         chart = (ChartVSAssembly) vs.getAssembly(VSWizardConstants.TEMP_CHART_NAME);
      }

      // the wizard temp assembly may have be closed due to async nature of the http requests
      if(chart == null) {
         return null;
      }

      model.setChartBindingModel((ChartBindingModel) bindingFactory.createModel(chart));

      if(assembly instanceof TableVSAssembly) {
         model.setTableBindingModel((TableBindingModel) bindingFactory.createModel(assembly));
      }
      else if(assembly instanceof SelectionVSAssembly) {
         model.setFilterBindingModel(
            new FilterBindingModel(dataRefService, (SelectionVSAssembly) assembly));
      }

      if(assembly instanceof ChartVSAssembly && disableFormula(tempInfo, (ChartVSAssembly)assembly))
      {
         model.setFixedFormulaMap(getFixedFormulas((ChartVSAssembly) assembly));
      }

      return model;
   }

   public void updateTitle(RuntimeViewsheet rvs, VSAssembly assembly) {
      updateTitle(rvs, assembly, false);
   }

   public void updateTitle(RuntimeViewsheet rvs, VSAssembly assembly, boolean isOriginal) {
      VSTemporaryInfo tempInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
      String desc = tempInfo.getDescription();
      VSAssembly tempAssembly = WizardRecommenderUtil.getTempAssembly(rvs.getViewsheet());
      boolean isChart = assembly instanceof ChartVSAssembly;

      if(assembly != tempAssembly || !isChart && StringUtils.isEmpty(desc) ||
         !(assembly instanceof TitledVSAssembly))
      {
         return;
      }

      if(desc != null) {
         ((TitledVSAssembly) tempAssembly).setTitleValue(desc);
         ((TitledVSAssemblyInfo) tempAssembly.getVSAssemblyInfo()).setTitleVisibleValue(true);
         return;
      }

      if(!isOriginal || tempInfo.getOriginalModel() == null) {
         return;
      }

      VSWizardOriginalModel originalModel = tempInfo.getOriginalModel();
      Assembly originalAssembly = rvs.getViewsheet().getAssembly(originalModel.getOriginalName());

      if(originalAssembly != null &&
         originalAssembly.getInfo() instanceof TitledVSAssemblyInfo)
      {
         boolean titleVisible = ((TitledVSAssemblyInfo) originalAssembly.getInfo())
            .getTitleVisibleValue();
         ((TitledVSAssemblyInfo) tempAssembly.getVSAssemblyInfo())
            .setTitleVisibleValue(titleVisible);
      }
   }

   /**
    * table don't support aggregate calcfield, but in order to avoid not recommend none assembly
    * when aggr calcfield is selected but not match other recommendation, so we just let table
    * always be recommended for multi fields selected, and using warning message to tell user
    * table will ignore aggregate calcfields.
    */
   private void checkCalcFields(RuntimeViewsheet rvs, CommandDispatcher dispatcher) {
      ChartVSAssembly chart = temporaryInfoService.getVSTemporaryInfo(rvs).getTempChart();
      VSChartInfo cinfo = chart.getVSChartInfo();
      List<ChartRef> aggrs = Arrays.asList(cinfo.getYFields());

      if(WizardRecommenderUtil.containsCalc(aggrs, rvs)) {
         TableWarningCommand command = new TableWarningCommand();
         command.setMessage(catalog.getString("common.viewsheet.table.bindingCalcAgg"));
         dispatcher.sendCommand(command);
      }
   }

   private WizardBindingFormatModel[] getBindingFormatModel(VSTemporaryInfo info) {
      Map<String, VSTemporaryInfo.TempFieldFormat> formatMap = info.getFormatMap();

      return formatMap.entrySet().stream()
         .map(e -> getBindingFormatModel(e.getKey(), e.getValue()))
         .toArray(WizardBindingFormatModel[]::new);
   }

   private WizardBindingFormatModel getBindingFormatModel(String field,
                                                          VSTemporaryInfo.TempFieldFormat format)
   {
      WizardBindingFormatModel formatModel = new WizardBindingFormatModel();
      VSFormat vsFormat = format.getUserFormat();

      if(vsFormat == null) {
         vsFormat = format.getDefaultFormat();
      }

      VSObjectFormatInfoModel model = getBindingFormatModel(vsFormat);
      formatModel.setFieldName(field);
      formatModel.setFormatModel(model);
      return formatModel;
   }

   public VSObjectFormatInfoModel getBindingFormatModel(VSFormat formatObj) {
      VSObjectFormatInfoModel model = new VSObjectFormatInfoModel();

      if(formatObj != null) {
         String modelFormat = formatObj.getFormat();
         String formatSpec = formatObj.getFormatExtent();
         String decimalSpec = "#,##0";

         if(XConstants.DECIMAL_FORMAT.equals(modelFormat) &&
            decimalSpec.equals(formatObj.getFormatExtent())) {
            modelFormat = XConstants.COMMA_FORMAT;
         }

         model.setFormat(modelFormat);
         model.setFormatSpec(formatSpec);
         model.fixDateSpec(modelFormat, formatSpec);
      }

      model.setDecimalFmts(ExtendedDecimalFormat.getSuffix().toArray(new String[0]));

      return model;
   }

   private int getSelectedSubTypeIdx(VSObjectRecommendation model) {
      List<VSSubType> subTypes = model.getSubTypes();
      int selectedSubTypeIdx = model.getSelectedIndex();

      if(selectedSubTypeIdx == -1 && subTypes != null && subTypes.size() != 0 ||
         subTypes != null && selectedSubTypeIdx >= subTypes.size())
      {
         selectedSubTypeIdx = 0;
      }

      return selectedSubTypeIdx;
   }

   private VSAssembly addOutputVSAssembly(VSOutputRecommendation model, RuntimeViewsheet rvs,
                                          SourceInfo source)
   {
      Viewsheet vs = rvs.getViewsheet();
      VSChartInfo temp = getTempChart(rvs);
      List<VSSubType> subTypes = model.getSubTypes();
      int selectedIndex = getSelectedSubTypeIdx(model);
      DataRef ref = model.getDataRef();
      VSAssembly assembly = null;

      if(model instanceof VSGaugeRecommendation) {
         assembly = new GaugeVSAssembly(vs, WizardRecommenderUtil.nextPrimaryAssemblyName());

         if(selectedIndex != -1) {
            VSSubType selectedSubType = subTypes.get(selectedIndex);
            String ftype = selectedSubType.getType();

            try {
               ((GaugeVSAssemblyInfo) assembly.getInfo()).setFace(Integer.parseInt(ftype));
            }
            catch(NumberFormatException ex) {
               throw new MessageException(ex.getMessage());
            }
         }
      }
      else if(model instanceof VSTextRecommendation) {
         assembly = new TextVSAssembly(vs, WizardRecommenderUtil.nextPrimaryAssemblyName());
      }

      if(assembly != null) {
         OutputVSAssemblyInfo info = (OutputVSAssemblyInfo) assembly.getInfo();
         info.setScalarBindingInfo(createScalarBindingInfo(ref, source, temp));
         vs.addAssembly(assembly);
      }

      return assembly;
   }

   private VSAssembly addFilterVSAssembly(VSFilterRecommendation model, Viewsheet vs,
                                          SourceInfo source)
   {
      List<VSSubType> subTypes = model.getSubTypes();
      int selectedIndex = getSelectedSubTypeIdx(model);
      String type = selectedIndex == -1 ? null : subTypes.get(selectedIndex).getType();
      DataRef[] refs = model.getDataRefs();
      AbstractSelectionVSAssembly assembly = null;

      if((VSFilterType.SELECTION_TREE + "").equals(type)) {
         assembly = new SelectionTreeVSAssembly(
            vs, WizardRecommenderUtil.nextPrimaryAssemblyName());
         SelectionTreeVSAssemblyInfo info = (SelectionTreeVSAssemblyInfo) assembly.getInfo();
         info.setDataRefs(getFilterDataRefs(refs));
         info.setTableName(source.getSource());
      }
      else if((VSFilterType.SELECTION_LIST + "").equals(type)) {
         assembly = new SelectionListVSAssembly(
            vs, WizardRecommenderUtil.nextPrimaryAssemblyName());
         SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) assembly.getInfo();
         info.setDataRef(refs[0]);
         info.setTableName(source.getSource());
         info.setTitleValue(getTitle(refs[0]));
      }
      else if((VSFilterType.RANGE_SLIDER + "").equals(type)) {
         assembly = new TimeSliderVSAssembly(
            vs, WizardRecommenderUtil.nextPrimaryAssemblyName());
         updateTimeSliderBinding((TimeSliderVSAssembly) assembly, refs[0], source);
      }
      else if((VSFilterType.CALENDAR + "").equals(type)) {
         assembly = new CalendarVSAssembly(
            vs, WizardRecommenderUtil.nextPrimaryAssemblyName());
         CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) assembly.getInfo();
         info.setDataRef(refs[0]);
         info.setTableName(source.getSource());
      }

      if(assembly != null) {
         assembly.setSourceType(source.getType());
         vs.addAssembly(assembly);
      }

      return assembly;
   }

   // For filter assembly, should not add the same date group to it.
   private DataRef[] getFilterDataRefs(DataRef[] refs) {
      List<String> names = new ArrayList();
      List<DataRef> refs1 = new ArrayList();

      for(int i = 0; refs != null && i < refs.length; i++) {
         String name = refs[i].getAttribute();

         if(!names.contains(name)) {
            names.add(name);
            refs1.add(refs[i]);
         }
      }

      return refs1.toArray(new DataRef[refs1.size()]);
   }

   private VSAssembly addTableVSAssembly(VSTableRecommendation model, RuntimeViewsheet rvs,
                                         SourceInfo source, boolean reload)
   {
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly oldTempAssembly = WizardRecommenderUtil.getTempAssembly(vs);
      TableVSAssembly assembly;
      Assembly wsTableAssembly = vs.getBaseWorksheet().getAssembly(source.getSource());

      if(oldTempAssembly != null) {
         VSAssemblyInfo oldInfo = oldTempAssembly.getVSAssemblyInfo();

         if(oldInfo instanceof TableVSAssemblyInfo && isEmbeddedSource(wsTableAssembly) &&
            (oldInfo instanceof EmbeddedTableVSAssemblyInfo ||
               ((TableVSAssemblyInfo) oldInfo).isEmbeddedTable()))
         {
            assembly = new EmbeddedTableVSAssembly(
               vs, WizardRecommenderUtil.nextPrimaryAssemblyName());
         }
         else {
            assembly = new TableVSAssembly(
               vs, WizardRecommenderUtil.nextPrimaryAssemblyName());
         }
      }
      else {
         assembly = new TableVSAssembly(
            vs, WizardRecommenderUtil.nextPrimaryAssemblyName());
      }

      TableVSAssemblyInfo info = (TableVSAssemblyInfo) assembly.getInfo();
      info.setSourceInfo(source);
      info.setColumnSelection(model.getColumns());
      vs.addAssembly(assembly);

      if(reload && oldTempAssembly instanceof TableVSAssembly) {
         TableVSAssembly oldTable = (TableVSAssembly) oldTempAssembly;
         // reload sort info.
         SortInfo sortInfo = oldTable.getSortInfo();
         assembly.setSortInfo(sortInfo);
      }

      return assembly;
   }

   private boolean isEmbeddedSource(Assembly wsTableAssembly) {
      Assembly baseTable = wsTableAssembly;

      if(wsTableAssembly instanceof MirrorTableAssembly) {
         baseTable = ((MirrorTableAssembly) wsTableAssembly).getTableAssembly();
      }

      return baseTable instanceof EmbeddedTableAssembly &&
         !(baseTable instanceof SnapshotEmbeddedTableAssembly);
   }

   private VSAssembly addCrosstabVSAssembly(VSCrosstabRecommendation model,
                                            RuntimeViewsheet rvs,
                                            SourceInfo source)
   {
      Viewsheet vs = rvs.getViewsheet();
      CrosstabVSAssembly assembly =
         new CrosstabVSAssembly(vs, WizardRecommenderUtil.nextPrimaryAssemblyName());
      CrosstabVSAssemblyInfo info = (CrosstabVSAssemblyInfo) assembly.getInfo();
      VSCrosstabInfo cinfo = model.getCrosstabInfo();
      cinfo.updateRuntimeId();
      info.setSourceInfo(source);
      info.setVSCrosstabInfo(cinfo);
      vs.addAssembly(assembly);

      return assembly;
   }

   private VSAssembly addChartVSAssembly(VSChartRecommendation model, RuntimeViewsheet rvs,
                                         SourceInfo source, CommandDispatcher dispatcher)
      throws Exception
   {
      int idx = getSelectedSubTypeIdx(model);

      if(idx == -1) {
         throw new MessageException("No valid subtype for chart!");
      }

      List<ChartInfo> list = model.getChartInfos();
      ChartVSAssembly assembly = new ChartVSAssembly(rvs.getViewsheet(),
         WizardRecommenderUtil.nextPrimaryAssemblyName());
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) assembly.getInfo();

      VSTemporaryInfo temporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
      info.setTitleVisibleValue(true);
      info.setVSChartInfo(list.size() > 0 && idx < list.size() ? (VSChartInfo) list.get(idx) : null);
      info.setSourceInfo(source);
      WizardRecommenderUtil.prepareCalculateRefs(rvs, info, temporaryInfo);
      GraphFormatUtil.fixDefaultNumberFormat(assembly.getChartDescriptor(),
              info.getVSChartInfo());
      rvs.getViewsheet().addAssembly(assembly);
      // sync legend status.
      boolean showLegend = temporaryInfo != null && temporaryInfo.isShowLegend();
      ChartDescriptor chartDescriptor = info.getChartDescriptor();
      TitlesDescriptor titles = chartDescriptor.getTitlesDescriptor();
      VSChartInfo chartInfo = info.getVSChartInfo();

      if(source != null) {
         VSUtil.setDefaultGeoColumns(chartInfo, rvs, source.getSource());
      }

      if(ChartRecommenderUtil.isHistogram(chartInfo)) {
         String group = ((VSDimensionRef) info.getVSChartInfo().getXField(0)).getGroupColumnValue();

         if(group.startsWith("Range@")) {
            titles.getXTitleDescriptor().setTitleValue(group.substring(6));
            titles.getYTitleDescriptor().setTitleValue(Catalog.getCatalog().getString("Frequency"));
         }
      }
      else {
         titles.getXTitleDescriptor().setTitleValue(null);
         titles.getYTitleDescriptor().setTitleValue(null);
      }

      Assembly originalAssembly = temporaryInfo == null ? null : getOriginalAssembly(temporaryInfo);

      if(originalAssembly instanceof ChartVSAssembly) {
         keepAesthetic((ChartVSAssembly) originalAssembly, assembly);
      }

      if(info.getVSChartInfo() != null) {
         (new ChangeChartProcessor()).fixSizeFrame(info.getVSChartInfo());
      }

      VSDndEvent event = VSDndEvent.builder()
                           .confirmed(false)
                           .checkTrap(true)
                           .sourceChanged(false)
                           .build();
      chartDataHandler.changeChartData(rvs, info, info, null, event, dispatcher);

      return assembly;
   }

   private String getTitle(DataRef ref) {
      if((ref.getRefType() & DataRef.CUBE) == 0) {
         return VSUtil.trimEntity(ref.getAttribute(), null);
      }

      ref = DataRefWrapper.getBaseDataRef(ref);
      return ref.toView();
   }

   private ScalarBindingInfo createScalarBindingInfo(DataRef ref, SourceInfo source,
      VSChartInfo temp)
   {
      ScalarBindingInfo info = new ScalarBindingInfo();
      info.setTableName(source.getSource());
      info.setType(source.getType());
      info.setColumn(ref);
      info.setColumnValue(ref.getName());
      info.setColumnType(ref.getDataType());

      if(temp.getYFieldCount() == 1 && temp.getYField(0) instanceof VSAggregateRef) {
         VSAggregateRef agg = (VSAggregateRef) temp.getYField(0);
         info.setAggregateValue(agg.getFormula().getFormulaName());

         if(agg.getSecondaryColumn() != null) {
            info.setColumn2Value(agg.getSecondaryColumnValue());
         }
         else if(agg.getNValue() != null) {
            info.setNValue(agg.getNValue());
         }
      }
      else {
         info.setAggregateValue(AssetUtil.getDefaultFormula(ref).getFormulaName());
      }

      return info;
   }

   private void updateTimeSliderBinding(TimeSliderVSAssembly assembly,
                                        DataRef ref, SourceInfo source)
   {
      TimeSliderVSAssemblyInfo assemblyinfo = (TimeSliderVSAssemblyInfo) assembly.getInfo();
      TimeInfo tinfo = new SingleTimeInfo();
      ((SingleTimeInfo) tinfo).setDataRef(ref);
      assemblyinfo.setComposite(false);
      int reftype = ref.getRefType();
      String dtype = ref.getDataType();

      if((reftype & DataRef.CUBE_DIMENSION) != 0 && !XSchema.isDateType(dtype)) {
         ((SingleTimeInfo) tinfo).setRangeTypeValue(TimeInfo.MEMBER);
      }
      else if(XSchema.isNumericType(dtype)) {
         // let TimeSliderVSAQuery to set the range size from data
         ((SingleTimeInfo) tinfo).setRangeTypeValue(TimeInfo.NUMBER);
      }
      else if(XSchema.TIME.equals(dtype)) {
         ((SingleTimeInfo) tinfo).setRangeTypeValue(TimeInfo.MINUTE_OF_DAY);
      }
      else if(XSchema.isDateType(dtype)) {
         ((SingleTimeInfo) tinfo).setRangeTypeValue(TimeInfo.MONTH);
      }
      else {
         tinfo = new CompositeTimeInfo();
         ((CompositeTimeInfo) tinfo).setDataRefs(new DataRef[]{ref});
         assemblyinfo.setComposite(true);
      }

      assemblyinfo.setTimeInfo(tinfo);
      assemblyinfo.setTableName(source.getSource());
      assemblyinfo.setTitleValue(getTitle(ref));
      assembly.resetSelection();
   }

   public BindingModel createTempChartBinding(VSTemporaryInfo temporaryInfo) {
      return bindingFactory.createModel(temporaryInfo.getTempChart());
   }

   public boolean sourceChanged(String table, SourceInfo oldSource) {
      if(oldSource == null || table == null) {
         return false;
      }

      return !VSUtil.getTableName(table).equals(oldSource.getSource());
   }

   private String getNormalizedTableName(String table) {
      return VSUtil.stripOuter(table);
   }

   /**
    * reload binding nodes.
    * @param assembly edit assembly
    * @return path of binding nodes.
    */
   public List<String> getSelectedPath(AggregateInfo aggInfo, VSAssembly assembly,
                                       AssetEntry baseEntry, Principal principal)
   {
      if(assembly == null || StringUtils.isEmpty(assembly.getTableName())) {
         return new ArrayList<>();
      }

      String aname = getNormalizedTableName(assembly.getTableName()) + "/";
      List<String> selectedPaths = new ArrayList<>();
      DataRef[] allBindingRefs = getAssemblyBindings(assembly);
      List<TableAssembly> assemblies = getLMTableAssemblies(baseEntry, principal);

      for (DataRef ref: allBindingRefs) {
         if(isExpressOrVariable(ref)) {
            continue;
         }

         String tableName = aname;
         TableAssembly tableAssembly
            = getLMTableAssembly(assemblies, getOriginalColName(ref));

         if(baseEntry.isLogicModel() && tableAssembly != null) {
            tableName = tableAssembly.getName() + "/";
         }
         else if(baseEntry.isLogicModel() && isCalcField(assembly, ref)) {
            tableName = "";
         }

         String path = getColumnPath(aggInfo, ref, tableName);

         if(!selectedPaths.contains(path)) {
            selectedPaths.add(path);
         }
      }

      return selectedPaths;
   }

   private boolean isCalcField(VSAssembly assembly, DataRef ref) {
      CalculateRef[] calcFields
         = assembly.getViewsheet().getCalcFields(assembly.getTableName());

      return WizardRecommenderUtil.isCalcField(ref, calcFields);
   }

   public String getColumnPath(AggregateInfo aggInfo, DataRef ref, String table) {
      String path;
      table = table.isEmpty() || table.endsWith("/") ? table : table + "/";
      String dimPath = "/baseWorksheet/" + table + "/";
      String aggPath = "/baseWorksheet/" + table + "/";

      if(aggInfo != null && (aggInfo.isEmpty() && isMeasure(ref) ||
         !aggInfo.isEmpty() && findGroupRef(aggInfo, ref) == null))
      {
         path = aggPath + trimEntity(getOriginalColName(ref), ref.getEntity());
      }
      else {
         path = dimPath + trimEntity(getOriginalColName(ref), ref.getEntity());
      }

      return path;
   }

   public String fixTableName(AssetEntry baseEntry, VSAssembly assembly, DataRef ref,
                               String tableName, Principal principal)
   {
      List<TableAssembly> assemblies = getLMTableAssemblies(baseEntry, principal);
      TableAssembly tableAssembly
         = getLMTableAssembly(assemblies, getOriginalColName(ref));

      if(baseEntry.isLogicModel() && tableAssembly != null) {
         tableName = tableAssembly.getName() + "/";
      }
      else if(baseEntry.isLogicModel() && isCalcField(assembly, ref)) {
         tableName = "";
      }

      return tableName;
   }

   // if calc changed from date to non-date, make sure multiple date levels are removed and
   // only a single field binding exists
   public void calculatedRefTypeChanged(RuntimeViewsheet rvs, VSAssembly assembly,
                                        CalculateRef ocalc, CalculateRef cref,
                                        CommandDispatcher dispatcher)
   {
      if(!(assembly instanceof ChartVSAssembly)) {
         return;
      }

      // if date changed to non-date, remove multiple (level) dimension of the same ref
      if(XSchema.isDateType(ocalc.getDataType()) && !XSchema.isDateType(cref.getDataType())) {
         VSChartInfo cinfo = ((ChartVSAssembly) assembly).getVSChartInfo();
         Set found = new HashSet();

         for(int i = cinfo.getXFieldCount() - 1; i >= 0; i--) {
            ChartRef ref = cinfo.getXField(i);

            if(ref instanceof ChartDimensionRef && ref.getName().equals(ocalc.getName())) {
               if(found.contains(ref.getName())) {
                  cinfo.removeXField(i);
               }
               else {
                  ((ChartDimensionRef) ref).setDateLevel(0);
                  found.add(ref.getName());
               }
            }
         }

         RefreshVsWizardBindingCommand refreshBindingCommand = new RefreshVsWizardBindingCommand();
         Viewsheet vs = rvs.getViewsheet();
         Assembly tempChart = vs.getAssembly(VSWizardConstants.TEMP_CHART_NAME);
         refreshBindingCommand.setBindingModel(
            getWizardBindingModel(rvs, (VSAssembly) tempChart, false));
         dispatcher.sendCommand(refreshBindingCommand);
      }
   }

   /**
    * find group ref from aggregateInfo
    */
   public GroupRef findGroupRef(AggregateInfo aggInfo, DataRef dataRef) {
      GroupRef[] groupRefs = aggInfo.getGroups();
      String dataName = trimEntity(dataRef.getName(), dataRef.getEntity());

      for (GroupRef groupRef: groupRefs) {
         String groupName = trimEntity(groupRef.getName(), groupRef.getEntity());

         if(dataName.equals(groupName)) {
            return groupRef;
         }
      }

      return null;
   }

   public String getOriginalColName(DataRef ref) {
      String colName = ref.getName();

      if(ref instanceof VSAggregateRef) {
         VSAggregateRef aggregateRef = (VSAggregateRef) ref;
         colName = aggregateRef.getColumnValue();
      }

      return colName;
   }

   private String trimEntity(String refName, String entity) {
      if(!StringUtils.isEmpty(refName) && refName.startsWith(entity + ".")) {
         refName = refName.substring(entity.length() + 1);
      }

      return refName;
   }

   private boolean isMeasure(DataRef ref) {
      boolean isAgg;

      if(ref instanceof ColumnRef) {
         ColumnRef columnRef = (ColumnRef) ref;
         isAgg = VSEventUtil.isMeasure(columnRef);
      }
      else if(ref instanceof GroupRef || ref instanceof VSDimensionRef) {
         isAgg = false;
      }
      else if(ref instanceof AggregateRef || ref instanceof VSAggregateRef) {
         isAgg = true;
      }
      else {
         isAgg = ref.getRefType() == DataRef.MEASURE;
      }

      return isAgg;
   }

   /**
    * Change current type as primary type
    */
   public void addOriginalAsPrimary(String vsId, Principal principal, String linkUri, boolean selectOriginal,
                                    CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);

      if(rvs == null) {
         LOG.info("Sheet is not exist! id: " + vsId);
         return;
      }

      Viewsheet vs = rvs.getViewsheet();
      VSTemporaryInfo tempInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
      VSWizardOriginalModel originalModel = tempInfo.getOriginalModel();
      String originalName = originalModel != null ? originalModel.getOriginalName() : null;
      VSAssembly assembly = (VSAssembly) vs.getAssembly(originalName).clone();
      // Shouldn't apply wizard editing status when preview original assembly.
      assembly.setWizardEditing(false);
      VSAssemblyInfo assemblyInfo = (VSAssemblyInfo) assembly.getInfo();
      assemblyInfo.setName(WizardRecommenderUtil.nextPrimaryAssemblyName());
      syncChartAssembly(tempInfo.getTempChart(), assembly);

      vs.addAssembly(assembly);
      this.updatePrimaryAssembly(rvs, linkUri, assembly, true, selectOriginal, dispatcher);

      if(assembly instanceof TableVSAssembly) {
         BindingModel binding = bindingFactory.createModel(assembly);
         SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
         dispatcher.sendCommand(bcommand);
      }

      //send load description command
      if(hasDescriptionAssembly(assembly)) {
         //send command
         String desAssembly =
            ((DescriptionableAssemblyInfo) assembly.getVSAssemblyInfo()).getDescriptionName();

         if(desAssembly == null) {
            return;
         }

         TextVSAssembly textVSAssembly = (TextVSAssembly)vs.getAssembly(desAssembly);

         if(textVSAssembly == null) {
            return;
         }

         RefreshDescriptionCommand command =
            new RefreshDescriptionCommand(textVSAssembly.getTextValue());
         dispatcher.sendCommand(command);
         temporaryInfoService.getVSTemporaryInfo(rvs).setDescription(textVSAssembly.getTextValue());
      }
      else if(assembly instanceof TitledVSAssembly) {
         temporaryInfoService.getVSTemporaryInfo(rvs)
            .setDescription(((TitledVSAssembly)assembly).getTitleValue());
      }
   }

   public DataRef getDataRef(AggregateInfo aggInfo, String refName) {
      GroupRef[] grefs = aggInfo.getGroups();
      AggregateRef[] arefs = aggInfo.getAggregates();

      for (GroupRef ref : grefs) {
         if(Tool.equals(ref.getName(), refName)) {
            return ref;
         }
      }

      for (AggregateRef ref : arefs) {
         if(Tool.equals(ref.getName(), refName)) {
            return ref;
         }
      }

      return null;
   }

   public void reloadLegendVisible(RuntimeViewsheet rtv, VSAssembly assembly) {
      VSTemporaryInfo temporaryInfo = temporaryInfoService.getVSTemporaryInfo(rtv);
      DataRef[] drefs = getAssemblyBindings(assembly);

      if(drefs == null || drefs.length == 0) {
         return;
      }

      if(assembly instanceof ChartVSAssembly) {
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) assembly.getInfo();
         ChartDescriptor desc = info.getChartDescriptor();
         VSChartInfo cinfo = info.getVSChartInfo();
         boolean visible = !GraphBuilder.isLegendHidden(desc, cinfo, info.getMaxSize() != null);
         temporaryInfo.setShowLegend(visible);
         GraphFormatUtil.fixDefaultNumberFormat(((ChartVSAssembly)assembly).getChartDescriptor(),
                 cinfo);
      }
   }

   /**
    * Convert the binding of assembly to temp chart,
    * add the dimension to x, add the measure to y.
    */
   public void convertBinding(Viewsheet vs, ChartVSAssembly tempChart, VSAssembly assembly,
                              VSTemporaryInfo temporaryInfo)
   {
      VSChartInfo chartInfo = tempChart.getVSChartInfo();
      chartInfo.removeXFields();
      chartInfo.removeYFields();
      DataRef[] refs = getAssemblyBindings(assembly);
      addFieldsToTempChart(vs, chartInfo, refs, assembly, temporaryInfo);

      if(assembly instanceof ChartVSAssembly) {
         VSChartInfo assemblyInfo = ((ChartVSAssembly) assembly).getVSChartInfo();
         chartInfo.setMeasureMapType(assemblyInfo.getMapType());
      }
   }

   private DataRef[] getAssemblyBindings(VSAssembly assembly) {
      if(assembly instanceof CrosstabVSAssembly) {
         VSCrosstabInfo crosstabInfo = ((CrosstabVSAssembly) assembly).getVSCrosstabInfo();
         DataRef[] result =
            (DataRef[]) ArrayUtils.addAll(crosstabInfo.getRowHeaders(), crosstabInfo.getColHeaders());

         return (DataRef[]) ArrayUtils.addAll(result, crosstabInfo.getAggregates());
      }
      else if(assembly instanceof ChartVSAssembly) {
         return GraphUtil.getChartBindingRefs(((ChartVSAssembly) assembly).getVSChartInfo());
      }
      else if(assembly instanceof OutputVSAssembly) {
         return getOutputBindingRefs((OutputVSAssembly) assembly);
      }
      else if(assembly instanceof TableVSAssembly) {
         return assembly.getBindingRefs();
      }
      else {
         return assembly.getAllBindingRefs();
      }
   }

   private DataRef[] getOutputBindingRefs(OutputVSAssembly assembly) {
      ScalarBindingInfo scalarBindingInfo = assembly.getScalarBindingInfo();
      DataRef column = scalarBindingInfo.getColumn();

      return new DataRef[] { column };
   }

   /**
    * update the field to temp chart.
    * add the dimension to x, add the measure to y.
    */
   public void updateTemporaryFields(RuntimeViewsheet rvs, AssetEntry[] entries,
                                     VSTemporaryInfo vsTemporaryInfo)
      throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      WizardRecommenderUtil.refreshDateInterval(box, entries, vsTemporaryInfo);
      ChartVSAssembly tempChart = vsTemporaryInfo.getTempChart();
      ChartVSAssemblyInfo oldVsAssemblyInfo = tempChart.getChartInfo();
      ChartVSAssemblyInfo newVsAssemblyInfo = (ChartVSAssemblyInfo) oldVsAssemblyInfo.clone();
      VSChartInfo oinfo = oldVsAssemblyInfo.getVSChartInfo();
      VSChartInfo ninfo = newVsAssemblyInfo.getVSChartInfo();
      ChartRef[] oldXFields = oinfo.getXFields();
      ChartRef[] oldYFields = oinfo.getYFields();
      clearTempChartAxis(ninfo, ninfo.getXFields(), true);
      clearTempChartAxis(ninfo, ninfo.getYFields(), false);
      Map<String, VSFormat> formatMap = new HashMap<>();

      Arrays.stream(entries).forEach((entry) -> {
         ChartRef chartRef =
            ChartRecommenderUtil.createChartRef(entry, rvs, vsTemporaryInfo, formatMap);
         boolean find = keepFieldsToTempChart(ninfo, oldXFields, chartRef);

         if(!find) {
            find = keepFieldsToTempChart(ninfo, oldYFields, chartRef);
         }

         if(!find) {
            vsTemporaryInfo.setFormat(
               chartRef.getFullName(), formatMap.get(chartRef.getFullName()));
            updateTemporaryFields(chartRef, ninfo, null, (DataRefWrapper) chartRef,
                                  vsTemporaryInfo, false);
         }
         else if(formatMap.get(chartRef.getFullName()) == null) {
            // initialize format
            vsTemporaryInfo.setFormat(chartRef.getFullName(), null);
         }
      });

      ninfo.setRTXFields(ninfo.getRTXFields());
      ninfo.setRTYFields(ninfo.getRTYFields());
      tempChart.setVSAssemblyInfo(newVsAssemblyInfo);

      syncFormats(ninfo.getXFields(), ninfo.getYFields(), vsTemporaryInfo, formatMap);
   }

   private void syncFormats(ChartRef[] xFields, ChartRef[] yFields, VSTemporaryInfo tempInfo,
                            Map<String, VSFormat> formatMap)
   {
      // remove any formats that do not have a corresponding field
      Set<String> formatKeys = Stream.concat(Arrays.stream(xFields), Arrays.stream(yFields))
         .map(this::getNameForFormat)
         .collect(Collectors.toSet());

      for(ChartRef ref : xFields) {
         syncFormat(ref, formatMap, tempInfo);
      }

      for(ChartRef ref : yFields) {
         syncFormat(ref, formatMap, tempInfo);
      }
   }

   private void syncFormat(ChartRef field, Map<String, VSFormat> formatMap,
                           VSTemporaryInfo tempInfo)
   {
      String name = getNameForFormat(field);

      if(tempInfo.getUserFormat(name) == null) {
         if(formatMap.containsKey(name)) {
            tempInfo.setFormat(name, formatMap.get(name));
         }
         else if(field instanceof VSChartAggregateRef) {
            VSChartAggregateRef aggr = (VSChartAggregateRef) field;

            if(aggr.getFormulaValue() != null) {
               String column;

               if((column = aggr.getColumnValue()) != null && formatMap.containsKey(column)) {
                  tempInfo.setFormat(name, formatMap.get(column));
               }
               else if((column = aggr.getSecondaryColumnValue()) != null &&
                  formatMap.containsKey(column))
               {
                  tempInfo.setFormat(name, formatMap.get(column));
               }
            }
         }
      }
   }

   private void clearTempChartAxis(VSChartInfo chartInfo, ChartRef[] chartRefs, boolean isX) {
      for(int i = chartRefs.length - 1; i >= 0; i--) {
         ChartRef field = chartRefs[i];

         if(!isExpressOrVariable(field)) {
            if(isX) {
               chartInfo.removeXField(i);
            }
            else {
               chartInfo.removeYField(i);
            }
         }
      }
   }

   private boolean isExpressOrVariable(DataRef field) {
      String value = null;

      if(field instanceof VSAggregateRef) {
         value = ((VSAggregateRef) field).getColumnValue();
      }
      else if(field instanceof VSDimensionRef) {
         value =((VSDimensionRef) field).getGroupColumnValue();
      }

      return VSUtil.isVariableValue(value) || VSUtil.isScriptValue(value);
   }

   private void addFieldsToTempChart(Viewsheet vs, VSChartInfo chartInfo, DataRef[] dataRefs,
                                     VSAssembly assembly, VSTemporaryInfo temporaryInfo)
   {
      if(chartInfo == null || dataRefs == null) {
         return;
      }

      Arrays.stream(dataRefs).forEach(ref -> {
         VSAssembly tempChart = vs.getAssembly(VSWizardConstants.TEMP_CHART_NAME);
         backToDefaultFormula(vs, assembly, ref, tempChart);

         if(findIndex(chartInfo.getXFields(), ref, false).size() == 0 &&
            findIndex(chartInfo.getYFields(), ref, false).size() == 0)
         {
            updateTemporaryFields(ref, chartInfo, assembly, null, temporaryInfo, true);
         }
      });
   }

   private boolean keepFieldsToTempChart(VSChartInfo chartInfo, ChartRef[] fields,
                                         ChartRef chartRef)
   {
      List<Integer> indexes = findIndex(fields, chartRef, true);
      boolean hasOld = false;

      if(indexes.size() > 0) {
         hasOld = true;

         for(int index : indexes) {
            updateTemporaryFields(fields[index], chartInfo, (DataRefWrapper) chartRef);
         }
      }

      return hasOld;
   }

   private void updateTemporaryFields(DataRef dataRef, VSChartInfo chartInfo,
                                      DataRefWrapper chartField)
   {
      updateTemporaryFields(dataRef, chartInfo, null, chartField, null, false);
   }

   /**
    * update the field to temp chart.
    * add the dimension to x, add the measure to y.
    * @param chartField field which created by data entry
    */
   private void updateTemporaryFields(DataRef dataRef, VSChartInfo chartInfo,
                                      VSAssembly assembly, DataRefWrapper chartField,
                                      VSTemporaryInfo temporaryInfo, boolean fromConvert)
   {
      ChartRef chartRef;

      if(dataRef instanceof ChartRef) {
         chartRef = (ChartRef) dataRef;
         purifyChartRef(chartRef);

         if(chartRef instanceof DataRefWrapper && chartField != null) {
            ((DataRefWrapper) chartRef).setDataRef(chartField.getDataRef());
         }

         //set the default date level to dimension ref.
         if(chartRef instanceof VSDimensionRef && XSchema.isDateType(chartRef.getDataType())
            && chartField != null && ((VSDimensionRef) chartRef).getDateLevelValue() == null)
         {
            ((VSDimensionRef) chartRef).setDateLevelValue(
               ((VSDimensionRef) chartField).getDateLevelValue());
         }
      }
      else {
         chartRef = convertToChartRefs(dataRef, assembly);
      }

      if(temporaryInfo != null) {
         temporaryInfo.setBrandNewColumn(chartRef.getFullName(), !fromConvert);
      }

      if(isMeasure(chartRef)) {
         chartInfo.addYField(chartRef);
      }
      else {
         chartInfo.addXField(chartRef);
      }
   }

   private void backToDefaultFormula(Viewsheet vs, VSAssembly assembly, DataRef dataRef,
                                     VSAssembly tempChart)
   {
      boolean back = false;
      VSAggregateRef aggr = null;

      if(assembly instanceof ChartVSAssembly) {
         ChartVSAssembly chart = (ChartVSAssembly) assembly;
         VSChartInfo chartInfo = chart.getVSChartInfo();

         if(GraphTypes.isPoint(chartInfo.getChartType()) ||
            GraphTypes.isBoxplot(chartInfo.getChartType()))
         {
            boolean scatter = GraphTypeUtil.isScatterMatrix(chartInfo);
            boolean isBox = GraphTypes.isBoxplot(chartInfo.getChartType());
            boolean hasMeasure = GraphUtil.hasMeasureOnX(chartInfo) &&
               GraphUtil.hasMeasureOnY(chartInfo);
            back = isBox || scatter || hasMeasure;
         }

         if(dataRef instanceof VSAggregateRef) {
            aggr = (VSAggregateRef) dataRef;
         }
      }

      // if the original chart has none aggregate (e.g. scattered chart), we should set
      // it back to default for recommendations
      // calc field shouldn't set formula to sum
      if(back && aggr != null && aggr.getFormula() == AggregateFormula.NONE
         && vs.getCalcField(tempChart.getTableName(), aggr.getFullName()) == null)
      {
         String dtype = aggr.getDataRef() != null ? aggr.getDataRef().getDataType() : null;
         aggr.setFormulaValue(dtype == null || XSchema.isNumericType(dtype) ?
            AggregateFormula.SUM.getFormulaName() : AggregateFormula.COUNT_ALL.getFormulaName());
         aggr.setAggregated(true);
      }
   }

   /**
    * Convert the dataRef to ChartRef.
    */
   private ChartRef convertToChartRefs(DataRef ref, VSAssembly assembly) {
      ChartRef chartRef;
      String caption = null;
      String field = null;
      int dateLevel = -1;
      String formulaValue = null;
      DataRef dataRef = null;
      DataRef secondaryColumn = null;
      String nValue = null;
      String col2Value = null;
      boolean isTimeSeries = false;
      XNamedGroupInfo namedGroupInfo = null;
      String groupType = null;
      String rankNValue = null;
      String rankOpValue = null;
      String rankColValue = null;
      String groupOthersValue = null;
      String percentageValue = null;

      if(ref instanceof ColumnRef) {
         ColumnRef columnRef = (ColumnRef) ref;
         field = columnRef.getAttribute();
         dataRef = ref;

         if(assembly instanceof OutputVSAssembly) {
            ScalarBindingInfo scalarBindingInfo
               = ((OutputVSAssembly) assembly).getScalarBindingInfo();
            formulaValue = scalarBindingInfo.getAggregateFormula().getFormulaName();
            secondaryColumn = scalarBindingInfo.getSecondaryColumn();
            nValue = scalarBindingInfo.getNValue();
            col2Value = scalarBindingInfo.getColumn2Value();
         }
         else {
            formulaValue = AggregateFormula.SUM.getFormulaName();
         }
      }
      else if(ref instanceof VSDimensionRef) {
         VSDimensionRef dim = (VSDimensionRef)ref;
         field = dim.getGroupColumnValue();
         caption = dim.getCaption();
         dataRef = dim.getDataRef();
         namedGroupInfo = dim.getNamedGroupInfo();
         groupType = dim.getGroupType();
         rankNValue = dim.getRankingNValue();
         rankOpValue = dim.getRankingOptionValue();
         rankColValue = dim.getRankingColValue();
         groupOthersValue = dim.getGroupOthersValue();

         if(XSchema.isDateType(dim.getDataType())) {
            dateLevel = dim.getDateLevel();
         }

         isTimeSeries = ((VSDimensionRef) ref).isTimeSeries();
      }
      else if(ref instanceof VSAggregateRef) {
         VSAggregateRef agg = (VSAggregateRef) ref;
         field = agg.getColumnValue();
         caption = agg.getCaption();
         formulaValue = agg.getFormulaValue();
         dataRef = agg.getDataRef();
         nValue = agg.getNValue();
         percentageValue = agg.getPercentageOptionValue();
         AggregateFormula formula = agg.getFormula();

         if(formula != null && formula.isTwoColumns()) {
            secondaryColumn = agg.getSecondaryColumn();
            col2Value = agg.getSecondaryColumnValue();
         }
      }

      if(isMeasure(ref)) {
         VSChartAggregateRef agg = new VSChartAggregateRef();
         chartRef = agg;
         agg.setRefType(ref.getRefType());
         agg.setColumnValue(field);
         agg.setOriginalDataType(ref.getDataType());
         agg.setFormulaValue(formulaValue);
         agg.setCaption(caption);
         agg.setDataRef(dataRef);
         agg.setSecondaryColumn(secondaryColumn);
         agg.setNValue(nValue);
         agg.setSecondaryColumnValue(col2Value);
         agg.setPercentageOptionValue(percentageValue);
      }
      else {
         VSChartDimensionRef dim = new VSChartDimensionRef();
         chartRef = dim;
         dim.setGroupColumnValue(field);
         dim.setDataType(ref.getDataType());
         dim.setRefType(ref.getRefType());
         dim.setCaption(caption);
         dim.setDataRef(dataRef);
         dim.setTimeSeries(isTimeSeries);
         dim.setNamedGroupInfo(namedGroupInfo);
         dim.setGroupType(groupType);
         dim.setRankingNValue(rankNValue);
         dim.setRankingOptionValue(rankOpValue);
         dim.setRankingColValue(rankColValue);
         dim.setGroupOthersValue(groupOthersValue);

         if(dateLevel != -1) {
            dim.setDateLevelValue(String.valueOf(dateLevel));
         }
         else {
            if(XSchema.TIME.equals(dim.getDataType())) {
               dim.setDateLevelValue(DateRangeRef.HOUR_INTERVAL + "");
            }
         }
      }

      return chartRef;
   }

   /**
    * Copy chart brush from assembly1 to assembly2.
    */
   public void syncChartAssembly(Assembly assembly1, Assembly assembly2) {
      if(!(assembly1 instanceof ChartVSAssembly) || !(assembly2 instanceof ChartVSAssembly)) {
         return;
      }

      ChartVSAssembly chart1 = (ChartVSAssembly) assembly1;
      ChartVSAssembly chart2 = (ChartVSAssembly) assembly2;
      VSSelection selection = chart1.getBrushSelection();

      // keep target. (54593)
      chart2.setChartDescriptor(chart1.getChartDescriptor());

      if(selection != null && !selection.isEmpty()) {
         chart2.setBrushSelection(selection);
      }
   }

   /**
    * Clean chart brush from original assembly.
    */
   public void clearOriginalBrush(Viewsheet vs, String originalName) {
      Assembly original = vs.getAssembly(originalName);

      if(original instanceof ChartVSAssembly) {
         ChartVSAssembly chart = (ChartVSAssembly) original;

         if(chart.getBrushSelection() != null) {
            chart.setBrushSelection(null);
            vs.setBrush(chart.getTableName(), chart);
         }
      }
   }

   /**
    * Copy format from assembly to formatMap.
    */
   public void reloadFormats(VSTemporaryInfo temporaryInfo, RuntimeViewsheet rvs, VSAssembly assembly)
      throws Exception
   {
      if(assembly instanceof TableDataVSAssembly) {
         updateTableDataFormat(rvs, temporaryInfo, (TableDataVSAssembly) assembly, false);
      }
      else if(assembly instanceof ChartVSAssembly) {
         updateChartFormat(temporaryInfo, assembly, rvs, false);
      }
      else if(assembly instanceof OutputVSAssembly) {
         updateOutputFormat(temporaryInfo, (OutputVSAssembly) assembly, false);
      }
      else if(assembly instanceof CalendarVSAssembly || assembly instanceof SelectionVSAssembly) {
         updateSelectionFormat(temporaryInfo, (SelectionVSAssembly) assembly, false);
      }
   }

   /**
    * Copy format from temp assembly to formatMap.
    */
   private void updateFormats(VSTemporaryInfo tempInfo, VSAssembly assembly,
                              RuntimeViewsheet rvs, CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      if(assembly instanceof TableDataVSAssembly) {
         updateTableDataFormat(rvs, tempInfo, (TableDataVSAssembly) assembly, true);
      }
      else if(assembly instanceof ChartVSAssembly) {
         updateChartFormat(tempInfo, assembly, rvs, true);
         ((ChartVSAssembly) assembly).getVSChartInfo().clearRuntime();
      }
      else if(assembly instanceof OutputVSAssembly) {
         updateOutputFormat(tempInfo, (OutputVSAssembly) assembly, true);
      }
      else if(assembly instanceof CalendarVSAssembly ||  assembly instanceof SelectionVSAssembly) {
         updateSelectionFormat(tempInfo, (SelectionVSAssembly) assembly, true);
      }

      if(assembly instanceof TableDataVSAssembly
         || assembly instanceof ChartVSAssembly
         || assembly instanceof OutputVSAssembly
         || assembly instanceof SelectionVSAssembly)
      {
         this.placeholderService.execute(rvs, assembly.getAbsoluteName(), linkUri,
            VSAssembly.VIEW_CHANGED, dispatcher);
      }
   }

   private TableDataPath getSelectionDataPath(SelectionVSAssembly assembly, int level, boolean last) {
      TableDataPath dataPath = null;

      DataRef[] refs = assembly.getDataRefs();

      if(assembly instanceof TimeSliderVSAssembly || assembly instanceof CalendarVSAssembly) {
         return new TableDataPath(-1, TableDataPath.OBJECT);
      }
      else if(refs.length == 1 || refs.length > 1 && last) {
         dataPath = VSWizardDataPathConstants.DETAIL;
      }
      else if(assembly instanceof SelectionTreeVSAssembly) {
         dataPath = VSWizardDataPathConstants.GROUP_HEADER_CELL;
         dataPath.setLevel(level);
      }

      return dataPath;
   }

   private List<String> getDataPath(VSDataRef[] fields, String field) {
      List<String> result = new ArrayList<>();

      for(VSDataRef vsDataRef : fields) {
         result.add(vsDataRef.getFullName());

         if(vsDataRef.getFullName().equals(field)) {
            return result;
         }
      }

      return new ArrayList<>();
   }

   /**
    * Find the position of the ref index.
    */
   public static List<Integer> findIndex(DataRef[] refs, DataRef ref, boolean justName) {
      List<Integer> result = new ArrayList<>();

      for(int i = 0; i < refs.length; i++) {
         if(refs[i] instanceof VSDimensionRef && ref instanceof VSDimensionRef) {
            VSDimensionRef dimRef = (VSDimensionRef) refs[i];
            VSDimensionRef otherDimRef = (VSDimensionRef) ref;

            if(Objects.equals(dimRef.getGroupType(), otherDimRef.getGroupType()) &&
               Objects.equals(dimRef.getGroupColumnValue(), otherDimRef.getGroupColumnValue()) &&
               (justName || dimRef.getDateLevel() == otherDimRef.getDateLevel()))
            {
               result.add(i);
            }
         }

         if(refs[i] instanceof VSAggregateRef && ref instanceof  VSAggregateRef) {
            VSAggregateRef agg = (VSAggregateRef) refs[i];
            VSAggregateRef other = (VSAggregateRef) ref;

            // columnRef's value is entity + "." + attribute
            if(agg.getColumnValue() != null &&
               (agg.getColumnValue().equals(other.getColumnValue()) ||
               agg.getColumnValue().indexOf("." + other.getColumnValue()) > 0)
               && (justName || Tool.equals(agg.getFullName(), other.getFullName()) &&
               Tool.equals(agg.getFormulaValue(), other.getFormulaValue())))
            {
               result.add(i);
            }
         }

         if(refs[i] instanceof VSDimensionRef && ref instanceof ColumnRef) {
            VSDimensionRef dimensionRef = (VSDimensionRef) refs[i];
            ColumnRef otherColumnRef = (ColumnRef) ref;

            if(dimensionRef.getGroupColumnValue().equals(otherColumnRef.getAttribute())) {
               result.add(i);
            }
         }
      }

      return result;
   }

   /**
    * Return chartinfo for the temp chart which used to store the selected binding fields that
    * will be used to recommend proper vs objects.
    */
   public VSChartInfo getTempChart(Principal principal) throws Exception {
      String runtimeId = runtimeViewsheetRef.getRuntimeId();
      return getTempChart(viewsheetService.getViewsheet(runtimeId, principal));
   }

   public VSChartInfo getTempChart(RuntimeViewsheet rvs) {
      Viewsheet vs = rvs.getViewsheet();
      ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(VSWizardConstants.TEMP_CHART_NAME);
      return chart == null ? null : chart.getVSChartInfo();
   }

   public VSChartInfo getTempChart(VSWizardData wizardData) {
      ChartVSAssembly chart = null;

      if(wizardData != null && wizardData.getVsTemporaryInfo() != null) {
         chart = wizardData.getVsTemporaryInfo().getTempChart();
      }

      return chart == null ? null : chart.getVSChartInfo();
   }

   /**
    * Create the pseudo base table assemblies for the logical model.
    */
   public List<TableAssembly> getLMTableAssemblies(AssetEntry baseEntry, Principal principal) {
      if(baseEntry != null && baseEntry.isLogicModel() && baseEntry.getProperty("pesdo2") == null) {
         return VSEventUtil.createPseudoAssemblies(assetRepository, baseEntry, principal);
      }

      return new ArrayList<>();
   }

   /**
    * Get the binding table assembly in the logical model
    */
   public TableAssembly getLMTableAssembly(List<TableAssembly> assemblies, String colName) {
      if(assemblies.size() == 0) {
         return null;
      }

      for (TableAssembly assembly : assemblies) {
         ColumnSelection columns = assembly.getColumnSelection();

         if(columns != null && columns.getAttribute(colName) != null) {
            return assembly;
         }
      }

      return null;
   }

   /**
    * @param updateAssembly true to copy format from formatMap to user format, false to copy
    *                       format from user format to formatMap.
    */
   private void updateChartFormat(VSTemporaryInfo tempInfo, VSAssembly assembly,
                                  RuntimeViewsheet rvs, boolean updateAssembly)
   {
      VSChartInfo chartInfo = ((ChartVSAssembly) assembly).getVSChartInfo();
      ChartRef[] yrefs = updateAssembly ? chartInfo.getYFields() : chartInfo.getRTYFields();
      yrefs = Arrays.copyOf(yrefs, yrefs.length);
      ChartRef[] xrefs = updateAssembly ? chartInfo.getXFields() : chartInfo.getRTXFields();
      xrefs = Arrays.copyOf(xrefs, xrefs.length);
      ChartRef[] grefs = chartInfo.getGroupFields();
      grefs = Arrays.copyOf(grefs, grefs.length);
      ChartRef pref = chartInfo.getPeriodField();

      if(updateAssembly && pref != null) {
         ChartRef[] rxrefs = chartInfo.getRTXFields();
         ChartRef[] nrefs;

         if(rxrefs.length > 0 && Tool.equals(pref.getFullName(), rxrefs[0].getFullName())) {
            nrefs = xrefs = Arrays.copyOf(xrefs, xrefs.length + 1);
         }
         else {
            nrefs = yrefs = Arrays.copyOf(yrefs, yrefs.length + 1);
         }

         nrefs[nrefs.length - 1] = pref;
      }

      for(ChartRef ref : xrefs) {
         AxisDescriptor descriptor = GraphUtil.getAxisDescriptor(chartInfo, ref);
         updateAxisRefFormats(descriptor, ref, updateAssembly, tempInfo, assembly, rvs);
      }

      for(ChartRef ref : yrefs) {
         AxisDescriptor descriptor = GraphUtil.getAxisDescriptor(chartInfo, ref);
         updateAxisRefFormats(descriptor, ref, updateAssembly, tempInfo, assembly, rvs);

         if(chartInfo.isMultiAesthetic() && ref instanceof VSChartAggregateRef) {
            VSChartAggregateRef chartRef = (VSChartAggregateRef) ref;
            updateMultiStyleVisualFormat(tempInfo, assembly, rvs, updateAssembly, chartRef.getColorField());
            updateMultiStyleVisualFormat(tempInfo, assembly, rvs, updateAssembly, chartRef.getShapeField());
            updateMultiStyleVisualFormat(tempInfo, assembly, rvs, updateAssembly, chartRef.getSizeField());
            updateMultiStyleVisualFormat(tempInfo, assembly, rvs, updateAssembly, chartRef.getTextField());
            updateTextFormat(chartRef, tempInfo, assembly, rvs, updateAssembly);
         }
      }

      for(ChartRef ref : grefs) {
         try {
            PlotDescriptor descriptor = ((ChartVSAssembly)assembly).getChartDescriptor().getPlotDescriptor();
            updatePlotFormats(descriptor, ref.getFullName(), tempInfo, assembly, rvs, chartInfo, updateAssembly);
         }
         catch(Exception e) {
            LOG.error("Failed to update geo ref format", e);
         }
      }

      ChartDescriptor chartDescriptor = ((ChartVSAssembly)assembly).getChartDescriptor();
      LegendsDescriptor legendsDesc = chartDescriptor.getLegendsDescriptor();
      LegendDescriptor legendDescriptor;

      AestheticRef colorRef = chartInfo.getColorField();

      if(colorRef != null) {
         legendDescriptor = legendsDesc.getColorLegendDescriptor();
         updateAestheticRefFormat(legendDescriptor, colorRef, updateAssembly, tempInfo, assembly, rvs);
      }

      AestheticRef shapeRef = chartInfo.getShapeField();

      if(shapeRef != null) {
         legendDescriptor = legendsDesc.getShapeLegendDescriptor();
         updateAestheticRefFormat(legendDescriptor, shapeRef, updateAssembly, tempInfo, assembly, rvs);
      }

      AestheticRef sizeRef = chartInfo.getSizeField();

      if(sizeRef != null) {
         legendDescriptor = legendsDesc.getSizeLegendDescriptor();
         updateAestheticRefFormat(legendDescriptor, sizeRef, updateAssembly, tempInfo, assembly, rvs);
      }

      AestheticRef textField = chartInfo.getTextField();

      if(textField != null) {
         updateTextFormat(getNameForFormat((VSChartRef) textField.getDataRef()), tempInfo, assembly, rvs, chartInfo,
                          updateAssembly);
      }

      if(chartInfo instanceof GanttVSChartInfo) {
         List<ChartAggregateRef> aestheticRefs = chartInfo.getAestheticAggregateRefs(!updateAssembly);

         for(ChartAggregateRef aRef : aestheticRefs) {
            updateMultiStyleVisualFormat(tempInfo, assembly, rvs, updateAssembly, aRef.getColorField());
            updateMultiStyleVisualFormat(tempInfo, assembly, rvs, updateAssembly, aRef.getShapeField());
            updateMultiStyleVisualFormat(tempInfo, assembly, rvs, updateAssembly, aRef.getSizeField());
            updateMultiStyleVisualFormat(tempInfo, assembly, rvs, updateAssembly, aRef.getTextField());
         }
      }

      if(!updateAssembly && chartInfo instanceof RelationChartInfo) {
         loadRelationChartFormat(rvs, tempInfo, assembly, (RelationChartInfo) chartInfo);
      }
   }

   private void loadRelationChartFormat(RuntimeViewsheet rvs, VSTemporaryInfo tempInfo,
                                        VSAssembly assembly, RelationChartInfo chartInfo)
   {
      ChartRef sourceField = chartInfo.getRTTargetField();
      changeFormat(sourceField.getTextFormat(), tempInfo, assembly, rvs,
         getNameForFormat(sourceField), false, sourceField);
      ChartRef targetField = chartInfo.getRTTargetField();
      changeFormat(targetField.getTextFormat(), tempInfo, assembly, rvs,
         getNameForFormat(targetField), false, targetField);
      AestheticRef nodeColorField = chartInfo.getNodeColorField();

      if(nodeColorField != null) {
         LegendDescriptor legendDesc = nodeColorField.getLegendDescriptor();
         updateAestheticRefFormat(legendDesc, nodeColorField, false, tempInfo, assembly, rvs);
      }

      AestheticRef nodeSizeField = chartInfo.getNodeSizeField();

      if(nodeSizeField != null) {
         LegendDescriptor legendDesc = nodeSizeField.getLegendDescriptor();
         updateAestheticRefFormat(legendDesc, nodeSizeField, false, tempInfo, assembly, rvs);
      }
   }

   private Format getAxisDefaultFormat(ViewsheetSandbox box, VSChartInfo chartInfo,
                                       String cname, String columnName)
           throws Exception
   {
      Object data = box.getData(cname);

      return columnName == null ? null : GraphFormatUtil.getDefaultFormat((VSDataSet) data,
              chartInfo, chartInfo.getChartDescriptor(), columnName);
   }

   private void updatePlotFormats(PlotDescriptor descriptor, String refName, VSTemporaryInfo tempInfo,
                                  VSAssembly assembly, RuntimeViewsheet rvs, ChartInfo chartInfo,
                                  boolean updateAssembly)
   {
      ChartBindable bindable = regionHandler.getChartBindable(chartInfo, refName);
      ChartRef aggr = chartInfo.getFieldByName(refName, false);
      bindable = chartInfo.isMultiAesthetic() ? bindable : chartInfo;
      CompositeTextFormat format = GraphFormatUtil.getTextFormat(bindable, aggr, descriptor);
      changeFormat(format, tempInfo, assembly, rvs, refName, updateAssembly, aggr);
   }

   private void updateTextFormat(String refName, VSTemporaryInfo tempInfo, VSAssembly assembly,
                                 RuntimeViewsheet rvs, ChartInfo chartInfo, boolean updateAssembly)
   {
      ChartBindable bindable = regionHandler.getChartBindable(chartInfo, refName);
      updateTextFormat(bindable, tempInfo, assembly, rvs, updateAssembly);
   }

   private void updateTextFormat(ChartBindable bindable, VSTemporaryInfo tempInfo, VSAssembly assembly,
                                 RuntimeViewsheet rvs, boolean updateAssembly)
   {
      CompositeTextFormat format = null;

      if(bindable instanceof ChartRef) {
         format = ((ChartRef) bindable).getTextFormat();
         changeFormat(format, tempInfo, assembly, rvs, getNameForFormat((ChartRef) bindable),
            updateAssembly, (ChartRef) bindable);
      }

      ChartRef chartRef = null;

      if(bindable != null) {
         AestheticRef aref = bindable.getTextField();

         if(aref != null) {
            DataRef ref = aref.getDataRef();

            if(ref instanceof ChartRef) {
               chartRef = (ChartRef) ref;
               format = chartRef.getTextFormat();
            }
         }
      }

      if(format == null || chartRef == null) {
         return;
      }

      changeFormat(format, tempInfo, assembly, rvs, getNameForFormat(chartRef), updateAssembly,
         chartRef);
   }

   private void updateMultiStyleVisualFormat(VSTemporaryInfo tempInfo, VSAssembly assembly, RuntimeViewsheet rvs,
                                             boolean updateAssembly, AestheticRef visualRef)
   {
      if(visualRef != null) {
         LegendDescriptor legendDescriptor = visualRef.getLegendDescriptor();
         updateAestheticRefFormat(legendDescriptor, visualRef, updateAssembly, tempInfo, assembly, rvs);
      }
   }

   private void updateAxisRefFormats(AxisDescriptor descriptor, ChartRef ref, boolean updateAssembly,
                                     VSTemporaryInfo tempInfo, VSAssembly assembly, RuntimeViewsheet rvs)
   {
      CompositeTextFormat format = descriptor.getAxisLabelTextFormat();

      if(format == null) {
         format = new CompositeTextFormat();
         descriptor.setAxisLabelTextFormat(format);
      }

      String name = getNameForFormat(ref);
      changeFormat(format, tempInfo, assembly,  rvs, name, updateAssembly, ref);
      format = descriptor.getColumnLabelTextFormat(ref.getFullName());

      if(format != null) {
         changeFormat(format, tempInfo, assembly, rvs, name, updateAssembly, ref);
      }
   }

   // For some formula(sum,max,min,frist,last...,should using the same format, so save them by
   // column name. others using fullname to put and get.
   private String getNameForFormat(ChartRef ref) {
      if(ref instanceof VSChartDimensionRef) {
         return ref.getFullName();
      }

      VSChartAggregateRef aggr = (VSChartAggregateRef)ref;

      if(sameTypeFormula(aggr.getFormula())) {
         return ref.getName();
      }

      return ref.getFullName();
   }

   private boolean sameTypeFormula(AggregateFormula formula) {
      String fname = formula.getFormulaName();

      for(AggregateFormula aggregateFormula : SAME_TYPE_FORMULA) {
         if(aggregateFormula.getFormulaName().equals(fname)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Copy format to/from formatMap.
    * @param updateAssembly true to copy format from formatMap to user format, false to copy
    */
   private void changeFormat(CompositeTextFormat format, VSTemporaryInfo tempInfo,
                             VSAssembly assembly, RuntimeViewsheet rvs, String refName,
                             boolean updateAssembly, ChartRef ref)
   {
      VSFormat format2 = tempInfo.getUserFormat(refName);
      VSFormat fieldDefaultFormat = tempInfo.getDefaultFormat(refName);
      final XFormatInfo defaultFormat = format.getDefaultFormat().getFormat();

      if(XSchema.isNumericType(ref.getDataType())) {
         String numberFormatSpec = ExtendedDecimalFormat.AUTO_FORMAT;

         try {
            final VSDataSet data = (VSDataSet) rvs.getViewsheetSandbox()
               .getData(assembly.getAbsoluteName());

            if(data != null) {
               final Format dataFormat = data.getFormat(ref.getFullName(), 0);

               if(dataFormat instanceof DecimalFormat) {
                  numberFormatSpec = ((DecimalFormat) dataFormat).toPattern();
               }
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to get chart data", ex);
         }

         defaultFormat.setFormat(TableFormat.DECIMAL_FORMAT);
         defaultFormat.setFormatSpec(numberFormatSpec);
      }

      if(updateAssembly && format2 != null &&
         (format2.isFormatDefined() || format2.isFormatValueDefined()))
      {
         XFormatInfo userFormat = format.getUserDefinedFormat().getFormat();
         userFormat.setFormat(format2.getFormatValue());
         userFormat.setFormatSpec(format2.getFormatExtentValue());
      }
      else if(!updateAssembly) {
         XFormatInfo fmtInfo = format.getUserDefinedFormat().getFormat();

         if(fmtInfo.getFormat() != null && !VSWizardConstants.NONE.equals(fmtInfo.getFormat())) {
            if(format2 == null) {
               format2 = new VSFormat();
               tempInfo.setFormat(refName, format2);
            }

            format2.setFormatValue(fmtInfo.getFormat());
         }

         if(fmtInfo.getFormatSpec() != null) {
            format2.setFormatExtentValue(fmtInfo.getFormatSpec());
         }

         if(fieldDefaultFormat == null) {
            fieldDefaultFormat = new VSFormat();
            tempInfo.setDefaultFormat(refName, fieldDefaultFormat);
         }

         fmtInfo = format.getDefaultFormat().getFormat();

         if(fmtInfo.getFormat() != null && !VSWizardConstants.NONE.equals(fmtInfo.getFormat())) {
            fieldDefaultFormat.setFormatValue(fmtInfo.getFormat());
         }

         if(fmtInfo.getFormatSpec() != null) {
            fieldDefaultFormat.setFormatExtentValue(fmtInfo.getFormatSpec());
         }
      }
   }

   private void updateAestheticRefFormat(LegendDescriptor descriptor, AestheticRef ref,
                                         boolean updateAssembly, VSTemporaryInfo tempInfo,
                                         VSAssembly assembly, RuntimeViewsheet rvs)
   {
      CompositeTextFormat format = descriptor.getContentTextFormat();

      if(format == null) {
         format = (CompositeTextFormat) descriptor.getContentTextFormat().clone();
         descriptor.setContentTextFormat(format);
      }

      VSChartRef chartRef = (VSChartRef) ref.getDataRef();
      changeFormat(format, tempInfo, assembly, rvs, getNameForFormat(chartRef), updateAssembly, chartRef);
   }

   private void updateOutputFormat(VSTemporaryInfo tempInfo, OutputVSAssembly assembly,
                                   boolean update)
   {
      TableDataPath dataPath = new TableDataPath(-1, TableDataPath.OBJECT);
      ScalarBindingInfo scalarBindingInfo = assembly.getScalarBindingInfo();

      if(scalarBindingInfo != null) {
         DataRef column = scalarBindingInfo.getColumn();
         String fullName = getAggColumnFullName(column.getAttribute(), scalarBindingInfo);
         String fieldName = sameTypeFormula(scalarBindingInfo.getAggregateFormula()) ?
            column.getAttribute() : fullName;
         updateAssemblyFormat(tempInfo, assembly, assembly.getFormatInfo(), dataPath,
                              fieldName, null, update);
      }
   }

   private void updateSelectionFormat(VSTemporaryInfo tempInfo,
                                      SelectionVSAssembly assembly, boolean update)
   {
      DataRef[] dataRefs = assembly.getDataRefs();

      for(int i = 0; i < dataRefs.length; i++) {
         boolean last = i == dataRefs.length - 1;
         TableDataPath dataPath = getSelectionDataPath(assembly, i, last);
         updateAssemblyFormat(tempInfo, assembly, assembly.getFormatInfo(), dataPath,
            dataRefs[i].getName(), null, update);
      }
   }

   private void updateTableDataFormat(RuntimeViewsheet rvs, VSTemporaryInfo tempInfo,
                                      TableDataVSAssembly assembly, boolean update)
      throws Exception
   {
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox();
      VSTableLens lens = sandbox.getVSTableLens(assembly.getName(), false);

      if(assembly instanceof CrosstabVSAssembly) {
         VSCrosstabInfo crosstabInfo = ((CrosstabVSAssembly) assembly).getVSCrosstabInfo();
         FormatInfo formatInfo = assembly.getFormatInfo();

         VSDataRef[] rows = Arrays.stream(crosstabInfo.getPeriodRuntimeRowHeaders())
            .map(VSDataRef.class::cast)
            .toArray(VSDataRef[]::new);
         VSDataRef[] cols = Arrays.stream(crosstabInfo.getRuntimeColHeaders())
            .map(VSDataRef.class::cast)
            .toArray(VSDataRef[]::new);
         // fix Bug #38386, full name the runtime aggr is wrong(like Sum(Sum(price))),
         // this change was done in VSAggregate.update function which is an very old logic,
         // changing the logic is incertitude, so using the aggrs instead runtime aggrs2,
         // and since we don't support dynamic binding, this change should be ok.
         VSDataRef[] aggs = Arrays.stream(crosstabInfo.getAggregates())
            .map(VSDataRef.class::cast)
            .toArray(VSDataRef[]::new);

         for(VSDataRef row : rows) {
            List<String> paths = getDataPath(rows, row.getFullName());
            updateTableDataFormatByPaths(tempInfo, formatInfo, row, paths, lens, update);
         }

         for(VSDataRef col : cols) {
            List<String> paths = getDataPath(cols, col.getFullName());
            updateTableDataFormatByPaths(tempInfo, formatInfo, col, paths, lens, update);
         }

         Map<String, Integer> aggMap = new HashMap<>();

         for(VSDataRef agg : aggs) {
            String aggName = agg.getFullName();

            aggMap.compute(aggName, (key, oldValue) -> {
               if(oldValue != null) {
                  return oldValue + 1;
               }
               else {
                  return 0;
               }
            });

            aggName = Util.getDupHeader(aggName, aggMap.get(aggName)).toString();

            List<String> paths = GraphUtil.getAggregatesPath(rows, cols, aggName);
            updateTableDataFormatByPaths(tempInfo, formatInfo, agg, paths, lens, update);
         }
      }
      else if(assembly instanceof TableVSAssembly) {
         TableVSAssembly table = (TableVSAssembly) assembly;

         for(DataRef ref : table.getBindingRefs()) {
            List<String> paths = new ArrayList<>();
            paths.add(ref.getName());
            updateTableDataFormatByPaths(tempInfo, assembly.getFormatInfo(), ref, paths, lens, update);
         }
      }
   }

   private void updateTableDataFormatByPaths(VSTemporaryInfo tempInfo,
                                             FormatInfo formatInfo, DataRef ref,
                                             List<String> paths, VSTableLens lens,
                                             boolean update)
   {
      TableDataPath dataPath = new TableDataPath();
      dataPath.setDataType(ref.getDataType());
      dataPath.setPath(paths.toArray(new String[0]));
      TableDataPath dataPathTitle = (TableDataPath)dataPath.clone();
      String field = ref.getName();
      boolean hasDimensions = paths.size() > 1;

      if(ref instanceof ColumnRef) {
         dataPath.setType(TableDataPath.DETAIL);
         updateAssemblyFormat(tempInfo, null, formatInfo, dataPath, field, lens, update);

         dataPathTitle.setType(TableDataPath.HEADER);
         updateAssemblyFormat(tempInfo, null, formatInfo, dataPathTitle,
            field + VSTemporaryInfo.HEADER_FORMAT_STRING, lens, update);

         return;
      }
      else if(ref instanceof XDimensionRef) {
         dataPath.setType(TableDataPath.GROUP_HEADER);
         field = ((XDimensionRef) ref).getFullName();
      }
      else if(ref instanceof XAggregateRef) {
         // if binding is only measures, crosstab will do grand total.
         dataPath.setType(hasDimensions ? TableDataPath.SUMMARY : TableDataPath.GRAND_TOTAL);
         VSAggregateRef aggr = (VSAggregateRef) ref;
         field = sameTypeFormula(aggr.getFormula()) ?
            ref.getName() : ((XAggregateRef) ref).getFullName();
      }

      updateAssemblyFormat(tempInfo, null, formatInfo, dataPath, field, lens, update);
   }

   private void updateAssemblyFormat(VSTemporaryInfo tempInfo, VSAssembly assembly,
                                     FormatInfo formatInfo, TableDataPath dataPath,
                                     String field, VSTableLens lens, boolean update)
   {
      VSCompositeFormat format = formatInfo.getFormat(dataPath, false);
      VSFormat formatObj = tempInfo.getUserFormat(field);

      if(format == null) {
         return;
      }

      if(update) {
         if(format.getUserDefinedFormat() != null && formatObj != null) {
            // font is set on text and should not be carried to other components (e.g. table)
            if(format.getUserDefinedFormat().isFontValueDefined()) {
               formatObj = (VSFormat) formatObj.clone();
               formatObj.setFontValue(format.getFontValue());
            }
         }

         if(format.getFormat() != null) {
            if(formatObj == null) {
               formatObj = new VSFormat();
               tempInfo.setFormat(field, formatObj);
            }

            if(!formatObj.isFormatValueDefined()) {
               if(formatObj.getFormat() == null) {
                  formatObj.setFormat(format.getFormat());
               }

               if(formatObj.getFormatExtent() == null) {
                  formatObj.setFormatExtent(format.getFormatExtent());
               }
            }
         }

         if((assembly instanceof SelectionListVSAssembly ||
            assembly instanceof SelectionTreeVSAssembly ||
            assembly instanceof GaugeVSAssembly) && formatObj != null)
         {
            formatObj.setBorderColors(null);
            formatObj.setBordersValue(new Insets(0, 0, 0, 0));
         }
         else if(formatObj != null) {
            Color color = new Color(218, 218, 218);
            formatObj.setBorderColors(new BorderColors(color, color, color, color));
            formatObj.setBordersValue(new Insets(4097, 4097, 4097, 4097));
         }

         format.setUserDefinedFormat(formatObj);
         formatInfo.setFormat(dataPath, format);
      }
      else {
         if(formatObj != null) {
            formatObj.setFormatValue(format.getFormat());
            formatObj.setFormatExtentValue(format.getFormatExtent());
         }
         else {
            format = format.clone();
            format.getUserDefinedFormat().setFontValue(null, false);
            VSFormat fmt = format.getUserDefinedFormat();
            setDefaultFormat(format, lens, dataPath);
            fmt = fmt.isDefined() ? fmt : format.getDefaultFormat();

            // don't need to share calendar's font.
            if(assembly instanceof CalendarVSAssembly) {
               fmt.setFontValue(null, false);
            }

            tempInfo.setFormat(field, fmt);
         }
      }
   }

   private void setDefaultFormat(VSCompositeFormat compositeFormat, VSTableLens lens, TableDataPath path) {
      VSFormat defaultFormat = compositeFormat.getDefaultFormat();

      if(lens != null) {
         Format fmt = lens.getDefaultFormat(path);

         if(fmt != null && defaultFormat.getFormat() == null) {
            String format = null;
            String format_spec = null;

            if(fmt instanceof SimpleDateFormat) {
               format = XConstants.DATE_FORMAT;
               format_spec = ((SimpleDateFormat) fmt).toPattern();
            }
            else if(fmt instanceof DecimalFormat) {
               format = XConstants.DECIMAL_FORMAT;
               format_spec = ((DecimalFormat) fmt).toPattern();
            }
            else if(fmt instanceof java.text.MessageFormat) {
               format = XConstants.MESSAGE_FORMAT;
               format_spec = ((java.text.MessageFormat) fmt).toPattern();
            }
            else if(fmt instanceof MessageFormat) {
               format = XConstants.MESSAGE_FORMAT;
               format_spec = ((MessageFormat) fmt).toPattern();
            }

            defaultFormat.setFormat(format, false);
            defaultFormat.setFormatExtent(format_spec, false);
         }
      }
   }

   private String getAggColumnFullName(String name, ScalarBindingInfo scalarBindingInfo) {
      StringBuilder sb = new StringBuilder();
      AggregateFormula formula = scalarBindingInfo.getAggregateFormula();
      String fname = formula == null ? null : formula.getFormulaName();

      if(fname != null && !VSWizardConstants.NONE.equals(fname)) {
         sb.append(fname);
         sb.append("(");
         sb.append(name);

         if(formula.isTwoColumns()) {
            sb.append(", ");
            sb.append(scalarBindingInfo.getColumn2Value());
         }

         if(formula.hasN()) {
            sb.append(", ");
            sb.append(scalarBindingInfo.getNValue());
         }

         sb.append(")");
      }
      else {
         sb.append(name);
      }

      return sb.toString();
   }

   /**
    *  Keep Aesthetic of chart, it should been executed before changeChartData, because it will fix
    *  Aesthetic according to chart type.
    */
   private void keepAesthetic(ChartVSAssembly fromChart, ChartVSAssembly targetChart) {
      VSChartInfo fromInfo = fromChart.getVSChartInfo();
      VSChartInfo targetInfo = targetChart.getVSChartInfo();
      syncAestheticFrame(fromInfo.getColorField(), targetInfo.getColorField());
      syncAestheticFrame(fromInfo.getShapeField(), targetInfo.getShapeField());
      syncAestheticFrame(fromInfo.getSizeField(), targetInfo.getSizeField());
      syncAestheticFrame(fromInfo.getTextField(), targetInfo.getTextField());
   }

   private void syncAestheticFrame(AestheticRef fromRef, AestheticRef targetRef) {
      if(fromRef == null || targetRef == null || fromRef.getDataRef() == null ||
         targetRef.getDataRef() == null)
      {
         return;
      }

      if(fromRef.getDataRef().getClass() == targetRef.getDataRef().getClass()) {
         targetRef.setVisualFrameWrapper(fromRef.getVisualFrameWrapper());
      }
   }

   public void updateTempChartAssembly(ChartBindingModel model, ChartVSAssembly assembly) {
      if(assembly == null) {
         return;
      }

      VSChartInfo oldInfo = assembly.getVSChartInfo();

      if(oldInfo == null || model == null) {
         return;
      }

      VSChartInfo newInfo = oldInfo.clone();
      newInfo.removeXFields();
      newInfo.removeYFields();

      for(ChartRefModel refModel : model.getXFields()) {
         newInfo.addXField(chartService.pasteChartRef(newInfo, refModel));
      }

      for(ChartRefModel refModel : model.getYFields()) {
         newInfo.addYField(chartService.pasteChartRef(newInfo, refModel));
      }

      newInfo.setRTXFields(newInfo.getXFields());
      newInfo.setRTYFields(newInfo.getYFields());
      assembly.setVSChartInfo(newInfo);
   }

   /**
    * sync user defined format when change level or formula
    */
   private void syncFormat(VSTemporaryInfo vsTemporaryInfo, String oname, String nname) {
      VSFormat userFormat = vsTemporaryInfo.getUserFormat(oname);

      if(userFormat != null) {
         vsTemporaryInfo.setFormat(nname, userFormat);
         vsTemporaryInfo.removeFormat(oname);
      }
   }

   /**
    * Set a fixed size for wizard preview assembly.
    */
   public void fixTempAssemblySize(VSAssemblyInfo info, RuntimeViewsheet rvs) {
      VSTemporaryInfo temporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);

      if(temporaryInfo.getPreviewPaneSize() == null) {
         return;
      }

      if(info instanceof ChartVSAssemblyInfo || info instanceof TableVSAssemblyInfo ||
         info instanceof CrosstabVSAssemblyInfo)
      {
         info.setPixelSize(temporaryInfo.getPreviewPaneSize());
      }
      else if(info instanceof GaugeVSAssemblyInfo) {
         info.setPixelSize(new Dimension(300, 300));
      }
      else if(info instanceof TextVSAssemblyInfo) {
         info.setPixelSize(new Dimension(120, 33));
      }
      else if(info instanceof SelectionListVSAssemblyInfo) {
         info.setPixelSize(new Dimension(180, 250));
      }
      else if(info instanceof SelectionTreeVSAssemblyInfo) {
         info.setPixelSize(new Dimension(180, 250));
      }
      else if(info instanceof TimeSliderVSAssemblyInfo) {
         info.setPixelSize(new Dimension(350, 50));
      }
      else if(info instanceof CalendarVSAssemblyInfo) {
         info.setPixelSize(new Dimension(450, 250));
      }
   }

   public boolean hasDescriptionAssembly(VSAssembly assembly) {
      return assembly.getVSAssemblyInfo() instanceof DescriptionableAssemblyInfo;
   }

   /**
    * When adding chartRef to temporaryChart, some properties may not be needed,
    * so do some purification here.
    * @param ref chartRef will added to temporary chart
    */
   public void purifyChartRef(ChartRef ref) {
      if(ref instanceof VSChartAggregateRef) {
         ((VSChartAggregateRef) ref).setSecondaryY(false);
         ref.setAxisDescriptor(new AxisDescriptor());
      }

      if(ref instanceof VSChartDimensionRef) {
         ref.setAxisDescriptor(new AxisDescriptor());
      }
   }

   private Catalog catalog = Catalog.getCatalog();

   private final VSChartHandler vsChartHandler;
   private final AssetRepository assetRepository;
   private final VSBindingService bindingFactory;
   private final ChartRegionHandler regionHandler;
   private final SyncInfoHandler syncInfoHandler;
   private final ViewsheetService viewsheetService;
   private final VSChartDataHandler chartDataHandler;
   private final PlaceholderService placeholderService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ChartRefModelFactoryService chartService;
   private final DataRefModelFactoryService dataRefService;
   private final VSWizardTemporaryInfoService temporaryInfoService;

   private static final List<AggregateFormula> SAME_TYPE_FORMULA = new ArrayList<>();

   static {
      SAME_TYPE_FORMULA.add(AggregateFormula.MAX);
      SAME_TYPE_FORMULA.add(AggregateFormula.MIN);
      SAME_TYPE_FORMULA.add(AggregateFormula.AVG);
      SAME_TYPE_FORMULA.add(AggregateFormula.SUM);
      SAME_TYPE_FORMULA.add(AggregateFormula.FIRST);
      SAME_TYPE_FORMULA.add(AggregateFormula.LAST);
      SAME_TYPE_FORMULA.add(AggregateFormula.MEDIAN);
      SAME_TYPE_FORMULA.add(AggregateFormula.MODE);
      SAME_TYPE_FORMULA.add(AggregateFormula.NTH_LARGEST);
      SAME_TYPE_FORMULA.add(AggregateFormula.NTH_SMALLEST);
      SAME_TYPE_FORMULA.add(AggregateFormula.NTH_MOST_FREQUENT);
      SAME_TYPE_FORMULA.add(AggregateFormula.SUMSQ);
      SAME_TYPE_FORMULA.add(AggregateFormula.WEIGHTED_AVG);
      SAME_TYPE_FORMULA.add(AggregateFormula.POPULATION_STANDARD_DEVIATION);
      SAME_TYPE_FORMULA.add(AggregateFormula.POPULATION_VARIANCE);
      SAME_TYPE_FORMULA.add(AggregateFormula.PTH_PERCENTILE);
      SAME_TYPE_FORMULA.add(AggregateFormula.STANDARD_DEVIATION);
      SAME_TYPE_FORMULA.add(AggregateFormula.VARIANCE);
      SAME_TYPE_FORMULA.add(AggregateFormula.COVARIANCE);
      SAME_TYPE_FORMULA.add(AggregateFormula.CORRELATION);
   }

   private static final Logger LOG = LoggerFactory.getLogger(VSWizardBindingHandler.class);
}
