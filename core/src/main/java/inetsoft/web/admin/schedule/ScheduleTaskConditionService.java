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

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.schedule.model.ScheduleConditionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class ScheduleTaskConditionService {
   @Autowired
   public ScheduleTaskConditionService(ScheduleService scheduleService,
                                       ScheduleManager scheduleManager,
                                       ScheduleConditionService scheduleConditionService)
   {
      this.scheduleService = scheduleService;
      this.scheduleManager = scheduleManager;
      this.scheduleConditionService = scheduleConditionService;
   }

   public ScheduleConditionModel getTaskCondition(String taskName, int index,
                                                  Principal principal)
      throws Exception
   {
      return getTaskCondition(taskName, null, index, principal);
   }

   public ScheduleConditionModel getTaskCondition(String taskName, IdentityID owner, int index,
                                                  Principal principal)
      throws Exception
   {
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      //just adjust task name when user edit own task(task can share between same group users)
      if(owner == null || principal != null && owner.equalsIgnoreCase(pId))
      {
         taskName = scheduleService.getTaskName(Tool.byteDecode(taskName), principal);
      }

      Catalog catalog = Catalog.getCatalog(principal);
      ScheduleCondition condition = null;

      if(index < 0) {
         throw new Exception(catalog.getString(
            "em.scheduler.invalidConditionIndex"));
      }

      if(taskName == null || "".equals(taskName)) {
         throw new Exception(catalog.getString("em.scheduler.emptyTaskName"));
      }

      ScheduleTask task = scheduleManager.getScheduleTask(taskName);

      if(task == null) {
         throw new Exception(catalog.getString(
            "em.scheduler.taskNotFound", taskName));
      }

      condition = condition == null ? task.getCondition(index) : condition;

      return scheduleConditionService.getConditionModel(condition, principal);
   }

   public void deleteTaskCondition(String taskName, int[] items, Principal principal)
      throws Exception
   {
      taskName = scheduleService.getTaskName(Tool.byteDecode(taskName), principal);
      Catalog catalog = Catalog.getCatalog(principal);

      if(taskName == null || "".equals(taskName)) {
         throw new Exception(catalog.getString("em.scheduler.emptyTaskName"));
      }

      ScheduleTask task = scheduleManager.getScheduleTask(taskName);

      if(task == null) {
         throw new Exception(catalog.getString(
            "em.scheduler.taskNotFound", taskName));
      }

      for(int i = 0; i < items.length; i++) {
         int index = items[i];
         task.removeCondition(index);
      }

      scheduleService.saveTask(taskName, task, principal);
   }

   public ScheduleConditionModel[] saveTaskCondition(String taskName, String oldTaskName,
                                                     IdentityID owner, int index,
                                                     ScheduleConditionModel model,
                                                     Principal principal) throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);

      if("".equals(taskName)) {
         throw new Exception(catalog.getString("em.scheduler.emptyTaskName"));
      }

      taskName = scheduleService.updateTaskName(oldTaskName, taskName, owner, principal);
      ScheduleTask task = scheduleManager.getScheduleTask(taskName);

      if(task == null) {
         throw new Exception(catalog.getString(
            "em.scheduler.taskNotFound", taskName));
      }

      TimeRange range = scheduleService.setTaskCondition(taskName, index, model, catalog, task);
      scheduleService.saveTask(taskName, task, principal);

      if(range != null) {
         new TaskBalancer().updateTask(task, range);
      }

      ScheduleConditionModel[] result = new ScheduleConditionModel[task.getConditionCount()];

      for(int i = 0; i < result.length; i++) {
         result[i] = getTaskCondition(taskName, task.getOwner(), i, principal);
      }

      return result;
   }

   private final ScheduleService scheduleService;
   private final ScheduleConditionService scheduleConditionService;
   private final ScheduleManager scheduleManager;
}
