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

/** Request body for {@code POST /api/wiz/v1/admin/schedule/transfer/preview}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleTaskImportPlanRequest {
   public String getTask() { return task; }
   public void setTask(String v) { this.task = v; }
   public String getStagingToken() { return stagingToken; }
   public void setStagingToken(String v) { this.stagingToken = v; }
   public List<ScheduleTaskImportChangeRequest> getChanges() { return changes; }
   public void setChanges(List<ScheduleTaskImportChangeRequest> v) { this.changes = v; }

   private String task;
   private String stagingToken;
   private List<ScheduleTaskImportChangeRequest> changes;
}
