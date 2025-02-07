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
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
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
 * Controller that provides the REST endpoints for the table view property
 * dialog
 *
 * @since 12.3
 */
@Controller
public class TableViewPropertyDialogController {
   /**
    * Creates a new instance of <tt>TablePropertyController</tt>.
    * @param vsObjectPropertyService VSObjectPropertyService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    * @param viewsheetService
    */
   @Autowired
   public TableViewPropertyDialogController(
      VSObjectPropertyService vsObjectPropertyService,
      RuntimeViewsheetRef runtimeViewsheetRef,
      VSDialogService dialogService,
      ViewsheetService viewsheetService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.dialogService = dialogService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Gets the top-level descriptor of the table view
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the table view.
    *
    * @return the rectangle descriptor.
    */

   @RequestMapping(
      value = "/api/composer/vs/table-view-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public TableViewPropertyDialogModel getTableViewPropertyDialogModel(@PathVariable("objectId") String objectId,
                                                                       @RemainingPath String runtimeId,
                                                                       Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeViewsheet rvs;
      Viewsheet vs;
      TableVSAssembly tableAssembly;
      TableVSAssemblyInfo tableAssemblyInfo;
      rvs = viewsheetService.getViewsheet(runtimeId, principal);

      try {
         rvs.getViewsheetSandbox().lockRead();
         vs = rvs.getViewsheet();
         tableAssembly = (TableVSAssembly) vs.getAssembly(objectId);
         tableAssemblyInfo = (TableVSAssemblyInfo) tableAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }
      finally {
         rvs.getViewsheetSandbox().unlockRead();
      }

      TableViewPropertyDialogModel result = new TableViewPropertyDialogModel();
      TableViewGeneralPaneModel tableViewGeneralPaneModel = result.getTableViewGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel =
         tableViewGeneralPaneModel.getGeneralPropPaneModel();
      TitlePropPaneModel titlePropPaneModel = tableViewGeneralPaneModel.getTitlePropPaneModel();
      TableStylePaneModel tableStylePaneModel = tableViewGeneralPaneModel.getTableStylePaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         tableViewGeneralPaneModel.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      TableAdvancedPaneModel tableAdvancedPaneModel = result.getTableAdvancedPaneModel();
      TipPaneModel tipPaneModel = tableAdvancedPaneModel.getTipPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel =
         VSAssemblyScriptPaneModel.builder();
      boolean isEmbeddedTable = tableAssemblyInfo instanceof EmbeddedTableVSAssemblyInfo;

      tableViewGeneralPaneModel.setShowMaxRows(!isEmbeddedTable);
      tableViewGeneralPaneModel.setMaxRows(tableAssemblyInfo.getMaxRows());
      tableViewGeneralPaneModel.setShowSubmitOnChange(isEmbeddedTable);
      tableViewGeneralPaneModel.setSubmitOnChange(isEmbeddedTable &&
         Boolean.valueOf(((EmbeddedTableVSAssemblyInfo) tableAssemblyInfo).getSubmitOnChangeValue()));

      titlePropPaneModel.setVisible(tableAssemblyInfo.getTitleVisibleValue());
      titlePropPaneModel.setTitle(tableAssemblyInfo.getTitleValue());

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(tableAssemblyInfo.getEnabledValue());

      basicGeneralPaneModel.setName(tableAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setNameEditable(!tableAssemblyInfo.isWizardTemporary());
      basicGeneralPaneModel.setPrimary(tableAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(tableAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(vs, tableAssemblyInfo.getAbsoluteName()));

      TableViewStylePaneController styleController = new TableViewStylePaneController();
      tableStylePaneModel.setTableStyle(tableAssemblyInfo.getTableStyleValue());
      tableStylePaneModel.setStyleTree(styleController.getStyleTree(rvs, principal, false));

      Point pos = dialogService.getAssemblyPosition(tableAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(tableAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setTitleHeight(tableAssemblyInfo.getTitleHeightValue());
      sizePositionPaneModel.setContainer(tableAssembly.getContainer() != null);

      tableAdvancedPaneModel.setFormVisible(LicenseManager.isComponentAvailable(LicenseManager.LicenseComponent.FORM));
      tableAdvancedPaneModel.setForm(tableAssemblyInfo.getFormValue());
      tableAdvancedPaneModel.setInsert(tableAssemblyInfo.getInsertValue());
      tableAdvancedPaneModel.setDel(tableAssemblyInfo.getDelValue());
      tableAdvancedPaneModel.setEdit(tableAssemblyInfo.getEditValue());
      tableAdvancedPaneModel.setWriteBack(tableAssemblyInfo.getWriteBackValue());
      tableAdvancedPaneModel.setEnableAdhoc(tableAssemblyInfo.getEnableAdhocValue());
      tableAdvancedPaneModel.setShrinkEnabled(!tableAssemblyInfo.getColumnSelection().isEmpty());
      tableAdvancedPaneModel.setShrink(tableAssemblyInfo.getShrinkValue());

      tipPaneModel.setTipOption(tableAssemblyInfo.getTipOptionValue() == TipVSAssemblyInfo.VIEWTIP_OPTION);
      String tipView = tableAssemblyInfo.getTipViewValue();
      tipView = vs.getAssembly(tipView) != null ? tipView : null;
      tipPaneModel.setTipView(tipView);
      tipPaneModel.setAlpha(tableAssemblyInfo.getAlphaValue() == null ?
                               "100" : tableAssemblyInfo.getAlphaValue());
      String[] flyoverViews = tableAssemblyInfo.getFlyoverViewsValue();
      tipPaneModel.setFlyOverViews(flyoverViews == null ? new String[0] : flyoverViews);
      tipPaneModel.setFlyOnClick(Boolean.valueOf(tableAssemblyInfo.getFlyOnClickValue()));
      tipPaneModel.setPopComponents(
         this.vsObjectPropertyService.getSupportedTablePopComponents(
            rvs, tableAssemblyInfo.getAbsoluteName(), false));
      tipPaneModel.setFlyoverComponents(
         this.vsObjectPropertyService.getSupportedTablePopComponents(
            rvs, tableAssemblyInfo.getAbsoluteName(), true));
      String srctbl = tableAssemblyInfo.getTableName();
      tipPaneModel.setDataViewEnabled(srctbl != null && !VSUtil.isVSAssemblyBinding(srctbl));

      vsAssemblyScriptPaneModel.scriptEnabled(tableAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(tableAssemblyInfo.getScript() == null ?
                                              "" : tableAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   /**
    * Sets the specified table assembly info.
    *
    * @param objectId   the table id
    * @param value the table property dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/table-view-property-dialog-model/{objectId}")
   public void setTablePropertyModel(@DestinationVariable("objectId") String objectId,
                                     @Payload TableViewPropertyDialogModel value,
                                     @LinkUri String linkUri,
                                     Principal principal,
                                     CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      TableVSAssembly tableAssembly;
      TableVSAssemblyInfo tableAssemblyInfo;

      try {
         viewsheet = viewsheetService.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
         tableAssembly = (TableVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         tableAssemblyInfo = (TableVSAssemblyInfo) Tool.clone(tableAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      TableViewGeneralPaneModel tableViewGeneralPaneModel = value.getTableViewGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel =
         tableViewGeneralPaneModel.getGeneralPropPaneModel();
      TitlePropPaneModel titlePropPaneModel = tableViewGeneralPaneModel.getTitlePropPaneModel();
      TableStylePaneModel tableStylePaneModel = tableViewGeneralPaneModel.getTableStylePaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         tableViewGeneralPaneModel.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      TableAdvancedPaneModel tableAdvancedPaneModel = value.getTableAdvancedPaneModel();
      TipPaneModel tipPaneModel = tableAdvancedPaneModel.getTipPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      if(tableViewGeneralPaneModel.isShowSubmitOnChange()) {
         ((EmbeddedTableVSAssemblyInfo) tableAssemblyInfo).setSubmitOnChangeValue(
            tableViewGeneralPaneModel.isSubmitOnChange());
      }

      tableAssemblyInfo.setMaxRows(tableViewGeneralPaneModel.getMaxRows());

      tableAssemblyInfo.setTitleVisibleValue(titlePropPaneModel.isVisible());
      tableAssemblyInfo.setTitleValue(titlePropPaneModel.getTitle());

      tableAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());

      tableAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      tableAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());

      tableAssemblyInfo.setTableStyleValue(tableStylePaneModel.getTableStyle());

      dialogService.setAssemblySize(tableAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(tableAssemblyInfo, sizePositionPaneModel);
      tableAssemblyInfo.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());

      if(!tableAssemblyInfo.isForm() && tableAdvancedPaneModel.isForm()) {
         ColumnSelection columnSelection = tableAssemblyInfo.getColumnSelection();
         ColumnSelection cloneSelection = (ColumnSelection) Tool.clone(columnSelection);
         columnSelection.clear();

         for(int i = 0; i < cloneSelection.getAttributeCount(); i++) {
            ColumnRef ref = (ColumnRef) cloneSelection.getAttribute(i);

            if(ref == null) {
               continue;
            }

            columnSelection.addAttribute(i, FormRef.toFormRef(ref));
         }
      }

      if(!tableAdvancedPaneModel.isForm()) {
         tableAssemblyInfo.getHiddenColumns().removeAllAttributes();
      }

      tableAssemblyInfo.setShrinkValue(tableAdvancedPaneModel.isShrink());
      tableAssemblyInfo.setFormValue(tableAdvancedPaneModel.isForm());
      tableAssemblyInfo.setInsertValue(tableAdvancedPaneModel.isInsert());
      tableAssemblyInfo.setDelValue(tableAdvancedPaneModel.isDel());
      tableAssemblyInfo.setEditValue(tableAdvancedPaneModel.isEdit());
      tableAssemblyInfo.setWriteBackValue(tableAdvancedPaneModel.isWriteBack());
      tableAssemblyInfo.setEnableAdhocValue(tableAdvancedPaneModel.isEnableAdhoc());

      if(tipPaneModel.isTipOption() && !tableAdvancedPaneModel.isForm()) {
         tableAssemblyInfo.setTipOptionValue(TipVSAssemblyInfo.VIEWTIP_OPTION);
         String str = tipPaneModel.getAlpha();
         tableAssemblyInfo.setAlphaValue(str != null && str.length() > 0 ? str : null);

         if(tipPaneModel.getTipView() != null && !"null".equals(tipPaneModel.getTipView())) {
            tableAssemblyInfo.setTipViewValue(tipPaneModel.getTipView());
         }
         else {
            tableAssemblyInfo.setTipViewValue(null);
         }
      }
      else {
         tableAssemblyInfo.setTipOptionValue(TipVSAssemblyInfo.TOOLTIP_OPTION);
         tableAssemblyInfo.setTipViewValue(null);
      }

      if(!tableAdvancedPaneModel.isForm()) {
         String[] flyovers = VSUtil.getValidFlyovers(tipPaneModel.getFlyOverViews(),
                                                     viewsheet.getViewsheet());
         tableAssemblyInfo.setFlyoverViewsValue(flyovers);
      }
      else {
         tableAssemblyInfo.setFlyoverViewsValue(new String[0]);
      }

      tableAssemblyInfo.setFlyOnClickValue(tipPaneModel.isFlyOnClick() + "");

      if(vsAssemblyScriptPaneModel != null) {
         tableAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
         tableAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());
      }

      tableAssemblyInfo.resetRColumnWidths();

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, tableAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
}
