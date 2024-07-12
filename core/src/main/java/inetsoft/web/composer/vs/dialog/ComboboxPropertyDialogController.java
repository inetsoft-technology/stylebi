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
import inetsoft.report.composition.execution.InputVSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ComboBoxVSAssemblyInfo;
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
import inetsoft.web.vswizard.model.VSWizardConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller that provides the REST endpoints for the combobox property dialog.
 *
 * @since 12.3
 */
@Controller
public class ComboboxPropertyDialogController {
   /**
    * Creates a new instance of <tt>ComboboxPropertyDialogController</tt>.
    * @param vsObjectPropertyService VSObjectPropertyService instance
    * @param vsInputService VSInputService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    * @param viewsheetService
    */
   @Autowired
   public ComboboxPropertyDialogController(
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
    * Gets the combo box property dialog model.
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId   the combo box id
    * @return the combo box property dialog model.
    */
   @RequestMapping(
      value = "/api/composer/vs/combobox-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public ComboboxPropertyDialogModel getComboboxPropertyDialogModel(
                                                         @PathVariable("objectId") String objectId,
                                                         @RemainingPath String runtimeId,
                                                         Principal principal)
      throws Exception
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
         //TODO decide what to do with exception
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
      basicGeneralPaneModel.setRefresh(comboBoxAssemblyInfo.isRefresh());

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
      List<String[]> tablesList = this.vsInputService.getInputTablesArray(rvs, principal);
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

      vsInputService.getTableName(comboBoxAssemblyInfo, dataInputPaneModel);
      dataInputPaneModel.setColumnValue(comboBoxAssemblyInfo.getColumnValue());
      dataInputPaneModel.setRowValue(comboBoxAssemblyInfo.getRowValue());
      dataInputPaneModel.setTargetTree(
         this.vsInputService.getInputTablesTree(rvs, false, principal));
      dataInputPaneModel.setWriteBackDirectly(comboBoxAssemblyInfo.getWriteBackValue());
      vsAssemblyScriptPaneModel.scriptEnabled(comboBoxAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(comboBoxAssemblyInfo.getScript() == null ?
                                              "" : comboBoxAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   /**
    * Sets the specified combo box assembly info.
    *
    * @param objectId   the combo box id
    * @param value the combo box dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/combobox-property-dialog-model/{objectId}")
   public void setComboboxPropertyDialogModel(@DestinationVariable("objectId") String objectId,
                                              @Payload ComboboxPropertyDialogModel value,
                                              @LinkUri String linkUri,
                                              Principal principal,
                                              CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      ComboBoxVSAssemblyInfo comboBoxAssemblyInfo;

      try {
         ViewsheetService engine = viewsheetService;
         viewsheet = engine.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
         ComboBoxVSAssembly comboBoxAssembly =
            (ComboBoxVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         comboBoxAssemblyInfo =
            (ComboBoxVSAssemblyInfo) Tool.clone(comboBoxAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         //TODO decide what to do with exception
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
   @PostMapping("/api/composer/vs/combobox-property-dialog-model/checkTrap/{objectId}/**")
   @ResponseBody
   public VSTableTrapModel checkTrap(@RequestBody() ComboboxPropertyDialogModel model,
                                     @PathVariable("objectId") String objectId,
                                     @RemainingPath String runtimeId,
                                     Principal principal) throws Exception
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

   @PostMapping("/api/composer/vs/comboboxeditor/larbel")
   @ResponseBody
   public ComboBoxDefaultValueListModel[] getComboBoxList(@RequestParam("runtimeId") String runtimeId,
                                                 @RequestParam("objectId") String objectId,
                                                 @RequestParam("sortType") int sortType,
                                                 @RequestParam("embeddedDataDown") boolean embeddedDataDown,
                                                 @RequestBody ComboBoxEditorModel model,
                                                 Principal principal)throws Exception
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
            listBindingInfo = this.vsInputService.updateBindingInfo(
                  listBindingInfo, selectionListEditorModel.getColumn(),
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
         listBindingInfo = this.vsInputService.updateBindingInfo(
            listBindingInfo, selectionListEditorModel.getColumn(),
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

   private final VSInputService vsInputService;
   private final VSObjectPropertyService vsObjectPropertyService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDialogService dialogService;
   private final VSTrapService trapService;
   private final ViewsheetService viewsheetService;
}
