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
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.event.ApplySelectionListEvent;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;

@Service
@ClusterProxy
public class SelectionTreePropertyDialogService {

   public SelectionTreePropertyDialogService(VSObjectPropertyService vsObjectPropertyService,
                                             VSOutputService vsOutputService,
                                             ViewsheetService viewsheetService,
                                             VSTrapService trapService,
                                             VSDialogService dialogService,
                                             VSSelectionService vsSelectionService,
                                             SelectionDialogService selectionDialogService,
                                             VSAssemblyInfoHandler assemblyInfoHandler,
                                             DataRefModelFactoryService dataRefService,
                                             DataSourceRegistry dataSourceRegistry,
                                             QueryManagerService queryManagerService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.vsOutputService = vsOutputService;
      this.viewsheetService = viewsheetService;
      this.trapService = trapService;
      this.dialogService = dialogService;
      this.vsSelectionService = vsSelectionService;
      this.selectionDialogService = selectionDialogService;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.dataRefService = dataRefService;
      this.dataSourceRegistry = dataSourceRegistry;
      this.queryManagerService = queryManagerService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public SelectionTreePropertyDialogModel getSelectionTreePropertyModel(@ClusterProxyKey String runtimeId,
                                                                         String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      SelectionTreeVSAssembly selectionTreeAssembly;
      SelectionTreeVSAssemblyInfo selectionTreeAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         selectionTreeAssembly = (SelectionTreeVSAssembly) vs.getAssembly(objectId);
         selectionTreeAssemblyInfo = (SelectionTreeVSAssemblyInfo) selectionTreeAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         throw e;
      }

      SelectionTreePropertyDialogModel result = new SelectionTreePropertyDialogModel();
      SelectionGeneralPaneModel selectionGeneralPane = result.getSelectionGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel = selectionGeneralPane.getTitlePropPaneModel();
      GeneralPropPaneModel generalPropPaneModel = selectionGeneralPane.getGeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         selectionGeneralPane.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      SelectionTreePaneModel selectionTreePaneModel = result.getSelectionTreePaneModel();
      SelectionMeasurePaneModel selectionMeasurePaneModel = selectionTreePaneModel.getSelectionMeasurePaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel = VSAssemblyScriptPaneModel.builder();

      titlePropPaneModel.setVisible(selectionTreeAssemblyInfo.getTitleVisibleValue());
      titlePropPaneModel.setTitle(selectionTreeAssemblyInfo.getTitleValue());

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(selectionTreeAssemblyInfo.getEnabledValue());

      Point pos = dialogService.getAssemblyPosition(selectionTreeAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(selectionTreeAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setTitleHeight(
         VSDensityDefaults.titleHeight(selectionTreeAssemblyInfo, selectionTreeAssemblyInfo.getTitleHeightValue()));
      sizePositionPaneModel.setTitleHeightFollowsDensity(
         selectionTreeAssemblyInfo.getVizMark() == null ? null :
            !selectionTreeAssemblyInfo.isUserTitleHeight());
      sizePositionPaneModel.setContainer(selectionTreeAssembly.getContainer() != null);

      int cellHeight = selectionTreeAssemblyInfo.getEffectiveCellHeight();
      sizePositionPaneModel.setCellHeight(cellHeight <= 0 ? AssetUtil.defh : cellHeight);
      sizePositionPaneModel.setCellHeightFollowsDensity(
         selectionTreeAssemblyInfo.getVizMark() == null ? null :
            !selectionTreeAssemblyInfo.isUserCellHeight());
      VSDialogService.readSizeFollowsDensity(selectionTreeAssemblyInfo, sizePositionPaneModel,
         !(selectionTreeAssembly.getContainer() instanceof CurrentSelectionVSAssembly) &&
            selectionTreeAssemblyInfo.getShowTypeValue() == SelectionVSAssemblyInfo.LIST_SHOW_TYPE);

      basicGeneralPaneModel.setName(selectionTreeAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(selectionTreeAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(selectionTreeAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, selectionTreeAssemblyInfo.getAbsoluteName()));

      selectionGeneralPane.setShowType(selectionTreeAssemblyInfo.getShowTypeValue());
      selectionGeneralPane.setListHeight(selectionTreeAssemblyInfo.getListHeight());
      selectionGeneralPane.setSortType(selectionTreeAssemblyInfo.getSortTypeValue());
      selectionGeneralPane.setSubmitOnChange(selectionTreeAssemblyInfo.getSubmitOnChangeValue());
      selectionGeneralPane.setSingleSelection(selectionTreeAssemblyInfo.getSingleSelectionValue());
      selectionGeneralPane.setSuppressBlank(selectionTreeAssemblyInfo.isSuppressBlankValue());
      selectionGeneralPane.setSelectFirstItem(selectionTreeAssemblyInfo.getSelectFirstItemValue());
      selectionGeneralPane.setQuickSwitchAllowed(selectionTreeAssemblyInfo.getQuickSwitchAllowedValue());

      PaddingPaneModel paddingPaneModel = selectionGeneralPane.getPaddingPaneModel();
      Insets padding = selectionTreeAssemblyInfo.getPadding();
      paddingPaneModel.setTop(padding.top);
      paddingPaneModel.setLeft(padding.left);
      paddingPaneModel.setBottom(padding.bottom);
      paddingPaneModel.setRight(padding.right);
      // null hides the checkbox: an unmarked selection has no default to follow, and the pane then
      // behaves exactly as it did before the checkbox existed
      paddingPaneModel.setFollowsDefault(
         selectionTreeAssemblyInfo.getVizMark() == null ? null : !selectionTreeAssemblyInfo.isUserPadding());

      PaddingPaneModel cellPaddingPaneModel = selectionGeneralPane.getCellPaddingPaneModel();
      Insets cellPadding = selectionTreeAssemblyInfo.getCellPadding();
      cellPaddingPaneModel.setTop(cellPadding == null ? 0 : cellPadding.top);
      cellPaddingPaneModel.setLeft(cellPadding == null ? 0 : cellPadding.left);
      cellPaddingPaneModel.setBottom(cellPadding == null ? 0 : cellPadding.bottom);
      cellPaddingPaneModel.setRight(cellPadding == null ? 0 : cellPadding.right);
      cellPaddingPaneModel.setFollowsDefault(
         selectionTreeAssemblyInfo.getVizMark() == null ? null : !selectionTreeAssemblyInfo.isUserCellPadding());

      if(selectionTreeAssemblyInfo.getSingleSelectionLevels() != null) {
         selectionGeneralPane.setSingleSelectionLevels(
            selectionTreeAssemblyInfo.getSingleSelectionLevelNames());
      }

      String parentId = selectionTreeAssemblyInfo.getParentIDValue();
      String id = selectionTreeAssemblyInfo.getIDValue();
      String label = selectionTreeAssemblyInfo.getLabelValue();
      final String selectedTableName = selectionTreeAssemblyInfo.getFirstTableName();
      selectionTreePaneModel.setMode(selectionTreeAssemblyInfo.getMode());
      selectionTreePaneModel.setSelectChildren(selectionTreeAssemblyInfo.isSelectChildren());
      selectionTreePaneModel.setParentId(parentId);
      selectionTreePaneModel.setId(id);
      selectionTreePaneModel.setLabel(label);
      selectionTreePaneModel.setSelectedTable(selectedTableName);
      selectionTreePaneModel.setAdditionalTables(selectionTreeAssemblyInfo.getAdditionalTableNames());
      selectionTreePaneModel.setGrayedOutFields(assemblyInfoHandler.getGrayedOutFields(rvs));
      selectionTreePaneModel.setModelSource(vs.getBaseEntry() == null ? false : vs.getBaseEntry().isLogicModel());
      final TreeNodeModel tree = vsOutputService.getSelectionTablesTree(rvs, principal);
      selectionTreePaneModel.setTargetTree(tree);
      DataRef[] dataRefs = selectionTreeAssemblyInfo.getDataRefs();

      if(dataRefs != null && dataRefs.length > 0) {
         List<OutputColumnRefModel> columns = new ArrayList<>();

         for(DataRef dataRef : dataRefs) {
            ColumnRef columnRef = (ColumnRef) dataRef;
            final Optional<OutputColumnRefModel> refModel = selectionDialogService
               .findSelectedOutputColumnRefModel(tree, selectedTableName, columnRef);

            if(refModel.isPresent()) {
               final OutputColumnRefModel columnModel = refModel.get();
               columns.add(columnModel);

               if(selectionTreeAssemblyInfo.getMode() == SelectionTreeVSAssemblyInfo.ID) {
                  if(parentId != null && columnRef.getName().equals(parentId)) {
                     selectionTreePaneModel.setParentIdRef(columnModel);
                  }

                  if(id != null && columnRef.getName().equals(id)) {
                     selectionTreePaneModel.setIdRef(columnModel);
                  }

                  if(label != null && columnRef.getName().equals(label)) {
                     selectionTreePaneModel.setLabelRef(columnModel);
                  }
               }
            }
         }

         if(selectionTreeAssemblyInfo.getMode() == SelectionTreeVSAssemblyInfo.COLUMN) {
            selectionTreePaneModel.setSelectedColumns(columns.toArray(new OutputColumnRefModel[0]));
         }
      }

      selectionMeasurePaneModel.setMeasure(selectionTreeAssemblyInfo.getMeasureValue());
      selectionMeasurePaneModel.setFormula(selectionTreeAssemblyInfo.getFormulaValue());
      selectionMeasurePaneModel.setShowText(selectionTreeAssemblyInfo.isShowTextValue());
      selectionMeasurePaneModel.setShowBar(selectionTreeAssemblyInfo.isShowBarValue());

      vsAssemblyScriptPaneModel.scriptEnabled(selectionTreeAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(selectionTreeAssemblyInfo.getScript() == null ?
                                              "" : selectionTreeAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setSelectionTreePropertyModel(@ClusterProxyKey String runtimeId, String objectId,
                                             SelectionTreePropertyDialogModel value, String linkUri,
                                             Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet viewsheet;
      SelectionTreeVSAssembly selectionTreeAssembly;
      SelectionTreeVSAssemblyInfo streeInfo;

      try {
         viewsheet = viewsheetService.getViewsheet(runtimeId, principal);
         selectionTreeAssembly = (SelectionTreeVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         streeInfo = (SelectionTreeVSAssemblyInfo) Tool.clone(selectionTreeAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         throw e;
      }

      boolean osingleSelection = streeInfo.isSingleSelection();
      List<Integer> osingleLevels = streeInfo.getSingleSelectionLevels();
      SelectionGeneralPaneModel selectionGeneralPane = value.getSelectionGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel = selectionGeneralPane.getTitlePropPaneModel();
      GeneralPropPaneModel generalPropPaneModel = selectionGeneralPane.getGeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         selectionGeneralPane.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      SelectionTreePaneModel selectionTreePaneModel = value.getSelectionTreePaneModel();
      SelectionMeasurePaneModel selectionMeasurePaneModel = selectionTreePaneModel.getSelectionMeasurePaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      streeInfo.setTitleVisibleValue(titlePropPaneModel.isVisible());
      streeInfo.setTitleValue(titlePropPaneModel.getTitle());

      Dimension shownSize =
         new Dimension(dialogService.getAssemblySize(streeInfo, viewsheet.getViewsheet()));
      VSDialogService.followDensitySize(streeInfo, sizePositionPaneModel);
      dialogService.setAssemblySize(streeInfo, sizePositionPaneModel);
      VSDialogService.recordAuthorSize(streeInfo, sizePositionPaneModel, shownSize);
      dialogService.setAssemblyPosition(streeInfo, sizePositionPaneModel);
      Boolean followsDensity = sizePositionPaneModel.getTitleHeightFollowsDensity();

      if(followsDensity == null) {
         if(sizePositionPaneModel.getTitleHeight() != streeInfo.getTitleHeightValue()) {
            streeInfo.setUserTitleHeight(true);
            streeInfo.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());
         }
      }
      else if(followsDensity) {
         streeInfo.setUserTitleHeight(false);
         streeInfo.setTitleHeightValue(streeInfo.getLegacyTitleHeight());
      }
      else {
         streeInfo.setUserTitleHeight(true);
         streeInfo.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());
      }

      Boolean cellFollowsDensity = sizePositionPaneModel.getCellHeightFollowsDensity();

      if(cellFollowsDensity == null) {
         if(sizePositionPaneModel.getCellHeight() != streeInfo.getEffectiveCellHeight()) {
            streeInfo.setUserCellHeight(true);
            streeInfo.setCellHeight(sizePositionPaneModel.getCellHeight());
         }
      }
      else if(cellFollowsDensity) {
         streeInfo.setUserCellHeight(false);
         streeInfo.setCellHeight(AssetUtil.defh);
      }
      else {
         streeInfo.setUserCellHeight(true);
         streeInfo.setCellHeight(sizePositionPaneModel.getCellHeight());
      }

      streeInfo.setEnabledValue(generalPropPaneModel.getEnabled());

      streeInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      streeInfo.setVisibleValue(basicGeneralPaneModel.getVisible());

      int oldShowType = streeInfo.getShowTypeValue();
      int newShowType = selectionGeneralPane.getShowType();
      streeInfo.setShowTypeValue(newShowType);
      streeInfo.setListHeight(selectionGeneralPane.getListHeight());

      Dimension size = viewsheet.getViewsheet().getPixelSize(streeInfo);

      if(newShowType == SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE) {
         // uses titleHeight directly rather than getBottomTabChildHeight() because
         // the else branch handles showType transitions specific to property-apply
         size.height = streeInfo.getTitleHeight();
      }
      else if(oldShowType != newShowType) {
         int minListHeight;

         if(streeInfo.getVizMark() != null) {
            // a marked tree's rows and title lane follow the density, and its rows sit inside the
            // card inset
            minListHeight = streeInfo.getListBodyHeight() +
               (streeInfo.isTitleVisible() ? streeInfo.getTitleHeight() : 0);
         }
         else {
            minListHeight = streeInfo.getListHeight() * AssetUtil.defh;

            if(streeInfo.isTitleVisible()) {
               minListHeight += streeInfo.getCellHeight();
            }
         }

         if(minListHeight > size.height) {
            size.height = minListHeight;
         }
      }

      VSAssembly container = selectionTreeAssembly.getContainer();

      if(container instanceof TabVSAssembly) {
         TabVSAssemblyInfo tabInfo =
            (TabVSAssemblyInfo) container.getVSAssemblyInfo();

         if(tabInfo.getBottomTabsValue() && tabInfo.getPixelOffset() != null
            && streeInfo.getPixelOffset() != null)
         {
            int tabTop = tabInfo.getPixelOffset().y;
            int x = streeInfo.getPixelOffset().x;
            streeInfo.setPixelOffset(new Point(x, tabTop - size.height));
         }
      }

      streeInfo.setListHeight(selectionGeneralPane.getListHeight());
      streeInfo.setSortTypeValue(selectionGeneralPane.getSortType());
      streeInfo.setSubmitOnChangeValue(selectionGeneralPane.isSubmitOnChange());
      streeInfo.setSuppressBlankValue(selectionGeneralPane.isSuppressBlank());
      streeInfo.setSelectFirstItemValue(selectionGeneralPane.isSelectFirstItem());
      streeInfo.setQuickSwitchAllowedValue(selectionGeneralPane.isQuickSwitchAllowed());

      PaddingPaneModel paddingPaneModel = selectionGeneralPane.getPaddingPaneModel();
      Insets editedPadding = new Insets(
         paddingPaneModel.getTop(), paddingPaneModel.getLeft(),
         paddingPaneModel.getBottom(), paddingPaneModel.getRight());
      Boolean paddingFollowsDefault = paddingPaneModel.getFollowsDefault();

      if(paddingFollowsDefault == null) {
         // no checkbox was shown, so this selection is not marked; store only a real edit
         if(!editedPadding.equals(streeInfo.getPadding())) {
            streeInfo.setUserPadding(true);
            streeInfo.setPadding(editedPadding);
         }
      }
      else if(paddingFollowsDefault) {
         // clear the opinion and let the default decide, the same shape Revert uses
         streeInfo.setUserPadding(false);
         streeInfo.resetPadding(VizContext.of(streeInfo));
      }
      else {
         streeInfo.setUserPadding(true);
         streeInfo.setPadding(editedPadding);
      }

      PaddingPaneModel cellPaddingPaneModel = selectionGeneralPane.getCellPaddingPaneModel();
      Insets editedCellPadding = new Insets(
         cellPaddingPaneModel.getTop(), cellPaddingPaneModel.getLeft(),
         cellPaddingPaneModel.getBottom(), cellPaddingPaneModel.getRight());
      Boolean cellPaddingFollowsDefault = cellPaddingPaneModel.getFollowsDefault();

      if(cellPaddingFollowsDefault == null) {
         // no checkbox was shown, so this selection is not marked; store only a real edit. the load
         // side shows 0 for an absent padding, so all zeros means none rather than a pinned 0
         if(editedCellPadding.equals(new Insets(0, 0, 0, 0))) {
            streeInfo.resetUserCellPadding();
         }
         else if(!editedCellPadding.equals(streeInfo.getCellPadding())) {
            streeInfo.setCellPadding(editedCellPadding, CompositeValue.Type.USER);
         }
      }
      else if(cellPaddingFollowsDefault) {
         // clear the opinion and let the density decide. Writing the current density value into
         // the USER tier here would pin this tier
         streeInfo.resetUserCellPadding();
      }
      else {
         streeInfo.setCellPadding(editedCellPadding, CompositeValue.Type.USER);
      }

      setAssemblyInfoTables(streeInfo, selectionTreePaneModel, principal);
      setAssemblyInfoDataRefs(streeInfo, selectionTreePaneModel);
      setAssemblyInfoMeasure(streeInfo, selectionMeasurePaneModel);

      streeInfo.setSingleSelectionValue(selectionGeneralPane.isSingleSelection());
      streeInfo.setSingleSelectionLevelNames(selectionGeneralPane.getSingleSelectionLevels());

      streeInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      streeInfo.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, streeInfo, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);

      Viewsheet vs = viewsheet.getViewsheet();
      selectionTreeAssembly =
         (SelectionTreeVSAssembly) vs.getAssembly(basicGeneralPaneModel.getName());
      streeInfo = (SelectionTreeVSAssemblyInfo) selectionTreeAssembly.getVSAssemblyInfo();
      updateSelection(runtimeId, osingleSelection, osingleLevels, streeInfo, linkUri, principal, commandDispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkVSTrap(@ClusterProxyKey String runtimeId, SelectionTreePaneModel value,
                                       String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box.isEmpty()) {
         return null;
      }

      box.get().lockRead();

      try {
         SelectionTreeVSAssembly assembly =
            (SelectionTreeVSAssembly) rvs.getViewsheet().getAssembly(objectId);

         if(assembly == null) {
            return null;
         }

         SelectionTreeVSAssemblyInfo oldAssemblyInfo =
            (SelectionTreeVSAssemblyInfo) Tool.clone(assembly.getVSAssemblyInfo());
         SelectionTreeVSAssemblyInfo newAssemblyInfo =
            (SelectionTreeVSAssemblyInfo) Tool.clone(assembly.getVSAssemblyInfo());

         SelectionMeasurePaneModel selectionMeasurePaneModel = value.getSelectionMeasurePaneModel();

         setAssemblyInfoTables(newAssemblyInfo, value, principal);
         setAssemblyInfoDataRefs(newAssemblyInfo, value);
         setAssemblyInfoMeasure(newAssemblyInfo, selectionMeasurePaneModel);
         VSTableTrapModel trap = trapService.checkTrap(rvs, oldAssemblyInfo, newAssemblyInfo);
         rvs.getViewsheet().getAssembly(objectId).setVSAssemblyInfo(oldAssemblyInfo);

         return trap;
      }
      finally {
         box.get().unlockRead();
      }
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public List<DataRefModel> getGrayedOutFields(@ClusterProxyKey String runtimeId, SelectionTreePaneModel value,
                                                String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box.isEmpty()) {
         return null;
      }

      box.get().lockRead();

      try {
         SelectionTreeVSAssembly assembly =
            (SelectionTreeVSAssembly) rvs.getViewsheet().getAssembly(objectId);

         if(assembly == null) {
            return null;
         }

         SelectionTreeVSAssemblyInfo oldAssemblyInfo =
            (SelectionTreeVSAssemblyInfo) Tool.clone(assembly.getVSAssemblyInfo());
         SelectionTreeVSAssemblyInfo newAssemblyInfo =
            (SelectionTreeVSAssemblyInfo) Tool.clone(assembly.getVSAssemblyInfo());

         SelectionMeasurePaneModel selectionMeasurePaneModel = value.getSelectionMeasurePaneModel();

         setAssemblyInfoTables(newAssemblyInfo, value, principal);
         setAssemblyInfoDataRefs(newAssemblyInfo, value);
         setAssemblyInfoMeasure(newAssemblyInfo, selectionMeasurePaneModel);
         List<DataRefModel> grayed = getGrayedFields(rvs, oldAssemblyInfo, newAssemblyInfo);
         rvs.getViewsheet().getAssembly(objectId).setVSAssemblyInfo(oldAssemblyInfo);

         return grayed;
      }
      finally {
         box.get().unlockRead();
      }
   }

   private List<DataRefModel> getGrayedFields(RuntimeViewsheet rvs,
                                              SelectionTreeVSAssemblyInfo oinfo,
                                              SelectionTreeVSAssemblyInfo ninfo)
   {
      VSModelTrapContext context = new VSModelTrapContext(rvs, dataSourceRegistry, true);
      context.checkTrap(oinfo, ninfo);
      DataRef[] refs = context.getGrayedFields();
      List<DataRefModel> fields = new ArrayList<>();

      for(DataRef ref : refs) {
         fields.add(dataRefService.createDataRefModel(ref));
      }

      return fields;
   }

   private ColumnRef createColumnRefFromModel(OutputColumnRefModel outputColumnRefModel) {
      AttributeRef aRef = new AttributeRef(outputColumnRefModel.getEntity(), outputColumnRefModel.getAttribute());
      aRef.setRefType(outputColumnRefModel.getRefType());
      ColumnRef cRef = new ColumnRef(aRef);
      cRef.setDataType(outputColumnRefModel.getDataType() == null ? XSchema.STRING : outputColumnRefModel.getDataType());
      cRef.setAlias(outputColumnRefModel.getAlias());
      return cRef;
   }

   private void setAssemblyInfoTables(SelectionTreeVSAssemblyInfo info,
                                      SelectionTreePaneModel model, Principal principal)
   {
      // a cube table is resolved from its data source without a permission check, so check
      // a newly bound one before it is set (Bug #77427)
      queryManagerService.checkNewCubeTablesReadPermission(
         model.getSelectedTable(), model.getAdditionalTables(), info.getTableNames(), principal);
      info.setFirstTableName(model.getSelectedTable());
      info.setAdditionalTableNames(model.getAdditionalTables());
   }

   private void setAssemblyInfoDataRefs(SelectionTreeVSAssemblyInfo info,
                                        SelectionTreePaneModel model) {
      int mode = model.getMode();
      info.setMode(mode);
      List<ColumnRef> columnRefs = new ArrayList<>();

      if(mode == SelectionTreeVSAssemblyInfo.ID) {
         info.setSelectChildren(model.isSelectChildren());
         info.setParentIDValue(model.getParentId());
         info.setIDValue(model.getId());
         info.setLabelValue(model.getLabel());

         OutputColumnRefModel parentIdRef = model.getParentIdRef();
         OutputColumnRefModel idRef = model.getIdRef();
         OutputColumnRefModel labelRef = model.getLabelRef();

         if(parentIdRef != null) {
            columnRefs.add(createColumnRefFromModel(parentIdRef));
         }

         if(idRef != null) {
            columnRefs.add(createColumnRefFromModel(idRef));
         }

         if(labelRef != null) {
            columnRefs.add(createColumnRefFromModel(labelRef));
         }
      }
      else {
         info.setSelectChildren(false);
         info.setParentIDValue(null);
         info.setIDValue(null);
         info.setLabel(null);
         OutputColumnRefModel[] columnRefModels = model.getSelectedColumns();

         if(columnRefModels != null && columnRefModels.length > 0) {
            for(OutputColumnRefModel columnRefModel : columnRefModels) {
               columnRefs.add(createColumnRefFromModel(columnRefModel));
            }
         }
      }

      info.setDataRefs(columnRefs.toArray(new ColumnRef[0]));
   }

   private void setAssemblyInfoMeasure(SelectionTreeVSAssemblyInfo info,
                                       SelectionMeasurePaneModel model)
   {
      String measure = model.getMeasure();
      measure = measure == null || measure.isEmpty() ? null : measure;
      info.setMeasureValue(measure);

      if(measure != null) {
         info.setShowTextValue(model.isShowText());
         info.setShowBarValue(model.isShowBar());

         if(info.getTableNames().size() > 0 &&
            info.getTableNames().stream().noneMatch((t) -> t.contains(Assembly.CUBE_VS)))
         {
            info.setFormulaValue(model.getFormula());
         }
         else {
            info.setFormulaValue("none");
         }
      }
   }

   /**
    * If value is selected by setting the single selection property to true in the property
    * dialog, update other selection lists/trees to stay in sync with the selected value
    */
   private void updateSelection(String runtimeId,boolean osingleSelection, List<Integer> osingleLevels,
                                SelectionTreeVSAssemblyInfo streeInfo,
                                String linkUri, Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      if((!osingleSelection || !Tool.equals(osingleLevels, streeInfo.getSingleSelectionLevels())) &&
         streeInfo.isSingleSelection())
      {
         List<String> list = new ArrayList<>();
         collectSelectedValues(streeInfo.getCompositeSelectionValue(), list);
         ApplySelectionListEvent.Value value = new ApplySelectionListEvent.Value();
         value.setValue(list.toArray(new String[list.size()]));
         value.setSelected(true);

         ApplySelectionListEvent event = new ApplySelectionListEvent();
         event.setEventSource(streeInfo.getAbsoluteName());
         event.setType(ApplySelectionListEvent.Type.APPLY);
         List<ApplySelectionListEvent.Value> values = new ArrayList<ApplySelectionListEvent.Value>();
         values.add(value);
         event.setValues(values);

         vsSelectionService.applySelection(runtimeId, streeInfo.getAbsoluteName(), event,
                                           principal, dispatcher, linkUri);
      }
   }

   private void collectSelectedValues(CompositeSelectionValue value, List<String> values) {
      if(value == null) {
         return;
      }

      SelectionList list = value.getSelectionList();

      if(list == null || list.getSelectionValueCount() == 0) {
         return;
      }

      SelectionValue selectionValue = list.getSelectionValue(0);

      if(selectionValue != null && selectionValue.isSelected()) {
         values.add(selectionValue.getValue());
      }

      if(selectionValue instanceof CompositeSelectionValue) {
         collectSelectedValues((CompositeSelectionValue) selectionValue, values);
      }
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSOutputService vsOutputService;
   private final ViewsheetService viewsheetService;
   private final VSTrapService trapService;
   private final VSDialogService dialogService;
   private final VSSelectionService vsSelectionService;
   private final SelectionDialogService selectionDialogService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final DataRefModelFactoryService dataRefService;
   private final DataSourceRegistry dataSourceRegistry;
   private final QueryManagerService queryManagerService;
}
