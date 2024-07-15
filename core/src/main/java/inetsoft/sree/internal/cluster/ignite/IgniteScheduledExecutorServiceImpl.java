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
package inetsoft.sree.internal.cluster.ignite;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.*;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.resources.IgniteInstanceResource;

import javax.cache.Cache;
import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.*;

public class IgniteScheduledExecutorServiceImpl implements IgniteScheduledExecutorService {
   IgniteScheduledExecutorServiceImpl() {
   }

   @Override
   public void init() throws Exception {
      executor = Executors.newSingleThreadScheduledExecutor();

      CacheConfiguration<String, ScheduledExecutorCommand> cacheConfiguration =
         new CacheConfiguration<>("IgniteScheduledExecutorServiceCache");
      cacheConfiguration.setBackups(2);
      cacheConfiguration.setCacheMode(CacheMode.PARTITIONED);
      cacheConfiguration.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
      cacheConfiguration.setRebalanceMode(CacheRebalanceMode.SYNC);
      cacheConfiguration.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
      cache = ignite.getOrCreateCache(cacheConfiguration);
   }

   @Override
   public void execute() throws Exception {
      for(Cache.Entry<String, ScheduledExecutorCommand> entry : cache) {
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
      shutdown();
   }

   @Override
   public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      String id = UUID.randomUUID().toString();
      cache.put(id, new ScheduledExecutorCommand((Serializable) command, delay, 0, unit));
      return scheduleCommand(id, command, delay, unit);
   }

   @Override
   public <V> ScheduledFuture<V> schedule(Callable<V> command, long delay, TimeUnit unit) {
      String id = UUID.randomUUID().toString();
      cache.put(id, new ScheduledExecutorCommand((Serializable) command, delay, 0, unit));
      return scheduleCommand(id, command, delay, unit);
   }

   @Override
   public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                                                 TimeUnit unit)
   {
      String id = UUID.randomUUID().toString();
      cache.put(id, new ScheduledExecutorCommand((Serializable) command, initialDelay, period, unit));
      return scheduleCommandAtFixedRate(id, command, initialDelay, period, unit);
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

   @IgniteInstanceResource
   private Ignite ignite;
   private IgniteCache<String, ScheduledExecutorCommand> cache;
   private ScheduledExecutorService executor;

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
         if(cache != null) {
            cache.remove(id);
         }
      }

      private final String id;
   }
}