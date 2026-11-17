/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.mv.fs;

import inetsoft.sree.internal.cluster.Cluster;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #81573, disposing the file system of an organization destroys its {@code lastLoad} marker
 * for the whole cluster, but the server node is cached on every cluster node. The nodes of the
 * other cluster members are cleared by a cluster task, or a clone that reuses the id of the
 * removed organization fails there on the destroyed marker.
 */
@Tag("core")
class FSServiceClearNodeCacheTest {
   @Test
   void clearingBroadcastsToTheOtherNodesAndDisposesOnce() throws Exception {
      String orgId = "fsclear81573";
      XFileSystem fsystem = mock(XFileSystem.class);
      XServerNode node = mock(XServerNode.class);
      when(node.getFSystem()).thenReturn(fsystem);
      servers().put(orgId, node);

      Cluster cluster = mock(Cluster.class);
      doReturn(CompletableFuture.completedFuture(Collections.<Void>emptyList()))
         .when(cluster).submitAll(any());

      try(MockedStatic<Cluster> mocked = mockStatic(Cluster.class)) {
         mocked.when(Cluster::getInstance).thenReturn(cluster);
         FSService.clearServerNodeCache(orgId);
      }

      assertFalse(servers().containsKey(orgId), "the node of the executing node is dropped");
      verify(fsystem, times(1)).dispose();

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Callable<Void>> task = ArgumentCaptor.forClass(Callable.class);
      verify(cluster).submitAll(task.capture());
      assertInstanceOf(FSService.ClearNodeCache.class, task.getValue());

      // another cluster node keeps its cached node, as the node that created the organization
      // before it was removed on the first node
      XFileSystem staleFsystem = mock(XFileSystem.class);
      XServerNode stale = mock(XServerNode.class);
      when(stale.getFSystem()).thenReturn(staleFsystem);
      servers().put(orgId, stale);
      datas().put(orgId, mock(XDataNode.class));

      task.getValue().call();

      assertFalse(servers().containsKey(orgId), "the stale node is dropped");
      assertFalse(datas().containsKey(orgId));
      // the file system was disposed by the first node, its marker is not destroyed again
      verify(staleFsystem, never()).dispose();
   }

   @Test
   void aFailedBroadcastDoesNotFailTheClear() throws Exception {
      String orgId = "fsclearfail81573";
      XFileSystem fsystem = mock(XFileSystem.class);
      XServerNode node = mock(XServerNode.class);
      when(node.getFSystem()).thenReturn(fsystem);
      servers().put(orgId, node);

      Cluster cluster = mock(Cluster.class);
      doReturn(CompletableFuture.<Collection<Void>>failedFuture(new IllegalStateException("down")))
         .when(cluster).submitAll(any());

      try(MockedStatic<Cluster> mocked = mockStatic(Cluster.class)) {
         mocked.when(Cluster::getInstance).thenReturn(cluster);
         assertDoesNotThrow(() -> FSService.clearServerNodeCache(orgId));
      }

      assertFalse(servers().containsKey(orgId));
      verify(fsystem).dispose();
   }

   @SuppressWarnings("unchecked")
   private static Map<String, XServerNode> servers() {
      return (Map<String, XServerNode>) ReflectionTestUtils.getField(FSService.getService(),
                                                                    "servers");
   }

   @SuppressWarnings("unchecked")
   private static Map<String, XDataNode> datas() {
      return (Map<String, XDataNode>) ReflectionTestUtils.getField(FSService.getService(), "datas");
   }
}
