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

/**
 * Request body for {@code POST /api/wiz/v1/admin/plugins/install} (03-reconcile.md). Unlike the
 * plan/hash mechanism every other high-risk area in this plugin uses, there is no
 * {@code planHash}/{@code taskToken} here -- an upload that has never been installed has no prior
 * state to diff a plan against. {@code acknowledgeServerCodeExecution} is required {@code true}
 * unconditionally (deliberately not reusing {@code acknowledgeIrreversibleDelete}: installing a
 * plugin/driver executes as the server, a different risk class than "this cannot be undone").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdminInstallDriverOrPluginRequest {
   public String getTask() { return task; }
   public void setTask(String v) { this.task = v; }
   public String getUploadId() { return uploadId; }
   public void setUploadId(String v) { this.uploadId = v; }
   public AdminDriverPluginSpec getAsDriverPlugin() { return asDriverPlugin; }
   public void setAsDriverPlugin(AdminDriverPluginSpec v) { this.asDriverPlugin = v; }
   public boolean isAcknowledgeServerCodeExecution() { return acknowledgeServerCodeExecution; }
   public void setAcknowledgeServerCodeExecution(boolean v) { this.acknowledgeServerCodeExecution = v; }
   public String getReviewOutcome() { return reviewOutcome; }
   public void setReviewOutcome(String v) { this.reviewOutcome = v; }

   private String task, uploadId, reviewOutcome;
   private AdminDriverPluginSpec asDriverPlugin;
   private boolean acknowledgeServerCodeExecution;
}
