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
 * One requested schedule-task change: create a new task from {@code spec}, update the task named
 * by {@code taskId} with {@code patch}, or delete it. {@code spec} (create), {@code taskId} with
 * {@code patch} (update) or {@code taskId} alone (delete) applies to a given entry -- {@link ScheduleChangePlanService#resolve} enforces which, per verb,
 * rather than silently ignoring whichever field does not apply (see its javadoc).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleChangeRequest {
   public static final String VERB_CREATE = "create";
   public static final String VERB_DELETE = "delete";
   public static final String VERB_UPDATE = "update";

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

   /** The fields to change, for an {@code update}. Ignored for the other verbs. */
   public ScheduleTaskPatch getPatch() { return patch; }
   public void setPatch(ScheduleTaskPatch v) { this.patch = v; }

   /**
    * The folder a {@code create} puts the new task in, root-normalized like the schedule folder
    * area's paths. {@code null} or {@code "/"} is the root folder.
    */
   public String getPath() { return path; }
   public void setPath(String v) { this.path = v; }

   /**
    * The task-level time zone a {@code create} stores on the new task (the portal Options tab's
    * Time Zone), the zone its start and end dates are read in. {@code null} or blank means none.
    * Not used for the other verbs; an update sends it in {@link ScheduleTaskPatch#getTimeZone()}.
    */
   public String getTimeZone() { return timeZone; }
   public void setTimeZone(String v) { this.timeZone = v; }

   private String verb;
   private String taskId;
   private CreateScheduleTaskRequest spec;
   private ScheduleTaskPatch patch;
   private String path;
   private String timeZone;
}
