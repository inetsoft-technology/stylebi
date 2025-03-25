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
package inetsoft.web.viewsheet.controller.dialog;

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.model.TimeZoneModel;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.dialog.schedule.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.VSBookmarkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class ScheduleDialogController {
   /**
    * Creates a new instance of ScheduleDialogController
    */
   @Autowired
   public ScheduleDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                   SecurityProvider securityProvider,
                                   ScheduleService scheduleService,
                                   VSBookmarkService vsBookmarkService,
                                   ScheduleDialogServiceProxy scheduleDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.securityProvider = securityProvider;
      this.scheduleService = scheduleService;
      this.vsBookmarkService = vsBookmarkService;
      this.scheduleDialogServiceProxy = scheduleDialogServiceProxy;
   }

   /**
    * Gets the schedule dialog model.
    * @param principal  the principal user
    * @return  the schedule dialog model
    * @throws Exception if could not create the schedule dialog model
    */
   @RequestMapping(value = "/api/vs/schedule-dialog-model", method = RequestMethod.GET)
   @ResponseBody
   public ScheduleDialogModel getScheduleDialogModel(Principal principal)
      throws Exception
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      boolean bookmarkEnabled = SecurityEngine.getSecurity().checkPermission(
         principal, ResourceType.VIEWSHEET_ACTION, "Bookmark", ResourceAction.READ) &&
         !"anonymous".equals(pId.name);

      String bookmarkName = "";
      boolean currentBookmark = true;

      if(bookmarkEnabled) {
         DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
         Date now = Calendar.getInstance().getTime();
         bookmarkName = formatter.format(now);
         currentBookmark = false;
      }

      Catalog catalog = Catalog.getCatalog(principal);
      List<TimeRangeModel> ranges = TimeRange.getTimeRanges().stream()
         .filter(r -> checkPermission(r, principal))
         .sorted()
         .map(r -> TimeRangeModel.builder().from(r, catalog).build())
         .collect(Collectors.toList());
      boolean startTimeEnabled = securityProvider.checkPermission(
         principal, ResourceType.SCHEDULE_OPTION, "startTime", ResourceAction.READ);
      boolean timeRangeEnabled = securityProvider.checkPermission(
         principal, ResourceType.SCHEDULE_OPTION, "timeRange", ResourceAction.READ);
      boolean userDialogEnabled = (principal == null || !"anonymous".equals(pId.name));
      List<TimeZoneModel> timeZoneOptions = TimeZoneModel.getTimeZoneOptions();

      SimpleScheduleDialogModel simpleModel = SimpleScheduleDialogModel.builder()
         .timeRanges(ranges)
         .timeZoneOptions(timeZoneOptions)
         .startTimeEnabled(startTimeEnabled)
         .timeRangeEnabled(timeRangeEnabled)
         .userDialogEnabled(userDialogEnabled)
         .build();

      return ScheduleDialogModel.builder()
         .currentBookmark(currentBookmark)
         .bookmark(bookmarkName)
         .bookmarkEnabled(bookmarkEnabled)
         .simpleScheduleDialogModel(simpleModel)
         .build();
   }

   /**
    * Gets the simple schedule dialog model.
    * @param runtimeId  the runetime id
    * @param principal  the principal user
    * @return  the schedule dialog model
    * @throws Exception if could not create the schedule dialog model
    */
   @RequestMapping(value = "/api/vs/simple-schedule-dialog-model/**", method = RequestMethod.GET)
   @ResponseBody
   public SimpleScheduleDialogModel getSimpleScheduleDialogModel(@RemainingPath String runtimeId,
      @RequestParam(value = "useCurrent", required = false) boolean useCurrent,
      @RequestParam(value = "bookmarkName", required = false, defaultValue = "") String bookmarkName,
      Principal principal) throws Exception
   {
      return scheduleDialogServiceProxy.getSimpleScheduleDialogModel(runtimeId, useCurrent, bookmarkName, principal);
   }

   /**
    * Checks if schedule dialog bookmark details are valid;
    * @param runtimeId  the runetime id
    * @param principal  the principal user
    * @return  the schedule dialog model
    * @throws Exception if could not create the schedule dialog model
    */
   @RequestMapping(value = "/api/vs/check-schedule-dialog/**", method = RequestMethod.GET)
   @ResponseBody
   public MessageCommand checkScheduleDialogl(@RemainingPath String runtimeId,
      @RequestParam(value = "useCurrent", required = false) boolean useCurrent,
      @RequestParam(value = "bookmarkName", required = false, defaultValue = "") String bookmarkName,
      Principal principal) throws Exception
   {
      return scheduleDialogServiceProxy.checkScheduleDialogl(runtimeId, useCurrent, bookmarkName, principal);
   }

   /**
    * Copy of SaveScheduleEvent.java
    * Schedule a viewsheet.
    * @param value               the Schedule Dialog Model
    * @param principal           the principal user
    * @param commandDispatcher   the command dispatcher instance
    * @throws Exception if could not Schedule the viewsheet
    */
   @MessageMapping("/vs/schedule-dialog-model")
   public void scheduleVS(@Payload ScheduleDialogModel value,
                           Principal principal,
                           CommandDispatcher commandDispatcher)
      throws Exception
   {
      scheduleDialogServiceProxy.scheduleVS(runtimeViewsheetRef.getRuntimeId(), value, principal, commandDispatcher);
   }

   private boolean checkPermission(TimeRange range, Principal user) {
      return securityProvider.checkPermission(
         user, ResourceType.SCHEDULE_TIME_RANGE, range.getName(), ResourceAction.ACCESS);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final SecurityProvider securityProvider;
   private final ScheduleService scheduleService;
   private ScheduleDialogServiceProxy scheduleDialogServiceProxy;
   private VSBookmarkService vsBookmarkService;
   private static final Logger LOG =
      LoggerFactory.getLogger(ScheduleDialogController.class);
}
