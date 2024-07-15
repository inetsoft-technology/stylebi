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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
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

/**
 * Controller that provides the REST endpoints for the textinput property dialog.
 *
 * @since 12.3
 */
@Controller
public class TextInputPropertyDialogController {
   /**
    * Creates a new instance of <tt>TextInputPropertyDialogController</tt>.
    * @param vsObjectPropertyService VSObjectPropertyService instance
    * @param vsInputService          VSInputService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    * @param viewsheetService
    */
   @Autowired
   public TextInputPropertyDialogController(
      VSObjectPropertyService vsObjectPropertyService,
      VSInputService vsInputService,
      RuntimeViewsheetRef runtimeViewsheetRef,
      VSDialogService dialogService,
      ViewsheetService viewsheetService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.vsInputService = vsInputService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.dialogService = dialogService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Gets the top-level descriptor of the textinput.
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId   the text input id
    * @return the textinput descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/textinput-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public TextInputPropertyDialogModel getTextInputPropertyDialogModel(
      @PathVariable("objectId") String objectId,
      @RemainingPath String runtimeId,
      Principal principal)
      throws Exception
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
         //TODO decide what to do with exception
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

      vsInputService.getTableName(textInputAssemblyInfo, dataInputPaneModel);
      dataInputPaneModel.setColumnValue(textInputAssemblyInfo.getColumnValue());
      dataInputPaneModel.setRowValue(textInputAssemblyInfo.getRowValue());
      dataInputPaneModel.setTargetTree(
         this.vsInputService.getInputTablesTree(rvs, false, principal));
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

   /**
    * Sets the top-level descriptor of the specified textinput.
    *
    * @param objectId   the text input id
    * @param value the viewsheet descriptor.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/textinput-property-dialog-model/{objectId}")
   public void setTextInputPropertyDialogModel(@DestinationVariable("objectId") String objectId,
                                               @Payload TextInputPropertyDialogModel value,
                                               @LinkUri String linkUri,
                                               Principal principal,
                                               CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      TextInputVSAssemblyInfo textInputAssemblyInfo;

      try {
         viewsheet = viewsheetService.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
         TextInputVSAssembly textInputAssembly = (TextInputVSAssembly)
            viewsheet.getViewsheet().getAssembly(objectId);
         textInputAssemblyInfo = (TextInputVSAssemblyInfo)
            Tool.clone(textInputAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         //TODO decide what to do with exception
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

      // TODO validate column/row variable/expression type
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
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSInputService vsInputService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
}
