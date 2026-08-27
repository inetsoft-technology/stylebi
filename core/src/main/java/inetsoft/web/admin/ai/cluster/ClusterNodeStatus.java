/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.ai.cluster;

/**
 * Read-tool projection for one configured cluster server (01-spec.md section 3): the same
 * {@code "Running"|"Paused"|"Stopped"} display label {@code ClusterService.getClusterStatus()}
 * already computes, plus {@code reachable} -- this area's own addition, {@code status != DOWN},
 * surfacing at read time the blind spot that a {@code DOWN} node's pause/resume silently no-ops
 * server-side today (section 1 of the scoping doc, re-verified in 01-spec.md section 3).
 */
public record ClusterNodeStatus(String server, String status, boolean reachable) {
}
