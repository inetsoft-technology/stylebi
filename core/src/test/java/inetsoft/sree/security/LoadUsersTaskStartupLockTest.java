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
package inetsoft.sree.security;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MockCluster;
import inetsoft.storage.KeyValueEngine;
import inetsoft.test.TestKeyValueEngine;
import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76975: the users store's load task checks the named-user license in {@code validate}. In
 * the enterprise license strategy that check loads the license under the strategy's lock, and the
 * license load reads {@code SreeEnv}, i.e. looks up {@code PropertiesEngine}. On a node whose main
 * thread is still refreshing the context, main holds the singleton lock while it creates
 * {@code PropertiesEngine}, and then takes the license lock in {@code SecurityEngine.doInit}. A
 * users load that runs in between deadlocks with main.
 *
 * <p>The real {@code LoadUsersTask} runs on a thread named like the service thread. The license
 * manager is a stand-in whose named-user count takes lock L and then looks up a bean that main is
 * still creating, as the license load does. Main holds the singleton lock, creates the engine,
 * then that bean, and then takes L, as {@code SecurityEngine.doInit} does.</p>
 */
@Tag("core")
class LoadUsersTaskStartupLockTest {
   @BeforeEach
   void saveContext() {
      previousContext = ConfigurationContext.getContext().getApplicationContext();
   }

   @AfterEach
   void restoreContext() {
      ConfigurationContext.getContext().setApplicationContext(previousContext);
   }

   @Test
   void usersLoadDoesNotTakeTheLicenseLockBeforeSecurityEngineExists() throws Exception {
      ReentrantLock licenseLock = new ReentrantLock();
      KeyValueEngine engine = new TestKeyValueEngine();
      engine.put(STORE, "admin~;~host-org", new FSUser(new IdentityID("admin", "host-org")));
      LicenseManager licenseManager = mock(LicenseManager.class);
      when(licenseManager.getNamedUserCount()).thenAnswer(invocation -> {
         licenseLock.lock();

         try {
            ConfigurationContext.getContext().getSpringBean(InCreation.class);
            return 0;
         }
         finally {
            licenseLock.unlock();
         }
      });

      CompletableFuture<Void> load = new CompletableFuture<>();
      Result result = new Result();

      try(StartupContext context = new StartupContext(() -> {
         ApplicationContext ctx = ConfigurationContext.getContext().getApplicationContext();
         ctx.getBean(LicenseManager.class);
         Cluster cluster = ctx.getBean(Cluster.class);
         Thread worker = startLoad(load);
         awaitWaiting(worker, load, true);
         ctx.getBean(KeyValueEngine.class);
         ctx.getBean(InCreation.class);

         try {
            // SecurityEngine.doInit -> LicenseManager.getNamedUserCount -> the license lock
            result.mainGotLicenseLock = licenseLock.tryLock(3, TimeUnit.SECONDS);

            if(result.mainGotLicenseLock) {
               licenseLock.unlock();
            }
         }
         catch(InterruptedException e) {
            throw new RuntimeException(e);
         }

         result.map = Map.copyOf(cluster.getReplicatedMap("inetsoft.storage.kv." + STORE));
         return new Object();
      }))
      {
         context.registerBean("cluster", Cluster.class, MockCluster::new, lazy());
         context.registerBean("keyValueEngine", KeyValueEngine.class, () -> engine, lazy());
         context.registerBean(
            "licenseManager", LicenseManager.class, () -> licenseManager, lazy());
         context.registerBean("inCreation", InCreation.class, () -> {
            // the license load asks for this bean while main creates it
            awaitWaiting(worker, load, false);
            return new InCreation();
         }, lazy());
         // defined, but main has not created it yet
         context.registerBean(
            "securityEngine", SecurityEngine.class, () -> mock(SecurityEngine.class), lazy());
         ConfigurationContext.getContext().setApplicationContext(context);
         context.refresh();

         load.get(10, TimeUnit.SECONDS);
         assertTrue(result.mainGotLicenseLock,
                    "the users load held the license lock while it waited for the singleton " +
                       "lock that main holds (ABBA with SecurityEngine.doInit)");
         assertFalse(result.map.isEmpty(), "the users load did not fill the store");
      }
   }

   private Thread startLoad(CompletableFuture<Void> load) {
      Thread thread = new Thread(() -> {
         try {
            newLoadUsersTask().run();
            load.complete(null);
         }
         catch(Throwable e) {
            load.completeExceptionally(e);
         }
      }, "cluster-service-" + STORE);
      thread.setDaemon(true);
      thread.start();
      worker = thread;
      return thread;
   }

   private static Runnable newLoadUsersTask() throws Exception {
      Class<?> type =
         Class.forName("inetsoft.sree.security.FileAuthenticationProvider$LoadUsersTask");
      Constructor<?> constructor = type.getDeclaredConstructor(String.class);
      constructor.setAccessible(true);
      return (Runnable) constructor.newInstance(STORE);
   }

   /**
    * Waits until the worker waits or is done. Before main created the engine it waits in a timed
    * sleep; later only an untimed wait (the singleton lock) counts.
    */
   private static void awaitWaiting(Thread thread, Future<?> done, boolean timed) {
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

      while(thread != null && System.nanoTime() < end && !done.isDone()) {
         Thread.State state = thread.getState();

         if(state == Thread.State.WAITING || timed && state == Thread.State.TIMED_WAITING) {
            return;
         }

         Thread.onSpinWait();
      }
   }

   private static org.springframework.beans.factory.config.BeanDefinitionCustomizer lazy() {
      return bd -> bd.setLazyInit(true);
   }

   /**
    * Requests the starter bean from {@code onRefresh}, so main holds the singleton lock.
    */
   private final class StartupContext extends AnnotationConfigApplicationContext {
      StartupContext(java.util.function.Supplier<Object> starter) {
         registerBean(STARTER, Object.class, starter, lazy());
      }

      @Override
      protected void onRefresh() {
         super.onRefresh();
         getBean(STARTER);
      }
   }

   private static final class Result {
      volatile boolean mainGotLicenseLock;
      volatile Map<Object, Object> map = Map.of();
   }

   static final class InCreation {
   }

   private volatile Thread worker;
   private ApplicationContext previousContext;
   private static final String STORE = "defaultSecurityUsers";
   private static final String STARTER = "bug76975Starter";
}
