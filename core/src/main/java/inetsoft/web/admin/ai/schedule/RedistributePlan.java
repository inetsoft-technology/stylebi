/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.admin.ai.schedule;

import inetsoft.web.api.schedule.ScheduleCondition;

import java.util.List;

/**
 * What a redistribution would change, computed without saving anything.
 *
 * @param tasks           every task whose conditions would change, in the order requested, with
 *                        its complete new condition list.
 * @param skipped         every requested task that is not in {@code tasks}, with the reason.
 * @param intervalMinutes the distance between two placed conditions, {@code 0} when no task
 *                        holds a time condition.
 */
public record RedistributePlan(List<RedistributedTask> tasks, List<SkippedTask> skipped,
                               long intervalMinutes)
{
   /**
    * One task that would change.
    *
    * @param taskId     the task id.
    * @param conditions the task's complete condition list after the redistribution, in the shape
    *                   an update patch takes, so conditions that are not moved are carried over.
    */
   public record RedistributedTask(String taskId, List<ScheduleCondition> conditions) {
   }

   /**
    * One requested task that does not change.
    *
    * @param taskId the id as requested.
    * @param reason why the task is left out.
    */
   public record SkippedTask(String taskId, String reason) {
   }
}
