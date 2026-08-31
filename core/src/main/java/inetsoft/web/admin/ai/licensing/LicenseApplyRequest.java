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
package inetsoft.web.admin.ai.licensing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for {@code POST /api/wiz/v1/admin/licensing/apply}: a plan request plus the hash
 * from {@code preview}. {@code acknowledgeDelicensing} must be exactly {@code true} when the
 * freshly re-resolved plan would leave zero installed license keys (01-spec.md section 5/6) --
 * an advisory gate, not a hard refusal (section 14 D3), mirroring
 * {@code DataSourceApplyRequest.acknowledgeIrreversibleDelete}'s shape, not its meaning: that flag
 * gates a non-compensable action, this one gates a reversible-but-severe one.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LicenseApplyRequest extends LicenseChangePlanRequest {
   public String getPlanHash() { return planHash; }
   public void setPlanHash(String v) { this.planHash = v; }
   public String getReviewOutcome() { return reviewOutcome; }
   public void setReviewOutcome(String v) { this.reviewOutcome = v; }
   public Boolean getAcknowledgeDelicensing() { return acknowledgeDelicensing; }
   public void setAcknowledgeDelicensing(Boolean v) { this.acknowledgeDelicensing = v; }

   private String planHash, reviewOutcome;
   private Boolean acknowledgeDelicensing;
}
