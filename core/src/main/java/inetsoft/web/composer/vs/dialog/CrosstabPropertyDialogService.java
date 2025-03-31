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
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
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
public class CrosstabPropertyDialogService {

   public CrosstabPropertyDialogService(VSObjectPropertyService vsObjectPropertyService,
                                        VSDialogService dialogService,
                                        ViewsheetService viewsheetService,
                                        VSAssemblyInfoHandler assemblyInfoHandler,
                                        VSTrapService trapService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.dialogService = dialogService;
      this.viewsheetService = viewsheetService;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.trapService = trapService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public CrosstabPropertyDialogModel getCrosstabPropertyDialogModel(@ClusterProxyKey String runtimeId,
                                                                     String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      CrosstabVSAssembly crosstabAssembly;
      CrosstabVSAssemblyInfo crosstabAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         crosstabAssembly = (CrosstabVSAssembly) vs.getAssembly(objectId);
         crosstabAssemblyInfo = (CrosstabVSAssemblyInfo) crosstabAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         throw e;
      }

      CrosstabPropertyDialogModel result = new CrosstabPropertyDialogModel();
      TableViewGeneralPaneModel tableViewGeneralPaneModel =
         result.getTableViewGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel =
         tableViewGeneralPaneModel.getGeneralPropPaneModel();
      TitlePropPaneModel titlePropPaneModel =
         tableViewGeneralPaneModel.getTitlePropPaneModel();
      TableStylePaneModel tableStylePaneModel =
         tableViewGeneralPaneModel.getTableStylePaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         tableViewGeneralPaneModel.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel =
         generalPropPaneModel.getBasicGeneralPaneModel();
      CrosstabAdvancedPaneModel crosstabAdvancedPaneModel =
         result.getCrosstabAdvancedPaneModel();
      TipPaneModel tipPaneModel = crosstabAdvancedPaneModel.getTipPaneModel();
      HierarchyPropertyPaneModel hierarchyPropertyPaneModel =
         result.getHierarchyPropertyPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel =
         VSAssemblyScriptPaneModel.builder();

      tableViewGeneralPaneModel.setShowMaxRows(false);

      titlePropPaneModel.setVisible(crosstabAssemblyInfo.getTitleVisibleValue());
      titlePropPaneModel.setTitle(crosstabAssemblyInfo.getTitleValue());

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(crosstabAssemblyInfo.getEnabledValue());

      basicGeneralPaneModel.setName(crosstabAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setNameEditable(!crosstabAssemblyInfo.isWizardTemporary());
      basicGeneralPaneModel.setPrimary(crosstabAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(crosstabAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, crosstabAssemblyInfo.getAbsoluteName()));

      TableViewStylePaneController styleController = new TableViewStylePaneController();
      tableStylePaneModel.setTableStyle(crosstabAssemblyInfo.getTableStyleValue());
      tableStylePaneModel.setStyleTree(dialogService.getStyleTree(rvs, principal, false));

      Point pos = dialogService.getAssemblyPosition(crosstabAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(crosstabAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setTitleHeight(crosstabAssemblyInfo.getTitleHeightValue());
      sizePositionPaneModel.setContainer(crosstabAssembly.getContainer() != null);

      VSCrosstabInfo vsCrossTabInfo = crosstabAssemblyInfo.getVSCrosstabInfo();

      if(vsCrossTabInfo != null) {
         crosstabAdvancedPaneModel.setFillBlankWithZero(
            vsCrossTabInfo.getFillBlankWithZeroValue());
         crosstabAdvancedPaneModel.setSummarySideBySide(
            vsCrossTabInfo.getSummarySideBySideValue());
         crosstabAdvancedPaneModel.setMergeSpan(vsCrossTabInfo.getMergeSpanValue());
         crosstabAdvancedPaneModel.setSortOthersLast(vsCrossTabInfo.getSortOthersLastValue());
         crosstabAdvancedPaneModel.setSortOthersLastEnabled(vsCrossTabInfo.isSortOthersLastEnabled());
         crosstabAdvancedPaneModel.setCalculateTotal(vsCrossTabInfo.getCalculateTotalValue());
      }
      else {
         crosstabAdvancedPaneModel.setCrosstabInfoNull(true);
      }

      crosstabAdvancedPaneModel.setShrink(crosstabAssemblyInfo.getShrinkValue());
      crosstabAdvancedPaneModel.setDrillEnabled(
         Boolean.valueOf(crosstabAssemblyInfo.getDrillEnabledValue()));
      crosstabAdvancedPaneModel.setEnableAdhoc(
         crosstabAssemblyInfo.getEnableAdhocValue());
      crosstabAdvancedPaneModel.setDateComparisonEnabled(
         "true".equals(crosstabAssemblyInfo.getDateComparisonEnabledValue()));
      crosstabAdvancedPaneModel.setDateComparisonSupport(
         DateComparisonUtil.supportDateComparison(crosstabAssemblyInfo.getVSCrosstabInfo(), true));

      tipPaneModel.setTipOption(
         crosstabAssemblyInfo.getTipOptionValue() == TipVSAssemblyInfo.VIEWTIP_OPTION);
      tipPaneModel.setTipView(crosstabAssemblyInfo.getTipViewValue());
      tipPaneModel.setAlpha(crosstabAssemblyInfo.getAlphaValue() == null ?
                               "100" : crosstabAssemblyInfo.getAlphaValue());
      String[] flyoverViews = crosstabAssemblyInfo.getFlyoverViewsValue();
      tipPaneModel.setFlyOverViews(flyoverViews == null ? new String[0] : flyoverViews);
      tipPaneModel.setFlyOnClick(Boolean.valueOf(crosstabAssemblyInfo.getFlyOnClickValue()));
      tipPaneModel.setPopComponents(
         this.vsObjectPropertyService.getSupportedTablePopComponents(
            rvs, crosstabAssemblyInfo.getAbsoluteName(), false));
      tipPaneModel.setFlyoverComponents(
         this.vsObjectPropertyService.getSupportedTablePopComponents(
            rvs, crosstabAssemblyInfo.getAbsoluteName(),  true));
      String srctbl = crosstabAssemblyInfo.getTableName();
      tipPaneModel.setDataViewEnabled(srctbl != null && !VSUtil.isVSAssemblyBinding(srctbl));

      SourceInfo sourceInfo = crosstabAssemblyInfo.getSourceInfo();
      String tableName = sourceInfo == null ? "" : sourceInfo.getSource();
      tableName = tableName == null ? "" : tableName;

      hierarchyPropertyPaneModel.setCube(tableName.contains(Assembly.CUBE_VS));

      if(tableName.length() > 0 && !tableName.contains(Assembly.CUBE_VS)) {
         hierarchyPropertyPaneModel.setColumnList(
            this.vsObjectPropertyService.getHierarchyColumnList(
               crosstabAssemblyInfo.getAbsoluteName(), tableName, rvs, principal));
      }
      else {
         hierarchyPropertyPaneModel.setColumnList(new OutputColumnRefModel[0]);
      }

      List<VSDimensionModel> vsDimensionModels = new ArrayList<>();
      XCube vsCube = crosstabAssemblyInfo.getXCube();

      if(vsCube != null) {
         Enumeration dims = vsCube.getDimensions();

         while(dims.hasMoreElements()) {
            Object dim = dims.nextElement();

            if(dim instanceof VSDimension) {
               vsDimensionModels.add(this.vsObjectPropertyService.convertVSDimensionToModel(
                  (VSDimension) dim));
            }
         }
      }

      hierarchyPropertyPaneModel.setDimensions(
         vsDimensionModels.toArray(new VSDimensionModel[0]));
      hierarchyPropertyPaneModel.setGrayedOutFields(assemblyInfoHandler.getGrayedOutFields(rvs));

      vsAssemblyScriptPaneModel.scriptEnabled(crosstabAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(crosstabAssemblyInfo.getScript() == null ?
                                              "" : crosstabAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkVSTrap(@ClusterProxyKey String runtimeId,
                                       CrosstabPropertyDialogModel value,
                                       String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      CrosstabVSAssembly crosstab = (CrosstabVSAssembly) rvs.getViewsheet().getAssembly(objectId);

      if(crosstab == null) {
         return VSTableTrapModel.builder()
            .showTrap(false)
            .build();
      }

      VSAssemblyInfo oldAssemblyInfo = (VSAssemblyInfo) Tool.clone(crosstab.getVSAssemblyInfo());
      CrosstabVSAssemblyInfo newAssemblyInfo =
         (CrosstabVSAssemblyInfo) Tool.clone(crosstab.getVSAssemblyInfo());

      HierarchyPropertyPaneModel hierarchyPropertyPaneModel = value.getHierarchyPropertyPaneModel();
      setCube(newAssemblyInfo, hierarchyPropertyPaneModel);

      rvs.getViewsheet().getAssembly(objectId).setVSAssemblyInfo(newAssemblyInfo);
      VSTableTrapModel trap = trapService.checkTrap(rvs, oldAssemblyInfo, newAssemblyInfo);
      rvs.getViewsheet().getAssembly(objectId).setVSAssemblyInfo(oldAssemblyInfo);

      return trap;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setCrosstabPropertyModel(@ClusterProxyKey String runtimeId, String objectId,
                                        CrosstabPropertyDialogModel value, String linkUri,
                                        Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet viewsheet;
      CrosstabVSAssembly crosstabAssembly;
      CrosstabVSAssemblyInfo assemblyInfo;

      try {
         viewsheet = viewsheetService.getViewsheet(runtimeId, principal);
         crosstabAssembly = (CrosstabVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         assemblyInfo = (CrosstabVSAssemblyInfo) Tool.clone(crosstabAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         throw e;
      }

      TableViewGeneralPaneModel tableViewGeneralPaneModel = value.getTableViewGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel = tableViewGeneralPaneModel.getGeneralPropPaneModel();
      TitlePropPaneModel titlePropPaneModel = tableViewGeneralPaneModel.getTitlePropPaneModel();
      TableStylePaneModel tableStylePaneModel = tableViewGeneralPaneModel.getTableStylePaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         tableViewGeneralPaneModel.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      CrosstabAdvancedPaneModel advancePane = value.getCrosstabAdvancedPaneModel();
      TipPaneModel tipPaneModel = advancePane.getTipPaneModel();
      HierarchyPropertyPaneModel hierarchyPropertyPaneModel = value.getHierarchyPropertyPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();
      VSCrosstabInfo vsCrossTabInfo = assemblyInfo.getVSCrosstabInfo();

      if(advancePane.isDateComparisonEnabled() != assemblyInfo.isDateComparisonEnabled() &&
         !assemblyInfo.isDateComparisonEnabled() &&
         !DateComparisonUtil.supportDateComparison(vsCrossTabInfo, false))
      {
         UserMessage msg = new UserMessage(Catalog.getCatalog().getString("date.comparison.enable"),
                                           ConfirmException.INFO);
         commandDispatcher.sendCommand(MessageCommand.fromUserMessage(msg));
      }

      assemblyInfo.setTitleVisibleValue(titlePropPaneModel.isVisible());
      assemblyInfo.setTitleValue(titlePropPaneModel.getTitle());
      assemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());
      assemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      assemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());
      assemblyInfo.setDateComparisonEnabledValue(advancePane.isDateComparisonEnabled() + "");

      if(!assemblyInfo.isDateComparisonEnabled()) {
         assemblyInfo.setDateComparisonInfo(null);
      }

      assemblyInfo.setTableStyleValue(tableStylePaneModel.getTableStyle());

      dialogService.setAssemblySize(assemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(assemblyInfo, sizePositionPaneModel);
      assemblyInfo.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());

      if(vsCrossTabInfo != null) {
         vsCrossTabInfo.setFillBlankWithZeroValue(advancePane.isFillBlankWithZero());
         vsCrossTabInfo.setSummarySideBySideValue(advancePane.isSummarySideBySide());
         vsCrossTabInfo.setMergeSpanValue(advancePane.isMergeSpan());
         vsCrossTabInfo.setSortOthersLastValue(advancePane.isSortOthersLast());
         vsCrossTabInfo.setCalculateTotalValue(advancePane.isCalculateTotal());
      }

      assemblyInfo.setDrillEnabledValue(advancePane.isDrillEnabled() + "");
      assemblyInfo.setEnableAdhocValue(advancePane.isEnableAdhoc());
      assemblyInfo.setShrinkValue(advancePane.isShrink());

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

      String[] flyovers = VSUtil.getValidFlyovers(tipPaneModel.getFlyOverViews(),
                                                  viewsheet.getViewsheet());
      assemblyInfo.setFlyoverViewsValue(flyovers);
      assemblyInfo.setFlyOnClickValue(tipPaneModel.isFlyOnClick() + "");
      setCube(assemblyInfo, hierarchyPropertyPaneModel);
      assemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      assemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());
      assemblyInfo.resetRColumnWidths();

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, assemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);

      return null;
   }

   private void setCube(CrosstabVSAssemblyInfo assemblyInfo,
                        HierarchyPropertyPaneModel hierarchyPropertyPaneModel)
   {
      XCube vsCube = assemblyInfo.getXCube();

      if(vsCube == null) {
         vsCube = new VSCube();
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
      else if(vsCube instanceof VSCube) {
         ((VSCube) vsCube).setDimensions(dimensionList);
         ((VSCube) vsCube).setMeasures(measureList);
         assemblyInfo.setXCube(vsCube);
      }
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final VSTrapService trapService;
}
