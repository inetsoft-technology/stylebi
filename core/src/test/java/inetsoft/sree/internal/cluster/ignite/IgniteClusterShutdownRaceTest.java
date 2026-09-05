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

import inetsoft.sree.internal.cluster.*;
import inetsoft.test.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Regression test for bug #74951: removing a replicated map listener after the
 * cluster has already been closed (e.g. shutdownInetsoft()/ClusterJobStore
 * closing the Ignite-backed cluster before a bean like Plugins/BlobStorage
 * unregisters its listener during its own shutdown) must not throw
 * IgniteIllegalStateException. Fixed as a side effect of commit 1b6ae84fbc.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class IgniteClusterShutdownRaceTest {
   @TempDir
   static Path clusterDir;

   @Test
   void removeReplicatedMapListenerAfterCloseDoesNotThrow() {
      TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);
      IgniteCluster cluster = IgniteClusterTestUtils.getIgniteCluster("bug74951", ipFinder, clusterDir);

      MapChangeListener<String, String> listener = new MapChangeListener<String, String>() {
         @Override
         public void entryAdded(EntryEvent<String, String> event) {
         }

         @Override
         public void entryUpdated(EntryEvent<String, String> event) {
         }

         @Override
         public void entryRemoved(EntryEvent<String, String> event) {
         }
      };

      cluster.addReplicatedMapListener("bug74951-map", listener);
      cluster.close();

      assertDoesNotThrow(() -> cluster.removeReplicatedMapListener("bug74951-map", listener));
   }
}
