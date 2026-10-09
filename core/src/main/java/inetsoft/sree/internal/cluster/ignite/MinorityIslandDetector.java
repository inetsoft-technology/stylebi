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

import inetsoft.util.config.InetsoftConfig;
import org.apache.ignite.*;
import org.apache.ignite.client.ClientRetryNonePolicy;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.ClientConfiguration;
import org.apache.ignite.configuration.ClientConnectorConfiguration;
import org.apache.ignite.failure.FailureContext;
import org.apache.ignite.failure.FailureType;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.spi.discovery.DiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Detects that this server node is in a minority cluster island (Bug #78105).
 * <p>
 * After a discovery split, Ignite 2.x never merges two running topologies, and a node that failed
 * all of its peers first is coordinator of its own cluster and is never segmented. Each island is
 * a valid, active cluster, so nothing in the node itself tells it apart from the main cluster.
 * This detector looks for other clusters of the same deployment: on a daemon thread it takes the
 * addresses in the discovery IP finder that no node of the local topology owns, and asks a node
 * at each of them for its server topology through Ignite's thin client protocol (the discovery
 * handshake only carries the responder's node id and order, not the size of its cluster). A
 * topology that shares no node with the local one is a foreign cluster.
 * <p>
 * The cluster that keeps serving is decided by a rule that every island computes the same way
 * from the same data: more server nodes wins; on a tie, the cluster whose earliest-started member
 * started first; then the smallest node id. A node only counts once it has joined its cluster
 * (order > 0). A node that finds a better cluster on several consecutive checks spanning more than
 * twice the failure detection timeout reports itself as a minority island, which the cluster
 * health check turns into not-ready. If {@code cluster.minorityIslandHalt} is set, it also raises
 * a segmentation failure, so Ignite's failure handler halts the node and the container is
 * restarted into the surviving cluster.
 * <p>
 * Nothing changes when no foreign cluster answers: a peer that is gone (rolling restart,
 * scale-in, a lone first start) refuses the connection, and a node still joining has no order.
 */
final class MinorityIslandDetector implements AutoCloseable {
   MinorityIslandDetector(Ignite ignite, long intervalMillis, long confirmMillis) {
      this.ignite = ignite;
      this.intervalMillis = intervalMillis > 0 ? intervalMillis : DEFAULT_INTERVAL_MILLIS;
      this.confirmMillis = confirmMillis > 0 ? confirmMillis :
         Math.max(2 * ignite.configuration().getFailureDetectionTimeout(), 2 * this.intervalMillis);
      this.connectTimeoutMillis = (int) Math.min(CONNECT_TIMEOUT_MILLIS, this.intervalMillis);
      this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
         Thread thread = new Thread(r, "IgniteIslandDetector");
         thread.setDaemon(true);
         return thread;
      });
   }

   /**
    * Starts the periodic check.
    */
   void start() {
      executor.scheduleWithFixedDelay(
         this::checkQuietly, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
   }

   /**
    * Gets the reason this node is in a minority island, or {@code null} if it is not. Never blocks.
    */
   String getMinorityMessage() {
      return minorityMessage;
   }

   @Override
   public void close() {
      executor.shutdownNow();
   }

   private void checkQuietly() {
      try {
         check();
      }
      catch(Exception e) {
         LOG.debug("Cluster island check failed", e);
      }
   }

   /**
    * Runs one check. Called only on the detector thread, or directly by tests.
    */
   void check() {
      Island local = Island.of(ignite.cluster().forServers().nodes());
      UUID localId = ignite.cluster().localNode().id();
      Island better = null;
      boolean conclusive = true;

      for(String endpoint : getThinClientEndpoints()) {
         Collection<ClusterNode> nodes = fetchServerTopology(endpoint);

         if(nodes == null) {
            continue;
         }

         Set<UUID> ids = nodes.stream().map(ClusterNode::id).collect(Collectors.toSet());

         if(ids.contains(localId)) {
            continue; // our own cluster, reached through an address we didn't match
         }

         if(!Collections.disjoint(ids, local.ids())) {
            // the two views overlap without containing this node: a topology change is in
            // progress, so this round can't tell anything
            conclusive = false;
            continue;
         }

         // only joined nodes of a StyleBI cluster count
         List<ClusterNode> joined = nodes.stream()
            .filter(n -> n.order() > 0 && n.attribute(LOCAL_IP_ATTR) != null)
            .toList();

         if(joined.isEmpty() || joined.size() != nodes.size()) {
            continue;
         }

         Island foreign = Island.of(joined);

         if(foreign.beats(local) && (better == null || foreign.beats(better))) {
            better = foreign;
         }
      }

      update(local, better, conclusive, System.currentTimeMillis());
   }

   private synchronized void update(Island local, Island better, boolean conclusive, long now) {
      if(better != null) {
         clearRounds = 0;

         if(betterSince < 0) {
            betterSince = now;
            betterRounds = 0;
         }

         betterRounds++;

         if(minorityMessage == null) {
            if(betterRounds >= MIN_ROUNDS && now - betterSince >= confirmMillis) {
               minorityMessage = getMessage(local, better);
               LOG.warn("{} Restart this node so that it rejoins the larger cluster.",
                        minorityMessage);
               haltIfEnabled();
            }
         }
         else {
            minorityMessage = getMessage(local, better);
         }
      }
      else {
         betterSince = -1L;
         betterRounds = 0;

         if(minorityMessage != null && conclusive && ++clearRounds >= MIN_CLEAR_ROUNDS) {
            minorityMessage = null;
            clearRounds = 0;
            LOG.warn("This node is no longer in a minority cluster island: no larger cluster " +
                        "of this deployment answers any more");
         }
      }
   }

   private void haltIfEnabled() {
      if(!InetsoftConfig.getInstance().getCluster().isMinorityIslandHalt()) {
         return;
      }

      LOG.error("cluster.minorityIslandHalt is enabled: raising a segmentation failure so " +
                   "that this node stops and is restarted into the larger cluster");

      try {
         ((IgniteEx) ignite).context().failure().process(new FailureContext(
            FailureType.SEGMENTATION, new IgniteException(minorityMessage)));
      }
      catch(Exception e) {
         LOG.error("Failed to raise the segmentation failure", e);
      }
   }

   private static String getMessage(Island local, Island better) {
      return "Node is in a minority cluster island: this cluster has " + local.size() +
         " server node(s) " + local.names() + ", while another cluster of this deployment " +
         "has " + better.size() + " server node(s) " + better.names() + ".";
   }

   /**
    * Gets the thin client endpoints of the discovery addresses that no node of the local
    * topology owns.
    */
   private Set<String> getThinClientEndpoints() {
      DiscoverySpi spi = ignite.configuration().getDiscoverySpi();
      ClientConnectorConfiguration connector =
         ignite.configuration().getClientConnectorConfiguration();

      if(!(spi instanceof TcpDiscoverySpi discoverySpi) || connector == null) {
         return Collections.emptySet();
      }

      Set<InetSocketAddress> owned = new HashSet<>();

      for(ClusterNode node : ignite.cluster().nodes()) {
         if(node instanceof TcpDiscoveryNode tcpNode && tcpNode.discoveryPort() > 0) {
            owned.addAll(tcpNode.socketAddresses());

            for(String address : tcpNode.addresses()) {
               owned.add(resolve(new InetSocketAddress(address, tcpNode.discoveryPort())));
            }
         }
      }

      int basePort = discoverySpi.getLocalPort();
      int portRange = Math.max(0, discoverySpi.getLocalPortRange());
      Set<String> endpoints = new LinkedHashSet<>();

      for(InetSocketAddress address : getRegisteredAddresses(discoverySpi.getIpFinder())) {
         int port = address.getPort() == 0 ? basePort : address.getPort();
         InetSocketAddress resolved = resolve(new InetSocketAddress(
            address.isUnresolved() ? address.getHostString() : address.getAddress().getHostAddress(),
            port));

         if(owned.contains(resolved) || resolved.isUnresolved()) {
            continue;
         }

         // Each JVM on a host binds the first free port of both ranges, so a node's offset
         // in the discovery range is normally its offset in the client connector range.
         int offset = port - basePort;

         if(offset < 0 || offset > portRange || offset > connector.getPortRange()) {
            offset = 0;
         }

         InetAddress host = resolved.getAddress();
         String hostName = host instanceof Inet6Address ?
            "[" + host.getHostAddress() + "]" : host.getHostAddress();
         endpoints.add(hostName + ":" + (connector.getPort() + offset));
      }

      return endpoints;
   }

   private Collection<InetSocketAddress> getRegisteredAddresses(TcpDiscoveryIpFinder ipFinder) {
      if(ipFinder == null) {
         return Collections.emptyList();
      }

      long now = System.currentTimeMillis();

      // an IP finder may call an external service (Kubernetes API, multicast), so don't ask it
      // on every round
      if(registeredAddresses == null || now - registeredAddressesTime > ADDRESS_REFRESH_MILLIS) {
         try {
            registeredAddresses = new ArrayList<>(ipFinder.getRegisteredAddresses());
            registeredAddressesTime = now;
         }
         catch(Exception e) {
            LOG.debug("Failed to get the registered discovery addresses", e);
            return registeredAddresses == null ? Collections.emptyList() : registeredAddresses;
         }
      }

      return registeredAddresses;
   }

   /**
    * Gets the joined server nodes of the cluster of the node listening at a thin client
    * endpoint, or {@code null} if nothing usable answers there.
    */
   private Collection<ClusterNode> fetchServerTopology(String endpoint) {
      int index = endpoint.lastIndexOf(':');
      String host = endpoint.substring(0, index).replace("[", "").replace("]", "");
      int port = Integer.parseInt(endpoint.substring(index + 1));

      // a plain connect first, so that an address with nothing behind it doesn't cost a client
      try(Socket socket = new Socket()) {
         socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
      }
      catch(Exception e) {
         return null;
      }

      ClientConfiguration config = new ClientConfiguration()
         .setAddresses(endpoint)
         .setHandshakeTimeout(connectTimeoutMillis)
         .setRequestTimeout(connectTimeoutMillis)
         .setPartitionAwarenessEnabled(false)
         .setClusterDiscoveryEnabled(false)
         .setHeartbeatEnabled(false)
         .setRetryPolicy(new ClientRetryNonePolicy());

      try(IgniteClient client = Ignition.startClient(config)) {
         return client.cluster().forServers().nodes();
      }
      catch(Exception e) {
         LOG.debug("Failed to get the cluster topology from {}", endpoint, e);
         return null;
      }
   }

   private static InetSocketAddress resolve(InetSocketAddress address) {
      if(address.isUnresolved()) {
         try {
            return new InetSocketAddress(
               InetAddress.getByName(address.getHostString()), address.getPort());
         }
         catch(UnknownHostException ignore) {
            // keep it unresolved
         }
      }

      return address;
   }

   /**
    * The server nodes of one cluster, and what the rule that picks the surviving cluster needs
    * to know about them.
    */
   record Island(Set<UUID> ids, List<String> names, long earliestStart, UUID smallestId) {
      static Island of(Collection<ClusterNode> nodes) {
         Set<UUID> ids = new HashSet<>();
         List<String> names = new ArrayList<>();
         long earliestStart = Long.MAX_VALUE;
         UUID smallestId = null;

         for(ClusterNode node : nodes) {
            ids.add(node.id());
            names.add(node.attribute(LOCAL_IP_ATTR) + "/" + node.id());
            Object start = node.attribute(IgniteCluster.START_TIME_ATTR);

            if(start instanceof Number number) {
               earliestStart = Math.min(earliestStart, number.longValue());
            }

            if(smallestId == null || node.id().compareTo(smallestId) < 0) {
               smallestId = node.id();
            }
         }

         Collections.sort(names);
         return new Island(ids, names, earliestStart, smallestId);
      }

      int size() {
         return ids.size();
      }

      /**
       * Checks if this cluster keeps serving rather than the other one. Both clusters compute
       * the same answer, because it only depends on node ids and on attributes that are fixed
       * when a node starts.
       */
      boolean beats(Island other) {
         if(size() != other.size()) {
            return size() > other.size();
         }

         if(earliestStart != other.earliestStart) {
            return earliestStart < other.earliestStart;
         }

         if(smallestId == null || other.smallestId == null) {
            return false;
         }

         return smallestId.compareTo(other.smallestId) < 0;
      }
   }

   private final Ignite ignite;
   private final long intervalMillis;
   private final long confirmMillis;
   private final int connectTimeoutMillis;
   private final ScheduledExecutorService executor;
   private volatile String minorityMessage;
   private long betterSince = -1L; // guarded by this
   private int betterRounds; // guarded by this
   private int clearRounds; // guarded by this
   private List<InetSocketAddress> registeredAddresses; // detector thread only
   private long registeredAddressesTime; // detector thread only

   private static final long DEFAULT_INTERVAL_MILLIS = 10_000L;
   private static final long CONNECT_TIMEOUT_MILLIS = 3_000L;
   private static final long ADDRESS_REFRESH_MILLIS = 60_000L;
   private static final int MIN_ROUNDS = 3;
   private static final int MIN_CLEAR_ROUNDS = 2;
   private static final String LOCAL_IP_ATTR = "local.ip.addr";
   private static final Logger LOG = LoggerFactory.getLogger(MinorityIslandDetector.class);
}
