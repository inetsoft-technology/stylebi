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
package inetsoft.web.admin.ai.schedulerstatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One requested change: start, stop, or restart the scheduler (track-status/01-design.md section
 * 5a). Unlike {@code ClusterChangeRequest}, there is no {@code server} field -- this whole area
 * addresses exactly one target, "the scheduler", never a named node (start/stop/restart is refused
 * entirely on a clustered deployment, see {@link SchedulerStatusChangePlanService}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchedulerStatusChangeRequest {
   public static final String VERB_START = "start";
   public static final String VERB_STOP = "stop";
   public static final String VERB_RESTART = "restart";

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   private String verb;
}
