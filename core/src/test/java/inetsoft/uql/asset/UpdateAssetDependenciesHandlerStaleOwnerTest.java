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
package inetsoft.uql.asset;

/*
 * Bug #81437: the rebuild-dependencies state is a cluster-wide atomic long. A node that dies in
 * the middle of a rebuild never resets it, so every later rebuild on a surviving node timed out
 * until the whole cluster was restarted. The handler now records the node id that set UPDATING and
 * treats UPDATING as abandoned once that node has left the cluster.
 *
 * The state is seeded directly, as the dead node would have left it: UPDATING, owned by a node id
 * that is (or is not) in the cluster's node set.
 */

import inetsoft.sree.internal.cluster.DistributedLong;
import inetsoft.sree.internal.cluster.DistributedReference;
import inetsoft.sree.internal.cluster.MockCluster;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XRepository;
import inetsoft.uql.service.DataSourceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UpdateAssetDependenciesHandlerStaleOwnerTest {
   private static final String PREFIX = UpdateAssetDependenciesHandler.class.getName();
   private static final long UPDATING = 1L;
   private static final String DEAD_NODE = "node-that-was-killed";
   private static final String LIVE_NODE = "node-still-running";

   private NodeSetCluster cluster;
   private UpdateAssetDependenciesHandler handler;

   @BeforeEach
   void setUp() {
      cluster = new NodeSetCluster();
      cluster.nodes.add(LIVE_NODE);
      handler = new UpdateAssetDependenciesHandler(
         cluster, mock(DataSourceRegistry.class), mock(XRepository.class));
   }

   private void seedUpdating(String owner) {
      DistributedLong state = cluster.getLong(PREFIX + ".state");
      state.set(UPDATING);

      if(owner != null) {
         DistributedReference<String> ref = cluster.getReference(PREFIX + ".owner");
         ref.set(owner);
      }
   }

   @Test
   void rebuildRunsWhenTheNodeThatSetUpdatingHasLeftTheCluster() throws Exception {
      seedUpdating(DEAD_NODE);

      assertTrue(handler.rebuild(2, TimeUnit.SECONDS));
      assertNotEquals(UPDATING, cluster.getLong(PREFIX + ".state").get());
   }

   @Test
   void updateRunsWhenTheNodeThatSetUpdatingHasLeftTheCluster() {
      seedUpdating(DEAD_NODE);

      handler.update(false);

      assertNotEquals(UPDATING, cluster.getLong(PREFIX + ".state").get());
   }

   @Test
   void rebuildStillWaitsWhileTheOwnerIsInTheCluster() {
      seedUpdating(LIVE_NODE);

      assertThrows(TimeoutException.class, () -> handler.rebuild(1, TimeUnit.SECONDS));
      assertEquals(UPDATING, cluster.getLong(PREFIX + ".state").get());
   }

   @Test
   void rebuildDoesNotStealWhenTheOwnerIsUnknown() {
      // state left by a node that did not record an owner: cannot tell, so keep waiting
      seedUpdating(null);

      assertThrows(TimeoutException.class, () -> handler.rebuild(1, TimeUnit.SECONDS));
      assertEquals(UPDATING, cluster.getLong(PREFIX + ".state").get());
   }

   /**
    * A cluster whose node set the test controls. Ids are what Cluster#getLocalNodeId and
    * #getClusterNodeIds report on a real (Ignite) cluster.
    */
   private static final class NodeSetCluster extends MockCluster {
      final Set<String> nodes = new HashSet<>();

      @Override
      public String getLocalNodeId() {
         return LIVE_NODE;
      }

      @Override
      public Set<String> getClusterNodeIds() {
         return new HashSet<>(nodes);
      }
   }
}
