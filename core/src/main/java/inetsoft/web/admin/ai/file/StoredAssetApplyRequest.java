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
package inetsoft.web.admin.ai.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for {@code POST /api/wiz/v1/admin/file/content/apply}. {@code
 * acknowledgeIrreversibleDelete} must be exactly {@code true} whenever the freshly re-resolved
 * plan contains any non-compensable change (a folder delete, or a file delete/overwrite whose
 * prior content could not be captured) -- 01-design.md section 6.3.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoredAssetApplyRequest extends StoredAssetChangePlanRequest {
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
