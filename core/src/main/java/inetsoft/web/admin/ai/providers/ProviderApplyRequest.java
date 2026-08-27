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
package inetsoft.web.admin.ai.providers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Request body for {@code POST /api/wiz/v1/admin/providers/apply}: a plan request plus the hash
 * from {@code preview}. The Tier-2 backup (01-spec.md section 6/7 -- unconditional for every verb
 * in this area) is taken synchronously inside {@link ProviderChangesetApplyService#apply}, under the
 * freshly generated transaction id used for the resulting {@link ProviderApplyResult}/audit trail --
 * matching every prior area's own apply-request shape. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderApplyRequest extends ProviderChangePlanRequest {
   public String getPlanHash() { return planHash; }
   public void setPlanHash(String v) { this.planHash = v; }
   public String getReviewOutcome() { return reviewOutcome; }
   public void setReviewOutcome(String v) { this.reviewOutcome = v; }

   private String planHash, reviewOutcome;
}
