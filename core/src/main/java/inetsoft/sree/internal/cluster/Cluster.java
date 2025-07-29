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
package inetsoft.sree.internal.cluster;

import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.util.SingletonManager;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Class that provides support for the clustering sub-system.
 *
 * @since 12.2
 */
@SingletonManager.Singleton(Cluster.Reference.class)
public interface Cluster extends AutoCloseable {
   /**
    * Gets the singleton cluster instance.
    *
    * @return the cluster instance.
    */
   static Cluster getInstance() {
      return SingletonManager.getInstance(Cluster.class);
   }

   /**
    * Shuts down and disposes of the singleton cluster instance.
    */
   static void clear() {
      SingletonManager.reset(Cluster.class);
   }

   /**
    * Gets the unique identifier for this cluster.
    */
   String getId();

   /**
    * Gets name of the local node.
    *
    * @return name of the local member.
    */
   String getLocalMember();

   /**
    * Gets the nodes of this cluster.
    *
    * @return the cluster nodes.
    */
   default Set<String> getClusterNodes() {
      return getClusterNodes(true);
   }

   /**
    * Gets the nodes of this cluster.
    *
    * @param includeClients {@code true} to include client nodes or {@code false} to exclude them.
    *
    * @return the cluster nodes.
    */
   Set<String> getClusterNodes(boolean includeClients);

   /**
    * Gets the server nodes of this cluster.
    *
    * @return the server cluster nodes.
    */
   Set<String> getServerClusterNodes();

   void debug();

   /**
    * Gets the host name or IP address of a cluster node.
    *
    * @param server the cluster node name.
    *
    * @return the host name or IP address.
    */
   String getClusterNodeHost(String server);

   /**
    * Get the ip address of a host name.
    * @param server host name, e.g. node1.
    * @return ip address or null if not known.
    */
   String getByName(String server);

   /**
    * Gets the named property of a cluster node.
    *
    * @param server the cluster node.
    * @param name   the name of the property.
    *
    * @param <T> the type of the property value.
    *
    * @return the property value.
    */
   <T> T getClusterNodeProperty(String server, String name);

   <T> T getLocalNodeProperty(String name);

   /**
    * Sets the named property of the current cluster node.
    *
    * @param name  the property name.
    * @param value the property value or {@code null} to remove the property.
    */
   void setLocalNodeProperty(String name, Object value);

   /**
    * Check if this node is the master. The oldest node in the cluster is
    * treated as the master.
    */
   boolean isMaster();

   /**
    * Gets the member that owns the partition containing the specified key.
    *
    * @param key the key.
    *
    * @return the owning member.
    */
   String getKeyOwner(String key);

   /**
    * Get a distributed lock.
    * @param name the name of the lock.
    */
   Lock getLock(String name);

   void destroyLock(String name);

   /**
    * Lock the named resource. This lock will be clear when the last lock
    * is removed on the key.
    * @param name the name of the resource.
    */
   void lockKey(String name);

   /**
    * Unlock the named resource.
    * @param name the name of the resource.
    */
   void unlockKey(String name);

   /**
    * Place a read lock on the named resource.
    */
   void lockRead(String name);

   /**
    * Unload a read lock on the named resource.
    */
   void unlockRead(String name);

   /**
    * Place a write lock on the named resource.
    */
   void lockWrite(String name);

   /**
    * Unlock a write lock on the named resource.
    */
   void unlockWrite(String name);

   /**
    * Get a distributed map.
    */
   <K, V> DistributedMap<K, V> getMap(String name);

   <K, V> MultiMap<K, V> getMultiMap(String name);

   void destroyMap(String name);

   void addMapListener(String name, MapChangeListener<?, ?> l);

   void removeMapListener(String name, MapChangeListener<?, ?> l);

   <K, V> void addMultiMapListener(String name, MapChangeListener<K, Collection<V>> l);

   void removeMultiMapListener(String name, MapChangeListener<?, ?> l);

   <K, V> DistributedMap<K, V> getReplicatedMap(String name);

   void destroyReplicatedMap(String name);

   <K, V> void addReplicatedMapListener(String name, MapChangeListener<K, V> l);

   void removeReplicatedMapListener(String name, MapChangeListener<?, ?> l);

   <E> BlockingQueue<E> getQueue(String name);

   void destroyQueue(String name);

   <E> Set<E> getSet(String name);

   void destroySet(String name);

   default <E> Set<E> getReplicatedSet(String name) {
      return getReplicatedSet(name, false);
   }

   <E> Set<E> getReplicatedSet(String name, boolean transactional);

   void destroyReplicatedSet(String name);

   DistributedLong getLong(String name);

   void destroyLong(String name);

   <V> DistributedReference<V> getReference(String name);

   void destroyReference(String name);

   /**
    * Submit a job to the ignite cluster
    */
   <T> Future<T> submit(Callable<T> task, boolean scheduler);

   /**
    * Submit a job to all nodes.
    */
   <T> Future<Collection<T>> submitAll(Callable<T> task);

   /**
    * Gets a scheduled task executor.
    *
    * @return the executor.
    */
   DistributedScheduledExecutorService getScheduledExecutor();

   /**
    * Destroys a scheduled task executor.
    */
   void destroyScheduledExecutor();

   /**
    * Gets the member that owns the service with the specified identifier.
    *
    * @param serviceId the service identifier.
    *
    * @return the owning member or {@code null} if none.
    */
   String getServiceOwner(String serviceId);

   /**
    * Submits a task to be processed. The task will be executed on a single node in the cluster,
    * ensuring split-brain protection.
    *
    * @param serviceId the identifier for the service.
    * @param task      the task to execute.
    *
    * @param <T> the return type of the task.
    *
    * @return the result of the task.
    */
   <T extends Serializable> Future<T> submit(String serviceId, SingletonCallableTask<T> task);

   /**
    * Submits a task to be processed. The task will be executed on a single node in the cluster,
    * ensuring split-brain protection.
    *
    * @param serviceId the identifier for the service.
    * @param task      the task to execute.
    *
    * @return a future that will resolve when the task is complete.
    */
   Future<?> submit(String serviceId, SingletonRunnableTask task);

   /**
    * Check if scheduler is running.
    */
   boolean isSchedulerRunning();

   void addMembershipListener(MembershipListener l);

   void removeMembershipListener(MembershipListener l);

   /**
    * Adds a listener that is notified when a message is received from a cluster
    * node.
    *
    * @param l the listener to add.
    */
   void addMessageListener(MessageListener l);

   /**
    * Removes a listener from the notification list.
    *
    * @param l the listener to remove.
    */
   void removeMessageListener(MessageListener l);

   /**
    * Sends a message to the other nodes in the cluster.
    *
    * @param message the message object.
    *
    * @throws Exception if the message could not be sent.
    */
   void sendMessage(Serializable message) throws Exception;

   /**
    * Sends a message to another node in the cluster.
    *
    * @param server  the address of the cluster node.
    * @param message the message object.
    *
    * @throws Exception if the message could not be sent.
    */
   void sendMessage(String server, Serializable message) throws Exception;

   /**
    * Performs a message exchange between the local node and a remote node.
    *
    * @param address         the address of the remote, recipient node.
    * @param outgoingMessage the message object to send to the recipient.
    * @param matcher         a function that checks if an incoming message is the response to the
    *                        sent message. If it is, it returns the expected output; otherwise it
    *                        returns {@code null}. It is guaranteed that the message was received
    *                        from the recipient at <i>address</i>.
    *
    * @param <T> the return type of the matcher function.
    *
    * @return the result of the matcher function.
    *
    * @throws Exception if an error occurs while sending the outgoing message.
    * @throws InterruptedException if no matching response is received within 30 seconds.
    */
   <T extends Serializable> T exchangeMessages(String address, Serializable outgoingMessage,
                                               Function<MessageEvent, T> matcher) throws Exception;

   /**
    * Specialized version of {@link #exchangeMessages(String, Serializable, Function)} that matches
    * any messages of the specified return type.
    *
    * @param address         the address of the remote, recipient node.
    * @param outgoingMessage the message object to send to the recipient.
    * @param responseType    the expected type of the response message object.
    *
    * @param <T> the type of the response message object.
    *
    * @return the result of the matcher function.
    *
    * @throws Exception if an error occurs while sending the outgoing message.
    * @throws InterruptedException if no matching response is received within 30 seconds.
    */
   <T extends Serializable> T exchangeMessages(String address, Serializable outgoingMessage,
                                               Class<T> responseType) throws Exception;

   /**
    * Refresh cluster config status.
    * Cluster is instantiated during server startup, and Config is constructed in the
    * constructor, so some states may not be up to date or correct.
    * @param start is true if start schedule.
    */
   void refreshConfig(boolean start);

   /**
    * The oldest scheduler node is treated as the master.
    */
   boolean isMasterScheduler();

   /**
    * Adds a listener that is notified of cluster lifecycle events.
    *
    * @param l the listener to add.
    */
   void addClusterLifecycleListener(ClusterLifecycleListener l);

   /**
    * Removes a listener from the notification list.
    *
    * @param l the listeners to remove.
    */
   void removeClusterLifecycleListener(ClusterLifecycleListener l);

   /**
    * Adds a file to be transferred to another node in the cluster. The file should be a temporary
    * file and will be deleted after the transfer is complete.
    *
    * @param file the file to transfer.
    *
    * @return the link to the file that should be supplied to the recipient.
    */
   String addTransferFile(File file);

   /**
    * Gets a file added on another node using {@link #addTransferFile(File)}.
    *
    * @param link the link to the file.
    *
    * @return a temporary file containing the transferred file's contents. This file should be
    *         deleted when it is no longer needed.
    *
    * @throws IOException if an I/O error occurs.
    */
   File getTransferFile(String link) throws IOException;

   /**
    * Executes a SQL query against a cache.
    *
    * @param cache    the name of the cache.
    * @param sql      the SQL query to execute.
    * @param params   the query parameters or {@code null}.
    * @param consumer the row consumer.
    */
   default void queryCache(String cache, String sql, Object[] params, Consumer<List<?>> consumer) {
      queryCache(cache, false, sql, params, consumer);
   }

   /**
    * Executes a SQL query against a cache.
    *
    * @param cache       the name of the cache.
    * @param distributed to perform a distributed query.
    * @param sql         the SQL query to execute.
    * @param params      the query parameters or {@code null}.
    * @param consumer    the row consumer.
    */
   default void queryCache(String cache, boolean distributed, String sql, Object[] params,
                           Consumer<List<?>> consumer)
   {
      throw new UnsupportedOperationException();
   }

   List<String> getClusterAddresses();

   DistributedTransaction startTx();

   final class Reference extends SingletonManager.Reference<Cluster> {
      @Override
      public Cluster get(Object... parameters) {
         lock.lock();

         try {
            if(instance == null) {
               String property = System.getProperty("inetsoft.sree.internal.cluster.implementation");

               if(property != null) {
                  try {
                     instance = (Cluster) Class.forName(property).getConstructor().newInstance();
                  }
                  catch(Exception e) {
                     throw new RuntimeException("Failed to create cluster instance", e);
                  }
               }
               else {
                  instance = new IgniteCluster();
               }
            }
         }
         finally {
            lock.unlock();
         }

         return instance;
      }

      @Override
      public void dispose() {
         lock.lock();

         try {
            if(instance != null) {
               try {
                  instance.close();
               }
               catch(Exception ignore) {
               }
            }
         }
         finally {
            lock.unlock();
         }
      }

      Cluster instance = null;
      private final Lock lock = new ReentrantLock();
   }
}
