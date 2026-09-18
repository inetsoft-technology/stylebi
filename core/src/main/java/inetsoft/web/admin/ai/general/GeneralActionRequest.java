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
 * Request body for this area's two action endpoints -- cache cleanup and locale reload.
 *
 * <p>Actions are not plan-shaped: there is no before/after value to diff, so there is no
 * {@code planHash} or {@code taskToken} to carry. What remains is {@code task}, the operator's own
 * description of why, which is what the audit record gets.
 *
 * <p>{@code acknowledgeIrreversibleAction} is required (exactly {@code true}) for cache cleanup
 * only. Cleanup deletes cached data across every live node and nothing restores it; it is the one
 * genuinely irreversible operation this area offers, which is why it -- and not the ordinary
 * change path -- carries the acknowledgement. Locale reload ignores the flag: it is idempotent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneralActionRequest {
   public String getTask() {
      return task;
   }

   public void setTask(String v) {
      this.task = v;
   }

   public Boolean getAcknowledgeIrreversibleAction() {
      return acknowledgeIrreversibleAction;
   }

   public void setAcknowledgeIrreversibleAction(Boolean v) {
      this.acknowledgeIrreversibleAction = v;
   }

   private String task;
   private Boolean acknowledgeIrreversibleAction;
}
