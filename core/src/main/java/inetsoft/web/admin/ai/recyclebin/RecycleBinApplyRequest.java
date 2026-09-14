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
package inetsoft.web.admin.ai.recyclebin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for {@code POST /api/wiz/v1/admin/recycle-bin/apply}: a plan request plus the hash
 * from {@code preview}. {@code reviewOutcome} is required whenever the resolved plan's {@code
 * requiresAgentSignoff} is {@code true} (any {@code purge} entry, or a {@code restore} entry whose
 * destination collision was accepted via {@code overwrite: true}). {@code
 * acknowledgeIrreversibleDelete} must be exactly {@code true} whenever the plan contains a {@code
 * purge} entry OR a {@code restore} entry that will overwrite (and thereby permanently destroy) an
 * existing asset at the destination -- both are the same "no live inverse for the destroyed asset"
 * class of action, so this reuses the existing flag name rather than inventing a second one
 * (track-a-recycle-bin/01-design.md section 6 item 3, settled without escalation per
 * 03-reconcile.md).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecycleBinApplyRequest extends RecycleBinChangePlanRequest {
   public String getPlanHash() { return planHash; }
   public void setPlanHash(String v) { this.planHash = v; }
   public String getReviewOutcome() { return reviewOutcome; }
   public void setReviewOutcome(String v) { this.reviewOutcome = v; }
   public Boolean getAcknowledgeIrreversibleDelete() { return acknowledgeIrreversibleDelete; }
   public void setAcknowledgeIrreversibleDelete(Boolean v) { this.acknowledgeIrreversibleDelete = v; }

   private String planHash, reviewOutcome;
   private Boolean acknowledgeIrreversibleDelete;
}
