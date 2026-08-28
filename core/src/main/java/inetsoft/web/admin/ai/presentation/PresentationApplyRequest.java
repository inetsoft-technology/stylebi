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
package inetsoft.web.admin.ai.presentation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for {@code POST /api/wiz/v1/admin/presentation/apply}. {@code
 * acknowledgeIrreversibleUpdate} must be exactly {@code true} whenever the freshly re-resolved plan
 * contains any of the 4 storage-scope sub-models ({@code lookAndFeel}/{@code welcomePage}/
 * {@code loginBanner}/{@code portalIntegration}) -- those have no live inverse in this cut and are
 * gated the same way an irreversible delete is gated elsewhere in this program (01-spec.md section 6).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PresentationApplyRequest extends PresentationChangePlanRequest {
   public String getPlanHash() {
      return planHash;
   }

   public void setPlanHash(String v) {
      this.planHash = v;
   }

   public String getReviewOutcome() {
      return reviewOutcome;
   }

   public void setReviewOutcome(String v) {
      this.reviewOutcome = v;
   }

   public Boolean getAcknowledgeIrreversibleUpdate() {
      return acknowledgeIrreversibleUpdate;
   }

   public void setAcknowledgeIrreversibleUpdate(Boolean v) {
      this.acknowledgeIrreversibleUpdate = v;
   }

   private String planHash;
   private String reviewOutcome;
   private Boolean acknowledgeIrreversibleUpdate;
}
