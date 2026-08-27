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
package inetsoft.web.admin.ai.cluster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One requested change: pause or resume one named cluster server node (01-spec.md section 1/11).
 * {@link ClusterChangePlanService#resolve} re-validates every field independently rather than
 * trusting the caller, per this repo's CLAUDE.md tool-robustness rule -- verb aliasing
 * ({@code "stop"}/{@code "unpause"}/{@code "start"}) is the plugin (TypeScript) tool layer's job,
 * matching {@code ProviderChangeRequest}/{@code IdentityChangePlanService}'s own precedent of
 * exact-label-only validation in Java (04-build-java.md).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClusterChangeRequest {
   public static final String VERB_PAUSE = "pause";
   public static final String VERB_RESUME = "resume";

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   /** The server name, exactly as {@code ServerClusterClient.getConfiguredServers()} returns it. */
   public String getServer() { return server; }
   public void setServer(String v) { this.server = v; }

   private String verb;
   private String server;
}
