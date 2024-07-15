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
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.util.Catalog;
import inetsoft.util.DataSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * SessionLicenseManager for clusters to manage concurrent session keys.
 */
public class ConcurrentSessionClusterService extends AbstractSessionService {
   ConcurrentSessionClusterService(Supplier<Integer> maxSessions) {
      this(maxSessions, true, "cluster_file.dat");
   }

   /**
    * Constructs a session license manager specific for a cluster server.
    * @param maxSessions Maximum # of concurrent sessions.
    * @param logoutAfterFail Whether to log the user out after a failure.
    * @param filename Filename used to store licenses.
    */
   ConcurrentSessionClusterService(Supplier<Integer> maxSessions, boolean logoutAfterFail,
                                   String filename)
   {
      this.clusterLicenses = new HashMap<>();
      this.maxSessions = maxSessions;
      this.logoutAfterFail = logoutAfterFail;
      this.updater = new LicenseUpdater(filename);
      this.runner = Executors.newSingleThreadScheduledExecutor();

      // Background thread which updates the cluster file. Will automatically
      // run every minute, but can also be invoked manually to force an update.
      runner.scheduleAtFixedRate(updater, 1, 60, TimeUnit.SECONDS);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public synchronized void newSession(SRPrincipal srPrincipal) {
      if(!clusterLicenses.containsKey(srPrincipal)) {
         if(clusterLicenses.size() == getMaxSessions()) {
            sessionError(srPrincipal);
         }
         else {
            ClusterLicense license = new ClusterLicense(srPrincipal,
               uuid, System.currentTimeMillis());

            clusterLicenses.put(srPrincipal, license);
            updateClusterFile();
         }
      }

      // 2nd check needed because other nodes may be modifying cluster file
      if(clusterLicenses.size() > getMaxSessions()) {
         if(clusterLicenses.remove(srPrincipal) != null) {
            updateClusterFile();
            sessionError(srPrincipal);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public synchronized void releaseSession(SRPrincipal srPrincipal) {
      clusterLicenses.remove(srPrincipal);
      updateClusterFile();
   }

   @Override
   public synchronized Set<SRPrincipal> getActiveSessions() {
      return Collections.unmodifiableSet(new HashSet<>(clusterLicenses.keySet()));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public synchronized void dispose() {
      super.dispose();
      runner.shutdownNow();
      clusterLicenses.clear();
      DataSpace dataSpace = DataSpace.getDataSpace();
      dataSpace.delete(null, updater.getClusterFilename());
   }

   private void updateClusterFile() {
      runner.execute(updater);
   }

   private void sessionError(SRPrincipal srPrincipal) {
      if(logoutAfterFail) {
         SUtil.logout(srPrincipal);
      }

      final Catalog catalog = Catalog.getCatalog(srPrincipal);
      final String msg = String.format("%s %s",
                                       catalog.getString("common.sessionsExceed"),
                                       catalog.getString("common.contactAdmin"));
      throw new LicenseException(msg);
   }

   private int getMaxSessions() {
      return maxSessions.get();
   }

   private static Map<SRPrincipal, ClusterLicense> readFromFile(InputStream is)
      throws IOException {
      BufferedReader br = new BufferedReader(new InputStreamReader(is));
      Map<SRPrincipal, ClusterLicense> set = new HashMap<>();

      String line;
      while((line = br.readLine()) != null) {
         ClusterLicense license = ClusterLicense.fromString(line);
         set.put(license.getPrincipal(), license);
      }

      return set;
   }

   private static void writeToFile(OutputStream os,
                                   Map<SRPrincipal, ClusterLicense> licenses)
      throws IOException {
      PrintWriter pw = new PrintWriter(os);

      for(ClusterLicense license : licenses.values()) {
         pw.println(license.toString());
      }

      pw.flush();
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(ConcurrentSessionClusterService.class);

   private static final long HEARTBEAT_TIMEOUT = 180000L;

   private final Map<SRPrincipal, ClusterLicense> clusterLicenses;
   private final LicenseUpdater updater;
   private final ScheduledExecutorService runner;
   private final UUID uuid = UUID.randomUUID();
   private final Supplier<Integer> maxSessions;
   private final boolean logoutAfterFail;

   /**
    * Runnable which keeps track of a file in the dataspace storing all used
    * ClusterLicenses by all nodes in a cluster. Every 60 seconds, this class
    * should update the timestamps of active sessions in this file, and clean
    * up any licenses where a timestamp is older than 60 seconds. This allows
    * licenses to be reused in the event of a node shutdown.
    */
   private class LicenseUpdater implements Runnable {

      public LicenseUpdater(String clusterFilename) {
        this.clusterFilename = clusterFilename;
      }

      public String getClusterFilename() {
         return clusterFilename;
      }

      /**
       * Updates the license file in the dataspace.
       */
      @Override
      public void run() {
         DataSpace dataSpace = DataSpace.getDataSpace();

         try(InputStream is = dataSpace.getInputStream(null, clusterFilename)) {
            if(is != null) {
               Map<SRPrincipal, ClusterLicense> fileMap = readFromFile(is);
               syncClusterState(fileMap);
            }
         }
         catch(IOException e) {
            LOG.error("Failed to read cluster file: " + clusterFilename, e);
         }

         try {
            HashMap<SRPrincipal, ClusterLicense> copy;

            // defensive copy
            synchronized(ConcurrentSessionClusterService.this) {
               copy = new HashMap<>(clusterLicenses);
            }

            dataSpace.withOutputStream(null, clusterFilename, os -> writeToFile(os, copy));
         }
         catch(Throwable e) {
            LOG.error("Failed to write cluster file: " + clusterFilename, e);
         }
      }

      /**
       * Updates in-memory representation of cluster state.
       * @param clusterMap The set of session licenses known to the cluster
       */
      private void syncClusterState(Map<SRPrincipal, ClusterLicense> clusterMap) {
         synchronized(ConcurrentSessionClusterService.this) {
            // Update timestamps for licenses not used by this node
            for(Map.Entry<SRPrincipal, ClusterLicense> entry : clusterMap.entrySet()) {
               ClusterLicense clusterLicense = entry.getValue();

               if(!clusterLicense.getNodeId().equals(uuid)) {
                  clusterLicenses.put(entry.getKey(), clusterLicense);
               }
            }

            Iterator<Map.Entry<SRPrincipal, ClusterLicense>> iterator =
               clusterLicenses.entrySet().iterator();

            while(iterator.hasNext()) {
               final ClusterLicense license = iterator.next().getValue();
               final long currentTime = System.currentTimeMillis();

               // Remove licenses that have been discarded by other nodes.
               if(!license.getNodeId().equals(uuid) &&
                  !clusterMap.containsValue(license)) {
                  iterator.remove();
               }
               // Update timestamps for this node's licenses
               else if(license.getNodeId().equals(uuid)) {
                  license.setTimestamp(currentTime);
               }

               // If no heartbeat received in over 3 minutes, assume the node died
               // and free the license for other nodes to use.
               if((currentTime - license.getTimestamp()) > HEARTBEAT_TIMEOUT) {
                  iterator.remove();
               }
            }
         }
      }

      private final String clusterFilename;
   }

   /**
    * Bean type class representing a unique session licenses in a cluster. The
    * license is uniquely identified together by the cluster node id (UUID) and
    * the principal object.
    */
   private static class ClusterLicense {
      public ClusterLicense(SRPrincipal srPrincipal) {
         this(srPrincipal, UUID.randomUUID(), System.currentTimeMillis());
      }

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

      /**
       * Constructs a ClusterLicense from its string representation.
       * @param val The string representation.
       * @return A ClusterLicense object.
       */
      public static ClusterLicense fromString(String val) {
         String[] parts = val.split(",");

         return new ClusterLicense(SRPrincipal.createFromID(parts[0]),
            UUID.fromString(parts[1]),
            Long.parseLong(parts[2]));
      }

      @Override
      public boolean equals(Object o) {
         if(o instanceof ClusterLicense) {
            ClusterLicense license = (ClusterLicense) o;

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
            Boolean.valueOf(srPrincipal.getProperty("__internal__"));

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
