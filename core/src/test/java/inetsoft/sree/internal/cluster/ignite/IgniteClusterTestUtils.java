/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.internal.cluster.ignite;

import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class IgniteClusterTestUtils {

   public static IgniteCluster getIgniteCluster(String name, TcpDiscoveryIpFinder ipFinder,
                                                Path clusterDir)
   {
      return getIgniteCluster(name, ipFinder, clusterDir, false);
   }

   public static IgniteCluster getIgniteCluster(String name, TcpDiscoveryIpFinder ipFinder,
                                                Path clusterDir, boolean scheduler)
   {
      IgniteConfiguration config = IgniteCluster.getDefaultConfig(clusterDir);
      config.setIgniteInstanceName(name);

      if(ipFinder != null) {
         TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
         discoverySpi.setIpFinder(ipFinder);
         config.setDiscoverySpi(discoverySpi);
      }

      if(scheduler) {
         Map<String, Object> attrs = new HashMap<>();
         attrs.put("scheduler", Boolean.TRUE);
         config.setUserAttributes(attrs);
      }

      return new IgniteCluster(config);
   }
}
