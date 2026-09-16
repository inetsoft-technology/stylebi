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
package inetsoft.web.admin.ai.shapes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for {@code POST /api/wiz/v1/admin/shapes/apply}. Unlike Stored Assets' own apply
 * request, there is no {@code acknowledgeIrreversibleDelete} flag here -- every change plan in this
 * area is fully compensable by construction (01-design.md section 2.3/3.2: both {@code upload} and
 * {@code delete} unconditionally capture the prior bytes needed for a live rollback), so there is no
 * irreversible case to gate behind an extra flag.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShapeApplyRequest extends ShapeChangePlanRequest {
   public String getPlanHash() { return planHash; }
   public void setPlanHash(String v) { this.planHash = v; }

   public String getTaskToken() { return taskToken; }
   public void setTaskToken(String v) { this.taskToken = v; }

   public String getReviewOutcome() { return reviewOutcome; }
   public void setReviewOutcome(String v) { this.reviewOutcome = v; }

   private String planHash, taskToken, reviewOutcome;
}
