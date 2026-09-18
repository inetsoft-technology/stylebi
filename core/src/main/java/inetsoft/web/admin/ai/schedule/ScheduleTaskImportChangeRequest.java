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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One task, from an already-staged import, to actually import: {@code taskId} (must be one of
 * {@code stage_schedule_task_import}'s own returned {@code tasks[].taskId} values -- never
 * hand-constructed) and {@code overwrite} (required {@code true} whenever a task with that id
 * already exists on this server; refused loud otherwise). There is no verb discriminator -- unlike
 * every other area's {@code changes[]}, this area has exactly one action (import the named staged
 * task), so nothing else needs to be named.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleTaskImportChangeRequest {
   public String getTaskId() { return taskId; }
   public void setTaskId(String v) { this.taskId = v; }

   /** Default {@code false}. Required {@code true} whenever a task with this id already exists on
    * the server as of preview/apply time -- omitted or {@code false} against an existing task is
    * refused loud, naming the collision, rather than silently overwriting it. */
   public Boolean getOverwrite() { return overwrite; }
   public void setOverwrite(Boolean v) { this.overwrite = v; }

   private String taskId;
   private Boolean overwrite;
}
