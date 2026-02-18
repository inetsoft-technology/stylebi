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
import inetsoft.uql.viewsheet.SpinnerVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SpinnerVSAssemblyInfo;
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
 * Controller that provides the REST endpoints for the spinner property dialog.
 *
 * @since 12.3
 */
@Controller
public class SpinnerPropertyDialogController {
   /**
    * Creates a new instance of <tt>SpinnerPropertyDialogController</tt>.
    * @param vsObjectPropertyService VSObjectPropertyService instance
    * @param vsInputService          VSInputService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    * @param viewsheetService
    */
   @Autowired
   public SpinnerPropertyDialogController(
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
    * Gets the top-level descriptor of the spinner.
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId   the spinner id
    * @return the spinner descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/spinner-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public SpinnerPropertyDialogModel getSpinnerPropertyDialogModel(@PathVariable("objectId") String objectId,
                                                                   @RemainingPath String runtimeId,
                                                                   Principal principal)
      throws Exception
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
         //TODO decide what to do with exception
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

      vsInputService.getTableName(spinnerAssemblyInfo, dataInputPaneModel);
      dataInputPaneModel.setColumnValue(spinnerAssemblyInfo.getColumnValue());
      dataInputPaneModel.setRowValue(spinnerAssemblyInfo.getRowValue());
      dataInputPaneModel.setTargetTree(
         this.vsInputService.getInputTablesTree(rvs, false, principal));
      dataInputPaneModel.setWriteBackDirectly(spinnerAssemblyInfo.getWriteBackValue());

      vsAssemblyScriptPaneModel.scriptEnabled(spinnerAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(spinnerAssemblyInfo.getScript() == null ?
                                              "" : spinnerAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   /**
    * Sets the top-level descriptor of the specified spinner.
    *
    * @param objectId   the spinner id
    * @param value the spinner descriptor.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/spinner-property-dialog-model/{objectId}")
   public void setSpinnerPropertyDialogModel(@DestinationVariable("objectId") String objectId,
                                             @Payload SpinnerPropertyDialogModel value,
                                             @LinkUri String linkUri,
                                             Principal principal,
                                             CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      SpinnerVSAssemblyInfo spinnerAssemblyInfo;

      try {
         viewsheet = viewsheetService.getViewsheet(
            this.runtimeViewsheetRef.getRuntimeId(), principal);
         SpinnerVSAssembly spinnerAssembly = (SpinnerVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         spinnerAssemblyInfo = (SpinnerVSAssemblyInfo) Tool.clone(spinnerAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         //TODO decide what to do with exception
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

      // TODO validate column/row variable/expression type
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
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSInputService vsInputService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
}
