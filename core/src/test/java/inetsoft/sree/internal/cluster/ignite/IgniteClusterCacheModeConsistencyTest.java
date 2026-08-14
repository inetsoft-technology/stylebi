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
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.test.*;
import inetsoft.util.config.InetsoftConfig;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that a node whose minReplicas-derived Ignite cache mode disagrees with a node
 * already running in the cluster fails fast on join (Bug #75824) instead of joining and later
 * failing with an unexplained cache mode mismatch exception from Ignite itself.
 */
@Disabled
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class IgniteClusterCacheModeConsistencyTest {
   @TempDir
   Path clusterDir;

   @BeforeEach
   void saveMinNodes() {
      originalMinNodes = InetsoftConfig.getInstance().getCluster().getMinNodes();
   }

   @AfterEach
   void restoreMinNodes() {
      InetsoftConfig.getInstance().getCluster().setMinNodes(originalMinNodes);
   }

   @Test
   void joinFailsWhenCacheModeChangedAcrossThreshold() {
      TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

      // minNodes > 2 -> PARTITIONED
      InetsoftConfig.getInstance().getCluster().setMinNodes(3);
      IgniteCluster ignite1 = IgniteClusterTestUtils.getIgniteCluster("mismatch1", ipFinder, clusterDir);

      try {
         // minNodes <= 2 -> REPLICATED, simulating minReplicas dropped across the threshold
         // and applied as a rolling update instead of a full cluster restart.
         InetsoftConfig.getInstance().getCluster().setMinNodes(2);

         IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> IgniteClusterTestUtils.getIgniteCluster("mismatch2", ipFinder, clusterDir));
         assertTrue(ex.getMessage().contains("restarting every node"), ex.getMessage());
      }
      finally {
         ignite1.close();
      }
   }

   @Test
   void joinSucceedsWhenCacheModeUnchanged() {
      TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);
      InetsoftConfig.getInstance().getCluster().setMinNodes(3);

      IgniteCluster ignite1 = IgniteClusterTestUtils.getIgniteCluster("match1", ipFinder, clusterDir);
      IgniteCluster ignite2 = null;

      try {
         ignite2 = IgniteClusterTestUtils.getIgniteCluster("match2", ipFinder, clusterDir);
         assertEquals(2, ignite1.getIgniteInstance().cluster().forServers().nodes().size());
      }
      finally {
         ignite1.close();

         if(ignite2 != null) {
            ignite2.close();
         }
      }
   }

   private int originalMinNodes;
}
