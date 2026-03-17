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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class IgniteScheduledExecutorServiceImpl implements IgniteScheduledExecutorService {
   IgniteScheduledExecutorServiceImpl() {
   }

   @Override
   public void init() throws Exception {
      executor = Executors.newSingleThreadScheduledExecutor();
      Cluster cluster = Cluster.getInstance();
      map = cluster.getMap("IgniteScheduledExecutorServiceCache");
      localMemberId = cluster.getLocalMember();
   }

   @Override
   public void execute() throws Exception {
      // This method is called by Ignite whenever the service is (re)deployed on this node,
      // which happens on every topology change that triggers service migration. Logging here
      // tells us (a) whether migration occurred at all, and (b) whether the distributed map
      // was populated at the time — an empty map would confirm the PME-timing hypothesis.
      // The local member ID lets you correlate this with cancel() on the node that lost the service.
      String localMember = localMemberId;

      if(map.isEmpty()) {
         LOG.warn("IgniteScheduledExecutorService.execute() called on node '{}' but the distributed " +
                  "scheduler map is empty — no periodic tasks will be rescheduled on this node. If a " +
                  "topology change just occurred, this may indicate execute() ran before the Partition " +
                  "Map Exchange completed and the map was not yet visible on this node.", localMember);
      }
      else {
         LOG.info("IgniteScheduledExecutorService.execute() called on node '{}'; rescheduling {} task(s): {}",
                  localMember, map.size(), map.keySet());
      }

      for(Map.Entry<String, ScheduledExecutorCommand> entry : map.entrySet()) {
         String id = entry.getKey();
         ScheduledExecutorCommand command = entry.getValue();

         if(command.period > 0) {
            scheduleCommandAtFixedRate(id, (Runnable) command.command, command.delay, command.period,
                                       command.unit);
         }
         else if(command.command instanceof Callable) {
            scheduleCommand(id, (Callable<?>) command.command, command.delay, command.unit);
         }
         else {
            scheduleCommand(id, (Runnable) command.command, command.delay, command.unit);
         }
      }
   }

   @Override
   public void cancel() {
      LOG.info("IgniteScheduledExecutorService.cancel() called on node '{}' — service is being " +
               "stopped or migrated due to a topology change.",
               localMemberId);
      shutdown();
   }

   @Override
   public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      String id = UUID.randomUUID().toString();
      map.put(id, new ScheduledExecutorCommand((Serializable) command, delay, 0, unit));
      return scheduleCommand(id, command, delay, unit);
   }

   @Override
   public <V> ScheduledFuture<V> schedule(Callable<V> command, long delay, TimeUnit unit) {
      String id = UUID.randomUUID().toString();
      map.put(id, new ScheduledExecutorCommand((Serializable) command, delay, 0, unit));
      return scheduleCommand(id, command, delay, unit);
   }

   @Override
   public void scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                                   TimeUnit unit)
   {
      String id = command.getClass().getName();

      if(map.putIfAbsent(id, new ScheduledExecutorCommand((Serializable) command, initialDelay, period, unit)) == null) {
         scheduleCommandAtFixedRate(id, command, initialDelay, period, unit);
      }
      else {
         // The distributed map already has an entry for this task. This is normal on secondary
         // pods — the first pod registered the task and it deduplicates by class name. The task
         // will only run if it was rescheduled via execute() when the service was last deployed.
         // If execute() was never called after a topology change, or saw an empty map, the task
         // may not be running on any node.
         LOG.debug("scheduleAtFixedRate: '{}' already registered in the distributed scheduler map " +
                   "— scheduling skipped (normal on secondary pods). If the executor service was " +
                   "recently redeployed, confirm that execute() was called and rescheduled this task.",
                   id);
      }
   }

   @Override
   public void scheduleWithId(String id, Runnable command, long delay, TimeUnit unit) {
      if(!map.containsKey(id)) {
         map.put(id, new ScheduledExecutorCommand((Serializable) command, delay, 0, unit));
         scheduleCommand(id, command, delay, unit);
      }
   }

   @Override
   public void shutdown() {
      executor.shutdown();
   }

   private ScheduledFuture<?> scheduleCommand(String id, Runnable command, long delay,
                                              TimeUnit unit)
   {
      executor.schedule(new RemoveCacheEntry(id), delay, unit);
      return executor.schedule(command, delay, unit);
   }

   private <V> ScheduledFuture<V> scheduleCommand(String id, Callable<V> command, long delay,
                                                  TimeUnit unit)
   {
      executor.schedule(new RemoveCacheEntry(id), delay, unit);
      return executor.schedule(command, delay, unit);
   }

   private ScheduledFuture<?> scheduleCommandAtFixedRate(String id, Runnable command,
                                                         long initialDelay,
                                                         long period,
                                                         TimeUnit unit)
   {
      return executor.scheduleAtFixedRate(command, initialDelay, period, unit);
   }

   private DistributedMap<String, ScheduledExecutorCommand> map;
   private ScheduledExecutorService executor;
   private String localMemberId;
   private static final Logger LOG = LoggerFactory.getLogger(IgniteScheduledExecutorServiceImpl.class);

   private static final class ScheduledExecutorCommand implements Serializable {
      public ScheduledExecutorCommand(Serializable command, long delay, long period, TimeUnit unit) {
         this.command = command;
         this.delay = delay;
         this.period = period;
         this.unit = unit;
      }

      private Serializable command;
      private long delay;
      private long period;
      private TimeUnit unit;
   }

   private final class RemoveCacheEntry implements Runnable, Serializable {
      public RemoveCacheEntry(String id) {
         this.id = id;
      }

      @Override
      public void run() {
         if(map != null) {
            map.remove(id);
         }
      }

      private final String id;
   }
}
