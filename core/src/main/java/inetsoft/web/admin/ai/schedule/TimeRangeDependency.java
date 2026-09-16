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
 * One live schedule task whose {@code TimeCondition} currently resolves (by name) to a time range
 * that a plan is about to delete or identity-change (name/startTime/endTime). {@code
 * reassignedToRangeName} is the range {@code TaskBalancer}/{@code TimeRange.getMatchingTimeRange}
 * would silently reassign the task to if the change were applied unchecked -- {@code null} when no
 * other range remains at all (the task would be left with a dangling reference and no reassignment
 * candidate). See {@code 01-design.md}'s "Revision -- delete-dependency check" for why this is
 * computed server-side rather than via a per-task client scan.
 */
public record TimeRangeDependency(String taskId, String reassignedToRangeName) {
}
