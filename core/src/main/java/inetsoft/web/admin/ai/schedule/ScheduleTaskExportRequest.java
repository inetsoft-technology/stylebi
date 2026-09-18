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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Request body for {@code POST /api/wiz/v1/admin/schedule/export} -- a bare, synchronous action,
 * matching {@code RepositoryExportRequest}'s own precedent (no preview/apply/planHash, since
 * nothing is mutated).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleTaskExportRequest {
   public List<String> getTaskIds() { return taskIds; }
   public void setTaskIds(List<String> v) { this.taskIds = v; }
   public Boolean getIncludeDependencies() { return includeDependencies; }
   public void setIncludeDependencies(Boolean v) { this.includeDependencies = v; }

   private List<String> taskIds;
   private Boolean includeDependencies;
}
