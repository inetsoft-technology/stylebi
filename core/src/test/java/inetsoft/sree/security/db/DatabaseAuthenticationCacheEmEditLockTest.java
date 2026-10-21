/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.security.db;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.management.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static inetsoft.sree.security.db.DatabaseCacheTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78793: an EM edit of a provider (AuthenticationProviderService.editAuthenticationProvider)
 * holds the chain's write lock while it tears the edited provider down, saves the chain (which
 * takes the chain's reload lock) and refreshes the new provider (which takes its cache locks). So
 * no thread may wait for the chain's read lock while it holds a provider's cache locks or the
 * chain's reload lock. Each test fails with the deadlocked threads instead of hanging.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DatabaseAuthenticationCacheEmEditLockTest {
   @BeforeEach
   void setUp() throws Exception {
      support = new DatabaseCacheTestSupport();
      support.setUp("3600000");
   }

   @AfterEach
   void tearDown() {
      // after a deadlock the chain's locks are never released: detach the chain from the engine
      // so that the next init and the context close don't tear it down and hang the fork
      if(deadlocked) {
         try {
            Field field = SecurityEngine.class.getDeclaredField("provider");
            field.setAccessible(true);
            field.set(SecurityEngine.getSecurity(), null);
         }
         catch(Exception ignore) {
         }
      }
      else {
         // wait for the refreshes the test queued, so they don't run into the next test or class
         try {
            refreshExecutor().submit(() -> { }).get(10, TimeUnit.SECONDS);
         }
         catch(Exception ignore) {
         }

         support.tearDown();
      }
   }

   /**
    * A refresh queued by another node's reload runs while an EM edit of the same provider on
    * this node (the host) holds the chain's write lock.
    */
   @Test
   void queuedReloadRefreshDuringAnEmEditOfTheSameProvider() throws Exception {
      AuthenticationChain host = support.initEngine(List.of(support.provider("emq78793", NO_ALICE)));
      DataSpace.getDataSpace().removeChangeListener(null, "authc-chain.json", host);
      DatabaseAuthenticationProvider p0 = find(host, "emq78793");
      p0.getUsers();
      assertTrue(await(p0::isCacheInitialized, 5_000));

      CountDownLatch busy = new CountDownLatch(1);
      refreshExecutor().execute(() -> {
         try {
            busy.await(20, TimeUnit.SECONDS);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      });

      // another node saves a change to e: this node's reload queues e's refresh
      AuthenticationChain writer = new AuthenticationChain();
      Thread.sleep(20);
      writer.setProviders(new ArrayList<>(List.of(support.provider("emq78793", ALL_USERS))));
      writer.tearDown();
      Method reload = SecurityChain.class.getDeclaredMethod("loadConfiguration");
      reload.setAccessible(true);
      reload.invoke(host);

      // the EM edit of e on this node, as AuthenticationProviderService.editAuthenticationProvider
      CountDownLatch set = new CountDownLatch(1);
      Thread em = new Thread(() -> {
         host.writeLock();

         try {
            List<AuthenticationProvider> list = host.getProviders();
            DatabaseAuthenticationProvider pn = support.provider("emq78793", NO_ALICE);
            AuthenticationProvider old = list.set(0, pn);
            old.tearDown();
            host.setProviders(list);
            set.countDown();
            Thread.sleep(1000);
            pn.clearCache();
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
         finally {
            host.writeUnlock();
         }
      }, "emEdit");
      em.setDaemon(true);
      em.start();
      assertTrue(set.await(5, TimeUnit.SECONDS));
      busy.countDown();

      assertFinished(em);
   }

   /**
    * A request reads a provider whose cache isn't connected yet while an EM edit of that provider
    * holds the chain's write lock: the request connects the cache (holding its service lock) and
    * the edit tears the provider down (which closes the cache under the same lock).
    */
   @Test
   void coldGetterDuringAnEmEditOfTheSameProvider() throws Exception {
      AuthenticationChain host = support.initEngine(List.of(support.provider("emc78793", ALL_USERS)));
      DataSpace.getDataSpace().removeChangeListener(null, "authc-chain.json", host);
      DatabaseAuthenticationProvider cold = find(host, "emc78793");
      ReentrantReadWriteLock rw = chainLock(host);
      CountDownLatch locked = new CountDownLatch(1);
      Thread getter = new Thread(() -> {
         try {
            locked.await(5, TimeUnit.SECONDS);
            cold.getUsers();
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      }, "coldGetter");
      getter.setDaemon(true);

      Thread em = new Thread(() -> {
         host.writeLock();

         try {
            locked.countDown();
            // let the getter connect the cache while the write lock is held
            long end = System.currentTimeMillis() + 2_000;

            while(!rw.hasQueuedThread(getter) && getter.isAlive() &&
                  System.currentTimeMillis() < end)
            {
               Thread.sleep(10);
            }

            Thread.sleep(200);
            List<AuthenticationProvider> list = host.getProviders();
            DatabaseAuthenticationProvider pn = support.provider("emc78793", NO_ALICE);
            AuthenticationProvider old = list.set(0, pn);
            old.tearDown();
            host.setProviders(list);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
         finally {
            host.writeUnlock();
         }
      }, "emEdit");
      em.setDaemon(true);
      em.start();
      getter.start();

      assertFinished(em, getter);
   }

   /**
    * Another node's save reloads this node's chain while an EM edit on this node holds the
    * chain's write lock: the reload replaces the providers while it holds the chain's reload
    * lock, and the edit's save waits for that lock.
    */
   @Test
   void chainReloadDuringAnEmEdit() throws Exception {
      AuthenticationChain host = support.initEngine(List.of(support.provider("emr78793", ALL_USERS)));
      DataSpace.getDataSpace().removeChangeListener(null, "authc-chain.json", host);
      ReentrantReadWriteLock rw = chainLock(host);

      // another node saves a change to the chain
      AuthenticationChain writer = new AuthenticationChain();
      Thread.sleep(20);
      writer.setProviders(new ArrayList<>(List.of(support.provider("emr78793", NO_ALICE))));
      writer.tearDown();
      Method reload = SecurityChain.class.getDeclaredMethod("loadConfiguration");
      reload.setAccessible(true);
      AtomicBoolean reloaded = new AtomicBoolean();
      Thread reloader = new Thread(() -> {
         try {
            reload.invoke(host);
            reloaded.set(true);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      }, "chainReload");
      reloader.setDaemon(true);

      // the EM edit on this node, as AuthenticationProviderService.editAuthenticationProvider
      Thread em = new Thread(() -> {
         host.writeLock();

         try {
            List<AuthenticationProvider> list = host.getProviders();
            reloader.start();
            // let the reload take its lock and replace the providers while the write lock is held
            long end = System.currentTimeMillis() + 2_000;

            while(!rw.hasQueuedThread(reloader) && reloader.isAlive() &&
                  System.currentTimeMillis() < end)
            {
               Thread.sleep(10);
            }

            Thread.sleep(200);
            host.setProviders(list);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
         finally {
            host.writeUnlock();
         }
      }, "emEdit");
      em.setDaemon(true);
      em.start();

      assertFinished(em, reloader);
      assertTrue(reloaded.get(), "the reload failed");
   }

   private void assertFinished(Thread... threads) throws InterruptedException {
      for(Thread thread : threads) {
         thread.join(8_000);
      }

      long[] ids = newDeadlocks();
      deadlocked = ids != null || Arrays.stream(threads).anyMatch(Thread::isAlive);
      assertNull(ids, "deadlocked threads: " + describe(ids));

      for(Thread thread : threads) {
         assertFalse(thread.isAlive(), thread.getName() + " never finished");
      }
   }

   // the threads deadlocked by an earlier failed test of this class stay deadlocked
   private long[] newDeadlocks() {
      long[] ids = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
      long[] result = ids == null ? new long[0] :
         Arrays.stream(ids).filter(id -> !earlierDeadlocks.contains(id)).toArray();

      if(ids != null) {
         Arrays.stream(ids).forEach(earlierDeadlocks::add);
      }

      return result.length == 0 ? null : result;
   }

   private static String describe(long[] ids) {
      if(ids == null) {
         return "none";
      }

      StringBuilder text = new StringBuilder();

      for(ThreadInfo info : ManagementFactory.getThreadMXBean().getThreadInfo(ids)) {
         text.append(info.getThreadName()).append(" waits for ").append(info.getLockName())
            .append(" held by ").append(info.getLockOwnerName()).append("; ");
      }

      return text.toString();
   }

   private static ReentrantReadWriteLock chainLock(AuthenticationChain chain) throws Exception {
      Field field = SecurityChain.class.getDeclaredField("readWriteLock");
      field.setAccessible(true);
      return (ReentrantReadWriteLock) field.get(chain);
   }

   private static ExecutorService refreshExecutor() throws Exception {
      Field field = AuthenticationChain.class.getDeclaredField("RELOAD_EXECUTOR");
      field.setAccessible(true);
      return (ExecutorService) field.get(null);
   }

   private DatabaseCacheTestSupport support;
   private boolean deadlocked;
   private static final Set<Long> earlierDeadlocks = new HashSet<>();
}
