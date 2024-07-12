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
package inetsoft.sree.schedule;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class InternalScheduledTaskService {
   /**
    * Internal task names.
    */
   public static final String ASSET_FILE_BACKUP = "__asset file backup__";
   public static final String BALANCE_TASKS = "__balance tasks__";
   public static final String UPDATE_ASSETS_DEPENDENCIES = "__update assets dependencies__";

   public InternalScheduledTaskService(ScheduleManager scheduleManager) {
      this.scheduleManager = scheduleManager;
   }

   static boolean isInternalTask(String taskName) {
      return ASSET_FILE_BACKUP.equals(taskName) ||
         BALANCE_TASKS.equals(taskName) || UPDATE_ASSETS_DEPENDENCIES.equals(taskName);
   }

   /**
    * Initializes the internal system tasks.
    *
    * @throws SchedulerException if the tasks could not be added.
    */
   void initInternalTasks() throws Exception {
      initBackupRepositoryTask();
      initTaskBalancerTask();
      initUpdateDependenciesTask();
   }

   private void setTask(ScheduleTask task) throws Exception {
      scheduleManager.setScheduleTask(task.getName(), task, null, true, null);
   }

   /**
    * Adds the internal task that backs up the asset repository.
    *
    * @throws SchedulerException if the task could not be added.
    */
   private void initBackupRepositoryTask() throws Exception {
      final ScheduleTask existingTask = scheduleManager
         .getScheduleTask(ASSET_FILE_BACKUP, Organization.getDefaultOrganizationID());

      // Backup action may not have been properly serialized.
      if(existingTask != null && existingTask.getActionCount() > 0) {
         return;
      }

      Calendar calendar = Calendar.getInstance();
      parseDateFromProperty("repository.asset.backup.time", calendar);
      ScheduleTask task = new ScheduleTask(ASSET_FILE_BACKUP);

      ScheduleStatusDao dao = ScheduleStatusDao.getInstance();
      dao.clearStatus(ASSET_FILE_BACKUP);

      if(task.getConditionCount() == 0) {
         TimeCondition condition = TimeCondition.at(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            calendar.get(Calendar.SECOND));
         condition.setInterval(1);
         task.addCondition(condition);
      }

      task.addAction(new AssetFileBackupAction());

      task.setOwner(new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getCurrentOrgName()));
      task.setRemovable(false);
      task.setEditable(false);
      // todo does it really need to be an instance of FileSystemDataSpace?
//      task.setEnabled(space instanceof FileSystemDataSpace && "true".equals(
//         SreeEnv.getProperty("repository.asset.backup.enable", "true")));
      task.setEnabled(false);

      setTask(task);
   }

   /**
    * Parse into date object from a string which is got from property.
    *
    * @param name     the property name.
    * @param calendar the calendar into which the date will be parsed.
    */
   private void parseDateFromProperty(String name, Calendar calendar) {
      Date time = null;
      String property = SreeEnv.getProperty(name);

      if(property != null) {
         SimpleDateFormat format =
            Tool.createDateFormat(Tool.DEFAULT_TIME_PATTERN);

         try {
            time = format.parse(property);
         }
         catch(Exception ex) {
            LOG.error("Invalid date value for property " + name + ": " + property, ex);
         }
      }

      if(time == null) {
         calendar.set(Calendar.DATE, 1);
         calendar.set(Calendar.HOUR_OF_DAY, 1);
         calendar.set(Calendar.MINUTE, 0);
         calendar.set(Calendar.SECOND, 0);
         calendar.set(Calendar.MILLISECOND, 0);
      }
      else {
         calendar.setTime(time);
      }
   }

   private void initTaskBalancerTask() throws Exception {
      final ScheduleTask existingTask = scheduleManager
         .getScheduleTask(BALANCE_TASKS, Organization.getDefaultOrganizationID());

      if(existingTask != null && existingTask.getActionCount() > 0) {
         existingTask.setRemovable(false);
         return;
      }

      ScheduleTask task = new ScheduleTask(BALANCE_TASKS);
      task.addCondition(new TaskBalancerCondition());
      task.addAction(new TaskBalancerAction());
      task.setOwner(new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getCurrentOrgName()));

      task.setRemovable(false);
      task.setEditable(false);

      setTask(task);
   }

   private void initUpdateDependenciesTask() throws Exception {
      final ScheduleTask existingTask = scheduleManager
         .getScheduleTask(UPDATE_ASSETS_DEPENDENCIES, Organization.getDefaultOrganizationID());

      if(existingTask != null && existingTask.getActionCount() > 0) {
         return;
      }

      ScheduleTask task = new ScheduleTask(UPDATE_ASSETS_DEPENDENCIES);
      ScheduleStatusDao dao = ScheduleStatusDao.getInstance();
      dao.clearStatus(UPDATE_ASSETS_DEPENDENCIES);

      task.addAction(new UpdateAssetsDependenciesAction());
      task.addCondition(new NeverRunCondition());
      task.setOwner(new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getCurrentOrgName()));
      task.setRemovable(false);
      task.setEditable(false);

      setTask(task);
   }

   private final ScheduleManager scheduleManager;

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
