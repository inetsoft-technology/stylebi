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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.web.cluster.ServerClusterClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Creates the {@link ServerClusterClient} this area reads the cluster through, a new one per
 * request, as EM's {@code ClusterService} does. The shared {@code ServerClusterClient} bean keeps the
 * node list it saw at startup and caches node addresses forever, so it never lists a node that
 * joined later and keeps reporting a departed node from its last status-map entry (bugs #82187,
 * #82189).
 */
@Component
public class ClusterClientFactory {
   @Autowired
   public ClusterClientFactory(Cluster cluster) {
      this.cluster = cluster;
   }

   public ServerClusterClient create() {
      return new ServerClusterClient(false, cluster);
   }

   private final Cluster cluster;
}
