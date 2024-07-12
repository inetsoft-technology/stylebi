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
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
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
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller that provides the REST endpoints for the submit property dialog.
 *
 * @since 12.3
 */
@Controller
public class CalendarPropertyDialogController {
   /**
    * Creates a new instance of <tt>CalendarPropertyController</tt>.
    *  @param vsObjectPropertyService VSObjectPropertyService instance
    * @param vsOutputService         VSOutputService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    * @param viewsheetService
    */
   @Autowired
   public CalendarPropertyDialogController(
      VSObjectPropertyService vsObjectPropertyService,
      VSOutputService vsOutputService,
      RuntimeViewsheetRef runtimeViewsheetRef,
      VSDialogService dialogService,
      ViewsheetService viewsheetService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.vsOutputService = vsOutputService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.dialogService = dialogService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Gets the calendar property dialog model.
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId   the calendar id
    * @return the calendar property dialog model.
    */
   @RequestMapping(
      value = "/api/composer/vs/calendar-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public CalendarPropertyDialogModel getCalendarPropertyModel(
      @PathVariable("objectId") String objectId, @RemainingPath String runtimeId,
      Principal principal)
         throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      CalendarVSAssembly calendarAssembly;
      CalendarVSAssemblyInfo calendarAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         calendarAssembly = (CalendarVSAssembly) vs.getAssembly(objectId);
         calendarAssemblyInfo = (CalendarVSAssemblyInfo) calendarAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      CalendarPropertyDialogModel result = new CalendarPropertyDialogModel();
      CalendarGeneralPaneModel calendarGeneralPane = result.getCalendarGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel = calendarGeneralPane.getTitlePropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         calendarGeneralPane.getSizePositionPaneModel();
      GeneralPropPaneModel generalPropPaneModel =
         calendarGeneralPane.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel =
         generalPropPaneModel.getBasicGeneralPaneModel();
      CalendarDataPaneModel calendarDataPaneModel = result.getCalendarDataPaneModel();
      CalendarAdvancedPaneModel calendarAdvancedPaneModel =
         result.getCalendarAdvancedPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel =
         VSAssemblyScriptPaneModel.builder();

      titlePropPaneModel.setVisible(calendarAssemblyInfo.getTitleVisibleValue());
      titlePropPaneModel.setTitle(calendarAssemblyInfo.getTitleValue());

      Point pos = dialogService.getAssemblyPosition(calendarAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(calendarAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setTitleHeight(calendarAssemblyInfo.getTitleHeightValue());
      sizePositionPaneModel.setContainer(calendarAssembly.getContainer() != null);

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(calendarAssemblyInfo.getEnabledValue());

      basicGeneralPaneModel.setName(calendarAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(calendarAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(calendarAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, calendarAssemblyInfo.getAbsoluteName()));

      calendarDataPaneModel.setTargetTree(
         this.vsOutputService.getCalendarTablesTree(rvs, principal));
      final String selectedTable = calendarAssemblyInfo.getFirstTableName();
      calendarDataPaneModel.setSelectedTable(selectedTable);
      calendarDataPaneModel.setAdditionalTables(calendarAssemblyInfo.getAdditionalTableNames());
      ColumnRef columnRef = (ColumnRef) calendarAssemblyInfo.getDataRef();

      if(columnRef != null) {
         OutputColumnRefModel columnModel = new OutputColumnRefModel();
         columnModel.setEntity(columnRef.getEntity());
         columnModel.setAttribute(columnRef.getAttribute());
         columnModel.setDataType(columnRef.getDataType());
         columnModel.setRefType(columnRef.getRefType());
         columnModel.setTable(selectedTable);
         calendarDataPaneModel.setSelectedColumn(columnModel);
      }

      int viewMode = calendarAssemblyInfo.getViewModeValue();
      calendarAdvancedPaneModel.setShowType(calendarAssemblyInfo.getShowTypeValue());
      calendarAdvancedPaneModel.setViewMode(viewMode);
      calendarAdvancedPaneModel.setYearView(calendarAssemblyInfo.getYearViewValue());
      calendarAdvancedPaneModel.setDaySelection(
         calendarAssemblyInfo.getDaySelectionValue());
      calendarAdvancedPaneModel.setSingleSelection(
         calendarAssemblyInfo.getSingleSelectionValue());
      calendarAdvancedPaneModel.setSubmitOnChange(viewMode == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE ?
         false : calendarAssemblyInfo.getSubmitOnChangeValue());
      calendarAdvancedPaneModel.setMin(new DynamicValueModel(calendarAssemblyInfo.getMinValue()));
      calendarAdvancedPaneModel.setMax(new DynamicValueModel(calendarAssemblyInfo.getMaxValue()));

      vsAssemblyScriptPaneModel.scriptEnabled(calendarAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(
         calendarAssemblyInfo.getScript() == null ?
            "" : calendarAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   /**
    * Sets the specified calendar assembly info.
    *
    * @param objectId   the calendar id
    * @param value the calendar property dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/calendar-property-dialog-model/{objectId}")
   public void setCalendarPropertyModel(@DestinationVariable("objectId") String objectId,
                                        @Payload CalendarPropertyDialogModel value,
                                        @LinkUri String linkUri,
                                        Principal principal,
                                        CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      CalendarVSAssemblyInfo info;

      try {
         viewsheet = viewsheetService.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
         CalendarVSAssembly calendarAssembly = (CalendarVSAssembly)
            viewsheet.getViewsheet().getAssembly(objectId);
         info = (CalendarVSAssemblyInfo)
            Tool.clone(calendarAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      CalendarGeneralPaneModel calendarGeneralPane = value.getCalendarGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel = calendarGeneralPane.getTitlePropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         calendarGeneralPane.getSizePositionPaneModel();
      GeneralPropPaneModel generalPropPaneModel = calendarGeneralPane.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      CalendarDataPaneModel calendarDataPaneModel = value.getCalendarDataPaneModel();
      CalendarAdvancedPaneModel calendarAdvancedPaneModel = value.getCalendarAdvancedPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      info.setTitleVisibleValue(titlePropPaneModel.isVisible());
      info.setTitleValue(titlePropPaneModel.getTitle());

      dialogService.setAssemblySize(info, sizePositionPaneModel);
      dialogService.setAssemblyPosition(info, sizePositionPaneModel);
      info.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());

      info.setEnabledValue(generalPropPaneModel.getEnabled());

      info.setPrimary(basicGeneralPaneModel.isPrimary());
      info.setVisibleValue(basicGeneralPaneModel.getVisible());

      OutputColumnRefModel selectedColumn = calendarDataPaneModel.getSelectedColumn();

      if(selectedColumn == null) {
         info.setDataRef(null);
      }
      else {
         info.setFirstTableName(
            VSUtil.getTableName(calendarDataPaneModel.getSelectedTable()));
         List<String> additionalNames =
            calendarDataPaneModel.getAdditionalTables().stream()
               .map(VSUtil::getTableName)
               .collect(Collectors.toList());
         info.setAdditionalTableNames(additionalNames);
         AttributeRef aRef = new AttributeRef(selectedColumn.getEntity(),
                                              selectedColumn.getAttribute());
         aRef.setRefType(selectedColumn.getRefType());
         ColumnRef cRef = new ColumnRef(aRef);
         cRef.setDataType(selectedColumn.getDataType());
         info.setDataRef(cRef);
      }

      int oMode = info.getViewModeValue();
      int type = calendarAdvancedPaneModel.getShowType();
      int mode = calendarAdvancedPaneModel.getViewMode();
      info.setShowTypeValue(type);
      info.setViewModeValue(mode);
      info.setYearViewValue(calendarAdvancedPaneModel.isYearView());
      info.setDaySelectionValue(calendarAdvancedPaneModel.isDaySelection());
      info.setSingleSelectionValue(calendarAdvancedPaneModel.isSingleSelection());
      info.setSubmitOnChangeValue(calendarAdvancedPaneModel.isSubmitOnChange());
      info.setMinValue(calendarAdvancedPaneModel.getMin().convertToValue());
      info.setMaxValue(calendarAdvancedPaneModel.getMax().convertToValue());

      //If switched from double to single calendar, reset dates
      if(oMode != mode) {
         info.setDates(new String[0]);
      }

      info.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      info.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, info, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSOutputService vsOutputService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
}
