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
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.ComposerVSTableController;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller that provides the endpoints for the column option dialog.
 *
 * @since 12.3
 */
@Controller
public class ColumnOptionDialogController {
   /**
    * Creates a new instance of <tt>ColumnOptionDialogController</tt>.
    *
    * @param runtimeViewsheetRef RuntimeViewsheetRef instance
    * @param placeholderService  PlaceholderService instance
    * @param viewsheetService    Viewsheet engine
    * @param vsInputService      VSInputService instance
    */
   @Autowired
   public ColumnOptionDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                       PlaceholderService placeholderService,
                                       ViewsheetService viewsheetService,
                                       VSInputService vsInputService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.viewsheetService = viewsheetService;
      this.vsInputService = vsInputService;
   }

   /**
    * Gets the model for the column option dialog.
    *
    * @param objectId  the object identifier.
    * @param col       the column of the selected cell.
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param principal the user information.
    *
    * @return the model.
    */
   @RequestMapping(
      value = "/api/composer/vs/column-option-dialog-model",
      method = RequestMethod.GET
   )
   @ResponseBody
   public ColumnOptionDialogModel getColumnOptionDialogModel(
      @RequestParam("objectId") String objectId,
      @RequestParam("col") Integer col,
      @RequestParam("runtimeId") String runtimeId,
      Principal principal)
         throws Exception
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

            List<String[]> tablesList = this.vsInputService.getInputTablesArray(rvs, principal);
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

      List<String[]> tablesList = this.vsInputService.getInputTablesArray(rvs, principal);
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

   /**
    * Sets information gathered from the column option.
    *
    * @param objectId   the object id.
    * @param col        the column index.
    * @param model      the hyperlink dialog model.
    * @param principal  the user information.
    * @param dispatcher the command dispatcher.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/column-option-dialog-model/{objectId}/{col}")
   public void setColumnOptionDialogModel(@DestinationVariable("objectId") String objectId,
                                          @DestinationVariable("col") int col,
                                          @Payload ColumnOptionDialogModel model,
                                          Principal principal,
                                          CommandDispatcher dispatcher,
                                          @LinkUri String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
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
               listBindingInfo = this.vsInputService.updateBindingInfo(
                  listBindingInfo, selectionListEditorModel.getColumn(),
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
      this.placeholderService
         .execute(rvs, assembly.getAbsoluteName(), linkUri, hint, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final ViewsheetService viewsheetService;
   private final VSInputService vsInputService;
}
