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
import inetsoft.web.admin.schedule.ScheduleTaskConditionService;
import inetsoft.web.admin.schedule.model.ScheduleConditionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller that provides a REST endpoint used for the scheduled tasks dialog conditions.
 *
 * @since 12.3
 */
@RestController
public class ScheduleTaskConditionController {
   /**
    * Creates a new instance of <tt>ScheduleTaskConditionController</tt>.
    */
   @Autowired
   public ScheduleTaskConditionController(
      ScheduleTaskConditionService scheduleTaskConditionService)
   {
      this.scheduleTaskConditionService = scheduleTaskConditionService;
   }

   /**
    * Gets the specified task condition.
    *
    * @param taskName  the name of the task
    * @param index     the index of the condition
    * @param principal the user
    *
    * @return the condition model
    *
    * @throws Exception if could not get task or condition
    */
   @RequestMapping(
      value = "/api/portal/schedule/task/condition",
      method = RequestMethod.GET
   )
   @ResponseBody
   public ScheduleConditionModel getTaskCondition(@RequestParam("name") String taskName,
                                                  @RequestParam("index") int index,
                                                  Principal principal)
      throws Exception
   {
      return scheduleTaskConditionService.getTaskCondition(taskName, index, principal);
   }

   /**
    * Delete a conditions from a task
    *
    * @param taskName the name of the task
    * @param items    the list of condition indexes to remove (in reverse sort)
    *
    * @throws Exception if could not get task or conditions
    */
   @RequestMapping(
      value = "/api/portal/schedule/task/condition/delete",
      method = RequestMethod.POST
   )
   @ResponseBody
   public void deleteTaskCondition(@RequestParam("name") String taskName,
                                   @RequestBody int[] items,
                                   Principal principal)
      throws Exception
   {
      scheduleTaskConditionService.deleteTaskCondition(taskName, items, principal);
   }

   /**
    * Save a task condition.
    *
    * @param taskName  the task name
    * @param index     the index of the task
    * @param model     the condition model
    * @param principal the user
    *
    * @throws Exception if could not get task or condition
    */
   @RequestMapping(
      value = "/api/portal/schedule/task/condition",
      method = RequestMethod.POST
   )
   @ResponseBody
   public ScheduleConditionModel[] saveTaskCondition(@RequestParam("name") String taskName,
                                     @RequestParam("oldTaskName") String oldTaskName,
                                     @RequestParam("owner") String owner,
                                     @RequestParam("index") int index,
                                     @RequestBody ScheduleConditionModel model,
                                     Principal principal)
      throws Exception
   {
      IdentityID ownerID = IdentityID.getIdentityIDFromKey(owner);
      return scheduleTaskConditionService
         .saveTaskCondition(taskName, oldTaskName, ownerID, index, model, principal);
   }

   private final ScheduleTaskConditionService scheduleTaskConditionService;
}
