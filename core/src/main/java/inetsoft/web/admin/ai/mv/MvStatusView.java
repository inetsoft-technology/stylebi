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
package inetsoft.web.admin.ai.mv;

import inetsoft.web.admin.content.repository.model.MaterializedModel;

import java.util.List;

/**
 * Response for {@code GET /api/wiz/v1/admin/mv/status}. {@code canMaterialize} lets a caller see a
 * future {@code apply_mv_changes} 403 risk before building a whole plan around it -- every mutating
 * endpoint underneath enforces the same {@code MATERIALIZATION}/{@code ACCESS} check server-side.
 */
public record MvStatusView(boolean wsMvEnabled, boolean canMaterialize, List<MaterializedModel> mvs) {
}
