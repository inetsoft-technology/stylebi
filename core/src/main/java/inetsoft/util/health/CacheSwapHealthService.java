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
package inetsoft.util.health;

import inetsoft.util.swap.XSwapper;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Lazy
public class CacheSwapHealthService implements AutoCloseable {
   public CacheSwapHealthService(XSwapper swapper) {
      this.swapper = swapper;
      executor.scheduleAtFixedRate(this::checkHealth, 180L, 30L, TimeUnit.SECONDS);
   }

   public CacheSwapStatus getStatus() {
      return status.get();
   }

   @Override
   @PreDestroy
   public void close() throws Exception {
      executor.shutdown();
   }

   private void checkHealth() {
      status.set(new CacheSwapStatus(status.get(), swapper));
   }

   private final XSwapper swapper;
   private final AtomicReference<CacheSwapStatus> status =
      new AtomicReference<>(new CacheSwapStatus());
   private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "CacheSwapHealthChecker");
      t.setDaemon(true);
      return t;
   });

}
