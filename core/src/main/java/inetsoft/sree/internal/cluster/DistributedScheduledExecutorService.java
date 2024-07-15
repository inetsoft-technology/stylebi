/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.internal.cluster;

import java.util.concurrent.*;

/**
 * Interface for classes that provided distributed, scheduled task execution across the cluster.
 */
public interface DistributedScheduledExecutorService {
   /**
    * Creates and executes a one-shot action that becomes enabled
    * after the given delay.
    *
    * @param command the task to execute
    * @param delay the time from now to delay execution
    * @param unit the time unit of the delay parameter
    * @return a ScheduledFuture representing pending completion of
    *         the task and whose {@code get()} method will return
    *         {@code null} upon completion
    * @throws RejectedExecutionException if the task cannot be
    *         scheduled for execution
    * @throws NullPointerException if command is null
    */
   ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

   /**
    * Creates and executes a one-shot action that becomes enabled
    * after the given delay.
    *
    * @param command the task to execute
    * @param delay the time from now to delay execution
    * @param unit the time unit of the delay parameter
    * @return a ScheduledFuture representing pending completion of
    *         the task and whose {@code get()} method will return
    *         {@code null} upon completion
    * @throws RejectedExecutionException if the task cannot be
    *         scheduled for execution
    * @throws NullPointerException if command is null
    */
   <V> ScheduledFuture<V> schedule(Callable<V> command, long delay, TimeUnit unit);

   /**
    * Creates and executes a periodic action that becomes enabled first
    * after the given initial delay, and subsequently with the given
    * period. Executions will commence after
    * {@code initialDelay} then {@code initialDelay+period}, then
    * {@code initialDelay + 2 * period}, and so on.
    * If any execution of this task
    * takes longer than its period, then subsequent execution will be skipped.
    *
    * @param command the task to execute
    * @param initialDelay the time to delay first execution
    * @param period the period between successive executions
    * @param unit the time unit of the initialDelay and period parameters
    * @return a ScheduledFuture representing pending completion of
    *         the task, and whose {@code get()} method will throw an
    *         exception upon cancellation
    * @throws RejectedExecutionException if the task cannot be
    *         scheduled for execution
    * @throws NullPointerException if command is null
    */
   ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                                          TimeUnit unit);

   /**
    * Initiates an orderly shutdown in which previously submitted
    * tasks are executed, but no new tasks will be accepted.
    * Invocation has no additional effect if already shut down.
    *
    * <p>This method does not wait for previously submitted tasks to
    * complete execution.
    */
   void shutdown();
}
