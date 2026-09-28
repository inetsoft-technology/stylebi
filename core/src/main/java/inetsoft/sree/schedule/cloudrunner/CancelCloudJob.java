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
package inetsoft.sree.schedule.cloudrunner;

import java.io.Serializable;

public class CancelCloudJob implements Serializable {
   public CancelCloudJob(String taskName) {
      this(taskName, null);
   }

   /**
    * @param executionId the id of the execution to cancel, or {@code null} to cancel every
    *                    execution of the task.
    */
   public CancelCloudJob(String taskName, String executionId) {
      this.taskName = taskName;
      this.executionId = executionId;
   }

   public String getTaskName() {
      return taskName;
   }

   /**
    * Gets the id of the execution to cancel. It is {@code null} when the sender does not know the
    * id, in which case every execution of the task is cancelled.
    */
   public String getExecutionId() {
      return executionId;
   }

   private final String taskName;
   private final String executionId;
}
