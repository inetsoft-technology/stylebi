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
package inetsoft.sree.schedule;

import java.io.Serializable;

/**
 * Message that notifies other members of the cluster that a schedule activity
 * has been updated.
 *
 * @since 12.2
 */
public class TaskActivityMessage implements Serializable {
   /**
    * Gets the name of the schedule task.
    *
    * @return the task name.
    */
   public String getTaskName() {
      return taskName;
   }

   /**
    * Sets the name of the schedule task.
    *
    * @param taskName the task name.
    */
   public void setTaskName(String taskName) {
      this.taskName = taskName;
   }

   /**
    * Gets the new status of the task.
    *
    * @return the task activity.
    */
   public TaskActivity getActivity() {
      return activity;
   }

   /**
    * Sets the new status of the task.
    *
    * @param activity the task activity.
    */
   public void setActivity(TaskActivity activity) {
      this.activity = activity;
   }

   @Override
   public String toString() {
      return "TaskActivityMessage{" +
         "taskName='" + taskName + '\'' +
         ", activity=" + activity +
         '}';
   }

   private String taskName;
   private TaskActivity activity;
}
