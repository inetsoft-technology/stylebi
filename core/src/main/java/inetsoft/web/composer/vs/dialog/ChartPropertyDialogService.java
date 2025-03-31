/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.graph.GraphTarget;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;
import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;

@Service
@ClusterProxy
public class ChartPropertyDialogService {

   public ChartPropertyDialogService(
      VSObjectPropertyService vsObjectPropertyService,
      ChartPropertyService chartPropertyService,
      VSChartHandler vsChartHandler,
      VSDialogService dialogService,
      ViewsheetService viewsheetService,
      VSBindingService vsBindingService,
      VSAssemblyInfoHandler assemblyInfoHandler,
      VSTrapService trapService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.chartPropertyService = chartPropertyService;
      this.vsChartHandler = vsChartHandler;
      this.dialogService = dialogService;
      this.viewsheetService = viewsheetService;
      this.vsBindingService = vsBindingService;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.trapService = trapService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ChartPropertyDialogModel getChartPropertyDialogModel(@ClusterProxyKey String runtimeId,
                                                               String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      ChartVSAssembly chartAssembly;
      ChartVSAssemblyInfo chartAssemblyInfo;
      VSChartInfo vsChartInfo;
      ChartDescriptor chartDescriptor;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         chartAssembly = (ChartVSAssembly) vs.getAssembly(objectId);
         chartAssemblyInfo = (ChartVSAssemblyInfo) chartAssembly.getVSAssemblyInfo();
         vsChartInfo = chartAssemblyInfo.getVSChartInfo();
         vsChartInfo = vsChartInfo == null ? new VSChartInfo() : vsChartInfo;
         chartDescriptor = chartAssemblyInfo.getChartDescriptor();
      }
      catch(Exception e) {
         throw e;
      }

      ChartPropertyDialogModel result = new ChartPropertyDialogModel();
      ChartGeneralPaneModel chartGeneralPaneModel = result.getChartGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel =
         chartGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel =
         generalPropPaneModel.getBasicGeneralPaneModel();
      TipPaneModel tipPaneModel = chartGeneralPaneModel.getTipPaneModel();
      TipCustomizeDialogModel tipCustomizeDialogModel =
         tipPaneModel.getTipCustomizeDialogModel();
      SizePositionPaneModel sizePositionPaneModel =
         chartGeneralPaneModel.getSizePositionPaneModel();
      ChartLinePaneModel chartLinePaneModel = result.getChartLinePaneModel();

      if(chartLinePaneModel == null && chartDescriptor != null) {
         ChartLinePaneModel linePaneModel =
            new ChartLinePaneModel(vsChartInfo, chartDescriptor.getPlotDescriptor());

         if(DateComparisonUtil.appliedDateComparison(chartAssemblyInfo)) {
            linePaneModel.setProjectForwardEnabled(vsChartInfo.canProjectForward(true));
         }

         result.setChartLinePaneModel(linePaneModel);
      }

      HierarchyPropertyPaneModel hierarchyPropertyPaneModel =
         result.getHierarchyPropertyPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel =
         VSAssemblyScriptPaneModel.builder();
      TitlePropPaneModel titlePropPaneModel = chartGeneralPaneModel.getTitlePropPaneModel();
      PaddingPaneModel paddingPaneModel = chartGeneralPaneModel.getPaddingPaneModel();

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(chartAssemblyInfo.getEnabledValue());

      basicGeneralPaneModel.setName(chartAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setNameEditable(!chartAssemblyInfo.isWizardTemporary());
      basicGeneralPaneModel.setPrimary(chartAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(chartAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, chartAssemblyInfo.getAbsoluteName()));

      paddingPaneModel.setTop(chartAssemblyInfo.getPadding().top);
      paddingPaneModel.setLeft(chartAssemblyInfo.getPadding().left);
      paddingPaneModel.setBottom(chartAssemblyInfo.getPadding().bottom);
      paddingPaneModel.setRight(chartAssemblyInfo.getPadding().right);

      titlePropPaneModel.setVisible(chartAssemblyInfo.getTitleVisibleValue());
      titlePropPaneModel.setTitle(chartAssemblyInfo.getTitleValue());

      tipPaneModel.setChart(true);
      tipPaneModel.setTipOption(
         chartAssemblyInfo.getTipOptionValue() == TipVSAssemblyInfo.VIEWTIP_OPTION);
      tipPaneModel.setTipView(chartAssemblyInfo.getTipViewValue());
      tipPaneModel.setAlpha(chartAssemblyInfo.getAlphaValue() == null ?
                               "100" : chartAssemblyInfo.getAlphaValue());
      String[] flyoverViews = chartAssemblyInfo.getFlyoverViewsValue();
      tipPaneModel.setFlyOverViews(flyoverViews == null ? new String[0] : flyoverViews);
      tipPaneModel.setFlyOnClick(Boolean.valueOf(chartAssemblyInfo.getFlyOnClickValue()));
      tipPaneModel.setPopComponents(
         this.vsObjectPropertyService.getSupportedTablePopComponents(
            rvs, chartAssemblyInfo.getAbsoluteName(), false));
      tipPaneModel.setFlyoverComponents(
         this.vsObjectPropertyService.getSupportedTablePopComponents(
            rvs, chartAssemblyInfo.getAbsoluteName(), true));
      String srctbl = chartAssemblyInfo.getTableName();
      tipPaneModel.setDataViewEnabled(srctbl != null && !VSUtil.isVSAssemblyBinding(srctbl));

      boolean tipViewInPopComponents = false;
      //prevent broken tipView after passing invalid string
      for(String popComp : tipPaneModel.getPopComponents()) {

         if(Tool.equals(popComp, tipPaneModel.getTipView())) {
            tipViewInPopComponents = true;
            break;
         }
      }

      if(!tipViewInPopComponents) {
         tipPaneModel.setTipView(null);
      }

      Point pos = dialogService.getAssemblyPosition(chartAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(chartAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setTitleHeight(chartAssemblyInfo.getTitleHeightValue());
      sizePositionPaneModel.setContainer(chartAssembly.getContainer() != null);
      String[] dataRefList = getDataRefList(vsChartInfo, VSUtil.getCubeType(chartAssembly));
      tipCustomizeDialogModel.setDataRefList(dataRefList);
      tipCustomizeDialogModel.setAvailableTipValues(VSUtil.getAvailableTipValues(dataRefList));

      String customString = vsChartInfo.getToolTipValue();

      if(vsChartInfo.isTooltipVisible()) {
         tipCustomizeDialogModel.setCustomRB(customString != null && !customString.isEmpty()
                                                ? TipCustomizeDialogModel.TipFormat.CUSTOM
                                                : TipCustomizeDialogModel.TipFormat.DEFAULT);
      }
      else {
         tipCustomizeDialogModel.setCustomRB(TipCustomizeDialogModel.TipFormat.NONE);
      }

      tipCustomizeDialogModel.setChart(true);
      tipCustomizeDialogModel.setCustomTip(vsChartInfo.getCustomTooltip());

      if(vsChartInfo.getCombinedToolTipValue()) {
         tipCustomizeDialogModel.setCombinedTip(true);
      }

      int chartStyle = vsChartInfo.getChartStyle();

      tipCustomizeDialogModel.setLineChart(!vsChartInfo.isMultiStyles() &&
                                              (chartStyle == GraphTypes.CHART_LINE ||
                                                 chartStyle == GraphTypes.CHART_LINE_STACK ||
                                                 chartStyle == GraphTypes.CHART_STEP ||
                                                 chartStyle == GraphTypes.CHART_STEP_STACK ||
                                                 chartStyle == GraphTypes.CHART_JUMP ||
                                                 chartStyle == GraphTypes.CHART_STEP_AREA ||
                                                 chartStyle == GraphTypes.CHART_STEP_AREA_STACK ||
                                                 chartStyle == GraphTypes.CHART_AREA ||
                                                 chartStyle == GraphTypes.CHART_AREA_STACK));

      ChartAdvancedPaneModel chartAdvancedPaneModel = result.getChartAdvancedPaneModel();

      if(chartAdvancedPaneModel == null) {
         chartAdvancedPaneModel = new ChartAdvancedPaneModel(chartAssemblyInfo);
      }

      chartAdvancedPaneModel.setGlossyEffectSupported(
         this.chartPropertyService.isSupported(vsChartInfo, "effectEnabled", false));
      chartAdvancedPaneModel.setSparklineSupported(
         this.chartPropertyService.isSupported(vsChartInfo, "isSparklineSupported", true));

      ChartTargetLinesPaneModel linesModel = new ChartTargetLinesPaneModel();
      linesModel.setMapInfo(chartAssemblyInfo.getVSChartInfo() instanceof MapInfo);
      linesModel.setSupportsTarget(this.chartPropertyService.
                                      supportsTarget(vsChartInfo, ChartPropertyService.NO_TARGET_STYLES));
      boolean appliedDateComparison = DateComparisonUtil.appliedDateComparison(chartAssemblyInfo);
      linesModel.setChartTargets(this.chartPropertyService.
                                    getTargetInfoList(chartDescriptor, vsChartInfo, appliedDateComparison || hasDynamic(vsChartInfo)));
      linesModel.setNewTargetInfo(this.chartPropertyService.
                                     getTargetInfo(vsChartInfo, new GraphTarget(), appliedDateComparison || hasDynamic(vsChartInfo)));
      linesModel.setAvailableFields(this.chartPropertyService.getMeasures(vsChartInfo,
                                                                          appliedDateComparison || hasDynamic(vsChartInfo)));
      chartAdvancedPaneModel.setChartTargetLinesPaneModel(linesModel);
      result.setChartAdvancedPaneModel(chartAdvancedPaneModel);

      SourceInfo sourceInfo = chartAssemblyInfo.getSourceInfo();
      String tableName = sourceInfo == null ? "" : sourceInfo.getSource();
      tableName = tableName == null ? "" : tableName;

      hierarchyPropertyPaneModel.setCube(tableName.contains(Assembly.CUBE_VS));

      if(tableName.length() > 0 && !tableName.contains(Assembly.CUBE_VS)) {
         hierarchyPropertyPaneModel.setColumnList(
            this.vsObjectPropertyService.getHierarchyColumnList(
               chartAssemblyInfo.getAbsoluteName(), tableName, rvs, principal));
      }
      else {
         hierarchyPropertyPaneModel.setColumnList(new OutputColumnRefModel[0]);
      }

      List<VSDimensionModel> vsDimensionModels = new ArrayList<>();

      XCube cube = chartAssemblyInfo.getXCube();

      if(cube instanceof VSCube) {
         VSCube vsCube = (VSCube) chartAssemblyInfo.getXCube();

         if(vsCube != null) {
            Enumeration dims = vsCube.getDimensions();

            while(dims.hasMoreElements()) {
               VSDimension dim = (VSDimension) dims.nextElement();
               vsDimensionModels.add(this.vsObjectPropertyService.convertVSDimensionToModel(dim));
            }
         }
      }

      hierarchyPropertyPaneModel.setDimensions(vsDimensionModels.toArray(new VSDimensionModel[0]));
      hierarchyPropertyPaneModel.setGrayedOutFields(assemblyInfoHandler.getGrayedOutFields(rvs));

      vsAssemblyScriptPaneModel.scriptEnabled(chartAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(chartAssemblyInfo.getScript() == null ?
                                              "" : chartAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkVSTrap(@ClusterProxyKey String runtimeId,
                                       ChartPropertyDialogModel value, String objectId,
                                       Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ChartVSAssembly chart = (ChartVSAssembly) rvs.getViewsheet().getAssembly(objectId);

      if(chart == null) {
         return VSTableTrapModel.builder()
            .showTrap(false)
            .build();
      }

      VSAssemblyInfo oldAssemblyInfo = (VSAssemblyInfo) Tool.clone(chart.getVSAssemblyInfo());
      ChartVSAssemblyInfo newAssemblyInfo = (ChartVSAssemblyInfo)
         Tool.clone(chart.getVSAssemblyInfo());

      HierarchyPropertyPaneModel hierarchyPropertyPaneModel = value.getHierarchyPropertyPaneModel();
      setCube(newAssemblyInfo, hierarchyPropertyPaneModel);

      rvs.getViewsheet().getAssembly(objectId).setVSAssemblyInfo(newAssemblyInfo);
      VSTableTrapModel trap = trapService.checkTrap(rvs, oldAssemblyInfo, newAssemblyInfo);
      rvs.getViewsheet().getAssembly(objectId).setVSAssemblyInfo(oldAssemblyInfo);

      return trap;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setChartPropertyModel(@ClusterProxyKey String runtimeId, String objectId,
                                     ChartPropertyDialogModel value, String linkUri,
                                     Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet viewsheet;
      ChartVSAssembly chartAssembly;
      ChartVSAssemblyInfo assemblyInfo;
      VSChartInfo vsChartInfo;
      ChartDescriptor chartDescriptor;

      try {
         viewsheet = viewsheetService.getViewsheet(runtimeId, principal);
         chartAssembly = (ChartVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         assemblyInfo = (ChartVSAssemblyInfo) Tool.clone(chartAssembly.getVSAssemblyInfo());
         vsChartInfo = assemblyInfo.getVSChartInfo();
         vsChartInfo = vsChartInfo == null ? new VSChartInfo() : vsChartInfo;
         chartDescriptor = assemblyInfo.getChartDescriptor();

         // 1. If only change sort other last, should clear all static values reassign.
         // 2. If change is rank per group, should clear all static values for color field.
         if(chartDescriptor != null && value != null && value.getChartAdvancedPaneModel() != null &&
            (chartDescriptor.isSortOthersLast() !=
               value.getChartAdvancedPaneModel().isSortOthersLast() ||
               chartDescriptor.isRankPerGroup() !=
                  value.getChartAdvancedPaneModel().isRankPerGroup()))
         {
            chartAssembly.getViewsheet().clearSharedFrames();
            VSChartHandler.clearColorFrame(vsChartInfo, false, null);
         }
      }
      catch(Exception e) {
         throw e;
      }

      ChartGeneralPaneModel chartGeneralPaneModel = value.getChartGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel = chartGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      TipPaneModel tipPaneModel = chartGeneralPaneModel.getTipPaneModel();
      TipCustomizeDialogModel tipModel = tipPaneModel.getTipCustomizeDialogModel();
      SizePositionPaneModel sizePositionPaneModel =
         chartGeneralPaneModel.getSizePositionPaneModel();
      ChartLinePaneModel chartLinePaneModel = value.getChartLinePaneModel();
      chartLinePaneModel.updateChartLinePaneModel(vsChartInfo, chartDescriptor.getPlotDescriptor());
      HierarchyPropertyPaneModel hierarchyPropertyPaneModel = value.getHierarchyPropertyPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();
      TitlePropPaneModel titlePropPaneModel = chartGeneralPaneModel.getTitlePropPaneModel();
      PaddingPaneModel paddingPaneModel = chartGeneralPaneModel.getPaddingPaneModel();

      assemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());

      assemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      assemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());

      assemblyInfo.setTitleVisibleValue(titlePropPaneModel.isVisible());
      assemblyInfo.setTitleValue(titlePropPaneModel.getTitle());
      assemblyInfo.setPadding(new Insets(paddingPaneModel.getTop(),
                                         paddingPaneModel.getLeft(),
                                         paddingPaneModel.getBottom(),
                                         paddingPaneModel.getRight()));

      if(tipPaneModel.isTipOption()) {
         assemblyInfo.setTipOptionValue(TipVSAssemblyInfo.VIEWTIP_OPTION);
         String str = tipPaneModel.getAlpha();
         assemblyInfo.setAlphaValue(str != null && str.length() > 0 ? str : null);

         if(tipPaneModel.getTipView() != null && !"null".equals(tipPaneModel.getTipView())) {
            assemblyInfo.setTipViewValue(tipPaneModel.getTipView());
         }
         else {
            assemblyInfo.setTipViewValue(null);
         }
      }
      else {
         assemblyInfo.setTipOptionValue(TipVSAssemblyInfo.TOOLTIP_OPTION);
         assemblyInfo.setTipViewValue(null);
      }

      if(!viewsheet.isViewer()) {
         dialogService.setAssemblySize(assemblyInfo, sizePositionPaneModel);
         dialogService.setAssemblyPosition(assemblyInfo, sizePositionPaneModel);
         assemblyInfo.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());
      }

      String[] flyovers = VSUtil.getValidFlyovers(tipPaneModel.getFlyOverViews(),
                                                  viewsheet.getViewsheet());
      assemblyInfo.setFlyoverViewsValue(flyovers);
      assemblyInfo.setFlyOnClickValue(tipPaneModel.isFlyOnClick() + "");
      vsChartInfo.setTooltipVisible(true);

      if(tipModel.getCustomRB() == TipCustomizeDialogModel.TipFormat.NONE) {
         vsChartInfo.setTooltipVisible(false);
      }
      else if(tipModel.getCustomRB() == TipCustomizeDialogModel.TipFormat.CUSTOM) {
         String customTip = tipModel.getCustomTip();
         customTip = (customTip == null || customTip.isEmpty()) ? null : customTip;
         vsChartInfo.setToolTipValue(customTip);
         vsChartInfo.setCombinedToolTipValue(false);
      }
      else {
         vsChartInfo.setToolTipValue(null);
         vsChartInfo.setCombinedToolTipValue(tipModel.isCombinedTip());
      }

      ChartAdvancedPaneModel advancePane = value.getChartAdvancedPaneModel();

      if(advancePane.isDateComparisonEnabled() != assemblyInfo.isDateComparisonEnabled() &&
         !assemblyInfo.isDateComparisonEnabled() &&
         !DateComparisonUtil.supportDateComparison(vsChartInfo, false))
      {
         UserMessage msg = new UserMessage(Catalog.getCatalog().getString("date.comparison.enable"),
                                           ConfirmException.INFO);
         commandDispatcher.sendCommand(MessageCommand.fromUserMessage(msg));
      }

      advancePane.updateChartAdvancedPaneModel(assemblyInfo);

      this.chartPropertyService.updateAllTargets(chartDescriptor,
                                                 advancePane.getChartTargetLinesPaneModel().getChartTargets());
      this.chartPropertyService.removeDeletedTargets(chartDescriptor,
                                                     advancePane.getChartTargetLinesPaneModel().getDeletedIndexList());

      assemblyInfo.setVSChartInfo(vsChartInfo);
      assemblyInfo.setRTChartDescriptor(null);
      setCube(assemblyInfo, hierarchyPropertyPaneModel);

      assemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      assemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());

      if(assemblyInfo.getTipViewValue() != null) {
         VSAssembly tip = viewsheet.getViewsheet().getAssembly(assemblyInfo.getTipViewValue());

         if(tip != null && tip.getTipConditionList() != null) {
            tip.setTipConditionList(null);
         }
      }

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, assemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);

      if(viewsheet.getOriginalID() != null) {
         VSChartInfo cinfo = assemblyInfo.getVSChartInfo();
         cinfo.setChartDescriptor(((ChartDescriptor) chartDescriptor.clone()));
         GraphUtil.fixVisualFrames(cinfo);
         final BindingModel model = vsBindingService.createModel(chartAssembly);
         commandDispatcher.sendCommand(new SetVSBindingModelCommand(model));
      }

      return null;
   }

   private void setCube(ChartVSAssemblyInfo assemblyInfo,
                        HierarchyPropertyPaneModel hierarchyPropertyPaneModel)
   {
      XCube xcube = assemblyInfo.getXCube();

      if(xcube == null) {
         xcube = new VSCube();
      }

      VSDimensionModel[] dims = hierarchyPropertyPaneModel.getDimensions();
      List<XDimension> dimensionList = new ArrayList<>();
      List<XCubeMember> measureList = new ArrayList<>();
      List<OutputColumnRefModel> allColumns =
         Arrays.asList(hierarchyPropertyPaneModel.getColumnList());

      for(VSDimensionModel dimModel : dims) {
         dimensionList.add(this.vsObjectPropertyService.convertModelToVSDimension(dimModel));

         for(VSDimensionMemberModel memberModel : dimModel.getMembers()) {
            int index = allColumns.indexOf(memberModel.getDataRef());

            if(index != -1) {
               allColumns.remove(index);
            }
         }
      }

      for(OutputColumnRefModel refModel : allColumns) {
         VSMeasure vsMeasure = new VSMeasure();
         AttributeRef aRef = new AttributeRef(refModel.getEntity(), refModel.getAttribute());
         aRef.setRefType(refModel.getRefType());
         ColumnRef cRef = new ColumnRef(aRef);
         cRef.setDataType(refModel.getDataType());
         vsMeasure.setName(cRef.getAttribute());
         vsMeasure.setDataRef(cRef);
         measureList.add(vsMeasure);
      }

      if(dimensionList.isEmpty()) {
         assemblyInfo.setXCube(null);
      }
      else if(xcube instanceof VSCube) {
         ((VSCube) xcube).setDimensions(dimensionList);
         ((VSCube) xcube).setMeasures(measureList);
         assemblyInfo.setXCube(xcube);
      }
   }

   private String[] getDataRefList(VSChartInfo vsInfo, String cubeType) {
      List<String> values = new ArrayList<>();
      List<ChartRef> rtRefs = new ArrayList<>();
      rtRefs.addAll(Arrays.asList(vsInfo.getRTXFields()));
      rtRefs.addAll(Arrays.asList(vsInfo.getRTYFields()));
      rtRefs.addAll(Arrays.asList(vsInfo.getRTGroupFields()));

      if(vsInfo.supportsPathField() && vsInfo.getRTPathField() != null) {
         rtRefs.add(vsInfo.getRTPathField());
      }

      List<VSDataRef> vsDataRefs = Arrays.asList(vsInfo.getAestheticRefs(true));

      for(VSDataRef vsDataRef : vsDataRefs) {
         if(vsDataRef instanceof AestheticRef) {
            rtRefs.add((ChartRef) ((AestheticRef) vsDataRef).getDataRef());
         }
         else {
            rtRefs.add((ChartRef) vsDataRef);
         }
      }

      rtRefs = fixSpecialRefs(vsInfo, rtRefs);

      for(ChartRef chartRef : rtRefs) {
         String tip = GraphUtil.getCaption(chartRef);

         if(chartRef instanceof VSDimensionRef) {
            tip = ((VSDimensionRef) chartRef).getCaption();

            if((chartRef.getRefType() & AbstractDataRef.CUBE) == AbstractDataRef.CUBE ||
               (chartRef.getRefType() & AbstractDataRef.CUBE) == 0)
            {
               tip = chartRef.getFullName();

               if(((VSDimensionRef) chartRef).isDateTime()) {
                  tip = vsChartHandler.getDateTimeView((XDimensionRef) chartRef);
               }
            }
         }

         if(!values.contains(tip)) {
            values.add(tip);
         }
      }

      return values.stream().filter(v -> v != null).toArray(String[]::new);
   }

   private static List<ChartRef> fixSpecialRefs(ChartInfo vsInfo, List<ChartRef> refs) {
      if(vsInfo instanceof MapInfo) {
         refs.addAll(Arrays.asList(((MapInfo) vsInfo).getRTGeoFields()));
      }
      else if(vsInfo instanceof CandleChartInfo) {
         CandleChartInfo candleInfo = (CandleChartInfo) vsInfo;
         refs.add(candleInfo.getRTCloseField());
         refs.add(candleInfo.getRTOpenField());
         refs.add(candleInfo.getRTHighField());
         refs.add(candleInfo.getRTLowField());
      }
      else if(vsInfo instanceof RelationChartInfo) {
         RelationChartInfo info2 = (RelationChartInfo) vsInfo;
         refs.add(info2.getRTSourceField());
         refs.add(info2.getRTTargetField());
      }
      else if(vsInfo instanceof GanttChartInfo) {
         GanttChartInfo info2 = (GanttChartInfo) vsInfo;
         refs.add(info2.getRTStartField());
         refs.add(info2.getRTEndField());
         refs.add(info2.getRTMilestoneField());
      }

      return refs;
   }

   private boolean hasDynamic(VSChartInfo info) {
      ChartRef[] xrefs = info.getModelRefsX(false);
      boolean has = Arrays.stream(xrefs).anyMatch(ref -> ref instanceof VSChartDimensionRef &&
         ((VSChartDimensionRef) ref).isDynamic() || ((VSChartAggregateRef) ref).isDynamicBinding());

      if(!has) {
         ChartRef[] yrefs = info.getModelRefsY(false);
         has = Arrays.stream(yrefs).anyMatch(ref -> ref instanceof VSChartDimensionRef &&
            ((VSChartDimensionRef) ref).isDynamic() || ((VSChartAggregateRef) ref).isDynamicBinding());
      }

      return has;
   }


   private final VSObjectPropertyService vsObjectPropertyService;
   private final ChartPropertyService chartPropertyService;
   private final VSChartHandler vsChartHandler;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
   private final VSBindingService vsBindingService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final VSTrapService trapService;
}
