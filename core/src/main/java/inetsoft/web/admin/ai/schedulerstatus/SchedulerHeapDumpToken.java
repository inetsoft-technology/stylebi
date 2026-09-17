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
package inetsoft.web.admin.ai.schedulerstatus;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response of {@code POST /api/wiz/v1/admin/scheduler/heap-dump}: the id
 * {@code ServerService.createHeapDump} returned, and the resolved node it was created against
 * (never a bare IP filter -- the exact value {@code ServerService}'s own methods expect back on
 * every subsequent call for this id). Poll with
 * {@code GET .../scheduler/heap-dump/{token}/status?clusterNode=<node>}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SchedulerHeapDumpToken(String token, String node) {
}
