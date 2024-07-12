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
package inetsoft.web.composer.vs.dialog;


import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.RadioButtonVSAssemblyInfo;
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
import java.util.List;
import java.util.*;

@Controller
public class RadioButtonPropertyDialogController {
   /**
    * Creates a new instance of <tt>RadioButtonPropertyController</tt>.
    *  @param vsObjectPropertyService VSObjectPropertyService instance
    * @param vsInputService          VSInputService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public RadioButtonPropertyDialogController(VSObjectPropertyService vsObjectPropertyService,
                                              VSInputService vsInputService,
                                              RuntimeViewsheetRef runtimeViewsheetRef,
                                              VSDialogService dialogService,
                                              ViewsheetService viewsheetService,
                                              VSTrapService trapService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.vsInputService = vsInputService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.dialogService = dialogService;
      this.viewsheetService = viewsheetService;
      this.trapService = trapService;
   }

   /**
    * Gets the radio button property dialog model.
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId   the radio button id
    * @return the radio button property dialog model.
    */
   @RequestMapping(
      value = "/api/composer/vs/radiobutton-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public RadioButtonPropertyDialogModel getRadioButtonPropertyModel(@PathVariable("objectId") String objectId,
                                                                     @RemainingPath String runtimeId,
                                                                     Principal principal)
      throws Exception
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
         //TODO decide what to do with exception
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
      List<String[]> tablesList = this.vsInputService.getInputTablesArray(rvs, principal);
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

      vsInputService.getTableName(radioButtonAssemblyInfo, dataInputPaneModel);
      dataInputPaneModel.setColumnValue(radioButtonAssemblyInfo.getColumnValue());
      dataInputPaneModel.setWriteBackDirectly(radioButtonAssemblyInfo.getWriteBackValue());
      dataInputPaneModel.setRowValue(radioButtonAssemblyInfo.getRowValue());
      dataInputPaneModel.setTargetTree(
         this.vsInputService.getInputTablesTree(rvs, false, principal));
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

   /**
    * Sets the specified radio button assembly info.
    *
    * @param objectId   the radio button id
    * @param value the radio button property dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/radiobutton-property-dialog-model/{objectId}")
   public void setRadioButtonPropertyModel(@DestinationVariable("objectId") String objectId,
                                           @Payload RadioButtonPropertyDialogModel value,
                                           @LinkUri String linkUri,
                                           Principal principal,
                                           CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      RadioButtonVSAssemblyInfo radioButtonAssemblyInfo;

      try {
         ViewsheetService engine = viewsheetService;
         viewsheet = engine.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
         RadioButtonVSAssembly radioButtonAssembly = (RadioButtonVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         radioButtonAssemblyInfo = (RadioButtonVSAssemblyInfo) Tool.clone(radioButtonAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         //TODO decide what to do with exception
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

      // TODO validate column/row variable/expression type
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
   @PostMapping("/api/composer/vs/radiobutton-property-dialog-model/checkTrap/{objectId}/**")
   @ResponseBody
   public VSTableTrapModel checkTrap(@RequestBody() RadioButtonPropertyDialogModel model,
                                     @PathVariable("objectId") String objectId,
                                     @RemainingPath String runtimeId,
                                     Principal principal) throws Exception
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

   private void setListValues(RadioButtonVSAssemblyInfo assemblyInfo,
                              RadioButtonPropertyDialogModel model,
                              RuntimeViewsheet rvs,
                              Principal principal)
      throws Exception
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
      listBindingInfo = this.vsInputService.updateBindingInfo(
         listBindingInfo, selectionListEditorModel.getColumn(),
         selectionListEditorModel.getValue(), rvs, principal);
      assemblyInfo.setListBindingInfo(listBindingInfo);
   }

   private final VSInputService vsInputService;
   private final VSObjectPropertyService vsObjectPropertyService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
   private final VSTrapService trapService;
}
