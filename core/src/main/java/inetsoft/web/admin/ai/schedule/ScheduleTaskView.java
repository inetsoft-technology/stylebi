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

import inetsoft.web.api.schedule.ScheduleTask;
import inetsoft.web.api.schedule.UnsupportedScheduleItem;

import java.util.List;

/**
 * A total read of one schedule task -- the task itself plus its conditions and actions, combined
 * into one response. {@code get_schedule_task} exists specifically so a caller does not have to
 * make the three separate calls {@link ScheduleTask} alone would require (spec §3, Track C.0
 * finding: the enterprise DTO carries neither on its own).
 *
 * <p>{@code conditions}/{@code actions} are {@code List<Object>}, not {@code
 * List<ScheduleCondition>}/{@code List<ScheduleAction>}, because each entry may instead be an
 * {@link UnsupportedScheduleItem} placeholder (see {@code
 * ScheduleApiService#getTaskConditionsLenient}/{@code #getTaskActionsLenient}) -- a built-in
 * system task's condition/action has no enterprise API DTO at all, and those two hierarchies are
 * shared with the public REST API, so they must stay closed rather than gain a placeholder
 * subtype.
 */
public class ScheduleTaskView {
   public ScheduleTaskView(String taskId, String path, ScheduleTask task,
                           List<Object> conditions, List<Object> actions)
   {
      this(taskId, path, null, task, conditions, actions);
   }

   public ScheduleTaskView(String taskId, String path, String timeZone, ScheduleTask task,
                           List<Object> conditions, List<Object> actions)
   {
      this(taskId, path, timeZone, task, conditions, actions, null);
   }

   public ScheduleTaskView(String taskId, String path, String timeZone, ScheduleTask task,
                           List<Object> conditions, List<Object> actions,
                           List<EmailZipPasswordView> emailZipPasswords)
   {
      this.emailZipPasswords = emailZipPasswords == null ? List.of() : emailZipPasswords;
      this.timeZone = timeZone;
      this.taskId = taskId;
      this.path = path;
      this.task = task;
      this.conditions = conditions;
      this.actions = actions;
   }

   public String getTaskId() {
      return taskId;
   }

   /** The folder the task sits in: {@code "/"} for the root, no leading slash otherwise. */
   public String getPath() {
      return path;
   }

   /**
    * The task-level time zone id (the portal Options tab's Time Zone), {@code null} when the task
    * has none. Not on the public {@link ScheduleTask} DTO, which is also the public REST body.
    */
   public String getTimeZone() {
      return timeZone;
   }

   public ScheduleTask getTask() {
      return task;
   }

   public List<Object> getConditions() {
      return conditions;
   }

   public List<Object> getActions() {
      return actions;
   }

   /**
    * Which viewsheet actions have an email zip password or secret id stored. The password value
    * is never part of the read. Not on the public {@link ScheduleTask} DTO.
    */
   public List<EmailZipPasswordView> getEmailZipPasswords() {
      return emailZipPasswords;
   }

   private final List<EmailZipPasswordView> emailZipPasswords;
   private final String taskId;
   private final String path;
   private final String timeZone;
   private final ScheduleTask task;
   private final List<Object> conditions;
   private final List<Object> actions;
}
