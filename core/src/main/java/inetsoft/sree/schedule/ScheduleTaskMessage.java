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

import java.io.Serializable;

/**
 * Message that notifies other members of the cluster that a schedule activity
 * has been updated.
 *
 * @since 12.2
 */
public class ScheduleTaskMessage implements Serializable {
   /**
    * Gets the name of the task that was modified.
    *
    * @return the task name.
    */
   public String getTaskName() {
      return taskName;
   }

   /**
    * Sets the name of the task that was modified.
    *
    * @param taskName the task name.
    */
   public void setTaskName(String taskName) {
      this.taskName = taskName;
   }

   /**
    * Gets the schedule task that was added or modified. The task will be
    * <tt>null</tt> if removed.
    *
    * @return the task.
    */
   public ScheduleTask getTask() {
      return task;
   }

   /**
    * Sets the task that was added or modified.
    *
    * @param task the task.
    */
   public void setTask(ScheduleTask task) {
      this.task = task;
   }

   /**
    * Gets the action that was performed on the task.
    *
    * @return the action.
    */
   public Action getAction() {
      return action;
   }

   /**
    * Sets the action that was performed on the task.
    *
    * @param action the action.
    */
   public void setAction(Action action) {
      this.action = action;
   }

   @Override
   public String toString() {
      return "ScheduleTaskMessage{" +
         "taskName='" + taskName + '\'' +
         ", task=" + task +
         ", action=" + action +
         '}';
   }

   private String taskName;
   private ScheduleTask task;
   private Action action;

   /**
    * Enumeration of the types of actions that may be performed on a schedule
    * task.
    */
   public enum Action {
      ADDED, MODIFIED, REMOVED
   }
}
