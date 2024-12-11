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

package inetsoft.sree.schedule;

public class ScheduleTaskMetaData {
   public ScheduleTaskMetaData(String taskName, String taskOwnerId) {
      this.taskName = taskName;
      this.taskOwnerId = taskOwnerId;
   }

   public String getTaskName() {
      return taskName;
   }

   public void setTaskName(String taskName) {
      this.taskName = taskName;
   }

   public String getTaskOwnerId() {
      return taskOwnerId;
   }

   public void setTaskOwnerId(String taskOwnerId) {
      this.taskOwnerId = taskOwnerId;
   }

   @Override
   public String toString() {
      return getTaskId();
   }

   public String getTaskId() {
      return ScheduleManager.getTaskId(taskOwnerId, taskName);
   }

   private String taskName;
   private String taskOwnerId;
}
