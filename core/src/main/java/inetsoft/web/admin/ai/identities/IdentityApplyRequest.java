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
package inetsoft.web.admin.ai.identities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Request body for {@code POST /api/wiz/v1/admin/identities/apply}: a plan request plus the hash
 * from {@code preview}. The Tier-2 backup (spec section 6/7 -- unconditional for every verb in this
 * area) is taken synchronously inside {@link IdentityChangesetApplyService#apply}, under the same
 * freshly generated transaction id used for the resulting {@link IdentityApplyResult}/audit trail,
 * matching {@code inetsoft.web.admin.ai.AdminChangesetApplyService}'s own existing pattern -- not a
 * separate precondition the caller must satisfy first (an earlier draft of this class carried a
 * {@code backupTransactionId} field for that now-abandoned design; removed, since the transaction
 * id apply hashes/audits under is generated fresh inside apply itself, so a caller could never know
 * it in advance to pre-run a matching {@code backup} call). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentityApplyRequest extends IdentityChangePlanRequest {
   public String getPlanHash() { return planHash; }
   public void setPlanHash(String v) { this.planHash = v; }
   public String getTaskToken() { return taskToken; }
   public void setTaskToken(String v) { this.taskToken = v; }
   public String getReviewOutcome() { return reviewOutcome; }
   public void setReviewOutcome(String v) { this.reviewOutcome = v; }

   private String planHash, taskToken, reviewOutcome;
}
