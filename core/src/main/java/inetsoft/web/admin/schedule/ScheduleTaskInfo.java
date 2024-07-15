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
package inetsoft.web.admin.schedule;

import inetsoft.sree.security.IdentityID;

import java.io.Serializable;
import java.util.Date;

/**
 * The ScheduleTaskInfo defines some details about each registered task include
 * some status and date.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class ScheduleTaskInfo implements Serializable {
   /**
    * Check if this task is currently enabled.
    * @return true if this task is enabled.
    */
   public boolean isEnabled() {
      return enabled;
   }

   /**
    * Enables or disables this task.
    * @param enabled true to enable and false to disable.
    */
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   /**
    * Get the name of this task.
    * @return the name of this task.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the name of this task.
    * @param name the task name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the LastRunFinish.
    * @return the date and time at which the last execution of
    * the task finished.
    */
   public Date getLastRunFinish() {
      return this.lastRunFinish;
   }

   /**
    * Set the date and time at which the last execution of the task finished.
    * @param date the date of last run finished.
    */
   public void setLastRunFinish(Date date) {
      this.lastRunFinish = date;
   }

   /**
    * Get the date and time at which the last execution of the task finished.
    * @return the date of last run started.
    */
   public Date getLastRunStart() {
      return this.lastRunStart;
   }

   /**
    * Set the LastRunStart.
    */
   public void setLastRunStart(Date date) {
      this.lastRunStart = date;
   }

   /**
    * Get the LastRunStatus.
    */
   public String getLastRunStatus() {
      return this.lastRunStatus;
   }

   /**
    * Set the Last Run Status.
    * @param status the status of LastRunStatus.
    */
   public void setLastRunStatus(String status) {
      this.lastRunStatus = status;
   }

   /**
    * Get the user.
    */
   public IdentityID getUser() {
      return this.user;
   }

   /**
    * Set the user name.
    * @param user the name of the user that owns the task.
    */
   public void setUser(IdentityID user) {
      this.user = user;
   }

   /**
    * Get the NextRunStart.
    */
   public Date getNextRunStart() {
      return this.nextRunStart;
   }

   /**
    * Set the NextRunStart.
    * @param date the date of the next run start.
    */
   public void setNextRunStart(Date date) {
      this.nextRunStart = date;
   }

   /**
    * Get the NextRunStatus.
    */
   public String getNextRunStatus() {
      return this.nextRunStatus;
   }

   /**
    * Set this next RunStatus.
    * @param status the next run status.
    */
   public void setNextRunStatus(String status) {
      this.nextRunStatus = status;
   }

   private boolean enabled;
   private String name;
   private Date lastRunFinish;
   private Date lastRunStart;
   private Date nextRunStart;
   private String lastRunStatus = null;
   private String nextRunStatus = null;
   private IdentityID user;
}
