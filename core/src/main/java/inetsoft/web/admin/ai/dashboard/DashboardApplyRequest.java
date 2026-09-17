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
package inetsoft.web.admin.ai.dashboard;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for {@code POST /api/wiz/v1/admin/dashboards/apply}: a plan request plus the hash
 * from {@code preview}. {@code reviewOutcome} is required whenever the resolved plan's {@code
 * requiresAgentSignoff} is {@code true} (a dashboard delete is the only {@code risk: high} verb in
 * this area).
 *
 * <p>Unlike the viewsheet area, no {@code acknowledgeIrreversibleDelete} field exists here: a
 * dashboard delete only removes the registry binding (name/description/viewsheet reference), never
 * the underlying viewsheet asset's content, so it is always compensable by re-creating the same
 * binding -- {@link DashboardChangesetApplyService} always queues a delete for rollback.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DashboardApplyRequest extends DashboardChangePlanRequest {
   public String getPlanHash() { return planHash; }
   public void setPlanHash(String v) { this.planHash = v; }
   public String getTaskToken() { return taskToken; }
   public void setTaskToken(String v) { this.taskToken = v; }
   public String getReviewOutcome() { return reviewOutcome; }
   public void setReviewOutcome(String v) { this.reviewOutcome = v; }

   private String planHash, taskToken, reviewOutcome;
}
