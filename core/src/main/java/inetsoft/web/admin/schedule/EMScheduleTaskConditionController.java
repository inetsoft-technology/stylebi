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
package inetsoft.web.admin.schedule;

import inetsoft.sree.security.IdentityID;
import inetsoft.web.admin.schedule.model.ScheduleConditionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class EMScheduleTaskConditionController {
   /**
    * Creates a new instance of <tt>ScheduleTaskConditionController</tt>.
    */
   @Autowired
   public EMScheduleTaskConditionController(
      ScheduleTaskConditionService scheduleTaskConditionService)
   {
      this.scheduleTaskConditionService = scheduleTaskConditionService;
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
   @PostMapping("/api/em/schedule/task/condition")
   public ScheduleConditionModel[] saveTaskCondition(
      @RequestParam("name") String taskName,
      @RequestParam("oldTaskName") String oldTaskName,
      @RequestParam("owner") String owner,
      @RequestParam("index") int index,
      @RequestBody ScheduleConditionModel model,
      Principal principal)
      throws Exception
   {
      IdentityID ownerID = IdentityID.getIdentityIDFromKey(owner);
      return scheduleTaskConditionService.saveTaskCondition(taskName, oldTaskName, ownerID, index,
                                                            model, principal);
   }

   private final ScheduleTaskConditionService scheduleTaskConditionService;
}
