/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@ClusterProxy
public class CalendarPropertyDialogService {

   public CalendarPropertyDialogService(VSObjectPropertyService vsObjectPropertyService,
                                        VSOutputService vsOutputService,
                                        VSDialogService dialogService,
                                        ViewsheetService viewsheetService,
                                        VSTrapService trapService,
                                        VSAssemblyInfoHandler assemblyInfoHandler)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.vsOutputService = vsOutputService;
      this.dialogService = dialogService;
      this.viewsheetService = viewsheetService;
      this.trapService = trapService;
      this.assemblyInfoHandler = assemblyInfoHandler;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public CalendarPropertyDialogModel getCalendarPropertyModel(@ClusterProxyKey String runtimeId,
                                                               String objectId, Principal principal) throws Exception
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
      calendarDataPaneModel.setGrayedOutFields(assemblyInfoHandler.getGrayedOutFields(rvs));
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

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkVSTrap(@ClusterProxyKey String runtimeId,
                                       CalendarPropertyDialogModel value, String objectId,
                                       Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      CalendarVSAssembly calendar = (CalendarVSAssembly) rvs.getViewsheet().getAssembly(objectId);

      if(calendar == null) {
         return VSTableTrapModel.builder()
            .showTrap(false)
            .build();
      }

      VSAssemblyInfo oldAssemblyInfo = (VSAssemblyInfo) Tool.clone(calendar.getVSAssemblyInfo());
      CalendarVSAssemblyInfo info =
         (CalendarVSAssemblyInfo) Tool.clone(calendar.getVSAssemblyInfo());
      CalendarDataPaneModel calendarDataPaneModel = value.getCalendarDataPaneModel();

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

      rvs.getViewsheet().getAssembly(objectId).setVSAssemblyInfo(info);
      VSTableTrapModel trap = trapService.checkTrap(rvs, oldAssemblyInfo, info);
      rvs.getViewsheet().getAssembly(objectId).setVSAssemblyInfo(oldAssemblyInfo);

      return trap;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setCalendarPropertyModel(@ClusterProxyKey String runtimeId, String objectId,
                                        CalendarPropertyDialogModel value, String linkUri,
                                        Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet viewsheet;
      CalendarVSAssemblyInfo info;

      try {
         viewsheet = viewsheetService.getViewsheet(runtimeId, principal);
         CalendarVSAssembly calendarAssembly = (CalendarVSAssembly)
            viewsheet.getViewsheet().getAssembly(objectId);
         info = (CalendarVSAssemblyInfo)
            Tool.clone(calendarAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
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

      return null;
   }


   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSOutputService vsOutputService;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
   private final VSTrapService trapService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
}
