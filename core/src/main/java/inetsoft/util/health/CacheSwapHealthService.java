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
package inetsoft.util.health;

import inetsoft.util.SingletonManager;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class CacheSwapHealthService implements AutoCloseable {
   public CacheSwapHealthService() {
      executor.scheduleAtFixedRate(this::checkHealth, 180L, 30L, TimeUnit.SECONDS);
   }

   public static CacheSwapHealthService getInstance() {
      return SingletonManager.getInstance(CacheSwapHealthService.class);
   }

   public CacheSwapStatus getStatus() {
      return status.get();
   }

   @Override
   public void close() throws Exception {
      executor.shutdown();
   }

   private void checkHealth() {
      status.set(new CacheSwapStatus(status.get()));
   }

   private final AtomicReference<CacheSwapStatus> status =
      new AtomicReference<>(new CacheSwapStatus());
   private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

}
