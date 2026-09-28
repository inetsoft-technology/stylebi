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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bug #76975: overrides the {@code cluster} and {@code keyValueEngine} beans of
 * {@link BaseTestConfiguration} (list it after that class) so that the node starts as it does
 * after the {@code sreeProperties} load timed out: the stored properties are in the key-value
 * engine, but the first load of {@code sreeProperties} times out in
 * {@code LocalKeyValueStorage}, which logs the failure and leaves the store empty. Later loads
 * run normally, so {@link #fill(Cluster)} does what the parked load does once it is released.
 */
@Configuration
public class UnloadedPropertiesTestConfiguration {
   public static final String STORE = "sreeProperties";

   @Bean
   public Cluster cluster() {
      return new FirstLoadTimesOutCluster();
   }

   @Bean
   public KeyValueEngine keyValueEngine() {
      KeyValueEngine engine = new TestKeyValueEngine();
      engine.put(STORE, "security.enabled", "true");
      engine.put(STORE, "password.encryption.key", STORED_ENCRYPTION_KEY);
      return engine;
   }

   /**
    * Runs the load of {@code sreeProperties} that was queued when the first load timed out, and
    * waits until it filled the store.
    */
   public static void fill(Cluster cluster) throws Exception {
      cluster.submit(STORE, new LoadKeyValueTask<String>(STORE)).get(10L, TimeUnit.SECONDS);
   }

   /**
    * Waits until the tasks already submitted to the {@code sreeProperties} service ran.
    */
   public static void flush(Cluster cluster) throws Exception {
      cluster.submit(STORE, (SingletonRunnableTask) () -> { }).get(10L, TimeUnit.SECONDS);
   }

   public static boolean firstLoadTimedOut(Cluster cluster) {
      return ((FirstLoadTimesOutCluster) cluster).timedOut.get();
   }

   /**
    * Not a valid encrypted key: only compared, never decrypted, while the store is unloaded.
    */
   public static final String STORED_ENCRYPTION_KEY = "c3RvcmVkLWtleS03Njk3NQ==";

   private static final class FirstLoadTimesOutCluster extends MockCluster {
      @Override
      public Future<?> submit(String serviceId, SingletonRunnableTask task) {
         if(task instanceof LoadKeyValueTask<?> load && STORE.equals(load.getId()) &&
            timedOut.compareAndSet(false, true))
         {
            return new TimedOutFuture();
         }

         return super.submit(serviceId, task);
      }

      private final AtomicBoolean timedOut = new AtomicBoolean();
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
