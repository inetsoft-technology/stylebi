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
 * Response body for {@code POST /api/wiz/v1/admin/schedule/transfer/export} -- a bare, synchronous
 * action
 * (no preview/apply/planHash) since export mutates nothing on the server, matching
 * {@code export_repository_assets}'s own precedent.
 *
 * @param xml                base64-encoded {@code <schedule>...</schedule>} XML, one
 *                           {@code <Task>} element per included task plus a trailing
 *                           {@code <timeRanges>} block enumerating every currently-configured
 *                           Time Range (StyleBI's own {@code ScheduleService.exportScheduledTasks}
 *                           always includes all of them, regardless of which tasks were selected --
 *                           not a gap this area introduces).
 * @param includedTaskIds    every task id actually written into {@code xml}, explicitly-requested
 *                           and auto-included dependencies both.
 * @param includedDependencyIds the subset of {@code includedTaskIds} that were NOT explicitly
 *                           requested but were pulled in because something requested was
 *                           requested depends on them (only populated when
 *                           {@code includeDependencies} was true).
 * @param missingDependencyIds dependencies of a requested task that were NOT included (only
 *                           possible when {@code includeDependencies} was false) -- relay this to
 *                           the human verbatim: importing this export elsewhere without those
 *                           tasks already present will leave the dependent task's own dependency
 *                           reference dangling.
 */
public record ScheduleTaskExportResult(String xml, List<String> includedTaskIds,
                                       List<String> includedDependencyIds,
                                       List<String> missingDependencyIds)
{
}
