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

package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.calendar.*;
import inetsoft.web.viewsheet.model.calendar.CalendarDateFormatModel;
import inetsoft.web.viewsheet.service.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;

@Service
@ClusterProxy
public class VSCalendarService {

   public VSCalendarService(CoreLifecycleService coreLifecycleService,
                            VSObjectPropertyService vsObjectPropertyService,
                            ViewsheetService viewsheetService) {
      this.coreLifecycleService = coreLifecycleService;
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void toggleRangeComparison(@ClusterProxyKey String vsId, String assemblyName,
                                     ToggleRangeComparisonEvent event, String linkUri, Principal principal,
                                     CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalendarVSAssembly calendarAssembly = (CalendarVSAssembly) viewsheet.getAssembly(assemblyName);
      CalendarVSAssemblyInfo calendarInfo = (CalendarVSAssemblyInfo) Tool.clone(calendarAssembly.getVSAssemblyInfo());
      calendarInfo.setPeriod(event.isPeriod());
      calendarInfo.setDates(event.getDates(), true);
      calendarInfo.setCurrentDate1(event.getCurrentDate1());
      calendarInfo.setCurrentDate2(event.getCurrentDate2());

      this.vsObjectPropertyService.editObjectProperty(
         rvs, calendarInfo, assemblyName, assemblyName, linkUri, principal, dispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void toggleYearView(@ClusterProxyKey String vsId, String assemblyName,
                              ToggleYearViewEvent event, String linkUri,
                              Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalendarVSAssembly calendarAssembly = (CalendarVSAssembly)
         viewsheet.getAssembly(assemblyName);
      CalendarVSAssemblyInfo calendarInfo = (CalendarVSAssemblyInfo)
         Tool.clone(calendarAssembly.getVSAssemblyInfo());
      calendarInfo.setYearViewValue(event.isYearView());
      // dates are reset when toggling year view
      calendarInfo.setDates(new String[0]);
      calendarInfo.setCurrentDate1(event.getCurrentDate1());
      calendarInfo.setCurrentDate2(event.getCurrentDate2());

      this.vsObjectPropertyService.editObjectProperty(
         rvs, calendarInfo, assemblyName, assemblyName, linkUri, principal, dispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void clearCalendar(@ClusterProxyKey String vsId, String assemblyName,
                             Principal principal, CommandDispatcher dispatcher,
                             @LinkUri String linkUri) throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalendarVSAssembly calendarAssembly = (CalendarVSAssembly) viewsheet.getAssembly(assemblyName);
      CalendarVSAssemblyInfo calendarInfo = (CalendarVSAssemblyInfo) (Tool.clone(calendarAssembly.getVSAssemblyInfo()));
      calendarInfo.setDates(new String[0]);
      applyCalendarInfo(calendarAssembly, calendarInfo, rvs, dispatcher, linkUri, null);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void toggleDoubleCalendar(@ClusterProxyKey String vsId, String assemblyName,
                                    ToggleDoubleCalendarEvent event, String linkUri,
                                    Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalendarVSAssembly calendarAssembly = (CalendarVSAssembly) viewsheet.getAssembly(assemblyName);
      CalendarVSAssemblyInfo calendarInfo = (CalendarVSAssemblyInfo) Tool.clone(calendarAssembly.getVSAssemblyInfo());
      // dates are reset when toggling double calendar
      calendarInfo.setDates(new String[0]);
      calendarInfo.setCurrentDate1(event.getCurrentDate1());
      calendarInfo.setCurrentDate2(event.getCurrentDate2());
      Dimension size =
         calendarInfo.getLayoutSize() != null && !viewsheet.getViewsheetInfo().isScaleToScreen() ?
            calendarInfo.getLayoutSize() : rvs.getViewsheet().getPixelSize(calendarInfo);

      if(event.isDoubleCalendar()) {
         calendarInfo.setViewModeValue(CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE);
         rvs.setProperty("calendar.submitOnChangeValue",
                         String.valueOf(calendarInfo.getSubmitOnChangeValue()));
         calendarInfo.setSubmitOnChangeValue(false);

         if(!calendarAssembly.isWizardTemporary()) {
            size.width *= 2;
         }
      }
      else {
         calendarInfo.setViewModeValue(CalendarVSAssemblyInfo.SINGLE_CALENDAR_MODE);
         calendarInfo.setPeriod(false);

         if(rvs.getProperty("calendar.submitOnChangeValue") != null) {
            calendarInfo.setSubmitOnChangeValue(
               Boolean.parseBoolean(String.valueOf(rvs.getProperty("calendar.submitOnChangeValue"))));
            rvs.setProperty("calendar.submitOnChangeValue", null);
         }

         if(!calendarAssembly.isWizardTemporary()) {
            size.width = size.width / 2;
         }
      }

      calendarInfo.setPixelSize(size);

      if(calendarInfo.getLayoutSize() != null) {
         calendarInfo.setLayoutSize(size);
      }

      this.vsObjectPropertyService.editObjectProperty(
         rvs, calendarInfo, assemblyName, assemblyName, linkUri, principal, dispatcher);
      CalendarVSAssemblyInfo ncalendarInfo = (CalendarVSAssemblyInfo) calendarAssembly.getVSAssemblyInfo();

      // view mode maybe changed by script.
      if(!calendarAssembly.isWizardTemporary() && ncalendarInfo.getViewMode() != calendarInfo.getViewModeValue()) {
         if(calendarInfo.getViewModeValue() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE) {
            size.width = size.width / 2;
         }
         else {
            size.width *= 2;
         }

         calendarInfo.setPixelSize(size);

         if(calendarInfo.getLayoutSize() != null) {
            calendarInfo.setLayoutSize(size);
         }
      }

      if(viewsheet.getViewsheetInfo().isScaleToScreen() && (rvs.isPreview() || rvs.isViewer())) {
         Object scaleSize = rvs.getProperty("viewsheet.appliedScale");

         if(scaleSize instanceof Dimension && ((Dimension) scaleSize).width > 0 &&
            ((Dimension) scaleSize).height > 0)
         {
            ChangedAssemblyList clist = coreLifecycleService.createList(true, dispatcher, rvs, linkUri);
            this.coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), linkUri, ((Dimension) scaleSize).width,
                                                       ((Dimension) scaleSize).height, false, null, dispatcher, false, false, true, clist);
         }
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void applyCalendar(@ClusterProxyKey String vsId, String assemblyName, CalendarSelectionEvent event,
                             Principal principal, CommandDispatcher dispatcher, String linkUri) throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalendarVSAssembly calendarAssembly = (CalendarVSAssembly) viewsheet.getAssembly(assemblyName);

      if(calendarAssembly == null) {
         return null;
      }

      CalendarVSAssemblyInfo calendarInfo = (CalendarVSAssemblyInfo) (Tool.clone(calendarAssembly.getVSAssemblyInfo()));
      calendarInfo.setDates(event.getDates());
      calendarInfo.setCurrentDate1(event.getCurrentDate1());

      if(event.getCurrentDate2() != null) {
         calendarInfo.setCurrentDate2(event.getCurrentDate2());
      }

      applyCalendarInfo(calendarAssembly, calendarInfo, rvs, dispatcher, linkUri,
                        event.getEventSource());

      return null;
   }

   /**
    * Method called when applying selection for calendar
    * Taken from ApplyVSAssemblyInfoEvent.java
    * @param calendarAssembly    the calendar vs assembly
    * @param newInfo             the new calendar info
    * @param rvs                 the runtime viewsheet
    * @param commandDispatcher   the command dispatcher instance
    * @throws Exception if failed to apply calendar
    */
   private void applyCalendarInfo(CalendarVSAssembly calendarAssembly,
                                  CalendarVSAssemblyInfo newInfo,
                                  RuntimeViewsheet rvs, CommandDispatcher commandDispatcher,
                                  String linkUri, String eventSource)
      throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();
      int hint = calendarAssembly.setVSAssemblyInfo(newInfo);

      String embedded =
         VSUtil.getEmbeddedTableWithSameSource(vs, calendarAssembly);

      if(embedded != null) {
         String msg = Catalog.getCatalog().getString(
            "viewer.viewsheet.selection.notSupportEmbeddedTable",
            embedded);
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(msg);
         messageCommand.setType(MessageCommand.Type.WARNING);
         commandDispatcher.sendCommand(messageCommand);
      }

      ChangedAssemblyList clist = this.coreLifecycleService.createList(true, commandDispatcher,
                                                                       rvs, linkUri);
      box.processChange(calendarAssembly.getAbsoluteName(), hint, clist);
      // Iterate over all assemblies and add to view list if they have
      // hyperlinks that "send selection parameters"
      this.coreLifecycleService.executeInfluencedHyperlinkAssemblies(vs, commandDispatcher,
                                                                     rvs, linkUri, null);

      boolean refresh = true;

      if(!StringUtils.isEmpty(eventSource)) {
         Assembly eventSourceAssembly = null;
         eventSourceAssembly = rvs.getViewsheet().getAssembly(eventSource);

         if(eventSourceAssembly instanceof SubmitVSAssembly) {
            SubmitVSAssembly submitAssembly = (SubmitVSAssembly) eventSourceAssembly;

            refresh = ((SubmitVSAssemblyInfo) submitAssembly.getVSAssemblyInfo()).isRefresh();
         }
      }

      // @davidd bug1366884826731, If there is only one calendar bound
      // to a block, then processChange doesn't call execute. Therefore
      // call it here. In cases where a selection list is also bound,
      // then the following isn't needed. This could be improved.
      this.coreLifecycleService.execute(rvs, calendarAssembly.getAbsoluteName(),
                                        linkUri, clist, commandDispatcher, true);

      if(refresh) {
         this.coreLifecycleService.layoutViewsheet(rvs, rvs.getID(), linkUri, commandDispatcher);
         coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), linkUri, commandDispatcher, false,
                                               true, true, clist);
      }
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String formatString(@ClusterProxyKey String vsId, CalendarDateFormatModel model, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalendarVSAssembly assembly = (CalendarVSAssembly) viewsheet.getAssembly(model.getAssemblyName());
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) assembly.getVSAssemblyInfo();
      String dateFormat = CalendarUtil.getCalendarSelectedDateFormat(info);

      return CalendarUtil.formatSelectedDates(model.getDates(), dateFormat, model.isDoubleCalendar(),
                                              model.isPeriod(), model.isMonthView());
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String formatCalendarTitleView(@ClusterProxyKey String vsId, CalendarDateFormatModel model,
                                         Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalendarVSAssembly assembly = (CalendarVSAssembly) viewsheet.getAssembly(model.getAssemblyName());

      if(assembly == null) {
         return model.getDates();
      }

      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) assembly.getVSAssemblyInfo();
      FormatInfo fmtInfo = info.getFormatInfo();
      TableDataPath dataPath = new TableDataPath(-1, TableDataPath.CALENDAR_TITLE);
      VSCompositeFormat compositeFormat = fmtInfo.getFormat(dataPath, false);

      return CalendarUtil.formatTitle(model.getDates(), assembly.isYearView(), compositeFormat);
   }


   private final CoreLifecycleService coreLifecycleService;
   private final VSObjectPropertyService vsObjectPropertyService;
   private final ViewsheetService viewsheetService;
}
