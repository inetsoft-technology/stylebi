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
package inetsoft.mv;

import inetsoft.mv.data.MV;
import inetsoft.mv.data.MVBuilder;
import inetsoft.mv.data.MVStorage;
import inetsoft.mv.fs.FSConfig;
import inetsoft.mv.fs.FSService;
import inetsoft.mv.fs.XDataNode;
import inetsoft.mv.fs.XFileSystem;
import inetsoft.mv.fs.XServerNode;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link MVSingleDispatcher#dispatch0()} always releases the {@code mv.exec.*}
 * write lock (and the {@code mv.fs.update} key lock) even when an exception is thrown while
 * updating the distributed file system, so a failed MV creation/update never leaves the lock
 * held for subsequent attempts.
 */
@Tag("core")
class MVSingleDispatcherTest {
   @Test
   void alwaysReleasesTheWriteLockEvenWhenFileSystemRefreshFails() throws Exception {
      MVDef def = mock(MVDef.class);
      when(def.getName()).thenReturn("mv1");

      MVSingleDispatcher dispatcher = spy(new MVSingleDispatcher(def));

      XTable data = mock(XTable.class);
      MVBuilder builder = mock(MVBuilder.class);
      MV mv = mock(MV.class);
      when(builder.getMV()).thenReturn(mv);

      doReturn(data).when(dispatcher).getData(anyBoolean(), any());
      doReturn(builder).when(dispatcher).getMVBuilder();
      doReturn(Collections.emptyList()).when(dispatcher).saveTempFile(same(builder));

      XFileSystem fsys = mock(XFileSystem.class);
      FSConfig config = mock(FSConfig.class);
      when(config.isDesktop()).thenReturn(false);
      XServerNode server = mock(XServerNode.class);
      when(server.getFSystem()).thenReturn(fsys);
      when(server.getConfig()).thenReturn(config);

      XDataNode dataNode = mock(XDataNode.class);

      RuntimeException refreshFailure = new RuntimeException("refresh failed");
      doThrow(refreshFailure).when(fsys).refresh(any(), anyBoolean());

      MVStorage storage = mock(MVStorage.class);
      when(storage.exists(anyString())).thenReturn(false);

      try(MockedStatic<FSService> fsServiceStatic = mockStatic(FSService.class);
          MockedStatic<Cluster> clusterStatic = mockStatic(Cluster.class);
          MockedStatic<MVStorage> storageStatic = mockStatic(MVStorage.class, CALLS_REAL_METHODS))
      {
         fsServiceStatic.when(FSService::getServer).thenReturn(server);
         fsServiceStatic.when(FSService::getDataNode).thenReturn(dataNode);

         Cluster cluster = mock(Cluster.class);
         clusterStatic.when(Cluster::getInstance).thenReturn(cluster);

         storageStatic.when(MVStorage::getInstance).thenReturn(storage);

         RuntimeException thrown =
            assertThrows(RuntimeException.class, dispatcher::dispatch0);
         assertSame(refreshFailure, thrown);

         String lockName = "mv.exec.mv1";
         verify(cluster).lockWrite(lockName);
         verify(cluster).unlockWrite(lockName);
         verify(cluster).lockKey("mv.fs.update");
         verify(cluster).unlockKey("mv.fs.update");
      }
   }
}
