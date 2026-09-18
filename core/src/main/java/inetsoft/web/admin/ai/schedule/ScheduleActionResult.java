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

import java.util.List;

/**
 * Result of a {@code run-tasks}/{@code stop-tasks} request: one {@link ScheduleActionOutcome} per
 * task name. Unlike {@link ScheduleApplyRequest}'s create/delete plans, run/stop are not
 * changesets -- each task's run/stop is independent, self-inverse (running or stopping a task
 * cannot be un-done the way a create/delete's rollback can), and applied immediately, so there is
 * no {@code status}/{@code backupRef}/rollback bookkeeping here, matching {@code
 * ClusterApplyResult}'s own precedent for the same "each action is live and non-compensable"
 * shape.
 */
public record ScheduleActionResult(List<ScheduleActionOutcome> results) {
}
