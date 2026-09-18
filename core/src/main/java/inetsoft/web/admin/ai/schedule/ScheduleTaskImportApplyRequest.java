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

/**
 * Request body for {@code POST /api/wiz/v1/admin/schedule/import/apply}: a plan request plus the
 * {@code planHash}/{@code taskToken} pair from {@code preview} -- the same {@link
 * inetsoft.web.admin.ai.TaskAuditToken} verification every other mutating area in this plugin
 * requires (e.g. {@code AdminAssetImportApplyService}), never skipped just because this area's own
 * token carries no extra digest wrapper (see {@code ScheduleTaskImportChangePlanService}'s own
 * javadoc for why no wrapper is needed here). {@code reviewOutcome} is required whenever the
 * resolved plan's {@code requiresAgentSignoff} is {@code true} (any entry overwriting an existing
 * task). {@code acknowledgeOverwrite} must be exactly {@code true} in the same case -- reusing the
 * Repository Import area's own field name for the identical "you are about to replace something
 * that already exists" gate, rather than inventing a differently-named flag for the same concept.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleTaskImportApplyRequest extends ScheduleTaskImportPlanRequest {
   public String getPlanHash() { return planHash; }
   public void setPlanHash(String v) { this.planHash = v; }
   public String getTaskToken() { return taskToken; }
   public void setTaskToken(String v) { this.taskToken = v; }
   public String getReviewOutcome() { return reviewOutcome; }
   public void setReviewOutcome(String v) { this.reviewOutcome = v; }
   public Boolean getAcknowledgeOverwrite() { return acknowledgeOverwrite; }
   public void setAcknowledgeOverwrite(Boolean v) { this.acknowledgeOverwrite = v; }

   private String planHash, taskToken, reviewOutcome;
   private Boolean acknowledgeOverwrite;
}
