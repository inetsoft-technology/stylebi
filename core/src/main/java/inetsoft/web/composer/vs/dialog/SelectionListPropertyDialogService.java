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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
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
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;

@Service
@ClusterProxy
public class SelectionListPropertyDialogService {

   public SelectionListPropertyDialogService(VSObjectPropertyService vsObjectPropertyService,
                                             VSOutputService vsOutputService,
                                             ViewsheetService viewsheetService,
                                             VSTrapService trapService,
                                             VSDialogService dialogService,
                                             SelectionDialogService selectionDialogService,
                                             VSAssemblyInfoHandler assemblyInfoHandler,
                                             DataRefModelFactoryService dataRefService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.vsOutputService = vsOutputService;
      this.viewsheetService = viewsheetService;
      this.trapService = trapService;
      this.dialogService = dialogService;
      this.selectionDialogService = selectionDialogService;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.dataRefService = dataRefService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public SelectionListPropertyDialogModel getSelectionListPropertyModel(@ClusterProxyKey String runtimeId,
                                                                         String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      SelectionListVSAssembly selectionListAssembly;
      SelectionListVSAssemblyInfo selectionListAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         selectionListAssembly = (SelectionListVSAssembly) vs.getAssembly(objectId);
         selectionListAssemblyInfo = (SelectionListVSAssemblyInfo) selectionListAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         throw e;
      }

      SelectionListPropertyDialogModel result = new SelectionListPropertyDialogModel();
      SelectionGeneralPaneModel selectionGeneralPane = result.getSelectionGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel = selectionGeneralPane.getTitlePropPaneModel();
      GeneralPropPaneModel generalPropPaneModel = selectionGeneralPane.getGeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         selectionGeneralPane.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      SelectionListPaneModel selectionListPaneModel = result.getSelectionListPaneModel();
      SelectionMeasurePaneModel selectionMeasurePaneModel = selectionListPaneModel.getSelectionMeasurePaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel = VSAssemblyScriptPaneModel.builder();

      boolean inSelectionContainer =
         selectionListAssembly.getContainer() instanceof CurrentSelectionVSAssembly;
      selectionGeneralPane.setInSelectionContainer(inSelectionContainer);
      titlePropPaneModel.setVisible(selectionListAssemblyInfo.getTitleVisibleValue());
      titlePropPaneModel.setTitle(selectionListAssemblyInfo.getTitleValue());

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(selectionListAssemblyInfo.getEnabledValue());

      Point pos = dialogService.getAssemblyPosition(selectionListAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(selectionListAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setTitleHeight(selectionListAssemblyInfo.getTitleHeightValue());
      sizePositionPaneModel.setContainer(selectionListAssembly.getContainer() != null);

      int cellHeight = selectionListAssemblyInfo.getCellHeight();
      sizePositionPaneModel.setCellHeight(cellHeight <= 0 ? AssetUtil.defh : cellHeight);

      basicGeneralPaneModel.setName(selectionListAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(selectionListAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(selectionListAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(vs, selectionListAssemblyInfo.getAbsoluteName()));

      selectionGeneralPane.setShowType(selectionListAssemblyInfo.getShowTypeValue());
      selectionGeneralPane.setListHeight(selectionListAssemblyInfo.getListHeight());
      selectionGeneralPane.setSortType(selectionListAssemblyInfo.getSortTypeValue());
      selectionGeneralPane.setSubmitOnChange(selectionListAssemblyInfo.getSubmitOnChangeValue());
      selectionGeneralPane.setSingleSelection(selectionListAssemblyInfo.getSingleSelectionValue());
      selectionGeneralPane.setSuppressBlank(selectionListAssemblyInfo.isSuppressBlankValue());
      selectionGeneralPane.setSelectFirstItem(selectionListAssemblyInfo.getSelectFirstItemValue());

      for(Assembly assembly : vs.getAssemblies()) {
         if(assembly instanceof CurrentSelectionVSAssembly) {
            CurrentSelectionVSAssembly currentAssembly = (CurrentSelectionVSAssembly) assembly;
            CurrentSelectionVSAssemblyInfo currentAssInfo =
               (CurrentSelectionVSAssemblyInfo) currentAssembly.getVSAssemblyInfo();
            String[] childAsses = currentAssInfo.getAssemblies();

            for(String childAss: childAsses) {
               if(selectionListAssemblyInfo.getAbsoluteName().equals(childAss)) {
                  selectionGeneralPane.setShowNonContainerProps(false);
                  break;
               }
            }
         }
      }

      final String selectedTableName = selectionListAssemblyInfo.getFirstTableName();
      selectionListPaneModel.setSelectedTable(selectedTableName);
      selectionListPaneModel.setAdditionalTables(selectionListAssemblyInfo.getAdditionalTableNames());
      final TreeNodeModel tree = this.vsOutputService.getSelectionTablesTree(rvs, principal);
      selectionListPaneModel.setTargetTree(tree);
      selectionListPaneModel.setGrayedOutFields(assemblyInfoHandler.getGrayedOutFields(rvs));
      ColumnRef columnRef = (ColumnRef) selectionListAssemblyInfo.getDataRef();

      if(selectedTableName != null && columnRef != null) {
         final Optional<OutputColumnRefModel> refModel = selectionDialogService
            .findSelectedOutputColumnRefModel(tree, selectedTableName, columnRef);
         refModel.ifPresent(selectionListPaneModel::setSelectedColumn);
      }

      selectionMeasurePaneModel.setMeasure(selectionListAssemblyInfo.getMeasureValue());
      selectionMeasurePaneModel.setFormula(selectionListAssemblyInfo.getFormulaValue());
      selectionMeasurePaneModel.setShowText(selectionListAssemblyInfo.isShowTextValue());
      selectionMeasurePaneModel.setShowBar(selectionListAssemblyInfo.isShowBarValue());

      vsAssemblyScriptPaneModel.scriptEnabled(selectionListAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(selectionListAssemblyInfo.getScript() == null ?
                                              "" : selectionListAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setSelectionListPropertyModel(@ClusterProxyKey String runtimeId, String objectId,
                                             SelectionListPropertyDialogModel value, String linkUri,
                                             Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs;
      SelectionListVSAssembly selectionListAssembly;
      SelectionListVSAssemblyInfo selectionListAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         selectionListAssembly = (SelectionListVSAssembly) rvs.getViewsheet().getAssembly(objectId);
         selectionListAssemblyInfo = (SelectionListVSAssemblyInfo) Tool.clone(selectionListAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         throw e;
      }

      SelectionGeneralPaneModel selectionGeneralPane = value.getSelectionGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel = selectionGeneralPane.getTitlePropPaneModel();
      GeneralPropPaneModel generalPropPaneModel = selectionGeneralPane.getGeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         selectionGeneralPane.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      SelectionListPaneModel selectionListPaneModel = value.getSelectionListPaneModel();
      SelectionMeasurePaneModel selectionMeasurePaneModel = selectionListPaneModel.getSelectionMeasurePaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      selectionListAssemblyInfo.setTitleVisibleValue(titlePropPaneModel.isVisible());
      selectionListAssemblyInfo.setTitleValue(titlePropPaneModel.getTitle());

      selectionListAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());

      dialogService.setAssemblySize(selectionListAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(selectionListAssemblyInfo, sizePositionPaneModel);

      selectionListAssemblyInfo.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());
      selectionListAssemblyInfo.setCellHeight(sizePositionPaneModel.getCellHeight());

      selectionListAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      selectionListAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());

      int showType = selectionGeneralPane.getShowType();

      Dimension size = rvs.getViewsheet().getPixelSize(selectionListAssemblyInfo);

      if(showType == SelectionListVSAssemblyInfo.DROPDOWN_SHOW_TYPE) {
         size.height = selectionListAssemblyInfo.getTitleHeight();
      }
      else if(showType != selectionListAssemblyInfo.getShowTypeValue()){
         size.height = selectionListAssemblyInfo.getTitleHeight() +
            selectionListAssemblyInfo.getListHeight() *
               selectionListAssemblyInfo.getCellHeight();
      }

      selectionListAssemblyInfo.setShowTypeValue(showType);
      selectionListAssemblyInfo.setListHeight(selectionGeneralPane.getListHeight());
      selectionListAssemblyInfo.setSortTypeValue(selectionGeneralPane.getSortType());
      selectionListAssemblyInfo.setSubmitOnChangeValue(selectionGeneralPane.isSubmitOnChange());
      selectionListAssemblyInfo.setSingleSelectionValue(selectionGeneralPane.isSingleSelection());
      selectionListAssemblyInfo.setSuppressBlankValue(selectionGeneralPane.isSuppressBlank());
      selectionListAssemblyInfo.setSelectFirstItemValue(selectionGeneralPane.isSelectFirstItem());

      setAssemblyInfoTables(selectionListAssemblyInfo, selectionListPaneModel);
      setAssemblyInfoDataRefs(selectionListAssemblyInfo, selectionListPaneModel);
      setAssemblyInfoMeasure(selectionListAssemblyInfo, selectionMeasurePaneModel);

      selectionListAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      selectionListAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         rvs, selectionListAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkVSTrap(@ClusterProxyKey String runtimeId, SelectionListPropertyDialogModel value,
                                       String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      SelectionListVSAssembly selectionListAssembly =
         (SelectionListVSAssembly) rvs.getViewsheet().getAssembly(objectId);

      if(selectionListAssembly == null) {
         return VSTableTrapModel.builder()
            .showTrap(false)
            .build();
      }

      VSAssemblyInfo oldAssemblyInfo = (VSAssemblyInfo) Tool.clone(selectionListAssembly.getVSAssemblyInfo());
      SelectionListVSAssemblyInfo newAssemblyInfo =
         (SelectionListVSAssemblyInfo) Tool.clone(selectionListAssembly.getVSAssemblyInfo());

      SelectionListPaneModel selectionListPaneModel = value.getSelectionListPaneModel();
      SelectionMeasurePaneModel selectionMeasurePaneModel = selectionListPaneModel.getSelectionMeasurePaneModel();

      setAssemblyInfoTables(newAssemblyInfo, selectionListPaneModel);
      setAssemblyInfoDataRefs(newAssemblyInfo, selectionListPaneModel);
      setAssemblyInfoMeasure(newAssemblyInfo, selectionMeasurePaneModel);

      rvs.getViewsheet().getAssembly(objectId).setVSAssemblyInfo(newAssemblyInfo);
      VSTableTrapModel trap = trapService.checkTrap(rvs, oldAssemblyInfo, newAssemblyInfo);
      rvs.getViewsheet().getAssembly(objectId).setVSAssemblyInfo(oldAssemblyInfo);

      return trap;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public List<DataRefModel> getGrayedOutFields(@ClusterProxyKey String runtimeId,
                                                SelectionListPropertyDialogModel value,
                                                String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      SelectionListVSAssembly selectionListAssembly =
         (SelectionListVSAssembly) rvs.getViewsheet().getAssembly(objectId);

      if(selectionListAssembly == null) {
         return null;
      }

      VSAssemblyInfo oldAssemblyInfo = (VSAssemblyInfo) Tool.clone(selectionListAssembly.getVSAssemblyInfo());
      SelectionListVSAssemblyInfo newAssemblyInfo =
         (SelectionListVSAssemblyInfo) Tool.clone(selectionListAssembly.getVSAssemblyInfo());

      SelectionListPaneModel selectionListPaneModel = value.getSelectionListPaneModel();
      SelectionMeasurePaneModel selectionMeasurePaneModel = selectionListPaneModel.getSelectionMeasurePaneModel();

      setAssemblyInfoTables(newAssemblyInfo, selectionListPaneModel);
      setAssemblyInfoDataRefs(newAssemblyInfo, selectionListPaneModel);
      setAssemblyInfoMeasure(newAssemblyInfo, selectionMeasurePaneModel);

      return getGrayedFields(rvs, oldAssemblyInfo, newAssemblyInfo);
   }

   private java.util.List<DataRefModel> getGrayedFields(RuntimeViewsheet rvs,
                                                        VSAssemblyInfo oinfo,
                                                        VSAssemblyInfo ninfo)
   {
      VSModelTrapContext context = new VSModelTrapContext(rvs, true);
      context.checkTrap(oinfo, ninfo);
      DataRef[] refs = context.getGrayedFields();
      List<DataRefModel> fields = new ArrayList<>();

      for(DataRef ref : refs) {
         DataRefModel model = dataRefService.createDataRefModel(ref);
         fields.add(model);
      }

      return fields;
   }

   private void setAssemblyInfoTables(SelectionListVSAssemblyInfo info,
                                      SelectionListPaneModel model)
   {
      info.setFirstTableName(model.getSelectedTable());
      info.setAdditionalTableNames(model.getAdditionalTables());
   }

   private void setAssemblyInfoDataRefs(SelectionListVSAssemblyInfo info,
                                        SelectionListPaneModel model)
   {
      OutputColumnRefModel selectedColumn = model.getSelectedColumn();

      if(selectedColumn != null && selectedColumn.getAttribute() != null &&
         !selectedColumn.getAttribute().isEmpty())
      {
         AttributeRef aRef = new AttributeRef(selectedColumn.getEntity(), selectedColumn.getAttribute());
         aRef.setRefType(selectedColumn.getRefType());
         ColumnRef cRef = new ColumnRef(aRef);
         cRef.setDataType(selectedColumn.getDataType() == null ? XSchema.STRING : selectedColumn.getDataType());
         cRef.setAlias(selectedColumn.getAlias());
         info.setDataRef(cRef);
      }
      else {
         info.setDataRef(null);
      }
   }

   private void setAssemblyInfoMeasure(SelectionListVSAssemblyInfo info,
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


   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSOutputService vsOutputService;
   private final ViewsheetService viewsheetService;
   private final VSTrapService trapService;
   private final VSDialogService dialogService;
   private final SelectionDialogService selectionDialogService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final DataRefModelFactoryService dataRefService;
}
