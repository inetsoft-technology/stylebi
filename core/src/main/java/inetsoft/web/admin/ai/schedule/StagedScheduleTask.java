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
 * One task parsed out of a staged import file -- the {@code stage_schedule_task_import} response
 * element. {@code dependency} is a comma-joined list of OTHER staged task ids this one depends on
 * (mirroring {@code ImportTaskController}'s own {@code TaskDependencyModel} shape exactly, so a
 * caller sees the identical dependency information EM's own Import Task dialog would show).
 * {@code existsAlready} is a live, current-server check (re-verified fresh at both preview and
 * apply time -- never trusted from this staging-time snapshot) telling the caller upfront which
 * entries will need {@code overwrite: true} to import.
 */
public record StagedScheduleTask(String taskId, String dependency, boolean existsAlready) {
}
