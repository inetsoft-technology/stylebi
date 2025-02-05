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
import inetsoft.util.SingletonManager;
import inetsoft.util.health.HealthStatus;

import java.rmi.RemoteException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CloudRunnerServerScheduleClient extends ScheduleClient {
   @Override
   public void startServer() throws Exception {
      getSchedule().start();
   }

   @Override
   public void startServer(long timeout, TimeUnit timeoutUnit) throws Exception {
      getSchedule().start();
   }

   @Override
   public void stopServer() throws RemoteException {
      getSchedule().stop();
   }

   @Override
   public void stopServer(String server) throws RemoteException {
      stopServer();
   }

   @Override
   protected Schedule getSchedule(String server) {
      return getSchedule();
   }

   @Override
   public boolean isRunning() {
      return getSchedule().isLocalServerRunning();
   }

   @Override
   public boolean isRunning(String server) {
      return isRunning();
   }

   @Override
   public boolean isReady() {
      return getSchedule().isLocalServerRunning();
   }

   @Override
   public boolean isReady(String server) {
      return isReady();
   }

   @Override
   public String getSchedulerServer() {
      Cluster cluster = Cluster.getInstance();

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
      return Optional.of(getSchedule().getHealth());
   }

   @Override
   public Optional<HealthStatus> getHealthStatus(String server) throws RemoteException {
      return Optional.of(getSchedule().getHealth());
   }

   private ScheduleServer getSchedule() {
      return ScheduleServer.getInstance();
   }
}
