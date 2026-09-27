/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.storage;

import inetsoft.sree.internal.cluster.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.TestKeyValueEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bug #77198: overrides the {@code cluster} and {@code keyValueEngine} beans of
 * {@link BaseTestConfiguration} (list it after that class) so that a context can be restarted
 * as a node is after the {@code dataSpace} load timed out. The key-value engine is shared by all
 * the contexts and is not closed with them, so it keeps what the previous context stored, as the
 * persistent engine does across a restart; the blob bytes are kept in the {@code @SreeHome} of
 * the test class. The loads of {@code dataSpace} set with {@link #timeOutLoads(int)} before a
 * context starts time out in {@code LocalKeyValueStorage}, which logs the failure and leaves the
 * store empty. Later loads run normally.
 */
@Configuration
public class UnloadedDataSpaceTestConfiguration {
   public static final String STORE = "dataSpace";

   @Bean
   public Cluster cluster() {
      DataSpaceLoadTimesOutCluster cluster = new DataSpaceLoadTimesOutCluster(timeOutLoads);
      lastCluster = cluster;
      return cluster;
   }

   @Bean(destroyMethod = "")
   public KeyValueEngine keyValueEngine() {
      return ENGINE;
   }

   /**
    * Sets the number of {@code dataSpace} loads that time out in the next context.
    */
   public static void timeOutLoads(int count) {
      timeOutLoads = count;
   }

   /**
    * Gets the cluster of the context that started last, also when its start failed.
    */
   public static Cluster getLastCluster() {
      return lastCluster;
   }

   /**
    * Gets the number of {@code dataSpace} loads submitted to the cluster.
    */
   public static int getLoads(Cluster cluster) {
      return ((DataSpaceLoadTimesOutCluster) cluster).loads.get();
   }

   /**
    * Gets the number of {@code dataSpace} loads that timed out.
    */
   public static int getTimedOutLoads(Cluster cluster) {
      return ((DataSpaceLoadTimesOutCluster) cluster).timedOut.get();
   }

   /**
    * The key-value engine that outlives the contexts.
    */
   public static final KeyValueEngine ENGINE = new TestKeyValueEngine();
   private static volatile int timeOutLoads;
   private static volatile Cluster lastCluster;

   private static final class DataSpaceLoadTimesOutCluster extends MockCluster {
      DataSpaceLoadTimesOutCluster(int timeOutLoads) {
         this.timeOutLoads = timeOutLoads;
      }

      @Override
      public Future<?> submit(String serviceId, SingletonRunnableTask task) {
         if(task instanceof LoadKeyValueTask<?> load && STORE.equals(load.getId()) &&
            loads.incrementAndGet() <= timeOutLoads)
         {
            timedOut.incrementAndGet();
            return new TimedOutFuture();
         }

         return super.submit(serviceId, task);
      }

      private final int timeOutLoads;
      private final AtomicInteger loads = new AtomicInteger();
      private final AtomicInteger timedOut = new AtomicInteger();
   }

   /**
    * A future whose bounded {@code get} times out at once, as the 3-minute wait in
    * {@code LocalKeyValueStorage} does in the stalled cluster.
    */
   private static final class TimedOutFuture extends CompletableFuture<Void> {
      TimedOutFuture() {
         completeExceptionally(new TimeoutException());
      }

      @Override
      public Void get(long timeout, TimeUnit unit) throws TimeoutException {
         throw new TimeoutException();
      }
   }
}
