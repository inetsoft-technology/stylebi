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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.AuthenticationService;
import inetsoft.util.*;
import inetsoft.util.config.*;
import org.apache.ignite.*;
import org.apache.ignite.cache.*;
import org.apache.ignite.cache.affinity.Affinity;
import org.apache.ignite.cache.query.FieldsQueryCursor;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.*;
import org.apache.ignite.events.*;
import org.apache.ignite.internal.NodeStoppingException;
import org.apache.ignite.lang.*;
import org.apache.ignite.services.ServiceConfiguration;
import org.apache.ignite.services.ServiceDescriptor;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.multicast.TcpDiscoveryMulticastIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cache.Cache;
import javax.cache.expiry.ExpiryPolicy;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.function.*;
import java.util.stream.Collectors;

@SingletonManager.ShutdownOrder(after = AuthenticationService.class)
public final class IgniteCluster implements inetsoft.sree.internal.cluster.Cluster {
   /**
    * Creates a new instance of <tt>Cluster</tt>.
    */
   public IgniteCluster() {
      this(getDefaultConfig(null));
   }

   IgniteCluster(IgniteConfiguration config) {
      System.out.format("Joining cluster on %s/24%n", Tool.getIP());
      ignite = createIgniteInstance(config);
      ignite.message().localListen(MESSAGE_TOPIC, new MessageDispatcher());
      ignite.message().localListen(AFFINITY_TOPIC, new AffinityCallProcessor());
      ignite.events().localListen(new MembershipDispatcher(), EventType.EVT_NODE_JOINED, EventType.EVT_NODE_LEFT);
      clusterFileTransfer = new ClusterFileTransfer();
      messageExecutor = Executors.newFixedThreadPool(
         Runtime.getRuntime().availableProcessors(),
         r -> new GroupedThread(r, "IgniteMessages"));
      affinityExecutor = Executors.newFixedThreadPool(
         Runtime.getRuntime().availableProcessors(),
         r -> new GroupedThread(r, "IgniteMessages"));

      if(!config.isClientMode()) {
         initLockTimer();
         ignite.getOrCreateCache(getCacheConfiguration(RW_MAP_NAME));
      }
   }

   public static IgniteConfiguration getDefaultConfig(Path workDir) {
      ClusterConfig clusterConfig = InetsoftConfig.getInstance().getCluster();
      IgniteConfiguration config = new IgniteConfiguration();
      config.setMetricsLogFrequency(0);
      config.setPeerClassLoadingEnabled(true);

      // atomic data structures like distributed long
      AtomicConfiguration atomicConfiguration = new AtomicConfiguration();
      atomicConfiguration.setBackups(getDefaultBackupCount());
      atomicConfiguration.setCacheMode(getDefaultCacheMode());
      config.setAtomicConfiguration(atomicConfiguration);

      boolean clientMode = clusterConfig.isClientMode();
      config.setClientMode(clientMode);

      if(workDir == null) {
         String workDirProp = System.getProperty("inetsoftClusterDir");

         if(workDirProp == null) {
            workDirProp = ConfigurationContext.getContext().getHome();
            workDir = Paths.get(workDirProp).resolve("cluster").toAbsolutePath();
         }
         else {
            workDir = Paths.get(workDirProp).toAbsolutePath();
         }
      }

      config.setWorkDirectory(workDir.toString());
      SslContextFactory sslContextFactory = createSslContextFactory(clusterConfig);

      if(clientMode) {
         TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
         config.setDiscoverySpi(discoverySpi);

         if(clusterConfig.getIpFinder() != null) {
            discoverySpi.setIpFinder(getIpFinder(clusterConfig.getIpFinder().getType()));
         }
         else if(clusterConfig.isMulticastEnabled()) {
            TcpDiscoveryMulticastIpFinder ipFinder = new TcpDiscoveryMulticastIpFinder();
            ipFinder.setMulticastGroup(clusterConfig.getMulticastAddress());
            ipFinder.setMulticastPort(clusterConfig.getMulticastPort());
            discoverySpi.setIpFinder(ipFinder);
         }
         else if(clusterConfig.isTcpEnabled()) {
            TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
            ipFinder.setAddresses(clusterConfig.getTcpMembers() == null ?
                                     Collections.emptyList() :
                                     Arrays.asList(clusterConfig.getTcpMembers()));
            discoverySpi.setIpFinder(ipFinder);
         }

         if(sslContextFactory != null) {
            ClientConnectorConfiguration clientConnectorConfig = new ClientConnectorConfiguration();
            clientConnectorConfig.setSslEnabled(true);
            clientConnectorConfig.setUseIgniteSslContextFactory(false);
            clientConnectorConfig.setSslContextFactory(sslContextFactory);
         }

         TcpCommunicationSpi communicationSpi = new TcpCommunicationSpi();
         communicationSpi.setForceClientToServerConnections(true);
         config.setCommunicationSpi(communicationSpi);
      }
      else {
         int[] includedEvents = new int[]{
            EventType.EVT_CACHE_OBJECT_PUT,
            EventType.EVT_CACHE_OBJECT_REMOVED,
            EventType.EVT_CACHE_OBJECT_EXPIRED,
            EventType.EVT_CACHE_ENTRY_EVICTED,
            EventType.EVT_CACHE_REBALANCE_STOPPED,
            EventType.EVT_NODE_JOINED,
            EventType.EVT_NODE_LEFT
         };

         config.setIncludeEventTypes(includedEvents);

         setLocalNodeAttributes(config);

         String localIP = Tool.getIP();
         AddressResolver addressResolver = getAddressResolver(localIP, clusterConfig);
         config.setAddressResolver(addressResolver);

         TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
         discoverySpi.setAddressResolver(addressResolver);
         discoverySpi.setLocalAddress(localIP);
         discoverySpi.setLocalPort(clusterConfig.getPortNumber());
         discoverySpi.setLocalPortRange(100);
         config.setDiscoverySpi(discoverySpi);

         TcpCommunicationSpi tcpCommunicationSpi = new TcpCommunicationSpi();
         tcpCommunicationSpi.setAddressResolver(addressResolver);
         tcpCommunicationSpi.setLocalAddress(localIP);

         if(clusterConfig.getOutboundPortNumber() != 0){
            tcpCommunicationSpi.setLocalPort(clusterConfig.getOutboundPortNumber());
         }

         config.setCommunicationSpi(tcpCommunicationSpi);

         if(clusterConfig.getK8s() != null &&
            clusterConfig.getK8s().getLabelName() != null &&
            !clusterConfig.getK8s().getLabelName().isEmpty() &&
            clusterConfig.getK8s().getLabelValue() != null &&
            !clusterConfig.getK8s().getLabelValue().isEmpty())
         {
            discoverySpi.setIpFinder(getIpFinder("kubernetes"));
         }
         else {
            if(clusterConfig.getIpFinder() != null) {
               discoverySpi.setIpFinder(getIpFinder(clusterConfig.getIpFinder().getType()));
            }
            else if(clusterConfig.isSingleNode()) {
               discoverySpi = new TcpDiscoverySpi();
               TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
               ipFinder.setAddresses(clusterConfig.getTcpMembers() == null ?
                                        Collections.emptyList() :
                                        Arrays.asList(clusterConfig.getTcpMembers()));
               discoverySpi.setIpFinder(ipFinder);
               config.setDiscoverySpi(discoverySpi);
            }
            else if(clusterConfig.isMulticastEnabled()) {
               TcpDiscoveryMulticastIpFinder ipFinder = new TcpDiscoveryMulticastIpFinder();
               ipFinder.setMulticastGroup(clusterConfig.getMulticastAddress());
               ipFinder.setMulticastPort(clusterConfig.getMulticastPort());
               discoverySpi.setIpFinder(ipFinder);
            }
            else if(clusterConfig.isTcpEnabled()) {
               TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
               ipFinder.setAddresses(clusterConfig.getTcpMembers() == null ?
                                        Collections.emptyList() :
                                        Arrays.asList(clusterConfig.getTcpMembers()));
               discoverySpi.setIpFinder(ipFinder);
            }
         }

         if(sslContextFactory != null) {
            config.setSslContextFactory(sslContextFactory);
         }

//         config.setMetricExporterSpi(new LogExporterSpi());
      }

      ExecutorConfiguration[] executePools = new ExecutorConfiguration[IGNITE_EXECUTE_POOL_COUNT];

      for(int i = 0; i < IGNITE_EXECUTE_POOL_COUNT; i++) {
         executePools[i] = new ExecutorConfiguration();
         executePools[i].setName(getIgniteExecutePoolName(i));
      }

      config.setExecutorConfiguration(executePools);
      SUtil.configBinaryTypes(config);

      return config;
   }

   private static String getIgniteExecutePoolName(int level) {
      return IGNITE_EXECUTE_POOL + level;
   }

   private static TcpDiscoveryIpFinder getIpFinder(String type) {
      LOG.warn("get TcpDiscoveryIpFinder finder with: {}", type);

      if(type == null) {
         return null;
      }

      try {
         for(DiscoveryFinderFactory factory : ServiceLoader.load(DiscoveryFinderFactory.class)) {
            if(type.equals(factory.getName())) {
               return factory.create(InetsoftConfig.getInstance().getCluster());
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to get the DiscoveryFinderFactory", ex);
      }

      LOG.error("Failed to get the DiscoveryFinderFactory with type:" + type);

      return null;
   }

   private static SslContextFactory createSslContextFactory(ClusterConfig clusterConfig) {
      SSLCertificateHelper helper = PasswordEncryption.newInstance().getSSLCertificateHelper();
      PrivateKey privateKey = getCAPrivateKey(clusterConfig, helper);
      X509Certificate certificate = getCACertificate(clusterConfig, helper);

      File keyStoreFile = new File(ConfigurationContext.getContext().getHome(), "node.jks")
         .getAbsoluteFile();
      File trustStoreFile = new File(ConfigurationContext.getContext().getHome(), "trust.jks")
         .getAbsoluteFile();

      if(privateKey != null && certificate != null &&
         (!keyStoreFile.exists() || !trustStoreFile.exists()))
      {
         char[] password = clusterConfig.getCaKeyPassword().toCharArray();
         SSLCertificateHelper.CertificateAndKey nodeCertificate = null;

         try {
            nodeCertificate =
               helper.generateCertificate(certificate, privateKey, Tool.getIP(), password);
         }
         catch(Exception e) {
            LOG.error("Failed to generate node SSL certificate", e);
         }

         if(nodeCertificate != null) {
            try {
               helper.saveKeyStore(
                  keyStoreFile.getAbsolutePath(), nodeCertificate.certificate(),
                  nodeCertificate.privateKey(), certificate, password);
            }
            catch(Exception e) {
               LOG.error("Failed to save node key store", e);
            }

            try {
               helper.saveKeyStore(trustStoreFile.getAbsolutePath(), certificate, password);
            }
            catch(Exception e) {
               LOG.error("Failed to save trust store", e);
            }
         }
      }

      if(privateKey != null && certificate != null &&
         keyStoreFile.exists() && trustStoreFile.exists())
      {
         SslContextFactory factory = new SslContextFactory();
         char[] password = clusterConfig.getCaKeyPassword().toCharArray();
         factory.setKeyStoreFilePath(keyStoreFile.getAbsolutePath());
         factory.setKeyStoreFilePath(keyStoreFile.getAbsolutePath());
         factory.setKeyStorePassword(password);
         factory.setTrustStoreFilePath(trustStoreFile.getAbsolutePath());
         factory.setTrustStorePassword(password);
         return factory;
      }

      return null;
   }

   private static PrivateKey getCAPrivateKey(ClusterConfig clusterConfig,
                                             SSLCertificateHelper helper)
   {
      if(clusterConfig.getCaKeyPassword() != null) {
         char[] password = clusterConfig.getCaKeyPassword().toCharArray();

         try {
            if(clusterConfig.getCaKey() != null) {
               return helper.loadPrivateKeyPEM(clusterConfig.getCaKey(), password);
            }
            else if(clusterConfig.getCaKeyFile() != null) {
               return helper.loadPrivateKeyFile(clusterConfig.getCaKeyFile(), password);
            }
         }
         catch(Exception e) {
            LOG.error("Failed to load CA private key", e);
         }
      }

      return null;
   }

   private static X509Certificate getCACertificate(ClusterConfig clusterConfig,
                                                   SSLCertificateHelper helper)
   {
      try {
         if(clusterConfig.getCaCertificate() != null) {
            return helper.loadCertificatePEM(clusterConfig.getCaCertificate());
         }
         else if(clusterConfig.getCaCertificateFile() != null) {
            return helper.loadCertificateFile(clusterConfig.getCaCertificateFile());
         }
      }
      catch(Exception e) {
         LOG.error("Failed to load CA certificate", e);
      }

      return null;
   }

   private static AddressResolver getAddressResolver(String localIP, ClusterConfig clusterConfig) {
      String hostIp = System.getProperty("inetsoft.host.ip");

      if(Tool.isEmptyString(hostIp)) {
         return null;
      }

      String hostPort = System.getProperty("inetsoft.host.port");
      String hostOutboundPort = System.getProperty("inetsoft.host.outbound.port");

      Map<String, String> map = new HashMap<>();
      map.put(localIP, hostIp);

      if(!Tool.isEmptyString(hostPort)) {
         map.put(localIP + ":" + clusterConfig.getPortNumber(), hostIp + ":" + hostPort);
      }

      if(!Tool.isEmptyString(hostOutboundPort)) {
         int localOutboundPort = clusterConfig.getOutboundPortNumber();

         if(localOutboundPort == 0) {
            localOutboundPort = TcpCommunicationSpi.DFLT_PORT;
         }

         map.put(localIP + ":" + localOutboundPort, hostIp + ":" + hostOutboundPort);
      }

      AddressResolver addressResolver = null;

      try {
         addressResolver = new BasicAddressResolver(map);
      }
      catch(UnknownHostException e) {
         LOG.error("Failed to create an address resolver", e);
      }

      return addressResolver;
   }

   private static <K, V> CacheConfiguration<K, V> getCacheConfiguration(String name) {
      return getCacheConfiguration(name, getDefaultCacheMode(), getDefaultBackupCount());
   }

   private static <K, V> CacheConfiguration<K, V> getCacheConfiguration(String name, CacheMode mode,
                                                                       int backupCount)
   {
      CacheConfiguration<K, V> cacheConfiguration = new CacheConfiguration<>(name);
      cacheConfiguration.setBackups(backupCount);
      cacheConfiguration.setCacheMode(mode);
      cacheConfiguration.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
      cacheConfiguration.setRebalanceMode(CacheRebalanceMode.SYNC);
      cacheConfiguration.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
      return cacheConfiguration;
   }

   private static int getDefaultBackupCount() {
      ClusterConfig config = InetsoftConfig.getInstance().getCluster();
      int backupCount = DEFAULT_BACKUP_COUNT;

      if(config.getMinNodes() > DEFAULT_BACKUP_COUNT) {
         backupCount = (config.getMinNodes() / 2) + 1;
      }

      return backupCount;
   }

   private static CacheMode getDefaultCacheMode() {
      ClusterConfig config = InetsoftConfig.getInstance().getCluster();
      return config.getMinNodes() > DEFAULT_BACKUP_COUNT ?
         CacheMode.PARTITIONED : CacheMode.REPLICATED;
   }

   private void initLockTimer() {
      timer.schedule(new TimerTask() {
         @Override
         public void run() {
            final long currentTime = System.currentTimeMillis();

            for(Map.Entry<String, LockInfo> entry : lockInfos.entrySet()) {
               final long lockDuration = currentTime - entry.getValue().lockTime;

               if(lockDuration > MIN_LOCK_DURATION_MILLIS) {
                  LOG.warn("Key {} has been locked for {} minutes. {}", entry.getKey(),
                           Duration.ofMillis(lockDuration).toMinutes(), entry.getValue());
               }
            }
         }
      }, MIN_LOCK_DURATION_MILLIS, MIN_LOCK_DURATION_MILLIS);
   }

   @Override
   public String getId() {
      return ignite.cluster().id().toString();
   }

   @Override
   public void refreshConfig(boolean start) {
      // can't be changed at runtime
   }

   private static void setLocalNodeAttributes(IgniteConfiguration config) {
      Map<String, Object> attrMap = new HashMap<>();
      attrMap.put("scheduler", "true".equals(System.getProperty("ScheduleServer")));
      attrMap.put("cloudRunner", System.getProperty("ScheduleTaskRunner") != null);
      attrMap.put("local.ip.addr", Tool.getIP());
      attrMap.put("inetsoft.host.ip", System.getProperty("inetsoft.host.ip"));
      attrMap.put("inetsoft.host.port", System.getProperty("inetsoft.host.port"));
      attrMap.put("inetsoft.host.outbound.port", System.getProperty("inetsoft.host.outbound.port"));
      config.setUserAttributes(attrMap);
   }

   @Override
   public String getLocalMember() {
      return getNodeName(ignite.cluster().localNode());
   }

   /**
    * Gets the name of the specified member node.
    *
    * @param node the node.
    * @return the node name.
    */
   private String getNodeName(ClusterNode node) {
      int port = 0;

      if(node instanceof TcpDiscoveryNode) {
         port = ((TcpDiscoveryNode) node).discoveryPort();
      }

      String host = node.attribute("local.ip.addr");
      return host + ":" + port;
   }

   @Override
   public Set<String> getClusterNodes(boolean includeClients) {
      return ignite.cluster().nodes().stream()
         .filter(n -> includeClients || !n.isClient())
         .map(this::getNodeName)
         .collect(Collectors.toSet());
   }

   @Override
   public Set<String> getServerClusterNodes() {
      return ignite.cluster()
         .forPredicate((node) -> !Boolean.TRUE.equals(node.attribute("scheduler")))
         .nodes()
         .stream()
         .map(this::getNodeName)
         .collect(Collectors.toSet());
   }

   @Override
   public void debug() {
      for(ClusterNode node : ignite.cluster().nodes()) {
         System.err.println(node);

         for(Map.Entry<String, Object> entry : node.attributes().entrySet()) {
            System.err.println("\t" + entry.getKey() + "=" + entry.getValue());
         }
      }

      printServiceInfo();
   }

   private void printServiceInfo() {
      Collection<ServiceDescriptor> services = ignite.services().serviceDescriptors();
      LinkedHashMap<String, String> serviceMap = new LinkedHashMap<>();

      for(ServiceDescriptor service : services) {
         String nodeIds = service.topologySnapshot().keySet().stream()
            .map((id) -> getNodeName(ignite.cluster().node(id)))
            .collect(Collectors.joining(", "));
         serviceMap.put(service.name(), nodeIds);
      }

      System.err.println("Ignite Services");

      for(String serviceId : serviceMap.keySet()) {
         System.err.println(serviceId + ": " + serviceMap.get(serviceId));
      }
   }

   @Override
   public String getClusterNodeHost(String server) {
      int index = server.lastIndexOf(':');
      return index < 0 ? server : server.substring(0, index);
   }

   @Override
   public String getByName(String server) {
      for(ClusterNode node : ignite.cluster().nodes()) {
         if(Objects.equals(getNodeName(node), server)) {
            return node.hostNames().stream().findFirst().orElse(null);
         }
      }

      return null;
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T> T getClusterNodeProperty(String server, String name) {
      T value = null;

      for(ClusterNode node : ignite.cluster().nodes()) {
         if(getNodeName(node).equals(server)) {
            value = (T) node.attributes().get(name);
            break;
         }
      }

      if(value == null) {
         value = (T) getNodePropertyMap().get(getNodePropertyKey(server, name));
      }

      return value;
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T> T getLocalNodeProperty(String name) {
      T value = (T) ignite.cluster().localNode().attributes().get(name);

      if(value == null) {
         value = (T) getNodePropertyMap().get(getNodePropertyKey(getLocalMember(), name));
      }

      return value;
   }

   @Override
   public void setLocalNodeProperty(String name, Object value) {
      getNodePropertyMap().put(getLocalMember() + ":" + name, value);
   }

   private Map<String, Object> getNodePropertyMap() {
      return getMap(getClass().getName() + ".nodeProperties");
   }

   private String getNodePropertyKey(String node, String key) {
      return node + ":" + key;
   }

   @Override
   public boolean isMaster() {
      ClusterNode self = ignite.cluster().localNode();
      return self.equals(ignite.cluster().nodes().iterator().next());
   }

   @Override
   public String getKeyOwner(String key) {
      return null;
   }

   @Override
   public Lock getLock(String name) {
      return ignite.reentrantLock(name, true, false, true);
   }

   @Override
   public void destroyLock(String name) {
      try(IgniteLock lock = ignite.reentrantLock(name, true, false, false)) {
         if(lock != null) {
            lock.unlock();
         }
      }
   }

   public void unlockLock(String name) {
      IgniteLock lock = ignite.reentrantLock(name, true, false, false);

      if(lock != null) {
         lock.unlock();
      }
   }

   @SuppressWarnings("ResultOfMethodCallIgnored")
   @Override
   public void lockKey(String name) {
      if(LOG.isDebugEnabled()) {
         LOG.debug("Thread {} is locking key \"{}\"", Thread.currentThread().threadId(), name);
      }

      try {
         Lock lock = getLock(name);
         lock.tryLock(10, TimeUnit.MINUTES);
      }
      catch(InterruptedException e) {
         throw new RuntimeException(e);
      }

      // Expeditors has been having issues with their report lib locking preventing node startup.
      if(name.contains("stylereport.srl.d")) {
         recordLockKey(name);
      }
   }

   private void recordLockKey(String name) {
      lockInfos.put(name, new LockInfo());
   }

   @Override
   public void unlockKey(String name) {
      if(LOG.isDebugEnabled()) {
         LOG.debug("Thread {} is unlocking key \"{}\"", Thread.currentThread().threadId(), name);
      }

      recordUnlockKey(name);
      unlockLock(name);
   }

   private void recordUnlockKey(String name) {
      final LockInfo lockInfo = lockInfos.remove(name);

      if(lockInfo != null) {
         final long threadId = Thread.currentThread().threadId();

         if(lockInfo.threadId != threadId) {
            LOG.warn("Unlocking lock \"{}\" from thread {}.", lockInfo, threadId,
                     new Exception("Current stack trace"));
         }
      }
   }

   @Override
   public void lockRead(String name) {
      IgniteCache<String, Object> map = ignite.cache(RW_MAP_NAME);
      String writeKeyName = "write." + name;
      String readKeyName = "read." + name;

      // lock on write
      Lock lock = getLock(writeKeyName);
      lock.lock();

      Integer cnt = (Integer) map.get(readKeyName);

      if(cnt == null) {
         cnt = 1;
      }
      else {
         cnt = cnt + 1;
      }

      map.put(readKeyName, cnt);
      unlockLock(writeKeyName);
   }

   @Override
   public void unlockRead(String name) {
      IgniteCache<String, Object> map = ignite.cache(RW_MAP_NAME);
      String writeKeyName = "write." + name;
      String readKeyName = "read." + name;

      // lock on write
      Lock lock = getLock(writeKeyName);
      lock.lock();

      Integer cnt = (Integer) map.get(readKeyName);

      if(cnt != null) {
         if(cnt <= 1) {
            map.remove(readKeyName);
         }
         else {
            cnt = cnt - 1;
            map.put(readKeyName, cnt);
         }
      }

      unlockLock(writeKeyName);
   }

   @SuppressWarnings("BusyWait")
   @Override
   public void lockWrite(String name) {
      // this implementation is not very efficient and fair. but since the
      // read/write lock is only used for MV creation, this is not a problem
      // since it's not very frequent. we may want to change to a more
      // robust implementation in the future if it's used in a more intense
      // execution scenario
      // consider replacing with thoughtwire implementation:
      // https://github.com/ThoughtWire/hazelcast-locks

      IgniteCache<String, Object> map = ignite.cache(RW_MAP_NAME);
      String writeKeyName = "write." + name;
      String readKeyName = "read." + name;

      // lock on write
      Lock lock = getLock(writeKeyName);
      lock.lock();

      while(map.containsKey(readKeyName)) {
         lock.unlock();

         try {
            Thread.sleep(100);
         }
         catch(Exception ignore) {
         }

         lock.lock();
      }
   }

   @Override
   public void unlockWrite(String name) {
      // lock on write
      unlockLock("write." + name);
   }

   @Override
   public <K, V> DistributedMap<K, V> getMap(String name) {
      return new IgniteDistributedMap<>(ignite.getOrCreateCache(
         getCacheConfiguration(name)));
   }


   public <K, V> IgniteMultiMap<K, V> getMultiMap(String name) {
      CacheConfiguration<K, Collection<V>> cacheConfiguration = getCacheConfiguration(name);
      return new IgniteMultiMap<>(ignite.getOrCreateCache(cacheConfiguration));
   }

   @Override
   public void destroyMap(String name) {
      ignite.destroyCache(name);
   }

   @Override
   public void addMapListener(String name, MapChangeListener<?, ?> l) {
      UUID id = ignite.events()
         .remoteListen(new MapListenerAdapter<>(l), new MapEventFilter(name),
                       EventType.EVT_CACHE_OBJECT_PUT, EventType.EVT_CACHE_OBJECT_REMOVED);
      mapListeners.put(l, id);
   }

   @Override
   public void removeMapListener(String name, MapChangeListener<?, ?> l) {
      UUID id = mapListeners.remove(l);

      if(id != null) {
         ignite.events(ignite.cluster().forRemotes()).stopRemoteListen(id);
      }
   }

   @Override
   public <K, V> void addMultiMapListener(String name, MapChangeListener<K, Collection<V>> l) {
      UUID id = ignite.events().remoteListen(new MapListenerAdapter<>(l),
                                             new MapEventFilter(name),
                                             EventType.EVT_CACHE_OBJECT_PUT,
                                             EventType.EVT_CACHE_OBJECT_REMOVED);
      multiMapListeners.put(l, id);
   }

   @Override
   public void removeMultiMapListener(String name, MapChangeListener<?, ?> l) {
      UUID id = multiMapListeners.get(l);

      if(id != null) {
         ignite.events().stopRemoteListen(id);
      }
   }

   @Override
   public <K, V> DistributedMap<K, V> getReplicatedMap(String name) {
      // backup count doesn't matter for replicated cache
      return new IgniteDistributedMap<>(ignite.getOrCreateCache(
         getCacheConfiguration(name, CacheMode.REPLICATED, DEFAULT_BACKUP_COUNT)));
   }

   @Override
   public void destroyReplicatedMap(String name) {
      ignite.destroyCache(name);
   }

   @Override
   public <K, V> void addReplicatedMapListener(String name, MapChangeListener<K, V> l) {
      UUID id = ignite.events().remoteListen(new MapListenerAdapter<>(l),
                                             new MapEventFilter(name),
                                             EventType.EVT_CACHE_OBJECT_PUT,
                                             EventType.EVT_CACHE_OBJECT_REMOVED);
      replicatedMapListeners.put(l, id);
   }

   @Override
   public void removeReplicatedMapListener(String name, MapChangeListener<?, ?> l) {
      try {
         UUID id = replicatedMapListeners.get(l);

         if(id != null) {
            ignite.events().stopRemoteListen(id);
         }
      }
      catch(IgniteIllegalStateException ignore) {
         // already shutting down cluster, can't remove listener
      }
      catch(IgniteException ex) {
         if(isNodeStoppingException(ex)) {
            // node already stoped, cannot remove listener.
            return;
         }

         throw ex;
      }
   }

   @Override
   public <K, V> Cache<K, V> getCache(String name, boolean replicated, ExpiryPolicy expiryPolicy) {
      CacheConfiguration<K, V> config;

      if(replicated) {
         config = getCacheConfiguration(name, CacheMode.REPLICATED, DEFAULT_BACKUP_COUNT);
      }
      else {
         config = getCacheConfiguration(name);
      }

      config = config.setEagerTtl(expiryPolicy != null);
      IgniteCache<K, V> cache = ignite.getOrCreateCache(config);

      if(expiryPolicy != null) {
         cache = cache.withExpiryPolicy(expiryPolicy);
      }

      return cache;
   }

   @Override
   public <K, V> Collection<K> getLocalCacheKeys(Cache<K, V> cache, Collection<K> keys) {
      Affinity<K> affinity = ignite.affinity(cache.getName());
      ClusterNode localNode = ignite.cluster().localNode();
      return keys.stream()
         .filter(k -> affinity.isPrimary(localNode, k))
         .toList();
   }

   @Override
   public <K> boolean isLocalCacheKey(String cache, K key) {
      Affinity<K> affinity = ignite.affinity(cache);
      ClusterNode localNode = ignite.cluster().localNode();
      return affinity.isPrimary(localNode, key);
   }

   @Override
   public <T> T affinityCall(String cache, String key, AffinityCallable<T> job) {
      String id = UUID.randomUUID().toString();
      ClusterNode node = ignite.affinity(cache).mapKeyToNode(key);
      AffinityCallRequest<T> request = new AffinityCallRequest<>(
         id, getNodeName(ignite.cluster().localNode()), getNodeName(node), job);
      CompletableFuture<T> future = new CompletableFuture<>();
      affinityFutures.put(id, future);
      ignite.message().sendOrdered(node, request, 0);

      try {
         return future.get();
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public <T> List<T> affinityCallAll(String cache, AffinityCallable<T> job) {
      Set<ClusterNode> nodes = new HashSet<>();
      Affinity<?> affinity = ignite.affinity(cache);

      for(int i = 0; i < affinity.partitions(); i++) {
         nodes.add(affinity.mapPartitionToNode(i));
      }

      List<CompletableFuture<T>> futures = new ArrayList<>(nodes.size());

      for(ClusterNode node : nodes) {
         String id = UUID.randomUUID().toString();
         AffinityCallRequest<T> request = new AffinityCallRequest<>(
            id, getNodeName(ignite.cluster().localNode()), getNodeName(node), job);
         CompletableFuture<T> future = new CompletableFuture<>();
         affinityFutures.put(id, future);
         futures.add(future);
         ignite.message().sendOrdered(node, request, 0);
      }

      List<T> results = new ArrayList<>();

      for(CompletableFuture<T> future : futures) {
         try {
            results.add(future.get());
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      }

      return results;
   }

   @Override
   public void addCacheRebalanceListener(String cacheName, CacheRebalanceListener listener) {
      UUID id = ignite.events()
         .remoteListen(new RebalanceListenerAdapter(listener),
                       new RebalanceEventFilter(cacheName),
                       EventType.EVT_CACHE_REBALANCE_STOPPED);
      rebalanceListeners.put(listener, id);
   }

   @Override
   public void removeCacheRebalanceListener(String cacheName, CacheRebalanceListener listener) {
      UUID id = rebalanceListeners.remove(listener);

      if(id != null) {
         ignite.events(ignite.cluster().forRemotes()).stopRemoteListen(id);
      }
   }

   private boolean isNodeStoppingException(Throwable t) {
      if(t == null) {
         return false;
      }

      if(t instanceof NodeStoppingException) {
         return true;
      }
      else {
         return isNodeStoppingException(t.getCause());
      }
   }

   @Override
   public <E> BlockingQueue<E> getQueue(String name) {
      CollectionConfiguration queueConfig = new CollectionConfiguration();
      queueConfig.setBackups(getDefaultBackupCount());
      queueConfig.setCacheMode(getDefaultCacheMode());
      queueConfig.setAtomicityMode(CacheAtomicityMode.ATOMIC);
      return ignite.queue(name, 0, queueConfig);
   }

   @Override
   public void destroyQueue(String name) {
      IgniteQueue<?> queue = ignite.queue(name, 0, null);

      if(queue != null) {
         queue.close();
      }
   }

   @Override
   public <E> Set<E> getSet(String name) {
      CollectionConfiguration setConfig = new CollectionConfiguration();
      setConfig.setBackups(getDefaultBackupCount());
      setConfig.setCacheMode(getDefaultCacheMode());
      setConfig.setAtomicityMode(CacheAtomicityMode.ATOMIC);
      return ignite.set(name, setConfig);
   }

   @Override
   public void destroySet(String name) {
      IgniteSet<?> set = ignite.set(name, null);

      if(set != null) {
         set.close();
      }
   }

   @Override
   public DistributedLong getLong(String name) {
      return new IgniteDistributedLong(ignite.atomicLong(name, 0, true));
   }

   @Override
   public void destroyLong(String name) {
      IgniteAtomicLong igniteAtomicLong = ignite.atomicLong(name, 0, false);

      if(igniteAtomicLong != null) {
         igniteAtomicLong.close();
      }
   }

   @Override
   public <V> DistributedReference<V> getReference(String name) {
      return new IgniteDistributedReference<>(ignite.atomicReference(name, null, true));
   }

   @Override
   public void destroyReference(String name) {
      IgniteAtomicReference<?> igniteAtomicReference = ignite.atomicReference(name, null, false);

      if(igniteAtomicReference != null) {
         igniteAtomicReference.close();
      }
   }

   @Override
   public <T> Future<T> submit(Callable<T> task, boolean scheduler) {
      return submit0(getNextTaskLevel(), task, scheduler);
   }

   private <T> Future<T> submit0(int level, Callable<T> task, boolean scheduler) {
      // Do not use lambda expression to submit the task to ignite, it can not run with JDK21.
      ClusterGroup clusterGroup = scheduler ? ignite.cluster().forPredicate(SCHEDULE_SELECTOR) :
         ignite.cluster().forServers();
      return CompletableFuture.supplyAsync(new IgniteTaskFuture<>(ignite, clusterGroup, task, level),
         getExecutorService(level));
   }

   @Override
   public <T> Future<Collection<T>> submitAll(Callable<T> task) {
      IgniteFuture<Collection<T>> future = getIgniteCompute(ignite, null, getNextTaskLevel())
         .broadcastAsync(new IgniteTaskCallable<>(task));
      CompletableFuture<Collection<T>> cf = new CompletableFuture<>();

      future.listen(f -> {
         try {
            cf.complete(future.get());
         }
         catch(Exception e) {
            cf.completeExceptionally(e);
         }
      });

      return cf;
   }


   /**
    * Get the next task execute level.
    */
   private int getNextTaskLevel() {
      Integer level = TASK_EXECUTE_LEVEL.get();

      if(level == null) {
         return 0;
      }

      return level + 1;
   }

   @Override
   public DistributedScheduledExecutorService getScheduledExecutor() {
      Collection<ServiceDescriptor> services = ignite.services().serviceDescriptors();
      String serviceId = "IgniteScheduledExecutorService";
      boolean deployed = false;

      for(ServiceDescriptor service : services) {
         if(service.name().equals(serviceId)) {
            // service found, no need to do anything
            deployed = true;
            break;
         }
      }

      if(!deployed) {
         ServiceConfiguration scheduledExecutorServiceConfig = new ServiceConfiguration();
         scheduledExecutorServiceConfig.setService(new IgniteScheduledExecutorServiceImpl());
         scheduledExecutorServiceConfig.setName(serviceId);
         scheduledExecutorServiceConfig.setTotalCount(1);
         ignite.services().deploy(scheduledExecutorServiceConfig);
      }

      return ignite.services().serviceProxy("IgniteScheduledExecutorService",
                                            IgniteScheduledExecutorService.class,
                                            false);
   }

   @Override
   public void destroyScheduledExecutor() {
      ignite.services().cancel("IgniteScheduledExecutorService");
   }

   @Override
   public String getServiceOwner(String serviceId) {
      // make sure the service has been deployed or else this will be null
      IgniteCluster.deployAndGetService(ignite, serviceId);
      Collection<ServiceDescriptor> services = ignite.services().serviceDescriptors();

      for(ServiceDescriptor service : services) {
         if(service.name().equals(serviceId)) {
            UUID id = service.topologySnapshot().keySet().stream().findFirst().orElse(null);

            if(id != null) {
               ClusterNode owner = ignite.cluster().node(id);
               return owner == null ? null : getNodeName(owner);
            }

            break;
         }
      }

      return null;
   }

   @Override
   public <T extends Serializable> Future<T> submit(String serviceId, SingletonCallableTask<T> task) {
      return submit0(getNextTaskLevel(), serviceId, task);
   }

   public <T extends Serializable> Future<T> submit0(int level, String serviceId, SingletonCallableTask<T> task) {
      return CompletableFuture.supplyAsync(
         new IgniteServiceCallableTask<>(ignite, serviceId, task, level),
         getExecutorService(level));
   }

   @Override
   public Future<?> submit(String serviceId, SingletonRunnableTask task) {
      return submit0(getNextTaskLevel(), serviceId, task);
   }

   private Future<?> submit0(int level, String serviceId, SingletonRunnableTask task) {
      return CompletableFuture.supplyAsync(new IgniteServiceRunnableTask(ignite, serviceId, task, level),
         getExecutorService(level));
   }

   /**
    * get the ExecutorService for the current task level.
    */
   private ExecutorService getExecutorService(int level) {
      return executorServiceMap
         .computeIfAbsent(level, k -> Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()));
   }

   private static ServiceTaskExecutor deployAndGetService(Ignite ignite, String serviceId) {
      Collection<ServiceDescriptor> services = ignite.services().serviceDescriptors();
      boolean deployed = false;

      for(ServiceDescriptor service : services) {
         if(service.name().equals(serviceId)) {
            // service found, no need to do anything
            deployed = true;
            break;
         }
      }

      // deploy a new service
      if(!deployed) {
         ignite.services().deployClusterSingleton(serviceId, new ServiceTaskExecutorImpl(serviceId));
      }

      return ignite.services().serviceProxy(serviceId, ServiceTaskExecutor.class, false);
   }

   @Override
   public boolean isSchedulerRunning() {
      CloudRunnerConfig cloudRunner = InetsoftConfig.getInstance().getCloudRunner();

      if(cloudRunner != null) {
         return true;
      }

      for(ClusterNode node : ignite.cluster().nodes()) {
         if(Boolean.TRUE.equals(node.attribute("scheduler"))) {
            return true;
         }
      }

      return false;
   }

   @Override
   public void addMembershipListener(inetsoft.sree.internal.cluster.MembershipListener l) {
      membershipListeners.add(l);
   }

   @Override
   public void removeMembershipListener(inetsoft.sree.internal.cluster.MembershipListener l) {
      membershipListeners.remove(l);
   }

   @Override
   public void addMessageListener(inetsoft.sree.internal.cluster.MessageListener l) {
      messageListeners.add(l);
   }

   @Override
   public void removeMessageListener(inetsoft.sree.internal.cluster.MessageListener l) {
      messageListeners.remove(l);
   }

   @Override
   public void sendMessage(Serializable message) {
      ignite.message().sendOrdered(MESSAGE_TOPIC, message, 0);
   }

   @Override
   public void sendMessage(String server, Serializable message) {
      AddressedMessage addressedMessage = new AddressedMessage();
      addressedMessage.setRecipient(server);
      addressedMessage.setMessage(message);
      ignite.message().sendOrdered(MESSAGE_TOPIC, addressedMessage, 0);
   }

   @Override
   public <T extends Serializable> T exchangeMessages(String address, Serializable outgoingMessage,
                                                      Function<MessageEvent, T> matcher)
      throws Exception
   {
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<T> result = new AtomicReference<>(null);

      inetsoft.sree.internal.cluster.MessageListener listener = e -> {
         if(address.equals(e.getSender())) {
            T value = matcher.apply(e);

            if(value != null) {
               result.set(value);
               latch.countDown();
            }
         }
      };

      addMessageListener(listener);

      try {
         sendMessage(address, outgoingMessage);

         if(!latch.await(30, TimeUnit.SECONDS)) {
            throw new InterruptedException("Timed out waiting for response from " + address);
         }
      }
      finally {
         removeMessageListener(listener);
      }

      return result.get();
   }

   @Override
   public <T extends Serializable> T exchangeMessages(String address, Serializable outgoingMessage,
                                                      Class<T> responseType)
      throws Exception
   {
      return exchangeMessages(address, outgoingMessage, e ->
         e.getMessage() != null && responseType.isAssignableFrom(e.getMessage().getClass()) ?
            responseType.cast(e.getMessage()) : null);
   }

   public boolean isMasterScheduler() {
      ClusterNode self = ignite.cluster().localNode();
      ClusterNode firstScheduler = ignite.cluster().nodes().stream()
         .filter(m -> m.attribute("scheduler") == Boolean.TRUE)
         .findFirst()
         .orElse(null);
      return self.equals(firstScheduler);
   }

   @Override
   public String addTransferFile(File file) {
      return clusterFileTransfer.addTransferFile(file);
   }

   @Override
   public File getTransferFile(String link) throws IOException {
      return clusterFileTransfer.getTransferFile(link);
   }

   @Override
   public void close() {
      fireLifecycleEvent(ClusterLifecycleEvent.Type.CLOSED);
      lifecycleListeners.clear();
      clusterFileTransfer.close();
      ignite.close();
      timer.cancel();

      for(ExecutorService value : executorServiceMap.values()) {
         if(value != null) {
            value.shutdownNow();
         }
      }

      executorServiceMap.clear();
   }

   /**
    * Get the ignite instance used by this cluster.
    * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    * This should not be used except when ignite is explicitly referenced
    * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    */
   public Ignite getIgniteInstance() {
      return ignite;
   }

   private Ignite createIgniteInstance(IgniteConfiguration config) {
      return Ignition.start(config);
   }

   @Override
   public void addClusterLifecycleListener(ClusterLifecycleListener l) {
      lifecycleListeners.add(l);
   }

   @Override
   public void removeClusterLifecycleListener(ClusterLifecycleListener l) {
      lifecycleListeners.remove(l);
   }

   @Override
   public void queryCache(String cache, boolean distributed, String sql, Object[] params,
                          Consumer<List<?>> consumer)
   {
      SqlFieldsQuery query = new SqlFieldsQuery(sql).setDistributedJoins(distributed);

      if(params != null && params.length > 0) {
         query = query.setArgs(params);
      }

      try(FieldsQueryCursor<List<?>> cursor = ignite.cache(cache).query(query)) {
         cursor.forEach(consumer);
      }
   }

   private void fireLifecycleEvent(@SuppressWarnings("SameParameterValue") ClusterLifecycleEvent.Type type) {
      ClusterLifecycleEvent event = null;

      for(ClusterLifecycleListener l : lifecycleListeners) {
         if(event == null) {
            event = new ClusterLifecycleEvent(this, type);
         }

         if(Objects.requireNonNull(type) == ClusterLifecycleEvent.Type.CLOSED) {
            l.clusterClosed(event);
         }
      }
   }

   @Override
   public List<String> getClusterAddresses() {
      return ignite.cluster().forServers().nodes().stream().map((node) -> {
         String name = getNodeName(node);
         String hostIp = node.attribute("inetsoft.host.ip");
         String hostPort = node.attribute("inetsoft.host.port");

         // host ip not specified then just return node name (local_ip:local_port)
         if(Tool.isEmptyString(hostIp)) {
            return name;
         }

         if(Tool.isEmptyString(hostPort)) {
            hostPort = name.substring(name.indexOf(":") + 1);
         }

         return hostIp + ":" + hostPort;
      }).toList();
   }

   private static IgniteCompute getIgniteCompute(Ignite igniteInstance, ClusterGroup clusterGroup, int level) {
      int poolLevel = level % IGNITE_EXECUTE_POOL_COUNT;

      if(poolLevel == 0) {
         return clusterGroup != null ? igniteInstance.compute(clusterGroup) : igniteInstance.compute();
      }
      else {
         poolLevel -= 1;

         return clusterGroup != null ?
            igniteInstance.compute(clusterGroup).withExecutor(getIgniteExecutePoolName(poolLevel)) :
            igniteInstance.compute().withExecutor(getIgniteExecutePoolName(poolLevel));
      }
   }

   private final Ignite ignite;
   private final Set<inetsoft.sree.internal.cluster.MessageListener> messageListeners = new CopyOnWriteArraySet<>();
   private final Set<inetsoft.sree.internal.cluster.MembershipListener> membershipListeners = new CopyOnWriteArraySet<>();
   private final Map<MapChangeListener<?, ?>, UUID> mapListeners = new ConcurrentHashMap<>();
   private final Map<MapChangeListener<?, ?>, UUID> multiMapListeners = new ConcurrentHashMap<>();
   private final Map<MapChangeListener<?, ?>, UUID> replicatedMapListeners = new ConcurrentHashMap<>();
   private final Map<CacheRebalanceListener, UUID> rebalanceListeners = new ConcurrentHashMap<>();
   private final Set<ClusterLifecycleListener> lifecycleListeners =
      new CopyOnWriteArraySet<>();
   private final Map<String, LockInfo> lockInfos = new ConcurrentHashMap<>();
   private final Map<String, CompletableFuture<?>> affinityFutures = new ConcurrentHashMap<>();
   private final Timer timer = new Timer();
   private final ClusterFileTransfer clusterFileTransfer;
   private final Map<Integer, ExecutorService> executorServiceMap = new ConcurrentHashMap<>();
   private final ExecutorService messageExecutor;
   private final ExecutorService affinityExecutor;

   private static final int DEFAULT_BACKUP_COUNT = 2;
   private static final long MIN_LOCK_DURATION_MILLIS = Duration.ofMinutes(10).toMillis();
   private static final String MESSAGE_TOPIC = IgniteCluster.class.getName() + ".messageTopic";
   private static final String AFFINITY_TOPIC = IgniteCluster.class.getName() + ".affinityTopic";
   private static final String RW_MAP_NAME = IgniteCluster.class.getName() + ".rwMap";
   private static final IgnitePredicate<ClusterNode> SCHEDULE_SELECTOR = node -> {
      // select any node when config has cloud runner, because the separate scheduler server do not exist.
      if(InetsoftConfig.getInstance().getCloudRunner() != null) {
         return true;
      }

      return Boolean.TRUE.equals(node.attribute("scheduler"));
   };
   private static final ThreadLocal<Integer> TASK_EXECUTE_LEVEL = new ThreadLocal<>();
   private static final String IGNITE_EXECUTE_POOL = "IGNITE_EXECUTE_POOL";
   private static final int IGNITE_EXECUTE_POOL_COUNT = 2;

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final class AffinityCallProcessor implements IgniteBiPredicate<UUID, Serializable> {
      @SuppressWarnings({ "rawtypes", "unchecked" })
      @Override
      public boolean apply(UUID uuid, Serializable message) {
         if(message instanceof AffinityCallRequest request) {
            if(getNodeName(ignite.cluster().localNode()).equals(request.getRecipient())) {
               affinityExecutor.submit(new AffinityCallRequestTask(request));
            }
         }
         else if(message instanceof AffinityCallResponse response) {
            if(getNodeName(ignite.cluster().localNode()).equals(response.getRecipient())) {
               CompletableFuture future = affinityFutures.remove(response.getId());

               if(future != null) {
                  if(response.getError() != null) {
                     future.completeExceptionally(response.getError());
                  }
                  else {
                     future.complete(response.getResult());
                  }
               }
            }
         }

         return true;
      }
   }

   private static final class AffinityCallRequest<T> implements Serializable {
      public AffinityCallRequest(String id, String sender, String recipient, AffinityCallable<T> callable) {
         this.id = id;
         this.sender = sender;
         this.recipient = recipient;
         this.callable = callable;
      }

      public String getId() {
         return id;
      }

      public String getSender() {
         return sender;
      }

      public String getRecipient() {
         return recipient;
      }

      public AffinityCallable<T> getCallable() {
         return callable;
      }

      private final String id;
      private final String sender;
      private final String recipient;
      private final AffinityCallable<T> callable;
   }

   private static final class AffinityCallResponse<T extends Serializable> implements Serializable {
      public AffinityCallResponse(String id, String recipient, T result, Throwable error) {
         this.id = id;
         this.recipient = recipient;
         this.result = result;
         this.error = error;
      }

      public String getId() {
         return id;
      }

      public String getRecipient() {
         return recipient;
      }

      public T getResult() {
         return result;
      }

      public Throwable getError() {
         return error;
      }

      private final String id;
      private final String recipient;
      private final T result;
      private final Throwable error;
   }

   private static final class AffinityCallRequestTask<T extends Serializable> implements Runnable {
      public AffinityCallRequestTask(AffinityCallRequest<T> request) {
         this.request = request;
      }

      @Override
      public void run() {
         T result = null;
         Throwable error = null;

         try {
            result = request.getCallable().call();
         }
         catch(Exception e) {
            error = e;
         }

         AffinityCallResponse<T> response =
            new AffinityCallResponse<>(request.getId(), request.getSender(), result, error);
         IgniteCluster cluster = (IgniteCluster) Cluster.getInstance();
         cluster.ignite.message().sendOrdered(AFFINITY_TOPIC, response, 0);
      }

      private final AffinityCallRequest<T> request;
   }

   private final class MessageDispatcher
      implements IgniteBiPredicate<UUID, Serializable>
   {
      @Override
      public boolean apply(UUID nodeId, Serializable message) {
         if(message instanceof AddressedMessage addressedMessage) {
            if(getNodeName(ignite.cluster().localNode())
               .equals(addressedMessage.getRecipient()))
            {
               message = addressedMessage.getMessage();
            }
            else {
               return true;
            }
         }

         MessageEvent event = null;

         for(inetsoft.sree.internal.cluster.MessageListener listener : messageListeners) {
            if(listener != null) {
               if(event == null) {
                  ClusterNode senderNode = ignite.cluster().node(nodeId);
                  ClusterNode localNode = ignite.cluster().localNode();
                  event = new MessageEvent(
                     IgniteCluster.this, getNodeName(senderNode),
                     Objects.equals(senderNode, localNode), message);
               }

               messageExecutor.submit(new MessageDispatchTask(listener, event));
            }
         }

         return true;
      }
   }

   private record MessageDispatchTask(MessageListener listener, MessageEvent event)
      implements Runnable
   {
      @Override
         public void run() {
            listener.messageReceived(event);
         }
   }

   private static final class AddressedMessage implements Serializable {
      public String getRecipient() {
         return recipient;
      }

      public void setRecipient(String recipient) {
         this.recipient = recipient;
      }

      public Serializable getMessage() {
         return message;
      }

      public void setMessage(Serializable message) {
         this.message = message;
      }

      private String recipient;
      private Serializable message;
   }

   private final class MembershipDispatcher implements IgnitePredicate<Event> {
      @Override
      public boolean apply(Event event) {
         ExecutorService executor = getExecutorService(Integer.MAX_VALUE);

         if(event.type() == EventType.EVT_NODE_JOINED) {
            MembershipEvent membershipEvent =
               new MembershipEvent(IgniteCluster.this, getNodeName(event.node()));

            executor.submit(() -> {
               try {
                  for(inetsoft.sree.internal.cluster.MembershipListener l : membershipListeners) {
                     l.memberAdded(membershipEvent);
                  }
               }
               catch(Exception e) {
                  LOG.error("Failed to apply membership event when detected node joined event.", e);
               }
            });
         }
         else if(event.type() == EventType.EVT_NODE_LEFT) {
            String node;

            if(event instanceof DiscoveryEvent discoveryEvent) {
               node = discoveryEvent.eventNode().addresses().iterator().next();
            }
            else {
               node = getNodeName(event.node());
            }

            executor.submit(() -> {
               try {
                  MembershipEvent membershipEvent = new MembershipEvent(IgniteCluster.this, node);

                  for(inetsoft.sree.internal.cluster.MembershipListener l : membershipListeners) {
                     l.memberRemoved(membershipEvent);
                  }
               }
               catch(Exception e) {
                  LOG.error("Failed to apply membership event when detected node left event.", e);
               }
            });
         }

         return true;
      }
   }

   private static final class MapListenerAdapter<K, V> implements IgniteBiPredicate<UUID, CacheEvent> {
      MapListenerAdapter(MapChangeListener<K, V> listener) {
         this.listener = listener;
      }

      @SuppressWarnings({ "rawtypes", "unchecked" })
      @Override
      public boolean apply(UUID nodeId, CacheEvent event) {
         EntryEvent entryEvent = new EntryEvent<>(event.cacheName(), event.key(), event.oldValue(),
                                                  event.newValue());

         if(event.type() == EventType.EVT_CACHE_OBJECT_PUT) {
            if(event.oldValue() == null) {
               listener.entryAdded(entryEvent);
            }
            else {
               listener.entryUpdated(entryEvent);
            }
         }
         else if(event.type() == EventType.EVT_CACHE_OBJECT_REMOVED) {
            listener.entryRemoved(entryEvent);
         }
         else if(event.type() == EventType.EVT_CACHE_OBJECT_EXPIRED) {
            listener.entryExpired(entryEvent);
         }
         else if(event.type() == EventType.EVT_CACHE_ENTRY_EVICTED) {
            listener.entryEvicted(entryEvent);
         }

         return true;
      }

      private final MapChangeListener<K, V> listener;
   }

   private static class MapEventFilter implements IgnitePredicate<CacheEvent> {
      public MapEventFilter(String cacheName) {
         this.cacheName = cacheName;
      }

      @Override
      public boolean apply(CacheEvent event) {
         return Objects.equals(event.cacheName(), cacheName);
      }

      private final String cacheName;
   }

   private static final class RebalanceListenerAdapter
      implements IgniteBiPredicate<UUID, CacheRebalancingEvent>
   {
      RebalanceListenerAdapter(CacheRebalanceListener listener) {
         this.listener = listener;
      }

      @Override
      public boolean apply(UUID nodeId, CacheRebalancingEvent event) {
         if(event.type() == EventType.EVT_CACHE_REBALANCE_STOPPED) {
            CacheRebalanceEvent newEvent = new CacheRebalanceEvent(
               this, event.cacheName(), event.timestamp(), event.partition());
            listener.cacheRebalanced(newEvent);
         }

         return true;
      }

      private final CacheRebalanceListener listener;
   }

   private static class RebalanceEventFilter implements IgnitePredicate<CacheRebalancingEvent> {
      public RebalanceEventFilter(String cacheName) {
         this.cacheName = cacheName;
      }

      @Override
      public boolean apply(CacheRebalancingEvent event) {
         return Objects.equals(event.cacheName(), cacheName);
      }

      private final String cacheName;
   }

   private static class LockInfo {
      public LockInfo() {
         this.threadId = Thread.currentThread().threadId();
         this.lockTime = System.currentTimeMillis();
         this.stackTrace = Thread.currentThread().getStackTrace();
      }

      @Override
      public String toString() {
         return "LockInfo{" +
            "threadId=" + threadId +
            ", lockTime=" + lockTime +
            ", stackTrace=" + Arrays.stream(stackTrace).map(Objects::toString)
            .collect(Collectors.joining("\n", "\n", "")) +
            '}';
      }

      private final long threadId;
      private final long lockTime;
      private final StackTraceElement[] stackTrace;
   }

   private static class IgniteTaskFuture<T> implements Supplier<T> {
      public IgniteTaskFuture(Ignite igniteInstance, ClusterGroup clusterGroup, Callable<T> task,
                              int level)
      {
         this.igniteInstance = igniteInstance;
         this.clusterGroup = clusterGroup;
         this.task = new IgniteTaskCallable<>(task, level);
      }

      @Override
      public T get() {
         return getIgniteCompute(igniteInstance, clusterGroup, task.level).call(task);
      }

      private final Ignite igniteInstance;
      private final ClusterGroup clusterGroup;
      private final IgniteTaskCallable<T> task;
   }

   private static class IgniteTaskCallable<T> implements IgniteCallable<T> {
      public IgniteTaskCallable(Callable<T> task) {
         this(task, 0);
      }

      public IgniteTaskCallable(Callable<T> task, int level) {
         this.task = task;
         this.level = level;
      }

      @Override
      public T call() throws Exception {
         try {
            TASK_EXECUTE_LEVEL.set(level);
            return task.call();
         }
         finally {
            TASK_EXECUTE_LEVEL.remove();
         }
      }

      private final Callable<T> task;
      private final int level;
   }

   private static class IgniteServiceRunnableTask extends IgniteServiceTask
      implements Supplier<Object>
   {
      public IgniteServiceRunnableTask(Ignite ignite, String service, SingletonRunnableTask task,
                                       int level)
      {
         super(ignite, service);
         this.runnableTask = new SingletonRunnableTaskProxy(task, level);
      }

      @Override
      public Object get() {
         IgniteCluster.deployAndGetService(ignite, service).submitTask(runnableTask);
         return null;
      }

      private final SingletonRunnableTask runnableTask;
   }

   private static class IgniteServiceCallableTask<T extends Serializable> extends IgniteServiceTask
      implements Supplier<T>
   {
      public IgniteServiceCallableTask(Ignite ignite, String service, SingletonCallableTask<T> task,
                                       int level)
      {
         super(ignite, service);
         this.task = new SingletonCallableTaskProxy<>(task, level);
      }

      @Override
      public T get() {
         return IgniteCluster.deployAndGetService(ignite, service).submitTask(task);
      }

      private final SingletonCallableTask<T> task;
   }

   private static class SingletonCallableTaskProxy<T extends Serializable>
      implements SingletonCallableTask<T>
   {
      private SingletonCallableTaskProxy(SingletonCallableTask<T> task, int level) {
         this.task = task;
         this.level = level;
      }

      @Override
      public T call() throws Exception {
         try {
            TASK_EXECUTE_LEVEL.set(level);
            return task.call();
         }
         finally {
            TASK_EXECUTE_LEVEL.remove();
         }
      }

      private final SingletonCallableTask<T> task;
      private final int level;
   }

   private static class SingletonRunnableTaskProxy implements SingletonRunnableTask {
      private SingletonRunnableTaskProxy(SingletonRunnableTask task, int level) {
         this.task = task;
         this.level = level;
      }

      @Override
      public void run() {
         try {
            TASK_EXECUTE_LEVEL.set(level);
            task.run();
         }
         finally {
            TASK_EXECUTE_LEVEL.remove();
         }
      }

      private final SingletonRunnableTask task;
      private final int level;
   }

   private static class IgniteServiceTask {
      public IgniteServiceTask(Ignite ignite, String service) {
         this.ignite = ignite;
         this.service = service;
      }

      protected String service;
      protected Ignite ignite;
   }

}
