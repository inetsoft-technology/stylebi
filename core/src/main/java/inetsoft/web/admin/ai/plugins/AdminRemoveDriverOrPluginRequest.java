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
package inetsoft.web.admin.ai.plugins;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Request body for {@code POST /api/wiz/v1/admin/plugins/remove} (03-reconcile.md). No
 * {@code planHash}/{@code taskToken}: removal is a simple, already-audited-server-side,
 * reversible-by-reinstall operation, and no automatic Tier-2 backup is proposed either, unlike
 * e.g. asset-import's silent overwrite of arbitrary repository content.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdminRemoveDriverOrPluginRequest {
   public String getTask() { return task; }
   public void setTask(String v) { this.task = v; }
   public List<String> getPluginIds() { return pluginIds; }
   public void setPluginIds(List<String> v) { this.pluginIds = v; }
   public boolean isAcknowledgeIrreversibleRemove() { return acknowledgeIrreversibleRemove; }
   public void setAcknowledgeIrreversibleRemove(boolean v) { this.acknowledgeIrreversibleRemove = v; }
   public String getReviewOutcome() { return reviewOutcome; }
   public void setReviewOutcome(String v) { this.reviewOutcome = v; }

   private String task, reviewOutcome;
   private List<String> pluginIds;
   private boolean acknowledgeIrreversibleRemove;
}
