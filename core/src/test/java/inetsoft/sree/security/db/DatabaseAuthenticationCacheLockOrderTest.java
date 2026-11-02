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

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static inetsoft.sree.security.db.DatabaseCacheTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78793: the cache refresh after a chain reload must not deadlock with a security engine
 * init. The engine holds its init lock while it tears the providers down, which takes
 * their cache locks; the refresh holds the cache locks while it looks the provider up for the
 * cache service, so that lookup must not take the init lock.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DatabaseAuthenticationCacheLockOrderTest {
   @BeforeEach
   void setUp() throws Exception {
      support = new DatabaseCacheTestSupport();
      support.setUp("3600000");
   }

   @AfterEach
   void tearDown() {
      // after a deadlock the init lock is never released: don't hang the fork on it
      if(initLock == null || !initLock.isLocked()) {
         support.tearDown();
      }
   }

   @Test
   void reloadRefreshDoesNotDeadlockWithEngineInit() throws Exception {
      AuthenticationChain host = support.initEngine(List.of(support.provider("lock78793", NO_ALICE)));
      DataSpace.getDataSpace().removeChangeListener(null, "authc-chain.json", host);
      DatabaseAuthenticationProvider p = find(host, "lock78793");
      p.getUsers();
      assertTrue(await(p::isCacheInitialized, 5_000));

      SecurityEngine engine = SecurityEngine.getSecurity();
      Field field = SecurityEngine.class.getDeclaredField("initLock");
      field.setAccessible(true);
      initLock = (ReentrantLock) field.get(engine);
      CountDownLatch locked = new CountDownLatch(1);
      CountDownLatch go = new CountDownLatch(1);

      // holds the init lock across the reload's refresh, then re-initializes the engine under
      // it, which tears the providers down while it holds the lock
      Thread initThread = new Thread(() -> {
         initLock.lock();

         try {
            locked.countDown();
            go.await(10, TimeUnit.SECONDS);
            // reentrant: the engine's own method takes the lock again
            engine.init();
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
         finally {
            initLock.unlock();
         }
      }, "engineInit");
      initThread.setDaemon(true);
      initThread.start();
      assertTrue(locked.await(5, TimeUnit.SECONDS));

      AuthenticationChain writer = new AuthenticationChain();

      try {
         Thread.sleep(20);
         writer.setProviders(new ArrayList<>(List.of(support.provider("lock78793", ALL_USERS))));
         Method reload = SecurityChain.class.getDeclaredMethod("loadConfiguration");
         reload.setAccessible(true);
         reload.invoke(host);

         // the refresh either finishes without the init lock, or (before the fix) waits for it
         // while it holds the new provider's cache locks
         await(() -> clusterLong("lock78793", "refreshSeq") > 0L ||
                  initLock.getQueueLength() > 0, 5_000);
         Thread.sleep(200);
      }
      finally {
         go.countDown();
         writer.tearDown();
      }

      initThread.join(10_000);
      long[] deadlocked = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
      assertNull(deadlocked, "deadlocked threads: " + Arrays.toString(deadlocked));
      assertFalse(initThread.isAlive(), "the engine init never finished");
      assertTrue(clusterLong("lock78793", "refreshSeq") > 0L, "the refresh never ran");
   }

   private DatabaseCacheTestSupport support;
   private ReentrantLock initLock;
}
