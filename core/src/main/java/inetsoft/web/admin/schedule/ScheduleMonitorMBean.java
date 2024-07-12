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
package inetsoft.web.admin.schedule;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.*;
import org.springframework.stereotype.Component;

import javax.management.openmbean.*;
import java.rmi.RemoteException;
import java.util.Date;

@Component
@ManagedResource
public class ScheduleMonitorMBean {
   @Autowired()
   public ScheduleMonitorMBean(SchedulerMonitoringService scheduleService) {
      this.scheduleService = scheduleService;
   }

   /**
    * Get the number of cycles that are currently registered with the scheduler.
    * @return the data cycle count.
    */
   @ManagedAttribute
   public int getCycleCount() {
      return scheduleService.getCycleCount();
   }

   /**
    * Get Datacycle infos from DataCycleManager.
    */
   @ManagedAttribute
   public TabularData getCycleInfo() throws OpenDataException {
      String[] names = { "DataCycle", "Condition" };
      OpenType[] types = { SimpleType.STRING, SimpleType.STRING };
      CompositeType rowType = new CompositeType(
         "DataCycle", "Information about a data cycle", names, names, types);
      TabularType tabularType = new TabularType(
         "DataCycles", "Information about the data cycles", rowType, names);
      TabularDataSupport data = new TabularDataSupport(tabularType);

      for(DataCycleInfo info : scheduleService.getCycleInfo()) {
         for(String condition : info.getConditions()) {
            data.put(new CompositeDataSupport(rowType, names, new Object[]{
               info.getName(), condition
            }));
         }
      }

      return data;
   }

   /**
    * Get the date and time at which the scheduler was started.
    * This attribute can be used to calculate the scheduler uptime.
    * @return the schedule start date.
    */
   @ManagedAttribute
   public Date getStartDate() {
      return scheduleService.getStartDate();
   }

   /**
    * Get schedule uptime.
    * This is an internal method.
    */
   @ManagedAttribute
   public long getUpTime() {
      return scheduleService.getUpTime();
   }

   /**
    * Get the number of tasks that are currently.
    * registered with the scheduler.
    * @return the task count.
    */
   @ManagedAttribute
   public int getTaskCount() throws Exception {
      return scheduleService.getTaskCount();
   }

   /**
    * Get TaskInfo from ScheduleManager.
    */
   @ManagedAttribute
   public TabularData getTaskInfo() throws Exception {
      String[] names = {
         "Name", "User", "Enabled", "LastRunStart", "LastRunFinish", "LastRunStatus",
         "NextRunStart", "NextRunStatus"
      };
      OpenType[] types = {
         SimpleType.STRING, SimpleType.STRING, SimpleType.BOOLEAN, SimpleType.DATE, SimpleType.DATE,
         SimpleType.STRING, SimpleType.DATE, SimpleType.STRING
      };
      CompositeType rowType = new CompositeType(
         "TaskInfo", "Information about a scheduled task", names, names, types);
      TabularType tabularType = new TabularType(
         "TaskInfos", "Information about the scheduled tasks", rowType, names);
      TabularDataSupport data = new TabularDataSupport(tabularType);

      for(ScheduleTaskInfo info : scheduleService.getTaskInfo(null, true)) {
         data.put(new CompositeDataSupport(rowType, names, new Object[] {
            info.getName(), info.getUser(), info.isEnabled(), info.getLastRunStart(),
            info.getLastRunFinish(), info.getLastRunStatus(), info.getNextRunStart(),
            info.getNextRunStatus()
         }));
      }

      return data;
   }

   /**
    * Runs the named task immediately.
    * @param taskName the task name
    */
   @ManagedOperation
   public void runTask(String taskName) throws Exception {
      scheduleService.runTask(taskName);
   }

   /**
    * Starts the schedule server.
    */
   @ManagedOperation
   public void startScheduler() {
      scheduleService.startScheduler();
   }

   /**
    * Stops the schedule server.
    */
   @ManagedOperation
   public void stopScheduler() throws RemoteException {
      scheduleService.stopScheduler();
   }

   /**
    * Stops the named task if it is running.
    * @param taskName the task name
    */
   @ManagedOperation
   public void stopTask(String taskName) throws Exception {
      scheduleService.stopTask(taskName);
   }

   private final SchedulerMonitoringService scheduleService;
}
