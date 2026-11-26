/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
 * Bug #81673, asks the cluster node that executes a run of a task to interrupt it. Sent by the
 * node that received the Stop request, which has no job instance to interrupt when another node
 * executes the run. Answered with a {@link CancelScheduleTaskResult}.
 */
public final class CancelScheduleTask implements Serializable {
   private static final long serialVersionUID = 1L;

   public CancelScheduleTask(String taskName, String fireInstanceId) {
      this.taskName = taskName;
      this.fireInstanceId = fireInstanceId;
   }

   public String getTaskName() {
      return taskName;
   }

   /**
    * The fire instance id of the run to stop, so a stale request cannot stop a newer run.
    */
   public String getFireInstanceId() {
      return fireInstanceId;
   }

   @Override
   public String toString() {
      return "CancelScheduleTask{taskName=" + taskName + ", fireInstanceId=" + fireInstanceId + '}';
   }

   private final String taskName;
   private final String fireInstanceId;
}
