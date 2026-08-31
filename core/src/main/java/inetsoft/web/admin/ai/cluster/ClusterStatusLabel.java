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

import inetsoft.web.cluster.ServerClusterStatus;

/**
 * The same three-way display label {@code ClusterService.getClusterStatus()} computes per node
 * (paused takes priority over running/down), reproduced here so this area's read tools, plan
 * resolution, and apply read-back can all compute it for a single, freshly-read
 * {@link ServerClusterStatus} without a second round trip through {@code ClusterService}'s own
 * all-servers method (01-spec.md section 3/5/6).
 */
final class ClusterStatusLabel {
   private ClusterStatusLabel() {
   }

   static final String STATUS_RUNNING = "Running";
   static final String STATUS_PAUSED = "Paused";
   static final String STATUS_STOPPED = "Stopped";

   static String displayStatus(ServerClusterStatus status) {
      if(status.isPaused()) {
         return STATUS_PAUSED;
      }

      return reachable(status) ? STATUS_RUNNING : STATUS_STOPPED;
   }

   /** {@code status != DOWN} -- 01-spec.md section 3's own definition. */
   static boolean reachable(ServerClusterStatus status) {
      return status.getStatus() != ServerClusterStatus.Status.DOWN;
   }
}
