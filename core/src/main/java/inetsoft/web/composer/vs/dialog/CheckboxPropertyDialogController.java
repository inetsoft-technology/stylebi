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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller that provides the REST endpoints for the checkbox property dialog.
 *
 * @since 12.3
 */
@Controller
public class CheckboxPropertyDialogController {
   /**
    * Creates a new instance of <tt>CheckBoxPropertyController</tt>.
    * @param vsObjectPropertyService VSObjectPropertyService instance
    * @param vsInputService          VSInputService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    * @param viewsheetService
    */
   @Autowired
   public CheckboxPropertyDialogController(
      VSObjectPropertyService vsObjectPropertyService,
      VSInputService vsInputService,
      RuntimeViewsheetRef runtimeViewsheetRef,
      VSDialogService dialogService,
      VSTrapService trapService, ViewsheetService viewsheetService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.vsInputService = vsInputService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.dialogService = dialogService;
      this.trapService = trapService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Gets the check box property dialog model.
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId   the check box id
    * @return the check box property dialog model.
    */
   @RequestMapping(
      value = "/api/composer/vs/checkbox-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public CheckboxPropertyDialogModel getCheckBoxPropertyModel(
      @PathVariable("objectId") String objectId, @RemainingPath String runtimeId,
      Principal principal) throws Exception
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
         //TODO decide what to do with exception
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
      List<String[]> tablesList = this.vsInputService.getInputTablesArray(rvs, principal);
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

      vsInputService.getTableName(checkBoxAssemblyInfo, dataInputPaneModel);
      dataInputPaneModel.setTargetTree(
         this.vsInputService.getInputTablesTree(rvs, true, principal));

      vsAssemblyScriptPaneModel.scriptEnabled(checkBoxAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(checkBoxAssemblyInfo.getScript() == null ?
                                              "" : checkBoxAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   /**
    * Sets the specified check box assembly info.
    *
    * @param objectId   the check box id
    * @param value the check box property dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/checkbox-property-dialog-model/{objectId}")
   public void setCheckboxPropertyModel(@DestinationVariable("objectId") String objectId,
                                        @Payload CheckboxPropertyDialogModel value,
                                        @LinkUri String linkUri,
                                        Principal principal,
                                        CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      CheckBoxVSAssemblyInfo checkBoxAssemblyInfo;

      try {
         ViewsheetService engine = viewsheetService;
         viewsheet = engine.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
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
   }

   /**
    * Check whether the list values columns for the assembly will cause a trap.
    *
    * @param model     the model containing the hyperlink model
    * @param objectId  the object id
    * @param runtimeId the runtime id
    * @param principal the user principal
    *
    * @return the table trap model stating whether or not here is a trap.
    */
   @PostMapping("/api/composer/vs/checkbox-property-dialog-model/checkTrap/{objectId}/**")
   @ResponseBody
   public VSTableTrapModel checkTrap(@RequestBody() CheckboxPropertyDialogModel model,
                                     @PathVariable("objectId") String objectId,
                                     @RemainingPath String runtimeId,
                                     Principal principal) throws Exception
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
      listBindingInfo = this.vsInputService.updateBindingInfo(
         listBindingInfo, selectionListEditorModel.getColumn(),
         selectionListEditorModel.getValue(), viewsheet, principal);
      assemblyInfo.setListBindingInfo(listBindingInfo);
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSInputService vsInputService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDialogService dialogService;
   private final VSTrapService trapService;
   private final ViewsheetService viewsheetService;
}
