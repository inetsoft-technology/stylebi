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

public interface CloudJobFactory {
   /**
    * The name of the cloud runner argument that carries the execution id, passed to the container
    * as {@code --schedule.execution.id=<id>}. The container echoes it in {@link CloudJobResult}
    * so that the result is matched to the execution that started it, not to any execution of the
    * same task.
    */
   String EXECUTION_ID_ARG = "schedule.execution.id";

   String getType();

   CloudJob createCloudJob(String taskName, String cycle, String orgID);

   /**
    * Creates a cloud job for one execution of a task. Implementations should pass the execution
    * id to the container in the {@link #EXECUTION_ID_ARG} argument. The default implementation
    * ignores it, so the container's result is matched by task name only.
    *
    * @param executionId the unique id of this execution.
    */
   default CloudJob createCloudJob(String taskName, String cycle, String orgID,
                                   String executionId)
   {
      return createCloudJob(taskName, cycle, orgID);
   }
}
