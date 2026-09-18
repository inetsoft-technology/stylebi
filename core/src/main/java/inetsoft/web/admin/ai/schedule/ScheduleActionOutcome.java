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

/**
 * Outcome of one attempted run/stop, one entry per task name in a {@code run-tasks}/{@code
 * stop-tasks} request (bug 76687). Unlike {@code EMScheduleController#runTasks}/{@code
 * #stopTasks}, which flatten every failure into a bare, {@code Catalog}-localized {@code
 * MessageException} string, {@code status} is a field-named, locale-independent discriminator so a
 * caller (admin-chat) can tell "scheduler down" apart from "task disabled" apart from a generic
 * failure without string-matching a translated message.
 *
 * @param taskName the task name, echoed back from the request.
 * @param status   {@code "started"}/{@code "stopped"} on success (matching the verb), {@code
 *                 "scheduler-not-running"} or {@code "task-disabled"} for the two conditions
 *                 {@code ScheduleService} itself distinguishes before attempting the run/stop, or
 *                 {@code "failed"} for any other error (including one thrown by {@code
 *                 ScheduleService} itself after this class's own pre-checks passed -- see {@code
 *                 AdminScheduleGateway#runTask}/{@code #stopTask}).
 * @param error    non-null only when {@code status} is not {@code "started"}/{@code "stopped"}.
 */
public record ScheduleActionOutcome(String taskName, String status, String error) {
   public static final String STARTED = "started";
   public static final String STOPPED = "stopped";
   public static final String SCHEDULER_NOT_RUNNING = "scheduler-not-running";
   public static final String TASK_DISABLED = "task-disabled";
   public static final String FAILED = "failed";
}
