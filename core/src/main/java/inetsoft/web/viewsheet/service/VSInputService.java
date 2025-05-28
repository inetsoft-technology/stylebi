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
package inetsoft.web.viewsheet.service;


import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.InputScriptEvent;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.internal.table.*;
import inetsoft.report.script.viewsheet.*;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.event.VSOnClickEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.DataInputController;
import inetsoft.web.composer.vs.objects.controller.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.*;
import inetsoft.web.vswizard.model.VSWizardConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.text.Format;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

@Service
@ClusterProxy
public class VSInputService {

   public VSInputService(VSObjectService vsObjectService,
                         CoreLifecycleService coreLifecycleService,
                         ViewsheetService viewsheetService,
                         VSObjectPropertyService vsObjectPropertyService,
                         VSDialogService dialogService,
                         VSTrapService trapService,
                         DataRefModelFactoryService dataRefModelFactoryService,
                         VSAssemblyInfoHandler vsAssemblyInfoHandler,
                         VSColumnHandler vsColumnHandler)
   {
      this.vsObjectService = vsObjectService;
      this.coreLifecycleService = coreLifecycleService;
      this.viewsheetService = viewsheetService;
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.dialogService = dialogService;
      this.trapService = trapService;
      this.dataRefModelFactoryService = dataRefModelFactoryService;
      this.vsAssemblyInfoHandler = vsAssemblyInfoHandler;
      this.vsColumnHandler = vsColumnHandler;
   }

   /**
    * Apply selection.
    *
    * @param assemblyName   the name of the selection assembly
    * @param selectedObject the selected object
    * @param principal      a principal identifying the current user.
    * @param dispatcher     the command dispatcher.
    * @param linkUri        the link URI
    *
    * @throws Exception if the selection could not be applied.
    */
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void singleApplySelection(@ClusterProxyKey String vsId, String assemblyName, Object selectedObject,
                                    Principal principal, CommandDispatcher dispatcher,
                                    @LinkUri String linkUri) throws Exception
   {
      final int hint = this.applySelection(vsId, assemblyName, selectedObject, principal, dispatcher);
      refreshVS(vsId, principal, dispatcher, getOldCrosstabInfo(vsId, principal),
                new String[]{ assemblyName }, new Object[]{ selectedObject }, new int[]{ hint },
                linkUri);
      // keep the 'event' object until onLoad is called. (62609)
      detachScriptEvent(vsId, principal);

      return null;
   }

   private void detachScriptEvent(String vsId, Principal principal) throws Exception {
      RuntimeViewsheet rvs =
         vsObjectService.getRuntimeViewsheet(vsId, principal);
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.detachScriptEvent();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void multiApplySelection(@ClusterProxyKey String vsId, String[] assemblyNames, Object[] selectedObjects, Principal principal,
                                   CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      int[] hints = new int[assemblyNames.length];
      Map<String, VSAssemblyInfo> oldCrosstabInfo = getOldCrosstabInfo(vsId, principal);

      for(int i = 0; i < assemblyNames.length; i++) {
         hints[i] = applySelection(vsId, assemblyNames[i], selectedObjects[i],
                                   principal, dispatcher);
      }

      refreshVS(vsId, principal, dispatcher, oldCrosstabInfo, assemblyNames,
                selectedObjects, hints, linkUri);
      // keep the 'event' object until onLoad is called. (62609)
      detachScriptEvent(vsId, principal);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void applySelection(@ClusterProxyKey String vsId, VSListInputSelectionEvent event,
                              Principal principal, CommandDispatcher dispatcher,
                              @LinkUri String linkUri)
      throws Exception
   {
      final String assemblyName = event.assemblyName();
      final RuntimeViewsheet rvs = vsObjectService.getRuntimeViewsheet(vsId, principal);
      final Viewsheet viewsheet = rvs.getViewsheet();
      final ComboBoxVSAssembly assembly = (ComboBoxVSAssembly) viewsheet.getAssembly(assemblyName);
      final ComboBoxVSAssemblyInfo info = (ComboBoxVSAssemblyInfo) assembly.getVSAssemblyInfo();
      Object selectedValue = event.value();

      try {
         if(info.isCalendar() && selectedValue != null && !"".equals(selectedValue)) {
            try {
               selectedValue = new Date(Long.parseLong(selectedValue.toString()));
            }
            catch(Exception ex) {
               String fmt = info.getFormat().getFormat();
               String spec = info.getFormat().getFormatExtent();
               Format format = TableFormat.getFormat(fmt, spec);

               if(format != null) {
                  selectedValue = format.parseObject(selectedValue.toString());
               }
               else {
                  throw ex;
               }
            }
         }
      }
      catch(Exception e) {
         MessageCommand command = new MessageCommand();
         command.setMessage(e.getMessage());
         command.setType(MessageCommand.Type.ERROR);
         dispatcher.sendCommand(command);
         return null;
      }

      singleApplySelection(vsId, assemblyName, selectedValue, principal, dispatcher, linkUri);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String setDetailHeight(@ClusterProxyKey String runtimeId, String objectId,
                                 double height, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      VSAssembly assembly;
      ListInputVSAssemblyInfo assemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         assembly = vs.getAssembly(objectId);
         assemblyInfo = (ListInputVSAssemblyInfo) assembly.getVSAssemblyInfo();
         assemblyInfo.setCellHeight((int) height);
      }
      catch(Exception e) {
         throw e;
      }

      return null;
   }


   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void onConfirm(@ClusterProxyKey String vsId, String name, String x, String y, boolean isConfirm,
                         VSOnClickEvent confirmEvent, String linkUri, Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      ViewsheetScope scope = box.getScope();
      List<UserMessage> usrmsg = new ArrayList<>();

      //Bug #21607 on confirm event, first execute script to get the correct message
      if(confirmEvent.confirmed()) {
         try {
            scope.execute("confirmEvent.confirmed = true", ViewsheetScope.VIEWSHEET_SCRIPTABLE);
         }
         catch(Exception ignore) {
         }

         onClick(vsId, name, x, y, linkUri, isConfirm, usrmsg, principal, dispatcher);
      }

      //set confirmEvent.confirmed back to false
      if(isConfirm) {
         try {
            scope.execute("confirmEvent.confirmed = false", ViewsheetScope.VIEWSHEET_SCRIPTABLE);
         }
         catch(Exception e) {
         }
      }

      //pop up confirm dialog
      String cmsg = Tool.getConfirmMessage();
      Tool.clearConfirmMessage();

      if(cmsg != null) {
         MessageCommand cmd = new MessageCommand();
         cmd.setMessage(cmsg);
         cmd.setType(MessageCommand.Type.CONFIRM);
         VSOnClickEvent event = new VSOnClickEvent();
         event.setConfirmEvent(true);
         cmd.addEvent("/events/onclick/" + name + "/" + x +
                         "/" + y + "/" + true, event);
         dispatcher.sendCommand(cmd);
      }

      if(!usrmsg.isEmpty()) {
         final UserMessage userMessage = usrmsg.get(0);

         if(userMessage != null) {
            dispatcher.sendCommand(MessageCommand.fromUserMessage(userMessage));
         }
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void onClick(@ClusterProxyKey String vsId, String name, String x, String y, VSSubmitEvent submitEvent,
                       String linkUri, Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      if(submitEvent != null && submitEvent.values() != null) {
         final InputValue[] inputValues = submitEvent.values();

         if(inputValues != null) {
            final String[] assemblyNames = Arrays.stream(inputValues)
               .map(InputValue::assemblyName)
               .toArray(String[]::new);
            final Object[] selectedObjects = Arrays.stream(inputValues)
               .map(InputValue::value)
               .toArray();
            multiApplySelection(vsId, assemblyNames,
                                                       selectedObjects, principal, dispatcher, linkUri);
         }
      }

      onClick(vsId, name, x, y, linkUri, false, null, principal, dispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public CheckboxPropertyDialogModel getCheckBoxPropertyModel(@ClusterProxyKey String runtimeId,
                                                               String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      CheckBoxVSAssembly checkBoxAssembly;
      CheckBoxVSAssemblyInfo checkBoxAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         checkBoxAssembly = (CheckBoxVSAssembly) vs.getAssembly(objectId);
         checkBoxAssemblyInfo = (CheckBoxVSAssemblyInfo) checkBoxAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         throw e;
      }

      CheckboxPropertyDialogModel result = new CheckboxPropertyDialogModel();
      CheckboxGeneralPaneModel checkBoxGeneralPaneModel =
         result.getCheckboxGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel =
         checkBoxGeneralPaneModel.getTitlePropPaneModel();
      GeneralPropPaneModel generalPropPaneModel =
         checkBoxGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel =
         generalPropPaneModel.getBasicGeneralPaneModel();
      ListValuesPaneModel listValuesPaneModel =
         checkBoxGeneralPaneModel.getListValuesPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         checkBoxGeneralPaneModel.getSizePositionPaneModel();
      ComboBoxEditorModel checkBoxEditorModel =
         listValuesPaneModel.getComboBoxEditorModel();
      SelectionListDialogModel selectionListDialogModel =
         checkBoxEditorModel.getSelectionListDialogModel();
      SelectionListEditorModel selectionListEditorModel =
         selectionListDialogModel.getSelectionListEditorModel();
      VariableListDialogModel variableListDialogModel =
         checkBoxEditorModel.getVariableListDialogModel();
      DataInputPaneModel dataInputPaneModel = result.getDataInputPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel =
         VSAssemblyScriptPaneModel.builder();

      titlePropPaneModel.setVisible(checkBoxAssemblyInfo.getTitleVisibleValue());
      titlePropPaneModel.setTitle(checkBoxAssemblyInfo.getTitleValue());

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(checkBoxAssemblyInfo.getEnabledValue());
      generalPropPaneModel.setShowSubmitCheckbox(true);
      generalPropPaneModel.setSubmitOnChange(
         Boolean.valueOf(checkBoxAssemblyInfo.getSubmitOnChangeValue()));

      basicGeneralPaneModel.setName(checkBoxAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(checkBoxAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(checkBoxAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, checkBoxAssemblyInfo.getAbsoluteName()));
      basicGeneralPaneModel.setRefresh(checkBoxAssemblyInfo.isRefresh());

      listValuesPaneModel.setSortType(checkBoxAssemblyInfo.getSortTypeValue());
      listValuesPaneModel.setEmbeddedDataDown(
         checkBoxAssemblyInfo.isEmbeddedDataDownValue());
      listValuesPaneModel.setSelectFirstItem(checkBoxAssemblyInfo.getSelectFirstItemValue());

      Point pos = dialogService.getAssemblyPosition(checkBoxAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(checkBoxAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setTitleHeight(checkBoxAssemblyInfo.getTitleHeightValue());
      sizePositionPaneModel.setContainer(checkBoxAssembly.getContainer() != null);

      int cellHeight = checkBoxAssemblyInfo.getCellHeight();
      sizePositionPaneModel.setCellHeight(cellHeight <= 0 ? AssetUtil.defh : cellHeight);

      ListData listData = checkBoxAssemblyInfo.getListData() == null ?
         new ListData() : checkBoxAssemblyInfo.getListData();
      checkBoxEditorModel.setDataType(checkBoxAssemblyInfo.getEmbeddedDataType());

      switch(checkBoxAssemblyInfo.getSourceType()) {
      case ListInputVSAssembly.EMBEDDED_SOURCE:
         checkBoxEditorModel.setEmbedded(true);
         break;
      case ListInputVSAssembly.BOUND_SOURCE:
         checkBoxEditorModel.setQuery(true);
         break;
      case ListInputVSAssembly.MERGE_SOURCE:
         checkBoxEditorModel.setEmbedded(true);
         checkBoxEditorModel.setQuery(true);
         break;
      }

      // keep consistent of the data type
      if(!listData.getDataType().equals(checkBoxAssemblyInfo.getEmbeddedDataType())) {
         listData.setDataType(checkBoxAssemblyInfo.getEmbeddedDataType());
      }

      List<String> values = new ArrayList<>();
      String dtype = listData.getDataType();

      for(Object val : listData.getValues()) {
         String valueString = val == null ? null : Tool.getDataString(val, dtype);
         values.add(valueString);
      }

      variableListDialogModel.setDataType(dtype);
      variableListDialogModel.setLabels(listData.getLabels());
      variableListDialogModel.setValues(values.toArray(new String[0]));

      ListBindingInfo listBindingInfo = checkBoxAssemblyInfo.getListBindingInfo();
      List<String[]> tablesList = this.getInputTablesArray(rvs, principal);
      selectionListEditorModel.setTables(tablesList.get(0));
      selectionListEditorModel.setLocalizedTables(tablesList.get(1));
      selectionListEditorModel.setLTablesDescription(tablesList.get(2));
      selectionListEditorModel.setForm(checkBoxAssemblyInfo.isForm());

      if(listBindingInfo != null) {
         selectionListEditorModel.setTable(listBindingInfo.getTableName());
         selectionListEditorModel.setColumn(
            listBindingInfo.getLabelColumn() == null ?
               "" : listBindingInfo.getLabelColumn().getName());
         selectionListEditorModel.setValue(
            listBindingInfo.getValueColumn() == null ?
               "" : listBindingInfo.getValueColumn().getName());
         selectionListEditorModel.setDataType(
            listBindingInfo.getDataType());
      }

      getTableName(checkBoxAssemblyInfo, dataInputPaneModel);
      dataInputPaneModel.setTargetTree(getInputTablesTree(rvs, true, principal));

      vsAssemblyScriptPaneModel.scriptEnabled(checkBoxAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(checkBoxAssemblyInfo.getScript() == null ?
                                              "" : checkBoxAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setCheckboxPropertyModel(@ClusterProxyKey String vsId, String objectId,
                                        CheckboxPropertyDialogModel value,String linkUri,
                                        Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      CheckBoxVSAssemblyInfo checkBoxAssemblyInfo;

      try {
         ViewsheetService engine = viewsheetService;
         viewsheet = engine.getViewsheet(vsId, principal);
         CheckBoxVSAssembly checkBoxAssembly = (CheckBoxVSAssembly)
            viewsheet.getViewsheet().getAssembly(objectId);
         checkBoxAssemblyInfo = (CheckBoxVSAssemblyInfo)
            Tool.clone(checkBoxAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         throw e;
      }

      CheckboxGeneralPaneModel checkBoxGeneralPaneModel = value.getCheckboxGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel = checkBoxGeneralPaneModel.getTitlePropPaneModel();
      GeneralPropPaneModel generalPropPaneModel =
         checkBoxGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         checkBoxGeneralPaneModel.getSizePositionPaneModel();
      DataInputPaneModel dataInputPaneModel = value.getDataInputPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      checkBoxAssemblyInfo.setTitleVisibleValue(titlePropPaneModel.isVisible());
      checkBoxAssemblyInfo.setTitleValue(titlePropPaneModel.getTitle());

      checkBoxAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());
      checkBoxAssemblyInfo.setSubmitOnChangeValue(generalPropPaneModel.isSubmitOnChange() + "");

      checkBoxAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      checkBoxAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());
      checkBoxAssemblyInfo.setRefreshValue(basicGeneralPaneModel.isRefresh() + "");

      dialogService.setAssemblySize(checkBoxAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(checkBoxAssemblyInfo, sizePositionPaneModel);

      checkBoxAssemblyInfo.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());
      checkBoxAssemblyInfo.setCellHeight(sizePositionPaneModel.getCellHeight());

      setListValues(checkBoxAssemblyInfo, value, viewsheet, principal);

      // TODO validate column/row variable/expression type
      String table = dataInputPaneModel.getTable();
      checkBoxAssemblyInfo.setTableName(
         table == null || "".equals(table.trim()) ? null : table);
      checkBoxAssemblyInfo.setVariable(table != null && dataInputPaneModel.isVariable());

      checkBoxAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      checkBoxAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, checkBoxAssemblyInfo, objectId, basicGeneralPaneModel.getName(),
         linkUri, principal, commandDispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkTrap(@ClusterProxyKey String runtimeId, CheckboxPropertyDialogModel model,
                                     String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet viewsheet = viewsheetService.getViewsheet(runtimeId, principal);
      CheckBoxVSAssembly checkBoxVSAssembly =
         (CheckBoxVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);

      if(checkBoxVSAssembly == null) {
         return VSTableTrapModel.builder()
            .showTrap(false)
            .build();
      }

      VSAssemblyInfo oldAssemblyInfo =
         (VSAssemblyInfo) Tool.clone(checkBoxVSAssembly.getVSAssemblyInfo());
      CheckBoxVSAssemblyInfo newAssemblyInfo =
         (CheckBoxVSAssemblyInfo) Tool.clone(checkBoxVSAssembly.getVSAssemblyInfo());

      setListValues(newAssemblyInfo, model, viewsheet, principal);

      return trapService.checkTrap(viewsheet, oldAssemblyInfo, newAssemblyInfo);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSSortingDialogModel getVSSortingDialogModel(@ClusterProxyKey String runtimeId, String objectId,
                                                       Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalcTableVSAssembly assembly = (CalcTableVSAssembly) viewsheet.getAssembly(objectId);
      CalcTableVSAssemblyInfo assemblyInfo = (CalcTableVSAssemblyInfo) assembly.getVSAssemblyInfo();
      SourceInfo sourceInfo = assemblyInfo.getSourceInfo();
      SortInfo sortInfo = assemblyInfo.getSortInfo();
      ColumnSelection columnSelection = null;
      SortRef[] sortRefs = sortInfo == null ? new SortRef[0] : sortInfo.getSorts();
      List<VSSortRefModel> columnSortList = new ArrayList<>();
      List<VSSortRefModel> columnNoneList = new ArrayList<>();
      DataRef[] allColumns = new DataRef[0];

      if(sourceInfo != null && sourceInfo.getSource() != null) {
         columnSelection = vsColumnHandler.getTableColumns(rvs, sourceInfo.getSource(), null,
                                                           null, null, false,
                                                           false, true, false,
                                                           false, true, principal);
         allColumns = (DataRef[]) Collections.list(columnSelection.getAttributes()).toArray(new DataRef[0]);
      }

      for(SortRef sortRef : sortRefs) {
         if(columnSelection == null || !containsColumn(sortRef.getDataRef(), allColumns)) {
            continue;
         }

         VSSortRefModel sortRefModel = new VSSortRefModel();
         sortRefModel.setDataRefModel(dataRefModelFactoryService.createDataRefModel(sortRef.getDataRef()));
         sortRefModel.setOrder(sortRef.getOrder());
         columnSortList.add(sortRefModel);
      }

      if(columnSelection != null) {
         for(int i = 0; i < columnSelection.getAttributeCount(); i++) {
            DataRef ref = columnSelection.getAttribute(i);

            if(containsColumn(ref, sortRefs)) {
               continue;
            }

            VSSortRefModel sortRefModel = new VSSortRefModel();
            sortRefModel.setDataRefModel(dataRefModelFactoryService.createDataRefModel(ref));
            sortRefModel.setOrder(StyleConstants.SORT_NONE);
            columnNoneList.add(sortRefModel);
         }
      }

      VSSortingDialogModel vsSortingDialogModel = new VSSortingDialogModel();
      VSSortingPaneModel vsSortingPaneModel = vsSortingDialogModel.getVsSortingPaneModel();
      vsSortingPaneModel.setColumnSortList(columnSortList.toArray(new VSSortRefModel[0]));
      vsSortingPaneModel.setColumnNoneList(columnNoneList.toArray(new VSSortRefModel[0]));

      return vsSortingDialogModel;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setVSSortingDialogModel(@ClusterProxyKey String vsId, String objectId,
                                       VSSortingDialogModel model,Principal principal,
                                       CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs =
         vsObjectService.getRuntimeViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalcTableVSAssembly assembly = (CalcTableVSAssembly) viewsheet.getAssembly(objectId);
      CalcTableVSAssemblyInfo assemblyInfo = (CalcTableVSAssemblyInfo) assembly.getVSAssemblyInfo();
      SortInfo sortInfo = new SortInfo();

      for(VSSortRefModel vsSortRefModel : model.getVsSortingPaneModel().getColumnSortList()) {
         SortRef sortRef = new SortRef();
         DataRefModel dataRefModel = vsSortRefModel.getDataRefModel();
         sortRef.setDataRef(dataRefModel.createDataRef());
         sortRef.setOrder(vsSortRefModel.getOrder());
         sortInfo.addSort(sortRef);
      }

      assemblyInfo.setSortInfo(sortInfo);
      this.vsAssemblyInfoHandler.apply(rvs, assemblyInfo, viewsheetService, false, false, true, false, dispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public TextInputPropertyDialogModel getTextInputPropertyDialogModel(@ClusterProxyKey String runtimeId,
                                                                       String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      TextInputVSAssembly textInputAssembly;
      TextInputVSAssemblyInfo textInputAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         textInputAssembly = (TextInputVSAssembly) vs.getAssembly(objectId);
         textInputAssemblyInfo = (TextInputVSAssemblyInfo) textInputAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         throw e;
      }

      TextInputPropertyDialogModel result = new TextInputPropertyDialogModel();
      TextInputGeneralPaneModel textInputGeneralPaneModel = result.getTextInputGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel = textInputGeneralPaneModel.getGeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         textInputGeneralPaneModel.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      DataInputPaneModel dataInputPaneModel = result.getDataInputPaneModel();
      TextInputColumnOptionPaneModel textInputColumnOptionPaneModel = result.getTextInputColumnOptionPaneModel();
      ClickableScriptPaneModel.Builder clickableScriptPaneModel = ClickableScriptPaneModel.builder();

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(textInputAssemblyInfo.getEnabledValue());
      generalPropPaneModel.setShowSubmitCheckbox(true);
      generalPropPaneModel.setSubmitOnChange(Boolean.valueOf(textInputAssemblyInfo.getSubmitOnChangeValue()));

      Point pos = dialogService.getAssemblyPosition(textInputAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(textInputAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setContainer(textInputAssembly.getContainer() != null);

      basicGeneralPaneModel.setName(textInputAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(textInputAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(textInputAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(
         this.vsObjectPropertyService.getObjectNames(vs, textInputAssemblyInfo.getAbsoluteName()));
      basicGeneralPaneModel.setRefresh(textInputAssemblyInfo.isRefresh());

      textInputGeneralPaneModel.setToolTip(textInputAssemblyInfo.getToolTipValue());
      textInputGeneralPaneModel.setDefaultText(textInputAssemblyInfo.getDefaultTextValue());
      textInputGeneralPaneModel.setInsetStyle(textInputAssemblyInfo.isInsetStyle());
      textInputGeneralPaneModel.setMultiLine(textInputAssemblyInfo.isMultiline());

      getTableName(textInputAssemblyInfo, dataInputPaneModel);
      dataInputPaneModel.setColumnValue(textInputAssemblyInfo.getColumnValue());
      dataInputPaneModel.setRowValue(textInputAssemblyInfo.getRowValue());
      dataInputPaneModel.setTargetTree(getInputTablesTree(rvs, false, principal));
      dataInputPaneModel.setWriteBackDirectly(textInputAssemblyInfo.getWriteBackValue());

      ColumnOption columnOption = textInputAssemblyInfo.getColumnOption();
      String optionType = columnOption.getType();
      textInputColumnOptionPaneModel.setType(optionType);

      if(optionType.equals(ColumnOption.TEXT)) {
         TextEditorModel textEditorModel = textInputColumnOptionPaneModel.getTextEditorModel();
         textEditorModel.setPattern(((TextColumnOption) columnOption).getPattern());
         textEditorModel.setErrorMessage(columnOption.getMessage());
      }
      else if(optionType.equals(ColumnOption.DATE)) {
         DateEditorModel dateEditorModel = textInputColumnOptionPaneModel.getDateEditorModel();
         dateEditorModel.setMinimum(((DateColumnOption) columnOption).getMin());
         dateEditorModel.setMaximum(((DateColumnOption) columnOption).getMax());
         dateEditorModel.setErrorMessage(columnOption.getMessage());
      }
      else if(optionType.equals(ColumnOption.INTEGER)) {
         IntegerEditorModel integerEditorModel = textInputColumnOptionPaneModel.getIntegerEditorModel();
         IntegerColumnOption integerColumnOption = (IntegerColumnOption) columnOption;
         Integer min = integerColumnOption.getMin() == Integer.MIN_VALUE ?
            null : integerColumnOption.getMin();
         Integer max = integerColumnOption.getMax() == Integer.MAX_VALUE ?
            null : integerColumnOption.getMax();
         integerEditorModel.setMinimum(min);
         integerEditorModel.setMaximum(max);
         integerEditorModel.setErrorMessage(columnOption.getMessage());
      }
      else if(optionType.equals(ColumnOption.FLOAT)) {
         FloatEditorModel floatEditorModel = textInputColumnOptionPaneModel.getFloatEditorModel();
         FloatColumnOption floatColumnOption = (FloatColumnOption) columnOption;
         Float min = floatColumnOption.getMin() == null ?
            null : Float.parseFloat(floatColumnOption.getMin());
         Float max = floatColumnOption.getMax() == null ?
            null : Float.parseFloat(floatColumnOption.getMax());
         floatEditorModel.setMinimum(min);
         floatEditorModel.setMaximum(max);
         floatEditorModel.setErrorMessage(columnOption.getMessage());
      }
      else if(optionType.equals(ColumnOption.PASSWORD)) {
         TextEditorModel passwordEditorModel =
            textInputColumnOptionPaneModel.getPasswordEditorModel();
         passwordEditorModel.setPattern(
            ((PasswordColumnOption) columnOption).getPattern());
         passwordEditorModel.setErrorMessage(columnOption.getMessage());
      }

      clickableScriptPaneModel.scriptEnabled(textInputAssemblyInfo.isScriptEnabled());
      String script = textInputAssemblyInfo.getScript() == null ? "" : textInputAssemblyInfo.getScript();
      String onClick = textInputAssemblyInfo.getOnClick() == null ? "" :textInputAssemblyInfo.getOnClick();
      clickableScriptPaneModel.scriptExpression(script);
      clickableScriptPaneModel.onClickExpression(onClick);
      result.setClickableScriptPaneModel(clickableScriptPaneModel.build());

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setTextInputPropertyDialogModel(@ClusterProxyKey String vsId, String objectId,
                                               TextInputPropertyDialogModel value, String linkUri,
                                               Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet viewsheet;
      TextInputVSAssemblyInfo textInputAssemblyInfo;

      try {
         viewsheet = viewsheetService.getViewsheet(vsId, principal);
         TextInputVSAssembly textInputAssembly = (TextInputVSAssembly)
            viewsheet.getViewsheet().getAssembly(objectId);
         textInputAssemblyInfo = (TextInputVSAssemblyInfo)
            Tool.clone(textInputAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         throw e;
      }

      TextInputGeneralPaneModel textInputGeneralPaneModel = value.getTextInputGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel =
         textInputGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         textInputGeneralPaneModel.getSizePositionPaneModel();
      DataInputPaneModel dataInputPaneModel = value.getDataInputPaneModel();
      TextInputColumnOptionPaneModel textInputColumnOptionPaneModel =
         value.getTextInputColumnOptionPaneModel();
      ClickableScriptPaneModel clickableScriptPaneModel = value.getClickableScriptPaneModel();

      textInputAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());
      textInputAssemblyInfo.setSubmitOnChangeValue(generalPropPaneModel.isSubmitOnChange() + "");

      textInputAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      textInputAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());
      textInputAssemblyInfo.setRefreshValue(basicGeneralPaneModel.isRefresh() + "");

      dialogService.setAssemblySize(textInputAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(textInputAssemblyInfo, sizePositionPaneModel);

      textInputAssemblyInfo.setToolTipValue(textInputGeneralPaneModel.getToolTip());
      textInputAssemblyInfo.setDefaultTextValue(textInputGeneralPaneModel.getDefaultText());
      textInputAssemblyInfo.setInsetStyle(textInputGeneralPaneModel.isInsetStyle());
      textInputAssemblyInfo.setMultiline(textInputGeneralPaneModel.isMultiLine());

      String table = dataInputPaneModel.getTable();
      textInputAssemblyInfo.setTableName(table == null ? "" : table);
      textInputAssemblyInfo.setColumnValue(dataInputPaneModel.getColumnValue());
      textInputAssemblyInfo.setRowValue(dataInputPaneModel.getRowValue());
      textInputAssemblyInfo.setVariable(table != null && table.startsWith("$(") &&
                                           table.endsWith(")"));
      textInputAssemblyInfo.setWriteBackValue(dataInputPaneModel.isWriteBackDirectly());

      String optionType = textInputColumnOptionPaneModel.getType();

      if(optionType.equals(ColumnOption.TEXT)) {
         TextEditorModel textEditorModel = textInputColumnOptionPaneModel.getTextEditorModel();
         TextColumnOption textColumnOption = new TextColumnOption(textEditorModel.getPattern(),
                                                                  textEditorModel.getErrorMessage(),
                                                                  true);
         textInputAssemblyInfo.setColumnOption(textColumnOption);
      }
      else if(optionType.equals(ColumnOption.DATE)) {
         DateEditorModel dateEditorModel = textInputColumnOptionPaneModel.getDateEditorModel();
         DateColumnOption dateColumnOption = new DateColumnOption(dateEditorModel.getMaximum(),
                                                                  dateEditorModel.getMinimum(),
                                                                  dateEditorModel.getErrorMessage(),
                                                                  true);
         textInputAssemblyInfo.setColumnOption(dateColumnOption);
      }
      else if(optionType.equals(ColumnOption.INTEGER)) {
         IntegerEditorModel integerEditorModel =
            textInputColumnOptionPaneModel.getIntegerEditorModel();
         IntegerColumnOption integerColumnOption = new IntegerColumnOption(
            integerEditorModel.getMaximum(),
            integerEditorModel.getMinimum(),
            integerEditorModel.getErrorMessage(),
            true);
         textInputAssemblyInfo.setColumnOption(integerColumnOption);
      }
      else if(optionType.equals(ColumnOption.FLOAT)) {
         FloatEditorModel floatEditorModel = textInputColumnOptionPaneModel.getFloatEditorModel();
         String max = floatEditorModel.getMaximum() == null ?
            null : floatEditorModel.getMaximum() + "";
         String min = floatEditorModel.getMinimum() == null ?
            null : floatEditorModel.getMinimum() + "";
         FloatColumnOption floatColumnOption =
            new FloatColumnOption(max, min, floatEditorModel.getErrorMessage(), true);
         textInputAssemblyInfo.setColumnOption(floatColumnOption);
      }
      else if(optionType.equals(ColumnOption.PASSWORD)) {
         TextEditorModel passwordEditorModel =
            textInputColumnOptionPaneModel.getPasswordEditorModel();
         PasswordColumnOption passwordColumnOption = new PasswordColumnOption(
            passwordEditorModel.getPattern(),
            passwordEditorModel.getErrorMessage(), true);
         textInputAssemblyInfo.setColumnOption(passwordColumnOption);
      }

      textInputAssemblyInfo.setScriptEnabled(clickableScriptPaneModel.scriptEnabled());
      textInputAssemblyInfo.setScript(clickableScriptPaneModel.scriptExpression());
      textInputAssemblyInfo.setOnClick(clickableScriptPaneModel.onClickExpression());

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, textInputAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public SliderPropertyDialogModel getSliderPropertyDialogModel(@ClusterProxyKey String runtimeId,
                                                                 String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      SliderVSAssembly sliderAssembly;
      SliderVSAssemblyInfo sliderAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         sliderAssembly = (SliderVSAssembly) vs.getAssembly(objectId);
         sliderAssemblyInfo = (SliderVSAssemblyInfo) sliderAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         throw e;
      }

      SliderPropertyDialogModel result = new SliderPropertyDialogModel();
      SliderGeneralPaneModel sliderGeneralPaneModel = result.getSliderGeneralPaneModel();
      NumericRangePaneModel numericRangePaneModel = sliderGeneralPaneModel.getNumericRangePaneModel();
      GeneralPropPaneModel generalPropPaneModel = sliderGeneralPaneModel.getGeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         sliderGeneralPaneModel.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      DataInputPaneModel dataInputPaneModel = result.getDataInputPaneModel();
      SliderAdvancedPaneModel sliderAdvancedPaneModel = result.getSliderAdvancedPaneModel();
      SliderLabelPaneModel sliderLabelPaneModel = sliderAdvancedPaneModel.getSliderLabelPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel = VSAssemblyScriptPaneModel.builder();

      numericRangePaneModel.setMinimum(sliderAssemblyInfo.getMinValue());
      numericRangePaneModel.setMaximum(sliderAssemblyInfo.getMaxValue());
      numericRangePaneModel.setIncrement(sliderAssemblyInfo.getIncrementValue());

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(sliderAssemblyInfo.getEnabledValue());
      generalPropPaneModel.setShowSubmitCheckbox(true);
      generalPropPaneModel.setSubmitOnChange(Boolean.valueOf(sliderAssemblyInfo.getSubmitOnChangeValue()));

      Point pos = dialogService.getAssemblyPosition(sliderAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(sliderAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setContainer(sliderAssembly.getContainer() != null);

      basicGeneralPaneModel.setName(sliderAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(sliderAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(sliderAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(
         this.vsObjectPropertyService.getObjectNames(vs, sliderAssemblyInfo.getAbsoluteName()));
      basicGeneralPaneModel.setRefresh(sliderAssemblyInfo.isRefresh());

      getTableName(sliderAssemblyInfo, dataInputPaneModel);
      dataInputPaneModel.setColumnValue(sliderAssemblyInfo.getColumnValue());
      dataInputPaneModel.setRowValue(sliderAssemblyInfo.getRowValue());
      dataInputPaneModel.setTargetTree(getInputTablesTree(rvs, false, principal));
      dataInputPaneModel.setWriteBackDirectly(sliderAssemblyInfo.getWriteBackValue());

      sliderLabelPaneModel.setShowLabel(true);
      sliderLabelPaneModel.setTick(sliderAssemblyInfo.getTickVisibleValue());
      sliderLabelPaneModel.setCurrentValue(sliderAssemblyInfo.getCurrentVisibleValue());
      sliderLabelPaneModel.setLabel(sliderAssemblyInfo.getLabelVisibleValue());
      sliderLabelPaneModel.setMinimum(sliderAssemblyInfo.getMinVisibleValue());
      sliderLabelPaneModel.setMaximum(sliderAssemblyInfo.getMaxVisibleValue());

      sliderAdvancedPaneModel.setSnap(sliderAssemblyInfo.isSnap());

      vsAssemblyScriptPaneModel.scriptEnabled(sliderAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(sliderAssemblyInfo.getScript() == null ?
                                              "" : sliderAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setSliderPropertyDialogModel(@ClusterProxyKey String vsId, String objectId,
                                            SliderPropertyDialogModel value,String linkUri, Principal principal,
                                            CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet viewsheet;
      SliderVSAssemblyInfo sliderAssemblyInfo;

      try {
         viewsheet = viewsheetService.getViewsheet(vsId, principal);
         SliderVSAssembly sliderAssembly = (SliderVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         sliderAssemblyInfo = (SliderVSAssemblyInfo) Tool.clone(sliderAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         throw e;
      }

      SliderGeneralPaneModel sliderGeneralPaneModel = value.getSliderGeneralPaneModel();
      NumericRangePaneModel numericRangePaneModel = sliderGeneralPaneModel.getNumericRangePaneModel();
      GeneralPropPaneModel generalPropPaneModel = sliderGeneralPaneModel.getGeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         sliderGeneralPaneModel.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      DataInputPaneModel dataInputPaneModel = value.getDataInputPaneModel();
      SliderAdvancedPaneModel sliderAdvancedPaneModel = value.getSliderAdvancedPaneModel();
      SliderLabelPaneModel sliderLabelPaneModel = sliderAdvancedPaneModel.getSliderLabelPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      sliderAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());
      sliderAssemblyInfo.setSubmitOnChangeValue(generalPropPaneModel.isSubmitOnChange() + "");
      sliderAssemblyInfo.setRefreshValue(basicGeneralPaneModel.isRefresh() + "");

      dialogService.setAssemblySize(sliderAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(sliderAssemblyInfo, sizePositionPaneModel);

      sliderAssemblyInfo.setMinValue(numericRangePaneModel.getMinimum());
      sliderAssemblyInfo.setMaxValue(numericRangePaneModel.getMaximum());

      if(sliderAssemblyInfo.getSelectedObject() instanceof Number &&
         ((Number) sliderAssemblyInfo.getSelectedObject()).doubleValue() < sliderAssemblyInfo.getMin()){
         sliderAssemblyInfo.setSelectedObject(sliderAssemblyInfo.getMin());
      }

      if(sliderAssemblyInfo.getSelectedObject() instanceof Number &&
         ((Number) sliderAssemblyInfo.getSelectedObject()).doubleValue() > sliderAssemblyInfo.getMax()){
         sliderAssemblyInfo.setSelectedObject(sliderAssemblyInfo.getMax());
      }

      sliderAssemblyInfo.setIncrementValue(numericRangePaneModel.getIncrement());

      sliderAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      sliderAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());

      String table = dataInputPaneModel.getTable();
      sliderAssemblyInfo.setTableName(table == null ? "" : table);
      sliderAssemblyInfo.setColumnValue(dataInputPaneModel.getColumnValue());
      sliderAssemblyInfo.setRowValue(dataInputPaneModel.getRowValue());
      sliderAssemblyInfo.setVariable(table != null && table.startsWith("$(") && table.endsWith(")"));
      sliderAssemblyInfo.setWriteBackValue(dataInputPaneModel.isWriteBackDirectly());

      sliderAssemblyInfo.setTickVisibleValue(sliderLabelPaneModel.isTick());
      sliderAssemblyInfo.setCurrentVisibleValue(sliderLabelPaneModel.isCurrentValue());
      sliderAssemblyInfo.setLabelVisibleValue(sliderLabelPaneModel.isLabel());
      sliderAssemblyInfo.setMinVisibleValue(sliderLabelPaneModel.isMinimum());
      sliderAssemblyInfo.setMaxVisibleValue(sliderLabelPaneModel.isMaximum());

      sliderAssemblyInfo.setSnapValue(sliderAdvancedPaneModel.isSnap());

      sliderAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      sliderAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, sliderAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public RadioButtonPropertyDialogModel getRadioButtonPropertyModel(@ClusterProxyKey String runtimeId,
                                                                     String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      RadioButtonVSAssemblyInfo radioButtonAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         RadioButtonVSAssembly radioButtonAssembly = (RadioButtonVSAssembly) vs.getAssembly(objectId);
         radioButtonAssemblyInfo = (RadioButtonVSAssemblyInfo) radioButtonAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         throw e;
      }

      RadioButtonPropertyDialogModel result = new RadioButtonPropertyDialogModel();
      RadioButtonGeneralPaneModel radioButtonGeneralPaneModel =
         result.getRadioButtonGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel =
         radioButtonGeneralPaneModel.getTitlePropPaneModel();
      GeneralPropPaneModel generalPropPaneModel =
         radioButtonGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel =
         generalPropPaneModel.getBasicGeneralPaneModel();
      ListValuesPaneModel listValuesPaneModel =
         radioButtonGeneralPaneModel.getListValuesPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         radioButtonGeneralPaneModel.getSizePositionPaneModel();
      ComboBoxEditorModel radioButtonEditorModel =
         listValuesPaneModel.getComboBoxEditorModel();
      SelectionListDialogModel selectionListDialogModel =
         radioButtonEditorModel.getSelectionListDialogModel();
      SelectionListEditorModel selectionListEditorModel =
         selectionListDialogModel.getSelectionListEditorModel();
      VariableListDialogModel variableListDialogModel =
         radioButtonEditorModel.getVariableListDialogModel();
      DataInputPaneModel dataInputPaneModel = result.getDataInputPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel =
         VSAssemblyScriptPaneModel.builder();

      titlePropPaneModel.setVisible(radioButtonAssemblyInfo.getTitleVisibleValue());
      titlePropPaneModel.setTitle(radioButtonAssemblyInfo.getTitleValue());

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(radioButtonAssemblyInfo.getEnabledValue());
      generalPropPaneModel.setShowSubmitCheckbox(true);
      generalPropPaneModel.setSubmitOnChange(
         Boolean.valueOf(radioButtonAssemblyInfo.getSubmitOnChangeValue()));

      basicGeneralPaneModel.setName(radioButtonAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(radioButtonAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(radioButtonAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, radioButtonAssemblyInfo.getAbsoluteName()));
      basicGeneralPaneModel.setRefresh(radioButtonAssemblyInfo.isRefresh());

      listValuesPaneModel.setSortType(radioButtonAssemblyInfo.getSortTypeValue());
      listValuesPaneModel.setEmbeddedDataDown(
         radioButtonAssemblyInfo.isEmbeddedDataDownValue());

      Point pos = dialogService.getAssemblyPosition(radioButtonAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(radioButtonAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setTitleHeight(radioButtonAssemblyInfo.getTitleHeightValue());
      int cellHeight = radioButtonAssemblyInfo.getCellHeight();
      sizePositionPaneModel.setCellHeight(cellHeight <= 0 ? AssetUtil.defh : cellHeight);

      ListData listData = radioButtonAssemblyInfo.getListData() == null ?
         new ListData() : radioButtonAssemblyInfo.getListData();
      radioButtonEditorModel.setDataType(radioButtonAssemblyInfo.getEmbeddedDataType());

      switch(radioButtonAssemblyInfo.getSourceType()) {
      case ListInputVSAssembly.EMBEDDED_SOURCE:
         radioButtonEditorModel.setEmbedded(true);
         break;
      case ListInputVSAssembly.BOUND_SOURCE:
         radioButtonEditorModel.setQuery(true);
         break;
      case ListInputVSAssembly.MERGE_SOURCE:
         radioButtonEditorModel.setEmbedded(true);
         radioButtonEditorModel.setQuery(true);
         break;
      }

      // keep consistent of the data type
      if(!Objects.equals(listData.getDataType(), radioButtonAssemblyInfo.getEmbeddedDataType())) {
         listData.setDataType(radioButtonAssemblyInfo.getEmbeddedDataType());
      }

      List<String> values = new ArrayList<>();
      String dtype = listData.getDataType();

      for(Object val : listData.getValues()) {
         values.add(val == null ? null : Tool.getDataString(val));
      }

      variableListDialogModel.setDataType(dtype);
      variableListDialogModel.setLabels(listData.getLabels());
      variableListDialogModel.setValues(values.toArray(new String[0]));

      ListBindingInfo listBindingInfo = radioButtonAssemblyInfo.getListBindingInfo();
      List<String[]> tablesList = getInputTablesArray(rvs, principal);
      selectionListEditorModel.setTables(tablesList.get(0));
      selectionListEditorModel.setLocalizedTables(tablesList.get(1));
      selectionListEditorModel.setLTablesDescription(tablesList.get(2));
      selectionListEditorModel.setForm(radioButtonAssemblyInfo.isForm());

      if(listBindingInfo != null) {
         selectionListEditorModel.setTable(listBindingInfo.getTableName());
         selectionListEditorModel.setColumn(
            listBindingInfo.getLabelColumn() == null ?
               "" : listBindingInfo.getLabelColumn().getName());
         selectionListEditorModel.setValue(
            listBindingInfo.getValueColumn() == null ?
               "" : listBindingInfo.getValueColumn().getName());
         selectionListEditorModel.setDataType(listBindingInfo.getDataType());
      }

      getTableName(radioButtonAssemblyInfo, dataInputPaneModel);
      dataInputPaneModel.setColumnValue(radioButtonAssemblyInfo.getColumnValue());
      dataInputPaneModel.setWriteBackDirectly(radioButtonAssemblyInfo.getWriteBackValue());
      dataInputPaneModel.setRowValue(radioButtonAssemblyInfo.getRowValue());
      dataInputPaneModel.setTargetTree(getInputTablesTree(rvs, false, principal));
      dataInputPaneModel.setVariable(radioButtonAssemblyInfo.isVariable());

      //set variable name
      if(dataInputPaneModel.isVariable()) {
         dataInputPaneModel.setTable(radioButtonAssemblyInfo.getTableName());
      }

      vsAssemblyScriptPaneModel.scriptEnabled(radioButtonAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(radioButtonAssemblyInfo.getScript() == null ?
                                              "" : radioButtonAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setRadioButtonPropertyModel(@ClusterProxyKey String vsId, String objectId,
                                           RadioButtonPropertyDialogModel value, String linkUri,
                                           Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet viewsheet;
      RadioButtonVSAssemblyInfo radioButtonAssemblyInfo;

      try {
         ViewsheetService engine = viewsheetService;
         viewsheet = engine.getViewsheet(vsId, principal);
         RadioButtonVSAssembly radioButtonAssembly = (RadioButtonVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         radioButtonAssemblyInfo = (RadioButtonVSAssemblyInfo) Tool.clone(radioButtonAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         throw e;
      }

      RadioButtonGeneralPaneModel radioButtonGeneralPaneModel = value.getRadioButtonGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel = radioButtonGeneralPaneModel.getTitlePropPaneModel();
      GeneralPropPaneModel generalPropPaneModel = radioButtonGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      ListValuesPaneModel listValuesPaneModel = radioButtonGeneralPaneModel.getListValuesPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         radioButtonGeneralPaneModel.getSizePositionPaneModel();
      ComboBoxEditorModel radioButtonEditorModel = listValuesPaneModel.getComboBoxEditorModel();
      SelectionListDialogModel selectionListDialogModel = radioButtonEditorModel.getSelectionListDialogModel();
      SelectionListEditorModel selectionListEditorModel = selectionListDialogModel.getSelectionListEditorModel();
      VariableListDialogModel variableListDialogModel = radioButtonEditorModel.getVariableListDialogModel();
      DataInputPaneModel dataInputPaneModel = value.getDataInputPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      radioButtonAssemblyInfo.setTitleVisibleValue(titlePropPaneModel.isVisible());
      radioButtonAssemblyInfo.setTitleValue(titlePropPaneModel.getTitle());

      radioButtonAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());
      radioButtonAssemblyInfo.setSubmitOnChangeValue(generalPropPaneModel.isSubmitOnChange() + "");

      radioButtonAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      radioButtonAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());
      radioButtonAssemblyInfo.setRefreshValue(basicGeneralPaneModel.isRefresh() + "");

      dialogService.setAssemblySize(radioButtonAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(radioButtonAssemblyInfo, sizePositionPaneModel);

      radioButtonAssemblyInfo.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());
      radioButtonAssemblyInfo.setCellHeight(sizePositionPaneModel.getCellHeight());

      setListValues(radioButtonAssemblyInfo, value, viewsheet, principal);

      String table = dataInputPaneModel.getTable();
      radioButtonAssemblyInfo.setTableName(table == null ? "" : table);
      radioButtonAssemblyInfo.setColumnValue(dataInputPaneModel.getColumnValue());
      radioButtonAssemblyInfo.setRowValue(dataInputPaneModel.getRowValue());
      radioButtonAssemblyInfo.setVariable(dataInputPaneModel.isVariable());
      radioButtonAssemblyInfo.setWriteBackValue(dataInputPaneModel.isWriteBackDirectly());

      radioButtonAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      radioButtonAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());

      if(radioButtonAssemblyInfo.getSelectedObject() == null &&
         radioButtonAssemblyInfo.getListData() != null &&
         radioButtonAssemblyInfo.getListData().getValues() != null &&
         radioButtonAssemblyInfo.getListData().getValues().length > 0)
      {
         radioButtonAssemblyInfo.setSelectedObject(
            radioButtonAssemblyInfo.getListData().getValues()[0]);
      }

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, radioButtonAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkTrap(@ClusterProxyKey String runtimeId, RadioButtonPropertyDialogModel model,
                                     String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      RadioButtonVSAssembly radioButtonVSAssembly =
         (RadioButtonVSAssembly) rvs.getViewsheet().getAssembly(objectId);

      if(radioButtonVSAssembly == null) {
         return VSTableTrapModel.builder()
            .showTrap(false)
            .build();
      }

      VSAssemblyInfo oldAssemblyInfo =
         (VSAssemblyInfo) Tool.clone(radioButtonVSAssembly.getVSAssemblyInfo());
      RadioButtonVSAssemblyInfo newAssemblyInfo =
         (RadioButtonVSAssemblyInfo) Tool.clone(radioButtonVSAssembly.getVSAssemblyInfo());

      setListValues(newAssemblyInfo, model, rvs, principal);

      return trapService.checkTrap(rvs, oldAssemblyInfo, newAssemblyInfo);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Map<String, String[]> getTableColumns(@ClusterProxyKey String runtimeId, String table,
                                                Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ColumnSelection selection = vsColumnHandler.getTableColumns(rvs,Tool.byteDecode(table),
                                                                  true, principal);
      String[] columnList = new String[selection.getAttributeCount()];
      String[] descriptionList = new String[selection.getAttributeCount()];

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         ColumnRef columnref = (ColumnRef) selection.getAttribute(i);
         columnList[i] = columnref.getName();
         descriptionList[i] = columnref.getDescription();
      }

      Map<String, String[]> result = new HashMap<>();
      result.put("columnlist", columnList);
      result.put("descriptionlist", descriptionList);

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String[] getColumnRows(@ClusterProxyKey String runtimeId, String table,
                                 String column, Principal principal) throws Exception
   {
      table = Tool.byteDecode(table);
      column = Tool.byteDecode(column);
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();

      if(column.startsWith("$")) {
         String variableAssembly = column.substring(2, column.length() - 1);

         InputVSAssembly assembly = (InputVSAssembly) viewsheet.getAssembly(variableAssembly);
         InputVSAssemblyInfo assemblyInfo = (InputVSAssemblyInfo) assembly.getVSAssemblyInfo();
         String selectedColumn = null;

         if(assemblyInfo instanceof CheckBoxVSAssemblyInfo) {
            Object[] objects =  assemblyInfo.getSelectedObjects();

            if(objects.length > 0) {
               selectedColumn = assemblyInfo.getSelectedObjects()[0].toString();
            }
         }
         else {
            selectedColumn = Objects.toString(assemblyInfo.getSelectedObject(), null);
         }

         return getColumnRows(rvs, table, selectedColumn, principal);
      }

      return getColumnRows(rvs, table, column, principal);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public DataInputController.PopupEmbeddedTable getPopupTable(@ClusterProxyKey String runtimeId,
                                                               String table, Principal principal) throws Exception
   {
      DataInputController.PopupEmbeddedTable result = new DataInputController.PopupEmbeddedTable();
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return result;
      }

      Worksheet ws = vs.getBaseWorksheet();

      if(ws != null && VSEventUtil.checkBaseWSPermission(
         vs, principal, engine.getAssetRepository(), ResourceAction.READ))
      {
         TableAssembly tableAssembly = (TableAssembly) ws.getAssembly(table);

         if(tableAssembly instanceof SnapshotEmbeddedTableAssembly) {
            result = new DataInputController.PopupEmbeddedTable(
               ((SnapshotEmbeddedTableAssembly)tableAssembly).getEmbeddedData(), table);
         }
         else if(tableAssembly instanceof EmbeddedTableAssembly) {
            result = new DataInputController.PopupEmbeddedTable(
               VSEventUtil.getVSEmbeddedData((EmbeddedTableAssembly) tableAssembly), table);
         }
      }

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ColumnOptionDialogModel getColumnOptionDialogModel(@ClusterProxyKey String runtimeId, String objectId,
                                                             Integer col,Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      TableVSAssembly assembly = (TableVSAssembly) viewsheet.getAssembly(objectId);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) assembly.getVSAssemblyInfo();

      ColumnSelection columnSelection = info.getVisibleColumns();
      ColumnRef columnRef = (ColumnRef) columnSelection.getAttribute(col);
      ColumnOptionDialogModel model = new ColumnOptionDialogModel();
      boolean isForm = false;

      if(columnRef instanceof FormRef) {
         FormRef form = (FormRef) columnRef;
         ColumnOption columnOption = form.getOption();
         model.setInputControl(columnOption.getType());
         model.setEnableColumnEditing(columnOption.isForm());

         EditorModel editor = null;

         if(columnOption instanceof TextColumnOption) {
            TextColumnOption textColumnOption = (TextColumnOption) columnOption;
            TextEditorModel textEditorModel = new TextEditorModel();
            textEditorModel.setPattern(textColumnOption.getPattern());
            textEditorModel.setErrorMessage(textColumnOption.getMessage());
            editor = textEditorModel;
         }
         else if(columnOption instanceof DateColumnOption) {
            DateColumnOption dateColumnOption = (DateColumnOption) columnOption;
            DateEditorModel dateEditorModel = new DateEditorModel();
            dateEditorModel.setMinimum(dateColumnOption.getMin());
            dateEditorModel.setMaximum(dateColumnOption.getMax());
            dateEditorModel.setErrorMessage(dateColumnOption.getMessage());
            editor = dateEditorModel;
         }
         else if(columnOption instanceof ComboBoxColumnOption) {
            ComboBoxColumnOption comboBoxColumnOption = (ComboBoxColumnOption) columnOption;
            ListBindingInfo listBindingInfo = comboBoxColumnOption.getListBindingInfo();
            ListData listData = comboBoxColumnOption.getListData() == null ?
               new ListData() : comboBoxColumnOption.getListData();

            boolean embedded = false;
            boolean query = false;

            switch(comboBoxColumnOption.getSourceType()) {
            case ListInputVSAssembly.EMBEDDED_SOURCE:
               embedded = true;
               break;
            case ListInputVSAssembly.BOUND_SOURCE:
               query = true;
               break;
            case ListInputVSAssembly.MERGE_SOURCE:
               embedded = true;
               query = true;
               break;
            }

            SelectionListDialogModel selectionListDialogModel = new SelectionListDialogModel();
            SelectionListEditorModel selectionListEditorModel = new SelectionListEditorModel();

            List<String[]> tablesList = getInputTablesArray(rvs, principal);
            selectionListEditorModel.setTables(tablesList.get(0));
            selectionListEditorModel.setLocalizedTables(tablesList.get(1));
            selectionListEditorModel.setForm(comboBoxColumnOption.isForm());

            if(listBindingInfo != null) {
               selectionListEditorModel.setTable(listBindingInfo.getTableName());
               selectionListEditorModel.setColumn(listBindingInfo.getLabelColumn() == null ?
                                                     "" : listBindingInfo.getLabelColumn().getName());
               selectionListEditorModel.setValue(listBindingInfo.getValueColumn() == null ?
                                                    "" : listBindingInfo.getValueColumn().getName());
            }

            selectionListDialogModel.setSelectionListEditorModel(selectionListEditorModel);
            VariableListDialogModel variableListDialogModel = new VariableListDialogModel();
            variableListDialogModel.setLabels(listData.getLabels());

            String dtype = listData.getDataType();
            List<String> values = new ArrayList<>();

            for(Object val : listData.getValues()) {
               String valueString = val == null ? null : Tool.getDataString(val, dtype);
               values.add(valueString);
            }

            variableListDialogModel.setValues(values.toArray(new String[0]));
            variableListDialogModel.setDataType(listData.getDataType());

            ComboBoxEditorModel comboBoxEditorModel = new ComboBoxEditorModel();
            comboBoxEditorModel.setEmbedded(embedded);
            comboBoxEditorModel.setQuery(query);
            comboBoxEditorModel.setCalendar(false);
            comboBoxEditorModel.setDataType(comboBoxColumnOption.getDataType());
            comboBoxEditorModel.setSelectionListDialogModel(selectionListDialogModel);
            comboBoxEditorModel.setValid(true);
            comboBoxEditorModel.setVariableListDialogModel(variableListDialogModel);

            editor = comboBoxEditorModel;
         }
         else if(columnOption instanceof FloatColumnOption) {
            FloatColumnOption floatColumnOption = (FloatColumnOption) columnOption;
            FloatEditorModel floatEditorModel = new FloatEditorModel();
            Float min = floatColumnOption.getMin() == null ?
               null : Float.parseFloat(floatColumnOption.getMin());
            Float max = floatColumnOption.getMax() == null ?
               null : Float.parseFloat(floatColumnOption.getMax());
            floatEditorModel.setMinimum(min);
            floatEditorModel.setMaximum(max);
            floatEditorModel.setErrorMessage(floatColumnOption.getMessage());
            editor = floatEditorModel;
         }
         else if(columnOption instanceof IntegerColumnOption) {
            IntegerColumnOption integerColumnOption = (IntegerColumnOption) columnOption;
            IntegerEditorModel integerEditorModel = new IntegerEditorModel();
            Integer min = integerColumnOption.getMin() == Integer.MIN_VALUE ?
               null : integerColumnOption.getMin();
            Integer max = integerColumnOption.getMax() == Integer.MAX_VALUE ?
               null : integerColumnOption.getMax();
            integerEditorModel.setMinimum(min);
            integerEditorModel.setMaximum(max);
            integerEditorModel.setErrorMessage(integerColumnOption.getMessage());
            editor = integerEditorModel;
         }

         model.setEditor(editor);
         isForm = columnOption.isForm();
      }

      SelectionListDialogModel selectionListDialogModel = new SelectionListDialogModel();
      SelectionListEditorModel selectionListEditorModel = new SelectionListEditorModel();

      List<String[]> tablesList = getInputTablesArray(rvs, principal);
      selectionListEditorModel.setTables(tablesList.get(0));
      selectionListEditorModel.setLocalizedTables(tablesList.get(1));

      selectionListEditorModel.setTable(null);
      selectionListEditorModel.setColumn(null);
      selectionListEditorModel.setForm(isForm);
      selectionListEditorModel.setValue(null);

      selectionListDialogModel.setSelectionListEditorModel(selectionListEditorModel);

      VariableListDialogModel variableListDialogModel = new VariableListDialogModel();
      variableListDialogModel.setLabels(new String[0]);
      variableListDialogModel.setValues(new String[0]);
      variableListDialogModel.setDataType(columnRef.getDataType());

      ComboBoxEditorModel comboBoxBlankEditorModel = new ComboBoxEditorModel();
      comboBoxBlankEditorModel.setEmbedded(false);
      comboBoxBlankEditorModel.setQuery(false);
      comboBoxBlankEditorModel.setCalendar(false);
      comboBoxBlankEditorModel.setDataType(columnRef.getDataType());
      comboBoxBlankEditorModel.setSelectionListDialogModel(selectionListDialogModel);
      comboBoxBlankEditorModel.setValid(true);
      comboBoxBlankEditorModel.setVariableListDialogModel(variableListDialogModel);

      model.setComboBoxBlankEditor(comboBoxBlankEditorModel);

      return model;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setColumnOptionDialogModel(@ClusterProxyKey String vsId, String objectId, int col,
                                          ColumnOptionDialogModel model, Principal principal,
                                          CommandDispatcher dispatcher, String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      TableVSAssembly assembly = (TableVSAssembly) viewsheet.getAssembly(objectId);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) assembly.getVSAssemblyInfo();

      ColumnSelection cols = info.getColumnSelection();
      col = ComposerVSTableController.getBindingColIndex(cols, col);
      ColumnRef columnRef = (ColumnRef) cols.getAttribute(col);

      FormRef formRef;

      if(columnRef instanceof FormRef) {
         formRef = (FormRef) columnRef;
      }
      else {
         formRef = FormRef.toFormRef(columnRef);
      }

      if(model.isEnableColumnEditing()) {
         String type = model.getInputControl();

         if(ColumnOption.TEXT.equals(type)) {
            TextEditorModel columnOptionModel = (TextEditorModel) model.getEditor();
            TextColumnOption textColumnOption = new TextColumnOption(
               columnOptionModel.getPattern(), columnOptionModel.getErrorMessage(), true);
            formRef.setOption(textColumnOption);
         }
         else if(ColumnOption.BOOLEAN.equals(type)) {
            BooleanColumnOption booleanColumnOption = new BooleanColumnOption(true);
            formRef.setOption(booleanColumnOption);
         }
         else if(ColumnOption.INTEGER.equals(type)) {
            IntegerEditorModel columnOptionModel = (IntegerEditorModel) model.getEditor();
            IntegerColumnOption integerColumnOption = new IntegerColumnOption(
               columnOptionModel.getMaximum(), columnOptionModel.getMinimum(),
               columnOptionModel.getErrorMessage(), true);
            formRef.setOption(integerColumnOption);
         }
         else if(ColumnOption.FLOAT.equals(type)) {
            FloatEditorModel columnOptionModel = (FloatEditorModel) model.getEditor();
            String max = columnOptionModel.getMaximum() == null ?
               null : columnOptionModel.getMaximum() + "";
            String min = columnOptionModel.getMinimum() == null ?
               null : columnOptionModel.getMinimum() + "";
            FloatColumnOption floatColumnOption = new FloatColumnOption(
               max, min, columnOptionModel.getErrorMessage(), true);
            formRef.setOption(floatColumnOption);
         }
         else if(ColumnOption.DATE.equals(type)) {
            DateEditorModel columnOptionModel = (DateEditorModel) model.getEditor();
            DateColumnOption dateColumnOption = new DateColumnOption(
               columnOptionModel.getMaximum(), columnOptionModel.getMinimum(),
               columnOptionModel.getErrorMessage(), true);
            formRef.setOption(dateColumnOption);
         }
         else if(ColumnOption.COMBOBOX.equals(type)) {
            ComboBoxEditorModel editorModel = (ComboBoxEditorModel) model.getEditor();

            int sourceType = 0;

            if(editorModel.isEmbedded() && editorModel.isQuery()) {
               sourceType = ListInputVSAssembly.MERGE_SOURCE;
            }
            else if(editorModel.isEmbedded()) {
               sourceType = ListInputVSAssembly.EMBEDDED_SOURCE;
            }
            else if(editorModel.isQuery()) {
               sourceType = ListInputVSAssembly.BOUND_SOURCE;
            }

            ComboBoxColumnOption comboBoxColumnOption = new ComboBoxColumnOption(
               sourceType, editorModel.getDataType(), null, true);

            SelectionListEditorModel selectionListEditorModel =
               editorModel.getSelectionListDialogModel().getSelectionListEditorModel();

            if(editorModel.isEmbedded()) {
               VariableListDialogModel variableListDialogModel =
                  editorModel.getVariableListDialogModel();

               String dtype = variableListDialogModel.getDataType();
               List<Object> values = new ArrayList<>();

               for(String val : variableListDialogModel.getValues()) {
                  values.add(val == null ? null : Tool.getData(dtype, val, true));
               }

               ListData listData = new ListData();
               listData.setDataType(variableListDialogModel.getDataType());
               listData.setDataType(dtype);
               listData.setLabels(variableListDialogModel.getLabels());
               listData.setValues(values.toArray());
               comboBoxColumnOption.setListData(listData);
            }

            if(editorModel.isQuery()) {
               ListBindingInfo listBindingInfo = new ListBindingInfo();
               listBindingInfo.setTableName(selectionListEditorModel.getTable());
               listBindingInfo = updateBindingInfo(listBindingInfo, selectionListEditorModel.getColumn(),
                                                   selectionListEditorModel.getValue(), rvs, principal);
               comboBoxColumnOption.setListBindingInfo(listBindingInfo);
            }

            formRef.setOption(comboBoxColumnOption);
         }
      }
      else {
         TextColumnOption text = new TextColumnOption();
         formRef.setOption(text);
      }

      cols.setAttribute(col, formRef);

      int hint = VSAssembly.OUTPUT_DATA_CHANGED;
      this.coreLifecycleService
         .execute(rvs, assembly.getAbsoluteName(), linkUri, hint, dispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ComboboxPropertyDialogModel getComboboxPropertyDialogModel(@ClusterProxyKey String runtimeId,
                                                                     String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      ComboBoxVSAssembly comboBoxAssembly;
      ComboBoxVSAssemblyInfo comboBoxAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         comboBoxAssembly = (ComboBoxVSAssembly) vs.getAssembly(objectId);
         comboBoxAssemblyInfo = (ComboBoxVSAssemblyInfo) comboBoxAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         throw e;
      }

      ComboboxPropertyDialogModel result = new ComboboxPropertyDialogModel();
      ComboboxGeneralPaneModel comboboxGeneralPaneModel =
         result.getComboboxGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel =
         comboboxGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel =
         generalPropPaneModel.getBasicGeneralPaneModel();
      ListValuesPaneModel listValuesPaneModel =
         comboboxGeneralPaneModel.getListValuesPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         comboboxGeneralPaneModel.getSizePositionPaneModel();
      ComboBoxEditorModel comboBoxEditorModel =
         listValuesPaneModel.getComboBoxEditorModel();
      SelectionListDialogModel selectionListDialogModel =
         comboBoxEditorModel.getSelectionListDialogModel();
      SelectionListEditorModel selectionListEditorModel =
         selectionListDialogModel.getSelectionListEditorModel();
      VariableListDialogModel variableListDialogModel =
         comboBoxEditorModel.getVariableListDialogModel();
      DataInputPaneModel dataInputPaneModel = result.getDataInputPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel =
         VSAssemblyScriptPaneModel.builder();

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(comboBoxAssemblyInfo.getEnabledValue());
      generalPropPaneModel.setShowSubmitCheckbox(true);
      generalPropPaneModel.setSubmitOnChange(
         Boolean.valueOf(comboBoxAssemblyInfo.getSubmitOnChangeValue()));

      basicGeneralPaneModel.setName(comboBoxAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(comboBoxAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(comboBoxAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setShowEditableCheckbox(true);
      basicGeneralPaneModel.setEditable(comboBoxAssemblyInfo.isTextEditable());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, comboBoxAssemblyInfo.getAbsoluteName()));
      basicGeneralPaneModel.setRefresh("true".equals(comboBoxAssemblyInfo.getRefreshValue()));

      listValuesPaneModel.setSortType(comboBoxAssemblyInfo.getSortTypeValue());
      listValuesPaneModel.setEmbeddedDataDown(
         comboBoxAssemblyInfo.isEmbeddedDataDownValue());

      Point pos = dialogService.getAssemblyPosition(comboBoxAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(comboBoxAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setContainer(comboBoxAssembly.getContainer() != null);

      String minDate = comboBoxAssemblyInfo.getMinDateValue();
      String maxDate = comboBoxAssemblyInfo.getMaxDateValue();
      minDate = minDate == null ? "" : minDate;
      maxDate = maxDate == null ? "" : maxDate;

      ListData listData = comboBoxAssemblyInfo.getListData() == null ?
         new ListData() : comboBoxAssemblyInfo.getListData();
      comboBoxEditorModel.setDataType(comboBoxAssemblyInfo.getEmbeddedDataType());
      comboBoxEditorModel.setCalendar(comboBoxAssemblyInfo.isCalendar());
      comboBoxEditorModel.setServerTZ(comboBoxAssemblyInfo.isServerTimeZone());
      comboBoxEditorModel.setMinDate(minDate);
      comboBoxEditorModel.setMaxDate(maxDate);
      comboBoxEditorModel.setDefaultValue(comboBoxAssemblyInfo.getDefaultValue());

      switch(comboBoxAssemblyInfo.getSourceType()) {
      case ListInputVSAssembly.EMBEDDED_SOURCE:
         comboBoxEditorModel.setEmbedded(true);
         break;
      case ListInputVSAssembly.BOUND_SOURCE:
         comboBoxEditorModel.setQuery(true);
         break;
      case ListInputVSAssembly.MERGE_SOURCE:
         comboBoxEditorModel.setEmbedded(true);
         comboBoxEditorModel.setQuery(true);
         break;
      }

      List<String> values = new ArrayList<>();
      String dtype = comboBoxAssemblyInfo.getEmbeddedDataType();

      for(Object val : listData.getValues()) {
         String valueString = val == null ? null : Tool.getDataString(val, dtype);
         values.add(valueString);
      }

      variableListDialogModel.setDataType(dtype);
      variableListDialogModel.setLabels(listData.getLabels());
      variableListDialogModel.setValues(values.toArray(new String[0]));

      ListBindingInfo listBindingInfo = comboBoxAssemblyInfo.getListBindingInfo();
      List<String[]> tablesList = getInputTablesArray(rvs, principal);
      selectionListEditorModel.setTables(tablesList.get(0));
      selectionListEditorModel.setLocalizedTables(tablesList.get(1));
      selectionListEditorModel.setLTablesDescription(tablesList.get(2));
      selectionListEditorModel.setForm(comboBoxAssemblyInfo.isForm());

      if(listBindingInfo != null) {
         selectionListEditorModel.setTable(listBindingInfo.getTableName());
         selectionListEditorModel.setColumn(
            listBindingInfo.getLabelColumn() == null ?
               "" : listBindingInfo.getLabelColumn().getName());
         selectionListEditorModel.setValue(
            listBindingInfo.getValueColumn() == null ?
               "" : listBindingInfo.getValueColumn().getName());
         selectionListEditorModel.setDataType(listBindingInfo.getDataType());
      }

      getTableName(comboBoxAssemblyInfo, dataInputPaneModel);
      dataInputPaneModel.setColumnValue(comboBoxAssemblyInfo.getColumnValue());
      dataInputPaneModel.setRowValue(comboBoxAssemblyInfo.getRowValue());
      dataInputPaneModel.setTargetTree(getInputTablesTree(rvs, false, principal));
      dataInputPaneModel.setWriteBackDirectly(comboBoxAssemblyInfo.getWriteBackValue());
      vsAssemblyScriptPaneModel.scriptEnabled(comboBoxAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(comboBoxAssemblyInfo.getScript() == null ?
                                              "" : comboBoxAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }


   private void setListValues(RadioButtonVSAssemblyInfo assemblyInfo, RadioButtonPropertyDialogModel model,
                              RuntimeViewsheet rvs, Principal principal) throws Exception
   {
      RadioButtonGeneralPaneModel radioButtonGeneralPaneModel = model.getRadioButtonGeneralPaneModel();
      ListValuesPaneModel listValuesPaneModel = radioButtonGeneralPaneModel.getListValuesPaneModel();
      ComboBoxEditorModel radioButtonEditorModel = listValuesPaneModel.getComboBoxEditorModel();
      VariableListDialogModel variableListDialogModel = radioButtonEditorModel.getVariableListDialogModel();
      SelectionListDialogModel selectionListDialogModel = radioButtonEditorModel.getSelectionListDialogModel();
      SelectionListEditorModel selectionListEditorModel = selectionListDialogModel.getSelectionListEditorModel();

      assemblyInfo.setSortTypeValue(listValuesPaneModel.getSortType());
      assemblyInfo.setEmbeddedDataDownValue(listValuesPaneModel.isEmbeddedDataDown());

      ListData listData = new ListData();
      assemblyInfo.setDataType(radioButtonEditorModel.getDataType());

      if(radioButtonEditorModel.isEmbedded()) {
         if(radioButtonEditorModel.isQuery()) {
            assemblyInfo.setSourceType(ListInputVSAssembly.MERGE_SOURCE);
         }
         else {
            assemblyInfo.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
         }
      }
      else if(radioButtonEditorModel.isQuery()) {
         assemblyInfo.setSourceType(ListInputVSAssembly.BOUND_SOURCE);
      }
      else {
         assemblyInfo.setSourceType(ListInputVSAssembly.NONE_SOURCE);
      }

      String dtype = variableListDialogModel.getDataType();
      List<Object> values = new ArrayList<>();

      for(String val : variableListDialogModel.getValues()) {
         values.add(val == null ? null : Tool.getData(dtype, val, true));
      }

      listData.setDataType(dtype);
      listData.setLabels(variableListDialogModel.getLabels());
      listData.setValues(values.toArray());
      assemblyInfo.setListData(listData);

      assemblyInfo.setForm(selectionListEditorModel.isForm());
      ListBindingInfo listBindingInfo = new ListBindingInfo();
      listBindingInfo.setTableName(selectionListEditorModel.getTable());
      listBindingInfo = updateBindingInfo(listBindingInfo, selectionListEditorModel.getColumn(),
                                          selectionListEditorModel.getValue(), rvs, principal);
      assemblyInfo.setListBindingInfo(listBindingInfo);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setComboboxPropertyDialogModel(@ClusterProxyKey String vsId, String objectId,
                                              ComboboxPropertyDialogModel value, String linkUri,
                                              Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet viewsheet;
      ComboBoxVSAssemblyInfo comboBoxAssemblyInfo;

      try {
         ViewsheetService engine = viewsheetService;
         viewsheet = engine.getViewsheet(vsId, principal);
         ComboBoxVSAssembly comboBoxAssembly =
            (ComboBoxVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         comboBoxAssemblyInfo =
            (ComboBoxVSAssemblyInfo) Tool.clone(comboBoxAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         throw e;
      }

      ComboboxGeneralPaneModel comboboxGeneralPaneModel = value.getComboboxGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel = comboboxGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         comboboxGeneralPaneModel.getSizePositionPaneModel();
      DataInputPaneModel dataInputPaneModel = value.getDataInputPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      comboBoxAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());
      comboBoxAssemblyInfo.setSubmitOnChangeValue(generalPropPaneModel.isSubmitOnChange() + "");

      comboBoxAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      comboBoxAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());
      comboBoxAssemblyInfo.setTextEditable(basicGeneralPaneModel.isEditable());

      ListValuesPaneModel listValuesPaneModel =
         comboboxGeneralPaneModel.getListValuesPaneModel();
      ComboBoxEditorModel comboBoxEditorModel = listValuesPaneModel.getComboBoxEditorModel();
      int width = getWidth(comboBoxEditorModel.getDataType(), sizePositionPaneModel.getWidth(),
                           comboBoxAssemblyInfo.getDataType());
      sizePositionPaneModel.setWidth(width);
      dialogService.setAssemblySize(comboBoxAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(comboBoxAssemblyInfo, sizePositionPaneModel);

      setListValues(comboBoxAssemblyInfo, value, viewsheet, principal);

      // TODO validate column/row variable/expression type
      String table = dataInputPaneModel.getTable();
      comboBoxAssemblyInfo.setTableName(table == null ? "" : table);
      comboBoxAssemblyInfo.setColumnValue(dataInputPaneModel.getColumnValue());
      comboBoxAssemblyInfo.setRowValue(dataInputPaneModel.getRowValue());
      comboBoxAssemblyInfo.setVariable(
         table != null && table.startsWith("$(") && table.endsWith(")"));
      comboBoxAssemblyInfo.setWriteBackValue(dataInputPaneModel.isWriteBackDirectly());
      comboBoxAssemblyInfo.setRefreshValue(basicGeneralPaneModel.isRefresh() + "");

      comboBoxAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      comboBoxAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, comboBoxAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkTrap(@ClusterProxyKey String runtimeId, ComboboxPropertyDialogModel model,
                                     String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet viewsheet = viewsheetService.getViewsheet(runtimeId, principal);
      ComboBoxVSAssembly comboBoxVSAssembly =
         (ComboBoxVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);

      if(comboBoxVSAssembly == null) {
         return VSTableTrapModel.builder()
            .showTrap(false)
            .build();
      }

      VSAssemblyInfo oldAssemblyInfo =
         (VSAssemblyInfo) Tool.clone(comboBoxVSAssembly.getVSAssemblyInfo());
      ComboBoxVSAssemblyInfo newAssemblyInfo =
         (ComboBoxVSAssemblyInfo) Tool.clone(comboBoxVSAssembly.getVSAssemblyInfo());

      setListValues(newAssemblyInfo, model, viewsheet, principal);

      return trapService.checkTrap(viewsheet, oldAssemblyInfo, newAssemblyInfo);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ComboBoxDefaultValueListModel[] getComboBoxList(@ClusterProxyKey String runtimeId, String objectId,
                                                          int sortType, boolean embeddedDataDown,
                                                          ComboBoxEditorModel model, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId,principal);
      ViewsheetSandbox viewsheetSandbox = rvs.getViewsheetSandbox();
      ComboBoxVSAssembly comboBoxVSAssembly = (ComboBoxVSAssembly) viewsheetSandbox.getViewsheet().getAssembly(objectId);
      ComboBoxVSAssembly cassembly = (ComboBoxVSAssembly) comboBoxVSAssembly.clone();
      cassembly.getInfo().setName(VSWizardConstants.TEMP_ASSEMBLY_PREFIX + cassembly.getName());

      try {
         SelectionListEditorModel selectionListEditorModel = model.getSelectionListDialogModel().getSelectionListEditorModel();

         if(model.isQuery()) {
            ListBindingInfo listBindingInfo = new ListBindingInfo();
            listBindingInfo.setTableName(selectionListEditorModel.getTable());
            listBindingInfo = updateBindingInfo(listBindingInfo, selectionListEditorModel.getColumn(),
                                                selectionListEditorModel.getValue(), rvs, principal);
            cassembly.setListBindingInfo(listBindingInfo);
         }
         else {
            cassembly.setListBindingInfo(null);
         }

         if(model.isEmbedded()) {
            if(model.isQuery()) {
               cassembly.setSourceType(ListInputVSAssembly.MERGE_SOURCE);
            }
            else {
               cassembly.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
            }
         }
         else if(model.isQuery()) {
            cassembly.setSourceType(ListInputVSAssembly.BOUND_SOURCE);
         }
         else {
            cassembly.setSourceType(ListInputVSAssembly.NONE_SOURCE);
         }

         viewsheetSandbox.getViewsheet().addAssembly(cassembly);
         InputVSAQuery inputVSAQuery = new InputVSAQuery(viewsheetSandbox, cassembly.getName());
         VariableListDialogModel variableListDialogModel = model.getVariableListDialogModel();
         ListData data = new ListData();
         String dtype = variableListDialogModel.getDataType();
         cassembly.setDataType(dtype);
         List<Object> values = new ArrayList<>();

         for(String val : variableListDialogModel.getValues()) {
            values.add(val == null ? null : Tool.getData(dtype, val, true));
         }

         data.setDataType(dtype);
         data.setLabels(variableListDialogModel.getLabels());
         data.setValues(values.toArray());
         cassembly.setListData(data);

         if(inputVSAQuery.getData() instanceof ListData) {
            data = (ListData) inputVSAQuery.getData();
            ComboBoxDefaultValueListModel[] comboDefaultValueListModel =
               new ComboBoxDefaultValueListModel[data.getValues().length];
            cassembly.setSortType(sortType);
            cassembly.setEmbeddedDataDown(embeddedDataDown);
            cassembly.setValues(data.getValues());
            inputVSAQuery.refreshView(data);
            String[] stringLabel = data.getLabels();
            String[] formatValue = new String[data.getValues().length];

            for(int i = 0; i < formatValue.length; i++) {
               formatValue[i] = String.valueOf(data.getValues()[i]);
            }

            data.setBinding(true);
            data.setLabels(formatValue);
            cassembly.setSortType(XConstants.SORT_NONE);
            cassembly.setSourceType(ListInputVSAssembly.NONE_SOURCE);
            inputVSAQuery.refreshView(data);

            for(int i = 0; i < comboDefaultValueListModel.length; i++) {
               stringLabel[i] = Tool.isEmptyString(stringLabel[i]) ? "" : stringLabel[i];
               ComboBoxDefaultValueListModel comboBoxDefaultValueListModel =
                  new ComboBoxDefaultValueListModel(stringLabel[i], data.getLabels()[i], Tool.toString(cassembly.getValues()[i]));
               comboDefaultValueListModel[i] = comboBoxDefaultValueListModel;
            }

            return comboDefaultValueListModel;
         }
      }
      finally {
         viewsheetSandbox.getViewsheet().removeAssembly(cassembly);
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public SpinnerPropertyDialogModel getSpinnerPropertyDialogModel(@ClusterProxyKey String runtimeId,
                                                                   String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      SpinnerVSAssembly spinnerAssembly;
      SpinnerVSAssemblyInfo spinnerAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         spinnerAssembly = (SpinnerVSAssembly) vs.getAssembly(objectId);
         spinnerAssemblyInfo = (SpinnerVSAssemblyInfo) spinnerAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         throw e;
      }

      SpinnerPropertyDialogModel result = new SpinnerPropertyDialogModel();
      SpinnerGeneralPaneModel spinnerGeneralPaneModel = result.getSpinnerGeneralPaneModel();
      NumericRangePaneModel numericRangePaneModel = spinnerGeneralPaneModel.getNumericRangePaneModel();
      GeneralPropPaneModel generalPropPaneModel = spinnerGeneralPaneModel.getGeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         spinnerGeneralPaneModel.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      DataInputPaneModel dataInputPaneModel = result.getDataInputPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel = VSAssemblyScriptPaneModel.builder();

      numericRangePaneModel.setMinimum(spinnerAssemblyInfo.getMinValue());
      numericRangePaneModel.setMaximum(spinnerAssemblyInfo.getMaxValue());
      numericRangePaneModel.setIncrement(spinnerAssemblyInfo.getIncrementValue());

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(spinnerAssemblyInfo.getEnabledValue());
      generalPropPaneModel.setShowSubmitCheckbox(true);
      generalPropPaneModel.setSubmitOnChange(Boolean.valueOf(spinnerAssemblyInfo.getSubmitOnChangeValue()));

      Point pos = dialogService.getAssemblyPosition(spinnerAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(spinnerAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setContainer(spinnerAssembly.getContainer() != null);

      basicGeneralPaneModel.setName(spinnerAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(spinnerAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(spinnerAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(
         this.vsObjectPropertyService.getObjectNames(vs, spinnerAssemblyInfo.getAbsoluteName()));
      basicGeneralPaneModel.setRefresh(spinnerAssemblyInfo.isRefresh());

      getTableName(spinnerAssemblyInfo, dataInputPaneModel);
      dataInputPaneModel.setColumnValue(spinnerAssemblyInfo.getColumnValue());
      dataInputPaneModel.setRowValue(spinnerAssemblyInfo.getRowValue());
      dataInputPaneModel.setTargetTree(getInputTablesTree(rvs, false, principal));
      dataInputPaneModel.setWriteBackDirectly(spinnerAssemblyInfo.getWriteBackValue());

      vsAssemblyScriptPaneModel.scriptEnabled(spinnerAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(spinnerAssemblyInfo.getScript() == null ?
                                              "" : spinnerAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setSpinnerPropertyDialogModel(@ClusterProxyKey String vsId, String objectId,
                                             SpinnerPropertyDialogModel value, String linkUri,
                                             Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet viewsheet;
      SpinnerVSAssemblyInfo spinnerAssemblyInfo;

      try {
         viewsheet = viewsheetService.getViewsheet(vsId, principal);
         SpinnerVSAssembly spinnerAssembly = (SpinnerVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         spinnerAssemblyInfo = (SpinnerVSAssemblyInfo) Tool.clone(spinnerAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         throw e;
      }

      SpinnerGeneralPaneModel spinnerGeneralPaneModel = value.getSpinnerGeneralPaneModel();
      NumericRangePaneModel numericRangePaneModel = spinnerGeneralPaneModel.getNumericRangePaneModel();
      GeneralPropPaneModel generalPropPaneModel = spinnerGeneralPaneModel.getGeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         spinnerGeneralPaneModel.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      DataInputPaneModel dataInputPaneModel = value.getDataInputPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      spinnerAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());
      spinnerAssemblyInfo.setSubmitOnChangeValue(generalPropPaneModel.isSubmitOnChange() + "");
      spinnerAssemblyInfo.setRefreshValue(basicGeneralPaneModel.isRefresh() + "");

      dialogService.setAssemblySize(spinnerAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(spinnerAssemblyInfo, sizePositionPaneModel);

      spinnerAssemblyInfo.setMinValue(numericRangePaneModel.getMinimum());
      spinnerAssemblyInfo.setMaxValue(numericRangePaneModel.getMaximum());

      if(spinnerAssemblyInfo.getSelectedObject() instanceof Number &&
         ((Number) spinnerAssemblyInfo.getSelectedObject()).doubleValue() < spinnerAssemblyInfo.getMin()){
         spinnerAssemblyInfo.setSelectedObject(spinnerAssemblyInfo.getMin());
      }

      if(spinnerAssemblyInfo.getSelectedObject() instanceof Number &&
         ((Number) spinnerAssemblyInfo.getSelectedObject()).doubleValue() > spinnerAssemblyInfo.getMax()){
         spinnerAssemblyInfo.setSelectedObject(spinnerAssemblyInfo.getMax());
      }

      spinnerAssemblyInfo.setIncrementValue(numericRangePaneModel.getIncrement());

      spinnerAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      spinnerAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());

      String table = dataInputPaneModel.getTable();
      spinnerAssemblyInfo.setTableName(table == null ? "" : table);
      spinnerAssemblyInfo.setColumnValue(dataInputPaneModel.getColumnValue());
      spinnerAssemblyInfo.setRowValue(dataInputPaneModel.getRowValue());
      spinnerAssemblyInfo.setVariable(table != null && table.startsWith("$(") && table.endsWith(")"));
      spinnerAssemblyInfo.setWriteBackValue(dataInputPaneModel.isWriteBackDirectly());

      if(dataInputPaneModel.getDefaultValue() != null) {
         double dvalue = 0;

         try {
            Double.valueOf(dataInputPaneModel.getDefaultValue());
         }
         catch(Exception e) {
            //ignore it.
         }

         spinnerAssemblyInfo.setValue(dvalue);
      }

      spinnerAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      spinnerAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, spinnerAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);

      return null;
   }


   private void setListValues(ComboBoxVSAssemblyInfo assemblyInfo,
                              ComboboxPropertyDialogModel model,
                              RuntimeViewsheet viewsheet,
                              Principal principal)
      throws Exception
   {
      ComboboxGeneralPaneModel comboboxGeneralPaneModel = model.getComboboxGeneralPaneModel();
      ListValuesPaneModel listValuesPaneModel = comboboxGeneralPaneModel.getListValuesPaneModel();
      ComboBoxEditorModel comboBoxEditorModel = listValuesPaneModel.getComboBoxEditorModel();
      SelectionListDialogModel selectionListDialogModel =
         comboBoxEditorModel.getSelectionListDialogModel();
      SelectionListEditorModel selectionListEditorModel =
         selectionListDialogModel.getSelectionListEditorModel();
      VariableListDialogModel variableListDialogModel =
         comboBoxEditorModel.getVariableListDialogModel();

      assemblyInfo.setSortTypeValue(listValuesPaneModel.getSortType());
      assemblyInfo.setEmbeddedDataDownValue(listValuesPaneModel.isEmbeddedDataDown());

      ListData listData = new ListData();

      assemblyInfo.setDataType(comboBoxEditorModel.getDataType());
      assemblyInfo.setCalendar(comboBoxEditorModel.isCalendar());
      assemblyInfo.setServerTimeZone(comboBoxEditorModel.isServerTZ());
      String dataType = comboBoxEditorModel.getDataType();
      boolean isDate = Tool.equals(XSchema.DATE, dataType) ||
         Tool.equals(XSchema.TIME_INSTANT, dataType);
      String minDate = isDate ? comboBoxEditorModel.getMinDate() : null;
      String maxDate = isDate ? comboBoxEditorModel.getMaxDate() : null;
      assemblyInfo.setMinDateValue(minDate);
      assemblyInfo.setMaxDateValue(maxDate);
      assemblyInfo.setDefaultValue(comboBoxEditorModel.getDefaultValue());

      if(comboBoxEditorModel.isEmbedded()) {
         if(comboBoxEditorModel.isQuery()) {
            assemblyInfo.setSourceType(ListInputVSAssembly.MERGE_SOURCE);
         }
         else {
            assemblyInfo.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
         }
      }
      else if(comboBoxEditorModel.isQuery()) {
         assemblyInfo.setSourceType(ListInputVSAssembly.BOUND_SOURCE);
      }
      else {
         assemblyInfo.setSourceType(ListInputVSAssembly.NONE_SOURCE);
      }

      String dtype = variableListDialogModel.getDataType();
      List<Object> values = new ArrayList<>();

      for(String val : variableListDialogModel.getValues()) {
         values.add(val == null ? null : Tool.getData(dtype, val, true));
      }

      listData.setDataType(dtype);
      listData.setLabels(variableListDialogModel.getLabels());
      listData.setValues(values.toArray());
      assemblyInfo.setListData(listData);
      assemblyInfo.setForm(selectionListEditorModel.isForm());

      if(comboBoxEditorModel.isQuery()) {
         ListBindingInfo listBindingInfo = new ListBindingInfo();
         listBindingInfo.setTableName(selectionListEditorModel.getTable());
         listBindingInfo = updateBindingInfo(listBindingInfo, selectionListEditorModel.getColumn(),
                                             selectionListEditorModel.getValue(), viewsheet, principal);
         assemblyInfo.setListBindingInfo(listBindingInfo);
      }
      else {
         assemblyInfo.setListBindingInfo(null);
      }
   }

   private int getWidth(String dataType, int width, String odataType) {
      if(width == getDefaultWidth(odataType)) {
         return getDefaultWidth(dataType);
      }

      return width;
   }

   private static int getDefaultWidth(String dataType) {
      int defaultWidth = AssetUtil.defw;

      if(XSchema.isDateType(dataType)) {
         defaultWidth = XSchema.TIME_INSTANT.equals(dataType) ?
            ComboBoxVSAssemblyInfo.TIME_INSTANT_DEFAULT_WIDTH :
            ComboBoxVSAssemblyInfo.TIME_DEFAULT_WIDTH;
      }

      return defaultWidth;
   }




   private boolean containsColumn(DataRef ref, DataRef[] refArray) {
      for(DataRef ref0 : refArray) {
         if(ref0 instanceof SortRef) {
            ref0 = ((SortRef) ref0).getDataRef();
         }

         if(Tool.equals(ref, ref0)) {
            return true;
         }
      }

      return false;
   }

   private void setListValues(CheckBoxVSAssemblyInfo assemblyInfo,
                              CheckboxPropertyDialogModel model,
                              RuntimeViewsheet viewsheet,
                              Principal principal)
      throws Exception
   {
      CheckboxGeneralPaneModel checkBoxGeneralPaneModel = model.getCheckboxGeneralPaneModel();
      ListValuesPaneModel listValuesPaneModel = checkBoxGeneralPaneModel.getListValuesPaneModel();
      ComboBoxEditorModel checkBoxEditorModel = listValuesPaneModel.getComboBoxEditorModel();
      SelectionListDialogModel selectionListDialogModel =
         checkBoxEditorModel.getSelectionListDialogModel();
      SelectionListEditorModel selectionListEditorModel =
         selectionListDialogModel.getSelectionListEditorModel();
      VariableListDialogModel variableListDialogModel =
         checkBoxEditorModel.getVariableListDialogModel();

      assemblyInfo.setSortTypeValue(listValuesPaneModel.getSortType());
      assemblyInfo.setEmbeddedDataDownValue(listValuesPaneModel.isEmbeddedDataDown());
      assemblyInfo.setSelectFirstItemValue(listValuesPaneModel.isSelectFirstItem());

      ListData listData = new ListData();
      assemblyInfo.setDataType(checkBoxEditorModel.getDataType());

      if(checkBoxEditorModel.isEmbedded()) {
         if(checkBoxEditorModel.isQuery()) {
            assemblyInfo.setSourceType(ListInputVSAssembly.MERGE_SOURCE);
         }
         else {
            assemblyInfo.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
         }
      }
      else if(checkBoxEditorModel.isQuery()) {
         assemblyInfo.setSourceType(ListInputVSAssembly.BOUND_SOURCE);
      }
      else {
         assemblyInfo.setSourceType(ListInputVSAssembly.NONE_SOURCE);
      }

      String dtype = variableListDialogModel.getDataType();
      List<Object> values = new ArrayList<>();

      for(String val : variableListDialogModel.getValues()) {
         values.add(val == null ? null : Tool.getData(dtype, val, true));
      }

      listData.setDataType(dtype);
      listData.setLabels(variableListDialogModel.getLabels());
      listData.setValues(values.toArray());
      assemblyInfo.setListData(listData);

      assemblyInfo.setForm(selectionListEditorModel.isForm());
      ListBindingInfo listBindingInfo = new ListBindingInfo();
      listBindingInfo.setTableName(selectionListEditorModel.getTable());
      listBindingInfo = this.updateBindingInfo(listBindingInfo, selectionListEditorModel.getColumn(),
                                               selectionListEditorModel.getValue(), viewsheet, principal);
      assemblyInfo.setListBindingInfo(listBindingInfo);
   }

   public void onClick(String vsId, String name, String x, String y, String linkUri,
                       boolean isConfirm, List<UserMessage> usrmsg, Principal principal,
                       CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);

      WSExecution.setAssetQuerySandbox(rvs.getViewsheetSandbox().getAssetQuerySandbox());

      try {
         process0(rvs, name, x, y, linkUri, isConfirm, usrmsg, principal, dispatcher);
      }
      finally {
         WSExecution.setAssetQuerySandbox(null);
      }
   }

   private void process0(RuntimeViewsheet rvs, String name, String xstr, String ystr,
                         String linkUri, boolean isConfirm, List<UserMessage> usrmsg,
                         Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      VSAssembly assembly = (VSAssembly) vs.getAssembly(name);

      if(assembly == null) {
         LOG.warn("Assembly is missing, failed to process on click event: " + name);
         return;
      }

      if(!(assembly.getInfo() instanceof ClickableOutputVSAssemblyInfo ||
         assembly.getInfo() instanceof  ClickableInputVSAssemblyInfo))
      {
         return;
      }

      if(!assembly.getVSAssemblyInfo().isScriptEnabled()) {
         return;
      }

      ViewsheetSandbox box0 = getVSBox(name, box);
      ViewsheetScope scope = box0.getScope();
      String script = null;

      if(assembly.getInfo() instanceof ClickableOutputVSAssemblyInfo) {
         script = ((ClickableOutputVSAssemblyInfo) assembly.getInfo()).getOnClick();
      }
      else {
         script = ((ClickableInputVSAssemblyInfo) assembly.getInfo()).getOnClick();
      }

      if(xstr != null && ystr != null) {
         scope.put("mouseX", scope, xstr);
         scope.put("mouseY", scope, ystr);
      }

      // after onClick event, the viewsheet will be refreshed, which reset
      // the runtime values. If the changes in onClick is applied to RValue,
      // they will be lost immediately.
      VSPropertyDescriptor.setUseDValue(true);

      try {
         scope.execute(script, assembly.getName());
      }
      finally {
         VSPropertyDescriptor.setUseDValue(false);
      }

      UserMessage msg = Tool.getUserMessage();

      if(usrmsg != null) {
         usrmsg.add(msg);
      }

      Set<AssemblyRef> set = new HashSet<>();
      ChangedAssemblyList clist = this.coreLifecycleService.createList(true, dispatcher,
                                                                       rvs, linkUri);

      VSUtil.getReferencedAssets(script, set,
                                 name.contains(".") ? box0.getViewsheet() : vs, assembly);

      for(AssemblyRef ref : set) {
         switch(ref.getType()) {
         case AssemblyRef.OUTPUT_DATA:
            clist.getDataList().add(ref.getEntry());
            break;
         case AssemblyRef.INPUT_DATA:
            clist.getDataList().add(ref.getEntry());
            break;
         case AssemblyRef.VIEW:
            clist.getViewList().add(ref.getEntry());
            break;
         }
      }

      ArrayList<VSAssembly> inputAssemblies = new ArrayList<>();

      try {
         // fix bug1269914683174, need to refresh chart when the chart data changed
         // by onclick script
         for(AssemblyEntry obj : clist.getDataList()) {
            String name0 = obj.getAbsoluteName();
            VSAssembly assembly0 = (VSAssembly) vs.getAssembly(name0);

            if(assembly0 instanceof TableVSAssembly) {
               box.resetDataMap(name0);

               // @by yanie: fix #691, refresh FormTableLens after commit
               TableVSAssembly ta = (TableVSAssembly) assembly0;
               TableVSAssemblyInfo tinfo = (TableVSAssemblyInfo) ta.getInfo();

               if(tinfo.isForm()) {
                  FormTableLens flens = box.getFormTableLens(name0);

                  if(hasFormScript(script, name0) && flens.isChanged()) {
                     box.addScriptChangedForm(name0);
                  }

                  box.syncFormData(name0);
               }
            }
            else if(assembly0 instanceof CrosstabVSAssembly) {
               box.resetDataMap(name0);
            }
            else if(assembly0 instanceof ChartVSAssembly) {
               processChart(rvs, name0, linkUri, principal, dispatcher);
            }
            else if(assembly0 instanceof InputVSAssembly) {
               inputAssemblies.add(assembly0);
            }
         }

         for(VSAssembly inputAssembly : inputAssemblies) {
            box.processChange(inputAssembly.getAbsoluteName(),
                              VSAssembly.OUTPUT_DATA_CHANGED, clist);
         }

         //If property "refresh viewsheet after submit" of the submit button is checked,
         //we should update whole viewsheet after clicking the button.
         if(assembly instanceof SubmitVSAssembly &&
            ((SubmitVSAssemblyInfo) assembly.getInfo()).isRefresh())
         {
            this.coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), linkUri, dispatcher,
                                                       false, true, true, clist, true);
         }
         else {
            box.processChange(name, VSAssembly.INPUT_DATA_CHANGED, clist);
            coreLifecycleService.execute(rvs, name, linkUri, clist, dispatcher, true);
         }
      }
      finally {
         box.clearScriptChangedFormSet();
      }

      if(!isConfirm) {
         String cmsg = Tool.getConfirmMessage();
         Tool.clearConfirmMessage();

         if(cmsg != null) {
            try {
               scope.execute("confirmEvent.confirmed = false", ViewsheetScope.VIEWSHEET_SCRIPTABLE);
            }
            catch(Exception ignore) {
            }

            MessageCommand cmd = new MessageCommand();
            cmd.setMessage(cmsg);
            cmd.setType(MessageCommand.Type.CONFIRM);
            VSOnClickEvent event = new VSOnClickEvent();
            event.setConfirmEvent(true);
            cmd.addEvent("/events/onclick/" + name + "/" + xstr + "/" + ystr + "/" + true, event);
            dispatcher.sendCommand(cmd);
         }

         if(msg != null) {
            dispatcher.sendCommand(MessageCommand.fromUserMessage(msg));
         }
      }
   }

   private boolean hasFormScript(String script, String name) {
      if(!LicenseManager.isComponentAvailable(LicenseManager.LicenseComponent.FORM)) {
         return false;
      }

      List<String> formFunc = new ArrayList<>();
      formFunc.add("setObject");
      formFunc.add("appendRow");
      formFunc.add("insertRow");
      formFunc.add("deleteRow");

      for(int i = 0; i < formFunc.size(); i++) {
         StringBuffer buffer = new StringBuffer();
         buffer.append(name);
         buffer.append(".");
         buffer.append(formFunc.get(i));

         if(script.indexOf(buffer.toString()) != -1) {
            return true;
         }
      }

      return false;
   }

   /**
    * Process chart when chart data changed.
    */
   private void processChart(RuntimeViewsheet rvs, String name, String linkUri,
                             Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      VSAssembly assembly = (VSAssembly) vs.getAssembly(name);
      box.clearGraph(name);
      coreLifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);
   }

   /**
    * If viewsheet is embeded, get matching sandbox.
    */
   private ViewsheetSandbox getVSBox(String name, ViewsheetSandbox box0) {
      if(name.indexOf(".") == -1) {
         return box0;
      }

      int index = name.indexOf(".");
      String vsName = name.substring(0, index);
      box0 = box0.getSandbox(vsName);

      return getVSBox(name.substring(index + 1, name.length()), box0);
   }

   /**
    * Apply selection.
    *
    * @param assemblyName   the name of the selection assembly
    * @param selectedObject the selected object
    * @param principal      a principal identifying the current user.
    * @param dispatcher     the command dispatcher.
    *
    * @throws Exception if the selection could not be applied.
    */
   private int applySelection(String vsId, String assemblyName, Object selectedObject,
                              Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      if(assemblyName == null) {
         return 0;
      }

      RuntimeViewsheet rvs =
         vsObjectService.getRuntimeViewsheet(vsId, principal);
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      int hint;

      rvs.setSocketSessionId(dispatcher.getSessionId());
      rvs.setSocketUserName(dispatcher.getUserName());

      box.lockWrite();

      try {
         hint = applySelection0(rvs, assemblyName, selectedObject, dispatcher);
      }
      finally {
         box.unlockWrite();
      }

      return hint;
   }

   private int applySelection0(RuntimeViewsheet rvs, String assemblyName, Object selectedObject,
                                CommandDispatcher dispatcher)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return 0;
      }

      // binding may be caused by variable, should retry mv if necessary
      rvs.resetMVOptions();
      VSAssembly vsAssembly = vs.getAssembly(assemblyName);

      if(!(vsAssembly instanceof InputVSAssembly assembly)) {
         return 0;
      }

      Object[] values;

      if(selectedObject == null) {
         values = null;
      }
      else if(selectedObject.getClass().isArray()) {
         values = (Object[]) selectedObject;
      }
      else if(selectedObject instanceof java.util.List) {
         values = ((java.util.List) selectedObject).toArray();
      }
      else {
         values = new Object[] { selectedObject };
      }

      InputVSAssemblyInfo info = (InputVSAssemblyInfo) assembly.getVSAssemblyInfo();
      String type = assembly.getDataType();
      Object obj = values == null || values.length == 0 ? null : Tool.getData(type, values[0]);

      if(values !=null && values.length > 0 && obj == null && type.equals(CoreTool.BOOLEAN) && values[0].equals("")) {
         obj = "";
      }

      int hint0;

      if(info instanceof SliderVSAssemblyInfo) {
         hint0 = info.setSelectedObject(obj);
         coreLifecycleService.refreshVSAssembly(rvs, info.getAbsoluteName(), dispatcher);
      }
      else if(info instanceof SpinnerVSAssemblyInfo) {
         hint0 = info.setSelectedObject(obj);
      }
      else if(info instanceof CheckBoxVSAssemblyInfo) {
         Object[] objs = values == null ? null : new Object[values.length];

         if(values != null) {
            for(int i = 0; i < values.length; i++) {
               objs[i] = Tool.getData(type, values[i]);
            }
         }

         hint0 = info.setSelectedObjects(objs);
      }
      else if(info instanceof RadioButtonVSAssemblyInfo) {
         hint0 = info.setSelectedObject(obj);
      }
      else if(info instanceof ComboBoxVSAssemblyInfo) {
         hint0 = info.setSelectedObject(obj);
      }
      else if(info instanceof TextInputVSAssemblyInfo) {
         hint0 = info.setSelectedObject(obj);
      }
      else {
         return 0;
      }

      final int hint = hint0;

      final ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return 0;
      }

      final boolean form = info instanceof TextInputVSAssemblyInfo ||
         info instanceof ListInputVSAssemblyInfo;

      // @by stephenwebster, fix bug1386097203077
      // instead of dispatching the event, only attach the event to the
      // VSScope, otherwise, both the call to execute and the call to dispatchEvent
      // will execute the onLoad script.
      if(form) {
         ScriptEvent event0 = new InputScriptEvent(assemblyName, assembly);
         box.attachScriptEvent(event0);
      }

      if(info.getWriteBackValue()) {
         ViewsheetSandbox baseBox = coreLifecycleService.getSandbox(box, assemblyName);
         String tableName = VSUtil.stripOuter(info.getTableName());

         baseBox.writeBackFormDataDirectly(viewsheetService.getAssetRepository(),
                                           tableName, info.getColumnValue(),
                                           info.getRow(), info.getSelectedObject());
      }

      final AssetQuerySandbox wbox = box.getAssetQuerySandbox();
      Worksheet ws = assembly.getViewsheet().getBaseWorksheet();
      refreshVariable(assembly, wbox, ws, vs);

      // if assembly is a variable then check if any worksheet tables depend on it and
      // clear any metadata that selection lists/trees might be using
      resetTableMetadata(assembly, ws, box);

      return hint;
   }

   private void refreshVS(String vsId, Principal principal, CommandDispatcher dispatcher,
                         Map<String, VSAssemblyInfo> oldCrosstabInfo,
                         String[] assemblyNames, Object[] selectedObjects, int[] hints,
                         @LinkUri String linkUri) throws Exception
   {
      RuntimeViewsheet rvs =
         vsObjectService.getRuntimeViewsheet(vsId, principal);
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockWrite();

      try {
         refreshVS0(rvs, principal, dispatcher, oldCrosstabInfo,
                    assemblyNames, selectedObjects, hints, linkUri);
      }
      finally {
         box.unlockWrite();
      }

   }

   private void refreshVS0(RuntimeViewsheet rvs, Principal principal, CommandDispatcher dispatcher,
                           Map<String, VSAssemblyInfo> oldCrosstabInfo,
                           String[] assemblyNames, Object[] selectedObjects, int[] hints,
                           @LinkUri String linkUri) throws Exception
   {
      ChangedAssemblyList clist = vsObjectService.createList(true, dispatcher, rvs, linkUri);
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();
      InputVSAssembly[] assemblies = new InputVSAssembly[assemblyNames.length];
      Object[] objs = new Object[selectedObjects.length];

      if(vs == null) {
         return;
      }

      for(int i = 0; i < assemblyNames.length; i ++) {
         String assemblyName = assemblyNames[i];
         VSAssembly vsAssembly = vs.getAssembly(assemblyName);

         if(!(vsAssembly instanceof InputVSAssembly)) {
            return;
         }

         assemblies[i] = (InputVSAssembly) vsAssembly;
      }

      final AssetQuerySandbox wbox = box.getAssetQuerySandbox();
      boolean wsOutputDataChanged = false;
      // true to refresh all input assemblies.
      boolean refreshInput = true;

      for(int i = 0; i < assemblies.length; i ++) {
         InputVSAssembly assembly = assemblies[i];

         Worksheet ws = assembly.getViewsheet().getBaseWorksheet();
         String tname = assembly.getTableName();

         if(assembly.isVariable() && tname != null && tname.contains("$(")) {
            tname = tname.substring(2, tname.length() - 1);
         }

         Assembly wsAssembly = ws == null ? null : ws.getAssembly(tname);
         InputVSAssemblyInfo info = (InputVSAssemblyInfo) assembly.getVSAssemblyInfo();
         // if not refreshing vs on input submit, refresh all input assemblies so
         // cascading parameters will work. (57856)
         refreshInput = refreshInput && !info.isRefresh();

         if(wbox != null && (wsAssembly instanceof VariableAssembly) &&
            hints[i] ==VSAssembly.OUTPUT_DATA_CHANGED)
         {
            wsOutputDataChanged = true;
         }
         else {
            // @by stephenwebster, For Bug #1726, remove the synchronize
            // on the VSAQueryLock.  getVGraphPair.init uses graphLock and
            // the VSAQueryLock is obtained after it to maintain correct
            // locking order.  Any calls to get data from a graph most likely
            // should be routed through the VGraphPair instead of getting it
            // direct.  I tested a similar asset related to bug1350539979627
            // and could not reproduce it.  This change is commented out below.

            //synchronized(box.getVSAQueryLock()) {
            // here may be cause fire command, AddVSObjectCommand for chart,
            // then GetChartAreaEvent will be fired in flex, VSEventUtil.execte
            // may cause ViewsheetSandbox.cancel, if current time, the chart
            // is in get data from GetChartAreaEvent, will cause no data returned
            // see bug1350539979627

            // @by larryl, should not be necessary to reset, optimization
            // Bug #24751, some dependency assemblies don't refresh after applying selection
            if(info.isSubmitOnChange() && info.isRefresh()) {
               box.reset(clist);
               vsObjectService.execute(
                  rvs, assemblyNames[i], linkUri, hints[i] | VSAssembly.VIEW_CHANGED, dispatcher);
            }
         }
      }

      if(wsOutputDataChanged) {
         wbox.setIgnoreFiltering(false);
         coreLifecycleService.refreshEmbeddedViewsheet(rvs, linkUri, dispatcher);
         box.resetRuntime();
         // @by stephenwebster, For Bug #6575
         // refreshViewsheet() already calls reset() making this call redundant
         // It also has a side-effect of making the viewsheet load twice.
         // I double checked bug1391802567612 and it seems like it
         // is working as expected
         // box.reset(clist);
         coreLifecycleService.refreshViewsheet(
            rvs, rvs.getID(), linkUri, dispatcher, false, true, true, clist);
      }

      for(int i = 0; i < assemblies.length; i ++) {
         InputVSAssembly assembly = assemblies[i];
         Object selectedObject = selectedObjects[i];
         Object[] values;

         if(selectedObject == null) {
            values = null;
         }
         else if(selectedObject.getClass().isArray()) {
            values = (Object[]) selectedObject;
         }
         else if(selectedObject instanceof java.util.List) {
            values = ((java.util.List) selectedObject).toArray();
         }
         else {
            values = new Object[] { selectedObject };
         }

         String type = assembly.getDataType();
         objs[i] = values == null || values.length == 0 ? null : Tool.getData(type, values[0]);
         InputVSAssemblyInfo info = (InputVSAssemblyInfo) assembly.getVSAssemblyInfo();

         // @davidd bug1364406849572, refactored processing of shared filters to
         // external and local.
         vsObjectService.processExtSharedFilters(assembly, hints[i], rvs, principal, dispatcher);
         box.processSharedFilters(assembly, clist, true);
      }

      // @by ankitmathur, Fix Bug #4211, Use the old VSCrosstabInfo's to sync
      // new TableDataPaths.
      // this should happen before table is refreshed
      for(Assembly cassembly : vs.getAssemblies()) {
         if(cassembly instanceof CrosstabVSAssembly) {
            try {
               box.updateAssembly(cassembly.getAbsoluteName());
               CrosstabVSAssembly cross = (CrosstabVSAssembly) cassembly;
               CrosstabVSAssemblyInfo ocinfo = (CrosstabVSAssemblyInfo) oldCrosstabInfo.get(
                  cassembly.getName());
               FormatInfo finfo = cross.getFormatInfo();
               CrosstabVSAssemblyInfo ncinfo = (CrosstabVSAssemblyInfo) cross.getVSAssemblyInfo();

               boolean allAggregateChanges = Arrays.stream(objs)
                  .filter(Objects::nonNull)
                  .map(Object::toString)
                  .allMatch(objValue -> isAggregateChange(ncinfo.getVSCrosstabInfo(), objValue));

               if(allAggregateChanges) {
                  continue;
               }

               TableHyperlinkAttr hyperlink = ncinfo.getHyperlinkAttr();
               TableHighlightAttr highlight = ncinfo.getHighlightAttr();

               if(finfo != null) {
                  synchronized(finfo.getFormatMap()) {
                     VSUtil.syncCrosstabPath(cross, ocinfo, false, finfo.getFormatMap(), true);
                  }
               }

               if(hyperlink != null) {
                  VSUtil.syncCrosstabPath(cross, ocinfo, false, hyperlink.getHyperlinkMap(), true);
               }

               if(highlight != null) {
                  VSUtil.syncCrosstabPath(cross, ocinfo, false, highlight.getHighlightMap(), true);
               }
            }
            catch(Exception ex) {
               LOG.warn("Failed to sync Crosstab paths", ex);
            }
         }
      }

      for(InputVSAssembly assembly : assemblies) {
         InputVSAssemblyInfo info = (InputVSAssemblyInfo) assembly.getVSAssemblyInfo();

         // fix bug1368262989004, fix this bug same as bug1366884826731, now
         // no matter process share filter whether success or not, we should
         // also execute, or some dependency assembly will not refresh.
         if(info.isSubmitOnChange() && info.isRefresh()) {
            for(Assembly a : vs.getAssemblies()) {
               if(isAssemblyReferenced(assembly, a)) {
                  // Bug #71186, execute dynamic values
                  if(a instanceof OutputVSAssembly) {
                     box.updateAssembly(a.getAbsoluteName());
                  }

                  clist.getDataList().add(a.getAssemblyEntry());
               }
            }

            vsObjectService.execute(rvs, assembly.getName(), linkUri, clist, dispatcher, true);
            vsObjectService.layoutViewsheet(rvs, linkUri, dispatcher);
         }
      }

      if(refreshInput) {
         for(Assembly assembly : vs.getAssemblies()) {
            if(assembly instanceof InputVSAssembly) {
               box.executeView(assembly.getAbsoluteName(), true);
               coreLifecycleService.refreshVSAssembly(rvs, (VSAssembly) assembly, dispatcher);
            }
         }
      }
   }

   private boolean isAssemblyReferenced(InputVSAssembly input, Assembly assembly) {
      if(input.getAbsoluteName().equals(assembly.getAbsoluteName())) {
         return false;
      }

      String inputName = input.getName();

      if(assembly instanceof VariableProvider) {
         if(isVariableProviderReferenced(inputName, (VariableProvider) assembly)) {
            return true;
         }
      }

      if(assembly instanceof VSAssembly) {
         if(isVSAssemblyReferenced("$(" + inputName + ")", (VSAssembly) assembly)) {
            return true;
         }

         // check for inputName.value in the script
         if(isVSAssemblyReferenced(inputName + ".value", (VSAssembly) assembly)) {
            return true;
         }
      }

      if(assembly instanceof CalcTableVSAssembly calc) {
         TableLayout layout = calc.getTableLayout();
         return layout != null && isTableLayoutReferenced(inputName, layout);
      }

      return false;
   }

   private boolean isVariableProviderReferenced(String varName, VariableProvider provider) {
      for(UserVariable var : provider.getAllVariables()) {
         if(Objects.equals(var.getName(), varName)) {
            return true;
         }
      }

      return false;
   }

   private boolean isVSAssemblyReferenced(String inputVar, VSAssembly assembly) {
      if(isAssemblyReferenced(inputVar, assembly.getDynamicValues())) {
         return true;
      }

      if(isAssemblyReferenced(inputVar, assembly.getViewDynamicValues(true))) {
         return true;
      }

      return isAssemblyReferenced(inputVar, assembly.getHyperlinkDynamicValues());
   }

   private boolean isAssemblyReferenced(String varName, List<DynamicValue> list) {
      return list != null && list.stream()
         .map(DynamicValue::getDValue)
         .anyMatch(v -> Objects.equals(varName, v) ||
            (VSUtil.isScriptValue(v) && v.contains(varName)));
   }

   private boolean isTableLayoutReferenced(String inputName, TableLayout layout) {
      for(Enumeration<?> e = layout.getAllVariables(); e.hasMoreElements(); ) {
         UserVariable var = (UserVariable) e.nextElement();

         if(Objects.equals(var.getName(), inputName)) {
            return true;
         }
      }

      return isCellReferenced(inputName, layout);
   }

   private boolean isCellReferenced(String inputName, TableLayout layout) {
      if(layout.isCalc()) {
         Pattern pattern1 = Pattern.compile("^\\s*" + inputName + "\\s*\\.\\s*value\\s*$");
         Pattern pattern2 = Pattern.compile("^\\s*" + inputName + "\\s*\\.\\s*value\\W");
         Pattern pattern3 = Pattern.compile("\\W" + inputName + "\\s*\\.\\s*value\\W");
         Pattern pattern4 = Pattern.compile("\\W" + inputName + "\\s*\\.\\s*value\\s*$");

         for(BaseLayout.Region region : layout.getRegions()) {
            for(int r = 0; r < region.getRowCount(); r++) {
               for(int c = 0; c < region.getColCount(); c++) {
                  TableCellBinding cell = (TableCellBinding) region.getCellBinding(r, c);

                  if(cell != null && cell.getType() == CellBinding.BIND_FORMULA) {
                     String formula = cell.getValue();

                     if(formula != null && (
                        pattern1.matcher(formula).find() ||
                        pattern2.matcher(formula).find() ||
                        pattern3.matcher(formula).find() ||
                        pattern4.matcher(formula).find()))
                     {
                        // could possibly return false positives, but the possibility should be
                        // minimized by the regular expressions used to match the input reference
                        return true;
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   private Map<String, VSAssemblyInfo> getOldCrosstabInfo(String vsId, Principal principal) throws Exception {
      // @by ankitmathur, Fix Bug #4211, Need to maintain the old instances of
      // all VSCrosstabInfo's which can be used to sync the new/updated
      // TableDataPaths after the assembly is updated.
      RuntimeViewsheet rvs =
         vsObjectService.getRuntimeViewsheet(vsId, principal);
      Viewsheet vs = rvs.getViewsheet();
      Map<String, VSAssemblyInfo> oldCrosstabInfo = new HashMap<>();

      if(vs == null) {
         return oldCrosstabInfo;
      }

      Assembly[] assemblyList = vs.getAssemblies();

      for(Assembly casembly : assemblyList) {
         if(casembly instanceof CrosstabVSAssembly) {
            oldCrosstabInfo.put(
               casembly.getName(), ((CrosstabVSAssembly) casembly).getVSAssemblyInfo().clone());
         }
      }

      return oldCrosstabInfo;
   }

   /**
    * Get the rows of a column.
    * @param rvs        Runtime Viewsheet
    * @param table      the table
    * @param column     the column
    * @param principal  the principal user
    * @return the rows
    */
   public String[] getColumnRows(RuntimeViewsheet rvs, String table,
                                 String column, Principal principal)
   {
      Viewsheet vs = rvs.getViewsheet();
      String[] rows = new String[0];

      if(vs == null || column == null) {
         return rows;
      }

      Worksheet ws = vs.getBaseWorksheet();

      if(ws != null && VSEventUtil.checkBaseWSPermission(
         vs, principal, viewsheetService.getAssetRepository(), ResourceAction.READ))
      {
         TableAssembly tableAssembly = (TableAssembly) ws.getAssembly(table);

         if(tableAssembly instanceof SnapshotEmbeddedTableAssembly) {
            XSwappableTable stable = ((SnapshotEmbeddedTableAssembly)tableAssembly).getTable();
            ColumnRef colRef = new ColumnRef(new AttributeRef(null, column));
            int col = AssetUtil.findColumn(stable, colRef);

            if(col != -1) {
               rows = new String[stable.getRowCount()];

               for(int i = stable.getHeaderRowCount(); i < stable.getRowCount(); i++) {
                  rows[i] = Tool.toString(stable.getObject(i, col));
               }
            }
         }
         else if(tableAssembly instanceof EmbeddedTableAssembly) {
            XEmbeddedTable data =
               VSEventUtil.getVSEmbeddedData((EmbeddedTableAssembly) tableAssembly);
            ColumnRef colRef = new ColumnRef(new AttributeRef(null, column));
            int col = AssetUtil.findColumn(data, colRef);

            if(col != -1) {
               rows = new String[data.getRowCount()];

               for(int i = data.getHeaderRowCount(); i < data.getRowCount(); i++) {
                  rows[i] = Tool.toString(data.getObject(i, col));
               }
            }
         }
      }

      return rows;
   }

   /**
    * Get tables available in viewsheet as tree. Mimic of GetTablesEvent for data input
    * @param rvs        the runtime viewsheet
    * @param principal  the principal user
    * @return the tree model for the tables
    */
   public TreeNodeModel getInputTablesTree(RuntimeViewsheet rvs, boolean onlyVars,
                                           Principal principal)
   {
      List[] lists = getInputTables(rvs, true, onlyVars, principal);
      List<TreeNodeModel> tableChildren = new ArrayList<>();
      List<TreeNodeModel> variableChildren = new ArrayList<>();

      if(lists != null) {
         for(Assembly table : (List<Assembly>) lists[0]) {
            String name = table.getName();
            String normalizedName = name;
            String description = ((TableAssembly) table).getDescription() != null ?
               ((TableAssembly) table).getDescription() : "";

            if(name.endsWith("_VSO")) {
               normalizedName = name.substring(0, name.length() - 4);
            }

            normalizedName = VSUtil.stripOuter(name);

            tableChildren.add(TreeNodeModel.builder()
                                 .label(Tool.localize(normalizedName))
                                 .data(name)
                                 .leaf(true)
                                 .tooltip(description)
                                 .type("table")
                                 .build());
         }

         for(VariableEntry ve : (List<VariableEntry>) lists[1]) {
            variableChildren.add(TreeNodeModel.builder()
               .label(Tool.localize(ve.label != null && ve.label.length() != 0 ? ve.label :
                ve.value))
               .data("$(" + ve.value + ")")
               .leaf(true)
               .type("variable")
               .build());
         }
      }

      List<TreeNodeModel> treeChildren = new ArrayList<>();

      treeChildren.add(TreeNodeModel.builder()
                          .label(Catalog.getCatalog().getString("None"))
                          .data("")
                          .leaf(true)
                          .type("none")
                          .build());

      if(!onlyVars) {
         treeChildren.add(TreeNodeModel.builder()
                             .label(Catalog.getCatalog().getString("Tables"))
                             .data("Tables")
                             .leaf(false)
                             .type("folder")
                             .children(tableChildren)
                             .build());
      }

      treeChildren.add(TreeNodeModel.builder()
                          .label(Catalog.getCatalog().getString("Variables"))
                          .data("Variables")
                          .leaf(false)
                          .type("folder")
                          .children(variableChildren)
                          .build());

      return TreeNodeModel.builder().children(treeChildren).build();
   }

   /**
    * Get tables available in viewsheet arrays. Mimic of GetTablesEvent for selection list
    * @param rvs        the runtime viewsheet
    * @param principal  the principal user
    * @return the tree model for the tables
    */
   public List<String[]> getInputTablesArray(RuntimeViewsheet rvs, Principal principal) {
      List[] lists = getInputTables(rvs, false, false, principal);
      List<String> tables = new ArrayList<>();
      List<String> descriptions = new ArrayList<>();
      List<String> localizedTables = new ArrayList<>();
      List<String[]> result = new ArrayList<>();
      Viewsheet vs = rvs.getViewsheet();
      AssetEntry baseEntry = vs.getBaseEntry();

      if(lists != null) {
         for(Assembly table : (List<Assembly>) lists[0]) {
            String name = table.getName();
            String normalizedName = name;
            String description = baseEntry.isLogicModel() || baseEntry.isQuery() ?
               baseEntry.getProperty("Tooltip") : ((TableAssembly) table).getDescription();

            tables.add(name);
            descriptions.add(description);
            localizedTables.add(Tool.localize(normalizedName));
         }
      }

      result.add(tables.toArray(new String[0]));
      result.add(localizedTables.toArray(new String[0]));
      result.add(descriptions.toArray(new String[0]));
      return result;
   }

   /**
    * Get tables available in viewsheet. Mimic of GetTablesEvent
    * @param rvs        the runtime viewsheet
    * @param embedded   only get embedded tables
    * @param onlyVars   only get variables
    * @param principal  the principal user
    * @return the tables and variable tables lists
    */
   public List[] getInputTables(RuntimeViewsheet rvs, boolean embedded,
                                boolean onlyVars, Principal principal)
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return null;
      }

      Worksheet ws = vs.getBaseWorksheet();
      List<Assembly> tableList =  new ArrayList<>();
      List<VariableEntry> variableList =  new ArrayList<>();
      List[] result = {tableList, variableList};

      if(ws != null && VSEventUtil.checkBaseWSPermission(vs, principal,
                                           viewsheetService.getAssetRepository(), ResourceAction.READ))
      {
         Assembly[] assemblies = ws.getAssemblies();

         for(Assembly assembly : assemblies) {
            if(!assembly.isVisible()) {
               continue;
            }

            if(embedded) {
               if(!onlyVars && assembly instanceof TableAssembly tableAssembly) {
                  TableAssembly embeddedAssembly = getBaseEmbedded(tableAssembly);

                  if(embeddedAssembly != null && tableAssembly.isVisibleTable()) {
                     tableList.add(embeddedAssembly);

                  }
               }

               if(assembly instanceof VariableAssembly va) {
                  VariableEntry ve = new VariableEntry(va.getVariable().getAlias(),
                                                       va.getVariable().getName());

                  if(!variableList.contains(ve)) {
                     variableList.add(ve);
                  }
               }
            }
            else if(assembly instanceof TableAssembly) {
               if(((TableAssembly) assembly).isVisibleTable()) {
                  tableList.add(assembly);
               }
            }
         }

         if(embedded) {
            UserVariable[] uvars = ws.getAllVariables();

            for(UserVariable uvar : uvars) {
               if(uvar != null) {
                  VariableEntry ve = new VariableEntry(uvar.getAlias(), uvar.getName());

                  if(!variableList.contains(ve)) {
                     variableList.add(ve);
                  }
               }
            }
         }

         tableList.sort(new VSEventUtil.WSAssemblyComparator(ws));
      }

      if(embedded) {
         Collections.sort(variableList);
      }

      return result;
   }

   /**
    * Check if the table is embedded.
    * @param table the table to check
    * @return the embedded table assembly
    */
   private TableAssembly getBaseEmbedded(TableAssembly table) {
      if(table == null) {
         return null;
      }

      if(VSEventUtil.isEmbeddedDataSource(table) &&
         !(table instanceof SnapshotEmbeddedTableAssembly))
      {
         return table;
      }

      TableAssembly embedded = null;
      String tname = table.getName();

      if(table instanceof EmbeddedTableAssembly) {
         embedded = table;
      }
      else if(table instanceof MirrorTableAssembly mtable) {
         String bname = mtable.getAssemblyName();

         if((bname.equals(tname + "_VSO") || bname.equals(tname + "_O")) &&
            mtable.getTableAssembly() instanceof EmbeddedTableAssembly)
         {
            embedded = mtable.getTableAssembly();
         }
      }

      if(embedded != null && !"true".equals(embedded.getProperty("auto.generate")) &&
         !(embedded instanceof SnapshotEmbeddedTableAssembly))
      {
         return embedded;
      }

      return null;
   }

   public ListBindingInfo updateBindingInfo(ListBindingInfo info, String column, String value,
                                            RuntimeViewsheet rvs, Principal principal)
      throws Exception
   {
      String table = info.getTableName();

      if(table != null && !table.isEmpty() && column != null &&
         !column.isEmpty() && value != null && !value.isEmpty())
      {
         ColumnSelection selection = vsColumnHandler.getTableColumns(rvs, table, principal);
         boolean colFound = false;
         boolean valFound = false;

         for(int i = 0; i < selection.getAttributeCount(); i++) {
            DataRef ref = selection.getAttribute(i);

            if(!colFound && column.equals(ref.getName())) {
               info.setLabelColumn(ref);
               colFound = true;
            }

            if(!valFound && value.equals(ref.getName())) {
               info.setValueColumn(ref);
               valFound = true;
            }

            if(colFound && valFound) {
               break;
            }
         }
      }

      return info;
   }

   public static final class VariableEntry implements Comparable{
      public VariableEntry(String label, String value) {
         this.label = label;
         this.value = value;
      }

      @Override
      public int compareTo(Object o) {
         VariableEntry that = (VariableEntry) o;

         if(this.label == null) {
            if(that.label == null) {
               return 0;
            }

            return -1;
         }
         else if(that.label == null) {
            return 1;
         }

         return this.label.compareTo(that.label);
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         VariableEntry that = (VariableEntry) o;

         return this.value.equals(that.value);
      }

      @Override
      public int hashCode() {
         int result = label.hashCode();
         result = 31 * result + value.hashCode();
         return result;
      }

      private String label;
      private String value;
   }

   /**
    * Update the model with the data table related info
    * @param info      the assembly info
    * @param paneModel the model to update
    */
   public void getTableName(InputVSAssemblyInfo info, DataInputPaneModel paneModel) {
      String table = info.getTableName();

      if(table == null) {
         paneModel.setTableLabel(null);
         paneModel.setTable(null);
         paneModel.setVariable(info.isVariable());
         return;
      }

      if(table.startsWith("$(") && table.endsWith(")")) {
         table = table.substring(2, table.length() - 1);
      }

      Worksheet ws = info.getViewsheet().getBaseWorksheet();
      Assembly wsAssembly = ws == null ? null : ws.getAssembly(table);

      if(ws != null && wsAssembly == null && table.endsWith("_O")) {
         table = VSUtil.stripOuter(table);
         wsAssembly = ws.getAssembly(table);
      }

      String tableLabel = table; // for handle alias

      if(info.isVariable()) {
         VariableAssembly variableAssembly = (VariableAssembly) wsAssembly;

         //variable might not be in the baseworksheet
         if(variableAssembly != null) {
            String alias = variableAssembly.getVariable().getAlias();
            tableLabel = (alias == null || alias.isEmpty()) ?
               variableAssembly.getVariable().getName() : alias;
         }
      }
      else {
         tableLabel = VSUtil.stripOuter(tableLabel);
         tableLabel = Tool.localize(tableLabel);
      }

      paneModel.setTableLabel(tableLabel);
      paneModel.setTable(table);
      paneModel.setVariable(info.isVariable());
   }

   /**
    * Verify whether the input value is a part of the runtime Aggregates for the
    * Crosstab. If so, no need to sync the old TableDataPath.
    *
    * @param ncinfo VSCrosstabInfo which is generated after the assembly has
    *               been updated.
    * @param changedValue The new input value.
    *
    * @return <tt>true</tt> if the new input value is a runtime aggregate.
    */
   private boolean isAggregateChange(VSCrosstabInfo ncinfo, String changedValue) {
      if(ncinfo == null || ncinfo.getRuntimeAggregates() == null)
      {
         return false;
      }

      DataRef[] nAggRefs = ncinfo.getRuntimeAggregates();

      try {
         for(DataRef nagg : nAggRefs) {
            if(nagg.getName().contains(changedValue)) {
               return true;
            }
         }
      }
      catch(Exception ex) {
         //ignore the exception
      }

      return false;
   }

   private void refreshVariable(VSAssembly assembly, AssetQuerySandbox wbox, Worksheet ws,
                                Viewsheet vs) throws Exception
   {
      if(!(assembly instanceof InputVSAssembly iassembly) || wbox == null || ws == null) {
         return;
      }

      Object cdata;
      Object mdata = null;

      if(iassembly instanceof SingleInputVSAssembly) {
         cdata = ((SingleInputVSAssembly) iassembly).getSelectedObject();
      }
      else if(iassembly instanceof CompositeInputVSAssembly) {
         Object[] objs =
            ((CompositeInputVSAssembly) iassembly).getSelectedObjects();
         cdata = objs == null || objs.length == 0 ? null : objs[0];
         mdata = objs == null || objs.length <= 1 ? null : objs;
      }
      else {
         throw new RuntimeException("Unsupported assembly found: " +
                                       assembly);
      }

      String tname = iassembly.getTableName();
      VariableTable vt = wbox.getVariableTable();

      if(iassembly.isVariable() && tname != null && tname.contains("$(")) {
         tname = tname.substring(2, tname.length() - 1);
      }

      if(tname != null) {
         Assembly ass = ws.getAssembly(tname);

         if(ass instanceof VariableAssembly) {
            vt.put(tname, mdata == null ? cdata : mdata);
            wbox.refreshVariableTable(vt);
         }
         else {
            ArrayList<UserVariable> variableList = new ArrayList<>();

            Viewsheet.mergeVariables(variableList, ws.getAllVariables());
            Viewsheet.mergeVariables(variableList, vs.getAllVariables());

            for(UserVariable var : variableList) {
               if(var != null && tname.equals(var.getName())) {
                  vt.put(tname, mdata == null ? cdata : mdata);
                  break;
               }
            }

            wbox.refreshVariableTable(vt);
         }
      }
      else {
         vt.put(iassembly.getName(), mdata == null ? cdata : mdata);
         wbox.refreshVariableTable(vt);
      }
   }

   /**
    * Reset table metadata for any worksheet tables that are dependent on
    * the variable of the input assembly
    */
   private void resetTableMetadata(InputVSAssembly inputAssembly, Worksheet ws,
                                   ViewsheetSandbox box)
   {
      if(!inputAssembly.isVariable()) {
         return;
      }

      String varName = inputAssembly.getTableName();

      if(varName != null && varName.contains("$(")) {
         varName = varName.substring(2, varName.length() - 1);
      }

      for(Assembly wsAssembly : ws.getAssemblies()) {
         if(!(wsAssembly instanceof TableAssembly tableAssembly)) {
            continue;
         }

         // loop through the table's variables
         for(UserVariable var : tableAssembly.getAllVariables()) {

            // if it matches to the variable name of the input assembly then reset metadata
            if(Tool.equals(var.getName(), varName)) {
               box.resetTableMetaData(tableAssembly.getName());
            }
         }
      }
   }


   private final DataRefModelFactoryService dataRefModelFactoryService;
   private VSAssemblyInfoHandler vsAssemblyInfoHandler;
   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSDialogService dialogService;
   private final VSTrapService trapService;
   private final VSObjectService vsObjectService;
   private final CoreLifecycleService coreLifecycleService;
   private final ViewsheetService viewsheetService;
   private final VSColumnHandler vsColumnHandler;
   private static final Logger LOG = LoggerFactory.getLogger(VSInputService.class);
}
