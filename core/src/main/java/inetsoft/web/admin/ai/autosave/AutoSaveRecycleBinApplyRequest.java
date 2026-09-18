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
package inetsoft.web.admin.ai.autosave;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for {@code POST /api/wiz/v1/admin/autosave-recyclebin/apply}: a plan request plus
 * the hash from {@code preview}. {@code reviewOutcome} is required whenever the resolved plan's
 * {@code requiresAgentSignoff} is {@code true} (any {@code delete} entry, unconditionally, or a
 * {@code restore} entry whose destination collision was accepted via {@code overwrite: true}).
 * {@code acknowledgeIrreversibleDelete} must be exactly {@code true} in the same two cases.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutoSaveRecycleBinApplyRequest extends AutoSaveRecycleBinChangePlanRequest {
   public String getPlanHash() { return planHash; }
   public void setPlanHash(String v) { this.planHash = v; }
   public String getReviewOutcome() { return reviewOutcome; }
   public void setReviewOutcome(String v) { this.reviewOutcome = v; }
   public Boolean getAcknowledgeIrreversibleDelete() { return acknowledgeIrreversibleDelete; }
   public void setAcknowledgeIrreversibleDelete(Boolean v) { this.acknowledgeIrreversibleDelete = v; }

   private String planHash, reviewOutcome;
   private Boolean acknowledgeIrreversibleDelete;
}
