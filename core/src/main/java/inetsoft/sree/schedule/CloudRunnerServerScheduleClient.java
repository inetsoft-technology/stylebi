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

package inetsoft.sree.schedule;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.health.HealthStatus;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CloudRunnerServerScheduleClient extends ScheduleClient {
   public CloudRunnerServerScheduleClient(ScheduleServer scheduleServer, Cluster cluster) {
      super(cluster);
      this.scheduleServer = scheduleServer;
   }

   @Override
   public void startServer() throws Exception {
      scheduleServer.start();
   }

   @Override
   public void startServer(long timeout, TimeUnit timeoutUnit) throws Exception {
      scheduleServer.start();
   }

   @Override
   public void stopServer() throws RemoteException {
      try {
         Scheduler.disposeScheduler();
      }
      catch(SchedulerException e) {
         LOG.error("Failed to stop scheduler", e);
      }
   }

   @Override
   public void stopServer(String server) throws RemoteException {
      stopServer();
   }

   @Override
   protected Schedule getSchedule(String server) {
      return scheduleServer;
   }

   @Override
   public boolean isRunning() {
      return scheduleServer.isLocalServerRunning();
   }

   @Override
   public boolean isRunning(String server) {
      return isRunning();
   }

   @Override
   public boolean isReady() {
      return scheduleServer.isLocalServerRunning();
   }

   @Override
   public boolean isReady(String server) {
      return isReady();
   }

   @Override
   public String getSchedulerServer() {
      return cluster.getClusterNodeHost(cluster.getLocalMember());
   }

   @Override
   public String[] getScheduleServers() {
      String schedulerServer = getSchedulerServer();

      if(schedulerServer != null) {
         return new String[] { schedulerServer };
      }

      return new String[0];
   }

   /**
    * Lists every non-client cluster node whose scheduler has started, not only the serving node,
    * because in this mode each node runs its own in-process scheduler. The serving node is always
    * listed first, as {@link #getScheduleServers()} does; the other nodes follow in host order. A
    * remote node that has not published a scheduler start time has no scheduler running and is
    * not listed.
    */
   @Override
   public Map<String, Date> getScheduleStartDates() {
      Map<String, Date> startDates = new LinkedHashMap<>();
      String localServer = getSchedulerServer();

      if(localServer != null) {
         startDates.put(localServer, getScheduleStartDate(localServer));
      }

      Set<String> nodes = cluster.getClusterNodes();

      if(nodes == null) {
         return startDates;
      }

      Map<String, Date> remoteDates = new TreeMap<>();

      for(String node : nodes) {
         if(node.equals(cluster.getLocalMember())) {
            continue;
         }

         String host = cluster.getClusterNodeHost(node);
         Object startTime = cluster.getClusterNodeProperty(node, Scheduler.SCHEDULER_START_TIME);

         if(host != null && !startDates.containsKey(host) && startTime instanceof Number) {
            remoteDates.put(host, new Date(((Number) startTime).longValue()));
         }
      }

      startDates.putAll(remoteDates);
      return startDates;
   }

   @Override
   public boolean isCluster() {
      return false;
   }

   @Override
   public boolean isCloud() {
      return true;
   }

   @Override
   public boolean isAutoStart() {
      return false;
   }

   @Override
   public Optional<HealthStatus> getHealthStatus() throws RemoteException {
      return Optional.of(scheduleServer.getHealth());
   }

   @Override
   public Optional<HealthStatus> getHealthStatus(String server) throws RemoteException {
      return Optional.of(scheduleServer.getHealth());
   }

   private final ScheduleServer scheduleServer;
   private static final Logger LOG = LoggerFactory.getLogger(CloudRunnerServerScheduleClient.class);
}
