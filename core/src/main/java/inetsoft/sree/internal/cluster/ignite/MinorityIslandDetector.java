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

import inetsoft.util.config.*;
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
import org.apache.ignite.spi.discovery.tcp.ipfinder.multicast.TcpDiscoveryMulticastIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * topology that shares no node with the local one is a foreign cluster, but only if it is a
 * cluster of this deployment: every one of its nodes must publish the same deployment
 * fingerprint as this node (the configured discovery base port and IP finder, see
 * {@link #getDeploymentFingerprint}), and one of them must own the probed host. The thin client
 * base port is shared by every StyleBI deployment on a host, so without this check another
 * deployment on a shared host could answer at a probed port. Nodes of an older version publish
 * no fingerprint and never count.
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

      try {
         // a round in progress is bounded by the connect and thin client timeouts
         executor.awaitTermination(2L * CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   private void checkQuietly() {
      try {
         check();
      }
      catch(Throwable e) {
         // never let an error end the periodic task: scheduleWithFixedDelay stops for good
         // after the first exception it sees
         LOG.debug("Cluster island check failed", e);
      }
   }

   /**
    * Runs one check. Called only on the detector thread, or directly by tests.
    */
   void check() {
      Island local = Island.of(ignite.cluster().forServers().nodes());
      ClusterNode localNode = ignite.cluster().localNode();
      UUID localId = localNode.id();
      Object fingerprint = localNode.attribute(DEPLOYMENT_ATTR);
      Island better = null;
      boolean conclusive = true;

      for(Map.Entry<String, InetAddress> endpoint : getThinClientEndpoints().entrySet()) {
         Collection<ClusterNode> nodes = fetchServerTopology(endpoint.getKey());

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

         logOtherDeployment(endpoint, nodes, fingerprint);

         // only joined nodes of a cluster of this deployment count, and one of them must be
         // on the probed host
         List<ClusterNode> joined = nodes.stream()
            .filter(n -> n.order() > 0 && n.attribute(LOCAL_IP_ATTR) != null)
            .filter(n -> fingerprint != null && fingerprint.equals(n.attribute(DEPLOYMENT_ATTR)))
            .toList();

         if(joined.isEmpty() || joined.size() != nodes.size() ||
            joined.stream().noneMatch(n -> ownsHost(n, endpoint.getValue())))
         {
            continue;
         }

         Island foreign = Island.of(joined);

         if(foreign.beats(local) && (better == null || foreign.beats(better))) {
            better = foreign;
         }
      }

      update(local, better, conclusive, System.currentTimeMillis());
   }

   /**
    * Logs once per address and fingerprint that a cluster answering at a probed address is
    * ignored because it is configured as another deployment, so that an operator can tell a
    * deployment whose nodes are configured differently from one with no islands.
    */
   private void logOtherDeployment(Map.Entry<String, InetAddress> endpoint,
                                   Collection<ClusterNode> nodes, Object fingerprint)
   {
      for(ClusterNode node : nodes) {
         Object other = node.attribute(DEPLOYMENT_ATTR);

         if(other != null && !other.equals(fingerprint) && ownsHost(node, endpoint.getValue())) {
            if(loggedOtherDeployments.size() < 100 &&
               loggedOtherDeployments.add(endpoint.getKey() + "|" + other))
            {
               LOG.info("The cluster that answers at {} has different discovery settings " +
                           "(base port or IP finder) and is treated as another deployment, " +
                           "not as a cluster island of this one", endpoint.getKey());
            }

            return;
         }
      }
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
               haltPending = InetsoftConfig.getInstance().getCluster().isMinorityIslandHalt();
            }
         }
         else {
            minorityMessage = getMessage(local, better);
         }

         if(haltPending) {
            // retried on the next rounds if raising the failure itself fails
            haltPending = !halt();
         }
      }
      else {
         betterSince = -1L;
         betterRounds = 0;

         // leaves after MIN_CLEAR_ROUNDS conclusive rounds without a better cluster; an
         // inconclusive round in between neither counts nor resets them
         if(minorityMessage != null && conclusive && ++clearRounds >= MIN_CLEAR_ROUNDS) {
            minorityMessage = null;
            clearRounds = 0;
            haltPending = false;
            LOG.warn("This node is no longer in a minority cluster island: no larger cluster " +
                        "of this deployment answers any more");
         }
      }
   }

   /**
    * Raises a segmentation failure, so that Ignite's failure handler halts this node.
    *
    * @return {@code true} if the failure was raised, {@code false} if it should be retried.
    */
   private boolean halt() {
      LOG.error("cluster.minorityIslandHalt is enabled: raising a segmentation failure so " +
                   "that this node stops and is restarted into the larger cluster");

      try {
         ((IgniteEx) ignite).context().failure().process(new FailureContext(
            FailureType.SEGMENTATION, new IgniteException(minorityMessage)));
         return true;
      }
      catch(Throwable e) {
         LOG.error("Failed to raise the segmentation failure, retrying on the next check", e);
         return false;
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
   private Map<String, InetAddress> getThinClientEndpoints() {
      DiscoverySpi spi = ignite.configuration().getDiscoverySpi();
      ClientConnectorConfiguration connector =
         ignite.configuration().getClientConnectorConfiguration();

      if(!(spi instanceof TcpDiscoverySpi discoverySpi) || connector == null) {
         return Collections.emptyMap();
      }

      Set<InetSocketAddress> owned = new HashSet<>();
      // the addresses an AddressResolver maps a node to (NAT), as TcpDiscoverySpi publishes them
      String extAddressesAttr = discoverySpi.getName() + "." + TcpDiscoverySpi.ATTR_EXT_ADDRS;

      for(ClusterNode node : ignite.cluster().nodes()) {
         if(node instanceof TcpDiscoveryNode tcpNode && tcpNode.discoveryPort() > 0) {
            owned.addAll(tcpNode.socketAddresses());

            for(String address : tcpNode.addresses()) {
               owned.add(resolve(new InetSocketAddress(address, tcpNode.discoveryPort())));
            }

            if(tcpNode.attribute(extAddressesAttr) instanceof Collection<?> extAddresses) {
               for(Object address : extAddresses) {
                  if(address instanceof InetSocketAddress socketAddress) {
                     owned.add(resolve(socketAddress));
                  }
               }
            }
         }
      }

      int basePort = getConfiguredPort(discoverySpi);
      int portRange = Math.max(0, discoverySpi.getLocalPortRange());
      Map<String, InetAddress> endpoints = new LinkedHashMap<>();

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
         endpoints.put(hostName + ":" + (connector.getPort() + offset), host);
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

   /**
    * Checks if a node has the probed host among its addresses, so that a node that answers at
    * a thin client port is one that the probed IP finder address can belong to.
    */
   private static boolean ownsHost(ClusterNode node, InetAddress host) {
      for(String address : node.addresses()) {
         try {
            if(InetAddress.getByName(address).equals(host)) {
               return true;
            }
         }
         catch(UnknownHostException ignore) {
            // not this one
         }
      }

      return false;
   }

   /**
    * Gets the fingerprint of the deployment a server node belongs to: a hash of its configured
    * discovery base port and of the kind of IP finder and what it looks up (multicast group,
    * Kubernetes label, cloud bucket, load balancer or storage account). Nodes of one deployment
    * are configured the same, and two deployments that agreed on both would have joined one
    * cluster at start, so a different fingerprint means a different deployment. It is a hash so
    * that the node attribute doesn't show the finder settings.
    *
    * @return the fingerprint, or {@code null} if discovery isn't TCP discovery.
    */
   static String getDeploymentFingerprint(DiscoverySpi spi, ClusterConfig clusterConfig) {
      if(!(spi instanceof TcpDiscoverySpi discoverySpi)) {
         return null;
      }

      StringBuilder text = new StringBuilder("port=").append(getConfiguredPort(discoverySpi));
      TcpDiscoveryIpFinder ipFinder = discoverySpi.getIpFinder();

      if(ipFinder instanceof TcpDiscoveryVmIpFinder) {
         // Not the member list: nodes of one deployment may list their members differently.
         // Two deployments with static members, the same base port and a shared host can't
         // stay apart (each one's host:port entry reaches the other's node, which it joins),
         // and the probed host must belong to the cluster that answers.
         text.append("\nvm");
      }
      else if(ipFinder instanceof TcpDiscoveryMulticastIpFinder multicastFinder) {
         text.append("\nmulticast=").append(multicastFinder.getMulticastGroup())
            .append(':').append(multicastFinder.getMulticastPort());
      }
      else if(ipFinder != null) {
         text.append("\nfinder=").append(ipFinder.getClass().getName());
         KubernetesConfig k8s = clusterConfig == null ? null : clusterConfig.getK8s();
         IpFinderConfig finderConfig = clusterConfig == null ? null : clusterConfig.getIpFinder();

         if(k8s != null) {
            text.append("\nk8s=").append(k8s.getNamespace()).append('|')
               .append(k8s.getLabelName()).append('=').append(k8s.getLabelValue());
         }

         if(finderConfig != null) {
            text.append("\ntype=").append(finderConfig.getType());

            if(finderConfig.getAwsElb() != null) {
               text.append("\nelb=").append(finderConfig.getAwsElb().getRegion()).append('|')
                  .append(finderConfig.getAwsElb().getLoadBalancerName());
            }

            if(finderConfig.getGoogleGcs() != null) {
               text.append("\ngcs=").append(finderConfig.getGoogleGcs().getBucket());
            }

            if(finderConfig.getAzureBlob() != null) {
               // the storage account (never the key) tells deployments that share the
               // container name apart
               text.append("\nazure=").append(finderConfig.getAzureBlob().getEndpoint())
                  .append('|').append(getAzureAccountName(
                     finderConfig.getAzureBlob().getConnectionString()))
                  .append('|').append(finderConfig.getAzureBlob().getContainer());
            }
         }
      }

      try {
         byte[] hash = MessageDigest.getInstance("SHA-256")
            .digest(text.toString().getBytes(StandardCharsets.UTF_8));
         return HexFormat.of().formatHex(hash);
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }

   /**
    * Gets the configured discovery base port. {@link TcpDiscoverySpi#getLocalPort()} returns the
    * port the node actually bound (0 before it starts), which differs from the base on a host
    * with more than one node, and the SPI has no getter for the configured one.
    */
   static int getConfiguredPort(TcpDiscoverySpi spi) {
      try {
         if(LOCAL_PORT_FIELD != null) {
            return LOCAL_PORT_FIELD.getInt(spi);
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to read the configured discovery port", e);
      }

      return spi.getLocalPort();
   }

   private static Field getLocalPortField() {
      try {
         Field field = TcpDiscoverySpi.class.getDeclaredField("locPort");
         field.setAccessible(true);
         return field;
      }
      catch(Exception e) {
         LOG.debug("TcpDiscoverySpi.locPort is not accessible", e);
         return null;
      }
   }

   /**
    * Gets the {@code AccountName} of an Azure storage connection string.
    */
   static String getAzureAccountName(String connectionString) {
      if(connectionString != null) {
         for(String part : connectionString.split(";")) {
            int index = part.indexOf('=');

            if(index > 0 && "AccountName".equalsIgnoreCase(part.substring(0, index).trim())) {
               return part.substring(index + 1).trim();
            }
         }
      }

      return null;
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
   private boolean haltPending; // guarded by this
   private List<InetSocketAddress> registeredAddresses; // detector thread only
   private final Set<String> loggedOtherDeployments = new HashSet<>(); // detector thread only
   private long registeredAddressesTime; // detector thread only

   private static final long DEFAULT_INTERVAL_MILLIS = 10_000L;
   private static final long CONNECT_TIMEOUT_MILLIS = 3_000L;
   private static final long ADDRESS_REFRESH_MILLIS = 60_000L;
   private static final int MIN_ROUNDS = 3;
   private static final int MIN_CLEAR_ROUNDS = 2;
   private static final String LOCAL_IP_ATTR = "local.ip.addr";
   static final String DEPLOYMENT_ATTR = "inetsoft.cluster.deployment";
   private static final Logger LOG = LoggerFactory.getLogger(MinorityIslandDetector.class);
   // after LOG, which getLocalPortField uses
   private static final Field LOCAL_PORT_FIELD = getLocalPortField();
}
