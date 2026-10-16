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
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.util.GroupedThread;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Delivers the broadcast messages of {@link IgniteCluster#sendMessage(Serializable)} to the
 * remote client nodes (cloud-runner task containers and other Ignite clients).
 * <p>
 * A server cannot open a connection to a client started with
 * {@code forceClientToServerConnections}; it asks the client to connect back and waits for up to
 * {@code failureDetectionTimeout}. A client that died without a graceful Ignite stop stays in the
 * topology until discovery drops it, and every send to it blocks for that timeout and then
 * fails. Sending to it as part of a whole-topology group made every broadcast throw and stall
 * its caller (Bug #78364).
 * <p>
 * So each client gets its own bounded queue served by its own thread: the caller never blocks
 * on a client and never sees a client's failure, a dead client never delays the messages for a
 * live one, and the messages for one client keep the order in which they were sent. After a
 * failed send to a client that is still in the topology, the messages for that client are
 * dropped for a while (the client failure detection timeout), long enough for discovery to drop
 * a dead client without stalling a thread on it for every message.
 */
final class ClientMessageSender {
   ClientMessageSender(Ignite ignite, String topic, long skipMillis) {
      this.ignite = ignite;
      this.topic = topic;
      this.skipMillis = skipMillis;
   }

   /**
    * Queues a message for every remote client node in the current topology. Never blocks and
    * never throws for a client that is unreachable or has left.
    */
   void send(Serializable message) {
      if(closed) {
         return;
      }

      for(ClusterNode node : ignite.cluster().forClients().forRemotes().nodes()) {
         queues.computeIfAbsent(node.id(), ClientQueue::new).submit(message);
      }

      // drop the queues of clients that have left the topology
      queues.entrySet().removeIf(e -> {
         if(isInTopology(e.getKey())) {
            return false;
         }

         e.getValue().close();
         return true;
      });

      if(closed) {
         close();
      }
   }

   void close() {
      closed = true;
      queues.values().forEach(ClientQueue::close);
      queues.clear();
   }

   private boolean isInTopology(UUID nodeId) {
      try {
         return ignite.cluster().node(nodeId) != null;
      }
      catch(Exception e) {
         // the local node is stopping
         return false;
      }
   }

   private final class ClientQueue {
      ClientQueue(UUID nodeId) {
         this.nodeId = nodeId;
         this.executor = new ThreadPoolExecutor(
            1, 1, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(MAX_QUEUED_MESSAGES),
            r -> {
               Thread thread = new GroupedThread(r, "IgniteClientMessages-" + nodeId);
               thread.setDaemon(true);
               return thread;
            });
         this.executor.allowCoreThreadTimeOut(true);
      }

      void submit(Serializable message) {
         if(isSkipping()) {
            LOG.debug("Dropped a cluster message for unreachable client node {}", nodeId);
            return;
         }

         try {
            executor.execute(() -> deliver(message));
         }
         catch(RejectedExecutionException e) {
            if(!executor.isShutdown() && overflowLogged.compareAndSet(false, true)) {
               LOG.warn("Too many cluster messages are queued for client node {}, " +
                           "dropping new ones until the queue drains", nodeId);
            }
         }
      }

      private void deliver(Serializable message) {
         if(isSkipping()) {
            LOG.debug("Dropped a cluster message for unreachable client node {}", nodeId);
            return;
         }

         try {
            ignite.message(ignite.cluster().forNodeId(nodeId)).sendOrdered(topic, message, 0);
            overflowLogged.set(false);
         }
         catch(Exception e) {
            if(executor.isShutdown() || !isInTopology(nodeId)) {
               LOG.debug("Did not send a cluster message to client node {}, it left the cluster",
                         nodeId, e);
            }
            else {
               skipUntil = System.currentTimeMillis() + skipMillis;
               LOG.warn("Failed to send a cluster message to client node {}, messages for it " +
                           "are dropped for {} ms or until it leaves the cluster: {}",
                        nodeId, skipMillis, e.toString());
               LOG.debug("Failed to send a cluster message to client node {}", nodeId, e);
            }
         }
      }

      private boolean isSkipping() {
         return System.currentTimeMillis() < skipUntil;
      }

      void close() {
         executor.shutdownNow();
      }

      private final UUID nodeId;
      private final ThreadPoolExecutor executor;
      private final AtomicBoolean overflowLogged = new AtomicBoolean();
      private volatile long skipUntil;
   }

   private final Ignite ignite;
   private final String topic;
   private final long skipMillis;
   private final Map<UUID, ClientQueue> queues = new ConcurrentHashMap<>();
   private volatile boolean closed;

   static final int MAX_QUEUED_MESSAGES = 1000;
   private static final Logger LOG = LoggerFactory.getLogger(ClientMessageSender.class);
}
