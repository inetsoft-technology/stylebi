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
import inetsoft.web.api.schedule.CreateScheduleTaskRequest;

/**
 * One requested schedule-task change: create a new task from {@code spec}, or delete the task
 * named by {@code taskId}. Exactly one of {@code spec} (create) or {@code taskId} alone (delete)
 * applies to a given entry -- {@link ScheduleChangePlanService#resolve} enforces which, per verb,
 * rather than silently ignoring whichever field does not apply (see its javadoc).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleChangeRequest {
   public static final String VERB_CREATE = "create";
   public static final String VERB_DELETE = "delete";

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   /**
    * The task id for a {@code delete}. Ignored for a {@code create}, whose id is derived from
    * {@code spec}'s owner and name (see {@code inetsoft.sree.schedule.ScheduleManager#getTaskId}).
    */
   public String getTaskId() { return taskId; }
   public void setTaskId(String v) { this.taskId = v; }

   /** The new task's definition, for a {@code create}. Ignored for a {@code delete}. */
   public CreateScheduleTaskRequest getSpec() { return spec; }
   public void setSpec(CreateScheduleTaskRequest v) { this.spec = v; }

   private String verb;
   private String taskId;
   private CreateScheduleTaskRequest spec;
}
