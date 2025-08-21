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

import inetsoft.sree.security.IdentityID;
import inetsoft.util.health.HealthStatus;
import inetsoft.web.admin.schedule.*;
import inetsoft.web.admin.server.ServerMetrics;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Date;

/**
 * Interface defining the exported methods of the schedule server.
 */
public interface Schedule extends Remote, TestableRemote {
   /**
    * Start the schedule server.
    */
   void start() throws RemoteException;

   /**
    * Stop the schedule server.
    */
   void stop() throws RemoteException;

   /**
    * Run a task now
    */
   void runNow(String task) throws RemoteException;

   /**
    * Run a task now with a specific user as the trigger.
    *
    * @param task the name of the task to run
    * @param triggerUser the user identity that is triggering the task
    */
   void runNow(String task, IdentityID triggerUser) throws RemoteException;

   /**
    * Stop a task now
    */
   void stopNow(String task) throws RemoteException;

   /**
    * Notifies the scheduler that a task has been added or modified.
    *
    * @param task the task that was added.
    */
   void addTask(ScheduleTask task) throws RemoteException;

   /**
    * Notifies the scheduler that a task has been removed.
    *
    * @param taskName the name of the task that was removed.
    */
   void removeTask(String taskName) throws RemoteException;

   /**
    * remove old task cache
    *
    * @param orgId org id.
    */
   void removeTaskCacheOfOrg(String orgId) throws RemoteException;

   /**
    * Gets the current status of all tasks.
    *
    * @return the task status.
    */
   TaskActivity[] getScheduleActivities() throws RemoteException;

   /**
    * Gets the time at which the scheduler was started.
    *
    * @return the start time.
    */
   Date getStartTime() throws RemoteException;

   /**
    * Verifies that this instance is running.
    *
    * @return "OK" if scheduler is running, null if it's starting, and
    * error message if the scheduler failed to start.
    */
   String ping() throws RemoteException;

   /**
    * Gets the health of this instance.
    *
    * @return the instance health.
    */
   HealthStatus getHealth() throws RemoteException;

   /**
    * Gets the metrics for the server.
    *
    * @param oldMetrics the previous server metrics.
    * @param timestamp  the timestamp to use as the current time in the metrics calculations.
    *
    * @return the server metrics.
    */
   ServerMetrics getServerMetrics(ServerMetrics oldMetrics, long timestamp, String address)
      throws RemoteException;

   /**
    * Gets the open and executing viewsheets on the scheduler.
    *
    * @return the viewsheets status.
    */
   ScheduleViewsheetsStatus getViewsheets() throws RemoteException;

   /**
    * Get the executing queries.
    */
   ScheduleQueriesStatus getQueries() throws RemoteException;
}
