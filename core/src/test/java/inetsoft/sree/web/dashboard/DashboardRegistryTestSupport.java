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
package inetsoft.sree.web.dashboard;

import inetsoft.util.DataSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Teardown support for tests that refresh a Spring context per test method and write dashboard
 * registry files through the {@link DataSpace}.
 */
final class DashboardRegistryTestSupport {
   private DashboardRegistryTestSupport() {
   }

   /**
    * Bug #77748. Called from {@code @AfterEach}, before {@code @DirtiesContext} closes the
    * context. After it returns, a change event that is still queued on the data space's
    * BlobStorageEvent thread must not make a {@link DashboardRegistry} of this context look up a
    * Spring bean when it is delivered, because by then the next test's context may be installed
    * and refreshing. Must return in bounded time even if the event thread is busy, and must not
    * fail because of it.
    *
    * <p>Every registry the manager has cached is detached with {@link DashboardRegistry#clear()},
    * whose flag is the first thing the change listener checks, so a late delivery is a no-op.
    * The event thread is not drained: it may be busy past the close of the context, and an
    * event can still be on its way to it (the key-value storage hands it over on an OnDemand
    * thread). Queued events are not discarded, and listeners that are not a registry's are
    * kept. A registry that the manager evicted is already detached by the manager, and a test
    * that creates a registry outside the manager clears it itself.
    *
    * <p>clear() is synchronized. A registry still locked by a test thread that did not end (a
    * deadlock that failed the test) is detached by setting the flag directly, after a bounded
    * wait.
    *
    * @param registryManager the context's registry manager.
    * @param dataSpace       the context's data space. Not used, the registries are detached
    *                        instead of the data space's event thread being drained.
    */
   @SuppressWarnings("unchecked")
   static void quiesce(DashboardRegistryManager registryManager, DataSpace dataSpace) {
      Map<String, DashboardRegistry> cache = (Map<String, DashboardRegistry>)
         field(DashboardRegistryManager.class, "registries", registryManager);
      Map<DashboardRegistry, Thread> clearing = new LinkedHashMap<>();

      for(DashboardRegistry registry : new ArrayList<>(cache.values())) {
         Thread thread = new Thread(registry::clear, "DashboardRegistryTestSupport.clear");
         thread.setDaemon(true);
         thread.start();
         clearing.put(registry, thread);
      }

      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

      for(Map.Entry<DashboardRegistry, Thread> entry : clearing.entrySet()) {
         try {
            long millis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            entry.getValue().join(Math.max(millis, 1L));
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }

         if(entry.getValue().isAlive()) {
            LOG.warn("Dashboard registry {} is locked by another thread, it is detached without " +
                        "clear()", entry.getKey().getPath());
            setField(DashboardRegistry.class, "detached", entry.getKey(), true);
         }
      }
   }

   // the product classes expose neither the manager's registries nor a way to detach a
   // registry without its lock
   private static Object field(Class<?> type, String name, Object target) {
      try {
         Field field = type.getDeclaredField(name);
         field.setAccessible(true);
         return field.get(target);
      }
      catch(ReflectiveOperationException e) {
         throw new AssertionError(e);
      }
   }

   private static void setField(Class<?> type, String name, Object target, Object value) {
      try {
         Field field = type.getDeclaredField(name);
         field.setAccessible(true);
         field.set(target, value);
      }
      catch(ReflectiveOperationException e) {
         throw new AssertionError(e);
      }
   }

   private static final int TIMEOUT_SECONDS = 10;
   private static final Logger LOG = LoggerFactory.getLogger(DashboardRegistryTestSupport.class);
}
