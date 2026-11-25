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
package inetsoft.uql.service;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageEvent;
import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.uql.jdbc.ConnectionPoolFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/**
 * Bug #82097: the XEngine bean is typed as the XRepository interface, so Spring AOT never ran
 * its {@code @PostConstruct}; the cluster listener was not registered and remote nodes never
 * closed their JDBC connection pools. The listener must be registered by the constructor.
 */
@Tag("core")
class XEngineClusterListenerTest {
   @Test
   void constructorRegistersClusterListener() throws Exception {
      Cluster cluster = mock(Cluster.class);
      XEngine engine = createEngine(cluster, mock(ConnectionPoolFactory.class));

      verify(cluster).addMessageListener(any(MessageListener.class));
      engine.close();
   }

   @Test
   void connectionChangedMessageClosesPools() throws Exception {
      Cluster cluster = mock(Cluster.class);
      ConnectionPoolFactory pools = mock(ConnectionPoolFactory.class);
      XEngine engine = createEngine(cluster, pools);

      MessageListener listener = captureListener(cluster);
      listener.messageReceived(new MessageEvent(
         this, "remote", false, new DataSourceRegistry.DataSourceConnectionChangedMessage()));

      verify(pools, timeout(2000)).closeAllConnectionsPools();
      engine.close();
   }

   @Test
   void burstOfMessagesClosesPoolsAtLeastOnce() throws Exception {
      Cluster cluster = mock(Cluster.class);
      ConnectionPoolFactory pools = mock(ConnectionPoolFactory.class);
      XEngine engine = createEngine(cluster, pools);

      MessageListener listener = captureListener(cluster);

      for(int i = 0; i < 5; i++) {
         listener.messageReceived(new MessageEvent(
            this, "remote", false, new DataSourceRegistry.DataSourceConnectionChangedMessage()));
      }

      verify(pools, timeout(2000).atLeastOnce()).closeAllConnectionsPools();
      engine.close();
   }

   @Test
   void closeRemovesSameListener() throws Exception {
      Cluster cluster = mock(Cluster.class);
      XEngine engine = createEngine(cluster, mock(ConnectionPoolFactory.class));
      MessageListener added = captureListener(cluster);

      engine.close();

      ArgumentCaptor<MessageListener> removed = ArgumentCaptor.forClass(MessageListener.class);
      verify(cluster).removeMessageListener(removed.capture());
      assertSame(added, removed.getValue());
   }

   private XEngine createEngine(Cluster cluster, ConnectionPoolFactory pools) {
      return new XEngine(cluster, null, mock(DataSourceRegistry.class), pools);
   }

   private MessageListener captureListener(Cluster cluster) {
      ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
      verify(cluster).addMessageListener(captor.capture());
      return captor.getValue();
   }
}
