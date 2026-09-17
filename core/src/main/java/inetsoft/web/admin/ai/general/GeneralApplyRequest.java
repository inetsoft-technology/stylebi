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
package inetsoft.web.admin.ai.general;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for {@code POST /api/wiz/v1/admin/general/apply}.
 *
 * <p><b>No {@code acknowledgeIrreversibleUpdate}</b>, deliberately, where
 * {@code PresentationApplyRequest} has one. Every writable sub-model in this area is compensable
 * -- see {@code GeneralSubModel.compensable()} for why storage scope does not imply otherwise here
 * -- so such a flag would be satisfiable on every single plan, which trains a caller to pass it
 * unread. The one genuinely irreversible operation in this area, cache cleanup, is a separate
 * endpoint with its own {@code acknowledgeIrreversibleAction} flag that means something.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneralApplyRequest extends GeneralChangePlanRequest {
   public String getPlanHash() {
      return planHash;
   }

   public void setPlanHash(String v) {
      this.planHash = v;
   }

   public String getTaskToken() {
      return taskToken;
   }

   public void setTaskToken(String v) {
      this.taskToken = v;
   }

   public String getReviewOutcome() {
      return reviewOutcome;
   }

   public void setReviewOutcome(String v) {
      this.reviewOutcome = v;
   }

   private String planHash;
   private String taskToken;
   private String reviewOutcome;
}
