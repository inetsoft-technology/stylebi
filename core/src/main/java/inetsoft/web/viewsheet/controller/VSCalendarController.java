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
package inetsoft.web.viewsheet.controller;

import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.calendar.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.calendar.CalendarDateFormatModel;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
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
    */
   @Autowired
   public VSCalendarController(RuntimeViewsheetRef runtimeViewsheetRef, VSCalendarServiceProxy vsCalendarServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsCalendarServiceProxy = vsCalendarServiceProxy;
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
      vsCalendarServiceProxy.toggleRangeComparison(runtimeViewsheetRef.getRuntimeId(), assemblyName,
                                                   event, linkUri, principal, dispatcher);
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
      vsCalendarServiceProxy.toggleYearView(runtimeViewsheetRef.getRuntimeId(), assemblyName,
                                            event, linkUri, principal, dispatcher);
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
      vsCalendarServiceProxy.clearCalendar(runtimeViewsheetRef.getRuntimeId(), assemblyName,
                                           principal, dispatcher, linkUri);
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
      vsCalendarServiceProxy.toggleDoubleCalendar(runtimeViewsheetRef.getRuntimeId(), assemblyName,
                                                  event, linkUri, principal, dispatcher);
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
      vsCalendarServiceProxy.applyCalendar(runtimeViewsheetRef.getRuntimeId(), assemblyName,
                                           event, principal, dispatcher, linkUri);
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

      return vsCalendarServiceProxy.formatString(model.getRuntimeId(), model, principal);
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

      return vsCalendarServiceProxy.formatCalendarTitleView(model.getRuntimeId(), model, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private VSCalendarServiceProxy vsCalendarServiceProxy;

}
