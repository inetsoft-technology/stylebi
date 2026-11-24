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
package inetsoft.web.cluster;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.web.admin.monitoring.StatusMetricsType;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #82186: the status metrics map is keyed by host (the local member name with its discovery
 * port stripped), but a cluster node resolved by {@code SUtil.computeServerClusterNode} is in
 * {@code host:port} form, so looking metrics up by that node always found nothing.
 */
@Tag("core")
class ServerClusterClientMetricsTest {
   private Map<String, Object> backing;
   private ServerClusterClient client;

   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() {
      backing = new HashMap<>();
      DistributedMap<String, Object> map = mock(DistributedMap.class);
      when(map.get(any())).thenAnswer(inv -> backing.get(inv.getArgument(0)));
      Cluster cluster = mock(Cluster.class);
      when(cluster.<String, Object>getMap(anyString())).thenReturn(map);
      when(cluster.getLocalMember()).thenReturn("172.22.0.3:5701");
      client = new ServerClusterClient(false, cluster);
      backing.put("172.22.0.3", "metrics-of-node-3");
      backing.put("172.22.0.4", "metrics-of-node-4");
   }

   @Test
   void clusterNodeNameWithPortFindsTheMetricsOfThatNode() {
      assertEquals("metrics-of-node-4",
                   client.getMetrics(StatusMetricsType.USER_METRICS, "172.22.0.4:5701"));
      assertEquals("metrics-of-node-3",
                   client.getMetrics(StatusMetricsType.USER_METRICS, "172.22.0.3:5701"));
   }

   @Test
   void bareHostStillFindsTheMetricsOfThatNode() {
      assertEquals("metrics-of-node-4",
                   client.getMetrics(StatusMetricsType.USER_METRICS, "172.22.0.4"));
   }

   @Test
   void nullOrEmptyServerReadsTheLocalNode() {
      assertEquals("metrics-of-node-3", client.getMetrics(StatusMetricsType.USER_METRICS, null));
      assertEquals("metrics-of-node-3", client.getMetrics(StatusMetricsType.USER_METRICS, ""));
   }

   @Test
   void unknownServerHasNoMetrics() {
      assertNull(client.getMetrics(StatusMetricsType.USER_METRICS, "10.9.9.9:5701"));
   }
}
