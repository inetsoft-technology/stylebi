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
package inetsoft.sree.web;

import inetsoft.report.internal.LicenseException;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * SessionLicenseManager for clusters to manage concurrent session keys.
 */
public class ConcurrentSessionClusterService extends AbstractSessionService {
   ConcurrentSessionClusterService(Supplier<Integer> maxSessions) {
      this(maxSessions, true, ConcurrentSessionClusterService.class.getName() + ".licenseMap");
   }

   /**
    * Constructs a session license manager specific for a cluster server.
    * @param maxSessions Maximum # of concurrent sessions.
    * @param logoutAfterFail Whether to log the user out after a failure.
    * @param clusterMapName name of the distributed map that contains the licenses.
    */
   ConcurrentSessionClusterService(Supplier<Integer> maxSessions, boolean logoutAfterFail,
                                   String clusterMapName)
   {
      this.clusterLicenses = Cluster.getInstance().getReplicatedMap(clusterMapName);
      this.maxSessions = maxSessions;
      this.logoutAfterFail = logoutAfterFail;
      LicenseUpdater updater = new LicenseUpdater();
      this.runner = Executors.newSingleThreadScheduledExecutor();

      // Background thread which refreshes heartbeat timestamps in the distributed
      // map and removes entries from nodes that have stopped heartbeating.
      runner.scheduleAtFixedRate(updater, 1, 60, TimeUnit.SECONDS);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public synchronized void newSession(SRPrincipal srPrincipal) {
      Lock lock = Cluster.getInstance().getLock(LICENSE_LOCK_NAME);
      lock.lock();

      try {
         if(LOG.isDebugEnabled()) {
            LOG.debug(
               "New session requested by '{}' from {}; active: {}, max: {}",
               srPrincipal.getName(), getRemoteAddress(srPrincipal),
               clusterLicenses.size(), getMaxSessions());
         }

         if(!clusterLicenses.containsKey(srPrincipal.getUser().getCacheKey())) {
            if(clusterLicenses.size() == getMaxSessions()) {
               if(LOG.isDebugEnabled()) {
                  LOG.debug(
                     "Session limit reached ({}) when '{}' from {}; active sessions: {}",
                     getMaxSessions(), srPrincipal.getName(), getRemoteAddress(srPrincipal),
                     clusterLicenses.values().stream()
                        .map(l -> l.getPrincipal().getName()).toList());
               }

               sessionError(srPrincipal);
            }
            else {
               ClusterLicense license =
                  new ClusterLicense(srPrincipal, uuid, System.currentTimeMillis());
               clusterLicenses.put(srPrincipal.getUser().getCacheKey(), license);
            }
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public synchronized void releaseSession(SRPrincipal srPrincipal) {
      Lock lock = Cluster.getInstance().getLock(LICENSE_LOCK_NAME);
      lock.lock();

      try {
         clusterLicenses.remove(srPrincipal.getUser().getCacheKey());
      }
      finally {
         lock.unlock();
      }
   }

   @Override
   public synchronized Set<SRPrincipal> getActiveSessions() {
      Set<SRPrincipal> result = new HashSet<>();
      clusterLicenses.values().forEach(l -> result.add(l.getPrincipal()));
      return Collections.unmodifiableSet(result);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public synchronized void dispose() {
      super.dispose();
      runner.shutdownNow();
   }

   private void sessionError(SRPrincipal srPrincipal) {
      if(OrganizationManager.getInstance().isSiteAdmin(srPrincipal)) {
         throw new SessionsExceededException(
            "Session limit reached", buildActiveSessionInfoList());
      }

      if(logoutAfterFail) {
         SUtil.logout(srPrincipal);
      }

      final Catalog catalog = Catalog.getCatalog(srPrincipal);
      final String msg = String.format("%s %s",
                                       catalog.getString("common.sessionsExceed"),
                                       catalog.getString("common.contactAdmin"));
      throw new LicenseException(msg);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public synchronized void newSession(SRPrincipal srPrincipal, String sessionIdToReplace) {
      Lock lock = Cluster.getInstance().getLock(LICENSE_LOCK_NAME);
      lock.lock();

      try {
         if(sessionIdToReplace != null && !sessionIdToReplace.isEmpty()) {
            SRPrincipal toTerminate = null;

            for(ClusterLicense license : clusterLicenses.values()) {
               SRPrincipal p = license.getPrincipal();

               if(sessionIdToReplace.equals(p.getSessionID())) {
                  toTerminate = p;
                  break;
               }
            }

            if(toTerminate != null) {
               // SUtil.logout() will fire SessionListeners, which will call releaseSession()
               // on this manager via AbstractSessionService.loggedOut(). Java synchronized is
               // reentrant so this is safe on the same thread.
               SUtil.logout(toTerminate);
            }
         }

         newSession(srPrincipal);
      }
      finally {
         lock.unlock();
      }
   }

   private List<ActiveSessionInfo> buildActiveSessionInfoList() {
      List<ActiveSessionInfo> list = new ArrayList<>();

      for(ClusterLicense license : clusterLicenses.values()) {
         SRPrincipal p = license.getPrincipal();
         String sessionId = p.getSessionID();
         String username = IdentityID.getIdentityIDFromKey(p.getName()).getName();
         long loginTime = 0;
         String loginTimeStr = p.getProperty(SUtil.LONGON_TIME);

         if(loginTimeStr != null && !loginTimeStr.isEmpty()) {
            try {
               loginTime = Long.parseLong(loginTimeStr);
            }
            catch(NumberFormatException ignore) {
            }
         }

         list.add(new ActiveSessionInfo(sessionId, username, loginTime));
      }

      return list;
   }

   private int getMaxSessions() {
      return maxSessions.get();
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(ConcurrentSessionClusterService.class);

   private static final long HEARTBEAT_TIMEOUT = 180000L;

   private final Map<ClientInfo, ClusterLicense> clusterLicenses;
   private final ScheduledExecutorService runner;
   private final UUID uuid = UUID.randomUUID();
   private final Supplier<Integer> maxSessions;
   private final boolean logoutAfterFail;
   private static final String LICENSE_LOCK_NAME =
      ConcurrentSessionClusterService.class.getName() + ".lock";

   /**
    * Runnable which maintains heartbeat timestamps in the distributed license map.
    * Every 60 seconds, this updates the timestamp of each license owned by this node
    * and removes any licenses whose timestamp has not been updated within
    * {@link #HEARTBEAT_TIMEOUT}, which indicates the owning node has crashed.
    */
   private class LicenseUpdater implements Runnable {
      /**
       * Updates the license file in the dataspace.
       */
      @Override
      public void run() {
         Lock lock = Cluster.getInstance().getLock(LICENSE_LOCK_NAME);
         lock.lock();

         try {
            List<ClientInfo> toRemove = new ArrayList<>();

            for(Map.Entry<ClientInfo, ClusterLicense> entry : clusterLicenses.entrySet()) {
               final ClusterLicense license = entry.getValue();
               final long currentTime = System.currentTimeMillis();

               // Refresh the heartbeat timestamp for sessions owned by this node.
               if(license.getNodeId().equals(uuid)) {
                  license.setTimestamp(currentTime);
                  clusterLicenses.put(entry.getKey(), license);
               }

               // If no heartbeat received in over 3 minutes, assume the node died
               // and free the license for other nodes to use.
               if((currentTime - license.getTimestamp()) > HEARTBEAT_TIMEOUT) {
                  toRemove.add(entry.getKey());
               }
            }

            toRemove.forEach(clusterLicenses::remove);
         }
         finally {
            lock.unlock();
         }
      }
   }

   /**
    * Bean type class representing a unique session licenses in a cluster. The
    * license is uniquely identified together by the cluster node id (UUID) and
    * the principal object.
    */
   private static class ClusterLicense implements Serializable {
      public ClusterLicense(SRPrincipal srPrincipal, UUID uuid, long timestamp) {
         this.srPrincipal = srPrincipal;
         this.nodeId = uuid;
         this.timestamp = timestamp;
      }

      /**
       * Gets the srPrincipal associated with this license.
       */
      public SRPrincipal getPrincipal() {
         return srPrincipal;
      }

      /**
       * Gets the last update timestamp of this license.
       */
      public synchronized long getTimestamp() {
         return timestamp;
      }

      /**
       * Sets the last update timestamp for this license.
       * @param timestamp The update timestamp.
       */
      public synchronized void setTimestamp(long timestamp) {
         this.timestamp = timestamp;
      }

      /**
       * Gets the cluster node ID associated with this license.
       */
      public UUID getNodeId() {
         return nodeId;
      }

      @Override
      public boolean equals(Object o) {
         if(o instanceof ClusterLicense license) {
            return license.getNodeId().equals(nodeId) &&
               license.getPrincipal().equals(srPrincipal);
         }

         return false;
      }

      @Override
      public int hashCode() {
         return 17 + nodeId.hashCode() + srPrincipal.hashCode();
      }

      @Override
      public String toString() {
         String ret;
         boolean internal =
            Boolean.parseBoolean(srPrincipal.getProperty("__internal__"));

         if(internal) {
            // Set the __internal__ property to false to get toIdentifier() to
            // actually generate a unique identifier
            srPrincipal.setProperty("__internal__", "false");
            ret = srPrincipal.toIdentifier() + "," + nodeId + "," + timestamp;
            srPrincipal.setProperty("__internal__", "true");
         }
         else {
            ret = srPrincipal.toIdentifier() + "," + nodeId + "," + timestamp;
         }

         return ret;
      }

      private final SRPrincipal srPrincipal;
      private final UUID nodeId;

      private long timestamp;
   }
}
