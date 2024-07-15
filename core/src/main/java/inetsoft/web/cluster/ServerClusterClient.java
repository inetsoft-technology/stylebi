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
package inetsoft.web.cluster;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.util.*;
import inetsoft.web.admin.monitoring.StatusMetricsType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ServerClusterClient {
   public ServerClusterClient() {
      this(false);
   }

   public ServerClusterClient(boolean updateMembership) {
      cluster = Cluster.getInstance();

      if(updateMembership) {
         cluster.addMembershipListener(new MembershipListener() {
            @Override
            public void memberAdded(MembershipEvent event) {
               configuredServers = loadConfiguredServers();
               monitoringServers = loadMonitoringServersServers();
            }

            @Override
            public void memberRemoved(MembershipEvent event) {
               String address = event.getMember();
               String server = getClusterNodeServer(address);

               if(server != null) {
                  setStatus(server, s -> {
                     s.setStatus(ServerClusterStatus.Status.DOWN);
                     s.setPaused(false);
                     s.setLoad(0);
                  });
               }

               configuredServers = loadConfiguredServers();
               monitoringServers = loadMonitoringServersServers();
            }
         });

         cluster.addMessageListener(event -> {
            if((event.getMessage() instanceof RefreshClusterMessage) &&
               !event.getSender().equals(cluster.getLocalMember()))
            {
               RefreshClusterMessage message = (RefreshClusterMessage) event.getMessage();
               refresh(message.getProperty(), message.getDefaultValue(), false);
            }
         });
      }

      configuredServers = loadConfiguredServers();
      monitoringServers = loadMonitoringServersServers();
   }

   public Set<String> getConfiguredServers() {
      Set<String> servers = configuredServers;
      return servers == null ? Collections.emptySet() : Collections.unmodifiableSet(servers);
   }

   public Set<String> getMonitoringServersServers() {
      Set<String> servers = monitoringServers;
      return servers == null ? Collections.emptySet() : Collections.unmodifiableSet(servers);
   }

   private String getServerClusterNode(String server) {
      String address = serverAddressCache.get(server);

      if(address == null) {
         address = SUtil.computeServerClusterNode(server);

         if(address != null) {
            serverAddressCache.put(server, address);
            addressServerCache.put(address, server);
         }
      }

      return address;
   }

   private String getClusterNodeServer(String clusterNode) {
      String server = addressServerCache.get(clusterNode);

      if(clusterNode != null && server == null) {
         for(String serverName : getConfiguredServers()) {
            String address = getServerClusterNode(serverName);

            if(clusterNode.equals(address)) {
               server = serverName;
               addressServerCache.put(clusterNode, server);
               serverAddressCache.put(server, clusterNode);
            }
         }
      }

      return server;
   }

   public ServerClusterStatus getStatus(String server) {
      return getClusterNodeStatus(getServerClusterNode(server));
   }

   public void setStatus(String server, Consumer<ServerClusterStatus> updater) {
      setClusterNodeStatus(getServerClusterNode(server), updater);
   }

   public ServerClusterStatus getStatus() {
      return getClusterNodeStatus(getLocalServer(cluster));
   }

   public void setStatus(Consumer<ServerClusterStatus> updater) {
      setClusterNodeStatus(getLocalServer(cluster), updater);
   }

   public boolean restartServer() {
      return sendMessage(cluster.getLocalMember(), new RestartMessage());
   }

   public boolean restartServer(String server) {
      return sendMessage(server, new RestartMessage());
   }

   @SuppressWarnings("UnusedReturnValue")
   public boolean pauseServer(String server) {
      return updatePaused(server, true);
   }

   @SuppressWarnings("UnusedReturnValue")
   public boolean resumeServer(String server) {
      return updatePaused(server, false);
   }

   public boolean startScheduler(String server) {
      return sendMessage(server, new StartSchedulerMessage());
   }

   public boolean stopScheduler(String server) {
      return sendMessage(server, new StopSchedulerMessage());
   }

   @SuppressWarnings("UnusedReturnValue")
   public boolean cleanCache(String server) {
      return sendMessage(server, new CleanCacheMessage());
   }

   public void addPropertyChangeListener(PropertyChangeListener l) {
      synchronized(support) {
         boolean start = support.getPropertyChangeListeners().length == 0;
         support.addPropertyChangeListener(l);

         if(start) {
            mapListener = createMapListener();
            cluster.addMapListener(CLUSTER_STATUS_MAP, mapListener);
         }
      }
   }

   public void removePropertyChangeListener(PropertyChangeListener l) {
      synchronized(support) {
         support.removePropertyChangeListener(l);

         if(support.getPropertyChangeListeners().length == 0 && mapListener != null) {
            cluster.removeMapListener(CLUSTER_STATUS_MAP, mapListener);
            mapListener = null;
         }
      }
   }

   public void refresh(String property, String defaultValue) {
      refresh(property, defaultValue, true);
   }

   public ServerClusterStatus getClusterNodeStatus(String address) {
      ServerClusterStatus status = null;

      if(address != null) {
         Map<String, ServerClusterStatus> map = cluster.getMap(CLUSTER_STATUS_MAP);
         status = map.get(address);
      }

      if(status == null && address != null && address.indexOf(':') != -1) {
         int colon = address.indexOf(':');
         String server = address.substring(0, colon);
         Map<String, ServerClusterStatus> map = cluster.getMap(CLUSTER_STATUS_MAP);
         status = map.get(server);
      }

      if(status == null) {
         status = new ServerClusterStatus();
         status.setStatus(ServerClusterStatus.Status.DOWN);
         status.setPaused(false);
         status.setLoad(0);
      }

      return status;
   }

   private void setClusterNodeStatus(String address, Consumer<ServerClusterStatus> updater) {
      if(address != null) {
         // adding status update to a queue to be applied with one lock to avoid
         // lock contention.
         synchronized(updaters) {
            updaters.add(updater);
         }

         TimedQueue.addSingleton(new TimedQueue.TimedRunnable(50) {
            @Override
            public void run() {
               List<Consumer<ServerClusterStatus>> updaters2;

               synchronized(ServerClusterClient.this.updaters) {
                  updaters2 = new ArrayList<>(ServerClusterClient.this.updaters);
                  ServerClusterClient.this.updaters.clear();
               }

               Map<String, ServerClusterStatus> map = cluster.getMap(CLUSTER_STATUS_MAP);
               Lock lock = cluster.getLock(CLUSTER_STATUS_LOCK);
               lock.lock();

               try {
                  ServerClusterStatus status = map.computeIfAbsent(address, ServerClusterStatus::new);
                  updaters2.forEach(updater -> updater.accept(status));
                  map.put(address, status);
               }
               finally {
                  lock.unlock();
               }
            }
         });
      }
   }

   private void refresh(String property, String defaultValue, boolean notify) {
      configuredServers = loadConfiguredServers();
      monitoringServers = loadMonitoringServersServers();

      if(notify) {
         RefreshClusterMessage message = new RefreshClusterMessage();
         message.setProperty(property);
         message.setDefaultValue(defaultValue);

         try {
            cluster.sendMessage(message);
         }
         catch(Exception e) {
            LOG.warn("Failed to send refresh cluster message", e);
         }
      }
   }

   private boolean updatePaused(String server, boolean paused) {
      PauseClusterMessage message = new PauseClusterMessage();
      message.setPaused(paused);
      return sendMessage(server, message);
   }

   private boolean sendMessage(String server, ServerClusterMessage message) {
      String address = getServerClusterNode(server);
      boolean success = false;

      if(address != null) {
         try {
            success = cluster.exchangeMessages(address, message, e -> {
               Boolean result = null;

               if(message.isCompletedBy(e)) {
                  ServerClusterCompleteMessage msg = (ServerClusterCompleteMessage) e.getMessage();
                  result = msg.isSuccess();
               }

               return result;
            });
         }
         catch(Exception e) {
            LOG.warn("Failed to send message to " + address, e);
         }
      }

      return success;
   }

   private Set<String> loadConfiguredServers() {
      return cluster.getClusterNodes().stream()
         .filter(n -> "true".equals(cluster.getClusterNodeProperty(n, "reportServer")))
         .map(ServerClusterClient::getLocalServer)
         .collect(Collectors.toSet());
   }

   private Set<String> loadMonitoringServersServers() {
      return cluster.getClusterNodes().stream()
         .filter(n -> "true".equals(cluster.getClusterNodeProperty(n, "reportServer")) ||
            Boolean.TRUE.equals(cluster.getClusterNodeProperty(n, "scheduler")))
         .map(ServerClusterClient::getLocalServer)
         .collect(Collectors.toSet());
   }

   private MapChangeListener<String, ServerClusterStatus> createMapListener() {
      return new ClusterStatusListener();
   }

   public <T> Queue<T> getStatusHistory(StatusMetricsType type, String server, Object subtype) {
      if(cluster == null) {
         return null;
      }

      server = server == null || server.isEmpty() ? getLocalServer(cluster)
         : getLocalServer(server);
      String key = type + server + (subtype != null ? subtype : "");
      return cluster.getQueue(key);
   }

   public <T> void addStatusHistory(StatusMetricsType type, String server, Object subtype, T record) {
      Queue<T> queue = getStatusHistory(type, server, subtype);

      if(queue != null) {
         queue.add(record);

         int maxHistory = Integer.parseInt(SreeEnv.getProperty("monitor.dataset.size"));

         while(queue.size() > maxHistory) {
            queue.poll();
         }
      }
   }

   public <T> T getMetrics(StatusMetricsType type, String server) {
      Map<String, T> map = cluster.getMap(CLUSTER_STATUS_MAP + type);
      return map.get(server == null || server.isEmpty() ? getLocalServer(cluster) : server);
   }

   public <T> void setMetrics(StatusMetricsType type, T metrics) {
      setMetrics(type, metrics, null);
   }

   public <T> void setMetrics(StatusMetricsType type, T metrics, Consumer<T> consumer) {
      getDebouncer().debounce("setMetrics." + type, 2, TimeUnit.SECONDS, () -> {
         Map<String, T> map = cluster.getMap(CLUSTER_STATUS_MAP + type);
         map.put(getLocalServer(cluster), metrics);

         if(consumer != null) {
            consumer.accept(metrics);
         }
      });
   }

   public static Debouncer<String> getDebouncer() {
      return ConfigurationContext.getContext()
         .computeIfAbsent(DEBOUNCER_KEY, k -> new DefaultDebouncer<>(false));
   }

   public static String getLocalServer(Cluster cluster) {
      String server = cluster.getLocalMember();
      return getLocalServer(server);
   }

   private static String getLocalServer(String server) {
      int colon = server.indexOf(':');

      // strip off port
      return colon > 0 ? server.substring(0, colon) : server;
   }

   private class ClusterStatusListener implements MapChangeListener<String, ServerClusterStatus> {
      @Override
      public void entryAdded(EntryEvent<String, ServerClusterStatus> event) {
         support.firePropertyChange(getKey(event), null, event.getValue());
      }

      @Override
      public void entryRemoved(EntryEvent<String, ServerClusterStatus> event) {
         support.firePropertyChange(getKey(event), event.getOldValue(), null);
      }

      @Override
      public void entryUpdated(EntryEvent<String, ServerClusterStatus> event) {
         support.firePropertyChange(getKey(event), event.getOldValue(), event.getValue());
      }

      private String getKey(EntryEvent<String, ServerClusterStatus> event) {
         return "status:" + event.getKey();
      }
   }

   private final Cluster cluster;
   private final Map<String, String> serverAddressCache = new ConcurrentHashMap<>();
   private final Map<String, String> addressServerCache = new ConcurrentHashMap<>();
   private final PropertyChangeSupport support = new PropertyChangeSupport(this);

   private MapChangeListener<String, ServerClusterStatus> mapListener;
   private final List<Consumer<ServerClusterStatus>> updaters = new ArrayList<>();

   private volatile Set<String> configuredServers = null;
   private volatile Set<String> monitoringServers = null;

   private static final Logger LOG = LoggerFactory.getLogger(ServerClusterClient.class);
   private static final String CLUSTER_STATUS_MAP = "inetsoft.cluster.status.map";
   private static final String CLUSTER_STATUS_LOCK = "inetsoft.cluster.status.lock";
   private static final String DEBOUNCER_KEY = "ServerClusterClient.debouncer";
}
