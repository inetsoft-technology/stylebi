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
package inetsoft.web.viewsheet.controller;


import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.calendar.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.calendar.CalendarDateFormatModel;
import inetsoft.web.viewsheet.service.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;

/**
 * Controller that provides REST endpoints and message handling for calendar
 * assemblies.
 *
 * @since 12.3
 */
@Controller
public class VSCalendarController {
   /**
    * Creates a new instance of <tt>VSCalendarController</tt>.
    * @param runtimeViewsheetRef the runtime viewsheet reference.
    * @param placeholderService  the placeholder service.
    * @param viewsheetService
    */
   @Autowired
   public VSCalendarController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      PlaceholderService placeholderService,
      VSObjectPropertyService vsObjectPropertyService,
      ViewsheetService viewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Toggles range comparison mode on calendar.
    *
    * @param assemblyName the absolute name of the calendar assembly.
    * @param event        the toggle range comparison event.
    * @param principal    a principal identifying the current user.
    * @param dispatcher   the command dispatcher.
    *
    * @throws Exception if the selection could not be applied.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/calendar/toggleRangeComparison/{name}")
   public void toggleRangeComparison(@DestinationVariable("name") String assemblyName,
                                     @Payload ToggleRangeComparisonEvent event,
                                     @LinkUri String linkUri, Principal principal,
                                     CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalendarVSAssembly calendarAssembly = (CalendarVSAssembly) viewsheet.getAssembly(assemblyName);
      CalendarVSAssemblyInfo calendarInfo = (CalendarVSAssemblyInfo) Tool.clone(calendarAssembly.getVSAssemblyInfo());
      calendarInfo.setPeriod(event.isPeriod());
      calendarInfo.setDates(event.getDates(), true);
      calendarInfo.setCurrentDate1(event.getCurrentDate1());
      calendarInfo.setCurrentDate2(event.getCurrentDate2());

      this.vsObjectPropertyService.editObjectProperty(
         rvs, calendarInfo, assemblyName, assemblyName, linkUri, principal, dispatcher);
   }

   /**
    * Toggles year mode on calendar.
    *
    * @param assemblyName the absolute name of the calendar assembly.
    * @param event        the toggle year view event.
    * @param principal    a principal identifying the current user.
    * @param dispatcher   the command dispatcher.
    *
    * @throws Exception if the toggle failed.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/calendar/toggleYearView/{name}")
   public void toggleYearView(@DestinationVariable("name") String assemblyName,
                              @Payload ToggleYearViewEvent event,
                              @LinkUri String linkUri,
                              Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
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
   }

   /**
    * Clear calendar selections.
    *
    * @param assemblyName the absolute name of the calendar assembly.
    * @param principal    a principal identifying the current user.
    * @param dispatcher   the command dispatcher.
    *
    * @throws Exception if the selection could not be applied.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/calendar/clearCalendar/{name}")
   public void clearCalendar(@DestinationVariable("name") String assemblyName,
                             Principal principal, CommandDispatcher dispatcher,
                             @LinkUri String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalendarVSAssembly calendarAssembly = (CalendarVSAssembly) viewsheet.getAssembly(assemblyName);
      CalendarVSAssemblyInfo calendarInfo = (CalendarVSAssemblyInfo) (Tool.clone(calendarAssembly.getVSAssemblyInfo()));
      calendarInfo.setDates(new String[0]);
      applyCalendarInfo(calendarAssembly, calendarInfo, rvs, dispatcher, linkUri, null);
   }

   /**
    *  Toggle double calendar on calendar.
    *
    * @param assemblyName the absolute name of the calendar assembly.
    * @param event        the toggle double calendar event
    * @param principal    a principal identifying the current user.
    * @param dispatcher   the command dispatcher.
    *
    * @throws Exception if the toggle failed
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/calendar/toggleDoubleCalendar/{name}")
   public void toggleDoubleCalendar(@DestinationVariable("name") String assemblyName,
                                    @Payload ToggleDoubleCalendarEvent event,
                                    @LinkUri String linkUri, Principal principal,
                                    CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
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
            ChangedAssemblyList clist = placeholderService.createList(true, dispatcher, rvs, linkUri);
            this.placeholderService.refreshViewsheet(rvs, rvs.getID(), linkUri, ((Dimension) scaleSize).width,
               ((Dimension) scaleSize).height, false, null, dispatcher, false, false, true, clist);
         }
      }
   }

   /**
    * Apply calendar selections.
    *
    * @param assemblyName the absolute name of the calendar assembly.
    * @param principal    a principal identifying the current user.
    * @param event        the apply calendar event
    * @param dispatcher   the command dispatcher.
    *
    * @throws Exception if the selection could not be applied.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/calendar/applyCalendar/{name}")
   public void applyCalendar(@DestinationVariable("name") String assemblyName,
                             @Payload CalendarSelectionEvent event,
                             Principal principal, CommandDispatcher dispatcher,
                             @LinkUri String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalendarVSAssembly calendarAssembly = (CalendarVSAssembly) viewsheet.getAssembly(assemblyName);

      if(calendarAssembly == null) {
         return;
      }

      CalendarVSAssemblyInfo calendarInfo = (CalendarVSAssemblyInfo) (Tool.clone(calendarAssembly.getVSAssemblyInfo()));
      calendarInfo.setDates(event.getDates());
      calendarInfo.setCurrentDate1(event.getCurrentDate1());

      if(event.getCurrentDate2() != null) {
         calendarInfo.setCurrentDate2(event.getCurrentDate2());
      }

      applyCalendarInfo(calendarAssembly, calendarInfo, rvs, dispatcher, linkUri,
                        event.getEventSource());
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

      ChangedAssemblyList clist = this.placeholderService.createList(true, commandDispatcher,
                                                                     rvs, linkUri);
      box.processChange(calendarAssembly.getAbsoluteName(), hint, clist);
      // Iterate over all assemblies and add to view list if they have
      // hyperlinks that "send selection parameters"
      this.placeholderService.executeInfluencedHyperlinkAssemblies(vs, commandDispatcher,
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
      this.placeholderService.execute(rvs, calendarAssembly.getAbsoluteName(),
         linkUri, clist, commandDispatcher, true);

      if(refresh) {
         this.placeholderService.layoutViewsheet(rvs, rvs.getID(), linkUri, commandDispatcher);
         placeholderService.refreshViewsheet(rvs, rvs.getID(), linkUri, commandDispatcher, false,
                                             true, true, clist);
      }
   }

   @PostMapping("/api/calendar/formatdates")
   @ResponseBody
   public String formatString(@RequestBody CalendarDateFormatModel model, Principal principal)
      throws Exception
   {
      if(model == null) {
         return null;
      }

      String name = model.getAssemblyName();

      if(name == null) {
         return model.getDates();
      }

      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(model.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalendarVSAssembly assembly = (CalendarVSAssembly) viewsheet.getAssembly(name);
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) assembly.getVSAssemblyInfo();
      String dateFormat = CalendarUtil.getCalendarSelectedDateFormat(info);

      return CalendarUtil.formatSelectedDates(model.getDates(), dateFormat, model.isDoubleCalendar(),
         model.isPeriod(), model.isMonthView());
   }


   @PostMapping("/api/calendar/formatTitle")
   @ResponseBody
   public String formatCalendarTitleView(@RequestBody CalendarDateFormatModel model,
                                         Principal principal)
      throws Exception
   {
      if(model == null) {
         return null;
      }

      String name = model.getAssemblyName();

      if(name == null || model.getDates() == null) {
         return model.getDates();
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(model.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalendarVSAssembly assembly = (CalendarVSAssembly) viewsheet.getAssembly(name);

      if(assembly == null) {
         return model.getDates();
      }

      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) assembly.getVSAssemblyInfo();
      FormatInfo fmtInfo = info.getFormatInfo();
      TableDataPath dataPath = new TableDataPath(-1, TableDataPath.CALENDAR_TITLE);
      VSCompositeFormat compositeFormat = fmtInfo.getFormat(dataPath, false);

      return CalendarUtil.formatTitle(model.getDates(), assembly.isYearView(), compositeFormat);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final VSObjectPropertyService vsObjectPropertyService;
   private final ViewsheetService viewsheetService;
}
