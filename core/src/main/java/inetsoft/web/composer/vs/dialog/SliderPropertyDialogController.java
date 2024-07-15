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
import inetsoft.uql.viewsheet.SliderVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SliderVSAssemblyInfo;
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
 * Controller that provides the REST endpoints for the slider property dialog.
 *
 * @since 12.3
 */
@Controller
public class SliderPropertyDialogController {
   /**
    * Creates a new instance of <tt>SliderPropertyDialogController</tt>.
    * @param vsObjectPropertyService VSObjectPropertyService instance
    * @param vsInputService          VSInputService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    * @param viewsheetService
    */
   @Autowired
   public SliderPropertyDialogController(
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
    * Gets the top-level descriptor of the slider.
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId   the slider id
    * @return the slider descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/slider-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public SliderPropertyDialogModel getSliderPropertyDialogModel(@PathVariable("objectId") String objectId,
                                                                 @RemainingPath String runtimeId,
                                                                 Principal principal)
      throws Exception
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
         //TODO decide what to do with exception
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

      vsInputService.getTableName(sliderAssemblyInfo, dataInputPaneModel);
      dataInputPaneModel.setColumnValue(sliderAssemblyInfo.getColumnValue());
      dataInputPaneModel.setRowValue(sliderAssemblyInfo.getRowValue());
      dataInputPaneModel.setTargetTree(
         this.vsInputService.getInputTablesTree(rvs, false, principal));
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

   /**
    * Sets the top-level descriptor of the specified slider.
    *
    * @param objectId   the slider id
    * @param value the slider descriptor.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/slider-property-dialog-model/{objectId}")
   public void setSliderPropertyDialogModel(@DestinationVariable("objectId") String objectId,
                                            @Payload SliderPropertyDialogModel value,
                                            @LinkUri String linkUri,
                                            Principal principal,
                                            CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      SliderVSAssemblyInfo sliderAssemblyInfo;

      try {
         viewsheet = viewsheetService.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
         SliderVSAssembly sliderAssembly = (SliderVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         sliderAssemblyInfo = (SliderVSAssemblyInfo) Tool.clone(sliderAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         //TODO decide what to do with exception
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

      // TODO validate column/row variable/expression type
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
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSInputService vsInputService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
}

