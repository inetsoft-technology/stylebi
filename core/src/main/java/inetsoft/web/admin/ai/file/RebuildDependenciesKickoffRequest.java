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
package inetsoft.web.admin.ai.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for {@code POST /api/wiz/v1/admin/repository-maintenance/rebuild-dependencies}.
 * {@code timeoutMs} is optional -- an LLM caller has no real basis to choose a lock-acquisition
 * timeout, so the controller supplies {@code DEFAULT_TIMEOUT_MS} when omitted rather than making
 * the raw {@code RebuildDependenciesRequest}'s own required field a hard requirement here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RebuildDependenciesKickoffRequest {
   public Long getTimeoutMs() { return timeoutMs; }
   public void setTimeoutMs(Long v) { this.timeoutMs = v; }

   private Long timeoutMs;
}
