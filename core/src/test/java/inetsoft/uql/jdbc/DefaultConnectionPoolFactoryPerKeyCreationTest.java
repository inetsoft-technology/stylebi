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

package inetsoft.uql.jdbc;

import com.zaxxer.hikari.HikariConfig;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81417: building the connection pool of an unreachable data source (a blocking probe
 * connect plus pool start) held the lock of the whole pool map, so every other data source's
 * pool request queued behind it. The creation is now serialized per data source only.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DefaultConnectionPoolFactoryPerKeyCreationTest {
   /** A factory whose pool creation for data source "A" blocks until released. */
   private static final class BlockingFactory extends DefaultConnectionPoolFactory {
      @Override
      HikariConfig createDataSourceConfig(JDBCDataSource ds, boolean forceJdbc4) {
         creations.merge(ds.getFullName(), 1, Integer::sum);

         if("A".equals(ds.getFullName())) {
            startedA.countDown();

            try {
               releaseA.await(30, TimeUnit.SECONDS);
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
               throw new IllegalStateException(e);
            }
         }

         HikariConfig config = new HikariConfig();
         config.setDataSource(mock(DataSource.class));
         config.setMaximumPoolSize(1);
         config.setMinimumIdle(0);
         // no connection is made while the pool starts
         config.setInitializationFailTimeout(-1);
         return config;
      }

      final CountDownLatch startedA = new CountDownLatch(1);
      final CountDownLatch releaseA = new CountDownLatch(1);
      final Map<String, Integer> creations = new ConcurrentHashMap<>();
   }

   private static JDBCDataSource dataSource(String name) {
      // a mock: the real accessors of a data source need the credential service bean
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn(name);
      when(ds.getURL()).thenReturn("jdbc:test:" + name);
      when(ds.getDriver()).thenReturn("test.Driver");
      return ds;
   }

   private final ExecutorService executor = Executors.newCachedThreadPool();
   private final BlockingFactory factory = new BlockingFactory();

   @AfterEach
   void cleanUp() throws Exception {
      factory.releaseA.countDown();
      executor.shutdownNow();
      factory.closeAllConnectionsPools();
   }

   @Test
   void aBlockedPoolCreationForOneDataSourceDoesNotDelayAnother() throws Exception {
      Future<DataSource> a = executor.submit(
         () -> factory.getConnectionPool(dataSource("A"), null));
      if(!factory.startedA.await(10, TimeUnit.SECONDS)) {
         String why = "still running";

         if(a.isDone()) {
            try {
               a.get();
            }
            catch(Exception e) {
               why = e.toString() + " / " + e.getCause();
            }
         }

         fail("A's creation did not start: " + why);
      }

      // A is still blocked inside its creation: B must neither wait for it nor fail
      Future<DataSource> b = executor.submit(
         () -> factory.getConnectionPool(dataSource("B"), null));
      assertNotNull(b.get(5, TimeUnit.SECONDS), "B's pool was created while A is blocked");
      assertFalse(a.isDone(), "A is still blocked");

      // and B's pool is reusable, not just created
      assertSame(b.get(), factory.getConnectionPool(dataSource("B"), null));

      factory.releaseA.countDown();
      assertNotNull(a.get(10, TimeUnit.SECONDS));
   }

   @Test
   void concurrentRequestsForTheSameDataSourceCreateItsPoolOnce() throws Exception {
      Future<DataSource> first = executor.submit(
         () -> factory.getConnectionPool(dataSource("A"), null));
      assertTrue(factory.startedA.await(10, TimeUnit.SECONDS));
      Future<DataSource> second = executor.submit(
         () -> factory.getConnectionPool(dataSource("A"), null));

      factory.releaseA.countDown();

      assertSame(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
      assertEquals(1, factory.creations.get("A"));
   }

   @Test
   void aThreadWaitingForAnotherThreadsCreationCanBeInterrupted() throws Exception {
      executor.submit(() -> factory.getConnectionPool(dataSource("A"), null));
      assertTrue(factory.startedA.await(10, TimeUnit.SECONDS));

      AtomicReference<Throwable> thrown = new AtomicReference<>();
      Thread waiter = new Thread(() -> {
         try {
            factory.getConnectionPool(dataSource("A"), null);
         }
         catch(Throwable e) {
            thrown.set(e);
         }
      });
      waiter.setDaemon(true);
      waiter.start();

      // wait until it is parked on the creation lock
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

      while(waiter.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
         Thread.sleep(10);
      }

      assertEquals(Thread.State.WAITING, waiter.getState());

      // the creating thread is still blocked: only an interruptible wait lets this thread go
      waiter.interrupt();
      waiter.join(5000);
      assertFalse(waiter.isAlive(), "the waiting thread returned after the interrupt");
      assertInstanceOf(IllegalStateException.class, thrown.get());
   }
}
