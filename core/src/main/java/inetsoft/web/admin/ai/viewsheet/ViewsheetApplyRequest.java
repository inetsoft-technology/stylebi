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
package inetsoft.web.admin.ai.viewsheet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for {@code POST /api/wiz/v1/admin/viewsheets/apply}: a plan request plus the hash
 * from {@code preview}. {@code reviewOutcome} is required whenever the resolved plan's {@code
 * requiresAgentSignoff} is {@code true} -- per 01-spec.md section 7, conditional on whether the
 * plan contains any viewsheet verb (risk: high), not unconditional the way every prior
 * storage-scoped area's own signoff requirement was. {@code acknowledgeIrreversibleDelete} must be
 * exactly {@code true} whenever the plan contains a viewsheet delete entry (section 4's
 * non-compensable declaration, section 11) OR a folder delete of a non-empty folder (bug #76469:
 * folder delete recursively hard-deletes every contained viewsheet/worksheet, exactly as
 * irreversible as a viewsheet delete when the folder is non-empty) -- checked in
 * {@link ViewsheetChangesetApplyService#apply}, not silently defaulted. An empty-folder delete has
 * a complete live inverse (recreating the label) and never requires this acknowledgement.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ViewsheetApplyRequest extends ViewsheetChangePlanRequest {
   public String getPlanHash() { return planHash; }
   public void setPlanHash(String v) { this.planHash = v; }
   public String getTaskToken() { return taskToken; }
   public void setTaskToken(String v) { this.taskToken = v; }
   public String getReviewOutcome() { return reviewOutcome; }
   public void setReviewOutcome(String v) { this.reviewOutcome = v; }
   public Boolean getAcknowledgeIrreversibleDelete() { return acknowledgeIrreversibleDelete; }
   public void setAcknowledgeIrreversibleDelete(Boolean v) { this.acknowledgeIrreversibleDelete = v; }

   private String planHash, taskToken, reviewOutcome;
   private Boolean acknowledgeIrreversibleDelete;
}
