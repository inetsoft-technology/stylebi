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
package inetsoft.web.portal.controller;

import inetsoft.sree.security.IdentityID;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.admin.schedule.ScheduleTaskActionService;
import inetsoft.web.admin.schedule.model.ScheduleActionModel;
import inetsoft.web.admin.schedule.model.ScheduleAlertModel;
import inetsoft.web.portal.model.database.StringWrapper;
import inetsoft.web.viewsheet.model.VSBookmarkInfoModel;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Controller that provides a REST endpoint used for the scheduled tasks dialog.
 *
 * @since 12.3
 */
@RestController
public class ScheduleTaskActionController {
   /**
    * Creates a new instance of <tt>ScheduleController</tt>.
    */
   @Autowired
   public ScheduleTaskActionController(ScheduleTaskActionService scheduleTaskActionService)
   {
      this.scheduleTaskActionService = scheduleTaskActionService;
   }

   /**
    * Gets a table of scheduled tasks.
    *
    * @param taskName  the name of the task
    * @param index     the index of the action
    * @param principal the user
    *
    * @return the action model
    *
    * @throws Exception if could not get task or action
    */
   @GetMapping("/api/portal/schedule/task/action")
   public ScheduleActionModel getTaskAction(@RequestParam("name") String taskName,
                                            @RequestParam("index") int index,
                                            Principal principal)
      throws Exception
   {
      return scheduleTaskActionService.getTaskAction(taskName, index, principal, false);
   }

   /**
    * Removes an action from the task.
    *
    * @param taskName  the name of the task
    * @param items     the indexes of the actions to remove (in reverse sort)
    * @param principal the user
    *
    * @throws Exception if could not get task or actions
    */
   @PostMapping("/api/portal/schedule/task/action/delete")
   public void deleteTaskActions(@RequestParam("name") String taskName,
                                 @RequestParam("owner") String taskOwner,
                                 @RequestBody int[] items,
                                 Principal principal)
      throws Exception
   {
      try{
         scheduleTaskActionService.deleteTaskActions(taskName, taskOwner, items, principal);
      }
      catch(ArrayIndexOutOfBoundsException e) {
         //delete action that is unsaved, ignore.
      }
   }

   /**
    * Saves the specified schedule task action
    *
    * @param taskName  the name of the task
    * @param index     the index of the action
    * @param principal the user
    *
    * @throws Exception if could not get task or action
    */
   @PostMapping("/api/portal/schedule/task/action")
   public ScheduleActionModel[] saveTaskAction(@RequestParam("name") String taskName,
                                  @RequestParam("oldTaskName") String oldTaskName,
                                  @RequestParam("index") int index,
                                  @RequestParam("owner") String owner,
                                  @RequestBody ScheduleActionModel model,
                                  @LinkUri String linkURI,
                                  Principal principal)
      throws Exception
   {
      IdentityID ownerID = IdentityID.getIdentityIDFromKey(owner);
      return scheduleTaskActionService.saveTaskAction(taskName, oldTaskName, ownerID, index, model,
                                                      Tool.replaceLocalhost(linkURI), principal, false);
   }

   /**
    * Get the bookmarks of a specific sheet.
    *
    * @param id        the id of the viewsheet.
    * @param principal the user information.
    *
    * @return the list of bookmarks.
    */
   @RequestMapping(
      value = "/api/portal/schedule/task/action/bookmarks",
      method = RequestMethod.GET
   )
   @ResponseBody
   public List<VSBookmarkInfoModel> getBookmarks(@DecodeParam("id") String id,
                                                 Principal principal)
      throws Exception
   {
      return scheduleTaskActionService.getBookmarks(id, false, principal);
   }

   /**
    * Get the alias of a specific replet.
    *
    * @param sheet        the id of the replet.
    * @param principal the user information.
    *
    * @return the alias of replet.
    */
   @RequestMapping(
      value = "/api/portal/schedule/task/action/sheetAlias",
      method = RequestMethod.GET
   )
   @ResponseBody
   public StringWrapper getRepletAlias(@DecodeParam("sheet") String sheet, Principal principal) {
      StringWrapper result = new StringWrapper();
      result.setBody(scheduleTaskActionService.getRepletAlias(sheet, principal));

      return result;
   }

   @RequestMapping(
      value = "/api/portal/schedule/task/action/hasPrintLayout",
      method = RequestMethod.GET
   )
   @ResponseBody
   public Boolean hasPrintLayout(@DecodeParam("id") String id, Principal principal)
      throws Exception
   {
      return scheduleTaskActionService.hasPrintLayout(id, principal);
   }

   /**
    * Gets the highlights for a specified dashboard,
    *
    * @param identifier the id of the viewsheet entry
    * @param principal  the user
    *
    * @return a table of highlights
    *
    * @throws Exception if could not get report or dashboard
    */
   @RequestMapping(
      value = "/api/portal/schedule/task/action/viewsheet/highlights",
      method = RequestMethod.GET
   )
   @ResponseBody
   public List<ScheduleAlertModel> getViewsheetHighlights(@DecodeParam("id") String identifier,
                                                          Principal principal) throws Exception
   {
      return scheduleTaskActionService.getViewsheetHighlights(identifier, principal);
   }

   /**
    * Gets the parameters for a specified viewsheet
    *
    * @param identifier the id of the vs
    * @param principal  the user
    *
    * @return a list of parameters
    *
    * @throws Exception if could not get report or dashboard
    */
   @RequestMapping(
      value = "/api/portal/schedule/task/action/viewsheet/parameters",
      method = RequestMethod.GET
   )
   @ResponseBody
   public List<String> getViewsheetParameters(@DecodeParam("id") String identifier,
                                              Principal principal)
      throws Exception
   {
      return scheduleTaskActionService.getViewsheetParameters(identifier, principal);
   }

   /**
    * Gets all table data assemblies for a specified viewsheet
    *
    * @param identifier the id of the vs
    * @param principal  the user
    *
    * @return a list of parameters
    *
    * @throws Exception if could not get report or dashboard
    */
   @GetMapping("/api/portal/schedule/task/action/viewsheet/tableDataAssemblies")
   public List<String> getViewsheetTableDataAssemblies(
      @DecodeParam("id") String identifier,
      Principal principal) throws Exception
   {
      return scheduleTaskActionService.getViewsheetTableDataAssemblies(identifier, principal);
   }

   private final ScheduleTaskActionService scheduleTaskActionService;
}
